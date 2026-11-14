package com.haooz.chedule.ui.activities.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.appearance.AppearanceConfig
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.NoopUpdateController
import com.haooz.chedule.data.platform.Notice
import com.haooz.chedule.data.platform.NoticeFetcher
import com.haooz.chedule.data.platform.randomUuidV4
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.activities.classschedule.AppMinuteClock
import com.haooz.chedule.ui.activities.classschedule.LandRippleSpec
import com.haooz.chedule.ui.activities.classschedule.LocalAppMinuteClock
import com.haooz.chedule.ui.activities.classschedule.LocalLandRipple
import com.haooz.chedule.ui.activities.classschedule.MainScheduleScreen
import com.haooz.chedule.ui.activities.classschedule.SchedulePageChrome
import com.haooz.chedule.ui.activities.classschedule.ScheduleViewMode
import com.haooz.chedule.ui.activities.classschedule.millisUntilNextMinute
import com.haooz.chedule.ui.activities.classschedule.nowMinuteOfDay
import com.haooz.chedule.ui.activities.today.TodayPageChrome
import com.haooz.chedule.ui.activities.today.TodayScreen
import com.haooz.chedule.ui.components.HorizontalPageSwitch
import com.haooz.chedule.ui.components.SoftGlassSurface
import com.haooz.chedule.ui.components.BackToNowFloatingButton
import com.haooz.chedule.ui.components.LiquidNavigationRail
import com.haooz.chedule.ui.components.LocalChromeStartInset
import com.haooz.chedule.ui.components.LocalPageStartPadding
import com.haooz.chedule.ui.components.PublishNativeBottomBar
import com.haooz.chedule.ui.components.ScheduleBottomBar
import com.haooz.chedule.ui.components.scheduleBottomBarHeight
import com.haooz.chedule.ui.components.ShareImportDialog
import com.haooz.chedule.ui.components.TabletNavSamplingTracker
import com.haooz.chedule.ui.components.TabletNavSideState
import com.haooz.chedule.ui.components.UpdateDialog
import com.haooz.chedule.ui.components.tabletNavExpandSampleTrack
import com.haooz.chedule.ui.components.tabletNavRailStartPadding
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.LocalMainLayerRedrawSignal
import com.haooz.chedule.ui.screens.AddCourseDialog
import com.haooz.chedule.ui.screens.ShiftScheduleScreen
import com.haooz.chedule.ui.screens.TabletCourseManagePane
import com.haooz.chedule.ui.screens.TabletSwitchSchedulePane
import com.haooz.chedule.ui.theme.rememberAppThemeController
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.LocalForcedDarkTheme
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.LocalNativeTopBarHost
import com.haooz.chedule.ui.utils.LocalNativeBottomBarHost
import com.haooz.chedule.ui.utils.NativeTopBarGroup
import com.haooz.chedule.ui.utils.NativeTopBarHost
import com.haooz.chedule.ui.utils.NativeTopBarMenuItem
import com.haooz.chedule.ui.utils.NativeTopBarState
import com.haooz.chedule.ui.utils.hostVerticalBarEdge
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.addScreenCornerRect
import com.haooz.chedule.ui.utils.bottomFloatingPadding
import com.haooz.chedule.ui.utils.buildShareScheduleMap
import com.haooz.chedule.ui.utils.rememberAppSettingDark
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.colorControls
import com.haooz.softglass.platform.isRenderEffectSupported
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults.CollapsedHeight
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

/** 其余弹窗：退场动画（最长约 320ms）播完再摘采样层，那时屏幕上已经没有它的玻璃了 */
private const val OVERLAY_GLASS_RELEASE_DELAY_MS = 420L

/**
 * 课表主界面的「主内容层」：整屏 Box（模糊 / 裁切 / 快照录制）+ Scaffold + 各 tab 页。
 *
 * 层级自下而上固定为：共享壁纸层 → 各页内容层 → 底栏 → 页面顶栏。顶栏与菜单画在各自的
 * 页面内部，跟着页面一起平移；只有壁纸是屏幕坐标的静态底图。
 *
 * 参数只放外壳算好的局部值，其余一律走 [CourseScheduleAppState]（函数体内 `with(state)`）。
 * 注意：这里读的很多是 state 的**普通字段**，它们不参与订阅，靠外壳重组把新值带下来 ——
 * 所以不要把这段拆成「参数只有 state」的独立 composable，那样会被跳过、页面不再更新。
 */
