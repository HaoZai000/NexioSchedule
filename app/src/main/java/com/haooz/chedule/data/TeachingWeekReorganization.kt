package com.haooz.chedule.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Two consecutive weekday ranges from stable, original calendar weeks form one teaching week. */
data class TeachingWeekReorganizationRule(
    val firstOriginalWeek: Int,
    val firstStartWeekday: Int,
    val firstEndWeekday: Int,
    val secondOriginalWeek: Int,
    val secondStartWeekday: Int,
    val secondEndWeekday: Int,
)

/** A date in a reorganization gap keeps its surrounding teaching-week context but has no weekday. */
data class TeachingWeekPosition(
    val week: Long,
    val weekday: Int?,
    val isReorganizationPause: Boolean = false,
)

/** Pure date mapping and strict persistence codec for teaching-week reorganizations. */
object TeachingWeekReorganization {
    const val SCHEMA_VERSION = 1
    private const val SCHEMA_VERSION_KEY = "schema_version"
    private const val RULES_KEY = "rules"
    private val gson = Gson()

    /**
     * Returns a user-facing validation error, or null for a complete, non-overlapping ruleset.
     * Every rule must compose exactly Monday–Sunday; the uncovered calendar dates are a pause.
     */
    fun validationError(
        rules: List<TeachingWeekReorganizationRule>,
        totalWeeks: Int,
    ): String? {
        if (totalWeeks <= 0) return "当前课表总周数无效"
        var compressedWeeks = 0L
        var previousSpanEnd = Long.MIN_VALUE
        val ordered = rules.sortedBy { it.firstOriginalWeek }
        for (rule in ordered) {
            if (rule.firstOriginalWeek < 1 || rule.secondOriginalWeek < 1 ||
                rule.firstEndWeekday !in 1..6 || rule.firstStartWeekday != 1 ||
                rule.secondEndWeekday != 7 || rule.secondStartWeekday !in 2..7
            ) {
                return "第一段须从星期一开始，第二段须延续到星期日"
            }
            if (rule.secondOriginalWeek <= rule.firstOriginalWeek) {
                return "第二段原始周必须晚于第一段"
            }
            if (rule.secondStartWeekday != rule.firstEndWeekday + 1) {
                return "两段星期必须连续组成完整教学周"
            }

            val targetWeek = rule.firstOriginalWeek.toLong() - compressedWeeks
            if (targetWeek !in 1L..totalWeeks.toLong()) {
                return "重组后的教学周超出当前课表总周数"
            }

            val spanStart = (rule.firstOriginalWeek.toLong() - 1L) * 7L
            val spanEnd = (rule.secondOriginalWeek.toLong() - 1L) * 7L + 6L
            if (spanStart <= previousSpanEnd) return "教学周重组规则的日期范围不能重叠"
            previousSpanEnd = spanEnd

            compressedWeeks += rule.secondOriginalWeek.toLong() - rule.firstOriginalWeek.toLong()
        }
        return null
    }

    /** Allow editing one existing rule while preserving other rules invalidated by a shorter term. */
    fun validationErrorForRuleChange(
        updatedRules: List<TeachingWeekReorganizationRule>,
        existingRules: List<TeachingWeekReorganizationRule>,
        changedRule: TeachingWeekReorganizationRule,
        totalWeeks: Int,
    ): String? {
        if (totalWeeks <= 0) return "当前课表总周数无效"
        validationError(updatedRules, Int.MAX_VALUE)?.let { return it }
        val changedTeachingWeek = teachingWeekForRule(changedRule, updatedRules)
            ?: return "找不到当前编辑的教学周规则"
        if (changedTeachingWeek !in 1L..totalWeeks.toLong()) {
            return "当前编辑的重组教学周超出课表总周数"
        }
        val newlyOutOfRange = updatedRules.firstOrNull { rule ->
            val teachingWeek = teachingWeekForRule(rule, updatedRules) ?: return@firstOrNull true
            val previousTeachingWeek = teachingWeekForRule(rule, existingRules)
            teachingWeek !in 1L..totalWeeks.toLong() &&
                (previousTeachingWeek == null || previousTeachingWeek in 1L..totalWeeks.toLong())
        }
        if (newlyOutOfRange != null) return "新规则超出当前课表总周数"
        return null
    }

