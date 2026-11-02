@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

import com.haooz.chedule.data.util.optBoolean
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optJsonObject
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.util.parseJsonArray
import com.haooz.chedule.data.util.parseJsonObject
import com.haooz.chedule.i18n.L
import com.haooz.chedule.ui.web.JS_PROMISE_BRIDGE
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * iOS 的 WakeUp 课表导入：**无头 WKWebView 跑云端适配脚本**，对应 Android
 * `ThirdPartyShareImporter.importWakeUp`（无头 `WebView` + `@JavascriptInterface`）。
 * 脚本本体由 [ShareCodeImport] 用共享 `ScriptRepository` 下载好再传入 —— 下载是跨平台能力，
 * 不与 WKWebView 绑在一起。
 *
 * ## 桥接差异（本文件唯一难的地方）
 *
 * Android 的 `addJavascriptInterface` 可让 JS 直接 `AndroidBridge.saveImportedCourses(a, b)`；
 * WKWebView **没有这个能力**，只有 `window.webkit.messageHandlers.X.postMessage(...)` 单向通道。
 * 故先注入 JS shim（[IOS_BRIDGE_JS]）：造一个**方法签名逐字相同**的 `window.AndroidBridge`，
 * 内部把 `(方法名, 参数数组)` postMessage 出来 —— 云端脚本与共享 [JS_PROMISE_BRIDGE] 零改动。
 *
 * Promise 兑现方向相反（Native → JS）：走 `evaluateJavaScript` 调
 * `window._resolveAndroidPromise(id, result)`，与 Android 完全同构。
 *
 * ## 三个必须记住的坑
 *
 * 1. **WKWebView 只能在主线程创建和调用**：协程跑在 `ioDispatcher`，所有 WebKit 操作切主队列，
 *    否则直接崩（不是静默失败）。
 * 2. **参数里的数字是 NSNumber**：WKScriptMessage 的 body 走 Foundation 桥，JS number 到 Kotlin
 *    是 `NSNumber`，**不能 `as? Number`**，一律 `toString()` 再转。
 * 3. **handler / delegate 要自己强引用**（[Controller] 的字段），否则页面加载完成前就可能被回收，
 *    回调永远不来（表现为「点了没反应」）。
 *
 * 另外：WebView 要**加到 key window 上**（1×1、屏幕外、`alpha` 0.01）—— 不挂视图层级时
 * WKWebView 可能延后执行 JS；`hidden = true` 会让系统跳过渲染，别用。
 */
internal object IosWakeUpImport {

    private const val TAG = "IosWakeUpImport"

    /** scriptMessageHandler 的注册名；JS shim 里必须同名 */
    private const val HANDLER_NAME = "NexioWakeUpBridge"

    /** 与 Android `loadDataWithBaseURL` 的 baseURL 一致：脚本要按同源去 fetch */
    private const val BASE_URL = "https://api.wakeup.fun/"

    /** 空白页载入超时；外层还有 45s 总超时，这里只是给出可读的失败原因 */
    private const val PAGE_LOAD_TIMEOUT_MS = 20_000L

    suspend fun run(shareKey: String, script: String): ThirdPartySharePayload {
        val controller = Controller(shareKey)
        try {
            controller.attach()
            controller.awaitPageLoad()
            controller.eval(IOS_BRIDGE_JS)
            controller.eval(JS_PROMISE_BRIDGE)
            // 与 Android 的 bootstrap 同构：口令回填 showPrompt，showAlert 一律当失败（脚本只用它报错）
            controller.eval(bootstrapJs(shareKey))
            controller.eval(script)
            return controller.awaitPayload()
        } finally {
            controller.dispose()
        }
    }

    // ── 注入给 WebView 的 JS shim ────────────────────────────────────

