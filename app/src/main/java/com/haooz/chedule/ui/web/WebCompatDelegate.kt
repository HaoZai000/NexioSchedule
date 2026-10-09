/** WebView 兼容性委托 - 处理不同 Android 版本的 WebView 兼容问题 */
package com.haooz.chedule.ui.web

import android.graphics.Bitmap
import android.os.Build
import com.haooz.chedule.data.NexioLog
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

private const val TAG = "WebCompatDelegate"

/** 桌面模式下使用的页面布局宽度（CSS px），见 desktopViewportContent() 的说明 */
private const val DESKTOP_LAYOUT_WIDTH = 1280

/**
 * 桌面模式视口覆盖脚本（document-start 注入，先于页面自身脚本执行）。
 *
 * 为什么非得这么早：本校门户在 `$(document).ready` 里就把 `body` 宽度量下来、
 * 把像素尺寸写进登录弹窗的行内样式（qsflat 全站没有任何 resize 监听），
 * 等 onPageFinished 再改 viewport 已经无从纠正。
 */
private fun buildDesktopViewportScript(content: String): String = """
    (function () {
        var CONTENT = '$content';
        var FLAG = 'data-nexio-desktop-viewport';
        function apply() {
            if (!document.head) return false;
            // 页面自带的 viewport meta 一律清掉，只留我们这一条
            var metas = document.head.querySelectorAll('meta[name="viewport"]');
            for (var i = metas.length - 1; i >= 0; i--) {
                if (!metas[i].hasAttribute(FLAG)) metas[i].parentNode.removeChild(metas[i]);
            }
            var mine = document.head.querySelector('meta[' + FLAG + ']');
            if (!mine) {
                mine = document.createElement('meta');
                mine.setAttribute('name', 'viewport');
                mine.setAttribute(FLAG, '1');
                document.head.appendChild(mine);
            }
            if (mine.getAttribute('content') !== CONTENT) mine.setAttribute('content', CONTENT);
            return true;
        }
        function boot() {
            // 文档开始时 head 还不存在，等它出现
            if (!document.head) { setTimeout(boot, 0); return; }
            apply();
            document.addEventListener('DOMContentLoaded', apply);
            document.addEventListener('load', apply);
            // 站点自己的 viewport meta 可能比 boot 更晚才被解析到，出现即清除
            new MutationObserver(function () {
                if (document.head.querySelector('meta[name="viewport"]:not([' + FLAG + '])')) apply();
            }).observe(document.head, { childList: true });
        }
        boot();
    })();
""".trimIndent()

/**
 * 注入到 WebView 的 Promise 桥接基础设施
 * 脚本通过 window.AndroidBridgePromise 调用 Native 方法并等待 Promise 回调
 */
val JS_PROMISE_BRIDGE = """
    (function() {
        if (window._resolveAndroidPromise) return;
        window._androidPromiseResolvers = {};
        window._androidPromiseRejectors = {};

        window._resolveAndroidPromise = function(promiseId, result) {
            if (window._androidPromiseResolvers[promiseId]) {
                window._androidPromiseResolvers[promiseId](result);
                delete window._androidPromiseResolvers[promiseId];
                delete window._androidPromiseRejectors[promiseId];
            }
        };

        window._rejectAndroidPromise = function(promiseId, error) {
            if (window._androidPromiseRejectors[promiseId]) {
                window._androidPromiseRejectors[promiseId](new Error(error));
                delete window._androidPromiseResolvers[promiseId];
                delete window._androidPromiseRejectors[promiseId];
            }
        };

        window.AndroidBridgePromise = {
            showAlert: function(title, content, confirmText) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'alert_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.showAlert(title, content, confirmText, promiseId);
                });
            },
            showPrompt: function(title, tip, defaultText, validatorJsFunction) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'prompt_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.showPrompt(title, tip, defaultText, validatorJsFunction, promiseId);
                });
            },
            showSingleSelection: function(title, itemsJsonString, defaultSelectedIndex) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'singleSelect_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.showSingleSelection(title, itemsJsonString, defaultSelectedIndex, promiseId);
                });
            },
            saveImportedCourses: function(coursesJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveCourses_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.saveImportedCourses(coursesJsonString, promiseId);
                });
            },
            saveCourseConfig: function(configJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveConfig_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.saveCourseConfig(configJsonString, promiseId);
                });
            },
            savePresetTimeSlots: function(timeSlotsJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveTimeSlots_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.savePresetTimeSlots(timeSlotsJsonString, promiseId);
                });
            }
        };

        // V2 桥接对象别名（shiguang Bridge）：业务接口完全不变，仅对象改名
        // window.shiguangBridge          -> AndroidBridge（同步：showToast、notifyTaskCompletion 等）
        // window.shiguangBridgePromise   -> AndroidBridgePromise（异步：showAlert、saveImportedCourses 等）
        window.shiguangBridge = window.shiguangBridge || window.AndroidBridge;
        window.shiguangBridgePromise = window.shiguangBridgePromise || window.AndroidBridgePromise;
    })();
""".trimIndent()

