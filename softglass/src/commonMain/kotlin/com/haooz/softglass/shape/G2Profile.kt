package com.haooz.softglass.shape

import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
data class Point(val x: Double, val y: Double) {
    operator fun plus(other: Point): Point = Point(x + other.x, y + other.y)
    operator fun minus(other: Point): Point = Point(x - other.x, y - other.y)
    operator fun times(scale: Double): Point = Point(x * scale, y * scale)

    companion object {
        val Zero = Point(0.0, 0.0)
    }
}

@Immutable
data class CubicBezier(val p0: Point, val p1: Point, val p2: Point, val p3: Point) {
    operator fun times(scale: Double): CubicBezier = CubicBezier(
        p0 * scale, p1 * scale, p2 * scale, p3 * scale,
    )
}

fun lerp(start: Double, stop: Double, fraction: Double): Double =
    start + (stop - start) * fraction

/** G2 连续圆角的剖面参数；[baseBezier] 是角部从直边过渡到圆弧的那一段三次贝塞尔。 */
@Immutable
data class G2Profile(
    val extendedFraction: Double,
    val arcFraction: Double,
    val bezierCurvatureScale: Double,
    val arcCurvatureScale: Double,
) {
    val baseBezier: CubicBezier by lazy(LazyThreadSafetyMode.PUBLICATION) { createBaseBezier() }

    private fun createBaseBezier(): CubicBezier {
        val arcRadians = PI * 0.5 * arcFraction
        val bezierRadians = (PI * 0.5 - arcRadians) * 0.5
        val s = sin(bezierRadians)
        val c = cos(bezierRadians)

        if (bezierCurvatureScale == 1.0 && arcCurvatureScale == 1.0) {
            val halfTan = s / (1.0 + c)
            return CubicBezier(
                Point(-extendedFraction, 0.0),
                Point((1.0 - 1.5 / (1.0 + c)) * halfTan, 0.0),
                Point(halfTan, 0.0),
                Point(s, 1.0 - c),
            )
        }

        val radiusScale = 1.0 / arcCurvatureScale
        val arcCenter = Point(0.0, 1.0) +
            Point(1.0 / sqrt(2.0), -1.0 / sqrt(2.0)) * (1.0 - radiusScale)
        val arcStart = arcCenter + Point(s, -c) * radiusScale
        return zeroStartCurvatureBezier(
            start = Point(-extendedFraction, 0.0),
            end = arcStart,
            startTangent = Point(1.0, 0.0),
            endTangent = Point(c, s),
            endCurvature = bezierCurvatureScale,
        )
    }

    private fun zeroStartCurvatureBezier(
        start: Point,
        end: Point,
        startTangent: Point,
        endTangent: Point,
        endCurvature: Double,
    ): CubicBezier {
        val a2 = 1.5 * endCurvature
        val b = startTangent.x * endTangent.y - startTangent.y * endTangent.x
        val dx = end.x - start.x
        val dy = end.y - start.y
        val c1 = -dy * startTangent.x + dx * startTangent.y
        val c2 = dy * endTangent.x - dx * endTangent.y
        val lambda0 = -c2 / b - a2 * c1 * c1 / (b * b * b)
        val lambda3 = -c1 / b
        return CubicBezier(
            p0 = start,
            p1 = start + Point(
                (lambda0 * startTangent.x).coerceAtLeast(0.0),
                (lambda0 * startTangent.y).coerceAtLeast(0.0),
            ),
            p2 = end - Point(
                (lambda3 * endTangent.x).coerceAtLeast(0.0),
                (lambda3 * endTangent.y).coerceAtLeast(0.0),
            ),
            p3 = end,
        )
    }

    companion object {
        val RoundedRectangle = G2Profile(
            extendedFraction = 0.5286651,
            arcFraction = 5.0 / 9.0,
            bezierCurvatureScale = 1.0732051,
            arcCurvatureScale = 1.0732051,
        )
        val Capsule = G2Profile(
            extendedFraction = 0.5286651 * 0.75,
            arcFraction = 0.0,
            bezierCurvatureScale = 1.0,
            arcCurvatureScale = 1.0,
        )
        val Circular = G2Profile(
            extendedFraction = 0.0,
            arcFraction = 1.0,
            bezierCurvatureScale = 1.0,
            arcCurvatureScale = 1.0,
        )
    }
}

fun lerp(start: G2Profile, stop: G2Profile, fraction: Double): G2Profile = G2Profile(
    extendedFraction = lerp(start.extendedFraction, stop.extendedFraction, fraction),
    arcFraction = lerp(start.arcFraction, stop.arcFraction, fraction),
    bezierCurvatureScale = lerp(start.bezierCurvatureScale, stop.bezierCurvatureScale, fraction),
    arcCurvatureScale = lerp(start.arcCurvatureScale, stop.arcCurvatureScale, fraction),
)