    fun teachingWeekForRule(
        rule: TeachingWeekReorganizationRule,
        rules: List<TeachingWeekReorganizationRule>,
    ): Long? {
        var compressedWeeks = 0L
        for (candidate in rules.sortedBy { it.firstOriginalWeek }) {
            val teachingWeek = candidate.firstOriginalWeek.toLong() - compressedWeeks
            if (candidate == rule) return teachingWeek
            compressedWeeks += candidate.secondOriginalWeek.toLong() - candidate.firstOriginalWeek.toLong()
        }
        return null
    }

    /** Date -> teaching week/day. Pause dates have a week context but no schedulable weekday. */
    fun mapDate(
        semesterStartDate: LocalDate,
        date: LocalDate,
        rules: List<TeachingWeekReorganizationRule>,
    ): TeachingWeekPosition {
        val monday = semesterMonday(semesterStartDate)
        val daysFromMonday = ChronoUnit.DAYS.between(monday, date)
        var compressedWeeks = 0L
        for (rule in rules.sortedBy { it.firstOriginalWeek }) {
            val firstStart = (rule.firstOriginalWeek.toLong() - 1L) * 7L + rule.firstStartWeekday - 1L
            val firstEnd = (rule.firstOriginalWeek.toLong() - 1L) * 7L + rule.firstEndWeekday - 1L
            val secondStart = (rule.secondOriginalWeek.toLong() - 1L) * 7L + rule.secondStartWeekday - 1L
            val secondEnd = (rule.secondOriginalWeek.toLong() - 1L) * 7L + rule.secondEndWeekday - 1L
            if (daysFromMonday < firstStart) break

            val targetWeek = rule.firstOriginalWeek.toLong() - compressedWeeks
            when {
                daysFromMonday <= firstEnd -> return TeachingWeekPosition(targetWeek, date.dayOfWeek.value)
                daysFromMonday < secondStart -> return TeachingWeekPosition(
                    week = targetWeek,
                    weekday = null,
                    isReorganizationPause = true,
                )
                daysFromMonday <= secondEnd -> return TeachingWeekPosition(targetWeek, date.dayOfWeek.value)
                else -> compressedWeeks += rule.secondOriginalWeek.toLong() - rule.firstOriginalWeek.toLong()
            }
        }

        val rawWeek = daysFromMonday.floorDiv(7L) + 1L
        return TeachingWeekPosition(rawWeek - compressedWeeks, date.dayOfWeek.value)
    }

    /** Teaching week/day -> actual calendar date; returns null for invalid positions or overflow. */
    fun dateForPosition(
        semesterStartDate: LocalDate,
        teachingWeek: Int,
        weekday: Int,
        rules: List<TeachingWeekReorganizationRule>,
    ): LocalDate? = dateForPosition(semesterStartDate, teachingWeek.toLong(), weekday, rules)

    fun datesForTeachingWeek(
        semesterStartDate: LocalDate,
        teachingWeek: Int,
        rules: List<TeachingWeekReorganizationRule>,
    ): List<LocalDate> = (1..7).mapNotNull { weekday ->
        dateForPosition(semesterStartDate, teachingWeek, weekday, rules)
    }.takeIf { it.size == 7 }.orEmpty()

    /** A teaching week cannot advance while any of its mapped calendar dates are still ahead. */
    fun hasFutureTeachingWeekDates(
        semesterStartDate: LocalDate,
        date: LocalDate,
        rules: List<TeachingWeekReorganizationRule>,
    ): Boolean {
        if (rules.isEmpty()) return false
        val position = mapDate(semesterStartDate, date, rules)
        return (1..7).any { weekday ->
            dateForPosition(semesterStartDate, position.week, weekday, rules)?.isAfter(date) == true
        }
    }

    fun dateForPosition(
        semesterStartDate: LocalDate,
        teachingWeek: Long,
        weekday: Int,
        rules: List<TeachingWeekReorganizationRule>,
    ): LocalDate? {
        if (weekday !in 1..7) return null
        val monday = semesterMonday(semesterStartDate)
        // Current-week alignment can legitimately shift a candidate before original week one.
        // Reorganization rules begin at week one, so such positions retain the linear baseline.
        if (teachingWeek < 1L) {
            return runCatching {
                monday.plusWeeks(Math.subtractExact(teachingWeek, 1L)).plusDays((weekday - 1).toLong())
            }.getOrNull()
        }
        var compressedWeeks = 0L
        for (rule in rules.sortedBy { it.firstOriginalWeek }) {
            val targetWeek = rule.firstOriginalWeek.toLong() - compressedWeeks
            if (teachingWeek == targetWeek) {
                val originalWeek = when (weekday) {
                    in rule.firstStartWeekday..rule.firstEndWeekday -> rule.firstOriginalWeek
                    in rule.secondStartWeekday..rule.secondEndWeekday -> rule.secondOriginalWeek
                    else -> return null
                }
                return originalWeekDate(monday, originalWeek, weekday)
            }
            if (teachingWeek > targetWeek) {
                compressedWeeks += rule.secondOriginalWeek.toLong() - rule.firstOriginalWeek.toLong()
            } else {
                break
            }
        }
        val originalWeek = runCatching { Math.addExact(teachingWeek, compressedWeeks) }
            .getOrNull() ?: return null
        return runCatching { monday.plusWeeks(originalWeek - 1L).plusDays((weekday - 1).toLong()) }
            .getOrNull()
    }

