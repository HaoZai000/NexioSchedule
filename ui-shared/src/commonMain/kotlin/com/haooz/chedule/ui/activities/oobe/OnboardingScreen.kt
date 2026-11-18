/**
 * 首次启动引导的页面层：语言页、设置页，以及两者之间的横向 3D 翻转。
 *
 * 语言页与设置页各自持有状态，互不共享；欢迎页与整体时序见 OnboardingFlow.kt。
 */
package com.haooz.chedule.ui.activities.oobe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.platform.PrivacyConsent
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.L10n
import com.haooz.chedule.i18n.LanguagePref
import com.haooz.chedule.i18n.LanguageStore
import com.haooz.chedule.i18n.MiscStr
import com.haooz.chedule.i18n.OnboardingStr
import com.haooz.chedule.i18n.PreferenceStr
import com.haooz.chedule.ui.activities.PrivacyPolicyScreen
import com.haooz.chedule.ui.activities.main.TabletTabTransitionSpec
import com.haooz.nexioui.textbutton.TextButton
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.components.text.miuix.Text
import com.haooz.nexioui.motion.ArcBendShape
import com.haooz.nexioui.motion.MORPH_BACKGROUND_DIM
import com.haooz.nexioui.motion.arcBend
import com.haooz.nexioui.motion.arcBendSupported
import com.haooz.chedule.ui.navigation.NAV_BG_DIM
import com.haooz.chedule.ui.navigation.NAV_ENTER_DURATION
import com.haooz.chedule.ui.navigation.NAV_ENTER_EASING
import com.haooz.chedule.ui.navigation.NAV_EXIT_DURATION
import com.haooz.chedule.ui.navigation.NAV_EXIT_EASING
import com.haooz.chedule.ui.navigation.NAV_PARALLAX
import com.haooz.chedule.ui.navigation.rememberNavTransitionState
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.KEY_TODAY_INFO_CARDS
import com.haooz.chedule.ui.utils.LocalLocationPermissionRequester
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.clipDuringTransition
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiiPx
import com.haooz.nexioui.window.toShape
import com.haooz.chedule.uishared.resources.Res
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.platform.isRenderEffectSupported
import io.github.alexzhirkevich.compottie.Lottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.checkbox.Checkbox
import com.haooz.nexioui.checkbox.CheckboxDefaults
import com.haooz.nexioui.dropdown.DropdownDefaults
import com.haooz.nexioui.dropdown.DropdownEntry
import com.haooz.nexioui.dropdown.DropdownItem
import androidx.compose.material3.Icon
import com.haooz.nexioui.scaffold.Scaffold
import com.haooz.nexioui.switch.Switch
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.ChevronBackward
import com.haooz.nexioui.icon.extended.ChevronForward
import com.haooz.nexioui.switchpreference.SwitchPreference
import com.haooz.nexioui.navigationback.NavigationBackHandlerFor
import kotlin.time.Duration.Companion.milliseconds

/** 与设置页共用的偏好文件名。 */
private const val PREFS_APP = "app_preferences"

/** 默认首页的持久化取值，与设置页保持一致。 */
private const val HOMEPAGE_TODAY = "今日"
private const val HOMEPAGE_SCHEDULE = "课程表"

/** 引导页正文配色：浅色渐变底，固定黑字。 */
private val TitleColor = Color(0xFF000000)
private val SummaryColor = Color(0x66000000)

/** 引导页底色。语言卡翻成整屏页时翻转层要原样重铺一遍，故提为共用。 */
internal val OobeBackgroundBrush = Brush.verticalGradient(
    listOf(Color(0xFFE3EEFF), Color(0xFFEFF6FF), Color(0xFFF8FBFF)),
)

/**
 * 底色对应的实色（取渐变中间那一档）。
 *
 * 玻璃按钮采样的是页面这一层，必须有东西可采；完成页把渐变让给流光后，
 * 下面还得垫一层不透明的实色，否则玻璃采到的是透明。
 */
private val OobeBackgroundBase = Color(0xFFEFF6FF)

// 引导的展示条件与隐私政策同意状态完全等价：未同意「当前版本」就先走引导。
// 递增 PrivacyConsent.CURRENT_VERSION 即让全部用户重新经历一次引导，

/** 单页形态，决定正文布局。 */
private enum class OobeKind { Language, Terms, Basic, Toggle, Finish }

/** 引导中的一页。 */
private class OobeStep(
    /** `files/lottie` 下的素材名（不含扩展名）；空串表示无插图。 */
    val asset: String,
    val title: L10n,
    val desc: L10n,
    val kind: OobeKind,
    /** 开关类页面的选项文案。 */
    val toggle: L10n? = null,
)

private val LanguageStep = OobeStep(
    "language",
    OnboardingStr.languageTitle,
    OnboardingStr.languageDesc,
    OobeKind.Language,
)

private val TermsStep = OobeStep(
    "terms",
    OnboardingStr.termsTitle,
    OnboardingStr.termsDesc,
    OobeKind.Terms,
)

private val BasicStep = OobeStep(
    "basic_settings",
    OnboardingStr.basicTitle,
    OnboardingStr.basicDesc,
    OobeKind.Basic,
)

private val LocationStep = OobeStep(
    "location",
    OnboardingStr.locationTitle,
    OnboardingStr.locationDesc,
    OobeKind.Toggle,
    OnboardingStr.locationSwitch,
)

private val FinishStep = OobeStep(
    "",
    OnboardingStr.finishTitle,
    OnboardingStr.finishDesc,
    OobeKind.Finish,
)

/**
 * 设置页的页序：位置服务只在开启天气时出现。
 *
 * 两份表都是常量，避免每次重组重建列表。
 */
private val SetupStepsWithLocation = listOf(TermsStep, BasicStep, LocationStep, FinishStep)
private val SetupStepsWithoutLocation = listOf(TermsStep, BasicStep, FinishStep)

