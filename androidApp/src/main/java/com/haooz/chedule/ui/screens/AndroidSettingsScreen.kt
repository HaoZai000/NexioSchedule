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
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.haooz.chedule.widget.WidgetUpdateCache
import java.net.HttpURLConnection
import java.net.URL
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior

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

/**
 * 本文件只做一件事：把测试通知 / 小部件刷新 / 节假日拉取这几个平台副作用装配好，
 * 通过注入参数交给共享页。
 */
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
    searchState: com.haooz.chedule.ui.screens.SettingsSearchState? = null,
    /** 是否显示「更新」入口（Android 恒 true；iOS 走 App Store 传 false） */
    showUpdateSettings: Boolean = true,
) {
    val context = LocalContext.current
    // 提醒页「测试通知」按钮文案需要超级岛开关（原共享页内联逻辑随迁回宿主）
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
        // 提醒页底部「测试通知」：原平板页覆盖层 onClick（FeatureLog + 超级岛/实时活动 + toast）逐字迁入
        onSendTestNotification = {
            val islandEnabled = islandNotification &&
                IslandNotificationHelper.isIslandSupported(context)
            com.haooz.chedule.ui.utils.FeatureLog.reminderFlow(
                "test_notification",
                if (islandEnabled) "island" else "live"
            )
            if (islandEnabled) {
                IslandNotificationHelper.sendTestIslandNotification(
                    context
                )
                com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("test_island_sent")
                showToast(AndroidMiscStr.islandTestSent())
            } else {
                CourseReminderHelper.sendTestLiveNotification(
                    context
                )
                com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("test_live_sent")
                showToast(AndroidMiscStr.liveActivityTestSent())
            }
        },
        isIslandSupported = { IslandNotificationHelper.isIslandSupported(context) },
        onRefreshWidgets = {
            WidgetUpdateCache.clear()
            WidgetUpdateCache.updateInstalledWidgets(context)
        },
        onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
        // 节假日 API 拉取：原平板页内联 HttpURLConnection（10s 超时）+ parseSourceResponse 逐字迁入
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
