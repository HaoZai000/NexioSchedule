/**
 * AI 教务导入预览页面 —— 与「AI 文本导入」的预览区同一套外观与交互：
 * 标题 + 一张卡片（课程数 / 可点开编辑的行 / 卡片内「确定导入」）+ 顶部确认弹窗。
 *
 * 为什么是独立一页而不是对话浮层里的覆盖层：这一页不需要那台 WebView，
 * 单独成页才能用系统返回、也才能复用文档页的外壳。
 */
package com.haooz.chedule.ui.activities.eduimport

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.ai.AiEduScriptUploader
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.ui.ai.AiEduPreviewHolder
import com.haooz.chedule.ui.ai.EduAgentDraft
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.nexioui.cardexpand.CardExpand
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.screens.AddCourseDialog
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.web.EduImportBridgeCore
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.continuousBackground
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.card.CardDefaults
import com.haooz.nexioui.dropdown.DropdownDefaults
import com.haooz.nexioui.dropdown.DropdownEntry
import com.haooz.nexioui.dropdown.DropdownItem
import androidx.compose.material3.Icon
import com.haooz.nexioui.textfield.TextField
import com.haooz.nexioui.scaffold.Scaffold
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.basic.ArrowRight
import com.haooz.nexioui.topappbar.CollapsibleTopAppBarDefaults
import com.haooz.nexioui.topappbar.SharedScrollBehavior
import com.haooz.nexioui.topappbar.collapsibleTopInset
import com.haooz.nexioui.window.isTabletWidth
import com.haooz.nexioui.overlaydialog.OverlayDialog
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic

