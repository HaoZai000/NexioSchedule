/** 应用更新页面 - Screen（更新能力经 UpdateController 注入） */
package com.haooz.chedule.ui.activities

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.platform.NoopUpdateController
import com.haooz.chedule.data.platform.UpdateController
import com.haooz.chedule.data.platform.UpdateRelease
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.getStringOrNull
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.AboutStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.data.changelogData
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.isAppDarkTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 更新页展示的最近几条更新日志（文件级：避免每次组合都新建 List）。 */
private val RecentChangelog = changelogData.take(3)

/** 今日日期 `yyyy-MM-dd`（手写零填充；⚠ 与迁移前逐字一致，勿改）。 */
private fun todayIsoDate(): String {
    val date = todayLocalDate()
    return "${date.year.toString().padStart(4, '0')}" +
        "-${date.monthNumber.toString().padStart(2, '0')}" +
        "-${date.dayOfMonth.toString().padStart(2, '0')}"
}

@Composable
fun UpdateSettingsScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    /** 更新能力控制器（Android 注入 `AndroidUpdateController`；桌面/iOS 用 [NoopUpdateController]）。 */
    updateController: UpdateController = NoopUpdateController,
    /** 进完整更新日志页 */
    onOpenChangelog: () -> Unit = {},
) {
    val hapticFeedback = LocalHapticFeedback.current
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

    // 版本号取自控制器（取不到显示「未知」）
    val currentVersion = remember { updateController.currentVersion }

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
            if (savedUrl != null && savedTag != null) UpdateRelease(
                savedTag,
                savedName ?: "",
                savedBody ?: "",
                savedUrl,
                savedApkUrl ?: "",
                savedDate ?: "",
                // 期望值统一存在 update_settings 里，这里按 tag 回读
                updateController.rememberedApkSha256(savedTag),
                updateController.rememberedApkSize(savedTag)
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
    /** 已下载安装包的句柄（绝对路径；commonMain 无平台文件类型，经 UpdateController 以 String 传递） */
    var downloadedFile by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (autoCheckUpdate) {
            val lastCheckDate = prefs.getString("last_check_date", "")
            val today = todayIsoDate()
            if (lastCheckDate != today) {
                isChecking = true
                val (update, release) = withContext(ioDispatcher) {
                    updateController.checkForUpdate(effectiveDownloadSource, updateChannel)
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
                    updateController.persistRelease(release)
                    updateController.rememberApkDigest(release.tagName, release.apkSha256, release.apkSize)
                }
            }
        }
    }

    // 切换通道时清缓存，下次自动检查重新拉取。
    // ⚠ 只在通道真的变化时才清：LaunchedEffect 首次组合也会执行，
    // 无差别清会把 has_update / latest_apk_url 抹掉，弹窗再也点不出下载。
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

    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val listState = rememberLazyListState()
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
                                    text = AboutStr.currentVersion(),
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

                            CardExpand(visible = hasUpdate && latestRelease != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 12.dp)
                                ) {
                                    Text(
                                        // ⚠ 不能用 `latestRelease!!`：CardExpand 收起动画期间内容仍在组合
                                        //（AnimatedVisibility 退出期保留子节点），切通道把它置空的那一帧就 NPE
                                        text = AboutStr.latestVersionN(latestRelease?.tagName.orEmpty()),
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                    CardExpand(visible = latestRelease?.body?.isNotBlank() == true) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = latestRelease?.body?.take(200).orEmpty(),
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                }
                            }

                            ArrowPreference(
                                title = AboutStr.checkForUpdates(),
                                endActions = {
                                    if (hasUpdate) {
                                        Text(
                                            text = AboutStr.newVersionAvailable(),
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
                                            val valid = withContext(ioDispatcher) {
                                                updateController.hasValidApk(tag)
                                            }
                                            downloadedFile = if (valid) updateController.localApkPath(tag) else null
                                            downloadComplete = valid
                                            downloadProgress = if (valid) 1f else 0f
                                            showDownloadDialog = true
                                            isResolvingApk = false
                                            isInstalling = false
                                        }
                                    } else if (!isChecking) {
                                        isChecking = true
                                        coroutineScope.launch {
                                            val (update, release) = withContext(ioDispatcher) {
                                                updateController.checkForUpdate(effectiveDownloadSource, updateChannel)
                                            }
                                            hasUpdate = update
                                            latestRelease = release
                                            isChecking = false
                                            prefs.edit {
                                                // 同上：KeyValueEditor 不可链式，逐条写
                                                putBoolean("has_update", update)
                                                putString("last_check_date", todayIsoDate())
                                            }
                                            if (update && release != null) {
                                                updateController.persistRelease(release)
                                                val tag = release.tagName
                                                updateController.rememberApkDigest(tag, release.apkSha256, release.apkSize)
                                                // 校验需解析 APK，放 IO 线程
                                                val valid = withContext(ioDispatcher) {
                                                    updateController.hasValidApk(tag)
                                                }
                                                downloadedFile = if (valid) updateController.localApkPath(tag) else null
                                                downloadComplete = valid
                                                downloadProgress = if (valid) 1f else 0f
                                                showDownloadDialog = true
                                                isResolvingApk = false
                                                isInstalling = false
                                            } else if (!update) {
                                                if (release == null) {
                                                    showToast(AboutStr.checkUpdateFailedNetwork())
                                                } else {
                                                    showToast(AboutStr.upToDate())
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
                        text = AboutStr.moreSettings(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val channelEntry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = AboutStr.stableChannel(),
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
                                title = AboutStr.updateChannel(),
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
                                title = AboutStr.downloadSource(),
                                summary = if (updateChannel == "beta") AboutStr.betaLockedGitee() else AboutStr.downloadSourceSummary(),
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
                                title = AboutStr.autoCheckUpdates(),
                                summary = AboutStr.autoCheckUpdatesSummary(),
                                checked = autoCheckUpdate,
                                onCheckedChange = {
                                    autoCheckUpdate = it
                                    prefs.edit { putBoolean("auto_check_update", it) }
                                }
                            )
                            SwitchPreference(
                                title = AboutStr.updateReminder(),
                                summary = AboutStr.updateReminderSummary(),
                                checked = updateReminder,
                                onCheckedChange = {
                                    updateReminder = it
                                    prefs.edit { putBoolean("update_reminder", it) }
                                }
                            )
                        }
                    }
                }

                item(key = "changelog") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenChangelog)
                                    .padding(start = 16.dp, end = 13.dp, top = 17.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = AboutStr.changelog(),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = MiuixIcons.ChevronForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            }
                            val recentChangelog = RecentChangelog
                            // 一次 remember 拿整张状态表：逐个 remember 会按位置错位
                            val expandedStates = remember {
                                List(RecentChangelog.size) { index -> mutableStateOf(index == 0) }
                            }
                            val rotations = List(recentChangelog.size) { index ->
                                val rotation by animateFloatAsState(
                                    targetValue = if (expandedStates[index].value) 90f else -90f,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "changelogRotation$index"
                                )
                                rotation
                            }
                            recentChangelog.forEachIndexed { index, entry ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedStates[index].value = !expandedStates[index].value
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                start = 20.dp,
                                                end = 18.dp,
                                                top = if (index == 0) 12.dp else 17.dp,
                                                bottom = 17.dp
                                            ),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = entry.version,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = entry.date,
                                            fontSize = 14.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = MiuixIcons.ChevronForward,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .graphicsLayer { rotationZ = rotations[index] },
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                    CardExpand(visible = expandedStates[index].value) {
                                        Column(
                                            modifier = Modifier.padding(
                                                start = 18.dp,
                                                end = 18.dp,
                                                bottom = 14.dp
                                            )
                                        ) {
                                            entry.changes.forEach { change ->
                                                Row(modifier = Modifier.padding(bottom = 2.dp)) {
                                                    Text(
                                                        text = "• ",
                                                        fontSize = 14.sp,
                                                        lineHeight = 22.sp,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                    )
                                                    Text(
                                                        text = change(),
                                                        fontSize = 14.sp,
                                                        lineHeight = 22.sp,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (index < recentChangelog.lastIndex) {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp)
                                            .height(0.5.dp)
                                            .background(
                                                MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.07f)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OverlayDialog(
                title = when {
                    isInstalling -> AboutStr.installing()
                    downloadComplete -> AboutStr.downloadComplete()
                    isResolvingApk -> AboutStr.gettingDownloadUrl()
                    isDownloading -> CommonStr.downloading()
                    else -> AboutStr.newVersionFound()
                },
                summary = when {
                    isInstalling -> AboutStr.installingSummary()
                    else -> AboutStr.latestVersionN(latestRelease?.tagName ?: "")
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
                            text = AboutStr.gettingDownloadUrlDots(),
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
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            textAlign = TextAlign.Center,
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
                            text = if (isInstalling) CommonStr.close() else CommonStr.cancel(),
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
                                    updateController.installApk(
                                        apkPath = file,
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
                                    text = AboutStr.install(),
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
                                        showToast(AboutStr.versionInfoNotFound())
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
                                        val apkUrl = withContext(ioDispatcher) {
                                            updateController.resolveApkUrl(
                                                tag,
                                                effectiveDownloadSource,
                                                updateChannel
                                            )
                                        }
                                        isResolvingApk = false
                                        if (apkUrl.isNullOrBlank()) {
                                            isDownloading = false
                                            showToast(AboutStr.getDownloadUrlFailedRetry(), longDuration = true)
                                            showDownloadDialog = false
                                            return@launch
                                        }
                                        try {
                                            val file = updateController.downloadApk(apkUrl, tag) { p ->
                                                downloadProgress = p
                                            }
                                            downloadedFile = file
                                            downloadComplete = true
                                            isDownloading = false
                                        } catch (e: Exception) {
                                            showToast(AboutStr.downloadFailedN(e.message))
                                            isDownloading = false
                                            showDownloadDialog = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text(
                                    text = AboutStr.startDownload(),
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
                                    text = if (isResolvingApk) AboutStr.getLink() else CommonStr.downloading(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    // 按钮自己居中内容，换字时按中心收放
                                    textAlign = TextAlign.Center,
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
