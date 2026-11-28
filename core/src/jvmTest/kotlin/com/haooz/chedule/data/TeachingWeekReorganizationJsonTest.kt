package com.haooz.chedule.data

import com.haooz.chedule.data.holiday.TeachingWeekReorganizationRule
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.util.jsonToPlainValue
import com.haooz.chedule.data.util.parseJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 对固定的备份 JSON 样本验证编解码、字段类型和往返稳定性。 */
class TeachingWeekReorganizationJsonTest {

    private fun rule(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) =
        TeachingWeekReorganizationRule(a, b, c, d, e, f)

    private val gsonBaselineOneRule =
        """{"schema_version":1,"rules":[{"firstOriginalWeek":4,"firstStartWeekday":1,""" +
            """"firstEndWeekday":3,"secondOriginalWeek":5,"secondStartWeekday":4,"secondEndWeekday":7}]}"""

    private val gsonBaselineEmptyRules = """{"schema_version":1,"rules":[]}"""

    @Test
    fun `encode 输出与 Gson 逐字一致`() {
        val rules = listOf(rule(4, 1, 3, 5, 4, 7))
        assertEquals(gsonBaselineOneRule, TeachingWeekReorganization.encode(rules, totalWeeks = 20))
        assertEquals(
            gsonBaselineEmptyRules,
            TeachingWeekReorganization.encode(emptyList(), totalWeeks = 20),
        )
    }

    @Test
    fun `decode 能读 Gson 时代的旧串`() {
        val rules = TeachingWeekReorganization.decode(gsonBaselineOneRule, totalWeeks = 20)
        assertEquals(listOf(rule(4, 1, 3, 5, 4, 7)), rules)

        val empty = TeachingWeekReorganization.decode(gsonBaselineEmptyRules, totalWeeks = 20)
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `encode 与 decode 往返一致`() {
        val cases = listOf(
            emptyList(),
            listOf(rule(4, 1, 3, 5, 4, 7)),
            listOf(rule(4, 1, 3, 5, 4, 7), rule(9, 1, 2, 10, 3, 7)),
        )
        cases.forEach { rules ->
            val encoded = TeachingWeekReorganization.encode(rules, totalWeeks = 20)
            assertEquals(rules, TeachingWeekReorganization.decode(encoded, totalWeeks = 20), "往返失败: $encoded")
            // 二次编码必须完全一致（round-trip 稳定性）
            assertEquals(
                encoded,
                TeachingWeekReorganization.encode(
                    TeachingWeekReorganization.decode(encoded, totalWeeks = 20),
                    totalWeeks = 20,
                ),
            )
        }
    }

    @Test
    fun `非法输入与 Gson 路径同样失败`() {
        // 非 JSON
        assertFailsWith<IllegalArgumentException> { TeachingWeekReorganization.decode("not json", 20) }
        // 顶层不是对象
        assertFailsWith<IllegalArgumentException> { TeachingWeekReorganization.decode("[]", 20) }
        assertFailsWith<IllegalArgumentException> { TeachingWeekReorganization.decode("null", 20) }
        assertFailsWith<IllegalArgumentException> { TeachingWeekReorganization.decode("1", 20) }
        // schema 版本不对
        assertFailsWith<IllegalArgumentException> {
            TeachingWeekReorganization.decode("""{"schema_version":2,"rules":[]}""", 20)
        }
        // rules 不是数组
        assertFailsWith<IllegalArgumentException> {
            TeachingWeekReorganization.decode("""{"schema_version":1,"rules":{}}""", 20)
        }
        // rules 元素缺字段
        assertFailsWith<IllegalArgumentException> {
            TeachingWeekReorganization.decode(
                """{"schema_version":1,"rules":[{"firstOriginalWeek":4}]}""",
                20,
            )
        }
        // 规则要求 JSON 数字，不接受引号包裹的数字。
        assertFailsWith<IllegalArgumentException> {
            TeachingWeekReorganization.decode(
                """{"schema_version":"1","rules":[]}""",
                20,
            )
        }
    }

    @Test
    fun `jsonToPlainValue 与 Gson 的 Map String Any 形状一致`() {
        val plain = jsonToPlainValue(
            parseJsonObject(
                """{"n":1,"s":"2","b":true,"z":null,"arr":[1,"x"],"obj":{"k":3},"f":1.5}""",
            ),
        ) as Map<*, *>

        assertEquals(1.0, plain["n"])
        assertTrue(plain["n"] is Double, "数字必须是 Double（与 Gson 一致）")
        assertEquals("2", plain["s"])
        assertTrue(plain["s"] is String, "带引号的数字必须是 String（与 Gson 一致）")
        assertEquals(true, plain["b"])
        assertNull(plain["z"])
        assertEquals(listOf<Any?>(1.0, "x"), plain["arr"])
        assertEquals(mapOf("k" to 3.0), plain["obj"])
        assertEquals(1.5, plain["f"])
    }
}
