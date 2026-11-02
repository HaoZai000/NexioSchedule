package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.appearance.CardContentAlignment
import com.haooz.chedule.data.appearance.CardRefractionLevel
import com.haooz.chedule.data.appearance.CardTextColor
import com.haooz.chedule.data.holiday.HolidayCourseExclusion
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.SpecialBlock
import com.haooz.chedule.data.model.SpecialItem
import com.haooz.chedule.data.model.pickNonCurrentWeekCourse
import com.haooz.chedule.data.util.CourseTimeSpan
import com.haooz.chedule.data.util.nowLocalTime
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.theme.scheduleAccentColor
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.LocalBackdropViewport
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
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
    specialBlocks: List<SpecialBlock> = emptyList(),
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
    cardContentAlignment: CardContentAlignment = CardContentAlignment.CENTER_CENTER,
    cardTextColor: CardTextColor = CardTextColor.COLORFUL,
    cardTextScale: Float = 1f,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    cardRefraction: CardRefractionLevel = CardRefractionLevel.DEFAULT,
    draggingCourseSlots: Set<Pair<String, Int>> = emptySet(),
    onCourseLongPress: (course: Course, cardLeft: Float, cardTop: Float, width: Float, height: Float, backdrop: Backdrop?, currentWeek: Int) -> Unit = { _, _, _, _, _, _, _ -> },
    onCourseDragStart: (courseId: String) -> Unit = { _ -> },
    onCourseDrag: (courseId: String, offsetX: Float, offsetY: Float) -> Unit = { _, _, _ -> },
    onCourseDragEnd: (courseId: String) -> Unit = { _ -> },
    onCourseDragCancel: (courseId: String) -> Unit = { _ -> },
    onCourseMenuDismiss: () -> Unit = {},
    // 非 state：滑动中跳过逐帧坐标计算
    touchState: ScheduleTouchState? = null,
    // 页面层统一读取，避免每列挂 prefs 监听
    isDark: Boolean = false,
    modifier: Modifier = Modifier
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
                    if (cs >= 0 && ce >= 0 && cs != ce) {
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
                        // 跨午夜（ce < cs）的课还要占住当天 00:00 之前的节次：
                        // 次日 00:30 结束 = 当天 23:00 起延续，覆盖当天所有靠后的节次
                        if (ce < cs) {
                            for (section in 1..totalSectionsGrid) {
                                val timeStr = sectionTimes[section] ?: continue
                                val parts = timeStr.split("-")
                                if (parts.size != 2) continue
                                val ss = parseMinutes(parts[0])
                                if (ss >= 0 && ss >= cs) add(section)
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
    val dropHighlightColor = (if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.primary else Color(0xFF9E9E9E))
        .copy(alpha = if (isDark) 0.13f else 0.15f)

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
                        cardRefraction = cardRefraction,
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
                draggingCourseSlots = draggingCourseSlots,
                touchState = touchState,
                isDark = isDark,

                onCourseClick = onCourseClick,
                onCourseLongPress = onCourseLongPress,
                onCourseDragStart = onCourseDragStart,
                onCourseDrag = onCourseDrag,
                onCourseDragEnd = onCourseDragEnd,
                onCourseDragCancel = onCourseDragCancel,
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
    cardContentAlignment: CardContentAlignment,
    cardTextColor: CardTextColor,
    cardTextScale: Float,
    showClassroom: Boolean,
    showTeacher: Boolean,
    cardRefraction: CardRefractionLevel,
    wallpaperBackdrop: Backdrop?,
    solidBackingColor: Color? = null,
    cardBlurRadius: Float,
    draggingCourseSlots: Set<Pair<String, Int>>,
    touchState: ScheduleTouchState? = null,
    isDark: Boolean,
    viewportTopDp: Float = 0f,
    viewportBottomDp: Float = Float.MAX_VALUE,
    onCourseClick: (Course) -> Unit,
    onCourseLongPress: (Course, Float, Float, Float, Float, Backdrop?, Int) -> Unit,
    onCourseDragStart: (String) -> Unit,
    onCourseDrag: (String, Float, Float) -> Unit,
    onCourseDragEnd: (String) -> Unit,
    onCourseDragCancel: (String) -> Unit,
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
                // 本周没课：按「谁下一次更早会上」挑一条（见 pickNonCurrentWeekCourse 的注释）
                val courseToShow = pickNonCurrentWeekCourse(otherCourses, currentWeek)
                    ?: return@forEach
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
        // ⚠ 本周课必须排在渲染序列**末尾**
        .sortedBy { if (it.isCurrentWeekCourse) 1 else 0 }
    }

    courseRenderDataList.forEach { renderData ->
        val course = renderData.course
        val isCurrentWeekCourse = renderData.isCurrentWeekCourse
        val isDragging = (course.id to currentWeek) in draggingCourseSlots && isCurrentWeekCourse

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
                // offset 落到课卡自身节点，省掉每卡一个 Box 包装层
                CourseCard(
                    modifier = Modifier.offset(y = layout.topDp.dp),
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
                    // 自定义时间课不支持拖动调课（按时间轴定位，没有「挪到第几节」的落点），
                    // 长按只弹「编辑 / 删除」菜单
                    dragEnabled = false,
                    onClick = {
                        onPendingChange(-1, -1)
                        onCourseClick(course)
                    },
                    onLongPressStart = { left, top, width, height ->
                        if (isCurrentWeekCourse) {
                            onCourseLongPress(course, left, top, width, height, wallpaperBackdrop, currentWeek)
                        }
                    },
                    onMenuDismiss = {
                        onCourseMenuDismiss()
                    },
                )
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

            // 同上：offset 落在课卡自身节点
            CourseCard(
                modifier = Modifier.offset(y = segOffset.dp),
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
                onDragCancel = {
                    onCourseDragCancel(course.id)
                },
                onMenuDismiss = {
                    onCourseMenuDismiss()
                }
            )
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
    cardRefraction: CardRefractionLevel,
    wallpaperBackdrop: Backdrop?,
    hapticFeedback: HapticFeedback,
    onEmptyClick: (Int) -> Unit
) {
    PendingAddCard(
        hasBlur = hasBlur,
        isDark = isDark,
        cardCornerRadius = cardCornerRadius,
        cardBlurRadius = cardBlurRadius,
        cardRefraction = cardRefraction,
        wallpaperBackdrop = wallpaperBackdrop,
        modifier = Modifier.fillMaxSize(),
    ) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        onEmptyClick(section)
    }
}

/** 加号卡片：网格空槽与时间轴空白点击共用同一张，外观只在这里改。 */
@Composable
internal fun PendingAddCard(
    hasBlur: Boolean,
    isDark: Boolean,
    cardCornerRadius: Float,
    cardBlurRadius: Float,
    cardRefraction: CardRefractionLevel,
    wallpaperBackdrop: Backdrop?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pendingColor = (if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.primary else Color(0xFF9E9E9E))
        .copy(alpha = if (isDark) 0.13f else 0.15f)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (hasBlur) {
            key(cardCornerRadius) {
                val backdropShape = remember(cardCornerRadius) { ContinuousRoundedRectangle(cardCornerRadius.dp) }
                val density = LocalDensity.current
                // pendingColor 还跟着动态取色 / 主题主色变，只 key isDark 会留下旧描边色
                val outlineColor = remember(pendingColor, isDark) { scheduleOutlineColor(pendingColor, isDark) }
                val outlineWidthPx = remember(density) { with(density) { SCHEDULE_OUTLINE_WIDTH.toPx() } }
                val outlineCache = remember { OutlineCache() }
                val blurPx = with(density) { remember(cardBlurRadius) { cardBlurRadius.dp.toPx() } }
                val surfaceColor = if (MiuixTheme.isDynamicColor) {
                    MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.64f else 0.5f)
                } else if (isDark) Color(0xFF242424).copy(alpha = 0.64f) else Color(0xFFF0F0F0).copy(alpha = 0.5f)
                val isSharedBlur = wallpaperBackdrop is SharedBlurBackdrop
                // 折射与课程卡同一套档位参数（跟随「卡片折射」设置）
                val lensRadiusPx = with(density) { remember(cardRefraction) { cardRefraction.lensRadiusDp.dp.toPx() } }
                val lensStrengthPx = with(density) { remember(cardRefraction) { cardRefraction.lensStrengthDp.dp.toPx() } }
                val pendingEffects: BackdropEffectScope.() -> Unit =
                    remember(isSharedBlur, blurPx, lensRadiusPx, lensStrengthPx, cardRefraction) {
                        {
                            if (!isSharedBlur) {
                                blur(blurPx)
                            }
                            if (cardRefraction != CardRefractionLevel.OFF) {
                                lens(lensRadiusPx, lensStrengthPx)
                            }
                        }
                    }
                val pendingOnSurface: DrawScope.() -> Unit = remember(surfaceColor) {
                    { drawRect(surfaceColor) }
                }
                // 与 CourseCard 同口径：模糊 0 且无折射时采样回来的就是格子正下方的壁纸像素，
                // 退化成一次圆角填充。空格子数量比有课的格子还多，这里是采样次数的大头。
                val pendingNeedsSampling =
                    cardBlurRadius > 0f || cardRefraction != CardRefractionLevel.OFF
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .then(
                            if (pendingNeedsSampling) {
                                Modifier.drawBackdrop(
                                    backdrop = wallpaperBackdrop!!,
                                    shape = { backdropShape },
                                    effects = pendingEffects,
                                    highlight = null,
                                    shadow = null,
                                    viewport = LocalBackdropViewport.current,
                                    onDrawSurface = pendingOnSurface
                                )
                            } else {
                                Modifier.background(surfaceColor, backdropShape)
                            }
                        )
                        .drawWithContent {
                            drawContent()
                            drawScheduleOutline(
                                cache = outlineCache,
                                cornerRadius = cardCornerRadius.dp,
                                color = outlineColor,
                                strokeWidthPx = outlineWidthPx,
                                layoutDirection = layoutDirection,
                            )
                        }
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
                        onClick = onClick
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Add,
                                contentDescription = CommonStr.add(),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // 纯色模式（无壁纸）不画描边，与课程卡一致
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                cornerRadius = cardCornerRadius.dp,
                insideMargin = PaddingValues(0.dp),
                pressFeedbackType = PressFeedbackType.Sink,
                showIndication = true,
                colors = CardDefaults.defaultColors(
                    color = pendingColor,
                    contentColor = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.onSurfaceVariantActions
                        else Color(0xFF9E9E9E).copy(alpha = 0.5f)
                ),
                onClick = onClick
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = CommonStr.add(),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * 槽位键：网格与时间轴共用的「同一格」判据。
 * 自定义时间课按起止时间区分，其余只按**起始节次**（8 节与 8-9 节是同一格）。
 */
internal fun courseSlotKey(course: Course): String {
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
    if (cs < 0 || ce < 0 || ce == cs) return null

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

    // 跨午夜（结束早于开始）的课在当天的课表格里没有「次日」这一格，
    // 按「从开始一直画到当天最后一节」处理，至少不会退化成 0 高度。
    val top = timeToYClamped(cs)
    val bottom = if (ce < cs) columnBottom else timeToYClamped(ce)
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
            val now = nowLocalTime()
            val nowMinutes = now.hour * 60 + now.minute
            ongoing = parseSpecialHm(startTime)?.let { s ->
                val e = parseSpecialHm(endTime) ?: return@let false
                CourseTimeSpan.isActiveAt(s, e, nowMinutes)
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
    cardRefraction: CardRefractionLevel = CardRefractionLevel.DEFAULT,
    isTablet: Boolean = false,
    wallpaperBackdrop: Backdrop?,
    // 子块为空时退化为整条显示名称
    items: List<SpecialItem> = emptyList(),
    // **每个可见列实际显示的星期**（长度=列数），不是列下标：调休日该列画的是被调星期的课。
    // 智能周末下可能只有 1..5，调休时也可能与列下标不一致，决定子块定位与列宽
    dayRange: List<Int> = emptyList()
) {
    val shownName = name.ifBlank { SchedStr.specialCourses() }
    // 默认 cardAlpha=0.15 时因子为 1；仅保护最终 alpha
    val alphaFactor = cardAlpha / 0.15f
    val effectiveCornerRadius = if (isTablet) cardCornerRadius * 1.3f else cardCornerRadius
    val isOngoing = rememberIsOngoing(startTime, endTime)
    val bgColor = if (isOngoing) {
        // 进行中：底色抬一档，和左侧时间列的 currentSection 高亮同一套视觉语言
        scheduleAccentColor().copy(alpha = (0.16f * alphaFactor).coerceIn(0f, 1f))
    } else if (isDark) {
        Color.White.copy(alpha = (0.06f * alphaFactor).coerceIn(0f, 1f))
    } else {
        Color.Black.copy(alpha = (0.04f * alphaFactor).coerceIn(0f, 1f))
    }

    if (hasBlur && wallpaperBackdrop != null) {
        key(effectiveCornerRadius) {
            val backdropShape = remember(effectiveCornerRadius) { ContinuousRoundedRectangle(effectiveCornerRadius.dp) }
            val density = LocalDensity.current
            val blurPx = with(density) { remember(cardBlurRadius) { cardBlurRadius.dp.toPx() } }
            val lensRadiusPx = with(density) { remember(cardRefraction) { cardRefraction.lensRadiusDp.dp.toPx() } }
            val lensStrengthPx = with(density) { remember(cardRefraction) { cardRefraction.lensStrengthDp.dp.toPx() } }
            val overlayColor = remember(isDark, cardSurfaceAlpha) {
                if (isDark) Color.Black.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
                else Color.White.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
            }
            // SharedBlurBackdrop 的 drawBackdrop 画已烘焙的模糊层，不用再 blur
            val isSharedBlur = wallpaperBackdrop is SharedBlurBackdrop
            val bandEffects: BackdropEffectScope.() -> Unit =
                remember(isSharedBlur, blurPx, lensRadiusPx, lensStrengthPx, cardRefraction) {
                    {
                        if (!isSharedBlur) {
                            blur(blurPx)
                        }
                        if (cardRefraction != CardRefractionLevel.OFF) {
                            lens(lensRadiusPx, lensStrengthPx)
                        }
                    }
                }
            // 两层纯色压成一次 drawRect（见 flattenStackedFills）
            val bandSurfaceColor = remember(bgColor, overlayColor) {
                flattenStackedFills(bgColor, overlayColor)
            }
            // onDrawSurface 必须固定，否则每次重组都重新录制壁纸层并重跑 GPU 模糊
            val onBandSurface: DrawScope.() -> Unit = remember(bandSurfaceColor) {
                { drawRect(bandSurfaceColor) }
            }
            val outlineColor = remember(bgColor, isDark) { scheduleOutlineColor(bgColor, isDark) }
            val outlineWidthPx = remember(density) { with(density) { SCHEDULE_OUTLINE_WIDTH.toPx() } }
            val outlineCache = remember { OutlineCache() }
            // 同 pending 格：模糊 0 且无折射时采样是纯冗余，退化成圆角填充
            val bandNeedsSampling =
                cardBlurRadius > 0f || cardRefraction != CardRefractionLevel.OFF
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .then(
                        if (bandNeedsSampling) {
                            Modifier.drawBackdrop(
                                backdrop = wallpaperBackdrop,
                                shape = { backdropShape },
                                effects = bandEffects,
                                highlight = null,
                                shadow = null,
                                viewport = LocalBackdropViewport.current,
                                onDrawSurface = onBandSurface
                            )
                        } else {
                            Modifier.background(bandSurfaceColor, backdropShape)
                        }
                    )
                    .drawWithContent {
                        drawContent()
                        drawScheduleOutline(
                            cache = outlineCache,
                            cornerRadius = effectiveCornerRadius.dp,
                            color = outlineColor,
                            strokeWidthPx = outlineWidthPx,
                            layoutDirection = layoutDirection,
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
    } else {
        // 纯色模式（无壁纸）不画描边，与课程卡一致
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
    items: List<SpecialItem>,
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
    // 只画有安排的段：没排安排的星期不画任何东西（曾试过常驻 "+" 提示，太吵）。
    // 横带自身的底色是整条连续的，空格子读作「这段还没排」而不是「条带子断了」；
    // 点击层仍铺满空格子，点一下就是新增安排，弹窗会预选该星期。
    val drawn = remember(segments) { segments.filter { it.item != null } }
    val dayCount = dayRange.size
    val itemShape = remember(cornerRadius) { ContinuousRoundedRectangle((cornerRadius * 0.8f).dp) }
    val itemBgColor = if (isDark) {
        Color.White.copy(alpha = (0.06f * alphaFactor).coerceIn(0f, 1f))
    } else {
        Color.Black.copy(alpha = (0.06f * alphaFactor).coerceIn(0f, 1f))
    }
    val itemTextColor = if (isDark) Color.White.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.74f)

    // 自绘 Layout 取代 BoxWithConstraints：这里只需按列宽比例摆位，没必要付 SubcomposeLayout 的子组合
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            drawn.forEach { seg ->
                val item = seg.item ?: return@forEach
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        // 首/尾卡外侧 +2，与内侧相邻间距 4dp 均衡
                        .padding(
                            start = if (seg.startIndex == 0) 4.dp else 2.dp,
                            end = if (seg.endIndex == dayCount - 1) 4.dp else 2.dp,
                            top = 4.dp,
                            bottom = 4.dp
                        )
                        .clip(itemShape)
                        .background(itemBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.name.ifBlank { SchedStr.unnamed() },
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                        color = itemTextColor,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val dayWidthPx = constraints.maxWidth.toFloat() / dayCount
        val placeables = measurables.mapIndexed { i, measurable ->
            val span = drawn[i].endIndex - drawn[i].startIndex + 1
            measurable.measure(Constraints.fixed((dayWidthPx * span).roundToInt(), h))
        }
        layout(constraints.maxWidth, h) {
            placeables.forEachIndexed { i, placeable ->
                placeable.place((dayWidthPx * drawn[i].startIndex).roundToInt(), 0)
            }
        }
    }
}

// 必须叠在课表 Row 之上：空节次层会消费整列点击，下层横带收不到
@Composable
fun SpecialBandClickLayer(
    items: List<SpecialItem>,
    dayRange: List<Int>,
    onItemClick: (SpecialItem) -> Unit,
    onEmptyClick: (Int) -> Unit
) {
    if (dayRange.isEmpty()) return

    val segments = remember(items, dayRange) { buildSpecialBandSegments(items, dayRange) }
    val dayCount = dayRange.size
    // 有安排段 / 空白列各自一条 place 序列，同样用自绘 Layout 取代 BoxWithConstraints
    val filled = remember(segments) { segments.filter { it.item != null } }
    val blankDays = remember(segments) {
        buildList {
            segments.forEach { seg ->
                if (seg.item != null) return@forEach
                for (idx in seg.startIndex..seg.endIndex) add(idx)
            }
        }
    }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            filled.forEach { seg ->
                val item = seg.item ?: return@forEach
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable(
                            enabled = true,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemClick(item) }
                )
            }
            // 未被覆盖的星期可点击新增；填满后无空白格即天然禁止再加
            blankDays.forEach { idx ->
                val day = dayRange[idx]
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onEmptyClick(day) }
                )
            }
        }
    ) { measurables, constraints ->
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val dayWidthPx = constraints.maxWidth.toFloat() / dayCount
        val placeables = measurables.mapIndexed { i, measurable ->
            val span = if (i < filled.size) {
                filled[i].endIndex - filled[i].startIndex + 1
            } else 1
            measurable.measure(Constraints.fixed((dayWidthPx * span).roundToInt(), h))
        }
        layout(constraints.maxWidth, h) {
            placeables.forEachIndexed { i, placeable ->
                val startIndex = if (i < filled.size) filled[i].startIndex else blankDays[i - filled.size]
                placeable.place((dayWidthPx * startIndex).roundToInt(), 0)
            }
        }
    }
}

/** 横带里一段连续列：item 为 null 表示这段列还没排安排 */
internal data class SpecialBandSegment(
    val item: SpecialItem?,
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
    items: List<SpecialItem>,
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
