package com.haooz.chedule.data

import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

// ── 节假日与调休 · 假期倒计时（今日页）──────────────────
//
// 拆分自 `Holidays.kt`（拆分说明见 `HolidayManager.kt` 的文件头）。
// 只依赖纯日期类型；假期数据由调用方查 `HolidayManager` 后传入。

/** Pure date/time rules for the holiday countdown shown on the Today page. */
object HolidayCountdown {
    fun millisUntilNextMinute(epochMillis: Long): Long =
        60_000L - epochMillis.mod(60_000L)

    data class HolidayPeriod(
        val startDate: LocalDate,
        val endDate: LocalDate,
    )

    fun holidayPeriodsFromStoredEntries(
        entriesByYear: Map<Int, List<HolidayEntry>>,
    ): List<HolidayPeriod> = entriesByYear.flatMap { (storageYear, entries) ->
        entries.asSequence()
            .filter { it.type == HolidayEntry.TYPE_HOLIDAY }
            .mapNotNull { entry ->
                val start = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                    ?: return@mapNotNull null
                val end = runCatching { LocalDate.parse(entry.endDate.ifBlank { entry.date }) }
                    .getOrNull() ?: return@mapNotNull null
                if (end < start || storageYear > end.year) return@mapNotNull null
                val firstEligibleDate = if (storageYear > start.year) {
                    LocalDate(storageYear, 1, 1)
                } else {
                    start
                }
                if (firstEligibleDate > end) null
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
            today >= holiday.startDate && today <= holiday.endDate
        }?.let { return Snapshot.DuringHoliday(it.endDate) }

        val nextHoliday = validHolidays.firstOrNull { it.startDate > today } ?: return null
        var additionalCandidate: Snapshot.BeforeHoliday? = null
        for (date in additionalCourseDates.asSequence()
                .filter { it < nextHoliday.startDate }
                .distinct()
                .sortedDescending()) {
            if (validHolidays.any {
                date >= it.startDate && date <= it.endDate && date != it.endDate
            }) {
                continue
            }
            val endTime = lastClassEndAt(date) ?: continue
            additionalCandidate = Snapshot.BeforeHoliday(date.atTime(endTime))
            if (latestPossibleCourseDate != null && date > latestPossibleCourseDate) {
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
            while (searchLimit >= searchStart) {
                val candidateDate = regularCoursePatterns.asSequence()
                    .mapNotNull { latestPatternDateOnOrBefore(it, searchLimit) }
                    .maxOrNull() ?: break
                if (additionalCandidate != null &&
                    candidateDate <= additionalCandidate.startsAt.date
                ) break

                val holiday = validHolidays.firstOrNull {
                    candidateDate >= it.startDate && candidateDate <= it.endDate
                }
                if (holiday != null) {
                    if (candidateDate == holiday.endDate) {
                        lastClassEndAt(candidateDate)?.let { endTime ->
                            return Snapshot.BeforeHoliday(candidateDate.atTime(endTime))
                        }
                    }
                    if (holiday.startDate == LOCAL_DATE_MIN) break
                    searchLimit = holiday.startDate.minusDays(1)
                    continue
                }
                lastClassEndAt(candidateDate)?.let { endTime ->
                    return Snapshot.BeforeHoliday(candidateDate.atTime(endTime))
                }
                if (candidateDate == searchStart || candidateDate == LOCAL_DATE_MIN) break
                searchLimit = candidateDate.minusDays(1)
            }
        } else if (regularCoursePatterns == null &&
            earliestPossibleCourseDate != null && latestPossibleCourseDate != null
        ) {
            var searchDate = minOf(nextHoliday.startDate.minusDays(1), latestPossibleCourseDate)
            while (searchDate >= earliestPossibleCourseDate &&
                (additionalCandidate == null || searchDate > additionalCandidate.startsAt.date)
            ) {
                val holiday = validHolidays.firstOrNull {
                    searchDate >= it.startDate && searchDate <= it.endDate
                }
                if (holiday != null) {
                    if (searchDate == holiday.endDate) {
                        lastClassEndAt(searchDate)?.let { endTime ->
                            return Snapshot.BeforeHoliday(searchDate.atTime(endTime))
                        }
                    }
                    if (holiday.startDate == LOCAL_DATE_MIN ||
                        holiday.startDate < earliestPossibleCourseDate
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
        entriesByYear: Map<Int, List<HolidayEntry>>,
        date: LocalDate,
    ): HolidayBlock? {
        val period = mergeHolidayPeriods(holidayPeriodsFromStoredEntries(entriesByYear))
            .firstOrNull { date >= it.startDate && date <= it.endDate }
            ?: return null
        val name = HolidayEntries.entriesForDate(entriesByYear, period.startDate)
            .firstOrNull { it.type == HolidayEntry.TYPE_HOLIDAY }?.name.orEmpty()
        return HolidayBlock(name = name, startDate = period.startDate, endDate = period.endDate)
    }

    private fun mergeHolidayPeriods(holidays: List<HolidayPeriod>): List<HolidayPeriod> {
        val merged = mutableListOf<HolidayPeriod>()
        holidays.filter { it.endDate >= it.startDate }
            .sortedBy { it.startDate }
            .forEach { holiday ->
                val previous = merged.lastOrNull()
                if (previous == null ||
                    previous.endDate.daysUntil(holiday.startDate).toLong() > 1L
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
        if (pattern.stepDays <= 0L || limit < pattern.firstDate) return null
        val boundedLimit = minOf(limit, pattern.lastDate)
        if (boundedLimit < pattern.firstDate) return null
        val intervals = pattern.firstDate.daysUntil(boundedLimit).toLong() / pattern.stepDays
        return runCatching { pattern.firstDate.plusDays(intervals * pattern.stepDays) }.getOrNull()
    }

    fun message(snapshot: Snapshot, now: LocalDateTime): String = when (snapshot) {
        is Snapshot.DuringHoliday -> {
            val daysUntilReturn = now.date.daysUntil(snapshot.returnDate)
            if (daysUntilReturn <= 0) "怎么今天就返校了……"
            else "还有 $daysUntilReturn 天返校"
        }

        is Snapshot.BeforeHoliday -> {
            // 原来用 java.time 的 Duration.between(now, startsAt)。kotlinx-datetime 没有
            // LocalDateTime 的 Duration 差，直接算秒差 —— 语义一致（两者同时区解释）。
            val remainingSeconds = secondsBetween(now, snapshot.startsAt)
            if (remainingSeconds <= 0L) {
                "恭喜你放假啦！"
            } else {
                formatRemaining(remainingSeconds)
            }
        }
    }

    /** 两个本地时间相差的秒数（to - from）。负数表示 [to] 在 [from] 之前。 */
    private fun secondsBetween(from: LocalDateTime, to: LocalDateTime): Long =
        (to.date.toEpochDays() - from.date.toEpochDays()) * 86_400L +
            (to.time.toSecondOfDay() - from.time.toSecondOfDay())

    private fun formatRemaining(remainingSeconds: Long): String = when {
        remainingSeconds / 86_400L >= 1 -> "还有 ${remainingSeconds / 86_400L} 天放假"
        remainingSeconds / 3_600L >= 1 -> "还有 ${remainingSeconds / 3_600L} 小时放假"
        else -> "还有 ${(remainingSeconds / 60L).coerceAtLeast(1)} 分钟放假"
    }
}
