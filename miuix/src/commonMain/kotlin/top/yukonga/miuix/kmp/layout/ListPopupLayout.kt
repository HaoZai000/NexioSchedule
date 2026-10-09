// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.interactive.DropdownPanelDragSelectState
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.SinOutEasing
import top.yukonga.miuix.kmp.basic.ListPopupContent
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupAnchors
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PopupPositionResult
import top.yukonga.miuix.kmp.basic.rememberListPopupLayoutInfo
import top.yukonga.miuix.kmp.basic.resolvePopupAnchors
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.utils.LocalPredictiveBackEnabled
import top.yukonga.miuix.kmp.utils.rememberNavigationBack
import kotlin.math.roundToInt

// 进场：fraction spring 0.77/220，锚点迁移 spring 0.77/420（比尺寸更快到 1）
private val FractionEnterAnimSpec = spring<Float>(dampingRatio = 0.77f, stiffness = 220f, visibilityThreshold = 0.0001f)
// 退场：fraction spring 0.85/650，锚点迁移 tween(340, CubicBezierEasing(0,0,0,1))
private val FractionExitAnimSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 650f, visibilityThreshold = 0.0001f)
private val OriginEnterAnimSpec = spring<Float>(dampingRatio = 0.77f, stiffness = 420f, visibilityThreshold = 0.0001f)
private val OriginExitAnimSpec = tween<Float>(340, easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f))
// 预测性返回取消后回到展开态
private val BackCancelAnimSpec = tween<Float>(150)
private val DimEnterAnimSpec = tween<Float>(durationMillis = 200, easing = SinOutEasing)
private val DimExitAnimSpec = tween<Float>(durationMillis = 300, easing = SinOutEasing)
private val LocalMinPopupHeight = 50.dp

/**
 * 弹窗玻璃盒为容纳 spring 过冲而额外撑出的余量（与 ListPopupContent 的 overshootRoom 一致）。
 * placeable 会因此每边比真实面板大一份，定位时需扣掉。
 */
private val PopupOvershootRoom = 14.dp

/** 弹窗外层为阴影预留的留白（与 ListPopupContent 的 shadowPadding 一致） */
private val PopupShadowPadding = 24.dp

private fun PopupPositionProvider.Align.resolve(layoutDirection: LayoutDirection): PopupPositionProvider.Align {
    if (layoutDirection == LayoutDirection.Ltr) return this
    return when (this) {
        PopupPositionProvider.Align.Start -> PopupPositionProvider.Align.End
        PopupPositionProvider.Align.End -> PopupPositionProvider.Align.Start
        PopupPositionProvider.Align.TopStart -> PopupPositionProvider.Align.TopEnd
        PopupPositionProvider.Align.TopEnd -> PopupPositionProvider.Align.TopStart
        PopupPositionProvider.Align.BottomStart -> PopupPositionProvider.Align.BottomEnd
        PopupPositionProvider.Align.BottomEnd -> PopupPositionProvider.Align.BottomStart
    }
}

/**
 * 下拉弹窗相对锚点的补偿量。
 *
 * **必须是 [Dp]，不能写死 px**：px 是设备相关的，同一个数值在 3x 机型上等于 8dp，
 * 在 4x 机型上只有 6dp（差 2dp），在 2.75x 上是 8.7dp —— 换机就错位。
 * 下面两个值就是原先 `24`/`18` px 在 3x 机型上的等效 dp，视觉不变。
 */
private val LiquidDropdownAnchorOffsetX = 7.dp
private val LiquidDropdownAnchorOffsetY = 5.dp

/**
 * 自定义下拉定位提供者（带偏移量）。
 *
 * 偏移量以 [Dp] 声明、在组合期按当前 [LocalDensity] 换算成 px，
 * 因此在任何 density / fontScale 下都与设计值一致。
 *
 * @param anchorOffsetX 水平补偿（弹窗右缘向右让出的量）
 * @param anchorOffsetY 竖直补偿（弹窗上缘相对锚点上缘的让出量）
 */
