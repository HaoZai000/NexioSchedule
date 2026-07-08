/** 调课单元格组件 */
package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.utils.isAppDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private val ShiftBlue = Color(0xFF2196F3)

@Composable
fun ShiftCell(
    courses: List<Pair<String, Course>>,
    scheduleColors: Map<String, Color>,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()

    if (courses.isEmpty()) return

    val isMulti = courses.size > 1
    val chipColor = ShiftBlue.copy(alpha = if (isDark) 0.16f else 0.14f)
    val textColor = ShiftBlue.copy(alpha = if (isDark) 0.9f else 0.85f)

    Card(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        cornerRadius = 8.dp,
        insideMargin = PaddingValues(0.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        colors = CardDefaults.defaultColors(
            color = chipColor,
            contentColor = MiuixTheme.colorScheme.onSurface
        ),
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                courses.forEach { (scheduleName, _) ->
                    Text(
                        text = scheduleName,
                        style = MiuixTheme.textStyles.footnote1.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 14.sp
                        ),
                        color = textColor,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isMulti) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(8.dp)
                        .background(
                            color = textColor,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}