private fun oobeSetupSteps(weatherOn: Boolean): List<OobeStep> =
    if (weatherOn) SetupStepsWithLocation else SetupStepsWithoutLocation

/**
 * 设置页自身的状态，与欢迎页、语言页互不影响。
 *
 * 开关初值直接读当前偏好，进引导时看到的就是实际生效值。
 */
internal class OobeSetupState {

    private val appPrefs = AppStorage.store(PREFS_APP)
    private val repository = CourseRepository.getInstance()

    var step by mutableIntStateOf(0)
    var agreed by mutableStateOf(false)
    var locationOn by mutableStateOf(true)

    var weatherOn by mutableStateOf(appPrefs.getBoolean(KEY_TODAY_INFO_CARDS, true))
    var predictiveBackOn by mutableStateOf(
        appPrefs.getBoolean(PredictiveBackSettings.KEY_PREDICTIVE_BACK_ANIMATION, true)
    )
    var defaultHomepage by mutableStateOf(repository.getDefaultHomepage())
    var materialLevel by mutableStateOf(AppMaterialSettings.level)

    /** 默认首页立刻落盘：选择结果要在引导结束后仍然生效。 */
    fun selectDefaultHomepage(value: String) {
        defaultHomepage = value
        repository.setDefaultHomepage(value)
    }
}

/** 当前是否停在最后的「欢迎使用」页。 */
internal fun OobeSetupState.isFinishStep(): Boolean {
    val steps = oobeSetupSteps(weatherOn)
    return steps[step.coerceIn(0, steps.lastIndex)].kind == OobeKind.Finish
}

/**
 * 语言页：只有两张语言卡，选中即通知宿主。
 *
 * @param hidden 正在翻出去的那张卡，由翻转层重画，原位留白
 * @param onPick 选中回调，携带该卡在窗口中的矩形，供翻转层起翻
 * @param onRootPositioned 页面上报自身 root 坐标，翻转层据此把卡片矩形换算到本地坐标
 */
