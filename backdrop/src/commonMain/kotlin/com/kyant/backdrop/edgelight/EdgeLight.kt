package com.kyant.backdrop.edgelight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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

// 以下三个便捷函数原先直接读 App 的 isAppDarkTheme() 与 AppMaterialSettings，
// 那让整个文件无法进 commonMain（backdrop 不能反向依赖业务层）。
// 现在把「主题亮暗」与「材质等级解析」都作为参数交给调用方传：
//   - :app 侧照旧传自己的实现，行为与迁移前完全一致；
//   - iOS 侧由 KMP 共享层提供自己的取值，不必依赖 Android 的偏好设置。
@Composable
fun rememberDefaultEdgeLight(
    baseColor: Color? = null,
    isLightTheme: Boolean = isSystemLightTheme(),
    resolveMaterial: (EdgeLight, Boolean, Color?) -> EdgeLight = { base, _, _ -> base },
): EdgeLight {
    val color = if (isLightTheme) Color.White.copy(alpha = 0.6f)
                else Color.White.copy(alpha = 0.2f)
    // 深色比浅色描边略粗、略柔
    val width = if (isLightTheme) 0.24.dp else 0.34.dp
    val blurRadius = 0.4.dp
    return remember(isLightTheme, baseColor) {
        resolveMaterial(
            EdgeLight.Uniform(color = color, width = width, blurRadius = blurRadius),
            isLightTheme,
            baseColor,
        )
    }
}

@Composable
fun rememberCourseCardEdgeLight(
    baseColor: Color? = null,
    isLightTheme: Boolean = isSystemLightTheme(),
    resolveMaterial: (EdgeLight, Boolean, Color?) -> EdgeLight = { base, _, _ -> base },
): EdgeLight {
    val color = Color.White.copy(alpha = 0.12f)
    return remember(isLightTheme, baseColor) {
        resolveMaterial(EdgeLight.CourseCard(color = color), isLightTheme, baseColor)
    }
}

@Composable
fun rememberCardEdgeLight(
    baseColor: Color? = null,
    isLightTheme: Boolean = isSystemLightTheme(),
    resolveMaterial: (EdgeLight, Boolean, Color?) -> EdgeLight = { base, _, _ -> base },
): EdgeLight {
    val color = Color.White.copy(alpha = 0.2f)
    return remember(isLightTheme, baseColor) {
        resolveMaterial(EdgeLight.Card(color = color), isLightTheme, baseColor)
    }
}

/** 主题亮暗的默认值来源；KMP 共享层可覆盖自己的实现。 */
@Composable
expect fun isSystemLightTheme(): Boolean