package top.yukonga.miuix.kmp.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android 实现 —— 读 App 的 SharedPreferences（app_theme_prefs / theme_mode）。
 *
 * 原先这段在 :app 的 ThemeUtils.kt 里，:miuix 的 BlurBottomSheet 直接调用它，
 * 构成反向依赖。搬进来后 :app 侧的 rememberAppSettingDark 改为转调本函数，
 * 单一实现、行为不变；Skia 侧另有一份退回系统深浅色的实现。
 */
@Composable
actual fun rememberAppSettingDark(): Boolean {
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