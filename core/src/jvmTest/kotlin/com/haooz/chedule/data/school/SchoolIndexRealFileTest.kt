package com.haooz.chedule.data.school

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 用**真实索引文件**做的回归测试。
 *
 * 夹具 `school_index.pb`（78,911 字节）是 2026-10-08 从
 * `shiguang_warehouse` 的 `index-pb-release` 分支下载的生产数据，
 * 含 248 所学校、273 个适配器，覆盖真实的中文字段、多字节 varint 长度、
 * 各级嵌套消息 —— 这些是手写字节用例难以完全模拟的。
 *
 * ## 为什么要有这个测试
 *
 * [SchoolIndexParser] 是**手写 protobuf 解析器**，且近期把实现从
 * `java.io.InputStream` 流式改写为下标游标。解析出错不会崩溃，只会静默产出
 * 错误的学校/适配器列表，用户看到的是「教务导入里学校不见了」。
 *
 * 改写前后已用「旧实现 vs 新实现」在**这个真实文件**上做过逐字段深比较，
 * 结果完全一致（protocolVersion=2 / 248 校 / 273 适配器）。
 * 这个测试把那一结论固化为可复跑的断言。
 *
 * ## 断言为何写死具体数字
 *
 * 夹具是冻结快照，所以这些数字**永远不该变**。一旦变了，说明解析器行为发生回归。
 * 若将来确实要换夹具，必须重新做一次新旧对比，并同步更新这里的期望值。
 *
 * 放在 `jvmTest` 而非 `commonTest`：需要读文件资源，commonTest 没有文件 IO
 * （这与 `app/test` 里的 protobuf 解析逻辑无关，解析器本身仍是 commonMain）。
 */
class SchoolIndexRealFileTest {

    private fun loadFixture(): ByteArray {
        val stream = javaClass.getResourceAsStream("/school_index.pb")
        assertNotNull(stream, "找不到测试夹具 /school_index.pb")
        return stream.use { it.readBytes() }
    }

    @Test
    fun `真实索引的顶层字段与条目数`() {
        val data = loadFixture()
        assertEquals(78_911, data.size, "夹具文件被改动过，需重新做新旧解析器对比")

        val index = SchoolIndexParser.parse(data)
        assertEquals(2, index.protocolVersion)
        assertEquals("TIME_20261008145522_344", index.versionId)
        assertEquals(248, index.schools.size, "学校数量变化说明解析器有回归")
        assertEquals(
            273,
            index.schools.sumOf { it.adapters.size },
            "适配器数量变化说明解析器有回归",
        )
    }

    @Test
    fun `所有学校都有 id 与名称`() {
        val index = SchoolIndexParser.parse(loadFixture())
        val missingId = index.schools.filter { it.id.isBlank() }
        val missingName = index.schools.filter { it.name.isBlank() }
        assertTrue(missingId.isEmpty(), "有 ${missingId.size} 所学校缺 id")
        assertTrue(missingName.isEmpty(), "有 ${missingName.size} 所学校缺 name")
    }

    /** 通用工具学校是内置的，必须始终存在且带适配器。 */
    @Test
    fun `内置通用工具学校存在且有适配器`() {
        val index = SchoolIndexParser.parse(loadFixture())
        val global = index.schools.firstOrNull { it.id == "GLOBAL_TOOLS" }
        assertNotNull(global, "缺少内置的 GLOBAL_TOOLS 学校")
        assertTrue(global.adapters.isNotEmpty(), "GLOBAL_TOOLS 没有适配器")
        assertTrue(
            global.adapters.any { it.category == AdapterData.CATEGORY_GENERAL_TOOL },
            "GLOBAL_TOOLS 的适配器应属于通用工具分类",
        )
    }

    /** 真实数据里的中文必须正确解码（UTF-8 多字节）。 */
    @Test
    fun `中文名称正确解码且无乱码`() {
        val index = SchoolIndexParser.parse(loadFixture())
        val chineseNames = index.schools.map { it.name }.filter { name ->
            name.any { it.code in 0x4E00..0x9FFF }
        }
        assertTrue(chineseNames.size > 50, "中文学校名过少（${chineseNames.size}），疑似解码问题")

        // 替换字符 U+FFFD 是解码失败的典型标志
        val broken = chineseNames.filter { it.contains('\uFFFD') }
        assertTrue(broken.isEmpty(), "存在解码乱码: ${broken.take(3)}")
    }

    /** 解析结果必须自洽：id 唯一、resourceFolder 非空。 */
    @Test
    fun `学校 id 唯一且 resourceFolder 非空`() {
        val index = SchoolIndexParser.parse(loadFixture())
        val duplicated = index.schools.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue(duplicated.isEmpty(), "存在重复的学校 id: ${duplicated.take(5)}")

        val noFolder = index.schools.filter { it.resourceFolder.isBlank() }
        assertTrue(noFolder.isEmpty(), "有 ${noFolder.size} 所学校缺 resourceFolder")
    }

    /** 适配器的脚本路径与分类必须有效，否则导入时会找不到脚本。 */
    @Test
    fun `适配器字段有效`() {
        val index = SchoolIndexParser.parse(loadFixture())
        val adapters = index.schools.flatMap { it.adapters }
        assertEquals(273, adapters.size)

        val noId = adapters.filter { it.adapterId.isBlank() }
        assertTrue(noId.isEmpty(), "有 ${noId.size} 个适配器缺 adapterId")

        val noJs = adapters.filter { it.assetJsPath.isBlank() }
        assertTrue(noJs.isEmpty(), "有 ${noJs.size} 个适配器缺 assetJsPath")

        val badCategory = adapters.filter { it.category !in 0..3 }
        assertTrue(badCategory.isEmpty(), "存在越界 category: ${badCategory.take(3).map { it.category }}")
    }

    /** 解析必须可重复（无隐藏的全局状态）。 */
    @Test
    fun `重复解析结果一致`() {
        val data = loadFixture()
        val a = SchoolIndexParser.parse(data)
        val b = SchoolIndexParser.parse(data)
        assertEquals(a.versionId, b.versionId)
        assertEquals(a.schools.size, b.schools.size)
        assertEquals(
            a.schools.map { it.id },
            b.schools.map { it.id },
        )
    }
}
