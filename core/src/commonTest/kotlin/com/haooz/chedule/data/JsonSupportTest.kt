package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [JsonSupport] 的契约测试。
 *
 * 重点覆盖**与 org.json 的语义差异**：这些差异如果没被显式测试，迁移时很容易
 * 在某个调用点上产生「解析结果和以前不一样」的静默问题。
 */
class JsonSupportTest {

    @Test
    fun `解析对象与嵌套取值`() {
        val obj = parseJsonObject("""{"a":"x","b":2,"c":true,"d":null}""")
        assertEquals("x", obj.optString("a"))
        assertEquals(2, obj.optInt("b", -1))
        assertEquals(true, obj.optBoolean("c"))
        assertNull(obj.optJsonObject("d"), "显式 null 应视为无值")
    }

    @Test
    fun `缺键返回默认值而不抛异常`() {
        val obj = parseJsonObject("""{"a":"x"}""")
        assertEquals("", obj.optString("缺失"))
        assertEquals("fb", obj.optString("缺失", "fb"))
        assertEquals(-1, obj.optInt("缺失", -1))
        assertEquals(-1L, obj.optLong("缺失", -1L))
        assertEquals(false, obj.optBoolean("缺失"))
        assertNull(obj.optJsonObject("缺失"))
        assertNull(obj.optJsonArray("缺失"))
        assertTrue(obj.isJsonNull("缺失"))
    }

    /** org.json 的 optString 遇到 JSONObject.NULL 会返回字符串 "null"（设计缺陷）。
     *  这里约定返回默认值 —— 明确测住，避免有人以为应该模仿那个行为。 */
    @Test
    fun `显式 null 不返回字符串 null`() {
        val obj = parseJsonObject("""{"id":null}""")
        assertEquals("", obj.optString("id"), "不应返回字面量 \"null\"")
        assertEquals("fb", obj.optString("id", "fb"))
    }

    @Test
    fun `类型不符时返回默认值`() {
        val obj = parseJsonObject("""{"n":"abc","s":123}""")
        assertEquals(-1, obj.optInt("n", -1), "字符串转 Int 失败应回默认值")
        // 数字按文本取用是 org.json 的行为（optString 会强制转换），保持一致
        assertEquals("123", obj.optString("s"), "数字应强制转成文本，与 org.json 一致")
        assertNull(obj.optJsonObject("n"))
        assertNull(obj.optJsonArray("s"))
    }

    @Test
    fun `hasKey 与 isJsonNull 语义`() {
        val obj = parseJsonObject("""{"a":1,"b":null}""")
        assertTrue(obj.hasKey("a"))
        assertTrue(obj.hasKey("b"))
        assertTrue(!obj.hasKey("z"))
        assertTrue(!obj.isJsonNull("a"))
        assertTrue(obj.isJsonNull("b"), "值为 null 视为空")
        assertTrue(obj.isJsonNull("z"), "键不存在也视为空")
    }

    @Test
    fun `数组长度与下标取值`() {
        val arr = parseJsonArray("""[{"n":"a"},{"n":"b"},"plain"]""")
        assertEquals(3, arr.length())
        assertEquals("a", arr.optJsonObject(0)?.optString("n"))
        assertEquals("b", arr.optJsonObject(1)?.optString("n"))
        assertNull(arr.optJsonObject(2), "下标 2 是字符串，取对象应为 null")
        assertNull(arr.optJsonObject(99), "越界应为 null 而不是抛异常")
        assertEquals("plain", arr.optString(2))
    }

    /** 后端分页接口的字段形状：records 数组 + has_more 布尔。 */
    @Test
    fun `还原后端分页响应的解析`() {
        val body = """
            {"records":[{"nickname":"甲","amount":"￥1.00","time":"2026-10-08","remark":""}],
             "total":1,
             "has_more":true}
        """.trimIndent()

        val obj = parseJsonObject(body)
        val records = obj.optJsonArray("records")
        assertNotNull(records)
        assertEquals(1, records.length())
        val first = records.optJsonObject(0)
        assertNotNull(first)
        assertEquals("甲", first.optString("nickname"))
        assertEquals("￥1.00", first.optString("amount"))
        assertEquals("", first.optString("remark"))
        assertTrue(obj.optBoolean("has_more"))
    }

    /** 空数组 `?: JSONArray()` 的迁移路径。 */
    @Test
    fun `缺 records 时可回退到空数组`() {
        val obj = parseJsonObject("""{"total":0}""")
        val records = obj.optJsonArray("records") ?: jsonArrayOf()
        assertEquals(0, records.length())
    }

    @Test
    fun `构造对象与数组`() {
        val obj = jsonObjectOf(
            "name" to "数据结构",
            "week" to 3,
            "on" to true,
            "nested" to mapOf("k" to "v"),
        )
        assertEquals("数据结构", obj.optString("name"))
        assertEquals(3, obj.optInt("week", 0))
        assertTrue(obj.optBoolean("on"))
        assertEquals("v", obj.optJsonObject("nested")?.optString("k"))

        val arr = jsonArrayOf("a", 1, true)
        assertEquals(3, arr.length())
        assertEquals("a", arr.optString(0))
    }

