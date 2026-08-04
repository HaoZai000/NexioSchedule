/** 日期列组件 - 显示单日课程列表 */
package com.haooz.chedule.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
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
    wallpaperBackdrop: Backdrop? = null,
    cardBlurRadius: Float = 0f,
    cardAlpha: Float = 0.20f,
    cardHeightPerSection: Float = 54f,
    cardCornerRadius: Float = 10f,
    showBreakDividers: Boolean = true,
    isTablet: Boolean = false,
    cardContentAlignment: com.haooz.chedule.data.CardContentAlignment = com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER,
    draggingCourseIds: Set<String> = emptySet(),
    onCourseLongPress: (course: Course, cardLeft: Float, cardTop: Float, width: Float, height: Float, backdrop: Backdrop?) -> Unit = { _, _, _, _, _, _ -> },
    onCourseDragStart: (courseId: String) -> Unit = { _ -> },
    onCourseDrag: (courseId: String, offsetX: Float, offsetY: Float) -> Unit = { _, _, _ -> },
    onCourseDragEnd: (courseId: String) -> Unit = { _ -> },
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val totalHeight = ((morningSections + afternoonSections + eveningSections) * cardHeightPerSection + (if (showBreakDividers) 24 * 2 else 0)).toInt()
    val isDark = isAppDarkTheme()
    val hasBlur = cardBlurRadius > 0f && wallpaperBackdrop != null
    val isPendingDay = pendingDay == dayOfWeek
    val hapticFeedback = LocalHapticFeedback.current
    // 共享交互源，避免每个空单元格创建新的 MutableInteractionSource
    val sharedInteractionSource = remember { MutableInteractionSource() }

    val occupiedSections = remember(courses) {
        buildSet {
            courses.forEach { course ->
                for (s in course.startSection..course.endSection) {
                    add(s)
                }
            }
        }
    }

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
                val isOccupied = section in occupiedSections
                val isSectionPending = isPendingDay && pendingSection == section && !isOccupied
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeightPerSection.dp)
                        .offset(y = currentOffset.dp)
                        .then(
                            if (!isSectionPending && !isOccupied) {
                                Modifier.combinedClickable(
                                    indication = null,
                                    interactionSource = sharedInteractionSource,
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                        .drawBackdrop(
                                            backdrop = wallpaperBackdrop,
                                            shape = { RoundedRectangle(cardCornerRadius.dp) },
                                            effects = {
                                                blur(cardBlurRadius.dp.toPx())
                                            },
                                            highlight = null,
                                            onDrawSurface = {
                                                drawRect( if (isDark) Color(0xFF242424).copy(alpha =0.64f) else Color(0xFFF0F0F0).copy(alpha =0.5f))
                                            }
                                        )
                                        .edgeLight(shape = RoundedRectangle(cardCornerRadius.dp), edgeLight = rememberDefaultEdgeLight())
                                ) {
                                    Card(
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = cardCornerRadius.dp,
                                        insideMargin = PaddingValues(0.dp),
                                        pressFeedbackType = PressFeedbackType.Sink,
                                        showIndication = true,
                                        colors = CardDefaults.defaultColors(
                                            color = Color.Transparent,
                                            contentColor = if (isDark) Color(0xFFF0F0F0).copy(alpha =0.64f) else Color(0xFF242424).copy(alpha =0.5f)
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
            if (showBreakDividers) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .offset(y = currentOffset.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isTablet) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .background(dividerColor)
                        )
                    }
                }
            }
            currentOffset += if (showBreakDividers) 24 else 0

            // 下午节次
            val afternoonStart = morningSections + 1
            val afternoonEnd = morningSections + afternoonSections
            for (section in afternoonStart..afternoonEnd) {
                val isOccupied = section in occupiedSections
                val isSectionPending = isPendingDay && pendingSection == section && !isOccupied
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeightPerSection.dp)
                        .offset(y = currentOffset.dp)
                        .then(
                            if (!isSectionPending && !isOccupied) {
                                Modifier.combinedClickable(
                                    indication = null,
                                    interactionSource = sharedInteractionSource,
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                        .drawBackdrop(
                                            backdrop = wallpaperBackdrop,
                                            shape = { RoundedRectangle(cardCornerRadius.dp) },
                                            effects = {
                                                blur(cardBlurRadius.dp.toPx())
                                            },
                                            highlight = null,
                                            onDrawSurface = {
                                                drawRect( if (isDark) Color(0xFF242424).copy(alpha =0.64f) else Color(0xFFF0F0F0).copy(alpha =0.5f))
                                            }
                                        )
                                        .edgeLight(shape = RoundedRectangle(cardCornerRadius.dp), edgeLight = rememberDefaultEdgeLight())
                                ) {
                                    Card(
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = cardCornerRadius.dp,
                                        insideMargin = PaddingValues(0.dp),
                                        pressFeedbackType = PressFeedbackType.Sink,
                                        showIndication = true,
                                        colors = CardDefaults.defaultColors(
                                            color = Color.Transparent,
                                            contentColor = if (isDark) Color(0xFFF0F0F0).copy(alpha =0.64f) else Color(0xFF242424).copy(alpha =0.5f)
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
            if (showBreakDividers) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .offset(y = currentOffset.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isTablet) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .background(dividerColor)
                        )
                    }
                }
            }
            currentOffset += if (showBreakDividers) 24 else 0

            // 晚上节次
            val eveningStart = morningSections + afternoonSections + 1
            val eveningEnd = morningSections + afternoonSections + eveningSections
            for (section in eveningStart..eveningEnd) {
                val isOccupied = section in occupiedSections
                val isSectionPending = isPendingDay && pendingSection == section && !isOccupied
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeightPerSection.dp)
                        .offset(y = currentOffset.dp)
                        .then(
                            if (!isSectionPending && !isOccupied) {
                                Modifier.combinedClickable(
                                    indication = null,
                                    interactionSource = sharedInteractionSource,
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                        .drawBackdrop(
                                            backdrop = wallpaperBackdrop,
                                            shape = { RoundedRectangle(cardCornerRadius.dp) },
                                            effects = {
                                                blur(cardBlurRadius.dp.toPx())
                                            },
                                            highlight = null,
                                            onDrawSurface = {
                                                drawRect( if (isDark) Color(0xFF242424).copy(alpha =0.64f) else Color(0xFFF0F0F0).copy(alpha =0.5f))
                                            }
                                        )
                                        .edgeLight(shape = RoundedRectangle(cardCornerRadius.dp), edgeLight = rememberDefaultEdgeLight())
                                ) {
                                    Card(
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = cardCornerRadius.dp,
                                        insideMargin = PaddingValues(0.dp),
                                        pressFeedbackType = PressFeedbackType.Sink,
                                        showIndication = true,
                                        colors = CardDefaults.defaultColors(
                                            color = Color.Transparent,
                                            contentColor = if (isDark) Color(0xFFF0F0F0).copy(alpha =0.64f) else Color(0xFF242424).copy(alpha =0.5f)
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
                val isCurrentWeekCourse = course.isActiveInWeek(currentWeek)
                val hasHiddenCourses = hiddenCoursesMap.containsKey(course.startSection)

                // 跨分界线的课程拆分为多段，避免午休/晚休栏穿过卡片中间
                val lunchBreak = morningSections
                val dinnerBreak = morningSections + afternoonSections
                val segments = mutableListOf<Pair<Int, Int>>()
                if (showBreakDividers) {
                    var segStart = course.startSection
                    while (segStart <= course.endSection) {
                        var segEnd = course.endSection
                        if (lunchBreak in segStart..<segEnd) segEnd = lunchBreak
                        if (dinnerBreak in segStart..<segEnd) segEnd = dinnerBreak
                        segments.add(segStart to segEnd)
                        segStart = segEnd + 1
                    }
                } else {
                    segments.add(course.startSection to course.endSection)
                }

                segments.forEachIndexed { idx, (segStartSection, segEndSection) ->
                    val displayCourse = course.copy(startSection = segStartSection, endSection = segEndSection)
                    val dividerGap = if (showBreakDividers) 24 else 0
                    val segOffset = when {
                        segStartSection <= morningSections -> ((segStartSection - 1) * cardHeightPerSection).toInt()
                        segStartSection <= morningSections + afternoonSections -> (morningSections * cardHeightPerSection + dividerGap + (segStartSection - morningSections - 1) * cardHeightPerSection).toInt()
                        else -> (morningSections * cardHeightPerSection + dividerGap + afternoonSections * cardHeightPerSection + dividerGap + (segStartSection - morningSections - afternoonSections - 1) * cardHeightPerSection).toInt()
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = segOffset.dp)
                    ) {
                        CourseCard(
                            course = displayCourse,
                            isCurrentWeek = isCurrentWeekCourse,
                            hasMultipleCourses = idx == 0 && hasHiddenCourses,
                            wallpaperBackdrop = wallpaperBackdrop,
                            cardBlurRadius = cardBlurRadius,
                            cardAlpha = cardAlpha,
                            cardHeightPerSection = cardHeightPerSection,
                            cardCornerRadius = cardCornerRadius,
                            isTablet = isTablet,
                            cardContentAlignment = cardContentAlignment,
                            isDragging = displayCourse.id in draggingCourseIds,
                            onClick = {
                                onPendingChange(-1, -1)
                                onCourseClick(course)
                            },
                            onLongPressStart = { left, top, width, height ->
                                onCourseLongPress(course, left, top, width, height, wallpaperBackdrop)
                            },
                            onDragStart = {
                                onCourseDragStart(course.id)
                            },
                            onDrag = { offsetX, offsetY ->
                                onCourseDrag(course.id, offsetX, offsetY)
                            },
                            onDragEnd = {
                                onCourseDragEnd(course.id)
                            }
                        )
                    }
                }
            }
        }
    }
}
