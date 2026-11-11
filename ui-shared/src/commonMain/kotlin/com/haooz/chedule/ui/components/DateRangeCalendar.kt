package com.haooz.chedule.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.util.lengthOfMonth
import com.haooz.chedule.i18n.HolidayStr
import com.haooz.chedule.ui.components.text.miuix.Text as Text
import com.haooz.softglass.shape.ContinuousCapsule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val CELL_HEIGHT = 40.dp
private val MARK_SIZE = 34.dp
private val CONTENT_PADDING = 12.dp
private val SECONDARY_MARK_COLOR = Color(0xFF34C759)

enum class DateCalendarMode {
    /** 点两下选一段 */
    Range,

    /** 点一下选一天 */
    Single,
}

/**
 * 通用日期日历。
 *
 * 区间模式：点第一下是起点（同时清掉上一次选择），点第二下完成区间；
 * 完成后再点任何日期，会以该日期为起点重新选择。
 * 单日模式：点一下直接返回该日期。
 */
@Composable
fun DateRangeCalendar(
    startDate: LocalDate,
    endDate: LocalDate,
    onRangeChange: (LocalDate, LocalDate) -> Unit,
    weekdayLabels: List<String>,
    monthTitle: (year: Int, month: Int) -> String,
    modifier: Modifier = Modifier,
    mode: DateCalendarMode = DateCalendarMode.Range,
    resetKey: Any? = Unit,
    yearRange: IntRange = 1970..2100,
) {
    val scheme = MiuixTheme.colorScheme
    var viewYear by remember(resetKey) { mutableIntStateOf(startDate.year) }
    var viewMonth by remember(resetKey) { mutableIntStateOf(startDate.monthNumber) }
    var pendingStart by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(resetKey) { pendingStart = null }

    fun shiftMonth(delta: Int) {
        var targetYear = viewYear
        var targetMonth = viewMonth + delta
        if (targetMonth < 1) {
            targetMonth = 12
            targetYear -= 1
        }
        if (targetMonth > 12) {
            targetMonth = 1
            targetYear += 1
        }
        if (targetYear in yearRange) {
            viewYear = targetYear
            viewMonth = targetMonth
        }
    }

    fun handleDayClick(date: LocalDate) {
        if (mode == DateCalendarMode.Single) {
            onRangeChange(date, date)
            return
        }
        val start = pendingStart
        if (start == null) {
            pendingStart = date
            onRangeChange(date, date)
        } else {
            onRangeChange(minOf(start, date), maxOf(start, date))
            pendingStart = null
        }
    }

    CalendarFrame(
        year = viewYear,
        month = viewMonth,
        monthTitle = monthTitle,
        onShiftMonth = { shiftMonth(it) },
        modifier = modifier,
    ) { cellYear, cellMonth ->
        MonthGrid(
            year = cellYear,
            month = cellMonth,
            weekdayLabels = weekdayLabels,
            styleOf = { date ->
                val isStart = date == startDate
                val isEnd = date == endDate
                val band = scheme.primary.copy(alpha = 0.12f)
                when {
                    isStart && isEnd -> DayStyle(fill = scheme.primary, content = scheme.onPrimary)
                    isStart -> DayStyle(
                        band = band,
                        bandHalf = BandHalf.Trailing,
                        fill = scheme.primary,
                        content = scheme.onPrimary,
                    )
                    isEnd -> DayStyle(
                        band = band,
                        bandHalf = BandHalf.Leading,
                        fill = scheme.primary,
                        content = scheme.onPrimary,
                    )
                    date > startDate && date < endDate -> DayStyle(
                        band = band,
                        bandHalf = BandHalf.Full,
                        content = scheme.primary,
                    )
                    else -> DayStyle(content = scheme.onSurface)
                }
            },
            onDayClick = { handleDayClick(it) },
        )
    }
}

/**
 * 双色双日期日历：一次选两个互不相关的日期。
 *
 * [primaryDate] 用主题主色（蓝），[secondaryDate] 用绿色；点下面的日期胶囊切换
 * 「下一次点击改哪一个」，两边都选好后自动切到还空着的那一侧。
 */
