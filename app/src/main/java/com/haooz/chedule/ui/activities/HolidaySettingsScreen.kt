package com.haooz.chedule.ui.activities

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.utils.overScrollVertical
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PopupPositionResult
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.layout.liquidDropdownPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val YEAR_RANGE = 2024..2035
private val WEEKDAYS = listOf(
    "星期一",
    "星期二",
    "星期三",
    "星期四",
    "星期五",
    "星期六",
    "星期日",
)

private fun monthLabel(value: Int): String = "${value}月"
private fun dayLabel(value: Int): String = "${value}日"

@Composable
fun HolidaySettingsScreen(
    scrollBehavior: SharedScrollBehavior?,
    liquidGlassBackdrop: Backdrop?,
    year: Int,
    entries: List<HolidayManager.Entry>,
    onYearChange: (Int) -> Unit,
    reload: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // 编辑弹窗
    var showDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var dialogType by remember { mutableIntStateOf(HolidayManager.TYPE_HOLIDAY) }
    var editingEntry by remember { mutableStateOf<HolidayManager.Entry?>(null) }
    var name by remember { mutableStateOf("") }
    var startYear by remember { mutableIntStateOf(year) }
    var startMonth by remember { mutableIntStateOf(1) }
    var startDay by remember { mutableIntStateOf(1) }
    var endYear by remember { mutableIntStateOf(year) }
    var endMonth by remember { mutableIntStateOf(1) }
    var endDay by remember { mutableIntStateOf(1) }
    var followWeek by remember { mutableStateOf("1") }
    var followWeekday by remember { mutableStateOf("1") }

    // 根据开始日期计算其对应课表的默认周次
    fun weekOfDate(year: Int, month: Int, day: Int): String {
        val classStartTime = CourseRepository.getInstance(context).getClassStartTime()
        return try {
            val start = LocalDate.parse(classStartTime.replace("/", "-"))
            val startMonday = start.minusDays((start.dayOfWeek.value - 1).toLong())
            val date = LocalDate.of(year, month, day)
            ChronoUnit.DAYS.between(startMonday, date).floorDiv(7).toInt() + 1
        } catch (_: Exception) {
            1
        }.toString()
    }

    fun startAdding(type: Int) {
        dialogType = type
        editingEntry = null
        name = ""
        startYear = year
        startMonth = 1
        startDay = 1
        endYear = year
        endMonth = 1
        endDay = 1
        followWeek = if (type == HolidayManager.TYPE_WORKSWAP) {
            weekOfDate(startYear, startMonth, startDay)
        } else {
            "1"
        }
        followWeekday = "1"
        showDialog = true
    }

    fun startEditing(entry: HolidayManager.Entry) {
        val start = runCatching { LocalDate.parse(entry.date) }.getOrNull()
            ?: LocalDate.of(year, 1, 1)
        val end = runCatching { LocalDate.parse(entry.endDate.ifBlank { entry.date }) }.getOrNull()
            ?: start
        dialogType = entry.type
        editingEntry = entry
        name = entry.name
        startYear = start.year
        startMonth = start.monthValue
        startDay = start.dayOfMonth
        endYear = end.year
        endMonth = end.monthValue
        endDay = end.dayOfMonth
        followWeek = if (entry.type == HolidayManager.TYPE_WORKSWAP) {
            weekOfDate(startYear, startMonth, startDay)
        } else {
            "1"
        }
        followWeekday = if (entry.followWeekday > 0) {
            entry.followWeekday.toString()
        } else {
            "1"
        }
        showDialog = true
    }

    fun saveEntry() {
        val isHoliday = dialogType == HolidayManager.TYPE_HOLIDAY
        val startDate = "%04d-%02d-%02d".format(startYear, startMonth, startDay)
        if (runCatching { LocalDate.parse(startDate) }.isFailure) {
            Toast.makeText(context, "日期格式不正确", Toast.LENGTH_SHORT).show()
            return
        }
        val endDate = if (isHoliday) {
            "%04d-%02d-%02d".format(endYear, endMonth, endDay)
        } else {
            ""
        }
        if (isHoliday && endDate < startDate) {
            Toast.makeText(context, "结束日期不能早于开始日期", Toast.LENGTH_SHORT).show()
            return
        }
        val week = followWeek.toIntOrNull()?.takeIf { it > 0 } ?: -1
        val weekday = followWeekday.toIntOrNull()?.takeIf { it in 1..7 } ?: -1
        val all = HolidayManager.load(context, year).toMutableList()
        editingEntry?.let { old ->
            all.removeAll {
                it.date == old.date &&
                    it.type == old.type &&
                    it.name == old.name
            }
        }
        all += HolidayManager.Entry(
            date = startDate,
            endDate = endDate,
            name = name.ifBlank { if (isHoliday) "节假日" else "调休工作日" },
            type = dialogType,
            followWeek = if (isHoliday) -1 else week,
            followWeekday = if (isHoliday) -1 else weekday,
            custom = true,
        )
        HolidayManager.save(context, year, all)
        reload()
        CourseReminderHelper.startReminderService(context)
        showDialog = false
        editingEntry = null
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    fun deleteEntry() {
        val all = HolidayManager.load(context, year).toMutableList()
        editingEntry?.let { old ->
            all.removeAll {
                it.date == old.date &&
                    it.type == old.type &&
                    it.name == old.name
            }
        }
        HolidayManager.save(context, year, all)
        reload()
        CourseReminderHelper.startReminderService(context)
        showDeleteConfirm = false
        showDialog = false
        editingEntry = null
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    val listState = rememberLazyListState()
    val holidayEntries = entries.filter { it.type == HolidayManager.TYPE_HOLIDAY }
    val workswapEntries = entries.filter { it.type == HolidayManager.TYPE_WORKSWAP }

    Scaffold(topBar = {}) { padding ->
        val topBarHeightDp = with(LocalDensity.current) {
            (scrollBehavior?.currentHeightPx ?: 0f).toDp()
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .then(
                    scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) }
                        ?: Modifier
                ),
            contentPadding = PaddingValues(
                16.dp,
                padding.calculateTopPadding() + topBarHeightDp + 12.dp,
                16.dp,
                60.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DataManagementCard(
                    year = year,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    onYearChange = onYearChange,
                )
            }

            item {
                SectionTitleRow(
                    text = "节假日",
                    description = "• 处于假期范围内的日期不会发送课程提醒\n• 可在此手动添加或编辑放假日期",
                    liquidGlassBackdrop = liquidGlassBackdrop,
                )
                if (holidayEntries.isNotEmpty()) {
                    HolidayEntriesCard(
                        entries = holidayEntries,
                        onEdit = { startEditing(it) },
                    )
                    Spacer(modifier = Modifier.fillMaxWidth().height(12.dp))
                }
                AddEntryCard(
                    type = HolidayManager.TYPE_HOLIDAY,
                    onAdd = { startAdding(HolidayManager.TYPE_HOLIDAY) },
                )
            }

            item {
                SectionTitleRow(
                    text = "调休工作日",
                    description = "• 原本的日常休息日因调休需要补课\n• 可在此指定某一天作为补班课程安排",
                    liquidGlassBackdrop = liquidGlassBackdrop,
                )
                if (workswapEntries.isNotEmpty()) {
                    HolidayEntriesCard(
                        entries = workswapEntries,
                        onEdit = { startEditing(it) },
                    )
                    Spacer(modifier = Modifier.fillMaxWidth().height(12.dp))
                }
                AddEntryCard(
                    type = HolidayManager.TYPE_WORKSWAP,
                    onAdd = { startAdding(HolidayManager.TYPE_WORKSWAP) },
                )
            }
        }
    }

    EntryEditDialog(
        show = showDialog,
        dialogTitle = if (editingEntry == null) {
            if (dialogType == HolidayManager.TYPE_HOLIDAY) {
                "添加节假日"
            } else {
                "添加调休工作日"
            }
        } else {
            val suffix = if (dialogType == HolidayManager.TYPE_HOLIDAY) {
                "节假日"
            } else {
                "调休工作日"
            }
            "编辑$suffix"
        },
        dialogType = dialogType,
        name = name,
        onNameChange = { name = it },
        startYear = startYear,
        startMonth = startMonth,
        startDay = startDay,
        onStartYearChange = { startYear = it },
        onStartMonthChange = { startMonth = it },
        onStartDayChange = { startDay = it },
        endYear = endYear,
        endMonth = endMonth,
        endDay = endDay,
        onEndYearChange = { endYear = it },
        onEndMonthChange = { endMonth = it },
        onEndDayChange = { endDay = it },
        followWeek = followWeek,
        followWeekday = followWeekday,
        onFollowWeekChange = { followWeek = it },
        onFollowWeekdayChange = { followWeekday = it },
        liquidGlassBackdrop = liquidGlassBackdrop,
        canDelete = editingEntry != null,
        onDeleteClick = { showDeleteConfirm = true },
        onDismiss = { showDialog = false },
        onSave = { saveEntry() },
    )

    OverlayDialog(
        title = "删除记录",
        summary = "确定要删除这条${if (dialogType == HolidayManager.TYPE_HOLIDAY) "节假日" else "调休工作日"}记录吗？\n此操作不可撤销。",
        show = showDeleteConfirm,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showDeleteConfirm = false },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                "取消",
                {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    showDeleteConfirm = false
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                "删除",
                { deleteEntry() },
                textColor = Color(0xFFF44336),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------- 区块标题（含功能说明） ----------

/**
 * 基于默认下拉位置，再沿展开方向外推 offsetPx（向下展开往下、向上展开往上）。
 */
private fun expandDirectionOffsetProvider(offsetPx: Int): PopupPositionProvider {
    val base = liquidDropdownPositionProvider()
    return object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowBounds: IntRect,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
            popupMargin: IntRect,
            alignment: PopupPositionProvider.Align,
        ): PopupPositionResult {
            val result = base.calculatePosition(
                anchorBounds,
                windowBounds,
                layoutDirection,
                popupContentSize,
                popupMargin,
                alignment,
            )
            val deltaY = when {
                result.showBelow -> offsetPx
                result.showAbove -> -offsetPx
                else -> 0
            }
            val clampedY = (result.offset.y + deltaY).coerceIn(
                windowBounds.top + popupMargin.top,
                windowBounds.bottom - popupContentSize.height - popupMargin.bottom,
            )
            return PopupPositionResult(
                IntOffset(result.offset.x, clampedY),
                result.showBelow,
                result.showAbove,
            )
        }

        override fun getMargins(): PaddingValues = base.getMargins()
    }
}

