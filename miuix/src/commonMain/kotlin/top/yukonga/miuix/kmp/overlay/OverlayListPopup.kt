// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.overlay

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.interactive.DropdownPanelDragSelectState
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.layout.ListPopupLayout
import top.yukonga.miuix.kmp.layout.liquidDropdownPositionProvider
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.PopupLayout

private val LocalMinWidth = 200.dp

/**
 * 带列表的弹窗。
 *
 * @param show 是否显示弹窗
 * @param popupModifier 弹窗修饰符
 * @param popupPositionProvider 弹窗位置提供者
 * @param alignment 弹窗对齐方式
 * @param enableWindowDim 是否启用背景变暗
 * @param onDismissRequest 关闭弹窗的回调
 * @param onDismissFinished 关闭动画完成后的回调
 * @param maxHeight 弹窗最大高度
 * @param minWidth 弹窗最小宽度
 * @param renderInRootScaffold 是否在根Scaffold中渲染
 * @param liquidGlassBackdrop 液体玻璃模糊效果的Backdrop
 * @param content 弹窗内容
 */
@Composable
fun OverlayListPopup(
    show: Boolean,
    popupModifier: Modifier = Modifier,
    popupPositionProvider: PopupPositionProvider = liquidDropdownPositionProvider(),
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.Start,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    maxHeight: Dp? = null,
    minWidth: Dp = LocalMinWidth,
    renderInRootScaffold: Boolean = true,
    liquidGlassBackdrop: Backdrop? = null,
    onFractionProgress: ((Float) -> Unit)? = null,
    collapseSize: IntSize? = null,
    collapseExtra: DpSize = DpSize.Zero,
    collapseContent: (@Composable () -> Unit)? = null,
    /** 跟手滑选状态。透传给 [ListPopupLayout]；为 null 时不启用。 */
    dragSelectState: DropdownPanelDragSelectState? = null,
    content: @Composable () -> Unit,
) {
    ListPopupLayout(
        show = show,
        popupHost = { visible, hostContent ->
            val visibleState = remember { mutableStateOf(false) }
            visibleState.value = visible
            PopupLayout(
                visible = visibleState,
                enableWindowDim = false,
                enableBackHandler = false,
                enterTransition = EnterTransition.None,
                exitTransition = ExitTransition.None,
                renderInRootScaffold = renderInRootScaffold,
            ) {
                hostContent()
            }
        },
        popupModifier = popupModifier,
        popupPositionProvider = popupPositionProvider,
        alignment = alignment,
        enableWindowDim = enableWindowDim,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        maxHeight = maxHeight,
        minWidth = minWidth,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onFractionProgress = onFractionProgress,
        collapseSize = collapseSize,
        collapseExtra = collapseExtra,
        collapseContent = collapseContent,
        dragSelectState = dragSelectState,
        content = content,
    )
}
