package com.haooz.chedule.ui.effects.motion

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * Skia 后端（iOS / 鸿蒙 / 桌面）用 SkSL 跑与 Android 同一份 shader 源。
 * 不复用 miuix-blur 的 `asSkikoRuntimeShader()`：桌面版 public、鸿蒙 vendor 版 internal，
 * 同一份 skikoMain 源码会在鸿蒙编不过；直接用 Skia API 没有这层差异。
 */
private var arcBendCapable: Boolean? = null

actual fun arcBendSupported(): Boolean {
    if (arcBendCapable == null) {
        arcBendCapable = runCatching { RuntimeEffect.makeForShader(ARC_BEND_SHADER) }.isSuccess
    }
    return arcBendCapable == true
}

internal actual fun createArcBendShader(): ArcBendShader? {
    if (!arcBendSupported()) return null
    val builder = runCatching {
        RuntimeShaderBuilder(RuntimeEffect.makeForShader(ARC_BEND_SHADER))
    }.getOrNull() ?: return null
    return object : ArcBendShader {
        override fun update(
            width: Float,
            height: Float,
            amplitude: Float,
            shape: ArcBendShape,
            axis: ArcBendAxis,
        ) {
            builder.uniform("resolution", width, height)
            builder.uniform("amplitude", amplitude)
            builder.uniform("bendShape", if (shape == ArcBendShape.InwardOutward) 1f else 0f)
            builder.uniform("bendAxis", if (axis == ArcBendAxis.Vertical) 1f else 0f)
        }

        override fun renderEffect(): RenderEffect? = runCatching {
            ImageFilter.makeRuntimeShader(builder, "content", null).asComposeRenderEffect()
        }.getOrNull()
    }
}
