package com.haooz.chedule.ui.components.slider

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/**
 * 触摸橡皮筋位移，对应 COUI 的 `COUIRubberAnimatorUnit`。
 *
 * 手指拖动时整条滑条跟随平移，越界部分按 [rubber] 公式阻尼收敛，最多平移
 * `28dp × density`。拖动期间直接贴手（不经过弹簧），松手后按起始速度弹回原位。
 *
 * @param scope 承载回弹动画与生命周期协程的作用域。
 * @param density 当前屏幕密度，用于把 28dp / 56dp / 100dp 换算成像素。
 */
internal class NexioTouchMotion(
    scope: CoroutineScope,
    private val density: Float,
) {
    private var disposed = false
    private val translationXSpring = NexioSpring(0f, scope, 0.5f)
    private val translationYSpring = NexioSpring(0f, scope, 0.5f)
    private val stretchDistance = round(28f * density)
    private var rubberOrigin = Offset.Zero
    private var targetSize = IntSize.Zero
    private var dragging = false
    private var moved = false

    /** 当前横向位移，供 `graphicsLayer` 读取。 */
    val translationX: State<Float> = translationXSpring.state

    /** 当前纵向位移；滑条只做横向拖动，正常为 0。 */
    val translationY: State<Float> = translationYSpring.state

    private val lifetime = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            disposed = true
            cancel()
        }
    }

    /**
     * 开始一次拖动。
     *
     * 先把当前位移反解成「等效起点」，保证上一轮未回零的位移不会在按下瞬间跳变。
     *
     * @param position 按下点位置。
     * @param size 滑条尺寸，用于松手时按面积决定回弹参数。
     */
    fun begin(position: Offset, size: IntSize) {
        if (disposed) return
        cancel()
        targetSize = size
        rubberOrigin = position - Offset(
            inverseRubber(translationX.value, 0.05f, stretchDistance),
            inverseRubber(translationY.value, 0.05f, stretchDistance),
        )
        dragging = true
        moved = translationX.value != 0f || translationY.value != 0f
    }

    /** 多指切换时补偿起点，避免位移跳变。 */
    fun switchPointer(previous: Offset?, next: Offset) {
        if (previous == null) return
        rubberOrigin += next - previous
    }

    /** 更新位移；超过 5px 才算真正移动。 */
    fun move(position: Offset) {
        if (!dragging) return
        val rubberDelta = position - rubberOrigin
        if (abs(rubberDelta.x) >= 5f || abs(rubberDelta.y) >= 5f) {
            moved = true
            // 位移与形变直接贴手；弹簧跟随在快速滑动时会落后手指一截。
            translationXSpring.snapTo(rubber(rubberDelta.x, 0.05f, stretchDistance))
            translationYSpring.snapTo(rubber(rubberDelta.y, 0.05f, stretchDistance))
        }
    }

    /**
     * 松手回弹。
     *
     * @param velocity 抬起时的手指速度（像素/秒），用于计算回弹的起始速度与阻尼。
     */
    fun release(velocity: Offset) {
        if (!dragging) return
        dragging = false
        if (!moved) return
        val smallSide = 56f * density
        val largeSide = 100f * density
        val area = targetSize.width.toFloat() * targetSize.height
        val fraction = ((area - smallSide * smallSide) /
            (largeSide * largeSide - smallSide * smallSide)).coerceIn(0f, 1f)
        val response = 0.42f + (0.45f - 0.42f) * HandUpAreaEasing.transform(fraction)
        releaseAxis(translationXSpring, 0f, velocity.x, response)
        releaseAxis(translationYSpring, 0f, velocity.y, response)
    }

    /** 停在当前位置，保留速度并清除尚未处理的目标。 */
    fun cancel() {
        translationXSpring.cancel()
        translationYSpring.cancel()
        dragging = false
    }

    /** 释放协程与事件会话。 */
    internal fun dispose() {
        disposed = true
        cancel()
        lifetime.cancel()
    }

    private fun releaseAxis(
        axis: NexioSpring,
        target: Float,
        velocity: Float,
        response: Float,
    ) {
        val rest = abs(axis.value - target)
        // 手抬归一化距离按 maxStretchDistance。
        val initialVelocity = (velocity / max(28f * density, 1f) * rest).coerceIn(-1000f, 1000f)
        val bounce = max(0.65f, min(abs(initialVelocity) / 1000f, 1f))
        axis.animateTo(target, response, bounce, velocity = initialVelocity)
    }

    /** 越界位移的阻尼曲线：距离越大，增长越慢，上限为 [maximum]。 */
    private fun rubber(distance: Float, ratio: Float, maximum: Float): Float =
        if (maximum <= 0f) 0f else
            sign(distance) * (1f - 1f / (abs(distance) * ratio / maximum + 1f)) * maximum

    /** [rubber] 的反函数，用于把当前位移反解成等效起点。 */
    private fun inverseRubber(value: Float, ratio: Float, maximum: Float): Float {
        if (maximum <= 0f) return value
        val magnitude = abs(value)
        if (magnitude >= maximum || ratio <= 0f) return sign(value) * maximum
        return sign(value) * magnitude * maximum / (ratio * (maximum - magnitude))
    }

    private companion object {
        /** COUI 手抬面积缓动曲线。 */
        val HandUpAreaEasing = CubicBezierEasing(0.55026454f, 0f, 0.8915344f, 0.18783069f)
    }
}
