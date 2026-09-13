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
import androidx.compose.runtime.rememberUpdatedState
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
 * - 按住滑动：从起点周拖到终点周，把二者之间的连续周次**并入**已选集合；
 *   例如先滑 1→8，再滑 10→14，结果为 1~8 且 10~14（已占用周自动跳过）
 *
 * 滑选始终追加、不会反选：起点周即使已选中，拖完该区间仍保持选中。
 * 取消某周请用单击。
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

    // pointerInput 只以 enabled/totalWeeks/columns 为 key，手势协程不会随选中集重启。
    // 直接捕获 selectedWeeks 会拿到旧快照：第二次滑选会用空集并集，把上一段冲掉。
    val selectedWeeksState = rememberUpdatedState(selectedWeeks)
    val occupiedWeeksState = rememberUpdatedState(occupiedWeeks)
    val onToggleWeekState = rememberUpdatedState(onToggleWeek)
    val onReplaceWeeksState = rememberUpdatedState(onReplaceWeeks)

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
        return (lo..hi).filter { it !in occupiedWeeksState.value }.toSet()
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
                    if (downWeek == null || downWeek in occupiedWeeksState.value) {
                        // 等待抬起，避免误消费
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        return@awaitEachGesture
                    }

                    var currentWeek: Int = downWeek
                    var dragging = false
                    // 每次新手势都读最新已选集合，保证多段滑选是累加而不是被旧空集覆盖
                    val dragBase = selectedWeeksState.value

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (change == null) break

                        if (!change.pressed) {
                            if (!dragging) {
                                // 点选：抬起时若几乎未移动则切换
                                onToggleWeekState.value(downWeek)
                            }
                            break
                        }

                        val delta = change.position - down.position
                        if (!dragging && (abs(delta.x) > viewConfiguration.touchSlop ||
                                abs(delta.y) > viewConfiguration.touchSlop)
                        ) {
                            dragging = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReplaceWeeksState.value(dragBase + rangeSelection(downWeek, downWeek))
                            change.consume()
                        }

                        if (dragging) {
                            change.consume()
                            val week = weekAt(change.position) ?: currentWeek
                            if (week != currentWeek) {
                                currentWeek = week
                                onReplaceWeeksState.value(dragBase + rangeSelection(downWeek, week))
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
