/** 课程提醒设置页面 - Screen */
package com.haooz.chedule.ui.activities

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.ai.AiClient
import com.haooz.chedule.data.ai.AiResult
import com.haooz.chedule.data.model.ClassroomLabel
import com.haooz.chedule.data.model.ClassroomLabelRule
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.NoopReminderController
import com.haooz.chedule.data.platform.ReminderController
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.ReminderStr
import com.haooz.chedule.ui.basic.AiSparkleIcon
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
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

/** AI 请求超时。服务端自己上限 30s，客户端留宽一点，别先于服务端放弃。 */
private val AI_TIMEOUTS = HttpTimeouts(connectSeconds = 10, readSeconds = 40, callSeconds = 40)

/** 预览那一整块的圆角 */
private val ClassroomPreviewShape = ContinuousRoundedRectangle(16.dp)

/**
 * 精简预览的一行。
 *
 * @param raw 课表原文（也是手动改写的键）
 * @param shown 现在会显示的文本（含手动改写）
 * @param auto 没有手动改写时会显示的文本 —— 编辑框里改回它就等于取消这一条改写
 */
private data class PreviewItem(val raw: String, val shown: String, val auto: String)

/**
 * 精简预览里的一行：左边课表原文，右边通知里会显示的文本，两边等宽。
 *
 * 不管有没有被改写，左右都有内容 —— 少一边就看不出「这条被改成什么了」。
 * 有变化的右侧染主题色，扫一眼就知道哪几条动了。
 *
 * 右侧那串**点一下就能改**（[editing] 为 true 时换成输入框）：自动规则再细也有认不出的
 * 楼名，让本人直接改比堆提示词划算。改完的值按原文整串存进 [ClassroomLabel.KEY_OVERRIDES]，
 * 优先级压过正则与 AI。
 */
