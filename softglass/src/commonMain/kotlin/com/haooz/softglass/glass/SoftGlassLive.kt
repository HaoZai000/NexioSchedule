package com.haooz.softglass.glass

import com.haooz.softglass.platform.GlassRuntimeShader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** σ（屏幕 px）→ Compose BlurEffect 半径；两条采样路的模糊量单位不同。 */
fun glassBlurRadiusForSigma(sigmaPx: Float): Float =
    if (sigmaPx <= 0f) 0f else (sigmaPx - 0.5f) / 0.57735f

internal fun smooth5Vertical(x: Float): Float {
    var t = 0.5f + 0.5f * sqrt(x.coerceIn(0f, 1f))
    t = t.coerceIn(0f, 1f)
    t = t * t * t * (t * (t * 6f - 15f) + 10f)
    return (t - 0.5f) * 2f
}

/** 把样式灌入实时录制版 shader；尺寸与圆角由调用方按当前布局传入。 */
fun GlassRuntimeShader.applySoftGlass(
    style: SoftGlassStyle,
    width: Float,
    height: Float,
    radiusPx: Float? = null,
    shapeRectPx: FloatArray? = null,
) {
    if (width <= 0f || height <= 0f) return
    val rect = shapeRectPx?.takeIf { it.size >= 4 }
    val shapeLeft = rect?.get(0) ?: 0f
    val shapeTop = rect?.get(1) ?: 0f
    val shapeRight = rect?.get(2) ?: width
    val shapeBottom = rect?.get(3) ?: height
    val half = minOf(shapeRight - shapeLeft, shapeBottom - shapeTop).coerceAtLeast(1f) * 0.5f
    val edgePx = style.edgeWidth
    val refractionOn = style.refractionScale > 0f

    setFloatUniform("uSize", width, height)
    setFloatUniform("uShapeRect", shapeLeft, shapeTop, shapeRight, shapeBottom)
    val r = (radiusPx ?: half).coerceIn(0f, half)
    setFloatUniform("uCornerRadii", r, r, r, r)

    setFloatUniform("uEdgePx", edgePx)
    setFloatUniform("uEdgePow", style.edgePow)
    setFloatUniform("uInvEdgePow", 1f / style.edgePow.coerceAtLeast(0.05f))
    setFloatUniform("uThicknessPx", style.thickness * style.refractionScale)
    setFloatUniform("uReflectOffsetPx", if (refractionOn) style.reflectOffset else 0f)
    setFloatUniform("uIOR", style.refractIOR)
    setFloatUniform("uReflStrength", style.reflectionStrength)
    setFloatUniform("uReflLighten", style.reflectionLighten)
    setFloatUniform("uColorPow", style.colorPow)
    setFloatUniform("uAlpha", style.alpha)
    setFloatUniform("uInnerBottom", style.innerBottom)
    setFloatUniform("uEdgeHighlight", if (style.edgeHighlight) 1f else 0f)
    setFloatUniform("uLightFalloff", style.edgeLightFalloff.coerceIn(0f, 1f))
    setFloatUniform("uUnshade", style.unshade)
    setFloatUniform("uUsePowGate", if (style.usePowGate) 1f else 0f)

    val ld = style.lightDirection
    setFloatUniform("uLightDir", ld.first, ld.second, ld.third)
    val cosA = cos(style.lightAngleRange * PI.toFloat() * 0.5f)
    setFloatUniform("uAngleK", 0.5f / sqrt((1f - cosA).coerceAtLeast(1e-6f)))
    setFloatUniform("uLightIntensity", style.lightIntensity)
    setFloatUniform("uLightOpposite", style.lightOppositeIntensity)
    setFloatUniform("uNmlZAAThreshold", smooth5Vertical(3f / edgePx.coerceAtLeast(1f)))

    setFloatUniform("uDarker", style.darker)
    val dc = style.darkerColor
    setFloatUniform("uDarkerColor", dc.red, dc.green, dc.blue)
    setFloatUniform("uDarkerRange", style.darkerRange.first, style.darkerRange.second)
    val lc = style.luminanceValues
    setFloatUniform(
        "uLuminance",
        lc.getOrElse(0) { 0f },
        lc.getOrElse(1) { 0f },
        lc.getOrElse(2) { 0f },
        lc.getOrElse(3) { 1f },
    )
    setFloatUniform("uLuminanceAmount", style.luminanceAmount)
    setFloatUniform("uSaturation", style.saturation)
    setFloatUniform("uBrightness", style.brightness)
    setFloatUniform("uBgSat", style.bgColorSaturation)
    setFloatUniform("uBgBri", style.bgColorBrightness)
    setFloatUniform("uInnerWhite", style.innerColorWhite)
    setFloatUniform("uInnerMix", style.innerColorMix)
    setFloatUniform("uBurn", style.burn)
    val gc = style.glassColor
    setFloatUniform("uGlassColor", gc.red, gc.green, gc.blue, style.glassColorMix)
}
