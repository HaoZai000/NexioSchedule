package com.haooz.chedule.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.utils.isAppDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 节次列（左侧显示节次号和时间）
 */
@Composable
fun SectionColumn(
    totalSections: Int = 11,
    morningSections: Int = 4,
    afternoonSections: Int = 4,
    eveningSections: Int = 3,
    sectionTimes: Map<Int, String> = Course.defaultSectionTimes,
    cardHeightPerSection: Float = 54f,
    cardBlurRadius: Float = 0f,
    showBreakDividers: Boolean = true,
    currentSection: Int = -1,
    isTablet: Boolean = false,
    hasWallpaper: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    // 缓存时间字符串拆分结果，避免每次重组重复 split
    val timePairs = remember(sectionTimes, totalSections) {
        (1..totalSections).map { section ->
            val timeStr = sectionTimes[section] ?: Course.defaultSectionTimes[section] ?: ""
            val parts = timeStr.split("-")
            (parts.firstOrNull() ?: "") to (parts.lastOrNull() ?: "")
        }
    }

    val totalHeight = (totalSections * cardHeightPerSection + (if (showBreakDividers) 24 * 2 else 0)).toInt()

    val sectionWidth = if (isTablet) 56.dp else 36.dp

    Box(
        modifier = modifier
            .width(sectionWidth)
            .height(totalHeight.dp)
    ) {
        var currentOffset = 0

        // 上午节次
        (1..morningSections).forEach { section ->
            val (startTime, endTime) = timePairs[section - 1]
            SectionItem(section, startTime, endTime, currentOffset, cardHeightPerSection, section == currentSection, hasWallpaper)
            currentOffset += cardHeightPerSection.toInt()
        }

        // 午休分界线
        val dividerColor = if (cardBlurRadius > 0f) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
        if (showBreakDividers && !isTablet) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .offset(y = currentOffset.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(dividerColor)
                )
            }
        }
        currentOffset += if (showBreakDividers) 24 else 0

        // 下午节次
        val afternoonStart = morningSections + 1
        val afternoonEnd = morningSections + afternoonSections
        (afternoonStart..afternoonEnd).forEach { section ->
            val (startTime, endTime) = timePairs[section - 1]
            SectionItem(section, startTime, endTime, currentOffset, cardHeightPerSection, section == currentSection, hasWallpaper)
            currentOffset += cardHeightPerSection.toInt()
        }

        // 晚休分界线
        if (showBreakDividers && !isTablet) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .offset(y = currentOffset.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(dividerColor)
                )
            }
        }
        currentOffset += if (showBreakDividers) 24 else 0

        // 晚上节次
        val eveningStart = morningSections + afternoonSections + 1
        val eveningEnd = morningSections + afternoonSections + eveningSections
        (eveningStart..eveningEnd).forEach { section ->
            val (startTime, endTime) = timePairs[section - 1]
            SectionItem(section, startTime, endTime, currentOffset, cardHeightPerSection, section == currentSection, hasWallpaper)
            currentOffset += cardHeightPerSection.toInt()
        }
    }
}

@Composable
private fun SectionItem(section: Int, startTime: String, endTime: String, yOffset: Int, cardHeightPerSection: Float = 54f, isCurrentSection: Boolean = false, hasWallpaper: Boolean = false) {
    val primaryColor = MiuixTheme.colorScheme.primary
    val onSurfaceColor = MiuixTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MiuixTheme.colorScheme.onSurfaceVariantActions

    val sectionStyle = MiuixTheme.textStyles.body2.copy(
        fontWeight = if (isCurrentSection) FontWeight.Medium else FontWeight.Normal
    )
    val sectionColor = if (isCurrentSection) primaryColor else onSurfaceColor
    val timeStyle = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp)
    val timeColor = if (isCurrentSection) primaryColor else onSurfaceVariantColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeightPerSection.dp)
            .offset(y = yOffset.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedText("$section", sectionStyle, sectionColor, hasWallpaper)
            OutlinedText(startTime, timeStyle, timeColor, hasWallpaper)
            OutlinedText(endTime, timeStyle, timeColor, hasWallpaper)
        }
    }
}

/**
 * 节次文字：设置壁纸时叠加柔和描边（渐变光晕）以保证可读性。
 * 通过多层 Stroke 叠加实现：外层宽且透明、内层窄且不透明，
 * 形成从字形边缘向外渐淡的过渡，比硬描边更自然。
 * 描边颜色按主题取反色（亮色模式白、暗色模式黑）。
 */
@Composable
private fun OutlinedText(
    text: String,
    style: TextStyle,
    color: Color,
    hasWallpaper: Boolean
) {
    if (hasWallpaper) {
        val isDark = isAppDarkTheme()
        val outlineColor = if (isDark) Color.Black else Color.White
        val density = LocalDensity.current
        // 从外到内：宽度递减、透明度递增，形成柔和渐变描边
        val layers = remember(density) {
            listOf(
                1.5.dp to 0.1f,
                1.dp to 0.16f,
                0.5.dp to 0.20f
            ).map { (w, a) ->
                with(density) { w.toPx() } to a
            }
        }
        Box {
            layers.forEach { (widthPx, alpha) ->
                Text(
                    text = text,
                    style = style.copy(drawStyle = Stroke(width = widthPx)),
                    color = outlineColor.copy(alpha = alpha)
                )
            }
            // 填充层：覆盖在描边之上
            Text(
                text = text,
                style = style,
                color = color
            )
        }
    } else {
        Text(
            text = text,
            style = style,
            color = color
        )
    }
}
