package com.haooz.chedule.data

import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.CourseTimeResolver
import com.haooz.chedule.data.model.PeriodTimeSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 上课提醒节次范围的判定：范围先换算成「起始节上课 ~ 结束节下课」的时间窗，
 * 再按课程自己的上课时间判断（自定义时间的课同样受约束）。
 */
class ReminderSectionFilterTest {

    private val morning = mapOf(
        1 to "08:00-08:45",
        2 to "08:55-09:40",
        3 to "10:00-10:45",
        4 to "10:55-11:40",
    )
    private val afternoon = mapOf(
        1 to "14:00-14:45",
        2 to "14:55-15:40",
    )

    private class FakeTimeSource(
        private val morning: Map<Int, String>,
        private val afternoon: Map<Int, String>,
        private val evening: Map<Int, String> = emptyMap(),
    ) : PeriodTimeSource {
        override fun getPeriodTimes(period: String): Map<Int, String> = when (period) {
            "morning" -> morning
            "afternoon" -> afternoon
            "evening" -> evening
            else -> emptyMap()
        }

        override fun getMorningSections(): Int = morning.size

        override fun getAfternoonSections(): Int = afternoon.size
    }

    private fun source() = FakeTimeSource(morning, afternoon)

    private fun course(
        startSection: Int,
        endSection: Int = startSection,
        customStart: String? = null,
        customEnd: String? = null,
    ) = Course(
        id = "c",
        name = "课",
        classroom = "",
        teacher = "",
        dayOfWeek = 1,
        startSection = startSection,
        endSection = endSection,
        startWeek = 1,
        endWeek = 16,
        weekType = 0,
        colorRes = 0,
        isCustomTime = customStart != null,
        customStartTime = customStart,
        customEndTime = customEnd,
    )

    private fun inRange(course: Course, start: Int, end: Int, src: PeriodTimeSource = source()) =
        CourseTimeResolver.isInReminderSections(course, src, start, end)

    @Test
    fun `不限范围时全部提醒`() {
        assertTrue(inRange(course(1), 1, 0))
        // 自定义时间的课也不受限制
        assertTrue(inRange(course(1, customStart = "23:00", customEnd = "23:45"), 1, 0))
    }

    @Test
    fun `普通课随节次落入时间窗`() {
        assertTrue(inRange(course(1), 1, 4))
        assertTrue(inRange(course(3), 1, 4))
        assertTrue(inRange(course(4), 1, 4))
        assertFalse(inRange(course(5), 1, 4))
        assertTrue(inRange(course(4), 2, 4))
        assertFalse(inRange(course(1), 2, 4))
    }

    @Test
    fun `自定义时间的课按上课时间落在时间窗内`() {
        // 1..4 节 = 08:00 ~ 11:40
        assertTrue(inRange(course(1, customStart = "08:00", customEnd = "08:40"), 1, 4))
        assertTrue(inRange(course(1, customStart = "10:30", customEnd = "11:10"), 1, 4))
        assertTrue(inRange(course(1, customStart = "11:40", customEnd = "12:30"), 1, 4))
        assertFalse(inRange(course(1, customStart = "07:30", customEnd = "08:10"), 1, 4))
        assertFalse(inRange(course(1, customStart = "11:41", customEnd = "12:30"), 1, 4))
    }

    @Test
    fun `结束节取下课时间`() {
        // 1..2 节 = 08:00 ~ 09:40
        assertTrue(inRange(course(1, customStart = "09:39", customEnd = "10:20"), 1, 2))
        assertFalse(inRange(course(1, customStart = "09:41", customEnd = "10:20"), 1, 2))
    }

    @Test
    fun `结束节为 0 表示不设上限`() {
        // 第 5 节（下午第一节）14:00 起
        assertTrue(inRange(course(1, customStart = "14:10", customEnd = "14:50"), 5, 0))
        assertFalse(inRange(course(1, customStart = "12:00", customEnd = "12:40"), 5, 0))
        assertFalse(inRange(course(4), 5, 0))
        assertTrue(inRange(course(6), 5, 0))
    }

    @Test
    fun `节次时间缺失时退回按节次判断`() {
        val empty = FakeTimeSource(emptyMap(), emptyMap())
        assertTrue(inRange(course(3), 2, 0, empty))
        assertFalse(inRange(course(1), 2, 0, empty))
        // 退化路径下自定义时间的课定位不到节次，保持提醒
        assertTrue(inRange(course(1, customStart = "23:00", customEnd = "23:45"), 2, 0, empty))
    }
}
