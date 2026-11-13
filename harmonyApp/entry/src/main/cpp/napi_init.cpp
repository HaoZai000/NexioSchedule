/*
 * 宿主 NAPI 胶水：把 Kotlin 侧导出的 C 函数包成 ArkTS 可 import 的模块方法。
 *
 * `XComponentHolder::InitXComponent()` 必须在这里调 —— 不登记就没有 surface 回调，画面全白。
 */

#include <napi/native_api.h>
#include <hilog/log.h>

// Compose 的 XComponent 桥（实现在 libnexioohos.so，头文件来自 third_party）
#include "compose/xcomponent_holder.h"
#include "ai_crypto.h"

#define LOG_DOMAIN 0x0000
#define LOG_TAG "nexio-host"

// Kotlin 侧 @CName 导出的符号（见 ohos/ohosApp/src/ohosSharedCore）
extern "C" napi_value MainArkUIViewController(napi_env env);

// 沙箱目录 + 屏幕像素：Kotlin 侧拿不到 UIAbilityContext，只能由 ArkTS 传。
extern "C" void NexioSetAppEnv(const char *filesDir, int32_t screenW, int32_t screenH, int32_t cornerRadiusPx);

// 应用版本 / 名称：Kotlin 侧拿不到 bundleManager，由 ArkTS 在启动时写入。
extern "C" void NexioSetAppInfo(const char *version, const char *name);

static napi_value NapiSetAppInfo(napi_env env, napi_callback_info info) {
    size_t argc = 2;
    napi_value args[2] = {nullptr, nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    char version[128] = {0};
    char name[256] = {0};
    size_t len = 0;
    napi_get_value_string_utf8(env, args[0], version, sizeof(version), &len);
    napi_get_value_string_utf8(env, args[1], name, sizeof(name), &len);
    NexioSetAppInfo(version, name);
    return nullptr;
}

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

// ── HTTP：ArkTS 侧用 @ohos.net.http 发请求，结果异步回传 ─────────────────────
// NDK sysroot 里没有 curl/openssl，网络只能走 ArkTS。两个方向：
//   Kotlin → ArkTS：调 registerHttpSender 存下的 JS 函数（**必须在 JS 线程**，否则
//                    ArkTS VM 报 ecma_vm cannot run in multi-thread 直接 abort）；
//   ArkTS → Kotlin：走 setHttpResult，转给 Kotlin 的 @CName("NexioOnHttpResult")。
static napi_ref g_httpSenderRef = nullptr;

static napi_value NapiRegisterHttpSender(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    napi_valuetype type = napi_undefined;
    napi_typeof(env, args[0], &type);
    if (type != napi_function) return nullptr;
    g_env = env;
    napi_create_reference(env, args[0], 1, &g_httpSenderRef);
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, LOG_TAG, "http sender registered");
    return nullptr;
}

/** Kotlin 侧的发送入口（函数指针交给 Kotlin，见 NexioOnHttpHostReady）。 */
static void HostHttpSend(int32_t requestId, const char *payloadJson, double maxBytes) {
    if (g_env == nullptr || g_httpSenderRef == nullptr) return;
    napi_value callback = nullptr;
    if (napi_get_reference_value(g_env, g_httpSenderRef, &callback) != napi_ok || callback == nullptr) return;
    napi_value undefined = nullptr;
    napi_get_undefined(g_env, &undefined);
    napi_value argv[3] = {nullptr, nullptr, nullptr};
    napi_create_int32(g_env, requestId, &argv[0]);
    napi_create_string_utf8(g_env, payloadJson, NAPI_AUTO_LENGTH, &argv[1]);
    napi_create_double(g_env, maxBytes, &argv[2]);
    napi_value result = nullptr;
    napi_call_function(g_env, undefined, callback, 3, argv, &result);
}

