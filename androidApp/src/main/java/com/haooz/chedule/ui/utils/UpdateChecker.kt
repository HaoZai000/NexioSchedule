package com.haooz.chedule.ui.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.NexioLog
import com.haooz.chedule.data.getStringOrNull
import androidx.core.content.edit
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

/**
 * 更新 APK 的校验与清理入口。
 *
 * 清理策略与 [UpdateInstaller] 下载路径对齐，按 **tag 有效性** 保留，而不是「按修改时间只留最新」：
 * - 目标 tag（update_settings.latest_tag）且包体完整 → 保留
 * - 其它 tag 的 update-*.apk → 删除
 * - 不完整/半成品（含 .part）→ 删除，即使它 mtime 最新
 * - 没有 latest_tag 时：至多保留一个完整 APK，同样先丢掉半成品
 *
 * 「完整」的判据见 [verifyApk]：必须真的能被 PackageManager 解析，而不是只看 ZIP 魔数。
 */
internal object UpdateChecker {

    private const val TAG = "UpdateChecker"

    private const val PREF_UPDATE = "update_settings"
    private const val KEY_LATEST_TAG = "latest_tag"
    private const val KEY_LATEST_APK_URL = "latest_apk_url"
    private const val KEY_SHA_PREFIX = "apk_sha256_"
    private const val KEY_SIZE_PREFIX = "apk_size_"
    private const val APK_PREFIX = "update-"
    private const val APK_SUFFIX = ".apk"
    private const val PART_SUFFIX = ".part"
    private const val MIN_COMPLETE_APK_BYTES = 512L * 1024L

    data class GiteeRelease(
        val tagName: String,
        val name: String,
        val body: String,
        val htmlUrl: String,
        val apkUrl: String,
        val createdAt: String,
        /** Release 资产下发的 SHA-256（GitHub 为 digest="sha256:<hex>"）；拿不到时为 null */
        val apkSha256: String? = null,
        /** Release 资产声明的字节数 */
        val apkSize: Long? = null
    )

