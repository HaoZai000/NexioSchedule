package com.haooz.chedule.ui.activities.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.appearance.CombinationDefaults
import com.haooz.chedule.data.holiday.HolidayCountdown
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.CourseScheduleDateBounds
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.repo.DayScheduleResolver
import com.haooz.chedule.data.util.coursePeriodCompleted
import com.haooz.chedule.data.util.coursePeriodOngoing
import com.haooz.chedule.data.util.formatChineseDate
import com.haooz.chedule.data.util.lengthOfMonth
import com.haooz.chedule.data.util.localDateTimeAt
import com.haooz.chedule.data.util.millisBetween
import com.haooz.chedule.data.util.nowLocalTime
import com.haooz.chedule.data.util.parseHourMinute
import com.haooz.chedule.data.util.plusDays
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.L10n
import com.haooz.chedule.i18n.TodayStr
import com.haooz.chedule.ui.activities.classschedule.ScheduleViewMode
import com.haooz.chedule.ui.activities.main.PagerJumpNeighbor
import com.haooz.chedule.ui.activities.main.jumpToPageSmoothly
import com.haooz.chedule.ui.components.CourseDetailSheet
import com.haooz.chedule.ui.components.LocalGlassRedrawSignal
import com.haooz.chedule.ui.components.tabletNavSamplingSignal
import com.haooz.chedule.ui.components.LocalPageStartPadding
import com.haooz.chedule.ui.components.LocalSoftGlassSurface
import com.haooz.chedule.ui.components.SoftGlassSurface
import com.haooz.chedule.ui.components.text.LocalTextMotionInstant
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.pagerAxisTakeoverGesture
import com.haooz.chedule.ui.utils.rememberPagerTakeoverGestureState
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import top.yukonga.miuix.kmp.utils.OverScrollState
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic


/**
 * [TodayScreen.todayAssistantCard] 槽位的数据载体（页面算好传给宿主渲染）。
 * 今日助手卡依赖 Android 天气栈（Gson / 定位 / Geocoder），留在 `:androidApp` 的 `TodayAssistant.kt`；
 * 本页只负责打包数据，渲染 lambda 由宿主注入。
 */
class TodayAssistantCardData(
    val courses: List<Course>,
    val tomorrowCourses: List<Course>,
    val sectionTimes: Map<Int, String>,
    val morningSections: Int,
    val afternoonSections: Int,
    val showClassroom: Boolean,
    val showTeacher: Boolean,
    val surfaceOpacity: Float,
)

@Composable
fun BlurCard(
    cornerRadius: Dp = 20.dp,
    surfaceOpacity: Float,
    modifier: Modifier = Modifier,
    transitionRecording: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val softGlass = LocalSoftGlassSurface.current
    val glassRedraw = LocalGlassRedrawSignal.current
    val isDark = isAppDarkTheme()

    if (softGlass != null) {
        val shape = remember(cornerRadius) { ContinuousRoundedRectangle(cornerRadius) }
        val density = LocalDensity.current
        val glassOrigin = remember { arrayOf(Offset.Zero) }
        val glassRadiusPx = with(density) { cornerRadius.toPx() }
        val softGlassModifier = remember(softGlass, glassRadiusPx, density, glassRedraw) {
            softGlass.modifier(
                density = density,
                origin = { glassOrigin[0] },
                radiusPx = glassRadiusPx,
                redraw = glassRedraw,
            )
        }
        val flatSurfaceColor = remember(isDark, surfaceOpacity) {
            if (isDark) Color.Black.copy(alpha = surfaceOpacity)
            else Color.White.copy(alpha = surfaceOpacity)
        }
        Box(
            modifier = modifier
                .clip(shape)
                // Record inside the source clip so its live subtree cannot replace retained rows.
                .then(transitionRecording)
                .onGloballyPositioned { glassOrigin[0] = it.positionInWindow() }
                .then(softGlassModifier)
                .background(flatSurfaceColor, shape)
        ) {
            content()
        }
    } else {
        Card(
            cornerRadius = cornerRadius,
            modifier = modifier,
            insideMargin = PaddingValues(0.dp)
        ) {
            content()
        }
    }
}


