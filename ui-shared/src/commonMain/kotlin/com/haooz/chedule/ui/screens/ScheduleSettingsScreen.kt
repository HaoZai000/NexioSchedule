package com.haooz.chedule.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.sync.kingosoft.KingosoftScheduleSyncer
import com.haooz.chedule.data.sync.kingosoft.KingosoftSyncConfig
import com.haooz.chedule.data.sync.kingosoft.KingosoftSyncStore
import com.haooz.chedule.data.util.formatDateTimeMinute
import com.haooz.chedule.data.util.localDateTimeAt
import com.haooz.chedule.data.util.suspendRunCatching
import com.haooz.softglass.backdrop.GlassBackdrop
import kotlinx.coroutines.withContext
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.i18n.L
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.i18n.TimeCfgStr
import com.haooz.chedule.i18n.orDefaultName
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.utils.ShiftModeRequest
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.nexioui.cardexpand.CardExpand
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.activities.eduimport.KingosoftConfigFields
import com.haooz.chedule.ui.activities.eduimport.withStoredKey
import com.haooz.chedule.ui.activities.setting.getDaysInMonth
import com.haooz.chedule.ui.activities.setting.parseDate
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import com.haooz.chedule.viewmodel.shiftViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.textfield.TextField
import com.haooz.nexioui.numberpicker.NumberPicker
import com.haooz.nexioui.scaffold.Scaffold
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.topappbar.CollapsibleTopAppBarDefaults
import com.haooz.nexioui.topappbar.collapsibleTopInset
import com.haooz.nexioui.window.isTabletWidth
import com.haooz.nexioui.overlaydialog.LocalOverlayDialogBackdrop
import com.haooz.nexioui.overlaydialog.OverlayDialog
import com.haooz.nexioui.arrowpreference.ArrowPreference
import com.haooz.nexioui.switchpreference.SwitchPreference
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic

