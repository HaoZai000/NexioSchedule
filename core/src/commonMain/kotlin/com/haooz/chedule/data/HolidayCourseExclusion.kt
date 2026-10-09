package com.haooz.chedule.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

// ── 节假日与调休 · 假期课程剔除与逐日裁决 ────────────────
//
// 拆分自 `Holidays.kt`（拆分说明见 `HolidayManager.kt` 的文件头）。
// 只依赖纯日期类型；假期数据由调用方查 `HolidayManager` 后传入。

/**
 * Pure rules for allowing selected courses on the final date of a holiday interval.
 *
 * 配套的 4 个数据结构（`HolidayEndCourseExclusion` / `HolidayBeforeCourseExclusion` /
 * `HolidayDayCourseResolution` / `HolidayCourseDisplaySelection`）已下沉 `:core`
 * （`core/.../data/HolidayTypes.kt`）—— 它们只由 Int / Boolean / [Course] 构成，
 * 不需要等本文件处理完 `java.time` 才能搬。
 */
object HolidayCourseExclusion {
    fun resolveDayCourses(
        entriesByYear: Map<Int, List<HolidayEntry>>,
        date: LocalDate,
        exclusion: HolidayEndCourseExclusion,
        candidates: () -> List<Course>,
        sectionTimes: () -> Map<Int, String>,
        sectionCount: () -> Int,
        beforeExclusion: HolidayBeforeCourseExclusion = HolidayBeforeCourseExclusion(),
    ): HolidayDayCourseResolution {
        val isHolidayDate = HolidayEntries.entriesForDate(entriesByYear, date)
            .any { it.type == HolidayEntry.TYPE_HOLIDAY }
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

    /**
 * 该日期是否为「假期的前一天」。
 *
 * 判定只看「后一天是否假期」，**刻意不看当天是否为调休上班日**：用户开启「假期前日课程
 * 排除」后，规则在假期前一天硬性生效，当天即使是调休上班日（TYPE_WORKSWAP + 配了
 * followWeekday）也一样停课。课表/今日/提醒/桌面组件/ICS 导出全部按这一条口径。
 * 不要因为「调休上班日本来就要上课」就擅自加排除 —— 那会与调休配置互相打架，
 * 是有意为之的取舍。
 */
fun isBeforeHolidayDate(
        entriesByYear: Map<Int, List<HolidayEntry>>,
        date: LocalDate,
    ): Boolean {
        if (HolidayEntries.entriesForDate(entriesByYear, date)
                .any { it.type == HolidayEntry.TYPE_HOLIDAY } || date == LOCAL_DATE_MAX
        ) return false

        return HolidayEntries.entriesForDate(entriesByYear, date.plusDays(1))
            .any { it.type == HolidayEntry.TYPE_HOLIDAY }
    }

    fun isEnabledBeforeHolidayDate(
        entriesByYear: Map<Int, List<HolidayEntry>>,
        date: LocalDate,
        exclusion: HolidayBeforeCourseExclusion,
    ): Boolean = exclusion.enabled && exclusion.isValid() && isBeforeHolidayDate(entriesByYear, date)

    fun isEnabledOnDate(
        entriesByYear: Map<Int, List<HolidayEntry>>,
        date: LocalDate,
        exclusion: HolidayEndCourseExclusion,
    ): Boolean = exclusion.enabled && exclusion.isValid() && isLastHolidayDate(entriesByYear, date)

    fun isLastHolidayDate(
        entriesByYear: Map<Int, List<HolidayEntry>>,
        date: LocalDate,
    ): Boolean {
        val isHoliday = HolidayEntries.entriesForDate(entriesByYear, date)
            .any { it.type == HolidayEntry.TYPE_HOLIDAY }
        if (!isHoliday) return false
        if (date == LOCAL_DATE_MAX) return true

        return HolidayEntries.entriesForDate(entriesByYear, date.plusDays(1))
            .none { it.type == HolidayEntry.TYPE_HOLIDAY }
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
            if (courseStart >= courseEnd) return false

            return selectedRange.any { section ->
                val (sectionStart, sectionEnd) = parseSectionTime(sectionTimes[section]) ?: return@any false
                courseStart < sectionEnd && courseEnd > sectionStart
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
        entriesByYear: Map<Int, List<HolidayEntry>>,
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
        return (start to end).takeIf { start < end }
    }

    private fun parseTime(value: String?): LocalTime? {
        val parts = value?.trim()?.split(':') ?: return null
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return runCatching { LocalTime(hour, minute) }.getOrNull()
    }
}
