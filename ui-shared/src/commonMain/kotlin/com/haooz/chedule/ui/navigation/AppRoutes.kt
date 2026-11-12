package com.haooz.chedule.ui.navigation

import com.haooz.chedule.ui.components.NoopWatchSyncController
import com.haooz.chedule.ui.components.WatchSyncController
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.backup.BackupFileController
import com.haooz.chedule.data.backup.NoopBackupFileController
import com.haooz.chedule.data.backup.NoopScheduleFileController
import com.haooz.chedule.data.backup.ScheduleFileController
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.platform.DiagnosticRecorder
import com.haooz.chedule.data.platform.FeedbackKind
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.NoopDiagnosticRecorder
import com.haooz.chedule.data.platform.NoopReminderController
import com.haooz.chedule.data.platform.NoopUpdateController
import com.haooz.chedule.data.platform.ReminderController
import com.haooz.chedule.data.platform.UpdateController
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.FeedbackStr
import com.haooz.chedule.i18n.HolidayStr
import com.haooz.chedule.i18n.WeatherStr
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AiImportScreen
import com.haooz.chedule.ui.activities.AppreciateAuthorScreen
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.CalendarSyncResult
import com.haooz.chedule.ui.activities.ChangelogScreen
import com.haooz.chedule.ui.activities.CommunicationScreen
import com.haooz.chedule.ui.activities.CourseManageScreen
import com.haooz.chedule.ui.activities.CourseReminderScreen
import com.haooz.chedule.ui.activities.CourseTimeSettingsScreen
import com.haooz.chedule.ui.activities.EduImportUpdater
import com.haooz.chedule.ui.activities.EducationalImportScreen
import com.haooz.chedule.ui.activities.FeedbackHistoryScreen
import com.haooz.chedule.ui.activities.FeedbackRoadmapScreen
import com.haooz.chedule.ui.activities.FeedbackScreen
import com.haooz.chedule.ui.activities.HolidaySettingsScreen
import com.haooz.chedule.ui.activities.LicenseScreen
import com.haooz.chedule.ui.activities.LocalBackupScreen
import com.haooz.chedule.ui.activities.PreferenceSettingsScreen
import com.haooz.chedule.ui.activities.PrivacyPolicyScreen
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.activities.TimeConfigCardBounds
import com.haooz.chedule.ui.activities.UpdatePreferencesScreen
import com.haooz.chedule.ui.activities.UpdateSettingsScreen
import com.haooz.chedule.ui.activities.WebDavSettingsScreen
import com.haooz.chedule.ui.activities.WebImportScreen
import com.haooz.chedule.ui.activities.WidgetCustomizeOverlay
import com.haooz.chedule.ui.activities.WidgetCustomizeRequest
import com.haooz.chedule.ui.activities.WidgetIntroScreen
import com.haooz.chedule.ui.activities.main.RenderSettingsPane
import com.haooz.chedule.ui.activities.main.SettingsPaneArgs
import com.haooz.chedule.ui.activities.rememberFeedbackFormState
import com.haooz.chedule.ui.activities.weather.WeatherScreen
import com.haooz.chedule.ui.basic.LiquidGlassDropdownMenu
import com.haooz.chedule.ui.basic.LiquidGlassDropdownMenuItem
import com.haooz.chedule.ui.basic.LiquidGlassDropdownShadowPadding
import com.haooz.chedule.ui.basic.LiquidGlassTextButton
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ShortcutMenu
import com.haooz.chedule.ui.basic.ShortcutMenuItem
import com.haooz.chedule.ui.components.CapsuleSegmentedControl
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.components.HorizontalPageSwitch
import com.haooz.chedule.ui.components.rememberPageSwitchState
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.screens.CourseEditScreen
import com.haooz.chedule.ui.screens.ScheduleSettingsScreen
import com.haooz.chedule.ui.screens.SettingsSearchHeader
import com.haooz.chedule.ui.screens.TimeConfigEditScreen
import com.haooz.chedule.ui.screens.rememberSettingsSearchState
import com.haooz.chedule.ui.theme.rememberAppThemeController
import com.haooz.chedule.ui.utils.LocalForcedDarkTheme
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.NativeDocumentAction
import com.haooz.chedule.ui.utils.NativeDocumentActionEffect
import com.haooz.chedule.ui.utils.NativeDocumentOverlayEffect
import com.haooz.chedule.ui.utils.ShareCodeFetchResult
import com.haooz.chedule.ui.utils.ShiftModeRequest
import com.haooz.chedule.ui.utils.ThirdPartySharePayload
import com.haooz.chedule.ui.utils.ThirdPartyShareSource
import com.haooz.chedule.ui.utils.TransitionLayerImage
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.isIosPlatform
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiiPx
import com.haooz.chedule.ui.web.WebHost
import com.haooz.chedule.ui.web.WebHostConfig
import com.haooz.chedule.ui.web.WebHostEvents
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import com.haooz.chedule.viewmodel.shiftViewModel
import com.haooz.chedule.weather.WeatherStore
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.platform.isRenderEffectSupported
import com.haooz.softglass.shape.addContinuousRoundRect
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.NavigationBackHandlerFor

/**
 * 路由宿主的跨平台部分：路由跳转、状态接线、Updater 订阅，平台能力一律参数注入。
 *
 * ⚠ 本文件不能改名 `AppRouteContent.kt`：`:androidApp` 同包已有一个同名文件，两边会生成同一 facade 类 `AppRouteContentKt`，编译期静默通过，但 D8/R8 打 dex 时同名 class 冲突只留一个，本文件路由全部 `NoSuchMethodError`，Android/iOS 都闪退。往 `:ui-shared` 加文件前先查 `:androidApp` 同包有无同名 `.kt`；Android 侧同名包装加 `Android` 前缀（如 `AndroidWebHost`）。
 */

// ── 路由间共用的结构件 ──

/**
 * 纯文档壳页面：标题 + 返回 + 只消费 scrollBehavior 的正文。
 *
 * 这类页面没有平台触点，`:androidApp` 直接调对应路由，不需要额外包装。
 */
@Composable
private fun DocumentRoute(
    title: String,
    router: AppRouter,
    content: @Composable (scrollBehavior: SharedScrollBehavior) -> Unit,
) {
    DocumentPageScaffold(title = title, onBack = { router.popBack() }) { scrollBehavior, _ ->
        content(scrollBehavior)
    }
}

/** 分段控件在标题下方 8dp；平板两侧 20dp，手机 16dp。 */
@Composable
private fun segmentControlModifier(): Modifier =
    Modifier
        .fillMaxWidth()
        .padding(top = 8.dp)
        .padding(horizontal = if (isTabletWidth()) 20.dp else 16.dp)

/** 底部主操作按钮：贴底居中，左右比正文再收 16dp，并避开导航栏。 */
@Composable
private fun BoxScope.BottomActionButton(
    text: String,
    backdrop: GlassBackdrop,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val horizontalPadding = if (isTabletWidth()) 20.dp else 16.dp
    LiquidGlassTextButton(
        text = text,
        enabled = enabled,
        backdrop = backdrop,
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(
                start = horizontalPadding + 16.dp,
                end = horizontalPadding + 16.dp,
            )
            .navigationBarsPadding()
            .padding(bottom = 20.dp),
    )
}

/**
 * 顶栏刷新按钮：载入中换成转圈，否则是 28dp 图标的玻璃按钮。
 *
 * [backdrop] 必须传页面真实采样层 —— 空层渲染出来只是一颗纯色圆，没有玻璃效果。
 */
@Composable
private fun RefreshTopBarAction(
    loading: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    backdrop: GlassBackdrop,
    backdropAlpha: Float,
    shadowAlpha: Float,
) {
    if (loading) {
        Box(
            modifier = Modifier
                // 转圈比图标小一圈，左移上移做视觉居中
                .offset(x = (-6).dp, y = (-4).dp)
                .size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
            )
        }
    } else {
        LiquidTopBarButton(
            onClick = onClick,
            backdrop = backdrop,
            icon = icon,
            contentDescription = contentDescription,
            iconSize = 28.dp,
            backdropAlpha = backdropAlpha,
            shadowAlpha = shadowAlpha,
        )
    }
}

