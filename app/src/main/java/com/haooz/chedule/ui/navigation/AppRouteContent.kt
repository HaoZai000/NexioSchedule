package com.haooz.chedule.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AppreciateAuthorScreen
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.ChangelogScreen
import com.haooz.chedule.ui.activities.CommunicationScreen
import com.haooz.chedule.ui.activities.LicenseScreen
import com.haooz.chedule.ui.activities.LocalBackupScreen
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.activities.PreferenceSettingsScreen
import com.haooz.chedule.ui.activities.PrivacyPolicyScreen
import com.haooz.chedule.ui.activities.UpdateSettingsScreen
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.ui.utils.isAppDarkTheme

/**
 * 子页路由 → 页面的映射表。
 *
 * 这是**唯一**需要知道「哪个路由对应哪个 Screen」的地方 —— 宿主只负责创建
 * [AppRouter]、渲染 [AppNavHost]，不关心具体有哪些页面。
 *
 * 这样拆的用意：阶段 5 把 UI 搬进共享模块时，**这个文件整体搬过去**即可，
 * 宿主（Android Activity / iOS UIViewController）不用跟着改。
 *
 * ## 本批已迁移 vs 仍是 Activity
 *
 * 已迁移（9 条路由）：About / Changelog / License / PrivacyPolicy /
 * PreferenceSettings / UpdateSettings / Communication / LocalBackup / AppreciateAuthor
 *
 * 仍是 Activity（各有 Activity 级状态或平台依赖，下一批处理）：
 * HolidaySettings（URL 拉取）/ WidgetIntro（弹窗+按钮）/ CourseReminder（超级岛）/
 * WebDavSettings（状态回调）/ BackupAndMigration（3 个 ViewModel + mode）/
 * CourseManage（571 行业务逻辑）/ CourseTimeSettings / SwitchSchedule（1928 行）/
 * EducationalImport（WebView）/ AiImport
 */
@Composable
fun AppRouteContent(
    router: AppRouter,
    route: AppRoute,
) {
    when (route) {
        AppRoute.About -> AboutRoute(router)

        AppRoute.Changelog -> DocumentPageScaffold(
            title = "更新日志",
            onBack = { router.popBack() },
        ) { scrollBehavior, _ ->
            ChangelogScreen(scrollBehavior = scrollBehavior)
        }

        AppRoute.License -> DocumentPageScaffold(
            title = "开源协议",
            onBack = { router.popBack() },
        ) { scrollBehavior, _ ->
            LicenseScreen(scrollBehavior = scrollBehavior)
        }

        AppRoute.PrivacyPolicy -> DocumentPageScaffold(
            title = "隐私政策",
            onBack = { router.popBack() },
        ) { scrollBehavior, _ ->
            PrivacyPolicyScreen(scrollBehavior = scrollBehavior)
        }

        AppRoute.PreferenceSettings -> DocumentPageScaffold(
            title = "应用偏好设置",
            onBack = { router.popBack() },
        ) { scrollBehavior, backdrop ->
            // 偏好设置页可能改主题 → 系统栏要跟着刷新（原来在 Activity 的 setContent 里）
            val activity = LocalActivity.current
            val isDark = isAppDarkTheme()
            LaunchedEffect(isDark) {
                activity?.applyThemeAwareSystemBars()
            }
            PreferenceSettingsScreen(
                scrollBehavior = scrollBehavior,
                liquidGlassBackdrop = backdrop,
            )
        }

        AppRoute.UpdateSettings -> DocumentPageScaffold(
            title = "更新设置",
            onBack = { router.popBack() },
        ) { scrollBehavior, backdrop ->
            UpdateSettingsScreen(
                scrollBehavior = scrollBehavior,
                liquidGlassBackdrop = backdrop,
            )
        }

        AppRoute.Communication -> DocumentPageScaffold(
            title = "交流与反馈",
            onBack = { router.popBack() },
        ) { scrollBehavior, _ ->
            CommunicationScreen(scrollBehavior = scrollBehavior)
        }

        AppRoute.LocalBackup -> DocumentPageScaffold(
            title = "本地备份",
            onBack = { router.popBack() },
        ) { scrollBehavior, backdrop ->
            LocalBackupScreen(
                scrollBehavior = scrollBehavior,
                liquidGlassBackdrop = backdrop,
            )
        }

        AppRoute.AppreciateAuthor -> DocumentPageScaffold(
            title = "捐赠支持",
            onBack = { router.popBack() },
        ) { scrollBehavior, _ ->
            AppreciateAuthorScreen(scrollBehavior = scrollBehavior)
        }

        // ── 数据管理（导入 / 导出 / 备份，共用 BackupAndMigrationScreen，按 mode 区分）──
        AppRoute.ScheduleImport -> BackupAndMigrationRoute(router, ScheduleDataManageMode.Import)
        AppRoute.ScheduleExport -> BackupAndMigrationRoute(router, ScheduleDataManageMode.Export)
        AppRoute.ScheduleBackup -> BackupAndMigrationRoute(router, ScheduleDataManageMode.Backup)
    }
}

/**
 * 课表导入 / 导出 / 备份。
 *
 * ⚠ 这三个入口**必须**走路由，不能再是 Activity：原来 `BackupAndMigrationActivity`
 * 是独立 Activity，`BackupAndMigrationScreen` 里的「本地备份」入口读 [LocalAppRouter]，
 * 而那个 Activity 的 setContent 没有 provide，默认值是 `error(...)` —— 一点开就崩。
 */
@Composable
private fun BackupAndMigrationRoute(router: AppRouter, mode: ScheduleDataManageMode) {
    val title = when (mode) {
        ScheduleDataManageMode.Import -> "课表导入"
        ScheduleDataManageMode.Export -> "课表导出"
        ScheduleDataManageMode.Backup -> "课表备份"
    }
    DocumentPageScaffold(
        title = title,
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        // 与原 BackupAndMigrationActivity 一致：ViewModel 由宿主创建后传入
        val courseViewModel: CourseViewModel = viewModel()
        val scheduleViewModel: ScheduleViewModel = viewModel()
        val settingsViewModel: SettingsViewModel = viewModel()
        BackupAndMigrationScreen(
            scrollBehavior = scrollBehavior,
            courseViewModel = courseViewModel,
            scheduleViewModel = scheduleViewModel,
            settingsViewModel = settingsViewModel,
            liquidGlassBackdrop = backdrop,
            mode = mode,
        )
    }
}

/** 关于页（原来内联在 AboutActivity 的 1200 行，已抽到 `AboutScreen.kt`）。 */
@Composable
private fun AboutRoute(router: AppRouter) {
    AboutScreen(
        onBack = { router.popBack() },
        liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(),
        onNavigate = { router.navigate(it) },
    )
}
