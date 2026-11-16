@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.util.parseJsonArray
import com.haooz.chedule.i18n.L
import com.haooz.chedule.ui.utils.IosUiHost
import kotlinx.cinterop.useContents
import platform.Foundation.NSError
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIDevice
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

private const val TAG = "IosWebHost"

/** scriptMessageHandler 注册名；[IOS_BRIDGE_JS] 里必须同名 */
private const val HANDLER_NAME = "NexioEduBridge"

/** 系统大版本号（"26.0" → "26"）。UA 里的版本号跟它走，绝不写死。 */
private fun iosMajorVersion(): String =
    UIDevice.currentDevice.systemVersion.substringBefore('.').takeIf { it.isNotBlank() } ?: "18"

/**
 * 手机版 UA —— 必须显式设置：WKWebView 默认 UA 缺 `Version/x.x ... Safari/604.1`，
 * 不少教务系统只认 Safari 版本号，会判「浏览器版本过低」拒绝渲染。
 */
private fun mobileUserAgent(): String {
    val v = iosMajorVersion()
    return "Mozilla/5.0 (iPhone; CPU iPhone OS ${v}_0 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/$v.0 Mobile/15E148 Safari/604.1"
}

/** 桌面版 UA（伪装成桌面 Safari，与 Android 侧 `buildDesktopUserAgent` 同一思路：版本取真实系统版本） */
private fun desktopUserAgent(): String {
    val v = iosMajorVersion()
    return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/$v.0 Safari/605.1.15"
}

/** 进度/标题轮询间隔（秒）：iOS 没有 Android 的 onProgressChanged，只能自己采 */
private const val POLL_INTERVAL_SECONDS = 0.25

/**
 * 伪造 `window.AndroidBridge`：WKWebView 没有 `addJavascriptInterface`，只有
 * `messageHandlers.X.postMessage` 单向通道；造签名逐字相同的桥把 `(方法名, 参数)` 转出来，
 * 云端脚本与共享 [JS_PROMISE_BRIDGE] 零改动（同 `IosWakeUpImport`）。
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
            isPostForwardEnabled: false,
            netLog: function(json) { post('netLog', [json]); }
        };
        window.WebPostService = { register: function(id, body, contentType) {} };
    })();
""".trimIndent()

/** 桌面版视口：与 Android 的 document-start 注入对齐（只声明宽度，缩放交给 UA/页面自身） */
private val DESKTOP_VIEWPORT_JS = """
    (function() {
        var metas = document.getElementsByTagName('meta');
        for (var i = metas.length - 1; i >= 0; i--) {
            if (metas[i].getAttribute('name') === 'viewport') metas[i].parentNode.removeChild(metas[i]);
        }
        var meta = document.createElement('meta');
        meta.name = 'viewport';
        meta.content = 'width=1280';
        document.head.appendChild(meta);
    })();
""".trimIndent()

/**
 * 造一个 iOS 网页宿主（WKWebView），对应 Android 的 `AndroidWebHost`。
 *
 * 与 Android 的差异（平台事实）：顶栏取色无等价物（不上报 onTopColor）；进度/标题靠轮询
 * （estimatedProgress 是 KVO 属性）；无 POST 转发通道（isPostForwardEnabled 恒 false）。
 *
 * ⚠ WKWebView 只能主线程创建与调用 —— 组合、回调都在主线程，别丢后台队列（会崩）。
 */
