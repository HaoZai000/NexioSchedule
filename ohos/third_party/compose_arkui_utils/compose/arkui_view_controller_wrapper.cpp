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

#include "libkn_api.h"
#include "arkui_view_controller_wrapper.h"
#include "arkui_view_controller.h"
#include "arkui_utils_export.h"
#include "napi/native_api.h"
#include "skikobridge/ComposeDispatcher.h"
#include "xcomponent_holder.h"
#include "xcomponent_log.h"
#include "xcomponent_render.h"
#include "xcomponent_utils.h"
#include <arkui/native_node_napi.h>
#include <dlfcn.h>
#include <hilog/log.h>

namespace androidx::compose::ui::arkui::utils::ArkUIViewControllerWrapper {
// NOTE: hilog/log.h defines LOG_DOMAIN as a macro, so avoid using that identifier here.
static constexpr uint32_t OHOS_LOG_DOMAIN = 0x0000;

constexpr int RENDERER_BACKEND_ID_UNKNOWN = -1;
constexpr int RENDERER_BACKEND_ID_SKIA = 0;
constexpr int RENDERER_BACKEND_ID_FUSION_RENDERER = 1;

constexpr int RENDERER_BACKEND_LINK_NONE = 0;
constexpr int RENDERER_BACKEND_LINK_WEAK = 1;
constexpr int RENDERER_BACKEND_LINK_DLSYM = 2;

constexpr size_t SET_FUSION_RENDERER_ROOT_NODE_ARG_COUNT = 2;

static int GetCachedRenderBackendId(int *fnModeOut) {
    // Cache once per process. Backend is a build-time constant, safe to cache.
    static int cachedId = RENDERER_BACKEND_ID_UNKNOWN;
    static int cachedFnMode = RENDERER_BACKEND_LINK_WEAK;
    if (cachedId == RENDERER_BACKEND_ID_UNKNOWN) {
        cachedId = androidx_compose_ui_arkui_ArkUIViewController_getRendererTypeId();
        OH_LOG_Print(LOG_APP, LOG_INFO, OHOS_LOG_DOMAIN, "render_mode",
            "androidx_compose_ui_arkui_ArkUIViewController_getRendererTypeId id=%{public}d", cachedId);
    }

    if (fnModeOut) *fnModeOut = cachedFnMode;
    return cachedId;
}

static napi_value GetRenderBackendId(napi_env env, napi_callback_info info) {
    int fnMode = RENDERER_BACKEND_LINK_NONE;
    int id = GetCachedRenderBackendId(&fnMode);
    OH_LOG_Print(LOG_APP, LOG_INFO, OHOS_LOG_DOMAIN, "render_mode", "getRenderBackendId id=%{public}d fnMode=%{public}d", id, fnMode);
    napi_value result = nullptr;
    napi_create_int32(env, id, &result);
    return result;
}

static napi_value GetRenderBackend(napi_env env, napi_callback_info info) {
    int fnMode = RENDERER_BACKEND_LINK_NONE;
    int id = GetCachedRenderBackendId(&fnMode);
    const char *backend = (id == 0) ? "skia" : "fusion-renderer";
    OH_LOG_Print(LOG_APP, LOG_INFO, OHOS_LOG_DOMAIN, "render_mode", "getRenderBackend id=%{public}d backend=%{public}s fnMode=%{public}d", id, backend, fnMode);

    napi_value result = nullptr;
    napi_create_string_utf8(env, backend, NAPI_AUTO_LENGTH, &result);
    return result;
}

static napi_value GetId(napi_env env, napi_callback_info info) {
    size_t argc = 0;
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, nullptr, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        napi_value empty = nullptr;
        napi_create_string_utf8(env, "", NAPI_AUTO_LENGTH, &empty);
        return empty;
    }
    std::string id = ArkUIViewController_getId(reinterpret_cast<ArkUIViewController *>(controller));
    napi_value result = nullptr;
    napi_create_string_utf8(env, id.c_str(), id.size(), &result);
    return result;
}

