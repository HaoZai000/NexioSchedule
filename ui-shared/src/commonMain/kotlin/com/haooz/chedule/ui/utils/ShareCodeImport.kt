package com.haooz.chedule.ui.utils

import com.haooz.chedule.data.platform.HttpService
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.data.util.optBoolean
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optJsonObject
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.school.ScriptRepository
import com.haooz.chedule.data.util.jsonToPlainValue
import com.haooz.chedule.data.util.parseJsonObject
import com.haooz.chedule.i18n.L
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 两条**纯网络**课表导入：本站分享口令 + 星链课表分享码。
 *
 * 对应 Android `ShareCodeApi.fetchShare` 与 `ThirdPartyShareImporter.importStarLink`；
 * Android 那两份还在用 OkHttp + Gson + JSONObject，为零回归不去动它们，
 * 这里复用共享 [HttpService] 与 JSON 层重抄逻辑。
 *
 * WakeUp 那条要跑**无头 WebView**（云端适配脚本），各平台实现不同，由 [wakeUpRunner] 注入。
 *
 * ⚠ **接口地址与字段名与服务端是契约**，逐字不能改（改了 Android 也一起坏）。
 */
object ShareCodeImport {

    /**
     * WakeUp 导入的无头浏览器实现，参数是 `(shareKey, 适配脚本)`。
     *
     * iOS 用 WKWebView（`IosWakeUpImport`）、Android 用 WebView；null = 该平台不支持。
     */
    var wakeUpRunner: (suspend (shareKey: String, script: String) -> ThirdPartySharePayload)? = null

    /** 本站口令服务；与 Android `ShareCodeApi.BASE_URL` / `IosScheduleShare` 逐字一致 */
    private const val SHARE_BASE_URL = "http://182.92.193.223:3000/api/share"

    /** 星链课表；与 Android `ThirdPartyShareImporter` 的 StarLink 分支逐字一致 */
    private const val STARLINK_BASE_URL = "https://api.starlinkkb.cn/share/curriculum/"

    private val http: HttpService by lazy {
        createHttpService(HttpTimeouts(connectSeconds = 12, readSeconds = 20, callSeconds = 30))
    }

    // ── 1. 本站分享口令取回 ──────────────────────────────────────────

    /**
     * 按口令取回分享的课表。
     *
     * 错误文案与 Android 逐字对齐（页面直接 `showToast(e.message)`）：空口令 → 「请输入口令」，
     * 404 → 「口令不存在或已过期」，其余 HTTP 失败先取服务端的 `error` 字段。
     */
    suspend fun fetchShareCode(rawCode: String): Result<ShareCodeFetchResult> =
        withContext(ioDispatcher) {
            runCatching {
                val trimmed = rawCode.trim()
                if (trimmed.isBlank()) throw IllegalArgumentException(L("请输入口令", "Please enter a share code")())
                val url = "$SHARE_BASE_URL?code=${encodeQuery(trimmed)}"
                val resp = http.get(url)
                val text = resp.text
                val json = runCatching { parseJsonObject(text) }.getOrNull()
                if (!resp.isSuccessful) {
                    val err = json?.optString("error").orEmpty()
                    throw IllegalStateException(
                        err.ifBlank {
                            if (resp.code == 404) L("口令不存在或已过期", "Share code not found or expired")() else L("服务器错误 HTTP %s", "Server error HTTP %s")(resp.code)
                        }
                    )
                }
                if (json == null || !json.optBoolean("ok", false)) {
                    throw IllegalStateException(L("口令不存在或已过期", "Share code not found or expired")())
                }
                val dataObj = json.optJsonObject("schedule_data")
                    ?: throw IllegalStateException(L("课表数据无效", "Invalid schedule data")())
                @Suppress("UNCHECKED_CAST")
                val data = jsonToPlainValue(dataObj) as? Map<String, Any>
                    ?: throw IllegalStateException(L("课表数据无效", "Invalid schedule data")())
                ShareCodeFetchResult(
                    code = json.optString("code").ifBlank { trimmed },
                    scheduleName = json.optString("schedule_name").ifBlank { L("分享的课表", "Shared Schedule")() },
                    scheduleData = data,
                )
            }
        }

    // ── 2. 第三方（WakeUp / 星链）口令导入 ───────────────────────────

