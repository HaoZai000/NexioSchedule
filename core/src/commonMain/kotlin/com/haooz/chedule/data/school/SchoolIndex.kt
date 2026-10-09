package com.haooz.chedule.data.school

/**
 * 学校索引数据模型，对应 shiguang_warehouse 的 `school_index.proto`。
 *
 * ## 关于字段名映射
 *
 * 这几个类原先带 `com.google.gson.annotations.SerializedName`，但**项目里没有任何
 * gson 反序列化用到它们**（解析走的是下面的手写 protobuf），注解是装饰性的，因此去掉。
 * 保留 wire 字段名在 KDoc 里，便于对照 `.proto`：
 *
 * ```
 * SchoolIndexData: protocol_version=1, version_id=2, schools=3
 * SchoolData:      id=1, name=2, initial=3, resource_folder=4, adapters=5
 * AdapterData:     adapter_id=1, adapter_name=2, category=3, asset_js_path=4,
 *                  import_url=5, description=6, maintainer=7
 * ```
 */
data class SchoolIndexData(
    /** wire 名 `protocol_version` */
    val protocolVersion: Int = 0,
    /** wire 名 `version_id` */
    val versionId: String = "",
    val schools: List<SchoolData> = emptyList(),
)

data class SchoolData(
    val id: String = "",
    val name: String = "",
    val initial: String = "",
    /** wire 名 `resource_folder` */
    val resourceFolder: String = "",
    val adapters: List<AdapterData> = emptyList(),
)

data class AdapterData(
    /** wire 名 `adapter_id` */
    val adapterId: String = "",
    /** wire 名 `adapter_name` */
    val adapterName: String = "",
    val category: Int = 0,
    /** wire 名 `asset_js_path` */
    val assetJsPath: String = "",
    /** wire 名 `import_url` */
    val importUrl: String? = null,
    val description: String = "",
    val maintainer: String = "",
) {
    companion object {
        const val CATEGORY_UNKNOWN = 0
        const val CATEGORY_GENERAL_TOOL = 1
        const val CATEGORY_BACHELOR = 2
        const val CATEGORY_POSTGRADUATE = 3
    }
}

/**
 * Protobuf 二进制格式解析器（手写，不引入 protobuf 运行时）。
 *
 * ## 迁移说明
 *
 * 原实现基于 `java.io.ByteArrayInputStream` / `InputStream` —— 那是 JVM 专有，
 * Kotlin/Native 与 JS 都没有。现改为基于 [ByteArray] + 下标的游标，
 * **不复制嵌套消息的字节**（原实现每层都 `ByteArrayInputStream(bytes)` 复制一次）。
 *
 * 行为保持不变的要点：
 * - 循环条件由 `input.available() > 0` 改为 `pos < end`，语义一致
 * - 长度校验、截断检测、[MAX_FIELD_BYTES] 上限全部保留
 * - 未知字段按 wire type 跳过（varint / 64bit / length-delimited / 32bit）
 */
object SchoolIndexParser {

    // 单字段最大字节数：损坏/篡改的 varint length 不得直接 ByteArray(length)
    private const val MAX_FIELD_BYTES = 8 * 1024 * 1024

    // Wire types
    private const val WIRE_TYPE_VARINT = 0
    private const val WIRE_TYPE_64BIT = 1
    private const val WIRE_TYPE_LENGTH_DELIMITED = 2
    private const val WIRE_TYPE_32BIT = 5

    fun parse(data: ByteArray): SchoolIndexData {
        val r = ProtoReader(data)
        var protocolVersion = 0
        var versionId = ""
        val schools = mutableListOf<SchoolData>()

        while (r.hasMore) {
            val tag = r.readVarint().toInt()
            val fieldNumber = tag shr 3
            val wireType = tag and 0x7

            when (fieldNumber) {
                1 -> protocolVersion = r.readVarint().toInt()
                2 -> versionId = r.readString()
                3 -> schools.add(parseSchool(r.readLengthDelimited()))
                else -> r.skipField(wireType)
            }
        }

        return SchoolIndexData(protocolVersion, versionId, schools)
    }

