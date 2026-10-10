package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [readAtMost] 的边界验证。
 *
 * 这是迁移时**新写**的公共逻辑（原来只存在于 `ScriptRepository` 内部，用
 * `byteStream()` + `ByteArrayOutputStream` 手写），守着「异常大响应不要 OOM」这道闸，
 * 所以 off-by-one 不能靠读代码确认。
 *
 * 契约：只在读取过程中停手；超限时返回已读到字节（最多 maxBytes）并置 truncated，
 * **不抛异常**；`maxBytes <= 0` 视为不限制（返回空 + 未截断，调用方本就不该这么用）。
 */
class ReadAtMostTest {

    /** 把一段字节切成固定大小的块，模拟网络流。 */
    private class ChunkedSource(private val data: ByteArray, private val chunk: Int) {
        private var pos = 0
        var reads = 0
            private set

        fun read(buffer: ByteArray): Int {
            reads++
            if (pos >= data.size) return -1
            val n = minOf(chunk, data.size - pos, buffer.size)
            data.copyInto(buffer, 0, pos, pos + n)
            pos += n
            return n
        }
    }

    @Test
    fun `正好等于上限时不截断`() {
        val data = ByteArray(100) { it.toByte() }
        val src = ChunkedSource(data, chunk = 8192)
        val (bytes, truncated) = readAtMost(100) { src.read(it) }
        assertFalse(truncated, "正好等于上限不应算截断")
        assertTrue(bytes.contentEquals(data))
    }

    @Test
    fun `超出一个字节时截断且只保留到上限`() {
        val data = ByteArray(101) { it.toByte() }
        val src = ChunkedSource(data, chunk = 8192)
        val (bytes, truncated) = readAtMost(100) { src.read(it) }
        assertTrue(truncated)
        assertEquals(100, bytes.size)
        assertTrue(bytes.contentEquals(data.copyOf(100)))
    }

    @Test
    fun `多块累积时同样只保留到上限`() {
        val data = ByteArray(1000) { it.toByte() }
        // 每块 64 字节，需要 16 块才到 1000，上限 250 → 第 4 块内就越界
        val src = ChunkedSource(data, chunk = 64)
        val (bytes, truncated) = readAtMost(250) { src.read(it) }
        assertTrue(truncated)
        assertEquals(250, bytes.size)
        assertTrue(bytes.contentEquals(data.copyOf(250)))
    }

    @Test
    fun `恰好整块边界超限`() {
        val data = ByteArray(128) { it.toByte() }
        val src = ChunkedSource(data, chunk = 64)
        // 上限 128：两块刚好读完，不应截断
        val (bytes, truncated) = readAtMost(128) { src.read(it) }
        assertFalse(truncated)
        assertEquals(128, bytes.size)

        // 上限 127：第二块内越界 → 保留 127
        val src2 = ChunkedSource(data, chunk = 64)
        val (bytes2, truncated2) = readAtMost(127) { src2.read(it) }
        assertTrue(truncated2)
        assertEquals(127, bytes2.size)
        assertTrue(bytes2.contentEquals(data.copyOf(127)))
    }

    @Test
    fun `空流返回空且不截断`() {
        val src = ChunkedSource(ByteArray(0), chunk = 8192)
        val (bytes, truncated) = readAtMost(100) { src.read(it) }
        assertTrue(bytes.isEmpty())
        assertFalse(truncated)
    }

    @Test
    fun `数据小于上限时原样返回`() {
        val data = ByteArray(10) { (it + 1).toByte() }
        val src = ChunkedSource(data, chunk = 8192)
        val (bytes, truncated) = readAtMost(1024) { src.read(it) }
        assertFalse(truncated)
        assertTrue(bytes.contentEquals(data))
    }

    @Test
    fun `上限为零或负数视为不限制并返回空`() {
        val data = ByteArray(1000) { it.toByte() }
        for (limit in listOf(0L, -1L)) {
            val src = ChunkedSource(data, chunk = 8192)
            val (bytes, truncated) = readAtMost(limit) { src.read(it) }
            assertTrue(bytes.isEmpty(), "maxBytes=$limit 应返回空")
            assertFalse(truncated, "maxBytes=$limit 不应标记截断")
            assertEquals(0, src.reads, "maxBytes=$limit 不应去读流")
        }
    }

    @Test
    fun `超限后不再继续读流`() {
        val data = ByteArray(1_000_000) { it.toByte() }
        val src = ChunkedSource(data, chunk = 8192)
        val (bytes, truncated) = readAtMost(16 * 1024) { src.read(it) }
        assertTrue(truncated)
        assertEquals(16 * 1024, bytes.size)
        // 16KB 上限 + 8KB 块 → 最多读 3 次就会越界停手，不该把 1MB 读完
        assertTrue(src.reads <= 3, "超限后应立刻停手，实际读了 ${src.reads} 次")
    }
}
