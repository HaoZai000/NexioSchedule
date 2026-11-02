package com.haooz.chedule.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.haooz.chedule.calendar.rememberCalendarSyncAction
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.AboutStr
import com.haooz.chedule.i18n.AndroidMiscStr
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AiImportScreen
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.CourseReminderScreen
import com.haooz.chedule.ui.activities.LocalBackupScreen
import com.haooz.chedule.ui.activities.MainActivity
import com.haooz.chedule.ui.activities.PreferenceSettingsScreen
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.activities.UpdateSettingsScreen
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.utils.AndroidBackupFileController
import com.haooz.chedule.ui.utils.AndroidDiagnosticRecorder
import com.haooz.chedule.ui.utils.AndroidReminderController
import com.haooz.chedule.ui.utils.AndroidScheduleFileController
import com.haooz.chedule.ui.utils.AndroidUpdateController
import com.haooz.chedule.ui.utils.ShareCodeApi
import com.haooz.chedule.ui.utils.ShareCodeFetchResult
import com.haooz.chedule.ui.utils.ThirdPartyShareImporter
import com.haooz.chedule.ui.utils.performScheduleShare
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.haooz.chedule.wearable.WearableScheduleSync
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
        showUpdateSettings = args.showUpdateSettings,
    )
}

 /**
 * 本文件只做一件事：把平板分支那 8 个平台专有 dest 的**接线**
 * （以及测试通知 / 小部件刷新 / 节假日拉取这几个平台副作用）装配好，
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
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    /** 是否显示「更新设置」入口（Android 恒 true；iOS 走 App Store 传 false） */
    showUpdateSettings: Boolean = true,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
        androidPane = { dest ->
            // 同步到日历：缺权限时点一下就地申请（导出面板用，launcher 只注册这一个）
            val syncToCalendar = rememberCalendarSyncAction()
            when (dest) {
                TabletSettingsDest.Reminder -> CourseReminderScreen(
                    settingsViewModel = settingsViewModel,
                    scrollBehavior = null,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    // Android 提醒能力（闹钟/电池/权限/设置页跳转）注入
                    reminderController = remember { AndroidReminderController(context) },
                )

                TabletSettingsDest.ScheduleImport -> BackupAndMigrationScreen(
                    courseViewModel = viewModel,
                    scheduleViewModel = scheduleViewModel,
                    settingsViewModel = settingsViewModel,
                    scrollBehavior = null,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    mode = ScheduleDataManageMode.Import,
                    compactImport = true,
                    // Android 专有能力注入（同 AppRouteContent 的 BackupAndMigrationRoute）
                    scheduleFileController = remember { AndroidScheduleFileController(context) },
                    onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
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
                            scope = coroutineScope,
                            scheduleName = name,
                            onSharingChanged = onSharingChanged,
                        )
                    },
                    onExportToWearable = { name ->
                        WearableScheduleSync.exportToWearable(context, name)
                    },
                )

                TabletSettingsDest.AiImport -> AiImportScreen(
                    onBack = {},
                    scrollBehavior = null,
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    // 剪贴板是 Android 专有能力，宿主注入给已迁 :ui-shared 的页面
                    onCopyToClipboard = { text ->
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(AndroidMiscStr.aiImportClipLabel(), text)
                        clipboard.setPrimaryClip(clip)
                    },
                )

                TabletSettingsDest.ScheduleExport -> BackupAndMigrationScreen(
                    courseViewModel = viewModel,
                    scheduleViewModel = scheduleViewModel,
                    settingsViewModel = settingsViewModel,
                    scrollBehavior = null,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    mode = ScheduleDataManageMode.Export,
                    scheduleFileController = remember { AndroidScheduleFileController(context) },
                    onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },                    onFetchShareCode = { code ->
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
                            scope = coroutineScope,
                            scheduleName = name,
                            onSharingChanged = onSharingChanged,
                        )
                    },
                    onExportToWearable = { name ->
                        WearableScheduleSync.exportToWearable(context, name)
                    },
                    onSyncToCalendar = syncToCalendar,
                )

                TabletSettingsDest.LocalBackup -> LocalBackupScreen(
                    scrollBehavior = null,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    // Android 文件 I/O（SAF + 备份目录）注入
                    backupFileController = remember { AndroidBackupFileController(context) },
                    onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
                )

                TabletSettingsDest.Preference -> PreferenceSettingsScreen(
                    scrollBehavior = null,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    // Android 宿主专有副作用：定位权限 / 天气缓存 / 最近任务卡片
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

                TabletSettingsDest.Update -> UpdateSettingsScreen(
                    scrollBehavior = null,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    // Android 更新能力（检查/下载/APK 安装）注入
                    updateController = remember { AndroidUpdateController(context) },
                )

                TabletSettingsDest.About -> {
                    val aboutBackdrop =
                        com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                    // LocalAppRouter.current 是 @Composable 调用，必须在组合层取
                    val appRouter = LocalAppRouter.current
                    // 宿主能力注入：版本号 / 应用名 / 图标（drawable→Bitmap→裁透明边→ImageBitmap）/ 退出 / 诊断录制
                    // （裁边逻辑原在 AboutScreen.cropBitmapToOpaque，随迁移到 ui-shared 已删除，宿主在此复刻）
                    val aboutActivity = LocalActivity.current
                    val aboutPackageInfo = remember {
                        runCatching {
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        }.getOrNull()
                    }
                    val aboutIcon = remember {
                        runCatching { context.packageManager.getApplicationIcon(context.packageName) }
                            .getOrNull()
                            ?.let { drawable ->
                                val size = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight, 1)
                                val bitmap = androidx.core.graphics.createBitmap(size, size)
                                drawable.setBounds(0, 0, size, size)
                                drawable.draw(android.graphics.Canvas(bitmap))
                                val pixels = IntArray(size * size)
                                bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
                                var minX = size
                                var minY = size
                                var maxX = -1
                                var maxY = -1
                                for (y in 0 until size) {
                                    for (x in 0 until size) {
                                        if (pixels[y * size + x] ushr 24 != 0) {
                                            if (x < minX) minX = x
                                            if (x > maxX) maxX = x
                                            if (y < minY) minY = y
                                            if (y > maxY) maxY = y
                                        }
                                    }
                                }
                                val cropped = if (maxX >= minX && maxY >= minY &&
                                    (maxX - minX + 1 != size || maxY - minY + 1 != size)
                                ) {
                                    android.graphics.Bitmap.createBitmap(
                                        bitmap, minX, minY, maxX - minX + 1, maxY - minY + 1
                                    )
                                } else {
                                    bitmap
                                }
                                cropped.asImageBitmap()
                            }
                    }
                    AboutScreen(
                        onBack = {},
                        liquidGlassBackdrop = aboutBackdrop,
                        embedded = true,
                        // 弹窗采样全屏层，才能把左栏内容一起虚化
                        dialogBackdrop = liquidGlassBackdrop,
                        // 平板设置页还没路由化（它在 MainActivity 里），
                        // 所以这三个子页仍以「开新宿主 + 指定初始路由」的方式打开 ——
                        // 行为与改造前一致：全屏打开，返回回到这里。
                        onNavigate = { route ->
                            appRouter.navigate(route)
                        },
                        appVersion = remember(AppLanguage.resolved) { aboutPackageInfo?.versionName ?: AboutStr.unknownVersion() },
                        appName = remember(AppLanguage.resolved) {
                            aboutPackageInfo?.applicationInfo?.labelRes?.let { context.getString(it) }
                                ?: context.applicationInfo?.nonLocalizedLabel?.toString()
                                ?: AboutStr.appName()
                        },
                        appIcon = aboutIcon,
                        onExitApp = { aboutActivity?.finishAffinity() },
                        diagnosticRecorder = remember { AndroidDiagnosticRecorder(context) },
                    )
                }

                else -> {}
            }
        },
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
