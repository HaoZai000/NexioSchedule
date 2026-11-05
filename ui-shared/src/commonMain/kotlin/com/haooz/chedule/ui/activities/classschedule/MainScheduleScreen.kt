package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.appearance.AppearanceConfig
import com.haooz.chedule.data.appearance.CardRefractionLevel
import com.haooz.chedule.data.appearance.scheduleSurfaceAlpha
import com.haooz.chedule.data.holiday.HolidayCourseExclusion
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.SpecialItem
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.repo.DayScheduleResolver
import com.haooz.chedule.data.util.minusDays
import com.haooz.chedule.data.util.nowLocalTime
import com.haooz.chedule.data.util.plusDays
import com.haooz.chedule.data.util.plusWeeks
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.activities.main.PagerJumpNeighbor
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.CourseDetailSheet
import com.haooz.chedule.ui.components.LocalGlassRedrawSignal
import com.haooz.chedule.ui.components.LocalSoftGlassSurface
import com.haooz.chedule.ui.components.SoftGlassSurface
import com.haooz.chedule.ui.components.text.LocalTextMotionInstant
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.PagerTakeoverGestureState
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.courseCardSolidBacking
import com.haooz.chedule.ui.utils.hostVerticalBarEdge
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.pagerAxisTakeoverGesture
import com.haooz.chedule.ui.utils.rememberPagerTakeoverGestureState
import com.haooz.chedule.ui.utils.schedulePageBackgroundColor
import com.haooz.chedule.ui.utils.screenHeightDp
import com.haooz.chedule.ui.utils.screenWidthDp
import com.kyant.backdrop.Backdrop
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.LocalSheetContentBackdrop
import top.yukonga.miuix.kmp.overlay.LocalSheetTopBarMaterial
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import top.yukonga.miuix.kmp.utils.OverScrollState
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// 普通 holder 而非 state：避免 onGloballyPositioned 每次写入触发卡片重组

data class ScheduleGridGeometry(
    val dayBounds: Map<Int, FloatArray>,
    val sectionHeightPx: Float,
    val morningSections: Int,
    val afternoonSections: Int,
    val eveningSections: Int,
    val showBreakDividers: Boolean,
    /**
     * 列星期 → 数据（星期, 周次）。调课日列显示的是 followWeekday/followWeek 的课，
     * 落点/冲突检测/写库必须映射回数据坐标，否则写进去的课在本页任何列都不渲染。
     */
    val columnDataPosition: Map<Int, Pair<Int, Int>> = emptyMap(),
    /**
     * 这份几何属于第几周（= 上报页的周次）。跨周调课翻页后上层拿到的可能还是上一页的
     * 几何，落点周次必须靠它判断能否信任 [columnDataPosition]。
     */
    val pageWeek: Int = 0,
    /** 教学周重组的休课日列：该列永远渲染为空，落进去的课会静默消失，必须拦掉 */
    val blockedColumns: Set<Int> = emptySet(),
    /**
     * 节次 → 顶部 y（dp，列内相对坐标），取自 [computeSpecialGridLayout]。
     *
     * ⚠ 定位必须用它，不能再用「节次高 × 序号 + 分界缝」现算：特殊块会把下面的节次
     * 整体下推，现算公式漏掉这部分，长按浮层/吸附落点会整体偏上（偏移量 = 该节次上方特殊块总高）。
     */
    val sectionTopDp: Map<Int, Float> = emptyMap(),
)

