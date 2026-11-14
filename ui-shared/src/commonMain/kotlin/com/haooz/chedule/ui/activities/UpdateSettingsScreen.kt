/** 应用更新页面 - Screen（更新能力经 UpdateController 注入） */
package com.haooz.chedule.ui.activities

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
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
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.data.ChangelogEntry
import com.haooz.chedule.ui.data.changelogData
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.HostNavigationState
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiiPx
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.about_app_icon
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
import com.haooz.softglass.platform.isRenderEffectSupported
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.NavigationBackHandlerFor
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor

/** 版本卡宽高比（宽 / 高）：横向短卡，和设置页里的普通卡片同呼吸，不做海报式竖版。 */
private const val HeroCardAspect = 1.0f

/** 版本卡平板端宽度上限：不做满宽，避免短卡被拉成一条横带。 */
private val HeroCardMaxWidth = 440.dp

/** 版本卡圆角（dp）：也是它翻成更新日志页时的起始圆角。 */
private const val HeroCardCornerDp = 20f

/**
 * 翻转角：整张卡绕自身横轴翻 180°
 */
private const val HeroFlipDegrees = 180f

/** 翻转时下潜的深度（相对卡片高度）：带一点明显的下潜惯性再翻上来。 */
private const val HeroFlipDipRatio = 0.5f

/** 缩放在 0.4 起步、1.0 收尾：这段和翻转的后半程叠在一起，中间没有停顿。 */
private const val HeroGrowStart = 0.4f
private const val HeroGrowEnd = 1f