/**
 * WebView 兼容性配置
 * 管理 WebView 设置、Cookie、桌面模式等
 */
class WebCompatDelegate(private val webView: WebView) {

    private var viewportScriptHandler: ScriptHandler? = null

    /** document-start 视口覆盖是否已生效；为 false 时由 onPageFinished 兜底注入 */
    var desktopViewportOverrideActive = false
        private set

    /**
     * 桌面版视口自愈是否已重载过。
     *
     * 首次加载 Chrome 只落实布局宽度、不重算缩放（vvScale 仍 1.0），重载一次才生效 ——
     * 这也是「切一次手机再切回电脑就好了」的真实原因。只重载一次，避免和页面跳转打架。
     */
    private var viewportRetried = false

    /**
     * 启用/关闭桌面模式视口覆盖。
     *
     * 返回是否成功走 document-start 注入；返回 false 时页面脚本已经跑过，
     * onPageFinished 兜底只能纠正页面自身布局，站点脚本里写死的像素尺寸无力回天。
     */
    fun applyDesktopViewportOverride(enabled: Boolean): Boolean {
        viewportScriptHandler?.remove()
        viewportScriptHandler = null
        desktopViewportOverrideActive = false
        // 模式切换 = 新一轮协商，允许再自愈一次
        viewportRetried = false
        if (!enabled) return false
        // 桌面版不用 document-start 提前注入，只保留 onPageFinished 兜底注入。
        // 提前注入会让 Chrome 只认width、不认initial-scale（inner=1283 但缩放 100%），
        // 页面放大到只看得见 28%；晚注入则两者一起生效（缩放 0.28，正好一屏）。
        // 这里 return false 同时让 onPageFinished 的兜底注入保持开启 —— 别改回去。
        NexioLog.d(TAG, "applyDesktopViewportOverride: 桌面版仅用onPageFinished 兜底注入")
        return false
    }

    /**
     * 桌面视口 content。
     *
     * 布局宽度取 max(1280, 屏幕宽度)：门户 `.main-content` 上限 1600、登录页 `#login`
     * 固定 1000 宽（表单右边界约 883），1280 下两者都完整可见。
     *
     * initial-scale 必须取 `屏幕宽度 / 布局宽度`，两个参数互相自洽；
     * 若写成 width=1280 + 固定 initial-scale=0.28，Chrome 会把布局宽度撑成
     * max(1280, 屏幕宽度/0.28)≈1403，整页多出一截且缩得过小。
     */
    private fun desktopViewportContent(): String {
        val cssWidth = viewportWidthInCssPx()
        val layoutWidth = maxOf(DESKTOP_LAYOUT_WIDTH, cssWidth)
        val scale = cssWidth.toDouble() / layoutWidth
        // 必须锁 Locale：默认区域（如德语）会把小数点写成逗号，CSS 直接失效
        return "width=$layoutWidth, initial-scale=" + "%.4f".format(java.util.Locale.US, scale)
    }

    /** WebView 的 CSS 像素宽度：优先实际布局宽度（分屏/自由窗口下才正确），未布局时退回屏幕宽度 */
    private fun viewportWidthInCssPx(): Int {
        val density = webView.resources.displayMetrics.density
        if (density > 0f && webView.width > 0) {
            val cssWidth = (webView.width / density).toInt()
            if (cssWidth > 0) return cssWidth
        }
        return webView.context.resources.configuration.screenWidthDp
    }

    /**
     * 增强 WebView 基础配置
     */
    fun enhanceSettings(isDesktopMode: Boolean): WebCompatDelegate {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // 下面三项在 API 33 起被废弃且不再生效（本地文件访问已被安全策略收紧），
            // 仅在旧版本上还有意义，所以按版本设置而不是无条件打开
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                databaseEnabled = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
            }

