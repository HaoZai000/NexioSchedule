package com.haooz.chedule.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpOffset
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
import com.haooz.chedule.i18n.WeatherStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AiImportScreen
import com.haooz.chedule.ui.activities.AppreciateAuthorScreen
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.weather.WeatherScreen
import com.haooz.chedule.weather.WeatherStore
import com.haooz.chedule.ui.activities.CalendarSyncResult
import com.haooz.chedule.ui.activities.ChangelogScreen
import com.haooz.chedule.ui.activities.CommunicationScreen
import com.haooz.chedule.ui.activities.CourseManageScreen
import com.haooz.chedule.ui.activities.CourseReminderScreen
import com.haooz.chedule.ui.activities.CourseTimeSettingsScreen
import com.haooz.chedule.ui.activities.EduImportUpdater
import com.haooz.chedule.ui.activities.EducationalImportScreen
import com.haooz.chedule.ui.activities.HolidaySettingsScreen
import com.haooz.chedule.ui.activities.LicenseScreen
import com.haooz.chedule.ui.activities.LocalBackupScreen
import com.haooz.chedule.ui.activities.PreferenceSettingsScreen
import com.haooz.chedule.ui.activities.PrivacyPolicyScreen
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.activities.TimeConfigCardBounds
import com.haooz.chedule.ui.activities.UpdateSettingsScreen
import com.haooz.chedule.ui.activities.WebDavSettingsScreen
import com.haooz.chedule.ui.activities.WebImportScreen
import com.haooz.chedule.ui.activities.WidgetCustomizeOverlay
import com.haooz.chedule.ui.activities.WidgetCustomizeRequest
import com.haooz.chedule.ui.activities.WidgetIntroScreen
import com.haooz.chedule.ui.activities.main.RenderSettingsPane
import com.haooz.chedule.ui.activities.main.SettingsPaneArgs
import com.haooz.chedule.ui.basic.LiquidGlassTextButton
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ShortcutMenu
import com.haooz.chedule.ui.basic.ShortcutMenuItem
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.components.WebDavActionButtons
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.screens.CourseEditScreen
import com.haooz.chedule.ui.screens.TimeConfigEditScreen
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
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.isRenderEffectSupported
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import com.kyant.capsule.addContinuousRoundRect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.NavigationBackHandlerFor

/**
 * 路由宿主的跨平台部分：路由跳转、状态接线、Updater 订阅，平台能力一律参数注入。
 *
 * ⚠ 本文件不能改名 `AppRouteContent.kt`：`:androidApp` 同包已有一个同名文件，两边会生成同一 facade 类 `AppRouteContentKt`，编译期静默通过，但 D8/R8 打 dex 时同名 class 冲突只留一个，本文件路由全部 `NoSuchMethodError`，Android/iOS 都闪退。往 `:ui-shared` 加文件前先查 `:androidApp` 同包有无同名 `.kt`；Android 侧同名包装加 `Android` 前缀（如 `AndroidWebHost`）。
 */

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

// ── 纯文档壳路由：DocumentPageScaffold + 单个 Screen，零平台触点 ──
// 五个形状完全一样（标题 + 返回 + 正文），Android 侧不需要 `Android*` 包装，`:androidApp` 直接调这里。

/** 更新日志路由。 */
@Composable
fun ChangelogRoute(router: AppRouter) {
    DocumentPageScaffold(title = ChromeStr.changelog(), onBack = { router.popBack() }) { scrollBehavior, _ ->
        ChangelogScreen(scrollBehavior = scrollBehavior)
    }
}

/** 开源协议路由。 */
@Composable
fun LicenseRoute(router: AppRouter) {
    DocumentPageScaffold(title = ChromeStr.openSourceLicenses(), onBack = { router.popBack() }) { scrollBehavior, _ ->
        LicenseScreen(scrollBehavior = scrollBehavior)
    }
}

