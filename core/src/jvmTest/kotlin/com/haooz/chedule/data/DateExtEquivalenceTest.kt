package com.haooz.chedule.data

import com.haooz.chedule.data.holiday.HolidayCountdown
import com.haooz.chedule.data.util.addExactOrNull
import com.haooz.chedule.data.util.formatChineseDate
import com.haooz.chedule.data.util.formatHourMinute
import com.haooz.chedule.data.util.formatMonthDay
import com.haooz.chedule.data.util.formatMonthDayShort
import com.haooz.chedule.data.util.formatSlashDate
import com.haooz.chedule.data.util.lengthOfMonth
import com.haooz.chedule.data.util.localDateFromEpochDays
import com.haooz.chedule.data.util.millisBetween
import com.haooz.chedule.data.util.parseHourMinute
import com.haooz.chedule.data.util.plusDays
import com.haooz.chedule.data.util.subtractExactOrNull
import com.haooz.chedule.data.util.weeksBetween
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** 用 JVM 日期与算术 API 对拍共享日期工具。 */
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
    fun `weeksBetween 与 ChronoUnit 一致`() {
        val others = sampleDates.filterIndexed { i, _ -> i % 37 == 0 }
        sampleDates.forEach { a ->
            val ja = java.time.LocalDate.parse(a.toString())
            others.forEach { b ->
                val jb = java.time.LocalDate.parse(b.toString())
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
    fun `millisUntilNextMinute 与 floorMod 推算的下一分钟间隔一致`() {
        val samples = listOf(
            0L, 1L, -1L, 59_999L, 60_000L, 60_001L, -60_000L, -60_001L,
            1_700_000_000_000L, -1_700_000_000_000L, Long.MAX_VALUE, Long.MIN_VALUE + 1,
        )
        samples.forEach { value ->
            assertEquals(
                java.lang.Math.floorMod(value, 60_000L).let { if (it == 0L) 60_000L else 60_000L - it },
                HolidayCountdown.millisUntilNextMinute(value),
                "millisUntilNextMinute($value)",
            )
        }
    }
}
