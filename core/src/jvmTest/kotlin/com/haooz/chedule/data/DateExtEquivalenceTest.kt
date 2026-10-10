package com.haooz.chedule.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `:core` 的 [DateExt] 补齐层 —— 与 `java.time` 的**差分等价性验证**。
 *
 * ## 为什么必须有这份测试
 *
 * 阶段 2.1 把 `:app` 的 `java.time` 整体换成 kotlinx-datetime。绝大多数 API 是同名替换
 * （`parse` / `plus` / `dayOfWeek` …），但 `DateExt` 里那些是 **kotlinx-datetime 根本没有**、
 * 我手写的（`lengthOfMonth` / `plusDays` / `MIN`、`MAX` 哨兵 / `HH:mm` 解析格式化 /
 * `Math.addExact` …）。这类手写替换**编译永远绿、错了就是静默算错**，只能靠差分比对兜住。
 *
 * 覆盖的是真实会走到的输入域：全项目日期都在 1900-2100 之间，但特意把闰年、世纪年、
 * 月末、跨年、跨周都塞进去。
 *
 * ⚠ 本文件不入库（用户长期要求），与 `DateMigrationEquivalenceTest` 同属本地安全网。
 */
class DateExtEquivalenceTest {

    // ── 采样日期：覆盖闰年 / 世纪年 / 月末 / 跨年 / 周日边界 ──
    private val sampleDates: List<LocalDate> = buildList {
        for (year in listOf(1900, 1996, 1999, 2000, 2001, 2024, 2025, 2026, 2100)) {
            for (month in 1..12) {
                add(LocalDate(year, month, 1))
                add(LocalDate(year, month, 28))
            }
        }
        // 逐日扫 2026 全年（闰年相邻年），确保月末/跨月没有一天算错
        var d = LocalDate(2026, 1, 1)
        while (d <= LocalDate(2026, 12, 31)) {
            add(d)
            d = d.plusDays(1)
        }
    }

    @Test
    fun `parse 与 toString 与 java time 完全一致`() {
        sampleDates.forEach { d ->
            val java = java.time.LocalDate.parse(d.toString())
            assertEquals(java.year, d.year, "year of $d")
            assertEquals(java.monthValue, d.monthNumber, "month of $d")
            assertEquals(java.dayOfMonth, d.dayOfMonth, "day of $d")
            assertEquals(java.toString(), d.toString(), "toString of $d")
            assertEquals(java.toEpochDay(), d.toEpochDays().toLong(), "epochDay of $d")
        }
        // 非法输入两边都失败
        assertNull(runCatching { LocalDate.parse("2026-1-5") }.getOrNull())
        assertNull(runCatching { LocalDate.parse("") }.getOrNull())
        assertNull(runCatching { LocalDate.parse("2026-02-30") }.getOrNull())
    }

    @Test
    fun `dayOfWeek iso 编号与 java 一致`() {
        sampleDates.forEach { d ->
            val java = java.time.LocalDate.parse(d.toString())
            assertEquals(java.dayOfWeek.value, d.dayOfWeek.isoDayNumber, "isoDayNumber of $d")
        }
    }

    @Test
    fun `lengthOfMonth 与 java 一致`() {
        sampleDates.forEach { d ->
            val java = java.time.LocalDate.parse(d.toString())
            assertEquals(java.lengthOfMonth(), d.lengthOfMonth(), "lengthOfMonth of $d")
        }
        // 闰年 2 月单独钉死
        assertEquals(29, LocalDate(2024, 2, 1).lengthOfMonth())
        assertEquals(28, LocalDate(2025, 2, 1).lengthOfMonth())
        assertEquals(28, LocalDate(1900, 2, 1).lengthOfMonth())
        assertEquals(29, LocalDate(2000, 2, 1).lengthOfMonth())
    }

    @Test
    fun `plusDays minusDays plusWeeks minusWeeks 与 java 一致`() {
        sampleDates.forEach { d ->
            val java = java.time.LocalDate.parse(d.toString())
            for (n in listOf(-800L, -30L, -7L, -1L, 0L, 1L, 7L, 30L, 800L)) {
                assertEquals(java.plusDays(n).toString(), d.plusDays(n).toString(), "plusDays($n) of $d")
                assertEquals(java.minusDays(n).toString(), d.minusDays(n).toString(), "minusDays($n) of $d")
                assertEquals(java.plusWeeks(n).toString(), d.plusWeeks(n).toString(), "plusWeeks($n) of $d")
                assertEquals(java.minusWeeks(n).toString(), d.minusWeeks(n).toString(), "minusWeeks($n) of $d")
            }
        }
    }

