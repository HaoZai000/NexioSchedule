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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.haooz.chedule.ui.effects.edgelight.rememberCourseCardEdgeLight
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
 * 课程卡片预计算数据，包裹在 remember 中避免每次重组重复执行 groupBy/filter/分段
 */
private data class CourseRenderData(
    val course: Course,
    val isCurrentWeekCourse: Boolean,
    val hasHiddenCourses: Boolean,
    val segments: List<Pair<Int, Int>>
)

/**
 * 单列星期（显示该天的所有课程）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayColumn(
    dayOfWeek: Int,
    courses: List<Course>,
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
    cardAlpha: Float = 0.15f,
    cardHeightPerSection: Float = 54f,
    cardCornerRadius: Float = 10f,
    showBreakDividers: Boolean = true,
    isTablet: Boolean = false,
    cardContentAlignment: com.haooz.chedule.data.CardContentAlignment = com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER,
    draggingCourseIds: Set<String> = emptySet(),
    onCourseLongPress: (course: Course, cardLeft: Float, cardTop: Float, width: Float, height: Float, backdrop: Backdrop?, currentWeek: Int) -> Unit = { _, _, _, _, _, _, _ -> },
    onCourseDragStart: (courseId: String) -> Unit = { _ -> },
    onCourseDrag: (courseId: String, offsetX: Float, offsetY: Float) -> Unit = { _, _, _ -> },
    onCourseDragEnd: (courseId: String) -> Unit = { _ -> },
    onCourseMenuDismiss: () -> Unit = {},
    // 拖拽落点高亮：当前列中需高亮的节次范围（含起止），null 表示无高亮
    dropHighlightSections: IntRange? = null,
    // 调课后需要淡入放大的课程ID集合
    animateInCourseIds: Set<String> = emptySet(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val totalHeight = ((morningSections + afternoonSections + eveningSections) * cardHeightPerSection + (if (showBreakDividers) 24 * 2 else 0)).toInt()
    val isDark = isAppDarkTheme()
    val hasBlur = wallpaperBackdrop != null
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
    // 落点高亮背景色：让用户清楚看到落点位置
    val dropHighlightColor = if (hasBlur) {
        if (isDark) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f)
    } else {
        if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
    }
    val dropHighlightCornerRadius = cardCornerRadius.dp
    val dropHighlightPaddingV = 2.dp
    val dropHighlightPaddingH = 2.dp
    fun dropHighlightPadding(section: Int): PaddingValues {
        val range = dropHighlightSections ?: return PaddingValues(0.dp)
        if (section !in range) return PaddingValues(0.dp)
        val isFirst = section == range.first
        val isLast = section == range.last
        val top = if (isFirst) dropHighlightPaddingV else 0.dp
        val bottom = if (isLast) dropHighlightPaddingV else 0.dp
        return PaddingValues(start = dropHighlightPaddingH, end = dropHighlightPaddingH, top = top, bottom = bottom)
    }
    fun dropHighlightShape(section: Int): androidx.compose.ui.graphics.Shape {
        val range = dropHighlightSections ?: return RoundedCornerShape(0.dp)
        if (section !in range) return RoundedCornerShape(0.dp)
        val isFirst = section == range.first
        val isLast = section == range.last
        val top = if (isFirst) dropHighlightCornerRadius else 0.dp
        val bottom = if (isLast) dropHighlightCornerRadius else 0.dp
        return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
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
                val isDropHighlight = dropHighlightSections?.contains(section) == true
                if (isSectionPending) {
                    // 仅 pending 状态需要完整渲染（模糊+边光+卡片）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeightPerSection.dp)
                            .offset(y = currentOffset.dp)
                    ) {
                        PendingSectionBox(
                            section = section,
                            dayOfWeek = dayOfWeek,
                            hasBlur = hasBlur,
                            isDark = isDark,
                            cardCornerRadius = cardCornerRadius,
                            cardBlurRadius = cardBlurRadius,
                            wallpaperBackdrop = wallpaperBackdrop,
                            hapticFeedback = hapticFeedback,
                            onEmptyClick = onEmptyClick
                        )
                    }
                } else {
                    // 空白或占用：仅占位 + 可点击
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeightPerSection.dp)
                            .offset(y = currentOffset.dp)
                            .then(if (isDropHighlight) Modifier.padding(dropHighlightPadding(section)).background(dropHighlightColor, dropHighlightShape(section)) else Modifier)
                            .then(
                                if (!isOccupied) {
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
                            )
                    )
                }
                currentOffset += cardHeightPerSection.toInt()
            }

            // 午休分界线
            val dividerColor = if (cardBlurRadius > 0f) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
            val dividerShape = RoundedRectangle(12.dp)
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
                                .padding(horizontal = 4.dp)
                                .background(dividerColor, dividerShape)
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
                val isDropHighlight = dropHighlightSections?.contains(section) == true
                if (isSectionPending) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeightPerSection.dp)
                            .offset(y = currentOffset.dp)
                    ) {
                        PendingSectionBox(
                            section = section,
                            dayOfWeek = dayOfWeek,
                            hasBlur = hasBlur,
                            isDark = isDark,
                            cardCornerRadius = cardCornerRadius,
                            cardBlurRadius = cardBlurRadius,
                            wallpaperBackdrop = wallpaperBackdrop,
                            hapticFeedback = hapticFeedback,
                            onEmptyClick = onEmptyClick
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeightPerSection.dp)
                            .offset(y = currentOffset.dp)
                            .then(if (isDropHighlight) Modifier.padding(dropHighlightPadding(section)).background(dropHighlightColor, dropHighlightShape(section)) else Modifier)
                            .then(
                                if (!isOccupied) {
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
                            )
                    )
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
                                .padding(horizontal = 4.dp)
                                .background(dividerColor, dividerShape)
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
                val isDropHighlight = dropHighlightSections?.contains(section) == true
                if (isSectionPending) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeightPerSection.dp)
                            .offset(y = currentOffset.dp)
                    ) {
                        PendingSectionBox(
                            section = section,
                            dayOfWeek = dayOfWeek,
                            hasBlur = hasBlur,
                            isDark = isDark,
                            cardCornerRadius = cardCornerRadius,
                            cardBlurRadius = cardBlurRadius,
                            wallpaperBackdrop = wallpaperBackdrop,
                            hapticFeedback = hapticFeedback,
                            onEmptyClick = onEmptyClick
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeightPerSection.dp)
                            .offset(y = currentOffset.dp)
                            .then(if (isDropHighlight) Modifier.padding(dropHighlightPadding(section)).background(dropHighlightColor, dropHighlightShape(section)) else Modifier)
                            .then(
                                if (!isOccupied) {
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
                            )
                    )
                }
                currentOffset += cardHeightPerSection.toInt()
            }

            // 课程卡片 —— 将分组/筛选/分段计算包裹在 remember 中，避免每次重组重复执行
            val courseRenderDataList = remember(courses, currentWeek, showBreakDividers, morningSections, afternoonSections, eveningSections) {
                val coursesBySection = courses.groupBy { it.startSection }
                val displayedCourses = mutableListOf<Course>()
                val hiddenCoursesMap = mutableMapOf<Int, List<Course>>()

                coursesBySection.forEach { (startSection, sectionCourses) ->
                    val currentWeekCourses = sectionCourses.filter { it.isActiveInWeek(currentWeek) }
                    val otherCourses = sectionCourses.filter { !it.isActiveInWeek(currentWeek) }

                    if (currentWeekCourses.isNotEmpty()) {
                        displayedCourses.add(currentWeekCourses.first())
                        val hidden = currentWeekCourses.drop(1) + otherCourses
                        if (hidden.isNotEmpty()) {
                            hiddenCoursesMap[startSection] = hidden
                        }
                    } else {
                        val allEnded = otherCourses.all { it.endWeek < currentWeek }
                        val courseToShow = if (allEnded) {
                            otherCourses.maxByOrNull { it.endWeek } ?: otherCourses.first()
                        } else {
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

                val lunchBreak = morningSections
                val dinnerBreak = morningSections + afternoonSections

                displayedCourses.map { course ->
                    val isCurrentWeekCourse = course.isActiveInWeek(currentWeek)
                    val hasHiddenCourses = hiddenCoursesMap.containsKey(course.startSection)

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

                    CourseRenderData(course, isCurrentWeekCourse, hasHiddenCourses, segments)
                }
            }

            courseRenderDataList.forEach { renderData ->
                val course = renderData.course
                val isCurrentWeekCourse = renderData.isCurrentWeekCourse
                val isDragging = course.id in draggingCourseIds && isCurrentWeekCourse

                renderData.segments.forEachIndexed { idx, (segStartSection, segEndSection) ->
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
                            hasMultipleCourses = idx == 0 && renderData.hasHiddenCourses,
                            wallpaperBackdrop = wallpaperBackdrop,
                            cardBlurRadius = cardBlurRadius,
                            cardAlpha = cardAlpha,
                            cardHeightPerSection = cardHeightPerSection,
                            cardCornerRadius = cardCornerRadius,
                            isTablet = isTablet,
                            cardContentAlignment = cardContentAlignment,
                            isDragging = isDragging,
                            onClick = {
                                onPendingChange(-1, -1)
                                onCourseClick(course)
                            },
                            onLongPressStart = { left, top, width, height ->
                                if (isCurrentWeekCourse) {
                                    onCourseLongPress(course, left, top, width, height, wallpaperBackdrop, currentWeek)
                                }
                            },
                            onDragStart = {
                                onCourseDragStart(course.id)
                            },
                            onDrag = { offsetX, offsetY ->
                                onCourseDrag(course.id, offsetX, offsetY)
                            },
                            onDragEnd = {
                                onCourseDragEnd(course.id)
                            },
                            onMenuDismiss = {
                                onCourseMenuDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pending 状态的空节次卡片（含模糊+边光+图标），仅在用户点击空白格时渲染。
 * 提取为独立 Composable 避免在普通空单元格中创建子树。
 */
