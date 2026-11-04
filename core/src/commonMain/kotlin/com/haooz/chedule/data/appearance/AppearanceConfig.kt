package com.haooz.chedule.data.appearance

import com.haooz.chedule.data.platform.DisplayLang

/**
 * 课表外观配置：全部可调参数收敛为不可变值对象
 */
data class AppearanceConfig(
    val cardBlurRadius: Float = 4f,
    /** 课程色着色程度（有无壁纸都可调） */
    val cardAlpha: Float = 0.15f,
    /** 有壁纸时卡片白/黑底不透明度 */
    val cardSurfaceAlpha: Float = 0.12f,
    val cardHeight: Float = 54f,
    val cardCornerRadius: Float = 10f,
    val wallpaperBrightness: Float = 0f,
    val showBreakDividers: Boolean = true,
    val cardContentAlignment: CardContentAlignment = CardContentAlignment.CENTER_CENTER,
    val cardTextColor: CardTextColor = CardTextColor.COLORFUL,
    /** 卡片文字缩放比例：作用于课程名称、教室、教师，0.5~2.0 */
    val cardTextScale: Float = 1f,
    val showClassroom: Boolean = true,
    val showTeacher: Boolean = true,
    val cardRefraction: CardRefractionLevel = CardRefractionLevel.DEFAULT,
    val wallpaperBlur: Boolean = false
) {
    companion object
}

/**
 * [AppearanceStyle]（落盘快照）→ 外观配置。
 *
 * 用于「搭配还没加载完」的首帧兜底：与 `AppearanceConfig.fromCombination` 字段一一对应，
 * 保证兜底值与加载完成后派生的值相同，不会闪一帧默认值。
 */
fun AppearanceConfig.Companion.fromStyle(style: AppearanceStyle): AppearanceConfig = AppearanceConfig(
    cardBlurRadius = style.cardBlur,
    cardAlpha = style.cardAlpha.coerceIn(0f, 1f),
    cardSurfaceAlpha = style.safeCardSurfaceAlpha,
    cardHeight = style.safeCardHeight,
    cardCornerRadius = style.cardCornerRadius,
    wallpaperBrightness = style.wallpaperBrightness,
    showBreakDividers = style.showBreakDividers,
    cardContentAlignment = style.safeAlignment,
    cardTextColor = style.safeTextColor,
    cardTextScale = style.safeCardTextScale,
    showClassroom = style.showClassroom,
    showTeacher = style.showTeacher,
    cardRefraction = style.safeRefraction,
    wallpaperBlur = style.wallpaperBlur,
)

/**
 * 外观存储的偏好文件名与键名（数据兼容契约的一部分）。
 * `:core` 原本反向依赖 `:app` 的 `ScheduleAppearance` 类型来判断外观键，这里只把两个字符串常量下沉到 `:core`，由 `:app` 用 `const val` 转发，两边取值同源。
 * ⚠ 值**逐字未变**：改了等于老用户的外观设置读不出来。
 */
object AppearancePrefs {
    /** 外观独立 prefs 文件名（`ScheduleAppearance.FILE`） */
    const val FILE = "appearance_settings"

    /** 唯一键：`CombinationStyle` 的 JSON 快照（`ScheduleAppearance.FILE_STYLE_KEY`） */
    const val FILE_STYLE_KEY = "appearance_style"
}

/**
 * 外观参数快照（跨平台）
 * 存量用户 JSON 快照键名=字段名，一个字符都不能改（改名/删除=老用户外观全部读不出来）；
 * 可新增字段（解析器忽略未知键），落盘格式须保持原样。
 */
data class AppearanceStyle(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    /** offset/scale 保存时的屏幕宽高（px）；0=未记录，加载时不重映射 */
    val offsetRefW: Float = 0f,
    val offsetRefH: Float = 0f,
    /** 课程卡片模糊半径（dp） */
    val cardBlur: Float = 4f,
    val cardAlpha: Float = 0.15f,
    /** 有壁纸时白/黑底不透明度；null=未设置，用默认 */
    val cardSurfaceAlpha: Float? = null,
    val cardHeight: Float = 54f,
    val cardCornerRadius: Float = 10f,
    val wallpaperBrightness: Float = 0f,
    /** null=无壁纸/未测光 */
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

    /** 0 一律视为损坏恢复默认（滑杆合法区间 34~92，0 会让网格整页静默空白） */
    val safeCardTextScale: Float get() = if (cardTextScale > 0f) cardTextScale else 1f

    val safeCardSurfaceAlpha: Float
        get() = cardSurfaceAlpha?.takeIf { it in 0f..1f }
            ?: CombinationDefaults.CARD_SURFACE_ALPHA_DEFAULT

    val safeCardHeight: Float
        get() = if (cardHeight > 0f) cardHeight else CombinationDefaults.CARD_HEIGHT_DEFAULT

    companion object {
        /** 默认值集中一处，三处（本类 / 落地 JSON / UI 滑杆）必须一致 */
        fun defaults(): AppearanceStyle = AppearanceStyle()
    }
}