    /**
     * 伪造 `window.AndroidBridge`：方法名与参数顺序照抄 Android 的 `@JavascriptInterface`，
     * 内部统一走 `postMessage`。`WebPostService` 也补个空实现（`WebPostInterceptJS` 会调它）。
     */
    private val IOS_BRIDGE_JS = """
    (function() {
        function post(name, args) {
            try {
                window.webkit.messageHandlers.$HANDLER_NAME.postMessage({ m: name, a: args || [] });
            } catch (e) {}
        }
        window.AndroidBridge = {
            showToast: function(message) { post('showToast', [message]); },
            notifyTaskCompletion: function() { post('notifyTaskCompletion', []); },
            saveImportedCourses: function(json, promiseId) { post('saveImportedCourses', [json, promiseId]); },
            saveCourseConfig: function(json, promiseId) { post('saveCourseConfig', [json, promiseId]); },
            savePresetTimeSlots: function(json, promiseId) { post('savePresetTimeSlots', [json, promiseId]); },
            saveComboSchedule: function(json, promiseId) { post('saveComboSchedule', [json, promiseId]); },
            showAlert: function(title, content, confirmText, promiseId) { post('showAlert', [title, content, confirmText, promiseId]); },
            showPrompt: function(title, tip, defaultText, validatorJs, promiseId) { post('showPrompt', [title, tip, defaultText, validatorJs, promiseId]); },
            showSingleSelection: function(title, itemsJson, defaultIndex, promiseId) { post('showSingleSelection', [title, itemsJson, defaultIndex, promiseId]); },
            onImportFailed: function(message) { post('onImportFailed', [message]); },
            isPostForwardEnabled: false
        };
        window.WebPostService = { register: function(id, body, contentType) {} };
        return true;
    })();
    """.trimIndent()

