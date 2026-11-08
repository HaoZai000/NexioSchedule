package com.haooz.softglass.glass

import androidx.compose.ui.graphics.Color

/** 玻璃本色的生成策略。 */
sealed interface GlassFill {
    fun apply(style: SoftGlassStyle, isDark: Boolean): SoftGlassStyle
}

object NoneFill : GlassFill {
    override fun apply(style: SoftGlassStyle, isDark: Boolean): SoftGlassStyle = style
}

object ThemeFill : GlassFill {
    override fun apply(style: SoftGlassStyle, isDark: Boolean): SoftGlassStyle =
        style.filledWith(if (isDark) Color(0xFF181818) else Color.White, if (isDark) 0.45f else 0.55f)
}

object BarFill : GlassFill {
    override fun apply(style: SoftGlassStyle, isDark: Boolean): SoftGlassStyle =
        style.filledWith(if (isDark) Color(0xFF222222) else Color.White, if (isDark) 0.45f else 0.55f)
}

data class CustomizeFill(val color: Color) : GlassFill {
    override fun apply(style: SoftGlassStyle, isDark: Boolean): SoftGlassStyle =
        style.filledWith(color, if (isDark) 0.55f else 0.65f)
}

private fun SoftGlassStyle.filledWith(color: Color, mix: Float): SoftGlassStyle = copy(
    glassColor = color,
    unshade = mix.coerceIn(0f, 1f),
    saturation = minOf(saturation, 2f),
)
