package com.haooz.chedule.ui.navigation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.calendar.rememberCalendarSyncAction
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.AboutStr
import com.haooz.chedule.i18n.AndroidMiscStr
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.activities.MainActivity
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.activities.decodeWallpaperSampled
import com.haooz.chedule.ui.screens.AndroidSettingsPane
import com.haooz.chedule.ui.screens.invalidateWeatherCache
import com.haooz.chedule.ui.utils.AndroidBackupFileController
import com.haooz.chedule.ui.utils.AndroidDiagnosticRecorder
import com.haooz.chedule.ui.utils.AndroidReminderController
import com.haooz.chedule.ui.utils.AndroidScheduleFileController
import com.haooz.chedule.ui.utils.AndroidUpdateController
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.ShareCodeApi
import com.haooz.chedule.ui.utils.ShareCodeFetchResult
import com.haooz.chedule.ui.utils.ThirdPartyShareImporter
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.performScheduleShare
import com.haooz.chedule.ui.web.rememberAndroidWebHost
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.wearable.WearableScheduleSync
import com.haooz.chedule.wearable.rememberWatchSyncController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 子页路由 → 页面的映射表。
 *
 * 这是**唯一**需要知道「哪个路由对应哪个 Screen」的地方 —— 宿主只负责创建
 * [AppRouter]、渲染 [AppNavHost]，不关心具体有哪些页面。
 *
 * 本文件留在 androidApp：页面壳多已迁到 `:ui-shared`，这里只剩映射与 Android 能力装配。
 * 两模块**共享包名**，靠「无同名顶层符号」维持，新增页面时先确认另一侧没有同名文件。
 */
