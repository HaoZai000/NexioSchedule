package com.haooz.chedule.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.activities.MainActivity
import com.haooz.chedule.ui.screens.applyScheduleData
import com.haooz.chedule.ui.screens.parseFullScheduleJson
import com.haooz.chedule.ui.screens.parseIcsFile
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField

/**
 * 分享导入课表的状态与弹窗。
 *
 * 监听 MainActivity 的 shareIntentVersion，解析 ACTION_VIEW / ACTION_SEND 携带的
 * JSON / ICS 文件，弹出确认对话框让用户编辑课表名后导入。
 */
@Composable
internal fun ShareImportDialog(
    activity: MainActivity?,
    shareIntentVersion: Int,
    courseViewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    var showShareImportDialog by remember { mutableStateOf(false) }
    var shareImportData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var shareImportScheduleName by remember { mutableStateOf("") }

    LaunchedEffect(shareIntentVersion) {
        val uri = activity?.shareIntentUri
        val action = activity?.shareIntentAction
        if (uri != null && action != null) {
            activity.clearShareIntent()

            val intentData = when (action) {
                Intent.ACTION_VIEW -> uri
                Intent.ACTION_SEND -> uri
                else -> null
            }

            intentData?.let { shareUri ->
                try {
                    val inputStream = context.contentResolver.openInputStream(shareUri)
                    val text = inputStream?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                    inputStream?.close()

                    if (text.isNotBlank()) {
                        val fileName = shareUri.lastPathSegment?.lowercase() ?: ""
                        val isIcs = fileName.endsWith(".ics")

                        val (success, message, data) = if (isIcs) {
                            parseIcsFile(text)
                        } else {
                            parseFullScheduleJson(text)
                        }

                        if (success && data != null) {
                            val scheduleName = if (isIcs) "ICS导入课表" else (data["schedule_name"] as? String) ?: "导入的课表"
                            shareImportData = data
                            shareImportScheduleName = scheduleName
                            showShareImportDialog = true
                        } else {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    OverlayDialog(
        title = "导入课表",
        summary = "是否导入课表「$shareImportScheduleName」？\n确定导入将创建一个新的课表",
        show = showShareImportDialog,
        outsideMargin = DpSize(17.dp, 12.dp),
        onDismissRequest = {
            showShareImportDialog = false
            shareImportData = null
            shareImportScheduleName = ""
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(
                value = shareImportScheduleName,
                onValueChange = { shareImportScheduleName = it },
                label = "课表名称",
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        showShareImportDialog = false
                        shareImportData = null
                        shareImportScheduleName = ""
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定导入",
                    onClick = {
                        if (shareImportScheduleName.isNotBlank() && shareImportData != null) {
                            val (_, importMessage) = applyScheduleData(
                                context,
                                courseViewModel,
                                scheduleViewModel,
                                settingsViewModel,
                                shareImportScheduleName,
                                shareImportData!!
                            )
                            Toast.makeText(context, importMessage, Toast.LENGTH_LONG).show()
                        }
                        showShareImportDialog = false
                        shareImportData = null
                        shareImportScheduleName = ""
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
