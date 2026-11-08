package com.haooz.softglass.shape

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline

/** 圆角连续性策略，负责把四角半径变成可裁剪的轮廓。 */
@Immutable
interface Continuity {
    fun createRoundedRectanglePathSegments(
        width: Double,
        height: Double,
        topLeft: Double,
        topRight: Double,
        bottomRight: Double,
        bottomLeft: Double,
    ): PathSegments

    fun createRoundedRectangleOutline(
        size: Size,
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float,
    ): Outline

    fun createCapsuleOutline(size: Size): Outline

    fun lerp(stop: Continuity, fraction: Double): Continuity

    companion object {
        @Stable
        val Default: Continuity = G2Continuity()
    }
}

@Immutable
abstract class AdvancedContinuity : Continuity {

    protected abstract fun createStandardRoundedRectanglePathSegments(
        width: Double,
        height: Double,
        topLeft: Double,
        topRight: Double,
        bottomRight: Double,
        bottomLeft: Double,
    ): PathSegments

    protected open fun createStandardRoundedRectangleOutline(
        size: Size,
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float,
    ): Outline = Outline.Generic(
        createStandardRoundedRectanglePathSegments(
            width = size.width.toDouble(),
            height = size.height.toDouble(),
            topLeft = topLeft.toDouble(),
            topRight = topRight.toDouble(),
            bottomRight = bottomRight.toDouble(),
            bottomLeft = bottomLeft.toDouble(),
        ).toPath()
    )

    protected open fun createHorizontalCapsulePathSegments(width: Double, height: Double): PathSegments =
        createStandardRoundedRectanglePathSegments(
            width = width,
            height = height,
            topLeft = width * 0.5,
            topRight = width * 0.5,
            bottomRight = width * 0.5,
            bottomLeft = width * 0.5,
        )

    protected open fun createHorizontalCapsuleOutline(size: Size): Outline = Outline.Generic(
        createHorizontalCapsulePathSegments(size.width.toDouble(), size.height.toDouble()).toPath()
    )

    protected open fun createVerticalCapsulePathSegments(width: Double, height: Double): PathSegments =
        createStandardRoundedRectanglePathSegments(
            width = width,
            height = height,
            topLeft = height * 0.5,
            topRight = height * 0.5,
            bottomRight = height * 0.5,
            bottomLeft = height * 0.5,
        )

    protected open fun createVerticalCapsuleOutline(size: Size): Outline = Outline.Generic(
        createVerticalCapsulePathSegments(size.width.toDouble(), size.height.toDouble()).toPath()
    )

    protected open fun createCircleOutline(size: Float): Outline {
        val radius = size * 0.5f
        return Outline.Rounded(
            RoundRect(Rect(0f, 0f, size, size), radiusX = radius, radiusY = radius)
        )
    }

    final override fun createRoundedRectanglePathSegments(
        width: Double,
        height: Double,
        topLeft: Double,
        topRight: Double,
        bottomRight: Double,
        bottomLeft: Double,
    ): PathSegments {
        if ((topLeft + topRight == width || topLeft + bottomLeft == height) &&
            topLeft == topRight && bottomRight == bottomLeft && topLeft == bottomRight
        ) {
            return createCapsulePathSegments(width, height)
        }
        return createStandardRoundedRectanglePathSegments(
            width, height, topLeft, topRight, bottomRight, bottomLeft,
        )
    }

    final override fun createRoundedRectangleOutline(
        size: Size,
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float,
    ): Outline {
        if ((topLeft + topRight == size.width || topLeft + bottomLeft == size.height) &&
            topLeft == topRight && bottomRight == bottomLeft && topLeft == bottomRight
        ) {
            return createCapsuleOutline(size)
        }
        return createStandardRoundedRectangleOutline(
            size, topLeft, topRight, bottomRight, bottomLeft,
        )
    }

    fun createCapsulePathSegments(width: Double, height: Double): PathSegments = when {
        width > height -> createHorizontalCapsulePathSegments(width, height)
        width < height -> createVerticalCapsulePathSegments(width, height)
        else -> listOf(PathSegment.Circle(Point(width * 0.5, height * 0.5), width * 0.5))
    }

    final override fun createCapsuleOutline(size: Size): Outline = when {
        size.width > size.height -> createHorizontalCapsuleOutline(size)
        size.width < size.height -> createVerticalCapsuleOutline(size)
        else -> createCircleOutline(size.width)
    }
}
