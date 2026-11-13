package com.haooz.softglass.internal

import androidx.compose.ui.graphics.Paint
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

/** 与桌面/iOS 一致：模糊走 Skia MaskFilter；`asFrameworkPaint()` 在本 klib 公开。 */
internal actual fun Paint.blur(radius: Float) {
    this.asFrameworkPaint().maskFilter =
        if (radius > 0f) MaskFilter.makeBlur(FilterBlurMode.NORMAL, radius) else null
}
