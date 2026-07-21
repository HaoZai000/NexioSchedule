/** AI 文本导入页面 - Screen */
package com.haooz.chedule.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.screens.AddCourseDialog
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AiImportScreen(
    onBack: () -> Unit,
    viewModel: CourseViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scrollBehavior = MiuixScrollBehavior()

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
    var editingCourseIndex by remember { mutableIntStateOf(-1) }
    // 标记删除操作：真正的删除延迟到退出动画结束后执行，避免列表缩短导致组件被提前移除
    var pendingDelete by remember { mutableStateOf(false) }

    // 可编辑的解析结果：当 inputText 变化时重新解析覆盖；用户在预览中修改时直接改此列表
    val parsedCourses = remember { mutableStateListOf<Course>() }
    val parsedWarnings = remember { mutableStateListOf<String>() }
    // 节次配置（可编辑）：AI 输出节次配置时解析得到，用户可在预览中修改
    var parsedSectionConfig by remember { mutableStateOf<SectionConfig?>(null) }

    LaunchedEffect(inputText, maxSection) {
        if (inputText.isBlank()) {
            parsedCourses.clear()
            parsedWarnings.clear()
            parsedSectionConfig = null
        } else {
            val config = parseSectionConfig(inputText)
            parsedSectionConfig = config
            // 如果解析到了节次配置，使用配置的总节数作为上限；否则用当前设置的上限
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

    // 编辑课程时使用的节次上限：优先使用解析到的节次配置
    val effectiveMaxSection = parsedSectionConfig?.let {
        (it.morningCount + it.afternoonCount + it.eveningCount).coerceAtLeast(12)
    } ?: maxSection

    val promptText = remember {
        """请根据课表信息按以下格式返回，包含节次配置和课程列表两部分：

第一部分 - 节次配置（放在课程列表之前，时间行可选，无时间则不输出）：
上午节数=N
下午节数=N
晚上节数=N
上午时间=1:08:00-08:45,2:08:55-09:40
下午时间=1:14:00-14:45,2:14:55-15:40
晚上时间=1:19:00-19:45,2:19:55-20:40

第二部分 - 课程列表（每行一门课程，用"|"分隔）：
课程名称|教室|教师|星期几|开始节次|结束节次|周次

字段说明：
- 星期几：1=周一 ... 7=周日
- 周次：英文逗号分隔，连续周次展开，例：1-6周和10-12周写为 1,2,3,4,5,6,10,11,12
- 教室和教师无法识别则留空

重要：同名但教师或教室不同的课程必须分行输出，只有名称、教室、教师、星期、节次完全相同时才可合并周次。

示例：
上午节数=4
下午节数=4
晚上节数=2
上午时间=1:08:00-08:45,2:08:55-09:40,3:10:00-10:45,4:10:55-11:40
下午时间=1:14:00-14:45,2:14:55-15:40,3:16:00-16:45,4:16:55-17:40
晚上时间=1:19:00-19:45,2:19:55-20:40
高等数学|A101|张三|1|1|2|1,2,3,4,5,6,7,8,9,10
高等数学|B202|李四|3|3|4|1,2,3,4,5,6,7,8,9,10
大学英语|||3|3|4|1,2,3,4,5,6,7,8

请严格按此格式返回，不要添加其他说明文字。"""
    }

    val isLiquidGlass = liquidGlassBackdrop != null

    Scaffold(
        topBar = {
            if (!isLiquidGlass) {
                TopAppBar(
                    title = "AI 文本导入",
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
                .then(
                    if (!isLiquidGlass) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
                ),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = if (isLiquidGlass) paddingValues.calculateTopPadding() + 56.dp else paddingValues.calculateTopPadding(),
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SmallTitle(
                    text = "课表数据",
                    modifier = Modifier.offset(x = (-16).dp)
                )
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    cornerRadius = 20.dp,
                    minLines = 1,
                    maxLines = 12,
                    label = "请粘贴AI返回的课表数据",
                    useLabelAsPlaceholder = true
                )
            }

            // 解析预览：标题 + 预览卡片 + 警告详情，整体动画
            item {
                val previewVisible = parsedCourses.isNotEmpty() || parsedWarnings.isNotEmpty() || parsedSectionConfig != null
                val previewScale = remember { Animatable(0.8f) }
                val previewAlpha = remember { Animatable(0f) }
                LaunchedEffect(previewVisible) {
                    if (previewVisible) {
                        launch { previewScale.animateTo(1f, animationSpec = tween(400)) }
                        launch { previewAlpha.animateTo(1f, animationSpec = tween(400)) }
                    } else {
                        launch { previewScale.animateTo(0.8f, animationSpec = tween(300)) }
                        launch { previewAlpha.animateTo(0f, animationSpec = tween(300)) }
                    }
                }
                AnimatedVisibility(
                    visible = previewVisible,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(250)),
                    exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(200))
                ) {
                    Column(
                        modifier = Modifier.graphicsLayer {
                            scaleX = previewScale.value
                            scaleY = previewScale.value
                            alpha = previewAlpha.value
                        }
                    ) {
                        SmallTitle(
                            text = "解析预览",
                            modifier = Modifier.offset(x = (-15).dp)
                        )
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "已识别 ${parsedCourses.size} 门课程",
                                    style = MiuixTheme.textStyles.title2,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 12.dp)
                                )
                                parsedSectionConfig?.let { config ->
                                    Text(
                                        text = "已识别节次配置：上午${config.morningCount}节 · 下午${config.afternoonCount}节 · 晚上${config.eveningCount}节" +
                                            if (config.morningTimes.isNotEmpty() || config.afternoonTimes.isNotEmpty() || config.eveningTimes.isNotEmpty())
                                                "（含上课时间，导入时自动应用）" else "（导入时自动应用）",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                    )
                                }
                                AnimatedVisibility(
                                    visible = parsedWarnings.isNotEmpty(),
                                    enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(150)),
                                    exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(100))
                                ) {
                                    Text(
                                        text = "提示 ${parsedWarnings.size} 条",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                    )
                                }
                                parsedCourses.forEachIndexed { index, course ->
                                    CoursePreviewRow(
                                        course = course,
                                        onClick = { editingCourseIndex = index }
                                    )
                                    if (index < parsedCourses.lastIndex) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                                if (parsedCourses.isNotEmpty()) {
                                    TextButton(
                                        text = "确定导入",
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
                        // 警告详情卡片
                        AnimatedVisibility(
                            visible = parsedWarnings.isNotEmpty(),
                            enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    cornerRadius = 20.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                    insideMargin = PaddingValues(0.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "提示详情",
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
                    }
                }
            }

            item {
                SmallTitle(
                    text = "使用说明",
                    modifier = Modifier.offset(x = (-15).dp)
                )
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "请将以下提示词发送给 AI ，再将 AI 返回的文本粘贴到上方输入框。",
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
                            text = "复制提示词",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("课表导入提示词", promptText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "提示词已复制到剪切板", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // 课程编辑弹窗：使用 AddCourseDialog 统一组件
    if (editingCourseIndex >= 0 && editingCourseIndex in parsedCourses.indices) {
        val editingCourse = parsedCourses[editingCourseIndex]
        AddCourseDialog(
            course = editingCourse,
            selectedDay = editingCourse.dayOfWeek,
            totalWeeks = totalWeeks,
            totalSections = effectiveMaxSection,
            defaultStartSection = editingCourse.startSection,
            defaultEndSection = editingCourse.endSection,
            getOccupiedWeeks = { _, _, _ -> emptySet() },
            onDismiss = {
                // 退出动画结束后才执行真正的删除，避免列表缩短导致组件被提前移除
                if (pendingDelete && editingCourseIndex in parsedCourses.indices) {
                    parsedCourses.removeAt(editingCourseIndex)
                }
                pendingDelete = false
                editingCourseIndex = -1
            },
            onConfirm = { updated ->
                if (editingCourseIndex in parsedCourses.indices) {
                    parsedCourses[editingCourseIndex] = updated
                }
            },
            onDelete = { _ ->
                pendingDelete = true
            },
            liquidGlassBackdrop = liquidGlassBackdrop
        )
    }

    // 导入确认弹窗
    OverlayDialog(
        title = "导入并覆盖当前课表？",
        summary = buildString {
            if (existingCourses.isNotEmpty()) {
                append("当前课表 ${existingCourses.size} 门 → 新课表 ${parsedCourses.size} 门")
            } else {
                append("共 ${parsedCourses.size} 门课程")
            }
            parsedSectionConfig?.let { cfg ->
                append("\n将同时应用节次配置：\n上午${cfg.morningCount}节 · 下午${cfg.afternoonCount}节 · 晚上${cfg.eveningCount}节")
            }
        },
        show = showConfirmDialog,

        onDismissRequest = { showConfirmDialog = false }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                        showConfirmDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确认导入",
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
                            }
                            viewModel.replaceCourses(parsedCourses.toList())
                            Toast.makeText(
                                context,
                                "成功导入 ${parsedCourses.size} 门课程",
                                Toast.LENGTH_LONG
                            ).show()
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

/**
 * 课程预览行：显示课程概要，点击进入编辑弹窗
 */
@Composable
private fun CoursePreviewRow(
    course: Course,
    onClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val dayText = remember(course.dayOfWeek) {
        when (course.dayOfWeek) {
            1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
            5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> "?"
        }
    }
    // 连续周次会被 AddCourseDialog 存为空列表（用 startWeek/endWeek 表示），
    // 因此使用 getWeekText() 综合判断，避免 selectedWeeks 为空时不显示
    val weekText = remember(course.selectedWeeks, course.startWeek, course.endWeek, course.weekType) {
        course.getWeekText()
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
                    .background(Color(course.colorRes), RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                // 第一行：周几｜节次｜地点
                val summaryLine1 = buildString {
                    append(dayText)
                    append("｜第${course.startSection}-${course.endSection}节")
                    if (course.classroom.isNotBlank() && course.classroom != "未指定教室") {
                        append("｜${course.classroom}")
                    }
                }
                Text(
                    text = summaryLine1,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                // 第二行：周次
                Text(
                    text = weekText,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = "编辑",
                modifier = Modifier.size(16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
        }
    }
}

/**
 * 节次配置数据：上午/下午/晚上的节数与每节起止时间
 * 时间格式: Map<节次编号, "HH:mm-HH:mm">，节次编号为各时段内的相对编号(1,2,3...)
 */
data class SectionConfig(
    val morningCount: Int,
    val afternoonCount: Int,
    val eveningCount: Int,
    val morningTimes: Map<Int, String>,
    val afternoonTimes: Map<Int, String>,
    val eveningTimes: Map<Int, String>
)

/**
 * 节次配置行的前缀，用于从文本中识别并跳过这些行
 */
private val SECTION_CONFIG_PREFIXES = listOf(
    "上午节数", "下午节数", "晚上节数", "上午时间", "下午时间", "晚上时间"
)

/**
 * 判断某行是否为节次配置行（以已知前缀开头且包含=）
 */
private fun isSectionConfigLine(line: String): Boolean {
    val trimmed = line.trim()
    return SECTION_CONFIG_PREFIXES.any { trimmed.startsWith(it) } && trimmed.contains("=")
}

/**
 * 从AI返回的文本中解析节次配置
 * 支持格式：
 *   上午节数=4
 *   下午节数=4
 *   晚上节数=2
 *   上午时间=1:08:00-08:45,2:08:55-09:40
 *   下午时间=1:14:00-14:45,...
 *   晚上时间=1:19:00-19:45,...
 *
 * @return SectionConfig? 未识别到任何配置行时返回 null
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
            "上午节数" -> morningCount = value.toIntOrNull()
            "下午节数" -> afternoonCount = value.toIntOrNull()
            "晚上节数" -> eveningCount = value.toIntOrNull()
            "上午时间" -> morningTimes = parseTimeMap(value)
            "下午时间" -> afternoonTimes = parseTimeMap(value)
            "晚上时间" -> eveningTimes = parseTimeMap(value)
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
 * 解析时间映射字符串
 * 格式: "1:08:00-08:45,2:08:55-09:40" → {1: "08:00-08:45", 2: "08:55-09:40"}
 * 注意: 时间字符串本身包含冒号，因此按第一个冒号分割节次编号与时间
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
 * 解析导入的课程文本数据
 * 格式: 课程名称|教室|教师|星期几|开始节次|结束节次|周次
 * 周次格式: 1-6,10-12 或 1,3,5 或 1-16
 * 部分字段可留空，使用默认值
 * 同名同教室同节次的课程自动合并周次
 * 节次配置行（上午节数=、下午时间=等）会被自动跳过
 *
 * @param maxSection 节次上限，超出则视为无效（由调用方根据用户设置传入）
 */
internal fun parseImportedCourses(text: String, maxSection: Int = 12): Pair<List<Course>, List<String>> {
    val warnings = mutableListOf<String>()
    val lines = text.trim().lines().filter { it.isNotBlank() && !isSectionConfigLine(it) }

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
            if (startSection == null || startSection !in 1..maxSection) {
                warnings.add("第${index + 1}行「${name}」: 开始节次无效（应为 1-$maxSection），已跳过")
                continue
            }
            if (endSection == null || endSection !in 1..maxSection || endSection < startSection) {
                warnings.add("第${index + 1}行「${name}」: 结束节次无效（应为 ${startSection}-$maxSection），已跳过")
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
 * 兼容全角逗号"，"和顿号"、"
 */
internal fun parseWeekString(weekStr: String): List<Int> {
    val weeks = mutableListOf<Int>()
    // 兼容全角逗号和顿号
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