/**
 * 教务导入路由（学校 / 适配器选择）。
 *
 * @param isInFreeformWindow Android 读 `Activity.isInMultiWindowMode`；桌面/iOS 没有自由窗口概念，传 false。
 */
@Composable
fun EducationalImportRoute(
    router: AppRouter,
    isInFreeformWindow: Boolean = false,
) {
    // 进入页面即触发脚本索引更新（原 EducationalImportActivity.onCreate 时机，7 天节流）
    LaunchedEffect(Unit) { EduImportUpdater.startUpdateIfNeeded() }
    val isUpdating by EduImportUpdater.isUpdating.collectAsState()
    val dataVersion by EduImportUpdater.dataVersion.collectAsState()

    EducationalImportScreen(
        isInFreeformWindow = isInFreeformWindow,
        isUpdating = isUpdating,
        dataVersion = dataVersion,
        onForceUpdate = { EduImportUpdater.forceUpdate() },
        onFinish = { router.popBack() },
        // 用路由自己的 router（不用 LocalAppRouter：iOS 原生文档页容器不 provide 它）
        onOpenWebImport = { router.navigate(AppRoute.WebImport) },
    )
}

/**
 * 网页导入路由：教务导入第二步，独立一层从右侧推入（返回先回学校选择页）。
 * 选中项由 `EduImportSelection` 带入。
 */
@Composable
fun WebImportRoute(
    router: AppRouter,
    webHostFactory: @Composable (WebHostConfig, WebHostEvents) -> WebHost,
) {
    WebImportScreen(
        webHostFactory = webHostFactory,
        onFinish = { router.popBack() },
    )
}

/**
 * 关于页路由。版本号 / 应用名 / 应用图标 / 退出应用四个平台值全部参数注入。
 *
 * Android 从 `packageManager` / `Activity.finishAffinity()` 取，iOS 从 `NSBundle` 取，桌面给常量。
 */
@Composable
fun AboutRoute(
    router: AppRouter,
    appVersion: String = ChromeStr.unknownVersion(),
    appName: String = ChromeStr.appName(),
    appIcon: ImageBitmap? = null,
    onExitApp: () -> Unit = {},
    diagnosticRecorder: DiagnosticRecorder = NoopDiagnosticRecorder,
    appIconShape: Shape = RectangleShape,
) {
    AboutScreen(
        onBack = { router.popBack() },
        liquidGlassBackdrop = rememberLayerBackdrop(),
        onNavigate = { router.navigate(it) },
        appVersion = appVersion,
        appName = appName,
        appIcon = appIcon,
        appIconShape = appIconShape,
        onExitApp = onExitApp,
        diagnosticRecorder = diagnosticRecorder,
    )
}

/** 更新日志路由。 */
@Composable
fun ChangelogRoute(router: AppRouter) {
    DocumentRoute(title = ChromeStr.changelog(), router = router) { scrollBehavior ->
        ChangelogScreen(scrollBehavior = scrollBehavior)
    }
}

/** 开源协议路由。 */
@Composable
fun LicenseRoute(router: AppRouter) {
    DocumentRoute(title = ChromeStr.openSourceLicenses(), router = router) { scrollBehavior ->
        LicenseScreen(scrollBehavior = scrollBehavior)
    }
}

/** 隐私政策路由。 */
@Composable
fun PrivacyPolicyRoute(router: AppRouter) {
    DocumentRoute(title = ChromeStr.privacyPolicy(), router = router) { scrollBehavior ->
        PrivacyPolicyScreen(scrollBehavior = scrollBehavior)
    }
}

/** 问题反馈路由。 */
@Composable
fun CommunicationRoute(router: AppRouter) {
    DocumentPageScaffold(title = ChromeStr.communityFeedback(), onBack = { router.popBack() }) { scrollBehavior, _ ->
        CommunicationScreen(
            scrollBehavior = scrollBehavior,
            onOpenFeedback = { router.navigate(AppRoute.Feedback) },
            onOpenRoadmap = { router.navigate(AppRoute.FeedbackRoadmap) },
        )
    }
}

