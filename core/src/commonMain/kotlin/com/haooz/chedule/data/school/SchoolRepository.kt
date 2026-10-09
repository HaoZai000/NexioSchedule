/** 学校信息仓库 - 管理学校列表和索引数据 */
package com.haooz.chedule.data.school

import com.haooz.chedule.data.AppFile
import com.haooz.chedule.data.AppFiles
import com.haooz.chedule.data.NexioLog

private const val TAG = "SchoolRepository"

/**
 * 学校索引仓库。
 *
 * ## 迁移说明
 *
 * 原先依赖 `Context`（`filesDir` + `assets`）与 `java.io.File`，两者都无法进 commonMain。
 * 现改用 [AppFiles] 注入的文件系统，**不再持有 Context**，因此整体下沉 `:core`。
 *
 * 行为保持不变：
 * - `repo/index/school_index.pb`、`repo/schools/resources` 路径逐字未变
 * - 内置索引引导仍是「本地不存在才拷贝」，失败打日志并跳过
 */
class SchoolRepository {

    private val indexFile: AppFile
        get() = AppFiles.root.resolve("repo/index/school_index.pb")

    private val schoolsDir: AppFile
        get() = AppFiles.root.resolve("repo/schools/resources")

    fun loadIndex(): SchoolIndexData? {
        // 首次使用：本地无索引时，从安装包内置 asset 引导一份，避免联网才能获取学校列表
        ensureBundledIndex()
        if (!indexFile.exists()) return null
        return try {
            SchoolIndexParser.parse(indexFile.readBytes())
        } catch (e: Exception) {
            NexioLog.e(TAG, "索引解析失败: ${e.message}")
            null
        }
    }

    /** 内置索引引导：仅当本地索引不存在时，从 assets/eduloader 拷贝内置 school_index.pb 供首启用 */
    private fun ensureBundledIndex() {
        if (indexFile.exists()) return
        try {
            // writeBytes 会自动创建父目录（原来这里是手写 parentFile?.mkdirs()）
            indexFile.writeBytes(AppFiles.readAsset("eduloader/school_index.pb"))
        } catch (e: Exception) {
            NexioLog.e(TAG, "读取内置索引失败: ${e.message}")
        }
    }

    fun getSchools(): List<SchoolData> {
        val index = loadIndex() ?: return emptyList()
        return index.schools.filter { school ->
            school.adapters.any { adapter ->
                adapter.category in listOf(
                    AdapterData.CATEGORY_BACHELOR,
                    AdapterData.CATEGORY_POSTGRADUATE,
                    AdapterData.CATEGORY_GENERAL_TOOL
                )
            }
        }.sortedBy { it.initial.uppercase() + it.name }
    }

    fun getAdaptersForSchool(schoolId: String, category: Int): List<AdapterData> {
        val index = loadIndex() ?: return emptyList()
        val school = index.schools.find { it.id == schoolId } ?: return emptyList()
        return school.adapters.filter { it.category == category }
    }

    fun getSchoolById(id: String): SchoolData? {
        val index = loadIndex() ?: return null
        return index.schools.find { it.id == id }
    }

    fun getScriptFile(adapter: AdapterData, school: SchoolData): AppFile? {
        val scriptFile = schoolsDir.resolve("${school.resourceFolder}/${adapter.assetJsPath}")
        return if (scriptFile.exists()) scriptFile else null
    }

    fun hasIndex(): Boolean = indexFile.exists()

    fun getIndexVersionId(): String? {
        val index = loadIndex() ?: return null
        return index.versionId
    }

    fun getIndexProtocolVersion(): Int {
        val index = loadIndex() ?: return 0
        return index.protocolVersion
    }
}
