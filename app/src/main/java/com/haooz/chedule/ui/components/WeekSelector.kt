package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 周次选择器
 */
@Composable
fun WeekSelector(
    currentWeek: Int,
    totalWeeks: Int,
    onWeekChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左箭头
            IconButton(
                onClick = {
                    if (currentWeek > 1) onWeekChange(currentWeek - 1)
                },
                enabled = currentWeek > 1
            ) {
                Icon(
                    imageVector = MiuixIcons.ChevronBackward,
                    contentDescription = "上一周"
                )
            }

            // 周次显示
            SmallTitle(
                text = "第 ${currentWeek} 周",
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // 周次快速选择
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items((1..totalWeeks).toList()) { week ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedRectangle(8.dp))
                            .background(
                                if (week == currentWeek) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onWeekChange(week) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$week",
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (week == currentWeek) MiuixTheme.colorScheme.onPrimary
                            else MiuixTheme.colorScheme.onSurfaceContainerVariant
                        )
                    }
                }
            }

            // 右箭头
            IconButton(
                onClick = {
                    if (currentWeek < totalWeeks) onWeekChange(currentWeek + 1)
                },
                enabled = currentWeek < totalWeeks
            ) {
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = "下一周"
                )
            }
        }
    }
}
