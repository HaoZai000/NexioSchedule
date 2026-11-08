package com.haooz.softglass.glass

import androidx.compose.ui.graphics.Color

/** 材质基调，全量覆盖所负责的渲染字段。 */
sealed interface GlassTone {
    fun apply(style: SoftGlassStyle): SoftGlassStyle
}

private fun SoftGlassStyle.withTone(
    edgeWidth: Float,
    thickness: Float,
    darkerColor: Color,
): SoftGlassStyle = copy(
    edgeWidth = edgeWidth,
    edgePow = 4f,
    thickness = thickness,
    reflectOffset = 600f,
    refractionScale = 1f,
    refractIOR = 3f,
    reflectionStrength = 0.8f,
    reflectionLighten = 1f,
    darker = 0.08f,
    darkerColor = darkerColor,
    darkerRange = 0.6f to 1f,
    luminanceValues = listOf(0f, 2f, 0.5f, 0.8f),
    luminanceAmount = 0.15f,
    saturation = 2f,
    brightness = 0.08f,
    bgColorSaturation = 0f,
    bgColorBrightness = 0f,
    innerColorWhite = 0.15f,
    innerColorMix = 0.4f,
    burn = 0f,
    glassColor = Color.White,
    glassColorMix = 0.1f,
    colorPow = 1.2f,
    alpha = 1f,
    unshade = 0f,
    usePowGate = true,
    lightDirection = GlassLightDirection,
    lightAngleRange = 1f,
    lightIntensity = 1.6f,
    lightOppositeIntensity = 0.8f,
    edgeHighlight = true,
    innerBottom = 0.05f,
)

/** 不改写样式，供档位预设原样保留自己的参数。 */
object RawTone : GlassTone {
    override fun apply(style: SoftGlassStyle): SoftGlassStyle = style
}

/** 大面板基调：亮部压暗偏深紫黑。 */
object TokenTone : GlassTone {
    override fun apply(style: SoftGlassStyle): SoftGlassStyle = style.withTone(
        edgeWidth = 60f,
        thickness = 96f,
        darkerColor = Color(0.07874f, 0.02848f, 0.09278f),
    )
}

/** 悬浮小控件基调：中性压暗。 */
object BrightTone : GlassTone {
    override fun apply(style: SoftGlassStyle): SoftGlassStyle = style.withTone(
        edgeWidth = 60f,
        thickness = 96f,
        darkerColor = Color.Black,
    )
}

/** 悬浮大控件基调：倒角带更宽、折射更厚。 */
object BrightLargeTone : GlassTone {
    override fun apply(style: SoftGlassStyle): SoftGlassStyle = style.withTone(
        edgeWidth = 75f,
        thickness = 120f,
        darkerColor = Color.Black,
    )
}
