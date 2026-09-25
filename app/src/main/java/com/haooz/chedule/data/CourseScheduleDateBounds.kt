package com.haooz.chedule.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Date bounds for every week that can contain an effective course in the active schedule. */
object CourseScheduleDateBounds {
    data class CourseDatePattern(
        val firstDate: LocalDate,
        val lastDate: LocalDate,
        val stepDays: Long,
    )

    data class Bounds(
        val firstDate: LocalDate,
        val lastDate: LocalDate,
        val additionalCourseDates: List<LocalDate> = emptyList(),
        val regularCoursePatterns: List<CourseDatePattern> = emptyList(),
    )

    fun calculate(
        today: LocalDate,
        semesterStartDate: LocalDate,
        currentWeek: Int,
        totalWeeks: Int,
        lastWeekWithCourses: Int,
        courses: List<Course>,
        workSwapEntries: List<HolidayManager.Entry>,
    ): Bounds? {
        val lastAllowedWeek = minOf(totalWeeks, lastWeekWithCourses)
        if (lastAllowedWeek <= 0) return null

        val semesterStartMonday = runCatching {
            semesterStartDate.minusDays((semesterStartDate.dayOfWeek.value - 1).toLong())
        }.getOrNull() ?: return null
        val weekOffset = calendarWeekForDate(semesterStartDate, today) - currentWeek.toLong()
        val regularCoursePatterns = courses.mapNotNull { course ->
            if (course.selectedWeeks.isNotEmpty() || course.dayOfWeek !in 1..7) return@mapNotNull null
            val weeks = activeWeekRange(course, lastAllowedWeek) ?: return@mapNotNull null
            courseDatePattern(course, weeks, weekOffset, semesterStartMonday)
        }

        val selectedCourseDates = courses.asSequence()
            .filter { it.selectedWeeks.isNotEmpty() && it.dayOfWeek in 1..7 }
            .flatMap { course ->
                course.selectedWeeks.asSequence()
                    .filter { it in 1..lastAllowedWeek && course.isActiveInWeek(it) }
                    .mapNotNull { week ->
                        val calendarWeek = week.toLong() + weekOffset
                        runCatching {
                            semesterStartMonday.plusWeeks(calendarWeek - 1L)
                                .plusDays((course.dayOfWeek - 1).toLong())
                        }.getOrNull()
                    }
            }
            .toList()

        val workSwapDates = workSwapEntries.asSequence()
            .filter { it.type == HolidayManager.TYPE_WORKSWAP }
            .mapNotNull { entry ->
                val firstDate = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                    ?: return@mapNotNull null
                val lastDate = runCatching {
                    LocalDate.parse(entry.endDate.ifBlank { entry.date })
                }.getOrNull() ?: return@mapNotNull null
                // Work-swap mappings are single-date overrides. Keep them as exact candidates
                // rather than stretching the regular schedule scan over an arbitrary date gap.
                if (lastDate != firstDate) return@mapNotNull null
                val displayWeek = entry.followWeek.takeIf { it > 0 }?.toLong()
                    ?: currentWeek.toLong() + calendarWeekForDate(semesterStartDate, firstDate) -
                        calendarWeekForDate(semesterStartDate, today)
                if (displayWeek !in 1L..lastAllowedWeek.toLong()) return@mapNotNull null
                val displayDay = entry.followWeekday.takeIf { it in 1..7 }
                    ?: firstDate.dayOfWeek.value
                if (courses.any {
                        it.dayOfWeek == displayDay && it.isActiveInWeek(displayWeek.toInt())
                    }
                ) {
                    firstDate
                } else {
                    null
                }
            }
            .distinct()
            .toList()

        val additionalCourseDates = (selectedCourseDates + workSwapDates).distinct()
        val possibleCourseDates = buildList {
            regularCoursePatterns.forEach { pattern ->
                add(pattern.firstDate)
                add(pattern.lastDate)
            }
            addAll(additionalCourseDates)
        }
        if (possibleCourseDates.isEmpty()) return null
        return Bounds(
            firstDate = possibleCourseDates.minOrNull() ?: return null,
            lastDate = possibleCourseDates.maxOrNull() ?: return null,
            additionalCourseDates = additionalCourseDates,
            regularCoursePatterns = regularCoursePatterns,
        )
    }

