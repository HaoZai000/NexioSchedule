/**
 * 备份与迁移页面 - Screen。平台专有能力（文件选择/另存为、口令分享/抓取、手环推送、
 * 节假日变更后重排提醒）全部由宿主注入，见各参数注释。
 */
package com.haooz.chedule.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.backup.NoopScheduleFileController
import com.haooz.chedule.data.backup.ScheduleFileController
import com.haooz.chedule.data.backup.WebDavManager
import com.haooz.chedule.data.backup.encodeBackupJson
import com.haooz.chedule.data.backup.parseFullScheduleJson
import com.haooz.chedule.data.backup.parseIcsFile
import com.haooz.chedule.data.holiday.HolidayBeforeCourseExclusion
import com.haooz.chedule.data.holiday.HolidayCourseExclusion
import com.haooz.chedule.data.holiday.HolidayEndCourseExclusion
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.holiday.TeachingWeekReorganizationRule
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.repo.DayScheduleResolver
import com.haooz.chedule.data.util.formatDateTimeMinute
import com.haooz.chedule.data.util.formatIcsTimestamp
import com.haooz.chedule.data.util.localDateTimeAt
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.BackupStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.components.ScheduleImportConfirmDialog
import com.haooz.chedule.ui.components.text.miuix.Text as Text
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.screens.applyScheduleData
import com.haooz.chedule.ui.screens.applyThirdPartySharePayload
import com.haooz.chedule.ui.utils.ShareCodeFetchResult
import com.haooz.chedule.ui.utils.ThirdPartySharePayload
import com.haooz.chedule.ui.utils.ThirdPartyShareSource
import com.haooz.chedule.ui.utils.buildShareScheduleMap
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
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

/** 数据管理：课表导入 / 导出 / 备份 三种页面模式 */
enum class ScheduleDataManageMode {
    Import,
    Export,
    Backup,
}

