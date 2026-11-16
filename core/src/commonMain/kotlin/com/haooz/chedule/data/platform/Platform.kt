package com.haooz.chedule.data.platform

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 平台必须提供的能力。actual 在各平台的 `Platform.<平台>.kt` 里。
 *
 * 这是 commonMain 唯一允许的平台分叉点：加新能力先想能不能在这里表达，
 * 不要往业务代码里塞 `if (isAndroid)`。
 */

/**
 * 跨平台互斥。
 *
 * Android / JVM 是真监视器锁（与迁移前逐字等价）；Native 用 `NSRecursiveLock`
 * 按对象身份分发。别删成直通 —— `HolidayManager` 的「读 → 算 → 写」靠它。
 */
expect fun <T> synchronizedOn(lock: Any, block: () -> T): T

/**
 * 阻塞式 IO 的调度器。
 *
 * `Dispatchers.IO` 不是跨平台 API（Native 上是 internal），Native 侧用
 * `Dispatchers.Default` —— 背后同样是多线程池，语义足够接近。
 */
expect val ioDispatcher: CoroutineDispatcher

/**
 * 按中文（拼音）比较，语义同 `Comparator.compare`。
 *
 * ⚠ Native 退化为码点比较：**有意的降级**，不是等价实现。
 */
expect fun compareByChineseCollator(a: String, b: String): Int

/**
 * 提示条。Android 是真 Toast，桌面落日志，iOS 走宿主注入的原生 HUD。
 *
 * 用全局函数而不是 `@Composable`：调用点大量在协程 / 回调 / 非组合上下文里。
 * 语义与 `Toast.makeText` 一致：**不排队、不合并**。
 */
expect fun showToast(message: String, longDuration: Boolean = false)

/** 非 Android 端的提示条实现钩子，启动时注入一次；Android 不看这里。 */
var toastHandler: ((message: String, longDuration: Boolean) -> Unit)? = null

/**
 * 设备与系统信息。
 *
 * 字段名是上报契约（`StatsReporter` 原样发给服务端），**不许改名**。
 */
data class DeviceInfo(
    val model: String,
    val brand: String,
    val manufacturer: String,
    val osVersion: String,
    /** Android 的 `Build.VERSION.SDK_INT`；非 Android 平台为 0 */
    val sdkLevel: Int,
)

expect fun currentDeviceInfo(): DeviceInfo

/**
 * 平台提供的稳定设备标识（卸载重装后保持不变）。
 *
 * - Android：`Settings.Secure.ANDROID_ID` 经应用内盐值 SHA-256 派生
 * - iOS：Keychain 中保存的随机 UUID
 * - 桌面：系统机器码派生
 * - 鸿蒙：平台可用的设备标识；取不到时退化为应用私有文件
 *
 * 返回空串表示当前平台拿不到稳定标识 —— 调用方不得再生成随机 ID 兜底。
 */
expect fun stableDeviceId(): String