extern "C" void NexioOnHttpHostReady(void (*fn)(int32_t, const char *, double));
extern "C" void NexioOnHttpResult(int32_t requestId, int32_t code, const uint8_t *data, int32_t length, bool truncated);

/** ArkTS 侧回传：setHttpResult(requestId, code, Uint8Array, truncated)。 */
static napi_value NapiSetHttpResult(napi_env env, napi_callback_info info) {
    size_t argc = 4;
    napi_value args[4] = {nullptr, nullptr, nullptr, nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    int32_t requestId = 0;
    napi_get_value_int32(env, args[0], &requestId);
    int32_t code = 0;
    napi_get_value_int32(env, args[1], &code);
    bool truncated = false;
    napi_get_value_bool(env, args[3], &truncated);

    napi_typedarray_type taType = napi_uint8_array;
    void *buf = nullptr;
    size_t len = 0;
    napi_value bufObj = nullptr;
    size_t offset = 0;
    if (napi_get_typedarray_info(env, args[2], &taType, &len, &buf, &bufObj, &offset) != napi_ok || buf == nullptr) {
        NexioOnHttpResult(requestId, code, nullptr, 0, truncated);
        return nullptr;
    }
    NexioOnHttpResult(requestId, code, static_cast<const uint8_t *>(buf), static_cast<int32_t>(len), truncated);
    return nullptr;
}

// ── Toast：Kotlin 的 showToast 走这里 → ArkTS promptAction ────────────────────
// 不接的话 `toastHandler` 恒为 null，所有错误提示（口令失效、导入失败…）都静默丢掉。
static napi_ref g_toastRef = nullptr;

static napi_value NapiRegisterToast(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    napi_valuetype type = napi_undefined;
    napi_typeof(env, args[0], &type);
    if (type != napi_function) return nullptr;
    g_env = env;
    napi_create_reference(env, args[0], 1, &g_toastRef);
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, LOG_TAG, "toast registered");
    return nullptr;
}

/** Kotlin 侧的弹提示入口（函数指针交给 Kotlin）。必须在 JS 线程调用。 */
static void HostShowToast(const char *message, bool longDuration) {
    if (g_env == nullptr || g_toastRef == nullptr) return;
    napi_value callback = nullptr;
    if (napi_get_reference_value(g_env, g_toastRef, &callback) != napi_ok || callback == nullptr) return;
    napi_value undefined = nullptr;
    napi_get_undefined(g_env, &undefined);
    napi_value argv[2] = {nullptr, nullptr};
    napi_create_string_utf8(g_env, message, NAPI_AUTO_LENGTH, &argv[0]);
    napi_get_boolean(g_env, longDuration, &argv[1]);
    napi_value result = nullptr;
    napi_call_function(g_env, undefined, callback, 2, argv, &result);
}

extern "C" void NexioOnToastHostReady(void (*fn)(const char *, bool));

// ── 子页栈状态：有没有子页 → ArkTS 用来决定系统返回键是否消费 ─────────────────
// 页面本身全在 Compose 里，这条通道只发一个布尔值。
static napi_ref g_overlayRef = nullptr;

static napi_value NapiRegisterOverlay(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    napi_valuetype type = napi_undefined;
    napi_typeof(env, args[0], &type);
    if (type != napi_function) return nullptr;
    g_env = env;
    napi_create_reference(env, args[0], 1, &g_overlayRef);
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, LOG_TAG, "overlay registered");
    return nullptr;
}

/** Kotlin 侧的发布入口（函数指针交给 Kotlin）。必须在 JS 线程调用。 */
static void HostPublishOverlay(bool active) {
    if (g_env == nullptr || g_overlayRef == nullptr) return;
    napi_value callback = nullptr;
    if (napi_get_reference_value(g_env, g_overlayRef, &callback) != napi_ok || callback == nullptr) return;
    napi_value undefined = nullptr;
    napi_get_undefined(g_env, &undefined);
    napi_value argv[1] = {nullptr};
    napi_get_boolean(g_env, active, &argv[0]);
    napi_value result = nullptr;
    napi_call_function(g_env, undefined, callback, 1, argv, &result);
}

