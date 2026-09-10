/** 本地备份页面 - Screen */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.haooz.chedule.data.CourseRepository
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private const val BACKUP_DIR_NAME = "Neixo_Schedule"

/** 解析单个备份文件前允许的最大体积，避免把超大/无关文件整个读进内存 */
private const val MAX_BACKUP_PARSE_BYTES = 8L * 1024 * 1024

/** 备份体：要么是单课表备份，要么是覆盖式全量备份 */
private sealed interface BackupPayload {
    data class Single(
        val scheduleName: String,
        val courses: List<Map<String, Any>>,
        val timeConfig: Map<String, Any>?
    ) : BackupPayload

    data class Full(val data: Map<String, Any>) : BackupPayload
}

private data class BackupFileInfo(
    val file: File,
    val fileName: String,
    val type: String,
    val scheduleName: String?,
    val scheduleCount: Int,
    val timestamp: String,
    val size: String,
    val isFullBackup: Boolean
)

/**
 * 备份目录。优先公共 Download（用户能在文件管理器里看到、方便拷走）；
 * 分区存储下该目录可能不可写（取决于系统策略与授权状态），此时降级到应用私有
 * Download 目录，避免整个备份功能失效。
 */
private fun getBackupDir(context: Context): File {
    val publicDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        BACKUP_DIR_NAME
    )
    if (isUsableDir(publicDir)) return publicDir
    val privateDir = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
        BACKUP_DIR_NAME
    )
    privateDir.mkdirs()
    return privateDir
}

private fun isUsableDir(dir: File): Boolean = try {
    (dir.exists() || dir.mkdirs()) && dir.canWrite()
} catch (_: Exception) {
    false
}

/**
 * 扫描用的候选目录（公共 + 私有）。两个都扫，这样在两种存储环境之间切换时
 * 历史备份不会凭空"消失"。
 */
private fun candidateBackupDirs(context: Context): List<File> {
    val publicDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        BACKUP_DIR_NAME
    )
    val privateDir = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
        BACKUP_DIR_NAME
    )
    return listOf(publicDir, privateDir).filter { it.isDirectory }.distinctBy { it.absolutePath }
}

/** 课表名可能含路径分隔符/非法字符，直接拼进文件名会写失败或写到意外位置 */
private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "课表" }

private fun generateBackupFileName(mode: String, scheduleName: String?): String {
    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    val timestamp = sdf.format(Date())
    return if (mode == "all") {
        "全部备份_$timestamp.json"
    } else {
        "${sanitizeFileName(scheduleName ?: "课表")}_$timestamp.json"
    }
}

/** 文件名形如「<课表名>_yyyyMMdd_HHmmss」，用正则解析，不再依赖固定的后缀长度 */
private val BACKUP_NAME_REGEX = Regex("^(.+)_(\\d{8}_\\d{6})$")

