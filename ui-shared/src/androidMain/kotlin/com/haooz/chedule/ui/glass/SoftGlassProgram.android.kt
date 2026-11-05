// Copyright (C) 2026 Haooz · AGPL-3.0-only
//
// AGSL（Android）后端：shader 编译、uniform 灌装、位图采样器与绘制。
//
// 源码与 Skiko 端共用，本文件仅封装 Android 平台差异。

package com.haooz.chedule.ui.glass

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

/** AGSL `RuntimeShader` 自 API 33 起可用；更低版本回退为纯色表面。 */
actual fun isSoftGlassShaderAvailable(): Boolean = Build.VERSION.SDK_INT >= 33

/**
 * Android 后端：AGSL `RuntimeShader` 与 `Paint`。
 *
 * 描边混合使用 `PorterDuff.Mode.LIGHTEN`，而非 API 29 才提供的
 * `Paint.setBlendMode(BlendMode.LIGHTEN)`，以兼容较低的 minSdk。
 * 二者均为逐通道取最大值，结果一致。
 */
@RequiresApi(33)
actual class SoftGlassProgram actual constructor(source: String) {
    private val shader = RuntimeShader(source)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = this@SoftGlassProgram.shader
        // 采样纹理需双线性过滤，否则圆角与描边会出现 1px 台阶
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
            // 需显式启用线性采样；否则纹理按最近邻取样，圆角与描边会出现 1px 台阶。
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

/** 由 CPU 生成的 ARGB 像素创建位图，用于回写模糊结果。 */
actual fun createImageBitmapFromArgb(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

/** Android 的 HARDWARE 位图无法被软件画布读取，此处返回可读取的副本。 */
actual fun ImageBitmap.asSoftwareBitmap(): ImageBitmap {
    val raw = asAndroidBitmap()
    if (raw.config != Bitmap.Config.HARDWARE) return this
    val copy = raw.copy(Bitmap.Config.ARGB_8888, false) ?: return this
    return copy.asImageBitmap()
}