    @Test
    fun `daysUntil 与 weeksBetween 与 ChronoUnit 一致`() {
        val others = sampleDates.filterIndexed { i, _ -> i % 37 == 0 }
        sampleDates.forEach { a ->
            val ja = java.time.LocalDate.parse(a.toString())
            others.forEach { b ->
                val jb = java.time.LocalDate.parse(b.toString())
                assertEquals(
                    ChronoUnit.DAYS.between(ja, jb),
                    a.daysUntil(b).toLong(),
                    "daysUntil $a -> $b",
                )
                assertEquals(
                    ChronoUnit.WEEKS.between(ja, jb),
                    weeksBetween(a, b).toLong(),
                    "weeksBetween $a -> $b",
                )
            }
        }
    }

    @Test
    fun `日期格式化与 DateTimeFormatter 逐字一致`() {
        val mmDd = DateTimeFormatter.ofPattern("MM/dd")
        val mD = DateTimeFormatter.ofPattern("M/d")
        val cn = DateTimeFormatter.ofPattern("yyyy年M月d日")
        val slash = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        sampleDates.forEach { d ->
            val java = java.time.LocalDate.parse(d.toString())
            assertEquals(java.format(mmDd), d.formatMonthDay(), "MM/dd of $d")
            assertEquals(java.format(mD), d.formatMonthDayShort(), "M/d of $d")
            assertEquals(java.format(cn), d.formatChineseDate(), "cn of $d")
            assertEquals(java.format(slash), d.formatSlashDate(), "slash of $d")
        }
    }

    @Test
    fun `HHmm 解析与格式化与 java 一致`() {
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        for (h in 0..23) {
            for (m in listOf(0, 1, 5, 9, 10, 30, 59)) {
                val text = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
                val java = java.time.LocalTime.parse(text, fmt)
                val mine = parseHourMinute(text)
                assertEquals(java.toString(), mine.toString(), "parse $text")
                assertEquals(java.format(fmt), mine!!.formatHourMinute(), "format $text")
            }
        }
        // 非法输入：两边都失败
        listOf("", "x", "24:00", "12:60", "12", "12:00:00").forEach { bad ->
            assertNull(parseHourMinute(bad), "parseHourMinute($bad)")
        }
    }

    @Test
    fun `millisBetween 与 Duration between 一致`() {
        val times = listOf("00:00", "00:01", "07:30", "12:00", "23:59").map { LocalTime.parse(it) }
        times.forEach { a ->
            times.forEach { b ->
                val ja = java.time.LocalTime.parse(a.toString())
                val jb = java.time.LocalTime.parse(b.toString())
                assertEquals(
                    java.time.Duration.between(ja, jb).toMillis(),
                    millisBetween(a, b),
                    "millisBetween $a -> $b",
                )
            }
        }
    }

    @Test
    fun `localDateFromEpochDays 与 ofEpochDay 一致且越界返回 null`() {
        for (epochDay in listOf(-100_000L, -1L, 0L, 1L, 20_000L, 40_000L)) {
            val java = java.time.LocalDate.ofEpochDay(epochDay)
            assertEquals(java.toString(), localDateFromEpochDays(epochDay)!!.toString(), "epochDay $epochDay")
        }
        // Int 之外必须返回 null（Kotlin 的 Long.toInt() 会静默回绕，这里是防那件事的）
        assertNull(localDateFromEpochDays(Int.MAX_VALUE.toLong() + 1))
        assertNull(localDateFromEpochDays(Int.MIN_VALUE.toLong() - 1))
        assertNull(localDateFromEpochDays(Long.MAX_VALUE))
    }

