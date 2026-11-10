package com.haooz.chedule.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 从哪里抄的：小米时钟 `miuix.navigation.OverlayView`（`BottomNavigator` 里的那颗选中胶囊）。
 *
 * 位置模型**不是** translationX + 固定宽度，而是 left / right 两条边界各挂一条弹簧：
 * 切换时“先到的边”用快弹簧、“落后的边”用慢弹簧，位移途中宽度先被撑开再收回来。
 *
 * 原版一个 folme state 配多套 `AnimConfig`（EaseStyle 随时切），这里照抄这个结构：
 * 两个 [Channel] 各自持有 target + 当前刚度/阻尼，按需切参数，由一个帧循环统一推进。
 * 之所以不用 `Animatable`：拖动时目标每帧都变，`animateTo` 要每帧重启一次动画协程；
 * 直接积分同一个弹簧既没有重启开销，也天然是原版那种“目标一直在动、弹簧一直在追”的行为。
 *
 * 弹簧参数按 `ω = sqrt(stiffness)`、`c = 2ζω` 落到物理形式，与 `spring(stiffness, dampingRatio)`
 * 手感等价（数值沿用接入前那套）。
 */
internal class CapsuleSelector(
    private val scope: CoroutineScope,
    /** 胶囊静止宽度（一格 tab 减去两侧内缩）。tab 数变化时整体重建，不做插值。 */
    private val widthPx: Float,
    startLeftPx: Float,
    /** 胶囊左边界允许的范围（含 trail 溢出后的夹限）。原版到边就不再拖尾。 */
    private val leftLimit: () -> ClosedRange<Float>,
) {

    private val left = Channel(startLeftPx)
    private val right = Channel(startLeftPx + widthPx)

    /**
     * 拖动期的收尾：原版 `spring(1.0, 0.15)` —— 临界阻尼、0.15s 响应。
     * 它比“1:1 直读”软一档，手指停下时胶囊还会往前蹭一点点，这就是原版跟手的质感。
     */
    private val dragStiffness = omega(0.15f) * omega(0.15f)
    private val dragDamping = 2f * 1f * omega(0.15f)

    private var running = false

    val leftPx: Float get() = left.value
    val rightPx: Float get() = right.value

    /** 视觉中心。按在滑块上、松手吸附最近槽位都用它，比逻辑 index 准。 */
    val centerPx: Float get() = (left.value + right.value) * 0.5f

    /**
     * 跟手拖动。[leftPx] 是胶囊静止左边界（不含 trail），[trailPx] 是尾巴拉伸量，
     * [dir] 为屏幕方向（>0 向右）。原版只往运动反方向那一侧长：向右拖长左边，向左拖长右边。
     */
    fun onDrag(leftPx: Float, trailPx: Float, dir: Int) {
        left.useSpring(dragStiffness, dragDamping)
        right.useSpring(dragStiffness, dragDamping)
        val range = leftLimit()
        left.target = (leftPx - if (dir > 0) trailPx else 0f).coerceIn(range)
        right.target = (leftPx + if (dir < 0) trailPx else 0f).coerceIn(range) + widthPx
        ensureLoop()
    }

    /**
     * 收尾 / 切换：两条边分别用快慢弹簧追目标，[forward] 表示目标在胶囊当前所在位置的右侧
     * （几何方向，与 RTL 无关 —— RTL 下 index 增大是往左走，forward 自然变成 false）。
     */
    fun settleTo(leftPx: Float, forward: Boolean) {
        val targetLeft = leftPx.coerceIn(leftLimit())
        val targetRight = targetLeft + widthPx
        if (targetLeft == left.target && targetRight == right.target) return

        // 快边先到、慢边后到 —— 宽度中途被撑开再收回，橡皮筋感就是这两条线速度差造出来的
        if (forward) {
            right.useLeading()
            left.useTrailing()
        } else {
            left.useLeading()
            right.useTrailing()
        }
        left.target = targetLeft
        right.target = targetRight
        ensureLoop()
    }

    /** 停在当前位置、速度归零，等下一次 [settleTo] 重新吸附。 */
    fun freeze() {
        left.freeze()
        right.freeze()
    }

    private fun ensureLoop() {
        if (running) return
        running = true
        scope.launch {
            var lastNanos = 0L
            while (isActive) {
                val nanos = withFrameNanos { it }
                val rawDt = if (lastNanos == 0L) 1f / 60f else (nanos - lastNanos) / 1_000_000_000f
                lastNanos = nanos
                // 掉帧时 dt 不能太大，否则显式积分会发散
                val dt = rawDt.coerceIn(1f / 240f, 1f / 30f)
                if (!step(dt)) break
            }
            running = false
            // 退出瞬间目标又被改过（松手与点按几乎同帧）时补一轮，否则会停在半路
            if (left.needsTick() || right.needsTick()) ensureLoop()
        }
    }

    private fun step(dt: Float): Boolean {
        val leftActive = left.advance(dt)
        val rightActive = right.advance(dt)
        return leftActive || rightActive
    }

    private class Channel(start: Float) {
        var value by mutableFloatStateOf(start)
        var target = start
        private var velocity = 0f
        private var stiffness = leadingStiffness
        private var damping = leadingDamping

        fun useLeading() {
            stiffness = leadingStiffness
            damping = leadingDamping
        }

        fun useTrailing() {
            stiffness = trailingStiffness
            damping = trailingDamping
        }

        fun useSpring(stiffness: Float, damping: Float) {
            this.stiffness = stiffness
            this.damping = damping
        }

        fun needsTick(): Boolean =
            abs(value - target) > SETTLE_PX || abs(velocity) > SETTLE_VELOCITY

        fun freeze() {
            target = value
            velocity = 0f
        }

        /** 半隐式欧拉，按 [MAX_STEP] 切子步；返回是否还在动。 */
        fun advance(dt: Float): Boolean {
            val steps = ceil(dt / MAX_STEP).toInt().coerceAtLeast(1)
            val h = dt / steps
            repeat(steps) {
                val accel = (target - value) * stiffness - velocity * damping
                velocity += accel * h
                value += velocity * h
            }
            if (!needsTick()) {
                value = target
                velocity = 0f
                return false
            }
            return true
        }
    }
}

