package com.haooz.nexio.ohos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.haooz.chedule.ui.activities.main.CourseScheduleApp
import com.haooz.chedule.ui.navigation.AppNavHost
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.utils.LocalNativeTopBarHost
import com.haooz.chedule.ui.navigation.MainLayerTransition
import com.haooz.chedule.ui.navigation.rememberAppRouter
import com.haooz.chedule.ui.navigation.rememberNavTransitionState
import com.haooz.chedule.ui.theme.CourseScheduleTheme

/**
 * 鸿蒙端根组合 —— 结构逐字对应 `MainActivity.setContent`。
 *
 * **这里没有任何 UI**：主界面、顶栏、底栏、弹窗、设置页、文档页全部来自 `:ui-shared`
 * （与 Android / iOS / 桌面同一份源码）。本文件只做三件事：
 * 1. 注入 `AppStorage` 的存储实现；
 * 2. 套 `CourseScheduleTheme`；
 * 3. 起 `rememberAppRouter` + `AppNavHost` 子页叠加层（与 Android 同构）。
 *
 * 平台差异全部落在库层（`:sharedBackdrop` / `:sharedMiuix` 的 ohos 变体 +
 * `ohosSharedCore` 里的 actual）和 [OhosRouteContent] 的路由表，这里不写任何 `if (鸿蒙)`。
 */
@Composable
fun NexioRoot() {
    // 存储装配：sqlite3 自带在 `third_party/sqlite`（鸿蒙 NDK 的 sysroot 里没有），
    // 与 Android / iOS 同一个 `nexio.db` schema —— 数据是持久的。
    installPlatformStorage()
    CourseScheduleTheme {
        val router = rememberAppRouter()
        // 顶栏是 ArkUI 画的、在 Compose 之外：push 子页时主内容会缩放位移，它不会跟着动，
        // 所以栈非空就整条收起（组合期读 hasOverlay，才能订阅到栈变化）
        val topBarHost = LocalNativeTopBarHost.current
        if (topBarHost != null) {
            val hasOverlay = router.hasOverlay
            SideEffect { topBarHost.suppressed = hasOverlay }
        }
        CompositionLocalProvider(LocalAppRouter provides router) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 主界面（不在转场容器里）的首层进度：栈被恢复成非空时它应是「已被盖住」
                val mainTransition = rememberNavTransitionState(
                    initialValue = if (router.hasOverlay) 1f else 0f,
                )
                // 主界面常驻底座（今日 / 课表两页）
                MainLayerTransition(mainTransition) {
                    CourseScheduleApp(
                        // 选壁纸入口：系统选图器只能由 ArkTS 起，桥在 ohosSharedCore 里
                        onPickWallpaperImage = PlatformWallpaperPicker,
                        // 鸿蒙没有应用内更新通道（应用市场分发），隐藏更新设置入口（同 iOS）
                        showUpdateSettings = false,
                    )
                }
                // 子页叠加层：设置、导入导出、文档页等
                AppNavHost(router = router, mainTransition = mainTransition) { route ->
                    OhosRouteContent(router, route)
                }
            }
        }
    }
}
