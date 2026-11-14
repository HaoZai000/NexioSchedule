package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.haooz.chedule.data.appearance.ThemeMode
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.KEY_CHANGED_ANYWHERE
import com.haooz.chedule.data.storage.getStringOrNull
import com.haooz.chedule.ui.activities.classschedule.ScheduleViewMode
import top.yukonga.miuix.kmp.theme.AppSettingDarkBridge
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.platformDynamicColors
import top.yukonga.miuix.kmp.theme.rememberAppSettingDark as miuixRememberAppSettingDark

// 壁纸强制主题：非 null 时 isAppDarkTheme 直接用该值，今日页/课程表页按壁纸亮暗锁定。
// 别名指向 :miuix 里的同一个 CompositionLocal 实例，调用方无需改动。
val LocalForcedDarkTheme = top.yukonga.miuix.kmp.theme.LocalForcedDarkTheme

/**
 * 课程表 / 排班页底色（无壁纸时铺满页面的那一层）。
 * 需要与页面底色对齐的元素统一取这里，不要各处抄 #F7F7F7 / #000000 字面量。
 */
@Composable
fun schedulePageBackgroundColor(isDark: Boolean): Color =
    if (MiuixTheme.isDynamicColor) platformDynamicColors(dark = isDark).background
    else if (isDark) Color(0xFF000000) else Color(0xFFF7F7F7)

/** 无壁纸时课程卡垫底色的不透明度（浮层与网格卡片共用，保持两者观感一致） */
private const val COURSE_CARD_BACKING_ALPHA = 0.92f

/**
 * 课程卡片在**无壁纸**时的垫底色：页底色 @COURSE_CARD_BACKING_ALPHA；有壁纸返回 null。
 * 无壁纸时课程色自身只有 0.1~0.4 透明度，不垫一层近不透明的页底色会透出网格线。
 */
@Composable
fun courseCardSolidBacking(isDark: Boolean, hasWallpaper: Boolean): Color? =
    if (hasWallpaper) null
    else schedulePageBackgroundColor(isDark).copy(alpha = COURSE_CARD_BACKING_ALPHA)

/**
 * 无材质档的卡片垫底色：这一档没有玻璃替它挡住壁纸，卡片必须自带一层近不透明的底。
 * 与 [courseCardSolidBacking] 同值，两种情况下卡片浓淡才一致（「一层底色 + 一层课程色」）。
 */
@Composable
fun courseCardNoneTierBacking(isDark: Boolean): Color =
    schedulePageBackgroundColor(isDark).copy(alpha = COURSE_CARD_BACKING_ALPHA)

/**
 * 当前作用域生效的深浅色：页面按壁纸强制的口径优先，否则读应用主题 / 系统。
 *
 * 纯读函数，**不写任何全局快照**。
 * `GlassEnvironment.isDark` 只由 [com.haooz.chedule.ui.theme.CourseScheduleTheme]
 * 根部唯一写入，供少量 draw 期观感（按压高光 / 阴影）使用。
 */
@Composable
fun isAppDarkTheme(): Boolean =
    LocalForcedDarkTheme.current ?: rememberAppSettingDark()

/**
 * App 偏好里的「是否深色」（`theme_mode`：system / light / dark）。
 *
 * 实现在 :miuix（弹窗也要用），数据源由 [installAppSettingDarkBridge] 注入。
 */
@Composable
fun rememberAppSettingDark(): Boolean = miuixRememberAppSettingDark()

/**
 * 向 :miuix 注入读 `theme_mode` 的实现。由 [CourseScheduleTheme] 在组合根部调用，
 * 必须在任何 miuix 组件之前。重复调用无副作用。
 */
internal fun installAppSettingDarkBridge() {
    if (AppSettingDarkBridge.read != null) return
    AppSettingDarkBridge.read = {
        AppStorage.store("app_theme_prefs").getString("theme_mode", "system")
    }
    val subscribe: (onChange: () -> Unit) -> (() -> Unit) = { onChange ->
        val store = AppStorage.store("app_theme_prefs")
        // iOS 的通知不带 key（传哨兵），必须接受它
        val token = store.registerListener { key ->
            if (key == KEY_CHANGED_ANYWHERE || key == "theme_mode") onChange()
        }
        // 取消订阅，否则每次主题重建泄漏一个监听
        val unsubscribe: () -> Unit = { store.unregisterListener(token) }
        unsubscribe
    }
    AppSettingDarkBridge.subscribe = subscribe
}

// ── 今日页天气显隐 ───────────────────────────────────────────────────────
// 与触感反馈共用 app_preferences；键名是落盘契约，改了即丢用户选择。
// 键名沿用旧值：历史上它同时控制信息卡，现在语义收窄为「只控制天气」。
const val KEY_TODAY_INFO_CARDS = "today_info_cards_enabled"

/** 关闭后今日页助手卡、课表顶栏都不再显示天气；日期、信息卡与课程不受影响。 */
@Composable
fun rememberWeatherVisible(): Boolean {
    val prefs = remember { AppStorage.store("app_preferences") }
    var enabled by remember { mutableStateOf(prefs.getBoolean(KEY_TODAY_INFO_CARDS, true)) }
    DisposableEffect(prefs) {
        val token = prefs.registerListener { key ->
            if (key == KEY_CHANGED_ANYWHERE || key == KEY_TODAY_INFO_CARDS) {
                enabled = prefs.getBoolean(KEY_TODAY_INFO_CARDS, true)
            }
        }
        onDispose { prefs.unregisterListener(token) }
    }
    return enabled
}

