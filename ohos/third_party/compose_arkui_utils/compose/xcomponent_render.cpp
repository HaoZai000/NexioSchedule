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

#include "xcomponent_render.h"
#include "arkui_view_controller.h"
#include "ohos_hiappevent_xperf_bridge.h"
#include "xcomponent_constant.h"
#include "xcomponent_holder.h"
#include "xcomponent_log.h"
#include "xcomponent_utils.h"
#include "deviceinfo.h"
#include <GLES3/gl3.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <native_vsync/native_vsync.h>
#include <atomic>
#include <cstdlib>
#include <cstring>
#include <mutex>

using namespace androidx::compose::ui::arkui::utils::XComponentUtils;

namespace androidx::compose::ui::arkui::utils {

namespace {
std::recursive_mutex gEglDisplayMutex;
} // namespace

EGLDisplay XComponentRender::eglDisplay = EGL_NO_DISPLAY;
EGLConfig XComponentRender::eglConfig = EGL_NO_CONFIG_KHR;

#define NEW_RENDERER_PREFIX "Maleoon "
#define NEW_RENDERER_MIN_VERSION "Maleoon 920"
#define OHOS_SUPPORT_ABI_MIN 14
// OH_NativeXComponent_GetKeyEventModifierKeyStates — API 20+ only; absent on API 17 (SIGSEGV if called).
#define OHOS_KEY_EVENT_MODIFIER_KEY_STATES_API_MIN 20

static int isNewRenderer = -1;

// SR003 / Mac ARM log tests: stable substrings for cold-start first VSync (see sr003_vsync_utils.py).
static std::atomic<bool> gFirstVsyncRequestedLogged{false};
static std::atomic<bool> gFirstVsyncCallbackLogged{false};

static bool newRenderer() {
    if (isNewRenderer < 0) {
        auto renderer = (const char *)glGetString(GL_RENDERER);
        // start with `NEW_RENDERER_PREFIX` and more than `NEW_RENDERER_MIN_VERSION`.
        isNewRenderer = renderer != nullptr &&
                        strncmp(renderer, NEW_RENDERER_PREFIX, strlen(NEW_RENDERER_PREFIX)) == 0 &&
                        strcmp(renderer, NEW_RENDERER_MIN_VERSION) >= 0;
    }
    return isNewRenderer;
}

static void OnFrameCallbackCB(OH_NativeXComponent *component, uint64_t timestamp, uint64_t targetTimestamp) {
    if (!gFirstVsyncCallbackLogged.exchange(true)) {
        LOGI("VsyncCallbackInner: First vsync has come back");
    }
    auto controller = XComponentHolder::GetInstance()->GetArkUIViewController(component);
    if (controller == nullptr) {
        LOGE("XComponentRender: OnFrameCallbackCB: controller is null for component %{public}p", component);
        return;
    }
    ArkUIViewController_onFrame(controller, timestamp, targetTimestamp);
}

static void OnSurfaceCreatedCB(OH_NativeXComponent *component, void *window) {
    LOGI("XComponentRender: OnSurfaceCreatedCB, component=%{public}p, window=%{public}p", component, window);
    OH_NativeXComponent_RegisterOnFrameCallback(component, OnFrameCallbackCB);

    if (component == nullptr || window == nullptr) {
        LOGE("XComponentRender: OnSurfaceCreatedCB: component or window is null");
        return;
    }

    auto render = XComponentHolder::GetInstance()->GetXComponentRender(component);
    if (render == nullptr) {
        LOGE("XComponentRender: OnSurfaceCreatedCB: render is null");
        return;
    }
    if (!render->EglInit(window)) {
        LOGE("XComponentRender: OnSurfaceCreatedCB: EglInit failed");
    }

    auto controller = XComponentHolder::GetInstance()->GetArkUIViewController(component);
    if (controller == nullptr) {
        LOGE("XComponentRender: OnSurfaceCreatedCB: controller is null");
        return;
    }
    uint64_t width;
    uint64_t height;
    int32_t result = OH_NativeXComponent_GetXComponentSize(component, window, &width, &height);

    if (result != OH_NATIVEXCOMPONENT_RESULT_SUCCESS) {
        LOGE("XComponentRender: OnSurfaceCreatedCB: unable to get component size");
        return;
    }
    ArkUIViewController_onSurfaceCreated(controller, component, width, height);
    ::compose::hiappevent::InitXperfBridge();
}
static void OnSurfaceChangedCB(OH_NativeXComponent *component, void *window) {
    LOGI("XComponentRender: OnSurfaceChangedCB");
    if (component == nullptr || window == nullptr) {
        LOGE("XComponentRender: OnSurfaceChangedCB: component or window is null");
        return;
    }

    uint64_t width;
    uint64_t height;
    int32_t result = OH_NativeXComponent_GetXComponentSize(component, window, &width, &height);
    if (result != OH_NATIVEXCOMPONENT_RESULT_SUCCESS) {
        LOGE("XComponentRender: OnSurfaceChangedCB: unable to get component size");
        return;
    }

    auto render = XComponentHolder::GetInstance()->GetXComponentRender(component);
    if (render != nullptr && window != nullptr) {
        if (!render->EglInit(window)) {
            LOGE("XComponentRender: OnSurfaceChangedCB: EglInit failed");
        }
    }

    auto controller = XComponentHolder::GetInstance()->GetArkUIViewController(component);
    if (controller == nullptr) {
        LOGE("XComponentRender: OnSurfaceChangedCB: controller is null");
        return;
    }
    ArkUIViewController_onSurfaceChanged(controller, width, height);
}

static void OnSurfaceShowCB(OH_NativeXComponent *component, void *window) {
    LOGI("XComponentRender: OnSurfaceShowCB, component=%{public}p, window=%{public}p", component, window);
    if (component == nullptr || window == nullptr) {
        LOGE("XComponentRender: OnSurfaceShowCB: component or window is null");
        return;
    }
    auto render = XComponentHolder::GetInstance()->GetXComponentRender(component);
    if (render == nullptr) {
        LOGE("XComponentRender: OnSurfaceShowCB: render is null");
        return;
    }
    if (render != nullptr && window != nullptr && !render->IsEglReady()) {
        // Light hide (InvalidateEglSurface) clears the window surface; bind the new native window here.
        // Context/display are reused — EglInit only recreates EGLSurface when needed.
        if (!render->EglInit(window)) {
            LOGE("XComponentRender: OnSurfaceShowCB: EglInit failed");
        }
    }
    auto controller = XComponentHolder::GetInstance()->GetArkUIViewController(component);
    ArkUIViewController_onSurfaceShow(controller);
}

static void OnSurfaceHideCB(OH_NativeXComponent *component, void *window) {
    LOGI("XComponentRender: OnSurfaceHideCB, component=%{public}p", component);
    // Stop native frame delivery immediately; Kotlin suspend is not enough for in-flight callbacks.
    OH_NativeXComponent_UnregisterOnFrameCallback(component);
    auto controller = XComponentHolder::GetInstance()->GetArkUIViewController(component);
    if (controller != nullptr) {
        ArkUIViewController_onSurfaceHide(controller);
    } else {
        LOGE("XComponentRender: OnSurfaceHideCB: controller is null");
    }
    auto render = XComponentHolder::GetInstance()->GetXComponentRender(component);
    if (render == nullptr) {
        return;
    }
    if (controller != nullptr) {
        render->InvalidateEglSurface();
    } else {
        render->ReleaseEGL();
    }
}

static void OnSurfaceDestroyedCB(OH_NativeXComponent *component, void *window) {
    LOGI("XComponentRender: OnSurfaceDestroyedCB");
    OH_NativeXComponent_UnregisterOnFrameCallback(component);

    auto controller = XComponentHolder::GetInstance()->GetArkUIViewController(component);
    ArkUIViewController_onSurfaceDestroyed(controller);

    auto render = XComponentHolder::GetInstance()->GetXComponentRender(component);
    // render is nullable, but it has no effect to delete a nullptr.
    delete render;
}

// Events are currently dispatched from the ArkView layer.
static void DispatchTouchEventCB(OH_NativeXComponent *component, void *window) {}

static void OnFocusEventCB(OH_NativeXComponent *component, void *window) {
    LOGI("XComponentRender: OnFocusEventCB");
    auto controller = XComponentHolder::GetInstance()->GetArkUIViewController(component);
    ArkUIViewController_onFocusEvent(controller);
}

static void OnKeyEventCB(OH_NativeXComponent *component, void * /* window */) {
    OH_NativeXComponent_KeyEvent *keyEvent = nullptr;
    const int32_t getKeyRc = OH_NativeXComponent_GetKeyEvent(component, &keyEvent);
    if (getKeyRc != OH_NATIVEXCOMPONENT_RESULT_SUCCESS || keyEvent == nullptr) {
        LOGW("XComponentRender: OnKeyEventCB: GetKeyEvent failed, falling back to legacy onKeyEvent");
        auto controller = XComponentHolder::GetInstance()->GetArkUIViewController(component);
        ArkUIViewController_onKeyEvent(controller);
        return;
    }

    OH_NativeXComponent_KeyAction keyAction{};
    OH_NativeXComponent_KeyCode keyCode{};
    uint64_t mod = 0;
    OH_NativeXComponent_GetKeyEventAction(keyEvent, &keyAction);
    OH_NativeXComponent_GetKeyEventCode(keyEvent, &keyCode);
    if (OH_GetSdkApiVersion() >= OHOS_KEY_EVENT_MODIFIER_KEY_STATES_API_MIN) {
        if (OH_NativeXComponent_GetKeyEventModifierKeyStates(keyEvent, &mod) != 0) {
            mod = 0;
        }
    }

    auto controller = XComponentHolder::GetInstance()->GetArkUIViewController(component);
    if (controller == nullptr) {
        LOGE("XComponentRender: OnKeyEventCB: controller is null");
        return;
    }
    ArkUIViewController_dispatchComposeKeyEvent(controller, static_cast<int64_t>(keyCode),
                                                static_cast<int32_t>(keyAction), mod);
}

static void DispatchMouseEventCB(OH_NativeXComponent *component, void *window) {
    auto controller = XComponentHolder::GetInstance()->GetArkUIViewController(component);
    ArkUIViewController_dispatchMouseEvent(controller);
}
static void DispatchHoverEventCB(OH_NativeXComponent *component, bool isHover) {
    LOGI("XComponentRender: DispatchHoverEventCB");
    auto controller = XComponentHolder::GetInstance()->GetArkUIViewController(component);
    ArkUIViewController_dispatchHoverEvent(controller);
}

XComponentRender::XComponentRender(OH_NativeXComponent *nativeXComponent)
    : id(XComponentUtils::GetXComponentId(nativeXComponent)), component(nativeXComponent),
      callback{.OnSurfaceCreated = OnSurfaceCreatedCB,
               .OnSurfaceChanged = OnSurfaceChangedCB,
               .OnSurfaceDestroyed = OnSurfaceDestroyedCB,
               .DispatchTouchEvent = DispatchTouchEventCB},
      mouseCallback{.DispatchMouseEvent = DispatchMouseEventCB, .DispatchHoverEvent = DispatchHoverEventCB} {

    OH_NativeXComponent_RegisterCallback(this->component, &callback);
    OH_NativeXComponent_RegisterSurfaceShowCallback(this->component, OnSurfaceShowCB);
    OH_NativeXComponent_RegisterSurfaceHideCallback(this->component, OnSurfaceHideCB);
    OH_NativeXComponent_RegisterFocusEventCallback(this->component, OnFocusEventCB);
    // API < 20: OH_NativeXComponent_GetKeyEventModifierKeyStates is absent (SIGSEGV if called).
    // Keys on low API Skia are dispatched from ArkTS onKeyEvent instead.
    if (OH_GetSdkApiVersion() >= OHOS_KEY_EVENT_MODIFIER_KEY_STATES_API_MIN) {
        OH_NativeXComponent_RegisterKeyEventCallback(this->component, OnKeyEventCB);
    } else {
        LOGI("XComponentRender: skip RegisterKeyEventCallback (api=%{public}d < %{public}d), keys via ArkTS",
             OH_GetSdkApiVersion(), OHOS_KEY_EVENT_MODIFIER_KEY_STATES_API_MIN);
    }
    OH_NativeXComponent_RegisterMouseEventCallback(this->component, &mouseCallback);
}

// OH_NativeXComponent is managed by js engine, should be destroyed by js gc.
// ArkUIViewController is managed by js engine, should be destroyed by js gc.
XComponentRender::~XComponentRender() {
    ReleaseNativeDvsync();
    auto controller_ = this->controller;
    auto renderer = ArkUIViewController_getXComponentRender(controller_);
    if (controller_ != nullptr && renderer != nullptr && renderer == this) {
        // Reset controller render to nullptr
        ArkUIViewController_setXComponentRender(controller_, nullptr);
    }
    XComponentHolder::GetInstance()->RemoveXComponentRender(this->id);
    terminateEGL();
};

void XComponentRender::terminateEGL() {
    teardownPending_.store(true, std::memory_order_release);
    std::lock_guard<std::recursive_mutex> lock(gEglDisplayMutex);
    LOGI("XComponentRender: terminateEGL, display=%{public}p, surface=%{public}p, context=%{public}p",
         eglDisplay,
         eglSurface,
         eglContext);
    if (eglDisplay != EGL_NO_DISPLAY) {
        eglWindow = 0;
        DestroyStaleEglSurface();
        if (eglContext != EGL_NO_CONTEXT) {
            if (eglGetCurrentContext() != eglContext) {
                if (eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, eglContext)) {
                    glFinish();
                    eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
                } else {
                    LOGW("XComponentRender: terminateEGL: eglMakeCurrent (NO_SURFACE) failed. Surfaceless context may not be supported by driver.");
                }
            } else {
                glFinish();
                eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            }
            eglDestroyContext(eglDisplay, eglContext);
            eglContext = EGL_NO_CONTEXT;
        }
    }
}

