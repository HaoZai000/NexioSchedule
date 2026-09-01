/** 今日课程页面 - 显示当天课程和当前/下一节课信息 */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.edit
import com.haooz.chedule.data.Course
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import com.haooz.chedule.ui.basic.SharedScrollBehavior
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
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.milliseconds
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberKyantLayerBackdrop

/**
 * 今日页卡片：有壁纸时使用 drawBackdrop 模糊壁纸（与课程卡片风格一致），
 * 无壁纸时回退为普通 miuix Card。
 * @param lightAlpha 亮色模式底色透明度
 * @param darkAlpha 暗色模式底色透明度
 * @param showEdgeLight 是否显示高光描边
 */
@Composable
fun BlurCard(
    cornerRadius: Dp = 20.dp,
    wallpaperBackdrop: Backdrop? = null,
    blurRadius: Float = 0f,
    lightAlpha: Float = 0.64f,
    darkAlpha: Float = 0.64f,
    showEdgeLight: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hasBlur = blurRadius > 0f && wallpaperBackdrop != null
    val isDark = isAppDarkTheme()

    if (hasBlur) {
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
                        lens(12f.dp.toPx(), 12f.dp.toPx())
                    },
                    highlight = null,
                    onDrawSurface = {
                        drawRect(if (isDark) Color.Black.copy(alpha = darkAlpha) else Color.White.copy(alpha = lightAlpha))
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
private fun CourseItemContent(course: Course, sectionTimes: Map<Int, String>, pageDate: LocalDate = LocalDate.now()) {
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
            LocalTime.parse(timeStr.trim(), DateTimeFormatter.ofPattern("HH:mm"))
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
        while (true) {
            val today = LocalDate.now()
            if (!pageDate.isEqual(today)) {
                courseStatus = if (pageDate.isBefore(today)) "已结束" else "未开始"
                return@LaunchedEffect
            }
            val now = LocalTime.now()
            when {
                startTime == null || endTime == null -> {
                    courseStatus = "未知"
                }
                now.isBefore(startTime) -> {
                    courseStatus = "未开始"
                }
                now.isAfter(endTime) -> {
                    courseStatus = "已结束"
                }
                else -> {
                    val duration = java.time.Duration.between(now, endTime)
                    val totalSeconds = duration.seconds
                    remainingMinutes = (totalSeconds / 60).toInt()
                    remainingSeconds = (totalSeconds % 60).toInt()
                    courseStatus = "进行中"
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
                    if (course.classroom.isNotEmpty()) append(" | ").append(course.classroom)
                    if (course.teacher.isNotEmpty()) append(" | ").append(course.teacher)
                },
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onBackgroundVariant
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

/**
 * 根据开学日期和目标日期计算对应周次
 */
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
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: android.graphics.Bitmap?, courseIdToHide: String) -> Unit = { _, _, _, _, _, _, _ -> },
    pagerState: androidx.compose.foundation.pager.PagerState,
    navBarStyle: String = "standard",
    onScrollYChanged: (Int) -> Unit = {},
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
    liquidGlassBackdrop: Backdrop? = null,
    // Activity 层提升的状态，return@Scaffold 不会销毁
    externalListState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
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


    val hapticFeedback = LocalHapticFeedback.current

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    // Kyant Backdrop：供今日页卡片 drawBackdrop 模糊壁纸使用
    val cardBackdrop = rememberKyantLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val hasWallpaper = todayShowWallpaper && wallpaperBitmap != null


    val isTablet = navBarStyle == "rail"
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 16.dp
    val topBarHeightDp = with(density) { (settingsScrollBehavior?.currentHeightPx ?: 0f).toDp() }

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            // 壁纸背景
            if (todayShowWallpaper && wallpaperBitmap != null) {
                Box(modifier = Modifier.fillMaxSize().kyantLayerBackdrop(cardBackdrop)) {
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
                    androidx.compose.foundation.Image(
                        bitmap = wallpaperBitmap.asImageBitmap(),
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
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageDate = LocalDate.now().plusDays((page - MAX_DATE_OFFSET).toLong())
                val pageDayOfWeek = pageDate.dayOfWeek.value.let { if (it == 7) 7 else it }
                val pageWeek = remember(pageDate, classStartTime) {
                    calculateWeekFromDate(classStartTime, pageDate)
                }
                val pageCourses = remember(courses, pageWeek, pageDayOfWeek, smartWeekend) {
                    val dayRange =
                        (1..5).toList() + settingsViewModel.getWeekendDaysForWeek(pageWeek)
                            .filter { it in 6..7 }
                    if (pageDayOfWeek in dayRange) {
                        courses.filter { it.dayOfWeek == pageDayOfWeek && it.isActiveInWeek(pageWeek) }
                            .sortedBy { it.startSection }
                    } else {
                        emptyList()
                    }
                }
                val morningCourses = pageCourses.filter { it.startSection <= morningSections }
                val afternoonCourses = pageCourses.filter {
                    it.startSection > morningSections && it.startSection <= morningSections + afternoonSections
                }
                val eveningCourses = pageCourses.filter {
                    it.startSection > morningSections + afternoonSections
                }

                val isPageToday = pageDate == LocalDate.now()

                // 计算明天的课程（用于今日助手提示）
                val tomorrowCourses = remember(courses, pageWeek, pageDayOfWeek, isPageToday) {
                    if (isPageToday) {
                        val tomorrowDay = if (pageDayOfWeek == 7) 1 else pageDayOfWeek + 1
                        val tomorrowWeek = if (pageDayOfWeek == 7) pageWeek + 1 else pageWeek
                        courses.filter { it.dayOfWeek == tomorrowDay && it.isActiveInWeek(tomorrowWeek) }
                            .sortedBy { it.startSection }
                    } else {
                        emptyList()
                    }
                }

                val dateText = pageDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
                val listState = externalListState
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemScrollOffset }
                        .collect { offset ->
                            onScrollYChanged(offset)
                        }
                }

                val now = LocalTime.now()
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
                        // 左侧 - 日期 + 每日一言 + 今日助手（固定）
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(
                                    top = paddingValues.calculateTopPadding() + topBarHeightDp,
                                    bottom = 60.dp
                                )
                        ) {
                            Text(
                                text = dateText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            QuoteCard(hour = now.hour, wallpaperBackdrop = if (hasWallpaper) cardBackdrop else null, blurRadius = cardBlurRadius)
                            if (isPageToday) {
                                Spacer(modifier = Modifier.height(12.dp))
                                TodayAssistantCard(
                                    courses = pageCourses,
                                    tomorrowCourses = tomorrowCourses,
                                    sectionTimes = sectionTimes,
                                    morningSections = morningSections,
                                    afternoonSections = afternoonSections,
                                    wallpaperBackdrop = if (hasWallpaper) cardBackdrop else null,
                                    blurRadius = cardBlurRadius
                                )
                            }
                        }
                        // 右侧 - 课程列表（独立滚动）
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .overScrollVertical()
                                .scrollEndHaptic(
                                    hapticFeedbackType = HapticFeedbackType.TextHandleMove
                                )
                                .then(
                                    if (settingsScrollBehavior != null) Modifier.nestedScroll(settingsScrollBehavior.nestedScrollConnection) else Modifier
                                ),
                            contentPadding = PaddingValues(
                                top = paddingValues.calculateTopPadding() + topBarHeightDp + 14.dp,
                                bottom = 120.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            addCourseSections(morningCourses, afternoonCourses, eveningCourses, pageCourses, isPageToday, pageDate, courses, hiddenCourseIds, sectionTimes, onCourseClick, if (hasWallpaper) cardBackdrop else null, cardBlurRadius)
                        }
                    }
                } else {
                    // 手机：上下排列，整体滚动
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
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
                                top = paddingValues.calculateTopPadding() + topBarHeightDp,
                            end = tabletHorizontalPadding,
                            bottom = 120.dp
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
                            QuoteCard(hour = now.hour, wallpaperBackdrop = if (hasWallpaper) cardBackdrop else null, blurRadius = cardBlurRadius)
                        }
                        if (isPageToday) {
                            item {
                                TodayAssistantCard(
                                    courses = pageCourses,
                                    tomorrowCourses = tomorrowCourses,
                                    sectionTimes = sectionTimes,
                                    morningSections = morningSections,
                                    afternoonSections = afternoonSections,
                                    wallpaperBackdrop = if (hasWallpaper) cardBackdrop else null,
                                    blurRadius = cardBlurRadius
                                )
                            }
                        }
                        addCourseSections(morningCourses, afternoonCourses, eveningCourses, pageCourses, isPageToday, pageDate, courses, hiddenCourseIds, sectionTimes, onCourseClick, if (hasWallpaper) cardBackdrop else null, cardBlurRadius)
                    }
                }
            }
        }

        // 日期选择弹窗
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

