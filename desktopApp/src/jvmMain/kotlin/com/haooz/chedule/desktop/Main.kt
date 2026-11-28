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
import com.haooz.chedule.ui.activities.eduimport.SchoolSelectionScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import com.haooz.nexioui.button.Button
import com.haooz.nexioui.scaffold.Scaffold
import com.haooz.nexioui.topappbar.SharedScrollBehavior
import com.haooz.nexioui.theme.NexioTheme

fun main() {
    // 键值表和课程行表共用数据库，以便在同一事务中更新。
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

    AppInfo.init("1.6.4-desktop")

    // 内置资源从 classpath 读取，下载的文件保存在桌面数据目录。
    AppFiles.init(
        root = FileAppFile(dataDir),
        readAsset = { path ->
            val stream = object {}.javaClass.classLoader.getResourceAsStream(path)
                ?: throw IllegalArgumentException(DesktopStr.missingResource(path))
            stream.use { it.readBytes() }
        },
    )

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

/** 桌面首页的入口，与共享页面路由分开维护。 */
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
                        // 上课时间（onEditRoutine 指向宿主路由，桌面端给空实现）
                        // 没有二级页 ⇒ morphActive 保持 null，点作息行不隐藏源行
                        DesktopPage.CourseTimeSettings -> CourseTimeSettingsScreen(
                            onEditRoutine = { _, _, _ -> },
                            scrollBehavior = scrollBehavior,
                        )
                        DesktopPage.Home -> Unit // 上面已分流，不可达
                        // WebDAV 云同步（节假日副作用回调桌面端给空实现）
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
            color = NexioTheme.colorScheme.onSurfaceVariantActions,
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

@Composable
private fun PageContainer(
    title: String,
    onBack: () -> Unit,
    content: @Composable (SharedScrollBehavior?) -> Unit,
) {
    // 共享页面从当前 owner 获取 ViewModel；离开详情页时清理该页的 store。
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

private class DesktopViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}
