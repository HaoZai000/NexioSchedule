package com.haooz.chedule.data

/**
 * Kotlin/Native 的 HTTP 实现 —— **尚未实现，调用即抛异常**。
 *
 * ## 为什么这里是占位而不是实现
 *
 * 现有三个平台实现里，Android / JVM 都复用 OkHttp，而 **OkHttp 没有 Kotlin/Native 版本**。
 * Native 上要么接平台框架（iOS 的 `NSURLSession`），要么引入 Ktor 的 CIO 引擎。
 * 两者都是独立交付物，且会牵连协程版本（见 [HttpService] 的说明），
 * 不应挤进「让 commonMain 保持平台中立」这一步里顺手做掉。
 *
 * ## 为什么要留这个文件而不是干脆不加 Native 目标
 *
 * `:core` 加了 `linuxX64` 作为**编译门禁**，目的是拦下 commonMain 里混入 JVM 专有 API
 * （`@Volatile` / `synchronized` / `Dispatchers.IO` / `System.currentTimeMillis` …）。
 * 门禁要编译通过，就必须有 actual 声明，否则报
 * `Expected createHttpService has no actual declaration`。
 *
 * 因此这里给出一个**显式失败**的占位：它让 commonMain 的平台中立性可被编译器持续校验，
 * 同时保证「Native 上网络不可用」这件事不可能被静默忽略 —— 一旦真去编 iOS，
 * 第一次网络调用就会带着这条消息抛出。
 *
 * **接 iOS 前必须替换本实现**（推荐 Ktor `ktor-client-darwin`，见 [HttpService] KDoc）。
 */
actual fun createHttpService(timeouts: HttpTimeouts): HttpService =
    NativeUnimplementedHttpService(timeouts)

private class NativeUnimplementedHttpService(
    @Suppress("unused") private val timeouts: HttpTimeouts,
) : HttpService {
    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBytes: Long,
    ): HttpResult = fail()

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String>,
    ): HttpResult = fail()

    override suspend fun request(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
    ): HttpResult = fail()

    private fun fail(): Nothing = throw NotImplementedError(
        "Kotlin/Native 的 HTTP 实现尚未接入：OkHttp 无 Native 版本，" +
            "需在此接入 Ktor CIO 或平台 NSURLSession。详见 HttpService.native.kt 顶部说明。"
    )
}
