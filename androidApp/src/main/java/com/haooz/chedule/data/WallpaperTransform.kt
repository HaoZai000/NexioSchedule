package com.haooz.chedule.data

import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

/**
 * 壁纸变换（offset/scale）与屏幕方向解耦。
 *
 * scale 相对 ContentScale.Fit：fit=min(sw/bw, sh/bh)，绘制再乘 scale。
 * 旋转后 fit 会变，同一份像素 offset/scale 不能直接复用，否则画面错位。
 * 这里用图像→屏幕系数 k=fit*scale 作为不变量做重映射。
 */
object WallpaperTransform {

    fun fitScale(bitmapW: Int, bitmapH: Int, screenW: Float, screenH: Float): Float {
        if (bitmapW <= 0 || bitmapH <= 0 || screenW <= 0f || screenH <= 0f) return 1f
        return min(screenW / bitmapW, screenH / bitmapH)
    }

    fun coverScale(bitmapW: Int, bitmapH: Int, screenW: Float, screenH: Float): Float {
        if (bitmapW <= 0 || bitmapH <= 0 || screenW <= 0f || screenH <= 0f) return 1f
        return max(screenW / bitmapW, screenH / bitmapH)
    }

    /** cover-fill 最小缩放（相对 Fit），保证壁纸填满屏幕 */
    fun minScale(bitmapW: Int, bitmapH: Int, screenW: Float, screenH: Float): Float {
        val fit = fitScale(bitmapW, bitmapH, screenW, screenH)
        if (fit <= 0f) return 1f
        val cover = coverScale(bitmapW, bitmapH, screenW, screenH)
        return cover / fit
    }

    /**
     * 把 offset/scale 从 fromW×fromH 屏幕映射到 toW×toH。
     * 保持 k=fit*scale，画面中心与图像视野缩放稳定；旋转一圈应能回到原观感。
     */
    fun remap(
        offset: Offset,
        scale: Float,
        bitmapW: Int,
        bitmapH: Int,
        fromW: Float,
        fromH: Float,
        toW: Float,
        toH: Float,
    ): Pair<Offset, Float> {
        if (bitmapW <= 0 || bitmapH <= 0) return offset to scale
        if (fromW <= 0f || fromH <= 0f || toW <= 0f || toH <= 0f) return offset to scale
        if (fromW == toW && fromH == toH) return offset to scale
        val fromFit = fitScale(bitmapW, bitmapH, fromW, fromH)
        val toFit = fitScale(bitmapW, bitmapH, toW, toH)
        if (fromFit <= 0f || toFit <= 0f) return offset to scale
        val fromK = fromFit * scale
        if (fromK <= 0f) return offset to scale
        val toScale = fromK / toFit
        val toK = toFit * toScale
        val ratio = toK / fromK
        return Offset(offset.x * ratio, offset.y * ratio) to toScale
    }
}
