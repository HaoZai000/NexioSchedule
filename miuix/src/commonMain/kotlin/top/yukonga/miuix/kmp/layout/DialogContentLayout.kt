// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.platform.isRenderEffectSupported
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.DecelerateEasing
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.MotionAwareText
import top.yukonga.miuix.kmp.material.LocalGlassDialogBlurDp
import top.yukonga.miuix.kmp.material.LocalMaterialNoneEnabled
import top.yukonga.miuix.kmp.material.currentSoftGlassLayer
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.LocalScreenCornerRadius
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalPredictiveBackEnabled
import top.yukonga.miuix.kmp.utils.getRoundedCorner
import top.yukonga.miuix.kmp.utils.rememberNavigationBack
import top.yukonga.miuix.kmp.window.WindowDialog

/** 对话框面板的柔光 shader 缓存键：尺寸与其他玻璃件不同，不能共用 uniform。 */
private const val SOFT_GLASS_DIALOG_KEY = "NexioSoftGlassLiveDialog"

/**
 * Internal shared layout logic for [OverlayDialog] and [WindowDialog].
 *
 * @param show Whether the dialog is currently shown.
 * @param titleColor The color of the title.
 * @param summaryColor The color of the summary.
 * @param backgroundColor The background color of the dialog.
 * @param outsideMargin The margin outside the dialog.
 * @param insideMargin The margin inside the dialog.
 * @param popupHost A composable that provides the dialog container (e.g., DialogLayout or Dialog).
 *   It receives the visibility state and the inner content composable.
 * @param modifier The modifier to be applied to the dialog content.
 * @param title The title of the dialog.
 * @param summary The summary of the dialog.
 * @param enableWindowDim Whether to enable window dimming.
 * @param onDismissRequest The callback when the dialog is dismissed.
 * @param onDismissFinished Invoked when the hide animation completes; not invoked if the hide
 *   is cancelled mid-flight (e.g., by [show] toggling back to true).
 * @param defaultWindowInsetsPadding Whether to apply default window insets padding.
 * @param topInset Optional top inset override. If null, calculated from window insets.
 * @param content The content of the dialog.
 */
