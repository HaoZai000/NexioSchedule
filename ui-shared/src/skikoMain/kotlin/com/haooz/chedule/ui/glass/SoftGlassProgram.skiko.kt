// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.glass

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode

actual fun isSoftGlassShaderAvailable(): Boolean = true

actual fun ImageBitmap.asSoftwareBitmap(): ImageBitmap = this

/**
 * Skiko 后端（桌面 / iOS / 鸿蒙共用）：SkSL `RuntimeEffect`。
 *
 * 与 Android 的 AGSL 是同一门方言，[source] 直接用 `SoftGlassShader.kt` 里那份。
 * uniform 是「builder 存值、`makeShader()` 时烘焙」，所以 `uOrigin` 每帧变也没关系 ——
 * 每帧重建一次 shader，但省掉 AGSL 那套 `setFloatUniform` 的平台分支。
 */
actual class SoftGlassProgram actual constructor(source: String) {
    private val effect = RuntimeEffect.makeForShader(source)
    private val builder = RuntimeShaderBuilder(effect)
    private val paint = Paint().apply { isAntiAlias = true }

    actual fun setFloat(name: String, value: Float) {
        builder.uniform(name, value)
    }

    actual fun setFloat2(name: String, x: Float, y: Float) {
        builder.uniform(name, x, y)
    }

    actual fun setFloat3(name: String, x: Float, y: Float, z: Float) {
        builder.uniform(name, x, y, z)
    }

    actual fun setFloat4(name: String, x: Float, y: Float, z: Float, w: Float) {
        builder.uniform(name, x, y, z, w)
    }

    actual fun setInt(name: String, value: Int) {
        builder.uniform(name, value)
    }

    actual fun setSampler(name: String, bitmap: ImageBitmap) {
        // 必须显式 LINEAR：Skia 的 SamplingMode.DEFAULT 是 NEAREST，
        // 纹理用最近邻采样会让圆角/描边出现 1px 锯齿。
        builder.child(name, bitmap.asSkiaBitmap().makeShader(sampling = SamplingMode.LINEAR))
    }

    actual fun draw(canvas: androidx.compose.ui.graphics.Canvas, width: Float, height: Float, blend: SoftGlassBlend) {
        paint.shader = builder.makeShader()
        paint.blendMode = when (blend) {
            SoftGlassBlend.LIGHTEN -> BlendMode.LIGHTEN
            SoftGlassBlend.NORMAL -> BlendMode.SRC_OVER
        }
        @Suppress("DEPRECATION", "DEPRECATION_ERROR")
        canvas.nativeCanvas.drawRect(Rect.makeWH(width, height), paint)
    }
}

/**
 * Skia 的 N32 在小端设备上是 BGRA_8888；Compose 的 ARGB Int 在内存里正好是 B,G,R,A，
 * 按字节顺序摊平即可，不需要逐像素打包。
 */
actual fun createImageBitmapFromArgb(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val bytes = ByteArray(pixels.size * 4)
    var i = 0
    for (c in pixels) {
        bytes[i++] = (c and 0xFF).toByte()
        bytes[i++] = ((c shr 8) and 0xFF).toByte()
        bytes[i++] = ((c shr 16) and 0xFF).toByte()
        bytes[i++] = ((c ushr 24) and 0xFF).toByte()
    }
    val info = ImageInfo.makeN32Premul(width, height)
    return Image.makeRaster(info, bytes, width * 4).toComposeImageBitmap()
}
