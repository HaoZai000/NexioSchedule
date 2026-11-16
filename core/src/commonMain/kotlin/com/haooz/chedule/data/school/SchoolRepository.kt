/** 学校信息仓库 - 管理学校列表和索引数据 */
package com.haooz.chedule.data.school

import com.haooz.chedule.data.platform.AppFile
import com.haooz.chedule.data.platform.AppFiles
import com.haooz.chedule.data.platform.NexioLog

private const val TAG = "SchoolRepository"

/**
 * 学校索引仓库。
 *
 * 改用 [AppFiles] 注入的文件系统，**不持有 Context**，因此整体在 commonMain。
 * 行为保持不变：`repo/index/school_index.pb`、`repo/schools/resources` 路径逐字未变；
 * 内置索引引导仍是「本地不存在才拷贝」，失败打日志并跳过。
 */
class SchoolRepository {

    private val indexFile: AppFile
        get() = AppFiles.root.resolve("repo/index/school_index.pb")

    private val schoolsDir: AppFile
        get() = AppFiles.root.resolve("repo/schools/resources")

    /** AI 仓库只读本地缓存（网络更新由 [AiDockRepository.updateIndex] 负责） */
    private val aiDock: AiDockRepository get() = AiDockRepository.shared

    fun loadIndex(): SchoolIndexData? {
        // 首次使用：本地无索引时，从安装包内置 asset 引导一份，避免联网才能获取学校列表
        ensureBundledIndex()
        ensureBundledAiIndex()
        val bundled = if (indexFile.exists()) {
            try {
                SchoolIndexParser.parse(indexFile.readBytes())
            } catch (e: Exception) {
                NexioLog.e(TAG, "索引解析失败: ${e.message}")
                null
            }
        } else null
        return mergeIndexes(bundled, aiDock.loadIndex())
    }

    /**
     * 合并拾光与 AI 仓库的索引：同 id 或同校名的学校合成一条，适配器按「来源 + id」去重。
     * 两边都有的学校会同时展示两套适配器，UI 用 [AdapterData.source] 区分来源。
     */
    private fun mergeIndexes(
        primary: SchoolIndexData?,
        secondary: SchoolIndexData?,
    ): SchoolIndexData? {
        if (primary == null && secondary == null) return null
        val merged = mutableListOf<SchoolData>()
        (primary?.schools.orEmpty() + secondary?.schools.orEmpty()).forEach { school ->
            val index = merged.indexOfFirst { existing ->
                (existing.id.isNotBlank() && existing.id == school.id) || existing.name == school.name
            }
            if (index >= 0) merged[index] = mergeSchool(merged[index], school) else merged.add(school)
        }
        return SchoolIndexData(
            protocolVersion = maxOf(primary?.protocolVersion ?: 0, secondary?.protocolVersion ?: 0),
            versionId = maxOf(primary?.versionId.orEmpty(), secondary?.versionId.orEmpty()),
            schools = merged,
        )
    }

    private fun mergeSchool(first: SchoolData, other: SchoolData): SchoolData = first.copy(
        id = first.id.ifBlank { other.id },
        name = first.name.ifBlank { other.name },
        initial = first.initial.ifBlank { other.initial },
        resourceFolder = first.resourceFolder.ifBlank { other.resourceFolder },
        adapters = (first.adapters + other.adapters).distinctBy { it.source to it.adapterId },
    )

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

    /** AI 仓库索引同理：Release 打包时已拉入 assets，首启直接用，不必等一次联网更新 */
    private fun ensureBundledAiIndex() {
        val aiIndexFile = AiDockRepository.indexFile()
        if (aiIndexFile.exists()) return
        try {
            aiIndexFile.writeBytes(AppFiles.readAsset(AiDockRepository.BUNDLED_INDEX_ASSET))
        } catch (e: Exception) {
            NexioLog.e(TAG, "读取内置 AI 索引失败: ${e.message}")
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
        if (adapter.source == AdapterData.SOURCE_AI_DOCK) {
            val file = AiDockRepository.scriptFile(adapter.assetJsPath)
            return if (file.exists()) file else null
        }
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