@Composable
private fun SectionTitleRow(
    text: String,
    description: String,
    liquidGlassBackdrop: Backdrop?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset((-16).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallTitle(
            text = text,
            modifier = Modifier.weight(1f),
        )
        InfoDropdown(
            description = description,
            liquidGlassBackdrop = liquidGlassBackdrop,
        )
    }
}

@Composable
private fun InfoDropdown(
    description: String,
    liquidGlassBackdrop: Backdrop?,
) {
    var expanded by remember { mutableStateOf(false) }
    val infoColor = MiuixTheme.colorScheme.primary
    val density = LocalDensity.current
    val positionProvider = remember {
        expandDirectionOffsetProvider(with(density) { 16.dp.roundToPx() })
    }
    Box(
        modifier = Modifier
            .size(36.dp)

            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { expanded = !expanded },
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Image(
            imageVector = MiuixIcons.Info,
            contentDescription = null,
            colorFilter = ColorFilter.tint(infoColor),
            modifier = Modifier.size(20.dp),
        )
        OverlayListPopup(
            show = expanded,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { expanded = false },
            popupPositionProvider = positionProvider,
            liquidGlassBackdrop = liquidGlassBackdrop,
        ) {
            ListPopupColumn {
                Text(
                    text = description,
                    fontSize = 14.2.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .width(200.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}

// ---------- 列表区块 ----------

@Composable
private fun DataManagementCard(
    year: Int,
    liquidGlassBackdrop: Backdrop?,
    onYearChange: (Int) -> Unit,
) {
    val dropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        OverlayDropdownMenu(
            entry = DropdownEntry(
                listOf(year - 1, year, year + 1)
                    .filter { it in YEAR_RANGE }
                    .map { selectedYear ->
                        DropdownItem(
                            text = selectedYear.toString(),
                            selected = selectedYear == year,
                            onClick = { onYearChange(selectedYear) },
                        )
                    }
            ),
            title = "年份",
            collapseOnSelection = true,
            liquidGlassBackdrop = liquidGlassBackdrop,
            dropdownColors = dropdownColors,
        )
    }
}

@Composable
private fun HolidayEntriesCard(
    entries: List<HolidayManager.Entry>,
    onEdit: (HolidayManager.Entry) -> Unit,
) {
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column {
            entries.forEach { entry ->
                EntryRow(entry = entry, onEdit = onEdit)
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: HolidayManager.Entry,
    onEdit: (HolidayManager.Entry) -> Unit,
) {
    ArrowPreference(
        title = entry.name,
        summary = entrySummary(entry),
        onClick = { onEdit(entry) },
    )
}

private fun displayDate(value: String): String {
    val parts = value.split("-")
    if (parts.size != 3) return value
    val (y, m, d) = parts
    return "${y}/${m.padStart(2, '0')}/${d.padStart(2, '0')}"
}

// 根据指定周次和星期，计算其对应的真实日期（以开学所在周的周一作为第 1 周起点）
private fun followDate(
    context: Context,
    followWeek: String,
    followWeekday: String,
): LocalDate? {
    val week = followWeek.toIntOrNull() ?: return null
    val weekday = followWeekday.toIntOrNull() ?: return null
    if (week < 1 || weekday !in 1..7) return null
    val classStartTime = CourseRepository.getInstance(context).getClassStartTime()
    return try {
        val start = LocalDate.parse(classStartTime.replace("/", "-"))
        val startMonday = start.minusDays((start.dayOfWeek.value - 1).toLong())
        startMonday.plusDays((week - 1) * 7L + (weekday - 1))
    } catch (_: Exception) {
        null
    }
}

private fun entrySummary(entry: HolidayManager.Entry): String {
    return if (entry.type == HolidayManager.TYPE_HOLIDAY) {
        val endSuffix = if (entry.endDate.isNotBlank()) {
            " 至 ${displayDate(entry.endDate)}"
        } else {
            ""
        }
        "${displayDate(entry.date)}$endSuffix"
    } else {
        val mapping = if (entry.followWeek > 0 && entry.followWeekday in 1..7) {
            "第${entry.followWeek}周${WEEKDAYS[entry.followWeekday - 1]}"
        } else {
            "待配置补班课程"
        }
        "${displayDate(entry.date)} · $mapping"
    }
}

@Composable
private fun AddEntryCard(type: Int, onAdd: () -> Unit) {
    val isHoliday = type == HolidayManager.TYPE_HOLIDAY
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        ArrowPreference(
            title = if (isHoliday) "添加节假日" else "添加调休工作日",
            onClick = onAdd,
        )
    }
}

// ---------- 编辑弹窗 ----------

@Composable
private fun EntryEditDialog(
    show: Boolean,
    dialogTitle: String,
    dialogType: Int,
    name: String,
    onNameChange: (String) -> Unit,
    startYear: Int,
    startMonth: Int,
    startDay: Int,
    onStartYearChange: (Int) -> Unit,
    onStartMonthChange: (Int) -> Unit,
    onStartDayChange: (Int) -> Unit,
    endYear: Int,
    endMonth: Int,
    endDay: Int,
    onEndYearChange: (Int) -> Unit,
    onEndMonthChange: (Int) -> Unit,
    onEndDayChange: (Int) -> Unit,
    followWeek: String,
    followWeekday: String,
    onFollowWeekChange: (String) -> Unit,
    onFollowWeekdayChange: (String) -> Unit,
    liquidGlassBackdrop: Backdrop?,
    canDelete: Boolean,
    onDeleteClick: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val isHoliday = dialogType == HolidayManager.TYPE_HOLIDAY
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    OverlayDialog(
        title = dialogTitle,
        summary = null,
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismiss,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NativeMiuixTextField(
                name,
                onNameChange,
                label = "名称",
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledDatePickerRow(
                text = "开始日期",
                year = startYear,
                month = startMonth,
                day = startDay,
                onYearChange = onStartYearChange,
                onMonthChange = onStartMonthChange,
                onDayChange = onStartDayChange,
            )
            if (isHoliday) {
                LabeledDatePickerRow(
                    text = "结束日期",
                    year = endYear,
                    month = endMonth,
                    day = endDay,
                    onYearChange = onEndYearChange,
                    onMonthChange = onEndMonthChange,
                    onDayChange = onEndDayChange,
                )
            }
            if (!isHoliday) {
                val date = followDate(context, followWeek, followWeekday)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "跟随课程",
                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Normal),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    )
                    if (date != null) {
                        Text(
                            "${date.year}/${date.monthValue}/${date.dayOfMonth}",
                            style = MiuixTheme.textStyles.body1.copy(
                                fontSize = 15.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            ),
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberPicker(
                        followWeek.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        { onFollowWeekChange(it.toString()) },
                        range = 1..52,
                        visibleItemCount = 3,
                        itemHeight = 44.dp,
                        textStyle = pickerTextStyle(),
                        label = { "第${it}周" },
                        modifier = Modifier.weight(1f),
                    )
                    NumberPicker(
                        followWeekday.toIntOrNull()?.coerceIn(1, 7) ?: 1,
                        { onFollowWeekdayChange(it.toString()) },
                        range = 1..7,
                        visibleItemCount = 3,
                        itemHeight = 44.dp,
                        textStyle = pickerTextStyle(),
                        label = { WEEKDAYS[it - 1] },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    "取消",
                    {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    "保存",
                    {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSave()
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (canDelete) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (-42).dp)
                    .size(36.dp)
                    .clip(ContinuousRoundedRectangle(20))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDeleteClick()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = "删除",
                    colorFilter = ColorFilter.tint(Color(0xFFF44336)),
                    modifier = Modifier.size(23.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun LabeledDatePickerRow(
    text: String,
    year: Int,
    month: Int,
    day: Int,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onDayChange: (Int) -> Unit,
) {
    Text(
        text,
        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Normal),
        modifier = Modifier.padding(start = 16.dp),
    )
    val maxDay = LocalDate.of(year, month, 1).lengthOfMonth()
    val currentYear = remember { LocalDate.now().year }
    val yearRange = (currentYear - 1)..(currentYear + 1)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        NumberPicker(
            year,
            { newYear ->
                onYearChange(newYear)
                onDayChange(day.coerceAtMost(LocalDate.of(newYear, month, 1).lengthOfMonth()))
            },
            range = yearRange,
            visibleItemCount = 3,
            itemHeight = 44.dp,
            textStyle = pickerTextStyle(),
            modifier = Modifier.weight(1f),
        )
        NumberPicker(
            month,
            { newMonth ->
                onMonthChange(newMonth)
                onDayChange(day.coerceAtMost(LocalDate.of(year, newMonth, 1).lengthOfMonth()))
            },
            range = 1..12,
            visibleItemCount = 3,
            itemHeight = 44.dp,
            textStyle = pickerTextStyle(),
            label = { monthLabel(it) },
            modifier = Modifier.weight(1f),
        )
        NumberPicker(
            day,
            { newDay -> onDayChange(newDay.coerceIn(1, maxDay)) },
            range = 1..maxDay,
            visibleItemCount = 3,
            itemHeight = 44.dp,
            textStyle = pickerTextStyle(),
            label = { dayLabel(it) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun pickerTextStyle() = MiuixTheme.textStyles.body1.copy(
    fontSize = 22.sp,
    fontWeight = FontWeight.Medium,
)