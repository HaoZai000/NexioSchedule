/** 关于页 —— 现在只做「路由宿主」，页面本体见 [AboutScreen] */
package com.haooz.chedule.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.haooz.chedule.ui.navigation.AppNavHost
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.AppRouteContent
import com.haooz.chedule.ui.navigation.rememberAppRouter
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars

/**
 * 关于页所在的**路由宿主**。
 *
 * ## 这一版改了什么
 *
 * 原来 `AboutActivity` 里内联了 1200 行的 `AboutScreen`，而更新日志 / 开源协议 /
 * 隐私政策三页各自是一个 96 行的 Activity 壳（三份逐字重复）。现在：
 *
 * - `AboutScreen` 抽到同目录的 `AboutScreen.kt`
 * - 本文件只做宿主：创建 [rememberAppRouter]、渲染 [AppNavHost]
 * - 那三个页面**不再是 Activity**，而是本宿主里的路由（[AppRoute.Changelog] 等）
 *
 * ## 为什么宿主先放这儿，而不是 `MainActivity`
 *
 * 单宿主的终点是 `MainActivity`，但它有 `handleBackNavigation` / `onNewIntent` /
 * widget 深链等一堆 Android 耦合，一次动它风险大。先把模式在一个 21 行的壳上跑通，
 * 下一个增量再把宿主迁到 `MainActivity` —— 那时 [AppRouteContent] 与路由表**原样搬过去**，
 * 不会白做。
 *
 * ## 入口参数
 *
 * 主界面的隐私弹窗要直接打开隐私政策，所以支持指定**初始路由**。
 * 这就是将来 route 参数（`AppRoute.PrivacyPolicy` 带参数）的雏形。
 */
class AboutActivity : ComponentActivity() {

    companion object {
        /** 初始路由的 extra key。值取 [AppRoute.id]；缺省/不认识时落到 [AppRoute.About]。 */
        const val EXTRA_INITIAL_ROUTE = "initial_route"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置页点击记的是 about/open，这里区分成 activity_open，免得日志里两条同名字分不清
        com.haooz.chedule.ui.utils.FeatureLog.about("activity_open")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyThemeAwareSystemBars()

        // 从 Intent 读初始路由：主界面隐私弹窗 → 直接开隐私政策。
        // 只在 onCreate 读一次；进程重建时由 rememberSaveable 恢复栈，不再看 Intent。
        val initialRoute = if (savedInstanceState == null) {
            AppRoute.fromId(intent?.getStringExtra(EXTRA_INITIAL_ROUTE).orEmpty()) ?: AppRoute.About
        } else {
            AppRoute.About
        }

        setContent {
            CourseScheduleTheme {
                val router = rememberAppRouter(initialRoute)
                AppNavHost(
                    router = router,
                    onExit = { finish() },
                ) { route ->
                    AppRouteContent(
                        router = router,
                        route = route,
                        onExit = { finish() },
                    )
                }
            }
        }
    }
}
