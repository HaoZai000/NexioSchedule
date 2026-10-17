/** 课程提醒设置页面 - Screen */
package com.haooz.chedule.ui.activities

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.ClassroomLabel
import com.haooz.chedule.data.ClassroomLabelRule
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.NoopReminderController
import com.haooz.chedule.data.ReminderController
import com.haooz.chedule.data.showToast
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.Color as ComposeColor

/** 课程提醒页详细流程日志：仅「开始录制」后写入 */
private fun rlog(event: String, detail: String = "") {
    com.haooz.chedule.ui.utils.FeatureLog.reminderFlow(event, detail)
}

/**
 * lambda 版：未录制时 detail 不求值，用于带补零格式化参数的埋点。
 *
 * ⚠ 不能是 `inline`：FeatureLog.reminderFlow 已改为非 inline（本模块 android 变体 target 与
 * :androidApp 不一致，跨模块 inline 编不过），inline 参数无法继续往下传。
 */
private fun rlog(event: String, detail: () -> String) {
    com.haooz.chedule.ui.utils.FeatureLog.reminderFlow(event, detail)
}

/** 两位补零（原 `%02d`；commonMain 没有字符串格式化器，用 padStart 等价实现） */
private fun pad2(n: Int): String = n.toString().padStart(2, '0')

/** `HH:mm` 两位补零（原 `%02d:%02d`） */
private fun pad2(hour: Int, minute: Int): String = "${pad2(hour)}:${pad2(minute)}"