@Composable
fun liquidDropdownPositionProvider(
    anchorOffsetX: Dp = LiquidDropdownAnchorOffsetX,
    anchorOffsetY: Dp = LiquidDropdownAnchorOffsetY,
): PopupPositionProvider {
    val density = LocalDensity.current
    val offsetXDelta = with(density) { anchorOffsetX.roundToPx() }
    val offsetYDelta = with(density) { anchorOffsetY.roundToPx() }

    return remember(offsetXDelta, offsetYDelta) {
        object : PopupPositionProvider {
            private val margins = PaddingValues(horizontal = 0.dp, vertical = 0.dp)

            override fun calculatePosition(
                anchorBounds: IntRect,
                windowBounds: IntRect,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
                popupMargin: IntRect,
                alignment: PopupPositionProvider.Align,
            ): PopupPositionResult {
                val offsetX = if (alignment.resolve(layoutDirection) == PopupPositionProvider.Align.End) {
                    anchorBounds.right - popupContentSize.width - popupMargin.right + offsetXDelta
                } else {
                    anchorBounds.left + popupMargin.left + offsetXDelta
                }

                val spaceBelow = windowBounds.bottom - anchorBounds.bottom
                val spaceAbove = anchorBounds.top - windowBounds.top
                val offsetY: Int
                val showBelow: Boolean
                val showAbove: Boolean
                if (spaceBelow > popupContentSize.height) {
                    offsetY = anchorBounds.top - offsetYDelta
                    showBelow = true
                    showAbove = false
                } else if (spaceAbove > popupContentSize.height) {
                    offsetY = anchorBounds.bottom - popupContentSize.height + offsetYDelta
                    showBelow = false
                    showAbove = true
                } else {
                    offsetY = anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2
                    showBelow = false
                    showAbove = false
                }

                val clampedOffset = IntOffset(
                    x = offsetX.coerceIn(
                        windowBounds.left,
                        (windowBounds.right - popupContentSize.width - popupMargin.right).coerceAtLeast(windowBounds.left),
                    ),
                    y = offsetY.coerceIn(
                        (windowBounds.top + popupMargin.top).coerceAtMost(windowBounds.bottom - popupContentSize.height - popupMargin.bottom),
                        windowBounds.bottom - popupContentSize.height - popupMargin.bottom,
                    ),
                )
                return PopupPositionResult(clampedOffset, showBelow, showAbove)
            }

            override fun getMargins(): PaddingValues = margins
        }
    }
}

/**
 * 弹窗布局的核心逻辑。
 *
 * @param show 是否显示弹窗
 * @param popupHost 弹窗容器
 * @param popupModifier 弹窗修饰符
 * @param popupPositionProvider 弹窗位置提供者
 * @param alignment 弹窗对齐方式
 * @param enableWindowDim 是否启用背景变暗
 * @param onDismissRequest 关闭弹窗的回调
 * @param onDismissFinished 关闭动画完成后的回调
 * @param maxHeight 弹窗最大高度
 * @param minWidth 弹窗最小宽度
 * @param content 弹窗内容
 */
