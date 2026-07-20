/** 本地备份页面 - Screen */
package com.haooz.chedule.ui.activities

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
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
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class BackupFileInfo(
    val file: File,
    val fileName: String,
    val type: String,
    val scheduleName: String?,
    val scheduleCount: Int,
    val timestamp: String,
    val size: String
)

private fun getBackupDir(): File {
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return File(downloads, "Neixo_Schedule")
}

private fun generateBackupFileName(mode: String, scheduleName: String?): String {
    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    val timestamp = sdf.format(Date())
    return if (mode == "all") {
        "全部备份_$timestamp.json"
    } else {
        "${scheduleName ?: "课表"}_$timestamp.json"
    }
}

private fun countSchedulesInBackup(file: File): Int {
    return try {
        val json = file.readText(Charsets.UTF_8)
        val data: Map<String, Any> = com.google.gson.Gson().fromJson(json, object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type)
        if (data.containsKey("schedule_name") && data.containsKey("courses")) {
            1
        } else {
            val prefixes = mutableSetOf<String>()
            for (key in data.keys) {
                if (key.startsWith("schedule_") && key.contains("_courses")) {
                    val name = key.removePrefix("schedule_").substringBeforeLast("_courses")
                    prefixes.add(name)
                }
            }
            prefixes.size
        }
    } catch (_: Exception) {
        0
    }
}

private fun scanBackupFiles(): List<BackupFileInfo> {
    val dir = getBackupDir()
    if (!dir.exists()) return emptyList()

    val displaySdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val parseSdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    return dir.listFiles()?.filter { it.isFile && it.extension == "json" }?.map { file ->
        val name = file.nameWithoutExtension
        val isAllBackup = name.startsWith("全部备份")
        val type = if (isAllBackup) "全部备份" else "单独课表"

        val scheduleName = if (!isAllBackup) {
            if (name.length > 16) name.dropLast(16) else name
        } else null

        val scheduleCount = countSchedulesInBackup(file)

        val timestamp = try {
            val timestampStr = if (name.length > 16) name.takeLast(15) else name
            parseSdf.parse(timestampStr)?.let { displaySdf.format(it) } ?: "未知时间"
        } catch (_: Exception) {
            "未知时间"
        }

        val sizeStr = if (file.length() < 1024) "${file.length()} B"
        else if (file.length() < 1024 * 1024) "${file.length() / 1024} KB"
        else "${file.length() / (1024 * 1024)} MB"

        BackupFileInfo(file, file.name, type, scheduleName, scheduleCount, timestamp, sizeStr)
    }?.sortedByDescending { it.file.lastModified() } ?: emptyList()
}

