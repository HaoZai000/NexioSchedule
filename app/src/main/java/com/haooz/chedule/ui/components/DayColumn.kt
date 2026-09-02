package com.haooz.chedule.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberCourseCardEdgeLight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.capsule.ContinuousRoundedRectangle
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
    sectionTimes: Map<Int, String> = Course.defaultSectionTimes,
    specialBlocks: List<com.haooz.chedule.data.SpecialBlock> = emptyList(),
    currentWeek: Int = 1,
    isHoliday: Boolean = false,
    isWorkSwap: Boolean = false,
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
    cardTextColor: com.haooz.chedule.data.CardTextColor = com.haooz.chedule.data.CardTextColor.COLORFUL,
    draggingCourseIds: Set<String> = emptySet(),
    onCourseLongPress: (course: Course, cardLeft: Float, cardTop: Float, width: Float, height: Float, backdrop: Backdrop?, currentWeek: Int) -> Unit = { _, _, _, _, _, _, _ -> },
    onCourseDragStart: (courseId: String) -> Unit = { _ -> },
    onCourseDrag: (courseId: String, offsetX: Float, offsetY: Float) -> Unit = { _, _, _ -> },
    onCourseDragEnd: (courseId: String) -> Unit = { _ -> },
    onCourseMenuDismiss: () -> Unit = {},
    // 拖拽落点高亮：当前列中需高亮的节次范围（含起止），null 表示无高亮
    dropHighlightSections: IntRange? = null,
    // 调课后需要淡入放大的课程ID集合
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val totalSectionsGrid = morningSections + afternoonSections + eveningSections
    // 特殊课程为时间轴浮层：节次保持固定位置，特殊课程按起止时间插值成一整条长卡片
    val grid = remember(
        totalSectionsGrid, morningSections, afternoonSections, eveningSections,
        specialBlocks, sectionTimes, cardHeightPerSection, showBreakDividers
    ) {
        computeSpecialGridLayout(
            morningSections = morningSections,
            afternoonSections = afternoonSections,
            eveningSections = eveningSections,
            specialBlocks = specialBlocks,
            sectionTimes = sectionTimes,
            cardHeightPerSection = cardHeightPerSection,
            dividerGap = if (showBreakDividers) 24 else 0
        )
    }
    val totalHeight = grid.totalHeight.toInt()
    val isDark = isAppDarkTheme()
    val hasBlur = wallpaperBackdrop != null
    val isPendingDay = pendingDay == dayOfWeek
    val hapticFeedback = LocalHapticFeedback.current

    // 已占用的节次：普通课程按节次范围，自定义时间课程按其时间区间覆盖到的节次
    val occupiedSections = remember(courses, sectionTimes, totalSectionsGrid) {
        buildSet {
            courses.forEach { course ->
                if (course.hasValidCustomTime()) {
                    val cs = parseMinutes(course.customStartTime)
                    val ce = parseMinutes(course.customEndTime)
                    if (cs in 0..<ce) {
                        for (section in 1..totalSectionsGrid) {
                            val timeStr = sectionTimes[section] ?: continue
                            val parts = timeStr.split("-")
                            if (parts.size != 2) continue
                            val ss = parseMinutes(parts[0])
                            val se = parseMinutes(parts[1])
                            if (ss >= 0 && se >= 0 && cs < se && ce > ss) {
                                add(section)
                            }
                        }
                    }
                } else {
                    for (s in course.startSection..course.endSection) {
                        add(s)
                    }
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

    Box(
        modifier = modifier
            .height(totalHeight.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            val totalSectionsGrid = morningSections + afternoonSections + eveningSections
            val density = LocalDensity.current
            val perSectionPx = with(density) { cardHeightPerSection.dp.toPx() }

            // 节次顶部偏移（dp）换算，与 CourseCardsLayer 的 segOffset 逻辑保持一致（含特殊课程块挤占偏移）
            fun sectionTopDp(section: Int): Float = grid.sectionTop[section] ?: 0f

            // 1. 空节次交互层 —— 单节点承载所有空节次的点击/长按，依据 Y 坐标换算节次，
            //    并将拖拽落点高亮一并绘制于此，减少每页布局节点数（原每个节次一个 Box）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .drawBehind {
                        val range = dropHighlightSections ?: return@drawBehind
                        val cornerPx = cardCornerRadius.dp.toPx()
                        val padV = 2.dp.toPx()
                        val padH = 2.dp.toPx()
                        val firstTopPx = with(density) { sectionTopDp(range.first).dp.toPx() }
                        val lastBottomPx = with(density) { sectionTopDp(range.last).dp.toPx() } + perSectionPx
                        val path = Path().apply {
                            addRoundRect(
                                roundRect = RoundRect(
                                    left = padH,
                                    top = firstTopPx + padV,
                                    right = size.width - padH,
                                    bottom = lastBottomPx - padV,
                                    topLeftCornerRadius = CornerRadius(cornerPx),
                                    topRightCornerRadius = CornerRadius(cornerPx),
                                    bottomLeftCornerRadius = CornerRadius(cornerPx),
                                    bottomRightCornerRadius = CornerRadius(cornerPx)
                                )
                            )
                        }
                        drawPath(path, dropHighlightColor)
                    }
                    .pointerInput(dayOfWeek, occupiedSections, totalSectionsGrid, perSectionPx, specialBlocks, grid) {
                        detectTapGestures(
                            onTap = { offset ->
                                val y = offset.y
                                // 依据实际节次顶部偏移（含特殊课程块挤占）反查落点节次，分界带/特殊块区域无匹配则忽略
                                var section = -1
                                for (s in 1..totalSectionsGrid) {
                                    val topDp = grid.sectionTop[s] ?: continue
                                    val topPx = with(density) { topDp.dp.toPx() }
                                    if (y >= topPx && y < topPx + perSectionPx) {
                                        section = s
                                        break
                                    }
                                }
                                if (section in 1..totalSectionsGrid && section !in occupiedSections) {
                                    onPendingChange(dayOfWeek, section)
                                }
                            },
                            onLongPress = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEmptyLongPress()
                            }
                        )
                    }
            )

            // 2. Pending 添加卡片（用户点击空节次后渲染，仅渲染当前日非占用的 pending 节次）
            if (isPendingDay && pendingSection in 1..totalSectionsGrid && pendingSection !in occupiedSections) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeightPerSection.dp)
                        .offset(y = sectionTopDp(pendingSection).dp)
                ) {
                    PendingSectionBox(
                        section = pendingSection,
                        hasBlur = hasBlur,
                        isDark = isDark,
                        cardCornerRadius = cardCornerRadius,
                        cardBlurRadius = cardBlurRadius,
                        wallpaperBackdrop = wallpaperBackdrop,
                        hapticFeedback = hapticFeedback,
                        onEmptyClick = onEmptyClick
                    )
                }
            }

            // 特殊课程横带统一在整表层（MainScheduleScreen）按时间插值横贯所有星期列渲染，
            // 此处不再逐列绘制，避免“每天一个卡片”。grid.specialBands 仅用于其它几何计算。

            // 课程卡片层 —— 独立组合函数，周切换时仅此层重组，静态网格骨架可被 Compose 跳过
            CourseCardsLayer(
                courses = courses,
                currentWeek = currentWeek,
                isHoliday = isHoliday,
                isWorkSwap = isWorkSwap,
                showBreakDividers = showBreakDividers,
                morningSections = morningSections,
                afternoonSections = afternoonSections,
                eveningSections = eveningSections,
                sectionTimes = sectionTimes,
                grid = grid,
                cardHeightPerSection = cardHeightPerSection,
                cardCornerRadius = cardCornerRadius,
                cardAlpha = cardAlpha,
                isTablet = isTablet,
                cardContentAlignment = cardContentAlignment,
                cardTextColor = cardTextColor,
                wallpaperBackdrop = wallpaperBackdrop,
                cardBlurRadius = cardBlurRadius,
                draggingCourseIds = draggingCourseIds,
                onCourseClick = onCourseClick,
                onCourseLongPress = onCourseLongPress,
                onCourseDragStart = onCourseDragStart,
                onCourseDrag = onCourseDrag,
                onCourseDragEnd = onCourseDragEnd,
                onCourseMenuDismiss = onCourseMenuDismiss,
                onPendingChange = onPendingChange
            )
        }
    }
}

