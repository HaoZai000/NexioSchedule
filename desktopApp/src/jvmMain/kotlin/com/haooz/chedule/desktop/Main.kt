// Nexio课程表 - 桌面客户端入口（Compose Multiplatform Desktop / JVM）：验证 KMP 迁移成果，与 :androidApp 共用 :ui-shared / :core；本模块不参与 Android 的 --offline 红线。
// 平台中立性：本模块是平台宿主（同 :androidApp），commonMain 规则不适用，但经它编译的 :ui-shared / :core 代码必须过 checkKmpPurity。
package com.haooz.chedule.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.haooz.chedule.data.platform.AppFiles
import com.haooz.chedule.data.platform.AppInfo
import com.haooz.chedule.data.platform.FileAppFile
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.CourseStorage
import com.haooz.chedule.data.storage.DesktopSqlStorage
import com.haooz.chedule.i18n.DesktopStr
import com.haooz.chedule.ui.activities.AppreciateAuthorScreen
import com.haooz.chedule.ui.activities.ChangelogScreen
import com.haooz.chedule.ui.activities.CommunicationScreen
import com.haooz.chedule.ui.activities.CourseTimeSettingsScreen
import com.haooz.chedule.ui.activities.LicenseScreen
import com.haooz.chedule.ui.activities.PrivacyPolicyScreen
import com.haooz.chedule.ui.activities.WebDavSettingsScreen
import com.haooz.chedule.ui.activities.WidgetIntroScreen
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.components.text.miuix.Text as Text
import com.haooz.chedule.ui.screens.AddCourseDialog
import com.haooz.chedule.ui.screens.CourseDetailScreen
import com.haooz.chedule.ui.screens.CourseEditScreen
import com.haooz.chedule.ui.screens.SchoolSelectionScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 桌面端当前阶段的形态：迁移验证壳。每迁入一批页面就多一批可点开的真实页面，作为「迁移可用、可以跑」的直接证据；全部迁完后由正式路由（AppRouter）替换。
 */
fun main() {
    // ── 启动注入（对应 Android 的 NexioApplication.onCreate 的四个 init）──────────
    // 1. 存储：与 Android / iOS 同一个 nexio.db（键值表 + 课程行表同库同事务）
    val dataDir = File(
        System.getProperty("user.home"),
        ".nexio-chedule",   // 桌面数据目录；Android 的 17 个偏好名原样沿用为命名空间
    )
    dataDir.mkdirs()
    DesktopSqlStorage.init(dataDir)
    AppStorage.init { name -> DesktopSqlStorage.store(name) }
    CourseStorage.install { DesktopSqlStorage.courseRows() }
    // 桌面从第一天就是 nexio.db：没有 blob 存量可搬，直接记为已迁移
    runCatching { DesktopSqlStorage.courseRows().markFullyMigrated() }
    // 语言镜像尽早就位：core 数据类的显示变体（DisplayLang）依赖它；与 CourseScheduleApp 里的恢复同值
    com.haooz.chedule.i18n.AppLanguage.init(com.haooz.chedule.i18n.LanguageStore.load())

    // 2. 版本号：桌面读不到 PackageManager，直接给（上报的 app_version 用）
    AppInfo.init("1.6.4-desktop")

    // 3. 文件系统 + 内置资源：root 指向应用数据目录；readAsset 从 classpath 读（Android 读 assets，桌面读 resources，同一契约、平台各自实现）
    AppFiles.init(
        root = FileAppFile(dataDir),
        readAsset = { path ->
            val stream = object {}.javaClass.classLoader.getResourceAsStream(path)
                ?: throw IllegalArgumentException(DesktopStr.missingResource(path))
            stream.use { it.readBytes() }
        },
    )

    // 4. 材质档位：与 Android（NexioApplication）同步从 app_preferences 载入；不调则玻璃/模糊材质停在默认档，桌面验证材质效果失真
    AppMaterialSettings.load()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = com.haooz.chedule.i18n.DesktopStr.appTitle(),
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            CourseScheduleTheme {
                DesktopShell()
            }
        }
    }
}

