package com.haooz.chedule.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Log
import java.util.UUID

object StatsReporter {
    private const val TAG = "StatsReporter"
    // 上报走明文 HTTP：HTTPS(443) 在该运营商网络下 TLS 握手被干扰（Connection reset），改用 3000 直达后端
    private const val API_URL = "http://182.92.193.223:3000/api/stats/report"
    // 弱网下偶发连接失败，最多重试 3 次（带指数退避）
    private const val MAX_RETRY = 3

    private var deviceId: String? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("stats_prefs", Context.MODE_PRIVATE)
        deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString("device_id", it) }
        }
    }

    /** 组装上报负载：设备ID、事件类型、时间戳 + 设备/系统/App 信息 */
    private fun buildPayload(context: Context, eventType: String): JSONObject {
        return JSONObject().apply {
            put("device_id", deviceId)
            put("event_type", eventType)
            put("timestamp", System.currentTimeMillis())
            put("app_version", getAppVersion(context))
            put("device_model", Build.MODEL)
            put("brand", Build.BRAND)
            put("manufacturer", Build.MANUFACTURER)
            put("android_version", Build.VERSION.RELEASE)
            put("sdk_level", Build.VERSION.SDK_INT)
        }
    }

    /**
     * 带退避的执行一次上报。返回 true 表示成功。
     * 网络抖动（IO 异常、非 2xx）时重试，指数退避：1s、2s、4s。
     */
    private suspend fun postWithRetry(json: JSONObject): Boolean {
        var last: Exception? = null
        repeat(MAX_RETRY) { attempt ->
            try {
                val resp = withContext(Dispatchers.IO) {
                    val body = json.toString()
                        .toRequestBody("application/json".toMediaType())
                    val request = Request.Builder().url(API_URL).post(body).build()
                    client.newCall(request).execute()
                }
                Log.i(TAG, "上报响应(${attempt + 1}/$MAX_RETRY): code=${resp.code} successful=${resp.isSuccessful}")
                if (resp.isSuccessful) return true
                last = IllegalStateException("HTTP ${resp.code}")
            } catch (e: Exception) {
                last = e
                Log.w(TAG, "上报失败(第${attempt + 1}次): ${e.message}")
            }
            if (attempt < MAX_RETRY - 1) delay((1L shl attempt) * 1000L)
        }
        Log.e(TAG, "上报重试 $MAX_RETRY 次后仍失败", last)
        return false
    }

    /** 上报安装事件（仅每个设备首次上报，成功后才标记，避免重复/丢失） */
    fun reportInstallOnce(context: Context) {
        val prefs = context.getSharedPreferences("stats_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("install_reported", false)) return
        if (deviceId == null) init(context)

        CoroutineScope(Dispatchers.IO).launch {
            val json = buildPayload(context, "install")
            Log.i(TAG, "上报 install: payload=${json}")
            if (postWithRetry(json)) {
                prefs.edit { putBoolean("install_reported", true) }
            }
        }
    }

    fun reportActive(context: Context) {
        if (deviceId == null) init(context)

        CoroutineScope(Dispatchers.IO).launch {
            val json = buildPayload(context, "active")
            Log.i(TAG, "上报 active: payload=${json}")
            postWithRetry(json)
        }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
