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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.appearance.AppearanceConfig
import com.haooz.chedule.data.appearance.CardContentAlignment
import com.haooz.chedule.data.appearance.CardTextColor
import com.haooz.chedule.data.appearance.scheduleSurfaceAlpha
import com.haooz.chedule.data.holiday.HolidayBeforeCourseExclusion
import com.haooz.chedule.data.holiday.HolidayCourseExclusion
import com.haooz.chedule.data.holiday.HolidayEndCourseExclusion
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.holiday.TeachingWeekReorganizationRule
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.SpecialBlock
import com.haooz.chedule.data.model.SpecialItem
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.repo.DayScheduleResolver
import com.haooz.chedule.data.util.coursePeriodOngoing
import com.haooz.chedule.data.util.minusDays
import com.haooz.chedule.data.util.nowLocalTime
import com.haooz.chedule.data.util.parseHourMinute
import com.haooz.chedule.data.util.plusDays
import com.haooz.chedule.data.util.plusWeeks
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.activities.main.MorePopupMenus
import com.haooz.chedule.ui.activities.main.PageLockedTopBarTheme
import com.haooz.chedule.ui.activities.main.PagerJumpNeighbor
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.CourseDetailSheet
import com.haooz.chedule.ui.components.HorizontalPageSwitch
import com.haooz.chedule.ui.components.LocalGlassRedrawSignal
import com.haooz.chedule.ui.components.LocalPageStartPadding
import com.haooz.chedule.ui.components.LocalSoftGlassSurface
import com.haooz.chedule.ui.components.PageSwitchState
import com.haooz.chedule.ui.components.SoftGlassSurface
import com.haooz.chedule.ui.components.rememberRevealPresence
import com.haooz.chedule.ui.components.rememberScheduleRevealState
import com.haooz.chedule.ui.components.scheduleRevealLayer
import com.haooz.chedule.ui.components.tabletNavSamplingSignal
import com.haooz.chedule.ui.components.text.LocalTextMotionInstant
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.courseCardSolidBacking
import com.haooz.chedule.ui.utils.hostVerticalBarEdge
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.schedulePageBackgroundColor
import com.haooz.chedule.ui.utils.screenHeightDp
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberCombinedBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.math.abs
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

/**
 * 一页课表的几何快照，供长按浮层、落点检测与写库换算。
 *
 * [columnDataPosition] 把「列星期」映射回数据坐标（调课日列显示被调星期的课）；
 * [pageWeek] 标明这份几何属于哪一周，跨周翻页后上层靠它判断能否信任映射。
 */
data class ScheduleGridGeometry(
    val dayBounds: Map<Int, FloatArray>,
    val sectionHeightPx: Float,
    val morningSections: Int,
    val afternoonSections: Int,
    val eveningSections: Int,
    val showBreakDividers: Boolean,
    val columnDataPosition: Map<Int, Pair<Int, Int>> = emptyMap(),
    val pageWeek: Int = 0,
    val blockedColumns: Set<Int> = emptySet(),
    /** 节次 → 顶部 y（dp），取 [computeSpecialGridLayout]；定位必须用它，不能用节次高度现算。 */
    val sectionTopDp: Map<Int, Float> = emptyMap(),
)