@Composable
private fun CourseItemContent(course: Course, sectionTimes: Map<Int, String>, pageDate: LocalDate = todayLocalDate(), showClassroom: Boolean = true, showTeacher: Boolean = true, week: Int = 0) {
    fun getSectionTimeRange(startSection: Int, endSection: Int): String {
        val startTime = sectionTimes[startSection]?.split("-")?.firstOrNull() ?: ""
        val endTime = sectionTimes[endSection]?.split("-")?.lastOrNull() ?: ""
        return if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
            "$startTime - $endTime"
        } else {
            TodayStr.sectionRange(startSection, endSection)
        }
    }

    fun parseTime(timeStr: String): LocalTime? {
        return try {
            parseHourMinute(timeStr)
        } catch (_: Exception) {
            null
        }
    }

    val timeRange = if (course.hasValidCustomTime()) {
        "${course.customStartTime} - ${course.customEndTime}"
    } else {
        getSectionTimeRange(course.startSection, course.endSection)
    }
    val startTimeStr = if (course.hasValidCustomTime()) {
        course.customStartTime ?: ""
    } else {
        sectionTimes[course.startSection]?.split("-")?.firstOrNull() ?: ""
    }
    val endTimeStr = if (course.hasValidCustomTime()) {
        course.customEndTime ?: ""
    } else {
        sectionTimes[course.endSection]?.split("-")?.lastOrNull() ?: ""
    }
    val startTime = parseTime(startTimeStr)
    val endTime = parseTime(endTimeStr)

    val initialStatus = remember(pageDate, startTime, endTime) {
        val today = todayLocalDate()
        when {
            pageDate != today ->
                if (pageDate < today) "已结束" else "未开始"
            startTime == null || endTime == null -> "未知"
            else -> {
                val now = nowLocalTime()
                when {
                    now < startTime -> "未开始"
                    now > endTime -> "已结束"
                    else -> "进行中"
                }
            }
        }
    }
    var courseStatus by remember { mutableStateOf(initialStatus) }

    LaunchedEffect(startTime, endTime, pageDate) {
        val today = todayLocalDate()
        if (pageDate != today) {
            courseStatus = if (pageDate < today) "已结束" else "未开始"
            return@LaunchedEffect
        }
        if (startTime == null || endTime == null) {
            if (courseStatus != "未知") courseStatus = "未知"
            return@LaunchedEffect
        }
        // 睡到下一个状态边界再醒：不要退化成每秒轮询 —— 每行常驻一个 1Hz 协程，一页下来白烧电
        while (true) {
            val now = nowLocalTime()
            val next = when {
                now < startTime -> "未开始"
                now > endTime -> "已结束"
                else -> "进行中"
            }
            if (courseStatus != next) courseStatus = next
            if (next == "已结束") return@LaunchedEffect
            val boundary = if (next == "未开始") startTime else endTime
            delay(millisBetween(now, boundary, allowRollover = true).coerceAtLeast(1_000L).milliseconds)
        }
    }

    val statusText = when (courseStatus) {
        "未开始" -> TodayStr.notStarted()
        "已结束" -> TodayStr.ended()
        // 剩余时间在今日助手展示，这里只标状态
        "进行中" -> TodayStr.inClass()
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.name,
                style = MiuixTheme.textStyles.body1.copy(fontSize = 17.sp),
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(course.timeDisplayTextLocalized())
                    if (showClassroom && course.classroom.isNotEmpty()) append(" | ").append(course.classroom)
                    if (showTeacher && course.teacher.isNotEmpty()) append(" | ").append(course.teacher)
                },
                style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            // 备注只在今日页显示：单周备注优先，没有就回退到「所有周」那条
            val note = if (week > 0) course.noteForWeek(week) else course.note
            if (note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note,
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 13.sp),
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeRange,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 15.sp),
                color = MiuixTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = statusText,
                style = MiuixTheme.textStyles.footnote2.copy(fontSize = 14.sp),
                color = when (courseStatus) {
                    "进行中" -> MiuixTheme.colorScheme.primary
                    else -> MiuixTheme.colorScheme.onSurfaceVariantActions
                },
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
fun TodayScreen(
    viewModel: CourseViewModel,
    settingsViewModel: SettingsViewModel,
    /**
     * 只在课程详情弹窗里用：点弹窗卡片进全屏详情时，要把那张源卡片藏掉，
     * 否则放大层和原卡片会叠在一起动（morph 看起来发糊）。
     *
 * 今日页的课程列表**不读**这个值 —— 它不直接进详情，藏起来只会在返回时闪一帧空白。
 */
    hiddenCourseIds: Set<String> = emptySet(),
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: TransitionLayerSnapshot?, courseIdToHide: String, targetWeek: Int) -> Unit = { _, _, _, _, _, _, _, _ -> },
    pagerState: PagerState,
    /** 长距跳日的借位页：让相邻页临时显示目标日期（见 PagerJumpNeighbor） */
    jumpNeighbor: PagerJumpNeighbor? = null,
    navBarStyle: String = "standard",
    onScrollYChanged: (Int) -> Unit = {},
    onListScrollInProgress: (Boolean) -> Unit = {},
    settingsScrollBehavior: SharedScrollBehavior? = null,
    onSelectedDayChanged: (Int) -> Unit = {},
    onSelectedDateChanged: (Boolean) -> Unit = {},
    scrollToTodayTrigger: Int = 0,
    jumpToDateTrigger: Int = 0,
    onJumpToDateProcessed: () -> Unit = {},
    /** 有壁纸时白/黑底不透明度（卡片不透明度） */
    cardSurfaceAlpha: Float = 0.34f,
    /** 柔光玻璃采样源（主内容层算一次，课表 / 今日共用；null = 无玻璃，卡片退回实色） */
    softGlassSurface: SoftGlassSurface? = null,
    /** 主 tab pager 的位置信号：切今日/课表时内容整体平移，必须让玻璃重绘 */
    tabRedraw: () -> Unit = {},
    // true：壁纸由主 pager 后共享层绘制，本页透明叠上，切 tab 时不随页平移
    useSharedWallpaper: Boolean = false,
    liquidGlassBackdrop: GlassBackdrop? = null,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    /** 主界面 resume/节假日版本变化时递增，强制今日页重读 HolidayManager */
    holidayDataTick: Int = 0,
    /** 今日助手卡（天气+智能提示）槽位：Android 宿主注入 TodayAssistantCard；null 不渲染 */
    todayAssistantCard: (@Composable (TodayAssistantCardData) -> Unit)? = null,
    /** 课表页当前呈现方式：时间轴时今日页也换成时间轴（外观弹窗里那个开关，两页共用） */
    scheduleViewMode: ScheduleViewMode = ScheduleViewMode.WEEK,
) {
    val todayIsDark = isAppDarkTheme()
    val overScrollState = remember { OverScrollState() }
    val pageOverscrollX = remember { mutableFloatStateOf(0f) }
    // 当前页的列表状态：draw 期直接读，滚动时同步失效；不要用异步 tick（会晚一帧、看起来在跳）
    val currentListState = remember { mutableStateOf<LazyListState?>(null) }
    val baseGlassSignal: () -> Unit = remember(pagerState, overScrollState, pageOverscrollX, tabRedraw, settingsScrollBehavior) {
        {
            currentListState.value?.firstVisibleItemIndex
            currentListState.value?.firstVisibleItemScrollOffset
            // 标题折叠不走 LazyListState：heightOffset / contentOffset 才是这一段的位移来源
            settingsScrollBehavior?.state?.heightOffset
            settingsScrollBehavior?.state?.contentOffset
            settingsScrollBehavior?.currentHeightPx
            pagerState.currentPageOffsetFraction
            overScrollState.offset
            pageOverscrollX.floatValue
            // nav 几何一变，整页内容横移，卡片玻璃要重新采样（唯一来源见该函数）
            tabletNavSamplingSignal()
            tabRedraw()
        }
    }
    val courses by viewModel.courses.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val dataVersion by viewModel.dataVersion.collectAsState()
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()
    val morningSections by settingsViewModel.morningSections.collectAsState()
    val afternoonSections by settingsViewModel.afternoonSections.collectAsState()
    val eveningSections by settingsViewModel.eveningSections.collectAsState()
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val holidayDataRevision by HolidayManager.dataRevision.collectAsState()

    // 当日/次日课表解析用（原提醒侧解析内部 new 的就是 CourseRepository()，
    // 而 CourseRepository() 返回单例，语义等价）
    val resolveRepository = remember { CourseRepository() }

    val MAX_DATE_OFFSET = 1000
    var currentLocalDateTime by remember {
        mutableStateOf(localDateTimeAt(Clock.System.now().toEpochMilliseconds()))
    }
    val currentLocalDate = currentLocalDateTime.date
    val initialDaysOffset = pagerState.currentPage - MAX_DATE_OFFSET
    val initialDate = currentLocalDate.plusDays(initialDaysOffset.toLong())
    var selectedDate by remember { mutableStateOf(initialDate) }
    var isToday by remember { mutableStateOf(initialDaysOffset == 0) }
    val scope = rememberCoroutineScope()
    var pendingDatePage by remember { mutableIntStateOf(-1) }
    var showDatePicker by remember { mutableStateOf(false) }
    // 点课程先出详情弹窗（与课程表页一致），弹窗里点卡片才进全屏详情
    var detailSheetCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var detailSheetWeek by remember { mutableIntStateOf(0) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var datePickerYear by remember { mutableIntStateOf(todayLocalDate().year) }
    var datePickerMonth by remember { mutableIntStateOf(todayLocalDate().monthNumber - 1) }
    var datePickerDay by remember { mutableIntStateOf(todayLocalDate().dayOfMonth) }
    // 手势接管状态：借位跳日被手势打断时，jumpToPageSmoothly 借它同步平移松手落页基准
    val takeoverGestureState = rememberPagerTakeoverGestureState()

    LaunchedEffect(scrollToTodayTrigger) {
        if (scrollToTodayTrigger > 0 && pagerState.currentPage != MAX_DATE_OFFSET) {
            // 相邻一天直接滑；跨多天借位后也只滑一页，全程连续
            pendingDatePage = MAX_DATE_OFFSET
            try {
                pagerState.jumpToPageSmoothly(MAX_DATE_OFFSET, jumpNeighbor, takeoverGestureState)
            } finally {
                // 落点不是目标（被打断）就立刻撤掉覆盖，别让标题卡在今天
                if (pagerState.currentPage != MAX_DATE_OFFSET) pendingDatePage = -1
            }
        }
    }

    LaunchedEffect(jumpToDateTrigger) {
        if (jumpToDateTrigger > 0) {
            val now = todayLocalDate()
            datePickerYear = now.year
            datePickerMonth = now.monthNumber - 1
            datePickerDay = now.dayOfMonth
            showDatePicker = true
            onJumpToDateProcessed()
        }
    }

    // 落页后撤掉「立刻切日期」的覆盖
    LaunchedEffect(pagerState.currentPage, pendingDatePage) {
        if (pendingDatePage >= 0 && pagerState.currentPage == pendingDatePage) {
            pendingDatePage = -1
        }
    }

    // 跟手换日期：过半页就换（settledPage 要等 fling 停稳，翻起来标题会看着卡住）。
    // 程序化跳日期间由 [pendingDatePage] 提前钉住目标，借位也不会闪到中间那天。
    LaunchedEffect(pagerState.currentPage, pendingDatePage, holidayDataTick) {
        var lastReportedDate: LocalDate? = null
        while (true) {
            val sampledAtMillis = Clock.System.now().toEpochMilliseconds()
            val sampledNow = localDateTimeAt(sampledAtMillis)
            val today = sampledNow.date
            currentLocalDateTime = sampledNow
            // 程序化跳日期间用目标页，点「今」的那一帧日期就切过去
            val anchorPage =
                if (pendingDatePage >= 0) pendingDatePage else pagerState.currentPage
            val daysOffset = anchorPage - MAX_DATE_OFFSET
            val newDate = today.plusDays(daysOffset.toLong())
            if (newDate != lastReportedDate) {
                val dateChanged = newDate != selectedDate
                val nowToday = daysOffset == 0
                val shouldReportSelectedDate =
                    dateChanged || isToday != nowToday || lastReportedDate == null
                if (dateChanged) selectedDate = newDate
                if (shouldReportSelectedDate) {
                    isToday = nowToday
                    onSelectedDateChanged(nowToday)
                }
                onSelectedDayChanged(newDate.dayOfWeek.isoDayNumber)
                lastReportedDate = newDate
            }

            delay(HolidayCountdown.millisUntilNextMinute(sampledAtMillis).milliseconds)
        }
    }

    // 横向 pager 各日期页必须各自持有 LazyListState：共用 externalListState 时，
    // 今日页有助手卡、其他日没有，内容高度不同会把共享 scroll 顶乱，回滑后底部留空错乱
    // externalListState 仅作兼容参数保留，不再驱动列表
    val hapticFeedback = LocalHapticFeedback.current

    // 卡片一律采主 pager 后的共享壁纸层（柔光自己采样），本页不自挂录制层。
    // 无材质档模糊/折射由 displayAppearance 钳 0，但卡片仍采壁纸（着色/不透明度照常生效）。
    // 不透明度：设置值先按深色模式 ×0.8，再分发（默认亮色 34% / 深色 27.2%）。
    // 今日页课程卡 = 基准值；高亮卡（摘要 / 格言）比它再亮 15pp。
    // 课表页相反，用基准值 −15pp，见 AppearanceConfig.scheduleSurfaceAlpha。
    val surfaceDark = isAppDarkTheme()
    val p = (cardSurfaceAlpha * if (surfaceDark) CombinationDefaults.DARK_SURFACE_ALPHA_FACTOR else 1f)
        .coerceIn(0f, 1f)
    val courseCardOpacity = p
    val highlightCardOpacity = (p + CombinationDefaults.HIGHLIGHT_SURFACE_ALPHA_BONUS)
        .coerceIn(0f, 1f)


    val isTablet = navBarStyle == "rail"
    val nativeChromeInset = LocalHostNavigation.current
        ?.takeIf { it.showsNativeTabBar && !isTablet }
        ?.bottomBarInset?.toFloat()?.dp
    val tabletHorizontalPadding = if (isTablet) 24.dp else 16.dp

    // 点课程只开弹窗：卡片位置/图层这些 morph 参数在这里用不上，全屏详情由弹窗里的卡片再触发
    val openDetailSheet: (List<Course>, Float, Float, Float, Float, TransitionLayerSnapshot?, String, Int) -> Unit =
        { coursesAtSlot, _, _, _, _, _, _, targetWeek ->
            detailSheetCourses = coursesAtSlot
            detailSheetWeek = targetWeek
            showDetailSheet = true
        }
    CompositionLocalProvider(
        LocalSoftGlassSurface provides softGlassSurface,
        LocalOverScrollState provides overScrollState,
        LocalGlassRedrawSignal provides baseGlassSignal,
    ) {
    Scaffold(
        topBar = {},
        // 共享壁纸时本页必须透明，否则会盖住主 pager 后面的壁纸层
        containerColor = if (useSharedWallpaper) {
            Color.Transparent
        } else {
            MiuixTheme.colorScheme.surface
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            HorizontalPager(
                state = pagerState,
                // 与课表周 pager 同款预组合：翻日/返回今日时下一日页面在第一帧就要露出，
                // 现组一整页（resolveDaySchedule + 假期/调休查询 + 卡片）会把那一帧顶爆。
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    // 与课表周 pager 共用的横竖主导翻页手势；纵向仍归页内 LazyColumn
                    .pagerAxisTakeoverGesture(pagerState = pagerState, gestureState = takeoverGestureState, overscrollXState = pageOverscrollX),
                userScrollEnabled = false,
            ) { physicalPage ->
                // 借位页：长距跳日期间这一页按目标日期出内容，于是跨 N 天也只是一页连续位移
                val page = jumpNeighbor?.resolve(physicalPage) ?: physicalPage
                // 借位页是「同一页换数据」，整屏文字直接换，不播交叉模糊
                CompositionLocalProvider(
                    LocalTextMotionInstant provides (jumpNeighbor?.isBorrowed(physicalPage) == true)
                ) {
                val pageListState = remember(page) { LazyListState() }
                SideEffect { currentListState.value = pageListState }
                // 只把当前页的滚动状态报给 Activity（顶栏折叠 / 液态玻璃重录）
                LaunchedEffect(pageListState, page) {
                    snapshotFlow {
                        Triple(
                            page,
                            pageListState.isScrollInProgress,
                            pageListState.firstVisibleItemScrollOffset
                        )
                    }.collect { (p, scrolling, offset) ->
                        if (p == pagerState.settledPage || p == pagerState.currentPage) {
                            onListScrollInProgress(scrolling)
                            if (scrolling || p == pagerState.settledPage) {
                                onScrollYChanged(offset)
                            }
                        }
                    }
                }
                val pageDate = currentLocalDate.plusDays((page - MAX_DATE_OFFSET).toLong())
                val isPageToday = pageDate == currentLocalDate
                val countdownNow = currentLocalDateTime
                // Minute ticks and the resume tick both reread in-app holiday changes.
                // key 用日期而不是整个 LocalDateTime：分钟 tick 不该每分钟重读一次 SP
                val holidayVersion = remember(
                    dataVersion,
                    classStartTime,
                    holidayDataTick,
                    holidayDataRevision,
                    countdownNow.date,
                ) {
                    HolidayManager.getVersion()
                }
                // 与提醒/小部件同口径：假日前移除命中课程，末日仅保留命中课程，调休按映射查课
                val pageResolution = remember(
                    pageDate,
                    courses,
                    dataVersion,
                    holidayVersion,
                    classStartTime,
                    totalWeeks,
                    sectionTimes,
                    morningSections,
                    afternoonSections,
                    eveningSections,
                ) {
                    DayScheduleResolver.resolveDaySchedule(
                        date = pageDate,
                        repository = resolveRepository,
                    )
                }
                val displayWeek = pageResolution.displayWeek
                val pageCourses = remember(pageResolution, smartWeekend, dataVersion) {
                    val dayRange =
                        (1..5).toList() + settingsViewModel.getWeekendDaysForWeek(displayWeek)
                            .filter { it in 6..7 }
                    // 节假日仅在未启用末日课程例外时清空；调休补班允许覆盖智能周末隐藏
                    when {
                        pageResolution.isHolidayDate &&
                            !pageResolution.isHolidayEndCourseExclusionActive -> emptyList()
                        pageResolution.isWorkSwap || pageResolution.displayDayOfWeek in dayRange ->
                            pageResolution.courses
                        else -> emptyList()
                    }
                }
                    // 分组结果按课程 / 节次表缓存：别退化成每次重组重建 map + 三次过滤
                val coursePeriods = remember(pageCourses, sectionTimes, morningSections, afternoonSections) {
                    pageCourses.associateWith {
                        it.periodIndex(sectionTimes, morningSections, afternoonSections)
                    }
                }
                val morningCourses = remember(pageCourses, coursePeriods) {
                    pageCourses.filter { coursePeriods[it] == Course.PERIOD_MORNING }
                }
                val afternoonCourses = remember(pageCourses, coursePeriods) {
                    pageCourses.filter { coursePeriods[it] == Course.PERIOD_AFTERNOON }
                }
                val eveningCourses = remember(pageCourses, coursePeriods) {
                    pageCourses.filter { coursePeriods[it] == Course.PERIOD_EVENING }
                }

                val holidayCountdownSnapshot = remember(
                    isPageToday,
                    countdownNow.date,
                    holidayVersion,
                    dataVersion,
                    classStartTime,
                    sectionTimes,
                    morningSections,
                    afternoonSections,
                    eveningSections,
                    courses,
                    smartWeekend,
                    totalWeeks,
                ) {
                    if (!isPageToday) {
                        null
                    } else {
                        val registeredEntriesByYear = HolidayManager.loadAllByYear()
                        val holidayPeriods = HolidayCountdown.holidayPeriodsFromStoredEntries(
                            registeredEntriesByYear
                        )
                        val regularCourseDays = courses.asSequence()
                            .map { it.dayOfWeek }
                            .filter { it in 1..7 }
                            .toSet()
                        val repository = CourseRepository()
                        val courseDateBoundsResult = runCatching {
                            val semesterStartDate = LocalDate.parse(
                                repository.getClassStartTime().replace('/', '-')
                            )
                            CourseScheduleDateBounds.calculate(
                                today = countdownNow.date,
                                semesterStartDate = semesterStartDate,
                                currentWeek = repository.getLiveTeachingWeek(countdownNow.date),
                                totalWeeks = totalWeeks,
                                lastWeekWithCourses = repository.getLastWeekWithCourses(),
                                courses = courses,
                                workSwapEntries = registeredEntriesByYear.values.asSequence()
                                    .flatten()
                                    .filter { it.type == HolidayManager.TYPE_WORKSWAP }
                                    .toList(),
                                teachingWeekReorganizations = repository.getTeachingWeekReorganizations(),
                            )
                        }

                        HolidayCountdown.createSnapshotWithCourseBoundsResult(
                            today = countdownNow.date,
                            holidays = holidayPeriods,
                            courseDateBounds = courseDateBoundsResult,
                            lastClassEndAt = { date ->
                                val entriesForDate = HolidayManager.entriesForDate(
                                    registeredEntriesByYear,
                                    date,
                                )
                                val hasPotentialCourse = date.dayOfWeek.isoDayNumber in regularCourseDays ||
                                    entriesForDate.any { entry ->
                                        entry.type == HolidayManager.TYPE_WORKSWAP &&
                                            entry.matches(date.toString()) &&
                                            // 跟随的是绝对日期，按当前课表换算出星期（换课表自动变）
                                            repository.resolveWorkSwapFollow(entry)?.weekday
                                                ?.let { it in regularCourseDays } == true
                                    }
                                if (!hasPotentialCourse) {
                                    null
                                } else {
                                    val resolution = DayScheduleResolver
                                        .resolveDaySchedule(
                                            date,
                                            repository,
                                            registeredEntriesByYear,
                                        )
                                    val dayRange = (1..5).toSet() +
                                        settingsViewModel.getWeekendDaysForWeek(resolution.displayWeek)
                                            .filter { it in 6..7 }
                                    val effectiveCourses = when {
                                        resolution.isHolidayDate &&
                                            !resolution.isHolidayEndCourseExclusionActive -> emptyList()
                                        resolution.isWorkSwap || resolution.displayDayOfWeek in dayRange ->
                                            resolution.courses
                                        else -> emptyList()
                                    }

                                    effectiveCourses.mapNotNull { course ->
                                        course.getEffectiveEndTime(sectionTimes)?.let { endTime ->
                                            runCatching { parseHourMinute(endTime) }
                                                .getOrNull()
                                        }
                                    }.maxOrNull()
                                }
                            },
                        )
                    }
                }
                val holidayCountdownText = remember(holidayCountdownSnapshot, countdownNow) {
                    holidayCountdownSnapshot?.let {
                        HolidayCountdown.message(it, countdownNow)
                    }
                }

                val tomorrowCourses = remember(
                    isPageToday, pageDate, courses, dataVersion, holidayVersion, classStartTime,
                    totalWeeks, sectionTimes, morningSections, afternoonSections, eveningSections,
                ) {
                    if (!isPageToday) emptyList()
                    else {
                        // 原 forTomorrow = true 就是「今天 + 1 天」
                        val tomorrow = todayLocalDate().plusDays(1)
                        DayScheduleResolver
                            .resolveDaySchedule(date = tomorrow, repository = resolveRepository)
                            .courses
                    }
                }

                val dateText = remember(pageDate) { pageDate.formatChineseDate() }

                if (isTablet) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(LocalPageStartPadding.current)
                            .padding(
                                start = tabletHorizontalPadding,
                                end = tabletHorizontalPadding,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(
                                    top = paddingValues.calculateTopPadding() +
                                        CollapsibleTopAppBarDefaults.CollapsedHeight,
                                    bottom = nativeChromeInset?.plus(8.dp) ?: 60.dp
                                )
                                // 布局期补展开高度，避免组合期读 currentHeightPx
                                .collapsibleTopInset(settingsScrollBehavior)
                        ) {
                            Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
                                Text(
                                    text = dateText,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                if (isPageToday && holidayCountdownText != null) {
                                    Text(
                                        text = holidayCountdownText,
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            QuoteCard(
                                pageDate = pageDate,
                                todayCourses = if (isPageToday) pageCourses else emptyList(),
                                sectionTimes = sectionTimes,
                                surfaceOpacity = highlightCardOpacity
                            )
                            if (isPageToday) {
                                Spacer(modifier = Modifier.height(12.dp))
                                todayAssistantCard?.invoke(
                                    TodayAssistantCardData(
                                        courses = pageCourses,
                                        tomorrowCourses = tomorrowCourses,
                                        sectionTimes = sectionTimes,
                                        morningSections = morningSections,
                                        afternoonSections = afternoonSections,
                                        showClassroom = showClassroom,
                                        showTeacher = showTeacher,
                                        surfaceOpacity = highlightCardOpacity,
                                    )
                                )
                            }
                        }
                        LazyColumn(
                            state = pageListState,
                            modifier = Modifier
                                .weight(1f)
                                .collapsibleTopInset(settingsScrollBehavior)
                                .fillMaxHeight()
                                .scrollEndHaptic(
                                    hapticFeedbackType = HapticFeedbackType.TextHandleMove
                                )
                                .then(
                                    if (settingsScrollBehavior != null) Modifier.nestedScroll(settingsScrollBehavior.nestedScrollConnection) else Modifier
                                )
                                // 放在最后 = 最靠近列表，优先收到 fling，与手机设置页一致
                                .overScrollVertical(),
                            contentPadding = PaddingValues(
                                top = paddingValues.calculateTopPadding() +
                                    CollapsibleTopAppBarDefaults.CollapsedHeight + 14.dp,
                                bottom = nativeChromeInset?.plus(24.dp) ?: 120.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (scheduleViewMode == ScheduleViewMode.TIMELINE && pageCourses.isNotEmpty()) {
                                addTodayTimeline(
                                    pageCourses, sectionTimes, displayWeek, isPageToday, todayIsDark,
                                    courseCardOpacity,
                                    showClassroom, showTeacher,
                                ) { course -> openTodayCourse(course, displayWeek, openDetailSheet) }
                            } else {
                                addCourseSections(morningCourses, afternoonCourses, eveningCourses, pageCourses, isPageToday, pageDate,
                                    displayWeek, sectionTimes, openDetailSheet, courseCardOpacity, showClassroom, showTeacher, isHolidayDate = pageResolution.isHolidayDate)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = pageListState,
                        modifier = Modifier
                            .collapsibleTopInset(settingsScrollBehavior)
                            .fillMaxSize()
                            .scrollEndHaptic(
                                hapticFeedbackType = HapticFeedbackType.TextHandleMove
                            )
                            .then(
                                if (settingsScrollBehavior != null) Modifier.nestedScroll(settingsScrollBehavior.nestedScrollConnection) else Modifier
                            )
                            .overScrollVertical(),
                        contentPadding = PaddingValues(
                            start = tabletHorizontalPadding,
                            top = paddingValues.calculateTopPadding() +
                                CollapsibleTopAppBarDefaults.CollapsedHeight,
                            end = tabletHorizontalPadding,
                            bottom = nativeChromeInset?.plus(80.dp) ?: 175.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .offset(x = (-15).dp)
                                    .padding(start = 28.dp, top = 8.dp)
                            ) {
                                Text(
                                    text = dateText,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                if (isPageToday && holidayCountdownText != null) {
                                    Text(
                                        text = holidayCountdownText,
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                        item {
                            QuoteCard(
                                pageDate = pageDate,
                                todayCourses = if (isPageToday) pageCourses else emptyList(),
                                sectionTimes = sectionTimes,
                                surfaceOpacity = highlightCardOpacity
                            )
                        }
                        if (isPageToday) {
                            item {
                                todayAssistantCard?.invoke(
                                    TodayAssistantCardData(
                                        courses = pageCourses,
                                        tomorrowCourses = tomorrowCourses,
                                        sectionTimes = sectionTimes,
                                        morningSections = morningSections,
                                        afternoonSections = afternoonSections,
                                        showClassroom = showClassroom,
                                        showTeacher = showTeacher,
                                        surfaceOpacity = highlightCardOpacity,
                                    )
                                )
                            }
                        }
                        if (scheduleViewMode == ScheduleViewMode.TIMELINE && pageCourses.isNotEmpty()) {
                            addTodayTimeline(
                                pageCourses, sectionTimes, displayWeek, isPageToday, todayIsDark,
                                courseCardOpacity,
                                showClassroom, showTeacher,
                            ) { course -> openTodayCourse(course, displayWeek, openDetailSheet) }
                        } else {
                            addCourseSections(morningCourses, afternoonCourses, eveningCourses, pageCourses, isPageToday, pageDate,
                                displayWeek, sectionTimes, openDetailSheet, courseCardOpacity, showClassroom, showTeacher, isHolidayDate = pageResolution.isHolidayDate)
                        }
                    }
                }
            }
            }
        }
    }

        // 课程详情弹窗：与课程表页共用同一个组件，备注入口就在卡片底部。
        // 今日页不传 onEdit —— 编辑走全屏详情，这里没有编辑弹窗。
        CourseDetailSheet(
            show = showDetailSheet,
            allCourses = courses,
            selectedCourses = detailSheetCourses,
            viewingWeek = detailSheetWeek,
            isTablet = isTablet,
            // 弹窗里的源卡片照常藏掉：morph 起点就是它，不藏会和放大层叠在一起动
            hiddenCourseIds = hiddenCourseIds,
            onDismiss = { showDetailSheet = false },
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
        )

        OverlayDialog(
            title = TodayStr.jumpToDate(),
            show = showDatePicker,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = { showDatePicker = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    NumberPicker(
                        value = datePickerYear,
                        onValueChange = { datePickerYear = it },
                        range = 2024..2030,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        label = { TodayStr.yearN(it) },
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
                    )
                    NumberPicker(
                        value = datePickerMonth,
                        onValueChange = { datePickerMonth = it },
                        range = 0..11,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        label = { TodayStr.monthN(it + 1) },
                        wrapAround = true,
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
                    )
                    val maxDay = try {
                        LocalDate(datePickerYear, datePickerMonth + 1, 1).lengthOfMonth()
                    } catch (_: Exception) {
                        31
                    }
                    val clampedDay = datePickerDay.coerceIn(1, maxDay)
                    if (clampedDay != datePickerDay) datePickerDay = clampedDay
                    NumberPicker(
                        value = datePickerDay,
                        onValueChange = { datePickerDay = it },
                        range = 1..maxDay,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        label = { CommonStr.dateN(it) },
                        wrapAround = true,
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = CommonStr.cancel(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showDatePicker = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = CommonStr.ok(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            val target =
                                LocalDate(datePickerYear, datePickerMonth + 1, datePickerDay)
                            val now = todayLocalDate()
                            val days = now.daysUntil(target)
                            val targetPage = MAX_DATE_OFFSET + days
                            scope.launch {
                                // 选日期可能跨几十上百天：借位后只滑一页，不逐页组合中间每一天
                                val dest = targetPage.coerceIn(
                                    0, (pagerState.pageCount - 1).coerceAtLeast(0)
                                )
                                // 标题日期立刻切到目标日
                                pendingDatePage = dest
                                try {
                                    pagerState.jumpToPageSmoothly(dest, jumpNeighbor, takeoverGestureState)
                                } finally {
                                    if (pagerState.currentPage != dest) pendingDatePage = -1
                                }
                            }
                            showDatePicker = false
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// 格言 = 课表场景 × 时段加菜；场景优先，时段补充
// 别的日子分两支：往后翻（未来）和往前翻（过去）说的话不一样——
// 未来是「别提前焦虑」，过去是「改不了，别沉浸」。
private enum class QuoteScene {
    DEEP_NIGHT,
    NO_CLASS,
    PAST_DAY,
    FUTURE_DAY,
    SCHEDULE_BROKEN,
    BEFORE_FIRST,
    IN_CLASS_JUST,
    IN_CLASS_MID,
    IN_CLASS_END,
    SHORT_BREAK,
    LONG_BREAK,
    AFTER_ALL,
}

private fun parseQuoteTime(timeStr: String): LocalTime? {
    return try {
        parseHourMinute(timeStr)
    } catch (_: Exception) {
        null
    }
}

private fun ceilQuoteMinutes(from: LocalTime, to: LocalTime): Int {
    // 跨午夜课（23:00-00:30）在课中时 to(00:30) < from(23:50)，按次日结束回卷
    val remain = millisBetween(from, to, allowRollover = true)
    if (remain <= 0L) return 0
    return ((remain + 59_999L) / 60_000L).toInt()
}

private data class QuoteTimeRange(
    val start: LocalTime,
    val end: LocalTime
)

private fun buildQuoteRanges(
    courses: List<Course>,
    sectionTimes: Map<Int, String>
): List<QuoteTimeRange> {
    return courses.mapNotNull { course ->
        val start = parseQuoteTime(course.getEffectiveStartTime(sectionTimes) ?: return@mapNotNull null)
        val end = parseQuoteTime(course.getEffectiveEndTime(sectionTimes) ?: return@mapNotNull null)
        if (start == null || end == null) return@mapNotNull null
        // 起止相同仍是脏数据；跨午夜课（end < start）保留，由 coursePeriodOngoing
        // 按「start→午夜」段判定，晚课时段的引言不再永远匹配不到
        if (start == end) return@mapNotNull null
        QuoteTimeRange(start, end)
    }.sortedBy { it.start }
}

private fun computeQuoteScene(
    pageDate: LocalDate,
    courses: List<Course>,
    sectionTimes: Map<Int, String>,
    now: LocalTime = nowLocalTime(),
    today: LocalDate = todayLocalDate()
): QuoteScene {
    // 别的日子分前后：往后是「还没到」，往前是「已经过去」，说的是两件事
    if (pageDate != today) {
        return if (pageDate > today) QuoteScene.FUTURE_DAY else QuoteScene.PAST_DAY
    }

    val ranges = buildQuoteRanges(courses, sectionTimes)
    // 有课但时间对不上有效区间 → 明确报「课表残缺」，不要装没课或看别的天
    if (ranges.isEmpty()) {
        return if (courses.isEmpty()) QuoteScene.NO_CLASS else QuoteScene.SCHEDULE_BROKEN
    }

    val ongoing = ranges.find { coursePeriodOngoing(it.start, it.end, now) }
    // 深夜优先让给「没在上课」的状态；晚课拖到 23 点仍按上课中（含跨午夜晚课）
    if (ongoing == null && now.hour in 23..4) return QuoteScene.DEEP_NIGHT

    if (ongoing != null) {
        val remain = ceilQuoteMinutes(now, ongoing.end)
        return when {
            remain <= 10 -> QuoteScene.IN_CLASS_END
            remain <= 30 -> QuoteScene.IN_CLASS_MID
            else -> QuoteScene.IN_CLASS_JUST
        }
    }

    val next = ranges.find { now < it.start }
    if (next != null) {
        val remain = ceilQuoteMinutes(now, next.start)
        // 跨午夜课结束点在次日，当天内不算「已上过课」
        val prev = ranges.lastOrNull { coursePeriodCompleted(it.start, it.end, now) }
        // 当天还没上过任何课 → 课前；否则是课间
        if (prev == null) return QuoteScene.BEFORE_FIRST
        return if (remain <= 15) QuoteScene.SHORT_BREAK else QuoteScene.LONG_BREAK
    }

    return QuoteScene.AFTER_ALL
}

// 时段：场景定语气，时段再加菜，整点也会换一条
private enum class QuoteTimeBand {
    DAWN,      // 5-7
    MORNING,   // 8-11
    NOON,      // 12-13
    AFTERNOON, // 14-17
    EVENING,   // 18-20
    NIGHT,     // 21-22
    LATE,      // 23-4
}

private fun quoteTimeBand(hour: Int): QuoteTimeBand = when (hour) {
    in 5..7 -> QuoteTimeBand.DAWN
    in 8..11 -> QuoteTimeBand.MORNING
    in 12..13 -> QuoteTimeBand.NOON
    in 14..17 -> QuoteTimeBand.AFTERNOON
    in 18..20 -> QuoteTimeBand.EVENING
    in 21..22 -> QuoteTimeBand.NIGHT
    else -> QuoteTimeBand.LATE
}

private fun timeExtrasFor(scene: QuoteScene, band: QuoteTimeBand): List<L10n> = when (scene) {
    QuoteScene.IN_CLASS_JUST, QuoteScene.IN_CLASS_MID, QuoteScene.IN_CLASS_END -> when (band) {
        QuoteTimeBand.DAWN -> TodayStr.extrasClassDawn
        QuoteTimeBand.MORNING -> TodayStr.extrasClassMorning
        QuoteTimeBand.NOON -> TodayStr.extrasClassNoon
        QuoteTimeBand.AFTERNOON -> TodayStr.extrasClassAfternoon
        QuoteTimeBand.EVENING -> TodayStr.extrasClassEvening
        QuoteTimeBand.NIGHT -> TodayStr.extrasClassNight
        QuoteTimeBand.LATE -> emptyList()
    }
    QuoteScene.SHORT_BREAK, QuoteScene.LONG_BREAK -> when (band) {
        QuoteTimeBand.DAWN -> TodayStr.extrasBreakDawn
        QuoteTimeBand.MORNING -> TodayStr.extrasBreakMorning
        QuoteTimeBand.NOON -> TodayStr.extrasBreakNoon
        QuoteTimeBand.AFTERNOON -> TodayStr.extrasBreakAfternoon
        QuoteTimeBand.EVENING -> TodayStr.extrasBreakEvening
        QuoteTimeBand.NIGHT -> TodayStr.extrasBreakNight
        QuoteTimeBand.LATE -> emptyList()
    }
    QuoteScene.BEFORE_FIRST, QuoteScene.NO_CLASS, QuoteScene.AFTER_ALL -> when (band) {
        QuoteTimeBand.DAWN -> TodayStr.extrasFreeDawn
        QuoteTimeBand.MORNING -> TodayStr.extrasFreeMorning
        QuoteTimeBand.NOON -> TodayStr.extrasFreeNoon
        QuoteTimeBand.AFTERNOON -> TodayStr.extrasFreeAfternoon
        QuoteTimeBand.EVENING -> TodayStr.extrasFreeEvening
        QuoteTimeBand.NIGHT -> TodayStr.extrasFreeNight
        QuoteTimeBand.LATE -> emptyList()
    }
    else -> emptyList()
}

private fun quotePoolFor(scene: QuoteScene, hour: Int): List<L10n> {
    val base = when (scene) {
        QuoteScene.DEEP_NIGHT -> TodayStr.quotesDeepNight
        QuoteScene.NO_CLASS -> TodayStr.quotesNoClass
        QuoteScene.PAST_DAY -> TodayStr.quotesPastDay
        QuoteScene.FUTURE_DAY -> TodayStr.quotesFutureDay
        QuoteScene.SCHEDULE_BROKEN -> TodayStr.quotesScheduleBroken
        QuoteScene.BEFORE_FIRST -> TodayStr.quotesBeforeFirst
        QuoteScene.IN_CLASS_JUST -> TodayStr.quotesInClassJust
        QuoteScene.IN_CLASS_MID -> TodayStr.quotesInClassMid
        QuoteScene.IN_CLASS_END -> TodayStr.quotesInClassEnd
        QuoteScene.SHORT_BREAK -> TodayStr.quotesShortBreak
        QuoteScene.LONG_BREAK -> TodayStr.quotesLongBreak
        QuoteScene.AFTER_ALL -> TodayStr.quotesAfterAll
    }
    val extras = timeExtrasFor(scene, quoteTimeBand(hour))
    if (extras.isEmpty()) return base
    // 去重，避免底池和加菜撞句
    return (base + extras).distinctBy { it() }
}

// 相当于「进程内 id」：top-level val 每进程只初始化一次，且 Android / Desktop / iOS 通用
private val quoteProcessId: Long = Random.nextLong()

@Composable
private fun QuoteCard(
    pageDate: LocalDate,
    todayCourses: List<Course>,
    sectionTimes: Map<Int, String>,
    surfaceOpacity: Float
) {
    var scene by remember {
        mutableStateOf(
            computeQuoteScene(pageDate, todayCourses, sectionTimes)
        )
    }
    var hour by remember { mutableIntStateOf(nowLocalTime().hour) }
    // 与助手同频：场景/整点切换时立刻换池。
    // 按「睡到下一分钟」而不是每秒轮询 —— 这个循环每页一个，且 computeQuoteScene 会做时间解析+排序
    LaunchedEffect(pageDate, todayCourses, sectionTimes) {
        while (true) {
            val nextScene = computeQuoteScene(pageDate, todayCourses, sectionTimes)
            if (nextScene != scene) scene = nextScene
            val nextHour = nowLocalTime().hour
            if (nextHour != hour) hour = nextHour
            delay(HolidayCountdown.millisUntilNextMinute(Clock.System.now().toEpochMilliseconds()).milliseconds)
        }
    }

    val pool = quotePoolFor(scene, hour)
    val processId = quoteProcessId
    // 同一进程同场景同时段固定一条；整点变化会自然换一条
    val quoteIndex = remember(scene, hour, processId, pool.size) {
        val seed = processId xor (scene.ordinal * 31L) xor (hour * 17L)
        (((seed % pool.size) + pool.size) % pool.size).toInt()
    }
    val quote = pool[quoteIndex]()

    BlurCard(
        surfaceOpacity = surfaceOpacity,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = quote,
                style = MiuixTheme.textStyles.body1.copy(fontSize = 18.sp),
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}


// 今日页分组标题：始终套椭圆（胶囊）底；有壁纸时走与卡片相同的 blur/透明度
// textAlign：节标题默认起始对齐；空态等居中场景由调用方传 Center
@Composable
private fun CourseSectionTitle(
    text: String,
    surfaceOpacity: Float,
    modifier: Modifier = Modifier,
    style: TextStyle =
        MiuixTheme.textStyles.subtitle.copy(fontWeight = FontWeight.Medium),
    textAlign: TextAlign? = null,
) {
    val isDark = isAppDarkTheme()
    val softGlass = LocalSoftGlassSurface.current
    val glassRedraw = LocalGlassRedrawSignal.current
    // 有玻璃时用纯黑/纯白，压在毛玻璃上更干净
    val textColor = if (softGlass != null) {
        if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)
    } else {
        Color(0xFF8F9CAE)
    }

    if (softGlass != null) {
        val shape = ContinuousCapsule()
        val density = LocalDensity.current
        val glassOrigin = remember { arrayOf(Offset.Zero) }
        val softGlassModifier = remember(softGlass, density, glassRedraw) {
            softGlass.modifier(
                density = density,
                origin = { glassOrigin[0] },
                // 胶囊由 softGlass 的半径钳制自动得到：style.cornerRadius 会被钳到高度一半
                radiusPx = null,
                redraw = glassRedraw,
            )
        }
        val flatTitleColor = remember(isDark, surfaceOpacity) {
            if (isDark) Color.Black.copy(alpha = surfaceOpacity)
            else Color.White.copy(alpha = surfaceOpacity)
        }
        Box(
            modifier = modifier
                .padding(vertical = 6.dp)
                .clip(shape)
                .onGloballyPositioned { glassOrigin[0] = it.positionInWindow() }
                .then(softGlassModifier)
                .background(flatTitleColor, shape)
        ) {
            Text(
                text = text,
                style = style,
                color = textColor,
                textAlign = textAlign,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        return
    }

    val pillBg = if (isDark) {
        Color.White.copy(alpha = 0.1f)
    } else {
        Color.White.copy(alpha = 0.8f)
    }
    Text(
        text = text,
        style = style,
        color = textColor,
        textAlign = textAlign,
        modifier = modifier
            .padding(vertical = 6.dp)
            .clip(ContinuousCapsule())
            .background(pillBg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private typealias CourseDetailClick = (
    courses: List<Course>,
    cardLeft: Float,
    cardTop: Float,
    cardWidth: Float,
    cardHeight: Float,
    snapshot: TransitionLayerSnapshot?,
    courseIdToHide: String,
    targetWeek: Int,
) -> Unit

/** Retain the shared glass and crop a row only when opening its details. */
@Composable
private fun CourseDetailSectionCard(
    surfaceOpacity: Float,
    onCourseClick: CourseDetailClick,
    content: @Composable (CourseDetailClick) -> Unit,
) {
    if (LocalSoftGlassSurface.current == null) {
        // Opaque rows already record their matching surface background locally.
        BlurCard(surfaceOpacity = surfaceOpacity, modifier = Modifier.fillMaxWidth()) {
            content(onCourseClick)
        }
        return
    }

    val sectionLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val latestOnCourseClick by rememberUpdatedState(onCourseClick)
    val sectionOrigin = remember { arrayOf(Offset.Zero) }
    val lastRecordedToken = remember { intArrayOf(0) }
    val capturePending = remember { booleanArrayOf(false) }
    var recordToken by remember { mutableIntStateOf(0) }
    val recording = Modifier.drawWithContent {
        val token = recordToken
        if (lastRecordedToken[0] != token) {
            sectionLayer.record { this@drawWithContent.drawContent() }
            lastRecordedToken[0] = token
            drawLayer(sectionLayer)
        } else {
            drawContent()
        }
    }
    BlurCard(
        surfaceOpacity = surfaceOpacity,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { sectionOrigin[0] = it.localToRoot(Offset.Zero) },
        transitionRecording = recording,
    ) {
        content { courses, left, top, width, height, _, courseId, targetWeek ->
            if (!capturePending[0]) {
                capturePending[0] = true
                val origin = sectionOrigin[0]
                val crop = Rect(left - origin.x, top - origin.y, left + width - origin.x, top + height - origin.y)
                scope.launch {
                    try {
                        val token = ++recordToken
                        // Publish only after the shared background and visible row are recorded.
                        while (lastRecordedToken[0] < token) {
                            withFrameNanos { }
                        }
                        latestOnCourseClick(
                            courses, left, top, width, height,
                            TransitionLayerSnapshot(sectionLayer, crop), courseId, targetWeek,
                        )
                    } finally {
                        capturePending[0] = false
                    }
                }
            }
        }
    }
}

/** 时间轴版课程区：整页一条时间轴（今日页单列），与列表版二选一 */
private fun LazyListScope.addTodayTimeline(
    pageCourses: List<Course>,
    sectionTimes: Map<Int, String>,
    pageWeek: Int,
    isPageToday: Boolean,
    isDark: Boolean,
    surfaceOpacity: Float,
    showClassroom: Boolean,
    showTeacher: Boolean,
    onCourseClick: (Course) -> Unit,
) {
    item(key = "today-timeline") {
        TodayTimelineSection(
            courses = pageCourses,
            sectionTimes = sectionTimes,
            pageWeek = pageWeek,
            isToday = isPageToday,
            style = TodayTimelineStyle(
                isDark = isDark,
                // 与今日列表行同一档表面不透明度（不是课表页的课程色着色度）
                surfaceOpacity = surfaceOpacity,
                showClassroom = showClassroom,
                showTeacher = showTeacher,
            ),
            onCourseClick = onCourseClick,
        )
    }
}

private fun LazyListScope.addCourseSections(
    morningCourses: List<Course>,
    afternoonCourses: List<Course>,
    eveningCourses: List<Course>,
    pageCourses: List<Course>,
    isPageToday: Boolean,
    pageDate: LocalDate,
    pageWeek: Int,
    sectionTimes: Map<Int, String>,
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: TransitionLayerSnapshot?, courseIdToHide: String, targetWeek: Int) -> Unit,
    surfaceOpacity: Float,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    isHolidayDate: Boolean = false,
) {
    if (morningCourses.isNotEmpty()) {
        item(key = "today-course-section-morning") {
            Column {
                CourseSectionTitle(
                    text = TodayStr.morningClasses(),
                    surfaceOpacity = surfaceOpacity
                )
                CourseDetailSectionCard(surfaceOpacity, onCourseClick) { onCardClick ->
                    Column {
                        morningCourses.forEach { course ->
                            key(course.id) {
                                CourseItemWithClick(course, sectionTimes, pageDate, pageWeek, onCardClick, showClassroom, showTeacher)
                            }
                        }
                    }
                }
            }
        }
    }
    if (afternoonCourses.isNotEmpty()) {
        item(key = "today-course-section-afternoon") {
            Column {
                CourseSectionTitle(
                    text = TodayStr.afternoonClasses(),
                    surfaceOpacity = surfaceOpacity
                )
                CourseDetailSectionCard(surfaceOpacity, onCourseClick) { onCardClick ->
                    Column {
                        afternoonCourses.forEach { course ->
                            key(course.id) {
                                CourseItemWithClick(course, sectionTimes, pageDate, pageWeek, onCardClick, showClassroom, showTeacher)
                            }
                        }
                    }
                }
            }
        }
    }
    if (eveningCourses.isNotEmpty()) {
        item(key = "today-course-section-evening") {
            Column {
                CourseSectionTitle(
                    text = TodayStr.eveningClasses(),
                    surfaceOpacity = surfaceOpacity
                )
                CourseDetailSectionCard(surfaceOpacity, onCourseClick) { onCardClick ->
                    Column {
                        eveningCourses.forEach { course ->
                            key(course.id) {
                                CourseItemWithClick(course, sectionTimes, pageDate, pageWeek, onCardClick, showClassroom, showTeacher)
                            }
                        }
                    }
                }
            }
        }
    }
    if (pageCourses.isEmpty()) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                contentAlignment = Alignment.Center
            ) {
                CourseSectionTitle(
                    text = when {
                        isHolidayDate && isPageToday -> TodayStr.holidayTodayRest()
                        isHolidayDate -> TodayStr.holidayOtherDay()
                        isPageToday -> TodayStr.noCourseTodayRest()
                        else -> TodayStr.noCourseOtherDay()
                    },
                    surfaceOpacity = surfaceOpacity,
                    style = MiuixTheme.textStyles.body1.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * 今日页点课程 → 弹详情：只带被点的这一节。
 *
 * 今日页每一行 / 每张卡就是当天的一节课，点谁只看谁；别的周里节次压过来的课不进弹窗。
 */
private fun openTodayCourse(
    course: Course,
    pageWeek: Int,
    openDetailSheet: (List<Course>, Float, Float, Float, Float, TransitionLayerSnapshot?, String, Int) -> Unit,
) {
    // 位置/图层参数供「点卡片直接 morph 进详情」用；今日页先弹详情弹窗，起点由弹窗内卡片提供
    openDetailSheet(listOf(course), 0f, 0f, 0f, 0f, null, course.id, pageWeek)
}

@Composable
private fun CourseItemWithClick(
    course: Course,
    sectionTimes: Map<Int, String>,
    pageDate: LocalDate,
    pageWeek: Int,
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: TransitionLayerSnapshot?, courseIdToHide: String, targetWeek: Int) -> Unit,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true
) {
    val hasGlass = LocalSoftGlassSurface.current != null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // ⚠ 无壁纸时必须用 surfaceContainer，不能用 background：外层 BlurCard 此时退化成
            //   miuix `Card()`，卡片色正是 surfaceContainer（Card.kt defaultColors）。内置方案下
            //   两者恰好相等（#FFFFFF / #242424），莫奈下才分家：background 取 tone 98/6、
            //   surfaceContainer 取 tone 94/12，差 4 个明度阶，写错会偏色。
            .then(if (hasGlass) Modifier else Modifier.background(MiuixTheme.colorScheme.surfaceContainer))
            .clickable {
                openTodayCourse(course, pageWeek, onCourseClick)
            }
    ) {
        CourseItemContent(course, sectionTimes, pageDate, showClassroom, showTeacher, pageWeek)
    }
}