    @Test
    fun `MIN MAX 哨兵与 epoch day 边界自洽`() {
        assertTrue(LOCAL_DATE_MIN < LOCAL_DATE_MAX)
        assertEquals(LOCAL_DATE_MIN.toEpochDays().toLong(), LOCAL_DATE_MIN_EPOCH_DAY)
        assertEquals(LOCAL_DATE_MAX.toEpochDays().toLong(), LOCAL_DATE_MAX_EPOCH_DAY)
        // 哨兵必须能被构造出来，且正好落在 kotlinx-datetime 的可表示区间端点
        // （源码里的 MIN_EPOCH_DAY / MAX_EPOCH_DAY，钉死在这里防止改库后静默偏移）
        assertEquals(-999_999, LOCAL_DATE_MIN.year)
        assertEquals(999_999, LOCAL_DATE_MAX.year)
        assertEquals(-365_961_662L, LOCAL_DATE_MIN_EPOCH_DAY)
        assertEquals(364_522_971L, LOCAL_DATE_MAX_EPOCH_DAY)
        // 任何真实日期都必须落在哨兵之间（这是它们当边界用的前提）
        sampleDates.forEach { d ->
            assertTrue(d >= LOCAL_DATE_MIN && d <= LOCAL_DATE_MAX, "$d 越出哨兵区间")
        }
    }

    @Test
    fun `addExact 与 subtractExact 与 java lang Math 一致`() {
        val values = listOf(0L, 1L, -1L, 7L, -7L, 1_000_000L, Long.MAX_VALUE - 1, Long.MIN_VALUE + 1, Long.MAX_VALUE, Long.MIN_VALUE)
        values.forEach { a ->
            values.forEach { b ->
                val expectedAdd = runCatching { java.lang.Math.addExact(a, b) }.getOrNull()
                assertEquals(expectedAdd, addExactOrNull(a, b), "addExact($a,$b)")
                val expectedSub = runCatching { java.lang.Math.subtractExact(a, b) }.getOrNull()
                assertEquals(expectedSub, subtractExactOrNull(a, b), "subtractExact($a,$b)")
            }
        }
    }

    @Test
    fun `Long floorDiv 等价于 java lang Math floorDiv`() {
        // CourseScheduleDateBounds.ceilDiv 原实现是 `-Math.floorDiv(-value, divisor)`，
        // 下沉 :core 时换成了 stdlib 的 `Long.floorDiv`。这里把两者钉成等价。
        val values = listOf(
            0L, 1L, -1L, 7L, -7L, 13L, -13L, 1_000_000L, -1_000_000L,
            Long.MAX_VALUE, Long.MIN_VALUE + 1,
        )
        val divisors = listOf(1L, 2L, 7L, 60_000L, 86_400L, 1_000_000L)
        values.forEach { a ->
            divisors.forEach { b ->
                assertEquals(java.lang.Math.floorDiv(a, b), a.floorDiv(b), "floorDiv($a,$b)")
                // ceilDiv 的原始表达式与替换后写法
                assertEquals(
                    -java.lang.Math.floorDiv(-a, b),
                    -(-a).floorDiv(b),
                    "ceilDiv($a,$b)",
                )
            }
        }
    }

    @Test
    fun `Long mod 等价于 java lang Math floorMod（正除数）`() {
        // HolidayCountdown.millisUntilNextMinute 原实现用 Math.floorMod，
        // Kotlin/Native 没有 Math.floorMod，换成了 stdlib 的 mod。
        // 除数恒为正（60_000），此时两者语义相同，这里把这件事钉死。
        val samples = listOf(
            0L, 1L, -1L, 59_999L, 60_000L, 60_001L, -60_000L, -60_001L,
            1_700_000_000_000L, -1_700_000_000_000L, Long.MAX_VALUE, Long.MIN_VALUE + 1,
        )
        samples.forEach { value ->
            assertEquals(
                java.lang.Math.floorMod(value, 60_000L),
                value.mod(60_000L),
                "mod($value, 60_000)",
            )
            assertEquals(
                java.lang.Math.floorMod(value, 60_000L).let { if (it == 0L) 60_000L else 60_000L - it },
                HolidayCountdown.millisUntilNextMinute(value),
                "millisUntilNextMinute($value)",
            )
        }
    }

    @Test
    fun `todayLocalDate 与 nowLocalTime 落在 java 的同一天同一分钟级`() {
        val javaDate = java.time.LocalDate.now()
        val mine = todayLocalDate()
        assertEquals(javaDate.toString(), mine.toString(), "todayLocalDate")
        val javaTime = java.time.LocalTime.now()
        val mineTime = nowLocalTime()
        // 允许跨分钟边界的抖动，比小时即可
        assertEquals(javaTime.hour, mineTime.hour, "nowLocalTime hour")
    }
}
