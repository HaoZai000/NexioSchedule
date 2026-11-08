package com.haooz.softglass.platform

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap

/** 主体使用常规混合，描边仅提亮。 */
enum class GlassBlend { NORMAL, LIGHTEN }

/** 柔光 shader 的平台后端；shader 源码在两端共用。 */
expect class GlassShaderProgram(source: String) {
    fun setFloat(name: String, value: Float)
    fun setFloat2(name: String, x: Float, y: Float)
    fun setFloat3(name: String, x: Float, y: Float, z: Float)
    fun setFloat4(name: String, x: Float, y: Float, z: Float, w: Float)
    fun setInt(name: String, value: Int)
    fun setSampler(name: String, bitmap: ImageBitmap)
    fun draw(canvas: Canvas, width: Float, height: Float, blend: GlassBlend)
}

/** 由 CPU 生成的 ARGB 像素创建位图。 */
expect fun createGlassImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap

/** 返回可被软件画布读取的位图；Android 的 HARDWARE 位图需复制。 */
expect fun ImageBitmap.asSoftwareBitmap(): ImageBitmap
