/**
 * 鸿蒙端 HTTP：**尚未接入 ArkTS 网络栈**，但必须「失败得体面」。
 *
 * 早先这里写成「调用即抛 NotImplementedError」，结果启动直接崩 —— `StatsReporter`
 * 在 App 一起来就发活跃/安装上报，它不关心结果，但异常会冒到顶把整个进程带走。
 *
 * 所以改成：**永远返回 503 + 空响应体**。语义上等于「服务端暂时不可用」：
 * - 调用方按状态码判失败（`HttpResult.isSuccessful` 为 false），走各自的失败分支；
 * - 不会有人把空 body 当成功数据解析；
 * - 启动路径、后台同步、更新检查全都安全降级。
 *
 * 接的时候按 ArkTS 的 `@ohos.net.http` 或 C++ libcurl 实现三个方法，语义照 `HttpService`：
 * 网络异常照常抛、响应体一次性读入、`maxBytes` 必须边读边计数。
 */
package com.haooz.chedule.data.platform

private const val UNAVAILABLE_CODE = 503

private class UnavailableHttpService : HttpService {

    private fun unavailable() = HttpResult(UNAVAILABLE_CODE, ByteArray(0))

    override suspend fun get(url: String, headers: Map<String, String>, maxBytes: Long): HttpResult =
        unavailable()

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String>,
    ): HttpResult = unavailable()

    override suspend fun request(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
    ): HttpResult = unavailable()
}

actual fun createHttpService(timeouts: HttpTimeouts): HttpService = UnavailableHttpService()
