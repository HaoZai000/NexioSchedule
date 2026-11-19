package com.haooz.chedule.ui.activities.setting

// ⚠ 本文件不得出现 android.* / androidx.activity / androidx.core；平台能力做成注入参数。

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.util.lengthOfMonth
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.nexioui.cardexpand.CardExpand
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text
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
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.card.Card
import androidx.compose.material3.Icon
import com.haooz.nexioui.basiccomponent.PreferenceFlash
import com.haooz.nexioui.button.Button
import com.haooz.nexioui.scaffold.Scaffold
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.All
import com.haooz.nexioui.icon.extended.ChevronForward
import com.haooz.nexioui.icon.extended.CloudFill
import com.haooz.nexioui.icon.extended.FavoritesFill
import com.haooz.nexioui.icon.extended.Months
import com.haooz.nexioui.topappbar.CollapsibleTopAppBarDefaults
import com.haooz.nexioui.topappbar.SharedScrollBehavior
import com.haooz.nexioui.topbarmaterial.TopBarMaterialThreshold
import com.haooz.nexioui.topappbar.collapsibleTopInset
import com.haooz.nexioui.window.isTabletWidth
import com.haooz.nexioui.checkboxpreference.CheckboxLocation
import com.haooz.nexioui.checkboxpreference.CheckboxPreference
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.softglass.backdrop.GlassBackdrop
import androidx.compose.ui.graphics.Color as ComposeColor

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
    /** 平板左栏：右栏正显示本行对应的页面时保持按压态。 */
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded == true) 90f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "settingsEntryRotation",
    )
    val interactionSource = remember { MutableInteractionSource() }
    // 按住不放：MiuixIndication 只认 PressInteraction，位置不参与绘制。
    // 选中行自己也是可点的：真实点击的 Release 会把按压态清掉，而 selected 没变、
    // 本 effect 不会重启 —— 所以松手后要补一次按下，否则点一下当前项高亮就没了。
    LaunchedEffect(selected) {
        if (!selected) return@LaunchedEffect
        var press = PressInteraction.Press(Offset.Zero)
        interactionSource.emit(press)
        try {
            launch {
                interactionSource.interactions.collect { interaction ->
                    val released = interaction is PressInteraction.Release ||
                        interaction is PressInteraction.Cancel
                    if (!released || interaction == press) return@collect
                    press = PressInteraction.Press(Offset.Zero)
                    interactionSource.emit(press)
                }
            }
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { interactionSource.emit(PressInteraction.Release(press)) }
        }
    }
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
                color = NexioTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    fontSize = 14.sp,
                    color = NexioTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (endText != null) {
            Text(
                text = endText,
                fontSize = 14.sp,
                color = NexioTheme.colorScheme.onSurfaceVariantActions,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = NexioIcons.ChevronForward,
            contentDescription = null,
            modifier = Modifier
                .size(17.dp)
                .graphicsLayer { rotationZ = rotation },
            tint = NexioTheme.colorScheme.onSurfaceVariantActions,
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
    VectorEntryIcon(NexioIcons.Months, SettingsAccents.Blue)
}

/** 课程提醒图标。 */
@Composable
internal fun CourseReminderIcon() {
    DrawableEntryIcon(Res.drawable.ic_settings_reminders, SettingsAccents.Red)
}

/** 桌面小部件图标。 */
@Composable
internal fun HomeScreenWidgetIcon() {
    VectorEntryIcon(NexioIcons.All, SettingsAccents.Green)
}

/** AI 功能图标：白底 + 灰色描边 + 彩色渐变图形。 */
@Composable
internal fun AiFeaturesIcon() {
    val shape = remember { ContinuousRoundedRectangle(8.dp) }
    val outline = if (isAppDarkTheme()) {
        Color.White.copy(alpha = 0.16f)
    } else {
        NexioTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    }
    // 渐变坐标按 1024 视口路径空间给出（对齐圆形轮廓 bounds）：Canvas 做了 scale，
    // 若用默认 size 取终点，渐变只覆盖左上角极小区域，整枚图标会渲染成最后一种颜色
    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFF6A00),
            Color(0xFFFFAA40),
            Color(0xFF7FB6DE),
            Color(0xFF008CFF)
        ),
        start = Offset(97f, 97f),
        end = Offset(926f, 926f),
    )
    // 渐变着色不支持直接套在 painter 上，按原始 1024 视口路径手绘
    val iconPath = remember { PathParser().parsePathString(AiIconPathData).toPath() }
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Color.White, shape)
            .border(0.5.dp, outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val factor = size.width / 1024f
            scale(scaleX = factor, scaleY = factor, pivot = Offset.Zero) {
                drawPath(iconPath, brush = gradient)
            }
        }
    }
}

