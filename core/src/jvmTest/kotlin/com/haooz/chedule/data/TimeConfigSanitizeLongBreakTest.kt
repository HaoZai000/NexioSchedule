package com.haooz.chedule.data

import com.haooz.chedule.data.model.TimeConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [TimeConfig.sanitize] 对大课间（longBreak）的夹取。
 *
 * 背景：`sanitize` 一直夹 `classDuration` 与 `shortBreak`，唯独 longBreak 三个字段
 * 原样透传。导入/备份/手工改 JSON 带进 `-5` 或 `999` 会直接进渲染，时间会被算到
 * 负分钟或十几个小时后。0 是**合法值**（= 不休息，issue #89 要求能设 0），
 * 所以下限是 0 而不是正数。
 */
class TimeConfigSanitizeLongBreakTest {

    private fun sanitize(
        morning: Int = 20,
        afternoon: Int = 20,
        evening: Int = 20,
    ): TimeConfig = TimeConfig.sanitize(
        id = 1L,
        raw = TimeConfig(
            id = 0L,
            longBreakMorning = morning,
            longBreakAfternoon = afternoon,
            longBreakEvening = evening,
        ),
    )

    @Test
    fun `负数被夹到 0`() {
        val config = sanitize(morning = -5, afternoon = -1, evening = -100)
        assertEquals(0, config.longBreakMorning)
        assertEquals(0, config.longBreakAfternoon)
        assertEquals(0, config.longBreakEvening)
    }

    @Test
    fun `超过 60 被夹到 60`() {
        val config = sanitize(morning = 999, afternoon = 61, evening = Int.MAX_VALUE)
        assertEquals(60, config.longBreakMorning)
        assertEquals(60, config.longBreakAfternoon)
        assertEquals(60, config.longBreakEvening)
    }

    @Test
    fun `0 是合法值，不被改写`() {
        val config = sanitize(morning = 0, afternoon = 0, evening = 0)
        assertEquals(0, config.longBreakMorning, "0 = 不休息，是 issue #89 要支持的语义")
        assertEquals(0, config.longBreakAfternoon)
        assertEquals(0, config.longBreakEvening)
    }

    @Test
    fun `正常值原样保留`() {
        val config = sanitize(morning = 20, afternoon = 15, evening = 30)
        assertEquals(20, config.longBreakMorning)
        assertEquals(15, config.longBreakAfternoon)
        assertEquals(30, config.longBreakEvening)
    }

    @Test
    fun `边界值 0 与 60 都保留`() {
        val low = sanitize(morning = 0)
        val high = sanitize(morning = 60)
        assertEquals(0, low.longBreakMorning)
        assertEquals(60, high.longBreakMorning)
    }
}