/** 展开相位：0.4 之前恒为 0，之后平滑长到 1；翻转仍由总进度本身驱动。 */
private fun heroGrowProgress(p: Float): Float {
    val t = ((p - HeroGrowStart) / (HeroGrowEnd - HeroGrowStart)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 翻到背面后额外增加的高度比例，给更新日志更多空间。 */
private const val HeroOpenHeightRatio = 0.12f

/** 相机距离（乘以 density 转成像素）：越小透视越猛 */
private const val HeroFlipCamera = 40f

/** 开卡动效时长（位移 + 翻转同一段）。 */
private const val HeroOpenMotionMs = 520

/** 收卡动效时长：回程略快，点外面时手感更利落。 */
private const val HeroCloseMotionMs = 380

/** 状态胶囊的柔光 shader 缓存键（尺寸与其它玻璃件不同，不能共用 uniform）。 */
private const val HeroPillGlassKey = "NexioSoftGlassLiveHeroPill"

/** 今日日期 `yyyy-MM-dd`（手写零填充；⚠ 与迁移前逐字一致，勿改）。 */
private fun todayIsoDate(): String {
    val date = todayLocalDate()
    return date.year.toString().padStart(4, '0') +
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
    // 版本号如果是构建时间戳（2025-11-14T21:37:50+08:00），只留日期部分
    val raw = version.trim().substringBefore('T').trim()
    val first = raw.firstOrNull() ?: return raw
    return if (first.isDigit()) "v$raw" else raw
}

/**
 * 更新日志卡片翻开期间，顶栏（返回 / 更多）要让位。
 *
 * 卡片浮在页面内容里，而顶栏是 [com.haooz.chedule.ui.components.DocumentPageScaffold]
 * 后画的一层，天然压在卡片之上：不拦的话点击会穿到返回 / 更多上，把整页退掉。
 * 路由层持有本对象，脚手架读 [blocked] 决定是否拦；页面在翻开 / 收起时更新状态。
 */
class UpdateHeroChromeState {
    var blocked by mutableStateOf(false)

    /** 点到被拦住的顶栏区域时执行：与点遮罩同义，退出卡片 */
    var onBlockedTap: (() -> Unit)? = null

    fun dismiss() {
        onBlockedTap?.invoke()
    }
}

@Composable
fun UpdateSettingsScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: GlassBackdrop? = null,
    /** 更新能力控制器（Android 注入 `AndroidUpdateController`；桌面/iOS 用 [NoopUpdateController]）。 */
    updateController: UpdateController = NoopUpdateController,
    /** 从版本卡翻进完整更新日志：把卡片当前矩形与内容交出去，由路由层在同一位置翻成整页 */
    onOpenChangelog: (UpdateChangelogOrigin) -> Unit = {},
    /** 日志页已接管屏幕：列表里的卡留坑不画，避免与翻出去的那张重影 */
    heroHidden: Boolean = false,
    /** 页面级模糊进度（dp） */
    pageBlur: Animatable<Float, AnimationVector1D>? = null,
    /** 翻开更新日志时接管顶栏点击；与路由层的脚手架共享同一份状态 */
    heroChrome: UpdateHeroChromeState = remember { UpdateHeroChromeState() },
) {
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { AppStorage.store("update_settings") }
    // 液态玻璃效果的透明下拉颜色

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

    // 版本卡「新版本打开态」：离位 → 浮到屏幕中央翻转出新版本说明。
    // 更新日志走另一条路：卡片直接在原位翻成日志页（见 UpdateChangelogMorphLayer），不再浮起来。
    var heroOpen by remember { mutableStateOf(false) }
    var heroMounted by remember { mutableStateOf(false) }
    // 落定后才允许点背面按钮：
    var heroSettled by remember { mutableStateOf(false) }
    val heroEntry = remember(currentVersion) { changelogFor(currentVersion) }
    /** 有新版本、拿到了 release、且当前不在检查中，才算「可看新版本日志」 */
    val heroHasUpdate = hasUpdate && latestRelease != null && !isChecking
    val heroRectRef = remember { HeroRectRef() }
    val heroProgress = remember { Animatable(0f) }
    LaunchedEffect(heroOpen) {
        if (heroOpen) {
            heroMounted = true
            heroSettled = false
            // ⚠ 必须在这里置位：heroMounted 只在 Scaffold 内容作用域里被读，外层组合不会跟着重组，
            // 写在 SideEffect 里会一直停留在初值，顶栏拦截就永远不生效。
            heroChrome.blocked = true
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
            heroChrome.blocked = false
            launch {
                pageBlur?.animateTo(0f, tween(HeroCloseMotionMs, easing = FastOutSlowInEasing))
            }
            heroProgress.animateTo(0f, tween(HeroCloseMotionMs, easing = FastOutSlowInEasing))
            heroMounted = false
        }
    }
    // 点到被拦住的顶栏区域等同点遮罩
    SideEffect {
        heroChrome.onBlockedTap = { heroOpen = false }
    }

    // 系统返回先退卡片，而不是把整页退掉
    NavigationBackHandlerFor(
        enabled = heroOpen,
        onBackCompleted = { heroOpen = false },
        onBackCancelled = {},
    )

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
                                hidden = heroMounted || heroHidden,
                                onBoundsChanged = { heroRectRef.rect = it },
                                onOpenChangelog = {
                                    val rect = heroRectRef.rect
                                    if (rect != null) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        onOpenChangelog(
                                            UpdateChangelogOrigin(
                                                rect = rect,
                                                version = currentVersion,
                                                entry = heroEntry,
                                                hasUpdate = heroHasUpdate,
                                                checking = isChecking,
                                            )
                                        )
                                    }
                                },
                                onOpenUpdate = {
                                    if (heroRectRef.rect != null && heroHasUpdate) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
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
                        hasUpdate = heroHasUpdate,
                        checking = isChecking,
                        interactive = heroSettled,
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

/**
 * 列表里的版本卡：只画正面、只负责「点我」。
 *
 * 真正的翻转不在这里发生——卡片要先离位浮到屏幕中央，所以静态面与浮层面是两套节点，
 * 中间靠 [onBoundsChanged] 记录的矩形对齐（浮层从同一位置起跳，视觉上就是同一张卡）。
 *
 * 翻成更新日志页时也一样：卡片要先跳到遮罩之上（否则下潜时会被下面的按钮盖住），
 * 所以那一张由 [UpdateChangelogMorphLayer] 按同一矩形重画，这里只留坑。
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
    // 首帧轻轻抬起：只动内层，外层矩形保持稳定，开卡起点不会跟着抖
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier
            .widthIn(max = HeroCardMaxWidth)
            .fillMaxWidth()
            .aspectRatio(HeroCardAspect)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    // 浮层打开时留坑不画：位置不能变，否则关回来会错位
                    alpha = if (hidden) 0f else enter.value
                    translationY = (1f - enter.value) * 6.dp.toPx()
                    scaleX = 0.99f + 0.01f * enter.value
                    scaleY = 0.99f + 0.01f * enter.value
                }
        ) {
            HeroCardFace(sheenOnEnter = true, highlighted = hasUpdate) { glass ->
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
}

/**
 * 打开态的浮层卡：从列表里的矩形位置滑到屏幕垂直居中，同时整卡翻 180°。
 * 轨迹只保留一点下潜惯性（`sin(πp)`，两端速度为 0），不做夸张的 U 型绕场。
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
    hasUpdate: Boolean = false,
    checking: Boolean = false,
    interactive: Boolean = true,
    onStartUpdate: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val densityScope = LocalDensity.current
    val cardWidth = with(densityScope) { rect.width.toDp() }
    val startTop = rect.top
    val dipPx = rect.height * HeroFlipDipRatio
    val endHeightPx = rect.height * (1f + HeroOpenHeightRatio)
    // 高度随翻面进度平滑长高；布局和位移用同一条曲线，卡片中心不会跳
    fun heightAt(p: Float): Float {
        val eased = p * p * (3f - 2f * p)
        return rect.height + (endHeightPx - rect.height) * eased
    }

    Box(
        modifier = modifier
            .width(cardWidth)
            .layout { measurable, constraints ->
                val h = heightAt(progress()).roundToInt()
                val placeable = measurable.measure(
                    constraints.copy(minHeight = h, maxHeight = h)
                )
                layout(placeable.width, h) {
                    placeable.place(0, 0)
                }
            }
            .graphicsLayer {
                val p = progress()
                val endTop = (rootHeightPx - heightAt(p)) / 2f
                translationY = startTop + (endTop - startTop) * p +
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
            HeroCardFace(highlighted = hasUpdate) { glass ->
                HeroCardFront(
                    version = version,
                    entry = entry,
                    // ⚠ 别用 `release != null`：「检查更新」即使没新版本也可能回一个 release，
                    // 那样翻面过程中正面会假报「发现新版本」
                    hasUpdate = hasUpdate,
                    checking = checking,
                    glass = glass,
                    // 从列表卡接着翻面：正面内容必须保持可见，不能重跑进场淡入
                    animateIn = false,
                    // 翻开态里正面只在前半程露脸，点它等于把卡翻回去
                    onOpenChangelog = onClose,
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
                NewVersionBack(
                    release = release,
                    interactive = interactive,
                    onStartUpdate = onStartUpdate,
                )
            }
        }
    }
}

/**
 * 展开中的日志页可见区域：从卡片大小长到整屏，圆角同步收到屏幕圆角（不是直角）。
 *
 * 页面本身按整屏布局、由外层图层整体缩放，所以裁切发生在这之前——
 * 可见高度与圆角都要先除以当前缩放，落到屏幕上才是「卡片 → 整屏」的那条矩形。
 */
private class ChangelogRevealShape(
    private val grow: () -> Float,
    private val cardWidth: Float,
    private val cardHeight: Float,
    private val screenWidth: Float,
    private val screenCorners: ScreenCornerRadiiPx,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val grown = heroGrowProgress(grow())
        val scale = (cardWidth + (screenWidth - cardWidth) * grown) / screenWidth
        val height = (cardHeight + (size.height - cardHeight) * grown) / scale
        val cardRadius = with(density) { HeroCardCornerDp.dp.toPx() }
        // 收尾是屏幕圆角；读不到屏幕圆角（直角屏 / 模拟器）时以卡片圆角兜底，不会收成直角
        val corners = screenCorners
            .coerceAtLeast(cardRadius)
            .interpolateFrom(cardRadius, grown)
            .scaled(1f / scale)
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = height,
                topLeftCornerRadius = CornerRadius(corners.topLeft, corners.topLeft),
                topRightCornerRadius = CornerRadius(corners.topRight, corners.topRight),
                bottomRightCornerRadius = CornerRadius(corners.bottomRight, corners.bottomRight),
                bottomLeftCornerRadius = CornerRadius(corners.bottomLeft, corners.bottomLeft),
            )
        )
    }
}

