package com.haooz.chedule.ui.components

import android.content.Context
import com.haooz.chedule.data.NexioLog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.utils.UpdateChecker
import com.haooz.chedule.ui.utils.UpdateInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.edit
import kotlin.time.Duration.Companion.milliseconds

/**
 * 更新弹窗：每天启动时检查一次更新，有新版本则弹窗提示。
 * 点「更新」直接下载安装包（已下好则直接安装），不再跳转设置页再点一次。
 */
@Composable
internal fun UpdateDialog(liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val updatePrefs = remember { context.getSharedPreferences("update_settings", Context.MODE_PRIVATE) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateTagName by remember { mutableStateOf("") }
    var updateBody by remember { mutableStateOf("") }
    var hasDownloadedApk by remember { mutableStateOf(false) }

    var isDownloading by remember { mutableStateOf(false) }
    /** 缓存里的下载地址不可用，正在回源重新解析 */
    var isResolvingUrl by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadComplete by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    fun updateChannel(): String = updatePrefs.getString("update_channel", "stable") ?: "stable"

    fun downloadSource(): String {
        val channel = updateChannel()
        return if (channel == "beta") "gitee"
        else (updatePrefs.getString("download_source", "gitee") ?: "gitee")
    }

    LaunchedEffect(Unit) {
        val autoCheck = updatePrefs.getBoolean("auto_check_update", true)
        val updateReminder = updatePrefs.getBoolean("update_reminder", true)

        if (!autoCheck) return@LaunchedEffect

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastCheckDate = updatePrefs.getString("last_check_date", "") ?: ""

        if (lastCheckDate != today) {
            val channel = updateChannel()
            val source = downloadSource()
            val (hasUpdate, release) = withContext(Dispatchers.IO) {
                try {
                    UpdateChecker.checkForUpdate(context, source, channel)
                } catch (e: Exception) {
                    NexioLog.e("UpdateDialog", "检查更新失败", e)
                    Pair(false, null)
                }
            }
            updatePrefs.edit {
                putString("last_check_date", today)
                    .putBoolean("has_update", hasUpdate)
            }

            if (hasUpdate && release != null) {
                UpdateChecker.persistRelease(context, release)
                UpdateChecker.rememberApkDigest(context, release.tagName, release.apkSha256, release.apkSize)
                UpdateChecker.cleanOldApks(context, release.tagName)
            }
        }

        delay(1400.milliseconds)

        val hasUpdate = updatePrefs.getBoolean("has_update", false)
        val tag = updatePrefs.getString("latest_tag", "") ?: ""
        val body = updatePrefs.getString("latest_body", "") ?: ""

        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
        val localVersion = currentVersion.removePrefix("v")
        val remoteVersion = tag.removePrefix("v")

        val actuallyHasUpdate = hasUpdate && tag.isNotBlank() && UpdateChecker.isNewerVersion(remoteVersion, localVersion)
        if (hasUpdate && !actuallyHasUpdate) {
            updatePrefs.edit { putBoolean("has_update", false) }
        }

        if (actuallyHasUpdate && updateReminder) {
            updateTagName = tag
            updateBody = body
            // 校验需解析 APK，放 IO 线程
            hasDownloadedApk = withContext(Dispatchers.IO) {
                UpdateInstaller.hasValidApk(context, tag)
            }
            if (hasDownloadedApk) {
                downloadedFile = UpdateInstaller.apkFile(context, tag)
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
            android.widget.Toast.makeText(context, "未找到版本信息", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        isDownloading = true
        isResolvingUrl = true
        downloadProgress = 0f
        downloadComplete = false
        coroutineScope.launch {
            // 当天缓存里可能没有下载地址（发版时 release 先于 APK 资产创建），
            // 这里不直接放弃：resolveApkUrl 会在缓存为空时回源重查并回写缓存。
            // 否则弹窗会一直报「未找到下载链接」，只有进设置页重新检查才能下。
            val apkUrl = withContext(Dispatchers.IO) {
                UpdateChecker.resolveApkUrl(context, tag, downloadSource(), updateChannel())
            }
            isResolvingUrl = false
            if (apkUrl.isNullOrBlank()) {
                isDownloading = false
                android.widget.Toast.makeText(
                    context,
                    "获取下载地址失败，请到「设置 - 应用更新」中重试",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            try {
                val file = UpdateInstaller.downloadApk(context, apkUrl, tag) { p ->
                    downloadProgress = p
                }
                downloadedFile = file
                downloadComplete = true
                isDownloading = false
                hasDownloadedApk = true
            } catch (e: Exception) {
                isDownloading = false
                downloadComplete = false
                android.widget.Toast.makeText(context, "下载失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startInstall() {
        val file = downloadedFile ?: return
        UpdateInstaller.installApk(
            context = context,
            file = file,
            onInstallingChanged = { isInstalling = it },
            onFinished = {
                // 安装流程已交棒（静默成功 / 系统安装器已拉起）：
                // 无条件收尾，避免用户在系统安装器点取消后弹窗卡在「安装中」
                isInstalling = false
                showUpdateDialog = false
                downloadComplete = false
                downloadProgress = 0f
            }
        )
    }

    val primaryLabel = when {
        isInstalling -> "安装中"
        isResolvingUrl -> "获取链接"
        isDownloading -> "正在下载"
        downloadComplete || hasDownloadedApk -> "安装"
        else -> "更新"
    }

    OverlayDialog(
        title = when {
            isInstalling -> "安装中"
            isResolvingUrl -> "正在获取链接"
            isDownloading -> "正在下载"
            downloadComplete -> "下载完成"
            else -> "发现新版本"
        },
        summary = when {
            isInstalling -> "正在安装应用，请稍候..."
            else -> "最新版本: $updateTagName"
        },
        show = showUpdateDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        // 安装/下载都不阻塞关闭：系统安装器可被用户取消，弹窗不能因此卡死。
        // 关闭时同步清掉 transient 状态，否则下次打开会残留「安装中」无法操作。
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
                        text = "正在获取下载地址...",
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
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
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
                        isInstalling -> "关闭"
                        isDownloading -> "后台下载"
                        else -> "稍后"
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
                                if (downloadedFile == null && updateTagName.isNotBlank()) {
                                    downloadedFile = UpdateInstaller.apkFile(context, updateTagName)
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
