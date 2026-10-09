package com.haooz.chedule.ui.activities

import com.haooz.chedule.data.HolidayEntry

import com.haooz.chedule.data.lengthOfMonth
import com.haooz.chedule.data.minusDays
import com.haooz.chedule.data.plusDays
import com.haooz.chedule.data.todayLocalDate
import kotlinx.datetime.isoDayNumber

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import com.haooz.chedule.ui.utils.overScrollVertical
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.overlay.BackdropHolder
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
import kotlinx.datetime.LocalDate
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
    entries: List<HolidayEntry>,
    onYearChange: (Int) -> Unit,
    reload: () -> Unit,
    onTeachingWeekReorganizationsChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val holidayDataRevision by HolidayManager.dataRevision.collectAsState()
    val repository = remember(context) { CourseRepository.getInstance() }
    val currentScheduleId = repository.getCurrentScheduleId()
    val currentTotalWeeks = repository.getTotalWeeks(currentScheduleId)
    val semesterStartDate = remember(currentScheduleId) {
        runCatching {
            LocalDate.parse(repository.getClassStartTime(currentScheduleId).replace("/", "-"))
        }.getOrElse { todayLocalDate() }
    }
    var teachingWeekReorganizations by remember(currentScheduleId) {
        mutableStateOf(repository.getTeachingWeekReorganizations(currentScheduleId))
    }
    val configuredSectionCount = repository.getMorningSections(currentScheduleId) +
        repository.getAfternoonSections(currentScheduleId) +
        repository.getEveningSections(currentScheduleId)
    val endCourseExclusion = remember(context, holidayDataRevision) {
        HolidayManager.loadEndCourseExclusion()
    }
    val beforeCourseExclusion = remember(context, holidayDataRevision) {
        HolidayManager.loadBeforeCourseExclusion()
    }
    var showSectionRangeDialog by remember { mutableStateOf(false) }
    var editingBeforeCourseExclusion by remember { mutableStateOf(false) }
    var pendingStartSection by remember { mutableIntStateOf(1) }
    var pendingEndSection by remember { mutableIntStateOf(1) }
    // 编辑弹窗
    var showDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var dialogType by remember { mutableIntStateOf(HolidayManager.TYPE_HOLIDAY) }
    var editingEntry by remember { mutableStateOf<HolidayEntry?>(null) }
    var editingEntryStorageYear by remember { mutableIntStateOf(year) }
    var name by remember { mutableStateOf("") }
    var startYear by remember { mutableIntStateOf(year) }
    var startMonth by remember { mutableIntStateOf(1) }
    var startDay by remember { mutableIntStateOf(1) }
    var endYear by remember { mutableIntStateOf(year) }
    var endMonth by remember { mutableIntStateOf(1) }
    var endDay by remember { mutableIntStateOf(1) }
    // UI 依旧显示「第几周 + 星期几」，但这只是**当前课表**下的投影：
    // 存档的是绝对日期（followDate），换课表时按新课表的学期开始时间重算 → 周次自动变。
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
    // 周次选择器是从教学周弹窗里点开的二级弹窗，在弹窗作用域之外读不到
    // LocalSheetContentBackdrop，需要这样把弹窗内容 backdrop 拿出来给它做模糊采样。
    val teachingWeekSheetBackdropHolder = remember { BackdropHolder() }

    // 根据开始日期计算其对应课表的默认周次
    fun weekOfDate(year: Int, month: Int, day: Int): String {
        return try {
            val date = LocalDate(year, month, day)
            TeachingWeekReorganization.mapDate(
                semesterStartDate,
                date,
                teachingWeekReorganizations,
            ).week.coerceIn(1L, currentTotalWeeks.toLong()).toInt()
        } catch (_: Exception) {
            1
        }.toString()
    }

    /** 当前课表下「第 week 周 星期 weekday」对应的那一天 —— 把 UI 上的周次落回绝对日期 */
    fun dateOfTeachingWeek(week: Int, weekday: Int): LocalDate? =
        runCatching {
            TeachingWeekReorganization.dateForPosition(
                semesterStartDate,
                week,
                weekday,
                teachingWeekReorganizations,
            )
        }.getOrNull()

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

    fun startEditing(entry: HolidayEntry) {
        val start = runCatching { LocalDate.parse(entry.date) }.getOrNull()
            ?: LocalDate(year, 1, 1)
        val end = runCatching { LocalDate.parse(entry.endDate.ifBlank { entry.date }) }.getOrNull()
            ?: start
        dialogType = entry.type
        editingEntry = entry
        editingEntryStorageYear = year
        name = entry.name
        startYear = start.year
        startMonth = start.monthNumber
        startDay = start.dayOfMonth
        endYear = end.year
        endMonth = end.monthNumber
        endDay = end.dayOfMonth
        // 调休日还没配映射时，按「补班日倒序、从假期最后一个工作日往前拿」预填一个建议值。
        // 只填进弹窗不落库：用户点保存才生效，没配过的条目库里保持 -1（不会擅自改课表）。
        val suggestion = if (entry.type == HolidayManager.TYPE_WORKSWAP) {
            HolidayManager.suggestWorkSwapFollowTargets(entries)
                .firstOrNull { it.date == entry.date && it.type == HolidayManager.TYPE_WORKSWAP }
                ?.takeIf { it.followLocalDate() != null }
        } else null
        // 已存的绝对日期 → 老数据的 (周次,星期) → 建议值，统一按**当前课表**投影成 (周次,星期)
        val initialFollow = entry.followLocalDate()
            ?: followDate(context, entry.followWeek.toString(), entry.followWeekday.toString())
            ?: suggestion?.followLocalDate()
        if (initialFollow != null) {
            val position = TeachingWeekReorganization.mapDate(
                semesterStartDate, initialFollow, teachingWeekReorganizations
            )
            followWeek = position.week.coerceIn(1L, currentTotalWeeks.toLong()).toString()
            followWeekday = (position.weekday ?: initialFollow.dayOfWeek.isoDayNumber).toString()
        } else {
            followWeek = weekOfDate(startYear, startMonth, startDay)
            followWeekday = "1"
        }
        followWeekManuallySelected = entry.followLocalDate() != null
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
        // UI 上的「第 X 周 星期 Y」只是当前课表的投影，落库一律转成绝对日期
        val followDateValue = if (isHoliday) null else dateOfTeachingWeek(week, weekday)
        if (!isHoliday && followDateValue == null) {
            Toast.makeText(context, "该周次在当前课表下没有对应日期", Toast.LENGTH_SHORT).show()
            return
        }
        // 按开始日期所属年份落库，避免 UI 选中年与日期年不一致时 workSwap 查不到
        val entryYear = runCatching { LocalDate.parse(startDate).year }.getOrDefault(year)
        val oldYear = editingEntry?.let { editingEntryStorageYear }
        val affectedYears = buildSet {
            add(entryYear)
            oldYear?.let(::add)
        }
        val newEntry = HolidayEntry(
            date = startDate,
            endDate = endDate,
            name = name.ifBlank { if (isHoliday) "节假日" else "调休工作日" },
            type = dialogType,
            // 存绝对日期；旧字段 followWeek/followWeekday 一律不再写入
            followDate = followDateValue?.toString() ?: "",
            custom = true,
        )
        val saved = HolidayManager.updateEntries(affectedYears) { current ->
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
            HolidayManager.updateEntries(setOf(oldYear)) { current ->
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
    val savedFirstPartWeek = if (draftValidationError == null) firstPartStartDate?.let {
        TeachingWeekReorganization.mapDate(semesterStartDate, it, rulesAfterDraft).week
    } else null
    val savedSecondPartWeek = if (draftValidationError == null) secondPartStartDate?.let {
        TeachingWeekReorganization.mapDate(semesterStartDate, it, rulesAfterDraft).week
    } else null
    val teachingWeekRulePreview = buildTeachingWeekRulePreview(
        firstPartStartDate = firstPartStartDate,
        firstPartEndDate = firstPartEndDate,
        pauseStartDate = pauseStartDate,
        pauseEndDate = pauseEndDate,
        secondPartStartDate = secondPartStartDate,
        secondPartEndDate = secondPartEndDate,
        savedFirstPartWeek = savedFirstPartWeek,
        savedSecondPartWeek = savedSecondPartWeek,
        shiftedWeeks = (secondOriginalWeek - firstOriginalWeek).coerceAtLeast(0),
    )
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
                padding.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight +
                    (if (isTablet) 24.dp else 12.dp),
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
                    text = "节假日课程排除",
                    description =
                        "• 假期前一天或最后一天的几节课不用上课\n" +
                        "• 可以将节假日前后的某几节课进行排除\n" +
                        "「假期前一天即使是调休上班日也会照常应用该规则」",
                    liquidGlassBackdrop = liquidGlassBackdrop,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SwitchPreference(
                        title = "假期前日课程排除",
                        checked = beforeCourseExclusion.enabled,
                        onCheckedChange = { enabled ->
                            val updated = beforeCourseExclusion.copy(enabled = enabled)
                            if (HolidayManager.saveBeforeCourseExclusion(updated)) {
                                CourseReminderHelper.onHolidayDataChanged(context)
                            }
                        },
                    )
                    ArrowPreference(
                        title = "节次范围",
                        summary = "第${beforeCourseExclusion.startSection}–${beforeCourseExclusion.endSection}节",
                        onClick = {
                            if (configuredSectionCount <= 0) {
                                Toast.makeText(context, "当前课表尚未配置节次", Toast.LENGTH_SHORT).show()
                            } else {
                                editingBeforeCourseExclusion = true
                                val maxSection = configuredSectionCount
                                pendingStartSection = beforeCourseExclusion.startSection
                                    .coerceIn(1, maxSection)
                                pendingEndSection = beforeCourseExclusion.endSection
                                    .coerceIn(pendingStartSection, maxSection)
                                showSectionRangeDialog = true
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.fillMaxWidth().height(12.dp))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SwitchPreference(
                        title = "假期末天课程排除",
                        checked = endCourseExclusion.enabled,
                        onCheckedChange = { enabled ->
                            val updated = endCourseExclusion.copy(enabled = enabled)
                            if (HolidayManager.saveEndCourseExclusion(updated)) {
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
                                editingBeforeCourseExclusion = false
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
            val saved = rangeFitsCurrentSchedule && if (editingBeforeCourseExclusion) {
                HolidayManager.saveBeforeCourseExclusion(
                    beforeCourseExclusion.copy(
                        startSection = pendingStartSection,
                        endSection = pendingEndSection,
                    ),
                )
            } else {
                HolidayManager.saveEndCourseExclusion(
                    endCourseExclusion.copy(
                        startSection = pendingStartSection,
                        endSection = pendingEndSection,
                    ),
                )
            }
            if (saved) {
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
        preview = teachingWeekRulePreview,
        validationError = draftValidationError,
        onDelete = { showTeachingWeekDeleteConfirm = true },
        onDismiss = { showTeachingWeekRuleDialog = false },
        onSave = { saveTeachingWeekRule() },
        onSheetContentBackdropCreated = { teachingWeekSheetBackdropHolder.value = it },
    )

    TeachingWeekNumberPickerDialog(
        show = showTeachingWeekPicker,
        value = pendingOriginalWeek,
        maxWeek = weekPickerMax,
        dateRangeSummary = weekPickerDateSummary,
        liquidGlassBackdrop = teachingWeekSheetBackdropHolder.value ?: liquidGlassBackdrop,
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
    "${date.year}/${date.monthNumber.toString().padStart(2, '0')}/${date.dayOfMonth.toString().padStart(2, '0')}"

internal data class TeachingWeekRulePreview(
    val firstPartDateRange: String?,
    val pauseDateRange: String?,
    val secondPartDateRange: String?,
    val savedFirstPartWeek: String?,
    val savedSecondPartWeek: String?,
    val shiftedWeeks: Int,
)

internal fun buildTeachingWeekRulePreview(
    firstPartStartDate: LocalDate?,
    firstPartEndDate: LocalDate?,
    pauseStartDate: LocalDate?,
    pauseEndDate: LocalDate?,
    secondPartStartDate: LocalDate?,
    secondPartEndDate: LocalDate?,
    savedFirstPartWeek: Long?,
    savedSecondPartWeek: Long?,
    shiftedWeeks: Int,
): TeachingWeekRulePreview = TeachingWeekRulePreview(
    firstPartDateRange = formatTeachingDateRange(firstPartStartDate, firstPartEndDate),
    pauseDateRange = formatTeachingDateRange(pauseStartDate, pauseEndDate),
    secondPartDateRange = formatTeachingDateRange(secondPartStartDate, secondPartEndDate),
    savedFirstPartWeek = savedFirstPartWeek?.let { "第${it}周" },
    savedSecondPartWeek = savedSecondPartWeek?.let { "第${it}周" },
    shiftedWeeks = shiftedWeeks.coerceAtLeast(0),
)

private fun formatTeachingDateRange(startDate: LocalDate?, endDate: LocalDate?): String? {
    if (startDate == null || endDate == null || endDate < startDate) return null
    val endLabel = if (startDate.year == endDate.year) {
        "${endDate.monthNumber.toString().padStart(2, '0')}/${endDate.dayOfMonth.toString().padStart(2, '0')}"
    } else {
        formatTeachingDate(endDate)
    }
    return "${formatTeachingDate(startDate)}–$endLabel"
}

internal data class TeachingWeekRuleOutcomeLabels(
    val mergeLabel: String?,
    val shiftLabel: String,
) {
    val summaryLabel: String
        get() = listOfNotNull(mergeLabel, shiftLabel).joinToString(" · ")
}

internal fun teachingWeekRuleOutcomeLabels(
    savedFirstPartWeek: String?,
    savedSecondPartWeek: String?,
    shiftedWeeks: Int,
): TeachingWeekRuleOutcomeLabels {
    val mergedWeek = savedFirstPartWeek?.takeIf { it == savedSecondPartWeek }
    val shiftLabel = if (shiftedWeeks > 0) {
        "后续顺延 +${shiftedWeeks}周"
    } else {
        "后续不变"
    }
    return TeachingWeekRuleOutcomeLabels(
        mergeLabel = mergedWeek?.let { "合并为$it" },
        shiftLabel = shiftLabel,
    )
}

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
    preview: TeachingWeekRulePreview,
    validationError: String?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onSheetContentBackdropCreated: ((Backdrop?) -> Unit)? = null,
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
            TeachingWeekRulePreviewCard(
                firstWeek = firstWeek,
                secondWeek = secondWeek,
                preview = preview,
                validationError = validationError,
                isDark = isDark,
            )
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
            onSheetContentBackdropCreated = onSheetContentBackdropCreated,
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
                Spacer(modifier = Modifier.height(4.dp))
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
            onSheetContentBackdropCreated = onSheetContentBackdropCreated,
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
                Spacer(modifier = Modifier.height(statusBarsPadding + 60.dp))
            }
        }
    }
}

@Composable
private fun TeachingWeekRulePreviewCard(
    firstWeek: Int,
    secondWeek: Int,
    preview: TeachingWeekRulePreview,
    validationError: String?,
    isDark: Boolean,
) {
    val primaryColor = MiuixTheme.colorScheme.primary
    val dividerColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)
    val outcomeLabels = teachingWeekRuleOutcomeLabels(
        savedFirstPartWeek = preview.savedFirstPartWeek,
        savedSecondPartWeek = preview.savedSecondPartWeek,
        shiftedWeeks = preview.shiftedWeeks,
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF),
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "重组预览",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = outcomeLabels.summaryLabel,
                    modifier = Modifier
                        .clip(ContinuousRoundedRectangle(14.dp))
                        .background(primaryColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryColor,
                    maxLines = 1,
                )
            }

            Column {
                TeachingWeekRuleTimelineItem(
                    title = "上半段 · 原第${firstWeek}周",
                    dateRange = preview.firstPartDateRange ?: "日期不可用",
                    markerColor = primaryColor,
                    connectorColor = dividerColor,
                    hasConnector = true,
                )
                preview.pauseDateRange?.let { dateRange ->
                    TeachingWeekRuleTimelineItem(
                        title = "课程暂停",
                        dateRange = dateRange,
                        markerColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
                        connectorColor = dividerColor,
                        hasConnector = true,
                    )
                }
                TeachingWeekRuleTimelineItem(
                    title = "下半段 · 原第${secondWeek}周",
                    dateRange = preview.secondPartDateRange ?: "日期不可用",
                    markerColor = MiuixTheme.colorScheme.secondaryVariant,
                    connectorColor = dividerColor,
                    hasConnector = false,
                )
            }

            validationError?.let { error ->
                Text(
                    text = "无法保存：$error",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ContinuousRoundedRectangle(12.dp))
                        .background(Color(0xFFF44336).copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    fontSize = 13.sp,
                    color = Color(0xFFF44336),
                )
            }
        }
    }
}

@Composable
private fun TeachingWeekRuleTimelineItem(
    title: String,
    dateRange: String,
    markerColor: Color,
    connectorColor: Color,
    hasConnector: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(24.dp),
        ) {
            if (hasConnector) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 12.dp)
                        .width(1.dp)
                        .height(16.dp)
                        .background(connectorColor),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(markerColor),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = dateRange,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
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
    val haptics = LocalHapticFeedback.current
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
                    val hapticTracker = TeachingWeekdayDragHapticTracker()

                    fun weekdayAt(x: Float): Int? = teachingWeekdayAtDragPosition(
                            xPx = x,
                            rowWidthPx = size.width.toFloat(),
                            spacingPx = daySpacingPx,
                            selectableDays = 1..7,
                        )

                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val day = weekdayAt(offset.x)
                            hapticTracker.begin(day)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            day?.takeIf { it in selectableDays }?.let(currentOnDayClick.value)
                        },
                        onHorizontalDrag = { change, _ ->
                            val day = weekdayAt(change.position.x)
                            if (day != null && hapticTracker.enter(day)) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                day.takeIf { it in selectableDays }?.let(currentOnDayClick.value)
                            }
                        },
                        onDragEnd = { hapticTracker.end() },
                        onDragCancel = { hapticTracker.end() },
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

