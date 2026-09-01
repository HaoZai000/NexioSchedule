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
import com.haooz.chedule.ui.utils.isAppDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A [SmallTitle] with Miuix style.
 *
 * @param text The text to be displayed in the [SmallTitle].
 * @param modifier The modifier to be applied to the [SmallTitle].
 * @param textColor The color of the [SmallTitle].
 * @param insideMargin The margin inside the [SmallTitle].
 * @param hasWallpaper 是否叠加与课表节次一致的柔和阴影（有壁纸时保证可读性）。
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
        val isDark = isAppDarkTheme()
        val shadowColor = if (isDark) Color.Black else Color.White
        remember(hasWallpaper, isDark) {
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
    /** The default inside margin of the [SmallTitle]. */
    val InsideMargin = PaddingValues(28.dp, 8.dp)
}
