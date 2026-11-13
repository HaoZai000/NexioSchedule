package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.appearance.CardContentAlignment
import com.haooz.chedule.data.appearance.CardTextColor
import com.haooz.chedule.data.holiday.HolidayCourseExclusion
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.pickNonCurrentWeekCourse
import com.haooz.chedule.data.util.nowLocalTime
import com.haooz.chedule.ui.components.ScheduleReveal
import com.haooz.chedule.ui.components.SoftGlassSurface
import com.haooz.chedule.ui.components.rememberRevealPresence
import com.haooz.chedule.ui.components.rememberScheduleRevealReplay
import com.haooz.chedule.ui.components.rememberScheduleRevealState
import com.haooz.chedule.ui.components.scheduleRevealLayer
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.theme.scheduleAccentColor
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 课表页的呈现方式。
 *
 * [WEEK] 是「节次 × 星期」网格；[TIMELINE] 保持同样的星期分列与卡片，
 * 但纵轴换成真实时间，卡片位置与高度按墙钟算。
 */
enum class ScheduleViewMode { WEEK, TIMELINE }

// ── 时间轴几何 ───────────────────────────────────────────────────────

/** 每分钟的纵向高度：1.2dp → 每小时 72dp，一节 45 分钟约 54dp（与默认卡高一致）。 */
internal val MinuteHeight = 1.2.dp

/** 卡片最小高度：极短的课也要可点。 */
internal const val MIN_CARD_HEIGHT_DP = 22f

// ── 数据模型 ─────────────────────────────────────────────────────────

/** 落在时间轴上的课程块（分钟为当天 00:00 起算）。 */
internal data class TimelineSlot(
    val course: Course,
    val startMinute: Int,
    val endMinute: Int,
    /** 同槽位还有别的课（卡片上画提示圆点） */
    val hasMultiple: Boolean = false,
)

/** 时间轴的一列输入：列位置、该列实际显示的教学周、该列课程与状态标记。 */
internal data class TimelineDay(
    /** 列位置（1..7；今日页单列固定 0） */
    val day: Int,
    /** 该列实际显示的教学周（调休列是映射后的周次） */
    val displayWeek: Int,
    /** 该列实际显示的星期：调休列画的是被调星期的课，加课也要落到它上面 */
    val displayDay: Int = day,
    val courses: List<Course>,
    val cancelledIds: Set<String> = emptySet(),
    val exemptIds: Set<String> = emptySet(),
    val isHoliday: Boolean = false,
    val isWorkSwap: Boolean = false,
)

/**
 * 空白处点出的「待添加」槽位。
 *
 * [centerMinute] 是点下的那一分钟，加号卡片的**上下中心**就落在这里；卡片高度取一节课的时长
 * [durationMinutes]。点卡片即按卡片的起止新建一条自定义时间课程。
 */
internal data class TimelinePendingAdd(
    val day: Int,
    val centerMinute: Int,
    val durationMinutes: Int,
) {
    /** 卡片实际起点：以点击处为中心，必要时钳进窗口，避免卡片探出上下边界 */
    fun startMinuteIn(windowStart: Int, windowEnd: Int): Int {
        val maxStart = (windowEnd - durationMinutes).coerceAtLeast(windowStart)
        return (centerMinute - durationMinutes / 2).coerceIn(windowStart, maxStart)
    }
}

/**
 * 整周共用一套刻度：七列各算各的会错行。
 *
 * 窗口从第一节课所在整点起、到最后一节课结束所在整点止，不额外留白。
 */
internal data class TimelineWeek(
    val windowStart: Int,
    val windowEnd: Int,
    val byDay: Map<Int, List<TimelineSlot>>,
) {
    val windowMinutes: Int get() = windowEnd - windowStart
}