/** 隐私政策路由。 */
@Composable
fun PrivacyPolicyRoute(router: AppRouter) {
    DocumentPageScaffold(title = ChromeStr.privacyPolicy(), onBack = { router.popBack() }) { scrollBehavior, _ ->
        PrivacyPolicyScreen(scrollBehavior = scrollBehavior)
    }
}

/** 交流与反馈路由。 */
@Composable
fun CommunicationRoute(router: AppRouter) {
    DocumentPageScaffold(title = ChromeStr.communityFeedback(), onBack = { router.popBack() }) { scrollBehavior, _ ->
        CommunicationScreen(scrollBehavior = scrollBehavior)
    }
}

/** 捐赠支持路由。 */
@Composable
fun AppreciateAuthorRoute(router: AppRouter) {
    DocumentPageScaffold(title = ChromeStr.donate(), onBack = { router.popBack() }) { scrollBehavior, _ ->
        AppreciateAuthorScreen(scrollBehavior = scrollBehavior)
    }
}

/**
 * 天气详情路由。
 *
 * 零平台触点：数据由 [com.haooz.chedule.weather.WeatherStore] 提供（今日卡先落地），
 * 定位权限、刷新都在数据层，本页只画。所以 Android / iOS 都能直接调这一个函数。
 */
@Composable
fun WeatherRoute(router: AppRouter) {
    // ⚠ recordKey 不能省：`layerBackdrop` 在 recordKey == null 时会**每帧**把整页录进图层
    // （滚动时整页画两遍 + 顶栏玻璃每帧重采样），天气页上下滑动掉帧的主因就是这个。
    // 指纹只要覆盖"会让被采样像素变的东西"：数据、载入态、主题底色。
    // 折叠动画那部分由脚手架自己的 mustRecord（draw 期比较 heightOffset）负责。
    val snapshot by WeatherStore.snapshot.collectAsState()
    val loading by WeatherStore.loading.collectAsState()
    val surface = MiuixTheme.colorScheme.surface
    val recordKey = remember(snapshot, loading, surface) { listOf(snapshot, loading, surface) }
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = { scope.launch { WeatherStore.requestRefresh() } }

    // iOS 原生文档页的顶栏按钮由 Swift 画，Compose 顶栏看不到 —— 照抄教务导入的做法，
    // 把同一个动作发布给原生 chrome（symbol 用系统的刷新图标）。
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
            // 与「教务导入」那颗一模一样：真实采样层当 backdrop（空层渲染出来是纯色圆，
            // 这就是之前"没有玻璃效果"的原因）、28dp 图标、载入中换成 20dp 转圈。
            // 唯一区别是图标用 Refresh。
            if (loading) {
                Box(
                    modifier = Modifier
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
                    onClick = refresh,
                    backdrop = backdrop,
                    icon = MiuixIcons.Normal.Refresh,
                    contentDescription = CommonStr.update(),
                    iconSize = 28.dp,
                    backdropAlpha = backdropAlpha,
                    shadowAlpha = shadowAlpha,
                )
            }
        },
    ) { scrollBehavior, _ ->
        WeatherScreen(scrollBehavior = scrollBehavior)
    }
}

// ── 带能力注入的文档壳路由：壳在 commonMain，平台能力走参数 ──

