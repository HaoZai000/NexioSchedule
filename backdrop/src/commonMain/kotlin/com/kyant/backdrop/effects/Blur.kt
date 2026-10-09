package com.kyant.backdrop.effects

import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.DOWNSAMPLE_SCALE
import com.kyant.backdrop.isRenderEffectSupported

fun BackdropEffectScope.blur(
    @FloatRange(from = 0.0) radius: Float,
    edgeTreatment: TileMode = TileMode.Clamp
) {
    if (!isRenderEffectSupported()) return
    if (radius <= 0f) return

    if (edgeTreatment != TileMode.Clamp || renderEffect != null) {
        if (radius > padding) {
            padding = radius
        }
    }

    // 模糊作用在降采样缓冲上，半径需同步 ×scale，使视觉扩散范围保持一致。
    val scaledRadius = radius * DOWNSAMPLE_SCALE
    renderEffect =
        BlurEffect(
            renderEffect,
            scaledRadius,
            scaledRadius,
            edgeTreatment
        )
}
