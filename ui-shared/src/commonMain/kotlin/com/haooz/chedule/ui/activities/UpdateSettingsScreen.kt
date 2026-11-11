/** 应用更新页面 - Screen（更新能力经 UpdateController 注入） */
package com.haooz.chedule.ui.activities

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.utils.HostNavigationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
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
import com.haooz.chedule.ui.data.ChangelogEntry
import com.haooz.chedule.ui.data.changelogData
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.glass.BrightTone
import com.haooz.softglass.glass.CustomizeFill
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.ThemeFill
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.chedule.ui.utils.AppMaterialSettings
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.haooz.softglass.platform.isRenderEffectSupported
import com.haooz.softglass.platform.isSoftGlassShaderAvailable
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
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
import top.yukonga.miuix.kmp.icon.extended.Update
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

/** 版本卡宽高比（宽 / 高）：0.78 是一个明确的纵向长方形，手机与平板同形不同尺寸。 */
private const val HeroCardAspect = 0.78f

/** 版本卡平板端宽度上限：不做满宽，否则纵向长方形会被拉成一张大床单。 */
private val HeroCardMaxWidth = 440.dp

/**
 * 翻转角：整张卡绕自身横轴翻 180°
 */
private const val HeroFlipDegrees = 180f

/** 翻转时下潜的深度（相对卡片高度）：U 的谷底离原位有多深 */
private const val HeroFlipDipRatio = 0.65f

/** 相机距离（乘以 density 转成像素）：越小透视越猛 */
private const val HeroFlipCamera = 40f

/** 开卡动效时长（位移 + 翻转同一段）。 */
private const val HeroOpenMotionMs = 560

/** 收卡动效时长：回程略快，点外面时手感更利落。 */
private const val HeroCloseMotionMs = 420

/** 状态胶囊的柔光 shader 缓存键（尺寸与其它玻璃件不同，不能共用 uniform）。 */
private const val HeroPillGlassKey = "NexioSoftGlassLiveHeroPill"

/** 今日日期 `yyyy-MM-dd`（手写零填充；⚠ 与迁移前逐字一致，勿改）。 */
private fun todayIsoDate(): String {
    val date = todayLocalDate()
    return "${date.year.toString().padStart(4, '0')}" +
        "-${date.monthNumber.toString().padStart(2, '0')}" +
        "-${date.dayOfMonth.toString().padStart(2, '0')}"
}

/**
 * 取版本号的「主.次」两位：`1.7.0` → `1.7`。
 * 解析不出来（如「未知版本」）返回 null，调用方退化成整串小字号显示。
 */
private fun majorMinorVersion(version: String): String? {
    val parts = version.trim().removePrefix("v").split('.')
    if (parts.size < 2) return null
    val major = parts[0].toIntOrNull() ?: return null
    val minor = parts[1].toIntOrNull() ?: return null
    return "$major.$minor"
}


/**
 * ISO 时间串只取日期：`2026-11-10T18:45:16+08:00` → `2026-11-10`。
 * 服务端给的是完整时间戳，直接拼在版本号后面会变成「v1.7.1 · 2026-11-10T18:45:16+08:00」。
 */
private fun isoDateOnly(raw: String): String {
    val text = raw.trim()
    val datePart = text.substringBefore('T').substringBefore(' ')
    return if (datePart.length == 10 && datePart.count { it == '-' } == 2) datePart else text
}

/**
 * 版本号去掉前缀与构建号：`1.6.7-1103` / `v1.6.7` → `1.6.7`。
 *
 * 必须带到 patch 位——只按「主.次」匹配的话，1.6.7 和 1.6.8 都会塌成 `1.6`，
 * 点「更新日志」就会拿错版本（比如 1.6.7 的包装机显示 1.6.8 的日志）。
 */
private fun versionCore(version: String): String? {
    val core = version.trim().removePrefix("v")
        .takeWhile { it != '-' && it != '+' }
    return core.takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() || c == '.' } }
}

/**
 * 当前版本对应的那一条更新日志：先按完整版本号（含 patch）精确匹配，
 * 再退回「主.次」，最后才拿最新一条保底。
 */
