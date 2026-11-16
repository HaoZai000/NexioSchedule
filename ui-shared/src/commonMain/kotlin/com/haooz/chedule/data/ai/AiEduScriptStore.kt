package com.haooz.chedule.data.ai

import com.haooz.chedule.data.platform.AppFiles
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.util.optBoolean
import com.haooz.chedule.data.util.optInt
import com.haooz.chedule.data.util.optLong
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonArray
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * AI 在教务导入里写过的一次脚本。
 *
 * 失败重写的每一版也各存一条：用户回头看历史时，真正有用的是「哪一版跑通了、
 * 当时这个系统长什么样」，而不是只留最后一次成功的成品。
 */
data class AiEduScriptRecord(
    val id: String,
    /** 创建时刻（epoch ms），历史页按它倒序 */
    val createdAtMs: Long,
    /** 写脚本时的页面地址，用来区分同一台设备上不同学校的记录 */
    val pageUrl: String,
    /** 模型给这次脚本的一句话说明（RUN_SCRIPT 的 note 参数），可能为空 */
    val note: String,
    /** 完整脚本源码 */
    val script: String,
    /** 是否跑通（沙箱回执，不是模型自述） */
    val ok: Boolean,
    val courseCount: Int,
    /** 失败原因；[ok] 为 true 时通常为空 */
    val error: String?,
) {
    companion object {
        fun create(
            pageUrl: String,
            note: String,
            script: String,
            ok: Boolean,
            courseCount: Int,
            error: String?,
        ): AiEduScriptRecord {
            val now = Clock.System.now().toEpochMilliseconds()
            return AiEduScriptRecord(
                id = now.toString(36) + "-" + Random.nextLong().toString(36),
                createdAtMs = now,
                pageUrl = pageUrl,
                note = note,
                script = script,
                ok = ok,
                courseCount = courseCount,
                error = error,
            )
        }
    }
}

/**
 * AI 教务脚本历史存储。
 *
 * 用私有目录里的单个 JSON 文件而不是偏好键：脚本动辄几 KB，塞进 SharedPreferences
 * 会在每次读写时整串反序列化，而且键值存储对长文本没有上限保障。
 *
 * 只保留最近 [MAX_RECORDS] 条：再早的脚本对用户没有回看价值，留着只会让历史页首屏变慢。
 */
object AiEduScriptStore {

    private const val FILE_NAME = "ai_edu_scripts.json"
    private const val MAX_RECORDS = 60

    /** 读全部记录（按写入顺序，旧的在前）。失败一律当空列表，不让历史页崩掉。 */
    suspend fun load(): List<AiEduScriptRecord> = withContext(ioDispatcher) {
        readAll()
    }

    /** 追加一条；返回 false 表示没写进去（未初始化 / 磁盘异常）。 */
    suspend fun append(record: AiEduScriptRecord): Boolean = withContext(ioDispatcher) {
        if (!AppFiles.isReady) return@withContext false
        runCatching {
            val updated = (readAll() + record).takeLast(MAX_RECORDS)
            val json = buildJsonArray { updated.forEach { add(it.toJson()) } }
            AppFiles.root.resolve(FILE_NAME).writeBytes(json.toString().encodeToByteArray())
            true
        }.getOrDefault(false)
    }

    private fun readAll(): List<AiEduScriptRecord> {
        if (!AppFiles.isReady) return emptyList()
        return runCatching {
            val file = AppFiles.root.resolve(FILE_NAME)
            if (!file.exists()) return@runCatching emptyList()
            parseJsonArray(file.readText()).mapNotNull { it.asRecord() }
        }.getOrDefault(emptyList())
    }

    private fun AiEduScriptRecord.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("createdAtMs", createdAtMs)
        put("pageUrl", pageUrl)
        put("note", note)
        put("script", script)
        put("ok", ok)
        put("courseCount", courseCount)
        error?.let { put("error", it) }
    }

    private fun JsonElement.asRecord(): AiEduScriptRecord? {
        val o = this as? JsonObject ?: return null
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val script = o.optString("script")
        if (script.isBlank()) return null
        return AiEduScriptRecord(
            id = id,
            createdAtMs = o.optLong("createdAtMs", 0L),
            pageUrl = o.optString("pageUrl"),
            note = o.optString("note"),
            script = script,
            ok = o.optBoolean("ok"),
            courseCount = o.optInt("courseCount", 0),
            error = o.optString("error").takeIf { it.isNotBlank() },
        )
    }
}
