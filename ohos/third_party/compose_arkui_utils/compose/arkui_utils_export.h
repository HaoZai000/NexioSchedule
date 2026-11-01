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

#ifndef ANDROIDX_COMPOSE_UI_ARKUI_UTILS_EXPORT_H
#define ANDROIDX_COMPOSE_UI_ARKUI_UTILS_EXPORT_H

#include "napi/native_api.h"
#include "xcomponent_common.h"
#include <arkui/native_type.h>

#define ARKUI_UTILS_EXPORT(name) androidx_compose_ui_arkui_utils_##name

EXTERN_C_START
void *ARKUI_UTILS_EXPORT(decodeToSurfaceBuffer)(
    const uint8_t *data,
    size_t size,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
);

void androidx_compose_ui_arkui_utils_releaseSurfaceBuffer(void *surfaceBuffer);
void androidx_compose_ui_arkui_utils_init(napi_env env, napi_value exports);
napi_value androidx_compose_ui_arkui_utils_wrapped(napi_env env, void *nativeController);

Boolean androidx_compose_ui_arkui_utils_xcomponent_prepareDraw(void *render);
Boolean androidx_compose_ui_arkui_utils_xcomponent_finishDraw(void *render);
Boolean androidx_compose_ui_arkui_utils_xcomponent_isEglReady(void *render);
/** @return true when the shared EGL display was terminated (no renders remain). */
Boolean androidx_compose_ui_arkui_utils_xcomponent_releaseEGL(void *render);
void androidx_compose_ui_arkui_utils_xcomponent_registerFrameCallback(void *render);
void androidx_compose_ui_arkui_utils_xcomponent_unregisterFrameCallback(void *render);

/** [enable]: 0 = off and destroy OH_NativeVSync; non-zero = DVSync on (API &lt; 14 no-op in native; capi起始版本). */
void androidx_compose_ui_arkui_utils_native_dvsync_set_enabled(void *render, int enable);

int androidx_compose_ui_arkui_utils_getHarmonyApiVersion(void);
int androidx_compose_ui_arkui_utils_setUiDvsyncSwitch(ArkUI_ContextHandle context, Boolean enable);

/** Warm Performance Radar xperfbridge processor registration (API 18+). */
void androidx_compose_ui_arkui_utils_initHiAppEventXperfBridge(void);

/**
 * Decodes encoded image bytes (e.g. JPEG/PNG) into a DMA-backed PixelMap and returns an opaque
 * handle that owns the PixelMap (and the associated native buffer lifetime). Must be released with
 * androidx_compose_ui_arkui_utils_releaseDecodeBufferHandle after Skia (or other GPU code) no longer
 * needs the buffer, following the ownership contract of OH_PixelmapNative_GetNativeBuffer.
 */
void *ARKUI_UTILS_EXPORT(decodeToDecodeBufferHandle)(
    const uint8_t *data,
    size_t size,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
);


void *ARKUI_UTILS_EXPORT(decodeToDecodeBufferHandleFromRawFile)(
    void *nativeResourceManager,
    const char *path,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
);

void *ARKUI_UTILS_EXPORT(decodeToSurfaceBufferFromRawFile)(
    void *nativeResourceManager,
    const char *path,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
);

/** Returns 1 on success and writes intrinsic size to out_width/out_height; 0 on failure. */
int ARKUI_UTILS_EXPORT(peekImageSizeFromRawFile)(
    void *nativeResourceManager,
    const char *path,
    uint32_t *out_width,
    uint32_t *out_height
);

/** Same as [peekImageSizeFromRawFile] but from encoded bytes (CreateFromData). */
int ARKUI_UTILS_EXPORT(peekImageSizeFromData)(
    const uint8_t *data,
    size_t size,
    uint32_t *out_width,
    uint32_t *out_height
);

/** Sandbox/disk file via [OH_ImageSourceNative_CreateFromFd] (caller keeps [fd] valid for the call). */
void *androidx_compose_ui_arkui_utils_decodeToDecodeBufferHandleFromFd(
    int32_t fd,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
);

void *androidx_compose_ui_arkui_utils_decodeToDecodeBufferHandleFromFile(
    const char *path,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
);

void *androidx_compose_ui_arkui_utils_decodeToSurfaceBufferFromFd(
    int32_t fd,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
);

void *androidx_compose_ui_arkui_utils_decodeToSurfaceBufferFromFile(
    const char *path,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
);

int androidx_compose_ui_arkui_utils_peekImageSizeFromFd(
    int32_t fd,
    uint32_t *out_width,
    uint32_t *out_height
);

int androidx_compose_ui_arkui_utils_peekImageSizeFromFile(
    const char *path,
    uint32_t *out_width,
    uint32_t *out_height
);

void androidx_compose_ui_arkui_utils_releaseDecodeBufferHandle(void *handle);
void *androidx_compose_ui_arkui_utils_decodeBufferHandleGetNativeBuffer(void *handle);

