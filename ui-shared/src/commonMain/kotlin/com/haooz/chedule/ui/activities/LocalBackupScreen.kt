/** 本地备份页面 - Screen（文件 I/O 走 BackupFileController） */
package com.haooz.chedule.ui.activities

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.backup.BackupFileController
import com.haooz.chedule.data.backup.BackupFileInfo
import com.haooz.chedule.data.backup.BackupPayload
import com.haooz.chedule.data.backup.NoopBackupFileController
import com.haooz.chedule.data.backup.encodeBackupJson
import com.haooz.chedule.data.backup.parseBackupPayload
import com.haooz.chedule.data.backup.toJsonMap
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.util.formatCompactStamp
import com.haooz.chedule.data.util.nowLocalDateTime
import com.haooz.chedule.i18n.BackupStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.cardRevealLayer
import com.haooz.chedule.ui.components.rememberCardRevealState
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.courseToShareMap
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic


/** 课表名可能含路径分隔符/非法字符，直接拼进文件名会写失败或写到意外位置 */
private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "课表" }

/** 备份文件名：时间戳走 [formatCompactStamp]（不受 Locale 影响，⚠ 与迁移前逐字一致，勿改）。 */
private fun generateBackupFileName(mode: String, scheduleName: String?): String {
    val timestamp = nowLocalDateTime().formatCompactStamp()
    return if (mode == "all") {
        "全部备份_$timestamp.json"
    } else {
        "${sanitizeFileName(scheduleName ?: "课表")}_$timestamp.json"
    }
}

// 单课表备份的课程字段序列化 = 分享/导出那份 `courseToShareMap`（见下方 import）。
// ⚠ 不要在这里另写一份字段清单：之前有一份私有副本，加 `note`/`weekNotes` 时只改了分享那份，
//   结果「本地备份丢备注、分享不丢」。

private fun countFullBackupSchedules(data: Map<String, Any>): Int =
    data.keys.count { it.startsWith("schedule_") && it.endsWith("_courses") }