@Composable
internal fun OnboardingLanguageScreen(
    hidden: LanguagePref?,
    onBack: () -> Unit,
    onPick: (LanguagePref, Rect) -> Unit,
    onRootPositioned: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 卡片矩形只在点击时读一次，用普通 Map：写快照状态只会带来无意义的失效与开销
    val cardRects = remember { mutableMapOf<LanguagePref, Rect>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { onRootPositioned(it.positionInRoot()) }
            .background(OobeBackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OobeActionBar(showBack = true, onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            StandardPage(LanguageStep) {
                LanguageCard(
                    hidden = hidden,
                    onSelect = { pref -> cardRects[pref]?.let { onPick(pref, it) } },
                    onBounds = { pref, rect -> cardRects[pref] = rect },
                )
            }
        }
    }
}

/**
 * 设置页：协议与声明 → 基础设置 → 位置服务（仅开启天气时）→ 完成页。
 *
 * 页内切换与平板端主 tab 同一套参数：新旧两页共存，按进度交叉缩放、淡变、模糊。
 *
 * @param onFinishRequest 点击「开始使用」，退场动画由宿主统一处理
 * @param onReachedFinishPage 完成页入场结束后回调，宿主据此开始加载主界面
 * @param settled 翻转是否已落定；未落定时不录玻璃层、底部按钮也不出现
 * @param mainContentLoaded 主界面是否已加载完成，完成页的「开始使用」等它加载完才出现
 */
@Composable
internal fun OnboardingSetupScreen(
    state: OobeSetupState,
    onBack: () -> Unit,
    onFinishRequest: () -> Unit,
    onReachedFinishPage: () -> Unit = {},
    settled: Boolean = true,
    mainContentLoaded: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // 关闭天气时定位页不出现、页数会变，索引在这里夹一次，避免越界
    val steps = oobeSetupSteps(state.weatherOn)
    val lastStep = steps.lastIndex
    val stepIndex = state.step.coerceIn(0, lastStep)
    val current = steps[stepIndex]

    // 切页进度：0 = 完全是旧页，1 = 完全是新页。shownStep 是正在显示的那一页，
    // fromStep 只在过渡期间有值，过渡结束即清空
    var shownStep by remember { mutableIntStateOf(stepIndex) }
    var fromStep by remember { mutableIntStateOf(-1) }
    // 每次换页都换一个从 0 起的新进度实例：新实例的第一帧就是 0。
    // 用 snapTo(0f) 复位会晚一帧生效，那一帧还会按旧值 1 画一次，看起来就是闪一下
    val initialShownStep = remember { shownStep }
    val stepProgress = remember(shownStep) {
        Animatable(if (shownStep == initialShownStep) 1f else 0f)
    }
    LaunchedEffect(stepIndex) {
        if (stepIndex == shownStep) return@LaunchedEffect
        fromStep = shownStep
        shownStep = stepIndex
    }
    LaunchedEffect(stepProgress) {
        stepProgress.animateTo(
            1f,
            tween(TabletTabTransitionSpec.DURATION_MS, easing = FastOutSlowInEasing),
        )
        fromStep = -1
    }

    // 完成页没有正文卡片：图标与字标直接铺在流光背景上，底色让给流光。
    // 判据必须取正在显示的页（shownStep）而不是目标页（stepIndex）：后者在点「继续」
    // 的那一帧就翻了，会把底色提前撤掉，而下一页还没淡入 —— 一样是闪
    val shown = steps[shownStep.coerceIn(0, lastStep)]
    val finishIncoming = shown.kind == OobeKind.Finish

    val requestLocationPermission = LocalLocationPermissionRequester.current
    val blurSupported = remember { isRenderEffectSupported() }

    // 完成页的按钮要等主界面加载完；其它页只看翻页是否落定
    val buttonVisible = settled && (current.kind != OobeKind.Finish || mainContentLoaded)
    val buttonAlpha by animateFloatAsState(
        targetValue = if (buttonVisible) 1f else 0f,
        animationSpec = tween(320),
        label = "oobePrimaryButtonAlpha",
    )

    // 完成页入场结束再通知宿主加载主界面，避免两套动画抢同一段帧
    LaunchedEffect(current.kind) {
        if (current.kind == OobeKind.Finish) {
            delay(FINISH_ANNOUNCE_DELAY_MILLIS.toLong().milliseconds)
            onReachedFinishPage()
        }
    }

    // 隐私政策在本层内就地展开，不走宿主路由，避免返回键被两处处理。
    // 转场与 AppNavHost 完全一致：上层 (1-p)W、下层 -parallax·p·W、黑幕 dim·p
    var policyPresent by remember { mutableStateOf(false) }
    var policyOpen by remember { mutableStateOf(false) }
    val policyProgress = rememberNavTransitionState(initialValue = 0f)
    val policyInFlight = remember(policyProgress) {
        { policyProgress.value > 0f && policyProgress.value < 1f }
    }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenCorners = rememberScreenCornerRadiiPx()
    val policyClipShape = remember(screenCorners, density) {
        if (screenCorners.maxRadius > 0f) screenCorners.toShape(density) else null
    }

    fun openPolicy() {
        policyPresent = true
        policyOpen = true
        policyProgress.snapTo(0f)
        scope.launch {
            policyProgress.animateTo(1f, tween(NAV_ENTER_DURATION, easing = NAV_ENTER_EASING))
        }
    }

    fun closePolicy() {
        policyOpen = false
        scope.launch {
            val finished =
                policyProgress.animateTo(0f, tween(NAV_EXIT_DURATION, easing = NAV_EXIT_EASING))
            if (finished) policyPresent = false
        }
    }

    // 页内返回：先回上一页，第一页交回宿主反向翻转；完成页不允许返回
    NavigationBackHandlerFor(
        enabled = !policyOpen && current.kind != OobeKind.Finish,
        onBackCompleted = { if (state.step == 0) onBack() else state.step-- },
        onBackCancelled = {},
    )
    NavigationBackHandlerFor(
        enabled = policyOpen,
        onBackCompleted = { closePolicy() },
        onBackCancelled = {},
    )

    // 下拉菜单的采样源：录下引导层自身（渐变底 + 卡片），弹窗按这层做玻璃
    val dropdownBackdrop = rememberLayerBackdrop()

    // 透明 Scaffold 只为给基础设置里的下拉菜单提供本地弹窗宿主（引导层在应用根 Scaffold 之外）
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 下层：引导页本身，随转场向左让出
            Box(
                Modifier
                    .fillMaxSize()
                    // 被完全盖住时停绘；层仍留在组合里，状态不丢
                    .drawWithContent { if (policyProgress.value < 0.999f) drawContent() }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.place(
                                (-NAV_PARALLAX * policyProgress.value * placeable.width).roundToInt(),
                                0,
                            )
                        }
                    },
            ) {
                // 整屏录一层：不透明的渐变底必须在层内，玻璃按钮才采得到东西。
                // 翻转期间不录——内容每帧都在重画，跟着重录纯属浪费
                Box(
                    Modifier.fillMaxSize().then(
                        if (settled) Modifier.layerBackdrop(dropdownBackdrop) else Modifier,
                    ),
                ) {
                    // 实色垫底：底部玻璃按钮采样的是这一层，整层透明会让它采到空气、
                    // 渲染成一块没有内容的玻璃。所有页面都垫，完成页叠在上面的流光也才有底
                    Box(Modifier.fillMaxSize().background(OobeBackgroundBase))

                    // 普通步骤的底色；完成页把它让给流光，随切页进度淡出而不是硬切
                    Box(
                        Modifier.fillMaxSize().drawBehind {
                            val a = if (finishIncoming) 1f - stepProgress.value else 1f
                            if (a > 0.001f) drawRect(OobeBackgroundBrush, alpha = a)
                        },
                    )

                    // 完成页：与欢迎页同一套流光 + 图标 / 字标，随切页进度整体淡入。
                    // 流光必须画在这一层里（而不是宿主那层），否则玻璃按钮又采不到
                    if (finishIncoming) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = stepProgress.value },
                        ) {
                            OnboardingWelcomeBackdrop()
                        }
                        OobeFinishHero(
                            desc = shown.desc,
                            progress = { stepProgress.value },
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        OobeActionBar(
                            showBack = current.kind != OobeKind.Finish,
                            onBack = {
                                if (stepIndex == 0) onBack() else state.step = stepIndex - 1
                            },
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            val from = fromStep
                            if (from in steps.indices) {
                                OobeStepPage(
                                    step = steps[from],
                                    state = state,
                                    backdrop = dropdownBackdrop,
                                    old = true,
                                    progress = { stepProgress.value },
                                    blurSupported = blurSupported,
                                    onOpenPrivacyPolicy = { openPolicy() },
                                )
                            }
                            OobeStepPage(
                                step = shown,
                                state = state,
                                backdrop = dropdownBackdrop,
                                old = false,
                                progress = { stepProgress.value },
                                blurSupported = blurSupported,
                                onOpenPrivacyPolicy = { openPolicy() },
                            )

                            // 同意行叠在本页最下方：属于页面内部，但不占滚动布局
                            if (shown.kind == OobeKind.Terms) {
                                TermsAgreeRow(
                                    agreed = state.agreed,
                                    onAgreedChange = { state.agreed = it },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        // 页面层 zIndex = 1，这里必须更高，否则整页透明区域会吃掉点击
                                        .zIndex(2f)
                                        .padding(bottom = 16.dp),
                                )
                            }
                        }

                        // 给悬浮在底部的按钮留出高度，避免遮住内容
                        Spacer(Modifier.height(50.dp + 38.dp))
                    }
                }

                OobePrimaryButton(
                    text = if (current.kind == OobeKind.Finish) {
                        OnboardingStr.start()
                    } else {
                        OnboardingStr.next()
                    },
                    backdrop = dropdownBackdrop,
                    enabled = buttonVisible && (current.kind != OobeKind.Terms || state.agreed),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // 出现时轻微上浮淡入，别在翻转尾巴上先冒出来
                        .graphicsLayer {
                            alpha = buttonAlpha
                            translationY = (1f - buttonAlpha) * 10.dp.toPx()
                        },
                    onClick = {
                        if (current.kind == OobeKind.Finish) {
                            onFinishRequest()
                        } else {
                            // 协议页点继续即视为同意隐私政策：同意入口只在这里
                            if (current.kind == OobeKind.Terms) {
                                PrivacyConsent.setConsented()
                            }
                            // 位置服务页：开关开着才弹系统权限，关着不打扰
                            if (current.kind == OobeKind.Toggle && state.locationOn) {
                                requestLocationPermission()
                            }
                            state.step = (stepIndex + 1).coerceAtMost(lastStep)
                        }
                    },
                )
            }

            if (policyPresent) {
                // 黑幕只压暗下层，不压当前页
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val a = if (policyProgress.value < 0.999f) {
                            NAV_BG_DIM * policyProgress.value
                        } else {
                            0f
                        }
                        if (a > 0.001f) drawRect(Color.Black.copy(alpha = a))
                    },
                )
                // 上层：隐私政策全文，转场期间裁屏幕圆角
                Box(
                    Modifier
                        .fillMaxSize()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                placeable.place(
                                    ((1f - policyProgress.value) * placeable.width).roundToInt(),
                                    0,
                                )
                            }
                        }
                        .clipDuringTransition(policyClipShape, policyInFlight),
                ) {
                    PolicyOverlay(onClose = { closePolicy() })
                }
            }
        }
    }
}

