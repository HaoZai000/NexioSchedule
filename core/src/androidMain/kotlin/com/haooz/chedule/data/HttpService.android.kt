package com.haooz.chedule.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Android 实现：OkHttp。
 *
 * **刻意与迁移前的用法逐字对齐**，包括：
 * - `RetryOnConnectionFailure` 保持默认（true），迁移前多数调用点依赖它
 * - 只有显式传了 [HttpTimeouts.callSeconds] 才设 `callTimeout`，
 *   迁移前有些文件根本不设（否则会给原本无整体超时的请求加上超时，属行为变更）
 * - 响应体用 `body?.bytes()` 一次读空，对应迁移前的 `body?.string()`
 *
 * 与迁移前的**唯一差异**：OkHttpClient 由本对象创建并在 [createHttpService] 内缓存，
 * 不再每个文件各建一个。迁移前 10 个文件各自 `new OkHttpClient()`，等于 10 套连接池
 * 与线程池；计划文档把这点记为「顺带要修的真问题」。
 *
 * 缓存键含超时值 —— 超时不同的请求不能共用同一个 Client。
 */
private val clientCache = HashMap<HttpTimeouts, OkHttpClient>()

private fun clientFor(timeouts: HttpTimeouts): OkHttpClient =
    synchronized(clientCache) {
        clientCache.getOrPut(timeouts) {
            OkHttpClient.Builder()
                .connectTimeout(timeouts.connectSeconds, TimeUnit.SECONDS)
                .readTimeout(timeouts.readSeconds, TimeUnit.SECONDS)
                .also { builder ->
                    if (timeouts.callSeconds > 0) {
                        builder.callTimeout(timeouts.callSeconds, TimeUnit.SECONDS)
                    }
                }
                .build()
        }
    }

actual fun createHttpService(timeouts: HttpTimeouts): HttpService = OkHttpHttpService(timeouts)

private class OkHttpHttpService(private val timeouts: HttpTimeouts) : HttpService {

    override suspend fun get(url: String, headers: Map<String, String>): HttpResult =
        execute(method = "GET", url = url, body = null, contentType = null, headers = headers)

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String>,
    ): HttpResult = execute("POST", url, body, contentType, headers)

    override suspend fun request(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
    ): HttpResult = execute(method, url, body, contentType, headers)

    private suspend fun execute(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
    ): HttpResult = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }

        // OkHttp 要求 GET/HEAD 不能带 body；其余方法按需构造。
        val requestBody = when {
            body == null -> null
            else -> body.toRequestBody(contentType?.toMediaType())
        }
        builder.method(method, requestBody)

        val response = clientFor(timeouts).newCall(builder.build()).execute()
        try {
            HttpResult(
                code = response.code,
                bytes = response.body?.bytes() ?: ByteArray(0),
            )
        } finally {
            response.close()
        }
    }
}
