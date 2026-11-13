/** 备份与同步页：本机文件与 WebDAV 记录同页，写入目标由用户明确选择。 */
package com.haooz.chedule.ui.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.backup.BackupFileController
import com.haooz.chedule.data.backup.BackupFileInfo
import com.haooz.chedule.data.backup.BackupInfo
import com.haooz.chedule.data.backup.BackupPayload
import com.haooz.chedule.data.backup.BackupResult
import com.haooz.chedule.data.backup.NoopBackupFileController
import com.haooz.chedule.data.backup.RestoreResult
import com.haooz.chedule.data.backup.WebDavManager
import com.haooz.chedule.data.backup.encodeBackupJson
import com.haooz.chedule.data.backup.parseBackupPayload
import com.haooz.chedule.data.backup.toJsonMap
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.util.formatCompactStamp
import com.haooz.chedule.data.util.formatDateTimeMinute
import com.haooz.chedule.data.util.localDateTimeAt
import com.haooz.chedule.data.util.nowLocalDateTime
import com.haooz.chedule.data.util.parseCompactStamp
import com.haooz.chedule.i18n.BackupStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.utils.courseToShareMap
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownColors
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

/** 页面度量：与设置页同一套节奏，行内边距交给组件默认值。 */
private object Metrics {
    val listGap = 12.dp
    val pageHorizontal = 16.dp
    val pageHorizontalTablet = 20.dp
    val listBottom = 120.dp
}

/**
 * 底部悬浮「立即备份」按钮：页面只写状态，按钮由路由 overlay 绘制。
 *
 * 玻璃按钮要折射页面内容，必须画在采样层之外，所以不能放在页面里。
 */
class BackupBottomActionState {
    var busy by mutableStateOf<String?>(null)
    var enabled by mutableStateOf(true)
    var onBackup: (() -> Unit)? = null
}

/** 危险操作色，与设置页「退出翻转模式」同色。 */
private val DangerRed = Color(0xFFF44336)

/** 历史默认展开条数，超出走「查看全部」。 */
private const val HISTORY_PREVIEW = 5

// ── 偏好 ────────────────────────────────────────────────────

/** 备份页的开关与「最近一次备份」时间戳；不进全量备份，恢复后保持本机设置。 */
object BackupCenterPrefs {
    private const val STORE = "backup_center"
    private const val KEY_SNAPSHOT = "snapshot_before_restore"
    private const val KEY_LAST_BACKUP = "last_backup_at"

    private val prefs get() = AppStorage.store(STORE)

    var snapshotBeforeRestore: Boolean
        get() = prefs.getBoolean(KEY_SNAPSHOT, true)
        set(value) = prefs.edit { putBoolean(KEY_SNAPSHOT, value) }

    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_BACKUP, value) }

    /** 最近一次备份时间；组合期读它，备份完成后设置页入口摘要即时刷新。 */
    private val lastBackupTick = mutableStateOf(0L)

    fun noteBackupNow() {
        val now = Clock.System.now().toEpochMilliseconds()
        lastBackupAt = now
        lastBackupTick.value = now
    }

    /** 设置页入口右侧摘要；从未备份时留空，调用方据此不显示。 */
    fun lastBackupLabel(): String {
        val at = lastBackupTick.value.takeIf { it > 0L } ?: lastBackupAt
        if (at <= 0L) return ""
        val days = (Clock.System.now().toEpochMilliseconds() - at) / 86_400_000L
        return when {
            days <= 0L -> BackupStr.backedUpToday()
            days == 1L -> BackupStr.backedUpYesterday()
            else -> BackupStr.backedUpDaysN(days)
        }
    }
}

// ── 记录模型 ────────────────────────────────────────────────

/** 备份写到哪里。 */
private enum class BackupTarget { Local, Cloud, Both }

/** 备份范围；云端固定全量，单课表只对本机有意义。 */
private enum class BackupScope { All, Single }

private enum class BackupSource { Local, Cloud }

