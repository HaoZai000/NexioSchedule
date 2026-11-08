package com.haooz.chedule.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.NoopUpdateController
import com.haooz.chedule.data.platform.UpdateController
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.isNewerVersion
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.AboutStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.components.text.miuix.Text as Text
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 更新弹窗：每天启动时检查一次更新，有新版本则弹窗提示。
 * 点「更新」直接下载安装包（已下好则直接安装），不再跳转设置页再点一次。
 */
@Composable
/** @param updateController 更新能力；桌面/iOS 用 [NoopUpdateController] */
fun UpdateDialog(
    updateController: UpdateController = NoopUpdateController,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    /**
     * 显隐回传：主界面据此决定要不要挂顶栏/底栏那两层采样层（本弹窗自己管 show，
     * 外面读不到）。晚一帧无所谓——弹窗是淡入的，第一帧采样层还没就位看不出来。
     */
    onShowChanged: ((Boolean) -> Unit)? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val updatePrefs = remember { AppStorage.store("update_settings") }
    var showUpdateDialog by remember { mutableStateOf(false) }
    LaunchedEffect(showUpdateDialog) { onShowChanged?.invoke(showUpdateDialog) }
    var updateTagName by remember { mutableStateOf("") }
    var updateBody by remember { mutableStateOf("") }
    var hasDownloadedApk by remember { mutableStateOf(false) }

    var isDownloading by remember { mutableStateOf(false) }
    /** 缓存里的下载地址不可用，正在回源重新解析 */
    var isResolvingUrl by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadComplete by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    // 用绝对路径而不是 File：跨平台侧没有 java.io.File（控制器接口也是这么定的）
    var downloadedApkPath by remember { mutableStateOf<String?>(null) }

    fun updateChannel(): String = updatePrefs.getString("update_channel", "stable")

    fun downloadSource(): String {
        val channel = updateChannel()
        return if (channel == "beta") "gitee"
        else (updatePrefs.getString("download_source", "gitee"))
    }

    LaunchedEffect(Unit) {
        val autoCheck = updatePrefs.getBoolean("auto_check_update", true)
        val updateReminder = updatePrefs.getBoolean("update_reminder", true)

        if (!autoCheck) return@LaunchedEffect

        val today = todayLocalDate().toString()
        val lastCheckDate = updatePrefs.getString("last_check_date", "")

        if (lastCheckDate != today) {
            val channel = updateChannel()
            val source = downloadSource()
            val (hasUpdate, release) = withContext(ioDispatcher) {
                try {
                    updateController.checkForUpdate(source, channel)
                } catch (e: Exception) {
                    NexioLog.e("UpdateDialog", AboutStr.checkUpdateFailed(), e)
                    Pair(false, null)
                }
            }
            updatePrefs.edit {
                // KeyValueEditor 的 putXxx 返回 Unit（不像 Android KTX 的 Editor 可链式），
                // 因此必须逐条写
                putString("last_check_date", today)
                putBoolean("has_update", hasUpdate)
            }

            if (hasUpdate && release != null) {
                updateController.persistRelease(release)
                updateController.rememberApkDigest(release.tagName, release.apkSha256, release.apkSize)
                updateController.cleanOldApks(release.tagName)
            }
        }

        delay(1400.milliseconds)

        val hasUpdate = updatePrefs.getBoolean("has_update", false)
        val tag = updatePrefs.getString("latest_tag", "")
        val body = updatePrefs.getString("latest_body", "")

        val currentVersion = updateController.currentVersion
        val localVersion = currentVersion.removePrefix("v")
        val remoteVersion = tag.removePrefix("v")

        val actuallyHasUpdate = hasUpdate && tag.isNotBlank() && isNewerVersion(remoteVersion, localVersion)
        if (hasUpdate && !actuallyHasUpdate) {
            updatePrefs.edit { putBoolean("has_update", false) }
        }

        if (actuallyHasUpdate && updateReminder) {
            updateTagName = tag
            updateBody = body
            // 校验需解析 APK，放 IO 线程
            hasDownloadedApk = withContext(ioDispatcher) {
                updateController.hasValidApk(tag)
            }
            if (hasDownloadedApk) {
                downloadedApkPath = updateController.localApkPath(tag)
                downloadComplete = true
            }
            delay(800.milliseconds)
            showUpdateDialog = true
        } else if (actuallyHasUpdate) {
            updatePrefs.edit { putBoolean("has_update", false) }
        }
    }

    fun startDownload() {
        val tag = updateTagName
        if (tag.isBlank()) {
            showToast(AboutStr.versionInfoNotFound())
            return
        }
        isDownloading = true
        isResolvingUrl = true
        downloadProgress = 0f
        downloadComplete = false
        coroutineScope.launch {
            // 缓存里可能没有下载地址（发版时 release 先于 APK 资产创建），不能直接放弃：
            // resolveApkUrl 会在缓存为空时回源重查并回写缓存。
            val apkUrl = withContext(ioDispatcher) {
                updateController.resolveApkUrl(tag, downloadSource(), updateChannel())
            }
            isResolvingUrl = false
            if (apkUrl.isNullOrBlank()) {
                isDownloading = false
                showToast(AboutStr.getDownloadUrlFailedSettings(), longDuration = true)
                return@launch
            }
            try {
                val path = updateController.downloadApk(apkUrl, tag) { p ->
                    downloadProgress = p
                }
                downloadedApkPath = path
                downloadComplete = true
                isDownloading = false
                hasDownloadedApk = true
            } catch (e: Exception) {
                isDownloading = false
                downloadComplete = false
                showToast(AboutStr.downloadFailedN(e.message))
            }
        }
    }

    fun startInstall() {
        val apkPath = downloadedApkPath ?: return
        updateController.installApk(
            apkPath = apkPath,
            onInstallingChanged = { isInstalling = it },
            onFinished = {
                // 安装流程已交棒（静默成功 / 系统安装器已拉起）：无条件收尾，
                // 避免用户在系统安装器点取消后弹窗卡在「安装中」
                isInstalling = false
                showUpdateDialog = false
                downloadComplete = false
                downloadProgress = 0f
            }
        )
    }

    val primaryLabel = when {
        isInstalling -> AboutStr.installing()
        isResolvingUrl -> AboutStr.getLink()
        isDownloading -> CommonStr.downloading()
        downloadComplete || hasDownloadedApk -> AboutStr.install()
        else -> CommonStr.update()
    }

    OverlayDialog(
        title = when {
            isInstalling -> AboutStr.installing()
            isResolvingUrl -> AboutStr.gettingLink()
            isDownloading -> CommonStr.downloading()
            downloadComplete -> AboutStr.downloadComplete()
            else -> AboutStr.newVersionFound()
        },
        summary = when {
            isInstalling -> AboutStr.installingSummary()
            else -> AboutStr.latestVersionN(updateTagName)
        },
        show = showUpdateDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        // 安装/下载都不阻塞关闭（系统安装器可被取消，弹窗不能卡死）；
        // 关闭时同步清掉 transient 状态，否则下次打开会残留「安装中」。
        onDismissRequest = {
            showUpdateDialog = false
            if (!isDownloading) {
                isInstalling = false
                downloadProgress = 0f
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (updateBody.isNotBlank() && !isDownloading && !isInstalling) {
                Text(
                    text = updateBody.take(300),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            if (isDownloading) {
                if (isResolvingUrl) {
                    // 回源解析地址时进度未知，用不确定态进度条
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = AboutStr.gettingDownloadUrlDots(),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                } else {
                    LinearProgressIndicator(
                        progress = downloadProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(downloadProgress * 100).toInt()}%",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (isInstalling) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 始终提供关闭入口：安装中也能关，否则用户在系统安装器点「取消」后回不来
                TextButton(
                    text = when {
                        isInstalling -> CommonStr.close()
                        isDownloading -> AboutStr.backgroundDownload()
                        else -> AboutStr.later()
                    },
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        // 下载中点「后台下载」只关弹窗，下载继续
                        showUpdateDialog = false
                        if (!isDownloading) {
                            isInstalling = false
                            downloadProgress = 0f
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !isDownloading && !isInstalling,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        when {
                            isDownloading || isInstalling -> Unit
                            downloadComplete || hasDownloadedApk -> {
                                if (downloadedApkPath == null && updateTagName.isNotBlank()) {
                                    downloadedApkPath = updateController.localApkPath(updateTagName)
                                }
                                startInstall()
                            }
                            else -> startDownload()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(
                        text = primaryLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        // 按钮自己把内容居中：文案在「立即更新 / 下载中 / 安装」之间换字时按中心收放
                        textAlign = TextAlign.Center,
                        color = if (isDownloading || isInstalling) {
                            MiuixTheme.colorScheme.onSurfaceVariantActions
                        } else {
                            Color.White
                        }
                    )
                }
            }
        }
    }
}
