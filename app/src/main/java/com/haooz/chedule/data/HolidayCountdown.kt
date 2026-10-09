package com.haooz.chedule.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

// ── 节假日与调休 · 假期倒计时（今日页）──────────────────
//
// 拆分自 `Holidays.kt`（拆分说明见 `HolidayManager.kt` 的文件头）。
// 只依赖纯日期类型；假期数据由调用方查 `HolidayManager` 后传入。

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
            if (validHolidays.any {
                !date.isBefore(it.startDate) && !date.isAfter(it.endDate) && date != it.endDate
            }) {
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
                    if (candidateDate == holiday.endDate) {
                        lastClassEndAt(candidateDate)?.let { endTime ->
                            return Snapshot.BeforeHoliday(candidateDate.atTime(endTime))
                        }
                    }
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
                    if (searchDate == holiday.endDate) {
                        lastClassEndAt(searchDate)?.let { endTime ->
                            return Snapshot.BeforeHoliday(searchDate.atTime(endTime))
                        }
                    }
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

    /** 一段连续假期的概览：[startDate, endDate] 闭区间，[name] 取该段首日的假期名 */
    data class HolidayBlock(
        val name: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
    )

    /**
     * 目标日所在的那一段连续假期；目标日不是假期时返回 null。
     *
     * 区间口径与今日页假期倒计时共用同一份 [mergeHolidayPeriods]：相邻两段记录
     * （如中秋 + 国庆）算同一段假期，中间只隔一个非假日也算同一段。
     * 提醒文案必须跟随这里，否则会出现「今日页显示放假 8 天、明日提醒却说 1 天」的分裂。
     */
    fun holidayBlockAt(
        entriesByYear: Map<Int, List<HolidayManager.Entry>>,
        date: LocalDate,
    ): HolidayBlock? {
        val period = mergeHolidayPeriods(holidayPeriodsFromStoredEntries(entriesByYear))
            .firstOrNull { !date.isBefore(it.startDate) && !date.isAfter(it.endDate) }
            ?: return null
        val name = HolidayManager.entriesForDate(entriesByYear, period.startDate)
            .firstOrNull { it.type == HolidayManager.TYPE_HOLIDAY }?.name.orEmpty()
        return HolidayBlock(name = name, startDate = period.startDate, endDate = period.endDate)
    }

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