/** 一条可恢复的记录：本机文件或云端备份。 */
private data class BackupRecord(
    val source: BackupSource,
    val id: String,
    val fileName: String,
    val title: String,
    val timestamp: String,
    val detail: String,
    val stamp: String,
)

/** 待确认的恢复来源：本机文件已解析，云端已下载 data 段。 */
private sealed interface RestoreSource {
    data class Local(val payload: BackupPayload) : RestoreSource
    data class Cloud(val backupId: String, val data: Map<String, Any>) : RestoreSource
}

private class PendingRestore(val label: String, val source: RestoreSource) {
    val destructive: Boolean
        get() = when (source) {
            is RestoreSource.Local -> source.payload is BackupPayload.Full
            is RestoreSource.Cloud -> true
        }
}

private val STAMP_REGEX = Regex("""\d{8}_\d{6}""")

private fun stampOf(fileName: String): String = STAMP_REGEX.find(fileName)?.value ?: fileName

private fun daysSince(stamp: String): Long? {
    val date = parseCompactStamp(stamp)?.date ?: return null
    return date.daysUntil(Clock.System.todayIn(TimeZone.currentSystemDefault())).toLong()
}

private fun BackupFileInfo.toRecord() = BackupRecord(
    source = BackupSource.Local,
    id = id,
    fileName = fileName,
    title = if (isFullBackup) BackupStr.fullBackup() else scheduleName ?: fileName,
    timestamp = timestamp,
    detail = BackupStr.localRecordDetailN(scheduleCount),
    stamp = stampOf(fileName),
)

private fun BackupInfo.toRecord() = BackupRecord(
    source = BackupSource.Cloud,
    id = backupId,
    fileName = fileName,
    title = BackupStr.fullBackup(),
    timestamp = backupTime,
    detail = BackupStr.cloudRecordDetail(),
    stamp = stampOf(fileName),
)

// ── 文件名与数据组装 ─────────────────────────────────────────

/** 课表名可能含路径分隔符，直接拼进文件名会写失败或写到意外位置。 */
private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "课表" }

/** 备份文件名；时间戳格式与旧版逐字一致，改动会让历史备份列不出来。 */
private fun backupFileName(scope: BackupScope, scheduleName: String): String {
    val timestamp = nowLocalDateTime().formatCompactStamp()
    return if (scope == BackupScope.All) "全部备份_$timestamp.json"
    else "${sanitizeFileName(scheduleName.ifEmpty { "课表" })}_$timestamp.json"
}

/** 恢复前快照沿用「全部备份」前缀，目录扫描才会当成全量备份。 */
private fun snapshotFileName(): String =
    "全部备份_恢复前_${nowLocalDateTime().formatCompactStamp()}.json"

/** 全量备份里的课表名来自 `schedule_<名>_courses` 键。 */
private fun fullScheduleNames(data: Map<String, Any>): Set<String> =
    data.keys.mapNotNull { key ->
        if (key.startsWith("schedule_") && key.endsWith("_courses")) {
            key.removePrefix("schedule_").removeSuffix("_courses")
        } else null
    }.toSet()

/** 单课表备份的课程字段与导出/分享共用 [courseToShareMap]，避免备注等字段漏写。 */
private suspend fun buildBackupData(scope: BackupScope, scheduleName: String): Map<String, Any> =
    withContext(ioDispatcher) {
        val repository = CourseRepository()
        if (scope == BackupScope.All) {
            repository.exportAllPreferences()
        } else {
            val courses = repository.getCoursesForSchedule(scheduleName)
            val timeConfig = repository.getTimeConfig(repository.getScheduleTimeConfigId(scheduleName)).effective()
            mapOf(
                "schedule_name" to scheduleName,
                "semester_settings" to mapOf(
                    "class_start_time" to repository.getClassStartTime(scheduleName),
                    "current_week" to repository.getCurrentWeek(scheduleName),
                    "total_weeks" to repository.getTotalWeeks(scheduleName),
                    "smart_weekend" to repository.getSmartWeekend(scheduleName),
                    "show_non_current_week" to repository.getShowNonCurrentWeek(scheduleName),
                ),
                "teaching_week_reorganizations" to TeachingWeekReorganization.toBackupValue(
                    repository.getTeachingWeekReorganizations(scheduleName),
                ),
                "courses" to courses.map(::courseToShareMap),
                "time_config" to timeConfig.toJsonMap(),
                "holiday" to HolidayManager.exportBackupDataFor(scheduleName),
            )
        }
    }

