// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.overlay

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import com.haooz.softglass.backdrop.GlassBackdrop
import top.yukonga.miuix.kmp.layout.DialogContentLayout
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout

/**
 * 浮层专用的整窗采样层，由分栏宿主（平板左列表 + 右详情）provide。
 *
 * 页面级 backdrop 只录当前栏的内容，浮层居中压在两栏之上时，落在另一栏的部分会采样到
 * 空层（黑）。分栏宿主在根节点挂 `layerBackdrop` 后 provide 这一层，[OverlayDialog] 优先用它。
 * 非分栏场景保持 null，行为与原来完全一致。
 */
val LocalOverlayDialogBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * A dialog with a title, a summary, and other contents.
 *
 * @param show Whether the [OverlayDialog] is shown.
 * @param modifier The modifier to be applied to the [OverlayDialog].
 * @param title The title of the [OverlayDialog].
 * @param titleColor The color of the title.
 * @param summary The summary of the [OverlayDialog].
 * @param summaryColor The color of the summary.
 * @param backgroundColor The background color of the [OverlayDialog].
 * @param liquidGlassBackdrop The [GlassBackdrop] for liquidGlass blur effect. When non-null, enables blur + edge light.
 * @param enableWindowDim Whether to enable window dimming when the [OverlayDialog] is shown.
 * @param onDismissRequest Will called when the user tries to dismiss the Dialog by clicking outside or pressing the back button.
 * @param onDismissFinished The callback when the [OverlayDialog] is completely dismissed.
 * @param enablePredictiveBackGesture Whether the dialog follows the predictive back gesture.
 *   Set to false for dialogs that must not be dismissed by back (e.g. a blocking consent dialog):
 *   the back gesture is still consumed, but no follow animation is played.
 * @param outsideMargin The margin outside the [OverlayDialog].
 * @param insideMargin The margin inside the [OverlayDialog].
 * @param defaultWindowInsetsPadding Whether to apply default window insets padding to the [OverlayDialog].
 * @param renderInRootScaffold Whether to render the dialog in the root (outermost) Scaffold.
 *   When true (default), the dialog covers the full screen. When false, it renders within the
 *   current Scaffold's bounds.
 * @param content The [Composable] content of the [OverlayDialog].
 */
@Composable
fun OverlayDialog(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: Color = DialogDefaults.titleColor(),
    summary: String? = null,
    summaryColor: Color = DialogDefaults.summaryColor(),
    backgroundColor: Color = DialogDefaults.backgroundColor(),
    liquidGlassBackdrop: GlassBackdrop? = null,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    enablePredictiveBackGesture: Boolean = true,
    outsideMargin: DpSize = DialogDefaults.outsideMargin,
    insideMargin: DpSize = DialogDefaults.insideMargin,
    defaultWindowInsetsPadding: Boolean = true,
    renderInRootScaffold: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    // 分栏宿主给了整窗层就用它，否则沿用调用方的页面级层
    val backdrop = LocalOverlayDialogBackdrop.current ?: liquidGlassBackdrop

    DialogContentLayout(
        show = show,
        titleColor = titleColor,
        summaryColor = summaryColor,
        backgroundColor = backgroundColor,
        outsideMargin = outsideMargin,
        insideMargin = insideMargin,
        popupHost = { visible, hostContent ->
            val visibleState = remember { mutableStateOf(false) }
            visibleState.value = visible
            DialogLayout(
                visible = visibleState,
                enableWindowDim = false,
                enterTransition = EnterTransition.None,
                exitTransition = ExitTransition.None,
                enableAutoLargeScreen = false,
                renderInRootScaffold = renderInRootScaffold,
            ) {
                hostContent()
            }
        },
        liquidGlassBackdrop = backdrop,
        isDark = isDark,
        enablePredictiveBackGesture = enablePredictiveBackGesture,
        modifier = modifier,
        title = title,
        summary = summary,
        enableWindowDim = enableWindowDim,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        content = content,
    )
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
