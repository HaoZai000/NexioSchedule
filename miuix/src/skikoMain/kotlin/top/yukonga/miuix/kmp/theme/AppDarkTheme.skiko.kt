package top.yukonga.miuix.kmp.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * Skia（iOS / Desktop / wasm）实现 —— 退回系统深浅色。
 *
 * iOS 接入时应改为读 NSUserDefaults 里的 theme_mode（dark / light / system），
 * 语义与 Android 侧一致；在此之前跟随系统即可，不会出现「无法编译」或反色。
 */
@Composable
actual fun rememberAppSettingDark(): Boolean = isSystemInDarkTheme()