package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.SpecialBlock
import com.haooz.chedule.ui.components.text.LocalTextMotionInstant
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.theme.scheduleAccentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 左侧时间列宽度。横带需要留出同样宽度才能与 [DayColumn] 逐列对齐。 */
@Composable
fun scheduleSectionColumnWidth(isTablet: Boolean): Dp = if (isTablet) 56.dp else 36.dp

/**
 * 课表左侧节次时间列。
 *
 * [currentSectionProvider] 以 lambda 传入，让「当前节次」在列内订阅，避免整页网格重组；
 * [grid] 由页面层共享，独立使用时可留空自算。
 */
@Composable
fun SectionColumn(
    totalSections: Int = 11,
    morningSections: Int = 4,
    afternoonSections: Int = 4,
    eveningSections: Int = 3,
    sectionTimes: Map<Int, String> = Course.defaultSectionTimes,
    sectionNames: Map<Int, String> = emptyMap(),
    specialBlocks: List<SpecialBlock> = emptyList(),
    grid: SpecialGridLayout? = null,
    cardHeightPerSection: Float = 54f,
    showBreakDividers: Boolean = true,
    currentSectionProvider: (() -> Int)? = null,
    isTablet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val currentSection = currentSectionProvider?.invoke() ?: -1
    val timePairs = remember(sectionTimes, totalSections) {
        (1..totalSections).map { section ->
            val raw = sectionTimes[section] ?: Course.defaultSectionTimes[section] ?: ""
            val parts = raw.split("-")
            (parts.firstOrNull() ?: "") to (parts.lastOrNull() ?: "")
        }
    }

    val effectiveGrid = grid ?: remember(
        totalSections, morningSections, afternoonSections, eveningSections,
        specialBlocks, sectionTimes, cardHeightPerSection, showBreakDividers,
    ) {
        computeSpecialGridLayout(
            morningSections = morningSections,
            afternoonSections = afternoonSections,
            eveningSections = eveningSections,
            specialBlocks = specialBlocks,
            sectionTimes = sectionTimes,
            cardHeightPerSection = cardHeightPerSection,
            dividerGap = if (showBreakDividers) 24 else 0,
        )
    }

    val sectionWidth = scheduleSectionColumnWidth(isTablet)

    // 时间列是静态标签：整列关掉文字动效机器，省下 节次数×3 个动画循环
    CompositionLocalProvider(LocalTextMotionInstant provides true) {
        Box(
            modifier = modifier
                .width(sectionWidth)
                .height(effectiveGrid.totalHeight.toInt().dp)
        ) {
            for (section in 1..totalSections) {
                SectionItem(
                    section = section,
                    timePairs = timePairs,
                    grid = effectiveGrid,
                    cardHeightPerSection = cardHeightPerSection,
                    currentSection = currentSection,
                    sectionNames = sectionNames,
                )
            }
            effectiveGrid.specialBands.forEach { band ->
                SpecialTimeLabel(
                    startTime = band.startTime,
                    endTime = band.endTime,
                    top = band.top,
                    height = band.height,
                    sectionWidth = sectionWidth,
                )
            }
        }
    }
}

@Composable
private fun SectionItem(
    section: Int,
    timePairs: List<Pair<String, String>>,
    grid: SpecialGridLayout,
    cardHeightPerSection: Float,
    currentSection: Int,
    sectionNames: Map<Int, String>,
) {
    val (startTime, endTime) = timePairs[section - 1]
    val isCurrent = section == currentSection
    val highlightColor = scheduleAccentColor()
    val onSurfaceColor = MiuixTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MiuixTheme.colorScheme.onSurfaceVariantActions
    val baseBody2 = MiuixTheme.textStyles.body2
    val baseFootnote2 = MiuixTheme.textStyles.footnote2

    val customName = sectionNames[section]
    val sectionStyle = remember(isCurrent, baseBody2) {
        baseBody2.copy(fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal)
    }
    // 自定义名称用小号 Medium，避免超出列宽
    val nameStyle = if (customName != null) {
        sectionStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    } else {
        sectionStyle
    }
    val timeStyle = remember(baseFootnote2) { baseFootnote2.copy(fontSize = 10.sp) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeightPerSection.dp)
            .offset(y = (grid.sectionTop[section] ?: 0f).toInt().dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = customName ?: section.toString(),
            style = nameStyle,
            color = if (isCurrent) highlightColor else onSurfaceColor,
            textAlign = TextAlign.Center,
        )
        Text(
            text = startTime,
            style = timeStyle,
            color = if (isCurrent) highlightColor else onSurfaceVariantColor,
            textAlign = TextAlign.Center,
        )
        Text(
            text = endTime,
            style = timeStyle,
            color = if (isCurrent) highlightColor else onSurfaceVariantColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SpecialTimeLabel(
    startTime: String,
    endTime: String,
    top: Float,
    height: Float,
    sectionWidth: Dp,
) {
    val baseFootnote2 = MiuixTheme.textStyles.footnote2
    val timeStyle = remember(baseFootnote2) { baseFootnote2.copy(fontSize = 10.sp) }
    val timeColor = MiuixTheme.colorScheme.onSurfaceVariantActions
    Box(
        modifier = Modifier
            .width(sectionWidth)
            .height(height.dp)
            .offset(y = top.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = startTime, style = timeStyle, color = timeColor, textAlign = TextAlign.Center)
            Text(text = endTime, style = timeStyle, color = timeColor, textAlign = TextAlign.Center)
        }
    }
}