// ── 课表页「默认主题」档位 ───────────────────────────────────────────────
// 落盘是数据契约：**与全局应用主题共用 app_theme_prefs 文件但键不同**
// （键名见 ThemeMode.SCHEDULE_THEME_MODE_KEY），文件名与键名逐字不可改。

/** 与全局应用主题共用一个 prefs 文件（值逐字不可改） */
private const val SCHEDULE_THEME_PREFS_FILE = "app_theme_prefs"

/** 读课表页「默认主题」档位（无值回落 ThemeMode.FOLLOW_WALLPAPER） */
fun loadScheduleThemeMode(): ThemeMode = ThemeMode.fromPrefsValue(
    AppStorage.store(SCHEDULE_THEME_PREFS_FILE)
        .getStringOrNull(ThemeMode.SCHEDULE_THEME_MODE_KEY)
)

/** 写课表页「默认主题」档位（CustomizeScheduleScreen 点「应用」时落盘） */
fun saveScheduleThemeMode(mode: ThemeMode) {
    AppStorage.store(SCHEDULE_THEME_PREFS_FILE).edit {
        putString(ThemeMode.SCHEDULE_THEME_MODE_KEY, mode.prefsValue)
    }
}

// ── 课表页视图模式（周网格 / 时间轴）──────────────────────────────────
// 与主题档位共用 app_theme_prefs 文件、键独立。键名与取值是落盘契约，改了即丢用户选择。

private const val SCHEDULE_VIEW_MODE_KEY = "schedule_view_mode"
private const val VIEW_MODE_WEEK = 0
private const val VIEW_MODE_TIMELINE = 1

/** 读课表页视图模式（缺省 = 周网格） */
fun loadScheduleViewMode(): ScheduleViewMode =
    if (AppStorage.store(SCHEDULE_THEME_PREFS_FILE)
            .getInt(SCHEDULE_VIEW_MODE_KEY, VIEW_MODE_WEEK) == VIEW_MODE_TIMELINE
    ) {
        ScheduleViewMode.TIMELINE
    } else {
        ScheduleViewMode.WEEK
    }

/** 写课表页视图模式（课表外观弹窗切换时落盘） */
fun saveScheduleViewMode(mode: ScheduleViewMode) {
    AppStorage.store(SCHEDULE_THEME_PREFS_FILE).edit {
        putInt(
            SCHEDULE_VIEW_MODE_KEY,
            if (mode == ScheduleViewMode.TIMELINE) VIEW_MODE_TIMELINE else VIEW_MODE_WEEK
        )
    }
}

// ── 今日页视图模式（周网格 / 时间轴）──────────────────────────────────
// 与课表页共用 app_theme_prefs 文件但键独立：今日页可以单独开时间轴，课表页保持周视图。

private const val TODAY_VIEW_MODE_KEY = "today_view_mode"
private const val TODAY_VIEW_WEEK = 0
private const val TODAY_VIEW_TIMELINE = 1

/** 今日页呈现方式：周网格 / 时间轴（不再提供「跟随课表页」档位）。 */
enum class TodayViewMode(val prefsValue: Int) {
    WEEK(TODAY_VIEW_WEEK),
    TIMELINE(TODAY_VIEW_TIMELINE);

    companion object {
        fun fromPrefs(value: Int): TodayViewMode? =
            entries.firstOrNull { it.prefsValue == value }
    }
}

/**
 * 读今日页视图模式。
 *
 * 没有存过值时按课表页当前视图初始化（旧版存的是「跟随」-1，也走这条）：
 * 老用户升级后首屏观感不变，之后再改今日页视图就与课表页解耦。
 */
fun loadTodayViewMode(): TodayViewMode =
    TodayViewMode.fromPrefs(
        AppStorage.store(SCHEDULE_THEME_PREFS_FILE).getInt(TODAY_VIEW_MODE_KEY, -1)
    ) ?: run {
        if (loadScheduleViewMode() == ScheduleViewMode.TIMELINE) {
            TodayViewMode.TIMELINE
        } else {
            TodayViewMode.WEEK
        }
    }

/** 写今日页视图模式（课表外观弹窗切换时落盘） */
fun saveTodayViewMode(mode: TodayViewMode) {
    AppStorage.store(SCHEDULE_THEME_PREFS_FILE).edit {
        putInt(TODAY_VIEW_MODE_KEY, mode.prefsValue)
    }
}

/**
 * 仅影响今日页/课程表页，与全局 theme_mode 隔离。
 *
 * ⚠ iOS 的 `NSUserDefaultsDidChangeNotification` 只通知「变了」、**不带 key**，
 * 所以回调里是无条件复查，而非按 key 精确过滤（见 `KEY_CHANGED_ANYWHERE`）。
 */
@Composable
fun rememberScheduleThemeMode(): ThemeMode {
    val prefs = remember { AppStorage.store(SCHEDULE_THEME_PREFS_FILE) }
    val themeMode = remember { mutableStateOf(loadScheduleThemeMode()) }

    DisposableEffect(prefs) {
        // ⚠ iOS 传 `KEY_CHANGED_ANYWHERE` 哨兵（系统通知不带 key），
        //   按 key 精确过滤会让 iOS 永远收不到生效。
        val token = prefs.registerListener { key ->
            if (key == KEY_CHANGED_ANYWHERE ||
                key == ThemeMode.SCHEDULE_THEME_MODE_KEY
            ) {
                themeMode.value = loadScheduleThemeMode()
            }
        }
        onDispose {
            prefs.unregisterListener(token)
        }
    }

    return themeMode.value
}
