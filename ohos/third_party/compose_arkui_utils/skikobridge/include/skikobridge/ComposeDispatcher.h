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

#ifndef SKIKOBRIDGE_MAIN_CPP_INCLUDE_SKIKOBRIDGE_COMPOSEDISPATCHER_H_
#define SKIKOBRIDGE_MAIN_CPP_INCLUDE_SKIKOBRIDGE_COMPOSEDISPATCHER_H_

#include <js_native_api_types.h>
#define COMPOSE_EXPORT extern "C" __attribute__((visibility("default")))

typedef void (*skikobridge_async_vsync_callback)(int64_t timestamp, void* dataContext);

typedef void (*skikobridge_async_callback)(void *data);

/* 获取当前的 napi_env */
COMPOSE_EXPORT napi_env skikobridge_get_current_napi_env();

/* 绑定 JS 的运行环境 */
COMPOSE_EXPORT void skikobridge_compose_coroutines_dispatcher_bind_jsenv(void *jsEnv);

/* 获取屏幕像素密度 */
COMPOSE_EXPORT float skikobridge_compose_get_screen_density();

/*
请求一帧 Vsync 回调
*/
COMPOSE_EXPORT void skikobridge_compose_request_vsync_frame(void *dataContext,
                                                            skikobridge_async_vsync_callback callBack);

/*
一段时间后将任务回抛到 JS 线程进行执行 after 是毫秒 
TODO:需要处理取消的情况
*/
COMPOSE_EXPORT void skikobridge_compose_coroutines_dispatcher_async_task(float after, void *data,
                                                                         skikobridge_async_callback callBack);

COMPOSE_EXPORT void skikobridge_compose_coroutines_dispatcher_async_work(void *data,
                                                                         skikobridge_async_callback callBack);

/* LogI */
COMPOSE_EXPORT void skikobridge_compose_logI(const char *log);

/* LogE */
COMPOSE_EXPORT void skikobridge_compose_logE(const char *log);

/* LogW */
COMPOSE_EXPORT void skikobridge_compose_logW(const char *log);

/* LogD */
COMPOSE_EXPORT void skikobridge_compose_logD(const char *log);

/* 通过 NAPI 调用 ArkTS 注入的 openUri 函数 */
COMPOSE_EXPORT void skikobridge_napi_open_uri(const char *uri);

/* 通过 NAPI 调用 ArkTS 注入的 getSystemTheme 函数 */
COMPOSE_EXPORT int skikobridge_napi_get_system_theme(void);

/* 通过 NAPI 调用 ArkTS 注入的剪贴板函数 */
COMPOSE_EXPORT void skikobridge_napi_clipboard_set_text(const char *text);
COMPOSE_EXPORT const char *skikobridge_napi_clipboard_get_text(void);
COMPOSE_EXPORT bool skikobridge_napi_clipboard_has_text(void);

/* 设置 platform provider（LocaleAndStringProvider），用于 clipboard/openUri/getSystemTheme，由 registerLocaleAndStringProvider 时调用 */
COMPOSE_EXPORT void skikobridge_set_platform_provider(napi_env env, napi_value provider);

#endif  //SKIKOBRIDGE_MAIN_CPP_INCLUDE_SKIKOBRIDGE_COMPOSEDISPATCHER_H_
