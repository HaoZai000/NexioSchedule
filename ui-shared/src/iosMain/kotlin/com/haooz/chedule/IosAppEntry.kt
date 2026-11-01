package com.haooz.chedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.IosBackupFileController
import com.haooz.chedule.data.IosCourseLiveActivity
import com.haooz.chedule.data.IosCourseNotifier
import com.haooz.chedule.data.IosScheduleFileController
import com.haooz.chedule.data.storage.IosSqlStorage
import com.haooz.chedule.data.IosWallpaperBackend
import com.haooz.chedule.data.IosWallpaperPicker
import com.haooz.chedule.data.IosWidgetSnapshot
import com.haooz.chedule.data.platform.NsFileAppFile
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.platform.defaultDocumentsDir
import com.haooz.chedule.data.platform.AppFiles
import com.haooz.chedule.data.platform.AppInfo
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.PrivacyConsent
import com.haooz.chedule.data.platform.ScheduleChangeSink
import com.haooz.chedule.data.platform.ScheduleEffects
import com.haooz.chedule.data.platform.StatsReporter
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.CourseStorage
import com.haooz.chedule.data.platform.toByteArray
import com.haooz.chedule.i18n.L
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.activities.main.CourseScheduleApp
import com.haooz.chedule.ui.activities.today.TodayAssistantCard
import com.haooz.chedule.ui.navigation.AboutRoute
import com.haooz.chedule.ui.navigation.AiImportRoute
import com.haooz.chedule.ui.navigation.AppNavHost
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.navigation.AppreciateAuthorRoute
import com.haooz.chedule.ui.navigation.BackupAndMigrationRoute
import com.haooz.chedule.ui.navigation.ChangelogRoute
import com.haooz.chedule.ui.navigation.CommunicationRoute
import com.haooz.chedule.ui.navigation.CourseManageRoute
import com.haooz.chedule.ui.navigation.CourseReminderRoute
import com.haooz.chedule.ui.navigation.CourseTimeSettingsRoute
import com.haooz.chedule.ui.navigation.EducationalImportRoute
import com.haooz.chedule.ui.navigation.HolidaySettingsRoute
import com.haooz.chedule.ui.navigation.LicenseRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.LocalBackupRoute
import com.haooz.chedule.ui.navigation.PreferenceSettingsRoute
import com.haooz.chedule.ui.navigation.PrivacyPolicyRoute
import com.haooz.chedule.ui.navigation.SettingsRoute
import com.haooz.chedule.ui.navigation.UpdateSettingsRoute
import com.haooz.chedule.ui.navigation.WebDavSettingsRoute
import com.haooz.chedule.ui.navigation.WebImportRoute
import com.haooz.chedule.ui.navigation.WidgetIntroRoute
import com.haooz.chedule.ui.screens.PrivacyConsentScreen
import com.haooz.chedule.ui.screens.rememberWeatherIos
import com.haooz.chedule.ui.utils.IosReminderController
import com.haooz.chedule.ui.utils.IosWakeUpImport
import com.haooz.chedule.ui.utils.ShareCodeImport
import com.haooz.chedule.ui.utils.IosSharedFileReceiver
import com.haooz.chedule.ui.utils.IosSystemBars
import com.haooz.chedule.ui.utils.IosToast
import com.haooz.chedule.ui.utils.rememberIosShareAction
import com.haooz.chedule.ui.web.rememberIosWebHost
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.about_app_icon
import com.haooz.chedule.viewmodel.settingsViewModel
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.capsule.continuities.G2Continuity
import org.jetbrains.compose.resources.imageResource
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIPasteboard

/**
 * iOS 启动注入 —— 对应 Android 的 `NexioApplication.onCreate()` 开头。
 *
 * ⚠ **必须在 Swift 侧 `@main` 的 `init()` 里调用，且早于任何 Compose 界面出现**。
 *
 * ⚠ **函数名别以 `init` 开头**：Kotlin/Native 导出 ObjC 时，`initXxx` 会被当成构造器，
 * Swift 侧 `IosAppEntryKt.` 下**找不到**该方法（已实测：其他方法都在，唯独它 `has no member`）。
 * 缺任何一个初始化都会首次使用时抛异常（AppInfo→app_version 丢失、AppStorage/AppFiles 首次调用即抛）。
 *
 * Also injects the toast host, the wallpaper backend and the schedule-change sink.
 */
