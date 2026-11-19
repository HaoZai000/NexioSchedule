@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.data.platform
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseCancel
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject

/**
 * iOS 实现：`NSURLSession`（**零第三方依赖**，不引入 Ktor）。
 *
 * 走 `NSURLSessionDataDelegate` 流式读而非 completionHandler：契约要求 `maxBytes` **在读取过程中
 * 生效**（读到超限立即停，读完再检查时 OOM 已发生）。语义对齐 commonMain 的 `readAtMost`：
 * **只在 total > maxBytes 时才算截断**。每次请求独立建 session、完成后 `finishTasksAndInvalidate()`，
 * 换来 delegate 状态零共享零锁（回调在 session 私有串行队列上执行）。
 *
 * ⚠ Kotlin/Native 的 Foundation 绑定不完全（签名取自本机 `klib dump-metadata`）：协议接口名带后缀
 * `NSURLSessionDataDelegateProtocol`；`NSURLSessionResponseDisposition` 是 `Long`（Allow = 1L / Cancel = 0L）；
 * `setValue` / `setHTTPMethod` / `setHTTPBody` 是 `NSMutableURLRequest` 的**扩展函数**，调用处必须显式 import；
 * `statusCode` / `expectedContentLength` 为 `Long`（`NSInteger`）。
 */
actual fun createPlatformHttpService(timeouts: HttpTimeouts): HttpService =
    NsUrlSessionHttpService(timeouts)

private class NsUrlSessionHttpService(private val timeouts: HttpTimeouts) : HttpService {

    /** 会话配置按超时构建一次；`sessionWithConfiguration` 会**拷贝**配置，可安全复用。 */
    private val config: NSURLSessionConfiguration by lazy { buildConfig(timeouts) }

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
    ): HttpResult {
        val nsUrl = NSURL.URLWithString(url)
            ?: throw Exception("无效的 URL：$url")

        // cachePolicy = 忽略本地缓存：对齐 OkHttp 默认（无响应缓存，每次真请求）
        val request = NSMutableURLRequest.requestWithURL(
            URL = nsUrl,
            cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
            // request 与会话配置都设 readSeconds：二者取谁生效不完全确定，同值即可对齐 readTimeout
            timeoutInterval = timeoutSeconds(timeouts.readSeconds),
        )
        request.setHTTPMethod(method)
        headers.forEach { (k, v) -> request.setValue(value = v, forHTTPHeaderField = k) }
        // Content-Type 单独传参时覆盖 headers（与 OkHttp 一致：MediaType 无条件写入 Content-Type 头）
        if (contentType != null) request.setValue(value = contentType, forHTTPHeaderField = "Content-Type")
        if (body != null) request.setHTTPBody(body.encodeToByteArray().toNSData())

        val delegate = DataTaskDelegate(maxBytes = maxBytes, url = url)
        val session = NSURLSession.sessionWithConfiguration(
            configuration = config,
            delegate = delegate,
            delegateQueue = null, // null → session 自建串行队列：回调天然无并发
        )
        val task = session.dataTaskWithRequest(request)
        return suspendCancellableCoroutine { cont ->
            // 必须在 task.resume() 之前赋值：回调可能立刻发生
            delegate.continuation = cont
            task.resume()
            // 协程取消 → 中止传输；didCompleteWithError 带 NSURLErrorCancelled 回来，
            // resume 已取消的 continuation 是安全 no-op（值被丢弃）。
            cont.invokeOnCancellation { task.cancel() }
        }
    }

    private fun buildConfig(timeouts: HttpTimeouts): NSURLSessionConfiguration {
        val config = NSURLSessionConfiguration.defaultSessionConfiguration
        // 对齐 OkHttp readTimeout：NSData 到达间隔，连接阶段也用它（iOS 无独立 connect 超时）
        config.setTimeoutIntervalForRequest(timeoutSeconds(timeouts.readSeconds))
        // 对齐 OkHttp callTimeout：整个资源加载的总上限；callSeconds <= 0 表示不设（默认 7 天等效不设）
        if (timeouts.callSeconds > 0) {
            config.setTimeoutIntervalForResource(timeoutSeconds(timeouts.callSeconds))
        }
        // 对齐 OkHttp 默认（无 Cookie 持久化、无响应缓存）：
        // 不关的话 WebDAV 等服务端下发的 Cookie 会跨请求带上，Android 侧不会。
        config.setHTTPCookieStorage(null)
        config.setURLCache(null)
        return config
    }

    /** NSURLSession 超时必须 > 0（0 表示用系统默认 60s），OkHttp 侧同理要求正数。 */
    private fun timeoutSeconds(seconds: Long): Double = seconds.coerceAtLeast(1L).toDouble()
}