    /** 中文与 emoji 必须原样往返（项目里有「加油做好Nexio课程表👍️」这类内容）。 */
    @Test
    fun `中英文与 emoji 往返不损坏`() {
        val text = "加油做好Nexio课程表👍️"
        val obj = jsonObjectOf("remark" to text)
        assertEquals(text, obj.optString("remark"))
        assertEquals(text, parseJsonObject(obj.toString()).optString("remark"))
    }

    @Test
    fun `非法 JSON 抛异常而非静默返回空对象`() {
        assertFailsWith<Exception> {
            parseJsonObject("{不是合法 json")
        }
    }

    /** 后端新增字段时不应导致解析失败。 */
    @Test
    fun `忽略未知字段`() {
        val obj = parseJsonObject("""{"a":"x","brand_new_field":{"deep":[1,2,3]}}""")
        assertEquals("x", obj.optString("a"))
    }

    // ─────────────────────────────────────────────────────────────────
    // 以下三组是 org.json 的**强制转型语义**，早期版本没对齐，会静默回落默认值。
    // :app 里还剩 5 个文件、88 处 org.json 调用，任何一处落到这些场景都会出错。
    // ─────────────────────────────────────────────────────────────────

    /**
     * org.json 的 `optString` 落在嵌套 object / array 上时返回**其 JSON 文本**
     * （`JSON.toString(Object)`），而不是空串。
     */
    @Test
    fun `optString 落在嵌套结构上返回 JSON 文本`() {
        val obj = parseJsonObject("""{"o":{"k":1},"a":[1,2],"s":"x"}""")
        assertEquals("""{"k":1}""", obj.optString("o"))
        assertEquals("""[1,2]""", obj.optString("a"))
        assertEquals("x", obj.optString("s"))
    }

    /**
     * org.json 的 `optInt` / `optLong` 走 `(int) Double.parseDouble(s)`：
     * **字符串数字可转、小数会截断、科学计数法可解析**。
     * 严格解析（`intOrNull`）会把 `"34"` 判为失败并静默回默认值。
     */
    @Test
    fun `数字转型对齐 org_json 的 Double 中转语义`() {
        val obj = parseJsonObject(
            """{"str":"34","dec":"34.9","sci":"1e3","real":34,"realDec":34.9,"bad":"abc"}""",
        )
        // 字符串形式的整数/小数/科学计数法
        assertEquals(34, obj.optInt("str", -1), "字符串 \"34\" 应转成 34")
        assertEquals(34, obj.optInt("dec", -1), "org.json 先转 double 再截断，\"34.9\" -> 34")
        assertEquals(1000, obj.optInt("sci", -1), "科学计数法 \"1e3\" -> 1000")
        // 真正的数字
        assertEquals(34, obj.optInt("real", -1))
        assertEquals(34, obj.optInt("realDec", -1), "34.9 -> 34")
        assertEquals(34L, obj.optLong("str", -1L))
        assertEquals(1000L, obj.optLong("sci", -1L))
        assertEquals(34.9, obj.optDouble("realDec", -1.0))
        assertEquals(34.9, obj.optDouble("dec", -1.0))
        // 无法解析时回默认值
        assertEquals(-1, obj.optInt("bad", -1))
        assertEquals(-1L, obj.optLong("bad", -1L))
    }

    /**
     * org.json 的 `optBoolean` 对字符串用 `equalsIgnoreCase`，
     * 且**只认 true/false**（数字、"1"、其他都回默认值）。
     */
    @Test
    fun `optBoolean 忽略大小写且只认 true-false`() {
        val obj = parseJsonObject(
            """{"low":"true","up":"TRUE","mix":"False","num":1,"one":"1","no":"yes","b":true}""",
        )
        assertTrue(obj.optBoolean("low", false))
        assertTrue(obj.optBoolean("up", false), "应忽略大小写")
        assertFalse(obj.optBoolean("mix", true), "\"False\" 应解析为 false 而非回默认值")
        assertTrue(obj.optBoolean("b", false))
        // 数字与 "1" 不是布尔（org.json 的 toBoolean 不处理数字）
        assertFalse(obj.optBoolean("num", false))
        assertFalse(obj.optBoolean("one", false))
        assertFalse(obj.optBoolean("no", false))
        assertEquals(true, obj.optBoolean("no", true), "无法解析时应回默认值")
    }

    /** 数组侧的同名强制转型也要对齐。 */
    @Test
    fun `数组下标取值也做强制转型`() {
        val arr = parseJsonArray("""["34",{"k":1},true]""")
        assertEquals("34", arr.optString(0))
        assertEquals("""{"k":1}""", arr.optString(1), "嵌套对象应返回 JSON 文本")
        assertEquals("true", arr.optString(2), "布尔应转文本")
    }
}