@Suppress("ktlint:compose:modifier-not-used-at-root")
@Composable
internal fun DialogContentLayout(
    show: Boolean,
    titleColor: Color,
    summaryColor: Color,
    backgroundColor: Color,
    outsideMargin: DpSize,
    insideMargin: DpSize,
    popupHost: @Composable (visible: Boolean, content: @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    defaultWindowInsetsPadding: Boolean = true,
    topInset: Dp? = null,
    /**
     * 柔光玻璃背景。非 null 时对话框走 `blur + 柔光 shader` 的实时折射。
     *
     * 这是本 fork 的定制（上游 Miuix 的对话框只有纯色 surface）。
     * App 侧 71 处 [top.yukonga.miuix.kmp.overlay.OverlayDialog] 调用点都在传它，
     * 迁移时一度被漏掉，导致对话框的模糊整体消失 —— 现已恢复。
     */
    liquidGlassBackdrop: GlassBackdrop? = null,
    /** 背景色是否需要压暗/压浅到半透明（配合玻璃背景使用）。由调用方按当前主题算。 */
    isDark: Boolean = false,
    /**
     * 是否跟随系统预测性返回手势。
     *
     * 置 false 用于「不允许被返回键关掉」的对话框（如隐私政策同意弹窗）：
     * 手势仍被消费，但**不播跟手动画**，直接等松手后关闭。
     */
    enablePredictiveBackGesture: Boolean = true,
    content: @Composable () -> Unit,
) {
    val animationProgress = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val dimProgress = remember { Animatable(0f) }
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    val internalVisible = remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val keyboardController = LocalSoftwareKeyboardController.current
    val isLargeScreen = DialogDefaults.isLargeScreen()
    // Capture before entering the popup host so detached overlay compositions use the caller's radius.
    val screenCornerRadius = LocalScreenCornerRadius.current ?: getRoundedCorner()

    LaunchedEffect(show) {
        // Snapshot at launch so a window-resize crossing the breakpoint mid-animation does not
        // swap the spec or relaunch the effect.
        val largeAtStart = isLargeScreen
        if (show) {
            internalVisible.value = true
            if (enableWindowDim) {
                launch { dimProgress.animateTo(1f, tween(300, easing = DecelerateEasing(1.5f))) }
            }
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = if (largeAtStart) {
                    folmeSpring(damping = 0.9f, response = 0.3f)
                } else {
                    spring(dampingRatio = 0.88f, stiffness = 450f, visibilityThreshold = 0.0001f)
                },
            )
        } else {
            if (!internalVisible.value) return@LaunchedEffect
            if (imeInsets.getBottom(density) > 0) {
                keyboardController?.hide()
            }
            if (enableWindowDim) {
                launch { dimProgress.animateTo(0f, tween(250, easing = DecelerateEasing(1.5f))) }
            }
            animationProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260, easing = DecelerateEasing(1.5f)),
            )
            dimProgress.snapTo(0f)
            internalVisible.value = false
            currentOnDismissFinished?.invoke()
        }
    }

    if (!show && !internalVisible.value) return

    val coroutineScope = rememberCoroutineScope()
    val dimAlpha = remember { mutableFloatStateOf(1f) }
    val dialogHeightPx = remember { mutableIntStateOf(0) }
    val backProgress = remember { Animatable(0f) }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)

    val windowInfo = LocalWindowInfo.current

    val requestDismiss: () -> Unit = remember {
        { currentOnDismissRequest?.invoke() }
    }

    val resetGesture: suspend () -> Unit = remember {
        {
            backProgress.animateTo(0f, animationSpec = tween(durationMillis = 150))
            animate(dimAlpha.floatValue, 1f, animationSpec = tween(durationMillis = 150)) { value, _ ->
                dimAlpha.floatValue = value
            }
        }
    }

    popupHost(internalVisible.value) {
        // 预测性返回：commonMain 走本模块的 expect 封装（utils/NavigationBack.kt），Android 内部
        // 仍用 androidx.navigationevent，进度语义与原代码一致。handler 与 backProgress() 必须来自
        // 同一个 backState。全局开关在组合期取好：LocalPredictiveBackEnabled.current 是 @Composable，
        // 读在 LaunchedEffect 里编译不过。
        val backState = rememberNavigationBack()
        val predictiveBackEnabled = LocalPredictiveBackEnabled.current
        backState.handler(
            enabled = show,
            onBackCancelled = {
                coroutineScope.launch { resetGesture() }
            },
            onBackCompleted = { requestDismiss() },
        )

        LaunchedEffect(Unit) {
            // 不响应预测性返回手势时，连手势进度都不订阅，避免弹窗被拖动。
            // 全局开关（App 偏好）同样在此生效：关闭时不驱动跟手动画，但返回键仍被拦截、直接关闭。
            if (!enablePredictiveBackGesture || !predictiveBackEnabled) {
                return@LaunchedEffect
            }
            // 单个协程内收集，避免手势期间每帧进度都取消/重启 LaunchedEffect。
            snapshotFlow { backState.backProgress() }
                .collect { progress ->
                    if (progress != null) {
                        backProgress.snapTo(progress)
                        dimAlpha.floatValue = 1f - progress
                    }
                }
        }

        if (enableWindowDim) {
            val baseColor = MiuixTheme.colorScheme.windowDimming
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(baseColor.copy(alpha = baseColor.alpha * dimAlpha.floatValue * dimProgress.value * 0.6f))
                    },
            )
        }

        val contentModifier = modifier.graphicsLayer {
            val progress = animationProgress.value
            if (isLargeScreen) {
                val scale = 0.8f + 0.2f * progress
                scaleX = scale
                scaleY = scale
                alpha = progress
            } else {
                translationY = (1f - progress) * windowInfo.containerSize.height.toFloat()
                alpha = 1f
            }
        }

        DialogContent(
            screenCornerRadius = screenCornerRadius,
            title = title,
            titleColor = titleColor,
            summary = summary,
            summaryColor = summaryColor,
            backgroundColor = backgroundColor,
            outsideMargin = outsideMargin,
            insideMargin = insideMargin,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            backProgress = backProgress,
            dialogHeightPx = dialogHeightPx,
            onDismissRequest = requestDismiss,
            modifier = contentModifier,
            topInset = topInset,
            liquidGlassBackdrop = liquidGlassBackdrop,
            isDark = isDark,
            content = {
                CompositionLocalProvider(LocalDismissState provides requestDismiss) {
                    content()
                }
            },
        )
    }
}

