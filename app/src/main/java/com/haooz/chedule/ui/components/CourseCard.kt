package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.haooz.chedule.isAppDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 课程卡片组件
 */
@Composable
fun CourseCard(
    course: Course,
    isCurrentWeek: Boolean = true,
    hasMultipleCourses: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sectionCount = course.endSection - course.startSection + 1
    val cardHeight = (sectionCount * 54).dp

    // 课程颜色根据深色模式调整透明度，非本周课程置灰
    val isDark = isAppDarkTheme()
    val cardColor = if (isCurrentWeek) {
        Color(course.colorRes).copy(alpha = if (isDark) 0.16f else 0.14f)
    } else {
        Color(0xFF9E9E9E).copy(alpha = if (isDark) 0.14f else 0.08f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        cornerRadius = 8.dp,
        insideMargin = PaddingValues(0.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        colors = CardDefaults.defaultColors(
            color = cardColor,
            contentColor = MiuixTheme.colorScheme.onSurface
        ),
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val textColor = if (isCurrentWeek) Color(course.colorRes) else Color(0xFF9E9E9E).copy(alpha = if (isDark) 0.28f else 0.45f)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = course.name,
                    style = MiuixTheme.textStyles.footnote1.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 14.sp
                    ),
                    color = textColor,
                    textAlign = TextAlign.Start,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (sectionCount >= 2 && course.classroom.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    val classroomText = "@${course.classroom}"
                    Text(
                        text = classroomText,
                        style = MiuixTheme.textStyles.footnote2,
                        color = textColor,
                        textAlign = TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                if (sectionCount >= 2 && course.teacher.isNotEmpty()) {
                    Text(
                        text = course.teacher,
                        style = MiuixTheme.textStyles.footnote2,
                        color = textColor,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 多课程圆形指示器
            if (hasMultipleCourses) {
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