static void FinalizeArkViewController(napi_env env, void *data, void *hint) {
    auto controller = reinterpret_cast<ArkUIViewController *>(data);
    auto id = ArkUIViewController_getId(controller);
    auto render = reinterpret_cast<XComponentRender *>(ArkUIViewController_getXComponentRender(controller));
    if (render != nullptr && render->controller == controller) {
        // 解除赋值，重置为 nullptr
        render->controller = nullptr;
    }
    XComponentHolder::GetInstance()->RemoveArkUIViewController(id);
    ArkUIViewController_onFinalize(controller);
}

static napi_value Init(napi_env env, napi_callback_info info) {
    size_t argc = 4;
    napi_value args[4] = {nullptr, nullptr, nullptr, nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    char idString[OH_XCOMPONENT_ID_BUFFER_LEN_MAX] = {0};
    size_t idSize = 0;
    napi_get_value_string_utf8(env, args[0], idString, OH_XCOMPONENT_ID_BUFFER_LEN_MAX, &idSize);

    std::string id(idString);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    ArkUIViewController_setId(typedController, id);
    ArkUIViewController_setEnv(typedController, env);
    // Save build-time backend id into Kotlin/core as a global flag (no developer runtime config).
    ArkUIViewController_setRenderBackendId(typedController, GetCachedRenderBackendId(nullptr));
    ArkUIViewController_setRootView(typedController, args[1], args[2], args[3]);

    XComponentHolder::GetInstance()->InitArkViewController(env, id, typedController);
    return nullptr;
}

static napi_value InitContext(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    ArkUIViewController_setContext(typedController, args[0]);
    return nullptr;
}

static napi_value InitUIContext(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    ArkUIViewController_setUIContext(typedController, args[0]);
    return nullptr;
}

static napi_value InitMessenger(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    ArkUIViewController_setMessenger(typedController, args[0]);
    return nullptr;
}

static napi_value RegisterLocaleAndStringProvider(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    ArkUIViewController_setLocaleAndStringProvider(typedController, args[0]);
    skikobridge_set_platform_provider(env, args[0]);
    return nullptr;
}

static napi_value AboutToAppear(napi_env env, napi_callback_info info) {
    size_t argc = 0;
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, nullptr, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController_aboutToAppear(reinterpret_cast<ArkUIViewController *>(controller));
    return nullptr;
}

static napi_value AboutToDisappear(napi_env env, napi_callback_info info) {
    size_t argc = 0;
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, nullptr, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController_aboutToDisappear(reinterpret_cast<ArkUIViewController *>(controller));
    return nullptr;
}

static napi_value OnConfigurationUpdate(napi_env env, napi_callback_info info) {
    size_t argc = 0;
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, nullptr, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController_onConfigurationUpdate(reinterpret_cast<ArkUIViewController *>(controller));
    return nullptr;
}

static napi_value OnPageShow(napi_env env, napi_callback_info info) {
    size_t argc = 0;
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, nullptr, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController_onPageShow(reinterpret_cast<ArkUIViewController *>(controller));
    return nullptr;
}

static napi_value OnPageHide(napi_env env, napi_callback_info info) {
    size_t argc = 0;
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, nullptr, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController_onPageHide(reinterpret_cast<ArkUIViewController *>(controller));
    return nullptr;
}

static napi_value OnSurfaceShow(napi_env env, napi_callback_info info) {
    size_t argc = 0;
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, nullptr, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController_onSurfaceShow(reinterpret_cast<ArkUIViewController *>(controller));
    return nullptr;
}

static napi_value OnSurfaceHide(napi_env env, napi_callback_info info) {
    size_t argc = 0;
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, nullptr, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController_onSurfaceHide(reinterpret_cast<ArkUIViewController *>(controller));
    return nullptr;
}

static napi_value OnBackPress(napi_env env, napi_callback_info info) {
    size_t argc = 0;
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, nullptr, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        napi_value result = nullptr;
        napi_get_boolean(env, false, &result);
        return result;
    }
    bool consumed = ArkUIViewController_onBackPress(reinterpret_cast<ArkUIViewController *>(controller));

    napi_value result = nullptr;
    napi_get_boolean(env, consumed, &result);
    return result;
}

