package com.haooz.chedule.data.platform

/** 平台 HTTP 接口。网络异常向调用方传播；非流式方法返回完整或有界的响应体。 */
interface HttpService {

    /**
     * GET，返回状态码与响应体。
     *
     * @param maxBytes 响应体上限；非正值表示不限制。超过上限时停止读取，
     *   并设置 [HttpResult.truncated]，不将整个响应载入内存。
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Long = -1,
    ): HttpResult

    /** 请求体使用 UTF-8 编码。 */
    suspend fun post(
        url: String,
        body: String,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
    ): HttpResult

    /**
     * 任意方法。WebDAV 备份用到 `PROPFIND` / `MKCOL`。
     * @param body 为 null 表示无请求体
     */
    suspend fun request(
        method: String,
        url: String,
        body: String? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): HttpResult

    /**
     * 将响应逐行交给 [onLine]，供 SSE 消费。支持流式读取的实现可在收到完整行时回调；
     * 默认实现在读完整个响应后回调。回调中的耗时操作会延迟后续行的处理。
     */
    suspend fun streamPost(
        url: String,
        body: String,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
        onLine: suspend (String) -> Unit,
    ) {
        val result = post(url, body, contentType, headers)
        result.text.lineSequence().forEach { line ->
            if (line.isNotBlank()) onLine(line)
        }
    }
}

/**
 * @param truncated 是否因超出 `maxBytes` 而被截断。为 true 时 [bytes] 是不完整内容，
 *   调用方应视为失败，不要拿去解析。
 */
class HttpResult(
    val code: Int,
    val bytes: ByteArray,
    val truncated: Boolean = false,
) {
    val isSuccessful: Boolean get() = code in 200..299

    val text: String get() = bytes.decodeToString()

    override fun toString(): String =
        "HttpResult(code=$code, bytes=${bytes.size}${if (truncated) ", truncated" else ""})"
}

/**
 * 超限时停止读取，返回前 [maxBytes] 个字节并标记截断；非正上限直接返回空数组。
 *
 * @param read 每次调用读一段到给定缓冲区，返回读到的字节数，-1 表示流结束。
 */
internal fun readAtMost(maxBytes: Long, read: (ByteArray) -> Int): Pair<ByteArray, Boolean> {
    if (maxBytes <= 0) return ByteArray(0) to false
    val buffer = ByteArray(8192)
    val chunks = ArrayList<ByteArray>()
    var total = 0L
    var truncated = false
    while (true) {
        val n = read(buffer)
        if (n == -1) break
        total += n
        if (total > maxBytes) {
            // 只保留到上限为止，多出的部分丢弃
            val keep = (n - (total - maxBytes)).toInt()
            if (keep > 0) chunks.add(buffer.copyOf(keep))
            truncated = true
            break
        }
        chunks.add(buffer.copyOf(n))
    }
    val size = chunks.sumOf { it.size }
    val out = ByteArray(size)
    var pos = 0
    for (chunk in chunks) {
        chunk.copyInto(out, pos)
        pos += chunk.size
    }
    return out to truncated
}

/** 各项超时的单位为秒。 */
data class HttpTimeouts(
    val connectSeconds: Long = 10,
    val readSeconds: Long = 10,
    /** <=0 表示不设置（对应 OkHttp 不调 callTimeout）。 */
    val callSeconds: Long = 0,
)

/** 平台实现（Android→OkHttp / JVM→UrlConnection / iOS→NSURLSession）。外部请用 [createHttpService]。 */
expect fun createPlatformHttpService(timeouts: HttpTimeouts): HttpService

/** 访问密钥请求头；App 开源，密钥可被逆向，只挡随手写脚本的，兜底靠服务端额度与预算。 */
private const val ACCESS_KEY_HEADER = "X-Nexio-Key"

/** 只看前缀会把 `nexioschedule.icu.evil.com` 也认成自家域名，必须要求主机后紧跟 `/`。 */
private fun isOwnServer(url: String): Boolean {
    if (!url.startsWith(ApiEndpoints.BASE)) return false
    val rest = url.substring(ApiEndpoints.BASE.length)
    return rest.isEmpty() || rest.startsWith("/") || rest.startsWith("?") || rest.startsWith("#")
}

/** 只给自家服务端加密钥头：BYOK / WebDAV 等第三方地址共用这个 HttpService，不能漏出去。 */
internal fun withAccessKey(url: String, headers: Map<String, String>): Map<String, String> {
    if (!isOwnServer(url)) return headers
    val key = NexioAccessKey.VALUE
    if (key.isEmpty() || headers.containsKey(ACCESS_KEY_HEADER)) return headers
    return headers + (ACCESS_KEY_HEADER to key)
}

private class AccessKeyHttpService(private val delegate: HttpService) : HttpService {

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBytes: Long,
    ): HttpResult = delegate.get(url, withAccessKey(url, headers), maxBytes)

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String>,
    ): HttpResult = delegate.post(url, body, contentType, withAccessKey(url, headers))

    override suspend fun request(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
    ): HttpResult = delegate.request(method, url, body, contentType, withAccessKey(url, headers))

    /** 显式转发：用默认实现会把 SSE 变成「读完再切行」。 */
    override suspend fun streamPost(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String>,
        onLine: suspend (String) -> Unit,
    ) = delegate.streamPost(url, body, contentType, withAccessKey(url, headers), onLine)
}

/** 全 App 统一的 HTTP 入口：平台实现外包一层访问密钥，调用方无需关心。 */
fun createHttpService(timeouts: HttpTimeouts): HttpService =
    AccessKeyHttpService(createPlatformHttpService(timeouts))
