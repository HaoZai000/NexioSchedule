package com.haooz.chedule.data.util

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

// kotlinx-datetime 的补齐层：补上它没有、而调用点仍在用的那几个 java.time API。

fun todayLocalDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun nowLocalTime(): LocalTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time

fun nowLocalDateTime(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** 由 epoch 毫秒取本地时间。 */
fun localDateTimeAt(epochMillis: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())

// plusDays / minusDays / plusWeeks 在 kotlinx-datetime 里不存在，只能用 DateTimeUnit 版。

fun LocalDate.plusDays(days: Long): LocalDate = plus(days, DateTimeUnit.DAY)

fun LocalDate.minusDays(days: Long): LocalDate = minus(days, DateTimeUnit.DAY)

fun LocalDate.plusWeeks(weeks: Long): LocalDate = plus(weeks, DateTimeUnit.WEEK)

fun LocalDate.minusWeeks(weeks: Long): LocalDate = minus(weeks, DateTimeUnit.WEEK)

// kotlinx-datetime 的 MIN/MAX 是 internal，取它自己支持的年份区间端点当哨兵。
// 超出这个区间 LocalDate 构造不出来，做边界哨兵足够。

val LOCAL_DATE_MIN: LocalDate = LocalDate(-999_999, 1, 1)

val LOCAL_DATE_MAX: LocalDate = LocalDate(999_999, 12, 31)

/** epoch-day 下限/上限，供 `CourseScheduleDateBounds` 做数值钳制。 */
val LOCAL_DATE_MIN_EPOCH_DAY: Long = LOCAL_DATE_MIN.toEpochDays().toLong()
val LOCAL_DATE_MAX_EPOCH_DAY: Long = LOCAL_DATE_MAX.toEpochDays().toLong()

// kotlinx-datetime 的 toEpochDays() 返回 Int、fromEpochDays() 也收 Int，
// 与 java.time 的 Long 版本不同，越界值会被静默回绕，所以统一走下面这个带检查的版本。

/** 超出可表示范围返回 null。 */
fun localDateFromEpochDays(epochDays: Long): LocalDate? =
    if (epochDays < Int.MIN_VALUE || epochDays > Int.MAX_VALUE) {
        null
    } else {
        runCatching { LocalDate.fromEpochDays(epochDays.toInt()) }.getOrNull()
    }

/** 用「下个月 1 号 - 本月 1 号」算。 */
fun LocalDate.lengthOfMonth(): Int {
    val first = LocalDate(year, monthNumber, 1)
    val next = if (monthNumber == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, monthNumber + 1, 1)
    return first.daysUntil(next)
}

// java.lang.Math 在 Kotlin/Native 上不存在，这里照抄 addExact/subtractExact 的溢出语义：溢出返回 null。

fun addExactOrNull(a: Long, b: Long): Long? {
    val result = a + b
    // 同号相加结果变号 = 溢出
    return if (((a xor result) and (b xor result)) < 0) null else result
}

fun subtractExactOrNull(a: Long, b: Long): Long? {
    val result = a - b
    // 异号相减结果变号 = 溢出
    return if (((a xor b) and (a xor result)) < 0) null else result
}

// 下面的格式化都手写，不用 kotlinx-datetime 的 Format DSL：
// 它的默认 padding 与 java 的 pattern 不完全一致，而节次时间等结果会被再次解析，差一个字符就是静默算错。

/** `HH:mm`（补零到两位）。 */
fun LocalTime.formatHourMinute(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** 解析 `HH:mm`，也接受 `H:mm`。 */
fun parseHourMinute(value: String): LocalTime? {
    val parts = value.trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime(hour, minute)
}

/**
 * 两个 LocalTime 相差的毫秒数（to - from），**不**处理跨午夜：to < from 时为负。
 *
 * 课程时段要跨午夜语义请传 [allowRollover] = true，或直接用 [CourseTimeSpan]。
 */
fun millisBetween(from: LocalTime, to: LocalTime, allowRollover: Boolean = false): Long {
    val delta = (to.toSecondOfDay() - from.toSecondOfDay()) * 1000L
    if (allowRollover && delta < 0L) return delta + 24L * 60L * 60L * 1000L
    return delta
}

/**
 * 课程时间段的「跨午夜」语义：结束时间小于等于开始时间即视为次日结束
 * （`23:00 - 00:30` 跨午夜 90 分钟）。存盘仍是 `"HH:mm"` 两个字段，不引入新字段，老数据与导入导出格式不受影响；
 * 起止相同按满 24 小时处理，避免算出 0 长的课。
 */
object CourseTimeSpan {

    /** 跨午夜判定：结束不晚于开始。相等也算，返回 true 时 [minutesUntilEnd] 给 24 小时。 */
    fun crossesMidnight(startMin: Int, endMin: Int): Boolean = endMin <= startMin

    /** 时段长度（分钟），跨午夜时给 `endMin + 1440 - startMin`，相等时给 1440。 */
    fun minutesUntilEnd(startMin: Int, endMin: Int): Int =
        if (endMin > startMin) endMin - startMin else endMin + 1440 - startMin

    /** 结束时刻在「从开始日零点起算」的分钟偏移，跨午夜时大于 1440。 */
    fun endOffsetMinutes(startMin: Int, endMin: Int): Int =
        if (endMin > startMin) endMin else endMin + 1440

    /**
     * 「正在上课」判定（分钟版的 [coursePeriodOngoing]，给按 `"HH:mm"` 解析成分钟的调用方用）。
     * 跨午夜只认 `[start, 24:00)` —— 0 点后的尾巴属于前一天，不在今日课表里；
     * 起止相同按非进行中处理（LocalTime 版视为 24 小时，这边多是脏数据/解析失败的 `0,0`）。
     */
    fun isActiveAt(startMin: Int, endMin: Int, currentMin: Int): Boolean = when {
        endMin < startMin -> currentMin >= startMin
        startMin == endMin -> false
        else -> currentMin in startMin until endMin
    }

    /**
     * 两个时段是否重叠。
     *
     * 跨午夜时段（[crossesMidnight] 为 true）先按「次日尾巴」展开成两段：
     * `[start, 1440)` 与 `[0, end)`，再逐段做普通的半开区间比较。
     * 两段都跨午夜的极端情况（整节都在午夜前开始、次日午夜后又跨一次）不现实，
     * 也不需要支持 —— 一节课最长 24 小时。
     */
    fun overlaps(
        aStart: Int, aEnd: Int,
        bStart: Int, bEnd: Int,
    ): Boolean {
        for (a in expand(aStart, aEnd)) {
            for (b in expand(bStart, bEnd)) {
                if (a.first < b.second && b.first < a.second) return true
            }
        }
        return false
    }

    /** 把 [startMin, endMin] 展开成不跨零点的若干半开区间 `(起点, 终点)`。 */
    private fun expand(startMin: Int, endMin: Int): List<Pair<Int, Int>> {
        val end = endOffsetMinutes(startMin, endMin)
        return if (end <= 1440) listOf(startMin to end)
        else listOf(startMin to 1440, 0 to (end - 1440))
    }
}

/**
 * 课程时段（允许跨午夜）此刻是否进行中。
 *
 * 同日课（end > start）为 `now ∈ [start, end)`；
 * 跨午夜课（end ≤ start）在「当天」只覆盖 start→午夜段——凌晨 `[0, end)` 那段
 * 属于**前一天**的那一节，而当天列表在零点后已整体换成新的一天，不能再往前认领，
 * 否则 23:00-00:30 的课会在凌晨 00:10 被当成"进行中"。
 */
fun coursePeriodOngoing(start: LocalTime, end: LocalTime, now: LocalTime): Boolean =
    if (end > start) now >= start && now < end else now >= start

/**
 * 课程时段此刻是否已结束。跨午夜课的结束点在次日，当天内永不结束——
 * 不能拿 `now > end(00:30)` 当"已上完"，否则白天全程误判。
 */
fun coursePeriodCompleted(start: LocalTime, end: LocalTime, now: LocalTime): Boolean =
    end > start && now > end

private fun pad2(value: Int): String = value.toString().padStart(2, '0')

/** `MM/dd`。 */
fun LocalDate.formatMonthDay(): String = "${pad2(monthNumber)}/${pad2(dayOfMonth)}"

/** `M/d`（不补零）。 */
fun LocalDate.formatMonthDayShort(): String = "$monthNumber/$dayOfMonth"

/** `yyyy年M月d日`。年补零到 4 位。 */
fun LocalDate.formatChineseDate(): String =
    "${year.toString().padStart(4, '0')}年${monthNumber}月${dayOfMonth}日"

/** `yyyy/MM/dd`。 */
fun LocalDate.formatSlashDate(): String =
    "${year.toString().padStart(4, '0')}/${pad2(monthNumber)}/${pad2(dayOfMonth)}"

/** `yyyyMMdd_HHmmss`。 */
fun LocalDateTime.formatCompactStamp(): String =
    "${year.toString().padStart(4, '0')}${pad2(monthNumber)}${pad2(dayOfMonth)}" +
        "_${pad2(hour)}${pad2(minute)}${pad2(second)}"

/** `yyyy-MM-dd HH:mm:ss`。 */
fun LocalDateTime.formatDisplayDateTime(): String =
    "${year.toString().padStart(4, '0')}-${pad2(monthNumber)}-${pad2(dayOfMonth)} " +
        "${pad2(hour)}:${pad2(minute)}:${pad2(second)}"

/** `yyyy-MM-dd HH:mm`（不带秒）。设置页「上次操作」文案用这个。 */
fun LocalDateTime.formatDateTimeMinute(): String =
    "${year.toString().padStart(4, '0')}-${pad2(monthNumber)}-${pad2(dayOfMonth)} " +
        "${pad2(hour)}:${pad2(minute)}"

/** ICS 的 `DTSTART` / `DTEND` 值：`yyyyMMdd'T'HHmmss`，分隔符是字面量 T。 */
fun LocalDateTime.formatIcsTimestamp(): String =
    "${year.toString().padStart(4, '0')}${pad2(monthNumber)}${pad2(dayOfMonth)}" +
        "T${pad2(hour)}${pad2(minute)}${pad2(second)}"

/** 解析 `yyyyMMdd_HHmmss`；长度或数字不合法时返回 null。 */
fun parseCompactStamp(value: String): LocalDateTime? {
    if (value.length != 15 || value[8] != '_') return null
    val digits = value.substring(0, 8) + value.substring(9)
    if (digits.any { !it.isDigit() }) return null
    val year = digits.substring(0, 4).toIntOrNull() ?: return null
    val month = digits.substring(4, 6).toIntOrNull() ?: return null
    val day = digits.substring(6, 8).toIntOrNull() ?: return null
    val hour = digits.substring(8, 10).toIntOrNull() ?: return null
    val minute = digits.substring(10, 12).toIntOrNull() ?: return null
    val second = digits.substring(12, 14).toIntOrNull() ?: return null
    return runCatching { LocalDateTime(year, month, day, hour, minute, second) }.getOrNull()
}

fun LocalDate.atStartOfDay(): LocalDateTime = LocalDateTime(this, LocalTime(0, 0))

val LocalDateTime.localDate: LocalDate get() = date

/** java.time 的 WEEKS.between 对负数向零截断，整数除法同样向零截断，等价。 */
fun weeksBetween(from: LocalDate, to: LocalDate): Int = from.daysUntil(to) / 7
