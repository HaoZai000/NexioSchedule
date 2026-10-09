package com.haooz.chedule.data

/**
 * Kotlin/Native 实现（linuxX64 门禁目标；将来 iosArm64 / iosSimulatorArm64 共用）。
 *
 * 与 JVM 实现一致：`i`/`w` 走 stdout、`e` 走 stderr、`d` 丢弃。
 * 将来接 iOS 时可换成 `NSLog`（`platform.Foundation.NSLog`），本文件的结构不用变。
 */
actual fun platformLog(level: Int, tag: String, message: String, throwable: Throwable?) {
    if (level < LOG_LEVEL_INFO) return // v / d：调试流水
    val prefix = if (level >= LOG_LEVEL_ERROR) "[E]" else "[I]"
    println("$prefix[$tag] $message")
    throwable?.printStackTrace()
}
