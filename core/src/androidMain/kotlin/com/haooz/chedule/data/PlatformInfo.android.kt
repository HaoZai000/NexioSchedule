package com.haooz.chedule.data

import android.os.Build

/**
 * Android 实现：逐字对应迁移前的 `Build.*` 读取，字段含义与上报口径完全不变。
 */
actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    model = Build.MODEL,
    brand = Build.BRAND,
    manufacturer = Build.MANUFACTURER,
    osVersion = Build.VERSION.RELEASE,
    // 迁移前上报的是 Build.VERSION.SDK_INT（数字），这里保持同一语义
    sdkLevel = Build.VERSION.SDK_INT,
)
