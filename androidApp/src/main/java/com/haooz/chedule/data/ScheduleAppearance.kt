package com.haooz.chedule.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.core.content.edit
import androidx.core.graphics.scale
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.haooz.chedule.data.ScheduleAppearance.FILE_STYLE_KEY
import java.io.File
import java.io.FileOutputStream

// ════════════════════════════════════════════════════════════════════════
//  课表外观 —— 单文件全包
//
//  壁纸 + 偏移缩放 + 课程卡片全部可调参数（模糊/底色/高度/圆角/亮度/
//  分隔线/对齐/文字色/文字缩放/教室/教师/折射/壁纸模糊），加上配套的
//  三个枚举与取值对象。**调整外观只需要动这一个文件。**
//
//  设计取舍：当前是**单搭配**（没有搭配列表 UI），所以这里不保留 id维度 ——
//
//  存储位置：独立的 `appearance_settings` 文件，不与课程数据混存，
//  也不进全量备份（外观是本机观感，不随备份迁移）。
// ════════════════════════════════════════════════════════════════════════

/** 卡片内容对齐方式 */
enum class CardContentAlignment(val label: String) {
    TOP_START("顶部居左"),
    TOP_CENTER("顶部居中"),
    CENTER_START("中间居左"),
    CENTER_CENTER("中间居中");

    companion object {
        fun fromOrdinal(ordinal: Int): CardContentAlignment =
            entries.getOrElse(ordinal) { CENTER_CENTER }
    }
}

/** 卡片文字配色 */
enum class CardTextColor(val label: String) {
    COLORFUL("彩色"),
    SOLID("纯色");

    companion object {
        fun fromOrdinal(ordinal: Int): CardTextColor =
            entries.getOrElse(ordinal) { COLORFUL }
    }
}

/** 卡片玻璃对壁纸的透镜折射强度（需壁纸才生效） */
enum class CardRefractionLevel(val label: String, val lensRadiusDp: Float, val lensStrengthDp: Float) {
    OFF("关闭", 0f, 0f),
    WEAK("较弱", 5f, 9f),
    DEFAULT("默认", 6f, 14f),
    STRONG("较强", 8f, 22f);

    companion object {
        fun fromOrdinal(ordinal: Int): CardRefractionLevel =
            entries.getOrElse(ordinal) { DEFAULT }
    }
}

/**
 * 课表外观自定义页「默认主题」下拉的取值。
 *
 * ⚠️ 与全局应用主题（`app_theme_prefs` 的 `theme_mode`，值域 system/light/dark）
 * **刻意隔离**：这里只决定今日页/课程表页跟随谁，全局主题开关不受影响。
 * 故 [FOLLOW_APP] 的 prefsValue 复用 "system" 字面量但落在不同的键上。
 */
enum class ThemeMode(val label: String, val prefsValue: String) {
    FOLLOW_WALLPAPER("跟随壁纸", "follow_wallpaper"),
    FOLLOW_APP("跟随应用", "system"),
    LIGHT("浅色模式", "light"),
    DARK("深色模式", "dark");

    companion object {
        /** 课表外观"默认主题"下拉专用的偏好 key，仅决定今日页/课程表页主题，与全局主题开关隔离 */
        const val SCHEDULE_THEME_MODE_KEY = "schedule_theme_mode"

        fun fromPrefsValue(value: String?): ThemeMode =
            entries.find { it.prefsValue == value } ?: FOLLOW_WALLPAPER
    }
}

/**
 * 外观参数快照 —— 落盘的唯一结构。
 *
 * ⚠️ 这里的默认值就是**实际生效的默认值**：反序列化后直接读它。
 * [AppearanceConfig] 与 [Combination] 里同名字段的默认值只在直接构造对象时有效，
 * 走 `fromCombination` 时一律被覆盖 —— **三处必须保持一致**。
 *
 * 枚举字段声明为可空并配 safe getter：Gson UnsafeAllocator 使 Kotlin 默认值不生效。
 */
