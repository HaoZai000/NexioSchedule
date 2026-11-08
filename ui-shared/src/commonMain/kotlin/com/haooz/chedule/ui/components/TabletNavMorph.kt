package com.haooz.chedule.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 只给现有 TabletNavSideBar 的那一个面板容器提供「四边界连续变化」的能力
 */

/** 一条 folme spring 通道（欠阻尼解析解）。 */
private class Spring(value: Float) {
    var value by mutableFloatStateOf(value)
        private set
    var target = value
        private set
    private var velocity = 0f
    private var damping = 0.82f
    private var response = 0.4f

    /** 启动延迟（毫秒）：帧循环里到这个时间才开始走。真实时间错落靠它。 */
    var delayMs = 0f

    fun configure(damping: Float, response: Float, target: Float) {
        this.damping = damping
        this.response = response
        this.target = target
    }

    fun step(dt: Float): Boolean {
        val zeta = damping
        val omega = 6.283185307179586f / response
        val c = 2f * omega * zeta
        val offset = value - target
        val decay = exp(c * (-0.5f) * dt)
        val wd = omega * sqrt(1f - zeta * zeta)
        val q = ((c * 0.5f * offset) + velocity) / wd
        val wt = wd * dt
        val off = ((q * sin(wt)) + (offset * cos(wt))) * decay
        velocity = (((q * cos(wt)) - (offset * sin(wt))) * wd * decay) - (0.5f * off * c)
        value = off + target
        if (abs(value - target) < 0.002f && abs(velocity) < 0.05f) {
            value = target
            velocity = 0f
            return false
        }
        return true
    }
}

/** 一组 X/Y 双弹簧；progress 0 = 侧栏，1 = 顶栏胶囊。 */
class TabletNavMorphMotion(
    private val scope: CoroutineScope,
    startProgress: Float,
    itemCount: Int,
    /** 每帧发布进度：页面内容区与面板共用同一个时钟，避免页面跳变。 */
    private val onFrame: (Float) -> Unit = {},
) {
    /** 总进度：不参与错落，容器/页面避让/形状都读它 */
    private val masterX = Spring(startProgress)
    private val masterY = Spring(startProgress)
    private val xs = List(itemCount) { Spring(startProgress) }
    private val ys = List(itemCount) { Spring(startProgress) }
    private var running = false
    /** 当前这一轮动画已经跑了多久（毫秒），启动延迟按它判断 */
    private var elapsedMs = 0f
    var lastTarget = startProgress
        private set

    val x: Float get() = masterX.value
    val y: Float get() = masterY.value
    fun x(index: Int) = xs[index.coerceIn(0, xs.lastIndex)].value
    fun y(index: Int) = ys[index.coerceIn(0, ys.lastIndex)].value

    init {
        onFrame(startProgress)
    }

    fun to(target: Float) {
        val dX: Float
        val rX: Float
        val dY: Float
        val rY: Float
        if (target == 0f) { // 展开回侧栏
            dX = 0.97f
            rX = 0.2f
            // 纵向压到 0.72：面板长到底时底缘要明显冲过终点再缩回
            dY = 0.72f
            rY = 0.4f
        } else { // 收成顶栏
            dX = 0.82f
            rX = 0.4f
            dY = 0.97f
            rY = 0.2f
        }
        // 总进度不参与错落，容器 / 页面避让 / 形状都读它
        masterX.configure(dX, rX, target)
        masterY.configure(dY, rY, target)
        // 真实时间错落：收顶栏从最下面一条先走，展开从最上面一条先归位
        val toTop = target > 0.5f
        xs.forEachIndexed { i, ch ->
            ch.configure(dX, rX, target)
            ch.delayMs = (if (toTop) (xs.lastIndex - i) else i) * ITEM_STAGGER_MS
        }
        ys.forEachIndexed { i, ch ->
            ch.configure(dY, rY, target)
            ch.delayMs = (if (toTop) (ys.lastIndex - i) else i) * ITEM_STAGGER_MS
        }
        // 每次起新动画都从 0 计时，否则沿用上一次的 elapsed，延迟会直接失效
        elapsedMs = 0f
        lastTarget = target
        ensureLoop()
    }

    private fun ensureLoop() {
        if (running) return
        running = true
        scope.launch {
            var last = 0L
            while (isActive) {
                val nanos = withFrameNanos { it }
                val dt = if (last == 0L) 1f / 60f else (nanos - last) / 1_000_000_000f
                last = nanos
                elapsedMs += dt * 1000f
                val h = dt.coerceIn(1f / 240f, 1f / 30f)
                var any = false
                masterX.step(h)
                masterY.step(h)
                // 还没到点的通道要算「仍在跑」，否则循环会提前收工
                xs.forEach { any = (if (elapsedMs >= it.delayMs) it.step(h) else true) || any }
                ys.forEach { any = (if (elapsedMs >= it.delayMs) it.step(h) else true) || any }
                onFrame(masterX.value)
                if (!any) break
            }
            running = false
        }
    }
}