/** 课程在墙钟上的区间（分钟，当天 00:00 起算）。解析失败返回 null，该课不上轴。 */
internal fun courseMinuteRange(course: Course, sectionTimes: Map<Int, String>): Pair<Int, Int>? {
    if (course.hasValidCustomTime()) {
        val s = parseHmMinutes(course.customStartTime) ?: return null
        val e = parseHmMinutes(course.customEndTime) ?: return null
        // 跨午夜截到当天 24:00（轴上没有次日刻度）
        return s to if (e <= s) 24 * 60 else e
    }
    val s = parseHmMinutes(sectionTimes[course.startSection]?.substringBefore('-')?.trim())
        ?: return null
    val e = parseHmMinutes(sectionTimes[course.endSection]?.substringAfter('-')?.trim())
        ?: return null
    if (e <= s) return null
    return s to e
}

internal fun parseHmMinutes(hm: String?): Int? {
    if (hm.isNullOrBlank()) return null
    val parts = hm.split(':')
    if (parts.size != 2) return null
    val h = parts[0].trim().toIntOrNull() ?: return null
    val m = parts[1].trim().toIntOrNull() ?: return null
    if (h !in 0..24 || m !in 0..59) return null
    return (h * 60 + m).coerceIn(0, 24 * 60)
}

/** [minute] 落在哪一节（落在节间空隙取它前面那节）；作息为空时返回 1。 */
internal fun sectionAtMinute(sectionTimes: Map<Int, String>, minute: Int): Int {
    var best = 0
    var bestStart = -1
    var first = 0
    var firstStart = Int.MAX_VALUE
    sectionTimes.forEach { (section, text) ->
        val start = parseHmMinutes(text.substringBefore('-').trim()) ?: return@forEach
        if (first == 0 || start < firstStart) {
            first = section
            firstStart = start
        }
        if (start <= minute && start >= bestStart) {
            best = section
            bestStart = start
        }
    }
    if (best != 0) return best
    return if (first != 0) first else 1
}

/** 一节课的分钟数（作息里该节的时长）；解析不出来按 45 分钟。 */
internal fun sectionMinutesOf(sectionTimes: Map<Int, String>, section: Int): Int {
    val text = sectionTimes[section] ?: return 45
    val start = parseHmMinutes(text.substringBefore('-').trim()) ?: return 45
    val end = parseHmMinutes(text.substringAfter('-').trim()) ?: return 45
    return (end - start).takeIf { it in 5..300 } ?: 45
}

internal fun minuteText(minute: Int): String {
    val clamped = minute.coerceIn(0, 24 * 60)
    val h = clamped / 60
    val m = clamped % 60
    return "${if (h < 10) "0$h" else "$h"}:${if (m < 10) "0$m" else "$m"}"
}

/**
 * 同槽位该显示哪一条，口径与网格 `DayColumn.CourseCardsLayer` 一致：
 * 1. 本周有效的优先，其中被调休取消的排后面；
 * 2. 本周没课才看非本周，规则见 [pickNonCurrentWeekCourse]。
 */
private fun pickRepresentative(
    group: List<Course>,
    week: Int,
    cancelledIds: Set<String>,
): Course {
    val current = group.filter { it.isActiveInWeek(week) }
    if (current.isNotEmpty()) {
        return HolidayCourseExclusion.selectDisplayCourses(current, cancelledIds).representative
            ?: current.first()
    }
    return pickNonCurrentWeekCourse(group, week) ?: group.first()
}

/**
 * 把一天的课程铺到时间轴上。
 *
 * 与网格同一套出牌逻辑：按 [courseSlotKey] 归并成一张代表卡（同格只显一张，其余进小圆点），
 * 叠在同一时段的两张卡**整列宽上下叠压**，不拆左右分栏 —— 网格里自定义时间卡就是按时间插值
 * 压在节次卡上的（叠整齐时看着就是一张），时间轴拆成两列会与网格对不上。
 */
