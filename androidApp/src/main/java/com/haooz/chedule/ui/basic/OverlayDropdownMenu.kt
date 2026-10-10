package com.haooz.chedule.ui.basic

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun resolveSelectedText(entries: List<DropdownEntry>): String? {
    for (entry in entries) {
        for (item in entry.items) {
            if (item.selected) return item.text
        }
    }
    return null
}

/**
 * 触发区内容：当前选中的文字 + 右侧下拉箭头。
 *
 * 同一份内容渲染在两处，尺寸完全一致：
 * - `BasicComponent` 的 endActions 里（弹窗外的静态行，同时用于实测收起态尺寸）
 * - 弹窗内部的收起态（`collapseContent`，随玻璃盒长大淡出）
 *
 * 是 [RowScope] 扩展，因为 [DropdownArrowEndAction] 本身是 RowScope 扩展。
 *
 * @param alpha 整体透明度。弹窗外那份随 fraction 淡出；弹窗内那份由容器变换驱动。
 */
@Composable
private fun RowScope.DropdownTriggerContent(
    selectedText: String?,
    actionColor: androidx.compose.ui.graphics.Color,
    alpha: () -> Float,
) {
    if (selectedText != null) {
        Text(
            text = selectedText,
            fontSize = 14.2.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            maxLines = 1,
            modifier = Modifier
                .padding(end = 8.dp)
                .graphicsLayer { this.alpha = alpha() },
        )
    }
    DropdownArrowEndAction(
        actionColor = actionColor,
        modifier = Modifier.graphicsLayer { this.alpha = alpha() },
    )
}

/**
 * 收起态胶囊的默认额外补偿。
 *
 * 收起态尺寸 = 「选中文字 + 箭头图标」的实测宽高 + 这个补偿。
 * 补偿只加在 lerp 的起点上，展开态终点仍是面板自然尺寸。
 * 单点下调即可全局生效；个别页面需要不同值时用 `OverlayDropdownMenu(collapseExtra = ...)` 覆盖。
 */
private val DefaultCollapseExtra = DpSize(14.dp, 10.dp)

/**
 * A [BasicComponent] wrapper that opens an [OverlayDropdownPopup] for a single [DropdownEntry].
 *
 * When [summary] is null, the text of the currently selected [top.yukonga.miuix.kmp.basic.DropdownItem]
 * is shown automatically.
 */
@Composable
fun OverlayDropdownMenu(
    entry: DropdownEntry,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    collapseExtra: DpSize = DefaultCollapseExtra,
) {
    val entries = remember(entry) { listOf(entry) }
    OverlayDropdownMenu(
        entries = entries,
        title = title,
        modifier = modifier,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        dropdownColors = dropdownColors,
        startAction = startAction,
        bottomAction = bottomAction,
        insideMargin = insideMargin,
        maxHeight = maxHeight,
        enabled = enabled,
        renderInRootScaffold = renderInRootScaffold,
        collapseOnSelection = collapseOnSelection,
        onExpandedChange = onExpandedChange,
        liquidGlassBackdrop = liquidGlassBackdrop,
        collapseExtra = collapseExtra,
    )
}

/**
 * A [BasicComponent] wrapper that opens an [OverlayDropdownPopup] for one or more [DropdownEntry] groups.
 */
