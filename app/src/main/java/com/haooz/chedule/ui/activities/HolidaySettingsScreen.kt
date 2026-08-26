package com.haooz.chedule.ui.activities

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.utils.overScrollVertical
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

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

private fun yearLabel(value: Int): String = "${value}年"
private fun monthLabel(value: Int): String = "${value}月"
private fun dayLabel(value: Int): String = "${value}日"

@Composable
fun HolidaySettingsScreen(
    scrollBehavior: SharedScrollBehavior?,
    liquidGlassBackdrop: Backdrop?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentDate = remember { LocalDate.now() }

    // 列表数据
    var year by remember { mutableIntStateOf(currentDate.year) }
    var entries by remember { mutableStateOf(HolidayManager.load(context, year)) }
    var loading by remember { mutableStateOf(false) }

    // 编辑弹窗
    var showDialog by remember { mutableStateOf(false) }
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

    fun reload() {
        entries = HolidayManager.load(context, year)
    }

    fun requestYear(targetYear: Int, automatic: Boolean = false) {
        if (loading) return
        loading = true
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val conn = URL("https://unpkg.com/holiday-calendar@1.3.0/data/CN/$targetYear.json")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                HolidayManager.parseApiResponse(text)
            }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                HolidayManager.mergeApiEntries(context, targetYear, result)
                if (targetYear == year) reload()
                loading = false
                if (!automatic) {
                    val message = if (result.isEmpty()) {
                        "获取失败或暂无数据"
                    } else {
                        "已更新 ${result.size} 条记录"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun clearYear() {
        HolidayManager.clear(context, year)
        reload()
        CourseReminderHelper.startReminderService(context)
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
        followWeek = "1"
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
        followWeek = if (entry.followWeek > 0) {
            entry.followWeek.toString()
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
        val old = editingEntry ?: return
        val remaining = HolidayManager.load(context, year).filterNot {
            it.date == old.date &&
                it.type == old.type &&
                it.name == old.name
        }
        HolidayManager.save(context, year, remaining)
        reload()
        CourseReminderHelper.startReminderService(context)
        showDialog = false
        editingEntry = null
    }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (entries.isEmpty()) requestYear(year, automatic = true)
    }

    Scaffold(topBar = {}) { padding ->
        val topBarHeightDp = with(LocalDensity.current) {
            (scrollBehavior?.currentHeightPx ?: 0f).toDp()
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .then(
                    scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) }
                        ?: Modifier
                ),
            contentPadding = PaddingValues(
                16.dp,
                padding.calculateTopPadding() + topBarHeightDp,
                16.dp,
                48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DataManagementCard(
                    year = year,
                    loading = loading,
                    currentDate = currentDate,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    onYearChange = { newYear ->
                        year = newYear
                        reload()
                    },
                    onRequestData = { requestYear(year) },
                    onClear = { clearYear() },
                )
            }

            if (entries.isEmpty()) {
                item {
                    EmptyPlaceholderCard(loading = loading, year = year)
                }
            }

            item {
                SmallTitle(text = "节假日", modifier = Modifier.offset((-15).dp))
            }
            item {
                HolidayEntriesCard(
                    type = HolidayManager.TYPE_HOLIDAY,
                    emptyText = "暂无 节假日",
                    entries = entries,
                    onEdit = { startEditing(it) },
                )
            }
            item {
                AddEntryCard(
                    type = HolidayManager.TYPE_HOLIDAY,
                    onAdd = { startAdding(HolidayManager.TYPE_HOLIDAY) },
                )
            }

            item {
                SmallTitle(text = "调休工作日", modifier = Modifier.offset((-15).dp))
            }
            item {
                HolidayEntriesCard(
                    type = HolidayManager.TYPE_WORKSWAP,
                    emptyText = "暂无 调休工作日",
                    entries = entries,
                    onEdit = { startEditing(it) },
                )
            }
            item {
                AddEntryCard(
                    type = HolidayManager.TYPE_WORKSWAP,
                    onAdd = { startAdding(HolidayManager.TYPE_WORKSWAP) },
                )
            }
        }
    }

    if (showDialog) {
        EntryEditDialog(
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
            editingEntry = editingEntry,
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
            onDismiss = { showDialog = false },
            onSave = { saveEntry() },
            onDelete = { deleteEntry() },
        )
    }
}