@Composable
fun MainScheduleScreen(
    viewModel: CourseViewModel,
    settingsViewModel: SettingsViewModel,
    pagerState: PagerState,
    /** 长距跳转借位页：让相邻页临时显示目标周（见 PagerJumpNeighbor） */
    jumpNeighbor: PagerJumpNeighbor? = null,
    hiddenCourseIds: Set<String> = emptySet(),
    draggingCourseSlots: Set<Pair<String, Int>> = emptySet(),
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: TransitionLayerSnapshot?, courseIdToHide: String, targetWeek: Int) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onPopupStateChange: (Boolean) -> Unit = {},
    // 空白格长按：返回 Root 绝对坐标供上层定位快捷菜单
    onEmptyLongPress: (
        day: Int,
        section: Int,
        centerX: Float,
        cellTopY: Float,
        width: Float,
        height: Float,
        // 调课日：添加课程默认落到 followWeekday / followWeek；非调课与 day 相同
        addDay: Int,
        addWeek: Int,
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onCourseLongPress: (course: Course, cardLeft: Float, cardTop: Float, width: Float, height: Float, currentWeek: Int) -> Unit = { _, _, _, _, _, _ -> },
    onCourseDragStart: (courseId: String) -> Unit = { _ -> },
    onCourseDrag: (courseId: String, offsetX: Float, offsetY: Float) -> Unit = { _, _, _ -> },
    onCourseDragEnd: (courseId: String) -> Unit = { _ -> },
    onCourseDragCancel: (courseId: String) -> Unit = { _ -> },
    onCourseMenuDismiss: () -> Unit = {},
    wallpaperBitmap: ImageBitmap? = null,
    wallpaperOffset: Offset = Offset.Zero,
    wallpaperScale: Float = 1f,
    // true：壁纸由主 pager 后共享层绘制，本页透明叠上，切 tab 时不随页平移
    useSharedWallpaper: Boolean = false,
    isWallpaperEditing: Boolean = false,
    onWallpaperOffsetChange: (Offset) -> Unit = {},
    onWallpaperScaleChange: (Float) -> Unit = {},
    appearance: AppearanceConfig = AppearanceConfig(),
    /** 周页呈现方式：网格（可编辑）或时间轴（按真实时间比例、只读） */
    scheduleViewMode: ScheduleViewMode = ScheduleViewMode.WEEK,
    liquidGlassBackdrop: Backdrop? = null,
    /** 柔光玻璃采样源（主内容层算一次；null = 无玻璃） */
    softGlassSurface: SoftGlassSurface? = null,
    /** 主 tab pager 的位置信号：切今日/课表时内容整体平移，必须让玻璃重绘 */
    tabRedraw: () -> Unit = {},
    // 拖拽落点高亮：Pair(dayOfWeek, sectionRange)，sectionRange 为落点覆盖的节次区间。
    // 传 lambda 而不是值：求值推迟到落点遮罩自己的组合作用域里，跨格只重组遮罩那一小块
    // （传值等于在调用方组合期读落点状态，每跨一格整页重组一次）。
    dropHighlight: () -> Pair<Int, IntRange>? = { null },
    // 落点遮罩出现动画的起点（通常是源课格），避免首次直接闪现在落点
    dropHighlightOrigin: () -> Pair<Int, IntRange>? = { null },
    // 拖课卡停在屏幕边缘热区（即将自动翻周）时为 true；供周 pager 在驻留空闲期
    // 预组合翻页要用的邻页（beyond 1→3），把组合成本挪出翻页动画帧
    edgeDwellHint: () -> Boolean = { false },
    // 周 pager 的手势接管状态：借位跳周被手势打断时，jumpToPageSmoothly 借它
    // 同步平移松手落页基准，避免手势被旧起点拉回跳转前的周
    pagerGestureState: PagerTakeoverGestureState = rememberPagerTakeoverGestureState(),
    onGridGeometryChange: (ScheduleGridGeometry) -> Unit = {},
    scheduleScrollBehavior: SharedScrollBehavior? = null,
    // 由 Activity 层传入：不是防销毁，是液态玻璃重录要在外层读滚动状态 / 弹窗开合
    externalScrollState: ScrollState = rememberScrollState(),
    externalShowCourseDetail: MutableState<Boolean> = mutableStateOf(false),
    externalSelectedCourse: MutableState<Course?> = mutableStateOf(null),
    externalSelectedCourses: MutableState<List<Course>> = mutableStateOf(emptyList()),
) {
    val cardBlurRadius = appearance.cardBlurRadius
    val cardAlpha = appearance.cardAlpha
    // 基准值（深色已 ×0.8）再低 15pp：亮色 40% ⇒ 25%，深色 32% ⇒ 17%。
    // 分界带 / 时间轴 / 浮层都从这里取，同页同值。
    val cardSurfaceAlpha = appearance.scheduleSurfaceAlpha(isAppDarkTheme())
    val cardHeightPerSection = appearance.cardHeight
    val cardCornerRadius = appearance.cardCornerRadius
    val showBreakDividers = appearance.showBreakDividers
    val cardContentAlignment = appearance.cardContentAlignment
    val cardTextColor = appearance.cardTextColor
    val cardTextScale = appearance.cardTextScale
    val showClassroom = appearance.showClassroom
    val showTeacher = appearance.showTeacher
    val cardRefraction = appearance.cardRefraction

    val courses by viewModel.courses.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val dataVersion by viewModel.dataVersion.collectAsState()
    val holidayDataRevision by HolidayManager.dataRevision.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val showNonCurrentWeek by settingsViewModel.showNonCurrentWeek.collectAsState()
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val morningSections by settingsViewModel.morningSections.collectAsState()
    val afternoonSections by settingsViewModel.afternoonSections.collectAsState()
    val eveningSections by settingsViewModel.eveningSections.collectAsState()
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()
    val sectionNames by settingsViewModel.sectionNames.collectAsState()
    val specialBlocks by settingsViewModel.specialBlocks.collectAsState()
    // 状态提升到页面顶层：弹窗在顶层作用域渲染，点击横带只写这些状态
    var showSpecialItemDialog by remember { mutableStateOf(false) }
    var specialItemEditingBlockId by remember { mutableLongStateOf(0L) }
    var specialItemEditingId by remember { mutableLongStateOf(-1L) } // -1 表示新增
    var specialItemName by remember { mutableStateOf("") }
    // 支持不连续点选，保存时自动合并为连续区间
    var specialItemSelectedDays by remember { mutableStateOf(setOf<Int>()) }
    val hapticFeedback = LocalHapticFeedback.current
    val isTablet = isTabletWidth()
    val density = LocalDensity.current
    // 沉浸式：滚动视口不避开底栏。底部留白写进滚动 layout 高度，避免被测量链裁掉
    val nativeChromeInset = LocalHostNavigation.current
        ?.takeIf { it.showsNativeTabBar && !isTablet }
        ?.bottomBarInset?.toFloat()?.dp
    val scheduleEndSpacer = nativeChromeInset?.plus(80.dp) ?: 175.dp
    val screenWidthPx = with(density) { screenWidthDp().dp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp().dp.toPx() }
    // 位置信号：卡片的玻璃在各自 draw 里画，而滚动/翻页/超出回弹只平移祖先图层。
    // 必须把 overScroll 的 offset 也读进来：回弹时 scrollState.value 卡在边界不变，
    // 少了它玻璃采样会冻在开始回弹那一帧。
    val overScrollState = remember { OverScrollState() }
    val weekOverscrollX = remember { mutableFloatStateOf(0f) }
    val glassRedrawSignal: () -> Unit = remember(pagerState, externalScrollState, overScrollState, weekOverscrollX, tabRedraw) {
        {
            externalScrollState.value
            pagerState.currentPageOffsetFraction
            overScrollState.offset
            weekOverscrollX.floatValue
            tabRedraw()
        }
    }
    // 非 state 版本：卡片 onGloballyPositioned 读取不触发重组
    val touchState = remember { ScheduleTouchState() }
    // 跨周调课 tracker 的坐标（挂在 pager 上，跨周不重建）
    val dragOwnerCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // 指针协程的 key 只有 touchState：回调一律经 rememberUpdatedState 取最新，
    // 否则每次重组都重启手势，拖到一半直接断
    val latestOnCourseDrag = rememberUpdatedState(onCourseDrag)
    val latestOnCourseDragEnd = rememberUpdatedState(onCourseDragEnd)
    val latestOnCourseDragCancel = rememberUpdatedState(onCourseDragCancel)
    // 整页被销毁（切 tab 且主 pager 回收本页）时 tracker 协程也一起没了：
    // 手指还没抬就没人收尾，浮层会留到别的页面上去。这里兜底取消。
    DisposableEffect(touchState) {
        onDispose {
            val activeDragId = touchState.draggingCourseId
            val hadDrag = touchState.draggingPointerId != null || touchState.dragTrackerActive
            // endDragTracking 会留下 dragEndHandled：课卡侧的 finally 同帧收尾时
            // 看到它就不会再 cancel 一次
            touchState.endDragTracking()
            if (hadDrag && activeDragId != null) {
                latestOnCourseDragCancel.value(activeDragId)
            }
        }
    }
    // 供停滑后冲刷 dayBounds 版本号；仅滑动起停各写一次
    val scheduleScrollInProgress = remember { mutableStateOf(false) }
    LaunchedEffect(pagerState, externalScrollState) {
        // 必须直接读 ScrollState：捕获 composition 期 Boolean 后 snapshotFlow 不会再观测变化
        snapshotFlow {
            pagerState.isScrollInProgress || externalScrollState.isScrollInProgress
        }.collect {
            touchState.scrolling = it
            scheduleScrollInProgress.value = it
        }
    }
    // 横纵双轴手势：横向由 pagerAxisTakeoverGesture 接管，纵向仍归页内 verticalScroll
    val latestIsWallpaperEditing by rememberUpdatedState(isWallpaperEditing)
    // 由顶栏自身几何纯计算（切页不变），避免 paddingValues 随 tab 变化导致位移
    val nativeBarInset = (LocalHostNavigation.current?.topBarInset ?: 0.0).dp
    // 竖排 tab bar 形态（折叠屏折叠态，屏幕顶部没有状态栏）：内容顶不按 160.dp 那套撑，
    // 详见 scheduleContentTopPadding
    val verticalBarEdge = hostVerticalBarEdge()
    val statusBarHeight = (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() -
            nativeBarInset).coerceAtLeast(0.dp)
    val contentTopPaddingDp = remember(statusBarHeight, verticalBarEdge) {
        scheduleContentTopPadding(statusBarHeight, verticalBarEdge)
    }

    // 填满短边不露底；最小缩放 = cover/fit
    val minWallpaperScale = remember(wallpaperBitmap, screenWidthPx, screenHeightPx) {
        if (wallpaperBitmap != null && wallpaperBitmap.width > 0 && wallpaperBitmap.height > 0) {
            val fitScale = minOf(
                screenWidthPx / wallpaperBitmap.width,
                screenHeightPx / wallpaperBitmap.height
            )
            val coverScale = maxOf(
                screenWidthPx / wallpaperBitmap.width,
                screenHeightPx / wallpaperBitmap.height
            )
            if (fitScale > 0f) coverScale / fitScale else 1f
        } else 1f
    }

    var showCourseDetail by externalShowCourseDetail
    // backdrop 在弹窗作用域读 LocalSheetContentBackdrop，不提升到本页 State
    var selectedCourse by externalSelectedCourse
    var selectedCourses by externalSelectedCourses
    var pendingDay by remember { mutableIntStateOf(-1) }
    var pendingSection by remember { mutableIntStateOf(-1) }
    var viewingWeek by remember { mutableIntStateOf(currentWeek) }

    LaunchedEffect(showAddDialog) {
        if (showAddDialog && pendingDay != -1) {
            delay(300.milliseconds)
            pendingDay = -1
            pendingSection = -1
        }
    }

    val totalSections = morningSections + afternoonSections + eveningSections

    val specialGrid = remember(
        totalSections, morningSections, afternoonSections, eveningSections,
        specialBlocks, sectionTimes, cardHeightPerSection, showBreakDividers
    ) {
        computeSpecialGridLayout(
            morningSections = morningSections,
            afternoonSections = afternoonSections,
            eveningSections = eveningSections,
            specialBlocks = specialBlocks,
            sectionTimes = sectionTimes,
            cardHeightPerSection = cardHeightPerSection,
            dividerGap = if (showBreakDividers) 24 else 0
        )
    }

    // 切回网格时归零：时间轴的纵向尺度（分钟）远大于节次，沿用它的值会停在网格底部附近。
    // 只在「时间轴 → 网格」这一次切换时做，首次进入或从别的页面返回都保持原滚动位置。
    var previousViewMode by remember { mutableStateOf(scheduleViewMode) }
    LaunchedEffect(scheduleViewMode) {
        val from = previousViewMode
        previousViewMode = scheduleViewMode
        // 课表页顶栏始终折叠，不要在这里调 scrollBehavior.expand()，那会顶出大标题
        if (from == ScheduleViewMode.TIMELINE && scheduleViewMode == ScheduleViewMode.WEEK) {
            externalScrollState.scrollTo(0)
        }
    }

    // 睡到下一次节次边界再重算，避免长时间停留后高亮过期
    var currentSection by remember { mutableIntStateOf(-1) }
    LaunchedEffect(sectionTimes, totalSections) {
        while (true) {
            val now = nowLocalTime()
            val currentMinutes = now.hour * 60 + now.minute
            var result = -1
            var nextTransition = Int.MAX_VALUE
            for (section in 1..totalSections) {
                val timeStr = sectionTimes[section] ?: ""
                if (timeStr.isEmpty()) continue
                val parts = timeStr.split("-")
                if (parts.size != 2) continue
                val startParts = parts[0].split(":")
                val endParts = parts[1].split(":")
                if (startParts.size != 2 || endParts.size != 2) continue
                val startMinutes =
                    (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
                val endMinutes =
                    (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
                if (currentMinutes in startMinutes until endMinutes) {
                    result = section
                }
                if (endMinutes in (currentMinutes + 1)..<nextTransition) {
                    nextTransition = endMinutes
                }
                if (startMinutes in (currentMinutes + 1)..<nextTransition) {
                    nextTransition = startMinutes
                }
            }
            if (currentSection != result) currentSection = result
            val sleepMinutes = if (nextTransition == Int.MAX_VALUE) {
                1
            } else {
                (nextTransition - currentMinutes).coerceIn(1, 60)
            }
            delay((sleepMinutes * 60_000L).milliseconds)
        }
    }

    // snapshotFlow 避免 LaunchedEffect(currentPage) 整页重组
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewingWeek = page + 1
        }
    }

    val allDays = (1..7).toList()
    val coursesByDay = remember(courses) {
        allDays.associateWith { dayOfWeek ->
            courses.filter { it.dayOfWeek == dayOfWeek }
                .sortedBy { it.startSection }
        }
    }

    val scheduleRepository = remember {
        CourseRepository.getInstance()
    }
    val currentScheduleId = remember(scheduleRepository, dataVersion) {
        scheduleRepository.getCurrentScheduleId()
    }
    val semesterStartDate = remember(dataVersion, classStartTime) {
        runCatching {
            LocalDate.parse(classStartTime.replace("/", "-"))
        }.getOrNull() ?: todayLocalDate()
    }
    val semesterStartMonday =
        semesterStartDate.minusDays((semesterStartDate.dayOfWeek.isoDayNumber - 1).toLong())
    val teachingWeekReorganizations = remember(
        scheduleRepository,
        currentScheduleId,
        dataVersion,
        classStartTime,
    ) {
        scheduleRepository.getTeachingWeekReorganizations(currentScheduleId)
    }
    val dateForTeachingPosition: (Int, Int) -> LocalDate? = remember(
        semesterStartDate,
        teachingWeekReorganizations,
    ) {
        { teachingWeek, weekday ->
            TeachingWeekReorganization.dateForPosition(
                semesterStartDate,
                teachingWeek,
                weekday,
                teachingWeekReorganizations,
            )
        }
    }
    val semesterLastDate = dateForTeachingPosition(totalWeeks, 7)
        ?: semesterStartMonday.plusWeeks((totalWeeks - 1).toLong()).plusDays(6)

    /**
     * 调休条目 → 该课表下的 (周次, 星期)。库存的是绝对日期，这里按当前课表的学期
     * 开始时间现算，切换课表后同一天会算出不同周次，调休列自动跟随那一周的课。
     */
    val followPositionFor: (HolidayEntry) -> Pair<Int, Int>? = remember(
        semesterStartDate,
        teachingWeekReorganizations,
        currentScheduleId,
    ) {
        { swap ->
            scheduleRepository.resolveWorkSwapFollow(swap, currentScheduleId)
                ?.let { it.week to it.weekday }
        }
    }

    // 记忆化版本号：假期编辑返回 bump dataVersion 时才重读 SP
    val holidayVersion = remember(dataVersion, holidayDataRevision) {
        HolidayManager.getVersion()
    }
    val holidayEntriesByYear = remember(
        dataVersion,
        holidayDataRevision,
        holidayVersion,
    ) {
        HolidayManager.loadAllByYear()
    }
    val holidayEntriesByDate = remember(
        holidayEntriesByYear,
        semesterStartMonday,
        semesterLastDate,
    ) {
        HolidayManager.entriesByDateRange(
            holidayEntriesByYear,
            semesterStartMonday.minusDays(1),
            semesterLastDate.plusDays(1),
        )
    }
    val holidayEndCourseExclusion = remember(holidayVersion) {
        HolidayManager.loadEndCourseExclusion()
    }
    val holidayBeforeCourseExclusion = remember(holidayVersion) {
        HolidayManager.loadBeforeCourseExclusion()
    }

    // 日期索引复用提醒解析的同日/同类型优先级，并且只查询当前学期可见日期
    val holidayIndex: Map<String, HolidayEntry> = remember(holidayEntriesByDate) {
        holidayEntriesByDate.mapNotNull { (date, entries) ->
            entries.firstOrNull { it.type == HolidayManager.TYPE_HOLIDAY }?.let { date to it }
        }.toMap()
    }
    val workswapIndex: Map<String, HolidayEntry> = remember(holidayEntriesByDate) {
        holidayEntriesByDate.mapNotNull { (date, entries) ->
            entries.firstOrNull { it.type == HolidayManager.TYPE_WORKSWAP }?.let { date to it }
        }.toMap()
    }

    // 一次算齐全部周，切页 O(1) 查表；智能周末下避免每次换周扫 courses+SP
    val weekendDaysByWeek: Map<Int, Set<Int>> = remember(
        courses, dataVersion, holidayVersion, smartWeekend, totalWeeks,
        semesterStartMonday, workswapIndex, teachingWeekReorganizations,
        followPositionFor,
        holidayEntriesByYear, holidayBeforeCourseExclusion, sectionTimes, totalSections,
        currentScheduleId,
    ) {
        if (!smartWeekend) {
            (1..totalWeeks).associateWith { setOf(6, 7) }
        } else {
            val result = HashMap<Int, Set<Int>>()
            for (week in 1..totalWeeks) {
                if (teachingWeekReorganizations.isNotEmpty()) {
                    result[week] = DayScheduleResolver.effectiveWeekendDays(
                        semesterStartDate, week, teachingWeekReorganizations,
                    ) { date ->
                        DayScheduleResolver.resolveDaySchedule(
                            date = date,
                            repository = scheduleRepository,
                            holidayEntriesByYear = holidayEntriesByYear,
                        ).courses.isNotEmpty()
                    }
                    continue
                }
                val satDate = dateForTeachingPosition(week, 6)?.toString()
                val sunDate = dateForTeachingPosition(week, 7)?.toString()
                fun hasWeekendCourses(dateString: String?, calendarDay: Int): Boolean {
                    val date = dateString?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    if (date != null && HolidayCourseExclusion.isEnabledBeforeHolidayDate(
                            holidayEntriesByYear,
                            date,
                            holidayBeforeCourseExclusion,
                        )
                    ) {
                        return DayScheduleResolver.resolveDaySchedule(
                            date = date,
                            repository = scheduleRepository,
                            holidayEntriesByYear = holidayEntriesByYear,
                        ).courses.isNotEmpty()
                    }
                    val hasWorkSwap = dateString?.let {
                        workswapIndex[it]?.let { swap -> followPositionFor(swap) != null }
                    } == true
                    return courses.any { it.dayOfWeek == calendarDay && it.isActiveInWeek(week) } ||
                            hasWorkSwap
                }

                val satActive = hasWeekendCourses(satDate, 6)
                val sunActive = hasWeekendCourses(sunDate, 7)
                result[week] = buildSet {
                    if (satActive) add(6)
                    if (sunActive) add(7)
                }
            }
            result
        }
    }

    // 数据变化时一次算齐全部周：换周组合只查表，避免新页首帧在 composition 里扫课程/调休
    /** page -> dayOfWeek -> Triple(显示星期, 显示周次, 课程)；调课日显示 follow 星期/周次 */
    val weekFilteredCourses: Map<Int, Map<Int, Triple<Int, Int, List<Course>>>> = remember(
        coursesByDay, showNonCurrentWeek, dataVersion, holidayVersion,
        workswapIndex, semesterStartMonday, totalWeeks, teachingWeekReorganizations,
        followPositionFor,
    ) {
        if (totalWeeks <= 0) emptyMap()
        else HashMap<Int, Map<Int, Triple<Int, Int, List<Course>>>>(totalWeeks).apply {
            for (page in 0 until totalWeeks) {
                val weekForPage = page + 1
                put(page, allDays.associateWith { dayOfWeek ->
                    val dateForDay = dateForTeachingPosition(weekForPage, dayOfWeek)
                        ?: return@associateWith Triple(dayOfWeek, weekForPage, emptyList())
                    val swapForDay = workswapIndex[dateForDay.toString()]
                    val follow = swapForDay?.let { followPositionFor(it) }
                    val displayDay = follow?.second ?: dayOfWeek
                    val displayWeek = follow?.first ?: weekForPage
                    val dayCourses = coursesByDay[displayDay] ?: emptyList()
                    val filtered = if (showNonCurrentWeek) dayCourses
                    else dayCourses.filter { it.isActiveInWeek(displayWeek) }
                    Triple(displayDay, displayWeek, filtered)
                })
            }
        }
    }
    val weekHolidayExemptCourseIds: Map<Int, Map<Int, Set<String>>> = remember(
        coursesByDay,
        weekFilteredCourses,
        holidayEntriesByYear,
        holidayEndCourseExclusion,
        workswapIndex,
        sectionTimes,
        totalSections,
        showNonCurrentWeek,
        semesterStartMonday,
        totalWeeks,
        teachingWeekReorganizations,
        followPositionFor,
    ) {
        if (totalWeeks <= 0 || !holidayEndCourseExclusion.enabled) {
            emptyMap()
        } else {
            HashMap<Int, Map<Int, Set<String>>>(totalWeeks).apply {
                for (page in 0 until totalWeeks) {
                    val weekForPage = page + 1
                    put(page, allDays.associateWith { dayOfWeek ->
                        val dateForDay = dateForTeachingPosition(weekForPage, dayOfWeek)
                            ?: return@associateWith emptySet()
                        if (!HolidayCourseExclusion.isEnabledOnDate(
                                holidayEntriesByYear,
                                dateForDay,
                                holidayEndCourseExclusion,
                            )
                        ) {
                            emptySet()
                        } else {
                            val swapForDay = workswapIndex[dateForDay.toString()]
                            val displayWeek =
                                swapForDay?.let { followPositionFor(it)?.first } ?: weekForPage
                            (weekFilteredCourses[page]?.get(dayOfWeek)?.third ?: emptyList())
                                .asSequence()
                                .filter { it.isActiveInWeek(displayWeek) }
                                .filter {
                                    HolidayCourseExclusion.matchesCourse(
                                        it,
                                        holidayEndCourseExclusion,
                                        sectionTimes,
                                        totalSections,
                                    )
                                }
                                .map { it.id }
                                .toSet()
                        }
                    })
                }
            }
        }
    }
    val weekHolidayCancelledCourseIds: Map<Int, Map<Int, Set<String>>> = remember(
        coursesByDay,
        weekFilteredCourses,
        holidayEntriesByYear,
        holidayBeforeCourseExclusion,
        workswapIndex,
        sectionTimes,
        totalSections,
        showNonCurrentWeek,
        semesterStartMonday,
        totalWeeks,
        teachingWeekReorganizations,
        followPositionFor,
    ) {
        if (totalWeeks <= 0 || !holidayBeforeCourseExclusion.enabled) {
            emptyMap()
        } else {
            HashMap<Int, Map<Int, Set<String>>>(totalWeeks).apply {
                for (page in 0 until totalWeeks) {
                    val weekForPage = page + 1
                    put(page, allDays.associateWith { dayOfWeek ->
                        val dateForDay = dateForTeachingPosition(weekForPage, dayOfWeek)
                            ?: return@associateWith emptySet()
                        if (!HolidayCourseExclusion.isEnabledBeforeHolidayDate(
                                holidayEntriesByYear,
                                dateForDay,
                                holidayBeforeCourseExclusion,
                            )
                        ) {
                            emptySet()
                        } else {
                            val swapForDay = workswapIndex[dateForDay.toString()]
                            val displayWeek =
                                swapForDay?.let { followPositionFor(it)?.first } ?: weekForPage
                            HolidayCourseExclusion.cancelledCourseIdsOnDate(
                                entriesByYear = holidayEntriesByYear,
                                date = dateForDay,
                                exclusion = holidayBeforeCourseExclusion,
                                candidates = weekFilteredCourses[page]?.get(dayOfWeek)?.third
                                    ?: emptyList(),
                                displayWeek = displayWeek,
                                sectionTimes = sectionTimes,
                                sectionCount = totalSections,
                            )
                        }
                    })
                }
            }
        }
    }
    // page -> dayOfWeek -> (isHoliday, isWorkSwap)，同样预计算
    val weekDayFlags: Map<Int, Map<Int, Pair<Boolean, Boolean>>> = remember(
        semesterStartMonday, holidayIndex, workswapIndex, totalWeeks, teachingWeekReorganizations,
        followPositionFor,
    ) {
        if (totalWeeks <= 0) emptyMap()
        else HashMap<Int, Map<Int, Pair<Boolean, Boolean>>>(totalWeeks).apply {
            for (page in 0 until totalWeeks) {
                val weekForPage = page + 1
                put(page, allDays.associateWith { dayOfWeek ->
                    val dateForDay = dateForTeachingPosition(weekForPage, dayOfWeek)
                        ?: return@associateWith (false to false)
                    val isHoliday = holidayIndex[dateForDay.toString()] != null
                    val isWorkSwap = workswapIndex[dateForDay.toString()]
                        ?.let { followPositionFor(it) != null } ?: false
                    isHoliday to isWorkSwap
                })
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

    // 顶层读一次主题，避免每列/分界带各自挂 prefs 监听
    val scheduleIsDark = isAppDarkTheme()
    // 无壁纸：网格里的课程卡与长按浮层垫同一份页底色；有壁纸为 null（玻璃层自己盖住）
    val cardSolidBacking = courseCardSolidBacking(
        isDark = scheduleIsDark,
        hasWallpaper = wallpaperBitmap != null
    )

    // 壁纸像素由主 pager 后的共享层绘制（[useSharedWallpaper]）；本页只负责透明叠上。
    // 没有共享层（无壁纸 / 非共享模式）时本页自己垫页底色。

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!useSharedWallpaper) {
                    Modifier.background(schedulePageBackgroundColor(scheduleIsDark))
                } else {
                    Modifier
                }
            )
    ) {
            // rememberUpdatedState：pointerInput(Unit) 读到最新值，避免捕获陈旧状态
            val latestWallpaperScale by rememberUpdatedState(wallpaperScale)
            val latestWallpaperOffset by rememberUpdatedState(wallpaperOffset)
            val latestOnScaleChange by rememberUpdatedState(onWallpaperScaleChange)
            val latestOnOffsetChange by rememberUpdatedState(onWallpaperOffsetChange)
            val latestMinWallpaperScale by rememberUpdatedState(minWallpaperScale)
            val latestWallpaperBitmap by rememberUpdatedState(wallpaperBitmap)
            val latestScreenWidthPx by rememberUpdatedState(screenWidthPx)
            val latestScreenHeightPx by rememberUpdatedState(screenHeightPx)

            // 指针作用域内无法直接 animate，经状态触发回弹
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


            // 预组合策略全在 WeekPagerHost 内（滚动收拢到 1 / 停稳 2 / 边缘驻留 3），
            // beyond 翻转只重组 Host，不重建页内容 lambda。
            WeekPagerHost(
                state = pagerState,
                dwellHint = edgeDwellHint,
                modifier = Modifier
                    .fillMaxSize()
                    // 页面级多指统计：多指（如三指下滑截图）时抑制卡片点击/长按
                    .pointerInput(touchState) {
                        awaitPointerEventScope {
                            var active = false
                            while (true) {
                                val downCount = awaitPointerEvent(PointerEventPass.Initial)
                                    .changes.count { it.pressed }
                                if (downCount == 0) {
                                    // 全部抬起：不清零，让最后一帧仍能被卡片读到「本手势是多指」
                                    active = false
                                } else {
                                    if (!active) touchState.multiTouch = false
                                    if (downCount > 1) touchState.multiTouch = true
                                    active = true
                                }
                            }
                        }
                    }
                    // 横竖主导手势（与今日页共用）：横向接管 pager，纵向仍归页内滚动
                    .pagerAxisTakeoverGesture(
                        pagerState = pagerState,
                        gestureState = pagerGestureState,
                        // 拖课卡那根手指由 blockedPointer 屏蔽；整页禁用会连「另一只手翻周」一起挡掉
                        blockGesture = { latestIsWallpaperEditing },
                        overscrollXState = weekOverscrollX,
                        blockedPointer = { touchState.draggingPointerId },
                        // 第二只手纵向滑动直接滚屏（verticalScroll 在跟被屏蔽那根手指）
                        onVerticalScroll = { dy -> externalScrollState.dispatchRawDelta(-dy) },
                    )
                    // 跨周调课：跟手位移与收尾放在 pager 上。课卡所在周页翻过去会被回收，
                    // 卡片自己的 pointerInput 跟着取消；pager 不重建，拖到第几周都能继续跟手。
                    .onGloballyPositioned { dragOwnerCoords[0] = it }
                    .courseDragOwnerGesture(
                        touchState = touchState,
                        coordinates = dragOwnerCoords,
                        onDrag = { id, dx, dy -> latestOnCourseDrag.value(id, dx, dy) },
                        onDragEnd = { id -> latestOnCourseDragEnd.value(id) },
                        onDragCancel = { id -> latestOnCourseDragCancel.value(id) },
                    ),
            ) { physicalPage ->
                // 借位页：长距跳转期间这一页临时按目标周出内容
                val page = jumpNeighbor?.resolve(physicalPage) ?: physicalPage
                val week = page + 1
                // 借位页是「同一页换数据」而不是值更新：整屏文字直接换，不播交叉模糊。
                // 否则一屏卡片的字各糊一遍 460ms，看起来像整页重播入场动画。
                val borrowedPage = jumpNeighbor?.isBorrowed(physicalPage) == true
                CompositionLocalProvider(LocalTextMotionInstant provides borrowedPage) {
                    // 本周显示哪几天（周末只看有没有课）：网格与时间轴共用，顶栏星期行的列数也依赖它
                    val pageDayRange = remember(weekendDaysByWeek, week) {
                        (1..5).toList() + (weekendDaysByWeek[week] ?: emptySet())
                            .filter { it in 6..7 }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 柔光采样源整页只算一份；页内所有卡片/分界带/落点遮罩共用
                        CompositionLocalProvider(
                            LocalSoftGlassSurface provides softGlassSurface,
                            LocalGlassRedrawSignal provides glassRedrawSignal,
                            LocalOverScrollState provides overScrollState,
                        ) {
                            // 时间轴视图：同一个 pager 页，把「节次」纵轴换成真实时间纵轴。
                            // 早退而不是 if/else 包住下面整段网格：条件分支的 group 处理与 if/else 等价。
                            if (scheduleViewMode == ScheduleViewMode.TIMELINE) {
                                // 空白处点出的待添加槽位（只在本页有效）
                                var pendingAdd by remember(page) {
                                    mutableStateOf<TimelinePendingAdd?>(null)
                                }
                                val timelineDays = pageDayRange.map { day ->
                                    val info = weekFilteredCourses[page]?.get(day)
                                    val flags = weekDayFlags[page]?.get(day) ?: (false to false)
                                    TimelineDay(
                                        day = day,
                                        displayWeek = info?.second ?: week,
                                        displayDay = info?.first ?: day,
                                        courses = info?.third.orEmpty(),
                                        cancelledIds = weekHolidayCancelledCourseIds[page]
                                            ?.get(day).orEmpty(),
                                        exemptIds = weekHolidayExemptCourseIds[page]
                                            ?.get(day).orEmpty(),
                                        isHoliday = flags.first,
                                        isWorkSwap = flags.second,
                                    )
                                }
                                TimelineWeekGrid(
                                    days = timelineDays,
                                    sectionTimes = sectionTimes,
                                    isTablet = isTablet,
                                    isCurrentWeekView = week == currentWeek,
                                    style = TimelineCardStyle(
                                        isDark = scheduleIsDark,
                                        softGlassSurface = softGlassSurface,
                                        cardBlurRadius = cardBlurRadius,
                                        cardAlpha = cardAlpha,
                                        cardSurfaceAlpha = cardSurfaceAlpha,
                                        solidBacking = cardSolidBacking,
                                        cardHeightPerSection = cardHeightPerSection,
                                        cardCornerRadius = cardCornerRadius,
                                        cardContentAlignment = cardContentAlignment,
                                        cardTextColor = cardTextColor,
                                        cardTextScale = cardTextScale,
                                        showClassroom = showClassroom,
                                        showTeacher = showTeacher,
                                        cardRefraction = cardRefraction,
                                    ),
                                    contentTopPadding = contentTopPaddingDp,
                                    contentBottomPadding = scheduleEndSpacer,
                                    scrollState = externalScrollState,
                                    scrollBehavior = scheduleScrollBehavior,
                                    pendingAdd = pendingAdd,
                                    // 点空白：先立一张加号卡片（上下中心落在点下的那一分钟）
                                    onEmptyTap = { day, minute ->
                                        if (dateForTeachingPosition(week, day.day) != null) {
                                            val section = sectionAtMinute(sectionTimes, minute)
                                            pendingAdd = TimelinePendingAdd(
                                                day = day.day,
                                                centerMinute = minute,
                                                durationMinutes = sectionMinutesOf(sectionTimes, section),
                                            )
                                        }
                                    },
                                    // 点加号卡片：按自定义时间新建，起止就是卡片覆盖的那一段
                                    onPendingAddClick = { day, startMinute, durationMinutes ->
                                        val section = sectionAtMinute(sectionTimes, startMinute)
                                        pendingAdd = null
                                        viewModel.showAddDialog(
                                            dayOfWeek = if (day.isWorkSwap) day.displayDay else day.day,
                                            startSection = section,
                                            endSection = section,
                                            defaultWeeks = if (day.isWorkSwap) {
                                                setOf(day.displayWeek)
                                            } else {
                                                emptySet()
                                            },
                                            customStartTime = minuteText(startMinute),
                                            customEndTime = minuteText(
                                                (startMinute + durationMinutes) % (24 * 60)
                                            ),
                                        )
                                    },
                                    // 长按：起浮层 + 菜单（拖不动、不给复制），与网格自定义时间课同一套
                                    onCourseLongPress = { course, cx, cy, w, h, displayWeek ->
                                        onCourseLongPress(course, cx, cy, w, h, displayWeek)
                                    },
                                    // 浮层期间隐藏原卡
                                    hiddenSlots = draggingCourseSlots,
                                    // 与网格卡片同一入口：调休列用该列映射后的周次查槽位
                                    onCourseClick = { course, displayWeek ->
                                        val coursesAtSlot = viewModel.getCoursesAtSlot(
                                            displayWeek,
                                            course.dayOfWeek,
                                            course.startSection,
                                            course.endSection
                                        )
                                        selectedCourses = coursesAtSlot
                                        selectedCourse = coursesAtSlot.find { it.id == course.id }
                                            ?: course
                                        showCourseDetail = true
                                        onPopupStateChange(true)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    glassBehindCards = { _ ->
                                        // 页级玻璃已下线：柔光玻璃由每张卡自己画（见上面说明）
                                    },
                                )
                                return@CompositionLocalProvider
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (scheduleScrollBehavior != null) Modifier.nestedScroll(
                                            scheduleScrollBehavior.nestedScrollConnection
                                        )
                                        else Modifier
                                    )
                                    // overScroll 必须在 verticalScroll 外侧，作为滚动容器的 parent 才能收到 fling
                                    .overScrollVertical()
                                    .scrollEndHaptic(
                                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                                    )
                                    .verticalScroll(externalScrollState)
                                    // 底部留白直接计入滚动内容高度；强制无界测量，避免 fillMaxSize/测量链把内容压回视口高
                                    .layout { measurable, constraints ->
                                        val topPad =
                                            contentTopPaddingDp.roundToPx().coerceAtLeast(0)
                                        val bottomPad =
                                            scheduleEndSpacer.roundToPx().coerceAtLeast(0)
                                        val placeable = measurable.measure(
                                            constraints.copy(
                                                minHeight = 0,
                                                maxHeight = Constraints.Infinity
                                            )
                                        )
                                        layout(
                                            placeable.width,
                                            placeable.height + topPad + bottomPad
                                        ) {
                                            placeable.place(0, topPad)
                                        }
                                    }
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    val dayBoundsArray = remember { arrayOfNulls<FloatArray>(8) }
                                    var lastDayBoundsVersion by remember { mutableIntStateOf(0) }
                                    val isScheduleScrolling by scheduleScrollInProgress
                                    LaunchedEffect(isScheduleScrolling) {
                                        // 停滑后冲刷：滑动中 onGloballyPositioned 只写数组不递增版本号
                                        if (!isScheduleScrolling) lastDayBoundsVersion++
                                    }
                                    // 用当前（始终实时写入的）dayBounds 数组拼几何。
                                    // 长按瞬间同步推一次，避免“停滑冲刷”晚于长按导致上层拿到旧页 dayBounds（菜单错位/浮层消失）
                                    fun buildGridGeometry(): ScheduleGridGeometry {
                                        val boundsMap = mutableMapOf<Int, FloatArray>()
                                        val dataMap = mutableMapOf<Int, Pair<Int, Int>>()
                                        val blocked = mutableSetOf<Int>()
                                        for (i in 1..7) {
                                            dayBoundsArray[i]?.let { boundsMap[i] = it }
                                            weekFilteredCourses[page]?.get(i)?.let { info ->
                                                dataMap[i] = info.first to info.second
                                            }
                                            // 教学周重组休课日：dateForPosition 返回 null，该列永远空白
                                            if (dateForTeachingPosition(
                                                    week,
                                                    i
                                                ) == null
                                            ) blocked.add(i)
                                        }
                                        return ScheduleGridGeometry(
                                            dayBounds = boundsMap,
                                            sectionHeightPx = with(density) { cardHeightPerSection.dp.toPx() },
                                            morningSections = morningSections,
                                            afternoonSections = afternoonSections,
                                            eveningSections = eveningSections,
                                            showBreakDividers = showBreakDividers,
                                            columnDataPosition = dataMap,
                                            pageWeek = week,
                                            blockedColumns = blocked,
                                            // 节次顶的权威来源：含特殊块挤占，浮层/落点定位都依赖它
                                            sectionTopDp = specialGrid.sectionTop,
                                        )
                                    }

                                    val pushFreshGeometry by rememberUpdatedState {
                                        onGridGeometryChange(buildGridGeometry())
                                    }
                                    // 提升到 Row 之外：特殊横带按同一套列宽切分内部星期子块
                                    // 每个可见列实际显示的星期（调休列画的是被调星期的课）；横带子块必须
                                    // 跟着它走，与下面 DayColumn 的 displayDayForCol 同源，别再拿列下标当星期。
                                    val pageColumnDays =
                                        remember(pageDayRange, weekFilteredCourses, page) {
                                            pageDayRange.map { col ->
                                                weekFilteredCourses[page]?.get(col)?.first ?: col
                                            }
                                        }
                                    val bandStartPadding =
                                        (if (isTablet) 24.dp else 0.dp) + scheduleSectionColumnWidth(
                                            isTablet
                                        )
                                    val bandEndPadding = if (isTablet) 24.dp else 2.dp
                                    // 特殊课程横带：作为 Row 下层背景条带，起止时间由左侧时间列标注
                                    specialGrid.specialBands.forEach { band ->
                                        val bandItems = remember(specialBlocks, band.blockId) {
                                            specialBlocks.firstOrNull { it.id == band.blockId }?.safeItems
                                                ?: emptyList()
                                        }
                                        // 只覆盖周一~周日列，不盖左侧时间轴
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(band.height.dp)
                                                .offset(y = band.top.dp)
                                                // end 与下方 Row 的 end padding 一致，子块才能与 DayColumn 逐列对齐
                                                .padding(
                                                    start = bandStartPadding,
                                                    end = bandEndPadding
                                                )
                                        ) {
                                            SpecialBandOverlay(
                                                name = band.name,
                                                startTime = band.startTime,
                                                endTime = band.endTime,
                                                hasBlur = wallpaperBitmap != null,
                                                isDark = scheduleIsDark,
                                                cardCornerRadius = cardCornerRadius,
                                                cardBlurRadius = cardBlurRadius,
                                                cardAlpha = cardAlpha,
                                                cardSurfaceAlpha = cardSurfaceAlpha,
                                                cardRefraction = cardRefraction,
                                                isTablet = isTablet,
                                                softGlassSurface = softGlassSurface,

                                                items = bandItems,
                                                dayRange = pageColumnDays
                                            )
                                        }
                                    }

                                    // 调课落点示意：跨列/节连续位移，出现从源格滑入，消失仅透明度
                                    AnimatedDropTargetMask(
                                        dropHighlight = dropHighlight,
                                        dropHighlightOrigin = dropHighlightOrigin,
                                        pageDayRange = pageDayRange,
                                        grid = specialGrid,
                                        cardHeightPerSection = cardHeightPerSection,
                                        cardCornerRadius = cardCornerRadius,
                                        softGlassSurface = softGlassSurface,
                                        isTablet = isTablet,
                                        isDark = scheduleIsDark,
                                        hasWallpaper = wallpaperBitmap != null,
                                        cardBlurRadius = cardBlurRadius,
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (isTablet) Modifier.padding(horizontal = 24.dp) else Modifier.padding(
                                                    end = 2.dp
                                                )
                                            )
                                    ) {
                                        SectionColumn(
                                            totalSections = totalSections,
                                            morningSections = morningSections,
                                            afternoonSections = afternoonSections,
                                            eveningSections = eveningSections,
                                            sectionTimes = sectionTimes,
                                            sectionNames = sectionNames,
                                            specialBlocks = specialBlocks,
                                            grid = specialGrid,
                                            cardHeightPerSection = cardHeightPerSection,
                                            showBreakDividers = showBreakDividers,
                                            currentSection = if (week == currentWeek) currentSection else -1,
                                            isTablet = isTablet
                                        )

                                        pageDayRange.forEach { dayOfWeek ->
                                            val (displayDayForCol, displayWeekForDay, filteredDayCourses) =
                                                weekFilteredCourses[page]?.get(dayOfWeek)
                                                    ?: Triple(dayOfWeek, week, emptyList())
                                            val dayFlags = weekDayFlags[page]?.get(dayOfWeek)
                                                ?: (false to false)
                                            val isHoliday = dayFlags.first
                                            val isWorkSwap = dayFlags.second
                                            val holidayExemptCourseIds =
                                                weekHolidayExemptCourseIds[page]?.get(dayOfWeek)
                                                    .orEmpty()
                                            val holidayCancelledCourseIds =
                                                weekHolidayCancelledCourseIds[page]?.get(dayOfWeek)
                                                    .orEmpty()
                                            // 调课日空白格添加：落到 follow 星期/周（周五课调到周二 → 点周二默认周五）
                                            val addDayForCol =
                                                if (isWorkSwap) displayDayForCol else dayOfWeek
                                            val addWeekForCol =
                                                if (isWorkSwap) displayWeekForDay else -1
                                            val stableOnCourseClick: (Course) -> Unit =
                                                remember(page, dayOfWeek, week, displayWeekForDay) {
                                                    { course ->
                                                        // 调休日用被调星期+映射周次查槽位，避免弹出原始课程
                                                        val coursesAtSlot =
                                                            viewModel.getCoursesAtSlot(
                                                                displayWeekForDay,
                                                                course.dayOfWeek,
                                                                course.startSection,
                                                                course.endSection
                                                            )
                                                        selectedCourses = coursesAtSlot
                                                        selectedCourse =
                                                            coursesAtSlot.find { it.id == course.id }
                                                                ?: course
                                                        showCourseDetail = true
                                                        onPopupStateChange(true)
                                                    }
                                                }
                                            // 休课日列永远渲染为空：加/粘的课在课表与今日页都看不见。键里带
                                            // dateForTeachingPosition（随重组数据重建），否则改完重组回主页会沿用旧判断
                                            val pausedColumn =
                                                remember(page, dayOfWeek, dateForTeachingPosition) {
                                                    dateForTeachingPosition(week, dayOfWeek) == null
                                                }
                                            val stableOnEmptyClick: (Int) -> Unit =
                                                remember(
                                                    dayOfWeek,
                                                    addDayForCol,
                                                    addWeekForCol,
                                                    pausedColumn
                                                ) {
                                                    { section ->
                                                        if (!pausedColumn) {
                                                            val defaultWeeks =
                                                                if (addWeekForCol > 0) setOf(
                                                                    addWeekForCol
                                                                ) else emptySet()
                                                            viewModel.showAddDialog(
                                                                addDayForCol,
                                                                section,
                                                                null,
                                                                defaultWeeks
                                                            )
                                                        }
                                                    }
                                                }
                                            val stableOnEmptyLongPress: (Int, Float, Float, Float, Float) -> Unit =
                                                remember(
                                                    dayOfWeek,
                                                    addDayForCol,
                                                    addWeekForCol,
                                                    onEmptyLongPress,
                                                    pausedColumn
                                                ) {
                                                    { section, centerX, cellTopY, width, height ->
                                                        if (!pausedColumn) {
                                                            // 长按进菜单时清掉 pending，避免两层交互叠加
                                                            pendingDay = -1
                                                            pendingSection = -1
                                                            onEmptyLongPress(
                                                                dayOfWeek,
                                                                section,
                                                                centerX,
                                                                cellTopY,
                                                                width,
                                                                height,
                                                                addDayForCol,
                                                                addWeekForCol,
                                                            )
                                                        }
                                                    }
                                                }
                                            val stableOnCourseLongPress: (Course, Float, Float, Float, Float, Int) -> Unit =
                                                remember(page, dayOfWeek) {
                                                    { course, left, top, width, height, cWeek ->
                                                        // 同步补一次最新几何，保证上层锚点用当前页 dayBounds
                                                        pushFreshGeometry()
                                                        onCourseLongPress(
                                                            course,
                                                            left,
                                                            top,
                                                            width,
                                                            height,
                                                            cWeek
                                                        )
                                                    }
                                                }
                                            DayColumn(
                                                dayOfWeek = dayOfWeek,
                                                touchState = touchState,
                                                courses = filteredDayCourses,
                                                onCourseClick = stableOnCourseClick,
                                                onCourseLongPress = stableOnCourseLongPress,
                                                onEmptyClick = stableOnEmptyClick,
                                                onEmptyLongPress = stableOnEmptyLongPress,
                                                morningSections = morningSections,
                                                afternoonSections = afternoonSections,
                                                eveningSections = eveningSections,
                                                sectionTimes = sectionTimes,
                                                specialBlocks = specialBlocks,
                                                grid = specialGrid,
                                                currentWeek = displayWeekForDay,
                                                isHoliday = isHoliday,
                                                holidayExemptCourseIds = holidayExemptCourseIds,
                                                holidayCancelledCourseIds = holidayCancelledCourseIds,
                                                isWorkSwap = isWorkSwap,
                                                pendingDay = pendingDay,
                                                pendingSection = pendingSection,
                                                onPendingChange = onPendingChange,
                                                softGlassSurface = softGlassSurface,

                                                solidBackingColor = cardSolidBacking,
                                                cardBlurRadius = cardBlurRadius,
                                                cardAlpha = cardAlpha,
                                                cardSurfaceAlpha = cardSurfaceAlpha,
                                                cardHeightPerSection = cardHeightPerSection,
                                                cardCornerRadius = cardCornerRadius,
                                                showBreakDividers = showBreakDividers,
                                                isTablet = isTablet,
                                                cardContentAlignment = cardContentAlignment,
                                                cardTextColor = cardTextColor,
                                                cardTextScale = cardTextScale,
                                                showClassroom = showClassroom,
                                                showTeacher = showTeacher,
                                                cardRefraction = cardRefraction,
                                                draggingCourseSlots = draggingCourseSlots,
                                                onCourseDragStart = onCourseDragStart,
                                                onCourseDrag = onCourseDrag,
                                                onCourseDragEnd = onCourseDragEnd,
                                                onCourseDragCancel = onCourseDragCancel,
                                                onCourseMenuDismiss = onCourseMenuDismiss,
                                                isDark = scheduleIsDark,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .onGloballyPositioned { coordinates ->
                                                        val pos = coordinates.positionInRoot()
                                                        val w = coordinates.size.width.toFloat()
                                                        val arr = dayBoundsArray[dayOfWeek]
                                                        // 滑动中只写数组不递增版本号，避免逐帧重组；停后由 LaunchedEffect 冲刷
                                                        if (arr == null) {
                                                            dayBoundsArray[dayOfWeek] =
                                                                floatArrayOf(
                                                                    pos.x,
                                                                    pos.x + w,
                                                                    pos.y
                                                                )
                                                            if (!touchState.scrolling) lastDayBoundsVersion++
                                                        } else if (arr[0] != pos.x || arr[1] != pos.x + w || arr[2] != pos.y) {
                                                            arr[0] = pos.x
                                                            arr[1] = pos.x + w
                                                            arr[2] = pos.y
                                                            if (!touchState.scrolling) lastDayBoundsVersion++
                                                        }
                                                    }
                                            )
                                        }
                                    }

                                    // 点击层必须在 Row 之上：空节次层会消费整列点击
                                    specialGrid.specialBands.forEach { band ->
                                        val bandItems = remember(specialBlocks, band.blockId) {
                                            specialBlocks.firstOrNull { it.id == band.blockId }?.safeItems
                                                ?: emptyList()
                                        }
                                        // 壁纸编辑态手势归编辑用，横带不再抢点击（否则误触会弹出「添加安排」）
                                        if (isWallpaperEditing) return@forEach
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(band.height.dp)
                                                .offset(y = band.top.dp)
                                                .padding(
                                                    start = bandStartPadding,
                                                    end = bandEndPadding
                                                )
                                        ) {
                                            SpecialBandClickLayer(
                                                items = bandItems,
                                                dayRange = pageColumnDays,
                                                onItemClick = { item ->
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.Confirm
                                                    )
                                                    specialItemEditingBlockId = band.blockId
                                                    specialItemEditingId = item.id
                                                    specialItemName = item.name
                                                    specialItemSelectedDays =
                                                        (item.startDay..item.endDay).toSet()
                                                    showSpecialItemDialog = true
                                                },
                                                onEmptyClick = { day ->
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.Confirm
                                                    )
                                                    specialItemEditingBlockId = band.blockId
                                                    specialItemEditingId = -1L
                                                    specialItemName = ""
                                                    specialItemSelectedDays = setOf(day)
                                                    showSpecialItemDialog = true
                                                }
                                            )
                                        }
                                    }

                                    // 仅当前页上报；几何实际变化时才触发回调，避免每次重组分配新对象
                                    val sectionHeightPx =
                                        with(density) { cardHeightPerSection.dp.toPx() }
                                    val prevBoundsVersion =
                                        remember { mutableIntStateOf(lastDayBoundsVersion) }
                                    val prevSectionHeight =
                                        remember { mutableFloatStateOf(sectionHeightPx) }
                                    val prevMorning =
                                        remember { mutableIntStateOf(morningSections) }
                                    val prevAfternoon =
                                        remember { mutableIntStateOf(afternoonSections) }
                                    val prevEvening =
                                        remember { mutableIntStateOf(eveningSections) }
                                    val prevShowBreak =
                                        remember { mutableStateOf(showBreakDividers) }
                                    SideEffect {
                                        if (page == pagerState.currentPage) {
                                            val changed =
                                                prevBoundsVersion.intValue != lastDayBoundsVersion
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
                                                onGridGeometryChange(buildGridGeometry())
                                            }
                                        }
                                    }
                                    // 跨周调课：翻周停下后补推一次几何。上面的 SideEffect 只在组合重跑时才推，
                                    // 而翻页停下不一定伴随重组 —— 落点会一直用上一页的周次映射，课写进错的周。
                                    LaunchedEffect(Unit) {
                                        snapshotFlow { pagerState.isScrollInProgress }
                                            .collect { scrolling ->
                                                if (!scrolling && page == pagerState.currentPage) {
                                                    onGridGeometryChange(buildGridGeometry())
                                                }
                                            }
                                    }

                                    val morningHeight = specialGrid.dividerY.getOrNull(0)?.toInt()
                                        ?: (morningSections * cardHeightPerSection).toInt()
                                    val afternoonHeight =
                                        (afternoonSections * cardHeightPerSection).toInt()
                                    val dividerOffset = if (showBreakDividers) 24 else 0
                                    val dinnerBreakY = specialGrid.dividerY.getOrNull(1)?.toInt()
                                        ?: (morningHeight + dividerOffset + afternoonHeight)

                                    if (showBreakDividers) {
                                        // 分界带净高 20dp（24dp − 上下各 2dp padding）：圆角必须 ≤ 10dp，
                                        // 否则 ContinuousRoundedRectangle 在短边超限后会解出尖角。
                                        val dividerCornerRadius = 10.dp
                                        val dividerShape = ContinuousRoundedRectangle(dividerCornerRadius)
                                        val dividerHorizontalPadding = if (isTablet) 24.dp else 4.dp
                                        val dividerDensity = LocalDensity.current
                                        val hasWallpaperDivider = wallpaperBitmap != null
                                        val dividerBaseColor = when {
                                            hasWallpaperDivider -> Color.Transparent
                                            MiuixTheme.isDynamicColor -> MiuixTheme.colorScheme.surfaceContainerHigh
                                            scheduleIsDark -> Color(0xFF121212)
                                            else -> Color(0xFFF0F0F0)
                                        }
                                        // 12% 锚定当前视觉；百分比映射到 0..1（仅作用于有壁纸玻璃表面）
                                        val dividerSurfaceAlphaAt12 =
                                            if (scheduleIsDark) 0.64f else 0.50f
                                        val dividerBlurShape =
                                            remember { ContinuousRoundedRectangle(dividerCornerRadius) }

                                        @Composable
                                        fun BreakDivider(offsetY: Int, text: String) {
                                            // 0%→0，12%→当前，100%→1；两段线性映射（仅作用于有壁纸玻璃表面）
                                            val p = cardSurfaceAlpha.coerceIn(0f, 1f)
                                            val surfaceAlpha = if (p <= 0.12f) {
                                                (p / 0.12f) * dividerSurfaceAlphaAt12
                                            } else {
                                                dividerSurfaceAlphaAt12 +
                                                        ((p - 0.12f) / 0.88f) * (1f - dividerSurfaceAlphaAt12)
                                            }
                                            // 无壁纸底色保持实色，不随「卡片不透明度」变化
                                            val dividerFgBase = dividerBaseColor
                                            val dividerFgSurface = if (MiuixTheme.isDynamicColor) {
                                                MiuixTheme.colorScheme.surfaceContainerHigh.copy(
                                                    alpha = surfaceAlpha
                                                )
                                            } else if (scheduleIsDark) {
                                                Color(0xFF323232).copy(alpha = surfaceAlpha)
                                            } else {
                                                Color.White.copy(alpha = surfaceAlpha)
                                            }
                                            // 柔光玻璃：分界带也是整页材质的一部分，采样原点 = 窗口坐标。
                                            // 宽度很大、高度只有 20dp，shader 的倒角带会吃掉上下两条边，
                                            // 但正好落在 2dp 内边距里，肉眼看到的是中间的柔光条。
                                            val dividerSoftOrigin = remember { arrayOf(Offset.Zero) }
                                            val dividerRadiusPx = remember(dividerDensity) {
                                                with(dividerDensity) { dividerCornerRadius.toPx() }
                                            }
                                            // remember：重组时不重建 Modifier（否则 AGSL 重编译）
                                            val glassRedraw = LocalGlassRedrawSignal.current
                                            val dividerSoftModifier = remember(
                                                softGlassSurface,
                                                dividerRadiusPx,
                                                dividerDensity,
                                                glassRedraw
                                            ) {
                                                softGlassSurface?.modifier(
                                                    density = dividerDensity,
                                                    radiusPx = dividerRadiusPx,
                                                    origin = { dividerSoftOrigin[0] },
                                                    redraw = glassRedraw,
                                                )
                                            }
                                            Box(
                                                modifier = Modifier.fillMaxWidth()
                                                    .offset(y = offsetY.dp)
                                                    .height(24.dp)
                                                    .padding(vertical = 2.dp)
                                                    .padding(horizontal = dividerHorizontalPadding)
                                                    .onGloballyPositioned {
                                                        dividerSoftOrigin[0] = it.positionInWindow()
                                                    }
                                                    .background(dividerFgBase, dividerShape)
                                                    .then(
                                                        if (hasWallpaperDivider && dividerSoftModifier != null) {
                                                            Modifier
                                                                .then(dividerSoftModifier)
                                                                .background(dividerFgSurface, dividerBlurShape)
                                                        } else if (hasWallpaperDivider) {
                                                            // 非 Android / 无柔光实现：半透明表面
                                                            Modifier.background(
                                                                dividerFgSurface,
                                                                dividerBlurShape
                                                            )
                                                        } else {
                                                            Modifier
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = text,
                                                    style = MiuixTheme.textStyles.footnote2,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        }
                                        BreakDivider(morningHeight, SchedStr.lunchBreak())
                                        BreakDivider(dinnerBreakY, SchedStr.dinnerBreak())
                                    }
                                }
                            }
                        }
                    }
                }

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
                                        val newScale = if (gestureScale < latestMinWallpaperScale) {
                                            val diff = gestureScale - latestMinWallpaperScale
                                            latestMinWallpaperScale + diff * 0.3f
                                        } else {
                                            gestureScale
                                        }
                                        lastDisplayScale = newScale
                                        val bmp = latestWallpaperBitmap
                                        if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                                            val fitScale = minOf(
                                                latestScreenWidthPx / bmp.width,
                                                latestScreenHeightPx / bmp.height
                                            )
                                            val scaledW = bmp.width * fitScale * newScale
                                            val scaledH = bmp.height * fitScale * newScale
                                            val maxOffsetX =
                                                ((scaledW - latestScreenWidthPx) / 2f).coerceAtLeast(
                                                    0f
                                                )
                                            val maxOffsetY =
                                                ((scaledH - latestScreenHeightPx) / 2f).coerceAtLeast(
                                                    0f
                                                )
                                            val newOffset = latestWallpaperOffset + pan
                                            latestOnScaleChange(newScale)
                                            latestOnOffsetChange(
                                                Offset(
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
                                    gestureEndScale = lastDisplayScale
                                    bounceBackTrigger++
                                }
                            }
                    )
                }
            }

            val detailEndAction: @Composable () -> Unit = {
                val scope = rememberCoroutineScope()
                val material = LocalSheetTopBarMaterial.current
                LiquidTopBarButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        val course = selectedCourse ?: selectedCourses.firstOrNull()
                        showCourseDetail = false
                        // 等关闭动画后再开添加弹窗
                        scope.launch {
                            delay(100.milliseconds)
                            if (course != null) {
                                viewModel.showAddDialog(
                                    course.dayOfWeek,
                                    course.startSection,
                                    course.endSection
                                )
                            } else {
                                viewModel.showAddDialog()
                            }
                        }
                    },
                    backdrop = LocalSheetContentBackdrop.current ?: liquidGlassBackdrop!!,
                    icon = MiuixIcons.Add,
                    contentDescription = SchedStr.addCourse(),
                    modifier = Modifier.padding(end = if (isTablet) 16.dp else 18.dp),
                    iconSize = 24.dp,
                    containerColor = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainerHigh
                        .copy(alpha = if (isAppDarkTheme()) 0.4f else 0.6f)
                    else if (isAppDarkTheme()) Color(0xFF363636).copy(0.4f)
                    else Color(0xFFFFFFFF).copy(0.6f),
                    backdropAlpha = material.backdropAlpha,
                    shadowAlpha = material.shadowAlpha,
                )
            }
            // show 状态下沉到子作用域：顶层读会在点卡片那帧重组整页，压在弹窗动画头两帧
            val detailEditScope = rememberCoroutineScope()
            CourseDetailSheet(
                show = externalShowCourseDetail.value,
                allCourses = courses,
                selectedCourses = selectedCourses.ifEmpty { listOfNotNull(selectedCourse) },
                viewingWeek = viewingWeek,
                isTablet = isTablet,
                hiddenCourseIds = hiddenCourseIds,
                onDismiss = {
                    showCourseDetail = false
                    onPopupStateChange(false)
                },
                onCourseClick = onCourseClick,
                onNoteSave = { course, week, text ->
                    val updated = if (week == null) {
                        course.copy(note = text)
                    } else {
                        val weekNotes = course.weekNotes.toMutableMap()
                        if (text.isEmpty()) weekNotes.remove(week) else weekNotes[week] = text
                        course.copy(weekNotes = weekNotes)
                    }
                    viewModel.updateCourse(updated)
                },
                onEdit = { course ->
                    showCourseDetail = false
                    onPopupStateChange(false)
                    // 等关闭动画后再开编辑弹窗
                    detailEditScope.launch {
                        delay(100.milliseconds)
                        viewModel.showEditDialog(course)
                    }
                },
                endAction = detailEndAction,
            )

            val editingBlock = specialBlocks.firstOrNull { it.id == specialItemEditingBlockId }
            // 编辑时排除自己，否则无法取消自己占用的格子
            val occupiedDays = (editingBlock?.safeItems ?: emptyList())
                .filter { it.id != specialItemEditingId }
                .flatMap { it.startDay..it.endDay }
                .toSet()
            SpecialItemEditDialog(
                show = showSpecialItemDialog,
                isEditing = specialItemEditingId != -1L,
                name = specialItemName,
                selectedDays = specialItemSelectedDays,
                occupiedDays = occupiedDays,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onNameChange = { specialItemName = it },
                onDayToggle = { day ->
                    specialItemSelectedDays = if (day in specialItemSelectedDays) {
                        specialItemSelectedDays - day
                    } else {
                        specialItemSelectedDays + day
                    }
                },
                onDismiss = { showSpecialItemDialog = false },
                onSave = {
                    if (editingBlock == null) {
                        showSpecialItemDialog = false
                    } else {
                        val trimmedName = specialItemName.trim()
                        val selected = specialItemSelectedDays.sorted()
                        when {
                            trimmedName.isBlank() -> {
                                showToast(SchedStr.enterName())
                            }

                            selected.isEmpty() -> {
                                showToast(SchedStr.selectAtLeastOneDay())
                            }

                            else -> {
                                // 不连续点选合并为连续区间，如 {1,3,4} → 两个子块。
                                // id 由 nextSpecialItemIds 保证一批内互不相同（见该函数注释）。
                                val ranges = mergeConsecutiveDays(selected)
                                val base =
                                    editingBlock.safeItems.filter { it.id != specialItemEditingId }
                                val ids = nextSpecialItemIds(base, ranges.size)
                                val newItems = ranges.mapIndexed { i, (s, e) ->
                                    SpecialItem(
                                        id = ids[i],
                                        name = trimmedName,
                                        startDay = s,
                                        endDay = e
                                    )
                                }
                                val finalItems = (base + newItems).sortedBy { it.startDay }
                                settingsViewModel.updateSpecialBlocks(
                                    specialBlocks.map {
                                        if (it.id == specialItemEditingBlockId) it.copy(items = finalItems) else it
                                    }
                                )
                                showSpecialItemDialog = false
                            }
                        }
                    }
                },
                onDelete = {
                    if (editingBlock != null) {
                        settingsViewModel.updateSpecialBlocks(
                            specialBlocks.map {
                                if (it.id == specialItemEditingBlockId) {
                                    it.copy(items = editingBlock.safeItems.filter { item -> item.id != specialItemEditingId })
                                } else it
                            }
                        )
                    }
                    showSpecialItemDialog = false
                }
            )
        }
}

