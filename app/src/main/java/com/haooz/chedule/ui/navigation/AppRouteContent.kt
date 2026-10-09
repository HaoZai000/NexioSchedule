package com.haooz.chedule.ui.navigation

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.data.todayLocalDate
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AiImportScreen
import com.haooz.chedule.ui.activities.AppreciateAuthorScreen
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.ChangelogScreen
import com.haooz.chedule.ui.activities.CommunicationScreen
import com.haooz.chedule.ui.activities.CourseReminderScreen
import com.haooz.chedule.ui.activities.HolidaySettingsScreen
import com.haooz.chedule.ui.activities.LicenseScreen
import com.haooz.chedule.ui.activities.LocalBackupScreen
import com.haooz.chedule.ui.activities.PreferenceSettingsScreen
import com.haooz.chedule.ui.activities.PrivacyPolicyScreen
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.activities.UpdateSettingsScreen
import com.haooz.chedule.ui.activities.WebDavSettingsScreen
import com.haooz.chedule.ui.activities.WidgetIntroScreen
import com.haooz.chedule.ui.basic.LiquidGlassTextButton
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.HttpURLConnection
import java.net.URL

/**
 * 子页路由 → 页面的映射表。
 *
 * 这是**唯一**需要知道「哪个路由对应哪个 Screen」的地方 —— 宿主只负责创建
 * [AppRouter]、渲染 [AppNavHost]，不关心具体有哪些页面。
 *
 * 这样拆的用意：阶段 5 把 UI 搬进共享模块时，**这个文件整体搬过去**即可，
 * 宿主（Android Activity / iOS UIViewController）不用跟着改。
 *
 * ## 本批已迁移 vs 仍是 Activity
 *
 * 已迁移（9 条路由）：About / Changelog / License / PrivacyPolicy /
 * PreferenceSettings / UpdateSettings / Communication / LocalBackup / AppreciateAuthor
 *
 * 仍是 Activity（各有 Activity 级状态或平台依赖，下一批处理）：
 * HolidaySettings（URL 拉取）/ WidgetIntro（弹窗+按钮）/ CourseReminder（超级岛）/
 * WebDavSettings（状态回调）/ BackupAndMigration（3 个 ViewModel + mode）/
 * CourseManage（571 行业务逻辑）/ CourseTimeSettings / SwitchSchedule（1928 行）/
 * EducationalImport（WebView）/ AiImport
 */
@Composable
fun AppRouteContent(
    router: AppRouter,
    route: AppRoute,
) {
    when (route) {
        AppRoute.About -> AboutRoute(router)

        AppRoute.Changelog -> DocumentPageScaffold(
            title = "更新日志",
            onBack = { router.popBack() },
        ) { scrollBehavior, _ ->
            ChangelogScreen(scrollBehavior = scrollBehavior)
        }

        AppRoute.License -> DocumentPageScaffold(
            title = "开源协议",
            onBack = { router.popBack() },
        ) { scrollBehavior, _ ->
            LicenseScreen(scrollBehavior = scrollBehavior)
        }

        AppRoute.PrivacyPolicy -> DocumentPageScaffold(
            title = "隐私政策",
            onBack = { router.popBack() },
        ) { scrollBehavior, _ ->
            PrivacyPolicyScreen(scrollBehavior = scrollBehavior)
        }

        AppRoute.PreferenceSettings -> DocumentPageScaffold(
            title = "应用偏好设置",
            onBack = { router.popBack() },
        ) { scrollBehavior, backdrop ->
            // 偏好设置页可能改主题 → 系统栏要跟着刷新（原来在 Activity 的 setContent 里）
            val activity = LocalActivity.current
            val isDark = isAppDarkTheme()
            LaunchedEffect(isDark) {
                activity?.applyThemeAwareSystemBars()
            }
            PreferenceSettingsScreen(
                scrollBehavior = scrollBehavior,
                liquidGlassBackdrop = backdrop,
            )
        }

        AppRoute.UpdateSettings -> DocumentPageScaffold(
            title = "更新设置",
            onBack = { router.popBack() },
        ) { scrollBehavior, backdrop ->
            UpdateSettingsScreen(
                scrollBehavior = scrollBehavior,
                liquidGlassBackdrop = backdrop,
            )
        }

        AppRoute.Communication -> DocumentPageScaffold(
            title = "交流与反馈",
            onBack = { router.popBack() },
        ) { scrollBehavior, _ ->
            CommunicationScreen(scrollBehavior = scrollBehavior)
        }

        AppRoute.LocalBackup -> DocumentPageScaffold(
            title = "本地备份",
            onBack = { router.popBack() },
        ) { scrollBehavior, backdrop ->
            LocalBackupScreen(
                scrollBehavior = scrollBehavior,
                liquidGlassBackdrop = backdrop,
            )
        }

        AppRoute.AppreciateAuthor -> DocumentPageScaffold(
            title = "捐赠支持",
            onBack = { router.popBack() },
        ) { scrollBehavior, _ ->
            AppreciateAuthorScreen(scrollBehavior = scrollBehavior)
        }

        // ── 数据管理（导入 / 导出 / 备份，共用 BackupAndMigrationScreen，按 mode 区分）──
        AppRoute.ScheduleImport -> BackupAndMigrationRoute(router, ScheduleDataManageMode.Import)
        AppRoute.ScheduleExport -> BackupAndMigrationRoute(router, ScheduleDataManageMode.Export)
        AppRoute.ScheduleBackup -> BackupAndMigrationRoute(router, ScheduleDataManageMode.Backup)

        // ── 带额外元素的页面（顶栏按钮 / 底部悬浮按钮 / 弹窗）──
        AppRoute.AiImport -> AiImportRoute(router)
        AppRoute.WidgetIntro -> WidgetIntroRoute(router)
        AppRoute.CourseReminder -> CourseReminderRoute(router)
        AppRoute.HolidaySettings -> HolidaySettingsRoute(router)
        AppRoute.WebDavSettings -> WebDavSettingsRoute(router)
    }
}

