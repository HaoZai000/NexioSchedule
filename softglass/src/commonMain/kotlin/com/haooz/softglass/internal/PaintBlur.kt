package com.haooz.softglass.internal

import androidx.compose.ui.graphics.Paint

/** 给 [Paint] 挂模糊滤镜（0 表示不模糊），平台差异只在这里。 */
internal expect fun Paint.blur(radius: Float)
