package com.haooz.chedule.ui.screens

// ⚠ 本文件不得出现 android.* / androidx.activity / androidx.core；平台能力做成注入参数。

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.util.lengthOfMonth
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.ic_settings_about
import com.haooz.chedule.uishared.resources.ic_settings_data
import com.haooz.chedule.uishared.resources.ic_settings_feedback
import com.haooz.chedule.uishared.resources.ic_settings_personalization
import com.haooz.chedule.uishared.resources.ic_settings_reminders
import com.haooz.chedule.uishared.resources.ic_settings_update
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.TopBarMaterialThreshold
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// 解析失败时回退到今天，避免弹窗初值出现非法日期
internal fun parseDate(dateStr: String): Triple<Int, Int, Int> {
    return try {
        val parts = dateStr.split("/")
        Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    } catch (_: Exception) {
        // kotlinx-datetime 替代 JVM 的 java.util.Calendar
        val d = todayLocalDate()
        Triple(d.year, d.monthNumber, d.dayOfMonth)
    }
}

internal fun getDaysInMonth(year: Int, month: Int): Int {
    return try {
        LocalDate(year, month, 1).lengthOfMonth()
    } catch (_: Exception) {
        31
    }
}

/** 带图标的设置入口行：[expanded] 非空为展开项，空为二级页入口；[summary] 为空时只画标题。 */
@Composable
private fun SettingsEntryRow(
    title: String,
    summary: String? = null,
    icon: @Composable () -> Unit,
    expanded: Boolean? = null,
    endText: String? = null,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded == true) 90f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "settingsEntryRotation",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(start = 16.dp, end = 13.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (endText != null) {
            Text(
                text = endText,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            modifier = Modifier
                .size(17.dp)
                .graphicsLayer { rotationZ = rotation },
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}

/** 设置入口图标底板：28dp 方块 + 8dp 连续圆角，底色取色板（莫奈取色时统一 primary）。 */
@Composable
internal fun SettingsEntryIcon(accent: Color, content: @Composable (Color) -> Unit) {
    val resolver = rememberSettingsAccentResolver()
    val shape = remember { ContinuousRoundedRectangle(8.dp) }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(shape)
            .background(resolver.background(accent)),
        contentAlignment = Alignment.Center,
    ) {
        content(resolver.foreground(accent))
    }
}

/** 矢量图标。 */
@Composable
internal fun VectorEntryIcon(imageVector: ImageVector, accent: Color) {
    SettingsEntryIcon(accent) { tint ->
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
    }
}

/** composeResources 里的自定义图标。 */
@Composable
internal fun DrawableEntryIcon(resource: DrawableResource, accent: Color) {
    SettingsEntryIcon(accent) { tint ->
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

/** 课程表图标。 */
@Composable
internal fun ScheduleSettingsIcon() {
    VectorEntryIcon(MiuixIcons.Months, SettingsAccents.Blue)
}

/** 课程提醒图标。 */
@Composable
internal fun CourseReminderIcon() {
    DrawableEntryIcon(Res.drawable.ic_settings_reminders, SettingsAccents.Red)
}

/** 桌面小部件图标。 */
@Composable
internal fun HomeScreenWidgetIcon() {
    VectorEntryIcon(MiuixIcons.All, SettingsAccents.Green)
}

/** 导入导出图标。 */
@Composable
internal fun ImportExportIcon() {
    DrawableEntryIcon(Res.drawable.ic_settings_data, SettingsAccents.Orange)
}

/** 备份与同步图标。 */
@Composable
internal fun BackupSyncIcon() {
    VectorEntryIcon(MiuixIcons.CloudFill, SettingsAccents.Blue)
}

/** 应用个性化图标。 */
@Composable
internal fun PersonalizationIcon() {
    DrawableEntryIcon(Res.drawable.ic_settings_personalization, SettingsAccents.Purple)
}

/** 更新图标。 */
@Composable
internal fun UpdateSettingsIcon() {
    DrawableEntryIcon(Res.drawable.ic_settings_update, SettingsAccents.Green)
}

/** 关于应用图标。 */
@Composable
internal fun AboutAppIcon() {
    DrawableEntryIcon(Res.drawable.ic_settings_about, SettingsAccents.Gray)
}

/** 捐赠支持图标。 */
@Composable
internal fun DonateIcon() {
    VectorEntryIcon(MiuixIcons.FavoritesFill, SettingsAccents.Red)
}

/** 问题反馈图标。 */
@Composable
internal fun CommunicationIcon() {
    DrawableEntryIcon(Res.drawable.ic_settings_feedback, SettingsAccents.Blue)
}

@Composable
fun SettingsScreen(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    shiftViewModel: ShiftViewModel,
    isShiftMode: Boolean = false,
    onExitShiftMode: () -> Unit = {},
    onEnterShiftMode: () -> Unit = {},
    navBarStyle: String = "standard",
    settingsScrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
    /** 搜索状态（由路由持有：搜索框挂在页面采样层外，与列表共享同一份状态） */
    searchState: SettingsSearchState? = null,
    // ── 平台注入（宿主提供；桌面/iOS 用默认空实现）──
    // 平板两栏外壳的 8 个平台专有 dest 接线留在宿主，本页只负责转交槽位。
    /** 平台专有设置面板（8 个 dest）由宿主渲染；null 显示占位 */
    androidPane: (@Composable (TabletSettingsDest) -> Unit)? = null,
    /** 提醒页「测试通知」动作（Android 发超级岛/实时活动） */
    onSendTestNotification: () -> Unit = {},
    /** 是否超级岛设备（决定「测试通知」按钮文案） */
    isIslandSupported: () -> Boolean = { false },
    /**
     * 提醒页底部测试按钮文案；null = 按 [isIslandSupported] 走平台默认文案
     * （超级岛设备「测试小米超级岛」/ 其余「测试实时活动」）。
     * iOS 传「测试灵动岛 / 测试通知」，与原生路由页同一套文案。
     */
    testActionLabel: String? = null,
    /** 小部件页「刷新已装小部件」（Android = WidgetUpdateCache） */
    onRefreshWidgets: () -> Unit = {},
    /** 节假日数据变更副作用（重排提醒 + 刷小部件） */
    onHolidayDataChanged: () -> Unit = {},
    /** 节假日 API 拉取（Android 宿主实现）；null 时返回 emptyList */
    fetchHolidayEntries: (suspend (year: Int) -> List<HolidayEntry>)? = null,
    /**
     * 是否显示「更新」入口。
     */
    showUpdateSettings: Boolean = true,
    /**
     * 是否显示「排班模式」入口。
     * ⚠ 传 false 时 `isShiftMode` 必为 false，只需挡「进入」两处，「退出」也一并挡掉以防状态被外部置起。
     */
) {
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val isSemesterStarted by viewModel.isSemesterStarted.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()
    // 不可在 forEach 内 collectAsState：remember 槽位会随课表增删错位
    val scheduleSummaries by scheduleViewModel.scheduleSummaries.collectAsState()
    val shiftSelectedSchedules by shiftViewModel.shiftSelectedSchedules.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    // LocalAppRouter.current 是 @Composable 调用，必须在组合层取（各入口共用）
    val settingsRouter = LocalAppRouter.current


    // 平板：设置页左右两栏，原地静态切换
    if (navBarStyle == "rail") {
        TabletSettingsScreen(
            viewModel = viewModel,
            scheduleViewModel = scheduleViewModel,
            settingsViewModel = settingsViewModel,
            shiftViewModel = shiftViewModel,
            isShiftMode = isShiftMode,
            onExitShiftMode = onExitShiftMode,
            onEnterShiftMode = onEnterShiftMode,
            liquidGlassBackdrop = liquidGlassBackdrop,
            // —— 8 个平台专有 dest，由宿主 androidPane 槽位渲染 ——
            androidPane = androidPane,
            onSendTestNotification = onSendTestNotification,
            isIslandSupported = isIslandSupported,
            testActionLabel = testActionLabel,
            onRefreshWidgets = onRefreshWidgets,
            onHolidayDataChanged = onHolidayDataChanged,
            fetchHolidayEntries = fetchHolidayEntries,
            // iOS 走 App Store 更新：入口整个不渲染（平板左栏也要挡）
            showUpdateSettings = showUpdateSettings,
        )
        return
    }
    val isDark = isAppDarkTheme()
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp
    val nativeChromeInset = LocalHostNavigation.current
        ?.takeIf { it.showsNativeTabBar && !isTablet }
        ?.bottomBarInset?.toFloat()?.dp

    val search = searchState ?: remember { SettingsSearchState() }
    val searchQuery = search.query
    val searchExpanded = search.expanded
    val searchEnabled = !isShiftMode
    // 点开搜索框时收起大标题（与教务导入页同一交互）
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) settingsScrollBehavior?.collapse() else search.query = ""
    }

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        val listState = rememberLazyListState()
        // 折叠高度在布局阶段补齐，避免组合期读 currentHeightPx 导致动画每帧重组
        val scrollBehaviorModifier = remember(settingsScrollBehavior) {
            settingsScrollBehavior?.let {
                Modifier
                    .collapsibleTopInset(it)
                    .nestedScroll(it.nestedScrollConnection)
            } ?: Modifier
        }
        val searchResults = if (searchExpanded) searchSettings(searchQuery) else emptyList()
        // 搜索时设置项整体淡出，让位给结果层
        val contentAlpha by animateFloatAsState(
            targetValue = if (searchExpanded) 0f else 1f,
            animationSpec = tween(durationMillis = 180),
            label = "settingsSearchContentAlpha",
        )
        // 材质出现时机与全站统一（滚过 10dp），不再「动 2px 就亮」。
        // 搜索框在页面采样层外（路由的 headerOverlay），这里只把「是否滚过阈值」告诉它。
        val materialThresholdPx = with(LocalDensity.current) { TopBarMaterialThreshold.toPx() }
        LaunchedEffect(listState, search, materialThresholdPx) {
            snapshotFlow {
                listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > materialThresholdPx
            }.collect { search.listScrolled = it }
        }
        val listTopPadding = paddingValues.calculateTopPadding() +
            CollapsibleTopAppBarDefaults.CollapsedHeight +
            pageTopExtra(isTablet, hasSmallTitle = false) +
            (if (searchEnabled) SettingsSearchBarBlockHeight else 0.dp)

        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(
                    hapticFeedbackType = HapticFeedbackType.TextHandleMove
                )
                .graphicsLayer { alpha = contentAlpha }
                // 采样连接必须最贴近列表：顺序与备份页 / 节假日页一致。
                // 反了（nestedScroll 在外、overScroll 在内）会让越界拉伸算进 consumed，
                // 把 contentOffset 顶成正值，之后要先把这截「还清」材质才亮 —— 就是「滑挺远才出玻璃」。
                .then(scrollBehaviorModifier),
            contentPadding = PaddingValues(
                start = tabletHorizontalPadding,
                top = listTopPadding,
                end = tabletHorizontalPadding,
                bottom = nativeChromeInset?.plus(24.dp) ?: 60.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "basic") {
                val basicSummary = when {
                    !isSemesterStarted -> SettingsStr.scheduleSettingsIdleSummary(
                        classStartTime, SettingsStr.notStarted(), totalWeeks
                    )
                    currentWeek > totalWeeks -> SettingsStr.scheduleSettingsIdleSummary(
                        classStartTime, SettingsStr.onBreak(), totalWeeks
                    )
                    else -> SettingsStr.scheduleSettingsSummary(
                        classStartTime, currentWeek, totalWeeks
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    SettingsEntryRow(
                        title = SettingsStr.scheduleSettings(),
                        summary = basicSummary,
                        icon = { ScheduleSettingsIcon() },
                        expanded = null,
                        onClick = {
                            settingsRouter.navigate(AppRoute.ScheduleSettings)
                        },
                    )
                }
            }

            if (!isShiftMode) {
                item(key = "features") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsEntryRow(
                                title = SettingsStr.courseReminder(),
                                icon = { CourseReminderIcon() },
                                onClick = {
                                    FeatureLog.reminder("open")
                                    settingsRouter.navigate(AppRoute.CourseReminder)
                                }
                            )
                            SettingsEntryRow(
                                title = SettingsStr.homeScreenWidget(),
                                icon = { HomeScreenWidgetIcon() },
                                onClick = {
                                    FeatureLog.widget("open")
                                    settingsRouter.navigate(AppRoute.WidgetIntro)
                                }
                            )
                        }
                    }
                }
            }

            if (isShiftMode) {
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

                item(key = "shift_exit") {
                    top.yukonga.miuix.kmp.basic.Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, end = tabletHorizontalPadding, start = tabletHorizontalPadding)
                            .height(50.dp),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onExitShiftMode()
                        },
                        colors = if (isDark) ButtonDefaults.buttonColors(
                            color = Color(0xFF181818)
                        ) else ButtonDefaults.buttonColors(),
                    ) {
                        Text(
                            text = SettingsStr.exitShiftMode(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = ComposeColor(0xFFF44336)
                        )
                    }
                }
            }

            if (!isShiftMode) {
                item(key = "data_manage") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // LocalAppRouter.current 是 @Composable 调用，必须在组合层取
                            val dataManageRouter = LocalAppRouter.current
                            SettingsEntryRow(
                                title = SettingsStr.appPreferences(),
                                icon = { PersonalizationIcon() },
                                onClick = {
                                    FeatureLog.preference("open")
                                    dataManageRouter.navigate(AppRoute.PreferenceSettings)
                                }
                            )
                            SettingsEntryRow(
                                title = SettingsStr.importExport(),
                                icon = { ImportExportIcon() },
                                onClick = {
                                    FeatureLog.import("open")
                                    dataManageRouter.navigate(AppRoute.ImportExport)
                                }
                            )
                            SettingsEntryRow(
                                title = SettingsStr.scheduleBackup(),
                                icon = { BackupSyncIcon() },
                                onClick = {
                                    FeatureLog.backup("open_backup")
                                    dataManageRouter.navigate(AppRoute.ScheduleBackup)
                                }
                            )
                        }
                    }
                }
            }

            if (!isShiftMode) {
                item(key = "others_prefs") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // LocalAppRouter.current 是 @Composable 调用，必须在组合层取
                            val appRouter = LocalAppRouter.current
                            // iOS 走 App Store 更新 → 宿主传 showUpdateSettings=false，入口整个不渲染
                            CardExpand(visible = showUpdateSettings) {
                                SettingsEntryRow(
                                    title = SettingsStr.updateSettings(),
                                    icon = { UpdateSettingsIcon() },
                                    onClick = {
                                        FeatureLog.update("open")
                                        appRouter.navigate(AppRoute.UpdateSettings)
                                    }
                                )
                            }
                            SettingsEntryRow(
                                title = SettingsStr.aboutApp(),
                                icon = { AboutAppIcon() },
                                onClick = {
                                    FeatureLog.about("open")
                                    appRouter.navigate(AppRoute.About)
                                }
                            )
                            SettingsEntryRow(
                                title = SettingsStr.destAppreciate(),
                                icon = { DonateIcon() },
                                onClick = {
                                    appRouter.navigate(AppRoute.AppreciateAuthor)
                                }
                            )
                        }
                    }
                }
                item(key = "community") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        SettingsEntryRow(
                            title = SettingsStr.destCommunication(),
                            icon = { CommunicationIcon() },
                            onClick = {
                                settingsRouter.navigate(AppRoute.Communication)
                            }
                        )
                    }
                }
            }
        }

        // 搜索时压一层透明手势层，挡住已经淡出的设置列表
        if (searchEnabled && searchExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }

        // 搜索结果覆盖层：展开时淡入，标题收起后紧跟搜索框
        AnimatedVisibility(
            visible = searchEnabled && searchExpanded,
            enter = fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150)),
            modifier = Modifier.fillMaxSize(),
        ) {
            SettingsSearchResults(
                groups = searchResults,
                query = searchQuery,
                topPadding = paddingValues.calculateTopPadding() +
                    CollapsibleTopAppBarDefaults.CollapsedHeight +
                    pageTopExtra(isTablet, hasSmallTitle = false) +
                    SettingsSearchBarBlockHeight + 4.dp,
                horizontalPadding = tabletHorizontalPadding,
                scrollBehavior = settingsScrollBehavior,
                onEntryClick = { entry ->
                    search.expanded = false
                    settingsRouter.navigate(entry.route)
                },
            )
        }

        }
    }
}
