package com.haooz.chedule.ui.activities

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
import com.haooz.chedule.data.TeachingWeekReorganization
import com.haooz.chedule.data.TeachingWeekReorganizationRule
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.basic.collapsibleTopInset
import com.haooz.chedule.ui.utils.overScrollVertical
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
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
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.layout.liquidDropdownPositionProvider
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.overlay.BlurBottomSheetTablet
import top.yukonga.miuix.kmp.overlay.LocalBlurBottomSheetContentExpanded
import top.yukonga.miuix.kmp.overlay.LocalSheetContentBackdrop
import top.yukonga.miuix.kmp.overlay.LocalSheetTopBarMaterial
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.LocalDate
import kotlin.math.floor

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
    onTeachingWeekReorganizationsChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val holidayDataRevision by HolidayManager.dataRevision.collectAsState()
    val repository = remember(context) { CourseRepository.getInstance(context) }
    val currentScheduleId = repository.getCurrentScheduleId()
    val currentTotalWeeks = repository.getTotalWeeks(currentScheduleId)
    val semesterStartDate = remember(currentScheduleId) {
        runCatching {
            LocalDate.parse(repository.getClassStartTime(currentScheduleId).replace("/", "-"))
        }.getOrElse { LocalDate.now() }
    }
    var teachingWeekReorganizations by remember(currentScheduleId) {
        mutableStateOf(repository.getTeachingWeekReorganizations(currentScheduleId))
    }
    val configuredSectionCount = repository.getMorningSections(currentScheduleId) +
        repository.getAfternoonSections(currentScheduleId) +
        repository.getEveningSections(currentScheduleId)
    val endCourseExclusion = remember(context, holidayDataRevision) {
        HolidayManager.loadEndCourseExclusion(context)
    }
    var showSectionRangeDialog by remember { mutableStateOf(false) }
    var pendingStartSection by remember { mutableIntStateOf(1) }
    var pendingEndSection by remember { mutableIntStateOf(1) }
    // 编辑弹窗
    var showDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var dialogType by remember { mutableIntStateOf(HolidayManager.TYPE_HOLIDAY) }
    var editingEntry by remember { mutableStateOf<HolidayManager.Entry?>(null) }
    var editingEntryStorageYear by remember { mutableIntStateOf(year) }
    var name by remember { mutableStateOf("") }
    var startYear by remember { mutableIntStateOf(year) }
    var startMonth by remember { mutableIntStateOf(1) }
    var startDay by remember { mutableIntStateOf(1) }
    var endYear by remember { mutableIntStateOf(year) }
    var endMonth by remember { mutableIntStateOf(1) }
    var endDay by remember { mutableIntStateOf(1) }
    var followWeek by remember { mutableStateOf("1") }
    var followWeekManuallySelected by remember { mutableStateOf(false) }
    var followWeekday by remember { mutableStateOf("1") }
    var showTeachingWeekRuleDialog by remember { mutableStateOf(false) }
    var showTeachingWeekPicker by remember { mutableStateOf(false) }
    var showTeachingWeekDeleteConfirm by remember { mutableStateOf(false) }
    var editingTeachingWeekRuleIndex by remember { mutableIntStateOf(-1) }
    var editingWeekPickerHalf by remember { mutableIntStateOf(1) }
    var pendingOriginalWeek by remember { mutableIntStateOf(1) }
    var firstOriginalWeek by remember { mutableIntStateOf(4) }
    var secondOriginalWeek by remember { mutableIntStateOf(5) }
    var firstEndWeekday by remember { mutableIntStateOf(3) }

    // 根据开始日期计算其对应课表的默认周次
    fun weekOfDate(year: Int, month: Int, day: Int): String {
        return try {
            val date = LocalDate.of(year, month, day)
            TeachingWeekReorganization.mapDate(
                semesterStartDate,
                date,
                teachingWeekReorganizations,
            ).week.coerceIn(1L, currentTotalWeeks.toLong()).toInt()
        } catch (_: Exception) {
            1
        }.toString()
    }

    fun originalWeekDate(week: Int, weekday: Int): LocalDate? =
        TeachingWeekReorganization.dateForPosition(
            semesterStartDate,
            week,
            weekday,
            emptyList(),
        )

    fun formatDateRange(week: Int, startDay: Int, endDay: Int): String {
        val first = originalWeekDate(week, startDay) ?: return "日期超出范围"
        val last = originalWeekDate(week, endDay) ?: return "日期超出范围"
        return "${formatTeachingDate(first)}–${formatTeachingDate(last)}"
    }

    fun draftTeachingWeekRule() = TeachingWeekReorganizationRule(
        firstOriginalWeek = firstOriginalWeek,
        firstStartWeekday = 1,
        firstEndWeekday = firstEndWeekday,
        secondOriginalWeek = secondOriginalWeek,
        secondStartWeekday = firstEndWeekday + 1,
        secondEndWeekday = 7,
    )

    fun startAddingTeachingWeekRule() {
        editingTeachingWeekRuleIndex = -1
        val selected = TeachingWeekReorganization.firstAvailableStartingWeek(
            currentTotalWeeks,
            teachingWeekReorganizations,
        ) ?: run {
            Toast.makeText(context, "当前课表没有可用的原始周次可重组", Toast.LENGTH_SHORT).show()
            return
        }
        firstOriginalWeek = selected
        secondOriginalWeek = selected + 1
        firstEndWeekday = 3
        showTeachingWeekRuleDialog = true
    }

    fun startEditingTeachingWeekRule(index: Int) {
        val rule = teachingWeekReorganizations.getOrNull(index) ?: return
        editingTeachingWeekRuleIndex = index
        firstOriginalWeek = rule.firstOriginalWeek
        secondOriginalWeek = rule.secondOriginalWeek
        firstEndWeekday = rule.firstEndWeekday
        showTeachingWeekRuleDialog = true
    }

    fun openOriginalWeekPicker(half: Int) {
        editingWeekPickerHalf = half
        pendingOriginalWeek = if (half == 1) firstOriginalWeek else secondOriginalWeek
        showTeachingWeekPicker = true
    }

    fun saveTeachingWeekRule() {
        val proposed = draftTeachingWeekRule()
        val updated = teachingWeekReorganizations.toMutableList().apply {
            if (editingTeachingWeekRuleIndex in indices) removeAt(editingTeachingWeekRuleIndex)
            add(proposed)
        }.sortedBy { it.firstOriginalWeek }
        val error = TeachingWeekReorganization.validationErrorForRuleChange(
            updatedRules = updated,
            existingRules = teachingWeekReorganizations,
            changedRule = proposed,
            totalWeeks = currentTotalWeeks,
        )
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            return
        }
        if (!repository.setTeachingWeekReorganizations(
                rules = updated,
                scheduleId = currentScheduleId,
                allowExistingOutOfRangeRules = true,
                changedRule = proposed,
            )
        ) {
            Toast.makeText(context, "保存失败，现有规则未更改", Toast.LENGTH_SHORT).show()
            return
        }
        teachingWeekReorganizations = updated
        showTeachingWeekRuleDialog = false
        showTeachingWeekDeleteConfirm = false
        onTeachingWeekReorganizationsChanged()
        CourseReminderHelper.onHolidayDataChanged(context)
    }

    fun deleteTeachingWeekRule() {
        val rule = teachingWeekReorganizations.getOrNull(editingTeachingWeekRuleIndex) ?: return
        if (!repository.removeTeachingWeekReorganization(rule, currentScheduleId)) {
            Toast.makeText(context, "删除失败，现有规则未更改", Toast.LENGTH_SHORT).show()
            return
        }
        val updated = teachingWeekReorganizations.toMutableList().apply {
            removeAt(editingTeachingWeekRuleIndex)
        }
        teachingWeekReorganizations = updated
        showTeachingWeekDeleteConfirm = false
        showTeachingWeekRuleDialog = false
        onTeachingWeekReorganizationsChanged()
        CourseReminderHelper.onHolidayDataChanged(context)
    }

    fun startAdding(type: Int) {
        dialogType = type
        editingEntry = null
        editingEntryStorageYear = year
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
        followWeekManuallySelected = false
        showDialog = true
    }

    fun startEditing(entry: HolidayManager.Entry) {
        val start = runCatching { LocalDate.parse(entry.date) }.getOrNull()
            ?: LocalDate.of(year, 1, 1)
        val end = runCatching { LocalDate.parse(entry.endDate.ifBlank { entry.date }) }.getOrNull()
            ?: start
        dialogType = entry.type
        editingEntry = entry
        editingEntryStorageYear = year
        name = entry.name
        startYear = start.year
        startMonth = start.monthValue
        startDay = start.dayOfMonth
        endYear = end.year
        endMonth = end.monthValue
        endDay = end.dayOfMonth
        followWeek = if (entry.type == HolidayManager.TYPE_WORKSWAP && entry.followWeek > 0) {
            entry.followWeek.toString()
        } else if (entry.type == HolidayManager.TYPE_WORKSWAP) {
            weekOfDate(startYear, startMonth, startDay)
        } else {
            "1"
        }
        followWeekday = if (entry.followWeekday > 0) {
            entry.followWeekday.toString()
        } else {
            "1"
        }
        followWeekManuallySelected = entry.followWeek > 0
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
        if (!isHoliday && (week !in 1..currentTotalWeeks || weekday !in 1..7)) {
            Toast.makeText(context, "请明确选择有效的跟随周次和星期", Toast.LENGTH_SHORT).show()
            return
        }
        // 按开始日期所属年份落库，避免 UI 选中年与日期年不一致时 workSwap 查不到
        val entryYear = runCatching { LocalDate.parse(startDate).year }.getOrDefault(year)
        val oldYear = editingEntry?.let { editingEntryStorageYear }
        val affectedYears = buildSet {
            add(entryYear)
            oldYear?.let(::add)
        }
        val newEntry = HolidayManager.Entry(
            date = startDate,
            endDate = endDate,
            name = name.ifBlank { if (isHoliday) "节假日" else "调休工作日" },
            type = dialogType,
            followWeek = if (isHoliday) -1 else week,
            followWeekday = if (isHoliday) -1 else weekday,
            custom = true,
        )
        val saved = HolidayManager.updateEntries(context, affectedYears) { current ->
            val updated = current.mapValues { it.value.toMutableList() }.toMutableMap()
            editingEntry?.let { old ->
                updated[oldYear!!] = HolidayManager.withoutEntry(updated.getValue(oldYear), old)
                    .toMutableList()
            }
            val all = updated.getValue(entryYear)
            if (dialogType == HolidayManager.TYPE_WORKSWAP) {
                updated[entryYear] = HolidayManager.withoutCustomWorkSwapsOnDate(all, startDate)
                    .toMutableList()
            }
            updated.getValue(entryYear).add(newEntry)
            updated.mapValues { (_, entries) -> entries.toList() }
        }
        if (!saved) {
            Toast.makeText(context, "节假日数据无法读取，未覆盖原数据", Toast.LENGTH_LONG).show()
            return
        }
        reload()
        CourseReminderHelper.onHolidayDataChanged(context)
        showDialog = false
        editingEntry = null
    }

    fun deleteEntry() {
        val deleted = editingEntry?.let { old ->
            val oldYear = editingEntryStorageYear
            HolidayManager.updateEntries(context, setOf(oldYear)) { current ->
                val retained = HolidayManager.withoutEntry(current.getValue(oldYear), old)
                current + (oldYear to retained)
            }
        } ?: true
        if (!deleted) {
            Toast.makeText(context, "节假日数据无法读取，未删除原数据", Toast.LENGTH_LONG).show()
            return
        }
        reload()
        CourseReminderHelper.onHolidayDataChanged(context)
        showDeleteConfirm = false
        showDialog = false
        editingEntry = null
    }

    val listState = rememberLazyListState()
    val holidayEntries = entries.filter { it.type == HolidayManager.TYPE_HOLIDAY }
    val workswapEntries = entries.filter { it.type == HolidayManager.TYPE_WORKSWAP }
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    val draftRuleForPreview = draftTeachingWeekRule()
    val rulesAfterDraft = teachingWeekReorganizations.toMutableList().apply {
        if (editingTeachingWeekRuleIndex in indices) removeAt(editingTeachingWeekRuleIndex)
        add(draftRuleForPreview)
    }.sortedBy { it.firstOriginalWeek }
    val draftValidationError = TeachingWeekReorganization.validationErrorForRuleChange(
        updatedRules = rulesAfterDraft,
        existingRules = teachingWeekReorganizations,
        changedRule = draftRuleForPreview,
        totalWeeks = currentTotalWeeks,
    )
    val firstPartStartDate = originalWeekDate(firstOriginalWeek, 1)
    val firstPartEndDate = originalWeekDate(firstOriginalWeek, firstEndWeekday)
    val secondPartStartDate = originalWeekDate(secondOriginalWeek, firstEndWeekday + 1)
    val secondPartEndDate = originalWeekDate(secondOriginalWeek, 7)
    val pauseStartDate = firstPartEndDate?.plusDays(1)
    val pauseEndDate = secondPartStartDate?.minusDays(1)
    val currentFirstPartWeek = firstPartStartDate?.let {
        TeachingWeekReorganization.mapDate(semesterStartDate, it, teachingWeekReorganizations)
    }
    val currentSecondPartWeek = secondPartStartDate?.let {
        TeachingWeekReorganization.mapDate(semesterStartDate, it, teachingWeekReorganizations)
    }
    val savedFirstPartWeek = if (draftValidationError == null) firstPartStartDate?.let {
        TeachingWeekReorganization.mapDate(semesterStartDate, it, rulesAfterDraft).week
    } else null
    val savedSecondPartWeek = if (draftValidationError == null) secondPartStartDate?.let {
        TeachingWeekReorganization.mapDate(semesterStartDate, it, rulesAfterDraft).week
    } else null
    val weekPickerRules = teachingWeekReorganizations.toMutableList().apply {
        if (editingTeachingWeekRuleIndex in indices) removeAt(editingTeachingWeekRuleIndex)
    }
    val weekPickerMax = maxOf(
        TeachingWeekReorganization.maxOriginalWeek(currentTotalWeeks, weekPickerRules),
        firstOriginalWeek,
        secondOriginalWeek,
        pendingOriginalWeek,
    )
    val weekPickerDateSummary = formatDateRange(pendingOriginalWeek, 1, 7)

    Scaffold(topBar = {}) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .collapsibleTopInset(scrollBehavior)
                .then(
                    scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) }
                        ?: Modifier
                ),
            contentPadding = PaddingValues(
                tabletHorizontalPadding,
                padding.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight + 24.dp,
                tabletHorizontalPadding,
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

            item {
                SectionTitleRow(
                    text = "教学周重组",
                    description = "• 长假可能带来教学周的重组，并引发后续教学周的顺延更改\n• 可在此进行教学周重组，后续教学周将重新分配计算",
                    liquidGlassBackdrop = liquidGlassBackdrop,
                )
                if (teachingWeekReorganizations.isNotEmpty()) {
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        Column {
                            teachingWeekReorganizations.forEachIndexed { index, rule ->
                                val firstRange = formatDateRange(
                                    rule.firstOriginalWeek,
                                    rule.firstStartWeekday,
                                    rule.firstEndWeekday,
                                )
                                val secondRange = formatDateRange(
                                    rule.secondOriginalWeek,
                                    rule.secondStartWeekday,
                                    rule.secondEndWeekday,
                                )
                                val targetWeek = originalWeekDate(rule.firstOriginalWeek, 1)?.let {
                                    TeachingWeekReorganization.mapDate(
                                        semesterStartDate,
                                        it,
                                        teachingWeekReorganizations,
                                    ).week
                                }
                                ArrowPreference(
                                    title = "重组规则 ${index + 1}",
                                    summary = "原始第${rule.firstOriginalWeek}周 $firstRange + " +
                                        "原始第${rule.secondOriginalWeek}周 $secondRange → " +
                                        (targetWeek?.let { "教学第${it}周" } ?: "日期超出范围"),
                                    onClick = { startEditingTeachingWeekRule(index) },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.fillMaxWidth().height(12.dp))
                }
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "添加教学周重组规则",
                        onClick = { startAddingTeachingWeekRule() },
                    )
                }
            }

            item {
                SectionTitleRow(
                    text = "节假日末期课程排除",
                    description = "• 假期最后一天的某几节课可能需要正常上课\n• 可以将节假日最后一天的某几节课程排除在休假情况以外",
                    liquidGlassBackdrop = liquidGlassBackdrop,
                )
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SwitchPreference(
                        title = "开启节次排除",
                        checked = endCourseExclusion.enabled,
                        onCheckedChange = { enabled ->
                            val updated = endCourseExclusion.copy(enabled = enabled)
                            if (HolidayManager.saveEndCourseExclusion(context, updated)) {
                                CourseReminderHelper.onHolidayDataChanged(context)
                            }
                        },
                    )
                    ArrowPreference(
                        title = "节次范围",
                        summary = "第${endCourseExclusion.startSection}–${endCourseExclusion.endSection}节",
                        onClick = {
                            if (configuredSectionCount <= 0) {
                                Toast.makeText(context, "当前课表尚未配置节次", Toast.LENGTH_SHORT).show()
                            } else {
                                val maxSection = configuredSectionCount
                                pendingStartSection = endCourseExclusion.startSection.coerceIn(1, maxSection)
                                pendingEndSection = endCourseExclusion.endSection
                                    .coerceIn(pendingStartSection, maxSection)
                                showSectionRangeDialog = true
                            }
                        },
                    )
                }
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
        onStartYearChange = {
            startYear = it
            if (dialogType == HolidayManager.TYPE_WORKSWAP && !followWeekManuallySelected) followWeek = weekOfDate(startYear, startMonth, startDay)
        },
        onStartMonthChange = {
            startMonth = it
            if (dialogType == HolidayManager.TYPE_WORKSWAP && !followWeekManuallySelected) followWeek = weekOfDate(startYear, startMonth, startDay)
        },
        onStartDayChange = {
            startDay = it
            if (dialogType == HolidayManager.TYPE_WORKSWAP && !followWeekManuallySelected) followWeek = weekOfDate(startYear, startMonth, startDay)
        },
        endYear = endYear,
        endMonth = endMonth,
        endDay = endDay,
        onEndYearChange = { endYear = it },
        onEndMonthChange = { endMonth = it },
        onEndDayChange = { endDay = it },
        followWeek = followWeek,
        followWeekday = followWeekday,
        onFollowWeekChange = { followWeek = it; followWeekManuallySelected = true },
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

    SectionRangeDialog(
        show = showSectionRangeDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        sectionCount = configuredSectionCount,
        startSection = pendingStartSection,
        endSection = pendingEndSection,
        onStartSectionChange = { start ->
            pendingStartSection = start
            if (pendingEndSection < start) pendingEndSection = start
        },
        onEndSectionChange = { pendingEndSection = it },
        onDismiss = { showSectionRangeDialog = false },
        onSave = {
            val rangeFitsCurrentSchedule = configuredSectionCount > 0 &&
                pendingStartSection in 1..configuredSectionCount &&
                pendingEndSection in pendingStartSection..configuredSectionCount
            val updated = endCourseExclusion.copy(
                startSection = pendingStartSection,
                endSection = pendingEndSection,
            )
            if (rangeFitsCurrentSchedule && HolidayManager.saveEndCourseExclusion(context, updated)) {
                CourseReminderHelper.onHolidayDataChanged(context)
                showSectionRangeDialog = false
            } else {
                Toast.makeText(context, "当前课表节次范围已变化，请重新选择", Toast.LENGTH_SHORT).show()
            }
        },
    )

    TeachingWeekRuleEditDialog(
        show = showTeachingWeekRuleDialog,
        isEditing = editingTeachingWeekRuleIndex >= 0,
        firstWeek = firstOriginalWeek,
        firstWeekSummary = "原始第${firstOriginalWeek}周 · ${formatDateRange(firstOriginalWeek, 1, 7)}",
        secondWeek = secondOriginalWeek,
        secondWeekSummary = "原始第${secondOriginalWeek}周 · ${formatDateRange(secondOriginalWeek, 1, 7)}",
        firstEndWeekday = firstEndWeekday,
        onFirstEndWeekdayChange = { firstEndWeekday = it.coerceIn(1, 6) },
        onSecondStartWeekdayChange = { firstEndWeekday = (it - 1).coerceIn(1, 6) },
        onPickFirstWeek = { openOriginalWeekPicker(1) },
        onPickSecondWeek = { openOriginalWeekPicker(2) },
        previewLines = buildList {
            if (firstPartStartDate != null && firstPartEndDate != null) {
                add("上半段：${formatTeachingDate(firstPartStartDate)}–${formatTeachingDate(firstPartEndDate)}")
            }
            if (secondPartStartDate != null && secondPartEndDate != null) {
                add("下半段：${formatTeachingDate(secondPartStartDate)}–${formatTeachingDate(secondPartEndDate)}")
            }
            if (currentFirstPartWeek != null && currentSecondPartWeek != null) {
                fun label(position: com.haooz.chedule.data.TeachingWeekPosition): String =
                    if (position.isReorganizationPause) "暂停（无常规教学周位置）" else "第${position.week}周"
                add("当前教学周：上半段${label(currentFirstPartWeek)}，下半段${label(currentSecondPartWeek)}")
            }
            if (savedFirstPartWeek != null && savedSecondPartWeek != null) {
                add("保存后：两段均归入教学第${savedFirstPartWeek}周")
            }
            if (pauseStartDate != null && pauseEndDate != null && !pauseEndDate.isBefore(pauseStartDate)) {
                add("暂停日期：${formatTeachingDate(pauseStartDate)}–${formatTeachingDate(pauseEndDate)}（常规课程暂停）")
            }
            add("此后教学周将重新分配，顺延${(secondOriginalWeek - firstOriginalWeek).coerceAtLeast(0)}周")
            draftValidationError?.let { add("无法保存：$it") }
        },
        validationError = draftValidationError,
        onDelete = { showTeachingWeekDeleteConfirm = true },
        onDismiss = { showTeachingWeekRuleDialog = false },
        onSave = { saveTeachingWeekRule() },
    )

    TeachingWeekNumberPickerDialog(
        show = showTeachingWeekPicker,
        value = pendingOriginalWeek,
        maxWeek = weekPickerMax,
        dateRangeSummary = weekPickerDateSummary,
        onValueChange = { pendingOriginalWeek = it },
        onDismiss = { showTeachingWeekPicker = false },
        onConfirm = {
            if (editingWeekPickerHalf == 1) firstOriginalWeek = pendingOriginalWeek
            else secondOriginalWeek = pendingOriginalWeek
            showTeachingWeekPicker = false
        },
    )

    OverlayDialog(
        title = "删除教学周重组规则",
        summary = "删除后，受影响的后续教学周会重新计算。确定删除这条规则吗？",
        show = showTeachingWeekDeleteConfirm,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showTeachingWeekDeleteConfirm = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                "取消",
                { showTeachingWeekDeleteConfirm = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                "删除",
                { deleteTeachingWeekRule() },
                textColor = Color(0xFFF44336),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun formatTeachingDate(date: LocalDate): String =
    "${date.year}/${date.monthValue.toString().padStart(2, '0')}/${date.dayOfMonth.toString().padStart(2, '0')}"

@Composable
private fun TeachingWeekRuleEditDialog(
    show: Boolean,
    isEditing: Boolean,
    firstWeek: Int,
    firstWeekSummary: String,
    secondWeek: Int,
    secondWeekSummary: String,
    firstEndWeekday: Int,
    onFirstEndWeekdayChange: (Int) -> Unit,
    onSecondStartWeekdayChange: (Int) -> Unit,
    onPickFirstWeek: () -> Unit,
    onPickSecondWeek: () -> Unit,
    previewLines: List<String>,
    validationError: String?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val sheetBackgroundColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF2F2F2)
    val startAction: @Composable () -> Unit = {
        val material = LocalSheetTopBarMaterial.current
        LiquidTopBarButton(
            onClick = {
                onDismiss()
            },
            backdrop = LocalSheetContentBackdrop.current!!,
            icon = MiuixIcons.Normal.Close,
            contentDescription = "取消",
            modifier = Modifier.padding(start = if (isTablet) 16.dp else 18.dp),
            iconSize = 24.dp,
            backdropAlpha = material.backdropAlpha,
            shadowAlpha = material.shadowAlpha,
        )
    }
    val endAction: @Composable () -> Unit = {
        val material = LocalSheetTopBarMaterial.current
        LiquidTopBarButton(
            onClick = {
                if (validationError == null) {
                    onSave()
                }
            },
            backdrop = LocalSheetContentBackdrop.current!!,
            icon = MiuixIcons.Ok,
            contentDescription = if (validationError == null) "保存" else "无法保存",
            modifier = Modifier.padding(end = if (isTablet) 16.dp else 18.dp),
            iconSize = 25.dp,
            iconTint = if (validationError == null) Color.Unspecified
                else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f),
            enabled = validationError == null,
            backdropAlpha = material.backdropAlpha,
            shadowAlpha = material.shadowAlpha,
        )
    }
    val sheetContent: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(
                    color = if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF),
                    contentColor = MiuixTheme.colorScheme.onSurface,
                ),
            ) {
                Column {
                    ArrowPreference(
                        title = "上半段周次",
                        summary = firstWeekSummary,
                        onClick = onPickFirstWeek,
                    )
                    TeachingWeekdayRangeSelector(
                        title = "上半段星期",
                        selectedStart = 1,
                        selectedEnd = firstEndWeekday,
                        selectableDays = 1..6,
                        onDayClick = onFirstEndWeekdayChange,
                    )
                }
            }
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(
                    color = if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF),
                    contentColor = MiuixTheme.colorScheme.onSurface,
                ),
            ) {
                Column {
                    ArrowPreference(
                        title = "下半段周次",
                        summary = secondWeekSummary,
                        onClick = onPickSecondWeek,
                    )
                    TeachingWeekdayRangeSelector(
                        title = "下半段星期",
                        selectedStart = firstEndWeekday + 1,
                        selectedEnd = 7,
                        selectableDays = 2..7,
                        onDayClick = onSecondStartWeekdayChange,
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                previewLines.forEach { line ->
                    Text(
                        text = line,
                        fontSize = 13.sp,
                        color = if (line.startsWith("无法保存：")) Color(0xFFF44336)
                            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            if (isEditing) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        color = if (isDark) Color.White.copy(alpha = 0.1f)
                        else Color.Black.copy(alpha = 0.06f),
                    ),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFFF44336),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "删除",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF44336),
                    )
                }
            }
        }
    }

    if (isTablet) {
        BlurBottomSheetTablet(
            show = show,
            title = if (isEditing) "编辑教学周重组规则" else "添加教学周重组规则",
            dimBackground = true,
            enableContentHeightSnap = true,
            sheetBackgroundColor = sheetBackgroundColor,
            sheetBackgroundAlpha = 1f,
            liquidGlassBackdrop = null,
            onDismissRequest = onDismiss,
            startAction = startAction,
            endAction = endAction,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (LocalBlurBottomSheetContentExpanded.current) Modifier.fillMaxHeight()
                        else Modifier,
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(modifier = Modifier.height(56.dp))
                sheetContent()
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    } else {
        BlurBottomSheet(
            show = show,
            title = if (isEditing) "编辑教学周重组规则" else "添加教学周重组规则",
            dimBackground = true,
            enableContentHeightSnap = true,
            sheetBackgroundColor = sheetBackgroundColor,
            sheetBackgroundAlpha = 1f,
            liquidGlassBackdrop = null,
            sheetOffsetDp = statusBarsPadding + 5.dp,
            onDismissRequest = onDismiss,
            startAction = startAction,
            endAction = endAction,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (LocalBlurBottomSheetContentExpanded.current) Modifier.fillMaxHeight()
                        else Modifier,
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(modifier = Modifier.height(58.dp))
                sheetContent()
                Spacer(modifier = Modifier.height(statusBarsPadding + 65.dp))
            }
        }
    }
}