    private fun courseDatePattern(
        course: Course,
        weeks: IntRange,
        weekOffset: Long,
        semesterStartMonday: LocalDate,
    ): CourseDatePattern? = runCatching {
        val stepWeeks = if (
            course.weekType == Course.WEEK_TYPE_ODD ||
            course.weekType == Course.WEEK_TYPE_EVEN
        ) 2L else 1L
        val stepDays = stepWeeks * 7L
        val firstCalendarWeek = weeks.first.toLong() + weekOffset
        val firstEpochDay = semesterStartMonday.toEpochDay() +
            (firstCalendarWeek - 1L) * 7L + (course.dayOfWeek - 1).toLong()
        val occurrenceCount = (weeks.last.toLong() - weeks.first.toLong()) / stepWeeks
        val lastEpochDay = firstEpochDay + occurrenceCount * stepDays
        val minEpochDay = LocalDate.MIN.toEpochDay()
        val maxEpochDay = LocalDate.MAX.toEpochDay()
        val firstOccurrence = if (firstEpochDay < minEpochDay) {
            ceilDivPositive(minEpochDay - firstEpochDay, stepDays)
        } else {
            0L
        }
        val lastOccurrence = if (lastEpochDay > maxEpochDay) {
            (maxEpochDay - firstEpochDay).floorDiv(stepDays)
        } else {
            occurrenceCount
        }
        if (firstOccurrence > lastOccurrence) return@runCatching null

        CourseDatePattern(
            firstDate = LocalDate.ofEpochDay(firstEpochDay + firstOccurrence * stepDays),
            lastDate = LocalDate.ofEpochDay(firstEpochDay + lastOccurrence * stepDays),
            stepDays = stepDays,
        )
    }.getOrNull()

    private fun ceilDivPositive(value: Long, divisor: Long): Long =
        value / divisor + if (value % divisor == 0L) 0L else 1L

    private fun activeWeekRange(course: Course, maxWeek: Int): IntRange? {
        if (course.selectedWeeks.isNotEmpty()) {
            val selected = course.selectedWeeks.filter { it in 1..maxWeek }
            return selected.minOrNull()?.let { first -> first..selected.max() }
        }

        val firstInRange = maxOf(1, course.startWeek)
        val lastInRange = minOf(maxWeek, course.endWeek)
        if (firstInRange > lastInRange) return null

        val first = when (course.weekType) {
            Course.WEEK_TYPE_ODD -> if (firstInRange % 2 == 1) firstInRange
                else if (firstInRange < lastInRange) firstInRange + 1 else return null
            Course.WEEK_TYPE_EVEN -> if (firstInRange % 2 == 0) firstInRange
                else if (firstInRange < lastInRange) firstInRange + 1 else return null
            else -> firstInRange
        }
        val last = when (course.weekType) {
            Course.WEEK_TYPE_ODD -> if (lastInRange % 2 == 1) lastInRange
                else if (lastInRange > first) lastInRange - 1 else return null
            Course.WEEK_TYPE_EVEN -> if (lastInRange % 2 == 0) lastInRange
                else if (lastInRange > first) lastInRange - 1 else return null
            else -> lastInRange
        }
        return first..last
    }

    /** Calendar week numbering shared with reminder schedule resolution. */
    fun calendarWeekForDate(semesterStartDate: LocalDate, date: LocalDate): Long {
        val daysFromSemesterMonday = ChronoUnit.DAYS.between(semesterStartDate, date) +
            (semesterStartDate.dayOfWeek.value - 1).toLong()
        return daysFromSemesterMonday.floorDiv(7L) + 1L
    }
}
