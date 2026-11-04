// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas

/** AGSL `RuntimeShader` 从 API 33 起才有；低版本直接不走玻璃，保留纯色表面。 */
actual fun isSoftGlassShaderAvailable(): Boolean = Build.VERSION.SDK_INT >= 33

/**
 * Android 后端：AGSL `RuntimeShader` + `Paint`。
 *
 * 描边混合用 `PorterDuff.Mode.LIGHTEN` 而不是 `BlendMode.LIGHTEN`：
 * 后者是 API 29 才有的 `Paint.setBlendMode`，minSdk 26 上会 `NoSuchMethodError`。
 * 两者都是逐通道取 max，观感一致。
 */
@RequiresApi(33)
actual class SoftGlassProgram actual constructor(source: String) {
    private val shader = RuntimeShader(source)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = this@SoftGlassProgram.shader
        // 采样纹理必须双线性，否则圆角/描边是 1px 台阶
        isFilterBitmap = true
    }

    actual fun setFloat(name: String, value: Float) {
        shader.setFloatUniform(name, value)
    }

    actual fun setFloat2(name: String, x: Float, y: Float) {
        shader.setFloatUniform(name, x, y)
    }

    actual fun setFloat3(name: String, x: Float, y: Float, z: Float) {
        shader.setFloatUniform(name, x, y, z)
    }

    actual fun setFloat4(name: String, x: Float, y: Float, z: Float, w: Float) {
        shader.setFloatUniform(name, x, y, z, w)
    }

    actual fun setInt(name: String, value: Int) {
        shader.setIntUniform(name, value)
    }

    actual fun setSampler(name: String, bitmap: ImageBitmap) {
        val bitmapShader = BitmapShader(
            bitmap.asAndroidBitmap(),
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP,
        ).apply {
            // RuntimeShader 的输入 shader 不受 Paint.isFilterBitmap 控制，
            // 必须显式开线性采样；否则纹理是最近邻，圆角与描边全是 1px 台阶。
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }
        shader.setInputShader(name, bitmapShader)
    }

    actual fun draw(canvas: androidx.compose.ui.graphics.Canvas, width: Float, height: Float, blend: SoftGlassBlend) {
        paint.xfermode = when (blend) {
            SoftGlassBlend.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
            SoftGlassBlend.NORMAL -> null
        }
        canvas.nativeCanvas.drawRect(0f, 0f, width, height, paint)
    }
}

actual fun createImageBitmapFromArgb(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

actual fun ImageBitmap.asSoftwareBitmap(): ImageBitmap {
    val raw = asAndroidBitmap()
    if (raw.config != Bitmap.Config.HARDWARE) return this
    val copy = raw.copy(Bitmap.Config.ARGB_8888, false) ?: return this
    return copy.asImageBitmap()
}
