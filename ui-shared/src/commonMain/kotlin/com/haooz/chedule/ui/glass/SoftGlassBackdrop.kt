// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 玻璃的采样源：每页只做一次，页内所有玻璃面板共用。
 *
 * [refract] 轻模糊（喂 `uRefractTex`，折射与内部都采它），
 * [big] 重模糊（喂 `uBgColorTex`，只给描边用）。
 *
 * 两张都**不含颜色处理** —— 调色在烘焙阶段由 `applyGlassColorProcess` 做掉，shader 只采样。
 * [meanLuma] 是背景平均亮度（0..1），当前只喂 [SoftGlassStyle.adapted]，而它是恒等返回。
 */
data class SoftGlassBackdrop(
    val refract: ImageBitmap,
    val big: ImageBitmap,
    val meanLuma: Float,
    /** 位图相对屏幕的缩放；shader 用 uBakeScale 把面板 px 映射到纹理 px。 */
    val bakeScale: Float = 1f,
)

/**
 * 把壁纸按它**实际显示**的变换烘成半分辨率位图，再出轻 / 重两级模糊。
 *
 * 对齐公式来自共享壁纸层的实际绘制（`CourseScheduleAppMainContent`）：
 * ```
 * screen = (bitmap × fitScale + fitOffset − center) × s + center + offset
 * s = max(wallpaperScale, minScale)；minScale = cover/fit
 * ```
 *
 * 亮度滤镜（`wallpaperBrightness`）没跟 —— 玻璃色调与壁纸亮度差一档，属已知取舍。
 */
fun buildSoftGlassBackdrop(
    wallpaper: ImageBitmap,
    style: SoftGlassStyle,
    density: Float,
    scale: Float,
    offset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
): SoftGlassBackdrop {
    // 玻璃本来就是模糊的，位图按半分辨率烘焙：CPU 模糊/内存都降到 1/4，画质几乎无损。
    val bakeScale = 0.5f
    val aligned = alignWallpaper(
        wallpaper = wallpaper.asSoftwareBitmap(),
        scale = scale,
        offset = offset,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        bakeScale = bakeScale,
    )
    val width = aligned.width
    val height = aligned.height
    val pixels = aligned.toPixelMap().buffer

    // 采样源降采样：aligned 是 1/2 屏，再 /2 = **1/4 屏**（1 texel = 4px）。
    // 不要再降到 1/8 屏：texel 变 8px 后，模糊半径小于 8px 时纹理精度不够，滑杆低段整段没反应。
    val baseW = (width / 2).coerceAtLeast(1)
    val baseH = (height / 2).coerceAtLeast(1)
    val base = downsample4x(pixels, width, height, baseW, baseH)
    val quarterW = (baseW / 4).coerceAtLeast(1)
    val quarterH = (baseH / 4).coerceAtLeast(1)
    val quarter = downsample4x(base, baseW, baseH, quarterW, quarterH)

    val refractPx = blurBase(base, baseW, baseH, style.smallBlurRadius * bakeScale)
    val bigPx = blurBase(base, baseW, baseH, style.bigBlurRadius * bakeScale)
    // 颜色处理放在这里（系统 `applyGlassColorProcess` 同位置）：跑在低分辨率模糊图上、上采样之前，
    // shader 采样到的直接是「已调色」的图，避免「先插值后调色」与系统顺序不符。
    val styled = applyGlassColorProcess(refractPx, bigPx, style)
    return SoftGlassBackdrop(
        refract = createImageBitmapFromArgb(styled, baseW, baseH),
        big = createImageBitmapFromArgb(bigPx, baseW, baseH),
        meanLuma = meanLumaOf(quarter),
        // 纹理又经过 4x 下采样：采样比例必须用「纹理宽 / 屏幕宽」，不能用对齐阶段的 bakeScale
        bakeScale = baseW.toFloat() / screenWidthPx.coerceAtLeast(1f),
    )
}

