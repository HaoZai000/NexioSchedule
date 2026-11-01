# libskikobridge.so（来自 HAR，仅用于链接期）

`compose_arkui_utils` 的 C++ 会调 `skikobridge_*` 一组 C 函数，实现在
`@cpf-kmp-cmp/compose` 1.9.2-1.2.0 的 `libs/arm64-v8a/libskikobridge.so` 里。

**这里放它只是为了链接期记录 DT_NEEDED** —— 不加的话 `libnexioohos.so` 的依赖表里没有
libskikobridge.so，运行时 ld.so 不会去那个库里找符号，
`skikobridge_napi_get_system_theme` 这类 GOT 槽位被填成 0，
一走到 `syncSystemThemeState` 就 NULL 解引用（2026-11-01 实测）。

运行时真正加载的是 HAP 里随 HAR 打进去的那份，与本文件是同一份二进制。
升级 HAR 时同步替换本文件。
