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

// 壁纸强制主题：非 null 时 isAppDarkTheme 直接用该值，今日页/课程表页按壁纸亮暗锁定
val LocalForcedDarkTheme = staticCompositionLocalOf<Boolean?> { null }

@Composable
fun isAppDarkTheme(): Boolean {
    LocalForcedDarkTheme.current?.let { return it }
    return rememberAppSettingDark()
}

// 不经过壁纸强制覆盖，只读 theme_mode
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

// 仅影响今日页/课程表页，与全局 theme_mode 隔离
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
    // 状态栏与导航栏均跟随应用设置，不受壁纸强制主题影响
    applyThemeAwareSystemBars(isDark)
    applyNavigationBarIsDark(isDark)
}

// 按显式深色值刷新状态栏；导航栏仍跟随应用设置
fun Activity.applyThemeAwareSystemBars(isDark: Boolean) {
    window.decorView.post {
        window.insetsController?.setSystemBarsAppearance(
            if (isDark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )
    }
}

// 导航栏图标始终跟随 theme_mode，不随壁纸强制主题变化
fun Activity.applyNavigationBarIsDark(isDark: Boolean) {
    window.decorView.post {
        window.insetsController?.setSystemBarsAppearance(
            if (isDark) 0 else WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
    }
}
