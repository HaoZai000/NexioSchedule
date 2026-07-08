/** 排班课表页面 */
package com.haooz.chedule.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.components.SectionColumn
import com.haooz.chedule.ui.components.ShiftDayColumn
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val ShiftBlue = Color(0xFF2196F3)

@Composable
fun ShiftScheduleScreen(
    viewModel: CourseViewModel,
    shiftViewModel: ShiftViewModel,
    currentDayOfWeek: Int,
    dayRange: List<Int>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    cardHeightPerSection: Float = 54f
) {
    val shiftScheduleCourses by shiftViewModel.shiftScheduleCourses.collectAsState()
    val shiftScheduleSections by shiftViewModel.shiftScheduleSections.collectAsState()
    val shiftSelectedSchedules by shiftViewModel.shiftSelectedSchedules.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val context = LocalContext.current
    val activity = context as? ComponentActivity as? com.haooz.chedule.ui.activities.MainActivity
    val isInFreeformWindow = activity?.isInFreeformWindow == true
    val isDark = isAppDarkTheme()
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current

    var showDetail by remember { mutableStateOf(false) }
    var detailCourses by remember { mutableStateOf<List<Pair<String, Course>>>(emptyList()) }
    var contentReady by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        contentReady = true
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

    val scheduleColors = remember(shiftSelectedSchedules) {
        shiftSelectedSchedules.mapIndexed { index, name ->
            name to Color(Course.courseColors[index % Course.courseColors.size])
        }.toMap()
    }

    if (!contentReady) return

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
                Row(modifier = Modifier.fillMaxWidth()) {
                    SectionColumn(
                        totalSections = totalSections,
                        morningSections = maxMorning,
                        afternoonSections = maxAfternoon,
                        eveningSections = maxEvening,
                        sectionTimes = emptyMap(),
                        cardHeightPerSection = cardHeightPerSection
                    )

                    dayRange.forEach { dayOfWeek ->
                        ShiftDayColumn(
                            dayOfWeek = dayOfWeek,
                            allScheduleCourses = shiftScheduleCourses,
                            scheduleColors = scheduleColors,
                            morningSections = maxMorning,
                            afternoonSections = maxAfternoon,
                            eveningSections = maxEvening,
                            currentWeek = week,
                            onSlotClick = { _, _, courses ->
                                hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                                detailCourses = courses
                                showDetail = true
                            },
                            cardHeightPerSection = cardHeightPerSection,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                val morningHeight = (maxMorning * cardHeightPerSection).toInt()
                val afternoonHeight = (maxAfternoon * cardHeightPerSection).toInt()
                val dinnerBreakY = morningHeight + 24 + afternoonHeight

                Box(
                    modifier = Modifier.fillMaxWidth().offset(y = morningHeight.dp).height(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "午休",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth().offset(y = dinnerBreakY.dp).height(24.dp),
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
    }

    OverlayBottomSheet(
        show = showDetail,
        title = "课表详情",
        backgroundColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF7F7F7),
        cornerRadius = 36.dp,
        insideMargin = DpSize(0.dp, 0.dp),
        onDismissRequest = { showDetail = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(
                    hapticFeedbackType = HapticFeedbackType.TextHandleMove
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            detailCourses.forEach { (scheduleName, course) ->
                val textColor = ShiftBlue.copy(alpha = if (isDark) 0.9f else 0.85f)

                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    insideMargin = PaddingValues(0.dp),
                    pressFeedbackType = PressFeedbackType.None,
                    showIndication = false,
                    colors = CardDefaults.defaultColors(
                        color = if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF),
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
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${course.name}  第${course.startSection}-${course.endSection}节",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}
