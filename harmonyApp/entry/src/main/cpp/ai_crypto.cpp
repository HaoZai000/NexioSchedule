/*
 * 鸿蒙端 AI 会话加密（对应 Android 的 `AiTransport.jvmShared.kt`）。
 *
 * 走 OpenHarmony 的 CryptoArchitectureKit C API，不经过 ArkTS：
 * Kotlin 侧从后台线程直接调用，免去「NAPI 只能在 JS 线程」的跨线程编组。
 * 参数与 Android / 服务端逐项对齐：
 * - RSA：RSA/ECB/OAEPPadding，OAEP SHA-256，MGF1 SHA-256，label 为空
 * - AES：AES-256-GCM，12 字节 IV，128 位 tag，密文按 `ciphertext || tag` 拼接
 */

#include "ai_crypto.h"

#include <CryptoArchitectureKit/crypto_asym_cipher.h>
#include <CryptoArchitectureKit/crypto_asym_key.h>
#include <CryptoArchitectureKit/crypto_rand.h>
#include <CryptoArchitectureKit/crypto_sym_cipher.h>
#include <CryptoArchitectureKit/crypto_sym_key.h>

#include <cstring>
#include <vector>

namespace {

constexpr int kAesKeyBytes = 32;
constexpr int kIvBytes = 12;
constexpr int kTagBytes = 16;

struct NexioAiSession {
    OH_CryptoKeyPair *keyPair = nullptr;
    uint8_t key[kAesKeyBytes] = {0};
    std::vector<uint8_t> wrapped;
};

std::vector<uint8_t> RandomBytes(int len) {
    std::vector<uint8_t> out;
    OH_CryptoRand *rand = nullptr;
    if (OH_CryptoRand_Create(&rand) != CRYPTO_SUCCESS) {
        return out;
    }
    Crypto_DataBlob blob = {nullptr, 0};
    if (OH_CryptoRand_GenerateRandom(rand, len, &blob) == CRYPTO_SUCCESS &&
        blob.data != nullptr && blob.len == static_cast<size_t>(len)) {
        out.assign(blob.data, blob.data + blob.len);
    }
    OH_Crypto_FreeDataBlob(&blob);
    OH_CryptoRand_Destroy(rand);
    return out;
}

void DestroySession(NexioAiSession *session) {
    if (session == nullptr) {
        return;
    }
    if (session->keyPair != nullptr) {
        OH_CryptoKeyPair_Destroy(session->keyPair);
    }
    memset(session->key, 0, sizeof(session->key));
    delete session;
}

bool ImportPublicKey(const uint8_t *der, int32_t derLen, OH_CryptoKeyPair **outPair) {
    const char *algorithms[] = {"RSA2048", "RSA1024", "RSA4096"};
    for (const char *algo : algorithms) {
        OH_CryptoAsymKeyGenerator *gen = nullptr;
        if (OH_CryptoAsymKeyGenerator_Create(algo, &gen) != CRYPTO_SUCCESS) {
            continue;
        }
        Crypto_DataBlob pub = {const_cast<uint8_t *>(der), static_cast<size_t>(derLen)};
        OH_CryptoKeyPair *pair = nullptr;
        auto rc = OH_CryptoAsymKeyGenerator_Convert(gen, CRYPTO_DER, &pub, nullptr, &pair);
        OH_CryptoAsymKeyGenerator_Destroy(gen);
        if (rc == CRYPTO_SUCCESS && pair != nullptr) {
            *outPair = pair;
            return true;
        }
        if (pair != nullptr) {
            OH_CryptoKeyPair_Destroy(pair);
        }
    }
    return false;
}

bool WrapKey(NexioAiSession *session) {
    OH_CryptoAsymCipher *cipher = nullptr;
    if (OH_CryptoAsymCipher_Create("RSA|PKCS1_OAEP|SHA256|MGF1_SHA256", &cipher) != CRYPTO_SUCCESS) {
        return false;
    }
    bool ok = false;
    if (OH_CryptoAsymCipher_Init(cipher, CRYPTO_ENCRYPT_MODE, session->keyPair) == CRYPTO_SUCCESS) {
        Crypto_DataBlob in = {session->key, kAesKeyBytes};
        Crypto_DataBlob out = {nullptr, 0};
        if (OH_CryptoAsymCipher_Final(cipher, &in, &out) == CRYPTO_SUCCESS &&
            out.data != nullptr && out.len > 0) {
            session->wrapped.assign(out.data, out.data + out.len);
            ok = true;
        }
        OH_Crypto_FreeDataBlob(&out);
    }
    OH_CryptoAsymCipher_Destroy(cipher);
    return ok;
}

OH_CryptoSymKey *CreateAesKey(NexioAiSession *session) {
    OH_CryptoSymKeyGenerator *gen = nullptr;
    if (OH_CryptoSymKeyGenerator_Create("AES256", &gen) != CRYPTO_SUCCESS) {
        return nullptr;
    }
    Crypto_DataBlob keyData = {session->key, kAesKeyBytes};
    OH_CryptoSymKey *key = nullptr;
    auto rc = OH_CryptoSymKeyGenerator_Convert(gen, &keyData, &key);
    OH_CryptoSymKeyGenerator_Destroy(gen);
    return rc == CRYPTO_SUCCESS ? key : nullptr;
}

}  // namespace

