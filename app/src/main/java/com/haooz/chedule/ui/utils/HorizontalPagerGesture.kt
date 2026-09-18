package com.haooz.chedule.ui.utils

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * HorizontalPager 横竖轴「主导方判定」翻页手势。
 *
 * - 横滑/对角：由本手势 `pagerState.scrollBy` 驱动，松手后 [settleHorizontalPager]
 * - 纵滑：不占 pager 写锁，交页内 verticalScroll / LazyColumn
 * - 使用方需将 pager 的 `userScrollEnabled = false`
 *
 * 判定：
 * - 横向越 touchSlop 且 ≥1.3×纵向 → 横滑主导：consume，防弧线 Y 带动纵向
 * - 纵向越 slop 且 >1.3×横向 → 纵滑主导：横轴不动
 * - 两轴都大且接近 → 对角：不 consume，两轴并行
 */

/** 持有手势 settle Job，避免与下一次拖动抢 PagerState 写锁 */
class PagerTakeoverGestureState internal constructor(
    internal val scope: CoroutineScope,
)

@Composable
fun rememberPagerTakeoverGestureState(): PagerTakeoverGestureState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PagerTakeoverGestureState(scope) }
}

/**
 * 松手后横向落页。
 *
 * 连续切页时上一拍 settle 常未结束，起点可能是小数页；目标以
 * `max/min(currentPage, startRound)` 为基准，避免「第一次划不过去」。
 * 距离约 1/4 页即认方向；甩速足够时至少推进基准页 ±1。
 */
internal suspend fun settleHorizontalPager(
    pagerState: PagerState,
    fingerVelocityX: Float,
    density: Density,
    startScrollOffset: Float,
) {
    val pageCount = pagerState.pageCount
    if (pageCount <= 0) return
    val visualOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
    var targetPage = visualOffset.roundToInt().coerceIn(0, pageCount - 1)
    val dragPages = visualOffset - startScrollOffset
    val livePage = pagerState.currentPage
    val startRound = startScrollOffset.roundToInt()
    val baseForward = maxOf(livePage, startRound)
    val baseBackward = minOf(livePage, startRound)

    if (dragPages >= 0.25f) {
        val distTarget = (baseForward + 1).coerceIn(0, pageCount - 1)
        if (targetPage < distTarget) targetPage = distTarget
    } else if (dragPages <= -0.25f) {
        val distTarget = (baseBackward - 1).coerceIn(0, pageCount - 1)
        if (targetPage > distTarget) targetPage = distTarget
    }

    val flickThreshold = with(density) { 400.dp.toPx() }
    if (fingerVelocityX <= -flickThreshold && dragPages >= -0.05f) {
        val velTarget = (baseForward + 1).coerceIn(0, pageCount - 1)
        if (targetPage < velTarget) targetPage = velTarget
    } else if (fingerVelocityX >= flickThreshold && dragPages <= 0.05f) {
        val velTarget = (baseBackward - 1).coerceIn(0, pageCount - 1)
        if (targetPage > velTarget) targetPage = velTarget
    }
    targetPage = targetPage.coerceIn(0, pageCount - 1)
    pagerState.animateScrollToPage(
        targetPage,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f),
    )
}

private class PagerDragState {
    var startScrollOffset = 0f
}

/**
 * 挂在 HorizontalPager 的 modifier 上。`blockGesture()==true` 时本手势不驱动 pager
 * （课表页：壁纸编辑 / 课卡拖拽独占）。
 */
@Composable
fun Modifier.pagerAxisTakeoverGesture(
    pagerState: PagerState,
    gestureState: PagerTakeoverGestureState = rememberPagerTakeoverGestureState(),
    blockGesture: () -> Boolean = { false },
): Modifier {
    val latestBlock = rememberUpdatedState(blockGesture)
    val scope = gestureState.scope
    val settleJob = remember { mutableStateOf<Job?>(null) }
    return this.pointerInput(pagerState, scope, settleJob) {
        val touchSlop = viewConfiguration.touchSlop
        val domRatio = 1.3f
        val density: Density = this
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            val dragState = PagerDragState()
            dragState.startScrollOffset =
                pagerState.currentPage + pagerState.currentPageOffsetFraction
            var dragChannel: Channel<Float>? = null
            var scrollWorker: Job? = null
            val workerDone = CompletableDeferred<Unit>()
            var accX = 0f
            var accY = 0f
            var xDominant = false
            var dualAxis = false
            var locked = false
            tracker.addPosition(down.uptimeMillis, down.position)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) break
                if (latestBlock.value()) break
                val dx = change.positionChangeIgnoreConsumed().x
                val dy = change.positionChangeIgnoreConsumed().y
                accX += dx
                accY += dy
                if (!locked) {
                    val ax = abs(accX)
                    val ay = abs(accY)
                    val yDominant = ay >= touchSlop && ay > ax * domRatio
                    when {
                        ax >= touchSlop && ax >= ay * domRatio -> {
                            xDominant = true
                            locked = true
                        }
                        yDominant -> {
                            locked = true
                        }
                        ax >= touchSlop && ay >= touchSlop -> {
                            dualAxis = true
                            locked = true
                        }
                    }
                    if ((xDominant || dualAxis) && scrollWorker == null) {
                        settleJob.value?.cancel()
                        settleJob.value = null
                        val channel = Channel<Float>(Channel.UNLIMITED)
                        dragChannel = channel
                        scrollWorker = scope.launch {
                            try {
                                pagerState.scroll(MutatePriority.UserInput) {
                                    dragState.startScrollOffset =
                                        pagerState.currentPage + pagerState.currentPageOffsetFraction
                                    for (delta in channel) {
                                        scrollBy(delta)
                                    }
                                }
                            } finally {
                                workerDone.complete(Unit)
                            }
                        }
                    }
                }
                if (!xDominant && !dualAxis) continue
                // 不 consume：对角斜滑时纵向滚动仍可并行
                dragChannel?.trySend(-dx)
                if (xDominant) change.consume()
            }
            dragChannel?.close()
            dragChannel = null
            // 受限 AwaitPointerEventScope 不能 join；在 scope 里等 worker 释放写锁再落页
            if (xDominant || dualAxis) {
                val fingerVelocity =
                    runCatching { tracker.calculateVelocity() }.getOrNull() ?: Velocity(0f, 0f)
                if (scrollWorker == null) workerDone.complete(Unit)
                settleJob.value = scope.launch {
                    workerDone.await()
                    settleHorizontalPager(
                        pagerState,
                        fingerVelocity.x,
                        density,
                        dragState.startScrollOffset,
                    )
                }
            }
            scrollWorker = null
        }
    }
}
