/*
 * Tencent is pleased to support the open source community by making ovCompose available.
 * Copyright (C) 2025 THL A29 Limited, a Tencent company. All rights reserved.
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

#include "napi/native_api.h"
#include "skikobridge/ComposeDispatcher.h"
#include <hilog/log.h>
#include <sys/stat.h>
#include <native_vsync/native_vsync.h>
#include <uv.h>
#include <queue>
#include <mutex>
#include <thread>
#include <functional>
#include <condition_variable>
#include <cstring>

extern "C" bool enableRuntimeLogs() {
    return false;
}

namespace skikobridge {

static inline napi_value getGlobalThis(napi_env jsEnv) {
    napi_value globalThis = nullptr;
    napi_get_global(jsEnv, &globalThis);
    return globalThis;
}

static inline void Log(int level, const char *log, ...) {
    va_list args;
    va_start(args, log);
    int len = vsnprintf(nullptr, 0, log, args);
    va_end(args);

    auto buffer = new char[len + 1];
    va_start(args, log);
    vsnprintf(buffer, len + 1, log, args);
    va_end(args);

    OH_LOG_Print(LOG_APP, (LogLevel)level, 0, "SkiohBridge", "%{public}s", buffer);
    delete[] buffer;
}

struct DispatchItem {
    float after = 0;
    void *outdata = nullptr;
    napi_env jsEnv = nullptr;
    skikobridge_async_callback callback = nullptr;
    napi_async_work asyncWork = nullptr;
};

static inline napi_value createWorkNameValue(napi_env jsEnv, const char *name = nullptr) {
    napi_value workName = nullptr;
    napi_create_string_utf8(jsEnv, name, NAPI_AUTO_LENGTH, &workName);
    return workName;
}

static void runInMainJSThread(napi_env env, napi_value js_cb, void *context, void *data);
static napi_value setTimeoutCallbackFn(napi_env env, napi_callback_info info);

class ComposeDispatcher {
 public:
    static ComposeDispatcher &sharedInstance() {
        static ComposeDispatcher *_instance;
        static std::once_flag onceFlag;
        std::call_once(onceFlag, []() {
            _instance = new ComposeDispatcher();
        });
        return *_instance;
    }

    void bindJSEnv(napi_env jsEnv) {
        _mainThreadId = std::this_thread::get_id();
        napi_create_string_utf8(jsEnv, "ohos", NAPI_AUTO_LENGTH, &_work_name);
        sharedInstance().processJSEnv(jsEnv);
    }

    static void WorkCallback(uv_work_t *workReq) {
    }

    static void AfterWorkCallback(uv_work_t *workReq, int status) {
        if (workReq != nullptr) {
            DispatchItem *dispatchItem = static_cast<DispatchItem *>(workReq->data);
            if (dispatchItem) {
                runInMainJSThread(dispatchItem->jsEnv, nullptr, nullptr, (void *)dispatchItem);
            }
            delete workReq;
        }
    }

    void dispatchAsyncTask(float time, void *outData, skikobridge_async_callback callBack) const {
        DispatchItem *item = new DispatchItem();
        item->outdata = outData;
        item->callback = callBack;
        item->after = time;
        item->jsEnv = _jsEnv;
        uv_loop_s *loop = nullptr;
        napi_get_uv_event_loop(_jsEnv, &loop);
        uv_work_t *workReq = new uv_work_t;
        workReq->data = (void *)item;
        uv_queue_work(loop, workReq, WorkCallback, AfterWorkCallback);
    }

    void dispatchAsyncWork(void *outData, skikobridge_async_callback callBack) const {
        DispatchItem *item = new DispatchItem();
        item->outdata = outData;
        item->callback = callBack;
        item->jsEnv = _jsEnv;

        napi_async_work work = nullptr;
        item->asyncWork = work;

        napi_value work_name = nullptr;
        napi_create_string_utf8(_jsEnv, "Work", NAPI_AUTO_LENGTH, &work_name);

        napi_create_async_work(_jsEnv, nullptr, work_name, [](napi_env, void *data) {}, [](napi_env env, napi_status status, void *data) {
            DispatchItem *innerItem = (DispatchItem*)data;
            innerItem->callback(innerItem->outdata);
            napi_delete_async_work(innerItem->jsEnv, innerItem->asyncWork);
            delete innerItem;
        }, item, &work);
        napi_queue_async_work(_jsEnv, work);
    }

    void setPlatformProvider(napi_env env, napi_value provider) {
        if (_platformProviderRef != nullptr) {
            napi_delete_reference(_jsEnv, _platformProviderRef);
            _platformProviderRef = nullptr;
        }
        if (env != nullptr && provider != nullptr) {
            napi_create_reference(env, provider, 1, &_platformProviderRef);
        }
    }

    napi_value getPlatformProvider() const {
        if (_jsEnv == nullptr || _platformProviderRef == nullptr) {
            return nullptr;
        }
        napi_value provider = nullptr;
        if (napi_get_reference_value(_jsEnv, _platformProviderRef, &provider) != napi_ok) {
            return nullptr;
        }
        return provider;
    }

    void processJSEnv(napi_env jsEnv) {
        if (jsEnv == nullptr) {
            return;
        }
        if (_platformProviderRef != nullptr) {
            napi_delete_reference(_jsEnv, _platformProviderRef);
            _platformProviderRef = nullptr;
        }
        _jsEnv = jsEnv;
        napi_get_global(jsEnv, &_globalThis);
        napi_value workName = createWorkNameValue(jsEnv, "skikobridge_workItem");
        napi_status status = napi_create_threadsafe_function(jsEnv, nullptr, NULL, workName, 0, 1, NULL, NULL, NULL,
                                                             runInMainJSThread, &_tsfn);
        if (status != napi_ok) {
            Log(LOG_ERROR, "skikobridge::ComposeDispatcher napi_create_threadsafe_function fail");
        }
    }

    napi_env getCurrentEnv() const {
        return _jsEnv;
    }

    napi_value getGlobalThis() const {
        return _globalThis;
    }

    ComposeDispatcher(const ComposeDispatcher &) = delete;
    ComposeDispatcher &operator=(const ComposeDispatcher &) = delete;

private:
    napi_value _globalThis = nullptr;
    napi_ref _platformProviderRef = nullptr;
    OH_NativeVSync *_nativeVSync = nullptr;
    std::thread::id _mainThreadId;
    napi_threadsafe_function _tsfn = nullptr;
    napi_env _jsEnv = nullptr;
    napi_value _work_name = nullptr;
    ComposeDispatcher() {
    }
    ~ComposeDispatcher() {
    }
};

static void runInMainJSThread(napi_env env, napi_value js_cb, void *context, void *data) {
    DispatchItem *dispatchItem = (DispatchItem *)data;
    if (!dispatchItem || !dispatchItem->callback) {
        return;
    }

    if (dispatchItem->after <= 0) {
        dispatchItem->callback(dispatchItem->outdata);
        delete dispatchItem;
        return;
    }

    napi_value globalThis = getGlobalThis(env);
    napi_value setTimeoutFn = nullptr;
    napi_get_named_property(env, globalThis, "setTimeout", &setTimeoutFn);

    napi_valuetype type = napi_undefined;
    napi_typeof(env, setTimeoutFn, &type);
    if (type == napi_function) {
        napi_value callbackFn = nullptr;
        napi_create_function(env, "skiko_bridge_timerout_callback", 0, setTimeoutCallbackFn, data, &callbackFn);

        napi_value afterValue = nullptr;
        napi_create_double(env, dispatchItem->after, &afterValue);
        napi_value args[2] = {callbackFn, afterValue};
        napi_call_function(env, nullptr, setTimeoutFn, 2, args, nullptr);
    }
}

static napi_value setTimeoutCallbackFn(napi_env env, napi_callback_info info) {
    DispatchItem *dispatchItem = nullptr;
    napi_get_cb_info(env, info, nullptr, nullptr, nullptr, (void **)&dispatchItem);
    if (dispatchItem && dispatchItem->callback) {
        dispatchItem->callback(dispatchItem->outdata);
        delete dispatchItem;
    }
    return nullptr;
}

}; // namespace skikobridge

COMPOSE_EXPORT float skikobridge_compose_get_screen_density() {
    return 3.25;
}

COMPOSE_EXPORT void skikobridge_compose_coroutines_dispatcher_bind_jsenv(void *jsEnv) {
    napi_env env = reinterpret_cast<napi_env>(jsEnv);
    skikobridge::ComposeDispatcher::sharedInstance().bindJSEnv(env);
}

COMPOSE_EXPORT void skikobridge_compose_coroutines_dispatcher_async_task(float after, void *data,
                                                                         skikobridge_async_callback callBack) {
    if (callBack) {
        skikobridge::ComposeDispatcher::sharedInstance().dispatchAsyncTask(after, data, callBack);
    }
}

COMPOSE_EXPORT void skikobridge_compose_coroutines_dispatcher_async_work(void *data,
                                                                         skikobridge_async_callback callBack) {
    if (callBack) {
        skikobridge::ComposeDispatcher::sharedInstance().dispatchAsyncWork(data, callBack);
    }
}

extern "C" void kotlin_native_kraus_hook() {
}

extern "C" void kotlin_native_kraus_Throw() {
}

COMPOSE_EXPORT void skikobridge_compose_logI(const char *log) {
    skikobridge::Log(LOG_INFO, log);
}

COMPOSE_EXPORT void skikobridge_compose_logE(const char *log) {
    skikobridge::Log(LOG_ERROR, log);
}

COMPOSE_EXPORT void skikobridge_compose_logW(const char *log) {
    skikobridge::Log(LOG_WARN, log);
}

COMPOSE_EXPORT void skikobridge_compose_logD(const char *log) {
    skikobridge::Log(LOG_DEBUG, log);
}

COMPOSE_EXPORT void skikobridge_napi_open_uri(const char *uri) {
    if (uri == nullptr) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_open_uri: uri is null");
        return;
    }

    napi_env env = skikobridge::ComposeDispatcher::sharedInstance().getCurrentEnv();
    if (env == nullptr) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_open_uri: napi_env is null");
        return;
    }

    napi_value provider = skikobridge::ComposeDispatcher::sharedInstance().getPlatformProvider();
    if (provider == nullptr) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_open_uri: platform provider is null");
        return;
    }

    napi_value openUriFn;
    napi_status status = napi_get_named_property(env, provider, "__global_openUri", &openUriFn);
    if (status != napi_ok) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_open_uri: failed to get __global_openUri");
        return;
    }

    napi_valuetype type;
    napi_typeof(env, openUriFn, &type);
    if (type != napi_function) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_open_uri: __global_openUri is not a function");
        return;
    }

    napi_value uriValue;
    status = napi_create_string_utf8(env, uri, NAPI_AUTO_LENGTH, &uriValue);
    if (status != napi_ok) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_open_uri: failed to create string");
        return;
    }

    napi_value args[1] = {uriValue};
    napi_value result;
    status = napi_call_function(env, provider, openUriFn, 1, args, &result);
    if (status != napi_ok) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_open_uri: failed to call function");
    }
}

COMPOSE_EXPORT int skikobridge_napi_get_system_theme(void) {
    napi_env env = skikobridge::ComposeDispatcher::sharedInstance().getCurrentEnv();
    if (env == nullptr) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_get_system_theme: napi_env is null");
        return 0;
    }

    napi_value provider = skikobridge::ComposeDispatcher::sharedInstance().getPlatformProvider();
    if (provider == nullptr) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_get_system_theme: platform provider is null");
        return 0;
    }

    napi_value getThemeFn;
    napi_status status = napi_get_named_property(env, provider, "__global_getSystemTheme", &getThemeFn);
    if (status != napi_ok) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_get_system_theme: failed to get __global_getSystemTheme");
        return 0;
    }

    napi_valuetype type;
    napi_typeof(env, getThemeFn, &type);
    if (type != napi_function) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_get_system_theme: __global_getSystemTheme is not a function");
        return 0;
    }

    napi_value result;
    status = napi_call_function(env, provider, getThemeFn, 0, nullptr, &result);
    if (status != napi_ok) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_get_system_theme: failed to call function");
        return 0;
    }

    int32_t value = 0;
    napi_get_value_int32(env, result, &value);
    return static_cast<int>(value);
}

static bool callJsClipboardFunc(napi_env env, const char *funcName, napi_value *result,
                                const char *argStr = nullptr) {
    napi_value provider = skikobridge::ComposeDispatcher::sharedInstance().getPlatformProvider();
    if (provider == nullptr) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_clipboard: platform provider is null");
        return false;
    }

    napi_value fn;
    napi_status status = napi_get_named_property(env, provider, funcName, &fn);
    if (status != napi_ok) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_clipboard: failed to get %s", funcName);
        return false;
    }

    napi_valuetype type;
    napi_typeof(env, fn, &type);
    if (type != napi_function) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_clipboard: %s is not a function", funcName);
        return false;
    }

    if (argStr != nullptr) {
        napi_value arg;
        status = napi_create_string_utf8(env, argStr, NAPI_AUTO_LENGTH, &arg);
        if (status != napi_ok) return false;
        napi_value args[1] = {arg};
        status = napi_call_function(env, provider, fn, 1, args, result);
    } else {
        status = napi_call_function(env, provider, fn, 0, nullptr, result);
    }
    return (status == napi_ok);
}

#define CLIPBOARD_BUFFER_SIZE 65536

COMPOSE_EXPORT void skikobridge_napi_clipboard_set_text(const char *text) {
    if (text == nullptr) return;

    napi_env env = skikobridge::ComposeDispatcher::sharedInstance().getCurrentEnv();
    if (env == nullptr) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_clipboard_set_text: napi_env is null");
        return;
    }

    napi_value result;
    if (!callJsClipboardFunc(env, "__global_clipboard_setText", &result, text)) {
        return;
    }
}

COMPOSE_EXPORT const char *skikobridge_napi_clipboard_get_text(void) {
    napi_env env = skikobridge::ComposeDispatcher::sharedInstance().getCurrentEnv();
    if (env == nullptr) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_clipboard_get_text: napi_env is null");
        return nullptr;
    }

    napi_value result;
    if (!callJsClipboardFunc(env, "__global_clipboard_getText", &result)) {
        return nullptr;
    }

    napi_valuetype type;
    napi_typeof(env, result, &type);
    if (type == napi_null || type == napi_undefined) {
        return nullptr;
    }

    size_t len = 0;
    napi_get_value_string_utf8(env, result, nullptr, 0, &len);
    if (len >= CLIPBOARD_BUFFER_SIZE) {
        len = CLIPBOARD_BUFFER_SIZE - 1;
    }

    static thread_local char buffer[CLIPBOARD_BUFFER_SIZE];
    size_t copied = 0;
    napi_get_value_string_utf8(env, result, buffer, CLIPBOARD_BUFFER_SIZE, &copied);
    buffer[copied] = '\0';
    return buffer;
}

COMPOSE_EXPORT bool skikobridge_napi_clipboard_has_text(void) {
    napi_env env = skikobridge::ComposeDispatcher::sharedInstance().getCurrentEnv();
    if (env == nullptr) {
        skikobridge::Log(LOG_ERROR, "skikobridge_napi_clipboard_has_text: napi_env is null");
        return false;
    }

    napi_value result;
    if (!callJsClipboardFunc(env, "__global_clipboard_hasText", &result)) {
        return false;
    }

    bool value = false;
    napi_get_value_bool(env, result, &value);
    return value;
}

COMPOSE_EXPORT napi_env skikobridge_get_current_napi_env() {
    return skikobridge::ComposeDispatcher::sharedInstance().getCurrentEnv();
}

COMPOSE_EXPORT void skikobridge_set_platform_provider(napi_env env, napi_value provider) {
    skikobridge::ComposeDispatcher::sharedInstance().setPlatformProvider(env, provider);
}

struct VsyncCallBack {
    long long _timestamp = 0;
    skikobridge_async_vsync_callback _callBack = nullptr;
    OH_NativeVSync *_nativeVSync = nullptr;
    void *_dataContext = nullptr;

    ~VsyncCallBack() {
        if (_nativeVSync) {
            OH_NativeVSync_Destroy(_nativeVSync);
            _nativeVSync = nullptr;
        }
    }
};

static void skikobridge_compose_request_vsync_js_call_back(void *data) {
    VsyncCallBack *vsyncData = (VsyncCallBack *)data;
    if (vsyncData && vsyncData->_callBack) {
        vsyncData->_callBack(vsyncData->_timestamp, vsyncData->_dataContext);
        delete vsyncData;
    }
}

static void skikobridge_compose_request_vsync_frame_call_back(long long timestamp, void *data) {
    if (data != nullptr) {
        VsyncCallBack *callback = (VsyncCallBack *)data;
        callback->_timestamp = timestamp;
        auto &instance = skikobridge::ComposeDispatcher::sharedInstance();
        instance.dispatchAsyncTask(0, data, skikobridge_compose_request_vsync_js_call_back);
    }
}

static void skikobridge_compose_request_vsync_frame_call_back_2(void *data) {
    if (data != nullptr) {
        VsyncCallBack *callback = (VsyncCallBack *)data;
        callback->_timestamp = 0;
        if (callback->_callBack) {
            callback->_callBack(0, callback->_dataContext);
        }
    }
}

static napi_value native_js_request_vsync_callback(napi_env env, napi_callback_info info) {
    VsyncCallBack *dispatchItem = nullptr;
    napi_get_cb_info(env, info, nullptr, nullptr, nullptr, (void **)&dispatchItem);
    if (dispatchItem && dispatchItem->_callBack) {
        dispatchItem->_callBack(0, dispatchItem->_dataContext);
    }
    return nullptr;
}

COMPOSE_EXPORT void skikobridge_compose_request_vsync_frame(void *dataContext,
                                                           skikobridge_async_vsync_callback callBack) {
    static char name[] = "skikobridge_compose_request_vsync_frame";
    VsyncCallBack *data = new VsyncCallBack();
    data->_callBack = callBack;
    data->_dataContext = dataContext;
    OH_NativeVSync *nativeVSync = OH_NativeVSync_Create(name, strlen(name));
    data->_nativeVSync = nativeVSync;
    OH_NativeVSync_RequestFrame(nativeVSync, skikobridge_compose_request_vsync_frame_call_back, (void *)data);
}

std::thread workerThread;
std::mutex workerMutex;
std::condition_variable workerCondition;
std::queue<std::function<void()>> tasks;
bool workerThreadRunning = true;

static void workerThreadFunc() {
    while (true) {
        std::function<void()> task;
        {
            std::unique_lock<std::mutex> lock(workerMutex);
            workerCondition.wait(lock, [] {
                return !tasks.empty() || !workerThreadRunning;
            });

            if (!workerThreadRunning && tasks.empty()) {
                break;
            }

            task = std::move(tasks.front());
            tasks.pop();
        }

        task();
    }
}

static bool hasPrepare = false;
COMPOSE_EXPORT void skikobridge_gcd_async(std::function<void()> task) {
    if (!hasPrepare) {
        workerThread = std::thread(workerThreadFunc);
        hasPrepare = true;
    }
    std::unique_lock<std::mutex> lock(workerMutex);
    tasks.push(std::move(task));
    workerCondition.notify_one();
}
