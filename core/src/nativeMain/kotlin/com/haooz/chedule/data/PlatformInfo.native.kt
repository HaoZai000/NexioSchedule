package com.haooz.chedule.data

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * Kotlin/Native 实现（linuxX64 门禁目标；将来 iOS 共用本文件）。
 *
 * 用 `kotlin.native.Platform` 的 osFamily / cpuArchitecture 填充 —— 这是 Native 侧
 * 唯一无需平台框架即可取得的环境信息。
 *
 * ⚠ 接 iOS 时**应当覆盖本文件**（或为 apple 目标单开源集），改用：
 * - `model` / `brand` / `manufacturer` → `UIDevice.currentDevice`（注意需在主线程）
 * - `osVersion` → `UIDevice.currentDevice.systemVersion`
 * `sdkLevel` 在非 Android 平台恒为 0（该字段是 Android SDK_INT 的语义）。
 */
@OptIn(ExperimentalNativeApi::class)
actual fun currentDeviceInfo(): DeviceInfo {
    val os = Platform.osFamily.name
    return DeviceInfo(
        model = Platform.cpuArchitecture.name,
        brand = os,
        manufacturer = os,
        osVersion = os,
        sdkLevel = 0,
    )
}