/**
 * 页面栈（极简：首页 + 若干详情页，够当前验证阶段用）。
 * ⚠ 与 `:ui-shared` 的 `AppRoute` 是两份路由知识：本壳未包 AppRoute，因 SchoolSelection（桌面无 WebView 导入，直接暴露选校页）与 AddCourse（弹窗演示）本就无对应路由，且路由→内容映射带着 Android 触点搬不动。代价：Android 侧增改路由时这里不告警，每迁一批页面记得补入口，正式路由替换后本注释删除。
 */
private enum class DesktopPage {
    Home,
    Changelog,
    License,
    PrivacyPolicy,
    Communication,
    SchoolSelection,
    CourseTimeSettings,
    AppreciateAuthor,
    AddCourse,
    WidgetIntro,
    WebDavSettings,
    CourseDetail,
    CourseEdit;

    fun displayTitle(): String = when (this) {
        Home -> com.haooz.chedule.i18n.DesktopStr.home()
        Changelog -> com.haooz.chedule.i18n.DesktopStr.changelog()
        License -> com.haooz.chedule.i18n.DesktopStr.license()
        PrivacyPolicy -> com.haooz.chedule.i18n.DesktopStr.privacyPolicy()
        Communication -> com.haooz.chedule.i18n.DesktopStr.communication()
        SchoolSelection -> com.haooz.chedule.i18n.DesktopStr.schoolSelection()
        CourseTimeSettings -> com.haooz.chedule.i18n.DesktopStr.courseTimeSettings()
        AppreciateAuthor -> com.haooz.chedule.i18n.DesktopStr.appreciateAuthor()
        AddCourse -> com.haooz.chedule.i18n.DesktopStr.addCourse()
        WidgetIntro -> com.haooz.chedule.i18n.DesktopStr.widgetIntro()
        WebDavSettings -> com.haooz.chedule.i18n.DesktopStr.webDavSettings()
        CourseDetail -> com.haooz.chedule.i18n.DesktopStr.courseDetail()
        CourseEdit -> com.haooz.chedule.i18n.DesktopStr.courseEdit()
    }
}