bool XComponentRender::EglInit(void *window) {
    std::lock_guard<std::recursive_mutex> lock(gEglDisplayMutex);
    LOGI("XComponentRender: EglInit, window=%{public}p, current_display=%{public}p, current_context=%{public}p",
         window,
         eglDisplay,
         eglContext);
    eglWindow = reinterpret_cast<EGLNativeWindowType>(window);
    if (eglWindow == 0) {
        LOGE("XComponentRender: EglInit: eglWindow is null");
        return false;
    }

    if (eglDisplay != EGL_NO_DISPLAY && eglContext != EGL_NO_CONTEXT) {
        DestroyStaleEglSurface();
        eglSurface = eglCreateWindowSurface(eglDisplay, eglConfig, eglWindow, NULL);
        if (eglSurface == EGL_NO_SURFACE) {
            EGLint error = eglGetError();
            LOGW("%{public}s: unable to recreate EGL surface, error=0x%{public}x. Will terminate EGL and retry full init.",
                 "XComponentRender: EglInit",
                 error);
            // 这里不调用 terminateEGL，因为可能还有其他实例在用 eglDisplay
            if (eglGetCurrentContext() == eglContext) {
                eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            }
            if (eglContext != EGL_NO_CONTEXT) {
                eglDestroyContext(eglDisplay, eglContext);
                eglContext = EGL_NO_CONTEXT;
            }
        } else {
            LOGI("XComponentRender: EglInit: reused Context and recreated Surface successfully, surface=%{public}p", eglSurface);
            // Surface rebind succeeded; clear teardown flag so draw can resume.
            teardownPending_ = false;
            return true;
        }
    }

    LOGI("XComponentRender: EglInit: starting full EGL initialization");
    eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) {
        LOGE("XComponentRender: EglInit: unable to get EGL display");
        return false;
    }

    EGLint majorVersion;
    EGLint minorVersion;
    if (!eglInitialize(eglDisplay, &majorVersion, &minorVersion)) {
        LOGE("XComponentRender: EglInit: eglInitialize failed, error=0x%{public}x", eglGetError());
        return false;
    }

    const EGLint configSize = 1;
    EGLint numConfig;
    if (!eglChooseConfig(eglDisplay, ATTRIB_LIST, &eglConfig, configSize, &numConfig)) {
        LOGE("XComponentRender: EglInit: eglChooseConfig failed");
        return false;
    }

    eglSurface = eglCreateWindowSurface(eglDisplay, eglConfig, eglWindow, NULL);
    if (eglSurface == EGL_NO_SURFACE) {
        LOGE("XComponentRender: EglInit: eglCreateWindowSurface failed, error=0x%{public}x", eglGetError());
        return false;
    }

    eglContext = eglCreateContext(eglDisplay, eglConfig, EGL_NO_CONTEXT, CONTEXT_ATTRIBS);
    if (eglContext == EGL_NO_CONTEXT) {
        LOGE("XComponentRender: EglInit: eglCreateContext failed, error=0x%{public}x", eglGetError());
        return false;
    }

    // Fast-path: EglInit succeeded; surface is live — clear teardown flag.
    teardownPending_ = false;
    LOGI("%{public}s: full initialization successful, display=%{public}p, surface=%{public}p, context=%{public}p",
         "XComponentRender: EglInit",
         eglDisplay,
         eglSurface,
         eglContext);
    return true;
}

