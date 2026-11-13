package com.haooz.chedule.ui.activities.classschedule

import com.haooz.chedule.i18n.SchedStr

internal data class CourseCardBadge(
    val text: String,
    val usesWorkSwapStyle: Boolean,
)

/** 课程卡右上角的状态角标；假期的优先级高于调休。 */
internal fun resolveCourseCardBadge(
    isHoliday: Boolean,
    isCourseCancelled: Boolean,
    isWorkSwap: Boolean,
    isCurrentWeek: Boolean,
): CourseCardBadge? = when {
    isHoliday -> CourseCardBadge(SchedStr.badgeHoliday(), isWorkSwap && isCurrentWeek)
    isCourseCancelled -> CourseCardBadge(SchedStr.badgeHoliday(), usesWorkSwapStyle = false)
    isWorkSwap -> CourseCardBadge(SchedStr.badgeAdjusted(), isCurrentWeek)
    else -> null
}
