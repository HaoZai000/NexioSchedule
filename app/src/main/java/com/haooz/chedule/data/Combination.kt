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
    var cardBlurRadius: Float = 4f,
    var cardAlpha: Float = 0.15f,
    var cardHeight: Float = 54f,
    var cardCornerRadius: Float = 10f,
    var wallpaperBrightness: Float = 0f,
    var showBreakDividers: Boolean = true,
    var cardContentAlignment: CardContentAlignment = CardContentAlignment.CENTER_CENTER,
    // 壁纸均匀测光结果：true=亮色壁纸，false=暗色壁纸，null=无壁纸/未测光
    var wallpaperIsLight: Boolean? = null
)
