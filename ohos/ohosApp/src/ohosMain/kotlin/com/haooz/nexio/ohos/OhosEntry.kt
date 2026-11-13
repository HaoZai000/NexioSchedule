package com.haooz.nexio.ohos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeArkUIViewController
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.platform.PrivacyConsent
import com.haooz.chedule.data.platform.StatsReporter
import com.haooz.chedule.ui.activities.main.CourseScheduleApp
import com.haooz.chedule.ui.navigation.AppNavHost
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.MainLayerTransition
import com.haooz.chedule.ui.navigation.rememberNavTransitionState
import com.haooz.chedule.ui.screens.PrivacyConsentScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_env
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_value

/**
 * 鸿蒙侧唯一的 Compose 实例 —— 结构逐字对应安卓的 `MainActivity.setContent`。
 *
 * 主界面、顶栏、底栏、子页顶栏、弹窗、设置页全部由 `:ui-shared` 的 Compose 画；
 * ArkUI 只负责挂一个 XComponent 与它盖不住的原生页面（系统 Web、文件选择器等）。
 */
private val sharedRouter = AppRouter()

/**
 * 主界面实例。函数名与签名必须与 `@cpf-kmp-cmp/compose` HAR 的约定一致。
 *
 * `ComposeArkUIViewController(env, content)` 来自 `androidx.compose.ui.window`（OH 分支新增）。
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@CName("MainArkUIViewController")
fun MainArkUIViewController(env: napi_env): napi_value {
    installPlatformBackends()
    return ComposeArkUIViewController(env) { OhosRoot() }
}

/**
 * 系统返回键 → 出栈。
 *
 * 鸿蒙 target 上 `rememberNavigationBack` 是空实现，Compose 内部接不到系统返回；
 * ArkTS 侧 `onBackPress` 检测到有子页时调这里消费事件，否则系统默认行为是退出应用。
 * ArkTS 主线程与 Compose 主线程同线程，直接改 snapshot list 是安全的。
 */
@CName("NexioOnOverlayPopBack")
fun NexioOnOverlayPopBack() {
    sharedRouter.popBack()
}

/**
 * 根组合：主界面常驻底座 + 子页叠加层 + 隐私弹窗，全部在这一个 Compose 实例里。
 *
 * 顶栏/底栏不再由 ArkUI 接管，因此不需要拆成两个 XComponent；
 * 子页与主界面的 z 序、转场、玻璃采样都与安卓同构。
 */
@Composable
private fun OhosRoot() {
    installPlatformStorage()
    // 先同步一次磁盘状态，再按 StateFlow 观察：关于页「撤回同意」后弹窗会立即回来
    remember { PrivacyConsent.refresh() }
    val privacyAgreed by PrivacyConsent.consented.collectAsState()
    LaunchedEffect(Unit) {
        // 合规：仅在用户已同意隐私政策后上报设备信息
        if (PrivacyConsent.hasConsented()) {
            StatsReporter.reportActive()
            StatsReporter.reportInstallOnce()
        }
    }
    CourseScheduleTheme {
        CompositionLocalProvider(LocalAppRouter provides sharedRouter) {
            // 主界面被推走的那段视差：进度由 AppNavHost 写，这里逐帧读
            val mainTransition = rememberNavTransitionState(
                initialValue = if (sharedRouter.hasOverlay) 1f else 0f,
            )
            Box(Modifier.fillMaxSize()) {
                MainLayerTransition(mainTransition) {
                    CourseScheduleApp(
                        privacyConsented = privacyAgreed,
                        // 选壁纸入口：系统选图器只能由 ArkTS 起，桥在 ohosSharedCore 里
                        onPickWallpaperImage = PlatformWallpaperPicker,
                        // 鸿蒙没有应用内更新通道（应用市场分发），隐藏更新入口（同 iOS）
                        showUpdateSettings = false,
                    )
                }
                // 子页叠加层：设置、导入导出、文档页等（就地渲染，与安卓同一套转场）
                AppNavHost(
                    router = sharedRouter,
                    mainTransition = mainTransition,
                    // 系统返回键的「有没有子页」：ArkTS 只读这一个布尔，页面本身全在 Compose 里
                    onOverlayActivityChanged = { OverlayBridge.publishOverlay(it) },
                ) { _, route ->
                    OhosRouteContent(sharedRouter, route)
                }

                val hasOverlay = sharedRouter.hasOverlay
                SideEffect { OverlayBridge.publishMain(hasOverlay) }

                // 隐私弹窗是模态层：画在最后，自然盖住 Compose 画的底栏
                if (!privacyAgreed) {
                    PrivacyConsentScreen(
                        onAgree = {
                            PrivacyConsent.setConsented()
                            StatsReporter.reportActive()
                            StatsReporter.reportInstallOnce()
                        },
                        onDecline = { ShareHostBridge.request("exitApp", "") },
                        onOpenPolicy = {
                            sharedRouter.navigate(com.haooz.chedule.ui.navigation.AppRoute.PrivacyPolicy)
                        },
                        policyPageVisible = sharedRouter.routes.any {
                            it == com.haooz.chedule.ui.navigation.AppRoute.PrivacyPolicy
                        },
                    )
                }
            }
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
