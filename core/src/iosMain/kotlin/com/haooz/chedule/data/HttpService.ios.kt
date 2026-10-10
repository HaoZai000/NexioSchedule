@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.data

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
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
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseCancel
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS 实现：`NSURLSession`（**零第三方依赖**，不引入 Ktor —— 原因见 [HttpService] KDoc）。
 *
 * ## 为什么用 delegate 流式读，而不是 completionHandler 一把梭
 *
 * 契约要求 `maxBytes` **在读取过程中生效**（读到超限立即停，不能读完再检查 ——
 * 那样 OOM 已经发生）。`dataTaskWithRequest(completionHandler:)` 要等**整个响应体
 * 收完**才回调，大响应会先把内存吃满，违背契约。所以这里走
 * `NSURLSessionDataDelegate` 三件套：
 *
 * - `didReceiveResponse` → 先看声明长度（`expectedContentLength`）：
 *   已超限就 `NSURLSessionResponseCancel` —— 一个字节都不收，
 *   **与 Android 实现的「声明长度已超限 → 空字节 + truncated」逐字对齐**。
 * - `didReceiveData` → 边收边计数，超过 `maxBytes` 只保留到上限为止并立即
 *   `task.cancel()`（语义对齐 commonMain 的 `readAtMost`：**只在 total > maxBytes
 *   时才算截断**，恰好等于上限不算）。
 * - `didCompleteWithError` → 统一收口：成功 / 超限截断 / 网络异常，各走各的分支。
 *
 * ## 会话生命周期
 *
 * **每次请求独立创建一个 session，完成后 `finishTasksAndInvalidate()`。**
 * 按 Apple 的指引 session 应当复用，但共享 session 的 delegate 必须维护
 * 「task → 请求状态」的路由表，引入共享可变状态；本 App 的 HTTP 调用频次很低
 * （学校索引 / 脚本 / 公告 / 打点 / WebDAV 备份，量级为每次运行几十次），
 * 换来的是 delegate 状态零共享、零锁 —— 回调在 session 私有的串行队列上执行。
 * 若将来高频调用，可改为按 [HttpTimeouts] 缓存 session + delegate 内按
 * `taskIdentifier` 路由（Android 侧 `clientCache` 就是这个模式）。
 *
 * ## 与 Android（OkHttp）的已知差异
 *
 * | 项 | OkHttp | NSURLSession | 影响 |
 * |---|---|---|---|
 * | connect 超时 | `connectTimeout` 独立配置 | 无独立 API，共用 `timeoutIntervalForRequest` | 连接阶段超时 ≈ readSeconds（iOS 系统限制，无解） |
 * | call 超时 | `callTimeout` | `timeoutIntervalForResource` | 语义近似：整个资源加载的上限 |
 * | Cookie | 默认无持久化 | 默认有 `sharedCookieStorage` → **已显式关闭** | 对齐 OkHttp |
 * | 响应缓存 | 默认无 | 默认有 URLCache → **`cachePolicy = 忽略本地缓存` + `URLCache = null`** | 对齐 OkHttp |
 * | 重定向 | 默认跟随 | 默认跟随 | 一致 |
 * | 异常类型 | IOException | 普通 `Exception`（带 NSError code/description） | 调用方全部 `catch (Exception)`，已核对 32 处 |
 *
 * ## 签名来源（不是猜的）
 *
 * 所有 ObjC 方法签名（协议方法、枚举值、setter 扩展名）来自本机
 * `klib dump-metadata` 导出的 `platform.Foundation`（Kotlin/Native 2.4.10 / ios_arm64）：
 * - 协议接口名带后缀：`NSURLSessionDataDelegateProtocol`（无短名别名）
 * - `NSURLSessionResponseDisposition` 是 `Long`：`Allow = 1L` / `Cancel = 0L`
 * - `setValue` / `setHTTPMethod` / `setHTTPBody` 是 `NSMutableURLRequest` 的**扩展函数**
 *   （调用处需 import，本文件顶部已列出）
 * - `statusCode` / `expectedContentLength` 为 `Long`（`NSInteger`）
 */
actual fun createHttpService(timeouts: HttpTimeouts): HttpService =
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
            // request 的超时与会话配置都设 readSeconds —— 二者取谁生效不完全确定，
            // 都设成同一值即可与 OkHttp 的 readTimeout 对齐
            timeoutInterval = timeoutSeconds(timeouts.readSeconds),
        )
        request.setHTTPMethod(method)
        headers.forEach { (k, v) -> request.setValue(value = v, forHTTPHeaderField = k) }
        // Content-Type 单独传参时优先级覆盖 headers —— 与 OkHttp 一致
        //（OkHttp 的 body MediaType 无条件写入 Content-Type 头，覆盖 builder 设置的值）
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
            // 协程被取消（调用方 scope 收掉）→ 中止传输；
            // didCompleteWithError 会带 NSURLErrorCancelled 回来，
            // resume 已取消的 continuation 属于安全 no-op（值被丢弃）。
            cont.invokeOnCancellation { task.cancel() }
        }
    }

    private fun buildConfig(timeouts: HttpTimeouts): NSURLSessionConfiguration {
        val config = NSURLSessionConfiguration.defaultSessionConfiguration
        // 对齐 OkHttp readTimeout：NSData 到达间隔，连接阶段也用它（iOS 无独立 connect 超时）
        config.setTimeoutIntervalForRequest(timeoutSeconds(timeouts.readSeconds))
        // 对齐 OkHttp callTimeout：整个资源加载的总上限；callSeconds <= 0 表示不设
        //（NSURLSession 的默认值 7 天，等效于不设，无需显式恢复）
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
 * 它在 `task.resume()` 前由调用线程写入，故标 `@Volatile` 保证可见性）。
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
        // 先看声明长度：明知超限就一个字节都不收（对齐 Android 的
        //「declared > maxBytes → 空字节 + truncated」分支）
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
            // 语义对齐 readAtMost：**total > maxBytes 才算截断**，
            // 恰好等于上限时全部收下、等下一包再判（流若在此结束则不算截断）
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
                // 网络异常/超时照常抛出、不吞（契约）。若协程已被调用方取消，
                // resume 落在已取消的 continuation 上会被安全忽略（值丢弃）。
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

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, size.convert())
    }
    return out
}

private fun ByteArray.toNSData(): NSData {
    // 空数组不能走 addressOf(0)（零长数组取指针会抛），显式走 null + 0 分支
    if (isEmpty()) return NSData.dataWithBytes(null, 0u)
    usePinned { pinned ->
        return NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }
}
