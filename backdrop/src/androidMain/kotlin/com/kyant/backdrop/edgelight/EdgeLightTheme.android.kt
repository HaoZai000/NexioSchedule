package com.kyant.backdrop.edgelight

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * Android 实现：读系统深浅色。
 * 迁进 backdrop 后无法访问 SharedPreferences，不再读 App 的 theme_mode 偏好；
 * 旧观感由 :app 显式传参（rememberDefaultEdgeLight(isLightTheme = !isAppDarkTheme(), ...)）保证。
 * 此默认值仅服务不传参的新调用方。
 */
@Composable
actual fun isSystemLightTheme(): Boolean = !isSystemInDarkTheme()
