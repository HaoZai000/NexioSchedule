/** 周次网格：点选切换 + 按住滑动选中连续周次区间 */
package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 周次选择网格。
 *
 * - 单击：切换该周
 * - 按住滑动：从起点周拖到终点周，选中（或反选）二者之间的连续周次；
 *   例如 1→8 选中 1~8，4→8 选中 4~8，2→8 选中 2~8（已占用周自动跳过）
 *
 * 起点周在拖动前若已选中，则整段区间执行反选，便于快速清掉一串周次。
 */
@Composable
fun WeekRangeSelectGrid(
    totalWeeks: Int,
    selectedWeeks: Set<Int>,
    occupiedWeeks: Set<Int>,
    enabled: Boolean,
    isDark: Boolean,
    columns: Int = 6,
    onToggleWeek: (week: Int) -> Unit,
    onReplaceWeeks: (weeks: Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val spacing = 6.dp
    val cellHeight = 32.dp
    val rows = (totalWeeks + columns - 1) / columns

    val primaryColor = MiuixTheme.colorScheme.primary
    val outlineColor = MiuixTheme.colorScheme.outline
    val onSurfaceSummaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val occupiedColor = if (isDark) Color(0xFF4A4A4A) else Color(0xFFF0F0F0)
    val defaultCardColor = if (isDark) Color(0xFF363636) else Color(0xFFF2F2F2)

    val cellBounds = remember(totalWeeks) { arrayOfNulls<Rect>(totalWeeks) }
    var gridWidth by remember { mutableStateOf(0) }

    fun weekAtApprox(pos: Offset): Int? {
        if (gridWidth <= 0) return null
        val spacingPx = with(density) { spacing.toPx() }
        val cellW = (gridWidth - spacingPx * (columns - 1)) / columns
        val cellH = with(density) { cellHeight.toPx() }
        val col = ((pos.x + spacingPx * 0.5f) / (cellW + spacingPx)).toInt()
        val row = ((pos.y + spacingPx * 0.5f) / (cellH + spacingPx)).toInt()
        if (col !in 0 until columns || row !in 0 until rows) return null
        val week = row * columns + col + 1
        return week.takeIf { it in 1..totalWeeks }
    }

    fun weekAt(pos: Offset): Int? {
        for (i in 0 until totalWeeks) {
            val r = cellBounds[i] ?: continue
            if (r.contains(pos)) return i + 1
        }
        return weekAtApprox(pos)
    }

    fun rangeSelection(anchor: Int, target: Int): Set<Int> {
        val lo = min(anchor, target)
        val hi = max(anchor, target)
        return (lo..hi).filter { it !in occupiedWeeks }.toSet()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { gridWidth = it.width }
            .pointerInput(enabled, totalWeeks, columns) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downWeek = weekAt(down.position)
                    if (downWeek == null || downWeek in occupiedWeeks) {
                        // 等待抬起，避免误消费
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        return@awaitEachGesture
                    }

                    var currentWeek: Int = downWeek
                    var dragging = false
                    var dragRemove = false
                    val dragBase = selectedWeeks
                    var lastApplied = selectedWeeks

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (change == null) break

                        if (!change.pressed) {
                            if (!dragging) {
                                // 点选：抬起时若几乎未移动则切换
                                onToggleWeek(downWeek)
                            }
                            break
                        }

                        val delta = change.position - down.position
                        if (!dragging && (abs(delta.x) > viewConfiguration.touchSlop ||
                                abs(delta.y) > viewConfiguration.touchSlop)
                        ) {
                            dragging = true
                            dragRemove = downWeek in dragBase
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val initRange = rangeSelection(downWeek, downWeek)
                            lastApplied = if (dragRemove) dragBase - initRange else dragBase + initRange
                            onReplaceWeeks(lastApplied)
                            change.consume()
                        }

                        if (dragging) {
                            change.consume()
                            val week = weekAt(change.position) ?: currentWeek
                            if (week != currentWeek) {
                                currentWeek = week
                                val range = rangeSelection(downWeek, week)
                                lastApplied =
                                    if (dragRemove) dragBase - range else dragBase + range
                                onReplaceWeeks(lastApplied)
                            }
                        }
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                for (col in 0 until columns) {
                    val idx = row * columns + col
                    if (idx < totalWeeks) {
                        val weekNum = idx + 1
                        WeekRangeCell(
                            weekNum = weekNum,
                            isSelected = weekNum in selectedWeeks,
                            isOccupied = weekNum in occupiedWeeks,
                            enabled = enabled,
                            isDark = isDark,
                            primaryColor = primaryColor,
                            outlineColor = outlineColor,
                            onSurfaceSummaryColor = onSurfaceSummaryColor,
                            occupiedColor = occupiedColor,
                            defaultCardColor = defaultCardColor,
                            onBounds = { cellBounds[weekNum - 1] = it }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.WeekRangeCell(
    weekNum: Int,
    isSelected: Boolean,
    isOccupied: Boolean,
    enabled: Boolean,
    isDark: Boolean,
    primaryColor: Color,
    outlineColor: Color,
    onSurfaceSummaryColor: Color,
    occupiedColor: Color,
    defaultCardColor: Color,
    onBounds: (Rect) -> Unit,
) {
    val bgColor = when {
        !enabled -> defaultCardColor
        isSelected -> primaryColor
        isOccupied -> occupiedColor
        else -> defaultCardColor
    }
    val textColor = when {
        !enabled -> if (isDark) Color(0xFF606060) else outlineColor
        isSelected -> Color.White
        isOccupied -> if (isDark) Color(0xFF606060) else outlineColor
        else -> onSurfaceSummaryColor
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .height(32.dp)
            .onGloballyPositioned { onBounds(it.boundsInParent()) }
            .squircleClip(10.dp)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$weekNum",
            fontSize = 13.sp,
            color = textColor
        )
    }
}
