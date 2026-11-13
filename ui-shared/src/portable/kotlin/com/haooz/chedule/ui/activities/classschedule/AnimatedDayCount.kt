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

/** 滑入/退场那一格的模糊上限（dp）；完全露出时归零。 */
internal const val DAY_CELL_REVEAL_BLUR_DP = 8f

/**
 * 星期列数（5/6/7）的连续过渡状态，只服务顶栏星期行。
 *
 * [value] 是小数：5 → 6 时既有列一边收窄一边左移，新的一天从右缘滑入。仅在布局期与
 * 绘制期读取，组合期不读。
 */
@Stable
internal class AnimatedDayCountState(initialCount: Int) {
    val value = Animatable(initialCount.toFloat())

    /** 一次过渡结束自增，调用方据此收掉正在退场的格子。 */
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
 * 滑入/退场那一格的淡入淡出与模糊。
 *
 * 露出比例是 `列数 - index`，在 graphicsLayer 块里读动画值，只重绘不重组也不重新测量。
 * 只挂给末尾两格，其余格恒为全露出。
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
 * 不能用 `Row` + `weight`：weight 只能按当前整数列数等分，做不出 5.3 列这种中间态。
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
