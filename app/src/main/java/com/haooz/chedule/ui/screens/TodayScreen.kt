package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.CardRefractionLevel
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.basic.collapsibleTopInset
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberCardEdgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.milliseconds
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberKyantLayerBackdrop

// 有壁纸 backdrop 才走毛玻璃半透明路径，与模糊半径无关（blur=0 仍采样壁纸）
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日")

// 由 BlurCard 统一应用折射档位
val LocalCardRefraction = staticCompositionLocalOf { CardRefractionLevel.DEFAULT }

@Composable
fun BlurCard(
    cornerRadius: Dp = 20.dp,
    wallpaperBackdrop: Backdrop? = null,
    blurRadius: Float = 0f,
    surfaceOpacity: Float,
    showEdgeLight: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hasBackdrop = wallpaperBackdrop != null
    val isDark = isAppDarkTheme()
    val refraction = LocalCardRefraction.current

    if (hasBackdrop) {
        val shape = ContinuousRoundedRectangle(cornerRadius)
        val defaultEdgeLight = rememberDefaultEdgeLight()
        Box(
            modifier = modifier
                .clip(shape)
                .drawBackdrop(
                    backdrop = wallpaperBackdrop,
                    shape = { ContinuousRoundedRectangle(cornerRadius) },
                    effects = {
                        blur(blurRadius.dp.toPx())
                        if (refraction != CardRefractionLevel.OFF) {
                            lens(refraction.lensRadiusDp.dp.toPx(), refraction.lensStrengthDp.dp.toPx())
                        }
                    },
                    highlight = null,
                    onDrawSurface = {
                        drawRect(if (isDark) Color.Black.copy(alpha = surfaceOpacity) else Color.White.copy(alpha = surfaceOpacity))
                    }
                )
                .then(
                    if (showEdgeLight) {
                        Modifier.edgeLight(
                            shape = ContinuousRoundedRectangle(cornerRadius),
                            edgeLight = defaultEdgeLight)
                    } else
                        Modifier.edgeLight(
                            shape = ContinuousRoundedRectangle(cornerRadius),
                            edgeLight = rememberCardEdgeLight())
                )
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
private fun CourseItemContent(course: Course, sectionTimes: Map<Int, String>, pageDate: LocalDate = LocalDate.now(), showClassroom: Boolean = true, showTeacher: Boolean = true) {
    fun getSectionTimeRange(startSection: Int, endSection: Int): String {
        val startTime = sectionTimes[startSection]?.split("-")?.firstOrNull() ?: ""
        val endTime = sectionTimes[endSection]?.split("-")?.lastOrNull() ?: ""
        return if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
            "$startTime - $endTime"
        } else {
            "第$startSection-${endSection}节"
        }
    }

    fun parseTime(timeStr: String): LocalTime? {
        return try {
            LocalTime.parse(timeStr.trim(), TIME_FORMATTER)
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
        val today = LocalDate.now()
        when {
            !pageDate.isEqual(today) ->
                if (pageDate.isBefore(today)) "已结束" else "未开始"
            startTime == null || endTime == null -> "未知"
            else -> {
                val now = LocalTime.now()
                when {
                    now.isBefore(startTime) -> "未开始"
                    now.isAfter(endTime) -> "已结束"
                    else -> "进行中"
                }
            }
        }
    }
    val initialRemaining = remember(startTime, endTime) {
        if (initialStatus == "进行中" && startTime != null && endTime != null) {
            val duration = java.time.Duration.between(LocalTime.now(), endTime)
            val totalSeconds = duration.seconds
            Pair((totalSeconds / 60).toInt(), (totalSeconds % 60).toInt())
        } else {
            Pair(0, 0)
        }
    }
    var courseStatus by remember { mutableStateOf(initialStatus) }
    var remainingMinutes by remember { mutableIntStateOf(initialRemaining.first) }
    var remainingSeconds by remember { mutableIntStateOf(initialRemaining.second) }

    LaunchedEffect(startTime, endTime, pageDate) {
        val today = LocalDate.now()
        if (!pageDate.isEqual(today)) {
            courseStatus = if (pageDate.isBefore(today)) "已结束" else "未开始"
            return@LaunchedEffect
        }
        while (true) {
            val now = LocalTime.now()
            when {
                startTime == null || endTime == null -> {
                    if (courseStatus != "未知") courseStatus = "未知"
                    // 本组合内时间来源固定，未知状态不会自行变化
                    return@LaunchedEffect
                }
                now.isBefore(startTime) -> {
                    if (courseStatus != "未开始") courseStatus = "未开始"
                }
                now.isAfter(endTime) -> {
                    if (courseStatus != "已结束") courseStatus = "已结束"
                    // 当天内状态不会再变
                    return@LaunchedEffect
                }
                else -> {
                    val duration = java.time.Duration.between(now, endTime)
                    val totalSeconds = duration.seconds
                    val newMinutes = (totalSeconds / 60).toInt()
                    val newSeconds = (totalSeconds % 60).toInt()
                    if (courseStatus != "进行中") courseStatus = "进行中"
                    // 仅值变化时写入，避免每秒触发重组
                    if (newMinutes != remainingMinutes) remainingMinutes = newMinutes
                    // 秒数只在最后一分钟文案使用
                    if (newMinutes <= 0 && newSeconds != remainingSeconds) remainingSeconds = newSeconds
                }
            }
            delay(1000L.milliseconds)
        }
    }

    val statusText = when (courseStatus) {
        "未开始" -> "未开始"
        "已结束" -> "已结束"
        "进行中" -> when {
            remainingMinutes <= 0 && remainingSeconds <= 0 -> "还剩0秒"
            remainingMinutes <= 0 -> "还剩${remainingSeconds}秒"
            remainingMinutes >= 60 -> {
                val hours = remainingMinutes / 60
                val mins = remainingMinutes % 60 + 1
                if (mins >= 60) "还剩${hours + 1}小时"
                else "还剩${hours}小时${mins}分钟"
            }
            else -> "还剩${remainingMinutes + 1}分钟"
        }
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
                    append(course.getTimeDisplayText())
                    if (showClassroom && course.classroom.isNotEmpty()) append(" | ").append(course.classroom)
                    if (showTeacher && course.teacher.isNotEmpty()) append(" | ").append(course.teacher)
                },
                style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeRange,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 15.sp),
                color = MiuixTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = statusText,
                style = MiuixTheme.textStyles.footnote2.copy(fontSize = 14.sp),
                color = when (courseStatus) {
                    "进行中" -> MiuixTheme.colorScheme.primary
                    else -> MiuixTheme.colorScheme.onSurfaceVariantActions
                }
            )
        }
    }
}