/** AI 功能图标路径（1024×1024 视口，圆形内嵌四角星镂空）。 */
private const val AiIconPathData =
    "M512 97.52381c228.912762 0 414.47619 185.563429 414.47619 414.47619s-185.563429 " +
        "414.47619-414.47619 414.47619S97.52381 740.912762 97.52381 512 283.087238 " +
        "97.52381 512 97.52381z m-12.190476 170.666666c-19.309714 0-36.035048 " +
        "34.669714-45.031619 51.395048l-23.942095 57.197714a146.285714 146.285714 0 0 " +
        "1-78.433524 78.433524l-57.197715 23.942095C278.479238 488.17981 243.809524 " +
        "504.880762 243.809524 524.190476s34.669714 36.035048 51.395047 45.031619l57.197715 " +
        "23.942095a146.285714 146.285714 0 0 1 78.433524 78.433524l23.942095 " +
        "57.197715c9.020952 16.725333 25.721905 51.395048 45.031619 51.395047s36.035048-34.669714 " +
        "45.031619-51.395047l23.942095-57.197715a146.285714 146.285714 0 0 1 " +
        "78.433524-78.433524l57.197714-23.942095C721.13981 560.201143 755.809524 " +
        "543.50019 755.809524 524.190476s-34.669714-36.035048-51.395048-45.031619l-57.197714-23.942095a146.285714 " +
        "146.285714 0 0 1-78.433524-78.433524l-23.942095-57.197714C535.82019 302.86019 " +
        "519.119238 268.190476 499.809524 268.190476z"

/** 导入导出图标。 */
@Composable
internal fun ImportExportIcon() {
    DrawableEntryIcon(Res.drawable.ic_settings_data, SettingsAccents.Orange)
}

