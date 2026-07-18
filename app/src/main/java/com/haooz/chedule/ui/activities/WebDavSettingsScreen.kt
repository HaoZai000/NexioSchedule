/** WebDAV 备份/恢复设置页面 - Screen */
package com.haooz.chedule.ui.activities

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.SyncManager
import com.haooz.chedule.data.WebDavManager
import com.haooz.chedule.ui.utils.rememberAppStyle
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
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.Color as ComposeColor

@Composable
fun WebDavSettingsScreen(onBack: () -> Unit, onConnectedChange: (Boolean) -> Unit = {}, onTestConnectionReady: (() -> Unit) -> Unit = {}) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val webDavManager = remember { WebDavManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }

    var serverUrl by remember { mutableStateOf(webDavManager.serverUrl.ifBlank { "https://dav.jianguoyun.com/dav/" }) }
    var username by remember { mutableStateOf(webDavManager.username) }
    var password by remember { mutableStateOf(webDavManager.password) }
    var statusText by remember { mutableStateOf("") }

    val saveConfig = {
        webDavManager.serverUrl = serverUrl
        webDavManager.username = username
        webDavManager.password = password
    }

    LaunchedEffect(serverUrl, username, password) {
        saveConfig()
    }
    var statusIsError by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var backingUp by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }

    LaunchedEffect(connected) {
        onConnectedChange(connected)
    }

    var lastSyncTimeMs by remember { mutableLongStateOf(webDavManager.lastSyncTime) }
    val lastSyncText = remember(lastSyncTimeMs) {
        if (lastSyncTimeMs > 0L) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            "上次操作: ${sdf.format(java.util.Date(lastSyncTimeMs))}"
        } else ""
    }
    val canTest = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    val syncManager = remember { SyncManager.getInstance(context) }
    val syncState by syncManager.syncState.collectAsState()
    LaunchedEffect(syncState) {
        when (val state = syncState) {
            is SyncManager.SyncOperationState.BackupSuccess -> {
                statusText = "备份成功: ${state.backupId}"
                statusIsError = false
                backingUp = false
                lastSyncTimeMs = webDavManager.lastSyncTime
            }
            is SyncManager.SyncOperationState.RestoreSuccess -> {
                statusText = "恢复成功: ${state.backupTime}"
                statusIsError = false
                restoring = false
                lastSyncTimeMs = webDavManager.lastSyncTime
            }
            is SyncManager.SyncOperationState.Error -> {
                statusText = state.message
                statusIsError = true
                backingUp = false
                restoring = false
            }
            is SyncManager.SyncOperationState.Running -> {
                statusText = if (backingUp) "正在备份..." else if (restoring) "正在恢复..." else "处理中..."
                statusIsError = false
            }
            is SyncManager.SyncOperationState.Idle -> {}
        }
    }

    val backdropColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backdropColor)
        drawContent()
    }
    val appStyleValue = rememberAppStyle()
    val isLiquidGlass = appStyleValue == "liquidglass"

    val doTestConnection = {
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
    }
    LaunchedEffect(Unit) {
        onTestConnectionReady(doTestConnection)
    }

    Scaffold(
        topBar = {
            if (!isLiquidGlass) {
                TopAppBar(
                    title = "WebDAV 云备份",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(
                            onClick = { onBack()
                                saveConfig()},
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
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
        }
    ) { paddingValues ->
        Box(
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
                modifier = Modifier.fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .then(
                        if (!isLiquidGlass) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
                    ),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (isLiquidGlass) paddingValues.calculateTopPadding() + 64.dp else paddingValues.calculateTopPadding(),
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                                    modifier = Modifier.fillMaxWidth(0.65f),
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
                                    modifier = Modifier.fillMaxWidth(0.65f),
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
                                    modifier = Modifier.fillMaxWidth(0.65f),
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
                            else statusText.ifBlank { "暂无操作记录" },
                            fontSize = 14.sp,
                            color = if (statusText.isNotBlank() && statusIsError)
                                ComposeColor(0xFFF44336) else MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!isConfigured) "配置后即可使用云备份功能"
                            else lastSyncText.ifBlank { "从未操作" },
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
                                text = "关联坚果云步骤：",
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

                // 备份与恢复说明
                item {
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                    ) {
                        Column {
                            Text(
                                text = "备份与恢复说明",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "备份到云端：将全部课表数据上传为带时间戳的文件，每次备份独立保存，不覆盖历史备份。\n\n从云端恢复：下载最新的备份文件覆盖本地数据。恢复前请先备份当前数据。",
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        }
                    }
                }
            }

            // 底部渐变遮罩
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to ComposeColor.Transparent,
                                0.15f to backdropColor.copy(alpha = 0.5f),
                                0.5f to backdropColor.copy(alpha = 0.85f),
                                1.0f to backdropColor
                            )
                        )
                    )
            )

            // 底部两个按钮：备份 + 恢复
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 备份按钮
                TextButton(
                    text = if (backingUp) "备份中..." else "备份到云端",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (!backingUp && !restoring) {
                            backingUp = true
                            statusText = "正在备份..."
                            statusIsError = false
                            coroutineScope.launch {
                                webDavManager.serverUrl = serverUrl
                                webDavManager.username = username
                                webDavManager.password = password
                                syncManager.backupNow()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )

                // 恢复按钮
                TextButton(
                    text = if (restoring) "恢复中..." else "从云端恢复",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (!backingUp && !restoring) {
                            restoring = true
                            statusText = "正在恢复..."
                            statusIsError = false
                            coroutineScope.launch {
                                webDavManager.serverUrl = serverUrl
                                webDavManager.username = username
                                webDavManager.password = password
                                syncManager.restoreNow()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