/**
 * 设置（二级页）
 * ⚠ 平板（rail）仍走 pager 内嵌那套，不经过这里。
 * 壳用 [DocumentPageScaffold]：返回按钮、折叠标题、页内采样层、iOS 原生 chrome 都由它给，
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
    // 排班模式状态直接读 VM：本页可能从普通模式进、也可能从排班模式进
    val isShiftMode by shiftViewModel.isShiftMode.collectAsState()
    // 设置页始终跟应用主题、不被壁纸锁深色 —— 这层原先由 pager 的设置分支提供，
    // 搬进路由后必须自己补上，否则深色壁纸下整页会被染深。
    val settingsThemeController =
        rememberAppThemeController(isDark = isAppDarkTheme())
    DocumentPageScaffold(
        title = CommonStr.settings(),
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        MiuixTheme(controller = settingsThemeController) {
            CompositionLocalProvider(LocalForcedDarkTheme provides null) {
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
                        // DocumentPageScaffold 的 scrollBehavior：顶栏折叠与列表滚动共用一个
                        settingsScrollBehavior = scrollBehavior,
                        liquidGlassBackdrop = backdrop,
                        showUpdateSettings = showUpdateSettings,
                    ),
                )
            }
        }
    }
}

/**
 * 应用偏好设置路由。四个平台能力全部参数注入：定位权限检查 / 天气缓存清空 /
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
        // 偏好设置页可能改主题 → 系统栏要跟着刷新
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
 * 更新设置路由。
 *
 * @param updateController Android 注入 `AndroidUpdateController(context)`（检查/下载/APK 安装）；桌面/iOS 用默认 [NoopUpdateController]。
 */
@Composable
fun UpdateSettingsRoute(
    router: AppRouter,
    updateController: UpdateController = NoopUpdateController,
) {
    DocumentPageScaffold(
        title = ChromeStr.updateSettingsTitle(),
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        UpdateSettingsScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            updateController = updateController,
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
        endAction = { backdropAlpha, shadowAlpha, backdrop ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .offset(x = (-6).dp, y = (-4).dp)
                        .size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        progress = null,
                    )
                }
            } else {
                LiquidTopBarButton(
                    onClick = { requestYear(year) },
                    backdrop = backdrop,
                    icon = MiuixIcons.Normal.Update,
                    contentDescription = CommonStr.update(),
                    iconSize = 28.dp,
                    backdropAlpha = backdropAlpha,
                    shadowAlpha = shadowAlpha,
                )
            }
        },
    ) { scrollBehavior, backdrop ->
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
        )
    }
}