internal fun buildTimelineWeek(days: List<TimelineDay>, sectionTimes: Map<Int, String>): TimelineWeek {
    var earliest = Int.MAX_VALUE
    var latest = Int.MIN_VALUE
    val byDay = HashMap<Int, List<TimelineSlot>>(days.size)

    days.forEach { day ->
        val representatives = day.courses
            .groupBy { courseSlotKey(it) }
            .map { (_, group) ->
                pickRepresentative(group, day.displayWeek, day.cancelledIds) to (group.size > 1)
            }
            .mapNotNull { (course, multiple) ->
                val range = courseMinuteRange(course, sectionTimes) ?: return@mapNotNull null
                Triple(course, range, multiple)
            }
            // 与网格同序：非本周先画、本周后画（后者压在上面），组内保持输入顺序。
            // 叠压时谁在上层必须和网格一致，否则两视图露出来的不是同一张
            .sortedBy { if (it.first.isActiveInWeek(day.displayWeek)) 1 else 0 }

        if (representatives.isNotEmpty()) {
            earliest = minOf(earliest, representatives.minOf { it.second.first })
            latest = maxOf(latest, representatives.maxOf { it.second.second })
        }

        byDay[day.day] = representatives.map { triple ->
            TimelineSlot(
                course = triple.first,
                startMinute = triple.second.first,
                endMinute = triple.second.second,
                hasMultiple = triple.third,
            )
        }
    }

    // 整周没课也要有刻度，否则页面一片空白
    if (earliest == Int.MAX_VALUE) return TimelineWeek(8 * 60, 17 * 60, byDay)

    val start = (earliest / 60) * 60
    val end = (((latest + 59) / 60) * 60).coerceAtLeast(start + 60)
    return TimelineWeek(start, end.coerceAtMost(24 * 60), byDay)
}

// ── 外观参数 ─────────────────────────────────────────────────────────

/** 时间轴卡片沿用网格卡片的外观量（同一张 `CourseCard`，参数一一对应）。 */
internal data class TimelineCardStyle(
    val isDark: Boolean,
    /** 柔光玻璃采样源（Android 才有实现；null = 纯色表面）。 */
    val softGlassSurface: SoftGlassSurface?,
    val cardAlpha: Float,
    val cardSurfaceAlpha: Float,
    val solidBacking: Color?,
    val cardHeightPerSection: Float,
    val cardCornerRadius: Float,
    val cardContentAlignment: CardContentAlignment,
    val cardTextColor: CardTextColor,
    val cardTextScale: Float,
    val showClassroom: Boolean,
    val showTeacher: Boolean,
)

// ── 页面 ─────────────────────────────────────────────────────────────

/**
 * 时间轴版周页：左侧时间刻度 + 右侧按天分列，卡片仍是网格那张 `CourseCard`。
 *
 * 与网格共用滚动容器（同一个 `ScrollState`）与 `SharedScrollBehavior`，
 * 顶栏折叠、回弹与翻周手感与网格一致。
 */
