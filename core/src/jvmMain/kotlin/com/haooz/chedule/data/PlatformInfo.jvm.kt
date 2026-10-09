package com.haooz.chedule.data

/**
 * JVM / 桌面实现：用系统属性填充，供桌面调试与 SkSL 验证使用。
 *
 * `sdkLevel` 固定为 0 —— 它在上报负载里是 Android 专有字段，非 Android 平台无意义。
 * 将来 iOS 的实现同样置 0，并在 `osVersion` 放 `UIDevice.systemVersion`。
 */
actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    model = System.getProperty("os.arch") ?: "unknown",
    brand = System.getProperty("os.name") ?: "unknown",
    manufacturer = System.getProperty("java.vendor") ?: "unknown",
    osVersion = System.getProperty("os.version") ?: "unknown",
    sdkLevel = 0,
)
