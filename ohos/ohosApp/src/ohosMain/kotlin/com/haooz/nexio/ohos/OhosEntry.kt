package com.haooz.nexio.ohos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeArkUIViewController
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.ui.activities.main.CourseScheduleApp
import com.haooz.chedule.ui.navigation.AppNavHost
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.MainLayerTransition
import com.haooz.chedule.ui.navigation.NavTransitionState
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.LocalNativeBottomBarHost
import com.haooz.chedule.ui.utils.LocalNativeTopBarHost
import com.haooz.chedule.ui.utils.NativeBottomBarHost
import com.haooz.chedule.ui.utils.NativeTopBarHost
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_env
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_value

/**
 * 鸿蒙侧的两个 Compose 实例（与 iOS 的 mainController / overlayController 同构）。
 *
 * 一个 XComponent 只有一个层，画在它里面的东西永远盖不住 ArkUI 的兄弟节点 ——
 * 顶栏按钮、底栏胶囊都是 ArkUI 画的，所以二级页必须待在**另一个 Compose 实例**里，
 * 由 ArkUI 把那一层排在顶栏/底栏之后（z 序更高）。这样设置页是真的压在它们上面，
 * 而不是靠「把顶栏底栏藏起来」装作被盖住。
 *
 * 两个实例共用下面这一份状态：Compose 的 snapshot state 是进程级的，两边读同一个对象，
 * 一边写另一边同帧失效 —— 不需要任何手工同步。
 */
private val sharedRouter = AppRouter()
private val sharedMainTransition = NavTransitionState(0f)
private val sharedTopBar = NativeTopBarHost().apply { ownsTopRight = true }
private val sharedBottomBar = NativeBottomBarHost().apply { ownsBottomBar = true }

/**
 * 主界面实例。函数名与签名必须与 `@cpf-kmp-cmp/compose` HAR 的约定一致。
 *
 * `ComposeArkUIViewController(env, content)` 来自 `androidx.compose.ui.window`（OH 分支新增）。
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@CName("MainArkUIViewController")
fun MainArkUIViewController(env: napi_env): napi_value {
    installPlatformBackends()
    return ComposeArkUIViewController(env) { OhosMainRoot() }
}

/**
 * 二级页实例：只画路由栈，主界面完全不在这个实例里。
 *
 * ⚠ 由 ArkUI 常驻挂载（不压栈时 `Visibility.Hidden`），不能按需创建 ——
 * 转场进度、页面状态全在这个组合里，重建一次就等于把正在滑的页面扔掉。
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@CName("OverlayArkUIViewController")
fun OverlayArkUIViewController(env: napi_env): napi_value =
    ComposeArkUIViewController(env) { OhosOverlayRoot() }

/** 主界面：常驻底座 + 顶栏/底栏语义发布。子页不在这里渲染。 */
@Composable
private fun OhosMainRoot() {
    installPlatformStorage()
    CourseScheduleTheme {
        BindNativeChrome()
        CompositionLocalProvider(
            LocalNativeTopBarHost provides sharedTopBar,
            LocalNativeBottomBarHost provides sharedBottomBar,
            LocalAppRouter provides sharedRouter,
        ) {
            Box(Modifier.fillMaxSize()) {
                // 主界面被推走的那段视差：进度由覆盖层的 AppNavHost 写，这里逐帧读
                MainLayerTransition(sharedMainTransition) {
                    CourseScheduleApp(
                        // 选壁纸入口：系统选图器只能由 ArkTS 起，桥在 ohosSharedCore 里
                        onPickWallpaperImage = PlatformWallpaperPicker,
                        // 鸿蒙没有应用内更新通道（应用市场分发），隐藏更新设置入口（同 iOS）
                        showUpdateSettings = false,
                    )
                }
                // 有子页时 ArkUI 才把覆盖层显出来；发布点在组合期读 hasOverlay 才能订阅到栈变化
                val hasOverlay = sharedRouter.hasOverlay
                SideEffect { OverlayBridge.publish(hasOverlay) }
            }
        }
    }
}

/** 二级页：整栈常驻渲染，转场、手势、return 全走与其它端同一份 `AppNavHost`。 */
@Composable
private fun OhosOverlayRoot() {
    CourseScheduleTheme {
        CompositionLocalProvider(
            LocalAppRouter provides sharedRouter,
            LocalNativeTopBarHost provides sharedTopBar,
            LocalNativeBottomBarHost provides sharedBottomBar,
        ) {
            AppNavHost(router = sharedRouter, mainTransition = sharedMainTransition) { route ->
                OhosRouteContent(sharedRouter, route)
            }
        }
    }
}

/**
 * 顶栏/底栏语义 → ArkUI。
 *
 * 这里把语义推过去、把宿主点的东西转回来。用 `onState` 同步出口而不是 `snapshotFlow`：
 * 后者要等 Compose 的 effect 协程被调度，至少晚一帧，翻页时按钮会肉眼可见地落后于页面。
 */
@Composable
private fun BindNativeChrome() {
    val scope = rememberCoroutineScope()
    DisposableEffect(sharedTopBar, scope) {
        TopBarBridge.bind(sharedTopBar, scope)
        sharedTopBar.onState = { TopBarBridge.publish(it) }
        // 绑定前可能已经发布过（首帧顺序不定），补一次
        TopBarBridge.publish(sharedTopBar.state)
        onDispose {
            sharedTopBar.onState = null
            TopBarBridge.publish(null)
        }
    }
    DisposableEffect(sharedBottomBar, scope) {
        BottomBarBridge.bind(sharedBottomBar, scope)
        sharedBottomBar.onState = { BottomBarBridge.publish(it) }
        BottomBarBridge.publish(sharedBottomBar.state)
        onDispose {
            sharedBottomBar.onState = null
            BottomBarBridge.publish(null)
        }
    }
}

private fun installPlatformBackends() {
    if (!OhosEnv.isReady) return
    if (WallpaperStore.backend == null) {
        val backend = OhosWallpaperBackend()
        WallpaperStore.backend = backend
        WallpaperStore.synchronousLoader = { backend.loadNow() }
    }
}
