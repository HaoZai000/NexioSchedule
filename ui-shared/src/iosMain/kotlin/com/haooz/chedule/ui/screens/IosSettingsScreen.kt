package com.haooz.chedule.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.IosCourseLiveActivity
import com.haooz.chedule.data.IosCourseNotifier
import com.haooz.chedule.data.IosWidgetSnapshot
import com.haooz.chedule.data.backup.BackupFileController
import com.haooz.chedule.data.backup.ScheduleFileController
import com.haooz.chedule.data.platform.AppInfo
import com.haooz.chedule.data.platform.ReminderController
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.chedule.ui.activities.main.SettingsPaneArgs
import com.haooz.chedule.ui.settings.DataManagePlatform
import com.haooz.chedule.ui.settings.SettingsDirectPlatform
import com.haooz.chedule.ui.utils.ShareCodeImport
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.about_app_icon
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import com.haooz.softglass.shape.G2Continuity
import org.jetbrains.compose.resources.imageResource
import platform.posix.exit

/**
 * `SettingsScreen` 的 **iOS 宿主包装** —— 对应 Android 的 `AndroidSettingsScreen`。
 *
 * 设置页改成「分类主页 + 分类二级页」后，原来给平板右栏用的 8 个内嵌面板（宿主 pane 槽位）
 * 已全部删除，改走已路由化的页面；这里只剩平台能力注入：
 * - 手机端的「数据管理 / 备份与同步」二级页经路由参数拿文件能力，不经过本文件；
 * - 平板右栏内嵌的「提醒 / 小组件 / 关于 / 捐赠」需要 [SettingsDirectPlatform]（见下），
 *   「数据管理 / 备份与同步」内嵌则需要 `dataManage` / `backupFileController` ——
 *   ⚠ 漏传会静默落到 Noop：点了没反应，且不报错。
 */
@Composable
internal fun IosSettingsScreen(
    args: SettingsPaneArgs,
    scheduleFileController: ScheduleFileController,
    backupFileController: BackupFileController,
    reminderController: ReminderController,
    onHolidayDataChanged: () -> Unit,
) {
    val settingsViewModel = args.settingsViewModel
    val islandNotification by settingsViewModel.islandNotification.collectAsState()
    SettingsScreen(
        viewModel = args.viewModel,
        scheduleViewModel = args.scheduleViewModel,
        settingsViewModel = settingsViewModel,
        shiftViewModel = args.shiftViewModel,
        isShiftMode = args.isShiftMode,
        onExitShiftMode = args.onExitShiftMode,
        onEnterShiftMode = args.onEnterShiftMode,
        navBarStyle = args.navBarStyle,
        settingsScrollBehavior = args.settingsScrollBehavior,
        liquidGlassBackdrop = args.liquidGlassBackdrop,
        showUpdateSettings = args.showUpdateSettings,
        // 平板右栏内嵌「备份与同步 / 数据管理」→ 注入 iOS 文件与导入能力
        backupFileController = backupFileController,
        dataManage = DataManagePlatform(
            scheduleFileController = scheduleFileController,
            onFetchShareCode = { code -> ShareCodeImport.fetchShareCode(code) },
            onImportThirdParty = { source, code -> ShareCodeImport.importThirdParty(source, code) },
        ),
        // 全量恢复会写节假日数据 → 重排提醒
        onHolidayDataChanged = onHolidayDataChanged,
        // 平板右栏内嵌「提醒 / 小组件 / 关于」→ 注入 iOS 侧能力
        directPlatform = SettingsDirectPlatform(
            reminderController = reminderController,
            // 与 AppRoute.CourseReminder 的原生页同一套实现与文案
            onSendTestNotification = {
                if (islandNotification) {
                    IosCourseLiveActivity.startTest()
                    showToast(SettingsStr.islandTestStarted())
                } else {
                    IosCourseNotifier.sendTestNotification()
                    showToast(SettingsStr.testNotificationSent())
                }
            },
            testActionLabel = if (islandNotification) {
                SettingsStr.testDynamicIsland()
            } else {
                ChromeStr.testNotification()
            },
            onRequestWidgetRefresh = { IosWidgetSnapshot.refresh() },
            aboutVersion = AppInfo.version,
            aboutIcon = imageResource(Res.drawable.about_app_icon),
            appIconShape = ContinuousRoundedRectangle(20.dp, continuity = G2Continuity()),
            // 「撤回同意」确认后退出应用：与 IosAppEntry 的 AboutRoute 保持同一语义
            onExitApp = { exit(0) },
        ),
    )
}
