package com.kyant.backdrop.edgelight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class EdgeLight(
    val width: Dp = 1.dp,
    val blurRadius: Dp = 2.dp,
    val intensity: Float = 1f,
    val style: EdgeLightStyle = EdgeLightStyle.Default
) {
    companion object {

        @Stable
        val Default: EdgeLight = EdgeLight()

        @Stable
        val Subtle: EdgeLight = EdgeLight(
            width = 0.5f.dp,
            blurRadius = 1.dp,
            intensity = 0.6f
        )

        @Stable
        val Prominent: EdgeLight = EdgeLight(
            width = 2.dp,
            blurRadius = 4.dp,
            intensity = 1f
        )

        @Stable
        fun Uniform(
            color: Color = Color.White.copy(alpha = 0.5f),
            width: Dp = 0.28.dp,
            blurRadius: Dp = 0.8.dp,
            intensity: Float = 1f
        ): EdgeLight = EdgeLight(
            width = width,
            blurRadius = blurRadius,
            intensity = intensity,
            style = EdgeLightStyle.Uniform(color = color)
        )

        @Stable
        fun Directional(
            color: Color = Color.White.copy(alpha = 0.5f),
            width: Dp = 1.dp,
            blurRadius: Dp = 2.dp,
            intensity: Float = 1f,
            angle: Float = 45f,
            falloff: Float = 1f
        ): EdgeLight = EdgeLight(
            width = width,
            blurRadius = blurRadius,
            intensity = intensity,
            style = EdgeLightStyle.Directional(color = color, angle = angle, falloff = falloff)
        )

        @Stable
        fun Glow(
            color: Color = Color.White.copy(alpha = 0.5f),
            width: Dp = 1.dp,
            blurRadius: Dp = 2.dp,
            intensity: Float = 1f,
            glowSize: Float = 10f
        ): EdgeLight = EdgeLight(
            width = width,
            blurRadius = blurRadius,
            intensity = intensity,
            style = EdgeLightStyle.Glow(color = color, glowSize = glowSize)
        )

        @Stable
        fun CourseCard(
            color: Color = Color.White.copy(alpha = 0.5f),
            width: Dp = 0.36.dp,
            blurRadius: Dp = 1.26.dp,
            intensity: Float = 0.52f,
        ): EdgeLight = Uniform(
            color = color,
            width = width,
            blurRadius = blurRadius,
            intensity = intensity
        )

        @Stable
        fun Card(
            color: Color = Color.White.copy(alpha = 0.5f),
            width: Dp = 0.35.dp,
            blurRadius: Dp = 1.24.dp,
            intensity: Float = 0.5f
        ): EdgeLight = Uniform(
            color = color,
            width = width,
            blurRadius = blurRadius,
            intensity = intensity
        )
    }
}

// 以下便捷函数把「主题亮暗」与「材质档解析」交给调用方传：backdrop 不能反向依赖业务层，由 :app / KMP 共享层各自提供实现。
@Composable
fun rememberDefaultEdgeLight(
    baseColor: Color? = null,
    isLightTheme: Boolean = isSystemLightTheme(),
    resolveMaterial: (EdgeLight, Boolean, Color?) -> EdgeLight = { base, _, _ -> base },
): EdgeLight {
    // 深色比浅色描边略粗
    val width = if (isLightTheme) 0.26.dp else 0.4.dp
    val blurRadius = 0.8.dp
    return remember(isLightTheme, baseColor) {
        resolveMaterial(
            uniformHighlight(
                baseColor = baseColor,
                alpha = if (isLightTheme) HIGHLIGHT_ALPHA_LIGHT else HIGHLIGHT_ALPHA_DARK,
                width = width,
                blurRadius = blurRadius,
                intensity = 1f,
                isLightTheme = isLightTheme,
            ),
            isLightTheme,
            baseColor,
        )
    }
}

/**
 * 高光描边色 = 底色的「满饱和 + 明度封顶」版本
 */
private fun highlightTint(baseColor: Color?, alpha: Float): Color {
    if (baseColor == null || baseColor.alpha <= 0f) return Color.White.copy(alpha = alpha)
    val v = maxOf(baseColor.red, maxOf(baseColor.green, baseColor.blue))
    val minC = minOf(baseColor.red, minOf(baseColor.green, baseColor.blue))
    val saturation = if (v > 0f) (v - minC) / v else 0f
    if (saturation < NEUTRAL_SATURATION_EPS) return Color.White.copy(alpha = alpha)
    // 提饱和后把明度等比缩到 HIGHLIGHT_VALUE（等比缩放不影响饱和度）
    fun boost(channel: Float) =
        ((v - (v - channel) * SATURATION_BOOST) / v * HIGHLIGHT_VALUE).coerceIn(0f, 1f)
    val vivid = Color(
        red = boost(baseColor.red),
        green = boost(baseColor.green),
        blue = boost(baseColor.blue),
    )
    return if (WHITE_MIX <= 0f) vivid.copy(alpha = alpha)
    else lerp(vivid, Color.White, WHITE_MIX).copy(alpha = alpha)
}

/** 描边色相对底色的饱和度倍数（超过 `1/底色S` 后只变暗，不会更饱和） */
private const val SATURATION_BOOST = 2.5f
/**
 * 默认高光描边 alpha（浅 / 深）
 */
private const val HIGHLIGHT_ALPHA_LIGHT = 0.24f
private const val HIGHLIGHT_ALPHA_DARK = 0.2f
/** 描边色的明度上限（0..1）：越低越浓越暗，越高越亮、越容易在 Screen 下算成白 */
private const val HIGHLIGHT_VALUE = 0.4f
/** 提饱和后向白回调的比例：0 = 完全不兑白（最浓） */
private const val WHITE_MIX = 0f
/** 低于此饱和度视为中性色，不给描边染色 */
private const val NEUTRAL_SATURATION_EPS = 0.02f

/**
 * 均匀描边。混合模式须为提亮型并按主题分工：深色用 `Screen`（只提亮、保留色相），浅色用 `Plus`（浅底近 1.0，Screen 几乎无效，加色才顶得出来）。
 * ⚠ `Color`/`Hue`/`Saturation` 不可用：描边色取自 baseColor，这类混合 = 把面板明度+色相再画一遍，与底色同色 → 整圈消失。
 * ⚠ `Overlay` 也不可用：暗底走乘法分支 2·b·s，同样会被压到和底色一样暗。
 */
private fun uniformHighlight(
    baseColor: Color?,
    alpha: Float,
    width: Dp,
    blurRadius: Dp,
    intensity: Float,
    isLightTheme: Boolean,
): EdgeLight = EdgeLight(
    width = width,
    blurRadius = blurRadius,
    intensity = intensity,
    style = EdgeLightStyle.Uniform(
        color = highlightTint(baseColor, alpha),
        blendMode = if (isLightTheme) BlendMode.Plus else BlendMode.Screen,
    ),
)


@Composable
fun rememberCardEdgeLight(
    baseColor: Color? = null,
    isLightTheme: Boolean = isSystemLightTheme(),
    resolveMaterial: (EdgeLight, Boolean, Color?) -> EdgeLight = { base, _, _ -> base },
): EdgeLight {
    return remember(isLightTheme, baseColor) {
        resolveMaterial(
            uniformHighlight(baseColor, 0.2f, 0.35.dp, 1.24.dp, 0.5f, isLightTheme),
            isLightTheme,
            baseColor,
        )
    }
}

/** 主题亮暗的默认值来源；KMP 共享层可覆盖自己的实现。 */
@Composable
expect fun isSystemLightTheme(): Boolean