/** WakeUp / 星链课表分享口令导入：原生请求 + 无界面脚本执行 */
package com.haooz.chedule.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.haooz.chedule.data.school.ScriptRepository
import com.haooz.chedule.ui.web.JS_PROMISE_BRIDGE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "ThirdPartyShareImport"

/** 教务通用工具中已提取到课表导入页的适配器 ID */
object ExtractedShareTools {
    const val WAKEUP = "WakeUp"
    const val STARLINK = "StarLink"
    val ADAPTER_IDS = setOf(WAKEUP, STARLINK)

    const val GLOBAL_TOOLS_SCHOOL_ID = "GLOBAL_TOOLS"
    const val GLOBAL_TOOLS_RESOURCE = "GLOBAL_TOOLS"
    const val WAKEUP_JS = "wake_up.js"
}

enum class ThirdPartyShareSource(val displayName: String, val inputLabel: String) {
    WakeUp("WakeUp课程表口令导入", "分享口令"),
    StarLink("星链课表分享码导入", "分享码"),
}

/** 第三方口令解析结果：课程 + 可选时间段/学期配置 */
data class ThirdPartySharePayload(
    val source: ThirdPartyShareSource,
    val courses: List<ThirdPartyCourse>,
    val timeSlots: List<ThirdPartyTimeSlot> = emptyList(),
    val semesterStartDate: String? = null,
    val semesterTotalWeeks: Int? = null,
) {
    val courseCount: Int get() = courses.size
}

data class ThirdPartyCourse(
    val name: String,
    val teacher: String = "",
    val position: String = "",
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int> = emptyList(),
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
) {
    fun toCourse(scheduleId: String, color: Long): Course {
        val sortedWeeks = weeks.sorted().ifEmpty { listOf(1) }
        return Course(
            id = UUID.randomUUID().toString(),
            name = name,
            classroom = position,
            teacher = teacher,
            dayOfWeek = day.coerceIn(1, 7),
            startSection = startSection.coerceAtLeast(1),
            endSection = endSection.coerceAtLeast(startSection),
            startWeek = sortedWeeks.first(),
            endWeek = sortedWeeks.last(),
            weekType = Course.WEEK_TYPE_ALL,
            selectedWeeks = sortedWeeks,
            colorRes = color,
            scheduleId = scheduleId,
            isCustomTime = isCustomTime,
            customStartTime = customStartTime,
            customEndTime = customEndTime,
        )
    }
}

data class ThirdPartyTimeSlot(
    val number: Int,
    val startTime: String,
    val endTime: String,
)

/**
 * WakeUp 无界面导入桥。
 * 必须是公开类：Android 对 @JavascriptInterface 的反射要求方法/类可公开访问。
 */
