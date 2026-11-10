package com.haooz.chedule.ui.utils

import android.content.Context
import com.haooz.chedule.data.platform.UpdateController
import com.haooz.chedule.data.platform.UpdateRelease
import com.haooz.chedule.i18n.CommonStr
import java.io.File

/**
 * [UpdateController] 的 Android 实现 —— 包装全局单例 [UpdateChecker] / [UpdateInstaller]。
 *
 * 同 [AndroidDiagnosticRecorder] 的套路：两个包装对象的方法都带 `context`，
 * 本层在构造时捕获宿主注入的 context，接口层保持无参（`:ui-shared` 的「应用更新」页
 * 只依赖跨平台接口，commonMain 里没有 android / java import）。
 * 宿主（AppRouteContent 的 UpdateSettings 路由 / 设置页的 `updateController` 注入点）在调用点创建本实例并注入：
 * `updateController = remember { AndroidUpdateController(context) }`。
 *
 * 数据类转换：`UpdateChecker.GiteeRelease`（internal 嵌套类，不能跨模块引用）
 * ↔ `:core` 的 [UpdateRelease]，字段一一对应。
 * APK 句柄：接口层用绝对路径 String，这里与 `java.io.File` 互转。
 */
class AndroidUpdateController(private val context: Context) : UpdateController {

    override val currentVersion: String
        get() = try {
            // 原页面在 remember 里现取：packageManager 版本号取不到时显示「未知」
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
