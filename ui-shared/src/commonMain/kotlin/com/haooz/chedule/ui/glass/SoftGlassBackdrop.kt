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
 * [refract] 是轻模糊（喂 `uRefractTex`，折射和内部都采它），
 * [big] 是重模糊（喂 `uBgColorTex`，描边和染色基色用）。
 *
 * 两张都**不做颜色处理** —— `processGlassColor` 在 shader 里逐像素算（原因见 `SoftGlassShader`）。
 * [meanLuma] 是背景平均亮度（0..1），给 [SoftGlassStyle.adapted] 选材质档位用。
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

    // 一次 4x 下采样，两个模糊共用（之前 small/big 各降采样一遍）
    val baseW = (width / 4).coerceAtLeast(1)
    val baseH = (height / 4).coerceAtLeast(1)
    val base = downsample4x(pixels, width, height, baseW, baseH)
    val quarterW = (baseW / 4).coerceAtLeast(1)
    val quarterH = (baseH / 4).coerceAtLeast(1)
    val quarter = downsample4x(base, baseW, baseH, quarterW, quarterH)

    val refract = blurBase(base, baseW, baseH, style.smallBlurRadius.value * density * bakeScale)
    val big = blurBase(base, baseW, baseH, style.bigBlurRadius.value * density * bakeScale)
    return SoftGlassBackdrop(
        refract = refract,
        big = big,
        meanLuma = meanLumaOf(quarter),
        // 纹理又经过 4x 下采样：采样比例必须用「纹理宽 / 屏幕宽」，不能用对齐阶段的 bakeScale
        bakeScale = refract.width.toFloat() / screenWidthPx.coerceAtLeast(1f),
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
 * 背景模糊：对齐引擎的 `mi_lm_downsample` + 对称模糊。
 *
 * - `mi_lm_downsample`（type 2）是 16 抽头，等价于对 4×4 取盒式均值；
 * - 之后在 1/4 分辨率上做三次盒式近似高斯（σ = 半径/3，换算到低分辨率），最后双线性放大回全分辨率。
 *
 * ⚠ 早先这里是固定 7 抽头（±3px）的高斯：σ 一旦大于 ~2，权重退化成近似均匀的盒式，
 * 但核宽永远只伸到 ±3px，模糊强度被硬顶住（`smallBlurRadius` 从 4dp 调到 32dp 画面不动）。
 * 现在盒半径由 σ 反解（`2r+1 = sqrt(4σ²+1)`），代价与半径无关。
 */
/** 在已降采样的图上做模糊；不动传入数组（两个模糊共用同一份 base）。 */
private fun blurBase(base: IntArray, width: Int, height: Int, radiusPx: Float): ImageBitmap {
    val work = base.copyOf()
    val sigma = (radiusPx / 4f) / 3f
    if (radiusPx > 0.5f && sigma >= 0.3f) gaussianApprox(work, width, height, sigma)
    // 不再双线性放大回全屏：低分辨率位图交给 shader 线性采样。
    return createImageBitmapFromArgb(work, width, height)
}

/** `mi_lm_downsample` type 2：4×4 盒式均值。 */
private fun downsample4x(
    src: IntArray,
    width: Int,
    height: Int,
    outWidth: Int,
    outHeight: Int,
): IntArray {
    val out = IntArray(outWidth * outHeight)
    for (y in 0 until outHeight) {
        for (x in 0 until outWidth) {
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (dy in 0 until 4) {
                val sy = (y * 4 + dy).coerceAtMost(height - 1)
                val row = sy * width
                for (dx in 0 until 4) {
                    val c = src[row + (x * 4 + dx).coerceAtMost(width - 1)]
                    sa += (c ushr 24) and 0xFF
                    sr += (c shr 16) and 0xFF
                    sg += (c shr 8) and 0xFF
                    sb += c and 0xFF
                }
            }
            out[y * outWidth + x] =
                ((sa / 16) shl 24) or ((sr / 16) shl 16) or ((sg / 16) shl 8) or (sb / 16)
        }
    }
    return out
}

/**
 * 三次盒式 ≈ 高斯。盒半径由 σ 反解：三次宽 `2r+1` 的盒式，方差 = `3·((2r+1)²-1)/12`，
 * 令它等于 σ² ⇒ `2r+1 = sqrt(4σ²+1)`。滑动窗口实现，代价与半径无关。
 */
private fun gaussianApprox(pixels: IntArray, width: Int, height: Int, sigma: Float) {
    val r = ((sqrt(4f * sigma * sigma + 1f) - 1f) * 0.5f).roundToInt().coerceAtLeast(1)
    val a = pixels
    val b = IntArray(pixels.size)
    repeat(3) {
        boxPass(a, b, width, height, r, horizontal = true)
        boxPass(b, a, width, height, r, horizontal = false)
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


