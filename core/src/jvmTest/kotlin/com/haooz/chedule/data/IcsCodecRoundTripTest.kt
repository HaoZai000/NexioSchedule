package com.haooz.chedule.data

import com.haooz.chedule.data.backup.parseIcsFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ICS 导入编解码的回归测试。
 *
 * 覆盖三类曾经会在「导出 → 导入」往返中丢信息或直接炸掉的场景：
 *
 * 1. **跨午夜课**（`23:00 - 00:30`）。导出时 `DTEND` 落在次日，导入侧必须把
 *    `endTotalMinutes` 补 `+1440`；否则下游 `require(endMinutes > startMinutes)`
 *    会抛错，整份 ICS（不只这一门）导入失败。
 * 2. **自定义时间的课**。ICS 本身没有「第几节」概念，靠 `X-NEXIO-CUSTOM-*`
 *    扩展属性带回 `isCustomTime`；没有这条信息，导回来会退化成按钟点映射节次，
 *    跨午夜课更是直接对不上。
 * 3. **LOCATION 的教室/教师约定**。`LOCATION` 一个字段表达不了「教室本身含空格」，
 *    导出侧另写 `X-NEXIO-CLASSROOM/TEACHER`，导入侧要优先读它们。
 */
class IcsCodecRoundTripTest {

    /** 按 ICS 的行格式拼一份最小可解析的日历。 */
    private fun ics(vararg events: List<String>): String {
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//test//test//CN")
        events.forEach { lines += it }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n")
    }

    private fun event(vararg lines: String): List<String> =
        listOf("BEGIN:VEVENT") + lines.toList() + listOf("END:VEVENT")

    private fun coursesOf(text: String): List<Map<String, Any?>> {
        val (ok, message, data) = parseIcsFile(text)
        assertTrue(ok, "解析应成功，实际：$message")
        @Suppress("UNCHECKED_CAST")
        val courses = data!!["courses"] as List<Map<String, Any?>>
        return courses
    }

    // ── 跨午夜 ────────────────────────────────────────────────

    @Test
    fun `跨午夜课导入时结束分钟要补一天`() {
        val courses = coursesOf(
            ics(
                event(
                    "SUMMARY:夜间实验",
                    "DTSTART:20260902T230000",
                    "DTEND:20260902T003000",
                )
            )
        )
        assertEquals(1, courses.size)
        val course = courses.single()
        // 23:00 = 1380；00:30 落在次日 = 30 + 1440
        assertEquals(1380, course["startTotalMinutes"], "起始分钟应为 23:00")
        assertEquals(1470, course["endTotalMinutes"], "跨午夜结束分钟应是次日 00:30")
        assertTrue(
            (course["endTotalMinutes"] as Int) > (course["startTotalMinutes"] as Int),
            "结束必须晚于开始，否则下游 require 会炸",
        )
    }

    @Test
    fun `普通课不会因为补一天而算错`() {
        val courses = coursesOf(
            ics(
                event(
                    "SUMMARY:高等数学",
                    "DTSTART:20260902T080000",
                    "DTEND:20260902T094500",
                )
            )
        )
        val course = courses.single()
        assertEquals(480, course["startTotalMinutes"])
        assertEquals(585, course["endTotalMinutes"])
    }

    @Test
    fun `起止相同时也按次日算，不产生零长课程`() {
        val courses = coursesOf(
            ics(
                event(
                    "SUMMARY:边界",
                    "DTSTART:20260902T120000",
                    "DTEND:20260902T120000",
                )
            )
        )
        val course = courses.single()
        assertEquals(720, course["startTotalMinutes"])
        assertEquals(720 + 1440, course["endTotalMinutes"], "起止相同应视为满 24 小时，与 CourseTimeSpan 一致")
    }

    // ── 自定义时间扩展属性 ─────────────────────────────────────