    /**
     * 按来源分发。WakeUp 走无头 WKWebView 跑适配脚本，星链是纯 HTTP。
     *
     * 总超时 45s 与 Android `importWakeUp` 的 `withTimeout(45_000L)` 对齐 ——
     * 星链本来就有自己的连接/读超时，这里只是兜底，别再往上加。
     */
    suspend fun importThirdParty(
        source: ThirdPartyShareSource,
        rawInput: String,
    ): Result<ThirdPartySharePayload> =
        runCatching {
            withTimeout(45_000L) {
                val code = extractShareKey(source, rawInput)
                if (code.isBlank()) throw IllegalArgumentException(L("请输入%s", "Please enter %s")(source.inputLabel))
                when (source) {
                    ThirdPartyShareSource.WakeUp -> importWakeUp(code)
                    ThirdPartyShareSource.StarLink -> importStarLink(code)
                }
            }
        }

    // ── 2a. 星链（原生 HTTP + JSON）──────────────────────────────────

    private suspend fun importStarLink(rawCode: String): ThirdPartySharePayload =
        withContext(ioDispatcher) {
            val shareCode = extractShareKey(ThirdPartyShareSource.StarLink, rawCode)
            val resp = http.get(STARLINK_BASE_URL + encodePath(shareCode))
            val text = resp.text
            if (!resp.isSuccessful) {
                throw IllegalStateException(L("分享码已失效或网络异常（HTTP %s）", "Share code is invalid or the network failed (HTTP %s)")(resp.code))
            }
            if (text.isBlank()) throw IllegalStateException(L("未获取到课程数据", "No course data received")())
            val envelope = runCatching { parseJsonObject(text) }.getOrNull()
                ?: throw IllegalStateException(L("星链课表响应格式无效", "Invalid StarLink response format")())
            val data = envelope.optJsonObject("data")
                ?: throw IllegalStateException(L("未获取到课程数据", "No course data received")())
            val rawCourses = data.optJsonArray("courses").orEmpty().mapCourses()
                .map { c ->
                    ThirdPartyCourse(
                        name = c.name.takeIf { it.isNotBlank() } ?: L("未命名课程", "Unnamed course")(),
                        teacher = c.teacher.takeIf { it.isNotBlank() && it != "无" } ?: L("未知教师", "Unknown teacher")(),
                        position = c.location
                            .removePrefix("@")
                            .trim()
                            .takeIf { it.isNotEmpty() } ?: L("未排地点", "No location")(),
                        day = c.weekday,
                        startSection = c.startSection,
                        endSection = c.endSection,
                        weeks = c.weeks,
                    )
                }
            if (rawCourses.isEmpty()) throw IllegalStateException(L("未获取到课程数据", "No course data received")())

            val timeSlots = data.optJsonObject("sectionMinutes")?.entries.orEmpty()
                .mapNotNull { (key, value) ->
                    val number = key.toIntOrNull() ?: return@mapNotNull null
                    val range = value as? JsonArray ?: return@mapNotNull null
                    if (range.size < 2) return@mapNotNull null
                    val start = range.intAt(0) ?: return@mapNotNull null
                    val end = range.intAt(1) ?: return@mapNotNull null
                    ThirdPartyTimeSlot(
                        number = number,
                        startTime = minutesToTime(start),
                        endTime = minutesToTime(end),
                    )
                }.sortedBy { it.number }

            ThirdPartySharePayload(
                source = ThirdPartyShareSource.StarLink,
                courses = mergeAndDistinctCourses(rawCourses),
                timeSlots = timeSlots,
                semesterStartDate = data.optString("startDate").take(10).takeIf { it.isNotBlank() },
                semesterTotalWeeks = data.intOrNull("totalWeeks"),
            )
        }

    private fun minutesToTime(minutes: Int): String {
        // ⚠ Kotlin/Native 没有 `String.format`（`"%02d".format` 直接 Unresolved），
        // 手写补零 —— 别改成 String.format，编译不过。
        return "${(minutes / 60).pad2()}:${(minutes % 60).pad2()}"
    }

    private fun Int.pad2(): String = if (this < 10) "0$this" else toString()

    // ── 2b. WakeUp（下载适配脚本 → 无头 WebView 执行）──────────────────

    private suspend fun importWakeUp(code: String): ThirdPartySharePayload {
        val scriptFile = ScriptRepository(ScriptRepository.getRepoUrl())
            .ensureScript(ExtractedShareTools.GLOBAL_TOOLS_RESOURCE, ExtractedShareTools.WAKEUP_JS)
            ?: throw IllegalStateException(L("WakeUp 适配脚本下载失败，请检查网络后重试", "Failed to download the WakeUp adapter script. Check your network and try again.")())
        val script = scriptFile.readText()
        if (script.isBlank()) throw IllegalStateException(L("WakeUp 适配脚本为空", "The WakeUp adapter script is empty.")())
        val runner = wakeUpRunner
            ?: throw IllegalStateException(L("当前平台不支持 WakeUp 导入", "WakeUp import is not supported on this platform")())
        return runner(code, script)
    }