/**
 * AI 文本导入（原 AiImportActivity，98 行）。
 *
 * Screen 的两个 backdrop 参数都有默认值，这里只传 [DocumentPageScaffold] 建好的玻璃层即可。
 */
@Composable
private fun AiImportRoute(router: AppRouter) {
    DocumentPageScaffold(
        title = "AI 文本导入",
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        AiImportScreen(
            onBack = { router.popBack() },
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
        )
    }
}

/** 桌面小部件引导（原 WidgetIntroActivity，168 行，含底部按钮 + 引导弹窗）。 */
@Composable
private fun WidgetIntroRoute(router: AppRouter) {
    val hapticFeedback = LocalHapticFeedback.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp
    var showGuideDialog by remember { mutableStateOf(false) }

    DocumentPageScaffold(
        title = "桌面小部件",
        onBack = { router.popBack() },
        overlay = { backdrop ->
            LiquidGlassTextButton(
                text = "添加到桌面",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    showGuideDialog = true
                },
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = tabletHorizontalPadding + 16.dp,
                        end = tabletHorizontalPadding + 16.dp
                    )
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            )
            OverlayDialog(
                title = "添加桌面小部件",
                show = showGuideDialog,
                liquidGlassBackdrop = backdrop,
                onDismissRequest = { showGuideDialog = false }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "1. 长按桌面空白处\n2. 选择「全部应用」内的「安卓小部件」\n3. 找到「Nexio课程表」并添加",
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    TextButton(
                        text = "我知道了",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showGuideDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
    ) { scrollBehavior, backdrop ->
        WidgetIntroScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
        )
    }
}

/** 课程提醒（原 CourseReminderActivity，155 行，含底部「测试通知」按钮）。 */
@Composable
private fun CourseReminderRoute(router: AppRouter) {
    val context = LocalContext.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    DocumentPageScaffold(
        title = "课程提醒",
        onBack = { router.popBack() },
        overlay = { backdrop ->
            // 是否支持超级岛是设备静态能力，缓存一次即可；开关值必须跟随 ViewModel，
            // 否则 remember 无 key 会把初次求值的结果钉死，切换开关后按钮文案不会更新
            val settingsViewModel: SettingsViewModel = viewModel()
            val islandNotification by settingsViewModel.islandNotification.collectAsState()
            val islandSupported = remember { IslandNotificationHelper.isIslandSupported(context) }
            val islandEnabled = islandNotification && islandSupported
            LiquidGlassTextButton(
                text = if (islandEnabled) "测试小米超级岛" else "测试实时活动",
                onClick = {
                    com.haooz.chedule.ui.utils.FeatureLog.reminderFlow(
                        "test_notification",
                        if (islandEnabled) "island" else "live"
                    )
                    if (islandEnabled) {
                        IslandNotificationHelper.sendTestIslandNotification(context)
                        com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("test_island_sent")
                        Toast.makeText(context, "已发送超级岛测试通知", Toast.LENGTH_SHORT).show()
                    } else {
                        CourseReminderHelper.sendTestLiveNotification(context)
                        com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("test_live_sent")
                        Toast.makeText(context, "已发送实时活动测试通知", Toast.LENGTH_SHORT).show()
                    }
                },
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = tabletHorizontalPadding + 16.dp,
                        end = tabletHorizontalPadding + 16.dp
                    )
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            )
        },
    ) { scrollBehavior, backdrop ->
        CourseReminderScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
        )
    }
}

