package com.haooz.chedule.ui.activities.classschedule

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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.haooz.chedule.data.appearance.CardContentAlignment
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
import com.haooz.chedule.ui.components.LocalGlassRedrawSignal
import com.haooz.chedule.ui.components.LocalPageMountStage
import com.haooz.chedule.ui.components.SoftGlassSurface
import com.haooz.chedule.ui.components.rememberRevealPresence
import com.haooz.chedule.ui.components.rememberScheduleRevealReplay
import com.haooz.chedule.ui.components.rememberScheduleRevealState
import com.haooz.chedule.ui.components.scheduleRevealLayer
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.theme.scheduleAccentColor
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 一门课在本列的一帧渲染数据；包在 remember 里避免每次重组重跑分组。 */
private data class CourseRenderData(
    val course: Course,
    val isCurrentWeekCourse: Boolean,
    val hasHiddenCourses: Boolean,
    val segments: List<Pair<Int, Int>>,
)

/** 待添加槽位的稳定容器：周页只持有不变对象，值变化只让本列重组。 */
@Stable
class PendingSlotState {
    var slot: Pair<Int, Int> by mutableStateOf(-1 to -1)
}

/** 拖拽中要隐藏的原卡集合；[update] 必须在组合期同步调用。 */
@Stable
class DraggingSlotsState {
    private var _slots by mutableStateOf(emptySet<Pair<String, Int>>())
    val slots: Set<Pair<String, Int>> get() = _slots

    fun update(next: Set<Pair<String, Int>>) {
        Snapshot.withoutReadObservation {
            if (_slots !== next) _slots = next
        }
    }
}

/** 课程卡在网格里的共享视觉参数，避免两层调用各传十几份。 */
private data class ColumnCardStyle(
    val isDark: Boolean,
    val cardAlpha: Float,
    val cardSurfaceAlpha: Float,
    val solidBackingColor: Color?,
    val cardHeightPerSection: Float,
    val cardCornerRadius: Float,
    val isTablet: Boolean,
    val cardContentAlignment: CardContentAlignment,
    val cardTextColor: CardTextColor,
    val cardTextScale: Float,
    val showClassroom: Boolean,
    val showTeacher: Boolean,
    val softGlassSurface: SoftGlassSurface?,
)

