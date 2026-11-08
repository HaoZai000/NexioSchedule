package com.haooz.chedule.ui.screens

// ⚠ 本文件不得出现 android.* / androidx.activity / androidx.core；平台能力做成注入参数。

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.util.lengthOfMonth
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.AboutStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// 解析失败时回退到今天，避免弹窗初值出现非法日期
internal fun parseDate(dateStr: String): Triple<Int, Int, Int> {
    return try {
        val parts = dateStr.split("/")
        Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    } catch (_: Exception) {
        // kotlinx-datetime 替代 JVM 的 java.util.Calendar
        val d = todayLocalDate()
        Triple(d.year, d.monthNumber, d.dayOfMonth)
    }
}

internal fun getDaysInMonth(year: Int, month: Int): Int {
    return try {
        LocalDate(year, month, 1).lengthOfMonth()
    } catch (_: Exception) {
        31
    }
}

// 顶栏折叠期间会逐帧重组，提到顶层避免组合期每帧新建 Set

@Composable
fun SettingsScreen(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    shiftViewModel: ShiftViewModel,
    isShiftMode: Boolean = false,
    onExitShiftMode: () -> Unit = {},
    onEnterShiftMode: () -> Unit = {},
    navBarStyle: String = "standard",
    settingsScrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    // ── 平台注入（宿主提供；桌面/iOS 用默认空实现）──
    // 平板两栏外壳的 8 个平台专有 dest 接线留在宿主，本页只负责转交槽位。
    /** 平台专有设置面板（8 个 dest）由宿主渲染；null 显示占位 */
    androidPane: (@Composable (TabletSettingsDest) -> Unit)? = null,
    /** 提醒页「测试通知」动作（Android 发超级岛/实时活动） */
    onSendTestNotification: () -> Unit = {},
    /** 是否超级岛设备（决定「测试通知」按钮文案） */
    isIslandSupported: () -> Boolean = { false },
    /**
     * 提醒页底部测试按钮文案；null = 按 [isIslandSupported] 走平台默认文案
     * （超级岛设备「测试小米超级岛」/ 其余「测试实时活动」）。
     * iOS 传「测试灵动岛 / 测试通知」，与原生路由页同一套文案。
     */
    testActionLabel: String? = null,
    /** 小部件页「刷新已装小部件」（Android = WidgetUpdateCache） */
    onRefreshWidgets: () -> Unit = {},
    /** 节假日数据变更副作用（重排提醒 + 刷小部件） */
    onHolidayDataChanged: () -> Unit = {},
    /** 节假日 API 拉取（Android 宿主实现）；null 时返回 emptyList */
    fetchHolidayEntries: (suspend (year: Int) -> List<HolidayEntry>)? = null,
    /**
     * 是否显示「更新设置」入口。
     */
    showUpdateSettings: Boolean = true,
    /**
     * 是否显示「排班模式」入口。
     * ⚠ 传 false 时 `isShiftMode` 必为 false，只需挡「进入」两处，「退出」也一并挡掉以防状态被外部置起。
     */
) {
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val isSemesterStarted by viewModel.isSemesterStarted.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val showNonCurrentWeek by settingsViewModel.showNonCurrentWeek.collectAsState()
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()
    // 不可在 forEach 内 collectAsState：remember 槽位会随课表增删错位
    val scheduleSummaries by scheduleViewModel.scheduleSummaries.collectAsState()
    val shiftSelectedSchedules by shiftViewModel.shiftSelectedSchedules.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    // LocalAppRouter.current 是 @Composable 调用，必须在组合层取（各入口共用）
    val settingsRouter = LocalAppRouter.current
    var showShiftModeConfirmDialog by remember { mutableStateOf(false) }
    var showNewSemesterDialog by remember { mutableStateOf(false) }
    var newSemesterName by remember { mutableStateOf("") }

    val (tempYearInit, tempMonthInit, tempDayInit) = remember(classStartTime) {
        parseDate(classStartTime)
    }

    var showCurrentWeekDialog by remember { mutableStateOf(false) }
    var showTotalWeeksDialog by remember { mutableStateOf(false) }
    var showStartDateDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    var tempCurrentWeek by remember { mutableIntStateOf(currentWeek) }
    var tempTotalWeeks by remember { mutableIntStateOf(totalWeeks) }
    var tempYear by remember { mutableIntStateOf(tempYearInit) }
    var tempMonth by remember { mutableIntStateOf(tempMonthInit) }
    var tempDay by remember { mutableIntStateOf(tempDayInit) }

    val backgroundColor = MiuixTheme.colorScheme.surface
    // 平板：设置页左右两栏，原地静态切换
    if (navBarStyle == "rail") {
        TabletSettingsScreen(
            viewModel = viewModel,
            scheduleViewModel = scheduleViewModel,
            settingsViewModel = settingsViewModel,
            shiftViewModel = shiftViewModel,
            isShiftMode = isShiftMode,
            onExitShiftMode = onExitShiftMode,
            onEnterShiftMode = onEnterShiftMode,
            liquidGlassBackdrop = liquidGlassBackdrop,
            // —— 8 个平台专有 dest，由宿主 androidPane 槽位渲染 ——
            androidPane = androidPane,
            onSendTestNotification = onSendTestNotification,
            isIslandSupported = isIslandSupported,
            testActionLabel = testActionLabel,
            onRefreshWidgets = onRefreshWidgets,
            onHolidayDataChanged = onHolidayDataChanged,
            fetchHolidayEntries = fetchHolidayEntries,
            // iOS 走 App Store 更新：入口整个不渲染（平板左栏也要挡）
            showUpdateSettings = showUpdateSettings,
        )
        return
    }
    val isDark = isAppDarkTheme()
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp
    val nativeChromeInset = LocalHostNavigation.current
        ?.takeIf { it.showsNativeTabBar && !isTablet }
        ?.bottomBarInset?.toFloat()?.dp

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        Scaffold(
        topBar = {}
    ) { paddingValues ->
            val listState = rememberLazyListState()
            // 折叠高度在布局阶段补齐，避免组合期读 currentHeightPx 导致动画每帧重组
            val scrollBehaviorModifier = remember(settingsScrollBehavior) {
                settingsScrollBehavior?.let {
                    Modifier
                        .collapsibleTopInset(it)
                        .nestedScroll(it.nestedScrollConnection)
                } ?: Modifier
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .then(scrollBehaviorModifier)
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    ),
                contentPadding = PaddingValues(
                    start = tabletHorizontalPadding,
                    top = paddingValues.calculateTopPadding() +
                        CollapsibleTopAppBarDefaults.CollapsedHeight,
                    end = tabletHorizontalPadding,
                    bottom = nativeChromeInset?.plus(24.dp) ?: 60.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "basic") {
                    SmallTitle(
                        text = SettingsStr.basicSettings(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
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
                                holdDownState = showStartDateDialog
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
                                    // 未开始学期时 currentWeek 可能是 0：滚轮会被 coerce 到 1 显示，
                                    // 这里也必须抬到 1，否则「看着是第 1 周、点确定却写回 0」
                                    tempCurrentWeek = currentWeek.coerceIn(1, totalWeeks)
                                    showCurrentWeekDialog = true
                                },
                                holdDownState = showCurrentWeekDialog
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
                                holdDownState = showTotalWeeksDialog
                            )

                            SwitchPreference(
                                title = SettingsStr.smartWeekend(),
                                summary = SettingsStr.smartWeekendSummary(),
                                checked = smartWeekend,
                                onCheckedChange = { settingsViewModel.setSmartWeekend(it) }
                            )

                            CardExpand(visible = !isShiftMode) {
                                SwitchPreference(
                                    title = SettingsStr.showNonCurrentWeek(),
                                    checked = showNonCurrentWeek,
                                    onCheckedChange = { settingsViewModel.setShowNonCurrentWeek(it) }
                                )
                            }

                            ArrowPreference(
                                    title = SettingsStr.courseTimeManagement(),
                                    summary = SettingsStr.courseTimeSummary(),
                                onClick = {
                                    FeatureLog.timeConfig("open")
                                    // 已路由化：保存/删除作息时 repository 会 notifyCourseChanged("settings")，
                                    // 各 VM 自行刷新，无需结果回调
                                    settingsRouter.navigate(AppRoute.CourseTimeSettings)
                                }
                            )
                        }
                    }
                }

                if (!isShiftMode) {
                    item(key = "features") {
                        SmallTitle(
                            text = SettingsStr.featuresSection(),
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = SettingsStr.courseReminder(),
                                    summary = SettingsStr.courseReminderSummary(),
                                    onClick = {
                                        FeatureLog.reminder("open")
                                        settingsRouter.navigate(AppRoute.CourseReminder)
                                    }
                                )
                                ArrowPreference(
                                    title = SettingsStr.holidayAndWorkswap(),
                                    onClick = {
                                        FeatureLog.holiday("open")
                                        settingsRouter.navigate(AppRoute.HolidaySettings)
                                    }
                                )
                                ArrowPreference(
                                    title = SettingsStr.homeScreenWidget(),
                                    onClick = {
                                        FeatureLog.widget("open")
                                        settingsRouter.navigate(AppRoute.WidgetIntro)
                                    }
                                )
                                ArrowPreference(
                                    title = SettingsStr.shiftMode(),
                                    summary = SettingsStr.shiftModeSummary(),
                                    holdDownState = showShiftModeConfirmDialog,
                                    onClick = { showShiftModeConfirmDialog = true }
                                )
                            }
                        }
                    }
                }

                if (isShiftMode) {
                    item(key = "shift_schedules") {
                        SmallTitle(
                            text = SettingsStr.selectCompareSchedules(),
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                scheduleNames.forEach { name ->
                                    CheckboxPreference(
                                        title = name,
                                        summary = scheduleSummaries[name] ?: "",
                                        checked = name in shiftSelectedSchedules,
                                        onCheckedChange = { checked ->
                                            val newList = if (checked) {
                                                shiftSelectedSchedules + name
                                            } else {
                                                shiftSelectedSchedules - name
                                            }
                                            shiftViewModel.setShiftSelectedSchedules(newList)
                                        },
                                        checkboxLocation = CheckboxLocation.End
                                    )
                                }
                            }
                        }
                    }

                    item(key = "shift_exit") {
                        top.yukonga.miuix.kmp.basic.Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, end = tabletHorizontalPadding, start = tabletHorizontalPadding)
                                .height(50.dp),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                onExitShiftMode()
                            },
                            colors = if (isDark) ButtonDefaults.buttonColors(
                                color = Color(0xFF181818)
                            ) else ButtonDefaults.buttonColors(),
                        ) {
                            Text(
                                text = SettingsStr.exitShiftMode(),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = ComposeColor(0xFFF44336)
                            )
                        }
                    }
                }

                if (!isShiftMode) {
                    item(key = "data_manage") {
                        SmallTitle(
                            text = SettingsStr.dataManagement(),
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // LocalAppRouter.current 是 @Composable 调用，必须在组合层取
                                val dataManageRouter = LocalAppRouter.current
                                ArrowPreference(
                                    title = SettingsStr.scheduleImport(),
                                    onClick = {
                                        FeatureLog.import("open")
                                        dataManageRouter.navigate(AppRoute.ScheduleImport)
                                    }
                                )
                                ArrowPreference(
                                    title = SettingsStr.scheduleExport(),
                                    onClick = {
                                        FeatureLog.backup("open_export")
                                        dataManageRouter.navigate(AppRoute.ScheduleExport)
                                    }
                                )
                                ArrowPreference(
                                    title = SettingsStr.scheduleBackup(),
                                    onClick = {
                                        FeatureLog.backup("open_backup")
                                        dataManageRouter.navigate(AppRoute.ScheduleBackup)
                                    }
                                )
                            }
                        }
                    }
                }

                if (!isShiftMode) {
                    item(key = "others_title") {
                        SmallTitle(
                            text = CommonStr.other(),
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = SettingsStr.newSemester(),
                                    summary = SettingsStr.newSemesterSummary(),
                                    holdDownState = showNewSemesterDialog,
                                    onClick = {
                                        FeatureLog.t("设置", "new_semester_dialog")
                                        newSemesterName = ""
                                        showNewSemesterDialog = true
                                    }
                                )
                            }
                        }
                    }
                    item(key = "others_prefs") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // LocalAppRouter.current 是 @Composable 调用，必须在组合层取
                                val appRouter = LocalAppRouter.current
                                ArrowPreference(
                                    title = SettingsStr.appPreferences(),
                                    // 只在偏好设置页仍在栈上时压暗；
                                    // 单独打开更新设置时不应连带压暗本项
                                    onClick = {
                                        FeatureLog.preference("open")
                                        appRouter.navigate(AppRoute.PreferenceSettings)
                                    }
                                )
                                // iOS 走 App Store 更新 → 宿主传 showUpdateSettings=false，入口整个不渲染
                                CardExpand(visible = showUpdateSettings) {
                                    ArrowPreference(
                                        title = SettingsStr.updateSettings(),
                                        onClick = {
                                            FeatureLog.update("open")
                                            appRouter.navigate(AppRoute.UpdateSettings)
                                        }
                                    )
                                }
                                ArrowPreference(
                                    title = SettingsStr.aboutApp(),
                                    onClick = {
                                        FeatureLog.about("open")
                                        appRouter.navigate(AppRoute.About)
                                    },
                                    endActions = {
                                        Text(
                                            text = SettingsStr.supportAuthor(),
                                            fontSize = 14.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

        // 即使 iOS 没有入口打开它也要挡：showShiftModeConfirmDialog 可能被别的路径置 true。
        OverlayDialog(
            title = SettingsStr.enterShiftMode(),
            summary = SettingsStr.enterShiftModeSummary(),
            show = showShiftModeConfirmDialog,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = { showShiftModeConfirmDialog = false },

        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = CommonStr.cancel(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showShiftModeConfirmDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = CommonStr.ok(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showShiftModeConfirmDialog = false
                            coroutineScope.launch {
                                delay(100.milliseconds)
                                onEnterShiftMode()
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        OverlayDialog(
            title = SettingsStr.newSemester(),
            summary = SettingsStr.newSemesterDialogSummary(),
            show = showNewSemesterDialog,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = {
                showNewSemesterDialog = false
                newSemesterName = ""
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NativeMiuixTextField(
                    value = newSemesterName,
                    onValueChange = { newSemesterName = it },
                    label = SettingsStr.newScheduleNameLabel(),
                    modifier = Modifier.fillMaxWidth(),
                    requestFocus = showNewSemesterDialog
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = CommonStr.cancel(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showNewSemesterDialog = false
                            newSemesterName = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = SettingsStr.create(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            val name = newSemesterName.trim()
                            when {
                                // 名称空着点「创建」以前是静默无反应，看着像按钮坏了
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
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        OverlayDialog(
            title = SettingsStr.startDate(),
            show = showStartDateDialog,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = { showStartDateDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberPicker(
                        value = tempYear,
                        onValueChange = { tempYear = it },
                        // 当年份超出 2024..2030 时滚轮会把值静默夹到边界，
                        // 点确定就写回一个不是用户选的年份，所以按当前值把范围撑开
                        range = minOf(2024, tempYearInit)..maxOf(2030, tempYearInit),
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
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
                        modifier = Modifier.weight(1f)
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
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = CommonStr.cancel(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showStartDateDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = CommonStr.ok(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            // ⚠ 手写 padStart（`String.format` 是 JVM 专有）；格式与迁移前逐字一致，且
                            // 不会像 `LocalDate(...)` 那样在月/日组合非法时抛异常（换月瞬间 tempDay 可能短暂越界）。
                            val date = "${tempYear.toString().padStart(4, '0')}/" +
                                "${tempMonth.toString().padStart(2, '0')}/" +
                                tempDay.toString().padStart(2, '0')
                            viewModel.setClassStartTime(date)
                            showStartDateDialog = false
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        OverlayDialog(
            title = SettingsStr.selectCurrentWeek(),
            show = showCurrentWeekDialog,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = { showCurrentWeekDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NumberPicker(
                    value = tempCurrentWeek,
                    onValueChange = { tempCurrentWeek = it },
                    range = 1..totalWeeks,
                    visibleItemCount = 3,
                    itemHeight = 60.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp, bottom = 20.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = CommonStr.cancel(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showCurrentWeekDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = CommonStr.ok(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.setCurrentWeek(tempCurrentWeek)
                            showCurrentWeekDialog = false
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        OverlayDialog(
            title = SettingsStr.selectTotalWeeks(),
            show = showTotalWeeksDialog,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = { showTotalWeeksDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NumberPicker(
                    value = tempTotalWeeks,
                    onValueChange = { tempTotalWeeks = it },
                    range = 1..30,
                    visibleItemCount = 3,
                    itemHeight = 60.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp, bottom = 20.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = CommonStr.cancel(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showTotalWeeksDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = CommonStr.ok(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.setTotalWeeks(tempTotalWeeks)
                            showTotalWeeksDialog = false
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    }
}
