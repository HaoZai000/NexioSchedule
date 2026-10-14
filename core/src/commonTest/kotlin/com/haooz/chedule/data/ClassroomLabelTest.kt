package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [ClassroomLabel] 的行为锁定。
 *
 * 前三组样例取自真实反馈：`2号综合楼B3027` 被系统截成 `2号综合楼B3`（issue #69），
 * `新校区J02西414` 被截成 `新校区J02西4`（前缀把房号挤出去了）。
 * 自动模式的规则一旦改动，这里会立刻翻车 —— 这是刻意的。
 */
class ClassroomLabelTest {

    private fun auto(raw: String, slotWidth: Int = ClassroomLabel.SLOT_WIDTH): String =
        ClassroomLabel.apply(raw, ClassroomLabel.rule(mode = ClassroomLabel.MODE_AUTO, slotWidth = slotWidth))

    private fun custom(raw: String, pattern: String, replacement: String): String =
        ClassroomLabel.apply(
            raw,
            ClassroomLabel.rule(mode = ClassroomLabel.MODE_CUSTOM, pattern = pattern, replacement = replacement),
        )

    @Test
    fun `原样模式不改写地盘`() {
        val raw = "新校区J02西414"
        assertEquals(raw, ClassroomLabel.apply(raw, ClassroomLabel.rule()))
    }

    @Test
    fun `自动模式剥掉校区前缀`() {
        // 前缀 6 个半角去掉后，楼栋 J02 与房号西414 全在，宽度 8 不再触发系统截断
        assertEquals("J02西414", auto("新校区J02西414"))
    }

    @Test
    fun `自动模式超宽时保留尾部房号`() {
        // 系统会从尾部截成「2号综合楼B3」，这里主动丢头保尾，房号完整
        assertEquals("综合楼B3027", auto("2号综合楼B3027"))
    }

    @Test
    fun `自动模式先剥前缀再截尾部`() {
        // 「东校区」剥掉后仍超宽（14 → 12），再丢最前面的「第」
        assertEquals("二教学楼A302", auto("东校区第二教学楼A302"))
    }

    @Test
    fun `自动模式不动本来就放得下的地点`() {
        assertEquals("博A201", auto("博A201"))
        assertEquals("教三201", auto("教三201"))
    }

    @Test
    fun `自动模式支持括号与分隔符包裹的前缀`() {
        assertEquals("J02西414", auto("（新校区）J02西414"))
        assertEquals("博学楼A302", auto("[南校区]博学楼A302"))
        assertEquals("博学楼A302", auto("东校区-博学楼A302"))
        assertEquals("博学楼A302", auto("新校区·博学楼A302"))
    }

    @Test
    fun `自动模式整串就是一个校区名时保留原文`() {
        // 剥完剩下空串没有意义，退回原文而不是显示空白
        assertEquals("新校区", auto("新校区"))
        assertEquals("南校区", auto("南校区"))
    }

    @Test
    fun `自动模式全角字母数字转半角`() {
        assertEquals("B3027", auto("Ｂ３０２７"))
    }

    @Test
    fun `自动模式丢掉空白`() {
        assertEquals("博A201", auto("  博  A201 "))
        assertEquals("3号楼201", auto("3 号 楼 201"))
    }

    @Test
    fun `自动模式限宽为零表示不截断`() {
        // 剥前缀是另一条规则，照常生效；只是不再按宽度丢头
        assertEquals("J02西414", auto("新校区J02西414", slotWidth = 0))
        assertEquals("2号综合楼B3027", auto("2号综合楼B3027", slotWidth = 0))
    }

    @Test
    fun `自动模式空串与空白串原样返回`() {
        assertEquals("", auto(""))
        assertEquals("   ", auto("   "))
    }

    @Test
    fun `自定义模式按正则替换`() {
        assertEquals("J02西414", custom("新校区J02西414", "^新校区", ""))
    }

    @Test
    fun `自定义模式支持捕获组`() {
        assertEquals("B3027", custom("2号综合楼B3027", "^.*?([A-Za-z]\\d{3,4})$", "$1"))
    }

    @Test
    fun `自定义模式不追加宽度截断`() {
        // 用户写清楚了要显示什么，App 不再补一刀 —— 结果超宽就超宽，由 UI 预览里提示
        assertEquals("二号综合楼B3027", custom("2号综合楼B3027", "^2号", "二号"))
    }

    @Test
    fun `自定义模式正则不合法时退回原文`() {
        assertEquals("博A201", custom("博A201", "([", ""))
        assertEquals("博A201", custom("博A201", "", "x"))
        assertEquals("博A201", custom("博A201", "a".repeat(ClassroomLabel.MAX_PATTERN_LENGTH + 1), "x"))
    }

    @Test
    fun `读取偏好默认原样`() {
        val store = InMemoryKeyValueStore()
        assertEquals(ClassroomLabelRule(), ClassroomLabel.read(store))
    }

    @Test
    fun `读取偏好取用户设置`() {
        val store = InMemoryKeyValueStore(
            mapOf(
                ClassroomLabel.KEY_MODE to ClassroomLabel.MODE_CUSTOM,
                ClassroomLabel.KEY_PATTERN to "^新校区",
                ClassroomLabel.KEY_REPLACEMENT to "",
            ),
        )
        assertEquals(
            ClassroomLabelRule(
                mode = ClassroomLabel.MODE_CUSTOM,
                pattern = "^新校区",
                replacement = "",
            ),
            ClassroomLabel.read(store),
        )
    }

    @Test
    fun `宽度按全角半角计算`() {
        assertEquals(0, ClassroomLabel.slotDisplayWidth(""))
        assertEquals(5, ClassroomLabel.slotDisplayWidth("A3027"))
        assertEquals(6, ClassroomLabel.slotDisplayWidth("博A201"))
        assertEquals(8, ClassroomLabel.slotDisplayWidth("J02西414"))
    }

    @Test
    fun `正则合法性检查`() {
        assertNull(ClassroomLabel.patternError("^新校区"))
        assertNull(ClassroomLabel.patternError(""))
        assertNotNull(ClassroomLabel.patternError("(["))
        assertNotNull(ClassroomLabel.patternError("a".repeat(ClassroomLabel.MAX_PATTERN_LENGTH + 1)))
    }
}
