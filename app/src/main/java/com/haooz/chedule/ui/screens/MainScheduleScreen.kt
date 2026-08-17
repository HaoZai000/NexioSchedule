/** 主课程表页面 - 显示周视图课程表 */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.basic.BlurBottomSheet
import com.haooz.chedule.ui.basic.BlurBottomSheetTablet
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.LocalSheetTopBarMaterial
import com.haooz.chedule.ui.basic.OverlayDialog
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.components.DayColumn
import com.haooz.chedule.ui.components.SectionColumn
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberCourseCardEdgeLight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberKyantLayerBackdrop

/**
 * 课表网格几何信息，供拖拽调课时落点检测使用。
 * - dayBounds: dayOfWeek(1-7) -> [leftX, rightX, topY]（root px）
 * - sectionHeightPx: 每节高度（root px）
 * - morningSections/afternoonSections/eveningSections: 上午/下午/晚上的节次数
 * - showBreakDividers: 是否有午休/晚休分界带（24dp）
 */
data class ScheduleGridGeometry(
    val dayBounds: Map<Int, FloatArray>,
    val sectionHeightPx: Float,
    val morningSections: Int,
    val afternoonSections: Int,
    val eveningSections: Int,
    val showBreakDividers: Boolean
)

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun MainScheduleScreen(
    viewModel: CourseViewModel,
    settingsViewModel: SettingsViewModel,
    pagerState: PagerState,
    hiddenCourseIds: Set<String> = emptySet(),
    draggingCourseIds: Set<String> = emptySet(),
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: android.graphics.Bitmap?, courseIdToHide: String) -> Unit = { _, _, _, _, _, _, _ -> },
    onPopupStateChange: (Boolean) -> Unit = {},
    onEmptyLongPress: () -> Unit = {},
    onCourseLongPress: (course: Course, cardLeft: Float, cardTop: Float, width: Float, height: Float, backdrop: com.kyant.backdrop.Backdrop?, currentWeek: Int) -> Unit = { _, _, _, _, _, _, _ -> },
    onCourseDragStart: (courseId: String) -> Unit = { _ -> },
    onCourseDrag: (courseId: String, offsetX: Float, offsetY: Float) -> Unit = { _, _, _ -> },
    onCourseDragEnd: (courseId: String) -> Unit = { _ -> },
    onCourseMenuDismiss: () -> Unit = {},
    wallpaperBitmap: android.graphics.Bitmap? = null,
    wallpaperOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    wallpaperScale: Float = 1f,
    isWallpaperEditing: Boolean = false,
    onWallpaperOffsetChange: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onWallpaperScaleChange: (Float) -> Unit = {},
    cardBlurRadius: Float = 0f,
    cardAlpha: Float = 0.15f,
    cardHeightPerSection: Float = 54f,
    cardCornerRadius: Float = 10f,
    wallpaperBrightness: Float = 0f,
    showBreakDividers: Boolean = true,
    cardContentAlignment: com.haooz.chedule.data.CardContentAlignment = com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    // 拖拽落点高亮：Pair(dayOfWeek, sectionRange)，sectionRange 为落点覆盖的节次区间
    dropHighlight: Pair<Int, IntRange>? = null,
    // 调课后需要淡入放大的课程ID集合
    animateInCourseIds: Set<String> = emptySet(),
    onGridGeometryChange: (ScheduleGridGeometry) -> Unit = {},
    scheduleScrollBehavior: SharedScrollBehavior? = null,
    paddingValues: PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
    // Activity 层提升的状态，return@Scaffold 不会销毁
    externalScrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    externalShowCourseDetail: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(false),
    externalSheetContentBackdrop: androidx.compose.runtime.MutableState<com.kyant.backdrop.Backdrop?> = mutableStateOf(null),
    externalSelectedCourse: androidx.compose.runtime.MutableState<Course?> = mutableStateOf(null),
    externalSelectedCourses: androidx.compose.runtime.MutableState<List<Course>> = mutableStateOf(emptyList()),
) {
    val courses by viewModel.courses.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val showJumpWeekDialog by viewModel.showJumpWeekDialog.collectAsState()
    val showNonCurrentWeek by settingsViewModel.showNonCurrentWeek.collectAsState()
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val morningSections by settingsViewModel.morningSections.collectAsState()
    val afternoonSections by settingsViewModel.afternoonSections.collectAsState()
    val eveningSections by settingsViewModel.eveningSections.collectAsState()
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val scrollState = externalScrollState
    val topBarHeightDp = with(density) { (scheduleScrollBehavior?.currentHeightPx ?: 0f).toDp() }

    // 计算壁纸最小缩放比例（填满短边，确保不露出底部背景）
    // ContentScale.Fit 的基础缩放 = min(screenW/bitmapW, screenH/bitmapH)
    // 要覆盖屏幕需要最终缩放 = max(screenW/bitmapW, screenH/bitmapH)
    // 所以 wallpaperScale 的最小值 = max / min
    val minWallpaperScale = remember(wallpaperBitmap, screenWidthPx, screenHeightPx) {
        if (wallpaperBitmap != null && wallpaperBitmap.width > 0 && wallpaperBitmap.height > 0) {
            val fitScale = minOf(screenWidthPx / wallpaperBitmap.width, screenHeightPx / wallpaperBitmap.height)
            val coverScale = maxOf(screenWidthPx / wallpaperBitmap.width, screenHeightPx / wallpaperBitmap.height)
            if (fitScale > 0f) coverScale / fitScale else 1f
        } else 1f
    }

    var showCourseDetail by externalShowCourseDetail
    var sheetContentBackdrop by externalSheetContentBackdrop
    var selectedCourse by externalSelectedCourse
    var selectedCourses by externalSelectedCourses
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

    // 计算当前节次：根据当前时间和节次时间配置，判断当前处于第几节课
    val currentSection = remember(sectionTimes, totalSections) {
        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        var result = -1
        for (section in 1..totalSections) {
            val timeStr = sectionTimes[section] ?: ""
            if (timeStr.isEmpty()) continue
            val parts = timeStr.split("-")
            if (parts.size != 2) continue
            val startParts = parts[0].split(":")
            val endParts = parts[1].split(":")
            if (startParts.size != 2 || endParts.size != 2) continue
            val startMinutes = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
            val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
            if (currentMinutes in startMinutes until endMinutes) {
                result = section
                break
            }
        }
        result
    }

    // 用 snapshotFlow 观察翻页，避免 LaunchedEffect(pagerState.currentPage) 导致整个页面重组
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewingWeek = page + 1
        }
    }

    LaunchedEffect(showJumpWeekDialog) {
        if (showJumpWeekDialog) {
            jumpWeekTemp = pagerState.currentPage + 1
        }
    }

    // 预计算每天的课程，避免在 HorizontalPager 内部重复过滤
    val allDays = (1..7).toList()
    val coursesByDay = remember(courses) {
        allDays.associateWith { dayOfWeek ->
            courses.filter { it.dayOfWeek == dayOfWeek }
                .sortedBy { it.startSection }
        }
    }

    // 按需缓存：仅在 pager 内部访问时计算，不在顶层读取 pagerState.currentPage
    @Suppress("RedundantInitializer")
    val filteredCoursesCache = remember(coursesByDay, showNonCurrentWeek) {
        mutableMapOf<Int, Map<Int, List<Course>>>()
    }

    @Suppress("RedundantInitializer")
    val onPendingChange: (Int, Int) -> Unit = remember {
        { day, section ->
            pendingDay = day
            pendingSection = section
        }
    }

    // 壁纸 LayerBackdrop：捕获壁纸内容供课程卡片 textureBlur 使用
    val wallpaperBackdropColor = if (isAppDarkTheme()) Color(0xFF101010) else Color(0xFFFAFAFA)
    val wallpaperBackdrop = rememberLayerBackdrop {
        drawRect(wallpaperBackdropColor)
        drawContent()
    }
    // 全屏 LayerBackdrop：捕获全部内容供弹窗模糊使用
    val screenBackdrop = rememberLayerBackdrop {
        drawRect(wallpaperBackdropColor)
        drawContent()
    }
    // Kyant Backdrop：供课程卡片 drawBackdrop 使用
    val courseCardBackdrop = rememberKyantLayerBackdrop {
        drawRect(wallpaperBackdropColor)
        drawContent()
    }

    Box(modifier = Modifier.fillMaxSize().layerBackdrop(screenBackdrop)) {
        // 壁纸背景
        if (wallpaperBitmap != null) {
            Box(modifier = Modifier.fillMaxSize().layerBackdrop(wallpaperBackdrop).kyantLayerBackdrop(courseCardBackdrop)) {
                val brightnessFilter = remember(wallpaperBrightness) {
                    if (wallpaperBrightness != 0f) {
                        val b = (1f + wallpaperBrightness / 50f).coerceIn(0f, 2f)
                        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(
                                floatArrayOf(
                                    b, 0f, 0f, 0f, 0f,
                                    0f, b, 0f, 0f, 0f,
                                    0f, 0f, b, 0f, 0f,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            )
                        )
                    } else null
                }
                val imageBitmap = remember(wallpaperBitmap) { wallpaperBitmap.asImageBitmap() }
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val effectiveScale = maxOf(wallpaperScale, minWallpaperScale)
                            scaleX = effectiveScale
                            scaleY = effectiveScale
                            translationX = wallpaperOffset.x
                            translationY = wallpaperOffset.y
                        },
                    contentScale = ContentScale.Fit,
                    colorFilter = brightnessFilter
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().layerBackdrop(wallpaperBackdrop).kyantLayerBackdrop(courseCardBackdrop).background(wallpaperBackdropColor))
        }

        // 用于手势回调中读取最新值，避免 pointerInput(Unit) 捕获陈旧状态
        val latestWallpaperScale by rememberUpdatedState(wallpaperScale)
        val latestWallpaperOffset by rememberUpdatedState(wallpaperOffset)
        val latestOnScaleChange by rememberUpdatedState(onWallpaperScaleChange)
        val latestOnOffsetChange by rememberUpdatedState(onWallpaperOffsetChange)
        val latestMinWallpaperScale by rememberUpdatedState(minWallpaperScale)
        val latestWallpaperBitmap by rememberUpdatedState(wallpaperBitmap)
        val latestScreenWidthPx by rememberUpdatedState(screenWidthPx)
        val latestScreenHeightPx by rememberUpdatedState(screenHeightPx)

        // 手势结束后触发缩放回弹动画（指针输入作用域内无法调用 animate，需通过状态触发）
        var bounceBackTrigger by remember { mutableIntStateOf(0) }
        var gestureEndScale by remember { mutableFloatStateOf(1f) }
        LaunchedEffect(bounceBackTrigger) {
            if (bounceBackTrigger > 0 && gestureEndScale < latestMinWallpaperScale) {
                animate(
                    initialValue = gestureEndScale,
                    targetValue = latestMinWallpaperScale,
                    animationSpec = tween(
                        durationMillis = 350,
                        easing = CubicBezierEasing(0.34f, 1.1f, 0.3f, 1f)
                    )
                ) { value, _ ->
                    onWallpaperScaleChange(value)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = !isWallpaperEditing
        ) { page ->
            val week = page + 1



            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (scheduleScrollBehavior != null) Modifier.nestedScroll(scheduleScrollBehavior.nestedScrollConnection)
                        else Modifier
                    )
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .verticalScroll(scrollState)
                    .padding(
                        top = paddingValues.calculateTopPadding() + topBarHeightDp - 78.dp,
                        bottom = 140.dp
                    )
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // 收集每列在 root 中的 x 区间与顶部 y，供拖拽落点检测使用
                    val dayBoundsArray = remember { arrayOfNulls<FloatArray>(8) }
                    var lastDayBoundsVersion by remember { mutableIntStateOf(0) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isTablet) Modifier.padding(horizontal = 24.dp) else Modifier.padding(end = 2.dp)
                            )
                    ) {
                        SectionColumn(
                            totalSections = totalSections,
                            morningSections = morningSections,
                            afternoonSections = afternoonSections,
                            eveningSections = eveningSections,
                            sectionTimes = sectionTimes,
                            cardHeightPerSection = cardHeightPerSection,
                            showBreakDividers = showBreakDividers,
                            currentSection = if (week == currentWeek) currentSection else -1,
                            isTablet = isTablet,
                            hasWallpaper = wallpaperBitmap != null
                        )

                        // 按周计算要显示的天数范围（智能周末模式下，不同周可能显示不同天数）
                        val pageDayRange = remember(week, smartWeekend, courses.size) {
                            (1..5).toList() + settingsViewModel.getWeekendDaysForWeek(week).filter { it in 6..7 }
                        }

                        pageDayRange.forEach { dayOfWeek ->
                            // 按需计算并缓存：仅在访问时计算，不在顶层读取 pagerState.currentPage
                            val filteredDayCourses = filteredCoursesCache.getOrPut(page) {
                                val weekForPage = page + 1
                                allDays.associateWith { dayOfWeek ->
                                    val dayCourses = coursesByDay[dayOfWeek] ?: emptyList()
                                    if (showNonCurrentWeek) dayCourses
                                    else dayCourses.filter { it.isActiveInWeek(weekForPage) }
                                }
                            }.getOrElse(dayOfWeek) { emptyList() }
                            val stableOnCourseClick: (Course) -> Unit =
                                remember(page, dayOfWeek, week) {
                                    { course ->
                                        val coursesAtSlot = viewModel.getCoursesAtSlot(
                                            week,
                                            dayOfWeek,
                                            course.startSection,
                                            course.endSection
                                        )
                                        // 选中点击的课程（若在槽位列表中），避免选到节次更靠前的旧课程
                                        selectedCourses = coursesAtSlot
                                        selectedCourse = coursesAtSlot.find { it.id == course.id } ?: course
                                        showCourseDetail = true
                                        onPopupStateChange(true)
                                    }
                                }
                            val stableOnEmptyClick: (Int) -> Unit = remember(dayOfWeek) {
                                { section -> viewModel.showAddDialog(dayOfWeek, section) }
                            }
                            val stableOnCourseLongPress: (Course, Float, Float, Float, Float, com.kyant.backdrop.Backdrop?, Int) -> Unit =
                                remember(page, dayOfWeek) {
                                    { course, left, top, width, height, _, cWeek ->
                                        onCourseLongPress(course, left, top, width, height, courseCardBackdrop, cWeek)
                                    }
                                }
                            DayColumn(
                                dayOfWeek = dayOfWeek,
                                courses = filteredDayCourses,
                                onCourseClick = stableOnCourseClick,
                                onEmptyClick = stableOnEmptyClick,
                                onEmptyLongPress = onEmptyLongPress,
                                morningSections = morningSections,
                                afternoonSections = afternoonSections,
                                eveningSections = eveningSections,
                                sectionTimes = sectionTimes,
                                currentWeek = week,
                                pendingDay = pendingDay,
                                pendingSection = pendingSection,
                                onPendingChange = onPendingChange,
                                wallpaperBackdrop = if (wallpaperBitmap != null) courseCardBackdrop else null,
                                cardBlurRadius = cardBlurRadius,
                                cardAlpha = cardAlpha,
                                cardHeightPerSection = cardHeightPerSection,
                                cardCornerRadius = cardCornerRadius,
                                showBreakDividers = showBreakDividers,
                                isTablet = isTablet,
                                cardContentAlignment = cardContentAlignment,
                                draggingCourseIds = draggingCourseIds,
                                onCourseLongPress = stableOnCourseLongPress,
                                onCourseDragStart = onCourseDragStart,
                                onCourseDrag = onCourseDrag,
                                onCourseDragEnd = onCourseDragEnd,
                                onCourseMenuDismiss = onCourseMenuDismiss,
                                dropHighlightSections = if (dropHighlight?.first == dayOfWeek) dropHighlight.second else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { coordinates ->
                                        val pos = coordinates.positionInRoot()
                                        val w = coordinates.size.width.toFloat()
                                        val old = dayBoundsArray[dayOfWeek]
                                        val new = floatArrayOf(pos.x, pos.x + w, pos.y)
                                        if (old == null || old[0] != new[0] || old[1] != new[1] || old[2] != new[2]) {
                                            dayBoundsArray[dayOfWeek] = new
                                            lastDayBoundsVersion++
                                        }
                                    }
                            )
                        }
                    }

                    // 网格布局完成后上报几何信息（仅当前页上报，避免 beyondViewportPageCount 缓存页覆盖当前页数据）
                    // 仅在几何参数实际变化时才触发回调，避免每次重组分配新对象
                    val sectionHeightPx = with(density) { cardHeightPerSection.dp.toPx() }
                    val prevBoundsVersion = remember { mutableIntStateOf(lastDayBoundsVersion) }
                    val prevSectionHeight = remember { mutableFloatStateOf(sectionHeightPx) }
                    val prevMorning = remember { mutableIntStateOf(morningSections) }
                    val prevAfternoon = remember { mutableIntStateOf(afternoonSections) }
                    val prevEvening = remember { mutableIntStateOf(eveningSections) }
                    val prevShowBreak = remember { mutableStateOf(showBreakDividers) }
                    SideEffect {
                        if (page == pagerState.currentPage) {
                            val changed = prevBoundsVersion.intValue != lastDayBoundsVersion
                                    || prevSectionHeight.floatValue != sectionHeightPx
                                    || prevMorning.intValue != morningSections
                                    || prevAfternoon.intValue != afternoonSections
                                    || prevEvening.intValue != eveningSections
                                    || prevShowBreak.value != showBreakDividers
                            if (changed) {
                                prevBoundsVersion.intValue = lastDayBoundsVersion
                                prevSectionHeight.floatValue = sectionHeightPx
                                prevMorning.intValue = morningSections
                                prevAfternoon.intValue = afternoonSections
                                prevEvening.intValue = eveningSections
                                prevShowBreak.value = showBreakDividers
                                val boundsMap = mutableMapOf<Int, FloatArray>()
                                for (i in 1..7) {
                                    val arr = dayBoundsArray[i]
                                    if (arr != null) boundsMap[i] = arr
                                }
                                onGridGeometryChange(
                                    ScheduleGridGeometry(
                                        dayBounds = boundsMap,
                                        sectionHeightPx = sectionHeightPx,
                                        morningSections = morningSections,
                                        afternoonSections = afternoonSections,
                                        eveningSections = eveningSections,
                                        showBreakDividers = showBreakDividers
                                    )
                                )
                            }
                        }
                    }

                    val morningHeight = (morningSections * cardHeightPerSection).toInt()
                    val afternoonHeight = (afternoonSections * cardHeightPerSection).toInt()
                    val dividerOffset = if (showBreakDividers) 24 else 0
                    val dinnerBreakY = morningHeight + dividerOffset + afternoonHeight

                    if (showBreakDividers) {
                    val dividerShape = RoundedRectangle(12.dp)
                    val dividerHorizontalPadding = if (isTablet) 24.dp else 4.dp
                    val dividerIsDark = isAppDarkTheme()
                    val dividerDensity = LocalDensity.current
                    // 与课程卡片一致的液态玻璃参数
                    val dividerBlurPx = with(dividerDensity) { remember(cardBlurRadius) { cardBlurRadius.dp.toPx() } }
                    val dividerLensRadiusPx = with(dividerDensity) { remember { 4f.dp.toPx() } }
                    val dividerLensStrengthPx = with(dividerDensity) { remember { 14f.dp.toPx() } }
                    val hasWallpaperDivider = wallpaperBitmap != null
                    // 无壁纸时用纯色背景；有壁纸时底色透明，由 drawBackdrop 绘制玻璃层
                    val dividerBaseColor = if (hasWallpaperDivider) Color.Transparent else if (dividerIsDark) Color(0xFF171717) else Color(0xFFF3F3F3)
                    // 玻璃底色与反光覆盖层（同 CourseCard 的液态玻璃观感）
                    val dividerGlassColor = if (dividerIsDark) Color(0xFF323232).copy(alpha = 0.64f) else Color.White.copy(alpha = 0.5f)
                    val dividerOverlayColor = if (dividerIsDark) Color(0xFF323232).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.1f)
                    val dividerBlurShape = remember { RoundedRectangle(12.dp) }
                    val dividerEdgeLightShape = remember { RoundedRectangle(12.dp) }

                    @Composable
                    fun BreakDivider(offsetY: Int, text: String) {
                        Box(
                            modifier = Modifier.fillMaxWidth().offset(y = offsetY.dp)
                                .height(24.dp)
                                .padding(vertical = 2.dp)
                                .padding(horizontal = dividerHorizontalPadding)
                                .background(dividerBaseColor, dividerShape)
                                .then(
                                    if (hasWallpaperDivider) {
                                        Modifier.drawBackdrop(
                                            backdrop = courseCardBackdrop,
                                            shape = { dividerBlurShape },
                                            effects = {
                                                blur(dividerBlurPx)
                                                lens(dividerLensRadiusPx, dividerLensStrengthPx)
                                            },
                                            highlight = null,
                                            onDrawSurface = {
                                                drawRect(dividerGlassColor)
                                                drawRect(dividerOverlayColor)
                                            }
                                        ).edgeLight(shape = dividerEdgeLightShape, edgeLight = rememberCourseCardEdgeLight())
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = text,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        }
                    }
                    BreakDivider(morningHeight, "午休")
                    BreakDivider(dinnerBreakY, "晚休")
                    }
                }
            }

            // 编辑模式透明手势遮罩层（最顶层，拦截触摸）
            if (isWallpaperEditing && wallpaperBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                var gestureScale = latestWallpaperScale
                                var lastDisplayScale = gestureScale
                                do {
                                    val event = awaitPointerEvent()
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    gestureScale *= zoom
                                    // 低于最小缩放时逐渐增大阻力，越缩越难
                                    val newScale = if (gestureScale < latestMinWallpaperScale) {
                                        val diff = gestureScale - latestMinWallpaperScale
                                        latestMinWallpaperScale + diff * 0.3f
                                    } else {
                                        gestureScale
                                    }
                                    lastDisplayScale = newScale
                                    // 计算合法偏移范围
                                    val bmp = latestWallpaperBitmap
                                    if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                                        val fitScale = minOf(latestScreenWidthPx / bmp.width, latestScreenHeightPx / bmp.height)
                                        val scaledW = bmp.width * fitScale * newScale
                                        val scaledH = bmp.height * fitScale * newScale
                                        val maxOffsetX = ((scaledW - latestScreenWidthPx) / 2f).coerceAtLeast(0f)
                                        val maxOffsetY = ((scaledH - latestScreenHeightPx) / 2f).coerceAtLeast(0f)
                                        val newOffset = latestWallpaperOffset + pan
                                        latestOnScaleChange(newScale)
                                        latestOnOffsetChange(
                                            androidx.compose.ui.geometry.Offset(
                                                newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                                newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                            )
                                        )
                                    } else {
                                        latestOnScaleChange(newScale)
                                        latestOnOffsetChange(latestWallpaperOffset + pan)
                                    }
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                                // 手势结束，记录最终显示缩放并标记需要回弹
                                gestureEndScale = lastDisplayScale
                                bounceBackTrigger++
                            }
                        }
                )
            }
        }

        val detailEndAction: @Composable () -> Unit = {
            val material = LocalSheetTopBarMaterial.current
            LiquidTopBarButton(
                onClick = {
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
                },
                backdrop = sheetContentBackdrop ?: liquidGlassBackdrop!!,
                icon = MiuixIcons.Add,
                contentDescription = "添加课程",
                modifier = Modifier.padding(end = if (isTablet) 16.dp else 18.dp),
                iconSize = 24.dp,
                containerColor = if (isAppDarkTheme()) Color(0xFF363636).copy(0.4f)
                else Color(0xFFFFFFFF).copy(0.6f),
                backdropAlpha = material.backdropAlpha,
                shadowAlpha = material.shadowAlpha,
            )
        }
        val detailContent: @Composable () -> Unit = {
            val coursesToShow = remember(selectedCourses, selectedCourse, viewingWeek) {
                selectedCourses.ifEmpty { listOfNotNull(selectedCourse) }
                    .sortedWith(
                        compareByDescending<Course> { it.isActiveInWeek(viewingWeek) }
                            .thenByDescending { it.endWeek }
                            .thenByDescending { it.startWeek }
                    )
            }
            Column(
                modifier = Modifier
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isTablet) 56.dp else 58.dp))
                coursesToShow.forEach { course ->
                    val summaryText = buildString {
                        append(course.getWeekText())
                        append(" ｜ ")
                        append(course.getTimeDisplayText())
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
                    val isHidden = course.id in hiddenCourseIds
                    var cardBounds by remember {
                        mutableStateOf<androidx.compose.ui.geometry.Rect?>(
                            null
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = if (isHidden) 0f else 1f }
                    ) {
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
                            color = if (isAppDarkTheme()) Color(0xFF363636).copy(alpha = 0.62f) else Color(0xFFFFFFFF).copy(alpha = 0.7f),
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
                                    null,
                                    course.id
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
                                    .clip(RoundedRectangle(20.dp))
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
                    } // Column (isHidden)
                }
                Spacer(modifier = Modifier.height(if (isTablet) 0.dp else 260.dp))
            }
        }
        // 重建时如果弹窗已打开，跳过进入动画；弹窗关闭后重置，避免后续打开始终无动画
        var skipSheetEnterAnimation by remember { mutableStateOf(showCourseDetail) }
        LaunchedEffect(showCourseDetail) {
            if (!showCourseDetail) {
                skipSheetEnterAnimation = false
            }
        }
        if (isTablet) {
            BlurBottomSheetTablet(
                show = showCourseDetail,
                title = "课程详情",
                dimBackground = true,
                isBottomAligned = true,
                onDismissRequest = {
                    showCourseDetail = false
                    onPopupStateChange(false)
                },
                liquidGlassBackdrop = liquidGlassBackdrop,
                onSheetContentBackdropCreated = { sheetContentBackdrop = it },
                endAction = detailEndAction,
                skipEnterAnimation = skipSheetEnterAnimation,
                content = detailContent
            )
        } else {
            BlurBottomSheet(
                show = showCourseDetail,
                title = "课程详情",
                liquidGlassBackdrop = liquidGlassBackdrop,
                dimBackground = true,
                onDismissRequest = {
                    showCourseDetail = false
                    onPopupStateChange(false)
                },
                onSheetContentBackdropCreated = { sheetContentBackdrop = it },
                endAction = detailEndAction,
                skipEnterAnimation = skipSheetEnterAnimation,
                content = detailContent
            )
        }

        OverlayDialog(
            title = "跳转周数",
            show = showJumpWeekDialog,
            liquidGlassBackdrop = liquidGlassBackdrop,
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
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
