package com.haooz.softglass.internal

import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.Paint

// Compose 1.11 废弃 asFrameworkPaint() 且无等价扩展（internalPaint 仅库内可见），
// 自持 android.graphics.Paint 要改整条渲染管线，这里保留原调用并压制警告。
@Suppress("DEPRECATION")
internal actual fun Paint.blur(radius: Float) {
    this.asFrameworkPaint().maskFilter =
        if (radius > 0f) BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL) else null
}