private fun changelogFor(version: String): ChangelogEntry? {
    val core = versionCore(version)
    if (core != null) {
        changelogData.firstOrNull { versionCore(it.version) == core }?.let { return it }
    }
    val key = majorMinorVersion(version) ?: return changelogData.firstOrNull()
    return changelogData.firstOrNull { majorMinorVersion(it.version) == key }
        ?: changelogData.firstOrNull()
}

/**
 * 带 `v` 前缀的版本号：数字版本（`1.7.0`）补前缀；已经是文字（`未知版本`、`v1.7.0-beta`）就原样返回，
 * 免得降级成「v未知版本」这种别扭写法。
 */
private fun displayVersion(version: String): String {
    val raw = version.trim()
    val first = raw.firstOrNull() ?: return raw
    return if (first.isDigit()) "v$raw" else raw
}

@Composable
fun UpdateSettingsScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    /** 更新能力控制器（Android 注入 `AndroidUpdateController`；桌面/iOS 用 [NoopUpdateController]）。 */
    updateController: UpdateController = NoopUpdateController,
    /** 进完整更新日志页 */
    onOpenChangelog: () -> Unit = {},
    /** 页面级模糊进度（dp） */
    pageBlur: Animatable<Float, AnimationVector1D>? = null,
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

    // 版本卡「打开态」：离位 → 浮到屏幕中央翻转，背后整页模糊。
    var heroOpen by remember { mutableStateOf(false) }
    var heroMounted by remember { mutableStateOf(false) }
    // 落定后才允许点背面「查看全部更新日志」：
    var heroSettled by remember { mutableStateOf(false) }
    /** 背面讲哪一本账：当前版本，还是服务端那个新版本 */
    var heroMode by remember { mutableStateOf(HeroBackMode.Current) }
    val heroEntry = remember(currentVersion) { changelogFor(currentVersion) }
    /** 有新版本、拿到了 release、且当前不在检查中，才算「可看新版本日志」 */
    val heroHasUpdate = hasUpdate && latestRelease != null && !isChecking
    val heroRectRef = remember { HeroRectRef() }
    val heroProgress = remember { Animatable(0f) }
    LaunchedEffect(heroOpen) {
        if (heroOpen) {
            heroMounted = true
            heroSettled = false
            // 模糊与翻转并行：模糊套 morph 的口径（16dp / 540ms QuartOut），翻转跟随同一曲线
            launch {
                pageBlur?.animateTo(
                    HostNavigationState.DETAIL_BACKDROP_BLUR_DP,
                    tween(HeroOpenMotionMs, easing = FastOutSlowInEasing),
                )
            }
            heroProgress.animateTo(1f, tween(HeroOpenMotionMs, easing = FastOutSlowInEasing))
            heroSettled = true
        } else if (heroMounted) {
            heroSettled = false
            launch {
                pageBlur?.animateTo(0f, tween(HeroCloseMotionMs, easing = FastOutSlowInEasing))
            }
            heroProgress.animateTo(0f, tween(HeroCloseMotionMs, easing = FastOutSlowInEasing))
            heroMounted = false
        }
    }
    val scrimColor = ComposeColor.Black.copy(alpha = if (isAppDarkTheme()) 0.34f else 0.16f)

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val rootHeightPx = constraints.maxHeight.toFloat()
            // 只糊「页面内容」这一层：浮层卡与遮罩在外层，不会被糊掉；
            // 标题由脚手架的顶栏自己糊（那边单独挂了一层）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val blurDp = pageBlur?.value ?: 0f
                        renderEffect = if (blurDp > 0.01f && isRenderEffectSupported()) {
                            val blurPx = blurDp.dp.toPx()
                            BlurEffect(null, blurPx, blurPx, TileMode.Clamp)
                        } else null
                    }
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
                            pageTopExtra(isTablet, hasSmallTitle = false),
                        bottom = 60.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "hero") {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            HeroCardPlaceholder(
                                version = currentVersion,
                                entry = heroEntry,
                                hasUpdate = heroHasUpdate,
                                checking = isChecking,
                                hidden = heroMounted,
                                onBoundsChanged = { heroRectRef.rect = it },
                                onOpenChangelog = {
                                    if (heroRectRef.rect != null) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        heroMode = HeroBackMode.Current
                                        heroOpen = true
                                    }
                                },
                                onOpenUpdate = {
                                    if (heroRectRef.rect != null && heroHasUpdate) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        heroMode = HeroBackMode.Update
                                        heroOpen = true
                                    }
                                },
                            )
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // 只剩「检查」这一件事：有新版本自己去卡片看（发现新版本 → 翻面 → 开始下载），
                                // 这里不再顺手拉下载弹窗，点一次就是重查一次
                                ArrowPreference(
                                    title = AboutStr.checkForUpdates(),
                                    onClick = {
                                        if (isChecking) return@ArrowPreference
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        isChecking = true
                                        coroutineScope.launch {
                                            val (update, release) = withContext(ioDispatcher) {
                                                updateController.checkForUpdate(effectiveDownloadSource, updateChannel)
                                            }
                                            hasUpdate = update
                                            latestRelease = release
                                            isChecking = false
                                            prefs.edit {
                                                // KeyValueEditor 不可链式，逐条写
                                                putBoolean("has_update", update)
                                                putString("last_check_date", todayIsoDate())
                                            }
                                            if (update && release != null) {
                                                updateController.persistRelease(release)
                                                updateController.rememberApkDigest(
                                                    release.tagName, release.apkSha256, release.apkSize
                                                )
                                            } else if (release == null) {
                                                showToast(AboutStr.checkUpdateFailedNetwork())
                                            } else {
                                                showToast(AboutStr.upToDate())
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                }
            }

            // 打开态：整页压暗（模糊在上一层）+ 卡片在屏幕中央翻转。
            // 顺序即层级：遮罩在页面之上、卡片之下，点遮罩等于「翻回去」。
            if (heroMounted) {
                val rect = heroRectRef.rect
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = heroProgress.value }
                        .background(scrimColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { heroOpen = false }
                )
                if (rect != null) {
                    HeroOpenCard(
                        modifier = Modifier.align(Alignment.TopCenter),
                        rect = rect,
                        rootHeightPx = rootHeightPx,
                        progress = { heroProgress.value },
                        version = currentVersion,
                        entry = heroEntry,
                        release = latestRelease,
                        mode = heroMode,
                        hasUpdate = heroHasUpdate,
                        checking = isChecking,
                        interactive = heroSettled,
                        onOpenChangelog = onOpenChangelog,
                        onStartUpdate = {
                            // 下载弹窗的准备工作搬到这里：「检查更新」不再负责开弹窗
                            heroOpen = false
                            val tag = latestRelease?.tagName
                            if (!tag.isNullOrBlank()) {
                                coroutineScope.launch {
                                    // 校验需解析 APK，放 IO 线程
                                    val valid = withContext(ioDispatcher) {
                                        updateController.hasValidApk(tag)
                                    }
                                    downloadedFile = if (valid) updateController.localApkPath(tag) else null
                                    downloadComplete = valid
                                    downloadProgress = if (valid) 1f else 0f
                                    isResolvingApk = false
                                    isInstalling = false
                                    showDownloadDialog = true
                                }
                            }
                        },
                        onClose = { heroOpen = false },
                    )
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

/** 占位卡的位置（只在点击那一刻读，避免滚动时写快照状态）。 */
private class HeroRectRef {
    var rect: Rect? = null
}

/** 卡片背面讲哪一本账：当前装的这版，还是服务端那个新版本。 */
internal enum class HeroBackMode {
    /** 当前版本更新日志 */
    Current,

    /** 新版本更新日志 */
    Update,
}

/**
 * 列表里的版本卡：只画正面、只负责「点我」。
 *
 * 真正的翻转不在这里发生——卡片要先离位浮到屏幕中央，所以静态面与浮层面是两套节点，
 * 中间靠 [onBoundsChanged] 记录的矩形对齐（浮层从同一位置起跳，视觉上就是同一张卡）。
 */
@Composable
private fun HeroCardPlaceholder(
    version: String,
    entry: ChangelogEntry?,
    hasUpdate: Boolean,
    checking: Boolean,
    hidden: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    Box(
        modifier = Modifier
            .widthIn(max = HeroCardMaxWidth)
            .fillMaxWidth()
            .aspectRatio(HeroCardAspect)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            // 浮层打开时留坑不画：位置不能变，否则关回来会错位
            .graphicsLayer { alpha = if (hidden) 0f else 1f }
    ) {
        HeroCardFace(watermark = true) { glass ->
            HeroCardFront(
                version = version,
                entry = entry,
                hasUpdate = hasUpdate,
                checking = checking,
                glass = glass,
                onOpenChangelog = onOpenChangelog,
                onOpenUpdate = onOpenUpdate,
            )
        }
    }
}

/**
 * 打开态的浮层卡：从列表里的矩形位置滑到屏幕垂直居中，同时整卡翻 180°。
 * 轨迹是**U 型**：水平不动，垂直方向先下潜再回位（`sin(πp)`，两端速度为 0，起止不顿）
 */
@Composable
internal fun HeroOpenCard(
    modifier: Modifier = Modifier,
    rect: Rect,
    rootHeightPx: Float,
    progress: () -> Float,
    version: String,
    entry: ChangelogEntry?,
    release: UpdateRelease?,
    mode: HeroBackMode,
    hasUpdate: Boolean = false,
    checking: Boolean = false,
    interactive: Boolean = true,
    onOpenChangelog: () -> Unit = {},
    onStartUpdate: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val densityScope = LocalDensity.current
    val cardWidth = with(densityScope) { rect.width.toDp() }
    val cardHeight = with(densityScope) { rect.height.toDp() }
    val centerTop = (rootHeightPx - rect.height) / 2f
    val startTop = rect.top
    val dipPx = rect.height * HeroFlipDipRatio

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .graphicsLayer {
                val p = progress()
                translationY = startTop + (centerTop - startTop) * p +
                    dipPx * sin(PI.toFloat() * p)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            )
    ) {
        // 正面：绕自身横轴转（轴心在卡片中心，不挪铰链）
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val deg = progress() * HeroFlipDegrees
                    cameraDistance = HeroFlipCamera * density
                    rotationX = deg
                    alpha = if (deg < 90f) 1f else 0f
                }
        ) {
            HeroCardFace(watermark = true) { glass ->
                HeroCardFront(
                    version = version,
                    entry = entry,
                    // ⚠ 别用 `release != null`：「检查更新」即使没新版本也可能回一个 release，
                    // 那样翻面过程中正面会假报「发现新版本」
                    hasUpdate = hasUpdate,
                    checking = checking,
                    glass = glass,
                    onOpenChangelog = onOpenChangelog,
                    onOpenUpdate = {},
                )
            }
        }
        // 背面：自身再转 180°，落定后是正立的
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val deg = progress() * HeroFlipDegrees
                    cameraDistance = HeroFlipCamera * density
                    rotationX = deg + HeroFlipDegrees
                    alpha = if (deg > 90f) 1f else 0f
                }
        ) {
            HeroCardFace { _ ->
                HeroCardBack(
                    mode = mode,
                    entry = entry,
                    release = release,
                    version = version,
                    interactive = interactive,
                    onOpenChangelog = onOpenChangelog,
                    onStartUpdate = onStartUpdate,
                )
            }
        }
    }
}

