package com.haooz.nexio.ohos

import kotlin.experimental.ExperimentalNativeApi

/**
 * 渲染后端标识 —— **必须由应用侧导出**，否则宿主一创建 controller 就崩。
 *
 * compose 插件会生成一份同名文件（build/generated/compose/ohosRendererBackend），但不会把它
 * 加进编译源集；缺了它，`androidx_compose_ui_arkui_ArkUIViewController_getRendererTypeId`
 * 在 libnexioohos.so 里就是未定义符号，`ArkUIViewControllerWrapper::Wrapped` 里那条 `bl`
 * 直接跳进未映射地址（2026-11-01 模拟器实测 SIGSEGV @0x1932210）。
 *
 * 1 = fusion-renderer（统一渲染，走 OHRender），与插件日志 `rendererBackend=fusion-renderer`
 * 一致。改成 0（skia 自渲染）并不能绕过下面这个问题，已实测同样崩溃，所以保持与插件一致。
 */
@OptIn(ExperimentalNativeApi::class)
@CName("androidx_compose_ui_arkui_ArkUIViewController_getRendererTypeId")
fun composeOhosRendererBackendId(): Int = 1
