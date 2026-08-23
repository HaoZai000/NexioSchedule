package com.haooz.chedule.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

object StatsReporter {
    private const val API_URL = "http://182.92.193.223:3000/api/stats/report"
    
    private var deviceId: String? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
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

    /** 上报安装事件（仅每个设备首次上报，成功后才标记，避免重复/丢失） */
    fun reportInstallOnce(context: Context) {
        val prefs = context.getSharedPreferences("stats_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("install_reported", false)) return
        if (deviceId == null) init(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = buildPayload(context, "install")
                val body = json.toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .build()

                val resp = client.newCall(request).execute()
                if (resp.isSuccessful) {
                    prefs.edit { putBoolean("install_reported", true) }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun reportActive(context: Context) {
        if (deviceId == null) init(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = buildPayload(context, "active")
                val body = json.toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .build()

                client.newCall(request).execute()
            } catch (_: Exception) {
            }
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
