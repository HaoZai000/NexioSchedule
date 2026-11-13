#ifndef NEXIO_AI_CRYPTO_H
#define NEXIO_AI_CRYPTO_H

#include <cstdint>

/** 会话句柄的创建/销毁与三个加密操作；返回值 -1 表示失败。 */
void *NexioAiCreate(const uint8_t *der, int32_t derLen);
void NexioAiDestroy(void *handle);
int32_t NexioAiWrap(void *handle, uint8_t *out, int32_t capacity);
int32_t NexioAiEncrypt(
    void *handle,
    const uint8_t *plain,
    int32_t plainLen,
    uint8_t *ivOut,
    int32_t ivCapacity,
    uint8_t *ctOut,
    int32_t ctCapacity);
int32_t NexioAiDecrypt(
    void *handle,
    const uint8_t *iv,
    int32_t ivLen,
    const uint8_t *ct,
    int32_t ctLen,
    uint8_t *out,
    int32_t outCapacity);

/** 把函数指针交给 Kotlin（Kotlin 侧无法通过动态符号表解析 libentry 里的符号）。 */
void NexioRegisterAiCrypto();

#endif  // NEXIO_AI_CRYPTO_H
