package com.haooz.chedule.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.FeedbackStr
import com.haooz.chedule.i18n.HolidayStr
import com.haooz.chedule.i18n.L10n
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.chedule.ui.activities.AppreciateAuthorScreen
import com.haooz.chedule.ui.activities.CommunicationScreen
import com.haooz.chedule.ui.activities.CourseTimeSettingsScreen
import com.haooz.chedule.ui.activities.FeedbackHistoryScreen
import com.haooz.chedule.ui.activities.FeedbackRoadmapScreen
import com.haooz.chedule.ui.activities.FeedbackScreen
import com.haooz.chedule.ui.activities.rememberFeedbackFormState
import com.haooz.chedule.ui.activities.HolidaySettingsScreen
import com.haooz.chedule.ui.activities.WebDavSettingsScreen
import com.haooz.chedule.ui.activities.WidgetIntroScreen
import com.haooz.chedule.ui.basic.LiquidGlassTextButton
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.CapsuleSegmentedControl
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.components.HorizontalPageSwitch
import com.haooz.chedule.ui.components.LocalPageStartPadding
import com.haooz.chedule.ui.components.rememberPageSwitchState
import com.haooz.chedule.ui.components.tabletNavChromeTitleSlot
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.layout.rememberTopBarMaterialState
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import top.yukonga.miuix.kmp.utils.OverScrollState
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 平板设置页目的地：全部入口平铺在左栏，右栏直接渲染内容，全程不跳 Activity。
 * 仅 pad 使用此分组（与手机设置页分类不同）。
 */
enum class TabletSettingsDest {
    Semester,
    CourseTime,
    Reminder,
    Holiday,
    Widget,
    ScheduleImport,
    AiImport,
    EducationalImport,
    ScheduleExport,
    LocalBackup,
    WebDav,
    Preference,
    Update,
    About,
    Appreciate,
    Communication,
    // 以下三个是「问题反馈」里的子页：只在右栏切换，不进左栏列表（未列入 StandaloneDests/BottomMoreDests）
    Feedback,
    FeedbackHistory,
    FeedbackRoadmap,
    ;

    /** 左栏入口与右栏固定顶栏的标题文案 */
    fun displayTitle(): String = when (this) {
        Semester -> SettingsStr.destSemester()
        CourseTime -> SettingsStr.courseTimeManagement()
        Reminder -> SettingsStr.courseReminder()
        Holiday -> SettingsStr.holidayAndWorkswap()
        Widget -> SettingsStr.homeScreenWidget()
        ScheduleImport -> SettingsStr.destFileCodeImport()
        AiImport -> SettingsStr.destAiImport()
        EducationalImport -> SettingsStr.destAcademicImport()
        ScheduleExport -> SettingsStr.scheduleExport()
        LocalBackup -> SettingsStr.destLocalBackup()
        WebDav -> SettingsStr.destWebDav()
        Preference -> SettingsStr.appPreferences()
        Update -> SettingsStr.updateSettings()
        About -> SettingsStr.aboutApp()
        Appreciate -> SettingsStr.destAppreciate()
        Communication -> SettingsStr.destCommunication()
        Feedback -> FeedbackStr.title()
        FeedbackHistory -> FeedbackStr.historyTitle()
        FeedbackRoadmap -> FeedbackStr.roadmapTitle()
    }

    /** 左栏分组标题文案 */
    fun displayGroup(): String = when (this) {
        Semester, CourseTime -> SettingsStr.basicSettings()
        Reminder, Holiday, Widget -> SettingsStr.featuresSection()
        ScheduleImport, AiImport, EducationalImport, ScheduleExport -> SettingsStr.groupImportExport()
        LocalBackup, WebDav -> SettingsStr.groupBackup()
        Preference, Update, About, Appreciate, Communication,
        Feedback, FeedbackHistory, FeedbackRoadmap -> CommonStr.other()
    }
}

/** 平板设置选中项：MainActivity 叠层读它画固定标题 */
object TabletSettingsUiState {
    var selected by mutableStateOf(TabletSettingsDest.Semester)

    /** 上课时间的编辑屏是否打开：打开时叠层不画右栏标题，避免与编辑屏标题重合 */
    var timeEditorOpen by mutableStateOf(false)
}

/**
 * 画在 MainActivity 层的设置页固定标题 + 分界线。标题用顶栏折叠态样式（19sp / Medium），
 * 垂直位置与 CollapsibleTopAppBar 折叠标题一致，水平在左右两栏各自居中。
 */
@Composable
fun TabletSettingsChromeOverlay() {
    val selected = TabletSettingsUiState.selected
    val statusBar = mainWindowTopInset()
    val collapsedH = top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults.CollapsedHeight
    val titleColor = MiuixTheme.colorScheme.onSurface
    val density = LocalDensity.current
    val widthDp = screenWidthDp()
    val statusBarPx = with(density) { statusBar.roundToPx() }
    val collapsedHPx = with(density) { collapsedH.roundToPx() }
    val collapsedTotalPx = with(density) {
        (com.haooz.chedule.ui.components.TabletNavSideInset +
                com.haooz.chedule.ui.components.TabletNavIconRailWidth).toPx()
    }
    val expandedWidthPx = with(density) {
        com.haooz.chedule.ui.components.tabletNavExpandedWidthDp(widthDp.toFloat()).toPx()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // 只高于右栏面板内容(0)，但低于 OverlayDialog 弹窗(z>=1)，避免分割线盖到弹窗上
            .zIndex(0.5f)
    ) {
        val maxWPx = with(density) { maxWidth.toPx() }

        // 折叠态标题：状态栏下 CollapsedHeight 内垂直居中。
        // textAlign 必须显式给 Center：文字变化动效是「旧字新字各画一份、容器取两者较宽」，
        // 短的那条按 textAlign 落位 —— 不传就是靠左，切页时能看到标题从左边滑成居中。
        val collapsedTitle: @Composable (String) -> Unit = { text ->
            Text(
                text = text,
                color = titleColor,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .tabletNavChromeTitleSlot(
                    maxWPx = maxWPx,
                    collapsedTotalPx = collapsedTotalPx,
                    expandedWidthPx = expandedWidthPx,
                    statusBarPx = statusBarPx,
                    heightPx = collapsedHPx,
                    isLeftColumn = true,
                ),
            contentAlignment = Alignment.Center
        ) {
            collapsedTitle(CommonStr.settings())
        }

        // 关于应用内嵌自绘顶栏标题 / 编辑屏打开时，设置页叠加层不再画右栏标题，避免重合
        if (selected != TabletSettingsDest.About && !TabletSettingsUiState.timeEditorOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .tabletNavChromeTitleSlot(
                        maxWPx = maxWPx,
                        collapsedTotalPx = collapsedTotalPx,
                        expandedWidthPx = expandedWidthPx,
                        statusBarPx = statusBarPx,
                        heightPx = collapsedHPx,
                        isLeftColumn = false,
                    ),
                contentAlignment = Alignment.Center
            ) {
                collapsedTitle(selected.displayTitle())
            }
        }
    }
}

