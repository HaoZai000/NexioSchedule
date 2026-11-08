/**
 * 「导入课表」确认弹窗 —— 口令取回 / 文件导入 / 外部分享三处共用一份。
 *
 * 为什么抽出来：撞名以前只有一条死路。[applyScheduleData] 撞名直接返回「课表已存在」，
 * 于是「手机改名后往平板同步同一张表」必定失败：平板提示已存在，课程一条都进不来，
 * 用户只能干看着（见 2026-10-28 反馈）。现在按输入的名字**实时**判冲突：
 * 撞名时说明改成「覆盖」口径、主按钮改文案，由用户决定覆盖还是改个名字另存。
 *
 * ⚠ 名字是**上提状态**（[scheduleName] + [onScheduleNameChange]），不在本组件里 remember：
 * 内部持有就得在 show 翻转时同步一次，会先闪一帧旧名字（退出动画期间又不能清，见 CardExpand 的老坑）。
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
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

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
            NativeMiuixTextField(
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
                TextButton(
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = if (conflict) BackupStr.overwriteImport() else BackupStr.confirmImport(),
                    onClick = {
                        if (trimmedName.isEmpty()) return@TextButton
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