@Composable
internal fun CourseScheduleAppMainContent(
    state: CourseScheduleAppState,
    displayAppearance: AppearanceConfig,
    chromeBackdrop: GlassBackdrop,
    /** 采样层重录指纹：结构量变化时强制重录一帧 */
    liquidGlassRecordKey: List<Any?>,
    liquidGlassMustRecord: () -> Boolean,
    wallpaperImage: ImageBitmap?,
    /** 课表外观页编辑前的壁纸（退出前一直显示这一张） */
    originalWallpaperImage: ImageBitmap?,
    softGlassSurface: SoftGlassSurface?,
) {
    with(state) {
        val density = densityProvider
        // ── 采样源 ────────────────────────────────────────────────────────────
        // 柔光采样源由外壳统一创建（见 rememberScheduleGlassSurface）：网格卡片、分界带、
        // 落点遮罩与长按/交换浮层共用同一个实例，玻璃参数不可能漂移。
        // 卡片玻璃在各自 draw 里按窗口坐标采样壁纸，而祖先层「只平移、不重绘子树」时
        // positionInWindow() 变了却没人重画 —— 玻璃就贴着旧像素走（贴纸感 / 错位）。
        // 滚动 / 翻页 / 侧栏伸缩逐帧订阅；二级页转场是整层平移，卡片要相对不动，
        // 只在转场结束、主界面归位后由 mainLayerRedraw() 补一帧重采样。
        val mainLayerRedraw = LocalMainLayerRedrawSignal.current
        val tabGlassRedraw: () -> Unit = remember(mainTabPosition, mainLayerRedraw) {
            {
                // 主 tab pager 整体平移
                mainTabPosition.offsetFraction
                // pad 侧栏展开 / 收起：页内留白在 layout 期插值，卡片跟着横移
                TabletNavSideState.expanded
                TabletNavSideState.expandProgress.floatValue
                // 退出二级页、整层归位后补一帧；转场过程中它不变，卡片玻璃不会自己漂
                mainLayerRedraw()
            }
        }

        // 无材质档：玻璃全关，内容层不挂采样层。
        fun pageContentLayer(backdrop: LayerBackdrop): Modifier =
            if (AppMaterialSettings.isNoneTier()) Modifier
            else Modifier.layerBackdrop(backdrop, liquidGlassRecordKey, liquidGlassMustRecord)

        /** 调课 / 覆盖 / 粘贴后，原位置露出的非本周课程淡入放大。 */
        fun flashAnimateIn(ids: Set<String>) {
            animateInCourseIds = ids
            coroutineScope.launch {
                delay(350.milliseconds)
                animateInCourseIds = emptySet()
            }
        }

        // 连贯动画期间下层的实时模糊半径（dp）：长按菜单优先，其次详情 morph；与宿主侧口径一致
        fun currentDetailBlurDp(): Float =
            if (shortcutMenuBlurRadius.value > 0.01f) shortcutMenuBlurRadius.value
            // 宿主已自己压一层系统 material（iOS）：主内容不再叠第二层。
            // 长按菜单不走宿主，仍在上面那支里自糊。
            else if (hostNavigation?.ownsDetailBackdropBlur == true) 0f
            // 详情背景已由快照层自己模糊
            else if (detailBackgroundSnapshot != null) 0f
            else managePageBlurRadius.value

        // 顶栏/底栏那两层采样层的重录谓词。⚠️ 不能复用 liquidGlassMustRecord：那份闭包里
        // 带着 rail 强制续录的帧计数与自己的比较状态，两个节点各调一次会把计数双倍扣掉。
        // 这两层只跟滚动 + nav 几何，自己另起一份追踪器。
        val chromeBarNavSampling = remember { TabletNavSamplingTracker() }
        val chromeBarMustRecord = remember(
            scheduleScrollState,
            todayListScrollInProgress,
            weekPageState,
            todayPageState,
            mainTabPosition,
        ) {
            {
                chromeBarNavSampling.moved() ||
                        scheduleScrollState.isScrollInProgress ||
                        todayListScrollInProgress.value ||
                        weekPageState.isAnimating ||
                        todayPageState.isAnimating ||
                        mainTabPosition.isAnimating
            }
        }

        // 只认「几何变换」：主内容层要不要挂离屏合成、要不要按圆角裁切，全看这里。
        val mainLayerNeeded by remember {
            derivedStateOf {
                isCustomizeExiting ||
                        isWindowCutoutActive ||
                        showSwitchSchedule ||
                        showCustomizePage ||
                        shortcutMenuBlurRadius.value > 0.01f ||
                        // 宿主接管页面级模糊时，这个半径不会再产生 renderEffect，
                        // 别为它白开一层全屏离屏合成
                        (managePageBlurRadius.value > 0.01f &&
                                hostNavigation?.ownsDetailBackdropBlur != true) ||
                        cutoutMainScale.value != 1f ||
                        customizeExitScale.value != 1f ||
                        sheetOffsetY.value != 0f
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (mainLayerNeeded) {
                        Modifier.graphicsLayer {
                            val blurAnim = currentDetailBlurDp()
                            // renderEffect 需要 Android 12+，低版本退化为无模糊
                            renderEffect = if (blurAnim > 0.01f && isRenderEffectSupported()) {
                                // 不能用 `blurAnim * density`：外层同名 Density 局部量会遮蔽隐式 receiver
                                val blurPx = blurAnim.dp.toPx()
                                // 同 Modifier.blur 的 Clamp 边界处理
                                BlurEffect(null, blurPx, blurPx, TileMode.Clamp)
                            } else null
                            val exitScale = if (isCustomizeExiting) customizeExitScale.value else 1f
                            val cutoutScale = cutoutMainScale.value
                            // 开洞时由 cutoutScale 控制；进出场统一由快照覆盖层处理，避免叠加闪烁
                            val effectiveScale = if (isCustomizeExiting && isWindowCutoutActive) {
                                cutoutScale
                            } else {
                                exitScale * cutoutScale
                            }
                            scaleX = effectiveScale
                            scaleY = effectiveScale
                            // 与 CustomizeScheduleScreen 共享同一 Animatable，像素级同帧
                            val sheetScale = cutoutMainScale.value
                            val sheetScaleProg =
                                ((sheetScale - 0.65f) / (1f - 0.65f)).coerceIn(0f, 1f)
                            val contentOffsetY = sheetOffsetY.value * (1f - sheetScaleProg)
                            translationY = contentOffsetY
                            // 同一个 graphicsLayer 块在**绘制阶段**跑
                            pushCutoutChromeOffset((contentOffsetY / density.density).toDouble())
                            if (isCustomizeExiting) {
                                transformOrigin = TransformOrigin(0.5f, 0.58f)
                            }
                            if (isWindowCutoutActive) {
                                // 使用 CustomizeScheduleScreen 传回的裁剪中心比例，保证两者完全对齐
                                transformOrigin = TransformOrigin(cutoutCenterXRatio, cutoutCenterYRatio)
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .then(
                    // 下层不再缩放，无需按缩放重裁圆角；仅搭配页退出时裁成屏幕圆角
                    Modifier.drawWithContent {
                        val animClipPx =
                            if (isCustomizeExiting) screenCornerRadius else ScreenCornerRadiiPx.Zero
                        if (animClipPx.maxRadius > 0f) {
                            val path = Path().apply {
                                addScreenCornerRect(
                                    width = size.width,
                                    height = size.height,
                                    corners = animClipPx
                                )
                            }
                            clipPath(path) {
                                this@drawWithContent.drawContent()
                            }
                        } else {
                            drawContent()
                        }
                    }
                )
                .then(
                    Modifier.drawWithContent {
                        // 逐帧把页面级模糊半径推给宿主（iOS 原生 chrome 与弹窗层不在主内容模糊层内）
                        hostNavigation?.updateDetailBackgroundBlur(managePageBlurRadius.value)
                        // 仅在被请求时录制一帧，避免每帧重绘整棵主内容树
                        if (mainSnapshotRequester.lastRecordedToken != mainSnapshotToken) {
                            screenGraphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            mainSnapshotRequester.lastRecordedToken = mainSnapshotToken
                            drawLayer(screenGraphicsLayer)
                        } else {
                            drawContent()
                        }
                    }
                )
        ) {
            val hostNav = LocalHostNavigation.current
            val nativeBarInset = (hostNav?.topBarInset ?: 0.0).dp
            // 横屏摄像头安全区宽度：只用于页内留白，不缩容器
            val startInsetDp = WindowInsets.safeDrawing.only(WindowInsetsSides.Left).union(
                WindowInsets(
                    left = (hostNav?.leftSafeAreaInset ?: 0.0).toFloat().dp,
                    right = 0.dp,
                )
            ).asPaddingValues().calculateLeftPadding(LocalLayoutDirection.current)
            // 右侧避让量：右下角竖排的原生 tab bar 占的空间（折叠屏 iPhone 折叠态）。
            val titleEndPadding = (hostNav?.rightSafeAreaInset ?: 0.0).toFloat().dp
            // 右上角两颗按钮的唯一发布点：今日页与课程表页各一组，跟着主 pager 平移。
            // 读 pager 的位移只在这个小函数里，重组范围不外溢到整页。
            val nativeBarHost = LocalNativeTopBarHost.current
            if (nativeBarHost?.ownsTopRight == true) {
                val nativeBarRouter = LocalAppRouter.current
                PublishNativeTopBarButtons(
                    host = nativeBarHost,
                    position = mainTabPosition,
                    vertical = useVerticalMainPager,
                    isTablet = isTablet,
                    isShiftMode = isShiftMode,
                    screenWidthDp = with(androidx.compose.ui.platform.LocalDensity.current) {
                        screenWPx.toDp().value
                    },
                    screenHeightDp = with(androidx.compose.ui.platform.LocalDensity.current) {
                        screenHPx.toDp().value
                    },
                    titleEndPadding = titleEndPadding,
                    onSwitchSchedule = { openScheduleLibrary() },
                    onJumpWeek = { viewModel.showJumpWeekDialog() },
                    onJumpToDate = { todayJumpToDateTrigger++ },
                    onCourseManage = { nativeBarRouter.navigate(AppRoute.CourseManage) },
                    onEnterCustomize = enterCustomizePage,
                )
            }
            // 底栏语义发布点：和顶栏一样放在 Scaffold **之外**。
            // 宿主接管时 Scaffold 的 bottomBar 槽里只有一个空 Box（ScheduleBottomBar 根本不组合），
            // 把发布写进那个分支等于一次都不发 —— 宿主拿不到状态就是整条不画。
            val nativeBottomBarHost = LocalNativeBottomBarHost.current
            if (nativeBottomBarHost?.ownsBottomBar == true && navBarStyle != "rail" && !nativeBottomBar) {
                val nativeBottomBarRouter = LocalAppRouter.current
                PublishNativeBottomBar(
                    host = nativeBottomBarHost,
                    isShiftMode = isShiftMode,
                    selectedTab = selectedTab,
                    onTabSelected = onMainTabSelected,
                    onOpenSettings = { nativeBottomBarRouter.navigate(AppRoute.Settings) },
                )
            }
            // 底栏那层录制**按需挂载**：只有真的有浮层在采「比底栏更高一层」的采样层时
            // 才挂上，否则滚动帧里会白白多录一条全宽条带。
            // 顶栏已经下放到各页面内、随内容层一起录制，这里不再单独挂条带。
            // ⚠️ Scaffold 先组 popup → 底栏，**最后**才组 content，所以这里要读的
            // 开关不能是 content 槽里的局部量（那时底栏已经组完了）。
            // 这两个 collectAsState 因此提前到 Scaffold 之外订阅。
            val showJumpWeekDialog by viewModel.showJumpWeekDialog.collectAsState()
            val showAddDialog by viewModel.showAddDialog.collectAsState()
            var notice by remember { mutableStateOf<Notice?>(null) }
            var updateDialogShowing by remember { mutableStateOf(false) }
            val otherGlassWanted = showJumpWeekDialog ||
                showAddDialog ||
                notice != null ||
                sharedFileIntent != null ||
                showDeleteConfirmDialog ||
                showPasteRangeDialog ||
                showRescheduleConflictDialog ||
                updateDialogShowing
            // 其余弹窗一路：退场动画播完屏幕上已经没有它的玻璃了，
            // 什么时候摘都看不见，按固定延时摘即可
            var otherGlassActive by remember { mutableStateOf(otherGlassWanted) }
            if (otherGlassWanted && !otherGlassActive) otherGlassActive = true
            LaunchedEffect(otherGlassWanted) {
                if (otherGlassWanted) return@LaunchedEffect
                delay(OVERLAY_GLASS_RELEASE_DELAY_MS.milliseconds)
                otherGlassActive = false
            }
            // 无材质档：玻璃全关，底栏采样加层不挂
            // 「更多」菜单已经在页面内、采的是「壁纸 + 本页内容」的组合层，
            // 不再需要为了它挂底栏那一层。
            val overlayGlassActive = otherGlassActive && !AppMaterialSettings.isNoneTier()
            val scaffoldContent = @Composable {
                Scaffold(
                    modifier = Modifier
                        .consumeWindowInsets(PaddingValues(top = nativeBarInset)),
                    bottomBar = {
                        // 平板导航在叠层；这里不占 Scaffold bottomBar，避免全屏挡住内容滚动
                        if (navBarStyle != "rail" && !nativeBottomBar) {
                            if (LocalNativeBottomBarHost.current?.ownsBottomBar == true) {
                                // 宿主画底栏（ArkUI 在 Compose 之外）：这里只留同高空位，
                                // 否则内容会一直铺到屏幕最底、最后一行被底栏盖住。
                                // 语义在上面那条 PublishNativeBottomBar 里发，这里一个像素都不画。
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(scheduleBottomBarHeight())
                                )
                            } else {
                                // 底栏再录一层：弹窗层的玻璃（下拉菜单 / OverlayDialog）要采到它。
                                // 只在真有浮层在采时挂——没挂时 backdrop.layerCoordinates 恒 null，
                                // 组合采样自动退化成「只有内容」那一层，即收起态的行为。
                                Box(
                                    modifier = if (overlayGlassActive) {
                                        // 底栏层必须用自己那份 mustRecord：liquidGlassMustRecord
                                        // 带强制续录帧计数，两个节点共用会双倍扣减。
                                        Modifier.layerBackdrop(
                                            backdrop = bottomBarSampleBackdrop,
                                            recordKey = liquidGlassRecordKey,
                                            mustRecord = chromeBarMustRecord,
                                        )
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    ScheduleBottomBar(
                                        navBarStyle = navBarStyle,
                                        isShiftMode = isShiftMode,
                                        selectedTab = selectedTab,
                                        onTabSelected = onMainTabSelected,
                                        // 设置是二级页：只压栈，不切 pager
                                        onOpenSettings = { router.navigate(AppRoute.Settings) },
                                        liquidGlassBackdrop = chromeBackdrop,
                                    )
                                }
                            }
                        }
                    },
                    // 「更多」按钮/下拉菜单已在各页面内组合，随页面一起平移；
                    // 这里只剩弹窗宿主，保持弹窗始终盖在页面顶栏之上。
                    popupHost = {
                        MiuixPopupHost()
                    }
                ) {
                    // 详情 morph 期间底下的真实源卡片必须继续画
                    // 不门控 combinations.isEmpty()：网格只依赖 viewModel，与壁纸加载解耦
                    // 不在这里录整页：整页会把页面内顶栏/菜单也录进去，形成循环采样；
                    // 内容由各页面自己的内容层录制，壁纸单独一层，外层只做组合采样。
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 共享壁纸层：铺满全屏（含侧栏下），与 rail 让位无关，侧栏伸缩时像素不动。
                        // 壁纸任何切页都钉死不动：课程表→设置靠「圆角设置卡片滑过来盖住壁纸」。
                        val sharedWallpaperBitmap =
                            if (showCustomizePage && !isWindowCutoutActive) originalWallpaperBitmap
                            else wallpaperBitmap
                        val sharedWallpaperOffset =
                            if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset
                            else wallpaperOffset
                        val sharedWallpaperScale =
                            if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale
                            else wallpaperScale
                        // 可见性用 derivedStateOf：pager 每帧变 offset 时不重组父层/HorizontalPager
                        val showSharedWallpaperLayer by remember(
                            isShiftMode,
                            sharedWallpaperBitmap,
                            showCustomizePage,
                            mainTabPosition,
                            navBarStyle,
                            selectedTab,
                        ) {
                            derivedStateOf {
                                // 搭配页/开洞编辑：必须始终显示壁纸层，否则选完壁纸「没反应」
                                if (showCustomizePage && sharedWallpaperBitmap != null) {
                                    return@derivedStateOf true
                                }
                                val page = mainTabPosition.currentPage
                                val off = abs(mainTabPosition.offsetFraction)
                                // 平板：壁纸层全程可见。侧栏常驻且玻璃采的就是这一层，
                                // 只要有一页不铺壁纸，侧栏就会采到空白（表现成深色块）。
                                // 手机：今日/课表页铺壁纸，切回主页面时随滑动提前铺上。
                                val onMainPage = if (navBarStyle == "rail") {
                                    true
                                } else {
                                    page != 2 || off > 0.01f
                                }
                                !isShiftMode && sharedWallpaperBitmap != null && onMainPage
                            }
                        }
                        if (showSharedWallpaperLayer && sharedWallpaperBitmap != null) {
                            // 全屏垫背景色 + 裁切：页滑走露出的空隙由背景色/壁纸垫住（侧栏下始终有内容，不出漏）
                            val wallpaperPadColor =
                                MiuixTheme.colorScheme.surface
                            // 壁纸层只跟壁纸自身状态走；页面平移/切页不该让它重录
                            val wallpaperLayerRecordKey = remember(
                                sharedWallpaperBitmap,
                                sharedWallpaperOffset,
                                sharedWallpaperScale,
                                displayAppearance.wallpaperBrightness,
                                displayAppearance.wallpaperBlur,
                                wallpaperPadColor,
                            ) {
                                var key = sharedWallpaperBitmap.hashCode()
                                key = 31 * key + sharedWallpaperOffset.hashCode()
                                key = 31 * key + sharedWallpaperScale.hashCode()
                                key = 31 * key + displayAppearance.wallpaperBrightness.hashCode()
                                key = 31 * key + displayAppearance.wallpaperBlur.hashCode()
                                key = 31 * key + wallpaperPadColor.value.toLong().hashCode()
                                key
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(wallpaperPadColor)
                                    .clipToBounds()
                                    .then(
                                        // 无材质档：玻璃全关，壁纸层不需要录；壁纸直接画
                                        if (AppMaterialSettings.isNoneTier()) {
                                            Modifier
                                        } else {
                                            Modifier.layerBackdrop(
                                                backdrop = wallpaperBackdrop,
                                                recordKey = wallpaperLayerRecordKey,
                                            )
                                        }
                                    )
                            ) {
                                val sharedMinScale =
                                    remember(sharedWallpaperBitmap, screenWPx, screenHPx) {
                                        if (sharedWallpaperBitmap.width > 0 && sharedWallpaperBitmap.height > 0) {
                                            val fit = minOf(
                                                screenWPx / sharedWallpaperBitmap.width,
                                                screenHPx / sharedWallpaperBitmap.height
                                            )
                                            val cover = maxOf(
                                                screenWPx / sharedWallpaperBitmap.width,
                                                screenHPx / sharedWallpaperBitmap.height
                                            )
                                            if (fit > 0f) cover / fit else 1f
                                        } else 1f
                                    }
                                val sharedBrightness = displayAppearance.wallpaperBrightness
                                val sharedBrightnessFilter = remember(sharedBrightness) {
                                    if (sharedBrightness != 0f) {
                                        val b = (1f + sharedBrightness / 50f).coerceIn(0f, 2f)
                                        ColorFilter.colorMatrix(
                                            ColorMatrix(
                                                floatArrayOf(
                                                    b, 0f, 0f, 0f, 0f,
                                                    0f, b, 0f, 0f, 0f,
                                                    0f, 0f, b, 0f, 0f,
                                                    0f, 0f, 0f, 1f, 0f
                                                )
                                            )
                                        )
                                    } else null
                                }
                                val sharedBlurEffect = remember(displayAppearance.wallpaperBlur) {
                                    val blurRadiusPx = 24f * density.density
                                    if (displayAppearance.wallpaperBlur && blurRadiusPx > 0f && isRenderEffectSupported()) {
                                        BlurEffect(null, blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                                    } else null
                                }
                                Image(
                                    bitmap = sharedWallpaperBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val s = maxOf(sharedWallpaperScale, sharedMinScale)
                                            scaleX = s
                                            scaleY = s
                                            // 缩放/平移裁在自身 bounds 内，避免突出课程表页画进侧栏
                                            clip = true
                                            // 只用用户自己设的偏移：切页时壁纸是静止底图，
                                            // 设置页以不透明面从它上面滑过来盖住
                                            translationX = sharedWallpaperOffset.x
                                            translationY = sharedWallpaperOffset.y
                                            renderEffect = sharedBlurEffect
                                        },
                                    contentScale = ContentScale.Fit,
                                    colorFilter = sharedBrightnessFilter
                                )
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            // 课表页与排班页共用同一套顶栏参数，只有内容层 / 主题 / 模式不同。
                            @Composable
                            fun scheduleChrome(
                                contentBackdrop: LayerBackdrop,
                                forcedDarkTheme: Boolean?,
                                shiftMode: Boolean,
                                onCourseManage: () -> Unit,
                            ) = SchedulePageChrome(
                                currentWeek = currentWeek,
                                totalWeeks = totalWeeks,
                                isHoliday = viewingIsHoliday,
                                dayRange = dayRange,
                                currentDayOfWeek = currentDayOfWeek,
                                weekDates = weekDates,
                                isReorganized = teachingWeekRules.isNotEmpty(),
                                forcedDarkTheme = forcedDarkTheme,
                                wallpaperBackdrop = if (showSharedWallpaperLayer) wallpaperBackdrop else null,
                                contentBackdrop = contentBackdrop,
                                moreMenu = PageMoreMenuState(
                                    show = showMorePopup,
                                    onDismiss = { showMorePopup = false },
                                    onExpand = { showMorePopup = true },
                                    fraction = morePopupFraction,
                                    enabled = navBarStyle != "rail" && hostNavigation == null,
                                    native = LocalNativeTopBarHost.current?.ownsTopRight == true,
                                ),
                                onOpenSwitchSchedule = { openScheduleLibrary() },
                                onJumpWeek = { viewModel.showJumpWeekDialog() },
                                onEnterCustomize = enterCustomizePage,
                                onCourseManage = onCourseManage,
                                isShiftMode = shiftMode,
                                titleEndPadding = titleEndPadding,
                                blurResampleKey = railBlurResampleEpoch.intValue,
                                blurSampleTrack = tabletNavExpandSampleTrack,
                                contentRecordKey = liquidGlassRecordKey,
                                contentMustRecord = liquidGlassMustRecord,
                            )

                            // 主 tab 仅由底栏 / 侧栏点击切换，关闭用户手势翻页；方向跟原生宿主 / 平板 rail 一致。
                            // pager 不做整体内缩（折叠屏 iPhone 折叠态右下角是竖排原生 tab bar）
                            val pageRightInset = (hostNav?.rightSafeAreaInset ?: 0.0).toFloat().dp
                            val pageInsetModifier = if (pageRightInset > 0.dp) {
                                Modifier.padding(end = pageRightInset)
                            } else {
                                Modifier
                            }
                            val mainPagerModifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(mainContentNestedScroll)
                            val mainPagerPageKey: (Int) -> String = { page ->
                                if (isShiftMode) "shift-$page" else "main-$page"
                            }
                            // 每页自己留白，pager 容器保持全屏，左右滑动不裁内容
                            val pageStartModifier = Modifier
                                .padding(start = startInsetDp)
                                .then(
                                    if (navBarStyle == "rail") tabletNavRailStartPadding()
                                    else Modifier
                                )
                            val mainPagerPageBody: @Composable (Int) -> Unit = { page ->
                                if (!isShiftMode) {
                                    when (page) {
                                        0 -> {
                                            // 内容避让右侧栏；页面背景（共享壁纸）仍然铺到屏幕边缘
                                            Box(Modifier.fillMaxSize().then(pageInsetModifier)) {
                                                // 今日页主题预先固定，切 tab 不跟着 chrome 闪一帧
                                                val todayForced =
                                                    if (captureThemeActive) captureThemeIsDark else todayPageForcedDark
                                                val todayDark = todayForced ?: appSettingDark
                                                val todayThemeController =
                                                    rememberAppThemeController(isDark = todayDark)
                                                MiuixTheme(controller = todayThemeController) {
                                                    CompositionLocalProvider(LocalForcedDarkTheme provides todayForced) {
                                                        val todayPageRouter = LocalAppRouter.current
                                                        TodayScreen(
                                                            viewModel = viewModel,
                                                            settingsViewModel = settingsViewModel,
                                                            // 今日页课程列表不直接进详情；这个只传给详情弹窗：
                                                            // 点弹窗卡片进全屏时要藏掉源卡片，morph 才干净。
                                                            hiddenCourseIds = hiddenCourseIds,
                                                            holidayDataTick = resumeCount,
                                                            onCourseClick = { c, l, t, w, h, layer, hideId, week ->
                                                                openCourseDetail(
                                                                    courses = c,
                                                                    cardLeft = l,
                                                                    cardTop = t,
                                                                    cardWidth = w,
                                                                    cardHeight = h,
                                                                    cardLayer = layer,
                                                                    fromToday = true,
                                                                    courseIdToHide = hideId,
                                                                    targetWeek = week,
                                                                )
                                                            },
                                                            pageState = todayPageState,
                                                            jumpNeighbor = todayJumpNeighbor,
                                                            navBarStyle = navBarStyle,
                                                            onScrollYChanged = { _ -> },
                                                            settingsScrollBehavior = todayScrollBehavior,
                                                            onSelectedDayChanged = {
                                                                todaySelectedDayOfWeek = it
                                                            },
                                                            onSelectedDateChanged = {
                                                                todayIsToday = it
                                                            },
                                                            scrollToTodayTrigger = scrollToTodayTrigger,
                                                            jumpToDateTrigger = todayJumpToDateTrigger,
                                                            onJumpToDateProcessed = {
                                                                todayJumpToDateTrigger = 0
                                                            },
                                                            useSharedWallpaper = !isShiftMode && wallpaperBitmap != null,
                                                            softGlassSurface = softGlassSurface,
                                                            tabRedraw = tabGlassRedraw,
                                                            cardSurfaceAlpha = displayAppearance.cardSurfaceAlpha,
                                                            liquidGlassBackdrop = chromeBackdrop,
                                                            showClassroom = displayAppearance.showClassroom,
                                                            showTeacher = displayAppearance.showTeacher,
                                                            onListScrollInProgress = {
                                                                todayListScrollInProgress.value = it
                                                            },
                                                            // 今日助手卡留在 :androidApp（天气/定位/Geocoder），通过槽位注入
                                                            todayAssistantCard = todayAssistantCard,
                                                            // 视图模式与课表页共用同一个开关
                                                            scheduleViewMode = scheduleViewMode,
                                                            // 顶栏与内容共用同一份越界状态（顶栏材质判据需要）
                                                            overScrollState = mainOverScrollState,
                                                            chrome = TodayPageChrome(
                                                                forcedDarkTheme = todayForced,
                                                                wallpaperBackdrop = if (showSharedWallpaperLayer) wallpaperBackdrop else null,
                                                                contentBackdrop = todayContentBackdrop,
                                                                moreMenu = PageMoreMenuState(
                                                                    show = showTodayMorePopup,
                                                                    onDismiss = { showTodayMorePopup = false },
                                                                    onExpand = { showTodayMorePopup = true },
                                                                    fraction = todayMorePopupFraction,
                                                                    enabled = navBarStyle != "rail" && hostNavigation == null,
                                                                    native = LocalNativeTopBarHost.current?.ownsTopRight == true,
                                                                ),
                                                                onJumpToDate = { todayJumpToDateTrigger++ },
                                                                onEnterCustomize = enterCustomizePage,
                                                                onCourseManage = {
                                                                    todayPageRouter.navigate(AppRoute.CourseManage)
                                                                },
                                                                titleEndPadding = titleEndPadding,
                                                                blurResampleKey = railBlurResampleEpoch.intValue,
                                                                blurSampleTrack = tabletNavExpandSampleTrack,
                                                                contentRecordKey = liquidGlassRecordKey,
                                                                contentMustRecord = liquidGlassMustRecord,
                                                            ),
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        1 -> {
                                            // 内容避让右侧栏；壁纸/页面背景仍然铺到屏幕边缘
                                            Box(Modifier.fillMaxSize().then(pageInsetModifier)) {
                                                val scheduleForced =
                                                    if (captureThemeActive) captureThemeIsDark else wallpaperForcedDark
                                                val scheduleDark = scheduleForced ?: appSettingDark
                                                val scheduleThemeController =
                                                    rememberAppThemeController(isDark = scheduleDark)
                                                MiuixTheme(controller = scheduleThemeController) {
                                                    CompositionLocalProvider(LocalForcedDarkTheme provides scheduleForced) {
                                                        CompositionLocalProvider(
                                                            LocalLandRipple provides LandRippleSpec(
                                                                center = landRippleCenter,
                                                                token = landRippleToken
                                                            )
                                                        ) {
                                                            val schedulePageRouter = LocalAppRouter.current
                                                            MainScheduleScreen(
                                                                viewModel = viewModel,
                                                                settingsViewModel = settingsViewModel,
                                                                pageState = weekPageState,
                                                                scheduleViewMode = scheduleViewMode,
                                                                // 顶栏与内容共用同一份越界状态（顶栏材质判据需要）
                                                                overScrollState = mainOverScrollState,
                                                                jumpNeighbor = weekJumpNeighbor,
                                                                hiddenCourseIds = hiddenCourseIds,
                                                                draggingCourseSlots = draggingCourseSlots,
                                                                onCourseClick = { c, l, t, w, h, layer, hideId, week ->
                                                                    openCourseDetail(
                                                                        courses = c,
                                                                        cardLeft = l,
                                                                        cardTop = t,
                                                                        cardWidth = w,
                                                                        cardHeight = h,
                                                                        cardLayer = layer,
                                                                        fromToday = false,
                                                                        courseIdToHide = hideId,
                                                                        targetWeek = week,
                                                                    )
                                                                },
                                                                onEmptyLongPress = { day, section, centerX, cellTopY, width, _, addDay, addWeek ->
                                                                    shortcutMenuCourse = null
                                                                    emptyCellMenuTarget =
                                                                        EmptyCellMenuTarget(
                                                                            columnDay = day,
                                                                            section = section,
                                                                            addDay = addDay,
                                                                            addWeek = addWeek,
                                                                        )
                                                                    shortcutMenuVisible = true
                                                                    shortcutMenuPosition = Offset(
                                                                        centerX - width / 2f,
                                                                        cellTopY
                                                                    )
                                                                    shortcutMenuAnchorWidth = width
                                                                },
                                                                onCourseLongPress = longPress@{ course, left, top, width, height, currentWeek ->
                                                                    // 非本周的课（灰卡）两种视图都不给长按菜单：它只是「这格以后会上什么」的占位
                                                                    if (!course.isActiveInWeek(currentWeek)) return@longPress
                                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    // 时间轴卡片按真实时间定位，节次几何对它没有意义：锚点直接用卡片实测值。
                                                                    // 自定义时间课同理。时间轴卡片 dragEnabled=false，浮层起来也拖不动。
                                                                    val measuredAnchor = course.hasValidCustomTime() ||
                                                                        scheduleViewMode == ScheduleViewMode.TIMELINE
                                                                    // 浮层照常起（原卡隐藏、浮层顶在原地）—— 自定义时间课也一样
                                                                    isDraggingCard = true
                                                                    floatingCardVisible = true
                                                                    // 新一轮长按：还没开始拖，高光先关
                                                                    isFloatingCardDragging = false
                                                                    draggingCourseSlots =
                                                                        setOf(course.id to currentWeek)
                                                                    draggedCardCourse = course
                                                                    draggedWeek = currentWeek
                                                                    // 跨午休/晚修课以整课视觉几何为锚点，避免长按在分段卡片上时中心偏移
                                                                    val geom = gridGeometry
                                                                    val anchorCenter: Offset
                                                                    val anchorSize: Offset
                                                                    if (geom != null) {
                                                                        val div = dividerPxFor(geom)
                                                                        val fullH =
                                                                            courseVisualHeightPx(
                                                                                geom,
                                                                                course.startSection,
                                                                                course.endSection,
                                                                                div
                                                                            )
                                                                        val bounds =
                                                                            geom.dayBounds[course.dayOfWeek]
                                                                                ?: geom.dayBounds.values.firstOrNull()
                                                                        // 宽度沿用卡片回调（含 Day 列内 2dp padding 后的实际宽），不要用整列 dayBounds
                                                                        val topRel = sectionTopPx(
                                                                            geom,
                                                                            course.startSection,
                                                                            div
                                                                        )
                                                                        // X 必须用卡片实测中心 left：调课日列数据星期 ≠ 列星期，
                                                                        // dayBounds[course.dayOfWeek] 会取到隔壁列；自定义时间课按时间轴定位。
                                                                        anchorCenter = when {
                                                                            measuredAnchor ->
                                                                            Offset(left, top)

                                                                            bounds != null && bounds.size >= 3 ->
                                                                                Offset(
                                                                                    left,
                                                                                    bounds[2] + topRel + fullH / 2f
                                                                                )

                                                                            else ->
                                                                                Offset(left, top)
                                                                        }
                                                                        anchorSize =
                                                                            if (measuredAnchor) {
                                                                            Offset(width, height)
                                                                            } else {
                                                                                Offset(width, fullH)
                                                                            }
                                                                    } else {
                                                                        anchorCenter = Offset(left, top)
                                                                        anchorSize =
                                                                            Offset(width, height)
                                                                    }
                                                                    draggedCardPosition = anchorCenter
                                                                    // 由卡片实测 X 反推所在列：数据星期在调课日列对不上列
                                                                    draggedColumnDay =
                                                                        geom?.dayBounds?.entries
                                                                            ?.firstOrNull { (_, b) ->
                                                                                b.size >= 2 && left >= b[0] && left <= b[1]
                                                                            }?.key ?: course.dayOfWeek
                                                                    dragMotion.reset()
                                                                    floatingOffsetX.floatValue = 0f
                                                                    floatingOffsetY.floatValue = 0f
                                                                    draggedCardSize = anchorSize
                                                                    shortcutMenuCourse = course
                                                                    emptyCellMenuTarget = null
                                                                    shortcutMenuVisible = true
                                                                    shortcutMenuPosition =
                                                                        Offset(
                                                                            anchorCenter.x - anchorSize.x / 2f,
                                                                            anchorCenter.y - anchorSize.y / 2f
                                                                        )
                                                                    shortcutMenuAnchorWidth =
                                                                        anchorSize.x
                                                                    shortcutMenuAllowCopy = scheduleViewMode != ScheduleViewMode.TIMELINE
                                                                },
                                                                onCourseDragStart = { _ ->
                                                                    // tracker 判定位移超 touchSlop 才回调：亮高光、收菜单、算落点
                                                                    isFloatingCardDragging = true
                                                                    shortcutMenuVisible = false
                                                                    coroutineScope.launch {
                                                                        delay(220.milliseconds)
                                                                        shortcutMenuCourse = null
                                                                    }
                                                                    refreshPendingDropTarget()
                                                                },
                                                                onCourseDrag = { _, offsetX, offsetY ->
                                                                    // 跨周调课：卡片停在屏幕左右边缘热区 → 自动翻周
                                                                    updateEdgeAutoPage(
                                                                        fingerX = draggedCardPosition.x + offsetX,
                                                                        // 必须真拖起来（isFloatingCardDragging）才判热区：
                                                                        // 刚进拖拽态时卡片还在原位，最右列离屏边不到 40dp
                                                                        enabled = isFloatingCardDragging && !isPasteFlight
                                                                    )
                                                                    // 跟手 1:1；offset 只进 graphicsLayer，速度进 holder
                                                                    val now = monotonicNow()
                                                                    if (dragMotion.lastTimeMs != 0L) {
                                                                        val dt =
                                                                            (now - dragMotion.lastTimeMs).coerceAtLeast(
                                                                                1L
                                                                            )
                                                                        dragMotion.velocityX =
                                                                            (offsetX - dragMotion.lastOffsetX) / dt * 1000f
                                                                        dragMotion.velocityY =
                                                                            (offsetY - dragMotion.lastOffsetY) / dt * 1000f
                                                                    }
                                                                    dragMotion.lastTimeMs = now
                                                                    dragMotion.lastOffsetX = offsetX
                                                                    dragMotion.lastOffsetY = offsetY
                                                                    floatingOffsetX.floatValue = offsetX
                                                                    floatingOffsetY.floatValue = offsetY
                                                                    // 落点按浮层中心对网格算（仅跨格时写 state，避免逐帧重组课表）
                                                                    refreshPendingDropTarget()
                                                                },
                                                                onCourseDragEnd = { _ ->
                                                                    // 浮层可能已经被别的路径收掉（返回键 / 点空白 / 切页）：
                                                                    // 这次松手只收尾，不按残留落点写库
                                                                    val dropAborted = !isFloatingCardDragging
                                                                    // 手指已离开：高光立刻开始淡出，不等下面的退场动画
                                                                    isFloatingCardDragging = false
                                                                    stopEdgeAutoPage()
                                                                    // 翻周/滚动后几何可能已换页：落点按最新几何重算一次，
                                                                    // 否则松手会落到上一页的格子（跨周写错周）
                                                                    refreshPendingDropTarget()
                                                                    // 只结束拖拽浮层；菜单已在起拖时收掉
                                                                    val source = if (dropAborted) null else draggedCardCourse
                                                                    val target = pendingDropTarget
                                                                    val week = draggedWeek
                                                                    setPendingDropTarget(null)
                                                                    // 翻周动画还在跑时落点仍属于上一页：此时写库会写进错的周，
                                                                    // 宁可吸回原位让用户重拖
                                                                    if (source != null && target != null &&
                                                                        !weekPageState.isAnimating
                                                                    ) {
                                                                        // 列 → 数据坐标：调课日列显示的是 followWeekday/followWeek 的课，
                                                                        // 冲突检测与写库都必须落在数据坐标上，几何/动画仍用列
                                                                        val (targetDay, targetWeek) =
                                                                            dataPositionForColumn(target.first)
                                                                        val sectionSpan =
                                                                            source.endSection - source.startSection
                                                                        val targetStart = target.second
                                                                        val targetEnd =
                                                                            targetStart + sectionSpan
                                                                        // 同格按数据坐标判：调课日列里原地拖动不再被误判成跨天
                                                                        val sameSlot =
                                                                            source.dayOfWeek == targetDay &&
                                                                                    week == targetWeek &&
                                                                                    source.startSection == targetStart &&
                                                                                    source.endSection == targetEnd
                                                                        if (!sameSlot) {
                                                                            val conflicts =
                                                                                viewModel.getCoursesAtSlot(
                                                                                    targetWeek,
                                                                                    targetDay,
                                                                                    targetStart,
                                                                                    targetEnd
                                                                                ).filter {
                                                                                    it.id != source.id && it.isActiveInWeek(
                                                                                        targetWeek
                                                                                    )
                                                                                }
                                                                            if (conflicts.isEmpty()) {
                                                                                viewModel.moveCourseForWeek(
                                                                                    source.id,
                                                                                    week,
                                                                                    targetDay,
                                                                                    targetStart,
                                                                                    targetEnd,
                                                                                    targetWeek
                                                                                )
                                                                                // 单周调课可能拆分/合并出新 id，按目标位重收并隐藏防叠影
                                                                                draggingCourseSlots =
                                                                                    viewModel.getCoursesAtSlot(
                                                                                        targetWeek,
                                                                                        targetDay,
                                                                                        targetStart,
                                                                                        targetEnd
                                                                                    )
                                                                                        .filter {
                                                                                            it.isActiveInWeek(
                                                                                                targetWeek
                                                                                            )
                                                                                        }
                                                                                        .map { it.id to targetWeek }
                                                                                        .toSet()
                                                                                snapFloatingCardToTarget(
                                                                                    target.first,
                                                                                    targetStart,
                                                                                    sectionSpan
                                                                                )
                                                                                // 原位置露出的非本周课程淡入放大
                                                                                val sourceCourses =
                                                                                    viewModel.getCoursesAtSlot(
                                                                                        week,
                                                                                        source.dayOfWeek,
                                                                                        source.startSection,
                                                                                        source.endSection
                                                                                    ).filter {
                                                                                        it.id != source.id && !it.isActiveInWeek(
                                                                                            week
                                                                                        )
                                                                                    }
                                                                                if (sourceCourses.isNotEmpty()) {
                                                                                    flashAnimateIn(
                                                                                        sourceCourses.map { it.id }.toSet()
                                                                                    )
                                                                                }
                                                                            } else {
                                                                                // 浮层连贯移到目标卡上方悬停等用户选择
                                                                                pendingConflictCourse =
                                                                                    conflicts.first()
                                                                                setPendingDropTarget(
                                                                                    target.first to targetStart
                                                                                )
                                                                                // 新冲突从第一步开始，清掉上一轮可能残留的两步状态
                                                                                pendingConflictResolution = null
                                                                                conflictConfirmResolution = null
                                                                                showRescheduleConflictDialog =
                                                                                    true
                                                                                val hoverTargetCenter =
                                                                                    computeTargetCenter(
                                                                                        target.first,
                                                                                        targetStart,
                                                                                        sectionSpan
                                                                                    )
                                                                                if (hoverTargetCenter != null) {
                                                                                    coroutineScope.launch {
                                                                                        stopConflictHover()
                                                                                        isConflictHover =
                                                                                            true
                                                                                        isSnapping =
                                                                                            true
                                                                                        // 拖拽位移已在 floatingOffset*
                                                                                        val sectionHPx =
                                                                                            gridGeometry?.sectionHeightPx
                                                                                                ?: 0f
                                                                                        val floatH =
                                                                                            (sectionSpan + 1) * sectionHPx
                                                                                        val gap =
                                                                                            with(density) {
                                                                                                10.dp.toPx()
                                                                                            }
                                                                                        val hoverOffsetX =
                                                                                            hoverTargetCenter.x -
                                                                                                    draggedCardPosition.x
                                                                                        val hoverOffsetY =
                                                                                            hoverTargetCenter.y -
                                                                                                    draggedCardPosition.y -
                                                                                                    floatH - gap
                                                                                        val settleEase =
                                                                                            CubicBezierEasing(
                                                                                                0.32f,
                                                                                                0.72f,
                                                                                                0.28f,
                                                                                                1f
                                                                                            )
                                                                                        val jobX =
                                                                                            launch {
                                                                                                animateFloatState(
                                                                                                    floatingOffsetX,
                                                                                                    hoverOffsetX,
                                                                                                    tween(
                                                                                                        durationMillis = 420,
                                                                                                        easing = settleEase
                                                                                                    )
                                                                                                )
                                                                                            }
                                                                                        val jobY =
                                                                                            launch {
                                                                                                animateFloatState(
                                                                                                    floatingOffsetY,
                                                                                                    hoverOffsetY,
                                                                                                    tween(
                                                                                                        durationMillis = 420,
                                                                                                        easing = settleEase
                                                                                                    )
                                                                                                )
                                                                                            }
                                                                                        val jobScale =
                                                                                            launch {
                                                                                                floatingScale.animateTo(
                                                                                                    1.08f,
                                                                                                    tween(
                                                                                                        durationMillis = 420
                                                                                                    )
                                                                                                )
                                                                                            }
                                                                                        jobX.join(); jobY.join(); jobScale.join()
                                                                                        val bobAmp =
                                                                                            with(density) {
                                                                                                4.dp.toPx()
                                                                                            }
                                                                                        conflictHoverBobJob =
                                                                                            launch {
                                                                                                while (true) {
                                                                                                    animateFloatState(
                                                                                                        floatingOffsetY,
                                                                                                        hoverOffsetY + bobAmp,
                                                                                                        tween(
                                                                                                            durationMillis = 1400,
                                                                                                            easing = FastOutSlowInEasing
                                                                                                        )
                                                                                                    )
                                                                                                    animateFloatState(
                                                                                                        floatingOffsetY,
                                                                                                        hoverOffsetY - bobAmp,
                                                                                                        tween(
                                                                                                            durationMillis = 1400,
                                                                                                            easing = FastOutSlowInEasing
                                                                                                        )
                                                                                                    )
                                                                                                }
                                                                                            }
                                                                                    }
                                                                                } else {
                                                                                    coroutineScope.launch {
                                                                                        floatingScale.animateTo(
                                                                                            1f,
                                                                                            tween(
                                                                                                durationMillis = 180
                                                                                            )
                                                                                        )
                                                                                        delay(180.milliseconds)
                                                                                        isDraggingCard =
                                                                                            false
                                                                                        floatingCardVisible =
                                                                                            false
                                                                                        draggingCourseSlots =
                                                                                            emptySet()
                                                                                        floatingOffsetX.floatValue =
                                                                                            0f
                                                                                        floatingOffsetY.floatValue =
                                                                                            0f
                                                                                    }
                                                                                }
                                                                            }
                                                                        } else {
                                                                            snapFloatingCardToOrigin()
                                                                        }
                                                                    } else if (source != null) {
                                                                        // 无落点：松手在网格外 / 超出总节次 / 落到重组休课日 /
                                                                        // 翻周还在滑（落点属于上一页）
                                                                        // → 与「同格」一样吸回原位，不凭空消失
                                                                        stopConflictHover()
                                                                        snapFloatingCardToOrigin()
                                                                    } else {
                                                                        dismissFloatingCard()
                                                                    }
                                                                },
                                                                onCourseDragCancel = { _ ->
                                                                    // 课卡所在周页被 pager 回收 / 手势被系统抢走：
                                                                    // 手指已经跟丢了，绝不能按最后的落点写库。
                                                                    isFloatingCardDragging = false
                                                                    stopEdgeAutoPage()
                                                                    setPendingDropTarget(null)
                                                                    stopConflictHover()
                                                                    dismissFloatingCard()
                                                                },
                                                                wallpaperBitmap = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperImage else wallpaperImage,
                                                                wallpaperOffset = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset else wallpaperOffset,
                                                                wallpaperScale = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale else wallpaperScale,
                                                                useSharedWallpaper = !isShiftMode && wallpaperBitmap != null,
                                                                isWallpaperEditing = isWindowCutoutActive,
                                                                onWallpaperOffsetChange = {
                                                                    wallpaperOffset = it
                                                                },
                                                                onWallpaperScaleChange = {
                                                                    wallpaperScale = it
                                                                },
                                                                appearance = displayAppearance,
                                                                liquidGlassBackdrop = chromeBackdrop,
                                                                softGlassSurface = softGlassSurface,
                                                                tabRedraw = tabGlassRedraw,
                                                                onGridGeometryChange = { geom ->
                                                                    gridGeometry = geom
                                                                    // 翻周停下 / 页内滚停后几何换页
                                                                    if (isFloatingCardDragging) {
                                                                        refreshPendingDropTarget()
                                                                    }
                                                                },
                                                                // 落点高亮按 **lambda** 传：组合期不在这里求值
                                                                dropHighlight = {
                                                                    // 订阅落点变更信号：pendingDropTarget 是普通字段
                                                                    dropTargetRevision
                                                                    val emptyTarget =
                                                                        emptyCellMenuTarget
                                                                    if (emptyTarget != null && shortcutMenuVisible) {
                                                                        emptyTarget.columnDay to (emptyTarget.section..emptyTarget.section)
                                                                    } else {
                                                                        val target = pendingDropTarget
                                                                        val source = draggedCardCourse
                                                                        // 与跟手高光同源：真拖起来才出遮罩，长按起浮层但还没拖时不示意；
                                                                        // 松手后的冲突悬停/二次确认也要保留落点（isFloatingCardDragging 此时已关）
                                                                        if (floatingCardVisible &&
                                                                            (isFloatingCardDragging || pendingConflictCourse != null) &&
                                                                            target != null && source != null
                                                                        ) {
                                                                            val sectionSpan =
                                                                                source.endSection - source.startSection
                                                                            target.first to (target.second..(target.second + sectionSpan))
                                                                        } else null
                                                                    }
                                                                },
                                                                dropHighlightOrigin = {
                                                                    dropTargetRevision
                                                                    val source = draggedCardCourse
                                                                    if (floatingCardVisible &&
                                                                        (isFloatingCardDragging || pendingConflictCourse != null) &&
                                                                        source != null && !isPasteFlight
                                                                    ) {
                                                                        // 高亮画在网格列上：调课日列要用列星期，不能用数据星期
                                                                        draggedColumnDay to (source.startSection..source.endSection)
                                                                    } else {
                                                                        val emptyTarget =
                                                                            emptyCellMenuTarget
                                                                        if (emptyTarget != null && shortcutMenuVisible) {
                                                                            emptyTarget.columnDay to (emptyTarget.section..emptyTarget.section)
                                                                        } else null
                                                                    }
                                                                },
                                                                // 边缘驻留（即将自动翻周）：周 pager 据此在空闲驻留期预组合邻页
                                                                edgeDwellHint = { edgeAutoPageDirection != 0 },
                                                                scheduleScrollBehavior = scheduleScrollBehavior,
                                                                externalScrollState = scheduleScrollState,
                                                                externalShowCourseDetail = scheduleShowCourseDetail,
                                                                externalSelectedCourse = scheduleSelectedCourse,
                                                                externalSelectedCourses = scheduleSelectedCourses,
                                                                chrome = scheduleChrome(
                                                                    contentBackdrop = scheduleContentBackdrop,
                                                                    forcedDarkTheme = scheduleForced,
                                                                    shiftMode = false,
                                                                    onCourseManage = {
                                                                        schedulePageRouter.navigate(AppRoute.CourseManage)
                                                                    },
                                                                ),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        2 -> {
                                            // 设置页始终跟应用主题，不被壁纸锁深色
                                            val settingsDark = appSettingDark
                                            val settingsThemeController =
                                                rememberAppThemeController(isDark = settingsDark)
                                            // 与今日/课表同一套壳：整页不再自己铺 surface，
                                            // 背景交给壳层（共享壁纸），右侧栏才采得到与页面一致的底色。
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .then(settingsPageClipModifier)
                                                    .then(pageContentLayer(settingsContentBackdrop))
                                            ) {
                                                MiuixTheme(controller = settingsThemeController) {
                                                    // 设置页始终跟应用主题，不受壁纸强制主题影响
                                                    CompositionLocalProvider(LocalForcedDarkTheme provides null) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .then(pageInsetModifier)
                                                        ) {
                                                            RenderSettingsPane(
                                                                settingsPane,
                                                                SettingsPaneArgs(
                                                                    viewModel = viewModel,
                                                                    scheduleViewModel = scheduleViewModel,
                                                                    settingsViewModel = settingsViewModel,
                                                                    shiftViewModel = shiftViewModel,
                                                                    isShiftMode = false,
                                                                    onExitShiftMode = {},
                                                                    onEnterShiftMode = {
                                                                        FeatureLog.shift("enter_loading")
                                                                        showShiftLoading = true
                                                                        isExitingShift = false
                                                                    },
                                                                    navBarStyle = navBarStyle,
                                                                    settingsScrollBehavior = settingsScrollBehavior,
                                                                    liquidGlassBackdrop = chromeBackdrop,
                                                                    showUpdateSettings = showUpdateSettings,
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        3 -> {
                                            // 组合与否由平板页面宿主决定（按需组合 + 转场时旧页留着交叉），
                                            // 这里不能再按 currentPage 自裁，否则退场中的旧页会变空白。
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .then(pageContentLayer(courseManageContentBackdrop))
                                            ) {
                                                TabletCourseManagePane(
                                                    viewModel = viewModel,
                                                    settingsViewModel = settingsViewModel,
                                                    liquidGlassBackdrop = chromeBackdrop,
                                                )
                                            }
                                        }

                                        4 -> {
                                            val shareRepository =
                                                remember { CourseRepository() }
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .then(pageContentLayer(switchScheduleContentBackdrop))
                                            ) {
                                                TabletSwitchSchedulePane(
                                                    viewModel = viewModel,
                                                    scheduleViewModel = scheduleViewModel,
                                                    settingsViewModel = settingsViewModel,
                                                    onShareSchedule = { name, changed ->
                                                        onShareSchedule?.invoke(name, changed)
                                                    },
                                                    isScheduleShareable = { name ->
                                                        buildShareScheduleMap(
                                                            shareRepository,
                                                            name
                                                        ) != null
                                                    },
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    when (page) {
                                        0 -> Box(Modifier.fillMaxSize().then(pageInsetModifier)) {
                                                // 内容避让右侧栏；壁纸/页面背景仍然铺到屏幕边缘
                                                val shiftPageRouter = LocalAppRouter.current
                                                ShiftScheduleScreen(
                                                    shiftViewModel = shiftViewModel,
                                                    settingsViewModel = settingsViewModel,
                                                    pageState = weekPageState,
                                                    pageCount = totalWeeks,
                                                    scheduleDataVersion = dataVersion,
                                                    cardHeightPerSection = currentAppearance().cardHeight,
                                                    scheduleScrollBehavior = scheduleScrollBehavior,
                                                    isInFreeformWindow = isInFreeformWindow,
                                                    titleBarHeight = 56.dp,
                                                    liquidGlassBackdrop = chromeBackdrop,
                                                    chrome = scheduleChrome(
                                                        contentBackdrop = shiftContentBackdrop,
                                                        forcedDarkTheme = wallpaperForcedDark,
                                                        shiftMode = true,
                                                        onCourseManage = {
                                                            shiftPageRouter.navigate(AppRoute.CourseManage)
                                                        },
                                                    ),
                                                )
                                        }

                                        1 -> Box(modifier = settingsPageClipModifier) {
                                            // 卡片面铺满整屏，列表内容再避让右侧栏（口径同手机设置页）
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    // ⚠ background 在 padding 之前：卡片面铺到屏幕边缘
                                                    .background(MiuixTheme.colorScheme.surface)
                                                    .then(pageInsetModifier)
                                            ) {
                                                RenderSettingsPane(
                                                    settingsPane,
                                                    SettingsPaneArgs(
                                                        viewModel = viewModel,
                                                        scheduleViewModel = scheduleViewModel,
                                                        settingsViewModel = settingsViewModel,
                                                        shiftViewModel = shiftViewModel,
                                                        isShiftMode = true,
                                                        onExitShiftMode = {
                                                            FeatureLog.shift("exit_loading")
                                                            showShiftLoading = true
                                                            isExitingShift = true
                                                        },
                                                        onEnterShiftMode = {
                                                            showShiftLoading = true
                                                            isExitingShift = false
                                                        },
                                                        navBarStyle = navBarStyle,
                                                        settingsScrollBehavior = settingsScrollBehavior,
                                                        liquidGlassBackdrop = chromeBackdrop,
                                                        showUpdateSettings = showUpdateSettings,
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // 今日页与课表时间轴共用同一个分钟时钟，全 App 只起一个 tick
                            val appMinuteClock = remember { AppMinuteClock() }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    appMinuteClock.minute.intValue = nowMinuteOfDay()
                                    delay(millisUntilNextMinute().milliseconds)
                                }
                            }
                            val mainPagerPageContent: @Composable (Int) -> Unit = { page ->
                                CompositionLocalProvider(
                                    LocalPageStartPadding provides pageStartModifier,
                                    LocalAppMinuteClock provides appMinuteClock,
                                ) {
                                    // 页面容器一律铺满，避让只发生在内容层
                                    Box(Modifier.fillMaxSize()) {
                                        mainPagerPageBody(page)
                                    }
                                }
                            }
                            // 竖排分支不预组合相邻页：Compose 从「当前页 + 1」开始数
                            // beyondViewportPageCount 个页去测量，请求的页号不会夹回 pageCount
                            // （折叠屏 iPhone 实测 IndexOutOfBoundsException: Index 3, size 3）。
                            // 平板的页面组合全部交给下面的宿主，pager 只当位置源、每页留透明占位：
                            // 宿主常驻今日 / 课表，其余页按需组合，转场时额外留住旧页做双页交叉。
                            val verticalPager = verticalMainPagerState
                            // 只在竖排 pager 分支启用：万一退回自绘平移，页面本来就在窗口里，
                            // 再挂宿主会把内容组合两遍。
                            val tabletHosted = isTablet && !isShiftMode &&
                                    useVerticalMainPager && verticalPager != null
                            if (useVerticalMainPager && verticalPager != null) {
                                VerticalPager(
                                    state = verticalPager,
                                    modifier = mainPagerModifier,
                                    key = mainPagerPageKey,
                                    userScrollEnabled = false,
                                    beyondViewportPageCount = 0,
                                ) { page ->
                                    if (tabletHosted) {
                                        // 内容在宿主层；这里只让 pager 保留这一页的位置
                                        Box(Modifier.fillMaxSize())
                                    } else {
                                        mainPagerPageContent(page)
                                    }
                                }
                            } else {
                                // 手机切 tab：自绘平移，swipeEnabled = false。
                                HorizontalPageSwitch(
                                    state = mainTabSwitchState,
                                    pageCount = mainPagerPageCount,
                                    modifier = mainPagerModifier,
                                    swipeEnabled = false,
                                ) { page -> mainPagerPageContent(page) }
                            }
                            if (tabletHosted) {
                                val transitionProgress = remember {
                                    { tabletTabTransition.floatValue }
                                }
                                TabletMainTabPages(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(mainContentNestedScroll),
                                    currentPage = mainTabPosition.currentPage,
                                    fromPage = tabletTransitionFrom,
                                    active = tabletTabTransitionActive,
                                    progress = transitionProgress,
                                    pageCount = mainPagerPageCount,
                                    content = mainPagerPageContent,
                                )
                            }
                        }
                    }

                    // 手机端：只要不在当前周/天就常驻显示
                    // Pad（rail）顶栏仍保留原返回按钮，此处不叠加
                    val isPhoneChrome = navBarStyle != "rail"
                    val shouldShowBackToNowFab = isPhoneChrome &&
                            !isShiftMode &&
                            !showDetail &&
                            !showCustomizePage &&
                            !showSwitchSchedule &&
                            !isWindowCutoutActive &&
                            !shortcutMenuVisible &&
                            !floatingCardVisible &&
                            when (selectedTab) {
                                0 -> !todayIsToday
                                1 -> !isViewingCurrentWeek
                                else -> false
                            }
                    val backToNowLabel = ChromeStr.todayBadge()
                    // ⚠ 必须与底栏同一套间距算法（bottomFloatingPadding）：按钮悬在底栏胶囊
                    // 上方 64dp，底栏间距变了这里必须跟着变，否则按钮会陷进底栏里
                    val backToNowBottomPadding = if (nativeBottomBar) {
                        hostNavigation!!.bottomBarInset.toFloat().dp + 8.dp
                    } else bottomFloatingPadding() + 56.dp
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 对齐切换课表底栏：None 转场 + 手动 appear + graphicsLayer clip=false，
                        // 避免 AnimatedVisibility 收拢尺寸时把阴影裁掉
                        AnimatedVisibility(
                            visible = shouldShowBackToNowFab,
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                // Native chrome reports its full occupied region, including safe area.
                                .padding(bottom = backToNowBottomPadding)
                                .zIndex(20f)
                        ) {
                            val appear by transition.animateFloat(
                                transitionSpec = {
                                    if (targetState == EnterExitState.Visible) {
                                        tween(durationMillis = 320, easing = FastOutSlowInEasing)
                                    } else {
                                        tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                    }
                                },
                                label = "BackToNowAppear"
                            ) { if (it == EnterExitState.Visible) 1f else 0f }

                            // 它和底栏同属底部 chrome：单录一层进 overlayGlassBackdrop 的组合，
                            // OverlayDialog「采底栏」时才能连带采到这颗按钮。
                            // 按钮自身的采样源是 chromeBackdrop，不含本层 → 不会自采样。
                            Box(
                                modifier = Modifier.then(
                                    if (AppMaterialSettings.isNoneTier()) {
                                        Modifier
                                    } else {
                                        Modifier.layerBackdrop(backToNowSampleBackdrop)
                                    }
                                )
                            ) {
                                BackToNowFloatingButton(
                                    backdrop = chromeBackdrop,
                                    label = backToNowLabel,
                                    modifier = Modifier.graphicsLayer {
                                        transformOrigin = TransformOrigin(0.5f, 1f)
                                        scaleX = 0.6f + 0.4f * appear
                                        scaleY = 0.6f + 0.4f * appear
                                        alpha = appear
                                        clip = false
                                    },
                                    onClick = {
                                        if (selectedTab == 0) {
                                            scrollToTodayTrigger++
                                        } else {
                                            // 标题立刻切到本周，再滑一页过去
                                            coroutineScope.launch { jumpToWeek(currentWeek) }
                                        }
                                    },
                                )
                            }
                        }

                    }

                    // 宿主把分享 Intent 摊平成 SharedFileIntent，并提供读该 uri 全文的回调。
                    ShareImportDialog(
                        shareIntent = sharedFileIntent,
                        onShareIntentConsumed = onShareIntentConsumed,
                        readSharedFileText = { uriString -> onReadSharedFileText?.invoke(uriString) },
                        courseViewModel = viewModel,
                        scheduleViewModel = scheduleViewModel,
                        settingsViewModel = settingsViewModel,
                        liquidGlassBackdrop = overlayGlassBackdrop,
                    )

                    // 更新能力（检查/下载/校验/安装）由宿主注入
                    UpdateDialog(
                        updateController = updateController ?: NoopUpdateController,
                        liquidGlassBackdrop = overlayGlassBackdrop,
                        onShowChanged = { updateDialogShowing = it },
                    )

                    var jumpWeekTemp by remember { mutableIntStateOf(1) }
                    val hapticFeedback = LocalHapticFeedback.current
                    LaunchedEffect(showJumpWeekDialog) {
                        if (showJumpWeekDialog) {
                            jumpWeekTemp = weekPageState.currentPage + 1
                        }
                    }
                    OverlayDialog(
                        title = ChromeStr.jumpToWeek(),
                        show = showJumpWeekDialog,
                        liquidGlassBackdrop = overlayGlassBackdrop,
                        onDismissRequest = { viewModel.hideJumpWeekDialog() }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            NumberPicker(
                                value = jumpWeekTemp,
                                onValueChange = { jumpWeekTemp = it },
                                range = 1..totalWeeks,
                                visibleItemCount = 3,
                                itemHeight = 60.dp,
                                textStyle = MiuixTheme.textStyles.title2,
                                label = { CommonStr.weekN(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextButton(
                                    text = CommonStr.cancel(),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        viewModel.hideJumpWeekDialog()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    text = CommonStr.ok(),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        viewModel.hideJumpWeekDialog()
                                        coroutineScope.launch {
                                            // 跳周同样立刻切标题，不等落页
                                            jumpToWeek(jumpWeekTemp)
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    val editingCourse by viewModel.editingCourse.collectAsState()
                    val selectedStartSection by viewModel.selectedStartSection.collectAsState()
                    val selectedEndSection by viewModel.selectedEndSection.collectAsState()
                    val editingStartSection =
                        editingCourse?.startSection ?: selectedStartSection
                    val editingEndSection = editingCourse?.endSection ?: selectedEndSection
                    val addDialogSectionTimes by settingsViewModel.sectionTimes.collectAsState()

                    // 始终跟随应用主题，不受壁纸强制主题影响
                    val appDialogDark = rememberAppSettingDark()
                    val appDialogController = rememberAppThemeController(isDark = appDialogDark)
                    MiuixTheme(controller = appDialogController) {
                        CompositionLocalProvider(LocalForcedDarkTheme provides null) {
                            val addDialogDefaultWeeks by viewModel.addDialogDefaultWeeks.collectAsState()
                            val addDialogCustomTime by viewModel.addDialogCustomTime.collectAsState()
                            AddCourseDialog(
                                show = showAddDialog,
                                course = editingCourse,
                                selectedDay = viewModel.selectedDay.collectAsState().value,
                                liquidGlassBackdrop = overlayGlassBackdrop,
                                totalWeeks = totalWeeks,
                                totalSections = totalSections,
                                defaultStartSection = editingStartSection,
                                defaultEndSection = editingEndSection,
                                defaultWeeks = addDialogDefaultWeeks,
                                defaultCustomStartTime = addDialogCustomTime?.first,
                                defaultCustomEndTime = addDialogCustomTime?.second,
                                getOccupiedWeeks = { dayOfWeek, startSection, endSection, excludeIds, startTime, endTime ->
                                    viewModel.getOccupiedWeeks(
                                        dayOfWeek = dayOfWeek,
                                        startSection = startSection,
                                        endSection = endSection,
                                        excludeIds = excludeIds.toSet(),
                                        startTime = startTime,
                                        endTime = endTime
                                    )
                                },
                                onDismiss = { viewModel.hideDialog() },
                                onConfirm = { course ->
                                    if (editingCourse != null) {
                                        viewModel.updateCourse(course)
                                    } else {
                                        viewModel.addCourse(course)
                                    }
                                },
                                onDelete = { courseId ->
                                    viewModel.deleteCourse(courseId)
                                },
                                sectionTimes = addDialogSectionTimes
                            )
                        }
                    }
                    DeleteWeekCourseDialog(
                        show = showDeleteConfirmDialog,
                        course = deleteConfirmCourse,
                        week = draggedWeek,
                        viewModel = viewModel,
                        liquidGlassBackdrop = overlayGlassBackdrop,
                        hapticFeedback = hapticFeedback,
                        onDismiss = { showDeleteConfirmDialog = false },
                    )
                    PasteRangeDialog(
                        show = showPasteRangeDialog,
                        courseName = copiedCourseForPaste?.name ?: "",
                        liquidGlassBackdrop = overlayGlassBackdrop,
                        hapticFeedback = hapticFeedback,
                        onPaste = { allWeeks ->
                            val meta = copiedCourseForPaste
                            val target = pasteRangeTarget
                            if (meta != null && target != null) {
                                // 写库用调课映射后的数据坐标；飞行/几何用列星期
                                val day = target.dataDay
                                val section = target.section
                                val span = (meta.endSection - meta.startSection).coerceAtLeast(0)
                                val endSection = section + span
                                // 「当前周」=该格所属教学周；调课日为 followWeek
                                val pasteWeek = target.dataWeek
                                if (endSection > totalSections) {
                                    showToast(ChromeStr.pasteNoSpace())
                                } else {
                                    val conflicts = viewModel.getCoursesAtSlot(
                                        pasteWeek,
                                        day,
                                        section,
                                        endSection
                                    )
                                        .filter { it.isActiveInWeek(pasteWeek) }
                                    if (conflicts.isNotEmpty()) {
                                        showToast(ChromeStr.pasteTargetOccupied())
                                    } else {
                                        val pasted = if (allWeeks) {
                                            meta.copy(
                                                id = randomUuidV4(),
                                                dayOfWeek = day,
                                                startSection = section,
                                                endSection = endSection,
                                                scheduleId = "",
                                                isCustomTime = false,
                                                customStartTime = null,
                                                customEndTime = null,
                                                lastModified = Clock.System.now()
                                                    .toEpochMilliseconds()
                                            )
                                        } else {
                                            meta.copy(
                                                id = randomUuidV4(),
                                                dayOfWeek = day,
                                                startSection = section,
                                                endSection = endSection,
                                                startWeek = pasteWeek,
                                                endWeek = pasteWeek,
                                                weekType = Course.WEEK_TYPE_ALL,
                                                selectedWeeks = emptyList(),
                                                scheduleId = "",
                                                isCustomTime = false,
                                                customStartTime = null,
                                                customEndTime = null,
                                                lastModified = Clock.System.now()
                                                    .toEpochMilliseconds()
                                            )
                                        }
                                        // 落地后再写入课程
                                        playPasteFlightAnimation(
                                            meta,
                                            target.columnDay,
                                            section
                                        ) {
                                            viewModel.addCourse(pasted)
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        }
                                    }
                                }
                            }
                            showPasteRangeDialog = false
                            pasteRangeTarget = null
                        },
                        onDismiss = {
                            showPasteRangeDialog = false
                            pasteRangeTarget = null
                        },
                    )
                    // 第一步：只选处理方式。竖排按钮；覆盖/交换不直接写库，
                    // 等本弹窗退出动画结束（onDismissFinished）再开第二步确认弹窗。
                    RescheduleConflictDialog(
                        show = showRescheduleConflictDialog,
                        liquidGlassBackdrop = overlayGlassBackdrop,
                        hapticFeedback = hapticFeedback,
                        onCancel = {
                            pendingConflictResolution = null
                            conflictConfirmResolution = null
                            showRescheduleConflictDialog = false
                            pendingConflictCourse = null
                            // 取消 = 不落课，浮层按相邻页锚点飞回源周方向
                            flyFloatingCardHome()
                        },
                        onChooseOverwrite = {
                            pendingConflictResolution = ConflictResolution.OVERWRITE
                            showRescheduleConflictDialog = false
                        },
                        onChooseSwap = {
                            pendingConflictResolution = ConflictResolution.SWAP
                            showRescheduleConflictDialog = false
                        },
                        onDismissFinished = {
                            val resolution = pendingConflictResolution
                            if (resolution != null) {
                                pendingConflictResolution = null
                                conflictConfirmResolution = resolution
                            }
                        },
                    )
                    // 第二步：写明覆盖/交换的具体操作，确认后才写库。
                    RescheduleConflictConfirmDialog(
                        show = conflictConfirmResolution != null,
                        resolution = conflictConfirmResolution ?: ConflictResolution.OVERWRITE,
                        source = draggedCardCourse,
                        target = pendingConflictCourse,
                        // 写库走数据坐标；flyFloatingCardToDropTarget 仍读列坐标算几何
                        dropTarget = pendingDropTarget?.let {
                            dataPositionForColumn(it.first).first to it.second
                        },
                        dropTargetWeek = pendingDropTarget?.let {
                            dataPositionForColumn(it.first).second
                        } ?: draggedWeek,
                        draggedWeek = draggedWeek,
                        viewModel = viewModel,
                        liquidGlassBackdrop = overlayGlassBackdrop,
                        hapticFeedback = hapticFeedback,
                        onCancel = {
                            // 只关弹窗；pendingConflictCourse 留到退出动画结束再清，
                            // 否则关闭过程中 summary 会突然变成“确定要执行此操作吗”
                            conflictConfirmResolution = null
                            pendingConflictResolution = null
                            flyFloatingCardHome()
                        },
                        onOverwriteResolved = {
                            // 同上：数据留到 onDismissFinished 再清，保证退出时文案稳定
                            conflictConfirmResolution = null
                            pendingConflictResolution = null
                            val source = draggedCardCourse
                            val target = pendingDropTarget
                            if (source != null && target != null) {
                                val (targetDay, targetWeek) = dataPositionForColumn(target.first)
                                val span = source.endSection - source.startSection
                                val atTarget = viewModel.getCoursesAtSlot(
                                    targetWeek, targetDay, target.second, target.second + span
                                )
                                draggingCourseSlots = atTarget
                                    .filter { it.isActiveInWeek(targetWeek) }
                                    .map { it.id to targetWeek }
                                    .toSet()
                            }
                            flyFloatingCardToDropTarget()
                        },
                        onSwapResolved = {
                            conflictConfirmResolution = null
                            pendingConflictResolution = null
                            val conflict = pendingConflictCourse
                            val source = draggedCardCourse
                            val target = pendingDropTarget
                            val week = draggedWeek
                            if (source != null && target != null) {
                                val (targetDay, targetWeek) = dataPositionForColumn(target.first)
                                val span = source.endSection - source.startSection
                                // 目标位按目标周次收、源位按 draggedWeek 收（调课日两者可能不同）
                                val atTarget = viewModel.getCoursesAtSlot(
                                    targetWeek, targetDay, target.second, target.second + span
                                ).filter { it.isActiveInWeek(targetWeek) }
                                val atSource = viewModel.getCoursesAtSlot(
                                    week, source.dayOfWeek, source.startSection, source.endSection
                                ).filter { it.isActiveInWeek(week) }
                                draggingCourseSlots =
                                    (atTarget.map { it.id to targetWeek } +
                                            atSource.map { it.id to week }).toSet()
                            }
                            if (conflict != null) {
                                flySwapCards(conflict)
                            } else {
                                dismissFloatingCard()
                            }
                        },
                        onOverwrite = { ids -> flashAnimateIn(ids) },
                        onSwap = { ids -> flashAnimateIn(ids) },
                        onDismissFinished = {
                            // 弹窗完全退出后再清冲突数据：退出动画期间文案不变
                            pendingConflictCourse = null
                            pendingConflictResolution = null
                            conflictConfirmResolution = null
                        },
                    )
                    // 节点常驻组合树、仅 show 控显隐，退出动画才能播完
                    // ⚠ notice 的声明在 Scaffold 之外（采样层开关要先读它）
                    LaunchedEffect(privacyConsented) {
                        // 合规：未同意隐私政策前不拉取公告（不触网）
                        if (!privacyConsented) return@LaunchedEffect
                        val n = NoticeFetcher.fetch()
                        if (n != null && NoticeFetcher.shouldShow(n)) {
                            notice = n
                        }
                    }
                    OverlayDialog(
                        title = notice?.title,
                        summary = notice?.content?.ifBlank { null },
                        show = notice != null,
                        liquidGlassBackdrop = overlayGlassBackdrop,
                        onDismissRequest = { notice = null },
                        onDismissFinished = { notice = null }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            notice?.let { n ->
                                TextButton(
                                    text = CommonStr.done(),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        NoticeFetcher.markSeen(n)
                                        notice = null
                                    }
                                )
                            }
                        }
                    }
                    // 跟手高光：半径 min(短边, 250dp) × 0.5，径向遮罩在半径外恒为 0，
                    // 所以节点只需这个正方形（整屏节点要多付两次全屏离屏）。
                    val glowRadiusPx = remember(windowInfo.containerSize, density) {
                        val container = windowInfo.containerSize
                        val shortSide = minOf(container.width, container.height).toFloat()
                        val cap = with(density) { 250.dp.toPx() }
                        (if (shortSide > 0f) minOf(shortSide, cap) else cap) * 0.5f
                    }
                    // 遮罩渐变随节点尺寸固定，逐帧只读不重建（否则每帧都要新建 Brush/颜色表）
                    val glowMaskBrush = remember(glowRadiusPx) {
                        val r = glowRadiusPx.coerceAtLeast(1f)
                        Brush.radialGradient(
                            colors = listOf(Color.White, Color.White, Color.Transparent),
                            center = Offset(r, r),
                            radius = r,
                        )
                    }
                    Box(
                        Modifier
                            // 位移在 layout 阶段读 float state：不进组合，不触发重组
                            .offset {
                                IntOffset(
                                    (draggedCardPosition.x + floatingOffsetX.floatValue - glowRadiusPx)
                                        .roundToInt(),
                                    (draggedCardPosition.y + floatingOffsetY.floatValue - glowRadiusPx)
                                        .roundToInt()
                                )
                            }
                            .size(with(density) { (glowRadiusPx * 2f).coerceAtLeast(1f).toDp() })
                            .drawWithContent {
                                if (glowAlpha > 0f) {
                                    drawContext.canvas.saveLayer(
                                        Rect(0f, 0f, size.width, size.height),
                                        Paint()
                                    )
                                    drawContent()
                                    drawRect(brush = glowMaskBrush, blendMode = BlendMode.DstIn)
                                    drawContext.canvas.restore()
                                }
                            }
                            .drawBackdrop(
                                backdrop = chromeBackdrop,
                                shadow = null,
                                shape = { RectangleShape },
                                effects = { colorControls(saturation = 1.5f) },
                            )
                    )
                }
            }
            // 始终用 MiuixTheme 包裹保持结构恒定；ThemeController 原地切 mode，重建实例会卡一帧
            val effectiveForcedDark = if (captureThemeActive) captureThemeIsDark else forcedDark
            val effectiveDark = effectiveForcedDark ?: appSettingDark
            val pageController = rememberAppThemeController(isDark = effectiveDark)
            MiuixTheme(controller = pageController) {
                CompositionLocalProvider(
                    LocalForcedDarkTheme provides effectiveForcedDark,
                    LocalChromeStartInset provides startInsetDp,
                    // 顶栏槽与内容槽共用一个越界实例：材质判据的「顶部下拉回弹」才成立
                    LocalOverScrollState provides mainOverScrollState,
                ) {
                    // 顶栏已经移进各页面内部，切页转场全部由页面宿主完成；
                    // 这里刻意不包整屏 graphicsLayer：空闲时常驻 layer 会在侧栏展开重排时
                    // 让玻璃采样贴着旧层走，展开动画乱闪。
                    scaffoldContent()
                    // 平板：胶囊/侧栏叠层（不占 Scaffold bottomBar，内容可正常滚动）
                    if (navBarStyle == "rail") {
                        // 与底栏共用同一套切页状态机：平板专属转场、连点打断、位置兜底都在里面
                        val onTabletTabSelected: (Int) -> Unit = onMainTabSelected
                        LiquidNavigationRail(
                            selectedTab = selectedTab,
                            onTabSelected = onTabletTabSelected,
                            backdrop = chromeBackdrop,
                            isShiftMode = isShiftMode,
                            // 侧栏遮罩圆角由宿主注入（同一份 px）
                            screenCornerRadiusPx = screenCornerRadius.maxRadius,
                            onSidebarExpandedChange = {
                                TabletNavSideState.expanded = it
                            },
                            showBackToNow = !isShiftMode &&
                                    !showDetail &&
                                    !showCustomizePage &&
                                    !showSwitchSchedule &&
                                    !isWindowCutoutActive &&
                                    !shortcutMenuVisible &&
                                    !floatingCardVisible &&
                                    when (selectedTab) {
                                        0 -> !todayIsToday
                                        1 -> !isViewingCurrentWeek
                                        else -> false
                                    },
                            onBackToNow = {
                                if (selectedTab == 0) {
                                    scrollToTodayTrigger++
                                } else {
                                    coroutineScope.launch { jumpToWeek(currentWeek) }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = startInsetDp)
                                .zIndex(22f),
                        )
                    }
                }
            }

        }
    }
}

/**
 * 右上角按钮的唯一发布点（鸿蒙 ArkUI 宿主）。
 *
 * 今日页与课程表页各发一组，位移取主 tab 位置源的 currentPage + offsetFraction。
 */
@Composable
private fun PublishNativeTopBarButtons(
    host: NativeTopBarHost,
    position: MainTabPosition,
    vertical: Boolean,
    isTablet: Boolean,
    isShiftMode: Boolean,
    screenWidthDp: Float,
    screenHeightDp: Float,
    titleEndPadding: Dp,
    onSwitchSchedule: () -> Unit,
    onJumpWeek: () -> Unit,
    onJumpToDate: () -> Unit,
    onCourseManage: () -> Unit,
    onEnterCustomize: () -> Unit,
) {
    val buttonSize = 42.dp
    val hostNav = LocalHostNavigation.current
    val statusBarHeight =
        if (hostVerticalBarEdge()) 0.dp
        else WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 折叠顶栏 collapsed 高 52，按钮在其中垂直居中 ⇒ 与 Compose 侧同一中线
    val top = statusBarHeight + (CollapsedHeight - buttonSize) / 2

    val page = position.currentPage
    val fraction = position.offsetFraction
    // 与顶栏同一套 delta：今日页 0、课程表页 1（排班模式只有课程表页）
    val todayDelta = (if (isShiftMode) -1f else 0f) - page - fraction
    val scheduleDelta = (if (isShiftMode) 0f else 1f) - page - fraction

    val todayMenu = remember {
        listOf(
            NativeTopBarMenuItem("jumpToDate", ChromeStr.jumpToDate()),
            NativeTopBarMenuItem("courseManage", ChromeStr.courseManage()),
            NativeTopBarMenuItem("customize", ChromeStr.scheduleAppearance()),
        )
    }
    val scheduleMenu = remember(isShiftMode) {
        buildList {
            add(NativeTopBarMenuItem("jumpWeek", ChromeStr.jumpToWeek()))
            if (!isShiftMode) add(NativeTopBarMenuItem("courseManage", ChromeStr.courseManage()))
            add(NativeTopBarMenuItem("customize", ChromeStr.scheduleAppearance()))
        }
    }
    val state = NativeTopBarState(
        today = NativeTopBarGroup(
            showsSwitch = false,
            showsMore = !isTablet,
            menu = todayMenu,
            offsetXDp = if (vertical) 0f else todayDelta * screenWidthDp,
            offsetYDp = if (vertical) todayDelta * screenHeightDp else 0f,
        ),
        schedule = NativeTopBarGroup(
            showsSwitch = !isShiftMode && !isTablet,
            showsMore = !isTablet,
            menu = scheduleMenu,
            offsetXDp = if (vertical) 0f else scheduleDelta * screenWidthDp,
            offsetYDp = if (vertical) scheduleDelta * screenHeightDp else 0f,
        ),
        onSurfaceArgb = MiuixTheme.colorScheme.onSurface.toArgb().toLong() and 0xFFFFFFFFL,
        topDp = top.value,
        sizeDp = buttonSize.value,
        spacingDp = 8f,
        rightPadDp = (16.dp + titleEndPadding).value,
    )
    SideEffect {
        host.state = state
        host.onAction = { id ->
            when (id) {
                "switchSchedule" -> onSwitchSchedule()
                "jumpWeek" -> onJumpWeek()
                "jumpToDate" -> onJumpToDate()
                "courseManage" -> onCourseManage()
                "customize" -> onEnterCustomize()
            }
        }
    }
    DisposableEffect(host) {
        onDispose {
            host.state = null
            host.onAction = null
        }
    }
}

/**
 * 平板主 tab 页面宿主。
 *
 * - 今日(0) / 课程表(1) 常驻组合；其余页只在成为当前页或转场旧页时组合（不预加载）。
 * - 顶栏已经移进页面内部，所以每个页面槽位自己承担缩放 / 模糊 / alpha：
 *   旧页 `1.0→0.96` + 模糊渐强 + 淡出，新页 `0.96→1.0` + 模糊渐弱 + 淡入，双页同时绘制交叉。
 * - **角色在组合期定死**（隐藏 / 稳态 / 旧页 / 新页），只有参与交叉的两页挂 layer；
 *   空闲时一个全屏 layer 都不留，避免侧栏展开重排时玻璃采样贴旧层走。
 * - 进度是每帧写的 float state，只在 `graphicsLayer` 的绘制期读，动画不触发重组。
 * - 隐藏页跳过绘制并 place 到屏外：组合与测量保留，但不吃点击。
 */
@Composable
private fun TabletMainTabPages(
    modifier: Modifier,
    currentPage: Int,
    fromPage: Int,
    active: Boolean,
    progress: () -> Float,
    pageCount: Int,
    content: @Composable (Int) -> Unit,
) {
    val pages = remember(currentPage, fromPage, active, pageCount) {
        (listOf(0, 1) + listOfNotNull(
            currentPage.takeIf { it in 2 until pageCount },
            fromPage.takeIf { active && it in 2 until pageCount && it != currentPage },
        )).distinct().sorted()
    }
    Box(modifier) {
        for (page in pages) {
            key(page) {
                // 0 隐藏 / 1 稳态当前页 / 2 转场旧页 / 3 转场新页
                val role = when {
                    !active -> if (page == currentPage) 1 else 0
                    page == fromPage -> 2
                    page == currentPage -> 3
                    else -> 0
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 新页压在退场旧页之上，交叉时不被旧页盖住
                        .zIndex(if (page == currentPage) 1f else 0f)
                        .then(
                            if (role >= 2) Modifier.graphicsLayer {
                                val p = progress()
                                val old = role == 2
                                alpha = if (old) 1f - p else p
                                val min = TabletTabTransitionSpec.SCALE_MIN
                                val s = if (old) {
                                    1f + (min - 1f) * p
                                } else {
                                    min + (1f - min) * p
                                }
                                scaleX = s
                                scaleY = s
                                val blurDp = TabletTabTransitionSpec.MAX_BLUR_DP *
                                        if (old) p else (1f - p)
                                renderEffect = if (blurDp > 0.01f && isRenderEffectSupported()) {
                                    val blurPx = blurDp.dp.toPx()
                                    BlurEffect(null, blurPx, blurPx, TileMode.Clamp)
                                } else null
                            } else Modifier
                        )
                        .drawWithContent {
                            if (role != 0) drawContent()
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                if (role != 0) {
                                    placeable.place(0, 0)
                                } else {
                                    // 移出命中区，组合与测量照旧保留
                                    placeable.place(-placeable.width, 0)
                                }
                            }
                        },
                ) { content(page) }
            }
        }
    }
}