fun bootstrapNexioIos() {
    val version = NSBundle.mainBundle
        .objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "unknown"
    AppInfo.init(version)

    // WakeUp 导入要跑无头 WKWebView，实现是 iOS 专有的，注入给共享的 ShareCodeImport
    ShareCodeImport.wakeUpRunner = { shareKey, script -> IosWakeUpImport.run(shareKey, script) }

    // 与 Android 同一个 nexio.db：键值表 + 课程行表同库同事务。
    // ⚠ 不能退回 UserDefaults：两端 schema 分叉会让备份/恢复只在一端正确。
    AppStorage.init { name -> IosSqlStorage.store(name) }
    // 课程行表接线。取不到就抛，不退回 JSON。
    CourseStorage.install { IosSqlStorage.courseRows() }
    // iOS 从第一天就是 nexio.db：没有 blob / UserDefaults 存量可搬，直接记为已迁移。
    // Android 那边由 LegacyStorageMigration 调 migrateLegacyEntitiesOnce 真正搬数据。
    runCatching { IosSqlStorage.courseRows().markFullyMigrated() }

    // 语言镜像尽早就位：core 数据类的显示变体（DisplayLang）依赖它；与 CourseScheduleApp 里的恢复同值
    com.haooz.chedule.i18n.AppLanguage.init(com.haooz.chedule.i18n.LanguageStore.load())

    AppFiles.init(
        root = NsFileAppFile(defaultDocumentsDir()),
        readAsset = { path ->
            // 内置学校索引引导用；失败需**抛**，由 SchoolRepository 捕获日志并跳过引导。
            val resolved = NSBundle.mainBundle.pathForResource(path, null)
                ?: error(L("内置资源缺失：%s", "Missing built-in resource: %s")(path))
            NSData.dataWithContentsOfFile(resolved)?.toByteArray()
                ?: error(L("内置资源读取失败：%s", "Failed to read built-in resource: %s")(path))
        },
    )

    // 壁纸后端：iOS 用 UIImage（JPEG）+ 共享 AppFiles 落盘；与 Android 同一条接口+注入链路。
    val wallpaperBackend = IosWallpaperBackend()
    WallpaperStore.backend = wallpaperBackend
    // 首帧壁纸同步解码（skia 本身同步）：首帧就要带上用户壁纸，等协程会先画一帧底色再跳。
    WallpaperStore.synchronousLoader = { wallpaperBackend.loadNow() }

    // 原生提示条：把 showToast 接到 UIKit HUD；不注入则 native actual 只打日志，用户「点了没反应」。
    IosToast.install()

    // 课程提醒：不注入此 sink 则副作用被静默丢弃（表现为「加了课但提醒不更新」）。
    // 须在修改课表前初始化——首次导入与切换课表都会触发重排。
    ScheduleEffects.init(IosScheduleChangeSink)
}

/** iOS schedule effects: native reminders and shared WidgetKit snapshots. */
private object IosScheduleChangeSink : ScheduleChangeSink {
    override fun rescheduleReminders() = IosCourseNotifier.reschedule()

    override fun syncWearable(reason: String) {
        NexioLog.d("IosScheduleChangeSink", "syncWearable($reason) ignored: no wearable on iOS")
    }

    override fun refreshWidgets() = IosWidgetSnapshot.refresh()
}

/**
 * 文件选择/另存实现，全 app 共用一个。
 *
 * ⚠ 必须全局单例：delegate 是弱引用、靠自身字段强引用；挂在顶层 val 上以免 picker 弹出后实例被回收。
 */
private val fileController = IosScheduleFileController()

/** 相册选壁纸实现，全 app 共用一个（delegate 强引用同上） */
private val wallpaperPicker = IosWallpaperPicker()

/** 本地备份的文件读写（Documents/Neixo_Schedule 扫描 + UIDocumentPicker 选文件） */
private val backupFileController = IosBackupFileController()

/**
 * Course reminder controller: local notice scheduling plus the Live Activity island.
 *
 * Process-wide singleton, same lifetime as the other four controllers. It only holds
 * references to stateless objects, so nothing needs releasing when a page goes away.
 */
private val reminderController = IosReminderController()

/**
 * 节假日数据变更（拉取/备份恢复/云同步恢复）的平台副作用：重排提醒。
 * 不注入则待发通知按旧数据排。
 */
private val onHolidayDataChanged: () -> Unit = {
    IosCourseNotifier.reschedule()
    IosWidgetSnapshot.refresh()
}