/** 完成页入场到「通知宿主加载主界面」之间的等待（毫秒）。 */
private const val FINISH_ANNOUNCE_DELAY_MILLIS = 420

/** 应用内《隐私政策》全文页，盖在引导之上；返回只关这一层。 */
@Composable
private fun PolicyOverlay(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            // 这一层成为命中目标，点击不会落到下面的引导页。
            // 只占位、不消费事件：消费会把页内滚动当成「被父级抢走」而取消
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                }
            },
    ) {
        // 与路由里的隐私政策页同一套壳：顶部材质模糊、标题与返回都由它提供
        DocumentPageScaffold(
            title = ChromeStr.privacyPolicy(),
            onBack = onClose,
        ) { scrollBehavior, _ ->
            PrivacyPolicyScreen(scrollBehavior = scrollBehavior)
        }
    }
}

/** 切页中的一页：与平板端主 tab 转场同参数（旧页缩到 0.98、模糊升起、淡出）。 */
@Composable
private fun OobeStepPage(
    step: OobeStep,
    state: OobeSetupState,
    backdrop: GlassBackdrop,
    old: Boolean,
    progress: () -> Float,
    blurSupported: Boolean,
    onOpenPrivacyPolicy: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 新页压在旧页之上，交叉时不被旧页盖住
            .zIndex(if (old) 0f else 1f)
            .graphicsLayer {
                val p = progress()
                val min = TabletTabTransitionSpec.SCALE_MIN
                alpha = if (old) 1f - p else p
                val scale = if (old) 1f + (min - 1f) * p else min + (1f - min) * p
                scaleX = scale
                scaleY = scale
                val blurDp = TabletTabTransitionSpec.MAX_BLUR_DP * if (old) p else (1f - p)
                renderEffect = if (blurSupported && blurDp > 0.01f) {
                    val blurPx = blurDp.dp.toPx()
                    BlurEffect(null, blurPx, blurPx, TileMode.Clamp)
                } else {
                    null
                }
            },
    ) {
        OobeStepContent(step, state, backdrop, onOpenPrivacyPolicy)
    }
}

/** 设置页里单页的正文。 */
@Composable
private fun OobeStepContent(
    page: OobeStep,
    state: OobeSetupState,
    backdrop: GlassBackdrop,
    onOpenPrivacyPolicy: () -> Unit,
) {
    when (page.kind) {
        OobeKind.Terms -> StandardPage(
            page,
            content = { TermsCard(onOpenPrivacyPolicy = onOpenPrivacyPolicy) },
        )

        OobeKind.Basic -> StandardPage(
            page,
            content = { BasicSettingsCard(state, backdrop) },
        )

        OobeKind.Toggle -> StandardPage(
            page,
            content = {
                ToggleCard(
                    label = page.toggle ?: page.title,
                    checked = state.locationOn,
                    onCheckedChange = { state.locationOn = it },
                )
            },
        )

        // 完成页的图标 / 字标由本层单独铺（不进正文卡片），这里不再画东西
        OobeKind.Finish -> Unit

        // 语言页是独立页面，不会走到这里
        OobeKind.Language -> Unit
    }
}

/**
 * 完成页：直接复用欢迎页的排版——图标落在 27%、字标落在 41%，下面就一句说明。
 *
 * 坐标按整屏算（不吃安全区、不进正文卡片），所以两页的图标与字标位置严格一致。
 *
 * @param progress 切页进度，整页按它淡入
 */
@Composable
private fun OobeFinishHero(
    desc: L10n,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // 进度只在绘制阶段读，淡入不触发重组
            .graphicsLayer { alpha = progress() },
    ) {
        OobeAppIcon(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.27f),
        )
        WelcomeWordmark(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.41f + 13.dp),
            alpha = { 1f },
            scale = { 1f },
            shimmer = true,
        )
        // 字标行高 47dp，往下留 27dp
        Text(
            text = desc(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.41f + 13.dp + 74.dp)
                .padding(horizontal = 32.dp),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            color = SummaryColor,
        )
    }
}

