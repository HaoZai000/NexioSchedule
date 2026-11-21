/** 课表外观页：壁纸、卡片与视图参数编辑，带开洞预览与入场形变动画。 */
package com.haooz.chedule.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.appearance.AppearanceConfig
import com.haooz.chedule.data.appearance.AppearanceStore
import com.haooz.chedule.data.appearance.ThemeMode
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.activities.classschedule.ScheduleViewMode
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.TodayViewMode
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.screenHeightDp
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.nexioui.divider.VerticalDivider
import com.haooz.nexioui.dropdown.DropdownDefaults
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Album
import com.haooz.nexioui.icon.extended.Background
import com.haooz.nexioui.icon.extended.Close
import com.haooz.nexioui.icon.extended.GridView
import com.haooz.nexioui.icon.extended.Image
import com.haooz.nexioui.icon.extended.Months
import com.haooz.nexioui.interaction.InteractiveHighlight
import com.haooz.nexioui.motion.CustomizePageEasing
import com.haooz.nexioui.navigationback.NavigationBackHandlerFor
import com.haooz.nexioui.progressindicator.InfiniteProgressIndicator
import com.haooz.nexioui.scaffold.Scaffold
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.window.addScreenCornerRect
import com.haooz.nexioui.window.isTabletWidth
import com.haooz.nexioui.window.toShape
import com.haooz.softglass.GlassEnvironment
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur as backdropBlur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.glass.BrightTone
import com.haooz.softglass.glass.CustomizeFill
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.softglass.shape.ContinuousCapsule
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 外观页遮罩底色（与外壳 `shellBackground` 同色）。 */
private val PageBackground = Color(0xFF1A1A1A)

/** 胶囊实色底（无材质档与中段切换器共用）。 */
private val CapsuleFill = Color(0xFF363636)

/** 「取消」胶囊的自定义玻璃底色。 */
private val CancelGlassFill = Color(0xFF5C5C5C)

private const val LIVE_CANCEL_GLASS_KEY = "NexioSoftGlassLiveCustomizeCancel"
private const val LIVE_APPLY_GLASS_KEY = "NexioSoftGlassLiveCustomizeApply"

/** 预览卡片缩放三档：常规 / 开洞编辑 / 全屏（应用与退出）。 */
private const val CARD_SCALE_PREVIEW = 0.65f
private const val CARD_SCALE_EDIT = 0.75f
private const val CARD_SCALE_FULL = 1f
private const val CARD_SCALE_RANGE = CARD_SCALE_FULL - CARD_SCALE_PREVIEW

/** 卡片中心相对屏幕中心的竖向偏移比例。 */
private const val CARD_OFFSET_Y_RATIO = 0.028f

private const val ENTER_MILLIS = 380
private const val EXIT_MILLIS = 500
private const val CUTOUT_MILLIS = 400
private const val FADE_MILLIS = 250

private fun scaleProgress(scale: Float): Float =
    ((scale - CARD_SCALE_PREVIEW) / CARD_SCALE_RANGE).coerceIn(0f, 1f)

/** 挖洞遮罩路径缓存：尺寸与圆角不变时只做 translate，不重建 squircle。 */
private class CutoutPathCache {
    private val path = Path()
    private var screenW = -1f
    private var screenH = -1f
    private var holeW = -1f
    private var holeH = -1f
    private var corners: ScreenCornerRadiiPx? = null

    fun obtain(
        screenW: Float,
        screenH: Float,
        holeW: Float,
        holeH: Float,
        corners: ScreenCornerRadiiPx,
    ): Path {
        if (this.screenW != screenW || this.screenH != screenH ||
            this.holeW != holeW || this.holeH != holeH || this.corners != corners
        ) {
            this.screenW = screenW
            this.screenH = screenH
            this.holeW = holeW
            this.holeH = holeH
            this.corners = corners
            path.rewind()
            val outer = Path().apply {
                addRect(Rect(-screenW * 2f, -screenH * 2f, screenW * 3f, screenH * 3f))
            }
            val hole = Path().apply {
                addScreenCornerRect(width = holeW, height = holeH, corners = corners)
            }
            if (!path.op(outer, hole, PathOperation.Difference)) {
                path.addPath(outer)
                path.addPath(hole)
                path.fillType = PathFillType.EvenOdd
            }
        }
        return path
    }
}

