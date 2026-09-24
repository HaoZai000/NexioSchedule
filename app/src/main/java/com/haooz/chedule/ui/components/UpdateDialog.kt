package com.haooz.chedule.ui.components

import android.content.Context
import android.util.Log
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
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadComplete by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        val autoCheck = updatePrefs.getBoolean("auto_check_update", true)
        val updateReminder = updatePrefs.getBoolean("update_reminder", true)

        if (!autoCheck) return@LaunchedEffect

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastCheckDate = updatePrefs.getString("last_check_date", "") ?: ""

        if (lastCheckDate != today) {
            val updateChannel = updatePrefs.getString("update_channel", "stable") ?: "stable"
            val downloadSource = if (updateChannel == "beta") "gitee" else
                (updatePrefs.getString("download_source", "gitee") ?: "gitee")
            val (hasUpdate, release) = withContext(Dispatchers.IO) {
                try {
                    UpdateChecker.checkForUpdate(context, downloadSource, updateChannel)
                } catch (e: Exception) {
                    Log.e("UpdateDialog", "检查更新失败", e)
                    Pair(false, null)
                }
            }
            updatePrefs.edit {
                putString("last_check_date", today)
                    .putBoolean("has_update", hasUpdate)
            }

            if (hasUpdate && release != null) {
                updatePrefs.edit {
                    putString("latest_url", release.htmlUrl)
                        .putString("latest_apk_url", release.apkUrl)
                        .putString("latest_tag", release.tagName)
                        .putString("latest_name", release.name)
                        .putString("latest_body", release.body)
                        .putString("latest_date", release.createdAt)
                }
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
            hasDownloadedApk = UpdateInstaller.hasValidApk(context, tag)
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
        val apkUrl = updatePrefs.getString("latest_apk_url", "") ?: ""
        if (tag.isBlank() || apkUrl.isBlank()) {
            android.widget.Toast.makeText(context, "未找到下载链接", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        isDownloading = true
        downloadProgress = 0f
        downloadComplete = false
        coroutineScope.launch {
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
                if (!isInstalling) {
                    showUpdateDialog = false
                    downloadComplete = false
                    downloadProgress = 0f
                }
            }
        )
    }

    val primaryLabel = when {
        isInstalling -> "安装中"
        isDownloading -> "正在下载"
        downloadComplete || hasDownloadedApk -> "安装"
        else -> "更新"
    }

    OverlayDialog(
        title = when {
            isInstalling -> "安装中"
            isDownloading -> "正在下载"
            downloadComplete -> "下载完成"
            else -> "发现新版本"
        },
        summary = when {
            isInstalling -> "正在安装应用，请稍候..."
            isDownloading -> "最新版本: $updateTagName"
            else -> "最新版本: $updateTagName"
        },
        show = showUpdateDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = {
            if (!isDownloading && !isInstalling) {
                showUpdateDialog = false
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
            } else if (isInstalling) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isInstalling) {
                    TextButton(
                        text = if (isDownloading) "后台下载" else "稍后",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            // 下载中点「后台下载」只关弹窗，下载继续
                            showUpdateDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
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