/**
 * 基础设置卡片：天气、预测性返回、默认首页、应用材质。
 *
 * 所有开关立即写盘——引导里改的就是真实偏好，不是草稿。
 */
@Composable
private fun BasicSettingsCard(
    state: OobeSetupState,
    backdrop: GlassBackdrop,
) {
    val appPrefs = remember { AppStorage.store(PREFS_APP) }
    // 与设置页同一套：容器透明，玻璃底交给采样层
    val glassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
            title = PreferenceStr.showWeather(),
            summary = PreferenceStr.showWeatherSummary(),
            checked = state.weatherOn,
            onCheckedChange = {
                state.weatherOn = it
                appPrefs.edit { putBoolean(KEY_TODAY_INFO_CARDS, it) }
            },
        )
        SwitchPreference(
            title = PreferenceStr.predictiveBack(),
            summary = PreferenceStr.predictiveBackSummary(),
            checked = state.predictiveBackOn,
            onCheckedChange = {
                state.predictiveBackOn = it
                PredictiveBackSettings.enabled = it
                appPrefs.edit {
                    putBoolean(PredictiveBackSettings.KEY_PREDICTIVE_BACK_ANIMATION, it)
                }
            },
        )
        OverlayDropdownMenu(
            title = PreferenceStr.defaultHomepage(),
            summary = PreferenceStr.defaultHomepageSummary(),
            entry = DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = CommonStr.today(),
                        selected = state.defaultHomepage == HOMEPAGE_TODAY,
                        onClick = { state.selectDefaultHomepage(HOMEPAGE_TODAY) },
                    ),
                    DropdownItem(
                        text = PreferenceStr.schedule(),
                        selected = state.defaultHomepage == HOMEPAGE_SCHEDULE,
                        onClick = { state.selectDefaultHomepage(HOMEPAGE_SCHEDULE) },
                    ),
                )
            ),
            collapseOnSelection = true,
            renderInRootScaffold = false,
            liquidGlassBackdrop = backdrop,
            dropdownColors = glassDropdownColors,
        )
        OverlayDropdownMenu(
            title = PreferenceStr.appMaterialEffect(),
            entry = DropdownEntry(
                items = AppMaterialSettings.entries.map { (value, label) ->
                    DropdownItem(
                        text = label,
                        selected = state.materialLevel == value,
                        onClick = {
                            AppMaterialSettings.apply(value)
                            state.materialLevel = AppMaterialSettings.level
                            appPrefs.edit {
                                putString(AppMaterialSettings.KEY_APP_MATERIAL, state.materialLevel)
                            }
                        },
                    )
                }
            ),
            // 平台锁定档位时不给切换入口
            enabled = AppMaterialSettings.lockedLevel == null,
            collapseOnSelection = true,
            renderInRootScaffold = false,
            liquidGlassBackdrop = backdrop,
            dropdownColors = glassDropdownColors,
        )
    }
}

// ── 语言卡 → 设置页的横向翻转 ───────────────────────────────────────────────
// 正反面共用同一套几何（OobeFlipFrame），p = 0.5 交接时两面的尺寸与位置完全重合：
//   正面：被点的语言卡，绕 Y 轴 0° → 180° 翻出去，同时宽高从卡片长到整屏；
//   背面：下一页按整屏排版，缩到当前可见窗口后 180° → 360° 翻回来；
//   横向 U 型侧摆：先摆出去再收回来，像把卡片拎起来翻了个面。

/** 翻转角：整张卡绕自身竖轴翻 180°。 */
private const val OobeFlipDegrees = 180f

/** 相机距离（乘 density）：越小透视越猛。 */
private const val OobeFlipCamera = 40f

/** 翻转动效时长（毫秒）。 */
internal const val OobeFlipMotionMs = 520

/** 起放大相位：从点击（p = 0）就开始从卡片长到整屏。 */
private const val OobeFlipGrowStart = 0f

/** U 型侧摆幅度（相对卡片宽度）。 */
private const val OobeFlipDipRatio = 0.5f

/** 卡片圆角（dp），与 miuix Card 一致。 */
private const val OobeFlipCardCornerDp = 20f

/** 第二段：翻转中上下的最大收缩量（dp），背面协议页专用，保持原值。 */
private const val OobeFlipMaxCurveDp = 110f

/**
 * 第一段：正面语言卡「左内凹 → 右鼓起」的最大弯曲幅度（相对半高，无量纲）。
 * 用比例而不是 dp：卡片高度远小于屏高，按绝对像素限幅会让两段的弯度对不上。
 */
private const val OobeFlipFrontBend = 0.35f

/** 降级「模糊切换」的起始模糊（dp）与缩放幅度；落定时都归零。 */
private const val OobeFallbackBlurDp = 12f
private const val OobeFallbackShrink = 0.04f

/** 弧度强度：起始与落定为直边，翻到中段最弯。两段曲线共用这条时间线。 */
private fun oobeFlipCurve(p: Float): Float = sin(PI.toFloat() * p)

