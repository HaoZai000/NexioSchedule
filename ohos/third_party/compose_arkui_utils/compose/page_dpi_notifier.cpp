/*
 * Copyright 2025 Huawei Device Co., Ltd. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

#include "page_dpi_notifier.h"
#include "xcomponent_log.h"
#include <atomic>
#include <dlfcn.h>

namespace androidx::compose::ui::arkui::utils {

using NotifyPageChangedFn = int32_t (*)(const char *, int32_t, int32_t);

static constexpr int32_t kAbilityRuntimeNoError = 0;
/** Local error codes mirrored by Kotlin PageDpiNotifier; system codes are >= 0. */
static constexpr int kResultUnavailable = -1;
static constexpr int kResultInvalidPage = -2;
static constexpr int kResultWindowNotReady = -3;

static constexpr const char *kNotifyPageChangedSymbol =
    "OH_AbilityRuntime_ApplicationContextNotifyPageChanged";

static void *ResolveAbilityRuntimeHandle() {
    static void *handle = []() -> void * {
        void *resolved = dlopen("libability_runtime.so", RTLD_LAZY);
        if (resolved == nullptr) {
            LOGW("ResolveAbilityRuntimeHandle dlopen failed");
        }
        return resolved;
    }();
    return handle;
}

template <typename T>
static T ResolveAbilityRuntimeSymbol(const char *symbolName) {
    void *handle = ResolveAbilityRuntimeHandle();
    if (handle == nullptr) {
        return nullptr;
    }
    auto symbol = dlsym(handle, symbolName);
    if (symbol == nullptr) {
        LOGD("ResolveAbilityRuntimeSymbol dlsym failed symbol=%{public}s", symbolName);
        return nullptr;
    }
    return reinterpret_cast<T>(symbol);
}

static NotifyPageChangedFn ResolveNotifyPageChanged() {
    static NotifyPageChangedFn fn = []() -> NotifyPageChangedFn {
        auto resolved = ResolveAbilityRuntimeSymbol<NotifyPageChangedFn>(kNotifyPageChangedSymbol);
        if (resolved == nullptr) {
            LOGW("PageDpiNotifier: CAPI symbol not found (requires API 23+)");
        }
        return resolved;
    }();
    return fn;
}

static std::atomic<int32_t> g_cachedWindowId{-1};

void PageDpiNotifier_setWindowId(int32_t windowId) {
    g_cachedWindowId.store(windowId, std::memory_order_relaxed);
    LOGD("PageDpiNotifier: setWindowId=%{public}d", windowId);
}

void PageDpiNotifier_clearWindowId() {
    g_cachedWindowId.store(-1, std::memory_order_relaxed);
}

bool PageDpiNotifier_isAvailable() {
    return ResolveNotifyPageChanged() != nullptr;
}

int PageDpiNotifier_notifyPageChanged(const char *pageName, int32_t pageNameLength) {
    auto fn = ResolveNotifyPageChanged();
    if (fn == nullptr) {
        return kResultUnavailable;
    }

    if (pageName == nullptr || pageNameLength <= 0) {
        LOGW("PageDpiNotifier: skip empty pageName");
        return kResultInvalidPage;
    }

    int32_t windowId = g_cachedWindowId.load(std::memory_order_relaxed);
    if (windowId < 0) {
        LOGW("PageDpiNotifier: windowId not ready, pageName=%{public}s", pageName);
        return kResultWindowNotReady;
    }

    int32_t result = fn(pageName, pageNameLength, windowId);
    if (result == kAbilityRuntimeNoError) {
        LOGD("PageDpiNotifier: ok pageName=%{public}s windowId=%{public}d", pageName, windowId);
    } else {
        LOGW(
            "PageDpiNotifier: failed code=%{public}d pageName=%{public}s windowId=%{public}d",
            result,
            pageName,
            windowId);
    }
    return result;
}

} // namespace androidx::compose::ui::arkui::utils