/**
 * 卡片底色 + 主色晕染 + 细描边；正反两面共用，翻面时跟着一起转。
 */
@Composable
private fun HeroCardFace(
    watermark: Boolean = false,
    content: @Composable (glass: GlassBackdrop?) -> Unit,
) {
    val shape = remember { ContinuousRoundedRectangle(28.dp) }
    val colorScheme = MiuixTheme.colorScheme
    val isDark = isAppDarkTheme()
    val tint = colorScheme.primary
    val cardColor = CardDefaults.defaultColors().color
    val onCard = colorScheme.onSurface
    val textStyles = MiuixTheme.textStyles
    val glassOn = true
    val cardBackdrop = rememberLayerBackdrop { drawContent() }
    val recordKey = remember(isDark, tint, cardColor) { listOf(isDark, tint, cardColor) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cardWidth = maxWidth
        // 离屏合成放在最外层：镂空字要用 DstOut 擦这一层，采样层只是它的一部分
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                // 内容也要裁：整行入口的 ripple 是方的，不裁会从卡片圆角外面漏出来
                .clip(shape)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (glassOn) Modifier.layerBackdrop(cardBackdrop, recordKey = recordKey)
                        else Modifier
                    )
                    .clip(shape)
                    .background(cardColor)
                    .drawBehind {
                        // 主色自顶向下化开：只染上层，不压暗正文
                        drawRect(
                            Brush.verticalGradient(
                                0f to tint.copy(alpha = if (isDark) 0.30f else 0.16f),
                                0.42f to tint.copy(alpha = if (isDark) 0.09f else 0.05f),
                                1f to Color.Transparent,
                            )
                        )
                        // 右上角光斑：给长方形一点体积感（圆心在卡片外，只露一段弧）
                        val glowRadius = size.width * 1.05f
                        val glowCenter = Offset(size.width * 0.92f, -size.height * 0.04f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    tint.copy(alpha = if (isDark) 0.26f else 0.15f),
                                    Color.Transparent,
                                ),
                                center = glowCenter,
                                radius = glowRadius,
                            ),
                            radius = glowRadius,
                            center = glowCenter,
                        )
                    }
                    .border(
                        width = 0.8.dp,
                        color = onCard.copy(alpha = if (isDark) 0.10f else 0.06f),
                        shape = shape,
                    )
            )
            if (watermark) {
                val watermarkStyle = remember(textStyles) {
                    textStyles.main.copy(
                        fontSize = 88.sp,
                        lineHeight = 92.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }
                val measurer = rememberTextMeasurer()
                val cardWidthPx = with(LocalDensity.current) { cardWidth.toPx() }
                val measuredWidth = remember(measurer, watermarkStyle) {
                    measurer.measure("NEXIO", watermarkStyle).size.width.toFloat()
                }
                val fitScale = if (measuredWidth > 0f) cardWidthPx / measuredWidth else 1f
                Text(
                    text = "NEXIO",
                    style = watermarkStyle,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(y = (-42).dp)
                        .graphicsLayer {
                            // 以左下角为锚点等比放大到正好一行宽
                            transformOrigin = TransformOrigin(0f, 1f)
                            scaleX = fitScale
                            scaleY = fitScale
                            blendMode = BlendMode.DstOut
                            alpha = 0.55f
                        },
                    color = Color.Black,
                    maxLines = 1,
                )
            }
            content(if (glassOn) cardBackdrop else null)
        }
    }
}

