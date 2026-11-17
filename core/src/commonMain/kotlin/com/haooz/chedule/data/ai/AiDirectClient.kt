package com.haooz.chedule.data.ai

import com.haooz.chedule.data.platform.HttpResult
import com.haooz.chedule.data.platform.HttpService
import com.haooz.chedule.data.util.optInt
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optJsonObject
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.TimeSource

/**
 * 用户自带服务商（BYOK）时的直连客户端。
 *
 * 请求直接发给用户填的 OpenAI 兼容端点，**不经过 Nexio 服务器**：
 * - 不占免费额度，也不受服务端参数配置影响；
 * - apikey 只在本机内存/偏好里，不随请求发给我们自己的服务端。
 *
 * prompt 与结果解析必须与云端保持一致（`AI_TASKS.shrink_classrooms`），
 * 否则用户切换服务商后缩略质量会肉眼可见地掉一档。
 */
internal class AiDirectClient(private val http: HttpService) {

    private companion object {
        /** [AiProviders] 里 OpenAI 的 id；GPT-5.x 的参数与别家不同 */
        const val OPENAI_PROVIDER = "gpt"
    }

    // ── 通知精简 ────────────────────────────────────────────────

    /**
     * 与云端 `shrink_classrooms` 同一套提示词与解析规则。
     *
     * @return 原名 → 缩写的对照表；空表表示「本来就够短」
     */
    suspend fun shrinkLabels(
        classrooms: List<String>,
        courses: List<String>,
    ): AiResult<List<Pair<String, String>>> {
        val cfg = AiByokStore.load()
        if (!cfg.isUsable) return AiResult.Error("还没配置自己的 AI 服务商")

        val messages = buildShrinkMessages(courses, classrooms)
        val text = when (val r = complete(cfg, messages, temperature = 0.0, maxTokens = 4096, reasoningEffort = "none")) {
            is AiResult.Ok -> r.value
            is AiResult.Error -> return r
        }
        val parsed = parseShrink(text, classrooms, courses)
            ?: return AiResult.Error("模型输出没法用")
        return AiResult.Ok(parsed)
    }

