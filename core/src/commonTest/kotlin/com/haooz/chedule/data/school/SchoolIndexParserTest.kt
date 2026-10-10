package com.haooz.chedule.data.school

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SchoolIndexParser] 的验证。
 *
 * 这是**手写的 protobuf 解析器**，且刚做过改造（`java.io.InputStream` → 下标游标）。
 * 解析错误不会崩，只会静默产出错的学校/适配器列表 —— 用户看到的是「教务导入列表空了」，
 * 所以这里用手写字节序列把每种 wire type、varint 长度边界、截断情形都钉死。
 *
 * protobuf 编码回顾：
 * - tag = `(fieldNumber shl 3) or wireType`
 * - varint 每字节 7 位数据，最高位为「后续还有」标志
 * - length-delimited(2)：tag + varint 长度 + 字节
 */
class SchoolIndexParserTest {

    // ---- protobuf 编码辅助 ----

    private fun varint(value: Long): ByteArray {
        val out = ArrayList<Byte>()
        var v = value
        while (true) {
            if (v and -128L == 0L) {
                out.add(v.toByte())
                break
            }
            out.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        return out.toByteArray()
    }

    private fun tag(field: Int, wire: Int): ByteArray = varint(((field shl 3) or wire).toLong())

    private fun vint(field: Int, value: Long): ByteArray = tag(field, 0) + varint(value)

    private fun lenDelim(field: Int, bytes: ByteArray): ByteArray =
        tag(field, 2) + varint(bytes.size.toLong()) + bytes

    private fun str(field: Int, s: String): ByteArray = lenDelim(field, s.encodeToByteArray())

    private fun bytes(vararg parts: ByteArray): ByteArray {
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var p = 0
        for (part in parts) {
            part.copyInto(out, p)
            p += part.size
        }
        return out
    }

    // ---- 测试 ----

    @Test
    fun `空数据返回默认值`() {
        val r = SchoolIndexParser.parse(ByteArray(0))
        assertEquals(0, r.protocolVersion)
        assertEquals("", r.versionId)
        assertTrue(r.schools.isEmpty())
    }

    @Test
    fun `解析顶层字段`() {
        val data = bytes(vint(1, 1), str(2, "v2026.10"))
        val r = SchoolIndexParser.parse(data)
        assertEquals(1, r.protocolVersion)
        assertEquals("v2026.10", r.versionId)
    }

    /** varint 跨字节：300 需要两字节（0xAC 0x02）。 */
    @Test
    fun `多字节 varint 正确还原`() {
        for (v in listOf(0L, 1L, 127L, 128L, 300L, 16383L, 16384L, 2_000_000_000L)) {
            val r = SchoolIndexParser.parse(vint(1, v))
            assertEquals(v.toInt(), r.protocolVersion, "varint $v 还原错误")
        }
    }

    @Test
    fun `解析单个学校`() {
        val school = bytes(
            str(1, "hdu"),
            str(2, "杭州电子科技大学"),
            str(3, "H"),
            str(4, "schools/hdu"),
        )
        val data = bytes(vint(1, 2), lenDelim(3, school))

        val r = SchoolIndexParser.parse(data)
        assertEquals(1, r.schools.size)
        val s = r.schools[0]
        assertEquals("hdu", s.id)
        assertEquals("杭州电子科技大学", s.name)
        assertEquals("H", s.initial)
        assertEquals("schools/hdu", s.resourceFolder)
    }

    @Test
    fun `解析完整三层嵌套`() {
        val adapter = bytes(
            str(1, "jwgl-v4"),
            str(2, "教务系统 v4"),
            vint(3, 2),
            str(4, "assets/jwgl_v4.js"),
            str(5, "https://example.com/import"),
            str(6, "适用于本科教务"),
            str(7, "某维护者"),
        )
        val school = bytes(
            str(1, "zju"),
            str(2, "浙江大学"),
            str(3, "Z"),
            str(4, "schools/zju"),
            lenDelim(5, adapter),
        )
        val data = bytes(vint(1, 3), str(2, "v1"), lenDelim(3, school))

        val r = SchoolIndexParser.parse(data)
        assertEquals(3, r.protocolVersion)
        assertEquals("v1", r.versionId)
        assertEquals(1, r.schools.size)

        val a = r.schools[0].adapters.single()
        assertEquals("jwgl-v4", a.adapterId)
        assertEquals("教务系统 v4", a.adapterName)
        assertEquals(AdapterData.CATEGORY_BACHELOR, a.category)
        assertEquals("assets/jwgl_v4.js", a.assetJsPath)
        assertEquals("https://example.com/import", a.importUrl)
        assertEquals("适用于本科教务", a.description)
        assertEquals("某维护者", a.maintainer)
    }

    /** 缺 `import_url` 时应为 null（而不是空串）—— 调用方据此判断是否可直连导入。 */
    @Test
    fun `缺 import_url 时为 null`() {
        val adapter = bytes(str(1, "x"), str(2, "y"))
        val school = bytes(str(1, "s"), lenDelim(5, adapter))
        val r = SchoolIndexParser.parse(lenDelim(3, school))
        assertNull(r.schools.single().adapters.single().importUrl)
    }

    @Test
    fun `多个学校与多个适配器按序保留`() {
        val a1 = bytes(str(1, "a1"))
        val a2 = bytes(str(1, "a2"))
        val s1 = bytes(str(1, "s1"), lenDelim(5, a1), lenDelim(5, a2))
        val s2 = bytes(str(1, "s2"))
        val data = bytes(lenDelim(3, s1), lenDelim(3, s2))

        val r = SchoolIndexParser.parse(data)
        assertEquals(listOf("s1", "s2"), r.schools.map { it.id })
        assertEquals(listOf("a1", "a2"), r.schools[0].adapters.map { it.adapterId })
        assertTrue(r.schools[1].adapters.isEmpty())
    }

    /** 后端加字段时客户端必须能跳过，否则整个索引解析失败。 */
    @Test
    fun `跳过未知字段的四种 wire type`() {
        val data = bytes(
            vint(1, 1),
            vint(99, 12345),               // varint
            tag(98, 1) + ByteArray(8),     // 64bit
            lenDelim(97, "ignored".encodeToByteArray()), // length-delimited
            tag(96, 5) + ByteArray(4),     // 32bit
            str(2, "ok"),
        )
        val r = SchoolIndexParser.parse(data)
        assertEquals(1, r.protocolVersion)
        assertEquals("ok", r.versionId)
    }

    @Test
    fun `未知 wire type 抛异常`() {
        // wire type 3/4 是已废弃的 group，本项目未支持
        val data = tag(50, 3)
        assertFailsWith<IllegalArgumentException> { SchoolIndexParser.parse(data) }
    }

    @Test
    fun `截断的 varint 抛异常`() {
        // 0x80 表示「还有后续字节」，但没有后续
        assertFailsWith<IllegalArgumentException> {
            SchoolIndexParser.parse(byteArrayOf(0x80.toByte()))
        }
    }

    /** 声明长度超过实际字节数 —— 旧实现在这里会静默读短，必须抛。 */
    @Test
    fun `声明的长度超过剩余字节时抛异常`() {
        val data = tag(2, 2) + varint(100L) + "short".encodeToByteArray()
        assertFailsWith<IllegalArgumentException> { SchoolIndexParser.parse(data) }
    }

    /** 篡改的巨型长度不得直接分配 8MB+ 内存。 */
    @Test
    fun `超长字段长度被拒绝`() {
        // 100MB 的 length-delimited 字段
        val data = tag(2, 2) + varint(100L * 1024 * 1024)
        assertFailsWith<IllegalArgumentException> { SchoolIndexParser.parse(data) }
    }

    @Test
    fun `UTF-8 中文正确解码`() {
        val school = bytes(str(1, "hdu"), str(2, "杭州电子科技大学"), str(3, "杭"))
        val r = SchoolIndexParser.parse(lenDelim(3, school))
        val s = r.schools.single()
        assertEquals("杭州电子科技大学", s.name)
        assertEquals("杭", s.initial)
    }

    /** 嵌套消息边界必须严格：子消息解析不能越界读到父层字节。 */
    @Test
    fun `嵌套消息不会越界读取父层数据`() {
        val adapter = bytes(str(1, "a"))
        val school = bytes(str(1, "s"), lenDelim(5, adapter))
        // 学校后面还跟着顶层字段；子解析不能把它吃掉
        val data = bytes(lenDelim(3, school), str(2, "tail"))

        val r = SchoolIndexParser.parse(data)
        assertEquals("tail", r.versionId, "子消息解析越界，吃掉了后续顶层字段")
        assertEquals("a", r.schools.single().adapters.single().adapterId)
    }

    @Test
    fun `同一字段重复出现时后值覆盖前值`() {
        val data = bytes(vint(1, 1), vint(1, 7))
        assertEquals(7, SchoolIndexParser.parse(data).protocolVersion)
    }

    @Test
    fun `category 的四个取值可正确还原`() {
        for (c in 0..3) {
            val adapter = bytes(str(1, "x"), vint(3, c.toLong()))
            val r = SchoolIndexParser.parse(lenDelim(3, bytes(lenDelim(5, adapter))))
            assertEquals(c, r.schools.single().adapters.single().category)
        }
    }
}