@Composable
private fun HeroCardFront(
    version: String,
    entry: ChangelogEntry?,
    hasUpdate: Boolean,
    checking: Boolean = false,
    glass: GlassBackdrop? = null,
    onOpenChangelog: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val onCard = MiuixTheme.colorScheme.onSurface
    val heroNumber = majorMinorVersion(version)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AboutStr.appName(),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                    color = onCard.copy(alpha = 0.92f),
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            VersionChip(label = displayVersion(version))
        }

        Spacer(modifier = Modifier.weight(1f))

        val heroText = remember(heroNumber, version, onCard) {
            buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Medium,
                        color = onCard.copy(alpha = 0.34f),
                        letterSpacing = 1.sp,
                    )
                ) {
                    append("V ")
                }
                withStyle(
                    SpanStyle(
                        fontSize = if (heroNumber != null) 92.sp else 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = if (heroNumber != null) (-4).sp else (-0.5).sp,
                        color = onCard,
                    )
                ) {
                    append(heroNumber ?: displayVersion(version))
                }
            }
        }
        Text(
            text = heroText,
            lineHeight = if (heroNumber != null) 96.sp else 40.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry != null) {
            Spacer(modifier = Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.date,
                    fontSize = 12.5.sp,
                    letterSpacing = 0.6.sp,
                    color = onCard.copy(alpha = 0.42f),
                    maxLines = 1,
                )
                if (entry.changes.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(9.dp))
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(onCard.copy(alpha = 0.22f))
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = AboutStr.changelogItemCount(entry.changes.size),
                        fontSize = 12.5.sp,
                        color = onCard.copy(alpha = 0.42f),
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        // 版本号与底部入口之间那块空间：上下左右都居中
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            UpdateStatusAction(
                hasUpdate = hasUpdate,
                checking = checking,
                glass = glass,
                onClick = onOpenUpdate,
            )
        }

        // 底部居中：更新日志入口（summary 色 + 右箭头，点击区带圆角与 padding）
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ChangelogLink(onClick = onOpenChangelog)
        }
    }
}

