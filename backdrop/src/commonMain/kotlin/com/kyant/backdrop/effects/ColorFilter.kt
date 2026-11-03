package com.kyant.backdrop.effects

import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.BackdropEffectScopeImpl
import com.kyant.backdrop.internal.ColorFilterEffect
import com.kyant.backdrop.internal.colorControlsColorMatrix
import com.kyant.backdrop.internal.opacityColorMatrix
import com.kyant.backdrop.internal.saturationColorMatrix
import com.kyant.backdrop.isRenderEffectSupported

/**
 * 任意颜色滤镜。矩阵未知，无法折进折射着色器，故整条链保持原样（退化为独立的颜色 pass）。
 * 只有本文件自带的饱和度 / 亮度 / 对比度 / 不透明度会走 [applyTint]，它们能折。
 */
fun BackdropEffectScope.colorFilter(colorFilter: ColorFilter) {
    if (!isRenderEffectSupported()) return

    renderEffect = ColorFilterEffect(renderEffect, colorFilter)
    (this as? BackdropEffectScopeImpl)?.recordRawOp()
}

fun BackdropEffectScope.opacity(@FloatRange(from = 0.0, to = 1.0) alpha: Float) {
    val matrix = opacityColorMatrix(alpha)
    applyTint(ColorMatrixColorFilter(ColorMatrix(matrix)), matrix)
}

fun BackdropEffectScope.colorControls(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f
) {
    if (brightness == 0f && contrast == 1f && saturation == 1f) {
        return
    }

    val matrix = colorControlsColorMatrix(brightness, contrast, saturation)
    applyTint(ColorMatrixColorFilter(ColorMatrix(matrix)), matrix)
}

private val VibrantColorFilter = colorControlsColorFilter(saturation = 1.5f)
private val VibrantColorMatrix = saturationColorMatrix(1.5f)

fun BackdropEffectScope.vibrancy() {
    applyTint(VibrantColorFilter, VibrantColorMatrix)
}

/**
 * 应用一个「已知矩阵」的颜色算子：既按老路子挂到 RenderEffect 链上（不满足合并条件时原样生效），
 * 也把矩阵记进 scope，供末尾把这一段折进折射着色器。
 */
private fun BackdropEffectScope.applyTint(colorFilter: ColorFilter, matrix: FloatArray) {
    if (!isRenderEffectSupported()) return

    renderEffect = ColorFilterEffect(renderEffect, colorFilter)
    (this as? BackdropEffectScopeImpl)?.recordTintOp(matrix)
}

private fun colorControlsColorFilter(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f
): ColorFilter = ColorMatrixColorFilter(
    ColorMatrix(colorControlsColorMatrix(brightness, contrast, saturation))
)
