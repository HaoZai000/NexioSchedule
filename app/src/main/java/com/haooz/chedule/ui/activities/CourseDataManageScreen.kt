/** 备份与迁移页面 - Screen */
package com.haooz.chedule.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.WebDavManager
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun CourseDataManageScreen(
    onBack: () -> Unit,
    courseViewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    liquidGlassBackdrop: LayerBackdrop? = null
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val isLiquidGlass = liquidGlassBackdrop != null
    val scrollBehavior = MiuixScrollBehavior()

    val webDavManager = remember { WebDavManager(context) }
    val lastSyncTimeMs = webDavManager.lastSyncTime
    val lastSyncSummary = remember(lastSyncTimeMs) {
        if (lastSyncTimeMs > 0L) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            "上次操作: ${sdf.format(java.util.Date(lastSyncTimeMs))}"
        } else "未操作"
    }

    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var pendingImportScheduleName by remember { mutableStateOf("") }

    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()
    val currentScheduleName by scheduleViewModel.currentScheduleName.collectAsState()
    var selectedExportSchedule by remember { mutableStateOf(currentScheduleName) }

    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var pendingExportIcs by remember { mutableStateOf<String?>(null) }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                pendingExportJson?.let { json ->
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                pendingExportJson = null
            }
        }
    }

    val icsExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        uri?.let {
            try {
                pendingExportIcs?.let { ics ->
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        os.write(ics.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                pendingExportIcs = null
            }
        }
    }

    val jsonFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val text = inputStream?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                inputStream?.close()

                if (text.isNotBlank()) {
                    val (success, message, data) = parseFullScheduleJson(text)
                    if (success && data != null) {
                        val scheduleName = (data["schedule_name"] as? String) ?: "导入的课表"
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

    val icsFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val text = inputStream?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                inputStream?.close()

                if (text.isNotBlank()) {
                    val (success, message, data) = parseIcsFile(text)
                    if (success && data != null) {
                        val scheduleName = "ICS导入课表"
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

    Scaffold(
        topBar = {
            if (!isLiquidGlass) {
                TopAppBar(
                    title = "备份与迁移",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(
                            onClick = { onBack() },
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
        }
    ) { paddingValues ->
        val listState = rememberLazyListState()
        var listScrollY by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemScrollOffset }
                .collect { offset -> listScrollY = offset }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .then(
                    if (!isLiquidGlass) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
                ),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = if (isLiquidGlass) paddingValues.calculateTopPadding() + 56.dp else paddingValues.calculateTopPadding(),
                bottom = 60.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SmallTitle(
                    text = "导入",
                    modifier = Modifier.offset(x = (-15).dp)
                )
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "JSON 文件导入",
                            summary = "支持拾光课程表/Neixo课程表",
                            onClick = {
                                jsonFilePickerLauncher.launch(
                                    arrayOf("application/json", "*/*")
                                )
                            }
                        )
                        ArrowPreference(
                            title = "ICS 文件导入",
                            summary = "从日程文件导入课程",
                            onClick = {
                                icsFilePickerLauncher.launch(
                                    arrayOf("text/calendar", "*/*")
                                )
                            }
                        )
                    }
                }
            }

            item {
                SmallTitle(
                    text = "导出",
                    modifier = Modifier.offset(x = (-15).dp)
                )
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (scheduleNames.isNotEmpty()) {
                            OverlayDropdownPreference(
                                title = "导出课表",
                                entries = listOf(
                                    DropdownEntry(
                                        items = scheduleNames.map { name ->
                                            DropdownItem(
                                                text = name,
                                                selected = selectedExportSchedule == name,
                                                onClick = { selectedExportSchedule = name }
                                            )
                                        }
                                    )
                                ),
                                collapseOnSelection = true
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "JSON 格式导出",
                            summary = "导出课表为JSON格式",
                            onClick = {
                                val json = buildExportJson(courseViewModel, scheduleViewModel, settingsViewModel, selectedExportSchedule)
                                if (json != null) {
                                    pendingExportJson = json
                                    jsonExportLauncher.launch("${selectedExportSchedule}.json")
                                }
                            }
                        )
                        ArrowPreference(
                            title = "ICS 格式导出",
                            summary = "导出课表为日程格式",
                            onClick = {
                                val ics = buildExportIcs(courseViewModel, scheduleViewModel, settingsViewModel, selectedExportSchedule)
                                if (ics != null) {
                                    pendingExportIcs = ics
                                    icsExportLauncher.launch("${selectedExportSchedule}.ics")
                                }
                            }
                        )
                    }
                }
            }

            item {
                SmallTitle(
                    text = "备份",
                    modifier = Modifier.offset(x = (-15).dp)
                )
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "本地备份",
                            summary = "备份课表数据到设备存储",
                            onClick = {
                                val intent = Intent(context, com.haooz.chedule.ui.activities.LocalBackupActivity::class.java)
                                context.startActivity(intent)
                            }
                        )
                        ArrowPreference(
                            title = "WebDAV 云备份",
                            summary = if (webDavManager.isConfigured()) lastSyncSummary else "配置服务器后可云备份/恢复",
                            onClick = {
                                val intent = Intent(context, com.haooz.chedule.ui.activities.WebDavSettingsActivity::class.java)
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showImportConfirmDialog && pendingImportData != null) {
        OverlayDialog(
            title = "导入课表",
            summary = "是否导入课表「$pendingImportScheduleName」？\n确定导入将创建一个新的课表",
            show = true,
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
                                    courseViewModel,
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

private fun buildExportJson(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    scheduleName: String
): String? {
    val repository = CourseRepository(viewModel.getApplication())
    val courses = repository.getCoursesForSchedule(scheduleName)
    if (courses.isEmpty()) {
        Toast.makeText(viewModel.getApplication(), "「$scheduleName」课表为空，无法导出", Toast.LENGTH_SHORT).show()
        return null
    }

    val data = mapOf(
        "schedule_name" to scheduleName,
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

    return com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data)
}

private fun buildExportIcs(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    scheduleName: String
): String? {
    val repository = CourseRepository(viewModel.getApplication())
    val courses = repository.getCoursesForSchedule(scheduleName)
    if (courses.isEmpty()) {
        Toast.makeText(viewModel.getApplication(), "「$scheduleName」课表为空，无法导出", Toast.LENGTH_SHORT).show()
        return null
    }

    val classStartTime = viewModel.classStartTime.value
    val dtStartSdf = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.getDefault())

    return buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//Nexio Schedule//Course Schedule//CN")
        appendLine("CALSCALE:GREGORIAN")
        appendLine("METHOD:PUBLISH")

        for (course in courses) {
            val weeks = course.selectedWeeks.ifEmpty {
                (course.startWeek..course.endWeek).toList()
            }

            val sectionTimes = settingsViewModel.sectionTimes.value
            val startSectionTime = sectionTimes[course.startSection] ?: continue
            val endSectionTime = sectionTimes[course.endSection] ?: continue

            val startHour = startSectionTime.substringBefore("-").substringBefore(":").toIntOrNull() ?: 8
            val startMinute = startSectionTime.substringBefore("-").substringAfter(":").toIntOrNull() ?: 0
            val endHour = endSectionTime.substringAfter("-").substringBefore(":").toIntOrNull() ?: 9
            val endMinute = endSectionTime.substringAfter("-").substringAfter(":").toIntOrNull() ?: 0

            for (week in weeks) {
                val calendar = java.util.Calendar.getInstance()
                val dateStr = classStartTime.replace("/", "-")
                val parts = dateStr.split("-")
                if (parts.size == 3) {
                    calendar.set(parts[0].toIntOrNull() ?: 2025, (parts[1].toIntOrNull() ?: 9) - 1, parts[2].toIntOrNull() ?: 1, 0, 0, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                }
                calendar.add(java.util.Calendar.WEEK_OF_YEAR, week - 1)
                calendar.set(java.util.Calendar.DAY_OF_WEEK, course.dayOfWeek + 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, startHour)
                calendar.set(java.util.Calendar.MINUTE, startMinute)
                calendar.set(java.util.Calendar.SECOND, 0)
                val eventStart = calendar.time

                calendar.set(java.util.Calendar.HOUR_OF_DAY, endHour)
                calendar.set(java.util.Calendar.MINUTE, endMinute)
                val eventEnd = calendar.time

                val uid = "${course.id}-${week}@nexio-schedule"

                appendLine("BEGIN:VEVENT")
                appendLine("UID:$uid")
                appendLine("DTSTART:${dtStartSdf.format(eventStart)}")
                appendLine("DTEND:${dtStartSdf.format(eventEnd)}")
                appendLine("RRULE:FREQ=WEEKLY;COUNT=1")
                appendLine("SUMMARY:${course.name}")
                if (course.classroom.isNotBlank() || course.teacher.isNotBlank()) {
                    val location = listOfNotNull(
                        course.classroom.takeIf { it.isNotBlank() },
                        course.teacher.takeIf { it.isNotBlank() }
                    ).joinToString(" ")
                    appendLine("LOCATION:$location")
                }
                appendLine("DESCRIPTION:第${week}周")
                appendLine("END:VEVENT")
            }
        }

        appendLine("END:VCALENDAR")
    }
}
