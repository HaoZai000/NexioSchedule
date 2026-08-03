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
            width: Dp = 0.32.dp,
            blurRadius: Dp = 1.24.dp,
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
    }
}

@Composable
fun rememberDefaultEdgeLight(): EdgeLight {
    val isLightTheme = !isAppDarkTheme()
    val color = if (isLightTheme) Color.White.copy(alpha = 0.5f)
                else Color.White.copy(alpha = 1f)
    return remember(isLightTheme) { EdgeLight.Uniform(color = color) }
}
