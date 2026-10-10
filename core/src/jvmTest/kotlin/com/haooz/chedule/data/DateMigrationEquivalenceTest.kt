package com.haooz.chedule.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.time.LocalDate as JLocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * kotlinx-datetime 的 `DayOfWeek` 是枚举（`ordinal` 0=MONDAY … 6=SUNDAY），
 * 0.6.2 **没有** `isoDayNumber`。ISO 编号（1=周一 … 7=周日）等于 `ordinal + 1`。
 * 项目里 `Course.dayOfWeek` 用的正是 1..7 语义，所以这个映射必须显式写出来。
 */
private val DayOfWeek.isoNumber: Int get() = ordinal + 1

/**
 * `java.time` ↔ `kotlinx-datetime` 的**差分等价性验证**。
 *
 * ## 为什么必须先有这份测试
 *
 * 计划文档的阶段 2 要把全项目的 `java.time` 换成 `kotlinx-datetime`，但写明：
 * 「该测试文件在提交前被删掉了。**做阶段 2 之前要按记忆里的对照表重新写回来**，
 * 否则日期算错没有安全网」。这个类就是补上那张安全网。
 *
 * 日期算错不会崩，只会静默错位 —— 表现为课表显示的周次/日期整体偏移一天，
 * 而「时间配置丢失」是本项目历史最严重的事故类型。
 *
 * ## 做法：拿 `java.time` 当基准穷举比对
 *
 * 不用「手写期望值」（写期望值的人可能就是算错的人），而是**同时用两套库计算同一个值**，
 * 逐条断言相等。范围覆盖 1900-2099 共 73,049 天，覆盖闰年、世纪年、跨年。
 *
 * 放在 `jvmTest`：只有 JVM 才有 `java.time` 可与 kotlinx-datetime 对照，
 * 而被验证的 kotlinx-datetime 代码在 commonMain。
 */
class DateMigrationEquivalenceTest {

    private val start = JLocalDate.of(1900, 1, 1)
    private val end = JLocalDate.of(2099, 12, 31)
    private val epoch = JLocalDate.of(1970, 1, 1)

    /** 把 java.time 的日期转成 kotlinx-datetime 的，二者字段语义必须一致。 */
    private fun JLocalDate.toKx(): LocalDate = LocalDate(year, monthValue, dayOfMonth)

    private fun allJavaDates(): Sequence<JLocalDate> = generateSequence(start) { d ->
        if (d >= end) null else d.plusDays(1)
    }

    // ---------- 1. 解析与字符串往返 ----------

    @Test
    fun `ISO 解析与 toString 在 200 年内逐日一致`() {
        var count = 0
        for (jd in allJavaDates()) {
            val text = jd.toString() // YYYY-MM-DD
            val kx = LocalDate.parse(text)
            assertEquals(jd.toKx(), kx, "解析 $text 结果不一致")
            assertEquals(text, kx.toString(), "toString 往返不一致")
            count++
        }
        assertEquals(73_049, count, "覆盖天数异常（1900-01-01..2099-12-31）")
    }

    // ---------- 2. 纪元天数（周次推算的基石） ----------

    @Test
    fun `toEpochDays 与 ChronoUnitDAYSbetween 逐日一致`() {
        for (jd in allJavaDates()) {
            val expected = ChronoUnit.DAYS.between(epoch, jd)
            assertEquals(
                expected,
                jd.toKx().toEpochDays().toLong(),
                "epochDays 不一致 @ $jd",
            )
        }
    }

    // ---------- 3. 加减天数（学期起始周一的推算依赖它） ----------

    @Test
    fun `plus 与 minus 天数在各种偏移下一致`() {
        val offsets = listOf(0, 1, 2, 6, 7, 13, 14, 27, 28, 29, 30, 31, 60, 90, 180, 365, 366, 400)
        // 逐日 × 每个偏移都验一遍会过慢，抽样：每 17 天取一个基准日（与 7 互质，能覆盖所有星期）
        var checked = 0
        allJavaDates().filterIndexed { i, _ -> i % 17 == 0 }.forEach { jd ->
            for (n in offsets) {
                val expected = jd.plusDays(n.toLong()).toKx()
                assertEquals(expected, jd.toKx().plus(n, DateTimeUnit.DAY), "plus($n) 不一致 @ $jd")
                val expectedMinus = jd.minusDays(n.toLong()).toKx()
                assertEquals(
                    expectedMinus,
                    jd.toKx().minus(n, DateTimeUnit.DAY),
                    "minus($n) 不一致 @ $jd",
                )
                checked += 2
            }
        }
        assertTrue(checked > 8_000, "抽样量过少: $checked")
    }

