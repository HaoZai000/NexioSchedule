package com.haooz.chedule.ui.utils

import com.haooz.chedule.data.platform.ApiEndpoints
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
import com.haooz.chedule.data.util.suspendRunCatching
import com.haooz.chedule.i18n.L
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** 本站口令与星链分享码通过 HTTP 导入；WakeUp 适配脚本由 [wakeUpRunner] 执行。 */
object ShareCodeImport {

    /** 宿主提供的无界面脚本执行器；为 null 时不支持 WakeUp 导入。 */
    var wakeUpRunner: (suspend (shareKey: String, script: String) -> ThirdPartySharePayload)? = null

    private const val SHARE_BASE_URL = ApiEndpoints.SHARE

    private const val STARLINK_BASE_URL = "https://api.starlinkkb.cn/share/curriculum/"

    private val http: HttpService by lazy {
        createHttpService(HttpTimeouts(connectSeconds = 12, readSeconds = 20, callSeconds = 30))
    }

    // ── 1. 本站分享口令取回 ──────────────────────────────────────────

    /** HTTP 错误优先返回服务端消息；404 且无消息时提示口令不存在或已过期。 */
    suspend fun fetchShareCode(rawCode: String): Result<ShareCodeFetchResult> =
        withContext(ioDispatcher) {
            suspendRunCatching {
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

    /** 整次导入共用一个超时，包括下载和执行 WakeUp 适配脚本。 */
    suspend fun importThirdParty(
        source: ThirdPartyShareSource,
        rawInput: String,
    ): Result<ThirdPartySharePayload> =
        try {
            suspendRunCatching {
                withTimeout(45_000L) {
                    val code = extractShareKey(source, rawInput)
                    if (code.isBlank()) throw IllegalArgumentException(L("请输入%s", "Please enter %s")(source.inputLabel))
                    when (source) {
                        ThirdPartyShareSource.WakeUp -> importWakeUp(code)
                        ThirdPartyShareSource.StarLink -> importStarLink(code)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            // 只将导入本身的超时报告给用户，调用方的超时仍是取消。
            currentCoroutineContext().ensureActive()
            Result.failure(IllegalStateException(L("导入超时，请重试", "Import timed out. Please try again.")(), e))
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

    /** 从粘贴的分享文案中按来源提取口令，也接受裸口令。 */
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

    private fun encodeQuery(value: String): String = percentEncode(value)

    private fun encodePath(value: String): String = percentEncode(value)

    /** 保留 RFC 3986 的非保留字符，其余按 UTF-8 字节作百分号编码。 */
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

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

/** 数组元素按文本读（数字 primitive 的 `content` 就是数字串）再转 Int，取不到的跳过。 */
private fun JsonArray.mapInts(): List<Int> =
    (0 until size).mapNotNull { optString(it, "").toIntOrNull() }

private fun JsonArray.intAt(index: Int): Int? = optString(index, "").toIntOrNull()

private fun JsonArray.mapCourses(): List<StarLinkCourseJson> =
    (0 until size).mapNotNull { optJsonObject(it)?.asCourseJson() }
