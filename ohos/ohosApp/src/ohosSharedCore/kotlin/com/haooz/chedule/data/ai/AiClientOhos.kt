@file:OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)

package com.haooz.chedule.data.ai

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.invoke
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 鸿蒙端 AI 会话加密。
 *
 * 实现在 `libentry.so` 的 `ai_crypto.cpp`（CryptoArchitectureKit C API），参数与
 * Android / 服务端逐项一致：RSA-OAEP(SHA-256/MGF1-SHA256) 包裹 32 字节会话密钥，
 * AES-256-GCM（12 字节 IV、128 位 tag）加密正文。
 *
 * 为什么不走 ArkTS：`AiCryptoSession` 的接口是同步的，而 NAPI 调 JS 必须在 JS 线程；
 * CryptoArchitectureKit 有 NDK C API，Kotlin/Native 直接拿到函数指针即可在任意线程调用。
 */
private object AiCryptoBridge {

    private var createFn: ((CPointer<UByteVar>?, Int) -> COpaquePointer?)? = null
    private var destroyFn: ((COpaquePointer?) -> Unit)? = null
    private var wrapFn: ((COpaquePointer?, CPointer<UByteVar>?, Int) -> Int)? = null
    private var encryptFn: ((COpaquePointer?, CPointer<UByteVar>?, Int, CPointer<UByteVar>?, Int, CPointer<UByteVar>?, Int) -> Int)? = null
    private var decryptFn: ((COpaquePointer?, CPointer<UByteVar>?, Int, CPointer<UByteVar>?, Int, CPointer<UByteVar>?, Int) -> Int)? = null

    fun register(
        create: COpaquePointer?,
        destroy: COpaquePointer?,
        wrap: COpaquePointer?,
        encrypt: COpaquePointer?,
        decrypt: COpaquePointer?,
    ) {
        create?.let {
            val fn = it.reinterpret<CFunction<(CPointer<UByteVar>?, Int) -> COpaquePointer?>>()
            createFn = { der, len -> fn.invoke(der, len) }
        }
        destroy?.let {
            val fn = it.reinterpret<CFunction<(COpaquePointer?) -> Unit>>()
            destroyFn = { handle -> fn.invoke(handle) }
        }
        wrap?.let {
            val fn = it.reinterpret<CFunction<(COpaquePointer?, CPointer<UByteVar>?, Int) -> Int>>()
            wrapFn = { handle, out, cap -> fn.invoke(handle, out, cap) }
        }
        encrypt?.let {
            val fn = it.reinterpret<
                CFunction<(
                    COpaquePointer?, CPointer<UByteVar>?, Int,
                    CPointer<UByteVar>?, Int,
                    CPointer<UByteVar>?, Int,
                ) -> Int>
                >()
            encryptFn = { handle, plain, plainLen, iv, ivCap, ct, ctCap ->
                fn.invoke(handle, plain, plainLen, iv, ivCap, ct, ctCap)
            }
        }
        decrypt?.let {
            val fn = it.reinterpret<
                CFunction<(
                    COpaquePointer?, CPointer<UByteVar>?, Int,
                    CPointer<UByteVar>?, Int,
                    CPointer<UByteVar>?, Int,
                ) -> Int>
                >()
            decryptFn = { handle, iv, ivLen, ct, ctLen, out, outCap ->
                fn.invoke(handle, iv, ivLen, ct, ctLen, out, outCap)
            }
        }
    }

    fun create(der: ByteArray): COpaquePointer? {
        val fn = createFn ?: throw IllegalStateException("AI 加密桥未就绪")
        return der.usePinned { pinned -> fn(pinned.addressOf(0).reinterpret(), der.size) }
    }

    fun destroy(handle: COpaquePointer?) {
        destroyFn?.invoke(handle)
    }