private fun calculateWeekFromDate(startDate: String, date: LocalDate): Int {
    return try {
        val start = LocalDate.parse(startDate.replace("/", "-"))
        val startMonday = start.minusDays((start.dayOfWeek.value - 1).toLong())
        val daysBetween = ChronoUnit.DAYS.between(startMonday, date)
        daysBetween.floorDiv(7).toInt() + 1
    } catch (_: Exception) { 1 }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun TodayScreen(
    viewModel: CourseViewModel,
    settingsViewModel: SettingsViewModel,
    hiddenCourseIds: Set<String> = emptySet(),
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: android.graphics.Bitmap?, courseIdToHide: String, targetWeek: Int) -> Unit = { _, _, _, _, _, _, _, _ -> },
    pagerState: androidx.compose.foundation.pager.PagerState,
    navBarStyle: String = "standard",
    onScrollYChanged: (Int) -> Unit = {},
    onListScrollInProgress: (Boolean) -> Unit = {},
    settingsScrollBehavior: SharedScrollBehavior? = null,
    onSelectedDayChanged: (Int) -> Unit = {},
    onSelectedDateChanged: (Boolean) -> Unit = {},
    scrollToTodayTrigger: Int = 0,
    jumpToDateTrigger: Int = 0,
    onJumpToDateProcessed: () -> Unit = {},
    wallpaperBitmap: android.graphics.Bitmap? = null,
    wallpaperOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    wallpaperScale: Float = 1f,
    wallpaperBrightness: Float = 0f,
    cardBlurRadius: Float = 0f,
    cardRefraction: CardRefractionLevel = CardRefractionLevel.DEFAULT,
    /** 有壁纸时白/黑底不透明度（卡片不透明度） */
    cardSurfaceAlpha: Float = 0.15f,
    wallpaperBlur: Boolean = false,
    // true：壁纸由主 pager 后共享层绘制，本页透明叠上，切 tab 时不随页平移
    useSharedWallpaper: Boolean = false,
    // 共享壁纸层的 backdrop，供卡片玻璃采样（useSharedWallpaper 时必传）
    sharedWallpaperBackdrop: Backdrop? = null,
    liquidGlassBackdrop: Backdrop? = null,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
) {
    val courses by viewModel.courses.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()
    val morningSections by settingsViewModel.morningSections.collectAsState()
    val afternoonSections by settingsViewModel.afternoonSections.collectAsState()
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val todayShowWallpaper by settingsViewModel.todayShowWallpaper.collectAsState()

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val appContext = LocalContext.current.applicationContext
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val minWallpaperScale = remember(wallpaperBitmap, screenWidthPx, screenHeightPx) {
        if (wallpaperBitmap != null && wallpaperBitmap.width > 0 && wallpaperBitmap.height > 0) {
            val fitScale = minOf(screenWidthPx / wallpaperBitmap.width, screenHeightPx / wallpaperBitmap.height)
            val coverScale = maxOf(screenWidthPx / wallpaperBitmap.width, screenHeightPx / wallpaperBitmap.height)
            if (fitScale > 0f) coverScale / fitScale else 1f
        } else 1f
    }

    val MAX_DATE_OFFSET = 1000
    val initialDaysOffset = pagerState.currentPage - MAX_DATE_OFFSET
    val initialDate = LocalDate.now().plusDays(initialDaysOffset.toLong())
    var selectedDate by remember { mutableStateOf(initialDate) }
    var isToday by remember { mutableStateOf(initialDaysOffset == 0) }
    val scope = rememberCoroutineScope()

    var showDatePicker by remember { mutableStateOf(false) }
    var datePickerYear by remember { mutableIntStateOf(LocalDate.now().year) }
    var datePickerMonth by remember { mutableIntStateOf(LocalDate.now().monthValue - 1) }
    var datePickerDay by remember { mutableIntStateOf(LocalDate.now().dayOfMonth) }

    LaunchedEffect(scrollToTodayTrigger) {
        if (scrollToTodayTrigger > 0 && pagerState.currentPage != MAX_DATE_OFFSET) {
            pagerState.animateScrollToPage(MAX_DATE_OFFSET)
        }
    }

    LaunchedEffect(jumpToDateTrigger) {
        if (jumpToDateTrigger > 0) {
            val now = LocalDate.now()
            datePickerYear = now.year
            datePickerMonth = now.monthValue - 1
            datePickerDay = now.dayOfMonth
            showDatePicker = true
            onJumpToDateProcessed()
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val daysOffset = pagerState.currentPage - MAX_DATE_OFFSET
        val newDate = LocalDate.now().plusDays(daysOffset.toLong())
        if (newDate != selectedDate) {
            selectedDate = newDate
            val nowToday = daysOffset == 0
            isToday = nowToday
            onSelectedDateChanged(nowToday)
        }
        val newDayOfWeek = newDate.dayOfWeek.value.let { if (it == 7) 7 else it }
        onSelectedDayChanged(newDayOfWeek)
    }

    // 横向 pager 各日期页必须各自持有 LazyListState：共用 externalListState 时，
    // 今日页有助手卡、其他日没有，内容高度不同会把共享 scroll 顶乱，回滑后底部留空错乱
    // externalListState 仅作兼容参数保留，不再驱动列表
    val hapticFeedback = LocalHapticFeedback.current

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    // 供今日页卡片 drawBackdrop 采样壁纸：共享层时直接用主层 backdrop，否则录本页壁纸
    val localCardBackdrop = rememberKyantLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val cardBackdrop: Backdrop =
        if (useSharedWallpaper && sharedWallpaperBackdrop != null) sharedWallpaperBackdrop
        else localCardBackdrop
    val hasWallpaper = todayShowWallpaper && wallpaperBitmap != null
    // 默认 15% 锚定当前观感（课程卡 0.45 / 格言卡 0.6），其余按百分比线性映射到 1
    val p = cardSurfaceAlpha.coerceIn(0f, 1f)
    fun anchorOpacity(anchor: Float): Float = if (p <= 0.15f) {
        (p / 0.15f) * anchor
    } else {
        anchor + ((p - 0.15f) / 0.85f) * (1f - anchor)
    }
    val courseCardOpacity = anchorOpacity(0.45f)
    val highlightCardOpacity = anchorOpacity(0.6f)


    val isTablet = navBarStyle == "rail"
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 16.dp

    val todayRefraction = cardRefraction
    CompositionLocalProvider(LocalCardRefraction provides todayRefraction) {
    Scaffold(
        topBar = {},
        // 共享壁纸时本页必须透明，否则会盖住主 pager 后面的壁纸层
        containerColor = if (useSharedWallpaper && todayShowWallpaper && wallpaperBitmap != null) {
            Color.Transparent
        } else {
            MiuixTheme.colorScheme.surface
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            if (todayShowWallpaper && wallpaperBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            // 共享层已负责显示并录制；本页不再录透明占位
                            if (useSharedWallpaper) Modifier
                            else Modifier.kyantLayerBackdrop(localCardBackdrop)
                        )
                ) {
                    val brightnessFilter = if (wallpaperBrightness != 0f) {
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
                    val todayWallpaperBlurEffect = remember(wallpaperBlur) {
                        if (wallpaperBlur) {
                            val blurRadiusPx = 24f * density.density
                            android.graphics.RenderEffect.createBlurEffect(
                                blurRadiusPx,
                                blurRadiusPx,
                                android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        } else null
                    }
                    androidx.compose.foundation.Image(
                        bitmap = wallpaperBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val effectiveScale = maxOf(wallpaperScale, minWallpaperScale)
                                scaleX = effectiveScale
                                scaleY = effectiveScale
                                // 共享层负责显示；这里只录 backdrop，保持不可见
                                alpha = if (useSharedWallpaper) 0f else 1f
                                translationX = wallpaperOffset.x
                                translationY = wallpaperOffset.y
                                renderEffect = todayWallpaperBlurEffect
                            },
                        contentScale = ContentScale.Fit,
                        colorFilter = brightnessFilter
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageListState = remember(page) { LazyListState() }
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
                val pageDate = LocalDate.now().plusDays((page - MAX_DATE_OFFSET).toLong())
                val pageDayOfWeek = pageDate.dayOfWeek.value.let { if (it == 7) 7 else it }
                val pageWeek = remember(pageDate, classStartTime) {
                    calculateWeekFromDate(classStartTime, pageDate)
                }
                // 调休补班日：按配置映射到「第 X 周星期 Y」的课，与课表页 / 小组件同一套逻辑
                val pageSwap = remember(pageDate) {
                    HolidayManager.workSwap(appContext, pageDate)
                }
                val displayDay = pageSwap?.followWeekday?.takeIf { it in 1..7 } ?: pageDayOfWeek
                val displayWeek = pageSwap?.followWeek?.takeIf { it > 0 } ?: pageWeek
                val isWorkSwapDay = pageSwap?.followWeekday?.let { it in 1..7 } == true
                val pageCourses = remember(courses, displayWeek, displayDay, smartWeekend, isWorkSwapDay) {
                    val dayRange =
                        (1..5).toList() + settingsViewModel.getWeekendDaysForWeek(displayWeek)
                            .filter { it in 6..7 }
                    // 调休补班即使落在智能周末隐藏的周六日也要显示
                    if (isWorkSwapDay || displayDay in dayRange) {
                        courses.filter { it.dayOfWeek == displayDay && it.isActiveInWeek(displayWeek) }
                            .sortedBy { it.startSection }
                    } else {
                        emptyList()
                    }
                }
                val coursePeriods = pageCourses.associateWith {
                    it.periodIndex(sectionTimes, morningSections, afternoonSections)
                }
                val morningCourses = pageCourses.filter { coursePeriods[it] == Course.PERIOD_MORNING }
                val afternoonCourses = pageCourses.filter { coursePeriods[it] == Course.PERIOD_AFTERNOON }
                val eveningCourses = pageCourses.filter { coursePeriods[it] == Course.PERIOD_EVENING }

                val isPageToday = pageDate == LocalDate.now()

                val tomorrowCourses = remember(courses, pageWeek, pageDayOfWeek, isPageToday, pageDate) {
                    if (isPageToday) {
                        val tomorrowDate = pageDate.plusDays(1)
                        val tomorrowSwap = HolidayManager.workSwap(appContext, tomorrowDate)
                        val tomorrowDay = tomorrowSwap?.followWeekday?.takeIf { it in 1..7 }
                            ?: if (pageDayOfWeek == 7) 1 else pageDayOfWeek + 1
                        val tomorrowWeek = tomorrowSwap?.followWeek?.takeIf { it > 0 }
                            ?: if (pageDayOfWeek == 7) pageWeek + 1 else pageWeek
                        courses.filter { it.dayOfWeek == tomorrowDay && it.isActiveInWeek(tomorrowWeek) }
                            .sortedBy { it.startSection }
                    } else {
                        emptyList()
                    }
                }

                val dateText = pageDate.format(DATE_FORMATTER)

                if (isTablet) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
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
                                    bottom = 60.dp
                                )
                                // 布局期补展开高度，避免组合期读 currentHeightPx
                                .collapsibleTopInset(settingsScrollBehavior)
                        ) {
                            Text(
                                text = dateText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            QuoteCard(
                                isPageToday = isPageToday,
                                todayCourses = if (isPageToday) pageCourses else emptyList(),
                                tomorrowCourses = tomorrowCourses,
                                sectionTimes = sectionTimes,
                                wallpaperBackdrop = if (hasWallpaper) cardBackdrop else null,
                                blurRadius = cardBlurRadius,
                                surfaceOpacity = highlightCardOpacity
                            )
                            if (isPageToday) {
                                Spacer(modifier = Modifier.height(12.dp))
                                TodayAssistantCard(
                                    courses = pageCourses,
                                    tomorrowCourses = tomorrowCourses,
                                    sectionTimes = sectionTimes,
                                    morningSections = morningSections,
                                    afternoonSections = afternoonSections,
                                    showClassroom = showClassroom,
                                    showTeacher = showTeacher,
                                    wallpaperBackdrop = if (hasWallpaper) cardBackdrop else null,
                                    blurRadius = cardBlurRadius,
                                    surfaceOpacity = highlightCardOpacity
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .collapsibleTopInset(settingsScrollBehavior)
                                .fillMaxHeight()
                                .overScrollVertical()
                                .scrollEndHaptic(
                                    hapticFeedbackType = HapticFeedbackType.TextHandleMove
                                )
                                .then(
                                    if (settingsScrollBehavior != null) Modifier.nestedScroll(settingsScrollBehavior.nestedScrollConnection) else Modifier
                                ),
                            contentPadding = PaddingValues(
                                top = paddingValues.calculateTopPadding() +
                                    CollapsibleTopAppBarDefaults.CollapsedHeight + 14.dp,
                                bottom = 120.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            addCourseSections(morningCourses, afternoonCourses, eveningCourses, pageCourses, isPageToday, pageDate, pageWeek, courses, hiddenCourseIds, sectionTimes, onCourseClick, if (hasWallpaper) cardBackdrop else null, cardBlurRadius, courseCardOpacity, showClassroom, showTeacher)
                        }
                    }
                } else {
                    LazyColumn(
                        state = pageListState,
                        modifier = Modifier
                            .collapsibleTopInset(settingsScrollBehavior)
                            .fillMaxSize()
                            .overScrollVertical()
                            .scrollEndHaptic(
                                hapticFeedbackType = HapticFeedbackType.TextHandleMove
                            )
                            .then(
                                if (settingsScrollBehavior != null) Modifier.nestedScroll(settingsScrollBehavior.nestedScrollConnection) else Modifier
                            ),
                        contentPadding = PaddingValues(
                            start = tabletHorizontalPadding,
                            top = paddingValues.calculateTopPadding() +
                                CollapsibleTopAppBarDefaults.CollapsedHeight,
                            end = tabletHorizontalPadding,
                            bottom = 175.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = dateText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .offset(x = (-15).dp)
                                    .padding(start = 28.dp, top = 8.dp)
                            )
                        }
                        item {
                            QuoteCard(
                                isPageToday = isPageToday,
                                todayCourses = if (isPageToday) pageCourses else emptyList(),
                                tomorrowCourses = tomorrowCourses,
                                sectionTimes = sectionTimes,
                                wallpaperBackdrop = if (hasWallpaper) cardBackdrop else null,
                                blurRadius = cardBlurRadius,
                                surfaceOpacity = highlightCardOpacity
                            )
                        }
                        if (isPageToday) {
                            item {
                                TodayAssistantCard(
                                    courses = pageCourses,
                                    tomorrowCourses = tomorrowCourses,
                                    sectionTimes = sectionTimes,
                                    morningSections = morningSections,
                                    afternoonSections = afternoonSections,
                                    showClassroom = showClassroom,
                                    showTeacher = showTeacher,
                                    wallpaperBackdrop = if (hasWallpaper) cardBackdrop else null,
                                    blurRadius = cardBlurRadius,
                                    surfaceOpacity = highlightCardOpacity
                                )
                            }
                        }
                        addCourseSections(morningCourses, afternoonCourses, eveningCourses, pageCourses, isPageToday, pageDate, pageWeek, courses, hiddenCourseIds, sectionTimes, onCourseClick, if (hasWallpaper) cardBackdrop else null, cardBlurRadius, courseCardOpacity, showClassroom, showTeacher)
                    }
                }
            }
        }
    }

        OverlayDialog(
            title = "跳转日期",
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
                        label = { "${it}年" },
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
                    )
                    NumberPicker(
                        value = datePickerMonth,
                        onValueChange = { datePickerMonth = it },
                        range = 0..11,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        label = { "${it + 1}月" },
                        wrapAround = true,
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
                    )
                    val maxDay = try {
                        LocalDate.of(datePickerYear, datePickerMonth + 1, 1).lengthOfMonth()
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
                        label = { "${it}日" },
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
                        text = "取消",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showDatePicker = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            val target =
                                LocalDate.of(datePickerYear, datePickerMonth + 1, datePickerDay)
                            val now = LocalDate.now()
                            val days = ChronoUnit.DAYS.between(now, target)
                            val targetPage = MAX_DATE_OFFSET + days.toInt()
                            scope.launch {
                                pagerState.animateScrollToPage(targetPage)
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
private enum class QuoteScene {
    DEEP_NIGHT,
    NO_CLASS,
    OTHER_DAY,
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
        LocalTime.parse(timeStr.trim(), TIME_FORMATTER)
    } catch (_: Exception) {
        null
    }
}

private fun ceilQuoteMinutes(from: LocalTime, to: LocalTime): Int {
    val remain = java.time.Duration.between(from, to).toMillis()
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
        // 结束不晚于开始：脏数据，丢掉，避免永远匹配不到
        if (!end.isAfter(start)) return@mapNotNull null
        QuoteTimeRange(start, end)
    }.sortedBy { it.start }
}

private fun computeQuoteScene(
    isPageToday: Boolean,
    courses: List<Course>,
    sectionTimes: Map<Int, String>,
    now: LocalTime = LocalTime.now()
): QuoteScene {
    if (!isPageToday) return QuoteScene.OTHER_DAY

    val ranges = buildQuoteRanges(courses, sectionTimes)
    // 有课但时间对不上有效区间 → 明确报「课表残缺」，不要装没课或看别的天
    if (ranges.isEmpty()) {
        return if (courses.isEmpty()) QuoteScene.NO_CLASS else QuoteScene.SCHEDULE_BROKEN
    }

    val ongoing = ranges.find { !now.isBefore(it.start) && now.isBefore(it.end) }
    // 深夜优先让给「没在上课」的状态；晚课拖到 23 点仍按上课中
    if (ongoing == null && now.hour in 23..4) return QuoteScene.DEEP_NIGHT

    if (ongoing != null) {
        val remain = ceilQuoteMinutes(now, ongoing.end)
        return when {
            remain <= 10 -> QuoteScene.IN_CLASS_END
            remain <= 30 -> QuoteScene.IN_CLASS_MID
            else -> QuoteScene.IN_CLASS_JUST
        }
    }

    val next = ranges.find { now.isBefore(it.start) }
    if (next != null) {
        val remain = ceilQuoteMinutes(now, next.start)
        val prev = ranges.lastOrNull { !now.isBefore(it.end) }
        // 当天还没上过任何课 → 课前；否则是课间
        if (prev == null) return QuoteScene.BEFORE_FIRST
        return if (remain <= 15) QuoteScene.SHORT_BREAK else QuoteScene.LONG_BREAK
    }

    return QuoteScene.AFTER_ALL
}

// 嘴硬一点，但别变成劝退
private val quotesDeepNight = listOf(
    "凌晨了，你的黑眼圈在申请吉尼斯",
    "别修仙了，早八不会为你的仙气让路",
    "这个点的努力，感动天感动地，感动不了绩点",
    "手机还剩 20% 电，你的意志力还剩多少",
    "关机，睡觉，别逼我跪下来求你",
    "你熬的不是夜，是明天迟到的底气",
    "深夜的你最清醒——清醒地摆烂",
    "头发在掉，作业在飘，就你在熬",
    "别拿灵感当借口，你刷的是短视频不是文献",
    "再不睡，明天第一节课的主角就是你的呼噜",
    "凌晨的自我感动，白天的现实打脸，稳了",
    "睡吧，让明天的你去面对今天的债",
    "台灯比你努力，它至少在工作",
    "这个点的「再学五分钟」，一般等于再玩两小时",
    "别数羊了，数数你今天翘过的知识点",
    "月亮都下班了，你还在硬撑，图啥"
)
private val quotesNoClass = listOf(
    "今天没课，你的生产力大概率也没上班",
    "自由日？通常是废掉日的委婉说法",
    "别躺出褥子，明天课表会找你算账",
    "没课的你：计划满满，进度零零",
    "今天没有老师催你，你也别把自己催崩了",
    "空出来的时间，最后都会变成刷手机的时长",
    "难得没课，做点人事——比如真的睡一觉",
    "别羡慕别人有课充实，你空虚得很彻底",
    "没课不是奖励，是你自己把今天过成了过场",
    "图书馆开门了，你大概率在门外路过",
    "闹钟不用响，你也照样起不来，很稳定",
    "别人满课像渡劫，你没课像断线",
    "自由是有了，自律呢？哦，没装这个模块",
    "今天省下的通勤，会在晚上变成刷夜"
)
private val quotesOtherDay = listOf(
    "翻别人的课表干嘛，今天的你才是重灾区",
    "别对照了，你自己的进度也好看不到哪去",
    "看课表不能让课消失，但能让你更焦虑",
    "这一天的你：还没到，已经开始慌了",
    "提前预习焦虑，是你唯一的超前学习",
    "别滑了，课不会因为你看两眼就少一节",
    "未来的苦，现在的你先替他尝了一口",
    "看得挺认真，就是一点用没有",
    "现在研究那天的教室，那天你照样会迷路",
    "课表翻得越勤，心里越慌，很科学"
)
private val quotesScheduleBroken = listOf(
    "课有，时间没有——你这是薛定谔的课表",
    "节次或时间没填全，格言都不知道怎么嘲你",
    "去课表补一下，不然倒计时和笑话都对不上",
    "时间残缺得跟期末范围似的，先补数据",
    "课表在，起点终点不在，当代悬疑片",
    "别指望我编故事，先把上课时间写上"
)
private val quotesBeforeFirst = listOf(
    "离上课还有点时间，够你再赖一次",
    "别吃了，再吃就得用跑的了",
    "今天的第一节课，是你和被窝的告别演出",
    "出门前再看一眼床，它舍不得你，但课表舍得",
    "还有时间？不，是还有借口",
    "你的早晨流程：醒→赖→慌→跑，稳定发挥",
    "别收拾了，教室的灯不会给你打光",
    "早饭随便塞两口，你的胃会记住这笔账",
    "这个点还从容的，等会儿会在走廊飙车",
    "闹钟响了三遍，你赢了两次，课赢了最后一次",
    "现在有多悠闲，待会儿就有多冲刺",
    "第一节还没开始，迟到的剧本已经写好了",
    "外套记得带，教室空调不跟你讲武德",
    "水杯记得带，忘带的上午你会懂"
)
private val quotesInClassJust = listOf(
    "刚点完名，你的魂就开始请假了",
    "这节课才开始，你已经在心里下课三次了",
    "别急着关书，还有很久够你表演专注",
    "老师还没讲到重点，你已经演完走神全套了",
    "坐姿挺标准，就是眼神在窗外办签证",
    "别装了，你打开书只是为了挡脸",
    "连着上？恭喜，你可以完整睡一觉了",
    "刚上课就盼下课，你的盼头比成绩稳定",
    "这节要是选修，你连装都懒得装",
    "上课五分钟，摸鱼两小时——预算已分配好",
    "第一节就耗尽电量，后面靠什么撑？意志力？笑死",
    "老师讲的是大纲，你听的是氛围",
    "别急着拍 PPT，拍了你也不会再看",
    "现在坐得多端正，中段就瘫得多彻底"
)
private val quotesInClassMid = listOf(
    "过半了，你的耐心没有",
    "老师的进度条在走，你的倒计时在蹦迪",
    "别掐大腿了，掐完还是困",
    "中段的你：眼睛睁着，脑子已签退",
    "笔记记到一半开始画火柴人，艺术家实锤",
    "撑住，后面的内容你反正也听不懂（不是）",
    "你的专注力像流量包，早超额了",
    "别问同学借笔记，你借的是别人的努力",
    "这会儿还能听进去的，建议保送",
    "半节课过去，你的人设从「认真」改成「还活着」",
    "知识点从左耳进右耳出，路过都不打卡",
    "别刷学习软件了，你在教室，不是在图书馆装",
    "现在犯的困，都是昨晚欠的债",
    "老师提问的概率在上升，你的低头角度也是"
)
private val quotesInClassEnd = listOf(
    "还有几分钟，你的脚已经完成热身",
    "别装了，书包拉链你都摸三遍了",
    "最后几分钟的演技，够评奥斯卡",
    "下课铃还没响，你的魂已经出校门打卡了",
    "老师拖堂一分钟，你少活一年",
    "忍住，现在冲出去会被记仇的",
    "你的坐姿从听课切换成起跑预备了",
    "最后几分钟，你听进去的只有自己的心跳",
    "别提前收东西，老师就爱抓这个",
    "再撑一下，食堂不会跑，但队伍会",
    "这个点的「认真」，是装给老师看的余光",
    "手机在包里震，你的心比它还抖",
    "最后几分钟的知识点，往往是考点——你正好在收拾书包",
    "拖堂是老师的特权，崩溃是你的义务"
)
private val quotesShortBreak = listOf(
    "就这么几分钟，你还掏出手机？格局小了",
    "接水排队都比你准备下节课久",
    "别去走廊演风云人物了，下节课更困",
    "课间是用来补觉的，不是用来假装社交的",
    "上厕所+接水+刷手机：完美卡点迟到套餐",
    "这几分钟够你想清楚要不要翘下一节（别）",
    "歇够没？没歇够也得滚回去了",
    "课间刷到的瓜，下节课讲不完更难受",
    "别瘫了，下节课的老师已经在门口蹲你了",
    "三分钟热度都不够你热完这个课间",
    "同桌都睡着了，就你还清醒地浪费课间",
    "打水的队伍，是你今天见过最长的卷",
    "走廊逛一圈，风没醒，瓜吃了一堆",
    "现在多玩一分钟，下节课多困十分钟，汇率很差"
)
private val quotesLongBreak = listOf(
    "这么久的空档，你大概率会用来后悔",
    "计划：预习。实际：刷到手机发烫",
    "别瘫了，待会还要演一小时清醒",
    "午休充电五分钟，续命一下午——并没充上",
    "这段时间够你写作业，也够你把作业忘光",
    "饭可以吃，觉可以睡，就是别打开那局游戏",
    "你管这叫休息？手机电量都不信",
    "长课间的尽头，是你踩点冲进教室的背影",
    "别把空档过成加班，最后累的还是自己",
    "现在有多闲，下节课开场就有多狼狈",
    "睡有睡的代价，玩有玩的报应，你自己选",
    "空档是用来回血的，不是用来掉段的",
    "说好只躺十分钟，醒来天都变了",
    "别开新剧了，开了一集就是一下午"
)
private val quotesAfterAll = listOf(
    "课是上完了，作业才刚刚开始看你笑话",
    "别急着躺，明天的早八已经在门口蹲着了",
    "今天的你：完整出席，完整走神",
    "恭喜通关今日副本，奖励是——明天还有",
    "书包可以放下了，良心放不下",
    "放学不是自由，是换个地方继续焦虑",
    "今天的知识点，一个都没办理入住",
    "别装充实了，你今天干了啥自己清楚",
    "老师下班了，你的愧疚开始上班",
    "自由了？作业群消息响了没",
    "走出校门的风，都比教室里的清醒",
    "今天的重点：你一个都没抓到",
    "人是放了，魂还在最后一排没领走",
    "复盘一下今天：出席率 100%，吸收率未知"
)

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

// 时段加菜：只在对应钟点并入当前场景池
private val extrasClassDawn = listOf(
    "早八的教室，灵魂还在宿舍办入住",
    "这个点的笔记，写的是睡姿速写",
    "别问老师为什么看你，你的眼睛还没开机",
    "天刚亮就上课，你的床想报警",
    "第一排都坐不醒的早八，建议改叫「晨间刑」",
    "你人到了，生物钟还在昨天",
    "老师声音越稳，你眼皮越重",
    "早八的正确打开方式：睁一只眼闭一只眼"
)
private val extrasClassMorning = listOf(
    "上午的清醒是限量款，你已经用完了",
    "阳光正好，适合发呆，不适合听课",
    "别看窗外了，窗外没有绩点",
    "上午的效率曲线：开局即巅峰，然后跳水",
    "别人在记重点，你在记「老师今天穿什么」",
    "连堂的上午，是意志力的连续剧",
    "别点头了，老师以为你听懂了会更来劲",
    "上午的咖啡，撑的是仪式感，不是脑子"
)
private val extrasClassNoon = listOf(
    "中午的课，是胃在讲课",
    "别想食堂了，想了也得坐着",
    "这节上完就是饭点，你的魂已经去排队了",
    "饭点上课，是对干饭魂的公开处刑",
    "老师讲公式，你脑子里全是菜名",
    "中午的知识进不去，下午的困拦不住",
    "别人等下课铃，你等开饭铃",
    "这个点还能听进去的，建议直接保送食堂"
)
private val extrasClassAfternoon = listOf(
    "下午的困，是生理规律，不是态度问题",
    "太阳晒着，老师讲着，你飘着",
    "别硬撑了，你的点头频率已经暴露了",
    "空调在吹，你在睡，分工明确",
    "下午第一节课，全班集体进入省电模式",
    "眼皮在跳科目一，你在跳下课",
    "别怪自己，是午饭在体内开了慢充",
    "老师擦黑板的声音，是你的白噪音"
)
private val extrasClassEvening = listOf(
    "晚课的教室，一半人在线下挂机",
    "别睡了，晚课睡了晚上就真睡不着了",
    "这个点还在听的，不是热爱，是怕点名",
    "晚课的意义：证明你今天还没完全摆",
    "窗外在天黑，你在神游，进度一致",
    "晚课的手机亮度，暴露了你的听课深度",
    "别看表了，看一次慢一分钟",
    "别人晚自习开卷，你晚自习开机"
)
private val extrasClassNight = listOf(
    "晚课收尾，宿舍的床已经在想你",
    "别急，最后一节的结束铃比下课铃更神圣",
    "撑完这节，今晚就可以名正言顺摆烂了",
    "夜色正好，可惜你在教室，不在操场",
    "这个点的知识，多半会和困意一起过期",
    "最后一节的专注，全用来倒计时了",
    "老师还在讲，你的魂已办理入住宿舍",
    "别提前收包，最后五分钟才是修罗场"
)

private val extrasBreakDawn = listOf(
    "早课间别趴了，趴了第二节更废",
    "这个点的课间，用来清醒，不是用来做梦",
    "走廊风大，吹不走你的困",
    "去洗把脸，比咖啡便宜，比硬撑体面",
    "早课间补觉，是给下午挖坑",
    "别人晒太阳，你晒枕头印",
    "别刷手机了，屏幕比天光还晃眼",
    "站着也困的课间，建议原地罚站（不是）"
)
private val extrasBreakMorning = listOf(
    "上午课间，刷两分钟就变十分钟",
    "别去小卖部了，去了钱包和腰围一起受伤",
    "接水的时候想想下节课，算了别想了",
    "课间最忙的器官：拇指",
    "走廊走一圈，消息刷一堆，重点一个没有",
    "同桌补觉，你补瓜，分工合理",
    "上午课间的规划很大，执行很手机",
    "别笑太大声，下节课老师记得你"
)
private val extrasBreakNoon = listOf(
    "饭点课间，食堂才是主战场",
    "别纠结吃什么了，纠结完队伍就长了",
    "中午能趴就趴，下午会感谢现在的你",
    "冲食堂的速度，暴露了你对这门课的态度",
    "课间十分钟，排队二十分钟，很公平",
    "别人午休，你午刷，下午一起倒",
    "吃完别立刻躺，除非你想给下午签到失败",
    "中午的自由很短，队伍很长"
)
private val extrasBreakAfternoon = listOf(
    "下午课间，走廊的风都比你清醒",
    "用这几分钟站起来，不然第三节直接焊在椅子上",
    "别刷手机了，你的眼睛比脑子先罢工",
    "晒两分钟太阳，比续杯咖啡管用（大概）",
    "下午课间的打哈欠会传染，你就是传染源",
    "别买冰饮了，胃和钱包一起凉",
    "站起来伸个懒腰，假装自己还活着",
    "走廊的吵闹，是下午唯一的提神饮料"
)
private val extrasBreakEvening = listOf(
    "晚课间，出去走两步，教室快把你腌入味了",
    "别瘫，晚课间瘫完，最后一节直接交卷式听课",
    "这个点的清醒，全靠走廊冷风硬灌",
    "楼道灯比教室亮，也比你的前途亮一点",
    "晚课间的风是免费的，困是自费的",
    "别开黑了，开了最后一节就没了",
    "去接杯热水，给晚上留点人样",
    "窗外路灯亮了，你的斗志该开灯了"
)
private val extrasBreakNight = listOf(
    "晚课间别开新局，开了就回不来了",
    "最后一点课间电量，留给下节课，不是短视频",
    "走廊灯下站会儿，回教室还能再演十分钟",
    "夜课间的安静，适合反省——反省完接着玩",
    "别去便利店了，去了钱包比人先空",
    "最后一点课间的体面，是回教室坐直",
    "晚课间刷到的下饭视频，下节课会在你脑子里重播"
)

private val extrasFreeDawn = listOf(
    "这么早没课，你确定不是忘看课表",
    "早起没事干，是奢侈，也是浪费",
    "别睡回笼了，睡完中午更废",
    "五点醒着还不学也不睡，卡在人生加载界面",
    "清晨没课，食堂都比你有安排",
    "这个点的自由，是昨晚熬夜的分期付款",
    "别感动自己起得早，起得早也没干正事",
    "窗外鸟都开工了，你还在缓冲"
)
private val extrasFreeMorning = listOf(
    "上午没课，图书馆有空位，你有借口",
    "别人在上课，你在计划——然后计划失败",
    "自由的上午，通常从「先躺十分钟」开始",
    "早饭可以慢慢吃，但别吃到中午",
    "没课的上午，手机电量掉得比绩点快",
    "说好预习，结果在给收藏夹分类",
    "别人在教室渡劫，你在宿舍修仙",
    "上午的安静很贵，你用来刷完了"
)
private val extrasFreeNoon = listOf(
    "没课的中午，吃慢点，反正没人催你上课",
    "午睡可以，别睡到以为今天是周末",
    "饭点自由，是你今天最实在的自由",
    "别人抢座，你抢被子，赛道不同",
    "中午的计划：学习。中午的实际：饭+躺",
    "没课的午饭吃出了放假的感觉，然后就真放假了一下午",
    "别点太多，下午你会困到骂自己",
    "午后的太阳不等你，你还在第二碗"
)
private val extrasFreeAfternoon = listOf(
    "下午没课，阳光这么好，适合焦虑",
    "别把空闲过成加班的愧疚，也别过成纯刷",
    "这个点还躺着的，晚上会还的",
    "说好下午学习，结果下午在想晚上吃什么",
    "没课的下午，是给手机充电，不是给自己充电",
    "别人实验报告，你实验发呆",
    "阳光透过窗帘：提示你该出门了，你当暖被",
    "下午的自由很长，进度条一直是 0%"
)
private val extrasFreeEvening = listOf(
    "晚上没课，自由的味道是手机发烫",
    "别报复性熬夜了，明天没有课也会困",
    "空出来的晚上，最后都交给了短视频",
    "晚上的计划很大，手很诚实地打开了娱乐软件",
    "没课的夜，更容易把「明天一定」说出口",
    "别人在晚自习，你在晚自习游戏（也算自习？）",
    "晚饭后的躺平，会一路躺到愧疚",
    "别开新番了，开了就是通宵预告"
)
private val extrasFreeNight = listOf(
    "没课的晚上，更容易把自己熬废",
    "别人晚自习，你晚自习手机——也算自习？",
    "夜深了，你的待办列表还醒着，你快睡了",
    "没课的夜很长，玩起来又太短",
    "说好只玩一局，天都快亮了还没「只」",
    "别把自由夜过成通宵前传",
    "月亮上班了，你的正事还没上班",
    "这个点还不睡的自由，明天会连本带利收走"
)

private fun timeExtrasFor(scene: QuoteScene, band: QuoteTimeBand): List<String> = when (scene) {
    QuoteScene.IN_CLASS_JUST, QuoteScene.IN_CLASS_MID, QuoteScene.IN_CLASS_END -> when (band) {
        QuoteTimeBand.DAWN -> extrasClassDawn
        QuoteTimeBand.MORNING -> extrasClassMorning
        QuoteTimeBand.NOON -> extrasClassNoon
        QuoteTimeBand.AFTERNOON -> extrasClassAfternoon
        QuoteTimeBand.EVENING -> extrasClassEvening
        QuoteTimeBand.NIGHT -> extrasClassNight
        QuoteTimeBand.LATE -> emptyList()
    }
    QuoteScene.SHORT_BREAK, QuoteScene.LONG_BREAK -> when (band) {
        QuoteTimeBand.DAWN -> extrasBreakDawn
        QuoteTimeBand.MORNING -> extrasBreakMorning
        QuoteTimeBand.NOON -> extrasBreakNoon
        QuoteTimeBand.AFTERNOON -> extrasBreakAfternoon
        QuoteTimeBand.EVENING -> extrasBreakEvening
        QuoteTimeBand.NIGHT -> extrasBreakNight
        QuoteTimeBand.LATE -> emptyList()
    }
    QuoteScene.BEFORE_FIRST, QuoteScene.NO_CLASS, QuoteScene.AFTER_ALL -> when (band) {
        QuoteTimeBand.DAWN -> extrasFreeDawn
        QuoteTimeBand.MORNING -> extrasFreeMorning
        QuoteTimeBand.NOON -> extrasFreeNoon
        QuoteTimeBand.AFTERNOON -> extrasFreeAfternoon
        QuoteTimeBand.EVENING -> extrasFreeEvening
        QuoteTimeBand.NIGHT -> extrasFreeNight
        QuoteTimeBand.LATE -> emptyList()
    }
    else -> emptyList()
}

private fun quotePoolFor(scene: QuoteScene, hour: Int): List<String> {
    val base = when (scene) {
        QuoteScene.DEEP_NIGHT -> quotesDeepNight
        QuoteScene.NO_CLASS -> quotesNoClass
        QuoteScene.OTHER_DAY -> quotesOtherDay
        QuoteScene.SCHEDULE_BROKEN -> quotesScheduleBroken
        QuoteScene.BEFORE_FIRST -> quotesBeforeFirst
        QuoteScene.IN_CLASS_JUST -> quotesInClassJust
        QuoteScene.IN_CLASS_MID -> quotesInClassMid
        QuoteScene.IN_CLASS_END -> quotesInClassEnd
        QuoteScene.SHORT_BREAK -> quotesShortBreak
        QuoteScene.LONG_BREAK -> quotesLongBreak
        QuoteScene.AFTER_ALL -> quotesAfterAll
    }
    val extras = timeExtrasFor(scene, quoteTimeBand(hour))
    if (extras.isEmpty()) return base
    // 去重，避免底池和加菜撞句
    val seen = HashSet<String>(base.size + extras.size)
    return (base + extras).filter { seen.add(it) }
}

@Composable
private fun QuoteCard(
    isPageToday: Boolean,
    todayCourses: List<Course>,
    tomorrowCourses: List<Course> = emptyList(),
    sectionTimes: Map<Int, String>,
    wallpaperBackdrop: Backdrop? = null,
    blurRadius: Float = 0f,
    surfaceOpacity: Float
) {
    var scene by remember {
        mutableStateOf(
            computeQuoteScene(isPageToday, todayCourses, sectionTimes)
        )
    }
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    // 与助手同频：场景/整点切换时立刻换池
    LaunchedEffect(isPageToday, todayCourses, sectionTimes) {
        while (true) {
            val nextScene = computeQuoteScene(isPageToday, todayCourses, sectionTimes)
            if (nextScene != scene) scene = nextScene
            val nextHour = LocalTime.now().hour
            if (nextHour != hour) hour = nextHour
            delay(1_000L.milliseconds)
        }
    }

    val pool = quotePoolFor(scene, hour)
    val processId = android.os.Process.myPid().toLong()
    // 同一进程同场景同时段固定一条；整点变化会自然换一条
    val quoteIndex = remember(scene, hour, processId, pool.size) {
        val seed = processId xor (scene.ordinal * 31L) xor (hour * 17L)
        (((seed % pool.size) + pool.size) % pool.size).toInt()
    }
    val quote = pool[quoteIndex]

    BlurCard(
        cornerRadius = 20.dp,
        wallpaperBackdrop = wallpaperBackdrop,
        blurRadius = blurRadius,
        surfaceOpacity = surfaceOpacity,
        showEdgeLight = wallpaperBackdrop != null && blurRadius > 0f,
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


// 今日页分组标题：始终套椭圆（胶囊）底；有壁纸时走与卡片相同的 blur/lens/透明度
@Composable
private fun CourseSectionTitle(
    text: String,
    wallpaperBackdrop: Backdrop? = null,
    blurRadius: Float = 0f,
    surfaceOpacity: Float,
    modifier: Modifier = Modifier
) {
    val style = MiuixTheme.textStyles.subtitle.copy(fontWeight = FontWeight.Medium)
    val isDark = isAppDarkTheme()
    val refraction = LocalCardRefraction.current
    // 有壁纸时用纯黑/纯白，压在毛玻璃上更干净
    val textColor = when {
        wallpaperBackdrop != null -> if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)
        else -> Color(0xFF8F9CAE)
    }

    if (wallpaperBackdrop != null) {
        val shape = ContinuousCapsule()
        Box(
            modifier = modifier
                .padding(vertical = 6.dp)
                .clip(shape)
                .drawBackdrop(
                    backdrop = wallpaperBackdrop,
                    shape = { shape },
                    effects = {
                        blur(blurRadius.dp.toPx())
                        if (refraction != CardRefractionLevel.OFF) {
                            lens(refraction.lensRadiusDp.dp.toPx(), refraction.lensStrengthDp.dp.toPx())
                        }
                    },
                    highlight = null,
                    onDrawSurface = {
                        drawRect(if (isDark) Color.Black.copy(alpha = surfaceOpacity) else Color.White.copy(alpha = surfaceOpacity))
                    }
                )
                // 标题与课程卡一致用淡描边；亮版只留给今日助手/格言
                .edgeLight(shape = shape, edgeLight = rememberCardEdgeLight())
        ) {
            Text(
                text = text,
                style = style,
                color = textColor,
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
        modifier = modifier
            .padding(vertical = 6.dp)
            .clip(ContinuousCapsule())
            .background(pillBg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.addCourseSections(
    morningCourses: List<Course>,
    afternoonCourses: List<Course>,
    eveningCourses: List<Course>,
    pageCourses: List<Course>,
    isPageToday: Boolean,
    pageDate: LocalDate,
    pageWeek: Int,
    courses: List<Course>,
    hiddenCourseIds: Set<String>,
    sectionTimes: Map<Int, String>,
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: android.graphics.Bitmap?, courseIdToHide: String, targetWeek: Int) -> Unit,
    wallpaperBackdrop: Backdrop? = null,
    blurRadius: Float = 0f,
    surfaceOpacity: Float,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true
) {
    if (morningCourses.isNotEmpty()) {
        item {
            Column {
                CourseSectionTitle(
                    text = "上午课程",
                    wallpaperBackdrop = wallpaperBackdrop,
                    blurRadius = blurRadius,
                    surfaceOpacity = surfaceOpacity
                )
                BlurCard(cornerRadius = 20.dp, wallpaperBackdrop = wallpaperBackdrop, blurRadius = blurRadius, surfaceOpacity = surfaceOpacity, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        morningCourses.forEach { course ->
                            CourseItemWithClick(course, courses, hiddenCourseIds, sectionTimes, pageDate, pageWeek, onCourseClick, wallpaperBackdrop != null, showClassroom, showTeacher)
                        }
                    }
                }
            }
        }
    }
    if (afternoonCourses.isNotEmpty()) {
        item {
            Column {
                CourseSectionTitle(
                    text = "下午课程",
                    wallpaperBackdrop = wallpaperBackdrop,
                    blurRadius = blurRadius,
                    surfaceOpacity = surfaceOpacity
                )
                BlurCard(cornerRadius = 20.dp, wallpaperBackdrop = wallpaperBackdrop, blurRadius = blurRadius, surfaceOpacity = surfaceOpacity, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        afternoonCourses.forEach { course ->
                            CourseItemWithClick(course, courses, hiddenCourseIds, sectionTimes, pageDate, pageWeek, onCourseClick, wallpaperBackdrop != null, showClassroom, showTeacher)
                        }
                    }
                }
            }
        }
    }
    if (eveningCourses.isNotEmpty()) {
        item {
            Column {
                CourseSectionTitle(
                    text = "晚上课程",
                    wallpaperBackdrop = wallpaperBackdrop,
                    blurRadius = blurRadius,
                    surfaceOpacity = surfaceOpacity
                )
                BlurCard(cornerRadius = 20.dp, wallpaperBackdrop = wallpaperBackdrop, blurRadius = blurRadius, surfaceOpacity = surfaceOpacity, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        eveningCourses.forEach { course ->
                            CourseItemWithClick(course, courses, hiddenCourseIds, sectionTimes, pageDate, pageWeek, onCourseClick, wallpaperBackdrop != null, showClassroom, showTeacher)
                        }
                    }
                }
            }
        }
    }
    if (pageCourses.isEmpty()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isPageToday) "今天没有课程，好好休息吧！" else "这天没有课程",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CourseItemWithClick(
    course: Course,
    allCourses: List<Course>,
    hiddenCourseIds: Set<String>,
    sectionTimes: Map<Int, String>,
    pageDate: LocalDate,
    pageWeek: Int,
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: android.graphics.Bitmap?, courseIdToHide: String, targetWeek: Int) -> Unit,
    hasWallpaper: Boolean = false,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true
) {
    var itemBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val isHidden = course.id in hiddenCourseIds
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isHidden) 0f else 1f }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasWallpaper) Modifier else Modifier.background(MiuixTheme.colorScheme.background))
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                    val size = coordinates.size
                    itemBounds = androidx.compose.ui.geometry.Rect(
                        left = position.x, top = position.y,
                        right = position.x + size.width, bottom = position.y + size.height
                    )
                }
                .clickable {
                    val bounds = itemBounds
                    if (bounds != null) {
                        val sameNameCourses = allCourses.filter { it.name == course.name }
                        onCourseClick(sameNameCourses, bounds.left, bounds.top, bounds.width, bounds.height, null, course.id, pageWeek)
                    }
                }
        ) {
            CourseItemContent(course, sectionTimes, pageDate, showClassroom, showTeacher)
        }
    }
}

