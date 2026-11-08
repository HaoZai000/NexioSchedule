package com.haooz.softglass.platform

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

actual fun createGlassRuntimeShader(source: String): GlassRuntimeShader =
    SkikoGlassRuntimeShader(RuntimeShaderBuilder(RuntimeEffect.makeForShader(source)))

actual fun GlassRuntimeShader.asComposeShader(): Shader =
    asSkikoRuntimeShader().makeShader().asComposeShader()

fun GlassRuntimeShader.asSkikoRuntimeShader(): RuntimeShaderBuilder =
    (this as SkikoGlassRuntimeShader).builder

internal class SkikoGlassRuntimeShader(val builder: RuntimeShaderBuilder) : GlassRuntimeShader {
    override fun setFloatUniform(name: String, value: Float) = builder.uniform(name, value)
    override fun setFloatUniform(name: String, value1: Float, value2: Float) =
        builder.uniform(name, value1, value2)
    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) =
        builder.uniform(name, value1, value2, value3)
    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) =
        builder.uniform(name, value1, value2, value3, value4)
    override fun setFloatUniform(name: String, values: FloatArray) = builder.uniform(name, values)
    override fun setIntUniform(name: String, value: Int) = builder.uniform(name, value)
    override fun setIntUniform(name: String, value1: Int, value2: Int) = builder.uniform(name, value1, value2)
    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) =
        builder.uniform(name, value1, value2, value3)
    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) =
        builder.uniform(name, value1, value2, value3, value4)
    override fun setIntUniform(name: String, values: IntArray) {
        throw UnsupportedOperationException("Skia runtime shader does not support int array uniforms")
    }

    override fun setColorUniform(name: String, color: Color) {
        val srgb = color.convert(ColorSpaces.Srgb)
        val a = srgb.alpha
        builder.uniform(name, srgb.red * a, srgb.green * a, srgb.blue * a, a)
    }
}