    fun wrapKey(handle: COpaquePointer): ByteArray {
        val fn = wrapFn ?: throw IllegalStateException("AI 加密桥未就绪")
        val buffer = ByteArray(1024)
        val written = buffer.usePinned { pinned ->
            fn(handle, pinned.addressOf(0).reinterpret(), buffer.size)
        }
        if (written <= 0) throw IllegalStateException("AI 会话密钥加密失败")
        return buffer.copyOf(written)
    }

    fun encrypt(handle: COpaquePointer, plain: ByteArray): Pair<ByteArray, ByteArray> {
        val fn = encryptFn ?: throw IllegalStateException("AI 加密桥未就绪")
        val iv = ByteArray(12)
        val cipherText = ByteArray(plain.size + 32)
        val written = plain.usePinned { plainPinned ->
            iv.usePinned { ivPinned ->
                cipherText.usePinned { ctPinned ->
                    fn(
                        handle,
                        plainPinned.addressOf(0).reinterpret(), plain.size,
                        ivPinned.addressOf(0).reinterpret(), iv.size,
                        ctPinned.addressOf(0).reinterpret(), cipherText.size,
                    )
                }
            }
        }
        if (written < 0) throw IllegalStateException("AI 请求加密失败")
        return iv to cipherText.copyOf(written)
    }

    fun decrypt(handle: COpaquePointer, iv: ByteArray, cipherText: ByteArray): ByteArray {
        val fn = decryptFn ?: throw IllegalStateException("AI 加密桥未就绪")
        val out = ByteArray(cipherText.size)
        val written = iv.usePinned { ivPinned ->
            cipherText.usePinned { ctPinned ->
                out.usePinned { outPinned ->
                    fn(
                        handle,
                        ivPinned.addressOf(0).reinterpret(), iv.size,
                        ctPinned.addressOf(0).reinterpret(), cipherText.size,
                        outPinned.addressOf(0).reinterpret(), out.size,
                    )
                }
            }
        }
        if (written < 0) throw IllegalStateException("AI 响应解密失败")
        return out.copyOf(written)
    }
}

/** C++ 在模块初始化时把五个函数指针交过来（见 `ai_crypto.cpp` / `napi_init.cpp`）。 */
@CName("NexioOnAiCryptoReady")
fun NexioOnAiCryptoReady(
    create: COpaquePointer?,
    destroy: COpaquePointer?,
    wrap: COpaquePointer?,
    encrypt: COpaquePointer?,
    decrypt: COpaquePointer?,
) {
    AiCryptoBridge.register(create, destroy, wrap, encrypt, decrypt)
}

actual class AiCryptoSession actual constructor(publicKeyPem: String) {

    private val handle: COpaquePointer = AiCryptoBridge.create(decodePem(publicKeyPem))
        ?: throw IllegalStateException("AI 加密初始化失败（公钥无效或系统加密能力不可用）")

    actual val wrappedKey: String = Base64.Default.encode(AiCryptoBridge.wrapKey(handle))

    actual fun encrypt(plainText: String): AiEnvelope {
        val (iv, cipherText) = AiCryptoBridge.encrypt(handle, plainText.encodeToByteArray())
        return AiEnvelope(
            ek = wrappedKey,
            iv = Base64.Default.encode(iv),
            ct = Base64.Default.encode(cipherText),
        )
    }

    actual fun decrypt(iv: String, ct: String): String {
        val ivBytes = Base64.Default.decode(iv)
        require(ivBytes.size == 12) { "IV 长度不对：${ivBytes.size}" }
        val plain = AiCryptoBridge.decrypt(handle, ivBytes, Base64.Default.decode(ct))
        return plain.decodeToString()
    }

    /** 只取 PEM 正文再 base64 解码；`\r`、空行与首尾空白都剔掉。 */
    private fun decodePem(pem: String): ByteArray {
        val body = pem
            .replace("\r", "")
            .lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
            .trim()
        require(body.isNotEmpty()) { "公钥 PEM 为空" }
        return Base64.Default.decode(body)
    }
}
