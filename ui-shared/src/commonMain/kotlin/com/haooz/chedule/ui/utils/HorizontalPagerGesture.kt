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
import androidx.compose.ui.input.pointer.PointerId
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
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** 持有手势 settle Job，避免与下一次拖动抢 PagerState 写锁 */
class PagerTakeoverGestureState internal constructor(
    internal val scope: CoroutineScope,
) {
    /**
     * 待平移的手势起点（页）。程序化借位跳周被手势打断时，pager 位置会被
     * dispatchRawDelta 平移回逻辑页（借位页↔真身像素相同，屏上不跳），
     * 松手落页计算用的本轮起点也要同步平移同一距离，否则落页目标会被
     * 旧基准拉回跳转前的页。手势在事件循环/松手计算前消费并清零。
     */
    @Volatile
    internal var pendingStartOffsetShiftPages = 0f

    internal fun requestStartOffsetShift(pages: Float) {
        if (pages != 0f) pendingStartOffsetShiftPages += pages
    }

    /** 取走并清零待平移量（手势侧消费） */
    internal fun consumeStartOffsetShift(): Float {
        val v = pendingStartOffsetShiftPages
        if (v != 0f) pendingStartOffsetShiftPages = 0f
        return v
    }
}

@Composable
fun rememberPagerTakeoverGestureState(): PagerTakeoverGestureState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PagerTakeoverGestureState(scope) }
}

/**
 * 松手后横向落页。
 *
 * 连续切页时上一拍 settle 常未结束、起点可能是小数页，故目标以 `max/min(currentPage, startRound)` 为基准。落页规则：位移约 1/4 页认方向；甩速 ≥ 100.dp/s 至少推进基准页 ±1。门限不能再高：小幅快甩释放速度常只有 300~600px/s，200.dp/s 会整段漏判；也不能再低，否则慢拖残余速度会误翻页。
 *
 * [fingerDragPages] 是手指拖过的页数（正 = 下一页）。课表页每页是整张重表格，拖动中 pager 的 offset 可能落后于手指，距离判定取它与 pager 实际位移的绝对值较大者。
 */
