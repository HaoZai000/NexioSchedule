package com.haooz.chedule.data

/**
 * 跨平台 HTTP 门面。
 *
 * ## 为什么不用 Ktor（重要，别顺手换掉）
 *
 * 计划文档原本写的是「Ktor 包一层，OkHttp 不删」。实测后改了主意，原因是**依赖代价**：
 *
 * | Ktor 版本 | 连带把 kotlinx-coroutines 从 1.9.0 顶到 |
 * |---|---|
 * | 3.6.0 | **1.11.0** |
 * | 3.1.3 | **1.10.2** |
 *
 * `:app` 的协程 1.9.0 是从 Compose 传递来的。引入任何现代 Ktor 都会把全 app 的协程
 * 换掉 —— 而这个 App 的提醒、闹钟、同步、小组件全压在协程上。
 *
 * **把「升级核心异步库」捆进「KMP 迁移」是坏主意**：一旦真机出现时序/取消类异常，
 * 无法判断是迁移引入的还是协程升级引入的，且无法单独回退其中一个。
 *
 * 所以这里只定义接口，Android 实现继续用 OkHttp（引擎、超时、重试语义与迁移前逐字一致）。
 * 将来想换 Ktor 时，只需换一个 [HttpService] 实现 —— 这也是把接口单独抽出来的意义。
 *
 * iOS 侧同理：由平台侧提供一个 `NSURLSession` 实现即可，commonMain 的调用方不用改。
 *
 * ## 与 OkHttp 的语义对应
 *
 * - [HttpResult.isSuccessful] ≡ `Response.isSuccessful`（200..299）
 * - 网络异常**照常抛出**（不吞）：迁移前的调用点普遍自带 `try/catch`，
 *   保持抛出才能让那些兜底继续生效。
 * - 响应体**一次性读入内存**。迁移前的调用点也都是一次性读取
 *   （`body.string()` / 下载脚本时整体写出），没有流式消费。
 */
interface HttpService {

    /** GET，返回状态码与响应体。 */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult

    /** POST，body 为 UTF-8 文本（迁移前都是 `toRequestBody(application/json)`）。 */
    suspend fun post(
        url: String,
        body: String,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
    ): HttpResult

    /**
     * 任意方法。WebDAV 备份用到 `PROPFIND` / `MKCOL`，OkHttp 直接支持。
     * @param body 为 null 表示无请求体
     */
    suspend fun request(
        method: String,
        url: String,
        body: String? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): HttpResult
}

/** 一次性读空的响应。 */
class HttpResult(
    val code: Int,
    val bytes: ByteArray,
) {
    val isSuccessful: Boolean get() = code in 200..299

    /** 响应体按 UTF-8 解码。 */
    val text: String get() = bytes.decodeToString()

    override fun toString(): String = "HttpResult(code=$code, bytes=${bytes.size})"
}

/**
 * 超时配置（秒）。
 *
 * 迁移前每个文件各自 `new OkHttpClient()` 并配不同超时，实测有 4 种组合：
 * 5/5、10/30、10/10+15、12/20+30。这里保留同样的粒度，不擅自统一。
 */
data class HttpTimeouts(
    val connectSeconds: Long = 10,
    val readSeconds: Long = 10,
    /** <=0 表示不设置（对应 OkHttp 不调 callTimeout）。 */
    val callSeconds: Long = 0,
)

/**
 * 平台侧创建默认实现。
 *
 * Android → OkHttp；JVM/桌面 → `HttpURLConnection`（纯 JDK，零依赖）；
 * iOS → `NSURLSession`（待平台侧实现）。
 */
expect fun createHttpService(timeouts: HttpTimeouts): HttpService