/** 展开相位：平滑 0 → 1。正反面的宽高与圆角都读它，保证三者在同一条曲线上。 */
private fun oobeFlipGrow(p: Float): Float {
    val t = ((p - OobeFlipGrowStart) / (1f - OobeFlipGrowStart)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun oobeFlipWidth(p: Float, cardWidth: Float, screenWidth: Float): Float =
    cardWidth + (screenWidth - cardWidth) * oobeFlipGrow(p)

private fun oobeFlipHeight(p: Float, cardHeight: Float, screenHeight: Float): Float =
    cardHeight + (screenHeight - cardHeight) * oobeFlipGrow(p)

/**
 * 一帧的翻转几何，正反面共用。
 *
 * 每帧都要重算，用可变实例复用以避免逐帧分配。
 */
private class OobeFlipFrame {

    /** 卡片面在屏幕上的尺寸 */
    var width = 0f
    var height = 0f

    /** 背面图层等比缩放 */
    var scale = 1f

    /** 背面可见窗口（图层像素）与其中中心（屏幕像素） */
    var visibleWidth = 0f
    var visibleHeight = 0f
    var centerX = 0f
    var centerY = 0f

    /** 横向 U 型侧摆量 */
    var dip = 0f

    fun update(
        p: Float,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
    ) {
        val grown = oobeFlipGrow(p)
        width = oobeFlipWidth(p, cardWidth, screenWidth)
        height = oobeFlipHeight(p, cardHeight, screenHeight)
        // 缩放基准取宽度：grow = 0 时正好缩成卡片宽，grow = 1 时正好整屏
        scale = if (screenWidth > 0f) (width / screenWidth).coerceAtLeast(0.0001f) else 1f
        dip = cardWidth * OobeFlipDipRatio * sin(PI.toFloat() * p)
        visibleWidth = width / scale
        visibleHeight = height / scale
        // 中心从卡片中心平移到屏幕中心，同时叠 U 型侧摆
        val startCenterX = cardLeft + cardWidth / 2f
        val startCenterY = cardTop + cardHeight / 2f
        centerX = startCenterX + (screenWidth / 2f - startCenterX) * grown + dip
        centerY = startCenterY + (screenHeight / 2f - startCenterY) * grown
    }
}

/**
 * 语言卡 → 设置页的横向 3D 翻转层。
 *
 * @param progress 翻转进度，0 = 语言卡的裸卡态，1 = 设置页铺满整屏
 * @param cardRect 被点击的语言卡在窗口中的矩形
 * @param origin 语言页自身的 root 坐标，用于把 [cardRect] 换算到本层坐标
 * @param language 正面替身卡显示的语言文案
 */
@Composable
internal fun OobeFlipLayer(
    progress: () -> Float,
    cardRect: Rect,
    origin: Offset,
    language: LanguagePref,
    page: @Composable () -> Unit,
) {
    // AGSL/SkSL 是这段转场唯一的实现路径；能力探测（含 shader 编译）只在组合期做一次。
    val shaderAvailable = remember { arcBendSupported() }
    // 只有 Android < 33（无 AGSL）或 shader 编译失败才降级成模糊淡入切换；
    // 桌面 / iOS / 鸿蒙走 SkSL 跑同一份 shader，这里同样拿到 true。
    if (!shaderAvailable) {
        OobeBlurCrossfade(progress = progress, content = page)
        return
    }

    val densityScope = LocalDensity.current
    val cornerPx = with(densityScope) { OobeFlipCardCornerDp.dp.toPx() }
    val maxCurvePx = with(densityScope) { OobeFlipMaxCurveDp.dp.toPx() }
    val screenCorners = rememberScreenCornerRadiiPx()
    val frame = remember { OobeFlipFrame() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val screenWidthDp = with(densityScope) { screenWidth.toDp() }
        val screenHeightDp = with(densityScope) { screenHeight.toDp() }

        val cardLeft = cardRect.left - origin.x
        val cardTop = cardRect.top - origin.y
        val cardWidth = cardRect.width.coerceAtLeast(1f)
        val cardHeight = cardRect.height.coerceAtLeast(1f)

        val revealShape = remember(cardWidth, cardHeight, screenWidth, screenHeight, cornerPx) {
            OobeFlipRevealShape(
                progress = progress,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                screenCorners = screenCorners,
                cornerPx = cornerPx,
            )
        }
        // 正面卡圆角按两个轴各自反向补偿，见 OobeFlipCardShape
        val cardCornerShape = remember(cardWidth, cardHeight, screenWidth, screenHeight, cornerPx) {
            OobeFlipCardShape(progress, cardWidth, cardHeight, screenWidth, screenHeight, cornerPx)
        }

        // 背景压暗：翻转期间盖一层黑幕，退场的语言页被按下去，卡片面才立得住
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val p = progress()
                    if (p < 1f) {
                        val a = MORPH_BACKGROUND_DIM * p
                        if (a > 0.001f) drawRect(Color.Black.copy(alpha = a))
                    }
                },
        )

        // 背面：下一页整页原样画在这里（不另拼一份壳），落定后它就是这一页本身
        Box(
            modifier = Modifier
                .requiredSize(screenWidthDp, screenHeightDp)
                .graphicsLayer {
                    val p = progress()
                    frame.update(
                        p, cardLeft, cardTop, cardWidth, cardHeight, screenWidth, screenHeight,
                    )
                    cameraDistance = OobeFlipCamera * density
                    // 轴心跟着可见窗口走，翻转始终绕卡片自身中心
                    transformOrigin = TransformOrigin(
                        frame.visibleWidth / 2f / screenWidth,
                        frame.visibleHeight / 2f / screenHeight,
                    )
                    scaleX = frame.scale
                    scaleY = frame.scale
                    translationX = frame.centerX - frame.visibleWidth / 2f
                    translationY = frame.centerY - frame.visibleHeight / 2f
                    // 左边往后压、右边翘起来 = 负向角；背面接着翻回来
                    rotationY = -OobeFlipDegrees * (1f + p)
                    alpha = if (p > 0.5f) 1f else 0f
                }
                .arcBend(
                    shape = ArcBendShape.RightShrink,
                    amplitude = { maxCurvePx * oobeFlipCurve(progress()) },
                    visible = { progress() > 0.5f },
                )
                .clip(revealShape),
        ) { page() }

        // 正面：被点的那张语言卡。落定后仍留在页面上方并被拉到整屏大小，
        // 靠 alpha = 0 退出命中测试；否则它会把下面整页的点击全吃掉
        Box(
            modifier = Modifier
                .offset { IntOffset(cardLeft.roundToInt(), cardTop.roundToInt()) }
                .size(
                    with(densityScope) { cardWidth.toDp() },
                    with(densityScope) { cardHeight.toDp() },
                )
                .graphicsLayer {
                    val p = progress()
                    frame.update(
                        p, cardLeft, cardTop, cardWidth, cardHeight, screenWidth, screenHeight,
                    )
                    cameraDistance = OobeFlipCamera * density
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    // 左边往后压、右边翘起来
                    rotationY = -OobeFlipDegrees * p
                    // 与背面同一条尺寸曲线，p = 0.5 交接时两面重合
                    scaleX = frame.width / cardWidth
                    scaleY = frame.height / cardHeight
                    translationX = frame.dip
                    alpha = if (p < 0.5f) 1f else 0f
                }
                // 第一段独立曲线：左内凹、右鼓起，动画里随 oobeFlipCurve 逐渐展开。
                // 不再按卡片高度限幅，也不再与背面共用曲线。
                .arcBend(
                    shape = ArcBendShape.InwardOutward,
                    amplitude = { OobeFlipFrontBend * oobeFlipCurve(progress()) },
                    visible = { progress() <= 0.5f },
                )
                .clip(cardCornerShape),
        ) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OobeRow(
                    label = if (language == LanguagePref.ZH) {
                        OnboardingStr.chinese()
                    } else {
                        OnboardingStr.english()
                    },
                )
            }
        }
    }
}