/** 课表导入 / 导出 / 备份页面；由 [mode] 决定展示内容，入口页共用。 */
@Composable
fun BackupAndMigrationScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    courseViewModel: CourseViewModel = courseViewModel(),
    scheduleViewModel: ScheduleViewModel = scheduleViewModel(),
    settingsViewModel: SettingsViewModel = settingsViewModel(),
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    mode: ScheduleDataManageMode = ScheduleDataManageMode.Import,
    /** pad 设置右栏：导入页去掉「导入方式」，并把口令/文件拆成两个小标题 */
    compactImport: Boolean = false,
    /** 课表文件的选文件 / 另存为能力（Android 注入 `AndroidScheduleFileController`；桌面/iOS 用 [NoopScheduleFileController]）。 */
    scheduleFileController: ScheduleFileController = NoopScheduleFileController,
    /** 节假日/调休数据变更后的平台副作用（重排提醒），Android 宿主注入。 */
    onHolidayDataChanged: () -> Unit = {},
    /** 分享口令取回（Android 专有，宿主注入）；不注入时按「取回失败」处理。 */
    onFetchShareCode: suspend (code: String) -> Result<ShareCodeFetchResult> =
        { Result.failure(IllegalStateException(BackupStr.platformNotSupportedShareCodeImport())) },
    /** 第三方（WakeUp / 星链）口令抓取（Android 专有，宿主注入）。 */
    onImportThirdParty: suspend (source: ThirdPartyShareSource, code: String)
        -> Result<ThirdPartySharePayload> =
        { source, _ -> Result.failure(IllegalStateException(BackupStr.platformNotSupportedN(source.displayName))) },
    /** 口令分享导出（上传 + 出图 + 系统分享，Android 专有，宿主注入）。 */
    onShareSchedule: (scheduleName: String, onSharingChanged: (Boolean) -> Unit) -> Unit = { _, _ -> },
    /** 导出到手环（Android 专有；未连接/不支持时返回 null） */
    onExportToWearable: suspend (scheduleName: String) -> String? = { null },
    /** 同步到系统日历（Android 专有；未实现的平台保持默认值 → 点按只提示不支持） */
    onSyncToCalendar: suspend (scheduleName: String) -> CalendarSyncResult = { CalendarSyncResult.Unsupported },
) {
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // 液态玻璃效果的透明下拉颜色
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    )

    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    // 恢复成功后的提醒重排是平台副作用，走注入
    val webDavManager = remember { WebDavManager { onHolidayDataChanged() } }
    val lastSyncTimeMs = webDavManager.lastSyncTime
    val lastSyncSummary = remember(lastSyncTimeMs, AppLanguage.resolved) {
        // 格式只含数字与固定分隔符、不受 Locale 影响（:core 手写等价格式化）。
        if (lastSyncTimeMs > 0L) BackupStr.lastOperationAtN(localDateTimeAt(lastSyncTimeMs).formatDateTimeMinute())
        else BackupStr.noOperationYet()
    }

    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var pendingImportScheduleName by remember { mutableStateOf("") }
    var showShareCodeDialog by remember { mutableStateOf(false) }
    var shareCodeInput by remember { mutableStateOf("") }
    var isImportingShareCode by remember { mutableStateOf(false) }

    // WakeUp / 星链：课表导入页直接输入口令导入
    // 弹窗始终挂载、靠 show 驱动，避免 if/let 卸载导致关闭无退出动画
    var showThirdPartyCodeDialog by remember { mutableStateOf(false) }
    var thirdPartySource by remember { mutableStateOf(ThirdPartyShareSource.WakeUp) }
    var thirdPartyCodeInput by remember { mutableStateOf("") }
    var isImportingThirdParty by remember { mutableStateOf(false) }
    var pendingThirdPartyPayload by remember { mutableStateOf<ThirdPartySharePayload?>(null) }
    var showThirdPartyConfirmDialog by remember { mutableStateOf(false) }

    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()
    val currentScheduleName by scheduleViewModel.currentScheduleName.collectAsState()
    var selectedExportSchedule by remember { mutableStateOf(currentScheduleName) }

    var showShareExportConfirmDialog by remember { mutableStateOf(false) }
    var isSharingExport by remember { mutableStateOf(false) }

    /** 选一个 JSON 备份/课表文件读入；控制器已按原文案处理取消与读取失败 */
    fun pickJsonFile() {
        scheduleFileController.openTextFile(listOf("application/json", "*/*")) { text ->
            if (text == null) return@openTextFile
            if (text.isBlank()) {
                showToast(BackupStr.fileReadFailed())
                return@openTextFile
            }
            val (success, message, data) = parseFullScheduleJson(text)
            if (success && data != null) {
                pendingImportData = data
                pendingImportScheduleName = (data["schedule_name"] as? String) ?: BackupStr.importedScheduleName()
                showImportConfirmDialog = true
            } else {
                showToast(message)
            }
        }
    }

    /** 选一个 ICS 日程文件读入 */
    fun pickIcsFile() {
        scheduleFileController.openTextFile(listOf("text/calendar", "*/*")) { text ->
            if (text == null) return@openTextFile
            if (text.isBlank()) {
                showToast(BackupStr.fileReadFailed())
                return@openTextFile
            }
            val (success, message, data) = parseIcsFile(text)
            if (success && data != null) {
                pendingImportData = data
                pendingImportScheduleName = BackupStr.icsImportedScheduleName()
                showImportConfirmDialog = true
            } else {
                showToast(message)
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
                    (if (isTablet) 12.dp else 0.dp),
                bottom = 60.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (mode == ScheduleDataManageMode.Import && !compactImport) {
                item {
                    SmallTitle(
                        text = BackupStr.importMethods(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // LocalAppRouter.current 是 @Composable 调用，必须在组合层取
                            val aiImportRouter = LocalAppRouter.current
                            ArrowPreference(
                                title = BackupStr.aiTextImport(),
                                summary = BackupStr.aiTextImportSummary(),
                                onClick = {
                                    aiImportRouter.navigate(AppRoute.AiImport)
                                }
                            )
                            ArrowPreference(
                                title = BackupStr.educationalImport(),
                                summary = BackupStr.educationalImportSummary(),
                                onClick = {
                                    // 走路由，不再 startActivity
                                    aiImportRouter.navigate(AppRoute.EducationalImport)
                                }
                            )
                        }
                    }
                }
            }

            if (mode == ScheduleDataManageMode.Import && compactImport) {
                // pad：口令
                item {
                    SmallTitle(
                        text = BackupStr.shareCodeSection(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = BackupStr.shareCodeImport(),
                                summary = BackupStr.shareCodeImportSummary(),
                                holdDownState = showShareCodeDialog,
                                onClick = {
                                    shareCodeInput = ""
                                    showShareCodeDialog = true
                                }
                            )
                            ArrowPreference(
                                title = BackupStr.wakeUpCodeImport(),
                                summary = BackupStr.wakeUpCodeImportSummary(),
                                holdDownState = showThirdPartyCodeDialog && thirdPartySource == ThirdPartyShareSource.WakeUp,
                                onClick = {
                                    thirdPartySource = ThirdPartyShareSource.WakeUp
                                    thirdPartyCodeInput = ""
                                    showThirdPartyCodeDialog = true
                                }
                            )
                            ArrowPreference(
                                title = BackupStr.starLinkCodeImport(),
                                summary = BackupStr.starLinkCodeImportSummary(),
                                holdDownState = showThirdPartyCodeDialog && thirdPartySource == ThirdPartyShareSource.StarLink,
                                onClick = {
                                    thirdPartySource = ThirdPartyShareSource.StarLink
                                    thirdPartyCodeInput = ""
                                    showThirdPartyCodeDialog = true
                                }
                            )
                        }
                    }
                }
                // pad：文件
                item {
                    SmallTitle(
                        text = BackupStr.fileSection(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = BackupStr.jsonFileImport(),
                                summary = BackupStr.jsonFileImportSummary(),
                                onClick = { pickJsonFile() }
                            )
                            ArrowPreference(
                                title = BackupStr.icsFileImport(),
                                summary = BackupStr.icsFileImportSummary(),
                                onClick = { pickIcsFile() }
                            )
                        }
                    }
                }
            } else if (mode == ScheduleDataManageMode.Import) {
                item {
                    SmallTitle(
                        text = BackupStr.fileAndCodeSection(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = BackupStr.shareCodeImport(),
                                summary = BackupStr.shareCodeImportSummary(),
                                holdDownState = showShareCodeDialog,
                                onClick = {
                                    shareCodeInput = ""
                                    showShareCodeDialog = true
                                }
                            )
                            ArrowPreference(
                                title = BackupStr.wakeUpCodeImport(),
                                summary = BackupStr.wakeUpCodeImportSummary(),
                                holdDownState = showThirdPartyCodeDialog && thirdPartySource == ThirdPartyShareSource.WakeUp,
                                onClick = {
                                    thirdPartySource = ThirdPartyShareSource.WakeUp
                                    thirdPartyCodeInput = ""
                                    showThirdPartyCodeDialog = true
                                }
                            )
                            ArrowPreference(
                                title = BackupStr.starLinkCodeImport(),
                                summary = BackupStr.starLinkCodeImportSummary(),
                                holdDownState = showThirdPartyCodeDialog && thirdPartySource == ThirdPartyShareSource.StarLink,
                                onClick = {
                                    thirdPartySource = ThirdPartyShareSource.StarLink
                                    thirdPartyCodeInput = ""
                                    showThirdPartyCodeDialog = true
                                }
                            )
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = BackupStr.jsonFileImport(),
                                summary = BackupStr.jsonFileImportSummary(),
                                onClick = { pickJsonFile() }
                            )
                            ArrowPreference(
                                title = BackupStr.icsFileImport(),
                                summary = BackupStr.icsFileImportSummary(),
                                onClick = { pickIcsFile() }
                            )
                        }
                    }
                }
            }

            if (mode == ScheduleDataManageMode.Export) {
                item {
                    SmallTitle(
                        text = BackupStr.exportSchedule(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            CardExpand(visible = scheduleNames.isNotEmpty()) {
                                OverlayDropdownMenu(
                                    title = BackupStr.selectSchedule(),
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
                                    collapseOnSelection = true,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                            }
                            CardExpand(visible = scheduleNames.isEmpty()) {
                                Text(
                                    text = BackupStr.noScheduleForExport(),
                                    modifier = Modifier.padding(16.dp),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                }
            }

            if (mode == ScheduleDataManageMode.Export) {
                item {
                    SmallTitle(
                        text = BackupStr.exportFormat(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = BackupStr.jsonExport(),
                                summary = BackupStr.jsonExportSummary(),
                                onClick = {
                                    val json = buildExportJson(selectedExportSchedule)
                                    if (json != null) {
                                        scheduleFileController.saveTextFile(
                                            fileName = "${selectedExportSchedule}.json",
                                            mimeType = "application/json",
                                            content = json,
                                        )
                                    }
                                }
                            )
                            ArrowPreference(
                                title = BackupStr.icsExport(),
                                summary = BackupStr.icsExportSummary(),
                                onClick = {
                                    val ics = buildExportIcs(selectedExportSchedule)
                                    if (ics != null) {
                                        scheduleFileController.saveTextFile(
                                            fileName = "${selectedExportSchedule}.ics",
                                            mimeType = "text/calendar",
                                            content = ics,
                                        )
                                    }
                                }
                            )
                            ArrowPreference(
                                title = BackupStr.shareCodeExport(),
                                summary = BackupStr.shareCodeExportSummary(),
                                holdDownState = showShareExportConfirmDialog,
                                onClick = {
                                    if (selectedExportSchedule.isBlank()) {
                                        showToast(BackupStr.selectScheduleToShareFirst())
                                    } else if (!isSharingExport) {
                                        showShareExportConfirmDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = BackupStr.syncToCalendar(),
                                summary = BackupStr.syncToCalendarSummary(),
                                onClick = {
                                    if (selectedExportSchedule.isBlank()) {
                                        showToast(BackupStr.selectScheduleToShareFirst())
                                    } else {
                                        scope.launch {
                                            val result = onSyncToCalendar(selectedExportSchedule)
                                            val message = when (result) {
                                                is CalendarSyncResult.Success ->
                                                    BackupStr.calendarSyncSuccessN(result.count)
                                                CalendarSyncResult.PermissionDenied ->
                                                    BackupStr.calendarPermissionDenied()
                                                CalendarSyncResult.NoWritableCalendar ->
                                                    BackupStr.calendarNoWritable()
                                                CalendarSyncResult.Failed ->
                                                    BackupStr.calendarSyncFailed()
                                                CalendarSyncResult.Unsupported ->
                                                    BackupStr.platformNotSupportedN(BackupStr.syncToCalendar())
                                            }
                                            showToast(message, longDuration = true)
                                        }
                                    }
                                }
                            )
                            ArrowPreference(
                                title = BackupStr.exportToWearable(),
                                summary = BackupStr.exportToWearableSummary(),
                                onClick = {
                                    scope.launch {
                                        val path = onExportToWearable(selectedExportSchedule)
                                        if (path != null) {
                                            showToast(BackupStr.wearableExportSuccessN(path), longDuration = true)
                                        } else {
                                            showToast(BackupStr.wearableExportFailed())
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (mode == ScheduleDataManageMode.Backup) {
                item {
                    SmallTitle(
                        text = BackupStr.backupSection(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // LocalAppRouter.current 是 @Composable 调用，必须在组合层取
                            val appRouter = LocalAppRouter.current
                            ArrowPreference(
                                title = BackupStr.localBackup(),
                                summary = BackupStr.localBackupSummary(),
                                onClick = {
                                    appRouter.navigate(AppRoute.LocalBackup)
                                }
                            )
                            ArrowPreference(
                                title = BackupStr.webDavBackup(),
                                summary = if (webDavManager.isConfigured()) lastSyncSummary else BackupStr.webDavBackupSummary(),
                                onClick = {
                                    appRouter.navigate(AppRoute.WebDavSettings)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 撞名时弹窗自己改成「覆盖」口径：口令同步同一张表必然撞名，不能只给一句「已存在」
    ScheduleImportConfirmDialog(
        show = showImportConfirmDialog && pendingImportData != null,
        scheduleName = pendingImportScheduleName,
        onScheduleNameChange = { pendingImportScheduleName = it },
        existingNames = scheduleNames,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismiss = {
            showImportConfirmDialog = false
            pendingImportData = null
            // 名字不清：show 变 false 后还有退出动画，清掉会让输入框文字当场消失
        },
        onConfirm = { scheduleName, overwrite ->
            val data = pendingImportData
            if (data != null) {
                val (_, message) = applyScheduleData(
                    viewModel = courseViewModel,
                    scheduleViewModel = scheduleViewModel,
                    settingsViewModel = settingsViewModel,
                    scheduleName = scheduleName,
                    data = data,
                    overwriteExisting = overwrite,
                )
                showToast(message, longDuration = true)
            }
            showImportConfirmDialog = false
            pendingImportData = null
        }
    )

    // 始终挂载、靠 show 驱动：避免 if 卸载导致关闭无退出动画
    OverlayDialog(
        title = BackupStr.shareCodeExport(),
        summary = BackupStr.shareExportConfirmSummaryN(selectedExportSchedule),
        show = showShareExportConfirmDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = {
            if (!isSharingExport) {
                showShareExportConfirmDialog = false
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                text = CommonStr.cancel(),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    showShareExportConfirmDialog = false
                },
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = if (isSharingExport) BackupStr.sharing() else BackupStr.confirmShare(),
                onClick = {
                    if (isSharingExport) return@TextButton
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    val name = selectedExportSchedule
                    showShareExportConfirmDialog = false
                    // 上传 + 出图 + 系统分享全是 Android 专有能力，宿主注入
                    onShareSchedule(name) { isSharingExport = it }
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f)
            )
        }
    }

    OverlayDialog(
        title = BackupStr.shareCodeImportTitle(),
        summary = BackupStr.shareCodeDialogSummary(),
        show = showShareCodeDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = {
            if (!isImportingShareCode) {
                showShareCodeDialog = false
                shareCodeInput = ""
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NativeMiuixTextField(
                value = shareCodeInput,
                onValueChange = { shareCodeInput = it },
                label = BackupStr.shareCodeLabel(),
                modifier = Modifier.fillMaxWidth(),
                requestFocus = showShareCodeDialog
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = CommonStr.cancel(),
                    onClick = {
                        if (!isImportingShareCode) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showShareCodeDialog = false
                            shareCodeInput = ""
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = if (isImportingShareCode) BackupStr.importing() else BackupStr.fetchSchedule(),
                    onClick = {
                        if (isImportingShareCode) return@TextButton
                        val code = shareCodeInput.trim()
                        if (code.isBlank()) {
                            showToast(BackupStr.enterShareCode())
                            return@TextButton
                        }
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        isImportingShareCode = true
                        scope.launch {
                            try {
                                onFetchShareCode(code).fold(
                                    onSuccess = { fetched ->
                                        showShareCodeDialog = false
                                        shareCodeInput = ""
                                        pendingImportData = fetched.scheduleData
                                        pendingImportScheduleName = fetched.scheduleName
                                        showImportConfirmDialog = true
                                    },
                                    onFailure = { e ->
                                        showToast(e.message ?: BackupStr.shareCodeNotFound())
                                    }
                                )
                            } finally {
                                isImportingShareCode = false
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // WakeUp / 星链口令输入（始终挂载，靠 show 驱动退出动画）
    OverlayDialog(
        title = thirdPartySource.displayName,
        summary = BackupStr.thirdPartyCodeSummaryN(thirdPartySource.inputLabel),
        show = showThirdPartyCodeDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = {
            if (!isImportingThirdParty) {
                showThirdPartyCodeDialog = false
                thirdPartyCodeInput = ""
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NativeMiuixTextField(
                value = thirdPartyCodeInput,
                onValueChange = { thirdPartyCodeInput = it },
                label = thirdPartySource.inputLabel,
                modifier = Modifier.fillMaxWidth(),
                requestFocus = showThirdPartyCodeDialog
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = CommonStr.cancel(),
                    onClick = {
                        if (!isImportingThirdParty) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showThirdPartyCodeDialog = false
                            thirdPartyCodeInput = ""
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = if (isImportingThirdParty) BackupStr.importing() else BackupStr.fetchSchedule(),
                    onClick = {
                        if (isImportingThirdParty) return@TextButton
                        val source = thirdPartySource
                        val code = thirdPartyCodeInput.trim()
                        if (code.isBlank()) {
                            showToast(BackupStr.enterInputN(source.inputLabel))
                            return@TextButton
                        }
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        isImportingThirdParty = true
                        scope.launch {
                            try {
                                onImportThirdParty(source, code).fold(
                                    onSuccess = { payload ->
                                        showThirdPartyCodeDialog = false
                                        thirdPartyCodeInput = ""
                                        pendingThirdPartyPayload = payload
                                        pendingImportScheduleName = when (payload.source) {
                                            ThirdPartyShareSource.WakeUp -> BackupStr.wakeUpImportedName()
                                            ThirdPartyShareSource.StarLink -> BackupStr.starLinkImportedName()
                                        }
                                        showThirdPartyConfirmDialog = true
                                    },
                                    onFailure = { e ->
                                        showToast(e.message ?: BackupStr.thirdPartyCodeInvalid(), longDuration = true)
                                    }
                                )
                            } finally {
                                isImportingThirdParty = false
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // WakeUp / 星链：确认课表名后导入（始终挂载，退出动画期间保留文案）
    val thirdPartyConfirmSourceName = pendingThirdPartyPayload?.source?.displayName.orEmpty()
    val thirdPartyConfirmCourseCount = pendingThirdPartyPayload?.courseCount ?: 0
    // WakeUp / 星链：确认课表名后导入。与「文件 / 口令导入」共用同一个确认弹窗，
    // 撞名时同样能选覆盖 —— 以前这里也是撞名即失败。
    ScheduleImportConfirmDialog(
        show = showThirdPartyConfirmDialog,
        scheduleName = pendingImportScheduleName,
        onScheduleNameChange = { pendingImportScheduleName = it },
        existingNames = scheduleNames,
        liquidGlassBackdrop = liquidGlassBackdrop,
        newScheduleSummary = {
            if (thirdPartyConfirmSourceName.isEmpty()) {
                BackupStr.importCreatesNewSchedule()
            } else {
                BackupStr.thirdPartyConfirmSummaryN(thirdPartyConfirmSourceName, thirdPartyConfirmCourseCount)
            }
        },
        onDismiss = {
            if (!isImportingThirdParty) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                showThirdPartyConfirmDialog = false
            }
        },
        onConfirm = { name, overwrite ->
            pendingThirdPartyPayload?.let { payload ->
                val (_, message) = applyThirdPartySharePayload(
                    courseViewModel = courseViewModel,
                    scheduleViewModel = scheduleViewModel,
                    settingsViewModel = settingsViewModel,
                    scheduleName = name,
                    payload = payload,
                    overwriteExisting = overwrite,
                )
                showToast(message, longDuration = true)
            }
            showThirdPartyConfirmDialog = false
        }
    )
}

private fun buildExportJson(scheduleName: String): String? {
    val repository = CourseRepository()
    val data = buildShareScheduleMap(repository, scheduleName)
    if (data == null) {
        showToast(BackupStr.exportEmptyScheduleN(scheduleName))
        return null
    }
    return encodeBackupJson(data)
}

internal fun icsDatesForCourse(
    course: Course,
    semesterStartDate: LocalDate,
    rules: List<TeachingWeekReorganizationRule>,
): List<Pair<Int, LocalDate>> {
    val weeks = course.selectedWeeks.ifEmpty { (course.startWeek..course.endWeek).toList() }
    return weeks.asSequence().filter { course.isActiveInWeek(it) }.mapNotNull { week ->
        TeachingWeekReorganization.dateForPosition(semesterStartDate, week, course.dayOfWeek, rules)
            ?.let { week to it }
    }.toList()
}

internal fun icsEffectiveDatesForCourse(
    course: Course,
    semesterStartDate: LocalDate,
    rules: List<TeachingWeekReorganizationRule>,
    entriesByYear: Map<Int, List<HolidayEntry>>,
    exclusion: HolidayEndCourseExclusion,
    sectionTimes: Map<Int, String>,
    sectionCount: Int,
    totalWeeks: Int,
    lastWeekWithCourses: Int,
    beforeExclusion: HolidayBeforeCourseExclusion = HolidayBeforeCourseExclusion(),
): List<Pair<Int, LocalDate>> {
    val candidates = (icsDatesForCourse(course, semesterStartDate, rules).map { it.second } +
        entriesByYear.values.flatten().asSequence()
            .filter { it.type == HolidayManager.TYPE_WORKSWAP }
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .toList()).distinct().sorted()
    return candidates.mapNotNull { date ->
        val position = TeachingWeekReorganization.mapDate(semesterStartDate, date, rules)
        val swap = HolidayManager.entriesForDate(entriesByYear, date)
            .firstOrNull { it.type == HolidayManager.TYPE_WORKSWAP }
        // 调休跟随的是绝对日期，按当前课表换算 (周次, 星期)；老数据无 followDate 才回退
        val followDate = swap?.followLocalDate()
        val followPosition = followDate?.let {
            TeachingWeekReorganization.mapDate(semesterStartDate, it, rules)
        }
        val weekday = followPosition?.weekday
            ?: swap?.followWeekday?.takeIf { it in 1..7 }
            // Native 上 kotlinx-datetime 自带枚举只有 isoDayNumber 扩展。
            // 两者语义相同（1=周一…7=周日），但只有 isoDayNumber 跨平台。
            ?: followDate?.dayOfWeek?.isoDayNumber
            ?: position.weekday
        val week = (followPosition?.week
            ?: swap?.followWeek?.takeIf { it > 0 }?.toLong()
            ?: position.week)
            .takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt() ?: return@mapNotNull null
        val allowed = DayScheduleResolver.canResolveCourseCandidates(
            position, swap, week, totalWeeks, lastWeekWithCourses,
        ) && weekday == course.dayOfWeek && course.isActiveInWeek(week)
        val resolution = HolidayCourseExclusion.resolveDayCourses(
            entriesByYear = entriesByYear,
            date = date,
            exclusion = exclusion,
            beforeExclusion = beforeExclusion,
            candidates = { if (allowed) listOf(course) else emptyList() },
            sectionTimes = { sectionTimes },
            sectionCount = { sectionCount },
        )
        (week to date).takeIf { resolution.courses.isNotEmpty() }
    }
}

internal fun icsEventUid(courseId: String, teachingWeek: Int, date: LocalDate): String =
    "$courseId-$teachingWeek-${date.toString().replace("-", "")}@nexio-schedule"

/** 一条待写进系统日历的课程事件；时间按「生效日 + 节次」算好，与 ICS 导出同源。 */
data class ScheduleCalendarEvent(
    val uid: String,
    val title: String,
    val location: String,
    val description: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
)

/** 「同步到日历」的结果。未实现的平台恒为 [Unsupported]。 */
sealed interface CalendarSyncResult {
    /** 写入条数（已存在而跳过的也算成功）。 */
    data class Success(val count: Int) : CalendarSyncResult
    data object PermissionDenied : CalendarSyncResult
    data object NoWritableCalendar : CalendarSyncResult
    data object Failed : CalendarSyncResult
    data object Unsupported : CalendarSyncResult
}

/**
 * 把某张课表展开成逐节课的事件列表；课表为空时弹 toast 并返回 null。
 *
 * [buildExportIcs] 和宿主的「同步到日历」都走这里，时间口径只有一份。
 * ⚠ 节次时间可能越界（如 `24:00`）：`LocalTime` 入参越界会抛异常，故夹取到 23:59，
 * 不沿用旧 `Calendar.set` 的静默进位。
 */
fun buildScheduleCalendarEvents(scheduleName: String): List<ScheduleCalendarEvent>? {
    val repository = CourseRepository()
    val courses = repository.getCoursesForSchedule(scheduleName)
    if (courses.isEmpty()) {
        showToast(BackupStr.exportEmptyScheduleN(scheduleName))
        return null
    }

    val sectionTimes = repository.getSectionTimes(scheduleName)
    val semesterStartDate = LocalDate.parse(repository.getClassStartTime(scheduleName).replace('/', '-'))
    val rules = repository.getTeachingWeekReorganizations(scheduleName)
    val holidayEntries = HolidayManager.loadAllByYear()
    val holidayExclusion = HolidayManager.loadEndCourseExclusion()
    val holidayBeforeExclusion =
        HolidayManager.loadBeforeCourseExclusion()
    val sectionCount = repository.getMorningSections(scheduleName) +
        repository.getAfternoonSections(scheduleName) + repository.getEveningSections(scheduleName)
    val lastWeekWithCourses = courses.maxOfOrNull {
        it.selectedWeeks.maxOrNull() ?: it.endWeek
    } ?: 0

    val events = ArrayList<ScheduleCalendarEvent>(courses.size * 8)
    for (course in courses) {
        val startSectionTime = course.getEffectiveStartTime(sectionTimes) ?: continue
        val endSectionTime = course.getEffectiveEndTime(sectionTimes) ?: continue

        val startHour = startSectionTime.substringBefore("-").substringBefore(":").toIntOrNull() ?: 8
        val startMinute = startSectionTime.substringBefore("-").substringAfter(":").toIntOrNull() ?: 0
        val endHour = endSectionTime.substringAfter("-").substringBefore(":").toIntOrNull() ?: 9
        val endMinute = endSectionTime.substringAfter("-").substringAfter(":").toIntOrNull() ?: 0

        // 为每个周次生成独立事件（避免非连续周的 RRULE 问题）
        for ((week, actualDate) in icsEffectiveDatesForCourse(
            course, semesterStartDate, rules, holidayEntries, holidayExclusion,
            sectionTimes, sectionCount, repository.getTotalWeeks(scheduleName), lastWeekWithCourses,
            holidayBeforeExclusion,
        )) {
            events += ScheduleCalendarEvent(
                uid = icsEventUid(course.id, week, actualDate),
                title = course.name,
                location = listOfNotNull(
                    course.classroom.takeIf { it.isNotBlank() },
                    course.teacher.takeIf { it.isNotBlank() }
                ).joinToString(" "),
                description = CommonStr.weekN(week),
                start = LocalDateTime(
                    actualDate,
                    LocalTime(startHour.coerceIn(0, 23), startMinute.coerceIn(0, 59), 0),
                ),
                end = LocalDateTime(
                    actualDate,
                    LocalTime(endHour.coerceIn(0, 23), endMinute.coerceIn(0, 59), 0),
                ),
            )
        }
    }
    return events
}

/** 组装 ICS 文本（`DTSTART/DTEND` 用 [LocalDateTime] + [formatIcsTimestamp]）。 */
private fun buildExportIcs(scheduleName: String): String? {
    val events = buildScheduleCalendarEvents(scheduleName) ?: return null

    return buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//Nexio Schedule//Course Schedule//CN")
        appendLine("CALSCALE:GREGORIAN")
        appendLine("METHOD:PUBLISH")

        for (e in events) {
            appendLine("BEGIN:VEVENT")
            appendLine("UID:${e.uid}")
            appendLine("DTSTART:${e.start.formatIcsTimestamp()}")
            appendLine("DTEND:${e.end.formatIcsTimestamp()}")
            appendLine("RRULE:FREQ=WEEKLY;COUNT=1")
            appendLine("SUMMARY:${e.title}")
            if (e.location.isNotBlank()) appendLine("LOCATION:${e.location}")
            appendLine("DESCRIPTION:${e.description}")
            appendLine("END:VEVENT")
        }

        appendLine("END:VCALENDAR")
    }
}
