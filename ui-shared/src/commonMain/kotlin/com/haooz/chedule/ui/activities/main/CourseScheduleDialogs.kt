package com.haooz.chedule.ui.activities.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.softglass.backdrop.GlassBackdrop
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 课表页的确认/选择类弹窗：删除课程、粘贴范围、调课冲突。
 * 从 `CourseScheduleApp.kt` 拆出。
 */

@Composable
internal fun DeleteWeekCourseDialog(
    show: Boolean,
    course: Course?,
    week: Int,
    viewModel: CourseViewModel,
    liquidGlassBackdrop: GlassBackdrop?,
    hapticFeedback: HapticFeedback,
    onDismiss: () -> Unit,
) {
    var deleteAllWeeks by remember(show) { mutableStateOf(false) }
    OverlayDialog(
        title = if (deleteAllWeeks) ChromeStr.deleteCourse() else ChromeStr.deleteThisWeekCourse(),
        summary = if (deleteAllWeeks) {
            ChromeStr.deleteAllSummary(course?.name)
        } else {
            ChromeStr.deleteWeekSummary(course?.name, week)
        },
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    state = if (deleteAllWeeks) ToggleableState.On else ToggleableState.Off,
                    onClick = {
                        deleteAllWeeks = !deleteAllWeeks
                    }
                )
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ChromeStr.deleteAllWeeksLabel(),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (deleteAllWeeks) ChromeStr.deleteAllWeeksDesc() else ChromeStr.deleteOnlyThisWeekDesc(week),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
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
                    text = CommonStr.delete(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        course?.let {
                            if (deleteAllWeeks) viewModel.deleteCourse(it.id)
                            else viewModel.deleteCourseForWeek(it.id, week)
                        }
                        onDismiss()
                    },
                    textColor = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun PasteRangeDialog(
    show: Boolean,
    courseName: String,
    liquidGlassBackdrop: GlassBackdrop?,
    hapticFeedback: HapticFeedback,
    onPaste: (allWeeks: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        title = ChromeStr.pasteCourse(),
        summary = ChromeStr.pasteRangeSummary(courseName),
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                text = ChromeStr.allWeeks(),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onPaste(true)
                },
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = ChromeStr.currentWeekOption(),
                textColor = MiuixTheme.colorScheme.primary,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onPaste(false)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 冲突处理方式：覆盖 / 交换 */
enum class ConflictResolution { OVERWRITE, SWAP }

/**
 * 调课冲突第一步：只让用户选处理方式。
 *
 * 覆盖/交换**不在这里写库** —— 先关掉本弹窗，等退出动画结束，再由
 * [RescheduleConflictConfirmDialog] 展示具体操作并等用户确认。
 */
@Composable
internal fun RescheduleConflictDialog(
    show: Boolean,
    liquidGlassBackdrop: GlassBackdrop?,
    hapticFeedback: HapticFeedback,
    onCancel: () -> Unit,
    onChooseOverwrite: () -> Unit,
    onChooseSwap: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    OverlayDialog(
        title = ChromeStr.slotOccupiedTitle(),
        summary = ChromeStr.chooseResolutionSummary(),
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onCancel,
        onDismissFinished = onDismissFinished,
    ) {
        // 竖排，一行一个按钮；取消放最后
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = ChromeStr.overwrite(),
                textColor = Color(0xFFFF9800),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onChooseOverwrite()
                },
            )
            TextButton(
                text = ChromeStr.swap(),
                textColor = MiuixTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onChooseSwap()
                },
            )
            TextButton(
                text = CommonStr.cancel(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCancel()
                },
            )
        }
    }
}

/**
 * 调课冲突第二步：写明覆盖/交换的具体结果，取消或确定。
 * 确定才真正写库；取消只把浮动卡片送回源周（相邻页锚点），不做任何修改。
 */
