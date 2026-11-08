package com.haooz.softglass.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.haooz.softglass.platform.asSoftwareBitmap
import com.haooz.softglass.platform.createGlassImageBitmap
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/** 逐级模糊的壁纸金字塔，同一页面内的玻璃面板共享。 */
data class SoftGlassBackdrop(
    val levels: List<ImageBitmap>,
    val meanLuma: Float,
) {
    val levelCount: Int get() = levels.size
}

private const val BASE_MAX_EDGE = 1024
private const val LEVEL_COUNT = 7

/** 每级降采样后追加的盒式模糊半径（该级 texel）。 */
private const val LEVEL_BLUR_RADIUS = 1

/** 第 1 级的等效 σ（第 0 级 texel）。 */
private const val LEVEL1_SIGMA = 3f

/** 由壁纸构建金字塔；逐级 1/2 降采样 + 三次盒式模糊。 */
fun prepareSoftGlassBackdrop(wallpaper: ImageBitmap): SoftGlassBackdrop? {
    if (wallpaper.width <= 0 || wallpaper.height <= 0) return null
    return runCatching {
        val base = scaleToMaxEdge(wallpaper.asSoftwareBitmap(), BASE_MAX_EDGE)
        var width = base.width
        var height = base.height
        var pixels = base.toPixelMap().buffer

        val levels = ArrayList<ImageBitmap>(LEVEL_COUNT)
        levels += createGlassImageBitmap(pixels, width, height)
        for (i in 1 until LEVEL_COUNT) {
            val nextW = (width / 2).coerceAtLeast(1)
            val nextH = (height / 2).coerceAtLeast(1)
            if (nextW == width && nextH == height) {
                levels += levels.last()
                continue
            }
            pixels = downsample2x(pixels, width, height, nextW, nextH)
            width = nextW
            height = nextH
            boxBlurTriple(pixels, width, height, LEVEL_BLUR_RADIUS)
            levels += createGlassImageBitmap(pixels, width, height)
        }
        SoftGlassBackdrop(levels = levels, meanLuma = meanLumaOf(levels.last()))
    }.getOrNull()
}

data class LevelPair(val a: Int, val b: Int, val t: Float)

/** 把模糊半径（第 0 级 texel）映射为相邻两级与混合权重。 */
fun SoftGlassBackdrop.levelPair(
    radiusInBaseTexels: Float,
    levelCount: Int = this.levelCount,
): LevelPair {
    if (levelCount <= 1) return LevelPair(0, 0, 0f)
    val last = levelCount - 1
    val r = radiusInBaseTexels.coerceAtLeast(0f)
    if (r <= 1f) return LevelPair(0, 0, 0f)
    if (r < LEVEL1_SIGMA) {
        return LevelPair(0, 1, (r / LEVEL1_SIGMA).coerceIn(0f, 1f))
    }
    val lvl = (1f + ln(r / LEVEL1_SIGMA) / ln(2f)).coerceIn(0f, last.toFloat())
    val a = lvl.toInt().coerceIn(0, last)
    if (a >= last) return LevelPair(last, last, 0f)
    return LevelPair(a, a + 1, (lvl - a).coerceIn(0f, 1f))
}

/** 屏幕坐标到第 0 级纹理 texel 的仿射 `texPx = screenPx * (kx, ky) + (mx, my)`。 */
fun wallpaperTexMap(
    textureWidth: Int,
    textureHeight: Int,
    wallpaperScale: Float,
    wallpaperOffset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
): FloatArray {
    val w = screenWidthPx.coerceAtLeast(1f)
    val h = screenHeightPx.coerceAtLeast(1f)
    val tw = textureWidth.coerceAtLeast(1).toFloat()
    val th = textureHeight.coerceAtLeast(1).toFloat()

    val fitScale = minOf(w / tw, h / th)
    val fitOffX = (w - tw * fitScale) / 2f
    val fitOffY = (h - th * fitScale) / 2f
    val cover = maxOf(w / tw, h / th)
    val minScale = if (fitScale > 0f) cover / fitScale else 1f
    val s = maxOf(wallpaperScale, minScale)

    val kx = 1f / (s * fitScale)
    val ky = kx
    val mx = ((-w / 2f - wallpaperOffset.x) / s - fitOffX + w / 2f) / fitScale
    val my = ((-h / 2f - wallpaperOffset.y) / s - fitOffY + h / 2f) / fitScale
    return floatArrayOf(kx, ky, mx, my)
}