private fun countSchedulesInBackup(file: File): Int {
    return try {
        if (file.length() > MAX_BACKUP_PARSE_BYTES) return 0
        val json = file.readText(Charsets.UTF_8)
        val data: Map<String, Any> = Gson().fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
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

private fun scanBackupFiles(context: Context): List<BackupFileInfo> {
    val displaySdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val parseSdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    return candidateBackupDirs(context)
        .flatMap { dir -> dir.listFiles()?.asList() ?: emptyList() }
        .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
        .map { file ->
            val name = file.nameWithoutExtension
            val isFullBackup = name.startsWith("全部备份")
            val type = if (isFullBackup) "全部备份" else "单独课表"

            val match = BACKUP_NAME_REGEX.matchEntire(name)
            val scheduleName = if (!isFullBackup) match?.groupValues?.get(1) ?: name else null

            val scheduleCount = countSchedulesInBackup(file)

            val timestamp = try {
                match?.let { parseSdf.parse(it.groupValues[2]) }?.let { displaySdf.format(it) } ?: "未知时间"
            } catch (_: Exception) {
                "未知时间"
            }

            val sizeStr = if (file.length() < 1024) "${file.length()} B"
            else if (file.length() < 1024 * 1024) "${file.length() / 1024} KB"
            else "${file.length() / (1024 * 1024)} MB"

            BackupFileInfo(file, file.name, type, scheduleName, scheduleCount, timestamp, sizeStr, isFullBackup)
        }
        .sortedByDescending { it.file.lastModified() }
}

/**
 * 解析并校验备份内容。
 *
 * 这一步是强制门禁：全量恢复走 [CourseRepository.importAllPreferences]，它会**先清空
 * 现有全部课表数据再写入**。如果不校验就放行，用户随便选一个无关 JSON 就会把课表全清掉。
 * 因此这里必须能明确识别出"这是本应用导出的备份"，否则直接抛错。
 */
private fun parseBackupPayload(json: String): BackupPayload {
    // 这里不能写成 mapNotNull/显式可空声明再判空：直接声明为 Map<String, Any> 时
    // Kotlin 会在赋值处插入空检查并抛 NPE，后面的 ?: 永远不会执行。
    // 用显式泛型参数 + elvis 才能让"解析失败"走到我们自己的异常分支。
    val data = Gson().fromJson<Map<String, Any>>(
        json, object : TypeToken<Map<String, Any>>() {}.type
    ) ?: throw IllegalArgumentException("文件内容不是有效的 JSON 对象")

    val scheduleName = data["schedule_name"]
    val courses = data["courses"]
    if (scheduleName is String && courses is List<*>) {
        val courseList = courses.filterIsInstance<Map<*, *>>().map {
            @Suppress("UNCHECKED_CAST")
            it as Map<String, Any>
        }
        @Suppress("UNCHECKED_CAST")
        val timeConfig = data["time_config"] as? Map<String, Any>
        return BackupPayload.Single(scheduleName, courseList, timeConfig)
    }

    val looksLikeFullBackup =
        data.containsKey("schedule_names") || data.keys.any { it.startsWith("schedule_") }
    if (!looksLikeFullBackup) {
        throw IllegalArgumentException("不是本应用导出的备份文件")
    }
    return BackupPayload.Full(data)
}

private fun readExternalBackupJson(context: Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("无法读取所选文件")
    return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

private fun countFullBackupSchedules(data: Map<String, Any>): Int =
    data.keys.count { it.startsWith("schedule_") && it.endsWith("_courses") }

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun LocalBackupScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var listScrollY by remember { mutableIntStateOf(0) }
    // 液态玻璃效果的透明下拉颜色
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    )

    var backupMode by remember { mutableStateOf("all") }
    var selectedSchedule by remember { mutableStateOf("") }

    val courseViewModel = remember { CourseViewModel(context.applicationContext as Application) }
    val scheduleViewModel = remember { ScheduleViewModel(context.applicationContext as Application) }
    val settingsViewModel = remember { SettingsViewModel(context.applicationContext as Application) }
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()

    // 扫描要读每个 json 并 Gson 全量解析，必须在 IO 线程做；初值留空，由 LaunchedEffect 填充
    var backupHistory by remember { mutableStateOf<List<BackupFileInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restorePayload by remember { mutableStateOf<BackupPayload?>(null) }
    var restoreSourceLabel by remember { mutableStateOf("") }
    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingDeleteFile by remember { mutableStateOf<File?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var deletingFileName by remember { mutableStateOf<String?>(null) }

    fun refreshHistory() {
        coroutineScope.launch {
            isScanning = true
            val scanned = withContext(Dispatchers.IO) { scanBackupFiles(context) }
            backupHistory = scanned
            isScanning = false
        }
    }

    /**
     * 先解析校验，再弹确认框。
     * 这样非法文件在弹框前就被拦下，且弹框文案能按备份类型给出准确说明。
     */
    fun beginRestore(file: File? = null, uri: Uri? = null) {
        if (isRestoring || showRestoreDialog) return
        coroutineScope.launch {
            isRestoring = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = when {
                        file != null -> file.readText(Charsets.UTF_8)
                        uri != null -> readExternalBackupJson(context, uri)
                        else -> throw IllegalArgumentException("没有可恢复的文件")
                    }
                    parseBackupPayload(json)
                }
            }
            isRestoring = false
            result.fold(
                onSuccess = { payload ->
                    restorePayload = payload
                    pendingRestoreFile = file
                    pendingRestoreUri = uri
                    restoreSourceLabel = file?.name ?: "外部文件"
                    showRestoreDialog = true
                },
                onFailure = { e ->
                    Toast.makeText(
                        context,
                        "无法识别备份文件：${e.message ?: "格式错误"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    fun applyRestore() {
        val payload = restorePayload ?: return
        showRestoreDialog = false
        restorePayload = null
        pendingRestoreFile = null
        pendingRestoreUri = null
        isRestoring = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val repository = CourseRepository(context.applicationContext as Application)
                    when (payload) {
                        is BackupPayload.Single -> {
                            // 重名时自动编号，避免覆盖已有课表
                            val existing = repository.getScheduleNames()
                            var name = payload.scheduleName
                            if (name in existing) {
                                var index = 1
                                while ("$name($index)" in existing) index++
                                name = "$name($index)"
                            }
                            repository.importSingleSchedule(name, payload.courses, payload.timeConfig)
                        }

                        is BackupPayload.Full -> repository.importAllPreferences(payload.data)
                    }
                    // 等加载完成再提示成功，否则会先弹 Toast 再刷出数据
                    courseViewModel.reloadCourses().join()
                    scheduleViewModel.refreshScheduleList()
                    settingsViewModel.refreshSettings()
                }
            }
            isRestoring = false
            result.fold(
                onSuccess = {
                    Toast.makeText(context, "恢复成功", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { beginRestore(uri = it) }
    }

    LaunchedEffect(Unit) {
        refreshHistory()
    }

    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 16.dp

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        val listState = rememberLazyListState()
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
                top = paddingValues.calculateTopPadding() + topBarHeightDp + 12.dp,
                bottom = 120.dp
            ),
        ) {
            item {
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
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
                        OverlayDropdownMenu(
                            title = "备份模式",
                            entry = modeEntry,
                            collapseOnSelection = true,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            dropdownColors = liquidGlassDropdownColors,
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
                            OverlayDropdownMenu(
                                title = "选择课表",
                                summary = if (backupMode == "single") {
                                    if (selectedSchedule.isNotEmpty()) null else "请选择要备份的课表"
                                } else "全部备份模式下无需选择",
                                entry = scheduleEntry,
                                collapseOnSelection = true,
                                enabled = backupMode == "single",
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
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
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
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
                                            val repository = CourseRepository(context.applicationContext as Application)
                                            val dir = getBackupDir(context)
                                            if (!isUsableDir(dir)) {
                                                throw IllegalStateException("备份目录不可写：${dir.absolutePath}")
                                            }

                                            val fileName = generateBackupFileName(backupMode, selectedSchedule)
                                            val file = File(dir, fileName)

                                            val data = if (backupMode == "all") {
                                                repository.exportAllPreferences()
                                            } else {
                                                val courses = repository.getCoursesForSchedule(selectedSchedule)
                                                // 获取该课表绑定的时间配置
                                                val configId = repository.getScheduleTimeConfigId(selectedSchedule)
                                                val timeConfig = repository.getTimeConfig(configId)
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
                                                            "isCustomTime" to course.isCustomTime,
                                                            "customStartTime" to course.customStartTime,
                                                            "customEndTime" to course.customEndTime,
                                                            "colorRes" to course.colorRes,
                                                            // selectedWeeks 为空时由 startWeek/endWeek/weekType 描述周次。
                                                            // 不能把区间展开写进 selectedWeeks：那样会丢掉单双周（weekType）。
                                                            "selectedWeeks" to course.selectedWeeks.sorted(),
                                                            "startWeek" to course.startWeek,
                                                            "endWeek" to course.endWeek,
                                                            "weekType" to course.weekType
                                                        )
                                                    },
                                                    "time_config" to mapOf(
                                                        "morningSections" to timeConfig.morningSections,
                                                        "afternoonSections" to timeConfig.afternoonSections,
                                                        "eveningSections" to timeConfig.eveningSections,
                                                        "sectionTimes" to timeConfig.sectionTimes,
                                                        "sectionNames" to timeConfig.sectionNames
                                                    )
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
                                            refreshHistory()
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

            // 备份历史
            item(key = "history_title") {
                SmallTitle(
                    text = "备份历史",
                    modifier = Modifier.offset(x = (-16).dp).animateItem()
                )
            }
            if (backupHistory.isEmpty()) {
                item(key = "history_empty") {
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = if (isScanning) "正在扫描备份文件..." else "暂无备份记录",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }
            itemsIndexed(backupHistory, key = { _, item -> item.fileName }) { _, info ->
                val isDeleting = deletingFileName == info.fileName
                val cardScale = remember { Animatable(0.8f) }
                val cardAlpha = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    launch { cardScale.animateTo(1f, animationSpec = tween(400)) }
                    launch { cardAlpha.animateTo(1f, animationSpec = tween(400)) }
                }
                LaunchedEffect(isDeleting) {
                    if (isDeleting) {
                        launch { cardScale.animateTo(0.8f, animationSpec = tween(300)) }
                        launch { cardAlpha.animateTo(0f, animationSpec = tween(300)) }
                    }
                }
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .animateItem()
                        .graphicsLayer {
                            scaleX = cardScale.value
                            scaleY = cardScale.value
                            alpha = cardAlpha.value
                        },
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
                                            .clip(com.kyant.capsule.ContinuousRoundedRectangle(20.dp))
                                            .background(MiuixTheme.colorScheme.primary)
                                            .clickable {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.VirtualKey
                                                )
                                                beginRestore(file = info.file)
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
                                            .clip(com.kyant.capsule.ContinuousRoundedRectangle(20.dp))
                                            .background(
                                                if (isAppDarkTheme()) Color(0xFF363636)
                                                else Color(0xFFF0F0F0)
                                            )
                                            .clickable {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.VirtualKey
                                                )
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

            item(key = "other_operations") {
                SmallTitle(
                    text = "其他操作",
                    modifier = Modifier.offset(x = (-16).dp).animateItem()
                )
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "手动选择文件",
                            summary = "从其他位置选择备份文件进行恢复",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                // 不限定 mime：各厂商文件管理器对 .json 上报的 mime 不一致
                                // （text/plain / application/octet-stream 都有），
                                // 限定 application/json 会让用户根本选不到文件。
                                // 合法性由 parseBackupPayload 把关，放开 mime 是安全的。
                                safLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }
            }
        }
    }

    val restorePayloadValue = restorePayload
    val restoreIsFull = restorePayloadValue is BackupPayload.Full
    val restoreSummary = when (restorePayloadValue) {
        is BackupPayload.Full -> {
            val count = countFullBackupSchedules(restorePayloadValue.data)
            "「$restoreSourceLabel」\n" +
                "这是全量备份，包含 $count 个课表。\n" +
                "恢复会先清除现有全部课表与时间配置，再整体覆盖为备份内容，此操作不可撤销。"
        }

        is BackupPayload.Single -> {
            "「$restoreSourceLabel」\n" +
                "这是单课表备份，包含 ${restorePayloadValue.courses.size} 门课程。\n" +
                "将新建课表「${restorePayloadValue.scheduleName}」（重名时自动编号），不影响现有课表。"
        }

        null -> ""
    }
    OverlayDialog(
        title = if (restoreIsFull) "恢复全部备份" else "恢复备份",
        summary = restoreSummary,
        show = showRestoreDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = {
            showRestoreDialog = false
            restorePayload = null
            pendingRestoreFile = null
            pendingRestoreUri = null
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
                        restorePayload = null
                        pendingRestoreFile = null
                        pendingRestoreUri = null
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = if (restoreIsFull) "覆盖恢复" else "确定恢复",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        applyRestore()
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
        liquidGlassBackdrop = liquidGlassBackdrop,
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
                TextButton(
                    text = "取消",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showDeleteDialog = false
                        pendingDeleteFile = null
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "删除",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        // 必须先把目标文件捕获成局部变量：删除动画有 300ms 延迟，
                        // 期间若用户点了另一个文件的删除按钮，pendingDeleteFile 会被覆盖，
                        // 原来的协程就会去删新选中的文件（旧版 bug：删错且原文件没删掉）
                        val target = pendingDeleteFile ?: return@TextButton
                        showDeleteDialog = false
                        pendingDeleteFile = null
                        deletingFileName = target.name
                        coroutineScope.launch {
                            try {
                                delay(300.milliseconds)
                                if (target.delete()) {
                                    Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "删除失败：文件不存在或不可写",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                refreshHistory()
                            } catch (e: Exception) {
                                Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                // 放在 finally：否则扫描抛异常时该卡片会永久停在 alpha=0
                                deletingFileName = null
                            }
                        }
                    },
                    textColor = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

}
