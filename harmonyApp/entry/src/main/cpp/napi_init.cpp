/*
 * 宿主 NAPI 胶水：把 Kotlin 侧导出的 C 函数包装成 ArkTS 可 import 的模块方法。
 *
 * 边界只有一个句柄：ArkTS 调 MainArkUIViewController() 拿到 controller，
 * 交给 HAR 的 Compose 组件去挂 XComponent；UI 树整体活在 Kotlin 侧，
 * 这里不复制任何界面语义。
 */

#include <napi/native_api.h>
#include <hilog/log.h>

#define LOG_DOMAIN 0x0000
#define LOG_TAG "nexio-host"

// Kotlin 侧 @CName("MainArkUIViewController") 导出的符号（见 ohos/ohosApp/src/ohosMain）
extern "C" napi_value MainArkUIViewController(napi_env env);

// NAPI 回调签名是 (env, info)，Kotlin 导出只有一个 env 参数，这里做一层转接
static napi_value NapiMainArkUIViewController(napi_env env, napi_callback_info info) {
    (void) info;
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, LOG_TAG, "create ArkUIViewController");
    return MainArkUIViewController(env);
}

static napi_value Init(napi_env env, napi_value exports) {
    napi_property_descriptor desc[] = {
        {"MainArkUIViewController", nullptr, NapiMainArkUIViewController,
         nullptr, nullptr, nullptr, napi_default, nullptr},
    };
    napi_define_properties(env, exports, sizeof(desc) / sizeof(desc[0]), desc);
    return exports;
}

// nm_modname 必须与 ArkTS 侧的 libraryName / import 的模块名一致（都是 "entry"）
static napi_module nexioModule = {
    .nm_version = 1,
    .nm_flags = 0,
    .nm_filename = nullptr,
    .nm_register_func = Init,
    .nm_modname = "entry",
    .nm_priv = nullptr,
    .reserved = {0},
};

extern "C" __attribute__((constructor)) void RegisterEntryModule(void) {
    napi_module_register(&nexioModule);
}