@Composable
fun CustomizeScheduleScreen(
    snapshot: ImageBitmap?,
    screenCornerRadius: ScreenCornerRadiiPx,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    /** 默认主题实时预览：只改内存临时档位，点「应用」后才落盘 */
    onThemeModePreview: (ThemeMode) -> Unit = {},
    onPickWallpaper: () -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    combinations: List<CombinationThumbnail> = emptyList(),
    currentCombinationIndex: Int = 0,
    isExiting: Boolean = false,
    isApplying: Boolean = false,
    isApplyingCustomize: Boolean = false,
    wallpaperBitmap: ImageBitmap? = null,
    wallpaperOffset: Offset = Offset.Zero,
    wallpaperScale: Float = 1f,
    onWallpaperOffsetChange: (Offset) -> Unit = {},
    onWallpaperScaleChange: (Float) -> Unit = {},
    /** 开洞中心回传（屏幕比例），供宿主对齐缩放原点 */
    onCutoutCenterChange: (xRatio: Float, yRatio: Float) -> Unit = { _, _ -> },
    /** 右侧是否有原生 chrome 占位（折叠屏竖排底栏），开洞需整体让开 */
    rightChromeReserved: Boolean = false,
    /** 弹窗开合位移驱动器：与主界面共享同一实例，保证同帧同步 */
    sheetOffsetShared: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> =
        Animatable(0f),
    pendingEnterCutout: Boolean = false,
    onCutoutEntered: () -> Unit = {},
    appearance: AppearanceConfig = AppearanceConfig(),
    onAppearanceChange: (AppearanceConfig) -> Unit = {},
    scheduleViewMode: ScheduleViewMode = ScheduleViewMode.WEEK,
    onScheduleViewModeChange: (ScheduleViewMode) -> Unit = {},
    todayViewMode: TodayViewMode = TodayViewMode.WEEK,
    onTodayViewModeChange: (TodayViewMode) -> Unit = {},
    hasWallpaper: Boolean = false,
    /** 预览页：0=今日，1=课程表 */
    previewPage: Int = 0,
    onPreviewPageChange: (Int) -> Unit = {},
    /** 当前「默认主题」档位 */
    themeMode: () -> ThemeMode = { ThemeMode.FOLLOW_WALLPAPER },
    onThemeModePersist: (ThemeMode) -> Unit = {},
) {
    val density = LocalDensity.current
    val isTablet = isTabletWidth()
    val materialNone = AppMaterialSettings.isNoneTier()
    val minCornerPx = with(density) { 20.dp.toPx() }
    val cutoutOffsetTargetPx = with(density) { (if (rightChromeReserved) -16.dp else -10.dp).toPx() }
    val cutoutOffsetXTargetPx = with(density) { (if (rightChromeReserved) -56.dp else 0.dp).toPx() }
    val sheetOffsetTargetPx = with(density) { 90.dp.toPx() }
    val toolOffsetTargetPx = with(density) { 80.dp.toPx() }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenWPx = with(density) { screenWidthDp().dp.toPx() }
    val screenHPx = with(density) { screenHeightDp().dp.toPx() }
    val defaultSheetHeight = screenHeightDp().dp * 0.52f

    // 壁纸最小缩放：铺满短边，避免缩放过小露出底色
    val minWallpaperScale = remember(wallpaperBitmap, screenWPx, screenHPx) {
        val bitmap = wallpaperBitmap
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            val fit = minOf(screenWPx / bitmap.width, screenHPx / bitmap.height)
            val cover = maxOf(screenWPx / bitmap.width, screenHPx / bitmap.height)
            if (fit > 0f) cover / fit else 1f
        } else 1f
    }

    // 纯坐标采样层：铺不透明底色，否则空内容会按 alpha 压成全透明、胶囊材质整体消失
    val glassBackdrop = com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop {
        drawRect(PageBackground)
    }
    val dropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )

    // ── 草稿状态 ──────────────────────────────────────────────────────────
    val draft = remember(currentCombinationIndex) { AppearanceDraft(appearance) }
    LaunchedEffect(appearance) { draft.sync(appearance) }

    // 无材质档会丢掉存档里的模糊/折射，写回时还原原值，避免静默清档
    val storedBlur = remember { AppearanceStore.getCardBlur() }
    val storedRefraction = remember { AppearanceStore.getCardRefraction() }
    val latestOnAppearanceChange by rememberUpdatedState(onAppearanceChange)
    LaunchedEffect(Unit) {
        snapshotFlow {
            val config = draft.config
            if (materialNone) {
                config.copy(cardBlurRadius = storedBlur, cardRefraction = storedRefraction)
            } else {
                config
            }
        }
            .distinctUntilChanged()
            .collect { latestOnAppearanceChange(it) }
    }

    var themeModeValue by remember(currentCombinationIndex) { mutableStateOf(themeMode()) }
    // 无壁纸时档位无意义，复位为跟随壁纸
    LaunchedEffect(hasWallpaper) {
        if (!hasWallpaper && themeModeValue != ThemeMode.FOLLOW_WALLPAPER) {
            themeModeValue = ThemeMode.FOLLOW_WALLPAPER
            onThemeModePersist(ThemeMode.FOLLOW_WALLPAPER)
        }
    }

    var showEffectSheet by remember { mutableStateOf(false) }
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showApplyLoading by remember { mutableStateOf(false) }
    LaunchedEffect(isApplyingCustomize) {
        if (isApplyingCustomize) showApplyLoading = false
    }

    // ── 动画状态 ──────────────────────────────────────────────────────────
    val animProgress = remember { Animatable(0f) }
    var animDone by remember { mutableStateOf(false) }
    var isPageAnimating by remember { mutableStateOf(true) }
    val buttonScale = remember { Animatable(1.5f) }
    val buttonAlpha = remember { Animatable(1f) }

    var isCutoutActive by remember { mutableStateOf(false) }
    var isCutoutAnimating by remember { mutableStateOf(false) }
    var cardHidden by remember { mutableStateOf(false) }
    val cutoutProgress = remember { Animatable(0f) }
    val cardScale = remember { Animatable(CARD_SCALE_PREVIEW) }
    val cutoutOffsetX = remember { Animatable(0f) }
    val cutoutOffsetY = remember { Animatable(0f) }
    val sheetOffsetY = sheetOffsetShared
    val cutoutPathCache = remember { CutoutPathCache() }

    val toolAlpha = remember { Animatable(0f) }
    val toolOffsetY = remember { Animatable(toolOffsetTargetPx) }
    val toolBlur = remember { Animatable(8f) }

    // 新建搭配后自动进入编辑模式
    LaunchedEffect(pendingEnterCutout) {
        if (pendingEnterCutout) {
            if (!isCutoutActive) isCutoutActive = true
            onCutoutEntered()
        }
    }

    // 入场时间轴：快照 / 顶栏按钮 / 底部工具栏同起同落
    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                animProgress.animateTo(1f, tween(ENTER_MILLIS, easing = CustomizePageEasing))
                animDone = true
                isPageAnimating = false
            }
            launch { buttonScale.animateTo(1f, tween(ENTER_MILLIS, easing = CustomizePageEasing)) }
            launch { toolAlpha.animateTo(1f, tween(ENTER_MILLIS, easing = CustomizePageEasing)) }
            launch { toolOffsetY.animateTo(0f, tween(ENTER_MILLIS, easing = CustomizePageEasing)) }
            launch { toolBlur.animateTo(0f, tween(ENTER_MILLIS, easing = CustomizePageEasing)) }
        }
    }

    LaunchedEffect(isExiting) {
        if (!isExiting) return@LaunchedEffect
        coroutineScope {
            launch { buttonScale.animateTo(1.5f, tween(EXIT_MILLIS, easing = CustomizePageEasing)) }
            launch { toolAlpha.animateTo(0f, tween(EXIT_MILLIS, easing = CustomizePageEasing)) }
            launch {
                toolOffsetY.animateTo(toolOffsetTargetPx, tween(EXIT_MILLIS, easing = CustomizePageEasing))
            }
            launch { toolBlur.animateTo(8f, tween(EXIT_MILLIS, easing = CustomizePageEasing)) }
            // 取消路径把开洞一并放大到全屏；应用路径由 isApplying 单独处理
            if (!isApplying) {
                launch { cardScale.animateTo(CARD_SCALE_FULL, tween(EXIT_MILLIS, easing = CustomizePageEasing)) }
            }
        }
    }

    LaunchedEffect(isCutoutActive) {
        if (isCutoutActive) {
            isCutoutAnimating = true
            coroutineScope {
                launch { cardScale.animateTo(CARD_SCALE_EDIT, tween(CUTOUT_MILLIS, easing = CustomizePageEasing)) }
                launch { cutoutProgress.snapTo(1f) }
                launch {
                    cutoutOffsetY.animateTo(cutoutOffsetTargetPx, tween(CUTOUT_MILLIS, easing = CustomizePageEasing))
                }
                launch {
                    cutoutOffsetX.animateTo(cutoutOffsetXTargetPx, tween(CUTOUT_MILLIS, easing = CustomizePageEasing))
                }
            }
            cardHidden = true
            isCutoutAnimating = false
        } else if (animDone) {
            cardHidden = false
            coroutineScope {
                launch {
                    cardScale.animateTo(CARD_SCALE_PREVIEW, tween(CUTOUT_MILLIS, easing = CustomizePageEasing))
                }
                launch { cutoutProgress.snapTo(0f) }
                launch { buttonAlpha.animateTo(1f, tween(FADE_MILLIS)) }
                launch { cutoutOffsetY.animateTo(0f, tween(CUTOUT_MILLIS, easing = CustomizePageEasing)) }
                launch { cutoutOffsetX.animateTo(0f, tween(CUTOUT_MILLIS, easing = CustomizePageEasing)) }
            }
        }
    }

    // 应用动画：裁剪区域跟随卡片放大
    LaunchedEffect(isApplying) {
        if (isApplying) {
            coroutineScope {
                launch { cardScale.animateTo(CARD_SCALE_FULL, tween(EXIT_MILLIS, easing = CustomizePageEasing)) }
                launch { buttonAlpha.animateTo(0f, tween(FADE_MILLIS)) }
            }
        }
    }

    val anySheetOpen = showEffectSheet || showCustomizeSheet
    LaunchedEffect(anySheetOpen) {
        if (anySheetOpen) {
            coroutineScope {
                launch { buttonAlpha.animateTo(0f, tween(FADE_MILLIS)) }
                launch { sheetOffsetY.animateTo(-sheetOffsetTargetPx, tween(400, easing = CustomizePageEasing)) }
            }
        } else {
            coroutineScope {
                launch { buttonAlpha.animateTo(1f, tween(FADE_MILLIS)) }
                launch { sheetOffsetY.animateTo(0f, tween(350, easing = CustomizePageEasing)) }
            }
        }
    }

    // 开洞中心回传：与主内容缩放同源，1 - prog == (1 - scale) / 0.35
    val latestScreenWPx by rememberUpdatedState(screenWPx)
    val latestScreenHPx by rememberUpdatedState(screenHPx)
    val latestOnCutoutCenterChange by rememberUpdatedState(onCutoutCenterChange)
    LaunchedEffect(Unit) {
        snapshotFlow { cutoutOffsetX.value to cutoutOffsetY.value }.collect { (offsetX, offsetY) ->
            latestOnCutoutCenterChange(
                0.5f + offsetX / (latestScreenWPx * 0.35f),
                0.58f + offsetY / (latestScreenHPx * 0.35f),
            )
        }
    }

    NavigationBackHandlerFor(
        enabled = true,
        onBackCompleted = {
            if (!isPageAnimating && !isCutoutAnimating) onDismiss()
        },
        onBackCancelled = {},
    )

    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .cutoutBackdrop(
                    snapshot = snapshot,
                    screenCornerRadius = screenCornerRadius,
                    minCornerPx = minCornerPx,
                    cardScale = cardScale,
                    cutoutProgress = cutoutProgress,
                    cutoutOffsetX = cutoutOffsetX,
                    cutoutOffsetY = cutoutOffsetY,
                    sheetOffsetY = sheetOffsetY,
                    cache = cutoutPathCache,
                )
        ) {
            val screenW = constraints.maxWidth.toFloat()
            val screenH = constraints.maxHeight.toFloat()
            val snapshotAspect =
                (snapshot?.height?.toFloat() ?: screenH) / (snapshot?.width?.toFloat() ?: screenW)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (materialNone) Modifier
                        else Modifier.liquidGlassLayerBackdrop(
                            glassBackdrop,
                            // 采样层内容恒定 ⇒ 首帧录一次后每帧跳过重录；节点必须常驻，
                            // 一旦 detach 坐标会被置空，顶栏胶囊材质会消失。
                            recordKey = "customize-empty-backdrop"
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                PreviewCard(
                    snapshot = snapshot,
                    wallpaperBitmap = wallpaperBitmap,
                    combination = combinations.firstOrNull(),
                    isCurrentCombination = currentCombinationIndex == 0,
                    animDone = animDone,
                    interactive = !cardHidden && !isCutoutActive,
                    cardHidden = cardHidden,
                    cardScale = cardScale,
                    cutoutOffsetX = cutoutOffsetX,
                    cutoutOffsetY = cutoutOffsetY,
                    sheetOffsetY = sheetOffsetY,
                    screenWPx = screenW,
                    screenHPx = screenH,
                    screenCornerRadius = screenCornerRadius,
                    minCornerPx = minCornerPx,
                )

                // 手势层放在标题/按钮之前，避免拦截顶部与底部按钮的点击
                if (isCutoutActive && cardHidden && wallpaperBitmap != null) {
                    WallpaperGestureLayer(
                        wallpaperBitmap = wallpaperBitmap,
                        scale = wallpaperScale,
                        offset = wallpaperOffset,
                        minScale = minWallpaperScale,
                        onScaleChange = onWallpaperScaleChange,
                        onOffsetChange = onWallpaperOffsetChange,
                        screenWPx = screenW,
                        screenHPx = screenH,
                    )
                }

                CustomizeTopBar(
                    backdrop = glassBackdrop,
                    previewPage = previewPage,
                    onPreviewPageChange = onPreviewPageChange,
                    buttonAlpha = buttonAlpha,
                    buttonScale = buttonScale,
                    statusBarPadding = statusBarPadding,
                    onCancel = {
                        if (!isPageAnimating && !isCutoutAnimating) onDismiss()
                    },
                    onApply = {
                        if (!showApplyLoading) {
                            showApplyLoading = true
                            onApply()
                        }
                    },
                )

                if (showApplyLoading) {
                    ApplyLoadingOverlay()
                }

                CustomizeToolbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    visible = isCutoutActive && !isApplyingCustomize,
                    hasWallpaper = hasWallpaper,
                    toolAlpha = toolAlpha,
                    toolOffsetY = toolOffsetY,
                    toolBlur = toolBlur,
                    cardScale = cardScale,
                    cutoutOffsetY = cutoutOffsetY,
                    sheetOffsetY = sheetOffsetY,
                    screenWPx = screenW,
                    screenHPx = screenH,
                    snapshotAspect = snapshotAspect,
                    onWallpaper = { if (hasWallpaper) onClearWallpaper() else onPickWallpaper() },
                    onEffects = { showEffectSheet = true },
                    onCustomize = { showCustomizeSheet = true },
                )

                // 退出快照由宿主覆盖层统一放大淡出，本页只负责入场形变
                if (snapshot != null && !animDone) {
                    EnterSnapshotOverlay(
                        snapshot = snapshot,
                        screenCornerRadius = screenCornerRadius,
                        animProgress = animProgress,
                        cardScale = cardScale,
                        cutoutOffsetX = cutoutOffsetX,
                        cutoutOffsetY = cutoutOffsetY,
                        sheetOffsetY = sheetOffsetY,
                    )
                }

                CustomizeSheetHost(
                    isTablet = isTablet,
                    show = showEffectSheet,
                    title = SchedStr.effects(),
                    defaultContentHeight = defaultSheetHeight,
                    fallbackBackdrop = glassBackdrop,
                    onDismiss = { showEffectSheet = false },
                ) {
                    EffectSheetContent(
                        draft = draft,
                        hasWallpaper = hasWallpaper,
                        materialNone = materialNone,
                        themeMode = themeModeValue,
                        onThemeModeChange = { mode ->
                            themeModeValue = mode
                            onThemeModePreview(mode)
                        },
                        fallbackBackdrop = glassBackdrop,
                        dropdownColors = dropdownColors,
                    )
                }

                CustomizeSheetHost(
                    isTablet = isTablet,
                    show = showCustomizeSheet,
                    title = SchedStr.customize(),
                    defaultContentHeight = defaultSheetHeight,
                    fallbackBackdrop = glassBackdrop,
                    onDismiss = { showCustomizeSheet = false },
                ) {
                    CustomizeSheetContent(
                        draft = draft,
                        scheduleViewMode = scheduleViewMode,
                        onScheduleViewModeChange = onScheduleViewModeChange,
                        todayViewMode = todayViewMode,
                        onTodayViewModeChange = onTodayViewModeChange,
                        fallbackBackdrop = glassBackdrop,
                        dropdownColors = dropdownColors,
                    )
                }
            }
        }
    }
}

