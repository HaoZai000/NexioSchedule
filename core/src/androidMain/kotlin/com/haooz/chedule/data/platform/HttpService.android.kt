package com.haooz.chedule.data.platform

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Android 实现：OkHttp。
 *
 * 重试保持默认 true；只有显式传 [HttpTimeouts.callSeconds] 时才设 callTimeout，
 * 避免给原本无总时长的请求加上超时。OkHttpClient 按超时配置缓存复用。
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

actual fun createPlatformHttpService(timeouts: HttpTimeouts): HttpService = OkHttpHttpService(timeouts)

private class OkHttpHttpService(private val timeouts: HttpTimeouts) : HttpService {

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBytes: Long,
    ): HttpResult = execute(
        method = "GET", url = url, body = null, contentType = null, headers = headers, maxBytes = maxBytes,
    )

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

    /**
     * 真流式：逐行把 SSE 交出去。
     *
     * ⚠ 必须在 IO 线程上跑，且**不能**用 `response.body.string()` —— 那会等连接关闭，
     * 回到「想完才显示」。这里用 `source.readUtf8Line()` 阻塞读，一行到一行回调。
     */
    override suspend fun streamPost(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String>,
        onLine: suspend (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(contentType.toMediaType()))
            .header("Accept", "text/event-stream")
        headers.forEach { (k, v) -> builder.header(k, v) }

        val call = clientFor(timeouts).newCall(builder.build())
        // ⚠ 协程取消必须能断开连接：这是阻塞读，不挂这个钩子的话「停止」之后
        // 这一轮 SSE 还会一直读到模型自己想完，delta 继续回灌给上层。
        val cancelHook = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        val response = try {
            call.execute()
        } catch (e: Throwable) {
            cancelHook?.dispose()
            throw e
        }
        try {
            val source = response.body?.source() ?: return@withContext
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isNotBlank()) onLine(line)
            }
        } finally {
            cancelHook?.dispose()
            response.close()
        }
    }

    private suspend fun execute(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
        maxBytes: Long = -1,
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
            val bodyOrNull = response.body
            val bytes: ByteArray
            var truncated = false
            if (maxBytes > 0 && bodyOrNull != null) {
                // 先看声明的长度：明知超限就不必去分配了
                val declared = bodyOrNull.contentLength()
                if (declared > maxBytes) {
                    bytes = ByteArray(0)
                    truncated = true
                } else {
                    // 流只能取一次，必须在 readAtMost 外面拿好
                    val stream = bodyOrNull.byteStream()
                    try {
                        val (b, t) = readAtMost(maxBytes) { buf -> stream.read(buf) }
                        bytes = b
                        truncated = t
                    } finally {
                        stream.close()
                    }
                }
            } else {
                bytes = bodyOrNull?.bytes() ?: ByteArray(0)
            }
            HttpResult(code = response.code, bytes = bytes, truncated = truncated)
        } finally {
            response.close()
        }
    }
}
