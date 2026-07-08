package com.haooz.chedule.ui.components
import com.haooz.chedule.ui.activities.UpdateSettingsActivity
import com.haooz.chedule.ui.utils.UpdateChecker

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * 更新弹窗：启动后延迟检查 SharedPreferences 缓存的更新标志，
 * 若存在新版本则弹出对话框，引导用户跳转到更新设置页。
 */
@Composable
internal fun UpdateDialog() {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    val updatePrefs = remember { context.getSharedPreferences("update_settings", Context.MODE_PRIVATE) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateTagName by remember { mutableStateOf("") }
    var updateBody by remember { mutableStateOf("") }
    var hasDownloadedApk by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1400)
        val hasUpdate = updatePrefs.getBoolean("has_update", false)
        val updateReminder = updatePrefs.getBoolean("update_reminder", true)
        val tag = updatePrefs.getString("latest_tag", "") ?: ""
        val body = updatePrefs.getString("latest_body", "") ?: ""
        if (hasUpdate && updateReminder && tag.isNotBlank()) {
            val currentVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (_: Exception) {
                ""
            }
            val latestVer = tag.removePrefix("v").substringBefore("-")
            val localVer = currentVersion.removePrefix("v").substringBefore("-")
            val actuallyNewer = UpdateChecker.isNewerVersion(latestVer, localVer)
            if (actuallyNewer) {
                updateTagName = tag
                updateBody = body
                val apkFile = File(context.filesDir, "update-$tag.apk")
                hasDownloadedApk = apkFile.exists() && apkFile.length() > 0
                kotlinx.coroutines.delay(800.milliseconds)
                showUpdateDialog = true
            } else {
                updatePrefs.edit().putBoolean("has_update", false).apply()
            }
        }
    }

    if (!showUpdateDialog) return

    OverlayDialog(
        title = "发现新版本",
        summary = "最新版本: $updateTagName",
        show = showUpdateDialog,
        outsideMargin = DpSize(17.dp, 12.dp),
        onDismissRequest = { showUpdateDialog = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (updateBody.isNotBlank()) {
                Text(
                    text = updateBody.take(300),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "稍后",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showUpdateDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showUpdateDialog = false
                        val intent = Intent(context, UpdateSettingsActivity::class.java)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(
                        text = if (hasDownloadedApk) "安装" else "更新",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }
}