    // ── 口令清洗（与 Android 的正则逐字一致）──────────────────────────

    /**
     * 从用户粘贴的一整段分享文案里抠出口令。
     *
     * 用户粘的通常不是裸口令，而是「你的分享口令为：XXXX」这种整段话（星链甚至带
     * 「输入：」前缀），所以两个来源各有一套兜底正则 —— 与 Android 逐字一致，别改。
     */
    fun extractShareKey(source: ThirdPartyShareSource, raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        return when (source) {
            ThirdPartyShareSource.WakeUp -> extractWakeUpKey(text)
            ThirdPartyShareSource.StarLink -> extractStarLinkCode(text)
        }
    }

    private fun extractWakeUpKey(text: String): String {
        val labelled = Regex(
            "分享口令(?:为)?\\s*[「“\"]?\\s*([A-Za-z0-9_-]{1,200})\\s*[」”\"]?",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)
        if (!labelled.isNullOrBlank()) return labelled
        val quoted = Regex("[「“\"]\\s*([A-Za-z0-9_-]{8,200})\\s*[」”\"]")
            .find(text)?.groupValues?.get(1)
        if (!quoted.isNullOrBlank()) return quoted
        val candidates = Regex("[A-Za-z0-9_-]{8,200}").findAll(text).map { it.value }.toList()
        return candidates.lastOrNull() ?: text
    }

    private fun extractStarLinkCode(text: String): String {
        val structured = Regex("输入：\\s*([^（\\s]+)").find(text)?.groupValues?.get(1)?.trim()
        if (!structured.isNullOrBlank()) return structured
        val fallback = Regex("([a-zA-Z0-9-]{5,20})").find(text)?.groupValues?.get(1)?.trim()
        return fallback ?: text
    }

    // ── URL 编码 ────────────────────────────────────────────────────

    /** 口令里可能带中文/符号，拼进 query 前必须转义（等价 Android 的 `URLEncoder.encode`） */
    private fun encodeQuery(value: String): String = percentEncode(value)

    /** 路径段转义（口令理论上不含 `/`，但仍按最严的来） */
    private fun encodePath(value: String): String = percentEncode(value)

    /**
     * RFC 3986 百分号编码。
     *
     * ⚠ 没用 `NSString.stringByAddingPercentEncodingWithAllowedCharacters`：Kotlin/Native
     * 里 String 不直接暴露 NSString 的方法（要 `as NSString` 桥一层，多一处易错点），
     * 这里十几个字符的活手写更稳；只保留 unreserved 集，其余按 UTF-8 字节逐个转义。
     */
    private fun percentEncode(value: String): String {
        val hex = "0123456789ABCDEF"
        val out = StringBuilder(value.length)
        for (byte in value.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            val ch = code.toChar()
            val keep = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
                ch == '-' || ch == '.' || ch == '_' || ch == '~'
            if (keep) {
                out.append(ch)
            } else {
                out.append('%').append(hex[code shr 4]).append(hex[code and 0xF])
            }
        }
        return out.toString()
    }
}

// ── 星链课程的中间结构（字段名与服务端一致）────

private data class StarLinkCourseJson(
    val name: String,
    val teacher: String,
    val location: String,
    val weekday: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>,
)

private fun JsonObject.asCourseJson(): StarLinkCourseJson {
    val start = intOrNull("startSection")
    return StarLinkCourseJson(
        name = optString("name"),
        teacher = optString("teacher"),
        location = optString("location"),
        weekday = intOrNull("weekday") ?: 1,
        startSection = start ?: 1,
        endSection = intOrNull("endSection") ?: start ?: 1,
        weeks = optJsonArray("weeks").orEmpty().mapInts(),
    )
}

/** [JsonObject] 的整数读取：先取文本再转（服务端有时把数字写成字符串）。 */
private fun JsonObject.intOrNull(key: String): Int? =
    optString(key).takeIf { it.isNotBlank() }?.toIntOrNull()

/** `JsonArray?` → `JsonArray`，省得每个调用点写 `?: emptyList()`。 */
private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

/** 数组元素按文本读（数字 primitive 的 `content` 就是数字串）再转 Int，取不到的跳过。 */
private fun JsonArray.mapInts(): List<Int> =
    (0 until size).mapNotNull { optString(it, "").toIntOrNull() }

private fun JsonArray.intAt(index: Int): Int? = optString(index, "").toIntOrNull()

private fun JsonArray.mapCourses(): List<StarLinkCourseJson> =
    (0 until size).mapNotNull { optJsonObject(it)?.asCourseJson() }
