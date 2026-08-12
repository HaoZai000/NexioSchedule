/** 备份与迁移页面 - Screen */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.google.gson.GsonBuilder
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.WebDavManager
import com.haooz.chedule.ui.basic.NativeMiuixTextField
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.screens.applyScheduleData
import com.haooz.chedule.ui.screens.parseFullScheduleJson
import com.haooz.chedule.ui.screens.parseIcsFile
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import com.haooz.chedule.ui.basic.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun BackupAndMigrationScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    courseViewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 16.dp

    val webDavManager = remember { WebDavManager(context) }
    val lastSyncTimeMs = webDavManager.lastSyncTime
    val lastSyncSummary = remember(lastSyncTimeMs) {
        if (lastSyncTimeMs > 0L) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            "上次操作: ${sdf.format(Date(lastSyncTimeMs))}"
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
        topBar = {}
    ) { paddingValues ->
        val listState = rememberLazyListState()
        var listScrollY by remember { mutableIntStateOf(0) }
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemScrollOffset }
                .collect { offset -> listScrollY = offset }
        }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val topBarHeightDp = with(density) {
            (scrollBehavior?.currentHeightPx ?: 0f).toDp()
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .then(
                    scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                ),
            contentPadding = PaddingValues(
                start = tabletHorizontalPadding,
                end = tabletHorizontalPadding,
                top = paddingValues.calculateTopPadding() + topBarHeightDp,
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
                            OverlayDropdownMenu(
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
                                val json = buildExportJson(courseViewModel,
                                    settingsViewModel, selectedExportSchedule)
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
                                val ics = buildExportIcs(courseViewModel,
                                    settingsViewModel, selectedExportSchedule)
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
                                val intent = Intent(context, LocalBackupActivity::class.java)
                                context.startActivity(intent)
                            }
                        )
                        ArrowPreference(
                            title = "WebDAV 云备份",
                            summary = if (webDavManager.isConfigured()) lastSyncSummary else "配置服务器后可云备份/恢复",
                            onClick = {
                                val intent = Intent(context, WebDavSettingsActivity::class.java)
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
            liquidGlassBackdrop = liquidGlassBackdrop,
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
                NativeMiuixTextField(
                    value = pendingImportScheduleName,
                    onValueChange = { pendingImportScheduleName = it },
                    label = "课表名称",
                    modifier = Modifier.fillMaxWidth(),
                    requestFocus = showImportConfirmDialog
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
            "smart_weekend" to settingsViewModel.smartWeekend.value,
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

    return GsonBuilder().setPrettyPrinting().create().toJson(data)
}

private fun buildExportIcs(
    viewModel: CourseViewModel,
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
    val dtStartSdf = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault())

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
            if (weeks.isEmpty()) continue

            val sectionTimes = settingsViewModel.sectionTimes.value
            val startSectionTime = sectionTimes[course.startSection] ?: continue
            val endSectionTime = sectionTimes[course.endSection] ?: continue

            val startHour = startSectionTime.substringBefore("-").substringBefore(":").toIntOrNull() ?: 8
            val startMinute = startSectionTime.substringBefore("-").substringAfter(":").toIntOrNull() ?: 0
            val endHour = endSectionTime.substringAfter("-").substringBefore(":").toIntOrNull() ?: 9
            val endMinute = endSectionTime.substringAfter("-").substringAfter(":").toIntOrNull() ?: 0

            // 解析开学日期
            val dateStr = classStartTime.replace("/", "-")
            val parts = dateStr.split("-")
            if (parts.size < 3) continue
            val semYear = parts[0].toIntOrNull() ?: 2025
            val semMonth = (parts[1].toIntOrNull() ?: 9) - 1
            val semDay = parts[2].toIntOrNull() ?: 1

            // 找到开学日期所在周的周一（第1周的起始日）
            val semCal = Calendar.getInstance()
            semCal.set(semYear, semMonth, semDay, 0, 0, 0)
            semCal.set(Calendar.MILLISECOND, 0)
            val semDayOfWeek = semCal.get(Calendar.DAY_OF_WEEK) // Sunday=1, Monday=2, ...
            val daysToMonday = (semDayOfWeek - Calendar.MONDAY + 7) % 7
            val week1Monday = semCal.clone() as Calendar
            week1Monday.add(Calendar.DAY_OF_MONTH, -daysToMonday)

            // 为每个周次生成独立的 VEVENT（避免非连续周的 RRULE 问题）
            for (week in weeks) {
                // 目标日期 = 第1周周一 + (week-1)周 + 课程星期偏移
                val targetDate = week1Monday.clone() as Calendar
                targetDate.add(Calendar.WEEK_OF_YEAR, week - 1)
                // course.dayOfWeek: 1=周一, 7=周日 → Calendar: Monday=2, Sunday=1
                targetDate.set(Calendar.DAY_OF_WEEK, course.dayOfWeek + 1)

                targetDate.set(Calendar.HOUR_OF_DAY, startHour)
                targetDate.set(Calendar.MINUTE, startMinute)
                targetDate.set(Calendar.SECOND, 0)
                val eventStart = targetDate.time

                targetDate.set(Calendar.HOUR_OF_DAY, endHour)
                targetDate.set(Calendar.MINUTE, endMinute)
                val eventEnd = targetDate.time

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