/**
 * 兜底降级（Android < 33 或 shader 编译失败）：不做 3D 翻转和弯曲，
 * 只做引导页常见的「模糊淡入」切换
 */
@Composable
private fun OobeBlurCrossfade(
    progress: () -> Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val maxBlurPx = remember(density) { with(density) { OobeFallbackBlurDp.dp.toPx() } }
    val blurSupported = remember { isRenderEffectSupported() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val p = progress()
                // 压暗比翻转档轻一档；落定帧不画，避免露出黑色直角
                if (p < 1f) {
                    val a = MORPH_BACKGROUND_DIM * 0.5f * p
                    if (a > 0.001f) drawRect(Color.Black.copy(alpha = a))
                }
            }
            .graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                alpha = p
                val scale = 1f - OobeFallbackShrink * (1f - p)
                scaleX = scale
                scaleY = scale
                val radius = maxBlurPx * (1f - p)
                renderEffect = if (blurSupported && radius > 0.01f) {
                    BlurEffect(null, radius, radius, TileMode.Clamp)
                } else {
                    null
                }
            },
    ) {
        content()
    }
}

/**
 * 正面卡的圆角裁切。
 *
 * 圆角会被外层的 scaleX/scaleY 一起拉长：翻到中段卡片已拉长数倍，20dp 圆角会变成
 * 上百 dp，看起来像被啃掉一块。这里按两个轴各自反向补偿。
 */
private class OobeFlipCardShape(
    private val progress: () -> Float,
    private val cardWidth: Float,
    private val cardHeight: Float,
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val cornerPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val p = progress()
        val scaleX = oobeFlipWidth(p, cardWidth, screenWidth) / cardWidth.coerceAtLeast(1f)
        val scaleY = oobeFlipHeight(p, cardHeight, screenHeight) / cardHeight.coerceAtLeast(1f)
        return Outline.Rounded(
            RoundRect(
                Rect(Offset.Zero, size),
                CornerRadius(
                    (cornerPx / scaleX).coerceIn(0f, size.width / 2f),
                    (cornerPx / scaleY).coerceIn(0f, size.height / 2f),
                ),
            )
        )
    }
}

/**
 * 翻转落点页的可见窗口：宽度从卡片长到整屏，圆角同步收到屏幕圆角。
 *
 * 页面按整屏排版、由外层图层整体缩放，裁切发生在缩放之前，所以宽高与圆角都要先除以缩放。
 */
private class OobeFlipRevealShape(
    private val progress: () -> Float,
    private val cardWidth: Float,
    private val cardHeight: Float,
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val screenCorners: ScreenCornerRadiiPx,
    private val cornerPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val p = progress()
        // 与图层缩放同一条公式
        val scale = (oobeFlipWidth(p, cardWidth, screenWidth) / screenWidth.coerceAtLeast(1f))
            .coerceAtLeast(0.0001f)
        val width = oobeFlipWidth(p, cardWidth, screenWidth) / scale
        val height = oobeFlipHeight(p, cardHeight, screenHeight) / scale
        // 收尾是屏幕圆角；读不到屏幕圆角时以卡片圆角兜底
        val corners = screenCorners
            .coerceAtLeast(cornerPx)
            .interpolateFrom(cornerPx, oobeFlipGrow(p))
            .scaled(1f / scale)
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = width,
                bottom = height,
                topLeftCornerRadius = CornerRadius(corners.topLeft, corners.topLeft),
                topRightCornerRadius = CornerRadius(corners.topRight, corners.topRight),
                bottomRightCornerRadius = CornerRadius(corners.bottomRight, corners.bottomRight),
                bottomLeftCornerRadius = CornerRadius(corners.bottomLeft, corners.bottomLeft),
            )
        )
    }
}

