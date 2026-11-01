/**
 * 鸿蒙端 AI 会话加密：**尚未接入**。
 *
 * Android/JVM 走 `javax.crypto`（RSA + AES-GCM），iOS 走 Swift 桥。鸿蒙侧对应的能力是
 * ArkTS 的 `@ohos.security.cryptoFramework`，需要一层 NAPI/akinterop 桥，还没做。
 *
 * 之所以留一个「构造就抛」的实现而不是空壳：AI 功能在鸿蒙端起不到，早点炸比静默发
 * 明文请求好。等真接 AI 时把这里换成 crypto 桥即可，`AiClient` 那边一行不用改。
 */
package com.haooz.chedule.data.ai

actual class AiCryptoSession actual constructor(publicKeyPem: String) {

    init {
        throw NotImplementedError("鸿蒙端 AI 加密尚未接入（需接 @ohos.security.cryptoFramework）")
    }

    actual val wrappedKey: String
        get() = throw NotImplementedError("鸿蒙端 AI 加密尚未接入")

    actual fun encrypt(plainText: String): AiEnvelope =
        throw NotImplementedError("鸿蒙端 AI 加密尚未接入")

    actual fun decrypt(iv: String, ct: String): String =
        throw NotImplementedError("鸿蒙端 AI 加密尚未接入")
}
