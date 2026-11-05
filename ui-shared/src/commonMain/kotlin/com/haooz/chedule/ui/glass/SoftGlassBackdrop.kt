// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 玻璃的采样源：一叠**逐级模糊的壁纸**（模糊金字塔），每页一份，页内所有玻璃面板共用。
 *
 * [levels] 第 0 级是清晰壁纸（最长边 [BASE_MAX_EDGE]），第 i 级是上一级的 1/2 再补一次模糊，
 * 等效半径约 `2^i` 个第 0 级 texel。shader 取相邻两级按权重混合 —— 于是「模糊半径」只是
 * 一个档位 + 一个权重，拖滑杆不重烘任何位图。
 *
 * 为什么不直接在 shader 里做螺旋模糊：半径 20 texel 以上要几千个抽头才能不出颗粒，
 * 十几个抽头只会得到「几个方向的重影」，看起来根本不像模糊。真实实现都是先降采样模糊。
 */
data class SoftGlassBackdrop(
    val levels: List<ImageBitmap>,
    val meanLuma: Float,
) {
    val levelCount: Int get() = levels.size
}

/** 第 0 级的最长边。再大对玻璃没意义（它本来就是模糊的），只白占显存与采样带宽。 */
private const val BASE_MAX_EDGE = 1024

/** 金字塔级数。7 级 ⇒ 等效半径覆盖到 64 个第 0 级 texel，够 80px 滑杆用。 */
private const val LEVEL_COUNT = 7

/**
 * 每级降采样之后再补的模糊半径（该级 texel）。
 *
 * 取 1 而不是 2：级间 σ 比越接近 1，shader 里「相邻两级按权重混合」时留下的
 * 清晰细节就越少 —— 否则低模糊段会看出「清晰图和糊图叠在一起」的半透明感。
 */
private const val LEVEL_BLUR_RADIUS = 1

/**
 * 把壁纸做成模糊金字塔。
 *
 * 逐级 1/2 降采样（2×2 盒式）再补一次小半径盒式模糊，三次盒式累积下来接近高斯。
 * 成本只跟壁纸有关，换壁纸时做一次，约几十毫秒。
 */
fun prepareSoftGlassBackdrop(wallpaper: ImageBitmap): SoftGlassBackdrop? {
    if (wallpaper.width <= 0 || wallpaper.height <= 0) return null
    return runCatching {
        val base = scaleToMaxEdge(wallpaper.asSoftwareBitmap(), BASE_MAX_EDGE)
        var width = base.width
        var height = base.height
        var pixels = base.toPixelMap().buffer

        // 第 0 级必须是**完全清晰**的原图：滑杆拖到 0 就是没有模糊。
        val levels = ArrayList<ImageBitmap>(LEVEL_COUNT)
        levels += createImageBitmapFromArgb(pixels, width, height)
        for (i in 1 until LEVEL_COUNT) {
            val nextW = (width / 2).coerceAtLeast(1)
            val nextH = (height / 2).coerceAtLeast(1)
            if (nextW == width && nextH == height) {
                // 已经到 1px，后面的级重复用最后一张
                levels += levels.last()
                continue
            }
            pixels = downsample2x(pixels, width, height, nextW, nextH)
            width = nextW
            height = nextH
            boxBlurTriple(pixels, width, height, LEVEL_BLUR_RADIUS)
            levels += createImageBitmapFromArgb(pixels, width, height)
        }
        SoftGlassBackdrop(levels = levels, meanLuma = meanLumaOf(levels.last()))
    }.getOrNull()
}

/**
 * 模糊半径（第 0 级 texel）→ 相邻两级 + 混合权重。
 *
 * 各级的等效 σ 是推算的：第 1 级由「1/2 降采样 + 三次盒式(半径 1)」构成，
 * 降采样贡献 σ ≈ 1 个第 0 级 texel，三次盒式（`3·((2r+1)²−1)/12` = 方差 2）在 1/2 分辨率上
 * 折算回来 σ ≈ 2.8，合起来约 **3**。之后每级翻倍。
 * 所以 [LEVEL1_SIGMA] 取 3：第 i 级（i≥1）的等效 σ ≈ `3·2^(i−1)`。
 *
 * 第 0 级是**完全清晰**的图（滑杆 0 = 无模糊），1 texel 以下直接用它；
 * 1 ~ 第 1 级之间线性过渡 —— 这段很窄，混合留下的「清晰细节残留」看不出来。
 */
data class LevelPair(val a: Int, val b: Int, val t: Float)

private const val LEVEL1_SIGMA = 3f

fun SoftGlassBackdrop.levelPair(radiusInBaseTexels: Float, levelCount: Int = this.levelCount): LevelPair {
    if (levelCount <= 1) return LevelPair(0, 0, 0f)
    val last = levelCount - 1
    val r = radiusInBaseTexels.coerceAtLeast(0f)
    if (r <= 1f) return LevelPair(0, 0, 0f)
    if (r < LEVEL1_SIGMA) {
        // 0 ~ 第 1 级之间线性过渡
        return LevelPair(0, 1, (r / LEVEL1_SIGMA).coerceIn(0f, 1f))
    }
    val lvl = (1f + kotlin.math.ln(r / LEVEL1_SIGMA) / kotlin.math.ln(2f))
        .coerceIn(0f, last.toFloat())
    val a = lvl.toInt().coerceIn(0, last)
    if (a >= last) return LevelPair(last, last, 0f)
    return LevelPair(a, a + 1, (lvl - a).coerceIn(0f, 1f))
}

/**
 * 屏幕坐标 → 第 0 级纹理 texel 的仿射：`texPx = screenPx * (k.x, k.y) + (m.x, m.y)`。
 *
 * 与壁纸层实际绘制一致（`CourseScheduleAppMainContent`）：
 * ```
 * screen = (tex × fitScale + fitOffset − center) × s + center + offset
 * s = max(wallpaperScale, minScale)；minScale = cover/fit
 * ```
 * 反解即得 k / m。**只跟手势与屏幕尺寸有关**，跟任何外观参数都无关 —— 拖壁纸时
 * 每条卡只是重灌这一个 float4，一次位图都不用重画。
 */
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

/** 等比缩到最长边 [maxEdge]；已经够小就原样返回（不复制）。 */
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

/** 2×2 盒式均值降采样。 */
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
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
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

/** 三趟盒式模糊（横竖交替），就地写回。三趟累积的方差够接近高斯。 */
private fun boxBlurTriple(pixels: IntArray, width: Int, height: Int, radius: Int) {
    if (radius <= 0) return
    val tmp = IntArray(pixels.size)
    repeat(3) {
        boxPass(pixels, tmp, width, height, radius, horizontal = true)
        boxPass(tmp, pixels, width, height, radius, horizontal = false)
    }
}

/**
 * 一趟盒式模糊，越界按边缘 clamp（窗口宽度恒为 `2r+1`）。
 * ARGB 打包成一个 Int，四个通道必须各自滑动累加 —— 直接对打包值求和会串味。
 */
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
        var sa = 0; var sr = 0; var sg = 0; var sb = 0
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

/** 平均亮度（Rec.709 权重）。传最粗的一级，几万像素，代价可忽略。 */
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
