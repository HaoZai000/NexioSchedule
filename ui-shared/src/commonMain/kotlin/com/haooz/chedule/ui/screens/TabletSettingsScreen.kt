package com.haooz.chedule.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.components.LocalPageStartPadding
import com.haooz.chedule.ui.components.tabletNavChromeTitleSlot
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.navigation.AboutRoute
import com.haooz.chedule.ui.navigation.AiImportRoute
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.navigation.AppreciateAuthorRoute
import com.haooz.chedule.ui.navigation.BackupAndMigrationRoute
import com.haooz.chedule.ui.navigation.BackupCenterRoute
import com.haooz.chedule.ui.navigation.ChangelogRoute
import com.haooz.chedule.ui.navigation.CommunicationRoute
import com.haooz.chedule.ui.navigation.CourseManageRoute
import com.haooz.chedule.ui.navigation.CourseReminderRoute
import com.haooz.chedule.ui.navigation.CourseTimeSettingsRoute
import com.haooz.chedule.ui.navigation.EducationalImportRoute
import com.haooz.chedule.ui.navigation.FeedbackHistoryRoute
import com.haooz.chedule.ui.navigation.FeedbackRoadmapRoute
import com.haooz.chedule.ui.navigation.FeedbackRoute
import com.haooz.chedule.ui.navigation.HolidaySettingsRoute
import com.haooz.chedule.ui.navigation.LicenseRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.NAV_BG_DIM
import com.haooz.chedule.ui.navigation.NAV_ENTER_DURATION
import com.haooz.chedule.ui.navigation.NAV_ENTER_EASING
import com.haooz.chedule.ui.navigation.NAV_EXIT_DURATION
import com.haooz.chedule.ui.navigation.NAV_EXIT_EASING
import com.haooz.chedule.ui.navigation.NAV_PARALLAX
import com.haooz.chedule.ui.navigation.NavDirection
import com.haooz.chedule.ui.navigation.PreferenceSettingsRoute
import com.haooz.chedule.ui.navigation.PrivacyPolicyRoute
import com.haooz.chedule.ui.navigation.ScheduleSettingsRoute
import com.haooz.chedule.ui.navigation.UpdatePreferencesRoute
import com.haooz.chedule.ui.navigation.UpdateSettingsRoute
import com.haooz.chedule.ui.navigation.WebDavSettingsRoute
import com.haooz.chedule.ui.navigation.WidgetIntroRoute
import com.haooz.chedule.ui.navigation.rememberNavTransitionState
import com.haooz.chedule.ui.navigation.settleOnGlobalCurve
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.overlay.LocalOverlayDialogBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.NavigationBackHandlerFor
import kotlin.math.roundToInt

/** 右栏页面壳：二级页无返回按钮，标题锁折叠态。 */
data class TabletSettingsPaneChrome(
    val showBackButton: Boolean = true,
    val forceCollapsedTitle: Boolean = false,
)

val LocalTabletSettingsPaneChrome = staticCompositionLocalOf { TabletSettingsPaneChrome() }

/** 右栏「路由 → 页面」注入口，宿主各 provide 自己的映射表。 */
val LocalTabletSettingsPaneRouteContent =
    staticCompositionLocalOf<@Composable (AppRouter, AppRoute) -> Unit> {
        { router, route -> DefaultTabletSettingsPaneRouteContent(router, route) }
    }