/** 外观默认值 —— 与滑杆默认值、JSON 缺省值保持一致。 */
object CombinationDefaults {
    const val CARD_HEIGHT_DEFAULT = 54f
    const val CARD_SURFACE_ALPHA_DEFAULT = 0.12f
}

// `ThemeMode` 独立成文件：只含纯数据，供 `:ui-shared` 页面引用
// （`ScheduleAppearance` 的读写函数要碰 SharedPreferences，仍留在宿主）。

/**
 * 课表外观自定义页「默认主题」下拉的取值。
 *
 * ⚠️ 与全局应用主题（`app_theme_prefs` 的 `theme_mode`，值域 system/light/dark）
 * **刻意隔离**：这里只决定今日页/课程表页跟随谁，全局主题开关不受影响。
 * 故 [FOLLOW_APP] 的 prefsValue 复用 "system" 字面量但落在不同的键上。
 *
 * ⚠ 落盘走 [prefsValue]（数据键，勿本地化）；[label] 是纯展示名，随应用语言（见 [DisplayLang]）。
 */
enum class ThemeMode(val prefsValue: String) {
    FOLLOW_WALLPAPER("follow_wallpaper"),
    FOLLOW_APP("system"),
    LIGHT("light"),
    DARK("dark");

    val label: String
        get() = when (this) {
            FOLLOW_WALLPAPER -> if (DisplayLang.isZh) "跟随壁纸" else "Follow Wallpaper"
            FOLLOW_APP -> if (DisplayLang.isZh) "跟随应用" else "Follow App"
            LIGHT -> if (DisplayLang.isZh) "浅色模式" else "Light Mode"
            DARK -> if (DisplayLang.isZh) "深色模式" else "Dark Mode"
        }

    companion object {
        /** 课表外观"默认主题"下拉专用的偏好 key，仅决定今日页/课程表页主题，与全局主题开关隔离 */
        const val SCHEDULE_THEME_MODE_KEY = "schedule_theme_mode"

        fun fromPrefsValue(value: String?): ThemeMode =
            entries.find { it.prefsValue == value } ?: FOLLOW_WALLPAPER
    }
}

/**
 * 卡片外观的三个纯枚举：CourseCard（`:ui-shared`）用它们做布局/配色分流，零依赖。
 *
 * ⚠ 落盘格式 = ordinal（fromOrdinal 是旧数据的恢复入口），**枚举顺序不能改**。
 * [label] 是纯展示名（随应用语言，见 [DisplayLang]），不参与落盘与比较。
 */

/** 卡片内容对齐方式 */
enum class CardContentAlignment {
    TOP_START,
    TOP_CENTER,
    CENTER_START,
    CENTER_CENTER;

    val label: String
        get() = when (this) {
            TOP_START -> if (DisplayLang.isZh) "顶部居左" else "Top left"
            TOP_CENTER -> if (DisplayLang.isZh) "顶部居中" else "Top center"
            CENTER_START -> if (DisplayLang.isZh) "中间居左" else "Middle left"
            CENTER_CENTER -> if (DisplayLang.isZh) "中间居中" else "Middle center"
        }

    companion object {
        fun fromOrdinal(ordinal: Int): CardContentAlignment =
            entries.getOrElse(ordinal) { CENTER_CENTER }
    }
}

/** 卡片文字配色 */
enum class CardTextColor {
    COLORFUL,
    SOLID;

    val label: String
        get() = when (this) {
            COLORFUL -> if (DisplayLang.isZh) "彩色" else "Colorful"
            SOLID -> if (DisplayLang.isZh) "纯色" else "Solid"
        }

    companion object {
        fun fromOrdinal(ordinal: Int): CardTextColor =
            entries.getOrElse(ordinal) { COLORFUL }
    }
}

/** 卡片玻璃对壁纸的透镜折射强度（需壁纸才生效） */
enum class CardRefractionLevel(val lensRadiusDp: Float, val lensStrengthDp: Float) {
    OFF(0f, 0f),
    WEAK(4f, 16f),
    DEFAULT(5f, 25f),
    STRONG(6f, 36f);

    val label: String
        get() = when (this) {
            OFF -> if (DisplayLang.isZh) "关闭" else "Off"
            WEAK -> if (DisplayLang.isZh) "较弱" else "Weak"
            DEFAULT -> if (DisplayLang.isZh) "默认" else "Default"
            STRONG -> if (DisplayLang.isZh) "较强" else "Strong"
        }

    companion object {
        fun fromOrdinal(ordinal: Int): CardRefractionLevel =
            entries.getOrElse(ordinal) { DEFAULT }
    }
}