/** 课程表（二级页）。 */
@Composable
fun ScheduleSettingsScreen(router: AppRouter) {
    val viewModel = courseViewModel()
    val scheduleViewModel = scheduleViewModel()
    val settingsViewModel = settingsViewModel()
    val shiftViewModel = shiftViewModel()

    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val isSemesterStarted by viewModel.isSemesterStarted.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val showNonCurrentWeek by settingsViewModel.showNonCurrentWeek.collectAsState()
    val isShiftMode by shiftViewModel.isShiftMode.collectAsState()

    val hapticFeedback = LocalHapticFeedback.current

    var showNewSemesterDialog by remember { mutableStateOf(false) }
    var newSemesterName by remember { mutableStateOf("") }
    var showShiftModeConfirmDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val timeRepository = remember { CourseRepository() }
    val scheduleId by scheduleViewModel.currentScheduleName.collectAsState()
    val syncRevision by KingosoftSyncStore.revision.collectAsState()
    val syncConfig = remember(scheduleId, syncRevision) { KingosoftSyncStore.getConfig(scheduleId) }
    val activeSync by KingosoftScheduleSyncer.activeSchedule.collectAsState()
    var manualSyncPending by remember { mutableStateOf(false) }
    var showKingosoftConfig by remember(scheduleId) { mutableStateOf(false) }
    var syncMessage by remember(scheduleId) { mutableStateOf<String?>(null) }
    // 走「可写」入口：未绑定的课表先补绑，否则保存会把节次写进它回退到的那套配置
    var timeConfig by remember { mutableStateOf(timeRepository.getWritableCurrentTimeConfig().ensureRoutine()) }
    var showSectionPicker by remember { mutableStateOf(false) }
    var secMorning by remember { mutableIntStateOf(4) }
    var secAfternoon by remember { mutableIntStateOf(4) }
    var secEvening by remember { mutableIntStateOf(4) }
    // 从「上课时间」页改完作息返回时重新取一次，右侧作息名要跟着变
    LaunchedEffect(Unit) {
        timeConfig = timeRepository.getWritableCurrentTimeConfig().ensureRoutine()
    }
    val activeRoutineName = timeConfig.safeRoutines
        .firstOrNull { it.id == timeConfig.effectiveRoutineId() }
        ?.name
        .orEmpty()
        .orDefaultName(TimeCfgStr.defaultRoutineName, TimeCfgStr.seededRoutineName, ImportStr.defaultRoutineName)

    val (tempYearInit, tempMonthInit, tempDayInit) = remember(classStartTime) {
        parseDate(classStartTime)
    }
    var showCurrentWeekDialog by remember { mutableStateOf(false) }
    var showTotalWeeksDialog by remember { mutableStateOf(false) }
    var showStartDateDialog by remember { mutableStateOf(false) }
    var tempCurrentWeek by remember { mutableIntStateOf(currentWeek) }
    var tempTotalWeeks by remember { mutableIntStateOf(totalWeeks) }
    var tempYear by remember { mutableIntStateOf(tempYearInit) }
    var tempMonth by remember { mutableIntStateOf(tempMonthInit) }
    var tempDay by remember { mutableIntStateOf(tempDayInit) }

    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp
    val nativeChromeInset = LocalHostNavigation.current
        ?.takeIf { it.showsNativeTabBar && !isTablet }
        ?.bottomBarInset?.toFloat()?.dp

    DocumentPageScaffold(
        title = SettingsStr.scheduleSettings(),
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        // 平板设置页右栏：页级 backdrop 只录右半栏，弹窗压在两栏上会一半采空。
        // 外层（TabletSettingsScreen 注入）录的是整页两侧，优先用它；手机无外层时回退页级。
        val dialogBackdrop = LocalOverlayDialogBackdrop.current ?: backdrop
        Scaffold(topBar = {}) { paddingValues ->
            val listState = rememberLazyListState()
            val scrollBehaviorModifier = remember(scrollBehavior) {
                Modifier
                    .collapsibleTopInset(scrollBehavior)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .then(scrollBehaviorModifier)
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove),
                contentPadding = PaddingValues(
                    start = tabletHorizontalPadding,
                    top = paddingValues.calculateTopPadding() +
                        CollapsibleTopAppBarDefaults.CollapsedHeight +
                        pageTopExtra(isTablet, hasSmallTitle = false),
                    end = tabletHorizontalPadding,
                    bottom = nativeChromeInset?.plus(24.dp) ?: 60.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "basic") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = SettingsStr.startDate(),
                                endActions = {
                                    Text(
                                        text = classStartTime,
                                        fontSize = 14.5.sp,
                                        color = NexioTheme.colorScheme.onSurfaceVariantActions,
                                        textAlign = TextAlign.End,
                                    )
                                },
                                onClick = {
                                    FeatureLog.t("设置", "start_date_dialog")
                                    val (y, m, d) = parseDate(classStartTime)
                                    tempYear = y
                                    tempMonth = m
                                    tempDay = d
                                    showStartDateDialog = true
                                },
                                holdDownState = showStartDateDialog,
                            )
                            ArrowPreference(
                                title = SettingsStr.currentWeek(),
                                endActions = {
                                    Text(
                                        text = when {
                                            !isSemesterStarted -> SettingsStr.notStarted()
                                            currentWeek > totalWeeks -> SettingsStr.onBreak()
                                            else -> CommonStr.weekN(currentWeek)
                                        },
                                        fontSize = 14.5.sp,
                                        color = NexioTheme.colorScheme.onSurfaceVariantActions,
                                        textAlign = TextAlign.End,
                                    )
                                },
                                onClick = {
                                    FeatureLog.t("设置", "current_week_dialog")
                                    tempCurrentWeek = currentWeek.coerceIn(1, totalWeeks)
                                    showCurrentWeekDialog = true
                                },
                                holdDownState = showCurrentWeekDialog,
                            )
                            ArrowPreference(
                                title = SettingsStr.totalWeeksTitle(),
                                endActions = {
                                    Text(
                                        text = SettingsStr.totalWeeksValue(totalWeeks),
                                        fontSize = 14.5.sp,
                                        color = NexioTheme.colorScheme.onSurfaceVariantActions,
                                        textAlign = TextAlign.End,
                                    )
                                },
                                onClick = {
                                    FeatureLog.t("设置", "total_weeks_dialog")
                                    tempTotalWeeks = totalWeeks
                                    showTotalWeeksDialog = true
                                },
                                holdDownState = showTotalWeeksDialog,
                            )
                            ArrowPreference(
                                title = TimeCfgStr.sectionCountTitle(),
                                endActions = {
                                    Text(
                                        text = TimeCfgStr.sectionsCompactN(
                                            timeConfig.morningSections,
                                            timeConfig.afternoonSections,
                                            timeConfig.eveningSections,
                                        ),
                                        fontSize = 14.5.sp,
                                        color = NexioTheme.colorScheme.onSurfaceVariantActions,
                                        textAlign = TextAlign.End,
                                    )
                                },
                                holdDownState = showSectionPicker,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    secMorning = timeConfig.morningSections
                                    secAfternoon = timeConfig.afternoonSections
                                    secEvening = timeConfig.eveningSections
                                    showSectionPicker = true
                                },
                            )
                            ArrowPreference(
                                title = SettingsStr.classTime(),
                                endActions = {
                                    Text(
                                        text = activeRoutineName,
                                        fontSize = 14.5.sp,
                                        color = NexioTheme.colorScheme.onSurfaceVariantActions,
                                        textAlign = TextAlign.End,
                                    )
                                },
                                onClick = {
                                    FeatureLog.timeConfig("open")
                                    router.navigate(AppRoute.CourseTimeSettings)
                                },
                            )
                            ArrowPreference(
                                title = SettingsStr.holidayAndWorkswap(),
                                onClick = {
                                    FeatureLog.holiday("open")
                                    router.navigate(AppRoute.HolidaySettings)
                                },
                            )
                        }
                    }
                }
                item(key = "display") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SwitchPreference(
                                title = SettingsStr.smartWeekend(),
                                summary = SettingsStr.smartWeekendSummary(),
                                checked = smartWeekend,
                                onCheckedChange = { settingsViewModel.setSmartWeekend(it) },
                            )
                            CardExpand(visible = !isShiftMode) {
                                SwitchPreference(
                                    title = SettingsStr.showNonCurrentWeek(),
                                    checked = showNonCurrentWeek,
                                    onCheckedChange = { settingsViewModel.setShowNonCurrentWeek(it) },
                                )
                            }
                        }
                    }
                }

                item(key = "other:$scheduleId") {
                    CardExpand(visible = syncConfig.isComplete) {
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            KingosoftSyncCard(
                                scheduleId = scheduleId,
                                config = syncConfig,
                                revision = syncRevision,
                                syncing = manualSyncPending || activeSync == scheduleId,
                                editing = showKingosoftConfig,
                                onEditConfig = { showKingosoftConfig = true },
                                onMessage = { syncMessage = it },
                                onSync = {
                                    if (!manualSyncPending) {
                                        manualSyncPending = true
                                        val target = scheduleId
                                        coroutineScope.launch {
                                            try {
                                                suspendRunCatching {
                                                    withContext(ioDispatcher) { KingosoftScheduleSyncer.sync(target, timeRepository) }
                                                }.onSuccess { outcome ->
                                                    viewModel.reloadCourses()
                                                    syncMessage = outcome.summary
                                                }.onFailure {
                                                    syncMessage = SettingsStr.kingosoftSyncFailed(it.message ?: it.toString())
                                                }
                                            } finally {
                                                manualSyncPending = false
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                    SmallTitle(text = CommonStr.other())
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            CardExpand(visible = !isShiftMode) {
                                ArrowPreference(
                                    title = SettingsStr.shiftMode(),
                                    summary = SettingsStr.shiftModeSummary(),
                                    holdDownState = showShiftModeConfirmDialog,
                                    onClick = { showShiftModeConfirmDialog = true },
                                )
                            }
                            ArrowPreference(
                                title = SettingsStr.newSemester(),
                                summary = SettingsStr.newSemesterSummary(),
                                holdDownState = showNewSemesterDialog,
                                onClick = {
                                    FeatureLog.t("设置", "new_semester_dialog")
                                    newSemesterName = ""
                                    showNewSemesterDialog = true
                                },
                            )
                        }
                    }
                }
            }

            KingosoftConfigDialog(
                show = showKingosoftConfig && syncConfig.isComplete,
                initial = syncConfig,
                backdrop = dialogBackdrop,
                syncing = manualSyncPending || activeSync == scheduleId,
                onDismiss = { showKingosoftConfig = false },
                onSave = { next ->
                    KingosoftSyncStore.saveConfig(scheduleId, next)
                    showKingosoftConfig = false
                },
                onUnbind = {
                    showKingosoftConfig = false
                    KingosoftSyncStore.deleteConfig(scheduleId)
                },
            )
            KingosoftSyncMessage(syncMessage, dialogBackdrop) { syncMessage = null }
            OverlayDialog(
                title = TimeCfgStr.sectionCountDialogTitle(),
                show = showSectionPicker,
                liquidGlassBackdrop = dialogBackdrop,
                onDismissRequest = { showSectionPicker = false },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        listOf(
                            Triple(TimeCfgStr.morning(), { v: Int -> secMorning = v }, secMorning),
                            Triple(TimeCfgStr.afternoon(), { v: Int -> secAfternoon = v }, secAfternoon),
                            Triple(TimeCfgStr.evening(), { v: Int -> secEvening = v }, secEvening),
                        ).forEach { (label, onPick, current) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = label,
                                    style = NexioTheme.textStyles.footnote1,
                                    color = NexioTheme.colorScheme.onSurfaceVariantActions,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                                    textAlign = TextAlign.Center,
                                )
                                NumberPicker(
                                    value = current,
                                    onValueChange = onPick,
                                    range = 0..6,
                                    visibleItemCount = 3,
                                    itemHeight = 50.dp,
                                )
                            }
                        }
                    }

                    Text(
                        TimeCfgStr.sectionsNote(),
                        fontSize = 13.sp,
                        color = NexioTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.padding(top = 16.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FlatTextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showSectionPicker = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        FlatTextButton(
                            text = CommonStr.ok(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                val next = timeConfig.copy(
                                    morningSections = secMorning,
                                    afternoonSections = secAfternoon,
                                    eveningSections = secEvening,
                                )
                                timeRepository.saveTimeConfig(next)
                                // 节次数变了 → 节次到时间的映射全变，必须广播让提醒/小部件重排
                                timeRepository.notifyTimeConfigChanged()
                                timeConfig = timeRepository.getWritableCurrentTimeConfig().ensureRoutine()
                                showSectionPicker = false
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            OverlayDialog(
                title = SettingsStr.enterShiftMode(),
                summary = SettingsStr.enterShiftModeSummary(),
                show = showShiftModeConfirmDialog,
                liquidGlassBackdrop = dialogBackdrop,
                onDismissRequest = { showShiftModeConfirmDialog = false },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FlatTextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showShiftModeConfirmDialog = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        FlatTextButton(
                            text = CommonStr.ok(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showShiftModeConfirmDialog = false
                                coroutineScope.launch {
                                    delay(100.milliseconds)
                                    ShiftModeRequest.enter()
                                    router.popBack()
                                }
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            OverlayDialog(
                title = SettingsStr.newSemester(),
                summary = SettingsStr.newSemesterDialogSummary(),
                show = showNewSemesterDialog,
                liquidGlassBackdrop = dialogBackdrop,
                onDismissRequest = {
                    showNewSemesterDialog = false
                    newSemesterName = ""
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TextField(
                        value = newSemesterName,
                        onValueChange = { newSemesterName = it },
                        label = SettingsStr.newScheduleNameLabel(),
                        modifier = Modifier.fillMaxWidth(),
                        requestFocus = showNewSemesterDialog,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FlatTextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showNewSemesterDialog = false
                                newSemesterName = ""
                            },
                            modifier = Modifier.weight(1f),
                        )
                        FlatTextButton(
                            text = SettingsStr.create(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                val name = newSemesterName.trim()
                                when {
                                    name.isEmpty() -> showToast(SettingsStr.emptyNameToast())
                                    name in scheduleViewModel.scheduleNames.value ->
                                        showToast(SettingsStr.duplicateNameToast())
                                    else -> {
                                        scheduleViewModel.createNewSemesterSchedule(name)
                                        showNewSemesterDialog = false
                                        newSemesterName = ""
                                        showToast(SettingsStr.createdToast(name))
                                    }
                                }
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            OverlayDialog(
                title = SettingsStr.startDate(),
                show = showStartDateDialog,
                liquidGlassBackdrop = dialogBackdrop,
                onDismissRequest = { showStartDateDialog = false },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val maxDaysInMonth = remember(tempYear, tempMonth) {
                        getDaysInMonth(tempYear, tempMonth)
                    }
                    LaunchedEffect(maxDaysInMonth) {
                        if (tempDay > maxDaysInMonth) {
                            tempDay = maxDaysInMonth
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NumberPicker(
                            value = tempYear,
                            onValueChange = { tempYear = it },
                            range = minOf(2024, tempYearInit)..maxOf(2030, tempYearInit),
                            visibleItemCount = 3,
                            itemHeight = 60.dp,
                            textStyle = NexioTheme.textStyles.title2,
                            modifier = Modifier.weight(1f),
                        )
                        NumberPicker(
                            value = tempMonth,
                            onValueChange = { tempMonth = it },
                            range = 1..12,
                            visibleItemCount = 3,
                            itemHeight = 60.dp,
                            label = { SettingsStr.monthN(it) },
                            wrapAround = true,
                            textStyle = NexioTheme.textStyles.title2,
                            modifier = Modifier.weight(1f),
                        )
                        NumberPicker(
                            value = tempDay,
                            onValueChange = { tempDay = it },
                            range = 1..maxDaysInMonth,
                            visibleItemCount = 3,
                            itemHeight = 60.dp,
                            label = { CommonStr.dateN(it) },
                            wrapAround = true,
                            textStyle = NexioTheme.textStyles.title2,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FlatTextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showStartDateDialog = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        FlatTextButton(
                            text = CommonStr.ok(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                val date = "${tempYear.toString().padStart(4, '0')}/" +
                                    "${tempMonth.toString().padStart(2, '0')}/" +
                                    tempDay.toString().padStart(2, '0')
                                viewModel.setClassStartTime(date)
                                showStartDateDialog = false
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            OverlayDialog(
                title = SettingsStr.selectCurrentWeek(),
                show = showCurrentWeekDialog,
                liquidGlassBackdrop = dialogBackdrop,
                onDismissRequest = { showCurrentWeekDialog = false },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NumberPicker(
                        value = tempCurrentWeek,
                        onValueChange = { tempCurrentWeek = it },
                        range = 1..totalWeeks,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 0.dp, bottom = 20.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FlatTextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showCurrentWeekDialog = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        FlatTextButton(
                            text = CommonStr.ok(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                viewModel.setCurrentWeek(tempCurrentWeek)
                                showCurrentWeekDialog = false
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            OverlayDialog(
                title = SettingsStr.selectTotalWeeks(),
                show = showTotalWeeksDialog,
                liquidGlassBackdrop = dialogBackdrop,
                onDismissRequest = { showTotalWeeksDialog = false },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NumberPicker(
                        value = tempTotalWeeks,
                        onValueChange = { tempTotalWeeks = it },
                        range = 1..30,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 0.dp, bottom = 20.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FlatTextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showTotalWeeksDialog = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        FlatTextButton(
                            text = CommonStr.ok(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                viewModel.setTotalWeeks(tempTotalWeeks)
                                showTotalWeeksDialog = false
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KingosoftSyncCard(
    scheduleId: String, config: KingosoftSyncConfig, revision: Int, syncing: Boolean,
    editing: Boolean, onEditConfig: () -> Unit, onMessage: (String) -> Unit, onSync: () -> Unit,
) {
    val store = KingosoftSyncStore
    // 解绑会同时清空配置和同步记录；退场期间沿用卡片最后显示的版本。
    var lastBound by remember(scheduleId) { mutableStateOf(config to revision) }
    if (config.isComplete) lastBound = config to revision
    val (lastBoundConfig, lastBoundRevision) = lastBound
    val history = remember(scheduleId, lastBoundRevision) {
        val at = store.lastSyncAtMillis(scheduleId)
        if (at == 0L) null else "${localDateTimeAt(at).formatDateTimeMinute()} · ${store.lastSyncSummary(scheduleId)}"
    }
    SmallTitle(text = SettingsStr.kingosoftSync())
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        Column {
            ArrowPreference(
                title = SettingsStr.kingosoftConfig(),
                summary = "${lastBoundConfig.userId} · ${lastBoundConfig.jwBaseUrl}",
                enabled = config.isComplete && !syncing,
                holdDownState = editing,
                onClick = onEditConfig,
            )
            SwitchPreference(
                title = SettingsStr.kingosoftAutoSync(),
                checked = lastBoundConfig.autoSyncEnabled,
                enabled = config.isComplete && !syncing,
                onCheckedChange = { checked ->
                    val current = store.getConfig(scheduleId)
                    if (checked == current.autoSyncEnabled) return@SwitchPreference
                    runCatching { store.saveConfig(scheduleId, current.copy(autoSyncEnabled = checked)) }
                        .onFailure { onMessage(it.message ?: it.toString()) }
                },
            )
            ArrowPreference(
                title = SettingsStr.kingosoftSyncNow(),
                summary = if (syncing) SettingsStr.kingosoftSyncing() else history ?: SettingsStr.kingosoftNeverSynced(),
                enabled = config.isComplete && !syncing,
                onClick = onSync,
            )
        }
    }
}

private val confirmUnbindLabel = L("解除绑定", "Unbind")

@Composable
private fun KingosoftConfigDialog(
    show: Boolean, initial: KingosoftSyncConfig, backdrop: GlassBackdrop?, syncing: Boolean,
    onDismiss: () -> Unit, onSave: (KingosoftSyncConfig) -> Unit, onUnbind: () -> Unit,
) {
    // 常驻挂载以保留 OverlayDialog 预测返回与关闭退场动画；打开时重置草稿，不把已存密钥回填到界面。
    var lastInitial by remember { mutableStateOf(initial) }
    var draft by remember { mutableStateOf(initial.copy(encrptKey = "")) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmingUnbind by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) {
            lastInitial = initial
            draft = initial.copy(encrptKey = "")
            error = null
            confirmingUnbind = false
        } else {
            confirmingUnbind = false
        }
    }
    OverlayDialog(
        title = SettingsStr.kingosoftConfig(),
        show = show && !confirmingUnbind,
        liquidGlassBackdrop = backdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KingosoftConfigFields(draft, hasStoredKey = lastInitial.encrptKey.isNotEmpty(), enabled = true) { draft = it }
            error?.let { Text(text = it) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FlatTextButton(text = CommonStr.cancel(), onClick = onDismiss, modifier = Modifier.weight(1f))
                FlatTextButton(text = CommonStr.save(), modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        val next = draft.withStoredKey(lastInitial)
                        runCatching { onSave(next) }.onFailure { error = it.message ?: it.toString() }
                    },
                )
            }
            FlatTextButton(
                text = SettingsStr.kingosoftUnbind(),
                enabled = !syncing,
                onClick = { confirmingUnbind = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    OverlayDialog(
        title = SettingsStr.kingosoftUnbind(),
        summary = SettingsStr.kingosoftUnbindSummary(),
        show = show && confirmingUnbind,
        liquidGlassBackdrop = backdrop,
        onDismissRequest = { confirmingUnbind = false },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlatTextButton(text = CommonStr.cancel(), onClick = { confirmingUnbind = false }, modifier = Modifier.weight(1f))
            FlatTextButton(text = confirmUnbindLabel(), enabled = !syncing, onClick = onUnbind, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun KingosoftSyncMessage(message: String?, backdrop: GlassBackdrop?, onDismiss: () -> Unit) {
    var lastMessage by remember { mutableStateOf("") }
    if (message != null) lastMessage = message
    OverlayDialog(
        title = SettingsStr.kingosoftSync(),
        summary = lastMessage,
        show = message != null,
        liquidGlassBackdrop = backdrop,
        onDismissRequest = onDismiss,
    ) {
        FlatTextButton(text = CommonStr.ok(), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}