/** The retained main content; UIKit applies the shared main-layer transition. */
@Composable
internal fun NexioIosMain(host: IosAppHost) {
    val router = host.router
    // 合规：对齐 Android MainActivity —— 启动时同步同意状态，未同意前不触网（云同步/公告/统计）
    PrivacyConsent.refresh()
    val consented by PrivacyConsent.consented.collectAsState()
    // 统计上报：已同意的回访用户，冷启动即上报（active + 一次性 install），对齐 Android onCreate
    LaunchedEffect(Unit) {
        if (PrivacyConsent.hasConsented()) {
            StatsReporter.reportActive()
            StatsReporter.reportInstallOnce()
        }
    }
    // 注入自检：iOS 的 AppStorage/UserDefaults 与 AppFiles/NSFileManager 是新写的，
    // 编译通过 ≠ 落盘与还原类型正确（Int/Long 不分是雷区）。降级为启动日志仍是唯一运行时证据，别删。
    LaunchedEffect(Unit) {
        runCatching {
            val store = AppStorage.store("ios_smoke_test")
            store.edit { putString("k", "v") }
            store.edit { putInt("i", 42) }
            val ok = store.getString("k", "") == "v" && store.getInt("i", 0) == 42
            NexioLog.i("IosBootstrap", "AppStorage/UserDefaults 自检：${if (ok) "OK" else "值不一致"}")
        }.onFailure { NexioLog.e("IosBootstrap", "AppStorage 自检失败：${it.message}", it) }

        runCatching {
            val file = AppFiles.root.resolve("ios_smoke_test").resolve("probe.txt")
            file.writeBytes("课程表".encodeToByteArray())
            val ok = file.readText() == "课程表"
            NexioLog.i("IosBootstrap", "AppFiles/NSFileManager 自检：${if (ok) "OK" else "内容不一致"}")
        }.onFailure { NexioLog.e("IosBootstrap", "AppFiles 自检失败：${it.message}", it) }
    }
    CompositionLocalProvider(LocalAppRouter provides router) {
        Box(Modifier.fillMaxSize()) {
            // 主界面常驻底座（真课表界面）；未接的宿主能力走默认「无此能力」，入口不渲染而非留死按钮。
                CourseScheduleApp(
                    privacyConsented = consented,
                    onSystemBarsChanged = { statusDark, _ ->
                        IosSystemBars.update(statusDark)
                    },
                    // 节假日数据变更 → 重排提醒
                    onHolidayDataChanged = onHolidayDataChanged,
                    // 相册选壁纸：PHPicker→UIImage（系统已按 EXIF 校正）→降采样到屏幕像素→skia 解码。
                    // 降采样必须在 iOS 侧完成，共享层直接原样用。
                    onPickWallpaperImage = { onPicked -> wallpaperPicker.pick(onPicked) },
                    // 分享课表：上传生成口令 → 复制剪贴板 → 模板图绘口令 → 系统分享面板
                    onShareSchedule = rememberIosShareAction(),
                    // 今日助手卡：卡片本体在共享层，iOS 只提供天气+定位（CoreLocation）
                    todayAssistantCard = { d ->
                        val (weather, hasLocationPermission, requestLocation) = rememberWeatherIos()
                        TodayAssistantCard(
                            weather = weather,
                            hasLocationPermission = hasLocationPermission,
                            onRequestLocation = requestLocation,
                            courses = d.courses,
                            tomorrowCourses = d.tomorrowCourses,
                            sectionTimes = d.sectionTimes,
                            morningSections = d.morningSections,
                            afternoonSections = d.afternoonSections,
                            showClassroom = d.showClassroom,
                            showTeacher = d.showTeacher,
                            wallpaperBackdrop = d.wallpaperBackdrop,
                            blurRadius = d.blurRadius,
                            surfaceOpacity = d.surfaceOpacity,
                            // 下课烟花是 Android 专有（LocalContext/nanoTime），iOS 不渲染
                            fireworksOverlay = null,
                        )
                    },
                    // 外部分享进来的文件（Swift 侧 .onOpenURL → IosSharedFileReceiver）
                    sharedFileIntent = IosSharedFileReceiver.pending,
                    onShareIntentConsumed = { IosSharedFileReceiver.clear() },
                    onReadSharedFileText = { uri -> IosSharedFileReceiver.readText(uri) },
                    // iOS 走 App Store 更新 → 设置页的「更新设置」入口整个不渲染
                    showUpdateSettings = false,
                    // 平板设置两栏的 8 个平台专有 dest 装配（对齐 Android 的 AndroidSettingsScreen）
                    settingsPane = { args ->
                        com.haooz.chedule.ui.screens.IosSettingsScreen(
                            args = args,
                            scheduleFileController = fileController,
                            backupFileController = backupFileController,
                            reminderController = reminderController,
                            onHolidayDataChanged = onHolidayDataChanged,
                        )
                    },
                )
        }
    }
}