/** 宿主未注入时的兜底分发。 */
@Composable
private fun DefaultTabletSettingsPaneRouteContent(router: AppRouter, route: AppRoute) {
    when (route) {
        AppRoute.ScheduleSettings -> ScheduleSettingsRoute(router)
        AppRoute.PreferenceSettings -> PreferenceSettingsRoute(router)
        AppRoute.UpdateSettings -> UpdateSettingsRoute(router)
        AppRoute.UpdatePreferences -> UpdatePreferencesRoute(router)
        AppRoute.About -> AboutRoute(router)
        AppRoute.Changelog -> ChangelogRoute(router)
        AppRoute.License -> LicenseRoute(router)
        AppRoute.PrivacyPolicy -> PrivacyPolicyRoute(router)
        AppRoute.AppreciateAuthor -> AppreciateAuthorRoute(router)
        AppRoute.Communication -> CommunicationRoute(router)
        AppRoute.Feedback -> FeedbackRoute(router)
        AppRoute.FeedbackHistory -> FeedbackHistoryRoute(router)
        AppRoute.FeedbackRoadmap -> FeedbackRoadmapRoute(router)
        AppRoute.CourseReminder -> CourseReminderRoute(router)
        AppRoute.HolidaySettings -> HolidaySettingsRoute(router)
        AppRoute.CourseTimeSettings -> CourseTimeSettingsRoute(router)
        AppRoute.CourseManage -> CourseManageRoute(router)
        AppRoute.WidgetIntro -> WidgetIntroRoute(router)
        AppRoute.WebDavSettings -> WebDavSettingsRoute(router)
        AppRoute.AiImport -> AiImportRoute(router)
        AppRoute.EducationalImport -> EducationalImportRoute(router)
        AppRoute.ImportExport -> BackupAndMigrationRoute(
            router = router,
            mode = ScheduleDataManageMode.Import,
            switchableModes = listOf(
                ScheduleDataManageMode.Import,
                ScheduleDataManageMode.Export,
            ),
        )
        AppRoute.ScheduleBackup -> BackupCenterRoute(router = router)
        // Settings 会套娃；Weather / WebImport 无兜底实现
        AppRoute.Settings,
        AppRoute.Weather,
        AppRoute.WebImport,
        -> Box(Modifier.fillMaxSize())
    }
}

/** 左栏固定标题；右栏标题由页面自带，避免重影。 */
@Composable
fun TabletSettingsChromeOverlay() {
    val statusBar = mainWindowTopInset()
    val collapsedH = CollapsibleTopAppBarDefaults.CollapsedHeight
    val titleColor = MiuixTheme.colorScheme.onSurface
    val density = LocalDensity.current
    val widthDp = screenWidthDp()
    val statusBarPx = with(density) { statusBar.roundToPx() }
    val collapsedHPx = with(density) { collapsedH.roundToPx() }
    val collapsedTotalPx = with(density) {
        (com.haooz.chedule.ui.components.TabletNavSideInset +
            com.haooz.chedule.ui.components.TabletNavIconRailWidth).toPx()
    }
    val expandedWidthPx = with(density) {
        com.haooz.chedule.ui.components.tabletNavExpandedWidthDp(widthDp.toFloat()).toPx()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().zIndex(0.5f)
    ) {
        val maxWPx = with(density) { maxWidth.toPx() }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .tabletNavChromeTitleSlot(
                    maxWPx = maxWPx,
                    collapsedTotalPx = collapsedTotalPx,
                    expandedWidthPx = expandedWidthPx,
                    statusBarPx = statusBarPx,
                    heightPx = collapsedHPx,
                    isLeftColumn = true,
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = CommonStr.settings(),
                color = titleColor,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** 分屏/自由窗口下只有 systemBars 顶部不为 0。 */
@Composable
private fun mainWindowTopInset(): Dp =
    WindowInsets.systemBars.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()

private val TabletPaneBlurHeight: Dp
    @Composable get() = 80.dp + mainWindowTopInset()

/** 左栏顶部糊层，采样本栏 backdrop。 */
@Composable
private fun TabletPaneTopChrome(
    backdrop: com.haooz.softglass.backdrop.GlassBackdrop?,
    modifier: Modifier = Modifier,
) {
    val maskHeight = TabletPaneBlurHeight
    Box(modifier = modifier.fillMaxWidth().height(maskHeight)) {
        if (backdrop != null) {
            ProgressiveBlurTopBar(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize(),
                height = maskHeight,
                blurAlpha = 1f,
                content = {},
            )
        }
    }
}

/** 右栏转场：同全局路由的进度与曲线，不裁圆角。 */
@Composable
private fun TabletPaneNavHost(
    router: AppRouter,
    content: @Composable (AppRoute) -> Unit,
) {
    val progress = rememberNavTransitionState(initialValue = 1f)
    var lastRoute by remember { mutableStateOf(router.current) }
    var outgoing by remember { mutableStateOf<AppRoute?>(null) }

    // 组合期同步，晚一帧会闪
    if (lastRoute !== router.current) {
        val interrupted = outgoing
        lastRoute = router.current
        outgoing = null
        if (router.lastDirection == NavDirection.Pop) {
            outgoing = router.lastPopped
        } else if (router.depth > 1 && interrupted?.id != router.current?.id) {
            progress.snapTo(0f)
        }
    }

    LaunchedEffect(router.current) {
        when {
            router.lastDirection == NavDirection.Pop -> {
                val (dur, easing) = settleOnGlobalCurve(
                    progress.value, 0f, NAV_EXIT_EASING, NAV_EXIT_DURATION, true, 0
                )
                if (progress.animateTo(0f, tween(dur, easing = easing))) {
                    outgoing = null
                    progress.snapTo(1f)
                }
            }
            // 左栏换的是栈底二级页，静态替换；只有二级→三级才播入场
            router.depth > 1 -> {
                val (dur, easing) = settleOnGlobalCurve(
                    progress.value, 1f, NAV_ENTER_EASING, NAV_ENTER_DURATION, false, 0
                )
                progress.animateTo(1f, tween(dur, easing = easing))
            }
        }
    }

    val layers = router.routes.ifEmpty { listOf(AppRoute.ScheduleSettings) } +
        listOfNotNull(outgoing)
    Box(Modifier.fillMaxSize()) {
        val top = layers.lastIndex
        layers.forEachIndexed { index, route ->
            val isTop = index == top
            // 黑幕压暗下层，不随页面平移
            if (isTop && top > 0) {
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val a = if (progress.value < 0.999f) NAV_BG_DIM * progress.value else 0f
                        if (a > 0.001f) drawRect(Color.Black.copy(alpha = a))
                    }
                )
            }
            key(index) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // 稳态时下层被盖住，停绘省一屏
                        .then(
                            if (isTop) Modifier
                            else Modifier.drawWithContent {
                                if (progress.value < 0.999f) drawContent()
                            }
                        )
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                val f = if (isTop) 1f - progress.value
                                else -NAV_PARALLAX * progress.value
                                placeable.place((f * placeable.width).roundToInt(), 0)
                            }
                        }
                ) {
                    // 按层给口径：栈底二级页无返回按钮，三级页起才有
                    CompositionLocalProvider(
                        LocalTabletSettingsPaneChrome provides TabletSettingsPaneChrome(
                            showBackButton = index > 0,
                            forceCollapsedTitle = true,
                        )
                    ) { content(route) }
                }
            }
        }
    }
}