@Composable
fun ListPopupLayout(
    show: Boolean,
    popupHost: @Composable (visible: Boolean, content: @Composable () -> Unit) -> Unit,
    popupModifier: Modifier = Modifier,
    popupPositionProvider: PopupPositionProvider = liquidDropdownPositionProvider(),
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.Start,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    maxHeight: Dp? = null,
    minWidth: Dp = ListPopupDefaults.MinWidth,
    liquidGlassBackdrop: Backdrop? = null,
    onFractionProgress: ((Float) -> Unit)? = null,
    collapseSize: IntSize? = null,
    collapseExtra: DpSize = DpSize.Zero,
    collapseContent: (@Composable () -> Unit)? = null,
    /** 跟手滑选状态。透传给 [ListPopupContent]；为 null 时不启用。 */
    dragSelectState: DropdownPanelDragSelectState? = null,
    content: @Composable () -> Unit,
) {
    val fractionProgress = remember { Animatable(0f) }
    // 锚点迁移进度：比尺寸更快到 1，先「移向面板中心」再放大
    val originProgress = remember { Animatable(0f) }
    val dimProgress = remember { Animatable(0f) }
    // 预测性返回手势进度（取消时据此决定是否要恢复）
    val backProgress = remember { Animatable(0f) }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    val internalVisible = remember { mutableStateOf(false) }
    var popupContentSize by remember { mutableStateOf(IntSize.Zero) }
    var hostPositionInWindow by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(show) {
        if (show) {
            internalVisible.value = true
            launch { fractionProgress.animateTo(1f, FractionEnterAnimSpec) }
            launch { originProgress.animateTo(1f, OriginEnterAnimSpec) }
            if (enableWindowDim) {
                launch { dimProgress.animateTo(1f, DimEnterAnimSpec) }
            }
        } else {
            if (!internalVisible.value) return@LaunchedEffect
            launch { fractionProgress.animateTo(0f, FractionExitAnimSpec) }
            if (enableWindowDim) {
                launch { dimProgress.animateTo(0f, DimExitAnimSpec) }
            }
            // 这里必须「等」动画跑完（不能立刻 snapTo，否则会把上面的动画取消掉）；
            // 用锚点 tween 兜住，结束后再收尾
            originProgress.animateTo(0f, OriginExitAnimSpec)
            fractionProgress.snapTo(0f)
            originProgress.snapTo(0f)
            dimProgress.snapTo(0f)
            internalVisible.value = false
            currentOnDismissFinished?.invoke()
        }
    }

    val currentOnFractionProgress by rememberUpdatedState(onFractionProgress)
    LaunchedEffect(Unit) {
        currentOnFractionProgress?.let { callback ->
            snapshotFlow { fractionProgress.value }
                .collect { callback(it) }
        }
    }

    if (!show && !internalVisible.value) return

    var parentBounds by remember { mutableStateOf(IntRect.Zero) }

    Spacer(
        modifier = Modifier
            .onGloballyPositioned { childCoordinates ->
                childCoordinates.parentLayoutCoordinates?.let { parentLayoutCoordinates ->
                    val positionInWindow = parentLayoutCoordinates.positionInWindow()
                    parentBounds = IntRect(
                        left = positionInWindow.x.toInt(),
                        top = positionInWindow.y.toInt(),
                        right = positionInWindow.x.toInt() + parentLayoutCoordinates.size.width,
                        bottom = positionInWindow.y.toInt() + parentLayoutCoordinates.size.height,
                    )
                }
            },
    )

    if (parentBounds == IntRect.Zero) return

    val layoutInfo = rememberListPopupLayoutInfo(
        alignment = alignment,
        popupPositionProvider = popupPositionProvider,
        parentBounds = parentBounds,
        popupContentSize = popupContentSize,
    )

    // layout 阶段算出的真实锚点，与 calculatedOffset 使用同一个 measuredSize，
    // 避免 composition 阶段的 popupContentSize 与 layout 阶段的 measuredSize 不一致。
    //
    // 用 PopupAnchors 而非 state：state 要下一帧才生效，draw 会先用错一帧。
    val popupAnchors = remember { PopupAnchors() }

    val requestDismiss: () -> Unit = remember {
        { currentOnDismiss?.invoke() }
    }

    popupHost(internalVisible.value) {
        val coroutineScope = rememberCoroutineScope()
        // 预测性返回：commonMain 走本模块的 expect 封装（utils/NavigationBack.kt），
        // Android 实现内部仍用 androidx.navigationevent，进度语义与原代码一致。
        val navBackState = rememberNavigationBack()
        val predictiveBackEnabled = LocalPredictiveBackEnabled.current

        // 手势进度驱动 fraction/origin/dim 全部收敛到 0（弹窗缩回消失），
        // 取消恢复、完成关闭；低版本 NavigationBackHandler 自动退化为立即关闭
        navBackState.handler(
            enabled = show,
            onBackCancelled = {
                coroutineScope.launch {
                    if (backProgress.value > 0f) {
                        fractionProgress.animateTo(1f, BackCancelAnimSpec)
                        originProgress.animateTo(1f, BackCancelAnimSpec)
                        if (enableWindowDim) {
                            dimProgress.animateTo(1f, DimEnterAnimSpec)
                        }
                        backProgress.snapTo(0f)
                    }
                }
            },
            onBackCompleted = {
                requestDismiss()
            },
        )

        // 逐帧收集返回手势进度（单独协程，避免手势期间每帧取消/重启 LaunchedEffect）
        LaunchedEffect(Unit) {
            snapshotFlow { navBackState.backProgress() }
                .collect { progress ->
                    // 预测性返回动画开关：关闭时不驱动跟随动画（返回仍被拦截，直接关闭）
                    if (progress != null && predictiveBackEnabled) {
                        backProgress.snapTo(progress)
                        fractionProgress.snapTo(1f - progress)
                        originProgress.snapTo(1f - progress)
                        if (enableWindowDim) {
                            dimProgress.snapTo(1f - progress)
                        }
                    }
                }
        }

        // 玻璃盒余量 + 外层阴影留白的总 chrome（每边一份）：
        // maxHeight 和定位尺寸都按它扣减，两者必须一致，否则弹窗会整体偏移。
        val chromePaddingPx = with(LocalDensity.current) {
            (PopupShadowPadding + PopupOvershootRoom).toPx()
        }
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = popupModifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        hostPositionInWindow = coordinates.positionInWindow()
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { requestDismiss() },
                        )
                    }
                    .layout { measurable, constraints ->
                        val windowBounds = layoutInfo.windowBounds
                        val popupMargin = layoutInfo.popupMargin
                        val minHeightPx = LocalMinPopupHeight.roundToPx()
                        // chrome = 弹窗外一圈非面板留白（每边 shadowPadding + overshootRoom）。
                        // maxHeight、measuredSize、place 三处必须扣同一个量，少扣一处就整体偏移。
                        val chromePadPx = (2 * chromePaddingPx).roundToInt()
                        val chromeOffPx = chromePaddingPx.roundToInt()
                        val placeable = measurable.measure(
                            constraints.copy(
                                maxHeight = (
                                    maxHeight?.roundToPx()?.coerceAtLeast(minHeightPx)
                                        ?: (windowBounds.height - popupMargin.top - popupMargin.bottom)
                                            .coerceAtLeast(minHeightPx)
                                    ).minus(chromePadPx).coerceAtLeast(minHeightPx),
                                minHeight = if (minHeightPx <= constraints.maxHeight) minHeightPx else constraints.maxHeight,
                                maxWidth = constraints.maxWidth,
                                minWidth = minWidth.roundToPx().coerceAtMost(constraints.maxWidth),
                            ),
                        )
                        val measuredSize = IntSize(
                            (placeable.width - chromePadPx).coerceAtLeast(0),
                            (placeable.height - chromePadPx).coerceAtLeast(0),
                        )

                        val positionResult = popupPositionProvider.calculatePosition(
                            parentBounds,
                            windowBounds,
                            layoutDirection,
                            measuredSize,
                            popupMargin,
                            alignment,
                        )
                        val calculatedOffset = positionResult.offset

                        val (_, transformOrigin) = resolvePopupAnchors(
                            positionResult, calculatedOffset, measuredSize, parentBounds, alignment, layoutDirection,
                        )
                        // 写普通 holder 而非 state：state 要下一帧才生效，draw 会先用错一帧，
                        // 导致 panelRect 的锚点分支选错（位置差一整个面板宽）。
                        popupAnchors.right = transformOrigin.pivotFractionX >= 0.5f
                        popupAnchors.bottom = transformOrigin.pivotFractionY >= 0.5f

                        val adjustedOffset = IntOffset(
                            x = calculatedOffset.x - hostPositionInWindow.x.toInt() - chromeOffPx,
                            y = calculatedOffset.y - hostPositionInWindow.y.toInt() - chromeOffPx,
                        )

                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(adjustedOffset)
                        }
                    },
            ) {
                ListPopupContent(
                    popupContentSize = popupContentSize,
                    onPopupContentSizeChange = { popupContentSize = it },
                    fractionProgress = { fractionProgress.value },
                    originProgress = { originProgress.value },
                    localTransformOrigin = layoutInfo.localTransformOrigin,
                    anchors = popupAnchors,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    collapseSize = collapseSize,
                    collapseExtra = collapseExtra,
                    collapseContent = collapseContent,
                    // show 为 true 即进场；退场动画期间 show 已是 false 但仍在渲染，
                    // 正好对应「淡出用晚一档」的时机。
                    isEntering = show,
                    dragSelectState = dragSelectState,
                    content = {
                        CompositionLocalProvider(LocalDismissState provides requestDismiss) {
                            content()
                        }
                    },
                )
            }
        }
    }
}