@Composable
fun LocalBackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }

    var backupMode by remember { mutableStateOf("all") }
    var selectedSchedule by remember { mutableStateOf("") }

    val courseViewModel = remember { CourseViewModel(context.applicationContext as android.app.Application) }
    val scheduleViewModel = remember { ScheduleViewModel(context.applicationContext as android.app.Application) }
    val settingsViewModel = remember { SettingsViewModel(context.applicationContext as android.app.Application) }
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()

    var backupHistory by remember { mutableStateOf(scanBackupFiles()) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingDeleteFile by remember { mutableStateOf<File?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }
    var pendingExternalUri by remember { mutableStateOf<Uri?>(null) }
    var showExternalRestoreDialog by remember { mutableStateOf(false) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            pendingExternalUri = it
            showExternalRestoreDialog = true
        }
    }

    LaunchedEffect(Unit) {
        backupHistory = scanBackupFiles()
    }

    val appStyleValue = rememberAppStyle()
    val isLiquidGlass = appStyleValue == "liquidglass"

    Scaffold(
        topBar = {
            if (!isLiquidGlass) {
                TopAppBar(
                    title = "本地备份",
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
                top = if (isLiquidGlass) paddingValues.calculateTopPadding() + 64.dp else paddingValues.calculateTopPadding() + 8.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val modeEntry = DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = "全部备份",
                                    selected = backupMode == "all",
                                    onClick = { backupMode = "all" }
                                ),
                                DropdownItem(
                                    text = "单独课表",
                                    selected = backupMode == "single",
                                    onClick = { backupMode = "single" }
                                ),
                            )
                        )
                        OverlayDropdownPreference(
                            title = "备份模式",
                            entry = modeEntry,
                            collapseOnSelection = true
                        )

                        if (scheduleNames.isNotEmpty()) {
                            val scheduleEntry = DropdownEntry(
                                items = scheduleNames.map { name ->
                                    DropdownItem(
                                        text = name,
                                        selected = selectedSchedule == name,
                                        onClick = { selectedSchedule = name }
                                    )
                                }
                            )
                            OverlayDropdownPreference(
                                title = "选择课表",
                                summary = if (backupMode == "single") {
                                    if (selectedSchedule.isNotEmpty()) null else "请选择要备份的课表"
                                } else "全部备份模式下无需选择",
                                entry = scheduleEntry,
                                collapseOnSelection = true,
                                enabled = backupMode == "single"
                            )
                        } else {
                            Text(
                                text = "暂无课表数据",
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
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
                            title = if (isBackingUp) "备份中..." else "立即备份",
                            summary = if (backupMode == "all") "备份全部课表数据至存储" else "备份「${selectedSchedule.ifEmpty { "未选择" }}」至存储",
                            onClick = {
                                if (isBackingUp) return@ArrowPreference
                                if (backupMode == "single" && selectedSchedule.isEmpty()) {
                                    Toast.makeText(context, "请先选择要备份的课表", Toast.LENGTH_SHORT).show()
                                    return@ArrowPreference
                                }
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                isBackingUp = true
                                coroutineScope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        try {
                                            val repository = CourseRepository(context.applicationContext as android.app.Application)
                                            val dir = getBackupDir()
                                            if (!dir.exists()) dir.mkdirs()

                                            val fileName = generateBackupFileName(backupMode, selectedSchedule)
                                            val file = File(dir, fileName)

                                            val data = if (backupMode == "all") {
                                                repository.exportAllPreferences()
                                            } else {
                                                val courses = repository.getCoursesForSchedule(selectedSchedule)
                                                mapOf(
                                                    "schedule_name" to selectedSchedule,
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
                                            }

                                            val json = com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data)
                                            file.writeText(json, Charsets.UTF_8)
                                            Result.success(file.name)
                                        } catch (e: Exception) {
                                            Result.failure(e)
                                        }
                                    }
                                    isBackingUp = false
                                    result.fold(
                                        onSuccess = { name ->
                                            Toast.makeText(context, "备份成功: $name", Toast.LENGTH_SHORT).show()
                                            backupHistory = scanBackupFiles()
                                        },
                                        onFailure = { e ->
                                            Toast.makeText(context, "备份失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            if (backupHistory.isEmpty()) {
                item {
                    SmallTitle(
                        text = "备份历史",
                        modifier = Modifier.offset(x = (-15).dp)
                    )
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "暂无备份记录",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            } else {
                itemsIndexed(backupHistory, key = { _, item -> item.fileName }) { index, info ->
                    if (index == 0) {
                        SmallTitle(
                            text = "备份历史",
                            modifier = Modifier.offset(x = (-15).dp)
                        )
                    }
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (info.scheduleName != null) "·${info.scheduleName}·" else "共${info.scheduleCount}个课表",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = info.timestamp,
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                }
                                Row {
                                    Box(
                                        modifier = Modifier
                                            .clip(com.kyant.shapes.RoundedRectangle(20.dp))
                                            .background(MiuixTheme.colorScheme.primary)
                                            .clickable {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                pendingRestoreFile = info.file
                                                showRestoreDialog = true
                                            }
                                            .padding(horizontal = 20.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "恢复",
                                            style = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp),
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(com.kyant.shapes.RoundedRectangle(20.dp))
                                            .background(if (isAppDarkTheme()) Color(0xFF363636)
                                                else Color(0xFFF0F0F0))
                                            .clickable {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                pendingDeleteFile = info.file
                                                showDeleteDialog = true
                                            }
                                            .padding(horizontal = 20.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "删除",
                                            style = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp),
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFF44336)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SmallTitle(
                    text = "其他操作",
                    modifier = Modifier.offset(x = (-15).dp)
                )
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "手动选择文件",
                            summary = "从其他位置选择备份文件进行恢复",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                safLauncher.launch(arrayOf("application/json"))
                            }
                        )
                    }
                }
            }
        }
    }

    val restoreDialogFile = pendingRestoreFile
    OverlayDialog(
        title = "恢复备份",
        summary = if (restoreDialogFile != null) "确定要恢复备份「${restoreDialogFile.name}」吗？\n当前数据将被覆盖，请确保已备份当前数据" else "",
        show = showRestoreDialog,
        onDismissRequest = {
            showRestoreDialog = false
            pendingRestoreFile = null
        }
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
                        showRestoreDialog = false
                        pendingRestoreFile = null
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定恢复",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showRestoreDialog = false
                        coroutineScope.launch {
                            val file = pendingRestoreFile ?: return@launch
                            pendingRestoreFile = null
                            val result = withContext(Dispatchers.IO) {
                                try {
                                    val json = file.readText(Charsets.UTF_8)
                                    val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                                    val data: Map<String, Any> = com.google.gson.Gson().fromJson(json, type)

                                    val repository = CourseRepository(context.applicationContext as android.app.Application)

                                    if (data.containsKey("courses") && data.containsKey("schedule_name")) {
                                        val scheduleName = data["schedule_name"] as String
                                        @Suppress("UNCHECKED_CAST")
                                        val courses = data["courses"] as List<Map<String, Any>>
                                        repository.importSingleSchedule(scheduleName, courses)
                                    } else {
                                        repository.importAllPreferences(data)
                                    }
                                    Result.success(Unit)
                                } catch (e: Exception) {
                                    Result.failure(e)
                                }
                            }
                            result.fold(
                                onSuccess = {
                                    Toast.makeText(context, "恢复成功", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { e ->
                                    Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    val deleteDialogFile = pendingDeleteFile
    OverlayDialog(
        title = "删除备份",
        summary = if (deleteDialogFile != null) "确定要删除备份「${deleteDialogFile.name}」吗？\n此操作不可恢复" else "",
        show = showDeleteDialog,
        onDismissRequest = {
            showDeleteDialog = false
            pendingDeleteFile = null
        }
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
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showDeleteDialog = false
                        pendingDeleteFile = null
                    },
                ) {
                    Text("取消",fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        try {
                            pendingDeleteFile?.delete()
                            Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
                            backupHistory = scanBackupFiles()
                        } catch (e: Exception) {
                            Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        showDeleteDialog = false
                        pendingDeleteFile = null
                    },
                ) {
                    Text("删除",fontSize = 17.sp, fontWeight = FontWeight.Medium,color = Color(0xFFF44336))
                }
            }
        }
    }

    val externalRestoreUri = pendingExternalUri
    OverlayDialog(
        title = "恢复外部备份",
        summary = if (externalRestoreUri != null) "确定要恢复从外部选择的备份文件吗？\n当前数据将被覆盖，请确保已备份当前数据" else "",
        show = showExternalRestoreDialog,
        onDismissRequest = {
            showExternalRestoreDialog = false
            pendingExternalUri = null
        }
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
                        showExternalRestoreDialog = false
                        pendingExternalUri = null
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定恢复",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showExternalRestoreDialog = false
                        coroutineScope.launch {
                            val uri = pendingExternalUri ?: return@launch
                            pendingExternalUri = null
                            val result = withContext(Dispatchers.IO) {
                                try {
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    val json = inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                                        ?: throw Exception("无法读取文件")
                                    inputStream.close()

                                    val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                                    val data: Map<String, Any> = com.google.gson.Gson().fromJson(json, type)

                                    val repository = CourseRepository(context.applicationContext as android.app.Application)

                                    if (data.containsKey("courses") && data.containsKey("schedule_name")) {
                                        val scheduleName = data["schedule_name"] as String
                                        @Suppress("UNCHECKED_CAST")
                                        val courses = data["courses"] as List<Map<String, Any>>
                                        repository.importSingleSchedule(scheduleName, courses)
                                    } else {
                                        repository.importAllPreferences(data)
                                    }
                                    Result.success(Unit)
                                } catch (e: Exception) {
                                    Result.failure(e)
                                }
                            }
                            result.fold(
                                onSuccess = {
                                    Toast.makeText(context, "恢复成功", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { e ->
                                    Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