/** 形变（宽/高/内部横排）在这个进度前就走完，剩下的时间只留给整体横移与回弹。 */
private const val MORPH_SETTLE_AT = 0.8f

/** 落地回弹最大下沉量 */
private val LANDING_BOUNCE_MAX = 8.dp

/**
 * 相邻条目的启动时差（毫秒）
 */
private const val ITEM_STAGGER_MS = 20f

/**
 * 形变进度：把 [TabletNavSideState.morphProgress] 提前到 [MORPH_SETTLE_AT] 收尾。
 * 只在 layout / draw 期调用。
 */
internal fun tabletNavSettleProgress(): Float =
    (TabletNavSideState.morphProgress.floatValue / MORPH_SETTLE_AT).coerceIn(0f, 1f)

/**
 * 横移进度：**不夹上界** —— 弹簧过冲要能透出来，胶囊才会在结尾冲出去再回正。
 */
internal fun tabletNavSlideProgress(): Float =
    TabletNavSideState.morphProgress.floatValue.coerceAtLeast(0f)

/**
 * 挂在现有侧栏面板容器链首：在 layout 期按 X/Y 弹簧逐帧改这个**同一个**容器的
 * 四边界（宽/高/位置），从侧栏面板连续变成顶部胶囊。
 *
 * 宽度直接从两个端点的几何插值出来，所以内层只负责画，不再自己算宽度 ——
 * 这样「侧栏宽 → 胶囊宽」是一条曲线，不会出现两套宽度互相打架。
 */
internal fun Modifier.tabletNavMorphPanel(
    sideInsetPx: Float,
    sideTopPx: Float,
    /** 胶囊态的顶缘：折叠成顶栏时贴得更高一点（侧栏态仍是 [sideTopPx]） */
    capsuleTopPx: Float,
    sideBottomInsetPx: Float,
    /** 侧栏态宽度（含展开弹簧过冲），layout 期现读 */
    sideWidthPx: () -> Float,
    capsuleWidthPx: Float,
    capsuleHeightPx: Float,
    morphX: () -> Float,
    morphY: () -> Float,
): Modifier = layout { measurable, constraints ->
    val hostW = constraints.maxWidth.toFloat()
    val hostH = constraints.maxHeight.toFloat()
    val px = morphX().coerceAtLeast(0f)
    // py 同样不夹上界：展开回侧栏时底缘要靠它冲过终点
    val py = morphY().coerceAtLeast(0f)
    // 宽高提前收尾；水平位置吃满整条弹簧（含过冲）
    val pShape = (px / MORPH_SETTLE_AT).coerceIn(0f, 1f)
    val sideW = sideWidthPx()
    val sideH = hostH - sideBottomInsetPx - sideTopPx
    val capLeft = (hostW - capsuleWidthPx) * 0.5f
    val w = sideW + (capsuleWidthPx - sideW) * pShape
    // 底缘「落地」回弹：显式动画（TabletNavSideState.landBounce）驱动，
    // 不再依赖 spring 那 2~4% 的过冲（被底部留白吃掉后肉眼为零）。
    val bottomOver = TabletNavSideState.landBounce.floatValue *
        LANDING_BOUNCE_MAX.roundToPx()
    val h = sideH + (capsuleHeightPx - sideH) * pShape + bottomOver
    val left = sideInsetPx + (capLeft - sideInsetPx) * px
    val top = sideTopPx + (capsuleTopPx - sideTopPx) * py.coerceIn(0f, 1f)
    val wi = w.roundToInt().coerceAtLeast(1)
    val hi = h.roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(wi, hi))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(left.roundToInt(), top.roundToInt())
    }
}

/**
 * 面板内容层固定尺寸：**不受**当前容器边界影响。
 *
 * 变形过程中容器高宽逐帧变，若内容跟着容器测量，整棵侧栏树会每帧重排（文字重排最贵）。
 * 这里让每一层都按自己的目标尺寸布局一次，容器只用裁剪决定露出多少 ——
 * 变形期间内容层零重排，只有 alpha 与裁剪在变。
 */
