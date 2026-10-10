// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.theme

import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * CompositionLocal that provides a dismiss request function for overlay components.
 *
 * This is automatically provided by all overlay components ([OverlayDialog], [WindowDialog],
 * [WindowBottomSheet], [OverlayListPopup], [WindowListPopup]).
 * 底部弹窗现在统一走 `BlurBottomSheet` / `BlurBottomSheetTablet`（fork 改造版，
 * 2026-10-10 迁入 commonMain），旧的 `OverlayBottomSheet` 已删除。
 *
 * Call the provided function to request dismissal from inside overlay content:
 * ```kotlin
 * val dismiss = LocalDismissState.current
 * Button(onClick = { dismiss?.invoke() }) { Text("Close") }
 * ```
 */
val LocalDismissState = staticCompositionLocalOf<(() -> Unit)?> { null }
