/** AI 教务导入的页面网络记录缓冲 */
package com.haooz.chedule.ui.web

import com.haooz.chedule.data.util.optBoolean
import com.haooz.chedule.data.util.optInt
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optLong
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 页面自身发起的一次网络请求。
 *
 * 响应体已由注入脚本截断并脱敏；[size] 是截断前的原始字符数。
 */
internal data class EduNetEntry(
    val id: String,
    val ts: Long,
    /** document / fetch / xhr / form */
    val kind: String,
    val method: String,
    val url: String,
    val status: Int,
    val contentType: String,
    val reqBody: String,
    val respBody: String,
    val json: Boolean,
    val keys: List<String>,
    val size: Int,
    val error: String,
    /** 是否顶层文档（RELOAD_PAGE 用它判断新文档已起来） */
    val top: Boolean,
)

/**
 * 进程内的网络记录环形缓冲。
 *
 * 用 [MutableStateFlow] 承载不可变列表：`@JavascriptInterface` 在后台线程写入，
 * `update` 的 CAS 足以保证并发安全，不需要额外加锁。
 *
 * 只保留最近 [MAX_ENTRIES] 条且总量不超过 [MAX_TOTAL_CHARS]：请求体与响应体各自
 * 最多 32KB，长时间停留可能累积几 MB，超出后从最旧的开始丢。
 */
internal object EduNetLogStore {

    private const val MAX_ENTRIES = 120
    private const val MAX_TOTAL_CHARS = 2_000_000

    private val _entries = MutableStateFlow<List<EduNetEntry>>(emptyList())
    val entries: StateFlow<List<EduNetEntry>> = _entries.asStateFlow()

    /** JS 侧 `AndroidBridge.netLog` 的入口；坏数据直接丢弃，不能让记录器影响页面。 */
    fun append(raw: String) {
        val entry = runCatching { parseJsonObject(raw).toEntry() }.getOrNull() ?: return
        _entries.update { list ->
            val next = (list + entry).takeLast(MAX_ENTRIES)
            var total = next.sumOf { it.reqBody.length + it.respBody.length }
            var from = 0
            while (from < next.size - 1 && total > MAX_TOTAL_CHARS) {
                total -= next[from].reqBody.length + next[from].respBody.length
                from++
            }
            if (from == 0) next else next.subList(from, next.size).toList()
        }
    }

    fun snapshot(): List<EduNetEntry> = _entries.value

    fun clear() {
        _entries.value = emptyList()
    }

    /** 最近一次顶层文档标记的时间；没有记录时为 0。 */
    fun latestTopDocumentTs(): Long =
        _entries.value.lastOrNull { it.kind == "document" && it.top }?.ts ?: 0L

    private fun kotlinx.serialization.json.JsonObject.toEntry(): EduNetEntry? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        val keys = optJsonArray("keys")
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
            .orEmpty()
        return EduNetEntry(
            id = id,
            ts = optLong("ts", 0L),
            kind = optString("kind").ifBlank { "fetch" },
            method = optString("method").ifBlank { "GET" },
            url = optString("url"),
            status = optInt("status", 0),
            contentType = optString("contentType").ifBlank { optString("type") },
            reqBody = optString("reqBody"),
            respBody = optString("respBody"),
            json = optBoolean("json"),
            keys = keys,
            size = optInt("size", 0),
            error = optString("error"),
            top = optBoolean("top"),
        )
    }
}

/** 给 `@JavascriptInterface` / WKScriptMessage 用的非 internal 入口（跨模块反射调用）。 */
fun recordEduNetLog(raw: String) = EduNetLogStore.append(raw)
