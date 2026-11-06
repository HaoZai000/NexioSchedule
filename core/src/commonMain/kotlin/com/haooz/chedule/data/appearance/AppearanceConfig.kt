package com.haooz.chedule.data.appearance

import com.haooz.chedule.data.platform.DisplayLang

/**
 * 课表外观配置：全部可调参数收敛为不可变值对象
 */
data class AppearanceConfig(
    /**
     * 卡片模糊半径，**单位 px**（不是 dp），语义就是高斯 σ。
     *
     * 直接喂柔光玻璃的 `smallBlurRadius`，**不做任何换算**。上限见 [CARD_BLUR_MAX_PX]。
     * 出厂默认 12px；与 [AppearanceStyle.cardBlur]、滑杆刻度、JSON 缺省值四处一致。
     */
    val cardBlurRadius: Float = 12f,
    /** 课程色着色程度（有无壁纸都可调） */
    val cardAlpha: Float = 0.15f,
    /**
     * 有壁纸时卡片白/黑底不透明度，即**基准值**。
     *
     * 分发时深色模式先 ×0.8（默认 40% ⇒ 32%），再按页面加减：
     * 今日页高亮卡 +0.15、课表页 −0.15。
     */
    val cardSurfaceAlpha: Float = 0.40f,
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
    /** 玻璃边缘厚度档位（乘在 `SoftGlassStyle.edgeWidth` 上，不改文件里的默认值） */
    val cardEdgeThickness: CardEdgeThickness = CardEdgeThickness.DEFAULT,
    /** 卡片玻璃的高光描边（倒角方向光）；课表页与今日页共用，出厂默认关 */
    val cardRim: Boolean = false,
    val wallpaperBlur: Boolean = false
) {
    companion object
}

/**
 * 设置值 → 基准不透明度。**深色模式先 ×0.8**，之后才套各页面的 ±15pp。
 *
 * 顺序不能反：先加偏移再乘系数，深色下课表页会掉到 0.30×0.8 = 24%，
 * 而正确值是 (0.40×0.8) − 0.15 = 17%。
 */
fun AppearanceConfig.baseSurfaceAlpha(isDark: Boolean): Float =
    (cardSurfaceAlpha * if (isDark) CombinationDefaults.DARK_SURFACE_ALPHA_FACTOR else 1f)
        .coerceIn(0f, 1f)

/**
 * 课表页专用不透明度：基准 −15pp（默认亮色 40% ⇒ 25%，深色 32% ⇒ 17%）。
 *
 * 课程卡、分界带、时间轴卡、拖拽/交换浮层**全部**走这一个入口，
 * 少一处就会出现「拖起来的一瞬间卡片变亮」。
 */
fun AppearanceConfig.scheduleSurfaceAlpha(isDark: Boolean): Float =
    (baseSurfaceAlpha(isDark) + CombinationDefaults.SCHEDULE_SURFACE_ALPHA_OFFSET).coerceIn(0f, 1f)

/** 今日页高亮卡：基准 +15pp（默认亮色 40% ⇒ 55%，深色 32% ⇒ 47%）。 */
fun AppearanceConfig.highlightSurfaceAlpha(isDark: Boolean): Float =
    (baseSurfaceAlpha(isDark) + CombinationDefaults.HIGHLIGHT_SURFACE_ALPHA_BONUS).coerceIn(0f, 1f)

/**
 * [AppearanceStyle]（落盘快照）→ 外观配置。
 *
 * 用于「搭配还没加载完」的首帧兜底：与 `AppearanceConfig.fromCombination` 字段一一对应，
 * 保证兜底值与加载完成后派生的值相同，不会闪一帧默认值。
 */
fun AppearanceConfig.Companion.fromStyle(style: AppearanceStyle): AppearanceConfig = AppearanceConfig(
    cardBlurRadius = coerceBlurPx(style.cardBlur),
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
    cardEdgeThickness = style.safeEdgeThickness,
    cardRim = style.cardRim,
    wallpaperBlur = style.wallpaperBlur,
)