void *NexioAiCreate(const uint8_t *der, int32_t derLen) {
    if (der == nullptr || derLen <= 0) {
        return nullptr;
    }
    auto *session = new NexioAiSession();
    if (!ImportPublicKey(der, derLen, &session->keyPair)) {
        DestroySession(session);
        return nullptr;
    }
    auto key = RandomBytes(kAesKeyBytes);
    if (key.size() != kAesKeyBytes) {
        DestroySession(session);
        return nullptr;
    }
    memcpy(session->key, key.data(), kAesKeyBytes);
    if (!WrapKey(session)) {
        DestroySession(session);
        return nullptr;
    }
    return session;
}

void NexioAiDestroy(void *handle) {
    DestroySession(static_cast<NexioAiSession *>(handle));
}

int32_t NexioAiWrap(void *handle, uint8_t *out, int32_t capacity) {
    auto *session = static_cast<NexioAiSession *>(handle);
    if (session == nullptr || out == nullptr) {
        return -1;
    }
    if (capacity < static_cast<int32_t>(session->wrapped.size())) {
        return -1;
    }
    memcpy(out, session->wrapped.data(), session->wrapped.size());
    return static_cast<int32_t>(session->wrapped.size());
}

int32_t NexioAiEncrypt(
    void *handle,
    const uint8_t *plain,
    int32_t plainLen,
    uint8_t *ivOut,
    int32_t ivCapacity,
    uint8_t *ctOut,
    int32_t ctCapacity) {
    auto *session = static_cast<NexioAiSession *>(handle);
    if (session == nullptr || plain == nullptr || plainLen < 0 || ivOut == nullptr ||
        ctOut == nullptr || ivCapacity < kIvBytes) {
        return -1;
    }
    auto iv = RandomBytes(kIvBytes);
    if (iv.size() != kIvBytes) {
        return -1;
    }
    OH_CryptoSymKey *key = CreateAesKey(session);
    if (key == nullptr) {
        return -1;
    }
    OH_CryptoSymCipherParams *params = nullptr;
    OH_CryptoSymCipher *cipher = nullptr;
    int32_t written = -1;
    do {
        if (OH_CryptoSymCipherParams_Create(&params) != CRYPTO_SUCCESS) {
            break;
        }
        Crypto_DataBlob ivBlob = {iv.data(), iv.size()};
        if (OH_CryptoSymCipherParams_SetParam(params, CRYPTO_IV_DATABLOB, &ivBlob) != CRYPTO_SUCCESS) {
            break;
        }
        if (OH_CryptoSymCipher_Create("AES256|GCM|NoPadding", &cipher) != CRYPTO_SUCCESS) {
            break;
        }
        if (OH_CryptoSymCipher_Init(cipher, CRYPTO_ENCRYPT_MODE, key, params) != CRYPTO_SUCCESS) {
            break;
        }
        Crypto_DataBlob in = {const_cast<uint8_t *>(plain), static_cast<size_t>(plainLen)};
        Crypto_DataBlob out = {nullptr, 0};
        auto rc = OH_CryptoSymCipher_Final(cipher, &in, &out);
        if (rc == CRYPTO_SUCCESS && out.data != nullptr &&
            static_cast<int32_t>(out.len) <= ctCapacity) {
            memcpy(ivOut, iv.data(), kIvBytes);
            memcpy(ctOut, out.data, out.len);
            written = static_cast<int32_t>(out.len);
        }
        OH_Crypto_FreeDataBlob(&out);
    } while (false);
    if (cipher != nullptr) {
        OH_CryptoSymCipher_Destroy(cipher);
    }
    if (params != nullptr) {
        OH_CryptoSymCipherParams_Destroy(params);
    }
    OH_CryptoSymKey_Destroy(key);
    return written;
}