            // 两项都保持 true（Chromium「like a browser」配置）：useWideViewPort 让注入的
            // width=1280 生效，loadWithOverviewMode 负责「内容宽于屏幕就缩到一屏」。
            // 别改成 false —— 实测关掉后页面按 100% 渲染 1283 宽布局，手机屏只看得到28%。
            useWideViewPort = true
            loadWithOverviewMode = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        return this
    }

    /**
     * 包装 WebViewClient，统一控制 JS 注入流程
     */
    fun wrapWebViewClient(original: WebViewClient, isDesktopMode: Boolean): WebViewClient {
        val interceptor = WebViewRequestInterceptor()
        // 在 UI 线程读取当前用户代理并缓存：shouldInterceptRequest 在后台线程执行，
        // 后台线程访问 WebView settings 会抛异常，故不能在那里获取 UA
        val currentUserAgent = webView.settings.userAgentString
        return object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request != null) {
                    val interceptedResponse = interceptor.intercept(request, currentUserAgent)
                    if (interceptedResponse != null) {
                        return interceptedResponse
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                original.onPageStarted(view, url, favicon)
                view?.evaluateJavascript(JS_INTERCEPT_POST, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                original.onPageFinished(view, url)
                view?.let { wv ->
                    wv.evaluateJavascript(JS_INTERCEPT_POST, null)
                    wv.evaluateJavascript(JS_PROMISE_BRIDGE, null)
                    // 正常情况下视口已由 document-start 脚本注入；只有老 WebView 走到这里兜底
                    if (isDesktopMode && !desktopViewportOverrideActive) {
                        injectDesktopViewport(wv)
                    }
                    // 视口自愈：首次加载Chrome 只落实布局宽度、不重算缩放，
                    // 重载一次才生效。inner（布局宽）与 vvScale（视觉缩放）必须都看 ——
                    // 只看 inner 会把「排版对了但没缩放」误判成已修好。
                    if (isDesktopMode) {
                        wv.evaluateJavascript(
                            "(function(){var v=window.visualViewport?window.visualViewport.scale:-1;" +
                                "return window.innerWidth+'|'+v})()"
                        ) { r ->
                            // 回调收到的是 JSON 编码结果，JS 返回字符串时带引号，
                            // 所以用正则抠数字，别直接 split+toInt（会全部解析失败）。
                            val raw = r ?: return@evaluateJavascript
                            val nums = Regex("-?\\d+(?:\\.\\d+)?")
                                .findAll(raw)
                                .map { it.value }
                                .toList()
                            val actual = nums.getOrNull(0)?.toIntOrNull() ?: return@evaluateJavascript
                            val vvScale = nums.getOrNull(1)?.toDoubleOrNull() ?: -1.0
                            NexioLog.d(TAG, "viewport: inner=$actual vvScale=$vvScale retried=$viewportRetried")
                            val contentWider = actual > viewportWidthInCssPx() + 1
                            val zoomOk = vvScale > 0.05 && vvScale < 0.95
                            if (contentWider && !zoomOk && !viewportRetried) {
                                viewportRetried = true
                                wv.post { wv.reload() }
                            }
                        }
                    }
                }
            }

            override fun onReceivedSslError(v: WebView, h: SslErrorHandler, e: android.net.http.SslError) =
                original.onReceivedSslError(v, h, e)

            override fun onReceivedError(v: WebView, q: WebResourceRequest, e: WebResourceError) =
                original.onReceivedError(v, q, e)
        }
    }

    /**
     * onPageFinished 兜底注入桌面模式 viewport（仅老 WebView 用）。
     * 此时页面脚本已执行完毕，只能保证页面自身布局正确，站点脚本里写死的像素尺寸纠正不了。
     */
    private fun injectDesktopViewport(view: WebView) {
        val content = desktopViewportContent()
        view.evaluateJavascript(
            """
            (function() {
                var metas = document.getElementsByTagName('meta');
                for (var i = metas.length - 1; i >= 0; i--) {
                    if (metas[i].getAttribute('name') === 'viewport') metas[i].parentNode.removeChild(metas[i]);
                }
                var meta = document.createElement('meta');
                meta.name = "viewport";
                meta.content = "$content";
                document.head.appendChild(meta);
            })();
            """.trimIndent(),
            null
        )
    }

    fun wrapWebChromeClient(original: WebChromeClient, onProgress: (Int) -> Unit): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgress(newProgress)
                original.onProgressChanged(view, newProgress)
            }

            override fun onReceivedTitle(v: WebView?, t: String?) = original.onReceivedTitle(v, t)
        }
    }
}
