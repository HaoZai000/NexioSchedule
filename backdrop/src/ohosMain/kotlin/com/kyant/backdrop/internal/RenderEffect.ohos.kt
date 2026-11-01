package com.kyant.backdrop.internal

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asSkiaColorFilter
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.skikoBuilderOrNull
import org.jetbrains.skia.ImageFilter

/**
 * `internal` 的 4 个 expect 在鸿蒙端的实现。
 *
 * RenderEffect 这条路是通的：`ImageFilter` ↔ `RenderEffect` 两座桥 ui-ohos klib 都公开，
 * 所以滤镜叠加、色彩矩阵、AGSL 图层滤镜都能真做。
 * 只有 `Paint.setRuntimeShader`（Paint shader 路线）没有桥，保持空实现。
 */
internal actual fun Paint.blur(radius: Float) = Unit

internal actual fun Paint.setRuntimeShader(runtimeShader: RuntimeShader?) = Unit

internal actual fun RenderEffect?.chain(other: RenderEffect): RenderEffect =
    if (this == null) {
        other
    } else {
        runCatching {
            ImageFilter.makeCompose(other.asSkiaImageFilter(), this.asSkiaImageFilter())
                .asComposeRenderEffect()
        }.getOrElse { other }
    }

internal actual fun RuntimeShaderEffect(
    runtimeShader: RuntimeShader,
    uniformShaderName: String,
): RenderEffect {
    val builder = runtimeShader.skikoBuilderOrNull() ?: return NoEffect
    return runCatching {
        ImageFilter.makeRuntimeShader(builder, uniformShaderName, null).asComposeRenderEffect()
    }.getOrElse { NoEffect }
}

internal actual fun ColorFilterEffect(
    renderEffect: RenderEffect?,
    colorFilter: ColorFilter,
): RenderEffect = runCatching {
    ImageFilter.makeColorFilter(
        colorFilter.asSkiaColorFilter(),
        renderEffect?.asSkiaImageFilter(),
        null,
    ).asComposeRenderEffect()
}.getOrElse { renderEffect ?: NoEffect }

/** 半径 0 的模糊是公开 API 里唯一的「空 RenderEffect」。 */
private val NoEffect: RenderEffect
    get() = BlurEffect(0f, 0f, TileMode.Decal)