data class CombinationStyle(
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
    val safeCardTextScale: Float get() = if (cardTextScale > 0f) cardTextScale else 1f
    val safeCardSurfaceAlpha: Float get() = cardSurfaceAlpha?.takeIf { it in 0f..1f } ?: CARD_SURFACE_ALPHA_DEFAULT

    /**
     * 为 0 时网格高度会整页静默空白（不崩、无日志）；滑杆合法区间 34~92，
     * 0 一律视为损坏恢复默认。
     */
    val safeCardHeight: Float get() = if (cardHeight > 0f) cardHeight else CARD_HEIGHT_DEFAULT

    companion object {
        /** 与自定义页滑杆默认值一致 */
        const val CARD_HEIGHT_DEFAULT = 54f
        const val CARD_SURFACE_ALPHA_DEFAULT = 0.15f

        /** 仅用于校验快照键名，不参与取值 */
        private val FIELD_NAMES = setOf(
            "offsetX", "offsetY", "scale", "offsetRefW", "offsetRefH",
            "cardBlur", "cardAlpha", "cardSurfaceAlpha",
            "cardHeight",
            "cardCornerRadius", "wallpaperBrightness", "wallpaperIsLight",
            "showBreakDividers", "cardContentAlignment", "cardTextColor",
            "cardTextScale", "showClassroom", "showTeacher", "cardRefraction",
            "wallpaperBlur"
        )

        /**
         * 至少命中一个已知字段名才采信。键名对不上时 Gson 会把字段停在 Java
         * 默认值（如 cardHeight=0 → 课表页空白），故判为不可用，由调用方恢复默认。
         */
        fun parseSnapshotOrNull(gson: Gson, json: String): CombinationStyle? {
            val obj = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull() ?: return null
            if (obj.keySet().none { it in FIELD_NAMES }) return null
            return runCatching { gson.fromJson(json, CombinationStyle::class.java) }.getOrNull()
        }
    }
}

/** 运行期的外观快照（壁纸位图 + 变换 + 参数），供课表页直接消费 */
data class Combination(
    val bitmap: Bitmap?,
    val offset: androidx.compose.ui.geometry.Offset,
    val scale: Float,
    var snapshot: Bitmap? = null,
    var cardBlurRadius: Float = 4f,
    var cardAlpha: Float = 0.15f,
    var cardSurfaceAlpha: Float? = null,
    var cardHeight: Float = 54f,
    var cardCornerRadius: Float = 10f,
    var wallpaperBrightness: Float = 0f,
    var showBreakDividers: Boolean = true,
    var cardContentAlignment: CardContentAlignment = CardContentAlignment.CENTER_CENTER,
    var cardTextColor: CardTextColor = CardTextColor.COLORFUL,
    var cardTextScale: Float = 1f,
    var showClassroom: Boolean = true,
    var showTeacher: Boolean = true,
    var cardRefraction: CardRefractionLevel = CardRefractionLevel.DEFAULT,
    var wallpaperIsLight: Boolean? = null,
    var wallpaperBlur: Boolean = false
)

/**
 * 课表外观配置：全部可调参数收敛为不可变值对象。
 *
 * 取代原先分散的 N 个字段 × 4 份状态（cached/live/saved/original）的写法，
 * 避免手工同步遗漏导致的 bug。
 */
data class AppearanceConfig(
    val cardBlurRadius: Float = 4f,
    /** 课程色着色程度（有无壁纸都可调） */
    val cardAlpha: Float = 0.15f,
    /** 有壁纸时卡片白/黑底不透明度 */
    val cardSurfaceAlpha: Float = 0.15f,
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
    companion object {
        fun fromCombination(c: Combination): AppearanceConfig = AppearanceConfig(
            cardBlurRadius = c.cardBlurRadius,
            cardAlpha = c.cardAlpha.coerceIn(0f, 1f),
            cardSurfaceAlpha = (c.cardSurfaceAlpha
                ?: CombinationStyle.CARD_SURFACE_ALPHA_DEFAULT).coerceIn(0f, 1f),
            cardHeight = c.cardHeight,
            cardCornerRadius = c.cardCornerRadius,
            wallpaperBrightness = c.wallpaperBrightness,
            showBreakDividers = c.showBreakDividers,
            cardContentAlignment = c.cardContentAlignment,
            cardTextColor = c.cardTextColor,
            cardTextScale = c.cardTextScale,
            showClassroom = c.showClassroom,
            showTeacher = c.showTeacher,
            cardRefraction = c.cardRefraction,
            wallpaperBlur = c.wallpaperBlur
        )
    }
}

