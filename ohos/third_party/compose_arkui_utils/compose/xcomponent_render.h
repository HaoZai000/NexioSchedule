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

#ifndef ANDROIDX_COMPOSE_UI_ARKUI_UTILS_XCOMPONENTRENDER_H
#define ANDROIDX_COMPOSE_UI_ARKUI_UTILS_XCOMPONENTRENDER_H

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <ace/xcomponent/native_interface_xcomponent.h>
#include <atomic>
#include <string>

typedef void (*OH_NativeXComponent_OnFrameCallback)(OH_NativeXComponent *component, uint64_t timestamp,
                                                    uint64_t targetTimestamp);

namespace androidx::compose::ui::arkui::utils {
typedef struct ArkUIViewController ArkUIViewController;
class XComponentRender {
public:
    explicit XComponentRender(OH_NativeXComponent *nativeXComponent);
    ~XComponentRender();

    bool EglInit(void *window);
    bool EglPrepareDraw() const;
    bool EglFinishDraw() const;

    bool IsEglReady() const;
    /** Light hide: drain GPU work and destroy EGLSurface; context/display unchanged. */
    void InvalidateEglSurface();
    /** @return true when the shared EGL display was terminated (no renders remain). */
    bool ReleaseEGL();
    void TerminateEGL();

    void RegisterFrameCallback();
    void UnregisterFrameCallback();

    /** Native DVSync (OH_NativeVSync_DVSyncSwitch); [enable] false releases resources. API &lt; 14 no-op (capi起始版本). */
    void SetNativeDvsyncEnabled(bool enable);
    void ReleaseNativeDvsync();

    std::string const id;
    OH_NativeXComponent *const component;
    ArkUIViewController *controller;

private:
    EGLNativeWindowType eglWindow;
    EGLSurface eglSurface = EGL_NO_SURFACE;
    EGLContext eglContext = EGL_NO_CONTEXT;
    static EGLDisplay eglDisplay;
    static EGLConfig eglConfig;
    OH_NativeXComponent_Callback callback;
    OH_NativeXComponent_MouseEvent_Callback mouseCallback;
    /** OH_NativeVSync* for DVSync; opaque to header consumers. */
    void *dvsyncNative_ = nullptr;
    /**
     * Set to true before any EGL teardown (InvalidateEglSurface / releaseEGL / terminateEGL);
     * cleared only on successful EglInit. Draw hot-path checks this without holding the mutex
     * so it can skip one frame rather than contend on the lock.
     */
    std::atomic<bool> teardownPending_{false};

    bool releaseEGL();
    void terminateEGL();
    /** Binds this render's surface/context, drains GPU work, then unbinds. */
    void DrainEglGpuWorkBeforeSurfaceTeardown();
    /** Destroys a stale EGLSurface after GPU work has drained (show rebind / heavy cleanup). */
    void DestroyStaleEglSurface();
};
} // namespace androidx::compose::ui::arkui::utils

#endif