private data class DropMaskBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * 周 pager 宿主：beyond 预组合策略全部收在本函数作用域内。
 *
 * - 滚动中**组合期直读** isScrollInProgress 立即收拢到 1：与首个滚动位移帧同一次
 *   重组/测量生效（快照原子性，无竞态），翻页动画帧最多释放 1 页，不再承担
 *   17~45ms 的新周页组合尖刺
 * - 停稳 250ms 后扩到 [idleBeyond]（默认 2，边缘驻留连续翻周 3）：把下一次翻页
 *   要用的邻页组合挪进空闲帧，防抖让它落在落页弹簧收尾之后、不挤进落地帧
 *
 * ⚠ 这些状态不得泄漏到调用方：beyond 翻转只重组本函数（体积极小），调用方传入的页内容
 *   lambda 实例保持不变，已组合的周页不会被连带重组。实测把状态写在 `MainScheduleScreen`
 *   主体里，每次翻转都重建页 lambda、整批页跟着重跑组合，反而更卡。
 */
@Composable
private fun WeekPagerHost(
    state: PagerState,
    dwellHint: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable PagerScope.(page: Int) -> Unit,
) {
    var idleBeyond by remember(state) { mutableIntStateOf(1) }
    LaunchedEffect(state) {
        snapshotFlow { !state.isScrollInProgress to dwellHint() }.collectLatest { (idle, dwelling) ->
            if (idle) {
                delay(250.milliseconds)
                idleBeyond = if (dwelling) 3 else 2
            }
        }
    }
    HorizontalPager(
        state = state,
        modifier = modifier,
        beyondViewportPageCount = if (state.isScrollInProgress) 1 else idleBeyond,
        // 横向触摸改由 pagerAxisTakeoverGesture 驱动；纵滑主导时交给 verticalScroll
        userScrollEnabled = false,
        pageContent = content,
    )
}

