package com.haooz.chedule.ui.utils

import android.content.Context
import android.util.Log

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
     * @param channel 更新通道，"stable" 或 "beta"；beta 通道取最新 prerelease
     * @return Pair(hasUpdate, release)，检查失败时返回 Pair(false, null)
     */
    fun checkForUpdate(context: Context, source: String = "gitee", channel: String = "stable"): Pair<Boolean, GiteeRelease?> {
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val baseUrl = if (source == "github") {
                "https://api.github.com/repos/HaoZai000/NexioSchedule/releases"
            } else {
                "https://gitee.com/api/v5/repos/com_haooz_account/hyper_schedule/releases"
            }
            val url = "$baseUrl?page=1&per_page=10&direction=desc&t=${System.currentTimeMillis()}"
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
            val arr = com.google.gson.JsonParser.parseString(responseBody).asJsonArray
            var best: com.google.gson.JsonObject? = null
            var bestVer = ""
            for (i in 0 until arr.size()) {
                val release = arr[i].asJsonObject
                if (channel == "stable") {
                    val isPre = release.get("prerelease")?.asBoolean ?: false
                    if (isPre) continue
                }
                val tag = release.get("tag_name")?.asString ?: continue
                val ver = tag.removePrefix("v")
                if (best == null || isNewerVersion(ver, bestVer)) {
                    best = release
                    bestVer = ver
                }
            }
            val json = best
            if (json == null) return Pair(false, null)
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

            val tagVersion = tagName.removePrefix("v")
            val appVersion = currentVersion.removePrefix("v")
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
         * 支持 "betaX" 后缀，如 "1.4.8beta1" < "1.4.8beta2" < "1.4.9"。
         */
        fun isNewerVersion(remote: String, local: String): Boolean {
            fun parseSegments(v: String): List<Int> {
                return v.split(".").flatMap { part ->
                    val betaIdx = part.indexOf("beta")
                    if (betaIdx >= 0) {
                        val num = part.substring(0, betaIdx).toIntOrNull() ?: 0
                        val betaNum = part.substring(betaIdx + 4).toIntOrNull() ?: 0
                        listOf(num, betaNum)
                    } else {
                        listOf(part.toIntOrNull() ?: 0)
                    }
                }
            }
            val remoteParts = parseSegments(remote)
            val localParts = parseSegments(local)
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
