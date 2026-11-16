package com.haooz.chedule.data.ai

import com.haooz.chedule.data.platform.HttpService
import com.haooz.chedule.data.platform.StatsReporter
import com.haooz.chedule.data.util.optInt
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optJsonObject
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 模型要求执行的一次工具调用。 */
data class AiAgentToolCall(
    val id: String,
    val name: String,
    /** 原始 JSON 文本；模型经常给出不合法 JSON，解析失败由调用方兜住 */
    val argumentsJson: String,
)

/** 流式过程中推给 UI 的事件：思考与正文都是增量，调用方自己累积。 */
sealed interface AiAgentEvent {
    /** 思考增量：每块几十字，调用方自己累积 */
    data class Reasoning(val text: String) : AiAgentEvent

    /** 正文增量：模型边写边给，界面要实时显示 */
    data class Content(val text: String) : AiAgentEvent
}

/** 一轮 assistant 回复：要么是正文，要么是一批工具调用，两者可能同时有。 */
data class AiAgentReply(
    val content: String,
    val toolCalls: List<AiAgentToolCall> = emptyList(),
    /**
     * 思考模式下这轮的推理内容。
     *
     * ⚠ 不是给用户看的装饰：下一轮请求的 assistant 消息必须原样带回来。
     * 官方文档明确要求多轮工具调用保留历史 `reasoning_content`，丢了模型会忘记自己为什么这么查。
     */
    val reasoningContent: String = "",
    /** 模型的停止原因；`length` = 撞到长度上限，通常意味着思考把预算吃光 */
    val finishReason: String = "",
    /** 今天还剩几轮；服务端没给就是 null */
    val remaining: Int? = null,
)

/**
 * 教务 agent 的云端通道。
 *
 * 工具读写的是本机 WebView，服务端只负责「一轮模型调用」：收 messages + tools，
 * 回一条 assistant message。循环、校验与落库都在本地，课程只能经预览确认后写入。
 *
 * 与 [AiClient] 共用 [aiRoundTrip]，避免两套会话密钥与信封实现漂移。
 */