/**
 * 单请求 delegate：状态只被 session 私有串行队列读写（[continuation] 除外，
 * 它在 `task.resume()` 前由调用线程写入）。
 * ⚠ [continuation] 必须用限定名 `kotlin.concurrent.Volatile`（见顶部 import）：不限定的
 * `@Volatile` 依赖 JVM 专有默认导入，在 Kotlin/Native 上 Unresolved 编译不过。
 */
private class DataTaskDelegate(
    private val maxBytes: Long,
    private val url: String,
) : NSObject(), NSURLSessionDataDelegateProtocol {

    /** 请求结束的唯一收口。在 `task.resume()` 之前赋值，之后只读。 */
    @Volatile
    var continuation: CancellableContinuation<HttpResult>? = null

    private val chunks = ArrayList<ByteArray>()
    private var totalSize = 0L
    private var statusCode = 0
    private var truncated = false
    private var cancelledByLimit = false

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (NSURLSessionResponseDisposition) -> Unit,
    ) {
        statusCode = (didReceiveResponse as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
        // 先看声明长度：明知超限就一个字节都不收（对齐 Android 的「declared > maxBytes」分支）
        if (maxBytes > 0 && didReceiveResponse.expectedContentLength > maxBytes) {
            truncated = true
            cancelledByLimit = true
            completionHandler(NSURLSessionResponseCancel)
        } else {
            completionHandler(NSURLSessionResponseAllow)
        }
    }

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData,
    ) {
        if (cancelledByLimit) return // 已中止，队尾数据丢弃
        val incoming = didReceiveData.toByteArray()
        if (incoming.isEmpty()) return
        if (maxBytes > 0) {
            val remaining = maxBytes - totalSize
            // 语义对齐 readAtMost：**total > maxBytes 才算截断**（恰好等于上限不算，等下一包再判）
            if (incoming.size.toLong() > remaining) {
                val keep = remaining.toInt().coerceAtLeast(0)
                if (keep > 0) {
                    chunks.add(incoming.copyOf(keep))
                    totalSize += keep
                }
                truncated = true
                cancelledByLimit = true
                dataTask.cancel() // 读取过程中即停，不把剩余响应收进内存
                return
            }
        }
        chunks.add(incoming)
        totalSize += incoming.size
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        // 每请求一个 session，用完必须 invalidate —— 否则 session 钉住 delegate 不释放
        session.finishTasksAndInvalidate()
        val cont = continuation ?: return
        when {
            didCompleteWithError == null ->
                cont.resume(HttpResult(statusCode, concatChunks(), truncated))

            cancelledByLimit ->
                // maxBytes 触发的中止按「截断」处理：调用方按失败对待（契约如此），不是异常
                cont.resume(HttpResult(statusCode, concatChunks(), truncated = true))

            else ->
                // 网络异常/超时照常抛出、不吞（契约）；协程已取消时 resume 会被安全忽略（值丢弃）
                cont.resumeWithException(
                    Exception(
                        "HTTP 请求失败 [${didCompleteWithError.code}] " +
                            "${didCompleteWithError.localizedDescription}：$url",
                    ),
                )
        }
    }

    private fun concatChunks(): ByteArray {
        if (chunks.size == 1) return chunks[0]
        val out = ByteArray(totalSize.toInt())
        var pos = 0
        for (chunk in chunks) {
            chunk.copyInto(out, pos)
            pos += chunk.size
        }
        return out
    }
}
