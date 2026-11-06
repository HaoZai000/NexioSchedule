// Copyright (C) 2026 Haooz · AGPL-3.0-only
//
// 材质填充策略：决定玻璃本色的来源与混合强度。
//
// 策略以单例实现，可独立引用；[SoftGlassStyle.filled] 是唯一应用入口。

package com.haooz.chedule.ui.glass

import androidx.compose.ui.graphics.Color

/**
 * 材质填充（混合）策略：决定玻璃本色如何生成。
 *
 * 每种策略是一个独立单例。新增策略只需在本文件增加一个实现，
 * 无需修改 [SoftGlassStyle] 或任何调用点。
 *
 * 实现约定：返回新样式（[SoftGlassStyle] 不可变），不修改传入对象。
 */
sealed interface GlassFill {

    /**
     * 把填充应用到 [style] 上。
     *
     * @param style 待填充的样式。
     * @param isDark 是否处于深色主题；主题信息在应用策略时才确定。
     * @return 应用策略后的新样式。
     */
    fun apply(style: SoftGlassStyle, isDark: Boolean): SoftGlassStyle
}

/** 不填充：保留采样得到的玻璃本色。 */
object NoneFill : GlassFill {
    override fun apply(style: SoftGlassStyle, isDark: Boolean): SoftGlassStyle = style
}

/** 主题混合 */
object ThemeFill : GlassFill {
    override fun apply(style: SoftGlassStyle, isDark: Boolean): SoftGlassStyle =
        style.filledWith(
            color = if (isDark) Color(0xFF181818) else Color.White,
            mix = if (isDark) 0.45f else 0.55f,
        )
}

/** 底栏混合：浅色主题混入白色 0.8，深色主题混入 `#242424` 0.8。 */
object BarFill : GlassFill {
    override fun apply(style: SoftGlassStyle, isDark: Boolean): SoftGlassStyle =
        style.filledWith(
            color = if (isDark) Color(0xFF222222) else Color.White,
            mix = if (isDark) 0.45f else 0.55f,
        )
}

/** 自定义颜色混合 */
data class CustomizeFill(val color: Color) : GlassFill {
    override fun apply(style: SoftGlassStyle, isDark: Boolean): SoftGlassStyle =
        style.filledWith(
            color = color,
            mix = if (isDark) 0.45f else 0.55f,
        )
}

/**
 * 填充的公共实现：设置玻璃本色、混合比例，并限制饱和度。
 *
 * 混合比例写入 [SoftGlassStyle.unshade] 而非 `glassColorMix`：shader 末步为
 * `rgb = mix(rgb, glassColor, unshade)`，负责整体着色；`glassColorMix` 只影响 gcr 的底色，
 * 作用量级小得多。
 */
internal fun SoftGlassStyle.filledWith(color: Color, mix: Float): SoftGlassStyle = copy(
    glassColor = color,
    unshade = mix.coerceIn(0f, 1f),
    saturation = minOf(saturation, 2.0f),
)
