package com.haooz.chedule.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.haooz.chedule.data.IosCourseLiveActivity
import com.haooz.chedule.data.IosCourseNotifier
import com.haooz.chedule.data.IosWidgetSnapshot
import com.haooz.chedule.data.backup.BackupFileController
import com.haooz.chedule.data.backup.ScheduleFileController
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.ReminderController
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.L
import com.haooz.chedule.ui.activities.main.SettingsPaneArgs
import kotlinx.coroutines.withContext

/**
 * `SettingsScreen` 的 **iOS 宿主包装** —— 对应 Android 的 `AndroidSettingsScreen`。
 *
 * 只做一件事：把「测试通知 / 刷新小部件 / 节假日拉取」几个平台副作用装配好，
 * 经注入参数交给共享页；注入实现与 [com.haooz.chedule.NexioIosRouteContent] 的原生文档页
 * 复用同一批控制器（UIDocumentPicker / 本地通知 / 灵动岛 / NSURLSession）。
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
    // 节假日 API：与 HolidaySettingsRoute 同一条共享 HttpService（iOS = NSURLSession）
    val holidayHttp = remember {
        createHttpService(HttpTimeouts(connectSeconds = 10, readSeconds = 10))
    }

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
        // 文案对齐原生路由页（AppRoute.CourseReminder）：iOS 没有小米超级岛
        testActionLabel = if (islandNotification) L("测试灵动岛", "Test Dynamic Island")() else L("测试通知", "Test Notification")(),
        // 提醒页底部「测试通知」：对齐 AppRoute.CourseReminder 的原生页实现
        onSendTestNotification = {
            if (islandNotification) {
                IosCourseLiveActivity.startTest()
                showToast(L("已启动灵动岛测试", "Dynamic Island test started")())
            } else {
                IosCourseNotifier.sendTestNotification()
                showToast(L("已发送测试通知", "Test notification sent")())
            }
        },
        // iOS 的「超级岛」= Live Activity（26.1 起恒可用），与 CourseReminderScreen 同判据
        isIslandSupported = { reminderController.supportsLiveActivity() },
        onRefreshWidgets = { IosWidgetSnapshot.refresh() },
        onHolidayDataChanged = onHolidayDataChanged,
        fetchHolidayEntries = { year ->
            runCatching {
                val response = withContext(ioDispatcher) {
                    holidayHttp.get(HolidayManager.sourceUrlFor(year))
                }
                HolidayManager.parseSourceResponse(response.text)
            }.getOrDefault(emptyList())
        },
    )
}
