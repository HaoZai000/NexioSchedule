package com.haooz.chedule.ui.utils

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowInsetsController
import com.haooz.chedule.data.storage.AppStorage

// ⚠⚠ 文件名不要再改回 ThemeUtils.kt：:ui-shared 已有同包名的 ThemeUtils.kt，
// 同包同文件名在 Kotlin 里会生成同一个类 FQN（com.haooz.chedule.ui.utils.ThemeUtilsKt），
// 编译期各自能解析通过，但打 dex 时同名类互相覆盖，运行期表现为
// NoSuchMethodError: applyThemeAwareSystemBars（真机崩溃，编译看不出来）。

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
