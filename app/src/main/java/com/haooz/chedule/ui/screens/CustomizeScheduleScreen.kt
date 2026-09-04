/** 自定义课表页面 - 课表外观选择 */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.haooz.chedule.data.AppearanceConfig
import com.haooz.chedule.data.CardContentAlignment
import com.haooz.chedule.data.CardRefractionLevel
import com.haooz.chedule.data.CardTextColor
import com.haooz.chedule.data.Combination
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
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
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.overlay.BlurBottomSheetTablet
import top.yukonga.miuix.kmp.overlay.LocalSheetTopBarMaterial
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

@SuppressLint(
    "ConfigurationScreenWidthHeight", "FrequentlyChangingValue",
    "AutoboxingStateCreation"
)
@Composable
fun CustomizeScheduleScreen(
    snapshot: Bitmap?,
    screenCornerRadius: Float,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onPickWallpaper: () -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    combinations: List<Combination> = emptyList(),
    currentCombinationIndex: Int = 0,
    isExiting: Boolean = false,
    isApplying: Boolean = false,
    isApplyingCustomize: Boolean = false,
    wallpaperBitmap: Bitmap? = null,
    wallpaperOffset: Offset = Offset.Zero,
    wallpaperScale: Float = 1f,
    onWallpaperOffsetChange: (Offset) -> Unit = {},
    onWallpaperScaleChange: (Float) -> Unit = {},
    onCutoutCenterChange: (Float) -> Unit = {},
    // 弹窗开合时开洞与主界面共用的位移驱动器：两边直接读同一 Animatable.value，保证同帧同步
    sheetOffsetShared: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> = Animatable(
        0f
    ),
    pendingEnterCutout: Boolean = false,
    onCutoutEntered: () -> Unit = {},
    // 外观配置：编辑器以 AppearanceConfig 整体读写，避免逐字段回调散点
    appearance: AppearanceConfig = AppearanceConfig(),
    onAppearanceChange: (AppearanceConfig) -> Unit = {},
    hasWallpaper: Boolean = false,
) {
    // ================================================================
    // 一、基础环境与尺寸计算
    // ================================================================
    val densityObj = LocalDensity.current
    val density = densityObj.density
    val screenRadiusDp = (screenCornerRadius / density).dp
    // 开洞时卡片上移的目标像素值
    val cutoutOffsetTargetPx = with(densityObj) { 10.dp.toPx() }
    // 弹窗打开时开洞区域上移的目标像素值
    val sheetOffsetTargetPx = with(densityObj) { 90.dp.toPx() }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val screenHPx = with(densityObj) { configuration.screenHeightDp.dp.toPx() }
    val screenWPx = with(densityObj) { configuration.screenWidthDp.dp.toPx() }

    // 计算壁纸最小缩放比例（填满短边，确保不露出底部背景）
    val minWallpaperScale = remember(wallpaperBitmap, screenWPx, screenHPx) {
        if (wallpaperBitmap != null && wallpaperBitmap.width > 0 && wallpaperBitmap.height > 0) {
            val fitScale =
                minOf(screenWPx / wallpaperBitmap.width, screenHPx / wallpaperBitmap.height)
            val coverScale =
                maxOf(screenWPx / wallpaperBitmap.width, screenHPx / wallpaperBitmap.height)
            if (fitScale > 0f) coverScale / fitScale else 1f
        } else 1f
    }

    // 模糊支持检测（API 31+ 支持 graphicsLayer blurRadius）

    // 液态玻璃支持（空采样：不采样实时底层内容，仅保留轻量着色效果）。
    // 页面背景/顶底栏下方表面本身已不透明，无需真实模糊；且避免 MIUI 的
    // MiBackgroundBlurBlend 采样含自身图层的渲染内容，导致渲染树无限递归（SIGSEGV）。
    // 两个 BlurBottomSheet 保留真实模糊，经由独立的 onSheetContentBackdropCreated 上报。
    val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop {
        drawRect(Color.Transparent)
    }
    // 液态玻璃效果的透明下拉颜色
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )

    // 页面根层背景 backdrop（空采样：只画表面色，不采样实时内容）。
    // 页面背景下方表面本身已不透明，无需真实模糊；避免 MIUI 的 MiBackgroundBlurBlend
    // 采样含自身图层的渲染内容，导致渲染树无限递归（SIGSEGV）。
    // 两个 BlurBottomSheet 保留真实模糊，经由独立的 onSheetContentBackdropCreated 上报。
    val sheetBackdropColor = MiuixTheme.colorScheme.surface
    val sheetBackdrop = rememberLayerBackdrop {
        drawRect(sheetBackdropColor)
    }

    val primaryColor = MiuixTheme.colorScheme.primary
    val exitContainerColor = Color.White.copy(0.08f)
    val exitIconColor = Color.White

    // ================================================================
    // 二、UI 状态：加载指示 / 底部弹窗 / 删除流程
    // ================================================================

    // --- 应用加载指示器：点击"应用"后显示，进入退出动画开始时隐藏 ---
    var showApplyLoading by remember { mutableStateOf(false) }
    LaunchedEffect(isApplyingCustomize) {
        if (isApplyingCustomize) {
            showApplyLoading = false
        }
    }

    // --- 编辑模式底部弹窗：效果 / 自定义 ---
    var showEffectSheet by remember { mutableStateOf(false) }
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var sheetContentBackdrop by remember { mutableStateOf<Backdrop?>(null) }
    // 重置标志：取消编辑时自增，触发弹窗内部状态回到 initial 值
    var sheetResetKey by remember { mutableIntStateOf(0) }

    // 效果参数：卡片模糊 / 卡片透明度（随当前搭配切换、随重置键复位）
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
    var wallpaperBrightnessValue by remember(
        currentCombinationIndex,
        sheetResetKey
    ) { mutableFloatStateOf(appearance.wallpaperBrightness) }
    LaunchedEffect(appearance.cardBlurRadius) { effectValue = appearance.cardBlurRadius }

    // 自定义参数：卡片高度 / 卡片圆角（随当前搭配切换、随重置键复位）
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

    // 由本地显示值组装完整外观配置，作为唯一上报入口
    fun buildAppearance() = AppearanceConfig(
        cardBlurRadius = effectValue,
        cardAlpha = cardAlphaValue,
        cardHeight = cardHeightValue,
        cardCornerRadius = cardCornerRadiusValue,
        wallpaperBrightness = wallpaperBrightnessValue,
        showBreakDividers = showBreakDividersValue,
        cardContentAlignment = cardContentAlignmentValue,
        cardTextColor = cardTextColorValue,
        cardTextScale = cardTextScaleValue,
        showClassroom = showClassroomValue,
        showTeacher = showTeacherValue,
        cardRefraction = cardRefractionValue,
        wallpaperBlur = wallpaperBlurValue
    )
    LaunchedEffect(effectValue, cardAlphaValue) { onAppearanceChange(buildAppearance()) }
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

    // --- 删除流程状态 ---
    // "自定义"按钮淡入淡出动画（进入编辑模式时淡出，退出时淡入）
    val customizeButtonAlpha = remember { Animatable(1f) }

    // ================================================================
    // 三、Pager 状态（单页：仅渲染一个搭配卡）
    // ================================================================
    val pageCount = combinations.size.coerceAtLeast(1)
    val initialPage = 0
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })

    // ================================================================
    // 四、动画状态声明
    // ================================================================

    // --- 进入动画相关 ---
    val animProgress = remember { Animatable(0f) }
    var animDone by remember { mutableStateOf(false) }
    var isPageAnimating by remember { mutableStateOf(true) }
    // 标题淡入淡出动画：进入延迟淡入，退出快速淡出
    val titleFadeAnim = remember { Animatable(0f) }
    // 按钮缩放动画：进入时 1.5→1.0，退出/应用时 1.0→1.5
    val buttonScaleAnim = remember { Animatable(1.5f) }
    // 卡片间距动画：进入时 -140 → -10
    val pagerSpacing = remember { Animatable(-140f) }
    // 退出进度：卡片反向放大（无延迟）使用
    val exitProgress = remember { Animatable(0f) }

    // --- 开洞（编辑模式）动画相关 ---
    var isCutoutActive by remember { mutableStateOf(false) }
    var isCutoutAnimating by remember { mutableStateOf(false) }
    var cardHidden by remember { mutableStateOf(false) }
    val cutoutProgress = remember { Animatable(0f) }
    val cardScaleAnim = remember { Animatable(0.65f) }
    val buttonAlphaAnim = remember { Animatable(1f) }
    val titleAlphaAnim = remember { Animatable(1f) }
    val cutoutOffsetY = remember { Animatable(0f) }
    // 弹窗打开时，开洞区域与 MainActivity 同步上移的额外偏移（共享到 MainActivity，同帧同步）
    val sheetOffsetY = sheetOffsetShared
    // 编辑模式进入/退出进度：驱动相邻卡片放大缩小
    val cutoutEnterProgress = remember { Animatable(0f) }
    // 底部工具栏动画（三个按钮 + 竖杠）：进入时轻微上移 + 淡入 + 模糊 8f→0f；退出反向
    // 初始为进入前状态：透明、下移、模糊8f；进入后淡入上移并去模糊
    // 上移高度：40dp（转 px）
    val toolOffsetTargetPx = with(densityObj) { 80.dp.toPx() }
    val toolAlphaAnim = remember { Animatable(0f) }
    val toolOffsetYAnim = remember { Animatable(toolOffsetTargetPx) }
    val toolBlurAnim = remember { Animatable(8f) }

    // ================================================================
    // 五、动画执行（LaunchedEffect 集中区）
    // ================================================================

    // 新建搭配后自动进入编辑模式
    LaunchedEffect(pendingEnterCutout) {
        if (pendingEnterCutout && !isCutoutActive) {
            delay(100.milliseconds)
            isCutoutActive = true
            onCutoutEntered()
        }
    }

    // 进入动画：animProgress 0→1，按钮 1.5→1.0，间距 -140→-10，标题延迟淡入
    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                animProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(450, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                )
                animDone = true
                isPageAnimating = false
            }
            // 卡片间距：-140 → -10
            launch {
                pagerSpacing.animateTo(
                    -10f,
                    tween(500, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                )
            }
            // 按钮缩小（1.5→1.0）：延迟 200ms 开始，与快照过渡动画并行
            launch {
                delay(200.milliseconds)
                buttonScaleAnim.animateTo(
                    1f,
                    tween(450, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                )
            }
            // 底部工具栏（三个按钮 + 竖杠）：进入时轻微上移 + 淡入 + 模糊8f→0f。
            // 与按钮缩小同一 200ms 延迟同步开始，同节奏并行运行
            launch {
                delay(200.milliseconds)
                coroutineScope {
                    launch {
                        toolAlphaAnim.animateTo(
                            1f,
                            tween(450, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                        )
                    }
                    launch {
                        toolOffsetYAnim.animateTo(
                            0f,
                            tween(450, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                        )
                    }
                    launch {
                        toolBlurAnim.animateTo(
                            0f,
                            tween(450, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                        )
                    }
                }
            }
            // 延迟 100ms 后淡入标题，与进入动画并行
            launch {
                delay(100.milliseconds)
                titleFadeAnim.animateTo(
                    1f,
                    tween(250, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                )
            }
        }
    }

    // 退出/应用时：按钮放大 1.0→1.5，标题快速淡出，exitProgress 0→1
    LaunchedEffect(isExiting) {
        if (isExiting) {
            exitProgress.snapTo(0f)
            coroutineScope {
                launch {
                    exitProgress.animateTo(
                        1f,
                        tween(500, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                    )
                }
                launch {
                    buttonScaleAnim.animateTo(
                        1.5f,
                        tween(500, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                    )
                }
                launch { titleFadeAnim.animateTo(0f, tween(150)) }
                // 底部工具栏反向动画：退出时下移 + 淡出 + 模糊0f→8f
                launch {
                    coroutineScope {
                        launch {
                            toolAlphaAnim.animateTo(
                                0f,
                                tween(500, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                            )
                        }
                        launch {
                            toolOffsetYAnim.animateTo(
                                toolOffsetTargetPx,
                                tween(500, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                            )
                        }
                        launch {
                            toolBlurAnim.animateTo(
                                8f,
                                tween(500, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                            )
                        }
                    }
                }
                // 取消退出时开洞一并放大到全屏，与应用时行为一致
                // （应用时由 LaunchedEffect(isApplying) 负责 cardScaleAnim → 1，此处仅处理取消路径）
                if (!isApplying) {
                    launch {
                        cardScaleAnim.animateTo(
                            1f,
                            tween(500, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                        )
                    }
                }
            }
        }
    }

    // 开洞动画：进入编辑模式 / 退出编辑模式
    LaunchedEffect(isCutoutActive) {
        if (isCutoutActive) {
            isCutoutAnimating = true
            coroutineScope {
                launch {
                    cardScaleAnim.animateTo(
                        0.75f,
                        tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                    )
                }
                launch { cutoutProgress.snapTo(1f) }
                // 编辑模式下顶部按钮保持可见（显示"取消"和"应用"），"自定义"按钮淡出
                launch { customizeButtonAlpha.animateTo(0f, tween(250)) }
                launch { titleAlphaAnim.animateTo(0f, tween(120)) }
                launch {
                    cutoutOffsetY.animateTo(
                        -cutoutOffsetTargetPx,
                        tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                    )
                }
                launch {
                    cutoutEnterProgress.animateTo(
                        1f,
                        tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                    )
                }
            }
            cardHidden = true
            isCutoutAnimating = false
        } else if (animDone) {
            cardHidden = false
            // 退出开洞，恢复原始状态
            coroutineScope {
                launch {
                    cardScaleAnim.animateTo(
                        0.65f,
                        tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                    )
                }
                launch { cutoutProgress.snapTo(0f) }
                launch { customizeButtonAlpha.animateTo(1f, tween(250)) }
                launch { buttonAlphaAnim.animateTo(1f, tween(250)) }
                launch { titleAlphaAnim.animateTo(1f, tween(250)) }
                launch {
                    cutoutOffsetY.animateTo(
                        0f,
                        tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                    )
                }
                launch {
                    cutoutEnterProgress.animateTo(
                        0f,
                        tween(400, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                    )
                }
                launch { pagerState.animateScrollToPage(currentCombinationIndex) }
            }
        }
    }

    // 应用动画：裁剪区域完全跟随卡片放大进程（位置由 cardScaleAnim 推导）
    LaunchedEffect(isApplying) {
        if (isApplying) {
            coroutineScope {
                launch {
                    cardScaleAnim.animateTo(
                        1f,
                        tween(500, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                    )
                }
                launch { buttonAlphaAnim.animateTo(0f, tween(250)) }
                launch { titleAlphaAnim.animateTo(0f, tween(250)) }
            }
        }
    }

    // 计算正确的 transformOrigin Y，传给 MainActivity 使缩放后的内容中心与裁剪区域中心对齐
    // 当 cutoutMainScale == cardScaleAnim 时（编辑模式和apply动画均满足），
    // transformOrigin Y = 0.58 + offset / (screenH * 0.35)，与 scaleProg 无关
    LaunchedEffect(cutoutOffsetY.value, screenHPx) {
        // sheetOffsetY 由 onSheetOffsetChange 单独同步，不通过 ratio 传递，避免帧延迟
        val tY = 0.58f + cutoutOffsetY.value / (screenHPx * 0.35f)
        onCutoutCenterChange(tY)
    }

    // 弹窗打开/关闭：取消/应用按钮消失，开洞区域与 MainActivity 同步上移/恢复
    val anySheetOpen = showEffectSheet || showCustomizeSheet
    LaunchedEffect(anySheetOpen) {
        if (anySheetOpen) {
            coroutineScope {
                launch { buttonAlphaAnim.animateTo(0f, tween(250)) }
                launch {
                    sheetOffsetY.animateTo(
                        -sheetOffsetTargetPx,
                        tween(400, easing = CubicBezierEasing(0.3f, 0.5f, 0.2f, 1.0f))
                    )
                }
            }
        } else {
            coroutineScope {
                launch { buttonAlphaAnim.animateTo(1f, tween(250)) }
                launch {
                    sheetOffsetY.animateTo(
                        0f,
                        tween(350, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                    )
                }
            }
        }
    }

    // ================================================================
    // 六、返回键处理
    // ================================================================
    BackHandler {
        when {
            isPageAnimating -> { /* 页面进入动画中，不响应 */
            }

            isCutoutAnimating -> { /* 编辑模式动画中，不响应 */
            }

            else -> onDismiss()
        }
    }

    // ================================================================
    // 七、派生动画值（每帧读取）
    // ================================================================
    // 当前进入动画值（动画完成后恒为 1f，避免无谓重组）
    val enterValue = if (animDone) 1f else animProgress.value
    // 按钮缩放：由 buttonScaleAnim 控制（进入 1.5→1.0，退出/应用 1.0→1.5）
    val buttonScale = buttonScaleAnim.value
    // 标题透明度：由 titleFadeAnim 控制（进入延迟淡入，退出快速淡出）
    val titleAlpha = titleFadeAnim.value

    // ================================================================
    // 八、UI 渲染
    // ================================================================

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(sheetBackdrop)
                // -------- 8.1 背景裁剪遮罩：在背景上挖出卡片形状的洞，露出底层 MainActivity 内容 --------
                .drawBehind {
                    val snapshotW = snapshot?.width?.toFloat() ?: size.width
                    val snapshotH = snapshot?.height?.toFloat() ?: size.height
                    val aspect = snapshotH / snapshotW
                    val cardCenterX = size.width / 2f
                    // 裁剪区域中心 Y 完全跟随卡片放大进程：
                    val scaleProg = ((cardScaleAnim.value - 0.65f) / (1f - 0.65f)).coerceIn(0f, 1f)
                    val baseOffsetY =
                        size.height * 0.028f + cutoutOffsetY.value + sheetOffsetY.value
                    val cardCenterY = size.height / 2f + baseOffsetY * (1f - scaleProg)
                    val animW = size.width * cardScaleAnim.value
                    val animH = animW * aspect
                    val p = cutoutProgress.value
                    val cutoutRadiusPx = screenRadiusDp.toPx() * cardScaleAnim.value * p

                    val left = cardCenterX + ((cardCenterX - animW / 2f) - cardCenterX) * p
                    val top = cardCenterY + ((cardCenterY - animH / 2f) - cardCenterY) * p

                    val path = Path().apply {
                        addRect(Rect(0f, 0f, size.width, size.height))
                        if (p > 0f) {
                            val squirclePath = Path().apply {
                                addSquircleRect(
                                    width = animW,
                                    height = animH,
                                    cornerRadius = cutoutRadiusPx,
                                )
                            }
                            addPath(squirclePath, Offset(left, top))
                        }
                        fillType = PathFillType.EvenOdd
                    }
                    drawPath(path, color = Color(0xFF1A1A1A))
                }
        ) {
            // -------- 8.2 尺寸计算 --------
            val screenW = constraints.maxWidth.toFloat()
            val screenH = constraints.maxHeight.toFloat()

            // 卡片尺寸：宽度基于屏幕，高度跟随快照比例
            val cardWidthPx = screenW * cardScaleAnim.value
            val snapshotWidth = snapshot?.width?.toFloat() ?: screenW
            val snapshotHeight = snapshot?.height?.toFloat() ?: screenH
            val snapshotAspect = snapshotHeight / snapshotWidth
            val cardHeightPx = cardWidthPx * snapshotAspect
            val cardWidthDp = with(densityObj) { cardWidthPx.toDp() }
            val cardHeightDp = with(densityObj) { cardHeightPx.toDp() }

            // 缩放比例：从全屏缩放到卡片大小
            val targetScaleX = cardWidthPx / screenW
            val targetScaleY = cardHeightPx / screenH
            val targetScale = minOf(targetScaleX, targetScaleY)

            // 进入动画
            val currentScale = 1f + (targetScale - 1f) * enterValue
            val currentTranslationY = 0f

            // -------- 8.3 内容区域：卡片 Pager + 自定义按钮 --------
            // 内容区域：卡片居中于屏幕 60% 高度处
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop),
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 水平翻页（卡片）
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
                    // 进入动画进度（带延迟，越远的卡片延迟越久）
                    val enterDelayed = if (pageOffset > 0.001f && !animDone) {
                        val delayThreshold = (pageOffset * 0.01f).coerceIn(0f, 0.01f)
                        ((enterValue - delayThreshold) / (1f - delayThreshold)).coerceIn(0f, 1f)
                    } else 1f
                    // 进入动画：相邻卡片从更大尺寸（2.0f）缩小到目标尺寸，带延迟
                    // 退出动画：反向放大到 2.0f，无延迟
                    // 编辑模式进入/退出：相邻卡片放大缩小（以屏幕中心为轴），幅度较小
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
                    // z-order：离当前页越近越在上（当前卡在最上方，相邻卡在其下，第三个更下）
                    // 距离越远 zIndex 越小
                    val zOrdinal = (pageCount - pageOffset).coerceAtLeast(0f)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(zOrdinal),
                        contentAlignment = Alignment.Center
                    ) {
                        // 卡片 Y 偏移跟随 cardScaleAnim 进程（与裁剪区域保持同步）
                        val cardScaleProg =
                            ((cardScaleAnim.value - 0.65f) / (1f - 0.65f)).coerceIn(0f, 1f)
                        val cardBaseOffsetY =
                            screenH * 0.028f + cutoutOffsetY.value + sheetOffsetY.value
                        val cardOffsetY = cardBaseOffsetY * (1f - cardScaleProg)
                        val comb = combinations.getOrNull(page)
                        val isCurrentComb = page == currentCombinationIndex
                        // 缩放中心始终为屏幕中心（0.5, 0.58），卡片从外侧缩向屏幕中心
                        // 稳态也用同一中心，动画结束与最终位置完全一致，无跳变
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
                                .offset(y = with(densityObj) { cardOffsetY.toDp() })
                                .graphicsLayer {
                                    scaleX = cardScale
                                    scaleY = cardScale
                                    transformOrigin = TransformOrigin(pivotOriginX, pivotOriginY)
                                    alpha = baseCardAlpha
                                }
                                .clip(ContinuousRoundedRectangle(screenRadiusDp * cardScaleAnim.value))
                        ) {
                            // 搭配卡：单卡（page 0 恒为当前搭配）
                            val combIdx = page
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        enabled = !cardHidden && !isCutoutActive,
                                        onClick = {}
                                    )
                            ) {
                                if (isCurrentComb && snapshot != null && animDone && !cardHidden) {
                                    // 当前搭配：实时快照（包含当前课表+壁纸的完整预览）
                                    Image(
                                        bitmap = snapshot.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (comb?.snapshot != null) {
                                    // 使用已保存的完整背景快照（课表+壁纸）
                                    val combSnapshot = comb.snapshot!!
                                    Image(
                                        bitmap = combSnapshot.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (comb?.bitmap != null) {
                                    // 兼容旧数据：仅有壁纸时回退使用壁纸
                                    val combBitmap = comb.bitmap!!
                                    Image(
                                        bitmap = combBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    // 无背景且无快照的搭配：显示纯色占位
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF363636)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "搭配 ${combIdx + 1}",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // 开洞模式下拦截拖拽/缩放手势，直接更新壁纸状态
                // 放在标题/按钮之前，确保顶部和底部按钮的点击不被拦截
                if (isCutoutActive && cardHidden && wallpaperBitmap != null) {
                    val latestScale by rememberUpdatedState(wallpaperScale)
                    val latestOffset by rememberUpdatedState(wallpaperOffset)
                    val latestOnScale by rememberUpdatedState(onWallpaperScaleChange)
                    val latestOnOffset by rememberUpdatedState(onWallpaperOffsetChange)
                    val latestMinWallpaperScale by rememberUpdatedState(minWallpaperScale)
                    val latestWallpaperBitmap by rememberUpdatedState(wallpaperBitmap)
                    val latestScreenWPx by rememberUpdatedState(screenWPx)
                    val latestScreenHPx by rememberUpdatedState(screenHPx)

                    // 手势结束后触发缩放回弹动画
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
                                        // 低于最小缩放时逐渐增大阻力，越缩越难
                                        val newScale = if (gestureScale < latestMinWallpaperScale) {
                                            val diff = gestureScale - latestMinWallpaperScale
                                            latestMinWallpaperScale + diff * 0.3f
                                        } else {
                                            gestureScale
                                        }
                                        lastDisplayScale = newScale
                                        // 计算合法偏移范围
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
                                    // 手势结束，记录最终显示缩放并标记需要回弹
                                    gestureEndScale = lastDisplayScale
                                    bounceBackTrigger++
                                }
                            }
                    )
                }

                // -------- 8.5 标题：进入/退出时以屏幕中心缩放 + 淡入淡出 --------
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = titleAlphaAnim.value * titleAlpha
                            scaleX = buttonScale
                            scaleY = buttonScale
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "课表外观",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .padding(top = statusBarPadding + 70.dp)
                    )
                }

                // -------- 8.6 顶部按钮栏（退出/应用） --------
                // 必须在内容区域之后，确保 Z 轴在最上层
                // 进入/退出时以屏幕中心缩放，缩放中心设在 (0.5, 0.5)
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
                            .padding(top = statusBarPadding + 16.dp)
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val animationScope = rememberCoroutineScope()
                        val exitHighlight =
                            remember(animationScope) { InteractiveHighlight(animationScope) }
                        val applyHighlight =
                            remember(animationScope) { InteractiveHighlight(animationScope) }
                        val hapticFeedback = LocalHapticFeedback.current
                        Box(
                            modifier = Modifier
                                .width(84.dp)
                                .height(40.dp)
                                .drawBackdrop(
                                    backdrop = liquidGlassBackdrop,
                                    shape = { ContinuousCapsule() },
                                    effects = {
                                        vibrancy()
                                        blur(2f.dp.toPx())
                                        lens(12f.dp.toPx(), 12f.dp.toPx())
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
                                    edgeLight = rememberDefaultEdgeLight()
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
                                text = "取消",
                                color = exitIconColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
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
                                        vibrancy()
                                        blur(2f.dp.toPx())
                                        lens(12f.dp.toPx(), 12f.dp.toPx())
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
                                        drawRect(primaryColor.copy(0.8f))
                                        drawRect(Color.Black.copy(alpha = 0.03f * applyHighlight.pressProgress))
                                    }
                                )
                                .edgeLight(
                                    shape = ContinuousCapsule(),
                                    edgeLight = rememberDefaultEdgeLight()
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
                                "应用",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // -------- 8.7 应用加载指示器：屏幕中央显示 --------
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
                                text = "正在应用",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // -------- 8.8 编辑模式工具栏（底部圆形按钮）：进入编辑模式时从底部滑入 --------
                AnimatedVisibility(
                    visible = isCutoutActive && !isApplyingCustomize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(
                            350,
                            easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)
                        )
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(
                            250,
                            easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 30.dp)
                            .offset(y = 7.dp)
                            .graphicsLayer {
                                alpha = toolAlphaAnim.value
                                translationY = toolOffsetYAnim.value
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 壁纸按钮：外层 Box 留 padding 承载模糊向外扩散空间，
                        // Modifier.blur 让模糊自然溢出圆形边界（边缘渐变正确），
                        // 内层 Box 保持圆形裁剪并可点击，避免 RenderEffect + clip 在边界裁切出尖角。
                        // 有壁纸时图标变为“叉”，点击清除壁纸；无壁纸时正常选择壁纸。
                        Box(
                            modifier = Modifier
                                .padding(7.dp)
                                .blur(radius = toolBlurAnim.value.dp)
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
                                    contentDescription = if (hasWallpaper) "清除壁纸" else "壁纸",
                                    modifier = Modifier.size(26.dp),
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        // 竖杠：同按钮方案——外层 padding 承载模糊扩散，blur 在外层自然溢出两端圆角
                        Box(
                            modifier = Modifier
                                .padding(7.dp)
                                .blur(radius = toolBlurAnim.value.dp)
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
                                .blur(radius = toolBlurAnim.value.dp)
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
                                    contentDescription = "效果",
                                    modifier = Modifier.size(28.dp),
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .padding(7.dp)
                                .blur(radius = toolBlurAnim.value.dp)
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
                                    contentDescription = "自定义",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // -------- 8.9 进入快照动画层：全屏 snapshot 缩放到卡片大小 --------
                // 容器填满整个屏幕，不在内部 padding，让 snapshot 从全屏位置开始
                if (snapshot != null && !animDone) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = snapshot.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = currentScale
                                    scaleY = currentScale
                                    transformOrigin = TransformOrigin(0.5f, 0.58f)
                                    translationY = currentTranslationY
                                    // clip 和 scale 在同一个 graphicsLayer 内，每帧重新裁剪
                                    // 视觉圆角 = screenRadius * currentScale（随缩放变化）
                                    clip = true
                                    shape = ContinuousRoundedRectangle(screenRadiusDp)
                                },
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // -------- 8.10 退出快照放大层：从卡片大小放大回全屏 --------
                // 快照放大由 MainActivity 的覆盖层统一处理，外观页面仅执行「放大淡出」（isExiting/customizeExitAlpha），
                // 自身不再渲染快照，避免在页面内重复放出一张快照。

                // -------- 8.11 弹窗共享内容（tablet / phone 复用，消除重复） --------
                // 通用关闭按钮（topBar startAction）
                val sheetCloseButton: @Composable ((() -> Unit), Dp) -> Unit = { onClose, startPad ->
                    val material = LocalSheetTopBarMaterial.current
                    LiquidTopBarButton(
                        onClick = onClose,
                        backdrop = sheetContentBackdrop ?: liquidGlassBackdrop,
                        icon = MiuixIcons.Normal.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.padding(start = startPad),
                        iconSize = 24.dp,
                        containerColor = if (isAppDarkTheme()) Color(0xFF363636).copy(0.4f)
                        else Color(0xFFFFFFFF).copy(0.6f),
                        backdropAlpha = material.backdropAlpha,
                        shadowAlpha = material.shadowAlpha,
                    )
                }
                // 效果弹窗内容：壁纸亮度置顶；课程卡片模糊 + 卡片不透明度放入同一卡片
                val effectSheetContent: @Composable () -> Unit = {
                    // 壁纸亮度置顶
                    SliderCard(
                        label = "壁纸亮度",
                        value = wallpaperBrightnessValue,
                        valueRange = -50f..50f,
                        keyPoints = listOf(0f),
                        enabled = hasWallpaper,
                        onValueChange = { if (hasWallpaper) wallpaperBrightnessValue = it },
                        // 正值（往右滑）带 + 号显示，如 +12
                        displayValue = { it.roundToInt().let { n -> if (n > 0) "+$n" else n.toString() } },
                        parseInput = { it.toFloatOrNull()?.coerceIn(-50f, 50f) }
                    )
                    // 壁纸模糊开关
                    SheetCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "壁纸模糊",
                                fontWeight = FontWeight.Medium,
                                fontSize = 17.sp,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = wallpaperBlurValue,
                                onCheckedChange = { wallpaperBlurValue = it }
                            )
                        }
                    }
                    // 课程卡片模糊 + 卡片不透明度：同一卡片内
                    SheetCard {
                        Column {
                            SliderItem(
                                label = "卡片模糊",
                                value = effectValue,
                                valueRange = 0f..20f,
                                keyPoints = listOf(4f),
                                enabled = hasWallpaper,
                                // 整数吸附：模糊范围是 0~20 的整 dp，显示也用 roundToInt。
                                // 否则放手会留下 0~1 间的随机小数（如 0.2dp），显示"0"却仍有一丝模糊。
                                onValueChange = { if (hasWallpaper) effectValue = it.roundToInt().coerceIn(0, 20).toFloat() },
                                suffix = "dp",
                                displayValue = { it.roundToInt().toString() },
                                parseInput = { it.toFloatOrNull()?.coerceIn(0f, 20f) }
                            )
                            // 卡片折射：4 个固定档位（关闭/较弱/默认/较强），需壁纸才生效
                            RefractionItem(
                                value = cardRefractionValue,
                                enabled = hasWallpaper,
                                onValueChange = { if (hasWallpaper) cardRefractionValue = it }
                            )
                            SliderItem(
                                label = "卡片不透明度",
                                value = cardAlphaValue,
                                valueRange = 0f..1f,
                                keyPoints = listOf(0.15f),
                                enabled = true,
                                onValueChange = { cardAlphaValue = it },
                                suffix = "%",
                                displayValue = { (it * 100).roundToInt().toString() },
                                parseInput = { it.toFloatOrNull()?.let { v -> (v / 100f).coerceIn(0f, 1f) } }
                            )
                        }
                    }
                }
                // 自定义弹窗内容：高度 + 圆角合并；对齐方式 + 文字颜色合并；分界线移至最底
                val customizeSheetContent: @Composable () -> Unit = {
                    // 课程卡片高度 + 课程卡片圆角：同一卡片内
                    SheetCard {
                        Column {
                            SliderItem(
                                label = "课程卡片高度",
                                value = cardHeightValue,
                                valueRange = 34f..92f,
                                keyPoints = listOf(54f),
                                enabled = true,
                                onValueChange = { cardHeightValue = (it.roundToInt() / 2 * 2).toFloat() },
                                suffix = "dp",
                                displayValue = { it.roundToInt().toString() },
                                parseInput = { it.toFloatOrNull()?.coerceIn(34f, 92f) }
                            )
                            SliderItem(
                                label = "课程卡片圆角",
                                value = cardCornerRadiusValue,
                                valueRange = 0f..48f,
                                keyPoints = listOf(10f),
                                enabled = true,
                                onValueChange = { cardCornerRadiusValue = it.roundToInt().toFloat() },
                                suffix = "dp",
                                displayValue = { it.roundToInt().toString() },
                                parseInput = { it.toFloatOrNull()?.coerceIn(0f, 48f) }
                            )
                        }
                    }
                    // 卡片内容对齐方式 + 卡片文字颜色：同一卡片内
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
                                title = "卡片内容对齐方式",
                                entry = contentAlignmentEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = sheetContentBackdrop
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
                                title = "卡片文字颜色",
                                entry = textColorEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = sheetContentBackdrop
                                    ?: liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )
                            SliderItem(
                                label = "卡片文字缩放比例",
                                value = cardTextScaleValue,
                                valueRange = 0.5f..2.0f,
                                keyPoints = listOf(1.0f),
                                enabled = true,
                                onValueChange = { cardTextScaleValue = (it * 10f).roundToInt() / 10f },
                                suffix = "x",
                                displayValue = { "%.1f".format(it) },
                                parseInput = { it.replace(',', '.').toFloatOrNull()?.coerceIn(0.5f, 2.0f) }
                            )
                        }
                    }
                    // 卡片内容显示：地点、教师
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
                                    text = "显示地点",
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
                                    text = "显示教师",
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
                    // 午休晚休分界线：移至最底部
                    SheetCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "午休晚休分界线",
                                fontWeight = FontWeight.Medium,
                                fontSize = 17.sp,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = showBreakDividersValue,
                                onCheckedChange = { showBreakDividersValue = it }
                            )
                        }
                    }
                }

                // -------- 8.11 效果弹窗 --------
                if (isTablet) {
                    BlurBottomSheetTablet(
                        show = showEffectSheet,
                        title = "效果",
                        sheetBackgroundAlpha = 1f,
                        sheetMaxHeight = 320.dp,
                        isBottomAligned = true,
                        onDismissRequest = { showEffectSheet = false },
                        onSheetContentBackdropCreated = { sheetContentBackdrop = it },
                        startAction = { sheetCloseButton({ showEffectSheet = false }, 16.dp) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 560.dp)
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
                        title = "效果",
                        sheetBackgroundAlpha = 1f,
                        onDismissRequest = { showEffectSheet = false },
                        onSheetContentBackdropCreated = { sheetContentBackdrop = it },
                        startAction = { sheetCloseButton({ showEffectSheet = false }, 18.dp) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 560.dp)
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
                } // end if (isTablet) else

                // -------- 8.12 自定义弹窗 --------
                if (isTablet) {
                    BlurBottomSheetTablet(
                        show = showCustomizeSheet,
                        title = "自定义",
                        sheetBackgroundAlpha = 1f,
                        sheetMaxHeight = 320.dp,
                        isBottomAligned = true,
                        onDismissRequest = { showCustomizeSheet = false },
                        onSheetContentBackdropCreated = { sheetContentBackdrop = it },
                        startAction = { sheetCloseButton({ showCustomizeSheet = false }, 16.dp) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 560.dp)
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
                        title = "自定义",
                        sheetBackgroundAlpha = 1f,
                        onDismissRequest = { showCustomizeSheet = false },
                        onSheetContentBackdropCreated = { sheetContentBackdrop = it },
                        startAction = { sheetCloseButton({ showCustomizeSheet = false }, 16.dp) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 560.dp)
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
                } // end if (isTablet) else for 自定义弹窗
            }
        } // Scaffold
    }
}

// -------- 8.11 弹窗共享组件（tablet / phone 复用） --------
// 通用卡片骨架：统一圆角、明暗背景色
@Composable
private fun SheetCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            color = if (isAppDarkTheme()) Color(0xFF363636).copy(alpha = 0.62f) else Color(
                0xFFFFFFFF
            ).copy(alpha = 0.7f),
            contentColor = MiuixTheme.colorScheme.onSurface
        ),
    ) { content() }
}

// 通用滑块设置项：单行（标题 + 可原地编辑的数值 + Slider），供单独或分组卡片复用
@Composable
private fun SliderItem(
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
    // 数值输入框本地文本：跟随外部 value 更新（聚焦编辑期间不回写，避免打断输入）
    var textInput by remember { mutableStateOf(displayValue(value)) }
    var isInputFocused by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!isInputFocused) textInput = displayValue(value)
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
                        parseInput(input)?.let(onValueChange)
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
                        color = textColor
                    )
                }
            } else {
                Text(
                    text = "需设置壁纸",
                    fontSize = 14.sp,
                    color = textColor
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
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

// 固定 4 档位滑块项：标题 + 档位标签 + 步进 Slider（供卡片折射等离散档位复用）
@Composable
private fun RefractionItem(
    value: CardRefractionLevel,
    enabled: Boolean,
    onValueChange: (CardRefractionLevel) -> Unit,
) {
    val levels = CardRefractionLevel.entries
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
                text = "卡片折射",
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
                    color = textColor
                )
            } else {
                Text(
                    text = "需设置壁纸",
                    fontSize = 14.sp,
                    color = textColor
                )
            }
        }
        // 4 个固定档位：把枚举序映射为 0..3 的整数级，拖动时吸附到整级并触发步进触感
        Slider(
            value = value.ordinal.toFloat(),
            onValueChange = { v ->
                onValueChange(levels[v.roundToInt().coerceIn(0, levels.lastIndex)])
            },
            valueRange = 0f..levels.lastIndex.toFloat(),
            showKeyPoints = true,
            keyPoints = levels.indices.map { it.toFloat() },
            magnetThreshold = 0.01f,
            modifier = Modifier.fillMaxWidth(),
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            enabled = enabled
        )
    }
}

// 通用滑块设置卡片：单个标题 + Slider
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
