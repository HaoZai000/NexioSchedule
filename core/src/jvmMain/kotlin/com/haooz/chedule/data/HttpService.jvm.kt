package com.haooz.chedule.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

/**
 * JVM / 桌面实现：`java.net.HttpURLConnection`（纯 JDK，零第三方依赖）。
 *
 * jvm target 在本项目里的作用主要是**验证 commonMain 能编过**（SkSL 路径同理），
 * 不承载线上流量，所以不引入 OkHttp 到这个源集。
 *
 * ⚠ 与 Android 实现的已知差异（仅影响桌面调试，不影响线上）：
 * - `HttpURLConnection` 会限制部分请求方法，WebDAV 的 `PROPFIND` / `MKCOL` 可能抛
 *   `ProtocolException`。相关文件（ScheduleBackup）当前仍在 `:app`，不受影响。
 * - 4xx/5xx 时 `inputStream` 会抛异常，这里改用 `errorStream` 兜住，语义对齐 Android。
 */
actual fun createHttpService(timeouts: HttpTimeouts): HttpService = UrlConnectionHttpService(timeouts)

private class UrlConnectionHttpService(private val timeouts: HttpTimeouts) : HttpService {

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBytes: Long,
    ): HttpResult = execute("GET", url, null, null, headers, maxBytes)

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
        maxBytes: Long = -1,
    ): HttpResult = withContext(Dispatchers.IO) {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = (timeouts.connectSeconds * 1000).toInt()
            readTimeout = (timeouts.readSeconds * 1000).toInt()
            // ⚠ [HttpTimeouts.callSeconds]（整体超时）在 HttpURLConnection 上**无法表达**，
            // 因此这里不设置、即被忽略。早先版本把它当成 readTimeout 覆盖了 readSeconds，
            // 那会让「read 10s / call 15s」变成 read 15s —— 与 Android 侧行为不一致。
            // 影响范围仅限桌面调试路径（线上走 androidMain 的 OkHttp）。
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            // 与 OkHttp 默认一致
            instanceFollowRedirects = true
        }

        try {
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            var truncated = false
            val bytes: ByteArray
            // 与 Android 实现保持一致：声明长度已超限就不去读，返回空字节 + truncated。
            // 两个平台若不统一，将来在 truncated 分支里用 bytes 的调用方会得到不同结果。
            if (maxBytes > 0 && conn.contentLengthLong > maxBytes) {
                truncated = true
                bytes = ByteArray(0)
            } else {
                // 4xx/5xx 走 errorStream，否则 HttpURLConnection 会抛 IOException
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                bytes = if (stream == null) {
                    ByteArray(0)
                } else {
                    stream.use { input ->
                        if (maxBytes > 0) {
                            val (b, t) = readAtMost(maxBytes) { buf -> input.read(buf) }
                            truncated = t
                            b
                        } else {
                            input.readBytes()
                        }
                    }
                }
            }
            HttpResult(code, bytes, truncated)
        } finally {
            conn.disconnect()
        }
    }
}
