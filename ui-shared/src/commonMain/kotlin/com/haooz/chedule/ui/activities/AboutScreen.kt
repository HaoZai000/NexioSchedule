package com.haooz.chedule.ui.activities

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.platform.DiagnosticRecorder
import com.haooz.chedule.data.platform.NoopDiagnosticRecorder
import com.haooz.chedule.data.platform.PrivacyConsent
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.AboutStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.components.rememberTopBarMaterialAlpha
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.data.changelogData
import com.haooz.chedule.ui.effects.background.BgEffectBackground
import com.haooz.chedule.ui.effects.miuix.rememberBlurBackdrop
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.hostVerticalBarEdge
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.ic_gitee
import com.haooz.chedule.uishared.resources.ic_github
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 关于页展示的最近几条更新日志。
 *
 * 提到文件级：`changelogData.take(3)` 每次组合都新建一个 List，而调用点用它当
 * 展开状态表的长度依据。
 */
private val RecentChangelog = changelogData.take(3)

/** 顶部留白高度（图标/名称/版本号区域下方） */
private val LogoSpacerHeight = 340.dp

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.backdrops.LayerBackdrop,
    embedded: Boolean = false,
    /** 弹窗的玻璃采样层：内嵌时传全屏层，使弹窗能采样到左侧内容 */
    dialogBackdrop: GlassBackdrop? = null,
    /**
     * 打开子页面（更新日志 / 开源协议 / 隐私政策 / 交流与反馈 / 赞赏作者）。
     *
     * 这五页全部是路由，**由宿主决定怎么跳**：
     * - 手机：`router.navigate(...)` 压全屏路由；
     * - 平板右栏内嵌（`SettingsDirectPane`）：同样压栈，返回后仍回到分栏。
     *
     * 默认空实现只为签名兼容；调用点都应显式传。
     */
    onNavigate: (AppRoute) -> Unit = {},
    /** 应用版本号（宿主从 packageManager 取）；桌面/iOS 用各自来源 */
    appVersion: String = AboutStr.unknownVersion(),
    /** 应用名 */
    appName: String = AboutStr.appName(),
    /** 应用图标（已裁边的 ImageBitmap）；null 不显示（回退为首字母文本） */
    appIcon: ImageBitmap? = null,
    /** 撤回隐私同意后退出应用（Android = activity.finishAffinity）；桌面/iOS 空实现 */
    onExitApp: () -> Unit = {},
    /** 诊断录制控制器（Android = CrashLogHelper 包装）；桌面/iOS 用 NoopDiagnosticRecorder */
    diagnosticRecorder: DiagnosticRecorder = NoopDiagnosticRecorder,
    appIconShape: Shape = RectangleShape,
) {
    // Embedded panes keep their own chrome even when an ancestor has a native page.
    val nativeChrome = LocalNativeDocumentChrome.current.takeUnless { embedded }
    val nativeBarInset = (nativeChrome?.barInset ?: 0.0).dp
    val tintArgb = MiuixTheme.colorScheme.primary.toArgb().toLong()
    SideEffect {
        nativeChrome?.title = AboutStr.aboutApp()
        nativeChrome?.tintArgb = tintArgb
    }

    // 弹窗默认跟随自身玻璃层；内嵌时由外层指定全屏层
    val dialogGlass: GlassBackdrop = dialogBackdrop ?: liquidGlassBackdrop
    val hapticFeedback = LocalHapticFeedback.current
    val scrollBehavior = rememberSharedScrollBehavior()
    val uriHandler = LocalUriHandler.current
    val isInDark = isAppDarkTheme()
    val layoutDirection = LocalLayoutDirection.current
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 4.dp else 0.dp

    val backdrop = rememberBlurBackdrop()
    // 无材质档玻璃全关：卡片退回标准实色，纹理模糊一并停用（采样层此时没挂，采不到东西）
    val noneTier = AppMaterialSettings.isNoneTier()
    val glassCards = backdrop != null && !noneTier
    val lazyListState = rememberLazyListState()
    val uiScope = rememberCoroutineScope()
    var recordingActive by remember { mutableStateOf(diagnosticRecorder.isRecording) }
    var canShareRecording by remember { mutableStateOf(diagnosticRecorder.hasReadyRecording) }
    var recordingElapsedSec by remember { mutableStateOf(0) }
    var exportingCrashLog by remember { mutableStateOf(false) }

    // 恢复上次被中断的录制：Application 启动已在后台做，这里兜底等待一次再读状态
    // （ensureRecovered 是磁盘恢复，保持在 IO 调度器上；commonMain 无 Dispatchers.IO，用 :core 的 ioDispatcher）
    LaunchedEffect(Unit) {
        withContext(ioDispatcher) {
            diagnosticRecorder.ensureRecovered()
        }
        recordingActive = diagnosticRecorder.isRecording
        canShareRecording = diagnosticRecorder.hasReadyRecording
        recordingElapsedSec = (diagnosticRecorder.recordingElapsedMs() / 1000L).toInt()
    }

    // 录制倒计时；到 30 分钟自动结束并点亮分享
    LaunchedEffect(recordingActive) {
        if (!recordingActive) return@LaunchedEffect
        while (recordingActive) {
            diagnosticRecorder.ensureRecordingNotExpired()
            recordingActive = diagnosticRecorder.isRecording
            canShareRecording = diagnosticRecorder.hasReadyRecording
            recordingElapsedSec = (diagnosticRecorder.recordingElapsedMs() / 1000L).toInt()
            if (!diagnosticRecorder.isRecording) break
            kotlinx.coroutines.delay(1000)
        }
        canShareRecording = diagnosticRecorder.hasReadyRecording
    }

    // 顶部留白高度：spacer 与滚动进度共用同一个来源，别再两处各写一个 340
    val logoSpacerHeightPx = with(LocalDensity.current) { LogoSpacerHeight.toPx() }
    val scrollProgress by remember(lazyListState, logoSpacerHeightPx) {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                // 只读 index + offset 两个 Int：读 layoutInfo 会在每次滚动时拉一份可见项列表，
                // 而首项恒为 logoSpacer，分母本身就是常量
                (lazyListState.firstVisibleItemScrollOffset / logoSpacerHeightPx).coerceIn(0f, 1f)
            }
        }
    }
    
    // 背景动效常开（没有任何入口会关它）。用 mutableStateOf 只会让 BgEffectBackground 的
    // LaunchedEffect key 与每次读取都多一层快照开销
    val dynamicBackground = true
    var showRepoDialog by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }

    val logoBlend = remember(isInDark) {
        if (isInDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }

    val cardBlend = remember(isInDark) {
        if (isInDark) {
            listOf(
                BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
                BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
                BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
            )
        }
    }

    Scaffold(
        modifier = Modifier.consumeWindowInsets(PaddingValues(top = nativeBarInset)),
        topBar = {
            // 内嵌到 pad 设置页右栏时，About 仍自绘顶栏糊层/遮罩/标题，仅隐藏返回按钮
            ProgressiveBlurTopBar(
                backdrop = liquidGlassBackdrop,
                topInsetAdjustment = nativeBarInset,
                noTopStatusBar = hostVerticalBarEdge(),
                // 无材质档没有模糊层：遮罩跟着返回按钮的材质一起出现（本页按 scrollProgress 才上材质）
                maskAlpha = rememberTopBarMaterialAlpha(scrollProgress >= 1f),
            ) {
                CollapsibleTopAppBar(
                    title = AboutStr.aboutApp(),
                    largeTitle = AboutStr.aboutApp(),
                    showLargeTitle = false,
                    showSmallTitle = scrollProgress > 0.5f,
                    showShadow = scrollProgress >= 1f,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    scrollBehavior = scrollBehavior,
                    contentPadding = {},
                    startAction = if (embedded || nativeChrome != null) null else { backdropAlpha, shadowAlpha ->
                        LiquidTopBarButton(
                            onClick = { onBack() },
                            backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.ChevronBackward,
                            contentDescription = CommonStr.back(),
                            performHapticFeedback = false,
                            iconSize = 24.dp,
                            iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                            backdropAlpha = backdropAlpha,
                            shadowAlpha = shadowAlpha,
                        )
                    },
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .then(
                    // 无材质档：玻璃全关，采样录屏层不挂
                    if (noneTier) Modifier
                    else Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                )
        ) {
            BgEffectBackground(
                dynamicBackground = dynamicBackground,
                isFullSize = true,
                modifier = Modifier.fillMaxSize(),
                // 背景特效采的是内容模糊录屏层，无材质档一并停录
                bgModifier = if (backdrop != null && !noneTier) {
                    Modifier.layerBackdrop(backdrop)
                } else Modifier,
                alpha = { 1f - scrollProgress },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = innerPadding.calculateTopPadding() + 72.dp,
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 应用图标由宿主注入（Android 宿主负责 drawable→Bitmap→裁边→ImageBitmap）；
                    // null 时回退为首字母文本
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer {
                                val iconProgress =
                                    ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                                alpha = 1 - iconProgress
                                scaleX = 1 - (iconProgress * 0.05f)
                                scaleY = 1 - (iconProgress * 0.05f)
                            }
                    ) {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = null,
                                modifier = Modifier.size(88.dp).clip(appIconShape),
                            )
                        } else {
                            Text(
                                text = appName.take(1),
                                color = MiuixTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 42.sp,
                            )
                        }
                    }
                    Text(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 5.dp)
                            .graphicsLayer {
                                val nameProgress =
                                    ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
                                alpha = 1 - nameProgress
                                scaleX = 1 - (nameProgress * 0.05f)
                                scaleY = 1 - (nameProgress * 0.05f)
                            }
                            .then(
                                if (glassCards) {
                                    Modifier.textureBlur(
                                        backdrop = backdrop,
                                        shape = ContinuousRoundedRectangle(16.dp),
                                        blurRadius = 150f,
                                        colors = BlurDefaults.blurColors(blendColors = logoBlend),
                                        contentBlendMode = ComposeBlendMode.DstIn,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        text = appName,
                        color = MiuixTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 35.sp,
                    )
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val verProgress =
                                    ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                                alpha = 1 - verProgress
                                scaleX = 1 - (verProgress * 0.05f)
                                scaleY = 1 - (verProgress * 0.05f)
                            },
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        text = "v$appVersion",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .scrollEndHaptic(
                            hapticFeedbackType = HapticFeedbackType.TextHandleMove
                        )
                        .then(
                            Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                        ),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() +
                                if (WindowInsets.statusBars.asPaddingValues()
                                        .calculateTopPadding() - nativeBarInset > 0.dp
                                ) (-8).dp else (-20).dp,
                        start = innerPadding.calculateStartPadding(layoutDirection) + tabletHorizontalPadding,
                        end = innerPadding.calculateEndPadding(layoutDirection) + tabletHorizontalPadding,
                    ),
                ) {
                    item(key = "logoSpacer") {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(LogoSpacerHeight),
                        )
                    }
                    item(key = "about") {
                        Card(
                            modifier = Modifier
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                                .then(
                                    if (glassCards) {
                                        Modifier.textureBlur(
                                            backdrop = backdrop,
                                            shape = ContinuousRoundedRectangle(20.dp),
                                            blurRadius = 60f,
                                            colors = BlurDefaults.blurColors(blendColors = cardBlend),
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                            colors = if (glassCards) {
                                CardDefaults.defaultColors(Color.Transparent, Color.Transparent)
                            } else {
                                // 无材质档：卡片标准实色
                                CardDefaults.defaultColors()
                            },
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 内嵌到平板设置右栏时也保留：两个入口都走路由，压栈后返回仍回到分栏
                                ArrowPreference(
                                    title = AboutStr.communicationFeedback(),
                                    onClick = {
                                        onNavigate(AppRoute.Communication)
                                    }
                                )
                                ArrowPreference(
                                    title = AboutStr.projectRepo(),
                                    onClick = {
                                        showRepoDialog = true
                                    }
                                )
                                ArrowPreference(
                                    title = AboutStr.donate(),
                                    endActions = {
                                        Text(
                                            text = AboutStr.buyCoffee(),
                                            fontSize = 14.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    onClick = {
                                        onNavigate(AppRoute.AppreciateAuthor)
                                    }
                                )
                            }
                        }
                    }

                    item(key = "changelog") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .then(
                                    if (glassCards) {
                                        Modifier.textureBlur(
                                            backdrop = backdrop,
                                            shape = ContinuousRoundedRectangle(20.dp),
                                            blurRadius = 60f,
                                            colors = BlurDefaults.blurColors(blendColors = cardBlend),
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                            colors = if (glassCards) {
                                CardDefaults.defaultColors(Color.Transparent, Color.Transparent)
                            } else {
                                // 无材质档：卡片标准实色
                                CardDefaults.defaultColors()
                            },
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onNavigate(AppRoute.Changelog)
                                        }
                                        .padding(
                                            start = 16.dp,
                                            end = 13.dp,
                                            top = 17.dp,
                                            bottom = 12.dp
                                        ),
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
                                        imageVector = MiuixIcons.Basic.ArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                }
                                val recentChangelog = RecentChangelog
                                // 一次 remember 拿整张状态表：原来在 List{} 的 lambda 里逐个 remember，
                                // 位置依赖 size，changelog 一变就会把展开态错位到别的版本上
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
                                                expandedStates[index].value =
                                                    !expandedStates[index].value
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
                                                    .graphicsLayer {
                                                        rotationZ = rotations[index]
                                                    },
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
                                                    MiuixTheme.colorScheme.onSurfaceVariantActions.copy(
                                                        alpha = 0.07f
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "thanks") {
                        Column(
                            modifier = Modifier
                                .padding(
                                    bottom = WindowInsets.navigationBars.asPaddingValues()
                                        .calculateBottomPadding()
                                )
                                .fillParentMaxHeight(),
                        ) {
                            var expanded by remember { mutableStateOf(true) }
                            val rotation by animateFloatAsState(
                                targetValue = if (expanded) 90f else -90f,
                                animationSpec = tween(durationMillis = 200),
                                label = "thanksRotation"
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .clip(ContinuousRoundedRectangle(20.dp))
                                    .clickable { expanded = !expanded }
                                    .then(
                                        if (glassCards) {
                                            Modifier.textureBlur(
                                                backdrop = backdrop,
                                                shape = ContinuousRoundedRectangle(20.dp),
                                                blurRadius = 60f,
                                                colors = BlurDefaults.blurColors(blendColors = cardBlend),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                                colors = if (glassCards) {
                                    CardDefaults.defaultColors(Color.Transparent, Color.Transparent)
                                } else {
                                    // 无材质档：卡片标准实色
                                    CardDefaults.defaultColors()
                                },
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = 18.dp,
                                            end = 18.dp,
                                            top = 20.dp,
                                            bottom = 17.dp
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = AboutStr.specialThanks(),
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = MiuixIcons.ChevronForward,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .graphicsLayer {
                                                    rotationZ = rotation
                                                },
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    CardExpand(visible = expanded) {
                                        Column {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Miuix",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.primary,
                                                    modifier = Modifier.clickable {
                                                        uriHandler.openUri("https://github.com/compose-miuix-ui/miuix")
                                                    }
                                                )
                                                Text(
                                                    text = "Yukonga",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "OkHttp",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.primary,
                                                    modifier = Modifier.clickable {
                                                        uriHandler.openUri("https://github.com/square/okhttp")
                                                    }
                                                )
                                                Text(
                                                    text = "Square",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "warehouse",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.primary,
                                                    modifier = Modifier.clickable {
                                                        uriHandler.openUri("https://github.com/XingHeYuZhuan/shiguang_warehouse")
                                                    }
                                                )
                                                Text(
                                                    text = "XingHeYuZhuan",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Shizuku",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.primary,
                                                    modifier = Modifier.clickable {
                                                        uriHandler.openUri("https://github.com/RikkaApps/Shizuku")
                                                    }
                                                )
                                                Text(
                                                    text = "RikkaApps",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "SoftGlass",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.primary,
                                                    modifier = Modifier.clickable {
                                                        uriHandler.openUri("https://github.com/HaoZai000/softglass")
                                                    }
                                                )
                                                Text(
                                                    text = "HaoZai000",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .then(
                                        if (glassCards) {
                                            Modifier.textureBlur(
                                                backdrop = backdrop,
                                                shape = ContinuousRoundedRectangle(20.dp),
                                                blurRadius = 60f,
                                                colors = BlurDefaults.blurColors(blendColors = cardBlend),
                                            )
                                        } else {
                                            Modifier
                                        }
                                    ),
                                colors = if (glassCards) {
                                    CardDefaults.defaultColors(Color.Transparent, Color.Transparent)
                                } else {
                                    // 无材质档：卡片标准实色
                                    CardDefaults.defaultColors()
                                },
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 16.dp)
                                ) {
                                    Text(
                                        text = AboutStr.diagRecording(),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    val remainSec =
                                        ((diagnosticRecorder.maxRecordMs / 1000L) - recordingElapsedSec)
                                            .coerceAtLeast(0)
                                    val mm = remainSec / 60
                                    val ss = remainSec % 60
                                    val statusText = when {
                                        recordingActive ->
                                            // 跨平台替代 String.format("%02d")（checkKmpPurity 拦截 + Locale 字形问题）
                                            AboutStr.recordingRemaining("${mm}:${ss.toString().padStart(2, '0')}")
                                        canShareRecording ->
                                            when (diagnosticRecorder.readyReason) {
                                                "crash" -> AboutStr.diagCrashSaved()
                                                "process_killed" -> AboutStr.diagKilled()
                                                "timeout_30min" -> AboutStr.diagTimeout()
                                                else -> AboutStr.diagEnded()
                                            }
                                        else ->
                                            AboutStr.diagIdleHint()
                                    }
                                    Text(
                                        text = statusText,
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        TextButton(
                                            text = if (recordingActive) AboutStr.diagStop() else AboutStr.diagStart(),
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                                exportingCrashLog = true
                                                uiScope.launch {
                                                    val ok = withContext(ioDispatcher) {
                                                        if (diagnosticRecorder.isRecording) {
                                                            diagnosticRecorder.stopRecording("manual")
                                                        } else {
                                                            diagnosticRecorder.startRecording()
                                                        }
                                                    }
                                                    exportingCrashLog = false
                                                    recordingActive = diagnosticRecorder.isRecording
                                                    canShareRecording = diagnosticRecorder.hasReadyRecording
                                                    recordingElapsedSec =
                                                        (diagnosticRecorder.recordingElapsedMs() / 1000L).toInt()
                                                    showToast(when {
                                                            !ok && recordingActive -> AboutStr.diagAlreadyRecording()
                                                            ok && recordingActive -> AboutStr.diagStarted()
                                                            ok && !recordingActive -> AboutStr.diagEndedShort()
                                                            else -> AboutStr.opFailedRetry()
                                                        })
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            enabled = !exportingCrashLog,
                                            colors = if (recordingActive) {
                                                ButtonDefaults.textButtonColorsPrimary()
                                            } else {
                                                ButtonDefaults.textButtonColors()
                                            }
                                        )
                                        TextButton(
                                            text = AboutStr.share(),
                                            onClick = {
                                                if (!canShareRecording) return@TextButton
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                                exportingCrashLog = true
                                                uiScope.launch {
                                                    val ok = withContext(ioDispatcher) {
                                                        diagnosticRecorder.shareReadyRecording()
                                                    }
                                                    exportingCrashLog = false
                                                    showToast(if (ok) AboutStr.shareOpened() else AboutStr.shareFailedRetry())
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            enabled = canShareRecording && !exportingCrashLog,
                                            colors = if (canShareRecording) {
                                                ButtonDefaults.textButtonColorsPrimary()
                                            } else {
                                                ButtonDefaults.textButtonColors()
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.defaultColors(
                                    color = Color.Transparent,
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = AboutStr.copyright(),
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                        // 开源协议入口：应用内展示 AGPL-3.0 全文，与隐私政策入口同构
                                        Text(
                                            text = AboutStr.license(),
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable {
                                                    onNavigate(AppRoute.License)
                                                }
                                                .padding(start = 4.dp)
                                        )
                                    }
                                    // 隐私政策入口 + 撤回同意：用户可随时查阅政策或撤回授权
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                        modifier = Modifier.padding(top = 6.dp)
                                    ) {
                                        Text(
                                            text = AboutStr.privacyPolicy(),
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.clickable {
                                                onNavigate(AppRoute.PrivacyPolicy)
                                            }
                                        )
                                        Text(
                                            text = AboutStr.revokeConsent(),
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            modifier = Modifier.clickable {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                showRevokeDialog = true
                                            }
                                        )
                                    }
                                    // APP 备案号：按工信部要求在「关于」页面显著位置展示
                                    Text(
                                        text = AboutStr.icpNumber(),
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }


        OverlayDialog(
            title = AboutStr.projectRepo(),
            show = showRepoDialog,
            liquidGlassBackdrop = dialogGlass,
            onDismissRequest = { showRepoDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(ContinuousRoundedRectangle(18.dp))
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                uriHandler.openUri("https://github.com/HaoZai000/NexioSchedule")
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Image(
                            modifier = Modifier.size(48.dp),
                            painter = painterResource(Res.drawable.ic_github),
                            contentDescription = "GitHub",
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "GitHub",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(ContinuousRoundedRectangle(18.dp))
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                uriHandler.openUri("https://gitee.com/com_haooz_account/hyper_schedule")
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Image(
                            modifier = Modifier.size(48.dp),
                            painter = painterResource(Res.drawable.ic_gitee),
                            contentDescription = "Gitee"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Gitee",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                TextButton(
                    text = CommonStr.done(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showRepoDialog = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 撤回隐私政策同意：确认后清空同意状态并关闭整个应用
        OverlayDialog(
            title = AboutStr.revokeDialogTitle(),
            summary = AboutStr.revokeDialogSummary(),
            show = showRevokeDialog,
            liquidGlassBackdrop = dialogGlass,
            onDismissRequest = { showRevokeDialog = false }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showRevokeDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = AboutStr.revokeAndExit(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        PrivacyConsent.revoke()
                        showRevokeDialog = false
                        onExitApp()
                    },
                    textColor = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

