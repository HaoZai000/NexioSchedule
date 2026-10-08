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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.Course
import kotlinx.coroutines.delay
import com.haooz.chedule.data.HolidayCourseExclusion
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberCourseCardEdgeLight
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

// 包在 remember 里，避免每次重组重跑 groupBy/分段
private data class CourseRenderData(
    val course: Course,
    val isCurrentWeekCourse: Boolean,
    val hasHiddenCourses: Boolean,
    val segments: List<Pair<Int, Int>>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayColumn(
    dayOfWeek: Int,
    courses: List<Course>,
    onCourseClick: (Course) -> Unit,
    onEmptyClick: (Int) -> Unit,
    // 返回 Root 绝对坐标，供上层定位快捷菜单
    onEmptyLongPress: (section: Int, centerX: Float, cellTopY: Float, width: Float, height: Float) -> Unit = { _, _, _, _, _ -> },
    morningSections: Int = 4,
    afternoonSections: Int = 4,
    eveningSections: Int = 3,
    sectionTimes: Map<Int, String> = Course.defaultSectionTimes,
    specialBlocks: List<com.haooz.chedule.data.SpecialBlock> = emptyList(),
    // 页面层算好共享，避免 7 列各自重算
    grid: SpecialGridLayout,
    currentWeek: Int = 1,
    isHoliday: Boolean = false,
    holidayExemptCourseIds: Set<String> = emptySet(),
    holidayCancelledCourseIds: Set<String> = emptySet(),
    isWorkSwap: Boolean = false,
    pendingDay: Int = -1,
    pendingSection: Int = -1,
    onPendingChange: (day: Int, section: Int) -> Unit = { _, _ -> },
    wallpaperBackdrop: Backdrop? = null,
    solidBackingColor: Color? = null,
    cardBlurRadius: Float = 4f,
    cardAlpha: Float = 0.15f,
    cardSurfaceAlpha: Float = 0.15f,
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
    // 当前列需高亮的节次范围（含起止）
    dropHighlightSections: IntRange? = null,
    // 非 state：滑动中跳过逐帧坐标计算
    touchState: com.haooz.chedule.ui.screens.ScheduleTouchState? = null,
    // 页面层统一读取，避免每列挂 prefs 监听
    isDark: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val totalSectionsGrid = morningSections + afternoonSections + eveningSections
    val totalHeight = grid.totalHeight.toInt()
    val hasBlur = wallpaperBackdrop != null
    val isPendingDay = pendingDay == dayOfWeek
    val hapticFeedback = LocalHapticFeedback.current

    // 普通课按节次范围，自定义时间课按时间区间反查覆盖的节次
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
    // 与 PendingSectionBox 灰色风格对齐
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

            fun sectionTopDp(section: Int): Float = grid.sectionTop[section] ?: 0f

            // 单节点承载所有空节次点击/长按；落点高亮已提升到 MainScheduleScreen 动画遮罩
            val emptyLayerBounds = remember { FloatArray(4) }
            // 滑动中 onGloballyPositioned 跳过计算会留下过期/零点，长按瞬间按需重算一次
            val emptyLayerCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
            val refreshEmptyLayerBounds = {
                val c = emptyLayerCoords[0]
                if (c != null && c.isAttached) {
                    val pos = c.localToRoot(Offset.Zero)
                    emptyLayerBounds[0] = pos.x
                    emptyLayerBounds[1] = pos.y
                    emptyLayerBounds[2] = c.size.width.toFloat()
                    emptyLayerBounds[3] = c.size.height.toFloat()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .onGloballyPositioned { coordinates ->
                        emptyLayerCoords[0] = coordinates
                        if (touchState?.scrolling != true) {
                            val pos = coordinates.localToRoot(Offset.Zero)
                            emptyLayerBounds[0] = pos.x
                            emptyLayerBounds[1] = pos.y
                            emptyLayerBounds[2] = coordinates.size.width.toFloat()
                            emptyLayerBounds[3] = coordinates.size.height.toFloat()
                        }
                    }
                    .pointerInput(dayOfWeek, occupiedSections, totalSectionsGrid, perSectionPx, specialBlocks, grid) {
                        detectTapGestures(
                            onTap = { offset ->
                                val y = offset.y
                                // 用实际节次 top 反查（含特殊块挤占），分界带/特殊块无匹配则忽略
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
                                    refreshEmptyLayerBounds()
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

            // 特殊横带在整表层渲染；独立成层使周切换只重组本层，静态骨架可跳过
            CourseCardsLayer(
                courses = courses,
                currentWeek = currentWeek,
                isHoliday = isHoliday,
                holidayExemptCourseIds = holidayExemptCourseIds,
                holidayCancelledCourseIds = holidayCancelledCourseIds,
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
                cardSurfaceAlpha = cardSurfaceAlpha,
                isTablet = isTablet,
                cardContentAlignment = cardContentAlignment,
                cardTextColor = cardTextColor,
                cardTextScale = cardTextScale,
                showClassroom = showClassroom,
                showTeacher = showTeacher,
                cardRefraction = cardRefraction,
                wallpaperBackdrop = wallpaperBackdrop,
                solidBackingColor = solidBackingColor,
                cardBlurRadius = cardBlurRadius,
                draggingCourseIds = draggingCourseIds,
                touchState = touchState,
                isDark = isDark,

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

@Composable
private fun CourseCardsLayer(
    courses: List<Course>,
    currentWeek: Int,
    isHoliday: Boolean,
    holidayExemptCourseIds: Set<String>,
    holidayCancelledCourseIds: Set<String>,
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
    cardSurfaceAlpha: Float = 0.15f,
    isTablet: Boolean,
    cardContentAlignment: com.haooz.chedule.data.CardContentAlignment,
    cardTextColor: com.haooz.chedule.data.CardTextColor,
    cardTextScale: Float,
    showClassroom: Boolean,
    showTeacher: Boolean,
    cardRefraction: com.haooz.chedule.data.CardRefractionLevel,
    wallpaperBackdrop: Backdrop?,
    solidBackingColor: Color? = null,
    cardBlurRadius: Float,
    draggingCourseIds: Set<String>,
    touchState: com.haooz.chedule.ui.screens.ScheduleTouchState? = null,
    isDark: Boolean,
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
    val courseRenderDataList = remember(
        courses,
        currentWeek,
        holidayCancelledCourseIds,
        showBreakDividers,
        morningSections,
        afternoonSections,
        eveningSections,
    ) {
        val coursesBySection = courses.groupBy { courseSlotKey(it) }
        val displayedCourses = mutableListOf<Course>()
        val hiddenCoursesMap = mutableMapOf<String, List<Course>>()

        coursesBySection.forEach { (slotKey, sectionCourses) ->
            val (currentWeekCourses, otherCourses) = sectionCourses.partition { it.isActiveInWeek(currentWeek) }

            if (currentWeekCourses.isNotEmpty()) {
                val displaySelection = HolidayCourseExclusion.selectDisplayCourses(
                    currentWeekCourses,
                    holidayCancelledCourseIds,
                )
                val courseToShow = requireNotNull(displaySelection.representative)
                displayedCourses.add(courseToShow)
                val hidden = displaySelection.hidden + otherCourses
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

        // 自定义时间课按时间轴插值定位/定高，不按节次分段
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
                        touchState = touchState,
                        isDark = isDark,
                        isCurrentWeek = isCurrentWeekCourse,
                        isHoliday = isHoliday && course.id !in holidayExemptCourseIds,
                        isCourseCancelled = course.id in holidayCancelledCourseIds,
                        isWorkSwap = isWorkSwap,
                        hasMultipleCourses = renderData.hasHiddenCourses,
                        wallpaperBackdrop = wallpaperBackdrop,
                        cardBlurRadius = cardBlurRadius,
                        cardAlpha = cardAlpha,
                        cardSurfaceAlpha = cardSurfaceAlpha,
                        solidBackingColor = solidBackingColor,
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
                    )
                }
            }
            return@forEach
        }

        renderData.segments.forEachIndexed { idx, (segStartSection, segEndSection) ->
            // 键必须带 course 本身：只记 id/节次时，改课程颜色/名称/教室后课程对象变了但键没变，
            // 分段副本会被缓存成旧值，卡片要等到重新进入/滑走才刷新
            val displayCourse = remember(course, segStartSection, segEndSection) {
                course.copy(startSection = segStartSection, endSection = segEndSection)
            }
            val segOffset = (grid.sectionTop[segStartSection] ?: 0f).toInt()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = segOffset.dp)
                ) {
                    CourseCard(
                        course = displayCourse,
                        touchState = touchState,
                        isDark = isDark,
                        isCurrentWeek = isCurrentWeekCourse,
                        isHoliday = isHoliday && course.id !in holidayExemptCourseIds,
                        isCourseCancelled = course.id in holidayCancelledCourseIds,
                        isWorkSwap = isWorkSwap,
                        hasMultipleCourses = idx == 0 && renderData.hasHiddenCourses,
                        wallpaperBackdrop = wallpaperBackdrop,
                        cardBlurRadius = cardBlurRadius,
                        cardAlpha = cardAlpha,
                        cardSurfaceAlpha = cardSurfaceAlpha,
                        solidBackingColor = solidBackingColor,
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
                val pendingOnSurface: DrawScope.() -> Unit = remember(surfaceColor) {
                    { drawRect(surfaceColor) }
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
                            viewport = com.kyant.backdrop.LocalBackdropViewport.current,
                            onDrawSurface = pendingOnSurface
                        )
                        .edgeLight(shape = edgeLightShape, edgeLight = rememberCourseCardEdgeLight(baseColor = surfaceColor))
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

// 自定义时间课用起止时间区分槽位，避免同时段不同节被折叠合并
private fun courseSlotKey(course: Course): String {
    if (course.hasValidCustomTime()) {
        return "custom|${course.customStartTime}|${course.customEndTime}"
    }
    return "section|${course.startSection}"
}

private fun parseMinutes(time: String?): Int {
    if (time.isNullOrBlank()) return -1
    val parts = time.split(":")
    if (parts.size != 2) return -1
    val h = parts[0].toIntOrNull() ?: return -1
    val m = parts[1].toIntOrNull() ?: return -1
    return h * 60 + m
}

private data class CustomTimeLayout(
    val topDp: Float,
    val heightDp: Float
)

// 节内按时间比例插值；跨午/晚休用统一分钟→Y；超范围钳到列顶/底
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

    fun timeToY(minutes: Int): Float {
        for (info in sections) {
            if (minutes <= info.end) {
                val sectionTop = grid.sectionTop[info.index] ?: 0f
                val fraction = if (info.end > info.start) {
                    ((minutes - info.start).toFloat() / (info.end - info.start)).coerceIn(0f, 1f)
                } else 0f
                return sectionTop + cardHeightPerSection * fraction
            }
        }
        return columnBottom
    }

    fun timeToYClamped(minutes: Int): Float {
        if (minutes <= sections.first().start) return 0f
        return timeToY(minutes).coerceIn(0f, columnBottom)
    }

    val top = timeToYClamped(cs)
    val bottom = timeToYClamped(ce)
    return CustomTimeLayout(top, (bottom - top).coerceAtLeast(0f))
}

/**
 * 当前时刻是否落在 [startTime]~[endTime] 内，用于给"正在进行"的横带加高亮。
 *
 * 自带 30 秒节拍，状态读在调用方自己的作用域里 —— 只有横带重组，不会带动整页。
 */
@Composable
internal fun rememberIsOngoing(startTime: String, endTime: String): Boolean {
    var ongoing by remember(startTime, endTime) { mutableStateOf(false) }
    LaunchedEffect(startTime, endTime) {
        while (true) {
            val now = java.time.LocalTime.now()
            val nowMinutes = now.hour * 60 + now.minute
            ongoing = parseSpecialHm(startTime)?.let { s ->
                val e = parseSpecialHm(endTime) ?: return@let false
                nowMinutes in s until e
            } ?: false
            delay(30_000L)
        }
    }
    return ongoing
}

private fun parseSpecialHm(time: String?): Int? {
    if (time.isNullOrBlank()) return null
    val parts = time.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

@Composable
fun SpecialBandOverlay(
    name: String,
    startTime: String,
    endTime: String,
    hasBlur: Boolean,
    isDark: Boolean,
    cardCornerRadius: Float,
    cardBlurRadius: Float,
    cardAlpha: Float,
    cardSurfaceAlpha: Float = 0.15f,
    cardRefraction: com.haooz.chedule.data.CardRefractionLevel = com.haooz.chedule.data.CardRefractionLevel.DEFAULT,
    isTablet: Boolean = false,
    wallpaperBackdrop: Backdrop?,
    // 子块为空时退化为整条显示名称
    items: List<com.haooz.chedule.data.SpecialItem> = emptyList(),
    // **每个可见列实际显示的星期**（长度=列数），不是列下标：调休日该列画的是被调星期的课。
    // 智能周末下可能只有 1..5，调休时也可能与列下标不一致，决定子块定位与列宽
    dayRange: List<Int> = emptyList()
) {
    val shownName = name.ifBlank { "特殊课程" }
    // 默认 cardAlpha=0.15 时因子为 1；仅保护最终 alpha
    val alphaFactor = cardAlpha / 0.15f
    val effectiveCornerRadius = if (isTablet) cardCornerRadius * 1.3f else cardCornerRadius
    val isOngoing = rememberIsOngoing(startTime, endTime)
    val bgColor = if (isOngoing) {
        // 进行中：底色抬一档，和左侧时间列的 currentSection 高亮同一套视觉语言
        Color(0xFF3482FF).copy(alpha = (0.16f * alphaFactor).coerceIn(0f, 1f))
    } else if (isDark) {
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
            val overlayColor = remember(isDark, cardSurfaceAlpha) {
                if (isDark) Color.Black.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
                else Color.White.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
            }
            val isSharedBlur = wallpaperBackdrop is SharedBlurBackdrop
            val bandEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
                remember(isSharedBlur, blurPx, lensRadiusPx, lensStrengthPx, cardRefraction) {
                    {
                        if (!isSharedBlur) {
                            blur(blurPx)
                        }
                        if (cardRefraction != com.haooz.chedule.data.CardRefractionLevel.OFF) {
                            lens(lensRadiusPx, lensStrengthPx)
                        }
                    }
                }
            // onDrawSurface 必须固定，否则每次重组都重新录制壁纸层并重跑 GPU 模糊
            val onBandSurface: DrawScope.() -> Unit = remember(bgColor, overlayColor) {
                {
                    drawRect(bgColor)
                    drawRect(overlayColor)
                }
            }
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
                        viewport = com.kyant.backdrop.LocalBackdropViewport.current,
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
                    .edgeLight(shape = edgeLightShape, edgeLight = rememberCourseCardEdgeLight(baseColor = bgColor))
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
private fun SpecialBandContent(name: String, isDark: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
            color = if (isDark) Color.White.copy(alpha = 0.74f) else Color.Black.copy(alpha = 0.74f),
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

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
        SpecialBandContent(name, isDark)
        return
    }

    val segments = remember(items, dayRange) { buildSpecialBandSegments(items, dayRange) }
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

        segments.forEach { seg ->
            val item = seg.item ?: return@forEach
            // 没排安排的星期不画任何东西（曾试过常驻 "+" 提示，太吵）。
            // 横带自身的底色是整条连续的，空格子读作「这段还没排」而不是「条带子断了」；
            // 点击层仍铺满空格子，点一下就是新增安排，弹窗会预选该星期。
            val lastIdx = dayRange.size - 1
            Box(
                modifier = Modifier
                    .offset(x = dayWidth * seg.startIndex)
                    .width(dayWidth * (seg.endIndex - seg.startIndex + 1))
                    .fillMaxHeight()
                    // 首/尾卡外侧 +2，与内侧相邻间距 4dp 均衡
                    .padding(
                        start = if (seg.startIndex == 0) 4.dp else 2.dp,
                        end = if (seg.endIndex == lastIdx) 4.dp else 2.dp,
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

// 必须叠在课表 Row 之上：空节次层会消费整列点击，下层横带收不到
@Composable
fun SpecialBandClickLayer(
    items: List<com.haooz.chedule.data.SpecialItem>,
    dayRange: List<Int>,
    onItemClick: (com.haooz.chedule.data.SpecialItem) -> Unit,
    onEmptyClick: (Int) -> Unit
) {
    if (dayRange.isEmpty()) return

    val segments = remember(items, dayRange) { buildSpecialBandSegments(items, dayRange) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val dayWidth = maxWidth / dayRange.size

        segments.forEach { seg ->
            val item = seg.item
            Box(
                modifier = Modifier
                    .offset(x = dayWidth * seg.startIndex)
                    .width(dayWidth * (seg.endIndex - seg.startIndex + 1))
                    .fillMaxHeight()
                    .clickable(
                        enabled = item != null,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { item?.let(onItemClick) }
            )
        }

        // 未被覆盖的星期可点击新增；填满后无空白格即天然禁止再加
        segments.forEach { seg ->
            if (seg.item != null) return@forEach
            for (idx in seg.startIndex..seg.endIndex) {
                val day = dayRange[idx]
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

/** 横带里一段连续列：item 为 null 表示这段列还没排安排 */
internal data class SpecialBandSegment(
    val item: com.haooz.chedule.data.SpecialItem?,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * 把「列 → 该列实际显示的星期」解析成横带内的连续区段。
 *
 * [columnDays] 是**列位置对应的显示星期**，不是列下标：调休日的列显示的是被调星期
 * （周一补班 → 该列画周日的课），子块必须跟着显示星期走，否则「周日早读」会留在周位列上。
 * 早先直接拿 1..7 当列下标查 indexOf，调休周整排错位。
 *
 * 相邻同 id 的列合成一段，避免一个「周一~周五」的子块被逐列拆成一排小格；
 * 两段不相邻的同日子块（如调休后周一与周日都显示周日）会各画各的，不连成一条。
 */
internal fun buildSpecialBandSegments(
    items: List<com.haooz.chedule.data.SpecialItem>,
    columnDays: List<Int>
): List<SpecialBandSegment> {
    if (columnDays.isEmpty()) return emptyList()
    val segments = mutableListOf<SpecialBandSegment>()
    columnDays.forEachIndexed { idx, day ->
        val item = items.firstOrNull { day in it.startDay..it.endDay }
        val last = segments.lastOrNull()
        if (last != null && last.item?.id == item?.id && last.endIndex == idx - 1) {
            segments[segments.lastIndex] = last.copy(endIndex = idx)
        } else {
            segments.add(SpecialBandSegment(item, idx, idx))
        }
    }
    return segments
}
