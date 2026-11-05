package com.haooz.chedule.ui.activities.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.ui.activities.classschedule.MIN_CARD_HEIGHT_DP
import com.haooz.chedule.ui.activities.classschedule.MinuteHeight
import com.haooz.chedule.ui.activities.classschedule.TimelineAxis
import com.haooz.chedule.ui.activities.classschedule.TimelineDay
import com.haooz.chedule.ui.activities.classschedule.TimelineSlot
import com.haooz.chedule.ui.activities.classschedule.TimelineWeek
import com.haooz.chedule.ui.activities.classschedule.buildTimelineWeek
import com.haooz.chedule.ui.activities.classschedule.millisUntilNextMinute
import com.haooz.chedule.ui.activities.classschedule.minuteText
import com.haooz.chedule.ui.activities.classschedule.nowMinuteOfDay
import com.haooz.chedule.ui.activities.classschedule.parseHmMinutes
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.theme.rememberCourseColors
import com.kyant.capsule.ContinuousCapsule
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 今日页时间轴的刻度列宽：与课表页手机端一致，两页观感对齐。 */
private val TodayTimelineAxisWidth = 36.dp

/** 卡片左侧课程色条的宽度（胶囊形 4dp 才看得出圆头）。 */
private val AccentBarWidth = 4.dp

/**
 * 今日页时间轴的卡片外观。
 *
 * 不复用课表页的 `CourseCard`：今日页是「大圆角整块 + 左对齐三行」，课表页是「小格子居中课名」。
 */
internal data class TodayTimelineStyle(
    val isDark: Boolean,
    val surfaceOpacity: Float,
    val cornerRadius: Float = 16f,
    val showClassroom: Boolean = true,
    val showTeacher: Boolean = true,
)

/**
 * 今日页的时间轴：单列（当天），左侧整点刻度，卡片按真实时长落位。
 *
 * 骨架与课表页共用 [TimelineAxis]，卡片是本页自己的样式（见 `TodayTimelineCard`）。
 */
@Composable
internal fun TodayTimelineSection(
    courses: List<Course>,
    sectionTimes: Map<Int, String>,
    pageWeek: Int,
    isToday: Boolean,
    style: TodayTimelineStyle,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 单列的 day key 用 0 占位：buildTimelineWeek 只把 day 当列标识
    val layout = remember(courses, sectionTimes, pageWeek) {
        buildTimelineWeek(
            days = listOf(TimelineDay(day = 0, displayWeek = pageWeek, courses = courses)),
            sectionTimes = sectionTimes,
        )
    }
    val slots = layout.byDay[0].orEmpty()
    val gridHeight = MinuteHeight * layout.windowMinutes.toFloat()

    var nowMinute by remember { mutableIntStateOf(nowMinuteOfDay()) }
    if (isToday) {
        LaunchedEffect(Unit) {
            while (true) {
                nowMinute = nowMinuteOfDay()
                delay(millisUntilNextMinute().milliseconds)
            }
        }
    }

    TimelineAxis(
        layout = layout,
        gridHeight = gridHeight,
        sectionColumnWidth = TodayTimelineAxisWidth,
        isShowNowLine = isToday,
        nowMinute = nowMinute,
        isDark = style.isDark,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            TodayTimelineCards(
                slots = slots,
                layout = layout,
                style = style,
                onCourseClick = onCourseClick,
            )
        }
    }
}

/** 当天的 slot 铺成今日页卡片（单列，不并排：同槽位课已合并成一张代表卡）。 */
@Composable
private fun TodayTimelineCards(
    slots: List<TimelineSlot>,
    layout: TimelineWeek,
    style: TodayTimelineStyle,
    onCourseClick: (Course) -> Unit,
) {
    slots.forEach { slot ->
        val top = MinuteHeight * ((slot.startMinute - layout.windowStart).toFloat())
        val heightDp = (MinuteHeight * ((slot.endMinute - slot.startMinute).toFloat()))
            .value.coerceAtLeast(MIN_CARD_HEIGHT_DP)
        TodayTimelineCard(
            slot = slot,
            style = style,
            onClick = { onCourseClick(slot.course) },
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = top)
                .height(heightDp.dp)
                .padding(2.dp),
        )
    }
}

/**
 * 今日页时间轴上的课程块。
 *
 * 版式：左色条 + 左对齐三行（课程名 → `08:00 - 09:40` → `教室 · 教师`）。
 * 高度不够时逐行舍掉下面的信息，不把文字压成一团。
 */
@Composable
private fun TodayTimelineCard(
    slot: TimelineSlot,
    style: TodayTimelineStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val course = slot.course
    val colors = rememberCourseColors(course, style.isDark)
    val heightDp = slot.let { (it.endMinute - it.startMinute) * MinuteHeight.value }

    val timeText = slotTimeText(course, slot)
    val infoText = buildString {
        if (style.showClassroom && course.classroom.isNotBlank()) append(course.classroom)
        if (style.showTeacher && course.teacher.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append(course.teacher)
        }
    }

    BlurCard(
        cornerRadius = style.cornerRadius.dp,
        surfaceOpacity = style.surfaceOpacity,
        modifier = modifier,
    ) {
        // 点击放在卡片内容层：与今日列表版课程行同一套（默认 indication，即卡面上的按压反馈），
        // 且必须落在 BlurCard 的圆角裁剪之内 —— 挂在外层 modifier 上，涟漪会溢出四个圆角。
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
        ) {
            // 左侧课程色条：两端圆的胶囊，上下收内距，短课自然缩成一小段而不是贯通到底
            Box(
                modifier = Modifier
                    .padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
                    .width(AccentBarWidth)
                    .fillMaxHeight()
                    .clip(ContinuousCapsule())
                    .background(colors.tint)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 12.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = course.name,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = if (heightDp >= 72f) 3 else if (heightDp >= 44f) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (heightDp >= 44f && timeText.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = timeText,
                        fontSize = 12.5.sp,
                        lineHeight = 15.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (heightDp >= 68f && infoText.isNotEmpty()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = infoText,
                        fontSize = 12.5.sp,
                        lineHeight = 15.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** `08:00 - 09:40`；跨午夜的自定义课按原始结束时间标「次日」。 */
private fun slotTimeText(course: Course, slot: TimelineSlot): String {
    val start = minuteText(slot.startMinute)
    val end = if (course.crossesMidnightCustomTime()) {
        // 轴上截到 24:00，标签用真实结束时间
        "次日" + minuteText(parseHmMinutes(course.customEndTime) ?: slot.endMinute)
    } else {
        minuteText(slot.endMinute)
    }
    return "$start - $end"
}
