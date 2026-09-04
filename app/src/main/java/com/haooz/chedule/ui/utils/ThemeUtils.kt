/** 主题工具类 - 管理深色模式切换和状态栏样式 */
package com.haooz.chedule.ui.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.haooz.chedule.data.ThemeMode

/**
 * 壁纸强制深色模式覆盖：非 null 时，isAppDarkTheme() 直接返回该值，
 * 用于今日页/课程表页有壁纸时按壁纸亮暗锁定主题。
 */
val LocalForcedDarkTheme = staticCompositionLocalOf<Boolean?> { null }

@Composable
fun isAppDarkTheme(): Boolean {
    // 若当前组合有壁纸并强制了主题，优先使用该结果
    LocalForcedDarkTheme.current?.let { return it }
    return rememberAppSettingDark()
}

/** 读取应用设置（theme_mode，系统/浅色/深色）对应的深浅色，不经过壁纸强制覆盖 */
@Composable
fun rememberAppSettingDark(): Boolean {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE) }
    val themeMode = remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String? ->
            if (key == "theme_mode") {
                themeMode.value = prefs.getString("theme_mode", "system") ?: "system"
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return when (themeMode.value) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
}

/** 读取课表外观"默认主题"下拉的档位（跟随壁纸/跟随应用/浅色模式/深色模式），默认跟随壁纸。
 *  仅决定今日页/课程表页的主题，与全局主题开关（theme_mode）相互隔离，不影响其它任何页面。 */
@Composable
fun rememberScheduleThemeMode(): ThemeMode {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE) }
    val themeMode = remember {
        mutableStateOf(
            ThemeMode.fromPrefsValue(prefs.getString(ThemeMode.SCHEDULE_THEME_MODE_KEY, "follow_wallpaper"))
        )
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String? ->
            if (key == ThemeMode.SCHEDULE_THEME_MODE_KEY) {
                themeMode.value = ThemeMode.fromPrefsValue(
                    prefs.getString(ThemeMode.SCHEDULE_THEME_MODE_KEY, "follow_wallpaper")
                )
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return themeMode.value
}

fun Activity.applyThemeAwareSystemBars() {
    val prefs = getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE)
    val themeMode = prefs.getString("theme_mode", "system") ?: "system"

    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> {
            val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMode == Configuration.UI_MODE_NIGHT_YES
        }
    }
    // 状态栏跟随应用设置，导航栏始终跟随应用设置（theme_mode），不受壁纸强制主题影响
    applyThemeAwareSystemBars(isDark)
    applyNavigationBarIsDark(isDark)
}

/** 按显式深色值仅刷新状态栏外观（用于壁纸强制锁定主题的场景，导航栏仍跟随应用设置） */
fun Activity.applyThemeAwareSystemBars(isDark: Boolean) {
    window.decorView.post {
        window.insetsController?.setSystemBarsAppearance(
            if (isDark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )
    }
}

/** 导航栏图标外观：始终跟随应用设置（theme_mode），不随壁纸强制主题变化 */
fun Activity.applyNavigationBarIsDark(isDark: Boolean) {
    window.decorView.post {
        window.insetsController?.setSystemBarsAppearance(
            if (isDark) 0 else WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
    }
}
