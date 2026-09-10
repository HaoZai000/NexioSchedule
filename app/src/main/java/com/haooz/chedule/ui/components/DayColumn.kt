package com.haooz.chedule.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
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
    // 空白格长按：section + 格子中心/顶部在 Root 中的绝对坐标与尺寸（px），供上层定位快捷菜单
    onEmptyLongPress: (section: Int, centerX: Float, cellTopY: Float, width: Float, height: Float) -> Unit = { _, _, _, _, _ -> },
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
    cardTextScale: Float = 1f,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    cardRefraction: com.haooz.chedule.data.CardRefractionLevel = com.haooz.chedule.data.CardRefractionLevel.DEFAULT,
    draggingCourseIds: Set<String> = emptySet(),
    onCourseLongPress: (course: Course, cardLeft: Float, cardTop: Float, width: Float, height: Float, backdrop: Backdrop?, currentWeek: Int) -> Unit = { _, _, _, _, _, _, _ -> },
    onCourseDragStart: (courseId: String) -> Unit = { _ -> },
    onCourseDrag: (courseId: String, offsetX: Float, offsetY: Float) -> Unit = { _, _, _ -> },
    onCourseDragEnd: (courseId: String) -> Unit = { _ -> },
    onCourseMenuDismiss: () -> Unit = {},
    // 拖拽落点高亮：当前列中需高亮的节次范围（含起止），null 表示无高亮
    dropHighlightSections: IntRange? = null,
    // 滑动中标记（非 state）：透传给课程卡片，滑动期间跳过逐帧坐标计算
    gridScrollFlag: com.haooz.chedule.ui.screens.GridScrollFlag? = null,
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
    // 落点高亮背景色：对齐 PendingSectionBox（加号卡片）的灰色风格
    val dropHighlightColor = Color(0xFF9E9E9E).copy(alpha = if (isDark) 0.13f else 0.15f)

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
            // 空节次交互层在 Root 中的边界：长按时据此计算格子绝对坐标供上层定位快捷菜单
            val emptyLayerBounds = remember { FloatArray(4) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .onGloballyPositioned { coordinates ->
                        if (gridScrollFlag?.scrolling != true) {
                            val pos = coordinates.localToRoot(Offset.Zero)
                            emptyLayerBounds[0] = pos.x
                            emptyLayerBounds[1] = pos.y
                            emptyLayerBounds[2] = coordinates.size.width.toFloat()
                            emptyLayerBounds[3] = coordinates.size.height.toFloat()
                        }
                    }
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
                            onLongPress = { offset ->
                                val y = offset.y
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
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val cellTopPx = with(density) { (grid.sectionTop[section] ?: 0f).dp.toPx() }
                                    onEmptyLongPress(
                                        section,
                                        emptyLayerBounds[0] + emptyLayerBounds[2] / 2f,
                                        emptyLayerBounds[1] + cellTopPx,
                                        emptyLayerBounds[2],
                                        perSectionPx
                                    )
                                }
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

            // 壁纸模式下的落点高亮：对齐 PendingSectionBox 加号卡片的 backdrop 样式
            // 非壁纸模式由上方 drawBehind 绘制纯色高亮
            if (hasBlur && wallpaperBackdrop != null && dropHighlightSections != null) {
                val hlRange = dropHighlightSections!!
                val hlTop = sectionTopDp(hlRange.first)
                val hlHeight = (hlRange.last - hlRange.first + 1) * cardHeightPerSection
                val hlShape = remember(cardCornerRadius) { ContinuousRoundedRectangle(cardCornerRadius.dp) }
                val hlBlurPx = with(density) { remember(cardBlurRadius) { cardBlurRadius.dp.toPx() } }
                val isSharedBlur = wallpaperBackdrop is SharedBlurBackdrop
                val hlEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(isSharedBlur, hlBlurPx) {
                    {
                        if (!isSharedBlur) blur(hlBlurPx)
                    }
                }
                // 对齐 PendingSectionBox 的 surface 颜色
                val hlSurfaceColor = remember(isDark) {
                    if (isDark) Color(0xFF242424).copy(alpha = 0.64f) else Color(0xFFF0F0F0).copy(alpha = 0.5f)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(hlHeight.dp)
                        .offset(y = hlTop.dp)
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .drawBackdrop(
                            backdrop = wallpaperBackdrop!!,
                            shape = { hlShape },
                            effects = hlEffects,
                            highlight = null,
                            shadow = null,
                            onDrawSurface = {
                                drawRect(hlSurfaceColor)
                            }
                        )
                        .edgeLight(shape = hlShape, edgeLight = rememberCourseCardEdgeLight())
                )
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
                cardTextScale = cardTextScale,
                showClassroom = showClassroom,
                showTeacher = showTeacher,
                cardRefraction = cardRefraction,
                wallpaperBackdrop = wallpaperBackdrop,
                cardBlurRadius = cardBlurRadius,
                draggingCourseIds = draggingCourseIds,
                gridScrollFlag = gridScrollFlag,

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
    cardTextScale: Float,
    showClassroom: Boolean,
    showTeacher: Boolean,
    cardRefraction: com.haooz.chedule.data.CardRefractionLevel,
    wallpaperBackdrop: Backdrop?,
    cardBlurRadius: Float,
    draggingCourseIds: Set<String>,
    gridScrollFlag: com.haooz.chedule.ui.screens.GridScrollFlag? = null,
    viewportTopDp: Float = 0f,
    viewportBottomDp: Float = Float.MAX_VALUE,
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
            // partition 只对每门课调一次 isActiveInWeek（原 filter + filter{!...} 调两次）
            val (currentWeekCourses, otherCourses) = sectionCourses.partition { it.isActiveInWeek(currentWeek) }

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
                        gridScrollFlag = gridScrollFlag,
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
                        cardTextScale = cardTextScale,
                        showClassroom = showClassroom,
                        showTeacher = showTeacher,
                        cardRefraction = cardRefraction,
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
                        gridScrollFlag = gridScrollFlag,
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
                        cardTextScale = cardTextScale,
                        showClassroom = showClassroom,
                        showTeacher = showTeacher,
                        cardRefraction = cardRefraction,
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
    cardRefraction: com.haooz.chedule.data.CardRefractionLevel = com.haooz.chedule.data.CardRefractionLevel.DEFAULT,
    isTablet: Boolean = false,
    wallpaperBackdrop: Backdrop?,
    // 内部按星期划分的子块（如周一~周二"画黑板报"）；为空时退化为整条显示名称
    items: List<com.haooz.chedule.data.SpecialItem> = emptyList(),
    // 当前实际显示的星期列表（智能周末模式下可能只有 1..5），决定列宽与子块定位
    dayRange: List<Int> = emptyList()
) {
    val shownName = name.ifBlank { "特殊课程" }
    // 因子基于原始 cardAlpha，确保默认时因子恒为 1；仅对最终 alpha 做 0~1 保护
    val alphaFactor = cardAlpha / 0.15f
    // 与课程卡片一致：平板上圆角放大 1.3 倍
    val effectiveCornerRadius = if (isTablet) cardCornerRadius * 1.3f else cardCornerRadius
    val bgColor = if (isDark) {
        Color.White.copy(alpha = (0.06f * alphaFactor).coerceIn(0f, 1f))
    } else {
        Color.Black.copy(alpha = (0.04f * alphaFactor).coerceIn(0f, 1f))
    }

    if (hasBlur && wallpaperBackdrop != null) {
        key(effectiveCornerRadius) {
            val backdropShape = remember(effectiveCornerRadius) { ContinuousRoundedRectangle(effectiveCornerRadius.dp) }
            val edgeLightShape = remember(effectiveCornerRadius) { ContinuousRoundedRectangle(effectiveCornerRadius.dp) }
            val density = LocalDensity.current
            val blurPx = with(density) { remember(cardBlurRadius) { cardBlurRadius.dp.toPx() } }
            val lensRadiusPx = with(density) { remember(cardRefraction) { cardRefraction.lensRadiusDp.dp.toPx() } }
            val lensStrengthPx = with(density) { remember(cardRefraction) { cardRefraction.lensStrengthDp.dp.toPx() } }
            val overlayColor = remember(isDark, alphaFactor) {
                if (isDark) Color.Black.copy(alpha = (0.15f * alphaFactor).coerceIn(0f, 1f))
                else Color.White.copy(alpha = (0.17f * alphaFactor).coerceIn(0f, 1f))
            }
            val isSharedBlur = wallpaperBackdrop is SharedBlurBackdrop
            val bandEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(isSharedBlur, blurPx, lensRadiusPx, lensStrengthPx, cardRefraction) {
                {
                    if (!isSharedBlur) {
                        blur(blurPx)
                    }
                    if (cardRefraction != com.haooz.chedule.data.CardRefractionLevel.OFF) {
                        lens(lensRadiusPx, lensStrengthPx)
                    }
                }
            }
            // 关键：onDrawSurface 必须固定下来。否则每次重组都是新的 lambda，
            // drawBackdrop 的 element 判不等 → 每次重组都重新录制壁纸层 + 重跑一次 GPU 模糊。
            // 滑动课表/拖动外观滑块时模糊层被反复重建，视觉上就表现为"特殊课程没有模糊"。
            val onBandSurface: DrawScope.() -> Unit = remember(bgColor, overlayColor) {
                {
                    drawRect(bgColor)
                    drawRect(overlayColor)
                }
            }
            // 与课程卡片一致的同色描边（缓存 outline，避免每帧重建路径）
            val outlineColor = remember(bgColor) { bgColor.copy(alpha = 0.05f) }
            val outlineStroke = remember(density) { Stroke(with(density) { 2.dp.toPx() }) }
            val outlineCache = remember { OutlineCache() }
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
                        downsampleScale = 0.48f,
                        onDrawSurface = onBandSurface
                    )
                    .drawWithContent {
                        drawContent()
                        val radiusDp = effectiveCornerRadius.dp
                        if (outlineCache.width != size.width ||
                            outlineCache.height != size.height ||
                            outlineCache.radius != radiusDp.value ||
                            outlineCache.layoutDirection != layoutDirection
                        ) {
                            outlineCache.outline = ContinuousRoundedRectangle(radiusDp)
                                .createOutline(size, layoutDirection, this)
                            outlineCache.width = size.width
                            outlineCache.height = size.height
                            outlineCache.radius = radiusDp.value
                            outlineCache.layoutDirection = layoutDirection
                        }
                        drawOutline(
                            outline = outlineCache.outline!!,
                            color = outlineColor,
                            style = outlineStroke
                        )
                    }
                    .edgeLight(shape = edgeLightShape, edgeLight = rememberCourseCardEdgeLight())
            ) {
                SpecialBandBody(
                    name = shownName,
                    items = items,
                    dayRange = dayRange,
                    isDark = isDark,
                    alphaFactor = alphaFactor,
                    cornerRadius = effectiveCornerRadius
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .drawBehind {
                    val cornerPx = effectiveCornerRadius.dp.toPx()
                    drawRoundRect(
                        color = bgColor,
                        cornerRadius = CornerRadius(cornerPx)
                    )
                }
        ) {
            SpecialBandBody(
                name = shownName,
                items = items,
                dayRange = dayRange,
                isDark = isDark,
                alphaFactor = alphaFactor,
                cornerRadius = effectiveCornerRadius
            )
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

/**
 * 特殊课程横带的内部内容。
 *
 * 当该横带内已划分星期子块时：按 [dayRange] 均分列宽，把每个子块渲染成跨列连续矩形
 * （如周一~周二一个矩形，周三单独一个），未被任何子块覆盖的星期渲染成透明可点击区域，
 * 点击后由外部弹出添加弹窗；点击已有矩形则进入编辑。
 *
 * 当没有子块时退化为原来的整条居中显示名称。
 */
@Composable
private fun SpecialBandBody(
    name: String,
    items: List<com.haooz.chedule.data.SpecialItem>,
    dayRange: List<Int>,
    isDark: Boolean,
    alphaFactor: Float,
    cornerRadius: Float
) {
    if (items.isEmpty() || dayRange.isEmpty()) {
        SpecialBandContent(name)
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val dayCount = dayRange.size
        val dayWidth = maxWidth / dayCount
        val itemShape = remember(cornerRadius) { ContinuousRoundedRectangle((cornerRadius * 0.8f).dp) }
        val itemBgColor = if (isDark) {
            Color.White.copy(alpha = (0.06f * alphaFactor).coerceIn(0f, 1f))
        } else {
            Color.Black.copy(alpha = (0.06f * alphaFactor).coerceIn(0f, 1f))
        }
        val itemTextColor = if (isDark) Color.White.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.74f)

        // 已划分的子块矩形：跨 startDay..endDay 连续
        items.forEach { item ->
            val fromIdx = dayRange.indexOf(item.startDay)
            val toIdx = dayRange.indexOf(item.endDay)
            // 该子块与当前显示的星期没有交集（如只在周末而周末未显示）时跳过
            if (fromIdx < 0 || toIdx < 0 || toIdx < fromIdx) return@forEach
            Box(
                modifier = Modifier
                    .offset(x = dayWidth * fromIdx)
                    .width(dayWidth * (toIdx - fromIdx + 1))
                    .fillMaxHeight()
                    // 相邻子卡间距 = 2+2=4dp；为了让外侧（首/尾卡到横带边缘）与内侧间距均衡：
                    // 所有卡上下间距 +2，最左卡左侧 +2、最右卡右侧 +2，内卡相互间距保持不变
                    .padding(
                        start = if (fromIdx == 0) 4.dp else 2.dp,
                        end = if (toIdx == dayRange.size - 1) 4.dp else 2.dp,
                        top = 4.dp,
                        bottom = 4.dp
                    )
                    .clip(itemShape)
                    .background(itemBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.name.ifBlank { "未命名" },
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                    color = itemTextColor,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 特殊课程横带的**点击交互层**（完全透明，只处理点击）。
 *
 * 必须放在课表 Row **之上**单独渲染：DayColumn 的「空节次交互层」是 fillMaxHeight 且带
 * pointerInput + detectTapGestures，会消费整个列高上的点击事件。横带若只在下层绘制，
 * 点击永远轮不到它 —— 视觉留在下层（避免遮挡自定义时间课程），点击由本层在上层接管。
 *
 * 子块矩形区域 → 编辑该子块；未被任何子块覆盖的星期 → 新增子块。
 */
@Composable
fun SpecialBandClickLayer(
    items: List<com.haooz.chedule.data.SpecialItem>,
    dayRange: List<Int>,
    onItemClick: (com.haooz.chedule.data.SpecialItem) -> Unit,
    onEmptyClick: (Int) -> Unit
) {
    if (dayRange.isEmpty()) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val dayWidth = maxWidth / dayRange.size

        items.forEach { item ->
            val fromIdx = dayRange.indexOf(item.startDay)
            val toIdx = dayRange.indexOf(item.endDay)
            if (fromIdx < 0 || toIdx < 0 || toIdx < fromIdx) return@forEach
            Box(
                modifier = Modifier
                    .offset(x = dayWidth * fromIdx)
                    .width(dayWidth * (toIdx - fromIdx + 1))
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onItemClick(item) }
            )
        }

        // 未被覆盖的星期：完全透明但可点击，点击后添加子块。
        // 填满时不存在空白格，因此天然满足"填满后不允许再添加"。
        dayRange.forEachIndexed { idx, day ->
            val occupied = items.any { item -> day in item.startDay..item.endDay }
            if (!occupied) {
                Box(
                    modifier = Modifier
                        .offset(x = dayWidth * idx)
                        .width(dayWidth)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onEmptyClick(day) }
                )
            }
        }
    }
}
