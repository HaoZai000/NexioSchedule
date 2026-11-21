package com.haooz.chedule.ui.ai

import com.haooz.chedule.data.ai.AiAgentEvent
import com.haooz.chedule.data.ai.AiEduAgentClient
import com.haooz.chedule.data.ai.AiEduScriptRecord
import com.haooz.chedule.data.ai.AiEduScriptStore
import com.haooz.chedule.data.ai.AiResult
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.data.platform.randomUuidV4
import com.haooz.chedule.ui.web.WebHost
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 聊天流里的一条记录。
 *
 * 刻意不按「消息」建模：harness 风格的界面要能看出「它想了多久、做了几步、哪步失败了」，
 * 所以思考块与工具步骤是平级的独立条目，而不是塞进一段 assistant 文本里。
 */
internal sealed interface EduChatItem {
    /** 生成时刻（毫秒），排序与展示用 */
    val atMs: Long

    data class User(val text: String, override val atMs: Long) : EduChatItem

    /** 一轮模型思考：正文是推理原文，[seconds] 是这轮从发请求到收到回复的耗时 */
    data class Thinking(
        val text: String,
        val seconds: Double,
        val empty: Boolean = false,
        /** 还在流式输出中：正文会长，标题显示「思考中」，不给展开箭头 */
        val streaming: Boolean = false,
        override val atMs: Long,
    ) : EduChatItem

    /** 一次工具调用：标题是人话，[detail] 是真实回执摘要 */
    data class Step(
        val title: String,
        val detail: String,
        val seconds: Double,
        val running: Boolean,
        val failed: Boolean,
        override val atMs: Long,
    ) : EduChatItem

    /** 模型说话（不是工具调用）：可能是提问、可能是收尾；[streaming] 表示正文还在增量输出 */
    data class Assistant(
        val text: String,
        override val atMs: Long,
        val streaming: Boolean = false,
    ) : EduChatItem
}

internal data class EduAgentUiState(
    val running: Boolean = false,
    val turn: Int = 0,
    /** 聊天流：用户 / 思考 / 步骤 / 助手发言，按发生顺序 */
    val items: List<EduChatItem> = emptyList(),
    /** 抓到的课程草稿；非 null 就说明有东西可以预览了 */
    val draft: EduAgentDraft? = null,
    /** 模型最后那段话 —— 没有草稿时它就是「缺什么」的说明 */
    val say: String = "",
    val error: String? = null,
    /** 服务端报的今日剩余步数 */
    val remaining: Int? = null,
    /** 本次分析开始的墙钟时间，用来在界面上跑「思考中 12s」 */
    val startedAtMs: Long = 0L,
    /** 结束时的总耗时（ms）；0 = 还没结束 */
    val elapsedMs: Long = 0L,
    /** 运行中排队的用户消息：当前这一轮结束后再送进对话 */
    val queued: List<String> = emptyList(),
) {
    /** 当前正在跑的那一步（没有就是 null），界面据此显示「正在…」 */
    val runningStep: EduChatItem.Step?
        get() = items.lastOrNull() as? EduChatItem.Step
}

/**
 * AI 教务导入的编排器：模型 → 工具 → 模型 → …，直到拿到课程或模型自己说完。
 *
 * 状态全部收敛在 [EduAgentUiState]，用普通类 + 页面 remember 持有：WebView 才是工具的所有者，
 * 页面销毁后继续跑没有意义。
 *
 * 运行中发来的话进入 [EduAgentUiState.queued]，在下一轮请求前插入，避免破坏
 * `tool_calls` 与结果的配对。
 */
internal class EduAgentSession {

    private val _state = MutableStateFlow(EduAgentUiState())
    val state: StateFlow<EduAgentUiState> = _state.asStateFlow()

    /**
     * 当前运行编号。SSE 是阻塞读，仅靠布尔标记无法立即停下；而停止后用户常马上重发，
     * 新循环会把标记复位。每次起跑领号，停止/重开时加一，旧循环对不上号即退出。
     */
    private var runId = 0

    /** 正在跑的那一轮的 Job：停止时取消它，连接才会真的断，而不是读到模型自己想完 */
    private var runJob: Job? = null

    /** 整段对话：反问 → 补充 → 续跑都接在同一条上下文上。 */
    private val conversation = mutableListOf<JsonObject>()

