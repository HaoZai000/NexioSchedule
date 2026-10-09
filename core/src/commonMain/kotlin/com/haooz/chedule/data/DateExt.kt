package com.haooz.chedule.data

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

// ════════════════════════════════════════════════════════════════════════
//  `java.time` → kotlinx-datetime 的补齐层
//
//  阶段 0 的验证 2 已证明两者在 parse / 构造 / plus / daysUntil / dayOfWeek /
//  toEpochDays 上**语义等价**，所以换类型是安全的。但有几个 API **kotlinx-datetime
//  根本没有**，不能靠「同名替换」解决 —— 这一层就是补它们的。
//
//  每条都写了「原来是什么、为什么必须自己写」，改之前先看这里，别凭印象改。
// ════════════════════════════════════════════════════════════════════════

// ── 1. 「现在」──────────────────────────────────
// java.time 的 LocalDate.now() / LocalTime.now() / LocalDateTime.now()。
// kotlinx-datetime 必须显式给时区，没有无参版本。

fun todayLocalDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun nowLocalTime(): LocalTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time

fun nowLocalDateTime(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** 由 epoch 毫秒取本地时间。原实现：`Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()`。 */
fun localDateTimeAt(epochMillis: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())

// ── 1b. plusDays / minusDays / plusWeeks ─────────
// kotlinx-datetime **没有**这三个（0.6.2 实测：`plusDays` 是 internal，其余不存在），
// 只有 `plus(value, DateTimeUnit)` / `minus(value, DateTimeUnit)`，而且它们是
// **顶层扩展**，必须 `import kotlinx.datetime.plus` 才能用。
// 这里包一层，让调用点保持原来的写法 —— 语义一致：越界时抛异常（java.time 也是抛）。

fun LocalDate.plusDays(days: Long): LocalDate = plus(days, DateTimeUnit.DAY)

fun LocalDate.minusDays(days: Long): LocalDate = minus(days, DateTimeUnit.DAY)

fun LocalDate.plusWeeks(weeks: Long): LocalDate = plus(weeks, DateTimeUnit.WEEK)

fun LocalDate.minusWeeks(weeks: Long): LocalDate = minus(weeks, DateTimeUnit.WEEK)

// ── 2. LocalDate.MIN / MAX ──────────────────────
// kotlinx-datetime **有** MIN/MAX，但是 `internal`（0.6.2 实测，见 commonMain/LocalDate.kt），
// 外部拿不到。而 `HolidayCountdown` 与 `CourseScheduleDateBounds` 拿它们当边界哨兵，不能没有。
//
// 取值 = kotlinx-datetime 自己支持的年份区间端点（YEAR_MIN = -999_999 / YEAR_MAX = 999_999）。
// 超出这个区间 `LocalDate` 根本构造不出来，所以做哨兵足够：
// 原实现里 `if (date == LocalDate.MAX) break` 这类判断语义不变。

val LOCAL_DATE_MIN: LocalDate = LocalDate(-999_999, 1, 1)

val LOCAL_DATE_MAX: LocalDate = LocalDate(999_999, 12, 31)

/** epoch-day 下限/上限（供 `CourseScheduleDateBounds` 做数值钳制，替代 `LocalDate.MIN.toEpochDay()`）。 */
val LOCAL_DATE_MIN_EPOCH_DAY: Long = LOCAL_DATE_MIN.toEpochDays().toLong()
val LOCAL_DATE_MAX_EPOCH_DAY: Long = LOCAL_DATE_MAX.toEpochDays().toLong()

// ── 3. epoch-day 与 Int 的坑 ────────────────────
// java.time 的 `toEpochDay()` 返回 **Long**，`ofEpochDay(Long)` 收 Long；
// kotlinx-datetime 的 `toEpochDays()` 返回 **Int**，`fromEpochDays(Int)` 收 Int。
// 直接 `.toInt()` 会把越界值**静默回绕**（Long→Int 不抛异常），这是最危险的一处。

/** 安全版 `LocalDate.ofEpochDay`：超出可表示范围返回 null（java.time 是抛 DateTimeException）。 */
fun localDateFromEpochDays(epochDays: Long): LocalDate? =
    if (epochDays < Int.MIN_VALUE || epochDays > Int.MAX_VALUE) {
        null
    } else {
        runCatching { LocalDate.fromEpochDays(epochDays.toInt()) }.getOrNull()
    }

// ── 4. lengthOfMonth ────────────────────────────
// kotlinx-datetime 没有 `lengthOfMonth()`。用「下个月 1 号 - 本月 1 号」算，
// 12 月单独处理跨年（year + 1 在极值年份会越界，但那种日期现实不存在，且越界时构造会抛、
// 被调用点的 runCatching 兜住）。

fun LocalDate.lengthOfMonth(): Int {
    val first = LocalDate(year, monthNumber, 1)
    val next = if (monthNumber == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, monthNumber + 1, 1)
    return first.daysUntil(next)
}

// ── 5. java.lang.Math.addExact / subtractExact ──
// `java.lang.Math` 在 Kotlin/Native 上**不存在**。`TeachingWeekReorganization` 用
// addExact/subtractExact 做溢出保护，外面套 `runCatching { }.getOrNull()` ——
// 也就是「溢出 → null」。这里照抄这个语义。

fun addExactOrNull(a: Long, b: Long): Long? {
    val result = a + b
    // 同号相加结果变号 = 溢出（与 java.lang.Math.addExact 同一判据）
    return if (((a xor result) and (b xor result)) < 0) null else result
}

fun subtractExactOrNull(a: Long, b: Long): Long? {
    val result = a - b
    // 异号相减结果变号 = 溢出
    return if (((a xor b) and (a xor result)) < 0) null else result
}

// ── 6. "HH:mm" 的解析与格式化 ───────────────────
// java.time 用 `DateTimeFormatter.ofPattern("HH:mm")`。kotlinx-datetime 有
// `LocalTime.Format { }` DSL，但它的 padding 默认行为与 java 的 pattern 不完全一致，
// 而这里格式化出的串**会被再次解析**（节次时间 `sectionTimes` 是 "HH:mm-HH:mm"），
// 差一个字符就是静默算错。所以直接手写，行为 100% 可控。

/** 格式化为 `HH:mm`（补零到两位）。等价 `DateTimeFormatter.ofPattern("HH:mm")`。 */
fun LocalTime.formatHourMinute(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** 解析 `HH:mm`；也接受 `H:mm`（原 DateTimeFormatter 的 HH 是严格两位，这里放宽不更宽松的行为）。 */
fun parseHourMinute(value: String): LocalTime? {
    val parts = value.trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return runCatching { LocalTime(hour, minute) }.getOrNull()
}

/** 两个 LocalTime 相差的毫秒数（to - from）。原实现：`Duration.between(from, to).toMillis()`。 */
fun millisBetween(from: LocalTime, to: LocalTime): Long =
    (to.toSecondOfDay() - from.toSecondOfDay()) * 1000L

// ── 6b. 固定 pattern 的日期格式化 ─────────────────
// 原实现用 `DateTimeFormatter.ofPattern(...)`。kotlinx-datetime 也有 `LocalDate.Format { }` DSL，
// 但它的默认 padding 与 java pattern 不完全一致，而这里格式化出来的串**会被再次解析**
// （`_classStartTime` 存 "yyyy/MM/dd"，别处 `.replace("/", "-")` 后再 parse），差一个字符就是
// 静默算错。直接手写最可控。
//
// ⚠ java 的 `yyyy` 是「年」且补零到 4 位；这里照做（`padStart(4, '0')`）。

private fun pad2(value: Int): String = value.toString().padStart(2, '0')

/** `MM/dd`。对应 `DateTimeFormatter.ofPattern("MM/dd")`。 */
fun LocalDate.formatMonthDay(): String = "${pad2(monthNumber)}/${pad2(dayOfMonth)}"

/** `M/d`（不补零）。对应 `DateTimeFormatter.ofPattern("M/d")`。 */
fun LocalDate.formatMonthDayShort(): String = "$monthNumber/$dayOfMonth"

/** `yyyy年M月d日`。对应 `DateTimeFormatter.ofPattern("yyyy年M月d日")`。 */
fun LocalDate.formatChineseDate(): String =
    "${year.toString().padStart(4, '0')}年${monthNumber}月${dayOfMonth}日"

/** `yyyy/MM/dd`。对应 `DateTimeFormatter.ofPattern("yyyy/MM/dd")`。 */
fun LocalDate.formatSlashDate(): String =
    "${year.toString().padStart(4, '0')}/${pad2(monthNumber)}/${pad2(dayOfMonth)}"

// ── 7. LocalDateTime 组装 ───────────────────────
// java.time 的 `date.atTime(time)` / `date.atStartOfDay()` / `dateTime.toLocalDate()`。

/** java.time 的 `date.atStartOfDay()`。（`atTime` 不需要 —— kotlinx-datetime 自带同名扩展。） */
fun LocalDate.atStartOfDay(): LocalDateTime = LocalDateTime(this, LocalTime(0, 0))

/** 对应 `LocalDateTime.toLocalDate()`。 */
val LocalDateTime.localDate: LocalDate get() = date

// ── 8. ChronoUnit.WEEKS.between ─────────────────
// java.time 的 WEEKS.between 对负数**向零截断**；Kotlin 的整数除法同样向零截断，等价。

fun weeksBetween(from: LocalDate, to: LocalDate): Int = from.daysUntil(to) / 7
