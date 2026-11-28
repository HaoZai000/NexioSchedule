package com.haooz.chedule.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.AndroidMiscStr
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.activities.setting.SettingsScreen
import com.haooz.chedule.ui.activities.setting.SettingsSearchState
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.haooz.chedule.widget.WidgetUpdateCache
import java.net.HttpURLConnection
import java.net.URL
import com.haooz.nexioui.topappbar.SharedScrollBehavior

/**
 * [SettingsPaneArgs] → [AndroidSettingsScreen] 的装配入口
 */
@Composable
fun AndroidSettingsPane(args: com.haooz.chedule.ui.activities.main.SettingsPaneArgs) {
    AndroidSettingsScreen(
        viewModel = args.viewModel,
        scheduleViewModel = args.scheduleViewModel,
        settingsViewModel = args.settingsViewModel,
        shiftViewModel = args.shiftViewModel,
        isShiftMode = args.isShiftMode,
        onExitShiftMode = args.onExitShiftMode,
        onEnterShiftMode = args.onEnterShiftMode,
        navBarStyle = args.navBarStyle,
        settingsScrollBehavior = args.settingsScrollBehavior,
        liquidGlassBackdrop = args.liquidGlassBackdrop,
        searchState = args.searchState,
        showUpdateSettings = args.showUpdateSettings,
    )
}

/** 为设置页提供通知测试、小部件刷新和节假日数据等 Android 能力。 */
@Composable
fun AndroidSettingsScreen(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    shiftViewModel: ShiftViewModel,
    isShiftMode: Boolean = false,
    onExitShiftMode: () -> Unit = {},
    onEnterShiftMode: () -> Unit = {},
    navBarStyle: String = "standard",
    settingsScrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    /** 搜索状态（由路由持有并挂在页面采样层外） */
    searchState: SettingsSearchState? = null,
    /** 是否显示「更新」入口（Android 恒 true；iOS 走 App Store 传 false） */
    showUpdateSettings: Boolean = true,
) {
    val context = LocalContext.current
    val islandNotification by settingsViewModel.islandNotification.collectAsState()

    SettingsScreen(
        viewModel = viewModel,
        scheduleViewModel = scheduleViewModel,
        settingsViewModel = settingsViewModel,
        shiftViewModel = shiftViewModel,
        isShiftMode = isShiftMode,
        onExitShiftMode = onExitShiftMode,
        onEnterShiftMode = onEnterShiftMode,
        showUpdateSettings = showUpdateSettings,
        navBarStyle = navBarStyle,
        settingsScrollBehavior = settingsScrollBehavior,
        liquidGlassBackdrop = liquidGlassBackdrop,
        searchState = searchState,
        onSendTestNotification = {
            val islandEnabled = islandNotification &&
                    IslandNotificationHelper.isIslandSupported(context)
            FeatureLog.reminderFlow(
                "test_notification",
                if (islandEnabled) "island" else "live"
            )
            if (islandEnabled) {
                IslandNotificationHelper.sendTestIslandNotification(
                    context
                )
                FeatureLog.reminderFlow("test_island_sent")
                showToast(AndroidMiscStr.islandTestSent())
            } else {
                CourseReminderHelper.sendTestLiveNotification(
                    context
                )
                FeatureLog.reminderFlow("test_live_sent")
                showToast(AndroidMiscStr.liveActivityTestSent())
            }
        },
        isIslandSupported = { IslandNotificationHelper.isIslandSupported(context) },
        onRefreshWidgets = {
            WidgetUpdateCache.clear()
            WidgetUpdateCache.updateInstalledWidgets(context)
        },
        onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
        fetchHolidayEntries = { year ->
            val conn = URL(
                HolidayManager.sourceUrlFor(year)
            ).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            HolidayManager.parseSourceResponse(text)
        },
    )
}