/**
 * 当前窗口顶部系统内边距。用 systemBars 仅取顶部：分屏/自由窗口下 statusBars
 * 顶部为 0，用 systemBars 才不会让内容贴顶。
 */
@Composable
private fun mainWindowTopInset(): Dp =
    WindowInsets.systemBars.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()

/** pad 设置左右栏顶部糊层高度：120.dp + 顶部内边距 */
private val TabletPaneBlurHeight: Dp
    @Composable get() {
        return 80.dp + mainWindowTopInset()
    }

/**
 * 本栏顶部：渐变模糊与表面色遮罩均常驻。
 * 糊层采样本栏本地 backdrop（兄弟节点），避免与全局层循环采样。
 */
@Composable
private fun TabletPaneTopChrome(
    backdrop: com.haooz.softglass.backdrop.GlassBackdrop?,
    modifier: Modifier = Modifier,
) {
    val maskHeight = TabletPaneBlurHeight

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(maskHeight)
    ) {
        // 渐变模糊：常驻。不再叠表面色遮罩（压暗层已全局移除）
        if (backdrop != null) {
            ProgressiveBlurTopBar(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize(),
                height = maskHeight,
                blurAlpha = 1f,
                content = {},
            )
        }
    }
}

/**
 * 右栏滚动观察：复用手机 [SharedScrollBehavior] 的 contentOffset 累计。
 * 外包一层代理：越界期间把 consumed.y 置零再交给委托，避免 OverScroll 把
 * available 回灌进 contentOffset。
 */
@Composable
private fun rememberPaneScrollTracker(
    resetKey: Any?,
    scrollBehavior: SharedScrollBehavior,
    overScroll: OverScrollState,
): NestedScrollConnection {
    return remember(resetKey, scrollBehavior, overScroll) {
        val delegate = scrollBehavior.nestedScrollConnection
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                delegate.onPreScroll(available, source)

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // offset != 0f 而非 isOverScrollActive：避开 0~1px 假阴性窗口
                val safeConsumed =
                    if (overScroll.offset != 0f) Offset.Zero else consumed
                return delegate.onPostScroll(safeConsumed, available, source)
            }

            override suspend fun onPreFling(available: Velocity): Velocity =
                delegate.onPreFling(available)

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = delegate.onPostFling(consumed, available)
        }
    }
}

/**
 * 平板设置：左右两栏。左=平铺入口，右=选中内容。静态替换，无 Activity 跳转。
 * 固定标题与分界线由 MainActivity 叠层绘制；顶部渐变画在本页内容层，左右各自驱动。
 */
