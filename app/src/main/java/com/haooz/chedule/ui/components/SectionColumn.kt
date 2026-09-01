package com.haooz.chedule.ui.components

import android.annotation.SuppressLint
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
import androidx.compose.ui.graphics.Shadow
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
    sectionNames: Map<Int, String> = emptyMap(),
    specialBlocks: List<com.haooz.chedule.data.SpecialBlock> = emptyList(),
    cardHeightPerSection: Float = 54f,
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

    // 特殊课程为时间轴浮层：节次保持固定位置，时间列在对应高度显示特殊课程起止时间
    val grid = remember(
        totalSections, morningSections, afternoonSections, eveningSections,
        specialBlocks, sectionTimes, cardHeightPerSection, showBreakDividers
    ) {
        computeSpecialGridLayout(
            morningSections = morningSections,
            afternoonSections = afternoonSections,
            eveningSections = eveningSections,
            specialBlocks = specialBlocks,
            sectionTimes = sectionTimes,
            cardHeightPerSection = cardHeightPerSection,
            dividerGap = if (showBreakDividers) 24 else 0
        )
    }
    val totalHeight = grid.totalHeight.toInt()

    val sectionWidth = if (isTablet) 56.dp else 36.dp

    Box(
        modifier = modifier
            .width(sectionWidth)
            .height(totalHeight.dp)
    ) {
        // 上午节次
        (1..morningSections).forEach { section ->
            val (startTime, endTime) = timePairs[section - 1]
            SectionItem(section, startTime, endTime, grid.sectionTop[section]?.toInt() ?: 0, cardHeightPerSection, section == currentSection, hasWallpaper, sectionNames)
        }

        // 下午节次
        val afternoonStart = morningSections + 1
        val afternoonEnd = morningSections + afternoonSections
        (afternoonStart..afternoonEnd).forEach { section ->
            val (startTime, endTime) = timePairs[section - 1]
            SectionItem(section, startTime, endTime, grid.sectionTop[section]?.toInt() ?: 0, cardHeightPerSection, section == currentSection, hasWallpaper, sectionNames)
        }

        // 晚上节次
        val eveningStart = morningSections + afternoonSections + 1
        val eveningEnd = morningSections + afternoonSections + eveningSections
        (eveningStart..eveningEnd).forEach { section ->
            val (startTime, endTime) = timePairs[section - 1]
            SectionItem(section, startTime, endTime, grid.sectionTop[section]?.toInt() ?: 0, cardHeightPerSection, section == currentSection, hasWallpaper, sectionNames)
        }

        // 特殊课程：无编号，左侧时间列在对应高度显示其起止时间
        grid.specialBands.forEach { band ->
            SpecialTimeLabel(
                startTime = band.startTime,
                endTime = band.endTime,
                top = band.top,
                height = band.height,
                hasWallpaper = hasWallpaper,
                sectionWidth = sectionWidth
            )
        }
    }
}

@Composable
private fun SectionItem(section: Int, startTime: String, endTime: String, yOffset: Int, cardHeightPerSection: Float = 54f, isCurrentSection: Boolean = false, hasWallpaper: Boolean = false, sectionNames: Map<Int, String> = emptyMap()) {
    val primaryColor = MiuixTheme.colorScheme.primary
    val onSurfaceColor = MiuixTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MiuixTheme.colorScheme.onSurfaceVariantActions
    val baseBody2 = MiuixTheme.textStyles.body2
    val baseFootnote2 = MiuixTheme.textStyles.footnote2

    // 自定义节次名称优先显示名称，否则显示节次号
    val customName = sectionNames[section]
    val displayText = customName ?: section.toString()

    val sectionStyle = remember(isCurrentSection, baseBody2) {
        baseBody2.copy(
            fontWeight = if (isCurrentSection) FontWeight.Medium else FontWeight.Normal
        )
    }
    // 自定义节次名称使用小号 Medium 字重，避免超出列宽
    val nameStyle = if (customName != null) {
        sectionStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    } else sectionStyle
    val sectionColor = if (isCurrentSection) primaryColor else onSurfaceColor
    val timeStyle = remember(baseFootnote2) { baseFootnote2.copy(fontSize = 10.sp) }
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
            OutlinedText(displayText, nameStyle, sectionColor, hasWallpaper)
            OutlinedText(startTime, timeStyle, timeColor, hasWallpaper)
            OutlinedText(endTime, timeStyle, timeColor, hasWallpaper)
        }
    }
}

/**
 * 特殊课程左侧时间标签：在时间轴对应高度（top..top+height）显示起止时间，无节次号。
 */
@Composable
private fun SpecialTimeLabel(
    startTime: String,
    endTime: String,
    top: Float,
    height: Float,
    hasWallpaper: Boolean,
    sectionWidth: androidx.compose.ui.unit.Dp
) {
    val baseFootnote2 = MiuixTheme.textStyles.footnote2
    val timeStyle = remember(baseFootnote2) { baseFootnote2.copy(fontSize = 10.sp) }
    val timeColor = MiuixTheme.colorScheme.onSurfaceVariantActions
    Box(
        modifier = Modifier
            .width(sectionWidth)
            .height(height.dp)
            .offset(y = top.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedText(startTime, timeStyle, timeColor, hasWallpaper)
            OutlinedText(endTime, timeStyle, timeColor, hasWallpaper)
        }
    }
}

/**
 * 节次文字：设置壁纸时叠加柔和阴影以保证可读性。
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
        val shadowColor = if (isDark) Color.Black else Color.White
        val shadowStyle = remember(style, isDark) {
            style.copy(
                shadow = Shadow(
                    color = shadowColor.copy(alpha = 0.92f),
                    blurRadius = 12f
                )
            )
        }
        Text(
            text = text,
            style = shadowStyle,
            color = color
        )
    } else {
        Text(
            text = text,
            style = style,
            color = color
        )
    }
}
