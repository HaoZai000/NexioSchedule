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
        if (!indexFile.exists()) return null
        return try {
            SchoolIndexParser.parse(indexFile.readBytes())
        } catch (e: Exception) {
            Log.e(TAG, "索引解析失败: ${e.message}")
            null
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
