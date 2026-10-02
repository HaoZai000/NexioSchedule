package com.haooz.chedule.ui.effects.edgelight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.utils.isAppDarkTheme

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

@Composable
fun rememberDefaultEdgeLight(baseColor: Color? = null): EdgeLight {
    val isLightTheme = !isAppDarkTheme()
    val materialLevel = com.haooz.chedule.ui.utils.AppMaterialSettings.level
    val color = if (isLightTheme) Color.White.copy(alpha = 0.6f)
                else Color.White.copy(alpha = 0.2f)
    // 深色比浅色描边略粗、略柔
    val width = if (isLightTheme) 0.24.dp else 0.34.dp
    val blurRadius = if (isLightTheme) 0.4.dp else 0.4.dp
    return remember(isLightTheme, materialLevel, baseColor) {
        com.haooz.chedule.ui.utils.AppMaterialSettings.resolveEdgeLight(
            EdgeLight.Uniform(color = color, width = width, blurRadius = blurRadius),
            isLightTheme,
            baseColor,
        )
    }
}

@Composable
fun rememberCourseCardEdgeLight(baseColor: Color? = null): EdgeLight {
    val isLightTheme = !isAppDarkTheme()
    val materialLevel = com.haooz.chedule.ui.utils.AppMaterialSettings.level
    val color = if (isLightTheme) Color.White.copy(alpha = 0.12f)
                else Color.White.copy(alpha = 0.12f)
    return remember(isLightTheme, materialLevel, baseColor) {
        com.haooz.chedule.ui.utils.AppMaterialSettings.resolveEdgeLight(
            EdgeLight.CourseCard(color = color),
            isLightTheme,
            baseColor,
        )
    }
}

@Composable
fun rememberCardEdgeLight(baseColor: Color? = null): EdgeLight {
    val isLightTheme = !isAppDarkTheme()
    val materialLevel = com.haooz.chedule.ui.utils.AppMaterialSettings.level
    val color = if (isLightTheme) Color.White.copy(alpha = 0.2f)
    else Color.White.copy(alpha = 0.2f)
    return remember(isLightTheme, materialLevel, baseColor) {
        com.haooz.chedule.ui.utils.AppMaterialSettings.resolveEdgeLight(
            EdgeLight.Card(color = color),
            isLightTheme,
            baseColor,
        )
    }
}