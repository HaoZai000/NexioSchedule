/** 应用更新设置页面 - Screen */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.getStringOrNull
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import com.haooz.chedule.ui.utils.UpdateChecker
import com.haooz.chedule.ui.utils.UpdateInstaller
import com.haooz.chedule.ui.utils.isAppDarkTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.io.File
import androidx.compose.ui.graphics.Color as ComposeColor

private fun checkForUpdate(
    context: Context,
    source: String = "gitee",
    channel: String = "stable"
): Pair<Boolean, UpdateChecker.GiteeRelease?> = UpdateChecker.checkForUpdate(context, source, channel)

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun UpdateSettingsScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var listScrollY by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { AppStorage.store("update_settings") }
    // 液态玻璃效果的透明下拉颜色
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )

    var autoCheckUpdate by remember { mutableStateOf(prefs.getBoolean("auto_check_update", true)) }
    var updateReminder by remember { mutableStateOf(prefs.getBoolean("update_reminder", true)) }
    var updateChannel by remember {
        mutableStateOf(
            prefs.getString("update_channel", "stable")
        )
    }
    var downloadSource by remember {
        mutableStateOf(
            prefs.getString("download_source", "gitee")
        )
    }
    val effectiveDownloadSource = if (updateChannel == "beta") "gitee" else downloadSource

    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
        } catch (_: Exception) {
            "未知"
        }
    }

    var isChecking by remember { mutableStateOf(false) }
    var hasUpdate by remember { mutableStateOf(prefs.getBoolean("has_update", false)) }
    var latestRelease by remember {
        // KeyValueStore.getString 收非空默认值，取「键可能不存在」的语义要用 getStringOrNull
        val savedUrl = prefs.getStringOrNull("latest_url")
        val savedApkUrl = prefs.getStringOrNull("latest_apk_url")
        val savedTag = prefs.getStringOrNull("latest_tag")
        val savedName = prefs.getStringOrNull("latest_name")
        val savedBody = prefs.getStringOrNull("latest_body")
        val savedDate = prefs.getStringOrNull("latest_date")
        mutableStateOf(
            if (savedUrl != null && savedTag != null) UpdateChecker.GiteeRelease(
                savedTag,
                savedName ?: "",
                savedBody ?: "",
                savedUrl,
                savedApkUrl ?: "",
                savedDate ?: "",
                // 期望值统一存在 update_settings 里，这里按 tag 回读
                UpdateChecker.rememberedApkSha256(context, savedTag),
                UpdateChecker.rememberedApkSize(context, savedTag)
            )
            else null
        )
    }

    var showDownloadDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    /** 缓存里的下载地址不可用，正在回源重新解析 */
    var isResolvingApk by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadComplete by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        if (autoCheckUpdate) {
            val lastCheckDate = prefs.getString("last_check_date", "")
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            if (lastCheckDate != today) {
                isChecking = true
                val (update, release) = withContext(Dispatchers.IO) {
                    checkForUpdate(context, effectiveDownloadSource, updateChannel)
                }
                hasUpdate = update
                latestRelease = release
                isChecking = false
                prefs.edit {
                    // KeyValueEditor 的 putXxx 返回 Unit（不像 Android KTX 的 Editor 可链式），
                    // 因此必须逐条写，不能 .putString(...).putBoolean(...)
                    putString("last_check_date", today)
                    putBoolean("has_update", update)
                }
                if (update && release != null) {
                    UpdateChecker.persistRelease(context, release)
                    UpdateChecker.rememberApkDigest(context, release.tagName, release.apkSha256, release.apkSize)
                }
            }
        }
    }

    // 切换更新通道时清除缓存，下次自动检查重新拉取。
    // 注意：LaunchedEffect 在**首次组合时也会执行**，原来无差别清缓存，
    // 导致「每次打开本页」都把 has_update / latest_apk_url 等抹掉——
    // 弹窗那边读到空的 latest_apk_url 就再也点不出下载，而本页重新检查又能下。
    // 因此只在通道真的发生变化时才清。
    var cachedChannel by remember { mutableStateOf(updateChannel) }
    LaunchedEffect(updateChannel) {
        if (updateChannel == cachedChannel) return@LaunchedEffect
        cachedChannel = updateChannel
        hasUpdate = false
        latestRelease = null
        prefs.edit {
            remove("has_update")
            remove("latest_url")
            remove("latest_apk_url")
            remove("latest_tag")
            remove("latest_name")
            remove("latest_body")
            remove("latest_date")
            remove("last_check_date")
        }
    }

    val backdropColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backdropColor)
        drawContent()
    }
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    Scaffold(
        topBar = {}
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
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .collapsibleTopInset(scrollBehavior)
                    .then(
                        scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) }
                            ?: Modifier
                    ),
                contentPadding = PaddingValues(
                    start = tabletHorizontalPadding,
                    end = tabletHorizontalPadding,
                    top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight +
                        (if (isTablet) 24.dp else 12.dp),
                    bottom = 60.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = "当前版本",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "v$currentVersion",
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }

                            if (hasUpdate && latestRelease != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 12.dp)
                                ) {
                                    Text(
                                        text = "最新版本: ${latestRelease!!.tagName}",
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                    if (latestRelease!!.body.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = latestRelease!!.body.take(200),
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                }
                            }

                            ArrowPreference(
                                title = "检查更新",
                                endActions = {
                                    if (hasUpdate) {
                                        Text(
                                            text = "有新版本",
                                            fontSize = 14.sp,
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    if (hasUpdate && latestRelease != null) {
                                        val tag = latestRelease!!.tagName
                                        coroutineScope.launch {
                                            // 校验需解析 APK，放 IO 线程
                                            val valid = withContext(Dispatchers.IO) {
                                                UpdateInstaller.hasValidApk(context, tag)
                                            }
                                            downloadedFile = if (valid) UpdateInstaller.apkFile(context, tag) else null
                                            downloadComplete = valid
                                            downloadProgress = if (valid) 1f else 0f
                                            showDownloadDialog = true
                                            isResolvingApk = false
                                            isInstalling = false
                                        }
                                    } else if (!isChecking) {
                                        isChecking = true
                                        coroutineScope.launch {
                                            val (update, release) = withContext(Dispatchers.IO) {
                                                checkForUpdate(context, effectiveDownloadSource, updateChannel)
                                            }
                                            hasUpdate = update
                                            latestRelease = release
                                            isChecking = false
                                            prefs.edit {
                                                // 同上：KeyValueEditor 不可链式，逐条写
                                                putBoolean("has_update", update)
                                                putString(
                                                    "last_check_date",
                                                    java.text.SimpleDateFormat(
                                                        "yyyy-MM-dd",
                                                        java.util.Locale.getDefault()
                                                    ).format(java.util.Date())
                                                )
                                            }
                                            if (update && release != null) {
                                                UpdateChecker.persistRelease(context, release)
                                                val tag = release.tagName
                                                UpdateChecker.rememberApkDigest(context, tag, release.apkSha256, release.apkSize)
                                                // 校验需解析 APK，放 IO 线程
                                                val valid = withContext(Dispatchers.IO) {
                                                    UpdateInstaller.hasValidApk(context, tag)
                                                }
                                                downloadedFile = if (valid) UpdateInstaller.apkFile(context, tag) else null
                                                downloadComplete = valid
                                                downloadProgress = if (valid) 1f else 0f
                                                showDownloadDialog = true
                                                isResolvingApk = false
                                                isInstalling = false
                                            } else if (!update) {
                                                if (release == null) {
                                                    Toast.makeText(
                                                        context,
                                                        "检查更新失败，请检查网络连接",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "当前已是最新版本",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    SmallTitle(
                        text = "更多设置",
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val channelEntry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = "稳定版",
                                        selected = updateChannel == "stable",
                                        onClick = {
                                            updateChannel = "stable"
                                            prefs.edit { putString("update_channel", "stable") }
                                        }
                                    ),
                                    DropdownItem(
                                        text = "Beta",
                                        selected = updateChannel == "beta",
                                        onClick = {
                                            updateChannel = "beta"
                                            prefs.edit { putString("update_channel", "beta") }
                                        }
                                    ),
                                )
                            )
                            OverlayDropdownMenu(
                                title = "更新通道",
                                entry = channelEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )
                            val downloadSourceEntry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = "Gitee",
                                        selected = effectiveDownloadSource == "gitee",
                                        onClick = {
                                            downloadSource = "gitee"
                                            prefs.edit { putString("download_source", "gitee") }
                                        }
                                    ),
                                    DropdownItem(
                                        text = "GitHub",
                                        selected = effectiveDownloadSource == "github",
                                        onClick = {
                                            downloadSource = "github"
                                            prefs.edit { putString("download_source", "github") }
                                        }
                                    ),
                                )
                            )
                            OverlayDropdownMenu(
                                title = "下载源",
                                summary = if (updateChannel == "beta") "Beta 通道已锁定 Gitee" else "选择应用更新的下载仓库",
                                entry = downloadSourceEntry,
                                enabled = updateChannel != "beta",
                                collapseOnSelection = true,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SwitchPreference(
                                title = "自动检查更新",
                                summary = "启动时自动检查是否有新版本",
                                checked = autoCheckUpdate,
                                onCheckedChange = {
                                    autoCheckUpdate = it
                                    prefs.edit { putBoolean("auto_check_update", it) }
                                }
                            )
                            SwitchPreference(
                                title = "更新提醒",
                                summary = "发现新版本时弹出提醒",
                                checked = updateReminder,
                                onCheckedChange = {
                                    updateReminder = it
                                    prefs.edit { putBoolean("update_reminder", it) }
                                }
                            )
                        }
                    }
                }
            }

            OverlayDialog(
                title = when {
                    isInstalling -> "安装中"
                    downloadComplete -> "下载完成"
                    isResolvingApk -> "正在获取下载地址"
                    isDownloading -> "正在下载"
                    else -> "发现新版本"
                },
                summary = when {
                    isInstalling -> "正在安装应用，请稍候..."
                    else -> "最新版本: ${latestRelease?.tagName ?: ""}"
                },
                show = showDownloadDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,

                onDismissRequest = {
                    // 安装/下载都不阻塞关闭：系统安装器被取消后弹窗不能卡在「安装中」
                    showDownloadDialog = false
                    if (!isDownloading) {
                        isInstalling = false
                        downloadComplete = false
                        downloadProgress = 0f
                    }
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isInstalling) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else if (isResolvingApk) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "正在获取下载地址...",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    } else if (isDownloading) {
                        LinearProgressIndicator(
                            progress = downloadProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    } else if (downloadComplete) {
                        Spacer(modifier = Modifier.height(0.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 始终提供关闭入口：安装中也能关掉弹窗
                        TextButton(
                            text = if (isInstalling) "关闭" else "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showDownloadDialog = false
                                downloadComplete = false
                                downloadProgress = 0f
                                isDownloading = false
                                isResolvingApk = false
                                isInstalling = false
                            }, modifier = Modifier.weight(1f)
                        )
                        if (downloadComplete && !isInstalling) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    val file = downloadedFile ?: return@Button
                                    UpdateInstaller.installApk(
                                        context = context,
                                        file = file,
                                        onInstallingChanged = { isInstalling = it },
                                        onFinished = {
                                            // 安装流程已交棒（静默成功 / 系统安装器已拉起）：
                                            // 无条件收尾，避免用户取消安装后弹窗锁死
                                            isInstalling = false
                                            downloadComplete = false
                                            showDownloadDialog = false
                                        }
                                    )
                                },
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text(
                                    text = "安装",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ComposeColor.White
                                )
                            }
                        } else if (!isInstalling && !isDownloading) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    val tag = latestRelease?.tagName
                                    if (tag.isNullOrBlank()) {
                                        Toast.makeText(context, "未找到版本信息", Toast.LENGTH_SHORT).show()
                                        showDownloadDialog = false
                                        return@Button
                                    }
                                    isDownloading = true
                                    isResolvingApk = true
                                    downloadProgress = 0f
                                    downloadComplete = false
                                    coroutineScope.launch {
                                        // 与启动弹窗走同一套解析逻辑：缓存为空时回源重查，
                                        // 避免本页能下、弹窗却提示「未找到下载链接」
                                        val apkUrl = withContext(Dispatchers.IO) {
                                            UpdateChecker.resolveApkUrl(
                                                context,
                                                tag,
                                                effectiveDownloadSource,
                                                updateChannel
                                            )
                                        }
                                        isResolvingApk = false
                                        if (apkUrl.isNullOrBlank()) {
                                            isDownloading = false
                                            Toast.makeText(
                                                context,
                                                "获取下载地址失败，请稍后重试",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            showDownloadDialog = false
                                            return@launch
                                        }
                                        try {
                                            val file = UpdateInstaller.downloadApk(context, apkUrl, tag) { p ->
                                                downloadProgress = p
                                            }
                                            downloadedFile = file
                                            downloadComplete = true
                                            isDownloading = false
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "下载失败: ${e.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            isDownloading = false
                                            showDownloadDialog = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text(
                                    text = "开始下载",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ComposeColor.White
                                )
                            }
                        } else if (isDownloading) {
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = false,
                                onClick = {}) {
                                Text(
                                    text = if (isResolvingApk) "获取链接" else "正在下载",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isAppDarkTheme()) ComposeColor.White else ComposeColor.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