bool XComponentRender::EglPrepareDraw() const {
    // Fast check without the mutex: if a teardown is in flight, skip this frame.
    if (teardownPending_.load(std::memory_order_acquire)) {
        if (IsEglReady() || eglWindow == 0) {
            LOGI("XComponentRender: EglPrepareDraw: teardown pending, skipping frame");
            return false;
        }
    }

    // Read EGL handles under a brief lock window; we only hold the lock while copying pointers.
    EGLDisplay display;
    EGLSurface surface;
    EGLContext context;
    EGLNativeWindowType window;
    {
        std::lock_guard<std::recursive_mutex> lock(gEglDisplayMutex);
        display = eglDisplay;
        surface = eglSurface;
        context = eglContext;
        window  = eglWindow;
    }

    if (window == 0) {
        LOGE("XComponentRender: EglPrepareDraw: eglWindow invalidated");
        return false;
    }
    if (display == EGL_NO_DISPLAY || surface == EGL_NO_SURFACE || context == EGL_NO_CONTEXT) {
        // Try to re-init EGL using the last known native window.
        if (window != 0) {
            auto self = const_cast<XComponentRender *>(this);
            const bool ok = self->EglInit(reinterpret_cast<void *>(window));
            if (!ok) {
                LOGE("XComponentRender: EglPrepareDraw: EnsureEglInit failed, eglWindow=%{public}p",
                        reinterpret_cast<void *>(window));
                return false;
            }
            // Refresh snapshot after re-init.
            {
                std::lock_guard<std::recursive_mutex> lock(gEglDisplayMutex);
                display = eglDisplay;
                surface = eglSurface;
                context = eglContext;
            }
        } else {
            LOGE("XComponentRender: EglPrepareDraw: EGL not ready and eglWindow is null");
            return false;
        }
    }

    // eglMakeCurrent is called outside the mutex; EGL spec guarantees it is thread-safe
    // provided no other thread destroys the same context concurrently. After makeCurrent
    // we do a second teardownPending_ check: if InvalidateEglSurface raced and destroyed the
    // surface between our snapshot and here, abandon this frame rather than drawing on a
    // stale surface.
    if (!eglMakeCurrent(display, surface, surface, context)) {
        LOGE("XComponentRender: EglPrepareDraw: unable to make current");
        return false;
    }
    if (teardownPending_.load(std::memory_order_acquire)) {
        LOGI("XComponentRender: EglPrepareDraw: teardown raced with makeCurrent, unbinding and skipping frame");
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        return false;
    }

    if (newRenderer()) {
        // 华为在 Maleoon 920 之后使用了不同的渲染方式，对此增加 glClear 解决此型号屏闪渲染问题。
        glClear(GL_COLOR_BUFFER_BIT);
    }
    return true;
}

