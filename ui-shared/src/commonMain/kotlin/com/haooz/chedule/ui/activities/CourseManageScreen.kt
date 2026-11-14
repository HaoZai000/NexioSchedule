/** 课程管理页面 - Screen */
package com.haooz.chedule.ui.activities

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.randomUuidV4
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SwitchStr
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.theme.rememberCourseColors
import com.haooz.chedule.ui.utils.CourseSorting
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import com.haooz.softglass.shape.continuousBorder
import com.haooz.softglass.shape.continuousClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun CourseManageScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    viewModel: CourseViewModel = courseViewModel(),
    shrinkingCourseIds: Set<String> = emptySet(),
    onCourseClick: (
        courses: List<Course>,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        snapshot: TransitionLayerSnapshot?,
        cardColor: Color,
        cardAlpha: Float
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onNewCourseCreated: (Course) -> Unit = {},
    onCourseUpdated: (oldName: String, updated: Course) -> Unit = { _, _ -> },
    onEditDismiss: () -> Unit = {},
    pendingEditCourse: Course? = null,
    onCourseLongPress: (courses: List<Course>, left: Float, top: Float, width: Float, height: Float) -> Unit = { _, _, _, _, _ -> },
    onCourseLongPressLayer: ((List<Course>, Float, Float, Float, Float, TransitionLayerSnapshot) -> Unit)? = null,
    onDeleteCourses: (List<Course>) -> Unit = {},
    deleteConfirmShow: Boolean = false,
    deleteConfirmCourses: List<Course> = emptyList(),
    onDeleteConfirmDismiss: () -> Unit = {},
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    /** 内嵌（平板左栏）时由外部指定内容顶距，替代按整屏状态栏+折叠标题推算 */
    contentTopPadding: androidx.compose.ui.unit.Dp? = null,
    /** 内嵌时指定列数，替代平板固定 4 列 */
    columnsOverride: Int? = null,
    /** 列表纵向滚动量回调，供内嵌顶栏遮罩使用 */
    onScrollYChanged: (Int) -> Unit = {},
    /** 选中课程名：内嵌分栏时给对应卡片加一圈课程色描边 */
    selectedCourseName: String? = null,
    /** 全局绝对节次号 -> "HH:mm-HH:mm"，用于把同一天内的时段按真实时刻排序 */
    sectionTimes: Map<Int, String> = emptyMap(),
    onDialogVisibilityChanged: (Boolean) -> Unit = {},
) {
    val hapticFeedback = LocalHapticFeedback.current
    val courses by viewModel.courses.collectAsState()
    val graphicsContext = LocalGraphicsContext.current
    val cardLayers = remember(graphicsContext) { mutableMapOf<String, GraphicsLayer>() }
    DisposableEffect(graphicsContext) {
        onDispose { cardLayers.values.forEach(graphicsContext::releaseGraphicsLayer) }
    }
    var listScrollY by remember { mutableIntStateOf(0) }

    val isDark = isAppDarkTheme()
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    val dayNames = listOf(
        "",
        CommonStr.weekdayMon(),
        CommonStr.weekdayTue(),
        CommonStr.weekdayWed(),
        CommonStr.weekdayThu(),
        CommonStr.weekdayFri(),
        CommonStr.weekdaySat(),
        CommonStr.weekdaySun(),
    )

    // 新建课程弹窗状态
    var showNewCourseDialog by remember { mutableStateOf(false) }
    var newCourseName by remember { mutableStateOf("") }
    var newCourseColor by remember { mutableLongStateOf(Course.courseColors.first()) }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(ComposeColor(Course.courseColors.first())) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }

    val currentOnDialogVisibilityChanged by rememberUpdatedState(onDialogVisibilityChanged)
    LaunchedEffect(showNewCourseDialog, showCustomColorDialog) {
        currentOnDialogVisibilityChanged(showNewCourseDialog || showCustomColorDialog)
    }

    // 接收外部传入的编辑课程
    LaunchedEffect(pendingEditCourse) {
        if (pendingEditCourse != null) {
            editingCourse = pendingEditCourse
            newCourseName = pendingEditCourse.name
            newCourseColor = pendingEditCourse.colorRes
            showNewCourseDialog = true
        }
    }

    // 新课程入场动画跟踪
    var newlyAddedCourseNames by remember { mutableStateOf(setOf<String>()) }
    var pendingNewCourse by remember { mutableStateOf<Course?>(null) }

    // dialog 关闭后延迟写入数据库并触发动画
    LaunchedEffect(showNewCourseDialog) {
        if (!showNewCourseDialog && pendingNewCourse != null) {
            delay(200.milliseconds)
            val course = pendingNewCourse!!
            onNewCourseCreated(course)
            newlyAddedCourseNames = newlyAddedCourseNames + course.name
            pendingNewCourse = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {}
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val gridState = rememberLazyStaggeredGridState()
                val currentOnScrollYChanged by rememberUpdatedState(onScrollYChanged)
                LaunchedEffect(gridState) {
                    snapshotFlow { gridState.firstVisibleItemScrollOffset }
                        .collect { offset ->
                            listScrollY = offset
                            currentOnScrollYChanged(offset)
                        }
                }

                // 课程按名称聚合，名称用中文拼音排（原来是 Unicode 码点排，中文完全没有顺序感）；
                // 组内按时段排，保证「代表性课程」（取 first）与卡片上的时段摘要稳定
                val groupedCourses = remember(courses, sectionTimes) {
                    courses.groupBy { it.name }
                        .map { (name, list) ->
                            name to list.sortedWith(
                                compareBy(
                                    { if (CourseSorting.hasScheduledTime(it)) 0 else 1 },
                                    { it.dayOfWeek },
                                    { CourseSorting.startMinutesOf(it, sectionTimes) },
                                    { it.startSection },
                                    { it.endSection },
                                    { it.startWeek },
                                    { it.endWeek }
                                )
                            )
                        }
                        .sortedWith { a, b -> CourseSorting.compareNames(a.first, b.first) }
                }

                val resolvedTopPadding = contentTopPadding
                    ?: (paddingValues.calculateTopPadding() +
                        CollapsibleTopAppBarDefaults.CollapsedHeight +
                        pageTopExtra(isTablet, hasSmallTitle = false))

                if (groupedCourses.isEmpty()) {
                    // 空状态
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = resolvedTopPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = SwitchStr.noCourses(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = SwitchStr.noCoursesHint(),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(columnsOverride ?: if (isTablet) 4 else 2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize()
                            .overScrollVertical()
                            .scrollEndHaptic(
                                hapticFeedbackType = HapticFeedbackType.TextHandleMove
                            )
                            .collapsibleTopInset(scrollBehavior)
                            .then(
                                scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                            ),
                        contentPadding = PaddingValues(
                            start = tabletHorizontalPadding,
                            top = resolvedTopPadding,
                            end = tabletHorizontalPadding,
                            bottom = 60.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 12.dp
                    ) {
                        items(groupedCourses, key = { it.first }) { (courseName, courseList) ->
                            val isNew = courseName in newlyAddedCourseNames
                            val isShrinking = courseList.any { it.id in shrinkingCourseIds }
                            val scale = remember { Animatable(0.8f) }
                            val alpha = remember { Animatable(0f) }
                            LaunchedEffect(courseName, isShrinking) {
                                if (isNew) {
                                    launch { scale.animateTo(1f, tween(350)) }
                                    launch { alpha.animateTo(1f, tween(300)) }
                                    newlyAddedCourseNames = newlyAddedCourseNames - courseName
                                } else if (isShrinking) {
                                    launch { scale.animateTo(0.8f, tween(300)) }
                                    launch { alpha.animateTo(0f, tween(250)) }
                                } else {
                                    scale.snapTo(1f)
                                    alpha.snapTo(1f)
                                }
                            }

                            val representative = courseList.first()
                            // 卡片色与主课表页 CourseCard 同一套算法（rememberCourseColors）：
                            // 莫奈取色下同名课稳定映射到主/次/第三色系，非莫奈时就是课程自定义色。
                            // 不接的话同一门课在课表页和管理页会两个颜色。
                            val courseColors = rememberCourseColors(representative, isDark)
                            // 卡片摘要里的时段：courseList 已排好序，去重后逐条交给卡片自己排成小标签，
                            // 不再拼成一长串顿号连接（原来挤在两行里既读不出条数也读不出边界）。
                            //判定「已排时间」用 hasScheduledTime 而不是 startSection > 0，
                            // 否则自定义时间的课（节次号为 0）会被整条滤掉、摘要变空
                            val daySections = courseList
                                .filter { CourseSorting.hasScheduledTime(it) }
                                .distinctBy { "${it.dayOfWeek}_${it.startSection}_${it.endSection}_${it.customStartTime}_${it.customEndTime}" }
                                .map {
                                    val day = dayNames.getOrElse(it.dayOfWeek) { "?" }
                                    "$day ${it.timeDisplayTextLocalized()}"
                                }

                            val teachers = courseList
                                .map { it.teacher }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString("/")

                            val classrooms = courseList
                                .map { it.classroom }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString("/")

                            Box(
                                modifier = Modifier
                                    .animateItem()
                                    .graphicsLayer {
                                        scaleX = scale.value
                                        scaleY = scale.value
                                        this.alpha = alpha.value
                                    }
                            ) {
                                CourseManageCard(
                                    cardLayer = cardLayers.getOrPut(courseName) { graphicsContext.createGraphicsLayer() },
                                    courseName = courseName,
                                    teacher = teachers,
                                    classroom = classrooms,
                                    color = courseColors.tint,
                                    daySections = daySections,
                                    sectionCount = courseList.size,
                                    isSelected = courseName == selectedCourseName,
                                    onClick = { left, top, width, height, snapshot ->
                                        onCourseClick(courseList, left, top, width, height, snapshot, courseColors.tint, 0.12f)
                                    },
                                    onLongPress = { left, top, width, height, snapshot ->
                                        onCourseLongPressLayer?.invoke(courseList, left, top, width, height, snapshot)
                                            ?: onCourseLongPress(courseList, left, top, width, height)
                                    }
                                )
                            }
                        }

                        items(1) {
                            Box(
                                modifier = Modifier.animateItem()
                            ) {
                                NewCourseCard(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        newCourseName = ""
                                        newCourseColor = Course.courseColors.first()
                                        showNewCourseDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 新建/编辑课程弹窗
    OverlayDialog(
        title = if (editingCourse != null) SwitchStr.editCourse() else SwitchStr.newCourse(),
        show = showNewCourseDialog,
        onDismissRequest = {
            showNewCourseDialog = false
            editingCourse = null
            newCourseName = ""
            newCourseColor = Course.courseColors.first()
            onEditDismiss()
        },
        liquidGlassBackdrop = liquidGlassBackdrop
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 课程名称输入
            NativeMiuixTextField(
                value = newCourseName,
                onValueChange = { newCourseName = it },
                label = SwitchStr.courseNameLabel(),

                requestFocus = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 课程颜色选择
            Column(modifier = Modifier.fillMaxWidth()) {
                val allColors = remember { Course.courseColors }
                val colorColumns = 6
                val totalItems = remember(allColors) { allColors.size + 1 }
                val colorRows = remember(totalItems, colorColumns) { (totalItems + colorColumns - 1) / colorColumns }
                Text(
                    text = SwitchStr.courseColor(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )
                for (row in 0 until colorRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (col in 0 until colorColumns) {
                            val colorIndex = row * colorColumns + col
                            if (colorIndex < allColors.size) {
                                val color = allColors[colorIndex]
                                val isSelected = color == newCourseColor
                                val primaryColor = MiuixTheme.colorScheme.primary
                                val borderAlpha by animateFloatAsState(
                                    targetValue = if (isSelected) 1f else 0f,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "borderAlpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .pointerInput(Unit) {
                                            detectTapGestures { newCourseColor = color }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // 选中态：沿外圈绘制主题色描边，描边内侧留空，内部填课程色（保留原 alpha）
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .continuousBorder(
                                                width = 2.dp,
                                                color = primaryColor.copy(alpha = borderAlpha),
                                                cornerRadius = 12.dp
                                            )
                                            .padding(4.dp)
                                            .continuousClip(8.dp)
                                            .background(Color(color).copy(alpha = if (isDark) 0.22f else 0.16f))
                                    )
                                }
                            } else if (colorIndex == allColors.size) {
                                // 自定义颜色按钮
                                val isCustomColor = newCourseColor !in allColors
                                val hintColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                val primaryColor = MiuixTheme.colorScheme.primary
                                val customBorderAlpha by animateFloatAsState(
                                    targetValue = if (isCustomColor) 1f else 0f,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "customBorderAlpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .pointerInput(Unit) {
                                            detectTapGestures {
                                                customColor = Color(newCourseColor)
                                                showCustomColorDialog = true
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // 选中态：沿外圈绘制主题色描边，描边内侧留空
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .continuousBorder(
                                                width = 2.dp,
                                                color = primaryColor.copy(alpha = customBorderAlpha),
                                                cornerRadius = 12.dp
                                            )
                                            .padding(4.dp)
                                            .continuousClip(8.dp)
                                            .background(
                                                if (isCustomColor) Color(newCourseColor).copy(alpha = if (isDark) 0.22f else 0.16f)
                                                else if (isDark) ComposeColor(0xFF424242) else ComposeColor(0xFFF0F0F0)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!isCustomColor) {
                                            Icon(
                                                imageVector = MiuixIcons.Add,
                                                contentDescription = SwitchStr.customColor(),
                                                modifier = Modifier.size(18.dp),
                                                tint = hintColor
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 确认和取消按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showNewCourseDialog = false
                        editingCourse = null
                        newCourseName = ""
                        newCourseColor = Course.courseColors.first()
                        onEditDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = CommonStr.ok(),
                    enabled = newCourseName.isNotBlank(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (newCourseName.isNotBlank()) {
                            if (editingCourse != null) {
                                val oldName = editingCourse!!.name
                                val newName = newCourseName.trim()
                                val updated = editingCourse!!.copy(
                                    name = newName,
                                    colorRes = newCourseColor
                                )
                                onCourseUpdated(oldName, updated)
                                showNewCourseDialog = false
                                editingCourse = null
                                newCourseName = ""
                                newCourseColor = Course.courseColors.first()
                                onEditDismiss()
                            } else {
                                if (courses.any { it.name == newCourseName.trim() }) {
                                    showToast(SwitchStr.duplicateCourse())
                                } else {
                                    val course = Course(
                                        id = randomUuidV4(),
                                        name = newCourseName.trim(),
                                        classroom = "",
                                        teacher = "",
                                        dayOfWeek = 0,
                                        startSection = 0,
                                        endSection = 0,
                                        startWeek = 0,
                                        endWeek = 0,
                                        weekType = Course.WEEK_TYPE_ALL,
                                        colorRes = newCourseColor
                                    )
                                    pendingNewCourse = course
                                    showNewCourseDialog = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 自定义颜色选择弹窗
    OverlayDialog(
        title = SwitchStr.selectColor(),
        show = showCustomColorDialog,
        onDismissRequest = { showCustomColorDialog = false },
        liquidGlassBackdrop = liquidGlassBackdrop
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ColorPalette(
                color = customColor,
                onColorChanged = { customColor = it },
                cornerRadius = 20.dp,
                indicatorRadius = 12.dp
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
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showCustomColorDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = CommonStr.ok(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        newCourseColor = (customColor.alpha * 255).toInt().toLong() shl 24 or
                                ((customColor.red * 255).toInt().toLong() shl 16) or
                                ((customColor.green * 255).toInt().toLong() shl 8) or
                                (customColor.blue * 255).toInt().toLong()
                        showCustomColorDialog = false
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 删除确认弹窗
    if (deleteConfirmCourses.isNotEmpty()) {
        val deleteCourseName = deleteConfirmCourses.firstOrNull()?.name ?: ""
        val deleteSummary = if (deleteConfirmCourses.size > 1) {
            SwitchStr.deleteCourseRecordsConfirm(deleteCourseName, deleteConfirmCourses.size)
        } else {
            SwitchStr.deleteCourseConfirm(deleteCourseName)
        }
        OverlayDialog(
            title = SwitchStr.deleteCourse(),
            summary = deleteSummary,
            show = deleteConfirmShow,
            onDismissRequest = onDeleteConfirmDismiss,
            liquidGlassBackdrop = liquidGlassBackdrop
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
                        onDeleteConfirmDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = CommonStr.delete(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDeleteConfirmDismiss()
                        onDeleteCourses(deleteConfirmCourses)
                    },
                    textColor = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 卡片排版参数：原来正文 11~12sp 太细、且没有显式 lineHeight（中文字体默认 leading 偏大），
 * 再叠上散落的 8/4/2/4dp Spacer，观感就是「字小、行松、全是纯文本」。
 * 现在统一：标题 16/22sp，正文 13/18sp，标签 11.5/15sp，组间距 10/4/8dp 三档。
 */
private val MANAGE_CARD_TITLE_SIZE = 16.sp
private val MANAGE_CARD_TITLE_LINE = 22.sp
private val MANAGE_CARD_BODY_SIZE = 13.sp
private val MANAGE_CARD_BODY_LINE = 18.sp
private val MANAGE_CARD_CHIP_SIZE = 11.5.sp
private val MANAGE_CARD_CHIP_LINE = 15.sp
/** 时段标签最多铺 4 枚，再多折叠成「+N」 */
private const val MANAGE_CARD_MAX_CHIPS = 4
/**
 * 卡片内所有「课程色淡底」标记（时段标签、标题行「N 节」徽标）共用的一份 alpha。
 * 两处原来是各自写的常量，暗色下标签 0.24 / 徽标 0.28，看起来不是一个体系 —— 统一成一份。
 */
private val MANAGE_CARD_TINT_LIGHT = 0.15f
private val MANAGE_CARD_TINT_DARK = 0.18f

/** 课程色淡底：亮/暗各一份 alpha，卡上标记统一取这里 */
@Composable
private fun courseTintBase(color: Color): Color =
    color.copy(alpha = if (isAppDarkTheme()) MANAGE_CARD_TINT_DARK else MANAGE_CARD_TINT_LIGHT)

/** 教师 / 教室这类元信息行：小图标给出语义锚点，避免三行纯文本读起来是一片。 */
@Composable
private fun CourseMetaRow(
    icon: ImageVector,
    text: String,
    iconTint: Color,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(top = 2.dp).size(13.dp),
            tint = iconTint
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = MANAGE_CARD_BODY_SIZE,
            lineHeight = MANAGE_CARD_BODY_LINE,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.72f)
        )
    }
}

/** 单个时段标签：原来一长串顿号连接改成可数的小块，一眼能看出有几段。 */
@Composable
private fun SectionChip(
    text: String,
    color: Color,
) {
    Box(
        modifier = Modifier
            .continuousClip(6.dp)
            .background(courseTintBase(color))
            .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = MANAGE_CARD_CHIP_SIZE,
            lineHeight = MANAGE_CARD_CHIP_LINE,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseManageCard(
    cardLayer: GraphicsLayer,
    courseName: String,
    teacher: String,
    classroom: String,
    color: Color,
    cardAlpha: Float = 0.12f,
    daySections: List<String>,
    /** 同名课程聚合出的记录条数，>1 时在标题行末尾给一枚「N 节」标记 */
    sectionCount: Int = 1,
    isSelected: Boolean = false,
    onClick: (left: Float, top: Float, width: Float, height: Float, snapshot: TransitionLayerSnapshot?) -> Unit,
    onLongPress: (left: Float, top: Float, width: Float, height: Float, snapshot: TransitionLayerSnapshot) -> Unit = { _, _, _, _, _ -> }
) {
    var cardLeft by remember { mutableFloatStateOf(0f) }
    var cardTop by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableFloatStateOf(0f) }
    var cardHeight by remember { mutableFloatStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val cardBackground = MiuixTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val position = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                val size = coordinates.size
                cardLeft = position.x
                cardTop = position.y
                cardWidth = size.width.toFloat()
                cardHeight = size.height.toFloat()
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onClick(cardLeft, cardTop, cardWidth, cardHeight, TransitionLayerSnapshot(cardLayer))
                },
                onLongClick = {
                    onLongPress(cardLeft, cardTop, cardWidth, cardHeight, TransitionLayerSnapshot(cardLayer))
                }
            )
            .drawWithContent {
                cardLayer.record {
                    drawRect(cardBackground)
                    this@drawWithContent.drawContent()
                }
                drawLayer(cardLayer)
            }
    ) {
        Card(
            cornerRadius = 16.dp,
            showIndication = true,
            // 14dp：手机两列时卡内可用宽度只有 ~126dp，16dp 会把正文挤得更窄
            insideMargin = PaddingValues(14.dp),
            colors = CardDefaults.defaultColors(
                color = color.copy(alpha = cardAlpha)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
        Column {
            // 标题行：课程色竖条 + 课程名（原来 8dp 色块独占一行，白占 16dp 高度）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 16.dp)
                        .clip(ContinuousRoundedRectangle(1.5.dp))
                        .background(color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = courseName,
                    fontSize = MANAGE_CARD_TITLE_SIZE,
                    lineHeight = MANAGE_CARD_TITLE_LINE,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (sectionCount > 1) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .continuousClip(5.dp)
                            .background(courseTintBase(color))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = SwitchStr.sectionCountN(sectionCount),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            val hasMeta = teacher.isNotBlank() || classroom.isNotBlank()
            if (hasMeta) Spacer(modifier = Modifier.height(10.dp))
            if (teacher.isNotBlank()) {
                CourseMetaRow(
                    icon = MiuixIcons.Contacts,
                    text = teacher,
                    iconTint = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                )
            }
            if (teacher.isNotBlank() && classroom.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (classroom.isNotBlank()) {
                CourseMetaRow(
                    icon = MiuixIcons.Location,
                    text = classroom,
                    iconTint = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                )
            }
            if (hasMeta) Spacer(modifier = Modifier.height(8.dp))

            if (daySections.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 最多 4 枚，超出的折叠成 +N，避免一个课占满整屏高度
                    val visible = daySections.take(MANAGE_CARD_MAX_CHIPS)
                    visible.forEach { SectionChip(text = it, color = color) }
                    if (daySections.size > visible.size) {
                        SectionChip(text = "+${daySections.size - visible.size}", color = color)
                    }
                }
            } else {
                Text(
                    text = SwitchStr.tapToSetTime(),
                    fontSize = MANAGE_CARD_BODY_SIZE,
                    lineHeight = MANAGE_CARD_BODY_LINE,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
    }
    // 选中描边：matchParentSize 覆盖层，不参与测量，避免卡片尺寸变化导致列表重排
    if (isSelected) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .continuousBorder(width = 2.dp, color = color, cornerRadius = 16.dp)
        )
    }
}
}

@Composable
private fun NewCourseCard(
    onClick: () -> Unit
) {
    Card(
        cornerRadius = 16.dp,
        showIndication = true,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(
            // 新建卡底色：莫奈取色下跟随 surfaceContainer（与课表页待放格、教务导入胶囊同一套）；
            // 非莫奈保持原硬编码（亮 #EEEEEE / 暗 #121212）
            color = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainer
            else if (isAppDarkTheme()) ComposeColor(0xFF121212) else ComposeColor(0xFFEEEEEE)
        ),
        modifier = Modifier.fillMaxWidth().height(132.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                MiuixIcons.Add,
                contentDescription = SwitchStr.newCourse(),
                modifier = Modifier.size(28.dp),
                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = SwitchStr.newCourse(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
