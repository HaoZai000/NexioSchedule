package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.components.text.miuix.Text
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.weather.WeatherAlert
import com.haooz.chedule.weather.alertKind
import com.haooz.chedule.weather.alertLevelWord
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 气象预警徽标：等级色底 + 预警类型（「大风」「台风」）。
 *
 * 等级由底色与文字色共同表达，框内不重复写「黄色」。
 */
@Composable
fun WeatherAlertBadge(alert: WeatherAlert, modifier: Modifier = Modifier) {
    val (background, foreground) = alertBadgeColors(alertLevelWord(alert), isAppDarkTheme())
    Box(
        modifier = modifier
            .widthIn(max = 60.dp)
            .clip(ContinuousRoundedRectangle(4.dp))
            .background(background)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            text = alertKind(alert),
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.Medium,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 预警等级 → (底色, 文字色)。底色为等级色的半透明底，文字取同色系深/浅档。 */
private fun alertBadgeColors(level: String, isDark: Boolean): Pair<Color, Color> {
    val (seed, lightText, darkText) = when (level) {
        "红色" -> Triple(Color(0xFFD93025), Color(0xFFB3261E), Color(0xFFFF8A80))
        "橙色" -> Triple(Color(0xFFE8710A), Color(0xFF9A4B00), Color(0xFFFFB74D))
        "黄色" -> Triple(Color(0xFFF2C200), Color(0xFF6F5800), Color(0xFFFFE066))
        "蓝色" -> Triple(Color(0xFF2B6CF6), Color(0xFF1B4FD8), Color(0xFF90B4FF))
        else -> return if (isDark) {
            Color.White.copy(alpha = 0.10f) to Color.White.copy(alpha = 0.92f)
        } else {
            Color.Black.copy(alpha = 0.06f) to Color.Black.copy(alpha = 0.88f)
        }
    }
    val background = seed.copy(alpha = if (isDark) 0.24f else 0.14f)
    return background to if (isDark) darkText else lightText
}