// ---------- 列表区块 ----------

@Composable
private fun DataManagementCard(
    year: Int,
    loading: Boolean,
    currentDate: LocalDate,
    liquidGlassBackdrop: Backdrop?,
    onYearChange: (Int) -> Unit,
    onRequestData: () -> Unit,
    onClear: () -> Unit,
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
        Column {
            if (currentDate.monthValue == 12) {
                OverlayDropdownMenu(
                    entry = DropdownEntry(
                        (currentDate.year..currentDate.year + 1).map { selectedYear ->
                            DropdownItem(
                                text = selectedYear.toString(),
                                selected = selectedYear == year,
                                onClick = { onYearChange(selectedYear) },
                            )
                        }
                    ),
                    title = "年份",
                    summary = year.toString(),
                    collapseOnSelection = true,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    dropdownColors = dropdownColors,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    if (loading) "正在获取" else "获取网络数据",
                    onRequestData,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                TextButton(
                    "清空",
                    onClear,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EmptyPlaceholderCard(loading: Boolean, year: Int) {
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (loading) "正在获取 $year 年数据" else "暂无 $year 年节假日数据",
                style = MiuixTheme.textStyles.body1,
            )
            Text(
                if (loading) "请稍候" else "可以获取网络数据，或使用下方入口手动添加",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}

@Composable
private fun HolidayEntriesCard(
    type: Int,
    emptyText: String,
    entries: List<HolidayManager.Entry>,
    onEdit: (HolidayManager.Entry) -> Unit,
) {
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        val filtered = entries.filter { it.type == type }
        if (filtered.isEmpty()) {
            Text(
                emptyText,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Column {
                filtered.forEach { entry ->
                    EntryRow(entry = entry, onEdit = onEdit)
                }
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

private fun entrySummary(entry: HolidayManager.Entry): String {
    return if (entry.type == HolidayManager.TYPE_HOLIDAY) {
        val endSuffix = if (entry.endDate.isNotBlank()) {
            " 至 ${entry.endDate}"
        } else {
            ""
        }
        "${entry.date}$endSuffix"
    } else {
        val mapping = if (entry.followWeek > 0 && entry.followWeekday in 1..7) {
            "第${entry.followWeek}周${WEEKDAYS[entry.followWeekday - 1]}"
        } else {
            "待配置补班课程"
        }
        "${entry.date} · $mapping"
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
            summary = if (isHoliday) "当天不发送课程提醒" else "按指定周次和星期匹配课程",
            onClick = onAdd,
        )
    }
}

// ---------- 编辑弹窗 ----------

@Composable
private fun EntryEditDialog(
    dialogTitle: String,
    dialogType: Int,
    editingEntry: HolidayManager.Entry?,
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
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val isHoliday = dialogType == HolidayManager.TYPE_HOLIDAY
    OverlayDialog(
        title = dialogTitle,
        summary = null,
        show = true,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
            NativeMiuixTextField(
                name,
                onNameChange,
                label = "名称",
                modifier = Modifier.fillMaxWidth(),
            )
            if (!isHoliday) {
                Text(
                    "跟随课程",
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Normal),
                )
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
                        modifier = Modifier.weight(1.2f),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    "保存",
                    onSave,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
                if (editingEntry != null) {
                    TextButton(
                        "删除",
                        onDelete,
                        modifier = Modifier.weight(1f),
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
    )
    val maxDay = LocalDate.of(year, month, 1).lengthOfMonth()
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
            range = YEAR_RANGE,
            visibleItemCount = 3,
            itemHeight = 44.dp,
            textStyle = pickerTextStyle(),
            label = { yearLabel(it) },
            modifier = Modifier.weight(1.35f),
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
            modifier = Modifier.weight(0.9f),
        )
        NumberPicker(
            day,
            { newDay -> onDayChange(newDay.coerceIn(1, maxDay)) },
            range = 1..maxDay,
            visibleItemCount = 3,
            itemHeight = 44.dp,
            textStyle = pickerTextStyle(),
            label = { dayLabel(it) },
            modifier = Modifier.weight(0.9f),
        )
    }
}

@Composable
private fun pickerTextStyle() = MiuixTheme.textStyles.body1.copy(
    fontSize = 18.sp,
    fontWeight = FontWeight.Normal,
)