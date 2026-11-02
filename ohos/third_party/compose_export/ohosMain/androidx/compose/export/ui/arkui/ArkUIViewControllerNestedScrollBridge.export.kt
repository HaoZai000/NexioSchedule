/*
 * Copyright 2025-2025 Huawei Device Co., Ltd. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

/**
 * **Nested-scroll @CName re-export shim file (export module).**
 *
 * Houses the public `@CName`-exported NAPI entry points used by the cpp wrapper
 * (`arkui_view_controller_wrapper.cpp`) for ArkTS / ETS to drive nested-scroll behavior.
 * These symbols **must always exist** for the cpp wrapper's static link to succeed —
 * regardless of which renderer backend is active — so they live here in the common export
 * surface even though nested scroll is conceptually fusion-only.
 *
 * Under fusion renderer the calls flow through to real bridges; under skia self-rendering
 * they degrade gracefully to no-op (see
 * `androidx.compose.ui.arkui.ArkUIViewControllerNestedScrollBridge.kt` for the inner shims).
 */
@file:Suppress("FunctionName")

package androidx.compose.export.ui.arkui

import androidx.compose.export.annotation.InternalExportApi
import androidx.compose.ui.arkui._ArkUIViewController_consumeOuterScroll
import androidx.compose.ui.arkui._ArkUIViewController_setParentScrollBridge
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_value

@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_setParentScrollBridge")
fun _Export_ArkUIViewController_setParentScrollBridge(controllerRef: COpaquePointer, scroller: napi_value) =
    _ArkUIViewController_setParentScrollBridge(controllerRef, scroller)

@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_consumeOuterScroll")
fun _Export_ArkUIViewController_consumeOuterScroll(
    controllerRef: COpaquePointer,
    offsetVp: Float,
    sourceInt: Int
): Float = _ArkUIViewController_consumeOuterScroll(controllerRef, offsetVp, sourceInt)
