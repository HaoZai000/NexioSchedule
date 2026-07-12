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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.ui.web.AndroidBridge
import com.haooz.chedule.ui.web.WebCompatDelegate
import com.haooz.chedule.ui.web.WebPostBridge
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private data class AlertData(
    val title: String,
    val content: String,
    val confirmText: String,
    val onResult: (Boolean) -> Unit
)

private data class PromptData(
    val title: String,
    val tip: String,
    val defaultText: String,
    val onResult: (String?) -> Unit
)

private data class SelectionData(
    val title: String,
    val items: List<String>,
    val defaultIndex: Int,
    val onResult: (Int?) -> Unit
)

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

    var alertData by remember { mutableStateOf<AlertData?>(null) }
    var promptData by remember { mutableStateOf<PromptData?>(null) }
    var selectionData by remember { mutableStateOf<SelectionData?>(null) }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    var importCompleted by remember { mutableStateOf(false) }

    val currentOnImportComplete by rememberUpdatedState(onImportComplete)
    val currentOnBack by rememberUpdatedState(onBack)

    val showAlertCallback by rememberUpdatedState(
        { title: String, content: String, confirmText: String, onResult: (Boolean) -> Unit ->
            alertData = AlertData(title, content, confirmText, onResult)
        }
    )
    val showPromptCallback by rememberUpdatedState(
        { title: String, tip: String, defaultText: String, validatorJs: String, onResult: (String?) -> Unit ->
            promptData = PromptData(title, tip, defaultText, onResult)
        }
    )
    val showSelectionCallback by rememberUpdatedState(
        { title: String, items: List<String>, defaultIndex: Int, onResult: (Int?) -> Unit ->
            selectionData = SelectionData(title, items, defaultIndex, onResult)
        }
    )

    val androidBridge = remember {
        AndroidBridge(
            context = context,
            webView = webView,
            onCourseImported = { courses ->
                importCompleted = true
                currentOnImportComplete(courses)
            },
            onTaskCompleted = {
                if (!importCompleted) {
                    Toast.makeText(context, "导入完成", Toast.LENGTH_LONG).show()
                }
                currentOnBack()
            },
            onShowAlert = { title, content, confirmText, onResult ->
                showAlertCallback(title, content, confirmText, onResult)
            },
            onShowPrompt = { title, tip, defaultText, validatorJs, onResult ->
                showPromptCallback(title, tip, defaultText, validatorJs, onResult)
            },
            onShowSingleSelection = { title, items, defaultIndex, onResult ->
                showSelectionCallback(title, items, defaultIndex, onResult)
            }
        )
    }

    // Alert 对话框
    alertData?.let { data ->
        WebAlertDialog(
            title = data.title,
            content = data.content,
            confirmText = data.confirmText,
            onConfirm = {
                data.onResult(true)
                alertData = null
            },
            onDismiss = {
                data.onResult(false)
                alertData = null
            }
        )
    }

    // Prompt 对话框
    promptData?.let { data ->
        WebPromptDialog(
            title = data.title,
            tip = data.tip,
            defaultText = data.defaultText,
            onConfirm = { input ->
                data.onResult(input)
                promptData = null
            },
            onDismiss = {
                data.onResult(null)
                promptData = null
            }
        )
    }

    // 单选列表对话框
    selectionData?.let { data ->
        WebSelectionDialog(
            title = data.title,
            items = data.items,
            defaultIndex = data.defaultIndex,
            onSelect = { index ->
                data.onResult(index)
                selectionData = null
            },
            onDismiss = {
                data.onResult(null)
                selectionData = null
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
                            Icon(MiuixIcons.Close, contentDescription = "关闭", modifier = Modifier.size(23.dp))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            webView.reload()
                        }, modifier = Modifier.padding(end = 4.dp)) {
                            Icon(MiuixIcons.Refresh, contentDescription = "刷新", modifier = Modifier.size(26.dp))
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

@Composable
private fun WebAlertDialog(
    title: String,
    content: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedRectangle(28.dp),
            color = MiuixTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = content,
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        text = confirmText,
                        onClick = onConfirm,
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}

@Composable
private fun WebPromptDialog(
    title: String,
    tip: String,
    defaultText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(defaultText)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedRectangle(28.dp),
            color = MiuixTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = tip,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedRectangle(12.dp),
                    color = MiuixTheme.colorScheme.surfaceVariant
                ) {
                    TextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onConfirm(textFieldValue.text) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

@Composable
private fun WebSelectionDialog(
    title: String,
    items: List<String>,
    defaultIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(defaultIndex) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedRectangle(28.dp),
            color = MiuixTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 400.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(items) { index, item ->
                        val isSelected = index == selectedIndex
                        Surface(
                            shape = RoundedRectangle(12.dp),
                            color = if (isSelected)
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else
                                androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedRectangle(12.dp))
                                .clickable {
                                    selectedIndex = index
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = item,
                                fontSize = 16.sp,
                                color = if (isSelected)
                                    MiuixTheme.colorScheme.primary
                                else
                                    MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                        if (index < items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onSelect(selectedIndex) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}
