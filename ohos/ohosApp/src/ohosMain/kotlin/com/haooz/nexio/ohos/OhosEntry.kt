package com.haooz.nexio.ohos

import androidx.compose.ui.window.ComposeArkUIViewController
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_env
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_value

/**
 * 鸿蒙侧唯一的导出符号：ArkTS 的 `Compose({ controller, libraryName: "entry" })` 通过
 * `libentry.so`（NAPI 胶水）拿到这个控制器句柄；UI 树整体活在 Kotlin 这边，ArkTS 不复制界面语义。
 *
 * 函数名与签名必须与 `@cpf-kmp-cmp/compose` HAR 的约定一致 —— 改名等于宿主找不到入口。
 * `ComposeArkUIViewController(env, content)` 来自 `androidx.compose.ui.window`（OH 分支新增），
 * 已在 ui-ohosarm64-1.9.2-1.2.0-08.klib 的元数据里核对过。
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@CName("MainArkUIViewController")
fun MainArkUIViewController(env: napi_env): napi_value {
    return ComposeArkUIViewController(env) { NexioRoot() }
}
