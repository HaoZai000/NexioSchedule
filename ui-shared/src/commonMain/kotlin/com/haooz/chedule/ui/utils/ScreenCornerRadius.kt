package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable

/** Physical screen corners in pixels; independent of the interface's layout direction. */
data class ScreenCornerRadiiPx(
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomRight: Float = 0f,
    val bottomLeft: Float = 0f,
) {
    val maxRadius: Float get() = maxOf(topLeft, topRight, bottomRight, bottomLeft)

    fun scaled(scale: Float): ScreenCornerRadiiPx = ScreenCornerRadiiPx(
        topLeft * scale, topRight * scale, bottomRight * scale, bottomLeft * scale,
    )

    /**
     * 给每个角设下限（px）。屏幕圆角读不到（直角屏 / 模拟器返回 0）或缩放后过小时，
     * 用它兜底出一个最小圆角，避免裁切结果变成硬直角。
     */
    fun coerceAtLeast(minRadius: Float): ScreenCornerRadiiPx = ScreenCornerRadiiPx(
        maxOf(topLeft, minRadius),
        maxOf(topRight, minRadius),
        maxOf(bottomRight, minRadius),
        maxOf(bottomLeft, minRadius),
    )

    /** Interpolate each physical corner from the same starting card radius. */
    fun interpolateFrom(startRadius: Float, fraction: Float): ScreenCornerRadiiPx = ScreenCornerRadiiPx(
        startRadius + (topLeft - startRadius) * fraction,
        startRadius + (topRight - startRadius) * fraction,
        startRadius + (bottomRight - startRadius) * fraction,
        startRadius + (bottomLeft - startRadius) * fraction,
    )

    companion object {
        val Zero = ScreenCornerRadiiPx()

        fun uniform(radius: Float): ScreenCornerRadiiPx = ScreenCornerRadiiPx(
            radius, radius, radius, radius,
        )
    }
}

/** Screen corners for page clipping. Unavailable corners are zero (no clipping). */
@Composable
expect fun rememberScreenCornerRadiiPx(): ScreenCornerRadiiPx

/** Uniform-radius consumers such as dialogs use the largest physical corner. */
@Composable
fun rememberScreenCornerRadiusPx(): Float = rememberScreenCornerRadiiPx().maxRadius