/** 遮罩层：整屏底色 + 按卡片几何挖出的连续圆角洞。 */
private fun Modifier.cutoutBackdrop(
    snapshot: ImageBitmap?,
    screenCornerRadius: ScreenCornerRadiiPx,
    minCornerPx: Float,
    cardScale: Animatable<Float, *>,
    cutoutProgress: Animatable<Float, *>,
    cutoutOffsetX: Animatable<Float, *>,
    cutoutOffsetY: Animatable<Float, *>,
    sheetOffsetY: Animatable<Float, *>,
    cache: CutoutPathCache,
): Modifier = drawBehind {
    val snapshotW = snapshot?.width?.toFloat() ?: size.width
    val snapshotH = snapshot?.height?.toFloat() ?: size.height
    val aspect = snapshotH / snapshotW
    val progress = scaleProgress(cardScale.value)

    val centerX = size.width / 2f + cutoutOffsetX.value * (1f - progress)
    val baseOffsetY = size.height * CARD_OFFSET_Y_RATIO + cutoutOffsetY.value + sheetOffsetY.value
    val centerY = size.height / 2f + baseOffsetY * (1f - progress)
    val holeW = size.width * cardScale.value
    val holeH = holeW * aspect
    val p = cutoutProgress.value

    if (p <= 0f) {
        drawRect(PageBackground)
    } else {
        val corners = screenCornerRadius.scaled(cardScale.value * p).coerceAtLeast(minCornerPx)
        val path = cache.obtain(size.width, size.height, holeW, holeH, corners)
        translate(centerX - holeW / 2f * p, centerY - holeH / 2f * p) {
            drawPath(path, PageBackground)
        }
    }
}