extern "C" void NexioOnOverlayHostReady(void (*fn)(bool));

// 系统返回键 → Kotlin 出栈（Compose 的预测返回在鸿蒙 target 是空实现，接不到系统返回）
extern "C" void NexioOnOverlayPopBack(void);

static napi_value NapiOverlayPopBack(napi_env env, napi_callback_info info) {
    (void) env;
    (void) info;
    NexioOnOverlayPopBack();
    return nullptr;
}

// ── 文件选择 / 另存为：系统文件选择器只能由 ArkTS 起 ─────────────────────────
// Kotlin → ArkTS：registerFilePicker 存下 JS 函数，Kotlin 点导入/导出时反着调；
// ArkTS → Kotlin：setFileResult 回传内容或取消。约定 op = "openText" / "saveText"。
static napi_ref g_filePickerRef = nullptr;

// ── 系统分享 / 剪贴板：ArkTS 的 ShareKit 与 pasteboard 只能由宿主起 ─────────
// op = "share"（payload = {path, fileName, text}）/ "clipboard"（payload = 文本）。
static napi_ref g_shareHostRef = nullptr;

static napi_value NapiRegisterShareHost(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    napi_valuetype type = napi_undefined;
    napi_typeof(env, args[0], &type);
    if (type != napi_function) return nullptr;
    g_env = env;
    napi_create_reference(env, args[0], 1, &g_shareHostRef);
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, LOG_TAG, "share host registered");
    return nullptr;
}

static void HostShareRequest(const char *op, const char *payload) {
    if (g_env == nullptr || g_shareHostRef == nullptr) return;
    napi_value callback = nullptr;
    if (napi_get_reference_value(g_env, g_shareHostRef, &callback) != napi_ok || callback == nullptr) return;
    napi_value undefined = nullptr;
    napi_get_undefined(g_env, &undefined);
    napi_value argv[2] = {nullptr, nullptr};
    napi_create_string_utf8(g_env, op, NAPI_AUTO_LENGTH, &argv[0]);
    napi_create_string_utf8(g_env, payload, NAPI_AUTO_LENGTH, &argv[1]);
    napi_value result = nullptr;
    napi_call_function(g_env, undefined, callback, 2, argv, &result);
}

extern "C" void NexioOnShareHostReady(void (*fn)(const char *, const char *));

static napi_value NapiRegisterFilePicker(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    napi_valuetype type = napi_undefined;
    napi_typeof(env, args[0], &type);
    if (type != napi_function) return nullptr;
    g_env = env;
    napi_create_reference(env, args[0], 1, &g_filePickerRef);
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, LOG_TAG, "file picker registered");
    return nullptr;
}

/** Kotlin 侧的发起入口（函数指针交给 Kotlin）。必须在 JS 线程调用。 */
static void HostRequestFile(int32_t requestId, const char *op, const char *payload) {
    if (g_env == nullptr || g_filePickerRef == nullptr) return;
    napi_value callback = nullptr;
    if (napi_get_reference_value(g_env, g_filePickerRef, &callback) != napi_ok || callback == nullptr) return;
    napi_value undefined = nullptr;
    napi_get_undefined(g_env, &undefined);
    napi_value argv[3] = {nullptr, nullptr, nullptr};
    napi_create_int32(g_env, requestId, &argv[0]);
    napi_create_string_utf8(g_env, op, NAPI_AUTO_LENGTH, &argv[1]);
    napi_create_string_utf8(g_env, payload, NAPI_AUTO_LENGTH, &argv[2]);
    napi_value result = nullptr;
    napi_call_function(g_env, undefined, callback, 3, argv, &result);
}