/**
 * 正面状态胶囊：三态同一个胶囊（尺寸不随状态变，否则一检查整卡版式就跳）。
 *
 * - 发现新版本：主色玻璃面 + 主色字，可点（翻到新版本日志）
 * - 暂无更新 / 检查中：中性玻璃面 + summary 字，不可点
 */
@Composable
private fun UpdateStatusAction(
    hasUpdate: Boolean,
    checking: Boolean,
    glass: GlassBackdrop?,
    onClick: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val isDark = isAppDarkTheme()
    val actionable = hasUpdate && !checking
    val isLightTheme = !isDark
    val tint = if (actionable) Color.White else colorScheme.onSurface.copy(alpha = 0.85f)
    val label = when {
        checking -> AboutStr.heroChecking()
        hasUpdate -> AboutStr.newVersionFound()
        else -> AboutStr.heroNoUpdate()
    }
    val containerColor = if (actionable) {
        colorScheme.primary.copy(
            alpha = AppMaterialSettings.solidSurfaceAlpha(if (isLightTheme) 0.72f else 0.62f)
        )
    } else if (MiuixTheme.isDynamicColor) {
        MiuixTheme.colorScheme.surfaceContainer.copy(
            alpha = AppMaterialSettings.glassSurfaceAlpha(if (isLightTheme) 0.76f else 0.84f, isLightTheme)
        )
    } else if (isLightTheme) {
        Color(0xFFFAFAFA).copy(AppMaterialSettings.glassSurfaceAlpha(0.76f, isLight = true))
    } else {
        Color(0xFF242424).copy(AppMaterialSettings.glassSurfaceAlpha(0.84f, isLight = false))
    }
    val solidSurface: DrawScope.() -> Unit = remember(containerColor) {
        { drawRect(containerColor) }
    }
    val capsule = remember { ContinuousCapsule() }
    val effectsOn = AppMaterialSettings.glassEffectsEnabled()
    val glassBlur = AppMaterialSettings.glassBlurDp()
    val glassStyle = AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = isDark,
        // 与顶栏按钮同一个 fill：中性 = ThemeFill（按钮默认），有版本 = CustomizeFill(primary)
        fill = if (actionable) CustomizeFill(colorScheme.primary) else ThemeFill,
        tone = BrightTone,
    )
    // drawBackdrop 按引用比较这三个 lambda：每帧新建会重录采样层
    val effects: GlassEffectScope.() -> Unit = remember(effectsOn, glassBlur, glassStyle) {
        {
            if (effectsOn) {
                blur(glassBlur.dp.toPx())
                runtimeShaderEffect(
                    key = HeroPillGlassKey,
                    shaderString = SOFT_GLASS_LIVE_SHADER,
                    uniformShaderName = "content",
                ) {
                    applySoftGlass(style = glassStyle, width = size.width, height = size.height)
                }
            }
        }
    }
    val surfaceModifier = if (glass != null) {
        Modifier.drawBackdrop(
            backdrop = glass,
            shape = { capsule },
            effects = effects,
            layerBlock = {},
            // 与顶栏按钮一致：开 shader 时表面全交给 fill（再垫底色会把折射盖住）；
            // 无材质档不跑 shader，必须留容器色，否则胶囊变成透空
            onDrawSurface = if (effectsOn) null else solidSurface,
        )
    } else {
        Modifier
            .clip(capsule)
            .background(containerColor)
    }

    Row(
        modifier = surfaceModifier
            .clickable(
                enabled = actionable,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (actionable) FontWeight.Medium else FontWeight.Normal,
            color = tint,
            maxLines = 1,
        )
    }
}