static napi_value DispatchTouchEvent(napi_env env, napi_callback_info info) {
    size_t argc = 2;
    napi_value args[2] = {nullptr, nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    napi_value touchEvent = args[0];
    bool ignoreInteropView;
    napi_get_value_bool(env, args[1], &ignoreInteropView);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }

    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    ArkUIViewController_dispatchTouchEvent(typedController, touchEvent, ignoreInteropView);
    return nullptr;
}

static napi_value DispatchComposeMouseEvent(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    void *controller = nullptr;
    napi_unwrap(env, thisArg, &controller);

    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    napi_value mouseEvent = args[0];
    const bool consumed =
        ArkUIViewController_dispatchComposeMouseEvent(typedController, mouseEvent);
    napi_value result = nullptr;
    napi_get_boolean(env, consumed, &result);
    return result;
}

static napi_value DispatchComposePinchEvent(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    void *controller = nullptr;
    napi_unwrap(env, thisArg, &controller);

    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    napi_value pinchEvent = args[0];
    const bool consumed =
        ArkUIViewController_dispatchComposePinchEvent(typedController, pinchEvent);
    napi_value result = nullptr;
    napi_get_boolean(env, consumed, &result);
    return result;
}

static napi_value DispatchComposeKeyEvent(napi_env env, napi_callback_info info) {
    size_t argc = 3;
    napi_value args[3] = {nullptr, nullptr, nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    int64_t keyCode = 0;
    int32_t nativeAction = -1;
    int64_t modifierKeyStates = 0;
    if (argc > 0) napi_get_value_int64(env, args[0], &keyCode);
    if (argc > 1) napi_get_value_int32(env, args[1], &nativeAction);
    if (argc > 2) napi_get_value_int64(env, args[2], &modifierKeyStates);

    void *controller = nullptr;
    napi_unwrap(env, thisArg, &controller);
    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    bool consumed = ArkUIViewController_dispatchComposeKeyEvent(
        typedController,
        keyCode,
        nativeAction,
        static_cast<uint64_t>(modifierKeyStates)
    );

    napi_value result = nullptr;
    napi_get_boolean(env, consumed, &result);
    return result;
}

static napi_value KeyboardWillShow(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    double keyboardHeight;
    napi_get_value_double(env, args[0], &keyboardHeight);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }

    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    ArkUIViewController_keyboardWillShow(typedController, keyboardHeight);

    return nullptr;
}

static napi_value KeyboardWillHide(napi_env env, napi_callback_info info) {
    size_t argc = 0;
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, nullptr, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }

    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    ArkUIViewController_keyboardWillHide(typedController);

    return nullptr;
}

static napi_value RequestSyncRefresh(napi_env env, napi_callback_info info) {
    size_t argc = 0;
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, nullptr, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        napi_value result = nullptr;
        napi_create_int32(env, 0, &result);
        return result;
    }

    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    int consumed = ArkUIViewController_requestSyncRefresh(typedController);
    napi_value result = nullptr;
    napi_create_int32(env, consumed, &result);
    return result;
}

static napi_value CancelSyncRefresh(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    double refreshId;
    napi_get_value_double(env, args[0], &refreshId);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }

    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    ArkUIViewController_cancelSyncRefresh(typedController, refreshId);

    return nullptr;
}

static napi_value SendMessage(napi_env env, napi_callback_info info) {
    size_t argc = 2;
    napi_value args[2] = {nullptr, nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    size_t typeSize = 0;
    napi_get_value_string_utf8(env, args[0], nullptr, 0, &typeSize);
    std::string type(typeSize, '\0');
    napi_get_value_string_utf8(env, args[0], type.data(), typeSize + 1, &typeSize);

    size_t messageSize = 0;
    napi_get_value_string_utf8(env, args[1], nullptr, 0, &messageSize);
    std::string message(messageSize, '\0');
    napi_get_value_string_utf8(env, args[1], message.data(), messageSize + 1, &messageSize);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        napi_value result = nullptr;
        napi_get_null(env, &result);
        return result;
    }
    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);

    auto response = ArkUIViewController_sendMessage(typedController, type, message);

    napi_value result = nullptr;

    if (response == nullptr) {
        napi_get_null(env, &result);
    } else {
        napi_create_string_utf8(env, response, NAPI_AUTO_LENGTH, &result);
    }
    return result;
}

