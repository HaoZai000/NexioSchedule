package com.haooz.softglass.internal

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.haooz.softglass.platform.GlassRuntimeShader
import com.haooz.softglass.platform.asAndroidRuntimeShader

@RequiresApi(Build.VERSION_CODES.S)
internal actual fun RenderEffect?.glassChain(other: RenderEffect): RenderEffect =
    if (this != null) {
        android.graphics.RenderEffect.createChainEffect(
            other.asAndroidRenderEffect(),
            asAndroidRenderEffect(),
        ).asComposeRenderEffect()
    } else {
        other
    }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun glassRuntimeShaderEffect(
    shader: GlassRuntimeShader,
    uniformShaderName: String,
): RenderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(
    shader.asAndroidRuntimeShader(),
    uniformShaderName,
).asComposeRenderEffect()

@RequiresApi(Build.VERSION_CODES.S)
internal actual fun glassColorFilterEffect(
    renderEffect: RenderEffect?,
    colorFilter: ColorFilter,
): RenderEffect {
    val filter = colorFilter.asAndroidColorFilter()
    return if (renderEffect != null) {
        android.graphics.RenderEffect.createColorFilterEffect(
            filter,
            renderEffect.asAndroidRenderEffect(),
        ).asComposeRenderEffect()
    } else {
        android.graphics.RenderEffect.createColorFilterEffect(filter).asComposeRenderEffect()
    }
}