@Composable
fun CourseReminderScreen(
    settingsViewModel: SettingsViewModel = settingsViewModel(),
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    /** 提醒控制器（Android = AndroidReminderController 包装）；桌面/iOS 用 NoopReminderController */
    reminderController: ReminderController = NoopReminderController,
    /** 复制文本到系统剪切板（楼名缩写的 AI 提示词用）；默认空实现，端上按平台注入 */
    onCopyToClipboard: (String) -> Unit = {},
) {
    val preClassReminder by settingsViewModel.preClassReminder.collectAsState()
    val preClassReminderMinutes by settingsViewModel.preClassReminderMinutes.collectAsState()
    val nextDayReminder by settingsViewModel.nextDayReminder.collectAsState()
    val nextDayReminderHour by settingsViewModel.nextDayReminderHour.collectAsState()
    val nextDayReminderMinute by settingsViewModel.nextDayReminderMinute.collectAsState()
    val islandNotification by settingsViewModel.islandNotification.collectAsState()
    val classDndEnabled by settingsViewModel.classDndEnabled.collectAsState()
    val classDndMode by settingsViewModel.classDndMode.collectAsState()
    val reminderPrefs = remember { AppStorage.store("course_reminder_prefs") }
    var isIgnoringBattery by remember { mutableStateOf(true) }
    val hapticFeedback = LocalHapticFeedback.current

    var showMinutesDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var tempMinutes by remember { mutableIntStateOf(preClassReminderMinutes) }
    var tempHour by remember { mutableIntStateOf(nextDayReminderHour) }
    var tempMinute by remember { mutableIntStateOf(nextDayReminderMinute) }

    var shizukuRunning by remember { mutableStateOf(false) }
    var shizukuAuthorized by remember { mutableStateOf(false) }
    var isIslandSupported by remember { mutableStateOf(false) }
    //
    // Live container gate: Android HyperOS super island OR iOS Live Activity.
    // Kept separate from isIslandSupported (HyperOS-only probe) so iOS does not
    // show Xiaomi-specific sub-options (Shizuku, left/right slot, AOD).
    var showIslandCard by remember { mutableStateOf(false) }
    val isHyperIsland = isIslandSupported
    // 槽位卡片 vs「实时动态右侧」卡片的分界：HyperOS 上超级岛开关关闭时要回落到
    // 实时动态右侧（此刻生效的是 live_right_mode，岛的槽位键不生效）；iOS 恒走槽位卡片
    val showSlotCard = showIslandCard && (islandNotification || !isHyperIsland)

    // 实况通知右侧显示：0=课程名称，1=上课地点，2=倒计时
    var liveRightMode by remember { mutableIntStateOf(reminderPrefs.getInt("live_right_mode", 0)) }
    // 超级岛左侧显示：0=课程名称，1=上课地点，2=倒计时
    var islandLeftMode by remember { mutableIntStateOf(reminderPrefs.getInt("island_left_mode", 0)) }
    // 超级岛右侧显示：0=课程名称，1=上课地点，2=倒计时
    var islandRightMode by remember { mutableIntStateOf(reminderPrefs.getInt("island_right_mode", 1)) }
    // 超级岛息屏显示：0=课程名称，1=上课地点
    var islandAodMode by remember { mutableIntStateOf(reminderPrefs.getInt("island_aod_mode", 0)) }
    // 课中岛缩略态右侧：0=正在上课，1=距下课倒计时（仅超级岛生效）
    var islandInClassRightMode by remember {
        mutableIntStateOf(reminderPrefs.getInt("island_in_class_right_mode", 0))
    }
    // 缩略态地点精简：0=原样，1=自动精简，2=自定义正则
    var classroomLabelMode by remember {
        mutableIntStateOf(reminderPrefs.getInt(ClassroomLabel.KEY_MODE, ClassroomLabel.MODE_RAW))
    }
    var classroomLabelPattern by remember { mutableStateOf(reminderPrefs.getString(ClassroomLabel.KEY_PATTERN, "")) }
    var classroomLabelReplacement by remember {
        mutableStateOf(reminderPrefs.getString(ClassroomLabel.KEY_REPLACEMENT, ""))
    }
    // 超宽时保尾部：默认关。开着才会按宽度丢开头，关着只做去前缀这类格式清理
    var classroomLabelTailKeep by remember {
        mutableStateOf(reminderPrefs.getBoolean(ClassroomLabel.KEY_TAIL_KEEP, false))
    }
    var classroomLabelSlotWidth by remember {
        mutableIntStateOf(
            reminderPrefs.getInt(ClassroomLabel.KEY_SLOT_WIDTH, ClassroomLabel.DEFAULT_SLOT_WIDTH)
        )
    }
    var showClassroomRuleDialog by remember { mutableStateOf(false) }
    var tempClassroomPattern by remember { mutableStateOf(classroomLabelPattern) }
    var tempClassroomReplacement by remember { mutableStateOf(classroomLabelReplacement) }
    // 楼名缩写：内置表之外，用户可自己补、或让 AI 按本校叫法生成后粘回来
    var classroomLabelAbbreviations by remember {
        mutableStateOf(reminderPrefs.getString(ClassroomLabel.KEY_ABBREVIATIONS, ""))
    }
    var showAbbrevDialog by remember { mutableStateOf(false) }
    var tempAbbreviations by remember { mutableStateOf(classroomLabelAbbreviations) }
    var showUsageDialog by remember { mutableStateOf(false) }
    val userAbbrevCount = ClassroomLabel.parseAbbreviations(classroomLabelAbbreviations).size
    // 预览用课表里的真实地点：能看出自己学校的地名会被改成什么样，比固定样例有用。
    // CourseRepository 是单例且带课程缓存，这里只在页面进入时取一次。取 4 条，
    // 好让超宽的情况也能在预览里露出来。
    val previewClassrooms = remember {
        val repository = CourseRepository()
        repository.getCoursesForSchedule(repository.getCurrentScheduleId())
            .map { it.classroom.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(4)
    }
    // AI 提示词要带上尽量多的真实地点，4 条不够归纳；专门取一批（带条数上限，避免超长）
    val promptClassrooms = remember {
        val repository = CourseRepository()
        repository.getCoursesForSchedule(repository.getCurrentScheduleId())
            .map { it.classroom.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(60)
    }
    // 课中提醒：超级岛 / 原生实况共用同一开关
    var inClassEnabled by remember {
        mutableStateOf(reminderController.isInClassEnabled())
    }
    // 提醒时机：0=全程，1=距下课
    var inClassTimingMode by remember {
        mutableIntStateOf(reminderController.getInClassTimingMode())
    }
    var inClassLeadMinutes by remember {
        mutableIntStateOf(reminderController.getInClassLeadMinutes())
    }
    var showInClassTimingDialog by remember { mutableStateOf(false) }
    var tempInClassTimingMode by remember { mutableIntStateOf(inClassTimingMode) }
    var tempInClassLeadMinutes by remember { mutableIntStateOf(inClassLeadMinutes) }

    val masterEnabled = preClassReminder || nextDayReminder
    // 总开关关闭时联动关掉子开关：勿扰 + 课中提醒（与下节课/次日提醒置 false 同一策略）
    val disableMasterDependentSwitches = {
        settingsViewModel.setClassDndEnabled(false)
        inClassEnabled = false
        reminderPrefs.edit { putBoolean(ReminderController.KEY_IN_CLASS, false) }
    }
    var permissionRefreshKey by remember { mutableIntStateOf(0) }
    // 电池优化 / 自启动设置页返回：重读「已忽略电池优化」并触发权限刷新
    // （原 batteryOptLauncher 与 autoStartLauncher 的回调逐字相同，合并为一个）
    val onBatterySettingsResult: () -> Unit = {
        isIgnoringBattery = reminderController.isIgnoringBatteryOptimizations()
        permissionRefreshKey++
    }
    var canPostPromoted by remember { mutableStateOf(false) }
    var canScheduleExactAlarms by remember { mutableStateOf(true) }
    var dndPermissionGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }
    // 勿扰授权页返回：重读勿扰权限 → 触发权限刷新 → 已授权且总开关开着则重启提醒服务。
    // rememberUpdatedState：回调在从系统设置返回时才触发，保证拿到最新组合的 masterEnabled
    // （对齐原 rememberLauncherForActivityResult 的 rememberUpdatedState 回调语义；
    //  调用点必须传 { onDndPermissionResult() }，让读取发生在结果返回时）
    val onDndPermissionResult by rememberUpdatedState<() -> Unit> {
        dndPermissionGranted = reminderController.isDndPermissionGranted()
        rlog("permission_dnd_result", "granted=$dndPermissionGranted")
        permissionRefreshKey++
        if (dndPermissionGranted && masterEnabled) {
            reminderController.startReminderService()
            rlog("reminder_service", "start_after_dnd_grant")
        }
    }
    // 实况通知设置页返回：重读能否发布实况通知
    val onPromotedSettingsResult: () -> Unit = {
        canPostPromoted = reminderController.canPostPromotedNotifications()
    }
    // 精确闹钟授权页返回：重读精确闹钟权限 → 已授权且总开关开着则重启提醒服务（同上 rememberUpdatedState）
    val onExactAlarmResult by rememberUpdatedState<() -> Unit> {
        canScheduleExactAlarms = reminderController.canScheduleExactAlarms()
        rlog("permission_exact_alarm_result", "granted=$canScheduleExactAlarms")
        if (canScheduleExactAlarms && masterEnabled) {
            reminderController.startReminderService()
            rlog("reminder_service", "start_after_exact_alarm_grant")
        }
    }

    LaunchedEffect(masterEnabled) {
        rlog("page_state_snapshot") {
            "master=$masterEnabled pre=$preClassReminder next=$nextDayReminder " +
                "inClass=$inClassEnabled dnd=$classDndEnabled dndMode=$classDndMode " +
                "island=$islandNotification preMin=$preClassReminderMinutes " +
                "nextTime=${pad2(nextDayReminderHour, nextDayReminderMinute)}"
        }
        if (masterEnabled) {
            isIgnoringBattery = reminderController.isIgnoringBatteryOptimizations()
            canPostPromoted = reminderController.canPostPromotedNotifications()
            canScheduleExactAlarms = reminderController.canScheduleExactAlarms()
            shizukuRunning = reminderController.isShizukuRunning()
            shizukuAuthorized = reminderController.isShizukuAuthorized()
            isIslandSupported = reminderController.isIslandSupported()
            showIslandCard = isIslandSupported || reminderController.supportsLiveActivity()
            dndPermissionGranted = reminderController.isDndPermissionGranted()
            notificationGranted = reminderController.hasNotificationPermission()
            // Clear leftover state on devices without any live-container capability.
            // iOS retains the switch through supportsLiveActivity.
            if (!isIslandSupported && !reminderController.supportsLiveActivity() && islandNotification) {
                settingsViewModel.setIslandNotification(false)
            }
        }
    }

    LaunchedEffect(permissionRefreshKey) {
        if (permissionRefreshKey > 0) {
            isIgnoringBattery = reminderController.isIgnoringBatteryOptimizations()
            canPostPromoted = reminderController.canPostPromotedNotifications()
            canScheduleExactAlarms = reminderController.canScheduleExactAlarms()
            dndPermissionGranted = reminderController.isDndPermissionGranted()
            notificationGranted = reminderController.hasNotificationPermission()
            // 授权状态变化后重新对账，保证开关与系统勿扰状态一致
            reminderController.startReminderService()
        }
    }

    var pendingPermissionAction by remember { mutableStateOf<Boolean?>(null) }
    // 通知权限弹窗返回（body 只读状态持有者，逐字保留原 launcher 回调逻辑）
    val onNotificationPermissionResult: (Boolean) -> Unit = { granted ->
        rlog("permission_notification_result", "granted=$granted pending=$pendingPermissionAction")
        notificationGranted = granted
        if (granted) {
            reminderController.startReminderService()
            pendingPermissionAction?.let { enable ->
                rlog("permission_notification_apply_pending", "enable=$enable")
                settingsViewModel.setPreClassReminder(enable)
                settingsViewModel.setNextDayReminder(enable)
                if (!enable) {
                    disableMasterDependentSwitches()
                }
                if (enable) {
                    reminderController.startReminderService()
                    rlog("reminder_service", "start_after_notif_grant")
                } else {
                    reminderController.stopReminderService()
                    rlog("reminder_service", "stop_after_notif_grant")
                }
            }
            pendingPermissionAction = null
        } else {
            pendingPermissionAction = null
            rlog("permission_notification_denied")
            showToast("需要通知权限才能使用课程提醒功能")
        }
    }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {}
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                        .overScrollVertical()
                        .scrollEndHaptic(
                            hapticFeedbackType = HapticFeedbackType.TextHandleMove
                        )
                        .collapsibleTopInset(scrollBehavior)
                        .then(
                            scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                        ),
                    contentPadding = PaddingValues(
                        start = tabletHorizontalPadding,
                        top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight +
                            (if (isTablet) 24.dp else 12.dp),
                        end = tabletHorizontalPadding,
                        bottom = 120.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 开启提醒
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            val masterEnabled = preClassReminder || nextDayReminder
                            SwitchPreference(
                                title = "开启提醒",
                                checked = masterEnabled,
                                onCheckedChange = {
                                    val hasPermission = reminderController.hasNotificationPermission()
                                    if (!hasPermission) {
                                        rlog("master_switch_permission_blocked", "want=$it")
                                        pendingPermissionAction = it
                                        reminderController.requestNotificationPermission(onNotificationPermissionResult)
                                        return@SwitchPreference
                                    }
                                    rlog("master_switch", "on=$it")
                                    settingsViewModel.setPreClassReminder(it)
                                    settingsViewModel.setNextDayReminder(it)
                                    if (!it) {
                                        disableMasterDependentSwitches()
                                    }
                                    if (it) {
                                        reminderController.startReminderService()
                                        rlog("reminder_service", "start")
                                    } else {
                                        reminderController.stopReminderService()
                                        rlog("reminder_service", "stop")
                                    }
                                }
                            )
                        }
                    }

                    // 提醒详情
                    item {
                        val masterEnabled = preClassReminder || nextDayReminder
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            SwitchPreference(
                                title = "下节课提醒",
                                summary = "提供下节上课时间、地点等信息，和上课勿扰等操作建议",
                                checked = preClassReminder,
                                enabled = masterEnabled,
                                onCheckedChange = {
                                    val hasPermission = reminderController.hasNotificationPermission()
                                    if (!hasPermission) {
                                        rlog("preclass_permission_blocked", "want=$it")
                                        pendingPermissionAction = it
                                        reminderController.requestNotificationPermission(onNotificationPermissionResult)
                                        return@SwitchPreference
                                    }
                                    rlog("preclass_switch", "on=$it")
                                    settingsViewModel.setPreClassReminder(it)
                                    reminderController.startReminderService()
                                    rlog("reminder_service", "start_after_preclass")
                                }
                            )
                            AnimatedVisibility(
                                visible = preClassReminder,
                                enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                            ) {
                                ArrowPreference(
                                    title = "提前提醒时间",
                                    endActions = {
                                        Text(
                                            text = "${preClassReminderMinutes}分钟",
                                            fontSize = 14.5.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    holdDownState = showMinutesDialog,
                                    onClick = {
                                        rlog("preclass_minutes_dialog_open", "cur=${preClassReminderMinutes}min")
                                        tempMinutes = preClassReminderMinutes
                                        showMinutesDialog = true
                                    }
                                )
                            }
                            SwitchPreference(
                                title = "次日课程提醒",
                                summary = "提供明天课程、首节时间、地点等信息，和定闹钟等操作建议",
                                checked = nextDayReminder,
                                enabled = masterEnabled,
                                onCheckedChange = {
                                    val hasPermission = reminderController.hasNotificationPermission()
                                    if (!hasPermission) {
                                        rlog("nextday_permission_blocked", "want=$it")
                                        pendingPermissionAction = it
                                        reminderController.requestNotificationPermission(onNotificationPermissionResult)
                                        return@SwitchPreference
                                    }
                                    rlog("nextday_switch", "on=$it")
                                    settingsViewModel.setNextDayReminder(it)
                                    reminderController.startReminderService()
                                    rlog("reminder_service", "start_after_nextday")
                                }
                            )
                            AnimatedVisibility(
                                visible = nextDayReminder,
                                enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                            ) {
                                ArrowPreference(
                                    title = "提醒时间",
                                    endActions = {
                                        Text(
                                            text = pad2(nextDayReminderHour, nextDayReminderMinute),
                                            fontSize = 14.5.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    holdDownState = showTimeDialog,
                                    onClick = {
                                        rlog("nextday_time_dialog_open") {
                                            "cur=${pad2(nextDayReminderHour, nextDayReminderMinute)}"
                                        }
                                        tempHour = nextDayReminderHour
                                        tempMinute = nextDayReminderMinute
                                        showTimeDialog = true
                                    }
                                )
                            }
                            SwitchPreference(
                                title = "课中提醒",
                                summary = "上课中展示距离下课时间与课程状态",
                                checked = inClassEnabled,
                                enabled = masterEnabled,
                                onCheckedChange = {
                                    rlog("inclass_switch", "on=$it")
                                    inClassEnabled = it
                                    reminderPrefs.edit { putBoolean(ReminderController.KEY_IN_CLASS, it) }
                                }
                            )
                            AnimatedVisibility(
                                visible = masterEnabled && inClassEnabled,
                                enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                            ) {
                                ArrowPreference(
                                    title = "提醒时机",
                                    endActions = {
                                        Text(
                                            text = if (inClassTimingMode == ReminderController.IN_CLASS_TIMING_FULL) {
                                                "全程"
                                            } else {
                                                "距下课 ${inClassLeadMinutes} 分钟"
                                            },
                                            fontSize = 14.5.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    holdDownState = showInClassTimingDialog,
                                    onClick = {
                                        rlog(
                                            "inclass_timing_dialog_open",
                                            "mode=$inClassTimingMode lead=${inClassLeadMinutes}min"
                                        )
                                        tempInClassTimingMode = inClassTimingMode
                                        tempInClassLeadMinutes = inClassLeadMinutes
                                        showInClassTimingDialog = true
                                    }
                                )
                            }
                            SwitchPreference(
                                title = "自动开启勿扰",
                                summary = if (!dndPermissionGranted) {
                                    "需要先授予勿扰权限才能自动开启勿扰"
                                } else {
                                    "上课时自动开启勿扰，下课后自动恢复"
                                },
                                checked = classDndEnabled,
                                // 仅受总开关约束：无权限时仍可点开，会跳到授权卡片引导
                                enabled = masterEnabled,
                                onCheckedChange = { enable ->
                                    rlog("dnd_switch", "on=$enable perm=$dndPermissionGranted")
                                    settingsViewModel.setClassDndEnabled(enable)
                                    if (enable && !reminderController.isDndPermissionGranted()) {
                                        rlog("dnd_permission_launch")
                                        reminderController.openNotificationAccessSettings { onDndPermissionResult() }
                                    }
                                    reminderController.startReminderService()
                                    rlog("reminder_service", "start_after_dnd")
                                }
                            )
                            // 总开关开启时档位始终可见：通知/超级岛上的「上课勿扰」按钮也能切换勿扰开关
                            // 总开关关闭时整页子项折叠，档位一并隐藏
                            val selectMode: (Int) -> Unit = { mode ->
                                rlog("dnd_mode_select", "mode=$mode")
                                settingsViewModel.setClassDndMode(mode)
                                // 三个档位都要「免打扰访问权限」：静音档走 setRingerMode，
                                // Android N 起同样被判定为切换勿扰，未授权会直接抛异常
                                if (!reminderController.isDndPermissionGranted()) {
                                    rlog("dnd_mode_permission_needed", "mode=$mode")
                                    showToast("该档位需要勿扰权限，已为你打开授权页")
                                    reminderController.openNotificationAccessSettings { onDndPermissionResult() }
                                }
                                reminderController.startReminderService()
                                rlog("reminder_service", "start_after_dnd_mode")
                            }
                            val modeEntry = remember(classDndMode) {
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "完全勿扰 (DND)",
                                            summary = "屏蔽全部通知与铃声",
                                            selected = classDndMode == ReminderController.MODE_DND,
                                            onClick = { selectMode(ReminderController.MODE_DND) }
                                        ),
                                        DropdownItem(
                                            text = "静音模式 (SILENT)",
                                            summary = "关闭铃声与振动",
                                            selected = classDndMode == ReminderController.MODE_SILENT,
                                            onClick = { selectMode(ReminderController.MODE_SILENT) }
                                        ),
                                        DropdownItem(
                                            text = "勿扰模式 (PRIORITY)",
                                            summary = "只放行优先通知",
                                            selected = classDndMode == ReminderController.MODE_PRIORITY,
                                            onClick = { selectMode(ReminderController.MODE_PRIORITY) }
                                        )
                                    )
                                )
                            }
                            AnimatedVisibility(
                                visible = masterEnabled,
                                enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                            ) {
                                OverlayDropdownMenu(
                                    title = "勿扰模式档位",
                                    entry = modeEntry,
                                    collapseOnSelection = true,
                                    enabled = masterEnabled,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                            }
                        }
                    }

                    // Live container card: HyperOS super island or iOS Live Activity.
                    if (showIslandCard && masterEnabled) {
                        item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SwitchPreference(
                                    title = if (isHyperIsland) "小米超级岛" else "灵动岛",
                                    summary = when {
                                        islandNotification && isHyperIsland ->
                                            "已开启，课程提醒将以超级岛样式显示"
                                        islandNotification ->
                                            "已开启，点击上课提醒将启动实时活动"
                                        isHyperIsland -> "关闭后使用实时动态通知"
                                        else -> "关闭后点击提醒仅打开应用"
                                    },
                                    checked = islandNotification,
                                    onCheckedChange = {
                                        rlog("island_switch", "on=$it hyper=$isHyperIsland")
                                        settingsViewModel.setIslandNotification(it)
                                    }
                                )
                                AnimatedVisibility(
                                    visible = islandNotification,
                                    enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .padding(bottom = 16.dp)
                                    ) {
                                        // Shizuku state: HyperOS-only, omitted on iOS.
                                        if (isHyperIsland) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Shizuku 状态",
                                                fontSize = 14.sp,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                text = when {
                                                    !shizukuRunning -> "未运行"
                                                    !shizukuAuthorized -> "未授权"
                                                    else -> "已就绪"
                                                },
                                                fontSize = 14.sp,
                                                color = when {
                                                    !shizukuRunning -> ComposeColor(0xFFFF6B6B)
                                                    !shizukuAuthorized -> ComposeColor(
                                                        0xFFFFB347
                                                    )

                                                    else -> ComposeColor(0xFF4CAF50)
                                                }
                                            )
                                        }
                                        if (!shizukuRunning) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "请安装并启动 Shizuku 应用",
                                                style = MiuixTheme.textStyles.body2,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            )
                                        } else if (!shizukuAuthorized) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            TextButton(
                                                text = "授权 Shizuku",
                                                onClick = {
                                                    rlog("shizuku_authorize_request")
                                                    reminderController.requestShizukuPermission { granted ->
                                                        rlog("shizuku_authorize_result", "granted=$granted")
                                                        shizukuAuthorized = granted
                                                        if (!granted) {
                                                            showToast("Shizuku 授权失败")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                        }
                                }
                            }
                        }
                    }
                    }


                    // 缩略态显示设置
                    if (masterEnabled) {
                        item {

                        val liveRightOptions = listOf(
                            DropdownItem(
                                text = "课程名称",
                                selected = liveRightMode == 0,
                                onClick = {
                                    rlog("live_right_mode", "mode=0 课程名称")
                                    liveRightMode = 0
                                    reminderPrefs.edit { putInt("live_right_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = "上课地点",
                                selected = liveRightMode == 1,
                                onClick = {
                                    rlog("live_right_mode", "mode=1 上课地点")
                                    liveRightMode = 1
                                    reminderPrefs.edit { putInt("live_right_mode", 1) }
                                }
                            ),
                            DropdownItem(
                                text = "倒计时",
                                selected = liveRightMode == 2,
                                onClick = {
                                    rlog("live_right_mode", "mode=2 倒计时")
                                    liveRightMode = 2
                                    reminderPrefs.edit { putInt("live_right_mode", 2) }
                                }
                            ),
                        )

                        val islandLeftOptions = listOf(
                            DropdownItem(
                                text = "课程名称",
                                selected = islandLeftMode == 0,
                                onClick = {
                                    rlog("island_left_mode", "mode=0 课程名称")
                                    islandLeftMode = 0
                                    reminderPrefs.edit { putInt("island_left_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = "上课地点",
                                selected = islandLeftMode == 1,
                                onClick = {
                                    rlog("island_left_mode", "mode=1 上课地点")
                                    islandLeftMode = 1
                                    reminderPrefs.edit { putInt("island_left_mode", 1) }
                                }
                            ),
                            DropdownItem(
                                text = "倒计时",
                                selected = islandLeftMode == 2,
                                onClick = {
                                    rlog("island_left_mode", "mode=2 倒计时")
                                    islandLeftMode = 2
                                    reminderPrefs.edit { putInt("island_left_mode", 2) }
                                }
                            ),
                        )

                        val islandRightOptions = listOf(
                            DropdownItem(
                                text = "课程名称",
                                selected = islandRightMode == 0,
                                onClick = {
                                    rlog("island_right_mode", "mode=0 课程名称")
                                    islandRightMode = 0
                                    reminderPrefs.edit { putInt("island_right_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = "上课地点",
                                selected = islandRightMode == 1,
                                onClick = {
                                    rlog("island_right_mode", "mode=1 上课地点")
                                    islandRightMode = 1
                                    reminderPrefs.edit { putInt("island_right_mode", 1) }
                                }
                            ),
                            DropdownItem(
                                text = "倒计时",
                                selected = islandRightMode == 2,
                                onClick = {
                                    rlog("island_right_mode", "mode=2 倒计时")
                                    islandRightMode = 2
                                    reminderPrefs.edit { putInt("island_right_mode", 2) }
                                }
                            ),
                        )

                        val islandAodOptions = listOf(
                            DropdownItem(
                                text = "课程名称",
                                selected = islandAodMode == 0,
                                onClick = {
                                    rlog("island_aod_mode", "mode=0 课程名称")
                                    islandAodMode = 0
                                    reminderPrefs.edit { putInt("island_aod_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = "上课地点",
                                selected = islandAodMode == 1,
                                onClick = {
                                    rlog("island_aod_mode", "mode=1 上课地点")
                                    islandAodMode = 1
                                    reminderPrefs.edit { putInt("island_aod_mode", 1) }
                                }
                            ),
                        )

                        // 课中岛缩略态右侧：与课前岛的左右两侧互不干扰
                        val islandInClassRightOptions = listOf(
                            DropdownItem(
                                text = "正在上课",
                                selected = islandInClassRightMode == 0,
                                onClick = {
                                    rlog("island_inclass_right_mode", "mode=0 正在上课")
                                    islandInClassRightMode = 0
                                    reminderPrefs.edit { putInt("island_in_class_right_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = "倒计时",
                                selected = islandInClassRightMode == 1,
                                onClick = {
                                    rlog("island_inclass_right_mode", "mode=1 倒计时")
                                    islandInClassRightMode = 1
                                    reminderPrefs.edit { putInt("island_in_class_right_mode", 1) }
                                }
                            ),
                        )

                        if (!showSlotCard) {
                            // 无生效的 live container：普通通知槽位卡（live_right_mode 生效的地方）
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                            ) {
                                OverlayDropdownMenu(
                                    title = "实时动态右侧",
                                    entry = DropdownEntry(items = liveRightOptions),
                                    collapseOnSelection = true,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                            }
                        } else {
                            // Slot customization for both HyperOS and iOS Live Activity.
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                            ) {
                                OverlayDropdownMenu(
                                    title = if (isHyperIsland) "超级岛左侧" else "灵动岛左侧",
                                    entry = DropdownEntry(items = islandLeftOptions),
                                    collapseOnSelection = true,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                                OverlayDropdownMenu(
                                    title = if (isHyperIsland) "超级岛右侧" else "灵动岛右侧",
                                    entry = DropdownEntry(items = islandRightOptions),
                                    collapseOnSelection = true,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                                if (inClassEnabled || !isHyperIsland) {
                                    OverlayDropdownMenu(
                                        title = if (isHyperIsland) "课中岛右侧" else "课中灵动岛右侧",
                                        entry = DropdownEntry(items = islandInClassRightOptions),
                                        collapseOnSelection = true,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                        dropdownColors = liquidGlassDropdownColors,
                                    )
                                }
                                if (isHyperIsland) {
                                    OverlayDropdownMenu(
                                        title = "息屏显示",
                                        summary = "全天候显示时无效",
                                        entry = DropdownEntry(items = islandAodOptions),
                                        collapseOnSelection = true,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                        dropdownColors = liquidGlassDropdownColors,
                                    )
                                }
                            }
                        }
                    }

                    // 上课地点精简：缩略态由系统按自己的宽度排版，超出部分从尾部丢，
                    // 丢掉的正好是「B3027」这种最要紧的房号。这里在交给系统之前先按用户规则缩短。
                    // iOS 的 Live Activity 槽位在 Swift 侧拼装，规则还没接过去，先不给点了没反应的开关。
                    if (!reminderController.supportsLiveActivity()) {
                        item {
                            val classroomLabelRule = ClassroomLabelRule(
                                mode = classroomLabelMode,
                                pattern = classroomLabelPattern,
                                replacement = classroomLabelReplacement,
                                tailKeepEnabled = classroomLabelTailKeep,
                                slotWidth = classroomLabelSlotWidth,
                                abbreviations = classroomLabelAbbreviations,
                            )
                            val customRuleSummary = when {
                                classroomLabelPattern.isBlank() -> "还没选规则，现在按原文显示"
                                classroomLabelReplacement.isEmpty() ->
                                    "把「$classroomLabelPattern」找到的内容删掉"
                                else -> "把「$classroomLabelPattern」换成「$classroomLabelReplacement」"
                            }
                            val classroomLabelOptions = listOf(
                                DropdownItem(
                                    text = "原样显示",
                                    summary = "课表里怎么写就怎么显示",
                                    selected = classroomLabelMode == ClassroomLabel.MODE_RAW,
                                    onClick = {
                                        rlog("classroom_label_mode", "mode=0 原样")
                                        classroomLabelMode = ClassroomLabel.MODE_RAW
                                        reminderPrefs.edit {
                                            putInt(ClassroomLabel.KEY_MODE, ClassroomLabel.MODE_RAW)
                                        }
                                    }
                                ),
                                DropdownItem(
                                    text = "自动精简",
                                    summary = "把「第五教学楼」这类常见楼名缩成「五教」，再去掉「××校区」前缀和多余空格；本校特有的叫法可在「楼名缩写」里补充匹配",
                                    selected = classroomLabelMode == ClassroomLabel.MODE_AUTO,
                                    onClick = {
                                        rlog("classroom_label_mode", "mode=1 自动精简")
                                        classroomLabelMode = ClassroomLabel.MODE_AUTO
                                        reminderPrefs.edit {
                                            putInt(ClassroomLabel.KEY_MODE, ClassroomLabel.MODE_AUTO)
                                        }
                                    }
                                ),
                                DropdownItem(
                                    text = "自定义正则",
                                    summary = "自己定改写规则，里面有现成的常用规则可选",
                                    selected = classroomLabelMode == ClassroomLabel.MODE_CUSTOM,
                                    onClick = {
                                        rlog("classroom_label_mode", "mode=2 自定义正则")
                                        classroomLabelMode = ClassroomLabel.MODE_CUSTOM
                                        reminderPrefs.edit {
                                            putInt(ClassroomLabel.KEY_MODE, ClassroomLabel.MODE_CUSTOM)
                                        }
                                        tempClassroomPattern = classroomLabelPattern
                                        tempClassroomReplacement = classroomLabelReplacement
                                        showClassroomRuleDialog = true
                                    }
                                ),
                            )
                            // 保尾部档位：只有打开开关才有意义。
                            // 「半角」对多数人没有画面感，每档都拿用户自己的第一间教室
                            // 算一遍，挑档位时看到的是实际显示结果。
                            val widthSample = previewClassrooms.firstOrNull()
                            val tailKeepWidthOptions = ClassroomLabel.SLOT_WIDTH_PRESETS.map { width ->
                                val sampleShown = widthSample?.let { raw ->
                                    ClassroomLabel.apply(
                                        raw,
                                        ClassroomLabelRule(
                                            mode = classroomLabelMode,
                                            pattern = classroomLabelPattern,
                                            replacement = classroomLabelReplacement,
                                            tailKeepEnabled = true,
                                            slotWidth = width,
                                            abbreviations = classroomLabelAbbreviations,
                                        )
                                    )
                                }
                                DropdownItem(
                                    text = if (width == ClassroomLabel.DEFAULT_SLOT_WIDTH) {
                                        "$width 格（默认）"
                                    } else {
                                        "$width 格"
                                    },
                                    summary = buildString {
                                        if (sampleShown != null && sampleShown != widthSample) {
                                            append("你的「$widthSample」会显示成「$sampleShown」")
                                        } else if (sampleShown != null) {
                                            append("你的「$widthSample」放得下，不用截")
                                        } else {
                                            append("课表里填了地点就能在这里看到实际效果")
                                        }
                                        when (width) {
                                            ClassroomLabel.DEFAULT_SLOT_WIDTH ->
                                                append("\n多数机型的岛差不多这么宽，不确定就用它")
                                            ClassroomLabel.SLOT_WIDTH_PRESETS.first() ->
                                                append("\n最窄，基本只留得下房号")
                                            ClassroomLabel.SLOT_WIDTH_PRESETS.last() ->
                                                append("\n最宽，留字最多；超出部分系统仍会再截")
                                        }
                                    },
                                    selected = classroomLabelSlotWidth == width,
                                    onClick = {
                                        rlog("classroom_label_width", "width=$width")
                                        classroomLabelSlotWidth = width
                                        reminderPrefs.edit {
                                            putInt(ClassroomLabel.KEY_SLOT_WIDTH, width)
                                        }
                                    }
                                )
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = PaddingValues(0.dp)
                            ) {
                                OverlayDropdownMenu(
                                    title = "上课地点精简",
                                    summary = classroomLabelCardSummary(
                                        mode = classroomLabelMode,
                                        tailKeep = classroomLabelTailKeep,
                                    ),
                                    entry = DropdownEntry(items = classroomLabelOptions),
                                    collapseOnSelection = true,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                                if (classroomLabelMode == ClassroomLabel.MODE_CUSTOM) {
                                    ArrowPreference(
                                        title = "自定义规则",
                                        summary = customRuleSummary,
                                        onClick = {
                                            rlog("classroom_label_dialog_open", customRuleSummary)
                                            tempClassroomPattern = classroomLabelPattern
                                            tempClassroomReplacement = classroomLabelReplacement
                                            showClassroomRuleDialog = true
                                        }
                                    )
                                }
                                SwitchPreference(
                                    title = "太长的地点保留后半段",
                                    // 与精简模式互不影响：模式决定文字怎么改，开关决定放不下时怎么办
                                    summary = if (classroomLabelTailKeep) {
                                        "放不下时先压楼名，房号不动；楼名太长才切掉开头"
                                    } else {
                                        "关闭时一个字都不删，系统会按自己的宽度截掉末尾，房号可能丢"
                                    },
                                    checked = classroomLabelTailKeep,
                                    onCheckedChange = {
                                        rlog("classroom_label_tail_keep", "on=$it")
                                        classroomLabelTailKeep = it
                                        reminderPrefs.edit {
                                            putBoolean(ClassroomLabel.KEY_TAIL_KEEP, it)
                                        }
                                    }
                                )
                                if (classroomLabelTailKeep) {
                                    OverlayDropdownMenu(
                                        title = "地点最多能占几格",
                                        summary = buildString {
                                            append("按你的设备调；拿不准就用默认")
                                            val widthSample = previewClassrooms.firstOrNull()
                                            if (widthSample != null) {
                                                append("\n当前 $classroomLabelSlotWidth 格：")
                                                append(
                                                    ClassroomLabel.apply(
                                                        widthSample,
                                                        ClassroomLabelRule(
                                                            mode = classroomLabelMode,
                                                            pattern = classroomLabelPattern,
                                                            replacement = classroomLabelReplacement,
                                                            tailKeepEnabled = true,
                                                            slotWidth = classroomLabelSlotWidth,
                                                            abbreviations = classroomLabelAbbreviations,
                                                        )
                                                    )
                                                )
                                            }
                                        },
                                        entry = DropdownEntry(items = tailKeepWidthOptions),
                                        collapseOnSelection = true,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                        dropdownColors = liquidGlassDropdownColors,
                                    )
                                }
                                ArrowPreference(
                                    title = "楼名缩写",
                                    summary = if (userAbbrevCount > 0) {
                                        "已加 $userAbbrevCount 条自己的写法；内置的「第五教学楼→五教」一类一直生效"
                                    } else {
                                        "内置约 20 条常见楼名，不设置也能用；本校特有叫法在这里补充匹配，或让 AI 按课表生成"
                                    },
                                    onClick = {
                                        rlog("classroom_label_abbrev_open", "count=$userAbbrevCount")
                                        tempAbbreviations = classroomLabelAbbreviations
                                        showAbbrevDialog = true
                                    }
                                )
                                if (classroomLabelMode != ClassroomLabel.MODE_RAW || classroomLabelTailKeep) {
                                    ClassroomLabelPreview(
                                        rule = classroomLabelRule,
                                        samples = previewClassrooms,
                                        // 超宽时直接告诉用户去开哪个开关，不只报一个数字
                                        tailKeepActionLabel = "太长的地点保留后半段",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .padding(bottom = 16.dp)
                                    )
                                }
                                ArrowPreference(
                                    title = "这几项怎么配合",
                                    summary = "四项设置之间的关系，以及你现在用的是哪一套",
                                    onClick = {
                                        rlog("classroom_label_usage_open")
                                        showUsageDialog = true
                                    }
                                )
                            }
                        }
                    }
                    }

                    // 权限设置
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = "通知权限",
                                    summary = "用于接收课程提醒通知",
                                    endActions = {
                                        Text(
                                            text = if (notificationGranted) "已授权" else "未授权",
                                            fontSize = 14.5.sp,
                                            color = if (notificationGranted) {
                                                ComposeColor(0xFF4CAF50)
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            }
                                        )
                                    },
                                    onClick = {
                                        rlog("permission_notification_click", "granted=$notificationGranted")
                                        if (!notificationGranted) {
                                            rlog("permission_notification_launch")
                                            reminderController.requestNotificationPermission(onNotificationPermissionResult)
                                        }
                                    }
                                )
                                if (reminderController.supportsPromotedNotifications) {
                                    ArrowPreference(
                                        title = "实况通知权限",
                                        summary = "在状态栏和锁屏实时显示课程倒计时",
                                        endActions = {
                                            Text(
                                                text = if (canPostPromoted) "已授权" else "未授权",
                                                fontSize = 14.5.sp,
                                                color = if (canPostPromoted) {
                                                    ComposeColor(0xFF4CAF50)
                                                } else {
                                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                                }
                                            )
                                        },
                                        onClick = {
                                            rlog("permission_promoted_click", "granted=$canPostPromoted")
                                            if (!canPostPromoted) {
                                                rlog("permission_promoted_launch")
                                                // 首选「实况通知管理页」、失败退回「应用通知设置」的兜底在 Android 包装层内逐字保留
                                                reminderController.openPromotedNotificationSettings(onPromotedSettingsResult)
                                            }
                                        }
                                    )
                                }
                                ArrowPreference(
                                    title = "精确闹钟权限",
                                    summary = "确保提醒准时触发，不受省电策略影响",
                                    endActions = {
                                        Text(
                                            text = if (canScheduleExactAlarms) "已授权" else "未授权",
                                            fontSize = 14.5.sp,
                                            color = if (canScheduleExactAlarms) {
                                                ComposeColor(0xFF4CAF50)
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            }
                                        )
                                    },
                                    onClick = {
                                        rlog("permission_exact_alarm_click", "granted=$canScheduleExactAlarms")
                                        if (!canScheduleExactAlarms) {
                                            rlog("permission_exact_alarm_launch")
                                            reminderController.openExactAlarmSettings { onExactAlarmResult() }
                                        }
                                    }
                                )
                                ArrowPreference(
                                    title = "电池优化",
                                    summary = "关闭电池优化以确保提醒准时送达",
                                    endActions = {
                                        Text(
                                            text = if (isIgnoringBattery) "已关闭" else "未关闭",
                                            fontSize = 14.5.sp,
                                            color = if (isIgnoringBattery) {
                                                ComposeColor(0xFF4CAF50)
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            }
                                        )
                                    },
                                    onClick = {
                                        rlog("permission_battery_click", "ignoring=$isIgnoringBattery")
                                        if (!isIgnoringBattery) {
                                            rlog("permission_battery_launch")
                                            // 首选「请求忽略电池优化」、失败退回「白名单列表」的兜底在 Android 包装层内逐字保留
                                            reminderController.openBatteryOptimizationSettings(onBatterySettingsResult)
                                        }
                                    }
                                )
                                ArrowPreference(
                                    title = "勿扰权限",
                                    summary = "用于上课时开启勿扰或静音",
                                    endActions = {
                                        Text(
                                            text = if (dndPermissionGranted) "已授权" else "未授权",
                                            fontSize = 14.5.sp,
                                            color = if (dndPermissionGranted) {
                                                ComposeColor(0xFF4CAF50)
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            }
                                        )
                                    },
                                    onClick = {
                                        rlog("permission_dnd_click", "granted=$dndPermissionGranted")
                                        if (!dndPermissionGranted) {
                                            rlog("permission_dnd_launch")
                                            try {
                                                reminderController.openNotificationAccessSettings { onDndPermissionResult() }
                                            } catch (_: Exception) {
                                                showToast("请手动在系统设置中授予勿扰权限")
                                            }
                                        }
                                    }
                                )
                                ArrowPreference(
                                    title = "自启动权限",
                                    summary = "在应用被系统清理后及时重启",
                                    endActions = {
                                        Text(
                                            text = "前往检查",
                                            fontSize = 14.5.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    onClick = {
                                        rlog("permission_autostart_click")
                                        reminderController.openAppDetailsSettings(onBatterySettingsResult)
                                    }
                                )
                            }
                        }
                    }

                }

                // 列表底部留白仍保留，给 Activity 层悬浮的测试按钮让位
            }

            // 课中提醒时机弹窗：左侧全程/距下课，右侧分钟数（全程时禁用）
            OverlayDialog(
                title = "提醒时机",
                show = showInClassTimingDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = { showInClassTimingDialog = false }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(bottom = 16.dp)
                    ) {
                        // 左侧滚轮：全程 / 距下课
                        NumberPicker(
                            value = tempInClassTimingMode,
                            onValueChange = {
                                tempInClassTimingMode = it
                            },
                            range = ReminderController.IN_CLASS_TIMING_FULL..ReminderController.IN_CLASS_TIMING_BEFORE_END,
                            visibleItemCount = 3,
                            itemHeight = 48.dp,
                            label = { mode ->
                                if (mode == ReminderController.IN_CLASS_TIMING_FULL) "全程" else "距下课"
                            },
                            textStyle = MiuixTheme.textStyles.title2,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // 右侧滚轮：距下课分钟数，全程时禁用
                        val minutesEnabled =
                            tempInClassTimingMode == ReminderController.IN_CLASS_TIMING_BEFORE_END
                        NumberPicker(
                            value = tempInClassLeadMinutes,
                            onValueChange = { if (minutesEnabled) tempInClassLeadMinutes = it },
                            range = 0..60,
                            visibleItemCount = 3,
                            itemHeight = 48.dp,
                            label = { "${it}分钟" },
                            textStyle = MiuixTheme.textStyles.title2,
                            wrapAround = true,
                            enabled = minutesEnabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = if (tempInClassTimingMode == ReminderController.IN_CLASS_TIMING_FULL) {
                            "全程显示课中进度"
                        } else {
                            "距下课 ${tempInClassLeadMinutes} 分钟开始显示"
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("inclass_timing_dialog_cancel")
                                showInClassTimingDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "确定",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog(
                                    "inclass_timing_dialog_ok",
                                    "mode=$tempInClassTimingMode lead=${tempInClassLeadMinutes}min"
                                )
                                inClassTimingMode = tempInClassTimingMode
                                inClassLeadMinutes = tempInClassLeadMinutes
                                reminderPrefs.edit {
                                    putInt(ReminderController.KEY_IN_CLASS_TIMING_MODE, tempInClassTimingMode)
                                    putInt(ReminderController.KEY_IN_CLASS_LEAD_MINUTES, tempInClassLeadMinutes)
                                }
                                showInClassTimingDialog = false
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 地点精简的自定义规则：边写边用自己课表里的地点看效果，不靠猜
            OverlayDialog(
                title = "自定义地点规则",
                summary = "按正则把课表里的地点改成岛上要显示的短文本。不想写正则，" +
                    "可以从下面的常用规则里选一条。",
                show = showClassroomRuleDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = { showClassroomRuleDialog = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 常用规则：一键填入，省掉写正则这件事
                    Text(
                        text = "常用规则",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    ClassroomLabel.CUSTOM_PRESETS.forEach { preset ->
                        ArrowPreference(
                            title = preset.title,
                            summary = "${preset.description}\n${preset.sample}",
                            onClick = {
                                tempClassroomPattern = preset.pattern
                                tempClassroomReplacement = preset.replacement
                            }
                        )
                    }
                    NativeMiuixTextField(
                        value = tempClassroomPattern,
                        onValueChange = { tempClassroomPattern = it },
                        label = "查找",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        requestFocus = false
                    )
                    NativeMiuixTextField(
                        value = tempClassroomReplacement,
                        onValueChange = { tempClassroomReplacement = it },
                        label = "替换为",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "替换为留空表示删掉找到的内容；写 \$1 表示保留第一个括号捕获到的部分。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    val patternError = ClassroomLabel.patternError(tempClassroomPattern)
                    if (patternError == null) {
                        ClassroomLabelPreview(
                            rule = ClassroomLabelRule(
                                mode = ClassroomLabel.MODE_CUSTOM,
                                pattern = tempClassroomPattern,
                                replacement = tempClassroomReplacement,
                                tailKeepEnabled = classroomLabelTailKeep,
                                slotWidth = classroomLabelSlotWidth,
                                abbreviations = classroomLabelAbbreviations,
                            ),
                            samples = previewClassrooms,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = patternError,
                            style = MiuixTheme.textStyles.body2,
                            color = ComposeColor(0xFFFF6B6B)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("classroom_label_dialog_cancel")
                                showClassroomRuleDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "确定",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("classroom_label_dialog_ok") {
                                    "$tempClassroomPattern -> $tempClassroomReplacement"
                                }
                                classroomLabelPattern = tempClassroomPattern
                                classroomLabelReplacement = tempClassroomReplacement
                                reminderPrefs.edit {
                                    putString(ClassroomLabel.KEY_PATTERN, tempClassroomPattern)
                                    putString(ClassroomLabel.KEY_REPLACEMENT, tempClassroomReplacement)
                                }
                                showClassroomRuleDialog = false
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            enabled = patternError == null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 四项设置的关系：多数人只需要知道开哪两个开关
            OverlayDialog(
                title = "这几项怎么配合",
                summary = "从上往下按需要加",
                show = showUsageDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = { showUsageDialog = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ClassroomLabelUsageItem(
                        title = "什么都不设置",
                        body = "和升级前一样。地点太长时系统自己截末尾，房号可能被截掉。",
                        sample = "新校区J02西414 → 新校区J02西4…",
                        isCurrent = classroomLabelMode == ClassroomLabel.MODE_RAW && !classroomLabelTailKeep,
                    )
                    ClassroomLabelUsageItem(
                        title = "打开「自动精简」和「保留后半段」",
                        body = "常见楼名缩成「五教」这种写法，去掉校区前缀；放不下时先压楼名，房号不丢。" +
                            "多数人开这两个就够了，不用配缩写。",
                        sample = "第五教学楼C509 → 五C509",
                        isCurrent = classroomLabelMode == ClassroomLabel.MODE_AUTO && classroomLabelTailKeep,
                    )
                    ClassroomLabelUsageItem(
                        title = "补充自己的缩写",
                        body = "内置表只认「第X教学楼」「综合楼」这类通用叫法。" +
                            "「明德楼」「格物楼」想显示得更短，在这里补充匹配；不补也能用，只是楼名会被压成一个字。",
                        sample = "明德楼A302 → 明A302（补了缩写可以是 明德A302）",
                        isCurrent = userAbbrevCount > 0,
                    )
                    ClassroomLabelUsageItem(
                        title = "用 AI 一次生成",
                        body = "课表里楼名多、一个个写麻烦时用。和「导入课表」一样，复制提示词发给任意 AI，" +
                            "再把结果粘回来。App 不联网、不用登录，生成的内容能逐条改。",
                        sample = "整张课表的楼名一次列成「原名=缩写」",
                        isCurrent = false,
                    )
                    Text(
                        text = "前两项不用设置就能用；后两项只影响本校特有楼名的显示效果。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    TextButton(
                        text = "知道了",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            rlog("classroom_label_usage_close")
                            showUsageDialog = false
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 楼名缩写：内置表管常见写法，这里管本校自己的叫法
            OverlayDialog(
                title = "楼名缩写",
                summary = "一行一条，写成「原名=缩写」。只压楼名，房间号别动。",
                show = showAbbrevDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = { showAbbrevDialog = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NativeMiuixTextField(
                        value = tempAbbreviations,
                        onValueChange = { tempAbbreviations = it },
                        minLines = 3,
                        maxLines = 10,
                        label = "第五教学楼=五教",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // AI 生成：跟「导入课表」一样，复制提示词到外部 AI，再把结果粘回来。
                    // 不接 API、不要联网权限，生成的东西用户能逐条看着改。
                    ArrowPreference(
                        title = "让 AI 按我的课表生成",
                        summary = if (promptClassrooms.isEmpty()) {
                            "课表里还没有地点，先填上课地点再来"
                        } else {
                            "复制一段提示词（已带上你课表里的 ${promptClassrooms.size} 个地点）到任意 AI，把结果粘回上面"
                        },
                        enabled = promptClassrooms.isNotEmpty(),
                        onClick = {
                            rlog("classroom_label_abbrev_prompt", "count=${promptClassrooms.size}")
                            onCopyToClipboard(ClassroomLabel.abbreviationPrompt(promptClassrooms))
                            showToast("提示词已复制，粘给 AI 后再把结果粘回这里")
                        }
                    )
                    // 粘贴回来的东西常带序号或解释句，先清一遍再展示，用户能看得出留下了什么
                    val cleaned = ClassroomLabel.sanitizeAbbreviationReply(tempAbbreviations)
                    if (cleaned != tempAbbreviations.trim()) {
                        ArrowPreference(
                            title = "整理成规范写法",
                            summary = "去掉序号和解释句，只留「原名=缩写」的行",
                            onClick = { tempAbbreviations = cleaned }
                        )
                    }
                    val parsedCount = ClassroomLabel.parseAbbreviations(tempAbbreviations).size
                    Text(
                        text = if (parsedCount > 0) {
                            "已识别 $parsedCount 条；生效效果看上面的预览"
                        } else {
                            "还没有可用的行。写成「第五教学楼=五教」这样即可"
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("classroom_label_abbrev_cancel")
                                showAbbrevDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "确定",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("classroom_label_abbrev_ok", "count=$parsedCount")
                                classroomLabelAbbreviations = tempAbbreviations
                                reminderPrefs.edit {
                                    putString(ClassroomLabel.KEY_ABBREVIATIONS, tempAbbreviations)
                                }
                                showAbbrevDialog = false
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 提前提醒分钟数弹窗
            OverlayDialog(
                title = "提前提醒时间",
                show = showMinutesDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = { showMinutesDialog = false }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NumberPicker(
                        value = tempMinutes,
                        onValueChange = { tempMinutes = it },
                        range = 1..60,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        label = { "${it}分钟" },
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("preclass_minutes_dialog_cancel")
                                showMinutesDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "确定",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("preclass_minutes_dialog_ok", "minutes=$tempMinutes")
                                settingsViewModel.setPreClassReminderMinutes(tempMinutes)
                                showMinutesDialog = false
                                reminderController.startReminderService()
                                rlog("reminder_service", "start_after_preclass_minutes")
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 次日提醒时间弹窗
            OverlayDialog(
                title = "提醒时间",
                show = showTimeDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = { showTimeDialog = false }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberPicker(
                            value = tempHour,
                            onValueChange = { tempHour = it },
                            range = 0..23,
                            visibleItemCount = 3,
                            itemHeight = 60.dp,
                            label = { pad2(it) },
                            wrapAround = true,
                            modifier = Modifier.weight(1f)
                        )
                        Text(":",
                            style = MiuixTheme.textStyles.title2,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding()
                                .offset(y = (-2).dp))
                        NumberPicker(
                            value = tempMinute,
                            onValueChange = { tempMinute = it },
                            range = 0..59,
                            visibleItemCount = 3,
                            itemHeight = 60.dp,
                            label = { pad2(it) },
                            wrapAround = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("nextday_time_dialog_cancel")
                                showTimeDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "确定",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("nextday_time_dialog_ok") {
                                    pad2(tempHour, tempMinute)
                                }
                                settingsViewModel.setNextDayReminderHour(tempHour)
                                settingsViewModel.setNextDayReminderMinute(tempMinute)
                                showTimeDialog = false
                                reminderController.startReminderService()
                                rlog("reminder_service", "start_after_nextday_time")
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

/**
 * 「这几项怎么配合」里的一条：标题、说明、一句实际效果。
 *
 * 每条都带上输入输出，比只描述机制直观。
 */
@Composable
private fun ClassroomLabelUsageItem(
    title: String,
    body: String,
    sample: String,
    isCurrent: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
            )
            if (isCurrent) {
                Text(
                    text = "当前",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = body,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = sample,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

/**
 * 地点精简设置卡那一行的说明文字：直接说清「现在会拿什么去显示」。
 *
 * 只写「原样显示 / 自动精简」这种模式名，用户仍然不知道自己的地点会变成什么样；
 * 这里把当前组合的后果讲出来，尤其是「关闭保尾部时房号可能丢」这件事。
 */
private fun classroomLabelCardSummary(mode: Int, tailKeep: Boolean): String {
    val what = when (mode) {
        ClassroomLabel.MODE_AUTO -> "显示时缩写常见楼名、去掉「××校区」前缀"
        ClassroomLabel.MODE_CUSTOM -> "显示时按你定的规则改写"
        else -> "显示时用课表原文"
    }
    val overflow = if (tailKeep) {
        "太长时先压楼名，房号留得住"
    } else {
        "太长会被系统截掉末尾，房号可能丢"
    }
    return "$what；$overflow"
}

/**
 * 地点精简预览：拿课表里的真实地点按 [rule] 算一遍。
 *
 * 结果和原文一样时只显示一条。超宽时把结论交给 [ClassroomLabel.previewHint]，
 * 只摆一个「原文 → 结果」，用户看不出算好还是不好，也不知道下一步该动哪里。
 */
@Composable
private fun ClassroomLabelPreview(
    rule: ClassroomLabelRule,
    samples: List<String>,
    modifier: Modifier = Modifier,
    tailKeepActionLabel: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (samples.isEmpty()) {
            Text(
                text = "课表里还没有填上课地点，这里没法试效果",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        } else {
            Text(
                text = "拿你课表里的地点试一下",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            samples.forEach { raw ->
                val shown = ClassroomLabel.apply(raw, rule)
                val hint = ClassroomLabel.previewHint(raw, rule, tailKeepActionLabel)
                Text(
                    text = buildString {
                        append(if (shown == raw) raw else "$raw → $shown")
                        if (hint != null) append("\n$hint")
                    },
                    style = MiuixTheme.textStyles.body2,
                    // 有问题才上色；正常结果显示为次要文字，不抢注意力
                    color = if (hint != null) {
                        ComposeColor(0xFFFFB347)
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    }
                )
            }
        }
    }
}
