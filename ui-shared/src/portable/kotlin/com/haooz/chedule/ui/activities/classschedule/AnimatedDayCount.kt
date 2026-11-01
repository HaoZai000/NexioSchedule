package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** 滑入/退场那一格的模糊上限（dp）：完全露出时归零，这里只决定起点有多糊 */
internal const val DAY_CELL_REVEAL_BLUR_DP = 8f

/**
 * 星期列数（5 / 6 / 7）的连贯过渡，**只服务顶栏星期行**。
 *
 * 列宽 = 可用宽度 / [AnimatedDayCountState.value]，[value] 是小数：5 → 6 时既有列
 * 一边收窄一边左移，新那一天从右边缘滑进来；反向切换则整体摊开。
 *
 * [value] 只在布局期（[DayColumnsLayout]）与绘制期（[dayCellReveal]）读，组合期不读。
 */
@Stable
internal class AnimatedDayCountState(initialCount: Int) {
    val value = Animatable(initialCount.toFloat())

    /** 一次过渡走完自增：调用方据此收掉正在退场的格子 */
    var settleTick by mutableIntStateOf(0)
        internal set
}

@Composable
internal fun rememberAnimatedDayCount(count: Int): AnimatedDayCountState {
    val state = remember { AnimatedDayCountState(count) }
    LaunchedEffect(count) {
        val target = count.toFloat()
        if (state.value.value == target) return@LaunchedEffect
        try {
            state.value.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 900f,
                ),
            )
        } finally {
            state.settleTick++
        }
    }
    return state
}

/**
 * 滑入 / 退场那一格的淡入淡出 + 模糊。
 *
 * 露出比例就是 `列数 - index`（这一列左缘在 index×列宽）：5 → 6 时第 6 格从 0 淡到 1，
 * 6 → 5 时反过来。动画值在 graphicsLayer 块里读 —— 绘制期，不重组也不重新测量。
 * 只挂给最后两格（其余格恒为全露出），静止态不额外产生图层。
 */
internal fun Modifier.dayCellReveal(
    state: AnimatedDayCountState,
    index: Int,
    blurPx: Float,
): Modifier = graphicsLayer {
    val reveal = (state.value.value - index).coerceIn(0f, 1f)
    alpha = reveal
    renderEffect = if (reveal < 1f && blurPx > 0f) {
        val radius = (1f - reveal) * blurPx
        BlurEffect(radius, radius, TileMode.Decal)
    } else {
        null
    }
}

/**
 * 按动画中的列数摆放 [content] 里的每一格。
 *
 * 不能用 `Row` + `weight`：weight 只能等分当前列数，做不出「列数在 5.3 这种中间态时
 * 每格该多宽」。列宽与落位都由 [count] 推出来，动画期间每一帧只是重新测量这几格文字。
 */
@Composable
internal fun DayColumnsLayout(
    count: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val columns = count().coerceAtLeast(1f)
        val columnWidth = constraints.maxWidth.toFloat() / columns
        val widthPx = columnWidth.roundToInt().coerceAtLeast(0)
        val placeables = measurables.map { measurable ->
            measurable.measure(
                Constraints(
                    minWidth = widthPx,
                    maxWidth = widthPx,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight,
                )
            )
        }
        val height = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            placeables.maxOfOrNull { it.height } ?: 0
        }
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place((columnWidth * index).roundToInt(), 0)
            }
        }
    }
}
