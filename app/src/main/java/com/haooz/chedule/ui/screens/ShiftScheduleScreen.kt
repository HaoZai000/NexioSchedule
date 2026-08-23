/** 排班课表页面 */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.components.SectionColumn
import com.haooz.chedule.ui.components.ShiftDayColumn
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.overlay.BlurBottomSheetTablet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun ShiftScheduleScreen(
    shiftViewModel: ShiftViewModel,
    settingsViewModel: com.haooz.chedule.viewmodel.SettingsViewModel,
    pagerState: androidx.compose.foundation.pager.PagerState,
    cardHeightPerSection: Float = 54f,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    scheduleScrollBehavior: SharedScrollBehavior? = null,
) {
    val shiftScheduleCourses by shiftViewModel.shiftScheduleCourses.collectAsState()
    val shiftScheduleSections by shiftViewModel.shiftScheduleSections.collectAsState()
    val context = LocalContext.current
    val activity = context as? ComponentActivity as? com.haooz.chedule.ui.activities.MainActivity
    val isInFreeformWindow = activity?.isInFreeformWindow == true
    val isDark = isAppDarkTheme()
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()

    var showDetail by remember { mutableStateOf(false) }
    var detailCourses by remember { mutableStateOf<List<Pair<String, Course>>>(emptyList()) }
    var sheetContentBackdrop by remember { mutableStateOf<com.kyant.backdrop.Backdrop?>(null) }
    var skipSheetEnterAnimation by remember { mutableStateOf(showDetail) }
    androidx.compose.runtime.LaunchedEffect(showDetail) {
        if (!showDetail) {
            skipSheetEnterAnimation = false
        }
    }

    val maxMorning = remember(shiftScheduleSections) {
        shiftScheduleSections.values.maxOfOrNull { it.first } ?: 4
    }
    val maxAfternoon = remember(shiftScheduleSections) {
        shiftScheduleSections.values.maxOfOrNull { it.second } ?: 4
    }
    val maxEvening = remember(shiftScheduleSections) {
        shiftScheduleSections.values.maxOfOrNull { it.third } ?: 4
    }
    val totalSections = maxMorning + maxAfternoon + maxEvening

    val wallpaperBackdropColor = if (isDark) Color(0xFF000000) else Color(0xFFF7F7F7)

    Box(modifier = Modifier.fillMaxSize().background(wallpaperBackdropColor)) {

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 0
    ) { page ->
        val week = page + 1
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (scheduleScrollBehavior != null) Modifier.nestedScroll(scheduleScrollBehavior.nestedScrollConnection)
                    else Modifier
                )
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .verticalScroll(scrollState)
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                        if (isInFreeformWindow) (activity.titleBarHeight) + 40.dp else 96.dp,
                    bottom = 140.dp
                )
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isTablet) Modifier.padding(horizontal = 24.dp) else Modifier
                        )
                ) {
                    SectionColumn(
                        totalSections = totalSections,
                        morningSections = maxMorning,
                        afternoonSections = maxAfternoon,
                        eveningSections = maxEvening,
                        sectionTimes = sectionTimes,
                        cardHeightPerSection = cardHeightPerSection,
                        isTablet = isTablet
                    )

                    val pageDayRange = remember(week, smartWeekend) {
                        (1..5).toList() + settingsViewModel.getWeekendDaysForWeek(week).filter { it in 6..7 }
                    }

                    pageDayRange.forEach { dayOfWeek ->
                        ShiftDayColumn(
                            dayOfWeek = dayOfWeek,
                            allScheduleCourses = shiftScheduleCourses,
                            morningSections = maxMorning,
                            afternoonSections = maxAfternoon,
                            eveningSections = maxEvening,
                            currentWeek = week,
                            onSlotClick = { _, _, courses ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                detailCourses = courses
                                showDetail = true
                            },
                            cardHeightPerSection = cardHeightPerSection,
                            isTablet = isTablet,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                val morningHeight = (maxMorning * cardHeightPerSection).toInt()
                val afternoonHeight = (maxAfternoon * cardHeightPerSection).toInt()
                val dinnerBreakY = morningHeight + 24 + afternoonHeight

                val dividerShape = ContinuousRoundedRectangle(12.dp)
                val dividerHorizontalPadding = if (isTablet) 24.dp else 4.dp
                val dividerBaseColor = if (isDark) Color(0xFF121212) else Color(0xFFF0F0F0)

                @Composable
                fun BreakDivider(offsetY: Int, text: String) {
                    Box(
                        modifier = Modifier.fillMaxWidth().offset(y = offsetY.dp)
                            .height(24.dp)
                            .padding(vertical = 2.dp)
                            .padding(horizontal = dividerHorizontalPadding)
                            .background(dividerBaseColor, dividerShape),
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

    } // wallpaper Box


    val detailContent: @Composable () -> Unit = {
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
            detailCourses.forEach { (scheduleName, course) ->

                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    insideMargin = PaddingValues(0.dp),
                    pressFeedbackType = PressFeedbackType.None,
                    showIndication = true,
                    colors = CardDefaults.defaultColors(
                        color = if (isDark) Color(0xFF363636).copy(alpha = 0.62f) else Color(0xFFFFFFFF).copy(alpha = 0.7f),
                        contentColor = MiuixTheme.colorScheme.onSurface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = scheduleName,
                            style = MiuixTheme.textStyles.body1.copy(fontSize = 17.sp),
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${course.name}  ${course.getTimeDisplayText()}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(if (isTablet) 0.dp else 260.dp))
        }
    }

    if (isTablet) {
        BlurBottomSheetTablet(
            show = showDetail,
            title = "课表详情",
            dimBackground = true,
            isBottomAligned = true,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = { showDetail = false },
            onSheetContentBackdropCreated = { sheetContentBackdrop = it },
            skipEnterAnimation = skipSheetEnterAnimation,
            content = detailContent
        )
    } else {
        BlurBottomSheet(
            show = showDetail,
            title = "课表详情",
            liquidGlassBackdrop = liquidGlassBackdrop,
            dimBackground = true,
            onDismissRequest = { showDetail = false },
            onSheetContentBackdropCreated = { sheetContentBackdrop = it },
            skipEnterAnimation = skipSheetEnterAnimation,
            content = detailContent
        )
    }
}
