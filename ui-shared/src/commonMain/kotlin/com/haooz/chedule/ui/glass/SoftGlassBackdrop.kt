// Copyright (C) 2026 Haooz · AGPL-3.0-only
//
// 柔光玻璃的采样源：把壁纸预烘焙为逐级模糊的金字塔，并计算屏幕到纹理的坐标映射。
//
// 模糊结果按页共享，参数变化只需更新 uniform，不重建位图。

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

// ── 采样源 ───────────────────────────────────────────────────────────

/**
 * 逐级模糊的壁纸金字塔，供同一页面内的所有玻璃面板共享。
 *
 * [levels] 第 0 级为清晰壁纸（最长边 [BASE_MAX_EDGE]），第 i 级由上一级按 1/2 降采样
 * 后补一次模糊得到，等效半径约 `2^i` 个第 0 级 texel。shader 在相邻两级间按权重混合，
 * 因此模糊半径只需表示为「级别 + 权重」，调整半径不触发位图重建。
 *
 * 半径超过 20 texel 时，shader 内少量抽头的螺旋采样会产生方向性重影；
 * 采用「先降采样、再模糊」的金字塔可获得稳定的大半径模糊。
 *
 * @property levels 由清晰到模糊排列的各级位图。
 * @property meanLuma 最粗一级的平均亮度（Rec.709）。
 */
data class SoftGlassBackdrop(
    val levels: List<ImageBitmap>,
    val meanLuma: Float,
) {
    val levelCount: Int get() = levels.size
}

/** 第 0 级位图的最长边；更大的分辨率对模糊采样没有增益，只会增加显存与带宽。 */
private const val BASE_MAX_EDGE = 1024

/** 金字塔级数。7 级可覆盖到 64 个第 0 级 texel 的等效半径。 */
private const val LEVEL_COUNT = 7

/**
 * 每级降采样后追加的盒式模糊半径（以该级 texel 计）。
 *
 * 取 1 而非 2：级间 σ 比越接近 1，相邻两级混合时残留的清晰细节越少，
 * 可避免低模糊段出现清晰层与模糊层叠加的半透明感。
 */
private const val LEVEL_BLUR_RADIUS = 1

/** 第 1 级的等效 σ（以第 0 级 texel 计）。 */
private const val LEVEL1_SIGMA = 3f

/**
 * 由壁纸构建模糊金字塔。
 *
 * 逐级按 1/2 降采样（2×2 盒式）并追加小半径盒式模糊，三次累积接近高斯。
 * 计算量仅与壁纸尺寸相关，更换壁纸时执行一次。
 *
 * @return 金字塔；输入尺寸非法或处理失败时返回 null。
 */
