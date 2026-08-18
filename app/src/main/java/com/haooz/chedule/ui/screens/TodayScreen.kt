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
import com.haooz.chedule.ui.basic.OverlayDialog
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
import com.kyant.shapes.RoundedRectangle
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
        val shape = RoundedRectangle(cornerRadius)
        val defaultEdgeLight = rememberDefaultEdgeLight()
        Box(
            modifier = modifier
                .clip(shape)
                .drawBackdrop(
                    backdrop = wallpaperBackdrop,
                    shape = { RoundedRectangle(cornerRadius) },
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
                            shape = RoundedRectangle(cornerRadius),
                            edgeLight = defaultEdgeLight)
                    } else
                        Modifier.edgeLight(
                            shape = RoundedRectangle(cornerRadius),
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
private fun CourseItemContent(course: Course, sectionTimes: Map<Int, String>) {
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

    val initialStatus = remember(startTime, endTime) {
        val now = LocalTime.now()
        when {
            startTime == null || endTime == null -> "未知"
            now.isBefore(startTime) -> "未开始"
            now.isAfter(endTime) -> "已结束"
            else -> "进行中"
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

    LaunchedEffect(startTime, endTime) {
        while (true) {
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
                text = "${course.getTimeDisplayText()} | ${course.classroom} | ${course.teacher}",
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
                            addCourseSections(morningCourses, afternoonCourses, eveningCourses, pageCourses, isPageToday, courses, hiddenCourseIds, sectionTimes, onCourseClick, if (hasWallpaper) cardBackdrop else null, cardBlurRadius)
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
                        addCourseSections(morningCourses, afternoonCourses, eveningCourses, pageCourses, isPageToday, courses, hiddenCourseIds, sectionTimes, onCourseClick, if (hasWallpaper) cardBackdrop else null, cardBlurRadius)
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
        "太阳刚打卡上班，我的灵魂还在梦里蹦迪",
        "闹钟响了三遍，和被窝的离婚官司还没打完",
        "这个点能醒的都是被穷醒的",
        "早起的虫儿被鸟吃，我选择当鸟",
        "天还没亮，我的黑眼圈先亮了",
        "闹钟：起床！我：再睡亿分钟",
        "早起毁一天，不早起毁一辈子",
        "我和被窝的分手现场，每天都在上演",
        "被窝的引力比地球还大",
        "这个点醒的都是被尿憋醒的"
    )
    val h7 = listOf(
        "被窝：你走吧，我配不上你",
        "闹钟：你再不起我就自爆",
        "早起打卡成功，奖励自己再躺五分钟",
        "这个点出门的都是和时间赛跑的亡命之徒",
        "早餐是什么？能吃吗？",
        "刷牙的时候眼睛还没睁开",
        "出门前照镜子：这是谁？不认识",
        "走路带风，因为快迟到了",
        "这个点的早餐摊是我的精神支柱",
        "灵魂还在梦里，肉体已经在赶路"
    )
    val h8 = listOf(
        "早八人的怨气能照亮整个教学楼",
        "教室里的灵魂浓度：2%肉体，98%行尸走肉",
        "这个点能在教室保持清醒的都是国家保护动物",
        "早八的课听了等于没听，没听等于赚到",
        "我的肉体在教室，灵魂还在被窝里办理入住",
        "早八的闹钟，是我和梦想的分手信",
        "教室里的空气有催眠成分，科学无法解释",
        "这个点的教室，是大型集体梦游现场",
        "早八人的一天从一杯美式开始",
        "早起的虫儿被鸟吃，早起的人被课吃"
    )
    val h9 = listOf(
        "困到开始怀疑自己是不是投错胎了",
        "上课五分钟，走神两小时，下课三秒钟",
        "老师的嘴是复读机，我的眼皮是弹簧床",
        "知识像Wi-Fi，我连上了但信号很弱",
        "这个知识点已读不回，我和它八字不合",
        "我的脑子和知识之间隔着一个太平洋",
        "上课的状态：灵魂出窍，肉身挂机",
        "这个点还能听懂的都是外星人",
        "老师的语速和我的困意成正比",
        "知识进脑子的路线：耳朵→大脑→直接弹出"
    )
    val h10 = listOf(
        "课堂，当代大学生的大型催眠现场",
        "笔记记了三页，回头一看全是鬼画符",
        "老师的PPT，当代最强安眠药",
        "这个点还能认真听讲的，建议保送清华",
        "我的眼睛说困了，我的手说它在记笔记",
        "课堂上最活跃的器官：眼皮",
        "笔记写了满页，脑子空空如也",
        "这个点的课堂是意志力的修罗场",
        "老师的PPT翻页声，是我困意的加速器",
        "认真听了二十分钟，成就感拉满"
    )
    val h11 = listOf(
        "等下课的心情，比等双十一快递还焦灼",
        "还有十分钟，我的胃已经发出了防空警报",
        "最后十分钟，是意志力和饥饿的终极Battle",
        "下课铃，是这世界上最动听的交响乐",
        "我的灵魂已经开始在食堂排队了",
        "十分钟有多长？取决于离下课还有多久",
        "我的胃比我的脑子更早知道要下课了",
        "这个点的意志力正在以光速消耗",
        "下课倒计时，我的心跳在加速",
        "最后五分钟，我愿意用一切来换"
    )
    val h12 = listOf(
        "干饭人干饭魂，干饭都是人上人",
        "食堂阿姨的手抖一下，我的快乐就少一半",
        "今日份的命是饭给的",
        "干饭不积极，思想有问题",
        "食堂的队伍，比我的人生规划还长",
        "干饭的速度决定了下午的幸福指数",
        "食堂是大学的第二个教室，教的是生存",
        "饭卡余额是支撑我上课的唯一动力",
        "这个点的食堂是修罗场",
        "吃货的快乐就是这么简单"
    )
    val h13 = listOf(
        "午休十分钟，人间两小时",
        "趴在桌上的灵魂出窍体验，免费赠送",
        "午睡五分钟精神一下午，午睡一小时困一下午",
        "这个点能秒睡的都是天赋型选手",
        "午休结束，我的魂还没从梦里出来",
        "午休是大学生的续命神器",
        "趴在桌上的姿势，比上课认真多了",
        "午休的十分钟，是我一天中最自由的时光",
        "这个点的教室比宿舍还适合睡觉",
        "午休结束的闹钟是世界上最讨厌的声音"
    )
    val h14 = listOf(
        "午后的困意是一场无法拒绝的温柔绑架",
        "困到想把头按进课桌，和知识融为一体",
        "阳光太暖，PPT太催眠，我不行了",
        "这个点还能坐直的都是人体工学椅成精",
        "我的眼皮在打架，我在旁边喊加油",
        "下午的课堂，困意的浓度超标了",
        "午后的阳光和老师的催眠术联手了",
        "这个点的我是行走的安眠药",
        "困到开始和课桌谈恋爱",
        "下午的课堂是意志力的炼狱模式"
    )
    val h15 = listOf(
        "人还活着，但眼睛已经下班了",
        "下午的最低谷，灵魂在休年假",
        "这个点的课堂是大型梦游直播现场",
        "困意来袭，任何抵抗都是徒劳",
        "老师的嘴在动，我的灵魂在放空",
        "下午三点，人和作业总有一个在路上",
        "这个点还能保持清醒的都是神仙",
        "我的眼皮已经罢工了",
        "下午的课堂是意志力的终极考验",
        "困到开始数老师的眨眼次数"
    )
    val h16 = listOf(
        "开始倒计时了，灵魂在收拾书包",
        "还有两节课，胜利就在眼前",
        "希望的曙光出现了，虽然还有两节课",
        "这个点还能听课的，建议去做特工",
        "我的灵魂已经在校门口等我了",
        "下午的阳光比上午的更有希望",
        "还有两节课，我的意志力还能撑一撑",
        "这个点的课堂是黎明前的黑暗",
        "开始计算离自由还有几节课",
        "下午的课堂是马拉松的最后冲刺"
    )
    val h17 = listOf(
        "解放区的天是晴朗的天",
        "最后半小时，快乐已经按捺不住",
        "放学前的躁动，是青春的味道",
        "这个点的课，听了算我输",
        "我的灵魂在倒计时，身体在躁动",
        "最后半小时是煎熬也是希望",
        "放学倒计时，我的内心在狂欢",
        "这个点的课堂是快乐的终点站",
        "我的书包已经准备好奔跑了",
        "放学铃声是我一天中最期待的声音"
    )
    val h18 = listOf(
        "晚饭吃什么，是当代大学生的终极哲学难题",
        "干饭人第二战场，开饭！",
        "食堂：永远的神，永远在排队",
        "今晚吃什么决定了今晚的快乐指数",
        "胃已经准备好接收今天的第二波能量了",
        "晚饭是一天中第二次灵魂充电",
        "食堂阿姨，我的第二个妈",
        "这个点的食堂是快乐的源泉",
        "晚饭的选择困难症又犯了",
        "干饭人永不言败"
    )
    val h19 = listOf(
        "夜猫子们的主场，正式开演",
        "卷王们的灯火，照亮了整个图书馆",
        "天黑了，但知识的灯还没亮",
        "夜生活？不，是夜学生活",
        "这个点还能学进去的都是时间管理大师",
        "晚上的自习室是卷王们的竞技场",
        "夜间的图书馆比白天更安静",
        "这个点的我是学习机器",
        "夜间的效率比白天高十倍",
        "晚上的课堂是学霸的主场"
    )
    val h20 = listOf(
        "自习室，卷王们的第二个家，常年无休",
        "该学习了，如果手机能放下的话",
        "图书馆的椅子比我宿舍的床还亲",
        "这个点还在学的都是卷王中的战斗机",
        "灵魂在自习室充电，肉体在宿舍躺尸",
        "晚上的自习室是我的第二个宿舍",
        "这个点的专注力是白天的三倍",
        "手机和书本的battle，手机赢了",
        "晚上的图书馆是学习的天堂",
        "自习室的灯光比我的前途还亮"
    )
    val h21 = listOf(
        "学习效率开始呈断崖式下跌",
        "夜间的意志力，和钱包一样空",
        "晚安课本，我们梦里继续卷",
        "这个点还能清醒的都是被梦想绑架的",
        "困意是老板，我是打工仔",
        "晚上的意志力正在以光速消耗",
        "这个点的我是行走的催眠师",
        "困到开始和书本说晚安",
        "晚间的课堂是困意的天堂",
        "这个点还能坚持的都是狠人"
    )
    val h22 = listOf(
        "收工！今天的卷量已达标",
        "终于可以合法熬夜了",
        "夜猫子的快乐时光，正式开始",
        "这个点不睡的都是时间的逆行者",
        "我的夜生活，从放下书本开始",
        "收工的快乐，只有卷王才懂",
        "终于可以做自己的事了",
        "这个点的我是自由的灵魂",
        "晚间的快乐时光开始了",
        "收工后的放松是最幸福的时刻"
    )
    val h23 = listOf(
        "熬夜冠军前来报到",
        "明天的早八？那是明天的我该操心的事",
        "熬最晚的夜，敷最贵的膜，做最强的卷王",
        "灵感总在深夜敲门，我选择开门",
        "头发：你礼貌吗？我：不好意思，习惯就好",
        "熬夜是对头发的不尊重，但我选择不尊重",
        "这个点不睡的都是熬夜界的扛把子",
        "深夜的灵感比白天的更有灵魂",
        "熬夜一时爽，一直熬夜一直爽",
        "深夜的手机屏幕比我的未来还亮"
    )
    val h0 = listOf(
        "夜生活？不，是夜修生活",
        "深夜的灵感，比白天多到溢出来",
        "这个点还在学的都是时间管理鬼才",
        "手机屏幕是黑夜里唯一的光",
        "熬夜一时爽，一直熬夜一直爽（假的）",
        "深夜的宿舍是灵感的源泉",
        "这个点的我是熬夜界的天花板",
        "深夜的专注力比白天高十倍",
        "熬夜是对生命的不尊重，但我选择不尊重",
        "深夜的图书馆是我的秘密基地"
    )
    val h1 = listOf(
        "修仙渡劫正式开始",
        "深夜的宿舍是修仙者的道场",
        "这个点不睡的都是仙界的预备役",
        "灵感爆发，挡都挡不住",
        "灵魂在飞升，肉体在犯困",
        "修仙者的夜晚才刚刚开始",
        "深夜的灵感像烟花一样绽放",
        "这个点的我是熬夜界的战斗机",
        "深夜的专注力是我的超能力",
        "修仙第一步：放弃睡眠"
    )
    val h2 = listOf(
        "极限修仙，挑战身体的边界",
        "深夜的宿舍，是和deadline赛跑的赛道",
        "这个点还醒着的都是铁人三项冠军",
        "灵感比黄金还稀有，但今晚我有",
        "眼睛说困了，手说它还能再战",
        "修仙第二步：和困意做朋友",
        "深夜的宿舍是我的第二个战场",
        "这个点的我是熬夜界的冠军",
        "深夜的灵感是我的秘密武器",
        "修仙第三步：忘记时间"
    )
    val h3 = listOf(
        "论文进度：0%，但灵感进度：100%",
        "深夜的图书馆，是孤独者的天堂",
        "这个点还在熬夜的都是人类高质量修仙者",
        "灵感比钻石还稀有，今晚我挖到了",
        "灵魂在学习，肉体已经在梦游了",
        "修仙第四步：灵魂出窍",
        "深夜的宿舍是我的灵感工厂",
        "这个点的我是熬夜界的艺术家",
        "深夜的专注力是我的超能力",
        "修仙第五步：和时间赛跑"
    )
    val h4 = listOf(
        "天快亮了，我的论文还没动",
        "深夜的战场，修仙者的最后阵地",
        "这个点还熬的都是真正的勇士",
        "灵感比阳光还珍贵，但我选择继续",
        "灵魂还在飞升，肉体已经投降",
        "修仙第六步：迎接黎明",
        "深夜的宿舍是我的避风港",
        "这个点的我是熬夜界的传奇",
        "深夜的灵感是我的救命稻草",
        "修仙第七步：天亮了"
    )
    val h5 = listOf(
        "天都快亮了，我和论文谁先秃",
        "修仙者最后的倔强，就是还没睡",
        "这个点没睡的都是熬夜界的天花板",
        "灵感是夜猫子的专属福利",
        "灵魂：再学亿会儿。肉体：告辞",
        "修仙第八步：见证日出",
        "深夜的宿舍是我的最后一个战场",
        "这个点的我是熬夜界的神话",
        "深夜的灵感是我的终极武器",
        "修仙最后一步：早安世界"
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
                SmallTitle(text = "上午课程", modifier = Modifier.offset(x = (-15).dp))
                BlurCard(cornerRadius = 20.dp, wallpaperBackdrop = wallpaperBackdrop, blurRadius = blurRadius, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        morningCourses.forEach { course ->
                            CourseItemWithClick(course, courses, hiddenCourseIds, sectionTimes, onCourseClick, wallpaperBackdrop != null && blurRadius > 0f)
                        }
                    }
                }
            }
        }
    }
    if (afternoonCourses.isNotEmpty()) {
        item {
            Column {
                SmallTitle(text = "下午课程", modifier = Modifier.offset(x = (-15).dp))
                BlurCard(cornerRadius = 20.dp, wallpaperBackdrop = wallpaperBackdrop, blurRadius = blurRadius, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        afternoonCourses.forEach { course ->
                            CourseItemWithClick(course, courses, hiddenCourseIds, sectionTimes, onCourseClick, wallpaperBackdrop != null && blurRadius > 0f)
                        }
                    }
                }
            }
        }
    }
    if (eveningCourses.isNotEmpty()) {
        item {
            Column {
                SmallTitle(text = "晚上课程", modifier = Modifier.offset(x = (-15).dp))
                BlurCard(cornerRadius = 20.dp, wallpaperBackdrop = wallpaperBackdrop, blurRadius = blurRadius, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        eveningCourses.forEach { course ->
                            CourseItemWithClick(course, courses, hiddenCourseIds, sectionTimes, onCourseClick, wallpaperBackdrop != null && blurRadius > 0f)
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
            CourseItemContent(course, sectionTimes)
        }
    }
}

