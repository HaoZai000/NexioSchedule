/** AI 文本导入页面 - Screen */
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.randomUuidV4
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.screens.AddCourseDialog
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.continuousBackground
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun AiImportScreen(
    onBack: () -> Unit,
    scrollBehavior: SharedScrollBehavior? = null,
    viewModel: CourseViewModel = courseViewModel(),
    settingsViewModel: SettingsViewModel = settingsViewModel(),
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    // 平台宿主注入：复制提示词到系统剪切板（由宿主实现，桌面/iOS 默认空实现）
    onCopyToClipboard: (String) -> Unit = {}
) {
    val hapticFeedback = LocalHapticFeedback.current

    val morningSections by settingsViewModel.morningSections.collectAsState()
    val afternoonSections by settingsViewModel.afternoonSections.collectAsState()
    val eveningSections by settingsViewModel.eveningSections.collectAsState()
    // 节次上限动态化：取用户设置的总节次数，至少 12 以兼容旧数据
    val maxSection by remember(morningSections, afternoonSections, eveningSections) {
        derivedStateOf {
            (morningSections + afternoonSections + eveningSections).coerceAtLeast(12)
        }
    }

    val existingCourses by viewModel.courses.collectAsState()
    val totalWeeks by viewModel.totalWeeks.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var appendMode by remember { mutableStateOf(false) }
    var editingCourseIndex by remember { mutableIntStateOf(-1) }
    // 编辑中课程快照：退出动画期间弹窗仍在屏上，不能依赖列表实时对象
    var editingCourseSnapshot by remember { mutableStateOf<Course?>(null) }
    // 关闭时先置 false 让底部弹窗播完退出动画，再卸载组件
    var showEditDialog by remember { mutableStateOf(false) }
    // 真正删除延迟到退出动画结束后，避免列表缩短导致组件被提前移除
    var pendingDelete by remember { mutableStateOf(false) }

    // inputText 变化时重新解析覆盖；用户在预览中修改时直接改此列表
    val parsedCourses = remember { mutableStateListOf<Course>() }
    val parsedWarnings = remember { mutableStateListOf<String>() }
    // AI 输出节次配置时解析得到，用户可在预览中修改
    var parsedSectionConfig by remember { mutableStateOf<SectionConfig?>(null) }

    LaunchedEffect(inputText, maxSection) {
        if (inputText.isBlank()) {
            parsedCourses.clear()
            parsedWarnings.clear()
            parsedSectionConfig = null
        } else {
            val config = parseSectionConfig(inputText)
            parsedSectionConfig = config
            // 有节次配置时用其总节数作为上限，否则用当前设置
            val effectiveMax = config?.let {
                (it.morningCount + it.afternoonCount + it.eveningCount).coerceAtLeast(12)
            } ?: maxSection
            val (courses, warnings) = parseImportedCourses(inputText, effectiveMax)
            parsedCourses.clear()
            parsedCourses.addAll(courses)
            parsedWarnings.clear()
            parsedWarnings.addAll(warnings)
        }
    }

    // 等底部弹窗退出动画（320ms）播完后再执行删除并卸载弹窗
    LaunchedEffect(showEditDialog) {
        if (!showEditDialog && editingCourseSnapshot != null) {
            delay(340.milliseconds)
            if (pendingDelete && editingCourseIndex in parsedCourses.indices) {
                parsedCourses.removeAt(editingCourseIndex)
            }
            pendingDelete = false
            editingCourseIndex = -1
            editingCourseSnapshot = null
        }
    }

    // 编辑课程时优先使用解析到的节次配置上限
    val effectiveMaxSection = parsedSectionConfig?.let {
        (it.morningCount + it.afternoonCount + it.eveningCount).coerceAtLeast(12)
    } ?: maxSection

    val promptText = remember(AppLanguage.resolved) {
        ImportStr.aiPrompt()
    }

    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .collapsibleTopInset(scrollBehavior)
                .then(
                    scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                )
                .verticalScroll(rememberScrollState())
                .padding(
                    start = tabletHorizontalPadding,
                    end = tabletHorizontalPadding,
                    top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight +
                        (if (isTablet) 12.dp else 0.dp),
                    bottom = 120.dp
                )
        ) {
            SmallTitle(
                text = ImportStr.scheduleData(),
            )
            NativeMiuixTextField(
                value = inputText,
                onValueChange = { inputText = it },
                minLines = 1,
                maxLines = 12,
                label = ImportStr.pasteAiDataHint(),
                useLabelAsPlaceholder = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 解析预览：标题 + 预览卡片 + 警告详情
            val previewVisible = parsedCourses.isNotEmpty() || parsedWarnings.isNotEmpty() || parsedSectionConfig != null
            if (previewVisible) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SmallTitle(
                        text = ImportStr.parsePreview(),
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .continuousBackground(CardDefaults.defaultColors().color, CardDefaults.CornerRadius)
                    ) {
                        Text(
                            text = ImportStr.identifiedCoursesN(parsedCourses.size),
                            style = MiuixTheme.textStyles.title2,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 12.dp)
                        )
                        parsedSectionConfig?.let { config ->
                            Text(
                                text = ImportStr.sectionConfigIdentified(config.morningCount, config.afternoonCount, config.eveningCount) +
                                    if (config.morningTimes.isNotEmpty() || config.afternoonTimes.isNotEmpty() || config.eveningTimes.isNotEmpty())
                                        ImportStr.withTimesSuffix() else ImportStr.autoApplySuffix(),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                            )
                        }
                        if (parsedWarnings.isNotEmpty()) {
                            Text(
                                text = ImportStr.warningsCountN(parsedWarnings.size),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                            )
                        }
                        parsedCourses.forEachIndexed { index, course ->
                            CoursePreviewRow(
                                course = course,
                                onClick = {
                                    editingCourseIndex = index
                                    editingCourseSnapshot = course
                                    showEditDialog = true
                                }
                            )
                            if (index < parsedCourses.lastIndex) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                        if (parsedCourses.isNotEmpty()) {
                            TextButton(
                                text = ImportStr.confirmImportCard(),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    showConfirmDialog = true
                                },
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }
                    }
                    if (parsedWarnings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .continuousBackground(CardDefaults.defaultColors().color, CardDefaults.CornerRadius)
                        ) {
                            Text(
                                text = ImportStr.warningDetails(),
                                style = MiuixTheme.textStyles.title2,
                                fontSize = 17.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 17.dp)
                            )
                            parsedWarnings.forEachIndexed { idx, w ->
                                Text(
                                    text = w,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
                                )
                                if (idx < parsedWarnings.lastIndex) {
                                    androidx.compose.material3.HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.1f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SmallTitle(
                text = ImportStr.usageGuide(),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = ImportStr.promptUsageHint(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = promptText,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        text = ImportStr.copyPrompt(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onCopyToClipboard(promptText)
                            showToast(ImportStr.promptCopied())
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // 组件常驻到退出动画结束（show=false 后仍保留在树中），否则会瞬间消失
    editingCourseSnapshot?.let { snapshot ->
        AddCourseDialog(
            show = showEditDialog,
            course = snapshot,
            selectedDay = snapshot.dayOfWeek,
            liquidGlassBackdrop = liquidGlassBackdrop,
            totalWeeks = totalWeeks,
            totalSections = effectiveMaxSection,
            defaultStartSection = snapshot.startSection,
            defaultEndSection = snapshot.endSection,
            getOccupiedWeeks = { _, _, _, _, _, _ -> emptySet() },
            onDismiss = {
                showEditDialog = false
            },
            onConfirm = { updated ->
                if (editingCourseIndex in parsedCourses.indices) {
                    parsedCourses[editingCourseIndex] = updated
                }
            },
            onDelete = { _ ->
                pendingDelete = true
            }
        )
    }

    OverlayDialog(
        title = if (appendMode) ImportStr.appendDialogTitle() else ImportStr.overwriteDialogTitle(),
        summary = buildString {
            if (appendMode) {
                if (existingCourses.isNotEmpty()) {
                    append(ImportStr.appendSummaryCombined(existingCourses.size, parsedCourses.size, existingCourses.size + parsedCourses.size))
                    append(ImportStr.appendKeepExisting())
                } else {
                    append(ImportStr.totalCoursesN(parsedCourses.size))
                }
            } else {
                if (existingCourses.isNotEmpty()) {
                    append(ImportStr.replaceSummary(existingCourses.size, parsedCourses.size))
                } else {
                    append(ImportStr.totalCoursesN(parsedCourses.size))
                }
            }
            parsedSectionConfig?.let { cfg ->
                append(ImportStr.applySectionConfigInfo(cfg.morningCount, cfg.afternoonCount, cfg.eveningCount))
            }
        },
        show = showConfirmDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showConfirmDialog = false }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 仅在已有课程时提供增量添加
            if (existingCourses.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        state = if (appendMode) ToggleableState.On else ToggleableState.Off,
                        onClick = {
                            appendMode = !appendMode
                        }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ImportStr.appendModeLabel(),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (appendMode) ImportStr.appendModeOnDesc() else ImportStr.appendModeOffDesc(),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }
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
                        showConfirmDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = if (appendMode) ImportStr.confirmAppend() else ImportStr.confirmImport(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (parsedCourses.isNotEmpty()) {
                            // 先应用节次配置（节数和时间），再替换课程
                            parsedSectionConfig?.let { cfg ->
                                settingsViewModel.setMorningSections(cfg.morningCount)
                                settingsViewModel.setAfternoonSections(cfg.afternoonCount)
                                settingsViewModel.setEveningSections(cfg.eveningCount)
                                if (cfg.morningTimes.isNotEmpty()) settingsViewModel.saveMorningTimes(cfg.morningTimes)
                                if (cfg.afternoonTimes.isNotEmpty()) settingsViewModel.saveAfternoonTimes(cfg.afternoonTimes)
                                if (cfg.eveningTimes.isNotEmpty()) settingsViewModel.saveEveningTimes(cfg.eveningTimes)
                                // 同步覆盖当前课表绑定的时间配置，避免之后被旧配置盖回
                                settingsViewModel.applyTimeImportToCurrentSchedule(
                                    cfg.morningCount, cfg.afternoonCount, cfg.eveningCount,
                                    cfg.morningTimes, cfg.afternoonTimes, cfg.eveningTimes
                                )
                            }
                            if (appendMode) {
                                viewModel.appendCourses(parsedCourses.toList())
                            } else {
                                viewModel.replaceCourses(parsedCourses.toList())
                            }
                            showToast(if (appendMode) ImportStr.addedCoursesN(parsedCourses.size) else ImportStr.importedCoursesN(parsedCourses.size), longDuration = true)
                            showConfirmDialog = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 课程预览行：显示课程概要，点击进入编辑弹窗 */
@Composable
private fun CoursePreviewRow(
    course: Course,
    onClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val dayText = remember(course.dayOfWeek, AppLanguage.resolved) {
        when (course.dayOfWeek) {
            1 -> CommonStr.weekdayMon(); 2 -> CommonStr.weekdayTue(); 3 -> CommonStr.weekdayWed(); 4 -> CommonStr.weekdayThu()
            5 -> CommonStr.weekdayFri(); 6 -> CommonStr.weekdaySat(); 7 -> CommonStr.weekdaySun(); else -> "?"
        }
    }
    // 连续周次存为空列表（用 startWeek/endWeek 表示），getWeekText() 综合判断
    val weekText = remember(course.selectedWeeks, course.startWeek, course.endWeek, course.weekType, AppLanguage.resolved) {
        course.weekTextLocalized()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 13.dp, top = 17.dp, bottom = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color(course.colorRes), ContinuousCapsule())
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                val summaryLine1 = buildString {
                    append(dayText)
                    append(ImportStr.sectionRangeSuffix(course.startSection, course.endSection))
                    if (course.classroom.isNotBlank()) {
                        append(ImportStr.classroomSuffix(course.classroom))
                    }
                }
                Text(
                    text = summaryLine1,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Text(
                    text = weekText,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = CommonStr.edit(),
                modifier = Modifier.size(16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
        }
    }
}

/**
 * 节次配置数据：上午/下午/晚上的节数与每节起止时间
 * 时间格式: Map<节次编号, "HH:mm-HH:mm">，节次编号为各时段内的相对编号
 */
data class SectionConfig(
    val morningCount: Int,
    val afternoonCount: Int,
    val eveningCount: Int,
    val morningTimes: Map<Int, String>,
    val afternoonTimes: Map<Int, String>,
    val eveningTimes: Map<Int, String>
)

/** 节次配置行前缀，用于从文本中识别并跳过（英文前缀供英文版提示词的 AI 返回格式使用） */
private val SECTION_CONFIG_PREFIXES = listOf(
    "上午节数", "下午节数", "晚上节数", "上午时间", "下午时间", "晚上时间",
    "Morning sections", "Afternoon sections", "Evening sections",
    "Morning times", "Afternoon times", "Evening times"
)

/** 判断某行是否为节次配置行 */
private fun isSectionConfigLine(line: String): Boolean {
    val trimmed = line.trim()
    return SECTION_CONFIG_PREFIXES.any { trimmed.startsWith(it) } && trimmed.contains("=")
}

/**
 * 从AI返回的文本中解析节次配置。
 * 支持 "上午节数=4"、"上午时间=1:08:00-08:45,2:08:55-09:40" 等格式；未识别到任何配置行时返回 null
 */
internal fun parseSectionConfig(text: String): SectionConfig? {
    var morningCount: Int? = null
    var afternoonCount: Int? = null
    var eveningCount: Int? = null
    var morningTimes: Map<Int, String>? = null
    var afternoonTimes: Map<Int, String>? = null
    var eveningTimes: Map<Int, String>? = null

    for (line in text.trim().lines()) {
        val trimmed = line.trim()
        if (!isSectionConfigLine(trimmed)) continue
        val eqIdx = trimmed.indexOf("=")
        if (eqIdx <= 0) continue
        val key = trimmed.substring(0, eqIdx).trim()
        val value = trimmed.substring(eqIdx + 1).trim()
        when (key) {
            "上午节数", "Morning sections" -> morningCount = value.toIntOrNull()
            "下午节数", "Afternoon sections" -> afternoonCount = value.toIntOrNull()
            "晚上节数", "Evening sections" -> eveningCount = value.toIntOrNull()
            "上午时间", "Morning times" -> morningTimes = parseTimeMap(value)
            "下午时间", "Afternoon times" -> afternoonTimes = parseTimeMap(value)
            "晚上时间", "Evening times" -> eveningTimes = parseTimeMap(value)
        }
    }

    // 至少识别到一个节数才返回配置
    if (morningCount == null && afternoonCount == null && eveningCount == null) return null

    return SectionConfig(
        morningCount = morningCount ?: 4,
        afternoonCount = afternoonCount ?: 4,
        eveningCount = eveningCount ?: 2,
        morningTimes = morningTimes ?: emptyMap(),
        afternoonTimes = afternoonTimes ?: emptyMap(),
        eveningTimes = eveningTimes ?: emptyMap()
    )
}

/**
 * 解析时间映射字符串。
 * 格式: "1:08:00-08:45,2:08:55-09:40" → {1: "08:00-08:45", 2: "08:55-09:40"}；
 * 时间本身含冒号，因此按第一个冒号分割节次编号与时间
 */
internal fun parseTimeMap(timeStr: String): Map<Int, String> {
    val map = mutableMapOf<Int, String>()
    val normalized = timeStr.replace("，", ",").replace("、", ",")
    for (part in normalized.split(",")) {
        val trimmed = part.trim()
        if (trimmed.isBlank()) continue
        val colonIdx = trimmed.indexOf(":")
        if (colonIdx <= 0) continue
        val num = trimmed.substring(0, colonIdx).trim().toIntOrNull() ?: continue
        val time = trimmed.substring(colonIdx + 1).trim()
        if (time.isNotBlank()) map[num] = time
    }
    return map
}

/**
 * 解析导入的课程文本数据。
 * 格式: 课程名称|教室|教师|星期几|开始节次|结束节次|周次；
 * 同名同教室同节次的课程自动合并周次；节次配置行会被自动跳过
 */
internal fun parseImportedCourses(text: String, maxSection: Int = 12): Pair<List<Course>, List<String>> {
    val warnings = mutableListOf<String>()
    val lines = text.trim().lines().filter { it.isNotBlank() && !isSectionConfigLine(it) }

    // key = 课程名|教室|教师|星期|开始节|结束节, value = 所有周次
    val mergedData = linkedMapOf<String, MutableSet<Int>>()
    val metaData = mutableMapOf<String, CourseMetaData>()

    for ((index, line) in lines.withIndex()) {
        try {
            val parts = line.split("|").map { it.trim() }
            if (parts.size < 7) {
                warnings.add(ImportStr.lineInsufficientFields(index + 1))
                continue
            }

            val name = parts[0].ifBlank { ImportStr.unnamedCourse() }
            val classroom = parts[1].trim()
            // 识别不到时留空，不写入占位文字
            val teacher = parts[2].trim()
            val dayOfWeek = parts[3].toIntOrNull()
            val startSection = parts[4].toIntOrNull()
            val endSection = parts[5].toIntOrNull()
            val weekStr = parts[6].trim()

            if (dayOfWeek == null || dayOfWeek !in 1..7) {
                warnings.add(ImportStr.lineInvalidDay(index + 1, name))
                continue
            }
            if (startSection == null || startSection !in 1..maxSection) {
                warnings.add(ImportStr.lineInvalidStartSection(index + 1, name, maxSection))
                continue
            }
            if (endSection == null || endSection !in 1..maxSection || endSection < startSection) {
                warnings.add(ImportStr.lineInvalidEndSection(index + 1, name, startSection, maxSection))
                continue
            }

            val selectedWeeks = parseWeekString(weekStr)
            if (selectedWeeks.isEmpty()) {
                warnings.add(ImportStr.lineInvalidWeeks(index + 1, name))
                continue
            }

            val key = "$name|$classroom|$teacher|$dayOfWeek|$startSection|$endSection"
            mergedData.getOrPut(key) { mutableSetOf() }.addAll(selectedWeeks)

            metaData[key] = CourseMetaData(name, classroom, teacher, dayOfWeek, startSection, endSection)

        } catch (_: Exception) {
            warnings.add(ImportStr.lineParseError(index + 1))
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
                id = randomUuidV4(),
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
 * 解析周次字符串，支持 "1,2,3,4,5,6,10,11,12" 和 "1-6,10-12"（兼容范围格式），
 * 兼容全角逗号"，"和顿号"、"
 */
internal fun parseWeekString(weekStr: String): List<Int> {
    val weeks = mutableListOf<Int>()
    val normalized = weekStr.replace("，", ",").replace("、", ",")
    val parts = normalized.split(",").map { it.trim() }

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
