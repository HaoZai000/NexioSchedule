package com.haooz.chedule.data.platform

import com.haooz.chedule.data.util.jsonObjectOf
import com.haooz.chedule.data.util.optBoolean
import com.haooz.chedule.data.util.optInt
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optJsonObject
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonObject
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 应用内反馈与开发进度的数据层。
 *
 * 与 [StatsReporter] 同包同风格：明文 HTTP + [HttpService]，**不要换 Ktor**
 * （会把 coroutines 顶到 1.10+，影响提醒/闹钟/小组件）。
 *
 * 身份只有匿名 `device_id`（与统计上报同一个 ID）+ 用户自填的邮箱：
 * 邮箱不是登录，只用来让用户在换设备后还能查到自己的提交。
 *
 * ⚠ 接口地址与字段名是与 `server/index.js` 的契约，改一边必须改另一边。
 */
object FeedbackClient {

    private const val TAG = "FeedbackClient"
    private const val BASE_URL = "http://182.92.193.223:3000/api/feedback"

    /** 与服务端 `FEEDBACK_MAX_*` 保持一致 */
    const val MAX_TITLE_LENGTH = 80
    const val MAX_CONTENT_LENGTH = 2000
    const val MAX_EMAIL_LENGTH = 120

    /** 可替换（`internal var`）以便测试注入 FakeHttpService */
    internal var http: HttpService = createHttpService(
        HttpTimeouts(connectSeconds = 10, readSeconds = 15, callSeconds = 20),
    )

    /**
     * 提交一条反馈。
     *
     * 设备/系统信息由客户端自动附带（用户不用填，也不允许改），
     * 邮箱可空 —— 留空时这条反馈只认 `device_id`。
     *
     * @return 服务端分配的单号；失败时 [Result.failure] 里带可直接展示的文案
     */
    suspend fun submit(
        kind: FeedbackKind,
        title: String,
        content: String,
        email: String = "",
    ): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val trimmedContent = content.trim()
            if (trimmedContent.length < 5) {
                throw IllegalArgumentException(
                    displayText("反馈内容太短了，至少写 5 个字", "Please write at least 5 characters"),
                )
            }
            val device = currentDeviceInfo()
            val payload = jsonObjectOf(
                "device_id" to StatsReporter.ensureDeviceId(),
                "type" to kind.wire,
                "title" to title.trim().take(MAX_TITLE_LENGTH),
                "content" to trimmedContent.take(MAX_CONTENT_LENGTH),
                "email" to email.trim().take(MAX_EMAIL_LENGTH),
                "app_version" to AppInfo.version,
                "platform" to StatsReporter.platformTag(device),
                "os_version" to device.osVersion,
                "device_model" to device.model,
                "locale" to if (DisplayLang.isZh) "zh-CN" else "en",
            ).toString()
            NexioLog.i(TAG, "提交反馈: type=${kind.wire} len=${trimmedContent.length}")
            val resp = http.post(BASE_URL, payload)
            val json = runCatching { parseJsonObject(resp.text) }.getOrNull()
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    json?.optString("error").orEmpty().ifBlank {
                        displayText("提交失败（HTTP ${resp.code}）", "Submit failed (HTTP ${resp.code})")
                    },
                )
            }
            json?.optString("id").orEmpty()
        }
    }

    /**
     * 查这台设备（以及同邮箱）提交过的反馈，从新到旧。
     *
     * 服务端按 `device_id` 主查，[email] 非空时把同邮箱的历史一并带出，
     * 这样换设备 / 重装后仍能找回。
     */
    suspend fun fetchMine(email: String = ""): Result<List<FeedbackRecord>> =
        withContext(ioDispatcher) {
            runCatching {
                val deviceId = StatsReporter.ensureDeviceId()
                val url = buildString {
                    append(BASE_URL).append("/mine?device_id=").append(encodeQuery(deviceId))
                    val trimmedEmail = email.trim()
                    if (trimmedEmail.isNotEmpty()) {
                        append("&email=").append(encodeQuery(trimmedEmail))
                    }
                    append("&limit=100")
                }
                NexioLog.i(TAG, "查询我的反馈")
                val resp = http.get(url)
                val json = parseOrThrow(resp)
                val records = json.optJsonArray("records") ?: JsonArray(emptyList())
                records.mapNotNull { it.asObjectOrNull()?.toFeedbackRecord() }
            }
        }

    /** 删除自己提交的一条反馈：服务端按 `device_id` 校验归属，别人的删不掉 */
    suspend fun deleteMine(id: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            if (id.isBlank()) {
                throw IllegalArgumentException(displayText("缺少反馈编号", "Missing feedback id"))
            }
            val payload = jsonObjectOf(
                "device_id" to StatsReporter.ensureDeviceId(),
                "id" to id,
            ).toString()
            NexioLog.i(TAG, "删除反馈: $id")
            val resp = http.post("$BASE_URL/delete", payload)
            val json = parseOrThrow(resp)
            if (!json.optBoolean("ok", false)) {
                throw IllegalStateException(displayText("删除失败", "Delete failed"))
            }
        }
    }

    /** 公开的开发进度列表（App 只读） */
    suspend fun fetchRoadmap(): Result<List<RoadmapRecord>> = withContext(ioDispatcher) {
        runCatching {
            NexioLog.i(TAG, "查询开发进度")
            val resp = http.get("$BASE_URL/roadmap")
            val json = parseOrThrow(resp)
            val records = json.optJsonArray("records") ?: JsonArray(emptyList())
            records.mapNotNull { it.asObjectOrNull()?.toRoadmapRecord() }
        }
    }

    /** HTTP 非 2xx 统一转成可展示文案：优先用服务端的 `error` 字段 */
    private fun parseOrThrow(resp: HttpResult): JsonObject {
        val json = runCatching { parseJsonObject(resp.text) }.getOrNull()
        if (!resp.isSuccessful) {
            throw IllegalStateException(
                json?.optString("error").orEmpty().ifBlank {
                    displayText("服务器开小差了（HTTP ${resp.code}）", "Server error (HTTP ${resp.code})")
                },
            )
        }
        return json ?: throw IllegalStateException(
            displayText("返回数据无法解析", "Malformed response"),
        )
    }

    private fun JsonObject.toFeedbackRecord(): FeedbackRecord {
        val timeline = optJsonArray("timeline")?.mapNotNull { entry ->
            entry.asObjectOrNull()?.let { obj ->
                FeedbackTimelineEntry(
                    status = obj.optString("status"),
                    note = obj.optString("note"),
                    version = obj.optString("version"),
                    at = obj.optString("at"),
                )
            }
        }.orEmpty()
        return FeedbackRecord(
            id = optString("id"),
            kind = FeedbackKind.fromWire(optString("type")),
            title = optString("title"),
            content = optString("content"),
            status = optString("status"),
            statusLabel = optString("status_label"),
            statusNote = optString("status_note"),
            version = optString("version"),
            createdAt = optString("created_at"),
            updatedAt = optString("updated_at"),
            timeline = timeline,
        )
    }

    private fun JsonObject.toRoadmapRecord(): RoadmapRecord = RoadmapRecord(
        id = optString("id"),
        kind = FeedbackKind.fromWire(optString("type")),
        title = optString("title"),
        summary = optString("summary"),
        status = optString("status"),
        statusLabel = optString("status_label"),
        version = optString("version"),
        updatedAt = optString("updated_at"),
    )
}