/** 顶栏：44dp 返回热区，其余留空（引导没有可跳过的页面）。 */
@Composable
private fun OobeActionBar(
    showBack: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 44dp 热区裁成圆形，按压反馈也是圆的
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(enabled = showBack, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            if (showBack) {
                Icon(
                    imageVector = NexioIcons.ChevronBackward,
                    contentDescription = OnboardingStr.back(),
                    tint = TitleColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** 底部按钮：复用应用的玻璃文字按钮，左右 18dp、距底 38dp。 */
@Composable
private fun OobePrimaryButton(
    text: String,
    backdrop: GlassBackdrop,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        text = text,
        enabled = enabled,
        backdrop = backdrop,
        minHeight = 50.dp,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(bottom = 38.dp),
    )
}

/** 普通页骨架：48dp 留白 → 70dp 插图 → 标题块 → 内容区。 */
@Composable
private fun StandardPage(step: OobeStep, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        // 固定占位：Lottie 异步解析，解析完前什么都不画；让它自己撑开会让整页跳一下
        Box(Modifier.size(70.dp)) {
            if (step.asset.isNotEmpty()) {
                HyperLottie(asset = step.asset, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (step.kind == OobeKind.Language) {
                CyclingHeading(step)
            } else {
                Text(
                    text = step.title(),
                    fontSize = 32.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = TitleColor,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = step.desc(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    color = SummaryColor,
                )
            }
            Spacer(Modifier.height(9.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 语言页标题：中英文轮播。
 *
 * 先淡出、隐藏后再换语言、再淡入，两行不重叠。这里不走 [L10n.invoke]（那只按当前
 * 应用语言取一条），而是用 raw() 取原文，同一屏就能先显示中文再切到英文。
 */
@Composable
private fun CyclingHeading(step: OobeStep) {
    var showZh by remember { mutableStateOf(true) }
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(HEADING_HOLD_MILLIS.toLong().milliseconds)
            visible = false
            delay(HEADING_FADE_OUT_MILLIS.toLong().milliseconds)
            showZh = !showZh
            visible = true
            delay(HEADING_FADE_IN_MILLIS.toLong().milliseconds)
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            if (visible) HEADING_FADE_IN_MILLIS else HEADING_FADE_OUT_MILLIS,
        ),
        label = "oobeLanguageHeading",
    )
    // 中英两行宽度不同：撑满宽度并居中，切换时锚点在中心
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = step.title.raw(showZh),
            fontSize = 32.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = TitleColor,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = step.desc.raw(showZh),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            color = SummaryColor,
        )
    }
}

/** 语言标题轮播：每段停留时长与淡出/淡入时长（毫秒）。 */
private const val HEADING_HOLD_MILLIS = 3200
private const val HEADING_FADE_OUT_MILLIS = 240
private const val HEADING_FADE_IN_MILLIS = 420

/** 最小 60dp 的卡片行：17sp 文案 + 可选副标题 + 右侧控件。 */
@Composable
private fun OobeRow(
    label: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 17.sp, color = TitleColor)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 14.sp, color = SummaryColor)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun LanguageCard(
    hidden: LanguagePref?,
    onSelect: (LanguagePref) -> Unit,
    onBounds: (LanguagePref, Rect) -> Unit,
) {
    // 每种语言一张独立卡片，语言页没有选中态
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LanguageCardItem(LanguagePref.ZH, OnboardingStr.chinese, hidden, onSelect, onBounds)
        LanguageCardItem(LanguagePref.EN, OnboardingStr.english, hidden, onSelect, onBounds)
    }
}

@Composable
private fun LanguageCardItem(
    pref: LanguagePref,
    label: L10n,
    hidden: LanguagePref?,
    onSelect: (LanguagePref) -> Unit,
    onBounds: (LanguagePref, Rect) -> Unit,
) {
    Card(
        modifier = Modifier
            // 翻转层要按这张卡的真实矩形起翻
            .onGloballyPositioned { onBounds(pref, it.boundsInRoot()) }
            // 正在翻出去的那张由翻转层重画，原位留白，免得同时看到两张
            .graphicsLayer { alpha = if (hidden == pref) 0f else 1f },
    ) {
        OobeRow(label = label(), onClick = { onSelect(pref) }, trailing = {})
    }
}

/** 写入语言设置：全局语言 + 持久化一次完成。 */
internal fun selectLanguage(pref: LanguagePref) {
    AppLanguage.set(pref)
    LanguageStore.save(pref)
}

@Composable
private fun TermsCard(onOpenPrivacyPolicy: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        OobeRow(
            label = MiscStr.privacyPolicyLink(),
            onClick = onOpenPrivacyPolicy,
            trailing = {
                Icon(
                    imageVector = NexioIcons.ChevronForward,
                    contentDescription = null,
                    tint = Color(0x33000000),
                    modifier = Modifier.size(20.dp),
                )
            },
        )
    }
}

/** 协议页底部的同意行：复选框 + 文案整体居中，贴在「继续」按钮上方。 */
@Composable
private fun TermsAgreeRow(
    agreed: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            state = if (agreed) ToggleableState.On else ToggleableState.Off,
            onClick = { onAgreedChange(!agreed) },
            colors = CheckboxDefaults.colors(
                uncheckedBackgroundColor = Color(0xFFF2F2F2),
                uncheckedForegroundColor = SummaryColor,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = OnboardingStr.agreeTerms(),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = TitleColor,
        )
    }
}

@Composable
private fun ToggleCard(label: L10n, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        OobeRow(
            label = label(),
            trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        )
    }
}

/** Lottie JSON 文本缓存：切页回来不重复读资源。 */
private object LottieJson {

    private val cache = mutableMapOf<String, String>()

    suspend fun load(asset: String): String {
        cache[asset]?.let { return it }
        val text = Res.readBytes("files/lottie/$asset.json").decodeToString()
        cache[asset] = text
        return text
    }
}

/** 原样播放 APK 里的 Lottie 素材，只播一次并停在末帧。 */
@Composable
private fun HyperLottie(asset: String, modifier: Modifier = Modifier) {
    val json by produceState<String?>(initialValue = null, asset) {
        value = runCatching { LottieJson.load(asset) }.getOrNull()
    }
    val text = json ?: return

    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(text))
    val painter = rememberLottiePainter(
        composition = composition,
        iterations = 1,
        useCompositionFrameRate = true,
        // 动画里的图层会画到合成边界之外，默认会被裁掉
        clipToCompositionBounds = false,
    )
    Lottie(
        painter = painter,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