@Composable
private fun PendingSectionBox(
    section: Int,
    dayOfWeek: Int,
    hasBlur: Boolean,
    isDark: Boolean,
    cardCornerRadius: Float,
    cardBlurRadius: Float,
    wallpaperBackdrop: Backdrop?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onEmptyClick: (Int) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (hasBlur) {
            key(cardCornerRadius) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .drawBackdrop(
                            backdrop = wallpaperBackdrop!!,
                            shape = { RoundedRectangle(cardCornerRadius.dp) },
                            effects = {
                                blur(cardBlurRadius.dp.toPx())
                            },
                            highlight = null,
                            onDrawSurface = {
                                drawRect(if (isDark) Color(0xFF242424).copy(alpha = 0.64f) else Color(0xFFF0F0F0).copy(alpha = 0.5f))
                            }
                        )
                        .edgeLight(shape = RoundedRectangle(cardCornerRadius.dp), edgeLight = rememberCourseCardEdgeLight())
                ) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = cardCornerRadius.dp,
                        insideMargin = PaddingValues(0.dp),
                        showIndication = true,
                        colors = CardDefaults.defaultColors(
                            color = Color.Transparent,
                            contentColor = if (isDark) Color(0xFFF0F0F0).copy(alpha = 0.64f) else Color(0xFF242424).copy(alpha = 0.5f)
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
