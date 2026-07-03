/** WebView 请求拦截器 - 拦截和处理 WebView 网络请求 */
package com.haooz.chedule.ui.web

import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * WebView 请求拦截器
 * 在桌面模式下拦截 POST 请求，使用 OkHttp 转发
 */
class WebViewRequestInterceptor {

    companion object {
        private val clientWithRedirects = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        private val clientNoRedirects = clientWithRedirects.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        private val postBodyRegistry = java.util.Collections.synchronizedMap(
            mutableMapOf<String, RegisteredPostData>()
        )

        fun registerPostData(id: String, body: String, contentType: String) {
            postBodyRegistry[id] = RegisteredPostData(body, contentType)
        }

        private data class RegisteredPostData(val body: String, val contentType: String)
    }

    private val cookieManager = CookieManager.getInstance()

    /**
     * 拦截 WebView 请求
     * @param isDesktopMode 仅在电脑模式开启时执行拦截
     */
    fun intercept(request: WebResourceRequest, isDesktopMode: Boolean): WebResourceResponse? {
        val rawUrl = request.url.toString()

        if (!rawUrl.startsWith("http")) return null
        if (!isDesktopMode) return null

        // 检查注册的 POST Body ID
        val requestIdHeader = request.requestHeaders["X-WebView-Post-Id"]
        val requestIdParam = request.url.getQueryParameter("_webview_post_id")
        val requestId = requestIdHeader ?: requestIdParam

        // URL 去污染逻辑
        val url = if (requestIdParam != null) {
            val uriBuilder = request.url.buildUpon().clearQuery()
            request.url.queryParameterNames.forEach { name ->
                if (name != "_webview_post_id") {
                    request.url.getQueryParameters(name).forEach { value ->
                        uriBuilder.appendQueryParameter(name, value)
                    }
                }
            }
            uriBuilder.build().toString()
        } else {
            rawUrl
        }

        val registeredData = requestId?.let { postBodyRegistry.remove(it) }

        if (request.method.uppercase() != "GET" && registeredData == null) {
            return null
        }

        val client = if (request.isForMainFrame) clientNoRedirects else clientWithRedirects

        try {
            val builder = Request.Builder().url(url)

            if (registeredData != null) {
                val mediaType = registeredData.contentType
                    .ifBlank { "application/x-www-form-urlencoded" }
                    .toMediaTypeOrNull()
                val body = registeredData.body.toRequestBody(mediaType)
                builder.method(request.method, body)
            } else {
                builder.method(request.method, null)
            }

            // 复制头部，剔除特殊头部
            request.requestHeaders.forEach { (key, value) ->
                if (!key.equals("X-Requested-With", ignoreCase = true) &&
                    !key.equals("X-WebView-Post-Id", ignoreCase = true)
                ) {
                    builder.addHeader(key, value)
                }
            }

            // 同步 Cookie
            val cookies = cookieManager.getCookie(url)
            if (!cookies.isNullOrEmpty()) {
                builder.addHeader("Cookie", cookies)
            }

            val response = client.newCall(builder.build()).execute()

            // 同步 Set-Cookie 回 WebView
            response.headers.values("Set-Cookie").forEach {
                cookieManager.setCookie(url, it)
            }
            cookieManager.flush()

            // 处理重定向
            if (response.code in 300..399) {
                if (request.isForMainFrame) {
                    val location = response.header("Location")
                    if (location != null) {
                        val absoluteLocation = response.request.url.resolve(location)?.toString() ?: location
                        val html = "<html><script>window.location.replace('$absoluteLocation');</script></html>"
                        response.close()
                        return WebResourceResponse(
                            "text/html",
                            "UTF-8",
                            200,
                            "OK",
                            mapOf("Cache-Control" to "no-cache"),
                            html.byteInputStream()
                        )
                    }
                }
                response.body?.close()
                response.close()
                return null
            }

            // 构造 WebResourceResponse
            val contentType = response.header("Content-Type")
            val mimeType = contentType?.substringBefore(";") ?: "text/html"
            val encoding = contentType?.substringAfter("charset=", "UTF-8") ?: "UTF-8"

            val responseHeadersMap = mutableMapOf<String, String>()
            for (i in 0 until response.headers.size) {
                val name = response.headers.name(i)
                if (!name.equals("Content-Encoding", ignoreCase = true)) {
                    responseHeadersMap[name] = response.headers.value(i)
                }
            }

            val body = response.body ?: run {
                response.close()
                return null
            }

            return WebResourceResponse(
                mimeType,
                encoding,
                response.code,
                response.message.ifBlank { "OK" },
                responseHeadersMap,
                body.byteStream()
            )
        } catch (e: Exception) {
            Log.e("WebViewInterceptor", "Error intercepting request: $url", e)
            return null
        }
    }
}

/**
 * WebPostBridge - 用于网络拦截的内部桥接
 */
class WebPostBridge {
    @JavascriptInterface
    fun register(id: String, body: String, type: String) {
        WebViewRequestInterceptor.registerPostData(id, body, type)
    }
}
