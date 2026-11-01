package com.kyant.backdrop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.TileMode

/**
 * 鸿蒙端 RuntimeShader：能构造、能设 uniform，画不出东西。
 *
 * ui-ohos klib 没公开 `asComposeShader` / `skiaPaint` / `skiaImageFilter`，AGSL 喂不进 Paint；
 * `isRuntimeShaderSupported()` 恒为 false，上层调用点都提前返回了。
 */
private object UnsupportedRuntimeShader : RuntimeShader {

    override fun setFloatUniform(name: String, value: Float) = Unit

    override fun setFloatUniform(name: String, value1: Float, value2: Float) = Unit

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) = Unit

    override fun setFloatUniform(
        name: String,
        value1: Float,
        value2: Float,
        value3: Float,
        value4: Float,
    ) = Unit

    override fun setFloatUniform(name: String, values: FloatArray) = Unit

    override fun setIntUniform(name: String, value: Int) = Unit

    override fun setIntUniform(name: String, value1: Int, value2: Int) = Unit

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) = Unit

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) = Unit

    override fun setIntUniform(name: String, values: IntArray) = Unit

    override fun setColorUniform(name: String, color: Color) = Unit
}

actual fun RuntimeShader(shaderString: String): RuntimeShader = UnsupportedRuntimeShader

/** 1×1 透明位图做的合法 Shader；调用方返回类型非空，给不了 null。 */
actual fun RuntimeShader.asComposeShader(): Shader = TransparentShader

private val TransparentShader: Shader by lazy {
    ImageShader(
        image = ImageBitmap(1, 1),
        tileModeX = TileMode.Clamp,
        tileModeY = TileMode.Clamp,
    )
}
