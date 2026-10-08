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
        teachingWeekReorganizations: List<TeachingWeekReorganizationRule> = emptyList(),
    ): Bounds? {
        val lastAllowedWeek = minOf(totalWeeks, lastWeekWithCourses)
        if (lastAllowedWeek <= 0) return null

        val rules = teachingWeekReorganizations.takeIf {
            TeachingWeekReorganization.validationError(it, Int.MAX_VALUE) == null
        }.orEmpty()
        val semesterStartMonday = runCatching {
            semesterStartDate.minusDays((semesterStartDate.dayOfWeek.value - 1).toLong())
        }.getOrNull() ?: return null
        val todayPosition = TeachingWeekReorganization.mapDate(semesterStartDate, today, rules)
        val weekOffset = todayPosition.week - currentWeek.toLong()
        val regularCoursePatterns = mutableListOf<CourseDatePattern>()
        val reorganizationCourseDates = mutableListOf<LocalDate>()
        courses.forEach { course ->
            if (course.selectedWeeks.isNotEmpty() || course.dayOfWeek !in 1..7) return@forEach
            val weeks = activeWeekRange(course, lastAllowedWeek) ?: return@forEach
            if (rules.isEmpty()) {
                courseDatePattern(course, weeks, weekOffset, semesterStartMonday)
                    ?.let(regularCoursePatterns::add)
            } else {
                val reorganized = courseDatesAcrossReorganizations(
                    course = course,
                    activeWeeks = weeks,
                    weekOffset = weekOffset,
                    semesterStartMonday = semesterStartMonday,
                    rules = rules,
                )
                regularCoursePatterns += reorganized.patterns
                reorganizationCourseDates += reorganized.specialDates
            }
        }

        val selectedCourseDates = courses.asSequence()
            .filter { it.selectedWeeks.isNotEmpty() && it.dayOfWeek in 1..7 }
            .flatMap { course ->
                course.selectedWeeks.asSequence()
                    .filter { it in 1..lastAllowedWeek && course.isActiveInWeek(it) }
                    .mapNotNull { week ->
                        TeachingWeekReorganization.dateForPosition(
                            semesterStartDate = semesterStartDate,
                            teachingWeek = week.toLong() + weekOffset,
                            weekday = course.dayOfWeek,
                            rules = rules,
                        )
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
                val datePosition = TeachingWeekReorganization.mapDate(semesterStartDate, firstDate, rules)
                // 跟随的是绝对日期，周次按当前课表现算；老数据没有 followDate 才回退到存的周次
                val followDate = entry.followLocalDate()
                val followPosition = followDate?.let {
                    TeachingWeekReorganization.mapDate(semesterStartDate, it, rules)
                }
                val explicitPauseMapping = followDate != null ||
                    (entry.followWeek > 0 && entry.followWeekday in 1..7)
                if (datePosition.isReorganizationPause && !explicitPauseMapping) return@mapNotNull null
                val displayWeek = when {
                    followPosition != null -> followPosition.week
                    entry.followWeek > 0 -> entry.followWeek.toLong()
                    else -> currentWeek.toLong() + datePosition.week - todayPosition.week
                }
                if (displayWeek !in 1L..lastAllowedWeek.toLong()) return@mapNotNull null
                val legacyDay = entry.followWeekday.takeIf { it in 1..7 }
                val displayDay = when {
                    followPosition?.weekday != null -> followPosition.weekday!!
                    legacyDay != null -> legacyDay
                    followDate != null -> followDate.dayOfWeek.value
                    else -> datePosition.weekday ?: firstDate.dayOfWeek.value
                }
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

        val additionalCourseDates = (selectedCourseDates + workSwapDates + reorganizationCourseDates).distinct()
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

    private data class ReorganizedCourseDates(
        val patterns: List<CourseDatePattern>,
        val specialDates: List<LocalDate>,
    )

    /** Split regular 7/14-day patterns at merged teaching weeks; those weeks are exact candidates. */
    private fun courseDatesAcrossReorganizations(
        course: Course,
        activeWeeks: IntRange,
        weekOffset: Long,
        semesterStartMonday: LocalDate,
        rules: List<TeachingWeekReorganizationRule>,
    ): ReorganizedCourseDates {
        val stepWeeks = if (
            course.weekType == Course.WEEK_TYPE_ODD || course.weekType == Course.WEEK_TYPE_EVEN
        ) 2L else 1L
        val firstEffectiveWeek = activeWeeks.first.toLong() + weekOffset
        val lastEffectiveWeek = activeWeeks.last.toLong() + weekOffset
        val specialWeeks = TeachingWeekReorganization.teachingWeeksForRules(rules)
            .filter { it in firstEffectiveWeek..lastEffectiveWeek }
        val patterns = mutableListOf<CourseDatePattern>()
        val specialDates = mutableListOf<LocalDate>()
        var segmentStart = firstEffectiveWeek
        for (specialWeek in specialWeeks) {
            if (specialWeek > segmentStart) {
                normalPatternForEffectiveRange(
                    course, activeWeeks, segmentStart, specialWeek - 1L, weekOffset,
                    stepWeeks, semesterStartMonday, rules,
                )?.let(patterns::add)
            }
            val sourceWeek = specialWeek - weekOffset
            if (sourceWeek in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() &&
                course.isActiveInWeek(sourceWeek.toInt())
            ) {
                TeachingWeekReorganization.dateForPosition(
                    semesterStartDate = semesterStartMonday,
                    teachingWeek = specialWeek,
                    weekday = course.dayOfWeek,
                    rules = rules,
                )?.let(specialDates::add)
            }
            segmentStart = specialWeek + 1L
        }
        if (segmentStart <= lastEffectiveWeek) {
            normalPatternForEffectiveRange(
                course, activeWeeks, segmentStart, lastEffectiveWeek, weekOffset,
                stepWeeks, semesterStartMonday, rules,
            )?.let(patterns::add)
        }
        return ReorganizedCourseDates(patterns, specialDates)
    }

    private fun normalPatternForEffectiveRange(
        course: Course,
        activeWeeks: IntRange,
        effectiveStart: Long,
        effectiveEnd: Long,
        weekOffset: Long,
        stepWeeks: Long,
        semesterStartMonday: LocalDate,
        rules: List<TeachingWeekReorganizationRule>,
    ): CourseDatePattern? {
        if (effectiveEnd < effectiveStart) return null
        val sourceStart = effectiveStart - weekOffset
        val sourceEnd = effectiveEnd - weekOffset
        val firstIndex = ceilDiv(sourceStart - activeWeeks.first.toLong(), stepWeeks).coerceAtLeast(0L)
        val occurrenceCount = (activeWeeks.last.toLong() - activeWeeks.first.toLong()) / stepWeeks
        val lastIndex = (sourceEnd - activeWeeks.first.toLong()).floorDiv(stepWeeks)
            .coerceAtMost(occurrenceCount)
        if (firstIndex > lastIndex) return null

        val firstEffective = activeWeeks.first.toLong() + firstIndex * stepWeeks + weekOffset
        val lastEffective = activeWeeks.first.toLong() + lastIndex * stepWeeks + weekOffset
        val firstCalendarWeek = TeachingWeekReorganization
            .originalWeekForTeachingWeek(firstEffective, rules) ?: return null
        if (TeachingWeekReorganization.originalWeekForTeachingWeek(lastEffective, rules) == null) return null
        val firstEpochDay = semesterStartMonday.toEpochDay() +
            (firstCalendarWeek - 1L) * 7L + (course.dayOfWeek - 1).toLong()
        val stepDays = stepWeeks * 7L
        val patternOccurrenceCount = (lastEffective - firstEffective) / stepWeeks
        val lastEpochDay = firstEpochDay + patternOccurrenceCount * stepDays
        val minEpochDay = LocalDate.MIN.toEpochDay()
        val maxEpochDay = LocalDate.MAX.toEpochDay()
        val firstOccurrence = if (firstEpochDay < minEpochDay) {
            ceilDivPositive(minEpochDay - firstEpochDay, stepDays)
        } else 0L
        val lastOccurrence = if (lastEpochDay > maxEpochDay) {
            (maxEpochDay - firstEpochDay).floorDiv(stepDays)
        } else patternOccurrenceCount
        if (firstOccurrence > lastOccurrence) return null

        return CourseDatePattern(
            firstDate = LocalDate.ofEpochDay(firstEpochDay + firstOccurrence * stepDays),
            lastDate = LocalDate.ofEpochDay(firstEpochDay + lastOccurrence * stepDays),
            stepDays = stepDays,
        )
    }

    private fun ceilDiv(value: Long, divisor: Long): Long = -Math.floorDiv(-value, divisor)

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
