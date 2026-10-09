package com.haooz.chedule.data

/**
 * 跨平台日志门面。
 *
 * 迁移前全项目 22 个文件直接 `import android.util.Log`，共 166 处调用。这些文件里
 * `StatsReporter` / `NoticeFetcher` / `ScriptRepository` / `ScheduleBackup` / `ScheduleImport`
 * 都是纯数据层，理论上应下沉到 `:core`，但 `android.util.Log` 会直接阻断 iOS 目标。
 *
 * 用法与 `android.util.Log` **完全同形**（tag 在前、message 在后、Throwable 为可选末参），
 * 所以调用点只需改 import，函数体一个字都不用动。
 *
 * 级别映射（Android 保留原始级别；桌面/JVM 侧按级别分流）：
 * | Android | 用途                      | 实际分布 | skiko |
 * |---------|---------------------------|---------|-------|
 * | `Log.v` | 未使用                    |  0 处   | —     |
 * | `Log.d` | 调试流水                  | 57 处   | 丢弃  |
 * | `Log.i` | 关键节点                  | 12 处   | stdout |
 * | `Log.w` | 可恢复异常                | 51 处   | stdout |
 * | `Log.e` | 不可恢复 / 需要排查       | 46 处   | stderr |
 *
 * ⚠ **不要在这里加 Android 专有重载**（可变参数、Bundle、字节数组等）。
 * 一旦加了，commonMain 又出现平台 API，得不偿失。
 */

/**
 * 平台日志实现。Android → Logcat；JVM/桌面 → stdout/stderr；iOS 后续接 NSLog。
 *
 * 用顶层函数而非 `expect object`：Kotlin 的 expect/actual **类与 object 仍是 Beta**，
 * 会刷 KT-61573 警告；顶层 `expect fun` 是稳定形态，本项目 `:miuix` 也一律用它
 * （`rememberAppSettingDark` / `isTabletWidth` / `rememberNavigationBack`）。
 */
expect fun platformLog(level: Int, tag: String, message: String, throwable: Throwable?)

internal const val LOG_LEVEL_VERBOSE = 0
internal const val LOG_LEVEL_DEBUG = 1
internal const val LOG_LEVEL_INFO = 2
internal const val LOG_LEVEL_WARN = 3
internal const val LOG_LEVEL_ERROR = 4

/**
 * 全局日志开关。关掉后 [PlatformLog] 完全不被调用，调用点上的字符串拼接也一并省掉 ——
 * 这是高频日志唯一有意义的优化点。
 */
var logEnabled: Boolean = true

object NexioLog {
    fun v(tag: String, message: String) {
        if (logEnabled) platformLog(LOG_LEVEL_VERBOSE, tag, message, null)
    }

    fun v(tag: String, message: String, throwable: Throwable?) {
        if (logEnabled) platformLog(LOG_LEVEL_VERBOSE, tag, message, throwable)
    }

    fun d(tag: String, message: String) {
        if (logEnabled) platformLog(LOG_LEVEL_DEBUG, tag, message, null)
    }

    fun d(tag: String, message: String, throwable: Throwable?) {
        if (logEnabled) platformLog(LOG_LEVEL_DEBUG, tag, message, throwable)
    }

    fun i(tag: String, message: String) {
        if (logEnabled) platformLog(LOG_LEVEL_INFO, tag, message, null)
    }

    fun i(tag: String, message: String, throwable: Throwable?) {
        if (logEnabled) platformLog(LOG_LEVEL_INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String) {
        if (logEnabled) platformLog(LOG_LEVEL_WARN, tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable?) {
        if (logEnabled) platformLog(LOG_LEVEL_WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String) {
        if (logEnabled) platformLog(LOG_LEVEL_ERROR, tag, message, null)
    }

    fun e(tag: String, message: String, throwable: Throwable?) {
        if (logEnabled) platformLog(LOG_LEVEL_ERROR, tag, message, throwable)
    }
}