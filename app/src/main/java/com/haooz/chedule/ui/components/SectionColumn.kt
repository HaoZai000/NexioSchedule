package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.haooz.chedule.ui.utils.isAppDarkTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
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
    modifier: Modifier = Modifier
) {
    // 缓存时间字符串拆分结果，避免每次重组重复 split
    val timePairs = remember(sectionTimes, totalSections) {
        (1..totalSections).map { section ->
            val timeStr = sectionTimes[section] ?: Course.defaultSectionTimes[section] ?: ""
            val parts = timeStr.split("-")
            (parts.firstOrNull() ?: "") to (parts.lastOrNull() ?: "")
        }
    }

    val totalHeight = (totalSections * cardHeightPerSection + 24 * 2).toInt()

    Box(
        modifier = modifier
            .width(36.dp)
            .height(totalHeight.dp)
    ) {
        var currentOffset = 0

        // 上午节次
        (1..morningSections).forEach { section ->
            val (startTime, endTime) = timePairs[section - 1]
            SectionItem(section, startTime, endTime, currentOffset, cardHeightPerSection)
            currentOffset += cardHeightPerSection.toInt()
        }

        // 午休分界线
        val dividerColor = if (cardBlurRadius > 0f) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
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
        currentOffset += 24

        // 下午节次
        val afternoonStart = morningSections + 1
        val afternoonEnd = morningSections + afternoonSections
        (afternoonStart..afternoonEnd).forEach { section ->
            val (startTime, endTime) = timePairs[section - 1]
            SectionItem(section, startTime, endTime, currentOffset, cardHeightPerSection)
            currentOffset += cardHeightPerSection.toInt()
        }

        // 晚休分界线
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
        currentOffset += 24

        // 晚上节次
        val eveningStart = morningSections + afternoonSections + 1
        val eveningEnd = morningSections + afternoonSections + eveningSections
        (eveningStart..eveningEnd).forEach { section ->
            val (startTime, endTime) = timePairs[section - 1]
            SectionItem(section, startTime, endTime, currentOffset, cardHeightPerSection)
            currentOffset += cardHeightPerSection.toInt()
        }
    }
}

@Composable
private fun SectionItem(section: Int, startTime: String, endTime: String, yOffset: Int, cardHeightPerSection: Float = 54f) {
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
            Text(
                text = "$section",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = startTime,
                style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            Text(
                text = endTime,
                style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
        }
    }
}
