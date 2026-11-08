package com.haooz.softglass.platform

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas

// 描边用 PorterDuff LIGHTEN 而非 API 29 的 BlendMode，兼容 minSdk 26；两者都是逐通道取最大值。
@RequiresApi(33)
actual class GlassShaderProgram actual constructor(source: String) {
    private val shader = RuntimeShader(source)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShader(this@GlassShaderProgram.shader)
        isFilterBitmap = true
    }

    actual fun setFloat(name: String, value: Float) = shader.setFloatUniform(name, value)
    actual fun setFloat2(name: String, x: Float, y: Float) = shader.setFloatUniform(name, x, y)
    actual fun setFloat3(name: String, x: Float, y: Float, z: Float) = shader.setFloatUniform(name, x, y, z)
    actual fun setFloat4(name: String, x: Float, y: Float, z: Float, w: Float) =
        shader.setFloatUniform(name, x, y, z, w)
    actual fun setInt(name: String, value: Int) = shader.setIntUniform(name, value)

    actual fun setSampler(name: String, bitmap: ImageBitmap) {
        val bitmapShader = BitmapShader(
            bitmap.asAndroidBitmap(),
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP,
        ).apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) }
        shader.setInputShader(name, bitmapShader)
    }

    actual fun draw(
        canvas: androidx.compose.ui.graphics.Canvas,
        width: Float,
        height: Float,
        blend: GlassBlend,
    ) {
        paint.xfermode = when (blend) {
            GlassBlend.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
            GlassBlend.NORMAL -> null
        }
        canvas.nativeCanvas.drawRect(0f, 0f, width, height, paint)
    }
}

actual fun createGlassImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

actual fun ImageBitmap.asSoftwareBitmap(): ImageBitmap {
    val raw = asAndroidBitmap()
    if (raw.config != Bitmap.Config.HARDWARE) return this
    val copy = raw.copy(Bitmap.Config.ARGB_8888, false) ?: return this
    return copy.asImageBitmap()
}