@Composable
fun rememberIosWebHost(config: WebHostConfig, events: WebHostEvents): WebHost {
    val currentConfig by rememberUpdatedState(config)
    val currentEvents by rememberUpdatedState(events)
    val controller = remember { IosWebHostController() }

    // 回调随重组刷新：WebView 的 delegate 是长生命周期对象，持旧闭包会写到过期 state 上
    controller.onTitle = { currentEvents.onTitle(it) }
    controller.onProgress = { currentEvents.onProgress(it) }
    controller.onCoursesImported = { currentEvents.onCoursesImported(it) }
    controller.onTaskCompleted = { currentEvents.onTaskCompleted() }
    controller.onAlert = { t, c, ok, onResult -> currentEvents.onAlert(t, c, ok, onResult) }
    controller.onPrompt = { t, tip, d, v, onResult -> currentEvents.onPrompt(t, tip, d, v, onResult) }
    controller.onSingleSelection = { t, items, idx, onResult ->
        currentEvents.onSingleSelection(t, items, idx, onResult)
    }

    LaunchedEffect(Unit) { controller.attach() }

    LaunchedEffect(currentConfig.isDesktopMode) {
        controller.setDesktopMode(currentConfig.isDesktopMode)
    }

    // 必须早于首轮 load：document-start 脚本只对之后的文档生效
    LaunchedEffect(currentConfig.netRecorderEnabled) {
        controller.setNetRecorderEnabled(currentConfig.netRecorderEnabled)
    }

    LaunchedEffect(currentConfig.importUrl) {
        controller.load(currentConfig.importUrl)
    }

    LaunchedEffect(currentConfig.contentTopPadding, currentConfig.contentBottomPadding) {
        controller.setContentPaddings(
            topPoints = currentConfig.contentTopPadding.value.toDouble(),
            bottomPoints = currentConfig.contentBottomPadding.value.toDouble(),
        )
    }

    DisposableEffect(Unit) {
        onDispose { controller.dispose() }
    }

    return remember(controller) {
        object : WebHost {
            @Composable
            override fun Content(modifier: Modifier) {
                // ⚠ 刻意不用传入的 modifier：它带 Offscreen（Android 为 layerBackdrop 录
                // WebView 像素而加），iOS 原生视图包进离屏 layer 后不参与合成，整块变黑。
                UIKitView(factory = { controller.webView }, modifier = Modifier.fillMaxSize())
            }

            override fun reload() = controller.reload()

            override fun canGoBack(): Boolean = controller.canGoBack()

            override fun goBack() = controller.goBack()

            override fun currentHostOrNull(): String? =
                controller.currentHost()?.lowercase()?.removePrefix("www.")

            override fun evaluateJavascript(js: String) = controller.eval(js)

            override fun evaluateJavascriptWithResult(js: String, onResult: (String?) -> Unit) =
                controller.evalWithResult(js, onResult)

            override fun setImportTableId(tableId: String) = controller.core.setImportTableId(tableId)
        }
    }
}

/**
 * WKWebView 宿主本体。
 *
 * ⚠ handler / navDelegate / timer 都被本对象强引用 —— addScriptMessageHandler 与
 * navigationDelegate 都是 weak，不拿住回调永远不来（表现为「点了导入没反应」）。
 */
private class IosWebHostController {

    var onTitle: (String) -> Unit = {}
    var onProgress: (Float) -> Unit = {}
    var onCoursesImported: (List<Course>) -> Unit = {}
    var onTaskCompleted: () -> Unit = {}
    var onAlert: ((String, String, String, (Boolean) -> Unit) -> Unit)? = null
    var onPrompt: ((String, String, String, String, (String?) -> Unit) -> Unit)? = null
    var onSingleSelection: ((String, List<String>, Int, (Int?) -> Unit) -> Unit)? = null

    /** 负载解析 / 落盘 / 作息顺序约束在共享内核里（Android 走同一份） */
    val core = EduImportBridgeCore(
        onCoursesImported = { onCoursesImported(it) },
        onTaskCompleted = { onTaskCompleted() },
    )

    private val handler = BridgeHandler { name, args -> handle(name, args) }
    private val navDelegate = NavDelegate(
        onFinish = { title ->
            NexioLog.d(TAG, "didFinish: $title")
            title?.takeIf { it.isNotBlank() }?.let(onTitle)
            onProgress(1f)
        },
        onFail = { error -> NexioLog.w(TAG, "加载失败: $error") },
    )

    private var timer: NSTimer? = null
    private var desktopMode = false
    private var netRecorderEnabled = false

    /** 首次必须应用一次 UA（手机版也要）—— 否则拿到的是 WKWebView 那个「版本过低」的默认 UA */
    private var userAgentApplied = false