    /** 本次分析的页面地址，脚本历史按它区分学校。 */
    private var entryUrl: String = ""

    /**
     * 本次导入的会话 ID。
     *
     * 服务端按会话计免费额度：一次导入无论来回多少轮，只扣 1 次；
     * 「补充信息后继续」沿用同一个 ID，重新开始分析才换新的。
     */
    private var sessionId: String = ""

    /**
     * 用户在本次分析里发过几条消息。
     *
     * 用来挡住「还没问作息就直接跑脚本」：没有用户回答时，`timeSlots=unknown` 不被接受。
     */
    private var userTurns = 0

    /** 模型是否已经在对话里问过作息：没问过时不允许以「作息未知」直接执行脚本。 */
    private var askedTimeSlots = false

    /** 本次 RUN_SCRIPT 是否被作息 gate 拦下（拦下不计入脚本预算）。 */
    private var timeSlotGateBlocked = false

    private val client by lazy {
        AiEduAgentClient(
            http = createHttpService(
                // 思考可能持续数分钟：不设总时长，只靠 readSeconds 判「完全无数据」。
                HttpTimeouts(connectSeconds = 15, readSeconds = 300, callSeconds = 0)
            )
        )
    }

    /** 停止当前运行：作废旧循环、断开 SSE，并把运行中的条目定格。 */
    fun requestStop() {
        runId++
        runJob?.cancel()
        runJob = null
        // 停止可能打断 tool_calls 与结果的配对，下一次请求会被模型拒绝
        dropDanglingToolCalls()
        _state.update {
            it.copy(
                running = false,
                elapsedMs = now() - it.startedAtMs,
                items = it.items.sealRunningItems("已停止"),
            )
        }
    }

    fun reset() {
        runId++
        runJob?.cancel()
        runJob = null
        entryUrl = ""
        sessionId = ""
        userTurns = 0
        askedTimeSlots = false
        timeSlotGateBlocked = false
        conversation.clear()
        _state.value = EduAgentUiState()
    }

    /**
     * 运行中发来的话：入队，等这一轮的工具都执行完再插进对话。
     *
     * @return false = 当前没在跑，调用方应该走 [continueWith]
     */
    fun enqueue(text: String): Boolean {
        if (!_state.value.running) return false
        val clean = text.trim()
        if (clean.isEmpty()) return false
        _state.update { it.copy(queued = it.queued + clean) }
        return true
    }

    /**
     * 开始一次全新分析。
     *
     * @param host 当前教务网页宿主 —— 工具都通过它执行
     * @param entryUrl 当前页面地址（进提示词，让模型知道自己在哪个系统上）
     * @param runtimeHint 节次换算规则与页面网络记录等运行环境说明
     */
    suspend fun analyze(
        host: WebHost,
        entryUrl: String,
        runtimeHint: String,
    ) {
        val start = now()
        this.entryUrl = entryUrl
        this.sessionId = randomUuidV4()
        this.userTurns = 0
        this.askedTimeSlots = false
        this.timeSlotGateBlocked = false
        val gen = beginRun()
        _state.value = EduAgentUiState(
            running = true,
            startedAtMs = start,
            items = listOf(EduChatItem.User(EduAgentPrompts.firstUserMessage(), start)),
        )

        // 对话留在会话里：模型中途要信息时，用户补充的内容必须接在同一段上下文后面
        conversation.clear()
        conversation += AiEduAgentClient.systemMessage(EduAgentPrompts.systemPrompt(entryUrl, runtimeHint))
        conversation += AiEduAgentClient.userMessage(EduAgentPrompts.firstUserMessage())
        launchLoop(host, gen)
    }

