package com.haooz.chedule.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.LocalMonetColorEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置入口图标底色：固定常量，不随深浅模式变化。 */
object SettingsAccents {
    val Blue = Color(0xFF3482FF)
    val Green = Color(0xFF1DCD3A)
    val Purple = Color(0xFF7767F9)
    val Red = Color(0xFFFA382E)
    val Orange = Color(0xFFFF8A00)
    val Gray = Color(0xFF8C9DB0)
}

/** 开启莫奈取色时统一走 primary 底 + onPrimary 字形，否则用色板常量 + 白字。 */
@Stable
class SettingsAccentResolver(
    private val monet: Boolean,
    private val primary: Color,
    private val onPrimary: Color,
) {
    fun background(accent: Color): Color = if (monet) primary else accent

    fun foreground(accent: Color): Color = if (monet) onPrimary else Color.White
}

@Composable
fun rememberSettingsAccentResolver(): SettingsAccentResolver {
    val monet = LocalMonetColorEnabled.current
    val scheme = MiuixTheme.colorScheme
    return remember(monet, scheme.primary, scheme.onPrimary) {
        SettingsAccentResolver(
            monet = monet,
            primary = scheme.primary,
            onPrimary = scheme.onPrimary,
        )
    }
}
