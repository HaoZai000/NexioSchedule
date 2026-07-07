package com.haooz.chedule.data

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset

/**
 * 搭配数据模型：一个搭配对应一张壁纸及其偏移/缩放，以及完整快照预览
 */
data class Combination(
    val id: Long,
    var bitmap: Bitmap?,
    var offset: Offset,
    var scale: Float,
    var snapshot: Bitmap? = null,
    var cardBlurRadius: Float = 0f,
    var cardAlpha: Float = 0.15f,
    var cardHeight: Float = 54f,
    var cardCornerRadius: Float = 8f
)
