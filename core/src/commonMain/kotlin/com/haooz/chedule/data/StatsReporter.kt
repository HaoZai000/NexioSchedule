package com.haooz.chedule.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.concurrent.Volatile

/**
 * 匿名统计上报。
 *
 * ## 迁移状态：四个 Android 依赖已全部解除，可下沉 `:core`
 *
 * | 原依赖 | 现状 |
 * |---|---|
 * | `Context.getSharedPreferences` | ✅ [AppStorage] |
 * | `OkHttpClient` | ✅ [HttpService]，超时仍是 connect 10s / read 10s / call 15s |
 * | `org.json.JSONObject` | ✅ [jsonObjectOf] |
 * | `Build.*` + `packageManager` | ✅ [currentDeviceInfo] / [AppInfo] |
 * | `java.util.UUID` | ✅ [randomUuidV4]，格式与 UUID v4 一致 |
 *
 * ⚠ 上报负载的字段名（`device_id` / `event_type` / `device_model`
 * / `android_version` / `sdk_level` …）是**服务端既有统计口径**，一个字都不能改。
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

    // 超时与迁移前一致
    /** 可替换（`internal var`）以便 commonTest 注入 FakeHttpService，覆盖重试/退避分支。 */
    internal var http: HttpService = createHttpService(
        HttpTimeouts(connectSeconds = 10, readSeconds = 10, callSeconds = 15),
    )

    @Volatile
    private var deviceId: String? = null

    /**
     * 仅供测试：清空内存缓存，模拟「进程冷启动」。
     *
     * 有必要暴露它是因为 [StatsReporter] 是全局 object，`deviceId` 会跨测试用例残留；
     * 而 [AppStorage] 在测试里会被重设为新的内存实现，两者不同步时
     * 「device_id 已落盘」这类断言会假失败。
     */
    internal fun resetForTest() {
        deviceId = null
    }

    /** 读取（或首次生成并落盘）设备标识。 */
    private fun ensureDeviceId(): String {
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
     *  `internal` 而非 private：字段名是**服务端既有统计口径**，用测试锁住。 */
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
     * 关键契约：**只有上报成功才落盘标记**，否则下次启动会重试 ——
     * 反过来（先标记后上报）会永久丢失安装量，是先标记先上报的经典陷阱。
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
