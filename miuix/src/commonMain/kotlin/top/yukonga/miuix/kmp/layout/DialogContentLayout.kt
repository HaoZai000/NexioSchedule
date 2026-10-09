// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.captionBarPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.edgelight.edgeLight
import com.kyant.backdrop.edgelight.rememberDefaultEdgeLight
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.DecelerateEasing
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.rememberNavigationBack
import top.yukonga.miuix.kmp.utils.LocalPredictiveBackEnabled
import top.yukonga.miuix.kmp.utils.getRoundedCorner
import top.yukonga.miuix.kmp.window.WindowDialog

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
     * 液态玻璃背景。非 null 时对话框走 `vibrancy + blur(24dp)` 的实时折射，
     * 并叠一层描边高光。
     *
     * 这是本 fork 的定制（上游 Miuix 的对话框只有纯色 surface）。
     * App 侧 71 处 [top.yukonga.miuix.kmp.overlay.OverlayDialog] 调用点都在传它，
     * 迁移时一度被漏掉，导致对话框的模糊与描边整体消失 —— 现已恢复。
     */
    liquidGlassBackdrop: Backdrop? = null,
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
        // 预测性返回：commonMain 走本模块的 expect 封装（utils/NavigationBack.kt），
        // Android 实现内部仍用 androidx.navigationevent，进度语义与原代码一致。
        // handler 与 backProgress() 必须来自同一个 backState —— 拆开就拿不到手势进度。
        //
        // 全局开关要在组合期取好：LocalPredictiveBackEnabled.current 是 @Composable，
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
            // 全局开关（App 偏好）同样在这里生效：关闭时不驱动跟手动画，
            // 但返回键仍被拦截、直接关闭。
            if (!enablePredictiveBackGesture || !predictiveBackEnabled) {
                return@LaunchedEffect
            }
            // Collect inside a single coroutine so the per-frame progress ticks during a
            // back gesture do not cancel/relaunch the LaunchedEffect on every progress update.
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
                        drawRect(baseColor.copy(alpha = baseColor.alpha * dimAlpha.floatValue * dimProgress.value))
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
                translationY = (1f - progress) * windowInfo.containerDpSize.height.toPx()
                alpha = 1f
            }
        }

        DialogContent(
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
    topInset: Dp? = null,
    liquidGlassBackdrop: Backdrop? = null,
    isDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val windowHeight = windowInfo.containerDpSize.height
    val isLargeScreen = DialogDefaults.isLargeScreen()
    val contentAlignment = remember(isLargeScreen) {
        if (isLargeScreen) Alignment.Center else Alignment.BottomCenter
    }
    val roundedCorner = getRoundedCorner()
    val bottomCornerRadius = remember(roundedCorner, outsideMargin.width, isLargeScreen) {
        val offset = if (isLargeScreen) 0.dp else outsideMargin.width
        (roundedCorner - offset).coerceAtLeast(32.dp)
    }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)

    val calculatedTopInset = if (topInset != null) {
        topInset
    } else {
        val statusBars = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val captionBar = WindowInsets.captionBar.asPaddingValues().calculateTopPadding()
        val displayCutout = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        maxOf(statusBars, captionBar, displayCutout)
    }

    // Predictive-back translation pad (small-screen only). Pre-converted to px so the per-frame
    // graphicsLayer block does not call asPaddingValues() / toPx() each invalidation.
    val bottomPadding = if (isLargeScreen) {
        0.dp
    } else {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
    }
    val extraBottomPaddingPx = remember(density, bottomPadding, outsideMargin.height, isLargeScreen) {
        if (isLargeScreen) 0f else with(density) { (bottomPadding + outsideMargin.height).toPx() }
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
            // 液态玻璃：实时折射 + 24dp 模糊。原判断是 Build.VERSION.SDK_INT >= 33，
            // 改用 backdrop 的能力探测 isRenderEffectSupported()（跨平台，含 skiko）。
            if (liquidGlassBackdrop != null && isRenderEffectSupported()) {
                Modifier.drawBackdrop(
                    backdrop = liquidGlassBackdrop,
                    shape = { ContinuousRoundedRectangle(bottomCornerRadius) },
                    effects = {
                        vibrancy()
                        blur(24.dp.toPx())
                    },
                    highlight = null,
                )
            } else Modifier
        )
        .edgeLight(
            shape = ContinuousRoundedRectangle(bottomCornerRadius),
            edgeLight = rememberDefaultEdgeLight(baseColor = backgroundColor),
        )
        .background(
            color = if (liquidGlassBackdrop != null && isRenderEffectSupported()) {
                // 玻璃层之上再压一层半透明底色，保证文字对比度；深色下压得更暗
                backgroundColor.copy(alpha = if (isDark) 0.87f else 0.82f)
            } else {
                backgroundColor
            },
            shape = ContinuousRoundedRectangle(bottomCornerRadius),
        )
        .padding(horizontal = insideMargin.width, vertical = insideMargin.height)

    Box(
        modifier = Modifier
            .then(
                if (defaultWindowInsetsPadding) {
                    Modifier
                        .imePadding()
                        .navigationBarsPadding()
                        .captionBarPadding()
                } else {
                    Modifier
                },
            )
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
            .padding(horizontal = outsideMargin.width)
            .padding(top = calculatedTopInset, bottom = outsideMargin.height),
    ) {
        Column(
            modifier = contentModifier.align(contentAlignment),
        ) {
            title?.let {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    text = it,
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = titleColor,
                )
            }
            summary?.let {
                Text(
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
        val windowWidth = windowInfo.containerDpSize.width
        val windowHeight = windowInfo.containerDpSize.height
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