    private fun parseSchool(r: ProtoReader): SchoolData {
        var id = ""
        var name = ""
        var initial = ""
        var resourceFolder = ""
        val adapters = mutableListOf<AdapterData>()

        while (r.hasMore) {
            val tag = r.readVarint().toInt()
            val fieldNumber = tag shr 3
            val wireType = tag and 0x7

            when (fieldNumber) {
                1 -> id = r.readString()
                2 -> name = r.readString()
                3 -> initial = r.readString()
                4 -> resourceFolder = r.readString()
                5 -> adapters.add(parseAdapter(r.readLengthDelimited()))
                else -> r.skipField(wireType)
            }
        }

        return SchoolData(id, name, initial, resourceFolder, adapters)
    }

    private fun parseAdapter(r: ProtoReader): AdapterData {
        var adapterId = ""
        var adapterName = ""
        var category = 0
        var assetJsPath = ""
        var importUrl: String? = null
        var description = ""
        var maintainer = ""

        while (r.hasMore) {
            val tag = r.readVarint().toInt()
            val fieldNumber = tag shr 3
            val wireType = tag and 0x7

            when (fieldNumber) {
                1 -> adapterId = r.readString()
                2 -> adapterName = r.readString()
                3 -> category = r.readVarint().toInt()
                4 -> assetJsPath = r.readString()
                5 -> importUrl = r.readString()
                6 -> description = r.readString()
                7 -> maintainer = r.readString()
                else -> r.skipField(wireType)
            }
        }

        return AdapterData(adapterId, adapterName, category, assetJsPath, importUrl, description, maintainer)
    }

    /**
     * 下标游标。`end` 用于嵌套消息的边界裁剪，避免为每个子消息复制字节。
     */
    private class ProtoReader(
        private val data: ByteArray,
        start: Int = 0,
        private val end: Int = data.size,
    ) {
        private var pos = start

        val hasMore: Boolean get() = pos < end

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                if (pos >= end) throw IllegalArgumentException("Unexpected end of stream")
                val byte = data[pos++].toInt()
                result = result or ((byte.toLong() and 0x7FL) shl shift)
                if ((byte and 0x80) == 0) break
                shift += 7
                // varint 最多 10 字节（64 位）；超出说明数据损坏，避免无限循环
                if (shift > 63) throw IllegalArgumentException("Varint too long")
            }
            return result
        }

        fun readString(): String = readBytes(readVarint().toInt()).decodeToString()

        fun readBytes(length: Int): ByteArray {
            checkLength(length)
            if (pos + length > end) throw IllegalArgumentException("Unexpected end of stream")
            val out = data.copyOfRange(pos, pos + length)
            pos += length
            return out
        }

        /** 读一个 length-delimited 字段，返回限定在它字节范围内的子游标。 */
        fun readLengthDelimited(): ProtoReader {
            val length = readVarint().toInt()
            checkLength(length)
            if (pos + length > end) throw IllegalArgumentException("Unexpected end of stream")
            val sub = ProtoReader(data, pos, pos + length)
            pos += length
            return sub
        }

        fun skipField(wireType: Int) {
            when (wireType) {
                WIRE_TYPE_VARINT -> readVarint()
                WIRE_TYPE_64BIT -> skip(8)
                WIRE_TYPE_LENGTH_DELIMITED -> skip(readVarint().toInt())
                WIRE_TYPE_32BIT -> skip(4)
                else -> throw IllegalArgumentException("Unknown wire type: $wireType")
            }
        }

        private fun skip(length: Int) {
            checkLength(length)
            if (pos + length > end) throw IllegalArgumentException("Unexpected end of stream")
            pos += length
        }

        private fun checkLength(length: Int) {
            if (length < 0 || length > MAX_FIELD_BYTES) {
                throw IllegalArgumentException("Invalid field length: $length")
            }
        }
    }
}