extern "C" void NexioOnFileHostReady(void (*fn)(int32_t, const char *, const char *));
extern "C" void NexioOnFileResult(int32_t requestId, bool ok, const char *payload);

/** ArkTS 侧回传：setFileResult(requestId, ok, payload)。 */
static napi_value NapiSetFileResult(napi_env env, napi_callback_info info) {
    size_t argc = 3;
    napi_value args[3] = {nullptr, nullptr, nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    int32_t requestId = 0;
    napi_get_value_int32(env, args[0], &requestId);
    bool ok = false;
    napi_get_value_bool(env, args[1], &ok);

    char *payload = nullptr;
    size_t len = 0;
    if (napi_get_value_string_utf8(env, args[2], nullptr, 0, &len) == napi_ok) {
        payload = new char[len + 1];
        napi_get_value_string_utf8(env, args[2], payload, len + 1, &len);
    }
    NexioOnFileResult(requestId, ok, payload != nullptr ? payload : "");
    delete[] payload;
    return nullptr;
}

// ── 同步到日历：系统日历只能由 ArkTS 的 calendarManager 写 ───────────────────
// Kotlin 把逐节课事件发过来，ArkTS 申请权限、写日历后回传 { status, count }。
static napi_ref g_calendarRef = nullptr;

static napi_value NapiRegisterCalendarSync(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    napi_valuetype type = napi_undefined;
    napi_typeof(env, args[0], &type);
    if (type != napi_function) return nullptr;
    g_env = env;
    napi_create_reference(env, args[0], 1, &g_calendarRef);
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, LOG_TAG, "calendar sync registered");
    return nullptr;
}

static void HostCalendarRequest(int32_t requestId, const char *payload) {
    if (g_env == nullptr || g_calendarRef == nullptr) return;
    napi_value callback = nullptr;
    if (napi_get_reference_value(g_env, g_calendarRef, &callback) != napi_ok || callback == nullptr) return;
    napi_value undefined = nullptr;
    napi_get_undefined(g_env, &undefined);
    napi_value argv[2] = {nullptr, nullptr};
    napi_create_int32(g_env, requestId, &argv[0]);
    napi_create_string_utf8(g_env, payload, NAPI_AUTO_LENGTH, &argv[1]);
    napi_value result = nullptr;
    napi_call_function(g_env, undefined, callback, 2, argv, &result);
}

extern "C" void NexioOnCalendarHostReady(void (*fn)(int32_t, const char *));
extern "C" void NexioOnCalendarResult(int32_t requestId, bool ok, const char *payload);

/** ArkTS 侧回传：setCalendarResult(requestId, ok, payload)。 */
static napi_value NapiSetCalendarResult(napi_env env, napi_callback_info info) {
    size_t argc = 3;
    napi_value args[3] = {nullptr, nullptr, nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    int32_t requestId = 0;
    napi_get_value_int32(env, args[0], &requestId);
    bool ok = false;
    napi_get_value_bool(env, args[1], &ok);

    char *payload = nullptr;
    size_t len = 0;
    if (napi_get_value_string_utf8(env, args[2], nullptr, 0, &len) == napi_ok) {
        payload = new char[len + 1];
        napi_get_value_string_utf8(env, args[2], payload, len + 1, &len);
    }
    NexioOnCalendarResult(requestId, ok, payload != nullptr ? payload : "");
    delete[] payload;
    return nullptr;
}

// ── 网页导入：ArkUI 的 Web 组件只能由 ArkTS 建 ───────────────────────────────
// Kotlin → ArkTS 发指令（open / evalJs / resolve…），ArkTS → Kotlin 回事件与脚本回调。
static napi_ref g_webHostRef = nullptr;

static napi_value NapiRegisterWebHost(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    napi_valuetype type = napi_undefined;
    napi_typeof(env, args[0], &type);
    if (type != napi_function) return nullptr;
    g_env = env;
    napi_create_reference(env, args[0], 1, &g_webHostRef);
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, LOG_TAG, "web host registered");
    return nullptr;
}

