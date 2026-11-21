package com.haooz.chedule.ui.ai

import com.haooz.chedule.data.ai.AiEduAgentClient
import com.haooz.chedule.data.util.optBoolean
import com.haooz.chedule.data.util.optInt
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optJsonObject
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonObject
import com.haooz.chedule.ui.web.EduNetEntry
import com.haooz.chedule.ui.web.EduNetLogStore
import com.haooz.chedule.ui.web.WebHost
import com.haooz.chedule.ui.web.evaluateJavascriptAwait
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 一次脚本执行的真实结果（全部来自页面里的沙箱记录，不看模型的自述）。 */
internal data class EduScriptRun(
    val ok: Boolean,
    val error: String?,
    val calls: List<String>,
    val courseCount: Int,
    /** 通过 day / weeks / 节次校验的课程数；0 表示预览一门都用不了 */
    val usableCount: Int = 0,
    /** 校验不通过的前几门课程名，回给模型用于修脚本 */
    val invalidNames: List<String> = emptyList(),
    /** 校验不通过的具体原因（字段名 + 实际值），用于让模型一次定位 */
    val invalidReasons: List<String> = emptyList(),
    val coursesJson: String?,
    val configJson: String?,
    val timeSlotsJson: String?,
    val comboJson: String?,
    val firstNames: List<String>,
    /** 脚本自己 showToast 的内容：提前 return 时这是唯一的线索 */
    val toasts: List<String> = emptyList(),
    /** 脚本 console 输出，用来定位「一次桥都没调就结束」 */
    val logs: List<String> = emptyList(),
    /** 运行环境快照（document / DOMParser / fetch / origin） */
    val env: JsonObject? = null,
)

/** 刷新页面的结果：`ok=false` 表示等不到新文档（可能超时或被重定向到登录页）。 */
internal data class EduReloadResult(
    val ok: Boolean,
    val text: String,
)

/** 一次分析里抓到的草稿：课程 + 脚本顺手交回的配置 / 作息。 */
internal data class EduAgentDraft(
    val coursesJson: String,
    val configJson: String?,
    val timeSlotsJson: String?,
    val comboJson: String?,
    val courseCount: Int,
    /** 产出这份草稿的那一版脚本源码（导入成功后要上传到服务端） */
    val script: String = "",
    /** 写脚本时的页面地址，上传时作为「哪个教务系统」的标识 */
    val pageUrl: String = "",
    /** 本次分析会话 ID，上传脚本时用来关联服务端保存的完整对话 */
    val sessionId: String = "",
)

/**
 * 三个工具的本地实现：READ_PAGE / REQUEST / RUN_SCRIPT。
 *
 * RUN_SCRIPT 执行期间把 `shiguangBridgePromise` 换成只记录、不落库的替身，结束后还原：
 * AI 产出的课程必须经用户预览，同时模型仍能拿到真实执行回执。
 */
internal class EduAgentTools(private val host: WebHost) {

    /** 读页面结构。返回给模型的 JSON 文本。 */
    suspend fun readPage(maxChars: Int): String {
        val limit = maxChars.coerceIn(1000, 12000)
        val js = READ_PAGE_JS.replace("__MAX__", limit.toString())
        return host.evaluateJavascriptAwait(js)?.let(::unwrapJsString)
            ?: "读取失败：当前平台不支持读取网页内容"
    }