/** 预览卡片：入场结束后显示当前搭配的壁纸 / 快照。 */
@Composable
private fun PreviewCard(
    snapshot: ImageBitmap?,
    wallpaperBitmap: ImageBitmap?,
    combination: CombinationThumbnail?,
    isCurrentCombination: Boolean,
    animDone: Boolean,
    interactive: Boolean,
    cardHidden: Boolean,
    cardScale: Animatable<Float, *>,
    cutoutOffsetX: Animatable<Float, *>,
    cutoutOffsetY: Animatable<Float, *>,
    sheetOffsetY: Animatable<Float, *>,
    screenWPx: Float,
    screenHPx: Float,
    screenCornerRadius: ScreenCornerRadiiPx,
    minCornerPx: Float,
) {
    val density = LocalDensity.current
    val scale = cardScale.value
    val aspect = (snapshot?.height?.toFloat() ?: screenHPx) / (snapshot?.width?.toFloat() ?: screenWPx)
    val cardWidth = screenWPx * scale
    val cardHeight = cardWidth * aspect
    val progress = scaleProgress(scale)
    val offsetX = cutoutOffsetX.value * (1f - progress)
    val offsetY =
        (screenHPx * CARD_OFFSET_Y_RATIO + cutoutOffsetY.value + sheetOffsetY.value) * (1f - progress)

    Box(
        modifier = Modifier
            .width(with(density) { cardWidth.toDp() })
            .height(with(density) { cardHeight.toDp() })
            .offset(x = with(density) { offsetX.toDp() }, y = with(density) { offsetY.toDp() })
            // 与遮罩洞共用同一套圆角（含下限），避免卡片露直角
            .clip(
                screenCornerRadius.scaled(scale)
                    .coerceAtLeast(minCornerPx)
                    .toShape(density)
            )
            .graphicsLayer { alpha = if (cardHidden) 0f else 1f }
            .clickable(enabled = interactive, onClick = {})
    ) {
        // 刚选完壁纸时搭配快照为空，优先实时壁纸，避免旧快照盖住新图
        val preview = when {
            isCurrentCombination && wallpaperBitmap != null && combination?.snapshot == null ->
                wallpaperBitmap
            isCurrentCombination && snapshot != null && animDone && !cardHidden -> snapshot
            combination?.snapshot != null -> combination.snapshot
            combination?.wallpaper != null -> combination.wallpaper
            else -> null
        }
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CapsuleFill),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = SchedStr.combinationN(1),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 入场快照：从满屏缩到卡片轮廓，落地后交还真实内容。 */
@Composable
private fun EnterSnapshotOverlay(
    snapshot: ImageBitmap,
    screenCornerRadius: ScreenCornerRadiiPx,
    animProgress: Animatable<Float, *>,
    cardScale: Animatable<Float, *>,
    cutoutOffsetX: Animatable<Float, *>,
    cutoutOffsetY: Animatable<Float, *>,
    sheetOffsetY: Animatable<Float, *>,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = snapshot,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val aspect = snapshot.height.toFloat() / snapshot.width
                    val target = minOf(
                        cardScale.value,
                        cardScale.value * aspect * size.width / size.height
                    )
                    val scale = 1f + (target - 1f) * animProgress.value
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0.58f)
                    val progress = scaleProgress(cardScale.value)
                    translationX = cutoutOffsetX.value * (1f - progress)
                    translationY = (cutoutOffsetY.value + sheetOffsetY.value) * (1f - progress)
                    clip = true
                    shape = screenCornerRadius.toShape(this)
                },
            contentScale = ContentScale.Crop
        )
    }
}