@Composable
fun DualDateCalendar(
    primaryDate: LocalDate,
    secondaryDate: LocalDate?,
    onPrimaryChange: (LocalDate) -> Unit,
    onSecondaryChange: (LocalDate) -> Unit,
    weekdayLabels: List<String>,
    monthTitle: (year: Int, month: Int) -> String,
    modifier: Modifier = Modifier,
    resetKey: Any? = Unit,
    yearRange: IntRange = 1970..2100,
    primaryColor: Color = MiuixTheme.colorScheme.primary,
    secondaryColor: Color = SECONDARY_MARK_COLOR,
    /** 首次进入时默认改哪一侧：true = 绿色（补课日），false = 蓝色（调休日） */
    startWithSecondary: Boolean = false,
) {
    val scheme = MiuixTheme.colorScheme
    var viewYear by remember(resetKey) { mutableIntStateOf(primaryDate.year) }
    var viewMonth by remember(resetKey) { mutableIntStateOf(primaryDate.monthNumber) }
    // true = 下一次点击改主日期（调休日），false = 改副日期（补课日）
    var editingPrimary by remember(resetKey) { mutableStateOf(!startWithSecondary) }

    fun shiftMonth(delta: Int) {
        var targetYear = viewYear
        var targetMonth = viewMonth + delta
        if (targetMonth < 1) {
            targetMonth = 12
            targetYear -= 1
        }
        if (targetMonth > 12) {
            targetMonth = 1
            targetYear += 1
        }
        if (targetYear in yearRange) {
            viewYear = targetYear
            viewMonth = targetMonth
        }
    }

    CalendarFrame(
        year = viewYear,
        month = viewMonth,
        monthTitle = monthTitle,
        onShiftMonth = { shiftMonth(it) },
        modifier = modifier,
        footer = {
            DualDateRoleRow(
                primaryDate = primaryDate,
                secondaryDate = secondaryDate,
                editingPrimary = editingPrimary,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                onSelectPrimary = { editingPrimary = true },
                onSelectSecondary = { editingPrimary = false },
            )
        },
    ) { cellYear, cellMonth ->
        MonthGrid(
            year = cellYear,
            month = cellMonth,
            weekdayLabels = weekdayLabels,
            styleOf = { date ->
                when (date) {
                    primaryDate -> DayStyle(fill = primaryColor, content = Color.White)
                    secondaryDate -> DayStyle(fill = secondaryColor, content = Color.White)
                    else -> DayStyle(content = scheme.onSurface)
                }
            },
            onDayClick = { date ->
                if (editingPrimary) {
                    onPrimaryChange(date)
                    // 另一侧还空着：顺手把编辑目标切过去，少点一次
                    if (secondaryDate == null) editingPrimary = false
                } else {
                    onSecondaryChange(date)
                }
            },
        )
    }
}

/** 双色日期的角色行：蓝 = 调休日，绿 = 补课日，点一下决定下次改哪个。 */
@Composable
private fun DualDateRoleRow(
    primaryDate: LocalDate,
    secondaryDate: LocalDate?,
    editingPrimary: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    onSelectPrimary: () -> Unit,
    onSelectSecondary: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateRoleChip(
            label = formatDate(primaryDate),
            color = primaryColor,
            active = editingPrimary,
            onClick = onSelectPrimary,
        )
        Text(
            text = HolidayStr.makeupConnector(),
            modifier = Modifier.padding(horizontal = 8.dp),
            fontSize = 13.sp,
            color = scheme.onSurfaceVariantSummary,
        )
        DateRoleChip(
            label = secondaryDate?.let(::formatDate) ?: HolidayStr.pickDateShort(),
            color = secondaryColor,
            active = !editingPrimary,
            onClick = onSelectSecondary,
        )
    }
}

@Composable
private fun DateRoleChip(
    label: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(ContinuousCapsule())
            .background(if (active) color else color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) Color.White else color,
        )
    }
}

private fun formatDate(date: LocalDate): String =
    "${date.year}/${date.monthNumber}/${date.dayOfMonth}"