    val webView: WKWebView = WKWebView(
        frame = platform.CoreGraphics.CGRectMake(0.0, 0.0, 0.0, 0.0),
        configuration = WKWebViewConfiguration().apply {
            userContentController = WKUserContentController().apply {
                addScriptMessageHandler(handler, HANDLER_NAME)
                // 桥与 Promise 基础设施都走 document-start：脚本可能在页面早期就调用，
                // 只靠 didFinish 注入会漏（Android 侧同理，见 WebCompatDelegate）。
                addUserScript(userScript(IOS_BRIDGE_JS))
                addUserScript(userScript(JS_PROMISE_BRIDGE))
            }
        },
    ).apply {
        navigationDelegate = navDelegate
        // 登录态与 Android 一样跨页面保存：默认 WKWebsiteDataStore 就是共享的
        opaque = false
        backgroundColor = platform.UIKit.UIColor.clearColor
        // 网页铺满整屏（含 home indicator 区域），滚动让位按系统安全区算（见 applySafeAreaInsets），不再手写 padding
        scrollView.contentInsetAdjustmentBehavior =
            UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
    }

    /** 页面额外要求的让位（pt）：顶部一般 0（交给系统），底部 = 悬浮条高度 */
    private var topPadding = 0.0
    private var bottomBarPadding = 0.0
    private var appliedTopInset = -1.0
    private var appliedBottomInset = -1.0

    fun setContentPaddings(topPoints: Double, bottomPoints: Double) {
        if (topPadding == topPoints && bottomBarPadding == bottomPoints) return
        topPadding = topPoints
        bottomBarPadding = bottomPoints
        appliedTopInset = -1.0
        appliedBottomInset = -1.0
    }

    /**
     * 把「系统安全区 + 页面要求的让位」写进滚动 inset，让网页铺满整屏、内容由 inset 避让。
     *
     * ⚠ 顶部用 webView 自己的 `safeAreaInsets.top`（含状态栏 + 原生导航栏）；window 那份只有
     * 状态栏会少算导航栏，自己被 Compose 容器清零时退回 window 兜底。放轮询里跟旋转/分屏。
     */
    private fun applySafeAreaInsets() {
        val window = webView.window ?: IosUiHost.keyWindow() ?: return
        val ownTop = webView.safeAreaInsets.useContents { top }
        val safeTop = if (ownTop > 1.0) ownTop else window.safeAreaInsets.useContents { top }
        val safeBottom = window.safeAreaInsets.useContents { bottom }
        val top = safeTop + topPadding
        val bottom = safeBottom + bottomBarPadding
        if (kotlin.math.abs(top - appliedTopInset) < 0.5 &&
            kotlin.math.abs(bottom - appliedBottomInset) < 0.5
        ) {
            return
        }
        appliedTopInset = top
        appliedBottomInset = bottom
        NexioLog.w(TAG, "contentInset top=$top (safe=$safeTop) bottom=$bottom (safe=$safeBottom)")
        val insets = UIEdgeInsetsMake(top, 0.0, bottom, 0.0)
        webView.scrollView.contentInset = insets
        webView.scrollView.verticalScrollIndicatorInsets = insets
    }

    fun attach() {
        startPolling()
    }

    fun setDesktopMode(enabled: Boolean) {
        if (userAgentApplied && desktopMode == enabled) return
        userAgentApplied = true
        desktopMode = enabled
        webView.customUserAgent = if (enabled) desktopUserAgent() else mobileUserAgent()
        // 切模式时打一次，排查「教务系统判浏览器版本过低」时全靠它
        NexioLog.w(TAG, "UA: ${webView.customUserAgent}")
        // ⚠ 换 UA 只对新文档生效，必须重新取页（与 Android 的「换 UA 后重载」同一坑）
        installScripts(enabled)
        if (webView.URL != null) webView.reload()
    }

    /**
     * AI 教务导入的网络记录器开关。
     *
     * 只影响之后的文档：调用方必须在首轮 `load` 之前打开，否则首屏请求抓不到。
     */
    fun setNetRecorderEnabled(enabled: Boolean) {
        if (netRecorderEnabled == enabled) return
        netRecorderEnabled = enabled
        installScripts(desktopMode)
    }