@Composable
fun AppRouteContent(
    router: AppRouter,
    route: AppRoute,
) {
    when (route) {
        // 设置二级页（原主 pager 第三页）
        AppRoute.Settings -> SettingsRoute(
            router = router,
            settingsPane = { args -> AndroidSettingsPane(args) },
        )

        AppRoute.ScheduleSettings -> ScheduleSettingsRoute(router)

        AppRoute.About -> AndroidAboutRoute(router)

        // 纯文档壳路由，直接调跨平台版
        AppRoute.Changelog -> ChangelogRoute(router)
        AppRoute.Weather -> WeatherRoute(router)
        AppRoute.License -> LicenseRoute(router)
        AppRoute.PrivacyPolicy -> PrivacyPolicyRoute(router)
        AppRoute.Communication -> CommunicationRoute(router)
        AppRoute.Feedback -> FeedbackRoute(router)
        AppRoute.FeedbackHistory -> FeedbackHistoryRoute(router)
        AppRoute.FeedbackRoadmap -> FeedbackRoadmapRoute(router)
        AppRoute.AppreciateAuthor -> AppreciateAuthorRoute(router)

        // 文档壳路由，Android 注入平台能力。
        // ⚠ context/activity 必须在组合期取好再捕获 —— `LocalContext.current` 是
        // @Composable getter，写进非组合 lambda 会编不过。
        AppRoute.PreferenceSettings -> {
            val context = LocalContext.current
            val activity = LocalActivity.current
            PreferenceSettingsRoute(
                router = router,
                hasLocationPermission = {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                },
                onInvalidateWeatherCache = { invalidateWeatherCache() },
                onSetTaskExcludedFromRecents = { hidden ->
                    MainActivity.setTaskExcludedFromRecents(context, hidden)
                },
                // 偏好设置页改主题 → 系统栏跟着刷新（原来在 Activity 的 setContent 里）
                onThemeDarkChanged = { activity?.applyThemeAwareSystemBars() },
            )
        }

        AppRoute.UpdateSettings -> {
            val context = LocalContext.current
            UpdateSettingsRoute(
                router = router,
                updateController = remember { AndroidUpdateController(context) },
            )
        }

        AppRoute.UpdatePreferences -> UpdatePreferencesRoute(router = router)

        AppRoute.ScheduleBackup -> {
            val context = LocalContext.current
            BackupCenterRoute(
                router = router,
                backupFileController = remember { AndroidBackupFileController(context) },
                onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
            )
        }

        AppRoute.AiImport -> {
            val context = LocalContext.current
            AiImportRoute(
                router = router,
                onCopyToClipboard = { text ->
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(AndroidMiscStr.aiImportClipLabel(), text)
                    clipboard.setPrimaryClip(clip)
                },
            )
        }

        // ── 导入导出 / 备份与同步 ──
        // 导入 / 导出同页，顶部胶囊切换
        AppRoute.ImportExport -> AndroidBackupAndMigrationRoute(
            router,
            ScheduleDataManageMode.Import,
            switchable = true,
        )

        // ── 带额外元素的页面（顶栏按钮 / 底部悬浮按钮 / 弹窗）──
        // Android 只注入平台能力。
        AppRoute.WidgetIntro -> {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            // 组件壁纸：相册选图 → 按「小部件实际像素尺寸」降采样解码 → 回填浮层
            var pendingPick by remember { mutableStateOf<((ImageBitmap?) -> Unit)?>(null) }
            var pickTargetW by remember { mutableIntStateOf(0) }
            var pickTargetH by remember { mutableIntStateOf(0) }
            var pickDecoding by remember { mutableStateOf(false) }
            val widgetWallpaperPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                if (uri == null) {
                    showToast(AndroidMiscStr.noImageSelected())
                    return@rememberLauncherForActivityResult
                }
                if (pickDecoding) {
                    showToast(AndroidMiscStr.processingWallpaperWait())
                    return@rememberLauncherForActivityResult
                }
                pickDecoding = true
                scope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        decodeWallpaperSampled(
                            context = context,
                            uri = uri,
                            screenWPx = pickTargetW.toFloat(),
                            screenHPx = pickTargetH.toFloat(),
                        )
                    }
                    pickDecoding = false
                    if (bitmap == null) {
                        showToast(AndroidMiscStr.wallpaperDecodeFailed(), longDuration = true)
                        pendingPick?.invoke(null)
                    } else {
                        pendingPick?.invoke(bitmap.asImageBitmap())
                    }
                }
            }
            WidgetIntroRoute(
                router = router,
                onRequestWidgetRefresh = {
                    com.haooz.chedule.widget.WidgetUpdateCache.clear()
                    com.haooz.chedule.widget.WidgetUpdateCache.updateInstalledWidgets(context)
                },
                onPickWidgetWallpaper = { widthPx, heightPx, onPicked ->
                    pickTargetW = widthPx
                    pickTargetH = heightPx
                    pendingPick = onPicked
                    widgetWallpaperPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
        }
        AppRoute.CourseReminder -> {
            val context = LocalContext.current
            // 开关值必须跟随 ViewModel，否则 remember 无 key 会把初次求值的结果钉死，
            // 切换开关后按钮文案不会更新；是否支持超级岛是设备静态能力，缓存一次即可
            val settingsViewModel: SettingsViewModel = viewModel()
            val islandNotification by settingsViewModel.islandNotification.collectAsState()
            val islandSupported = remember { IslandNotificationHelper.isIslandSupported(context) }
            val islandEnabled = islandNotification && islandSupported
            CourseReminderRoute(
                router = router,
                reminderController = remember { AndroidReminderController(context) },
                testActionLabel = if (islandEnabled) SettingsStr.testXiaomiSuperIsland() else SettingsStr.testLiveActivity(),
                onSendTestNotification = {
                    FeatureLog.reminderFlow(
                        "test_notification",
                        if (islandEnabled) "island" else "live"
                    )
                    if (islandEnabled) {
                        IslandNotificationHelper.sendTestIslandNotification(context)
                        FeatureLog.reminderFlow("test_island_sent")
                        showToast(AndroidMiscStr.islandTestSent())
                    } else {
                        CourseReminderHelper.sendTestLiveNotification(context)
                        FeatureLog.reminderFlow("test_live_sent")
                        showToast(AndroidMiscStr.liveActivityTestSent())
                    }
                },
            )
        }
        AppRoute.WebDavSettings -> {
            val context = LocalContext.current
            WebDavSettingsRoute(
                router = router,
                backupFileController = remember { AndroidBackupFileController(context) },
                onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
            )
        }
        AppRoute.HolidaySettings -> {
            val context = LocalContext.current
            // 节假日数据变更是 Android 专有的平台副作用（提醒 / 勿扰 / 小组件），由宿主注入
            HolidaySettingsRoute(
                router = router,
                onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
            )
        }
        // Android 只注入自由小窗判定
        AppRoute.CourseTimeSettings -> CourseTimeSettingsRoute(
            router = router,
            isInFreeformWindow = isInFreeformWindow(),
        )
        AppRoute.CourseManage -> CourseManageRoute(
            router = router,
            isInFreeformWindow = isInFreeformWindow(),
        )
        AppRoute.EducationalImport -> AndroidEducationalImportRoute(router)
        AppRoute.WebImport -> AndroidWebImportRoute(router)
    }
}

/**
 * 课表导入 / 导出的 **Android 包装**：文件选择器 / 提醒重排 / 口令取回与抓取 /
 * 口令分享出图 / 手环推送。
 */