// ── 带额外元素的页面路由：底部悬浮按钮 / 引导弹窗 / 顶栏按钮 ──
// 共同形状：DocumentPageScaffold + 一个 Screen，平台能力全部走参数；
// Android 注入真实能力，桌面/iOS 用默认值（按钮不显示 / 空实现）。

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
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp
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
                LiquidGlassTextButton(
                    text = ChromeStr.addToHomeScreen(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        showGuideDialog = true
                    },
                    backdrop = backdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = tabletHorizontalPadding + 16.dp,
                            end = tabletHorizontalPadding + 16.dp
                        )
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp),
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
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    DocumentPageScaffold(
        title = ChromeStr.courseReminders(),
        onBack = { router.popBack() },
        overlay = { backdrop ->
            if (onSendTestNotification != null) {
                LiquidGlassTextButton(
                    text = testActionLabel,
                    onClick = onSendTestNotification,
                    backdrop = backdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = tabletHorizontalPadding + 16.dp,
                            end = tabletHorizontalPadding + 16.dp
                        )
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp),
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
 * WebDAV 云备份设置路由：顶栏「测试连接」（[DocumentPageScaffold] 的 endAction）+
 * 底部「备份到云端 / 从云端恢复」（[WebDavActionButtons]，三端同一份实现）。
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
            WebDavActionButtons(
                backdrop = backdrop,
                backingUp = backingUp,
                restoring = restoring,
                onBackup = onBackup,
                onRestore = onRestore,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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
 * 课表导入 / 导出 / 备份（三者共用 [BackupAndMigrationScreen]，只差 [mode]）。
 *
 * ⚠ 三个入口必须走路由、不能是独立 Activity：`BackupAndMigrationScreen` 的「本地备份」入口读 [LocalAppRouter]，独立 Activity 的 setContent 没 provide，默认值 `error(...)` 一点就崩。口令取回 / 第三方抓取 / 分享出图 / 手环推送四条 Android 能力走注入，iOS/桌面默认「当前平台不支持」—— 这一页在 iOS 上仍能打开（WebDAV / 本地备份 / 文件导入导出可用），只是口令通道不可用。
 *
 * @param scheduleFileController Android 注入 `AndroidScheduleFileController(context)`；iOS/桌面用 [NoopScheduleFileController]（点导入会提示不可用）。
 */
@Composable
fun BackupAndMigrationRoute(
    router: AppRouter,
    mode: ScheduleDataManageMode,
    scheduleFileController: ScheduleFileController = NoopScheduleFileController,
    onHolidayDataChanged: () -> Unit = {},
    onFetchShareCode: suspend (code: String) -> Result<ShareCodeFetchResult> =
        { Result.failure(IllegalStateException(ChromeStr.platformUnsupported(ChromeStr.shareCodeImport()))) },
    onImportThirdParty: suspend (source: ThirdPartyShareSource, code: String)
    -> Result<ThirdPartySharePayload> =
        { source, _ -> Result.failure(IllegalStateException(ChromeStr.platformUnsupported(source.displayName))) },
    onShareSchedule: (scheduleName: String, onSharingChanged: (Boolean) -> Unit) -> Unit = { _, _ -> },
    onExportToWearable: suspend (scheduleName: String) -> String? = { null },
    onSyncToCalendar: suspend (scheduleName: String) -> CalendarSyncResult = { CalendarSyncResult.Unsupported },
) {
    val title = when (mode) {
        ScheduleDataManageMode.Import -> ChromeStr.scheduleImport()
        ScheduleDataManageMode.Export -> ChromeStr.scheduleExport()
        ScheduleDataManageMode.Backup -> ChromeStr.scheduleBackup()
    }
    DocumentPageScaffold(
        title = title,
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        BackupAndMigrationScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            mode = mode,
            scheduleFileController = scheduleFileController,
            onHolidayDataChanged = onHolidayDataChanged,
            onFetchShareCode = onFetchShareCode,
            onImportThirdParty = onImportThirdParty,
            onShareSchedule = onShareSchedule,
            onExportToWearable = onExportToWearable,
            onSyncToCalendar = onSyncToCalendar,
        )
    }
}

// ── 最后两个路由壳：二级页转场 + 卡片快照 ──
// 一级列表之上叠一层「背景缩放 + 模糊」；二级编辑页渲染在 Scaffold **外面**（否则会被顶栏裁掉），
// 快捷菜单还要在模糊层之外再叠一层卡片快照。结构必须整体搬，不能只搬 Screen。

/**
 * 课程时间管理。
 *
 * 比普通文档页多两层结构：外层 `Box` 做「背景缩放 + 模糊」动画；
 * ⚠ 二级编辑页 `TimeConfigEditScreen` 必须渲染在 `Scaffold` 外面，否则会被顶栏裁掉。
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

    // 屏幕尺寸（px）：与 CourseManageRoute 同口径读 Compose 容器尺寸，跨平台一致
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
    // morph 进二级页期间要隐藏的源行（退出动画播完、回到列表时才放开）
    var morphingRoutineId by remember { mutableStateOf<Long?>(null) }
    var listRefreshTrigger by remember { mutableIntStateOf(0) }

    // Retained source-card drawing; the list page owns the underlying layer.
    var cardSnapshot by remember { mutableStateOf<TransitionLayerSnapshot?>(null) }

    // 页面级模糊动画（连贯动画只保留下层模糊，缩放已去掉）
    val managePageBlurRadius = remember { Animatable(0f) }
    // 二级编辑页的玻璃层是独立的（与一级页不共享采样源）
    val editLiquidGlassBackdrop = rememberLayerBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 用 renderEffect 模糊，避免动画时重组
                    //（:backdrop 的 isRenderEffectSupported 跨平台；不支持时返回 null = 不模糊）
                    val blurRadiusPx = managePageBlurRadius.value * density.density
                    renderEffect = if (blurRadiusPx > 0f && isRenderEffectSupported()) {
                        BlurEffect(null, blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                    } else null
                }
                .background(MiuixTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                            morphingRoutineId = routine.id
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
                        morphingRoutineId = morphingRoutineId,
                    )
                }
            }
        }

        // 编辑页面渲染在 Scaffold 外面
        if (currentPage == "edit") {
            editingConfig?.let { config ->
                val bounds = editingCardBounds
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
                            morphingRoutineId = null
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
                        cardLeft = bounds?.left ?: 0f,
                        cardTop = bounds?.top ?: 0f,
                        cardWidth = bounds?.width ?: screenWidth,
                        cardHeight = bounds?.height ?: (screenHeight * 0.2f),
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        screenCornerRadius = screenCornerRadius,
                        cardStartCornerRadius = 20f,
                        cardSnapshot = cardSnapshot,
                        // 新建配置的 FAB 形态已移除，二级页永远从列表卡片展开
                        isFabCreation = false,
                        onDeleteRoutine = { routineId ->
                            // 删除会换掉生效作息，重排提醒等都在 repository 里一并做完
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

    // 编辑页状态
    var showEditScreen by remember { mutableStateOf(false) }
    var selectedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var cardLeft by remember { mutableFloatStateOf(0f) }
    var cardTop by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableFloatStateOf(0f) }
    var cardHeight by remember { mutableFloatStateOf(0f) }
    var cardSnapshot by remember { mutableStateOf<TransitionLayerSnapshot?>(null) }
    var cardColor by remember { mutableStateOf(Color(0xFF4CAF50)) }
    var cardAlpha by remember { mutableFloatStateOf(0.15f) }
    var hiddenCourseIds by remember { mutableStateOf(setOf<String>()) }
    var shrinkingCourseIds by remember { mutableStateOf(setOf<String>()) }
    var pendingAutoExitDeleteIds by remember { mutableStateOf(setOf<String>()) }

    // Shortcut菜单状态
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

    // 删除确认弹窗状态
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

    // 返回键关闭菜单（嵌套返回拦截：菜单开着时由它接管，否则交给 AppNavHost 的返回）
    NavigationBackHandlerFor(
        enabled = showShortcutMenu,
        onBackCompleted = { showShortcutMenu = false },
        onBackCancelled = {},
    )

    val managePageBlurRadius = remember { Animatable(0f) }

    // 圆角：Android 读系统真实圆角（自由小窗固定 20dp）；iOS/桌面返回 0 = 不裁切
    val screenCornerRadius = rememberScreenCornerRadiiPx()

    // 二级编辑页 / 快捷菜单各自独立的玻璃采样源
    val editLiquidGlassBackdrop = rememberLayerBackdrop()
    val shortcutMenuBackdrop = rememberLayerBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容（模糊 + 快捷菜单页面缩放）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // renderEffect 在绘制阶段赋半径，避免动画每帧重组整页（同 CourseScheduleApp）
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
            Box(modifier = Modifier.fillMaxSize()) {
                DocumentPageScaffold(
                    title = ChromeStr.courseManage(),
                    onBack = { router.popBack() },
                ) { scrollBehavior, liquidGlassBackdrop ->
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

        // Shortcut菜单遮罩层
        if (showShortcutMenu || shortcutMenuBlurRadius.value > 0.01f) {
            val snapshot = shortcutMenuSnapshot
            val cardLeftPx = shortcutMenuPosition.x
            val cardTopPx = shortcutMenuPosition.y
            val cardW = shortcutMenuCardWidth
            val cardH = shortcutMenuCardHeight

            // 点击空白区域关闭菜单
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showShortcutMenu = false }
            )

            // 卡片快照（清晰，在最顶层）
            if (snapshot != null && cardW > 0f && cardH > 0f) {
                TransitionLayerImage(
                    snapshot = snapshot,
                    contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
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

        // Shortcut菜单
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
            backdrop = shortcutMenuBackdrop,
            onDismiss = { showShortcutMenu = false },
            onMeasuredSize = { _, height ->
                shortcutMenuHeight = height.toFloat()
            }
        )
    }
}
