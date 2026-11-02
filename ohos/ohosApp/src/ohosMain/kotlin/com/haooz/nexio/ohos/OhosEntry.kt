package com.haooz.nexio.ohos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.ComposeArkUIViewController
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.ui.utils.LocalNativeTopBarHost
import com.haooz.chedule.ui.utils.NativeTopBarHost
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
    installPlatformBackends()
    return ComposeArkUIViewController(env) { OhosRoot() }
}

/**
 * 鸿蒙根：装上「右上角按钮交给宿主」的注入点。
 *
 * 标题/星期行/渐进模糊仍由 Compose 画，只有右上角两颗按钮和菜单走 ArkTS，这里把语义推过去、
 * 把宿主点的按钮转回来。观察必须放在组合里：`snapshotFlow` 才能拿到 Compose 的状态快照。
 */
@Composable
private fun OhosRoot() {
    val topBar = remember { NativeTopBarHost().apply { ownsTopRight = true } }
    val scope = rememberCoroutineScope()
    LaunchedEffect(topBar, scope) {
        TopBarBridge.bind(topBar, scope)
        snapshotFlow { topBar.state }.collect { TopBarBridge.publish(it) }
    }
    CompositionLocalProvider(LocalNativeTopBarHost provides topBar) {
        NexioRoot()
    }
}

/**
 * 平台能力注入 —— 与 `IosAppEntry` / `NexioApplication` 里做的是同一件事。
 *
 * 必须在建 controller 之前跑完：`WallpaperStore.synchronousLoader` 是**首帧同步路径**，
 * 晚一步就会先画一帧纯色再跳成用户壁纸。
 *
 * ⚠ 这里只在 `OhosEnv` 就绪（ArkTS 已调 `setAppEnv`）时才装上壁纸后端 ——
 * 路径没传进来时读盘会失败，不如让它保持 null（表现为"没有壁纸"，卡片走纯色底）。
 */
private fun installPlatformBackends() {
    if (!OhosEnv.isReady) return
    if (WallpaperStore.backend == null) {
        val backend = OhosWallpaperBackend()
        WallpaperStore.backend = backend
        WallpaperStore.synchronousLoader = { backend.loadNow() }
    }
}