@Composable
private fun AndroidBackupAndMigrationRoute(
    router: AppRouter,
    mode: ScheduleDataManageMode,
    switchable: Boolean = false,
) {
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    BackupAndMigrationRoute(
        router = router,
        mode = mode,
        switchableModes = if (switchable) {
            listOf(ScheduleDataManageMode.Import, ScheduleDataManageMode.Export)
        } else emptyList(),
        // 导入选文件 / 导出另存为（SAF）
        scheduleFileController = remember { AndroidScheduleFileController(context) },
        // WebDAV 恢复后的提醒重排
        onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
        // 分享口令取回（OkHttp + Gson）
        onFetchShareCode = { code ->
            ShareCodeApi.fetchShare(code).map {
                ShareCodeFetchResult(
                    code = it.code,
                    scheduleName = it.scheduleName,
                    scheduleData = it.scheduleData,
                )
            }
        },
        // 第三方口令抓取（无头 WebView + OkHttp + Gson）
        onImportThirdParty = { source, code ->
            ThirdPartyShareImporter.import(context, source, code)
        },
        // 口令分享（上传 + 出图 + 系统分享）
        onShareSchedule = { name, onSharingChanged ->
            performScheduleShare(
                context = context,
                scope = shareScope,
                scheduleName = name,
                onSharingChanged = onSharingChanged,
            )
        },
        // 手环推送
        onExportToWearable = { name -> WearableScheduleSync.exportToWearable(context, name) },
        // 手表蓝牙推送（扫描 + 分片写入）
        watchSyncController = rememberWatchSyncController(),
        // 同步到日历（缺权限时点一下就申请）
        onSyncToCalendar = rememberCalendarSyncAction(),
    )
}

/**
 * 是否处于自由小窗 / 分屏。
 *
 * 原来由每个 Activity 自己的 `onMultiWindowModeChanged` 维护（该回调 API 35 起废弃且无等价替代）。
 * 单宿主之后子页拿不到自己的 Activity 回调，统一读**宿主** [MainActivity] 的 Compose 状态 ——
 * 它由宿主的同一个回调驱动，语义不变。
 */
@Composable
private fun isInFreeformWindow(): Boolean {
    val activity = LocalActivity.current as? MainActivity ?: return false
    return activity.isInFreeformWindow
}

/**
 * 关于页（原来内联在 AboutActivity 的 1200 行，已抽到 `:ui-shared` commonMain 的 `AboutScreen.kt`）。
 *
 * 平台专有的一半留在宿主：版本号 / 应用名 / **裁边后的应用图标**（`packageManager` +
 * AdaptiveIcon 透明边裁剪，Compose 没有跨平台等价物）/ `finishAffinity` / 诊断录制器。
 * 其余（路由跳转、子页面导航）走 `:ui-shared` 的 [com.haooz.chedule.ui.navigation.AboutRoute]。
 */
@Composable
private fun AndroidAboutRoute(router: AppRouter) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    // 应用图标：drawable → Bitmap → 裁掉 AdaptiveIcon 四周透明边 → ImageBitmap
    // （裁边逻辑原在 AboutScreen.cropBitmapToOpaque，随迁移到 ui-shared 已删除，宿主在此复刻）
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
                    android.graphics.Bitmap.createBitmap(bitmap, minX, minY, maxX - minX + 1, maxY - minY + 1)
                } else {
                    bitmap
                }
                cropped.asImageBitmap()
            }
    }
    com.haooz.chedule.ui.navigation.AboutRoute(
        router = router,
        appVersion = remember(AppLanguage.resolved) { packageInfo?.versionName ?: AboutStr.unknownVersion() },
        appName = remember(AppLanguage.resolved) {
            packageInfo?.applicationInfo?.labelRes?.let { context.getString(it) }
                ?: context.applicationInfo?.nonLocalizedLabel?.toString()
                ?: AboutStr.appName()
        },
        appIcon = aboutIcon,
        onExitApp = { activity?.finishAffinity() },
        diagnosticRecorder = remember { AndroidDiagnosticRecorder(context) },
    )
}

/**
 * 教务导入（学校 / 适配器选择，原 `EducationalImportActivity` 并入单 Activity 路由）。
 *
 * 进入路由即 `startUpdateIfNeeded()`（原 `onCreate` 时机）；自由窗口判定经
 * [isInFreeformWindow] 读宿主 MainActivity；系统栏 / configChanges 由 Manifest 覆盖。
 */
@Composable
private fun AndroidEducationalImportRoute(router: AppRouter) {
    com.haooz.chedule.ui.navigation.EducationalImportRoute(
        router = router,
        isInFreeformWindow = isInFreeformWindow(),
    )
}

/** 网页导入：教务导入的第二步，独立一层（返回先回学校选择页）。 */
@Composable
private fun AndroidWebImportRoute(router: AppRouter) {
    com.haooz.chedule.ui.navigation.WebImportRoute(
        router = router,
        webHostFactory = { config, events -> rememberAndroidWebHost(config, events) },
    )
}
