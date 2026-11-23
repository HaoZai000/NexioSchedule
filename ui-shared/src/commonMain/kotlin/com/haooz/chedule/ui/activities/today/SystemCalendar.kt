package com.haooz.chedule.ui.activities.today

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

/** 系统日历中的一条日程。时间已经换算到当前浏览日，单位为当日分钟。 */
data class SystemCalendarEvent(
    val id: String,
    val title: String,
    val location: String,
    val calendarName: String,
    val startMinute: Int,
    val endMinute: Int,
    val color: Long,
    val allDay: Boolean,
)

enum class SystemCalendarPermission {
    GRANTED,
    DENIED,
    UNSUPPORTED,
}

data class SystemCalendarSnapshot(
    val events: List<SystemCalendarEvent> = emptyList(),
    val permission: SystemCalendarPermission = SystemCalendarPermission.UNSUPPORTED,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
)

class SystemCalendarController(
    val snapshot: SystemCalendarSnapshot,
    val requestPermission: () -> Unit,
)

/**
 * 读取选中日期的系统日历，并监听日历变更。
 * Android 使用 CalendarContract；其他平台保留课程时间轴，不显示权限引导。
 */
@Composable
expect fun rememberSystemCalendarController(
    date: LocalDate,
    refreshKey: Int,
): SystemCalendarController