bool XComponentRender::EglFinishDraw() const {
    // Fast check: skip swap if a teardown started between prepareDraw and now.
    if (teardownPending_.load(std::memory_order_acquire)) {
        LOGI("XComponentRender: EglFinishDraw: teardown pending, skipping swap");
        return false;
    }

    EGLDisplay display;
    EGLSurface surface;
    EGLNativeWindowType window;
    {
        std::lock_guard<std::recursive_mutex> lock(gEglDisplayMutex);
        display = eglDisplay;
        surface = eglSurface;
        window  = eglWindow;
    }

    if (window == 0 || display == EGL_NO_DISPLAY || surface == EGL_NO_SURFACE) {
        LOGE("XComponentRender: EglFinishDraw: eglDisplay or eglSurface is null");
        return false;
    }
    // eglSwapBuffers is called outside the mutex.
    if (!eglSwapBuffers(display, surface)) {
        const EGLint error = eglGetError();
        LOGE("XComponentRender: EglFinishDraw: eglSwapBuffers failed, error=0x%{public}x", error);
        auto self = const_cast<XComponentRender *>(this);
        self->InvalidateEglSurface();
        return false;
    }
    return true;
}

void XComponentRender::DrainEglGpuWorkBeforeSurfaceTeardown() {
    if (eglDisplay == EGL_NO_DISPLAY || eglSurface == EGL_NO_SURFACE || eglContext == EGL_NO_CONTEXT) {
        return;
    }

    // Tiered drain:
    //
    // Case A — context is already current on this thread (hide fires after last eglSwapBuffers
    //          without an intervening unbind): GPU commands were just submitted; glFinish is
    //          sufficient to drain. glFenceSync / eglWaitGL / eglWaitClient are all redundant
    //          after glFinish and add up to 500 ms of unnecessary blocking.
    //
    // Case B — context is NOT current (EglInit show-rebind, heavy cleanup, or first-ever call
    //          before any draw): the context was never made current on this thread since the last
    //          explicit unbind, so there are no pending GPU commands. eglDestroySurface handles
    //          any driver-side resource cleanup. Avoid the expensive makeCurrent + glFinish round-trip.
    if (eglGetCurrentContext() == eglContext) {
        // Case A: drain in-flight GPU work, then unbind.
        glFinish();
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        LOGI("XComponentRender: DrainEglGpuWork: context-current path, glFinish done");
    } else {
        // Case B: context is NOT current on this thread.
        // Temporarily make it current, call glFinish to drain in-flight GPU work, and restore previous context.
        EGLContext prevContext = eglGetCurrentContext();
        EGLDisplay prevDisplay = eglGetCurrentDisplay();
        EGLSurface prevDraw = eglGetCurrentSurface(EGL_DRAW);
        EGLSurface prevRead = eglGetCurrentSurface(EGL_READ);

        if (eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            glFinish();
            if (prevContext != EGL_NO_CONTEXT && prevContext != eglContext && prevDisplay != EGL_NO_DISPLAY) {
                eglMakeCurrent(prevDisplay, prevDraw, prevRead, prevContext);
            } else {
                eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            }
            LOGI("XComponentRender: DrainEglGpuWork: context-not-current path, temporarily made current and glFinish done");
        } else {
            LOGE("XComponentRender: DrainEglGpuWork: context-not-current path, eglMakeCurrent failed");
        }
    }
}

