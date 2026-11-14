package com.kyant.backdrop.effects

import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import com.kyant.backdrop.BackdropEffectScope
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

    // 模糊作用在降采样缓冲上，半径需同步 ×该缓冲的实际比例，使视觉扩散范围保持一致。
    // ⚠ 必须取 scope 上的 downsampleScale，不能用编译期常量：曾残留常量使所有卡片自模糊半径被缩到 42%（长按浮层清于网格卡）；Lens.kt 用同一字段，两边必须一致。
    val scaledRadius = radius * downsampleScale
    renderEffect =
        BlurEffect(
            renderEffect,
            scaledRadius,
            scaledRadius,
            edgeTreatment
        )
}
