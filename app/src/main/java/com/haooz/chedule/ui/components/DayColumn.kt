/** 日期列组件 - 显示单日课程列表 */
package com.haooz.chedule.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.key
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 单列星期（显示该天的所有课程）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayColumn(
    dayOfWeek: Int,
    courses: List<Course>,
    currentDay: Int,
    onCourseClick: (Course) -> Unit,
    onEmptyClick: (Int) -> Unit,
    onEmptyLongPress: () -> Unit = {},
    morningSections: Int = 4,
    afternoonSections: Int = 4,
    eveningSections: Int = 3,
    currentWeek: Int = 1,
    pendingDay: Int = -1,
    pendingSection: Int = -1,
    onPendingChange: (day: Int, section: Int) -> Unit = { _, _ -> },
    wallpaperBackdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop? = null,
    cardBlurRadius: Float = 0f,
    cardAlpha: Float = 0.15f,
    cardHeightPerSection: Float = 54f,
    cardCornerRadius: Float = 8f,
    modifier: Modifier = Modifier
) {
    val totalHeight = ((morningSections + afternoonSections + eveningSections) * cardHeightPerSection + 24 * 2).toInt()
    val isDark = isAppDarkTheme()
    val hasBlur = cardBlurRadius > 0f && wallpaperBackdrop != null
    val emptyCardBlurColors = if (hasBlur) BlurDefaults.blurColors(
        blendColors = listOf(
            if (isDark) BlendColorEntry(color = Color.Black.copy(alpha = 0.13f), mode = BlurBlendMode.Multiply)
            else BlendColorEntry(color = Color.White.copy(alpha = 0.15f), mode = BlurBlendMode.Screen)
        )
    ) else null
    val isPendingDay = pendingDay == dayOfWeek
    val hapticFeedback = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .height(totalHeight.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            var currentOffset = 0

            // 上午节次
            for (section in 1..morningSections) {
                val isSectionPending = isPendingDay && pendingSection == section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeightPerSection.dp)
                        .offset(y = currentOffset.dp)
                        .then(
                            if (!isSectionPending) {
                                Modifier.combinedClickable(
                                    onClick = {
                                        onPendingChange(dayOfWeek, section)
                                    },
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onEmptyLongPress()
                                    }
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSectionPending) {
                        if (hasBlur) {
                            key(cardCornerRadius) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                        .textureBlur(
                                            backdrop = wallpaperBackdrop!!,
                                            shape = RoundedRectangle(cardCornerRadius.dp),
                                            blurRadius = cardBlurRadius,
                                            colors = emptyCardBlurColors!!
                                        ),
                                    cornerRadius = cardCornerRadius.dp,
                                    insideMargin = PaddingValues(0.dp),
                                    pressFeedbackType = PressFeedbackType.Sink,
                                    showIndication = true,
                                    colors = CardDefaults.defaultColors(
                                        color = Color(0xFF9E9E9E).copy(alpha = if (isDark) 0.13f else 0.15f),
                                        contentColor = Color(0xFF6E6E6E).copy(alpha = if (isDark) 0.7f else 0.85f)
                                    ),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onEmptyClick(section)
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Add,
                                            contentDescription = "添加",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 2.dp, vertical = 2.dp),
                                cornerRadius = cardCornerRadius.dp,
                                insideMargin = PaddingValues(0.dp),
                                pressFeedbackType = PressFeedbackType.Sink,
                                showIndication = true,
                                colors = CardDefaults.defaultColors(
                                    color = Color(0xFF9E9E9E).copy(alpha = if (isDark) 0.13f else 0.15f),
                                    contentColor = Color(0xFF9E9E9E).copy(alpha = 0.5f)
                                ),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onEmptyClick(section)
                                }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Add,
                                        contentDescription = "添加",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
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
            for (section in afternoonStart..afternoonEnd) {
                val isSectionPending = isPendingDay && pendingSection == section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeightPerSection.dp)
                        .offset(y = currentOffset.dp)
                        .then(
                            if (!isSectionPending) {
                                Modifier.combinedClickable(
                                    onClick = {
                                        onPendingChange(dayOfWeek, section)
                                    },
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onEmptyLongPress()
                                    }
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSectionPending) {
                        if (hasBlur) {
                            key(cardCornerRadius) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                        .textureBlur(
                                            backdrop = wallpaperBackdrop!!,
                                            shape = RoundedRectangle(cardCornerRadius.dp),
                                            blurRadius = cardBlurRadius,
                                            colors = emptyCardBlurColors!!
                                        ),
                                    cornerRadius = cardCornerRadius.dp,
                                    insideMargin = PaddingValues(0.dp),
                                    pressFeedbackType = PressFeedbackType.Sink,
                                    showIndication = true,
                                    colors = CardDefaults.defaultColors(
                                        color = Color(0xFF9E9E9E).copy(alpha = if (isDark) 0.13f else 0.15f),
                                        contentColor = Color(0xFF6E6E6E).copy(alpha = if (isDark) 0.7f else 0.85f)
                                    ),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onEmptyClick(section)
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Add,
                                            contentDescription = "添加",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 2.dp, vertical = 2.dp),
                                cornerRadius = cardCornerRadius.dp,
                                insideMargin = PaddingValues(0.dp),
                                pressFeedbackType = PressFeedbackType.Sink,
                                showIndication = true,
                                colors = CardDefaults.defaultColors(
                                    color = Color(0xFF9E9E9E).copy(alpha = if (isDark) 0.13f else 0.15f),
                                    contentColor = Color(0xFF9E9E9E).copy(alpha = 0.5f)
                                ),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onEmptyClick(section)
                                }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Add,
                                        contentDescription = "添加",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
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
            for (section in eveningStart..eveningEnd) {
                val isSectionPending = isPendingDay && pendingSection == section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeightPerSection.dp)
                        .offset(y = currentOffset.dp)
                        .then(
                            if (!isSectionPending) {
                                Modifier.combinedClickable(
                                    onClick = {
                                        onPendingChange(dayOfWeek, section)
                                    },
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onEmptyLongPress()
                                    }
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSectionPending) {
                        if (hasBlur) {
                            key(cardCornerRadius) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                        .textureBlur(
                                            backdrop = wallpaperBackdrop!!,
                                            shape = RoundedRectangle(cardCornerRadius.dp),
                                            blurRadius = cardBlurRadius,
                                            colors = emptyCardBlurColors!!
                                        ),
                                    cornerRadius = cardCornerRadius.dp,
                                    insideMargin = PaddingValues(0.dp),
                                    pressFeedbackType = PressFeedbackType.Sink,
                                    showIndication = true,
                                    colors = CardDefaults.defaultColors(
                                        color = Color(0xFF9E9E9E).copy(alpha = if (isDark) 0.13f else 0.15f),
                                        contentColor = Color(0xFF6E6E6E).copy(alpha = if (isDark) 0.7f else 0.85f)
                                    ),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onEmptyClick(section)
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Add,
                                            contentDescription = "添加",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 2.dp, vertical = 2.dp),
                                cornerRadius = cardCornerRadius.dp,
                                insideMargin = PaddingValues(0.dp),
                                pressFeedbackType = PressFeedbackType.Sink,
                                showIndication = true,
                                colors = CardDefaults.defaultColors(
                                    color = Color(0xFF9E9E9E).copy(alpha = if (isDark) 0.13f else 0.15f),
                                    contentColor = Color(0xFF9E9E9E).copy(alpha = 0.5f)
                                ),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onEmptyClick(section)
                                }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Add,
                                        contentDescription = "添加",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                currentOffset += cardHeightPerSection.toInt()
            }

            // 课程卡片
            // 按节次分组，优先显示本周课程
            val coursesBySection = courses.groupBy { it.startSection }
            val displayedCourses = mutableListOf<Course>()
            val hiddenCoursesMap = mutableMapOf<Int, List<Course>>()

            coursesBySection.forEach { (startSection, sectionCourses) ->
                val currentWeekCourses = sectionCourses.filter { it.isActiveInWeek(currentWeek) }
                val otherCourses = sectionCourses.filter { !it.isActiveInWeek(currentWeek) }

                if (currentWeekCourses.isNotEmpty()) {
                    // 有本周课程，显示第一个本周课程，其余隐藏
                    displayedCourses.add(currentWeekCourses.first())
                    val hidden = currentWeekCourses.drop(1) + otherCourses
                    if (hidden.isNotEmpty()) {
                        hiddenCoursesMap[startSection] = hidden
                    }
                } else {
                    // 没有本周课程
                    // 判断是否所有课程都已结课
                    val allEnded = otherCourses.all { it.endWeek < currentWeek }
                    val courseToShow = if (allEnded) {
                        // 所有课程都已结课，显示最后结课的课程
                        otherCourses.maxByOrNull { it.endWeek } ?: otherCourses.first()
                    } else {
                        // 优先显示下周此处的课程（startWeek 最小且 > currentWeek）
                        otherCourses.filter { it.startWeek > currentWeek }
                            .minByOrNull { it.startWeek }
                            ?: otherCourses.first()
                    }
                    displayedCourses.add(courseToShow)
                    val hidden = otherCourses - courseToShow
                    if (hidden.isNotEmpty()) {
                        hiddenCoursesMap[startSection] = hidden
                    }
                }
            }

            displayedCourses.forEach { course ->
                var courseOffset = 0
                if (course.startSection <= morningSections) {
                    courseOffset = ((course.startSection - 1) * cardHeightPerSection).toInt()
                } else if (course.startSection <= morningSections + afternoonSections) {
                    courseOffset = (morningSections * cardHeightPerSection + 24 + (course.startSection - morningSections - 1) * cardHeightPerSection).toInt()
                } else {
                    courseOffset = (morningSections * cardHeightPerSection + 24 + afternoonSections * cardHeightPerSection + 24 + (course.startSection - morningSections - afternoonSections - 1) * cardHeightPerSection).toInt()
                }

                val isCurrentWeekCourse = course.isActiveInWeek(currentWeek)
                val hasHiddenCourses = hiddenCoursesMap.containsKey(course.startSection)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = courseOffset.dp)
                ) {
                    CourseCard(
                        course = course,
                        isCurrentWeek = isCurrentWeekCourse,
                        hasMultipleCourses = hasHiddenCourses,
                        wallpaperBackdrop = wallpaperBackdrop,
                        cardBlurRadius = cardBlurRadius,
                        cardAlpha = cardAlpha,
                        cardHeightPerSection = cardHeightPerSection,
                        cardCornerRadius = cardCornerRadius,
                        onClick = {
                            onPendingChange(-1, -1)
                            onCourseClick(course)
                        }
                    )
                }
            }
        }
    }
}
