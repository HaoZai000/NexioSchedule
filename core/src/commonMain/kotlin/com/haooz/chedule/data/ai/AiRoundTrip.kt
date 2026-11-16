package com.haooz.chedule.data.ai

import com.haooz.chedule.data.platform.HttpService
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 发一次加密请求并解开响应 —— 所有走云端 AI 的能力共用这一段。
 *
 * 响应体既可能是 `{iv, ct}`（正常），也可能是服务端的明文错误体（信封都解不开时）。
 * 后者一律当失败 —— 拿不到会话密钥就没有可读内容。
 *
 * ⚠ 会话密钥一次性：加密请求与解密响应必须是同一个 session，所以不能拆成两步调用。
 */
/**
 * 一次请求用的加密会话。
 *
 * 请求体只封一次（里面带 ek），响应可以**逐块**解密 —— 流式（SSE）每一块都是新的 iv/ct，
 * 但共用同一把会话密钥。抽出来是为了让一次性请求与流式请求走同一套密钥逻辑。
 */
internal class AiEnvelopeSession(pem: String = AiServerKey.PUBLIC_KEY_PEM) {

    private val session = AiCryptoSession(pem)

    /** 封装请求体：`{"ek","iv","ct"}` */
    fun sealRequest(json: String): String {
        val envelope = session.encrypt(json)
        return buildJsonObject {
            put("ek", JsonPrimitive(envelope.ek))
            put("iv", JsonPrimitive(envelope.iv))
            put("ct", JsonPrimitive(envelope.ct))
        }.toString()
    }

    /** 解开一块响应 */
    fun openChunk(ivB64: String, ctB64: String): String = session.decrypt(ivB64, ctB64)
}

internal suspend fun aiRoundTrip(http: HttpService, path: String, body: JsonObject): AiResult<String> {
    if (!AiServerKey.isConfigured()) {
        return AiResult.Error("这个版本还没内置服务端公钥")
    }

    val envelope = try {
        AiEnvelopeSession()
    } catch (e: Exception) {
        return AiResult.Error("加密会话创建失败：${e.describe()}")
    }
    val payload = try {
        envelope.sealRequest(body.toString())
    } catch (e: Exception) {
        return AiResult.Error("请求加密失败：${e.describe()}")
    }

    val result = try {
        http.post(path, payload)
    } catch (e: Exception) {
        return AiResult.Error("网络请求失败：${e.describe()}")
    }
    if (!result.isSuccessful) {
        return AiResult.Error("服务端返回 ${result.code}")
    }

    val response = runCatching { parseJsonObject(result.text) }.getOrNull()
        ?: return AiResult.Error("响应不是 JSON")
    val iv = response.optString("iv")
    val ct = response.optString("ct")
    if (iv.isEmpty() || ct.isEmpty()) {
        // 走到这里说明服务端是明文回的错误体，把它带出来
        val serverError = response.optString("error")
        return AiResult.Error(serverError.ifEmpty { "响应里没有密文" })
    }
    return try {
        AiResult.Ok(envelope.openChunk(iv, ct))
    } catch (e: Exception) {
        AiResult.Error("响应解密失败：${e.describe()}")
    }
}

internal fun Throwable.describe(): String = message ?: this::class.simpleName ?: "未知错误"
