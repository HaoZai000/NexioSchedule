/*
 * skiko 要的 ICU 符号名与 CPF 提供的 libicu.a 对不上，这里做纯转发。
 *
 * 现象：`libnexioohos.so` 里 `uloc_getDefault_skiko` / `uloc_toLanguageTag_skiko`
 * 一直是 `U`（未定义），skia 后端第一次进 `ArkUIViewController_setContext` 就跳空地址：
 *   SIGSEGV(SEGV_ACCERR)  pc 0x49624c0
 *
 * 原因：skiko 的 Kotlin 侧通过 cinterop 把 ICU 的 `uloc_getDefault` 重命名成
 * `uloc_getDefault_skiko`（避免和其他链接进来的 ICU 撞名），但 CPF 打进
 * `skiko-ohosarm64` klib 的 `libicu.a` 里符号仍是原名。于是 skiko 找不到自己的符号。
 *
 * 处理：给出同名转发。参数与 ICU 的真实 C API 一致，符号名对上后
 * libicu.a 里对应的 object 才会被链接器拉进来。
 *
 * ⚠ 这不是「平台分支」：产出的行为就是 skiko 在 iOS/桌面上的同一套 ICU 调用，
 * 只是补上被改名的那两个入口。CPF 修好后删掉本文件即可。
 */

#include <cstdint>

extern "C" {

// ICU 的 UBool / UErrorCode 在 ABI 上分别是 int8_t / int32_t
const char *uloc_getDefault(void);

int32_t uloc_toLanguageTag(const char *localeID, char *langtag, int32_t langtagCapacity, int8_t strict,
                           int32_t *status);

// skiko 侧的名字
const char *uloc_getDefault_skiko(void) { return uloc_getDefault(); }

int32_t uloc_toLanguageTag_skiko(const char *localeID, char *langtag, int32_t langtagCapacity, int8_t strict,
                                 int32_t *status) {
    return uloc_toLanguageTag(localeID, langtag, langtagCapacity, strict, status);
}

} // extern "C"