static napi_value OnSurfaceDestroyed(napi_env env, napi_callback_info info)
{
    size_t argc = 0;
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, nullptr, &thisArg, nullptr);
    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController_onSurfaceDestroyed(reinterpret_cast<ArkUIViewController *>(controller));
    return nullptr;
}

static napi_value OnSurfaceCreated(napi_env env, napi_callback_info info) {
    size_t argc = 2;
    napi_value args[2] = {nullptr, nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);
    int width;
    napi_get_value_int32(env, args[0], &width);
    int height;
    napi_get_value_int32(env, args[1], &height);
    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController_onSurfaceCreated(reinterpret_cast<ArkUIViewController *>(controller), nullptr, width, height);
    return nullptr;
}

static napi_value OnFrame(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }

    int64_t timeStamp;
    napi_get_value_int64(env, args[0], &timeStamp);

    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    ArkUIViewController_onFrame(typedController, timeStamp, -1);
    return nullptr;
}

static napi_value OnIdle(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    int64_t timeLeft;
    napi_get_value_int64(env, args[0], &timeLeft);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }

    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    ArkUiViewControllerOnIdle(typedController, timeLeft);

    return nullptr;
}

static napi_value Draw(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }

    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    ArkUIViewController_draw(typedController, args[0]);

    return nullptr;
}

static napi_value GetJsNode(napi_env env, napi_callback_info info)
{
    size_t argc = 0;
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, nullptr, &thisArg, nullptr);

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }

    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    return reinterpret_cast<napi_value>(ArkUiViewControllerGetJsNode(typedController));
}

static napi_value FindNodeIdAt(napi_env env, napi_callback_info info) {
    size_t argc = 2;
    napi_value args[2] = {nullptr, nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);

    auto returnMinusOne = [&]() -> napi_value {
        napi_value result = nullptr;
        napi_create_int32(env, -1, &result);
        return result;
    };

    if (argc < 2 || thisArg == nullptr) {
        return returnMinusOne();
    }

    double x = 0.0;
    double y = 0.0;
    if (napi_get_value_double(env, args[0], &x) != napi_ok || napi_get_value_double(env, args[1], &y) != napi_ok) {
        return returnMinusOne();
    }

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return returnMinusOne();
    }

    ArkUIViewController *typedController = reinterpret_cast<ArkUIViewController *>(controller);
    int nodeId = ArkUIViewController_findNodeIdAt(typedController, static_cast<float>(x), static_cast<float>(y));

    napi_value result = nullptr;
    napi_create_int32(env, nodeId, &result);
    return result;
}

// Weak symbol: only present in FusionRenderer skiko (not in Skia-only skiko).

// When linked against Skia-only skiko, this resolves to nullptr → JsNodeDraw becomes no-op.
extern "C" __attribute__((weak)) void org_jetbrains_skia_RenderNode__1nRenderNodeJsNodeDraw(napi_env env, napi_value node, napi_value canvas);

static napi_value JsNodeDraw(napi_env env, napi_callback_info info)
{
    if (!org_jetbrains_skia_RenderNode__1nRenderNodeJsNodeDraw) {
        // FusionRenderer skiko not linked (skia-only build) — no-op
        return nullptr;
    }
    size_t argc = 2;
    napi_value argv[2];
    napi_status status;

    status = napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
    const size_t kRequiredArgs = 2;
    if (status != napi_ok || argc < kRequiredArgs) {
        napi_throw_type_error(env, nullptr, "Expected 2 argument");
        return nullptr;
    }
    org_jetbrains_skia_RenderNode__1nRenderNodeJsNodeDraw(env, argv[1], argv[0]);
    return nullptr;
}