/**
 * 外观存储的偏好文件名与键名（数据兼容契约）。
 *
 * `:core` 不能依赖 `:app`，所以两个字符串常量下沉到这里，`:app` 用 `const val` 转发，取值同源。
 * ⚠ 值**逐字未变**：改了等于老用户的外观设置全部读不出来。
 */
/**
 * 卡片模糊滑杆的上限（px，等于 σ）。
 *
 * 滑杆值就是 σ，没有任何隐藏倍率 —— 显示多少就是多少。上限取 24：再大在课程卡这种
 * 小尺寸上已经看不出区别，只是白糊。
 */
const val CARD_BLUR_MAX_PX = 24f

/** 只做范围钳制（滑杆调不出界，防的是手改存档 / 旧文件里的越界值）。 */
fun coerceBlurPx(value: Float): Float = value.coerceIn(0f, CARD_BLUR_MAX_PX)

object AppearancePrefs {
    /** 外观独立 prefs 文件名（`ScheduleAppearance.FILE`） */
    const val FILE = "appearance_settings"

    /** 唯一键：`CombinationStyle` 的 JSON 快照（`ScheduleAppearance.FILE_STYLE_KEY`） */
    const val FILE_STYLE_KEY = "appearance_style"
}

/**
 * 外观参数快照（跨平台落盘）。
 *
 * ⚠ JSON 键名 = 字段名，一个字符都不能改（改名/删除 = 老用户外观全部读不出来）；
 * 可新增字段（解析器忽略未知键），落盘格式保持原样。
 */
data class AppearanceStyle(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    /** offset/scale 保存时的屏幕宽高（px）；0=未记录，加载时不重映射 */
    val offsetRefW: Float = 0f,
    val offsetRefH: Float = 0f,
    /** 课程卡片模糊半径（**px**，直接喂 `smallBlurRadius`，不乘 density）；出厂默认 12 */
    val cardBlur: Float = 12f,
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
    /** 边缘厚度档位；null = 未设置，用默认 */
    val cardEdgeThickness: CardEdgeThickness? = null,
    /** 卡片高光描边；缺键（老数据）按出厂默认关 */
    val cardRim: Boolean = false,
    val wallpaperBlur: Boolean = false
) {
    val safeAlignment: CardContentAlignment get() = cardContentAlignment ?: CardContentAlignment.CENTER_CENTER
    val safeTextColor: CardTextColor get() = cardTextColor ?: CardTextColor.COLORFUL
    val safeRefraction: CardRefractionLevel get() = cardRefraction ?: CardRefractionLevel.DEFAULT
    val safeEdgeThickness: CardEdgeThickness get() = cardEdgeThickness ?: CardEdgeThickness.DEFAULT

    /** 0 一律视为损坏并恢复默认（滑杆合法区间见上方的 0.5~2.0；0 会让网格整页静默空白） */
    val safeCardTextScale: Float get() = if (cardTextScale > 0f) cardTextScale else 1f

    val safeCardSurfaceAlpha: Float
        get() = cardSurfaceAlpha?.takeIf { it in 0f..1f }
            ?: CombinationDefaults.CARD_SURFACE_ALPHA_DEFAULT

    val safeCardHeight: Float
        get() = if (cardHeight > 0f) cardHeight else CombinationDefaults.CARD_HEIGHT_DEFAULT

    companion object {
        /** 默认值集中一处；本类 / JSON 缺省值 / UI 滑杆刻度 / 运行期快照四处必须一致 */
        fun defaults(): AppearanceStyle = AppearanceStyle()
    }
}

/** 外观默认值 —— 与滑杆默认值、JSON 缺省值保持一致。 */
object CombinationDefaults {
    const val CARD_HEIGHT_DEFAULT = 54f
    const val CARD_SURFACE_ALPHA_DEFAULT = 0.40f

    /**
     * 深色模式下白/黑底压得太实，设置值先乘这个系数再分发到各页面。
     * 默认 40% ⇒ 深色 32%。
     */
    const val DARK_SURFACE_ALPHA_FACTOR = 0.8f

    /**
     * 今日页高亮卡（摘要 / 格言）相对基准值的增量：默认 40% ⇒ 55%。
     */
    const val HIGHLIGHT_SURFACE_ALPHA_BONUS = 0.15f