    /** 用户在模型反问之后补充信息：接到同一段对话后面继续跑。 */
    suspend fun continueWith(host: WebHost, text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || conversation.isEmpty() || _state.value.running) return
        val gen = beginRun()
        _state.update {
            it.copy(
                running = true,
                error = null,
                say = "",
                startedAtMs = now(),
                elapsedMs = 0L,
                items = it.items + EduChatItem.User(clean, now()),
            )
        }
        conversation += AiEduAgentClient.userMessage(clean)
        userTurns++
        launchLoop(host, gen)
    }

    // ── 主循环 ──────────────────────────────────────────────────

    /** 领一个运行号：上一轮（如果还挂着）就地作废 */
    private fun beginRun(): Int {
        runJob?.cancel()
        runJob = null
        return ++runId
    }

    /**
     * 主循环跑在独立子作用域里，只为了持有可取消的 Job。
     * 取消这一层不会影响调用方；页面退出时调用方的取消仍会传下来。
     */
    private suspend fun launchLoop(host: WebHost, gen: Int) = coroutineScope {
        val self = coroutineContext[Job]
        runJob = self
        try {
            runLoop(host, gen)
        } finally {
            if (runJob === self) runJob = null
        }
    }

    private suspend fun runLoop(host: WebHost, gen: Int) {
        val tools = EduAgentTools(host)
        val messages = conversation

        var scriptRuns = 0
        var usedTurns = 0

        while (usedTurns < EduAgentPrompts.MAX_TURNS) {
            if (gen != runId) return

            // 排队的话在「下一轮请求前」插入：此时上一轮的 tool 结果都已配对完毕
            drainQueued()

            usedTurns++
            _state.update { it.copy(turn = usedTurns) }

            val turnStarted = now()
            // 思考块先占位：流式下正文会一块块长出来，等整轮结束才插入就又是「想完才显示」
            val thinkingIndex = beginThinking(turnStarted)
            val reasoning = StringBuilder()
            val content = StringBuilder()
            var assistantIndex = -1
            var lastFlushAt = 0L
            var lastContentFlushAt = 0L
            var streamError: String? = null

            val reply = when (
                val r = client.turnStream(messages, EduAgentPrompts.tools(), sessionId) { event ->
                    // 已被「停止 / 重开」作废：这条连接可能还没断干净，delta 一律丢弃，
                    // 否则旧思考块会继续长字（用户看到的就是两段思考同时在跑）
                    if (gen != runId) return@turnStream
                    // 每个 delta 都提交会让人一帧重组好几次，按 120ms 节流
                    val t = now()
                    when (event) {
                        is AiAgentEvent.Reasoning -> {
                            reasoning.append(event.text)
                            if (t - lastFlushAt >= 120) {
                                lastFlushAt = t
                                updateThinking(thinkingIndex, reasoning.toString())
                            }
                        }
                        is AiAgentEvent.Content -> {
                            content.append(event.text)
                            if (t - lastContentFlushAt >= 120) {
                                lastContentFlushAt = t
                                assistantIndex = streamAssistant(assistantIndex, content.toString())
                            }
                        }
                    }
                }
            ) {
                is AiResult.Ok -> r.value
                is AiResult.Error -> {
                    streamError = r.message
                    null
                }
            }
            val turnSeconds = (now() - turnStarted) / 1000.0

            // 取消可能落在这条连接的收尾上：别把「停止」翻译成一句失败提示
            if (gen != runId) return

            if (reply == null) {
                endThinking(thinkingIndex, reasoning.toString(), turnSeconds, failed = true)
                fail(streamError ?: "这一轮没有结果")
                return
            }
            if (reply.remaining != null) _state.update { it.copy(remaining = reply.remaining) }

            endThinking(
                index = thinkingIndex,
                fullText = reply.reasoningContent.ifBlank { reasoning.toString() },
                seconds = turnSeconds,
            )

            // 正文以收尾时的完整内容为准；平台没给增量（iOS 一次性读完）就补一条
            val said = reply.content.ifBlank { content.toString() }
            settleAssistant(assistantIndex, said)

            if (reply.content.isNotBlank()) {
                _state.update { s -> s.copy(say = reply.content) }
            }

            if (reply.toolCalls.isEmpty()) {
                // 模型说完了：有草稿就是收工，没草稿就是它在要信息（或干脆拒绝）
                if (said.isNotBlank()) {
                    if (!askedTimeSlots && said.asksTimeSlots()) askedTimeSlots = true
                    // ⚠ 这句必须留在历史里 —— 用户点「发送」补充信息时是一段连续对话，
                    // 少了这条 assistant，模型会忘掉自己刚刚问了什么。
                    conversation += AiEduAgentClient.assistantMessage(
                        content = said,
                        reasoningContent = reply.reasoningContent,
                    )
                }
                _state.update { it.copy(running = false, elapsedMs = now() - it.startedAtMs) }
                return
            }

            messages += AiEduAgentClient.assistantToolCallsMessage(
                content = reply.content,
                calls = reply.toolCalls,
                // 思考历史必须回填：MiMo 多轮工具调用带着它才接得上自己上一轮的判断
                reasoningContent = reply.reasoningContent,
            )

            for (call in reply.toolCalls) {
                if (gen != runId) return
                val isScriptCall = call.name == EduAgentPrompts.TOOL_RUN_SCRIPT
                if (isScriptCall) timeSlotGateBlocked = false
                val (resultText, patch) = execute(
                    tools,
                    call,
                    if (isScriptCall) scriptRuns + 1 else scriptRuns,
                )
                // 被作息 gate 拦下的调用没有真的执行，不占脚本预算
                if (isScriptCall && !timeSlotGateBlocked) scriptRuns++
                if (patch != null) {
                    _state.update { it.copy(draft = patch) }
                }
                // 工具执行期间被停止：结果不能再回填，否则 conversation 末尾会留下
                // 没有 tool_calls 对应的孤立 tool 消息，下一次请求会被模型拒绝
                if (gen != runId) {
                    dropDanglingToolCalls()
                    return
                }
                messages += AiEduAgentClient.toolResultMessage(call.id, resultText)
            }

            if (_state.value.draft != null) {
                // 课程已经拿到，没必要再让模型绕 —— 停在有结果的地方
                _state.update { it.copy(running = false, elapsedMs = now() - it.startedAtMs) }
                return
            }
            if (scriptRuns >= EduAgentPrompts.MAX_SCRIPT_RUNS) {
                fail("脚本已经跑了 $scriptRuns 次还没拿到可用课程，先停在这里。你可以补充信息后再试。")
                return
            }
        }

        fail("这一次分析了 $usedTurns 轮还没拿到课程，先停在这里。你可以补充信息后再试。")
    }

    /** 把排队的话插进对话，并在聊天流里标出来（用户此刻才看到自己那句话生效） */
    private fun drainQueued() {
        val queued = _state.value.queued
        if (queued.isEmpty()) return
        val at = now()
        queued.forEach { text ->
            conversation += AiEduAgentClient.userMessage(text)
        }
        userTurns += queued.size
        _state.update { it.copy(queued = emptyList(), items = it.items + queued.map { EduChatItem.User(it, at) }) }
    }

    /**
     * 执行一条工具调用。
     *
     * @return 回给模型的文本，以及（如果这次脚本跑出了课程）草稿
     */
    private suspend fun execute(
        tools: EduAgentTools,
        call: com.haooz.chedule.data.ai.AiAgentToolCall,
        runIndex: Int,
    ): Pair<String, EduAgentDraft?> {
        val args = runCatching { call.argumentsJson.toJsonObjectOrEmpty() }
            .getOrElse { emptyJsonObject() }

        return when (call.name) {
            EduAgentPrompts.TOOL_READ_PAGE -> {
                val index = beginStep("读取页面")
                val text = tools.readPage(
                    AiEduAgentClient.argString(args, "maxChars").toIntOrNull() ?: 6000
                )
                endStep(index, summarizePage(text), failed = false)
                text to null
            }

            EduAgentPrompts.TOOL_REQUEST -> {
                val path = AiEduAgentClient.argString(args, "path")
                val method = AiEduAgentClient.argString(args, "method").ifBlank { "GET" }
                val index = beginStep("查询接口 $path")
                val text = tools.request(path, method, AiEduAgentClient.argString(args, "body"))
                endStep(index, summarizeRequest(text), failed = text.contains("\"error\""))
                text to null
            }

            EduAgentPrompts.TOOL_RUN_SCRIPT -> {
                val timeSlots = AiEduAgentClient.argString(args, "timeSlots").lowercase()
                val script = AiEduAgentClient.stripFences(AiEduAgentClient.argString(args, "script"))
                // 没问过用户就不允许以「作息未知」直接开跑
                val blocked = timeSlots.isBlank() ||
                    (timeSlots == "unknown" && (!askedTimeSlots || userTurns == 0))
                if (blocked) {
                    timeSlotGateBlocked = true
                    val index = beginStep("执行适配脚本（先确认作息）")
                    endStep(index, "还没确认作息，先问用户", failed = true)
                    return "脚本没有执行：先确认作息。timeSlots 必填（api/user/custom/unknown）；" +
                        "要用 unknown，必须先在对话里问过用户作息并拿到回答。" to null
                }
                val index = beginStep("执行适配脚本（第 $runIndex 次）")
                val run = tools.runScript(script)
                // 每写一版都留档：失败的那几版同样是「这个教务系统长什么样」的证据
                AiEduScriptStore.append(
                    AiEduScriptRecord.create(
                        pageUrl = entryUrl,
                        note = AiEduAgentClient.argString(args, "note"),
                        script = script,
                        ok = run.ok,
                        courseCount = run.courseCount,
                        error = run.error,
                    )
                )
                val detail = when {
                    run.usableCount > 0 -> buildString {
                        append("拿到 ").append(run.usableCount).append(" 门课")
                        if (run.courseCount > run.usableCount) {
                            append("（").append(run.courseCount - run.usableCount).append(" 条缺字段已跳过）")
                        }
                    }
                    !run.error.isNullOrBlank() -> run.error.orEmpty()
                    run.courseCount > 0 -> "解析到 ${run.courseCount} 条，但没有一门有效"
                    run.toasts.isNotEmpty() -> run.toasts.last()
                    run.calls.isEmpty() -> "脚本没有调用桥（提前结束）"
                    else -> "没有拿到课程"
                }
                endStep(index, detail, failed = !run.ok || run.usableCount == 0)
                val payload = buildJsonObject {
                    put("ok", run.ok)
                    put("error", run.error)
                    put("calls", buildJsonArray { run.calls.forEach { add(JsonPrimitive(it)) } })
                    put("courseCount", run.courseCount)
                    put("usableCount", run.usableCount)
                    if (run.invalidNames.isNotEmpty()) {
                        put(
                            "invalidNames",
                            buildJsonArray { run.invalidNames.forEach { add(JsonPrimitive(it)) } },
                        )
                    }
                    if (run.invalidReasons.isNotEmpty()) {
                        put(
                            "invalidReasons",
                            buildJsonArray { run.invalidReasons.forEach { add(JsonPrimitive(it)) } },
                        )
                    }
                    put(
                        "firstCourseNames",
                        buildJsonArray { run.firstNames.forEach { add(JsonPrimitive(it)) } },
                    )
                    if (run.toasts.isNotEmpty()) {
                        put(
                            "toasts",
                            buildJsonArray { run.toasts.forEach { add(JsonPrimitive(it)) } },
                        )
                    }
                    if (run.logs.isNotEmpty()) {
                        put(
                            "logs",
                            buildJsonArray { run.logs.forEach { add(JsonPrimitive(it)) } },
                        )
                    }
                    if (run.calls.isEmpty()) run.env?.let { put("env", it) }
                }.toString() + buildString {
                    if (run.calls.isEmpty()) {
                        append("\n（脚本执行完了但一次桥调用都没有：多半是中途 return 或异步报错。")
                        append("看 toasts、logs 与 env 定位原因。）")
                    }
                    if (run.courseCount > 0 && run.usableCount == 0) {
                        append("\n（提交了 ${run.courseCount} 条课程，但没有一条通过 day/weeks/节次校验；")
                        append("按 invalidReasons 修脚本后重跑。）")
                    } else if (run.usableCount > 0 && run.usableCount < run.courseCount) {
                        append("\n（有 ${run.courseCount - run.usableCount} 条课程缺 day/weeks/节次，")
                        append("预览会跳过它们。）")
                    } else if (run.courseCount == 0) {
                        append("\n（courseCount 为 0 表示这次没拿到课程：检查接口地址、字段名、周次展开）")
                    }
                }
                val draft = if (run.usableCount > 0 && run.coursesJson != null) {
                    EduAgentDraft(
                        coursesJson = run.coursesJson,
                        configJson = run.configJson,
                        timeSlotsJson = run.timeSlotsJson,
                        comboJson = run.comboJson,
                        courseCount = run.courseCount,
                        script = script,
                        pageUrl = entryUrl,
                        sessionId = sessionId,
                    )
                } else null
                payload to draft
            }

            EduAgentPrompts.TOOL_NET_LOG -> {
                val action = AiEduAgentClient.argString(args, "action").ifBlank { "list" }.lowercase()
                val id = AiEduAgentClient.argString(args, "id")
                val maxChars = AiEduAgentClient.argString(args, "maxChars").toIntOrNull() ?: 12_000
                val index = beginStep(
                    when (action) {
                        "read" -> "读取接口 ${id.ifBlank { "?" }}"
                        "clear" -> "清空网络记录"
                        else -> "查看网络请求"
                    }
                )
                val text = tools.netLog(action, id, maxChars)
                val missing = text.startsWith("没有编号")
                endStep(
                    index = index,
                    detail = summarizeNetLog(action, text),
                    failed = missing,
                )
                text to null
            }

            EduAgentPrompts.TOOL_RELOAD_PAGE -> {
                val waitSeconds = AiEduAgentClient.argString(args, "waitSeconds")
                    .toLongOrNull()?.coerceIn(10L, 60L) ?: 25L
                val index = beginStep("刷新页面并等待加载")
                val result = tools.reloadAndWait(waitSeconds * 1000)
                endStep(
                    index = index,
                    detail = result.text.take(80),
                    failed = !result.ok,
                )
                result.text to null
            }

            else -> {
                val index = beginStep("未知工具 ${call.name}")
                endStep(index, "不在允许的工具表里", failed = true)
                "未知工具：${call.name}。可用工具只有 NET_LOG / RELOAD_PAGE / READ_PAGE / REQUEST / RUN_SCRIPT。" to null
            }
        }
    }

    // ── 聊天流写入 ──────────────────────────────────────────────

    /** 插一条空的思考占位，返回下标；流式 delta 会往它身上追加 */
    private fun beginThinking(atMs: Long): Int {
        var index = -1
        _state.update {
            val items = it.items + EduChatItem.Thinking(
                text = "",
                seconds = 0.0,
                empty = false,
                streaming = true,
                atMs = atMs,
            )
            index = items.lastIndex
            it.copy(items = items)
        }
        return index
    }

    private fun updateThinking(index: Int, text: String) {
        _state.update { s ->
            if (index !in s.items.indices) return@update s
            val item = s.items[index] as? EduChatItem.Thinking ?: return@update s
            s.copy(items = s.items.toMutableList().also { it[index] = item.copy(text = text) })
        }
    }

    private fun endThinking(index: Int, fullText: String, seconds: Double, failed: Boolean = false) {
        _state.update { s ->
            if (index !in s.items.indices) return@update s
            val item = s.items[index] as? EduChatItem.Thinking ?: return@update s
            val text = fullText.ifBlank { item.text }
            s.copy(
                items = s.items.toMutableList().also {
                    it[index] = item.copy(
                        text = text,
                        seconds = seconds,
                        streaming = false,
                        // 一个字都没有、也不是失败：这轮确实没给思考内容，整块不显示
                        empty = text.isBlank() && !failed,
                    )
                }
            )
        }
    }

    /**
     * 流式正文：第一条增量到达时插一条气泡，之后原地改写。
     *
     * @return 气泡在 items 里的下标（-1 = 还没有气泡）
     */
    private fun streamAssistant(index: Int, text: String): Int {
        if (index >= 0) {
            _state.update { s ->
                if (index !in s.items.indices) return@update s
                val item = s.items[index] as? EduChatItem.Assistant ?: return@update s
                s.copy(items = s.items.toMutableList().also { it[index] = item.copy(text = text) })
            }
            return index
        }
        var created = -1
        _state.update {
            val items = it.items + EduChatItem.Assistant(text = text, atMs = now(), streaming = true)
            created = items.lastIndex
            it.copy(items = items)
        }
        return created
    }

    /** 本轮收尾：把流式气泡定格成完整正文；没收到增量（一次性响应）就补一条。 */
    private fun settleAssistant(index: Int, text: String) {
        if (text.isBlank()) return
        if (index < 0) {
            _state.update { it.copy(items = it.items + EduChatItem.Assistant(text, now())) }
            return
        }
        _state.update { s ->
            if (index !in s.items.indices) return@update s
            val item = s.items[index] as? EduChatItem.Assistant ?: return@update s
            s.copy(
                items = s.items.toMutableList().also {
                    it[index] = item.copy(text = text, streaming = false)
                }
            )
        }
    }

    /** 插一条「正在跑」的步骤，返回它在 items 里的下标（结束时要按它替换） */
    private fun beginStep(title: String): Int {
        var index = -1
        _state.update {
            val items = it.items + EduChatItem.Step(
                title = title,
                detail = "",
                seconds = 0.0,
                running = true,
                failed = false,
                atMs = now(),
            )
            index = items.lastIndex
            it.copy(items = items)
        }
        return index
    }

    private fun endStep(index: Int, detail: String, failed: Boolean) {
        _state.update { s ->
            if (index !in s.items.indices) return@update s
            val step = s.items[index] as? EduChatItem.Step ?: return@update s
            val updated = step.copy(
                detail = detail,
                seconds = (now() - step.atMs) / 1000.0,
                running = false,
                failed = failed,
            )
            s.copy(items = s.items.toMutableList().also { it[index] = updated })
        }
    }

    private fun fail(message: String) {
        _state.update {
            it.copy(
                running = false,
                error = message,
                elapsedMs = if (it.startedAtMs > 0) now() - it.startedAtMs else 0L,
                items = it.items.sealRunningItems("未完成"),
            )
        }
    }

    /**
     * 停止会打断「tool_calls → tool 结果」的配对，配不上的请求下一次会被模型直接拒掉。
     * 这里把末尾那段没配完的来回摘掉，让对话停在一个合法的节点上。
     */
    private fun dropDanglingToolCalls() {
        while (true) {
            val last = conversation.lastOrNull() ?: return
            when {
                last["role"]?.jsonPrimitive?.contentOrNull == "tool" ->
                    conversation.removeAt(conversation.lastIndex)
                last["tool_calls"] != null ->
                    conversation.removeAt(conversation.lastIndex)
                else -> return
            }
        }
    }

    /** 收尾时把还挂着 running / streaming 的条目定格，否则界面会永远转圈或一直闪 */
    private fun List<EduChatItem>.sealRunningItems(reason: String): List<EduChatItem> {
        val at = now()
        return map { item ->
            when {
                item is EduChatItem.Step && item.running -> item.copy(running = false, detail = reason)
                item is EduChatItem.Assistant && item.streaming -> item.copy(streaming = false)
                // 思考块同样要收尾：否则「停止」之后它还挂着「思考中」，
                // 看上去就是没有被打断（还会继续往里写字）
                item is EduChatItem.Thinking && item.streaming -> item.copy(
                    streaming = false,
                    seconds = ((at - item.atMs) / 1000.0).coerceAtLeast(0.0),
                    empty = item.text.isBlank(),
                )
                else -> item
            }
        }
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    // ── 工具结果摘要（只给界面看，模型拿的是全文） ──

    private fun summarizePage(text: String): String {
        val obj = runCatching { text.toJsonObjectOrEmpty() }.getOrNull()
            ?: return AiEduAgentClient.clip(text, 60)
        val title = obj.get("title")?.jsonPrimitive?.contentOrNull ?: ""
        val links = (obj.get("links") as? JsonArray)?.size ?: 0
        val resources = (obj.get("resources") as? JsonArray)?.size ?: 0
        return "$title · $links 个链接 · $resources 个同源资源"
    }

    private fun summarizeNetLog(action: String, text: String): String = when (action) {
        "list" -> text.lineSequence().firstOrNull()?.take(60) ?: "无记录"
        "clear" -> "已清空"
        else -> {
            val status = Regex("""(?m)^status: (\d+)""").find(text)?.groupValues?.get(1)
            val size = Regex("""(?m)^size: (\d+)""").find(text)?.groupValues?.get(1)
            if (status == null) "读取完成" else "HTTP $status · ${size ?: "?"} 字"
        }
    }

    private fun summarizeRequest(text: String): String {
        val obj = runCatching { text.toJsonObjectOrEmpty() }.getOrNull() ?: return "返回 ${text.length} 字"
        obj.get("error")?.jsonPrimitive?.contentOrNull?.let { return it }
        val status = obj.get("status")?.jsonPrimitive?.contentOrNull ?: "?"
        val body = obj.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
        return "HTTP $status · ${body.length} 字"
    }
}

/** 模型这句反问里是否提到了作息（用来判断它有没有真的问过用户）。 */
private fun String.asksTimeSlots(): Boolean =
    contains("作息") || contains("第1节") || contains("第 1 节") ||
        contains("几点") || contains("上课时间") || contains("下课时间")

internal fun String.toJsonObjectOrEmpty(): JsonObject =
    runCatching { kotlinx.serialization.json.Json.parseToJsonElement(this).jsonObject }
        .getOrElse { emptyJsonObject() }

private fun emptyJsonObject(): JsonObject = JsonObject(emptyMap())