void XComponentRender::DestroyStaleEglSurface() {
    if (eglDisplay == EGL_NO_DISPLAY || eglSurface == EGL_NO_SURFACE) {
        return;
    }
    DrainEglGpuWorkBeforeSurfaceTeardown();
    const EGLSurface surfaceToDestroy = eglSurface;
    eglSurface = EGL_NO_SURFACE;
    if (!eglDestroySurface(eglDisplay, surfaceToDestroy)) {
        LOGW("XComponentRender: DestroyStaleEglSurface: destroy failed, error=0x%{public}x", eglGetError());
    } else {
        LOGI("XComponentRender: DestroyStaleEglSurface: surface destroyed");
    }
}

void XComponentRender::InvalidateEglSurface() {
    // Signal draw hot-path to skip the current/next frame before acquiring the lock.
    teardownPending_.store(true, std::memory_order_release);
    std::lock_guard<std::recursive_mutex> lock(gEglDisplayMutex);
    DestroyStaleEglSurface();
    if (eglDisplay != EGL_NO_DISPLAY && eglGetCurrentContext() == eglContext && eglContext != EGL_NO_CONTEXT) {
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    }
    LOGI("XComponentRender: InvalidateEglSurface: drained and destroyed surface on hide");
}

bool XComponentRender::ReleaseEGL() { return releaseEGL(); }

