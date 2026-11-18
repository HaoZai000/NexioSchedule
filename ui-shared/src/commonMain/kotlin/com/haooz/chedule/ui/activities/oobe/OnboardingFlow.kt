/**
 * 引导宿主：组织欢迎页、语言页、设置页，并负责三者之间的过渡。
 *
 * 欢迎页 → 语言页复用项目已有的「卡片 ⇄ 全屏」形变（rememberMorphTransition + fadeInMorphClip），
 * 源卡片是欢迎页那颗圆形箭头按钮；语言页 → 设置页走 OobeFlipLayer 的横向翻转。
 * 收尾统一按「缩到 0.8 + 模糊 8dp + 淡出」退出，结束后回调宿主。
 *
 * 性能约定：所有动画进度只在绘制阶段读取（graphicsLayer / drawWithContent），不触发逐帧重组。
 */
package com.haooz.chedule.ui.activities.oobe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.haooz.chedule.i18n.LanguagePref
import com.haooz.nexioui.background.BgEffectConfig
import com.haooz.nexioui.motion.MORPH_BACKGROUND_SHRINK
import com.haooz.nexioui.motion.MorphTransition
import com.haooz.nexioui.motion.OobeHomeSpringEasing
import com.haooz.nexioui.motion.OobeSinOutEasing
import com.haooz.nexioui.motion.rememberMorphTransition
import com.haooz.nexioui.ripple.WaterRippleOverlay
import com.haooz.nexioui.ripple.supportsWaterRipple
import com.haooz.chedule.ui.theme.AppNexioTheme
import com.haooz.chedule.ui.theme.rememberAppThemeColors
import com.haooz.chedule.ui.utils.LocalForcedDarkTheme
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiiPx
import com.haooz.nexioui.window.toShape
import com.haooz.softglass.platform.isRenderEffectSupported
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 引导页专用流光配色：同一套流光算法，换成淡蓝色调。
 *
 * 颜色按 RGBA 四元组摊平，每组 4 个分量。
 */
internal val OobeBluePresetLight = BgEffectConfig.Config(
    points = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f),
    colors1 = floatArrayOf(
        0.91f, 0.945f, 1.0f, 1.0f, 0.839f, 0.902f, 1.0f, 1.0f,
        0.918f, 0.965f, 1.0f, 1.0f, 0.812f, 0.878f, 1.0f, 1.0f,
    ),
    colors2 = floatArrayOf(
        0.749f, 0.851f, 1.0f, 1.0f, 0.89f, 0.941f, 1.0f, 1.0f,
        0.847f, 0.906f, 1.0f, 1.0f, 0.776f, 0.867f, 1.0f, 1.0f,
    ),
    colors3 = floatArrayOf(
        0.851f, 0.914f, 1.0f, 1.0f, 0.749f, 0.843f, 1.0f, 1.0f,
        0.914f, 0.953f, 1.0f, 1.0f, 0.788f, 0.875f, 1.0f, 1.0f,
    ),
    colorInterpPeriod = 6.0f,
    lightOffset = 0.1f,
    saturateOffset = 0.15f,
    pointOffset = 0.2f,
)

/** 深色版流光配色，仅在引导层需要跟随深色时使用（当前引导固定浅色，保留备用）。 */
internal val OobeBluePresetDark = BgEffectConfig.Config(
    points = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f),
    colors1 = floatArrayOf(
        0.106f, 0.165f, 0.29f, 0.55f, 0.063f, 0.118f, 0.22f, 0.6f,
        0.086f, 0.161f, 0.302f, 0.6f, 0.055f, 0.102f, 0.188f, 0.6f,
    ),
    colors2 = floatArrayOf(
        0.043f, 0.098f, 0.235f, 0.6f, 0.11f, 0.18f, 0.35f, 0.55f,
        0.06f, 0.13f, 0.28f, 0.6f, 0.03f, 0.08f, 0.2f, 0.6f,
    ),
    colors3 = floatArrayOf(
        0.16f, 0.24f, 0.42f, 0.5f, 0.07f, 0.13f, 0.3f, 0.55f,
        0.13f, 0.21f, 0.4f, 0.5f, 0.05f, 0.11f, 0.26f, 0.6f,
    ),
    colorInterpPeriod = 8.0f,
    lightOffset = 0.0f,
    saturateOffset = 0.12f,
    pointOffset = 0.4f,
)