    /** 打同源接口。 */
    suspend fun request(path: String, method: String, body: String): String {
        val js = REQUEST_JS
            .replace("__PATH__", JsonPrimitive(path).toString())
            .replace("__METHOD__", JsonPrimitive(method.uppercase()).toString())
            .replace("__BODY__", JsonPrimitive(body).toString())
        val first = host.evaluateJavascriptAwait(js, timeoutMs = 30_000L)?.let(::unwrapJsString)
            ?: return "请求失败：当前平台不支持从网页上下文发起请求"
        // 同源校验在 JS 里做，拒绝时直接返回一段 JSON 错误体
        if (first.startsWith("{")) return first.redactHiddenFields()
        val key = first.removePrefix("__poll__")
        if (!first.startsWith("__poll__")) return "请求没有发起：$first"

        val deadline = 30_000L
        var waited = 0L
        while (waited < deadline) {
            delay(200)
            waited += 200
            val raw = host.evaluateJavascriptAwait(
                "JSON.stringify(window[${JsonPrimitive(key)}] || null)"
            )?.let(::unwrapJsString) ?: continue
            if (raw == "null" || raw.isBlank()) continue
            val obj = runCatching { parseJsonObject(raw) }.getOrNull() ?: continue
            if (obj.optBoolean("pending")) continue
            return raw.redactHiddenFields()
        }
        return "请求超时：30 秒没有响应，可能卡在登录态或校园网络"
    }

    /** 执行模型写的脚本，等它真的跑完（沙箱记录回执）。 */
    suspend fun runScript(script: String): EduScriptRun {
        if (script.length > MAX_SCRIPT_CHARS) {
            return failed("脚本太长了（${script.length} 字，上限 $MAX_SCRIPT_CHARS）")
        }
        val wrapped = RUN_SCRIPT_JS.replace("__SCRIPT__", JsonPrimitive(script).toString())
        val started = host.evaluateJavascriptAwait(wrapped)?.let(::unwrapJsString)
        if (started != "started") {
            return failed("脚本没有启动：${started ?: "平台不支持执行脚本"}")
        }
        // evaluateJavascript 在 JS 返回时立刻回调，拿不到 async 的最终结果 —— 轮询沙箱状态
        val deadline = 90_000L
        var waited = 0L
        while (waited < deadline) {
            delay(200)
            waited += 200
            val raw = host.evaluateJavascriptAwait("JSON.stringify(window.__nexioAgentRun || null)")
                ?.let(::unwrapJsString) ?: continue
            if (raw == "null" || raw.isBlank()) continue
            val obj = runCatching { parseJsonObject(raw) }.getOrNull() ?: continue
            if (!obj.optBoolean("done")) continue
            return obj.toRun()
        }
        return failed("脚本超过 90 秒没有结束，可能卡在某个请求上")
    }

    /**
     * 读取页面自身的网络记录。
     *
     * @param action list / read / clear
     * @param id [action]=read 时的记录 id（形如 `n12`）
     * @param maxChars read 时单段正文的上限，默认 12000
     */
    fun netLog(action: String, id: String, maxChars: Int): String = when (action.lowercase()) {
        "list" -> listNetLog()
        "read" -> readNetLog(id, maxChars.coerceIn(1000, 32_000))
        "clear" -> {
            EduNetLogStore.clear()
            "已清空页面网络记录。"
        }
        else -> "action 只能是 list / read / clear。"
    }

    /**
     * 刷新页面并等新文档起来，给模型一个「重新暴露首屏请求」的手段。
     *
     * 只轮询本地记录里的顶层 document 标记，不依赖平台的 page-finished 回调：
     * 两端宿主都只保证 reload() 是异步的，没有统一的加载完成事件。
     */
    suspend fun reloadAndWait(waitMs: Long): EduReloadResult {
        val before = EduNetLogStore.latestTopDocumentTs()
        val startedAt = now()
        host.reload()

        val deadline = startedAt + waitMs
        var sawDocument = false
        var lastCount = EduNetLogStore.snapshot().size
        var quietSince = startedAt
        while (now() < deadline) {
            delay(300)
            if (!sawDocument && EduNetLogStore.latestTopDocumentTs() > before) {
                sawDocument = true
                quietSince = now()
            }
            if (sawDocument) {
                val count = EduNetLogStore.snapshot().size
                if (count != lastCount) {
                    lastCount = count
                    quietSince = now()
                } else if (now() - quietSince >= 1200) {
                    break
                }
            }
        }

        val newCount = EduNetLogStore.snapshot().count { it.ts >= startedAt && it.kind != "document" }
        return if (!sawDocument) {
            EduReloadResult(
                ok = false,
                text = "刷新后 ${waitMs / 1000} 秒内没有检测到新文档：页面可能跳到了登录页，" +
                    "或这个站点不允许直接 reload。可以让用户手动点一次「课表查询」，再调 net_log list。",
            )
        } else {
            EduReloadResult(
                ok = true,
                text = "页面已刷新，本次新捕获 $newCount 条请求。用 net_log list 查看，再挑候选读响应。",
            )
        }
    }