/**
 * 外观存储：单例，持有 prefs、壁纸位图缓存与全部读写方法。
 *
 * 只有一个键：[FILE_STYLE_KEY]（参数 JSON 快照）+ 一个壁纸文件。没有 id 维度。
 */
object ScheduleAppearance {

    // 这两个常量已下沉 :core（AppearancePrefs）—— CourseRepository 判「是不是外观键」
    // 时要用它们，而 :core 不能反向依赖 :app。这里 const 转发，取值同源不会抄错。
    /** 独立 prefs 文件名 */
    const val FILE = AppearancePrefs.FILE

    /** 唯一的数据键：参数JSON 快照 */
    const val FILE_STYLE_KEY = AppearancePrefs.FILE_STYLE_KEY

    /** 壁纸文件名（存 filesDir） */
    private const val WALLPAPER_FILE = "appearance_wallpaper.webp"

    private lateinit var appContext: Context
    private val gson = Gson()
    private lateinit var prefs: KeyValueStore

    /** 参数快照缓存，读多写少 */
    private var cached: CombinationStyle? = null

    /** 壁纸解码是主要瓶颈，按可用内存 1/8 做 Lru 缓存，绝对上限 32MB 防大堆机型占压过大 */
    private val wallpaperCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        // 4MB 下限不可省：小内存机型 maxMemory/8 可能只有几百 KB，等于没缓存
        minOf(Runtime.getRuntime().maxMemory() / 8, 32L * 1024 * 1024)
            .coerceAtLeast(4L * 1024 * 1024)
            .toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * 批量保存期间累积的写入；非 null 表示正在批量中。
     *
     * 原实现共享一个 `SharedPreferences.Editor`（可跨调用累积、最后 apply 一次）；
     * `KeyValueStore.edit` 是一次性事务、没有可跨调用的 Editor，
     * 因此改成**累积写入块**，[batchSave] 结束时一次性提交 —— 语义等价
     * （都是「块内多次写入只落盘一次」），且滑杆拖动这种高频场景仍只提交一次。
     */
    private var pendingWrites: (KeyValueEditor.() -> Unit)? = null

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        // 走 AppStorage 按名字取（Android 侧仍是同一个 appearance_settings 文件）
        prefs = AppStorage.store(FILE)
        migrateLegacyAppearanceIfNeeded()
    }

    /**
     * 从旧版主 prefs（`course_schedule_prefs`）迁移外观数据。
     *
     * 外观曾寄存在主 prefs 里，键为 `combination_style_{id}`（JSON 快照，
     * 自 1.5.2 起就是这个格式），壁纸文件为 `combination_wallpaper_{id}.webp`。
     * 搬进独立文件后若不迁移，老用户升级会静默丢失全部外观设置 —— 且不报错，
     * 故这里必须做。**兼容基线：1.5.2**；更早的 `comb_xxx` 分散键格式不再处理。
     *
     * 幂等：只在**新文件完全为空**（本机从无外观数据）时执行一次；
     * 已迁过一次或用户在新文件里有任何值，一律跳过，绝不覆盖。
     */
    private fun migrateLegacyAppearanceIfNeeded() {
        if (prefs.all().isNotEmpty()) return
        val legacy = runCatching {
            AppStorage.store(LEGACY_PREFS)
        }.getOrNull() ?: return

        // 迁「当前正在用的那套」：1.5.6 及更早存有 current_combination_id 指针，
        // 只取 combination_ids 首个会在多搭配老数据上迁错（迁成用户没在用的那套）。
        val legacyIds = legacy.getStringOrNull(LEGACY_KEY_COMBINATION_IDS)
            ?.split(",")?.mapNotNull { it.trim().toLongOrNull() }
            ?.takeIf { it.isNotEmpty() }
        val currentId = runCatching {
            legacy.getLong(LEGACY_KEY_CURRENT_COMBINATION_ID, Long.MIN_VALUE)
        }.getOrDefault(Long.MIN_VALUE)
        val legacyId = when {
            legacyIds == null -> 0L
            currentId in legacyIds -> currentId
            else -> legacyIds.first()
        }

        // 快照坏掉（键名对不上/ JSON 损坏）时**不迁**，交给 getStyle() 走
        // restoreAfterBadSnapshot 按壁纸重测光，避免把默认值当用户设置落盘
        val style = legacy.getStringOrNull("$LEGACY_STYLE_PREFIX$legacyId")
            ?.let { CombinationStyle.parseSnapshotOrNull(gson, it) }
        if (style != null) {
            prefs.edit { putString(FILE_STYLE_KEY, gson.toJson(style)) }
            cached = style
        }
        migrateLegacyWallpaperFile(legacyId)
    }

    /** 壁纸文件改名迁移：webp 为主，旧 png 兜底（1.5.2 前后都写过 png） */
    private fun migrateLegacyWallpaperFile(legacyId: Long) {
        val target = File(appContext.filesDir, WALLPAPER_FILE)
        if (target.exists()) return
        val candidates = listOf(
            File(appContext.filesDir, WALLPAPER_FILE),
            File(appContext.filesDir, "$LEGACY_WALLPAPER_PREFIX$legacyId.webp"),
            File(appContext.filesDir, "$LEGACY_WALLPAPER_PREFIX$legacyId.png")
        )
        candidates.firstOrNull { it.exists() }?.let { src ->
            runCatching { src.copyTo(target, overwrite = true) }
        }
    }

    // ── 旧版存储位置（仅迁移期使用）──────────────────────────
    private const val LEGACY_PREFS = "course_schedule_prefs"
    private const val LEGACY_STYLE_PREFIX = "combination_style_"
    private const val LEGACY_WALLPAPER_PREFIX = "combination_wallpaper_"
    private const val LEGACY_KEY_COMBINATION_IDS = "combination_ids"
    private const val LEGACY_KEY_CURRENT_COMBINATION_ID = "current_combination_id"

    /** 批量保存外观参数，块内写入合并为一次提交；可嵌套 */
    fun batchSave(block: () -> Unit) {
        if (pendingWrites != null) {
            block()
            return
        }
        pendingWrites = {}
        try {
            block()
        } finally {
            val writes = pendingWrites ?: {}
            pendingWrites = null
            prefs.edit(writes)
        }
    }

    /** 所有写入统一入口：批量期间累积，否则独立提交 */
    private fun edit(block: KeyValueEditor.() -> Unit) {
        val pending = pendingWrites
        if (pending != null) {
            // 累积：先跑已攒下的写入，再跑本次
            pendingWrites = { pending(); block() }
        } else {
            prefs.edit(block)
        }
    }

    // ── 参数快照 ────────────────────────────────────────────

    /** 坏快照重置后用现有壁纸重测光，否则主题开关因 isLight=null 整条失效 */
    private fun restoreAfterBadSnapshot(): CombinationStyle {
        val isLight = computeWallpaperIsLight(loadWallpaper())
        val style = CombinationStyle(wallpaperIsLight = isLight)
        saveStyle(style)
        return style
    }

    fun getStyle(): CombinationStyle {
        cached?.let { return it }
        val json = prefs.getStringOrNull(FILE_STYLE_KEY)
        val style = if (json == null) {
            CombinationStyle().also { saveStyle(it) }
        } else {
            CombinationStyle.parseSnapshotOrNull(gson, json) ?: restoreAfterBadSnapshot()
        }
        cached = style
        return style
    }

    fun saveStyle(style: CombinationStyle) {
        edit { putString(FILE_STYLE_KEY, gson.toJson(style)) }
        cached = style
    }

    /** 在现有快照基础上做一次变更并落盘 */
    fun updateStyle(transform: (CombinationStyle) -> CombinationStyle) {
        saveStyle(transform(getStyle()))
    }

    // ── 壁纸变换 ────────────────────────────────────────────

    fun getOffsetX(): Float = getStyle().offsetX
    fun getOffsetY(): Float = getStyle().offsetY
    fun getScale(): Float = getStyle().scale
    fun getOffsetRefW(): Float = getStyle().offsetRefW
    fun getOffsetRefH(): Float = getStyle().offsetRefH

    fun saveState(offsetX: Float, offsetY: Float, scale: Float, refW: Float = 0f, refH: Float = 0f) =
        updateStyle { it.copy(offsetX = offsetX, offsetY = offsetY, scale = scale, offsetRefW = refW, offsetRefH = refH) }

    // ── 卡片参数 ────────────────────────────────────────────

    fun saveCardBlur(radius: Float) = updateStyle { it.copy(cardBlur = radius) }
    fun getCardBlur(): Float = getStyle().cardBlur

    fun saveCardAlpha(alpha: Float) = updateStyle { it.copy(cardAlpha = alpha) }
    fun getCardAlpha(): Float = getStyle().cardAlpha

    fun saveCardSurfaceAlpha(alpha: Float) = updateStyle { it.copy(cardSurfaceAlpha = alpha) }
    fun getCardSurfaceAlpha(): Float = getStyle().safeCardSurfaceAlpha

    fun saveCardHeight(height: Float) = updateStyle { it.copy(cardHeight = height) }
    fun getCardHeight(): Float = getStyle().safeCardHeight

    fun saveCardCornerRadius(radius: Float) = updateStyle { it.copy(cardCornerRadius = radius) }
    fun getCardCornerRadius(): Float = getStyle().cardCornerRadius

    fun saveWallpaperBrightness(brightness: Float) = updateStyle { it.copy(wallpaperBrightness = brightness) }
    fun getWallpaperBrightness(): Float = getStyle().wallpaperBrightness

    fun saveWallpaperIsLight(isLight: Boolean?) = updateStyle { it.copy(wallpaperIsLight = isLight) }

    fun getWallpaperIsLight(): Boolean? {
        getStyle().wallpaperIsLight?.let { return it }
        // 有壁纸但测光结果丢失时兜底重算
        val isLight = computeWallpaperIsLight(loadWallpaper()) ?: return null
        updateStyle { it.copy(wallpaperIsLight = isLight) }
        return isLight
    }

    fun saveShowBreakDividers(show: Boolean) = updateStyle { it.copy(showBreakDividers = show) }
    fun getShowBreakDividers(): Boolean = getStyle().showBreakDividers

    fun saveCardContentAlignment(alignment: CardContentAlignment) =
        updateStyle { it.copy(cardContentAlignment = alignment) }
    fun getCardContentAlignment(): CardContentAlignment = getStyle().safeAlignment

    fun saveCardTextColor(color: CardTextColor) = updateStyle { it.copy(cardTextColor = color) }
    fun getCardTextColor(): CardTextColor = getStyle().safeTextColor

    fun saveCardTextScale(scale: Float) = updateStyle { it.copy(cardTextScale = scale) }
    fun getCardTextScale(): Float = getStyle().safeCardTextScale

    fun saveShowClassroom(show: Boolean) = updateStyle { it.copy(showClassroom = show) }
    fun getShowClassroom(): Boolean = getStyle().showClassroom

    fun saveShowTeacher(show: Boolean) = updateStyle { it.copy(showTeacher = show) }
    fun getShowTeacher(): Boolean = getStyle().showTeacher

    fun saveCardRefraction(level: CardRefractionLevel) = updateStyle { it.copy(cardRefraction = level) }
    fun getCardRefraction(): CardRefractionLevel = getStyle().safeRefraction

    fun saveWallpaperBlur(blur: Boolean) = updateStyle { it.copy(wallpaperBlur = blur) }
    fun getWallpaperBlur(): Boolean = getStyle().wallpaperBlur

    // ⚠️ 下课烟花开关**不在本文件**：它的真实读写方是 `ClassEndEffectSettings`
    // （`app_preferences` 的 `class_end_fireworks`）。外观曾在这里重复定义一份同键属性，
    // 但零调用点且读的是本文件，读到的永远是默认值 —— 已删除，别再加回来。

    // ── 默认主题（课表外观自定义页的下拉）────────────────────

    /**
     * 课表页/今日页跟随的主题。
     *
     * ⚠️ 存`app_theme_prefs` 而**不是**本文件的 `appearance_settings`：
     * 它与全局应用主题共用一个文件但**不同的键**，且要被 `Theme.kt`、
     * `WidgetTextSizes` 在首帧读取 —— 放进外观文件会让这些组件多依赖一个文件，
     * 而首帧主题计算不能碰 IO 大的文件。键语义见 [ThemeMode]。
     */
    private val themePrefs: KeyValueStore
        get() = AppStorage.store(FILE_THEME)

    fun getThemeMode(): ThemeMode =
        ThemeMode.fromPrefsValue(themePrefs.getStringOrNull(ThemeMode.SCHEDULE_THEME_MODE_KEY))

    fun setThemeMode(mode: ThemeMode) {
        themePrefs.edit { putString(ThemeMode.SCHEDULE_THEME_MODE_KEY, mode.prefsValue) }
    }

    /** 与全局应用主题共用的 prefs 文件名 */
    const val FILE_THEME = "app_theme_prefs"

    // ── 壁纸位图 ────────────────────────────────────────────

    /** 壁纸的目标存储/解码分辨率：用长短边而非当前横竖屏，避免进应用方向不同导致采样/尺寸漂移 */
    private fun wallpaperTargetBounds(): Pair<Int, Int> {
        val metrics = appContext.resources.displayMetrics
        return maxOf(metrics.widthPixels, metrics.heightPixels) to
            minOf(metrics.widthPixels, metrics.heightPixels)
    }

    /** 计算满足目标尺寸的 2 的幂次降采样倍数（inSampleSize） */
    private fun calculateInSampleSize(bounds: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (bounds.outHeight > reqHeight || bounds.outWidth > reqWidth) {
            val halfHeight = bounds.outHeight / 2
            val halfWidth = bounds.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /** 16×16 网格感知加权平均亮度；无壁纸返回 null */
    private fun computeWallpaperIsLight(bitmap: Bitmap?): Boolean? {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
        val gridW = 16
        val gridH = 16
        val small = bitmap.scale(gridW, gridH)
        var sum = 0.0
        for (x in 0 until gridW) {
            for (y in 0 until gridH) {
                val c = small.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                sum += 0.299 * r + 0.587 * g + 0.114 * b
            }
        }
        val avg = sum / (gridW * gridH)
        return avg >= 128
    }

    /** 缩放到屏幕分辨率后以 WebP 有损 80 存储 */
    fun saveWallpaper(bitmap: Bitmap): Boolean {
        return try {
            val (targetW, targetH) = wallpaperTargetBounds()
            val scaled = if (bitmap.width > targetW || bitmap.height > targetH) {
                val scale = minOf(targetW / bitmap.width.toFloat(), targetH / bitmap.height.toFloat())
                bitmap.scale(
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap
            val file = File(appContext.filesDir, WALLPAPER_FILE)
            // 旧的 CompressFormat.WEBP 在 API 30 起被 WEBP_LOSSY 取代，语义等价（都是有损）
            val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                CompressFormat.WEBP
            }
            FileOutputStream(file).use { out -> scaled.compress(webpFormat, 80, out) }
            wallpaperCache.put(WALLPAPER_FILE, scaled)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 删磁盘文件并清缓存，实现持久化清除 */
    fun clearWallpaper() {
        wallpaperCache.remove(WALLPAPER_FILE)
        File(appContext.filesDir, WALLPAPER_FILE).delete()
    }

    /** 带 Lru 缓存；解码按屏幕分辨率降采样 */
    fun loadWallpaper(): Bitmap? {
        wallpaperCache.get(WALLPAPER_FILE)?.let { return it }
        val file = File(appContext.filesDir, WALLPAPER_FILE)
        if (!file.exists()) return null
        return try {
            val (targetW, targetH) = wallpaperTargetBounds()
            // 先读尺寸再降采样，避免超大图一次性解码耗尽内存
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            opts.inSampleSize = calculateInSampleSize(opts, targetW, targetH)
            opts.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, opts)?.also {
                wallpaperCache.put(WALLPAPER_FILE, it)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 组装运行期快照，供课表页消费 */
    fun currentCombination(): Combination {
        val style = getStyle()
        return Combination(
            bitmap = loadWallpaper(),
            offset = androidx.compose.ui.geometry.Offset(style.offsetX, style.offsetY),
            scale = style.scale,
            cardBlurRadius = style.cardBlur,
            cardAlpha = style.cardAlpha,
            cardSurfaceAlpha = style.cardSurfaceAlpha,
            cardHeight = style.cardHeight,
            cardCornerRadius = style.cardCornerRadius,
            wallpaperBrightness = style.wallpaperBrightness,
            showBreakDividers = style.showBreakDividers,
            cardContentAlignment = style.safeAlignment,
            cardTextColor = style.safeTextColor,
            cardTextScale = style.safeCardTextScale,
            showClassroom = style.showClassroom,
            showTeacher = style.showTeacher,
            cardRefraction = style.safeRefraction,
            wallpaperIsLight = style.wallpaperIsLight,
            wallpaperBlur = style.wallpaperBlur
        )
    }
}