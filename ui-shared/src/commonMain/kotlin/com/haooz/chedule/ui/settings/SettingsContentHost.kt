package com.haooz.chedule.ui.settings

import androidx.compose.runtime.Stable
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel

/**
 * 设置二级页的上下文包：把 4 个 ViewModel + 平台注入能力 + 路由回调打包，
 * 避免 [SettingsCategoryList] 与所有行控件出现十几个参数。
 *
 * 手机分类页与平板右栏用的是**同一个**实例类型 —— 这是两端共享一份实现的契约。
 */
@Stable
class SettingsContentHost(
    val courseViewModel: CourseViewModel,
    val scheduleViewModel: ScheduleViewModel,
    val settingsViewModel: SettingsViewModel,
    val shiftViewModel: ShiftViewModel,
    val isShiftMode: Boolean,
    val liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop?,
    /** 是否已授予定位权限（Android 宿主注入；桌面/iOS 恒 false） */
    val hasLocationPermission: () -> Boolean = { false },
    /** 清空天气缓存（Android 宿主注入；桌面/iOS 空实现） */
    val onInvalidateWeatherCache: () -> Unit = {},
    /** 隐藏最近任务卡片（Android 专有能力；宿主注入） */
    val onSetTaskExcludedFromRecents: (Boolean) -> Unit = {},
    /** 更新能力控制器（Android 注入 `AndroidUpdateController`；桌面/iOS 用 Noop）——「更新」二级页内嵌用 */
    val updateController: com.haooz.chedule.data.platform.UpdateController =
        com.haooz.chedule.data.platform.NoopUpdateController,
    /** 备份文件能力（Android=SAF，iOS=UIDocumentPicker）——「备份与同步」二级页内嵌本地备份用 */
    val backupFileController: com.haooz.chedule.data.backup.BackupFileController =
        com.haooz.chedule.data.backup.NoopBackupFileController,
    /** 导入/导出平台能力包（Android 注入 SAF/口令/日历/手环等；其余平台 Noop）——「数据管理」二级页内嵌用 */
    val dataManage: DataManagePlatform = DataManagePlatform(),
    /** 直达型分类（提醒 / 小组件 / 关于 / 捐赠）在平板右栏内嵌时需要的平台能力 */
    val directPlatform: SettingsDirectPlatform = SettingsDirectPlatform(),
    /** 全量恢复成功后的平台副作用（重排提醒），由宿主注入 */
    val onHolidayDataChanged: () -> Unit = {},
    /** 进出排班模式由主界面执行转场，这里只投递请求 */
    val onExitShiftMode: () -> Unit = {},
    val onEnterShiftMode: () -> Unit = {},
    val onNavigate: (AppRoute) -> Unit,
)

/**
 * 「数据管理」二级页整页内联导入/导出所需的平台能力。
 * Android 注入 SAF 选/存文件、分享口令、第三方口令抓取、口令分享、手环导出、日历同步；
 * 其余平台全部走默认 Noop（点按只提示「平台不支持」）。
 */
@Stable
class DataManagePlatform(
    /** 导入选文件 / 导出另存为（Android=SAF） */
    val scheduleFileController: com.haooz.chedule.data.backup.ScheduleFileController =
        com.haooz.chedule.data.backup.NoopScheduleFileController,
    /** 分享口令取回（Android = OkHttp + Gson） */
    val onFetchShareCode: suspend (code: String) -> Result<com.haooz.chedule.ui.utils.ShareCodeFetchResult> =
        { Result.failure(IllegalStateException("unsupported")) },
    /** 第三方（WakeUp / 星链）口令抓取（Android 专有） */
    val onImportThirdParty: suspend (
        source: com.haooz.chedule.ui.utils.ThirdPartyShareSource,
        code: String,
    ) -> Result<com.haooz.chedule.ui.utils.ThirdPartySharePayload> =
        { source, _ -> Result.failure(IllegalStateException(source.displayName)) },
    /** 口令分享导出（上传 + 出图 + 系统分享，Android 专有） */
    val onShareSchedule: (scheduleName: String, onSharingChanged: (Boolean) -> Unit) -> Unit = { _, _ -> },
    /** 导出到手环（Android 专有；未连接/不支持时返回 null） */
    val onExportToWearable: suspend (scheduleName: String) -> String? = { null },
    /** 同步到系统日历（Android 专有；未实现平台保持 Unsupported） */
    val onSyncToCalendar: suspend (scheduleName: String) -> com.haooz.chedule.ui.activities.CalendarSyncResult =
        { com.haooz.chedule.ui.activities.CalendarSyncResult.Unsupported },
)

/**
 * 直达型分类（提醒 / 小组件 / 关于 / 捐赠）在**平板右栏内嵌**时需要的平台能力。
 *
 * 手机端这四个分类仍然是「点一下就压路由」的全屏页面，不需要本包；
 * 平板右栏不能压全屏页，否则「分栏」体验就断了，所以把页面直接内嵌进右栏，
 * 平台能力（提醒控制器 / 小部件刷新 / 关于页版本与图标 / 退出 / 诊断录制）由宿主注入。
 */
@Stable
class SettingsDirectPlatform(
    /** 提醒能力（Android=AndroidReminderController，iOS=原生提醒控制器） */
    val reminderController: com.haooz.chedule.data.platform.ReminderController =
        com.haooz.chedule.data.platform.NoopReminderController,
    /** 提醒页底部「测试通知」动作；null = 不渲染该按钮（iOS/桌面） */
    val onSendTestNotification: (() -> Unit)? = null,
    /** 测试按钮文案（Android 按设备能力在「测试小米超级岛 / 测试实时活动」间切换） */
    val testActionLabel: String = com.haooz.chedule.i18n.ChromeStr.testNotification(),
    /** 刷新已装小部件（Android=WidgetUpdateCache，iOS=IosWidgetSnapshot） */
    val onRequestWidgetRefresh: () -> Unit = {},
    /** 关于页：版本号（宿主从 packageManager / Info.plist 取） */
    val aboutVersion: String = com.haooz.chedule.i18n.AboutStr.unknownVersion(),
    /** 关于页：应用名 */
    val aboutName: String = com.haooz.chedule.i18n.AboutStr.appName(),
    /** 关于页：应用图标（已裁边的 ImageBitmap）；null 时回退为首字母文本 */
    val aboutIcon: androidx.compose.ui.graphics.ImageBitmap? = null,
    /** 关于页：图标形状（iOS 用连续圆角） */
    val appIconShape: androidx.compose.ui.graphics.Shape =
        androidx.compose.ui.graphics.RectangleShape,
    /** 撤回隐私同意后退出应用（Android=finishAffinity，iOS=exit(0)） */
    val onExitApp: () -> Unit = {},
    /** 诊断录制控制器（Android=CrashLogHelper 包装） */
    val diagnosticRecorder: com.haooz.chedule.data.platform.DiagnosticRecorder =
        com.haooz.chedule.data.platform.NoopDiagnosticRecorder,
)
