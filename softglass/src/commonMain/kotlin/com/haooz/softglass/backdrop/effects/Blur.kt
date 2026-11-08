package com.haooz.softglass.backdrop.effects

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.platform.isRenderEffectSupported

fun GlassEffectScope.blur(radius: Float, edgeTreatment: TileMode = TileMode.Clamp) {
    if (!isRenderEffectSupported() || radius <= 0f) return

    if (edgeTreatment != TileMode.Clamp || renderEffect != null) {
        if (radius > padding) padding = radius
    }

    val scaledRadius = radius * downsampleScale
    renderEffect = BlurEffect(renderEffect, scaledRadius, scaledRadius, edgeTreatment)
}
