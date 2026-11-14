package com.haooz.chedule.ui.activities.today

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.appearance.CardRefractionLevel
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.repo.DayScheduleResolver
import com.haooz.chedule.data.holiday.HolidayCountdown
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.util.formatChineseDate
import com.haooz.chedule.data.util.lengthOfMonth
import com.haooz.chedule.data.util.localDateTimeAt
import com.haooz.chedule.data.util.plusDays
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.TodayStr
import com.haooz.chedule.ui.activities.main.PagerJumpNeighbor
import com.haooz.chedule.ui.activities.main.jumpToPageSmoothly
import com.haooz.chedule.ui.components.text.LocalTextMotionInstant
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberCardEdgeLight
import com.haooz.chedule.ui.utils.rememberDefaultEdgeLight
import com.haooz.chedule.ui.utils.screenHeightDp
import com.haooz.chedule.ui.utils.screenWidthDp
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import com.haooz.chedule.ui.activities.main.MorePopupMenus
import com.haooz.chedule.ui.activities.main.PageLockedTopBarTheme
import com.haooz.chedule.ui.activities.main.TodayTopBar
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.ui.activities.classschedule.ScheduleViewMode
import com.haooz.chedule.ui.components.HorizontalPageSwitch
import com.haooz.chedule.ui.components.PageSwitchState
import com.haooz.chedule.ui.components.SoftGlassSurface
import com.haooz.softglass.backdrop.GlassBackdrop
import top.yukonga.miuix.kmp.utils.OverScrollState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberKyantLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.edgelight.edgeLight
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
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

/** 由 [BlurCard] 统一应用折射档位，与课程表页面一致。 */
val LocalCardRefraction = staticCompositionLocalOf { CardRefractionLevel.DEFAULT }

/**
 * 保留给宿主的今日助手数据类型。日程页已改为统一时间轴，不再渲染这张卡；
 * 类型保留可避免 Android / iOS 宿主注入接口发生无关破坏。
 */
class TodayAssistantCardData(
    val courses: List<Course>,
    val tomorrowCourses: List<Course>,
    val sectionTimes: Map<Int, String>,
    val morningSections: Int,
    val afternoonSections: Int,
    val showClassroom: Boolean,
    val showTeacher: Boolean,
    val wallpaperBackdrop: Backdrop?,
    val blurRadius: Float,
    val surfaceOpacity: Float,
)

