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
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.screens.getDaysInMonth
import com.haooz.chedule.ui.screens.parseDate
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import com.haooz.chedule.viewmodel.shiftViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 课表设置（二级页）。 */
/** 课表设置。 */
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
                        CollapsibleTopAppBarDefaults.CollapsedHeight + 8.dp,
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
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
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
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
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
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
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
                            ArrowPreference(
                                title = SettingsStr.courseTimeManagement(),
                                summary = SettingsStr.courseTimeSummary(),
                                onClick = {
                                    FeatureLog.timeConfig("open")
                                    router.navigate(AppRoute.CourseTimeSettings)
                                },
                            )
                        }
                    }
                }
                item(key = "entries") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = SettingsStr.holidayAndWorkswap(),
                                onClick = {
                                    FeatureLog.holiday("open")
                                    router.navigate(AppRoute.HolidaySettings)
                                },
                            )
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

            OverlayDialog(
                title = SettingsStr.newSemester(),
                summary = SettingsStr.newSemesterDialogSummary(),
                show = showNewSemesterDialog,
                liquidGlassBackdrop = backdrop,
                onDismissRequest = {
                    showNewSemesterDialog = false
                    newSemesterName = ""
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NativeMiuixTextField(
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
                        TextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showNewSemesterDialog = false
                                newSemesterName = ""
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
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
                liquidGlassBackdrop = backdrop,
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
                            textStyle = MiuixTheme.textStyles.title2,
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
                            textStyle = MiuixTheme.textStyles.title2,
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
                            textStyle = MiuixTheme.textStyles.title2,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showStartDateDialog = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
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
                liquidGlassBackdrop = backdrop,
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
                        TextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showCurrentWeekDialog = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
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
                liquidGlassBackdrop = backdrop,
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
                        TextButton(
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showTotalWeeksDialog = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
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
