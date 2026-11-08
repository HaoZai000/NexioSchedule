package com.haooz.softglass.shape

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.LayoutDirection.Ltr
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** G2 连续圆角矩形，四角半径可独立设置。 */
@Immutable
open class ContinuousRoundedRectangle(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    open val continuity: Continuity = Continuity.Default,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) {
            return Outline.Rectangle(size.toRect())
        }
        val maxRadius = min(size.width, size.height) * 0.5f
        val topLeft = (if (layoutDirection == Ltr) topStart else topEnd).coerceIn(0f, maxRadius)
        val topRight = (if (layoutDirection == Ltr) topEnd else topStart).coerceIn(0f, maxRadius)
        val bottomRight = (if (layoutDirection == Ltr) bottomEnd else bottomStart).coerceIn(0f, maxRadius)
        val bottomLeft = (if (layoutDirection == Ltr) bottomStart else bottomEnd).coerceIn(0f, maxRadius)
        return continuity.createRoundedRectangleOutline(size, topLeft, topRight, bottomRight, bottomLeft)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): ContinuousRoundedRectangle = ContinuousRoundedRectangle(
        topStart, topEnd, bottomEnd, bottomStart, continuity,
    )

    fun copy(
        topStart: CornerSize = this.topStart,
        topEnd: CornerSize = this.topEnd,
        bottomEnd: CornerSize = this.bottomEnd,
        bottomStart: CornerSize = this.bottomStart,
        continuity: Continuity = this.continuity,
    ): ContinuousRoundedRectangle = ContinuousRoundedRectangle(
        topStart, topEnd, bottomEnd, bottomStart, continuity,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContinuousRoundedRectangle) return false
        return topStart == other.topStart && topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd && bottomStart == other.bottomStart &&
            continuity == other.continuity
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + continuity.hashCode()
        return result
    }

    override fun toString(): String =
        "ContinuousRoundedRectangle(topStart=$topStart, topEnd=$topEnd, " +
            "bottomEnd=$bottomEnd, bottomStart=$bottomStart, continuity=$continuity)"
}

@Stable
val ContinuousRectangle: ContinuousRoundedRectangle = ContinuousRectangleShape()

@Suppress("FunctionName")
@Stable
fun ContinuousRectangle(continuity: Continuity = Continuity.Default): ContinuousRoundedRectangle =
    ContinuousRectangleShape(continuity)

@Immutable
private data class ContinuousRectangleShape(
    override val continuity: Continuity = Continuity.Default,
) : ContinuousRoundedRectangle(
    ZeroCornerSize, ZeroCornerSize, ZeroCornerSize, ZeroCornerSize, continuity,
) {
    override fun toString(): String = "ContinuousRectangle(continuity=$continuity)"
}

private val FullCornerSize = CornerSize(50)

@Stable
val ContinuousCapsule: ContinuousRoundedRectangle = ContinuousCapsule()

@Suppress("FunctionName")
@Stable
fun ContinuousCapsule(continuity: Continuity = Continuity.Default): ContinuousRoundedRectangle =
    ContinuousCapsuleShape(continuity)

@Immutable
private data class ContinuousCapsuleShape(
    override val continuity: Continuity = Continuity.Default,
) : ContinuousRoundedRectangle(
    FullCornerSize, FullCornerSize, FullCornerSize, FullCornerSize, continuity,
) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline = continuity.createCapsuleOutline(size)

    override fun toString(): String = "ContinuousCapsule(continuity=$continuity)"
}

@Stable
fun ContinuousRoundedRectangle(
    corner: CornerSize,
    continuity: Continuity = Continuity.Default,
): ContinuousRoundedRectangle = ContinuousRoundedRectangle(corner, corner, corner, corner, continuity)

@Stable
fun ContinuousRoundedRectangle(
    size: Dp,
    continuity: Continuity = Continuity.Default,
): ContinuousRoundedRectangle = ContinuousRoundedRectangle(CornerSize(size), continuity = continuity)

@Stable
fun ContinuousRoundedRectangle(
    size: Float,
    continuity: Continuity = Continuity.Default,
): ContinuousRoundedRectangle = ContinuousRoundedRectangle(CornerSize(size), continuity = continuity)

@Stable
fun ContinuousRoundedRectangle(
    percent: Int,
    continuity: Continuity = Continuity.Default,
): ContinuousRoundedRectangle = ContinuousRoundedRectangle(CornerSize(percent), continuity = continuity)

@Stable
fun ContinuousRoundedRectangle(
    topStart: Dp = 0f.dp,
    topEnd: Dp = 0f.dp,
    bottomEnd: Dp = 0f.dp,
    bottomStart: Dp = 0f.dp,
    continuity: Continuity = Continuity.Default,
): ContinuousRoundedRectangle = ContinuousRoundedRectangle(
    CornerSize(topStart), CornerSize(topEnd), CornerSize(bottomEnd), CornerSize(bottomStart), continuity,
)

@Stable
fun ContinuousRoundedRectangle(
    topStart: Float = 0f,
    topEnd: Float = 0f,
    bottomEnd: Float = 0f,
    bottomStart: Float = 0f,
    continuity: Continuity = Continuity.Default,
): ContinuousRoundedRectangle = ContinuousRoundedRectangle(
    CornerSize(topStart), CornerSize(topEnd), CornerSize(bottomEnd), CornerSize(bottomStart), continuity,
)

@Stable
fun ContinuousRoundedRectangle(
    topStartPercent: Int = 0,
    topEndPercent: Int = 0,
    bottomEndPercent: Int = 0,
    bottomStartPercent: Int = 0,
    continuity: Continuity = Continuity.Default,
): ContinuousRoundedRectangle = ContinuousRoundedRectangle(
    CornerSize(topStartPercent), CornerSize(topEndPercent),
    CornerSize(bottomEndPercent), CornerSize(bottomStartPercent), continuity,
)
