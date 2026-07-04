/** WebDAV 同步设置页面 */
package com.haooz.chedule.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color as ComposeColor
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.SyncManager
import com.haooz.chedule.data.SyncResult
import com.haooz.chedule.data.WebDavManager
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

class WebDavSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyThemeAwareSystemBars()
        setContent {
            CourseScheduleTheme {
                WebDavSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun WebDavSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val webDavManager = remember { WebDavManager(context) }
    val repository = remember { CourseRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }

    var serverUrl by remember { mutableStateOf(webDavManager.serverUrl.ifBlank { "https://dav.jianguoyun.com/dav/" }) }
    var username by remember { mutableStateOf(webDavManager.username) }
    var password by remember { mutableStateOf(webDavManager.password) }
    var autoSync by remember { mutableStateOf(webDavManager.autoSyncEnabled) }
    var statusText by remember { mutableStateOf("") }

    val saveConfig = {
        webDavManager.serverUrl = serverUrl
        webDavManager.username = username
        webDavManager.password = password
        webDavManager.autoSyncEnabled = autoSync
    }

    LaunchedEffect(serverUrl, username, password, autoSync) {
        saveConfig()
    }
    var statusIsError by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }

    var lastSyncTimeMs by remember { mutableLongStateOf(webDavManager.lastSyncTime) }
    val lastSyncText = remember(lastSyncTimeMs) {
        if (lastSyncTimeMs > 0L) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            "上次同步: ${sdf.format(java.util.Date(lastSyncTimeMs))}"
        } else ""
    }
    val canTest = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    // 观察 SyncManager 状态，后台同步完成后刷新时间
    val syncManager = remember { SyncManager.getInstance(context) }
    val syncState by syncManager.syncState.collectAsState()
    LaunchedEffect(syncState) {
        if (syncState !is SyncManager.SyncState.Syncing) {
            lastSyncTimeMs = webDavManager.lastSyncTime
        }
    }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val blurAlpha = if (listScrollY < 50) 0f else ((listScrollY - 50) / 50f).coerceIn(0f, 0.7f)
    val topBarColorProgress = ((listScrollY - 50) / 50f).coerceIn(0f, 1f)
    val topBarColor = if (listScrollY < 50) {
        MiuixTheme.colorScheme.surface
    } else {
        val surface = MiuixTheme.colorScheme.surface
        val target = if (isDark) ComposeColor.Black.copy(alpha = 0.7f) else ComposeColor.White.copy(alpha = 0.7f)
        lerp(surface, target, topBarColorProgress)
    }
    val topAppBarColors = BlurDefaults.blurColors(
        blendColors = listOf(
            if (isDark) BlendColorEntry(ComposeColor.Black.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
            else BlendColorEntry(ComposeColor.White.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
        ),
        brightness = 0f,
        contrast = 1f,
        saturation = 1.2f
    )

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = if (blurAlpha > 0f) {
                    Modifier.textureBlur(backdrop = backdrop, shape = RectangleShape, colors = topAppBarColors)
                } else {
                    Modifier
                },
                color = topBarColor,
                title = "WebDAV 云同步",
                largeTitle = "WebDAV 云同步",
                scrollBehavior = scrollBehavior,
                navigationIconPadding = 20.dp,
                navigationIcon = {
                    IconButton(onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        saveConfig()
                        onBack()
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (!testing) {
                                testing = true
                                statusText = ""
                                connected = false
                                coroutineScope.launch {
                                    webDavManager.serverUrl = serverUrl
                                    webDavManager.username = username
                                    webDavManager.password = password
                                    val result = webDavManager.testConnection()
                                    if (result.isSuccess) {
                                        statusText = result.getOrThrow()
                                        statusIsError = false
                                        connected = true
                                    } else {
                                        statusText = result.exceptionOrNull()?.message ?: "连接失败"
                                        statusIsError = true
                                        connected = false
                                    }
                                    testing = false
                                }
                            }
                        },
                        enabled = canTest && !testing && !connected
                    ) {
                        Icon(
                            imageVector = if (connected) MiuixIcons.Ok else MiuixIcons.Play,
                            contentDescription = if (connected) "连接成功" else "测试连接",
                            modifier = Modifier.size(26.dp),
                            tint = if (connected) ComposeColor(0xFF4CAF50) else MiuixTheme.colorScheme.onSurface.copy(alpha = if (canTest) 1f else 0.3f)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            val listState = rememberLazyListState()
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemScrollOffset }
                    .collect { offset ->
                        listScrollY = offset
                    }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding() + 12.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 自动同步开关
                item {
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    autoSync = !autoSync
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "自动同步",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "课程变更时自动同步到云端",
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            }
                            top.yukonga.miuix.kmp.basic.Switch(
                                checked = autoSync,
                                onCheckedChange = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    autoSync = it
                                }
                            )
                        }
                    }
                }

                // 服务器配置
                item {
                    SmallTitle(
                        text = "服务器配置",
                        modifier = Modifier.offset(x = (-15).dp)
                    )
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 16.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 服务器地址
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "服务器地址",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                androidx.compose.foundation.text.BasicTextField(
                                    value = serverUrl,
                                    onValueChange = { serverUrl = it },
                                    modifier = Modifier.widthIn(max = 200.dp),
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        textAlign = TextAlign.End,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterEnd) {
                                            if (serverUrl.isEmpty()) {
                                                Text(
                                                    text = "https://...",
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }

                            // 用户名
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "用户名",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                androidx.compose.foundation.text.BasicTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    modifier = Modifier.widthIn(max = 200.dp),
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        textAlign = TextAlign.End,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterEnd) {
                                            if (username.isEmpty()) {
                                                Text(
                                                    text = "必填",
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }

                            // 密码
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "应用密码",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                androidx.compose.foundation.text.BasicTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    modifier = Modifier.widthIn(max = 200.dp),
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        textAlign = TextAlign.End,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterEnd) {
                                            if (password.isEmpty()) {
                                                Text(
                                                    text = "必填",
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // 状态信息
                item {
                    val isConfigured = webDavManager.isConfigured()
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = if (!isConfigured) "请先配置服务器信息"
                            else statusText.ifBlank { "暂无同步记录" },
                            fontSize = 14.sp,
                            color = if (statusText.isNotBlank() && statusIsError)
                                ComposeColor(0xFFF44336) else MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!isConfigured) "配置后即可使用云同步功能"
                            else lastSyncText.ifBlank { "从未同步" },
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }

                // 使用提示
                item {
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                    ) {
                        Column {
                            Text(
                                text = "默认 WebDAV 请按照以下步骤关联坚果云：",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "① 下载并安装坚果云客户端\n② 登录后前往「设置 → 第三方应用管理」获取应用密码\n③ 将应用密码填入上方「应用密码」",
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        }
                    }
                }
            }

            // 立即同步按钮 - 固定在底部
            TextButton(
                text = if (syncing) "同步中..." else "立即同步",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (!syncing) {
                        syncing = true
                        statusText = "正在同步..."
                        statusIsError = false
                        coroutineScope.launch {
                            webDavManager.serverUrl = serverUrl
                            webDavManager.username = username
                            webDavManager.password = password
                            syncManager.syncNow()
                            when (syncManager.syncState.value) {
                                is SyncManager.SyncState.Success -> {
                                    val s = syncManager.syncState.value as SyncManager.SyncState.Success
                                    statusText = "已同步: 上传${s.uploaded}条, 下载${s.downloaded}条"
                                    statusIsError = false
                                }
                                is SyncManager.SyncState.Error -> {
                                    statusText = (syncManager.syncState.value as SyncManager.SyncState.Error).message
                                    statusIsError = true
                                }
                                else -> {
                                    statusText = "同步完成"
                                    statusIsError = false
                                }
                            }
                            syncing = false
                            lastSyncTimeMs = webDavManager.lastSyncTime
                        }
                    }
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, bottom = 48.dp)
            )
        }
    }
}