    /** 全量重装 document-start 脚本；顺序固定：桥 → Promise → 视口 → 网络记录器。 */
    private fun installScripts(desktop: Boolean) {
        webView.configuration.userContentController.removeAllUserScripts()
        webView.configuration.userContentController.apply {
            addUserScript(userScript(IOS_BRIDGE_JS))
            addUserScript(userScript(JS_PROMISE_BRIDGE))
            if (desktop) addUserScript(userScript(DESKTOP_VIEWPORT_JS))
            // 记录器要盖住子 frame：不少教务系统把课表塞在 iframe 里
            if (netRecorderEnabled) addUserScript(userScript(EDU_NET_RECORDER_JS, allFrames = true))
        }
    }

    fun load(url: String?) {
        if (url.isNullOrBlank() || url == "about:blank") return
        val full = if (url.startsWith("http")) url else "https://$url"
        val nsUrl = NSURL.URLWithString(full) ?: return
        NexioLog.d(TAG, "load: $full")
        webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
    }

    fun reload() {
        webView.reload()
    }

    fun canGoBack(): Boolean = webView.canGoBack

    fun goBack() {
        webView.goBack()
    }

    fun currentHost(): String? = webView.URL?.host

    fun eval(js: String) {
        webView.evaluateJavaScript(js, null)
    }

    /**
     * 执行 JS 并回传结果（AI 教务导入读页面 / 打接口用）。
     *
     * WKWebView 的 completion 回到主线程，但结果可能是 NSString / NSNumber / NSArray，
     * 这里统一 `toString()` —— 调用方拿到的应该是脚本自己 `return` 的字符串，
     * 真给了对象时至少还能看到内容，不至于静默变 null。
     */
    fun evalWithResult(js: String, onResult: (String?) -> Unit) {
        webView.evaluateJavaScript(js) { result: Any?, _: NSError? ->
            onResult(result?.toString())
        }
    }

    fun dispose() {
        timer?.invalidate()
        timer = null
        webView.stopLoading()
        webView.navigationDelegate = null
        webView.configuration.userContentController
            .removeScriptMessageHandlerForName(HANDLER_NAME)
    }

    // ── 进度 / 标题轮询 ─────────────────────────────────────────────

    private fun startPolling() {
        timer = NSTimer.scheduledTimerWithTimeInterval(POLL_INTERVAL_SECONDS, repeats = true) {
            onProgress(webView.estimatedProgress.toFloat())
            webView.title?.takeIf { it.isNotBlank() }?.let(onTitle)
            applySafeAreaInsets()
        }
    }

    // ── 脚本 → Native ──────────────────────────────────────────────

    private fun handle(name: String, args: List<Any?>) {
        NexioLog.d(TAG, "bridge: $name")
        when (name) {
            "netLog" -> runCatching { recordEduNetLog(args.getOrNull(0)?.toString().orEmpty()) }
            "showToast" -> showToast(args.getOrNull(0)?.toString().orEmpty())
            "showAlert" -> {
                val promiseId = args.getOrNull(3)?.toString()
                val callback = onAlert
                if (callback == null) {
                    resolve(promiseId, "true")
                } else {
                    callback(
                        args.getOrNull(0)?.toString().orEmpty(),
                        args.getOrNull(1)?.toString().orEmpty(),
                        args.getOrNull(2)?.toString().orEmpty(),
                    ) { confirmed -> resolve(promiseId, if (confirmed) "true" else "false") }
                }
            }
            "showPrompt" -> {
                val promiseId = args.getOrNull(4)?.toString()
                val validator = args.getOrNull(3)?.toString().orEmpty()
                val callback = onPrompt
                if (callback == null) {
                    resolve(promiseId, "'${escapeJs(args.getOrNull(2)?.toString().orEmpty())}'")
                } else {
                    callback(
                        args.getOrNull(0)?.toString().orEmpty(),
                        args.getOrNull(1)?.toString().orEmpty(),
                        args.getOrNull(2)?.toString().orEmpty(),
                        validator,
                    ) { input ->
                        if (input == null) resolve(promiseId, "null")
                        else validateThenResolve(promiseId, validator, input)
                    }
                }
            }
            "showSingleSelection" -> {
                val promiseId = args.getOrNull(3)?.toString()
                val items = runCatching {
                    parseJsonArray(args.getOrNull(1)?.toString().orEmpty())
                        .map { it.toString().trim('"') }
                }.getOrDefault(emptyList())
                // ⚠ JS number 过桥是 NSNumber，不能 as? Number（见 IosWakeUpImport 注释）
                val defaultIndex = args.getOrNull(2)?.toString()?.toIntOrNull() ?: -1
                val callback = onSingleSelection
                if (callback == null) {
                    resolve(promiseId, defaultIndex.toString())
                } else {
                    callback(
                        args.getOrNull(0)?.toString().orEmpty(),
                        items,
                        defaultIndex,
                    ) { index -> resolve(promiseId, index?.toString() ?: "null") }
                }
            }
            else -> dispatchToCore(name, args)
        }
    }