@Composable
internal fun TimelineWeekGrid(
    days: List<TimelineDay>,
    sectionTimes: Map<Int, String>,
    isTablet: Boolean,
    isCurrentWeekView: Boolean,
    style: TimelineCardStyle,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    scrollState: ScrollState,
    scrollBehavior: SharedScrollBehavior?,
    onCourseClick: (Course, Int) -> Unit,
    /** 长按课程（root 坐标的卡片中心与尺寸，见 `CourseCard.onLongPressStart`） */
    onCourseLongPress: (Course, Float, Float, Float, Float, Int) -> Unit = { _, _, _, _, _, _ -> },
    /** 点空白时段：[TimelineDay] + 落点的当天分钟 */
    onEmptyTap: (TimelineDay, Int) -> Unit = { _, _ -> },
    /** 长按起浮层时要隐藏的原卡（课程 id 到教学周），与网格同源 */
    hiddenSlotsState: DraggingSlotsState? = null,
    /** 加号卡片被点：[TimelineDay] + 卡片实际起始分钟 + 时长 */
    onPendingAddClick: (TimelineDay, Int, Int) -> Unit = { _, _, _ -> },
    pendingAdd: TimelinePendingAdd? = null,
    modifier: Modifier = Modifier,
    /** 页级玻璃节点：插在「横线之下、卡片之上」，否则半透明卡片会透出横线 */
    glassBehindCards: @Composable (Modifier) -> Unit = {},
) {
    val density = LocalDensity.current
    val sectionColumnWidth = scheduleSectionColumnWidth(isTablet)
    // 周页 lambda 只传容器，拖拽集合的变化在本 composable 内订阅
    val activeHiddenSlots = hiddenSlotsState?.slots.orEmpty()
    // 当前时间：与今日页共用宿主的分钟 tick，此处只拿稳定 State 不读 .intValue
    val appMinuteClock = rememberAppMinuteClock()

    // 键取数据本身而不是 lambda：调用方的 lambda 每次重组都是新实例
    val layout = remember(days, sectionTimes) { buildTimelineWeek(days, sectionTimes) }
    val gridHeight = MinuteHeight * layout.windowMinutes.toFloat()

    // 进入本页时把「现在」带到顶部下方约一小时处：网格的滚动值按节次算，直接沿用会落在无意义的位置。
    // 等一帧再滚：测量前 maxValue 还是 0，scrollTo 会被钳回顶部。
    LaunchedEffect(Unit) {
        if (!isCurrentWeekView) return@LaunchedEffect
        val minutesAboveNow = appMinuteClock.minute.intValue - layout.windowStart - 60
        if (minutesAboveNow <= 0) return@LaunchedEffect
        val targetPx = with(density) { (MinuteHeight * minutesAboveNow.toFloat()).roundToPx() }
        withFrameNanos { }
        scrollState.scrollTo(targetPx.coerceIn(0, scrollState.maxValue.coerceAtLeast(0)))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .overScrollVertical()
            .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else Modifier
            )
            .verticalScroll(scrollState)
            // 顶部避让顶栏、底部留白：与网格同一套算法（高度不进测量链）
            .layout { measurable, constraints ->
                val topPad = contentTopPadding.roundToPx().coerceAtLeast(0)
                val bottomPad = contentBottomPadding.roundToPx().coerceAtLeast(0)
                val placeable = measurable.measure(
                    constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                )
                layout(placeable.width, placeable.height + topPad + bottomPad) {
                    placeable.place(0, topPad)
                }
            }
            .then(
                if (isTablet) Modifier.padding(horizontal = 24.dp) else Modifier.padding(end = 2.dp)
            )
    ) {
        TimelineAxis(
            layout = layout,
            gridHeight = gridHeight,
            sectionColumnWidth = sectionColumnWidth,
            isShowNowLine = isCurrentWeekView,
            nowMinuteState = appMinuteClock.minute,
            isDark = style.isDark,
            glassBehindCards = glassBehindCards,
        ) {
            days.forEach { day ->
                TimelineDayLane(
                    slots = layout.byDay[day.day].orEmpty(),
                    windowStart = layout.windowStart,
                    windowEnd = layout.windowEnd,
                    gridHeight = gridHeight,
                    day = day,
                    style = style,
                    isTablet = isTablet,
                    onCourseClick = onCourseClick,
                    onCourseLongPress = onCourseLongPress,
                    onEmptyTap = onEmptyTap,
                    onPendingAddClick = onPendingAddClick,
                    isCourseHidden = { (it.id to day.displayWeek) in activeHiddenSlots },
                    pendingAdd = pendingAdd?.takeIf { it.day == day.day },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * 时间轴骨架：左侧整点刻度 + 主轴竖线 + 每小时一条极淡横线 + 当前时间线。
 *
 * @param columns 主轴右侧的列（课表页是七天列，今日页一列）
 */
@Composable
internal fun TimelineAxis(
    layout: TimelineWeek,
    gridHeight: Dp,
    sectionColumnWidth: Dp,
    isShowNowLine: Boolean,
    /** 稳定 State：每分钟 tick 只重组当前时间线那个小 content */
    nowMinuteState: MutableIntState,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    glassBehindCards: @Composable (Modifier) -> Unit = {},
    columns: @Composable RowScope.() -> Unit,
) {
    val accent = scheduleAccentColor()
    val density = LocalDensity.current
    // 每分钟的 intValue 由派生状态吃掉，只重组当前时间线那个小 content
    val showNowLine by remember(
        isShowNowLine, layout.windowStart, layout.windowEnd, nowMinuteState
    ) {
        derivedStateOf {
            isShowNowLine && nowMinuteState.intValue in layout.windowStart..layout.windowEnd
        }
    }
    // 横线口径与周视图网格线一致（深色比亮色略白一档）。
    // 注意：周视图的网格线已全部统一为这一档；时间轴视图时间列右缘仍保留略深的
    // rail 作为唯一主轴（这个视图没有周视图那样的竖向日分界线），不要反向同步过去。
    val hourLineColor = MiuixTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.09f else 0.05f)
    val railColor = MiuixTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.16f else 0.12f)
    val tickColor = MiuixTheme.colorScheme.onSurfaceVariantActions
    val sectionWidthPx = with(density) { sectionColumnWidth.toPx() }
    val gapPx = with(density) { 2.dp.toPx() }
    val hairlinePx = with(density) { 1.dp.toPx() }.coerceAtLeast(1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(gridHeight)
            // 每小时一条极淡横线 + 时间列右缘的主轴，卡片压在它们之上
            .drawBehind {
                // 最上方封边：窗口起点那条 hourLine 落在 y=0，下面的循环会跳过，单独补一条
                drawLine(
                    color = hourLineColor,
                    start = Offset(sectionWidthPx + gapPx, hairlinePx / 2f),
                    end = Offset(size.width, hairlinePx / 2f),
                    strokeWidth = hairlinePx,
                )
                var hour = layout.windowStart / 60
                while (hour <= layout.windowEnd / 60) {
                    val y = (hour * 60 - layout.windowStart) * MinuteHeight.toPx()
                    if (y > hairlinePx) {
                        drawLine(
                            color = hourLineColor,
                            start = Offset(sectionWidthPx + gapPx, y),
                            end = Offset(size.width, y),
                            strokeWidth = hairlinePx,
                        )
                    }
                    hour++
                }
                drawLine(
                    color = railColor,
                    start = Offset(sectionWidthPx, 0f),
                    end = Offset(sectionWidthPx, size.height),
                    strokeWidth = hairlinePx,
                )
            }
    ) {
        // 玻璃在横线之后、卡片之前：卡片表面是半透明的，玻璃若画在整页最底层，横线会透出来
        glassBehindCards(Modifier.fillMaxWidth().height(gridHeight))
        Row(modifier = Modifier.fillMaxWidth().height(gridHeight)) {
            Box(modifier = Modifier.width(sectionColumnWidth).fillMaxHeight()) {
                var hour = layout.windowStart / 60
                while (hour <= layout.windowEnd / 60) {
                    val y = MinuteHeight * ((hour * 60 - layout.windowStart).toFloat())
                    if (y < gridHeight) {
                        Text(
                            text = minuteText(hour * 60),
                            fontSize = 9.5.sp,
                            color = tickColor,
                            maxLines = 1,
                            textAlign = TextAlign.End,
                            // 刻度文字排在横线下方而不是骑在线上，否则文字被线穿过
                            modifier = Modifier
                                .offset(y = y + 1.dp)
                                .fillMaxWidth()
                                .padding(end = 3.dp),
                        )
                    }
                    hour++
                }
            }
            columns()
        }

        // 当前时间线：气泡右缘贴主轴，横线从主轴起画
        ScheduleReveal(
            visible = showNowLine,
            modifier = Modifier.matchParentSize(),
            // 细线只淡入淡出，不模糊不位移
            blur = false,
            transform = false,
        ) {
            // .intValue 只在这个小作用域读，tick 不重组整条时间轴
            val minute = nowMinuteState.intValue
            val nowTop = MinuteHeight * ((minute - layout.windowStart).toFloat())
            val bubbleInset = 3.dp
            Box(
                modifier = Modifier
                    .offset(x = bubbleInset, y = nowTop - 8.dp)
                    .width(sectionColumnWidth - bubbleInset)
                    .height(16.dp)
                    .clip(ContinuousRoundedRectangle(8.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = minuteText(minute),
                    fontSize = 11.sp,
                    color = Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
            Box(
                modifier = Modifier
                    .offset(x = sectionColumnWidth, y = nowTop - 0.75.dp)
                    .padding(end = 2.dp)
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(accent.copy(alpha = 0.85f))
            )
        }
    }
}

/**
 * 一列时间轴：空白点击层 + 课程卡 + 待添加的加号卡片。
 */
@Composable
private fun TimelineDayLane(
    slots: List<TimelineSlot>,
    windowStart: Int,
    windowEnd: Int,
    gridHeight: Dp,
    day: TimelineDay,
    style: TimelineCardStyle,
    isTablet: Boolean,
    onCourseClick: (Course, Int) -> Unit,
    onCourseLongPress: (Course, Float, Float, Float, Float, Int) -> Unit,
    onEmptyTap: (TimelineDay, Int) -> Unit,
    onPendingAddClick: (TimelineDay, Int, Int) -> Unit,
    pendingAdd: TimelinePendingAdd?,
    isCourseHidden: (Course) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.height(gridHeight)) {
        // 空白点击层：卡片自己消费点击，这里只接落在空时段上的点（空列也要能加课）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(day.day, slots, windowStart, windowEnd) {
                    detectTapGestures { offset ->
                        val minute = windowStart + (offset.y / MinuteHeight.toPx()).toInt()
                        if (minute < windowStart || minute >= windowEnd) return@detectTapGestures
                        if (slots.any { minute >= it.startMinute && minute < it.endMinute }) {
                            return@detectTapGestures
                        }
                        onEmptyTap(day, minute)
                    }
                }
        )
        // 卡片层始终组合，slots 清空时由 presence 留住旧卡播完退场
        TimelineCardsLayer(
            slots = slots,
            windowStart = windowStart,
            displayWeek = day.displayWeek,
            style = style,
            isTablet = isTablet,
            isCourseActive = { it.isActiveInWeek(day.displayWeek) },
            isCourseHoliday = { day.isHoliday && it.id !in day.exemptIds },
            isCourseCancelled = { it.id in day.cancelledIds },
            isWorkSwap = day.isWorkSwap,
            isCourseHidden = isCourseHidden,
            onCourseClick = { course -> onCourseClick(course, day.displayWeek) },
            onCourseLongPress = { course, cx, cy, w, h ->
                onCourseLongPress(course, cx, cy, w, h, day.displayWeek)
            },
        )
        // 加号卡片：上下中心落在点下的那一分钟，高度按一节课的时长。直接显隐，不播动效
        pendingAdd?.let { add ->
            val startMinute = add.startMinuteIn(windowStart, windowEnd)
            val top = MinuteHeight * ((startMinute - windowStart).toFloat())
            val height = (MinuteHeight * add.durationMinutes.toFloat())
                .value.coerceAtLeast(MIN_CARD_HEIGHT_DP)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = top)
                    .height(height.dp)
            ) {
                PendingAddCard(
                    isDark = style.isDark,
                    cardCornerRadius = style.cardCornerRadius,
                    softGlassSurface = style.softGlassSurface,
                    modifier = Modifier.fillMaxSize(),
                    onClick = {
                        onPendingAddClick(day, startMinute, add.durationMinutes)
                    },
                )
            }
        }
    }
}

