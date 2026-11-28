package com.haooz.chedule.ui.utils

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.school.ScriptRepository
import com.haooz.chedule.i18n.AndroidMiscStr
import com.haooz.chedule.i18n.BackupStr
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.i18n.L
import com.haooz.chedule.ui.web.JS_PROMISE_BRIDGE
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

// ════════════════════════════════════════════════════════════════════════
//  课表导入 —— 单文件全包
//
//  两条导入入口，都归于此：
//  1. 分享口令导入   `parseShare*` 全套：把分享 JSON 解析成课表结构，
//                   再写回 [CourseRepository]
//  2. 第三方 App 导入 [ThirdPartyShareImporter]等：WakeUp / 星链等
//                   App 的课表，通过无头 WebView 执行其脚本抓取数据
//
//  与 [ScheduleExport.kt] 成对：那边负责**产出**分享 JSON，这边负责**消费**。
//  改分享格式字段名时**两个文件都要改**。
//
//  教务导入（正方/ajax 课表抓取）**不属本文件**，它是独立的数据源
// （`EducationalImportActivity` + `data/school/`），实现方式完全不同。
// ════════════════════════════════════════════════════════════════════════

// ── 1. 分享数据解析（分享 JSON → 课表结构）────────────────────
//
// parseShare* 全家 2026-10-11 已迁 :ui-shared（同包名，本文件直接可见，无需 import）
// —— 见 ui/utils/ShareScheduleCodec.kt。它们是纯 `Map<String, Any>` 校验，
//   留在本文件会让「课表导入」页反向依赖 WebView/Gson，进不了 commonMain。

/** 将分享口令写入系统剪贴板 */
internal fun copyShareCodeToClipboard(context: Context, code: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(AndroidMiscStr.clipboardShareLabel(), code))
    } catch (_: Exception) {
        // 剪贴板失败不影响分享流程
    }
}

// ── 2. 第三方 App 课表导入（WakeUp / 星链）─────────────────────

/** WakeUp / 星链课表分享口令导入：原生请求 + 无界面脚本执行 */

private const val TAG = "ThirdPartyShareImport"

// 数据契约（ThirdPartyShareSource / Payload / Course / TimeSlot）2026-10-11 已迁
// :ui-shared（同包名，本文件直接可见，无需 import）—— 见 ui/utils/ThirdPartyShare.kt。
// 抓取器本体要无头 WebView + OkHttp + Gson，只能留在 Android 侧，由宿主注入给共享页。

// ExtractedShareTools 已抽成独立文件并迁入 :ui-shared（同包名，这里直接可见，无需 import）。
// 它只是一组常量，原先跟 WebView/Gson/Handler 挤在同一个文件里，
// 会让平台中立的学校/适配器选择页反向依赖整个 Android 导入模块。

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
        NexioLog.d(TAG, "WakeUp: $message")
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
                if (courses.isEmpty()) onError(ImportStr.noCoursesParsed()) else onComplete()
            }
        } catch (e: Exception) {
            mainHandler.post {
                onError(AndroidMiscStr.parseFailedN(e.message))
                rejectPromise(promiseId, e.message ?: AndroidMiscStr.parseFailed())
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
            onError(contentText?.takeIf { it.isNotBlank() } ?: AndroidMiscStr.importFailed())
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
        mainHandler.post { onError(message?.takeIf { it.isNotBlank() } ?: AndroidMiscStr.importFailed()) }
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
        try {
            val code = extractShareKey(source, rawInput)
            if (code.isBlank()) throw IllegalArgumentException(BackupStr.enterInputN(source.inputLabel))
            val payload = when (source) {
                ThirdPartyShareSource.WakeUp -> importWakeUp(context, code)
                ThirdPartyShareSource.StarLink -> importStarLink(code)
            }
            Result.success(payload)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
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
                throw IllegalStateException(AndroidMiscStr.shareCodeInvalidHttpN(response.code))
            }
            response.body?.string().orEmpty()
        }
        if (bodyText.isBlank()) throw IllegalStateException(AndroidMiscStr.noCourseData())
        val envelope = try {
            gson.fromJson(bodyText, StarLinkEnvelope::class.java)
        } catch (_: Exception) {
            throw IllegalStateException(AndroidMiscStr.starLinkInvalidResponse())
        }
        val data = envelope.data ?: throw IllegalStateException(AndroidMiscStr.noCourseData())
        val rawCourses = data.courses.orEmpty().map { c ->
            ThirdPartyCourse(
                name = c.name?.takeIf { it.isNotBlank() } ?: ImportStr.unnamedCourse(),
                teacher = c.teacher?.takeIf { it.isNotBlank() && it != "无" } ?: AndroidMiscStr.unknownTeacher(),
                position = c.location
                    ?.removePrefix("@")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: AndroidMiscStr.noLocation(),
                day = c.weekday ?: 1,
                startSection = c.startSection ?: 1,
                endSection = c.endSection ?: (c.startSection ?: 1),
                weeks = c.weeks.orEmpty(),
            )
        }
        if (rawCourses.isEmpty()) throw IllegalStateException(AndroidMiscStr.noCourseData())
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
        val scriptFile = ScriptRepository(ScriptRepository.getRepoUrl())
            .ensureScript(ExtractedShareTools.GLOBAL_TOOLS_RESOURCE, ExtractedShareTools.WAKEUP_JS)
            ?: throw IllegalStateException(AndroidMiscStr.wakeUpScriptDownloadFailed())
        val script = scriptFile.readText()
        if (script.isBlank()) throw IllegalStateException(AndroidMiscStr.wakeUpScriptEmpty())

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
                    payloadDeferred.completeExceptionally(IllegalStateException(ImportStr.noCoursesParsed()))
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
        return withTimeoutOrNull(45_000L) {
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
                                                    window.AndroidBridge.onImportFailed(content || title || '${AndroidMiscStr.importFailed()}');
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
                                    NexioLog.e(TAG, AndroidMiscStr.wakeUpScriptStartFailed(), e)
                                    failOnce(e.message ?: AndroidMiscStr.wakeUpScriptStartFailed())
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
                NexioLog.e(TAG, AndroidMiscStr.wakeUpWebViewCreateFailed(), e)
                failOnce(e.message ?: AndroidMiscStr.wakeUpWebViewCreateFailed())
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
        } ?: throw IllegalStateException(L("导入超时，请重试", "Import timed out. Please try again.")())
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

}

// mergeAndDistinctCourses 2026-10-13 已上提到 :ui-shared（同包名 ThirdPartyShare.kt，
// 直接可见）—— iOS 的星链导入要用同一套合并规则，两边各写一份必然漂移。
