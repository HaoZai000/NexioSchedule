package com.haooz.softglass.backdrop.effects

import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.internal.colorControlsColorMatrix
import com.haooz.softglass.internal.glassColorFilterEffect
import com.haooz.softglass.platform.isRenderEffectSupported

/** 亮度 / 对比度 / 饱和度，挂成一段颜色滤镜。 */
fun GlassEffectScope.colorControls(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f,
) {
    if (brightness == 0f && contrast == 1f && saturation == 1f) return
    if (!isRenderEffectSupported()) return

    val matrix = colorControlsColorMatrix(brightness, contrast, saturation)
    renderEffect = glassColorFilterEffect(renderEffect, ColorMatrixColorFilter(ColorMatrix(matrix)))
}
