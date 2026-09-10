package com.haooz.chedule.data

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import com.google.gson.Gson
import com.google.gson.JsonObject

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

    val safeCardTextScale: Float get() = if (cardTextScale > 0f) cardTextScale else 1f

    /**
     * 卡片每节高度（dp），带下限兜底。
     *
     * 这是最后一道保险：整张课表的网格高度由 `cardHeight × 总节数` 算出，
     * 一旦为 0，网格高度塌成 0、整页静默空白（不崩、无日志）——正是 v1.5.0 升级事故的最终表现。
     * 自定义页滑杆的取值区间是 34f..92f，0 从来不是一个有意义的取值，所以这里兜到默认值。
     */
    val safeCardHeight: Float get() = if (cardHeight > 0f) cardHeight else CARD_HEIGHT_DEFAULT

    companion object {
        /** 卡片每节高度的默认值，与自定义页滑杆一致 */
        const val CARD_HEIGHT_DEFAULT = 54f

        /**
         * 本快照在 JSON 里应当出现的字段名（只用于校验，不参与取值）。
         */
        private val FIELD_NAMES = setOf(
            "offsetX", "offsetY", "scale", "cardBlur", "cardAlpha", "cardHeight",
            "cardCornerRadius", "wallpaperBrightness", "wallpaperIsLight",
            "showBreakDividers", "cardContentAlignment", "cardTextColor",
            "cardTextScale", "showClassroom", "showTeacher", "cardRefraction",
            "wallpaperBlur"
        )

        /**
         * 严格解析搭配外观快照：只有 JSON 里**至少命中一个已知字段名**时才采信，否则返回 null。
         *
         * 为什么需要这道校验：v1.5.0 正式版（e51c159）的 `proguard-rules.pro` 漏了本类的 keep 规则，
         * R8 把类名连同全部字段名一起改掉了。它写进 `combination_style_<id>` 的 JSON 键名因此是
         * 混淆后的短名（形如 `{"a":54.0,...}`）；当前版本按真实字段名去读一个都匹配不上，
         * 而 Gson 又用 UnsafeAllocator 绕过构造器（Kotlin 默认值不生效），字段全部停在 Java 默认值：
         * `cardHeight=0` → 网格高度 0 → 课表页空白。
         *
         * 键名已经丢失、无法反推映射，所以这里如实判为"不可用快照"。调用方据此**丢弃并恢复默认外观**，
         * 同时覆写回正常格式，实现一次性自愈（反正那几个字段也读不出来，重置比留着一个坏快照好）。
         */
        fun parseSnapshotOrNull(gson: Gson, json: String): CombinationStyle? {
            val obj = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull() ?: return null
            if (obj.keySet().none { it in FIELD_NAMES }) return null
            return runCatching { gson.fromJson(json, CombinationStyle::class.java) }.getOrNull()
        }
    }
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