@Composable
private fun QuoteCard(hour: Int, wallpaperBackdrop: Backdrop? = null, blurRadius: Float = 0f) {
    val h6 = listOf(
        "太阳都打卡上班了，你还在被窝里装死？",
        "这个点能醒的，不是被穷醒就是被尿憋醒",
        "早起的人不是在努力，是在跟被窝演宫斗",
        "天都亮了，你的斗志呢？哦，还在睡",
        "叫醒你的不是梦想，是昨晚没关的闹钟",
        "被窝那么香，可惜它养不出存款",
        "早起的虫儿被鸟吃，你怎么不去当鸟？",
        "六点的风很凉，但穷更凉",
        "别躺了，你的对手已经练完一题了",
        "太阳都起来卷了，你还装死"
    )
    val h7 = listOf(
        "刷牙都刷不利索，还指望刷题？",
        "这个点出门的，都是敢跟早高峰硬碰硬的勇士",
        "别磨叽了，时间不等人，食堂更不等",
        "早餐吃得快，才能抢得到教室第一排",
        "七点的你像赶集，其实是在赶自己的命",
        "别照镜子了，镜子里的你也不认识这个点起的你",
        "让你起床的不是闹钟，是怕迟到被记名字",
        "出门带风？不，是被风推的",
        "早饭吃半顿，上午饿到怀疑人生",
        "迟到的借口千篇一律，挨批的方式五花八门"
    )
    val h8 = listOf(
        "早八的课，听了等于没听，但你敢不听？",
        "教室里的清醒程度，取决于昨晚几点睡",
        "这个点还精神的，不是卷王就是昨晚没睡觉",
        "早八的困，是任何咖啡都救不了的绝症",
        "第一节课就把魂抽走，剩下五节全是空壳",
        "别装懂了，老师走过你身边时你笔都没动",
        "早八人的一天，从'我是谁我在哪'开始",
        "这节课的价值，取决于你下次考试抄不抄得到",
        "坐前排的勇士，和后排的睡神隔着一个银河系",
        "早八拼的不是学习，是看谁先顶不住"
    )
    val h9 = listOf(
        "老师讲的是天书，你听的是寂寞",
        "知识点从耳朵进，从另一只耳朵出，挺圆滑",
        "这个点还在坚持听的，建议查查是不是睡眠障碍",
        "你记的笔记，老师看了都得愣三秒",
        "别问老师讲没讲过，问就是你睡过去了",
        "书的封面很新，因为你根本不好意思翻开",
        "听懂一半就敢举手，勇气可嘉",
        "你的脑子在说：这题超纲，我先撤了",
        "别人在记重点，你在画重点'假的'",
        "九点的你：脑子空空，只有困意满载"
    )
    val h10 = listOf(
        "笔记记了三页，字都不认识，挺行为艺术",
        "这个点的课堂，谁撑得住谁就是装逼之王",
        "别盯着PPT发呆，它比你稳定多了",
        "你记的笔记，期末复习时你管它叫'天书'",
        "认真听了十分钟，觉得今天值了——太天真",
        "眼睛是撑着的，脑子已经打烊了",
        "老师拖堂时，你的灵魂已经在食堂排队了",
        "这节的内容，就是你期末的送分题，你该睡",
        "别问了，问就是你错过了重点",
        "你的人坐着，心早就开溜了"
    )
    val h11 = listOf(
        "还有十分钟，你的胃已经开始倒计时读秒了",
        "下课铃没响，你的脚已经在门口就位了",
        "最后十分钟的意志力，全用在忍饿上了",
        "别装了，你现在满脑子都是食堂的饭",
        "下课倒计时，比高考倒计时还激动",
        "你听课的质量，和离下课的时间成反比",
        "还剩一节课，你的魂已经提前下课了",
        "别抱怨了，食堂的队伍可比你想象的长",
        "这个点的你，饭还没吃，梦先做了",
        "下课铃一响，你是全校跑得最快的选手"
    )
    val h12 = listOf(
        "干饭积极，学习能不能也这么积极？",
        "食堂阿姨的手一抖，抖掉的是你的肉和快乐",
        "这个点的你，学习10分钟，干饭到下午",
        "饭卡一刷，命都续上了，工位接着坐",
        "干饭不积极，脑子有问题（仅限饿的时候）",
        "一边干饭一边刷手机，是你一天中最专注的时刻",
        "饭是吃饱了，作业一行没动，挺好",
        "中午不睡，下午崩溃，但你现在吃得挺香",
        "学习强国你没学，干饭强国你是一把手",
        "干饭魂上身，回到教室又是一条咸鱼"
    )
    val h13 = listOf(
        "午休十分钟，你睡了俩小时，够意思",
        "这个点的你，趴桌子的姿势比学习姿势标准",
        "午觉睡到自然醒？那下午的课也算废了",
        "午休是你的续命符，也是下午课的催命符",
        "别睡了，期中考试的题可不会睡",
        "午休结束的闹钟，是你和梦想的分手信",
        "睡饱了下午照样困，你只是骗过了自己的良心",
        "桌子比你努力，它一直撑着你的脸",
        "午休是充电还是断电，你自己心里没数吗",
        "醒了就别赖了，下午还有一炉要烧"
    )
    val h14 = listOf(
        "午后的困，是任何励志语录都治不好的",
        "阳光一晒，你的眼皮就开始集体叛变",
        "这个点还能坐直的，都是人形立牌成精",
        "别跟下午的困意硬刚，你刚不过它",
        "老师的催眠术，午后达到峰值",
        "你困到怀疑自己是不是被知识腌入味了",
        "下午第一节课，全班集体梦游现场",
        "咖啡续命，你的胃替你扛下了所有",
        "别怪天气，你就是困",
        "下午的课堂，清醒的是老师，睡着的是全世界"
    )
    val h15 = listOf(
        "下午三点，你的灵魂准时下班去摸鱼了",
        "这个点的你，和桌椅融为一体，是行为艺术",
        "别撑了，你的眼皮都开始自由落体了",
        "下午的课是意志力的极限测试，你及格了吗",
        "数老师的眨眼次数，是你唯一的清醒来源",
        "低谷期的人别硬卷，先把手里的咖啡喝完",
        "下午的你像在开盲盒，开出来全是困",
        "想睡就睡，别浪费那份学费",
        "三点还清醒的，建议去开公司，别上学了",
        "下午的风吹不醒你，只有下课铃能"
    )
    val h16 = listOf(
        "倒计时了，你的魂已经开始收拾书包跑了",
        "还有两节课，够你把今天的觉补完",
        "下午的曙光出现了，虽然前面还有两座山",
        "这个点的你，听课全靠意志力在硬撑",
        "别努力了，今天已经没救了，明天再说（假的）",
        "撑到放学你就是今天的英雄——惰性英雄",
        "最后两节，是你一天中演技最高的时刻",
        "别装了，你早就在想着晚饭了",
        "下午的你像在跑马拉松，其实只想躺平",
        "离自由还有两节课，宫斗剧都不敢这么演"
    )
    val h17 = listOf(
        "放学前的最后半小时，你的心已经飞出校门了",
        "这个点的课，你人还在，魂已经放学了",
        "最后半小时是在熬鹰，只不过你是那只鹰",
        "别装了，书包背带你都扣好了",
        "放学铃一响，你就是全校最快乐的人——仅限于今天",
        "最后一节课的演技巅峰：装作在听课",
        "下课倒计时，比追剧更新还让人期待",
        "别磨蹭，你现在跑得比校门口的摊贩还快",
        "课是熬完了，作业一个字没写，明天再说",
        "放学不是终点，是你逃避作业的起点"
    )
    val h18 = listOf(
        "晚饭吃得挺香，作业要写得下才行",
        "干饭第二战，吃完就瘫，标准流程",
        "今晚的快乐是饭给的，悲伤是作业给的",
        "食堂排那么长的队，你是去修行吗",
        "晚上吃点好的，好有力气刷手机",
        "饭后瘫一小时，才有力气开始假装学习",
        "别吃了，再吃晚上又要胖着焦虑了",
        "晚饭的决定性作用：决定你今晚几点开始刷视频",
        "一边吃一边好香，根本不想放下筷子拿笔",
        "干饭干到爽，写作业时才想起来崩溃"
    )
    val h19 = listOf(
        "夜场开卷，卷王们开始表演了",
        "别人在刷手机，你在刷题——关键时刻，演技在线",
        "晚上的图书馆是卷王的修罗场，你连门都懒得进",
        "这个点学习，效率高不高不知道，朋友圈反正先装个",
        "别装了，你打开书拍照的那一刻，今天就已经赢了",
        "晚上八点，你的学习热情还不如手机电量高",
        "书翻开了就是胜利，能不能看进去另说",
        "夜猫子学习，骗得了别人骗不了自己",
        "晚上的专注力，全用来刷新B站了",
        "别装学习机器了，你连洗衣机都不如"
    )
    val h20 = listOf(
        "这个点的你，学不学习不知道，手机是肯定放不下",
        "别骗自己了，你打开作业是为了逃避背题",
        "晚自习是你演技的最高舞台，观众在讲台",
        "别熬了，你眼里的红血丝比脱贫还难除",
        "坐两个小时的屁股，比你的学习成果更值得表扬",
        "这个点还在坚持的——不，你是坚持不住才玩手机",
        "别装深沉了，你连一页书都没翻过去",
        "晚上的你自信心爆棚，早上一觉瞬间清醒",
        "书上的字你认得，组合起来就不认得了",
        "别卷到深夜了，你的黑眼圈比你更懂努力"
    )
    val h21 = listOf(
        "晚上九点，你的意志力和钱包一起告急",
        "这个点的困意，是任何咖啡都续不上的",
        "别撑了，你现在的专注力比流量还少",
        "晚上学习效率断崖式下跌，你选择硬刚断崖",
        "困到开始跟书说话，是不是也该睡了",
        "别装了，你盯着书发呆半小时了吧",
        "夜深人静，你的手机亮得比台灯还刺眼",
        "这个点还清醒的，不是卷王，是明天要交作业的",
        "别熬夜了，你熬夜的样子很努力，结果很打脸",
        "今晚的最后一搏，搏了个寂寞"
    )
    val h22 = listOf(
        "收工了？还是准备开始罪恶的刷手机？",
        "今天的论文进度：0%，游戏进度：80%，明天补",
        "别嘴硬了，你今天的正经学习时长撑不起这张嘴",
        "这个点的你，睡意和愧疚在打架，愧疚先输",
        "别拿'明天一定早睡'骗自己了，你字典里没这词",
        "收工前的最后一秒，你决定点开一个'只看一集'",
        "今晚的任务清单，比你的头发还稀疏",
        "别复盘了，复盘的结论都是又白卷一天",
        "立flag的你最帅，打脸的你也最稳",
        "晚安了，别刷了，明天还要继续装努力"
    )
    val h23 = listOf(
        "这个点的你，不是在努力，是在跟明天抢时间",
        "熬夜熬的不是夜，是第二天的黑眼圈",
        "别拿'灵感在深夜'骗自己，你深夜刷的只是手机",
        "头发：你再熬，我就真没了",
        "明天的课？那是明天的我该祈祷的事",
        "晚上效率高？高的是你的刷视频手速",
        "别骗自己了，你熬夜的样子像在跟床暧昧",
        "这个点的你，自尊和困意博弈中，困意赢了",
        "晚安吧，再刷下去，明天的笔记你又不认识了",
        "深夜的灵感=白日梦+不想睡，都很真实"
    )
    val h0 = listOf(
        "零点已过，你的'今天'还没结束，明天的愧疚已上线",
        "别熬夜修仙了，你连早八都顶不住，还想飞升",
        "凌晨的你是最自信的，明天的你是最愧疚的",
        "这个点的清醒，是用来反省今天白卷的",
        "别刷了，你刷的不是手机，是明天的睡眠",
        "凌晨的灵感，多半是明天醒来就忘的梦话",
        "别装了，你的努力都在手机里，书里没有",
        "零点整，新的一天，新的白卷，开局",
        "别讨好深夜了，它不会给你发工资",
        "睡了睡了，明天一定早睡（你每次都这么说）"
    )
    val h1 = listOf(
        "凌晨一点的倔强，是用来安慰明天的自己的",
        "别修仙了，你连早八都起不来，还渡劫",
        "这个点还醒着的，不是学霸，是不会睡觉",
        "深夜的效率，指的是你刷视频的手速吗",
        "别拿'年轻人熬夜没事'骗自己，脑细胞会抗议",
        "凌晨的安静，是你逃避作业的最佳时刻",
        "别装了，你现在的手和眼，都在手机上",
        "熬夜一时爽，早上火葬场，你懂的",
        "别跟自己较劲了，你输了还能赢一觉",
        "关了手机吧，别让明天的你恨死今天的你"
    )
    val h2 = listOf(
        "凌晨两点，你的黑眼圈已经进入下一条龙服务",
        "这个点还醒着，不是卷，是没救地刷手机",
        "别熬了，凌晨两点的努力，白天八点见真章",
        "深夜的灵感像鬼，白天醒来一个都不剩",
        "别骗自己了，你只是舍不得关掉那个短视频",
        "凌晨两点的你，是在补白天的偷懒账",
        "别跟身体硬刚，它明天会让你跪着还",
        "熬夜的尽头，是明早你起不来的闹钟",
        "这个点的清醒，是明天困的预售券",
        "放下手机，睡觉，别演了，镜头都关了"
    )
    val h3 = listOf(
        "凌晨三点，别硬撑了，你又不是在救火",
        "这个点还醒着，说明你白天睡得太多了",
        "别拿'黑夜给我灵感'当借口，你白天也没见多勤",
        "凌晨三点的执着，是让你明早更狼狈的伏笔",
        "别熬了，你的肝已经在后台申请离职了",
        "深夜的自我感动，白天的现实打脸，稳了",
        "都三点了，你是在修仙还是单纯不敢睡觉",
        "别装了，你的努力连你自己都感动不了几次",
        "凌晨的安静陪你一起摆烂，公平",
        "睡了，明天还要早起（我说的可能是笑话）"
    )
    val h4 = listOf(
        "凌晨四点，天快亮了，你的任务还没开始",
        "这个点醒着的，是在给白天的自己挖坑",
        "别熬了，黎明前的黑暗，是留给倒霉蛋的",
        "别拿通宵换成绩，这种汇率低得离谱",
        "凌晨四点的你，脸色比窗外的天还灰",
        "别感动了，你现在的努力只感动了自己",
        "通宵一晚，换三天颓废，亏大发了",
        "别硬扛了，天亮之前你注定赢不了",
        "这个点的坚持，主要是肌肉记忆在撑着",
        "睡吧，让明天的你去面对今天的债"
    )
    val h5 = listOf(
        "天亮了，你的'今晚一定早睡'正式宣布破产",
        "这个点还醒着，像极了你通宵后的尊容",
        "别熬了，太阳都起来遛弯了，你还硬扛",
        "凌晨五点的你是冠军？不，是熊猫代言人",
        "别感动了，一晚通宵换一个黑眼圈纪念章",
        "天都亮了，你的作业比你的脸色还白吗",
        "别装了，你这个点睡，下午还怎么装努力",
        "早起的鸟儿有虫吃，熬夜的你只有困",
        "新的一天，新的白卷，你准备好了吗",
        "睡吧，天亮了，今晚再战（你也说烂了）"
    )
    val quotes = when (hour) {
        6 -> h6; 7 -> h7; 8 -> h8; 9 -> h9; 10 -> h10
        11 -> h11; 12 -> h12; 13 -> h13; 14 -> h14; 15 -> h15
        16 -> h16; 17 -> h17; 18 -> h18; 19 -> h19; 20 -> h20
        21 -> h21; 22 -> h22; 23 -> h23; 0 -> h0; 1 -> h1
        2 -> h2; 3 -> h3; 4 -> h4; else -> h5
    }
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("quote_prefs", Context.MODE_PRIVATE)
    val savedIndex = prefs.getInt("quote_index", -1)
    val savedProcessId = prefs.getLong("process_id", -1)
    val currentProcessId = android.os.Process.myPid().toLong()
    var quoteIndex by remember {
        if (savedProcessId == currentProcessId && savedIndex >= 0) {
            mutableIntStateOf(savedIndex)
        } else {
            val newIndex = (System.nanoTime() % quotes.size).toInt()
            prefs.edit {
                putInt("quote_index", newIndex)
                    .putLong("process_id", currentProcessId)
            }
            mutableIntStateOf(newIndex)
        }
    }
    val quote = quotes[quoteIndex]
    BlurCard(
        cornerRadius = 20.dp,
        wallpaperBackdrop = wallpaperBackdrop,
        blurRadius = blurRadius,
        lightAlpha = 0.74f,
        darkAlpha = 0.74f,
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

private fun androidx.compose.foundation.lazy.LazyListScope.addCourseSections(
    morningCourses: List<Course>,
    afternoonCourses: List<Course>,
    eveningCourses: List<Course>,
    pageCourses: List<Course>,
    isPageToday: Boolean,
    pageDate: LocalDate,
    courses: List<Course>,
    hiddenCourseIds: Set<String>,
    sectionTimes: Map<Int, String>,
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: android.graphics.Bitmap?, courseIdToHide: String) -> Unit,
    wallpaperBackdrop: Backdrop? = null,
    blurRadius: Float = 0f
) {
    if (morningCourses.isNotEmpty()) {
        item {
            Column {
                SmallTitle(
                    text = "上午课程",
                    modifier = Modifier.offset(x = (-15).dp),
                    hasWallpaper = wallpaperBackdrop != null && blurRadius > 0f
                )
                BlurCard(cornerRadius = 20.dp, wallpaperBackdrop = wallpaperBackdrop, blurRadius = blurRadius, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        morningCourses.forEach { course ->
                            CourseItemWithClick(course, courses, hiddenCourseIds, sectionTimes, pageDate, onCourseClick, wallpaperBackdrop != null && blurRadius > 0f)
                        }
                    }
                }
            }
        }
    }
    if (afternoonCourses.isNotEmpty()) {
        item {
            Column {
                SmallTitle(
                    text = "下午课程",
                    modifier = Modifier.offset(x = (-15).dp),
                    hasWallpaper = wallpaperBackdrop != null && blurRadius > 0f
                )
                BlurCard(cornerRadius = 20.dp, wallpaperBackdrop = wallpaperBackdrop, blurRadius = blurRadius, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        afternoonCourses.forEach { course ->
                            CourseItemWithClick(course, courses, hiddenCourseIds, sectionTimes, pageDate, onCourseClick, wallpaperBackdrop != null && blurRadius > 0f)
                        }
                    }
                }
            }
        }
    }
    if (eveningCourses.isNotEmpty()) {
        item {
            Column {
                SmallTitle(
                    text = "晚上课程",
                    modifier = Modifier.offset(x = (-15).dp),
                    hasWallpaper = wallpaperBackdrop != null && blurRadius > 0f
                )
                BlurCard(cornerRadius = 20.dp, wallpaperBackdrop = wallpaperBackdrop, blurRadius = blurRadius, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        eveningCourses.forEach { course ->
                            CourseItemWithClick(course, courses, hiddenCourseIds, sectionTimes, pageDate, onCourseClick, wallpaperBackdrop != null && blurRadius > 0f)
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
    onCourseClick: (courses: List<Course>, cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float, snapshot: android.graphics.Bitmap?, courseIdToHide: String) -> Unit,
    hasWallpaper: Boolean = false
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
                        onCourseClick(sameNameCourses, bounds.left, bounds.top, bounds.width, bounds.height, null, course.id)
                    }
                }
        ) {
            CourseItemContent(course, sectionTimes, pageDate)
        }
    }
}

