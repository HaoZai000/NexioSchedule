package com.haooz.softglass.shape

import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 全应用默认圆角：圆弧 + 两段三次贝塞尔，曲率 G2 连续。 */
@Immutable
data class G2Continuity(
    val profile: G2Profile = G2Profile.RoundedRectangle,
    val capsuleProfile: G2Profile = G2Profile.Capsule,
) : AdvancedContinuity() {

    override fun createStandardRoundedRectanglePathSegments(
        width: Double,
        height: Double,
        topLeft: Double,
        topRight: Double,
        bottomRight: Double,
        bottomLeft: Double,
    ): PathSegments {
        val centerX = width * 0.5
        val centerY = height * 0.5
        val ext = profile.extendedFraction
        val capExt = capsuleProfile.extendedFraction

        val ratioTLV = ((centerY / topLeft - 1.0) / ext).coerceIn(0.0, 1.0)
        val ratioTLH = ((centerX / topLeft - 1.0) / ext).coerceIn(0.0, 1.0)
        val ratioTRH = ((centerX / topRight - 1.0) / ext).coerceIn(0.0, 1.0)
        val ratioTRV = ((centerY / topRight - 1.0) / ext).coerceIn(0.0, 1.0)
        val ratioBRV = ((centerY / bottomRight - 1.0) / ext).coerceIn(0.0, 1.0)
        val ratioBRH = ((centerX / bottomRight - 1.0) / ext).coerceIn(0.0, 1.0)
        val ratioBLH = ((centerX / bottomLeft - 1.0) / ext).coerceIn(0.0, 1.0)
        val ratioBLV = ((centerY / bottomLeft - 1.0) / ext).coerceIn(0.0, 1.0)

        val ratioTL = min(ratioTLV, ratioTLH)
        val ratioTR = min(ratioTRH, ratioTRV)
        val ratioBR = min(ratioBRV, ratioBRH)
        val ratioBL = min(ratioBLH, ratioBLV)

        val extFracTL = lerp(capExt, ext, ratioTL)
        val extFracTR = lerp(capExt, ext, ratioTR)
        val extFracBR = lerp(capExt, ext, ratioBR)
        val extFracBL = lerp(capExt, ext, ratioBL)

        val extFracTLV = extFracTL * ratioTLV
        val extFracTLH = extFracTL * ratioTLH
        val extFracTRH = extFracTR * ratioTRH
        val extFracTRV = extFracTR * ratioTRV
        val extFracBRV = extFracBR * ratioBRV
        val extFracBRH = extFracBR * ratioBRH
        val extFracBLH = extFracBL * ratioBLH
        val extFracBLV = extFracBL * ratioBLV

        val offsetTLV = -topLeft * extFracTLV
        val offsetTLH = -topLeft * extFracTLH
        val offsetTRH = -topRight * extFracTRH
        val offsetTRV = -topRight * extFracTRV
        val offsetBRV = -bottomRight * extFracBRV
        val offsetBRH = -bottomRight * extFracBRH
        val offsetBLH = -bottomLeft * extFracBLH
        val offsetBLV = -bottomLeft * extFracBLV

        val bezKScaleTLV = lerp(capsuleProfile.bezierCurvatureScale, profile.bezierCurvatureScale, ratioTLV)
        val bezKScaleTLH = lerp(capsuleProfile.bezierCurvatureScale, profile.bezierCurvatureScale, ratioTLH)
        val bezKScaleTRH = lerp(capsuleProfile.bezierCurvatureScale, profile.bezierCurvatureScale, ratioTRH)
        val bezKScaleTRV = lerp(capsuleProfile.bezierCurvatureScale, profile.bezierCurvatureScale, ratioTRV)
        val bezKScaleBRV = lerp(capsuleProfile.bezierCurvatureScale, profile.bezierCurvatureScale, ratioBRV)
        val bezKScaleBRH = lerp(capsuleProfile.bezierCurvatureScale, profile.bezierCurvatureScale, ratioBRH)
        val bezKScaleBLH = lerp(capsuleProfile.bezierCurvatureScale, profile.bezierCurvatureScale, ratioBLH)
        val bezKScaleBLV = lerp(capsuleProfile.bezierCurvatureScale, profile.bezierCurvatureScale, ratioBLV)

        val arcFracTL = lerp(capsuleProfile.arcFraction, profile.arcFraction, ratioTL)
        val arcFracTR = lerp(capsuleProfile.arcFraction, profile.arcFraction, ratioTR)
        val arcFracBR = lerp(capsuleProfile.arcFraction, profile.arcFraction, ratioBR)
        val arcFracBL = lerp(capsuleProfile.arcFraction, profile.arcFraction, ratioBL)

        val arcKScaleTL = 1.0 + (profile.arcCurvatureScale - 1.0) * ratioTL
        val arcKScaleTR = 1.0 + (profile.arcCurvatureScale - 1.0) * ratioTR
        val arcKScaleBR = 1.0 + (profile.arcCurvatureScale - 1.0) * ratioBR
        val arcKScaleBL = 1.0 + (profile.arcCurvatureScale - 1.0) * ratioBL

        val bezierTLV = G2Profile(extFracTLV, arcFracTL, bezKScaleTLV, arcKScaleTL).baseBezier
        val bezierTLH = G2Profile(extFracTLH, arcFracTL, bezKScaleTLH, arcKScaleTL).baseBezier
        val bezierTRH = G2Profile(extFracTRH, arcFracTR, bezKScaleTRH, arcKScaleTR).baseBezier
        val bezierTRV = G2Profile(extFracTRV, arcFracTR, bezKScaleTRV, arcKScaleTR).baseBezier
        val bezierBRV = G2Profile(extFracBRV, arcFracBR, bezKScaleBRV, arcKScaleBR).baseBezier
        val bezierBRH = G2Profile(extFracBRH, arcFracBR, bezKScaleBRH, arcKScaleBR).baseBezier
        val bezierBLH = G2Profile(extFracBLH, arcFracBL, bezKScaleBLH, arcKScaleBL).baseBezier
        val bezierBLV = G2Profile(extFracBLV, arcFracBL, bezKScaleBLV, arcKScaleBL).baseBezier

        return buildPathSegments {
            var x = 0.0
            var y = topLeft
            moveTo(x, y - offsetTLV)

            if (topLeft > 0.0) {
                with(bezierTLV) {
                    cubicTo(
                        x + p1.y * topLeft, y - p1.x * topLeft,
                        x + p2.y * topLeft, y - p2.x * topLeft,
                        x + p3.y * topLeft, y - p3.x * topLeft,
                    )
                }
                arcToWithScaledRadius(
                    center = Point(topLeft, topLeft),
                    radius = topLeft,
                    radiusScale = 1.0 / arcKScaleTL,
                    startAngle = PI + PI * 0.5 * (1.0 - arcFracTL) * 0.5,
                    sweepAngle = PI * 0.5 * arcFracTL,
                )
                x = topLeft
                y = 0.0
                with(bezierTLH) {
                    cubicTo(
                        x - p2.x * topLeft, y + p2.y * topLeft,
                        x - p1.x * topLeft, y + p1.y * topLeft,
                        x - (p0.x * topLeft).coerceAtLeast(offsetTLH), y + p0.y * topLeft,
                    )
                }
            }

            x = width - topRight
            y = 0.0
            lineTo(x + offsetTRH, y)

            if (topRight > 0.0) {
                with(bezierTRH) {
                    cubicTo(
                        x + p1.x * topRight, y + p1.y * topRight,
                        x + p2.x * topRight, y + p2.y * topRight,
                        x + p3.x * topRight, y + p3.y * topRight,
                    )
                }
                arcToWithScaledRadius(
                    center = Point(width - topRight, topRight),
                    radius = topRight,
                    radiusScale = 1.0 / arcKScaleTR,
                    startAngle = -PI * 0.5 + PI * 0.5 * (1.0 - arcFracTR) * 0.5,
                    sweepAngle = PI * 0.5 * arcFracTR,
                )
                x = width
                y = topRight
                with(bezierTRV) {
                    cubicTo(
                        x - p2.y * topRight, y - p2.x * topRight,
                        x - p1.y * topRight, y - p1.x * topRight,
                        x - p0.y * topRight, y - (p0.x * topRight).coerceAtLeast(offsetTRV),
                    )
                }
            }

            x = width
            y = height - bottomRight
            lineTo(x, y + offsetBRV)

            if (bottomRight > 0.0) {
                with(bezierBRV) {
                    cubicTo(
                        x - p1.y * bottomRight, y + p1.x * bottomRight,
                        x - p2.y * bottomRight, y + p2.x * bottomRight,
                        x - p3.y * bottomRight, y + p3.x * bottomRight,
                    )
                }
                arcToWithScaledRadius(
                    center = Point(width - bottomRight, height - bottomRight),
                    radius = bottomRight,
                    radiusScale = 1.0 / arcKScaleBR,
                    startAngle = PI * 0.5 * (1.0 - arcFracBR) * 0.5,
                    sweepAngle = PI * 0.5 * arcFracBR,
                )
                x = width - bottomRight
                y = height
                with(bezierBRH) {
                    cubicTo(
                        x + p2.x * bottomRight, y - p2.y * bottomRight,
                        x + p1.x * bottomRight, y - p1.y * bottomRight,
                        x + (p0.x * bottomRight).coerceAtLeast(offsetBRH), y - p0.y * bottomRight,
                    )
                }
            }

            x = bottomLeft
            y = height
            lineTo(x - offsetBLH, y)

            if (bottomLeft > 0.0) {
                with(bezierBLH) {
                    cubicTo(
                        x - p1.x * bottomLeft, y - p1.y * bottomLeft,
                        x - p2.x * bottomLeft, y - p2.y * bottomLeft,
                        x - p3.x * bottomLeft, y - p3.y * bottomLeft,
                    )
                }
                arcToWithScaledRadius(
                    center = Point(bottomLeft, height - bottomLeft),
                    radius = bottomLeft,
                    radiusScale = 1.0 / arcKScaleBL,
                    startAngle = PI * 0.5 + PI * 0.5 * (1.0 - arcFracBL) * 0.5,
                    sweepAngle = PI * 0.5 * arcFracBL,
                )
                x = 0.0
                y = height - bottomLeft
                with(bezierBLV) {
                    cubicTo(
                        x + p2.y * bottomLeft, y + p2.x * bottomLeft,
                        x + p1.y * bottomLeft, y + p1.x * bottomLeft,
                        x + p0.y * bottomLeft, y + (p0.x * bottomLeft).coerceAtLeast(offsetBLV),
                    )
                }
            }
            close()
        }
    }

    override fun createHorizontalCapsulePathSegments(width: Double, height: Double): PathSegments {
        val radius = height * 0.5
        val ratioH = ((width * 0.5 / radius - 1.0) / capsuleProfile.extendedFraction).coerceIn(0.0, 1.0)
        val extFracH = capsuleProfile.extendedFraction * ratioH
        val offsetH = -radius * extFracH
        val bezKScaleH = lerp(capsuleProfile.bezierCurvatureScale, profile.bezierCurvatureScale, ratioH)
        val arcFrac = capsuleProfile.arcFraction
        val bezierH = G2Profile(extFracH, arcFrac, bezKScaleH, 1.0).baseBezier * radius

        val arcRad = PI * 0.5 * arcFrac
        val bezRad = (PI * 0.5 - arcRad) * 0.5
        val sweepRad = (bezRad + arcRad) * 2.0

        return buildPathSegments {
            var x = 0.0
            var y = radius
            moveTo(x, y)
            arcTo(
                center = Point(radius, radius),
                radius = radius,
                startAngle = PI * 0.5 + bezRad,
                sweepAngle = sweepRad,
            )
            x = radius
            y = 0.0
            with(bezierH) {
                cubicTo(
                    x - p2.x, y + p2.y,
                    x - p1.x, y + p1.y,
                    x - p0.x.coerceAtLeast(offsetH), y + p0.y,
                )
            }
            x = width - radius
            y = 0.0
            lineTo(x + offsetH, y)
            with(bezierH) {
                cubicTo(x + p1.x, y + p1.y, x + p2.x, y + p2.y, x + p3.x, y + p3.y)
            }
            arcTo(
                center = Point(width - radius, radius),
                radius = radius,
                startAngle = -(PI * 0.5 - bezRad),
                sweepAngle = sweepRad,
            )
            x = width - radius
            y = height
            with(bezierH) {
                cubicTo(
                    x + p2.x, y - p2.y,
                    x + p1.x, y - p1.y,
                    x + p0.x.coerceAtLeast(offsetH), y - p0.y,
                )
            }
            x = radius
            y = height
            lineTo(x - offsetH, y)
            with(bezierH) {
                cubicTo(x - p1.x, y - p1.y, x - p2.x, y - p2.y, x - p3.x, y - p3.y)
            }
        }
    }

    override fun createVerticalCapsulePathSegments(width: Double, height: Double): PathSegments {
        val radius = width * 0.5
        val ratioV = ((height * 0.5 / radius - 1.0) / capsuleProfile.extendedFraction).coerceIn(0.0, 1.0)
        val extFracV = capsuleProfile.extendedFraction * ratioV
        val offsetV = -radius * extFracV
        val bezKScaleV = lerp(capsuleProfile.bezierCurvatureScale, profile.bezierCurvatureScale, ratioV)
        val arcFrac = capsuleProfile.arcFraction
        val bezierV = G2Profile(extFracV, arcFrac, bezKScaleV, 1.0).baseBezier * radius

        val arcRad = PI * 0.5 * arcFrac
        val bezRad = (PI * 0.5 - arcRad) * 0.5
        val sweepRad = (bezRad + arcRad) * 2.0

        return buildPathSegments {
            var x = 0.0
            var y = radius
            moveTo(x, y - offsetV)
            with(bezierV) {
                cubicTo(x + p1.y, y - p1.x, x + p2.y, y - p2.x, x + p3.y, y - p3.x)
            }
            arcTo(
                center = Point(radius, radius),
                radius = radius,
                startAngle = -(PI - bezRad),
                sweepAngle = sweepRad,
            )
            x = width
            y = radius
            with(bezierV) {
                cubicTo(
                    x - p2.y, y - p2.x,
                    x - p1.y, y - p1.x,
                    x - p0.y, y - p0.x.coerceAtLeast(offsetV),
                )
            }
            x = width
            y = height - radius
            lineTo(x, y + offsetV)
            with(bezierV) {
                cubicTo(x - p1.y, y + p1.x, x - p2.y, y + p2.x, x - p3.y, y + p3.x)
            }
            arcTo(
                center = Point(width - radius, height - radius),
                radius = radius,
                startAngle = bezRad,
                sweepAngle = sweepRad,
            )
            x = 0.0
            y = height - radius
            with(bezierV) {
                cubicTo(
                    x + p2.y, y + p2.x,
                    x + p1.y, y + p1.x,
                    x + p0.y, y + p0.x.coerceAtLeast(offsetV),
                )
            }
            close()
        }
    }

    override fun lerp(stop: Continuity, fraction: Double): Continuity = when (stop) {
        is G2Continuity -> G2Continuity(
            profile = lerp(profile, stop.profile, fraction),
            capsuleProfile = lerp(capsuleProfile, stop.capsuleProfile, fraction),
        )
        else -> stop.lerp(this, 1.0 - fraction)
    }
}

private fun PathSegmentsBuilder.arcToWithScaledRadius(
    center: Point,
    radius: Double,
    radiusScale: Double,
    startAngle: Double,
    sweepAngle: Double,
) {
    val centerAngle = startAngle + sweepAngle * 0.5
    arcTo(
        center = center + Point(cos(centerAngle), sin(centerAngle)) * radius * (1.0 - radiusScale),
        radius = radius * radiusScale,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
    )
}