/** Log Pixelmap GetImageInfo (stride/format) + NativeBuffer GetConfig on Skiko import failure. */
void androidx_compose_ui_arkui_utils_logDecodeBufferHandleDiagnostics(void *handle);
void androidx_compose_ui_arkui_utils_logNativeBufferDiagnostics(void *nativeBuffer);

/**
 * Image decode parameters. desiredWidth/Height 0 = intrinsic / decoder default.
 * useNv21: 1 = developer prefers NV21 (SetPixelFormat may still be skipped for alpha sources);
 *          0 = do not SetPixelFormat (decoder default).
 * Actual output may still differ; caller must honor outIsYuv / NativeBuffer format.
 */
typedef struct {
    uint32_t desiredWidth;
    uint32_t desiredHeight;
    int useNv21;
} OhosImageDecodeParams;

/** Output slots for DMA surface-buffer decode (caller-owned pointers). */
typedef struct {
    uint32_t *outWidth;
    uint32_t *outHeight;
    int *outIsYuv;
    void **outSurfaceBuffer;
} OhosDmaDecodeOutput;

/**
 * Decodes to a referenced OH_NativeBuffer (release via releaseSurfaceBuffer).
 * outIsYuv: 1 when pixel layout is NV21 (YUV), 0 for RGBA.
 * @return 1 on success, 0 on failure.
 */
int androidx_compose_ui_arkui_utils_decodeToDmaSurfaceBuffer(
    const uint8_t *data,
    size_t size,
    const OhosImageDecodeParams *params,
    OhosDmaDecodeOutput *output);

int androidx_compose_ui_arkui_utils_decodeRawFileToDmaSurfaceBuffer(
    void *nativeResourceManager,
    const char *path,
    const OhosImageDecodeParams *params,
    OhosDmaDecodeOutput *output);

/**
 * Report frame-drop metrics to HarmonyOS HiAppEvent (Performance Radar).
 *
 * @return hiappevent result code (0 on success, non-zero on failure).
 */
int androidx_compose_ui_arkui_utils_hiAppEventReportFrameDrop(
    const char *domain,
    const char *eventName,
    const char *traceName,
    const char *controllerId,
    int droppedFrameCount,
    int longFrameCount,
    long long durationMs,
    double avgFps);

/**
 * Performance Radar scroll jank: domain PERFORMANCE, event OTHER_SCROLL_JANK.
 * Times are wall-clock milliseconds unless otherwise documented by the caller.
 */
int androidx_compose_ui_arkui_utils_hiAppEventReportScrollJank(
    const char *frameworkName,
    int versionCode,
    long long startTimeMillis,
    long long endTimeMillis,
    long long maxFrameTimeMillis,
    int maxMissedFrameRate,
    int totalMissedFrames,
    int totalFrames,
    const char *pageName,
    int recentScrollCount,
    const char *scrollPhase,
    int pid);

/**
 * Mounts a C-API [ArkUI_NodeHandle] root into an ArkTS [NodeContent] (same semantics as Skiko's
 * ExternalRenderNode.mountHandleToNodeContent). Used by Skia stacked interop so
 * [ArkUINativeInteropContainer] participates in the host hit-test tree.
 */
void androidx_compose_ui_arkui_utils_mountHandleToNodeContent(
    napi_env env,
    napi_value nodeContent,
    void *arkUiNodeHandle);
void ARKUI_UTILS_EXPORT(registerAccessibilityNodeHandle)(const char *composeId, void *nodeHandle);
void ARKUI_UTILS_EXPORT(registerFusionAccessibilityNodeHandle)(const char *composeId, void *nodeHandle);
void ARKUI_UTILS_EXPORT(registerFusionAccessibilityRenderNode)(
    const char *composeId,
    long long renderNodePtr);
void ARKUI_UTILS_EXPORT(unregisterAccessibilityNodeHandle)(const char *composeId);
void *ARKUI_UTILS_EXPORT(accessibilityProviderFromComposeId)(const char *composeId);
int ARKUI_UTILS_EXPORT(setThreadQoS)(int level);

/** Page-level DPI via OH_AbilityRuntime CAPI (API 23+). Returns 0 on success. */
int ARKUI_UTILS_EXPORT(notifyPageDpi)(const char *pageName, int32_t pageNameLength);
/** Cache main window id for page DPI (pushed from ArkTS WindowStageManager). */
void ARKUI_UTILS_EXPORT(setPageDpiWindowId)(int32_t windowId);
/** Clear cached page DPI window id. */
void ARKUI_UTILS_EXPORT(clearPageDpiWindowId)();
/** Returns 1 if OH_AbilityRuntime CAPI is available (API 23+), 0 otherwise. */
int ARKUI_UTILS_EXPORT(isPageDpiAvailable)();

EXTERN_C_END

#undef ARKUI_UTILS_EXPORT

#endif
