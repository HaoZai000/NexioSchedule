package com.haooz.chedule.ui.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用更新检查工具
 *
 * 负责每日从 Gitee releases 接口检查最新版本，并写入 SharedPreferences 缓存。
 */
internal object UpdateChecker {

    private const val PREFS_NAME = "update_settings"

    /**
     * 在 IO 线程检查最新版本。每天最多触发一次网络请求。
     * 结果写入 [PREFS_NAME] SharedPreferences。
     */
    fun checkOnLaunch(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_check_update", true)) return
        val lastCheckDate = prefs.getString("last_check_date", "") ?: ""
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (lastCheckDate == today) return

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val response =
                    URL("https://gitee.com/api/v5/repos/com_haooz_account/hyper_schedule/releases/latest")
                        .readText()
                val json = JSONObject(response)
                val tagName = json.getString("tag_name")
                val currentVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
                } catch (_: Exception) {
                    ""
                }
                val tagVer = tagName.removePrefix("v").substringBefore("-")
                val appVer = currentVersion.removePrefix("v").substringBefore("-")
                val hasUpdate = isNewerVersion(tagVer, appVer)
                prefs.edit()
                    .putString("last_check_date", today)
                    .putBoolean("has_update", hasUpdate)
                    .apply()
                if (hasUpdate) {
                    var apkUrl = ""
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            if (a.getString("name").endsWith(".apk")) {
                                apkUrl = a.getString("browser_download_url"); break
                            }
                        }
                    }
                    prefs.edit()
                        .putString("latest_url", json.getString("html_url"))
                        .putString("latest_tag", tagName)
                        .putString("latest_name", json.getString("name"))
                        .putString("latest_body", json.optString("body", ""))
                        .putString("latest_apk_url", apkUrl)
                        .putString("latest_date", json.getString("created_at"))
                        .apply()
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 比较版本号字符串，返回 [remote] 是否比 [local] 更新。
     * 例如 "1.2.3" > "1.2.2"。
     */
    fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }
        val maxSize = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxSize) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
