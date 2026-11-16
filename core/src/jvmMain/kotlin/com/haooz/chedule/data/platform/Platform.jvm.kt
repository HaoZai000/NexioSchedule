package com.haooz.chedule.data.platform

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 桌面（JVM）侧平台实现。桌面定位是「迁移验证壳」：能真跑库与网络，不追求平台观感。
 */

actual fun <T> synchronizedOn(lock: Any, block: () -> T): T = synchronized(lock) { block() }

actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

actual fun compareByChineseCollator(a: String, b: String): Int = ChineseCollatorImpl.compare(a, b)

/** `sdkLevel` 固定 0：这是 Android 专有字段，非 Android 平台无意义。 */
actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    model = System.getProperty("os.arch") ?: "unknown",
    brand = System.getProperty("os.name") ?: "unknown",
    manufacturer = System.getProperty("java.vendor") ?: "unknown",
    osVersion = System.getProperty("os.version") ?: "unknown",
    sdkLevel = 0,
)

/**
 * 桌面端用系统机器码派生：macOS `IOPlatformUUID`、Linux `machine-id`、Windows `MachineGuid`。
 * 取不到时返回空串，不再生成随机匿名 ID。
 */
actual fun stableDeviceId(): String {
    val raw = machineId() ?: return ""
    return sha256Hex("nexio-device-v1:$raw")
}

private fun machineId(): String? {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        os.contains("mac") -> runCommand("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
            ?.lineSequence()
            ?.firstOrNull { it.contains("IOPlatformUUID") }
            ?.substringAfterLast('=')
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }

        os.contains("win") -> runCommand(
            "reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid",
        )
            ?.lineSequence()
            ?.firstOrNull { it.contains("MachineGuid") }
            ?.substringAfter("REG_SZ", "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        else -> sequenceOf("/etc/machine-id", "/var/lib/dbus/machine-id")
            .mapNotNull { path ->
                runCatching { File(path).takeIf { it.exists() }?.readText()?.trim() }.getOrNull()
            }
            .firstOrNull { it.isNotBlank() }
    }
}

private fun runCommand(vararg cmd: String): String? = runCatching {
    val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    val text = process.inputStream.bufferedReader().use { it.readText() }
    if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        null
    } else {
        text
    }
}.getOrNull()

private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
    return buildString(digest.size * 2) {
        for (b in digest) {
            val v = b.toInt() and 0xFF
            append(HEX_DIGITS[v ushr 4]).append(HEX_DIGITS[v and 0x0F])
        }
    }
}

private const val HEX_DIGITS = "0123456789abcdef"

/** 没有系统提示条，落到日志；将来要弹窗只改这一个函数，调用方不动。 */
actual fun showToast(message: String, longDuration: Boolean) {
    NexioLog.i(TAG, message)
}

private const val TAG = "Toast"
