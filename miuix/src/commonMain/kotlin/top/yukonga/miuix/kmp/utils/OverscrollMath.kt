package top.yukonga.miuix.kmp.utils

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/** 越界阻尼与回弹的公共公式：`overScroll*` 节点与自绘位移共用。 */
object OverscrollMath {
    /** fling 速度 → 弹簧初速的除子。 */
    internal const val FLING_VELOCITY_DIVISOR = 1.53333f

    /** 手指位移 → 屏上位移；[range] 为该轴容器尺寸。 */
    fun dampedDistance(rawDistance: Float, range: Float): Float {
        if (range <= 0f || rawDistance == 0f) return 0f
        val normalized = min(abs(rawDistance) / range, 1f)
        return sign(rawDistance) * SpringMath.obtainDampingDistance(normalized, range)
    }

    /** 屏上位移 → 等效手指位移。 */
    fun rawDistance(offset: Float, range: Float): Float {
        if (range <= 0f || offset == 0f) return 0f
        return sign(offset) * SpringMath.obtainTouchDistance(offset, range)
    }

    /** 松手速度 → 弹簧初速。 */
    fun settleVelocity(flingVelocity: Float): Float = flingVelocity / FLING_VELOCITY_DIVISOR

    /** 越界回弹；[from] 与 [initialVelocity] 单位为像素，[onFrame] 返回相对边界的位移。 */
    suspend fun settle(
        from: Float,
        initialVelocity: Float,
        onFrame: (Float) -> Unit,
    ) {
        SpringEngine().runSettleAnimation(
            startValue = from,
            initialVelocity = initialVelocity,
            onFrame = onFrame,
            onSettle = {},
        )
    }
}
