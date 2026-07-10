package com.haooz.chedule.ui.components
import com.haooz.chedule.ui.activities.UpdateSettingsActivity
import com.haooz.chedule.ui.utils.UpdateChecker

import android.content.Context
import android.content.Intent
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 更新弹窗：每天启动时检查一次更新，有新版本则弹窗提示。
 * 检查和弹窗逻辑统一在此处完成，避免竞态条件。
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
        val autoCheck = updatePrefs.getBoolean("auto_check_update", true)
        val updateReminder = updatePrefs.getBoolean("update_reminder", true)

        if (!autoCheck || !updateReminder) return@LaunchedEffect

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastCheckDate = updatePrefs.getString("last_check_date", "") ?: ""

        if (lastCheckDate != today) {
            val (hasUpdate, release) = withContext(Dispatchers.IO) {
                try {
                    UpdateChecker.checkForUpdate(context)
                } catch (e: Exception) {
                    Log.e("UpdateDialog", "检查更新失败", e)
                    Pair(false, null)
                }
            }
            updatePrefs.edit()
                .putString("last_check_date", today)
                .putBoolean("has_update", hasUpdate)
                .apply()

            if (hasUpdate && release != null) {
                updatePrefs.edit()
                    .putString("latest_url", release.htmlUrl)
                    .putString("latest_apk_url", release.apkUrl)
                    .putString("latest_tag", release.tagName)
                    .putString("latest_name", release.name)
                    .putString("latest_body", release.body)
                    .putString("latest_date", release.createdAt)
                    .apply()
                UpdateChecker.cleanOldApks(context, release.tagName)
            }
        }

        delay(1400)

        val hasUpdate = updatePrefs.getBoolean("has_update", false)
        val tag = updatePrefs.getString("latest_tag", "") ?: ""
        val body = updatePrefs.getString("latest_body", "") ?: ""

        if (hasUpdate && tag.isNotBlank()) {
            val currentVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (_: Exception) { "" }
            val latestVer = tag.removePrefix("v").substringBefore("-")
            val localVer = currentVersion.removePrefix("v").substringBefore("-")

            if (UpdateChecker.isNewerVersion(latestVer, localVer)) {
                updateTagName = tag
                updateBody = body
                val apkFile = File(context.filesDir, "update-$tag.apk")
                hasDownloadedApk = apkFile.exists() && apkFile.length() > 0
                delay(800)
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