static napi_value SetParentScrollBridge(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value argv[1] = {nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, argv, &thisArg, nullptr);
    if (argc < 1 || argv[0] == nullptr) {
        napi_throw_type_error(env, nullptr, "setParentScrollBridge: Expected scroller argument");
        return nullptr;
    }

    void *controller = nullptr;
    napi_unwrap(env, thisArg, &controller);
    ArkUIViewController_setParentScrollBridge(reinterpret_cast<ArkUIViewController *>(controller), argv[0]);
    return nullptr;
}

// Sync entry called from ETS outer List `.onScrollFrameBegin`. Inner Compose consumes via
// ArkUIChildScrollBridge.dispatchPreScroll; return value is the consumed vp so ETS can compute
// offsetRemain. Returns 0 when controller / state unregistered (no-op fallback under skia).
static napi_value ConsumeOuterScroll(napi_env env, napi_callback_info info) {
    size_t argc = 2;
    napi_value argv[2] = {nullptr, nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, argv, &thisArg, nullptr);
    napi_value resultZero = nullptr;
    napi_create_double(env, 0.0, &resultZero);
    // Expected ETS call shape: `controller.consumeOuterScroll(offsetVp, sourceInt)`.
    //   2: argc < 2      : caller passed fewer than the required 2 arguments — bail out.
    //   0: argv[0] null  : missing `offsetVp` (Float, vp per frame from onScrollFrameBegin).
    //   1: argv[1] null  : missing `sourceInt` (1 = drag / UserInput, 2 = fling / SideEffect).
    if (argc < 2 || argv[0] == nullptr || argv[1] == nullptr) {
        return resultZero;
    }
    double offsetVp = 0.0;
    if (napi_get_value_double(env, argv[0], &offsetVp) != napi_ok) {
        return resultZero;
    }
    int32_t sourceInt = 0;
    napi_get_value_int32(env, argv[1], &sourceInt);

    void *controller = nullptr;
    napi_unwrap(env, thisArg, &controller);
    float consumedVp = ArkUIViewController_consumeOuterScroll(
        reinterpret_cast<ArkUIViewController *>(controller),
        static_cast<float>(offsetVp),
        sourceInt);
    napi_value result = nullptr;
    napi_create_double(env, static_cast<double>(consumedVp), &result);
    return result;
}

static napi_value OnSurfaceChanged(napi_env env, napi_callback_info info) {
    size_t argc = 2;
    napi_value args[2] = {nullptr, nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, args, &thisArg, nullptr);
    int width;
    napi_get_value_int32(env, args[0], &width);
    int height;
    napi_get_value_int32(env, args[1], &height);
    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController_onSurfaceChanged(reinterpret_cast<ArkUIViewController *>(controller), width, height);

    return nullptr;
}

static napi_value InitRenderNodeContext(napi_env env, napi_callback_info info) {
    size_t argc = 4;
    napi_value argv[4];
    napi_status status;

    status = napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
    const size_t kRequiredArgs = 4;
    if (status != napi_ok || argc < kRequiredArgs) {
        napi_throw_type_error(env, nullptr, "initRenderNodeContext: Expected 4 arguments (nodeConstructor, statusModifyConstructor, ratio, fixed)");
        return nullptr;
    }
    double ratio = 1.0;
    napi_get_value_double(env, argv[2], &ratio);
    bool fixed = false;
    napi_get_value_bool(env, argv[3], &fixed);
    ArkUIViewController_initRenderNodeContext(env, argv[0], argv[1], ratio, fixed);
    return nullptr;
}

static napi_value InitFusionRendererNode(napi_env env, napi_callback_info info) {
    constexpr int argIndexEnableCApi = 0;
    constexpr int argIndexRootContent = 1;
    constexpr int argIndexFrameMgr = 2;
    constexpr int argIndexIsPreCompose = 3;
    constexpr int minArgc = 3;
    constexpr int argcWithPreCompose = 4;

    size_t argc = argcWithPreCompose;
    napi_value argv[argcWithPreCompose];
    napi_value thisArg;
    napi_status status;

    status = napi_get_cb_info(env, info, &argc, argv, &thisArg, nullptr);
    if (status != napi_ok || argc < minArgc) {
        napi_throw_type_error(env, nullptr,
            "initFusionRendererNode: expected (enableCApi, rootContent, frameMgr)"
            " and optional 4th (isPreCompose)");
        return nullptr;
    }
    bool enableCApi = false;
    napi_get_value_bool(env, argv[argIndexEnableCApi], &enableCApi);
    bool isPreCompose = false;
    if (argc >= argcWithPreCompose) {
        napi_get_value_bool(env, argv[argIndexIsPreCompose], &isPreCompose);
    }

    void *controller;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        return nullptr;
    }
    ArkUIViewController_initFusionRendererNode(
        reinterpret_cast<ArkUIViewController *>(controller), env, enableCApi,
        argv[argIndexRootContent], argv[argIndexFrameMgr], isPreCompose);
    return nullptr;
}