/**
 * 课程卡片层。将分组/筛选/分段计算与卡片组合包裹在独立组合函数中，
 * 使周切换（courses/currentWeek 变化）时重组范围收缩到本层，
 * 静态网格骨架（空占位 + 分界线）不随周数据重建。
 */
@Composable
private fun CourseCardsLayer(
    courses: List<Course>,
    currentWeek: Int,
    isHoliday: Boolean,
    isWorkSwap: Boolean,
    showBreakDividers: Boolean,
    morningSections: Int,
    afternoonSections: Int,
    eveningSections: Int,
    sectionTimes: Map<Int, String>,
    grid: SpecialGridLayout,
    cardHeightPerSection: Float,
    cardCornerRadius: Float,
    cardAlpha: Float,
    isTablet: Boolean,
    cardContentAlignment: com.haooz.chedule.data.CardContentAlignment,
    cardTextColor: com.haooz.chedule.data.CardTextColor,
    wallpaperBackdrop: Backdrop?,
    cardBlurRadius: Float,
    draggingCourseIds: Set<String>,
    onCourseClick: (Course) -> Unit,
    onCourseLongPress: (Course, Float, Float, Float, Float, Backdrop?, Int) -> Unit,
    onCourseDragStart: (String) -> Unit,
    onCourseDrag: (String, Float, Float) -> Unit,
    onCourseDragEnd: (String) -> Unit,
    onCourseMenuDismiss: () -> Unit,
    onPendingChange: (Int, Int) -> Unit
) {
    val courseRenderDataList = remember(courses, currentWeek, showBreakDividers, morningSections, afternoonSections, eveningSections) {
        val coursesBySection = courses.groupBy { courseSlotKey(it) }
        val displayedCourses = mutableListOf<Course>()
        val hiddenCoursesMap = mutableMapOf<String, List<Course>>()

        coursesBySection.forEach { (slotKey, sectionCourses) ->
            val currentWeekCourses = sectionCourses.filter { it.isActiveInWeek(currentWeek) }
            val otherCourses = sectionCourses.filter { !it.isActiveInWeek(currentWeek) }

            if (currentWeekCourses.isNotEmpty()) {
                displayedCourses.add(currentWeekCourses.first())
                val hidden = currentWeekCourses.drop(1) + otherCourses
                if (hidden.isNotEmpty()) {
                    hiddenCoursesMap[slotKey] = hidden
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
                    hiddenCoursesMap[slotKey] = hidden
                }
            }
        }

        val dinnerBreak = morningSections + afternoonSections

        displayedCourses.map { course ->
            val isCurrentWeekCourse = course.isActiveInWeek(currentWeek)
            val hasHiddenCourses = hiddenCoursesMap.containsKey(courseSlotKey(course))

            val segments = mutableListOf<Pair<Int, Int>>()
            if (showBreakDividers) {
                var segStart = course.startSection
                while (segStart <= course.endSection) {
                    var segEnd = course.endSection
                    if (morningSections in segStart..<segEnd) segEnd = morningSections
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

        // 自定义时间课程：按时间轴插值定位/定高，不按节次分段，忽略午休/晚休分界
        if (course.hasValidCustomTime()) {
            val layout = computeCustomTimeLayout(
                customStart = course.customStartTime,
                customEnd = course.customEndTime,
                morningSections = morningSections,
                afternoonSections = afternoonSections,
                eveningSections = eveningSections,
                cardHeightPerSection = cardHeightPerSection,
                sectionTimes = sectionTimes,
                grid = grid
            )
            if (layout != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = layout.topDp.dp)
                ) {
                    CourseCard(
                        course = course,
                        isCurrentWeek = isCurrentWeekCourse,
                        isHoliday = isHoliday,
                        isWorkSwap = isWorkSwap,
                        hasMultipleCourses = renderData.hasHiddenCourses,
                        wallpaperBackdrop = wallpaperBackdrop,
                        cardBlurRadius = cardBlurRadius,
                        cardAlpha = cardAlpha,
                        cardHeightPerSection = cardHeightPerSection,
                        customCardHeightDp = layout.heightDp,
                        cardCornerRadius = cardCornerRadius,
                        isTablet = isTablet,
                        cardContentAlignment = cardContentAlignment,
                        cardTextColor = cardTextColor,
                        isDragging = isDragging,
                        onClick = {
                            onPendingChange(-1, -1)
                            onCourseClick(course)
                        },
                        // 自定义时间课程不允许长按调课，不传入长按/拖拽回调
                    )
                }
            }
            return@forEach
        }

        renderData.segments.forEachIndexed { idx, (segStartSection, segEndSection) ->
            val displayCourse = course.copy(startSection = segStartSection, endSection = segEndSection)
            val segOffset = (grid.sectionTop[segStartSection] ?: 0f).toInt()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = segOffset.dp)
            ) {
                CourseCard(
                    course = displayCourse,
                    isCurrentWeek = isCurrentWeekCourse,
                    isHoliday = isHoliday,
                    isWorkSwap = isWorkSwap,
                    hasMultipleCourses = idx == 0 && renderData.hasHiddenCourses,
                    wallpaperBackdrop = wallpaperBackdrop,
                    cardBlurRadius = cardBlurRadius,
                    cardAlpha = cardAlpha,
                    cardHeightPerSection = cardHeightPerSection,
                    cardCornerRadius = cardCornerRadius,
                    isTablet = isTablet,
                    cardContentAlignment = cardContentAlignment,
                    cardTextColor = cardTextColor,
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

/**
 * Pending 状态的空节次卡片（含模糊+边光+图标），仅在用户点击空白格时渲染。
 * 提取为独立 Composable 避免在普通空单元格中创建子树。
 */
@Composable
private fun PendingSectionBox(
    section: Int,
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
                val backdropShape = remember(cardCornerRadius) { ContinuousRoundedRectangle(cardCornerRadius.dp) }
                val edgeLightShape = remember(cardCornerRadius) { ContinuousRoundedRectangle(cardCornerRadius.dp) }
                val density = LocalDensity.current
                val blurPx = with(density) { remember(cardBlurRadius) { cardBlurRadius.dp.toPx() } }
                val surfaceColor = remember(isDark) { if (isDark) Color(0xFF242424).copy(alpha = 0.64f) else Color(0xFFF0F0F0).copy(alpha = 0.5f) }
                val isSharedBlur = wallpaperBackdrop is SharedBlurBackdrop
                val pendingEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(isSharedBlur, blurPx) {
                    {
                        if (!isSharedBlur) {
                            blur(blurPx)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .drawBackdrop(
                            backdrop = wallpaperBackdrop!!,
                            shape = { backdropShape },
                            effects = pendingEffects,
                            highlight = null,
                            shadow = null,
                            onDrawSurface = {
                                drawRect(surfaceColor)
                            }
                        )
                        .edgeLight(shape = edgeLightShape, edgeLight = rememberCourseCardEdgeLight())
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

/**
 * 课程所在“格子槽位”的标识。
 * 普通课程按节次号；自定义时间课程按实际起止时间，
 * 避免同名师同日但不同时段（如上午/下午）的自定义时间课程被误判为同一槽位而折叠合并。
 */
private fun courseSlotKey(course: Course): String {
    if (course.hasValidCustomTime()) {
        return "custom|${course.customStartTime}|${course.customEndTime}"
    }
    return "section|${course.startSection}"
}

/**
 * 解析 "HH:mm" 为当天分钟数，解析失败返回 -1
 */
private fun parseMinutes(time: String?): Int {
    if (time.isNullOrBlank()) return -1
    val parts = time.split(":")
    if (parts.size != 2) return -1
    val h = parts[0].toIntOrNull() ?: return -1
    val m = parts[1].toIntOrNull() ?: return -1
    return h * 60 + m
}

/**
 * 自定义时间课程在网格中的布局：顶部偏移（dp）+ 高度（dp）
 */
private data class CustomTimeLayout(
    val topDp: Float,
    val heightDp: Float
)

/**
 * 按时间轴插值计算自定义时间课程的位置与高度。
 *
 * 保留节次网格骨架，早/午/晚三个连续时间段各自按时间比例插值到对应高度区间；
 * 跨时间段（含午休/晚休分隔带）时用统一的"分钟 → Y"映射，使课程能自然跨过分隔带。
 * 自定义时间在网格时间范围之外时做钳制（早于第一节→列顶，晚于最后一节→列底）。
 */
private fun computeCustomTimeLayout(
    customStart: String?,
    customEnd: String?,
    morningSections: Int,
    afternoonSections: Int,
    eveningSections: Int,
    cardHeightPerSection: Float,
    sectionTimes: Map<Int, String>,
    grid: SpecialGridLayout
): CustomTimeLayout? {
    val cs = parseMinutes(customStart)
    val ce = parseMinutes(customEnd)
    if (cs < 0 || ce < 0 || ce <= cs) return null

    val totalSections = morningSections + afternoonSections + eveningSections
    val columnBottom = grid.totalHeight

    // 收集所有节次的起止时间
    data class SectionInfo(val start: Int, val end: Int, val index: Int)
    val sections = mutableListOf<SectionInfo>()
    for (section in 1..totalSections) {
        val timeStr = sectionTimes[section] ?: continue
        val parts = timeStr.split("-")
        if (parts.size != 2) continue
        val ss = parseMinutes(parts[0])
        val se = parseMinutes(parts[1])
        if (ss < 0 || se < 0) continue
        sections.add(SectionInfo(ss, se, section))
    }
    if (sections.isEmpty()) return null

    // 将时间映射到 Y 坐标：在节次内按比例插值，跳过课间
    fun timeToY(minutes: Int): Float {
        // 找到该时间所在的节次
        for (info in sections) {
            if (minutes <= info.end) {
                val sectionTop = grid.sectionTop[info.index] ?: 0f
                // 在该节次内按时间比例插值
                val fraction = if (info.end > info.start) {
                    ((minutes - info.start).toFloat() / (info.end - info.start)).coerceIn(0f, 1f)
                } else 0f
                return sectionTop + cardHeightPerSection * fraction
            }
        }
        // 超出最后一节：返回列底
        return columnBottom
    }

    // 早于第一节时：Y=0
    fun timeToYClamped(minutes: Int): Float {
        if (minutes <= sections.first().start) return 0f
        return timeToY(minutes).coerceIn(0f, columnBottom)
    }

    val top = timeToYClamped(cs)
    val bottom = timeToYClamped(ce)
    return CustomTimeLayout(top, (bottom - top).coerceAtLeast(0f))
}

/**
 * 特殊课程长条（无编号，如早读/大课间/眼保健操）的渲染：显示名称居中。
 * 时间为时间轴浮层，起止时间显示在左侧时间列，此处仅显示名称。
 */
@Composable
fun SpecialBandOverlay(
    name: String,
    hasBlur: Boolean,
    isDark: Boolean,
    cardCornerRadius: Float,
    cardBlurRadius: Float,
    cardAlpha: Float,
    wallpaperBackdrop: Backdrop?
) {
    val shownName = name.ifBlank { "特殊课程" }
    // 因子基于原始 cardAlpha，确保默认时因子恒为 1；仅对最终 alpha 做 0~1 保护
    val alphaFactor = cardAlpha / 0.15f
    val bgColor = if (isDark) {
        Color.White.copy(alpha = (0.06f * alphaFactor).coerceIn(0f, 1f))
    } else {
        Color.Black.copy(alpha = (0.04f * alphaFactor).coerceIn(0f, 1f))
    }

    if (hasBlur && wallpaperBackdrop != null) {
        key(cardCornerRadius) {
            val backdropShape = remember(cardCornerRadius) { ContinuousRoundedRectangle((cardCornerRadius).dp) }
            val edgeLightShape = remember(cardCornerRadius) { ContinuousRoundedRectangle(cardCornerRadius.dp) }
            val density = LocalDensity.current
            val blurPx = with(density) { remember(cardBlurRadius) { cardBlurRadius.dp.toPx() } }
            val lensRadiusPx = with(density) { remember { 6f.dp.toPx() } }
            val lensStrengthPx = with(density) { remember { 14f.dp.toPx() } }
            val overlayColor = remember(isDark, alphaFactor) {
                if (isDark) Color.Black.copy(alpha = (0.15f * alphaFactor).coerceIn(0f, 1f))
                else Color.White.copy(alpha = (0.17f * alphaFactor).coerceIn(0f, 1f))
            }
            val isSharedBlur = wallpaperBackdrop is SharedBlurBackdrop
            val bandEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(isSharedBlur, blurPx, lensRadiusPx, lensStrengthPx) {
                {
                    if (!isSharedBlur) {
                        blur(blurPx)
                    }
                    lens(lensRadiusPx, lensStrengthPx)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .drawBackdrop(
                        backdrop = wallpaperBackdrop,
                        shape = { backdropShape },
                        effects = bandEffects,
                        highlight = null,
                        shadow = null,
                        onDrawSurface = {
                            drawRect(bgColor)
                            drawRect(overlayColor)
                        }
                    )
                    .edgeLight(shape = edgeLightShape, edgeLight = rememberCourseCardEdgeLight())
            ) {
                SpecialBandContent(shownName)
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .drawBehind {
                    val cornerPx = cardCornerRadius.dp.toPx()
                    drawRoundRect(
                        color = bgColor,
                        cornerRadius = CornerRadius(cornerPx)
                    )
                }
        ) {
            SpecialBandContent(shownName)
        }
    }
}

@Composable
private fun SpecialBandContent(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
            color = if (isAppDarkTheme()) Color.White.copy(alpha = 0.74f) else Color.Black.copy(alpha = 0.74f),
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}