/** 应用内反馈路由。 */
@Composable
fun FeedbackRoute(router: AppRouter) {
    val state = rememberFeedbackFormState()
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    DocumentPageScaffold(
        title = FeedbackStr.title(),
        onBack = { router.popBack() },
        // 类型选择挂在标题下方：顶栏下方的兄弟层，随大标题高度变化上移
        headerOverlay = { backdrop, scrollBehavior ->
            if (state.submittedId == null) {
                CapsuleSegmentedControl(
                    options = listOf(FeedbackStr.typeBug(), FeedbackStr.typeFeature()),
                    selectedIndex = state.kind.ordinal,
                    onSelect = { i -> state.kind = if (i == 0) FeedbackKind.Bug else FeedbackKind.Feature },
                    backdrop = backdrop,
                    scrollBehavior = scrollBehavior,
                    modifier = segmentControlModifier(),
                )
            }
        },
        overlay = { backdrop ->
            // 成功态由页面内的卡片给出口，底部不再压一个提交按钮
            if (state.submittedId == null) {
                BottomActionButton(
                    text = if (state.submitting) FeedbackStr.submitting() else FeedbackStr.submit(),
                    enabled = state.canSubmit,
                    backdrop = backdrop,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        if (state.validateEmail()) {
                            scope.launch {
                                state.submit().onFailure { error ->
                                    showToast(error.message ?: FeedbackStr.loadFailed())
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { scrollBehavior, _ ->
        FeedbackScreen(
            state = state,
            scrollBehavior = scrollBehavior,
            onOpenHistory = { router.navigate(AppRoute.FeedbackHistory) },
            onOpenRoadmap = { router.navigate(AppRoute.FeedbackRoadmap) },
            // 头部控件占掉的高度 + 间距
            topExtra = 64.dp,
        )
    }
}

/** 我的反馈路由。 */
@Composable
fun FeedbackHistoryRoute(router: AppRouter) {
    DocumentPageScaffold(
        title = FeedbackStr.historyTitle(),
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        FeedbackHistoryScreen(
            scrollBehavior = scrollBehavior,
            onWriteFeedback = { router.navigate(AppRoute.Feedback) },
            // 确认弹窗要接材质，需要页面采样层
            liquidGlassBackdrop = backdrop,
        )
    }
}

/** 开发进度路由。 */
@Composable
fun FeedbackRoadmapRoute(router: AppRouter) {
    var roadmapFilter by remember { mutableStateOf<FeedbackKind?>(null) }

    DocumentPageScaffold(
        title = FeedbackStr.roadmapTitle(),
        onBack = { router.popBack() },
        headerOverlay = { backdrop, scrollBehavior ->
            CapsuleSegmentedControl(
                // 0 = 全部，1 = 新功能，2 = Bug 修复
                options = listOf(
                    FeedbackStr.filterAll(),
                    FeedbackStr.filterFeature(),
                    FeedbackStr.filterBug(),
                ),
                selectedIndex = when (roadmapFilter) {
                    null -> 0
                    FeedbackKind.Feature -> 1
                    FeedbackKind.Bug -> 2
                },
                onSelect = { i ->
                    roadmapFilter = when (i) {
                        0 -> null
                        1 -> FeedbackKind.Feature
                        else -> FeedbackKind.Bug
                    }
                },
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
                modifier = segmentControlModifier(),
            )
        },
    ) { scrollBehavior, _ ->
        FeedbackRoadmapScreen(
            scrollBehavior = scrollBehavior,
            filter = roadmapFilter,
            topExtra = 64.dp,
        )
    }
}

/** 捐赠支持路由。 */
@Composable
fun AppreciateAuthorRoute(router: AppRouter) {
    DocumentRoute(title = ChromeStr.donate(), router = router) { scrollBehavior ->
        AppreciateAuthorScreen(scrollBehavior = scrollBehavior)
    }
}

/**
 * 天气详情路由。
 *
 * 零平台触点：数据由 [WeatherStore] 提供（今日卡先落地），定位权限与刷新都在数据层，
 * 本页只画。Android / iOS 都能直接调这一个函数。
 */
@Composable
fun WeatherRoute(router: AppRouter) {
    // recordKey 不能省：layerBackdrop 在 recordKey == null 时会每帧把整页录进图层
    //（滚动时整页画两遍 + 顶栏玻璃每帧重采样）。指纹只需覆盖会让被采样像素变化的东西：
    // 数据、载入态、主题底色；折叠动画由脚手架自己的 mustRecord 负责。
    val snapshot by WeatherStore.snapshot.collectAsState()
    val loading by WeatherStore.loading.collectAsState()
    val surface = MiuixTheme.colorScheme.surface
    val recordKey = remember(snapshot, loading, surface) { listOf(snapshot, loading, surface) }
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = { scope.launch { WeatherStore.requestRefresh() } }

    // iOS 原生文档页的顶栏由 Swift 画，Compose 顶栏看不到，因此把同一动作发布给原生 chrome。
    NativeDocumentActionEffect(
        NativeDocumentAction(
            symbol = "arrow.clockwise",
            label = CommonStr.update(),
            enabled = !loading,
            busy = loading,
            onClick = refresh,
        )
    )

    DocumentPageScaffold(
        title = WeatherStr.title(),
        onBack = { router.popBack() },
        recordKey = recordKey,
        endAction = { backdropAlpha, shadowAlpha, backdrop ->
            RefreshTopBarAction(
                loading = loading,
                icon = MiuixIcons.Normal.Refresh,
                contentDescription = CommonStr.update(),
                onClick = refresh,
                backdrop = backdrop,
                backdropAlpha = backdropAlpha,
                shadowAlpha = shadowAlpha,
            )
        },
    ) { scrollBehavior, _ ->
        WeatherScreen(scrollBehavior = scrollBehavior)
    }
}

/**
 * 设置（二级页）。
 *
 * ⚠ 平板（rail）仍走 pager 内嵌那套，不经过这里。
 * 壳用 [DocumentPageScaffold]：返回按钮、折叠标题、页内采样层、iOS 原生 chrome 都由它给。
 *
 * @param settingsPane 宿主装配（Android = `AndroidSettingsScreen`，iOS = `IosSettingsScreen`）；
 *   null 时回落到共享 [SettingsScreen] 默认装配。
 */
@Composable
fun SettingsRoute(
    router: AppRouter,
    showUpdateSettings: Boolean = true,
    settingsPane: (@Composable (SettingsPaneArgs) -> Unit)? = null,
) {
    val shiftViewModel = shiftViewModel()
    // 本页可能从普通模式进、也可能从排班模式进，状态直接读 VM
    val isShiftMode by shiftViewModel.isShiftMode.collectAsState()
    // 设置页始终跟应用主题、不被壁纸锁深色。主题必须包住整个 DocumentPageScaffold：
    // 顶栏材质、玻璃返回按钮、页面底色都在脚手架里取色，包在内容层里会继续用外层主题。
    val settingsThemeController =
        rememberAppThemeController(isDark = isAppDarkTheme())
    // 搜索框挂在 headerOverlay（页面采样层之外），否则它自己会被录进采样层，玻璃折射到自己。
    val searchState = rememberSettingsSearchState()
    MiuixTheme(controller = settingsThemeController) {
        CompositionLocalProvider(LocalForcedDarkTheme provides null) {
            DocumentPageScaffold(
                title = CommonStr.settings(),
                onBack = { router.popBack() },
                headerOverlay = if (isShiftMode) null else { backdrop, _ ->
                    SettingsSearchHeader(state = searchState, backdrop = backdrop)
                },
            ) { scrollBehavior, backdrop ->
                RenderSettingsPane(
                    settingsPane,
                    SettingsPaneArgs(
                        viewModel = courseViewModel(),
                        scheduleViewModel = scheduleViewModel(),
                        settingsViewModel = settingsViewModel(),
                        shiftViewModel = shiftViewModel,
                        isShiftMode = isShiftMode,
                        // loading 转场与 pager 重排只能由主界面执行，这里只投递请求并退回主界面
                        onExitShiftMode = {
                            ShiftModeRequest.exit()
                            router.popBack()
                        },
                        onEnterShiftMode = {
                            ShiftModeRequest.enter()
                            router.popBack()
                        },
                        navBarStyle = "standard",
                        settingsScrollBehavior = scrollBehavior,
                        liquidGlassBackdrop = backdrop,
                        searchState = searchState,
                        showUpdateSettings = showUpdateSettings,
                    ),
                )
            }
        }
    }
}

/** 课程表。 */
@Composable
fun ScheduleSettingsRoute(router: AppRouter) {
    ScheduleSettingsScreen(router = router)
}

/**
 * 应用个性化路由。四个平台能力全部参数注入：定位权限检查 / 天气缓存清空 /
 * 最近任务卡片隐藏 / 主题切换后的系统栏刷新。
 *
 * Android 从 `ContextCompat` / `MainActivity` 取；桌面/iOS 用默认值（前三项空/false，后一项无系统栏概念）。
 */
@Composable
fun PreferenceSettingsRoute(
    router: AppRouter,
    hasLocationPermission: () -> Boolean = { false },
    onInvalidateWeatherCache: () -> Unit = {},
    onSetTaskExcludedFromRecents: (Boolean) -> Unit = {},
    onThemeDarkChanged: (Boolean) -> Unit = {},
) {
    DocumentPageScaffold(
        title = ChromeStr.appPreferences(),
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        // 本页可能改主题，系统栏要跟着刷新
        val isDark = isAppDarkTheme()
        LaunchedEffect(isDark) { onThemeDarkChanged(isDark) }
        PreferenceSettingsScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            hasLocationPermission = hasLocationPermission,
            onInvalidateWeatherCache = onInvalidateWeatherCache,
            onSetTaskExcludedFromRecents = onSetTaskExcludedFromRecents,
        )
    }
}

/**
 * 更新路由。
 *
 * @param updateController Android 注入 `AndroidUpdateController(context)`（检查/下载/APK 安装）；桌面/iOS 用默认 [NoopUpdateController]。
 */
@Composable
fun UpdateSettingsRoute(
    router: AppRouter,
    updateController: UpdateController = NoopUpdateController,
) {
    // 页面级模糊由脚手架挂在整页根节点上（才能糊到折叠标题），这里只持有进度源
    val pageBlur = remember { Animatable(0f) }
    // 右上角「更多」+ 玻璃下拉。触发器由组件自己画（收起态那颗圆），这里只维护开合状态。
    var moreOpen by remember { mutableStateOf(false) }
    val moreFraction = remember { Animatable(0f) }
    val moreSlotTopPx = remember { mutableFloatStateOf(0f) }
    val moreSlotRightPx = remember { mutableFloatStateOf(0f) }
    // 浮层自己的位置也要记：占位槽和浮层都在页面里，页面入场滑动时两者一起平移，
    // 只有「相对差值」才是稳定的。直接拿槽的 root 坐标当位移，平移会被算两遍。
    val overlayLeftPx = remember { mutableFloatStateOf(0f) }
    val overlayTopPx = remember { mutableFloatStateOf(0f) }
    val overlayWidthPx = remember { mutableFloatStateOf(0f) }
    val moreMaterialAlpha = remember { mutableFloatStateOf(1f) }
    val shadowPaddingPx = with(LocalDensity.current) { LiquidGlassDropdownShadowPadding.toPx() }
    val fallbackAnchorTopPx = with(LocalDensity.current) {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        (if (statusBarTop > 0.dp) statusBarTop - 20.dp else 17.dp).coerceAtLeast(0.dp).toPx()
    }
    DocumentPageScaffold(
        title = ChromeStr.updateSettingsTitle(),
        onBack = { router.popBack() },
        pageBlurDp = { pageBlur.value },
        endAction = { barAlpha, _, _ ->
            SideEffect { moreMaterialAlpha.floatValue = barAlpha }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .onGloballyPositioned {
                        val pos = it.positionInRoot()
                        moreSlotTopPx.floatValue = pos.y
                        moreSlotRightPx.floatValue = pos.x + it.size.width
                    }
            )
        },
        overlay = { backdrop ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (moreOpen) {
                    // 点空白收起
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { moreOpen = false }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            val pos = it.positionInRoot()
                            overlayLeftPx.floatValue = pos.x
                            overlayTopPx.floatValue = pos.y
                            overlayWidthPx.floatValue = it.size.width.toFloat()
                        }
                        .graphicsLayer {
                            clip = false
                            // 占位槽顶边减掉阴影外扩，才是菜单内容顶边（相对浮层，去掉页面平移）
                            val measured = moreSlotTopPx.floatValue - overlayTopPx.floatValue - shadowPaddingPx
                            translationY = if (measured > 0f) measured else fallbackAnchorTopPx
                        }
                        .offset {
                            IntOffset(
                                x = (
                                    moreSlotRightPx.floatValue -
                                        (overlayLeftPx.floatValue + overlayWidthPx.floatValue)
                                    ).roundToInt() + LiquidGlassDropdownShadowPadding.roundToPx(),
                                y = 0,
                            )
                        },
                    contentAlignment = Alignment.TopEnd,
                ) {
                    LiquidGlassDropdownMenu(
                        show = moreOpen,
                        backdrop = backdrop,
                        fraction = moreFraction,
                        onDismiss = { moreOpen = false },
                        onExpand = { moreOpen = true },
                        triggerIcon = MiuixIcons.More,
                        triggerContentDescription = CommonStr.more(),
                        materialAlpha = moreMaterialAlpha.floatValue,
                    ) {
                        LiquidGlassDropdownMenuItem(
                            text = ChromeStr.updatePreferencesTitle(),
                            onClick = {
                                moreOpen = false
                                router.navigate(AppRoute.UpdatePreferences)
                            },
                        )
                    }
                }
            }
        },
    ) { scrollBehavior, backdrop ->
        UpdateSettingsScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            updateController = updateController,
            pageBlur = pageBlur,
            onOpenChangelog = { router.navigate(AppRoute.Changelog) },
        )
    }
}

/** 更新设置路由（更新通道 / 下载源）。 */
@Composable
fun UpdatePreferencesRoute(router: AppRouter) {
    DocumentPageScaffold(
        title = ChromeStr.updatePreferencesTitle(),
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        UpdatePreferencesScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
        )
    }
}

/**
 * 本地备份路由。
 *
 * @param backupFileController Android 注入 `AndroidBackupFileController(context)`（SAF 选文件 + 目录扫描读写）；桌面/iOS 用默认 [NoopBackupFileController]。
 * @param onHolidayDataChanged 全量恢复成功后的平台副作用（重排提醒等），Android 宿主注入。
 */
@Composable
fun LocalBackupRoute(
    router: AppRouter,
    backupFileController: BackupFileController = NoopBackupFileController,
    onHolidayDataChanged: () -> Unit = {},
) {
    DocumentPageScaffold(
        title = ChromeStr.localBackup(),
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        LocalBackupScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            backupFileController = backupFileController,
            onHolidayDataChanged = onHolidayDataChanged,
        )
    }
}

