package com.haooz.nexio.ohos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.navigation.AboutRoute
import com.haooz.chedule.ui.navigation.AiImportRoute
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.navigation.AppreciateAuthorRoute
import com.haooz.chedule.ui.navigation.BackupAndMigrationRoute
import com.haooz.chedule.ui.navigation.ChangelogRoute
import com.haooz.chedule.ui.navigation.CommunicationRoute
import com.haooz.chedule.ui.navigation.CourseManageRoute
import com.haooz.chedule.ui.navigation.CourseReminderRoute
import com.haooz.chedule.ui.navigation.CourseTimeSettingsRoute
import com.haooz.chedule.ui.navigation.EducationalImportRoute
import com.haooz.chedule.ui.navigation.FeedbackHistoryRoute
import com.haooz.chedule.ui.navigation.FeedbackRoadmapRoute
import com.haooz.chedule.ui.navigation.FeedbackRoute
import com.haooz.chedule.ui.navigation.HolidaySettingsRoute
import com.haooz.chedule.ui.navigation.LicenseRoute
import com.haooz.chedule.ui.navigation.LocalBackupRoute
import com.haooz.chedule.ui.navigation.PreferenceSettingsRoute
import com.haooz.chedule.ui.navigation.PrivacyPolicyRoute
import com.haooz.chedule.ui.navigation.SettingsRoute
import com.haooz.chedule.ui.navigation.UpdateSettingsRoute
import com.haooz.chedule.ui.navigation.WebDavSettingsRoute
import com.haooz.chedule.ui.navigation.WidgetIntroRoute
import com.haooz.chedule.ui.utils.ShareCodeImport
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 鸿蒙端的子页路由表。
 *
 * 为什么每个平台要有一份：`AppRoute` 是共享的，但「路由 → 具体页面 + 平台能力注入」这层
 * 天然与平台相关（Android 要传 Activity / 选图器 / 剪贴板，iOS 要传 UIKit 容器）。
 * Android 在 `:androidApp` 的 `AppRouteContent.kt`，iOS 在 `NexioIosRouteContent`，这里是第三份。
 *
 * ⚠ 本文件名不能叫 `AppRouteContent.kt`：Android 侧同包已有同名文件，两边会生成同一个
 * facade 类 `AppRouteContentKt`（见 `ui-shared` 的 AppRoutes.kt 顶部的告警）。
 *
 * 大部分页面直接调共享的 `XxxRoute(router)` —— 它们对平台能力的参数都有 Noop 默认值。
 * 少数真的需要平台能力的（网页导入 / 选文件）暂时挂 [RouteUnavailable]，等接 ArkTS 桥。
 */
@Composable
fun OhosRouteContent(router: AppRouter, route: AppRoute) {
    when (route) {
        // 鸿蒙没有应用内更新通道（应用市场分发），隐藏「更新设置」入口（同 iOS）
        AppRoute.Settings -> SettingsRoute(router = router, showUpdateSettings = false)
        AppRoute.About -> AboutRoute(router = router)
        AppRoute.Changelog -> ChangelogRoute(router)
        AppRoute.License -> LicenseRoute(router)
        AppRoute.PrivacyPolicy -> PrivacyPolicyRoute(router)
        AppRoute.PreferenceSettings -> PreferenceSettingsRoute(router = router)
        AppRoute.UpdateSettings -> UpdateSettingsRoute(router = router)
        AppRoute.Communication -> CommunicationRoute(router)
        AppRoute.Feedback -> FeedbackRoute(router)
        AppRoute.FeedbackHistory -> FeedbackHistoryRoute(router)
        AppRoute.FeedbackRoadmap -> FeedbackRoadmapRoute(router)
        AppRoute.LocalBackup -> LocalBackupRoute(router = router)
        AppRoute.AppreciateAuthor -> AppreciateAuthorRoute(router)
        AppRoute.AiImport -> AiImportRoute(router = router)
        AppRoute.WidgetIntro -> WidgetIntroRoute(router = router)
        AppRoute.CourseReminder -> CourseReminderRoute(router = router)
        AppRoute.HolidaySettings -> HolidaySettingsRoute(router = router)
        AppRoute.WebDavSettings -> WebDavSettingsRoute(router = router)
        AppRoute.CourseTimeSettings -> CourseTimeSettingsRoute(router = router)
        AppRoute.CourseManage -> CourseManageRoute(router = router)
        AppRoute.EducationalImport -> EducationalImportRoute(router = router)

        AppRoute.ScheduleImport ->
            BackupAndMigrationRoute(
                router = router,
                mode = ScheduleDataManageMode.Import,
                // 本站口令 / 星链走纯 HTTP；WakeUp 要无头 WebView，鸿蒙还没接（wakeUpRunner 为 null 时给明确报错）
                onFetchShareCode = { code -> ShareCodeImport.fetchShareCode(code) },
                onImportThirdParty = { source, code -> ShareCodeImport.importThirdParty(source, code) },
            )

        AppRoute.ScheduleExport ->
            BackupAndMigrationRoute(router = router, mode = ScheduleDataManageMode.Export)

        AppRoute.ScheduleBackup ->
            BackupAndMigrationRoute(router = router, mode = ScheduleDataManageMode.Backup)

        // 需要 ArkTS 的 Web 组件 / 选文件能力，还没搭桥
        AppRoute.WebImport -> RouteUnavailable(router, "网页导入")
    }
}

/** 平台能力未接入时的占位页：说明缺什么、能干什么（返回），不伪装成空页面。 */
@Composable
private fun RouteUnavailable(router: AppRouter, name: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(name, fontSize = 18.sp, color = MiuixTheme.colorScheme.onSurface)
            Text(
                "该页面需要 ArkTS 侧的 Web 组件 / 文件选择器，尚未搭桥",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            Text(
                "点此返回",
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable { router.popBack() },
            )
        }
    }
}