static napi_value SetFusionRendererRootNode(napi_env env, napi_callback_info info) {
    size_t argc = SET_FUSION_RENDERER_ROOT_NODE_ARG_COUNT;
    napi_value argv[SET_FUSION_RENDERER_ROOT_NODE_ARG_COUNT];
    napi_value thisArg;
    napi_status status = napi_get_cb_info(env, info, &argc, argv, &thisArg, nullptr);
    if (status != napi_ok || argc < SET_FUSION_RENDERER_ROOT_NODE_ARG_COUNT) {
        napi_throw_type_error(
            env,
            nullptr,
            "setFusionRendererRootNode: Expected rootFrameNode and uiContext arguments");
        return nullptr;
    }

    void *controller = nullptr;
    napi_unwrap(env, thisArg, &controller);
    ArkUIViewController_setFusionRendererRootNode(
        reinterpret_cast<ArkUIViewController *>(controller),
        env,
        argv[0],
        argv[1]);
    return nullptr;
}

static napi_value InitLtpoFrameRateController(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value argv[1];
    napi_value thisArg;
    napi_status status;

    status = napi_get_cb_info(env, info, &argc, argv, &thisArg, nullptr);
    if (status != napi_ok || argc < 1) {
        napi_throw_type_error(env, nullptr, "initLtpoFrameRateController: Expected 1 argument (frameMgr)");
        return nullptr;
    }

    void *controller = nullptr;
    status = napi_unwrap(env, thisArg, &controller);
    if (status != napi_ok || controller == nullptr) {
        napi_throw_type_error(env, nullptr, "initLtpoFrameRateController: native controller is null");
        return nullptr;
    }
    ArkUIViewController_initLtpoFrameRateController(reinterpret_cast<ArkUIViewController *>(controller), env, argv[0]);
    return nullptr;
}

static napi_value SetAccessibilityFrameNode(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value argv[1] = {nullptr};
    napi_value thisArg = nullptr;
    napi_get_cb_info(env, info, &argc, argv, &thisArg, nullptr);
    if (argc < 1 || argv[0] == nullptr) {
        LOGE("SetAccessibilityFrameNode missing frameNode");
        return nullptr;
    }

    void *controller = nullptr;
    if (napi_unwrap(env, thisArg, &controller) != napi_ok || controller == nullptr) {
        LOGE("SetAccessibilityFrameNode missing controller");
        return nullptr;
    }

    auto typedController = reinterpret_cast<ArkUIViewController *>(controller);
    auto composeId = ArkUIViewController_getId(typedController);

    ArkUI_NodeHandle nodeHandle = nullptr;
    int32_t result = OH_ArkUI_GetNodeHandleFromNapiValue(env, argv[0], &nodeHandle);
    LOGD(
        "SetAccessibilityFrameNode composeId=%{public}s result=%{public}d nodeHandle=%{public}p",
        composeId.c_str(),
        result,
        nodeHandle);
    if (result == 0 && nodeHandle != nullptr) {
        androidx_compose_ui_arkui_utils_registerAccessibilityNodeHandle(composeId.c_str(), nodeHandle);
    }
    return nullptr;
}

static void bindFunction(napi_env env, napi_value object, const char *functionName, napi_callback functionCallback) {
    napi_value functionValue = nullptr;
    napi_create_function(env, functionName, NAPI_AUTO_LENGTH, functionCallback, nullptr, &functionValue);
    napi_set_named_property(env, object, functionName, functionValue);
}

