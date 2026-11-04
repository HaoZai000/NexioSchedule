package com.haooz.chedule.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 天气数据层的纯逻辑回归测试。
 *
 * 两条断言是**真实踩过的坑**，别删：
 * - `chinaWeatherText("0")`：`trimStart('0')` 会把「晴」吃成空串，晴天全页空白且不报错；
 * - `parseIsoEpochSec("...T06:46")`：Open-Meteo 的日出没有秒和时区，`substring(19)` 直接抛，
 *   表现为「三源里永远少一路」，日志里只有一行 StringIndexOutOfBounds。
 */
class WeatherFusionTest {

    @Test
    fun codeZeroIsSunnyNotEmpty() {
        assertEquals("晴", chinaWeatherText("0"))
        assertEquals("晴", chinaWeatherText("00"))
        assertEquals("晴", chinaWeatherText("d00"))
        assertEquals("多云", chinaWeatherText("1"))
        assertEquals("雾", chinaWeatherText("d18"))
        assertEquals("", chinaWeatherText(null))
        assertEquals("", chinaWeatherText(""))
    }

    @Test
    fun isoWithoutSecondsOrOffsetIsParsable() {
        // 2026-11-04T06:46 +08:00 → 纪元秒，用它与「带秒带偏移」的写法对齐
        val short = parseIsoEpochSec("2026-11-04T06:46")
        val full = parseIsoEpochSec("2026-11-04T06:46:00+08:00")
        assertEquals(full, short)
        assertEquals("06:46", clockOf("2026-11-04T06:46:00+08:00"))
        // 时区必须真的生效：东八区的 06:46 比 UTC 的 06:46 早 8 小时
        val utc = parseIsoEpochSec("2026-11-04T06:46:00Z")
        assertEquals(-8 * 3600L, full!! - utc!!)
        assertNull(parseIsoEpochSec("nope"))
    }

    @Test
    fun civilDateRoundTrip() {
        val days = daysFromCivil(2026, 11, 4)
        assertEquals(Triple(2026, 11, 4), civilFromDays(days))
        // 1970-01-01 是周四（index 4）
        assertEquals(4, weekdayIndex("1970-01-01"))
        assertEquals("2026-11-04", localDateKey(days * 86_400L, 0))
        // 跨零点：UTC 23:30 在东八区已经是第二天
        assertEquals("2026-11-05", localDateKey(days * 86_400L + 23 * 3600L + 1800L, 28_800))
    }

    @Test
    fun weightedMedianIgnoresTheOutlier() {
        // 三个源：两个说 10 度，一个抽风说 30 度 —— 中位数必须站在 10 这边
        assertEquals(10.0, WeatherFusion.weightedMedian(listOf(10.0 to 1.0, 10.0 to 0.8, 30.0 to 0.85)))
        // 单值直接返回
        assertEquals(7.0, WeatherFusion.weightedMedian(listOf(7.0 to 1.0)))
        assertNull(WeatherFusion.weightedMedian(emptyList()))
        // 权重悬殊时跟权重走：重的那一端胜出
        assertEquals(30.0, WeatherFusion.weightedMedian(listOf(10.0 to 0.1, 30.0 to 9.0)))
        assertTrue(WeatherFusion.weightedMedian(listOf(1.0 to 1.0, 2.0 to 1.0, 3.0 to 1.0))!! in 1.5..2.5)
    }

    @Test
    fun aqiLevelsFollowChineseBreakpoints() {
        assertEquals("优", aqiLevel(30))
        assertEquals("良", aqiLevel(100))
        assertEquals("轻度污染", aqiLevel(120))
        assertEquals("重度污染", aqiLevel(280))
        assertEquals("", aqiLevel(null))
    }

    @Test
    fun windHelpersDegradeQuietly() {
        assertEquals("北风", windDirFromDeg(0.0))
        assertEquals("东风", windDirFromDeg(90.0))
        assertEquals("南风", windDirFromDeg(180.0))
        assertEquals("西风", windDirFromDeg(270.0))
        // 超界角度要归一，不能数组越界
        assertEquals("北风", windDirFromDeg(720.0))
        assertEquals("", windDirFromDeg(null))
        assertEquals("静风", windPowerFromKmh(0.4))
        assertEquals("3级", windPowerFromKmh(15.0))
    }
}
