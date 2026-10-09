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

    /**
     * GET，返回状态码与响应体。
     *
     * @param maxBytes 响应体上限。`<= 0` 表示不限制。
     *   超出上限时**不抛异常**，而是返回 [HttpResult.truncated] = true（可能带已读到的部分字节），
     *   由调用方决定怎么处理 —— 迁移前 `ScriptRepository` 就是「超限即视为下载失败」。
     *   存在的意义是**边读边计数、超限即中止**，避免异常大的响应先把内存吃掉。
     *   ⚠ 平台实现必须真正在读取过程中就停，不能读完再检查大小（那样 OOM 已经发生）。
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Long = -1,
    ): HttpResult

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

/**
 * 一次性读空的响应。
 *
 * @param truncated 是否因超出 `maxBytes` 而被截断。为 true 时 [bytes] 是不完整内容，
 *   调用方应视为失败，不要拿去解析。
 */
class HttpResult(
    val code: Int,
    val bytes: ByteArray,
    val truncated: Boolean = false,
) {
    val isSuccessful: Boolean get() = code in 200..299

    /** 响应体按 UTF-8 解码。 */
    val text: String get() = bytes.decodeToString()

    override fun toString(): String =
        "HttpResult(code=$code, bytes=${bytes.size}${if (truncated) ", truncated" else ""})"
}

/**
 * 在读取过程中最多读 [maxBytes]：超限立即停手，返回（已读字节, 是否被截断）。
 *
 * 抽到 commonMain 是因为各平台实现需要同一套语义 ——
 * 迁移前这段逻辑只存在于 `ScriptRepository`（用 `byteStream()` 手写），
 * 其余调用点都是无上限的 `body.bytes()`。
 *
 * 用「按块收集再拼接」而不是平台专有的 `ByteArrayOutputStream`（JVM 专有）。
 * 超限时保留到上限为止的字节，并标记截断 —— 调用方一律按失败处理。
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