/** 引导的三个页面。 */
private enum class OobePage { Welcome, Language, Setup }

/** 退场时长（毫秒）：结束后才回调宿主。 */
internal const val OOBE_FINISH_MILLIS = 360

/** 退场时的最大模糊（dp）。 */
private const val OOBE_FINISH_BLUR_DP = 8f

/** 退场时整体缩小的比例。 */
private const val OOBE_FINISH_SHRINK = 0.2f

/** 页面退到背景时的模糊（dp）。 */
private const val OOBE_MORPH_BLUR_DP = 12f

/**
 * 引导层入口。
 *
 * @param onFinish 退场动画播完，引导可以撤下
 * @param onReachedFinishPage 走到完成页，宿主据此开始加载主界面
 * @param mainContentLoaded 主界面首帧已就绪，完成页的「开始使用」等它
 * @param onFinishStart 开始退场，宿主据此把主界面弹到 1
 */
@Composable
fun OnboardingFlow(
    onFinish: () -> Unit,
    onReachedFinishPage: () -> Unit = {},
    mainContentLoaded: Boolean = true,
    onFinishStart: () -> Unit = {},
) {
    // 引导固定浅色，不跟随应用/系统深色模式
    val lightColors = rememberAppThemeColors(isDark = false, monetEnabled = false)
    // 玻璃按钮等组件读 LocalForcedDarkTheme：一起锁成浅色，避免深色偏好下变黑底
    CompositionLocalProvider(LocalForcedDarkTheme provides false) {
        AppNexioTheme(colors = lightColors) {
            OnboardingFlowContent(onFinish, onReachedFinishPage, mainContentLoaded, onFinishStart)
        }
    }
}

