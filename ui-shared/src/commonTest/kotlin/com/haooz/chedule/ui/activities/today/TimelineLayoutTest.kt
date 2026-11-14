package com.haooz.chedule.ui.activities.today

import com.haooz.chedule.data.Course
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineLayoutTest {

    private fun entry(key: String, startMinute: Int, endMinute: Int): TimelineEntry {
        val course = Course(
            id = key,
            name = key,
            classroom = "",
            teacher = "",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 1,
            startWeek = 1,
            endWeek = 20,
            weekType = Course.WEEK_TYPE_ALL,
            colorRes = 0xFF4CAF50L,
        )
        return TimelineEntry(
            key = key,
            course = course,
            startMinute = startMinute,
            endMinute = endMinute,
            source = TimelineSource.COURSE,
        )
    }

    @Test
    fun partialOverlapKeepsFullWidthOutsideAndSplitsOnlyOverlap() {
        // 12:00–14:00 的日程与 13:00–15:00 的课程，只在 13–14 点重叠。
        val layouts = buildTimelineLayouts(
            listOf(
                entry("A", 12 * 60, 14 * 60),
                entry("B", 13 * 60, 15 * 60),
            )
        )

        assertEquals(2, layouts.size)
        val a = layouts.first { it.entry.key == "A" }.blocks
        val b = layouts.first { it.entry.key == "B" }.blocks

        assertEquals(listOf(720 to 780), a.take(1).map { it.startMinute to it.endMinute })
        assertEquals(listOf(780 to 840), a.drop(1).map { it.startMinute to it.endMinute })
        assertEquals(1, a[0].laneCount)
        assertEquals(2, a[1].laneCount)
        assertEquals(0, a[1].lane)

        assertEquals(listOf(780 to 840), b.take(1).map { it.startMinute to it.endMinute })
        assertEquals(listOf(840 to 900), b.drop(1).map { it.startMinute to it.endMinute })
        assertEquals(2, b[0].laneCount)
        assertEquals(1, b[0].lane)
        assertEquals(1, b[1].laneCount)
        assertEquals(0, b[1].lane)
    }

    @Test
    fun sameSliceStateIsMergedIntoOneBlock() {
        val layouts = buildTimelineLayouts(
            listOf(
                entry("A", 8 * 60, 9 * 60),
                entry("B", 9 * 60, 10 * 60),
            )
        )

        assertEquals(1, layouts.first { it.entry.key == "A" }.blocks.size)
        assertEquals(1, layouts.first { it.entry.key == "B" }.blocks.size)
    }

    @Test
    fun tripleOverlapUsesThreeLanesOnlyInSharedRange() {
        val layouts = buildTimelineLayouts(
            listOf(
                entry("A", 10 * 60, 12 * 60),
                entry("B", 11 * 60, 13 * 60),
                entry("C", 11 * 60 + 30, 12 * 60 + 30),
            )
        )
        val laneCounts = layouts.associate { layout ->
            layout.entry.key to layout.blocks.map { it.laneCount }
        }
        assertEquals(listOf(1, 2, 3), laneCounts.getValue("A"))
        assertEquals(listOf(2, 3, 2, 1), laneCounts.getValue("B"))
        assertEquals(listOf(3, 2), laneCounts.getValue("C"))
    }
}
