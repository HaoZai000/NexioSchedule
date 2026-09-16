package com.haooz.chedule.ui.utils

import android.content.Context
import android.util.Log

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
     * 解析版本号为数字序列。
     * 格式: [v]MAJOR.MINOR.PATCH[-DATE] 或 [v]MAJOR.MINOR.PATCH.BETA[-DATE]
     * 例: 1.5.0-0905 → [1,5,0]；1.5.0.2-0905 → [1,5,0,2]
     * 日期后缀不参与比较。
     */
    fun parseVersion(raw: String): List<Int> {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        return cleaned.split('.').map { it.toIntOrNull() ?: 0 }
    }

    /** 是否 beta 版（第 4 段版本号存在） */
    fun isBetaVersion(raw: String): Boolean = parseVersion(raw).size >= 4

    /** 比较版本：remote 是否比 local 更新。忽略日期后缀。 */
    fun isNewerVersion(remote: String, local: String): Boolean {
        val r = parseVersion(remote)
        val l = parseVersion(local)
        val max = maxOf(r.size, l.size)
        for (i in 0 until max) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    // 需在 IO 线程调用。
    // stable: 正式通道，跳过 prerelease 与 beta 版本（含第4段版本号）
    // beta: 可检测正式版 + beta 版
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
                val tag = release.get("tag_name")?.asString ?: continue
                val ver = tag.removePrefix("v")

                if (channel == "stable") {
                    val isPre = release.get("prerelease")?.asBoolean ?: false
                    // 正式通道：不检测 beta（含第4段版本号的预发布）
                    if (isPre || isBetaVersion(ver)) continue
                }
                // beta 通道：正式 + beta 均可；stable 通道已在上方过滤

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

            Log.d(TAG, "检查完成: channel=$channel, hasUpdate=$hasUpdate, remote=$tagVersion, local=$appVersion")
            Pair(hasUpdate, GiteeRelease(tagName, name, body, htmlUrl, apkUrl, createdAt))
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            Pair(false, null)
        }
    }

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

}