    /** Rebase the semester start so [date] maps to the requested teaching week and weekday. */
    fun semesterStartDateForTeachingWeekOnDate(
        semesterStartDate: LocalDate,
        date: LocalDate,
        teachingWeek: Long,
        rules: List<TeachingWeekReorganizationRule>,
    ): LocalDate? {
        val targetPositionDate = dateForPosition(
            semesterStartDate = semesterStartDate,
            teachingWeek = teachingWeek,
            weekday = date.dayOfWeek.value,
            rules = rules,
        ) ?: return null
        val targetOffsetDays = ChronoUnit.DAYS.between(semesterMonday(semesterStartDate), targetPositionDate)
        return runCatching {
            date.minusDays(targetOffsetDays)
                .plusDays((semesterStartDate.dayOfWeek.value - 1).toLong())
        }.getOrNull()
    }

    /** Teaching-week labels occupied by merge rules, in chronological order. */
    fun teachingWeeksForRules(rules: List<TeachingWeekReorganizationRule>): List<Long> {
        var compressedWeeks = 0L
        return rules.sortedBy { it.firstOriginalWeek }.map { rule ->
            (rule.firstOriginalWeek.toLong() - compressedWeeks).also {
                compressedWeeks += rule.secondOriginalWeek.toLong() - rule.firstOriginalWeek.toLong()
            }
        }
    }

    /** Raw calendar-week index for a non-reorganized teaching week. */
    fun originalWeekForTeachingWeek(
        teachingWeek: Long,
        rules: List<TeachingWeekReorganizationRule>,
    ): Long? {
        if (teachingWeek < 1L) return teachingWeek
        var compressedWeeks = 0L
        for (rule in rules.sortedBy { it.firstOriginalWeek }) {
            val targetWeek = rule.firstOriginalWeek.toLong() - compressedWeeks
            if (teachingWeek == targetWeek) return null
            if (teachingWeek > targetWeek) {
                compressedWeeks += rule.secondOriginalWeek.toLong() - rule.firstOriginalWeek.toLong()
            } else {
                break
            }
        }
        return runCatching { Math.addExact(teachingWeek, compressedWeeks) }.getOrNull()
    }

