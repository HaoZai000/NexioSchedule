/** 应用更新设置页面 - Screen */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.haooz.chedule.shizuku.ShizukuManager
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
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
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.graphics.Color as ComposeColor

private data class GiteeRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val apkUrl: String,
    val createdAt: String
)

private fun isNewerVersion(remote: String, local: String): Boolean {
    fun parseSegments(v: String): List<Int> {
        return v.split(".").flatMap { part ->
            val betaIdx = part.indexOf("beta")
            if (betaIdx >= 0) {
                val num = part.substring(0, betaIdx).toIntOrNull() ?: 0
                val betaNum = part.substring(betaIdx + 4).toIntOrNull() ?: 0
                listOf(num, betaNum)
            } else {
                listOf(part.toIntOrNull() ?: 0)
            }
        }
    }
    val remoteParts = parseSegments(remote)
    val localParts = parseSegments(local)
    val maxSize = maxOf(remoteParts.size, localParts.size)
    for (i in 0 until maxSize) {
        val r = remoteParts.getOrElse(i) { 0 }
        val l = localParts.getOrElse(i) { 0 }
        if (r > l) return true
        if (r < l) return false
    }
    return false
}

private fun checkForUpdate(
    context: Context,
    source: String = "gitee",
    channel: String = "stable"
): Pair<Boolean, GiteeRelease?> {
    return try {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val baseUrl = if (source == "github") {
            "https://api.github.com/repos/HaoZai000/NexioSchedule/releases"
        } else {
            "https://gitee.com/api/v5/repos/com_haooz_account/hyper_schedule/releases"
        }
        val url = "$baseUrl?page=1&per_page=10&direction=desc&t=${System.currentTimeMillis()}"
        val request = okhttp3.Request.Builder().url(url).apply {
            if (source == "github") {
                header("Accept", "application/vnd.github.v3+json")
            }
        }.build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            android.util.Log.e("UpdateCheck", "HTTP ${response.code}")
            return Pair(false, null)
        }

        val responseBody = response.body?.string() ?: return Pair(false, null)
        android.util.Log.d("UpdateCheck", "响应长度: ${responseBody.length}")

        val arr = com.google.gson.JsonParser.parseString(responseBody).asJsonArray
        var best: com.google.gson.JsonObject? = null
        var bestVer = ""
        for (i in 0 until arr.size()) {
            val release = arr[i].asJsonObject
            if (channel == "stable") {
                val isPre = release.get("prerelease")?.asBoolean ?: false
                if (isPre) continue
            }
            val tag = release.get("tag_name")?.asString ?: continue
            val ver = tag.removePrefix("v")
            if (best == null || isNewerVersion(ver, bestVer)) {
                best = release
                bestVer = ver
            }
        }
        val json = best
        if (json == null) return Pair(false, null)

        val tagName = json.get("tag_name")?.asString ?: ""
        val name = json.get("name")?.asString ?: ""
        val body = json.get("body")?.asString ?: ""
        val htmlUrl = json.get("html_url")?.asString ?: ""
        val createdAt = json.get("created_at")?.asString ?: ""

        val assets = json.getAsJsonArray("assets")
        var apkUrl = ""
        if (assets != null) {
            for (i in 0 until assets.size()) {
                val a = assets[i].asJsonObject
                val assetName = a.get("name")?.asString ?: ""
                if (assetName.endsWith(".apk")) {
                    apkUrl = a.get("browser_download_url")?.asString ?: ""
                    break
                }
            }
        }

        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }

        val tagVersion = tagName.removePrefix("v")
        val appVersion = currentVersion.removePrefix("v")
        val hasUpdate = isNewerVersion(tagVersion, appVersion)
        Pair(hasUpdate, GiteeRelease(tagName, name, body, htmlUrl, apkUrl, createdAt))
    } catch (e: Exception) {
        android.util.Log.e("UpdateCheck", "检查更新失败", e)
        Pair(false, null)
    }
}

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
    val prefs = remember { context.getSharedPreferences("update_settings", Context.MODE_PRIVATE) }
    // 液态玻璃效果的透明下拉颜色
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )

    var autoCheckUpdate by remember { mutableStateOf(prefs.getBoolean("auto_check_update", true)) }
    var updateReminder by remember { mutableStateOf(prefs.getBoolean("update_reminder", true)) }
    var updateChannel by remember {
        mutableStateOf(
            prefs.getString("update_channel", "stable") ?: "stable"
        )
    }
    var downloadSource by remember {
        mutableStateOf(
            prefs.getString("download_source", "gitee") ?: "gitee"
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
        val savedUrl = prefs.getString("latest_url", null)
        val savedApkUrl = prefs.getString("latest_apk_url", null)
        val savedTag = prefs.getString("latest_tag", null)
        val savedName = prefs.getString("latest_name", null)
        val savedBody = prefs.getString("latest_body", null)
        val savedDate = prefs.getString("latest_date", null)
        mutableStateOf(
            if (savedUrl != null && savedTag != null) GiteeRelease(
                savedTag,
                savedName ?: "",
                savedBody ?: "",
                savedUrl,
                savedApkUrl ?: "",
                savedDate ?: ""
            )
            else null
        )
    }

    var showDownloadDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
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
                    putString("last_check_date", today)
                        .putBoolean("has_update", update)
                }
                if (update && release != null) {
                    prefs.edit {
                        putString("latest_url", release.htmlUrl)
                            .putString("latest_apk_url", release.apkUrl)
                            .putString("latest_tag", release.tagName)
                            .putString("latest_name", release.name)
                            .putString("latest_body", release.body)
                            .putString("latest_date", release.createdAt)
                    }
                }
            }
        }
    }

    // 切换更新通道时清除缓存，下次自动检查重新拉取
    LaunchedEffect(updateChannel) {
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
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 16.dp

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
            val density = androidx.compose.ui.platform.LocalDensity.current
            val topBarHeightDp = with(density) {
                (scrollBehavior?.currentHeightPx ?: 0f).toDp()
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .then(
                        scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) }
                            ?: Modifier
                    ),
                contentPadding = PaddingValues(
                    start = tabletHorizontalPadding,
                    end = tabletHorizontalPadding,
                    top = paddingValues.calculateTopPadding() + topBarHeightDp + 12.dp,
                    bottom = 60.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        cornerRadius = 20.dp,
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
                                        val apkFile = File(
                                            context.filesDir,
                                            "update-${latestRelease!!.tagName}.apk"
                                        )
                                        if (apkFile.exists() && apkFile.length() > 0) {
                                            downloadedFile = apkFile
                                            downloadComplete = true
                                            downloadProgress = 1f
                                        } else {
                                            downloadComplete = false
                                            downloadProgress = 0f
                                        }
                                        showDownloadDialog = true
                                        isInstalling = false
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
                                                putBoolean("has_update", update)
                                                    .putString(
                                                        "last_check_date",
                                                        java.text.SimpleDateFormat(
                                                            "yyyy-MM-dd",
                                                            java.util.Locale.getDefault()
                                                        ).format(java.util.Date())
                                                    )
                                            }
                                            if (update && release != null) {
                                                prefs.edit {
                                                    putString("latest_url", release.htmlUrl)
                                                        .putString("latest_apk_url", release.apkUrl)
                                                        .putString("latest_tag", release.tagName)
                                                        .putString("latest_name", release.name)
                                                        .putString("latest_body", release.body)
                                                        .putString("latest_date", release.createdAt)
                                                }
                                                val apkFile = File(
                                                    context.filesDir,
                                                    "update-${release.tagName}.apk"
                                                )
                                                if (apkFile.exists() && apkFile.length() > 0) {
                                                    downloadedFile = apkFile
                                                    downloadComplete = true
                                                    downloadProgress = 1f
                                                } else {
                                                    downloadComplete = false
                                                    downloadProgress = 0f
                                                }
                                                showDownloadDialog = true
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
                        modifier = Modifier.offset(x = (-15).dp)
                    )
                    Card(
                        cornerRadius = 20.dp,
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
                        cornerRadius = 20.dp,
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
                    if (!isDownloading && !isInstalling) {
                        showDownloadDialog = false
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
                        if (!isInstalling) {
                            TextButton(
                                text = "取消",
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    showDownloadDialog = false
                                    downloadComplete = false
                                    downloadProgress = 0f
                                    isDownloading = false
                                }, modifier = Modifier.weight(1f)
                            )
                        }
                        if (downloadComplete && !isInstalling) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    val file = downloadedFile ?: return@Button
                                    if (ShizukuManager.isShizukuRunning() &&
                                        ShizukuManager.checkSelfPermission()
                                    ) {
                                        // 有 Shizuku 权限 → ADB 式静默安装
                                        isInstalling = true
                                        coroutineScope.launch {
                                            val (ok, message) = withContext(Dispatchers.IO) {
                                                ShizukuManager.silentInstallApk(file.absolutePath)
                                            }
                                            if (ok) {
                                                isInstalling = false
                                                downloadComplete = false
                                                showDownloadDialog = false
                                                Toast.makeText(
                                                    context,
                                                    message,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                // 静默安装失败 → 回退系统安装器
                                                isInstalling = false
                                                Toast.makeText(
                                                    context,
                                                    "静默安装失败，已改用系统安装器",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                isInstalling = true
                                                installApk(context, file)
                                            }
                                        }
                                    } else {
                                        // 无 Shizuku → 系统安装器
                                        isInstalling = true
                                        installApk(context, file)
                                    }
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
                                    isDownloading = true; downloadProgress = 0f; downloadComplete =
                                    false
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            try {
                                                val apkUrl = latestRelease?.apkUrl
                                                if (apkUrl.isNullOrBlank()) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(
                                                            context,
                                                            "未找到下载链接",
                                                            Toast.LENGTH_SHORT
                                                        ).show(); isDownloading =
                                                        false; showDownloadDialog = false
                                                    }
                                                    return@withContext
                                                }
                                                val url = URL(apkUrl)
                                                val connection =
                                                    url.openConnection() as HttpURLConnection
                                                connection.connectTimeout =
                                                    30000; connection.readTimeout =
                                                    30000; connection.connect()
                                                val fileSize = connection.contentLength.toLong()
                                                val fileName =
                                                    "update-${latestRelease?.tagName}.apk"
                                                val file = File(context.filesDir, fileName)
                                                connection.inputStream.use { input ->
                                                    FileOutputStream(file).use { output ->
                                                        val buffer = ByteArray(8192)
                                                        var bytesRead: Int
                                                        var totalRead = 0L
                                                        while (input.read(buffer)
                                                                .also { bytesRead = it } != -1
                                                        ) {
                                                            output.write(
                                                                buffer,
                                                                0,
                                                                bytesRead
                                                            ); totalRead += bytesRead
                                                            if (fileSize > 0) downloadProgress =
                                                                (totalRead.toFloat() / fileSize).coerceIn(
                                                                    0f,
                                                                    1f
                                                                )
                                                        }
                                                    }
                                                }
                                                downloadedFile = file; downloadComplete =
                                                    true; isDownloading = false
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context,
                                                        "下载失败: ${e.message}",
                                                        Toast.LENGTH_SHORT
                                                    ).show(); isDownloading =
                                                    false; showDownloadDialog = false
                                                }
                                            }
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
                                    text = "正在下载",
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

private fun installApk(context: Context, file: File) {
    try {
        val uri: Uri =
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "安装失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

