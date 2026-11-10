package com.haooz.chedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.OverscrollMath

/** 横向切页位置来源：页间位置与越界量，单位均为页。 */
@Stable
class PageSwitchState(initialPage: Int) {
    /** 页间位置。 */
    internal val position = Animatable(initialPage.toFloat())

    /** 越界位移（相对边界，带符号）；屏上位置 = [position] + 它。 */
    internal var edgeExtra by mutableFloatStateOf(0f)

    /** 拖动期未阻尼的手指位置。 */
    internal var dragRaw by mutableFloatStateOf(initialPage.toFloat())

    internal var dragging by mutableStateOf(false)
    internal var settleJob by mutableStateOf<Job?>(null)

    /** 已下令的目标页。 */
    internal var commanded by mutableIntStateOf(initialPage)

    /** 落页协程作用域。 */
    internal var scope: CoroutineScope? = null

    /** 页数，只用于夹限。 */
    var pageCount: Int = 1

    /** 上一次落定的页号。 */
    private var settledBacking by mutableIntStateOf(initialPage)
    val settledPage: Int get() = settledBacking

    internal val lastPage: Int get() = (pageCount - 1).coerceAtLeast(0)
    internal val last: Float get() = lastPage.toFloat()

    /** 屏上位置（页，小数）。 */
    internal val visual: Float
        get() = if (dragging) resistEdge(dragRaw, last) else position.value + edgeExtra

    private val pageIndexState = derivedStateOf {
        visual.roundToInt().coerceIn(0, lastPage)
    }
    private val animatingState = derivedStateOf {
        dragging || settleJob?.isActive == true || position.isRunning
    }

    /** 当前页（过半即翻）。 */
    val currentPage: Int get() = pageIndexState.value

    /** 相对当前页的偏移，取值 -0.5..0.5。 */
    val offsetFraction: Float
        get() {
            val shown = visual
            return shown - shown.roundToInt()
        }

    /** 是否正在拖动或播放动画。 */
    val isAnimating: Boolean get() = animatingState.value

    /** 平移到指定页；目标变化会取消上一条动画。 */
    suspend fun animateTo(page: Int, animationSpec: AnimationSpec<Float> = SwitchSpecPages) {
        val target = page.coerceIn(0, lastPage)
        if (commanded == target && position.isRunning) return
        commanded = target
        settleJob?.cancel()
        settleJob = null
        if (edgeExtra != 0f) {
            position.snapTo(position.value + edgeExtra)
            edgeExtra = 0f
        }
        if (position.value != target.toFloat()) {
            position.animateTo(target.toFloat(), animationSpec)
        }
        land()
    }

    /** 无动画落到指定页。 */
    suspend fun snapTo(page: Int) {
        val target = page.coerceIn(0, lastPage)
        commanded = target
        settleJob?.cancel()
        settleJob = null
        edgeExtra = 0f
        dragging = false
        position.snapTo(target.toFloat())
        settledBacking = target
    }

    /** 停止动画，停在当前值。 */
    suspend fun cancel() {
        settleJob?.cancel()
        settleJob = null
        position.stop()
    }

    // ───────────────── 拖动 ─────────────────

    /** 开始拖动，接上当前屏上位置。 */
    internal fun beginDrag() {
        settleJob?.cancel()
        settleJob = null
        scope?.launch { position.stop() }
        dragRaw = rawFromDisplay(position.value + edgeExtra, last)
        edgeExtra = 0f
        dragging = true
    }

    /** [deltaPages] 为未阻尼的手指位移（正 = 页码变大）。 */
    internal fun dragBy(deltaPages: Float) {
        dragRaw = (dragRaw + deltaPages).coerceIn(-PAGE_RANGE, last + PAGE_RANGE)
    }

