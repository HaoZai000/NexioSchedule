package com.haooz.chedule.ui.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.PixelCopy
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.AndroidMiscStr
import com.haooz.chedule.i18n.SchedStr
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.delay

/** 顶栏色条采样高度：只取页面最顶部几行，用来统计主导底色 */
private const val TOP_MIRROR_SAMPLE_HEIGHT_PX = 12

/** 顶栏色条采样延迟启动 */
private const val TOP_MIRROR_START_DELAY_MS = 600L

/** 采样节流间隔：顶栏色是缓变的大面积底色，逐帧采纯浪费 */
private const val TOP_MIRROR_SAMPLE_INTERVAL_MS = 120L

/** JS 报"基础库没定义"的特征：站点脚本没加载全（多因 `X-Requested-With` 被判非浏览器） */
private val JS_LIB_MISSING = Regex("""(\$|jQuery) is not defined|requires jQuery""")

/** 主导色与上一帧接近时的通道阈值，低于此视为同一颜色 */
private const val TOP_MIRROR_COLOR_CLOSE_DELTA = 20

// ════════════════════════════════════════════════════════════════════════
//  网页内容宿主 —— Android 实现（android.webkit.WebView）
//
//  页面外壳已迁 :ui-shared（见 ui/web/WebHost.kt 契约），这里只剩网页本体：
//  创建配置 / UA 与视口策略（委派 WebCompatDelegate）/ JS 桥 / PixelCopy 逐帧采色 / 销毁。
//  iOS 侧对应 IosWebHost（WKWebView），本文件逻辑一行都复用不了。
// ════════════════════════════════════════════════════════════════════════


