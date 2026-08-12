package com.haooz.chedule.ui.basic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDropdownExpanded = remember { mutableStateOf(false) }
    val isHoldDown = remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val currentOnExpandedChange = rememberUpdatedState(onExpandedChange)
    val setExpanded: (Boolean) -> Unit = remember {
        { expanded ->
            if (isDropdownExpanded.value != expanded) {
                isDropdownExpanded.value = expanded
                currentOnExpandedChange.value?.invoke(expanded)
            }
        }
    }

    // 弹窗动画进度，用于驱动选项文字与箭头的淡入淡出
    val fractionState = remember { mutableStateOf(0f) }
    val contentAlpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        var prevFraction = 0f
        var contentVisible = true
        var animJob: Job? = null
        snapshotFlow { fractionState.value }
            .collect { current ->
                val isEntering = current >= prevFraction
                prevFraction = current
                // 出现时到 0.6 消失，关闭时到 0.5 出现
                val newVisible = if (isEntering) current < 0.15f else current < 0.2f
                if (newVisible != contentVisible) {
                    contentVisible = newVisible
                    animJob?.cancel()
                    animJob = launch {
                        contentAlpha.animateTo(
                            targetValue = if (newVisible) 1f else 0f,
                            animationSpec = tween(180)
                        )
                    }
                }
            }
    }

    val nonEmptyEntries = entries.filter { it.items.isNotEmpty() }
    val hasEntries = nonEmptyEntries.isNotEmpty()
    // 总条目数超过 2 时，揭示裁剪到两行高度
    val totalItemCount = nonEmptyEntries.sumOf { it.items.size }
    val revealLimitHeight = if (totalItemCount > 2) (56.dp * 2) else 0.dp
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
            if (selectedText != null) {
                Text(
                    text = selectedText,
                    fontSize = 14.2.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .graphicsLayer { alpha = contentAlpha.value }
                )
            }
            DropdownArrowEndAction(
                actionColor = actionColor,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha.value }
            )
            if (hasEntries) {
                OverlayDropdownPopup(
                    entries = nonEmptyEntries,
                    show = isDropdownExpanded.value,
                    onDismiss = { setExpanded(false) },
                    onDismissFinished = { isHoldDown.value = false },
                    maxHeight = maxHeight,
                    dropdownColors = dropdownColors,
                    renderInRootScaffold = renderInRootScaffold,
                    collapseOnSelection = collapseOnSelection,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    onFractionProgress = { fraction -> fractionState.value = fraction },
                    revealLimitHeight = revealLimitHeight,
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
