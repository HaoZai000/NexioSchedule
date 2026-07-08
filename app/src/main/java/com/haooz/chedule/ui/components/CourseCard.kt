package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun CourseCard(
    course: Course,
    isCurrentWeek: Boolean = true,
    hasMultipleCourses: Boolean = false,
    wallpaperBackdrop: LayerBackdrop? = null,
    cardBlurRadius: Float = 0f,
    cardAlpha: Float = 0.15f,
    cardHeightPerSection: Float = 54f,
    cardCornerRadius: Float = 8f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sectionCount = course.endSection - course.startSection + 1
    val cardHeight = (sectionCount * cardHeightPerSection).dp
    val hasBlur = cardBlurRadius > 0f && wallpaperBackdrop != null

    val cardColor = if (isCurrentWeek) {
        Color(course.colorRes).copy(alpha = cardAlpha)
    } else {
        Color(0xFF9E9E9E).copy(alpha = cardAlpha * 0.7f)
    }
    val textColor = if (isCurrentWeek) {
        Color(course.colorRes)
    } else {
        Color(0xFF9E9E9E).copy(alpha = if (isAppDarkTheme()) 0.28f else 0.45f)
    }

    val blurColors = if (hasBlur) BlurDefaults.blurColors(
        blendColors = listOf(
            if (isAppDarkTheme()) BlendColorEntry(color = Color.Black.copy(alpha = cardAlpha), mode = BlurBlendMode.Multiply)
            else BlendColorEntry(color = Color.White.copy(alpha = cardAlpha), mode = BlurBlendMode.Screen)
        )
    ) else null

    // 仅在有模糊时用 key 包裹，缩小重建范围；无模糊时直接渲染 Card
    if (hasBlur) {
        key(cardCornerRadius) {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .textureBlur(
                        backdrop = wallpaperBackdrop!!,
                        shape = RoundedRectangle(cardCornerRadius.dp),
                        blurRadius = cardBlurRadius,
                        colors = blurColors!!
                    ),
                cornerRadius = cardCornerRadius.dp,
                insideMargin = PaddingValues(0.dp),
                pressFeedbackType = PressFeedbackType.Sink,
                showIndication = true,
                colors = CardDefaults.defaultColors(
                    color = cardColor,
                    contentColor = MiuixTheme.colorScheme.onSurface
                ),
                onClick = onClick
            ) {
                CardContent(course, sectionCount, cardColor, textColor, hasMultipleCourses)
            }
        }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(cardHeight)
                .padding(horizontal = 2.dp, vertical = 2.dp),
            cornerRadius = cardCornerRadius.dp,
            insideMargin = PaddingValues(0.dp),
            pressFeedbackType = PressFeedbackType.Sink,
            showIndication = true,
            colors = CardDefaults.defaultColors(
                color = cardColor,
                contentColor = MiuixTheme.colorScheme.onSurface
            ),
            onClick = onClick
        ) {
            CardContent(course, sectionCount, cardColor, textColor, hasMultipleCourses)
        }
    }
}

@Composable
private fun CardContent(course: Course, sectionCount: Int, cardColor: Color, textColor: Color, hasMultipleCourses: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
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
                Text(
                    text = "@${course.classroom}",
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

        if (hasMultipleCourses) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(8.dp)
                    .background(color = textColor, shape = CircleShape)
            )
        }
    }
}