/** 反馈类型，`type` 是服务端字段值 */
enum class FeedbackKind(val wire: String) {
    Bug("bug"),
    Feature("feature"),
    ;

    companion object {
        fun fromWire(value: String): FeedbackKind = if (value == "feature") Feature else Bug
    }
}

/** 时间线一条：每次改状态都会追加，用户在「我的反馈」里看到推进过程 */
data class FeedbackTimelineEntry(
    val status: String,
    val note: String,
    val version: String,
    val at: String,
)

/** 一条反馈（App 侧视图，不含 device_id / email） */
data class FeedbackRecord(
    val id: String,
    val kind: FeedbackKind,
    val title: String,
    val content: String,
    val status: String,
    val statusLabel: String,
    val statusNote: String,
    val version: String,
    val createdAt: String,
    val updatedAt: String,
    val timeline: List<FeedbackTimelineEntry>,
)

/** 一条对外开发进度 */
data class RoadmapRecord(
    val id: String,
    val kind: FeedbackKind,
    val title: String,
    val summary: String,
    val status: String,
    val statusLabel: String,
    val version: String,
    val updatedAt: String,
)

// ---- 内部小工具 ----

/**
 * 最小 percent-encode：只处理 ASCII 不安全字符。
 *
 * `device_id` 是 UUID、邮箱是 ASCII，够用；非 ASCII 原样保留（服务端只在
 * `URLSearchParams` 里取值，不会二次编码）。
 */
private fun encodeQuery(raw: String): String {
    val hex = "0123456789ABCDEF"
    return buildString {
        for (ch in raw) {
            val code = ch.code
            val safe = code in 'a'.code..'z'.code ||
                code in 'A'.code..'Z'.code ||
                code in '0'.code..'9'.code ||
                ch == '-' || ch == '_' || ch == '.' || ch == '~' || ch == '@'
            if (safe || code > 127) {
                append(ch)
            } else {
                append('%').append(hex[(code shr 4) and 0xF]).append(hex[code and 0xF])
            }
        }
    }
}

/** kotlinx JsonElement → JsonObject，非对象返回 null（服务端字段缺失时不至于整条丢掉） */
private fun kotlinx.serialization.json.JsonElement.asObjectOrNull(): JsonObject? =
    runCatching { this as JsonObject }.getOrNull()