/** 节假日与调休（原 HolidaySettingsActivity，194 行，含网络拉取）。 */
@Composable
private fun HolidaySettingsRoute(router: AppRouter) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentDate = remember { todayLocalDate() }
    var year by remember { mutableIntStateOf(currentDate.year) }
    var entries by remember { mutableStateOf(HolidayManager.load(year)) }
    var loading by remember { mutableStateOf(false) }

    fun reload() {
        entries = HolidayManager.load(year)
    }

    fun requestYear(targetYear: Int) {
        if (loading) return
        loading = true
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val conn = URL(HolidayManager.sourceUrlFor(targetYear))
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                HolidayManager.parseSourceResponse(text)
            }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                val merged = HolidayManager.mergeApiEntries(targetYear, result)
                if (targetYear == year) reload()
                loading = false
                if (merged && result.isNotEmpty()) {
                    // API 合并同样要重排提醒并刷小部件，不能只改本地 SP
                    CourseReminderHelper.onHolidayDataChanged(context)
                }
            }
        }
    }

    DocumentPageScaffold(
        title = "节假日与调休",
        onBack = { router.popBack() },
        endAction = { backdropAlpha, shadowAlpha ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .offset(x = (-6).dp, y = (-4).dp)
                        .size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        progress = null,
                    )
                }
            } else {
                LiquidTopBarButton(
                    onClick = { requestYear(year) },
                    backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(),
                    icon = MiuixIcons.Normal.Update,
                    contentDescription = "更新",
                    iconSize = 28.dp,
                    backdropAlpha = backdropAlpha,
                    shadowAlpha = shadowAlpha,
                )
            }
        },
    ) { scrollBehavior, backdrop ->
        HolidaySettingsScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            year = year,
            entries = entries,
            onYearChange = { newYear ->
                year = newYear
                reload()
            },
            reload = { reload() },
        )
    }
}

/** WebDAV 云备份设置（原 WebDavSettingsActivity，170 行，含顶栏「测试连接」）。 */
@Composable
private fun WebDavSettingsRoute(router: AppRouter) {
    var connected by remember { mutableStateOf(false) }
    var onTestConnection by remember { mutableStateOf({}) }
    var backingUp by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var onBackup by remember { mutableStateOf({}) }
    var onRestore by remember { mutableStateOf({}) }

    DocumentPageScaffold(
        title = "WebDAV 云备份",
        onBack = { router.popBack() },
        endAction = { backdropAlpha, shadowAlpha ->
            LiquidTopBarButton(
                onClick = { onTestConnection() },
                backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(),
                icon = if (connected) MiuixIcons.Ok else MiuixIcons.Play,
                contentDescription = if (connected) "已连接" else "测试连接",
                backdropAlpha = backdropAlpha,
                shadowAlpha = shadowAlpha,
                iconOffset = if (!connected) DpOffset(x = 2.dp, y = 0.dp) else DpOffset.Zero,
                iconTint = if (connected) Color(0xFF4CAF50) else Color.Unspecified,
            )
        },
    ) { scrollBehavior, backdrop ->
        WebDavSettingsScreen(
            scrollBehavior = scrollBehavior,
            onConnectedChange = { connected = it },
            onTestConnectionReady = { onTestConnection = it },
            onBackupRestoreReady = { backup, restore ->
                onBackup = backup
                onRestore = restore
            },
            onBusyStateChange = { b, r ->
                backingUp = b
                restoring = r
            },
        )
    }
}

/**
 * 课表导入 / 导出 / 备份。
 *
 * ⚠ 这三个入口**必须**走路由，不能再是 Activity：原来 `BackupAndMigrationActivity`
 * 是独立 Activity，`BackupAndMigrationScreen` 里的「本地备份」入口读 [LocalAppRouter]，
 * 而那个 Activity 的 setContent 没有 provide，默认值是 `error(...)` —— 一点开就崩。
 */
@Composable
private fun BackupAndMigrationRoute(router: AppRouter, mode: ScheduleDataManageMode) {
    val title = when (mode) {
        ScheduleDataManageMode.Import -> "课表导入"
        ScheduleDataManageMode.Export -> "课表导出"
        ScheduleDataManageMode.Backup -> "课表备份"
    }
    DocumentPageScaffold(
        title = title,
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        // 与原 BackupAndMigrationActivity 一致：ViewModel 由宿主创建后传入
        val courseViewModel: CourseViewModel = viewModel()
        val scheduleViewModel: ScheduleViewModel = viewModel()
        val settingsViewModel: SettingsViewModel = viewModel()
        BackupAndMigrationScreen(
            scrollBehavior = scrollBehavior,
            courseViewModel = courseViewModel,
            scheduleViewModel = scheduleViewModel,
            settingsViewModel = settingsViewModel,
            liquidGlassBackdrop = backdrop,
            mode = mode,
        )
    }
}

/** 关于页（原来内联在 AboutActivity 的 1200 行，已抽到 `AboutScreen.kt`）。 */
@Composable
private fun AboutRoute(router: AppRouter) {
    AboutScreen(
        onBack = { router.popBack() },
        liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(),
        onNavigate = { router.navigate(it) },
    )
}
