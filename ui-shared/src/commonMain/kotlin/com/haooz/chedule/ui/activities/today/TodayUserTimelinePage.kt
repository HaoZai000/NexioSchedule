package com.haooz.chedule.ui.activities.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.i18n.TodayStr
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.topappbar.SharedScrollBehavior
import kotlinx.datetime.LocalDate

/**
 * 「时间轴视图2」页面：本 fork 自研的单日时间轴（缩放 / 全天日程 / 系统日历合并）。
 *
 * 与上游的时间轴视图1（[TodayTimelineSection]）并列，由今日页视图选择器切换。
 */
@Composable
internal fun TodayUserTimelinePage(
    date: LocalDate,
    isToday: Boolean,
    courses: List<Course>,
    allCourses: List<Course>,
    hiddenCourseIds: Set<String>,
    sectionTimes: Map<Int, String>,
    pageWeek: Int,
    isTablet: Boolean,
    showClassroom: Boolean,
    showTeacher: Boolean,
    surfaceOpacity: Float,
    scrollBehavior: SharedScrollBehavior?,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = rememberSystemCalendarController(date = date, refreshKey = 0)
    val snapshot = controller.snapshot
    val allDayEvents = remember(snapshot) {
        snapshot.events.filter { it.allDay || (it.startMinute <= 0 && it.endMinute >= 24 * 60) }
    }
    val timedEvents = remember(snapshot, allDayEvents) {
        val allDayIds = allDayEvents.mapTo(HashSet()) { it.id }
        snapshot.events.filterNot { it.id in allDayIds }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (snapshot.permission == SystemCalendarPermission.DENIED) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { controller.requestPermission() }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = TodayStr.calendarPermissionTitle(),
                        fontSize = 15.sp,
                        color = NexioTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = TodayStr.calendarPermissionSummary(),
                        fontSize = 12.sp,
                        color = NexioTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Text(
                    text = TodayStr.calendarPermissionAction(),
                    fontSize = 14.sp,
                    color = NexioTheme.colorScheme.primary,
                )
            }
        } else if (snapshot.loadFailed) {
            Text(
                text = TodayStr.calendarLoadFailed(),
                fontSize = 13.sp,
                color = NexioTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        if (allDayEvents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            AllDayEventsStrip(
                events = allDayEvents,
                surfaceOpacity = surfaceOpacity,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        TodayUserTimelineSection(
            date = date,
            isToday = isToday,
            courses = courses,
            allCourses = allCourses,
            hiddenCourseIds = hiddenCourseIds,
            sectionTimes = sectionTimes,
            systemEvents = timedEvents,
            contentReady = true,
            pageWeek = pageWeek,
            isTablet = isTablet,
            showClassroom = showClassroom,
            showTeacher = showTeacher,
            cardSurfaceAlpha = surfaceOpacity,
            scrollBehavior = scrollBehavior,
            onScrollYChanged = {},
            onListScrollInProgress = {},
            hasAllDayEvents = allDayEvents.isNotEmpty(),
            onHorizontalOverflowChanged = { _, _ -> },
            onCourseClick = { courseList, _, _, _, _, _, _, _ ->
                courseList.firstOrNull()?.let(onCourseClick)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
