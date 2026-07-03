/** WebView 兼容性委托 - 处理不同 Android 版本的 WebView 兼容问题 */
package com.haooz.chedule.ui.web

import android.graphics.Bitmap
import android.webkit.*

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
    })();
""".trimIndent()

/**
 * WebView 兼容性配置
 * 管理 WebView 设置、Cookie、桌面模式等
 */
class WebCompatDelegate(private val webView: WebView) {

    /**
     * 增强 WebView 基础配置
     */
    fun enhanceSettings(isDesktopMode: Boolean): WebCompatDelegate {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowUniversalAccessFromFileURLs = true
            allowFileAccessFromFileURLs = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

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
        return object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request != null) {
                    val interceptedResponse = interceptor.intercept(request, isDesktopMode)
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
                    if (isDesktopMode) {
                        injectDesktopViewport(wv)
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
     * 注入桌面模式 viewport，强制 16:9 布局触发网站桌面版显示
     */
    private fun injectDesktopViewport(view: WebView) {
        view.evaluateJavascript(
            """
            (function() {
                var metas = document.getElementsByTagName('meta');
                for (var i = metas.length - 1; i >= 0; i--) {
                    if (metas[i].getAttribute('name') === 'viewport') metas[i].parentNode.removeChild(metas[i]);
                }
                var meta = document.createElement('meta');
                meta.name = "viewport";
                meta.content = "width=1280, initial-scale=0.28, minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes";
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