int32_t NexioAiDecrypt(
    void *handle,
    const uint8_t *iv,
    int32_t ivLen,
    const uint8_t *ct,
    int32_t ctLen,
    uint8_t *out,
    int32_t outCapacity) {
    auto *session = static_cast<NexioAiSession *>(handle);
    if (session == nullptr || iv == nullptr || ct == nullptr || out == nullptr ||
        ivLen != kIvBytes || ctLen <= kTagBytes) {
        return -1;
    }
    OH_CryptoSymKey *key = CreateAesKey(session);
    if (key == nullptr) {
        return -1;
    }
    OH_CryptoSymCipherParams *params = nullptr;
    OH_CryptoSymCipher *cipher = nullptr;
    int32_t written = -1;
    do {
        if (OH_CryptoSymCipherParams_Create(&params) != CRYPTO_SUCCESS) {
            break;
        }
        Crypto_DataBlob ivBlob = {const_cast<uint8_t *>(iv), static_cast<size_t>(ivLen)};
        if (OH_CryptoSymCipherParams_SetParam(params, CRYPTO_IV_DATABLOB, &ivBlob) != CRYPTO_SUCCESS) {
            break;
        }
        Crypto_DataBlob tagBlob = {
            const_cast<uint8_t *>(ct) + (ctLen - kTagBytes), static_cast<size_t>(kTagBytes)};
        if (OH_CryptoSymCipherParams_SetParam(params, CRYPTO_TAG_DATABLOB, &tagBlob) != CRYPTO_SUCCESS) {
            break;
        }
        if (OH_CryptoSymCipher_Create("AES256|GCM|NoPadding", &cipher) != CRYPTO_SUCCESS) {
            break;
        }
        if (OH_CryptoSymCipher_Init(cipher, CRYPTO_DECRYPT_MODE, key, params) != CRYPTO_SUCCESS) {
            break;
        }
        Crypto_DataBlob in = {
            const_cast<uint8_t *>(ct), static_cast<size_t>(ctLen - kTagBytes)};
        Crypto_DataBlob plain = {nullptr, 0};
        auto rc = OH_CryptoSymCipher_Final(cipher, &in, &plain);
        if (rc == CRYPTO_SUCCESS && plain.data != nullptr &&
            static_cast<int32_t>(plain.len) <= outCapacity) {
            memcpy(out, plain.data, plain.len);
            written = static_cast<int32_t>(plain.len);
        }
        OH_Crypto_FreeDataBlob(&plain);
    } while (false);
    if (cipher != nullptr) {
        OH_CryptoSymCipher_Destroy(cipher);
    }
    if (params != nullptr) {
        OH_CryptoSymCipherParams_Destroy(params);
    }
    OH_CryptoSymKey_Destroy(key);
    return written;
}

// Kotlin 侧 @CName 导出的注册入口（libentry 有 DT_NEEDED → libnexioohos，可直接调用）。
extern "C" void NexioOnAiCryptoReady(
    void *(*create)(const uint8_t *, int32_t),
    void (*destroy)(void *),
    int32_t (*wrap)(void *, uint8_t *, int32_t),
    int32_t (*encrypt)(void *, const uint8_t *, int32_t, uint8_t *, int32_t, uint8_t *, int32_t),
    int32_t (*decrypt)(void *, const uint8_t *, int32_t, const uint8_t *, int32_t, uint8_t *, int32_t));

void NexioRegisterAiCrypto() {
    NexioOnAiCryptoReady(
        NexioAiCreate,
        NexioAiDestroy,
        NexioAiWrap,
        NexioAiEncrypt,
        NexioAiDecrypt);
}