/**
 * 把一组 slot 铺成卡片：位置取分钟差、高度取时长，整列宽（与网格一致）。
 *
 * 叠压的卡按上面的顺序上下覆盖，不并排分栏；卡宽就是列宽，
 * 左右各 2dp 的缝由 `CourseCard` 自己留，外面再补会比网格窄。
 */
@Composable
internal fun TimelineCardsLayer(
    slots: List<TimelineSlot>,
    windowStart: Int,
    displayWeek: Int,
    style: TimelineCardStyle,
    isTablet: Boolean,
    isCourseActive: (Course) -> Boolean,
    isCourseHoliday: (Course) -> Boolean,
    isCourseCancelled: (Course) -> Boolean,
    isWorkSwap: Boolean,
    isCourseHidden: (Course) -> Boolean = { false },
    onCourseClick: (Course) -> Unit,
    onCourseLongPress: (Course, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
) {
    // 身份只按 course.id；状态翻转不算增删，交给 replay
    val cardPresence = rememberRevealPresence(slots) { it.course.id }
    cardPresence.forEach { presence ->
        val slot = presence.value
        val top = MinuteHeight * ((slot.startMinute - windowStart).toFloat())
        val heightDp = (MinuteHeight * ((slot.endMinute - slot.startMinute).toFloat()))
            .value.coerceAtLeast(MIN_CARD_HEIGHT_DP)
        key(presence.key) {
            val isActive = isCourseActive(slot.course)
            val cardReveal = rememberScheduleRevealState(
                visible = presence.visible,
                animateEnter = presence.animateEnter,
            )
            // 调课落地只让新状态淡入，翻周换基线不播
            val stateReveal = rememberScheduleRevealReplay(
                epoch = displayWeek,
                state = isActive,
            )
            val revealModifier = when {
                cardReveal.isAnimating -> Modifier.scheduleRevealLayer(cardReveal)
                stateReveal.isAnimating -> Modifier.scheduleRevealLayer(stateReveal)
                else -> Modifier
            }
            CourseCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = top)
                    .then(revealModifier),
                course = slot.course,
                // 原位隐形而非摘掉整卡，否则后续卡会串位
                isDragging = isCourseHidden(slot.course),
                // 按 customCardHeightDp 定高，与网格自定义时间课同一路径
                customCardHeightDp = heightDp,
                isDark = style.isDark,
                isCurrentWeek = isActive,
                isHoliday = isCourseHoliday(slot.course),
                isCourseCancelled = isCourseCancelled(slot.course),
                isWorkSwap = isWorkSwap,
                hasMultipleCourses = slot.hasMultiple,
                softGlassSurface = style.softGlassSurface,
                cardAlpha = style.cardAlpha,
                cardSurfaceAlpha = style.cardSurfaceAlpha,
                solidBackingColor = style.solidBacking,
                cardHeightPerSection = style.cardHeightPerSection,
                cardCornerRadius = style.cardCornerRadius,
                isTablet = isTablet,
                cardContentAlignment = style.cardContentAlignment,
                cardTextColor = style.cardTextColor,
                cardTextScale = style.cardTextScale,
                showClassroom = style.showClassroom,
                showTeacher = style.showTeacher,
                // 阅读视图不做拖拽
                dragEnabled = false,
                onClick = { onCourseClick(slot.course) },
                onLongPressStart = { cx, cy, w, h ->
                    onCourseLongPress(slot.course, cx, cy, w, h)
                },
            )
        }
    }
}