internal fun Modifier.tabletNavFixedSize(
    widthPx: () -> Float,
    heightPx: () -> Float,
): Modifier = layout { measurable, constraints ->
    val w = widthPx().roundToInt().coerceAtLeast(1)
    val h = heightPx().roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(w.coerceAtMost(constraints.maxWidth), h.coerceAtMost(constraints.maxHeight)) {
        placeable.place(0, 0)
    }
}

/**
 * 单个条目从「侧栏行」连续移到「胶囊槽位」。
 *
 * 对外报告始终是侧栏态的尺寸 —— 父级 Column 的流式布局在整段动画里一动不动；
 * 真正的子节点按插值后的宽高测量、按插值后的目标点放置，超出部分由面板裁掉。
 * 进度只在 layout 期读。
 */
internal fun Modifier.tabletNavMorphItem(
    /** 侧栏态自身宽度（跟展开/折叠走），layout 期现读 */
    sideWidthPx: () -> Float,
    sideHeightPx: Float,
    /** 本条目自己的进度（带错落延迟） */
    progress: () -> Float,
    /** 目标点相对本条目原点的偏移（可以超出自身，甚至为负 —— 会被面板裁剪） */
    targetLeftPx: Float,
    targetTopPx: Float,
    targetWidthPx: Float,
    targetHeightPx: Float,
): Modifier = layout { measurable, constraints ->
    val p = progress()
    val fromW = sideWidthPx()
    val w = (fromW + (targetWidthPx - fromW) * p).roundToInt().coerceAtLeast(1)
    val h = (sideHeightPx + (targetHeightPx - sideHeightPx) * p).roundToInt().coerceAtLeast(1)
    val x = (targetLeftPx * p).roundToInt()
    val y = (targetTopPx * p).roundToInt()
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(fromW.roundToInt().coerceAtLeast(1), sideHeightPx.roundToInt().coerceAtLeast(1)) {
        placeable.place(x, y)
    }
}

/**
 * 第 [index] 条自己的 morph 进度：来自它那条带启动延迟的通道，
 * 再套上 [MORPH_SETTLE_AT] 的提前收尾（形变先走完，剩下留给横移）。
 */
internal fun tabletNavItemProgress(motion: TabletNavMorphMotion, index: Int): Float =
    (motion.x(index) / MORPH_SETTLE_AT).coerceIn(0f, 1f)

/** 条目内部某个量随 morph 从 [fromPx] 变到 [toPx] 的方块占位（图标、间距共用）。 */
internal fun Modifier.tabletNavMorphSize(fromPx: Float, toPx: Float): Modifier =
    layout { measurable, constraints ->
        val p = tabletNavSettleProgress()
        val v = (fromPx + (toPx - fromPx) * p).roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(v, v))
        layout(v, v) { placeable.place(0, 0) }
    }

/** 宽高各自插值：让一颗 40dp 圆钮长成 48×52 的胶囊格。 */
internal fun Modifier.tabletNavMorphBoxSize(
    widthFromPx: Float,
    heightFromPx: Float,
    widthToPx: Float,
    heightToPx: Float,
): Modifier = layout { measurable, constraints ->
    val p = tabletNavSettleProgress()
    val w = (widthFromPx + (widthToPx - widthFromPx) * p).roundToInt().coerceAtLeast(1)
    val h = (heightFromPx + (heightToPx - heightFromPx) * p).roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(w, h) { placeable.place(0, 0) }
}


/**
 * 唯一那一层材质用的形状：侧栏圆角 → 胶囊（h/2），按 morph 进度连续插值。
 * createOutline 在 draw 期调用，读 progress 只触发重绘、不进组合。
 * 轮廓仍由项目唯一的 G2 [ContinuousRoundedRectangle] 生成，不自画圆角。
 */
internal class TabletNavMorphShape(private val sideCornerPx: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val p = tabletNavSettleProgress()
        val capsule = min(size.width, size.height) / 2f
        val radiusPx = sideCornerPx + (capsule - sideCornerPx) * p
        return ContinuousRoundedRectangle(with(density) { radiusPx.toDp() })
            .createOutline(size, layoutDirection, density)
    }

    override fun equals(other: Any?): Boolean =
        other is TabletNavMorphShape && sideCornerPx == other.sideCornerPx

    override fun hashCode(): Int = sideCornerPx.hashCode()
}
