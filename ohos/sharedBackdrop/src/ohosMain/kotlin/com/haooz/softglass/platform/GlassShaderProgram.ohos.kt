package com.haooz.softglass.platform

import androidx.compose.ui.graphics.Canvas
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

/** 与桌面/iOS 的 Skiko 实现一致：AGSL 源码直接交给 Skia 的 SkSL 运行时编译。 */
actual class GlassShaderProgram actual constructor(source: String) {
    private val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(source))
    private val paint = Paint().apply { isAntiAlias = true }

    actual fun setFloat(name: String, value: Float) = builder.uniform(name, value)

    actual fun setFloat2(name: String, x: Float, y: Float) = builder.uniform(name, x, y)

    actual fun setFloat3(name: String, x: Float, y: Float, z: Float) =
        builder.uniform(name, x, y, z)

    actual fun setFloat4(name: String, x: Float, y: Float, z: Float, w: Float) =
        builder.uniform(name, x, y, z, w)

    actual fun setInt(name: String, value: Int) = builder.uniform(name, value)

    actual fun setSampler(name: String, bitmap: ImageBitmap) {
        builder.child(name, bitmap.asSkiaBitmap().makeShader(sampling = SamplingMode.LINEAR))
    }

    actual fun draw(canvas: Canvas, width: Float, height: Float, blend: GlassBlend) {
        paint.shader = builder.makeShader()
        paint.blendMode = when (blend) {
            GlassBlend.LIGHTEN -> BlendMode.LIGHTEN
            GlassBlend.NORMAL -> BlendMode.SRC_OVER
        }
        canvas.nativeCanvas.drawRect(Rect.makeWH(width, height), paint)
    }
}

// Skia N32 在小端是 BGRA_8888，Compose 的 ARGB Int 内存顺序即 B,G,R,A，按字节摊平即可。
actual fun createGlassImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val bytes = ByteArray(pixels.size * 4)
    var i = 0
    for (c in pixels) {
        bytes[i++] = (c and 0xFF).toByte()
        bytes[i++] = ((c shr 8) and 0xFF).toByte()
        bytes[i++] = ((c shr 16) and 0xFF).toByte()
        bytes[i++] = ((c ushr 24) and 0xFF).toByte()
    }
    return Image.makeRaster(
        ImageInfo.makeN32Premul(width, height),
        bytes,
        width * 4,
    ).toComposeImageBitmap()
}

actual fun ImageBitmap.asSoftwareBitmap(): ImageBitmap = this