/** 快弹簧 ≈ 原版 `spring(0.7, 0.4)`：位移方向先到的那条边。 */
private const val leadingStiffness = 260f
private val leadingDamping = 2f * 0.72f * sqrt(leadingStiffness)

/** 慢弹簧 ≈ 原版 `spring(0.75, 0.5)`：落后那条边，它才是拉伸感的来源。 */
private const val trailingStiffness = 165f
private val trailingDamping = 2f * 0.8f * sqrt(trailingStiffness)

/** 积分子步上限，保证显式积分在高刚度下不发散。 */
private const val MAX_STEP = 1f / 240f

/** 收敛判定：位置（px）与速度（px/s）。 */
private const val SETTLE_PX = 0.05f
private const val SETTLE_VELOCITY = 2f

/** response（秒）→ 弹簧固有角频率。原版 folme 的 response 语义。 */
private fun omega(responseSeconds: Float): Float = 6.2831855f / responseSeconds

/**
 * 把滑块按「左边界 + 宽度」放进父级，宽度与位置都在 **layout 期** 读
 * [CapsuleSelector] 的 state —— 组合期一个高频值都不读，只 relayout 这一个节点。
 *
 * 之所以不借助 `Modifier.width(...)`：那要在组合期取动画值，每帧整节点重组。
 * 之所以不用 draw 期自己画圆角矩形：胶囊必须是 G2 `ContinuousCapsule`，自画会退化成普通圆角，
 * 端头曲率肉眼可见地不一样。
 */
internal fun Modifier.capsuleBounds(
    selector: CapsuleSelector,
    heightPx: Int,
): Modifier = layout { measurable, constraints ->
    val left = selector.leftPx.roundToInt()
    val width = (selector.rightPx - selector.leftPx).roundToInt().coerceAtLeast(1)
    val height = if (constraints.hasBoundedHeight) {
        heightPx.coerceAtMost(constraints.maxHeight)
    } else {
        heightPx
    }
    val placeable = measurable.measure(Constraints.fixed(width, height))
    val reportWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else width
    val reportHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else height
    layout(reportWidth, reportHeight) {
        placeable.place(left, ((reportHeight - height) / 2).coerceAtLeast(0))
    }
}
