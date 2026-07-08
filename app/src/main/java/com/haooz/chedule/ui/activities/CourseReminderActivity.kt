/** 课程提醒设置页面 */
package com.haooz.chedule.ui.activities
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.shizuku.ShizukuManager
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.Color as ComposeColor

class CourseReminderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyThemeAwareSystemBars()
        setContent {
            CourseScheduleTheme {
                CourseReminderScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun CourseReminderScreen(
    onBack: () -> Unit,
    viewModel: CourseViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val preClassReminder by settingsViewModel.preClassReminder.collectAsState()
    val preClassReminderMinutes by settingsViewModel.preClassReminderMinutes.collectAsState()
    val nextDayReminder by settingsViewModel.nextDayReminder.collectAsState()
    val nextDayReminderHour by settingsViewModel.nextDayReminderHour.collectAsState()
    val nextDayReminderMinute by settingsViewModel.nextDayReminderMinute.collectAsState()
    val islandNotification by settingsViewModel.islandNotification.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var listScrollY by remember { mutableIntStateOf(0) }

    var showMinutesDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var tempMinutes by remember { mutableIntStateOf(preClassReminderMinutes) }
    var tempHour by remember { mutableIntStateOf(nextDayReminderHour) }
    var tempMinute by remember { mutableIntStateOf(nextDayReminderMinute) }

    // Shizuku 状态
    var shizukuRunning by remember { mutableStateOf(false) }
    var shizukuAuthorized by remember { mutableStateOf(false) }
    var isIslandSupported by remember { mutableStateOf(false) }

    val masterEnabled = preClassReminder || nextDayReminder
    var isIgnoringBattery by remember { mutableStateOf(true) }
    var autoStartDismissed by remember { mutableStateOf(false) }
    var permissionRefreshKey by remember { mutableIntStateOf(0) }
    val batteryOptLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName)
        permissionRefreshKey++
    }
    val autoStartLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName)
        permissionRefreshKey++
    }
    var canPostPromoted by remember { mutableStateOf(false) }
    var canScheduleExactAlarms by remember { mutableStateOf(true) }
    val promotedSettingsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        canPostPromoted = CourseReminderHelper.canPostPromotedNotifications(context)
    }
    val exactAlarmLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        canScheduleExactAlarms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
        if (canScheduleExactAlarms && masterEnabled) {
            CourseReminderHelper.startReminderService(context)
        }
    }

    LaunchedEffect(masterEnabled) {
        if (masterEnabled) {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
            isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName)
            canPostPromoted = CourseReminderHelper.canPostPromotedNotifications(context)
            // 检查精确闹钟权限
            val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            canScheduleExactAlarms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else true
            // 检查 Shizuku 和超级岛支持
            shizukuRunning = ShizukuManager.isShizukuRunning()
            shizukuAuthorized = ShizukuManager.checkSelfPermission()
            isIslandSupported = IslandNotificationHelper.isIslandSupported(context)
        }
    }

    var pendingPermissionAction by remember { mutableStateOf<Boolean?>(null) }
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            CourseReminderHelper.startReminderService(context)
            pendingPermissionAction?.let { enable ->
                settingsViewModel.setPreClassReminder(enable)
                settingsViewModel.setNextDayReminder(enable)
                if (enable) {
                    CourseReminderHelper.startReminderService(context)
                } else {
                    CourseReminderHelper.stopReminderService(context)
                }
            }
            pendingPermissionAction = null
        } else {
            pendingPermissionAction = null
            Toast.makeText(context, "需要通知权限才能使用课程提醒功能", Toast.LENGTH_SHORT).show()
        }
    }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val blurAlpha = if (listScrollY < 50) 0f else ((listScrollY - 50) / 30f).coerceIn(0f, 0.7f)
    val topBarColorProgress = ((listScrollY - 50) / 30f).coerceIn(0f, 1f)
    val topBarColor = if (listScrollY < 50) {
        MiuixTheme.colorScheme.surface
    } else {
        val surface = MiuixTheme.colorScheme.surface
        val target = if (isDark) ComposeColor.Black.copy(alpha = 0.7f) else ComposeColor.White.copy(alpha = 0.7f)
        lerp(surface, target, topBarColorProgress)
    }
    val topAppBarColors = BlurDefaults.blurColors(
        blendColors = listOf(
            if (isDark) BlendColorEntry(ComposeColor.Black.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
            else BlendColorEntry(ComposeColor.White.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
        ),
        brightness = 0f,
        contrast = 1f,
        saturation = 1.2f
    )

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = if (blurAlpha > 0f) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        colors = topAppBarColors
                    )
                } else {
                    Modifier
                },
                color = topBarColor,
                title = "课程提醒",
                largeTitle = "课程提醒",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onBack()
                        },
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            val listState = rememberLazyListState()
            LaunchedEffect(listState) {
                snapshotFlow {
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }.collect { (index, offset) ->
                    listScrollY = if (index > 0) {
                        val firstItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                        val accumulated = if (firstItem != null)
                            (firstItem.offset - listState.layoutInfo.beforeContentPadding).coerceAtLeast(0)
                        else 0
                        accumulated + offset
                    } else {
                        offset
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = paddingValues.calculateTopPadding(),
                    end = 16.dp,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                }
                // 开启提醒
                item {
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        val masterEnabled = preClassReminder || nextDayReminder
                        SwitchPreference(
                            title = "开启提醒",
                            checked = masterEnabled,
                            onCheckedChange = {
                                val hasPermission = context.checkSelfPermission(
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    pendingPermissionAction = it
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@SwitchPreference
                                }
                                settingsViewModel.setPreClassReminder(it)
                                settingsViewModel.setNextDayReminder(it)
                                if (it) {
                                    CourseReminderHelper.startReminderService(context)
                                } else {
                                    // 关闭总开关：取消所有已注册闹钟，避免残留
                                    CourseReminderHelper.stopReminderService(context)
                                }
                            }
                        )
                    }
                }

                // 提醒详情
                item {
                    val masterEnabled = preClassReminder || nextDayReminder
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        SwitchPreference(
                            title = "下节课提醒",
                            summary = "提供下节上课时间、地点等信息，和上课静音等操作建议",
                            checked = preClassReminder,
                            enabled = masterEnabled,
                            onCheckedChange = {
                                val hasPermission = context.checkSelfPermission(
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    pendingPermissionAction = it
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@SwitchPreference
                                }
                                settingsViewModel.setPreClassReminder(it)
                                // 无论开启还是关闭，都重新调度（startReminderService 内部会处理关闭分支）
                                CourseReminderHelper.startReminderService(context)
                            }
                        )
                        if (preClassReminder && masterEnabled) {
                            ArrowPreference(
                                title = "提前提醒时间",
                                endActions = {
                                    Text(
                                        text = "${preClassReminderMinutes}分钟",
                                        fontSize = 14.5.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = {
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
                                val hasPermission = context.checkSelfPermission(
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    pendingPermissionAction = it
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@SwitchPreference
                                }
                                settingsViewModel.setNextDayReminder(it)
                                // 无论开启还是关闭，都重新调度（startReminderService 内部会处理关闭分支）
                                CourseReminderHelper.startReminderService(context)
                            }
                        )
                        if (nextDayReminder && masterEnabled) {
                            ArrowPreference(
                                title = "提醒时间",
                                endActions = {
                                    Text(
                                        text = String.format("%02d:%02d", nextDayReminderHour, nextDayReminderMinute),
                                        fontSize = 14.5.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = {
                                    tempHour = nextDayReminderHour
                                    tempMinute = nextDayReminderMinute
                                    showTimeDialog = true
                                }
                            )
                        }
                    }
                }

                // 超级岛设置
                if (masterEnabled && isIslandSupported) {
                    item {
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SwitchPreference(
                                    title = "小米超级岛",
                                    summary = if (islandNotification) "已开启，课程提醒将以超级岛样式显示" else "关闭后使用实时动态通知",
                                    checked = islandNotification,
                                    onCheckedChange = {
                                        settingsViewModel.setIslandNotification(it)
                                    }
                                )
                                // Shizuku 状态
                                if (islandNotification) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .padding(bottom = 16.dp)
                                    ) {
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
                                                    !shizukuAuthorized -> ComposeColor(0xFFFFB347)
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
                                                    IslandNotificationHelper.requestShizukuPermission { granted ->
                                                        shizukuAuthorized = granted
                                                        if (!granted) {
                                                            Toast.makeText(context, "Shizuku 授权失败", Toast.LENGTH_SHORT).show()
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

                // 电池优化提示
                if (masterEnabled && !isIgnoringBattery) {
                    item {
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Text(
                                    text = "电池优化可能影响提醒",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 17.sp,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "系统电池优化可能会延迟或阻止课程提醒通知，建议关闭以确保提醒准时送达",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(
                                    text = "前往关闭电池优化",
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            batteryOptLauncher.launch(intent)
                                        } catch (_: Exception) {
                                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            batteryOptLauncher.launch(intent)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // 自启动权限提示
                if (masterEnabled && !autoStartDismissed) {
                    item {
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Text(
                                    text = "开启自启动权限",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 17.sp,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "不同厂商路径不同，通常在「设置」→「应用管理」→「自启动」中开启，确保课程提醒不会被系统杀死",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(
                                        text = "前往开启",
                                        onClick = {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            autoStartLauncher.launch(intent)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        text = "已开启",
                                        onClick = { autoStartDismissed = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 精确闹钟权限提示
                if (masterEnabled && !canScheduleExactAlarms) {
                    item {
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Text(
                                    text = "开启精确闹钟权限",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 17.sp,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "精确闹钟权限可确保课程提醒准时触发，不受系统省电策略影响",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(
                                    text = "前往开启精确闹钟",
                                    onClick = {
                                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        exactAlarmLauncher.launch(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // 实况通知提示
                if (masterEnabled && !canPostPromoted && android.os.Build.VERSION.SDK_INT >= 36) {
                    item {
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Text(
                                    text = "开启实况通知",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 17.sp,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "开启后，下节课倒计时将实时显示在状态栏和锁屏上，无需打开应用即可查看",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(
                                    text = "前往开启实时动态",
                                    onClick = {
                                        try {
                                            val intent = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            promotedSettingsLauncher.launch(intent)
                                        } catch (_: Exception) {
                                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            }
                                            promotedSettingsLauncher.launch(intent)
                                        }
                                    },
                                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // 底部渐变遮罩
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to ComposeColor.Transparent,
                                0.15f to backgroundColor.copy(alpha = 0.5f),
                                0.5f to backgroundColor.copy(alpha = 0.85f),
                                1.0f to backgroundColor
                            )
                        )
                    )
            )

            // 发送测试通知 - 固定在底部
            TextButton(
                text = if (islandNotification && isIslandSupported) "测试小米超级岛" else "测试实时活动",
                onClick = {
                    val repo = com.haooz.chedule.data.CourseRepository(context)
                    val nextCourse = CourseReminderHelper.findNextCourseToday(context)
                    val courseName = nextCourse?.name ?: "暂无课程"
                    val classroom = nextCourse?.classroom ?: ""
                    val startTime = nextCourse?.let { CourseReminderHelper.getCourseStartTime(it, repo) } ?: ""
                    val section = nextCourse?.let { it.getSectionText() } ?: ""
                    if (islandNotification && isIslandSupported) {
                        IslandNotificationHelper.sendTestIslandNotification(context)
                        Toast.makeText(context, "已发送超级岛测试通知", Toast.LENGTH_SHORT).show()
                    } else {
                        val startMillis = System.currentTimeMillis() + 30_000L
                        val endMillis = startMillis + 45 * 60_000L
                        CourseReminderHelper.showPreClassCountdownNotification(
                            context = context,
                            courseName = courseName,
                            classroom = classroom,
                            section = section,
                            startTime = startTime,
                            startMillis = startMillis,
                            endMillis = endMillis
                        )
                        Toast.makeText(context, "已发送: $courseName (模拟30秒后上课)", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, bottom = 48.dp)
            )
        }

        // 提前提醒分钟数弹窗
        OverlayDialog(
            title = "提前提醒时间",
            show = showMinutesDialog,
            outsideMargin = DpSize(17.dp, 12.dp),
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
                            showMinutesDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            settingsViewModel.setPreClassReminderMinutes(tempMinutes)
                            showMinutesDialog = false
                            CourseReminderHelper.startReminderService(context)
                        },
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 次日提醒时间弹窗
        OverlayDialog(
            title = "提醒时间",
            show = showTimeDialog,
            outsideMargin = DpSize(17.dp, 12.dp),
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
                        label = { String.format("%02d", it) },
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
                        label = { String.format("%02d", it) },
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
                            showTimeDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            settingsViewModel.setNextDayReminderHour(tempHour)
                            settingsViewModel.setNextDayReminderMinute(tempMinute)
                            showTimeDialog = false
                            CourseReminderHelper.startReminderService(context)
                        },
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