private suspend fun writeBackup(
    controller: BackupFileController,
    fileName: String,
    data: Map<String, Any>,
): Result<String> = withContext(ioDispatcher) {
    runCatching {
        if (!controller.saveBackupFile(fileName, encodeBackupJson(data))) {
            throw IllegalStateException(BackupStr.backupWriteFailed())
        }
        fileName
    }
}

/** 覆盖恢复前把当前数据落成一份本机全量备份。 */
internal suspend fun createSnapshot(controller: BackupFileController): Result<String> =
    withContext(ioDispatcher) {
        runCatching {
            val fileName = snapshotFileName()
            if (!controller.saveBackupFile(fileName, encodeBackupJson(CourseRepository().exportAllPreferences()))) {
                throw IllegalStateException(BackupStr.backupWriteFailed())
            }
            fileName
        }
    }

private suspend fun applyPayload(
    payload: BackupPayload,
    courseViewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    onHolidayDataChanged: () -> Unit,
): Result<Unit> = withContext(ioDispatcher) {
    runCatching {
        val repository = CourseRepository()
        when (payload) {
            is BackupPayload.Single -> {
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
                    holiday = payload.holiday,
                )
                onHolidayDataChanged()
            }

            is BackupPayload.Full -> {
                repository.importAllPreferences(payload.data)
                onHolidayDataChanged()
            }
        }
        courseViewModel.reloadCourses().join()
        scheduleViewModel.refreshScheduleList()
        settingsViewModel.refreshSettings()
    }
}

// ── 状态 ────────────────────────────────────────────────────

