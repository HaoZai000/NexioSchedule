package com.haooz.chedule.ui.activities

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.holiday.TeachingWeekReorganizationRule
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.util.lengthOfMonth
import com.haooz.chedule.data.util.minusDays
import com.haooz.chedule.data.util.plusDays
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.HolidayStr
import com.haooz.chedule.i18n.orDefaultName
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.components.text.miuix.Text as Text
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlin.math.floor
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
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
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
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
import com.kyant.capsule.continuousClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val YEAR_RANGE = 2024..2035

private fun monthLabel(value: Int): String = HolidayStr.monthN(value)
private fun dayLabel(value: Int): String = CommonStr.dateN(value)

// commonMain 没有 JVM 的 String.format（原来是 "%04d-%02d-%02d".format(...)），改用 padStart 手写零填充日期
private fun isoDate(year: Int, month: Int, day: Int): String =
    "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

@Composable
fun HolidaySettingsScreen(
    scrollBehavior: SharedScrollBehavior?,
    liquidGlassBackdrop: Backdrop?,
    year: Int,
    entries: List<HolidayEntry>,
    onYearChange: (Int) -> Unit,
    reload: () -> Unit,
    onTeachingWeekReorganizationsChanged: () -> Unit = {},
    // 节假日/调休/教学周数据变更后的平台副作用（重排提醒 + 勿扰 + 刷新小部件）。
    // 由 Android 宿主注入：CourseReminderHelper.onHolidayDataChanged(context)。
    // 不直接调 reminder 模块 —— 那是 Android 专有的（内部要 Context，桌面/iOS 无此概念）。
    onHolidayDataChanged: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val holidayDataRevision by HolidayManager.dataRevision.collectAsState()
    val repository = remember { CourseRepository.getInstance() }
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
    val endCourseExclusion = remember(holidayDataRevision) {
        HolidayManager.loadEndCourseExclusion()
    }
    val beforeCourseExclusion = remember(holidayDataRevision) {
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
        val first = originalWeekDate(week, startDay) ?: return HolidayStr.dateOutOfRange()
        val last = originalWeekDate(week, endDay) ?: return HolidayStr.dateOutOfRange()
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
            showToast(HolidayStr.noWeekToReorganize())
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
            showToast(error)
            return
        }
        if (!repository.setTeachingWeekReorganizations(
                rules = updated,
                scheduleId = currentScheduleId,
                allowExistingOutOfRangeRules = true,
                changedRule = proposed,
            )
        ) {
            showToast(HolidayStr.saveFailedUnchanged())
            return
        }
        teachingWeekReorganizations = updated
        showTeachingWeekRuleDialog = false
        showTeachingWeekDeleteConfirm = false
        onTeachingWeekReorganizationsChanged()
        onHolidayDataChanged()
    }

    fun deleteTeachingWeekRule() {
        val rule = teachingWeekReorganizations.getOrNull(editingTeachingWeekRuleIndex) ?: return
        if (!repository.removeTeachingWeekReorganization(rule, currentScheduleId)) {
            showToast(HolidayStr.deleteFailedUnchanged())
            return
        }
        val updated = teachingWeekReorganizations.toMutableList().apply {
            removeAt(editingTeachingWeekRuleIndex)
        }
        teachingWeekReorganizations = updated
        showTeachingWeekDeleteConfirm = false
        showTeachingWeekRuleDialog = false
        onTeachingWeekReorganizationsChanged()
        onHolidayDataChanged()
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
        name = entry.name.orDefaultName(
            if (entry.type == HolidayManager.TYPE_WORKSWAP) HolidayStr.defaultWorkswapName
            else HolidayStr.defaultHolidayName
        )
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
            ?: followDate(entry.followWeek.toString(), entry.followWeekday.toString())
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
        val startDate = isoDate(startYear, startMonth, startDay)
        if (runCatching { LocalDate.parse(startDate) }.isFailure) {
            showToast(HolidayStr.invalidDate())
            return
        }
        val endDate = if (isHoliday) {
            isoDate(endYear, endMonth, endDay)
        } else {
            ""
        }
        if (isHoliday && endDate < startDate) {
            showToast(HolidayStr.endBeforeStart())
            return
        }
        val week = followWeek.toIntOrNull()?.takeIf { it > 0 } ?: -1
        val weekday = followWeekday.toIntOrNull()?.takeIf { it in 1..7 } ?: -1
        if (!isHoliday && (week !in 1..currentTotalWeeks || weekday !in 1..7)) {
            showToast(HolidayStr.selectValidFollow())
            return
        }
        // UI 上的「第 X 周 星期 Y」只是当前课表的投影，落库一律转成绝对日期
        val followDateValue = if (isHoliday) null else dateOfTeachingWeek(week, weekday)
        if (!isHoliday && followDateValue == null) {
            showToast(HolidayStr.followWeekNoDate())
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
            name = name.ifBlank { if (isHoliday) HolidayStr.defaultHolidayName() else HolidayStr.defaultWorkswapName() },
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
            showToast(HolidayStr.dataReadFailedSave(), longDuration = true)
            return
        }
        reload()
        onHolidayDataChanged()
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
            showToast(HolidayStr.dataReadFailedDelete(), longDuration = true)
            return
        }
        reload()
        onHolidayDataChanged()
        showDeleteConfirm = false
        showDialog = false
        editingEntry = null
    }

    val listState = rememberLazyListState()
    val holidayEntries = entries.filter { it.type == HolidayManager.TYPE_HOLIDAY }
    val workswapEntries = entries.filter { it.type == HolidayManager.TYPE_WORKSWAP }
    val isTablet = isTabletWidth()
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
                    text = HolidayStr.holidaysTitle(),
                    description = HolidayStr.holidaysDesc(),
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
                    text = HolidayStr.workswapTitle(),
                    description = HolidayStr.workswapDesc(),
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
                    text = HolidayStr.reorgTitle(),
                    description = HolidayStr.reorgDesc(),
                    liquidGlassBackdrop = liquidGlassBackdrop,
                )
                CardExpand(visible = teachingWeekReorganizations.isNotEmpty()) {
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
                                    title = HolidayStr.reorgRuleN(index + 1),
                                    summary = HolidayStr.originalWeekN(rule.firstOriginalWeek) + " $firstRange + " +
                                        HolidayStr.originalWeekN(rule.secondOriginalWeek) + " $secondRange → " +
                                        (targetWeek?.let { HolidayStr.teachingWeekN(it) } ?: HolidayStr.dateOutOfRange()),
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
                        title = HolidayStr.addReorgRule(),
                        onClick = { startAddingTeachingWeekRule() },
                    )
                }
            }

            item {
                SectionTitleRow(
                    text = HolidayStr.exclusionTitle(),
                    description = HolidayStr.exclusionDesc(),
                    liquidGlassBackdrop = liquidGlassBackdrop,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SwitchPreference(
                        title = HolidayStr.beforeExclusionTitle(),
                        checked = beforeCourseExclusion.enabled,
                        onCheckedChange = { enabled ->
                            val updated = beforeCourseExclusion.copy(enabled = enabled)
                            if (HolidayManager.saveBeforeCourseExclusion(updated)) {
                                onHolidayDataChanged()
                            }
                        },
                    )
                    ArrowPreference(
                        title = HolidayStr.sectionRangeLabel(),
                        summary = HolidayStr.sectionRangeN(beforeCourseExclusion.startSection, beforeCourseExclusion.endSection),
                        onClick = {
                            if (configuredSectionCount <= 0) {
                                showToast(HolidayStr.noSectionsConfigured())
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
                        title = HolidayStr.endExclusionTitle(),
                        checked = endCourseExclusion.enabled,
                        onCheckedChange = { enabled ->
                            val updated = endCourseExclusion.copy(enabled = enabled)
                            if (HolidayManager.saveEndCourseExclusion(updated)) {
                                onHolidayDataChanged()
                            }
                        },
                    )
                    ArrowPreference(
                        title = HolidayStr.sectionRangeLabel(),
                        summary = HolidayStr.sectionRangeN(endCourseExclusion.startSection, endCourseExclusion.endSection),
                        onClick = {
                            if (configuredSectionCount <= 0) {
                                showToast(HolidayStr.noSectionsConfigured())
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
                HolidayStr.addHoliday()
            } else {
                HolidayStr.addWorkswap()
            }
        } else {
            if (dialogType == HolidayManager.TYPE_HOLIDAY) {
                HolidayStr.editHoliday()
            } else {
                HolidayStr.editWorkswap()
            }
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
        canDelete = editingEntry != null,
        onDeleteClick = { showDeleteConfirm = true },
        onDismiss = { showDialog = false },
        onSave = { saveEntry() },
    )

    OverlayDialog(
        title = HolidayStr.deleteRecord(),
        summary = if (dialogType == HolidayManager.TYPE_HOLIDAY) {
            HolidayStr.deleteHolidayConfirm()
        } else {
            HolidayStr.deleteWorkswapConfirm()
        },
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
                CommonStr.cancel(),
                {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    showDeleteConfirm = false
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                CommonStr.delete(),
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
                onHolidayDataChanged()
                showSectionRangeDialog = false
            } else {
                showToast(HolidayStr.sectionRangeChanged())
            }
        },
    )

    TeachingWeekRuleEditDialog(
        show = showTeachingWeekRuleDialog,
        isEditing = editingTeachingWeekRuleIndex >= 0,
        firstWeek = firstOriginalWeek,
        firstWeekSummary = HolidayStr.originalWeekN(firstOriginalWeek) + " · " + formatDateRange(firstOriginalWeek, 1, 7),
        secondWeek = secondOriginalWeek,
        secondWeekSummary = HolidayStr.originalWeekN(secondOriginalWeek) + " · " + formatDateRange(secondOriginalWeek, 1, 7),
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
        title = HolidayStr.deleteReorgRule(),
        summary = HolidayStr.deleteReorgConfirm(),
        show = showTeachingWeekDeleteConfirm,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showTeachingWeekDeleteConfirm = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                CommonStr.cancel(),
                { showTeachingWeekDeleteConfirm = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                CommonStr.delete(),
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
    savedFirstPartWeek = savedFirstPartWeek?.let { CommonStr.weekN(it) },
    savedSecondPartWeek = savedSecondPartWeek?.let { CommonStr.weekN(it) },
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
        HolidayStr.weeksShiftedN(shiftedWeeks)
    } else {
        HolidayStr.laterUnchanged()
    }
    return TeachingWeekRuleOutcomeLabels(
        mergeLabel = mergedWeek?.let { HolidayStr.mergedInto(it) },
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
    val isTablet = isTabletWidth()
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val startAction: @Composable () -> Unit = {
        val material = LocalSheetTopBarMaterial.current
        LiquidTopBarButton(
            onClick = {
                onDismiss()
            },
            backdrop = LocalSheetContentBackdrop.current!!,
            icon = MiuixIcons.Normal.Close,
            contentDescription = CommonStr.cancel(),
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
            contentDescription = if (validationError == null) CommonStr.save() else HolidayStr.cannotSave(),
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
                    // 卡底色：莫奈取色下跟随 surfaceContainerHigh（比 sheet 底亮一档，
                    // 与 AddEditCourseBottomSheet / AddCourseDialog 同一套）；非莫奈保持原硬编码
                    color = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainerHigh
                    else if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF),
                    contentColor = MiuixTheme.colorScheme.onSurface,
                ),
            ) {
                Column {
                    ArrowPreference(
                        title = HolidayStr.firstHalfWeeksLabel(),
                        summary = firstWeekSummary,
                        onClick = onPickFirstWeek,
                    )
                    TeachingWeekdayRangeSelector(
                        title = HolidayStr.firstHalfWeekdaysLabel(),
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
                    // 同上：莫奈取色下跟随 surfaceContainerHigh
                    color = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainerHigh
                    else if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF),
                    contentColor = MiuixTheme.colorScheme.onSurface,
                ),
            ) {
                Column {
                    ArrowPreference(
                        title = HolidayStr.secondHalfWeeksLabel(),
                        summary = secondWeekSummary,
                        onClick = onPickSecondWeek,
                    )
                    TeachingWeekdayRangeSelector(
                        title = HolidayStr.secondHalfWeekdaysLabel(),
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
                        color = if (isDark) Color.White.copy(alpha = 0.04f)
                        else Color.Black.copy(alpha = 0.04f)
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
                        CommonStr.delete(),
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
            title = if (isEditing) HolidayStr.editReorgRule() else HolidayStr.addReorgRule(),
            dimBackground = true,
            enableContentHeightSnap = true,
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
            title = if (isEditing) HolidayStr.editReorgRule() else HolidayStr.addReorgRule(),
            dimBackground = true,
            enableContentHeightSnap = true,
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
            // 卡底色：莫奈取色下跟随 surfaceContainerHigh（本组件只在 sheet 内使用）
            color = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainerHigh
            else if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF),
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
                    text = HolidayStr.reorgPreview(),
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
                    title = HolidayStr.firstHalfOriginalWeekN(firstWeek),
                    dateRange = preview.firstPartDateRange ?: HolidayStr.dateUnavailable(),
                    markerColor = primaryColor,
                    connectorColor = dividerColor,
                    hasConnector = true,
                )
                preview.pauseDateRange?.let { dateRange ->
                    TeachingWeekRuleTimelineItem(
                        title = HolidayStr.coursesPaused(),
                        dateRange = dateRange,
                        markerColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
                        connectorColor = dividerColor,
                        hasConnector = true,
                    )
                }
                TeachingWeekRuleTimelineItem(
                    title = HolidayStr.secondHalfOriginalWeekN(secondWeek),
                    dateRange = preview.secondPartDateRange ?: HolidayStr.dateUnavailable(),
                    markerColor = MiuixTheme.colorScheme.secondaryVariant,
                    connectorColor = dividerColor,
                    hasConnector = false,
                )
            }

            validationError?.let { error ->
                Text(
                    text = HolidayStr.cannotSaveWithError(error),
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
            textAlign = TextAlign.End,
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
                text = HolidayStr.continuousRange(),
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
                    // 未选中格子底：莫奈下 surfaceContainerHighest（比分组卡再亮一档）
                    else if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainerHighest
                    else if (isDark) Color(0xFF363636) else Color(0xFFF2F2F2)
                val textColor = when {
                    // 选中格文字取 onPrimary（非莫奈时即白，行为不变；莫奈 primary 亮度不定，
                    // 死白对比可能不足）
                    selected -> MiuixTheme.colorScheme.onPrimary
                    enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .continuousClip(10.dp)
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
                        text = HolidayStr.weekdayChars[day - 1](),
                        fontSize = 14.sp,
                        color = textColor,
                        textAlign = TextAlign.Center,
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
        title = HolidayStr.pickOriginalWeek(),
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
                label = { HolidayStr.originalWeekN(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = dateRangeSummary,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                // 父 Column 居中摆位：日期区间变长变短时要按中心收放，否则会先贴到左边
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(CommonStr.cancel(), onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    CommonStr.ok(),
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
        title = HolidayStr.pickSections(),
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
                        text = HolidayStr.startLabel(),
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
                        text = HolidayStr.endLabel(),
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
                    text = CommonStr.cancel(),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = CommonStr.ok(),
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
// 直接改这两行即可，不用碰 provider：Y 沿展开方向偏移（向下为正、向上自动取反），
// X 水平偏移（右为正）；两者都会过窗口边界 clamp，太远会停在屏幕边缘不会跑出去。
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
            title = HolidayStr.yearLabel(),
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
        title = entry.name.orDefaultName(
            if (entry.type == HolidayManager.TYPE_HOLIDAY) HolidayStr.defaultHolidayName
            else HolidayStr.defaultWorkswapName
        ),
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
// 注意：原来的 `context: Context` 形参从未被使用（函数体只用 CourseRepository），迁 :ui-shared 时删掉
private fun followDate(
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
            HolidayStr.dateRangeTo(displayDate(entry.endDate))
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
                HolidayStr.classOnN(date.monthNumber, date.dayOfMonth)
            }
            entry.followWeek > 0 && entry.followWeekday in 1..7 ->
                HolidayStr.weekWeekdayN(entry.followWeek, HolidayStr.weekdays[entry.followWeekday - 1]())
            else -> HolidayStr.makeupPending()
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
            title = if (isHoliday) HolidayStr.addHoliday() else HolidayStr.addWorkswap(),
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
    canDelete: Boolean,
    onDeleteClick: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val isHoliday = dialogType == HolidayManager.TYPE_HOLIDAY
    val hapticFeedback = LocalHapticFeedback.current
    val isTablet = isTabletWidth()
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val cardColor = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainerHigh
    else if (isDark) Color(0xFF303030) else Color(0xFFFFFFFF)
    val canSave = name.isNotBlank()

    // 顶栏按钮：左 × 取消，右 ✓ 保存。⚠ backdrop 必须取 LocalSheetContentBackdrop ——
    // sheet 顶栏是浮在内容之上的，不喂 backdrop 就是一块实心色块。
    val startAction: @Composable () -> Unit = {
        val material = LocalSheetTopBarMaterial.current
        LiquidTopBarButton(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                onDismiss()
            },
            backdrop = LocalSheetContentBackdrop.current!!,
            icon = MiuixIcons.Normal.Close,
            contentDescription = CommonStr.cancel(),
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
                if (canSave) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSave()
                }
            },
            backdrop = LocalSheetContentBackdrop.current!!,
            icon = MiuixIcons.Ok,
            contentDescription = if (canSave) CommonStr.save() else HolidayStr.enterNameFirst(),
            modifier = Modifier.padding(end = if (isTablet) 16.dp else 18.dp),
            iconSize = 25.dp,
            iconTint = if (canSave) Color.Unspecified
                else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f),
            enabled = canSave,
            backdropAlpha = material.backdropAlpha,
            shadowAlpha = material.shadowAlpha,
        )
    }
    val sheetContent: @Composable () -> Unit = {
        // 一字段一块：控件放 Card，标题走 SmallTitle。
        //
        // ⚠ 两层 Column 是刻意的：外层管「块与块」之间的 12dp；内层只管「标题 + 它自己的
        //    卡片」，间距 0 —— 标题浮在卡片上方会显得空一大截。SmallTitle 自带的
        //    bottomPadding 也在 [SHEET_TITLE_PADDING] 里归零。
        //
        // ⚠ 左对齐：外层 16dp + 标题左 12dp = 28dp；卡片内文字同样 padding 12dp = 28dp。
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(
                    color = cardColor,
                    contentColor = MiuixTheme.colorScheme.onSurface,
                ),
            ) {
                NativeMiuixTextField(
                    name,
                    onNameChange,
                    label = HolidayStr.nameLabel(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column {
                SmallTitle(
                    text = if (isHoliday) HolidayStr.startDateLabel() else HolidayStr.adjustedDateLabel(),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    // 滚轮上下自留白：0 会让首尾两行贴卡片边缘
                    insideMargin = PaddingValues(vertical = 16.dp),
                    colors = CardDefaults.defaultColors(
                        color = cardColor,
                        contentColor = MiuixTheme.colorScheme.onSurface,
                    ),
                ) {
                    DatePickerRow(
                        year = startYear,
                        month = startMonth,
                        day = startDay,
                        onYearChange = onStartYearChange,
                        onMonthChange = onStartMonthChange,
                        onDayChange = onStartDayChange,
                    )
                }
            }

            if (isHoliday) {
                Column {
                    SmallTitle(
                        text = HolidayStr.endDateLabel(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(vertical = 16.dp),
                        colors = CardDefaults.defaultColors(
                            color = cardColor,
                            contentColor = MiuixTheme.colorScheme.onSurface,
                        ),
                    ) {
                        DatePickerRow(
                            year = endYear,
                            month = endMonth,
                            day = endDay,
                            onYearChange = onEndYearChange,
                            onMonthChange = onEndMonthChange,
                            onDayChange = onEndDayChange,
                        )
                    }
                }
            }

            if (!isHoliday) {
                Column {
                    // 推算日期与小标题同行、右对齐：用户要核对「补的是哪一天」，别塞进卡片
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SmallTitle(
                            text = HolidayStr.followCourseLabel(),
                            modifier = Modifier.weight(1f),
                        )
                        val date = followDate(followWeek, followWeekday)
                        if (date != null) {
                            Text(
                                "${date.year}/${date.monthNumber}/${date.dayOfMonth}",
                                style = MiuixTheme.textStyles.body1.copy(
                                    fontSize = 15.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                ),
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(vertical = 16.dp),
                        colors = CardDefaults.defaultColors(
                            color = cardColor,
                            contentColor = MiuixTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    label = { CommonStr.weekN(it) },
                                    modifier = Modifier.weight(1f),
                                )
                                NumberPicker(
                                    followWeekday.toIntOrNull()?.coerceIn(1, 7) ?: 1,
                                    { onFollowWeekdayChange(it.toString()) },
                                    range = 1..7,
                                    visibleItemCount = 3,
                                    itemHeight = 44.dp,
                                    textStyle = pickerTextStyle(),
                                    label = { HolidayStr.weekdays[it - 1]() },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // 国家级数据只说「哪天补班」，不说「补哪天的课」，上面是推算出来的预填值，
                            // 各校安排未必一致 —— 明确告诉用户：以学校通知为准，保存后才生效。
                            Text(
                                HolidayStr.followPrefillNote(),
                                style = MiuixTheme.textStyles.body1.copy(
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
            }

            if (canDelete) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDeleteClick()
                    },
                    colors = ButtonDefaults.buttonColors(
                        color = if (isDark) Color.White.copy(alpha = 0.04f)
                        else Color.Black.copy(alpha = 0.04f)
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
                        CommonStr.delete(),
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
            title = dialogTitle,
            dimBackground = true,
            enableContentHeightSnap = true,
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
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    } else {
        BlurBottomSheet(
            show = show,
            title = dialogTitle,
            dimBackground = true,
            enableContentHeightSnap = true,
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
                Spacer(modifier = Modifier.height(statusBarsPadding + 60.dp))
            }
        }
    }
}

/**
 * 年月日三列滚轮（卡片内容；标题由调用方的 SmallTitle 承担）。
 *
 * ⚠ `day` 会随年/月被 clamp 回当月天数 —— 从大月滚到小月不能留下非法日期，
 * 这个逻辑别在搬移时丢掉。
 */
@Composable
private fun DatePickerRow(
    year: Int,
    month: Int,
    day: Int,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onDayChange: (Int) -> Unit,
) {
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
