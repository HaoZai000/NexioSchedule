/** 应用主题 - 定义 Material3 主题配色方案 */
package com.haooz.chedule.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 应用触感反馈开关对应的 SharedPreferences 文件与键 */
const val APP_PREFS_NAME = "app_preferences"
const val KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled"

@Composable
fun CourseScheduleTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE) }
    val themeMode = remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }

    // 全局触感反馈开关：读取应用偏好，关闭后在整个 App 范围内屏蔽所有触感/震动
    // （本项目及 Miuix 组件的触感均通过 LocalHapticFeedback 触发，此处统一拦截即可全局生效）
    val hapticPrefs = remember { context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE) }
    var hapticFeedbackEnabled by remember {
        mutableStateOf(hapticPrefs.getBoolean(KEY_HAPTIC_FEEDBACK, true))
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_mode") {
                themeMode.value = prefs.getString("theme_mode", "system") ?: "system"
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    DisposableEffect(hapticPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_HAPTIC_FEEDBACK) {
                hapticFeedbackEnabled = hapticPrefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
            }
        }
        hapticPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            hapticPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val controller = remember(themeMode.value) {
        ThemeController(
            when (themeMode.value) {
                "light" -> ColorSchemeMode.Light
                "dark" -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            }
        )
    }
    // 捕获当前（系统默认）触感实现，封装为受开关控制的门控实现
    val defaultHaptic = LocalHapticFeedback.current
    val gatedHaptic = remember(defaultHaptic) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (hapticFeedbackEnabled) {
                    defaultHaptic.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
    MiuixTheme(
        controller = controller,
        content = {
            CompositionLocalProvider(LocalHapticFeedback provides gatedHaptic) {
                content()
            }
        }
    )
}