@Composable
fun DayColumn(
    dayOfWeek: Int,
    courses: List<Course>,
    onCourseClick: (Course) -> Unit,
    onEmptyClick: (Int) -> Unit,
    onEmptyLongPress: (section: Int, centerX: Float, cellTopY: Float, width: Float, height: Float) -> Unit = { _, _, _, _, _ -> },
    morningSections: Int = 4,
    afternoonSections: Int = 4,
    eveningSections: Int = 3,
    sectionTimes: Map<Int, String> = Course.defaultSectionTimes,
    specialBlocks: List<SpecialBlock> = emptyList(),
    grid: SpecialGridLayout,
    currentWeek: Int = 1,
    isHoliday: Boolean = false,
    holidayExemptCourseIds: Set<String> = emptySet(),
    holidayCancelledCourseIds: Set<String> = emptySet(),
    isWorkSwap: Boolean = false,
    pendingSlot: PendingSlotState? = null,
    onPendingChange: (day: Int, section: Int) -> Unit = { _, _ -> },
    softGlassSurface: SoftGlassSurface? = null,
    solidBackingColor: Color? = null,
    cardAlpha: Float = 0.12f,
    cardSurfaceAlpha: Float = 0.34f,
    cardHeightPerSection: Float = 54f,
    cardCornerRadius: Float = 10f,
    showBreakDividers: Boolean = true,
    isTablet: Boolean = false,
    cardContentAlignment: CardContentAlignment = CardContentAlignment.CENTER_CENTER,
    cardTextColor: CardTextColor = CardTextColor.COLORFUL,
    cardTextScale: Float = 1f,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    draggingSlotsState: DraggingSlotsState? = null,
    onCourseLongPress: (course: Course, cardLeft: Float, cardTop: Float, width: Float, height: Float, currentWeek: Int) -> Unit = { _, _, _, _, _, _ -> },
    touchState: ScheduleTouchState? = null,
    isDark: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val totalSections = morningSections + afternoonSections + eveningSections
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    // 只订阅本列的待添加节次，点空格子时其余列不重组
    val pendingSection by remember(dayOfWeek, pendingSlot) {
        derivedStateOf { pendingSlot?.slot?.takeIf { it.first == dayOfWeek }?.second ?: -1 }
    }
    val activeDraggingSlots = draggingSlotsState?.slots.orEmpty()

    val parsedSectionTimes = remember(sectionTimes, totalSections) {
        List(totalSections) { index ->
            val parts = sectionTimes[index + 1]?.split("-").orEmpty()
            if (parts.size == 2) {
                parseClockMinutes(parts[0]) to parseClockMinutes(parts[1])
            } else {
                -1 to -1
            }
        }
    }
    val occupiedSections = remember(courses, parsedSectionTimes, totalSections) {
        computeOccupiedSections(courses, parsedSectionTimes, totalSections)
    }

    val perSectionPx = with(density) { cardHeightPerSection.dp.toPx() }
    val sectionTopPx = remember(grid, density, totalSections) {
        FloatArray(totalSections + 1) { index ->
            if (index == 0) 0f else with(density) { (grid.sectionTop[index] ?: 0f).dp.toPx() }
        }
    }

    val emptyLayerBounds = remember { FloatArray(4) }
    val emptyLayerCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val refreshEmptyLayerBounds = {
        val coords = emptyLayerCoords[0]
        if (coords != null && coords.isAttached) {
            val position = coords.localToRoot(Offset.Zero)
            emptyLayerBounds[0] = position.x
            emptyLayerBounds[1] = position.y
            emptyLayerBounds[2] = coords.size.width.toFloat()
            emptyLayerBounds[3] = coords.size.height.toFloat()
        }
    }

    fun sectionAt(y: Float): Int {
        for (section in 1..totalSections) {
            val top = sectionTopPx[section]
            if (y >= top && y < top + perSectionPx) return section
        }
        return -1
    }

    Box(modifier = modifier.height(grid.totalHeight.toInt().dp)) {
        // 单节点承载所有空节次点击/长按；落点高亮由页面层遮罩负责
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    emptyLayerCoords[0] = coordinates
                    if (touchState?.scrolling != true) {
                        val position = coordinates.localToRoot(Offset.Zero)
                        emptyLayerBounds[0] = position.x
                        emptyLayerBounds[1] = position.y
                        emptyLayerBounds[2] = coordinates.size.width.toFloat()
                        emptyLayerBounds[3] = coordinates.size.height.toFloat()
                    }
                }
                .pointerInput(
                    dayOfWeek,
                    occupiedSections,
                    perSectionPx,
                    sectionTopPx,
                    density,
                ) {
                    detectTapGestures(
                        onTap = { offset ->
                            val section = sectionAt(offset.y)
                            if (section != -1 && section !in occupiedSections) {
                                onPendingChange(dayOfWeek, section)
                            }
                        },
                        onLongPress = { offset ->
                            val section = sectionAt(offset.y)
                            if (section != -1 && section !in occupiedSections) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                refreshEmptyLayerBounds()
                                onEmptyLongPress(
                                    section,
                                    emptyLayerBounds[0] + emptyLayerBounds[2] / 2f,
                                    emptyLayerBounds[1] + sectionTopPx[section],
                                    emptyLayerBounds[2],
                                    perSectionPx,
                                )
                            }
                        },
                    )
                },
        )

        // 加号卡直接显隐，点空格子要立刻响应
        if (
            pendingSection in 1..totalSections &&
            pendingSection !in occupiedSections
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeightPerSection.dp)
                    .offset(y = (grid.sectionTop[pendingSection] ?: 0f).dp)
            ) {
                PendingAddCard(
                    isDark = isDark,
                    cardCornerRadius = cardCornerRadius,
                    softGlassSurface = softGlassSurface,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onEmptyClick(pendingSection)
                }
            }
        }

        StagedCourseCards(threshold = (dayOfWeek - 1) / 2) {
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
                style = ColumnCardStyle(
                    isDark = isDark,
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
                    softGlassSurface = softGlassSurface,
                ),
                draggingCourseSlots = activeDraggingSlots,
                touchState = touchState,
                onCourseClick = onCourseClick,
                onCourseLongPress = onCourseLongPress,
                onPendingChange = onPendingChange,
            )
        }
    }
}