void XComponentRender::TerminateEGL() { terminateEGL(); }

bool XComponentRender::IsEglReady() const {
    return eglWindow != 0 && eglDisplay != EGL_NO_DISPLAY && eglSurface != EGL_NO_SURFACE &&
           eglContext != EGL_NO_CONTEXT;
}

bool XComponentRender::releaseEGL() {
    teardownPending_.store(true, std::memory_order_release);
    std::lock_guard<std::recursive_mutex> lock(gEglDisplayMutex);
    ReleaseNativeDvsync();
    bool displayTerminated = false;
    if (eglDisplay != EGL_NO_DISPLAY) {
        DestroyStaleEglSurface();
        if (eglContext != EGL_NO_CONTEXT) {
            eglDestroyContext(eglDisplay, eglContext);
            eglContext = EGL_NO_CONTEXT;
        }

        // Patch behavior: In the patch, eglTerminate(eglDisplay) is called unconditionally.
        // But for multi-instance support, we only terminate if no more renders are active.
        if (XComponentHolder::GetInstance()->GetRenderCount() == 0) {
            LOGI("XComponentRender: releaseEGL: No more active renders, terminating EGL display");
            eglTerminate(eglDisplay);
            eglDisplay = EGL_NO_DISPLAY;
            eglConfig = EGL_NO_CONFIG_KHR;
            displayTerminated = true;
        } else {
            LOGI("XComponentRender: releaseEGL: %{public}zu renders still active, keeping EGL display",
                 XComponentHolder::GetInstance()->GetRenderCount());
        }
        LOGI("XComponentRender: DMA Surface released, Context cleaned.");
    }
    return displayTerminated;
}

