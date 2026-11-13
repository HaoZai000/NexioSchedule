package com.haooz.chedule.data.platform

import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.util.jsonObjectOf
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * 匿名统计上报。
 *
 * ⚠ 上报负载的字段名（`device_id` / `event_type` / `device_model` / `android_version`
 * / `sdk_level` …）是服务端既有统计口径，一个字都不能改。
 */
object StatsReporter {
    private const val TAG = "StatsReporter"
    // 上报走明文 HTTP：HTTPS(443) 在该运营商网络下 TLS 握手被干扰（Connection reset），改用 3000 直达后端
    private const val API_URL = "http://182.92.193.223:3000/api/stats/report"
    // 弱网下偶发连接失败，最多重试 3 次（带指数退避）
    private const val MAX_RETRY = 3

    private const val PREFS = "stats_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_INSTALL_REPORTED = "install_reported"

    // 超时 connect 10s / read 10s / call 15s
    /** 可替换（`internal var`）以便 commonTest 注入 FakeHttpService，覆盖重试/退避分支。 */
    internal var http: HttpService = createHttpService(
        HttpTimeouts(connectSeconds = 10, readSeconds = 10, callSeconds = 15),
    )

    @Volatile
    private var deviceId: String? = null

    /**
     * 平台标签：服务端按此维度分平台统计。
     * `sdkLevel` 是 Android 专有字段（>0 即 Android）；鸿蒙端 brand 固定为 "HarmonyOS"；
     * Apple 体系是 iOS；其余归桌面。老版 iOS 客户端 brand 是 osFamily 名（"IOS"）也要能归进 iOS。
     */
    internal fun platformTag(device: DeviceInfo): String = when {
        device.brand.equals("HarmonyOS", ignoreCase = true) ||
            device.brand.equals("Harmony", ignoreCase = true) ||
            device.manufacturer.equals("HarmonyOS", ignoreCase = true) ||
            device.model.equals("HarmonyOS", ignoreCase = true) -> "harmony"
        device.sdkLevel > 0 -> "android"
        device.brand.equals("Apple", ignoreCase = true) ||
            device.brand.equals("IOS", ignoreCase = true) -> "ios"
        else -> "desktop"
    }

    /**
     * 仅供测试：清空内存缓存，模拟进程冷启动。`deviceId` 会跨测试用例残留，
     * 而 [AppStorage] 在测试里会被重设为新的内存实现，不同步会让「已落盘」断言假失败。
     */
    internal fun resetForTest() {
        deviceId = null
    }

    /**
     * 读取（或首次生成并落盘）设备标识。
     *
     * 与统计上报是同一个 ID —— 服务端按它算每台设备每天的 AI 用量，
     * 两边各生成一份的话限额就形同虚设。
     */
    internal fun ensureDeviceId(): String {
        deviceId?.let { return it }
        val prefs = AppStorage.store(PREFS)
        val id = prefs.getString(KEY_DEVICE_ID, "").ifBlank {
            randomUuidV4().also { generated ->
                prefs.edit { putString(KEY_DEVICE_ID, generated) }
            }
        }
        deviceId = id
        return id
    }

    /** 组装上报负载：设备ID、事件类型、时间戳 + 设备/系统/App 信息。
     *  `internal` 而非 private：字段名是服务端既有统计口径，用测试锁住。
     *  `platform` 是新增维度（android / ios / desktop），服务端不认识时会忽略，不影响旧口径。 */
    internal fun buildPayload(eventType: String): String {
        val device = currentDeviceInfo()
        return jsonObjectOf(
            "device_id" to ensureDeviceId(),
            "event_type" to eventType,
            "timestamp" to Clock.System.now().toEpochMilliseconds(),
            "app_version" to AppInfo.version,
            "device_model" to device.model,
            "brand" to device.brand,
            "manufacturer" to device.manufacturer,
            "android_version" to device.osVersion,
            "sdk_level" to device.sdkLevel,
            "platform" to platformTag(device),
        ).toString()
    }

    /**
     * 带退避的执行一次上报。返回 true 表示成功。
     * 网络抖动（IO 异常、非 2xx）时重试，指数退避：1s、2s、4s。
     */
    internal suspend fun postWithRetry(json: String): Boolean {
        var last: Exception? = null
        repeat(MAX_RETRY) { attempt ->
            try {
                val resp = http.post(API_URL, json)
                NexioLog.i(
                    TAG,
                    "上报响应(${attempt + 1}/$MAX_RETRY): code=${resp.code} successful=${resp.isSuccessful}",
                )
                if (resp.isSuccessful) return true
                last = IllegalStateException("HTTP ${resp.code}")
            } catch (e: Exception) {
                last = e
                NexioLog.w(TAG, "上报失败(第${attempt + 1}次): ${e.message}")
            }
            if (attempt < MAX_RETRY - 1) delay((1L shl attempt) * 1000L)
        }
        NexioLog.e(TAG, "上报重试 $MAX_RETRY 次后仍失败", last)
        return false
    }

    /**
     * 上报安装事件的实际逻辑（可挂起、可测）。
     *
     * ⚠ 只有上报成功才落盘标记，否则下次启动会重试 —— 反过来（先标记后上报）会永久丢失安装量。
     *
     * @return 是否成功上报
     */
    internal suspend fun reportInstallOnceSuspending(): Boolean {
        val prefs = AppStorage.store(PREFS)
        if (prefs.getBoolean(KEY_INSTALL_REPORTED, false)) return false

        val json = buildPayload("install")
        NexioLog.i(TAG, "上报 install: payload=$json")
        val ok = postWithRetry(json)
        if (ok) prefs.edit { putBoolean(KEY_INSTALL_REPORTED, true) }
        return ok
    }

    /** 上报安装事件（仅每个设备首次上报，成功后才标记，避免重复/丢失） */
    fun reportInstallOnce() {
        if (AppStorage.store(PREFS).getBoolean(KEY_INSTALL_REPORTED, false)) return
        CoroutineScope(ioDispatcher).launch { reportInstallOnceSuspending() }
    }

    internal suspend fun reportActiveSuspending(): Boolean {
        val json = buildPayload("active")
        NexioLog.i(TAG, "上报 active: payload=$json")
        return postWithRetry(json)
    }

    fun reportActive() {
        CoroutineScope(ioDispatcher).launch { reportActiveSuspending() }
    }
}
