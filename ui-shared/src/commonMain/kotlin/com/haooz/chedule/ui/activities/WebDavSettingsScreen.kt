/** WebDAV 备份/恢复设置页面 - Screen */
package com.haooz.chedule.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.backup.WebDavManager
import com.haooz.chedule.data.util.formatDateTimeMinute
import com.haooz.chedule.data.util.localDateTimeAt
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.MiscStr
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.NativeTextField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun WebDavSettingsScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    onConnectedChange: (Boolean) -> Unit = {},
    onTestConnectionReady: (() -> Unit) -> Unit = {},
    onHolidayDataChanged: () -> Unit = {},
) {
    val webDavManager = remember { WebDavManager { onHolidayDataChanged() } }
    val coroutineScope = rememberCoroutineScope()
    var listScrollY by remember { mutableIntStateOf(0) }

    var serverUrl by remember { mutableStateOf(webDavManager.serverUrl.ifBlank { "https://dav.jianguoyun.com/dav/" }) }
    // 远端备份目录：可任意层级/任意名字；留空 = 直接放服务器根目录
    var remotePath by remember { mutableStateOf(webDavManager.remotePath) }
    var username by remember { mutableStateOf(webDavManager.username) }
    var password by remember { mutableStateOf(webDavManager.password) }
    var statusText by remember { mutableStateOf("") }

    val saveConfig = {
        webDavManager.serverUrl = serverUrl
        webDavManager.remotePath = remotePath
        webDavManager.username = username
        webDavManager.password = password
    }

    LaunchedEffect(serverUrl, remotePath, username, password) {
        saveConfig()
    }
    var statusIsError by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }

    LaunchedEffect(connected) {
        onConnectedChange(connected)
    }

    val lastSyncTimeMs = webDavManager.lastSyncTime
    // 2026-10-11：原实现是**全限定名**的 java.text.SimpleDateFormat + java.util.Date ——
    // checkKmpPurity 的 `import java.*` 规则抓不到这种写法，于是这行在 commonMain 里
    // 一路绿灯（android/jvm 目标都能编过），真编 iOS 时才炸。换 :core 的手写格式化。
    val lastSyncText = remember(lastSyncTimeMs, AppLanguage.resolved) {
        if (lastSyncTimeMs > 0L) {
            MiscStr.lastOperationN(localDateTimeAt(lastSyncTimeMs).formatDateTimeMinute())
        } else ""
    }

    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    val doTestConnection = {
        if (!testing) {
            testing = true
            statusText = ""
            connected = false
            coroutineScope.launch {
                webDavManager.serverUrl = serverUrl
                webDavManager.remotePath = remotePath
                webDavManager.username = username
                webDavManager.password = password
                val result = webDavManager.testConnection()
                if (result.isSuccess) {
                    statusText = result.getOrThrow()
                    statusIsError = false
                    connected = true
                } else {
                    statusText = result.exceptionOrNull()?.message ?: MiscStr.connectionFailed()
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
        topBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                    .collapsibleTopInset(scrollBehavior)
                    .then(
                        scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                    ),
                contentPadding = PaddingValues(
                    start = tabletHorizontalPadding,
                    end = tabletHorizontalPadding,
                    top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight +
                        pageTopExtra(isTablet, hasSmallTitle = true),
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 服务器配置
                item {
                    SmallTitle(
                        text = MiscStr.serverConfigSection(),
                    )
                    Card(
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
                                    text = MiscStr.serverAddress(),
                                    modifier = Modifier.weight(1f),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                NativeTextField(
                                    value = serverUrl,
                                    onValueChange = { serverUrl = it },
                                    modifier = Modifier.fillMaxWidth(0.65f),
                                    hint = "https://...",
                                    singleLine = true,
                                    textAlign = TextAlign.End,
                                    textStyle = TextStyle(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = MiscStr.backupPath(),
                                    modifier = Modifier.weight(1f),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                NativeTextField(
                                    value = remotePath,
                                    onValueChange = { remotePath = it },
                                    modifier = Modifier.fillMaxWidth(0.65f),
                                    hint = MiscStr.hintLeaveEmptyForRoot(),
                                    singleLine = true,
                                    textAlign = TextAlign.End,
                                    textStyle = TextStyle(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = MiscStr.accountLabel(),
                                    modifier = Modifier.weight(1f),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                NativeTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    modifier = Modifier.fillMaxWidth(0.65f),
                                    hint = MiscStr.requiredHint(),
                                    singleLine = true,
                                    textAlign = TextAlign.End,
                                    textStyle = TextStyle(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = MiscStr.appPasswordLabel(),
                                    modifier = Modifier.weight(1f),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                NativeTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    modifier = Modifier.fillMaxWidth(0.65f),
                                    hint = MiscStr.requiredHint(),
                                    singleLine = true,
                                    textAlign = TextAlign.End,
                                    textStyle = TextStyle(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }

                // 状态信息
                item {
                    val isConfigured = webDavManager.isConfigured()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = if (!isConfigured) MiscStr.configureServerFirst()
                            else statusText.ifBlank { MiscStr.noOperationRecord() },
                            fontSize = 14.sp,
                            color = if (statusText.isNotBlank() && statusIsError)
                                ComposeColor(0xFFF44336) else MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!isConfigured) MiscStr.cloudBackupAfterConfig()
                            else lastSyncText.ifBlank { MiscStr.neverOperated() },
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }

                // 使用提示
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                    ) {
                        Column {
                            Text(
                                text = MiscStr.nutstoreStepsTitle(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = MiscStr.nutstoreStepsDetail(),
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
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                    ) {
                        Column {
                            Text(
                                text = MiscStr.backupRestoreNotesTitle(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = MiscStr.backupRestoreNotesDetail(),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        }
                    }
                }
            }

        }
    }
}