fun prepareSoftGlassBackdrop(wallpaper: ImageBitmap): SoftGlassBackdrop? {
    if (wallpaper.width <= 0 || wallpaper.height <= 0) return null
    return runCatching {
        val base = scaleToMaxEdge(wallpaper.asSoftwareBitmap(), BASE_MAX_EDGE)
        var width = base.width
        var height = base.height
        var pixels = base.toPixelMap().buffer

        // 第 0 级保持完全清晰，使模糊半径为 0 时无额外模糊。
        val levels = ArrayList<ImageBitmap>(LEVEL_COUNT)
        levels += createImageBitmapFromArgb(pixels, width, height)
        for (i in 1 until LEVEL_COUNT) {
            val nextW = (width / 2).coerceAtLeast(1)
            val nextH = (height / 2).coerceAtLeast(1)
            if (nextW == width && nextH == height) {
                // 已降到 1px，后续级别复用最后一张位图
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

// ── 级别选择 ─────────────────────────────────────────────────────────

/**
 * 相邻两级的索引与混合权重。
 *
 * @property a 较清晰一级的索引。
 * @property b 较模糊一级的索引。
 * @property t 由 [a] 到 [b] 的混合权重，取值 0..1。
 */
data class LevelPair(val a: Int, val b: Int, val t: Float)

/**
 * 把模糊半径（第 0 级 texel）映射为相邻两级与混合权重。
 *
 * 第 1 级由「1/2 降采样 + 三次盒式（半径 1）」构成：降采样贡献 σ ≈ 1 个第 0 级 texel，
 * 三次盒式（方差 `3·((2r+1)²−1)/12` = 2）在 1/2 分辨率上折算为 σ ≈ 2.8，合计约 3。
 * 之后每级翻倍，故第 i 级（i ≥ 1）的等效 σ ≈ `3·2^(i−1)`，即 [LEVEL1_SIGMA]。
 *
 * 第 0 级完全清晰，半径小于 1 texel 时直接使用；1 到第 1 级之间作线性过渡，
 * 该区间较窄，混合残留的清晰细节不明显。
 *
 * @param radiusInBaseTexels 以第 0 级 texel 计的模糊半径。
 * @param levelCount 参与选择的级别数，默认取当前金字塔的级数。
 */
fun SoftGlassBackdrop.levelPair(radiusInBaseTexels: Float, levelCount: Int = this.levelCount): LevelPair {
    if (levelCount <= 1) return LevelPair(0, 0, 0f)
    val last = levelCount - 1
    val r = radiusInBaseTexels.coerceAtLeast(0f)
    if (r <= 1f) return LevelPair(0, 0, 0f)
    if (r < LEVEL1_SIGMA) {
        // 第 0 级到第 1 级之间作线性过渡
        return LevelPair(0, 1, (r / LEVEL1_SIGMA).coerceIn(0f, 1f))
    }
    val lvl = (1f + kotlin.math.ln(r / LEVEL1_SIGMA) / kotlin.math.ln(2f))
        .coerceIn(0f, last.toFloat())
    val a = lvl.toInt().coerceIn(0, last)
    if (a >= last) return LevelPair(last, last, 0f)
    return LevelPair(a, a + 1, (lvl - a).coerceIn(0f, 1f))
}

// ── 坐标映射 ─────────────────────────────────────────────────────────

/**
 * 计算屏幕坐标到第 0 级纹理 texel 的仿射：`texPx = screenPx * (k.x, k.y) + (m.x, m.y)`。
 *
 * 与壁纸层的绘制变换互为逆运算：
 * ```
 * screen = (tex × fitScale + fitOffset − center) × s + center + offset
 * s = max(wallpaperScale, minScale)；minScale = cover/fit
 * ```
 * 结果只取决于变换参数与屏幕尺寸，与材质参数无关，因此纹理平移时只需重灌该 float4。
 *
 * @return `(kx, ky, mx, my)` 四个仿射系数。
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

/**
 * 把采样原点由变换后的窗口坐标还原到壁纸层的逻辑坐标。
 *
 * @param origin 变换后的窗口坐标。
 * @param scale 缩放系数；为 0 时按 1 处理。
 * @param translationY 纵向平移量。
 * @param pivotX 缩放中心 x。
 * @param pivotY 缩放中心 y。
 */
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

/** 等比缩放至最长边不超过 [maxEdge]；尺寸已满足时原样返回，不复制位图。 */
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

/** 2×2 盒式均值降采样，写出新的 ARGB 像素数组。 */
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

/** 三趟横竖交替的盒式模糊，结果就地写回；三趟累积的方差接近高斯。 */
private fun boxBlurTriple(pixels: IntArray, width: Int, height: Int, radius: Int) {
    if (radius <= 0) return
    val tmp = IntArray(pixels.size)
    repeat(3) {
        boxPass(pixels, tmp, width, height, radius, horizontal = true)
        boxPass(tmp, pixels, width, height, radius, horizontal = false)
    }
}

/**
 * 一趟盒式模糊，越界按边缘 clamp，窗口宽度恒为 `2r+1`。
 *
 * ARGB 打包在单个 Int 中，四个通道需分别滑动累加；直接对打包值求和会造成通道串扰。
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

/** 按 Rec.709 权重计算平均亮度；输入为最粗一级，像素量小，开销可忽略。 */
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
