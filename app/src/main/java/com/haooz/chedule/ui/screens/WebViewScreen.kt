/** WebView 页面 - 用于加载教务系统网页 */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
import android.net.http.SslError
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.ui.web.AndroidBridge
import com.haooz.chedule.ui.web.WebCompatDelegate
import com.haooz.chedule.ui.web.WebPostBridge
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

@SuppressLint("JavascriptInterfaceRedundantCheck")
@Composable
fun WebViewScreen(
    school: SchoolData,
    adapterId: String,
    importUrl: String?,
    assetJsPath: String?,
    onBack: () -> Unit,
    onImportComplete: (List<Course>) -> Unit
) {
    val context = LocalContext.current
    var currentUrl by remember { mutableStateOf(importUrl ?: "about:blank") }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var pageTitle by remember { mutableStateOf("加载中...") }
    var isDesktopMode by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    val androidBridge = remember {
        AndroidBridge(
            context = context,
            webView = webView,
            onCourseImported = { courses -> onImportComplete(courses) },
            onTaskCompleted = {
                Toast.makeText(context, "导入完成", Toast.LENGTH_LONG).show()
                onBack()
            }
        )
    }

    LaunchedEffect(Unit) {
        webView.addJavascriptInterface(androidBridge, "AndroidBridge")
        webView.addJavascriptInterface(WebPostBridge(), "WebPostService")
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    LaunchedEffect(isDesktopMode) {
        val compatDelegate = WebCompatDelegate(webView)
        compatDelegate.enhanceSettings(isDesktopMode)
        webView.settings.userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT
        else WebSettings.getDefaultUserAgent(context)

        webView.webViewClient = compatDelegate.wrapWebViewClient(
            object : WebViewClient() {
                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.proceed()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        view.post {
                            Toast.makeText(view.context, "加载失败: ${error.description}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            isDesktopMode
        )

        webView.webChromeClient = compatDelegate.wrapWebChromeClient(
            object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loadingProgress = newProgress / 100f
                }
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (title != null) pageTitle = title
                }
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        Log.d("EduImport", "JS [${it.messageLevel()}]: ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                    }
                    return true
                }
            }
        ) { }
    }

    LaunchedEffect(currentUrl) {
        if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
            val url = if (currentUrl.startsWith("http")) currentUrl else "https://$currentUrl"
            webView.loadUrl(url)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
            WebStorage.getInstance().deleteAllData()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    val onExecuteImport: () -> Unit = {
        assetJsPath?.let { path ->
            val scriptFile = File(context.filesDir, "repo/schools/resources/${school.resourceFolder}/$path")
            if (scriptFile.exists()) {
                val jsCode = scriptFile.readText()
                webView.evaluateJavascript(jsCode, null)
                Toast.makeText(context, "正在执行导入脚本...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "脚本文件不存在: $path", Toast.LENGTH_LONG).show()
            }
        } ?: Toast.makeText(context, "无导入脚本", Toast.LENGTH_LONG).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                SmallTopAppBar(
                    title = pageTitle,
                    navigationIcon = {
                        IconButton(onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onBack()
                        }, modifier = Modifier.padding(start = 4.dp)) {
                            Icon(MiuixIcons.Close, contentDescription = "关闭", modifier = Modifier.size(22.dp))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            webView.reload()
                        }, modifier = Modifier.padding(end = 4.dp)) {
                            Icon(MiuixIcons.Refresh, contentDescription = "刷新", modifier = Modifier.size(27.dp))
                        }
                    }
                )
            }
        ) { paddingValues ->
            AndroidView(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                factory = { webView },
                update = {}
            )
        }

        AnimatedVisibility(
            visible = loadingProgress in 0.01f..0.99f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MiuixTheme.colorScheme.primary)
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .border(0.5.dp, MiuixTheme.colorScheme.outline, Capsule())
                .fillMaxWidth(),
            color = MiuixTheme.colorScheme.surfaceVariant,
            shape = Capsule()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 11.dp, end = 12.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = school.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedRectangle(4.dp),
                            color = if (isDesktopMode)
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else
                                androidx.compose.ui.graphics.Color(0xFF66BB6A).copy(alpha = 0.15f),
                            modifier = Modifier
                                .clip(RoundedRectangle(4.dp))
                                .clickable {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    isDesktopMode = !isDesktopMode
                                    webView.reload()
                                }
                        ) {
                            Text(
                                text = if (isDesktopMode) "桌面版" else "手机版",
                                fontSize = 12.sp,
                                color = if (isDesktopMode)
                                    MiuixTheme.colorScheme.primary
                                else
                                    androidx.compose.ui.graphics.Color(0xFF66BB6A),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(20.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "登录教务系统 → 进入课表页面 → 执行导入",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(44.dp),
                    shape = RoundedRectangle(22.dp),
                    color = if (assetJsPath != null)
                        MiuixTheme.colorScheme.primary
                    else
                        MiuixTheme.colorScheme.surfaceVariant
                ) {
                    IconButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onExecuteImport()
                        },
                        enabled = assetJsPath != null
                    ) {
                        Icon(
                            MiuixIcons.Normal.Download,
                            contentDescription = "执行导入",
                            modifier = Modifier.size(26.dp),
                            tint = if (assetJsPath != null)
                                MiuixTheme.colorScheme.onPrimary
                            else
                                MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }
        }
    }
}