    @Test
    fun `带 X-NEXIO-CUSTOM 的课被还原成自定义时间`() {
        val courses = coursesOf(
            ics(
                event(
                    "SUMMARY:夜间实验",
                    "DTSTART:20260902T230000",
                    "DTEND:20260903T003000",
                    "X-NEXIO-CUSTOM-START:23:00",
                    "X-NEXIO-CUSTOM-END:00:30",
                )
            )
        )
        val course = courses.single()
        assertEquals(true, course["isCustomTime"], "应认出这是自定义时间课")
        assertEquals("23:00", course["customStartTime"])
        assertEquals("00:30", course["customEndTime"], "跨午夜的结束钟点要原样保留，不能是 1470 分钟")
    }

    @Test
    fun `外部 ICS 的普通事件不会被误标成自定义时间`() {
        val courses = coursesOf(
            ics(
                event(
                    "SUMMARY:别人分享的课",
                    "DTSTART:20260902T080000",
                    "DTEND:20260902T094500",
                )
            )
        )
        val course = courses.single()
        assertEquals(false, course["isCustomTime"])
        assertNull(course["customStartTime"], "非自定义课不该塞钟点")
        assertNull(course["customEndTime"])
    }

    // ── LOCATION 约定 ─────────────────────────────────────────

    @Test
    fun `只有教师时前导空格表示教师而非教室`() {
        val courses = coursesOf(
            ics(
                event(
                    "SUMMARY:形势与政策",
                    "DTSTART:20260902T080000",
                    "DTEND:20260902T094500",
                    "LOCATION: 张老师",
                )
            )
        )
        val course = courses.single()
        assertEquals("", course["classroom"], "不该把教师名塞进教室")
        assertEquals("张老师", course["teacher"])
    }

    @Test
    fun `X-NEXIO 扩展属性优先于 LOCATION 拆分`() {
        val courses = coursesOf(
            ics(
                event(
                    "SUMMARY:实验课",
                    "DTSTART:20260902T080000",
                    "DTEND:20260902T094500",
                    // 教室本身带空格，LOCATION 用空格拼会拆错
                    "LOCATION:A 101 张老师",
                    "X-NEXIO-CLASSROOM:A 101",
                    "X-NEXIO-TEACHER:张老师",
                )
            )
        )
        val course = courses.single()
        assertEquals("A 101", course["classroom"], "扩展属性应保真，不被空格拆分破坏")
        assertEquals("张老师", course["teacher"])
    }

    @Test
    fun `没有扩展属性时仍按老约定拆分教室与教师`() {
        val courses = coursesOf(
            ics(
                event(
                    "SUMMARY:老数据",
                    "DTSTART:20260902T080000",
                    "DTEND:20260902T094500",
                    "LOCATION:教三201 张老师",
                )
            )
        )
        val course = courses.single()
        assertEquals("教三201", course["classroom"])
        assertEquals("张老师", course["teacher"])
    }

    @Test
    fun `没有任何 LOCATION 时两个字段都为空`() {
        val courses = coursesOf(
            ics(
                event(
                    "SUMMARY:无地点",
                    "DTSTART:20260902T080000",
                    "DTEND:20260902T094500",
                )
            )
        )
        val course = courses.single()
        assertEquals("", course["classroom"])
        assertEquals("", course["teacher"])
    }

    // ── 解析失败路径 ──────────────────────────────────────────

    @Test
    fun `没有 VEVENT 时返回失败而不是空课表`() {
        val (ok, _, data) = parseIcsFile(ics())
        assertFalse(ok, "空日历应报「未找到课程事件」而不是成功返回空列表")
        assertNull(data)
    }

    @Test
    fun `处理时区前缀不会丢 DTSTART 的值`() {
        val courses = coursesOf(
            ics(
                event(
                    "SUMMARY:带 TZID 的事件",
                    "DTSTART;TZID=Asia/Shanghai:20260902T080000",
                    "DTEND;TZID=Asia/Shanghai:20260902T094500",
                )
            )
        )
        val course = courses.single()
        assertEquals(480, course["startTotalMinutes"])
        assertEquals(585, course["endTotalMinutes"])
    }
}