/**
 * 被点开的那张版本卡：矩形 + 当时画在卡上的内容。
 *
 * 翻转要发生在遮罩之上（否则下潜时会被下面的按钮盖住），而遮罩在路由层，
 * 所以交给路由层按同一矩形重画正面——它仍然是被点开的那张卡，不是另一份页面。
 */
class UpdateChangelogOrigin(
    val rect: Rect,
    val version: String,
    val entry: ChangelogEntry?,
    val hasUpdate: Boolean,
    val checking: Boolean,
)

/**
 * 更新页 → 更新日志页：同一张卡翻着长成同一个页面。
 *
 * 正面按 [UpdateChangelogOrigin] 的矩形重画（压在遮罩之上，下潜时不会被列表下面的按钮盖住），
 * 背面就是真正的 [ChangelogScreen]：先按整屏布局，再缩到卡片矩形，随同一个进度翻进来并长满整屏。
 * 背景压暗与模糊也读同一个进度，所以三者是同一条时间线上的连续运动。
 * p = 1 时它就是全屏页面本身（自带顶栏与返回），全程没有路由跳转，也没有第二份页面内容接棒。
 */
@Composable
internal fun UpdateChangelogMorphLayer(
    origin: UpdateChangelogOrigin,
    flip: () -> Float,
    grow: () -> Float,
    pageReady: Boolean = true,
    onPageLaidOut: () -> Unit = {},
    onCardReady: () -> Unit = {},
    onClose: () -> Unit,
) {
    val cardRect = origin.rect
    val densityScope = LocalDensity.current
    val screenCorners = rememberScreenCornerRadiiPx()
    // 展开到位后才补齐日志页剩余条目：点开那一刻只排首屏那几条，预加载才够快
    val settled by remember { derivedStateOf { heroGrowProgress(grow()) >= 0.999f } }
    // 翻过 90° 正面那张卡已经看不见了，必须卸载：它压在日志页之上，
    // 留着会吃掉那块区域的点击与滑动（点一下直接退出日志页）
    val frontCardGone by remember { derivedStateOf { flip() >= 0.5f } }
    // 翻转期间背景压暗：与连贯动画同款（progress × 0.5，上限 0.5）
    val dimColor = ComposeColor.Black
    var originInRoot by remember { mutableStateOf<Offset?>(null) }

    // 翻开的日志页先回它自己：返回键把页面翻回卡片，而不是退出更新页
    NavigationBackHandlerFor(
        enabled = true,
        onBackCompleted = onClose,
        onBackCancelled = {},
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { originInRoot = it.positionInRoot() }
    ) {
        // 首帧还没量到位置时按 (0,0) 画：更新页是整屏路由，本来就重合；
        // 宁可差一帧也不能空一帧，否则卡片会在第一帧消失。
        val layerOrigin = originInRoot ?: Offset.Zero
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val screenWidthDp = with(densityScope) { screenWidth.toDp() }
        val screenHeightDp = with(densityScope) { screenHeight.toDp() }
        val cardWidth = cardRect.width
        val cardHeight = cardRect.height
        val cardWidthDp = with(densityScope) { cardWidth.toDp() }
        val cardHeightDp = with(densityScope) { cardHeight.toDp() }
        val revealShape = remember(cardWidth, cardHeight, screenWidth, screenCorners) {
            ChangelogRevealShape(grow, cardWidth, cardHeight, screenWidth, screenCorners)
        }

        // 背景压暗：跟着翻转进度连续加深，同时替更新页接住点击（返回 / 更多 / 列表都点不到）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (flip() * 0.5f).coerceIn(0f, 0.5f) }
                .background(dimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                )
        ) {}

        val startLeft = cardRect.left - layerOrigin.x
        val startTop = cardRect.top - layerOrigin.y
        val cardCenterX = startLeft + cardWidth / 2f
        val cardCenterY = startTop + cardHeight / 2f

        // 页面按整屏布局（只量一次），每帧只改图层的缩放 / 位移 / 翻转与裁切。
        // pageReady 之前不挂整页：点击那一帧只画卡与遮罩，翻转不必等整页组合完
        if (pageReady) {
            Box(
                modifier = Modifier
                    .requiredSize(screenWidthDp, screenHeightDp)
                    .onGloballyPositioned {
                        // 整页排完第一帧才放动画（路由层据此开跑）
                        onPageLaidOut()
                    }
                    .graphicsLayer {
                        val p = flip()
                        val grown = heroGrowProgress(grow())
                        val scale = (cardWidth + (screenWidth - cardWidth) * grown) / screenWidth
                        // 缩放在裁切之前执行：可见高度要除以缩放，落到屏幕上才是那条矩形
                        val visibleHeight =
                            (cardHeight + (screenHeight - cardHeight) * grown) / scale
                        // 中心跟着展开一起从卡片中心滑向屏幕中心
                        val centerX = cardCenterX + (screenWidth / 2f - cardCenterX) * grown
                        // 与正面同一条 U 型轨迹：先下潜，再翻上来
                        val dip = cardHeight * HeroFlipDipRatio * sin(PI.toFloat() * p)
                        val centerY =
                            cardCenterY + (screenHeight / 2f - cardCenterY) * grown + dip
                        cameraDistance = HeroFlipCamera * density
                        // 翻转轴心 = 可见区域中心（也就是卡片中心），不是整屏中心
                        transformOrigin = TransformOrigin(
                            0.5f,
                            visibleHeight / 2f / screenHeight,
                        )
                        scaleX = scale
                        scaleY = scale
                        translationX = centerX - screenWidth / 2f
                        translationY = centerY - visibleHeight / 2f
                        // 与正面同一条时间线：正面 0°→90° 翻走，背面 180°→360° 翻回来
                        rotationX = HeroFlipDegrees * (1f + p)
                        alpha = if (p > 0.5f) 1f else 0f
                    }
                    .clip(revealShape)
                    // 整页自己接住点击：铺开后点空白处不该再落到下面的遮罩上退出。
                    // 必须放在 graphicsLayer 之内，触摸区域才会跟着翻转/缩放的那条矩形走
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                DocumentPageScaffold(
                    title = ChromeStr.changelog(),
                    onBack = onClose,
                ) { scrollBehavior, _ ->
                    ChangelogScreen(scrollBehavior = scrollBehavior, settled = settled)
                }
            }
        }

        // 正面：被点开的那张卡，压在遮罩与日志页之上（所以下潜时不会被下面的按钮盖住）
        if (!frontCardGone) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(startLeft.roundToInt(), startTop.roundToInt()) }
                    .size(cardWidthDp, cardHeightDp)
                    .onGloballyPositioned {
                        // 这一层的卡真正上屏之后才让列表里那张隐身：否则首帧玻璃还没出图，
                        // 会先看到卡凭空消失一下
                        onCardReady()
                    }
                    .graphicsLayer {
                        val p = flip()
                        cameraDistance = HeroFlipCamera * density
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        rotationX = HeroFlipDegrees * p
                        // 与背面同一条 U 型轨迹
                        translationY = cardHeight * HeroFlipDipRatio * sin(PI.toFloat() * p)
                        alpha = if (p < 0.5f) 1f else 0f
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    )
            ) {
                HeroCardFace(highlighted = origin.hasUpdate) { glass ->
                    HeroCardFront(
                        version = origin.version,
                        entry = origin.entry,
                        hasUpdate = origin.hasUpdate,
                        checking = origin.checking,
                        glass = glass,
                        animateIn = false,
                        onOpenChangelog = onClose,
                        onOpenUpdate = {},
                    )
                }
            }
        }
    }
}

