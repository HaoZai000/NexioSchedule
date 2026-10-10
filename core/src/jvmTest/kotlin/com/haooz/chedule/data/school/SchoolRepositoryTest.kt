package com.haooz.chedule.data.school

import com.haooz.chedule.data.AppFiles
import com.haooz.chedule.data.InMemoryAppFile
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [SchoolRepository] 的迁移等价性验证。
 *
 * 这次改动把它的文件访问从 `Context.filesDir` + `java.io.File` 换成了注入的 [AppFiles]，
 * **涉及真实数据落盘**，所以要验证的不是「能编译」，而是：
 * - 路径逐字未变（`repo/index/school_index.pb`、`repo/schools/resources`）
 * - 「本地不存在才从内置资源引导」的分支仍正确
 * - 引导后能解析出与直接解析同样的结果
 *
 * 用真实生产索引做夹具（与 SchoolIndexRealFileTest 同一份 78,911 字节快照）。
 */
class SchoolRepositoryTest {

    private val indexAssetPath = "eduloader/school_index.pb"

    private fun fixtureBytes(): ByteArray {
        val stream = javaClass.getResourceAsStream("/school_index.pb")
        assertNotNull(stream, "找不到测试夹具 /school_index.pb")
        return stream.use { it.readBytes() }
    }

    // ─────────────── 内置索引引导 ───────────────

    @Test
    fun `首次使用从内置资源引导并成功解析`() {
        val bytes = fixtureBytes()
        val root = InMemoryAppFile.root()
        AppFiles.init(root, readAsset = { path ->
            assertEquals(indexAssetPath, path, "内置资源路径必须与迁移前一致")
            bytes
        })

        val repo = SchoolRepository()
        val index = repo.loadIndex()
        assertNotNull(index, "引导后应能解析索引")
        assertEquals(2, index.protocolVersion)
        assertEquals("TIME_20261008145522_344", index.versionId)
        assertEquals(248, index.schools.size)
    }

    /** 路径必须逐字不变：改了等于老用户本地已下载的索引读不出来。 */
    @Test
    fun `引导写入的路径与迁移前一致`() {
        val bytes = fixtureBytes()
        val root = InMemoryAppFile.root()
        AppFiles.init(root, readAsset = { bytes })

        SchoolRepository().loadIndex()

        assertEquals(
            setOf("repo/index/school_index.pb"),
            root.filePaths(),
            "落盘路径发生变化",
        )
        assertTrue(
            root.directoryPaths().contains("repo/index"),
            "父目录应被自动创建",
        )
    }

    /** 「本地已存在就不再读内置资源」是原实现的关键分支，必须仍成立。 */
    @Test
    fun `本地已有索引时不再读内置资源`() {
        val bytes = fixtureBytes()
        val root = InMemoryAppFile.root()
        var assetReads = 0
        AppFiles.init(root, readAsset = { assetReads++; bytes })

        SchoolRepository().loadIndex()
        assertEquals(1, assetReads, "首次应读一次内置资源")

        SchoolRepository().loadIndex()
        assertEquals(1, assetReads, "本地已有索引，不应再读内置资源")
    }

    /** 内置资源缺失（如本地开发未执行 downloadEduIndex）时应打日志跳过，而不是崩。 */
    @Test
    fun `内置资源缺失时安全降级`() {
        val root = InMemoryAppFile.root()
        AppFiles.init(root, readAsset = { throw IOException("asset 不存在") })

        val repo = SchoolRepository()
        assertEquals(null, repo.loadIndex(), "无索引时应返回 null 而不是抛异常")
        assertEquals(false, repo.hasIndex())
        assertTrue(repo.getSchools().isEmpty())
    }

    @Test
    fun `未注入 readAsset 时也不崩`() {
        AppFiles.init(InMemoryAppFile.root(), readAsset = null)
        val repo = SchoolRepository()
        assertEquals(null, repo.loadIndex())
    }

    // ─────────────── 查询语义 ───────────────

    @Test
    fun `getSchools 过滤与排序保持原语义`() {
        val bytes = fixtureBytes()
        AppFiles.init(InMemoryAppFile.root(), readAsset = { bytes })

        val schools = SchoolRepository().getSchools()
        assertTrue(schools.isNotEmpty(), "应能筛出可导入的学校")

        // 只保留含「本科/研究生/通用工具」适配器的学校
        val allowed = setOf(
            AdapterData.CATEGORY_BACHELOR,
            AdapterData.CATEGORY_POSTGRADUATE,
            AdapterData.CATEGORY_GENERAL_TOOL,
        )
        assertTrue(
            schools.all { s -> s.adapters.any { it.category in allowed } },
            "存在不含可导入适配器的学校，过滤逻辑有变",
        )

        // 排序键：initial.uppercase() + name
        val keys = schools.map { it.initial.uppercase() + it.name }
        assertEquals(keys.sorted(), keys, "排序规则发生变化")
    }

    @Test
    fun `getSchoolById 命中与未命中`() {
        val bytes = fixtureBytes()
        AppFiles.init(InMemoryAppFile.root(), readAsset = { bytes })
        val repo = SchoolRepository()

        val global = repo.getSchoolById("GLOBAL_TOOLS")
        assertNotNull(global, "内置通用工具学校应能查到")
        assertEquals("GLOBAL_TOOLS", global.id)

        assertEquals(null, repo.getSchoolById("__不存在__"))
    }

    @Test
    fun `版本与协议号可读`() {
        val bytes = fixtureBytes()
        AppFiles.init(InMemoryAppFile.root(), readAsset = { bytes })
        val repo = SchoolRepository()
        assertEquals("TIME_20261008145522_344", repo.getIndexVersionId())
        assertEquals(2, repo.getIndexProtocolVersion())
    }

    /** 索引损坏时应返回 null 并打日志，不能把异常抛给 UI。 */
    @Test
    fun `索引损坏时安全降级`() {
        val root = InMemoryAppFile.root()
        AppFiles.init(root, readAsset = { byteArrayOf(0x08) }) // 截断的 varint
        val repo = SchoolRepository()
        assertEquals(null, repo.loadIndex())
    }
}