/** 左一级 / 右二级三级。 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun TabletSettingsScreen(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    shiftViewModel: ShiftViewModel,
    isShiftMode: Boolean,
    onExitShiftMode: () -> Unit,
    onEnterShiftMode: () -> Unit = {},
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    onSendTestNotification: () -> Unit = {},
    isIslandSupported: () -> Boolean = { false },
    testActionLabel: String? = null,
    onRefreshWidgets: () -> Unit = {},
    onHolidayDataChanged: () -> Unit = {},
    fetchHolidayEntries: (suspend (year: Int) -> List<HolidayEntry>)? = null,
    showUpdateSettings: Boolean = true,
) {
    // 右栏局部栈：二级页是根，三级页 push 进来替换右栏
    val globalRouter = LocalAppRouter.current
    val paneRouter = remember(globalRouter) {
        AppRouter().also {
            it.navigate(AppRoute.ScheduleSettings)
            // 全屏页：Web 导入、开源协议、隐私政策，不塞进右栏
            it.nativeRouteHandler = { route ->
                if (route == AppRoute.WebImport ||
                    route == AppRoute.License ||
                    route == AppRoute.PrivacyPolicy
                ) {
                    globalRouter.navigate(route)
                    true
                } else false
            }
        }
    }
    val paneContent = LocalTabletSettingsPaneRouteContent.current

    // 三级页压在右栏局部栈里，返回键要先退二级页；宿主那层只在全局有浮层时才接管，
    // 不拦的话系统返回会直接退出应用。
    val paneDepth = paneRouter.depth
    NavigationBackHandlerFor(
        enabled = paneDepth > 1 && !globalRouter.hasOverlay,
        onBackCompleted = { paneRouter.popBack() },
        onBackCancelled = {},
    )

    fun openFromLeft(route: AppRoute) {
        // 清空旧栈，不残留上一次的三级页
        while (paneRouter.popBack()) { }
        paneRouter.navigate(route)
    }

    val paneSurface = MiuixTheme.colorScheme.surface
    val chromeTop = mainWindowTopInset() +
        CollapsibleTopAppBarDefaults.CollapsedHeight + 12.dp
    // 浮层采样层：页级 backdrop 只录一栏，浮层压在两栏上会一半采样到空层
    val overlayDialogBackdrop = rememberLayerBackdrop {
        drawRect(paneSurface)
        drawContent()
    }

    // 底色铺满，内容避让侧栏
    Box(Modifier.fillMaxSize()) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(paneSurface)
            .then(
                if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) Modifier
                else Modifier.layerBackdrop(overlayDialogBackdrop)
            )
            .then(LocalPageStartPadding.current)
    ) {
        val leftWidth = screenWidthDp().dp * 0.39f
        val rightWidth = maxWidth - leftWidth
        val dividerColor = if (com.haooz.chedule.ui.utils.isAppDarkTheme()) {
            Color.White.copy(alpha = 0.1f)
        } else {
            Color.Black.copy(alpha = 0.06f)
        }
        val surfaceColor = MiuixTheme.colorScheme.surface
        val leftPaneBackdrop = rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // 左栏：手机端设置列表
            Box(
                modifier = Modifier
                    .width(leftWidth)
                    .fillMaxHeight()
                    .background(surfaceColor)
            ) {
                // 列表与搜索框共用一份状态
                val leftSearchState = rememberSettingsSearchState()
                val searchVisible = !isShiftMode
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) Modifier
                            else Modifier.layerBackdrop(leftPaneBackdrop)
                        )
                ) {
                    // SettingsScreen 组合期会取 LocalAppRouter，宿主没 provide 会崩
                    CompositionLocalProvider(LocalAppRouter provides paneRouter) {
                        SettingsScreen(
                            viewModel = viewModel,
                            scheduleViewModel = scheduleViewModel,
                            settingsViewModel = settingsViewModel,
                            shiftViewModel = shiftViewModel,
                            isShiftMode = isShiftMode,
                            onExitShiftMode = onExitShiftMode,
                            onEnterShiftMode = onEnterShiftMode,
                            navBarStyle = "standard",
                            settingsScrollBehavior = null,
                            showUpdateSettings = showUpdateSettings,
                            onOpenRoute = { route -> openFromLeft(route) },
                            // 栈底就是左栏选中的那一项，进三级页后仍然保持按下态
                            selectedRoute = paneRouter.routes.firstOrNull(),
                            searchState = leftSearchState,
                            showSearch = searchVisible,
                            listTopPaddingOverride = chromeTop +
                                (if (searchVisible) SettingsSearchBarBlockHeight else 0.dp),
                        )
                    }
                }
                TabletPaneTopChrome(
                    backdrop = leftPaneBackdrop,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                // 搜索框画在糊层之上
                if (searchVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                y = mainWindowTopInset() +
                                    CollapsibleTopAppBarDefaults.CollapsedHeight
                            )
                            .fillMaxWidth()
                    ) {
                        SettingsSearchHeader(
                            state = leftSearchState,
                            backdrop = leftPaneBackdrop,
                        )
                    }
                }
            }

            // 右栏：手机端二级 / 三级路由页。转场时下层左移会越过左边界，裁到栏内
            Box(
                modifier = Modifier
                    .width(rightWidth)
                    .fillMaxHeight()
                    .background(surfaceColor)
                    .clipToBounds()
            ) {
                CompositionLocalProvider(
                    LocalAppRouter provides paneRouter,
                    // 浮层改用整页采样层，否则跨栏浮层背面一半是黑的
                    LocalOverlayDialogBackdrop provides
                        if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) null
                        else overlayDialogBackdrop,
                ) {
                    TabletPaneNavHost(paneRouter) { route -> paneContent(paneRouter, route) }
                }
            }
        }

        // 分界线画在 Row 之上，画在栏内会被糊层糊掉
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = leftWidth)
                .width(1.dp)
                .fillMaxHeight()
                .background(dividerColor)
        )
    }
        // 左栏「我的」标题与设置页同层：切页 / 转场时跟着页面一起动，
        // 不再由主界面外层按 currentPage 单独叠一层（避免串页与双份维护）。
        TabletSettingsChromeOverlay()
    }
}