@Composable
fun MainScheduleScreen(
    viewModel: CourseViewModel,
    settingsViewModel: SettingsViewModel,
    /** 翻周位置来源。 */
    pageState: PageSwitchState,
    /** 长距跳转借位页：让相邻页临时显示目标周。 */
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
        addDay: Int,
        addWeek: Int,
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onCourseLongPress: (course: Course, cardLeft: Float, cardTop: Float, width: Float, height: Float, currentWeek: Int) -> Unit = { _, _, _, _, _, _ -> },
    onCourseDragStart: (courseId: String) -> Unit = { _ -> },
    onCourseDrag: (courseId: String, offsetX: Float, offsetY: Float) -> Unit = { _, _, _ -> },
    onCourseDragEnd: (courseId: String) -> Unit = { _ -> },
    onCourseDragCancel: (courseId: String) -> Unit = { _ -> },
    wallpaperBitmap: ImageBitmap? = null,
    wallpaperOffset: Offset = Offset.Zero,
    wallpaperScale: Float = 1f,
    /** true：壁纸由主 pager 后的共享层绘制，本页透明叠上。 */
    useSharedWallpaper: Boolean = false,
    isWallpaperEditing: Boolean = false,
    onWallpaperOffsetChange: (Offset) -> Unit = {},
    onWallpaperScaleChange: (Float) -> Unit = {},
    appearance: AppearanceConfig = AppearanceConfig(),
    /** 周页呈现方式：网格（可编辑）或时间轴（按真实时间比例、只读）。 */
    scheduleViewMode: ScheduleViewMode = ScheduleViewMode.WEEK,
    liquidGlassBackdrop: GlassBackdrop? = null,
    /** 柔光玻璃采样源（主内容层算一次；null = 无玻璃）。 */
    softGlassSurface: SoftGlassSurface? = null,
    /** 主 tab pager 的位置信号：切今日/课表时内容整体平移，必须让玻璃重绘。 */
    tabRedraw: () -> Unit = {},
    // 拖拽落点高亮：求值推迟到遮罩自己的组合作用域，跨格只重组那一小块
    dropHighlight: () -> Pair<Int, IntRange>? = { null },
    dropHighlightOrigin: () -> Pair<Int, IntRange>? = { null },
    // 拖课卡停在边缘热区（即将自动翻周）时为 true；pager 据此预组合邻页
    edgeDwellHint: () -> Boolean = { false },
    onGridGeometryChange: (ScheduleGridGeometry) -> Unit = {},
    scheduleScrollBehavior: SharedScrollBehavior? = null,
    externalScrollState: ScrollState = rememberScrollState(),
    externalShowCourseDetail: MutableState<Boolean> = mutableStateOf(false),
    externalSelectedCourse: MutableState<Course?> = mutableStateOf(null),
    externalSelectedCourses: MutableState<List<Course>> = mutableStateOf(emptyList()),
    overScrollState: OverScrollState = remember { OverScrollState() },
    /** 顶栏宿主数据；null 时本页不画顶栏（独立使用 / 预览）。 */
    chrome: SchedulePageChrome? = null,
) {
    val cardAlpha = appearance.cardAlpha
    // 周页 lambda 只捕获稳定容器；update 必须在组合期同步写
    val draggingSlotsState = remember { DraggingSlotsState() }
    draggingSlotsState.update(draggingCourseSlots)
    // 基准值（深色已 ×0.8）再低 15pp：亮色 34% ⇒ 19%，深色 27.2% ⇒ 12.2%
    val cardSurfaceAlpha = appearance.scheduleSurfaceAlpha(isAppDarkTheme())
    val cardHeightPerSection = appearance.cardHeight
    val cardCornerRadius = appearance.cardCornerRadius
    val showBreakDividers = appearance.showBreakDividers
    val showWeekGridLines = appearance.showWeekGridLines
    val cardContentAlignment = appearance.cardContentAlignment
    val cardTextColor = appearance.cardTextColor
    val cardTextScale = appearance.cardTextScale
    val showClassroom = appearance.showClassroom
    val showTeacher = appearance.showTeacher

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

    val isTablet = isTabletWidth()
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    // 沉浸式：滚动视口不避开底栏，底部留白写进滚动 layout 高度
    val nativeChromeInset = LocalHostNavigation.current
        ?.takeIf { it.showsNativeTabBar && !isTablet }
        ?.bottomBarInset?.toFloat()?.dp
    val scheduleEndSpacer = nativeChromeInset?.plus(80.dp) ?: 175.dp
    val screenWidthPx = with(density) { screenWidthDp().dp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp().dp.toPx() }

    // 位置信号：卡片玻璃在各自 draw 里画，滚动/翻页/回弹只平移祖先图层
    val glassRedrawSignal: () -> Unit = remember(pageState, externalScrollState, overScrollState, tabRedraw) {
        {
            externalScrollState.value
            pageState.offsetFraction
            overScrollState.offset
            tabletNavSamplingSignal()
            tabRedraw()
        }
    }
    val touchState = remember { ScheduleTouchState() }
    // 跨周调课 tracker 的坐标（挂在 pager 上，跨周不重建）
    val dragOwnerCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // 指针协程的 key 只有 touchState，回调经 rememberUpdatedState 取最新
    val latestOnCourseDragStart = rememberUpdatedState(onCourseDragStart)
    val latestOnCourseDrag = rememberUpdatedState(onCourseDrag)
    val latestOnCourseDragEnd = rememberUpdatedState(onCourseDragEnd)
    val latestOnCourseDragCancel = rememberUpdatedState(onCourseDragCancel)

    // 整页被销毁时 tracker 协程也一起没了：手指还没抬就没人收尾，兜底取消
    DisposableEffect(touchState) {
        onDispose {
            if (touchState.pointerId != null) {
                touchState.courseId?.let { latestOnCourseDragCancel.value(it) }
                touchState.clear()
            }
        }
    }

    // 供停滑后冲刷 dayBounds 版本号；仅滑动起停各写一次
    val scheduleScrollInProgress = remember { mutableStateOf(false) }
    LaunchedEffect(pageState, externalScrollState) {
        snapshotFlow { pageState.isAnimating || externalScrollState.isScrollInProgress }.collect {
            touchState.scrolling = it
            scheduleScrollInProgress.value = it
        }
    }

    // 由顶栏自身几何纯计算（切页不变），避免 paddingValues 随 tab 变化导致位移
    val nativeBarInset = (LocalHostNavigation.current?.topBarInset ?: 0.0).dp
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
                screenHeightPx / wallpaperBitmap.height,
            )
            val coverScale = maxOf(
                screenWidthPx / wallpaperBitmap.width,
                screenHeightPx / wallpaperBitmap.height,
            )
            if (fitScale > 0f) coverScale / fitScale else 1f
        } else {
            1f
        }
    }

    val selection = remember(externalShowCourseDetail, externalSelectedCourse, externalSelectedCourses) {
        CourseDetailState(
            show = externalShowCourseDetail,
            course = externalSelectedCourse,
            courses = externalSelectedCourses,
        )
    }
    val specialItemEditor = remember { SpecialItemEditorState() }
    val pendingSlot = remember { PendingSlotState() }
    // 惰性：只在详情弹窗真的组合时求值，翻页不牵动本页与周页重组
    val viewingWeek = remember(pageState) {
        derivedStateOf { pageState.currentPage + 1 }
    }

    LaunchedEffect(showAddDialog) {
        if (showAddDialog && pendingSlot.slot.first != -1) {
            delay(300.milliseconds)
            pendingSlot.slot = -1 to -1
        }
    }

    val totalSections = morningSections + afternoonSections + eveningSections

    val specialGrid = remember(
        totalSections, morningSections, afternoonSections, eveningSections,
        specialBlocks, sectionTimes, cardHeightPerSection, showBreakDividers,
    ) {
        computeSpecialGridLayout(
            morningSections = morningSections,
            afternoonSections = afternoonSections,
            eveningSections = eveningSections,
            specialBlocks = specialBlocks,
            sectionTimes = sectionTimes,
            cardHeightPerSection = cardHeightPerSection,
            dividerGap = if (showBreakDividers) 24 else 0,
        )
    }

    // 切回网格时归零：时间轴的纵向尺度远大于节次，沿用会停在网格底部附近
    var previousViewMode by remember { mutableStateOf(scheduleViewMode) }
    LaunchedEffect(scheduleViewMode) {
        val from = previousViewMode
        previousViewMode = scheduleViewMode
        if (from == ScheduleViewMode.TIMELINE && scheduleViewMode == ScheduleViewMode.WEEK) {
            externalScrollState.scrollTo(0)
        }
    }

    // 睡到下一次节次边界再重算，避免长时间停留后高亮过期
    val currentSectionState = remember { mutableIntStateOf(-1) }
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
                if (currentMinutes in startMinutes until endMinutes) result = section
                if (endMinutes in (currentMinutes + 1)..<nextTransition) nextTransition = endMinutes
                if (startMinutes in (currentMinutes + 1)..<nextTransition) nextTransition = startMinutes
            }
            if (currentSectionState.intValue != result) currentSectionState.intValue = result
            val sleepMinutes = if (nextTransition == Int.MAX_VALUE) {
                1
            } else {
                (nextTransition - currentMinutes).coerceIn(1, 60)
            }
            delay((sleepMinutes * 60_000L).milliseconds)
        }
    }

    val allDays = remember { (1..7).toList() }
    val coursesByDay = remember(courses) {
        allDays.associateWith { dayOfWeek -> courses.filter { it.dayOfWeek == dayOfWeek } }
    }

    val scheduleRepository = remember { CourseRepository.getInstance() }
    val currentScheduleId = remember(scheduleRepository, dataVersion) {
        scheduleRepository.getCurrentScheduleId()
    }
    val semesterStartDate = remember(dataVersion, classStartTime) {
        runCatching { LocalDate.parse(classStartTime.replace("/", "-")) }.getOrNull()
            ?: todayLocalDate()
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

    // 调休条目 → 当前课表下的 (周次, 星期)
    val followPositionFor: (HolidayEntry) -> Pair<Int, Int>? = remember(
        semesterStartDate,
        teachingWeekReorganizations,
        currentScheduleId,
    ) {
        { swap -> scheduleRepository.resolveWorkSwapFollow(swap, currentScheduleId)?.let { it.week to it.weekday } }
    }

    val holidayVersion = remember(dataVersion, holidayDataRevision) { HolidayManager.getVersion() }
    val holidayEntriesByYear = remember(dataVersion, holidayDataRevision, holidayVersion) {
        HolidayManager.loadAllByYear()
    }
    val holidayEntriesByDate = remember(holidayEntriesByYear, semesterStartMonday, semesterLastDate) {
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
    // 日期索引复用提醒解析的同日/同类型优先级，只查询当前学期可见日期
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

    // 按周懒算：首次进入只算可见页，不再把整学期一次算完
    val weekData = remember(
        totalWeeks,
        allDays,
        coursesByDay,
        showNonCurrentWeek,
        smartWeekend,
        dateForTeachingPosition,
        workswapIndex,
        holidayIndex,
        followPositionFor,
        holidayEntriesByYear,
        holidayEndCourseExclusion,
        holidayBeforeCourseExclusion,
        sectionTimes,
        totalSections,
        teachingWeekReorganizations,
        semesterStartDate,
        scheduleRepository,
    ) {
        WeekDataCache(
            totalWeeks = totalWeeks,
            days = allDays,
            allCourses = courses,
            coursesByDay = coursesByDay,
            showNonCurrentWeek = showNonCurrentWeek,
            smartWeekend = smartWeekend,
            dateForPosition = dateForTeachingPosition,
            workswapIndex = workswapIndex,
            holidayIndex = holidayIndex,
            followPositionFor = followPositionFor,
            holidayEntriesByYear = holidayEntriesByYear,
            holidayEndExclusion = holidayEndCourseExclusion,
            holidayBeforeExclusion = holidayBeforeCourseExclusion,
            sectionTimes = sectionTimes,
            totalSections = totalSections,
            teachingWeekReorganizations = teachingWeekReorganizations,
            semesterStartDate = semesterStartDate,
            repository = scheduleRepository,
        )
    }

    // 当前上课课程：取「今天这一列实际显示的课」，30 秒一拍且结果变化才写容器
    val ongoingCourseIdsState = remember { OngoingCourseIdsState() }
    val computeOngoingIds by rememberUpdatedState {
        ongoingCourseIds(weekData, currentWeek, sectionTimes)
    }
    LaunchedEffect(Unit) {
        while (true) {
            val next = computeOngoingIds()
            if (next != ongoingCourseIdsState.ids) ongoingCourseIdsState.ids = next
            delay(30_000L)
        }
    }
    // 数据或周次一变立刻重算，不等节拍
    LaunchedEffect(weekData, currentWeek) {
        val next = computeOngoingIds()
        if (next != ongoingCourseIdsState.ids) ongoingCourseIdsState.ids = next
    }

    val onPendingChange: (Int, Int) -> Unit = remember {
        { day, section -> pendingSlot.slot = day to section }
    }

    val scheduleIsDark = isAppDarkTheme()
    val cardSolidBacking = courseCardSolidBacking(
        isDark = scheduleIsDark,
        hasWallpaper = wallpaperBitmap != null,
    )

    val contentBackdrop = chrome?.contentBackdrop ?: rememberLayerBackdrop()
    val glassLayersEnabled =
        !com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier() && chrome != null
    val wallpaperBackdrop = chrome?.wallpaperBackdrop
    val chromeBackdrop: GlassBackdrop = if (wallpaperBackdrop != null) {
        rememberCombinedBackdrop(wallpaperBackdrop, contentBackdrop)
    } else {
        contentBackdrop
    }
    val moreSlotTopPx = remember { mutableFloatStateOf(0f) }
    val moreMaterialAlpha = remember { mutableFloatStateOf(0f) }
    val pageRootCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // 顶栏单独录一层：展开的「更多」菜单采「本页内容 + 本页顶栏」，不会采到自己
    val topBarBackdrop = rememberLayerBackdrop()
    val menuBackdrop = if (wallpaperBackdrop != null) {
        rememberCombinedBackdrop(wallpaperBackdrop, contentBackdrop, topBarBackdrop)
    } else {
        rememberCombinedBackdrop(contentBackdrop, topBarBackdrop)
    }
    // 这层只为「更多」菜单服务：收起且动画归零就摘掉，避免顶栏每帧白录一层
    val topBarLayerWanted by remember(chrome?.moreMenu) {
        derivedStateOf {
            val menu = chrome?.moreMenu
            menu != null && (menu.show || menu.fraction.value > 0f)
        }
    }

    // 页面级静态配置：只在相关参数变化时重建，避免周页被无谓重组
    val pageConfig = remember(
        isTablet,
        cardAlpha,
        cardSurfaceAlpha,
        cardHeightPerSection,
        cardCornerRadius,
        showBreakDividers,
        showWeekGridLines,
        cardContentAlignment,
        cardTextColor,
        cardTextScale,
        showClassroom,
        showTeacher,
        scheduleIsDark,
        cardSolidBacking,
        softGlassSurface,
        wallpaperBitmap != null,
        contentTopPaddingDp,
        scheduleEndSpacer,
        specialGrid,
        totalSections,
        morningSections,
        afternoonSections,
        eveningSections,
        sectionTimes,
        sectionNames,
        specialBlocks,
    ) {
        WeekPageConfig(
            isTablet = isTablet,
            cardAlpha = cardAlpha,
            cardSurfaceAlpha = cardSurfaceAlpha,
            cardHeightPerSection = cardHeightPerSection,
            cardCornerRadius = cardCornerRadius,
            showBreakDividers = showBreakDividers,
            showWeekGridLines = showWeekGridLines,
            cardContentAlignment = cardContentAlignment,
            cardTextColor = cardTextColor,
            cardTextScale = cardTextScale,
            showClassroom = showClassroom,
            showTeacher = showTeacher,
            isDark = scheduleIsDark,
            solidBacking = cardSolidBacking,
            softGlassSurface = softGlassSurface,
            hasWallpaper = wallpaperBitmap != null,
            contentTopPadding = contentTopPaddingDp,
            endSpacer = scheduleEndSpacer,
            grid = specialGrid,
            totalSections = totalSections,
            morningSections = morningSections,
            afternoonSections = afternoonSections,
            eveningSections = eveningSections,
            sectionTimes = sectionTimes,
            sectionNames = sectionNames,
            specialBlocks = specialBlocks,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 采样层必须在页底色之前：layerBackdrop 只录它内层的内容
            .then(
                if (glassLayersEnabled) {
                    Modifier.layerBackdrop(
                        contentBackdrop,
                        chrome.contentRecordKey,
                        chrome.contentMustRecord,
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (!useSharedWallpaper) {
                    Modifier.background(schedulePageBackgroundColor(scheduleIsDark))
                } else {
                    Modifier
                }
            )
            .onGloballyPositioned { pageRootCoords[0] = it }
    ) {
        // 回弹状态由独立 composable 订阅，不在 content 直读
        val bounceState = remember { WallpaperBounceState() }
        WallpaperBounceBack(bounceState, minWallpaperScale, onWallpaperScaleChange)

        WeekPagerHost(
            state = pageState,
            pageCount = totalWeeks,
            swipeEnabled = !isWallpaperEditing,
            blockedPointerId = { touchState.pointerId },
            // 第一根手指按住浮层时，第二只手的纵向位移直接喂给共享 ScrollState
            onVerticalScroll = { dy -> externalScrollState.dispatchRawDelta(-dy) },
            dwellHint = edgeDwellHint,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { dragOwnerCoords[0] = it }
                .courseDragOwnerGesture(
                    touchState = touchState,
                    coordinates = dragOwnerCoords,
                    onDrag = { id, dx, dy -> latestOnCourseDrag.value(id, dx, dy) },
                    onDragStart = { id -> latestOnCourseDragStart.value(id) },
                    onDragEnd = { id -> latestOnCourseDragEnd.value(id) },
                    onDragCancel = { id -> latestOnCourseDragCancel.value(id) },
                ),
        ) { physicalPage ->
            ScheduleWeekPage(
                physicalPage = physicalPage,
                jumpNeighbor = jumpNeighbor,
                pageState = pageState,
                viewModel = viewModel,
                weekData = weekData,
                currentWeek = currentWeek,
                config = pageConfig,
                scheduleViewMode = scheduleViewMode,
                currentSectionState = currentSectionState,
                pendingSlot = pendingSlot,
                onPendingChange = onPendingChange,
                draggingSlotsState = draggingSlotsState,
                touchState = touchState,
                scrollInProgress = scheduleScrollInProgress,
                glassRedrawSignal = glassRedrawSignal,
                overScrollState = overScrollState,
                ongoingCourseIdsState = ongoingCourseIdsState,
                externalScrollState = externalScrollState,
                scheduleScrollBehavior = scheduleScrollBehavior,
                dropHighlight = dropHighlight,
                dropHighlightOrigin = dropHighlightOrigin,
                onGridGeometryChange = onGridGeometryChange,
                onCourseLongPress = onCourseLongPress,
                onEmptyLongPress = onEmptyLongPress,
                onPopupStateChange = onPopupStateChange,
                selection = selection,
                dateForTeachingPosition = dateForTeachingPosition,
                specialItemEditor = specialItemEditor,
            )
        }

        // 壁纸编辑手势：整层盖住 pager，编辑态下替代翻页/点击
        if (isWallpaperEditing && wallpaperBitmap != null) {
            WallpaperEditLayer(
                bounceState = bounceState,
                minScale = minWallpaperScale,
                scale = wallpaperScale,
                offset = wallpaperOffset,
                bitmap = wallpaperBitmap,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                onScaleChange = onWallpaperScaleChange,
                onOffsetChange = onWallpaperOffsetChange,
            )
        }

        val detailEditScope = rememberCoroutineScope()
        CourseDetailSheetHost(
            viewingWeek = viewingWeek,
            show = selection.show.value,
            allCourses = courses,
            selectedCourses = selection.courses.value.ifEmpty {
                listOfNotNull(selection.course.value)
            },
            isTablet = isTablet,
            hiddenCourseIds = hiddenCourseIds,
            onDismiss = {
                selection.show.value = false
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
                selection.show.value = false
                onPopupStateChange(false)
                // 等关闭动画后再开编辑弹窗
                detailEditScope.launch {
                    delay(100.milliseconds)
                    viewModel.showEditDialog(course)
                }
            },
            endAction = {
                val scope = rememberCoroutineScope()
                val material = LocalSheetTopBarMaterial.current
                LiquidTopBarButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        val course = selection.course.value ?: selection.courses.value.firstOrNull()
                        selection.show.value = false
                        scope.launch {
                            delay(100.milliseconds)
                            if (course != null) {
                                viewModel.showAddDialog(course.dayOfWeek, course.startSection, course.endSection)
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
                    containerColor = if (MiuixTheme.isDynamicColor) {
                        MiuixTheme.colorScheme.surfaceContainerHigh
                            .copy(alpha = if (scheduleIsDark) 0.4f else 0.6f)
                    } else if (scheduleIsDark) {
                        Color(0xFF363636).copy(0.4f)
                    } else {
                        Color(0xFFFFFFFF).copy(0.6f)
                    },
                    backdropAlpha = material.backdropAlpha,
                    shadowAlpha = material.shadowAlpha,
                )
            },
        )

        SpecialItemEditDialog(
            show = specialItemEditor.show,
            isEditing = specialItemEditor.editingId != -1L,
            name = specialItemEditor.name,
            selectedDays = specialItemEditor.selectedDays,
            occupiedDays = specialItemEditor.occupiedDays(specialBlocks),
            liquidGlassBackdrop = liquidGlassBackdrop,
            onNameChange = { specialItemEditor.name = it },
            onDayToggle = { day ->
                specialItemEditor.selectedDays = if (day in specialItemEditor.selectedDays) {
                    specialItemEditor.selectedDays - day
                } else {
                    specialItemEditor.selectedDays + day
                }
            },
            onDismiss = { specialItemEditor.show = false },
            onSave = {
                val block = specialItemEditor.block(specialBlocks)
                if (block == null) {
                    specialItemEditor.show = false
                } else {
                    val trimmedName = specialItemEditor.name.trim()
                    val selected = specialItemEditor.selectedDays.sorted()
                    when {
                        trimmedName.isBlank() -> showToast(SchedStr.enterName())
                        selected.isEmpty() -> showToast(SchedStr.selectAtLeastOneDay())
                        else -> {
                            // 不连续点选合并为连续区间
                            val ranges = mergeConsecutiveDays(selected)
                            val base = block.safeItems.filter { it.id != specialItemEditor.editingId }
                            val ids = nextSpecialItemIds(base, ranges.size)
                            val newItems = ranges.mapIndexed { index, (start, end) ->
                                SpecialItem(id = ids[index], name = trimmedName, startDay = start, endDay = end)
                            }
                            val finalItems = (base + newItems).sortedBy { it.startDay }
                            settingsViewModel.updateSpecialBlocks(
                                specialBlocks.map {
                                    if (it.id == specialItemEditor.blockId) it.copy(items = finalItems) else it
                                }
                            )
                            specialItemEditor.show = false
                        }
                    }
                }
            },
            onDelete = {
                val block = specialItemEditor.block(specialBlocks)
                if (block != null) {
                    settingsViewModel.updateSpecialBlocks(
                        specialBlocks.map {
                            if (it.id == specialItemEditor.blockId) {
                                it.copy(
                                    items = block.safeItems.filter { item ->
                                        item.id != specialItemEditor.editingId
                                    }
                                )
                            } else {
                                it
                            }
                        }
                    )
                }
                specialItemEditor.show = false
            },
        )
    }

    // 顶栏与页面同层：翻周 / 切 tab 时跟着页面一起位移
    chrome?.let { c ->
        PageLockedTopBarTheme(c.forcedDarkTheme) {
            Box(
                Modifier.then(
                    if (glassLayersEnabled && topBarLayerWanted) {
                        Modifier.layerBackdrop(topBarBackdrop)
                    } else {
                        Modifier
                    }
                )
            ) {
                ScheduleTopBar(
                    visible = true,
                    pagerCurrentPage = pageState.currentPage,
                    currentWeek = c.currentWeek,
                    isHoliday = c.isHoliday,
                    dayRange = c.dayRange,
                    currentDayOfWeek = c.currentDayOfWeek,
                    isCurrentWeek = pageState.currentPage + 1 == c.currentWeek &&
                        c.currentWeek in 1..c.totalWeeks,
                    weekDates = c.weekDates,
                    isReorganized = c.isReorganized,
                    onOpenSwitchSchedule = c.onOpenSwitchSchedule,
                    onJumpWeek = c.onJumpWeek,
                    onEnterCustomize = c.onEnterCustomize,
                    onCourseManage = c.onCourseManage,
                    isTablet = isTablet,
                    isShiftMode = c.isShiftMode,
                    liquidGlassBackdrop = chromeBackdrop,
                    scrollBehavior = scheduleScrollBehavior,
                    blurResampleKey = c.blurResampleKey,
                    blurSampleTrack = c.blurSampleTrack,
                    titleEndPadding = c.titleEndPadding,
                    onMoreSlotTop = { slot ->
                        // positionInRoot 会把外层开洞缩放算进去，外观页缩小时「更多」会被算得偏高；
                        // 换算成相对页面根的局部坐标，与 MorePopupMenus 的绘制坐标系一致
                        val root = pageRootCoords[0]
                        if (root != null && root.isAttached && slot.isAttached) {
                            moreSlotTopPx.floatValue = root.localPositionOf(slot, Offset.Zero).y
                        }
                    },
                    onMoreMaterial = { moreMaterialAlpha.floatValue = it },
                    onMoreExpand = { c.moreMenu?.onExpand?.invoke() },
                )
            }
            c.moreMenu?.let { menu ->
                MorePopupMenus(
                    showMorePopup = menu.show,
                    onMorePopupDismiss = menu.onDismiss,
                    onMorePopupExpand = menu.onExpand,
                    showTodayMorePopup = false,
                    onTodayMorePopupDismiss = {},
                    morePopupFraction = menu.fraction,
                    todayMorePopupFraction = remember { Animatable(0f) },
                    scheduleMenuOffset = { Offset.Zero },
                    moreSlotTopPx = moreSlotTopPx,
                    scheduleMaterialAlpha = moreMaterialAlpha.floatValue,
                    liquidGlassBackdrop = menuBackdrop,
                    isShiftMode = c.isShiftMode,
                    enabled = menu.enabled,
                    scheduleMoreNative = menu.native,
                    todayMoreNative = true,
                    onJumpWeek = c.onJumpWeek,
                    onCourseManage = c.onCourseManage,
                    onEnterCustomize = c.onEnterCustomize,
                    onJumpToDate = {},
                )
            }
        }
    }
}

/** 页面级静态配置：外观、节次几何与设置项。 */
@Immutable
private class WeekPageConfig(
    val isTablet: Boolean,
    val cardAlpha: Float,
    val cardSurfaceAlpha: Float,
    val cardHeightPerSection: Float,
    val cardCornerRadius: Float,
    val showBreakDividers: Boolean,
    val showWeekGridLines: Boolean,
    val cardContentAlignment: CardContentAlignment,
    val cardTextColor: CardTextColor,
    val cardTextScale: Float,
    val showClassroom: Boolean,
    val showTeacher: Boolean,
    val isDark: Boolean,
    val solidBacking: Color?,
    val softGlassSurface: SoftGlassSurface?,
    val hasWallpaper: Boolean,
    val contentTopPadding: Dp,
    val endSpacer: Dp,
    val grid: SpecialGridLayout,
    val totalSections: Int,
    val morningSections: Int,
    val afternoonSections: Int,
    val eveningSections: Int,
    val sectionTimes: Map<Int, String>,
    val sectionNames: Map<Int, String>,
    val specialBlocks: List<SpecialBlock>,
)

/** 一列在某周最终要显示的数据。 */
private data class DaySchedule(
    val displayDay: Int,
    val displayWeek: Int,
    val courses: List<Course>,
    val isHoliday: Boolean,
    val isWorkSwap: Boolean,
    val exemptCourseIds: Set<String>,
    val cancelledCourseIds: Set<String>,
)

/**
 * 按周懒加载的课表数据。
 *
 * 旧实现首次组合时把整学期（周数 × 7 天）的过滤与假期排除一次算完；这里改成页面真正
 * 渲染某周时才计算该周，冷启动开销从 O(全部周) 降到 O(可见页)。
 */
@Stable
private class WeekDataCache(
    private val totalWeeks: Int,
    private val days: List<Int>,
    private val allCourses: List<Course>,
    private val coursesByDay: Map<Int, List<Course>>,
    private val showNonCurrentWeek: Boolean,
    private val smartWeekend: Boolean,
    private val dateForPosition: (Int, Int) -> LocalDate?,
    private val workswapIndex: Map<String, HolidayEntry>,
    private val holidayIndex: Map<String, HolidayEntry>,
    private val followPositionFor: (HolidayEntry) -> Pair<Int, Int>?,
    private val holidayEntriesByYear: Map<Int, List<HolidayEntry>>,
    private val holidayEndExclusion: HolidayEndCourseExclusion,
    private val holidayBeforeExclusion: HolidayBeforeCourseExclusion,
    private val sectionTimes: Map<Int, String>,
    private val totalSections: Int,
    private val teachingWeekReorganizations: List<TeachingWeekReorganizationRule>,
    private val semesterStartDate: LocalDate,
    private val repository: CourseRepository,
) {
    private val weekCache = HashMap<Int, Map<Int, DaySchedule>>(totalWeeks)
    private val rangeCache = HashMap<Int, List<Int>>(totalWeeks)
    private val weekendCache = HashMap<Int, Set<Int>>(totalWeeks)

    /** 该周可见的星期列（含智能周末）。 */
    fun dayRange(week: Int): List<Int> = rangeCache.getOrPut(week) {
        (1..5).toList() + weekendDays(week).filter { it in 6..7 }
    }

    fun day(week: Int, dayOfWeek: Int): DaySchedule? {
        if (week !in 1..totalWeeks || dayOfWeek !in 1..7) return null
        return weekData(week)[dayOfWeek]
    }

    private fun weekData(week: Int): Map<Int, DaySchedule> = weekCache.getOrPut(week) {
        days.associateWith { dayOfWeek ->
            val date = dateForPosition(week, dayOfWeek)
                ?: return@associateWith DaySchedule(
                    dayOfWeek, week, emptyList(), false, false, emptySet(), emptySet()
                )
            val dateText = date.toString()
            val swap = workswapIndex[dateText]
            val follow = swap?.let(followPositionFor)
            val displayDay = follow?.second ?: dayOfWeek
            val displayWeek = follow?.first ?: week
            val candidates = coursesByDay[displayDay] ?: emptyList()
            val filtered = if (showNonCurrentWeek) {
                candidates
            } else {
                candidates.filter { it.isActiveInWeek(displayWeek) }
            }
            val exemptIds = if (!holidayEndExclusion.enabled ||
                !HolidayCourseExclusion.isEnabledOnDate(holidayEntriesByYear, date, holidayEndExclusion)
            ) {
                emptySet()
            } else {
                filtered.asSequence()
                    .filter { it.isActiveInWeek(displayWeek) }
                    .filter {
                        HolidayCourseExclusion.matchesCourse(
                            it, holidayEndExclusion, sectionTimes, totalSections
                        )
                    }
                    .map { it.id }
                    .toSet()
            }
            val cancelledIds = if (!holidayBeforeExclusion.enabled) {
                emptySet()
            } else {
                HolidayCourseExclusion.cancelledCourseIdsOnDate(
                    entriesByYear = holidayEntriesByYear,
                    date = date,
                    exclusion = holidayBeforeExclusion,
                    candidates = filtered,
                    displayWeek = displayWeek,
                    sectionTimes = sectionTimes,
                    sectionCount = totalSections,
                )
            }
            DaySchedule(
                displayDay = displayDay,
                displayWeek = displayWeek,
                courses = filtered,
                isHoliday = holidayIndex[dateText] != null,
                isWorkSwap = swap != null && follow != null,
                exemptCourseIds = exemptIds,
                cancelledCourseIds = cancelledIds,
            )
        }
    }

    /** 智能周末：只看该周周末是否有课；关闭时恒为周六日。 */
    private fun weekendDays(week: Int): Set<Int> = weekendCache.getOrPut(week) {
        if (!smartWeekend) return@getOrPut setOf(6, 7)
        if (teachingWeekReorganizations.isNotEmpty()) {
            return@getOrPut DayScheduleResolver.effectiveWeekendDays(
                semesterStartDate,
                week,
                teachingWeekReorganizations,
            ) { date ->
                DayScheduleResolver.resolveDaySchedule(
                    date = date,
                    repository = repository,
                    holidayEntriesByYear = holidayEntriesByYear,
                ).courses.isNotEmpty()
            }
        }
        fun hasWeekendCourses(dateString: String?, calendarDay: Int): Boolean {
            val date = dateString?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (date != null && HolidayCourseExclusion.isEnabledBeforeHolidayDate(
                    holidayEntriesByYear, date, holidayBeforeExclusion,
                )
            ) {
                return DayScheduleResolver.resolveDaySchedule(
                    date = date,
                    repository = repository,
                    holidayEntriesByYear = holidayEntriesByYear,
                ).courses.isNotEmpty()
            }
            val hasWorkSwap = dateString?.let {
                workswapIndex[it]?.let { swap -> followPositionFor(swap) != null }
            } == true
            return allCourses.any { it.dayOfWeek == calendarDay && it.isActiveInWeek(week) } ||
                hasWorkSwap
        }
        buildSet {
            if (hasWeekendCourses(dateForPosition(week, 6)?.toString(), 6)) add(6)
            if (hasWeekendCourses(dateForPosition(week, 7)?.toString(), 7)) add(7)
        }
    }
}

/** 详情弹窗选中的课程组；直接代理外层传入的 state。 */
@Stable
private class CourseDetailState(
    val show: MutableState<Boolean>,
    val course: MutableState<Course?>,
    val courses: MutableState<List<Course>>,
) {
    fun open(atSlot: List<Course>, fallback: Course) {
        courses.value = atSlot
        course.value = atSlot.find { it.id == fallback.id } ?: fallback
        show.value = true
    }
}

/** 特殊横带子块编辑弹窗的状态。 */
@Stable
private class SpecialItemEditorState {
    var show by mutableStateOf(false)
    var blockId by mutableLongStateOf(0L)
    var editingId by mutableLongStateOf(-1L) // -1 = 新增
    var name by mutableStateOf("")
    var selectedDays by mutableStateOf(setOf<Int>())

    fun block(blocks: List<SpecialBlock>): SpecialBlock? = blocks.firstOrNull { it.id == blockId }

    /** 编辑时排除自己，否则无法取消自己占用的格子。 */
    fun occupiedDays(blocks: List<SpecialBlock>): Set<Int> =
        (block(blocks)?.safeItems ?: emptyList())
            .filter { it.id != editingId }
            .flatMap { it.startDay..it.endDay }
            .toSet()

    fun openItem(blockId: Long, item: SpecialItem) {
        this.blockId = blockId
        editingId = item.id
        name = item.name
        selectedDays = (item.startDay..item.endDay).toSet()
        show = true
    }

    fun openEmpty(blockId: Long, day: Int) {
        this.blockId = blockId
        editingId = -1L
        name = ""
        selectedDays = setOf(day)
        show = true
    }
}

/** 当前正在上课的课程 id；数据/时间变化时由页面重算。 */
private fun ongoingCourseIds(
    weekData: WeekDataCache,
    currentWeek: Int,
    sectionTimes: Map<Int, String>,
): Set<String> {
    val today = todayLocalDate().dayOfWeek.isoDayNumber
    val day = weekData.day(currentWeek, today) ?: return emptySet()
    val now = nowLocalTime()
    return day.courses.mapNotNull { course ->
        // 「显示非本周课程」打开时同屏也有别周的课，它们不点亮
        if (!course.isActiveInWeek(day.displayWeek)) return@mapNotNull null
        if (day.isHoliday && course.id !in day.exemptCourseIds) return@mapNotNull null
        if (course.id in day.cancelledCourseIds) return@mapNotNull null
        val start = course.getEffectiveStartTime(sectionTimes)?.let(::parseHourMinute)
            ?: return@mapNotNull null
        val end = course.getEffectiveEndTime(sectionTimes)?.let(::parseHourMinute)
            ?: return@mapNotNull null
        if (!coursePeriodOngoing(start, end, now)) return@mapNotNull null
        course.id
    }.toSet()
}

/** 一页：翻周位置的解析 + 按视图模式分发。 */
@Composable
private fun ScheduleWeekPage(
    physicalPage: Int,
    jumpNeighbor: PagerJumpNeighbor?,
    pageState: PageSwitchState,
    viewModel: CourseViewModel,
    weekData: WeekDataCache,
    currentWeek: Int,
    config: WeekPageConfig,
    scheduleViewMode: ScheduleViewMode,
    currentSectionState: State<Int>,
    pendingSlot: PendingSlotState,
    onPendingChange: (Int, Int) -> Unit,
    draggingSlotsState: DraggingSlotsState,
    touchState: ScheduleTouchState,
    scrollInProgress: MutableState<Boolean>,
    glassRedrawSignal: () -> Unit,
    overScrollState: OverScrollState,
    ongoingCourseIdsState: OngoingCourseIdsState,
    externalScrollState: ScrollState,
    scheduleScrollBehavior: SharedScrollBehavior?,
    dropHighlight: () -> Pair<Int, IntRange>?,
    dropHighlightOrigin: () -> Pair<Int, IntRange>?,
    onGridGeometryChange: (ScheduleGridGeometry) -> Unit,
    onCourseLongPress: (Course, Float, Float, Float, Float, Int) -> Unit,
    onEmptyLongPress: (Int, Int, Float, Float, Float, Float, Int, Int) -> Unit,
    onPopupStateChange: (Boolean) -> Unit,
    selection: CourseDetailState,
    dateForTeachingPosition: (Int, Int) -> LocalDate?,
    specialItemEditor: SpecialItemEditorState,
) {
    // 借位页：长距跳转期间这一页临时按目标周出内容
    val page = jumpNeighbor?.resolve(physicalPage) ?: physicalPage
    val week = page + 1
    // 借位页是「同一页换数据」：整屏文字直接换，不播交叉模糊
    val borrowedPage = jumpNeighbor?.isBorrowed(physicalPage) == true

    CompositionLocalProvider(LocalTextMotionInstant provides borrowedPage) {
        val dayRange = remember(weekData, week) { weekData.dayRange(week) }
        // 屏外页的玻璃不必逐帧重采样：只有 ±1 页可能被拖进视野。距离按物理页算，
        // 借位页（换数据但位置相邻）仍要跟手。引用必须稳定，否则玻璃节点会重录采样。
        val pageGlassRedraw = remember(physicalPage, glassRedrawSignal) {
            {
                if (abs(physicalPage - pageState.currentPage) <= 1) glassRedrawSignal()
            }
        }
        Box(modifier = Modifier.fillMaxSize().then(LocalPageStartPadding.current)) {
            CompositionLocalProvider(
                LocalSoftGlassSurface provides config.softGlassSurface,
                LocalGlassRedrawSignal provides pageGlassRedraw,
                LocalOverScrollState provides overScrollState,
                // 只有当前周这一页发光
                LocalOngoingCourseIds provides if (week == currentWeek) {
                    ongoingCourseIdsState
                } else {
                    EmptyOngoingCourseIds
                },
            ) {
                if (scheduleViewMode == ScheduleViewMode.TIMELINE) {
                    TimelineWeekPage(
                        page = page,
                        week = week,
                        dayRange = dayRange,
                        weekData = weekData,
                        config = config,
                        currentWeek = currentWeek,
                        viewModel = viewModel,
                        externalScrollState = externalScrollState,
                        scheduleScrollBehavior = scheduleScrollBehavior,
                        dateForTeachingPosition = dateForTeachingPosition,
                        onCourseLongPress = onCourseLongPress,
                        onPopupStateChange = onPopupStateChange,
                        selection = selection,
                        draggingSlotsState = draggingSlotsState,
                    )
                } else {
                    WeekGridPage(
                        page = page,
                        week = week,
                        dayRange = dayRange,
                        weekData = weekData,
                        config = config,
                        currentWeek = currentWeek,
                        currentSectionState = currentSectionState,
                        viewModel = viewModel,
                        pendingSlot = pendingSlot,
                        onPendingChange = onPendingChange,
                        draggingSlotsState = draggingSlotsState,
                        touchState = touchState,
                        scrollInProgress = scrollInProgress,
                        externalScrollState = externalScrollState,
                        scheduleScrollBehavior = scheduleScrollBehavior,
                        pageState = pageState,
                        dropHighlight = dropHighlight,
                        dropHighlightOrigin = dropHighlightOrigin,
                        onGridGeometryChange = onGridGeometryChange,
                        onCourseLongPress = onCourseLongPress,
                        onEmptyLongPress = onEmptyLongPress,
                        onPopupStateChange = onPopupStateChange,
                        selection = selection,
                        dateForTeachingPosition = dateForTeachingPosition,
                        specialItemEditor = specialItemEditor,
                    )
                }
            }
        }
    }
}

/** 时间轴视图：同一个 pager 页，把节次纵轴换成真实时间纵轴。 */
@Composable
private fun TimelineWeekPage(
    page: Int,
    week: Int,
    dayRange: List<Int>,
    weekData: WeekDataCache,
    config: WeekPageConfig,
    currentWeek: Int,
    viewModel: CourseViewModel,
    externalScrollState: ScrollState,
    scheduleScrollBehavior: SharedScrollBehavior?,
    dateForTeachingPosition: (Int, Int) -> LocalDate?,
    onCourseLongPress: (Course, Float, Float, Float, Float, Int) -> Unit,
    onPopupStateChange: (Boolean) -> Unit,
    selection: CourseDetailState,
    draggingSlotsState: DraggingSlotsState,
) {
    // 空白处点出的待添加槽位（只在本页有效）
    var pendingAdd by remember(page) { mutableStateOf<TimelinePendingAdd?>(null) }
    // 列表身份要稳定：TimelineWeekGrid 按 days 内容做 remember，每次重组新建会重复布局
    val timelineDays = remember(dayRange, weekData, week) {
        dayRange.map { day ->
            val info = weekData.day(week, day)
            TimelineDay(
                day = day,
                displayWeek = info?.displayWeek ?: week,
                displayDay = info?.displayDay ?: day,
                courses = info?.courses.orEmpty(),
                cancelledIds = info?.cancelledCourseIds.orEmpty(),
                exemptIds = info?.exemptCourseIds.orEmpty(),
                isHoliday = info?.isHoliday ?: false,
                isWorkSwap = info?.isWorkSwap ?: false,
            )
        }
    }
    TimelineWeekGrid(
        days = timelineDays,
        sectionTimes = config.sectionTimes,
        isTablet = config.isTablet,
        isCurrentWeekView = week == currentWeek,
        style = TimelineCardStyle(
            isDark = config.isDark,
            softGlassSurface = config.softGlassSurface,
            cardAlpha = config.cardAlpha,
            cardSurfaceAlpha = config.cardSurfaceAlpha,
            solidBacking = config.solidBacking,
            cardHeightPerSection = config.cardHeightPerSection,
            cardCornerRadius = config.cardCornerRadius,
            cardContentAlignment = config.cardContentAlignment,
            cardTextColor = config.cardTextColor,
            cardTextScale = config.cardTextScale,
            showClassroom = config.showClassroom,
            showTeacher = config.showTeacher,
        ),
        contentTopPadding = config.contentTopPadding,
        contentBottomPadding = config.endSpacer,
        scrollState = externalScrollState,
        scrollBehavior = scheduleScrollBehavior,
        pendingAdd = pendingAdd,
        // 点空白：先立一张加号卡片（上下中心落在点下的那一分钟）
        onEmptyTap = { day, minute ->
            if (dateForTeachingPosition(week, day.day) != null) {
                val section = sectionAtMinute(config.sectionTimes, minute)
                pendingAdd = TimelinePendingAdd(
                    day = day.day,
                    centerMinute = minute,
                    durationMinutes = sectionMinutesOf(config.sectionTimes, section),
                )
            }
        },
        // 点加号卡片：按自定义时间新建，起止就是卡片覆盖的那一段
        onPendingAddClick = { day, startMinute, durationMinutes ->
            val section = sectionAtMinute(config.sectionTimes, startMinute)
            pendingAdd = null
            viewModel.showAddDialog(
                dayOfWeek = if (day.isWorkSwap) day.displayDay else day.day,
                startSection = section,
                endSection = section,
                defaultWeeks = if (day.isWorkSwap) setOf(day.displayWeek) else emptySet(),
                customStartTime = minuteText(startMinute),
                customEndTime = minuteText((startMinute + durationMinutes) % (24 * 60)),
            )
        },
        onCourseLongPress = onCourseLongPress,
        hiddenSlotsState = draggingSlotsState,
        // 与网格卡片同一入口：调休列用该列映射后的周次查槽位
        onCourseClick = { course, displayWeek ->
            val coursesAtSlot = viewModel.getCoursesAtSlot(
                displayWeek,
                course.dayOfWeek,
                course.startSection,
                course.endSection,
            )
            selection.open(coursesAtSlot, course)
            onPopupStateChange(true)
        },
        modifier = Modifier.fillMaxSize(),
        glassBehindCards = { _ ->
            // 柔光玻璃由每张卡自己画
        },
    )
}

/** 网格视图：整页滚动、星期行、课程卡、特殊横带与分界带。 */
@Composable
private fun WeekGridPage(
    page: Int,
    week: Int,
    dayRange: List<Int>,
    weekData: WeekDataCache,
    config: WeekPageConfig,
    currentWeek: Int,
    currentSectionState: State<Int>,
    viewModel: CourseViewModel,
    pendingSlot: PendingSlotState,
    onPendingChange: (Int, Int) -> Unit,
    draggingSlotsState: DraggingSlotsState,
    touchState: ScheduleTouchState,
    scrollInProgress: MutableState<Boolean>,
    externalScrollState: ScrollState,
    scheduleScrollBehavior: SharedScrollBehavior?,
    pageState: PageSwitchState,
    dropHighlight: () -> Pair<Int, IntRange>?,
    dropHighlightOrigin: () -> Pair<Int, IntRange>?,
    onGridGeometryChange: (ScheduleGridGeometry) -> Unit,
    onCourseLongPress: (Course, Float, Float, Float, Float, Int) -> Unit,
    onEmptyLongPress: (Int, Int, Float, Float, Float, Float, Int, Int) -> Unit,
    onPopupStateChange: (Boolean) -> Unit,
    selection: CourseDetailState,
    dateForTeachingPosition: (Int, Int) -> LocalDate?,
    specialItemEditor: SpecialItemEditorState,
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
            .then(
                if (scheduleScrollBehavior != null) {
                    Modifier.nestedScroll(scheduleScrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                }
            )
            .verticalScroll(externalScrollState)
            // 底部留白直接计入滚动内容高度；强制无界测量
            .layout { measurable, constraints ->
                val topPad = config.contentTopPadding.roundToPx().coerceAtLeast(0)
                val bottomPad = config.endSpacer.roundToPx().coerceAtLeast(0)
                val placeable = measurable.measure(
                    constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                )
                layout(placeable.width, placeable.height + topPad + bottomPad) {
                    placeable.place(0, topPad)
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val dayBoundsArray = remember { arrayOfNulls<FloatArray>(8) }
            var lastDayBoundsVersion by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                snapshotFlow { scrollInProgress.value }.collect { scrolling ->
                    if (!scrolling) lastDayBoundsVersion++
                }
            }

            // 用当前（实时写入的）dayBounds 数组拼几何；长按瞬间同步推一次
            fun buildGridGeometry(): ScheduleGridGeometry {
                val boundsMap = mutableMapOf<Int, FloatArray>()
                val dataMap = mutableMapOf<Int, Pair<Int, Int>>()
                val blocked = mutableSetOf<Int>()
                for (dayOfWeek in 1..7) {
                    dayBoundsArray[dayOfWeek]?.let { boundsMap[dayOfWeek] = it }
                    weekData.day(week, dayOfWeek)?.let { dataMap[dayOfWeek] = it.displayDay to it.displayWeek }
                    // 教学周重组休课日：dateForPosition 返回 null，该列永远空白
                    if (dateForTeachingPosition(week, dayOfWeek) == null) blocked.add(dayOfWeek)
                }
                return ScheduleGridGeometry(
                    dayBounds = boundsMap,
                    sectionHeightPx = with(density) { config.cardHeightPerSection.dp.toPx() },
                    morningSections = config.morningSections,
                    afternoonSections = config.afternoonSections,
                    eveningSections = config.eveningSections,
                    showBreakDividers = config.showBreakDividers,
                    columnDataPosition = dataMap,
                    pageWeek = week,
                    blockedColumns = blocked,
                    sectionTopDp = config.grid.sectionTop,
                )
            }

            val pushFreshGeometry by rememberUpdatedState {
                onGridGeometryChange(buildGridGeometry())
            }

            // 特殊横带按同一套列宽切分内部星期子块
            val pageColumnDays = remember(dayRange, weekData, week) {
                dayRange.map { column -> weekData.day(week, column)?.displayDay ?: column }
            }
            val bandStartPadding = (if (config.isTablet) 24.dp else 0.dp) +
                scheduleSectionColumnWidth(config.isTablet)
            val bandEndPadding = if (config.isTablet) 24.dp else 2.dp

            val bandPresence = rememberRevealPresence(config.grid.specialBands) {
                // 起止时间一起进 key：Gson 旁路可能让多个块 id 都落成 0
                "${it.blockId}|${it.startTime}|${it.endTime}"
            }
            bandPresence.forEach { presence ->
                val band = presence.value
                key(presence.key) {
                    val bandReveal = rememberScheduleRevealState(
                        visible = presence.visible,
                        animateEnter = presence.animateEnter,
                    )
                    val bandRevealModifier = if (bandReveal.isAnimating) {
                        Modifier.scheduleRevealLayer(bandReveal, blur = false, transform = false)
                    } else {
                        Modifier
                    }
                    val bandItems = remember(config.specialBlocks, band.blockId) {
                        config.specialBlocks.firstOrNull { it.id == band.blockId }?.safeItems
                            ?: emptyList()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(band.height.dp)
                            .offset(y = band.top.dp)
                            .then(bandRevealModifier)
                            // end 与下方 Row 的 end padding 一致，子块才能逐列对齐
                            .padding(start = bandStartPadding, end = bandEndPadding)
                    ) {
                        SpecialBandOverlay(
                            name = band.name,
                            startTime = band.startTime,
                            endTime = band.endTime,
                            hasBlur = config.hasWallpaper,
                            isDark = config.isDark,
                            cardCornerRadius = config.cardCornerRadius,
                            cardAlpha = config.cardAlpha,
                            cardSurfaceAlpha = config.cardSurfaceAlpha,
                            isTablet = config.isTablet,
                            softGlassSurface = config.softGlassSurface,
                            items = bandItems,
                            dayRange = pageColumnDays,
                        )
                    }
                }
            }

            // 调课落点示意：跨列/节连续位移，出现从源格滑入，消失仅透明度
            AnimatedDropTargetMask(
                dropHighlight = dropHighlight,
                dropHighlightOrigin = dropHighlightOrigin,
                pageDayRange = dayRange,
                grid = config.grid,
                cardHeightPerSection = config.cardHeightPerSection,
                cardCornerRadius = config.cardCornerRadius,
                softGlassSurface = config.softGlassSurface,
                isTablet = config.isTablet,
                isDark = config.isDark,
                hasWallpaper = config.hasWallpaper,
            )

            val gridReveal = rememberScheduleRevealState(config.showWeekGridLines)
            val lineColor = MiuixTheme.colorScheme.onSurface.copy(
                alpha = if (config.isDark) 0.09f else 0.05f
            )
            val sectionWidthDp = scheduleSectionColumnWidth(config.isTablet)
            val hairlinePx = remember(density) { with(density) { 1.dp.toPx() }.coerceAtLeast(1f) }
            val sectionWidthPx = remember(sectionWidthDp, density) {
                with(density) { sectionWidthDp.toPx() }
            }
            val breakBandsPx: List<Pair<Float, Float>> = remember(
                config.showBreakDividers, config.grid, density
            ) {
                if (config.showBreakDividers) {
                    val bandHeightPx = with(density) { 24.dp.toPx() }
                    config.grid.dividerY.map { topDp ->
                        val topPx = with(density) { topDp.dp.toPx() }
                        topPx to topPx + bandHeightPx
                    }
                } else {
                    emptyList()
                }
            }
            val gridRowYsPx = remember(config.grid, breakBandsPx, density, config.totalSections) {
                buildList {
                    for (section in 1..config.totalSections) {
                        val yDp = config.grid.sectionTop[section] ?: continue
                        add(with(density) { yDp.dp.toPx() })
                    }
                    breakBandsPx.forEach { (top, bottom) ->
                        add(top)
                        add(bottom)
                    }
                }.sorted().toFloatArray()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (config.isTablet) {
                            Modifier.padding(horizontal = 24.dp)
                        } else {
                            Modifier.padding(end = 2.dp)
                        }
                    )
                    .drawBehind {
                        val progress = gridReveal.value
                        // 关闭时整条绘制直接跳过，稳态不产生任何线段
                        if (progress <= 0f) return@drawBehind
                        val line = lineColor.copy(alpha = lineColor.alpha * progress)
                        val hairline = hairlinePx
                        val columnCount = dayRange.size
                        val dayWidthPx = if (columnCount > 0) {
                            (size.width - sectionWidthPx) / columnCount
                        } else {
                            0f
                        }
                        // 分界带中间不画，不贯穿午/晚休
                        val topEdgeY = hairline / 2f
                        val bottomEdgeY = size.height - hairline / 2f
                        drawLine(
                            color = line,
                            start = Offset(sectionWidthPx, topEdgeY),
                            end = Offset(size.width, topEdgeY),
                            strokeWidth = hairline,
                        )
                        var lastY = topEdgeY
                        for (y in gridRowYsPx) {
                            val lineY = y.coerceIn(topEdgeY, bottomEdgeY)
                            if (lineY <= lastY + 1f) continue
                            lastY = lineY
                            drawLine(
                                color = line,
                                start = Offset(sectionWidthPx, lineY),
                                end = Offset(size.width, lineY),
                                strokeWidth = hairline,
                            )
                        }
                        if (bottomEdgeY > lastY + 1f) {
                            drawLine(
                                color = line,
                                start = Offset(sectionWidthPx, bottomEdgeY),
                                end = Offset(size.width, bottomEdgeY),
                                strokeWidth = hairline,
                            )
                        }
                        if (dayWidthPx > 0f) {
                            for (index in 0..columnCount) {
                                val x = if (index == columnCount) {
                                    // 内缩半个线宽，右封边才不会被裁掉一半
                                    size.width - hairline / 2f
                                } else {
                                    sectionWidthPx + dayWidthPx * index
                                }
                                var segStart = 0f
                                for ((bandTop, bandBottom) in breakBandsPx) {
                                    val segEnd = bandTop.coerceIn(0f, size.height).coerceAtLeast(segStart)
                                    if (segEnd > segStart) {
                                        drawLine(
                                            color = line,
                                            start = Offset(x, segStart),
                                            end = Offset(x, segEnd),
                                            strokeWidth = hairline,
                                        )
                                    }
                                    segStart = bandBottom.coerceIn(0f, size.height).coerceAtLeast(segStart)
                                }
                                if (segStart < size.height) {
                                    drawLine(
                                        color = line,
                                        start = Offset(x, segStart),
                                        end = Offset(x, size.height),
                                        strokeWidth = hairline,
                                    )
                                }
                            }
                        }
                    }
            ) {
                // provider 实例要稳定，订阅才落在时间列自身而不带整页重组
                val currentSectionProvider = remember(week, currentWeek, currentSectionState) {
                    { if (week == currentWeek) currentSectionState.value else -1 }
                }
                SectionColumn(
                    totalSections = config.totalSections,
                    morningSections = config.morningSections,
                    afternoonSections = config.afternoonSections,
                    eveningSections = config.eveningSections,
                    sectionTimes = config.sectionTimes,
                    sectionNames = config.sectionNames,
                    specialBlocks = config.specialBlocks,
                    grid = config.grid,
                    cardHeightPerSection = config.cardHeightPerSection,
                    showBreakDividers = config.showBreakDividers,
                    currentSectionProvider = currentSectionProvider,
                    isTablet = config.isTablet,
                )

                dayRange.forEach { dayOfWeek ->
                    val dayInfo = weekData.day(week, dayOfWeek)
                    val isHoliday = dayInfo?.isHoliday ?: false
                    val isWorkSwap = dayInfo?.isWorkSwap ?: false
                    val displayWeekForDay = dayInfo?.displayWeek ?: week
                    val displayDayForCol = dayInfo?.displayDay ?: dayOfWeek
                    val holidayExemptCourseIds = dayInfo?.exemptCourseIds.orEmpty()
                    val holidayCancelledCourseIds = dayInfo?.cancelledCourseIds.orEmpty()
                    // 调课日空白格添加：落到 follow 星期/周
                    val addDayForCol = if (isWorkSwap) displayDayForCol else dayOfWeek
                    val addWeekForCol = if (isWorkSwap) displayWeekForDay else -1

                    val stableOnCourseClick: (Course) -> Unit = remember(
                        viewModel, selection, onPopupStateChange, page, dayOfWeek, week, displayWeekForDay
                    ) {
                        { course ->
                            val coursesAtSlot = viewModel.getCoursesAtSlot(
                                displayWeekForDay,
                                course.dayOfWeek,
                                course.startSection,
                                course.endSection,
                            )
                            selection.open(coursesAtSlot, course)
                            onPopupStateChange(true)
                        }
                    }
                    // 休课日列永远渲染为空：键里带 dateForTeachingPosition，改完重组回主页不会沿用旧判断
                    val pausedColumn = remember(page, dayOfWeek, dateForTeachingPosition) {
                        dateForTeachingPosition(week, dayOfWeek) == null
                    }
                    val stableOnEmptyClick: (Int) -> Unit = remember(
                        viewModel, dayOfWeek, addDayForCol, addWeekForCol, pausedColumn
                    ) {
                        { section ->
                            if (!pausedColumn) {
                                val defaultWeeks =
                                    if (addWeekForCol > 0) setOf(addWeekForCol) else emptySet()
                                viewModel.showAddDialog(addDayForCol, section, null, defaultWeeks)
                            }
                        }
                    }
                    val stableOnEmptyLongPress: (Int, Float, Float, Float, Float) -> Unit = remember(
                        dayOfWeek, addDayForCol, addWeekForCol, onEmptyLongPress, pausedColumn
                    ) {
                        { section, centerX, cellTopY, width, height ->
                            if (!pausedColumn) {
                                // 长按进菜单时清掉 pending，避免两层交互叠加
                                pendingSlot.slot = -1 to -1
                                onEmptyLongPress(
                                    dayOfWeek, section, centerX, cellTopY, width, height,
                                    addDayForCol, addWeekForCol,
                                )
                            }
                        }
                    }
                    val stableOnCourseLongPress: (Course, Float, Float, Float, Float, Int) -> Unit = remember(
                        page, dayOfWeek, onCourseLongPress
                    ) {
                        { course, left, top, width, height, cWeek ->
                            // 同步补一次最新几何，保证上层锚点用当前页 dayBounds
                            pushFreshGeometry()
                            onCourseLongPress(course, left, top, width, height, cWeek)
                        }
                    }

                    DayColumn(
                        dayOfWeek = dayOfWeek,
                        touchState = touchState,
                        courses = dayInfo?.courses.orEmpty(),
                        onCourseClick = stableOnCourseClick,
                        onCourseLongPress = stableOnCourseLongPress,
                        onEmptyClick = stableOnEmptyClick,
                        onEmptyLongPress = stableOnEmptyLongPress,
                        morningSections = config.morningSections,
                        afternoonSections = config.afternoonSections,
                        eveningSections = config.eveningSections,
                        sectionTimes = config.sectionTimes,
                        specialBlocks = config.specialBlocks,
                        grid = config.grid,
                        currentWeek = displayWeekForDay,
                        isHoliday = isHoliday,
                        holidayExemptCourseIds = holidayExemptCourseIds,
                        holidayCancelledCourseIds = holidayCancelledCourseIds,
                        isWorkSwap = isWorkSwap,
                        pendingSlot = pendingSlot,
                        onPendingChange = onPendingChange,
                        softGlassSurface = config.softGlassSurface,
                        solidBackingColor = config.solidBacking,
                        cardAlpha = config.cardAlpha,
                        cardSurfaceAlpha = config.cardSurfaceAlpha,
                        cardHeightPerSection = config.cardHeightPerSection,
                        cardCornerRadius = config.cardCornerRadius,
                        showBreakDividers = config.showBreakDividers,
                        isTablet = config.isTablet,
                        cardContentAlignment = config.cardContentAlignment,
                        cardTextColor = config.cardTextColor,
                        cardTextScale = config.cardTextScale,
                        showClassroom = config.showClassroom,
                        showTeacher = config.showTeacher,
                        draggingSlotsState = draggingSlotsState,
                        isDark = config.isDark,
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInRoot()
                                val width = coordinates.size.width.toFloat()
                                val bounds = dayBoundsArray[dayOfWeek]
                                // 滑动中只写数组不递增版本号，避免逐帧重组
                                if (bounds == null) {
                                    dayBoundsArray[dayOfWeek] = floatArrayOf(
                                        position.x, position.x + width, position.y
                                    )
                                    if (!touchState.scrolling) lastDayBoundsVersion++
                                } else if (bounds[0] != position.x ||
                                    bounds[1] != position.x + width ||
                                    bounds[2] != position.y
                                ) {
                                    bounds[0] = position.x
                                    bounds[1] = position.x + width
                                    bounds[2] = position.y
                                    if (!touchState.scrolling) lastDayBoundsVersion++
                                }
                            }
                    )
                }
            }

            // 点击层必须在 Row 之上：空节次层会消费整列点击
            config.grid.specialBands.forEach { band ->
                val bandItems = remember(config.specialBlocks, band.blockId) {
                    config.specialBlocks.firstOrNull { it.id == band.blockId }?.safeItems
                        ?: emptyList()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(band.height.dp)
                        .offset(y = band.top.dp)
                        .padding(start = bandStartPadding, end = bandEndPadding)
                ) {
                    SpecialBandClickLayer(
                        items = bandItems,
                        dayRange = pageColumnDays,
                        onItemClick = { item ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            specialItemEditor.openItem(band.blockId, item)
                        },
                        onEmptyClick = { day ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            specialItemEditor.openEmpty(band.blockId, day)
                        },
                    )
                }
            }

            // 仅当前页上报；几何实际变化时才触发回调
            val sectionHeightPx = with(density) { config.cardHeightPerSection.dp.toPx() }
            val prevBoundsVersion = remember { mutableIntStateOf(lastDayBoundsVersion) }
            val prevSectionHeight = remember { mutableFloatStateOf(sectionHeightPx) }
            val prevMorning = remember { mutableIntStateOf(config.morningSections) }
            val prevAfternoon = remember { mutableIntStateOf(config.afternoonSections) }
            val prevEvening = remember { mutableIntStateOf(config.eveningSections) }
            val prevShowBreak = remember { mutableStateOf(config.showBreakDividers) }
            SideEffect {
                if (page == pageState.currentPage) {
                    val changed = prevBoundsVersion.intValue != lastDayBoundsVersion ||
                        prevSectionHeight.floatValue != sectionHeightPx ||
                        prevMorning.intValue != config.morningSections ||
                        prevAfternoon.intValue != config.afternoonSections ||
                        prevEvening.intValue != config.eveningSections ||
                        prevShowBreak.value != config.showBreakDividers
                    if (changed) {
                        prevBoundsVersion.intValue = lastDayBoundsVersion
                        prevSectionHeight.floatValue = sectionHeightPx
                        prevMorning.intValue = config.morningSections
                        prevAfternoon.intValue = config.afternoonSections
                        prevEvening.intValue = config.eveningSections
                        prevShowBreak.value = config.showBreakDividers
                        onGridGeometryChange(buildGridGeometry())
                    }
                }
            }
            // 翻周停下后补推一次几何：SideEffect 只在组合重跑时推，落点会用到上一页周次
            LaunchedEffect(Unit) {
                snapshotFlow { pageState.isAnimating }.collect { animating ->
                    if (!animating && page == pageState.currentPage) {
                        onGridGeometryChange(buildGridGeometry())
                    }
                }
            }

            val morningHeight = config.grid.dividerY.getOrNull(0)?.toInt()
                ?: (config.morningSections * config.cardHeightPerSection).toInt()
            val afternoonHeight = (config.afternoonSections * config.cardHeightPerSection).toInt()
            val dividerOffset = if (config.showBreakDividers) 24 else 0
            val dinnerBreakY = config.grid.dividerY.getOrNull(1)?.toInt()
                ?: (morningHeight + dividerOffset + afternoonHeight)

            // 两条分界带贴着节次几何画，直接显隐
            if (config.showBreakDividers) {
                Box(modifier = Modifier.matchParentSize()) {
                    BreakDivider(offsetY = morningHeight, text = SchedStr.lunchBreak(), config = config)
                    BreakDivider(offsetY = dinnerBreakY, text = SchedStr.dinnerBreak(), config = config)
                }
            }
        }
    }
}

/** 午/晚休分界带；玻璃与纯色两条路径共用同一几何。 */
@Composable
private fun BreakDivider(offsetY: Int, text: String, config: WeekPageConfig) {
    val dividerCornerRadius = 10.dp
    val dividerShape = remember { ContinuousRoundedRectangle(dividerCornerRadius) }
    val dividerHorizontalPadding = if (config.isTablet) 24.dp else 4.dp
    val density = LocalDensity.current
    val baseColor = when {
        config.hasWallpaper -> Color.Transparent
        MiuixTheme.isDynamicColor -> MiuixTheme.colorScheme.surfaceContainerHigh
        config.isDark -> Color(0xFF121212)
        else -> Color(0xFFF0F0F0)
    }
    // 12% 锚定当前视觉；百分比映射到 0..1（仅作用于有壁纸的玻璃表面）
    val surfaceAlphaAt12 = if (config.isDark) 0.64f else 0.50f
    val progress = config.cardSurfaceAlpha.coerceIn(0f, 1f)
    val surfaceAlpha = if (progress <= 0.12f) {
        (progress / 0.12f) * surfaceAlphaAt12
    } else {
        surfaceAlphaAt12 + ((progress - 0.12f) / 0.88f) * (1f - surfaceAlphaAt12)
    }
    // 无壁纸底色保持实色，不随「卡片不透明度」变化
    val surfaceColor = if (MiuixTheme.isDynamicColor) {
        MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = surfaceAlpha)
    } else if (config.isDark) {
        Color(0xFF323232).copy(alpha = surfaceAlpha)
    } else {
        Color.White.copy(alpha = surfaceAlpha)
    }
    val origin = remember { arrayOf(Offset.Zero) }
    val glassModifier = rememberPanelGlassModifier(
        softGlassSurface = config.softGlassSurface,
        cornerRadius = dividerCornerRadius.value,
        origin = { origin[0] },
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = offsetY.dp)
            .height(24.dp)
            .padding(vertical = 2.dp)
            .padding(horizontal = dividerHorizontalPadding)
            .onGloballyPositioned { origin[0] = it.positionInWindow() }
            .background(baseColor, dividerShape)
            .then(
                if (config.hasWallpaper && glassModifier != null) {
                    Modifier.then(glassModifier).background(surfaceColor, dividerShape)
                } else if (config.hasWallpaper) {
                    Modifier.background(surfaceColor, dividerShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            textAlign = TextAlign.Center,
        )
    }
}

/** 拖拽落点遮罩：位置/尺寸弹簧跟随，出现从源格滑入，消失仅淡出。 */
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
) {
    val density = LocalDensity.current
    val left = remember { Animatable(0f) }
    val top = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    val height = remember { Animatable(0f) }
    val maskAlpha = remember { Animatable(0f) }
    val lastTarget = remember { mutableStateOf<DropMaskBox?>(null) }

    // 落点在这里求值：跨格时只重组遮罩这一小块
    val highlight = dropHighlight()
    val originHighlight = dropHighlightOrigin()

    // 没有落点且遮罩已淡尽时不挂这棵树；判据走 derivedStateOf 避免逐帧重建子组合
    val hasTarget = highlight != null || originHighlight != null
    val mounted by remember(hasTarget) {
        derivedStateOf { hasTarget || maskAlpha.value > 0.01f }
    }
    if (!mounted) return

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(grid.totalHeight.dp)
    ) {
        val sectionWidth = with(density) { scheduleSectionColumnWidth(isTablet).toPx() }
        val padStart = with(density) { (if (isTablet) 24 else 0).dp.toPx() }
        val padEnd = with(density) { (if (isTablet) 24 else 2).dp.toPx() }
        val padH = with(density) { 2.dp.toPx() }
        val padV = with(density) { 2.dp.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val dayCount = pageDayRange.size.coerceAtLeast(1)
        val dayW = ((maxW - padStart - sectionWidth - padEnd) / dayCount).coerceAtLeast(0f)
        val dayAreaLeft = padStart + sectionWidth
        val cornerPx = with(density) { cardCornerRadius.dp.toPx() }

        val boxOf: (Pair<Int, IntRange>?) -> DropMaskBox? = remember(
            pageDayRange, grid, cardHeightPerSection, dayW, dayAreaLeft, density, padH, padV
        ) {
            { target ->
                if (target == null) {
                    null
                } else {
                    val dayIndex = pageDayRange.indexOf(target.first)
                    if (dayIndex < 0) {
                        null
                    } else {
                        // 用 sectionTop 差值算高度：跨午休/晚修时计入分界缝与特殊块挤占
                        val first = target.second.first
                        val last = target.second.last
                        val topDp = grid.sectionTop[first] ?: 0f
                        val bottomDp = (grid.sectionTop[last] ?: topDp) + cardHeightPerSection
                        val heightDp = (bottomDp - topDp).coerceAtLeast(cardHeightPerSection)
                        DropMaskBox(
                            left = dayAreaLeft + dayIndex * dayW + padH,
                            top = with(density) { topDp.dp.toPx() } + padV,
                            width = (dayW - padH * 2).coerceAtLeast(0f),
                            height = (with(density) { heightDp.dp.toPx() } - padV * 2).coerceAtLeast(0f),
                        )
                    }
                }
            }
        }
        val targetBox = remember(highlight, boxOf) { boxOf(highlight) }
        val originBox = remember(originHighlight, boxOf) { boxOf(originHighlight) }

        LaunchedEffect(targetBox) {
            if (targetBox == null) {
                maskAlpha.animateTo(
                    0f,
                    tween(durationMillis = 180, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
                )
                lastTarget.value = null
            } else {
                val prev = lastTarget.value
                val wasVisible = maskAlpha.value > 0.05f && prev != null
                val spec = spring<Float>(dampingRatio = 0.95f, stiffness = 800f)
                if (!wasVisible) {
                    // 出现：从源课格连续滑入落点（无源时在落点淡入）
                    val from = originBox ?: targetBox
                    left.snapTo(from.left)
                    top.snapTo(from.top)
                    width.snapTo(from.width)
                    height.snapTo(from.height)
                    maskAlpha.snapTo(0f)
                    launch {
                        maskAlpha.animateTo(
                            1f,
                            tween(durationMillis = 180, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))
                        )
                    }
                    if (from != targetBox) {
                        launch { left.animateTo(targetBox.left, spec) }
                        launch { top.animateTo(targetBox.top, spec) }
                        launch { width.animateTo(targetBox.width, spec) }
                        launch { height.animateTo(targetBox.height, spec) }
                    }
                } else {
                    launch { left.animateTo(targetBox.left, spec) }
                    launch { width.animateTo(targetBox.width, spec) }
                    launch { height.animateTo(targetBox.height, spec) }
                    launch { top.animateTo(targetBox.top, spec) }
                    if (maskAlpha.value < 1f) {
                        launch { maskAlpha.animateTo(1f, tween(durationMillis = 180)) }
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
        } else if (isDark) {
            Color(0xFF242424).copy(alpha = 0.64f)
        } else {
            Color(0xFFF0F0F0).copy(alpha = 0.5f)
        }
        val maskShape = remember(cardCornerRadius) { ContinuousRoundedRectangle(cardCornerRadius.dp) }
        val maskOrigin = remember { arrayOf(Offset.Zero) }
        val glassRedraw = LocalGlassRedrawSignal.current
        // 遮罩位置/尺寸由 Animatable 每帧驱动，必须读进绘制失效信号
        val maskRedraw: () -> Unit = remember(left, top, width, height, glassRedraw) {
            {
                left.value
                top.value
                width.value
                height.value
                glassRedraw()
            }
        }
        val maskGlassModifier = remember(
            softGlassSurface, cardCornerRadius, density, maskRedraw
        ) {
            softGlassSurface?.modifier(
                density = density,
                radiusPx = with(density) { cardCornerRadius.dp.toPx() },
                origin = { maskOrigin[0] },
                redraw = maskRedraw,
            )
        }

        if (hasWallpaper && maskGlassModifier != null) {
            // 柔光玻璃表面：offset + layout 跟动画几何，alpha 走 graphicsLayer
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(left.value.roundToInt(), top.value.roundToInt())
                    }
                    .layout { measurable, _ ->
                        val widthPx = width.value.roundToInt().coerceAtLeast(0)
                        val heightPx = height.value.roundToInt().coerceAtLeast(0)
                        val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
                        layout(widthPx, heightPx) { placeable.place(0, 0) }
                    }
                    .onGloballyPositioned { maskOrigin[0] = it.positionInWindow() }
                    .graphicsLayer { alpha = maskAlpha.value }
                    .then(maskGlassModifier)
                    .background(glassSurface, maskShape)
            )
        } else {
            // 纯色路径：drawBehind 读 Animatable，位移/尺寸/透明度均不触发重组
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(grid.totalHeight.dp)
                    // 独立图层：拖拽跨格时只重画这一层圆角矩形
                    .graphicsLayer { }
                    .drawBehind {
                        val value = maskAlpha.value
                        if (value <= 0.01f) return@drawBehind
                        drawRoundRect(
                            color = solidColor.copy(alpha = solidColor.alpha * value),
                            topLeft = Offset(left.value, top.value),
                            size = Size(width.value, height.value),
                            cornerRadius = CornerRadius(cornerPx),
                        )
                    }
            )
        }
    }
}

private data class DropMaskBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * 课程详情弹窗宿主。
 *
 * 查看周次只在本作用域读：翻页时只有弹窗自己重组，pager 与已组合的周页不受牵连。
 */
@Composable
private fun CourseDetailSheetHost(
    viewingWeek: State<Int>,
    show: Boolean,
    allCourses: List<Course>,
    selectedCourses: List<Course>,
    isTablet: Boolean,
    hiddenCourseIds: Set<String>,
    onDismiss: () -> Unit,
    onCourseClick: (
        courses: List<Course>,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        snapshot: TransitionLayerSnapshot?,
        courseIdToHide: String,
        targetWeek: Int,
    ) -> Unit,
    onNoteSave: (course: Course, week: Int?, text: String) -> Unit,
    onEdit: ((Course) -> Unit)?,
    endAction: @Composable (() -> Unit)?,
) {
    CourseDetailSheet(
        show = show,
        allCourses = allCourses,
        selectedCourses = selectedCourses,
        viewingWeek = viewingWeek.value,
        isTablet = isTablet,
        hiddenCourseIds = hiddenCourseIds,
        onDismiss = onDismiss,
        onCourseClick = onCourseClick,
        onNoteSave = onNoteSave,
        onEdit = onEdit,
        endAction = endAction,
    )
}

/** 周 pager 组合窗口的一次快照。 */
private data class PagerWindowSnapshot(
    val currentPage: Int,
    val settledPage: Int,
    val beyond: Int,
    val animating: Boolean,
    val dwelling: Boolean,
)

/**
 * 周 pager 宿主。
 *
 * 组合窗口的锚点在手势期间保持不动，停稳一小段时间后才追到落页：一次正常翻页全程
 * 只绘制已经组合好的页，新页的组合被挪到空闲帧，翻页动画里不再插入组合工作。
 * 首帧仍只组当前页，随后补相邻页。
 */
@Composable
private fun WeekPagerHost(
    state: PageSwitchState,
    pageCount: Int,
    swipeEnabled: Boolean,
    dwellHint: () -> Boolean,
    blockedPointerId: () -> PointerId? = { null },
    onVerticalScroll: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int) -> Unit,
) {
    // 0：首帧只有当前页，缩短冷启动首帧；80ms 后才把相邻页补进来
    var idleBeyond by remember(state) { mutableIntStateOf(0) }
    var anchorPage by remember(state) { mutableIntStateOf(state.currentPage) }
    // lambda 身份保持稳定，锚点变化只让 Pages 重组，不牵连 pager 宿主
    val anchorProvider = remember(state) { { anchorPage } }

    LaunchedEffect(state) {
        delay(80.milliseconds)
        idleBeyond = 1
        snapshotFlow {
            PagerWindowSnapshot(
                currentPage = state.currentPage,
                settledPage = state.settledPage,
                beyond = idleBeyond,
                animating = state.isAnimating,
                dwelling = dwellHint(),
            )
        }.collectLatest { snapshot ->
            // 兜底：窗口必须覆盖当前页（各级 beyond 都留了一页余量），跨多页时直接跳到落点
            val effectiveBeyond = if (snapshot.animating) maxOf(snapshot.beyond, 1) else snapshot.beyond
            val lagLimit = (effectiveBeyond - 1).coerceAtLeast(0)
            while (snapshot.currentPage - anchorPage > lagLimit) anchorPage++
            while (anchorPage - snapshot.currentPage > lagLimit) anchorPage--

            if (snapshot.animating) return@collectLatest
            // 停稳后先等一拍再换锚点：邻页预组合不再落在收尾动画帧上
            delay(120.milliseconds)
            if (state.isAnimating) return@collectLatest
            if (snapshot.settledPage != anchorPage) anchorPage = snapshot.settledPage
            // 完全空闲后再扩到 2/3，给连续翻页留出预组合余量
            delay(130.milliseconds)
            if (!state.isAnimating) {
                idleBeyond = if (snapshot.dwelling) 3 else 2
            }
        }
    }
    HorizontalPageSwitch(
        state = state,
        pageCount = pageCount,
        modifier = modifier,
        swipeEnabled = swipeEnabled,
        blockedPointerId = blockedPointerId,
        onVerticalScroll = onVerticalScroll,
        // 手势期间不收缩窗口：收缩会丢掉马上要用的邻页，松手时再组合回来
        beyondPageCount = if (state.isAnimating) maxOf(idleBeyond, 1) else idleBeyond,
        windowAnchor = anchorProvider,
        content = content,
    )
}

/** 壁纸缩放回弹的触发状态：只有 [WallpaperBounceBack] 订阅。 */
@Stable
private class WallpaperBounceState {
    val trigger = mutableIntStateOf(0)
    val endScale = mutableFloatStateOf(1f)
}

@Composable
private fun WallpaperBounceBack(
    state: WallpaperBounceState,
    minScale: Float,
    onScaleChange: (Float) -> Unit,
) {
    val trigger = state.trigger.intValue
    LaunchedEffect(trigger) {
        val endScale = state.endScale.floatValue
        if (trigger > 0 && endScale < minScale) {
            animate(
                initialValue = endScale,
                targetValue = minScale,
                animationSpec = tween(
                    durationMillis = 350,
                    easing = CubicBezierEasing(0.34f, 1.1f, 0.3f, 1f),
                ),
            ) { value, _ -> onScaleChange(value) }
        }
    }
}

/** 壁纸编辑手势：单指/双指缩放与平移，松手回弹到最小缩放。 */
@Composable
private fun WallpaperEditLayer(
    bounceState: WallpaperBounceState,
    minScale: Float,
    scale: Float,
    offset: Offset,
    bitmap: ImageBitmap,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit,
) {
    val latestScale by rememberUpdatedState(scale)
    val latestOffset by rememberUpdatedState(offset)
    val latestBitmap by rememberUpdatedState(bitmap)
    val latestMinScale by rememberUpdatedState(minScale)
    val latestScreenWidth by rememberUpdatedState(screenWidthPx)
    val latestScreenHeight by rememberUpdatedState(screenHeightPx)
    val latestOnScaleChange by rememberUpdatedState(onScaleChange)
    val latestOnOffsetChange by rememberUpdatedState(onOffsetChange)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var gestureScale = latestScale
                    var lastDisplayScale = gestureScale
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        gestureScale *= zoom
                        val newScale = if (gestureScale < latestMinScale) {
                            val diff = gestureScale - latestMinScale
                            latestMinScale + diff * 0.3f
                        } else {
                            gestureScale
                        }
                        lastDisplayScale = newScale
                        val image = latestBitmap
                        if (image.width > 0 && image.height > 0) {
                            val fitScale = minOf(
                                latestScreenWidth / image.width,
                                latestScreenHeight / image.height,
                            )
                            val scaledW = image.width * fitScale * newScale
                            val scaledH = image.height * fitScale * newScale
                            val maxOffsetX = ((scaledW - latestScreenWidth) / 2f).coerceAtLeast(0f)
                            val maxOffsetY = ((scaledH - latestScreenHeight) / 2f).coerceAtLeast(0f)
                            val newOffset = latestOffset + pan
                            latestOnScaleChange(newScale)
                            latestOnOffsetChange(
                                Offset(
                                    newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                    newOffset.y.coerceIn(-maxOffsetY, maxOffsetY),
                                )
                            )
                        } else {
                            latestOnScaleChange(newScale)
                            latestOnOffsetChange(latestOffset + pan)
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    bounceState.endScale.floatValue = lastDisplayScale
                    bounceState.trigger.intValue++
                }
            }
    )
}