/** Draws routes above both the main content and the native tab bar. */
@Composable
internal fun NexioIosNavigation(host: IosAppHost) {
    val router = host.router
    val mainTransition = host.mainTransition
    val consented by PrivacyConsent.consented.collectAsState()
    // 与 Android MainActivity 对齐：隐私弹窗挂在 AppNavHost 之上，全文页在栈上时撤遮罩（否则读不了也滑不动）。
    // ⚠ iOS「隐私政策」是原生文档页（isNativeDocument），被 nativeRouteHandler 吞掉不进 router.routes，只能查 nativePageIds。
    val policyPageVisible = host.nativeRouteIds.any { it == AppRoute.PrivacyPolicy.id }
    CompositionLocalProvider(LocalAppRouter provides router) {
        Box(Modifier.fillMaxSize()) {
            AppNavHost(
                router,
                mainTransition = mainTransition,
                onOverlayActivityChanged = { host.navigationOverlayActive = it },
            ) { route ->
                NexioIosRouteContent(router, route)
            }
            // 隐私同意弹窗：与 Android 同挂载方式（AppNavHost 之后、同层 Box），且须在此层——位于原生
            // TabBar 之上遮罩才盖得住底栏。同意后 consented 翻转才触发 CourseScheduleApp 的云同步/公告与统计上报。
            if (!consented) {
                PrivacyConsentScreen(
                    onAgree = {
                        PrivacyConsent.setConsented()
                        // 合规：同意后方可进行设备信息上报（对齐 Android 的同意回调）
                        StatsReporter.reportActive()
                        StatsReporter.reportInstallOnce()
                    },
                    onDecline = {
                        // 对齐 Android 的 finish()：拒绝即退出。iOS 无公开退应用 API，exit(0) 是仅有的等价手段。
                        platform.posix.exit(0)
                    },
                    onOpenPolicy = { router.navigate(AppRoute.PrivacyPolicy) },
                    policyPageVisible = policyPageVisible,
                )
            }
        }
    }
}

