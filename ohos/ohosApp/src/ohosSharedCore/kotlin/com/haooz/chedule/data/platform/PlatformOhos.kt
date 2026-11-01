/**
 * `:core` 平台抽象在鸿蒙端的实现。
 *
 * `core/src/commonMain/.../data/platform/Platform.kt` 里那 7 个 `expect` 是 commonMain
 * 唯一的平台分叉点，这里逐个给 actual。**语义必须与 iOS/桌面侧保持同类**：
 * Native 上没有 `Dispatchers.IO`、没有监视器锁、没有系统 Toast，就照 iOS 的降级口径办。
 */
package com.haooz.chedule.data.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 跨平台互斥。
 *
 * ⚠ 这里是**直通**，不是真锁（iOS 用 NSRecursiveLock）。鸿蒙端 Native 侧暂时没有可用的
 * 递归锁原语，只要当前没有并发读写同一份课表就等价。接存储层并发写入前必须补上，
 * 否则 `HolidayManager` 的「读 → 算 → 写」会被交错。
 */
actual fun <T> synchronizedOn(lock: Any, block: () -> T): T = block()

/**
 * 鸿蒙端 `ioDispatcher` 退回**当前线程**。
 *
 * 用 `Dispatchers.Default` 会让 `launch(ioDispatcher)` / `withContext(ioDispatcher)` 的续体
 * 在 FFRT worker 上恢复，而鸿蒙的 `Dispatchers.Main` 没初始化（`runOnMainThread` 直接内联），
 * 于是 UI 协程在 worker 线程上继续跑、写 Compose 状态，主线程和它抢 Compose 的
 * `SynchronizedObject`，两个线程一起卡死（`THREAD_BLOCK_6S`）。
 *
 * 代价是 IO 走 UI 线程：鸿蒙端 HTTP 是 503 空实现、DB 是本地 sqlite 小读写，暂时可接受。
 */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

/** 中文排序降级为码点比较 —— 与 iOS 侧同一个降级，不是等价实现。 */
actual fun compareByChineseCollator(a: String, b: String): Int = a.compareTo(b)

/** 系统提示条。宿主可用 `toastHandler` 注入（ArkTS 侧想接 promptAction 就注进来）。 */
actual fun showToast(message: String, longDuration: Boolean) {
    toastHandler?.invoke(message, longDuration)
}

/**
 * 设备信息。字段名是上报契约（`StatsReporter` 原样发服务端），不许改名。
 *
 * 真实型号要走 ArkTS 桥（`deviceInfo`），这里先给合规兜底值：
 * `sdkLevel = 0` 在 core 里表示「非 Android」，与桌面端一致。
 */
actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    model = "HarmonyOS",
    brand = "Huawei",
    manufacturer = "Huawei",
    osVersion = "6.0",
    sdkLevel = 0,
)

/** 日志落 stdout（`hdc shell hilog` 能看到）。等级门槛由 core 的 `emit` 统一把关。 */
actual fun platformLog(level: Int, tag: String, message: String, throwable: Throwable?) {
    val levelName = when (level) {
        LOG_LEVEL_VERBOSE -> "V"
        LOG_LEVEL_DEBUG -> "D"
        LOG_LEVEL_INFO -> "I"
        LOG_LEVEL_WARN -> "W"
        else -> "E"
    }
    println("$levelName/$tag: $message")
    throwable?.printStackTrace()
}