static void HostWebCommand(const char *json) {
    if (g_env == nullptr || g_webHostRef == nullptr) return;
    napi_value callback = nullptr;
    if (napi_get_reference_value(g_env, g_webHostRef, &callback) != napi_ok || callback == nullptr) return;
    napi_value undefined = nullptr;
    napi_get_undefined(g_env, &undefined);
    napi_value argv[1] = {nullptr};
    napi_create_string_utf8(g_env, json, NAPI_AUTO_LENGTH, &argv[0]);
    napi_value result = nullptr;
    napi_call_function(g_env, undefined, callback, 1, argv, &result);
}

extern "C" void NexioOnWebHostReady(void (*fn)(const char *));
extern "C" void NexioOnWebHostEvent(const char *json);

/** ArkTS 侧回传：webHostEvent(json)。 */
static napi_value NapiWebHostEvent(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    char *json = nullptr;
    size_t len = 0;
    if (napi_get_value_string_utf8(env, args[0], nullptr, 0, &len) == napi_ok) {
        json = new char[len + 1];
        napi_get_value_string_utf8(env, args[0], json, len + 1, &len);
    }
    NexioOnWebHostEvent(json != nullptr ? json : "");
    delete[] json;
    return nullptr;
}

// 初始化协程的主线程调度器（Kotlin 侧 @CName("NexioInitMainHandler")）。
// 不调的话 MainHandler.tsfn 恒为 null，Dispatchers.Main 会内联在调用方线程执行。
extern "C" void NexioInitMainHandler(void *env);

static napi_value Init(napi_env env, napi_value exports) {
    NexioInitMainHandler(reinterpret_cast<void *>(env));

    napi_property_descriptor desc[] = {
        {"MainArkUIViewController", nullptr, NapiMainArkUIViewController,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"setAppEnv", nullptr, NapiSetAppEnv,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"setAppInfo", nullptr, NapiSetAppInfo,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"registerWallpaperPicker", nullptr, NapiRegisterWallpaperPicker,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"setWallpaperBytes", nullptr, NapiSetWallpaperBytes,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"registerHttpSender", nullptr, NapiRegisterHttpSender,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"setHttpResult", nullptr, NapiSetHttpResult,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"registerToast", nullptr, NapiRegisterToast,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"registerOverlay", nullptr, NapiRegisterOverlay,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"overlayPopBack", nullptr, NapiOverlayPopBack,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"registerFilePicker", nullptr, NapiRegisterFilePicker,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"registerShareHost", nullptr, NapiRegisterShareHost,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"setFileResult", nullptr, NapiSetFileResult,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"registerCalendarSync", nullptr, NapiRegisterCalendarSync,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"setCalendarResult", nullptr, NapiSetCalendarResult,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"registerWebHost", nullptr, NapiRegisterWebHost,
         nullptr, nullptr, nullptr, napi_default, nullptr},
        {"webHostEvent", nullptr, NapiWebHostEvent,
         nullptr, nullptr, nullptr, napi_default, nullptr},
    };
    napi_define_properties(env, exports, sizeof(desc) / sizeof(desc[0]), desc);

    androidx::compose::ui::arkui::utils::XComponentHolder::GetInstance()->InitXComponent(env, exports);
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, LOG_TAG, "Init: XComponentHolder wired");
    NexioOnWallpaperPickHostReady(HostRequestWallpaperPick);
    NexioOnHttpHostReady(HostHttpSend);
    NexioOnToastHostReady(HostShowToast);
    NexioOnOverlayHostReady(HostPublishOverlay);
    NexioOnFileHostReady(HostRequestFile);
    NexioOnShareHostReady(HostShareRequest);
    NexioOnCalendarHostReady(HostCalendarRequest);
    NexioOnWebHostReady(HostWebCommand);
    NexioRegisterAiCrypto();
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