/** 壁纸显示变换 → 屏幕尺寸位图。纯 Compose Canvas，四端同一份。 */
private fun alignWallpaper(
    wallpaper: ImageBitmap,
    scale: Float,
    offset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
    bakeScale: Float,
): ImageBitmap {
    val w = screenWidthPx.toInt().coerceAtLeast(1)
    val h = screenHeightPx.toInt().coerceAtLeast(1)
    val targetW = (w * bakeScale).toInt().coerceAtLeast(1)
    val targetH = (h * bakeScale).toInt().coerceAtLeast(1)
    val screen = ImageBitmap(targetW, targetH, ImageBitmapConfig.Argb8888)
    val canvas = Canvas(screen)
    canvas.scale(bakeScale, bakeScale)

    val fitScale = min(w / wallpaper.width.toFloat(), h / wallpaper.height.toFloat())
    val fitOffX = (w - wallpaper.width * fitScale) / 2f
    val fitOffY = (h - wallpaper.height * fitScale) / 2f
    val cover = max(w / wallpaper.width.toFloat(), h / wallpaper.height.toFloat())
    val minScale = if (fitScale > 0f) cover / fitScale else 1f
    val s = max(scale, minScale)

    val centerX = w / 2f
    val centerY = h / 2f
    canvas.save()
    canvas.translate(offset.x, offset.y)
    canvas.translate(centerX, centerY)
    canvas.scale(s, s)
    canvas.translate(-centerX, -centerY)
    canvas.translate(fitOffX, fitOffY)
    canvas.scale(fitScale, fitScale)
    canvas.drawImage(wallpaper, Offset.Zero, Paint())
    canvas.restore()
    return screen
}

/** 平均亮度（Rec.709 权重）。传进来的应该是 1/4 下采样图。 */
private fun meanLumaOf(pixels: IntArray): Float {
    if (pixels.isEmpty()) return 0.5f
    var sum = 0f
    for (c in pixels) {
        sum += (0.2126f * ((c shr 16) and 0xFF) +
            0.7152f * ((c shr 8) and 0xFF) +
            0.0722f * (c and 0xFF)) / 255f
    }
    return sum / pixels.size
}

/**
 * 颜色处理，逐行等价实现 `libhwui.so` 的 `processGlassColor`，对应系统 `MiBlurBlendUtils`
 * 在 blur stage、低分辨率图、上采样之前跑的那一步：
 * darker(往紫黑常量混) → luminanceCurve → adjustColor → glassColor/gcr → innerMix。
 *
 * 必须在烘焙阶段做，不能挪进 shader：这几步乘法都是非线性的，系统「先调色后上采样」，
 * 反过来会插值出不同的过渡带与折射边缘。
 */