/**
 * AI 文本导入路由。
 *
 * @param onCopyToClipboard 复制提示词到系统剪切板：Android 用 `ClipboardManager`，iOS 用 `UIPasteboard`，桌面用 `Toolkit`；默认空实现。
 */
@Composable
fun AiImportRoute(
    router: AppRouter,
    onCopyToClipboard: (String) -> Unit = {},
) {
    DocumentPageScaffold(
        title = ChromeStr.aiImport(),
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        AiImportScreen(
            onBack = { router.popBack() },
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            onCopyToClipboard = onCopyToClipboard,
        )
    }
}

/**
 * 节假日与调休路由。网络拉取统一走 `:core` 的 [createHttpService]（iOS 是 NSURLSession，
 * 桌面是 OkHttp），协程调度用 [ioDispatcher]。
 *
 * @param onHolidayDataChanged 节假日数据变更后的平台副作用（重排提醒/刷小部件），Android 宿主注入。
 */
@Composable
fun HolidaySettingsRoute(
    router: AppRouter,
    onHolidayDataChanged: () -> Unit = {},
) {
    val http = remember { createHttpService(HttpTimeouts(connectSeconds = 10, readSeconds = 10)) }
    val scope = rememberCoroutineScope()
    val currentDate = remember { todayLocalDate() }
    var year by remember { mutableIntStateOf(currentDate.year) }
    var entries by remember { mutableStateOf(HolidayManager.load(year)) }
    var loading by remember { mutableStateOf(false) }
    // 两个分段同层切换（年度安排 / 其他操作），横向平移翻页，不新增页面层级
    var selectedTab by remember { mutableIntStateOf(0) }
    val pageSwitch = rememberPageSwitchState(0)
    LaunchedEffect(selectedTab) { pageSwitch.animateTo(selectedTab) }
    // 抓起冻住胶囊滑块；松手强制吸附
    var pillFreezeKey by remember { mutableIntStateOf(0) }
    var pillAlignKey by remember { mutableIntStateOf(0) }

    fun reload() {
        entries = HolidayManager.load(year)
    }

    fun requestYear(targetYear: Int) {
        if (loading) return
        loading = true
        scope.launch {
            val result = runCatching {
                val response = withContext(ioDispatcher) {
                    http.get(HolidayManager.sourceUrlFor(targetYear))
                }
                HolidayManager.parseSourceResponse(response.text)
            }.getOrDefault(emptyList())
            val merged = HolidayManager.mergeApiEntries(targetYear, result)
            if (targetYear == year) reload()
            loading = false
            if (merged && result.isNotEmpty()) {
                // API 合并同样要重排提醒并刷小部件，不能只改本地 SP
                onHolidayDataChanged()
            }
        }
    }

    NativeDocumentActionEffect(NativeDocumentAction(
        symbol = "arrow.clockwise",
        label = if (loading) ChromeStr.updating() else CommonStr.update(),
        enabled = !loading,
        busy = loading,
        onClick = { requestYear(year) },
    ))

    DocumentPageScaffold(
        title = ChromeStr.holidaysTitle(),
        onBack = { router.popBack() },
        headerOverlay = { backdrop, scrollBehavior ->
            CapsuleSegmentedControl(
                options = listOf(
                    HolidayStr.tabAnnual(),
                    HolidayStr.tabOther(),
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                selectOnPress = true,
                freezeKey = pillFreezeKey,
                alignKey = pillAlignKey,
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
                modifier = segmentControlModifier(),
            )
        },
        endAction = { backdropAlpha, shadowAlpha, backdrop ->
            RefreshTopBarAction(
                loading = loading,
                icon = MiuixIcons.Normal.Update,
                contentDescription = CommonStr.update(),
                onClick = { requestYear(year) },
                backdrop = backdrop,
                backdropAlpha = backdropAlpha,
                shadowAlpha = shadowAlpha,
            )
        },
    ) { scrollBehavior, backdrop ->
        HorizontalPageSwitch(
            state = pageSwitch,
            pageCount = 2,
            modifier = Modifier.fillMaxSize(),
            // 手势接管：停住滑块
            onGestureStart = { pillFreezeKey++ },
            // 松手回灌落点并吸附滑块
            onGestureEnd = { page ->
                selectedTab = page
                pillAlignKey++
            },
        ) { page ->
            HolidaySettingsScreen(
                scrollBehavior = scrollBehavior,
                liquidGlassBackdrop = backdrop,
                year = year,
                entries = entries,
                onYearChange = { newYear ->
                    year = newYear
                    reload()
                },
                reload = { reload() },
                onHolidayDataChanged = onHolidayDataChanged,
                tab = page,
                topExtra = 64.dp,
                syncing = loading,
                onSync = { requestYear(year) },
            )
        }
    }
}

/**
 * 桌面小部件引导路由。
 *
 * @param onRequestWidgetRefresh Android 注入 `WidgetUpdateCache` 清缓存 + 刷新已安装小部件；桌面/iOS 无 AppWidget 概念，默认空实现。
 * ⚠ 弹窗「添加到桌面」文案是 Android 措辞；iOS 若做 WidgetKit 需提成参数（现在走不到这一页）。
 */
@Composable
fun WidgetIntroRoute(
    router: AppRouter,
    onRequestWidgetRefresh: () -> Unit = {},
    /**
     * 拉起系统选图器给组件换壁纸。Android 注入真实能力（相册）；
     * 桌面/iOS 没有对应组件能力，默认空实现 —— 按钮点了没反应，但页面照常渲染。
     */
    onPickWidgetWallpaper: (widthPx: Int, heightPx: Int, onPicked: (ImageBitmap?) -> Unit) -> Unit =
        { _, _, _ -> },
) {
    val hapticFeedback = LocalHapticFeedback.current
    var showGuideDialog by remember { mutableStateOf(false) }
    // 自定义浮层铺满整屏（含状态栏），所以挂在脚手架外侧，而不是页面内容里
    var customizeRequest by remember { mutableStateOf<WidgetCustomizeRequest?>(null) }
    // 浮层已把小部件送回原位（但还没摘）：页面那份预览此时可以放出来了
    var customizeSettled by remember { mutableStateOf(false) }
    // 「应用」后自增，逼页面重读壁纸（换图但 key 不变时只靠内部缓存是刷不出来的）
    var wallpaperRevision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        DocumentPageScaffold(
            title = ChromeStr.widgetsTitle(),
            onBack = { router.popBack() },
            overlay = { backdrop ->
                BottomActionButton(
                    text = ChromeStr.addToHomeScreen(),
                    backdrop = backdrop,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        showGuideDialog = true
                    },
                )
                OverlayDialog(
                    title = ChromeStr.addWidgetTitle(),
                    show = showGuideDialog,
                    liquidGlassBackdrop = backdrop,
                    onDismissRequest = { showGuideDialog = false }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isIosPlatform) {
                                ChromeStr.widgetGuideIos()
                            } else {
                                ChromeStr.widgetGuideAndroid()
                            },
                            fontSize = 14.sp,
                            lineHeight = 24.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        TextButton(
                            text = CommonStr.done(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                showGuideDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
        ) { scrollBehavior, backdrop ->
            WidgetIntroScreen(
                scrollBehavior = scrollBehavior,
                liquidGlassBackdrop = backdrop,
                onRequestWidgetRefresh = onRequestWidgetRefresh,
                onCustomizeRequest = { customizeRequest = it },
                // 浮层在画这张小部件，页面里那份必须让位；
                // 但「已回位、还没摘」的那一刻要放出来 —— 否则摘浮层那帧是空的
                hiddenPage = if (customizeSettled) null else customizeRequest?.page,
                wallpaperRevision = wallpaperRevision,
            )
        }

        customizeRequest?.let { request ->
            WidgetCustomizeOverlay(
                request = request,
                onDismiss = {
                    customizeRequest = null
                    customizeSettled = false
                },
                onApply = {
                    customizeRequest = null
                    customizeSettled = false
                    // 桌面组件刷新会把所有已装组件的位图现场重画（解码 + 模糊 + 编码），
                    // 是同步重活。留在这一帧里做，等于把「摘浮层」那帧的绘制顶掉。
                    scope.launch {
                        withFrameNanos { }
                        onRequestWidgetRefresh()
                    }
                },
                // 页面先亮起来（和浮层那份完全重合），顺便读到刚落盘的外观
                onSettled = {
                    wallpaperRevision++
                    customizeSettled = true
                },
                onPickWallpaper = onPickWidgetWallpaper,
            )
        }
    }
}

/**
 * 课程提醒路由。
 *
 * @param reminderController Android 注入 `AndroidReminderController(context)`（闹钟 / 电池优化 / 权限 / 系统设置页跳转）；桌面/iOS 用 [NoopReminderController]。
 * @param testActionLabel 底部测试按钮文案，Android 按设备能力在「测试小米超级岛」/「测试实时活动」间切换后传入。
 * @param onSendTestNotification **传 null 表示这一端没有测试能力 → 不渲染按钮**，别给一个点了没反应的按钮。
 */
@Composable
fun CourseReminderRoute(
    router: AppRouter,
    reminderController: ReminderController = NoopReminderController,
    testActionLabel: String = ChromeStr.testNotification(),
    onSendTestNotification: (() -> Unit)? = null,
) {
    DocumentPageScaffold(
        title = ChromeStr.courseReminders(),
        onBack = { router.popBack() },
        overlay = { backdrop ->
            if (onSendTestNotification != null) {
                BottomActionButton(
                    text = testActionLabel,
                    backdrop = backdrop,
                    onClick = onSendTestNotification,
                )
            }
        },
    ) { scrollBehavior, backdrop ->
        CourseReminderScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            reminderController = reminderController,
        )
    }
}

/**
 * WebDAV 云同步设置路由：顶栏「测试连接」（[DocumentPageScaffold] 的 endAction）+
 * 底部「同步到云端 / 从云端恢复」（overlay 里直接两颗 [LiquidGlassTextButton]）。
 *
 * ⚠ 底部按钮必须画在 `overlay` 里，不能挪进 [WebDavSettingsScreen]：本页内容整体在录制层内，
 * 按钮自己又采样同一层 backdrop，放进去就是自采自，直接崩。
 *
 * 按钮的点击与忙碌态由 Screen 反抛（`onBackupRestoreReady` / `onBusyStateChange`），本路由只持有并渲染。
 * @param onHolidayDataChanged 云端恢复后的平台副作用（重排提醒 / 勿扰 / 小组件），Android 宿主注入。
 */
@Composable
fun WebDavSettingsRoute(
    router: AppRouter,
    onHolidayDataChanged: () -> Unit = {},
) {
    var connected by remember { mutableStateOf(false) }
    var onTestConnection by remember { mutableStateOf({}) }
    var backingUp by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var onBackup by remember { mutableStateOf({}) }
    var onRestore by remember { mutableStateOf({}) }

    NativeDocumentActionEffect(NativeDocumentAction(
        symbol = if (connected) "checkmark" else "play.fill",
        label = if (connected) ChromeStr.connected() else ChromeStr.testConnection(),
        successful = connected,
        onClick = { onTestConnection() },
    ))

    DocumentPageScaffold(
        title = ChromeStr.webdavBackup(),
        onBack = { router.popBack() },
        overlay = { backdrop ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = if (isTabletWidth()) 28.dp else 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    LiquidGlassTextButton(
                        text = if (backingUp) ChromeStr.backingUp() else ChromeStr.backupToCloud(),
                        onClick = onBackup,
                        backdrop = backdrop,
                        primary = true,
                    )
                }
                Box(Modifier.weight(1f)) {
                    LiquidGlassTextButton(
                        text = if (restoring) ChromeStr.restoring() else ChromeStr.restoreFromCloud(),
                        onClick = onRestore,
                        backdrop = backdrop,
                        primary = true,
                    )
                }
            }
        },
        endAction = { backdropAlpha, shadowAlpha, backdrop ->
            LiquidTopBarButton(
                onClick = { onTestConnection() },
                backdrop = backdrop,
                icon = if (connected) MiuixIcons.Ok else MiuixIcons.Play,
                contentDescription = if (connected) ChromeStr.connected() else ChromeStr.testConnection(),
                backdropAlpha = backdropAlpha,
                shadowAlpha = shadowAlpha,
                iconOffset = if (!connected) DpOffset(x = 2.dp, y = 0.dp) else DpOffset.Zero,
                iconTint = if (connected) Color(0xFF4CAF50) else Color.Unspecified,
            )
        },
    ) { scrollBehavior, _ ->
        WebDavSettingsScreen(
            scrollBehavior = scrollBehavior,
            onConnectedChange = { connected = it },
            onTestConnectionReady = { onTestConnection = it },
            onBackupRestoreReady = { backup, restore ->
                onBackup = backup
                onRestore = restore
            },
            onBusyStateChange = { b, r ->
                backingUp = b
                restoring = r
            },
            onHolidayDataChanged = onHolidayDataChanged,
        )
    }
}

