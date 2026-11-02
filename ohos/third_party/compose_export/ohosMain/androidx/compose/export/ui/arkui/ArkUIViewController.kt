/*
 * Tencent is pleased to support the open source community by making ovCompose available.
 * Copyright (C) 2025 Tencent. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("FunctionName")
@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package androidx.compose.export.ui.arkui

import androidx.compose.export.annotation.InternalExportApi
import androidx.compose.ui.arkui._ArkUIViewController_aboutToAppear
import androidx.compose.ui.arkui._ArkUIViewController_aboutToDisappear
import androidx.compose.ui.arkui._ArkUIViewController_cancelSyncRefresh
import androidx.compose.ui.arkui._ArkUIViewController_dispatchHoverEvent
import androidx.compose.ui.arkui._ArkUIViewController_dispatchComposeMouseEvent
import androidx.compose.ui.arkui._ArkUIViewController_dispatchComposePinchEvent
import androidx.compose.ui.arkui._ArkUIViewController_dispatchMouseEvent
import androidx.compose.ui.arkui._ArkUIViewController_dispatchTouchEvent
import androidx.compose.ui.arkui._ArkUIViewController_getId
import androidx.compose.ui.arkui._ArkUIViewController_keyboardWillHide
import androidx.compose.ui.arkui._ArkUIViewController_keyboardWillShow
import androidx.compose.ui.arkui._ArkUIViewController_onBackPress
import androidx.compose.ui.arkui._ArkUIViewController_onConfigurationUpdate
import androidx.compose.ui.arkui._ArkUIViewController_onFinalize
import androidx.compose.ui.arkui._ArkUIViewController_onFocusEvent
import androidx.compose.ui.arkui._ArkUIViewController_onFrame
import androidx.compose.ui.arkui._ArkUIViewController_onIdle
import androidx.compose.ui.arkui._ArkUIViewController_draw
import androidx.compose.ui.arkui._ArkUIViewController_getJsNode
import androidx.compose.ui.arkui._ArkUIViewController_initFusionRendererNodeImpl
import androidx.compose.ui.arkui._ArkUIViewController_setFusionRendererRootNode
import androidx.compose.ui.arkui._InitJsRenderNodeContext
import androidx.compose.ui.arkui._ArkUIViewController_dispatchComposeKeyEvent
import androidx.compose.ui.arkui._ArkUIViewController_onKeyEvent
import androidx.compose.ui.arkui._ArkUIViewController_onPageHide
import androidx.compose.ui.arkui._ArkUIViewController_onPageShow
import androidx.compose.ui.arkui._ArkUIViewController_onSurfaceChanged
import androidx.compose.ui.arkui._ArkUIViewController_onSurfaceCreated
import androidx.compose.ui.arkui._ArkUIViewController_onSurfaceDestroyed
import androidx.compose.ui.arkui._ArkUIViewController_findNodeIdAt
import androidx.compose.ui.arkui._ArkUIViewController_onSurfaceHide
import androidx.compose.ui.arkui._ArkUIViewController_onSurfaceShow
import androidx.compose.ui.arkui._ArkUIViewController_requestSyncRefresh
import androidx.compose.ui.arkui._ArkUIViewController_sendMessage
import androidx.compose.ui.arkui._ArkUIViewController_setContext
import androidx.compose.ui.arkui._ArkUIViewController_setEnv
import androidx.compose.ui.arkui._ArkUIViewController_setId
import androidx.compose.ui.arkui._ArkUIViewController_setRendererBackendId
import androidx.compose.ui.arkui._ArkUIViewController_setMessenger
import androidx.compose.ui.arkui._ArkUIViewController_setLocaleAndStringProvider
import androidx.compose.ui.arkui._ArkUIViewController_setRootView
import androidx.compose.ui.arkui._ArkUIViewController_setUIContext
import androidx.compose.ui.arkui._ArkUIViewController_getXComponentRender
import androidx.compose.ui.arkui._ArkUIViewController_setXComponentRender
import androidx.compose.ui.graphics.kLog
import androidx.compose.ui.platform.LowMemoryMonitor
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.NativePtr
import kotlinx.cinterop.nativeNullPtr
import kotlinx.cinterop.staticCFunction
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_env
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_value
import kotlinx.cinterop.*
import platform.posix.clock_gettime
import platform.posix.timespec
import platform.posix.CLOCK_MONOTONIC
import org.jetbrains.skiko.getRendererTypeId

// 这是一个完全替代 getTimeNanos() 的函数
fun getCurrentTimeNanos(): Long {
    memScoped {
        val timespec = alloc<timespec>()
        // CLOCK_MONOTONIC 保证时间是单调递增的，不受系统时间修改影响，适合计算时间间隔
        // 如果你需要挂钟时间（墙上时间），可以用 CLOCK_REALTIME
        clock_gettime(CLOCK_MONOTONIC, timespec.ptr)
        
        // 秒 * 10^9 + 纳秒
        return timespec.tv_sec * 1_000_000_000L + timespec.tv_nsec
    }
}

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_setId")
fun _Export_ArkUIViewController_setId(controllerRef: COpaquePointer, id: String) =
    _ArkUIViewController_setId(controllerRef, id)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_getId")
fun _Export_ArkUIViewController_getId(controllerRef: COpaquePointer): String? =
    _ArkUIViewController_getId(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_setEnv")
fun _Export_ArkUIViewController_setEnv(controllerRef: COpaquePointer, env: napi_env) =
    _ArkUIViewController_setEnv(controllerRef, env)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_setRendererBackendId")
fun _Export_ArkUIViewController_setRendererBackendId(controllerRef: COpaquePointer, backendId: Int) =
    _ArkUIViewController_setRendererBackendId(controllerRef, backendId)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_setContext")
fun _Export_ArkUIViewController_setContext(controllerRef: COpaquePointer, context: napi_value) =
    _ArkUIViewController_setContext(controllerRef, context)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_setUIContext")
fun _Export_ArkUIViewController_setUIContext(controllerRef: COpaquePointer, uiContext: napi_value) =
    _ArkUIViewController_setUIContext(controllerRef, uiContext)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_setMessenger")
fun _Export_ArkUIViewController_setMessenger(controllerRef: COpaquePointer, messenger: napi_value) =
    _ArkUIViewController_setMessenger(controllerRef, messenger)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_setLocaleAndStringProvider")
fun _Export_ArkUIViewController_setLocaleAndStringProvider(controllerRef: COpaquePointer, provider: napi_value) =
    _ArkUIViewController_setLocaleAndStringProvider(controllerRef, provider)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_sendMessage")
fun _Export_ArkUIViewController_sendMessage(controllerRef: COpaquePointer, type: String, message: String): String? =
    _ArkUIViewController_sendMessage(controllerRef, type, message)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_setXComponentRender")
fun _Export_ArkUIViewController_setXComponentRender(controllerRef: COpaquePointer, render: COpaquePointer) =
    _ArkUIViewController_setXComponentRender(controllerRef, render)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_getXComponentRender")
fun _Export_ArkUIViewController_getXComponentRender(controllerRef: COpaquePointer): COpaquePointer? =
    _ArkUIViewController_getXComponentRender(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_setRootView")
fun _Export_ArkUIViewController_setRootView(
    controllerRef: COpaquePointer,
    backRootView: napi_value,
    foreRootView: napi_value,
    touchableRootView: napi_value
) = _ArkUIViewController_setRootView(controllerRef, backRootView, foreRootView, touchableRootView)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_aboutToAppear")
fun _Export_ArkUIViewController_aboutToAppear(controllerRef: COpaquePointer) =
    _ArkUIViewController_aboutToAppear(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_aboutToDisappear")
fun _Export_ArkUIViewController_aboutToDisappear(controllerRef: COpaquePointer) =
    _ArkUIViewController_aboutToDisappear(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_onPageShow")
fun _Export_ArkUIViewController_onPageShow(controllerRef: COpaquePointer) =
    _ArkUIViewController_onPageShow(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_onPageHide")
fun _Export_ArkUIViewController_onPageHide(controllerRef: COpaquePointer) =
    _ArkUIViewController_onPageHide(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_onConfigurationUpdate")
fun _Export_ArkUIViewController_onConfigurationUpdate(controllerRef: COpaquePointer) =
    _ArkUIViewController_onConfigurationUpdate(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_onBackPress")
fun _Export_ArkUIViewController_onBackPress(controllerRef: COpaquePointer): Boolean =
    _ArkUIViewController_onBackPress(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_onSurfaceCreated")
fun _Export_ArkUIViewController_onSurfaceCreated(
    controllerRef: COpaquePointer,
    xcomponentPtr: COpaquePointer,
    width: Int,
    height: Int
) = _ArkUIViewController_onSurfaceCreated(controllerRef, xcomponentPtr, width, height)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_onSurfaceChanged")
fun _Export_ArkUIViewController_onSurfaceChanged(controllerRef: COpaquePointer, width: Int, height: Int) =
    _ArkUIViewController_onSurfaceChanged(controllerRef, width, height)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_onSurfaceShow")
fun _Export_ArkUIViewController_onSurfaceShow(controllerRef: COpaquePointer) =
    _ArkUIViewController_onSurfaceShow(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_onSurfaceHide")
fun _Export_ArkUIViewController_onSurfaceHide(controllerRef: COpaquePointer) =
    _ArkUIViewController_onSurfaceHide(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_onSurfaceDestroyed")
fun _Export_ArkUIViewController_onSurfaceDestroyed(controllerRef: COpaquePointer) =
    _ArkUIViewController_onSurfaceDestroyed(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_onFrame")
fun _Export_ArkUIViewController_onFrame(controllerRef: COpaquePointer, timestamp: Long, targetTimestamp: Long) =
    _ArkUIViewController_onFrame(controllerRef, timestamp, targetTimestamp)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_onFocusEvent")
fun _Export_ArkUIViewController_onFocusEvent(controllerRef: COpaquePointer) =
    _ArkUIViewController_onFocusEvent(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_onKeyEvent")
fun _Export_ArkUIViewController_onKeyEvent(controllerRef: COpaquePointer) =
    _ArkUIViewController_onKeyEvent(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_dispatchComposeKeyEvent")
fun _Export_ArkUIViewController_dispatchComposeKeyEvent(
    controllerRef: COpaquePointer,
    keyCode: Long,
    nativeAction: Int,
    modifierKeyStates: Long,
): Boolean = _ArkUIViewController_dispatchComposeKeyEvent(controllerRef, keyCode, nativeAction, modifierKeyStates)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_dispatchTouchEvent")
fun _Export_ArkUIViewController_dispatchTouchEvent(
    controllerRef: COpaquePointer,
    nativeTouchEvent: napi_value,
    ignoreInteropView: Boolean
): Boolean = _ArkUIViewController_dispatchTouchEvent(controllerRef, nativeTouchEvent, ignoreInteropView)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_dispatchComposeMouseEvent")
fun _Export_ArkUIViewController_dispatchComposeMouseEvent(
    controllerRef: COpaquePointer,
    nativeMouseEvent: napi_value,
): Boolean = _ArkUIViewController_dispatchComposeMouseEvent(controllerRef, nativeMouseEvent)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_dispatchComposePinchEvent")
fun _Export_ArkUIViewController_dispatchComposePinchEvent(
    controllerRef: COpaquePointer,
    nativePinchEvent: napi_value,
): Boolean = _ArkUIViewController_dispatchComposePinchEvent(controllerRef, nativePinchEvent)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_dispatchMouseEvent")
fun _Export_ArkUIViewController_dispatchMouseEvent(controllerRef: COpaquePointer) =
    _ArkUIViewController_dispatchMouseEvent(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_dispatchHoverEvent")
fun _Export_ArkUIViewController_dispatchHoverEvent(controllerRef: COpaquePointer) =
    _ArkUIViewController_dispatchHoverEvent(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_keyboardWillShow")
fun _Export_ArkUIViewController_keyboardWillShow(controllerRef: COpaquePointer, keyboardHeight: Float) =
    _ArkUIViewController_keyboardWillShow(controllerRef, keyboardHeight)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_keyboardWillHide")
fun _Export_ArkUIViewController_keyboardWillHide(controllerRef: COpaquePointer) =
    _ArkUIViewController_keyboardWillHide(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_requestSyncRefresh")
fun _Export_ArkUIViewController_requestSyncRefresh(controllerRef: COpaquePointer): Int =
    _ArkUIViewController_requestSyncRefresh(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_findNodeIdAt")
fun _Export_ArkUIViewController_findNodeIdAt(controllerRef: COpaquePointer, x: Float, y: Float): Int =
    _ArkUIViewController_findNodeIdAt(controllerRef, x, y)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_cancelSyncRefresh")
fun _Export_ArkUIViewController_cancelSyncRefresh(controllerRef: COpaquePointer, refreshId: Int) =
    _ArkUIViewController_cancelSyncRefresh(controllerRef, refreshId)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_onFinalize")
fun _Export_ArkUIViewController_onFinalize(controllerRef: COpaquePointer) =
    _ArkUIViewController_onFinalize(controllerRef)

@InternalExportApi
@CName("InitJsRenderNodeContext")
fun InitJsRenderNodeContext(env: napi_env, nodeConstructor: napi_value, statusModifyConstructor: napi_value, ratio: Double, fixed: Boolean) {
    _InitJsRenderNodeContext(env, nodeConstructor, statusModifyConstructor, ratio, fixed)
}

@InternalExportApi
@CName("AndroidXComposeUIArkUIArkUIViewControllerOnIdle")
fun _Export_ArkUIViewController_onIdle(controllerRef: COpaquePointer, timeLeft: Long) =
    _ArkUIViewController_onIdle(controllerRef, timeLeft)

@InternalExportApi
@CName("AndroidxComposeUiArkuiArkUiViewControllerDraw")
fun _Export_ArkUIViewController_draw(controllerRef: COpaquePointer, canvas: napi_value) =
    _ArkUIViewController_draw(controllerRef, canvas)

@InternalExportApi
@CName("AndroidxComposeUiArkuiArkUiViewControllerGetJsNode")
fun _Export_ArkUIViewController_getJsNode(controllerRef: COpaquePointer): napi_value? =
    _ArkUIViewController_getJsNode(controllerRef)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_initFusionRendererNode")
fun _Export_ArkUIViewController_initFusionRendererNode(controllerRef: COpaquePointer, enableCApi: Boolean, rootContent: napi_value?, frameMgr: napi_value?) =
    _ArkUIViewController_initFusionRendererNodeImpl(controllerRef, enableCApi, rootContent, frameMgr, false)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_initFusionRendererNodePreComposed")
fun _Export_ArkUIViewController_initFusionRendererNodePreComposed(controllerRef: COpaquePointer, enableCApi: Boolean, rootContent: napi_value?, frameMgr: napi_value?, isPreCompose: Boolean) =
    _ArkUIViewController_initFusionRendererNodeImpl(controllerRef, enableCApi, rootContent, frameMgr, isPreCompose)

@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_setFusionRendererRootNode")
fun _Export_ArkUIViewController_setFusionRendererRootNode(
    controllerRef: COpaquePointer,
    rootFrameNode: napi_value,
    uiContext: napi_value,
) = _ArkUIViewController_setFusionRendererRootNode(controllerRef, rootFrameNode, uiContext)

/**
 * Call from native host when receiving low-memory level.
 * [rawLevel]: either HarmonyOS **AbilityConstant.MemoryLevel** (0/1/2, …) or Android-style **TRIM_MEMORY_*** (≥ 5);
 * resolved in [LowMemoryMonitor.notifyLowMemoryFromHost]. Uses `appInForeground = true` (NAPI has no flag).
 * ArkTS sends `rawLevel,1|0` on `compose.ui:LowMemory.notify` (ComposeComponentHost.ets).
 */
@InternalExportApi
@CName("androidx_compose_ui_platform_LowMemoryMonitor_notifyLowMemoryFromHost")
fun _Export_LowMemoryMonitor_notifyLowMemoryFromHost(rawLevel: Int) {
    kLog.i("LowMemory: NAPI notify low memory raw=$rawLevel foreground=true(default)")
    LowMemoryMonitor.notifyLowMemoryFromHost(rawLevel)
}
@InternalExportApi
@CName("androidx_compose_ui_arkui_ArkUIViewController_getRendererTypeId")
fun _Export_ArkUIViewController_getRendererTypeId(): Int =
    getRendererTypeId()