@Composable
fun TabletSettingsScreen(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    shiftViewModel: ShiftViewModel,
    isShiftMode: Boolean,
    onExitShiftMode: () -> Unit,
    onEnterShiftMode: () -> Unit = {},
    /** 是否显示「排班模式」入口（iOS 不支持 → false，入口整个不渲染） */
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    /** 平台专有设置面板（下述 8 个 dest）由宿主渲染；null 显示占位 */
    androidPane: (@Composable (TabletSettingsDest) -> Unit)? = null,
    /** 提醒页「测试通知」动作（Android 发超级岛/实时活动） */
    onSendTestNotification: () -> Unit = {},
    /** 是否超级岛设备（决定「测试通知」按钮文案；Android 注入，桌面/iOS false） */
    isIslandSupported: () -> Boolean = { false },
    /**
     * 提醒页底部测试按钮文案；null = 按 [isIslandSupported] 走平台默认
     * （「测试小米超级岛」/「测试实时活动」）。iOS 传「测试灵动岛」/「测试通知」。
     */
    testActionLabel: String? = null,
    /** 小部件页「刷新已装小部件」（Android = WidgetUpdateCache） */
    onRefreshWidgets: () -> Unit = {},
    /** 节假日数据变更副作用（重排提醒+刷小部件） */
    onHolidayDataChanged: () -> Unit = {},
    /** 节假日 API 拉取（Android 宿主实现）；null 时返回 emptyList */
    fetchHolidayEntries: (suspend (year: Int) -> List<HolidayEntry>)? = null,
    /** 是否显示「更新」入口（iOS 走 App Store → false，左栏那一项整个不渲染） */
    showUpdateSettings: Boolean = true,
) {
    val selected = TabletSettingsUiState.selected
    val groups = remember(AppLanguage.resolved) {
        TabletSettingsDest.entries.groupBy { it.displayGroup() }
    }
    val paneHorizontal = 20.dp
    // 顶栏折叠标题高度：左右内容都从这条线下方开始，避免被 MainActivity 叠层标题压住
    val chromeTop = mainWindowTopInset() +
            top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults.CollapsedHeight +
            12.dp

    // 手机端底部按钮画在路由 overlay；pad 内嵌 Screen 时右栏在录制层之外补同一份
    var showWidgetGuideDialog by remember { mutableStateOf(false) }
    var webDavBackingUp by remember { mutableStateOf(false) }
    var webDavRestoring by remember { mutableStateOf(false) }
    var onWebDavBackup by remember { mutableStateOf({}) }
    var onWebDavRestore by remember { mutableStateOf({}) }
    var webDavConnected by remember { mutableStateOf(false) }
    var onWebDavTestConnection by remember { mutableStateOf({}) }
    var holidayLoading by remember { mutableStateOf(false) }
    var onHolidayUpdate by remember { mutableStateOf({}) }
    // 上课时间：右栏叠编辑屏
    val courseRepository = remember { com.haooz.chedule.data.repo.CourseRepository() }
    var editingTimeConfig by remember { mutableStateOf<TimeConfig?>(null) }
    var editingTimeRoutineId by remember { mutableStateOf<Long?>(null) }
    var timeConfigRefreshTrigger by remember { mutableIntStateOf(0) }
    val uiScope = rememberCoroutineScope()
    // 1 = 完全在右侧屏外，0 = 完全滑入；退出时先滑到 1 再清空配置，保证退场有内容
    val timeEditorSlide = remember { Animatable(1f) }
    // 切页时收起编辑屏，并恢复叠加层标题
    LaunchedEffect(selected) {
        editingTimeConfig = null
        editingTimeRoutineId = null
        TabletSettingsUiState.timeEditorOpen = false
    }
    val islandNotification by settingsViewModel.islandNotification.collectAsState()
    // 是否超级岛设备由宿主注入（Android 平台侧查询，桌面/iOS 恒 false）
    val islandEnabled = islandNotification && isIslandSupported()
    val hapticFeedback = LocalHapticFeedback.current
    var showShiftModeConfirmDialog by remember { mutableStateOf(false) }

    val leftListState = rememberLazyListState()
    // 右栏专属 overscroll：与全局 CompositionLocal 分离，避免左右栏越界状态串扰
    val rightOverScroll = remember { OverScrollState() }

    // 页面底色铺到屏幕边缘（含侧栏下），内容整体避让 —— background 在 padding 之前，
    // 所以铺满的是底色、收窄的只有内容。
    val paneSurface = MiuixTheme.colorScheme.surface
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(paneSurface)
            .then(LocalPageStartPadding.current)
    ) {
        val density = LocalDensity.current
        // 左栏固定为屏宽 0.39，不随侧栏展开/收起变化；剩余宽度全给右栏
        val leftWidth = screenWidthDp().dp * 0.39f
        val rightWidth = maxWidth - leftWidth
        val dividerColor =
            if (com.haooz.chedule.ui.utils.isAppDarkTheme()) {
                Color.White.copy(alpha = 0.1f)
            } else {
                Color.Black.copy(alpha = 0.06f)
            }
        val surfaceColor = MiuixTheme.colorScheme.surface
        val leftPaneBackdrop = rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
        val rightPaneBackdrop = rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // —— 左栏：结构对齐手机设置页 —— layerBackdrop 包列表，列表只用 overScrollVertical
            Box(
                modifier = Modifier
                    .width(leftWidth)
                    .fillMaxHeight()
                    .background(surfaceColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 无材质档：玻璃全关，采样录屏层不挂
                        .then(
                            if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) Modifier
                            else Modifier.layerBackdrop(leftPaneBackdrop)
                        )
                ) {
                    LazyColumn(
                        state = leftListState,
                        // 与完整设置页相同：只用公共 overScrollVertical
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove),
                        contentPadding = PaddingValues(
                            start = paneHorizontal,
                            top = chromeTop,
                            end = paneHorizontal,
                            bottom = 60.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groups.filterKeys { key ->
                            // 排班模式只保留「基本设置」分类
                            !isShiftMode || key == TabletSettingsDest.Semester.displayGroup()
                        }.forEach { (group, dests) ->
                            item(key = "g_$group") {
                                SmallTitle(
                                    text = group,
                                )
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    insideMargin = PaddingValues(0.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // LocalAppRouter.current 是 @Composable 调用，必须在组合层取
                                        val settingsRouter = LocalAppRouter.current
                                        dests.filterNot {
                                            it in StandaloneDests || it in BottomMoreDests ||
                                                    (!showUpdateSettings && it == TabletSettingsDest.Update)
                                        }
                                            .forEach { dest ->
                                                val jumpActivity =
                                                    dest == TabletSettingsDest.EducationalImport
                                                TabletLeftEntry(
                                                    dest = dest,
                                                    isSelected = !jumpActivity && dest == selected,
                                                    onSelect = {
                                                        if (jumpActivity) {
                                                            // 单 Activity：教务导入走路由，不再 startActivity
                                                            settingsRouter.navigate(AppRoute.EducationalImport)
                                                        } else {
                                                            TabletSettingsUiState.selected = dest
                                                        }
                                                    }
                                                )
                                            }
                                    }
                                }
                            }
                            // 课表导出独立卡片
                            if (group == TabletSettingsDest.ScheduleExport.displayGroup() && StandaloneDests.isNotEmpty()) {
                                item(key = "x_export") {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            StandaloneDests.forEach { dest ->
                                                TabletLeftEntry(
                                                    dest = dest,
                                                    isSelected = dest == selected,
                                                    onSelect = {
                                                        TabletSettingsUiState.selected = dest
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // 关于应用/捐赠支持/问题反馈独立卡片：同「其他」分类下
                            if (group == TabletSettingsDest.About.displayGroup() && BottomMoreDests.isNotEmpty()) {
                                item(key = "x_about_more") {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(0.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            BottomMoreDests.forEach { dest ->
                                                TabletLeftEntry(
                                                    dest = dest,
                                                    isSelected = dest == selected,
                                                    // 与手机设置页一致：「关于应用」右侧提示该页内含捐赠入口
                                                    hint = if (dest == TabletSettingsDest.About) {
                                                        SettingsStr.supportAuthor
                                                    } else null,
                                                    onSelect = {
                                                        TabletSettingsUiState.selected = dest
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // 排班模式：退出入口放左栏
                        if (isShiftMode) {
                            item(key = "shift_exit") {
                                top.yukonga.miuix.kmp.basic.Button(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 24.dp)
                                        .height(50.dp),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onExitShiftMode()
                                    },
                                    colors = if (
                                        com.haooz.chedule.ui.utils.isAppDarkTheme()
                                    ) ButtonDefaults.buttonColors(color = Color(0xFF181818))
                                    else ButtonDefaults.buttonColors(),
                                ) {
                                    Text(
                                        text = SettingsStr.exitShiftMode(),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFF44336)
                                    )
                                }
                            }
                        }
                    }
                }
                TabletPaneTopChrome(
                    backdrop = leftPaneBackdrop,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }

            // —— 右栏：观察 nestedScroll 放在录制层外层，列表自身只保留 overScrollVertical ——
            Box(
                modifier = Modifier
                    .width(rightWidth)
                    .fillMaxHeight()
                    .background(surfaceColor)
            ) {
                // 右栏独立 overscroll 作用域：子屏列表与遮罩/右上角按钮共用同一实例
                CompositionLocalProvider(LocalOverScrollState provides rightOverScroll) {
                    // 与手机同一套 contentOffset 累计（甩动也走 onPostScroll）
                    val rightScrollBehavior = rememberSharedScrollBehavior()
                    LaunchedEffect(rightScrollBehavior) {
                        // pad 无大标题折叠栏：不消费滚动，只记 contentOffset
                        rightScrollBehavior.state.heightOffsetLimit = -1f
                    }
                    LaunchedEffect(selected) {
                        rightScrollBehavior.state.contentOffset = 0f
                        rightScrollBehavior.state.heightOffset = 0f
                    }
                    val rightTrack =
                        rememberPaneScrollTracker(selected, rightScrollBehavior, rightOverScroll)
                    // 反馈表单状态：内容在下方 when 里、提交按钮在底部玻璃层，同一次组合内共用一份
                    val feedbackFormState = rememberFeedbackFormState()
                    val feedbackScope = rememberCoroutineScope()
                    // 右上角按钮的材质走全站统一判据（含越界污染自愈）；本栏遮罩仍常驻
                    val rightMaterial = rememberTopBarMaterialState(rightScrollBehavior)
                    val rightMaskAlpha = rightMaterial.alpha
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(rightTrack)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // 无材质档：玻璃全关，采样录屏层不挂
                                .then(
                                    if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) Modifier
                                    else Modifier.layerBackdrop(rightPaneBackdrop)
                                )
                        ) {
                            when (selected) {
                                TabletSettingsDest.Semester -> TabletSemesterPane(
                                    viewModel = viewModel,
                                    scheduleViewModel = scheduleViewModel,
                                    settingsViewModel = settingsViewModel,
                                    shiftViewModel = shiftViewModel,
                                    isShiftMode = isShiftMode,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    scrollBehavior = null,
                                    onRequestEnterShiftMode = { showShiftModeConfirmDialog = true },
                                )

                                TabletSettingsDest.CourseTime -> CourseTimeSettingsScreen(
                                    onEditRoutine = { routine, baseConfig, _ ->
                                        // 二级页面只编辑这一个作息：把该作息的时间叠加上去
                                        editingTimeConfig = baseConfig.effectiveFor(routine.id)
                                        editingTimeRoutineId = routine.id
                                        TabletSettingsUiState.timeEditorOpen = true
                                        uiScope.launch {
                                            timeEditorSlide.snapTo(1f)
                                            timeEditorSlide.animateTo(
                                                0f,
                                                animationSpec = tween(
                                                    520,
                                                    easing = CubicBezierEasing(
                                                        0.3f,
                                                        0.92f,
                                                        0.3f,
                                                        1f
                                                    )
                                                )
                                            )
                                        }
                                    },
                                    refreshTrigger = timeConfigRefreshTrigger,
                                    scrollBehavior = null,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    // 右栏编辑屏打开期间隐藏源行，关掉动画播完才放开
                                    morphActive = editingTimeConfig != null,
                                )

                                TabletSettingsDest.Reminder ->
                                    androidPane?.invoke(selected) ?: Box(Modifier.fillMaxSize())

                                TabletSettingsDest.Holiday -> TabletHolidayPane(
                                    scrollBehavior = null,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    onUpdateReady = { onHolidayUpdate = it },
                                    onLoadingChange = { holidayLoading = it },
                                    onTeachingWeekReorganizationsChanged =
                                        viewModel::refreshTeachingWeekReorganizations,
                                    onHolidayDataChanged = onHolidayDataChanged,
                                    fetchHolidayEntries = fetchHolidayEntries,
                                )

                                TabletSettingsDest.Widget -> WidgetIntroScreen(
                                    scrollBehavior = null,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    // 已装小部件刷新是 Android 专有能力，由宿主注入
                                    onRequestWidgetRefresh = { onRefreshWidgets() },
                                )

                                TabletSettingsDest.ScheduleImport ->
                                    androidPane?.invoke(selected) ?: Box(Modifier.fillMaxSize())

                                TabletSettingsDest.EducationalImport -> {
                                    Box(modifier = Modifier.fillMaxSize())
                                }

                                TabletSettingsDest.AiImport ->
                                    androidPane?.invoke(selected) ?: Box(Modifier.fillMaxSize())

                                TabletSettingsDest.ScheduleExport ->
                                    androidPane?.invoke(selected) ?: Box(Modifier.fillMaxSize())

                                TabletSettingsDest.LocalBackup ->
                                    androidPane?.invoke(selected) ?: Box(Modifier.fillMaxSize())

                                TabletSettingsDest.WebDav -> WebDavSettingsScreen(
                                    scrollBehavior = null,
                                    onConnectedChange = { webDavConnected = it },
                                    onTestConnectionReady = { onWebDavTestConnection = it },
                                    onBackupRestoreReady = { backup, restore ->
                                        onWebDavBackup = backup
                                        onWebDavRestore = restore
                                    },
                                    onBusyStateChange = { b, r ->
                                        webDavBackingUp = b
                                        webDavRestoring = r
                                    },
                                    onHolidayDataChanged = { onHolidayDataChanged() },
                                )

                                TabletSettingsDest.Preference ->
                                    androidPane?.invoke(selected) ?: Box(Modifier.fillMaxSize())

                                TabletSettingsDest.Update ->
                                    androidPane?.invoke(selected) ?: Box(Modifier.fillMaxSize())

                                TabletSettingsDest.About ->
                                    androidPane?.invoke(selected) ?: Box(Modifier.fillMaxSize())

                                TabletSettingsDest.Appreciate -> AppreciateAuthorScreen(
                                    scrollBehavior = null,
                                )


                                TabletSettingsDest.Communication -> CommunicationScreen(
                                    scrollBehavior = null,
                                    onOpenFeedback = {
                                        TabletSettingsUiState.selected = TabletSettingsDest.Feedback
                                    },
                                    onOpenRoadmap = {
                                        TabletSettingsUiState.selected = TabletSettingsDest.FeedbackRoadmap
                                    },
                                )

                                TabletSettingsDest.Feedback -> FeedbackScreen(
                                    state = feedbackFormState,
                                    scrollBehavior = null,
                                    onOpenHistory = {
                                        TabletSettingsUiState.selected = TabletSettingsDest.FeedbackHistory
                                    },
                                    onOpenRoadmap = {
                                        TabletSettingsUiState.selected = TabletSettingsDest.FeedbackRoadmap
                                    },
                                )

                                TabletSettingsDest.FeedbackHistory -> FeedbackHistoryScreen(
                                    scrollBehavior = null,
                                    onWriteFeedback = {
                                        TabletSettingsUiState.selected = TabletSettingsDest.Feedback
                                    },
                                )

                                TabletSettingsDest.FeedbackRoadmap -> FeedbackRoadmapScreen(
                                    scrollBehavior = null,
                                )
                            }
                        }

                        // 底部操作按钮：玻璃按钮必须采样右栏本地 backdrop（兄弟节点，记录内容不含按钮），
                        // 不能采样全局 liquidGlassBackdrop——按钮就在全局玻璃层内，采样自身会循环采样。
                        val glassBackdrop = liquidGlassBackdrop
                        if (glassBackdrop != null) when (selected) {
                            // 反馈页的提交按钮：与手机同一枚玻璃按钮，这里采样右栏本地 backdrop
                            TabletSettingsDest.Feedback -> {
                                val state = feedbackFormState
                                if (state.submittedId == null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .padding(horizontal = 36.dp)
                                            .navigationBarsPadding()
                                            .padding(bottom = 20.dp)
                                    ) {
                                        LiquidGlassTextButton(
                                            text = if (state.submitting) {
                                                FeedbackStr.submitting()
                                            } else {
                                                FeedbackStr.submit()
                                            },
                                            enabled = state.canSubmit,
                                            backdrop = rightPaneBackdrop,
                                            onClick = {
                                                if (state.validateEmail()) {
                                                    feedbackScope.launch {
                                                        state.submit().onFailure { error ->
                                                            showToast(
                                                                error.message ?: FeedbackStr.loadFailed(),
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }

                            TabletSettingsDest.Reminder -> {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 36.dp)
                                        .navigationBarsPadding()
                                        .padding(bottom = 20.dp)
                                ) {
                                    LiquidGlassTextButton(
                                        text = testActionLabel
                                            ?: if (islandEnabled) SettingsStr.testXiaomiSuperIsland() else SettingsStr.testLiveActivity(),
                                        onClick = { onSendTestNotification() },
                                        backdrop = rightPaneBackdrop,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }

                            TabletSettingsDest.Widget -> {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 36.dp)
                                        .navigationBarsPadding()
                                        .padding(bottom = 20.dp)
                                ) {
                                    LiquidGlassTextButton(
                                        text = SettingsStr.addToHomeScreen(),
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            showWidgetGuideDialog = true
                                        },
                                        backdrop = rightPaneBackdrop,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                OverlayDialog(
                                    title = SettingsStr.addWidgetDialogTitle(),
                                    show = showWidgetGuideDialog,
                                    liquidGlassBackdrop = glassBackdrop,
                                    onDismissRequest = { showWidgetGuideDialog = false }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = SettingsStr.widgetGuideSteps(),
                                            fontSize = 14.sp,
                                            lineHeight = 24.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        TextButton(
                                            text = CommonStr.done(),
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.Confirm
                                                )
                                                showWidgetGuideDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            // 右栏底部主操作：在录制层之外（本 Box 的上层），采样 rightPaneBackdrop
                            TabletSettingsDest.WebDav -> Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp)
                                    .navigationBarsPadding()
                                    .padding(bottom = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(Modifier.weight(1f)) {
                                    LiquidGlassTextButton(
                                        text = if (webDavBackingUp) ChromeStr.backingUp()
                                        else ChromeStr.backupToCloud(),
                                        onClick = { onWebDavBackup() },
                                        backdrop = rightPaneBackdrop,
                                        primary = true,
                                    )
                                }
                                Box(Modifier.weight(1f)) {
                                    LiquidGlassTextButton(
                                        text = if (webDavRestoring) ChromeStr.restoring()
                                        else ChromeStr.restoreFromCloud(),
                                        onClick = { onWebDavRestore() },
                                        backdrop = rightPaneBackdrop,
                                        primary = true,
                                    )
                                }
                            }

                            else -> {}
                        }
                    }
                    // 关于应用页内嵌且自绘顶栏糊层/遮罩，这里不再叠加设置页右栏顶部糊层
                    if (selected != TabletSettingsDest.About) {
                        TabletPaneTopChrome(
                            backdrop = rightPaneBackdrop,
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    }

                    // 右上角操作按钮：同样采样右栏本地 backdrop，作为 TabletPaneTopChrome 后的
                    // 兄弟绘制落在顶部糊层之上；出现/消失只动玻璃材质，图标常驻。
                    if (liquidGlassBackdrop != null) when (selected) {
                        TabletSettingsDest.Holiday -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(
                                            WindowInsetsSides.Top
                                        )
                                    )
                                    .padding(top = 6.dp, end = 16.dp)
                            ) {
                                if (holidayLoading) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = (-6).dp, y = (-4).dp)
                                            .size(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            progress = null,
                                        )
                                    }
                                } else {
                                    LiquidTopBarButton(
                                        onClick = { onHolidayUpdate() },
                                        backdrop = rightPaneBackdrop,
                                        icon = MiuixIcons.Normal.Update,
                                        contentDescription = CommonStr.update(),
                                        iconSize = 28.dp,
                                        backdropAlpha = rightMaskAlpha(),
                                        shadowAlpha = rightMaskAlpha(),
                                    )
                                }
                            }
                        }

                        TabletSettingsDest.WebDav -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(
                                            WindowInsetsSides.Top
                                        )
                                    )
                                    .padding(top = 6.dp, end = 16.dp)
                            ) {
                                LiquidTopBarButton(
                                    onClick = { onWebDavTestConnection() },
                                    backdrop = rightPaneBackdrop,
                                    icon = if (webDavConnected) MiuixIcons.Ok else MiuixIcons.Play,
                                    contentDescription = if (webDavConnected) SettingsStr.connected() else SettingsStr.testConnection(),
                                    iconTint = if (webDavConnected) Color(0xFF4CAF50) else Color.Unspecified,
                                    iconOffset = if (!webDavConnected) DpOffset(
                                        x = 2.dp,
                                        y = 0.dp
                                    ) else DpOffset.Zero,
                                    backdropAlpha = rightMaskAlpha(),
                                    shadowAlpha = rightMaskAlpha(),
                                )
                            }
                        }

                        else -> {}
                    }

                    // 上课时间：右栏叠编辑屏，从底部滑入/滑出（对齐 BlurBottomSheet 曲线时长，无透明度变化）
                    if (editingTimeConfig != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationY = timeEditorSlide.value * size.height
                                }
                        ) {
                            editingTimeConfig?.let { config ->
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    val editDensity = LocalDensity.current
                                    TimeConfigEditScreen(
                                        timeConfig = config,
                                        onBack = {
                                            uiScope.launch {
                                                timeEditorSlide.animateTo(
                                                    1f,
                                                    animationSpec = tween(
                                                        320,
                                                        easing = CubicBezierEasing(
                                                            0.34f,
                                                            1f,
                                                            0.3f,
                                                            1f
                                                        )
                                                    )
                                                )
                                                editingTimeConfig = null
                                                editingTimeRoutineId = null
                                            }
                                            uiScope.launch {
                                                delay(40.milliseconds) // 标题延迟 40ms 恢复
                                                TabletSettingsUiState.timeEditorOpen = false
                                            }
                                        },
                                        onSave = { savedConfig ->
                                            // 只替换这一个作息；节次骨架由一级页面管理，这里不碰。
                                            // 顶层 name 是用户在这一页改的作息名，要带回去。
                                            val saved = editingTimeRoutineId?.let { routineId ->
                                                courseRepository.saveRoutine(
                                                    routineId, savedConfig, savedConfig.name
                                                )
                                            } ?: false
                                            if (!saved) {
                                                // 保存被丢弃必须让用户看见：以前静默丢弃，表现成「改完保存、重开还是原来的」
                                                showToast(SettingsStr.saveFailedRetry())
                                            }
                                            timeConfigRefreshTrigger++
                                            uiScope.launch {
                                                timeEditorSlide.animateTo(
                                                    1f,
                                                    animationSpec = tween(
                                                        320,
                                                        easing = CubicBezierEasing(
                                                            0.34f,
                                                            1f,
                                                            0.3f,
                                                            1f
                                                        )
                                                    )
                                                )
                                                editingTimeConfig = null
                                            }
                                            uiScope.launch {
                                                delay(40.milliseconds) // 标题延迟 40ms 恢复
                                                TabletSettingsUiState.timeEditorOpen = false
                                            }
                                        },
                                        screenWidth = with(editDensity) { maxWidth.toPx() },
                                        screenHeight = with(editDensity) { maxHeight.toPx() },
                                        liquidGlassBackdrop = rightPaneBackdrop,
                                        routineId = editingTimeRoutineId ?: 0L,
                                        onDeleteRoutine = { routineId ->
                                            // 删除会换掉生效作息，重排提醒等都在 repository 里一并做完
                                            courseRepository.deleteRoutine(routineId)
                                            timeConfigRefreshTrigger++
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // 分界线画在 Row 之上：左栏内部有顶栏/底栏模糊层，画在栏内会被糊掉
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = leftWidth)
                .width(1.dp)
                .fillMaxHeight()
                .background(dividerColor)
        )
    }

    // 进入排班模式确认
    OverlayDialog(
        title = SettingsStr.enterShiftMode(),
        summary = SettingsStr.enterShiftModeSummary(),
        show = showShiftModeConfirmDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showShiftModeConfirmDialog = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showShiftModeConfirmDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = CommonStr.ok(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showShiftModeConfirmDialog = false
                        uiScope.launch {
                            delay(100.milliseconds)
                            onEnterShiftMode()
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 左栏单独卡片：课表导出 */
private val StandaloneDests = listOf(
    TabletSettingsDest.ScheduleExport,
)

/** 左栏「其他」分类下独立卡片：关于应用 / 捐赠支持 / 问题反馈 */
private val BottomMoreDests = listOf(
    TabletSettingsDest.About,
    TabletSettingsDest.Appreciate,
    TabletSettingsDest.Communication,
)

/** 左栏单个设置项：点击选中对应右栏内容 */
@Composable
private fun TabletLeftEntry(
    dest: TabletSettingsDest,
    isSelected: Boolean,
    onSelect: () -> Unit,
    /** 非空时在标题右侧渲染灰色提示；为空时必须不传 endActions —— 空 lambda 也会占 8dp 间距 */
    hint: L10n? = null,
) {
    if (hint == null) {
        ArrowPreference(
            title = dest.displayTitle(),
            holdDownState = isSelected,
            onClick = onSelect,
        )
    } else {
        ArrowPreference(
            title = dest.displayTitle(),
            holdDownState = isSelected,
            onClick = onSelect,
            endActions = {
                Text(
                    text = hint(),
                    fontSize = 14.5.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    textAlign = TextAlign.End,
                )
            },
        )
    }
}

/** 右栏：学期与周次 — 与手机设置页同等可编辑（日期/周数/新学期） */
@Composable
private fun TabletSemesterPane(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    shiftViewModel: ShiftViewModel,
    isShiftMode: Boolean,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop?,
    scrollBehavior: top.yukonga.miuix.kmp.layout.SharedScrollBehavior? = null,
    /** 请求进入排班模式（确认弹窗由外层持有） */
    onRequestEnterShiftMode: () -> Unit = {},
) {
    val hapticFeedback = LocalHapticFeedback.current
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val showNonCurrentWeek by settingsViewModel.showNonCurrentWeek.collectAsState()
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val isSemesterStarted by viewModel.isSemesterStarted.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()
    val scheduleSummaries by scheduleViewModel.scheduleSummaries.collectAsState()
    val shiftSelectedSchedules by shiftViewModel.shiftSelectedSchedules.collectAsState()

    var showStartDateDialog by remember { mutableStateOf(false) }
    var showCurrentWeekDialog by remember { mutableStateOf(false) }
    var showTotalWeeksDialog by remember { mutableStateOf(false) }
    var showNewSemesterDialog by remember { mutableStateOf(false) }
    var newSemesterName by remember { mutableStateOf("") }
    val (tempYearInit, tempMonthInit, tempDayInit) = remember(classStartTime) {
        parseDate(
            classStartTime
        )
    }
    var tempYear by remember { mutableIntStateOf(tempYearInit) }
    var tempMonth by remember { mutableIntStateOf(tempMonthInit) }
    var tempDay by remember { mutableIntStateOf(tempDayInit) }
    var tempCurrentWeek by remember { mutableIntStateOf(currentWeek) }
    var tempTotalWeeks by remember { mutableIntStateOf(totalWeeks) }

    val chromeTop = mainWindowTopInset() +
            top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults.CollapsedHeight +
            12.dp

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else Modifier
            ),
        // 学期页无自带顶栏 inset，这里对齐折叠标题下方
        contentPadding = PaddingValues(
            start = 20.dp,
            top = chromeTop + 12.dp,
            end = 20.dp,
            bottom = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = SettingsStr.startDate(),
                        endActions = {
                            Text(
                                text = classStartTime,
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                textAlign = TextAlign.End,
                            )
                        },
                        onClick = {
                            val (y, m, d) = parseDate(classStartTime)
                            tempYear = y
                            tempMonth = m
                            tempDay = d
                            showStartDateDialog = true
                        },
                        holdDownState = showStartDateDialog
                    )
                    ArrowPreference(
                        title = SettingsStr.currentWeek(),
                        endActions = {
                            Text(
                                text = when {
                                    !isSemesterStarted -> SettingsStr.notStarted()
                                    currentWeek > totalWeeks -> SettingsStr.onBreak()
                                    else -> CommonStr.weekN(currentWeek)
                                },
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                textAlign = TextAlign.End,
                            )
                        },
                        onClick = {
                            tempCurrentWeek = currentWeek.coerceAtMost(totalWeeks)
                            showCurrentWeekDialog = true
                        },
                        holdDownState = showCurrentWeekDialog
                    )
                    ArrowPreference(
                        title = SettingsStr.totalWeeksTitle(),
                        endActions = {
                            Text(
                                text = SettingsStr.totalWeeksValue(totalWeeks),
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                textAlign = TextAlign.End,
                            )
                        },
                        onClick = {
                            tempTotalWeeks = totalWeeks
                            showTotalWeeksDialog = true
                        },
                        holdDownState = showTotalWeeksDialog
                    )
                    SwitchPreference(
                        title = SettingsStr.smartWeekend(),
                        summary = SettingsStr.smartWeekendSummary(),
                        checked = smartWeekend,
                        onCheckedChange = { settingsViewModel.setSmartWeekend(it) }
                    )
                    CardExpand(visible = !isShiftMode) {
                        SwitchPreference(
                            title = SettingsStr.showNonCurrentWeek(),
                            checked = showNonCurrentWeek,
                            onCheckedChange = { settingsViewModel.setShowNonCurrentWeek(it) }
                        )
                    }
                }
            }
        }
        if (isShiftMode) {
            // 排班模式：选择要对比的课表
            item(key = "shift_schedules") {
                SmallTitle(
                    text = SettingsStr.selectCompareSchedules(),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        scheduleNames.forEach { name ->
                            CheckboxPreference(
                                title = name,
                                summary = scheduleSummaries[name] ?: "",
                                checked = name in shiftSelectedSchedules,
                                onCheckedChange = { checked ->
                                    val newList = if (checked) {
                                        shiftSelectedSchedules + name
                                    } else {
                                        shiftSelectedSchedules - name
                                    }
                                    shiftViewModel.setShiftSelectedSchedules(newList)
                                },
                                checkboxLocation = CheckboxLocation.End
                            )
                        }
                    }
                }
            }
        }
        // 开启新学期：始终在页面最底部，独立卡片（排班模式不显示）
        if (!isShiftMode) {
            item(key = "new_semester") {
                SmallTitle(
                    text = SettingsStr.otherActions(),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = SettingsStr.shiftMode(),
                            summary = SettingsStr.shiftModeSummary(),
                            onClick = onRequestEnterShiftMode,
                        )
                        ArrowPreference(
                            title = SettingsStr.newSemester(),
                            summary = SettingsStr.newSemesterSummary(),
                            onClick = {
                                newSemesterName = ""
                                showNewSemesterDialog = true
                            },
                            holdDownState = showNewSemesterDialog
                        )
                    }
                }
            }
        }
    }

    // 开启新学期
    OverlayDialog(
        title = SettingsStr.newSemester(),
        summary = SettingsStr.newSemesterDialogSummary(),
        show = showNewSemesterDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = {
            showNewSemesterDialog = false
            newSemesterName = ""
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            top.yukonga.miuix.kmp.basic.NativeMiuixTextField(
                value = newSemesterName,
                onValueChange = { newSemesterName = it },
                label = SettingsStr.newScheduleNameLabel(),
                modifier = Modifier.fillMaxWidth(),
                requestFocus = showNewSemesterDialog
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showNewSemesterDialog = false
                        newSemesterName = ""
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = SettingsStr.create(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (newSemesterName.isNotBlank()) {
                            val name = newSemesterName
                            if (name in scheduleNames) {
                                showToast(SettingsStr.duplicateNameToast())
                            } else {
                                scheduleViewModel.createNewSemesterSchedule(name)
                                showNewSemesterDialog = false
                                newSemesterName = ""
                                showToast(SettingsStr.createdToast(name))
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 开始上课日期
    OverlayDialog(
        title = SettingsStr.startDate(),
        show = showStartDateDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showStartDateDialog = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val maxDaysInMonth =
                remember(tempYear, tempMonth) { getDaysInMonth(tempYear, tempMonth) }
            LaunchedEffect(maxDaysInMonth) {
                if (tempDay > maxDaysInMonth) tempDay = maxDaysInMonth
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberPicker(
                    value = tempYear,
                    onValueChange = { tempYear = it },
                    range = 2024..2030,
                    visibleItemCount = 3,
                    itemHeight = 60.dp,
                    textStyle = MiuixTheme.textStyles.title2,
                    modifier = Modifier.weight(1f)
                )
                NumberPicker(
                    value = tempMonth,
                    onValueChange = { tempMonth = it },
                    range = 1..12,
                    visibleItemCount = 3,
                    itemHeight = 60.dp,
                    label = { SettingsStr.monthN(it) },
                    wrapAround = true,
                    textStyle = MiuixTheme.textStyles.title2,
                    modifier = Modifier.weight(1f)
                )
                NumberPicker(
                    value = tempDay,
                    onValueChange = { tempDay = it },
                    range = 1..maxDaysInMonth,
                    visibleItemCount = 3,
                    itemHeight = 60.dp,
                    label = { CommonStr.dateN(it) },
                    wrapAround = true,
                    textStyle = MiuixTheme.textStyles.title2,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showStartDateDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = CommonStr.ok(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        val date = "${tempYear.toString().padStart(4, '0')}/${
                            tempMonth.toString().padStart(2, '0')
                        }/${tempDay.toString().padStart(2, '0')}"
                        viewModel.setClassStartTime(date)
                        showStartDateDialog = false
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 当前周数
    OverlayDialog(
        title = SettingsStr.selectCurrentWeek(),
        show = showCurrentWeekDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showCurrentWeekDialog = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NumberPicker(
                value = tempCurrentWeek,
                onValueChange = { tempCurrentWeek = it },
                range = 1..totalWeeks.coerceAtLeast(1),
                visibleItemCount = 3,
                itemHeight = 60.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showCurrentWeekDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = CommonStr.ok(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        viewModel.setCurrentWeek(tempCurrentWeek)
                        showCurrentWeekDialog = false
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 总周数
    OverlayDialog(
        title = SettingsStr.selectTotalWeeks(),
        show = showTotalWeeksDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showTotalWeeksDialog = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NumberPicker(
                value = tempTotalWeeks,
                onValueChange = { tempTotalWeeks = it },
                range = 1..30,
                visibleItemCount = 3,
                itemHeight = 60.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showTotalWeeksDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = CommonStr.ok(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        viewModel.setTotalWeeks(tempTotalWeeks)
                        showTotalWeeksDialog = false
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 右栏：节假日（状态由 HolidayManager 维护）。顶部「更新」联网拉取。 */
@Composable
private fun TabletHolidayPane(
    scrollBehavior: SharedScrollBehavior?,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop?,
    onUpdateReady: (() -> Unit) -> Unit = {},
    onLoadingChange: (Boolean) -> Unit = {},
    onTeachingWeekReorganizationsChanged: () -> Unit = {},
    // 节假日数据变更是 Android 专有的平台副作用（提醒 / 勿扰 / 小组件），由宿主注入
    onHolidayDataChanged: () -> Unit = {},
    // 节假日 API 拉取由宿主实现（Android 联网，10s 超时）；null 时返回 emptyList
    fetchHolidayEntries: (suspend (year: Int) -> List<HolidayEntry>)? = null,
) {
    val scope = rememberCoroutineScope()
    val currentDate = remember { todayLocalDate() }
    var year by remember { mutableIntStateOf(currentDate.year) }
    var entries by remember { mutableStateOf(HolidayManager.load(year)) }
    var loading by remember { mutableStateOf(false) }
    val latestYear by rememberUpdatedState(year)
    val holidayRevision by HolidayManager.dataRevision.collectAsState()
    LaunchedEffect(holidayRevision) {
        if (holidayRevision != 0L) entries = HolidayManager.load(latestYear)
    }

    val doUpdate = {
        if (!loading) {
            loading = true
            scope.launch(ioDispatcher) {
                val targetYear = latestYear
                val result = runCatching {
                    fetchHolidayEntries?.invoke(targetYear) ?: emptyList()
                }.getOrDefault(emptyList())
                withContext(ioDispatcher) {
                    val merged = HolidayManager.mergeApiEntries(targetYear, result)
                    entries = HolidayManager.load(latestYear)
                    loading = false
                    if (merged && result.isNotEmpty()) {
                        // API 合并同样要重排提醒并刷小部件，不能只改本地 SP
                        onHolidayDataChanged()
                    }
                    val message =
                        when {
                            result.isEmpty() -> SettingsStr.fetchFailedNoData()
                            !merged -> SettingsStr.localDataError()
                            else -> SettingsStr.updatedNRecords(result.size)
                        }
                    showToast(message)
                }
            }
        }
    }
    val latestUpdate by rememberUpdatedState(doUpdate)
    LaunchedEffect(Unit) { onUpdateReady { latestUpdate() } }
    LaunchedEffect(loading) { onLoadingChange(loading) }

    // 与手机端同一套两段式：年度安排 / 其他操作，横向平移翻页
    var selectedTab by remember { mutableIntStateOf(0) }
    val pageSwitch = rememberPageSwitchState(0)
    LaunchedEffect(selectedTab) { pageSwitch.animateTo(selectedTab) }
    var pillFreezeKey by remember { mutableIntStateOf(0) }
    var pillAlignKey by remember { mutableIntStateOf(0) }
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPageSwitch(
            state = pageSwitch,
            pageCount = 2,
            modifier = Modifier.fillMaxSize(),
            onGestureStart = { pillFreezeKey++ },
            onGestureEnd = { page ->
                selectedTab = page
                pillAlignKey++
            },
        ) { page ->
            HolidaySettingsScreen(
                scrollBehavior = scrollBehavior,
                liquidGlassBackdrop = liquidGlassBackdrop,
                year = year,
                entries = entries,
                onYearChange = { y ->
                    year = y
                    entries = HolidayManager.load(y)
                },
                reload = { entries = HolidayManager.load(year) },
                onTeachingWeekReorganizationsChanged = onTeachingWeekReorganizationsChanged,
                onHolidayDataChanged = {
                    onHolidayDataChanged()
                },
                tab = page,
                topExtra = 64.dp,
                syncing = loading,
                onSync = { doUpdate() },
            )
        }
        val backdrop = liquidGlassBackdrop
        if (backdrop != null) {
            CapsuleSegmentedControl(
                options = listOf(
                    HolidayStr.tabAnnual(),
                    HolidayStr.tabOther(),
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                selectOnPress = true,
                freezeKey = pillFreezeKey,
                alignKey = pillAlignKey,
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .padding(horizontal = 20.dp),
            )
        }
    }
}
