// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.basic

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.AppThemeSnapshot
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A [SmallTitle] with Miuix style.
 *
 * @param text The text to be displayed in the [SmallTitle].
 * @param modifier The modifier to be applied to the [SmallTitle].
 * @param textColor The color of the [SmallTitle].
 * @param insideMargin The margin inside the [SmallTitle].
 */
@Composable
@NonRestartableComposable
fun SmallTitle(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFF8F9CAE),
    insideMargin: PaddingValues = SmallTitleDefaults.InsideMargin,
    hasWallpaper: Boolean = false,
) {
    val baseStyle = MiuixTheme.textStyles.subtitle.copy(fontWeight = FontWeight.Medium)
    val style = if (hasWallpaper) {
        // 原来读 App 的 isAppDarkTheme()；这里改用 backdrop 的绘制期主题快照，
        // 由 App 在组合期写入（值与 isAppDarkTheme 相同，行为不变）。
        val isDark = AppThemeSnapshot.isDark.value
        val shadowColor = if (isDark) Color.Black else Color.White
        remember(true, isDark) {
            baseStyle.copy(
                shadow = Shadow(
                    color = shadowColor.copy(alpha = 0.92f),
                    blurRadius = 12f
                )
            )
        }
    } else baseStyle
    Text(
        modifier = modifier.padding(insideMargin),
        text = text,
        style = style,
        color = textColor,
    )
}

/** Contains default values used by [SmallTitle]. */
object SmallTitleDefaults {
    /**
     * The default inside margin of the [SmallTitle].
     *
     * 水平留白只保留 12.dp：调用方的列表一般已有 16.dp(手机)/20.dp(平板) 的 contentPadding，
     * 叠加后正好是 MIUI 小标题的 28.dp/32.dp 起始位置，调用处无需再手动 offset 负值做补偿。
     */
    val InsideMargin = PaddingValues(12.dp, 8.dp)
}