/** Shared by native documents and the retained Compose stack, including platform injections. */
@Composable
internal fun NexioIosRouteContent(router: AppRouter, route: AppRoute) {
    when (route) {
        // 设置二级页（原主 pager 第三页）：iOS 走原生文档壳，标题/返回由 NavigationBar 给
        AppRoute.Settings -> SettingsRoute(
            router = router,
            // iOS 走 App Store 更新 → 「更新设置」入口整个不渲染
            showUpdateSettings = false,
            settingsPane = { args ->
                com.haooz.chedule.ui.screens.IosSettingsScreen(
                    args = args,
                    scheduleFileController = fileController,
                    backupFileController = backupFileController,
                    reminderController = reminderController,
                    onHolidayDataChanged = onHolidayDataChanged,
                )
            },
        )

        AppRoute.About -> AboutRoute(
            router = router,
            appVersion = AppInfo.version,
            appIcon = imageResource(Res.drawable.about_app_icon),
            appIconShape = ContinuousRoundedRectangle(20.dp, continuity = G2Continuity()),
            // 「撤回同意」确认后退出应用：iOS 无 finishAffinity 等价物，与拒绝路径一致用 exit(0)；
            // 不传则默认空实现，撤回只清状态不退出，语义丢失。
            onExitApp = { platform.posix.exit(0) },
        )
        // 纯文档壳路由，iOS 直接可用
        AppRoute.Changelog -> ChangelogRoute(router)
        AppRoute.License -> LicenseRoute(router)
        AppRoute.PrivacyPolicy -> PrivacyPolicyRoute(router)
        AppRoute.Communication -> CommunicationRoute(router)
        AppRoute.AppreciateAuthor -> AppreciateAuthorRoute(router)
        // 带注入的文档壳：iOS 用默认 Noop 能力，剪贴板注入 UIPasteboard
        AppRoute.PreferenceSettings -> PreferenceSettingsRoute(router)
        AppRoute.UpdateSettings -> UpdateSettingsRoute(router)
        // 本地备份：目录扫描+手动选文件都需原生文件能力。
        // ⚠ 不传会落到 NoopBackupFileController，点「手动选择文件」静默回调 null，什么都不发生。
        AppRoute.LocalBackup -> LocalBackupRoute(
            router = router,
            backupFileController = backupFileController,
            // 全量恢复会写节假日数据 → 重排提醒
            onHolidayDataChanged = onHolidayDataChanged,
        )
        AppRoute.AiImport -> AiImportRoute(
            router = router,
            onCopyToClipboard = { text ->
                UIPasteboard.generalPasteboard.string = text
            },
        )
        // 节假日：网络拉取走跨平台 HttpService（iOS = NSURLSession）
        AppRoute.HolidaySettings -> HolidaySettingsRoute(
            router = router,
            onHolidayDataChanged = onHolidayDataChanged,
        )
        AppRoute.WidgetIntro -> WidgetIntroRoute(
            router,
            onRequestWidgetRefresh = { IosWidgetSnapshot.refresh() },
        )
        // 课程提醒：课前通知与次日汇总。与 Android 的差异（docs/KMP_IOS_MIGRATION_PLAN.md §7）：
        // 无 setAlarmClock 对应物（课前通知可能迟到）；灵动岛只能从点击或前台启动；
        // DND/精确闹钟/电量/Shizuku 无系统 API，对应入口隐藏。
        AppRoute.CourseReminder -> {
            val islandNotification by settingsViewModel().islandNotification.collectAsState()
            CourseReminderRoute(
                router = router,
                reminderController = reminderController,
                testActionLabel = if (islandNotification) L("测试灵动岛", "Test Dynamic Island")() else L("测试通知", "Test Notification")(),
                onSendTestNotification = {
                    if (islandNotification) {
                        IosCourseLiveActivity.startTest()
                        showToast(L("已启动灵动岛测试", "Dynamic Island test started")())
                    } else {
                        IosCourseNotifier.sendTestNotification()
                        showToast(L("已发送测试通知", "Test notification sent")())
                    }
                },
            )
        }
        // WebDAV：备份/恢复本体已在 :core；恢复成功后要重排提醒
        AppRoute.WebDavSettings -> WebDavSettingsRoute(
            router = router,
            onHolidayDataChanged = onHolidayDataChanged,
        )
        // 课表导入/导出/备份：接 iOS 原生文件选择器（UIDocumentPicker）+ 三条口令导入。
        // 口令取回与星链是纯 HTTP（共享 HttpService = NSURLSession），WakeUp 走无头
        // WKWebView（IosWakeUpImport）。口令**导出**与手环推送仍是 Android 专有。
        AppRoute.ScheduleImport -> BackupAndMigrationRoute(
            router = router,
            mode = ScheduleDataManageMode.Import,
            scheduleFileController = fileController,
            onHolidayDataChanged = onHolidayDataChanged,
            onFetchShareCode = { code -> ShareCodeImport.fetchShareCode(code) },
            onImportThirdParty = { source, code -> ShareCodeImport.importThirdParty(source, code) },
        )
        AppRoute.ScheduleExport -> BackupAndMigrationRoute(
            router = router,
            mode = ScheduleDataManageMode.Export,
            scheduleFileController = fileController,
            onHolidayDataChanged = onHolidayDataChanged,
            onFetchShareCode = { code -> ShareCodeImport.fetchShareCode(code) },
            onImportThirdParty = { source, code -> ShareCodeImport.importThirdParty(source, code) },
        )
        AppRoute.ScheduleBackup -> BackupAndMigrationRoute(
            router = router,
            mode = ScheduleDataManageMode.Backup,
            scheduleFileController = fileController,
            onHolidayDataChanged = onHolidayDataChanged,
            onFetchShareCode = { code -> ShareCodeImport.fetchShareCode(code) },
            onImportThirdParty = { source, code -> ShareCodeImport.importThirdParty(source, code) },
        )
        // 课程时间管理 / 课程管理：iOS 没有自由小窗，传 false
        AppRoute.CourseTimeSettings -> CourseTimeSettingsRoute(router)
        AppRoute.CourseManage -> CourseManageRoute(router)
        // 教务导入（学校选择）：外壳全在 :ui-shared，无需平台能力（索引更新走共享 HttpService）。
        AppRoute.EducationalImport -> EducationalImportRoute(router = router)
        // 网页导入：独立一层（右侧推入），网页宿主用 WKWebView + JS 桥 shim（见 IosWebHost）。
        AppRoute.WebImport -> WebImportRoute(
            router = router,
            webHostFactory = { config, events -> rememberIosWebHost(config, events) },
        )
    }
}
