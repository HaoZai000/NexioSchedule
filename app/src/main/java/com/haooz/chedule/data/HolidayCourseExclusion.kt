package com.haooz.chedule.data

import java.time.LocalDate
import java.time.LocalTime

data class HolidayEndCourseExclusion(
    val enabled: Boolean = false,
    val startSection: Int = 1,
    val endSection: Int = 1,
) {
    fun isValid(): Boolean = startSection > 0 && endSection >= startSection
}

data class HolidayDayCourseResolution(
    val courses: List<Course>,
    val isHolidayDate: Boolean,
    val isHolidayEndCourseExclusionActive: Boolean,
)

/** Pure rules for allowing selected courses on the final date of a holiday interval. */
object HolidayCourseExclusion {
    fun resolveDayCourses(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
        exclusion: HolidayEndCourseExclusion,
        candidates: () -> List<Course>,
        sectionTimes: () -> Map<Int, String>,
        sectionCount: () -> Int,
    ): HolidayDayCourseResolution {
        val isHolidayDate = HolidayManager.entriesForDate(entriesByYear, date)
            .any { it.type == HolidayManager.TYPE_HOLIDAY }
        val isExclusionActive = isHolidayDate && isEnabledOnDate(entriesByYear, date, exclusion)
        if (isHolidayDate && !isExclusionActive) {
            return HolidayDayCourseResolution(
                courses = emptyList(),
                isHolidayDate = true,
                isHolidayEndCourseExclusionActive = false,
            )
        }

        val dayCandidates = candidates()
        val courses = if (isExclusionActive) {
            filterMatchingCourses(dayCandidates, exclusion, sectionTimes(), sectionCount())
        } else {
            dayCandidates
        }
        return HolidayDayCourseResolution(
            courses = courses,
            isHolidayDate = isHolidayDate,
            isHolidayEndCourseExclusionActive = isExclusionActive,
        )
    }

    fun isEnabledOnDate(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
        exclusion: HolidayEndCourseExclusion,
    ): Boolean = exclusion.enabled && exclusion.isValid() && isLastHolidayDate(entriesByYear, date)

    fun isLastHolidayDate(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
    ): Boolean {
        val isHoliday = HolidayManager.entriesForDate(entriesByYear, date)
            .any { it.type == HolidayManager.TYPE_HOLIDAY }
        if (!isHoliday) return false
        if (date == LocalDate.MAX) return true

        return HolidayManager.entriesForDate(entriesByYear, date.plusDays(1))
            .none { it.type == HolidayManager.TYPE_HOLIDAY }
    }

    fun matchesCourse(
        course: Course,
        exclusion: HolidayEndCourseExclusion,
        sectionTimes: Map<Int, String>,
        sectionCount: Int,
    ): Boolean {
        if (!exclusion.enabled || !exclusion.isValid() || sectionCount <= 0) return false
        val selectedRange = exclusion.startSection..minOf(exclusion.endSection, sectionCount)
        if (selectedRange.isEmpty()) return false

        if (course.isCustomTime) {
            if (!course.hasValidCustomTime()) return false
            val courseStart = parseTime(course.customStartTime) ?: return false
            val courseEnd = parseTime(course.customEndTime) ?: return false
            if (!courseStart.isBefore(courseEnd)) return false

            return selectedRange.any { section ->
                val (sectionStart, sectionEnd) = parseSectionTime(sectionTimes[section]) ?: return@any false
                courseStart.isBefore(sectionEnd) && courseEnd.isAfter(sectionStart)
            }
        }

        if (course.startSection <= 0 || course.endSection < course.startSection) return false
        return course.startSection <= selectedRange.last && course.endSection >= selectedRange.first
    }

    fun filterMatchingCourses(
        candidates: List<Course>,
        exclusion: HolidayEndCourseExclusion,
        sectionTimes: Map<Int, String>,
        sectionCount: Int,
    ): List<Course> = candidates.filter {
        matchesCourse(it, exclusion, sectionTimes, sectionCount)
    }

    private fun parseSectionTime(value: String?): Pair<LocalTime, LocalTime>? {
        val parts = value?.split('-') ?: return null
        if (parts.size != 2) return null
        val start = parseTime(parts[0]) ?: return null
        val end = parseTime(parts[1]) ?: return null
        return (start to end).takeIf { start.isBefore(end) }
    }

    private fun parseTime(value: String?): LocalTime? {
        val parts = value?.trim()?.split(':') ?: return null
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }
}
