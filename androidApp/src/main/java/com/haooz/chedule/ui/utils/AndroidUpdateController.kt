package com.haooz.chedule.ui.utils

import android.content.Context
import com.haooz.chedule.data.platform.UpdateController
import com.haooz.chedule.data.platform.UpdateRelease
import com.haooz.chedule.i18n.CommonStr
import java.io.File

/** 将发布信息转换为 [UpdateRelease]，在安装器与共享接口之间转换 APK 文件路径。 */
class AndroidUpdateController(private val context: Context) : UpdateController {

    override val currentVersion: String
        get() = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: CommonStr.unknown()
        } catch (_: Exception) {
            CommonStr.unknown()
        }

    override fun checkForUpdate(source: String, channel: String): Pair<Boolean, UpdateRelease?> {
        val (update, release) = UpdateChecker.checkForUpdate(context, source, channel)
        return update to release?.toCore()
    }

    override fun persistRelease(release: UpdateRelease) {
        UpdateChecker.persistRelease(context, release.toChecker())
    }

    override fun rememberApkDigest(tag: String, sha256: String?, sizeBytes: Long?) {
        UpdateChecker.rememberApkDigest(context, tag, sha256, sizeBytes)
    }

    override fun rememberedApkSha256(tag: String): String? =
        UpdateChecker.rememberedApkSha256(context, tag)

    override fun rememberedApkSize(tag: String): Long? =
        UpdateChecker.rememberedApkSize(context, tag)

    override fun hasValidApk(tag: String): Boolean =
        UpdateInstaller.hasValidApk(context, tag)

    override fun localApkPath(tag: String): String =
        UpdateInstaller.apkFile(context, tag).absolutePath

    override fun cleanOldApks(keepTag: String?) {
        UpdateChecker.cleanOldApks(context, keepTag)
    }

    override fun resolveApkUrl(tag: String, source: String, channel: String): String? =
        UpdateChecker.resolveApkUrl(context, tag, source, channel)

    override suspend fun downloadApk(
        apkUrl: String,
        tag: String,
        onProgress: suspend (Float) -> Unit,
    ): String = UpdateInstaller.downloadApk(context, apkUrl, tag, onProgress).absolutePath

    override fun installApk(
        apkPath: String,
        onInstallingChanged: (Boolean) -> Unit,
        onFinished: (() -> Unit)?,
    ) {
        UpdateInstaller.installApk(
            context = context,
            file = File(apkPath),
            onInstallingChanged = onInstallingChanged,
            onFinished = onFinished,
        )
    }

    private fun UpdateRelease.toChecker() = UpdateChecker.GiteeRelease(
        tagName, name, body, htmlUrl, apkUrl, createdAt, apkSha256, apkSize
    )

    private fun UpdateChecker.GiteeRelease.toCore() = UpdateRelease(
        tagName, name, body, htmlUrl, apkUrl, createdAt, apkSha256, apkSize
    )
}