    /**
     * 课表页（课程卡 + 分界带）相对基准值的偏移：亮色 40% ⇒ 25%，深色 32% ⇒ 17%。
     *
     * 三档因此是一条对称的梯子：课表 −15pp / 今日课程卡 0 / 今日高亮卡 +15pp。
     */
    const val SCHEDULE_SURFACE_ALPHA_OFFSET = -0.15f
}

// ── 课表页主题与卡片外观枚举：纯数据，供 `:ui-shared` 直接引用，不碰存储 ──

/**
 * 课表外观自定义页「默认主题」下拉的取值。
 *
 * ⚠ 与全局应用主题（`app_theme_prefs` 的 `theme_mode`，system/light/dark）**刻意隔离**：
 * 这里只决定今日页 / 课程表页跟随谁，全局开关不受影响 —— 所以 [FOLLOW_APP] 的 prefsValue
 * 复用了 "system" 字面量，但落在 [SCHEDULE_THEME_MODE_KEY] 这个不同的键上。
 *
 * ⚠ 落盘只走 [prefsValue]（数据键，勿本地化）；[label] 是展示名，随应用语言（见 [DisplayLang]）。
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
        /** 课表页主题专用 key，与全局主题开关隔离（见 [ThemeMode]） */
        const val SCHEDULE_THEME_MODE_KEY = "schedule_theme_mode"

        fun fromPrefsValue(value: String?): ThemeMode =
            entries.find { it.prefsValue == value } ?: FOLLOW_WALLPAPER
    }
}

/*
 * 卡片外观枚举：`CourseCard`（`:ui-shared`）用它们做布局 / 配色分流，零依赖。
 *
 * ⚠ 全部按 ordinal 落盘（`fromOrdinal` 是旧数据恢复入口），**枚举顺序不能改**。
 * `label` 是纯展示名（随应用语言），不参与落盘与比较。
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

/**
 * 卡片玻璃的折射强度（需壁纸才生效）。
 *
 * [scale] 乘在柔光玻璃的折射厚度上（`SoftGlassStyle.thickness`，基准 96px）；
 * 反射采样偏移不跟着缩放。没有柔光时不折射
 *
 * ⚠ 按 ordinal 落盘，**新档位只能往后加**。
 */
enum class CardRefractionLevel(
    /** 折射厚度倍率；0 = 关（厚度与反射偏移一起归零） */
    val scale: Float,
) {
    OFF(0f),
    WEAK(0.75f),
    DEFAULT(1f),
    STRONG(1.25f),
    EXTREME(1.5f);

    val label: String
        get() = when (this) {
            OFF -> if (DisplayLang.isZh) "关闭" else "Off"
            WEAK -> if (DisplayLang.isZh) "较弱" else "Weak"
            DEFAULT -> if (DisplayLang.isZh) "默认" else "Default"
            STRONG -> if (DisplayLang.isZh) "较强" else "Strong"
            EXTREME -> if (DisplayLang.isZh) "最强" else "Strongest"
        }

    companion object {
        fun fromOrdinal(ordinal: Int): CardRefractionLevel =
            entries.getOrElse(ordinal) { DEFAULT }
    }
}

/**
 * 玻璃边缘厚度档位：**乘在** `SoftGlassStyle.edgeWidth`（文件内默认 60px）上的倍率。
 *
 * 只改倍率、不改默认值 —— 换档不会让别处跟着变。⚠ 按 ordinal 落盘，顺序不能改。
 */
enum class CardEdgeThickness(val scale: Float) {
    SMALLER(0.8f),
    DEFAULT(1.0f),
    LARGER(1.2f),
    LARGEST(1.5f);

    val label: String
        get() = when (this) {
            SMALLER -> if (DisplayLang.isZh) "较小" else "Smaller"
            DEFAULT -> if (DisplayLang.isZh) "默认" else "Default"
            LARGER -> if (DisplayLang.isZh) "较大" else "Larger"
            LARGEST -> if (DisplayLang.isZh) "最大" else "Largest"
        }

    companion object {
        fun fromOrdinal(ordinal: Int): CardEdgeThickness =
            entries.getOrElse(ordinal) { DEFAULT }
    }
}