/**
 * 跨周调课：页面级唯一的跟手与收尾来源。
 *
 * 挂在 pager 上（跨周不重建），用 root 坐标算相对按下时的位移。课卡只负责长按并登记
 * 指针，真拖起来后才 consume。
 */
private fun Modifier.courseDragOwnerGesture(
    touchState: ScheduleTouchState,
    coordinates: Array<LayoutCoordinates?>,
    onDrag: (courseId: String, offsetX: Float, offsetY: Float) -> Unit,
    onDragStart: (courseId: String) -> Unit,
    onDragEnd: (courseId: String) -> Unit,
    onDragCancel: (courseId: String) -> Unit,
): Modifier = pointerInput(touchState) {
    awaitPointerEventScope {
        val slop = viewConfiguration.touchSlop
        var gestureActive = false
        var tracking = false
        var startRoot = Offset.Zero
        while (true) {
            // Initial：先于课卡 / verticalScroll / 翻页手势拿到本帧事件
            val event = awaitPointerEvent(PointerEventPass.Initial)

            // 多指统计：三指截图等系统手势拦掉误触发的点击与长按
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) {
                gestureActive = false
            } else {
                if (!gestureActive) touchState.multiTouch = false
                if (pressed > 1) touchState.multiTouch = true
                gestureActive = true
            }

            val id = touchState.pointerId
            if (id == null) {
                tracking = false
                continue
            }
            val change = event.changes.firstOrNull { it.id == id }
            if (change == null) {
                // 指针从事件流消失（系统 cancel）：别把浮层冻在最后一帧
                touchState.courseId?.let { courseId ->
                    if (touchState.dragStarted) onDragCancel(courseId) else onDrag(courseId, 0f, 0f)
                }
                touchState.clear()
                tracking = false
                continue
            }
            if (!tracking) {
                tracking = true
                startRoot = touchState.downRoot ?: dragOwnerRoot(coordinates, change.position)
            }
            val root = dragOwnerRoot(coordinates, change.position)
            val dx = root.x - startRoot.x
            val dy = root.y - startRoot.y

            if (!change.pressed) {
                touchState.courseId?.let { courseId ->
                    // 只是菜单上的手抖：偏移归零、菜单留着；真拖起来才按落点写库
                    if (touchState.dragStarted) onDragEnd(courseId) else onDrag(courseId, 0f, 0f)
                }
                touchState.clear()
                tracking = false
                continue
            }
            if (!touchState.dragStarted && Offset(dx, dy).getDistance() > slop) {
                touchState.dragStarted = true
                touchState.courseId?.let(onDragStart)
            }
            touchState.courseId?.let { onDrag(it, dx, dy) }
            // 只有真拖起来才拦位移
            if (touchState.dragStarted) change.consume()
        }
    }
}

private fun dragOwnerRoot(coordinates: Array<LayoutCoordinates?>, local: Offset): Offset {
    val coords = coordinates[0]
    return if (coords != null && coords.isAttached) coords.localToRoot(local) else local
}