@Composable
private fun OnboardingFlowContent(
    onFinish: () -> Unit,
    onReachedFinishPage: () -> Unit,
    mainContentLoaded: Boolean,
    onFinishStart: () -> Unit,
) {
    // 三页各自独立：每页有自己的组合与状态，页面之间只靠形变 / 翻转叠层过渡
    var page by remember { mutableStateOf(OobePage.Welcome) }
    // 每次进入页面都换一份新的形变实例：形变播完后内部状态不能再复用
    var morphSession by remember { mutableIntStateOf(0) }
    var flipSession by remember { mutableIntStateOf(0) }

    /** 形变进度，只给内容层在绘制阶段读，避免逐帧重组。 */
    val bgProgress = remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val screenCornerRadius = rememberScreenCornerRadiiPx()
    val morphBlurPx = remember(density) { with(density) { OOBE_MORPH_BLUR_DP.dp.toPx() } }
    val finishBlurPx = remember(density) { with(density) { OOBE_FINISH_BLUR_DP.dp.toPx() } }
    val blurSupported = remember { isRenderEffectSupported() }

    // 欢迎页启动水波（扩散光环），只播一次
    val rippleSupported = remember { supportsWaterRipple() }
    val rippleProgress = remember { Animatable(0f) }

    // —— 语言页 → 设置页 ——
    var flipCardRect by remember { mutableStateOf<Rect?>(null) }
    var flipLanguage by remember { mutableStateOf<LanguagePref?>(null) }
    var languageOrigin by remember { mutableStateOf(Offset.Zero) }
    // 翻完停在 1：设置页就住在翻转层里，之后不再换手
    val flipProgress = remember { Animatable(1f) }
    // 翻转落定后才恢复玻璃采样层：动画期间整页每帧重画，跟着重录纯属浪费
    val flipSettled by remember { derivedStateOf { flipProgress.value >= 1f } }
    // 只有设置页在场（翻转中 / 翻完）才处理语言页背景：flipProgress 平时停在 1，
    // 直接用会让刚进来的语言页也全屏模糊缩小
    val flipBackground = remember {
        { if (page == OobePage.Setup) flipProgress.value else 0f }
    }

    // 设置页自己的状态（协议 / 基础设置 / 位置 / 完成）
    val setupState = remember { OobeSetupState() }
    val scope = rememberCoroutineScope()

    // 收尾：缩放走弹簧曲线缩到 0.8，淡出走 SinOut，另叠一层模糊
    var finishing by remember { mutableStateOf(false) }
    val finishScale by animateFloatAsState(
        targetValue = if (finishing) 1f else 0f,
        animationSpec = tween(OOBE_FINISH_MILLIS, easing = OobeHomeSpringEasing),
        label = "onboardingFinishScale",
    )
    val finishAlpha by animateFloatAsState(
        targetValue = if (finishing) 1f else 0f,
        animationSpec = tween(OOBE_FINISH_MILLIS, easing = OobeSinOutEasing),
        label = "onboardingFinishAlpha",
    )
    // 退场缩小时按屏幕圆角裁切，四角跟屏幕形状对齐
    val finishClipShape = remember(screenCornerRadius, density) {
        if (screenCornerRadius.maxRadius > 0f) screenCornerRadius.toShape(density) else null
    }
    LaunchedEffect(finishing) {
        if (!finishing) return@LaunchedEffect
        delay(OOBE_FINISH_MILLIS.toLong())
        onFinish()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 不缩放的整屏浅色底：退场缩小时四角露出的仍是引导底色，而不是下层深色 surface。
        // background 必须在 graphicsLayer 之后，否则 alpha 管不到它
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - finishAlpha }
                .background(OobeBackgroundBrush),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // 先模糊、再裁屏幕圆角：clip 在外层，裁的是已经糊好的结果
                .then(
                    if (finishing && finishClipShape != null) {
                        Modifier.clip(finishClipShape)
                    } else {
                        Modifier
                    },
                )
                // 收尾图层：背景、页面、按钮一起走，不会露出下层页面
                .graphicsLayer {
                    alpha = 1f - finishAlpha
                    val shrink = 1f - OOBE_FINISH_SHRINK * finishScale
                    scaleX = shrink
                    scaleY = shrink
                    val radius = finishBlurPx * finishScale
                    renderEffect = if (blurSupported && radius > 0.01f) {
                        BlurEffect(null, radius, radius, TileMode.Clamp)
                    } else {
                        null
                    }
                }
                // 引导层独占触摸：这一层成为命中目标，下层应用收不到点击。
                // 只占位、不消费事件——消费会把页内滚动当成「被父级抢走」而取消
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                    }
                },
        ) {
            val screenWidth = constraints.maxWidth.toFloat()
            val screenHeight = constraints.maxHeight.toFloat()

            // 停在完成页时，语言页撤掉；流光背景要留着——完成页正是铺在它上面
            val onFinishPage = page == OobePage.Setup && setupState.isFinishStep()
            // 翻转未落定时暂停流光：此时下层被缩小 + 模糊 + 压暗盖着，逐帧重绘
            // 只会让上层那张全屏模糊每帧失效。derivedStateOf 只在落定那一帧重组一次。
            val flipping = page == OobePage.Setup && !flipSettled

            // 常驻一份实时流光背景：流光不停，进出引导都不会像快照那样闪一下。
            // 完成页自己带一份（必须落在玻璃采样层里，按钮才采得到），这里就不再叠第二个
            if (!onFinishPage) {
                OnboardingWelcomeBackdrop(playing = !flipping)
            }

            if (rippleSupported && page == OobePage.Welcome) {
                WaterRippleOverlay(progress = rippleProgress)
            }

            // 欢迎页常驻：形变时随进度缩放 + 模糊地退到背景里。
            // 完成页会把底色让给流光，此时这层（含箭头）必须撤掉，否则会透出来
            if (!onFinishPage) {
                OnboardingWelcomeContent(
                    onContinue = {
                        morphSession++
                        page = OobePage.Language
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = bgProgress.floatValue
                            val scale = 1f + (MORPH_BACKGROUND_SHRINK - 1f) * p
                            scaleX = scale
                            scaleY = scale
                            val radius = morphBlurPx * p
                            renderEffect = if (blurSupported && radius > 0.01f) {
                                BlurEffect(null, radius, radius, TileMode.Clamp)
                            } else {
                                null
                            }
                        },
                    interactive = page == OobePage.Welcome,
                    showArrow = page == OobePage.Welcome,
                    // 离开欢迎页后字标流光没人看得到，停掉无限动画
                    shimmer = page == OobePage.Welcome,
                )
            }

            // 语言页：住在形变层里（欢迎页那颗箭头圆长成这一页），返回时反向收回
            if (page != OobePage.Welcome && !onFinishPage) {
                key(morphSession) {
                    PageMorphLayer(
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        density = density.density,
                        screenCornerRadius = screenCornerRadius,
                        bgProgress = bgProgress,
                        onClosed = {
                            // 回到欢迎页：把翻转相关状态清干净，下次重进语言页才能再翻
                            bgProgress.floatValue = 0f
                            flipLanguage = null
                            flipCardRect = null
                            page = OobePage.Welcome
                        },
                    ) { close ->
                        OnboardingLanguageScreen(
                            hidden = if (page == OobePage.Setup) flipLanguage else null,
                            onBack = close,
                            onPick = { pref, rect ->
                                if (flipLanguage == null) {
                                    selectLanguage(
                                        pref
                                    )
                                    flipCardRect = rect
                                    flipLanguage = pref
                                    flipSession++
                                    scope.launch {
                                        flipProgress.snapTo(0f)
                                        page = OobePage.Setup
                                        flipProgress.animateTo(
                                            1f,
                                            tween(
                                                OobeFlipMotionMs,
                                                easing = FastOutSlowInEasing
                                            ),
                                        )
                                    }
                                }
                            },
                            onRootPositioned = { languageOrigin = it },
                            modifier = Modifier
                                .fillMaxSize()
                                // 与 morph 一致：设置页翻进来时，背后的语言页同步压暗、模糊、缩小
                                .graphicsLayer {
                                    val p = flipBackground()
                                    val shrink = 1f + (MORPH_BACKGROUND_SHRINK - 1f) * p
                                    scaleX = shrink
                                    scaleY = shrink
                                    val radius = morphBlurPx * p
                                    renderEffect = if (blurSupported && radius > 0.01f) {
                                        BlurEffect(null, radius, radius, TileMode.Clamp)
                                    } else {
                                        null
                                    }
                                },
                        )
                    }
                }
            }

            // 设置页：住在翻转层里，翻完就地留下；返回时反向翻回语言页
            val flipRect = flipCardRect
            val flipPref = flipLanguage
            if (page == OobePage.Setup && flipRect != null && flipPref != null) {
                key(flipSession) {
                    OobeFlipLayer(
                        progress = { flipProgress.value },
                        cardRect = flipRect,
                        origin = languageOrigin,
                        language = flipPref,
                    ) {
                        OnboardingSetupScreen(
                            state = setupState,
                            onBack = {
                                scope.launch {
                                    flipProgress.animateTo(
                                        0f,
                                        tween(
                                            OobeFlipMotionMs,
                                            easing = FastOutSlowInEasing
                                        ),
                                    )
                                    page = OobePage.Language
                                    flipLanguage = null
                                }
                            },
                            onFinishRequest = {
                                finishing = true
                                onFinishStart()
                            },
                            onReachedFinishPage = onReachedFinishPage,
                            settled = flipSettled,
                            mainContentLoaded = mainContentLoaded,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** 淡入在形变 40% 处结束。 */
private const val FADE_IN_END = 0.4f

/**
 * 内容层与快照箭头共用一条曲线：
 */
private fun fadeInAlpha(progress: Float): Float =
    ((progress - 0) / (FADE_IN_END - 0)).coerceIn(0f, 1f)

/**
 * 箭头落定时的过冲：沿**实际运动方向**冲过终点、再拉回原位。
 */
private const val ARROW_LAND_TRIGGER = 0.12f
private const val ARROW_LAND_OVERSHOOT_DP = 6f
private const val ARROW_LAND_OUT_MILLIS = 140
private const val ARROW_LAND_DAMPING = 0.72f
private const val ARROW_LAND_STIFFNESS = 200f

/**
 * 与形变自身的裁剪区域一致（顶端展开到 `clipBottom`，绘制阶段每帧重算），只改圆角：
 * 淡入完成前（p ≤ [FADE_IN_END]）保持圆形（短边一半），之后线性收敛到屏幕圆角。
 *
 * 不复用 `morph.clipShape` 的 outline：它是连续圆角，返回 Outline.Generic，拿不到矩形，
 * 也做不出严格圆形。
 */
private fun Modifier.fadeInMorphClip(morph: MorphTransition, screenCornerRadiusPx: Float): Modifier =
    drawWithContent {
        val s = morph.state.value
        // 形变结束就是全屏直角，不必裁
        if (s.progress >= 1f && s.gesture <= 0f) {
            drawContent()
            return@drawWithContent
        }
        val bottom = s.clipBottom.coerceIn(0f, size.height)
        if (bottom <= 0f) return@drawWithContent
        // 淡入期间裁成以页面顶边中点为心的正圆（直径 = 短边），淡入结束后展开成整页
        val diameter = min(size.width, bottom)
        val k = ((s.progress - FADE_IN_END) / (1f - FADE_IN_END)).coerceIn(0f, 1f)
        fun lerp(from: Float, to: Float) = from + (to - from) * k
        val left = lerp((size.width - diameter) / 2f, 0f)
        val right = lerp((size.width + diameter) / 2f, size.width)
        val height = lerp(diameter, bottom)
        val radius = lerp(diameter / 2f, screenCornerRadiusPx)
        val width = right - left
        if (width <= 0f || height <= 0f) return@drawWithContent
        // G2 连续圆角（squircle），与应用图标、卡片等其它圆角同一套形状
        val shape = ContinuousRoundedRectangle(min(radius, min(width, height) / 2f).toDp())
        val outline = shape.createOutline(Size(width, height), LayoutDirection.Ltr, this)
        clipPath(
            Path().apply {
                addOutline(outline)
                translate(Offset(left, 0f))
            },
        ) {
            this@drawWithContent.drawContent()
        }
    }

/**
 * 页面 + 形变层。
 *
 * 与课程详情/课程编辑同构：graphicsLayer 做 scale / translation / 锚点，fadeInMorphClip
 * 裁形变形状，页面自身带不透明底色。收起按展开曲线反向播放，所以退出就是进入的倒放。
 *
 * @param bgProgress 形变进度出口，供宿主在绘制阶段读
 */
@Composable
private fun PageMorphLayer(
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    screenCornerRadius: ScreenCornerRadiiPx,
    bgProgress: MutableFloatState,
    onClosed: () -> Unit,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    val buttonPx = WelcomeLayout.buttonSize.value * density
    val center = WelcomeLayout.buttonCenter(screenWidth, screenHeight, density)
    val cardTop = center.y - buttonPx / 2f
    val scope = rememberCoroutineScope()
    val overshootPx = ARROW_LAND_OVERSHOOT_DP * density
    // 过冲幅度 0→1→0 用固定时长播，不跟 progress 的 easing 走；方向是普通字段，不进快照状态
    val land = remember { Animatable(0f) }
    val landDir = remember { floatArrayOf(0f, 1f) }
    val landStarted = remember { booleanArrayOf(false) }
    val landJob = remember { arrayOfNulls<Job>(1) }
    // 绘制期把当前位移写进来，触发时直接拿来求运动方向，免得再抄一遍几何公式
    val arrowOffset = remember { floatArrayOf(0f, 0f) }

    // 源卡片 = 箭头按钮（圆形），起点圆角取半径
    val morph = rememberMorphTransition(
        cardLeft = center.x - buttonPx / 2f,
        cardTop = cardTop,
        cardWidth = buttonPx,
        cardHeight = buttonPx,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        screenCornerRadius = screenCornerRadius,
        // 起点必须是正圆：半径取按钮可见直径的一半
        startCornerRadiusDp = WelcomeLayout.buttonSize.value / 2f,
        onBackStart = {},
        onBack = {
            // 等过冲拉回原位再交回常驻层：收起一结束就卸载的话，回弹会被硬截断
            scope.launch {
                landJob[0]?.join()
                onClosed()
            }
        },
        autoStart = true,
        predictiveBackEnabled = { PredictiveBackSettings.enabled },
    )

    // 进度推给背景层；同时在收起末段触发一次过冲（snapshotFlow 订阅，不引起重组）
    LaunchedEffect(morph) {
        var lastProgress = morph.state.value.progress
        snapshotFlow { morph.state.value.progress }.collect { p ->
            bgProgress.floatValue = p
            val closing = p < lastProgress - 0.0001f
            lastProgress = p
            if (!closing || landStarted[0] || p > ARROW_LAND_TRIGGER) return@collect
            landStarted[0] = true
            // 位移向量正缩回原点，沿运动方向冲过终点 = 取反并归一化
            val dx = arrowOffset[0]
            val dy = arrowOffset[1]
            val len = sqrt(dx * dx + dy * dy)
            if (len > 1f) {
                landDir[0] = -dx / len
                landDir[1] = -dy / len
            }
            landJob[0] = scope.launch {
                land.snapTo(0f)
                land.animateTo(1f, tween(ARROW_LAND_OUT_MILLIS))
                land.animateTo(
                    0f,
                    spring(
                        dampingRatio = ARROW_LAND_DAMPING,
                        stiffness = ARROW_LAND_STIFFNESS,
                    ),
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // 与翻转层同一条规矩：压暗随进度单调加深，铺满那一帧直接不画
                    // （此时页面已不透明地盖住全屏，撤掉没有视觉影响，却能避免
                    //  这层直角矩形在页面圆角之外露出来）
                    val s = morph.state.value
                    if (s.progress < 1f && s.bgAlpha > 0.001f) {
                        drawRect(Color.Black.copy(alpha = s.bgAlpha))
                    }
                },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = morph.state.value
                    clip = false
                    transformOrigin = TransformOrigin(s.anchorX, 0.5f)
                    scaleX = s.scale
                    scaleY = s.scale
                    translationX = s.translationX
                    translationY = s.translationY
                }
                .fadeInMorphClip(morph, screenCornerRadius.maxRadius)
                // 页面底色常驻，内容另行淡入
                .background(OobeBackgroundBrush),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 40% 进度时内容已完全不透明
                    .graphicsLayer { alpha = fadeInAlpha(morph.state.value.progress) },
            ) {
                content { morph.close() }
            }
        }

        // 源图快照：页面就是从这个矩形展开的，所以按钮直接跟着「页面左上边缘」走，
        // 与形变完全同步；倍率只按自身尺寸放大（套整页 scale 会缩成卡片比例）
        WelcomeArrow(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = morph.state.value
                    // 页面顶边的位置（与页面层同一条变换曲线）
                    val pageTop = s.translationY + screenHeight * 0.5f * (1f - s.scale)
                    // 页面绕 anchorX 横向缩放，展开区域始终以页面横向中心对称，
                    // 按钮跟着这个中心走（贴左边缘会一路往左偏）
                    val pageCenterX =
                        s.translationX + (s.anchorX + (0.5f - s.anchorX) * s.scale) * screenWidth
                    val dx = pageCenterX - center.x
                    val dy = pageTop - cardTop
                    // 绘制期留一份当前位移，供过冲触发时求实际运动方向
                    arrowOffset[0] = dx
                    arrowOffset[1] = dy
                    // 过冲沿 landDir 冲过终点、再拉回；两个轴都跟着方向走，不是固定 +Y
                    val push = land.value * overshootPx
                    translationX = dx + push * landDir[0]
                    translationY = dy + push * landDir[1]
                    // 以按钮自身的顶边中点为锚点放大，顶在页面展开处
                    transformOrigin = TransformOrigin(
                        center.x / screenWidth,
                        cardTop / screenHeight,
                    )
                    val scale = 1f + 0.45f * s.progress
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - fadeInAlpha(s.progress)
                },
        )
    }
}
