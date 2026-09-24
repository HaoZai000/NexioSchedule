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
    isWorkSwap: Boolean = false,
    pendingDay: Int = -1,
    pendingSection: Int = -1,
    onPendingChange: (day: Int, section: Int) -> Unit = { _, _ -> },
    wallpaperBackdrop: Backdrop? = null,
    cardBlurRadius: Float = 0f,
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
    gridScrollFlag: com.haooz.chedule.ui.screens.GridScrollFlag? = null,
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
                cardBlurRadius = cardBlurRadius,
                draggingCourseIds = draggingCourseIds,
                gridScrollFlag = gridScrollFlag,
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
    cardBlurRadius: Float,
    draggingCourseIds: Set<String>,
    gridScrollFlag: com.haooz.chedule.ui.screens.GridScrollFlag? = null,
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
    val courseRenderDataList = remember(courses, currentWeek, showBreakDividers, morningSections, afternoonSections, eveningSections) {
        val coursesBySection = courses.groupBy { courseSlotKey(it) }
        val displayedCourses = mutableListOf<Course>()
        val hiddenCoursesMap = mutableMapOf<String, List<Course>>()

        coursesBySection.forEach { (slotKey, sectionCourses) ->
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
                        gridScrollFlag = gridScrollFlag,
                        isDark = isDark,
                        isCurrentWeek = isCurrentWeekCourse,
                        isHoliday = isHoliday,
                        isWorkSwap = isWorkSwap,
                        hasMultipleCourses = renderData.hasHiddenCourses,
                        wallpaperBackdrop = wallpaperBackdrop,
                        cardBlurRadius = cardBlurRadius,
                        cardAlpha = cardAlpha,
                        cardSurfaceAlpha = cardSurfaceAlpha,
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
            val displayCourse = remember(course.id, segStartSection, segEndSection) {
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
                        gridScrollFlag = gridScrollFlag,
                        isDark = isDark,
                        isCurrentWeek = isCurrentWeekCourse,
                        isHoliday = isHoliday,
                        isWorkSwap = isWorkSwap,
                        hasMultipleCourses = idx == 0 && renderData.hasHiddenCourses,
                        wallpaperBackdrop = wallpaperBackdrop,
                        cardBlurRadius = cardBlurRadius,
                        cardAlpha = cardAlpha,
                        cardSurfaceAlpha = cardSurfaceAlpha,
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

@Composable
fun SpecialBandOverlay(
    name: String,
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
    // 智能周末下可能只有 1..5，决定列宽与子块定位
    dayRange: List<Int> = emptyList()
) {
    val shownName = name.ifBlank { "特殊课程" }
    // 默认 cardAlpha=0.15 时因子为 1；仅保护最终 alpha
    val alphaFactor = cardAlpha / 0.15f
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

        items.forEach { item ->
            val fromIdx = dayRange.indexOf(item.startDay)
            val toIdx = dayRange.indexOf(item.endDay)
            if (fromIdx < 0 || toIdx < 0 || toIdx < fromIdx) return@forEach
            Box(
                modifier = Modifier
                    .offset(x = dayWidth * fromIdx)
                    .width(dayWidth * (toIdx - fromIdx + 1))
                    .fillMaxHeight()
                    // 首/尾卡外侧 +2，与内侧相邻间距 4dp 均衡
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

// 必须叠在课表 Row 之上：空节次层会消费整列点击，下层横带收不到
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

        // 未被覆盖的星期可点击新增；填满后无空白格即天然禁止再加
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