    /** 学期起始日 → 该周周一：`minusDays(dayOfWeek - 1)`。 */
    @Test
    fun `推算所在周的周一与 java_time 一致`() {
        for (jd in allJavaDates()) {
            val isoDow = jd.dayOfWeek.value // 1=周一 .. 7=周日
            val expectedMonday = jd.minusDays((isoDow - 1).toLong())
            val kx = jd.toKx()
            val kxMonday = kx.minus(kx.dayOfWeek.isoNumber - 1, DateTimeUnit.DAY)
            assertEquals(expectedMonday.toKx(), kxMonday, "周一对齐不一致 @ $jd")
        }
    }

    // ---------- 4. 星期与字段分量 ----------

    @Test
    fun `dayOfWeek 的 ISO 编号逐日一致`() {
        for (jd in allJavaDates()) {
            assertEquals(
                jd.dayOfWeek.value,
                jd.toKx().dayOfWeek.isoNumber,
                "dayOfWeek 不一致 @ $jd",
            )
        }
    }

    @Test
    fun `年月日分量逐日一致`() {
        for (jd in allJavaDates()) {
            val kx = jd.toKx()
            assertEquals(jd.year, kx.year, "year @ $jd")
            assertEquals(jd.monthValue, kx.monthNumber, "month @ $jd")
            assertEquals(jd.dayOfMonth, kx.dayOfMonth, "day @ $jd")
            assertEquals(jd.dayOfYear, kx.dayOfYear, "dayOfYear @ $jd")
        }
    }

    // ---------- 5. 闰年 / 世纪年（文档点名的重点） ----------

    /** 1900 不是闰年（世纪年需被 400 整除），2000 是闰年 —— 最经典的坑。 */
    @Test
    fun `闰年与世纪年的二月天数`() {
        val cases = mapOf(
            1900 to 28, // 能被 4 整除但不能被 400 整除 → 平年
            2000 to 29, // 能被 400 整除 → 闰年
            2004 to 29,
            2024 to 29,
            2026 to 28,
            2100 to 28, // 本测试范围外，但确认边界行为
        )
        for ((year, febDays) in cases) {
            val jd = JLocalDate.of(year, 2, 1)
            val kx = LocalDate(year, 2, 1)
            // 二月最后一天 = 3 月 1 日往前一天
            val expected = jd.plusMonths(1).minusDays(1).dayOfMonth
            val actual = kx.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
            assertEquals(febDays, expected, "java.time 的 $year 年二月天数异常")
            assertEquals(expected, actual, "$year 年二月天数不一致")
        }
    }

    /** 2 月 29 日 → 3 月 1 日 的推进必须一致。 */
    @Test
    fun `闰日推进一致`() {
        val jd = JLocalDate.of(2024, 2, 28)
        val kx = LocalDate(2024, 2, 28)
        assertEquals(jd.plusDays(1).toString(), kx.plus(1, DateTimeUnit.DAY).toString())
        assertEquals("2024-02-29", kx.plus(1, DateTimeUnit.DAY).toString())
        assertEquals("2024-03-01", kx.plus(2, DateTimeUnit.DAY).toString())
    }

    // ---------- 6. 跨年 / 跨世纪 ----------

    @Test
    fun `跨年与跨世纪边界一致`() {
        val boundaries = listOf(
            JLocalDate.of(1999, 12, 31),
            JLocalDate.of(2000, 1, 1),
            JLocalDate.of(2000, 2, 29),
            JLocalDate.of(2026, 12, 31),
            JLocalDate.of(2027, 1, 1),
            JLocalDate.of(1900, 12, 31),
            JLocalDate.of(2100, 1, 1),
        )
        for (jd in boundaries) {
            val kx = jd.toKx()
            assertEquals(jd.plusDays(1).toKx(), kx.plus(1, DateTimeUnit.DAY), "跨年 +1 @ $jd")
            assertEquals(jd.minusDays(1).toKx(), kx.minus(1, DateTimeUnit.DAY), "跨年 -1 @ $jd")
            assertEquals(jd.plusDays(31).toKx(), kx.plus(31, DateTimeUnit.DAY), "跨月 +31 @ $jd")
        }
    }

    // ---------- 7. 比较与排序（周次区间判断依赖） ----------