@Composable
fun AiEduPreviewScreen(
    /** 已写入课表：宿主负责把预览页和 AI 导入页一起收掉 */
    onImported: () -> Unit,
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: GlassBackdrop? = null,
) {
    val courseViewModel = courseViewModel()
    val settingsViewModel = settingsViewModel()
    val scheduleViewModel = scheduleViewModel()
    val hapticFeedback = LocalHapticFeedback.current

    // 路由之间没有参数通道，草稿与课程由 AiEduPreviewHolder 带进来
    val draft = remember { AiEduPreviewHolder.draft }
    // 离开本页就清空，下一次进来不会拿到上一轮的草稿
    DisposableEffect(Unit) {
        onDispose { AiEduPreviewHolder.clear() }
    }
    val courses = remember {
        mutableStateListOf<Course>().apply { addAll(AiEduPreviewHolder.courses) }
    }
    // 解析期做过的取舍：合并掉多少重复、跳过多少门缺信息的课 —— 必须让用户看得见
    val mergedDuplicates = remember { AiEduPreviewHolder.mergedDuplicates }
    val skippedNames = remember { AiEduPreviewHolder.skippedNames }

    val morningSections by settingsViewModel.morningSections.collectAsState()
    val afternoonSections by settingsViewModel.afternoonSections.collectAsState()
    val eveningSections by settingsViewModel.eveningSections.collectAsState()
    val maxSection by remember(morningSections, afternoonSections, eveningSections) {
        derivedStateOf {
            (morningSections + afternoonSections + eveningSections).coerceAtLeast(12)
        }
    }
    val totalWeeks by courseViewModel.totalWeeks.collectAsState()
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()
    val currentScheduleName by scheduleViewModel.currentScheduleName.collectAsState()
    // 读目标课表已有课程数：课程流只反映当前课表，别的课表得直接问仓库
    val repository = remember { CourseRepository() }
    // 液态玻璃下拉：容器透明，底色交给浮层玻璃自己画
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )

    var showConfirmDialog by remember { mutableStateOf(false) }
    // 目标课表：默认当前课表；用户没选过或选中的表被删掉时回落到当前 / 第一张
    var pickedSchedule by remember { mutableStateOf<String?>(null) }
    val targetSchedule = pickedSchedule?.takeIf { it in scheduleNames }
        ?: currentScheduleName.takeIf { it in scheduleNames }
        ?: scheduleNames.firstOrNull()
    val targetExistingCount = remember(targetSchedule, currentScheduleName, showConfirmDialog) {
        val name = targetSchedule ?: return@remember 0
        if (name == currentScheduleName) courseViewModel.courses.value.size
        else repository.getCoursesForSchedule(name).size
    }
    // 学校名称：脚本上传到服务端后靠它归类，必填
    var schoolInput by remember { mutableStateOf("") }
    var editingCourseIndex by remember { mutableIntStateOf(-1) }
    // 编辑中课程快照：退出动画期间弹窗仍在屏上，不能依赖列表实时对象
    var editingCourseSnapshot by remember { mutableStateOf<Course?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    // 真正删除延迟到退出动画结束后，避免列表缩短导致组件被提前移除
    var pendingDelete by remember { mutableStateOf(false) }

    // 等底部弹窗退出动画（320ms）播完后再执行删除并卸载弹窗
    LaunchedEffect(showEditDialog) {
        if (!showEditDialog && editingCourseSnapshot != null) {
            delay(340.milliseconds)
            if (pendingDelete && editingCourseIndex in courses.indices) {
                courses.removeAt(editingCourseIndex)
            }
            pendingDelete = false
            editingCourseIndex = -1
            editingCourseSnapshot = null
        }
    }

    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    Scaffold(topBar = {}) { paddingValues ->
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
                        pageTopExtra(isTablet, hasSmallTitle = true),
                    bottom = 120.dp
                )
        ) {
            SmallTitle(
                text = ImportStr.parsePreview(),
            )
            if (draft == null || courses.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .continuousBackground(CardDefaults.defaultColors().color, CardDefaults.CornerRadius)
                        .padding(16.dp)
                ) {
                    Text(
                        text = ImportStr.aiEduPreviewEmpty(),
                        style = NexioTheme.textStyles.body1,
                        color = NexioTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .continuousBackground(CardDefaults.defaultColors().color, CardDefaults.CornerRadius)
                ) {
                    Text(
                        text = ImportStr.identifiedCoursesN(courses.size),
                        style = NexioTheme.textStyles.title2,
                        color = NexioTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 12.dp)
                    )
                    if (mergedDuplicates > 0) {
                        Text(
                            text = ImportStr.aiEduPreviewMergedN(mergedDuplicates),
                            style = NexioTheme.textStyles.body2,
                            color = NexioTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp).padding(bottom = 8.dp)
                        )
                    }
                    if (skippedNames.isNotEmpty()) {
                        Text(
                            text = ImportStr.aiEduPreviewSkippedN(skippedNames.size),
                            style = NexioTheme.textStyles.body2,
                            color = NexioTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp).padding(bottom = 8.dp)
                        )
                    }
                    courses.forEachIndexed { index, course ->
                        CoursePreviewRow(
                            course = course,
                            onClick = {
                                editingCourseIndex = index
                                editingCourseSnapshot = course
                                showEditDialog = true
                            }
                        )
                        if (index < courses.lastIndex) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                    CardExpand(visible = courses.isNotEmpty()) {
                        FlatTextButton(
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
            totalSections = maxSection,
            defaultStartSection = snapshot.startSection,
            defaultEndSection = snapshot.endSection,
            getOccupiedWeeks = { _, _, _, _, _, _ -> emptySet() },
            onDismiss = { showEditDialog = false },
            onConfirm = { updated ->
                if (editingCourseIndex in courses.indices) {
                    courses[editingCourseIndex] = updated
                }
            },
            onDelete = { _ -> pendingDelete = true }
        )
    }

    OverlayDialog(
        title = ImportStr.aiEduImportDialogTitle(),
        summary = targetSchedule?.let {
            ImportStr.aiEduImportTargetSummary(it, targetExistingCount, courses.size)
        },
        show = showConfirmDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showConfirmDialog = false }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 学校名称：脚本按它归类上传，留空则不允许导入
            TextField(
                value = schoolInput,
                onValueChange = { if (it.length <= AiEduScriptUploader.MAX_SCHOOL_LENGTH) schoolInput = it },
                minLines = 1,
                maxLines = 1,
                label = ImportStr.aiEduSchoolLabel(),
                useLabelAsPlaceholder = true,
            )
            Text(
                text = ImportStr.aiEduSchoolHint(),
                style = NexioTheme.textStyles.body2,
                color = NexioTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
            )
            // 目标课表：整表替换，默认当前课表
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(
                    color = if (isAppDarkTheme()) Color.White.copy(0.1f) else Color.Black.copy(0.06f)
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OverlayDropdownMenu(
                        title = ImportStr.aiEduTargetSchedule(),
                        entry = DropdownEntry(
                            items = scheduleNames.map { name ->
                                DropdownItem(
                                    text = name,
                                    selected = name == targetSchedule,
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        pickedSchedule = name
                                    },
                                )
                            },
                        ),
                        collapseOnSelection = true,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        dropdownColors = liquidGlassDropdownColors,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlatTextButton(
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showConfirmDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                FlatTextButton(
                    text = ImportStr.confirmImport(),
                    enabled = schoolInput.isNotBlank() && targetSchedule != null,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        val target = draft
                        val targetName = targetSchedule
                        if (target != null && targetName != null && courses.isNotEmpty()) {
                            val written = applyAgentDraft(
                                draft = target,
                                courses = courses.toList(),
                                targetSchedule = targetName,
                                settingsViewModel = settingsViewModel,
                                scheduleViewModel = scheduleViewModel,
                                courseViewModel = courseViewModel,
                            )
                            // 脚本上传走后台：页面马上出栈，不能被自己的 scope 取消
                            AiEduScriptUploader.uploadInBackground(
                                school = schoolInput,
                                pageUrl = target.pageUrl,
                                script = target.script,
                                courseCount = written,
                                sessionId = target.sessionId,
                            )
                            showToast(ImportStr.importedCoursesN(written), longDuration = true)
                            showConfirmDialog = false
                            onImported()
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
                    style = NexioTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = NexioTheme.colorScheme.onSurface
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
                    style = NexioTheme.textStyles.body2,
                    color = NexioTheme.colorScheme.onSurfaceVariantActions
                )
                Text(
                    text = weekText,
                    style = NexioTheme.textStyles.body2,
                    color = NexioTheme.colorScheme.onSurfaceVariantActions
                )
            }
            Icon(
                imageVector = NexioIcons.Basic.ArrowRight,
                contentDescription = CommonStr.edit(),
                modifier = Modifier.size(16.dp),
                tint = NexioTheme.colorScheme.onSurfaceVariantActions
            )
        }
    }
}

