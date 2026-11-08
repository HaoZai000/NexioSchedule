package com.haooz.chedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.haooz.chedule.data.backup.parseFullScheduleJson
import com.haooz.chedule.data.backup.parseIcsFile
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.BackupStr
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.ui.screens.applyScheduleData
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel

// ⚠ 本文件 2026-10-11 自 :androidApp 迁入 :ui-shared（第二十七批）。
// 迁移动机：整页只碰 Android 三样 —— Intent.ACTION_VIEW/SEND 常量、contentResolver.openInputStream、
// MainActivity 三个分享 Intent 字段；抽成「宿主投递 [SharedFileIntent] + 提供读文本回调」后即纯 UI。
// ⚠ 本文件不应再出现任何 `android.*`；要读别的平台数据就再加注入回调。

/**
 * 宿主投递进来的「外部分享」请求。
 * 宿主负责在 `onNewIntent` / 冷启动里解析 Intent，**只把 ACTION_VIEW / ACTION_SEND 转成本类型**
 * （其它 action 直接丢，页面不再做 action 判断 —— 那需要 `android.content.Intent`）。
 * [version] 单调递增：同一 uri 再次分享时靠它触发重新处理（`LaunchedEffect` 的 key）。
 */
class SharedFileIntent(
    val uri: String,
    val version: Int,
)

/**
 * 分享导入课表的状态与弹窗。
 *
 * 监听宿主投递的 [shareIntent]，解析 JSON / ICS 文件，弹出确认对话框让用户编辑课表名后导入。
 *
 * @param shareIntent 待处理的分享请求；null = 无
 * @param onShareIntentConsumed 已取走该请求（宿主据此清掉自己的 pending 状态）
 * @param readSharedFileText 读该 uri 的全部文本；读失败返回 null（**宿主内部已提示**，
 *   页面不再重复报错）
 */
@Composable
fun ShareImportDialog(
    shareIntent: SharedFileIntent? = null,
    onShareIntentConsumed: () -> Unit = {},
    readSharedFileText: (String) -> String? = { null },
    courseViewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
) {
    var showShareImportDialog by remember { mutableStateOf(false) }
    var shareImportData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var shareImportScheduleName by remember { mutableStateOf("") }
    val existingScheduleNames by scheduleViewModel.scheduleNames.collectAsState()

    LaunchedEffect(shareIntent?.version) {
        val payload = shareIntent ?: return@LaunchedEffect
        // 先取走再读：与迁移前一致（原实现在读流之前就 clearShareIntent）
        onShareIntentConsumed()

        val text = readSharedFileText(payload.uri) ?: return@LaunchedEffect
        if (text.isBlank()) return@LaunchedEffect

        try {
            // 与原实现同口径：`shareUri.lastPathSegment` 就是最后一段路径
            val fileName = payload.uri.substringAfterLast('/').lowercase()
            val isIcs = fileName.endsWith(".ics")

            val (success, message, data) = if (isIcs) {
                parseIcsFile(text)
            } else {
                parseFullScheduleJson(text)
            }

            if (success && data != null) {
                val scheduleName =
                    if (isIcs) BackupStr.icsImportedScheduleName() else (data["schedule_name"] as? String) ?: BackupStr.importedScheduleName()
                shareImportData = data
                shareImportScheduleName = scheduleName
                showShareImportDialog = true
            } else {
                showToast(message)
            }
        } catch (e: Exception) {
            showToast(ImportStr.importFailedReason(e.message))
        }
    }

    // 撞名时弹窗自己改成「覆盖」口径（见 ScheduleImportConfirmDialog 的注释）
    ScheduleImportConfirmDialog(
        show = showShareImportDialog,
        scheduleName = shareImportScheduleName,
        onScheduleNameChange = { shareImportScheduleName = it },
        existingNames = existingScheduleNames,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismiss = {
            showShareImportDialog = false
            shareImportData = null
        },
        onConfirm = { scheduleName, overwrite ->
            val data = shareImportData
            if (data != null) {
                val (_, importMessage) = applyScheduleData(
                    viewModel = courseViewModel,
                    scheduleViewModel = scheduleViewModel,
                    settingsViewModel = settingsViewModel,
                    scheduleName = scheduleName,
                    data = data,
                    overwriteExisting = overwrite,
                )
                showToast(importMessage, longDuration = true)
            }
            showShareImportDialog = false
            shareImportData = null
        }
    )
}
