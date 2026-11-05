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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.haooz.chedule.ui.activities.classschedule.LandRippleSpec
import com.haooz.chedule.ui.activities.classschedule.LocalLandRipple
import com.haooz.chedule.ui.activities.classschedule.MainScheduleScreen
import com.haooz.chedule.ui.activities.classschedule.ScheduleTopBar
import com.haooz.chedule.ui.activities.classschedule.ScheduleViewMode
import com.haooz.chedule.ui.activities.today.TodayScreen
import com.haooz.chedule.ui.components.rememberSoftGlassSurface
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.chedule.ui.components.BackToNowFloatingButton
import com.haooz.chedule.ui.components.LiquidNavigationRail
import com.haooz.chedule.ui.components.PublishNativeBottomBar
import com.haooz.chedule.ui.components.ScheduleBottomBar
import com.haooz.chedule.ui.components.scheduleBottomBarHeight
import com.haooz.chedule.ui.components.ShareImportDialog
import com.haooz.chedule.ui.components.TabletNavSideState
import com.haooz.chedule.ui.components.UpdateDialog
import com.haooz.chedule.ui.components.tabletNavExpandSampleTrack
import com.haooz.chedule.ui.components.tabletNavRailStartPadding
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.screens.AddCourseDialog
import com.haooz.chedule.ui.screens.ShiftScheduleScreen
import com.haooz.chedule.ui.screens.TabletCourseManagePane
import com.haooz.chedule.ui.screens.TabletSettingsChromeOverlay
import com.haooz.chedule.ui.screens.TabletSwitchSchedulePane
import com.haooz.chedule.ui.theme.rememberAppThemeController
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
import com.haooz.chedule.ui.utils.rememberPagerTakeoverGestureState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.isRenderEffectSupported
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults.CollapsedHeight
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

/**
 * 「更多」菜单收起动画播到这个 fraction 就摘掉「比底栏更高一层」的采样层。
 * 取 0.5：面板还在缩的时候换层看不出来；等播完再换，按钮已静止在右上角，玻璃少一层会当场跳一下。
 */
private const val MENU_GLASS_RELEASE_FRACTION = 0.5f

/** 其余弹窗：退场动画（最长约 320ms）播完再摘采样层，那时屏幕上已经没有它的玻璃了 */
private const val OVERLAY_GLASS_RELEASE_DELAY_MS = 420L

/**
 * 课表主界面「主内容层」：整屏 Box + graphicsLayer 模糊 / 裁切 + Scaffold 内容。
 *
 * 参数只放外壳算好的局部值，其余状态一律走 [CourseScheduleAppState]（函数体内 `with(state)`）。
 */