/**
 * 把草稿落地。
 *
 * 配置 / 作息 / 组合方案一律写进 `edu_import_prefs` 再交给既有的 `apply*` 消费端 ——
 * 那三条链路（节次时间、开学日与总周数、组合作息）已经在教务网页导入里跑了几百次，
 * 这里再写一遍必然漂移。
 */
private fun applyAgentDraft(
    draft: EduAgentDraft,
    courses: List<Course>,
    targetSchedule: String,
    settingsViewModel: SettingsViewModel,
    scheduleViewModel: ScheduleViewModel,
    courseViewModel: CourseViewModel,
): Int {
    val bridge = EduImportBridgeCore(onCoursesImported = {}, onTaskCompleted = {})
    bridge.setImportTableId(null)
    draft.configJson?.let { json ->
        runCatching { bridge.handle("saveCourseConfig", listOf(json)) }
    }
    draft.timeSlotsJson?.let { json ->
        runCatching { bridge.handle("savePresetTimeSlots", listOf(json)) }
    }
    draft.comboJson?.let { json ->
        runCatching { bridge.handle("saveComboSchedule", listOf(json)) }
    }

    if (draft.timeSlotsJson != null) {
        applyPresetTimeSlots(settingsViewModel, scheduleViewModel, null)
    }
    applyImportedScheduleConfig(courseViewModel, scheduleViewModel)
    if (draft.comboJson != null) {
        applyComboSchedule(settingsViewModel, courseViewModel, scheduleViewModel, null)
    }

    // 写入选定的课表：目标就是当前表时走 CourseViewModel（顺带更新缓存与 widget），
    // 否则临时切表写入再切回，对外表现为不切换
    if (targetSchedule == scheduleViewModel.currentScheduleName.value) {
        courseViewModel.replaceCourses(courses)
    } else {
        scheduleViewModel.saveCoursesToSchedule(targetSchedule, courses)
    }
    scheduleViewModel.refreshScheduleList()
    return courses.size
}