@Composable
fun LocalBackupScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    /** 备份文件能力控制器（Android 注入 `AndroidBackupFileController`；桌面/iOS 用 [NoopBackupFileController]）。 */
    backupFileController: BackupFileController = NoopBackupFileController,
    // 全量恢复成功后的平台副作用（重排提醒），由 Android 宿主注入。
    onHolidayDataChanged: () -> Unit = {},
    /** 列表最前的附加内容（「备份与同步」分类页在本页顶部挂「本地备份」小标题） */
    listHeader: (@Composable () -> Unit)? = null,
    /** 列表最后的附加内容（「备份与同步」分类页把 WebDAV 入口挂在本页最底端） */
    listFooter: (@Composable () -> Unit)? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var listScrollY by remember { mutableIntStateOf(0) }
    // 液态玻璃效果的透明下拉颜色
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )

    var backupMode by remember { mutableStateOf("all") }
    var selectedSchedule by remember { mutableStateOf("") }

    // 三个 VM 统一走 viewModel() 进 ViewModelStore
    val courseViewModel = courseViewModel()
    val scheduleViewModel = scheduleViewModel()
    val settingsViewModel = settingsViewModel()
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()

    // 扫描要逐个读 json 解析统计课表数，必须在 IO 线程做；初值留空，由 LaunchedEffect 填充
    var backupHistory by remember { mutableStateOf<List<BackupFileInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restorePayload by remember { mutableStateOf<BackupPayload?>(null) }
    var restoreSourceLabel by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<BackupFileInfo?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var deletingFileName by remember { mutableStateOf<String?>(null) }

    fun refreshHistory() {
        coroutineScope.launch {
            isScanning = true
            val scanned = withContext(ioDispatcher) { backupFileController.listBackups() }
            backupHistory = scanned
            isScanning = false
        }
    }

    /**
     * 先解析校验，再弹确认框 —— 非法文件在弹框前就被拦下，文案也能按备份类型给准确说明。
     * @param externalJson SAF 选中的外部文件内容（已由控制器读出）
     */
    fun beginRestore(backupId: String? = null, backupFileName: String? = null, externalJson: String? = null) {
        if (isRestoring || showRestoreDialog) return
        coroutineScope.launch {
            isRestoring = true
            val result = withContext(ioDispatcher) {
                runCatching {
                    val json = when {
                        backupId != null -> backupFileController.readBackupFile(backupId)
                            ?: throw IllegalArgumentException(BackupStr.noRestorableFile())
                        externalJson != null -> externalJson
                        else -> throw IllegalArgumentException(BackupStr.noRestorableFile())
                    }
                    parseBackupPayload(json)
                }
            }
            isRestoring = false
            result.fold(
                onSuccess = { payload ->
                    restorePayload = payload
                    restoreSourceLabel = backupFileName ?: BackupStr.externalFile()
                    showRestoreDialog = true
                },
                onFailure = { e ->
                    showToast(BackupStr.unrecognizedBackupN(e.message ?: BackupStr.formatError()))
                }
            )
        }
    }

    fun applyRestore() {
        val payload = restorePayload ?: return
        showRestoreDialog = false
        restorePayload = null
        isRestoring = true
        coroutineScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val repository = CourseRepository()
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
                            repository.importSingleSchedule(
                                scheduleName = name,
                                coursesData = payload.courses,
                                timeConfigData = payload.timeConfig,
                                classStartTime = payload.semesterSettings?.classStartTime,
                                currentWeek = payload.semesterSettings?.currentWeek,
                                totalWeeks = payload.semesterSettings?.totalWeeks,
                                teachingWeekReorganizations = payload.teachingWeekReorganizations,
                                smartWeekend = payload.semesterSettings?.smartWeekend,
                                showNonCurrentWeek = payload.semesterSettings?.showNonCurrentWeek,
                            )
                        }

                        is BackupPayload.Full -> {
                            repository.importAllPreferences(payload.data)
                            onHolidayDataChanged()
                        }
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
                    showToast(BackupStr.restoreSuccess())
                },
                onFailure = { e ->
                    showToast(BackupStr.restoreFailedN(e.message))
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        refreshHistory()
    }

    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    Scaffold(
        topBar = {}
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
                .collapsibleTopInset(scrollBehavior)
                .then(
                    scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                ),
            contentPadding = PaddingValues(
                start = tabletHorizontalPadding,
                end = tabletHorizontalPadding,
                top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight +
                    (if (isTablet) 0.dp else 12.dp),
                bottom = 120.dp
            ),
        ) {
            listHeader?.let { header -> item(key = "list_header") { header() } }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val modeEntry = DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = BackupStr.fullBackup(),
                                    selected = backupMode == "all",
                                    onClick = { backupMode = "all" }
                                ),
                                DropdownItem(
                                    text = BackupStr.singleSchedule(),
                                    selected = backupMode == "single",
                                    onClick = { backupMode = "single" }
                                ),
                            )
                        )
                        OverlayDropdownMenu(
                            title = BackupStr.backupModeLabel(),
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
                                title = BackupStr.selectSchedule(),
                                summary = if (backupMode == "single") {
                                    if (selectedSchedule.isNotEmpty()) null else BackupStr.selectScheduleToBackup()
                                } else BackupStr.fullBackupNoSelection(),
                                entry = scheduleEntry,
                                collapseOnSelection = true,
                                enabled = backupMode == "single",
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )
                        } else {
                            Text(
                                text = BackupStr.noScheduleData(),
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
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = if (isBackingUp) BackupStr.backingUp() else BackupStr.backupNow(),
                            summary = if (backupMode == "all") BackupStr.backupAllSummary() else BackupStr.backupSingleSummaryN(selectedSchedule.ifEmpty { BackupStr.notSelected() }),
                            onClick = {
                                if (isBackingUp) return@ArrowPreference
                                if (backupMode == "single" && selectedSchedule.isEmpty()) {
                                    showToast(BackupStr.selectScheduleToBackupFirst())
                                    return@ArrowPreference
                                }
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                isBackingUp = true
                                coroutineScope.launch {
                                    val result = withContext(ioDispatcher) {
                                        try {
                                            val repository = CourseRepository()

                                            val fileName = generateBackupFileName(backupMode, selectedSchedule)

                                            val data = if (backupMode == "all") {
                                                repository.exportAllPreferences()
                                            } else {
                                                val courses = repository.getCoursesForSchedule(selectedSchedule)
                                                // 获取该课表绑定的时间配置
                                                val configId = repository.getScheduleTimeConfigId(selectedSchedule)
                                                // effective()：顶层记「当天生效那套」的时间，routines 里仍带全部作息，
                                                // 旧版本 App 只读顶层也能拿到正确时间
                                                val timeConfig = repository.getTimeConfig(configId).effective()
                                                // 课程字段与导出/分享共用 courseToShareMap：颜色、周次模型保持一致
                                                mapOf(
                                                    "schedule_name" to selectedSchedule,
                                                    "semester_settings" to mapOf(
                                                        "class_start_time" to repository.getClassStartTime(selectedSchedule),
                                                        "current_week" to repository.getCurrentWeek(selectedSchedule),
                                                        "total_weeks" to repository.getTotalWeeks(selectedSchedule),
                                                        // 与导出 settings 对齐，避免单课表恢复后显示设置丢失
                                                        "smart_weekend" to repository.getSmartWeekend(selectedSchedule),
                                                        "show_non_current_week" to repository.getShowNonCurrentWeek(selectedSchedule),
                                                    ),
                                                    "teaching_week_reorganizations" to TeachingWeekReorganization.toBackupValue(
                                                        repository.getTeachingWeekReorganizations(selectedSchedule),
                                                    ),
                                                    "courses" to courses.map(::courseToShareMap),
                                                    // TimeConfig → 扁平 Map（:core 的 toJsonMap，
                                                    // 等价原 Gson 反射展开；写侧再剔 null）
                                                    "time_config" to timeConfig.toJsonMap()
                                                )
                                            }

                                            val json = encodeBackupJson(data)
                                            // 目录选择/可写校验/写盘全在控制器里（Android 包装保留原异常文案）
                                            if (!backupFileController.saveBackupFile(fileName, json)) {
                                                throw IllegalStateException(BackupStr.backupWriteFailed())
                                            }
                                            Result.success(fileName)
                                        } catch (e: Exception) {
                                            Result.failure(e)
                                        }
                                    }
                                    isBackingUp = false
                                    result.fold(
                                        onSuccess = { name ->
                                            showToast(BackupStr.backupSuccessN(name))
                                            refreshHistory()
                                        },
                                        onFailure = { e ->
                                            showToast(BackupStr.backupFailedN(e.message))
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
                    text = BackupStr.backupHistorySection(),
                    modifier = Modifier.animateItem()
                )
            }
            if (backupHistory.isEmpty()) {
                item(key = "history_empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .animateItem(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = if (isScanning) BackupStr.scanningBackups() else BackupStr.noBackupRecords(),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }
            itemsIndexed(backupHistory, key = { _, item -> item.fileName }) { _, info ->
                val isDeleting = deletingFileName == info.fileName
                val cardReveal = rememberCardRevealState(visible = !isDeleting)
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .animateItem()
                        .cardRevealLayer(cardReveal),
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
                                        text = if (info.scheduleName != null) "·${info.scheduleName}·" else BackupStr.scheduleCountN(info.scheduleCount),
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
                                            .clip(com.haooz.softglass.shape.ContinuousRoundedRectangle(20.dp))
                                            .background(MiuixTheme.colorScheme.primary)
                                            .clickable {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.VirtualKey
                                                )
                                                beginRestore(backupId = info.id, backupFileName = info.fileName)
                                            }
                                            .padding(horizontal = 20.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = BackupStr.restore(),
                                            style = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp),
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(com.haooz.softglass.shape.ContinuousRoundedRectangle(20.dp))
                                            .background(
                                                if (isAppDarkTheme()) Color(0xFF363636)
                                                else Color(0xFFF0F0F0)
                                            )
                                            .clickable {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.VirtualKey
                                                )
                                                pendingDelete = info
                                                showDeleteDialog = true
                                            }
                                            .padding(horizontal = 20.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = CommonStr.delete(),
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
                    text = BackupStr.otherOperations(),
                    modifier = Modifier.animateItem()
                )
                Card(
                    // 最后一张卡：补 12dp 下边距，避免贴住列表底（页尾 WebDAV 入口）
                    modifier = Modifier.fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .animateItem(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = BackupStr.manualFilePick(),
                            summary = BackupStr.manualFilePickSummary(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                // mime 必须放开（*/*）：各厂商文件管理器对 .json 上报的 mime 不一致，
                                // 限定 application/json 会选不到文件；合法性由 parseBackupPayload 把关。
                                backupFileController.pickAndReadBackup { json, _ ->
                                    if (json != null) beginRestore(externalJson = json)
                                }
                            }
                        )
                    }
                }
            }
            listFooter?.let { footer -> item(key = "list_footer") { footer() } }
        }
    }

    val restorePayloadValue = restorePayload
    val restoreIsFull = restorePayloadValue is BackupPayload.Full
    val restoreSummary = when (restorePayloadValue) {
        is BackupPayload.Full -> {
            val count = countFullBackupSchedules(restorePayloadValue.data)
            BackupStr.restoreFullSummaryN(restoreSourceLabel, count)
        }

        is BackupPayload.Single -> {
            BackupStr.restoreSingleSummaryN(
                restoreSourceLabel, restorePayloadValue.courses.size, restorePayloadValue.scheduleName,
            )
        }

        null -> ""
    }
    OverlayDialog(
        title = if (restoreIsFull) BackupStr.restoreFullBackup() else BackupStr.restoreBackup(),
        summary = restoreSummary,
        show = showRestoreDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = {
            showRestoreDialog = false
            restorePayload = null
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
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showRestoreDialog = false
                        restorePayload = null
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = if (restoreIsFull) BackupStr.overwriteRestore() else BackupStr.confirmRestore(),
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

    val deleteDialogInfo = pendingDelete
    OverlayDialog(
        title = BackupStr.deleteBackup(),
        summary = if (deleteDialogInfo != null) BackupStr.deleteBackupSummaryN(deleteDialogInfo.fileName) else "",
        show = showDeleteDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = {
            showDeleteDialog = false
            pendingDelete = null
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
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showDeleteDialog = false
                        pendingDelete = null
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = CommonStr.delete(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        // ⚠ 必须先捕获成局部变量：删除动画有 300ms 延迟，期间 pendingDelete 可能被
                        // 另一个文件的删除按钮覆盖，导致删错文件（旧版 bug）。
                        val target = pendingDelete ?: return@TextButton
                        showDeleteDialog = false
                        pendingDelete = null
                        deletingFileName = target.fileName
                        coroutineScope.launch {
                            try {
                                delay(300.milliseconds)
                                if (backupFileController.deleteBackupFile(target.id)) {
                                    showToast(CommonStr.deleteSuccess())
                                } else {
                                    showToast(BackupStr.deleteFailedNotWritable())
                                }
                                refreshHistory()
                            } catch (e: Exception) {
                                showToast(BackupStr.deleteFailedN(e.message))
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
