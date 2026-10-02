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

data class HolidayBeforeCourseExclusion(
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
    val isHolidayBeforeCourseExclusionActive: Boolean = false,
)

data class HolidayCourseDisplaySelection(
    val representative: Course?,
    val hidden: List<Course>,
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
        beforeExclusion: HolidayBeforeCourseExclusion = HolidayBeforeCourseExclusion(),
    ): HolidayDayCourseResolution {
        val isHolidayDate = HolidayManager.entriesForDate(entriesByYear, date)
            .any { it.type == HolidayManager.TYPE_HOLIDAY }
        val isExclusionActive = isHolidayDate && isEnabledOnDate(entriesByYear, date, exclusion)
        val isBeforeExclusionActive = !isHolidayDate &&
            isEnabledBeforeHolidayDate(entriesByYear, date, beforeExclusion)
        if (isHolidayDate && !isExclusionActive) {
            return HolidayDayCourseResolution(
                courses = emptyList(),
                isHolidayDate = true,
                isHolidayEndCourseExclusionActive = false,
                isHolidayBeforeCourseExclusionActive = false,
            )
        }

        val dayCandidates = candidates()
        val courses = when {
            isExclusionActive -> filterMatchingCourses(
                dayCandidates,
                exclusion,
                sectionTimes(),
                sectionCount(),
            )
            isBeforeExclusionActive -> filterExcludedCourses(
                dayCandidates,
                beforeExclusion,
                sectionTimes(),
                sectionCount(),
            )
            else -> dayCandidates
        }
        return HolidayDayCourseResolution(
            courses = courses,
            isHolidayDate = isHolidayDate,
            isHolidayEndCourseExclusionActive = isExclusionActive,
            isHolidayBeforeCourseExclusionActive = isBeforeExclusionActive,
        )
    }

    fun isBeforeHolidayDate(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
    ): Boolean {
        if (HolidayManager.entriesForDate(entriesByYear, date)
                .any { it.type == HolidayManager.TYPE_HOLIDAY } || date == LocalDate.MAX
        ) return false

        return HolidayManager.entriesForDate(entriesByYear, date.plusDays(1))
            .any { it.type == HolidayManager.TYPE_HOLIDAY }
    }

    fun isEnabledBeforeHolidayDate(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
        exclusion: HolidayBeforeCourseExclusion,
    ): Boolean = exclusion.enabled && exclusion.isValid() && isBeforeHolidayDate(entriesByYear, date)

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
        return matchesCourseInRange(
            course,
            exclusion.enabled,
            exclusion.startSection,
            exclusion.endSection,
            sectionTimes,
            sectionCount,
        )
    }

    fun matchesCourse(
        course: Course,
        exclusion: HolidayBeforeCourseExclusion,
        sectionTimes: Map<Int, String>,
        sectionCount: Int,
    ): Boolean = matchesCourseInRange(
        course,
        exclusion.enabled,
        exclusion.startSection,
        exclusion.endSection,
        sectionTimes,
        sectionCount,
    )

    private fun matchesCourseInRange(
        course: Course,
        enabled: Boolean,
        startSection: Int,
        endSection: Int,
        sectionTimes: Map<Int, String>,
        sectionCount: Int,
    ): Boolean {
        if (!enabled || startSection <= 0 || endSection < startSection || sectionCount <= 0) return false
        val selectedRange = startSection..minOf(endSection, sectionCount)
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

    fun filterExcludedCourses(
        candidates: List<Course>,
        exclusion: HolidayBeforeCourseExclusion,
        sectionTimes: Map<Int, String>,
        sectionCount: Int,
    ): List<Course> = candidates.filterNot {
        matchesCourse(it, exclusion, sectionTimes, sectionCount)
    }

    fun cancelledCourseIdsOnDate(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
        exclusion: HolidayBeforeCourseExclusion,
        candidates: List<Course>,
        displayWeek: Int,
        sectionTimes: Map<Int, String>,
        sectionCount: Int,
    ): Set<String> {
        if (!isEnabledBeforeHolidayDate(entriesByYear, date, exclusion)) return emptySet()
        return candidates.asSequence()
            .filter { it.isActiveInWeek(displayWeek) }
            .filter { matchesCourse(it, exclusion, sectionTimes, sectionCount) }
            .map { it.id }
            .toSet()
    }

    fun selectDisplayCourses(
        currentWeekCourses: List<Course>,
        cancelledCourseIds: Set<String>,
    ): HolidayCourseDisplaySelection {
        val representativeIndex = currentWeekCourses.indexOfFirst {
            it.id !in cancelledCourseIds
        }.takeIf { it >= 0 } ?: currentWeekCourses.indices.firstOrNull()
            ?: return HolidayCourseDisplaySelection(null, emptyList())
        return HolidayCourseDisplaySelection(
            representative = currentWeekCourses[representativeIndex],
            hidden = currentWeekCourses.filterIndexed { index, _ -> index != representativeIndex },
        )
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