@Composable
private fun AnimatedDropTargetMask(
    softGlassSurface: SoftGlassSurface? = null,
    dropHighlight: () -> Pair<Int, IntRange>?,
    dropHighlightOrigin: () -> Pair<Int, IntRange>?,
    pageDayRange: List<Int>,
    grid: SpecialGridLayout,
    cardHeightPerSection: Float,
    cardCornerRadius: Float,
    isTablet: Boolean,
    isDark: Boolean,
    hasWallpaper: Boolean,
    cardBlurRadius: Float,
) {
    val density = LocalDensity.current
    val left = remember { Animatable(0f) }
    val top = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    val height = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val lastTarget = remember { mutableStateOf<DropMaskBox?>(null) }

    // 落点在**这里**求值：调用方只传 lambda，跨格时订阅落在本函数的作用域，
    // 只重组遮罩这一小块，不会把整页课表拖下水。
    val highlight = dropHighlight()
    val originHighlight = dropHighlightOrigin()

    // 没有落点且遮罩已淡尽时不挂这棵树：BoxWithConstraints 是 SubcomposeLayout。
    // 判据走 derivedStateOf —— 直接读 alpha.value 会让淡入/淡出的每一帧都重建整棵子组合。
    val hasTarget = highlight != null || originHighlight != null
    val mounted by remember(hasTarget) {
        derivedStateOf { hasTarget || alpha.value > 0.01f }
    }
    if (!mounted) return

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(grid.totalHeight.dp)
    ) {
        val sectionW = with(density) { (if (isTablet) 56 else 36).dp.toPx() }
        val padStart = with(density) { (if (isTablet) 24 else 0).dp.toPx() }
        val padEnd = with(density) { (if (isTablet) 24 else 2).dp.toPx() }
        val padH = with(density) { 2.dp.toPx() }
        val padV = with(density) { 2.dp.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val dayCount = pageDayRange.size.coerceAtLeast(1)
        val dayW = ((maxW - padStart - sectionW - padEnd) / dayCount).coerceAtLeast(0f)
        val dayAreaLeft = padStart + sectionW
        val cornerPx = with(density) { cardCornerRadius.dp.toPx() }

        val boxOf: (Pair<Int, IntRange>?) -> DropMaskBox? = remember(
            pageDayRange, grid, cardHeightPerSection, dayW, dayAreaLeft, density, padH, padV
        ) {
            { hl ->
                if (hl == null) {
                    null
                } else {
                    val dayIndex = pageDayRange.indexOf(hl.first)
                    if (dayIndex < 0) {
                        null
                    } else {
                        // 用 sectionTop 差值算高度：跨午休/晚修时计入分界缝与特殊块挤占
                        val first = hl.second.first
                        val last = hl.second.last
                        val topDp = grid.sectionTop[first] ?: 0f
                        val bottomDp = (grid.sectionTop[last] ?: topDp) + cardHeightPerSection
                        val hDp = (bottomDp - topDp).coerceAtLeast(cardHeightPerSection)
                        DropMaskBox(
                            left = dayAreaLeft + dayIndex * dayW + padH,
                            top = with(density) { topDp.dp.toPx() } + padV,
                            width = (dayW - padH * 2).coerceAtLeast(0f),
                            height = (with(density) { hDp.dp.toPx() } - padV * 2).coerceAtLeast(0f),
                        )
                    }
                }
            }
        }
        val targetBox = remember(highlight, boxOf) { boxOf(highlight) }
        val originBox = remember(originHighlight, boxOf) { boxOf(originHighlight) }

        LaunchedEffect(targetBox) {
            if (targetBox == null) {
                // 带出：仅透明度
                alpha.animateTo(
                    0f,
                    tween(durationMillis = 180, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
                )
                lastTarget.value = null
            } else {
                val prev = lastTarget.value
                val wasVisible = alpha.value > 0.05f && prev != null
                val spec = spring<Float>(dampingRatio = 0.95f, stiffness = 800f)
                if (!wasVisible) {
                    // 出现：从源课格连续滑入落点（无源时在落点淡入），避免闪现
                    val from = originBox ?: targetBox
                    left.snapTo(from.left)
                    top.snapTo(from.top)
                    width.snapTo(from.width)
                    height.snapTo(from.height)
                    alpha.snapTo(0f)
                    launch {
                        alpha.animateTo(
                            1f,
                            tween(
                                durationMillis = 180,
                                easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
                            )
                        )
                    }
                    if (from != targetBox) {
                        launch { left.animateTo(targetBox.left, spec) }
                        launch { top.animateTo(targetBox.top, spec) }
                        launch { width.animateTo(targetBox.width, spec) }
                        launch { height.animateTo(targetBox.height, spec) }
                    }
                } else {
                    // 目标变化：位置/尺寸弹簧连贯移动
                    launch { left.animateTo(targetBox.left, spec) }
                    launch { width.animateTo(targetBox.width, spec) }
                    launch { height.animateTo(targetBox.height, spec) }
                    launch { top.animateTo(targetBox.top, spec) }
                    if (alpha.value < 1f) {
                        launch { alpha.animateTo(1f, tween(durationMillis = 180)) }
                    }
                }
                lastTarget.value = targetBox
            }
        }

        val solidColor =
            (if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.primary else Color(0xFF9E9E9E))
                .copy(alpha = if (isDark) 0.13f else 0.15f)
        val glassSurface = if (MiuixTheme.isDynamicColor) {
            MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.64f else 0.5f)
        } else if (isDark) Color(0xFF242424).copy(alpha = 0.64f) else Color(0xFFF0F0F0).copy(alpha = 0.5f)
        val maskShape =
            remember(cardCornerRadius) { ContinuousRoundedRectangle(cardCornerRadius.dp) }
        val maskSoftOrigin = remember { arrayOf(Offset.Zero) }
        val maskRadiusPx = remember(cardCornerRadius, density) {
            with(density) { cardCornerRadius.dp.toPx() }
        }
        val glassRedraw = LocalGlassRedrawSignal.current
        // 遮罩位置/尺寸由 Animatable 每帧驱动，offset/layout 只搬图层、不触发 draw；
        // 必须把这些值读进绘制失效信号，否则拖拽时玻璃采样跟着卡片平移、不重新采样。
        val maskRedraw: () -> Unit = remember(left, top, width, height, glassRedraw) {
            {
                left.value
                top.value
                width.value
                height.value
                glassRedraw()
            }
        }
        val maskSoftModifier = remember(softGlassSurface, maskRadiusPx, density, maskRedraw) {
            softGlassSurface?.modifier(
                density = density,
                radiusPx = maskRadiusPx,
                origin = { maskSoftOrigin[0] },
                redraw = maskRedraw,
            )
        }

        if (hasWallpaper && maskSoftModifier != null) {
            // 柔光玻璃表面：offset+layout 跟动画几何，alpha 走 graphicsLayer
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            left.value.roundToInt(),
                            top.value.roundToInt()
                        )
                    }
                    .layout { measurable, _ ->
                        val w = width.value.roundToInt().coerceAtLeast(0)
                        val h = height.value.roundToInt().coerceAtLeast(0)
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(w, h) {
                            placeable.place(0, 0)
                        }
                    }
                    .onGloballyPositioned { maskSoftOrigin[0] = it.positionInWindow() }
                    .graphicsLayer { this.alpha = alpha.value }
                    .then(maskSoftModifier)
                    .background(glassSurface, maskShape)
            )
        } else {
            // 纯色路径：drawBehind 读 Animatable，位移/尺寸/透明度均不触发重组
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(grid.totalHeight.dp)
                    // 独立图层：拖拽跨格时弹簧每帧只重画这一层圆角矩形，
                    // 没有它失效会冒泡到最近的页图层，整页课表每帧重绘 4~14ms
                    .graphicsLayer {}
                    .drawBehind {
                        val a = alpha.value
                        if (a <= 0.01f) return@drawBehind
                        drawRoundRect(
                            color = solidColor.copy(alpha = solidColor.alpha * a),
                            topLeft = Offset(left.value, top.value),
                            size = Size(width.value, height.value),
                            cornerRadius = CornerRadius(cornerPx),
                        )
                    }
            )
        }
    }
}