/** 把采样原点从变换后的窗口坐标还原到壁纸层的逻辑坐标。 */
fun untransformGlassOrigin(
    origin: Offset,
    scale: Float,
    translationY: Float,
    pivotX: Float,
    pivotY: Float,
): Offset {
    if (scale == 1f && translationY == 0f) return origin
    val s = if (scale == 0f) 1f else scale
    return Offset(
        x = (origin.x - pivotX) / s + pivotX,
        y = (origin.y - pivotY - translationY) / s + pivotY,
    )
}

private fun scaleToMaxEdge(src: ImageBitmap, maxEdge: Int): ImageBitmap {
    val longest = max(src.width, src.height)
    if (longest <= maxEdge) return src
    val f = maxEdge.toFloat() / longest
    val tw = (src.width * f).roundToInt().coerceAtLeast(1)
    val th = (src.height * f).roundToInt().coerceAtLeast(1)
    val out = ImageBitmap(tw, th, ImageBitmapConfig.Argb8888)
    Canvas(out).drawImageRect(
        image = src,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(src.width, src.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(tw, th),
        paint = Paint().apply { filterQuality = FilterQuality.Medium },
    )
    return out
}

private fun downsample2x(
    src: IntArray,
    width: Int,
    height: Int,
    outWidth: Int,
    outHeight: Int,
): IntArray {
    val fx = (width / outWidth).coerceAtLeast(1)
    val fy = (height / outHeight).coerceAtLeast(1)
    val count = fx * fy
    val out = IntArray(outWidth * outHeight)
    for (y in 0 until outHeight) {
        for (x in 0 until outWidth) {
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (dy in 0 until fy) {
                val sy = (y * fy + dy).coerceAtMost(height - 1)
                val row = sy * width
                for (dx in 0 until fx) {
                    val c = src[row + (x * fx + dx).coerceAtMost(width - 1)]
                    sa += (c ushr 24) and 0xFF
                    sr += (c shr 16) and 0xFF
                    sg += (c shr 8) and 0xFF
                    sb += c and 0xFF
                }
            }
            out[y * outWidth + x] =
                ((sa / count) shl 24) or ((sr / count) shl 16) or
                    ((sg / count) shl 8) or (sb / count)
        }
    }
    return out
}

private fun boxBlurTriple(pixels: IntArray, width: Int, height: Int, radius: Int) {
    if (radius <= 0) return
    val tmp = IntArray(pixels.size)
    repeat(3) {
        boxPass(pixels, tmp, width, height, radius, horizontal = true)
        boxPass(tmp, pixels, width, height, radius, horizontal = false)
    }
}

private fun boxPass(
    src: IntArray,
    dst: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val outer = if (horizontal) height else width
    val inner = if (horizontal) width else height
    val step = if (horizontal) 1 else width
    val lineStart = if (horizontal) width else 1
    val count = 2 * radius + 1

    for (o in 0 until outer) {
        val base = o * lineStart
        var sa = 0
        var sr = 0
        var sg = 0
        var sb = 0
        for (i in -radius..radius) {
            val c = src[base + i.coerceIn(0, inner - 1) * step]
            sa += (c ushr 24) and 0xFF
            sr += (c shr 16) and 0xFF
            sg += (c shr 8) and 0xFF
            sb += c and 0xFF
        }
        for (i in 0 until inner) {
            dst[base + i * step] =
                ((sa / count) shl 24) or
                    ((sr / count) shl 16) or
                    ((sg / count) shl 8) or
                    (sb / count)

            val outC = src[base + (i - radius).coerceIn(0, inner - 1) * step]
            val inC = src[base + (i + radius + 1).coerceIn(0, inner - 1) * step]
            sa += ((inC ushr 24) and 0xFF) - ((outC ushr 24) and 0xFF)
            sr += ((inC shr 16) and 0xFF) - ((outC shr 16) and 0xFF)
            sg += ((inC shr 8) and 0xFF) - ((outC shr 8) and 0xFF)
            sb += (inC and 0xFF) - (outC and 0xFF)
        }
    }
}

private fun meanLumaOf(pixels: ImageBitmap): Float {
    val buffer = runCatching { pixels.toPixelMap().buffer }.getOrNull() ?: return 0.5f
    if (buffer.isEmpty()) return 0.5f
    var sum = 0f
    for (c in buffer) {
        sum += (0.2126f * ((c shr 16) and 0xFF) +
            0.7152f * ((c shr 8) and 0xFF) +
            0.0722f * (c and 0xFF)) / 255f
    }
    return sum / buffer.size
}
