package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.ui.activities.AboutActivity
import com.haooz.chedule.ui.activities.CourseReminderActivity
import com.haooz.chedule.ui.activities.CourseTimeSettingsActivity
import com.haooz.chedule.ui.activities.HolidaySettingsActivity
import com.haooz.chedule.ui.activities.PreferenceSettingsActivity
import com.haooz.chedule.ui.activities.UpdateSettingsActivity
import com.haooz.chedule.ui.activities.WidgetIntroActivity
import com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.basic.collapsibleTopInset
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor

// 解析失败时回退到今天，避免弹窗初值出现非法日期
private fun parseDate(dateStr: String): Triple<Int, Int, Int> {
    return try {
        val parts = dateStr.split("/")
        Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    } catch (_: Exception) {
        val cal = Calendar.getInstance()
        Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }
}

private fun getDaysInMonth(year: Int, month: Int): Int {
    return try {
        LocalDate.of(year, month, 1).lengthOfMonth()
    } catch (_: Exception) {
        31
    }
}

// 顶栏折叠期间会逐帧重组，提到顶层避免组合期每帧新建 Set
private val BackupMigrationActivities = setOf(
    "BackupAndMigrationActivity",
    "LocalBackupActivity",
    "WebDavSettingsActivity",
)

// 同 BackupMigrationActivities
private val AboutActivities = setOf(
    "AboutActivity",
    "AppreciateAuthorActivity",
    "ChangelogActivity",
)

