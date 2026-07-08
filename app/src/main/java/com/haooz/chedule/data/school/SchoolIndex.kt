package com.haooz.chedule.data.school

import com.google.gson.annotations.SerializedName
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * 学校索引数据模型
 * 对应 shiguang_warehouse 的 school_index.proto
 */
data class SchoolIndexData(
    @SerializedName("protocol_version") val protocolVersion: Int = 0,
    @SerializedName("version_id") val versionId: String = "",
    val schools: List<SchoolData> = emptyList()
)

data class SchoolData(
    val id: String = "",
    val name: String = "",
    val initial: String = "",
    @SerializedName("resource_folder") val resourceFolder: String = "",
    val adapters: List<AdapterData> = emptyList()
)

data class AdapterData(
    @SerializedName("adapter_id") val adapterId: String = "",
    @SerializedName("adapter_name") val adapterName: String = "",
    val category: Int = 0,
    @SerializedName("asset_js_path") val assetJsPath: String = "",
    @SerializedName("import_url") val importUrl: String? = null,
    val description: String = "",
    val maintainer: String = ""
) {
    companion object {
        const val CATEGORY_UNKNOWN = 0
        const val CATEGORY_GENERAL_TOOL = 1
        const val CATEGORY_BACHELOR = 2
        const val CATEGORY_POSTGRADUATE = 3
    }
}

/**
 * Protobuf 二进制格式解析器
 * 手动解析 school_index.pb 文件
 */
object SchoolIndexParser {

    // Wire types
    private const val WIRE_TYPE_VARINT = 0
    private const val WIRE_TYPE_64BIT = 1
    private const val WIRE_TYPE_LENGTH_DELIMITED = 2
    private const val WIRE_TYPE_32BIT = 5

    fun parse(data: ByteArray): SchoolIndexData {
        return parseSchoolIndex(ByteArrayInputStream(data))
    }

    private fun parseSchoolIndex(input: InputStream): SchoolIndexData {
        var protocolVersion = 0
        var versionId = ""
        val schools = mutableListOf<SchoolData>()

        while (input.available() > 0) {
            val tag = readVarint(input).toInt()
            val fieldNumber = tag shr 3
            val wireType = tag and 0x7

            when (fieldNumber) {
                1 -> protocolVersion = readVarint(input).toInt()
                2 -> versionId = readString(input)
                3 -> {
                    val length = readVarint(input).toInt()
                    val bytes = readBytes(input, length)
                    schools.add(parseSchool(ByteArrayInputStream(bytes)))
                }
                else -> skipField(input, wireType)
            }
        }

        return SchoolIndexData(protocolVersion, versionId, schools)
    }

    private fun parseSchool(input: InputStream): SchoolData {
        var id = ""
        var name = ""
        var initial = ""
        var resourceFolder = ""
        val adapters = mutableListOf<AdapterData>()

        while (input.available() > 0) {
            val tag = readVarint(input).toInt()
            val fieldNumber = tag shr 3
            val wireType = tag and 0x7

            when (fieldNumber) {
                1 -> id = readString(input)
                2 -> name = readString(input)
                3 -> initial = readString(input)
                4 -> resourceFolder = readString(input)
                5 -> {
                    val length = readVarint(input).toInt()
                    val bytes = readBytes(input, length)
                    adapters.add(parseAdapter(ByteArrayInputStream(bytes)))
                }
                else -> skipField(input, wireType)
            }
        }

        return SchoolData(id, name, initial, resourceFolder, adapters)
    }

    private fun parseAdapter(input: InputStream): AdapterData {
        var adapterId = ""
        var adapterName = ""
        var category = 0
        var assetJsPath = ""
        var importUrl: String? = null
        var description = ""
        var maintainer = ""

        while (input.available() > 0) {
            val tag = readVarint(input).toInt()
            val fieldNumber = tag shr 3
            val wireType = tag and 0x7

            when (fieldNumber) {
                1 -> adapterId = readString(input)
                2 -> adapterName = readString(input)
                3 -> category = readVarint(input).toInt()
                4 -> assetJsPath = readString(input)
                5 -> importUrl = readString(input)
                6 -> description = readString(input)
                7 -> maintainer = readString(input)
                else -> skipField(input, wireType)
            }
        }

        return AdapterData(adapterId, adapterName, category, assetJsPath, importUrl, description, maintainer)
    }

    private fun readVarint(input: InputStream): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val byte = input.read()
            if (byte == -1) throw IllegalArgumentException("Unexpected end of stream")
            result = result or ((byte.toLong() and 0x7FL) shl shift)
            if ((byte and 0x80) == 0) break
            shift += 7
        }
        return result
    }

    private fun readString(input: InputStream): String {
        val length = readVarint(input).toInt()
        val bytes = readBytes(input, length)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readBytes(input: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read == -1) throw IllegalArgumentException("Unexpected end of stream")
            offset += read
        }
        return buffer
    }

    private fun skipField(input: InputStream, wireType: Int) {
        when (wireType) {
            WIRE_TYPE_VARINT -> readVarint(input)
            WIRE_TYPE_64BIT -> readBytes(input, 8)
            WIRE_TYPE_LENGTH_DELIMITED -> {
                val length = readVarint(input).toInt()
                readBytes(input, length)
            }
            WIRE_TYPE_32BIT -> readBytes(input, 4)
            else -> throw IllegalArgumentException("Unknown wire type: $wireType")
        }
    }
}
