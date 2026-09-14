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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.haooz.chedule.data.ThemeMode
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
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
import top.yukonga.miuix.kmp.overlay.LocalSheetContentBackdrop
import top.yukonga.miuix.kmp.overlay.LocalSheetTopBarMaterial
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

/** 挖洞遮罩路径缓存：尺寸/圆角不变时只 translate，避免每帧重建 squircle */
private class CutoutPathCache {
    private val path = Path()
    private var screenW = -1f
    private var screenH = -1f
    private var holeW = -1f
    private var holeH = -1f
    private var radius = -1f

    fun obtain(
        screenW: Float,
        screenH: Float,
        holeW: Float,
        holeH: Float,
        radius: Float
    ): Path {
        if (this.screenW != screenW || this.screenH != screenH ||
            this.holeW != holeW || this.holeH != holeH || this.radius != radius
        ) {
            this.screenW = screenW
            this.screenH = screenH
            this.holeW = holeW
            this.holeH = holeH
            this.radius = radius
            path.rewind()
            path.fillType = PathFillType.EvenOdd
            path.addRect(Rect(-screenW * 2f, -screenH * 2f, screenW * 3f, screenH * 3f))
            path.addSquircleRect(width = holeW, height = holeH, cornerRadius = radius)
        }
        return path
    }
}

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
    /** 默认主题实时预览：只改内存中的临时档位，点「应用」后才写入 SharedPreferences */
    onThemeModePreview: (ThemeMode) -> Unit = {},
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
    val densityObj = LocalDensity.current
    val density = densityObj.density
    val screenRadiusDp = (screenCornerRadius / density).dp
    val cutoutOffsetTargetPx = with(densityObj) { 10.dp.toPx() }
    val sheetOffsetTargetPx = with(densityObj) { 90.dp.toPx() }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val screenHPx = with(densityObj) { configuration.screenHeightDp.dp.toPx() }
    val screenWPx = with(densityObj) { configuration.screenWidthDp.dp.toPx() }

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

    // 不采样自身图层：含自身的 backdrop 会导致渲染树无限递归（SIGSEGV）
    val sheetBackdropColor = MiuixTheme.colorScheme.surface
    val sheetBackdrop = rememberLayerBackdrop {
        drawRect(sheetBackdropColor)
    }

    val primaryColor = MiuixTheme.colorScheme.primary
    val exitContainerColor = Color.White.copy(0.08f)
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

    val context = LocalContext.current
    val themePrefs = remember { context.getSharedPreferences("app_theme_prefs", android.content.Context.MODE_PRIVATE) }
    var themeModeValue by remember(currentCombinationIndex, sheetResetKey) {
        // 独立偏好 key，仅影响今日/课表页，不污染全局 theme_mode
        mutableStateOf(ThemeMode.fromPrefsValue(themePrefs.getString(ThemeMode.SCHEDULE_THEME_MODE_KEY, "follow_wallpaper")))
    }

    // 无壁纸时档位无意义，清壁纸即复位为跟随壁纸
    LaunchedEffect(hasWallpaper) {
        if (!hasWallpaper && themeModeValue != ThemeMode.FOLLOW_WALLPAPER) {
            themeModeValue = ThemeMode.FOLLOW_WALLPAPER
            themePrefs.edit().putString(ThemeMode.SCHEDULE_THEME_MODE_KEY, ThemeMode.FOLLOW_WALLPAPER.prefsValue).apply()
        }
    }

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

    val customizeButtonAlpha = remember { Animatable(1f) }

    val pageCount = combinations.size.coerceAtLeast(1)
    val initialPage = 0
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })

    val animProgress = remember { Animatable(0f) }
    var animDone by remember { mutableStateOf(false) }
    var isPageAnimating by remember { mutableStateOf(true) }
    val titleFadeAnim = remember { Animatable(0f) }
    val buttonScaleAnim = remember { Animatable(1.5f) }
    val pagerSpacing = remember { Animatable(-140f) }
    val exitProgress = remember { Animatable(0f) }

    var isCutoutActive by remember { mutableStateOf(false) }
    var isCutoutAnimating by remember { mutableStateOf(false) }
    var cardHidden by remember { mutableStateOf(false) }
    val cutoutProgress = remember { Animatable(0f) }
    val cardScaleAnim = remember { Animatable(0.65f) }
    val buttonAlphaAnim = remember { Animatable(1f) }
    val titleAlphaAnim = remember { Animatable(1f) }
    val cutoutOffsetY = remember { Animatable(0f) }
    // 与 MainActivity 共享同一 Animatable，弹窗开合时两边同帧同步位移
    val sheetOffsetY = sheetOffsetShared
    val cutoutEnterProgress = remember { Animatable(0f) }
    val cutoutPathCache = remember { CutoutPathCache() }
    val toolOffsetTargetPx = with(densityObj) { 80.dp.toPx() }
    val toolAlphaAnim = remember { Animatable(0f) }
    val toolOffsetYAnim = remember { Animatable(toolOffsetTargetPx) }
    val toolBlurAnim = remember { Animatable(8f) }

    // 新建搭配后自动进入编辑模式
    LaunchedEffect(pendingEnterCutout) {
        if (pendingEnterCutout && !isCutoutActive) {
            delay(100.milliseconds)
            isCutoutActive = true
            onCutoutEntered()
        }
    }

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
            launch {
                pagerSpacing.animateTo(
                    -10f,
                    tween(500, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                )
            }
            launch {
                delay(200.milliseconds)
                buttonScaleAnim.animateTo(
                    1f,
                    tween(450, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                )
            }
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
            launch {
                delay(100.milliseconds)
                titleFadeAnim.animateTo(
                    1f,
                    tween(250, easing = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f))
                )
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
                // 取消路径：开洞一并放大到全屏（应用路径由 isApplying 单独处理）
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

    // 应用动画：裁剪区域跟随 cardScaleAnim 放大进程
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

    // snapshotFlow 订阅，避免把 Animatable.value 当 LaunchedEffect key 导致重启
    val latestScreenHPx by rememberUpdatedState(screenHPx)
    val latestOnCutoutCenterChange by rememberUpdatedState(onCutoutCenterChange)
    LaunchedEffect(Unit) {
        snapshotFlow { cutoutOffsetY.value }.collect { offsetY ->
            latestOnCutoutCenterChange(0.58f + offsetY / (latestScreenHPx * 0.35f))
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

    BackHandler {
        when {
            isPageAnimating -> { /* 页面进入动画中，不响应 */
            }

            isCutoutAnimating -> { /* 编辑模式动画中，不响应 */
            }

            else -> onDismiss()
        }
    }

    // 动画完成后恒为 1f，避免无谓重组
    val enterValue = if (animDone) 1f else animProgress.value
    val buttonScale = buttonScaleAnim.value
    val titleAlpha = titleFadeAnim.value

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(sheetBackdrop)
                .drawBehind {
                    val snapshotW = snapshot?.width?.toFloat() ?: size.width
                    val snapshotH = snapshot?.height?.toFloat() ?: size.height
                    val aspect = snapshotH / snapshotW
                    val cardCenterX = size.width / 2f
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

                    if (p <= 0f) {
                        drawRect(color = Color(0xFF1A1A1A))
                    } else {
                        // 形状不变、仅位置变时走缓存 translate，squircle 不重建
                        val cached = cutoutPathCache.obtain(
                            screenW = size.width,
                            screenH = size.height,
                            holeW = animW,
                            holeH = animH,
                            radius = cutoutRadiusPx
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

            val targetScaleX = cardWidthPx / screenW
            val targetScaleY = cardHeightPx / screenH
            val targetScale = minOf(targetScaleX, targetScaleY)

            val currentScale = 1f + (targetScale - 1f) * enterValue
            val currentTranslationY = 0f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        // 弹窗打开时不录 liquidGlassBackdrop，只依赖 anySheetOpen 布尔，避免 backdrop 就绪引发结构变更重组
                        if (anySheetOpen) Modifier
                        else Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop),
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
                                .offset(y = with(densityObj) { cardOffsetY.toDp() })
                                .graphicsLayer {
                                    scaleX = cardScale
                                    scaleY = cardScale
                                    transformOrigin = TransformOrigin(pivotOriginX, pivotOriginY)
                                    alpha = baseCardAlpha
                                }
                                .clip(ContinuousRoundedRectangle(screenRadiusDp * cardScaleAnim.value))
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
                                if (isCurrentComb && snapshot != null && animDone && !cardHidden) {
                                    Image(
                                        bitmap = snapshot.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (comb?.snapshot != null) {
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
                                    contentDescription = if (hasWallpaper) "清除壁纸" else "壁纸",
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
                                    contentDescription = "自定义",
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
                            bitmap = snapshot.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = currentScale
                                    scaleY = currentScale
                                    transformOrigin = TransformOrigin(0.5f, 0.58f)
                                    translationY = currentTranslationY
                                    clip = true
                                    shape = ContinuousRoundedRectangle(screenRadiusDp)
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
                        contentDescription = "关闭",
                        modifier = Modifier.padding(start = startPad),
                        iconSize = 24.dp,
                        containerColor = if (isAppDarkTheme()) Color(0xFF363636).copy(0.4f)
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
                                items = listOf(DropdownItem(text = "跟随应用", selected = true, onClick = {}))
                            )
                        }
                        OverlayDropdownMenu(
                            title = "默认主题",
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
                                label = "壁纸亮度",
                                value = wallpaperBrightnessValue,
                                valueRange = -50f..50f,
                                keyPoints = listOf(0f),
                                enabled = hasWallpaper,
                                onValueChange = { if (hasWallpaper) wallpaperBrightnessValue = it },
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
                                    text = "壁纸模糊",
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
                                label = "卡片模糊",
                                value = effectValue,
                                valueRange = 0f..20f,
                                keyPoints = listOf(4f),
                                enabled = hasWallpaper,
                                // 整数吸附，避免留下显示为 0 却仍有一丝模糊的小数
                                onValueChange = { if (hasWallpaper) effectValue = it.roundToInt().coerceIn(0, 20).toFloat() },
                                suffix = "dp",
                                displayValue = { it.roundToInt().toString() },
                                parseInput = { it.toFloatOrNull()?.coerceIn(0f, 20f) }
                            )
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
                val customizeSheetContent: @Composable () -> Unit = {
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
                                title = "卡片文字颜色",
                                entry = textColorEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = LocalSheetContentBackdrop.current
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

                if (isTablet) {
                    BlurBottomSheetTablet(
                        show = showEffectSheet,
                        title = "效果",
                        sheetBackgroundAlpha = 1f,
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
                        title = "效果",
                        sheetBackgroundAlpha = 1f,
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
                        title = "自定义",
                        sheetBackgroundAlpha = 1f,
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
                        title = "自定义",
                        sheetBackgroundAlpha = 1f,
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
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            color = if (isAppDarkTheme()) Color(0xFF303030) else Color(0xFFFFFFFF),
            contentColor = MiuixTheme.colorScheme.onSurface
        ),
    ) { content() }
}

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
    // 聚焦编辑期间不回写外部 value，避免打断输入
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
