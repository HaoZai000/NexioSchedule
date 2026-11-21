/**
 * 「导入课表」确认弹窗 —— 口令取回 / 文件导入 / 外部分享三处共用一份。
 */
package com.haooz.chedule.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.haooz.chedule.i18n.BackupStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.textfield.TextField
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.overlaydialog.OverlayDialog

/**
 * @param show 是否呈现；组件本身始终挂载（靠它驱动进出场动画，别用 `if` 包）
 * @param scheduleName 当前输入框里的课表名（状态由调用方持有）
 * @param existingNames 本机现有课表名，仅用于实时判冲突
 * @param newScheduleSummary 没撞名时的说明文案（各入口口径不同，入参是当前名字）
 * @param onDismiss 取消 / 点外部关闭；调用方负责清掉待导入数据
 * @param onConfirm 第一参 = 最终课表名（已 trim），第二参 = true 表示用户确认**覆盖**同名课表
 */
@Composable
fun ScheduleImportConfirmDialog(
    show: Boolean,
    scheduleName: String,
    onScheduleNameChange: (String) -> Unit,
    existingNames: List<String>,
    newScheduleSummary: (String) -> String = { BackupStr.importScheduleConfirmSummaryN(it) },
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    onDismiss: () -> Unit = {},
    onConfirm: (scheduleName: String, overwrite: Boolean) -> Unit = { _, _ -> },
) {
    val hapticFeedback = LocalHapticFeedback.current
    val trimmedName = scheduleName.trim()
    val conflict = trimmedName.isNotEmpty() && trimmedName in existingNames

    OverlayDialog(
        title = BackupStr.importSchedule(),
        summary = if (conflict) {
            BackupStr.importOverwriteSummaryN(trimmedName)
        } else {
            newScheduleSummary(trimmedName)
        },
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(
                value = scheduleName,
                onValueChange = onScheduleNameChange,
                label = BackupStr.scheduleNameLabel(),
                modifier = Modifier.fillMaxWidth(),
                requestFocus = show
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlatTextButton(
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                FlatTextButton(
                    text = if (conflict) BackupStr.overwriteImport() else BackupStr.confirmImport(),
                    onClick = {
                        if (trimmedName.isEmpty()) return@FlatTextButton
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onConfirm(trimmedName, conflict)
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
