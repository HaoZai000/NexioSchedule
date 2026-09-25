package com.haooz.chedule.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Pure date/time rules for the holiday countdown shown on the Today page. */
object HolidayCountdown {
    fun millisUntilNextMinute(epochMillis: Long): Long =
        60_000L - Math.floorMod(epochMillis, 60_000L)

    data class HolidayPeriod(
        val startDate: LocalDate,
        val endDate: LocalDate,
    )

    fun holidayPeriodsFromStoredEntries(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
    ): List<HolidayPeriod> = entriesByYear.flatMap { (storageYear, entries) ->
        entries.asSequence()
            .filter { it.type == HolidayManager.TYPE_HOLIDAY }
            .mapNotNull { entry ->
                val start = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                    ?: return@mapNotNull null
                val end = runCatching { LocalDate.parse(entry.endDate.ifBlank { entry.date }) }
                    .getOrNull() ?: return@mapNotNull null
                if (end.isBefore(start) || storageYear > end.year) return@mapNotNull null
                val firstEligibleDate = if (storageYear > start.year) {
                    LocalDate.of(storageYear, 1, 1)
                } else {
                    start
                }
                if (firstEligibleDate.isAfter(end)) null
                else HolidayPeriod(firstEligibleDate, end)
            }
            .toList()
    }

    sealed interface Snapshot {
        data class BeforeHoliday(val startsAt: LocalDateTime) : Snapshot
        data class DuringHoliday(val returnDate: LocalDate) : Snapshot
    }

    /**
     * Builds a date-stable snapshot. The callback supplies the end time of the latest effective
     * class on a date, or null when that date has no effective class.
     */
    fun createSnapshot(
        today: LocalDate,
        holidays: List<HolidayPeriod>,
        lastClassEndAt: (LocalDate) -> LocalTime?,
        earliestPossibleCourseDate: LocalDate? = null,
        latestPossibleCourseDate: LocalDate? = null,
        additionalCourseDates: Collection<LocalDate> = emptyList(),
        regularCoursePatterns: List<CourseScheduleDateBounds.CourseDatePattern>? = null,
    ): Snapshot? {
        val validHolidays = mergeHolidayPeriods(holidays)

        validHolidays.firstOrNull { holiday ->
            !today.isBefore(holiday.startDate) && !today.isAfter(holiday.endDate)
        }?.let { return Snapshot.DuringHoliday(it.endDate) }

        val nextHoliday = validHolidays.firstOrNull { it.startDate.isAfter(today) } ?: return null
        var additionalCandidate: Snapshot.BeforeHoliday? = null
        for (date in additionalCourseDates.asSequence()
                .filter { it.isBefore(nextHoliday.startDate) }
                .distinct()
                .sortedDescending()) {
            if (validHolidays.any { !date.isBefore(it.startDate) && !date.isAfter(it.endDate) }) {
                continue
            }
            val endTime = lastClassEndAt(date) ?: continue
            additionalCandidate = Snapshot.BeforeHoliday(date.atTime(endTime))
            if (latestPossibleCourseDate != null && date.isAfter(latestPossibleCourseDate)) {
                return additionalCandidate
            }
            break
        }

        if (regularCoursePatterns != null && regularCoursePatterns.isNotEmpty()) {
            var searchLimit = minOf(
                nextHoliday.startDate.minusDays(1),
                latestPossibleCourseDate ?: nextHoliday.startDate.minusDays(1),
            )
            val earliestPatternDate = regularCoursePatterns.minOf { it.firstDate }
            val searchStart = earliestPossibleCourseDate?.let { minOf(it, earliestPatternDate) }
                ?: earliestPatternDate
            while (!searchLimit.isBefore(searchStart)) {
                val candidateDate = regularCoursePatterns.asSequence()
                    .mapNotNull { latestPatternDateOnOrBefore(it, searchLimit) }
                    .maxOrNull() ?: break
                if (additionalCandidate != null &&
                    !candidateDate.isAfter(additionalCandidate.startsAt.toLocalDate())
                ) break

                val holiday = validHolidays.firstOrNull {
                    !candidateDate.isBefore(it.startDate) && !candidateDate.isAfter(it.endDate)
                }
                if (holiday != null) {
                    if (holiday.startDate == LocalDate.MIN) break
                    searchLimit = holiday.startDate.minusDays(1)
                    continue
                }
                lastClassEndAt(candidateDate)?.let { endTime ->
                    return Snapshot.BeforeHoliday(candidateDate.atTime(endTime))
                }
                if (candidateDate == searchStart || candidateDate == LocalDate.MIN) break
                searchLimit = candidateDate.minusDays(1)
            }
        } else if (regularCoursePatterns == null &&
            earliestPossibleCourseDate != null && latestPossibleCourseDate != null
        ) {
            var searchDate = minOf(nextHoliday.startDate.minusDays(1), latestPossibleCourseDate)
            while (!searchDate.isBefore(earliestPossibleCourseDate) &&
                (additionalCandidate == null || searchDate.isAfter(additionalCandidate.startsAt.toLocalDate()))
            ) {
                val holiday = validHolidays.firstOrNull {
                    !searchDate.isBefore(it.startDate) && !searchDate.isAfter(it.endDate)
                }
                if (holiday != null) {
                    if (holiday.startDate == LocalDate.MIN ||
                        holiday.startDate.isBefore(earliestPossibleCourseDate)
                    ) break
                    searchDate = holiday.startDate.minusDays(1)
                    continue
                }
                lastClassEndAt(searchDate)?.let { endTime ->
                    return Snapshot.BeforeHoliday(searchDate.atTime(endTime))
                }
                if (searchDate == earliestPossibleCourseDate) break
                searchDate = searchDate.minusDays(1)
            }
        }

        return additionalCandidate ?: Snapshot.BeforeHoliday(nextHoliday.startDate.atStartOfDay())
    }