/** 页面全部可变状态与动作；Composable 只负责渲染。 */
private class BackupCenterState(
    private val files: BackupFileController,
    private val webDav: WebDavManager,
    private val scope: CoroutineScope,
    private val courseViewModel: CourseViewModel,
    private val scheduleViewModel: ScheduleViewModel,
    private val settingsViewModel: SettingsViewModel,
    private val onHolidayDataChanged: () -> Unit,
    private val bottomAction: BackupBottomActionState,
) {
    var records by mutableStateOf<List<BackupRecord>>(emptyList())
        private set
    var localCount by mutableStateOf(0)
        private set
    var cloudCount by mutableStateOf(0)
        private set
    var scanning by mutableStateOf(true)
        private set
    var lastSyncAt by mutableStateOf(webDav.lastSyncTime)
        private set

    /** 本机备份目录展示文本，以及是否已获得 SAF 长期授权。 */
    var backupDirLabel by mutableStateOf("")
        private set
    var backupDirAuthorized by mutableStateOf(false)
        private set

    /** 进行中的操作文案；直接落到悬浮按钮上，页面其余部分只关心是否忙碌。 */
    var busy: String?
        get() = bottomAction.busy
        private set(value) {
            bottomAction.busy = value
        }

    var target by mutableStateOf(BackupTarget.Local)
    var backupScope by mutableStateOf(BackupScope.All)
    var scheduleName by mutableStateOf("")
    var expanded by mutableStateOf(false)
    var pendingRestore by mutableStateOf<PendingRestore?>(null)
    var pendingDelete by mutableStateOf<BackupRecord?>(null)

    val localAvailable: Boolean get() = files !is NoopBackupFileController
    val cloudReady: Boolean get() = webDav.isConfigured()
    val canChooseBackupDirectory: Boolean get() = files.canChooseBackupDirectory()
    val visibleRecords: List<BackupRecord> get() = if (expanded) records else records.take(HISTORY_PREVIEW)

    fun refresh() {
        scope.launch {
            scanning = true
            val local = withContext(ioDispatcher) {
                runCatching { files.listBackups() }.getOrDefault(emptyList())
            }.map { it.toRecord() }
            val dir = withContext(ioDispatcher) {
                files.backupDirectoryLabel() to files.isBackupDirectoryAuthorized()
            }
            val cloudResult = if (cloudReady) {
                webDav.listBackupsResult()
            } else {
                Result.success(emptyList<BackupInfo>())
            }
            val cloud = cloudResult.getOrDefault(emptyList()).map { it.toRecord() }
            backupDirLabel = dir.first
            backupDirAuthorized = dir.second
            records = (local + cloud).sortedByDescending { it.stamp }
            localCount = local.size
            cloudCount = cloud.size
            lastSyncAt = webDav.lastSyncTime
            scanning = false
        }
    }

    /** 让用户授权一个可长期读写的备份目录（Android SAF）；授权后本机备份不再受卸载影响。 */
    fun chooseBackupDirectory() {
        if (!files.canChooseBackupDirectory()) return
        files.chooseBackupDirectory { granted ->
            scope.launch {
                backupDirLabel = withContext(ioDispatcher) { files.backupDirectoryLabel() }
                backupDirAuthorized = files.isBackupDirectoryAuthorized()
                showToast(
                    if (granted) BackupStr.backupFolderAuthorized() else BackupStr.backupFolderDenied()
                )
                refresh()
            }
        }
    }

    fun backupNow() {
        if (busy != null) return
        val toLocal = target != BackupTarget.Cloud
        val toCloud = target != BackupTarget.Local
        when {
            toLocal && !localAvailable -> return showToast(BackupStr.localBackupUnsupported())
            toCloud && !cloudReady -> return showToast(BackupStr.configureWebDavFirst())
            toLocal && backupScope == BackupScope.Single && scheduleName.isEmpty() ->
                return showToast(BackupStr.selectScheduleToBackupFirst())
        }
        scope.launch {
            busy = BackupStr.backingUp()
            val localResult = if (toLocal) {
                runCatching {
                    writeBackup(files, backupFileName(backupScope, scheduleName), buildBackupData(backupScope, scheduleName))
                        .getOrThrow()
                }
            } else null
            val cloudResult = if (toCloud) webDav.backupAllData(CourseRepository()) else null
            busy = null
            val lines = buildList {
                localResult?.fold(
                    onSuccess = { add(BackupStr.backupLocalOk()) },
                    onFailure = { add(BackupStr.backupLocalFailedN(it.message ?: BackupStr.formatError())) },
                )
                when (cloudResult) {
                    is BackupResult.Success -> add(BackupStr.backupCloudOk())
                    is BackupResult.Error -> add(BackupStr.backupCloudFailedN(cloudResult.message))
                    null -> Unit
                }
            }
            if (localResult?.isSuccess == true || cloudResult is BackupResult.Success) {
                BackupCenterPrefs.noteBackupNow()
            }
            showToast(lines.joinToString("\n"), longDuration = true)
            refresh()
        }
    }

    /** 先读出内容再弹确认框，非法文件在确认前就被拦下。 */
    fun requestRestore(record: BackupRecord) {
        if (busy != null) return
        scope.launch {
            busy = BackupStr.loadingBackup()
            val source = withContext(ioDispatcher) {
                when (record.source) {
                    BackupSource.Local -> runCatching {
                        val json = files.readBackupFile(record.id)
                            ?: throw IllegalStateException(BackupStr.noRestorableFile())
                        RestoreSource.Local(parseBackupPayload(json))
                    }
                    BackupSource.Cloud -> webDav.downloadBackupData(record.id).fold(
                        onSuccess = { Result.success<RestoreSource>(RestoreSource.Cloud(record.id, it)) },
                        onFailure = { Result.failure(it) },
                    )
                }
            }
            busy = null
            source.fold(
                onSuccess = { pendingRestore = PendingRestore(record.fileName, it) },
                onFailure = { showToast(BackupStr.unrecognizedBackupN(it.message ?: BackupStr.formatError())) },
            )
        }
    }

    fun requestExternalFile() {
        files.pickAndReadBackup { json, name ->
            if (json == null) return@pickAndReadBackup
            scope.launch {
                val parsed = withContext(ioDispatcher) { runCatching { parseBackupPayload(json) } }
                parsed.fold(
                    onSuccess = {
                        pendingRestore = PendingRestore(name ?: BackupStr.externalFile(), RestoreSource.Local(it))
                    },
                    onFailure = {
                        showToast(BackupStr.unrecognizedBackupN(it.message ?: BackupStr.formatError()))
                    },
                )
            }
        }
    }

    fun confirmRestore() {
        val pending = pendingRestore ?: return
        pendingRestore = null
        scope.launch {
            busy = BackupStr.restoring()
            if (pending.destructive && BackupCenterPrefs.snapshotBeforeRestore && localAvailable) {
                val snapshot = createSnapshot(files)
                if (snapshot.isFailure) {
                    busy = null
                    showToast(BackupStr.snapshotFailedN(snapshot.exceptionOrNull()?.message ?: BackupStr.formatError()))
                    return@launch
                }
            }
            val result = when (val source = pending.source) {
                is RestoreSource.Local -> applyPayload(
                    payload = source.payload,
                    courseViewModel = courseViewModel,
                    scheduleViewModel = scheduleViewModel,
                    settingsViewModel = settingsViewModel,
                    onHolidayDataChanged = onHolidayDataChanged,
                )

                is RestoreSource.Cloud -> {
                    val restored = webDav.restoreBackup(source.backupId, CourseRepository())
                    if (restored is RestoreResult.Success) {
                        withContext(ioDispatcher) {
                            courseViewModel.reloadCourses().join()
                            scheduleViewModel.refreshScheduleList()
                            settingsViewModel.refreshSettings()
                        }
                        Result.success(Unit)
                    } else {
                        Result.failure(
                            IllegalStateException(
                                (restored as? RestoreResult.Error)?.message ?: BackupStr.formatError()
                            )
                        )
                    }
                }
            }
            busy = null
            result.fold(
                onSuccess = {
                    showToast(BackupStr.restoreSuccess())
                    refresh()
                },
                onFailure = { showToast(BackupStr.restoreFailedN(it.message)) },
            )
        }
    }

    fun confirmDelete() {
        val record = pendingDelete ?: return
        pendingDelete = null
        scope.launch {
            val ok = withContext(ioDispatcher) {
                when (record.source) {
                    BackupSource.Local ->
                        runCatching { files.deleteBackupFile(record.id) }.getOrDefault(false)
                    BackupSource.Cloud ->
                        runCatching { webDav.deleteBackup(record.id) }.getOrNull()?.isSuccess == true
                }
            }
            showToast(if (ok) CommonStr.deleteSuccess() else BackupStr.deleteFailedNotWritable())
            refresh()
        }
    }

    fun chooseTarget(value: BackupTarget) {
        if (value == target) return
        if (value != BackupTarget.Local && !cloudReady) {
            showToast(BackupStr.configureWebDavFirst())
            return
        }
        if (value != BackupTarget.Cloud && !localAvailable) {
            showToast(BackupStr.localBackupUnsupported())
            return
        }
        target = value
        if (value != BackupTarget.Local) backupScope = BackupScope.All
    }
}

