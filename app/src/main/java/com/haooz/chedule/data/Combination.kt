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
    var cardTextColor: CardTextColor = CardTextColor.COLORFUL,
    // 卡片文字缩放比例：作用于课程名称、教室、教师，默认 1.0（0.5~2.0）
    var cardTextScale: Float = 1f,
    var showClassroom: Boolean = true,
    var showTeacher: Boolean = true,
    // 卡片折射档位：关闭/较弱/默认/较强（需要壁纸才生效）
    var cardRefraction: CardRefractionLevel = CardRefractionLevel.DEFAULT,
    // 壁纸均匀测光结果：true=亮色壁纸，false=暗色壁纸，null=无壁纸/未测光
    var wallpaperIsLight: Boolean? = null,
    // 壁纸模糊开关：开启后对壁纸进行12dp模糊处理
    var wallpaperBlur: Boolean = false
)

/**
 * 搭配外观的持久化快照。
 *
 * 历史原因：外观参数是逐个版本加进来的，每加一个就多一个 SharedPreferences 键
 * （`comb_xxx_{搭配id}`），最终变成加载一个搭配要读 17 次 prefs、保存要写 16 次独立事务。
 * 这里把 17 个键收敛成单个 JSON，读写各一次；旧的分散键在首次读取时自动迁移。
 *
 * 注意：Gson 用 UnsafeAllocator 绕过构造器反序列化，Kotlin 默认值不生效，
 * 缺失字段会被置 null。所以枚举字段声明为可空并配 safe getter（同 [SpecialBlock.items] 的处理）。
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
    // 三态：null = 无壁纸/未测光，true = 亮色壁纸，false = 暗色壁纸
    val wallpaperIsLight: Boolean? = null,
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
}

/** 卡片文字颜色模式 */
enum class CardTextColor(val label: String) {
    COLORFUL("彩色"),
    SOLID("纯色");

    companion object {
        fun fromOrdinal(ordinal: Int): CardTextColor {
            return entries.getOrElse(ordinal) { COLORFUL }
        }
    }
}

/** 卡片折射档位：控制课程卡片玻璃对壁纸的透镜折射强度（需要壁纸才生效） */
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
