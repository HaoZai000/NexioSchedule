package com.haooz.chedule.ui.navigation

import androidx.compose.runtime.Composable
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.ChangelogScreen
import com.haooz.chedule.ui.activities.LicenseScreen
import com.haooz.chedule.ui.activities.PrivacyPolicyScreen
import com.haooz.chedule.ui.components.DocumentPageScaffold

/**
 * 路由 → 页面的映射表。
 *
 * 这是**唯一**需要知道「哪个路由对应哪个 Screen」的地方 —— 宿主只负责创建
 * [AppRouter]、渲染 [AppNavHost]，不关心具体有哪些页面。
 *
 * 这样拆的用意：阶段 5 把 UI 搬进共享模块时，**这个文件整体搬过去**即可，
 * 宿主（Android Activity / iOS UIViewController）不用跟着改。
 *
 * @param onExit 栈里没有上一页时的退出动作（对应原来 Activity 的 `finish()`）
 * @param mainContent [AppRoute.Main] 的内容。主界面目前还由 `MainActivity` 自己持有，
 *   所以留成参数；等主界面也路由化之后，这里改成直接调用即可。
 */
@Composable
fun AppRouteContent(
    router: AppRouter,
    route: AppRoute,
    onExit: () -> Unit,
    mainContent: @Composable () -> Unit = {},
) {
    when (route) {
        AppRoute.Main -> mainContent()

        AppRoute.About -> AboutScreen(
            onBack = { router.backOrExit(onExit) },
            liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(),
            // 宿主就是路由容器 → 直接入栈，而不是再开一个 Activity
            onNavigate = { router.navigate(it) },
        )

        AppRoute.Changelog -> DocumentPageScaffold(
            title = "更新日志",
            onBack = { router.backOrExit(onExit) },
        ) { scrollBehavior ->
            ChangelogScreen(scrollBehavior = scrollBehavior)
        }

        AppRoute.License -> DocumentPageScaffold(
            title = "开源协议",
            onBack = { router.backOrExit(onExit) },
        ) { scrollBehavior ->
            LicenseScreen(scrollBehavior = scrollBehavior)
        }

        AppRoute.PrivacyPolicy -> DocumentPageScaffold(
            title = "隐私政策",
            onBack = { router.backOrExit(onExit) },
        ) { scrollBehavior ->
            PrivacyPolicyScreen(scrollBehavior = scrollBehavior)
        }
    }
}
