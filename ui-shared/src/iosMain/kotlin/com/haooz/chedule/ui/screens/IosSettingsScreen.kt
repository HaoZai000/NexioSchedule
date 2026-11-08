package com.haooz.chedule.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.IosCourseLiveActivity
import com.haooz.chedule.data.IosCourseNotifier
import com.haooz.chedule.data.IosWidgetSnapshot
import com.haooz.chedule.data.backup.BackupFileController
import com.haooz.chedule.data.backup.ScheduleFileController
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.platform.AppInfo
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.ReminderController
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.L
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AiImportScreen
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.CourseReminderScreen
import com.haooz.chedule.ui.activities.LocalBackupScreen
import com.haooz.chedule.ui.activities.PreferenceSettingsScreen
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.activities.main.SettingsPaneArgs
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.utils.ShareCodeImport
import com.haooz.chedule.ui.utils.rememberIosShareAction
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.about_app_icon
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import com.haooz.softglass.shape.G2Continuity
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.imageResource
import platform.Foundation.NSBundle
import platform.UIKit.UIPasteboard

/**
 * `SettingsScreen` 的 **iOS 宿主包装** —— 对应 Android 的 `AndroidSettingsScreen`。
 *
 * 只做一件事：把平板（rail）分支 8 个平台专有 dest 的接线、「测试通知 / 刷新小部件 /
 * 节假日拉取」几个平台副作用装配好，经注入参数交给共享页；注入实现与
 * [com.haooz.chedule.NexioIosRouteContent] 的原生文档页复用同一批控制器
 * （UIDocumentPicker / 本地通知 / 灵动岛 / NSURLSession）。
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
    // 分享课表（上传生成口令 → 系统分享面板）：与主界面同一个 iOS 实现
    val shareSchedule = rememberIosShareAction()
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
        androidPane = { dest ->
            when (dest) {
                TabletSettingsDest.Reminder -> CourseReminderScreen(
                    settingsViewModel = settingsViewModel,
                    scrollBehavior = null,
                    liquidGlassBackdrop = args.liquidGlassBackdrop,
                    // iOS 提醒能力（本地通知 / 灵动岛 / 权限）
                    reminderController = reminderController,
                )

                TabletSettingsDest.ScheduleImport -> BackupAndMigrationScreen(
                    courseViewModel = args.viewModel,
                    scheduleViewModel = args.scheduleViewModel,
                    settingsViewModel = settingsViewModel,
                    scrollBehavior = null,
                    liquidGlassBackdrop = args.liquidGlassBackdrop,
                    mode = ScheduleDataManageMode.Import,
                    // 平板右栏：去掉「导入方式」，口令/文件拆成两个小标题
                    compactImport = true,
                    // UIDocumentPicker 选文件（全 app 共用一个实例）
                    scheduleFileController = scheduleFileController,
                    onHolidayDataChanged = onHolidayDataChanged,
                    onFetchShareCode = { code -> ShareCodeImport.fetchShareCode(code) },
                    onImportThirdParty = { source, code -> ShareCodeImport.importThirdParty(source, code) },
                    onShareSchedule = { name, changed -> shareSchedule(name, changed) },
                )

                TabletSettingsDest.AiImport -> AiImportScreen(
                    onBack = {},
                    scrollBehavior = null,
                    viewModel = args.viewModel,
                    settingsViewModel = settingsViewModel,
                    liquidGlassBackdrop = args.liquidGlassBackdrop,
                    // 剪贴板是平台能力，宿主注入给已迁 :ui-shared 的页面
                    onCopyToClipboard = { text ->
                        UIPasteboard.generalPasteboard.string = text
                    },
                )

                TabletSettingsDest.ScheduleExport -> BackupAndMigrationScreen(
                    courseViewModel = args.viewModel,
                    scheduleViewModel = args.scheduleViewModel,
                    settingsViewModel = settingsViewModel,
                    scrollBehavior = null,
                    liquidGlassBackdrop = args.liquidGlassBackdrop,
                    mode = ScheduleDataManageMode.Export,
                    scheduleFileController = scheduleFileController,
                    onHolidayDataChanged = onHolidayDataChanged,
                    onFetchShareCode = { code -> ShareCodeImport.fetchShareCode(code) },
                    onImportThirdParty = { source, code -> ShareCodeImport.importThirdParty(source, code) },
                    onShareSchedule = { name, changed -> shareSchedule(name, changed) },
                )

                TabletSettingsDest.LocalBackup -> LocalBackupScreen(
                    scrollBehavior = null,
                    liquidGlassBackdrop = args.liquidGlassBackdrop,
                    // Documents 目录扫描 + UIDocumentPicker 选文件
                    backupFileController = backupFileController,
                    onHolidayDataChanged = onHolidayDataChanged,
                )

                TabletSettingsDest.Preference -> PreferenceSettingsScreen(
                    scrollBehavior = null,
                    liquidGlassBackdrop = args.liquidGlassBackdrop,
                    // 定位权限 / 天气缓存 / 最近任务卡片都是 Android 专有；iOS 走默认值
                )

                TabletSettingsDest.Update -> Unit

                TabletSettingsDest.About -> {
                    // 关于页自绘内嵌顶栏：屏幕玻璃层独立，弹窗采样外层全屏层（左栏一起虚化）
                    val aboutBackdrop = rememberLayerBackdrop()
                    val appRouter = LocalAppRouter.current
                    AboutScreen(
                        onBack = {},
                        liquidGlassBackdrop = aboutBackdrop,
                        embedded = true,
                        dialogBackdrop = args.liquidGlassBackdrop,
                        // 子页（更新日志 / 开源协议 / 隐私政策）已路由化，交给宿主 router
                        onNavigate = { route: AppRoute -> appRouter.navigate(route) },
                        appVersion = AppInfo.version,
                        appName = NSBundle.mainBundle
                            .objectForInfoDictionaryKey("CFBundleDisplayName") as? String
                            ?: L("Nexio课程表", "Nexio Schedule")(),
                        appIcon = imageResource(Res.drawable.about_app_icon),
                        appIconShape = ContinuousRoundedRectangle(20.dp, continuity = G2Continuity()),
                        // 撤回隐私同意即退出：iOS 没有 finishAffinity，与隐私弹窗拒绝同一手段
                        onExitApp = { platform.posix.exit(0) },
                    )
                }

                else -> Unit
            }
        },
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
