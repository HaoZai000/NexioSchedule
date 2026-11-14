package com.kyant.backdrop.internal

import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.Paint
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asAndroidRuntimeShader

// Compose 1.11 废弃 asFrameworkPaint() 但本版本无等价扩展（internalPaint 仅库内可见）；
// 自行持有 android.graphics.Paint 需改动整条渲染管线，风险过大。保留原调用并压制警告，待正式替代。
@Suppress("DEPRECATION")
internal actual fun Paint.blur(radius: Float) {
    this.asFrameworkPaint().maskFilter =
        if (radius > 0f) BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        else null
}

@Suppress("DEPRECATION")
internal actual fun Paint.setRuntimeShader(runtimeShader: RuntimeShader?) {
    this.asFrameworkPaint().shader = runtimeShader?.asAndroidRuntimeShader()
}