void XComponentRender::RegisterFrameCallback() {
    if (!gFirstVsyncRequestedLogged.exchange(true)) {
        LOGI("RequestVsync: First vsync has requested");
    }
    OH_NativeXComponent_RegisterOnFrameCallback(component, OnFrameCallbackCB);
}

void XComponentRender::UnregisterFrameCallback() { OH_NativeXComponent_UnregisterOnFrameCallback(component); }

void XComponentRender::ReleaseNativeDvsync() {
    auto *vs = reinterpret_cast<OH_NativeVSync *>(dvsyncNative_);
    if (vs == nullptr) {
        LOGD("XComponentRender::ReleaseNativeDvsync: already null (render=%{public}p)", this);
        return;
    }
    LOGI("XComponentRender::ReleaseNativeDvsync: DVSyncSwitch(false)+Destroy (render=%{public}p vsync=%{public}p)",
         this, vs);
    (void)OH_NativeVSync_DVSyncSwitch(vs, false);
    OH_NativeVSync_Destroy(vs);
    dvsyncNative_ = nullptr;
}

void XComponentRender::SetNativeDvsyncEnabled(bool enable) {
    LOGI("XComponentRender::SetNativeDvsyncEnabled: enter enable=%{public}d render=%{public}p api=%{public}d",
         enable ? 1 : 0, this, OH_GetSdkApiVersion());
    if (OH_GetSdkApiVersion() < OHOS_SUPPORT_ABI_MIN) {
        LOGI("XComponentRender::SetNativeDvsyncEnabled: skip (api < %{public}d)", OHOS_SUPPORT_ABI_MIN);
        return;
    }
    if (!enable) {
        ReleaseNativeDvsync();
        LOGI("XComponentRender::SetNativeDvsyncEnabled: disabled (render=%{public}p)", this);
        return;
    }

    // Do NOT cast [eglWindow] to OHNativeWindow* and call OH_NativeWindow_GetSurfaceId: XComponent passes
    // a native object suitable for eglCreateWindowSurface(EGLNativeWindowType), which is not guaranteed to
    // be the same type / layout as OHNativeWindow — that mismatch caused SIGSEGV (SEGV_ACCERR) in the field.
    // Window-bound OH_NativeVSync_Create_ForAssociatedWindow requires a real surface id from OH_NativeWindow;
    // reintroduce that path only when the platform exposes a documented OHNativeWindow from this pipeline.
    auto *existing = reinterpret_cast<OH_NativeVSync *>(dvsyncNative_);
    if (existing != nullptr) {
        const int sw = OH_NativeVSync_DVSyncSwitch(existing, true);
        LOGI("%{public}s: reuse OH_NativeVSync_Create handle, DVSyncSwitch ret=%{public}d render=%{public}p",
             "XComponentRender::SetNativeDvsyncEnabled", sw, this);
        return;
    }

    static const char kTag[] = "cmp_dvsync";
    const unsigned int kTagLen = static_cast<unsigned int>(sizeof(kTag) - 1U);
    OH_NativeVSync *vsync = OH_NativeVSync_Create(kTag, kTagLen);
    if (vsync == nullptr) {
        LOGE("XComponentRender::SetNativeDvsyncEnabled: OH_NativeVSync_Create failed");
        return;
    }
    dvsyncNative_ = vsync;
    const int sw = OH_NativeVSync_DVSyncSwitch(vsync, true);
    LOGI("%{public}s: OH_NativeVSync_Create + DVSyncSwitch ret=%{public}d render=%{public}p",
         "XComponentRender::SetNativeDvsyncEnabled", sw, this);
}

} // namespace androidx::compose::ui::arkui::utils
