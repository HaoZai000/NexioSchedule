package com.haooz.chedule.data

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import com.google.gson.Gson
import com.google.gson.JsonObject

/** 一个搭配：壁纸 + 偏移/缩放 + 完整快照预览 */
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
    var cardTextColor: CardTextColor = CardTextColor.COLORFUL,
    var cardTextScale: Float = 1f, // 0.5~2.0，作用于课程名/教室/教师
    var showClassroom: Boolean = true,
    var showTeacher: Boolean = true,
    var cardRefraction: CardRefractionLevel = CardRefractionLevel.DEFAULT,
    // null=无壁纸/未测光；true=亮色；false=暗色
    var wallpaperIsLight: Boolean? = null,
    var wallpaperBlur: Boolean = false
)

/**
 * 外观持久化快照。历史原因参数曾拆成 17 个 SharedPreferences 键（读写各十余次事务），
 * 现收敛为单 JSON；旧分散键首次读取时自动迁移。
 *
 * 枚举字段声明为可空并配 safe getter：Gson UnsafeAllocator 使 Kotlin 默认值不生效。
 */
data class CombinationStyle(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val cardBlur: Float = 0f,
    val cardAlpha: Float = 0.15f,
    val cardHeight: Float = 54f,
    val cardCornerRadius: Float = 8f,
    val wallpaperBrightness: Float = 0f,
    val wallpaperIsLight: Boolean? = null, // null=无壁纸/未测光
    val showBreakDividers: Boolean = true,
    val cardContentAlignment: CardContentAlignment? = null,
    val cardTextColor: CardTextColor? = null,
    val cardTextScale: Float = 1f,
    val showClassroom: Boolean = true,
    val showTeacher: Boolean = true,
    val cardRefraction: CardRefractionLevel? = null,
    val wallpaperBlur: Boolean = false
) {
    val safeAlignment: CardContentAlignment get() = cardContentAlignment ?: CardContentAlignment.CENTER_CENTER
    val safeTextColor: CardTextColor get() = cardTextColor ?: CardTextColor.COLORFUL
    val safeRefraction: CardRefractionLevel get() = cardRefraction ?: CardRefractionLevel.DEFAULT

    val safeCardTextScale: Float get() = if (cardTextScale > 0f) cardTextScale else 1f

    /**
     * 为 0 时网格高度会整页静默空白（不崩、无日志）；滑杆合法区间 34~92，0 一律视为损坏恢复默认。
     */
    val safeCardHeight: Float get() = if (cardHeight > 0f) cardHeight else CARD_HEIGHT_DEFAULT

    companion object {
        /** 与自定义页滑杆默认值一致 */
        const val CARD_HEIGHT_DEFAULT = 54f

        /** 仅用于校验快照键名，不参与取值 */
        private val FIELD_NAMES = setOf(
            "offsetX", "offsetY", "scale", "cardBlur", "cardAlpha", "cardHeight",
            "cardCornerRadius", "wallpaperBrightness", "wallpaperIsLight",
            "showBreakDividers", "cardContentAlignment", "cardTextColor",
            "cardTextScale", "showClassroom", "showTeacher", "cardRefraction",
            "wallpaperBlur"
        )

        /**
         * 至少命中一个已知字段名才采信。键名对不上时 Gson 会把字段停在 Java 默认值
         * （如 cardHeight=0 → 课表页空白），故判为不可用，由调用方丢弃并恢复默认。
         */
        fun parseSnapshotOrNull(gson: Gson, json: String): CombinationStyle? {
            val obj = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull() ?: return null
            if (obj.keySet().none { it in FIELD_NAMES }) return null
            return runCatching { gson.fromJson(json, CombinationStyle::class.java) }.getOrNull()
        }
    }
}

enum class CardTextColor(val label: String) {
    COLORFUL("彩色"),
    SOLID("纯色");

    companion object {
        fun fromOrdinal(ordinal: Int): CardTextColor {
            return entries.getOrElse(ordinal) { COLORFUL }
        }
    }
}

/** 控制课程卡片玻璃对壁纸的透镜折射强度（需要壁纸才生效） */
enum class CardRefractionLevel(val label: String, val lensRadiusDp: Float, val lensStrengthDp: Float) {
    OFF("关闭", 0f, 0f),
    WEAK("较弱", 5f, 9f),
    DEFAULT("默认", 6f, 14f),
    STRONG("较强", 8f, 22f);

    companion object {
        fun fromOrdinal(ordinal: Int): CardRefractionLevel {
            return entries.getOrElse(ordinal) { DEFAULT }
        }
    }
}
