// 鸿蒙端：与 Android / 桌面同一套运行时着色器实现。
// `Shader` 在本 klib 是 `org.jetbrains.skia.Shader` 的类型别名，Skia 的 RuntimeShaderBuilder
// 编译出的 shader 可直接交给 Compose，AGSL/SkSL 源码两端共用。
package top.yukonga.miuix.kmp.shader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

actual fun isRuntimeShaderSupported(): Boolean = true

actual fun isRenderEffectSupported(): Boolean = true

private class SkikoRuntimeShader(val builder: RuntimeShaderBuilder) : RuntimeShader {

    override fun setFloatUniform(name: String, value: Float) = builder.uniform(name, value)

    override fun setFloatUniform(name: String, value1: Float, value2: Float) =
        builder.uniform(name, value1, value2)

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) =
        builder.uniform(name, value1, value2, value3)

    override fun setFloatUniform(
        name: String,
        value1: Float,
        value2: Float,
        value3: Float,
        value4: Float,
    ) = builder.uniform(name, value1, value2, value3, value4)

    override fun setFloatUniform(name: String, values: FloatArray) = builder.uniform(name, values)

    override fun setIntUniform(name: String, value: Int) = builder.uniform(name, value)

    override fun setIntUniform(name: String, value1: Int, value2: Int) =
        builder.uniform(name, value1, value2)

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) =
        builder.uniform(name, value1, value2, value3)

    override fun setIntUniform(
        name: String,
        value1: Int,
        value2: Int,
        value3: Int,
        value4: Int,
    ) = builder.uniform(name, value1, value2, value3, value4)

    override fun setIntUniform(name: String, values: IntArray) {
        throw UnsupportedOperationException("Skia runtime shader does not support int array uniforms")
    }

    override fun setColorUniform(name: String, color: Color) {
        val srgb = color.convert(ColorSpaces.Srgb)
        val a = srgb.alpha
        builder.uniform(name, srgb.red * a, srgb.green * a, srgb.blue * a, a)
    }

    override fun setInputShader(name: String, shader: Shader) = builder.child(name, shader)
}

actual fun RuntimeShader(shaderString: String): RuntimeShader =
    SkikoRuntimeShader(RuntimeShaderBuilder(RuntimeEffect.makeForShader(shaderString)))

actual fun RuntimeShader.asComposeShader(): Shader =
    (this as SkikoRuntimeShader).builder.makeShader()

/** Skiko 下每次都要新建 Brush：uniform 更新后才拿得到新的不可变 shader 快照。 */
actual fun RuntimeShader.asBrush(): ShaderBrush = ShaderBrush(asComposeShader())

internal fun RuntimeShader.asSkikoRuntimeShader(): RuntimeShaderBuilder =
    (this as SkikoRuntimeShader).builder
