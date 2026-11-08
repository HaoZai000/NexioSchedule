package com.haooz.softglass.platform

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader

/** 平台运行时 shader 的统一 uniform 接口。 */
interface GlassRuntimeShader {
    fun setFloatUniform(name: String, value: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float)
    fun setFloatUniform(name: String, values: FloatArray)
    fun setIntUniform(name: String, value: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int)
    fun setIntUniform(name: String, values: IntArray)
    fun setColorUniform(name: String, color: Color)
}

expect fun createGlassRuntimeShader(source: String): GlassRuntimeShader

expect fun GlassRuntimeShader.asComposeShader(): Shader

/** 进程级共享编译程序池，key 与 shader 源码一一对应。 */
object GlassShaderRegistry {
    private val shaders = mutableMapOf<String, GlassRuntimeShader>()

    fun obtain(key: String, source: String): GlassRuntimeShader =
        shaders.getOrPut(key) { createGlassRuntimeShader(source) }
}