/** 顶栏：取消 / 预览页切换 / 应用。 */
@Composable
private fun CustomizeTopBar(
    backdrop: LayerBackdrop,
    previewPage: Int,
    onPreviewPageChange: (Int) -> Unit,
    buttonAlpha: Animatable<Float, *>,
    buttonScale: Animatable<Float, *>,
    statusBarPadding: Dp,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val isDark = isAppDarkTheme()
    val glassOn = AppMaterialSettings.glassEffectsEnabled()
    val blurDp = AppMaterialSettings.glassBlurDp()
    val primaryColor = NexioTheme.colorScheme.primary
    val capsuleColor = CapsuleFill.copy(alpha = AppMaterialSettings.solidSurfaceAlpha(0.8f))

    val cancelStyle = AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = isDark,
        fill = CustomizeFill(CancelGlassFill),
        tone = BrightTone,
    )
    val applyStyle = AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = isDark,
        fill = CustomizeFill(primaryColor),
        tone = BrightTone,
    )
    val capsuleShape = remember { { ContinuousCapsule() } }

    val cancelEffects: GlassEffectScope.() -> Unit = remember(blurDp, glassOn, cancelStyle) {
        {
            if (glassOn) {
                backdropBlur(blurDp.dp.toPx())
                runtimeShaderEffect(
                    key = LIVE_CANCEL_GLASS_KEY,
                    shaderString = SOFT_GLASS_LIVE_SHADER,
                    uniformShaderName = "content",
                ) {
                    applySoftGlass(style = cancelStyle, width = size.width, height = size.height)
                }
            }
        }
    }
    val applyEffects: GlassEffectScope.() -> Unit = remember(blurDp, glassOn, applyStyle) {
        {
            if (glassOn) {
                backdropBlur(blurDp.dp.toPx())
                runtimeShaderEffect(
                    key = LIVE_APPLY_GLASS_KEY,
                    shaderString = SOFT_GLASS_LIVE_SHADER,
                    uniformShaderName = "content",
                ) {
                    applySoftGlass(style = applyStyle, width = size.width, height = size.height)
                }
            }
        }
    }

    val cancelHighlight = remember(scope) {
        InteractiveHighlight(animationScope = scope, isDark = { GlassEnvironment.isDark })
    }
    val applyHighlight = remember(scope) {
        InteractiveHighlight(animationScope = scope, isDark = { GlassEnvironment.isDark })
    }
    val cancelSurface: DrawScope.() -> Unit = remember(capsuleColor, cancelHighlight) {
        {
            drawRect(capsuleColor)
            drawRect(Color.Black.copy(alpha = 0.03f * cancelHighlight.pressProgress))
        }
    }
    val applySurface: DrawScope.() -> Unit = remember(primaryColor, applyHighlight) {
        {
            drawRect(primaryColor.copy(alpha = AppMaterialSettings.solidSurfaceAlpha(0.8f)))
            drawRect(Color.Black.copy(alpha = 0.03f * applyHighlight.pressProgress))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = buttonAlpha.value
                scaleX = buttonScale.value
                scaleY = buttonScale.value
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(top = statusBarPadding + 16.dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCapsuleButton(
                text = CommonStr.cancel(),
                backdrop = backdrop,
                shape = capsuleShape,
                effects = cancelEffects,
                onDrawSurface = if (glassOn) null else cancelSurface,
                highlight = cancelHighlight,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onCancel()
                }
            )

            PreviewPageSwitch(
                previewPage = previewPage,
                onPreviewPageChange = onPreviewPageChange,
                containerColor = capsuleColor,
            )

            GlassCapsuleButton(
                text = SchedStr.applyChanges(),
                backdrop = backdrop,
                shape = capsuleShape,
                effects = applyEffects,
                onDrawSurface = if (glassOn) null else applySurface,
                highlight = applyHighlight,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onApply()
                }
            )
        }
    }
}