/**
 * 课表导入 / 导出 / 备份（共用 [BackupAndMigrationScreen]，只差 [mode]）。
 *
 * [switchableModes] 非空时启用「导入导出」胶囊切换页；留空为单模式页。
 * 三个入口必须走路由，不能是独立 Activity。
 *
 * @param scheduleFileController Android 注入 `AndroidScheduleFileController(context)`；iOS/桌面用 [NoopScheduleFileController]（点导入会提示不可用）。
 */
@Composable
fun BackupAndMigrationRoute(
    router: AppRouter,
    mode: ScheduleDataManageMode,
    /** 非空时启用胶囊切换页。 */
    switchableModes: List<ScheduleDataManageMode> = emptyList(),
    scheduleFileController: ScheduleFileController = NoopScheduleFileController,
    onHolidayDataChanged: () -> Unit = {},
    onFetchShareCode: suspend (code: String) -> Result<ShareCodeFetchResult> =
        { Result.failure(IllegalStateException(ChromeStr.platformUnsupported(ChromeStr.shareCodeImport()))) },
    onImportThirdParty: suspend (source: ThirdPartyShareSource, code: String)
    -> Result<ThirdPartySharePayload> =
        { source, _ -> Result.failure(IllegalStateException(ChromeStr.platformUnsupported(source.displayName))) },
    onShareSchedule: (scheduleName: String, onSharingChanged: (Boolean) -> Unit) -> Unit = { _, _ -> },
    onExportToWearable: suspend (scheduleName: String) -> String? = { null },
    watchSyncController: WatchSyncController = NoopWatchSyncController,
    onSyncToCalendar: suspend (scheduleName: String) -> CalendarSyncResult = { CalendarSyncResult.Unsupported },
) {
    val switchable = switchableModes.size > 1
    val pageCount = switchableModes.size.coerceAtLeast(1)
    val initialPage = switchableModes.indexOf(mode).coerceIn(0, pageCount - 1)
    // selectedTab 由胶囊与页面手势松手写入
    var selectedTab by remember(mode) { mutableIntStateOf(initialPage) }
    val pageSwitch = rememberPageSwitchState(initialPage)
    LaunchedEffect(switchable, selectedTab, pageCount) {
        if (switchable) pageSwitch.animateTo(selectedTab.coerceIn(0, pageCount - 1))
    }
    // 抓起冻住滑块；松手强制吸附
    var pillFreezeKey by remember(mode) { mutableIntStateOf(0) }
    var pillAlignKey by remember(mode) { mutableIntStateOf(0) }
    val goToPage: (Int) -> Unit = { index ->
        selectedTab = index.coerceIn(0, pageCount - 1)
    }
    val title = if (mode == ScheduleDataManageMode.Backup && !switchable) {
        ChromeStr.scheduleBackup()
    } else {
        ChromeStr.importExport()
    }
    DocumentPageScaffold(
        title = title,
        onBack = { router.popBack() },
        headerOverlay = if (switchable) {
            { backdrop, scrollBehavior ->
                CapsuleSegmentedControl(
                    options = switchableModes.map { modeTabLabel(it) },
                    selectedIndex = selectedTab.coerceIn(0, switchableModes.lastIndex),
                    onSelect = goToPage,
                    selectOnPress = true,
                    freezeKey = pillFreezeKey,
                    alignKey = pillAlignKey,
                    backdrop = backdrop,
                    scrollBehavior = scrollBehavior,
                    modifier = segmentControlModifier(),
                )
            }
        } else null,
    ) { scrollBehavior, backdrop ->
        // 两页与单模式页共用参数表
        val screen: @Composable (ScheduleDataManageMode, Dp) -> Unit = { pageMode, topExtra ->
            BackupAndMigrationScreen(
                scrollBehavior = scrollBehavior,
                liquidGlassBackdrop = backdrop,
                mode = pageMode,
                topExtra = topExtra,
                scheduleFileController = scheduleFileController,
                onHolidayDataChanged = onHolidayDataChanged,
                onFetchShareCode = onFetchShareCode,
                onImportThirdParty = onImportThirdParty,
                onShareSchedule = onShareSchedule,
                onExportToWearable = onExportToWearable,
                watchSyncController = watchSyncController,
                onSyncToCalendar = onSyncToCalendar,
            )
        }
        if (switchable) {
            HorizontalPageSwitch(
                state = pageSwitch,
                pageCount = pageCount,
                modifier = Modifier.fillMaxSize(),
                // 手势接管：停住滑块
                onGestureStart = { pillFreezeKey++ },
                // 松手回灌落点并吸附滑块
                onGestureEnd = { page ->
                    selectedTab = page
                    pillAlignKey++
                },
            ) { page ->
                screen(switchableModes[page], 64.dp)
            }
        } else {
            screen(mode, 0.dp)
        }
    }
}

