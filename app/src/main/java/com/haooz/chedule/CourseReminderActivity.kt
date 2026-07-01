package com.haooz.chedule

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
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.viewmodel.CourseViewModel
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
    viewModel: CourseViewModel = viewModel()
) {
    val preClassReminder by viewModel.preClassReminder.collectAsState()
    val preClassReminderMinutes by viewModel.preClassReminderMinutes.collectAsState()
    val nextDayReminder by viewModel.nextDayReminder.collectAsState()
    val nextDayReminderHour by viewModel.nextDayReminderHour.collectAsState()
    val nextDayReminderMinute by viewModel.nextDayReminderMinute.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var listScrollY by remember { mutableIntStateOf(0) }

    var showMinutesDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var tempMinutes by remember { mutableIntStateOf(preClassReminderMinutes) }
    var tempHour by remember { mutableIntStateOf(nextDayReminderHour) }
    var tempMinute by remember { mutableIntStateOf(nextDayReminderMinute) }

    val masterEnabled = preClassReminder || nextDayReminder
    var isIgnoringBattery by remember { mutableStateOf(true) }
    val batteryOptLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    var canPostPromoted by remember { mutableStateOf(false) }
    val promotedSettingsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        canPostPromoted = CourseReminderHelper.canPostPromotedNotifications(context)
    }

    LaunchedEffect(masterEnabled) {
        if (masterEnabled) {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
            isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName)
            canPostPromoted = CourseReminderHelper.canPostPromotedNotifications(context)
        }
    }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            CourseReminderHelper.startReminderService(context)
        } else {
            Toast.makeText(context, "需要通知权限才能使用课程提醒功能", Toast.LENGTH_SHORT).show()
        }
    }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val blurAlpha = if (listScrollY < 50) 0f else ((listScrollY - 50) / 50f).coerceIn(0f, 0.7f)
    val topBarColorProgress = ((listScrollY - 50) / 50f).coerceIn(0f, 1f)
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
                snapshotFlow { listState.firstVisibleItemScrollOffset }
                    .collect { offset ->
                        listScrollY = offset
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
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@SwitchPreference
                                }
                                viewModel.setPreClassReminder(it)
                                viewModel.setNextDayReminder(it)
                                if (it) {
                                    CourseReminderHelper.startReminderService(context)
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
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@SwitchPreference
                                }
                                viewModel.setPreClassReminder(it)
                                if (it) {
                                    CourseReminderHelper.startReminderService(context)
                                }
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
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@SwitchPreference
                                }
                                viewModel.setNextDayReminder(it)
                                if (it) {
                                    CourseReminderHelper.startReminderService(context)
                                }
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
                                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
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
                                    text = "开启后，下节课倒计时将显示在状态栏和锁屏上，无需打开应用即可查看",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(
                                    text = "前往开启",
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
                            viewModel.setPreClassReminderMinutes(tempMinutes)
                            showMinutesDialog = false
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
                            viewModel.setNextDayReminderHour(tempHour)
                            viewModel.setNextDayReminderMinute(tempMinute)
                            showTimeDialog = false
                        },
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