internal suspend fun settleHorizontalPager(
    pagerState: PagerState,
    fingerVelocityX: Float,
    density: Density,
    startScrollOffset: Float,
    fingerDragPages: Float = 0f,
) {
    val pageCount = pagerState.pageCount
    if (pageCount <= 0) return
    val visualOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
    var targetPage = visualOffset.roundToInt().coerceIn(0, pageCount - 1)
    val pagerDragPages = visualOffset - startScrollOffset
    val dragPages = if (abs(fingerDragPages) > abs(pagerDragPages)) {
        fingerDragPages
    } else {
        pagerDragPages
    }
    val currentPage = pagerState.currentPage
    val startRound = startScrollOffset.roundToInt()
    val baseForward = maxOf(currentPage, startRound)
    val baseBackward = minOf(currentPage, startRound)

    if (dragPages >= 0.25f) {
        val distTarget = (baseForward + 1).coerceIn(0, pageCount - 1)
        if (targetPage < distTarget) targetPage = distTarget
    } else if (dragPages <= -0.25f) {
        val distTarget = (baseBackward - 1).coerceIn(0, pageCount - 1)
        if (targetPage > distTarget) targetPage = distTarget
    }

    // 松手速度与净位移方向相反（「拖到位后手指回带」）：不能按反向甩处理，
    // 否则一次前向拖动会被 baseBackward-1 直接拍回上一页。
    // 位移够大就按位移方向落页，太小则什么都不做、让 round 自然回弹。
    val velocityFightsDrag =
        dragPages != 0f && (fingerVelocityX > 0f) == (dragPages > 0f)
    val flickThreshold = with(density) { 100.dp.toPx() }
    if (velocityFightsDrag) {
        if (abs(dragPages) >= 0.15f) {
            val velTarget = if (dragPages > 0f) {
                (baseForward + 1).coerceIn(0, pageCount - 1)
            } else {
                (baseBackward - 1).coerceIn(0, pageCount - 1)
            }
            if (dragPages > 0f) {
                if (targetPage < velTarget) targetPage = velTarget
            } else {
                if (targetPage > velTarget) targetPage = velTarget
            }
        }
    } else if (fingerVelocityX <= -flickThreshold && dragPages > -0.25f) {
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

/** 末端回弹的视觉平移。必须进布局坐标，课卡 backdrop 采样才会跟随。 */
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
 * HorizontalPager 的横竖轴主导翻页手势。使用方需 `userScrollEnabled = false`。
 *
 * 越 touchSlop 后二选一锁定、不双轴并行：
 * - 横 ≥ 1.3×纵 → 横主导并 consume，防弧线 Y 带动纵向
 * - 纵 ≥ 1.3×横 → 纵主导，横轴不动
 * - 两轴接近 → 取位移大的一轴锁定
 *
 * [blockGesture] 为 true 时不驱动 pager（课表页：壁纸编辑）。
 * [blockedPointer] 只屏蔽这一根指针（拖课卡的手指），其余指针照常翻页 ——
 * 跨周调课要「一只手拖课、另一只手翻周」。
 * [onVerticalScroll] 纵向主导时的滚动回调（dy 为手指位移，正=向下），
 * 只在第二只手时不接管页内 verticalScroll 才需要。
 */
@Composable
fun Modifier.pagerAxisTakeoverGesture(
    pagerState: PagerState,
    gestureState: PagerTakeoverGestureState = rememberPagerTakeoverGestureState(),
    blockGesture: () -> Boolean = { false },
    blockedPointer: () -> PointerId? = { null },
    onVerticalScroll: ((dy: Float) -> Unit)? = null,
    /** 超出回弹的横向位移（scrollBy 坐标）；外部玻璃采样需要读它来按帧失效 */
    overscrollXState: androidx.compose.runtime.MutableFloatState = remember { mutableFloatStateOf(0f) },
): Modifier {
    val latestBlock = rememberUpdatedState(blockGesture)
    val latestBlockedPointer = rememberUpdatedState(blockedPointer)
    val latestOnVerticalScroll = rememberUpdatedState(onVerticalScroll)
    val scope = gestureState.scope
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val overscrollJob = remember { mutableStateOf<Job?>(null) }
    val overscrollX = overscrollXState
    return this
        .clipToBounds()
        .then(PagerOverscrollOffsetElement(overscrollX))
        .pointerInput(pagerState, scope, settleJob, overscrollJob, overscrollX) {
            val touchSlop = viewConfiguration.touchSlop
            val domRatio = 1.3f
            val density: Density = this
            awaitEachGesture {
                var down = awaitFirstDown(requireUnconsumed = false)
                var isAltPointer = false
                val tracker = VelocityTracker()
                val dragState = PagerDragState()
                // 新手势起点即将按当前位置重新捕获：丢弃上一轮残留的平移量，
                // 否则会错误地平移这一轮的落页基准
                gestureState.consumeStartOffsetShift()
                dragState.startScrollOffset =
                    pagerState.currentPage + pagerState.currentPageOffsetFraction
                var dragChannel: Channel<Float>? = null
                var scrollWorker: Job? = null
                var workerDone = CompletableDeferred<Unit>()
                var accX = 0f
                var accY = 0f
                var xDominant = false
                var locked = false
                // 最后发生真实移动的时刻：甩完停顿再抬手时 tracker 的 100ms 窗口
                // 被静止段稀释，需要按「位移段」重新估均速兜底
                var lastMoveUptimeMillis = down.uptimeMillis
                // 与 overscrollX 同为 scrollBy 坐标；阻尼前的原始累积
                var overscrollRaw = 0f
                // 本轮起点只在第一次拿到 pager 锁时读一次：worker 重建再读会把起点
                // 拉到当前偏移，拖动距离归零、松手就弹回原页
                var startCaptured = false
                val viewportPx = size.width.toFloat().coerceAtLeast(1f)
                tracker.addPosition(down.uptimeMillis, down.position)

                /**
                 * 结束当前这一「轮」横向拖动：关掉本轮 delta 通道、按速度落页。
                 * 不结束整个手势 —— 拖课卡那根手指还按着时必须能等来下一根手指，
                 * 否则手势会卡死在 awaitAllPointersUp。
                 */
                fun settleCurrentRound() {
                    if (!xDominant) return
                    val roundDownUptime = down.uptimeMillis
                    val done = workerDone
                    val trackedVelocity =
                        runCatching { tracker.calculateVelocity() }.getOrNull() ?: Velocity(0f, 0f)
                    val moveSpanMs =
                        (lastMoveUptimeMillis - roundDownUptime).coerceAtLeast(16L)
                    val moveAvgVelocity = Velocity(
                        x = accX * 1000f / moveSpanMs,
                        y = accY * 1000f / moveSpanMs,
                    )
                    // tracker 只看最近 100ms，手指回带时会给出与净位移相反的方向；
                    // 方向矛盾时以位移平均速度为准
                    val fingerVelocity = when {
                        moveAvgVelocity.x != 0f && trackedVelocity.x != 0f &&
                            (moveAvgVelocity.x > 0f) != (trackedVelocity.x > 0f) ->
                            moveAvgVelocity

                        abs(moveAvgVelocity.x) > abs(trackedVelocity.x) -> moveAvgVelocity
                        else -> trackedVelocity
                    }
                    // 程序化借位跳周被打断时的位置平移：松手落页基准同步平移，
                    // 否则会被旧起点拉回跳转前的页
                    dragState.startScrollOffset += gestureState.consumeStartOffsetShift()
                    val startOffset = dragState.startScrollOffset
                    // scrollBy 坐标：负位移 = 下一页
                    val fingerDragPages = -accX / viewportPx
                    if (scrollWorker == null) done.complete(Unit)
                    settleJob.value = scope.launch {
                        done.await()
                        settleHorizontalPager(
                            pagerState,
                            fingerVelocity.x,
                            density,
                            startOffset,
                            fingerDragPages = fingerDragPages,
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

                /** 为下一根手指准备一轮干净的累积状态（速度、轴向、滚动基准）。 */
                fun resetRound() {
                    tracker.resetTracking()
                    workerDone = CompletableDeferred()
                    dragState.startScrollOffset =
                        pagerState.currentPage + pagerState.currentPageOffsetFraction
                    startCaptured = false
                    accX = 0f
                    accY = 0f
                    locked = false
                    xDominant = false
                    overscrollRaw = 0f
                }

                fun finishRound() {
                    dragChannel?.close()
                    dragChannel = null
                    settleCurrentRound()
                    scrollWorker = null
                    resetRound()
                }

                // 手势主体整体包在 try 里：任何退出路径（抬手 / blockGesture / 协程取消）
                // 都必须 close 掉 dragChannel，否则 worker 攥着 pager 的写锁不还，
                // 之后所有翻页都会挂起
                try {
                    while (true) {
                        // Initial：在子级 verticalScroll 之前拿到事件，锁横后才能拦住纵向
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        var change = event.changes.firstOrNull { it.id == down.id }

                        if (change == null || latestBlockedPointer.value() == down.id) {
                            val alt = event.changes.firstOrNull {
                                it.pressed && it.id != down.id &&
                                    it.id != latestBlockedPointer.value()
                            }
                            if (alt != null) {
                                down = alt
                                isAltPointer = true
                                change = alt
                                resetRound()
                                lastMoveUptimeMillis = alt.uptimeMillis
                                tracker.addPosition(alt.uptimeMillis, alt.position)
                                continue
                            }
                            if (change != null && !change.pressed) break // 手指抬起且无替代
                            // ⚠ down 的指针已从事件里彻底消失（抬手帧被吞 / 事件被注入取消）：
                            // 没有按下手指就必须收掉，否则 while 永远 continue，这个
                            // pointerInput 会挂死在旧手势里，之后所有横滑都进不来。
                            if (change == null && event.changes.none { it.pressed }) break
                            continue // 被屏蔽但暂无替代，等下一根
                        }

                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) {
                            // 被追踪的手指抬起 → 看看有没有另一根可以继续
                            val alt = event.changes.firstOrNull {
                                it.pressed && it.id != latestBlockedPointer.value()
                            }
                            if (alt != null) {
                                down = alt
                                isAltPointer = true
                                change = alt
                                accX = 0f
                                accY = 0f
                                locked = false
                                xDominant = false
                                lastMoveUptimeMillis = alt.uptimeMillis
                                tracker.addPosition(alt.uptimeMillis, alt.position)
                                continue
                            }
                            // 拖课卡那根还被屏蔽着：本轮先落页，手势继续等下一根手指
                            val blockedId = latestBlockedPointer.value()
                            val blockedStillDown = blockedId != null &&
                                event.changes.any { it.id == blockedId && it.pressed }
                            if (blockedStillDown) {
                                finishRound()
                                down = event.changes.first { it.id == blockedId }
                                isAltPointer = false
                                continue
                            }
                            break
                        }
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

                                yDominant -> locked = true
                                // 两轴接近：取位移大的一轴锁定，不并行
                                ax >= touchSlop && ay >= touchSlop -> {
                                    xDominant = ax >= ay
                                    locked = true
                                }
                            }
                            // ⚠ 判活而不是判空：worker 会被外部掐死（切 tab 的 cancelScroll
                            // 取消 mutator），协程结束但引用仍非空。只看 == null 就不再重建，
                            // 后续 delta 会送进没人读的 channel、事件还照旧 consume →
                            // 整页再也翻不动。
                            if (xDominant && scrollWorker?.isActive != true) {
                                dragChannel?.close()
                                dragChannel = null
                                scrollWorker?.cancel()
                                scrollWorker = null
                                workerDone = CompletableDeferred()
                                settleJob.value?.cancel()
                                settleJob.value = null
                                overscrollJob.value?.cancel()
                                overscrollJob.value = null
                                val channel = Channel<Float>(Channel.UNLIMITED)
                                dragChannel = channel
                                val roundDone = workerDone
                                scrollWorker = scope.launch {
                                    try {
                                        pagerState.scroll(MutatePriority.UserInput) {
                                            if (!startCaptured) {
                                                dragState.startScrollOffset =
                                                    pagerState.currentPage + pagerState.currentPageOffsetFraction
                                                startCaptured = true
                                            }
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
                                        roundDone.complete(Unit)
                                    }
                                }
                            }
                        }
                        if (!xDominant) {
                            // 第二只手纵向滑动：verticalScroll 在跟被屏蔽那根手指，看不到这根。
                            // 第一只手时纵向归 verticalScroll，避免双重滚。
                            if (isAltPointer && locked && dy != 0f) {
                                latestOnVerticalScroll.value?.invoke(dy)
                                change.consume()
                            }
                            continue
                        }
                        dragChannel?.trySend(-dx)
                        // Initial consume：子级（verticalScroll / 课卡手势）本帧不再看到位移
                        change.consume()
                    }
                } finally {
                    dragChannel?.close()
                    dragChannel = null
                }
                settleCurrentRound()
                scrollWorker = null
            }
        }
}
