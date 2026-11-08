package com.haooz.softglass

import com.haooz.softglass.glass.NoneFill
import com.haooz.softglass.glass.RawTone
import com.haooz.softglass.glass.SoftGlassStyle

// bigBlur 标 * 的档位沿用相邻档位的寄存器值，统一记为 500px。
private val TOKEN_LIGHT_DIRECTION = Triple(-0.4f, 0.6f, -0.8f)
private val TOKEN_LUMINANCE = listOf(0f, 2f, 0.5f, 0.8f)

/**
 * 系统玻璃材质档位。数值取自 `docs/hyperos-glass-token.md`，几何量与模糊半径单位为 px。
 * 未在表中逐档给出的字段继承当前默认样式。
 */
enum class GlassToken(
    val edgePx: Float,
    val thicknessPx: Float,
    val reflectOffsetPx: Float,
    val reflectionLighten: Float,
    val reflectionStrength: Float,
    val refractIOR: Float,
    val lightIntensity: Float,
    val lightOppositeIntensity: Float,
    val saturation: Float,
    val brightness: Float,
    val darker: Float,
    val innerColorWhite: Float,
    val innerColorMix: Float,
    val colorPow: Float,
    val bgColorSaturation: Float,
    val burn: Float,
    val smallBlurPx: Float,
    val bigBlurPx: Float,
) {
    BIONIC_MEDIUM_THIN(60f, 60f, 1000f, 1f, 0.8f, 3f, 1.2f, 0.8f, 1.4f, 0.08f, 0.20f, 0.15f, 0.40f, 1.2f, 2.4f, 0f, 40f, 500f),
    BIONIC_MEDIUM_NORMAL(60f, 80f, 1200f, 1f, 0.8f, 3f, 1.6f, 0.8f, 1.4f, -0.02f, 0.30f, 0.20f, 0.30f, 1.2f, 2.6f, 0f, 120f, 120f),
    BIONIC_MEDIUM_REGULAR(60f, 80f, 600f, 1f, 0.8f, 3f, 2.6f, 1.2f, 1.8f, -0.18f, 0.10f, 0.16f, 0.30f, 1.2f, 2f, 1f, 36f, 500f),
    BIONIC_SEMIBOLD_MOTION(60f, 80f, 1200f, 1f, 0.8f, 3f, 3f, 2f, 2f, -0.02f, 0.30f, 0.15f, 0.90f, 1.2f, 2.6f, 0f, 120f, 120f),
    COMMON_SMALL_REGULAR(18f, 100f, 110f, 1f, 0.8f, 3f, 1.1f, 0.5f, 1.4f, 0.14f, 0.20f, 0.30f, 0.50f, 1.2f, 1f, 0f, 8f, 500f),
    COMMON_SMALL_BOLD(60f, 80f, 1200f, 1f, 0.8f, 3f, 1.6f, 0.8f, 1.4f, 0.28f, 0.40f, 0.20f, 0.30f, 1.2f, 2.6f, 0f, 120f, 120f),
    COMMON_MEDIUM_THIN_LOW(60f, 60f, 600f, 1f, 0.8f, 3f, 1.2f, 0.8f, 1.5f, 0.20f, 0.10f, 0f, 0.30f, 1.2f, 1f, 0f, 36f, 500f),
    COMMON_MEDIUM_REGULAR_DARK(60f, 80f, 600f, 1f, 0.8f, 3f, 2.4f, 1.6f, 3f, 0.20f, 0f, 0f, 0.80f, 1.2f, 0f, 0f, 36f, 500f),
    COMMON_LARGE_REGULAR(50f, 80f, 600f, 1f, 0.8f, 3f, 1.6f, 0.8f, 2.2f, 0.08f, 0.20f, 0.15f, 0.40f, 1.2f, 0f, 0f, 80f, 500f),
    COMMON_LARGE_DEMIBOLD(50f, 80f, 600f, 1f, 0.8f, 3f, 2f, 1f, 2f, 0.18f, 0.26f, 0.60f, 0.90f, 1.2f, 0f, 0f, 50f, 500f),
    TINT_PURPLE(60f, 80f, 600f, 1f, 0.8f, 3f, 1.4f, 1f, 1f, 0.60f, 0.20f, 0.50f, 0.80f, 1.2f, 0f, 0f, 36f, 500f),
    TINT_BLACK_REGULAR(60f, 80f, 1200f, 1f, 0.8f, 3f, 3.4f, 2.6f, 1.4f, 0.10f, 0.30f, 0.20f, 0.30f, 1.2f, 4f, 0f, 120f, 120f),
    TINT_BLACK_HEAVY(72f, 80f, 1000f, 1f, 0.8f, 3f, 1.6f, 1f, 1.2f, 0f, 0.40f, 0.26f, 0.30f, 1.2f, 0f, 0f, 40f, 500f),
    ;

    val spec: GlassSpec = GlassSpec(
        style = SoftGlassStyle().copy(
            edgeWidth = edgePx,
            thickness = thicknessPx,
            reflectOffset = reflectOffsetPx,
            reflectionLighten = reflectionLighten,
            reflectionStrength = reflectionStrength,
            refractIOR = refractIOR,
            lightIntensity = lightIntensity,
            lightOppositeIntensity = lightOppositeIntensity,
            saturation = saturation,
            brightness = brightness,
            darker = darker,
            innerColorWhite = innerColorWhite,
            innerColorMix = innerColorMix,
            colorPow = colorPow,
            bgColorSaturation = bgColorSaturation,
            burn = burn,
            smallBlurRadius = smallBlurPx,
            bigBlurRadius = bigBlurPx,
            lightAngleRange = 1.4f,
            lightDirection = TOKEN_LIGHT_DIRECTION,
            luminanceValues = TOKEN_LUMINANCE,
            darkerRange = 0.6f to 1f,
        ),
        tone = RawTone,
        fill = NoneFill,
    )
}