/**
 * 卡片表面：中性底色 + 顶部微光 + 一次掠过的玻璃高光 + 细描边。
 * 正反两面共用，翻面时跟着一起转；不再叠加主色渐变、光斑和巨型水印。
 */
@Composable
private fun HeroCardFace(
    sheenOnEnter: Boolean = false,
    highlighted: Boolean = false,
    content: @Composable (glass: GlassBackdrop?) -> Unit,
) {
    // 与设置页普通卡片同圆角，视觉上属于同一套器件
    val shape = remember { ContinuousRoundedRectangle(20.dp) }
    val isDark = isAppDarkTheme()
    val baseCardColor = CardDefaults.defaultColors().color
    val primary = MiuixTheme.colorScheme.primary
    // 淡蓝底：纯白上微光几乎不可见，给卡片一点色相才能读出高光扫过
    val cardColor = remember(baseCardColor, primary, isDark) {
        lerp(baseCardColor, primary, if (isDark) 0.12f else 0.08f)
    }
    val onCard = MiuixTheme.colorScheme.onSurface
    val glassOn = true
    val cardBackdrop = rememberLayerBackdrop { drawContent() }
    val recordKey = remember(isDark, cardColor) { listOf(isDark, cardColor) }
    // 首次进场一道高光掠过；之后每隔约 9 秒再轻轻扫一次，让玻璃像在呼吸环境光
    val sheen = remember { Animatable(0f) }
    LaunchedEffect(sheenOnEnter) {
        if (sheenOnEnter) {
            sheen.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
            while (true) {
                delay(8000.milliseconds)
                sheen.snapTo(0f)
                sheen.animateTo(1f, tween(1200, easing = LinearEasing))
            }
        } else {
            sheen.snapTo(1f)
        }
    }
    // 课表格线跟随首次高光逐条显影
    val gridReveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        gridReveal.animateTo(1f, tween(1000, delayMillis = 100, easing = LinearEasing))
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        // 顶部一层极淡的白，给平面一点玻璃的受光感
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = if (isDark) 0.045f else 0.06f),
                                0.55f to Color.Transparent,
                            )
                        )
                        // 隐约的课表格线：从右上角向卡片内部渐隐，给平面一点专属质感
                        val gridBrush = Brush.linearGradient(
                            colors = listOf(
                                onCard.copy(alpha = if (isDark) 0.05f else 0.035f),
                                Color.Transparent,
                            ),
                            start = Offset(size.width, 0f),
                            end = Offset(size.width * 0.34f, size.height * 0.92f),
                        )
                        val gridLeft = size.width * 0.46f
                        val gridBottom = size.height * 0.78f
                        val step = 20.dp.toPx()
                        // 顺着首次高光的方向逐条显影：竖线从左到右，横线从上到下
                        fun lineReveal(t: Float): Float =
                            ((gridReveal.value - t * 0.45f) / 0.55f).coerceIn(0f, 1f)
                        var gridX = gridLeft
                        while (gridX < size.width) {
                            val t = ((gridX - gridLeft) / (size.width - gridLeft)).coerceIn(0f, 1f)
                            drawLine(
                                brush = gridBrush,
                                start = Offset(gridX, 0f),
                                end = Offset(gridX, gridBottom),
                                strokeWidth = 0.6.dp.toPx(),
                                alpha = lineReveal(t),
                            )
                            gridX += step
                        }
                        var gridY = 0f
                        while (gridY < gridBottom) {
                            drawLine(
                                brush = gridBrush,
                                start = Offset(gridLeft, gridY),
                                end = Offset(size.width, gridY),
                                strokeWidth = 0.6.dp.toPx(),
                                alpha = lineReveal((gridY / gridBottom).coerceIn(0f, 1f)),
                            )
                            gridY += step
                        }
                        if (sheenOnEnter && sheen.value < 1f) {
                            val band = size.width * 0.55f
                            val startX = -band + (size.width + band * 2f) * sheen.value
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = if (isDark) 0.06f else 0.10f),
                                        Color.Transparent,
                                    ),
                                    startX = startX,
                                    endX = startX + band,
                                )
                            )
                        }
                        // 顶边高光：玻璃件的上沿，比四周描边亮一点
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = if (isDark) 0.16f else 0.40f),
                                    Color.Transparent,
                                )
                            ),
                            start = Offset(size.width * 0.06f, 0.5.dp.toPx()),
                            end = Offset(size.width * 0.94f, 0.5.dp.toPx()),
                            strokeWidth = 0.8.dp.toPx(),
                        )
                    }
                    .border(
                        width = 0.8.dp,
                        color = onCard.copy(alpha = if (isDark) 0.10f else 0.06f),
                        shape = shape,
                    )
            )
            content(if (glassOn) cardBackdrop else null)
        }
        // 有新版本时，外圈主色描边缓慢呼吸，作为「待处理」的提示
        if (highlighted) {
            val pulse = rememberInfiniteTransition(label = "updateBorderPulse")
            val borderAlpha by pulse.animateFloat(
                initialValue = 0.12f,
                targetValue = 0.32f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "updateBorderAlpha",
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 1.dp,
                        color = MiuixTheme.colorScheme.primary.copy(alpha = borderAlpha),
                        shape = shape,
                    )
            )
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
    animateIn: Boolean = true,
    onOpenChangelog: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val onCard = MiuixTheme.colorScheme.onSurface
    val summary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val heroNumber = majorMinorVersion(version)
    // 内容比卡片晚半拍落定：先看到卡，再看到版本号浮上来。
    // 但浮层卡是「同一张卡接着翻面」，不是新进场：必须直接落到终态，
    // 否则版本号 / 日期 / 「共 N 项」会在点下去的瞬间重新从透明淡入，看起来就是立刻消失。
    val lift = remember(animateIn) { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(animateIn) {
        if (animateIn) {
            lift.animateTo(1f, tween(520, delayMillis = 80, easing = FastOutSlowInEasing))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroMonogram()
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AboutStr.appName(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                    color = onCard.copy(alpha = 0.90f),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayVersion(version),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                    color = summary,
                    maxLines = 1,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.graphicsLayer {
                alpha = lift.value
                translationY = (1f - lift.value) * 8.dp.toPx()
            }
        ) {
            if (heroNumber != null) {
                AnimatedHeroVersion(heroNumber = heroNumber, color = onCard, animateIn = animateIn)
            } else {
                Text(
                    text = displayVersion(version),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    lineHeight = 34.sp,
                    color = onCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry != null) {
                Spacer(modifier = Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.date,
                        fontSize = 12.sp,
                        letterSpacing = 0.4.sp,
                        color = onCard.copy(alpha = 0.42f),
                        maxLines = 1,
                    )
                    if (entry.changes.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(onCard.copy(alpha = 0.22f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AboutStr.changelogItemCount(entry.changes.size),
                            fontSize = 12.sp,
                            color = onCard.copy(alpha = 0.42f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 底部一行：左边看状态（柔光玻璃胶囊），右边进更新日志
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UpdateStatusAction(
                hasUpdate = hasUpdate,
                checking = checking,
                glass = glass,
                onClick = onOpenUpdate,
            )
            Spacer(modifier = Modifier.weight(1f))
            ChangelogLink(onClick = onOpenChangelog)
        }
    }
}

/**
 * 版本号遮罩上浮：数字从裁切框底部升起，按 30ms 错峰。
 * 只用于 major.minor 形态，避免逐字排版影响完整版本号的可读性。
 */
@Composable
private fun AnimatedHeroVersion(heroNumber: String, color: Color, animateIn: Boolean = true) {
    val chars = remember(heroNumber) { heroNumber.toList() }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "V ",
            fontSize = 23.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = color.copy(alpha = 0.34f),
            modifier = Modifier.alignByBaseline(),
        )
        chars.forEachIndexed { index, ch ->
            val rise = remember(heroNumber, index, animateIn) {
                Animatable(if (animateIn) 0f else 1f)
            }
            LaunchedEffect(heroNumber, index, animateIn) {
                if (animateIn) {
                    rise.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 440,
                            delayMillis = 120 + index * 30,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
            }
            Text(
                text = ch.toString(),
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.5).sp,
                color = color,
                modifier = Modifier
                    .alignByBaseline()
                    // 先裁到自身边界，再在内部做位移，形成从下方升起的遮罩
                    .clipToBounds()
                    .graphicsLayer {
                        translationY = (1f - rise.value) * 72.dp.toPx()
                        alpha = rise.value
                    },
            )
        }
    }
}

/**
 * 左上角应用图标：直接使用打包的应用图标，裁成和卡片同一套连续圆角。
 * 加一圈极细描边，避免浅色模式下图标和卡片底色糊在一起。
 */
@Composable
private fun HeroMonogram() {
    val shape = remember { ContinuousRoundedRectangle(11.dp) }
    val isDark = isAppDarkTheme()
    val outline = if (isDark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .border(width = 0.8.dp, color = outline, shape = shape),
    ) {
        Image(
            painter = painterResource(Res.drawable.about_app_icon),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
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

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(90),
        label = "heroStatusPress",
    )

    Row(
        modifier = surfaceModifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                enabled = actionable,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(
            pulsing = checking,
            tint = when {
                actionable -> Color.White
                checking -> colorScheme.primary
                else -> colorScheme.onSurface.copy(alpha = 0.30f)
            },
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (actionable) FontWeight.Medium else FontWeight.Normal,
            color = tint,
            maxLines = 1,
        )
    }
}

/**
 * 状态点：检查中向外扩散一圈涟漪，有新版本时常亮，其余状态压低存在感。
 * 三态都占同样的 5dp 宽度，切换时胶囊宽度不会跳。
 */
@Composable
private fun StatusDot(
    pulsing: Boolean,
    tint: Color,
) {
    Box(
        modifier = Modifier.size(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (pulsing) {
            val transition = rememberInfiniteTransition(label = "heroStatusPulse")
            val pulse by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "heroStatusPulseRing",
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .graphicsLayer {
                        val scale = 1f + pulse * 1.8f
                        scaleX = scale
                        scaleY = scale
                        alpha = (1f - pulse) * 0.42f
                    }
                    .clip(CircleShape)
                    .background(tint)
            )
        }
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(tint)
        )
    }
}

/** 底部「更新日志 ›」：整块圆角点击区，别让手指只能点到那几个字。 */
@Composable
private fun ChangelogLink(onClick: () -> Unit) {
    val summary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        modifier = Modifier
            .clip(ContinuousRoundedRectangle(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = AboutStr.changelog(),
            fontSize = 13.5.sp,
            color = summary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = summary
        )
    }
}

/**
 * 更新说明轻量清洗：去掉井号标题与星号加粗，列表标记换成 `·`，丢空行。
 * 服务端发的是 markdown 原文，直接当纯文本画会满屏井号和星号。
 */
private fun prettyReleaseBody(raw: String): String =
    raw.lineSequence()
        .map { it.trim().replace("**", "") }
        .filter { it.isNotEmpty() }.joinToString("\n") { line ->
            when {
                line.startsWith("- ") || line.startsWith("* ") -> "· " + line.substring(2).trim()
                line.startsWith("#") -> line.trimStart('#').trim()
                else -> line
            }
        }
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
                .padding(start = 22.dp, end = 22.dp, top = 20.dp)
        ) {
            Text(
                text = AboutStr.newVersionFound(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = onCard,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append(displayVersion(release?.tagName.orEmpty()))
                        val date = release?.createdAt.orEmpty()
                        if (date.isNotBlank()) append(" · ").append(date)
                    },
                    fontSize = 12.sp,
                    letterSpacing = 0.4.sp,
                    color = onCard.copy(alpha = 0.42f),
                    maxLines = 1,
                )
                if (body.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(onCard.copy(alpha = 0.22f))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AboutStr.changelogItemCount(body.lineSequence().count { it.isNotBlank() }),
                        fontSize = 12.sp,
                        color = onCard.copy(alpha = 0.42f),
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = body.ifEmpty { AboutStr.updateHasNoNotes() },
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    color = onCard.copy(alpha = 0.80f),
                    maxLines = 9,
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
                .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 18.dp),
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