private enum class BandHalf { None, Leading, Trailing, Full }

private data class DayStyle(
    val band: Color? = null,
    val bandHalf: BandHalf = BandHalf.None,
    val fill: Color? = null,
    val content: Color,
)

/** 卡片外框：统一的四边内间距 + 月份标题（固定）+ 可平移的月视图 + 可选脚注。 */
@Composable
private fun CalendarFrame(
    year: Int,
    month: Int,
    monthTitle: (year: Int, month: Int) -> String,
    onShiftMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
    monthContent: @Composable (year: Int, month: Int) -> Unit,
) {
    Column(
        // 内间距由组件持有：调用方把 Card 的 insideMargin 归零，四边就都一致
        modifier = modifier.padding(CONTENT_PADDING),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthNavButton(
                imageVector = MiuixIcons.ChevronBackward,
                contentDescription = HolidayStr.prevMonth(),
                onClick = { onShiftMonth(-1) },
            )
            Text(
                text = monthTitle(year, month),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            MonthNavButton(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = HolidayStr.nextMonth(),
                onClick = { onShiftMonth(1) },
            )
        }

        // 翻月整块平移：日期格（连同星期头）一起滑走/滑入，标题不跟着抖
        AnimatedContent(
            targetState = year * 12 + (month - 1),
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                val forward = targetState > initialState
                val slide = tween<IntOffset>(durationMillis = 240, easing = FastOutSlowInEasing)
                val fade = tween<Float>(durationMillis = 200)
                if (forward) {
                    (slideInHorizontally(slide) { it } + fadeIn(fade)) togetherWith
                        (slideOutHorizontally(slide) { -it } + fadeOut(fade))
                } else {
                    (slideInHorizontally(slide) { -it } + fadeIn(fade)) togetherWith
                        (slideOutHorizontally(slide) { it } + fadeOut(fade))
                }
            },
            contentAlignment = Alignment.TopCenter,
            label = "calendar-month",
        ) { monthKey ->
            monthContent(monthKey / 12, monthKey % 12 + 1)
        }

        footer()
    }
}

@Composable
private fun MonthNavButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(ContinuousCapsule())
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun MonthGrid(
    year: Int,
    month: Int,
    weekdayLabels: List<String>,
    styleOf: (LocalDate) -> DayStyle,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstOfMonth = LocalDate(year, month, 1)
    val leading = firstOfMonth.dayOfWeek.isoDayNumber - 1
    val daysInMonth = firstOfMonth.lengthOfMonth()
    val rowCount = (leading + daysInMonth + 6) / 7

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(rowCount) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val day = row * 7 + column - leading + 1
                        if (day in 1..daysInMonth) {
                            val date = LocalDate(year, month, day)
                            CalendarDayCell(
                                date = date,
                                style = styleOf(date),
                                modifier = Modifier.weight(1f),
                                onClick = { onDayClick(date) },
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    style: DayStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(CELL_HEIGHT)
            // 网格点选不要水波纹：日期选中态自己就是反馈，水波纹反而脏
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 区间条只画在「圆之外」：中间日铺满整格，起止日只铺朝区间内的半边 ——
        // 不再让胶囊压在圆角方块上（圆套方）
        val band = style.band
        if (band != null) {
            val base = Modifier
                .fillMaxHeight()
                .padding(vertical = 3.dp)
                .background(band)
            when (style.bandHalf) {
                BandHalf.Full -> Box(base.fillMaxWidth())
                BandHalf.Trailing -> Box(base.fillMaxWidth(0.5f).align(Alignment.CenterEnd))
                BandHalf.Leading -> Box(base.fillMaxWidth(0.5f).align(Alignment.CenterStart))
                BandHalf.None -> Unit
            }
        }

        Box(
            modifier = Modifier
                .size(MARK_SIZE)
                .clip(ContinuousCapsule())
                .background(style.fill ?: Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (style.fill != null) FontWeight.Medium else FontWeight.Normal,
                color = style.content,
            )
        }
    }
}