    private fun buildShrinkMessages(courses: List<String>, classrooms: List<String>): JsonArray {
        val lines = mutableListOf<String>()
        if (courses.isNotEmpty()) {
            lines += "课程名："
            courses.forEach { lines += "- $it" }
            lines += ""
        }
        if (classrooms.isNotEmpty()) {
            lines += "上课地点："
            classrooms.forEach { lines += "- $it" }
        }
        return buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", "只输出结果，不解释、不寒暄、不加序号。")
            })
            add(buildJsonObject {
                put("role", "user")
                put(
                    "content",
                    "把下面的课程名和上课地点压短，缩略态一行只放得下几个字。\n" +
                        "一行一条 `原文=缩写`，等号两边不加空格；左边从原文照抄一段。\n\n" +
                        lines.joinToString("\n") + "\n\n" +
                        "宽度按半角算（汉字 2、字母数字 1），目标 12 以内；能看出是哪门课比字数重要。\n" +
                        "课程名删括号注记、开课学校名、学时学分，以及「概论」「导论」「纲要」「与」" +
                        "这类虚词；术语可换公认短形式（`概率论与数理统计=概率与数统`），" +
                        "但不要压成「马原」那种简称。只摘括号不算精简，名字本身也要压短。\n" +
                        "地点保留房号，楼名压到 1~2 字：`第五教学楼=五教`、`2号综合楼=2综`。\n" +
                        "本来就够短的和左右一样的不用写；整批都不用精简就只回「无需精简」。\n\n" +
                        "例：\n" +
                        "中药药剂学(江西中医药大学)(网络公选)=中药药剂学\n" +
                        "东校区4号教学楼B114教室=4号楼B114",
                )
            })
        }
    }

    // ── 教务 agent ──────────────────────────────────────────────

    suspend fun turn(
        cfg: AiByokConfig,
        messages: List<JsonObject>,
        tools: JsonArray,
    ): AiResult<AiAgentReply> {
        if (messages.isEmpty()) return AiResult.Error("对话是空的")
        val body = agentBody(cfg, messages, tools, stream = false)
        val result = post(cfg, body)
        if (result is AiResult.Error) return result
        val json = (result as AiResult.Ok).value
        val choice = json.optJsonArray("choices")?.firstOrNull() as? JsonObject
            ?: return AiResult.Error("供应商没返回结果")
        val message = choice.optJsonObject("message") ?: return AiResult.Error("供应商没返回消息")
        return AiResult.Ok(parseReply(message, choice.optString("finish_reason")))
    }

    suspend fun turnStream(
        cfg: AiByokConfig,
        messages: List<JsonObject>,
        tools: JsonArray,
        onEvent: suspend (AiAgentEvent) -> Unit,
    ): AiResult<AiAgentReply> {
        if (messages.isEmpty()) return AiResult.Error("对话是空的")
        val body = agentBody(cfg, messages, tools, stream = true)

        val content = StringBuilder()
        val reasoning = StringBuilder()
        val slots = mutableMapOf<Int, ToolSlot>()
        var finish = ""

        try {
            http.streamPost(
                url = endpoint(cfg.baseUrl),
                body = body.toString(),
                headers = authHeaders(cfg.apiKey),
            ) { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("data:")) return@streamPost
                val data = trimmed.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") return@streamPost
                val json = runCatching { parseJsonObject(data) }.getOrNull() ?: return@streamPost
                val choice = json.optJsonArray("choices")?.firstOrNull() as? JsonObject
                    ?: return@streamPost
                val delta = choice.optJsonObject("delta") ?: return@streamPost

                val rc = delta.optString("reasoning_content")
                if (rc.isNotEmpty()) {
                    reasoning.append(rc)
                    onEvent(AiAgentEvent.Reasoning(rc))
                }
                val c = delta.optString("content")
                if (c.isNotEmpty()) {
                    content.append(c)
                    onEvent(AiAgentEvent.Content(c))
                }
                delta.optJsonArray("tool_calls")?.forEach { element ->
                    val part = element as? JsonObject ?: return@forEach
                    val index = part.optInt("index", 0)
                    val slot = slots.getOrPut(index) { ToolSlot() }
                    val id = part.optString("id")
                    if (id.isNotEmpty()) slot.id = id
                    val fn = part.optJsonObject("function") ?: return@forEach
                    val name = fn.optString("name")
                    if (name.isNotEmpty()) slot.name += name
                    val args = fn.optString("arguments")
                    if (args.isNotEmpty()) slot.arguments += args
                }
                val done = choice.optString("finish_reason")
                if (done.isNotEmpty()) finish = done
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AiResult.Error("网络请求失败：${e.describe()}")
        }

        val calls = slots.entries.sortedBy { it.key }.map { it.value }
            .filter { it.name.isNotBlank() }
            .map { AiAgentToolCall(it.id.ifEmpty { it.name }, it.name, it.arguments.ifBlank { "{}" }) }
        return AiResult.Ok(
            AiAgentReply(
                content = content.toString(),
                toolCalls = calls,
                reasoningContent = reasoning.toString(),
                finishReason = finish,
            )
        )
    }

    private data class ToolSlot(
        var id: String = "",
        var name: String = "",
        var arguments: String = "",
    )

    private fun agentBody(
        cfg: AiByokConfig,
        messages: List<JsonObject>,
        tools: JsonArray,
        stream: Boolean,
    ): JsonObject = buildJsonObject {
        put("model", cfg.model)
        put("messages", JsonArray(AiEduAgentClient.compactForWire(messages)))
        // 与云端缺省一致：MiMo 只认 max_completion_tokens 且思考 token 也算在里面
        when {
            isMimoEndpoint(cfg.baseUrl) -> {
                put("temperature", 1.0)
                put("top_p", 0.95)
                put("thinking", buildJsonObject { put("type", "enabled") })
                put("max_completion_tokens", 131072)
            }
            // GPT-5.x 只接受 max_completion_tokens，且思考模型不接受自定义 temperature
            cfg.provider == OPENAI_PROVIDER -> {
                put("max_completion_tokens", 16384)
                put("reasoning_effort", "medium")
            }
            else -> {
                put("temperature", 1.0)
                put("top_p", 0.95)
                // DeepSeek 官方接受 32K；思考与输出共用预算，16K 容易被思考吃光。
                // 只对官方域名放宽，其它兼容服务仍用 16K，避免超出它们的上限。
                put(
                    "max_tokens",
                    if (cfg.baseUrl.contains("api.deepseek.com", ignoreCase = true)) 32768 else 16384,
                )
            }
        }
        if (tools.isNotEmpty()) {
            put("tools", JsonArray(tools.take(12)))
            put("tool_choice", "auto")
        }
        if (stream) put("stream", true)
    }

    private fun parseReply(message: JsonObject, finish: String): AiAgentReply {
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
            finishReason = finish,
        )
    }

    // ── 连通性 ──────────────────────────────────────────────────

    /**
     * 发一条最小请求，只验证地址、key、模型能不能接通。
     *
     * 不跑业务任务：通不通和精简得好不好是两回事，混在一起时用户分不清
     * 「没接通」还是「模型没按格式回」。
     *
     * @return 耗时（毫秒）
     */
    suspend fun ping(cfg: AiByokConfig): AiResult<Long> {
        if (!cfg.isUsable) return AiResult.Error("服务商配置不完整")
        val started = TimeSource.Monotonic.markNow()
        val body = buildJsonObject {
            put("model", cfg.model)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "ping")
                })
            })
            when {
                isMimoEndpoint(cfg.baseUrl) -> put("max_completion_tokens", 16)
                cfg.provider == OPENAI_PROVIDER -> {
                    put("max_completion_tokens", 16)
                    put("reasoning_effort", "none")
                }
                else -> put("max_tokens", 16)
            }
        }
        return when (val result = post(cfg, body)) {
            is AiResult.Error -> result
            is AiResult.Ok -> AiResult.Ok(started.elapsedNow().inWholeMilliseconds)
        }
    }

    // ── 通用请求 ────────────────────────────────────────────────

    private suspend fun complete(
        cfg: AiByokConfig,
        messages: JsonArray,
        temperature: Double,
        maxTokens: Int,
        reasoningEffort: String,
    ): AiResult<String> {
        val body = buildJsonObject {
            put("model", cfg.model)
            put("messages", messages)
            if (cfg.provider == OPENAI_PROVIDER) {
                // GPT-5.x：只认 max_completion_tokens，且思考模型不接受自定义 temperature
                put("max_completion_tokens", maxTokens)
                put("reasoning_effort", "none")
            } else {
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                if (reasoningEffort.isNotEmpty()) put("reasoning_effort", reasoningEffort)
            }
        }
        val result = post(cfg, body)
        if (result is AiResult.Error) return result
        val json = (result as AiResult.Ok).value
        val choice = json.optJsonArray("choices")?.firstOrNull() as? JsonObject
            ?: return AiResult.Error("供应商没返回结果")
        val content = choice.optJsonObject("message")?.optString("content").orEmpty()
        if (content.isBlank()) return AiResult.Error("供应商返回了空内容")
        return AiResult.Ok(content)
    }

    private suspend fun post(cfg: AiByokConfig, body: JsonObject): AiResult<JsonObject> {
        val result = try {
            http.post(endpoint(cfg.baseUrl), body.toString(), headers = authHeaders(cfg.apiKey))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AiResult.Error("网络请求失败：${e.describe()}")
        }
        if (!result.isSuccessful) return AiResult.Error(apiError(result))
        val json = runCatching { parseJsonObject(result.text) }.getOrNull()
            ?: return AiResult.Error("供应商响应不是 JSON：${result.text}")
        return AiResult.Ok(json)
    }

    /**
     * 把供应商的错误完整带出来。
     *
     * 优先取 `error.message` / `message` / `msg`；都没有时直接回**原始响应体**，
     * 不要只留一个状态码 —— 用户排查 401/404/模型名写错时全靠这段文字。
     */
    private fun apiError(result: HttpResult): String {
        val body = result.text.trim()
        val detail = runCatching {
            val obj = parseJsonObject(body)
            obj.optJsonObject("error")?.optString("message").orEmpty()
                .ifBlank { obj.optString("message") }
                .ifBlank { obj.optString("msg") }
                .ifBlank { obj.optString("error") }
        }.getOrDefault("")
        val text = detail.ifBlank { body }
        return "AI 返回 ${result.code}" + if (text.isNotBlank()) "：$text" else ""
    }

    private fun authHeaders(apiKey: String): Map<String, String> =
        mapOf("Authorization" to "Bearer $apiKey")

    private fun endpoint(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    private fun isMimoEndpoint(baseUrl: String): Boolean {
        val host = runCatching {
            baseUrl.trim()
                .substringAfter("://", "")
                .substringBefore('/')
                .substringBefore(':')
                .lowercase()
        }.getOrDefault("")
        return host == "xiaomimimo.com" || host.endsWith(".xiaomimimo.com")
    }

    // ── 精简结果解析（与云端逐条对齐） ──────────────────────────

    private val noShrinkSentinel = Regex("无需精简|不用精简|没有需要精简")

    private fun parseShrink(
        text: String,
        classrooms: List<String>,
        courses: List<String>,
    ): List<Pair<String, String>>? {
        val sources = classrooms + courses
        val out = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()
        var sawMapping = false

        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
                .removePrefix("|").removeSuffix("|")
                .replace("**", "")
                .removePrefix("- ").removePrefix("* ")
                .replace(Regex("^\\d+\\s*[.、)）]\\s*"), "")
                .trim()
                .trim('"', '\'', '「', '」', '『', '』')
                .trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("```")) continue

            var sep = line.indexOfFirst { it == '=' || it == '＝' }
            var sepLen = 1
            if (sep < 0) sep = line.indexOf('→')
            if (sep < 0) {
                sep = line.indexOf("->")
                sepLen = 2
            }
            if (sep < 0) sep = line.indexOfFirst { it == ':' || it == '：' }
            if (sep <= 0) continue
            val from = line.substring(0, sep).trim()
            val to = line.substring(sep + sepLen).trim()
            if (from.isEmpty() || to.isEmpty() || from.length > 40) continue

            val key = sources.firstNotNullOfOrNull { locateInSource(it, from) } ?: continue
            sawMapping = true
            if (to.length >= key.length) continue
            if (isPseudoShrink(key, to)) continue
            val keyIsClassroom = classrooms.any { it.contains(key) }
            if (keyIsClassroom && !roomNumberPreserved(key, to)) continue
            if (!seen.add(key)) continue
            out += key to to
        }

        if (sawMapping) return out
        if (noShrinkSentinel.containsMatchIn(text)) return emptyList()
        if (echoesSources(text, sources)) return emptyList()
        return null
    }

    private fun foldForMatch(c: Char): String = when {
        c.isWhitespace() -> ""
        c in 'A'..'Z' -> c.lowercaseChar().toString()
        c.code in 0xFF01..0xFF5E -> (c.code - 0xFEE0).toChar().toString()
        c.code == 0x3000 -> ""
        c == '—' || c == '－' || c == '–' || c == '―' -> "-"
        c == '“' || c == '”' -> "\""
        c == '‘' || c == '’' -> "'"
        c == '《' || c == '》' || c == '〈' || c == '〉' -> ""
        else -> c.toString()
    }

    private fun normalizeForMatch(s: String): Pair<String, List<Int>> {
        val norm = StringBuilder()
        val map = mutableListOf<Int>()
        s.forEachIndexed { index, c ->
            val folded = foldForMatch(c)
            folded.forEach { _ -> map += index }
            norm.append(folded)
        }
        return norm.toString() to map
    }

    private fun locateInSource(source: String, from: String): String? {
        if (source.contains(from)) return from
        val a = normalizeForMatch(from)
        if (a.first.isEmpty()) return null
        val b = normalizeForMatch(source)
        val at = b.first.indexOf(a.first)
        if (at < 0) return null
        return source.substring(b.second[at], b.second[at + a.first.length - 1] + 1)
    }

    private fun isPseudoShrink(from: String, to: String): Boolean =
        to == from.filterNot { it.isWhitespace() || it in "-－—·、,，" }

    private fun roomNumberPreserved(from: String, to: String): Boolean {
        val last = Regex("\\d+").findAll(from).lastOrNull()?.value ?: return true
        return to.contains(last)
    }

    private fun echoesSources(text: String, sources: List<String>): Boolean {
        if (sources.isEmpty()) return false
        val hit = sources.count { it.isNotEmpty() && text.contains(it) }
        return hit >= 2 && hit >= sources.size * 0.5
    }
}
