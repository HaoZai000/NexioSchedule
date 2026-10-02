package com.haooz.chedule.ui.components

internal data class CourseCardBadge(
    val text: String,
    val usesWorkSwapStyle: Boolean,
)

internal fun resolveCourseCardBadge(
    isHoliday: Boolean,
    isCourseCancelled: Boolean,
    isWorkSwap: Boolean,
    isCurrentWeek: Boolean,
): CourseCardBadge? = when {
    isHoliday -> CourseCardBadge("假", isWorkSwap && isCurrentWeek)
    isCourseCancelled -> CourseCardBadge("假", usesWorkSwapStyle = false)
    isWorkSwap -> CourseCardBadge("调", isCurrentWeek)
    else -> null
}
