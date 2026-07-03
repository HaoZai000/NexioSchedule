/** 主题工具类 - 管理深色模式切换和状态栏样式 */
package com.haooz.chedule

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
import androidx.compose.ui.platform.LocalContext

@Composable
fun isAppDarkTheme(): Boolean {
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

    window.decorView.post {
        window.insetsController?.let { controller ->
            val appearance = if (isDark) 0 else (
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
            controller.setSystemBarsAppearance(appearance,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        }
    }
}
