package com.haooz.chedule.ui.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用更新检查工具
 *
 * 负责从 Gitee releases 接口检查最新版本。
 */
internal object UpdateChecker {

    private const val TAG = "UpdateChecker"

    data class GiteeRelease(
        val tagName: String,
        val name: String,
        val body: String,
        val htmlUrl: String,
        val apkUrl: String,
        val createdAt: String
    )

    /**
     * 检查是否有新版本。需在 IO 线程调用。
     * @param source 下载源，"gitee" 或 "github"
     * @return Pair(hasUpdate, release)，检查失败时返回 Pair(false, null)
     */
    fun checkForUpdate(context: Context, source: String = "gitee"): Pair<Boolean, GiteeRelease?> {
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val baseUrl = if (source == "github") {
                "https://api.github.com/repos/HaoZai000/NexioSchedule/releases/latest"
            } else {
                "https://gitee.com/api/v5/repos/com_haooz_account/hyper_schedule/releases/latest"
            }
            val url = "$baseUrl?t=${System.currentTimeMillis()}"
            val request = okhttp3.Request.Builder().url(url).apply {
                if (source == "github") {
                    header("Accept", "application/vnd.github.v3+json")
                }
            }.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP ${response.code}")
                return Pair(false, null)
            }

            val responseBody = response.body?.string() ?: return Pair(false, null)
            val json = com.google.gson.JsonParser.parseString(responseBody).asJsonObject
            val tagName = json.get("tag_name")?.asString ?: ""
            val name = json.get("name")?.asString ?: ""
            val body = json.get("body")?.asString ?: ""
            val htmlUrl = json.get("html_url")?.asString ?: ""
            val createdAt = json.get("created_at")?.asString ?: ""

            val assets = json.getAsJsonArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (i in 0 until assets.size()) {
                    val a = assets[i].asJsonObject
                    val assetName = a.get("name")?.asString ?: ""
                    if (assetName.endsWith(".apk")) {
                        apkUrl = a.get("browser_download_url")?.asString ?: ""
                        break
                    }
                }
            }

            val currentVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (_: Exception) { "" }

            val tagVersion = tagName.removePrefix("v").substringBefore("-")
            val appVersion = currentVersion.removePrefix("v").substringBefore("-")
            val hasUpdate = isNewerVersion(tagVersion, appVersion)

            Log.d(TAG, "检查完成: hasUpdate=$hasUpdate, remote=$tagVersion, local=$appVersion")
            Pair(hasUpdate, GiteeRelease(tagName, name, body, htmlUrl, apkUrl, createdAt))
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            Pair(false, null)
        }
    }

    /**
     * 清理旧版本的APK文件，只保留指定版本的文件。
     */
    fun cleanOldApks(context: Context, keepTag: String) {
        try {
            val filesDir = context.filesDir
            val prefix = "update-"
            val suffix = ".apk"
            filesDir.listFiles()?.forEach { file ->
                val name = file.name
                if (name.startsWith(prefix) && name.endsWith(suffix)) {
                    val tag = name.removePrefix(prefix).removeSuffix(suffix)
                    if (tag != keepTag) {
                        if (file.delete()) {
                            Log.d(TAG, "已清理旧APK: $name")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理旧APK失败", e)
        }
    }

    /**
     * 比较版本号字符串，返回 [remote] 是否比 [local] 更新。
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