internal class TeachingWeekdayDragHapticTracker {
    private var currentDay: Int? = null

    fun begin(day: Int?) {
        currentDay = day
    }

    fun enter(day: Int): Boolean {
        if (day == currentDay) return false
        currentDay = day
        return true
    }

    fun end() {
        currentDay = null
    }
}

@Composable
private fun TeachingWeekNumberPickerDialog(
    show: Boolean,
    value: Int,
    maxWeek: Int,
    dateRangeSummary: String,
    liquidGlassBackdrop: Backdrop?,
    onValueChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    OverlayDialog(
        title = "选择原始周",
        summary = null,
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
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
 * 基于默认下拉位置再做微调。
 *
 * @param base 基础 provider（[liquidDropdownPositionProvider]，已按当前 density 归一）
 * @param offsetYPx 沿展开方向的额外偏移：向下展开时为正、向上展开时为负
 * @param offsetXPx 水平额外偏移：右为正、左为负
 * 两处都会再过一次窗口边界 clamp，不会把弹窗顶出屏幕。
 */
@Composable
private fun expandDirectionOffsetProvider(
    base: PopupPositionProvider,
    offsetYPx: Int,
    offsetXPx: Int = 0,
): PopupPositionProvider = remember(base, offsetYPx, offsetXPx) {
    object : PopupPositionProvider {
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
                result.showBelow -> offsetYPx
                result.showAbove -> -offsetYPx
                else -> 0
            }
            val minX = windowBounds.left + popupMargin.left
            val maxX = (windowBounds.right - popupContentSize.width - popupMargin.right).coerceAtLeast(minX)
            val clampedX = (result.offset.x + offsetXPx).coerceIn(minX, maxX)
            val clampedY = (result.offset.y + deltaY).coerceIn(
                windowBounds.top + popupMargin.top,
                windowBounds.bottom - popupContentSize.height - popupMargin.bottom,
            )
            return PopupPositionResult(
                IntOffset(clampedX, clampedY),
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
        modifier = Modifier.fillMaxWidth(),
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

// ── 叹号说明弹窗的位置微调 ──────────────────────────────
// 直接改这两行即可，不用碰 provider。
//   Y：沿展开方向的偏移，向下展开为正、向上展开自动取反
//   X：水平偏移，右为正、左为负
// 两者都会过一遍窗口边界 clamp，挪太远顶到屏幕边缘就停住，不会跑出去。
private val InfoPopupOffsetY = 13.dp
private val InfoPopupOffsetX = (-15).dp

@Composable
private fun InfoDropdown(
    description: String,
    liquidGlassBackdrop: Backdrop?,
) {
    var expanded by remember { mutableStateOf(false) }
    // 弹窗展开/退场期间硬切隐藏图标（不做过渡），与课表页「更多」菜单同一套语言。
    var triggerVisible by remember { mutableStateOf(true) }
    var triggerSize by remember { mutableStateOf(IntSize.Zero) }
    val coroutineScope = rememberCoroutineScope()
    val infoColor = MiuixTheme.colorScheme.primary
    val density = LocalDensity.current
    val positionProvider = expandDirectionOffsetProvider(
        base = liquidDropdownPositionProvider(),
        offsetYPx = with(density) { InfoPopupOffsetY.roundToPx() },
        offsetXPx = with(density) { InfoPopupOffsetX.roundToPx() },
    )

    // 展开后等两帧再藏：第一帧让弹窗画出收起态，第二帧确保已上屏，否则中间会空一帧。
    LaunchedEffect(expanded) {
        if (expanded) {
            repeat(2) { withFrameNanos { } }
            triggerVisible = false
        }
    }

    // 弹窗里那份图标：没有那 8dp 尾部内边距（那是给列表对齐用的，与弹窗无关）
    val collapseIcon: @Composable () -> Unit = {
        Image(
            imageVector = MiuixIcons.Info,
            contentDescription = null,
            colorFilter = ColorFilter.tint(infoColor),
            modifier = Modifier.size(20.dp),
        )
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
            modifier = Modifier
                // CenterEnd 会把图标贴到 36dp Box 的右边缘，而这个 Box 是 fillMaxWidth
                // Row 的末项、没有尾部 padding，结果图标右边缘与列表内容右边缘重合、
                // 看着是顶在边上。让出 8dp（其余行的内容都有内边距，视觉上才齐平）；
                // padding 套在 size 外层，36dp 的可点区域保持不变。
                .padding(end = 8.dp)
                .size(20.dp)
                .onSizeChanged { triggerSize = it }
                .graphicsLayer { alpha = if (triggerVisible) 1f else 0f },
        )
        OverlayListPopup(
            show = expanded,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { expanded = false },
            onDismissFinished = {
                // 压满三帧再显示：弹窗节点撤下后渲染还要一帧才真正不画
                coroutineScope.launch {
                    repeat(3) { withFrameNanos { } }
                    triggerVisible = true
                }
            },
            popupPositionProvider = positionProvider,
            liquidGlassBackdrop = liquidGlassBackdrop,
            // 弹窗从叹号原位长成面板，而不是从一个大圆里长出来
            collapseSize = triggerSize,
            collapseContent = collapseIcon,
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
    entries: List<HolidayEntry>,
    onEdit: (HolidayEntry) -> Unit,
) {
    Card(
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
    entry: HolidayEntry,
    onEdit: (HolidayEntry) -> Unit,
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
    return CourseRepository.getInstance().dateForTeachingWeekDay(week, weekday)
}

private fun entrySummary(entry: HolidayEntry): String {
    return if (entry.type == HolidayManager.TYPE_HOLIDAY) {
        val endSuffix = if (entry.endDate.isNotBlank()) {
            " 至 ${displayDate(entry.endDate)}"
        } else {
            ""
        }
        "${displayDate(entry.date)}$endSuffix"
    } else {
        // 存的是绝对日期就显示日期（周次是相对课表的，写死在列表里会误导）；
        // 只有尚未迁移的老数据才回退显示周次
        val mapping = when {
            entry.followLocalDate() != null -> {
                val date = entry.followLocalDate()!!
                "上 ${date.monthNumber}月${date.dayOfMonth}日 的课"
            }
            entry.followWeek > 0 && entry.followWeekday in 1..7 ->
                "第${entry.followWeek}周${WEEKDAYS[entry.followWeekday - 1]}"
            else -> "待配置补班课程"
        }
        "${displayDate(entry.date)} · $mapping"
    }
}

@Composable
private fun AddEntryCard(type: Int, onAdd: () -> Unit) {
    val isHoliday = type == HolidayManager.TYPE_HOLIDAY
    Card(
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
                text = if (isHoliday) "开始日期" else "调休日期",
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
                // 显示成日期，让用户能核对「补的是哪一天」；存档也是这个日期
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
                            "${date.year}/${date.monthNumber}/${date.dayOfMonth}",
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
                // 国家级数据只说「哪天补班」，不说「补哪天的课」，上面是推算出来的预填值，
                // 各校安排未必一致 —— 明确告诉用户：以学校通知为准，保存后才生效。
                Text(
                    "已按假期末尾推算预填，请以学校通知为准，保存后生效",
                    style = MiuixTheme.textStyles.body1.copy(
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
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
    val maxDay = LocalDate(year, month, 1).lengthOfMonth()
    val currentYear = remember { todayLocalDate().year }
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
                onDayChange(day.coerceAtMost(LocalDate(newYear, month, 1).lengthOfMonth()))
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
                onDayChange(day.coerceAtMost(LocalDate(year, newMonth, 1).lengthOfMonth()))
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
