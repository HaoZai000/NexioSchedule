package com.haooz.chedule.ui.utils

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * HorizontalPager 横竖轴主导判定翻页手势。使用方需 `userScrollEnabled = false`。
 *
 * 横滑/对角由 `pagerState.scrollBy` 驱动，松手 [settleHorizontalPager] 落页；
 * 纵滑不占 pager 写锁，交页内 verticalScroll / LazyColumn。
 * 末端 `scrollBy` 吃不下的位移走橡皮筋，松手弹簧回弹。
 *
 * 轴向锁定（越 touchSlop 后）：
 * - 横 ≥ 1.3×纵 → 横主导并 consume，防弧线 Y 带动纵向
 * - 纵 ≥ 1.3×横 → 纵主导，横轴不动
 * - 两轴接近 → 对角，不 consume，两轴并行
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
 *
 * 落页规则：距离约 1/4 页认方向；甩速 ≥ 200.dp/s 时至少推进基准页 ±1。
 * 甩速阈值不宜过高（高密度屏上 400.dp/s ≈ 1200px/s，短甩到不了），
 * 位移门也不宜过窄（±0.05 页会被噪声/轻微反向挡掉）。
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

    // 同向甩（或只带轻微反向）至少推进一页；反向拖超过 1/4 页则尊重距离结果
    val flickThreshold = with(density) { 200.dp.toPx() }
    if (fingerVelocityX <= -flickThreshold && dragPages > -0.25f) {
        val velTarget = (baseForward + 1).coerceIn(0, pageCount - 1)
        if (targetPage < velTarget) targetPage = velTarget
    } else if (fingerVelocityX >= flickThreshold && dragPages < 0.25f) {
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
 * 末端橡皮筋：原始位移 → 阻尼视觉偏移。
 * `x-x²+x³/3` 让最大值约为 [range] 的 1/3，拉得越远越难再动。
 */
private fun dampedOverscroll(raw: Float, range: Float): Float {
    if (raw == 0f || range <= 0f) return 0f
    val x = min(abs(raw) / range, 1f)
    val dampedFactor = x - x * x + (x * x * x) / 3f
    return sign(raw) * dampedFactor * range
}

/**
 * 挂在 HorizontalPager 的 modifier 上。`blockGesture()==true` 时不驱动 pager
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
    val overscrollJob = remember { mutableStateOf<Job?>(null) }
    val overscrollX = remember { mutableFloatStateOf(0f) }
    return this
        .clipToBounds()
        // overscrollX 是 scrollBy 坐标（正值=下一页=内容左移），视觉平移须取反
        .graphicsLayer { translationX = -overscrollX.floatValue }
        .pointerInput(pagerState, scope, settleJob, overscrollJob, overscrollX) {
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
                var lastUptimeMillis = down.uptimeMillis
                // 与 overscrollX 同为 scrollBy 坐标；阻尼前的原始累积
                var overscrollRaw = 0f
                val viewportPx = size.width.toFloat().coerceAtLeast(1f)
                tracker.addPosition(down.uptimeMillis, down.position)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    lastUptimeMillis = change.uptimeMillis
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
                            overscrollJob.value?.cancel()
                            overscrollJob.value = null
                            val channel = Channel<Float>(Channel.UNLIMITED)
                            dragChannel = channel
                            scrollWorker = scope.launch {
                                try {
                                    pagerState.scroll(MutatePriority.UserInput) {
                                        dragState.startScrollOffset =
                                            pagerState.currentPage + pagerState.currentPageOffsetFraction
                                        // 回弹未结束就再次按住时接上当前偏移，小位移下 visual≈raw
                                        if (overscrollX.floatValue != 0f && overscrollRaw == 0f) {
                                            overscrollRaw = overscrollX.floatValue
                                        }
                                        for (delta in channel) {
                                            var remaining = delta
                                            // 反向拖动先收回回弹，抵完再交给 pager
                                            if (overscrollRaw != 0f && remaining != 0f &&
                                                sign(remaining) != sign(overscrollRaw)
                                            ) {
                                                val reduce =
                                                    if (abs(remaining) >= abs(overscrollRaw)) {
                                                        -overscrollRaw
                                                    } else {
                                                        remaining
                                                    }
                                                overscrollRaw += reduce
                                                remaining -= reduce
                                                overscrollX.floatValue =
                                                    dampedOverscroll(overscrollRaw, viewportPx)
                                            }
                                            if (remaining != 0f) {
                                                val consumed = scrollBy(remaining)
                                                val leftover = remaining - consumed
                                                // 末端 scrollBy 吃不下 → 橡皮筋超出
                                                if (abs(leftover) > 0.25f) {
                                                    overscrollRaw = (overscrollRaw + leftover)
                                                        .coerceIn(-viewportPx, viewportPx)
                                                    overscrollX.floatValue =
                                                        dampedOverscroll(overscrollRaw, viewportPx)
                                                }
                                            }
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
                // AwaitPointerEventScope 不能 join；回 scope 等 worker 放锁后再落页
                if (xDominant || dualAxis) {
                    // 短甩时 VelocityTracker 可能采样不足得到 0 速，用整段位移估算兜底
                    val trackedVelocity =
                        runCatching { tracker.calculateVelocity() }.getOrNull() ?: Velocity(0f, 0f)
                    val fingerVelocity = if (abs(trackedVelocity.x) > 1f) {
                        trackedVelocity
                    } else {
                        val dtMs = (lastUptimeMillis - down.uptimeMillis).coerceAtLeast(16L)
                        Velocity(x = accX * 1000f / dtMs, y = accY * 1000f / dtMs)
                    }
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
                    // 松手弹簧归位；甩速给一点初速，回弹更跟手
                    if (abs(overscrollX.floatValue) > 0.5f) {
                        val start = overscrollX.floatValue
                        val releaseVel = fingerVelocity.x
                        overscrollJob.value = scope.launch {
                            animate(
                                initialValue = start,
                                targetValue = 0f,
                                // overscrollX 与 fingerVelocity 坐标系相反
                                initialVelocity = -releaseVel * 0.22f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ) { value, _ -> overscrollX.floatValue = value }
                            overscrollX.floatValue = 0f
                        }
                    } else {
                        overscrollX.floatValue = 0f
                    }
                }
                scrollWorker = null
            }
        }
}
