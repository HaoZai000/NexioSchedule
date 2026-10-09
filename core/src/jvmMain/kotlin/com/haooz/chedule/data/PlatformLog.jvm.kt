package com.haooz.chedule.data

/**
 * JVM / 桌面实现。
 *
 * 按级别分流，让桌面端跑 `ImageComposeScene` 时能在控制台看到关键日志：
 * `d` 直接丢弃（否则 `schedule_*` 这类流水会淹没控制台），
 * `e` 走 stderr 以便与 stdout 分开重定向。
 */
actual fun platformLog(level: Int, tag: String, message: String, throwable: Throwable?) {
    if (level < LOG_LEVEL_INFO) return // v / d：项目当前 0 处使用 v，d 为调试流水
    val stream = if (level >= LOG_LEVEL_ERROR) System.err else System.out
    stream.println("[$tag] $message")
    throwable?.printStackTrace(stream)
}