/**
 * 跨周调课：拖拽跟手与收尾的页面级归属。
 *
 * 为什么不能留在课卡上：
 *  - 课卡住在周 pager 的页里，翻周时整页平移，卡片本地坐标跟着变一屏 → 浮层飞走；
 *  - 页内滚动同理 → 浮层越拖越偏；
 *  - 翻到隔周后源页会被 pager 回收，卡片上的 pointerInput 被取消 → 跟手直接断。
 *
 * 这里挂在 pager 上（跨周不重建），用 root 坐标算「相对按下时」的位移。
 * 指针由课卡在起拖时登记（[ScheduleTouchState.draggingPointerId]），tracker 只认这一根；
 * 接管后 consume 掉它的位移，避免源页被回收后 verticalScroll 把拖动当滚动。
 * 收尾也归这里：真拖起来过 → [onDragEnd] 按落点写库；只是菜单上的手抖 →
 * 偏移归零、菜单留着（[onDrag] 传 0）。
 */
private fun Modifier.courseDragOwnerGesture(
    touchState: ScheduleTouchState,
    coordinates: Array<LayoutCoordinates?>,
    onDrag: (courseId: String, offsetX: Float, offsetY: Float) -> Unit,
    onDragEnd: (courseId: String) -> Unit,
    onDragCancel: (courseId: String) -> Unit,
): Modifier = pointerInput(touchState) {
    awaitPointerEventScope {
        var trackedId: PointerId? = null
        var startRoot = Offset.Zero
        while (true) {
            // Initial：课卡在 Main 处理，这里先拿到本帧事件
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val dragId = touchState.draggingPointerId
            if (dragId == null) {
                trackedId = null
                continue
            }
            val change = event.changes.firstOrNull { it.id == dragId }
            if (change == null) {
                // 指针从事件流里消失（系统取消）：已接管的必须收尾，别把浮层冻在屏上
                if (trackedId == dragId) {
                    val id = touchState.draggingCourseId
                    if (id != null) {
                        if (touchState.dragStarted) onDragCancel(id)
                        else onDrag(id, 0f, 0f)
                    }
                    touchState.endDragTracking()
                    trackedId = null
                }
                continue
            }
            if (trackedId != dragId) {
                trackedId = dragId
                // 用按下时的 root 位置当零点：课卡在 E0 上报过的位移与这里同源，交接不跳
                startRoot = touchState.draggingDownRoot
                    ?: dragOwnerRoot(coordinates, change.position)
                touchState.dragTrackerActive = true
            }
            if (!change.pressed) {
                // 先回调（要读最后的 offset/落点）再清状态，否则课卡侧看不出已收尾
                val id = touchState.draggingCourseId
                if (id != null) {
                    if (touchState.dragStarted) onDragEnd(id) else onDrag(id, 0f, 0f)
                }
                touchState.endDragTracking()
                trackedId = null
                change.consume()
                continue
            }
            val id = touchState.draggingCourseId
            if (id != null) {
                val rootNow = dragOwnerRoot(coordinates, change.position)
                onDrag(id, rootNow.x - startRoot.x, rootNow.y - startRoot.y)
            }
            change.consume()
        }
    }
}

private fun dragOwnerRoot(coordinates: Array<LayoutCoordinates?>, local: Offset): Offset {
    val coords = coordinates[0]
    return if (coords != null && coords.isAttached) coords.localToRoot(local) else local
}
