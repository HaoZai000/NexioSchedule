package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Calendar

/**
 * 星期表头
 */
@Composable
fun DayHeader(
    modifier: Modifier = Modifier
) {
    val days = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    // 将 Java Calendar 的星期转换为我们的格式 (1=周一, ..., 7=周日)
    val todayIndex = when (today) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> -1
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        // 左上角空白
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(40.dp)
        )

        // 星期标题
        days.drop(1).forEachIndexed { index, day ->
            val isToday = (index + 1) == todayIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day,
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isToday) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
