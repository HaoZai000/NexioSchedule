// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.layout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ListPopupContent
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.rememberListPopupLayoutInfo
import top.yukonga.miuix.kmp.theme.LocalDismissState

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
internal fun ListPopupLayout(
    show: Boolean,
    popupHost: @Composable (visible: Boolean, content: @Composable () -> Unit) -> Unit,
    popupModifier: Modifier = Modifier,
    popupPositionProvider: PopupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.Start,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    maxHeight: Dp? = null,
    minWidth: Dp = ListPopupDefaults.MinWidth,
    liquidGlassBackdrop: Backdrop? = null,
    content: @Composable () -> Unit,
) {
    val fractionProgress = remember { Animatable(0f) }
    val alphaProgress = remember { Animatable(0f) }
    val dimProgress = remember { Animatable(0f) }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    val internalVisible = remember { mutableStateOf(false) }
    var popupContentSize by remember { mutableStateOf(IntSize.Zero) }
    var hostPositionInWindow by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(show) {
        if (show) {
            internalVisible.value = true
            // 进入动画：使用较慢的弹簧
            launch { fractionProgress.animateTo(1f, ListPopupDefaults.FractionEnterAnimationSpec) }
            launch { alphaProgress.animateTo(1f, ListPopupDefaults.AlphaEnterAnimationSpec) }
            if (enableWindowDim) {
                launch { dimProgress.animateTo(1f, ListPopupDefaults.DimEnterAnimationSpec) }
            }
        } else {
            if (!internalVisible.value) return@LaunchedEffect
            // 退出动画：使用较快的弹簧
            launch { fractionProgress.animateTo(0f, ListPopupDefaults.FractionExitAnimationSpec) }
            if (enableWindowDim) {
                launch { dimProgress.animateTo(0f, ListPopupDefaults.DimExitAnimationSpec) }
            }
            // 透明度控制整体时序：淡出后立即卸载
            alphaProgress.animateTo(0f, ListPopupDefaults.AlphaExitAnimationSpec)
            // 强制重置所有动画状态，确保下次进入从零开始
            fractionProgress.snapTo(0f)
            alphaProgress.snapTo(0f)
            dimProgress.snapTo(0f)
            internalVisible.value = false
            currentOnDismissFinished?.invoke()
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

    val requestDismiss: () -> Unit = remember {
        { currentOnDismiss?.invoke() }
    }

    popupHost(internalVisible.value) {
        BackHandler(enabled = show) {
            requestDismiss()
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
                        val minHeightPx = ListPopupDefaults.MinPopupHeight.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(
                                maxHeight = maxHeight?.roundToPx()?.coerceAtLeast(minHeightPx)
                                    ?: (windowBounds.height - popupMargin.top - popupMargin.bottom)
                                        .coerceAtLeast(minHeightPx),
                                minHeight = if (minHeightPx <= constraints.maxHeight) minHeightPx else constraints.maxHeight,
                                maxWidth = constraints.maxWidth,
                                minWidth = minWidth.roundToPx().coerceAtMost(constraints.maxWidth),
                            ),
                        )
                        val measuredSize = IntSize(placeable.width, placeable.height)

                        val calculatedOffset = popupPositionProvider.calculatePosition(
                            parentBounds,
                            windowBounds,
                            layoutDirection,
                            measuredSize,
                            popupMargin,
                            alignment,
                        )

                        val adjustedOffset = IntOffset(
                            x = calculatedOffset.x - hostPositionInWindow.x.toInt(),
                            y = calculatedOffset.y - hostPositionInWindow.y.toInt(),
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
                    alphaProgress = { alphaProgress.value },
                    popupLayoutPosition = layoutInfo.popupLayoutPosition,
                    localTransformOrigin = layoutInfo.localTransformOrigin,
                    liquidGlassBackdrop = liquidGlassBackdrop,
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