// ── 小工具 ───────────────────────────────────────────────────────────

/** 今日页与课表时间轴共用的分钟时钟，宿主只起一个 tick。 */
@Stable
class AppMinuteClock {
    val minute = mutableIntStateOf(nowMinuteOfDay())
}

/** 宿主（主 pager 外层）提供；没有宿主的预览 / 独立调用自动回退到本地时钟。 */
val LocalAppMinuteClock = compositionLocalOf<AppMinuteClock?> { null }

@Composable
internal fun rememberAppMinuteClock(): AppMinuteClock {
    val provided = LocalAppMinuteClock.current
    val fallback = remember { AppMinuteClock() }
    if (provided == null) {
        LaunchedEffect(Unit) {
            while (true) {
                fallback.minute.intValue = nowMinuteOfDay()
                delay(millisUntilNextMinute().milliseconds)
            }
        }
    }
    return provided ?: fallback
}

internal fun nowMinuteOfDay(): Int {
    val now = nowLocalTime()
    return now.hour * 60 + now.minute
}

/** 距下一个整分钟还有多少毫秒（当前时间气泡按分钟翻字用）。 */
internal fun millisUntilNextMinute(): Long {
    val now = nowLocalTime()
    return (60_000L - now.second * 1000L).coerceIn(200L, 60_000L)
}