    /** 回填口令 + 把 showAlert 当失败（与 Android 的 bootstrap 逐字同构） */
    private fun bootstrapJs(shareKey: String): String {
        val escapedKey = escapeJs(shareKey)
        return """
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
    }

    private fun escapeJs(value: String): String = value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "")

    // ── 主队列切换 ──────────────────────────────────────────────────

    /** 同步块切到主队列。⚠ 参数不是 suspend lambda —— 队列回调里起不了协程。 */
    private suspend fun <T> onMain(block: () -> T): T = suspendCancellableCoroutine { cont ->
        NSOperationQueue.mainQueue.addOperationWithBlock {
            try {
                cont.resume(block())
            } catch (e: Throwable) {
                cont.resumeWithException(e)
            }
        }
    }

    /** 丢到主队列不等结果（清理路径用，协程可能已经被取消）。 */
    private fun postMain(block: () -> Unit) {
        NSOperationQueue.mainQueue.addOperationWithBlock(block)
    }

    // ── 一次导入的全部状态 ──────────────────────────────────────────

    private class Controller(private val shareKey: String) {

        private val deferred = CompletableDeferred<ThirdPartySharePayload>()
        private val pageLoaded = CompletableDeferred<Unit>()

        private var courses: List<ThirdPartyCourse>? = null
        private var startDate: String? = null
        private var totalWeeks: Int? = null
        private var timeSlots: List<ThirdPartyTimeSlot> = emptyList()
        private var settled = false

        private var webView: WKWebView? = null
        private var handler: BridgeHandler? = null
        private var navDelegate: NavDelegate? = null

        /** 建 WebView → 挂到 window → 载入空白页（baseURL 决定脚本的同源） */
        suspend fun attach() = onMain {
            val contentController = WKUserContentController()
            val bridge = BridgeHandler(::handle)
            contentController.addScriptMessageHandler(bridge, name = HANDLER_NAME)
            handler = bridge

            val config = WKWebViewConfiguration()
            config.userContentController = contentController

            val wv = WKWebView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0), configuration = config)
            // 挪到屏幕外 + 几乎全透明：保证 WKWebView 正常渲染/执行 JS，又看不见。
            // ⚠ 别用 hidden = true —— 系统会跳过渲染，JS 执行时机不可控。
            wv.setFrame(CGRectMake(-10000.0, -10000.0, 1.0, 1.0))
            wv.alpha = 0.01
            val nav = NavDelegate(
                onFinish = { if (!pageLoaded.isCompleted) pageLoaded.complete(Unit) },
                onFail = ::fail,
            )
            wv.navigationDelegate = nav
            navDelegate = nav
            IosUiHost.keyWindow()?.addSubview(wv)
            webView = wv
            wv.loadHTMLString("<html><body></body></html>", baseURL = NSURL(string = BASE_URL))
        }

        /**
         * 等空白页载入完成（注入脚本的前置条件）。
         *
         * ⚠ 自带 20s 超时并把 `TimeoutCancellationException` 换成可读文案：网络不通时
         * `didFinishNavigation` 不会来，不兜底就干等到外层 45s 超时、抛没人看得懂的
         * "Timed out waiting for ..."（文案风格与 ShareCodeImport 一致）。
         */
        suspend fun awaitPageLoad() {
            try {
                withTimeout(PAGE_LOAD_TIMEOUT_MS) { pageLoaded.await() }
            } catch (e: TimeoutCancellationException) {
                throw IllegalStateException(L("页面加载超时，请检查网络后重试", "Page load timed out. Check your network and try again.")())
            }
        }

        /** 注入一段 JS，等它执行完再返回（顺序注入靠这个串行化） */
        suspend fun eval(js: String) {
            val wv = webView ?: return
            suspendCancellableCoroutine<Unit> { cont ->
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    wv.evaluateJavaScript(js) { _, error ->
                        if (error != null) {
                            NexioLog.w(TAG, "JS 注入异常：${error.localizedDescription}")
                        }
                        if (!cont.isCompleted) cont.resume(Unit)
                    }
                }
            }
        }

        suspend fun awaitPayload(): ThirdPartySharePayload = deferred.await()

        fun dispose() {
            val wv = webView
            webView = null
            handler = null
            navDelegate = null
            if (wv == null) return
            postMain {
                wv.stopLoading()
                wv.navigationDelegate = null
                wv.configuration.userContentController
                    .removeScriptMessageHandlerForName(HANDLER_NAME)
                wv.removeFromSuperview()
            }
        }

        // ── 脚本 → Native ──

        private fun handle(name: String, args: List<Any?>) {
            NexioLog.d(TAG, "bridge: $name")
            when (name) {
                "showToast" -> {
                    val text = args.getOrNull(0) as? String ?: return
                    if (text.contains("失败") || text.contains("无效") || text.contains("无法") ||
                        text.contains("不能为空") || text.contains("未检测到")
                    ) {
                        fail(text)
                    }
                }
                "notifyTaskCompletion" -> complete()
                "onImportFailed" -> fail(args.getOrNull(0) as? String ?: L("导入失败", "Import failed")())
                "saveImportedCourses" -> {
                    val promiseId = args.getOrNull(1) as? String
                    val parsed = runCatching { parseCourses(args.getOrNull(0) as? String) }
                        .onFailure { NexioLog.e(TAG, "课程解析失败", it) }
                        .getOrDefault(emptyList())
                    if (parsed.isEmpty()) {
                        fail(L("未解析到课程数据", "No course data could be parsed")())
                        rejectPromise(promiseId, L("未解析到课程数据", "No course data could be parsed")())
                        return
                    }
                    courses = parsed
                    resolvePromise(promiseId, "true")
                    complete()
                }
                "saveCourseConfig" -> {
                    runCatching { parseJsonObject(args.getOrNull(0) as? String ?: "") }
                        .getOrNull()?.let {
                            startDate = it.optString("semesterStartDate").takeIf { s -> s.isNotBlank() }
                            val weeks = it.optString("semesterTotalWeeks").toIntOrNull()
                            totalWeeks = weeks?.takeIf { w -> w > 0 }
                        }
                    resolvePromise(args.getOrNull(1) as? String, "true")
                }
                "savePresetTimeSlots" -> {
                    timeSlots = runCatching { parseTimeSlots(args.getOrNull(0) as? String) }
                        .getOrDefault(emptyList())
                    resolvePromise(args.getOrNull(1) as? String, "true")
                }
                "saveComboSchedule" -> resolvePromise(args.getOrNull(1) as? String, "true")
                "showAlert" -> {
                    val content = args.getOrNull(1) as? String
                    fail(content?.takeIf { it.isNotBlank() } ?: L("导入失败", "Import failed")())
                    resolvePromise(args.getOrNull(3) as? String, "true")
                }
                "showPrompt" -> resolvePromise(args.getOrNull(4) as? String, "'${escapeJs(shareKey)}'")
                "showSingleSelection" -> {
                    // ⚠ JS number 过桥后是 NSNumber，不能 as? Number（见类注释第 2 点）
                    val index = args.getOrNull(2)?.toString()?.toIntOrNull() ?: 0
                    resolvePromise(args.getOrNull(3) as? String, index.toString())
                }
            }
        }

        // ── Native → 脚本（兑现 Promise）──

        private fun resolvePromise(promiseId: String?, result: String) {
            val id = promiseId ?: return
            val js = "window._resolveAndroidPromise && " +
                "window._resolveAndroidPromise('${escapeJs(id)}', $result);"
            runOnWebView(js)
        }

        private fun rejectPromise(promiseId: String?, error: String) {
            val id = promiseId ?: return
            val js = "window._rejectAndroidPromise && " +
                "window._rejectAndroidPromise('${escapeJs(id)}', '${escapeJs(error)}');"
            runOnWebView(js)
        }

        private fun runOnWebView(js: String) {
            val wv = webView ?: return
            postMain { wv.evaluateJavaScript(js, null) }
        }

        // ── 收口 ──

        private fun complete() {
            if (settled) return
            val list = courses
            if (list.isNullOrEmpty()) {
                fail(L("未解析到课程数据", "No course data could be parsed")())
                return
            }
            settled = true
            deferred.complete(
                ThirdPartySharePayload(
                    source = ThirdPartyShareSource.WakeUp,
                    courses = list,
                    timeSlots = timeSlots,
                    semesterStartDate = startDate,
                    semesterTotalWeeks = totalWeeks,
                )
            )
        }

        private fun fail(message: String) {
            if (settled) return
            settled = true
            deferred.completeExceptionally(IllegalStateException(message))
            if (!pageLoaded.isCompleted) pageLoaded.complete(Unit)
        }

        // ── 解析（字段与 Android 的 Gson data class 逐字一致）──

        private fun parseCourses(json: String?): List<ThirdPartyCourse> {
            val array = runCatching { parseJsonArray(json.orEmpty()) }.getOrNull()
                ?: return emptyList()
            return (0 until array.size).mapNotNull { i ->
                val item = array.optJsonObject(i) ?: return@mapNotNull null
                val weeks = item.optJsonArray("weeks")?.mapInts().orEmpty()
                    .sorted().ifEmpty { listOf(1) }
                val start = item.intOrNull("startSection") ?: 1
                ThirdPartyCourse(
                    name = item.optString("name"),
                    teacher = item.optString("teacher"),
                    position = item.optString("position"),
                    day = item.intOrNull("day") ?: 1,
                    startSection = start,
                    endSection = item.intOrNull("endSection") ?: start,
                    weeks = weeks,
                    isCustomTime = item.optBoolean("isCustomTime", false),
                    customStartTime = item.optString("customStartTime").takeIf { it.isNotBlank() },
                    customEndTime = item.optString("customEndTime").takeIf { it.isNotBlank() },
                )
            }
        }

        private fun parseTimeSlots(json: String?): List<ThirdPartyTimeSlot> {
            val array = runCatching { parseJsonArray(json.orEmpty()) }.getOrNull()
                ?: return emptyList()
            return (0 until array.size).mapNotNull { i ->
                val item = array.optJsonObject(i) ?: return@mapNotNull null
                val number = item.intOrNull("number") ?: return@mapNotNull null
                val start = item.optString("startTime").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val end = item.optString("endTime").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ThirdPartyTimeSlot(number, start, end)
            }
        }
    }

    /** scriptMessageHandler：把 `(方法名, 参数)` 转出来。对象由 [Controller] 强引用持有。 */
    private class BridgeHandler(
        private val onMessage: (String, List<Any?>) -> Unit,
    ) : NSObject(), WKScriptMessageHandlerProtocol {

        override fun userContentController(
            userContentController: WKUserContentController,
            didReceiveScriptMessage: WKScriptMessage,
        ) {
            val body = didReceiveScriptMessage.body as? Map<*, *> ?: return
            val name = body["m"] as? String ?: return
            val args = (body["a"] as? List<*>)?.map { it } ?: emptyList<Any?>()
            onMessage(name, args)
        }
    }

    /** 载入完成才注入脚本；失败直接按导入失败收口。 */
    private class NavDelegate(
        private val onFinish: () -> Unit,
        private val onFail: (String) -> Unit,
    ) : NSObject(), WKNavigationDelegateProtocol {

        override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
            onFinish()
        }

        // ⚠ 只留一个 didFail：Kotlin 侧 `didFailNavigation` 与 `didFailProvisionalNavigation`
        // 签名**完全一样**（仅 ObjC selector 不同），两个都写会报 "Conflicting overloads"
        // （编译器提示的 @ObjCSignatureOverride 本仓库 Kotlin 版本没有）。
        // 载入失败/没载入完由 [Controller.awaitPageLoad] 的超时兜底。
        override fun webView(
            webView: WKWebView,
            didFailNavigation: WKNavigation?,
            withError: NSError,
        ) {
            onFail(withError.localizedDescription)
        }
    }
}

private fun JsonObject.intOrNull(key: String): Int? =
    optString(key).takeIf { it.isNotBlank() }?.toIntOrNull()

/** 数字数组：元素按文本读（primitive 的 `content` 就是数字串）再转 Int */
private fun JsonArray.mapInts(): List<Int> =
    (0 until size).mapNotNull { optString(it, "").toIntOrNull() }