    private fun listNetLog(): String {
        val all = EduNetLogStore.snapshot().filter { it.kind != "document" }
        if (all.isEmpty()) {
            return "还没有记录到任何页面请求。可以让用户手动点一次「课表查询 / 我的课表」，" +
                "或调用 RELOAD_PAGE 刷新一次后再看。"
        }
        val recent = all.takeLast(40)
        return buildString {
            append("共 ").append(all.size).append(" 条请求，显示最近 ").append(recent.size).append(" 条。")
            append("用 net_log read + id 读取单条响应。\n")
            recent.forEach { entry -> append(formatSummary(entry)).append('\n') }
        }
    }

    private fun formatSummary(entry: EduNetEntry): String = buildString {
        append('#').append(entry.id).append(' ')
        append(entry.method).append(' ').append(entry.url.take(240))
        append(" → ").append(if (entry.status > 0) entry.status else "未完成")
        if (entry.contentType.isNotBlank()) append(' ').append(entry.contentType.take(60))
        if (entry.size > 0) append(' ').append(entry.size).append('字')
        if (entry.json) append(" json")
        if (entry.keys.isNotEmpty()) append(" keys=").append(entry.keys.joinToString(","))
        if (entry.reqBody.isNotBlank()) {
            append(" req=").append(entry.reqBody.replace('\n', ' ').take(80))
        }
        if (entry.error.isNotBlank()) append(" error=").append(entry.error.take(80))
    }

    private fun readNetLog(id: String, maxChars: Int): String {
        val clean = id.trim().removePrefix("#")
        val entry = EduNetLogStore.snapshot().lastOrNull { it.id == clean }
            ?: return "没有编号为 #$clean 的记录。先调 net_log list 看现有请求。"
        return buildString {
            append("id: ").append(entry.id).append('\n')
            append("kind: ").append(entry.kind).append('\n')
            append("method: ").append(entry.method).append('\n')
            append("url: ").append(entry.url).append('\n')
            append("status: ").append(entry.status).append('\n')
            append("contentType: ").append(entry.contentType).append('\n')
            append("size: ").append(entry.size).append('\n')
            if (entry.keys.isNotEmpty()) append("keys: ").append(entry.keys.joinToString(",")).append('\n')
            if (entry.error.isNotBlank()) append("error: ").append(entry.error).append('\n')
            if (entry.reqBody.isNotBlank()) {
                append("--- 请求体 ---\n")
                    .append(AiEduAgentClient.clip(entry.reqBody.redactHiddenFields(), maxChars)).append('\n')
            }
            if (entry.respBody.isNotBlank()) {
                append("--- 响应 ---\n")
                    .append(AiEduAgentClient.clip(entry.respBody.redactHiddenFields(), maxChars))
            } else {
                append("--- 响应 ---\n（没有可读正文：可能是二进制、跨域 opaque 响应或请求未完成）")
            }
        }
    }

