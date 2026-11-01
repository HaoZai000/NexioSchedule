// 鸿蒙端：RuntimeShader 与 RenderEffect 都不可用（ui-ohos klib 未公开 skiko 桥）。
// 一律空实现，不抛异常 —— 这些调用在 draw 链路里，抛出去就是整进程 SIGABRT。
package top.yukonga.miuix.kmp.shader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode

actual fun isRuntimeShaderSupported(): Boolean = false

actual fun isRenderEffectSupported(): Boolean = false

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

    override fun setInputShader(name: String, shader: Shader) = Unit
}

actual fun RuntimeShader(shaderString: String): RuntimeShader = UnsupportedRuntimeShader

/** 合法但透明的 Shader；理由同 `:backdrop` 的同名实现。 */
actual fun RuntimeShader.asComposeShader(): Shader = TransparentShader

actual fun RuntimeShader.asBrush(): ShaderBrush = TransparentBrush

private val TransparentShader: Shader by lazy {
    ImageShader(
        image = ImageBitmap(1, 1),
        tileModeX = TileMode.Clamp,
        tileModeY = TileMode.Clamp,
    )
}

private object TransparentBrush : ShaderBrush() {
    override fun createShader(size: Size): Shader = TransparentShader
}