/** 导入导出页胶囊短标签。 */
private fun modeTabLabel(mode: ScheduleDataManageMode): String = when (mode) {
    ScheduleDataManageMode.Import -> ChromeStr.importTab()
    ScheduleDataManageMode.Export -> ChromeStr.exportTab()
    ScheduleDataManageMode.Backup -> ChromeStr.scheduleBackup()
}

/**
 * 上课时间。
 *
 * 比普通文档页多一层结构：外层 `Box` 做「背景模糊」动画；
 * ⚠ 二级编辑页 [TimeConfigEditScreen] 必须渲染在 [DocumentPageScaffold] 外面，否则会被顶栏裁掉。
 *
 * @param isInFreeformWindow Android 读宿主 `MainActivity` 的 Compose 状态（子页拿不到自己的 Activity 回调）；桌面/iOS 没有这个概念，传 false。
 */
@Composable
fun CourseTimeSettingsRoute(
    router: AppRouter,
    isInFreeformWindow: Boolean = false,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val repository = remember { CourseRepository() }
    val coroutineScope = rememberCoroutineScope()

    // 与课程管理页同口径读 Compose 容器尺寸，跨平台一致
    val screenWidth = windowInfo.containerSize.width.toFloat()
    val screenHeight = windowInfo.containerSize.height.toFloat()
    // 圆角：Android 读系统真实圆角（自由小窗固定 20dp）；iOS/桌面返回 0 = 不裁切
    val screenCornerRadius = rememberScreenCornerRadiiPx()

    var currentPage by remember { mutableStateOf("select") }
    NativeDocumentOverlayEffect(currentPage == "edit")
    val nativeBarInset = (LocalNativeDocumentChrome.current?.barInset ?: 0.0).dp
    var editingExitStarted by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<TimeConfig?>(null) }
    var editingRoutineId by remember { mutableStateOf<Long?>(null) }
    var editingCardBounds by remember { mutableStateOf<TimeConfigCardBounds?>(null) }
    var listRefreshTrigger by remember { mutableIntStateOf(0) }
    // 列表页持有卡片本体，这里只留快照完成转场
    var cardSnapshot by remember { mutableStateOf<TransitionLayerSnapshot?>(null) }

    // 页面级背景模糊
    val managePageBlurRadius = remember { Animatable(0f) }
    // 二级编辑页的玻璃层是独立的（与一级页不共享采样源）
    val editLiquidGlassBackdrop = rememberLayerBackdrop()

    // 形变的坐标基准必须是「本页在窗口里的实际矩形」：平板右栏只占一栏，
    // 拿窗口尺寸当 screenWidth、拿窗口坐标当卡片坐标，起落点会整体偏出一栏。
    var pageBoundsInWindow by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { pageBoundsInWindow = it.boundsInWindow() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 用 renderEffect 在绘制阶段求半径，避免动画时重组
                    //（isRenderEffectSupported 跨平台；不支持时返回 null = 不模糊）
                    val blurRadiusPx = managePageBlurRadius.value * density.density
                    renderEffect = if (blurRadiusPx > 0f && isRenderEffectSupported()) {
                        BlurEffect(null, blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                    } else null
                }
                .background(MiuixTheme.colorScheme.surface)
        ) {
            DocumentPageScaffold(
                title = ChromeStr.classTimeSettings(),
                onBack = { router.popBack() },
            ) { scrollBehavior, liquidGlassBackdrop ->
                CourseTimeSettingsScreen(
                    onEditRoutine = { routine, baseConfig, bounds ->
                        editingExitStarted = false
                        editingConfig = baseConfig.effectiveFor(routine.id)
                        editingRoutineId = routine.id
                        editingCardBounds = bounds
                        cardSnapshot = bounds.snapshot
                        coroutineScope.launch {
                            if (editingExitStarted || currentPage != "edit") return@launch
                            managePageBlurRadius.animateTo(
                                targetValue = 16f,
                                animationSpec = tween(540, easing = OobeQuartOutEasing)
                            )
                        }
                        currentPage = "edit"
                    },
                    refreshTrigger = listRefreshTrigger,
                    scrollBehavior = scrollBehavior,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    morphActive = currentPage == "edit",
                )
            }
        }

        // 编辑页渲染在脚手架外面
        if (currentPage == "edit") {
            editingConfig?.let { config ->
                val bounds = editingCardBounds
                // 编辑页在本页坐标里的可用区域：顶部还要让出原生栏（consumeWindowInsets 会下推内容）。
                val pageTopInsetPx = with(density) { nativeBarInset.toPx() }
                val pageWidthPx = pageBoundsInWindow.width.takeIf { it > 0f } ?: screenWidth
                val pageHeightPx = (pageBoundsInWindow.height - pageTopInsetPx)
                    .takeIf { it > 0f } ?: screenHeight
                Box(Modifier.fillMaxSize().consumeWindowInsets(PaddingValues(top = nativeBarInset))) {
                    TimeConfigEditScreen(
                        timeConfig = config,
                        routineId = editingRoutineId ?: 0L,
                        onBackStart = {
                            editingExitStarted = true
                            coroutineScope.launch {
                                managePageBlurRadius.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(320, easing = OobeCubicOutEasing)
                                )
                            }
                        },
                        onBack = {
                            currentPage = "select"
                            editingConfig = null
                            editingRoutineId = null
                            editingCardBounds = null
                            cardSnapshot = null
                        },
                        onSave = { savedConfig ->
                            // 只替换这一个作息；节次骨架由一级页面管理，这里不碰。
                            // 顶层 name 是用户在这一页改的作息名，要带回去。
                            val saved = editingRoutineId?.let { routineId ->
                                repository.saveRoutine(routineId, savedConfig, savedConfig.name)
                            } ?: false
                            if (!saved) {
                                // 保存被丢弃必须让用户看见，否则表现成「改完保存、重开还是原来的」
                                showToast(ChromeStr.saveFailedRetry())
                            }
                            listRefreshTrigger++
                        },
                        // 卡片给的是窗口坐标，换算到本页左上角再交给形变
                        cardLeft = (bounds?.left ?: 0f) - pageBoundsInWindow.left,
                        cardTop = (bounds?.top ?: 0f) - pageBoundsInWindow.top - pageTopInsetPx,
                        cardWidth = bounds?.width ?: pageWidthPx,
                        cardHeight = bounds?.height ?: (pageHeightPx * 0.2f),
                        screenWidth = pageWidthPx,
                        screenHeight = pageHeightPx,
                        screenCornerRadius = screenCornerRadius,
                        cardStartCornerRadius = 20f,
                        cardSnapshot = cardSnapshot,
                        // FAB 新建形态已移除，二级页只从列表卡片展开
                        isFabCreation = false,
                        onDeleteRoutine = { routineId ->
                            // 删除会换掉生效作息，重排提醒等由 repository 一并完成
                            repository.deleteRoutine(routineId)
                            listRefreshTrigger++
                        },
                        liquidGlassBackdrop = editLiquidGlassBackdrop,
                    )
                }
            }
        }
    }
}

