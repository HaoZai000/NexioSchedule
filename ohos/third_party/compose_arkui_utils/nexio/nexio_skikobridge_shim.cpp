/*
 * 补齐 HAR 版 libskikobridge.so 缺的符号 —— 我们自己的文件，不改上游源码。
 *
 * 背景：compose_arkui_utils 的 arkui_view_controller_wrapper.cpp（上游 2026-10-30 快照）
 * 在 registerLocaleAndStringProvider 里会调 `skikobridge_set_platform_provider`。
 * 但 ohpm 上 `@cpf-kmp-cmp/compose` 1.9.2-1.2.0 打的 libskikobridge.so 只导出了 12 个
 * COMPOSE_EXPORT 函数，没有这一个（比上游源码少一次提交）。于是 libnexioohos.so 里该符号
 * 是 U 且运行时解析不到，宿主一走到 onAppear 就跳空地址：
 *   SIGSEGV(SEGV_ACCERR)  #00 0x1932f00  #01 ...RegisterLocaleAndStringProvider+116
 *
 * 这里在同一个静态库里给出定义，链接时本 .so 优先命中自己的定义，不再去查 libskikobridge.so。
 *
 * 语义影响：platform provider 只被 skikobridge 的 openUri / clipboard / systemTheme 用，
 * 且取不到时有空值保护（ComposeDispatcher.cpp 会 log 后 return），所以不设置＝那几个
 * 平台能力不可用，不影响首屏渲染。将来 HAR 补上该导出，删掉本文件即可。
 */

#include "compose/xcomponent_holder.h"
#include "napi/native_api.h"
#include "hilog/log.h"

static const char *kTag = "nexio-shim";

extern "C" void skikobridge_set_platform_provider(napi_env env, napi_value provider) {
    (void)env;
    (void)provider;
    OH_LOG_Print(LOG_APP, LOG_INFO, LOG_DOMAIN, kTag,
                 "skikobridge_set_platform_provider: no-op (HAR skikobridge lacks this symbol)");
}