@Composable
internal fun CourseScheduleAppMainContent(
    state: CourseScheduleAppState,
    displayAppearance: AppearanceConfig,
    chromeBackdrop: Backdrop,
    liquidGlassRecordKey: List<Any?>,
    liquidGlassMustRecord: () -> Boolean,
    mainContentAlpha: Float,
    wallpaperImage: ImageBitmap?,
    originalWallpaperImage: ImageBitmap?,
) {
    with(state) {
        val density = densityProvider
        // 两页各自一份柔光样式（edgeWidth/折射/模糊互不影响）；壁纸采样源相同但分别烘焙
        val glassWallpaper =
            if (showCustomizePage && !isWindowCutoutActive) originalWallpaperImage else wallpaperImage
        val glassScale =
            if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale else wallpaperScale
        val glassOffset =
            if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset else wallpaperOffset
        // 外观里的「卡片折射 / 卡片模糊 / 边缘厚度」两页共用同一套值
        val glassRefractionScale = displayAppearance.cardRefraction.scale
        // 柔光样式统一吃 px：外观里的模糊本身就是 px，直接透传
        val glassBlurPx = displayAppearance.cardBlurRadius
        // 边缘厚度档位只是倍率，乘在文件默认的 edgeWidth 上
        val glassEdgePx = ScheduleSoftGlassStyle.edgeWidth *
            displayAppearance.cardEdgeThickness.scale
        // 高光描边：倒角上的方向光，两页共用同一个开关
        val glassEdgeHighlight = displayAppearance.cardRim
        val scheduleGlassStyle = remember(glassRefractionScale, glassBlurPx, glassEdgePx, glassEdgeHighlight) {
            ScheduleSoftGlassStyle.copy(
                refractionScale = glassRefractionScale,
                smallBlurRadius = glassBlurPx,
                edgeWidth = glassEdgePx,
                edgeHighlight = glassEdgeHighlight,
            )
        }
        // 两页共用一套玻璃参数（折射 / 模糊 / 厚度 / 描边都来自 displayAppearance），
        // 所以**只烘焙一次、共用一个 surface**：
        //  · 少一次整屏烘焙（对齐 + 模糊 + 调色），拖「卡片模糊」时不再 ×2；
        //  · 翻页时两页不会拿着两份 backdrop 去抢同一个 shader 程序的 uniform 配置
        //   （程序按尺寸共享，配置不同会每帧来回重灌、还每次新建 BitmapShader）。
        // 以后真要给两页不同参数，得让 surface 带上各自的 style 再分开烘焙。
        val softGlassSurface = rememberSoftGlassSurface(
            wallpaperBitmap = glassWallpaper,
            wallpaperScale = glassScale,
            wallpaperOffset = glassOffset,
            screenWidthPx = screenWPx,
            screenHeightPx = screenHPx,
            style = scheduleGlassStyle,
        )
        // 主 tab 切换时 pager 整体平移，两页的玻璃采样都要按帧刷新
        val tabGlassRedraw: () -> Unit = remember(mainPagerState) {
            { mainPagerState.currentPageOffsetFraction }
        }

        // 连贯动画期间下层的实时模糊半径（dp）：长按菜单优先，其次详情 morph；与宿主侧口径一致
        fun currentDetailBlurDp(): Float =
            if (shortcutMenuBlurRadius.value > 0.01f) shortcutMenuBlurRadius.value
            // 宿主已自己压一层系统 material（iOS）：主内容不再叠第二层。
            // 长按菜单不走宿主，仍在上面那支里自糊。
            else if (hostNavigation?.ownsDetailBackdropBlur == true) 0f
            else managePageBlurRadius.value

        // 顶栏/底栏那两层采样层的重录谓词。⚠️ 不能复用 liquidGlassMustRecord：那份闭包里
        // 带着 rail 强制续录的帧计数与「侧栏进度是否还在动」的比较状态，两个节点各调一次
        // 会把计数双倍扣掉、把 expandMoving 判成 false。这两层只跟滚动，自己另起一份。
        val chromeBarMustRecord = remember(
            scheduleScrollState,
            todayListScrollInProgress,
            pagerState,
            todayPagerState,
            mainPagerState,
        ) {
            {
                scheduleScrollState.isScrollInProgress ||
                        todayListScrollInProgress.value ||
                        pagerState.isScrollInProgress ||
                        todayPagerState.isScrollInProgress ||
                        mainPagerState.isScrollInProgress
            }
        }

        // 主内容（带模糊和裁切）
        // 空闲态不挂 graphicsLayer：全屏离屏合成是周滑 P50 主头之一。
        // 仅模糊/透明度/位移/裁切真正非单位时才开层；derivedStateOf 避免 Animatable 每帧重组。
        // 模糊与缩放共用同一个 graphicsLayer：renderEffect 在绘制阶段读半径，组合期不依赖它
        //（`Modifier.blur` 会在组合期读 dp 值，半径每帧变 = 每帧失效整个主内容 scope）。
        val mainLayerNeeded by remember {
            derivedStateOf {
                isCustomizeExiting ||
                        isWindowCutoutActive ||
                        showSwitchSchedule ||
                        showCustomizePage ||
                        mainContentAlpha != 1f ||
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

        // 只认「几何变换」
        val mainContentTransformed by remember {
            derivedStateOf {
                isCustomizeExiting ||
                        isWindowCutoutActive ||
                        showSwitchSchedule ||
                        showCustomizePage ||
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
                            alpha = mainContentAlpha
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
            // 右侧避让量：右下角竖排的原生 tab bar 占的空间（折叠屏 iPhone 折叠态）。
            val titleEndPadding = (hostNav?.rightSafeAreaInset ?: 0.0).toFloat().dp
            // 右上角两颗按钮的唯一发布点：今日页与课程表页各一组，跟着主 pager 平移。
            // 读 pager 的位移只在这个小函数里，重组范围不外溢到整页。
            val nativeBarHost = LocalNativeTopBarHost.current
            if (nativeBarHost?.ownsTopRight == true) {
                val nativeBarRouter = LocalAppRouter.current
                PublishNativeTopBarButtons(
                    host = nativeBarHost,
                    pagerState = mainPagerState,
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
            // 顶栏/底栏那两层录制**按需挂载**：只有真的有浮层在采「比底栏更高一层」的采样层时
            // 才挂上，否则滚动帧里会白白多录两条全宽条带。
            // ⚠️ Scaffold 先组 popup → 顶栏 → 底栏，**最后**才组 content，所以这里要读的
            // 开关不能是 content 槽里的局部量（那时顶栏/底栏已经组完了）。
            // 这两个 collectAsState 因此提前到 Scaffold 之外订阅。
            val showJumpWeekDialog by viewModel.showJumpWeekDialog.collectAsState()
            val showAddDialog by viewModel.showAddDialog.collectAsState()
            var notice by remember { mutableStateOf<Notice?>(null) }
            var updateDialogShowing by remember { mutableStateOf(false) }
            val menuGlassWanted = showMorePopup || showTodayMorePopup
            val otherGlassWanted = showJumpWeekDialog ||
                showAddDialog ||
                notice != null ||
                sharedFileIntent != null ||
                showDeleteConfirmDialog ||
                showPasteRangeDialog ||
                showRescheduleConflictDialog ||
                updateDialogShowing
            // 菜单单独一路：它有明确的收起进度（fraction）可以跟
            var menuGlassActive by remember { mutableStateOf(menuGlassWanted) }
            // 挂上要**同帧生效**（展开第一帧玻璃就得多采到顶栏），所以在组合期直接写
            if (menuGlassWanted && !menuGlassActive) menuGlassActive = true
            LaunchedEffect(menuGlassWanted) {
                if (menuGlassWanted) return@LaunchedEffect
                // 在收起动画播到一半时摘层：面板还在缩、眼睛跟着运动，换一层看不出来（理由见上方常量）
                snapshotFlow {
                    maxOf(morePopupFraction.value, todayMorePopupFraction.value)
                }.first { it <= MENU_GLASS_RELEASE_FRACTION }
                menuGlassActive = false
            }
            // 其余弹窗一路：退场动画播完屏幕上已经没有它的玻璃了，
            // 什么时候摘都看不见，按固定延时摘即可
            var otherGlassActive by remember { mutableStateOf(otherGlassWanted) }
            if (otherGlassWanted && !otherGlassActive) otherGlassActive = true
            LaunchedEffect(otherGlassWanted) {
                if (otherGlassWanted) return@LaunchedEffect
                delay(OVERLAY_GLASS_RELEASE_DELAY_MS.milliseconds)
                otherGlassActive = false
            }
            // 无材质档：玻璃全关，顶栏/底栏采样加层不挂
            val overlayGlassActive = (menuGlassActive || otherGlassActive) &&
                !com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()
            val scaffoldContent = @Composable {
                Scaffold(
                    modifier = Modifier
                        // ⚠ 只取 Left、不取 Horizontal：`safeDrawing` 的 right 
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Left).union(
                                WindowInsets(
                                    left = (hostNav?.leftSafeAreaInset ?: 0.0).toFloat().dp,
                                    right = 0.dp,
                                )
                            )
                        )
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
                                        Modifier.liquidGlassLayerBackdrop(
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
                    topBar = {
                        // 标题可见性只在页码/方向变化时重组；平移读 pager 写在 graphicsLayer，
                        // 避免动画每帧重组顶栏及 ScheduleTopBar 整棵树。
                        val showTodayTitle by remember(isShiftMode, mainPagerState) {
                            derivedStateOf {
                                if (isShiftMode) {
                                    false
                                } else {
                                    val page = mainPagerState.currentPage
                                    val off = mainPagerState.currentPageOffsetFraction
                                    page == 0 || (page == 1 && off < 0f)
                                }
                            }
                        }
                        val showScheduleTitle by remember(isShiftMode, mainPagerState) {
                            derivedStateOf {
                                val page = mainPagerState.currentPage
                                val off = mainPagerState.currentPageOffsetFraction
                                if (isShiftMode) {
                                    page == 0 || (page == 1 && off < 0f)
                                } else {
                                    page == 1 ||
                                            (page == 0 && off > 0f) ||
                                            (page == 2 && off < 0f)
                                }
                            }
                        }
                        val showSettingsTitle by remember(isShiftMode, mainPagerState) {
                            derivedStateOf {
                                val page = mainPagerState.currentPage
                                val off = mainPagerState.currentPageOffsetFraction
                                if (isShiftMode) {
                                    page == 1 || (page == 0 && off > 0f)
                                } else {
                                    page == 2 || (page == 1 && off > 0f)
                                }
                            }
                        }
                        // 顶栏同样再录一层：下拉菜单挂在它下面、OverlayDialog 也会压到它，
                        // 两者的采样层要「比底栏还高一层」，即内容 + 顶栏 + 底栏
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    // 同底栏：按需挂载，没挂载时组合采样自动退化成内容层
                                    if (overlayGlassActive) {
                                        Modifier.liquidGlassLayerBackdrop(
                                            backdrop = topBarSampleBackdrop,
                                            recordKey = liquidGlassRecordKey,
                                            mustRecord = chromeBarMustRecord,
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            if (showScheduleTitle) {
                                val scheduleTitleIndex = if (isShiftMode) 0 else 1
                                // 空闲停在本页时 delta=0，不加位移节点。
                                // 切 tab 过程中只做 place 位移（`offset {}` 的 lambda 版不进 constraints，
                                // 不会 re-measure）：比 graphicsLayer 少一整块全宽离屏合成，
                                // 也少掉它带来的软件裁剪遮罩（实测每帧 ~2.5ms）。
                                val scheduleTitleIdle by remember(scheduleTitleIndex, navBarStyle) {
                                    derivedStateOf {
                                        val page = mainPagerState.currentPage
                                        val off = mainPagerState.currentPageOffsetFraction
                                        (scheduleTitleIndex - page - off) == 0f
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (scheduleTitleIdle) {
                                                Modifier
                                            } else {
                                                Modifier.offset {
                                                    val page = mainPagerState.currentPage
                                                    val off =
                                                        mainPagerState.currentPageOffsetFraction
                                                    val delta = scheduleTitleIndex - page - off
                                                    if (useVerticalMainPager) {
                                                        IntOffset(0, (delta * screenHPx).roundToInt())
                                                    } else {
                                                        IntOffset(
                                                            (delta * screenWPx).roundToInt(),
                                                            0
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                ) {
                                    // 锁本页主题：共享顶栏槽外层主题跟 selectedTab 变，
                                    // 切到设置页（浅色）时课程表顶栏遮罩会跟着跳浅
                                    PageLockedTopBarTheme(
                                        if (captureThemeActive) captureThemeIsDark
                                        else wallpaperForcedDark
                                    ) {
                                        // 宿主自绘顶栏时，「更多」菜单由宿主用原生菜单渲染，
                                        // 动作仍回到这里 —— 与下面 MorePopupMenus 同一套语义
                                        val topBarRouter = LocalAppRouter.current
                                        ScheduleTopBar(
                                            // Compose 顶栏始终在，翻页靠 offset 位移
                                            visible = true,
                                            // 标题跟着 currentViewingWeek 走：程序化跳周的那一帧就切成目标周，
                                            // 不会再有一段「点完还停在第 10 周」的空窗
                                            pagerCurrentPage = currentViewingWeek - 1,
                                            currentWeek = currentWeek,
                                            isHoliday = viewingIsHoliday,
                                            dayRange = dayRange,
                                            currentDayOfWeek = currentDayOfWeek,
                                            isCurrentWeek = currentViewingWeek == currentWeek && currentWeek in 1..totalWeeks,
                                            weekDates = weekDates,
                                            isReorganized = teachingWeekRules.isNotEmpty(),
                                            onOpenSwitchSchedule = { openScheduleLibrary() },
                                            onJumpWeek = { viewModel.showJumpWeekDialog() },
                                            onEnterCustomize = enterCustomizePage,
                                            onCourseManage = { topBarRouter.navigate(AppRoute.CourseManage) },
                                            isTablet = isTablet,
                                            isShiftMode = isShiftMode,
                                            liquidGlassBackdrop = chromeBackdrop,
                                            scrollBehavior = scheduleScrollBehavior,
                                            blurResampleKey = railBlurResampleEpoch.intValue,
                                            blurSampleTrack = tabletNavExpandSampleTrack,
                                            titleEndPadding = titleEndPadding,
                                            onMoreSlotTop = {
                                                if (canSampleMoreSlot) moreSlotTopPx.value = it
                                            },
                                            onMoreMaterial = { scheduleMaterialAlpha.value = it },
                                            onMoreExpand = { showMorePopup = true },
                                        )
                                    }
                                }
                            }
                            // 设置页是二级页（`AppRoute.Settings`），pager 里没有它。
                            // 只有平板 rail 把设置内嵌成第 3 页，这条顶栏只服务它。
                            // ⚠ 先判 rail 再读 showSettingsTitle：手机上短路，省一次 derivedState 求值。
                            if (navBarStyle == "rail" && showSettingsTitle) {
                                val settingsTitleIndex = if (isShiftMode) 1 else 2
                                val settingsTitleIdle by remember(settingsTitleIndex, navBarStyle) {
                                    derivedStateOf {
                                        val page = mainPagerState.currentPage
                                        val off = mainPagerState.currentPageOffsetFraction
                                        (settingsTitleIndex - page - off) == 0f
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (settingsTitleIdle) {
                                                Modifier
                                            } else {
                                                Modifier.graphicsLayer {
                                                    val page = mainPagerState.currentPage
                                                    val off =
                                                        mainPagerState.currentPageOffsetFraction
                                                    val delta = settingsTitleIndex - page - off
                                                    if (useVerticalMainPager) {
                                                        translationX = 0f
                                                        translationY = delta * screenHPx
                                                    } else {
                                                        translationY = 0f
                                                        translationX = delta * size.width
                                                    }
                                                }
                                            }
                                        )
                                ) {
                                    SettingsTopBar(
                                        liquidGlassBackdrop = chromeBackdrop,
                                        navBarStyle = navBarStyle,
                                        scrollBehavior = settingsScrollBehavior,
                                        blurResampleKey = railBlurResampleEpoch.intValue,
                                        blurSampleTrack = tabletNavExpandSampleTrack,
                                        titleEndPadding = titleEndPadding,
                                    )
                                }
                            }
                            // 始终渲染，保证 currentHeightPx 启动即就位，切页不慢一帧
                            // 空闲在本页时不挂层；离屏时用 graphicsLayer 位移+隐藏
                            val todayTitleIdle by remember(navBarStyle) {
                                derivedStateOf {
                                    val page = mainPagerState.currentPage
                                    val off = mainPagerState.currentPageOffsetFraction
                                    val delta = 0 - page - off
                                    delta == 0f && showTodayTitle
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (todayTitleIdle) {
                                            Modifier
                                        } else {
                                            Modifier.graphicsLayer {
                                                val page = mainPagerState.currentPage
                                                val off = mainPagerState.currentPageOffsetFraction
                                                val delta = 0 - page - off
                                                if (useVerticalMainPager) {
                                                    translationX = 0f
                                                    translationY = delta * screenHPx
                                                } else {
                                                    translationY = 0f
                                                    translationX = delta * size.width
                                                }
                                                alpha = if (showTodayTitle) 1f else 0f
                                            }
                                        }
                                    )
                            ) {
                                // 同 ScheduleTopBar：锁本页主题，避免切到设置页时今日页遮罩跳浅
                                PageLockedTopBarTheme(
                                    if (captureThemeActive) captureThemeIsDark
                                    else todayPageForcedDark
                                ) {
                                    TodayTopBar(
                                        liquidGlassBackdrop = chromeBackdrop,
                                        navBarStyle = navBarStyle,
                                        currentDayOfWeek = todaySelectedDayOfWeek,
                                        isToday = todayIsToday,
                                        onBackToToday = { scrollToTodayTrigger++ },
                                        onJumpToDate = { todayJumpToDateTrigger++ },
                                        onEnterCustomize = enterCustomizePage,
                                        scrollBehavior = todayScrollBehavior,
                                        visible = showTodayTitle,
                                        blurResampleKey = railBlurResampleEpoch.intValue,
                                        blurSampleTrack = tabletNavExpandSampleTrack,
                                        titleEndPadding = titleEndPadding,
                                        onMoreSlotTop = {
                                            if (canSampleMoreSlot) moreSlotTopPx.value = it
                                        },
                                        onMoreMaterial = { todayMaterialAlpha.value = it },
                                        onMoreExpand = { showTodayMorePopup = true },
                                    )
                                }
                            }
                        }
                    },
                    // popup 槽按组合顺序绘制：「更多」排在 MiuixPopupHost 之前，
                    // 只高于顶栏按钮、低于弹窗，打开弹窗时会被遮罩一起压暗。
                    popupHost = {
                        // 单个「更多」控件随所在顶栏一起滑动：与顶栏同一套 delta 公式；
                        // 非当前页时整块被平移出屏幕，天然只留一份可见
                        val scheduleMoreMenuOffset: () -> Offset = {
                            val page = mainPagerState.currentPage
                            val off = mainPagerState.currentPageOffsetFraction
                            val delta = (if (isShiftMode) 0 else 1) - page - off
                            if (useVerticalMainPager) Offset(0f, delta * screenHPx)
                            else Offset(delta * screenWPx, 0f)
                        }
                        val todayMoreMenuOffset: () -> Offset = {
                            val page = mainPagerState.currentPage
                            val off = mainPagerState.currentPageOffsetFraction
                            // 排班模式下没有今日页，恒移出屏幕
                            val delta = if (isShiftMode) -1f else (-page - off)
                            if (useVerticalMainPager) Offset(0f, delta * screenHPx)
                            else Offset(delta * screenWPx, 0f)
                        }
                        // 不包 HostedOverlay：iOS 上浮层独立合成根拿不到主内容开洞缩放，
                        // 「更多」会不随顶栏缩小、盖在课表外观页上仍可点；留在本槽与顶栏按钮同层。
                        PageLockedTopBarTheme(wallpaperForcedDark) {
                            // LocalAppRouter.current 必须在组合层取（onClick lambda 里取不到）
                            val popupRouter = LocalAppRouter.current
                            MorePopupMenus(
                                showMorePopup = showMorePopup,
                                onMorePopupDismiss = { showMorePopup = false },
                                onMorePopupExpand = { showMorePopup = true },
                                showTodayMorePopup = showTodayMorePopup,
                                onTodayMorePopupDismiss = { showTodayMorePopup = false },
                                onTodayMorePopupExpand = { showTodayMorePopup = true },
                                morePopupFraction = morePopupFraction,
                                todayMorePopupFraction = todayMorePopupFraction,
                                scheduleMenuOffset = scheduleMoreMenuOffset,
                                todayMenuOffset = todayMoreMenuOffset,
                                moreSlotTopPx = moreSlotTopPx,
                                scheduleMaterialAlpha = scheduleMaterialAlpha.value,
                                todayMaterialAlpha = todayMaterialAlpha.value,
                                liquidGlassBackdrop = overlayGlassBackdrop,
                                isShiftMode = isShiftMode,
                                // 平板（rail）右上角不显示「更多」：跳转/管理/外观都在侧栏里。
                                // iOS 主界面右上角那颗已由原生 UIMenu 接管，Compose 侧恒不渲染
                                enabled = navBarStyle != "rail" && hostNavigation == null,
                                // 鸿蒙：「更多」按钮和它的菜单由 ArkUI 画，
                                // 这颗 Compose 触发按钮必须关掉，否则会从原生按钮下面露出来
                                scheduleMoreNative = LocalNativeTopBarHost.current?.ownsTopRight == true,
                                todayMoreNative = LocalNativeTopBarHost.current?.ownsTopRight == true,
                                // 预测性返回由 LiquidGlassDropdownMenu 内部驱动同一个 fraction Animatable，
                                // 外面再 snapTo/animateTo 会和它互相 cancel，动画直接卡死。
                                onJumpWeek = { viewModel.showJumpWeekDialog() },
                                onCourseManage = {
                                    popupRouter.navigate(
                                        AppRoute.CourseManage
                                    )
                                },
                                onEnterCustomize = enterCustomizePage,
                                onJumpToDate = { todayJumpToDateTrigger++ },
                            )
                        }
                        // 弹窗排在「更多」之后 → 始终盖在它之上，遮罩能压暗「更多」
                        MiuixPopupHost()
                    }
                ) {
                    // 详情 morph 期间底下的真实源卡片必须继续画
                    // 不门控 combinations.isEmpty()：网格只依赖 viewModel，与壁纸加载解耦
                    // backdrop 挂在未 padding 的全屏层：录制原点固定在屏幕
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 开洞时 mustRecord 恒 true：停录会让顶栏/底栏玻璃采样空内容
                            .then(
                                // 无材质档：玻璃全关，内容采样录屏层不挂
                                if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) {
                                    Modifier
                                } else {
                                    Modifier.liquidGlassLayerBackdrop(
                                        backdrop = liquidGlassBackdrop,
                                        recordKey = liquidGlassRecordKey,
                                        mustRecord = liquidGlassMustRecord
                                    )
                                }
                            )
                    ) {
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
                            mainPagerState,
                        ) {
                            derivedStateOf {
                                // 搭配页/开洞编辑：必须始终显示壁纸层，否则选完壁纸「没反应」
                                if (showCustomizePage && sharedWallpaperBitmap != null) {
                                    return@derivedStateOf true
                                }
                                val page = mainPagerState.currentPage
                                val off = abs(mainPagerState.currentPageOffsetFraction)
                                !isShiftMode && sharedWallpaperBitmap != null &&
                                        (page != 2 || off > 0.01f)
                            }
                        }
                        if (showSharedWallpaperLayer && sharedWallpaperBitmap != null) {
                            // 全屏垫背景色 + 裁切：页滑走露出的空隙由背景色/壁纸垫住（侧栏下始终有内容，不出漏）
                            val wallpaperPadColor =
                                MiuixTheme.colorScheme.surface
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(wallpaperPadColor)
                                    .clipToBounds()
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
                                val sharedWallpaperImage = remember(sharedWallpaperBitmap) {
                                    sharedWallpaperBitmap
                                }
                                Image(
                                    bitmap = sharedWallpaperImage,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        // 卡片（含无材质档：模糊/折射关但保留壁纸采样与着色/不透明度）
                                        // 从这里实时采样
                                        .liquidGlassLayerBackdrop(
                                            backdrop = sharedWallpaperBackdrop,
                                            recordKey = listOf(
                                                sharedWallpaperBitmap,
                                                sharedWallpaperScale,
                                                sharedWallpaperOffset.x,
                                                sharedWallpaperOffset.y,
                                                sharedBrightness,
                                                displayAppearance.wallpaperBlur,
                                                sharedMinScale,
                                            ),
                                        )
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

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (navBarStyle == "rail") {
                                        tabletNavRailStartPadding()
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
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
                            val mainPagerPageContent: @Composable (Int) -> Unit = { page ->
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
                                                        TodayScreen(
                                                            viewModel = viewModel,
                                                            settingsViewModel = settingsViewModel,
                                                            // 今日页课程列表不直接进详情；这个只传给详情弹窗：
                                                            // 点弹窗卡片进全屏时要藏掉源卡片，morph 才干净。
                                                            hiddenCourseIds = hiddenCourseIds,
                                                            holidayDataTick = resumeCount,
                                                            onCourseClick = { courses, left, top, width, height, cardLayer, courseIdToHide, targetWeek ->
                                                                openCourseDetail(
                                                                    courses,
                                                                    left,
                                                                    top,
                                                                    width,
                                                                    height,
                                                                    cardLayer = cardLayer,
                                                                    fromToday = true,
                                                                    courseIdToHide = courseIdToHide,
                                                                    targetWeek = targetWeek
                                                                )
                                                            },
                                                            pagerState = todayPagerState,
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
                                                            liquidGlassBackdrop = liquidGlassBackdrop,
                                                            showClassroom = displayAppearance.showClassroom,
                                                            showTeacher = displayAppearance.showTeacher,
                                                            onListScrollInProgress = {
                                                                todayListScrollInProgress.value = it
                                                            },
                                                            // 今日助手卡留在 :androidApp（天气/定位/Geocoder），通过槽位注入
                                                            todayAssistantCard = todayAssistantCard,
                                                            // 视图模式与课表页共用同一个开关
                                                            scheduleViewMode = scheduleViewMode,
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
                                                            // 手势状态提升并注入 state：借位跳周被手势打断时，
                                                            // jumpToPageSmoothly 借它同步平移松手落页基准
                                                            val weekGestureState = rememberPagerTakeoverGestureState()
                                                            weekPagerGestureState = weekGestureState
                                                            MainScheduleScreen(
                                                                viewModel = viewModel,
                                                                settingsViewModel = settingsViewModel,
                                                                pagerState = pagerState,
                                                                scheduleViewMode = scheduleViewMode,
                                                                jumpNeighbor = weekJumpNeighbor,
                                                                pagerGestureState = weekGestureState,
                                                                hiddenCourseIds = hiddenCourseIds,
                                                                draggingCourseSlots = draggingCourseSlots,
                                                                onCourseClick = { courses, left, top, width, height, cardLayer, courseIdToHide, targetWeek ->
                                                                    openCourseDetail(
                                                                        courses,
                                                                        left,
                                                                        top,
                                                                        width,
                                                                        height,
                                                                        cardLayer = cardLayer,
                                                                        fromToday = false,
                                                                        courseIdToHide = courseIdToHide,
                                                                        targetWeek = targetWeek
                                                                    )
                                                                },
                                                                onPopupStateChange = {
                                                                    showCourseDetailPopup = it
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
                                                                    // 拖过位移阈值才到这里（卡片侧 moved > slop 才回调），
                                                                    // 跟手高光从这一刻才亮，紧接着 onCourseMenuDismiss 收菜单
                                                                    isFloatingCardDragging = true
                                                                    refreshPendingDropTarget()
                                                                },
                                                                onCourseMenuDismiss = {
                                                                    shortcutMenuVisible = false
                                                                    coroutineScope.launch {
                                                                        delay(220.milliseconds)
                                                                        shortcutMenuCourse = null
                                                                    }
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
                                                                    // 只结束拖拽浮层；菜单关闭交给 onCourseMenuDismiss
                                                                    val source = if (dropAborted) null else draggedCardCourse
                                                                    val target = pendingDropTarget
                                                                    val week = draggedWeek
                                                                    setPendingDropTarget(null)
                                                                    // 翻周动画还在跑时落点仍属于上一页：此时写库会写进错的周，
                                                                    // 宁可吸回原位让用户重拖
                                                                    if (source != null && target != null &&
                                                                        !pagerState.isScrollInProgress
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
                                                                                    animateInCourseIds =
                                                                                        sourceCourses.map { it.id }
                                                                                            .toSet()
                                                                                    coroutineScope.launch {
                                                                                        delay(350.milliseconds)
                                                                                        animateInCourseIds =
                                                                                            emptySet()
                                                                                    }
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
                                                                liquidGlassBackdrop = liquidGlassBackdrop,
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
                                                                externalSelectedCourses = scheduleSelectedCourses
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
                                            // 卡片面铺满整屏（含右侧栏下方），列表内容再避让右侧栏。
                                            // 只内缩内容的话，屏幕圆角裁切会在屏内 ~84pt 处收口 ——
                                            // 圆角跑到屏幕里侧、右边露一条壁纸，与顶部渐变糊带的沉浸口径不一致。
                                            Box(modifier = settingsPageClipModifier) {
                                                MiuixTheme(controller = settingsThemeController) {
                                                    CompositionLocalProvider(LocalForcedDarkTheme provides settingsPageForcedDark) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                // ⚠ 顺序不能反：background 必须在 padding 之前 ——
                                                                // 卡片面要铺到屏幕边缘（右侧栏下方），只有内容内缩
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
                                                                    isShiftMode = false,
                                                                    onExitShiftMode = {},
                                                                    onEnterShiftMode = {
                                                                        FeatureLog.shift("enter_loading")
                                                                        showShiftLoading = true
                                                                        isExitingShift = false
                                                                    },
                                                                    navBarStyle = navBarStyle,
                                                                    settingsScrollBehavior = settingsScrollBehavior,
                                                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                                                    showUpdateSettings = showUpdateSettings,
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        3 -> {
                                            // 课程管理不预加载：beyond 撑满整窗后这一格任何时候都在窗口里，
                                            // 不加 if 就等于常驻；只有它是当前页才真正组合。
                                            if (mainPagerState.currentPage == 3) {
                                                TabletCourseManagePane(
                                                    viewModel = viewModel,
                                                    settingsViewModel = settingsViewModel,
                                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                                )
                                            }
                                        }

                                        4 -> {
                                            // 切换课表同上，不预加载
                                            if (mainPagerState.currentPage == 4) {
                                                val shareRepository =
                                                    remember { CourseRepository() }
                                                TabletSwitchSchedulePane(
                                                    viewModel = viewModel,
                                                    scheduleViewModel = scheduleViewModel,
                                                    settingsViewModel = settingsViewModel,
                                                    liquidGlassBackdrop = liquidGlassBackdrop,
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
                                                ShiftScheduleScreen(
                                                    shiftViewModel = shiftViewModel,
                                                    settingsViewModel = settingsViewModel,
                                                    pagerState = pagerState,
                                                    scheduleDataVersion = dataVersion,
                                                    cardHeightPerSection = currentAppearance().cardHeight,
                                                    scheduleScrollBehavior = scheduleScrollBehavior,
                                                    isInFreeformWindow = isInFreeformWindow,
                                                    titleBarHeight = 56.dp,
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
                                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                                        showUpdateSettings = showUpdateSettings,
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // 竖排分支不预组合相邻页：Compose 从「当前页 + 1」开始数
                            // beyondViewportPageCount 个页去测量，请求的页号不会夹回 pageCount
                            // （折叠屏 iPhone 实测 IndexOutOfBoundsException: Index 3, size 3）。
                            if (useVerticalMainPager) {
                                VerticalPager(
                                    state = mainPagerState,
                                    modifier = mainPagerModifier,
                                    key = mainPagerPageKey,
                                    userScrollEnabled = false,
                                    beyondViewportPageCount = 0,
                                ) { page -> mainPagerPageContent(page) }
                            } else {
                                // 手机切 tab 是 spring 平移动画：beyond=1 时目标页隔壁那页会在动画途中
                                // 才首次组合、砸在首帧上；beyond=2 让三页常驻，切页变纯位移动画。
                                HorizontalPager(
                                    state = mainPagerState,
                                    modifier = mainPagerModifier,
                                    key = mainPagerPageKey,
                                    userScrollEnabled = false,
                                    beyondViewportPageCount = 2,
                                ) { page -> mainPagerPageContent(page) }
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
                            jumpWeekTemp = pagerState.currentPage + 1
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
                        onOverwrite = { ids ->
                            animateInCourseIds = ids
                            coroutineScope.launch {
                                delay(350.milliseconds)
                                animateInCourseIds = emptySet()
                            }
                        },
                        onSwap = { ids ->
                            animateInCourseIds = ids
                            coroutineScope.launch {
                                delay(350.milliseconds)
                                animateInCourseIds = emptySet()
                            }
                        },
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
                    // 提饱和：与玻璃面板同源，采样主内容 export。
                    //
                    // 半径取 min(短边, 250dp) × 0.5（与 InteractiveHighlight 同式），遮罩用径向渐变
                    // ——渐变在半径外恒为 0，所以只有「光晕包围盒」这一块有像素。节点做成这个
                    // 正方形、跟着卡片中心走：整屏节点要付两次全屏离屏（saveLayer + 带 ColorFilter
                    // 的采样层），包围盒只有 250dp 见方，像素量差一个数量级。
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
                                backdrop = liquidGlassBackdrop,
                                shape = { RectangleShape },
                                effects = { colorControls(saturation = 1.5f) },
                                highlight = null,
                                shadow = null,
                            )
                    )
                }
            }
            // 始终用 MiuixTheme 包裹保持结构恒定；ThemeController 原地切 mode，重建实例会卡一帧
            val effectiveForcedDark = if (captureThemeActive) captureThemeIsDark else forcedDark
            val effectiveDark = effectiveForcedDark ?: appSettingDark
            val pageController = rememberAppThemeController(isDark = effectiveDark)
            MiuixTheme(controller = pageController) {
                CompositionLocalProvider(LocalForcedDarkTheme provides effectiveForcedDark) {
                    scaffoldContent()
                    // 平板：胶囊/侧栏叠层（不占 Scaffold bottomBar，内容可正常滚动）
                    if (navBarStyle == "rail") {
                        val onTabletTabSelected: (Int) -> Unit = { idx ->
                            if (idx != selectedTab) {
                                mainTabProgrammatic = true
                                selectedTab = idx
                                coroutineScope.launch {
                                    try {
                                        if (todayPagerState.isScrollInProgress) todayPagerState.cancelScroll()
                                        if (pagerState.isScrollInProgress) pagerState.cancelScroll()
                                        if (mainPagerState.isScrollInProgress) mainPagerState.cancelScroll()
                                        mainPagerState.scrollToPage(idx)
                                    } finally {
                                        mainTabProgrammatic = false
                                    }
                                }
                            }
                        }
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
                            modifier = Modifier.fillMaxSize().zIndex(22f),
                        )
                    }
                    // 平板设置：标题与分界线画在顶栏模糊之上。
                    // 主题锁定 appSettingDark：不随课程表/壁纸锁色瞬间切换。
                    // ⚠ 平板主界面切页是瞬时的（scrollToPage，无动画），这里不挂跟位位移与圆角裁切
                    //   —— 那套只服务手机的「圆角卡片盖住壁纸」转场。
                    // ⚠ 叠层必须只在设置页（rail 下第 3 页）当前时渲染：它画的是左栏「我的」+
                    //   右栏 dest 标题，画在 pager 外面、不带位移，留在今日/课程表页就会串页
                    //   （「今天是周六 我的 学期与周次」）。
                    if (navBarStyle == "rail" && !isShiftMode && mainPagerState.currentPage == 2) {
                        val settingsChromeController =
                            rememberAppThemeController(isDark = appSettingDark)
                        MiuixTheme(controller = settingsChromeController) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                TabletSettingsChromeOverlay()
                            }
                        }
                    }
                }
            }

        }
    }
}

/**
 * 右上角按钮的唯一发布点（鸿蒙 ArkUI 宿主）。
 *
 * 今日页与课程表页各发一组，位移按主 pager 的 `page + offsetFraction` 算 —— 宿主画在 Compose
 * 之外，不跟着 pager 走，只能靠这个值让按钮随页面平移。
 */
@Composable
private fun PublishNativeTopBarButtons(
    host: NativeTopBarHost,
    pagerState: PagerState,
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
    val statusBarHeight =
        if (hostVerticalBarEdge()) 0.dp
        else WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 折叠顶栏 collapsed 高 52，按钮在其中垂直居中 ⇒ 与 Compose 侧同一中线
    val top = statusBarHeight + (CollapsedHeight - buttonSize) / 2

    val page = pagerState.currentPage
    val fraction = pagerState.currentPageOffsetFraction
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