@Suppress("ktlint:compose:modifier-not-used-at-root")
@Composable
internal fun DialogContent(
    title: String?,
    titleColor: Color,
    summary: String?,
    summaryColor: Color,
    backgroundColor: Color,
    outsideMargin: DpSize,
    insideMargin: DpSize,
    defaultWindowInsetsPadding: Boolean,
    backProgress: Animatable<Float, *>,
    dialogHeightPx: MutableIntState,
    onDismissRequest: (() -> Unit)?,
    modifier: Modifier = Modifier,
    screenCornerRadius: Dp,
    topInset: Dp? = null,
    liquidGlassBackdrop: GlassBackdrop? = null,
    isDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val windowHeight = with(density) { windowInfo.containerSize.height.toDp() }
    val isLargeScreen = DialogDefaults.isLargeScreen()
    val contentAlignment = remember(isLargeScreen) {
        if (isLargeScreen) Alignment.Center else Alignment.BottomCenter
    }
    val bottomCornerRadius = remember(screenCornerRadius, outsideMargin.width, isLargeScreen) {
        val offset = if (isLargeScreen) 0.dp else outsideMargin.width
        (screenCornerRadius - offset).coerceAtLeast(32.dp)
    }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)

    val safeDrawingInsets = WindowInsets.safeDrawing
    // 底部 inset 不在这里吃：贴底弹窗的底部间距统一交给下面的 bottomOutsideMargin 算，
    // 否则导航栏高度会被 windowInsetsPadding 和它各加一次。
    val dialogInsets = if (topInset != null) {
        safeDrawingInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            .union(WindowInsets(top = topInset))
    } else {
        safeDrawingInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    }

    // 贴底弹窗的底部间距，与底部浮动导航栏同一套算法
    val bottomOutsideMargin = if (isLargeScreen) {
        outsideMargin.height
    } else {
        maxOf(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 2.dp,
            22.dp,
        )
    }
    // 预测返回时把弹窗推出屏幕的额外位移 = 它到屏幕底的距离（dialogInsets 已不吃底部）。
    // 预转成 px，避免 graphicsLayer block 每帧调 toPx()。
    val extraBottomPaddingPx = remember(density, bottomOutsideMargin, isLargeScreen) {
        if (isLargeScreen) 0f else with(density) { bottomOutsideMargin.toPx() }
    }

    // 液态玻璃：实时折射 + 档位模糊（柔光玻璃档更薄）。能力探测用 backdrop 的
    // isRenderEffectSupported()（跨平台，含 skiko）；无材质档整体关玻璃，
    // 关掉后底色走半透明而非实心——模糊不可用也不降级纯色。
    val glassActive = liquidGlassBackdrop != null &&
        isRenderEffectSupported() &&
        !LocalMaterialNoneEnabled.current
    // 柔光玻璃由 App 在根部注入（shader + uniform 灌装）；null = 不启用，只剩半透明底色。
    val softGlass = currentSoftGlassLayer()
    val dialogBlur = LocalGlassDialogBlurDp.current
    val cornerRadiusPx = with(density) { bottomCornerRadius.toPx() }
    // drawBackdrop 按引用比较 effects；lambda 每帧新建会让节点每帧重新录制采样层
    val dialogEffects: com.haooz.softglass.backdrop.GlassEffectScope.() -> Unit =
        remember(dialogBlur, softGlass, cornerRadiusPx) {
            {
                if (softGlass != null) {
                    blur(dialogBlur.dp.toPx())
                    runtimeShaderEffect(
                        key = SOFT_GLASS_DIALOG_KEY,
                        shaderString = softGlass.shaderString,
                        uniformShaderName = "content",
                    ) {
                        // 对话框面板：BarFill + 悬浮大控件材质（App 根部单独注入一套 style）
                        softGlass.applyDialogUniforms(this, size.width, size.height, cornerRadiusPx)
                    }
                }
            }
        }
    val dialogShapeBlock: () -> Shape = remember(bottomCornerRadius) {
        { ContinuousRoundedRectangle(bottomCornerRadius) }
    }

    val contentModifier = modifier
        .widthIn(max = DialogDefaults.MaxWidth)
        .heightIn(max = if (isLargeScreen) windowHeight * (2f / 3f) else Dp.Unspecified)
        .onGloballyPositioned { coordinates ->
            dialogHeightPx.intValue = coordinates.size.height
        }
        .graphicsLayer {
            // Apply predictive back animation; branch inside the block so the modifier chain
            // produces a single graphicsLayer node instead of swapping nodes per recomposition.
            if (isLargeScreen) {
                val scale = 1f - (backProgress.value * 0.2f)
                scaleX = scale
                scaleY = scale
            } else {
                val maxOffset = if (dialogHeightPx.intValue > 0) {
                    dialogHeightPx.intValue.toFloat() + extraBottomPaddingPx
                } else {
                    500f
                }
                translationY = backProgress.value * maxOffset
            }
        }
        .pointerInput(Unit) {
            detectTapGestures { /* Consume click */ }
        }
        .clip(ContinuousRoundedRectangle(bottomCornerRadius))
        .then(
            if (glassActive) {
                Modifier.drawBackdrop(
                    backdrop = liquidGlassBackdrop,
                    shape = dialogShapeBlock,
                    effects = dialogEffects,
                )
            } else Modifier
        )
        .background(
            // 柔光时不压底色（会把折射盖住）；其余档保留原来的实色 / 半透明底色保证文字对比度。
            // 玻璃关（无材质档 / 无 backdrop）：实色。模糊不可用不挂半透明「假玻璃」。
            color = when {
                softGlass != null && glassActive -> Color.Transparent
                glassActive -> backgroundColor.copy(alpha = if (isDark) 0.87f else 0.78f)
                else -> backgroundColor
            },
            shape = ContinuousRoundedRectangle(bottomCornerRadius),
        )
        .padding(horizontal = insideMargin.width, vertical = insideMargin.height)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnDismiss?.invoke() },
                )
            }
            .semantics {
                onClick(label = "Dismiss") {
                    currentOnDismiss?.invoke()
                    true
                }
            }
            // Keep the scrim and outside-tap area full screen; constrain the entire dialog surface.
            .then(
                if (defaultWindowInsetsPadding) {
                    Modifier.windowInsetsPadding(dialogInsets).imePadding()
                } else {
                    Modifier.padding(top = topInset ?: safeDrawingInsets.asPaddingValues().calculateTopPadding())
                },
            )
            .padding(horizontal = outsideMargin.width)
            .padding(bottom = bottomOutsideMargin),
    ) {
        Column(
            modifier = contentModifier.align(contentAlignment),
        ) {
            title?.let {
                MotionAwareText(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    text = it,
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = titleColor,
                )
            }
            summary?.let {
                MotionAwareText(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    text = it,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    textAlign = TextAlign.Center,
                    color = summaryColor,
                )
            }
            content()
        }
    }
}