// ── 页面 ────────────────────────────────────────────────────

@Composable
fun BackupCenterScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    backupFileController: BackupFileController = NoopBackupFileController,
    onHolidayDataChanged: () -> Unit = {},
    bottomAction: BackupBottomActionState = remember { BackupBottomActionState() },
) {
    val haptic = LocalHapticFeedback.current
    val appRouter = LocalAppRouter.current
    val scope = rememberCoroutineScope()
    val holidayCallback by rememberUpdatedState(onHolidayDataChanged)
    val webDav = remember { WebDavManager { holidayCallback() } }
    val courseViewModel = courseViewModel()
    val scheduleViewModel = scheduleViewModel()
    val settingsViewModel = settingsViewModel()
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()

    val state = remember(backupFileController, webDav) {
        BackupCenterState(
            files = backupFileController,
            webDav = webDav,
            scope = scope,
            courseViewModel = courseViewModel,
            scheduleViewModel = scheduleViewModel,
            settingsViewModel = settingsViewModel,
            onHolidayDataChanged = { holidayCallback() },
            bottomAction = bottomAction,
        ).also { created -> bottomAction.onBackup = { created.backupNow() } }
    }
    LaunchedEffect(state) { state.refresh() }
    LaunchedEffect(state.busy, state.backupScope, state.scheduleName) {
        bottomAction.enabled = state.busy == null &&
            (state.backupScope == BackupScope.All || state.scheduleName.isNotEmpty())
    }
    LaunchedEffect(scheduleNames) {
        if (state.scheduleName !in scheduleNames) state.scheduleName = scheduleNames.firstOrNull().orEmpty()
    }

    val dropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )
    val latest = state.records.firstOrNull()
    val days = latest?.let { daysSince(it.stamp) }
    val headline = when {
        latest == null -> BackupStr.headlineNoBackup()
        days == null -> latest.timestamp
        days <= 0L -> BackupStr.headlineToday()
        days == 1L -> BackupStr.headlineYesterday()
        else -> BackupStr.headlineDaysN(days)
    }
    val statusText = when {
        latest == null -> BackupStr.statusNoBackup()
        days != null && days > 7 -> BackupStr.statusStale()
        days != null && days > 1 -> BackupStr.statusSuggested()
        else -> BackupStr.statusProtected()
    }
    // 上次备份时间与本机/云端份数分两行，时间较长时不会把份数挤出卡片
    val statusDetail = if (latest == null) {
        BackupStr.statusCountsN(state.localCount, state.cloudCount)
    } else {
        "${BackupStr.statusLastBackupN(latest.timestamp)}\n" +
            BackupStr.statusCountsN(state.localCount, state.cloudCount)
    }
    val lastSyncText = if (state.lastSyncAt > 0L) {
        localDateTimeAt(state.lastSyncAt).formatDateTimeMinute()
    } else {
        BackupStr.neverSynced()
    }

    val isTablet = isTabletWidth()
    val horizontalPadding = if (isTablet) Metrics.pageHorizontalTablet else Metrics.pageHorizontal

    Scaffold(topBar = {}) { paddingValues ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .collapsibleTopInset(scrollBehavior)
                .then(scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = paddingValues.calculateTopPadding() +
                    CollapsibleTopAppBarDefaults.CollapsedHeight +
                    pageTopExtra(isTablet, hasSmallTitle = false),
                bottom = Metrics.listBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(Metrics.listGap),
        ) {
            item(key = "status") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    BasicComponent(
                        title = headline,
                        summary = statusDetail,
                        endActions = {
                            StatusPill(
                                text = statusText,
                                warn = latest == null || (days != null && days > 7),
                            )
                        },
                    )
                }
            }

            item(key = "backup") {
                SmallTitle(text = BackupStr.backupSection())
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OverlayDropdownMenu(
                            title = BackupStr.targetSection(),
                            summary = when (state.target) {
                                BackupTarget.Local -> BackupStr.targetLocalHint()
                                BackupTarget.Cloud -> BackupStr.targetCloudHint()
                                BackupTarget.Both -> BackupStr.targetBothHint()
                            },
                            entry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = BackupStr.targetLocal(),
                                        selected = state.target == BackupTarget.Local,
                                        onClick = { state.chooseTarget(BackupTarget.Local) },
                                    ),
                                    DropdownItem(
                                        text = BackupStr.targetCloud(),
                                        selected = state.target == BackupTarget.Cloud,
                                        onClick = {
                                            if (state.cloudReady) state.chooseTarget(BackupTarget.Cloud)
                                            else appRouter.navigate(AppRoute.WebDavSettings)
                                        },
                                    ),
                                    DropdownItem(
                                        text = BackupStr.targetBoth(),
                                        selected = state.target == BackupTarget.Both,
                                        onClick = {
                                            if (state.cloudReady) state.chooseTarget(BackupTarget.Both)
                                            else appRouter.navigate(AppRoute.WebDavSettings)
                                        },
                                    ),
                                ),
                            ),
                            collapseOnSelection = true,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            dropdownColors = dropdownColors,
                        )
                        OverlayDropdownMenu(
                            title = BackupStr.backupModeLabel(),
                            summary = if (state.target == BackupTarget.Local) null else BackupStr.cloudOnlyFullMode(),
                            entry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = BackupStr.fullBackup(),
                                        selected = state.backupScope == BackupScope.All,
                                        onClick = { state.backupScope = BackupScope.All },
                                    ),
                                    DropdownItem(
                                        text = BackupStr.singleSchedule(),
                                        selected = state.backupScope == BackupScope.Single,
                                        onClick = { state.backupScope = BackupScope.Single },
                                    ),
                                ),
                            ),
                            enabled = state.target == BackupTarget.Local,
                            collapseOnSelection = true,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            dropdownColors = dropdownColors,
                        )
                        OverlayDropdownMenu(
                            title = BackupStr.selectSchedule(),
                            summary = when {
                                state.target != BackupTarget.Local -> BackupStr.fullBackupNoSelection()
                                state.backupScope == BackupScope.All -> BackupStr.fullBackupNoSelection()
                                state.scheduleName.isEmpty() -> BackupStr.selectScheduleToBackup()
                                else -> null
                            },
                            entry = DropdownEntry(
                                items = scheduleNames.map { name ->
                                    DropdownItem(
                                        text = name,
                                        selected = state.scheduleName == name,
                                        onClick = { state.scheduleName = name },
                                    )
                                },
                            ),
                            enabled = state.target == BackupTarget.Local &&
                                state.backupScope == BackupScope.Single &&
                                scheduleNames.isNotEmpty(),
                            collapseOnSelection = true,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            dropdownColors = dropdownColors,
                        )
                    }
                }
            }

            item(key = "history") {
                SmallTitle(text = BackupStr.backupHistorySection())
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val visible = state.visibleRecords
                        if (visible.isEmpty()) {
                            BasicComponent(
                                title = if (state.scanning) {
                                    BackupStr.scanningBackups()
                                } else {
                                    BackupStr.noBackupRecords()
                                },
                                titleColor = BasicComponentDefaults.titleColor(
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                ),
                            )
                        } else {
                            visible.forEach { record ->
                                RecordRow(
                                    record = record,
                                    backdrop = liquidGlassBackdrop,
                                    dropdownColors = dropdownColors,
                                    onRestore = { state.requestRestore(record) },
                                    onDelete = { state.pendingDelete = record },
                                )
                            }
                        }
                        if (state.records.size > HISTORY_PREVIEW) {
                            ArrowPreference(
                                title = if (state.expanded) {
                                    BackupStr.showLess()
                                } else {
                                    BackupStr.showAllN(state.records.size)
                                },
                                onClick = { state.expanded = !state.expanded },
                            )
                        }
                    }
                }
            }

            item(key = "other_actions") {
                SmallTitle(text = BackupStr.otherOperations())
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = BackupStr.webDavBackup(),
                            summary = if (state.cloudReady) {
                                BackupStr.cloudConfiguredN(lastSyncText)
                            } else {
                                BackupStr.webDavBackupSummary()
                            },
                            onClick = { appRouter.navigate(AppRoute.WebDavSettings) },
                        )
                        if (state.canChooseBackupDirectory) {
                            ArrowPreference(
                                title = BackupStr.backupFolder(),
                                summary = if (state.backupDirAuthorized) {
                                    BackupStr.backupFolderAuthorizedN(state.backupDirLabel)
                                } else {
                                    BackupStr.backupFolderUnauthorizedN(state.backupDirLabel)
                                },
                                onClick = { state.chooseBackupDirectory() },
                            )
                        }
                        ArrowPreference(
                            title = BackupStr.restoreFromFile(),
                            summary = BackupStr.manualFilePickSummary(),
                            enabled = state.localAvailable,
                            onClick = { state.requestExternalFile() },
                        )
                    }
                }
            }
        }
    }

    val restore = state.pendingRestore
    BackupDialog(
        show = restore != null,
        title = if (restore?.destructive == true) BackupStr.restoreFullBackup() else BackupStr.restoreBackup(),
        summary = restore?.let {
            restoreSummary(
                label = it.label,
                data = when (val source = it.source) {
                    is RestoreSource.Local -> (source.payload as? BackupPayload.Full)?.data
                    is RestoreSource.Cloud -> source.data
                },
                single = (it.source as? RestoreSource.Local)?.payload as? BackupPayload.Single,
                currentNames = scheduleNames,
                snapshotEnabled = BackupCenterPrefs.snapshotBeforeRestore && state.localAvailable,
            )
        }.orEmpty(),
        confirmLabel = if (restore?.destructive == true) BackupStr.overwriteRestore() else BackupStr.confirmRestore(),
        onDismiss = { state.pendingRestore = null },
        onConfirm = { state.confirmRestore() },
    )

    val deleting = state.pendingDelete
    BackupDialog(
        show = deleting != null,
        title = BackupStr.deleteBackup(),
        summary = deleting?.let { BackupStr.deleteBackupSummaryN(it.fileName) }.orEmpty(),
        confirmLabel = CommonStr.delete(),
        confirmColor = DangerRed,
        onDismiss = { state.pendingDelete = null },
        onConfirm = { state.confirmDelete() },
    )
}

