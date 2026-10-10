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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
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
 *
 * 轴向锁定（越 touchSlop 后，二选一，不双轴并行）：
 * - 横 ≥ 1.3×纵 → 横主导并 consume，防弧线 Y 带动纵向
 * - 纵 ≥ 1.3×横 → 纵主导，横轴不动
 * - 两轴接近 → 取位移大的一轴锁定；横则 consume 翻页，纵则交页内滚动
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
 * 落页规则：距离约 1/4 页认方向；甩速 ≥ 100.dp/s 时至少推进基准页 ±1。
 * 小幅快甩的释放速度常只有 300~600px/s，门限取 200.dp/s 会整段漏掉、
 * 只能按距离回弹（表现为「反向拉回」）；再取低则慢拖释放的残余速度
 * 会误翻页，100.dp/s 是两者的平衡点。
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

    // 同向甩（或只带轻微反向）至少推进一页；反向拖超过 1/4 页则尊重距离结果。
    // 100.dp/s ≈ 300px/s（3x 屏）：小幅快甩的常见释放区间下沿，再高会漏判回弹
    val flickThreshold = with(density) { 100.dp.toPx() }
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
 * 末端回弹的视觉平移（placement/layer，进布局坐标）。
 *
 */
private class PagerOverscrollOffsetElement(
    private val overscrollX: androidx.compose.runtime.MutableFloatState,
) : ModifierNodeElement<PagerOverscrollOffsetNode>() {
    override fun create(): PagerOverscrollOffsetNode = PagerOverscrollOffsetNode(overscrollX)

    override fun update(node: PagerOverscrollOffsetNode) {
        if (node.overscrollX !== overscrollX) {
            node.overscrollX = overscrollX
            node.invalidatePlacement()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pagerOverscrollOffset"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PagerOverscrollOffsetElement) return false
        return overscrollX === other.overscrollX
    }

    override fun hashCode(): Int = overscrollX.hashCode()
}

private class PagerOverscrollOffsetNode(
    var overscrollX: androidx.compose.runtime.MutableFloatState,
) : LayoutModifierNode, ObserverModifierNode, Modifier.Node() {

    override fun onAttach() {
        observeOffset()
    }

    override fun onObservedReadsChanged() {
        invalidatePlacement()
        observeOffset()
    }

    private fun observeOffset() {
        observeReads { overscrollX.floatValue }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            // overscrollX 是 scrollBy 坐标（正值=下一页=内容左移），视觉平移须取反
            val tx = -overscrollX.floatValue
            if (tx == 0f) {
                placeable.place(0, 0)
            } else {
                placeable.placeWithLayer(0, 0) {
                    translationX = tx
                }
            }
        }
    }
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
        // 回弹平移必须进布局坐标（placeWithLayer），课卡采样才会跟随
        .then(PagerOverscrollOffsetElement(overscrollX))
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
                var locked = false
                // 最后发生真实移动的时刻：甩完停顿再抬手时，VelocityTracker 的
                // 100ms 窗口被静止段稀释，需要按「位移段」重新估均速兜底
                var lastMoveUptimeMillis = down.uptimeMillis
                // 与 overscrollX 同为 scrollBy 坐标；阻尼前的原始累积
                var overscrollRaw = 0f
                val viewportPx = size.width.toFloat().coerceAtLeast(1f)
                tracker.addPosition(down.uptimeMillis, down.position)
                while (true) {
                    // Initial：在子级 verticalScroll 之前拿到事件，锁横后才能拦住纵向
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) break
                    if (latestBlock.value()) break
                    val dx = change.positionChangeIgnoreConsumed().x
                    val dy = change.positionChangeIgnoreConsumed().y
                    accX += dx
                    accY += dy
                    if (dx != 0f || dy != 0f) lastMoveUptimeMillis = change.uptimeMillis
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
                            // 两轴接近：取位移大的一轴锁定，不并行
                            ax >= touchSlop && ay >= touchSlop -> {
                                xDominant = ax >= ay
                                locked = true
                            }
                        }
                        if (xDominant && scrollWorker == null) {
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
                    if (!xDominant) continue
                    dragChannel?.trySend(-dx)
                    // Initial consume：子级（verticalScroll / 课卡手势）本帧不再看到位移
                    change.consume()
                }
                dragChannel?.close()
                dragChannel = null
                // AwaitPointerEventScope 不能 join；回 scope 等 worker 放锁后再落页
                if (xDominant) {
                    val trackedVelocity =
                        runCatching { tracker.calculateVelocity() }.getOrNull() ?: Velocity(0f, 0f)
                    val moveSpanMs =
                        (lastMoveUptimeMillis - down.uptimeMillis).coerceAtLeast(16L)
                    val moveAvgVelocity = Velocity(
                        x = accX * 1000f / moveSpanMs,
                        y = accY * 1000f / moveSpanMs,
                    )
                    val fingerVelocity = if (abs(moveAvgVelocity.x) > abs(trackedVelocity.x)) {
                        moveAvgVelocity
                    } else {
                        trackedVelocity
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