@Composable
private fun TeachingWeekdayRangeSelector(
    title: String,
    selectedStart: Int,
    selectedEnd: Int,
    selectableDays: IntRange,
    onDayClick: (Int) -> Unit,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val daySpacing = 6.dp
    val daySpacingPx = with(LocalDensity.current) { daySpacing.toPx() }
    val currentOnDayClick = rememberUpdatedState(onDayClick)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "连续范围",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(selectableDays, daySpacingPx) {
                    fun selectDayAt(x: Float) {
                        teachingWeekdayAtDragPosition(
                            xPx = x,
                            rowWidthPx = size.width.toFloat(),
                            spacingPx = daySpacingPx,
                            selectableDays = selectableDays,
                        )?.let(currentOnDayClick.value)
                    }

                    detectHorizontalDragGestures(
                        onDragStart = { selectDayAt(it.x) },
                        onHorizontalDrag = { change, _ -> selectDayAt(change.position.x) },
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(daySpacing),
        ) {
            for (day in 1..7) {
                val enabled = day in selectableDays
                val selected = day in selectedStart..selectedEnd
                val backgroundColor = if (selected) MiuixTheme.colorScheme.primary
                    else if (isDark) Color(0xFF363636) else Color(0xFFF2F2F2)
                val textColor = when {
                    selected -> Color.White
                    enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .squircleClip(10.dp)
                        .background(backgroundColor)
                        .clickable(
                            enabled = enabled,
                            interactionSource = null,
                            indication = null,
                            onClick = { onDayClick(day) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = WEEKDAYS[day - 1].removePrefix("星期"),
                        fontSize = 14.sp,
                        color = textColor,
                    )
                }
            }
        }
    }
}

internal fun teachingWeekdayAtDragPosition(
    xPx: Float,
    rowWidthPx: Float,
    spacingPx: Float,
    selectableDays: IntRange,
): Int? {
    if (rowWidthPx <= 0f || spacingPx < 0f || xPx < 0f || xPx >= rowWidthPx) return null

    val cellWidthPx = (rowWidthPx - spacingPx * 6) / 7f
    if (cellWidthPx <= 0f) return null

    val cellStepPx = cellWidthPx + spacingPx
    val day = floor((xPx + spacingPx / 2f) / cellStepPx).toInt() + 1
    return day.takeIf { it in 1..7 && it in selectableDays }
}

@Composable
private fun TeachingWeekNumberPickerDialog(
    show: Boolean,
    value: Int,
    maxWeek: Int,
    dateRangeSummary: String,
    onValueChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    OverlayDialog(
        title = "选择原始周",
        summary = null,
        show = show,
        liquidGlassBackdrop = null,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NumberPicker(
                value = value.coerceIn(1, maxWeek),
                onValueChange = onValueChange,
                range = 1..maxWeek,
                visibleItemCount = 3,
                itemHeight = 50.dp,
                textStyle = pickerTextStyle(),
                label = { "原始第${it}周" },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = dateRangeSummary,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton("取消", onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    "确定",
                    {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onConfirm()
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
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
private fun SectionRangeDialog(
    show: Boolean,
    liquidGlassBackdrop: Backdrop?,
    sectionCount: Int,
    startSection: Int,
    endSection: Int,
    onStartSectionChange: (Int) -> Unit,
    onEndSectionChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    OverlayDialog(
        title = "选择上课节次",
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "开始",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    NumberPicker(
                        value = startSection.coerceIn(1, sectionCount.coerceAtLeast(1)),
                        onValueChange = onStartSectionChange,
                        range = 1..sectionCount.coerceAtLeast(1),
                        visibleItemCount = 3,
                        itemHeight = 50.dp,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "结束",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    NumberPicker(
                        value = endSection.coerceIn(
                            startSection.coerceIn(1, sectionCount.coerceAtLeast(1)),
                            sectionCount.coerceAtLeast(1),
                        ),
                        onValueChange = onEndSectionChange,
                        range = startSection.coerceIn(1, sectionCount.coerceAtLeast(1))..
                            sectionCount.coerceAtLeast(1),
                        visibleItemCount = 3,
                        itemHeight = 50.dp,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "确定",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSave()
                    },
                    enabled = sectionCount > 0,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
    return CourseRepository.getInstance(context).dateForTeachingWeekDay(week, weekday)
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