class AiEduAgentClient(
    private val http: HttpService,
    private val baseUrl: String = AiClient.DEFAULT_BASE_URL,
) {

    /**
     * 跑一轮：把完整对话与工具表交给模型，拿回下一步。
     *
     * @param messages OpenAI Chat 格式的整段历史（含 tool 结果），由调用方维护
     * @param tools 工具 schema 数组；空数组表示这一轮不允许调工具
     * @param sessionId 一次导入的会话 ID；同一会话内多轮只扣 1 次额度
     */
    suspend fun turn(
        messages: List<JsonObject>,
        tools: JsonArray,
        sessionId: String? = null,
    ): AiResult<AiAgentReply> {
        if (messages.isEmpty()) return AiResult.Error("对话是空的")

        // 用户自带服务商：直连，不经过我们的服务器
        val byok = AiByokStore.load()
        if (byok.isUsable) return AiDirectClient(http).turn(byok, messages, tools)

        val body = buildJsonObject {
            // 服务端按 device_id 计每日额度（与统计上报同一个 ID）
            put("device_id", StatsReporter.ensureDeviceId())
            if (!sessionId.isNullOrBlank()) put("session_id", sessionId)
            put("messages", JsonArray(compactForWire(messages)))
            put("tools", tools)
        }

        val plain = when (val r = aiRoundTrip(http, "$baseUrl/agent", body)) {
            is AiResult.Ok -> r.value
            is AiResult.Error -> return r
        }

        val obj = runCatching { parseJsonObject(plain) }
            .getOrElse { return AiResult.Error("服务端响应不是 JSON") }
        if (!obj.optString("ok").toBoolean()) {
            return AiResult.Error(obj.optString("error").ifEmpty { "服务端说这次没成" })
        }

        val message = obj.optJsonObject("message")
            ?: return AiResult.Error("服务端没返回消息")
        val remaining = obj.optInt("remaining", -1).takeIf { it >= 0 }
        return AiResult.Ok(parseReply(message, remaining))
    }

    /**
     * 流式跑一轮：思考内容随到随回调，结束时拿到与 [turn] 结构完全一致的回复。
     *
     * 走 `/agent/stream`，响应是 SSE，每条事件的 payload 都是**逐块加密**的密文，
     * 用同一个 [AiEnvelopeSession] 逐条解开。这样"边想边看"与"线上不留明文"能同时成立。
     *
     * ⚠ iOS 的 `streamPost` 目前是一次性读完后按行回调（NSURLSession 没有真流式），
     * 那边表现仍是"想完一次跳出来"，但协议、解析、渲染路径与 Android 完全一致。
     */
    suspend fun turnStream(
        messages: List<JsonObject>,
        tools: JsonArray,
        sessionId: String? = null,
        onEvent: suspend (AiAgentEvent) -> Unit,
    ): AiResult<AiAgentReply> {
        if (messages.isEmpty()) return AiResult.Error("对话是空的")

        // 用户自带服务商：直连供应商的 SSE，不经过我们的服务器
        val byok = AiByokStore.load()
        if (byok.isUsable) return AiDirectClient(http).turnStream(byok, messages, tools, onEvent)

        if (!AiServerKey.isConfigured()) return AiResult.Error("这个版本还没内置服务端公钥")

        val envelope = try {
            AiEnvelopeSession()
        } catch (e: Exception) {
            return AiResult.Error("加密会话创建失败：${e.describe()}")
        }

        val body = buildJsonObject {
            put("device_id", StatsReporter.ensureDeviceId())
            if (!sessionId.isNullOrBlank()) put("session_id", sessionId)
            put("messages", JsonArray(compactForWire(messages)))
            put("tools", tools)
        }
        val payload = try {
            envelope.sealRequest(body.toString())
        } catch (e: Exception) {
            return AiResult.Error("请求加密失败：${e.describe()}")
        }

        var failure: String? = null
        var done: JsonObject? = null
        var remaining: Int? = null

        try {
            http.streamPost("$baseUrl/agent/stream", payload) { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("data:")) return@streamPost
                val data = trimmed.removePrefix("data:").trim()
                if (data.isEmpty()) return@streamPost

                val chunk = runCatching { parseJsonObject(data) }.getOrNull() ?: return@streamPost
                // 信封都解不开时服务端只能回明文 {"error":...}
                if (chunk.optString("iv").isEmpty()) {
                    failure = chunk.optString("error").ifEmpty { "服务端没有返回密文" }
                    return@streamPost
                }
                val plain = runCatching { envelope.openChunk(chunk.optString("iv"), chunk.optString("ct")) }
                    .getOrElse {
                        failure = "响应解密失败：${it.describe()}"
                        return@streamPost
                    }
                val event = runCatching { parseJsonObject(plain) }.getOrNull() ?: return@streamPost
                when (event.optString("t")) {
                    "reasoning" -> onEvent(AiAgentEvent.Reasoning(event.optString("x")))
                    "content" -> onEvent(AiAgentEvent.Content(event.optString("x")))
                    "done" -> {
                        done = event.optJsonObject("message")
                        val rem = event.optInt("remaining", -1)
                        if (rem >= 0) remaining = rem
                    }
                    "error" -> failure = event.optString("error").ifEmpty { "服务端报错" }
                }
            }
        } catch (e: CancellationException) {
            // 「停止」是主动取消：不要把它翻译成一句「网络请求失败」再画到界面上
            throw e
        } catch (e: Exception) {
            return AiResult.Error("网络请求失败：${e.describe()}")
        }

        failure?.let { return AiResult.Error(it) }
        val message = done ?: return AiResult.Error("服务端没有给出结果")
        return AiResult.Ok(parseReply(message, remaining))
    }

    private fun parseReply(message: JsonObject, remaining: Int?): AiAgentReply {
        val calls = message.optJsonArray("tool_calls")?.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val fn = item.optJsonObject("function") ?: return@mapNotNull null
            val name = fn.optString("name").trim()
            if (name.isEmpty()) return@mapNotNull null
            AiAgentToolCall(
                id = item.optString("id").ifEmpty { name },
                name = name,
                argumentsJson = fn.optString("arguments").ifBlank { "{}" },
            )
        }.orEmpty()

        return AiAgentReply(
            content = message.optString("content"),
            toolCalls = calls,
            reasoningContent = message.optString("reasoning_content"),
            finishReason = message.optString("finish_reason"),
            remaining = remaining,
        )
    }

    companion object {
        // ── 上下文压缩 ──

        /**
         * 整段 messages 序列化后的字符上限：服务端 `readBody` 卡在 1e6，
         * 预留 40% 给信封（base64 约 1.33 倍）与 JSON 结构。
         */
        private const val MAX_WIRE_CHARS = 600_000

        /** 最近几条一律不动：模型正靠它们做决定 */
        private const val KEEP_RECENT = 6

        /**
         * 超长时把较早的工具结果与脚本正文换成占位。
         *
         * 脚本原文留在 `tool_calls.arguments` 里，几轮 60KB 脚本就能顶穿上限；被省略的内容
         * 模型可以重新调工具获取。只压缩发出去的副本，会话本身保持完整。
         */
        internal fun compactForWire(messages: List<JsonObject>): List<JsonObject> {
            var total = messages.sumOf { it.toString().length }
            if (total <= MAX_WIRE_CHARS) return messages

            val out = messages.toMutableList()
            // 跳过第 0 条 system，最近 KEEP_RECENT 条不动
            val until = (out.size - KEEP_RECENT).coerceAtLeast(1)
            for (i in 1 until until) {
                if (total <= MAX_WIRE_CHARS) break
                val msg = out[i]
                val role = msg.optString("role")
                val calls = msg.optJsonArray("tool_calls")
                if (role != "tool" && !(role == "assistant" && calls != null)) continue

                val before = msg.toString().length
                val replaced = if (role == "tool") {
                    buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", msg.optString("tool_call_id"))
                        put("content", "[较早的工具结果已省略以控制上下文长度；需要时重新调用工具获取]")
                    }
                } else {
                    buildJsonObject {
                        put("role", "assistant")
                        msg.optString("content").takeIf { it.isNotBlank() }?.let { put("content", it) }
                        msg.optString("reasoning_content").takeIf { it.isNotBlank() }
                            ?.let { put("reasoning_content", it) }
                        put(
                            "tool_calls",
                            buildJsonArray {
                                calls!!.forEach { element ->
                                    val call = element as? JsonObject ?: return@forEach
                                    add(
                                        buildJsonObject {
                                            put("id", call.optString("id"))
                                            put("type", "function")
                                            put(
                                                "function",
                                                buildJsonObject {
                                                    put(
                                                        "name",
                                                        call.optJsonObject("function")?.optString("name").orEmpty()
                                                    )
                                                    put(
                                                        "arguments",
                                                        "{\"note\":\"较早的脚本内容已省略\"}"
                                                    )
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
                out[i] = replaced
                total -= before - replaced.toString().length
            }
            return out
        }

        // ── 消息构造：调用方只关心语义，JSON 细节留在这里 ──

        fun systemMessage(text: String): JsonObject = buildJsonObject {
            put("role", "system")
            put("content", text)
        }

        fun userMessage(text: String): JsonObject = buildJsonObject {
            put("role", "user")
            put("content", text)
        }

        /** 模型只说话、不调工具的那条回复。续跑时要留在历史里，否则它忘了自己问过什么。 */
        fun assistantMessage(content: String, reasoningContent: String = ""): JsonObject =
            buildJsonObject {
                put("role", "assistant")
                put("content", content)
                if (reasoningContent.isNotBlank()) put("reasoning_content", reasoningContent)
            }

        /**
         * 把模型的工具调用原样回填进历史 —— 少了这一步，下一轮 tool 结果会配对不上。
         *
         * @param reasoningContent 思考模式下的推理原文。MiMo 要求多轮工具调用保留这段历史，
         *   不给就等于让模型每轮重新推理一遍自己上一轮想干什么
         */
        fun assistantToolCallsMessage(
            content: String,
            calls: List<AiAgentToolCall>,
            reasoningContent: String = "",
        ): JsonObject =
            buildJsonObject {
                put("role", "assistant")
                put("content", if (content.isBlank()) JsonNull else JsonPrimitive(content))
                if (reasoningContent.isNotBlank()) put("reasoning_content", reasoningContent)
                put(
                    "tool_calls",
                    buildJsonArray {
                        calls.forEach { call ->
                            add(
                                buildJsonObject {
                                    put("id", call.id)
                                    put("type", "function")
                                    put(
                                        "function",
                                        buildJsonObject {
                                            put("name", call.name)
                                            put("arguments", call.argumentsJson)
                                        }
                                    )
                                }
                            )
                        }
                    }
                )
            }

        fun toolResultMessage(callId: String, content: String): JsonObject = buildJsonObject {
            put("role", "tool")
            put("tool_call_id", callId)
            put("content", content)
        }

        /** 工具 schema 的简写：`tool(name, description, params)` */
        fun tool(name: String, description: String, params: JsonObject): JsonObject = buildJsonObject {
            put("type", "function")
            put(
                "function",
                buildJsonObject {
                    put("name", name)
                    put("description", description)
                    put("parameters", params)
                }
            )
        }

        /** 对象型参数表；[required] 为空时不下发 required 字段（部分服务商对空数组报错）。 */
        fun params(
            properties: Map<String, JsonObject>,
            required: List<String> = emptyList(),
        ): JsonObject = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties))
            if (required.isNotEmpty()) put("required", JsonArray(required.map { JsonPrimitive(it) }))
        }

        fun stringProp(description: String): JsonObject = buildJsonObject {
            put("type", "string")
            put("description", description)
        }

        fun enumProp(description: String, values: List<String>): JsonObject = buildJsonObject {
            put("type", "string")
            put("description", description)
            put("enum", JsonArray(values.map { JsonPrimitive(it) }))
        }

        /** 从模型给的参数 JSON 里取字符串；容错到「解析不出来就当空串」。 */
        fun argString(args: JsonObject, key: String): String = args.optString(key).trim()

        /** 把任意 JsonElement 压成回传给模型的文本（超长截断）。 */
        fun clip(text: String, max: Int): String =
            if (text.length <= max) text else text.take(max) + "\n…（已截断，共 ${text.length} 字）"

        /** 模型有时把参数写成带 ```json 围栏的字符串，剥掉。 */
        fun stripFences(text: String): String {
            val t = text.trim()
            if (!t.startsWith("```")) return t
            val start = t.indexOf('\n')
            if (start < 0) return t
            val body = t.substring(start + 1)
            val end = body.lastIndexOf("```")
            return if (end >= 0) body.substring(0, end).trim() else body.trim()
        }

        fun JsonElement?.asJsonObjectOrNull(): JsonObject? = this as? JsonObject
    }
}
