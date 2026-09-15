package com.haooz.chedule.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 课表分享口令：创建与按口令取回 */
object ShareCodeApi {
    // 与其他后端接口一致：HTTPS(443) 在部分运营商网络下 TLS 握手被干扰，改用 3000 直达
    private const val BASE_URL = "http://182.92.193.223:3000/api/share"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    data class CreateResult(
        val code: String,
        val scheduleName: String,
        val expiresInSeconds: Int,
    )

    data class FetchResult(
        val code: String,
        val scheduleName: String,
        val scheduleData: Map<String, Any>,
    )

    /**
     * 上传课表创建分享口令。
     * @param scheduleJson 已序列化的课表 JSON 字符串
     */
    suspend fun createShare(
        scheduleName: String,
        scheduleJson: String,
    ): Result<CreateResult> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("schedule_name", scheduleName)
                put("schedule_data", JSONObject(scheduleJson))
            }
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(BASE_URL).post(body).build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val err = try {
                        JSONObject(text).optString("error")
                    } catch (_: Exception) {
                        ""
                    }
                    return@withContext Result.failure(
                        IllegalStateException(err.ifBlank { "服务器错误 HTTP ${resp.code}" })
                    )
                }
                val json = JSONObject(text)
                val code = json.optString("code")
                if (code.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("服务器未返回口令"))
                }
                Result.success(
                    CreateResult(
                        code = code,
                        scheduleName = json.optString("schedule_name").ifBlank { scheduleName },
                        expiresInSeconds = json.optInt("expires_in", 1800),
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 按口令取回课表数据 */
    suspend fun fetchShare(code: String): Result<FetchResult> = withContext(Dispatchers.IO) {
        try {
            val trimmed = code.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("请输入口令"))
            }
            val url = "$BASE_URL?code=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val json = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    null
                }
                if (!resp.isSuccessful) {
                    val err = json?.optString("error").orEmpty()
                    return@withContext Result.failure(
                        IllegalStateException(
                            err.ifBlank {
                                when (resp.code) {
                                    404 -> "口令不存在或已过期"
                                    else -> "服务器错误 HTTP ${resp.code}"
                                }
                            }
                        )
                    )
                }
                if (json == null || !json.optBoolean("ok", false)) {
                    return@withContext Result.failure(IllegalStateException("口令不存在或已过期"))
                }
                @Suppress("UNCHECKED_CAST")
                val data = json.optJSONObject("schedule_data")?.let { obj ->
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                    com.google.gson.Gson().fromJson<Map<String, Any>>(obj.toString(), type)
                }
                if (data == null) {
                    return@withContext Result.failure(IllegalStateException("课表数据无效"))
                }
                Result.success(
                    FetchResult(
                        code = json.optString("code").ifBlank { trimmed },
                        scheduleName = json.optString("schedule_name").ifBlank { "分享的课表" },
                        scheduleData = data,
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
