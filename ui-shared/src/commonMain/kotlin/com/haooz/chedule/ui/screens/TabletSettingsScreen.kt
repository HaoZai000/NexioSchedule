package com.haooz.chedule.ui.screens

// ⚠ 本文件不得出现 android.* / androidx.activity；平台能力全部经 SettingsContentHost 注入。

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.chedule.ui.components.LocalPageStartPadding
import com.haooz.chedule.ui.components.tabletNavChromeTitleSlot
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.settings.SettingsCategory
import com.haooz.chedule.ui.settings.SettingsCategoryBadge
import com.haooz.chedule.ui.settings.DataSourcesSectionCard
import com.haooz.chedule.ui.settings.SyncWebDavEntryCard
import com.haooz.chedule.ui.settings.SettingsCategoryList
import com.haooz.chedule.ui.settings.SettingsDirectPane
import com.haooz.chedule.ui.settings.SettingsContentHost
import com.haooz.chedule.ui.settings.rememberSettingsAccentResolver
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 平板设置选中项。`MainActivity` 叠层读它画右栏固定标题。
 *
 * 只在页面存活期间有意义，不做 save/restore（与改造前一致）。
 */
object TabletSettingsUiState {
    var selectedCategory by mutableStateOf(SettingsCategory.Course)
}

/**
 * 画在 MainActivity 层的设置页固定标题。左「设置」恒定，右为当前分类名。
 * 水平位置由 [tabletNavChromeTitleSlot] 按左栏 0.39 分栏各自居中。
 */
@Composable
fun TabletSettingsChromeOverlay() {
    val selected = TabletSettingsUiState.selectedCategory
    val statusBar = mainWindowTopInset()
    val collapsedH = CollapsibleTopAppBarDefaults.CollapsedHeight
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

        // 「关于」页内嵌时自带自绘顶栏（ProgressiveBlurTopBar + CollapsibleTopAppBar），
        // 设置页叠加层就不再画右栏标题，否则同一条线上会重叠两份标题。
        if (selected != SettingsCategory.About) {
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
                collapsedTitle(selected.title())
            }
        }
    }
}

/** 当前窗口顶部系统内边距。用 systemBars 仅取顶部：分屏/自由窗口下 statusBars 顶部为 0。 */
@Composable
private fun mainWindowTopInset(): Dp =
    WindowInsets.systemBars.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()

/** pad 设置左右栏顶部糊层高度：80.dp + 顶部内边距 */
private val TabletPaneBlurHeight: Dp
    @Composable get() = 80.dp + mainWindowTopInset()