@Composable
fun OverlayDropdownMenu(
    entries: List<DropdownEntry>,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = entries.size <= 1,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    collapseExtra: DpSize = DefaultCollapseExtra,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDropdownExpanded = remember { mutableStateOf(false) }
    val isHoldDown = remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val currentOnExpandedChange = rememberUpdatedState(onExpandedChange)
    val coroutineScope = rememberCoroutineScope()

    // 触发内容只在「彻底退场」后显示。
    // 判据不能用 fraction：退场 spring 阻尼 0.85 会过冲到负值并在 0 附近振荡，
    // 拿阈值判断会在振荡途中误判结束 → 内容提前冒出来。
    // onDismissFinished 是 ListPopupLayout 在所有退场动画结束、snapTo(0) 之后才发的信号。
    var popupFullyDismissed by remember { mutableStateOf(true) }

    val setExpanded: (Boolean) -> Unit = remember {
        { expanded ->
            if (isDropdownExpanded.value != expanded) {
                isDropdownExpanded.value = expanded
                currentOnExpandedChange.value?.invoke(expanded)
            }
        }
    }

    // 展开时延后两帧再藏 —— 第一帧让弹窗画出收起态，第二帧确保它已经上屏，
    // 否则中间会空一帧（两边都没内容）。
    LaunchedEffect(isDropdownExpanded.value) {
        if (isDropdownExpanded.value) {
            repeat(2) { withFrameNanos { } }
            popupFullyDismissed = false
        }
    }

    // 弹窗展开进度，用于驱动触发内容（选中文字与箭头）的淡入淡出
    val fractionState = remember { mutableStateOf(0f) }
    // 触发区（选中文字 + 箭头）实测尺寸，作为弹窗收起态的起点尺寸
    var triggerSize by remember { mutableStateOf(IntSize.Zero) }
    // 退场后是否显示触发区内容。**硬切**，不要过渡：
    //   展开 → 立刻消失；退场动画跑完 → 立刻出现。中间没有半透明状态。
    // 保持节点渲染只用 alpha=0：那层 Row 挂了 onSizeChanged 上报 collapseSize，
    // 摘掉节点的话 triggerSize 变 0，弹窗就不知道从哪长出来了。
    val triggerAlpha: () -> Float = { if (popupFullyDismissed) 1f else 0f }

    // 跟手滑选状态。必须在本组件创建（不能由弹窗内容层自建）：弹窗在 Popup 独立
    // 窗口里，要跨窗口回传选中项，只能靠调用方持有的同一个实例
    val dragSelectState = remember { DropdownPanelDragSelectState() }

    val nonEmptyEntries = entries.filter { it.items.isNotEmpty() }
    val hasEntries = nonEmptyEntries.isNotEmpty()
    val actualEnabled = enabled && hasEntries
    val selectedText = resolveSelectedText(nonEmptyEntries)
    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }

    val handleClick = remember(actualEnabled) {
        {
            if (actualEnabled) {
                setExpanded(!isDropdownExpanded.value)
                if (isDropdownExpanded.value) {
                    isHoldDown.value = true
                    currentHapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
        }
    }

    BasicComponent(
        modifier = modifier,
        interactionSource = interactionSource,
        insideMargin = insideMargin,
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = {
            // 触发区（选中文字 + 箭头图标）实测尺寸 —— 弹窗收起态的起点尺寸。
            // 用 Row 包一层并测量，弹窗从这块内容原位长成面板。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.onSizeChanged { triggerSize = it },
            ) {
                DropdownTriggerContent(
                    selectedText = selectedText,
                    actionColor = actionColor,
                    alpha = { triggerAlpha() },
                )
            }
            if (hasEntries) {
                OverlayDropdownPopup(
                    entries = nonEmptyEntries,
                    show = isDropdownExpanded.value,
                    onDismiss = { setExpanded(false) },
                    onDismissFinished = {
                        isHoldDown.value = false
                        // 压满3帧再显示：否则弹窗还在屏幕上会重叠
                        coroutineScope.launch {
                            repeat(3) { withFrameNanos { } }
                            popupFullyDismissed = true
                        }
                    },
                    maxHeight = maxHeight,
                    dropdownColors = dropdownColors,
                    renderInRootScaffold = renderInRootScaffold,
                    collapseOnSelection = collapseOnSelection,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    onFractionProgress = { fractionState.value = it },
                    collapseSize = triggerSize,
                    collapseExtra = collapseExtra,
                    dragSelectState = dragSelectState,
                    // 收起态显示的内容：同样是「选中文字 + 箭头」，
                    // 弹窗从这块内容原位长成菜单。
                    collapseContent = {
                        DropdownTriggerContent(
                            selectedText = selectedText,
                            actionColor = actionColor,
                            alpha = { 1f },
                        )
                    },
                )
            }
        },
        bottomAction = bottomAction,
        onClick = handleClick,
        role = Role.DropdownList,
        holdDownState = isHoldDown.value,
        enabled = actualEnabled,
    )
}