    /**
     * ASP.NET 的 __VIEWSTATE 等隐藏域动辄几十 KB。
     * 原样回给模型会被整段贴进思考、直接撞到输出上限；这里换成占位，脚本运行时再从 DOM 读。
     */
    private fun String.redactHiddenFields(): String = HIDDEN_FIELD.replace(this) { m ->
        val size = m.groupValues[3].length
        "${m.groupValues[1]}${m.groupValues[2]}[已省略 $size 字，脚本里用 document.querySelector 读取]"
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private fun kotlinx.serialization.json.JsonObject.toRun(): EduScriptRun {
        val error = optString("error")
        return EduScriptRun(
            ok = optBoolean("ok") && error.isEmpty(),
            error = error.takeIf { it.isNotBlank() },
            calls = optJsonArray("calls")?.mapNotNull { element ->
                val item = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val kind = item.optString("kind")
                if (kind.isEmpty()) null else if (item.optBoolean("ok")) "$kind ✓" else "$kind ✗"
            }.orEmpty(),
            courseCount = optInt("courseCount", 0),
            usableCount = optInt("usableCount", 0),
            invalidNames = optJsonArray("invalidNames")?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }.orEmpty(),
            invalidReasons = optJsonArray("invalidReasons")?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }.orEmpty(),
            coursesJson = optString("coursesJson").takeIf { it.isNotBlank() },
            configJson = optString("configJson").takeIf { it.isNotBlank() },
            timeSlotsJson = optString("timeSlotsJson").takeIf { it.isNotBlank() },
            comboJson = optString("comboJson").takeIf { it.isNotBlank() },
            firstNames = optJsonArray("firstNames")?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }.orEmpty(),
            toasts = optJsonArray("toasts")?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }.orEmpty(),
            logs = optJsonArray("logs")?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }.orEmpty(),
            env = optJsonObject("env"),
        )
    }

    private fun failed(message: String) = EduScriptRun(
        ok = false, error = message, calls = emptyList(), courseCount = 0,
        coursesJson = null, configJson = null, timeSlotsJson = null, comboJson = null,
        firstNames = emptyList(),
    )

    companion object {
        const val MAX_SCRIPT_CHARS = 60_000

        /** ASP.NET 隐藏域：值通常几十 KB，原样回给模型会被贴进思考并撞到输出上限。 */
        private val HIDDEN_FIELD = Regex(
            """(__VIEWSTATEGENERATOR|__VIEWSTATE|__EVENTVALIDATION|__RequestVerificationToken)""" +
                """(?![A-Za-z0-9])([\s\S]{0,120}?)([A-Za-z0-9+/%_\-][A-Za-z0-9+/=%_\-]{299,})"""
        )

        /**
         * 平台差异归一：Android 的 `evaluateJavascript` 回调给的是 JSON 字面量
         * （字符串会带一层引号与转义），iOS 给的是原文。这里统一取原文。
         */
        fun unwrapJsString(raw: String): String {
            val text = raw.trim()
            if (text.length < 2 || !text.startsWith("\"")) return text
            return runCatching {
                val element = Json.parseToJsonElement(text)
                (element as? JsonPrimitive)?.contentOrNull ?: text
            }.getOrDefault(text)
        }

        private val READ_PAGE_JS = """
            (() => {
              const clip = (s, n) => { const t = String(s == null ? '' : s).replace(/\s+/g, ' ').trim();
                return t.length > n ? t.slice(0, n) + '…' : t; };
              const redact = (u) => { try { const x = new URL(u, location.href);
                for (const k of Array.from(x.searchParams.keys()))
                  if (/token|ticket|password|secret|auth|cookie|session|sign/i.test(k)) x.searchParams.set(k, '***');
                return x.pathname + (x.search || ''); } catch (e) { return String(u || '').slice(0, 200); } };
              const same = (u) => { try { return new URL(u, location.href).origin === location.origin; } catch (e) { return false; } };
              const text = clip(document.body ? document.body.innerText : '', __MAX__);
              const forms = Array.from(document.forms).slice(0, 8).map((f) => ({
                action: redact(f.action || ''), method: String(f.method || 'get').toUpperCase(),
                fields: Array.from(f.elements || []).slice(0, 14).map((e) => e.name || e.id).filter(Boolean)
              }));
              const links = Array.from(document.querySelectorAll('a[href]')).slice(0, 70)
                .map((a) => ({ text: clip(a.textContent, 24), href: redact(a.href) }))
                .filter((x) => x.text && x.href);
              const res = [];
              try { performance.getEntriesByType('resource').forEach((r) => { if (same(r.name)) res.push(redact(r.name)); }); } catch (e) {}
              const tables = Array.from(document.querySelectorAll('table')).slice(0, 6).map((t) => ({
                rows: t.rows ? t.rows.length : 0,
                cols: (t.rows && t.rows[0]) ? t.rows[0].cells.length : 0,
                head: (t.rows && t.rows[0]) ? Array.from(t.rows[0].cells).slice(0, 10).map((c) => clip(c.innerText, 10)) : []
              }));
              const frames = Array.from(document.querySelectorAll('iframe')).slice(0, 8)
                .map((f) => redact(f.src || '')).filter(Boolean);
              return JSON.stringify({
                url: redact(location.href), title: clip(document.title, 80), text: text,
                forms: forms, links: links, resources: Array.from(new Set(res)).slice(-70),
                tables: tables, frames: frames
              });
            })()
        """.trimIndent()

        private val REQUEST_JS = """
            (() => {
              const key = '__nexioAgentReq_' + Math.random().toString(36).slice(2);
              const controller = new AbortController();
              window[key] = { pending: true, cancel: () => controller.abort() };
              const timer = setTimeout(() => controller.abort(), 20000);
              const path = __PATH__, method = __METHOD__, body = __BODY__;
              const target = new URL(path, location.href).href;
              if (new URL(target).origin !== location.origin) {
                clearTimeout(timer);
                return JSON.stringify({ error: '只能请求当前教务站点的同源地址：' + target });
              }
              const init = { method: method, credentials: 'same-origin', signal: controller.signal };
              if (method !== 'GET' && body) {
                init.body = body;
                init.headers = { 'Content-Type': body.trim().startsWith('{') || body.trim().startsWith('[')
                  ? 'application/json' : 'application/x-www-form-urlencoded' };
              }
              // 标记这是工具自己的请求，网络记录器跳过它（模型已经拿到这份响应）
              window.__nexioNetSkip = true;
              var pendingFetch;
              try { pendingFetch = fetch(target, init); } finally { window.__nexioNetSkip = false; }
              pendingFetch.then(async (r) => {
                if (new URL(r.url).origin !== location.origin) {
                  window[key] = { error: '接口跳转到了其他登录地址，请重新登录后再试' };
                  return;
                }
                let text = await r.text();
                if (text.length > 400000) text = text.slice(0, 400000) + '…（已截断）';
                try {
                  text = JSON.stringify(JSON.parse(text), (k, v) =>
                    /password|token|ticket|cookie|secret|auth|session/i.test(k) ? '***' : v);
                } catch (e) {}
                window[key] = { status: r.status, type: r.headers.get('content-type') || '', text: text };
              }).catch((e) => {
                window[key] = { error: '请求失败：' + (e && e.message ? e.message : '请检查登录态与校园网络') };
              }).finally(() => clearTimeout(timer));
              return '__poll__' + key;
            })()
        """.trimIndent()

        private val RUN_SCRIPT_JS = """
            (() => {
              const script = __SCRIPT__;
              const run = { done: false, ok: false, error: '', calls: [], toasts: [], logs: [],
                courseCount: 0, usableCount: 0, invalidNames: [], invalidReasons: [],
                coursesJson: '', configJson: '', timeSlotsJson: '', comboJson: '',
                firstNames: [], env: null };
              window.__nexioAgentRun = run;
              const safe = (v) => { try { return String(v).slice(0, 600); } catch (e) { return ''; } };
              const clockOk = (v) => {
                const m = String(v == null ? '' : v).trim().match(/^(\d{1,2}):(\d{2})$/);
                if (!m) return false;
                const h = Number(m[1]), mi = Number(m[2]);
                return h >= 0 && h <= 23 && mi >= 0 && mi <= 59;
              };
              const weeksOk = (weeks) => Array.isArray(weeks) && weeks.some((w) => {
                const n = Number(w);
                return Number.isInteger(n) && n >= 1 && n <= 30;
              });
              // 与预览页 parseDraft 的有效性规则保持一致，并给出字段级原因，避免模型瞎猜
              const courseIssue = (c) => {
                if (!c || typeof c !== 'object' || Array.isArray(c)) return '不是对象';
                if (!String(c.name == null ? '' : c.name).trim()) return '缺 name';
                const day = Number(c.day);
                if (!(day >= 1 && day <= 7)) return 'day 不是 1-7（收到 ' + safe(c.day) + '）';
                if (!Array.isArray(c.weeks)) {
                  return 'weeks 不是数组（收到 ' + (c.weeks == null ? 'null' : typeof c.weeks) + '）';
                }
                if (!weeksOk(c.weeks)) {
                  return 'weeks 里没有 1-30 的整数（收到 ' + safe(JSON.stringify(c.weeks)) + '）';
                }
                const custom = !!c.isCustomTime && clockOk(c.customStartTime) && clockOk(c.customEndTime);
                const ss = Number(c.startSection), es = Number(c.endSection);
                if (!custom && !(ss >= 1 && es >= ss)) {
                  return '缺节次或时间（startSection=' + safe(c.startSection) +
                    ', endSection=' + safe(c.endSection) + '）';
                }
                return '';
              };
              const record = (kind) => async (json) => {
                let parsed = null;
                try { parsed = JSON.parse(json); }
                catch (e) {
                  run.calls.push({ kind: kind, ok: false });
                  throw new Error(kind + ' 的参数不是合法 JSON');
                }
                if (kind === 'saveImportedCourses') {
                  const list = Array.isArray(parsed) ? parsed : [];
                  if (list.length === 0) {
                    run.calls.push({ kind: kind, ok: false });
                    throw new Error('saveImportedCourses 不能为空：至少提交一门课');
                  }
                  const issues = list.map((c) => courseIssue(c));
                  const usable = list.filter((c, i) => !issues[i]);
                  run.courseCount = list.length;
                  run.usableCount = usable.length;
                  run.invalidNames = list.filter((c, i) => !!issues[i])
                    .slice(0, 5).map((c) => safe(c && c.name));
                  run.invalidReasons = list.map((c, i) => {
                    if (!issues[i]) return '';
                    const keys = (c && typeof c === 'object' && !Array.isArray(c))
                      ? Object.keys(c).slice(0, 10).join(',') : '无';
                    return safe((c && c.name ? c.name : '(未命名)') + '：' + issues[i] + '；keys: ' + keys);
                  }).filter(Boolean).slice(0, 3);
                  if (usable.length === 0) {
                    run.calls.push({ kind: kind, ok: false });
                    throw new Error('saveImportedCourses 里没有一门有效课程：' +
                      (run.invalidReasons[0] || '每门都需要 day(1-7)、weeks 非空，以及节次或真实起止时间'));
                  }
                  run.coursesJson = json;
                  run.firstNames = usable.slice(0, 5).map((c) => safe(c && c.name));
                  run.calls.push({ kind: kind, ok: true });
                  // 真实桥成功时 resolve 的是 true，不是课程数
                  return true;
                }
                if (kind === 'saveCourseConfig') {
                  // 空对象会把用户已有的开学日 / 总周数清掉：拿不到就整段不要调用
                  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed) ||
                      Object.keys(parsed).length === 0) {
                    run.calls.push({ kind: kind, ok: false });
                    throw new Error('saveCourseConfig 不能为空：拿不到配置就整段不要调用');
                  }
                  run.configJson = json;
                }
                if (kind === 'savePresetTimeSlots') {
                  if (!Array.isArray(parsed) || parsed.length === 0) { run.calls.push({ kind: kind, ok: false });
                    throw new Error('savePresetTimeSlots 不能为空数组：拿不到作息就整段不要调用'); }
                  // 同一节次出现两套作息时不能直接收：先按「课多的一套做标准、其余走自定义时间」拆开，
                  // 否则标准作息会把另一套的课整体挪到错误时间上，而界面上完全看不出来
                  const seen = new Map();
                  for (const slot of parsed) {
                    const number = Number(slot && slot.number);
                    const start = String((slot && slot.startTime) || '');
                    const end = String((slot && slot.endTime) || '');
                    if (!(number > 0) || !start || !end) {
                      run.calls.push({ kind: kind, ok: false });
                      throw new Error('savePresetTimeSlots 每一项都需要 number/startTime/endTime');
                    }
                    const range = start + '-' + end;
                    if (seen.has(number) && seen.get(number) !== range) {
                      run.calls.push({ kind: kind, ok: false });
                      throw new Error('同一节次出现两套作息：请以覆盖课程最多的一套作为标准作息，其余课程改用 isCustomTime=true + customStartTime/customEndTime');
                    }
                    seen.set(number, range);
                  }
                  run.timeSlotsJson = json;
                }
                if (kind === 'saveComboSchedule') run.comboJson = json;
                run.calls.push({ kind: kind, ok: true });
                return true;
              };
              const sandbox = {
                saveCourseConfig: record('saveCourseConfig'),
                savePresetTimeSlots: record('savePresetTimeSlots'),
                saveImportedCourses: record('saveImportedCourses'),
                saveComboSchedule: record('saveComboSchedule'),
                showAlert: async (title, content, confirmText) => {
                  if (typeof title !== 'string' || typeof content !== 'string' || typeof confirmText !== 'string') {
                    run.calls.push({ kind: 'showAlert', ok: false });
                    throw new Error('showAlert 参数不对：应为 showAlert(标题, 内容, 确认按钮文字)');
                  }
                  run.calls.push({ kind: 'showAlert', ok: true });
                  return true;
                },
                showPrompt: async () => { throw new Error('AI 试跑里不能用 showPrompt，请用 showSingleSelection 或直接使用已知信息'); },
                // 标准适配器会让最终用户选学年/学期；试跑时没人在界面上点，
                // 这里直接取默认项（通常就是页面当前学期）。脚本里的选择器原样保留，上传后照常交互。
                showSingleSelection: async (title, optionsJson, defaultIndex) => {
                  let list = null;
                  try { list = JSON.parse(optionsJson); } catch (e) {}
                  if (!Array.isArray(list)) {
                    run.calls.push({ kind: 'showSingleSelection', ok: false });
                    throw new Error('showSingleSelection 参数不对：应为 showSingleSelection(标题, JSON.stringify(选项数组), 默认下标)');
                  }
                  let index = Number(defaultIndex);
                  if (!Number.isFinite(index) || index < 0) index = 0;
                  index = list.length > 0 ? Math.min(Math.floor(index), list.length - 1) : -1;
                  run.calls.push({ kind: 'showSingleSelection', ok: true });
                  return index;
                }
              };
              // 缓存真正的桥：超时后沙箱会留在 window 上，下一轮必须恢复到真桥而不是上一轮沙箱
              if (!window.__nexioRealBridgePromise) {
                window.__nexioRealBridgePromise = window.shiguangBridgePromise;
              }
              if (!window.__nexioRealBridge) {
                window.__nexioRealBridge = window.shiguangBridge;
              }
              const origPromise = window.__nexioRealBridgePromise;
              const origBridge = window.__nexioRealBridge;
              const origConsole = { log: console.log, warn: console.warn, error: console.error };
              const origFetch = window.fetch;
              const origXhrSend = window.XMLHttpRequest && window.XMLHttpRequest.prototype.send;
              let pending = 0;
              let fetchHooked = false;
              let xhrHooked = false;
              const capture = (level) => function () {
                if (run.logs.length < 40) {
                  run.logs.push(level + ': ' + Array.prototype.slice.call(arguments).map(safe).join(' '));
                }
              };
              window.shiguangBridgePromise = sandbox;
              window.shiguangBridge = {
                showToast: (t) => { if (run.toasts.length < 20) run.toasts.push(safe(t)); },
                notifyTaskCompletion: () => {}
              };
              console.log = capture('log');
              console.warn = capture('warn');
              console.error = capture('error');
              try {
                if (typeof origFetch === 'function') {
                  window.fetch = function () {
                    pending++;
                    return origFetch.apply(this, arguments).finally(() => { pending = Math.max(0, pending - 1); });
                  };
                  fetchHooked = true;
                }
              } catch (e) {}
              try {
                if (origXhrSend) {
                  window.XMLHttpRequest.prototype.send = function () {
                    pending++;
                    this.addEventListener('loadend', () => { pending = Math.max(0, pending - 1); });
                    return origXhrSend.apply(this, arguments);
                  };
                  xhrHooked = true;
                }
              } catch (e) {}
              const onRejection = (e) => {
                const reason = e && e.reason;
                const msg = safe(reason && reason.message ? reason.message : reason);
                // 一次成功都没发生过才当成脚本错误；已经拿到结果后的页面噪音只记日志
                if (!run.error && !run.calls.some((c) => c.ok)) run.error = msg;
                else if (run.logs.length < 40) run.logs.push('unhandled: ' + msg);
              };
              window.addEventListener('unhandledrejection', onRejection);
              run.env = {
                document: typeof document,
                domParser: typeof DOMParser,
                fetch: typeof fetch,
                origin: (typeof location !== 'undefined' ? String(location.origin) : '')
              };
              const restoreHooks = () => {
                console.log = origConsole.log;
                console.warn = origConsole.warn;
                console.error = origConsole.error;
                if (fetchHooked) window.fetch = origFetch;
                if (xhrHooked) window.XMLHttpRequest.prototype.send = origXhrSend;
                window.removeEventListener('unhandledrejection', onRejection);
              };
              const restoreBridge = () => {
                window.shiguangBridgePromise = origPromise;
                window.shiguangBridge = origBridge;
              };
              const restore = () => {
                restoreHooks();
                restoreBridge();
              };
              // 脚本通常是「定义完直接 runImportFlow();」的 fire-and-forget：外层函数会立刻返回，
              // 此时桥和网络请求其实还在跑。必须等它真正停下来，再恢复沙箱并标记完成。
              const finishWhenIdle = () => {
                const startedAt = Date.now();
                let lastCalls = run.calls.length;
                let quietSince = startedAt;
                const tick = () => {
                  const now = Date.now();
                  if (run.calls.length !== lastCalls) {
                    lastCalls = run.calls.length;
                    quietSince = now;
                  }
                  if (pending === 0 && now - quietSince >= 800) {
                    restore();
                    run.done = true;
                    return;
                  }
                  if (now - startedAt > 60000) {
                    // 脚本可能还挂在某个请求上：只还原 hooks，桥继续保持沙箱，
                    // 避免它稍后恢复执行时绕过预览直接写真实课程表。
                    restoreHooks();
                    run.done = true;
                    return;
                  }
                  setTimeout(tick, 200);
                };
                setTimeout(tick, 200);
              };
              (async () => {
                try {
                  const factory = new Function('return (async () => {\n' + script + '\n})()');
                  await factory();
                  run.ok = true;
                  finishWhenIdle();
                } catch (e) {
                  run.error = safe(e && e.message ? e.message : e);
                  restore();
                  run.done = true;
                }
              })();
              return 'started';
            })()
        """.trimIndent()
    }
}