@Composable
private fun DesktopShell() {
    var page by remember { mutableStateOf(DesktopPage.Home) }

    Scaffold(
        topBar = {},
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (page == DesktopPage.Home) {
                HomeContent(onOpen = { page = it })
            } else {
                // 与 Android 路由页同一脚手架（DocumentPageScaffold：折叠大标题顶栏 + 液态玻璃圆形返回按钮 + 采样层），正是「多平台视觉一致性」的验证点——同一份组件代码在桌面渲染。
                PageContainer(
                    title = page.displayTitle(),
                    onBack = { page = DesktopPage.Home },
                ) { scrollBehavior ->
                    when (page) {
                        DesktopPage.Changelog -> ChangelogScreen(scrollBehavior)
                        DesktopPage.License -> LicenseScreen(scrollBehavior)
                        DesktopPage.PrivacyPolicy -> PrivacyPolicyScreen(scrollBehavior)
                        DesktopPage.Communication -> CommunicationScreen(scrollBehavior)
                        // 教务导入的学校/适配器选择页；onAdapterSelected 在 Android 侧接「打开 WebView 导入」
                        DesktopPage.SchoolSelection -> SchoolSelectionScreen(
                            scrollBehavior = scrollBehavior,
                            onAdapterSelected = { _, _ -> },
                        )
                        // 课程时间管理（onEditRoutine 指向宿主路由，桌面端给空实现）
                        // 没有二级页 ⇒ morphActive 保持 null，点作息行不隐藏源行
                        DesktopPage.CourseTimeSettings -> CourseTimeSettingsScreen(
                            onEditRoutine = { _, _, _ -> },
                            scrollBehavior = scrollBehavior,
                        )
                        DesktopPage.Home -> Unit // 上面已分流，不可达
                        // WebDAV 云备份（节假日副作用回调桌面端给空实现）
                        DesktopPage.WebDavSettings -> WebDavSettingsScreen(
                            scrollBehavior = scrollBehavior,
                        )
                        // 赞赏作者（捐赠明细，走 :core 的 AppreciationFetcher）
                        DesktopPage.AppreciateAuthor -> AppreciateAuthorScreen(
                            scrollBehavior = scrollBehavior,
                        )
                        // 添加课程弹窗（自带 OverlayDialog 浮层，不套页面脚手架）
                        DesktopPage.AddCourse -> AddCourseDialog(
                            show = true,
                            course = null,
                            selectedDay = 1,
                            onDismiss = { page = DesktopPage.Home },
                            onConfirm = { page = DesktopPage.Home },
                            onDelete = { },
                        )
                        // 桌面小部件介绍页。onRequestWidgetRefresh 是 Android 专有能力（AppWidget + RemoteViews），桌面端无小组件，宿主给空实现，页面本体照常渲染。
                        DesktopPage.WidgetIntro -> WidgetIntroScreen(
                            scrollBehavior = scrollBehavior,
                            onRequestWidgetRefresh = { },
                        )
                        // 课程详情页（morph 转场页，自带全屏 Box 脚手架）；快照为 null 时只渲染内容层，空课程列表即空列表页。
                        DesktopPage.CourseDetail -> CourseDetailScreen(
                            courses = emptyList(),
                            cardLeft = 0f,
                            cardTop = 0f,
                            cardWidth = 480f,
                            cardHeight = 120f,
                            screenWidth = 1200f,
                            screenHeight = 800f,
                            screenCornerRadius = ScreenCornerRadiiPx.uniform(20f),
                            cardSnapshot = null,
                            sectionTimes = emptyMap(),
                            classStartTime = "2026/3/2",
                            teachingWeekReorganizations = emptyList(),
                            onBackStart = { },
                            onBack = { page = DesktopPage.Home },
                        )
                        // 课程编辑页（同为 morph 转场页）；liquidGlassBackdrop 必须非空（页内 `!!`），桌面端自建采样层。
                        DesktopPage.CourseEdit -> CourseEditScreen(
                            courses = emptyList(),
                            cardLeft = 0f,
                            cardTop = 0f,
                            cardWidth = 480f,
                            cardHeight = 120f,
                            screenWidth = 1200f,
                            screenHeight = 800f,
                            screenCornerRadius = ScreenCornerRadiiPx.uniform(20f),
                            cardSnapshot = null,
                            onBackStart = { },
                            onBack = { page = DesktopPage.Home },
                            liquidGlassBackdrop = rememberLayerBackdrop(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 首页：列出**已迁入 :ui-shared 的页面**。每个入口渲染的都是与 Android 端完全相同的一份代码（同一个 composable 函数）——点开能显示，就证明这批迁移在第二个平台上真实可用。
 */
@Composable
private fun HomeContent(onOpen: (DesktopPage) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = com.haooz.chedule.i18n.DesktopStr.migratedPages(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        Text(
            text = com.haooz.chedule.i18n.DesktopStr.migratedPagesDesc(),
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
        DesktopPage.entries
            .filter { it != DesktopPage.Home }
            .forEach { target ->
                Button(
                    onClick = { onOpen(target) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = target.displayTitle())
                }
            }
    }
}

/**
 * 详情页容器：与 Android 路由页共用 [DocumentPageScaffold]（折叠大标题 + 玻璃返回按钮 + 采样层）。
 */
@Composable
private fun PageContainer(
    title: String,
    onBack: () -> Unit,
    content: @Composable (SharedScrollBehavior?) -> Unit,
) {
    // 共享页面普遍用 `viewModel()` 自取 ViewModel，而桌面端没有 Android 的 Activity/Fragment 提供 ViewModelStoreOwner —— 不补一个会直接崩：
    //   UnsupportedOperationException: `Factory.create(String, CreationExtras)` is not implemented
    // 这里给每个页面发一个独立 store，离开页面时 clear（等价于页面销毁）。
    val owner = remember { DesktopViewModelStoreOwner() }
    DisposableEffect(Unit) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        DocumentPageScaffold(
            title = title,
            onBack = onBack,
        ) { scrollBehavior, _ ->
            content(scrollBehavior)
        }
    }
}

/** 桌面端最小的 [ViewModelStoreOwner]：一个页面一个 store，不做跨页面共享。 */
private class DesktopViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}