    /** Week picker ceiling follows the active instructional horizon and existing raw-week entries. */
    fun maxOriginalWeek(
        totalWeeks: Int,
        rules: List<TeachingWeekReorganizationRule>,
    ): Int {
        val compressed = rules.sumOf {
            it.secondOriginalWeek.toLong() - it.firstOriginalWeek.toLong()
        }
        val horizon = totalWeeks.toLong().coerceAtLeast(1L) + compressed + 1L
        val existing = rules.maxOfOrNull { maxOf(it.firstOriginalWeek, it.secondOriginalWeek) } ?: 1
        return maxOf(horizon.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), existing, 1)
    }

    /** Pick a valid adjacent-week merge draft, preferring week four when available. */
    fun firstAvailableStartingWeek(
        totalWeeks: Int,
        rules: List<TeachingWeekReorganizationRule>,
        preferredWeek: Int = 4,
    ): Int? {
        val maxWeek = maxOriginalWeek(totalWeeks, rules)
        val spans = rules.sortedBy { it.firstOriginalWeek }
        fun validCandidate(week: Int): Boolean {
            val candidate = TeachingWeekReorganizationRule(
                firstOriginalWeek = week,
                firstStartWeekday = 1,
                firstEndWeekday = 3,
                secondOriginalWeek = week + 1,
                secondStartWeekday = 4,
                secondEndWeekday = 7,
            )
            return validationErrorForRuleChange(
                updatedRules = rules + candidate,
                existingRules = rules,
                changedRule = candidate,
                totalWeeks = totalWeeks,
            ) == null
        }
        if (preferredWeek in 1 until maxWeek && validCandidate(preferredWeek)) return preferredWeek
        var week = 1L
        while (week < maxWeek.toLong()) {
            val conflicting = spans.firstOrNull { rule ->
                week <= rule.secondOriginalWeek.toLong() && week + 1L >= rule.firstOriginalWeek.toLong()
            }
            if (conflicting != null) {
                week = conflicting.secondOriginalWeek.toLong() + 1L
                continue
            }
            if (validCandidate(week.toInt())) return week.toInt()
            week++
        }
        return null
    }

    fun encode(rules: List<TeachingWeekReorganizationRule>, totalWeeks: Int): String {
        require(validationError(rules, totalWeeks) == null) { validationError(rules, totalWeeks)!! }
        return gson.toJson(toBackupValue(rules))
    }

    fun decode(raw: String, totalWeeks: Int): List<TeachingWeekReorganizationRule> {
        val data = runCatching {
            gson.fromJson<Map<String, Any>>(raw, object : TypeToken<Map<String, Any>>() {}.type)
        }.getOrNull() ?: throw IllegalArgumentException("Invalid teaching-week reorganization data")
        return fromBackupValue(data, present = true, totalWeeks = totalWeeks)
    }

    fun toBackupValue(rules: List<TeachingWeekReorganizationRule>): Map<String, Any> = mapOf(
        SCHEMA_VERSION_KEY to SCHEMA_VERSION,
        RULES_KEY to rules.map { rule ->
            mapOf(
                "firstOriginalWeek" to rule.firstOriginalWeek,
                "firstStartWeekday" to rule.firstStartWeekday,
                "firstEndWeekday" to rule.firstEndWeekday,
                "secondOriginalWeek" to rule.secondOriginalWeek,
                "secondStartWeekday" to rule.secondStartWeekday,
                "secondEndWeekday" to rule.secondEndWeekday,
            )
        },
    )

    /** A missing field is a legacy configuration; a present null or invalid value is rejected. */
    fun fromBackupValue(
        value: Any?,
        present: Boolean,
        totalWeeks: Int,
        preserveOutOfRangeRules: Boolean = false,
    ): List<TeachingWeekReorganizationRule> {
        if (!present) return emptyList()
        require(value is Map<*, *>) { "Invalid teaching-week reorganization backup" }
        require(readInteger(value[SCHEMA_VERSION_KEY]) == SCHEMA_VERSION) {
            "Unsupported teaching-week reorganization schema"
        }
        val rawRules = value[RULES_KEY]
        require(rawRules is List<*>) { "Invalid teaching-week reorganization rules" }
        val rules = rawRules.map { rawRule ->
            require(rawRule is Map<*, *>) { "Invalid teaching-week reorganization rule" }
            TeachingWeekReorganizationRule(
                firstOriginalWeek = requiredInteger(rawRule, "firstOriginalWeek"),
                firstStartWeekday = requiredInteger(rawRule, "firstStartWeekday"),
                firstEndWeekday = requiredInteger(rawRule, "firstEndWeekday"),
                secondOriginalWeek = requiredInteger(rawRule, "secondOriginalWeek"),
                secondStartWeekday = requiredInteger(rawRule, "secondStartWeekday"),
                secondEndWeekday = requiredInteger(rawRule, "secondEndWeekday"),
            )
        }.sortedBy { it.firstOriginalWeek }
        val validationHorizon = if (preserveOutOfRangeRules) Int.MAX_VALUE else totalWeeks
        val error = validationError(rules, validationHorizon)
        require(error == null) { error ?: "Invalid teaching-week reorganization rules" }
        return rules
    }

    private fun requiredInteger(map: Map<*, *>, key: String): Int =
        readInteger(map[key]) ?: throw IllegalArgumentException("Invalid $key in teaching-week reorganization")

    private fun readInteger(value: Any?): Int? {
        val number = (value as? Number)?.toDouble() ?: return null
        if (!number.isFinite() || number % 1.0 != 0.0 ||
            number < Int.MIN_VALUE.toDouble() || number > Int.MAX_VALUE.toDouble()
        ) return null
        return number.toInt()
    }

    private fun semesterMonday(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())

    private fun originalWeekDate(monday: LocalDate, week: Int, weekday: Int): LocalDate? =
        runCatching { monday.plusWeeks((week.toLong() - 1L)).plusDays((weekday - 1).toLong()) }
            .getOrNull()
}
