package com.haooz.softglass.internal

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asSkiaColorFilter
import com.haooz.softglass.platform.GlassRuntimeShader
import com.haooz.softglass.platform.asSkikoRuntimeShader
import org.jetbrains.skia.ImageFilter

/**
 * RenderEffect 链在鸿蒙上与桌面同构：全部落到 Skia ImageFilter，再包回 Compose。
 * 用到的 `RenderEffect.asSkiaImageFilter()` / `ImageFilter.asComposeRenderEffect()` 都是
 * 这套 klib 的公开 API，效果与 Android 的 `android.graphics.RenderEffect` 等价。
 */
internal actual fun RenderEffect?.glassChain(other: RenderEffect): RenderEffect =
    if (this != null) {
        ImageFilter.makeCompose(other.asSkiaImageFilter(), asSkiaImageFilter())
            .asComposeRenderEffect()
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
    renderEffect?.asSkiaImageFilter(),
    null,
).asComposeRenderEffect()