@Composable
private fun ClassroomPreviewRow(
    raw: String,
    shown: String,
    editing: Boolean,
    editText: String,
    onEditTextChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val changed = shown != raw
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = raw,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            tint = if (changed) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
            modifier = Modifier.size(14.dp),
        )
        if (editing) {
            val focusRequester = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            // 刚切进编辑态就聚焦并弹键盘，不用再点第二下
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
            // 首次组合时焦点回调可能先报「未聚焦」，不能据此直接提交，否则输入框刚出现就没了
            var hadFocus by remember { mutableStateOf(false) }
            BasicTextField(
                value = editText,
                onValueChange = onEditTextChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) hadFocus = true else if (hadFocus) onCommit()
                    },
                textStyle = MiuixTheme.textStyles.body2.copy(
                    color = MiuixTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                ),
                singleLine = true,
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommit() }),
            )
        } else {
            Text(
                text = shown,
                style = MiuixTheme.textStyles.body2,
                color = if (changed) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .weight(1f)
                    .clip(ContinuousRoundedRectangle(6.dp))
                    .clickable { onStartEdit() }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}

/** 两位补零（原 `%02d`；commonMain 没有字符串格式化器，用 padStart 等价实现） */
private fun pad2(n: Int): String = n.toString().padStart(2, '0')

/** `HH:mm` 两位补零（原 `%02d:%02d`） */
private fun pad2(hour: Int, minute: Int): String = "${pad2(hour)}:${pad2(minute)}"

/**
 * 「平台权限」区里可以按平台隐藏的条目。
 *
 * 没有对应系统概念的平台（鸿蒙没有精确闹钟授权、电池优化白名单、免打扰改写、自启动）
 * 不该显示一个永远「已授权」或点了没反应的入口。
 */
enum class ReminderPermissionRow {
    EXACT_ALARM,
    BATTERY_OPTIMIZATION,
    DND,
    AUTOSTART,
}

@Composable
fun CourseReminderScreen(
    settingsViewModel: SettingsViewModel = settingsViewModel(),
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    /** 提醒控制器（Android = AndroidReminderController 包装）；桌面/iOS 用 NoopReminderController */
    reminderController: ReminderController = NoopReminderController,
    /** 本平台没有对应系统概念的权限行，默认全部展示（Android/iOS 行为不变） */
    hiddenPermissionRows: Set<ReminderPermissionRow> = emptySet(),
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
    // 缩略态精简：作用范围（原始 / 地点 / 课程 / 两者）+ 精简方式（正则 / AI）
    var classroomLabelScope by remember {
        mutableIntStateOf(
            reminderPrefs.getInt(ClassroomLabel.KEY_SCOPE, ClassroomLabel.SCOPE_OFF)
                .takeIf { it in ClassroomLabel.SCOPE_OFF..ClassroomLabel.SCOPE_BOTH }
                ?: ClassroomLabel.SCOPE_OFF
        )
    }
    val classroomLabelEnabled = classroomLabelScope != ClassroomLabel.SCOPE_OFF
    var classroomLabelMethod by remember {
        mutableIntStateOf(
            reminderPrefs.getInt(ClassroomLabel.KEY_METHOD, ClassroomLabel.METHOD_REGEX)
        )
    }
    var classroomLabelAbbreviations by remember {
        mutableStateOf(reminderPrefs.getString(ClassroomLabel.KEY_ABBREVIATIONS, ""))
    }
    var showClassroomPreviewDialog by remember { mutableStateOf(false) }
    // 手动改写：在精简预览里点右侧文字改的，按「课表原文=想显示的文本」存（见 ClassroomLabel.KEY_OVERRIDES）
    var classroomLabelOverrides by remember {
        mutableStateOf(reminderPrefs.getString(ClassroomLabel.KEY_OVERRIDES, ""))
    }
    // 正在手动修改的那一行：原文 / 自动结果 / 输入中的文本（raw 为 null 表示没有在改）
    var previewEditRaw by remember { mutableStateOf<String?>(null) }
    var previewEditAuto by remember { mutableStateOf("") }
    var previewEditText by remember { mutableStateOf("") }

    /**
     * 提交预览里正在编辑的那一行。
     *
     * 「改回自动结果」和「清空」都当成取消这一条手动改写，把那一行从偏好里删掉 ——
     * 留着一条和自动结果一模一样的条目，用户以后自己也分不清哪些是他改过的。
     */
    fun commitPreviewEdit() {
        val raw = previewEditRaw ?: return
        previewEditRaw = null
        val typed = previewEditText.trim()
        val value = typed.takeIf { it.isNotEmpty() && it != previewEditAuto }
        val text = ClassroomLabel.putOverride(classroomLabelOverrides, raw, value)
        if (text == classroomLabelOverrides) return
        classroomLabelOverrides = text
        reminderPrefs.edit { putString(ClassroomLabel.KEY_OVERRIDES, text) }
        rlog("classroom_label_override", if (value == null) "clear" else "set")
    }

    // AI 精简请求中：按钮换成转圈并禁用，避免连点重复计费
    var aiShrinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 预览用课表里全部的真实地点：能看出自己学校的地名会被改成什么样，比固定样例有用。
    // CourseRepository 是单例且带课程缓存，这里只在页面进入时取一次。
    // 缩略态要精简两类东西：课程名与地点，预览也两者都列
    val previewClassrooms = remember {
        val repository = CourseRepository()
        repository.getCoursesForSchedule(repository.getCurrentScheduleId())
            .map { it.classroom.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
    val previewCourses = remember {
        val repository = CourseRepository()
        repository.getCoursesForSchedule(repository.getCurrentScheduleId())
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
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
    val dndSupported = reminderController.supportsDnd()
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
            showToast(ReminderStr.notificationPermissionRequired())
        }
    }

    val backgroundColor = MiuixTheme.colorScheme.surface
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
                            pageTopExtra(isTablet, hasSmallTitle = false),
                        end = tabletHorizontalPadding,
                        bottom = 120.dp
                    ),
                    // 间距由每张卡自己的 bottom padding 提供：条件卡收起时要连间距一起收掉，
                    // 用 spacedBy 的话空 item 也会白占 12dp
                    verticalArrangement = Arrangement.Top
                ) {
                    // 开启提醒
                    item(key = "reminder_master") {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            val masterEnabled = preClassReminder || nextDayReminder
                            SwitchPreference(
                                title = ReminderStr.enableReminders(),
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
                    item(key = "reminder_timing") {
                        val masterEnabled = preClassReminder || nextDayReminder
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            SwitchPreference(
                                title = ReminderStr.preClassReminder(),
                                summary = ReminderStr.preClassReminderSummary(),
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
                            CardExpand(visible = preClassReminder) {
                                ArrowPreference(
                                    title = ReminderStr.reminderLeadTime(),
                                    endActions = {
                                        Text(
                                            text = ReminderStr.minutesN(preClassReminderMinutes),
                                            fontSize = 14.5.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            textAlign = TextAlign.End,
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
                                title = ReminderStr.nextDayReminder(),
                                summary = ReminderStr.nextDayReminderSummary(),
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
                            CardExpand(visible = nextDayReminder) {
                                ArrowPreference(
                                    title = ReminderStr.reminderTime(),
                                    endActions = {
                                        Text(
                                            text = pad2(nextDayReminderHour, nextDayReminderMinute),
                                            fontSize = 14.5.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            textAlign = TextAlign.End,
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
                                title = ReminderStr.inClassReminder(),
                                summary = ReminderStr.inClassReminderSummary(),
                                checked = inClassEnabled,
                                enabled = masterEnabled,
                                onCheckedChange = {
                                    rlog("inclass_switch", "on=$it")
                                    inClassEnabled = it
                                    reminderPrefs.edit { putBoolean(ReminderController.KEY_IN_CLASS, it) }
                                }
                            )
                            CardExpand(visible = masterEnabled && inClassEnabled) {
                                ArrowPreference(
                                    title = ReminderStr.reminderTiming(),
                                    endActions = {
                                        Text(
                                            text = if (inClassTimingMode == ReminderController.IN_CLASS_TIMING_FULL) {
                                                ReminderStr.inClassTimingFull()
                                            } else {
                                                ReminderStr.beforeEndN(inClassLeadMinutes)
                                            },
                                            fontSize = 14.5.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            textAlign = TextAlign.End,
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
                            if (dndSupported) SwitchPreference(
                                title = ReminderStr.autoDnd(),
                                summary = if (!dndPermissionGranted) {
                                    ReminderStr.autoDndNeedsPermission()
                                } else {
                                    ReminderStr.autoDndSummary()
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
                            val selectMode: (Int) -> Unit = { mode ->
                                rlog("dnd_mode_select", "mode=$mode")
                                settingsViewModel.setClassDndMode(mode)
                                // 三个档位都要「免打扰访问权限」：静音档走 setRingerMode，
                                // Android N 起同样被判定为切换勿扰，未授权会直接抛异常
                                if (!reminderController.isDndPermissionGranted()) {
                                    rlog("dnd_mode_permission_needed", "mode=$mode")
                                    showToast(ReminderStr.dndModeNeedsPermission())
                                    reminderController.openNotificationAccessSettings { onDndPermissionResult() }
                                }
                                reminderController.startReminderService()
                                rlog("reminder_service", "start_after_dnd_mode")
                            }
                            val modeEntry = remember(classDndMode, AppLanguage.resolved) {
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = ReminderStr.dndModeTotalSilence(),
                                            summary = ReminderStr.dndModeTotalSilenceSummary(),
                                            selected = classDndMode == ReminderController.MODE_DND,
                                            onClick = { selectMode(ReminderController.MODE_DND) }
                                        ),
                                        DropdownItem(
                                            text = ReminderStr.dndModeSilent(),
                                            summary = ReminderStr.dndModeSilentSummary(),
                                            selected = classDndMode == ReminderController.MODE_SILENT,
                                            onClick = { selectMode(ReminderController.MODE_SILENT) }
                                        ),
                                        DropdownItem(
                                            text = ReminderStr.dndModePriority(),
                                            summary = ReminderStr.dndModePrioritySummary(),
                                            selected = classDndMode == ReminderController.MODE_PRIORITY,
                                            onClick = { selectMode(ReminderController.MODE_PRIORITY) }
                                        )
                                    )
                                )
                            }
                            if (dndSupported) CardExpand(visible = masterEnabled) {
                                OverlayDropdownMenu(
                                    title = ReminderStr.dndModeTitle(),
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
                    item(key = "reminder_island") {
                        CardExpand(visible = showIslandCard && masterEnabled) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SwitchPreference(
                                    title = if (isHyperIsland) ReminderStr.xiaomiSuperIsland() else ReminderStr.dynamicIsland(),
                                    summary = when {
                                        islandNotification && isHyperIsland ->
                                            ReminderStr.superIslandOnSummary()
                                        islandNotification ->
                                            ReminderStr.liveActivityOnSummary()
                                        isHyperIsland -> ReminderStr.islandOffHyperSummary()
                                        else -> ReminderStr.islandOffIosSummary()
                                    },
                                    checked = islandNotification,
                                    onCheckedChange = {
                                        rlog("island_switch", "on=$it hyper=$isHyperIsland")
                                        settingsViewModel.setIslandNotification(it)
                                    }
                                )
                                CardExpand(visible = islandNotification) {
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
                                                text = ReminderStr.shizukuStatus(),
                                                fontSize = 14.sp,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                text = when {
                                                    !shizukuRunning -> ReminderStr.notRunning()
                                                    !shizukuAuthorized -> ReminderStr.notAuthorized()
                                                    else -> ReminderStr.ready()
                                                },
                                                fontSize = 14.sp,
                                                color = when {
                                                    !shizukuRunning -> ComposeColor(0xFFFF6B6B)
                                                    !shizukuAuthorized -> ComposeColor(
                                                        0xFFFFB347
                                                    )

                                                    else -> ComposeColor(0xFF4CAF50)
                                                },
                                                textAlign = TextAlign.End,
                                            )
                                        }
                                        CardExpand(visible = !shizukuRunning) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = ReminderStr.installShizuku(),
                                                style = MiuixTheme.textStyles.body2,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            )
                                        }
                                        CardExpand(visible = shizukuRunning && !shizukuAuthorized) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            TextButton(
                                                text = ReminderStr.authorizeShizuku(),
                                                onClick = {
                                                    rlog("shizuku_authorize_request")
                                                    reminderController.requestShizukuPermission { granted ->
                                                        rlog("shizuku_authorize_result", "granted=$granted")
                                                        shizukuAuthorized = granted
                                                        if (!granted) {
                                                            showToast(ReminderStr.shizukuAuthFailed())
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
                    item(key = "reminder_thumb_display") {
                        CardExpand(visible = masterEnabled) {

                        val liveRightOptions = listOf(
                            DropdownItem(
                                text = ReminderStr.courseName(),
                                selected = liveRightMode == 0,
                                onClick = {
                                    rlog("live_right_mode", "mode=0 课程名称")
                                    liveRightMode = 0
                                    reminderPrefs.edit { putInt("live_right_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = ReminderStr.classLocation(),
                                selected = liveRightMode == 1,
                                onClick = {
                                    rlog("live_right_mode", "mode=1 上课地点")
                                    liveRightMode = 1
                                    reminderPrefs.edit { putInt("live_right_mode", 1) }
                                }
                            ),
                            DropdownItem(
                                text = ReminderStr.countdown(),
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
                                text = ReminderStr.courseName(),
                                selected = islandLeftMode == 0,
                                onClick = {
                                    rlog("island_left_mode", "mode=0 课程名称")
                                    islandLeftMode = 0
                                    reminderPrefs.edit { putInt("island_left_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = ReminderStr.classLocation(),
                                selected = islandLeftMode == 1,
                                onClick = {
                                    rlog("island_left_mode", "mode=1 上课地点")
                                    islandLeftMode = 1
                                    reminderPrefs.edit { putInt("island_left_mode", 1) }
                                }
                            ),
                            DropdownItem(
                                text = ReminderStr.countdown(),
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
                                text = ReminderStr.courseName(),
                                selected = islandRightMode == 0,
                                onClick = {
                                    rlog("island_right_mode", "mode=0 课程名称")
                                    islandRightMode = 0
                                    reminderPrefs.edit { putInt("island_right_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = ReminderStr.classLocation(),
                                selected = islandRightMode == 1,
                                onClick = {
                                    rlog("island_right_mode", "mode=1 上课地点")
                                    islandRightMode = 1
                                    reminderPrefs.edit { putInt("island_right_mode", 1) }
                                }
                            ),
                            DropdownItem(
                                text = ReminderStr.countdown(),
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
                                text = ReminderStr.courseName(),
                                selected = islandAodMode == 0,
                                onClick = {
                                    rlog("island_aod_mode", "mode=0 课程名称")
                                    islandAodMode = 0
                                    reminderPrefs.edit { putInt("island_aod_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = ReminderStr.classLocation(),
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
                                text = ReminderStr.inClassNow(),
                                selected = islandInClassRightMode == 0,
                                onClick = {
                                    rlog("island_inclass_right_mode", "mode=0 正在上课")
                                    islandInClassRightMode = 0
                                    reminderPrefs.edit { putInt("island_in_class_right_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = ReminderStr.countdown(),
                                selected = islandInClassRightMode == 1,
                                onClick = {
                                    rlog("island_inclass_right_mode", "mode=1 倒计时")
                                    islandInClassRightMode = 1
                                    reminderPrefs.edit { putInt("island_in_class_right_mode", 1) }
                                }
                            ),
                        )

                        // 同一张 Card 里用 CardExpand 切换，别用 if/else 换两张卡 ——
                        // 换卡是「新节点替换旧节点」，高度直接跳，没有任何动画可言。
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            CardExpand(visible = !showSlotCard) {
                                OverlayDropdownMenu(
                                    title = ReminderStr.liveRightSide(),
                                    entry = DropdownEntry(items = liveRightOptions),
                                    collapseOnSelection = true,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                            }
                            // Slot customization for both HyperOS and iOS Live Activity.
                            CardExpand(visible = showSlotCard) {
                                OverlayDropdownMenu(
                                    title = if (isHyperIsland) ReminderStr.superIslandLeft() else ReminderStr.dynamicIslandLeft(),
                                    entry = DropdownEntry(items = islandLeftOptions),
                                    collapseOnSelection = true,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                                OverlayDropdownMenu(
                                    title = if (isHyperIsland) ReminderStr.superIslandRight() else ReminderStr.dynamicIslandRight(),
                                    entry = DropdownEntry(items = islandRightOptions),
                                    collapseOnSelection = true,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                                CardExpand(visible = inClassEnabled || !isHyperIsland) {
                                    OverlayDropdownMenu(
                                        title = if (isHyperIsland) ReminderStr.inClassIslandRight() else ReminderStr.inClassDynamicIslandRight(),
                                        entry = DropdownEntry(items = islandInClassRightOptions),
                                        collapseOnSelection = true,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                        dropdownColors = liquidGlassDropdownColors,
                                    )
                                }
                                CardExpand(visible = isHyperIsland) {
                                    OverlayDropdownMenu(
                                        title = ReminderStr.islandAod(),
                                        summary = ReminderStr.islandAodSummary(),
                                        entry = DropdownEntry(items = islandAodOptions),
                                        collapseOnSelection = true,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                        dropdownColors = liquidGlassDropdownColors,
                                    )
                                }
                            }
                        }
                    }
                    }

                    // 上课地点精简：
                    item(key = "reminder_thumb_label") {
                        CardExpand(visible = masterEnabled && !reminderController.supportsLiveActivity()) {
                            val scopeOptions = listOf(
                                ClassroomLabel.SCOPE_OFF to ReminderStr.scopeOriginal(),
                                ClassroomLabel.SCOPE_CLASSROOM to ReminderStr.scopeClassroom(),
                                ClassroomLabel.SCOPE_COURSE to ReminderStr.scopeCourse(),
                                ClassroomLabel.SCOPE_BOTH to ReminderStr.scopeBoth(),
                            ).map { (value, label) ->
                                DropdownItem(
                                    text = label,
                                    selected = classroomLabelScope == value,
                                    onClick = {
                                        rlog("classroom_label_scope", "scope=$value")
                                        classroomLabelScope = value
                                        reminderPrefs.edit {
                                            putInt(ClassroomLabel.KEY_SCOPE, value)
                                        }
                                    }
                                )
                            }
                            val methodOptions = listOf(
                                DropdownItem(
                                    text = ReminderStr.methodRegex(),
                                    summary = ReminderStr.methodRegexSummary(),
                                    selected = classroomLabelMethod == ClassroomLabel.METHOD_REGEX,
                                    onClick = {
                                        rlog("classroom_label_method", "method=0 正则")
                                        classroomLabelMethod = ClassroomLabel.METHOD_REGEX
                                        reminderPrefs.edit {
                                            putInt(
                                                ClassroomLabel.KEY_METHOD,
                                                ClassroomLabel.METHOD_REGEX
                                            )
                                        }
                                    }
                                ),
                                DropdownItem(
                                    text = ReminderStr.methodAi(),
                                    summary = ReminderStr.methodAiSummary(),
                                    selected = classroomLabelMethod == ClassroomLabel.METHOD_AI,
                                    onClick = {
                                        rlog("classroom_label_method", "method=1 AI")
                                        classroomLabelMethod = ClassroomLabel.METHOD_AI
                                        reminderPrefs.edit {
                                            putInt(
                                                ClassroomLabel.KEY_METHOD,
                                                ClassroomLabel.METHOD_AI
                                            )
                                        }
                                    }
                                ),
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                insideMargin = PaddingValues(0.dp)
                            ) {
                                OverlayDropdownMenu(
                                    title = ReminderStr.thumbAbbreviation(),
                                    entry = DropdownEntry(items = scopeOptions),
                                    collapseOnSelection = true,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                                CardExpand(visible = classroomLabelEnabled) {
                                    OverlayDropdownMenu(
                                        title = ReminderStr.abbreviationMethod(),
                                        entry = DropdownEntry(items = methodOptions),
                                        collapseOnSelection = true,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                        dropdownColors = liquidGlassDropdownColors,
                                    )
                                    ArrowPreference(
                                        title = ReminderStr.viewAbbreviationPreview(),
                                        summary = ReminderStr.viewAbbreviationPreviewSummary(),
                                        endActions = {
                                            if (classroomLabelMethod == ClassroomLabel.METHOD_AI) {
                                                IconButton(
                                                    onClick = {
                                                        if (aiShrinking) return@IconButton
                                                        hapticFeedback.performHapticFeedback(
                                                            HapticFeedbackType.Confirm
                                                        )
                                                        rlog(
                                                            "classroom_label_ai_shrink",
                                                            "count=${previewClassrooms.size}"
                                                        )
                                                        aiShrinking = true
                                                        scope.launch {
                                                            val outcome = runCatching {
                                                                AiClient(createHttpService(AI_TIMEOUTS))
                                                                    .shrinkLabels(
                                                                        classrooms = previewClassrooms.take(60),
                                                                        courses = previewCourses.take(60),
                                                                    )
                                                            }.getOrElse { AiResult.Error(it.message ?: ReminderStr.unknownError()) }
                                                            aiShrinking = false
                                                            // 失败必须把原因说出来：
                                                            if (outcome is AiResult.Error) {
                                                                rlog("classroom_label_ai_shrink_fail", outcome.message)
                                                                showToast(ReminderStr.aiShrinkFailedS(outcome.message))
                                                                return@launch
                                                            }
                                                            val ok = outcome as AiResult.Ok
                                                            val lines = ok.value
                                                            // 空结果是正常的：楼名本来就短（「博105」「安503」），
                                                            // 没什么可缩。这时别把已有偏好覆盖成空。
                                                            if (lines.isEmpty()) {
                                                                rlog("classroom_label_ai_shrink_noop")
                                                                showToast(ReminderStr.alreadyShort())
                                                                return@launch
                                                            }
                                                            val text = lines.joinToString("\n") { (from, to) -> "$from=$to" }
                                                            classroomLabelAbbreviations = text
                                                            reminderPrefs.edit {
                                                                putString(ClassroomLabel.KEY_ABBREVIATIONS, text)
                                                            }
                                                            rlog(
                                                                "classroom_label_ai_shrink_ok",
                                                                "lines=${lines.size} remaining=${ok.remaining}"
                                                            )
                                                            // 每天有次数上限，把剩多少顺带说清楚，免得用户点着点着突然被拦
                                                            showToast(
                                                                if (ok.remaining != null) {
                                                                    ReminderStr.aiShrinkDoneRemaining(lines.size, ok.remaining)
                                                                } else {
                                                                    ReminderStr.aiShrinkDone(lines.size)
                                                                }
                                                            )
                                                        }
                                                    },
                                                    enabled = !aiShrinking,
                                                    backgroundColor = MiuixTheme.colorScheme.primary.copy(0.8f),
                                                    modifier = Modifier.size(36.dp),
                                                ) {
                                                    if (aiShrinking) {
                                                        CircularProgressIndicator(
                                                            size = 20.dp,
                                                            strokeWidth = 2.dp,
                                                            // 底色是主题色，转圈要跟着变白才看得见
                                                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                                                foregroundColor = Color.White,
                                                                backgroundColor = Color.Transparent,
                                                            ),
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = AiSparkleIcon,
                                                            contentDescription = ReminderStr.oneTapAbbreviate(),
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .offset(x = 1.dp),
                                                            tint = Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onClick = {
                                            rlog("classroom_label_preview_open")
                                            showClassroomPreviewDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 权限设置
                    item(key = "reminder_permissions") {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = ReminderStr.notificationPermission(),
                                    summary = ReminderStr.notificationPermissionSummary(),
                                    endActions = {
                                        Text(
                                            text = if (notificationGranted) ReminderStr.authorized() else ReminderStr.notAuthorized(),
                                            fontSize = 14.5.sp,
                                            color = if (notificationGranted) {
                                                ComposeColor(0xFF4CAF50)
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            },
                                            textAlign = TextAlign.End,
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
                                CardExpand(visible = reminderController.supportsPromotedNotifications) {
                                    ArrowPreference(
                                        title = ReminderStr.liveNotificationPermission(),
                                        summary = ReminderStr.liveNotificationPermissionSummary(),
                                        endActions = {
                                            Text(
                                                text = if (canPostPromoted) ReminderStr.authorized() else ReminderStr.notAuthorized(),
                                                fontSize = 14.5.sp,
                                                color = if (canPostPromoted) {
                                                    ComposeColor(0xFF4CAF50)
                                                } else {
                                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                                },
                                                textAlign = TextAlign.End,
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
                                if (ReminderPermissionRow.EXACT_ALARM !in hiddenPermissionRows) {
                                ArrowPreference(
                                    title = ReminderStr.exactAlarmPermission(),
                                    summary = ReminderStr.exactAlarmPermissionSummary(),
                                    endActions = {
                                        Text(
                                            text = if (canScheduleExactAlarms) ReminderStr.authorized() else ReminderStr.notAuthorized(),
                                            fontSize = 14.5.sp,
                                            color = if (canScheduleExactAlarms) {
                                                ComposeColor(0xFF4CAF50)
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            },
                                            textAlign = TextAlign.End,
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
                                }
                                if (ReminderPermissionRow.BATTERY_OPTIMIZATION !in hiddenPermissionRows) {
                                ArrowPreference(
                                    title = ReminderStr.batteryOptimization(),
                                    summary = ReminderStr.batteryOptimizationSummary(),
                                    endActions = {
                                        Text(
                                            text = if (isIgnoringBattery) ReminderStr.optimizationDisabled() else ReminderStr.optimizationNotDisabled(),
                                            fontSize = 14.5.sp,
                                            color = if (isIgnoringBattery) {
                                                ComposeColor(0xFF4CAF50)
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            },
                                            textAlign = TextAlign.End,
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
                                }
                                if (ReminderPermissionRow.DND !in hiddenPermissionRows) {
                                ArrowPreference(
                                    title = ReminderStr.dndPermission(),
                                    summary = ReminderStr.dndPermissionSummary(),
                                    endActions = {
                                        Text(
                                            text = if (dndPermissionGranted) ReminderStr.authorized() else ReminderStr.notAuthorized(),
                                            fontSize = 14.5.sp,
                                            color = if (dndPermissionGranted) {
                                                ComposeColor(0xFF4CAF50)
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            },
                                            textAlign = TextAlign.End,
                                        )
                                    },
                                    onClick = {
                                        rlog("permission_dnd_click", "granted=$dndPermissionGranted")
                                        if (!dndPermissionGranted) {
                                            rlog("permission_dnd_launch")
                                            try {
                                                reminderController.openNotificationAccessSettings { onDndPermissionResult() }
                                            } catch (_: Exception) {
                                                showToast(ReminderStr.dndPermissionManual())
                                            }
                                        }
                                    }
                                )
                                }
                                if (ReminderPermissionRow.AUTOSTART !in hiddenPermissionRows) {
                                ArrowPreference(
                                    title = ReminderStr.autostartPermission(),
                                    summary = ReminderStr.autostartPermissionSummary(),
                                    endActions = {
                                        Text(
                                            text = ReminderStr.goToCheck(),
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

                }

                // 列表底部留白仍保留，给 Activity 层悬浮的测试按钮让位
            }

            // 课中提醒时机弹窗：左侧全程/距下课，右侧分钟数（全程时禁用）
            OverlayDialog(
                title = ReminderStr.reminderTiming(),
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
                                if (mode == ReminderController.IN_CLASS_TIMING_FULL) ReminderStr.inClassTimingFull() else ReminderStr.inClassTimingBeforeEnd()
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
                            label = { ReminderStr.minutesN(it) },
                            textStyle = MiuixTheme.textStyles.title2,
                            wrapAround = true,
                            enabled = minutesEnabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = if (tempInClassTimingMode == ReminderController.IN_CLASS_TIMING_FULL) {
                            ReminderStr.inClassFullDesc()
                        } else {
                            ReminderStr.inClassBeforeEndDesc(tempInClassLeadMinutes)
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
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("inclass_timing_dialog_cancel")
                                showInClassTimingDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = CommonStr.ok(),
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

            // 地点精简预览：课表里每一个地点都按当前设置算一遍，改完立刻能看到差别
            OverlayDialog(
                title = ReminderStr.abbreviationPreview(),
                summary = ReminderStr.previewEditHint(),
                show = showClassroomPreviewDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = {
                    // 顺手提交正在改的那一行：点遮罩/返回键不该悄悄丢掉刚打的字
                    commitPreviewEdit()
                    showClassroomPreviewDialog = false
                }
            ) {
                val rule = ClassroomLabelRule(
                    enabled = classroomLabelEnabled,
                    method = classroomLabelMethod,
                    abbreviations = classroomLabelAbbreviations,
                    scope = classroomLabelScope,
                    overrides = classroomLabelOverrides,
                )
                // 「没有手动改写时的结果」：编辑框里改回它就等于取消这一条手动改写
                val autoRule = rule.copy(overrides = "")
                // 课程名走 applyCourse，地点走 apply —— 两者共用同一张对照表，只是正则方式的表不同
                val previewItems = previewCourses.map {
                    PreviewItem(it, ClassroomLabel.applyCourse(it, rule), ClassroomLabel.applyCourse(it, autoRule))
                } + previewClassrooms.map {
                    PreviewItem(it, ClassroomLabel.apply(it, rule), ClassroomLabel.apply(it, autoRule))
                }
                val changedCount = previewItems.count { it.raw != it.shown }
                val overriddenCount = previewItems.count { rule.overrideMap.containsKey(it.raw) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end= 8.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (classroomLabelMethod == ClassroomLabel.METHOD_AI) {
                                    ReminderStr.methodAi()
                                } else {
                                    ReminderStr.methodRegex()
                                },
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = ReminderStr.currentMethod(),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Text(
                            text = if (previewItems.isEmpty()) {
                                ReminderStr.scheduleEmpty()
                            } else {
                                "$changedCount / ${previewItems.size}"
                            },
                            fontSize = 19.sp,
                            color = MiuixTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = ReminderStr.willBeRewritten(),
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    // 整块一个圆角矩形，里面滚动
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        shape = ClassroomPreviewShape,
                        color = if (isAppDarkTheme()) {
                            Color.White.copy(alpha = 0.1f)
                        } else {
                            Color.Black.copy(alpha = 0.06f)
                        },
                        shadowElevation = 0.dp,
                    ) {
                        if (previewItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = ReminderStr.scheduleNoCourseOrClassroom(),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                itemsIndexed(previewItems) { index, item ->
                                    ClassroomPreviewRow(
                                        raw = item.raw,
                                        shown = item.shown,
                                        editing = previewEditRaw == item.raw,
                                        editText = previewEditText,
                                        onEditTextChange = { previewEditText = it },
                                        onStartEdit = {
                                            // 换一行改时先把上一行提交掉，不然那几笔白打了
                                            if (previewEditRaw != item.raw) commitPreviewEdit()
                                            previewEditRaw = item.raw
                                            previewEditAuto = item.auto
                                            previewEditText = item.shown
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        },
                                        onCommit = { commitPreviewEdit() },
                                        // 首尾各再让 6，凑成上下各 12，别贴着圆角
                                        modifier = Modifier.padding(
                                            top = if (index == 0) 6.dp else 0.dp,
                                            bottom = if (index == previewItems.lastIndex) 6.dp else 0.dp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 只改过才有「恢复自动」可点，没改过就别摆一个按不动的按钮
                        if (overriddenCount > 0) {
                            TextButton(
                                text = ReminderStr.restoreAutoN(overriddenCount),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    previewEditRaw = null
                                    classroomLabelOverrides = ""
                                    reminderPrefs.edit { putString(ClassroomLabel.KEY_OVERRIDES, "") }
                                    rlog("classroom_label_override", "clear_all")
                                    showToast(ReminderStr.allRestoredAuto())
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        TextButton(
                            text = CommonStr.done(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                commitPreviewEdit()
                                showClassroomPreviewDialog = false
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // 提前提醒分钟数弹窗
            OverlayDialog(
                title = ReminderStr.reminderLeadTime(),
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
                        label = { ReminderStr.minutesN(it) },
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
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("preclass_minutes_dialog_cancel")
                                showMinutesDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = CommonStr.ok(),
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
                title = ReminderStr.reminderTime(),
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
                            text = CommonStr.cancel(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("nextday_time_dialog_cancel")
                                showTimeDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = CommonStr.ok(),
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
