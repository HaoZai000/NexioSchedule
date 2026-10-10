package com.haooz.chedule.ui.utils

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.ScheduleAppearance
import com.haooz.chedule.data.ThemeMode
import top.yukonga.miuix.kmp.theme.rememberAppSettingDark as miuixRememberAppSettingDark

// 壁纸强制主题：非 null 时 isAppDarkTheme 直接用该值，今日页/课程表页按壁纸亮暗锁定。
// 已搬进 :miuix 的 theme 包（BlurBottomSheet 在模块内要用它提供 null 阻止渗色），
// 这里保留一个别名，:app 内 15 处引用无需改动，且与模块内是同一个 CompositionLocal 实例。
val LocalForcedDarkTheme = top.yukonga.miuix.kmp.theme.LocalForcedDarkTheme

/**
 * 课程表 / 排班页底色（无壁纸时铺满页面的那一层）。
 * 需要与页面底色对齐的元素统一取这里，不要各处抄 #F7F7F7 / #000000 字面量。
 */
fun schedulePageBackgroundColor(isDark: Boolean): Color =
    if (isDark) Color(0xFF000000) else Color(0xFFF7F7F7)

/** 无壁纸时课程卡垫底色的不透明度（浮层与网格卡片共用，保持两者观感一致） */
private const val COURSE_CARD_BACKING_ALPHA = 0.92f

/**
 * 课程卡片在**无壁纸**时的垫底色：页底色 @COURSE_CARD_BACKING_ALPHA；有壁纸返回 null。
 *
 * 无壁纸时课程色自身只有 0.1~0.4 透明度，卡片压在网格上会透出格线，垫一层接近
 * 不透明的页底色才立得住。有壁纸时玻璃层采样的是不透明壁纸层，垫色会被盖住。
 *
 * 网格里的课程卡与长按拖拽浮层统一取这里，避免两处各抄一份后漂移。
 */
fun courseCardSolidBacking(isDark: Boolean, hasWallpaper: Boolean): Color? =
    if (hasWallpaper) null
    else schedulePageBackgroundColor(isDark).copy(alpha = COURSE_CARD_BACKING_ALPHA)

/**
 * 主题亮暗的全局快照，供 draw 阶段这类非组合上下文读取 ——
 * [isAppDarkTheme] 是 `@Composable`，绘制里调不了，而高光亮度要按主题分流。
 *
 * 由 [isAppDarkTheme] 每次组合后写入。写同值不触发失效，开销可忽略。
 *
 * 实例已搬到 backdrop（InteractiveHighlight 在 :miuix 内读它），这里只做别名，
 * 保证 :app 写入的值就是 :miuix 读到的值。
 */
val AppThemeSnapshot = com.kyant.backdrop.AppThemeSnapshot

/**
 * 当前可见主页面背后是否为壁纸，理由同 [AppThemeSnapshot]：draw 阶段读不到页面状态，
 * 而高光亮不亮取决于背后是壁纸还是近白底。
 *
 * 由 MainActivity 写入。典型用途：浅色模式下高光默认压到 0.05（近白底加白会被截断
 * 成白斑），但今日页 / 课程表页有壁纸时背景不是近白 —— 此时给到 0.1。
 */
val PageBackdropSnapshot = com.kyant.backdrop.PageBackdropSnapshot

/** 当前生效的深浅色；顺带把结果写进 [AppThemeSnapshot] 供 draw 阶段读取 */
@Composable
fun isAppDarkTheme(): Boolean {
    val forced = LocalForcedDarkTheme.current
    if (forced != null) {
        SideEffect { AppThemeSnapshot.isDark.value = forced }
        return forced
    }
    val dark = rememberAppSettingDark()
    SideEffect { AppThemeSnapshot.isDark.value = dark }
    return dark
}

// 不经过壁纸强制覆盖，只读 theme_mode
// 实现已搬进 :miuix（theme/AppDarkTheme.android.kt），因为 BlurBottomSheet 在
// :miuix 内也需要读同一个偏好，构成反向依赖。这里转调，行为与迁移前一致。
@Composable
fun rememberAppSettingDark(): Boolean = miuixRememberAppSettingDark()

// 仅影响今日页/课程表页，与全局 theme_mode 隔离
@Composable
fun rememberScheduleThemeMode(): ThemeMode {
    val prefs = remember { AppStorage.store(ScheduleAppearance.FILE_THEME) }
    val themeMode = remember { mutableStateOf(ScheduleAppearance.getThemeMode()) }

    DisposableEffect(prefs) {
        // 走 KeyValueStore.registerListener。⚠ iOS 侧该实现返回 null
        //（NSUserDefaultsDidChangeNotification 不带具体 key），主题切换后要等下次组合才刷新 ——
        // 是平台能力差异，不是崩溃：token 为 null 时 unregisterListener 是空操作。
        val token = prefs.registerListener { key ->
            if (key == ThemeMode.SCHEDULE_THEME_MODE_KEY) {
                themeMode.value = ScheduleAppearance.getThemeMode()
            }
        }
        onDispose {
            prefs.unregisterListener(token)
        }
    }

    return themeMode.value
}

fun Activity.applyThemeAwareSystemBars() {
    val themeMode = AppStorage.store("app_theme_prefs").getString("theme_mode", "system")

    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> {
            val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMode == Configuration.UI_MODE_NIGHT_YES
        }
    }
    // 状态栏与导航栏均跟随应用设置，不受壁纸强制主题影响
    applyThemeAwareSystemBars(isDark)
    applyNavigationBarIsDark(isDark)
}

// 按显式深色值刷新状态栏；导航栏仍跟随应用设置
fun Activity.applyThemeAwareSystemBars(isDark: Boolean) {
    window.decorView.post {
        ApiCompat.setSystemBarsAppearance(
            window,
            if (isDark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )
    }
}

// 导航栏图标始终跟随 theme_mode，不随壁纸强制主题变化
fun Activity.applyNavigationBarIsDark(isDark: Boolean) {
    window.decorView.post {
        ApiCompat.setSystemBarsAppearance(
            window,
            if (isDark) 0 else WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
    }
}
