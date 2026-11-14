package com.haooz.chedule.ui.activities.today

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

@Composable
actual fun rememberSystemCalendarController(
    date: LocalDate,
    refreshKey: Int,
): SystemCalendarController = SystemCalendarController(
    snapshot = SystemCalendarSnapshot(permission = SystemCalendarPermission.UNSUPPORTED),
    requestPermission = {},
)
