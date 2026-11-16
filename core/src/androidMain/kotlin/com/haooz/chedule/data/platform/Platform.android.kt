package com.haooz.chedule.data.platform

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Android 侧平台实现。五条 `actual` 全部与迁移前逐字等价 —— 这里只做接线，不改行为。
 */

actual fun <T> synchronizedOn(lock: Any, block: () -> T): T = synchronized(lock) { block() }

actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

actual fun compareByChineseCollator(a: String, b: String): Int = ChineseCollatorImpl.compare(a, b)

actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    model = Build.MODEL,
    brand = Build.BRAND,
    manufacturer = Build.MANUFACTURER,
    osVersion = Build.VERSION.RELEASE,
    sdkLevel = Build.VERSION.SDK_INT,
)

private var deviceContext: Context? = null

/** 必须在 `Application.onCreate` 里调用一次，[stableDeviceId] 才能读到 `ANDROID_ID`。 */
fun initDeviceIdentity(context: Context) {
    deviceContext = context.applicationContext
}

/**
 * `ANDROID_ID` 派生。
 *
 * Android 8+ 上它按「设备 + 用户 + 应用签名」生成，卸载重装不变；
 * 恢复出厂、更换签名后会变。加应用内盐值做 SHA-256，服务端拿不到原始 ID。
 */
actual fun stableDeviceId(): String {
    val context = deviceContext
    if (context == null) {
        NexioLog.w(TAG, "initDeviceIdentity 未调用，无法获取 ANDROID_ID")
        return ""
    }
    val androidId = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull()
    if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") {
        NexioLog.w(TAG, "ANDROID_ID 不可用")
        return ""
    }
    return sha256Hex("nexio-device-v1:$androidId")
}

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

/**
 * Toast 宿主。`:core` 不能反向依赖 `android.app.Application`，Context 由宿主注入
 * （同 `AppStorage.init` / `AppFiles.init` / `AppInfo.init`）。
 */
private var toastHost: ((message: String, longDuration: Boolean) -> Unit)? = null

/** 必须在 `Application.onCreate` 里调用一次，之后 [com.haooz.chedule.data.platform.showToast] 才弹得出来。 */
fun initToast(host: (message: String, longDuration: Boolean) -> Unit) {
    toastHost = host
}

actual fun showToast(message: String, longDuration: Boolean) {
    val host = toastHost
    if (host == null) {
        // 未注入是宿主接线漏了，不是业务错误：记一笔、丢弃，绝不能让「提示用户」把应用搞崩
        NexioLog.w(TAG, "showToast 未注入，丢弃提示：$message")
        return
    }
    host(message, longDuration)
}

private const val TAG = "Platform"