private fun applyGlassColorProcess(
    refract: IntArray,
    big: IntArray,
    style: SoftGlassStyle,
): IntArray {
    val n = refract.size
    val out = IntArray(n)
    // uDarker 混向的常量，硬编码在 libhwui.so
    val darkR = 0.07874f
    val darkG = 0.02848f
    val darkB = 0.09278f
    val darker = style.darker
    val dLo = style.darkerRange.first
    val dHi = style.darkerRange.second
    val lc = style.luminanceValues
    val c0 = lc.getOrElse(0) { 0f }
    val c1 = lc.getOrElse(1) { 0f }
    val c2 = lc.getOrElse(2) { 0f }
    val c3 = lc.getOrElse(3) { 1f }
    val amount = style.luminanceAmount
    val sat = style.saturation
    val bri = style.brightness
    val bgSat = style.bgColorSaturation
    val bgBri = style.bgColorBrightness
    val white = style.innerColorWhite
    val burn = style.burn
    val glassR = style.glassColor.red
    val glassG = style.glassColor.green
    val glassB = style.glassColor.blue
    val glassMix = style.glassColorMix
    val innerMix = style.innerColorMix

    for (i in 0 until n) {
        val cp = refract[i]
        var r = ((cp shr 16) and 0xFF) / 255f
        var g = ((cp shr 8) and 0xFF) / 255f
        var b = (cp and 0xFF) / 255f
        val bp = big[i]
        var br = ((bp shr 16) and 0xFF) / 255f
        var bgc = ((bp shr 8) and 0xFF) / 255f
        var bb = (bp and 0xFF) / 255f

        // ── darker：往深紫黑混合，越亮混得越多 ──
        val kc = mixF(0f, darker, smoothstep(dLo, dHi, luma(r, g, b)))
        r += (darkR - r) * kc
        g += (darkG - g) * kc
        b += (darkB - b) * kc
        val kb = mixF(0f, darker, smoothstep(dLo, dHi, luma(br, bgc, bb)))
        br += (darkR - br) * kb
        bgc += (darkG - bgc) * kb
        bb += (darkB - bb) * kb

        // ── luminanceCurve（比例模式，位图 alpha = 1）──
        val l = clamp01(luma(r, g, b))
        val adj = clamp01(((c0 * l + c1) * l + c2) * l + c3)
        val s = adj / maxOf(l, 0.01f) * smoothstep(0f, 0.1f, l)
        val mCurve = mixF(1f, s, amount)
        r *= mCurve
        g *= mCurve
        b *= mCurve

        // ── adjustColor(col, saturation, brightness) ──
        val l1 = 0.2125f * r + 0.7153f * g + 0.0721f * b
        r = mixF(l1, r, sat) + bri
        g = mixF(l1, g, sat) + bri
        b = mixF(l1, b, sat) + bri

        // ── glassColor：adjustColor(bigCol, bgSat, bgBri) → 往白抬 ──
        val lb = 0.2125f * br + 0.7153f * bgc + 0.0721f * bb
        var gr = mixF(lb, br, bgSat) + bgBri
        var gg = mixF(lb, bgc, bgSat) + bgBri
        var gb = mixF(lb, bb, bgSat) + bgBri
        gr = mixF(gr, 1f, white)
        gg = mixF(gg, 1f, white)
        gb = mixF(gb, 1f, white)

        // ── gcr ──
        val lumin = clamp01(luma(gr, gg, gb))
        val tBurn = (if (burn <= 0f) 1f else powF(lumin, burn)) - 0.5f
        val ratio = 0.8f * mixF(lumin, 1f, 1.587f * tBurn * tBurn * tBurn + 0.5f)
        var gcrR = mixF(1f, gr, ratio)
        var gcrG = mixF(1f, gg, ratio)
        var gcrB = mixF(1f, gb, ratio)
        val m = (gcrR + gcrG + gcrB) / 3f
        val dx = gr - m
        val dy = gg - m
        val dz = gb - m
        val gmask = smoothstep(0f, 0.4f, sqrt(dx * dx + dy * dy + dz * dz))
        gcrR = mixF(m, gcrR, gmask)
        gcrG = mixF(m, gcrG, gmask)
        gcrB = mixF(m, gcrB, gmask)
        gcrR = mixF(gcrR, glassR, glassMix)
        gcrG = mixF(gcrG, glassG, glassMix)
        gcrB = mixF(gcrB, glassB, glassMix)

        // ── 合成 ──
        val im = innerMix * ratio
        r = mixF(r * gcrR, gcrR, im)
        g = mixF(g * gcrG, gcrG, im)
        b = mixF(b * gcrB, gcrB, im)

        out[i] = (0xFF shl 24) or
            ((r * 255f).toInt().coerceIn(0, 255) shl 16) or
            ((g * 255f).toInt().coerceIn(0, 255) shl 8) or
            (b * 255f).toInt().coerceIn(0, 255)
    }
    return out
}

private fun luma(r: Float, g: Float, b: Float): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b

private fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

private fun mixF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = clamp01((x - edge0) / (edge1 - edge0))
    return t * t * (3f - 2f * t)
}

private fun powF(base: Float, exp: Float): Float {
    if (base <= 0f) return 0f
    return kotlin.math.exp(exp * kotlin.math.ln(base))
}

