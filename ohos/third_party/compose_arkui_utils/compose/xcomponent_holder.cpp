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

#include "xcomponent_holder.h"
#include "arkui_view_controller.h"
#include "xcomponent_log.h"
#include "xcomponent_render.h"
#include "xcomponent_utils.h"

namespace androidx::compose::ui::arkui::utils {

XComponentHolder XComponentHolder::instance;

XComponentHolder *XComponentHolder::GetInstance() { return &instance; }

XComponentHolder::~XComponentHolder() {
    // XComponentRender should be destroyed by NativeXComponentFinalizer.
    renderMap.clear();

    // NativeArkViewController is managed by js engine, should be destroyed by gc.
    controllerMap.clear();
}

void XComponentHolder::InitXComponent(napi_env env, napi_value exports) {
    LOGI("XComponentHolder: InitXComponent: start");
    if (env == nullptr || exports == nullptr) {
        LOGE("XComponentHolder: InitXComponent: env or exports is null");
        return;
    }

    napi_value xComponent = nullptr;
    auto getPropertyStatus = napi_get_named_property(env, exports, OH_NATIVE_XCOMPONENT_OBJ, &xComponent);
    if (getPropertyStatus != napi_ok) {
        LOGD("XComponentHolder: InitXComponent: property %{public}s not found in exports, likely a regular module load", OH_NATIVE_XCOMPONENT_OBJ);
        return;
    }

    napi_valuetype type = napi_undefined;
    napi_typeof(env, xComponent, &type);
    if (type != napi_object) {
        LOGD("XComponentHolder: InitXComponent: %{public}s is not an object (type=%{public}d), likely a regular module load", OH_NATIVE_XCOMPONENT_OBJ, type);
        return;
    }

    OH_NativeXComponent *nativeXComponent = nullptr;
    auto unwrapStatus = napi_unwrap(env, xComponent, reinterpret_cast<void **>(&nativeXComponent));
    if (unwrapStatus != napi_ok) {
        LOGD("XComponentHolder: InitXComponent: napi_unwrap failed(%{public}d). This is expected if the module is loaded as a regular library instead of via XComponent.", unwrapStatus);
        return;
    }

    auto id = XComponentUtils::GetXComponentId(nativeXComponent);
    if (id.empty()) {
        LOGE("XComponentHolder: InitXComponent: invalid id");
        return;
    }

    LOGI("XComponentHolder: InitXComponent: success for id=%{public}s", id.c_str());
    XComponentRender *render = new XComponentRender(nativeXComponent);
    renderMap[id] = render;

    auto controller = GetArkUIViewController(id);
    if (controller != nullptr) {
        // 互相赋值
        render->controller = controller;
        ArkUIViewController_setXComponentRender(controller, render);
    }
}

void XComponentHolder::InitArkViewController(napi_env env, const std::string &id, ArkUIViewController *controller) {
    LOGI("XComponentHolder: InitArkViewController: id=%{public}s", id.c_str());
    controllerMap[id] = controller;

    auto render = GetXComponentRender(id);
    if (render != nullptr) {
        LOGI("XComponentHolder: InitArkViewController: render found for id=%{public}s, linking", id.c_str());
        // 互相赋值
        render->controller = controller;
        ArkUIViewController_setXComponentRender(controller, render);
    } else {
        LOGW("XComponentHolder: InitArkViewController: render NOT found for id=%{public}s yet", id.c_str());
    }
}

void XComponentHolder::RemoveXComponentRender(const std::string &id) { renderMap.erase(id); }
void XComponentHolder::RemoveArkUIViewController(const std::string &id) { controllerMap.erase(id); }

XComponentRender *XComponentHolder::GetXComponentRender(const std::string &id) const {
    auto pair = renderMap.find(id);
    if (pair == renderMap.end()) {
        return nullptr;
    }
    return pair->second;
}

XComponentRender *XComponentHolder::GetXComponentRender(OH_NativeXComponent *component) const {
    auto id = XComponentUtils::GetXComponentId(component);
    return XComponentHolder::GetInstance()->GetXComponentRender(id);
}

ArkUIViewController *XComponentHolder::GetArkUIViewController(const std::string &id) const {
    auto pair = controllerMap.find(id);
    if (pair == controllerMap.end()) {
        return nullptr;
    }
    return pair->second;
}
ArkUIViewController *XComponentHolder::GetArkUIViewController(OH_NativeXComponent *component) const {
    auto id = XComponentUtils::GetXComponentId(component);
    return XComponentHolder::GetInstance()->GetArkUIViewController(id);
}
} // namespace androidx::compose::ui::arkui::utils