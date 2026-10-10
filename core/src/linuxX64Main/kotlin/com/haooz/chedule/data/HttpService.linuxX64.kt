package com.haooz.chedule.data

/**
 * Linux/Native 的 HTTP 实现 —— **占位：调用即抛异常**。
 *
 * ## 为什么这个文件在 linuxX64Main 而不是 nativeMain
 *
 * `linuxX64` 目标在本项目里**只作编译门禁**（拦截 commonMain 混入 JVM 专有 API），
 * 不承载任何线上流量，因此这里保留一个显式失败的占位即可。
 *
 * 曾经它放在 `nativeMain`（覆盖全部 Native 目标），2026-10-10 实现 iOS 侧的
 * `NSURLSession` 之后挪到了这里 —— 原因：**iOS 与 Linux 不能共存同一个 actual**，
 * `iosMain` 已有真正的实现（见 `iosMain/HttpService.ios.kt`），
 * 若 nativeMain 仍保留 actual，iOS 目标会同时看到两个 actual 而编译失败。
 *
 * 因此现在的分工：
 * - `linuxX64Main`（本文件）→ 编译门禁目标，显式失败占位
 * - `iosMain` → 真正的 `NSURLSession` 实现
 *
 * **若将来再加非 Apple 的 Native 目标**（mingwX64 等），它会继承本占位 ——
 * 需要按 iOS 实现的模式再补一个平台实现，编译门禁会立刻把缺 actual 报出来。
 *
 * @see com.haooz.chedule.data.createHttpService
 */
actual fun createHttpService(timeouts: HttpTimeouts): HttpService =
    LinuxUnimplementedHttpService(timeouts)

private class LinuxUnimplementedHttpService(
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
        "linuxX64 仅作编译门禁目标，不承载网络调用：OkHttp 无 Native 版本，" +
            "iOS 侧见 iosMain/HttpService.ios.kt（NSURLSession）。详见本文件顶部说明。"
    )
}