@Composable
fun BlurCard(
    cornerRadius: Dp = 20.dp,
    wallpaperBackdrop: Backdrop? = null,
    blurRadius: Float = 0f,
    surfaceOpacity: Float,
    showEdgeLight: Boolean = false,
    modifier: Modifier = Modifier,
    transitionRecording: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val hasBackdrop = wallpaperBackdrop != null
    val isDark = isAppDarkTheme()
    val refraction = LocalCardRefraction.current

    if (hasBackdrop) {
        val shape = remember(cornerRadius) { ContinuousRoundedRectangle(cornerRadius) }
        val defaultEdgeLight = rememberDefaultEdgeLight(baseColor = if (isDark) Color.Black else Color.White)
        val blurPx = with(LocalDensity.current) { remember(blurRadius) { blurRadius.dp.toPx() } }
        val glassEffects: BackdropEffectScope.() -> Unit = remember(refraction, blurPx) {
            {
                blur(blurPx)
                if (refraction != CardRefractionLevel.OFF) {
                    lens(refraction.lensRadiusDp.dp.toPx(), refraction.lensStrengthDp.dp.toPx())
                }
            }
        }
        val flatSurfaceColor = remember(isDark, surfaceOpacity) {
            if (isDark) Color.Black.copy(alpha = surfaceOpacity)
            else Color.White.copy(alpha = surfaceOpacity)
        }
        val glassSurface: DrawScope.() -> Unit = remember(isDark, surfaceOpacity) {
            { drawRect(flatSurfaceColor) }
        }
        val needsSampling = blurRadius > 0f || refraction != CardRefractionLevel.OFF
        Box(
            modifier = modifier
                .clip(shape)
                .then(transitionRecording)
                .then(
                    if (needsSampling) {
                        Modifier.drawBackdrop(
                            backdrop = wallpaperBackdrop,
                            shape = { shape },
                            effects = glassEffects,
                            highlight = null,
                            onDrawSurface = glassSurface,
                        )
                    } else {
                        Modifier.background(flatSurfaceColor, shape)
                    }
                )
                .then(
                    if (showEdgeLight) {
                        Modifier.edgeLight(shape = shape, edgeLight = defaultEdgeLight)
                    } else {
                        Modifier.edgeLight(
                            shape = shape,
                            edgeLight = rememberCardEdgeLight(baseColor = if (isDark) Color.Black else Color.White),
                        )
                    }
                ),
        ) {
            content()
        }
    } else {
        Card(
            cornerRadius = cornerRadius,
            modifier = modifier,
            insideMargin = PaddingValues(0.dp),
        ) {
            content()
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun TodayScreen(
    viewModel: CourseViewModel,
    settingsViewModel: SettingsViewModel,
    hiddenCourseIds: Set<String> = emptySet(),
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: TransitionLayerSnapshot?, courseIdToHide: String, targetWeek: Int) -> Unit = { _, _, _, _, _, _, _, _ -> },
    pageState: PageSwitchState,
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
    wallpaperBitmap: ImageBitmap? = null,
    wallpaperOffset: Offset = Offset.Zero,
    wallpaperScale: Float = 1f,
    wallpaperBrightness: Float = 0f,
    cardBlurRadius: Float = 0f,
    cardRefraction: CardRefractionLevel = CardRefractionLevel.DEFAULT,
    cardSurfaceAlpha: Float = 0.15f,
    wallpaperBlur: Boolean = false,
    useSharedWallpaper: Boolean = false,
    sharedWallpaperBackdrop: Backdrop? = null,
    liquidGlassBackdrop: GlassBackdrop? = null,
    softGlassSurface: SoftGlassSurface? = null,
    tabRedraw: () -> Unit = {},
    scheduleViewMode: ScheduleViewMode = ScheduleViewMode.WEEK,
    overScrollState: OverScrollState = remember { OverScrollState() },
    chrome: TodayPageChrome? = null,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    holidayDataTick: Int = 0,
    todayAssistantCard: (@Composable (TodayAssistantCardData) -> Unit)? = null,
) {
    val courses by viewModel.courses.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val dataVersion by viewModel.dataVersion.collectAsState()
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val holidayDataRevision by HolidayManager.dataRevision.collectAsState()

    val density = LocalDensity.current
    val resolveRepository = remember { CourseRepository() }
    val screenWidthPx = with(density) { screenWidthDp().dp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp().dp.toPx() }
    val minWallpaperScale = remember(wallpaperBitmap, screenWidthPx, screenHeightPx) {
        if (wallpaperBitmap != null && wallpaperBitmap.width > 0 && wallpaperBitmap.height > 0) {
            val fitScale = minOf(screenWidthPx / wallpaperBitmap.width, screenHeightPx / wallpaperBitmap.height)
            val coverScale = maxOf(screenWidthPx / wallpaperBitmap.width, screenHeightPx / wallpaperBitmap.height)
            if (fitScale > 0f) coverScale / fitScale else 1f
        } else {
            1f
        }
    }

    val maxDateOffset = 1000
    var currentLocalDateTime by remember {
        mutableStateOf(localDateTimeAt(Clock.System.now().toEpochMilliseconds()))
    }
    val currentLocalDate = currentLocalDateTime.date
    val initialDaysOffset = pageState.currentPage - maxDateOffset
    val initialDate = currentLocalDate.plusDays(initialDaysOffset.toLong())
    var selectedDate by remember { mutableStateOf(initialDate) }
    var isToday by remember { mutableStateOf(initialDaysOffset == 0) }
    val scope = rememberCoroutineScope()
    var pendingDatePage by remember { mutableIntStateOf(-1) }
    var showDatePicker by remember { mutableStateOf(false) }
    var datePickerYear by remember { mutableIntStateOf(todayLocalDate().year) }
    var datePickerMonth by remember { mutableIntStateOf(todayLocalDate().monthNumber - 1) }
    var datePickerDay by remember { mutableIntStateOf(todayLocalDate().dayOfMonth) }
    // 某些日期冲突列多到塞不下时会横向溢出，这时横向拖动优先给时间轴平移，不再翻页
    val timelinePanPages = remember { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(scrollToTodayTrigger) {
        if (scrollToTodayTrigger > 0 && pageState.currentPage != maxDateOffset) {
            pendingDatePage = maxDateOffset
            try {
                pageState.jumpToPageSmoothly(maxDateOffset, jumpNeighbor)
            } finally {
                if (pageState.currentPage != maxDateOffset) pendingDatePage = -1
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

    LaunchedEffect(pageState.currentPage, pendingDatePage) {
        if (pendingDatePage >= 0 && pageState.currentPage == pendingDatePage) {
            pendingDatePage = -1
        }
    }

    LaunchedEffect(pageState.currentPage, pendingDatePage, holidayDataTick) {
        var lastReportedDate: LocalDate? = null
        while (true) {
            val sampledAtMillis = Clock.System.now().toEpochMilliseconds()
            val sampledNow = localDateTimeAt(sampledAtMillis)
            val today = sampledNow.date
            currentLocalDateTime = sampledNow
            val anchorPage = if (pendingDatePage >= 0) pendingDatePage else pageState.currentPage
            val daysOffset = anchorPage - maxDateOffset
            val newDate = today.plusDays(daysOffset.toLong())
            if (newDate != lastReportedDate) {
                val dateChanged = newDate != selectedDate
                val nowToday = daysOffset == 0
                val shouldReportSelectedDate = dateChanged || isToday != nowToday || lastReportedDate == null
                if (dateChanged) selectedDate = newDate
                if (shouldReportSelectedDate) {
                    isToday = nowToday
                    onSelectedDateChanged(nowToday)
                }
                onSelectedDayChanged(newDate.dayOfWeek.isoDayNumber)
                lastReportedDate = newDate
            }
            delay(HolidayCountdown.millisUntilNextMinute(sampledAtMillis))
        }
    }

    val hapticFeedback = LocalHapticFeedback.current
    val backgroundColor = MiuixTheme.colorScheme.surface
    val localCardBackdrop = rememberKyantLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val cardBackdrop: Backdrop =
        if (useSharedWallpaper && sharedWallpaperBackdrop != null) sharedWallpaperBackdrop
        else localCardBackdrop
    val hasWallpaper = wallpaperBitmap != null
    val p = cardSurfaceAlpha.coerceIn(0f, 1f)
    val supportingCardOpacity = if (p <= 0.15f) {
        (p / 0.15f) * 0.45f
    } else {
        0.45f + ((p - 0.15f) / 0.85f) * 0.55f
    }
    val isTablet = navBarStyle == "rail"
    val nativeChromeInset = LocalHostNavigation.current
        ?.takeIf { it.showsNativeTabBar && !isTablet }
        ?.bottomBarInset?.toFloat()?.dp
    val horizontalPadding = if (isTablet) 24.dp else 16.dp
    // ── 上游 1.7.0 的页面内顶栏：顶栏由页面自己组合，宿主只注入 chrome 数据 ──
    val contentBackdrop = chrome?.contentBackdrop ?: rememberLayerBackdrop()
    val chromeBackdrop: GlassBackdrop = chrome?.wallpaperBackdrop ?: contentBackdrop
    val moreSlotTopPx = remember { mutableFloatStateOf(0f) }
    val moreMaterialAlpha = remember { mutableFloatStateOf(0f) }

    CompositionLocalProvider(LocalCardRefraction provides cardRefraction) {
        Scaffold(
            topBar = {},
            containerColor = if (useSharedWallpaper && wallpaperBitmap != null) {
                Color.Transparent
            } else {
                MiuixTheme.colorScheme.surface
            },
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (wallpaperBitmap != null && !useSharedWallpaper) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(Modifier.kyantLayerBackdrop(localCardBackdrop)),
                    ) {
                        val brightnessFilter = if (wallpaperBrightness != 0f) {
                            val b = (1f + wallpaperBrightness / 50f).coerceIn(0f, 2f)
                            ColorFilter.colorMatrix(
                                ColorMatrix(
                                    floatArrayOf(
                                        b, 0f, 0f, 0f, 0f,
                                        0f, b, 0f, 0f, 0f,
                                        0f, 0f, b, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f,
                                    )
                                )
                            )
                        } else {
                            null
                        }
                        val wallpaperBlurEffect = remember(wallpaperBlur) {
                            val blurRadiusPx = 24f * density.density
                            if (wallpaperBlur && blurRadiusPx > 0f && isRenderEffectSupported()) {
                                BlurEffect(null, blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                            } else {
                                null
                            }
                        }
                        Image(
                            bitmap = wallpaperBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val effectiveScale = maxOf(wallpaperScale, minWallpaperScale)
                                    scaleX = effectiveScale
                                    scaleY = effectiveScale
                                    translationX = wallpaperOffset.x
                                    translationY = wallpaperOffset.y
                                    renderEffect = wallpaperBlurEffect
                                },
                            contentScale = ContentScale.Fit,
                            colorFilter = brightnessFilter,
                        )
                    }
                }

                HorizontalPageSwitch(
                    state = pageState,
                    pageCount = maxDateOffset * 2 + 1,
                    modifier = Modifier.fillMaxSize(),
                ) { physicalPage ->
                    val page = jumpNeighbor?.resolve(physicalPage) ?: physicalPage
                    CompositionLocalProvider(
                        LocalTextMotionInstant provides (jumpNeighbor?.isBorrowed(physicalPage) == true),
                    ) {
                        val pageDate = currentLocalDate.plusDays((page - maxDateOffset).toLong())
                        val isPageToday = pageDate == currentLocalDate
                        val holidayVersion = remember(
                            dataVersion,
                            classStartTime,
                            holidayDataTick,
                            holidayDataRevision,
                            currentLocalDateTime.date,
                        ) {
                            HolidayManager.getVersion()
                        }
                        val pageResolution = remember(
                            pageDate,
                            courses,
                            dataVersion,
                            holidayVersion,
                            classStartTime,
                            totalWeeks,
                            sectionTimes,
                        ) {
                            DayScheduleResolver.resolveDaySchedule(
                                date = pageDate,
                                repository = resolveRepository,
                            )
                        }
                        val displayWeek = pageResolution.displayWeek
                        val pageCourses = remember(pageResolution, smartWeekend, dataVersion) {
                            val dayRange = (1..5).toList() +
                                settingsViewModel.getWeekendDaysForWeek(displayWeek).filter { it in 6..7 }
                            when {
                                pageResolution.isHolidayDate &&
                                    !pageResolution.isHolidayEndCourseExclusionActive -> emptyList()
                                pageResolution.isWorkSwap || pageResolution.displayDayOfWeek in dayRange ->
                                    pageResolution.courses
                                else -> emptyList()
                            }
                        }
                        val calendarController = rememberSystemCalendarController(
                            date = pageDate,
                            refreshKey = holidayDataTick,
                        )
                        val calendarSnapshot = calendarController.snapshot
                        // 整天 / 跨天事件放顶部胶囊，否则会被渲染成 0-24 点的整列卡片铺满屏幕
                        val allDayEvents = remember(calendarSnapshot) {
                            calendarSnapshot.events.filter {
                                it.allDay || (it.startMinute <= 0 && it.endMinute >= 24 * 60)
                            }
                        }
                        val timedEvents = remember(calendarSnapshot, allDayEvents) {
                            val allDayIds = allDayEvents.mapTo(HashSet()) { it.id }
                            calendarSnapshot.events.filterNot { it.id in allDayIds }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .collapsibleTopInset(settingsScrollBehavior)
                                .padding(
                                    start = horizontalPadding,
                                    end = horizontalPadding,
                                    top = paddingValues.calculateTopPadding() +
                                        CollapsibleTopAppBarDefaults.CollapsedHeight + 8.dp,
                                    bottom = nativeChromeInset?.plus(76.dp) ?: 88.dp,
                                ),
                        ) {
                            Text(
                                text = pageDate.formatChineseDate(),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = TodayStr.scheduleSummary(
                                        pageCourses.size + calendarSnapshot.events.size,
                                        pageCourses.size,
                                    ),
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = TodayStr.pinchToZoom(),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.72f),
                                )
                            }

                            if (calendarSnapshot.permission == SystemCalendarPermission.DENIED) {
                                Spacer(modifier = Modifier.height(10.dp))
                                CalendarPermissionCard(
                                    wallpaperBackdrop = if (hasWallpaper) cardBackdrop else null,
                                    blurRadius = cardBlurRadius,
                                    surfaceOpacity = supportingCardOpacity,
                                    onRequest = calendarController.requestPermission,
                                )
                            } else if (calendarSnapshot.loadFailed) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = TodayStr.calendarLoadFailed(),
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        text = CommonStr.retry(),
                                        onClick = calendarController.requestPermission,
                                    )
                                }
                            }

                            if (allDayEvents.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = TodayStr.allDay(),
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(end = 10.dp, top = 7.dp),
                                    )
                                    AllDayEventsStrip(
                                        events = allDayEvents,
                                        wallpaperBackdrop = if (hasWallpaper) cardBackdrop else null,
                                        blurRadius = cardBlurRadius,
                                        surfaceOpacity = supportingCardOpacity,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            ScheduleTimeline(
                                date = pageDate,
                                isToday = isPageToday,
                                courses = pageCourses,
                                allCourses = courses,
                                hiddenCourseIds = hiddenCourseIds,
                                sectionTimes = sectionTimes,
                                systemEvents = timedEvents,
                                page = page,
                                hasAllDayEvents = allDayEvents.isNotEmpty(),
                                onHorizontalOverflowChanged = { pageIndex, overflow ->
                                    timelinePanPages[pageIndex] = overflow
                                },
                                contentReady = !calendarSnapshot.isLoading,
                                pageWeek = displayWeek,
                                isTablet = isTablet,
                                showClassroom = showClassroom,
                                showTeacher = showTeacher,
                                wallpaperBackdrop = if (hasWallpaper) cardBackdrop else null,
                                cardBlurRadius = cardBlurRadius,
                                cardRefraction = cardRefraction,
                                cardSurfaceAlpha = cardSurfaceAlpha,
                                scrollBehavior = settingsScrollBehavior,
                                onScrollYChanged = onScrollYChanged,
                                onListScrollInProgress = onListScrollInProgress,
                                onCourseClick = onCourseClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }
                    }
                }
            }
        }

        OverlayDialog(
            title = TodayStr.jumpToDate(),
            show = showDatePicker,
            liquidGlassBackdrop = null,
            onDismissRequest = { showDatePicker = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberPicker(
                        value = datePickerYear,
                        onValueChange = { datePickerYear = it },
                        range = 2024..2030,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        label = { TodayStr.yearN(it) },
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f),
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
                        modifier = Modifier.weight(1f),
                    )
                    val maxDay = runCatching {
                        LocalDate(datePickerYear, datePickerMonth + 1, 1).lengthOfMonth()
                    }.getOrDefault(31)
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
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    TextButton(
                        text = CommonStr.cancel(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showDatePicker = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = CommonStr.ok(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            val target = LocalDate(datePickerYear, datePickerMonth + 1, datePickerDay)
                            val days = todayLocalDate().daysUntil(target)
                            val targetPage = maxDateOffset + days
                            scope.launch {
                                val dest = targetPage.coerceIn(0, (pageState.pageCount - 1).coerceAtLeast(0))
                                pendingDatePage = dest
                                try {
                                    pageState.jumpToPageSmoothly(dest, jumpNeighbor)
                                } finally {
                                    if (pageState.currentPage != dest) pendingDatePage = -1
                                }
                            }
                            showDatePicker = false
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 顶栏与页面同层：翻日 / 切 tab 时跟着页面一起位移
        chrome?.let { c ->
            PageLockedTopBarTheme(c.forcedDarkTheme) {
                TodayTopBar(
                    liquidGlassBackdrop = chromeBackdrop,
                    navBarStyle = navBarStyle,
                    currentDayOfWeek = selectedDate.dayOfWeek.isoDayNumber,
                    isToday = isToday,
                    onJumpToDate = c.onJumpToDate,
                    onEnterCustomize = c.onEnterCustomize,
                    scrollBehavior = settingsScrollBehavior,
                    visible = true,
                    blurResampleKey = c.blurResampleKey,
                    blurSampleTrack = c.blurSampleTrack,
                    onMoreSlotTop = { slot ->
                        // v1.7.1 起回调给的是 LayoutCoordinates：换算成根坐标系的 y
                        if (slot.isAttached) moreSlotTopPx.floatValue = slot.positionInRoot().y
                    },
                    onMoreMaterial = { moreMaterialAlpha.floatValue = it },
                    onMoreExpand = { c.moreMenu?.onExpand?.invoke() },
                    titleEndPadding = c.titleEndPadding,
                )
                c.moreMenu?.let { menu ->
                    MorePopupMenus(
                        showMorePopup = false,
                        onMorePopupDismiss = {},
                        showTodayMorePopup = menu.show,
                        onTodayMorePopupDismiss = menu.onDismiss,
                        onTodayMorePopupExpand = menu.onExpand,
                        morePopupFraction = remember { Animatable(0f) },
                        todayMorePopupFraction = menu.fraction,
                        todayMenuOffset = { Offset.Zero },
                        moreSlotTopPx = moreSlotTopPx,
                        todayMaterialAlpha = moreMaterialAlpha.floatValue,
                        liquidGlassBackdrop = chromeBackdrop,
                        enabled = menu.enabled,
                        todayMoreNative = menu.native,
                        scheduleMoreNative = true,
                        onJumpWeek = {},
                        onCourseManage = c.onCourseManage,
                        onEnterCustomize = c.onEnterCustomize,
                        onJumpToDate = c.onJumpToDate,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarPermissionCard(
    wallpaperBackdrop: Backdrop?,
    blurRadius: Float,
    surfaceOpacity: Float,
    onRequest: () -> Unit,
) {
    BlurCard(
        cornerRadius = 16.dp,
        wallpaperBackdrop = wallpaperBackdrop,
        blurRadius = blurRadius,
        surfaceOpacity = surfaceOpacity,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = TodayStr.calendarPermissionTitle(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = TodayStr.calendarPermissionSummary(),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 3.dp, end = 8.dp),
                )
            }
            TextButton(
                text = TodayStr.calendarPermissionAction(),
                onClick = onRequest,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
