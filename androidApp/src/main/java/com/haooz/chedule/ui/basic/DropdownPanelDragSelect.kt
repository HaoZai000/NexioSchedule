package com.haooz.chedule.ui.basic

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.interactive.dropdownPanelDragSelect as miuixDropdownPanelDragSelect
import top.yukonga.miuix.kmp.interactive.dropdownPanelDragTransform as miuixDropdownPanelDragTransform
import top.yukonga.miuix.kmp.interactive.dropdownPanelEntry as miuixDropdownPanelEntry

// 跟手滑选实现已搬进 :miuix
// （miuix/src/commonMain/kotlin/top/yukonga/miuix/kmp/interactive/DropdownPanelDragSelect.kt）。
//
// 原因：:miuix 自己的 ListPopup / Dropdown / OverlayListPopup / LiquidOverlayDropdownPopup
// 都要用它 —— 那几个文件在 :app 里，会让 :miuix 反向依赖业务层，无法进 commonMain。
//
// 搬走之前这里是 405 行的完整实现，搬走后与 :miuix 那份逐字等价（同为 379/405 行，
// 符号集合一致：DragSelectAxis、panelLeftInRoot、computeDragTransform、VerticalDragRefScale…）。
// 本文件改为**重导出**，让 :app 内按 com.haooz.chedule.ui.basic.* 引用的地方
// （TabletNavSideBar 等）一行都不用改，且两边操作的是同一批类型 —— 不是副本，状态互通。

typealias DropdownPanelEntry = top.yukonga.miuix.kmp.interactive.DropdownPanelEntry

typealias DropdownPanelDragSelectState =
    top.yukonga.miuix.kmp.interactive.DropdownPanelDragSelectState

typealias DragSelectAxis = top.yukonga.miuix.kmp.interactive.DragSelectAxis

/** 原为 internal；:app 侧需要读取，搬进模块后必须放开。 */
val LocalDropdownPanelDragSelect =
    top.yukonga.miuix.kmp.interactive.LocalDropdownPanelDragSelect

fun Modifier.dropdownPanelDragSelect(
    state: DropdownPanelDragSelectState,
    fraction: () -> Float,
    hapticFeedback: HapticFeedback,
): Modifier = miuixDropdownPanelDragSelect(
    state = state,
    fraction = fraction,
    hapticFeedback = hapticFeedback,
)

fun Modifier.dropdownPanelDragTransform(
    fraction: () -> Float,
    pressProgress: () -> Float,
    dragOffset: () -> Offset,
): Modifier = miuixDropdownPanelDragTransform(
    fraction = fraction,
    pressProgress = pressProgress,
    dragOffset = dragOffset,
)

@androidx.compose.runtime.Composable
fun Modifier.dropdownPanelEntry(
    enabled: Boolean,
    state: DropdownPanelDragSelectState? = null,
    highlightPadding: PaddingValues = PaddingValues(0.dp),
    highlightShape: Shape = RectangleShape,
    selected: Boolean = false,
    action: () -> Unit,
): Modifier = miuixDropdownPanelEntry(
    enabled = enabled,
    state = state,
    highlightPadding = highlightPadding,
    highlightShape = highlightShape,
    selected = selected,
    action = action,
)