/** 记录行：点开是「恢复 / 删除」两个动作，与设置页的下拉一致。 */
@Composable
private fun RecordRow(
    record: BackupRecord,
    backdrop: com.haooz.softglass.backdrop.GlassBackdrop?,
    dropdownColors: DropdownColors,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    OverlayDropdownMenu(
        title = record.title,
        summary = "${record.timestamp} · ${record.detail}",
        entry = DropdownEntry(
            items = listOf(
                DropdownItem(text = BackupStr.restore(), onClick = onRestore),
                DropdownItem(text = CommonStr.delete(), onClick = onDelete),
            ),
        ),
        collapseOnSelection = true,
        liquidGlassBackdrop = backdrop,
        dropdownColors = dropdownColors,
    )
}

/** 状态标签；颜色只从主题派生，告警档用统一危险色。 */
@Composable
private fun StatusPill(text: String, warn: Boolean) {
    val color = if (warn) DangerRed else MiuixTheme.colorScheme.primary
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 弹窗统一壳：[onDismiss] 为 null 时只留一颗确认按钮。 */
@Composable
internal fun BackupDialog(
    show: Boolean,
    title: String,
    summary: String,
    confirmLabel: String,
    confirmColor: Color? = null,
    onDismiss: (() -> Unit)? = null,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        title = title,
        summary = summary,
        show = show,
        onDismissRequest = onDismiss,
    ) {
        if (onDismiss == null) {
            TextButton(
                text = confirmLabel,
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = CommonStr.cancel(),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    textColor = confirmColor,
                    colors = if (confirmColor == null) {
                        ButtonDefaults.textButtonColorsPrimary()
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 恢复确认摘要：全量给「新增 / 覆盖 / 移除」差异，单课表说清新建行为。 */
internal fun restoreSummary(
    label: String,
    data: Map<String, Any>?,
    single: BackupPayload.Single?,
    currentNames: List<String>,
    snapshotEnabled: Boolean,
): String {
    if (single != null) {
        return BackupStr.restoreSingleSummaryN(label, single.courses.size, single.scheduleName)
    }
    val names = fullScheduleNames(data.orEmpty())
    val current = currentNames.toSet()
    return buildString {
        append("「").append(label).append("」\n")
        append(
            BackupStr.fullRestoreDiffN(
                names.size,
                (names - current).size,
                (names intersect current).size,
                (current - names).size,
            )
        )
        append("\n")
        append(BackupStr.fullRestoreWarning())
        append("\n\n")
        append(if (snapshotEnabled) BackupStr.snapshotNote() else BackupStr.noSnapshotNote())
    }
}