/** 本栏顶部渐变模糊常驻；糊层采样本栏本地 backdrop（兄弟节点），避免与全局层循环采样。 */
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
 * 平板设置：左栏是分类卡片（与手机主页同一套 `SettingsCategory.homeGroups`），
 * 右栏渲染该分类的内容（与手机分类页共用 `SettingsCategoryList`）。
 *
 * 右栏分两类：
 * - **直达型分类**（提醒 / 小组件 / 关于 / 捐赠）内嵌真实页面，不压栈 —— 压全屏会盖掉左栏，分栏就断了；
 * - **容器型分类**里跨模块的功能条目（课程时间管理 / 节假日 / WebDAV / 交流反馈）走**全屏路由压栈**。
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
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    showUpdateSettings: Boolean = true,
    hasLocationPermission: () -> Boolean = { false },
    onInvalidateWeatherCache: () -> Unit = {},
    onSetTaskExcludedFromRecents: (Boolean) -> Unit = {},
    /** 更新能力控制器（Android 注入；「更新」二级页内嵌用） */
    updateController: com.haooz.chedule.data.platform.UpdateController =
        com.haooz.chedule.data.platform.NoopUpdateController,
    /** 备份文件能力（Android=SAF）——「备份与同步」二级页内嵌本地备份用 */
    backupFileController: com.haooz.chedule.data.backup.BackupFileController =
        com.haooz.chedule.data.backup.NoopBackupFileController,
    /** 导入/导出平台能力包（Android 注入 SAF/口令/日历/手环）——「数据管理」二级页内嵌用 */
    dataManage: com.haooz.chedule.ui.settings.DataManagePlatform =
        com.haooz.chedule.ui.settings.DataManagePlatform(),
    /** 直达型分类（提醒/小组件/关于）内嵌所需的平台能力 */
    directPlatform: com.haooz.chedule.ui.settings.SettingsDirectPlatform =
        com.haooz.chedule.ui.settings.SettingsDirectPlatform(),
    /** 全量恢复成功后的平台副作用（重排提醒） */
    onHolidayDataChanged: () -> Unit = {},
) {
    val router = LocalAppRouter.current
    val selected = TabletSettingsUiState.selectedCategory
    val resolver = rememberSettingsAccentResolver()
    // 左栏与手机主页同一套：只有「课表」行带 summary
    val scheduleName by scheduleViewModel.currentScheduleName.collectAsState()
    val scheduleSummaries by scheduleViewModel.scheduleSummaries.collectAsState()
    val courseSummary = scheduleSummaries[scheduleName]?.let { "$scheduleName，$it" }
        ?: scheduleName.takeIf { it.isNotBlank() }

    val host = remember(
        viewModel,
        scheduleViewModel,
        settingsViewModel,
        shiftViewModel,
        isShiftMode,
        liquidGlassBackdrop,
        router,
        // 平台能力包进 key：宿主替换实例后（如超级岛开关变化重建 directPlatform）要跟着换
        updateController,
        backupFileController,
        dataManage,
        directPlatform,
    ) {
        SettingsContentHost(
            courseViewModel = viewModel,
            scheduleViewModel = scheduleViewModel,
            settingsViewModel = settingsViewModel,
            shiftViewModel = shiftViewModel,
            isShiftMode = isShiftMode,
            liquidGlassBackdrop = liquidGlassBackdrop,
            hasLocationPermission = hasLocationPermission,
            onInvalidateWeatherCache = onInvalidateWeatherCache,
            onSetTaskExcludedFromRecents = onSetTaskExcludedFromRecents,
            updateController = updateController,
            backupFileController = backupFileController,
            dataManage = dataManage,
            directPlatform = directPlatform,
            onHolidayDataChanged = onHolidayDataChanged,
            onExitShiftMode = onExitShiftMode,
            onEnterShiftMode = onEnterShiftMode,
            onNavigate = { route -> router.navigate(route) },
        )
    }

    val paneHorizontal = 20.dp
    // 顶栏折叠标题高度：左右内容都从这条线下方开始，避免被 MainActivity 叠层标题压住
    val chromeTop = mainWindowTopInset() + CollapsibleTopAppBarDefaults.CollapsedHeight + 12.dp
    val leftListState = rememberLazyListState()
    val leftGroups = SettingsCategory.homeGroups(
        showUpdateSettings = showUpdateSettings,
        isShiftMode = isShiftMode,
    )
    val rightListState = remember(selected) { LazyListState() }

    // 页面底色铺到屏幕边缘（含侧栏下），内容整体避让
    val paneSurface = MiuixTheme.colorScheme.surface
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(paneSurface)
            .then(LocalPageStartPadding.current)
    ) {
        val leftWidth = screenWidthDp().dp * 0.39f
        val rightWidth = maxWidth - leftWidth
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
            // —— 左栏：分类卡片 ——
            Box(
                modifier = Modifier
                    .width(leftWidth)
                    .fillMaxHeight()
                    .background(surfaceColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (AppMaterialSettings.isNoneTier()) Modifier
                            else Modifier.layerBackdrop(leftPaneBackdrop)
                        )
                ) {
                    LazyColumn(
                        state = leftListState,
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
                        // 一组一张卡，卡间距走卡片下边距 12dp，与手机主页同一套规则
                    ) {
                        leftGroups.forEachIndexed { index, group ->
                            item(key = "g_$index") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    insideMargin = PaddingValues(0.dp),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        group.forEach { category ->
                                            val isSelected = !isShiftMode && category == selected
                                            ArrowPreference(
                                                title = category.title(),
                                                // 只有「课表」行带 summary：课表名 + 周次/节次
                                                summary = if (category == SettingsCategory.Course) {
                                                    courseSummary
                                                } else {
                                                    null
                                                },
                                                // 选中态用「按住高亮」的底色（与旧平板左栏一致），不改标题色
                                                holdDownState = isSelected,
                                                startAction = {
                                                    SettingsCategoryBadge(
                                                        category = category,
                                                        resolver = resolver,
                                                    )
                                                },
                                                onClick = {
                                                    // 左栏点击一律原地切换右栏内容，不压路由 ——
                                                    // 压全屏会把左栏盖掉，分栏就断了。
                                                    // 容器型渲染二级列表，直达型（提醒/小组件/关于/捐赠）内嵌真实页面。
                                                    if (!isShiftMode) {
                                                        TabletSettingsUiState.selectedCategory = category
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 排班模式的入口与「退出」都由右栏课表内容渲染（与手机同一份实现），
                        // 左栏不再单独放「模式」卡，避免两处入口各自为政。
                    }
                }
                TabletPaneTopChrome(
                    backdrop = leftPaneBackdrop,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }

            // —— 右栏：选中分类的二级页 ——
            Box(
                modifier = Modifier
                    .width(rightWidth)
                    .fillMaxHeight()
                    .background(surfaceColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (AppMaterialSettings.isNoneTier()) Modifier
                            else Modifier.layerBackdrop(rightPaneBackdrop)
                        )
                ) {
                    when (selected) {
                        SettingsCategory.Reminder,
                        SettingsCategory.Widget,
                        SettingsCategory.About,
                        SettingsCategory.Donate,
                        -> {
                            // 直达型分类：右栏内嵌真实页面（手机端仍是压路由的全屏页）。
                            // 传本栏采样层：右栏底部玻璃按钮不能采样全局层（会循环采样）
                            SettingsDirectPane(
                                category = selected,
                                host = host,
                                paneBackdrop = rightPaneBackdrop,
                            )
                        }

                        SettingsCategory.Update -> {
                            // 「更新」二级页整页内联更新设置 + 历史日志，与手机同一份实现
                            com.haooz.chedule.ui.activities.UpdateSettingsScreen(
                                scrollBehavior = null,
                                liquidGlassBackdrop = host.liquidGlassBackdrop,
                                updateController = host.updateController,
                                changelogInline = true,
                            )
                        }

                        SettingsCategory.Sync -> {
                            // 「备份与同步」= 本地备份整页内容 + 页尾「同步」组 WebDAV 入口
                            com.haooz.chedule.ui.activities.LocalBackupScreen(
                                scrollBehavior = null,
                                liquidGlassBackdrop = host.liquidGlassBackdrop,
                                backupFileController = host.backupFileController,
                                onHolidayDataChanged = host.onHolidayDataChanged,
                                listHeader = { SmallTitle(text = SettingsStr.destLocalBackup()) },
                                listFooter = { SyncWebDavEntryCard(host) },
                            )
                        }

                        SettingsCategory.Data -> {
                            // 「数据管理」= 智能导入 + 导入 + 导出整页内联 + 页尾「数据源」分组。
                            // 与手机**完全同一份实现**：右栏不再走「平板精简版」（曾把口令/文件拆成
                            // 两个小标题并砍掉智能导入，导致平板少条目）。
                            com.haooz.chedule.ui.activities.BackupAndMigrationScreen(
                                scrollBehavior = null,
                                liquidGlassBackdrop = host.liquidGlassBackdrop,
                                mode = com.haooz.chedule.ui.activities.ScheduleDataManageMode.Import,
                                alsoExport = true,
                                scheduleFileController = host.dataManage.scheduleFileController,
                                onHolidayDataChanged = host.onHolidayDataChanged,
                                onFetchShareCode = host.dataManage.onFetchShareCode,
                                onImportThirdParty = host.dataManage.onImportThirdParty,
                                onShareSchedule = host.dataManage.onShareSchedule,
                                onExportToWearable = host.dataManage.onExportToWearable,
                                onSyncToCalendar = host.dataManage.onSyncToCalendar,
                                listFooter = { DataSourcesSectionCard(host) },
                            )
                        }

                        else -> {
                            SettingsCategoryList(
                                category = selected,
                                host = host,
                                contentPadding = PaddingValues(
                                    start = paneHorizontal,
                                    top = chromeTop,
                                    end = paneHorizontal,
                                    bottom = 60.dp,
                                ),
                                state = rightListState,
                            )
                        }
                    }
                }
                // 关于页内嵌时自绘顶栏糊层/遮罩（ProgressiveBlurTopBar + 自己的 CollapsibleTopAppBar），
                // 这里不再叠加设置页右栏顶部糊层，否则会多出一层顶栏背景。
                if (selected != SettingsCategory.About) {
                    TabletPaneTopChrome(
                        backdrop = rightPaneBackdrop,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
            }
        }

        // 分界线画在 Row 之外（否则会被两栏的模糊层糊掉）
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(
                    if (isAppDarkTheme()) Color.White.copy(alpha = 0.1f)
                    else Color.Black.copy(alpha = 0.06f)
                )
                .offset(x = leftWidth)
        )
    }
}
