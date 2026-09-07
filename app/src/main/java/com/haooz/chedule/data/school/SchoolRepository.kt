/** 学校信息仓库 - 管理学校列表和索引数据 */
package com.haooz.chedule.data.school

import android.content.Context
import android.util.Log
import java.io.File

private const val TAG = "SchoolRepository"

class SchoolRepository(private val context: Context) {

    private val indexFile: File
        get() = File(context.filesDir, "repo/index/school_index.pb")

    private val schoolsDir: File
        get() = File(context.filesDir, "repo/schools/resources")

    fun loadIndex(): SchoolIndexData? {
        // 首次使用：本地无索引时，从安装包内置 asset 引导一份，避免联网才能获取学校列表
        ensureBundledIndex()
        if (!indexFile.exists()) return null
        return try {
            SchoolIndexParser.parse(indexFile.readBytes())
        } catch (e: Exception) {
            Log.e(TAG, "索引解析失败: ${e.message}")
            null
        }
    }

    /** 内置索引引导：仅当本地索引不存在时，从 assets/eduloader 拷贝内置 school_index.pb 供首启用 */
    private fun ensureBundledIndex() {
        if (indexFile.exists()) return
        try {
            context.assets.open("eduloader/school_index.pb").use { inbound ->
                indexFile.parentFile?.mkdirs()
                indexFile.outputStream().use { outbound ->
                    inbound.copyTo(outbound)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取内置索引失败: ${e.message}")
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

    fun getScriptFile(adapter: AdapterData, school: SchoolData): File? {
        val scriptFile = File(schoolsDir, "${school.resourceFolder}/${adapter.assetJsPath}")
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