/**
 * 课程管理。与 [CourseTimeSettingsRoute] 同构，但多两块**必须在模糊层之外**的叠加内容：
 * 快捷菜单（遮罩 + 卡片快照 + [ShortcutMenu]）与 [CourseEditScreen]。
 * 它们不能走 `DocumentPageScaffold` 的 `overlay`（那是采样层内部），必须留在根 `Box` 里
 * 当外层 `Box` 的兄弟，否则会跟着一起被模糊/缩放。
 *
 * @param isInFreeformWindow 自由小窗 / 分屏判定，Android 宿主注入。
 */
@Composable
fun CourseManageRoute(
    router: AppRouter,
    isInFreeformWindow: Boolean = false,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val coroutineScope = rememberCoroutineScope()
    val courseViewModel = courseViewModel()
    val settingsViewModel = settingsViewModel()
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()

    var showEditScreen by remember { mutableStateOf(false) }
    var selectedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var cardLeft by remember { mutableFloatStateOf(0f) }
    var cardTop by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableFloatStateOf(0f) }
    var cardHeight by remember { mutableFloatStateOf(0f) }
    var cardSnapshot by remember { mutableStateOf<TransitionLayerSnapshot?>(null) }
    var cardColor by remember { mutableStateOf(Color(0xFF4CAF50)) }
    var cardAlpha by remember { mutableFloatStateOf(0.12f) }
    var hiddenCourseIds by remember { mutableStateOf(setOf<String>()) }
    var shrinkingCourseIds by remember { mutableStateOf(setOf<String>()) }
    var pendingAutoExitDeleteIds by remember { mutableStateOf(setOf<String>()) }

    var showShortcutMenu by remember { mutableStateOf(false) }
    var shortcutMenuPosition by remember { mutableStateOf(Offset.Zero) }
    var shortcutMenuCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var shortcutMenuHeight by remember { mutableFloatStateOf(0f) }
    var shortcutMenuCardWidth by remember { mutableFloatStateOf(0f) }
    var shortcutMenuCardHeight by remember { mutableFloatStateOf(0f) }
    val shortcutMenuBlurRadius = remember { Animatable(0f) }
    val shortcutMenuCardScale = remember { Animatable(1f) }
    val shortcutMenuPageScale = remember { Animatable(1f) }
    var shortcutMenuSnapshot by remember { mutableStateOf<TransitionLayerSnapshot?>(null) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteCourses by remember { mutableStateOf<List<Course>>(emptyList()) }

    var courseDialogVisible by remember { mutableStateOf(false) }
    NativeDocumentOverlayEffect(showEditScreen || showShortcutMenu || showDeleteConfirmDialog || courseDialogVisible)

    // 本次新建的课程 id，退出本页时清理
    var createdCourseIds by remember { mutableStateOf(setOf<String>()) }
    // 打开过编辑的课程 id，避免误删已修改的
    var editedCourseIds by remember { mutableStateOf(setOf<String>()) }

    DisposableEffect(Unit) {
        onDispose {
            val coursesToDelete = createdCourseIds - editedCourseIds
            coursesToDelete.forEach { courseId: String ->
                courseViewModel.deleteCourse(courseId)
            }
        }
    }

    LaunchedEffect(showShortcutMenu) {
        if (showShortcutMenu) {
            launch { shortcutMenuBlurRadius.animateTo(10f, tween(280)) }
            launch { shortcutMenuCardScale.animateTo(1.04f, tween(280)) }
            launch { shortcutMenuPageScale.animateTo(0.98f, tween(280)) }
        } else {
            launch { shortcutMenuBlurRadius.animateTo(0f, tween(250)) }
            launch { shortcutMenuCardScale.animateTo(1f, tween(250)) }
            launch { shortcutMenuPageScale.animateTo(1f, tween(250)) }
        }
    }

    // 菜单开着时由它接管返回，否则交给 AppNavHost
    NavigationBackHandlerFor(
        enabled = showShortcutMenu,
        onBackCompleted = { showShortcutMenu = false },
        onBackCancelled = {},
    )

    val managePageBlurRadius = remember { Animatable(0f) }

    // 圆角：Android 读系统真实圆角（自由小窗固定 20dp）；iOS/桌面返回 0 = 不裁切
    val screenCornerRadius = rememberScreenCornerRadiiPx()

    // 二级编辑页独立采样源；快捷菜单采课程管理页的内容层（空层渲染不出材质）
    val editLiquidGlassBackdrop = rememberLayerBackdrop()
    val shortcutMenuBackdrop = rememberLayerBackdrop()
    var managePageBackdrop by remember { mutableStateOf<GlassBackdrop?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容（模糊 + 快捷菜单页面缩放）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // renderEffect 在绘制阶段赋半径，避免动画每帧重组整页
                    val blurAnim =
                        if (shortcutMenuBlurRadius.value > 0.01f) shortcutMenuBlurRadius.value
                        else managePageBlurRadius.value
                    renderEffect = if (blurAnim > 0.01f && isRenderEffectSupported()) {
                        val blurPx = blurAnim.dp.toPx()
                        BlurEffect(null, blurPx, blurPx, TileMode.Clamp)
                    } else null
                    scaleX = shortcutMenuPageScale.value
                    scaleY = shortcutMenuPageScale.value
                }
                .background(MiuixTheme.colorScheme.surface)
        ) {
            DocumentPageScaffold(
                title = ChromeStr.courseManage(),
                onBack = { router.popBack() },
            ) { scrollBehavior, liquidGlassBackdrop ->
                SideEffect { managePageBackdrop = liquidGlassBackdrop }
                CourseManageScreen(
                    scrollBehavior = scrollBehavior,
                    hiddenCourseIds = hiddenCourseIds,
                    shrinkingCourseIds = shrinkingCourseIds,
                    sectionTimes = sectionTimes,
                    onDialogVisibilityChanged = { courseDialogVisible = it },
                    onNewCourseCreated = { course ->
                        courseViewModel.addCourse(course)
                        createdCourseIds = createdCourseIds + course.id
                    },
                    onCourseUpdated = { oldName, course ->
                        courseViewModel.updateCoursesByName(oldName, course)
                    },
                    onEditDismiss = {
                        editingCourse = null
                    },
                    pendingEditCourse = editingCourse,
                    onCourseLongPressLayer = { courses, left, top, width, height, snapshot ->
                        shortcutMenuCourses = courses
                        shortcutMenuPosition = Offset(left, top)
                        shortcutMenuCardWidth = width
                        shortcutMenuCardHeight = height
                        shortcutMenuSnapshot = snapshot
                        showShortcutMenu = true
                    },
                    onDeleteCourses = { courses ->
                        coroutineScope.launch {
                            courses.forEach { course ->
                                courseViewModel.deleteCourse(course.id)
                            }
                        }
                    },
                    deleteConfirmShow = showDeleteConfirmDialog,
                    deleteConfirmCourses = pendingDeleteCourses,
                    onDeleteConfirmDismiss = { showDeleteConfirmDialog = false },
                    onCourseClick = { courses, left, top, width, height, snapshot, color, alpha ->
                        coroutineScope.launch {
                            selectedCourses = courses
                            cardLeft = left
                            cardTop = top
                            cardWidth = width
                            cardHeight = height
                            cardColor = color
                            cardAlpha = alpha
                            cardSnapshot = snapshot
                            hiddenCourseIds = courses.map { it.id }.toSet()
                            // 打开即视为编辑过
                            editedCourseIds = editedCourseIds + courses.map { it.id }

                            showEditScreen = true
                            launch {
                                managePageBlurRadius.animateTo(
                                    targetValue = 16f,
                                    animationSpec = tween(540, easing = OobeQuartOutEasing)
                                )
                            }
                        }
                    },
                    liquidGlassBackdrop = liquidGlassBackdrop,
                )
            }
        }

        // 编辑页在模糊层之上
        if (showEditScreen) {
            CourseEditScreen(
                courses = selectedCourses,
                cardLeft = cardLeft,
                cardTop = cardTop,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                screenWidth = windowInfo.containerSize.width.toFloat(),
                screenHeight = windowInfo.containerSize.height.toFloat(),
                screenCornerRadius = screenCornerRadius,
                cardSnapshot = cardSnapshot,
                cardColor = cardColor,
                cardAlpha = cardAlpha,
                onBackStart = {
                    coroutineScope.launch {
                        managePageBlurRadius.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(320, easing = OobeCubicOutEasing)
                        )
                    }
                },
                onBack = {
                    if (pendingAutoExitDeleteIds.isNotEmpty()) {
                        showEditScreen = false
                        cardSnapshot = null
                        hiddenCourseIds = emptySet()
                        shrinkingCourseIds = pendingAutoExitDeleteIds
                        coroutineScope.launch {
                            delay(300.milliseconds)
                            pendingAutoExitDeleteIds.forEach { id ->
                                courseViewModel.deleteCourse(id)
                            }
                            shrinkingCourseIds = emptySet()
                            pendingAutoExitDeleteIds = emptySet()
                        }
                    } else {
                        showEditScreen = false
                        cardSnapshot = null
                        hiddenCourseIds = emptySet()
                    }
                },
                onCourseUpdated = { course ->
                    courseViewModel.updateCourse(course)
                    selectedCourses = selectedCourses.map { if (it.id == course.id) course else it }
                },
                onCourseAdded = { course ->
                    courseViewModel.addCourse(course)
                    selectedCourses = selectedCourses + course
                },
                onDeleteCourse = { courseId ->
                    if (selectedCourses.size > 1) {
                        courseViewModel.deleteCourse(courseId)
                    } else {
                        pendingAutoExitDeleteIds = setOf(courseId)
                    }
                    selectedCourses = selectedCourses.filter { it.id != courseId }
                },
                onColorChanged = { colorRes ->
                    cardColor = Color(colorRes)
                },
                getOccupiedWeeks = { dayOfWeek, startSection, endSection, excludeIds, startTime, endTime ->
                    courseViewModel.getOccupiedWeeks(
                        dayOfWeek = dayOfWeek,
                        startSection = startSection,
                        endSection = endSection,
                        excludeIds = excludeIds.toSet(),
                        startTime = startTime,
                        endTime = endTime
                    )
                },
                liquidGlassBackdrop = editLiquidGlassBackdrop,
                sectionTimes = sectionTimes
            )
        }

        // Shortcut 菜单遮罩 + 卡片快照
        if (showShortcutMenu || shortcutMenuBlurRadius.value > 0.01f) {
            val snapshot = shortcutMenuSnapshot
            val cardLeftPx = shortcutMenuPosition.x
            val cardTopPx = shortcutMenuPosition.y
            val cardW = shortcutMenuCardWidth
            val cardH = shortcutMenuCardHeight

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showShortcutMenu = false }
            )

            // 卡片快照不参与模糊，画在最顶层
            if (snapshot != null && cardW > 0f && cardH > 0f) {
                TransitionLayerImage(
                    snapshot = snapshot,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset(
                            x = with(density) { cardLeftPx.toDp() },
                            y = with(density) { cardTopPx.toDp() }
                        )
                        .size(
                            width = with(density) { cardW.toDp() },
                            height = with(density) { cardH.toDp() }
                        )
                        .graphicsLayer {
                            scaleX = shortcutMenuCardScale.value
                            scaleY = shortcutMenuCardScale.value
                        }
                        .drawWithContent {
                            val cornerRadius = 16.dp.toPx()
                            val cardPath = Path().apply {
                                addContinuousRoundRect(
                                    width = size.width,
                                    height = size.height,
                                    cornerRadius = cornerRadius
                                )
                            }
                            clipPath(cardPath) { this@drawWithContent.drawContent() }
                        }
                )
            }
        }

        ShortcutMenu(
            show = showShortcutMenu,
            items = listOf(
                ShortcutMenuItem(
                    icon = MiuixIcons.Edit,
                    label = CommonStr.edit(),
                    onClick = {
                        if (shortcutMenuCourses.isNotEmpty()) {
                            val courseToEdit = shortcutMenuCourses.first()
                            showShortcutMenu = false
                            coroutineScope.launch {
                                delay(260.milliseconds)
                                editingCourse = courseToEdit
                            }
                        }
                    }
                ),
                ShortcutMenuItem(
                    icon = MiuixIcons.Delete,
                    label = CommonStr.delete(),
                    onClick = {
                        pendingDeleteCourses = shortcutMenuCourses.toList()
                        showShortcutMenu = false
                        coroutineScope.launch {
                            delay(260.milliseconds)
                            showDeleteConfirmDialog = true
                        }
                    }
                )
            ),
            modifier = Modifier.offset(
                x = with(density) { shortcutMenuPosition.x.toDp() - 14.dp },
                y = with(density) { shortcutMenuPosition.y.toDp() - shortcutMenuHeight.toDp() + 4.dp }
            ),
            backdrop = managePageBackdrop ?: shortcutMenuBackdrop,
            onDismiss = { showShortcutMenu = false },
            onMeasuredSize = { _, height ->
                shortcutMenuHeight = height.toFloat()
            }
        )
    }
}