class WakeUpHeadlessBridge(
    private val shareKey: String,
    private val onCourses: (List<ThirdPartyCourse>) -> Unit,
    private val onConfig: (String?, Int?) -> Unit,
    private val onTimeSlots: (List<ThirdPartyTimeSlot>) -> Unit,
    private val onComplete: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    @Volatile
    private var webView: WebView? = null

    fun attach(target: WebView) {
        webView = target
    }

    @JavascriptInterface
    fun showToast(message: String?) {
        Log.d(TAG, "WakeUp: $message")
        val text = message.orEmpty()
        if (text.contains("失败") || text.contains("无效") || text.contains("无法") ||
            text.contains("不能为空") || text.contains("未检测到")
        ) {
            mainHandler.post { onError(text) }
        }
    }

    @JavascriptInterface
    fun notifyTaskCompletion() {
        mainHandler.post { onComplete() }
    }

    @JavascriptInterface
    fun saveImportedCourses(coursesJsonString: String?, promiseId: String?) {
        try {
            val list = gson.fromJson<List<ImportCourseJson>>(
                coursesJsonString,
                object : TypeToken<List<ImportCourseJson>>() {}.type
            ).orEmpty()
            val courses = list.map { json ->
                val weeks = json.weeks.orEmpty().sorted().ifEmpty { listOf(1) }
                ThirdPartyCourse(
                    name = json.name ?: "",
                    teacher = json.teacher ?: "",
                    position = json.position ?: "",
                    day = json.day,
                    startSection = json.startSection ?: 1,
                    endSection = json.endSection ?: json.startSection ?: 1,
                    weeks = weeks,
                    isCustomTime = json.isCustomTime == true,
                    customStartTime = json.customStartTime,
                    customEndTime = json.customEndTime,
                )
            }
            mainHandler.post {
                onCourses(courses)
                resolvePromise(promiseId, "true")
                if (courses.isEmpty()) onError("未解析到课程数据") else onComplete()
            }
        } catch (e: Exception) {
            mainHandler.post {
                onError("课程数据解析失败: ${e.message}")
                rejectPromise(promiseId, e.message ?: "课程数据解析失败")
            }
        }
    }

    @JavascriptInterface
    fun saveCourseConfig(configJsonString: String?, promiseId: String?) {
        try {
            val config = gson.fromJson(configJsonString, ConfigJson::class.java)
            mainHandler.post {
                onConfig(config?.semesterStartDate, config?.semesterTotalWeeks)
                resolvePromise(promiseId, "true")
            }
        } catch (_: Exception) {
            resolvePromise(promiseId, "true")
        }
    }

    @JavascriptInterface
    fun savePresetTimeSlots(timeSlotsJsonString: String?, promiseId: String?) {
        try {
            val slots = gson.fromJson<List<TimeSlotJson>>(
                timeSlotsJsonString,
                object : TypeToken<List<TimeSlotJson>>() {}.type
            ).orEmpty()
            val mapped = slots.mapNotNull { slot ->
                val number = slot.number ?: return@mapNotNull null
                val start = slot.startTime ?: return@mapNotNull null
                val end = slot.endTime ?: return@mapNotNull null
                ThirdPartyTimeSlot(number, start, end)
            }
            mainHandler.post {
                onTimeSlots(mapped)
                resolvePromise(promiseId, "true")
            }
        } catch (_: Exception) {
            resolvePromise(promiseId, "true")
        }
    }

    @JavascriptInterface
    fun showAlert(titleText: String?, contentText: String?, confirmText: String?, promiseId: String?) {
        mainHandler.post {
            onError(contentText?.takeIf { it.isNotBlank() } ?: "导入失败")
            resolvePromise(promiseId, "true")
        }
    }

    @JavascriptInterface
    fun showPrompt(titleText: String?, tipText: String?, defaultText: String?, validatorJs: String?, promiseId: String?) {
        // 课表导入页已收集口令，直接回填
        resolvePromise(promiseId, "'${escapeJs(shareKey)}'")
    }

    @JavascriptInterface
    fun showSingleSelection(titleText: String?, itemsJsonString: String?, defaultSelectedIndex: Int, promiseId: String?) {
        resolvePromise(promiseId, (defaultSelectedIndex).toString())
    }

    @JavascriptInterface
    fun onImportFailed(message: String?) {
        mainHandler.post { onError(message?.takeIf { it.isNotBlank() } ?: "导入失败") }
    }

    private fun escapeJs(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    private fun resolvePromise(promiseId: String?, result: String) {
        val id = promiseId ?: return
        val safeId = escapeJs(id)
        mainHandler.post {
            webView?.evaluateJavascript(
                "window._resolveAndroidPromise && window._resolveAndroidPromise('$safeId', $result);",
                null
            )
        }
    }

    private fun rejectPromise(promiseId: String?, error: String) {
        val id = promiseId ?: return
        val safeId = escapeJs(id)
        val safeErr = escapeJs(error)
        mainHandler.post {
            webView?.evaluateJavascript(
                "window._rejectAndroidPromise && window._rejectAndroidPromise('$safeId', '$safeErr');",
                null
            )
        }
    }

    private data class ImportCourseJson(
        val name: String? = null,
        val teacher: String? = null,
        val position: String? = null,
        val day: Int = 1,
        val startSection: Int? = null,
        val endSection: Int? = null,
        val weeks: List<Int>? = null,
        val isCustomTime: Boolean? = null,
        val customStartTime: String? = null,
        val customEndTime: String? = null,
    )

    private data class ConfigJson(
        val semesterStartDate: String? = null,
        val semesterTotalWeeks: Int? = null,
    )

    private data class TimeSlotJson(
        val number: Int? = null,
        val startTime: String? = null,
        val endTime: String? = null,
    )
}

object ThirdPartyShareImporter {

    private val gson = Gson()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20L, TimeUnit.SECONDS)
            .readTimeout(20L, TimeUnit.SECONDS)
            .build()
    }

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

    suspend fun import(
        context: Context,
        source: ThirdPartyShareSource,
        rawInput: String,
    ): Result<ThirdPartySharePayload> = withContext(Dispatchers.IO) {
        runCatching {
            val code = extractShareKey(source, rawInput)
            if (code.isBlank()) throw IllegalArgumentException("请输入${source.inputLabel}")
            when (source) {
                ThirdPartyShareSource.WakeUp -> importWakeUp(context, code)
                ThirdPartyShareSource.StarLink -> importStarLink(code)
            }
        }
    }

    // region StarLink（原生 HTTP）

    private data class StarLinkEnvelope(
        @SerializedName("data") val data: StarLinkData? = null,
    )

    private data class StarLinkData(
        @SerializedName("courses") val courses: List<StarLinkCourse>? = null,
        @SerializedName("startDate") val startDate: String? = null,
        @SerializedName("totalWeeks") val totalWeeks: Int? = null,
        @SerializedName("sectionMinutes") val sectionMinutes: Map<String, List<Int>>? = null,
    )

    private data class StarLinkCourse(
        @SerializedName("name") val name: String? = null,
        @SerializedName("teacher") val teacher: String? = null,
        @SerializedName("location") val location: String? = null,
        @SerializedName("weekday") val weekday: Int? = null,
        @SerializedName("startSection") val startSection: Int? = null,
        @SerializedName("endSection") val endSection: Int? = null,
        @SerializedName("weeks") val weeks: List<Int>? = null,
    )

    private suspend fun importStarLink(rawCode: String): ThirdPartySharePayload {
        val shareCode = extractStarLinkCode(rawCode)
        val url = "https://api.starlinkkb.cn/share/curriculum/$shareCode"
        val request = Request.Builder().url(url).get().build()
        val bodyText = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("分享码已失效或网络异常（HTTP ${response.code}）")
            }
            response.body?.string().orEmpty()
        }
        if (bodyText.isBlank()) throw IllegalStateException("未获取到课程数据")
        val envelope = try {
            gson.fromJson(bodyText, StarLinkEnvelope::class.java)
        } catch (_: Exception) {
            throw IllegalStateException("星链课表响应格式无效")
        }
        val data = envelope.data ?: throw IllegalStateException("未获取到课程数据")
        val rawCourses = data.courses.orEmpty().map { c ->
            ThirdPartyCourse(
                name = c.name?.takeIf { it.isNotBlank() } ?: "未命名课程",
                teacher = c.teacher?.takeIf { it.isNotBlank() && it != "无" } ?: "未知教师",
                position = c.location
                    ?.removePrefix("@")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: "未排地点",
                day = c.weekday ?: 1,
                startSection = c.startSection ?: 1,
                endSection = c.endSection ?: (c.startSection ?: 1),
                weeks = c.weeks.orEmpty(),
            )
        }
        if (rawCourses.isEmpty()) throw IllegalStateException("未获取到课程数据")
        val courses = mergeAndDistinctCourses(rawCourses)
        val timeSlots = data.sectionMinutes.orEmpty().mapNotNull { (key, range) ->
            val number = key.toIntOrNull() ?: return@mapNotNull null
            if (range.size < 2) return@mapNotNull null
            ThirdPartyTimeSlot(
                number = number,
                startTime = minutesToTime(range[0]),
                endTime = minutesToTime(range[1]),
            )
        }.sortedBy { it.number }
        return ThirdPartySharePayload(
            source = ThirdPartyShareSource.StarLink,
            courses = courses,
            timeSlots = timeSlots,
            semesterStartDate = data.startDate?.take(10),
            semesterTotalWeeks = data.totalWeeks,
        )
    }

    private fun minutesToTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }

    // endregion

    // region WakeUp（无界面 WebView 跑适配脚本）

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun importWakeUp(context: Context, shareKey: String): ThirdPartySharePayload {
        val scriptFile = ScriptRepository(context, ScriptRepository.getRepoUrl(context))
            .ensureScript(ExtractedShareTools.GLOBAL_TOOLS_RESOURCE, ExtractedShareTools.WAKEUP_JS)
            ?: throw IllegalStateException("WakeUp 适配脚本下载失败，请检查网络后重试")
        val script = scriptFile.readText()
        if (script.isBlank()) throw IllegalStateException("WakeUp 适配脚本为空")

        val payloadDeferred = CompletableDeferred<ThirdPartySharePayload>()
        val coursesRef = AtomicReference<List<ThirdPartyCourse>?>(null)
        val startDateRef = AtomicReference<String?>(null)
        val totalWeeksRef = AtomicReference<Int?>(null)
        val timeSlotsRef = AtomicReference<List<ThirdPartyTimeSlot>>(emptyList())
        val finished = AtomicBoolean(false)

        val completeOnce = {
            if (finished.compareAndSet(false, true) && !payloadDeferred.isCompleted) {
                val courses = coursesRef.get()
                if (courses.isNullOrEmpty()) {
                    payloadDeferred.completeExceptionally(IllegalStateException("未解析到课程数据"))
                } else {
                    payloadDeferred.complete(
                        ThirdPartySharePayload(
                            source = ThirdPartyShareSource.WakeUp,
                            courses = courses,
                            timeSlots = timeSlotsRef.get().orEmpty(),
                            semesterStartDate = startDateRef.get(),
                            semesterTotalWeeks = totalWeeksRef.get(),
                        )
                    )
                }
            }
        }
        val failOnce = { message: String ->
            if (finished.compareAndSet(false, true) && !payloadDeferred.isCompleted) {
                payloadDeferred.completeExceptionally(IllegalStateException(message))
            }
        }

        val mainHandler = Handler(Looper.getMainLooper())
        return withTimeout(45_000L) {
            var webView: WebView? = null
            val bridge = WakeUpHeadlessBridge(
                shareKey = shareKey,
                onCourses = { coursesRef.set(it) },
                onConfig = { start, weeks ->
                    startDateRef.set(start)
                    totalWeeksRef.set(weeks)
                },
                onTimeSlots = { timeSlotsRef.set(it) },
                onComplete = { completeOnce() },
                onError = { failOnce(it) },
            )

            try {
                webView = runOnMain {
                    val wv = WebView(context.applicationContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.userAgentString = WebSettings.getDefaultUserAgent(context)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                val target = view ?: return
                                bridge.attach(target)
                                try {
                                    target.evaluateJavascript(JS_PROMISE_BRIDGE) {
                                        val escapedKey = shareKey
                                            .replace("\\", "\\\\")
                                            .replace("'", "\\'")
                                            .replace("\n", "\\n")
                                            .replace("\r", "")
                                        val bootstrap = """
                                            (function() {
                                              window.shiguangBridge = window.AndroidBridge;
                                              window.shiguangBridgePromise = window.AndroidBridgePromise;
                                              var __key = '$escapedKey';
                                              if (window.AndroidBridgePromise) {
                                                window.AndroidBridgePromise.showPrompt = function() {
                                                  return Promise.resolve(__key);
                                                };
                                                window.AndroidBridgePromise.showAlert = function(title, content) {
                                                  if (window.AndroidBridge && window.AndroidBridge.onImportFailed) {
                                                    window.AndroidBridge.onImportFailed(content || title || '导入失败');
                                                  }
                                                  return Promise.resolve(true);
                                                };
                                              }
                                              if (window.AndroidBridge) {
                                                var origToast = window.AndroidBridge.showToast;
                                                window.AndroidBridge.showToast = function(msg) {
                                                  try { if (origToast) origToast.call(window.AndroidBridge, msg); } catch (e) {}
                                                  if (typeof msg === 'string' && /失败|无效|无法|不能为空|未检测到/.test(msg)) {
                                                    if (window.AndroidBridge.onImportFailed) window.AndroidBridge.onImportFailed(msg);
                                                  }
                                                };
                                              }
                                            })();
                                        """.trimIndent()
                                        target.evaluateJavascript(bootstrap) {
                                            target.evaluateJavascript(script, null)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "WakeUp 脚本启动失败", e)
                                    failOnce(e.message ?: "WakeUp 脚本启动失败")
                                }
                            }
                        }
                    }
                    bridge.attach(wv)
                    wv.addJavascriptInterface(bridge, "AndroidBridge")
                    wv.addJavascriptInterface(bridge, "WebPostService")
                    wv.loadDataWithBaseURL(
                        "https://api.wakeup.fun/",
                        "<html><body></body></html>",
                        "text/html",
                        "utf-8",
                        null
                    )
                    wv
                }
            } catch (e: Exception) {
                Log.e(TAG, "WakeUp WebView 创建失败", e)
                failOnce(e.message ?: "WakeUp WebView 创建失败")
            }

            try {
                payloadDeferred.await()
            } finally {
                mainHandler.post {
                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private suspend fun <T> runOnMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val handler = Handler(Looper.getMainLooper())
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            handler.post {
                try {
                    cont.resume(block())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }
        }
    }

    // endregion

    /** 与适配脚本一致：合并连续节次与同节次周次 */
    fun mergeAndDistinctCourses(courses: List<ThirdPartyCourse>): List<ThirdPartyCourse> {
        if (courses.size <= 1) return courses
        val list = courses.map { c ->
            c.copy(
                name = c.name.trim(),
                teacher = c.teacher.trim(),
                position = c.position.trim(),
                weeks = c.weeks.sorted(),
            )
        }

        val sorted1 = list.sortedWith(
            compareBy(
                { it.name },
                { it.teacher },
                { it.position },
                { it.day },
                { it.weeks.joinToString(",") },
                { it.startSection },
            )
        )
        val step1 = mutableListOf<ThirdPartyCourse>()
        var current = sorted1.first()
        for (i in 1 until sorted1.size) {
            val next = sorted1[i]
            val sameCourseAndWeeks =
                current.name == next.name &&
                    current.teacher == next.teacher &&
                    current.position == next.position &&
                    current.day == next.day &&
                    current.weeks == next.weeks
            val continuous = current.endSection + 1 == next.startSection
            val duplicate = current.startSection == next.startSection &&
                current.endSection == next.endSection
            when {
                sameCourseAndWeeks && continuous ->
                    current = current.copy(endSection = next.endSection)
                sameCourseAndWeeks && duplicate -> Unit
                else -> {
                    step1.add(current)
                    current = next
                }
            }
        }
        step1.add(current)

        val sorted2 = step1.sortedWith(
            compareBy(
                { it.name },
                { it.teacher },
                { it.position },
                { it.day },
                { it.startSection },
                { it.endSection },
            )
        )
        val step2 = mutableListOf<ThirdPartyCourse>()
        var cur = sorted2.first()
        for (i in 1 until sorted2.size) {
            val nxt = sorted2[i]
            val same =
                cur.name == nxt.name &&
                    cur.teacher == nxt.teacher &&
                    cur.position == nxt.position &&
                    cur.day == nxt.day &&
                    cur.startSection == nxt.startSection &&
                    cur.endSection == nxt.endSection
            if (same) {
                cur = cur.copy(weeks = (cur.weeks + nxt.weeks).distinct().sorted())
            } else {
                step2.add(cur)
                cur = nxt
            }
        }
        step2.add(cur)
        return step2
    }
}