@Composable
private fun GlassCapsuleButton(
    text: String,
    backdrop: LayerBackdrop,
    shape: () -> Shape,
    effects: GlassEffectScope.() -> Unit,
    onDrawSurface: (DrawScope.() -> Unit)?,
    highlight: InteractiveHighlight,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(84.dp)
            .height(40.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = shape,
                effects = effects,
                layerBlock = {
                    val scale = 1f + 2.dp.toPx() / 40.dp.toPx() * highlight.pressProgress
                    scaleX = scale
                    scaleY = scale
                    val offset = highlight.offset
                    translationX = size.minDimension * 0.05f * offset.x / size.maxDimension
                    translationY = size.minDimension * 0.05f * offset.y / size.maxDimension
                },
                // 柔光自绘玻璃色；无材质档必须保留实色底
                onDrawSurface = onDrawSurface,
            )
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .then(highlight.modifier)
            .then(highlight.gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** 中段「今日 / 课程表」切换胶囊，不参与玻璃采样。 */
@Composable
private fun PreviewPageSwitch(
    previewPage: Int,
    onPreviewPageChange: (Int) -> Unit,
    containerColor: Color,
) {
    val haptics = LocalHapticFeedback.current
    val resolved = previewPage.coerceIn(0, 1)
    val thumbProgress = remember { Animatable(if (resolved == 1) 1f else 0f) }
    LaunchedEffect(resolved) {
        thumbProgress.animateTo(
            targetValue = if (resolved == 1) 1f else 0f,
            animationSpec = tween(
                durationMillis = 280,
                easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
            )
        )
    }

    Box(
        modifier = Modifier
            .width(120.dp)
            .height(40.dp)
            .clip(ContinuousCapsule())
            .background(containerColor)
            .padding(3.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .graphicsLayer { translationX = thumbProgress.value * size.width }
                    .clip(ContinuousCapsule())
                    .background(Color.White.copy(alpha = 0.1f))
            )
            Row(modifier = Modifier.fillMaxSize()) {
                PreviewPageTab(
                    icon = NexioIcons.Album,
                    description = CommonStr.today(),
                    selected = resolved == 0,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        if (resolved != 0) onPreviewPageChange(0)
                    }
                )
                PreviewPageTab(
                    icon = NexioIcons.Months,
                    description = SchedStr.schedule(),
                    selected = resolved == 1,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        if (resolved != 1) onPreviewPageChange(1)
                    }
                )
            }
        }
    }
}