/** 备份与同步图标。 */
@Composable
internal fun BackupSyncIcon() {
    VectorEntryIcon(NexioIcons.CloudFill, SettingsAccents.Blue)
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
    VectorEntryIcon(NexioIcons.FavoritesFill, SettingsAccents.Red)
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
    liquidGlassBackdrop: GlassBackdrop? = null,
    /** 搜索状态（由路由持有：搜索框挂在页面采样层外，与列表共享同一份状态） */
    searchState: SettingsSearchState? = null,
    // ── 平台注入（宿主提供；桌面/iOS 用默认空实现）──
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
    /** 是否显示「更新」入口。
     */
    showUpdateSettings: Boolean = true,
    /** 是否显示「桌面小部件」入口（鸿蒙无小组件能力）。 */
    showWidgetEntry: Boolean = true,
    /**
     * 是否显示「排班模式」入口。
     * ⚠ 传 false 时 `isShiftMode` 必为 false，只需挡「进入」两处，「退出」也一并挡掉以防状态被外部置起。
     */
    /** 非 null 时入口交给平板右栏局部栈；手机端走全局路由。 */
    onOpenRoute: ((AppRoute) -> Unit)? = null,
    /** 平板左栏：右栏栈底页面（三级页压在它上面时本栏仍保持按压态）。 */
    selectedRoute: AppRoute? = null,
    /** 关掉搜索占位与结果层。 */
    showSearch: Boolean = true,
    /** 直接指定列表顶部内边距。 */
    listTopPaddingOverride: Dp? = null,
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
    // 唯一入口：手机端推全局路由，平板左栏改推右栏局部栈
    val openRoute: (AppRoute) -> Unit = onOpenRoute ?: settingsRouter::navigate


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
            onSendTestNotification = onSendTestNotification,
            isIslandSupported = isIslandSupported,
            testActionLabel = testActionLabel,
            onRefreshWidgets = onRefreshWidgets,
            onHolidayDataChanged = onHolidayDataChanged,
            fetchHolidayEntries = fetchHolidayEntries,
            // iOS 走 App Store 更新：入口整个不渲染（平板左栏也要挡）
            showUpdateSettings = showUpdateSettings,
            showWidgetEntry = showWidgetEntry,
        )
        return
    }
    val isDark = isAppDarkTheme()
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp
    val nativeChromeInset = LocalHostNavigation.current
        ?.takeIf { it.showsNativeTabBar && !isTablet }
        ?.bottomBarInset?.toFloat()?.dp

    val search = searchState ?: rememberSettingsSearchState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchQuery = search.query
    val searchExpanded = search.expanded
    val searchEnabled = showSearch && !isShiftMode
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
        val listTopPadding = listTopPaddingOverride ?: (
            paddingValues.calculateTopPadding() +
                CollapsibleTopAppBarDefaults.CollapsedHeight +
                pageTopExtra(isTablet, hasSmallTitle = false) +
                (if (searchEnabled) SettingsSearchBarBlockHeight else 0.dp)
            )

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
                        selected = selectedRoute == AppRoute.ScheduleSettings,
                        onClick = {
                            openRoute(AppRoute.ScheduleSettings)
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
                                selected = selectedRoute == AppRoute.CourseReminder,
                                onClick = {
                                    FeatureLog.reminder("open")
                                    openRoute(AppRoute.CourseReminder)
                                }
                            )
                            if (showWidgetEntry) {
                                SettingsEntryRow(
                                    title = SettingsStr.homeScreenWidget(),
                                    icon = { HomeScreenWidgetIcon() },
                                    selected = selectedRoute == AppRoute.WidgetIntro,
                                    onClick = {
                                        FeatureLog.widget("open")
                                        openRoute(AppRoute.WidgetIntro)
                                    }
                                )
                            }
                            SettingsEntryRow(
                                title = SettingsStr.aiFeatures(),
                                icon = { AiFeaturesIcon() },
                                selected = selectedRoute == AppRoute.AiFeatures,
                                onClick = { openRoute(AppRoute.AiFeatures) }
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
                    Button(
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
                            SettingsEntryRow(
                                title = SettingsStr.appPreferences(),
                                icon = { PersonalizationIcon() },
                                selected = selectedRoute == AppRoute.PreferenceSettings,
                                onClick = {
                                    FeatureLog.preference("open")
                                    openRoute(AppRoute.PreferenceSettings)
                                }
                            )
                            SettingsEntryRow(
                                title = SettingsStr.importExport(),
                                icon = { ImportExportIcon() },
                                selected = selectedRoute == AppRoute.ImportExport,
                                onClick = {
                                    FeatureLog.import("open")
                                    openRoute(AppRoute.ImportExport)
                                }
                            )
                            SettingsEntryRow(
                                title = SettingsStr.scheduleBackup(),
                                icon = { BackupSyncIcon() },
                                selected = selectedRoute == AppRoute.ScheduleBackup,
                                onClick = {
                                    FeatureLog.backup("open_backup")
                                    openRoute(AppRoute.ScheduleBackup)
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
                            // iOS 走 App Store 更新 → 宿主传 showUpdateSettings=false，入口整个不渲染
                            CardExpand(visible = showUpdateSettings) {
                                SettingsEntryRow(
                                    title = SettingsStr.updateSettings(),
                                    icon = { UpdateSettingsIcon() },
                                    selected = selectedRoute == AppRoute.UpdateSettings,
                                    onClick = {
                                        FeatureLog.update("open")
                                        openRoute(AppRoute.UpdateSettings)
                                    }
                                )
                            }
                            SettingsEntryRow(
                                title = SettingsStr.aboutApp(),
                                icon = { AboutAppIcon() },
                                selected = selectedRoute == AppRoute.About,
                                onClick = {
                                    FeatureLog.about("open")
                                    openRoute(AppRoute.About)
                                }
                            )
                            SettingsEntryRow(
                                title = SettingsStr.destAppreciate(),
                                icon = { DonateIcon() },
                                selected = selectedRoute == AppRoute.AppreciateAuthor,
                                onClick = {
                                    openRoute(AppRoute.AppreciateAuthor)
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
                            selected = selectedRoute == AppRoute.Communication,
                            onClick = {
                                openRoute(AppRoute.Communication)
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
                    // 跳转时主动收键盘（搜索保持展开，但退出输入态）
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    // 目标页里对应设置项闪两下，提示搜索项位置
                    PreferenceFlash.trigger(entry.title)
                    openRoute(entry.route)
                },
            )
        }

        }
    }
}