napi_value Wrapped(napi_env env, void *nativeController) {
    napi_value result = nullptr;
    napi_create_object(env, &result);
    napi_wrap(env, result, nativeController, FinalizeArkViewController, nullptr, nullptr);

    bindFunction(env, result, "init", Init);
    bindFunction(env, result, "initContext", InitContext);
    bindFunction(env, result, "initUIContext", InitUIContext);
    bindFunction(env, result, "initMessenger", InitMessenger);
    bindFunction(env, result, "registerLocaleAndStringProvider", RegisterLocaleAndStringProvider);
    bindFunction(env, result, "aboutToAppear", AboutToAppear);
    bindFunction(env, result, "aboutToDisappear", AboutToDisappear);
    bindFunction(env, result, "onConfigurationUpdate", OnConfigurationUpdate);
    bindFunction(env, result, "onPageShow", OnPageShow);
    bindFunction(env, result, "onPageHide", OnPageHide);
    bindFunction(env, result, "onSurfaceShow", OnSurfaceShow);
    bindFunction(env, result, "onSurfaceHide", OnSurfaceHide);
    bindFunction(env, result, "onBackPress", OnBackPress);
    bindFunction(env, result, "dispatchTouchEvent", DispatchTouchEvent);
    bindFunction(env, result, "dispatchComposeMouseEvent", DispatchComposeMouseEvent);
    bindFunction(env, result, "dispatchComposePinchEvent", DispatchComposePinchEvent);
    bindFunction(env, result, "dispatchComposeKeyEvent", DispatchComposeKeyEvent);
    bindFunction(env, result, "onKeyboardShow", KeyboardWillShow);
    bindFunction(env, result, "onKeyboardHide", KeyboardWillHide);
    bindFunction(env, result, "requestSyncRefresh", RequestSyncRefresh);
    bindFunction(env, result, "cancelSyncRefresh", CancelSyncRefresh);
    bindFunction(env, result, "sendMessage", SendMessage);
    bindFunction(env, result, "onSurfaceCreated", OnSurfaceCreated);
    bindFunction(env, result, "onSurfaceDestroyed", OnSurfaceDestroyed);
    bindFunction(env, result, "onSurfaceChanged", OnSurfaceChanged);
    bindFunction(env, result, "setAccessibilityFrameNode", SetAccessibilityFrameNode);
    bindFunction(env, result, "getRenderBackend", GetRenderBackend);
    bindFunction(env, result, "getRenderBackendId", GetRenderBackendId);
    bindFunction(env, result, "getId", GetId);
    bindFunction(env, result, "findNodeIdAt", FindNodeIdAt);
    bindFunction(env, result, "onFrame", OnFrame);

    bindFunction(env, result, "onIdle", OnIdle);
    bindFunction(env, result, "initLtpoFrameRateController", InitLtpoFrameRateController);

    // FusionRenderer-specific NAPI bindings: only expose when renderBackendId == 1 (FusionRenderer).
    // When skia-only build, these functions are no-ops at Kotlin level (via FusionRendererNapiBridge/JsRenderNodeBridge),
    // but we also skip binding them to avoid unnecessary ArkTS → NAPI calls.
    // Nested-scroll NAPI bindings must be registered unconditionally: the Kotlin-side
    // implementations live in the common source set and gracefully no-op under skia.
    bindFunction(env, result, "setParentScrollBridge", SetParentScrollBridge);
    bindFunction(env, result, "consumeOuterScroll", ConsumeOuterScroll);
    if (GetCachedRenderBackendId(nullptr) == 1) {
        bindFunction(env, result, "draw", Draw);
        bindFunction(env, result, "getJsNode", GetJsNode);
        bindFunction(env, result, "JsNodeDraw", JsNodeDraw);
        bindFunction(env, result, "initRenderNodeContext", InitRenderNodeContext);
        bindFunction(env, result, "initFusionRendererNode", InitFusionRendererNode);
        bindFunction(env, result, "setFusionRendererRootNode", SetFusionRendererRootNode);
    }
    return result;
}
} // namespace androidx::compose::ui::arkui::utils::ArkUIViewControllerWrapper