/** 底部「更新日志 ›」：整块圆角点击区，别让手指只能点到那几个字。 */
@Composable
private fun ChangelogLink(onClick: () -> Unit) {
    val summary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        modifier = Modifier
            .clip(ContinuousRoundedRectangle(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = AboutStr.changelog(),
            fontSize = 15.sp,
            color = summary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(7.dp))
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = summary
        )
    }
}

@Composable
private fun HeroCardBack(
    mode: HeroBackMode,
    entry: ChangelogEntry?,
    release: UpdateRelease?,
    version: String,
    interactive: Boolean,
    onOpenChangelog: () -> Unit,
    onStartUpdate: () -> Unit,
) {
    when (mode) {
        HeroBackMode.Current -> CurrentChangelogBack(
            entry = entry,
            version = version,
            interactive = interactive,
            onOpenChangelog = onOpenChangelog,
        )

        HeroBackMode.Update -> NewVersionBack(
            release = release,
            interactive = interactive,
            onStartUpdate = onStartUpdate,
        )
    }
}

/** 背面（当前版本）：标题 + 版本/日期/条数 + 日志条目 + 查看全部。 */
@Composable
private fun CurrentChangelogBack(
    entry: ChangelogEntry?,
    version: String,
    interactive: Boolean,
    onOpenChangelog: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val onCard = colorScheme.onSurface
    val changes = entry?.changes.orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        // 上半：标题 + 条目，仍按 24dp 内缩
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp)
        ) {
            Text(
                text = AboutStr.changelog(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = onCard,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append(displayVersion(version))
                        if (entry != null) append(" · ").append(entry.date)
                    },
                    fontSize = 12.5.sp,
                    letterSpacing = 0.4.sp,
                    color = onCard.copy(alpha = 0.42f),
                    maxLines = 1,
                )
                if (changes.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(onCard.copy(alpha = 0.22f))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = AboutStr.changelogItemCount(changes.size),
                        fontSize = 12.5.sp,
                        color = onCard.copy(alpha = 0.42f),
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 条目区吃掉中间的弹性空间，写不下时可以滚（卡片高度是固定的）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column {
                    changes.forEach { change ->
                        Row(modifier = Modifier.padding(bottom = 11.dp)) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(colorScheme.primary.copy(alpha = 0.65f))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = change(),
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = onCard.copy(alpha = 0.80f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 分割线仍在 24dp 内缩的内容区里（不动它），只有下面那一行通到卡片两边
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(onCard.copy(alpha = 0.10f))
            )
        }
        // 底部入口：整宽、无圆角裁切，行自己的 padding 就是它和分割线的距离
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 不裁圆角：整行通到底，圆角交给卡片自身的 clip
                .clickable(
                    enabled = interactive,
                    onClick = onOpenChangelog,
                )
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AboutStr.viewFullChangelog(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colorScheme.primary
            )
        }
    }
}