    fun createSnapshotWithCourseBoundsResult(
        today: LocalDate,
        holidays: List<HolidayPeriod>,
        courseDateBounds: Result<CourseScheduleDateBounds.Bounds?>,
        lastClassEndAt: (LocalDate) -> LocalTime?,
    ): Snapshot? = courseDateBounds.fold(
        onSuccess = { bounds ->
            createSnapshot(
                today = today,
                holidays = holidays,
                lastClassEndAt = lastClassEndAt,
                earliestPossibleCourseDate = bounds?.firstDate,
                latestPossibleCourseDate = bounds?.lastDate,
                additionalCourseDates = bounds?.additionalCourseDates.orEmpty(),
                regularCoursePatterns = bounds?.regularCoursePatterns.orEmpty(),
            )
        },
        onFailure = { null },
    )

    private fun mergeHolidayPeriods(holidays: List<HolidayPeriod>): List<HolidayPeriod> {
        val merged = mutableListOf<HolidayPeriod>()
        holidays.filter { !it.endDate.isBefore(it.startDate) }
            .sortedBy { it.startDate }
            .forEach { holiday ->
                val previous = merged.lastOrNull()
                if (previous == null ||
                    ChronoUnit.DAYS.between(previous.endDate, holiday.startDate) > 1L
                ) {
                    merged += holiday
                } else {
                    merged[merged.lastIndex] = previous.copy(
                        endDate = maxOf(previous.endDate, holiday.endDate)
                    )
                }
            }
        return merged
    }

    private fun latestPatternDateOnOrBefore(
        pattern: CourseScheduleDateBounds.CourseDatePattern,
        limit: LocalDate,
    ): LocalDate? {
        if (pattern.stepDays <= 0L || limit.isBefore(pattern.firstDate)) return null
        val boundedLimit = minOf(limit, pattern.lastDate)
        if (boundedLimit.isBefore(pattern.firstDate)) return null
        val intervals = ChronoUnit.DAYS.between(pattern.firstDate, boundedLimit) / pattern.stepDays
        return runCatching { pattern.firstDate.plusDays(intervals * pattern.stepDays) }.getOrNull()
    }

    fun message(snapshot: Snapshot, now: LocalDateTime): String = when (snapshot) {
        is Snapshot.DuringHoliday -> {
            val daysUntilReturn = ChronoUnit.DAYS.between(now.toLocalDate(), snapshot.returnDate)
            if (daysUntilReturn <= 0) "怎么今天就返校了……"
            else "还有 $daysUntilReturn 天返校"
        }

        is Snapshot.BeforeHoliday -> {
            val remaining = Duration.between(now, snapshot.startsAt)
            if (remaining.isNegative || remaining.isZero) {
                "恭喜你放假啦！"
            } else {
                formatRemaining(remaining)
            }
        }
    }

    private fun formatRemaining(remaining: Duration): String = when {
        remaining.toDays() >= 1 -> "还有 ${remaining.toDays()} 天放假"
        remaining.toHours() >= 1 -> "还有 ${remaining.toHours()} 小时放假"
        else -> "还有 ${remaining.toMinutes().coerceAtLeast(1)} 分钟放假"
    }
}