    /** 课程 / 配置 / 时间段 / 组合作息 / notifyTaskCompletion 全在共享内核里 */
    private fun dispatchToCore(name: String, args: List<Any?>) {
        val promiseId = args.getOrNull(1)?.toString()
        try {
            val result = core.handle(name, args)
            if (result == null) {
                NexioLog.w(TAG, "未知桥方法: $name")
                return
            }
            resolve(promiseId, result)
        } catch (e: EduBridgeReject) {
            NexioLog.w(TAG, "$name 被拒绝: ${e.message}")
            showToast(e.message ?: L("导入失败", "Import failed")())
            reject(promiseId, e.message ?: L("导入失败", "Import failed")())
        } catch (e: Exception) {
            NexioLog.e(TAG, "$name 处理异常", e)
            reject(promiseId, L("导入失败：%s", "Import failed: %s")(e.message ?: ""))
        }
    }

    // ── 输入校验 / Promise 兑现 ────────────────────────────────────

    /**
     * 官方约定：验证函数返回 `false` 表示通过，返回错误文案表示失败；执行的是脚本在全局
     * 作用域定义的函数名（与 Android 一致）。
     */
    private fun validateThenResolve(promiseId: String?, validator: String, input: String) {
        if (validator.isBlank()) {
            resolve(promiseId, "'${escapeJs(input)}'")
            return
        }
        webView.evaluateJavaScript("$validator('${escapeJs(input)}')") { result, _ ->
            val text = (result as? String)?.trim('"')
            if (text.isNullOrEmpty() || text.equals("false", ignoreCase = true)) {
                resolve(promiseId, "'${escapeJs(input)}'")
            } else {
                showToast(text)
                reject(promiseId, text)
            }
        }
    }

    private fun resolve(promiseId: String?, result: String) {
        val id = promiseId ?: return
        webView.evaluateJavaScript(
            "window._resolveAndroidPromise && window._resolveAndroidPromise('${escapeJs(id)}', $result);",
            null,
        )
    }

    private fun reject(promiseId: String?, error: String) {
        val id = promiseId ?: return
        webView.evaluateJavaScript(
            "window._rejectAndroidPromise && window._rejectAndroidPromise('${escapeJs(id)}', '${escapeJs(error)}');",
            null,
        )
    }

    private fun userScript(source: String, allFrames: Boolean = false): WKUserScript = WKUserScript(
        source = source,
        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
        forMainFrameOnly = !allFrames,
    )
}

private fun escapeJs(value: String): String = value
    .replace("\\", "\\\\")
    .replace("'", "\\'")
    .replace("\n", "\\n")
    .replace("\r", "")

/** scriptMessageHandler：把 `(方法名, 参数)` 转出来。对象由 [IosWebHostController] 强引用持有。 */
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

/**
 * 载入完成时刷一次标题并把进度顶到 1。
 *
 * ⚠ **只能留一个 `webView(_:didXxx:)`**：`didFinishNavigation` 与 `didStartProvisionalNavigation`
 * Kotlin 侧签名完全一样（`(WKWebView, WKNavigation?)`），两个都写报 "Conflicting overloads"
 * （`@ObjCSignatureOverride` 本仓库 Kotlin 版本没有）—— 同 `IosWakeUpImport` 的 didFail 坑。
 * 加载中的进度由 `estimatedProgress` 轮询提供，不需要 start 回调。
 */
private class NavDelegate(
    private val onFinish: (String?) -> Unit,
    private val onFail: (String) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onFinish(webView.title)
    }

    // ⚠ 只留 provisional 这个失败回调：它与 didFailNavigation 签名相同，
    // 两个都写会报 Conflicting overloads（同 IosWakeUpImport）。
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) {
        onFail(withError.localizedDescription)
    }
}