@Composable
internal fun RescheduleConflictConfirmDialog(
    show: Boolean,
    resolution: ConflictResolution,
    source: Course?,
    target: Course?,
    /** 目标位数据坐标（星期, 起始节次） */
    dropTarget: Pair<Int, Int>?,
    /** 目标位数据周次：调课日 = followWeek */
    dropTargetWeek: Int,
    draggedWeek: Int,
    viewModel: CourseViewModel,
    liquidGlassBackdrop: GlassBackdrop?,
    hapticFeedback: HapticFeedback,
    onCancel: () -> Unit,
    onOverwriteResolved: () -> Unit,
    onSwapResolved: () -> Unit,
    onOverwrite: (Set<String>) -> Unit,
    onSwap: (Set<String>) -> Unit,
    onDismissFinished: () -> Unit,
) {
    val crossWeek = draggedWeek != dropTargetWeek
    val summary = when {
        source == null || target == null -> ChromeStr.confirmOperationSummary()
        resolution == ConflictResolution.OVERWRITE && crossWeek ->
            ChromeStr.overwriteCrossWeekSummary(
                target.name, dropTargetWeek, source.name, draggedWeek, dropTargetWeek
            )
        resolution == ConflictResolution.OVERWRITE ->
            ChromeStr.overwriteThisWeekSummary(target.name, source.name)
        crossWeek ->
            ChromeStr.swapCrossWeekSummary(source.name, draggedWeek, target.name, dropTargetWeek)
        else ->
            ChromeStr.swapThisWeekSummary(source.name, target.name)
    }
    OverlayDialog(
        title = if (resolution == ConflictResolution.OVERWRITE) ChromeStr.confirmOverwrite() else ChromeStr.confirmSwap(),
        summary = summary,
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onCancel,
        onDismissFinished = onDismissFinished,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = CommonStr.cancel(),
                modifier = Modifier.weight(1f),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCancel()
                },
            )
            TextButton(
                text = CommonStr.ok(),
                textColor = if (resolution == ConflictResolution.OVERWRITE) {
                    Color(0xFFFF9800)
                } else {
                    MiuixTheme.colorScheme.primary
                },
                modifier = Modifier.weight(1f),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (resolution == ConflictResolution.OVERWRITE) {
                        if (source != null && dropTarget != null && target != null) {
                            val sectionSpan = source.endSection - source.startSection
                            val targetEnd = dropTarget.second + sectionSpan
                            // 源课在 draggedWeek，目标位在 dropTargetWeek（调课日两者可能不同 → 跨周移动）
                            // 注意 targetWeek 是最后一个参数，别插到中间（全是 Int，错位不报错）
                            viewModel.overwriteCourseForWeek(
                                source.id, draggedWeek,
                                dropTarget.first, dropTarget.second, targetEnd,
                                dropTargetWeek
                            )
                            val sourceCourses = viewModel.getCoursesAtSlot(
                                draggedWeek, source.dayOfWeek, source.startSection, source.endSection
                            ).filter { it.id != source.id && !it.isActiveInWeek(draggedWeek) }
                            if (sourceCourses.isNotEmpty()) {
                                onOverwrite(sourceCourses.map { it.id }.toSet())
                            }
                        }
                        onOverwriteResolved()
                    } else {
                        if (source != null && target != null) {
                            // 源课在 draggedWeek、冲突课在 dropTargetWeek，两侧各拆一周互换
                            viewModel.swapCoursesForWeek(
                                source.id, target.id, draggedWeek, dropTargetWeek
                            )
                            val sourceCourses = viewModel.getCoursesAtSlot(
                                draggedWeek, source.dayOfWeek, source.startSection, source.endSection
                            ).filter {
                                it.id != source.id && it.id != target.id && !it.isActiveInWeek(
                                    draggedWeek
                                )
                            }
                            val conflictCourses = viewModel.getCoursesAtSlot(
                                dropTargetWeek, target.dayOfWeek, target.startSection, target.endSection
                            ).filter {
                                it.id != target.id && it.id != source.id && !it.isActiveInWeek(
                                    dropTargetWeek
                                )
                            }
                            val allAnimated =
                                (sourceCourses + conflictCourses).map { it.id }.toSet()
                            if (allAnimated.isNotEmpty()) {
                                onSwap(allAnimated)
                            }
                        }
                        onSwapResolved()
                    }
                },
            )
        }
    }
}
