package com.haooz.chedule.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs

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
    /** 被别的交互占用的指针（长按拖课的那根）：翻页要跳过它，但其余手指照常可用。 */
    blockedPointerId: () -> PointerId? = { null },
    /**
     * 拖课浮层占住第一根指针时，第二只手的纵向位移旁路喂给页内滚动容器。
     * 只有周 pager 传：`verticalScroll` 的 `awaitEachGesture` 要等所有手指抬起，
     * 第一根一直按着时第二只手的事件根本没人处理（页内滚动协程挂在
     * `awaitAllPointersUp()`）。见 2026-10-21 的「第二只手纵向滚动」。
     */
    onVerticalScroll: ((Float) -> Unit)? = null,
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
        // 回调每帧取最新：pointerInput 的 key 只有 state / pageWidthPx，不能把 lambda 塞进 key
        val latestVerticalScroll = rememberUpdatedState(onVerticalScroll)
    val dragModifier = if (!swipeEnabled) {
        Modifier
    } else {
        Modifier.horizontalPageDragGesture(
            state = state,
            pageWidthPx = pageWidthPx,
            blockedPointerId = blockedPointerId,
            verticalScrollSink = latestVerticalScroll,
            onGestureStart = onGestureStart,
            onGestureEnd = onGestureEnd,
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

/**
 * 横向翻页手势：每帧挑一根「没被别的交互占用」的按下指针。
 *
 * 不用 Modifier.draggable —— 它锁死第一根按下的指针，那根被别处消费后会停在
 * gesture-pickup 状态，只有抬手才换指针；长按拖课时另一只手就翻不了页。
 */
private fun Modifier.horizontalPageDragGesture(
    state: PageSwitchState,
    pageWidthPx: Float,
    blockedPointerId: () -> PointerId?,
    verticalScrollSink: State<((Float) -> Unit)?>,
    onGestureStart: () -> Unit,
    onGestureEnd: (Int) -> Unit,
): Modifier = pointerInput(state, pageWidthPx) {
    awaitPointerEventScope {
        val slop = viewConfiguration.touchSlop
        var pointer: PointerId? = null
        var downX = 0f
        var downY = 0f
        var lastX = 0f
        var lastY = 0f
        var lastT = 0L
        var velocity = 0f
        var dragging = false

        fun finish() {
            if (dragging) onGestureEnd(state.release(-velocity / pageWidthPx, pageWidthPx))
            dragging = false
            pointer = null
            velocity = 0f
        }

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val blocked = blockedPointerId()
            // 还没起拖时，新按下的手指优先：按住卡片那只手不动、另一只手翻周。
            if (!dragging && event.changes.any {
                    it.changedToDown() && it.id != blocked && !it.isConsumed && it.id != pointer
                }
            ) {
                pointer = null
            }
            val change = if (pointer == null) {
                event.changes.firstOrNull { it.id != blocked && it.pressed && !it.isConsumed }
            } else {
                event.changes.firstOrNull { it.id == pointer }
            }
            // 已跟的那根被别的交互接管（长按拖课）或被消费：放手，下一帧改挑另一根手指
            if (change != null && (change.id == blocked || change.isConsumed)) {
                finish()
                continue
            }
            if (change == null) {
                finish()
                continue
            }
            if (pointer == null) {
                pointer = change.id
                downX = change.position.x
                downY = change.position.y
                lastX = downX
                lastY = downY
                lastT = change.uptimeMillis
                velocity = 0f
            }
            if (!change.pressed) {
                finish()
                continue
            }
            val totalX = change.position.x - downX
            val totalY = change.position.y - downY
            val dy = change.position.y - lastY
            lastY = change.position.y
            if (!dragging) {
                // 纵向主导时整段留给 verticalScroll，不抢上下滑
                if (abs(totalX) > slop && abs(totalX) > abs(totalY) * 1.2f) {
                    dragging = true
                    state.beginDrag()
                    onGestureStart()
                    state.dragBy(-totalX / pageWidthPx)
                    lastX = change.position.x
                    lastT = change.uptimeMillis
                } else if (
                    blocked != null && change.id != blocked &&
                    verticalScrollSink.value != null &&
                    abs(totalY) > slop && abs(totalY) >= abs(totalX)
                ) {
                    // 一只手按住拖课浮层不放 ⇒ 页内 verticalScroll 的 awaitEachGesture
                    // 卡在 awaitAllPointersUp()，第二只手的滚动事件没人处理。
                    // 这里直接把逐帧纵向位移喂给共享 ScrollState；consume 掉是为了
                    // 万一页内手势协程恢复，不会同一根手指滚两遍。
                    verticalScrollSink.value?.invoke(dy)
                    change.consume()
                }
            } else {
                val dx = change.position.x - lastX
                val dt = (change.uptimeMillis - lastT).coerceAtLeast(1L)
                velocity = dx / dt * 1000f
                state.dragBy(-dx / pageWidthPx)
                lastX = change.position.x
                lastT = change.uptimeMillis
                change.consume()
            }
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