/**
 * 课卡层分批挂载：新页入窗时按列错开，躲开整页同帧组合的尖刺。
 *
 * 本页已在窗口内（[LocalPageMountStage] 为 null）时直接放出；读时钟的作用域只在本函数，
 * 展开后本列不再因时钟重组。
 */
@Composable
private fun StagedCourseCards(threshold: Int, content: @Composable () -> Unit) {
    val stage = LocalPageMountStage.current
    if (stage != null && stage.value < threshold) return
    content()
}

/** 普通课按节次范围占格；自定义时间课按时间区间反查覆盖的节次。 */
private fun computeOccupiedSections(
    courses: List<Course>,
    parsedSectionTimes: List<Pair<Int, Int>>,
    totalSections: Int,
): Set<Int> = buildSet {
    courses.forEach { course ->
        if (!course.hasValidCustomTime()) {
            for (section in course.startSection..course.endSection) add(section)
            return@forEach
        }
        val start = parseClockMinutes(course.customStartTime)
        val end = parseClockMinutes(course.customEndTime)
        if (start < 0 || end < 0 || start == end) return@forEach
        parsedSectionTimes.forEachIndexed { index, time ->
            val (sectionStart, sectionEnd) = time
            if (sectionStart >= 0 && sectionEnd >= 0 && start < sectionEnd && end > sectionStart) {
                add(index + 1)
            }
        }
        // 跨午夜（end < start）：00:00 之前的节次仍属于这门课
        if (end < start) {
            parsedSectionTimes.forEachIndexed { index, time ->
                if (time.first >= start) add(index + 1)
            }
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
    style: ColumnCardStyle,
    draggingCourseSlots: Set<Pair<String, Int>>,
    touchState: ScheduleTouchState?,
    onCourseClick: (Course) -> Unit,
    onCourseLongPress: (Course, Float, Float, Float, Float, Int) -> Unit,
    onPendingChange: (Int, Int) -> Unit,
) {
    val renderDataList = remember(
        courses,
        currentWeek,
        holidayCancelledCourseIds,
        showBreakDividers,
        morningSections,
        afternoonSections,
    ) {
        buildCourseRenderData(
            courses = courses,
            currentWeek = currentWeek,
            cancelledIds = holidayCancelledCourseIds,
            showBreakDividers = showBreakDividers,
            morningSections = morningSections,
            dinnerBreak = morningSections + afternoonSections,
        )
    }

    // 身份只按 course.id：无 key 时挪走一节课会让整列串位、文字动画误判换字。
    // 拖拽浮层已接管的卡静默移除，落地后不再显形。
    val draggedIds = remember { HashSet<String>() }
    val cardPresence = rememberRevealPresence(
        renderDataList,
        silentRemoval = { it.course.id in draggedIds },
    ) { it.course.id }
    if (draggingCourseSlots.isNotEmpty()) {
        draggedIds.clear()
        draggedIds.addAll(draggingCourseSlots.map { it.first })
    } else if (cardPresence.none { !it.visible }) {
        draggedIds.clear()
    }

    cardPresence.forEach { presence ->
        val renderData = presence.value
        val course = renderData.course
        key(presence.key) {
            val cardReveal = rememberScheduleRevealState(
                visible = presence.visible,
                animateEnter = presence.animateEnter,
            )
            val isCurrentWeekCourse = renderData.isCurrentWeekCourse
            // 调课落地只让新状态淡入；翻周换基线不播
            val stateReveal = rememberScheduleRevealReplay(
                epoch = currentWeek,
                state = isCurrentWeekCourse,
            )
            val revealModifier = when {
                cardReveal.isAnimating -> Modifier.scheduleRevealLayer(cardReveal)
                stateReveal.isAnimating -> Modifier.scheduleRevealLayer(stateReveal)
                else -> Modifier
            }
            val isDragging = (course.id to currentWeek) in draggingCourseSlots && isCurrentWeekCourse
            val cardHoliday = isHoliday && course.id !in holidayExemptCourseIds
            val isCancelled = course.id in holidayCancelledCourseIds
            val openDetail = {
                onPendingChange(-1, -1)
                onCourseClick(course)
            }
            val longPress = { left: Float, top: Float, width: Float, height: Float ->
                if (isCurrentWeekCourse) {
                    onCourseLongPress(course, left, top, width, height, currentWeek)
                }
            }

            if (course.hasValidCustomTime()) {
                val layout = computeCustomTimeLayout(
                    customStart = course.customStartTime,
                    customEnd = course.customEndTime,
                    morningSections = morningSections,
                    afternoonSections = afternoonSections,
                    eveningSections = eveningSections,
                    cardHeightPerSection = style.cardHeightPerSection,
                    sectionTimes = sectionTimes,
                    grid = grid,
                )
                if (layout != null) {
                    CourseSlotCard(
                        course = course,
                        style = style,
                        topDp = layout.topDp,
                        // 自定义时间课按时间轴定位，没有「挪到第几节」的落点，不支持拖动调课
                        customCardHeightDp = layout.heightDp,
                        dragEnabled = false,
                        hasMultipleCourses = renderData.hasHiddenCourses,
                        isCurrentWeekCourse = isCurrentWeekCourse,
                        isDragging = isDragging,
                        isHoliday = cardHoliday,
                        isCourseCancelled = isCancelled,
                        isWorkSwap = isWorkSwap,
                        touchState = touchState,
                        revealModifier = revealModifier,
                        onClick = openDetail,
                        onLongPressStart = longPress,
                    )
                }
            } else {
                renderData.segments.forEachIndexed { index, (segmentStart, segmentEnd) ->
                    key(segmentStart, segmentEnd) {
                        // 分段副本要带 course 本身，改颜色/名称后才会刷新
                        val displayCourse = remember(course, segmentStart, segmentEnd) {
                            course.copy(startSection = segmentStart, endSection = segmentEnd)
                        }
                        CourseSlotCard(
                            course = displayCourse,
                            style = style,
                            topDp = grid.sectionTop[segmentStart] ?: 0f,
                            customCardHeightDp = null,
                            dragEnabled = true,
                            hasMultipleCourses = index == 0 && renderData.hasHiddenCourses,
                            isCurrentWeekCourse = isCurrentWeekCourse,
                            isDragging = isDragging,
                            isHoliday = cardHoliday,
                            isCourseCancelled = isCancelled,
                            isWorkSwap = isWorkSwap,
                            touchState = touchState,
                            revealModifier = revealModifier,
                            onClick = openDetail,
                            onLongPressStart = longPress,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseSlotCard(
    course: Course,
    style: ColumnCardStyle,
    topDp: Float,
    customCardHeightDp: Float?,
    dragEnabled: Boolean,
    hasMultipleCourses: Boolean,
    isCurrentWeekCourse: Boolean,
    isDragging: Boolean,
    isHoliday: Boolean,
    isCourseCancelled: Boolean,
    isWorkSwap: Boolean,
    touchState: ScheduleTouchState?,
    revealModifier: Modifier,
    onClick: () -> Unit,
    onLongPressStart: (Float, Float, Float, Float) -> Unit,
) {
    CourseCard(
        modifier = Modifier.offset(y = topDp.dp).then(revealModifier),
        course = course,
        touchState = touchState,
        isDark = style.isDark,
        isCurrentWeek = isCurrentWeekCourse,
        isHoliday = isHoliday,
        isCourseCancelled = isCourseCancelled,
        isWorkSwap = isWorkSwap,
        hasMultipleCourses = hasMultipleCourses,
        softGlassSurface = style.softGlassSurface,
        cardAlpha = style.cardAlpha,
        cardSurfaceAlpha = style.cardSurfaceAlpha,
        solidBackingColor = style.solidBackingColor,
        cardHeightPerSection = style.cardHeightPerSection,
        customCardHeightDp = customCardHeightDp,
        cardCornerRadius = style.cardCornerRadius,
        isTablet = style.isTablet,
        cardContentAlignment = style.cardContentAlignment,
        cardTextColor = style.cardTextColor,
        cardTextScale = style.cardTextScale,
        showClassroom = style.showClassroom,
        showTeacher = style.showTeacher,
        isDragging = isDragging,
        dragEnabled = dragEnabled,
        onClick = onClick,
        onLongPressStart = onLongPressStart,
    )
}

/**
 * 同一格的课程归并成一张代表卡。
 *
 * 本周无课时按「谁下一次更早会上」挑一条；有课时假期排除优先，其余进小圆点。
 * 本周课排在渲染序列末尾，避免被非本周的占位卡压住。
 */
private fun buildCourseRenderData(
    courses: List<Course>,
    currentWeek: Int,
    cancelledIds: Set<String>,
    showBreakDividers: Boolean,
    morningSections: Int,
    dinnerBreak: Int,
): List<CourseRenderData> {
    val displayed = mutableListOf<Course>()
    val hiddenKeys = HashSet<String>()

    courses.groupBy { courseSlotKey(it) }.forEach { (slotKey, slotCourses) ->
        val (currentWeekCourses, otherCourses) = slotCourses.partition { it.isActiveInWeek(currentWeek) }
        if (currentWeekCourses.isNotEmpty()) {
            val selection = HolidayCourseExclusion.selectDisplayCourses(currentWeekCourses, cancelledIds)
            displayed.add(requireNotNull(selection.representative))
            if (selection.hidden.isNotEmpty() || otherCourses.isNotEmpty()) hiddenKeys.add(slotKey)
        } else {
            val representative = pickNonCurrentWeekCourse(otherCourses, currentWeek) ?: return@forEach
            displayed.add(representative)
            if (otherCourses.size > 1) hiddenKeys.add(slotKey)
        }
    }

    return displayed
        .map { course ->
            val segments = if (!showBreakDividers) {
                listOf(course.startSection to course.endSection)
            } else {
                buildList {
                    var start = course.startSection
                    while (start <= course.endSection) {
                        var end = course.endSection
                        if (morningSections in start until end) end = morningSections
                        if (dinnerBreak in start until end) end = dinnerBreak
                        add(start to end)
                        start = end + 1
                    }
                }
            }
            CourseRenderData(
                course = course,
                isCurrentWeekCourse = course.isActiveInWeek(currentWeek),
                hasHiddenCourses = courseSlotKey(course) in hiddenKeys,
                segments = segments,
            )
        }
        .sortedBy { if (it.isCurrentWeekCourse) 1 else 0 }
}

/** 加号卡：网格空槽与时间轴空白点击共用同一张，外观只在这里改。 */
@Composable
internal fun PendingAddCard(
    isDark: Boolean,
    cardCornerRadius: Float,
    softGlassSurface: SoftGlassSurface? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pendingColor =
        (if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.primary else Color(0xFF9E9E9E))
            .copy(alpha = if (isDark) 0.13f else 0.15f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (softGlassSurface != null) {
            val backdropShape = remember(cardCornerRadius) {
                ContinuousRoundedRectangle(cardCornerRadius.dp)
            }
            val origin = remember { arrayOf(Offset.Zero) }
            val glassModifier = rememberPanelGlassModifier(
                softGlassSurface = softGlassSurface,
                cornerRadius = cardCornerRadius,
                origin = { origin[0] },
            )
            val surfaceColor = if (MiuixTheme.isDynamicColor) {
                MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.64f else 0.5f)
            } else if (isDark) {
                Color(0xFF242424).copy(alpha = 0.64f)
            } else {
                Color(0xFFF0F0F0).copy(alpha = 0.5f)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .onGloballyPositioned { origin[0] = it.positionInWindow() }
                    .then(glassModifier ?: Modifier)
                    .background(surfaceColor, backdropShape)
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = cardCornerRadius.dp,
                    insideMargin = PaddingValues(0.dp),
                    showIndication = true,
                    colors = CardDefaults.defaultColors(
                        color = Color.Transparent,
                        contentColor = if (isDark) {
                            Color(0xFFF0F0F0).copy(alpha = 0.64f)
                        } else {
                            Color(0xFF242424).copy(alpha = 0.5f)
                        },
                    ),
                    onClick = onClick,
                ) {
                    AddIcon()
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
                    color = pendingColor,
                    contentColor = if (MiuixTheme.isDynamicColor) {
                        MiuixTheme.colorScheme.onSurfaceVariantActions
                    } else {
                        Color(0xFF9E9E9E).copy(alpha = 0.5f)
                    },
                ),
                onClick = onClick,
            ) {
                AddIcon()
            }
        }
    }
}

@Composable
private fun AddIcon() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = MiuixIcons.Add,
            contentDescription = CommonStr.add(),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 带柔光玻璃的面板 modifier；没有采样源或平台不支持时返回 null，调用方退回纯色。 */
@Composable
internal fun rememberPanelGlassModifier(
    softGlassSurface: SoftGlassSurface?,
    cornerRadius: Float,
    origin: () -> Offset,
): Modifier? {
    val density = LocalDensity.current
    val radiusPx = remember(cornerRadius, density) { with(density) { cornerRadius.dp.toPx() } }
    val redraw = LocalGlassRedrawSignal.current
    return remember(softGlassSurface, radiusPx, density, redraw) {
        softGlassSurface?.modifier(
            density = density,
            radiusPx = radiusPx,
            origin = origin,
            redraw = redraw,
        )
    }
}

/**
 * 槽位键：网格与时间轴共用的「同一格」判据。
 * 自定义时间课按起止时间区分，其余只按起始节次（8 节与 8-9 节是同一格）。
 */
internal fun courseSlotKey(course: Course): String {
    if (course.hasValidCustomTime()) {
        return "custom|${course.customStartTime}|${course.customEndTime}"
    }
    return "section|${course.startSection}"
}

private data class CustomTimeLayout(val topDp: Float, val heightDp: Float)

/** 自定义时间课按时间比例插值定位；跨午/晚休用统一分钟→Y，超范围钳到列顶/底。 */
private fun computeCustomTimeLayout(
    customStart: String?,
    customEnd: String?,
    morningSections: Int,
    afternoonSections: Int,
    eveningSections: Int,
    cardHeightPerSection: Float,
    sectionTimes: Map<Int, String>,
    grid: SpecialGridLayout,
): CustomTimeLayout? {
    val start = parseClockMinutes(customStart)
    val end = parseClockMinutes(customEnd)
    if (start < 0 || end < 0 || end == start) return null

    val totalSections = morningSections + afternoonSections + eveningSections
    val columnBottom = grid.totalHeight
    data class SectionInfo(val start: Int, val end: Int, val index: Int)

    val sections = buildList {
        for (section in 1..totalSections) {
            val parts = sectionTimes[section]?.split("-").orEmpty()
            if (parts.size != 2) continue
            val sectionStart = parseClockMinutes(parts[0])
            val sectionEnd = parseClockMinutes(parts[1])
            if (sectionStart >= 0 && sectionEnd >= 0) {
                add(SectionInfo(sectionStart, sectionEnd, section))
            }
        }
    }
    if (sections.isEmpty()) return null

    fun timeToY(minutes: Int): Float {
        for (info in sections) {
            if (minutes <= info.end) {
                val sectionTop = grid.sectionTop[info.index] ?: 0f
                val fraction = if (info.end > info.start) {
                    ((minutes - info.start).toFloat() / (info.end - info.start)).coerceIn(0f, 1f)
                } else {
                    0f
                }
                return sectionTop + cardHeightPerSection * fraction
            }
        }
        return columnBottom
    }

    fun timeToYClamped(minutes: Int): Float {
        if (minutes <= sections.first().start) return 0f
        return timeToY(minutes).coerceIn(0f, columnBottom)
    }

    // 跨午夜的课在当天没有「次日」格：从开始一直画到当天最后一节
    val top = timeToYClamped(start)
    val bottom = if (end < start) columnBottom else timeToYClamped(end)
    return CustomTimeLayout(top, (bottom - top).coerceAtLeast(0f))
}

/** 当前时刻是否落在 [startTime]~[endTime] 内；自带 30 秒节拍，只让横带重组。 */
@Composable
internal fun rememberIsOngoing(startTime: String, endTime: String): Boolean {
    var ongoing by remember(startTime, endTime) { mutableStateOf(false) }
    LaunchedEffect(startTime, endTime) {
        while (true) {
            val now = nowLocalTime()
            val nowMinutes = now.hour * 60 + now.minute
            val start = parseClockMinutes(startTime)
            val end = parseClockMinutes(endTime)
            ongoing = if (start >= 0 && end >= 0) {
                CourseTimeSpan.isActiveAt(start, end, nowMinutes)
            } else {
                false
            }
            delay(30_000L)
        }
    }
    return ongoing
}

@Composable
fun SpecialBandOverlay(
    name: String,
    startTime: String,
    endTime: String,
    hasBlur: Boolean,
    isDark: Boolean,
    cardCornerRadius: Float,
    cardAlpha: Float,
    cardSurfaceAlpha: Float = 0.34f,
    isTablet: Boolean = false,
    softGlassSurface: SoftGlassSurface? = null,
    // 子块为空时退化为整条显示名称
    items: List<SpecialItem> = emptyList(),
    // 每个可见列实际显示的星期（长度 = 列数），决定子块定位与列宽
    dayRange: List<Int> = emptyList(),
) {
    val shownName = name.ifBlank { SchedStr.specialCourses() }
    val alphaFactor = cardAlpha / 0.12f
    val cornerRadius = if (isTablet) cardCornerRadius * 1.3f else cardCornerRadius
    val isOngoing = rememberIsOngoing(startTime, endTime)
    val backgroundColor = when {
        isOngoing -> scheduleAccentColor().copy(alpha = (0.16f * alphaFactor).coerceIn(0f, 1f))
        isDark -> Color.White.copy(alpha = (0.06f * alphaFactor).coerceIn(0f, 1f))
        else -> Color.Black.copy(alpha = (0.04f * alphaFactor).coerceIn(0f, 1f))
    }

    if (hasBlur) {
        val backdropShape = remember(cornerRadius) { ContinuousRoundedRectangle(cornerRadius.dp) }
        val bandAlpha = cardSurfaceAlpha.coerceIn(0f, 1f)
        val overlayColor = remember(isDark, bandAlpha) {
            if (isDark) Color.Black.copy(alpha = bandAlpha) else Color.White.copy(alpha = bandAlpha)
        }
        val surfaceColor = remember(backgroundColor, overlayColor) {
            flattenStackedFills(backgroundColor, overlayColor)
        }
        val origin = remember { arrayOf(Offset.Zero) }
        val glassModifier = rememberPanelGlassModifier(
            softGlassSurface = softGlassSurface,
            cornerRadius = cornerRadius,
            origin = { origin[0] },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .onGloballyPositioned { origin[0] = it.positionInWindow() }
                .then(
                    if (glassModifier != null) {
                        Modifier.then(glassModifier).background(surfaceColor, backdropShape)
                    } else {
                        // 无壁纸 / 非 Android：直接铺表面色
                        Modifier.background(surfaceColor, backdropShape)
                    }
                )
        ) {
            SpecialBandBody(
                name = shownName,
                items = items,
                dayRange = dayRange,
                isDark = isDark,
                alphaFactor = alphaFactor,
                cornerRadius = cornerRadius,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .drawBehind {
                    drawRoundRect(
                        color = backgroundColor,
                        cornerRadius = CornerRadius(cornerRadius.dp.toPx()),
                    )
                }
        ) {
            SpecialBandBody(
                name = shownName,
                items = items,
                dayRange = dayRange,
                isDark = isDark,
                alphaFactor = alphaFactor,
                cornerRadius = cornerRadius,
            )
        }
    }
}

@Composable
private fun SpecialBandBody(
    name: String,
    items: List<SpecialItem>,
    dayRange: List<Int>,
    isDark: Boolean,
    alphaFactor: Float,
    cornerRadius: Float,
) {
    if (items.isEmpty() || dayRange.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = name,
                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                color = if (isDark) Color.White.copy(alpha = 0.74f) else Color.Black.copy(alpha = 0.74f),
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    // 只画有安排的段；未排安排的星期留空，阅读为「还没排」而不是「条带断了」
    val drawn = remember(items, dayRange) {
        buildSpecialBandSegments(items, dayRange).filter { it.item != null }
    }
    val dayCount = dayRange.size
    val itemShape = remember(cornerRadius) { ContinuousRoundedRectangle((cornerRadius * 0.8f).dp) }
    val itemBackground = if (isDark) {
        Color.White.copy(alpha = (0.06f * alphaFactor).coerceIn(0f, 1f))
    } else {
        Color.Black.copy(alpha = (0.06f * alphaFactor).coerceIn(0f, 1f))
    }
    val itemTextColor = if (isDark) Color.White.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.74f)

    // 自绘 Layout 取代 BoxWithConstraints：只需按列宽比例摆位
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            drawn.forEach { segment ->
                val item = segment.item ?: return@forEach
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        // 首/尾卡外侧 +2，与内侧相邻间距 4dp 均衡
                        .padding(
                            start = if (segment.startIndex == 0) 4.dp else 2.dp,
                            end = if (segment.endIndex == dayCount - 1) 4.dp else 2.dp,
                            top = 4.dp,
                            bottom = 4.dp,
                        )
                        .clip(itemShape)
                        .background(itemBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.name.ifBlank { SchedStr.unnamed() },
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                        color = itemTextColor,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val dayWidth = constraints.maxWidth.toFloat() / dayCount
        val placeables = measurables.mapIndexed { index, measurable ->
            val span = drawn[index].endIndex - drawn[index].startIndex + 1
            measurable.measure(Constraints.fixed((dayWidth * span).roundToInt(), height))
        }
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place((dayWidth * drawn[index].startIndex).roundToInt(), 0)
            }
        }
    }
}

/** 必须叠在课表行之上：空节次层会消费整列点击，下层横带收不到。 */
@Composable
fun SpecialBandClickLayer(
    items: List<SpecialItem>,
    dayRange: List<Int>,
    onItemClick: (SpecialItem) -> Unit,
    onEmptyClick: (Int) -> Unit,
) {
    if (dayRange.isEmpty()) return

    val segments = remember(items, dayRange) { buildSpecialBandSegments(items, dayRange) }
    val filled = remember(segments) { segments.filter { it.item != null } }
    val blankDays = remember(segments) {
        buildList {
            segments.forEach { segment ->
                if (segment.item != null) return@forEach
                for (index in segment.startIndex..segment.endIndex) add(index)
            }
        }
    }
    val dayCount = dayRange.size
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            filled.forEach { segment ->
                val item = segment.item ?: return@forEach
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onItemClick(item) }
                )
            }
            blankDays.forEach { index ->
                val day = dayRange[index]
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onEmptyClick(day) }
                )
            }
        },
    ) { measurables, constraints ->
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val dayWidth = constraints.maxWidth.toFloat() / dayCount
        val placeables = measurables.mapIndexed { index, measurable ->
            val span = if (index < filled.size) {
                filled[index].endIndex - filled[index].startIndex + 1
            } else {
                1
            }
            measurable.measure(Constraints.fixed((dayWidth * span).roundToInt(), height))
        }
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                val startIndex = if (index < filled.size) {
                    filled[index].startIndex
                } else {
                    blankDays[index - filled.size]
                }
                placeable.place((dayWidth * startIndex).roundToInt(), 0)
            }
        }
    }
}

/** 横带里一段连续列；item 为 null 表示这段列还没排安排。 */
internal data class SpecialBandSegment(
    val item: SpecialItem?,
    val startIndex: Int,
    val endIndex: Int,
)

/**
 * 把「列 → 该列实际显示的星期」解析成横带内的连续区段。
 *
 * [columnDays] 是列位置对应的**显示星期**，不是列下标：调休日的列画的是被调星期的课，
 * 子块必须跟着显示星期走。相邻同 id 的列合成一段，不相邻的各自成段。
 */
internal fun buildSpecialBandSegments(
    items: List<SpecialItem>,
    columnDays: List<Int>,
): List<SpecialBandSegment> {
    if (columnDays.isEmpty()) return emptyList()
    val segments = mutableListOf<SpecialBandSegment>()
    columnDays.forEachIndexed { index, day ->
        val item = items.firstOrNull { day in it.startDay..it.endDay }
        val last = segments.lastOrNull()
        if (last != null && last.item?.id == item?.id && last.endIndex == index - 1) {
            segments[segments.lastIndex] = last.copy(endIndex = index)
        } else {
            segments.add(SpecialBandSegment(item, index, index))
        }
    }
    return segments
}
