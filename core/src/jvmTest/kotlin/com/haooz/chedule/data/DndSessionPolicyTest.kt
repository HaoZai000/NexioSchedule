package com.haooz.chedule.data

import com.haooz.chedule.data.util.DndSessionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DndSessionPolicyTest {

    private fun hm(s: String): Int {
        val p = s.split(":")
        return p[0].toInt() * 60 + p[1].toInt()
    }

    @Test
    fun `主通道仍等于写入值或目标值才交还`() {
        assertTrue(DndSessionPolicy.ownsPrimaryChannel(2, 2, 2))
        assertTrue(DndSessionPolicy.ownsPrimaryChannel(2, 3, 2))
        assertFalse(DndSessionPolicy.ownsPrimaryChannel(1, 2, 2))
        assertFalse(DndSessionPolicy.ownsPrimaryChannel(null, 2, 2))
    }

    @Test
    fun `次通道未观测或读不到都算没被用户动过`() {
        assertFalse(DndSessionPolicy.secondaryTouched(null, 1))
        assertFalse(DndSessionPolicy.secondaryTouched(1, null))
        assertFalse(DndSessionPolicy.secondaryTouched(1, 1))
        assertTrue(DndSessionPolicy.secondaryTouched(1, 2))
    }

    @Test
    fun `普通课窗口`() {
        val start = hm("08:00")
        val end = hm("08:45")
        assertEquals(end, DndSessionPolicy.windowEndOffset(start, end, hm("08:30")))
        assertEquals(end, DndSessionPolicy.windowEndOffset(start, end, hm("08:00")))
        assertNull(DndSessionPolicy.windowEndOffset(start, end, hm("07:59")))
        assertNull(DndSessionPolicy.windowEndOffset(start, end, hm("08:45")))
        assertNull(DndSessionPolicy.windowEndOffset(start, start, hm("08:30")))
    }

    @Test
    fun `跨零点课今晚落在次日 零点后的尾巴落在今天`() {
        val start = hm("23:00")
        val end = hm("00:30")
        assertEquals(end + 1440, DndSessionPolicy.windowEndOffset(start, end, hm("23:30")))
        assertEquals(end + 1440, DndSessionPolicy.windowEndOffset(start, end, hm("23:59")))
        assertEquals(end, DndSessionPolicy.windowEndOffset(start, end, hm("00:10")))
        assertNull(DndSessionPolicy.windowEndOffset(start, end, hm("22:59")))
        assertNull(DndSessionPolicy.windowEndOffset(start, end, hm("00:30")))
    }
}