    @Test
    fun `比较运算符与 java_time 一致`() {
        val samples = listOf(
            "2026-01-01" to "2026-01-02",
            "2026-02-28" to "2026-03-01",
            "2024-02-29" to "2024-03-01",
            "1999-12-31" to "2000-01-01",
            "2026-10-08" to "2026-10-08",
        )
        for ((a, b) in samples) {
            val ja = JLocalDate.parse(a); val jb = JLocalDate.parse(b)
            val ka = LocalDate.parse(a); val kb = LocalDate.parse(b)
            assertEquals(ja.isBefore(jb), ka < kb, "isBefore $a/$b")
            assertEquals(ja.isAfter(jb), ka > kb, "isAfter $a/$b")
            assertEquals(ja.isEqual(jb), ka == kb, "isEqual $a/$b")
            assertEquals(ja.compareTo(jb).coerceIn(-1, 1), ka.compareTo(kb).coerceIn(-1, 1), "compareTo $a/$b")
        }
    }

    // ---------- 8. 差值与排序（maxBy / sortedBy 依赖） ----------

    @Test
    fun `天数差值与排序结果一致`() {
        val dates = listOf(
            "2026-10-08", "2026-09-01", "2024-02-29", "1900-01-01",
            "2099-12-31", "2000-02-29", "2026-01-01",
        )
        val ja = dates.map(JLocalDate::parse)
        val ka = dates.map(LocalDate::parse)

        assertEquals(ja.sorted().map { it.toString() }, ka.sorted().map { it.toString() })

        for (i in ja.indices) {
            for (j in ja.indices) {
                assertEquals(
                    ChronoUnit.DAYS.between(ja[i], ja[j]),
                    (ka[j].toEpochDays() - ka[i].toEpochDays()).toLong(),
                    "天数差 ${dates[i]} -> ${dates[j]}",
                )
            }
        }
    }

    // ---------- 9. Int 收窄边界（toEpochDays 返回 Int） ----------

    /**
     * `toEpochDays()` 返回 **Int**，而 `ChronoUnit.DAYS.between` 返回 Long。
     * 1900 年距今约 -25,567 天，2149 年才接近 Int 上限 —— 本项目取值范围内安全，
     * 但这条断言把「安全边界」显式记录下来。
     */
    @Test
    fun `toEpochDays 的 Int 范围足够覆盖本项目使用区间`() {
        val earliest = LocalDate(1900, 1, 1).toEpochDays()
        val latest = LocalDate(2099, 12, 31).toEpochDays()
        assertTrue(earliest > Int.MIN_VALUE, "1900 年已溢出 Int: $earliest")
        assertTrue(latest < Int.MAX_VALUE, "2099 年已溢出 Int: $latest")
        // Int 上限对应约 5,881,580 年，本项目无虞；这里只锁住 200 年区间
        assertEquals(-25_567, earliest)
        assertEquals(47_481, latest)
    }

    // ---------- 10. 非法输入的拒绝行为 ----------

    @Test
    fun `两套库都拒绝非法日期`() {
        val invalid = listOf(
            "2026-02-30", // 二月没有 30 日
            "2026-13-01", // 没有 13 月
            "2025-02-29", // 平年没有 2 月 29
            "not-a-date",
            "2026-1-1",   // 非 ISO 补零格式
        )
        for (text in invalid) {
            val javaFailed = runCatching { JLocalDate.parse(text) }.isFailure
            val kxFailed = runCatching { LocalDate.parse(text) }.isFailure
            assertTrue(javaFailed, "java.time 本应拒绝 $text")
            assertEquals(javaFailed, kxFailed, "对 $text 的拒绝行为不一致")
        }
    }

    // ---------- 11. 与项目真实用例一致（2026-10-08 学期推算） ----------

    @Test
    fun `真实学期推算场景一致`() {
        // 2026-10-08 是周四（dayOfWeek.value == 4），当周周一为 2026-10-05
        val today = JLocalDate.parse("2026-10-08")
        assertEquals(4, today.dayOfWeek.value, "基准日期星期不对，用例失效")

        val kxToday = LocalDate.parse("2026-10-08")
        assertEquals(4, kxToday.dayOfWeek.isoNumber)
        assertEquals("2026-10-05", today.minusDays(3).toString())
        assertEquals(
            "2026-10-05",
            kxToday.minus(kxToday.dayOfWeek.isoNumber - 1, DateTimeUnit.DAY).toString(),
        )

        // 学期起始（2026-09-01，周二）→ 首个周一 2026-08-31
        val semesterStart = JLocalDate.parse("2026-09-01")
        assertEquals("2026-08-31", semesterStart.minusDays((semesterStart.dayOfWeek.value - 1).toLong()).toString())
        val kxStart = LocalDate.parse("2026-09-01")
        assertEquals(
            "2026-08-31",
            kxStart.minus(kxStart.dayOfWeek.isoNumber - 1, DateTimeUnit.DAY).toString(),
        )

        // 第 6 周周一 → 2026-10-05
        assertEquals("2026-10-05", LocalDate.parse("2026-08-31").plus(35, DateTimeUnit.DAY).toString())
    }
}
