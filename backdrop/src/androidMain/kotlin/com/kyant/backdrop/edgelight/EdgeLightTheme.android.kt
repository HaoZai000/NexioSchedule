package com.kyant.backdrop.edgelight

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * Android 实现 —— 读系统深浅色。
 *
 * 注意：原来这里读的是 App 的 theme_mode 偏好（App 允许「跟随系统 / 强制浅 / 强制深」），
 * 而不是系统值。搬进 backdrop 后拿不到 SharedPreferences，所以 :app 侧改为显式传参
 * （rememberDefaultEdgeLight(isLightTheme = !isAppDarkTheme(), ...)），
 * 保证 App 内的观感与迁移前一致；这个默认值只服务于不传参的新调用方。
 */
@Composable
actual fun isSystemLightTheme(): Boolean = !isSystemInDarkTheme()