object DialogDefaults {
    /**
     * Window-size threshold above which the dialog is centered (instead of bottom-aligned)
     * and uses scale-in transitions. Roughly aligns with the Window Size Class
     * compact -> expanded boundary (840 dp width / 480 dp height).
     */
    @Composable
    internal fun isLargeScreen(): Boolean {
        val windowInfo = LocalWindowInfo.current
        val density = LocalDensity.current
        val windowWidth = with(density) { windowInfo.containerSize.width.toDp() }
        val windowHeight = with(density) { windowInfo.containerSize.height.toDp() }
        return windowHeight >= 480.dp && windowWidth >= 840.dp
    }

    /**
     * The default color of the title.
     */
    @Composable
    fun titleColor() = MiuixTheme.colorScheme.onBackground

    /**
     * The default color of the summary.
     */
    @Composable
    fun summaryColor() = MiuixTheme.colorScheme.onSurfaceSecondary

    /**
     * The default background color of the dialog.
     */
    @Composable
    fun backgroundColor() = MiuixTheme.colorScheme.background

    /**
     * The default upper bound on dialog content width. Keeps dialogs from stretching across
     * tablet / desktop windows.
     */
    val MaxWidth = 420.dp

    /**
     * The default margin outside the dialog.
     */
    val outsideMargin = DpSize(12.dp, 12.dp)

    /**
     * The default margin inside the dialog.
     */
    val insideMargin = DpSize(24.dp, 24.dp)
}
