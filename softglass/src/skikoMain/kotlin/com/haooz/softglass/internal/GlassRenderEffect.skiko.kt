package com.haooz.softglass.internal

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.skiaImageFilter
import com.haooz.softglass.platform.GlassRuntimeShader
import com.haooz.softglass.platform.asSkikoRuntimeShader
import org.jetbrains.skia.ImageFilter

internal actual fun RenderEffect?.glassChain(other: RenderEffect): RenderEffect =
    if (this != null) {
        ImageFilter.makeCompose(other.skiaImageFilter, skiaImageFilter).asComposeRenderEffect()
    } else {
        other
    }

internal actual fun glassRuntimeShaderEffect(
    shader: GlassRuntimeShader,
    uniformShaderName: String,
): RenderEffect = ImageFilter.makeRuntimeShader(
    shader.asSkikoRuntimeShader(),
    uniformShaderName,
    null,
).asComposeRenderEffect()

internal actual fun glassColorFilterEffect(
    renderEffect: RenderEffect?,
    colorFilter: ColorFilter,
): RenderEffect = ImageFilter.makeColorFilter(
    colorFilter.asSkiaColorFilter(),
    renderEffect?.skiaImageFilter,
    null,
).asComposeRenderEffect()