@SuppressLint("ConfigurationScreenWidthHeight")
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
    onScrollYChanged: (Int) -> Unit = {},
    settingsScrollBehavior: SharedScrollBehavior? = null,
    activeSecondaryActivity: String? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var showShiftModeConfirmDialog by remember { mutableStateOf(false) }
    var showNewSemesterDialog by remember { mutableStateOf(false) }
    var newSemesterName by remember { mutableStateOf("") }

    val courseTimeSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        settingsViewModel.refreshSettings()
        viewModel.reloadCourses()
    }

    val reminderSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.reloadCourses()
    }

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
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val tabletHorizontalPadding = if (navBarStyle == "rail") {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 16.dp

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
            // rememberUpdatedState：LaunchedEffect 只依赖 listState，避免持旧闭包
            val currentOnScrollYChanged by rememberUpdatedState(onScrollYChanged)
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemScrollOffset }
                    .collect { offset ->
                        currentOnScrollYChanged(offset)
                    }
            }
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
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "basic") {
                    SmallTitle(
                        text = "基本设置",
                        modifier = Modifier.offset(x = (-16).dp)
                    )
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                title = "开始上课日期",
                                endActions = {
                                    Text(
                                        text = classStartTime,
                                        fontSize = 14.5.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = {
                                    val (y, m, d) = parseDate(classStartTime)
                                    tempYear = y
                                    tempMonth = m
                                    tempDay = d
                                    showStartDateDialog = true
                                },
                                holdDownState = showStartDateDialog
                            )

                            ArrowPreference(
                                title = "当前周数",
                                endActions = {
                                    Text(
                                        text = when {
                                            !isSemesterStarted -> "未开始"
                                            currentWeek > totalWeeks -> "放假中"
                                            else -> "第${currentWeek}周"
                                        },
                                        fontSize = 14.5.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = {
                                    tempCurrentWeek = currentWeek.coerceAtMost(totalWeeks)
                                    showCurrentWeekDialog = true
                                },
                                holdDownState = showCurrentWeekDialog
                            )

                            ArrowPreference(
                                title = "本学期总周数",
                                endActions = {
                                    Text(
                                        text = "第${totalWeeks}周",
                                        fontSize = 14.5.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = {
                                    tempTotalWeeks = totalWeeks
                                    showTotalWeeksDialog = true
                                },
                                holdDownState = showTotalWeeksDialog
                            )

                            SwitchPreference(
                                title = "智能显示周末",
                                summary = "开启后隐藏无课的周六日",
                                checked = smartWeekend,
                                onCheckedChange = { settingsViewModel.setSmartWeekend(it) }
                            )

                            if (!isShiftMode) {
                                SwitchPreference(
                                    title = "显示非本周课程",
                                    checked = showNonCurrentWeek,
                                    onCheckedChange = { settingsViewModel.setShowNonCurrentWeek(it) }
                                )
                            }

                            ArrowPreference(
                                title = "课表节数与时间",
                                summary = "管理不同课表的节数与课程时间",
                                holdDownState = activeSecondaryActivity == "CourseTimeSettingsActivity",
                                onClick = {
                                    val intent =
                                        Intent(context, CourseTimeSettingsActivity::class.java)
                                    courseTimeSettingsLauncher.launch(intent)
                                }
                            )
                        }
                    }
                }

                if (!isShiftMode) {
                    item(key = "features") {
                        SmallTitle(
                            text = "特色功能",
                            modifier = Modifier.offset(x = (-16).dp)
                        )
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = "课程提醒",
                                    summary = "课前提醒、次日课程提醒",
                                    holdDownState = activeSecondaryActivity == "CourseReminderActivity",
                                    onClick = {
                                        val intent = Intent(context, CourseReminderActivity::class.java)
                                        reminderSettingsLauncher.launch(intent)
                                    }
                                )
                                ArrowPreference(
                                    title = "节假日与调休",
                                    holdDownState = activeSecondaryActivity == "HolidaySettingsActivity",
                                    onClick = {
                                        context.startActivity(Intent(context, HolidaySettingsActivity::class.java))
                                    }
                                )
                                ArrowPreference(
                                    title = "桌面小部件",
                                    holdDownState = activeSecondaryActivity == "WidgetIntroActivity",
                                    onClick = {
                                        val intent = Intent(context, WidgetIntroActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                }

                if (isShiftMode) {
                    item(key = "shift_schedules") {
                        SmallTitle(
                            text = "选择对比课表",
                            modifier = Modifier.offset(x = (-16).dp)
                        )
                        Card(
                            cornerRadius = 20.dp,
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
                                text = "退出排班模式",
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
                            text = "数据管理",
                            modifier = Modifier.offset(x = (-16).dp)
                        )
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = "AI文本导入",
                                    holdDownState = activeSecondaryActivity == "AiImportActivity",
                                    onClick = {
                                        val intent = Intent(context, com.haooz.chedule.ui.activities.AiImportActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                                ArrowPreference(
                                    title = "教务系统导入",
                                    holdDownState = activeSecondaryActivity == "EducationalImportActivity",
                                    onClick = {
                                        val intent = Intent(context, com.haooz.chedule.ui.activities.EducationalImportActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = "备份与迁移",
                                    summary = "课表导入导出与备份",
                                    holdDownState = activeSecondaryActivity in BackupMigrationActivities,
                                    onClick = {
                                        val intent = Intent(context, com.haooz.chedule.ui.activities.BackupAndMigrationActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                }

                if (!isShiftMode) {
                    item(key = "others_title") {
                        SmallTitle(
                            text = "其他",
                            modifier = Modifier.offset(x = (-16).dp)
                        )
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = "开启新学期",
                                    summary = "复用当前课表设置，创建空课程的新课表",
                                    onClick = {
                                        newSemesterName = ""
                                        showNewSemesterDialog = true
                                    }
                                )
                            }
                        }
                    }
                    item(key = "others_prefs") {
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = "应用偏好设置",
                                    holdDownState = activeSecondaryActivity == "PreferenceSettingsActivity",
                                    onClick = {
                                        val intent = Intent(context, PreferenceSettingsActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                                ArrowPreference(
                                    title = "更新设置",
                                    holdDownState = activeSecondaryActivity == "UpdateSettingsActivity",
                                    onClick = {
                                        val intent = Intent(context, UpdateSettingsActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                                ArrowPreference(
                                    title = "关于应用",
                                    holdDownState = activeSecondaryActivity in AboutActivities,
                                    onClick = {
                                        val intent = Intent(context, AboutActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        OverlayDialog(
            title = "进入排班模式",
            summary = "将切换到排班课表模式，可同时对比多个课表的排班情况。确定进入？",
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
                        text = "取消",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showShiftModeConfirmDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
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
            title = "开启新学期",
            summary = "将复用当前课表的所有设置数据，创建一个清空课程的新课表",
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
                    label = "新课表名称",
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
                        text = "取消",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showNewSemesterDialog = false
                            newSemesterName = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "创建",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (newSemesterName.isNotBlank()) {
                                val name = newSemesterName
                                if (name in scheduleViewModel.scheduleNames.value) {
                                    Toast.makeText(context, "该课表名称已存在", Toast.LENGTH_SHORT).show()
                                } else {
                                    scheduleViewModel.createNewSemesterSchedule(name)
                                    showNewSemesterDialog = false
                                    newSemesterName = ""
                                    Toast.makeText(context, "「${name}」创建成功", Toast.LENGTH_SHORT).show()
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
            title = "开始上课日期",
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
                        range = 2024..2030,
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
                        label = { "${it}月" },
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
                        label = { "${it}日" },
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
                        text = "取消",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showStartDateDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            val date = String.format("%04d/%02d/%02d", tempYear, tempMonth, tempDay)
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
            title = "选择当前周次",
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
                        text = "取消",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showCurrentWeekDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
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
            title = "选择学期总周数",
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
                        text = "取消",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showTotalWeeksDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
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

// 解析成功时返回 data，供后续 applyScheduleData 写入
internal fun parseFullScheduleJson(text: String): Triple<Boolean, String, Map<String, Any>?> {
    try {
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
        val data: Map<String, Any> = gson.fromJson(text, type)

        if (data.containsKey("settings") || data.containsKey("courses")) {
            // 拾光格式：有 courses + config（无 settings）
            if (data.containsKey("config") && data.containsKey("courses") && !data.containsKey("settings")) {
                return parseShiguangScheduleJson(data)
            }
            return Triple(true, "解析成功", data)
        }

        return Triple(false, "无效的课表数据格式", null)
    } catch (e: Exception) {
        return Triple(false, "解析失败: ${e.message}", null)
    }
}

// 拾光格式 { courses, timeSlots, config } → 内部通用课表 map
private fun parseShiguangScheduleJson(data: Map<String, Any>): Triple<Boolean, String, Map<String, Any>?> {
    try {
        @Suppress("UNCHECKED_CAST")
        val shiguangCourses = data["courses"] as? List<Map<String, Any>>
        if (shiguangCourses.isNullOrEmpty()) {
            return Triple(false, "未找到课程数据", null)
        }

        val courses = mutableListOf<Map<String, Any>>()
        for (sg in shiguangCourses) {
            val name = sg["name"] as? String ?: continue
            val teacher = sg["teacher"] as? String ?: ""
            val position = sg["position"] as? String ?: ""
            val day = (sg["day"] as? Number)?.toInt() ?: continue
            val startSection = (sg["startSection"] as? Number)?.toInt() ?: continue
            val endSection = (sg["endSection"] as? Number)?.toInt() ?: startSection
            @Suppress("UNCHECKED_CAST")
            val weeks = (sg["weeks"] as? List<Number>)?.map { it.toInt() } ?: emptyList()
            val color = (sg["color"] as? Number)?.toInt()

            if (weeks.isEmpty()) continue

            courses.add(mapOf(
                "name" to name,
                "teacher" to teacher,
                "classroom" to position,
                "dayOfWeek" to day,
                "startSection" to startSection,
                "endSection" to endSection,
                "selectedWeeks" to weeks,
                "shiguangColor" to (color ?: -1)
            ))
        }

        @Suppress("UNCHECKED_CAST")
        val config = data["config"] as? Map<String, Any>
        val settings = mutableMapOf<String, Any>()
        if (config != null) {
            (config["semesterStartDate"] as? String)?.let { settings["class_start_time"] = it }
            (config["semesterTotalWeeks"] as? Number)?.toInt()?.let { settings["total_weeks"] = it }
            (config["firstDayOfWeek"] as? Number)?.toInt()?.let { settings["first_day_of_week"] = it }
        }

        @Suppress("UNCHECKED_CAST")
        val timeSlots = data["timeSlots"] as? List<Map<String, Any>>
        val times = mutableMapOf<String, Any>()
        if (!timeSlots.isNullOrEmpty()) {
            val morningTimes = mutableMapOf<String, String>()
            val afternoonTimes = mutableMapOf<String, String>()
            val eveningTimes = mutableMapOf<String, String>()

            for (slot in timeSlots) {
                val number = (slot["number"] as? Number)?.toInt() ?: continue
                val startTime = slot["startTime"] as? String ?: continue
                val endTime = slot["endTime"] as? String ?: continue
                val timeStr = "$startTime-$endTime"

                when {
                    number <= 6 -> morningTimes[number.toString()] = timeStr
                    number <= 12 -> afternoonTimes[(number - 6).toString()] = timeStr
                    else -> eveningTimes[(number - 12).toString()] = timeStr
                }
            }

            if (morningTimes.isNotEmpty()) times["morning"] = morningTimes
            if (afternoonTimes.isNotEmpty()) times["afternoon"] = afternoonTimes
            if (eveningTimes.isNotEmpty()) times["evening"] = eveningTimes
        }

        val result = mapOf<String, Any>(
            "schedule_name" to "拾光课程表导入",
            "courses" to courses,
            "settings" to settings,
            "times" to times
        )

        return Triple(true, "解析成功", result)
    } catch (e: Exception) {
        return Triple(false, "拾光课程表解析失败: ${e.message}", null)
    }
}

internal fun parseIcsFile(text: String): Triple<Boolean, String, Map<String, Any>?> {
    return try {
        // 同名课在同星期同时段时合并周次
        val courseGroups = mutableMapOf<String, MutableList<Map<String, Any>>>()
        val lines = text.lines()

        var currentEvent = mutableMapOf<String, String>()
        var inEvent = false

        for (line in lines) {
            val trimmed = line.trim().trimEnd('\r', '\n')
            when {
                trimmed == "BEGIN:VEVENT" -> {
                    inEvent = true
                    currentEvent = mutableMapOf()
                }
                trimmed == "END:VEVENT" -> {
                    inEvent = false
                    if (currentEvent.isNotEmpty()) {
                        val parsed = parseIcsEvent(currentEvent)
                        if (parsed != null) {
                            // 键含教室/教师，避免不同地点的同名课被误合并
                            val mergeKey = "${parsed["name"]}_${parsed["dayOfWeek"]}_${parsed["startTotalMinutes"]}_${parsed["classroom"]}_${parsed["teacher"]}"
                            courseGroups.getOrPut(mergeKey) { mutableListOf() }.add(parsed)
                        }
                    }
                }
                inEvent -> {
                    val colonIndex = trimmed.indexOf(':')
                    if (colonIndex > 0) {
                        var key = trimmed.substring(0, colonIndex)
                        val value = trimmed.substring(colonIndex + 1)
                        key = key.substringBefore(';')
                        currentEvent[key] = value
                    }
                }
            }
        }

        if (courseGroups.isEmpty()) {
            return Triple(false, "未找到课程事件", null)
        }

        // List<List<String>> 承载日期对，避免 Pair 序列化问题
        val mergedCourses = mutableListOf<Map<String, Any>>()
        for ((_, courseEvents) in courseGroups) {
            val firstEvent = courseEvents.first()
            val datePairs = mutableListOf<List<String>>()
            for (event in courseEvents) {
                val sd = event["startDate"] as? String
                val ud = event["untilDate"] as? String
                if (!sd.isNullOrEmpty()) {
                    datePairs.add(listOf(sd, ud ?: ""))
                }
            }
            mergedCourses.add(firstEvent + mapOf("datePairs" to datePairs))
        }

        val data = mapOf(
            "schedule_name" to "ICS导入课表",
            "courses" to mergedCourses,
            "settings" to emptyMap<String, Any>()
        )
        Triple(true, "成功", data)
    } catch (e: Exception) {
        Triple(false, "ICS解析失败: ${e.message}", null)
    }
}

private fun parseIcsEvent(event: Map<String, String>): Map<String, Any>? {
    val summary = event["SUMMARY"] ?: return null
    val location = event["LOCATION"] ?: ""

    val dtstart = event["DTSTART"] ?: return null
    val dtend = event["DTEND"] ?: return null

    // ICS: YYYYMMDDTHHMMSS[.Z]，全天事件仅 YYYYMMDD
    val startRaw = dtstart.substringAfter(":")
    val endRaw = dtend.substringAfter(":")

    val isAllDay = startRaw.length == 8 && !startRaw.contains('T')

    val startDateStr = startRaw.take(8)
    val startTimeStr = if (isAllDay) "080000" else startRaw.drop(9).take(6)
    val endTimeStr = if (isAllDay) "090000" else endRaw.drop(9).take(6)

    val startYear = startDateStr.substring(0, 4).toIntOrNull() ?: return null
    val startMonth = startDateStr.substring(4, 6).toIntOrNull() ?: return null
    val startDay = startDateStr.substring(6, 8).toIntOrNull() ?: return null

    val startHour = startTimeStr.substring(0, 2).toIntOrNull() ?: 8
    val startMinute = startTimeStr.substring(2, 4).toIntOrNull() ?: 0
    val endHour = endTimeStr.substring(0, 2).toIntOrNull() ?: (startHour + 1)
    val endMinute = endTimeStr.substring(2, 4).toIntOrNull() ?: 0

    // 只返回分钟数，节次映射由 applyScheduleData 用用户配置完成
    val startTotalMinutes = startHour * 60 + startMinute
    val endTotalMinutes = endHour * 60 + endMinute

    val startDate = LocalDate.of(startYear, startMonth, startDay)
    val dayOfWeek = startDate.dayOfWeek.value

    val rrule = event["RRULE"] ?: ""
    var untilStr = ""
    var countStr = ""
    var byDayStr = ""

    for (part in rrule.split(";")) {
        when {
            part.startsWith("UNTIL=") -> untilStr = part.substringAfter("UNTIL=").take(8)
            part.startsWith("COUNT=") -> countStr = part.substringAfter("COUNT=")
            part.startsWith("BYDAY=") -> byDayStr = part.substringAfter("BYDAY=")
        }
    }

    if (byDayStr.isNotEmpty()) {
        val dayMap = mapOf("MO" to 1, "TU" to 2, "WE" to 3, "TH" to 4, "FR" to 5, "SA" to 6, "SU" to 7)
        val byDays = byDayStr.split(",").mapNotNull { dayMap[it.trim()] }
        if (byDays.isNotEmpty() && dayOfWeek !in byDays) {
            return null
        }
    }

    // LOCATION 约定：前导空格=仅教师；尾随/无空格=仅教室；中间空格分隔两者
    val classroom: String
    val teacher: String
    when {
        location.startsWith(" ") -> {
            classroom = ""
            teacher = location.trim()
        }
        location.trimEnd().endsWith(" ") || !location.contains(" ") -> {
            classroom = location.trim()
            teacher = ""
        }
        else -> {
            val spaceIndex = location.indexOf(' ')
            classroom = location.substring(0, spaceIndex).trim()
            teacher = location.substring(spaceIndex + 1).trim()
        }
    }

    return mapOf(
        "name" to summary,
        "classroom" to classroom,
        "teacher" to teacher,
        "dayOfWeek" to dayOfWeek,
        "startTotalMinutes" to startTotalMinutes,
        "endTotalMinutes" to endTotalMinutes,
        "selectedWeeks" to emptyList<Int>(),
        "startDate" to startDateStr,
        "untilDate" to untilStr,
        "count" to countStr,
        "isAllDay" to isAllDay
    )
}

internal fun applyScheduleData(
    @Suppress("UNUSED_PARAMETER") context: Context,
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    scheduleName: String,
    data: Map<String, Any>
): Pair<Boolean, String> {
    try {
        if (scheduleName in scheduleViewModel.scheduleNames.value) {
            return Pair(false, "课表「$scheduleName」已存在")
        }
        scheduleViewModel.addSchedule(scheduleName)

        @Suppress("UNCHECKED_CAST")
        val coursesData = data["courses"] as? List<Map<String, Any>>
        val courses = mutableListOf<Course>()
        val courseNameColorMap = mutableMapOf<String, Long>()
        var colorIndex = 0

        val classStartTime = viewModel.classStartTime.value
        val defaultClassStartDate = try {
            LocalDate.parse(classStartTime.replace("/", "-"))
        } catch (_: Exception) {
            val today = LocalDate.now()
            today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks(16)
        }

        // ICS 无开学日设置时，用最早课程所在周的周一反推
        var icsClassStartDate: LocalDate? = null
        coursesData?.forEach { courseMap ->
            val startDateStr = courseMap["startDate"] as? String
            if (startDateStr != null && startDateStr.length == 8) {
                val date = try {
                    LocalDate.of(
                        startDateStr.substring(0, 4).toInt(),
                        startDateStr.substring(4, 6).toInt(),
                        startDateStr.substring(6, 8).toInt()
                    )
                } catch (_: Exception) { null }
                if (date != null) {
                    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
                    if (icsClassStartDate == null || monday.isBefore(icsClassStartDate)) {
                        icsClassStartDate = monday
                    }
                }
            }
        }
        val classStartDate = icsClassStartDate ?: defaultClassStartDate

        val classStartMonday = classStartDate.minusDays((classStartDate.dayOfWeek.value - 1).toLong())

        val userSectionTimes = settingsViewModel.sectionTimes.value
        fun findSectionByTime(startMinutes: Int, endMinutes: Int): Pair<Int, Int>? {
            var foundStart: Int? = null
            var foundEnd: Int? = null
            for ((section, timeRange) in userSectionTimes) {
                val parts = timeRange.split("-")
                if (parts.size != 2) continue
                val rangeStartParts = parts[0].split(":")
                val rangeEndParts = parts[1].split(":")
                if (rangeStartParts.size != 2 || rangeEndParts.size != 2) continue
                val rangeStart = (rangeStartParts[0].toIntOrNull() ?: continue) * 60 + (rangeStartParts[1].toIntOrNull() ?: continue)
                val rangeEnd = (rangeEndParts[0].toIntOrNull() ?: continue) * 60 + (rangeEndParts[1].toIntOrNull() ?: continue)
                if (startMinutes in rangeStart until rangeEnd) {
                    foundStart = section
                }
                // 结束时间允许落在节次末尾
                if (endMinutes in (rangeStart + 1)..rangeEnd) {
                    foundEnd = section
                }
            }
            if (foundStart != null && foundEnd != null) {
                return Pair(foundStart, foundEnd)
            }
            // 无精确匹配时退回起始节次
            if (foundStart != null) {
                return Pair(foundStart, foundStart)
            }
            return null
        }

        coursesData?.forEach { courseMap ->
            val name = courseMap["name"] as? String ?: return@forEach
            val classroom = courseMap["classroom"] as? String ?: ""
            val teacher = courseMap["teacher"] as? String ?: ""
            val dayOfWeek = (courseMap["dayOfWeek"] as? Number)?.toInt() ?: return@forEach
            @Suppress("UNCHECKED_CAST")
            var selectedWeeks = (courseMap["selectedWeeks"] as? List<Number>)?.map { it.toInt() } ?: emptyList()

            // JSON 导出有 startSection；ICS 需按时间映射节次
            val directStartSection = (courseMap["startSection"] as? Number)?.toInt()
            val directEndSection = (courseMap["endSection"] as? Number)?.toInt()
            val sectionPair = if (directStartSection != null && directEndSection != null) {
                Pair(directStartSection, directEndSection)
            } else {
                val startTotalMinutes = (courseMap["startTotalMinutes"] as? Number)?.toInt()
                val endTotalMinutes = (courseMap["endTotalMinutes"] as? Number)?.toInt()
                if (startTotalMinutes != null && endTotalMinutes != null) {
                    findSectionByTime(startTotalMinutes, endTotalMinutes)
                } else null
            }

            if (sectionPair == null) return@forEach
            val (startSection, endSection) = sectionPair

            if (selectedWeeks.isEmpty()) {
                @Suppress("UNCHECKED_CAST")
                val datePairs = courseMap["datePairs"] as? List<List<String>>
                if (!datePairs.isNullOrEmpty()) {
                    val allWeeks = mutableSetOf<Int>()
                    for (pair in datePairs) {
                        if (pair.isEmpty()) continue
                        val sd = pair[0]
                        val ud = if (pair.size > 1) pair[1] else ""
                        if (sd.length == 8) {
                            val courseStartDate = try {
                                LocalDate.of(
                                    sd.substring(0, 4).toInt(),
                                    sd.substring(4, 6).toInt(),
                                    sd.substring(6, 8).toInt()
                                )
                            } catch (_: Exception) { null }

                            if (courseStartDate != null) {
                                val courseMonday = courseStartDate.minusDays((courseStartDate.dayOfWeek.value - 1).toLong())
                                val startWeek = ChronoUnit.WEEKS.between(classStartMonday, courseMonday).toInt() + 1

                                val endWeek = if (ud.length == 8) {
                                    val untilDate = try {
                                        LocalDate.of(
                                            ud.substring(0, 4).toInt(),
                                            ud.substring(4, 6).toInt(),
                                            ud.substring(6, 8).toInt()
                                        )
                                    } catch (_: Exception) { null }
                                    if (untilDate != null) {
                                        val daysDiff = (untilDate.dayOfWeek.value - dayOfWeek + 7) % 7
                                        val lastCourseDate = untilDate.minusDays(daysDiff.toLong())
                                        val lastCourseMonday = lastCourseDate.minusDays((lastCourseDate.dayOfWeek.value - 1).toLong())
                                        ChronoUnit.WEEKS.between(classStartMonday, lastCourseMonday).toInt() + 1
                                    } else {
                                        startWeek
                                    }
                                } else {
                                    startWeek
                                }

                                allWeeks.addAll(startWeek..endWeek)
                            }
                        }
                    }
                    selectedWeeks = allWeeks.sorted()
                }
            }

            if (selectedWeeks.isEmpty()) {
                val countStr = courseMap["count"] as? String
                val startDateStr = courseMap["startDate"] as? String
                if (!countStr.isNullOrEmpty() && !startDateStr.isNullOrEmpty() && startDateStr.length == 8) {
                    val count = countStr.toIntOrNull()
                    if (count != null && count > 0) {
                        val courseStartDate = try {
                            LocalDate.of(
                                startDateStr.substring(0, 4).toInt(),
                                startDateStr.substring(4, 6).toInt(),
                                startDateStr.substring(6, 8).toInt()
                            )
                        } catch (_: Exception) { null }
                        if (courseStartDate != null) {
                            val courseMonday = courseStartDate.minusDays((courseStartDate.dayOfWeek.value - 1).toLong())
                            val startWeek = ChronoUnit.WEEKS.between(classStartMonday, courseMonday).toInt() + 1
                            selectedWeeks = (startWeek until startWeek + count).toList()
                        }
                    }
                }
            }

            if (selectedWeeks.isNotEmpty()) {
                val colorRes = courseNameColorMap.getOrPut(name) {
                    val color = Course.courseColors[colorIndex % Course.courseColors.size]
                    colorIndex++
                    color
                }
                courses.add(
                    Course(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        classroom = classroom,
                        teacher = teacher,
                        dayOfWeek = dayOfWeek,
                        startSection = startSection,
                        endSection = endSection,
                        isCustomTime = (courseMap["isCustomTime"] as? Boolean) ?: false,
                        customStartTime = courseMap["customStartTime"] as? String,
                        customEndTime = courseMap["customEndTime"] as? String,
                        startWeek = selectedWeeks.min(),
                        endWeek = selectedWeeks.max(),
                        weekType = Course.WEEK_TYPE_ALL,
                        selectedWeeks = selectedWeeks,
                        colorRes = colorRes
                    )
                )
            }
        }

        scheduleViewModel.saveCoursesToSchedule(scheduleName, courses)

        // 先刷摘要再继续，否则切换页课程数会短暂错
        scheduleViewModel.refreshScheduleList()

        @Suppress("UNCHECKED_CAST")
        val settings = data["settings"] as? Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val times = data["times"] as? Map<String, Any>
        val importedMorningSections = (settings?.get("morning_sections") as? Number)?.toInt()
        val importedAfternoonSections = (settings?.get("afternoon_sections") as? Number)?.toInt()
        val importedEveningSections = (settings?.get("evening_sections") as? Number)?.toInt()

        if (importedMorningSections != null || importedAfternoonSections != null || importedEveningSections != null || times != null) {
            val sectionTimesMap = mutableMapOf<String, String>()
            if (times != null) {
                @Suppress("UNCHECKED_CAST")
                (times["morning"] as? Map<String, String>)?.forEach { (k, v) -> sectionTimesMap["morning_$k"] = v }
                @Suppress("UNCHECKED_CAST")
                (times["afternoon"] as? Map<String, String>)?.forEach { (k, v) -> sectionTimesMap["afternoon_$k"] = v }
                @Suppress("UNCHECKED_CAST")
                (times["evening"] as? Map<String, String>)?.forEach { (k, v) -> sectionTimesMap["evening_$k"] = v }
            }
            @Suppress("UNCHECKED_CAST")
            val importedSectionNames = (times?.get("section_names") as? Map<String, String>) ?: emptyMap()

            val newConfig = TimeConfig(
                name = scheduleName,
                morningSections = importedMorningSections ?: 4,
                afternoonSections = importedAfternoonSections ?: 4,
                eveningSections = importedEveningSections ?: 4,
                sectionTimes = sectionTimesMap,
                sectionNames = importedSectionNames
            )
            val newConfigId = scheduleViewModel.addTimeConfig(newConfig)
            scheduleViewModel.setScheduleTimeConfigId(scheduleName, newConfigId)
        } else {
            val currentScheduleTimeConfigId = scheduleViewModel.getCurrentScheduleTimeConfigId()
            if (currentScheduleTimeConfigId != 0L) {
                scheduleViewModel.setScheduleTimeConfigId(scheduleName, currentScheduleTimeConfigId)
            }
        }

        if (settings != null) {
            scheduleViewModel.switchToSchedule(scheduleName)

            (settings["class_start_time"] as? String)?.let { viewModel.setClassStartTime(it) }
            (settings["total_weeks"] as? Number)?.toInt()?.let { viewModel.setTotalWeeks(it) }
            (settings["smart_weekend"] as? Boolean)?.let {
                settingsViewModel.setSmartWeekend(it)
            }
            @Suppress("UNCHECKED_CAST")
            (settings["show_weekend_days"] as? List<Number>)?.let {
                if (it.isNotEmpty()) settingsViewModel.setSmartWeekend(true)
            }
            (settings["show_non_current_week"] as? Boolean)?.let {
                settingsViewModel.setShowNonCurrentWeek(it)
            }
            (settings["morning_sections"] as? Number)?.toInt()?.let { settingsViewModel.setMorningSections(it) }
            (settings["afternoon_sections"] as? Number)?.toInt()?.let { settingsViewModel.setAfternoonSections(it) }
            (settings["evening_sections"] as? Number)?.toInt()?.let { settingsViewModel.setEveningSections(it) }

            @Suppress("UNCHECKED_CAST")
            val times = data["times"] as? Map<String, Any>
            if (times != null) {
                val morningTimes = mutableMapOf<Int, String>()
                val afternoonTimes = mutableMapOf<Int, String>()
                val eveningTimes = mutableMapOf<Int, String>()

                @Suppress("UNCHECKED_CAST")
                (times["morning"] as? Map<String, String>)?.forEach { (k, v) ->
                    k.toIntOrNull()?.let { morningTimes[it] = v }
                }
                @Suppress("UNCHECKED_CAST")
                (times["afternoon"] as? Map<String, String>)?.forEach { (k, v) ->
                    k.toIntOrNull()?.let { afternoonTimes[it] = v }
                }
                @Suppress("UNCHECKED_CAST")
                (times["evening"] as? Map<String, String>)?.forEach { (k, v) ->
                    k.toIntOrNull()?.let { eveningTimes[it] = v }
                }

                if (morningTimes.isNotEmpty()) settingsViewModel.saveMorningTimes(morningTimes)
                if (afternoonTimes.isNotEmpty()) settingsViewModel.saveAfternoonTimes(afternoonTimes)
                if (eveningTimes.isNotEmpty()) settingsViewModel.saveEveningTimes(eveningTimes)
            }
        }

        viewModel.reloadCourses()
        settingsViewModel.refreshSettings()
        scheduleViewModel.refreshScheduleList()

        return Pair(true, "成功导入课表「$scheduleName」\n共${courses.size}门课程")
    } catch (e: Exception) {
        return Pair(false, "导入失败: ${e.message}")
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