    /** 校验结论；[reason] 用于日志与用户提示，仅在失败时有值 */
    data class ApkCheck(val ok: Boolean, val reason: String? = null)

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
                NexioLog.e(TAG, "HTTP ${response.code}")
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
            var apkSha256: String? = null
            var apkSize: Long? = null
            if (assets != null) {
                for (i in 0 until assets.size()) {
                    val a = assets[i].asJsonObject
                    val assetName = a.get("name")?.asString ?: ""
                    if (assetName.endsWith(".apk")) {
                        apkUrl = a.get("browser_download_url")?.asString ?: ""
                        // GitHub 资产下发 digest（"sha256:<hex>"），部分平台直接给 sha256；都没有就退化为解析级校验
                        val digest = a.get("digest")?.takeIf { !it.isJsonNull }?.asString
                            ?: a.get("sha256")?.takeIf { !it.isJsonNull }?.asString
                        apkSha256 = digest
                            ?.replace("sha256:", "", ignoreCase = true)
                            ?.trim()
                            ?.takeIf { it.length == 64 }
                        apkSize = a.get("size")?.takeIf { !it.isJsonNull }?.asLong
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

            NexioLog.d(TAG, "检查完成: channel=$channel, hasUpdate=$hasUpdate, remote=$tagVersion, local=$appVersion")
            Pair(hasUpdate, GiteeRelease(tagName, name, body, htmlUrl, apkUrl, createdAt, apkSha256, apkSize))
        } catch (e: Exception) {
            NexioLog.e(TAG, "检查更新失败", e)
            Pair(false, null)
        }
    }

    /**
     * 把一次检查到的 release 落到 update_settings 缓存（弹窗与设置页共用同一份）。
     *
     * 这里**原样缓存** apkUrl，即使它为空：release 先创建、APK 资产后上传时，
     * 当天缓存下来的会是空串。此时不写缓存反而会让弹窗拿到的是别的 tag，
     * 那比空串更危险（会把别的版本下到当前 tag 的文件名下）。
     * 空值由 [resolveApkUrl] 在下载时回源补齐。
     */
    fun persistRelease(context: Context, release: GiteeRelease) {
        AppStorage.store(PREF_UPDATE).edit {
            putString("latest_url", release.htmlUrl)
            putString(KEY_LATEST_APK_URL, release.apkUrl)
            putString(KEY_LATEST_TAG, release.tagName)
            putString("latest_name", release.name)
            putString("latest_body", release.body)
            putString("latest_date", release.createdAt)
        }
        if (release.apkUrl.isBlank()) {
            NexioLog.w(TAG, "release ${release.tagName} 未下发 APK 资产，下载时回源重查")
        }
    }

    /**
     * 解析 [tag] 对应的 APK 下载地址，解析不到返回 null。
     *
     * 缓存优先，但**不接受缓存里的空值**。`last_check_date` 会把一次失败的检查
     * 缓存一整天，若 release 当时还没挂上 APK 资产，缓存里就是空串；
     * 弹窗直接放弃的话会一直报「未找到下载链接」，而进「设置 - 应用更新」
     * 重新检查反而能下——两条路径行为不一致。
     *
     * 缓存不可用时回源重查（两种通道各试一次，因为 tag 可能来自另一通道），
     * 命中即回写缓存，让后续进入设置页时也拿到同一个地址。
     *
     * 缓存里的 tag 与 [tag] 不一致时**不复用**那条链接，否则会把别的版本
     * 下到 [tag] 的文件名下。
     *
     * 需在 IO 线程调用。
     */
    fun resolveApkUrl(
        context: Context,
        tag: String,
        source: String,
        channel: String,
    ): String? {
        if (tag.isBlank()) return null
        val prefs = AppStorage.store(PREF_UPDATE)
        // 键可能不存在 → 用 getStringOrNull（KeyValueStore.getString 收非空默认值）
        val cachedTag = prefs.getStringOrNull(KEY_LATEST_TAG)
        val cachedUrl = prefs.getStringOrNull(KEY_LATEST_APK_URL)?.takeIf { it.isNotBlank() }
        if (cachedUrl != null && cachedTag == tag) return cachedUrl

        val channels = if (channel == "beta") listOf("beta", "stable") else listOf(channel, "beta")
        for (ch in channels.distinct()) {
            val release = checkForUpdate(context, source, ch).second ?: continue
            if (release.tagName != tag || release.apkUrl.isBlank()) continue
            persistRelease(context, release)
            NexioLog.d(TAG, "回源解析到下载地址: ${release.tagName} channel=$ch source=$source")
            return release.apkUrl
        }
        NexioLog.w(TAG, "未解析到 $tag 的下载地址（缓存与回源均失败）source=$source")
        return null
    }

    /** 当前待安装目标 tag；无则 null */
    fun currentKeepTag(context: Context): String? {
        return AppStorage.store(PREF_UPDATE)
            .getStringOrNull(KEY_LATEST_TAG)
            ?.takeIf { it.isNotBlank() }
    }

    /** 记录该 tag 安装包的期望校验值，供后续复核使用 */
    fun rememberApkDigest(context: Context, tag: String, sha256: String?, sizeBytes: Long?) {
        val shaKey = KEY_SHA_PREFIX + tag
        val sizeKey = KEY_SIZE_PREFIX + tag
        AppStorage.store(PREF_UPDATE).edit {
            if (sha256.isNullOrBlank()) remove(shaKey) else putString(shaKey, sha256)
            if (sizeBytes == null || sizeBytes <= 0L) remove(sizeKey) else putLong(sizeKey, sizeBytes)
        }
    }

    fun rememberedApkSha256(context: Context, tag: String): String? =
        AppStorage.store(PREF_UPDATE)
            .getStringOrNull(KEY_SHA_PREFIX + tag)
            ?.takeIf { it.isNotBlank() }

    fun rememberedApkSize(context: Context, tag: String): Long? =
        AppStorage.store(PREF_UPDATE)
            .getLong(KEY_SIZE_PREFIX + tag, -1L)
            .takeIf { it > 0L }

    /** 流式计算文件 SHA-256（大文件避免一次性读入内存） */
    fun sha256(file: File): String? = try {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        NexioLog.e(TAG, "SHA-256 计算失败: ${file.name}", e)
        null
    }

    /**
     * 包体是否像完整 APK：ZIP 魔数 + 最小体积。
     *
     * 仅作为**廉价前置筛选**，不能单独作为「可安装」判据——被截断的包只要保留了
     * 文件头就能通过。真正的判定见 [verifyApk]。
     */
    fun isLikelyCompleteApk(file: File): Boolean {
        if (!file.isFile) return false
        if (file.length() < MIN_COMPLETE_APK_BYTES) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 4L) return false
                val header = ByteArray(4)
                raf.readFully(header)
                // ZIP local file header: PK\x03\x04
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 安装包完整性校验：能不能安全交给系统安装器。
     *
     * 分层校验，任一层失败即判定不可用，并给出可展示的原因：
     * 1. 体积 + ZIP 魔数：挡住明显的半成品/空文件
     * 2. ZIP 中央目录 + AndroidManifest.xml：挡住截断包与「HTML 错误页伪装成 APK」
     * 3. PackageManager 解析归档：与系统安装器同一套解析逻辑，能提前复现「解析失败」
     * 4. 包名一致、versionCode 递增：挡住张冠李戴的包
     * 5. 签名证书与已安装应用一致：挡住签名不符导致的安装失败
     * 6. （可选）服务端下发的 SHA-256：字节级校验
     *
     * 需在 IO 线程调用。
     */
    fun verifyApk(context: Context, file: File, expectedSha256: String? = null): ApkCheck {
        if (!isLikelyCompleteApk(file)) {
            return ApkCheck(false, "安装包不完整或已损坏")
        }

        // 截断包通常保留文件头但缺少中央目录，这里先卡一道
        if (!hasZipCentralDirectory(file)) {
            return ApkCheck(false, "安装包不完整（中央目录损坏）")
        }

        val pm = context.packageManager
        val archive = try {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (e: Exception) {
            NexioLog.e(TAG, "解析安装包异常: ${file.name}", e)
            return ApkCheck(false, "安装包解析失败：${e.message ?: e.javaClass.simpleName}")
        } ?: return ApkCheck(false, "安装包解析失败：包体损坏")

        if (archive.packageName != context.packageName) {
            return ApkCheck(false, "包名不匹配：${archive.packageName}")
        }

        val installed = runCatching {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull()
        if (!sameSigner(installed, archive)) {
            return ApkCheck(false, "签名与已安装应用不一致")
        }

        val installedCode = installed?.let { ApiCompat.longVersionCode(it) } ?: -1L
        val archiveCode = ApiCompat.longVersionCode(archive)
        if (installedCode >= 0L && archiveCode <= installedCode) {
            return ApkCheck(false, "安装包版本($archiveCode)不高于当前($installedCode)")
        }

        expectedSha256?.takeIf { it.isNotBlank() }?.let { want ->
            val actual = sha256(file) ?: return ApkCheck(false, "校验值计算失败")
            if (!actual.equals(want.trim(), ignoreCase = true)) {
                return ApkCheck(false, "校验值不匹配，文件可能已损坏")
            }
        }

        return ApkCheck(true)
    }

    /** ZIP 中央目录可读且含 AndroidManifest.xml */
    private fun hasZipCentralDirectory(file: File): Boolean = try {
        ZipFile(file).use { zip ->
            zip.getEntry("AndroidManifest.xml") != null && zip.entries().hasMoreElements()
        }
    } catch (_: Exception) {
        false
    }

    /** 签名一致性：优先比对历史签名链，兼容签名轮换 */
    private fun sameSigner(installed: PackageInfo?, archive: PackageInfo?): Boolean {
        val installedCerts = signingCerts(installed)
        val archiveCerts = signingCerts(archive)
        if (installedCerts == null || archiveCerts == null) return true // 拿不到就放行，交给系统安装器裁决
        return installedCerts.any { archiveCerts.contains(it) }
    }

    private fun signingCerts(info: PackageInfo?): List<String>? {
        val signatures: Array<Signature>? = if (ApiCompat.isSigningInfoAvailable) {
            val signingInfo = info?.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info?.signatures
        }
        return signatures
            ?.map { sig -> sig.toByteArray().toHexString() }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    fun apkTagOrNull(fileName: String): String? {
        if (!fileName.startsWith(APK_PREFIX) || !fileName.endsWith(APK_SUFFIX)) return null
        return fileName.removePrefix(APK_PREFIX).removeSuffix(APK_SUFFIX)
    }

    /**
     * 按 tag 清理 filesDir 下的更新包。
     * [keepTag] 为目标版本；null/空则不按 tag 保，只保证「不留下半成品、至多一个完整包」。
     */
    fun cleanOldApks(context: Context, keepTag: String?) {
        try {
            val keep = keepTag?.takeIf { it.isNotBlank() }
            val filesDir = context.filesDir
            val candidates = filesDir.listFiles()?.filter { file ->
                file.isFile && file.name.startsWith(APK_PREFIX) &&
                    (file.name.endsWith(APK_SUFFIX) || file.name.endsWith(PART_SUFFIX))
            } ?: return

            var keptComplete = false
            for (file in candidates) {
                val name = file.name
                val isPart = name.endsWith(PART_SUFFIX)
                val tag = if (isPart) null else apkTagOrNull(name)

                if (isPart) {
                    if (file.delete()) NexioLog.d(TAG, "清理下载中间态: $name")
                    continue
                }

                // 用完整校验（而非仅 ZIP 魔数）判定，损坏包必须删掉，
                // 否则会被下一轮 hasValidApk 反复当成「已下载」继续复用
                val expectedSha = if (tag != null) rememberedApkSha256(context, tag) else null
                val check = verifyApk(context, file, expectedSha)
                val complete = check.ok
                if (!complete) {
                    NexioLog.w(TAG, "清理损坏APK: $name 原因=${check.reason}")
                }
                val shouldKeep = when {
                    keep != null && tag == keep && complete -> true
                    keep != null -> false
                    complete && !keptComplete -> true
                    else -> false
                }
                if (shouldKeep) {
                    keptComplete = true
                    continue
                }
                if (file.delete()) {
                    NexioLog.d(TAG, "已清理APK: $name complete=$complete keepTag=$keep")
                }
            }
        } catch (e: Exception) {
            NexioLog.e(TAG, "清理旧APK失败", e)
        }
    }

    /**
     * 启动 / 通用清理入口：与检查更新后的 cleanOldApks 同一策略。
     * 不再「按 mtime 只留最新」，避免半成品挤掉完好旧包。
     */
    fun cleanupTransientApks(context: Context) {
        cleanOldApks(context, currentKeepTag(context))
    }
}
