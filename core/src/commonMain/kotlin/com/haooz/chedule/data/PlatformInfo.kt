package com.haooz.chedule.data

import kotlin.concurrent.Volatile

/**
 * 设备与系统信息。
 *
 * 迁移前 19 个文件直接读 `android.os.Build.*` 与 `context.packageManager` —— 两者都是
 * Android 专有。这里抽成跨平台数据类，把平台差异收敛到 [currentDeviceInfo] 一个 actual。
 *
 * 字段刻意**不**做重命名（`model` / `brand` / `manufacturer` / `sdkLevel`），
 * 因为 [StatsReporter] 会把这些字段原样上报给后端，改名会破坏服务端既有统计口径。
 */
data class DeviceInfo(
    /** 对应 `Build.MODEL`，如 "Pixel 7" */
    val model: String,
    /** 对应 `Build.BRAND` */
    val brand: String,
    /** 对应 `Build.MANUFACTURER` */
    val manufacturer: String,
    /** OS 版本字符串：Android 为 `Build.VERSION.RELEASE`（如 "14"）；桌面为 `os.version` */
    val osVersion: String,
    /** Android 的 `Build.VERSION.SDK_INT`；非 Android 平台为 0 */
    val sdkLevel: Int,
)

/** 平台侧读取设备信息。 */
expect fun currentDeviceInfo(): DeviceInfo

/**
 * 应用自身的元信息。
 *
 * `appVersion` 需要 `PackageManager`（Android）或 `CFBundleShortVersionString`（iOS），
 * 无法在 commonMain 直接取，所以由各平台在启动时 [init] 一次 ——
 * 与 [AppStorage] 同样的「启动时注入」模式。
 */
object AppInfo {
    @Volatile
    private var appVersion: String = "unknown"

    fun init(version: String) {
        appVersion = version
    }

    /** 未初始化时返回 "unknown"，与迁移前 `getAppVersion` 失败时的返回值一致。 */
    val version: String get() = appVersion
}

/**
 * 生成 UUID v4 格式（8-4-4-4-12 十六进制）的随机标识。
 *
 * 迁移前用 `java.util.UUID.randomUUID()`（JVM 专有）。这里基于 `kotlin.random.Random`
 * 自行拼装，**格式与 UUID v4 完全一致**（含版本位 `4` 与变体位），
 * 因此后端若按 UUID 校验/索引不会受影响。
 *
 * 用途仅为匿名统计的设备标识，不需要密码学强度；122 位随机量足以避免碰撞。
 */
fun randomUuidV4(): String {
    val bytes = ByteArray(16)
    kotlin.random.Random.Default.nextBytes(bytes)
    // version 4
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    // variant 10xx
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

    val sb = StringBuilder(36)
    for (i in 0 until 16) {
        val v = bytes[i].toInt() and 0xFF
        sb.append(HEX_DIGITS[v ushr 4]).append(HEX_DIGITS[v and 0x0F])
        // 8-4-4-4-12：在第 4、6、8、10 字节后插入连字符
        if (i == 3 || i == 5 || i == 7 || i == 9) sb.append('-')
    }
    return sb.toString()
}

private const val HEX_DIGITS = "0123456789abcdef"
