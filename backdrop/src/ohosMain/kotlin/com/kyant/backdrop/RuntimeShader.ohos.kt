package com.kyant.backdrop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * 鸿蒙端 RuntimeShader：**真编译 AGSL**，uniform 也真写进去。
 *
 * 能用的路线是图层滤镜（`ImageFilter.makeRuntimeShader` → `RenderEffect`，见
 * `internal/RenderEffect.ohos.kt`）；Paint shader 路线缺 `asComposeShader`，
 * 所以 [asComposeShader] 只返回透明 shader，`isRuntimeShaderSupported()` 保持 false。
 */
actual fun RuntimeShader(shaderString: String): RuntimeShader =
    runCatching {
        SkikoRuntimeShader(RuntimeShaderBuilder(RuntimeEffect.makeForShader(shaderString)))
    }.getOrElse {
        // 编译失败会静默退化成「没有折射」，留一条日志好定位
        if (!reportedShaderFailure) {
            reportedShaderFailure = true
            println("backdrop/ohos: AGSL 编译失败，折射降级 -> ${it.message}")
        }
        UnsupportedRuntimeShader
    }

private var reportedShaderFailure = false

/** Paint 路线没有桥；返回 1×1 透明 ImageShader，调用方返回类型非空。 */
actual fun RuntimeShader.asComposeShader(): Shader = TransparentShader

/** 给 `ImageFilter.makeRuntimeShader` 用；不是真 shader 时返回 null。 */
internal fun RuntimeShader.skikoBuilderOrNull(): RuntimeShaderBuilder? =
    (this as? SkikoRuntimeShader)?.shader

private class SkikoRuntimeShader(val shader: RuntimeShaderBuilder) : RuntimeShader {

    override fun setFloatUniform(name: String, value: Float) = shader.uniform(name, value)

    override fun setFloatUniform(name: String, value1: Float, value2: Float) =
        shader.uniform(name, value1, value2)

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) =
        shader.uniform(name, value1, value2, value3)

    override fun setFloatUniform(
        name: String,
        value1: Float,
        value2: Float,
        value3: Float,
        value4: Float,
    ) = shader.uniform(name, value1, value2, value3, value4)

    override fun setFloatUniform(name: String, values: FloatArray) = shader.uniform(name, values)

    override fun setIntUniform(name: String, value: Int) = shader.uniform(name, value)

    override fun setIntUniform(name: String, value1: Int, value2: Int) =
        shader.uniform(name, value1, value2)

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) =
        shader.uniform(name, value1, value2, value3)

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) =
        shader.uniform(name, value1, value2, value3, value4)

    override fun setIntUniform(name: String, values: IntArray) = Unit

    override fun setColorUniform(name: String, color: Color) {
        val srgb = color.convert(ColorSpaces.Srgb)
        val a = srgb.alpha
        shader.uniform(name, srgb.red * a, srgb.green * a, srgb.blue * a, a)
    }
}

/** AGSL 编译失败时的兜底：uniform 照收，画不出东西。 */
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

private val TransparentShader: Shader by lazy {
    ImageShader(
        image = ImageBitmap(1, 1),
        tileModeX = TileMode.Clamp,
        tileModeY = TileMode.Clamp,
    )
}
