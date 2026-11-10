package com.haooz.chedule.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/**
 * 横向平铺 [pageCount] 页；切页只做位移，窗口内页常驻。
 *
 * @param swipeEnabled 是否允许手指左右拖动切页
 * @param onGestureStart 手指横向接管页面
 * @param onGestureEnd 松手落点页
 */
@Composable
fun HorizontalPageSwitch(
    state: PageSwitchState,
    pageCount: Int,
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true,
    beyondPageCount: Int = 1,
    onGestureStart: () -> Unit = {},
    onGestureEnd: (Int) -> Unit = {},
    content: @Composable (Int) -> Unit,
) {
    state.pageCount = pageCount.coerceAtLeast(1)

    // 页数收口
    LaunchedEffect(state.pageCount) {
        if (!state.dragging && state.position.value > state.last) state.snapTo(state.lastPage)
    }

    BoxWithConstraints(modifier.clipToBounds()) {
        val pageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val pos: () -> Float = { state.visual }
        val dragModifier = if (!swipeEnabled) {
            Modifier
        } else {
            Modifier.draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    if (!state.dragging) state.beginDrag()
                    // 手指右移 = 上一页
                    state.dragBy(-delta / pageWidthPx)
                },
                onDragStarted = {
                    state.beginDrag()
                    onGestureStart()
                },
                onDragStopped = { velocity ->
                    // 速度换算为页/秒
                    val landed = state.release(-velocity / pageWidthPx, pageWidthPx)
                    onGestureEnd(landed)
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(dragModifier)
                // 位置在绘制期读
                .graphicsLayer { translationX = -pos() * pageWidthPx },
        ) {
            Pages(
                state = state,
                pageCount = pageCount.coerceAtLeast(1),
                beyond = beyondPageCount.coerceAtLeast(0),
                content = content,
            )
        }
    }
}

/** 窗口内的页按屏宽并排摆放，坐标用绝对页号。 */
@Composable
private fun Pages(
    state: PageSwitchState,
    pageCount: Int,
    beyond: Int,
    content: @Composable (Int) -> Unit,
) {
    // 只订阅当前页
    val anchor = state.currentPage
    val first = (anchor - beyond).coerceAtLeast(0)
    val lastVisible = (anchor + beyond).coerceAtMost(pageCount - 1)
    Layout(
        content = {
            for (index in first..lastVisible) {
                // key 用绝对页号
                key(index) {
                    // 一页 = 一个 measurable
                    Box(Modifier.fillMaxSize()) { content(index) }
                }
            }
        },
    ) { measurables, constraints ->
        val pageWidth = constraints.maxWidth
        // 高度不受限：按内容最高页取容器高；固定高度：每页整屏
        val unboundedHeight = constraints.maxHeight == Constraints.Infinity
        val placeables = measurables.map {
            if (unboundedHeight) {
                it.measure(Constraints(minWidth = pageWidth, maxWidth = pageWidth))
            } else {
                it.measure(Constraints.fixed(pageWidth, constraints.maxHeight))
            }
        }
        val h = if (unboundedHeight) {
            placeables.maxOfOrNull { it.height } ?: 0
        } else {
            constraints.maxHeight
        }
        layout(pageWidth, h) {
            placeables.forEachIndexed { i, placeable ->
                placeable.place((first + i) * pageWidth, 0)
            }
        }
    }
}
