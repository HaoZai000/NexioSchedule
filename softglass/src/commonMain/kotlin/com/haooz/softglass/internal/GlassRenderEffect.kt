package com.haooz.softglass.internal

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import com.haooz.softglass.platform.GlassRuntimeShader

internal expect fun RenderEffect?.glassChain(other: RenderEffect): RenderEffect

internal expect fun glassRuntimeShaderEffect(
    shader: GlassRuntimeShader,
    uniformShaderName: String,
): RenderEffect

internal expect fun glassColorFilterEffect(
    renderEffect: RenderEffect?,
    colorFilter: ColorFilter,
): RenderEffect