/**
 * 更新说明轻量清洗：去掉井号标题与星号加粗，列表标记换成 `·`，丢空行。
 * 服务端发的是 markdown 原文，直接当纯文本画会满屏井号和星号。
 */
private fun prettyReleaseBody(raw: String): String =
    raw.lineSequence()
        .map { it.trim().replace("**", "") }
        .filter { it.isNotEmpty() }
        .map { line ->
            when {
                line.startsWith("- ") || line.startsWith("* ") -> "· " + line.substring(2).trim()
                line.startsWith("#") -> line.trimStart('#').trim()
                else -> line
            }
        }
        .joinToString("\n")
        .trim()

/** 背面（新版本）：标题 + 新版本号/日期 + 更新说明 + 开始更新。 */
@Composable
private fun NewVersionBack(
    release: UpdateRelease?,
    interactive: Boolean,
    onStartUpdate: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val onCard = colorScheme.onSurface
    val body = prettyReleaseBody(release?.body.orEmpty())

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp)
        ) {
            Text(
                text = AboutStr.newVersionFound(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = onCard,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append(displayVersion(release?.tagName.orEmpty()))
                        val date = release?.createdAt.orEmpty()
                        if (date.isNotBlank()) append(" · ").append(date)
                    },
                    fontSize = 12.5.sp,
                    letterSpacing = 0.4.sp,
                    color = onCard.copy(alpha = 0.42f),
                    maxLines = 1,
                )
                if (body.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(onCard.copy(alpha = 0.22f))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = AboutStr.changelogItemCount(body.lineSequence().count { it.isNotBlank() }),
                        fontSize = 12.5.sp,
                        color = onCard.copy(alpha = 0.42f),
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (body.isEmpty()) AboutStr.updateHasNoNotes() else body,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = onCard.copy(alpha = 0.80f),
                    maxLines = 11,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(onCard.copy(alpha = 0.10f))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = interactive,
                    onClick = onStartUpdate,
                )
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AboutStr.startDownload(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = colorScheme.primary
            )
        }
    }
}

/** 版本号胶囊（主色浅底 + 主色字），正面右上角。 */
@Composable
private fun VersionChip(label: String) {
    val tint = MiuixTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(ContinuousRoundedRectangle(11.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            color = tint,
            maxLines = 1,
        )
    }
}
