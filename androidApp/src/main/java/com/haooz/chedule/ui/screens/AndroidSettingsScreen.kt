@file:Suppress("UNUSED_PARAMETER")

package com.haooz.chedule.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.LocalActivity
import androidx.core.content.ContextCompat
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.AndroidMiscStr
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.activities.MainActivity
import com.haooz.chedule.ui.activities.main.SettingsPaneArgs
import com.haooz.chedule.calendar.rememberCalendarSyncAction
import com.haooz.chedule.ui.settings.DataManagePlatform
import com.haooz.chedule.ui.settings.SettingsDirectPlatform
import com.haooz.chedule.ui.utils.AndroidBackupFileController
import com.haooz.chedule.ui.utils.AndroidDiagnosticRecorder
import com.haooz.chedule.ui.utils.AndroidReminderController
import com.haooz.chedule.ui.utils.AndroidScheduleFileController
import com.haooz.chedule.ui.utils.AndroidUpdateController
import com.haooz.chedule.ui.utils.ShareCodeApi
import com.haooz.chedule.ui.utils.ShareCodeFetchResult
import com.haooz.chedule.ui.utils.ThirdPartyShareImporter
import com.haooz.chedule.ui.utils.performScheduleShare
import com.haooz.chedule.ui.utils.rememberAndroidAboutInfo
import com.haooz.chedule.wearable.WearableScheduleSync
import com.haooz.chedule.widget.WidgetUpdateCache
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior

/**
 * [SettingsPaneArgs] → [AndroidSettingsScreen] 的装配入口。
 */
@Composable
fun AndroidSettingsPane(args: SettingsPaneArgs) {
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
        showUpdateSettings = args.showUpdateSettings,
    )
}

/**
 * Android 宿主包装。
 *
 * 设置页改成「分类主页 + 分类二级页」后，原来给平板右栏用的 8 个内嵌面板（宿主 pane 槽位）
 * 已全部删除，改走已路由化的页面；这里只剩平台能力装配：定位权限 / 天气缓存 / 最近任务卡片 /
 * 更新控制器 / SAF 文件能力 / 导入导出能力包 / 直达型分类能力。
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
    /** 是否显示「更新设置」入口（Android 恒 true；iOS 走 App Store 传 false） */
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
        navBarStyle = navBarStyle,
        settingsScrollBehavior = settingsScrollBehavior,
        liquidGlassBackdrop = liquidGlassBackdrop,
        showUpdateSettings = showUpdateSettings,
        // 平板「更新」二级页内嵌更新设置/日志 → 注入 Android 更新能力
        updateController = remember { AndroidUpdateController(context) },
        // 平板「备份与同步」二级页内嵌本地备份 → 注入 SAF 文件能力与恢复副作用
        backupFileController = remember { AndroidBackupFileController(context) },
        // 平板「数据管理」二级页内嵌导入/导出 → 注入 SAF/口令/日历/手环能力
        dataManage = run {
            val shareScope = rememberCoroutineScope()
            val calendarSyncAction = rememberCalendarSyncAction()
            remember(calendarSyncAction) {
                DataManagePlatform(
                    scheduleFileController = AndroidScheduleFileController(context),
                    onFetchShareCode = { code ->
                        ShareCodeApi.fetchShare(code).map {
                            ShareCodeFetchResult(
                                code = it.code,
                                scheduleName = it.scheduleName,
                                scheduleData = it.scheduleData,
                            )
                        }
                    },
                    onImportThirdParty = { source, code ->
                        ThirdPartyShareImporter.import(context, source, code)
                    },
                    onShareSchedule = { name, onSharingChanged ->
                        performScheduleShare(
                            context = context,
                            scope = shareScope,
                            scheduleName = name,
                            onSharingChanged = onSharingChanged,
                        )
                    },
                    onExportToWearable = { name -> WearableScheduleSync.exportToWearable(context, name) },
                    onSyncToCalendar = calendarSyncAction,
                )
            }
        },
        // 平板右栏内嵌「提醒 / 小组件 / 关于」→ 注入各自的 Android 能力
        directPlatform = run {
            val aboutInfo = rememberAndroidAboutInfo()
            val activity = LocalActivity.current
            val diagnosticRecorder = remember { AndroidDiagnosticRecorder(context) }
            val islandEnabled = islandNotification && IslandNotificationHelper.isIslandSupported(context)
            remember(aboutInfo, activity, diagnosticRecorder, islandEnabled) {
                SettingsDirectPlatform(
                    reminderController = AndroidReminderController(context),
                    onSendTestNotification = {
                        com.haooz.chedule.ui.utils.FeatureLog.reminderFlow(
                            "test_notification",
                            if (islandEnabled) "island" else "live",
                        )
                        if (islandEnabled) {
                            IslandNotificationHelper.sendTestIslandNotification(context)
                            com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("test_island_sent")
                            showToast(AndroidMiscStr.islandTestSent())
                        } else {
                            CourseReminderHelper.sendTestLiveNotification(context)
                            com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("test_live_sent")
                            showToast(AndroidMiscStr.liveActivityTestSent())
                        }
                    },
                    testActionLabel = if (islandEnabled) {
                        SettingsStr.testXiaomiSuperIsland()
                    } else {
                        SettingsStr.testLiveActivity()
                    },
                    onRequestWidgetRefresh = {
                        WidgetUpdateCache.clear()
                        WidgetUpdateCache.updateInstalledWidgets(context)
                    },
                    aboutVersion = aboutInfo.version,
                    aboutName = aboutInfo.name,
                    aboutIcon = aboutInfo.icon,
                    onExitApp = { activity?.finishAffinity() },
                    diagnosticRecorder = diagnosticRecorder,
                )
            }
        },
        onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
        hasLocationPermission = {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        },
        onInvalidateWeatherCache = { invalidateWeatherCache() },
        onSetTaskExcludedFromRecents = { hidden ->
            MainActivity.setTaskExcludedFromRecents(context, hidden)
        },
    )
}