@Composable
private fun PreviewPageTab(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.75f)
        )
    }
}

/** 底部工具栏：壁纸 / 效果 / 自定义，位置随开洞底部居中。 */
@Composable
private fun CustomizeToolbar(
    modifier: Modifier,
    visible: Boolean,
    hasWallpaper: Boolean,
    toolAlpha: Animatable<Float, *>,
    toolOffsetY: Animatable<Float, *>,
    toolBlur: Animatable<Float, *>,
    cardScale: Animatable<Float, *>,
    cutoutOffsetY: Animatable<Float, *>,
    sheetOffsetY: Animatable<Float, *>,
    screenWPx: Float,
    screenHPx: Float,
    snapshotAspect: Float,
    onWallpaper: () -> Unit,
    onEffects: () -> Unit,
    onCustomize: () -> Unit,
) {
    val density = LocalDensity.current
    // 工具栏实测高度（px）：用它将「洞底→屏底」的中点换算为底边距，避免写死尺寸
    var rowHeightPx by remember { mutableFloatStateOf(0f) }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(350, easing = CustomizePageEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(250, easing = CustomizePageEasing)
        )
    ) {
        val progress = scaleProgress(cardScale.value)
        val cardHeightPx = screenWPx * cardScale.value * snapshotAspect
        val centerY = screenHPx / 2f +
            (screenHPx * CARD_OFFSET_Y_RATIO + cutoutOffsetY.value + sheetOffsetY.value) * (1f - progress)
        val gapToBottom = (screenHPx - (centerY + cardHeightPx / 2f)).coerceAtLeast(0f)
        val bottomPadding = with(density) {
            val padding = ((gapToBottom / 2f - rowHeightPx / 2f).toDp() + 8.dp) * (2f / 3f)
            if (padding < 0.dp) 0.dp else padding
        }
        val blurModifier =
            if (toolBlur.value > 0.01f) Modifier.blur(toolBlur.value.dp) else Modifier

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // onSizeChanged 必须在 padding 之内，否则量到「内容 + padding」形成自指
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(bottom = bottomPadding)
                .onSizeChanged { rowHeightPx = it.height.toFloat() }
                .graphicsLayer {
                    alpha = toolAlpha.value
                    translationY = toolOffsetY.value
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolCircleButton(
                icon = if (hasWallpaper) NexioIcons.Normal.Close else NexioIcons.Image,
                description = if (hasWallpaper) SchedStr.clearWallpaper() else SchedStr.wallpaper(),
                color = if (hasWallpaper) Color(0xFFB71C1C) else CapsuleFill,
                iconSize = 26.dp,
                blurModifier = blurModifier,
                onClick = onWallpaper,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .padding(7.dp)
                    .then(blurModifier)
            ) {
                VerticalDivider(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(ContinuousCapsule()),
                    thickness = 2.dp,
                    color = CapsuleFill
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            ToolCircleButton(
                icon = NexioIcons.Background,
                description = SchedStr.effects(),
                color = CapsuleFill,
                iconSize = 28.dp,
                blurModifier = blurModifier,
                onClick = onEffects,
            )
            Spacer(modifier = Modifier.width(10.dp))
            ToolCircleButton(
                icon = NexioIcons.GridView,
                description = SchedStr.customize(),
                color = CapsuleFill,
                iconSize = 24.dp,
                blurModifier = blurModifier,
                onClick = onCustomize,
            )
        }
    }
}

@Composable
private fun ToolCircleButton(
    icon: ImageVector,
    description: String,
    color: Color,
    iconSize: Dp,
    blurModifier: Modifier,
    onClick: () -> Unit,
) {
    // 外层 padding 承载模糊溢出，内层圆裁剪可点击
    Box(modifier = Modifier.padding(7.dp).then(blurModifier)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(ContinuousCapsule())
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(iconSize),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ApplyLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfiniteProgressIndicator(
                size = 24.dp,
                strokeWidth = 2.2.dp,
                orbitingDotSize = 2.7.dp,
            )
            Text(
                text = SchedStr.applying(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** 壁纸缩放/拖动层：低于最小缩放时带阻力，松手弹回铺满状态。 */
@Composable
private fun WallpaperGestureLayer(
    wallpaperBitmap: ImageBitmap,
    scale: Float,
    offset: Offset,
    minScale: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit,
    screenWPx: Float,
    screenHPx: Float,
) {
    val latestScale by rememberUpdatedState(scale)
    val latestOffset by rememberUpdatedState(offset)
    val latestMinScale by rememberUpdatedState(minScale)
    val latestOnScale by rememberUpdatedState(onScaleChange)
    val latestOnOffset by rememberUpdatedState(onOffsetChange)
    val latestBitmap by rememberUpdatedState(wallpaperBitmap)

    var bounceBackTrigger by remember { mutableIntStateOf(0) }
    var gestureEndScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(bounceBackTrigger) {
        if (bounceBackTrigger > 0 && gestureEndScale < latestMinScale) {
            animate(
                initialValue = gestureEndScale,
                targetValue = latestMinScale,
                animationSpec = tween(
                    durationMillis = 350,
                    easing = CubicBezierEasing(0.34f, 1.1f, 0.3f, 1f)
                )
            ) { value, _ ->
                latestOnScale(value)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var gestureScale = latestScale
                    var displayScale = gestureScale
                    do {
                        val event = awaitPointerEvent()
                        gestureScale *= event.calculateZoom()
                        val pan = event.calculatePan()
                        displayScale = if (gestureScale < latestMinScale) {
                            // 低于铺满下限后线性加阻
                            latestMinScale + (gestureScale - latestMinScale) * 0.3f
                        } else {
                            gestureScale
                        }
                        val bitmap = latestBitmap
                        if (bitmap.width > 0 && bitmap.height > 0) {
                            val fit = minOf(screenWPx / bitmap.width, screenHPx / bitmap.height)
                            val scaledW = bitmap.width * fit * displayScale
                            val scaledH = bitmap.height * fit * displayScale
                            val maxX = ((scaledW - screenWPx) / 2f).coerceAtLeast(0f)
                            val maxY = ((scaledH - screenHPx) / 2f).coerceAtLeast(0f)
                            val dragged = latestOffset + pan
                            latestOnOffset(
                                Offset(
                                    dragged.x.coerceIn(-maxX, maxX),
                                    dragged.y.coerceIn(-maxY, maxY)
                                )
                            )
                        } else {
                            latestOnOffset(latestOffset + pan)
                        }
                        latestOnScale(displayScale)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    gestureEndScale = displayScale
                    bounceBackTrigger++
                }
            }
    )
}

/**
 * 搭配缩略图：宿主把运行期 `Combination`（含平台 Bitmap）映射为两张 [ImageBitmap]。
 */
data class CombinationThumbnail(
    /** 该搭配的快照；null 表示尚未生成（回落 [wallpaper]） */
    val snapshot: ImageBitmap?,
    /** 该搭配的壁纸 */
    val wallpaper: ImageBitmap?,
)
