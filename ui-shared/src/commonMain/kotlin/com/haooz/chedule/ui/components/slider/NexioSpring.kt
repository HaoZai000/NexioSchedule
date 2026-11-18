package com.haooz.chedule.ui.components.slider

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.PI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 单通道弹簧动画，对应 COUI 的 `CouiSpring`。
 *
 * 原实现基于 `androidx.dynamicanimation.SpringAnimation`，跨平台版改用 Compose 的
 * [spring] 表达同一组物理参数。两者都是二阶弹簧，落点与收敛速度一致：
 *
 * | 参数 | 含义 | 换算 |
 * | --- | --- | --- |
 * | `response` | 固有振荡周期，单位秒 | `stiffness = (2π / response)²` |
 * | `bounce` | 回弹幅度，0 为临界阻尼 | `dampingRatio = 1 - bounce` |
 *
 * 运行中再次调用 [animateTo] 会沿用当前速度重定向（等价于
 * `SpringAnimation.animateToFinalPosition`），因此连续喂目标时不会从静止重新起步。
 *
 * @param initial 初始值。
 * @param scope 承载动画协程的作用域，通常取 `rememberCoroutineScope()`。
 * @param minimumChange 默认收敛阈值，与目标距离小于该值时视为已到达。
 * @param onUpdate 每帧数值更新后的回调，供不经过 Compose 状态的消费者感知变化。
 */
internal class NexioSpring(
    initial: Float,
    private val scope: CoroutineScope,
    private val minimumChange: Float = 1f,
    private val onUpdate: () -> Unit = {},
) {
    private val position = mutableFloatStateOf(initial)
    private var job: Job? = null
    private var onFrame: ((Float) -> Unit)? = null
    private var onEnd: (() -> Unit)? = null

    /** 只读快照状态，供 `Canvas`、`graphicsLayer` 等绘制阶段读取。 */
    val state: State<Float> get() = position

    /** 当前值；非快照读取，适合在事件回调中使用。 */
    val value: Float get() = position.floatValue

    /** 是否有动画正在运行。 */
    val isRunning: Boolean get() = job?.isActive == true

    /** 最近一帧的速度（单位/秒），用于重定向时保持连续性。 */
    var velocity: Float = 0f
        private set

    /**
     * 启动动画，或把运行中的动画重定向到 [target]。
     *
     * @param target 目标值。
     * @param response 固有振荡周期，单位秒；越小越快。
     * @param bounce 回弹幅度，0 为临界阻尼。
     * @param minimumChange 收敛阈值，默认取构造参数。
     * @param velocity 起始速度；为 null 时沿用 [velocity]。
     * @param onFrame 每帧回调，参数为当前插值结果。
     * @param onEnd 正常结束时回调；被重定向或取消时不触发。
     */
    fun animateTo(
        target: Float,
        response: Float,
        bounce: Float = 0f,
        minimumChange: Float = this.minimumChange,
        velocity: Float? = null,
        onFrame: ((Float) -> Unit)? = null,
        onEnd: (() -> Unit)? = null,
    ) {
        this.onFrame = onFrame
        this.onEnd = onEnd
        val startVelocity = velocity ?: this.velocity
        job?.cancel()
        val stiffness = ((2.0 * PI / response) * (2.0 * PI / response)).toFloat()
        job = scope.launch {
            try {
                animate(
                    initialValue = position.floatValue,
                    targetValue = target,
                    initialVelocity = startVelocity,
                    animationSpec = spring(
                        dampingRatio = 1f - bounce,
                        stiffness = stiffness,
                        visibilityThreshold = minimumChange,
                    ),
                ) { next, speed ->
                    position.floatValue = next
                    this@NexioSpring.velocity = speed
                    this@NexioSpring.onFrame?.invoke(next)
                    onUpdate()
                }
                this@NexioSpring.velocity = 0f
                this@NexioSpring.onEnd?.invoke()
            } catch (_: CancellationException) {
                // 重定向或主动取消：保留当前位置与速度，交给下一次 animateTo 接管。
            }
        }
    }

    /** 立即跳到 [next]，并清除速度与未执行的结束回调。 */
    fun snapTo(next: Float) {
        cancelComplete()
        position.floatValue = next
    }

    /** 取消动画但保留当前位置与速度。 */
    fun cancel() {
        onFrame = null
        onEnd = null
        job?.cancel()
        job = null
    }

    /** 取消动画并清除速度。 */
    fun cancelComplete() {
        cancel()
        velocity = 0f
    }
}
