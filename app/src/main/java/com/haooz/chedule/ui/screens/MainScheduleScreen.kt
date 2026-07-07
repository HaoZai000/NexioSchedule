/** 主课程表页面 - 显示周视图课程表 */
package com.haooz.chedule.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.activities.isAppDarkTheme
import com.haooz.chedule.ui.components.DayColumn
import com.haooz.chedule.ui.components.SectionColumn
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MainScheduleScreen(
    viewModel: CourseViewModel,
    settingsViewModel: SettingsViewModel,
    pagerState: PagerState,
    currentDayOfWeek: Int,
    dayRange: List<Int>,
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: android.graphics.Bitmap?) -> Unit = { _, _, _, _, _, _ -> },
    onPopupStateChange: (Boolean) -> Unit = {},
    onEmptyLongPress: () -> Unit = {},
    wallpaperBitmap: android.graphics.Bitmap? = null,
    wallpaperOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    wallpaperScale: Float = 1f,
    isWallpaperEditing: Boolean = false,
    onWallpaperOffsetChange: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onWallpaperScaleChange: (Float) -> Unit = {},
    cardBlurRadius: Float = 0f,
    cardAlpha: Float = 0.15f,
    cardHeightPerSection: Float = 54f,
    cardCornerRadius: Float = 8f,
    wallpaperHasAppeared: Boolean = false,
    onWallpaperAppeared: () -> Unit = {}
) {
    val courses by viewModel.courses.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val showJumpWeekDialog by viewModel.showJumpWeekDialog.collectAsState()
    val editingCourse by viewModel.editingCourse.collectAsState()
    val showNonCurrentWeek by settingsViewModel.showNonCurrentWeek.collectAsState()
    val morningSections by settingsViewModel.morningSections.collectAsState()
    val afternoonSections by settingsViewModel.afternoonSections.collectAsState()
    val eveningSections by settingsViewModel.eveningSections.collectAsState()
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()
    val selectedStartSection by viewModel.selectedStartSection.collectAsState()
    val selectedEndSection by viewModel.selectedEndSection.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current

    var showCourseDetail by remember { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var selectedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var pendingDay by remember { mutableIntStateOf(-1) }
    var pendingSection by remember { mutableIntStateOf(-1) }
    var viewingWeek by remember { mutableIntStateOf(currentWeek) }
    var jumpWeekTemp by remember { mutableIntStateOf(1) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(showAddDialog) {
        if (showAddDialog && pendingDay != -1) {
            kotlinx.coroutines.delay(300.milliseconds)
            pendingDay = -1
            pendingSection = -1
        }
    }

    val totalSections = morningSections + afternoonSections + eveningSections

    LaunchedEffect(pagerState.currentPage) {
        viewingWeek = pagerState.currentPage + 1
    }

    LaunchedEffect(showJumpWeekDialog) {
        if (showJumpWeekDialog) {
            jumpWeekTemp = pagerState.currentPage + 1
        }
    }

    val context = LocalContext.current
    val activity = context as? ComponentActivity as? com.haooz.chedule.ui.activities.MainActivity

    @Suppress("RedundantInitializer")
    val isInFreeformWindow = activity?.isInFreeformWindow == true

    // 预计算每天的课程，避免在 HorizontalPager 内部重复过滤
    val coursesByDay = remember(courses, dayRange) {
        dayRange.associateWith { dayOfWeek ->
            courses.filter { it.dayOfWeek == dayOfWeek }
                .sortedBy { it.startSection }
        }
    }

    // 预计算每周每天的过滤后课程列表，避免在 pager 页面内重复计算
    @Suppress("RedundantInitializer")
    val filteredCoursesByDayAndWeek =
        remember(coursesByDay, showNonCurrentWeek, pagerState.pageCount) {
            Array(pagerState.pageCount.coerceAtLeast(1)) { weekIndex ->
                val week = weekIndex + 1
                dayRange.associateWith { dayOfWeek ->
                    val dayCourses = coursesByDay[dayOfWeek] ?: emptyList()
                    if (showNonCurrentWeek) dayCourses
                    else dayCourses.filter { it.isActiveInWeek(week) }
                }
            }
        }

    @Suppress("RedundantInitializer")
    val onPendingChange: (Int, Int) -> Unit = remember {
        { day, section ->
            pendingDay = day
            pendingSection = section
        }
    }

    // 壁纸 LayerBackdrop：捕获壁纸内容供课程卡片 textureBlur 使用
    val wallpaperBackdropColor = MiuixTheme.colorScheme.surface
    val wallpaperBackdrop = rememberLayerBackdrop {
        drawRect(wallpaperBackdropColor)
        drawContent()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 壁纸背景
        if (wallpaperBitmap != null) {
            Box(modifier = Modifier.fillMaxSize().layerBackdrop(wallpaperBackdrop)) {
                androidx.compose.foundation.Image(
                    bitmap = wallpaperBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = wallpaperScale
                            scaleY = wallpaperScale
                            translationX = wallpaperOffset.x
                            translationY = wallpaperOffset.y
                        },
                    contentScale = ContentScale.Fit
                )
            }
        }

        // 用于手势回调中读取最新值，避免 pointerInput(Unit) 捕获陈旧状态
        val latestWallpaperScale by rememberUpdatedState(wallpaperScale)
        val latestWallpaperOffset by rememberUpdatedState(wallpaperOffset)
        val latestOnScaleChange by rememberUpdatedState(onWallpaperScaleChange)
        val latestOnOffsetChange by rememberUpdatedState(onWallpaperOffsetChange)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            userScrollEnabled = !isWallpaperEditing
        ) { page ->
            val week = page + 1
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .verticalScroll(scrollState)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues()
                            .calculateTopPadding() + if (isInFreeformWindow) activity.titleBarHeight + 40.dp else 96.dp,
                        bottom = 140.dp
                    )
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        SectionColumn(
                            totalSections = totalSections,
                            morningSections = morningSections,
                            afternoonSections = afternoonSections,
                            eveningSections = eveningSections,
                            sectionTimes = sectionTimes,
                            cardHeightPerSection = cardHeightPerSection,
                            cardBlurRadius = cardBlurRadius
                        )

                        dayRange.forEach { dayOfWeek ->
                            val filteredDayCourses = filteredCoursesByDayAndWeek
                                .getOrElse(page) { emptyMap() }
                                .getOrElse(dayOfWeek) { emptyList() }
                            key(page, dayOfWeek) {
                                val stableOnCourseClick: (Course) -> Unit =
                                    remember(page, dayOfWeek, week) {
                                        { course ->
                                            val coursesAtSlot = viewModel.getCoursesAtSlot(
                                                week,
                                                dayOfWeek,
                                                course.startSection,
                                                course.endSection
                                            )
                                            if (coursesAtSlot.size > 1) {
                                                selectedCourses = coursesAtSlot
                                                selectedCourse = coursesAtSlot.first()
                                            } else {
                                                selectedCourses = emptyList()
                                                selectedCourse = course
                                            }
                                            showCourseDetail = true
                                            onPopupStateChange(true)
                                        }
                                    }
                                val stableOnEmptyClick: (Int) -> Unit = remember(dayOfWeek) {
                                    { section -> viewModel.showAddDialog(dayOfWeek, section) }
                                }
                                DayColumn(
                                    dayOfWeek = dayOfWeek,
                                    courses = filteredDayCourses,
                                    currentDay = if (week == currentWeek) currentDayOfWeek else -1,
                                    onCourseClick = stableOnCourseClick,
                                    onEmptyClick = stableOnEmptyClick,
                                    onEmptyLongPress = onEmptyLongPress,
                                    morningSections = morningSections,
                                    afternoonSections = afternoonSections,
                                    eveningSections = eveningSections,
                                    currentWeek = week,
                                    pendingDay = pendingDay,
                                    pendingSection = pendingSection,
                                    onPendingChange = onPendingChange,
                                    wallpaperBackdrop = wallpaperBackdrop,
                                    cardBlurRadius = cardBlurRadius,
                                    cardAlpha = cardAlpha,
                                    cardHeightPerSection = cardHeightPerSection,
                                    cardCornerRadius = cardCornerRadius,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    val morningHeight = (morningSections * cardHeightPerSection).toInt()
                    val afternoonHeight = (afternoonSections * cardHeightPerSection).toInt()
                    val dinnerBreakY = morningHeight + 24 + afternoonHeight

                    Box(
                        modifier = Modifier.fillMaxWidth().offset(y = morningHeight.dp)
                            .height(24.dp)
                            .padding(vertical = 2.dp)
                            .then(
                                if (cardBlurRadius > 0f) {
                                    Modifier.textureBlur(
                                        backdrop = wallpaperBackdrop,
                                        shape = RectangleShape,
                                        blurRadius = cardBlurRadius * 2,
                                        colors = BlurDefaults.blurColors(
                                            blendColors = listOf(
                                                BlendColorEntry(
                                                    color = if (isAppDarkTheme())Color.Black.copy(alpha = cardAlpha * 2)
                                                    else Color.White.copy(alpha = cardAlpha * 2),
                                                    mode = BlurBlendMode.SrcOver
                                                )
                                            )
                                        )
                                    )
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "午休",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().offset(y = dinnerBreakY.dp)
                            .height(24.dp)
                            .padding(vertical = 2.dp)
                            .then(
                                if (cardBlurRadius > 0f) {
                                    Modifier.textureBlur(
                                        backdrop = wallpaperBackdrop,
                                        shape = RectangleShape,
                                        blurRadius = cardBlurRadius * 2,
                                        colors = BlurDefaults.blurColors(
                                            blendColors = listOf(
                                                BlendColorEntry(
                                                    color = if (isAppDarkTheme())Color.Black.copy(alpha = cardAlpha * 2)
                                                            else Color.White.copy(alpha = cardAlpha * 2),
                                                    mode = BlurBlendMode.SrcOver
                                                )
                                            )
                                        )
                                    )
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "晚休",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }

            // 编辑模式透明手势遮罩层（最顶层，拦截触摸）
            if (isWallpaperEditing && wallpaperBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                latestOnScaleChange((latestWallpaperScale * zoom).coerceIn(0.5f, 3f))
                                latestOnOffsetChange(latestWallpaperOffset + pan)
                            }
                        }
                )
            }
        }

        OverlayBottomSheet(
            show = showCourseDetail,
            title = "课程详情",
            endAction = {
                IconButton(onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    val course = selectedCourse ?: selectedCourses.firstOrNull()
                    showCourseDetail = false
                    if (course != null) {
                        viewModel.showAddDialog(
                            course.dayOfWeek,
                            course.startSection,
                            course.endSection
                        )
                    } else {
                        viewModel.showAddDialog()
                    }
                }, modifier = Modifier.padding(horizontal = 20.dp)) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = "添加课程",
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            backgroundColor = if (isAppDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF7F7F7),
            cornerRadius = 36.dp,
            insideMargin = DpSize(0.dp, 0.dp),
            onDismissRequest = {
                showCourseDetail = false
                onPopupStateChange(false)
            }
        ) {
            val coursesToShow =
                selectedCourses.ifEmpty { listOfNotNull(selectedCourse) }
                    .sortedWith(
                        compareByDescending<Course> { it.isActiveInWeek(viewingWeek) }
                            .thenByDescending { it.endWeek }
                            .thenByDescending { it.startWeek }
                    )
            Column(
                modifier = Modifier
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                coursesToShow.forEach { course ->
                    val summaryText = buildString {
                        append(course.getWeekText())
                        append(" ｜ ")
                        append(course.getSectionText())
                        if (course.classroom.isNotEmpty()) {
                            append("\n")
                            append(course.classroom)
                        }
                        if (course.teacher.isNotEmpty()) {
                            if (course.classroom.isNotEmpty()) append(" ｜ ")
                            append(course.teacher)
                        }
                    }
                    val isCurrentWeekCourse = course.isActiveInWeek(viewingWeek)
                    var cardBounds by remember {
                        mutableStateOf<androidx.compose.ui.geometry.Rect?>(
                            null
                        )
                    }
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .onGloballyPositioned { coordinates ->
                                val position =
                                    coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                                val size = coordinates.size
                                cardBounds = androidx.compose.ui.geometry.Rect(
                                    left = position.x,
                                    top = position.y,
                                    right = position.x + size.width,
                                    bottom = position.y + size.height
                                )
                            },
                        insideMargin = PaddingValues(0.dp),
                        pressFeedbackType = PressFeedbackType.None,
                        showIndication = true,
                        colors = CardDefaults.defaultColors(
                            color = if (isAppDarkTheme()) Color(0xFF363636) else Color(0xFFFFFFFF),
                            contentColor = MiuixTheme.colorScheme.onSurface
                        ),
                        onClick = {
                            // Open course detail page with all courses of the same name
                            val coursesForDetail = courses.filter { it.name == course.name }
                            val bounds = cardBounds
                            if (bounds != null) {
                                onCourseClick(
                                    coursesForDetail,
                                    bounds.left,
                                    bounds.top,
                                    bounds.width,
                                    bounds.height,
                                    null
                                )
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isCurrentWeekCourse) course.name else "${course.name}（非本周）",
                                    style = MiuixTheme.textStyles.body1.copy(fontSize = 17.sp),
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = summaryText,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(com.kyant.shapes.RoundedRectangle(20.dp))
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    .clickable {
                                        showCourseDetail = false
                                        onPopupStateChange(false)
                                        viewModel.showEditDialog(course)
                                    }
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "编辑",
                                    style = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp),
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(60.dp))
            }
        }

        if (showAddDialog) {
            val editingStartSection =
                editingCourse?.startSection ?: selectedStartSection
            val editingEndSection = editingCourse?.endSection ?: selectedEndSection

            AddCourseDialog(
                course = editingCourse,
                selectedDay = viewModel.selectedDay.collectAsState().value,
                totalWeeks = viewModel.totalWeeks.collectAsState().value,
                totalSections = totalSections,
                defaultStartSection = editingStartSection,
                defaultEndSection = editingEndSection,
                getOccupiedWeeks = { dayOfWeek, startSection, endSection ->
                    viewModel.getOccupiedWeeks(
                        dayOfWeek = dayOfWeek,
                        startSection = startSection,
                        endSection = endSection,
                        excludeId = editingCourse?.id
                    )
                },
                onDismiss = { viewModel.hideDialog() },
                onConfirm = { course ->
                    if (editingCourse != null) {
                        viewModel.updateCourse(course)
                    } else {
                        viewModel.addCourse(course)
                    }
                },
                onDelete = { courseId ->
                    viewModel.deleteCourse(courseId)
                }
            )
        }

        OverlayDialog(
            title = "跳转周数",
            show = showJumpWeekDialog,
            outsideMargin = DpSize(17.dp, 12.dp),
            onDismissRequest = { viewModel.hideJumpWeekDialog() }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NumberPicker(
                    value = jumpWeekTemp,
                    onValueChange = { jumpWeekTemp = it },
                    range = 1..totalWeeks,
                    visibleItemCount = 3,
                    itemHeight = 60.dp,
                    textStyle = MiuixTheme.textStyles.title2,
                    label = { "第${it}周" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.hideJumpWeekDialog()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.hideJumpWeekDialog()
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(jumpWeekTemp - 1)
                            }
                        },
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}