    /**
     * 松手落页，返回落点页。
     *
     * @param velocityPages 松手速度（页/秒，正 = 往下一页）
     * @param pageWidthPx 一页的像素宽
     */
    internal fun release(velocityPages: Float, pageWidthPx: Float): Int {
        if (!dragging) return currentPage
        val last = last
        val widthPx = pageWidthPx.coerceAtLeast(1f)
        val releasedPos = resistEdge(dragRaw, last)
        val thisScope = scope
        val edgePage = when {
            releasedPos < 0f -> 0
            releasedPos > last -> lastPage
            else -> -1
        }
        settleJob?.cancel()
        if (edgePage >= 0) {
            commanded = edgePage
            settleJob = thisScope?.launch {
                position.snapTo(edgePage.toFloat())
                edgeExtra = releasedPos - edgePage
                dragging = false
                OverscrollMath.settle(
                    from = edgeExtra * widthPx,
                    initialVelocity = OverscrollMath.settleVelocity(velocityPages * widthPx),
                ) { offsetPx -> edgeExtra = offsetPx / widthPx }
                edgeExtra = 0f
                settledBacking = edgePage
            }
            return edgePage
        }
        // 落点 = 当前位置 + 速度投影，最多跨一页
        val anchor = dragRaw.roundToInt().coerceIn(0, lastPage)
        val projected = releasedPos + velocityPages * FLING_PROJECTION_SECONDS
        val target = projected.roundToInt().coerceIn(
            (anchor - 1).coerceAtLeast(0),
            (anchor + 1).coerceAtMost(lastPage),
        )
        commanded = target
        settleJob = thisScope?.launch {
            position.snapTo(releasedPos)
            dragging = false
            val limit = abs(target - releasedPos) * SETTLE_VELOCITY_PER_PAGE
            position.animateTo(
                targetValue = target.toFloat(),
                animationSpec = SnapSpecPages,
                initialVelocity = velocityPages.coerceIn(-limit, limit),
            )
            settledBacking = target
        }
        return target
    }

    /** 借位跳页被打断时换成真身页，并平移手指基准。 */
    internal suspend fun adoptBorrowedPage(dest: Int) {
        val shift = dest.toFloat() - position.value
        position.snapTo(dest.toFloat())
        if (dragging) dragRaw += shift
        commanded = dest
        if (!dragging) settledBacking = dest
    }

    /** 动画收尾后刷新 [settledPage]。 */
    private fun land() {
        if (dragging) return
        settledBacking = position.value.roundToInt().coerceIn(0, lastPage)
    }
}

@Composable
fun rememberPageSwitchState(initialPage: Int): PageSwitchState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PageSwitchState(initialPage).also { it.scope = scope } }
}

/** 越界段的屏上位移（阻尼后）。 */
private fun resistEdge(raw: Float, lastPage: Float): Float {
    val over = when {
        raw < 0f -> raw
        raw > lastPage -> raw - lastPage
        else -> return raw
    }
    val damped = OverscrollMath.dampedDistance(over, PAGE_RANGE)
    return if (over < 0f) damped else lastPage + damped
}

/** 屏上位移 → 等效手指位移。 */
private fun rawFromDisplay(shown: Float, lastPage: Float): Float = when {
    shown < 0f -> OverscrollMath.rawDistance(shown, PAGE_RANGE)
    shown > lastPage -> lastPage + OverscrollMath.rawDistance(shown - lastPage, PAGE_RANGE)
    else -> shown
}

/** 点选切页曲线（页单位）。 */
private val SwitchSpecPages: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 320f,
    visibilityThreshold = 5e-5f,
)

/** 手势落页吸附曲线。 */
private val SnapSpecPages: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 300f,
    visibilityThreshold = 5e-5f,
)

/** 越界阻尼归一化基准（页）。 */
private const val PAGE_RANGE = 1f

/** 松手速度折算位移的等效时长（秒）。 */
private const val FLING_PROJECTION_SECONDS = 0.35f

/** 收尾弹簧初速的每页距离上限（页/秒/页）。 */
private const val SETTLE_VELOCITY_PER_PAGE = 3.2f
