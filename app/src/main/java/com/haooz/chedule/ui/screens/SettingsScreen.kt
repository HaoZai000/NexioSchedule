/** 设置页面 - 应用全局设置 */
package com.haooz.chedule.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.WebDavManager
import com.haooz.chedule.ui.activities.AboutActivity
import com.haooz.chedule.ui.activities.CourseReminderActivity
import com.haooz.chedule.ui.activities.CourseTimeSettingsActivity
import com.haooz.chedule.ui.activities.PreferenceSettingsActivity
import com.haooz.chedule.ui.activities.WidgetIntroActivity
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.LocalDate
import java.util.Calendar
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * 解析日期字符串 "YYYY/MM/DD" 为年、月、日
 */
private fun parseDate(dateStr: String): Triple<Int, Int, Int> {
    return try {
        val parts = dateStr.split("/")
        Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    } catch (_: Exception) {
        val cal = Calendar.getInstance()
        Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }
}

/**
 * 获取指定年月的天数
 */
private fun getDaysInMonth(year: Int, month: Int): Int {
    return try {
        LocalDate.of(year, month, 1).lengthOfMonth()
    } catch (_: Exception) {
        31
    }
}

/**
 * 设置页面
 */
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
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    onScrollYChanged: (Int) -> Unit = {}
) {
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val isSemesterStarted by viewModel.isSemesterStarted.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val showWeekendDays by settingsViewModel.showWeekendDays.collectAsState()
    val showNonCurrentWeek by settingsViewModel.showNonCurrentWeek.collectAsState()
    val morningSections by settingsViewModel.morningSections.collectAsState()
    val afternoonSections by settingsViewModel.afternoonSections.collectAsState()
    val eveningSections by settingsViewModel.eveningSections.collectAsState()
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()
    val shiftSelectedSchedules by shiftViewModel.shiftSelectedSchedules.collectAsState()
    val defaultHomepage by settingsViewModel.defaultHomepage.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var listScrollY by remember { mutableIntStateOf(0) }
    var showShiftModeConfirmDialog by remember { mutableStateOf(false) }
    var showNewSemesterDialog by remember { mutableStateOf(false) }
    var newSemesterName by remember { mutableStateOf("") }
    var showDonateDialog by remember { mutableStateOf(false) }

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

    // 解析开始日期
    val (tempYearInit, tempMonthInit, tempDayInit) = parseDate(classStartTime)

    // 弹窗状态
    var showCurrentWeekDialog by remember { mutableStateOf(false) }
    var showTotalWeeksDialog by remember { mutableStateOf(false) }
    var showStartDateDialog by remember { mutableStateOf(false) }
    var showSectionDialog by remember { mutableStateOf(false) }
    var showImportPromptDialog by remember { mutableStateOf(false) }
    var showImportInputDialog by remember { mutableStateOf(false) }
    var importInputText by remember { mutableStateOf("") }

    // 完整课表导入相关状态
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var pendingImportScheduleName by remember { mutableStateOf("") }

    // WebDAV 云同步
    val webDavManager = remember { WebDavManager(context) }
    val lastSyncTimeMs = webDavManager.lastSyncTime
    val lastSyncSummary = remember(lastSyncTimeMs) {
        if (lastSyncTimeMs > 0L) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            "上次同步: ${sdf.format(java.util.Date(lastSyncTimeMs))}"
        } else "未同步"
    }

    // 教务导入仓库源设置
    val coroutineScope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val text = inputStream?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                inputStream?.close()

                if (text.isNotBlank()) {
                    val fileName = uri.lastPathSegment?.lowercase() ?: ""
                    val isIcs = fileName.endsWith(".ics")

                    val (success, message, data) = if (isIcs) parseIcsFile(text) else parseFullScheduleJson(text)
                    if (success && data != null) {
                        val scheduleName = if (isIcs) "ICS导入课表" else (data["schedule_name"] as? String) ?: "导入的课表"
                        pendingImportData = data
                        pendingImportScheduleName = scheduleName
                        showImportConfirmDialog = true
                    } else {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "文件读取失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 临时选择状态
    var tempCurrentWeek by remember { mutableIntStateOf(currentWeek) }
    var tempTotalWeeks by remember { mutableIntStateOf(totalWeeks) }
    var tempYear by remember { mutableIntStateOf(tempYearInit) }
    var tempMonth by remember { mutableIntStateOf(tempMonthInit) }
    var tempDay by remember { mutableIntStateOf(tempDayInit) }
    var tempMorningSections by remember { mutableIntStateOf(morningSections) }
    var tempAfternoonSections by remember { mutableIntStateOf(afternoonSections) }
    var tempEveningSections by remember { mutableIntStateOf(eveningSections) }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()

    val appStyle = rememberAppStyle()
    val isLiquidGlass = appStyle == "liquidglass" && liquidGlassBackdrop != null
    val isTabletLiquidGlass = navBarStyle == "rail" && isLiquidGlass

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
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemScrollOffset }
                    .collect { offset ->
                        listScrollY = offset
                        onScrollYChanged(offset)
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
                    SmallTitle(
                        text = "基本设置",
                        modifier = Modifier.offset(x = (-15).dp)
                    )
                    // 基本设置卡片
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

                            // 当前周数
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

                            // 本学期总周数
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

                            // 显示周末设置
                            val weekendEntries = listOf(
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "不显示",
                                            selected = showWeekendDays.isEmpty(),
                                            onClick = {
                                                settingsViewModel.setShowWeekendDays(emptySet())
                                            }
                                        ),
                                    )
                                ),
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "仅周六",
                                            selected = showWeekendDays == setOf(6),
                                            onClick = {
                                                settingsViewModel.setShowWeekendDays(setOf(6))
                                            }
                                        ),
                                        DropdownItem(
                                            text = "仅周日",
                                            selected = showWeekendDays == setOf(7),
                                            onClick = {
                                                settingsViewModel.setShowWeekendDays(setOf(7))
                                            }
                                        ),
                                        DropdownItem(
                                            text = "周六与周日",
                                            selected = showWeekendDays == setOf(6, 7),
                                            onClick = {
                                                settingsViewModel.setShowWeekendDays(setOf(6, 7))
                                            }
                                        ),
                                    )
                                )
                            )

                            OverlayDropdownPreference(
                                title = "显示周末",
                                entries = weekendEntries,
                                collapseOnSelection = true
                            )

                            // 显示非本周课程开关
                            if (!isShiftMode) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            settingsViewModel.setShowNonCurrentWeek(!showNonCurrentWeek)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "显示非本周课程",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Switch(
                                        checked = showNonCurrentWeek,
                                        onCheckedChange = {
                                            settingsViewModel.setShowNonCurrentWeek(it)
                                        }
                                    )
                                }
                            }

                            // 课程表节数设置
                            ArrowPreference(
                                title = "课表节数设置",
                                endActions = {
                                    Text(
                                        text = "$morningSections·$afternoonSections·$eveningSections",
                                        fontSize = 14.5.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = {
                                    tempMorningSections = morningSections
                                    tempAfternoonSections = afternoonSections
                                    tempEveningSections = eveningSections
                                    showSectionDialog = true
                                },
                                holdDownState = showSectionDialog
                            )

                            // 课表时间设置
                            ArrowPreference(
                                title = "课程时间设置",
                                onClick = {
                                    val intent =
                                        Intent(context, CourseTimeSettingsActivity::class.java)
                                    courseTimeSettingsLauncher.launch(intent)
                                }
                            )
                        }
                    }
                }

                // 特色功能分类
                if (!isShiftMode) {
                    item {
                        SmallTitle(
                            text = "特色功能",
                            modifier = Modifier.offset(x = (-15).dp)
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
                                    onClick = {
                                        val intent = Intent(context, CourseReminderActivity::class.java)
                                        reminderSettingsLauncher.launch(intent)
                                    }
                                )
                                ArrowPreference(
                                    title = "桌面小部件",
                                    onClick = {
                                        val intent = Intent(context, WidgetIntroActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                                ArrowPreference(
                                    title = "排班课表",
                                    summary = "对比查看多个课表的排班情况",
                                    onClick = {
                                        showShiftModeConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                // 排班模式设置（仅在排班模式下显示）
                if (isShiftMode) {
                    item {
                        SmallTitle(
                            text = "选择对比课表",
                            modifier = Modifier.offset(x = (-15).dp)
                        )
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                scheduleNames.forEach { name ->
                                    val summary = scheduleViewModel.scheduleSummaries.collectAsState().value[name] ?: ""
                                    CheckboxPreference(
                                        title = name,
                                        summary = summary,
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

                    item {
                        top.yukonga.miuix.kmp.basic.Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, end= 16.dp, start = 16.dp)
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

                // 导入导出分类
                if (!isShiftMode) {
                    item {
                        SmallTitle(
                            text = "导入导出",
                            modifier = Modifier.offset(x = (-15).dp)
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
                                    onClick = {
                                        importInputText = ""
                                        showImportPromptDialog = true
                                    }
                                )
                                ArrowPreference(
                                    title = "教务系统导入",
                                    onClick = {
                                        val intent = android.content.Intent(context, com.haooz.chedule.ui.activities.EducationalImportActivity::class.java)
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
                                    title = "课表导入",
                                    summary = "导入JSON或ICS格式的课表文件",
                                    onClick = {
                                        filePickerLauncher.launch(arrayOf("application/json", "text/calendar", "*/*"))
                                    }
                                )
                                ArrowPreference(
                                    title = "课表导出",
                                    summary = "以JSON格式导出完整课表数据",
                                    onClick = {
                                        exportSchedule(context, viewModel, scheduleViewModel, settingsViewModel)
                                    }
                                )
                            }
                        }
                    }
                }

                // 其他分类
                if (!isShiftMode) {
                    item {
                        SmallTitle(
                            text = "其他",
                            modifier = Modifier.offset(x = (-15).dp)
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
                    item {
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
                                    onClick = {
                                        val intent = Intent(context, PreferenceSettingsActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                                ArrowPreference(
                                    title = "WebDav云同步",
                                    summary = if (webDavManager.isConfigured()) lastSyncSummary else "配置服务器后可手动同步",
                                    onClick = {
                                        val intent = Intent(context, com.haooz.chedule.ui.activities.WebDavSettingsActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                                ArrowPreference(
                                    title = "关于应用",
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

        // 排班模式确认弹窗
        OverlayDialog(
            title = "进入排班模式",
            summary = "将切换到排班课表模式，可同时对比多个课表的排班情况。确定进入？",
            show = showShiftModeConfirmDialog,
            onDismissRequest = { showShiftModeConfirmDialog = false },
            outsideMargin = DpSize(17.dp, 12.dp),
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
                                kotlinx.coroutines.delay(100)
                                onEnterShiftMode()
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 开启新学期弹窗
        OverlayDialog(
            title = "开启新学期",
            summary = "将复用当前课表的所有设置数据，创建一个清空课程的新课表",
            show = showNewSemesterDialog,
            outsideMargin = DpSize(17.dp, 12.dp),
            onDismissRequest = {
                showNewSemesterDialog = false
                newSemesterName = ""
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextField(
                    value = newSemesterName,
                    onValueChange = { newSemesterName = it },
                    label = "新课表名称",
                    modifier = Modifier.fillMaxWidth()
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
                                scheduleViewModel.createNewSemesterSchedule(name)
                                showNewSemesterDialog = false
                                newSemesterName = ""
                                Toast.makeText(context, "「${name}」创建成功", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 开始上课日期弹窗
        OverlayDialog(
            title = "开始上课日期",
            show = showStartDateDialog,
            outsideMargin = DpSize(17.dp, 12.dp),
            onDismissRequest = { showStartDateDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 计算当前月份的天数
                val maxDaysInMonth = remember(tempYear, tempMonth) {
                    getDaysInMonth(tempYear, tempMonth)
                }
                // 如果当前日期超过该月最大天数，自动调整
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

        // 当前周次弹窗
        OverlayDialog(
            title = "选择当前周次",
            show = showCurrentWeekDialog,
            outsideMargin = DpSize(17.dp, 12.dp),
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

        // 总周数弹窗
        OverlayDialog(
            title = "选择学期总周数",
            show = showTotalWeeksDialog,
            outsideMargin = DpSize(17.dp, 12.dp),
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

        // 节数设置弹窗 — 三个选择器并排显示
        OverlayDialog(
            title = "课表节数设置",
            show = showSectionDialog,
            outsideMargin = DpSize(17.dp, 12.dp),
            onDismissRequest = { showSectionDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 上午节数
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "上午",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        NumberPicker(
                            value = tempMorningSections,
                            onValueChange = { tempMorningSections = it },
                            range = 0..6,
                            visibleItemCount = 3,
                            itemHeight = 50.dp
                        )
                    }

                    // 下午节数
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "下午",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        NumberPicker(
                            value = tempAfternoonSections,
                            onValueChange = { tempAfternoonSections = it },
                            range = 0..6,
                            visibleItemCount = 3,
                            itemHeight = 50.dp
                        )
                    }

                    // 晚上节数
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "晚上",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        NumberPicker(
                            value = tempEveningSections,
                            onValueChange = { tempEveningSections = it },
                            range = 0..6,
                            visibleItemCount = 3,
                            itemHeight = 50.dp
                        )
                    }
                }

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
                            showSectionDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            settingsViewModel.setMorningSections(tempMorningSections)
                            settingsViewModel.setAfternoonSections(tempAfternoonSections)
                            settingsViewModel.setEveningSections(tempEveningSections)
                            showSectionDialog = false
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 文本导入提示词弹窗
        val importPromptText = remember {
            """请根据我提供的课表信息，按照以下格式返回每门课程的数据：

每门课程一行，用"|"分隔各字段：
课程名称|教室|教师|星期几|开始节次|结束节次|周次

字段说明：
- 课程名称：课程名称（必填）
- 教室：上课地点（无法识别则留空）
- 教师：授课教师（无法识别则留空）
- 星期几：1=周一, 2=周二, 3=周三, 4=周四, 5=周五, 6=周六, 7=周日（必填）
- 开始节次：该课程在这天的起始节次（必填）
- 结束节次：该课程在这天的结束节次（必填）
- 周次：上课的周次，用英文逗号分隔每个周次
  例：第1至6周和第10至12周，应写为 1,2,3,4,5,6,10,11,12
  例：第1、3、5、7、9周，应写为 1,3,5,7,9
  例：第1至12周，应写为 1,2,3,4,5,6,7,8,9,10,11,12

无法识别的字段请留空（两个竖线之间不写内容），但必填字段必须填写，教室不同需分开输出。
示例：
高等数学|A101|张三|1|1|2|1,2,3,4,5,6,7,8,9,10
大学英语|||3|3|4|1,2,3,4,5,6,7,8

请严格按照此格式返回，每行一门课程，不要添加其他说明文字。"""
        }

        OverlayDialog(
            title = "文本导入",
            summary = "请将以下提示词发送给AI\n将AI返回的文本粘贴到下一弹窗",
            show = showImportPromptDialog,
            outsideMargin = DpSize(17.dp, 12.dp),
            onDismissRequest = { showImportPromptDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = importPromptText,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
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
                            showImportPromptDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "复制并继续",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("课表导入提示词", importPromptText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "提示词已复制到剪切板", Toast.LENGTH_SHORT)
                                .show()
                            showImportPromptDialog = false
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(150.milliseconds)
                                showImportInputDialog = true
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        val importInputRequester = remember { FocusRequester() }
        LaunchedEffect(showImportInputDialog) {
            if (showImportInputDialog) {
                kotlinx.coroutines.delay(180.milliseconds)
                importInputRequester.requestFocus()
            }
        }

        // 文本导入输入弹窗
        OverlayDialog(
            title = "导入课程数据",
            summary = "请粘贴AI返回的课表数据",
            outsideMargin = DpSize(17.dp, 12.dp),
            show = showImportInputDialog,
            onDismissRequest = {
                showImportInputDialog = false
                importInputText = ""
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (showImportInputDialog) {
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(150)
                        importInputRequester.requestFocus()
                    }
                }
                TextField(
                    value = importInputText,
                    onValueChange = { importInputText = it },
                    label = "粘贴课表数据",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .focusRequester(importInputRequester)
                )
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
                            showImportInputDialog = false
                            importInputText = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (importInputText.isNotBlank()) {
                                val (courses, warnings) = parseImportedCourses(importInputText)
                                if (courses.isNotEmpty()) {
                                    viewModel.replaceCourses(courses)
                                    val message = buildString {
                                        append("成功导入${courses.size}门课程")
                                        if (warnings.isNotEmpty()) {
                                            append("\n${warnings.size}条提示:")
                                            warnings.take(3).forEach { append("\n$it") }
                                            if (warnings.size > 3) append("\n...")
                                        }
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "未识别到有效课程数据",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            showImportInputDialog = false
                            importInputText = ""
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // 完整课表导入确认弹窗
    if (showImportConfirmDialog && pendingImportData != null) {
        OverlayDialog(
            title = "导入课表",
            summary = "是否导入课表「$pendingImportScheduleName」？\n确定导入将创建一个新的课表",
            show = true,
            outsideMargin = DpSize(17.dp, 12.dp),
            onDismissRequest = {
                showImportConfirmDialog = false
                pendingImportData = null
                pendingImportScheduleName = ""
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextField(
                    value = pendingImportScheduleName,
                    onValueChange = { pendingImportScheduleName = it },
                    label = "课表名称",
                    modifier = Modifier.fillMaxWidth()
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
                            showImportConfirmDialog = false
                            pendingImportData = null
                            pendingImportScheduleName = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定导入",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (pendingImportScheduleName.isNotBlank() && pendingImportData != null) {
                                val (_, message) = applyScheduleData(
                                    context,
                                    viewModel,
                                    scheduleViewModel,
                                    settingsViewModel,
                                    pendingImportScheduleName,
                                    pendingImportData!!
                                )
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                            showImportConfirmDialog = false
                            pendingImportData = null
                            pendingImportScheduleName = ""
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
 * 解析导入的课程文本数据
 * 格式: 课程名称|教室|教师|星期几|开始节次|结束节次|周次
 * 周次格式: 1-6,10-12 或 1,3,5 或 1-16
 * 部分字段可留空，使用默认值
 * 同名同教室同节次的课程自动合并周次
 */
private fun parseImportedCourses(text: String): Pair<List<Course>, List<String>> {
    val warnings = mutableListOf<String>()
    val lines = text.trim().lines().filter { it.isNotBlank() }

    // 临时数据：key = 课程名|教室|教师|星期|开始节|结束节, value = 所有周次
    val mergedData = linkedMapOf<String, MutableSet<Int>>()
    // 保留每组的元数据
    val metaData = mutableMapOf<String, CourseMetaData>()

    for ((index, line) in lines.withIndex()) {
        try {
            val parts = line.split("|").map { it.trim() }
            if (parts.size < 7) {
                warnings.add("第${index + 1}行: 字段数量不足，已跳过")
                continue
            }

            val name = parts[0].ifBlank { "未命名课程" }
            val classroom = parts[1].ifBlank { "未指定教室" }
            val teacher = parts[2].ifBlank { "未指定教师" }
            val dayOfWeek = parts[3].toIntOrNull()
            val startSection = parts[4].toIntOrNull()
            val endSection = parts[5].toIntOrNull()
            val weekStr = parts[6].trim()

            if (dayOfWeek == null || dayOfWeek !in 1..7) {
                warnings.add("第${index + 1}行「${name}」: 星期几无效，已跳过")
                continue
            }
            if (startSection == null || startSection !in 1..12) {
                warnings.add("第${index + 1}行「${name}」: 开始节次无效，已跳过")
                continue
            }
            if (endSection == null || endSection !in 1..12 || endSection < startSection) {
                warnings.add("第${index + 1}行「${name}」: 结束节次无效，已跳过")
                continue
            }

            val selectedWeeks = parseWeekString(weekStr)
            if (selectedWeeks.isEmpty()) {
                warnings.add("第${index + 1}行「${name}」: 周次无效，已跳过")
                continue
            }

            // 检查可选字段是否缺失
            val missingFields = mutableListOf<String>()
            if (parts[1].isBlank()) missingFields.add("教室")
            if (parts[2].isBlank()) missingFields.add("教师")
            if (missingFields.isNotEmpty()) {
                warnings.add("第${index + 1}行「${name}」: 缺少${missingFields.joinToString("、")}")
            }

            // 合并键：课程名+教室+教师+星期+节次
            val key = "$name|$classroom|$teacher|$dayOfWeek|$startSection|$endSection"
            mergedData.getOrPut(key) { mutableSetOf() }.addAll(selectedWeeks)

            // 保存元数据（后续会覆盖，取最后一条即可）
            metaData[key] = CourseMetaData(name, classroom, teacher, dayOfWeek, startSection, endSection)

        } catch (_: Exception) {
            warnings.add("第${index + 1}行: 格式解析异常，已跳过")
            continue
        }
    }

    // 生成最终课程列表
    val courses = mutableListOf<Course>()
    val nameColorMap = mutableMapOf<String, Long>()
    var colorIndex = 0

    for ((key, weeks) in mergedData) {
        val meta = metaData[key] ?: continue
        val sortedWeeks = weeks.sorted()
        val minWeek = sortedWeeks.first()
        val maxWeek = sortedWeeks.last()

        val color = nameColorMap.getOrPut(meta.name) {
            val c = Course.courseColors[colorIndex % Course.courseColors.size]
            colorIndex++
            c
        }

        courses.add(
            Course(
                id = UUID.randomUUID().toString(),
                name = meta.name,
                classroom = meta.classroom,
                teacher = meta.teacher,
                dayOfWeek = meta.dayOfWeek,
                startSection = meta.startSection,
                endSection = meta.endSection,
                startWeek = minWeek,
                endWeek = maxWeek,
                weekType = Course.WEEK_TYPE_ALL,
                selectedWeeks = sortedWeeks,
                colorRes = color
            )
        )
    }

    return Pair(courses, warnings)
}

private data class CourseMetaData(
    val name: String,
    val classroom: String,
    val teacher: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int
)

/**
 * 解析周次字符串，支持格式:
 * "1,2,3,4,5,6,10,11,12" → [1,2,3,4,5,6,10,11,12]
 * "1-6,10-12" → [1,2,3,4,5,6,10,11,12] (兼容范围格式)
 */
private fun parseWeekString(weekStr: String): List<Int> {
    val weeks = mutableListOf<Int>()
    val parts = weekStr.split(",").map { it.trim() }

    for (part in parts) {
        if (part.contains("-")) {
            val range = part.split("-").mapNotNull { it.trim().toIntOrNull() }
            if (range.size == 2 && range[0] <= range[1]) {
                weeks.addAll(range[0]..range[1])
            }
        } else {
            part.toIntOrNull()?.let { weeks.add(it) }
        }
    }
    return weeks.distinct().sorted()
}

/**
 * 导出课表为JSON文件并调用系统分享
 */
private fun exportSchedule(context: Context, viewModel: CourseViewModel, scheduleViewModel: ScheduleViewModel, settingsViewModel: SettingsViewModel) {
    val courses = viewModel.courses.value
    if (courses.isEmpty()) {
        Toast.makeText(context, "当前课表为空，无法导出", Toast.LENGTH_SHORT).show()
        return
    }

    val data = mapOf(
        "schedule_name" to scheduleViewModel.currentScheduleName.value,
        "settings" to mapOf(
            "class_start_time" to viewModel.classStartTime.value,
            "current_week" to viewModel.currentWeek.value,
            "total_weeks" to viewModel.totalWeeks.value,
            "show_weekend_days" to settingsViewModel.showWeekendDays.value.toList(),
            "show_non_current_week" to settingsViewModel.showNonCurrentWeek.value,
            "morning_sections" to settingsViewModel.morningSections.value,
            "afternoon_sections" to settingsViewModel.afternoonSections.value,
            "evening_sections" to settingsViewModel.eveningSections.value
        ),
        "times" to mapOf(
            "morning" to settingsViewModel.getMorningTimes().mapKeys { it.key.toString() },
            "afternoon" to settingsViewModel.getAfternoonTimes().mapKeys { it.key.toString() },
            "evening" to settingsViewModel.getEveningTimes().mapKeys { it.key.toString() }
        ),
        "courses" to courses.map { course ->
            mapOf(
                "name" to course.name,
                "classroom" to course.classroom,
                "teacher" to course.teacher,
                "dayOfWeek" to course.dayOfWeek,
                "startSection" to course.startSection,
                "endSection" to course.endSection,
                "selectedWeeks" to (course.selectedWeeks.ifEmpty {
                    (course.startWeek..course.endWeek).toList()
                }).sorted()
            )
        }
    )

    val json = com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data)

    try {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val fileName = "${scheduleViewModel.currentScheduleName.value}.json"
        val file = java.io.File(dir, fileName)
        file.writeText(json, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "课表数据")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享课表"))
    } catch (e: Exception) {
        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 解析课表数据（JSON格式）
 * 返回 Triple: (是否成功, 消息, 解析出的数据用于后续处理)
 */
internal fun parseFullScheduleJson(text: String): Triple<Boolean, String, Map<String, Any>?> {
    try {
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
        val data: Map<String, Any> = gson.fromJson(text, type)

        if (!data.containsKey("settings") || !data.containsKey("courses")) {
            return Triple(false, "无效的课表数据格式", null)
        }

        return Triple(true, "解析成功", data)
    } catch (e: Exception) {
        return Triple(false, "解析失败: ${e.message}", null)
    }
}

/**
 * 将解析出的数据应用到新课表
 */
internal fun parseIcsFile(text: String): Triple<Boolean, String, Map<String, Any>?> {
    return try {
        // 按课程名称分组，合并同一课程的不同周次
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
                            val name = parsed["name"] as String
                            courseGroups.getOrPut(name) { mutableListOf() }.add(parsed)
                        }
                    }
                }
                inEvent -> {
                    val colonIndex = trimmed.indexOf(':')
                    if (colonIndex > 0) {
                        var key = trimmed.substring(0, colonIndex)
                        val value = trimmed.substring(colonIndex + 1)
                        // 移除 ;TZID=xxx 等后缀
                        key = key.substringBefore(';')
                        currentEvent[key] = value
                    }
                }
            }
        }

        if (courseGroups.isEmpty()) {
            return Triple(false, "未找到课程事件", null)
        }

        // 合并同一课程的不同周次
        val mergedCourses = mutableListOf<Map<String, Any>>()
        for ((_, courseEvents) in courseGroups) {
            val firstEvent = courseEvents.first()
            // 收集所有事件的日期对，用于后续计算周次
            val datePairs = mutableListOf<Pair<String, String>>()
            for (event in courseEvents) {
                val sd = event["startDate"] as? String
                val ud = event["untilDate"] as? String
                if (sd != null && ud != null) {
                    datePairs.add(sd to ud)
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

    // 提取日期和时间部分 (格式: YYYYMMDDTHHMMSS 或 YYYYMMDDTHHMMSSZ)
    val startDateTime = dtstart.substringAfter(":")
    val endDateTime = dtend.substringAfter(":")

    val startDateStr = startDateTime.take(8)
    val startTimeStr = startDateTime.drop(9).take(6)

    val endTimeStr = endDateTime.drop(9).take(6)

    // 解析日期
    val startYear = startDateStr.substring(0, 4).toIntOrNull() ?: return null
    val startMonth = startDateStr.substring(4, 6).toIntOrNull() ?: return null
    val startDay = startDateStr.substring(6, 8).toIntOrNull() ?: return null

    // 解析时间
    val startHour = startTimeStr.substring(0, 2).toIntOrNull() ?: 8
    val startMinute = startTimeStr.substring(2, 4).toIntOrNull() ?: 0

    // 根据时间计算节次 (1-2节: 8:00-9:50, 3-4节: 10:10-12:00, 5-6节: 14:00-15:50, 7-8节: 16:10-18:00, 9-10节: 19:00-20:50)
    val startTotalMinutes = startHour * 60 + startMinute

    val (startSection, endSection) = when {
        startTotalMinutes < 10 * 60 -> Pair(1, 2)  // 1-2节
        startTotalMinutes < 14 * 60 -> Pair(3, 4)  // 3-4节
        startTotalMinutes < 16 * 60 -> Pair(5, 6)  // 5-6节
        startTotalMinutes < 19 * 60 -> Pair(7, 8)  // 7-8节
        else -> Pair(9, 10)  // 9-10节
    }

    // 计算星期几 (1=周一, 7=周日)
    val startDate = LocalDate.of(startYear, startMonth, startDay)
    val dayOfWeek = startDate.dayOfWeek.value

    // 解析 RRULE 获取周次信息
    val rrule = event["RRULE"] ?: ""
    val untilStr = rrule.substringAfter("UNTIL=").substringBefore(";").take(8)

    // 计算周次：需要知道开学日期才能计算
    // 这里先返回原始日期，后续在 applyScheduleData 中根据实际开学日期计算
    val selectedWeeks = mutableListOf<Int>()
    // 临时标记：使用 -1 表示需要后续计算
    // 实际周次会在 applyScheduleData 中根据 classStartTime 计算

    // 解析教室和老师 (格式: "教室 老师" 或 "教室" 或 " 老师")
    val classroom: String
    val teacher: String
    when {
        location.startsWith(" ") -> {
            // 开头有空格：只有老师，没有地点 例如 " 测试老师"
            classroom = ""
            teacher = location.trim()
        }
        location.trimEnd().endsWith(" ") || !location.contains(" ") -> {
            // 结尾有空格或无空格：只有地点 例如 "测试地点 " 或 "测试地点"
            classroom = location.trim()
            teacher = ""
        }
        else -> {
            // 有空格分隔：地点 老师 例如 "安201 花爱阳"
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
        "startSection" to startSection,
        "endSection" to endSection,
        "selectedWeeks" to selectedWeeks,
        "startDate" to startDateStr,
        "untilDate" to untilStr
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
        // 创建新课表
        scheduleViewModel.addSchedule(scheduleName)

        // 保存课程数据到新课表
        @Suppress("UNCHECKED_CAST")
        val coursesData = data["courses"] as? List<Map<String, Any>>
        val courses = mutableListOf<Course>()
        val courseNameColorMap = mutableMapOf<String, Long>()
        var colorIndex = 0

        // 获取开学日期用于ICS周次计算
        val classStartTime = viewModel.classStartTime.value
        val defaultClassStartDate = try {
            LocalDate.parse(classStartTime.replace("/", "-"))
        } catch (_: Exception) {
            val today = LocalDate.now()
            today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks(16)
        }

        // 从ICS数据推算开学日期：找到最早课程的 startDate，取其所在周的周一
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
                    // 取该日期所在周的周一
                    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
                    if (icsClassStartDate == null || monday.isBefore(icsClassStartDate)) {
                        icsClassStartDate = monday
                    }
                }
            }
        }
        val classStartDate = icsClassStartDate ?: defaultClassStartDate

        coursesData?.forEach { courseMap ->
            val name = courseMap["name"] as? String ?: return@forEach
            val classroom = courseMap["classroom"] as? String ?: ""
            val teacher = courseMap["teacher"] as? String ?: ""
            val dayOfWeek = (courseMap["dayOfWeek"] as? Number)?.toInt() ?: return@forEach
            val startSection = (courseMap["startSection"] as? Number)?.toInt() ?: return@forEach
            val endSection = (courseMap["endSection"] as? Number)?.toInt() ?: return@forEach
            @Suppress("UNCHECKED_CAST")
            var selectedWeeks = (courseMap["selectedWeeks"] as? List<Number>)?.map { it.toInt() } ?: emptyList()

            // ICS格式：根据datePairs计算所有事件的周次
            if (selectedWeeks.isEmpty()) {
                @Suppress("UNCHECKED_CAST")
                val datePairs = courseMap["datePairs"] as? List<Pair<String, String>>
                if (!datePairs.isNullOrEmpty()) {
                    val allWeeks = mutableSetOf<Int>()
                    for ((sd, ud) in datePairs) {
                        if (sd.length == 8) {
                            val courseStartDate = try {
                                LocalDate.of(
                                    sd.substring(0, 4).toInt(),
                                    sd.substring(4, 6).toInt(),
                                    sd.substring(6, 8).toInt()
                                )
                            } catch (_: Exception) { null }

                            if (courseStartDate != null) {
                                val startWeek = java.time.temporal.ChronoUnit.WEEKS.between(classStartDate, courseStartDate).toInt() + 1

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
                                        java.time.temporal.ChronoUnit.WEEKS.between(classStartDate, lastCourseDate).toInt() + 1
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
                        startWeek = selectedWeeks.min(),
                        endWeek = selectedWeeks.max(),
                        weekType = Course.WEEK_TYPE_ALL,
                        selectedWeeks = selectedWeeks,
                        colorRes = colorRes
                    )
                )
            }
        }

        // 保存课程到新课表
        scheduleViewModel.saveCoursesToSchedule(scheduleName, courses)

        // 保存设置到新课表
        @Suppress("UNCHECKED_CAST")
        val settings = data["settings"] as? Map<String, Any>
        if (settings != null) {
            // 切换到新课表来保存设置
            scheduleViewModel.switchToSchedule(scheduleName)

            (settings["class_start_time"] as? String)?.let { viewModel.setClassStartTime(it) }
            (settings["total_weeks"] as? Number)?.toInt()?.let { viewModel.setTotalWeeks(it) }
            @Suppress("UNCHECKED_CAST")
            (settings["show_weekend_days"] as? List<Number>)?.map { it.toInt() }?.toSet()?.let {
                settingsViewModel.setShowWeekendDays(it)
            }
            (settings["show_non_current_week"] as? Boolean)?.let {
                settingsViewModel.setShowNonCurrentWeek(it)
            }
            (settings["morning_sections"] as? Number)?.toInt()?.let { settingsViewModel.setMorningSections(it) }
            (settings["afternoon_sections"] as? Number)?.toInt()?.let { settingsViewModel.setAfternoonSections(it) }
            (settings["evening_sections"] as? Number)?.toInt()?.let { settingsViewModel.setEveningSections(it) }

            // 保存课程时间
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

        return Pair(true, "成功导入课表「$scheduleName」\n共${courses.size}门课程")
    } catch (e: Exception) {
        return Pair(false, "导入失败: ${e.message}")
    }
}