/**
 * 背景模糊：对齐引擎的 `mi_lm_downsample` + 对称模糊。在传入数组的副本上算，不动 base
 * （轻 / 重两档共用同一份），返回 ARGB 像素，放大交给 shader 线性采样。
 *
 * - `mi_lm_downsample`（type 2）是 16 抽头，等价于对 4×4 取盒式均值；
 * - 之后在低分辨率上做三次盒式近似高斯（σ = 半径/3 再换算到该分辨率）。
 *
 * 盒半径必须由 σ 反解（`2r+1 = sqrt(4σ²+1)`）：固定核宽（如 ±3px 的 7 抽头）会把模糊强度
 * 硬顶住，`smallBlurRadius` 从 4dp 调到 32dp 画面不动。滑动窗口实现，代价与半径无关。
 */
private fun blurBase(base: IntArray, width: Int, height: Int, radiusPx: Float): IntArray {
    val work = base.copyOf()
    // base 是 1/4 屏（aligned 的 1/2），所以 texel = 2 个 aligned px = 4 个屏幕 px
    val sigma = (radiusPx / 2f) / 3f
    if (radiusPx > 0.5f && sigma >= 0.2f) gaussianApprox(work, width, height, sigma)
    // 不在这里放大回全屏：低分辨率位图交给 shader 线性采样。
    return work
}

/** `mi_lm_downsample` type 2：4×4 盒式均值。 */
private fun downsample4x(
    src: IntArray,
    width: Int,
    height: Int,
    outWidth: Int,
    outHeight: Int,
): IntArray {
    // 采样步长由实际缩放比决定（支持 2x / 4x），不是写死 4
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
                ((sa / count) shl 24) or ((sr / count) shl 16) or ((sg / count) shl 8) or (sb / count)
        }
    }
    return out
}

/**
 * 三次盒式 ≈ 高斯。盒半径由 σ 反解：三次宽 `2r+1` 的盒式，方差 = `3·((2r+1)²-1)/12`，
 * 令它等于 σ² ⇒ `2r+1 = sqrt(4σ²+1)`。滑动窗口实现，代价与半径无关。
 */
private fun gaussianApprox(pixels: IntArray, width: Int, height: Int, sigma: Float) {
    // 三次盒式的方差 = 3·((2r+1)²−1)/12。整数 r 的方差跳变很大（r=1→2 相当于 σ 从 0.87 跳到
    // 2.45 texel），直接四舍五入会让滑杆低段没反应；改成按**方差**在相邻两档间插值，跑两遍再混合。
    fun variance(r: Int): Float {
        val c = (2 * r + 1).toFloat()
        return 3f * (c * c - 1f) / 12f
    }
    val target = sigma * sigma
    var r = 0
    while (r < 64 && variance(r + 1) <= target) r++
    val v0 = variance(r)
    val v1 = variance(r + 1)
    val t = if (v1 > v0) ((target - v0) / (v1 - v0)).coerceIn(0f, 1f) else 0f

    if (t < 0.02f) {
        boxTriple(pixels, width, height, r)
        return
    }
    val lo = pixels.copyOf()
    boxTriple(lo, width, height, r)
    val hi = pixels.copyOf()
    boxTriple(hi, width, height, r + 1)
    for (i in pixels.indices) {
        val a = lo[i]
        val b = hi[i]
        val ch = { x: Int, shift: Int -> (x shr shift) and 0xFF }
        val ra = (ch(a, 16) + ((ch(b, 16) - ch(a, 16)) * t).toInt())
        val ga = (ch(a, 8) + ((ch(b, 8) - ch(a, 8)) * t).toInt())
        val ba = (ch(a, 0) + ((ch(b, 0) - ch(a, 0)) * t).toInt())
        val aa = (ch(a, 24) + ((ch(b, 24) - ch(a, 24)) * t).toInt())
        pixels[i] = (aa shl 24) or (ra shl 16) or (ga shl 8) or ba
    }
}

/** 三趟盒式模糊（横竖交替），就地写回 [pixels]。 */
private fun boxTriple(pixels: IntArray, width: Int, height: Int, radius: Int) {
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


