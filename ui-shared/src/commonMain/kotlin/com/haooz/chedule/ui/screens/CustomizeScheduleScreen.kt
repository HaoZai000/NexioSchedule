/** 自定义课表页面 - 课表外观选择 */
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.appearance.AppearanceConfig
import com.haooz.chedule.data.appearance.AppearanceStore
import com.haooz.chedule.data.appearance.CardContentAlignment
import com.haooz.chedule.data.appearance.CardRefractionLevel
import com.haooz.chedule.data.appearance.CardTextColor
import com.haooz.chedule.data.appearance.ThemeMode
import com.haooz.chedule.data.util.formatOneDecimal
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.activities.classschedule.ScheduleViewMode
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.effects.motion.CustomizePageEasing
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.addScreenCornerRect
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberDefaultEdgeLight
import com.haooz.chedule.ui.utils.screenHeightDp
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.ui.utils.toShape
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.edgelight.edgeLight
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.NativeTextField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.overlay.BlurBottomSheetTablet
import top.yukonga.miuix.kmp.overlay.LocalSheetContentBackdrop
import top.yukonga.miuix.kmp.overlay.LocalSheetTopBarMaterial
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.NavigationBackHandlerFor
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 挖洞遮罩路径缓存：尺寸/圆角不变时只 translate，避免每帧重建 squircle */
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
        corners: ScreenCornerRadiiPx
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
    /** 默认主题实时预览：只改内存中的临时档位，点「应用」后才写入 SharedPreferences */
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
    /** 开洞中心回传（屏幕比例）：x 供宿主对齐横向缩放原点，y 同原有的竖向原点 */
    onCutoutCenterChange: (xRatio: Float, yRatio: Float) -> Unit = { _, _ -> },
    /**
     * 右侧是否有一条原生 chrome 占位（折叠屏 iPhone 竖排底栏，约 84pt）。
     * 这类机型内容实际中心比屏幕中心偏左，开洞要整体让开 —— 见下面的两个微调旋钮。
     */
    rightChromeReserved: Boolean = false,
    // 弹窗开合时开洞与主界面共用的位移驱动器：两边直接读同一 Animatable.value，保证同帧同步
    sheetOffsetShared: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> = Animatable(
        0f
    ),
    pendingEnterCutout: Boolean = false,
    onCutoutEntered: () -> Unit = {},
    // 外观配置：编辑器以 AppearanceConfig 整体读写，避免逐字段回调散点
    appearance: AppearanceConfig = AppearanceConfig(),
    onAppearanceChange: (AppearanceConfig) -> Unit = {},
    /** 课表页当前呈现方式（周网格 / 时间轴）。外观页里改它，回主界面即生效。 */
    scheduleViewMode: ScheduleViewMode = ScheduleViewMode.WEEK,
    onScheduleViewModeChange: (ScheduleViewMode) -> Unit = {},
    hasWallpaper: Boolean = false,
    /** 预览页：0=今日，1=课程表；椭圆滑块与取消/应用同一行 */
    previewPage: Int = 0,
    onPreviewPageChange: (Int) -> Unit = {},
    // ── 平台注入 ──────────────────────────────────────────────────────────
    /** 当前「默认主题」档位（Android = `ScheduleAppearance.getThemeMode()`） */
    themeMode: () -> ThemeMode = { ThemeMode.FOLLOW_WALLPAPER },
    /** 落盘「默认主题」（Android = `ScheduleAppearance.setThemeMode`） */
    onThemeModePersist: (ThemeMode) -> Unit = {},
) {
    val densityObj = LocalDensity.current
    // ── 开洞微调旋钮 ────────────────────────────────────────────────────────
    // 竖向：正值=下移；横向：负值=左移
    val cutoutOffsetDp = if (rightChromeReserved) (-16).dp else (-10).dp
    val cutoutOffsetXDp = if (rightChromeReserved) (-56).dp else 0.dp
    // 洞/卡片圆角下限：
    val cutoutMinCornerPx = with(densityObj) { 20.dp.toPx() }
    val cutoutOffsetTargetPx = with(densityObj) { cutoutOffsetDp.toPx() }
    val cutoutOffsetXTargetPx = with(densityObj) { cutoutOffsetXDp.toPx() }
    val sheetOffsetTargetPx = with(densityObj) { 90.dp.toPx() }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isTablet = isTabletWidth()
    val screenHPx = with(densityObj) { screenHeightDp().dp.toPx() }
    val screenWPx = with(densityObj) { screenWidthDp().dp.toPx() }

    // 壁纸最小缩放：填满短边，避免缩放过小时露出底部背景
    val minWallpaperScale = remember(wallpaperBitmap, screenWPx, screenHPx) {
        if (wallpaperBitmap != null && wallpaperBitmap.width > 0 && wallpaperBitmap.height > 0) {
            val fitScale =
                minOf(screenWPx / wallpaperBitmap.width, screenHPx / wallpaperBitmap.height)
            val coverScale =
                maxOf(screenWPx / wallpaperBitmap.width, screenHPx / wallpaperBitmap.height)
            if (fitScale > 0f) coverScale / fitScale else 1f
        } else 1f
    }


    val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop {
        drawRect(Color.Transparent)
    }
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )

    val primaryColor = MiuixTheme.colorScheme.primary
    val exitContainerColor = Color(0xFF363636)
        .copy(com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(0.8f))
    val exitIconColor = Color.White

    var showApplyLoading by remember { mutableStateOf(false) }
    LaunchedEffect(isApplyingCustomize) {
        if (isApplyingCustomize) {
            showApplyLoading = false
        }
    }

    var showEffectSheet by remember { mutableStateOf(false) }
    var showCustomizeSheet by remember { mutableStateOf(false) }
    // 弹窗 backdrop 用 LocalSheetContentBackdrop 读取，不提升成本页 State，否则进入动画头几帧整页重跑组合
    var sheetResetKey by remember { mutableIntStateOf(0) }

    var effectValue by remember(currentCombinationIndex, sheetResetKey) {
        mutableFloatStateOf(
            appearance.cardBlurRadius
        )
    }
    var cardAlphaValue by remember(currentCombinationIndex, sheetResetKey) {
        mutableFloatStateOf(
            appearance.cardAlpha
        )
    }
    var cardSurfaceAlphaValue by remember(currentCombinationIndex, sheetResetKey) {
        mutableFloatStateOf(appearance.cardSurfaceAlpha)
    }
    var wallpaperBrightnessValue by remember(
        currentCombinationIndex,
        sheetResetKey
    ) { mutableFloatStateOf(appearance.wallpaperBrightness) }
    LaunchedEffect(appearance.cardBlurRadius) { effectValue = appearance.cardBlurRadius }

    var cardHeightValue by remember(currentCombinationIndex, sheetResetKey) {
        mutableFloatStateOf(
            appearance.cardHeight
        )
    }
    var cardCornerRadiusValue by remember(
        currentCombinationIndex,
        sheetResetKey
    ) { mutableFloatStateOf(appearance.cardCornerRadius) }
    var showBreakDividersValue by remember(currentCombinationIndex, sheetResetKey) {
        mutableStateOf(
            appearance.showBreakDividers
        )
    }
    var cardContentAlignmentValue by remember(
        currentCombinationIndex,
        sheetResetKey
    ) { mutableStateOf(appearance.cardContentAlignment) }

    var cardTextColorValue by remember(
        currentCombinationIndex,
        sheetResetKey
    ) { mutableStateOf(appearance.cardTextColor) }
    var cardTextScaleValue by remember(
        currentCombinationIndex,
        sheetResetKey
    ) { mutableStateOf(appearance.cardTextScale) }
    var showClassroomValue by remember(currentCombinationIndex, sheetResetKey) {
        mutableStateOf(appearance.showClassroom)
    }
    var showTeacherValue by remember(currentCombinationIndex, sheetResetKey) {
        mutableStateOf(appearance.showTeacher)
    }
    var cardRefractionValue by remember(
        currentCombinationIndex,
        sheetResetKey
    ) { mutableStateOf(appearance.cardRefraction) }
    LaunchedEffect(appearance.cardRefraction) { cardRefractionValue = appearance.cardRefraction }
    var wallpaperBlurValue by remember(
        currentCombinationIndex,
        sheetResetKey
    ) { mutableStateOf(appearance.wallpaperBlur) }

    var themeModeValue by remember(currentCombinationIndex, sheetResetKey) {
        // 独立偏好 key，仅影响今日/课表页，不污染全局 theme_mode
        mutableStateOf(themeMode())
    }

    // 无壁纸时档位无意义，清壁纸即复位为跟随壁纸
    LaunchedEffect(hasWallpaper) {
        if (!hasWallpaper && themeModeValue != ThemeMode.FOLLOW_WALLPAPER) {
            themeModeValue = ThemeMode.FOLLOW_WALLPAPER
            onThemeModePersist(ThemeMode.FOLLOW_WALLPAPER)
        }
    }

    // 无材质档：卡片模糊/折射设置锁死，写回时保留存档原值——
    // 屏内这两个值来自钳制后的 appearance（恒 0/OFF），直接写回会把用户的存档清掉
    val materialNone = com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()

    fun buildAppearance() = AppearanceConfig(
        cardBlurRadius = if (materialNone) AppearanceStore.getCardBlur() else effectValue,
        cardAlpha = cardAlphaValue,
        cardSurfaceAlpha = cardSurfaceAlphaValue,
        cardHeight = cardHeightValue,
        cardCornerRadius = cardCornerRadiusValue,
        wallpaperBrightness = wallpaperBrightnessValue,
        showBreakDividers = showBreakDividersValue,
        cardContentAlignment = cardContentAlignmentValue,
        cardTextColor = cardTextColorValue,
        cardTextScale = cardTextScaleValue,
        showClassroom = showClassroomValue,
        showTeacher = showTeacherValue,
        cardRefraction = if (materialNone) AppearanceStore.getCardRefraction() else cardRefractionValue,
        wallpaperBlur = wallpaperBlurValue
    )
    LaunchedEffect(effectValue, cardAlphaValue, cardSurfaceAlphaValue) { onAppearanceChange(buildAppearance()) }
    LaunchedEffect(wallpaperBrightnessValue) { onAppearanceChange(buildAppearance()) }
    LaunchedEffect(cardHeightValue, cardCornerRadiusValue) {
        delay(16.milliseconds)
        onAppearanceChange(buildAppearance())
    }
    LaunchedEffect(showBreakDividersValue) {
        onAppearanceChange(buildAppearance())
    }
    LaunchedEffect(cardContentAlignmentValue) {
        onAppearanceChange(buildAppearance())
    }
    LaunchedEffect(cardTextColorValue) { onAppearanceChange(buildAppearance()) }
    LaunchedEffect(cardTextScaleValue) { onAppearanceChange(buildAppearance()) }
    LaunchedEffect(showClassroomValue) { onAppearanceChange(buildAppearance()) }
    LaunchedEffect(showTeacherValue) { onAppearanceChange(buildAppearance()) }
    LaunchedEffect(cardRefractionValue) { onAppearanceChange(buildAppearance()) }
    LaunchedEffect(wallpaperBlurValue) { onAppearanceChange(buildAppearance()) }

    val customizeButtonAlpha = remember { Animatable(1f) }

    val pageCount = combinations.size.coerceAtLeast(1)
    val initialPage = 0
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })

    val animProgress = remember { Animatable(0f) }
    var animDone by remember { mutableStateOf(false) }
    var isPageAnimating by remember { mutableStateOf(true) }
    val buttonScaleAnim = remember { Animatable(1.5f) }
    val pagerSpacing = remember { Animatable(-140f) }
    val exitProgress = remember { Animatable(0f) }

    var isCutoutActive by remember { mutableStateOf(false) }
    var isCutoutAnimating by remember { mutableStateOf(false) }
    var cardHidden by remember { mutableStateOf(false) }
    val cutoutProgress = remember { Animatable(0f) }
    val cardScaleAnim = remember { Animatable(0.65f) }
    val buttonAlphaAnim = remember { Animatable(1f) }
    val cutoutOffsetY = remember { Animatable(0f) }
    val cutoutOffsetX = remember { Animatable(0f) }
    // 与 MainActivity 共享同一 Animatable，弹窗开合时两边同帧同步位移
    val sheetOffsetY = sheetOffsetShared
    val cutoutEnterProgress = remember { Animatable(0f) }
    val cutoutPathCache = remember { CutoutPathCache() }
    val toolOffsetTargetPx = with(densityObj) { 80.dp.toPx() }
    val toolAlphaAnim = remember { Animatable(0f) }
    val toolOffsetYAnim = remember { Animatable(toolOffsetTargetPx) }
    val toolBlurAnim = remember { Animatable(8f) }
    // 工具栏实测高度（px）。用它把「洞底→屏底」的中点换算成 padding(bottom)，
    // 不写死 56dp，按钮尺寸变化时也不会失准。首帧为 0，下一帧由 onSizeChanged 修正。
    var toolRowHeightPx by remember { mutableFloatStateOf(0f) }

    // 新建搭配后自动进入编辑模式
    LaunchedEffect(pendingEnterCutout) {
        if (pendingEnterCutout && !isCutoutActive) {
            isCutoutActive = true
            onCutoutEntered()
        }
    }

    // ── 入场时间轴 ───────────────────────────────────────────────────────
    // 这些动画在封面快照盖满全屏时就开始跑；硬约束：全部元素须在
    // CUSTOMIZE_COVER_WINDOW_MILLIS（封面撤除时刻）内落地，否则封面没了按钮/工具栏还在飞，
    // 观感脱节。每个 ENTER_* 的 (delay + duration) 都必须 ≤ 该窗口，改动前先核对。
    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                animProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(ENTER_CARD_MILLIS, easing = CustomizePageEasing)
                )
                animDone = true
                isPageAnimating = false
            }
            launch {
                pagerSpacing.animateTo(
                    -10f,
                    tween(ENTER_PAGER_SPACING_MILLIS, easing = CustomizePageEasing)
                )
            }
            launch {
                delay(ENTER_BUTTON_DELAY_MILLIS.toLong())
                buttonScaleAnim.animateTo(
                    1f,
                    tween(ENTER_BUTTON_MILLIS, easing = CustomizePageEasing)
                )
            }
            launch {
                delay(ENTER_TOOL_DELAY_MILLIS.toLong())
                coroutineScope {
                    launch {
                        toolAlphaAnim.animateTo(
                            1f,
                            tween(ENTER_TOOL_MILLIS, easing = CustomizePageEasing)
                        )
                    }
                    launch {
                        toolOffsetYAnim.animateTo(
                            0f,
                            tween(ENTER_TOOL_MILLIS, easing = CustomizePageEasing)
                        )
                    }
                    launch {
                        toolBlurAnim.animateTo(
                            0f,
                            tween(ENTER_TOOL_MILLIS, easing = CustomizePageEasing)
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            exitProgress.snapTo(0f)
            coroutineScope {
                launch {
                    exitProgress.animateTo(
                        1f,
                        tween(500, easing = CustomizePageEasing)
                    )
                }
                launch {
                    buttonScaleAnim.animateTo(
                        1.5f,
                        tween(500, easing = CustomizePageEasing)
                    )
                }
                launch {
                    coroutineScope {
                        launch {
                            toolAlphaAnim.animateTo(
                                0f,
                                tween(500, easing = CustomizePageEasing)
                            )
                        }
                        launch {
                            toolOffsetYAnim.animateTo(
                                toolOffsetTargetPx,
                                tween(500, easing = CustomizePageEasing)
                            )
                        }
                        launch {
                            toolBlurAnim.animateTo(
                                8f,
                                tween(500, easing = CustomizePageEasing)
                            )
                        }
                    }
                }
                // 取消路径：开洞一并放大到全屏（应用路径由 isApplying 单独处理）
                if (!isApplying) {
                    launch {
                        cardScaleAnim.animateTo(
                            1f,
                            tween(500, easing = CustomizePageEasing)
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(isCutoutActive) {
        if (isCutoutActive) {
            isCutoutAnimating = true
            coroutineScope {
                launch {
                    cardScaleAnim.animateTo(
                        0.75f,
                        tween(400, easing = CustomizePageEasing)
                    )
                }
                launch { cutoutProgress.snapTo(1f) }
                launch { customizeButtonAlpha.animateTo(0f, tween(250)) }
                launch {
                    cutoutOffsetY.animateTo(
                        cutoutOffsetTargetPx,
                        tween(400, easing = CustomizePageEasing)
                    )
                }
                launch {
                    cutoutOffsetX.animateTo(
                        cutoutOffsetXTargetPx,
                        tween(400, easing = CustomizePageEasing)
                    )
                }
                launch {
                    cutoutEnterProgress.animateTo(
                        1f,
                        tween(400, easing = CustomizePageEasing)
                    )
                }
            }
            cardHidden = true
            isCutoutAnimating = false
        } else if (animDone) {
            cardHidden = false
            coroutineScope {
                launch {
                    cardScaleAnim.animateTo(
                        0.65f,
                        tween(400, easing = CustomizePageEasing)
                    )
                }
                launch { cutoutProgress.snapTo(0f) }
                launch { customizeButtonAlpha.animateTo(1f, tween(250)) }
                launch { buttonAlphaAnim.animateTo(1f, tween(250)) }
                launch {
                    cutoutOffsetY.animateTo(
                        0f,
                        tween(400, easing = CustomizePageEasing)
                    )
                }
                launch {
                    cutoutOffsetX.animateTo(
                        0f,
                        tween(400, easing = CustomizePageEasing)
                    )
                }
                launch {
                    cutoutEnterProgress.animateTo(
                        0f,
                        tween(400, easing = CustomizePageEasing)
                    )
                }
                launch { pagerState.animateScrollToPage(currentCombinationIndex) }
            }
        }
    }

    // 应用动画：裁剪区域跟随 cardScaleAnim 放大进程
    LaunchedEffect(isApplying) {
        if (isApplying) {
            coroutineScope {
                launch {
                    cardScaleAnim.animateTo(
                        1f,
                        tween(500, easing = CustomizePageEasing)
                    )
                }
                launch { buttonAlphaAnim.animateTo(0f, tween(250)) }
            }
        }
    }

    // snapshotFlow 订阅，避免把 Animatable.value 当 LaunchedEffect key 导致重启
    val latestScreenHPx by rememberUpdatedState(screenHPx)
    val latestScreenWPx by rememberUpdatedState(screenWPx)
    val latestOnCutoutCenterChange by rememberUpdatedState(onCutoutCenterChange)
    LaunchedEffect(Unit) {
        snapshotFlow { cutoutOffsetX.value to cutoutOffsetY.value }.collect { (offsetX, offsetY) ->
            // 与主内容缩放同源：1 - prog == (1 - scale) / 0.35，故原点比值 = 0.5 + o / (0.35 * 屏边)
            latestOnCutoutCenterChange(
                0.5f + offsetX / (latestScreenWPx * 0.35f),
                0.58f + offsetY / (latestScreenHPx * 0.35f)
            )
        }
    }

    val anySheetOpen = showEffectSheet || showCustomizeSheet
    LaunchedEffect(anySheetOpen) {
        if (anySheetOpen) {
            coroutineScope {
                launch { buttonAlphaAnim.animateTo(0f, tween(250)) }
                launch {
                    sheetOffsetY.animateTo(
                        -sheetOffsetTargetPx,
                        tween(400, easing = CustomizePageEasing)
                    )
                }
            }
        } else {
            coroutineScope {
                launch { buttonAlphaAnim.animateTo(1f, tween(250)) }
                launch {
                    sheetOffsetY.animateTo(
                        0f,
                        tween(350, easing = CustomizePageEasing)
                    )
                }
            }
        }
    }

    NavigationBackHandlerFor(
        enabled = true,
        onBackCompleted = {
            when {
                isPageAnimating -> { /* 页面进入动画中，不响应 */
                }

                isCutoutAnimating -> { /* 编辑模式动画中，不响应 */
                }

                else -> onDismiss()
            }
        },
        onBackCancelled = {},
    )

    // 动画完成后恒为 1f，避免无谓重组
    val enterValue = if (animDone) 1f else animProgress.value
    val buttonScale = buttonScaleAnim.value

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val snapshotW = snapshot?.width?.toFloat() ?: size.width
                    val snapshotH = snapshot?.height?.toFloat() ?: size.height
                    val aspect = snapshotH / snapshotW
                    val scaleProg = ((cardScaleAnim.value - 0.65f) / (1f - 0.65f)).coerceIn(0f, 1f)
                    // 横向微调与竖向同一套「按缩放进度淡出」的算法：落地时归零，洞整体回屏幕中心
                    val cardCenterX = size.width / 2f + cutoutOffsetX.value * (1f - scaleProg)
                    val baseOffsetY =
                        size.height * 0.028f + cutoutOffsetY.value + sheetOffsetY.value
                    val cardCenterY = size.height / 2f + baseOffsetY * (1f - scaleProg)
                    val animW = size.width * cardScaleAnim.value
                    val animH = animW * aspect
                    val p = cutoutProgress.value
                    val cutoutCorners =
                        screenCornerRadius.scaled(cardScaleAnim.value * p)
                            .coerceAtLeast(cutoutMinCornerPx)

                    val left = cardCenterX + ((cardCenterX - animW / 2f) - cardCenterX) * p
                    val top = cardCenterY + ((cardCenterY - animH / 2f) - cardCenterY) * p

                    if (p <= 0f) {
                        drawRect(color = Color(0xFF1A1A1A))
                    } else {
                        // 形状不变、仅位置变时走缓存 translate，squircle 不重建
                        val cached = cutoutPathCache.obtain(
                            screenW = size.width,
                            screenH = size.height,
                            holeW = animW,
                            holeH = animH,
                            corners = cutoutCorners
                        )
                        translate(left, top) {
                            drawPath(cached, color = Color(0xFF1A1A1A))
                        }
                    }
                }
        ) {
            val screenW = constraints.maxWidth.toFloat()
            val screenH = constraints.maxHeight.toFloat()

            val cardWidthPx = screenW * cardScaleAnim.value
            val snapshotWidth = snapshot?.width?.toFloat() ?: screenW
            val snapshotHeight = snapshot?.height?.toFloat() ?: screenH
            val snapshotAspect = snapshotHeight / snapshotWidth
            val cardHeightPx = cardWidthPx * snapshotAspect
            val cardWidthDp = with(densityObj) { cardWidthPx.toDp() }
            val cardHeightDp = with(densityObj) { cardHeightPx.toDp() }

            val cutoutScaleProg = ((cardScaleAnim.value - 0.65f) / (1f - 0.65f)).coerceIn(0f, 1f)
            val cutoutBaseOffsetY = screenH * 0.028f +
                cutoutOffsetY.value + sheetOffsetY.value
            val cutoutCenterY = screenH / 2f + cutoutBaseOffsetY * (1f - cutoutScaleProg)
            val cutoutBottom = cutoutCenterY + cardHeightPx / 2f
            val cutoutToBottomGap = (screenH - cutoutBottom).coerceAtLeast(0f)
            val toolNudgeDp = 8.dp
            val toolBottomPadding = with(densityObj) {
                // ⚠ 末尾那个 2/3 是还原静止位置用的，不是随手取的系数：旧式（onSizeChanged
                // 挂在 padding 外，量到含 padding 的高度）是自指方程 P = (G - H - P)/2 + 8dp，
                // 其唯一解正好 = 本式 2/3；修掉振荡后不补它，工具栏会停在公式原值（约 116px 而非 77px）。
                val raw = (cutoutToBottomGap / 2f - toolRowHeightPx / 2f).toDp() + toolNudgeDp
                val restored = raw * (2f / 3f)
                if (restored < 0.dp) 0.dp else restored
            }

            val targetScaleX = cardWidthPx / screenW
            val targetScaleY = cardHeightPx / screenH
            val targetScale = minOf(targetScaleX, targetScaleY)

            val currentScale = 1f + (targetScale - 1f) * enterValue
            // ⚠ 必须与卡片 cardOffsetY 的位移项逐项一致：`0.028H` 已由下面
            // TransformOrigin(0.5f, 0.58f) 的缩放原点承担（0.028/0.35 == 0.08），但
            // `cutoutOffsetY` / `sheetOffsetY` 原点管不着；留 0f 会在 animDone 交接时让卡片
            // 比覆盖层高 |o|*(1-prog)（落位 o=-10dp → 约 7dp），观感是开洞区域底部上跳一下。
            val currentTranslationY =
                (cutoutOffsetY.value + sheetOffsetY.value) * (1f - cutoutScaleProg)
            // 横向同理（弹窗无横向让位，故不含 sheetOffset）
            val currentTranslationX = cutoutOffsetX.value * (1f - cutoutScaleProg)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        // 弹窗打开时不录 liquidGlassBackdrop，只依赖 anySheetOpen 布尔，避免 backdrop 就绪引发结构变更重组；
                        // 无材质档玻璃全关，录屏层不挂
                        if (anySheetOpen ||
                            com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()
                        ) {
                            Modifier
                        } else {
                            Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                        },
                    ),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (cardHidden) 0f else 1f },
                    beyondViewportPageCount = 1,
                    pageSpacing = pagerSpacing.value.dp,
                    userScrollEnabled = false
                ) { page ->
                    val pageOffset =
                        ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                    val targetCardScale = 1f - pageOffset * 0.35f
                    val baseCardAlpha = 1f
                    val isExitingNow = isExiting && exitProgress.value < 1f
                    val enterDelayed = if (pageOffset > 0.001f && !animDone) {
                        val delayThreshold = (pageOffset * 0.01f).coerceIn(0f, 0.01f)
                        ((enterValue - delayThreshold) / (1f - delayThreshold)).coerceIn(0f, 1f)
                    } else 1f
                    val cutoutScaleBoost = if (pageOffset > 0.001f) {
                        (1.2f - targetCardScale) * cutoutEnterProgress.value
                    } else 0f
                    val cardScale = when {
                        isExitingNow && pageOffset > 0.001f -> {
                            targetCardScale + (1.4f - targetCardScale) * exitProgress.value
                        }

                        pageOffset > 0.001f && !animDone -> {
                            2.0f + (targetCardScale - 2.0f) * enterDelayed
                        }

                        else -> targetCardScale + cutoutScaleBoost
                    }
                    // 离当前页越近 zIndex 越大，当前卡在最上方
                    val zOrdinal = (pageCount - pageOffset).coerceAtLeast(0f)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(zOrdinal),
                        contentAlignment = Alignment.Center
                    ) {
                        val cardScaleProg =
                            ((cardScaleAnim.value - 0.65f) / (1f - 0.65f)).coerceIn(0f, 1f)
                        val cardBaseOffsetY =
                            screenH * 0.028f + cutoutOffsetY.value + sheetOffsetY.value
                        val cardOffsetY = cardBaseOffsetY * (1f - cardScaleProg)
                        // 与 drawBehind 里洞的横向位移逐项一致（含 (1 - prog) 淡出），否则洞与卡片错位
                        val cardOffsetX = cutoutOffsetX.value * (1f - cardScaleProg)
                        val comb = combinations.getOrNull(page)
                        val isCurrentComb = page == currentCombinationIndex
                        // 缩放中心固定为屏幕中心，稳态与动画终点一致，无跳变
                        val signedRelativePosition =
                            (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                        val pageSpacingPx = with(densityObj) { pagerSpacing.value.dp.toPx() }
                        val cardCenterOffsetX = signedRelativePosition * (screenW + pageSpacingPx)
                        val pivotOriginX = 0.5f - cardCenterOffsetX / cardWidthPx
                        val pivotOriginY = 0.5f + (screenH * 0.08f - cardOffsetY) / cardHeightPx
                        Box(
                            modifier = Modifier
                                .width(cardWidthDp)
                                .height(cardHeightDp)
                                .offset(
                                    x = with(densityObj) { cardOffsetX.toDp() },
                                    y = with(densityObj) { cardOffsetY.toDp() }
                                )
                                .graphicsLayer {
                                    scaleX = cardScale
                                    scaleY = cardScale
                                    transformOrigin = TransformOrigin(pivotOriginX, pivotOriginY)
                                    alpha = baseCardAlpha
                                }
                                // 与遮罩洞用同一套圆角（含 20dp 下限），否则卡片露直角、洞是圆的
                                .clip(
                                    screenCornerRadius.scaled(cardScaleAnim.value)
                                        .coerceAtLeast(cutoutMinCornerPx)
                                        .toShape(densityObj)
                                )
                        ) {
                            val combIdx = page
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        enabled = !cardHidden && !isCutoutActive,
                                        onClick = {}
                                    )
                            ) {
                                // 当前搭配：刚选完壁纸时 comb.snapshot 被清掉，优先实时壁纸，避免旧快照盖住新图
                                val previewBitmap = when {
                                    isCurrentComb && wallpaperBitmap != null && comb?.snapshot == null ->
                                        wallpaperBitmap
                                    isCurrentComb && snapshot != null && animDone && !cardHidden ->
                                        snapshot
                                    comb != null && comb.snapshot != null -> comb.snapshot
                                    comb != null && comb.wallpaper != null -> comb.wallpaper
                                    else -> null
                                }
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF363636)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = SchedStr.combinationN(combIdx + 1),
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 16.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // 手势层放在标题/按钮之前，确保顶部和底部按钮点击不被拦截
                if (isCutoutActive && cardHidden && wallpaperBitmap != null) {
                    val latestScale by rememberUpdatedState(wallpaperScale)
                    val latestOffset by rememberUpdatedState(wallpaperOffset)
                    val latestOnScale by rememberUpdatedState(onWallpaperScaleChange)
                    val latestOnOffset by rememberUpdatedState(onWallpaperOffsetChange)
                    val latestMinWallpaperScale by rememberUpdatedState(minWallpaperScale)
                    val latestWallpaperBitmap by rememberUpdatedState(wallpaperBitmap)
                    val latestScreenWPx by rememberUpdatedState(screenWPx)
                    val latestScreenHPx by rememberUpdatedState(screenHPx)

                    var bounceBackTrigger by remember { mutableIntStateOf(0) }
                    var gestureEndScale by remember { mutableStateOf(1f) }
                    LaunchedEffect(bounceBackTrigger) {
                        if (bounceBackTrigger > 0 && gestureEndScale < latestMinWallpaperScale) {
                            animate(
                                initialValue = gestureEndScale,
                                targetValue = latestMinWallpaperScale,
                                animationSpec = tween(
                                    durationMillis = 350,
                                    easing = CubicBezierEasing(0.34f, 1.1f, 0.3f, 1f)
                                )
                            ) { value, _ ->
                                onWallpaperScaleChange(value)
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
                                    var lastDisplayScale: Float
                                    do {
                                        val event = awaitPointerEvent()
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        gestureScale *= zoom
                                        // 低于最小缩放时逐渐增大阻力
                                        val newScale = if (gestureScale < latestMinWallpaperScale) {
                                            val diff = gestureScale - latestMinWallpaperScale
                                            latestMinWallpaperScale + diff * 0.3f
                                        } else {
                                            gestureScale
                                        }
                                        lastDisplayScale = newScale
                                        val bmp = latestWallpaperBitmap
                                        if (bmp.width > 0 && bmp.height > 0) {
                                            val fitScale = minOf(
                                                latestScreenWPx / bmp.width,
                                                latestScreenHPx / bmp.height
                                            )
                                            val scaledW = bmp.width * fitScale * newScale
                                            val scaledH = bmp.height * fitScale * newScale
                                            val maxOffsetX =
                                                ((scaledW - latestScreenWPx) / 2f).coerceAtLeast(0f)
                                            val maxOffsetY =
                                                ((scaledH - latestScreenHPx) / 2f).coerceAtLeast(0f)
                                            val newOffset = latestOffset + pan
                                            latestOnScale(newScale)
                                            latestOnOffset(
                                                Offset(
                                                    newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                                    newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                                )
                                            )
                                        } else {
                                            latestOnScale(newScale)
                                            latestOnOffset(latestOffset + pan)
                                        }
                                        event.changes.forEach { it.consume() }
                                    } while (event.changes.any { it.pressed })
                                    gestureEndScale = lastDisplayScale
                                    bounceBackTrigger++
                                }
                            }
                    )
                }

                // 必须在内容区域之后，确保 Z 轴在最上层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = buttonAlphaAnim.value
                            scaleX = buttonScale
                            scaleY = buttonScale
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
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
                        val animationScope = rememberCoroutineScope()
                        val exitHighlight =
                            remember(animationScope) { InteractiveHighlight(animationScope) }
                        val applyHighlight =
                            remember(animationScope) { InteractiveHighlight(animationScope) }
                        val hapticFeedback = LocalHapticFeedback.current
                        val resolvedPreview = previewPage.coerceIn(0, 1)
                        // 选中滑块位置动画：0=今日，1=课程表
                        val thumbProgress = remember { Animatable(if (previewPage.coerceIn(0, 1) == 1) 1f else 0f) }
                        LaunchedEffect(resolvedPreview) {
                            thumbProgress.animateTo(
                                targetValue = if (resolvedPreview == 1) 1f else 0f,
                                animationSpec = tween(
                                    durationMillis = 280,
                                    easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(84.dp)
                                .height(40.dp)
                                .drawBackdrop(
                                    backdrop = liquidGlassBackdrop,
                                    shape = { ContinuousCapsule() },
                                    effects = {
                                        if (com.haooz.chedule.ui.utils.AppMaterialSettings.glassEffectsEnabled()) {
                                            vibrancy()
                                        }
                                    },
                                    highlight = null,
                                    shadow = { Shadow(alpha = 0.3f) },
                                    layerBlock = {
                                        val progress = exitHighlight.pressProgress
                                        val scale = 1f + 2f.dp.toPx() / 40.dp.toPx() * progress
                                        scaleX = scale
                                        scaleY = scale
                                        val offset = exitHighlight.offset
                                        translationX =
                                            size.minDimension * 0.05f * offset.x / size.maxDimension
                                        translationY =
                                            size.minDimension * 0.05f * offset.y / size.maxDimension
                                    },
                                    onDrawSurface = {
                                        drawRect(exitContainerColor)
                                        drawRect(Color.Black.copy(alpha = 0.03f * exitHighlight.pressProgress))
                                    }
                                )
                                .edgeLight(
                                    shape = ContinuousCapsule(),
                                    edgeLight = rememberDefaultEdgeLight(degradedBaseColor = exitContainerColor),
                                )
                                .clickable(
                                    interactionSource = null,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        if (isPageAnimating || isCutoutAnimating) { /* 动画中不响应 */
                                        } else onDismiss()
                                    }
                                )
                                .then(exitHighlight.modifier)
                                .then(exitHighlight.gestureModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = CommonStr.cancel(),
                                color = exitIconColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        // 中段：今日 / 课程表 预览切换（椭圆滑块，与取消/应用同行）
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(40.dp)
                                .drawBackdrop(
                                    backdrop = liquidGlassBackdrop,
                                    shape = { ContinuousCapsule() },
                                    effects = {
                                        if (com.haooz.chedule.ui.utils.AppMaterialSettings.glassEffectsEnabled()) {
                                            vibrancy()
                                        }
                                    },
                                    highlight = null,
                                    shadow = { Shadow(alpha = 0.3f) },
                                    onDrawSurface = { drawRect(exitContainerColor) }
                                )
                                .padding(3.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // 滑块：0.1f 白底，叠在图标下方左右滑动
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(0.5f)
                                        .graphicsLayer {
                                            translationX = thumbProgress.value * size.width
                                        }
                                        .clip(ContinuousCapsule())
                                        .background(Color.White.copy(alpha = 0.1f))
                                )
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable(
                                                interactionSource = null,
                                                indication = null,
                                                role = Role.Button,
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                    if (resolvedPreview != 0) onPreviewPageChange(0)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Album,
                                            contentDescription = CommonStr.today(),
                                            modifier = Modifier.size(20.dp),
                                            tint = if (resolvedPreview == 0) Color.White
                                            else Color.White.copy(alpha = 0.75f)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable(
                                                interactionSource = null,
                                                indication = null,
                                                role = Role.Button,
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                    if (resolvedPreview != 1) onPreviewPageChange(1)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Months,
                                            contentDescription = SchedStr.schedule(),
                                            modifier = Modifier.size(20.dp),
                                            tint = if (resolvedPreview == 1) Color.White
                                            else Color.White.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .width(84.dp)
                                .height(40.dp)
                                .drawBackdrop(
                                    backdrop = liquidGlassBackdrop,
                                    shape = { ContinuousCapsule() },
                                    effects = {
                                        if (com.haooz.chedule.ui.utils.AppMaterialSettings.glassEffectsEnabled()) {
                                            vibrancy()
                                        }
                                    },
                                    highlight = null,
                                    shadow = { Shadow(alpha = 0.3f) },
                                    layerBlock = {
                                        val progress = applyHighlight.pressProgress
                                        val scale = 1f + 2f.dp.toPx() / 40.dp.toPx() * progress
                                        scaleX = scale
                                        scaleY = scale
                                        val offset = applyHighlight.offset
                                        translationX =
                                            size.minDimension * 0.05f * offset.x / size.maxDimension
                                        translationY =
                                            size.minDimension * 0.05f * offset.y / size.maxDimension
                                    },
                                    onDrawSurface = {
                                        drawRect(primaryColor.copy(com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(0.8f)))
                                        drawRect(Color.Black.copy(alpha = 0.03f * applyHighlight.pressProgress))
                                    }
                                )
                                .edgeLight(
                                    shape = ContinuousCapsule(),
                                    edgeLight = rememberDefaultEdgeLight(degradedBaseColor = primaryColor),
                                )
                                .clickable(
                                    interactionSource = null,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        if (!showApplyLoading) {
                                            showApplyLoading = true
                                            onApply()
                                        }
                                    }
                                )
                                .then(applyHighlight.modifier)
                                .then(applyHighlight.gestureModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                SchedStr.applyChanges(),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (showApplyLoading) {
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

                AnimatedVisibility(
                    visible = isCutoutActive && !isApplyingCustomize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(
                            350,
                            easing = CustomizePageEasing
                        )
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(
                            250,
                            easing = CustomizePageEasing
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // ⚠ onSizeChanged 必须在 padding 之内：挂外面量到的是「内容高 + 底部
                            // padding」，而 padding 又由这个高度算出 → 自指方程（收敛因子 -1/2），
                            // 工具栏会交替上下跳（198→17→107→62…px）；放进来只观测内容高。
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(bottom = toolBottomPadding)
                            .onSizeChanged { toolRowHeightPx = it.height.toFloat() }
                            .graphicsLayer {
                                alpha = toolAlphaAnim.value
                                translationY = toolOffsetYAnim.value
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // radius=0 时不挂 blur，避免 Modifier.blur 额外建 RenderEffect 离屏层
                        val toolBlurModifier =
                            if (toolBlurAnim.value > 0.01f) Modifier.blur(toolBlurAnim.value.dp)
                            else Modifier
                        // 外层 padding 承载模糊溢出，内层圆裁剪可点击，避免 RenderEffect+clip 在边界裁出尖角
                        Box(
                            modifier = Modifier
                                .padding(7.dp)
                                .then(toolBlurModifier)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(if (hasWallpaper) Color(0xFFB71C1C) else Color(0xFF363636))
                                    .clickable {
                                        if (hasWallpaper) onClearWallpaper() else onPickWallpaper()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (hasWallpaper) MiuixIcons.Normal.Close else MiuixIcons.Image,
                                    contentDescription = if (hasWallpaper) SchedStr.clearWallpaper() else SchedStr.wallpaper(),
                                    modifier = Modifier.size(26.dp),
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .padding(7.dp)
                                .then(toolBlurModifier)
                        ) {
                            VerticalDivider(
                                Modifier
                                    .height(36.dp)
                                    .clip(CircleShape),
                                thickness = 2.dp,
                                color = Color(0xFF363636)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .padding(7.dp)
                                .then(toolBlurModifier)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF363636))
                                    .clickable { showEffectSheet = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Background,
                                    contentDescription = SchedStr.effects(),
                                    modifier = Modifier.size(28.dp),
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .padding(7.dp)
                                .then(toolBlurModifier)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF363636))
                                    .clickable { showCustomizeSheet = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.GridView,
                                    contentDescription = SchedStr.customize(),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                if (snapshot != null && !animDone) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = snapshot,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = currentScale
                                    scaleY = currentScale
                                    transformOrigin = TransformOrigin(0.5f, 0.58f)
                                    translationX = currentTranslationX
                                    translationY = currentTranslationY
                                    clip = true
                                    shape = screenCornerRadius.toShape(densityObj)
                                },
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // 退出快照由 MainActivity 覆盖层统一放大淡出，本页不重复渲染
                val sheetCloseButton: @Composable ((() -> Unit), Dp) -> Unit = { onClose, startPad ->
                    val material = LocalSheetTopBarMaterial.current
                    LiquidTopBarButton(
                        onClick = onClose,
                        // 读 Local 而非页面级 State，避免 backdrop 就绪时整页重跑组合
                        backdrop = LocalSheetContentBackdrop.current
                            ?: liquidGlassBackdrop,
                        icon = MiuixIcons.Normal.Close,
                        contentDescription = CommonStr.close(),
                        modifier = Modifier.padding(start = startPad),
                        iconSize = 24.dp,
                        containerColor = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainerHigh
                            .copy(alpha = if (isAppDarkTheme()) 0.4f else 0.6f)
                        else if (isAppDarkTheme()) Color(0xFF363636).copy(0.4f)
                        else Color(0xFFFFFFFF).copy(0.6f),
                        backdropAlpha = material.backdropAlpha,
                        shadowAlpha = material.shadowAlpha,
                    )
                }
                val effectSheetContent: @Composable () -> Unit = {
                    SheetCard {
                        // 无壁纸时选项禁用，固定显示"跟随应用"
                        val themeModeEntry = if (hasWallpaper) {
                            DropdownEntry(
                                items = ThemeMode.entries.map { mode ->
                                    DropdownItem(
                                        text = mode.label,
                                        selected = themeModeValue == mode,
                                        onClick = {
                                            // 仅本地预览，点「应用」才持久化
                                            themeModeValue = mode
                                            onThemeModePreview(mode)
                                        }
                                    )
                                }
                            )
                        } else {
                            DropdownEntry(
                                items = listOf(DropdownItem(text = SchedStr.followApp(), selected = true, onClick = {}))
                            )
                        }
                        OverlayDropdownMenu(
                            title = SchedStr.defaultTheme(),
                            entry = themeModeEntry,
                            collapseOnSelection = true,
                            enabled = hasWallpaper,
                            liquidGlassBackdrop = LocalSheetContentBackdrop.current
                                ?: liquidGlassBackdrop,
                            dropdownColors = liquidGlassDropdownColors,
                        )
                    }
                    SheetCard {
                        Column {
                            SliderItem(
                                label = SchedStr.wallpaperBrightness(),
                                value = wallpaperBrightnessValue,
                                valueRange = -50f..50f,
                                keyPoints = listOf(0f),
                                enabled = hasWallpaper,
                                onValueChange = { if (hasWallpaper) wallpaperBrightnessValue = it },
                                quantize = { it.roundToInt().toFloat() },
                                displayValue = { it.roundToInt().let { n -> if (n > 0) "+$n" else n.toString() } },
                                parseInput = { it.toFloatOrNull()?.coerceIn(-50f, 50f) }
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = SchedStr.wallpaperBlur(),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 17.sp,
                                    color = if (hasWallpaper) MiuixTheme.colorScheme.onSurface
                                    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Switch(
                                    checked = wallpaperBlurValue && hasWallpaper,
                                    enabled = hasWallpaper,
                                    onCheckedChange = { if (hasWallpaper) wallpaperBlurValue = it }
                                )
                            }
                        }
                    }
                    SheetCard {
                        Column {
                            SliderItem(
                                label = SchedStr.cardTint(),
                                value = cardAlphaValue,
                                valueRange = 0f..1f,
                                keyPoints = listOf(0.15f),
                                enabled = true,
                                onValueChange = { cardAlphaValue = it },
                                quantize = { (it * 100f).roundToInt() / 100f },
                                suffix = "%",
                                displayValue = { (it * 100).roundToInt().toString() },
                                parseInput = { it.toFloatOrNull()?.let { v -> (v / 100f).coerceIn(0f, 1f) } }
                            )
                            SliderItem(
                                label = SchedStr.cardOpacity(),
                                value = cardSurfaceAlphaValue,
                                valueRange = 0f..1f,
                                keyPoints = listOf(0.15f),
                                enabled = hasWallpaper,
                                onValueChange = { if (hasWallpaper) cardSurfaceAlphaValue = it },
                                quantize = { (it * 100f).roundToInt() / 100f },
                                suffix = "%",
                                displayValue = { (it * 100).roundToInt().toString() },
                                parseInput = { it.toFloatOrNull()?.let { v -> (v / 100f).coerceIn(0f, 1f) } }
                            )
                            SliderItem(
                                label = SchedStr.cardBlur(),
                                value = effectValue,
                                valueRange = 0f..20f,
                                keyPoints = listOf(4f),
                                // 无材质档锁死：卡片模糊/折射整体关（渲染侧同步强制 0）
                                enabled = hasWallpaper &&
                                    !com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier(),
                                // 整数吸附，避免留下显示为 0 却仍有一丝模糊的小数
                                onValueChange = { if (hasWallpaper) effectValue = it },
                                quantize = { it.roundToInt().coerceIn(0, 20).toFloat() },
                                suffix = "dp",
                                displayValue = { it.roundToInt().toString() },
                                parseInput = { it.toFloatOrNull()?.coerceIn(0f, 20f) },
                                disabledText = if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) {
                                    SchedStr.noMaterial()
                                } else {
                                    SchedStr.wallpaperRequired()
                                }
                            )
                            RefractionItem(
                                value = cardRefractionValue,
                                // 无材质档整体关；性能优先档渲染侧强制 OFF（折射是这个档最贵的一项）
                                enabled = hasWallpaper &&
                                    !com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier() &&
                                    !com.haooz.chedule.ui.utils.AppMaterialSettings.isPerformanceTier(),
                                onValueChange = { if (hasWallpaper) cardRefractionValue = it },
                                disabledText = when {
                                    com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier() ->
                                        SchedStr.noMaterial()
                                    com.haooz.chedule.ui.utils.AppMaterialSettings.isPerformanceTier() ->
                                        SchedStr.performanceMode()
                                    else -> SchedStr.wallpaperRequired()
                                }
                            )
                        }
                    }
                }
                val customizeSheetContent: @Composable () -> Unit = {
                    SheetCard {
                        Column {
                            SliderItem(
                                label = SchedStr.courseCardHeight(),
                                value = cardHeightValue,
                                valueRange = 34f..92f,
                                keyPoints = listOf(54f),
                                // 时间轴高度由真实时长决定，卡高档位无效：禁用并写明原因，否则像「拖了没反应」
                                enabled = scheduleViewMode != ScheduleViewMode.TIMELINE,
                                disabledText = SchedStr.timelineFixedHeight(),
                                onValueChange = { cardHeightValue = it },
                                quantize = { (it.roundToInt() / 2 * 2).toFloat() },
                                suffix = "dp",
                                displayValue = { it.roundToInt().toString() },
                                parseInput = { it.toFloatOrNull()?.coerceIn(34f, 92f) }
                            )
                            SliderItem(
                                label = SchedStr.courseCardCornerRadius(),
                                value = cardCornerRadiusValue,
                                valueRange = 0f..48f,
                                keyPoints = listOf(10f),
                                enabled = true,
                                onValueChange = { cardCornerRadiusValue = it },
                                quantize = { it.roundToInt().toFloat() },
                                suffix = "dp",
                                displayValue = { it.roundToInt().toString() },
                                parseInput = { it.toFloatOrNull()?.coerceIn(0f, 48f) }
                            )
                        }
                    }
                    SheetCard {
                        Column {
                            val contentAlignmentEntry = DropdownEntry(
                                items = CardContentAlignment.entries.map { alignment ->
                                    DropdownItem(
                                        text = alignment.label,
                                        selected = cardContentAlignmentValue == alignment,
                                        onClick = { cardContentAlignmentValue = alignment }
                                    )
                                }
                            )
                            OverlayDropdownMenu(
                                title = SchedStr.cardContentAlignment(),
                                entry = contentAlignmentEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = LocalSheetContentBackdrop.current
                                    ?: liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )
                            val textColorEntry = DropdownEntry(
                                items = CardTextColor.entries.map { color ->
                                    DropdownItem(
                                        text = color.label,
                                        selected = cardTextColorValue == color,
                                        onClick = { cardTextColorValue = color }
                                    )
                                }
                            )
                            OverlayDropdownMenu(
                                title = SchedStr.cardTextColor(),
                                entry = textColorEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = LocalSheetContentBackdrop.current
                                    ?: liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )
                            SliderItem(
                                label = SchedStr.cardTextScale(),
                                value = cardTextScaleValue,
                                valueRange = 0.5f..2.0f,
                                keyPoints = listOf(1.0f),
                                enabled = true,
                                onValueChange = { cardTextScaleValue = it },
                                quantize = { (it * 10f).roundToInt() / 10f },
                                suffix = "x",
                                // 用 formatOneDecimal 而非 "%.1f".format(it)：后者 JVM 专有，跨平台编不过
                                displayValue = { it.formatOneDecimal() },
                                parseInput = { it.replace(',', '.').toFloatOrNull()?.coerceIn(0.5f, 2.0f) }
                            )
                        }
                    }
                    SheetCard {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = SchedStr.showClassroom(),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 17.sp,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = showClassroomValue,
                                    onCheckedChange = { showClassroomValue = it }
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = SchedStr.showTeacher(),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 17.sp,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = showTeacherValue,
                                    onCheckedChange = { showTeacherValue = it }
                                )
                            }
                        }
                    }
                    SheetCard {
                        Column {
                            // 视图与分界线同卡：后者是否可用取决于前者
                            val viewModeEntry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = SchedStr.viewAsWeek(),
                                        selected = scheduleViewMode == ScheduleViewMode.WEEK,
                                        onClick = {
                                            onScheduleViewModeChange(ScheduleViewMode.WEEK)
                                        }
                                    ),
                                    DropdownItem(
                                        text = SchedStr.viewAsTimeline(),
                                        selected = scheduleViewMode == ScheduleViewMode.TIMELINE,
                                        onClick = {
                                            onScheduleViewModeChange(ScheduleViewMode.TIMELINE)
                                        }
                                    ),
                                )
                            )
                            OverlayDropdownMenu(
                                title = SchedStr.viewAs(),
                                entry = viewModeEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = LocalSheetContentBackdrop.current
                                    ?: liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )
                            // 时间轴没有节次分界线（纵轴是连续时间），开关置灰
                            val dividersEnabled = scheduleViewMode != ScheduleViewMode.TIMELINE
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = SchedStr.breakDividers(),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 17.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(
                                        alpha = if (dividersEnabled) 1f else 0.4f
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = showBreakDividersValue,
                                    onCheckedChange = { showBreakDividersValue = it },
                                    enabled = dividersEnabled
                                )
                            }
                        }
                    }
                }

                if (isTablet) {
                    BlurBottomSheetTablet(
                        show = showEffectSheet,
                        title = SchedStr.effects(),
                        sheetMaxHeight = 320.dp,
                        isBottomAligned = true,
                        onDismissRequest = { showEffectSheet = false },
                        startAction = { sheetCloseButton({ showEffectSheet = false }, 16.dp) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 580.dp)
                                .overScrollVertical()
                                .scrollEndHaptic(
                                    hapticFeedbackType = HapticFeedbackType.TextHandleMove
                                )
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Spacer(Modifier.height(56.dp))
                            effectSheetContent()
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                } else {
                    BlurBottomSheet(
                        show = showEffectSheet,
                        title = SchedStr.effects(),
                        onDismissRequest = { showEffectSheet = false },
                        startAction = { sheetCloseButton({ showEffectSheet = false }, 18.dp) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 580.dp)
                                .overScrollVertical()
                                .scrollEndHaptic(
                                    hapticFeedbackType = HapticFeedbackType.TextHandleMove
                                )
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Spacer(Modifier.height(58.dp))
                            effectSheetContent()
                            Spacer(Modifier.height(240.dp))
                        }
                    }
                }

                if (isTablet) {
                    BlurBottomSheetTablet(
                        show = showCustomizeSheet,
                        title = SchedStr.customize(),
                        sheetMaxHeight = 320.dp,
                        isBottomAligned = true,
                        onDismissRequest = { showCustomizeSheet = false },
                        startAction = { sheetCloseButton({ showCustomizeSheet = false }, 16.dp) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 580.dp)
                                .overScrollVertical()
                                .scrollEndHaptic(
                                    hapticFeedbackType = HapticFeedbackType.TextHandleMove
                                )
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Spacer(Modifier.height(56.dp))
                            customizeSheetContent()
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                } else {
                    BlurBottomSheet(
                        show = showCustomizeSheet,
                        title = SchedStr.customize(),
                        onDismissRequest = { showCustomizeSheet = false },
                        startAction = { sheetCloseButton({ showCustomizeSheet = false }, 16.dp) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 580.dp)
                                .overScrollVertical()
                                .scrollEndHaptic(
                                    hapticFeedbackType = HapticFeedbackType.TextHandleMove
                                )
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Spacer(Modifier.height(58.dp))
                            customizeSheetContent()
                            Spacer(Modifier.height(240.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.defaultColors(
            color = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainerHigh
            else if (isAppDarkTheme()) Color(0xFF303030) else Color(0xFFFFFFFF),
            contentColor = MiuixTheme.colorScheme.onSurface
        ),
    ) { content() }
}

@Composable
internal fun SliderItem(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    keyPoints: List<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    /**
     * 档位量化（取整）。只作用于对外提交的值，不参与滑块位置计算：位置由 rawValue 连续驱动，
     * 若回写路径也取整，animateFloatAsState 目标值只能在整档间跳、拖动一格一格卡顿。
     */
    quantize: (Float) -> Float = { it },
    suffix: String = "",
    displayValue: (Float) -> String = { it.roundToInt().toString() },
    parseInput: (String) -> Float? = { it.toFloatOrNull() },
    /** 禁用时的右侧文案（默认「需设置壁纸」；无材质档传「无材质」） */
    disabledText: String = SchedStr.wallpaperRequired(),
) {
    // 聚焦编辑期间不回写外部 value，避免打断输入
    var textInput by remember { mutableStateOf(displayValue(value)) }
    var isInputFocused by remember { mutableStateOf(false) }
    // 拖动位置用原始浮点值，保证跟手连续；松手后吸附回整档，与外部 value 对齐
    var rawValue by remember { mutableFloatStateOf(value) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!isInputFocused) textInput = displayValue(value)
        // 拖动中禁止回写，否则 thumb 会被拉回整数档位、丢掉连贯性
        if (!isDragging) rawValue = value
    }
    val textColor = if (enabled) MiuixTheme.colorScheme.onSurface
    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 15.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            if (enabled) {
                NativeTextField(
                    value = textInput,
                    onValueChange = { input ->
                        textInput = input
                        parseInput(input)?.let { onValueChange(quantize(it)) }
                    },
                    modifier = Modifier
                        .width(56.dp)
                        .onFocusChanged { isInputFocused = it.isFocused },
                    singleLine = true,
                    textAlign = TextAlign.End,
                    textStyle = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                if (suffix.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = suffix,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                        color = textColor,
                        textAlign = TextAlign.End,
                    )
                }
            } else {
                Text(
                    text = disabledText,
                    fontSize = 14.sp,
                    color = textColor
                )
            }
        }
        Slider(
            value = rawValue,
            onValueChange = { raw ->
                isDragging = true
                rawValue = raw
                onValueChange(quantize(raw))
            },
            onValueChangeFinished = {
                isDragging = false
                // 松手后落到整档，thumb 与外部 value 重新对齐
                rawValue = quantize(rawValue)
            },
            valueRange = valueRange,
            showKeyPoints = true,
            keyPoints = keyPoints,
            magnetThreshold = 0.05f,
            modifier = Modifier.fillMaxWidth(),
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            enabled = enabled
        )
    }
}

@Composable
private fun RefractionItem(
    value: CardRefractionLevel,
    enabled: Boolean,
    onValueChange: (CardRefractionLevel) -> Unit,
    /** 禁用时的右侧文案（默认「需设置壁纸」；无材质档传「无材质」） */
    disabledText: String = SchedStr.wallpaperRequired(),
) {
    val levels = CardRefractionLevel.entries
    val lastIndex = levels.lastIndex
    val textColor = if (enabled) MiuixTheme.colorScheme.onSurface
    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    // 与 SliderItem 同理：位置连续跟手，档位取整只作用于对外提交
    var rawValue by remember { mutableFloatStateOf(value.ordinal.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!isDragging) rawValue = value.ordinal.toFloat()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 15.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = SchedStr.cardRefraction(),
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            if (enabled) {
                Text(
                    text = value.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    textAlign = TextAlign.End,
                )
            } else {
                Text(
                    text = disabledText,
                    fontSize = 14.sp,
                    color = textColor
                )
            }
        }
        Slider(
            value = rawValue,
            onValueChange = { v ->
                isDragging = true
                rawValue = v
                onValueChange(levels[v.roundToInt().coerceIn(0, lastIndex)])
            },
            onValueChangeFinished = {
                isDragging = false
                // 落到最近档，与外部 value 重新对齐
                rawValue = rawValue.roundToInt().coerceIn(0, lastIndex).toFloat()
            },
            valueRange = 0f..lastIndex.toFloat(),
            showKeyPoints = true,
            keyPoints = levels.indices.map { it.toFloat() },
            magnetThreshold = 0.01f,
            modifier = Modifier.fillMaxWidth(),
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            enabled = enabled
        )
    }
}

@Composable
private fun SliderCard(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    keyPoints: List<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    suffix: String = "",
    displayValue: (Float) -> String = { it.roundToInt().toString() },
    parseInput: (String) -> Float? = { it.toFloatOrNull() },
) {
    SheetCard {
        SliderItem(
            label = label,
            value = value,
            valueRange = valueRange,
            keyPoints = keyPoints,
            enabled = enabled,
            onValueChange = onValueChange,
            suffix = suffix,
            displayValue = displayValue,
            parseInput = parseInput
        )
    }
}

/**
 * 缩略图条需要的「搭配视图」——宿主从自己的 `Combination` 映射过来。宿主 `Combination` 是运行期
 * 结构、字段是 `android.graphics.Bitmap`（还要 remap / recycle / 像素测光），跨平台侧不存在；
 * 本页只用到「有没有图、图是什么」，故按最小面暴露两张 `ImageBitmap?`。
 */
data class CombinationThumbnail(
    /** 该搭配的快照；null 表示尚未生成（此时回落到 [wallpaper]） */
    val snapshot: ImageBitmap?,
    /** 该搭配的壁纸 */
    val wallpaper: ImageBitmap?,
)

// ──────────────────────────────────────────────────────────────────────────
// 外观页入场时间轴：动画在封面快照盖着整屏时就开始跑，用户只看到收尾。硬约束：
// 每个元素 (delay + duration) ≤ CUSTOMIZE_COVER_WINDOW_MILLIS（封面撤除时刻），
// 否则封面消失后元素还在动，观感「脱节」。各元素落地（窗口 552ms）：
// 卡片错落 380 / 翻页间距 400 / 顶栏按钮 420 / 底部工具栏 420；
// 实测（iPhone 18 Pro Max / iOS 27.0）再晚 40~60ms（组合+帧对齐），故留 ~100ms 余量。
// ──────────────────────────────────────────────────────────────────────────

/** 卡片错落入场时长 */
private const val ENTER_CARD_MILLIS = 380

/** 翻页间距（-140f → -10f）收起时长 */
private const val ENTER_PAGER_SPACING_MILLIS = 400

/** 顶栏「取消 / 应用」按钮缩放（1.5f → 1f）收拢：延迟 + 时长 = 420ms */
private const val ENTER_BUTTON_DELAY_MILLIS = 80
private const val ENTER_BUTTON_MILLIS = 340

/** 底部工具栏（透明度 / 上移 / 模糊）归位：延迟 + 时长 = 420ms，与按钮同步落地 */
private const val ENTER_TOOL_DELAY_MILLIS = 80
private const val ENTER_TOOL_MILLIS = 340