private fun pickDominantColor(src: Bitmap, prevColor: Int): Int {
    // 页面销毁时 onDispose 会回收采样位图，而 PixelCopy 回调可能晚一步才到
    if (src.isRecycled) return if (prevColor != -1) prevColor else 0xFF808080.toInt()
    val width = src.width
    val height = src.height
    if (width <= 0 || height <= 0) {
        return if (prevColor != -1) prevColor else 0xFF808080.toInt()
    }

    val pixels = IntArray(width * height)
    src.getPixels(pixels, 0, width, 0, 0, width, height)
    val total = pixels.size

    // 4bit/通道量化：key = 0..4095
    val counts = IntArray(4096)
    val sumR = IntArray(4096)
    val sumG = IntArray(4096)
    val sumB = IntArray(4096)
    val touched = IntArray(total.coerceAtMost(4096))
    var touchedN = 0

    val pr = if (prevColor != -1) (prevColor ushr 16) and 0xFF else -1
    val pg = if (prevColor != -1) (prevColor ushr 8) and 0xFF else -1
    val pb = if (prevColor != -1) prevColor and 0xFF else -1
    var prevNearCount = 0

    for (i in pixels.indices) {
        val c = pixels[i]
        val r = (c ushr 16) and 0xFF
        val g = (c ushr 8) and 0xFF
        val b = c and 0xFF
        if (pr >= 0 &&
            abs(r - pr) <= TOP_MIRROR_COLOR_CLOSE_DELTA &&
            abs(g - pg) <= TOP_MIRROR_COLOR_CLOSE_DELTA &&
            abs(b - pb) <= TOP_MIRROR_COLOR_CLOSE_DELTA
        ) {
            prevNearCount++
        }
        val key = ((r ushr 4) shl 8) or ((g ushr 4) shl 4) or (b ushr 4)
        if (counts[key] == 0) {
            if (touchedN < touched.size) {
                touched[touchedN] = key
                touchedN++
            }
        }
        counts[key]++
        sumR[key] += r
        sumG[key] += g
        sumB[key] += b
    }

    if (touchedN == 0) {
        return if (prevColor != -1) prevColor else 0xFF808080.toInt()
    }

    var bestKey = touched[0]
    var bestCount = counts[bestKey]
    var t = 1
    while (t < touchedN) {
        val k = touched[t]
        val cnt = counts[k]
        if (cnt > bestCount) {
            bestCount = cnt
            bestKey = k
        }
        t++
    }

    val n = bestCount.coerceAtLeast(1)
    val avgR = (sumR[bestKey] / n).coerceIn(0, 255)
    val avgG = (sumG[bestKey] / n).coerceIn(0, 255)
    val avgB = (sumB[bestKey] / n).coerceIn(0, 255)
    val color = (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB

    if (prevColor == -1) return color

    val close = abs(pr - avgR) <= TOP_MIRROR_COLOR_CLOSE_DELTA &&
        abs(pg - avgG) <= TOP_MIRROR_COLOR_CLOSE_DELTA &&
        abs(pb - avgB) <= TOP_MIRROR_COLOR_CLOSE_DELTA
    if (close) return color

    // 上一帧色已几乎不在采样里，或新众数比「接近上一帧的像素」多 5% 以上 → 切换
    // 对半开时两边接近，维持上一帧；页面真变色时 prevNearCount 会掉下去，能切走
    if (prevNearCount * 20 < total || bestCount * 100 > prevNearCount * 105) {
        return color
    }
    return prevColor
}

/**
 * 造一个 Android 网页宿主。
 *
 * `config` / `events` 都随页面状态变，内部用 [rememberUpdatedState] 跟：
 * WebView 的 client 是长生命周期对象，持旧闭包会写到过期的 state 上。
 */
@Composable
fun rememberAndroidWebHost(
    config: WebHostConfig,
    events: WebHostEvents,
): WebHost {
    val context = LocalContext.current
    val currentConfig by rememberUpdatedState(config)
    val currentEvents by rememberUpdatedState(events)
    val density = LocalDensity.current


    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.Transparent.toArgb())
            // 与 SleepDown 一致：硬件层 + 后面 AndroidView 的 Offscreen，页面像素才能被 layerBackdrop 录进去
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
    }
    var importCompleted by remember { mutableStateOf(false) }
    val androidBridge = remember {
        AndroidBridge(
            context = context,
            webView = webView,
            onCourseImported = { courses ->
                importCompleted = true
                currentEvents.onCoursesImported(courses)
            },
            onTaskCompleted = {
                currentEvents.onTaskCompleted()
                if (!importCompleted) {
                    showToast(SchedStr.importDone(), longDuration = true)
                }
            },
            onShowAlert = { title, content, confirmText, onResult ->
                currentEvents.onAlert(title, content, confirmText, onResult)
            },
            onShowPrompt = { title, tip, defaultText, validatorJs, onResult ->
                currentEvents.onPrompt(title, tip, defaultText, validatorJs, onResult)
            },
            onShowSingleSelection = { title, items, defaultIndex, onResult ->
                currentEvents.onSingleSelection(title, items, defaultIndex, onResult)
            }
        )
    }
    // 顶栏空白区：采样页面顶部一小段的主导底色（int 表示，无 bitmap 中间层）。
    // 上一帧主导色（滞回用）
    val lastTopMirrorColor = remember { intArrayOf(-1) }
    val edgeSampleHandler = remember { Handler(Looper.getMainLooper()) }
    val topMirrorVisiblePx = with(density) { currentConfig.contentTopPadding.roundToPx() }
    val pixelCopyInFlight = remember { AtomicBoolean(false) }
    val mirrorFrameRunning = remember { AtomicBoolean(false) }
    val rawBitmapRef = remember { arrayOfNulls<Bitmap>(1) }

    val captureWebTopMirror: (WebView) -> Unit = remember(topMirrorVisiblePx) {
        { target ->
            if (topMirrorVisiblePx <= 0) return@remember
            // 上一帧还没回来就跳过，避免 PixelCopy 排队打满
            if (!pixelCopyInFlight.compareAndSet(false, true)) return@remember
            try {
                val activity = target.context.findActivity()
                val window = activity?.window
                val width = target.width
                val height = target.height
                if (window != null && width > 0 && height > 0) {
                    // 只采样固定高度的一小段：足够判断色块/文字，又比整条 top padding 便宜
                    val strip = TOP_MIRROR_SAMPLE_HEIGHT_PX.coerceAtMost(height)
                    val location = IntArray(2)
                    target.getLocationInWindow(location)
                    val src = Rect(
                        location[0],
                        location[1],
                        location[0] + width,
                        location[1] + strip
                    )
                    var raw = rawBitmapRef[0]
                    if (raw == null || raw.isRecycled || raw.width != width || raw.height != strip) {
                        raw?.takeIf { !it.isRecycled }?.recycle()
                        raw = Bitmap.createBitmap(width, strip, Bitmap.Config.ARGB_8888)
                        rawBitmapRef[0] = raw
                    }
                    PixelCopy.request(
                        window,
                        src,
                        raw,
                        listener@{ result ->
                            // 位图已被 onDispose 回收：回调还在路上，直接丢弃这一帧
                            if (raw.isRecycled) {
                                pixelCopyInFlight.set(false)
                                return@listener
                            }
                            if (result == PixelCopy.SUCCESS) {
                                val target = pickDominantColor(raw, lastTopMirrorColor[0])
                                lastTopMirrorColor[0] = target
                                // 每次采样直接上报采样色
                                currentEvents.onTopColor(
                                    Color(
                                        red = ((target ushr 16) and 0xFF) / 255f,
                                        green = ((target ushr 8) and 0xFF) / 255f,
                                        blue = (target and 0xFF) / 255f,
                                        alpha = 1f
                                    )
                                )
                            }
                            pixelCopyInFlight.set(false)
                        },
                        edgeSampleHandler
                    )
                } else {
                    pixelCopyInFlight.set(false)
                }
            } catch (_: Throwable) {
                pixelCopyInFlight.set(false)
            }
        }
    }

    DisposableEffect(webView, captureWebTopMirror) {
        val choreographer = Choreographer.getInstance()
        // 上次实际发起采样的帧时间（ms）；0 = 还没采过
        val lastSampleMs = longArrayOf(0L)
        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                // 循环常驻：mirrorFrameRunning 只决定这一帧采不采样，
                // 不在 doFrame 里 return 断链（否则延迟启动后没人再把它唤起）
                if (mirrorFrameRunning.get()) {
                    // 节流：顶栏色是缓变的大面积底色，不需要逐帧精度
                    val nowMs = frameTimeNanos / 1_000_000L
                    if (nowMs - lastSampleMs[0] >= TOP_MIRROR_SAMPLE_INTERVAL_MS) {
                        lastSampleMs[0] = nowMs
                        captureWebTopMirror(webView)
                    }
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(frameCallback)
        onDispose {
            mirrorFrameRunning.set(false)
            choreographer.removeFrameCallback(frameCallback)
            rawBitmapRef[0]?.takeIf { !it.isRecycled }?.recycle()
            rawBitmapRef[0] = null
        }
    }
    // 顶栏色条采样延后启动：
    LaunchedEffect(Unit) {
        delay(TOP_MIRROR_START_DELAY_MS)
        mirrorFrameRunning.set(true)
    }
    val compatDelegate = remember(webView) { WebCompatDelegate(webView) }
    LaunchedEffect(Unit) {
        webView.addJavascriptInterface(androidBridge, "AndroidBridge")
        webView.addJavascriptInterface(WebPostBridge(), "WebPostService")
    }
    // AI 教务导入：document-start 挂网络记录器。注册必须早于首次 loadUrl，
    // 否则页面首屏那几个课表接口已经在钩子安装前发完。
    DisposableEffect(webView, config.netRecorderEnabled) {
        if (!config.netRecorderEnabled) return@DisposableEffect onDispose {}
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            NexioLog.w("EduNet", "当前 WebView 不支持 document-start，无法记录页面首屏请求")
            return@DisposableEffect onDispose {}
        }
        val handler = WebViewCompat.addDocumentStartJavaScript(
            webView,
            EDU_NET_RECORDER_JS,
            setOf("*"),
        )
        onDispose { handler.remove() }
    }
    // 本轮是否已试过切到转发通道；每轮模式切换重置，避免反复重载打转。
    val forwardSwitched = remember { AtomicBoolean(false) }
    /**
     * 转发通道重载延迟：触发场景（脚本 4xx / `$ is not defined`）天然紧跟一次
     * "可疑请求"，立刻 reload 会被不少 WAF 的"短时间同源重复请求"规则打
     * （挑战页 / 429）。约 1 秒的间隔够把重载拆进下一个统计窗口，又不显得卡。
     */
    val forwardReloadDelayMs = 1200L
    /** 对该 host 开转发后延迟重载一次自救；已开过则不动 —— 那就不是 `X-Requested-With` 的问题。 */
    val tryForwardAndReload: () -> Unit = remember(webView) {
        {
            if (WebForwardHosts.enable(webView.url?.toUri()?.host)) {
                showToast(AndroidMiscStr.reloadSiteDifferently(), longDuration = true)
                webView.postDelayed({
                    // 延迟期间用户可能已退出导入页（WebView 被 destroy）：detach 后不重载
                    if (!webView.isAttachedToWindow) return@postDelayed
                    runCatching { webView.reload() }
                }, forwardReloadDelayMs)
            }
        }
    }
    LaunchedEffect(config.isDesktopMode) {
        forwardSwitched.set(false)
        compatDelegate.enhanceSettings(config.isDesktopMode)
        // 视口覆盖必须赶在页面自身脚本之前注册，晚了站点就把像素尺寸写死了（登录弹窗会留在屏外）
        compatDelegate.applyDesktopViewportOverride(config.isDesktopMode)
        webView.settings.userAgentString = if (config.isDesktopMode)
            com.haooz.chedule.ui.web.buildDesktopUserAgent(context)
        else WebSettings.getDefaultUserAgent(context)

        webView.webViewClient = compatDelegate.wrapWebViewClient(
            object : WebViewClient() {
                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url.isNullOrBlank()) return false
                    val scheme = url.toUri().scheme?.lowercase() ?: return false
                    if (scheme == "http" || scheme == "https") return false
                    // 自定义 scheme（intent://、market://、tel:// 等）交给外部处理
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                            url.toUri())
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        view?.context?.startActivity(intent)
                    } catch (_: Exception) { }
                    return true
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    val scheme = request.url.scheme?.lowercase() ?: return false
                    if (scheme == "http" || scheme == "https") return false
                    // 同上：自定义 scheme 外跳
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        view?.context?.startActivity(intent)
                    } catch (_: Exception) { }
                    return true
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    // ⚠ 子资源证书错误 Chromium 不回调这里（只回调主框架）、直接静默丢弃，
                    // 只能靠 onReceivedHttpError 与 onConsoleMessage 兜。
                    NexioLog.w("EduImport", AndroidMiscStr.logSslProceedN(error.url, error.primaryError))
                    handler.proceed()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    val urlScheme = request.url.scheme?.lowercase() ?: ""
                    val isCustomScheme = urlScheme.isNotEmpty() && urlScheme != "http" && urlScheme != "https"
                    if (request.isForMainFrame) {
                        if (isCustomScheme) {
                            // 自定义 scheme 加载失败，回到之前的页面
                            view.post {
                                if (view.canGoBack()) view.goBack() else view.loadUrl("about:blank")
                            }
                        } else {
                            view.post {
                                showToast(AndroidMiscStr.loadFailedN(error.description), longDuration = true)
                            }
                        }
                    } else {
                        // ⚠ 子资源失败必须留痕：此前完全静默，页面"没样式/图白/按钮无反应"
                        // 而日志一个字都没有。
                        NexioLog.w(
                            "EduImport",
                            AndroidMiscStr.logSubresourceFailedN(error.errorCode, request.url, error.description)
                        )
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse
                ) {
                    // 4xx/5xx 只走这里（onReceivedError 只报网络层失败）：
                    // 静态资源被 WAF 拦、防盗链、路径改版 404，都在这里现形。
                    val frame = if (request.isForMainFrame) "main" else "sub"
                    NexioLog.w(
                        "EduImport",
                        "HTTP ${errorResponse.statusCode} [$frame] ${request.url} " +
                            "mime=${errorResponse.mimeType} enc=${errorResponse.encoding}"
                    )
                    // 脚本/样式拿不到 → 换转发通道重试。favicon 的 dest 是 image，不会误触发。
                    val dest = request.requestHeaders["Sec-Fetch-Dest"]?.lowercase()
                    if (errorResponse.statusCode >= 400 &&
                        (dest == "script" || dest == "style") &&
                        forwardSwitched.compareAndSet(false, true)
                    ) {
                        tryForwardAndReload()
                    }
                }
            },
            config.isDesktopMode
        )

        webView.webChromeClient = compatDelegate.wrapWebChromeClient(
            object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    currentEvents.onProgress(newProgress / 100f)
                }
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (title != null) currentEvents.onTitle(title)
                }
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        NexioLog.d("EduImport", "JS [${it.messageLevel()}]: ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                        // 站点脚本库没加载全（典型是 X-Requested-With 被当成非浏览器而降级）：
                        // 对该 host 开启 OkHttp 转发并重载一次自救。
                        if (it.messageLevel() == ConsoleMessage.MessageLevel.ERROR &&
                            JS_LIB_MISSING.containsMatchIn(it.message().orEmpty()) &&
                            forwardSwitched.compareAndSet(false, true)
                        ) {
                            tryForwardAndReload()
                        }
                    }
                    return true
                }
            }
        ) { }

        // 切换 UA 后必须带新 UA 重新取页才生效（document-start 脚本只对新的文档起作用）。
        // 不能只 reload 当前地址：部分站点识别出手机端后会重定向到手机专属域名（m.xxx.com），
        // 此时 reload 仍是手机版；同域未被重定向才 reload 保持当前流程位置。
        if (webView.url != null) {
            val currentHost = webView.url?.toUri()?.host?.lowercase()?.removePrefix("www.")
            val importHost = currentConfig.importUrl?.toUri()?.host?.lowercase()?.removePrefix("www.")
            if (!currentHost.isNullOrBlank() && !importHost.isNullOrBlank() && currentHost != importHost) {
                currentConfig.importUrl?.let { webView.loadUrl(it) }
            } else {
                webView.reload()
            }
        }
    }
    LaunchedEffect(currentConfig.importUrl) {
        val target = currentConfig.importUrl
        if (!target.isNullOrBlank() && target != "about:blank") {
            webView.loadUrl(if (target.startsWith("http")) target else "https://$target")
        }
    }
    DisposableEffect(webView) {
        onDispose {
            com.haooz.chedule.ui.web.WebViewRequestInterceptor.clearPostData()
            webView.stopLoading()
            webView.clearCache(true)
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
            // 不清 Cookie / localStorage：教务登录态跨页面保存，下次进入免登录；
            // 两者按域名隔离，不同学校互不影响。换账号请在教务页面内退出登录。
        }
    }

    return remember(webView) {
        object : WebHost {
            @Composable
            override fun Content(modifier: Modifier) {
                AndroidView(
                    modifier = modifier,
                    factory = { webView },
                    update = {},
                )
            }

            override fun reload() {
                webView.reload()
            }

            override fun canGoBack(): Boolean = webView.canGoBack()

            override fun goBack() {
                webView.goBack()
            }

            override fun currentHostOrNull(): String? =
                webView.url?.toUri()?.host?.lowercase()?.removePrefix("www.")

            override fun evaluateJavascript(js: String) {
                webView.evaluateJavascript(js, null)
            }

            override fun evaluateJavascriptWithResult(js: String, onResult: (String?) -> Unit) {
                // evaluateJavascript 只能在主线程调；AI 工具是从 io 协程里发起的
                webView.post {
                    runCatching {
                        webView.evaluateJavascript(js) { value -> onResult(value) }
                    }.onFailure { onResult(null) }
                }
            }

            override fun setImportTableId(tableId: String) {
                androidBridge.setImportTableId(tableId)
            }
        }
    }
}

/**
 * 从任意 Context 向外层找到宿主 Activity（`PixelCopy.request(window, …)` 需要 window）。
 *
 * ⚠ `ContextWrapper` 一支不能省：`LocalContext.current` 在 Dialog / ComposeView 里
 * 拿到的是包装 Context，少这支会一路返回 null → 顶栏采样静默失效（不崩、无日志）。
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
