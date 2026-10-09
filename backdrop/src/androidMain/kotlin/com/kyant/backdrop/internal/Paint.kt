package com.kyant.backdrop.internal

import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.Paint
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asAndroidRuntimeShader

// Compose 1.11 里 asFrameworkPaint() 被标记废弃，建议改用「平台专属扩展」；
// 但那个扩展在本版本尚未提供（AndroidPaint 的 internalPaint 是库内部可见），
// 唯一等价的绕法是自己持有 android.graphics.Paint，那要改动整条渲染管线的数据流，
// 风险远大于收益。这里保留原调用并压制警告，等 Compose 提供正式替代再改。
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
