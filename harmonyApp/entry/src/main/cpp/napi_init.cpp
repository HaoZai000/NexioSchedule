/*
 * 宿主 NAPI 胶水：把 Kotlin 侧导出的 C 函数包成 ArkTS 可 import 的模块方法。
 *
 * `XComponentHolder::InitXComponent()` 必须在这里调 —— 不登记就没有 surface 回调，画面全白。
 */

#include <napi/native_api.h>
#include <hilog/log.h>

// Compose 的 XComponent 桥（实现在 libnexioohos.so，头文件来自 third_party）
#include "compose/xcomponent_holder.h"

#define LOG_DOMAIN 0x0000
#define LOG_TAG "nexio-host"

// Kotlin 侧 @CName 导出的符号（见 ohos/ohosApp/src/ohosSharedCore）
extern "C" napi_value MainArkUIViewController(napi_env env);

// 沙箱目录 + 屏幕像素：Kotlin 侧拿不到 UIAbilityContext，只能由 ArkTS 传。
extern "C" void NexioSetAppEnv(const char *filesDir, int32_t screenW, int32_t screenH, int32_t cornerRadiusPx);

static napi_value NapiSetAppEnv(napi_env env, napi_callback_info info) {
    size_t argc = 4;
    napi_value args[4] = {nullptr, nullptr, nullptr, nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    char dir[1024] = {0};
    size_t len = 0;
    napi_get_value_string_utf8(env, args[0], dir, sizeof(dir), &len);

    int32_t w = 0;
    int32_t h = 0;
    napi_get_value_int32(env, args[1], &w);
    napi_get_value_int32(env, args[2], &h);

    int32_t cornerRadiusPx = 0;
    napi_get_value_int32(env, args[3], &cornerRadiusPx);

    NexioSetAppEnv(dir, w, h, cornerRadiusPx);
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, LOG_TAG, "setAppEnv: dir=%{public}s %{public}dx%{public}d corner=%{public}d", dir, w, h, cornerRadiusPx);
    return nullptr;
}


// NAPI 回调签名是 (env, info)，Kotlin 导出只有一个 env 参数，这里做一层转接
static napi_value NapiMainArkUIViewController(napi_env env, napi_callback_info info) {
    (void) info;
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, LOG_TAG, "create ArkUIViewController");
    return MainArkUIViewController(env);
}

// ── 壁纸选图：ArkTS 注册回调，Kotlin 侧触发 ──────────────────────────────────
// 系统选图器只能由 ArkTS 起，所以 ArkTS 启动时把 picker 存成 napi_ref；
// Kotlin 点「选壁纸」时反着触发，ArkTS 选完走 `setWallpaperBytes` 回传字节。
static napi_env g_env = nullptr;
static napi_ref g_wallpaperPickerRef = nullptr;

static napi_value NapiRegisterWallpaperPicker(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    napi_valuetype type = napi_undefined;
    napi_typeof(env, args[0], &type);
    if (type != napi_function) return nullptr;
    g_env = env;
    napi_create_reference(env, args[0], 1, &g_wallpaperPickerRef);
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, LOG_TAG, "wallpaper picker registered");
    return nullptr;
}

// 反向没有 DT_NEEDED（libentry → libnexioohos 单向），Kotlin 解析不到 C++ 符号，
// 所以由 C++ 把函数指针交过去，纯地址调用。
extern "C" void NexioOnWallpaperPickHostReady(void (*fn)(void));

static void HostRequestWallpaperPick(void) {
    if (g_env == nullptr || g_wallpaperPickerRef == nullptr) return;
    napi_value callback = nullptr;
    if (napi_get_reference_value(g_env, g_wallpaperPickerRef, &callback) != napi_ok || callback == nullptr) return;
    napi_value undefined = nullptr;
    napi_get_undefined(g_env, &undefined);
    napi_value result = nullptr;
    napi_call_function(g_env, undefined, callback, 0, nullptr, &result);
}

// Kotlin 侧 `@CName("NexioSetWallpaperBytes")`：选图结果回传，data 为 null = 取消/失败。
extern "C" void NexioSetWallpaperBytes(const uint8_t *data, int32_t length);

static napi_value NapiSetWallpaperBytes(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    napi_typedarray_type taType = napi_uint8_array;
    void *buf = nullptr;
    size_t len = 0;
    napi_value bufObj = nullptr;
    size_t offset = 0;
    if (napi_get_typedarray_info(env, args[0], &taType, &len, &buf, &bufObj, &offset) != napi_ok ||
        buf == nullptr || len == 0) {
        NexioSetWallpaperBytes(nullptr, 0);
        return nullptr;
    }
    NexioSetWallpaperBytes(static_cast<const uint8_t *>(buf), static_cast<int32_t>(len));
    return nullptr;
}

static napi_value Init(napi_env env, napi_value exports) {
    napi_property_descriptor desc[] = {
        {"MainArkUIViewController", nullptr, NapiMainArkUIViewController,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"setAppEnv", nullptr, NapiSetAppEnv,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"registerWallpaperPicker", nullptr, NapiRegisterWallpaperPicker,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"setWallpaperBytes", nullptr, NapiSetWallpaperBytes,
         nullptr, nullptr, nullptr, napi_default, nullptr},
    };
    napi_define_properties(env, exports, sizeof(desc) / sizeof(desc[0]), desc);

    androidx::compose::ui::arkui::utils::XComponentHolder::GetInstance()->InitXComponent(env, exports);
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, LOG_TAG, "Init: XComponentHolder wired");
    NexioOnWallpaperPickHostReady(HostRequestWallpaperPick);
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
