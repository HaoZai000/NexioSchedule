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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AiImportScreen
import com.haooz.chedule.ui.activities.AppreciateAuthorScreen
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.CommunicationScreen
import com.haooz.chedule.ui.activities.CourseReminderScreen
import com.haooz.chedule.ui.activities.CourseTimeSettingsScreen
import com.haooz.chedule.ui.activities.HolidaySettingsScreen
import com.haooz.chedule.ui.activities.LocalBackupScreen
import com.haooz.chedule.ui.activities.PreferenceSettingsScreen
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.activities.UpdateSettingsScreen
import com.haooz.chedule.ui.activities.WebDavSettingsScreen
import com.haooz.chedule.ui.activities.WidgetIntroScreen
import com.haooz.chedule.ui.basic.LiquidGlassTextButton
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.utils.LocalOverScrollState
import com.haooz.chedule.ui.utils.OverScrollState
import com.haooz.chedule.ui.utils.overScrollVertical
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
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
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.math.abs

/**
 * 平板设置页目的地：原一级 + 原二级入口全部平铺在左栏；
 * 右栏直接渲染内容（原三级页变为右栏二级内容）。全程不跳 Activity。
 * 仅 pad 使用此分组（与手机设置页分类不同）。
 */
enum class TabletSettingsDest(val title: String, val group: String) {
    Semester("学期与周次", "基本设置"),
    CourseTime("课表节数与时间", "基本设置"),
    Reminder("课程提醒", "特色功能"),
    Holiday("节假日与调休", "特色功能"),
    Widget("桌面小部件", "特色功能"),
    ScheduleImport("文件口令导入", "导入与导出"),
    AiImport("AI 文本导入", "导入与导出"),
    EducationalImport("教务系统导入", "导入与导出"),
    ScheduleExport("课表导出", "导入与导出"),
    LocalBackup("本地备份", "备份"),
    WebDav("WebDAV 云同步", "备份"),
    Preference("应用偏好设置", "其他"),
    Update("更新设置", "其他"),
    About("关于应用", "其他"),
    Appreciate("捐赠支持", "其他"),
    Communication("交流与反馈", "其他"),
}

/** 平板设置选中项：MainActivity 叠层读它画固定标题 */
object TabletSettingsUiState {
    var selected by mutableStateOf(TabletSettingsDest.Semester)
    /** 课表节数与时间的编辑屏是否打开：打开时叠层不画右栏标题，避免与编辑屏标题重合 */
    var timeEditorOpen by mutableStateOf(false)
}

/**
 * 画在 MainActivity 层的设置页固定标题 + 分界线。
 * 标题用顶栏折叠态样式（19sp / Medium），垂直位置与 CollapsibleTopAppBar 折叠标题一致：
 * 状态栏下方 CollapsedHeight 区域内垂直居中；水平在左右两栏各自居中。
 */
@Composable
fun TabletSettingsChromeOverlay() {
    val selected = TabletSettingsUiState.selected
    val density = LocalDensity.current
    val statusBar = mainWindowTopInset()
    val collapsedH = com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults.CollapsedHeight
    val dividerColor = if (com.haooz.chedule.ui.utils.isAppDarkTheme()) {
        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f)
    } else {
        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.06f)
    }
    val titleColor = MiuixTheme.colorScheme.onSurface

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(24f)
    ) {
        val sidePad = com.haooz.chedule.ui.components.tabletNavSideStartPadding()
        val contentWidth = maxWidth - sidePad
        val leftWidth = contentWidth * 0.42f
        val dividerX = sidePad + leftWidth

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(with(density) { dividerX.toPx().toInt() }, 0) }
                .width(1.dp)
                .fillMaxHeight()
                .background(dividerColor)
        )

        // 折叠态标题：状态栏下 CollapsedHeight 内垂直居中
        val collapsedTitle: @Composable (String) -> Unit = { text ->
            Text(
                text = text,
                color = titleColor,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = sidePad, top = statusBar)
                .width(leftWidth)
                .height(collapsedH),
            contentAlignment = Alignment.Center
        ) {
            collapsedTitle("我的")
        }

        // 关于应用内嵌自绘顶栏标题 / 编辑屏打开时，设置页叠加层不再画右栏标题，避免重合
        if (selected != TabletSettingsDest.About && !TabletSettingsUiState.timeEditorOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = dividerX, top = statusBar)
                    .width(maxWidth - dividerX)
                    .height(collapsedH),
                contentAlignment = Alignment.Center
            ) {
                collapsedTitle(selected.title)
            }
        }
    }
}

/**
 * 当前窗口顶部系统内边距。对齐 CollapsibleTopAppBar：用 systemBars 仅取顶部，
 * 分屏/自由窗口下 statusBars 顶部为 0，用 systemBars 才不会让内容贴顶。
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
 * 本栏顶部表面色遮罩 / 右上角按钮共用的 alpha，对齐 CollapsibleTopAppBar 手机实现：
 * 滚动超阈值后 spring 淡入/淡出，不随滚动距离改变透明度。
 */
@Composable
private fun rememberPaneMaskAlpha(scrolledPx: Float): Float {
    val density = LocalDensity.current
    val overScroll = LocalOverScrollState.current
    val scrollThresholdPx = with(density) { 10.dp.toPx() }
    val overscrollThresholdPx = with(density) { 4.dp.toPx() }
    // 真实滚动或越界拉伸都算「顶部内容位移」：越界拉伸给出反馈，回弹静止时
    // overScroll.offset 归零，遮罩随之淡出
    val showMask =
        scrolledPx > scrollThresholdPx || abs(overScroll.offset) > overscrollThresholdPx
    val maskAnim = remember { Animatable(0f) }
    LaunchedEffect(showMask) {
        val spec =
            if (showMask) folmeSpring(damping = 1.0f, response = 0.6f)
            else folmeSpring<Float>(damping = 1.0f, response = 0.4f)
        maskAnim.animateTo(
            targetValue = if (showMask) 1f else 0f,
            animationSpec = spec,
        )
    }
    return maskAnim.value
}

/**
 * 本栏顶部：渐变模糊常驻；表面色遮罩仅在上滑后出现。
 * 糊层采样本栏本地 backdrop（兄弟节点），避免与全局层循环采样。
 * maskAlpha 传入时直接复用（供右上角按钮与之同步），否则自行计算。
 */
@Composable
private fun TabletPaneTopChrome(
    scrolledPx: Float,
    backdrop: com.kyant.backdrop.Backdrop?,
    modifier: Modifier = Modifier,
    maskAlpha: Float? = null,
) {
    val resolvedMaskAlpha = if (maskAlpha != null) maskAlpha else rememberPaneMaskAlpha(scrolledPx)
    val maskHeight = TabletPaneBlurHeight
    val gradientColor = if (com.haooz.chedule.ui.utils.isAppDarkTheme()) Color.Black else Color.White

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(maskHeight)
    ) {
        // 渐变模糊：常驻
        if (backdrop != null) {
            ProgressiveBlurTopBar(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize(),
                height = maskHeight,
                blurAlpha = 1f,
                content = {},
            )
        }
        // 表面色遮罩：alpha 随滚动连续变化
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .graphicsLayer { alpha = resolvedMaskAlpha }
                .background(
                    Brush.verticalGradient(
                        0f to gradientColor.copy(alpha = 0.85f),
                        0.45f to gradientColor.copy(alpha = 0.55f),
                        0.7f to gradientColor.copy(alpha = 0.32f),
                        0.85f to gradientColor.copy(alpha = 0.14f),
                        0.93f to gradientColor.copy(alpha = 0.05f),
                        1f to Color.Transparent,
                    )
                )
        )
    }
}

/** 只观察、不消费的滚动累计：正数表示内容已上滑；到顶/顶部回弹时清零 */
@Composable
private fun rememberPaneScrollTracker(
    resetKey: Any?,
    onScrollPx: (Float) -> Unit,
): NestedScrollConnection {
    val currentOnScrollPx by rememberUpdatedState(onScrollPx)
    // 以 resetKey 作为 remember 键：切子页时重建连接并清零累计位移，
    // 避免残留上一页滚动量导致新页遮罩触发时机提前/错乱
    val acc = remember(resetKey) { floatArrayOf(0f) }
    val overScroll = LocalOverScrollState.current
    return remember(resetKey) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 越界拉伸（回弹）期间不累计真实滚动：不可滚动页上滑只触发回弹、
                // 不产生真实位移，回弹后遮罩会残留，故拉伸期一律不写 acc
                if (!overScroll.isOverScrollActive) {
                    when {
                        consumed.y != 0f -> {
                            acc[0] = (acc[0] - consumed.y).coerceAtLeast(0f)
                        }
                        // 列表已在顶部：继续下拉/回弹时未消费的向下位移 → 遮罩应收起
                        available.y > 0f -> {
                            acc[0] = 0f
                        }
                    }
                }
                currentOnScrollPx(acc[0])
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                // 朝列表顶部甩、还有剩余速度：归位后清零
                if (available.y > 1f) {
                    acc[0] = 0f
                    currentOnScrollPx(0f)
                }
                return Velocity.Zero
            }
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
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    settingsScrollBehavior: com.haooz.chedule.ui.basic.SharedScrollBehavior? = null,
) {
    val selected = TabletSettingsUiState.selected
    val groups = remember { TabletSettingsDest.entries.groupBy { it.group } }
    val context = LocalContext.current
    val paneHorizontal = 20.dp
    // 顶栏折叠标题高度：左右内容都从这条线下方开始，避免被 MainActivity 叠层标题压住
    val chromeTop = mainWindowTopInset() +
            com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults.CollapsedHeight +
            12.dp

    // 左栏滚动用列表状态驱动遮罩；右栏用只观察的 nestedScroll（不挂在列表上）
    var leftScrollPx by remember { mutableFloatStateOf(0f) }
    var rightScrollPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(selected) { rightScrollPx = 0f }

    // 手机端底部按钮画在各 Activity；pad 内嵌 Screen 时需要在右栏叠层补回
    var showWidgetGuideDialog by remember { mutableStateOf(false) }
    var webDavBackingUp by remember { mutableStateOf(false) }
    var webDavRestoring by remember { mutableStateOf(false) }
    var onWebDavBackup by remember { mutableStateOf({}) }
    var onWebDavRestore by remember { mutableStateOf({}) }
    var webDavConnected by remember { mutableStateOf(false) }
    var onWebDavTestConnection by remember { mutableStateOf({}) }
    var holidayLoading by remember { mutableStateOf(false) }
    var onHolidayUpdate by remember { mutableStateOf({}) }
    // 课表节数与时间：右栏叠编辑屏
    val courseRepository = remember { com.haooz.chedule.data.CourseRepository(context) }
    var editingTimeConfig by remember { mutableStateOf<com.haooz.chedule.data.TimeConfig?>(null) }
    var creatingTimeConfig by remember { mutableStateOf(false) }
    var timeConfigRefreshTrigger by remember { mutableIntStateOf(0) }
    val uiScope = rememberCoroutineScope()
    // 1 = 完全在右侧屏外，0 = 完全滑入；退出时先滑到 1 再清空配置，保证退场有内容
    val timeEditorSlide = remember { Animatable(1f) }
    // 切页时收起编辑屏，并恢复叠加层标题
    LaunchedEffect(selected) {
        editingTimeConfig = null
        creatingTimeConfig = false
        TabletSettingsUiState.timeEditorOpen = false
    }
    val islandNotification by settingsViewModel.islandNotification.collectAsState()
    val islandSupported = remember { IslandNotificationHelper.isIslandSupported(context) }
    val islandEnabled = islandNotification && islandSupported
    val hapticFeedback = LocalHapticFeedback.current

    val leftListState = rememberLazyListState()
    LaunchedEffect(leftListState) {
        snapshotFlow {
            leftListState.firstVisibleItemIndex * 8_000 +
                leftListState.firstVisibleItemScrollOffset
        }.collect { leftScrollPx = it.toFloat().coerceAtLeast(0f) }
    }
    LaunchedEffect(leftListState) {
        snapshotFlow {
            leftListState.firstVisibleItemIndex == 0 &&
                leftListState.firstVisibleItemScrollOffset == 0
        }.collect { atTop -> if (atTop) leftScrollPx = 0f }
    }
    // 右栏专属 overscroll：与全局 CompositionLocal 分离，避免左右栏越界状态串扰
    val rightOverScroll = remember { OverScrollState() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val leftWidth = maxWidth * 0.42f
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
                        .layerBackdrop(leftPaneBackdrop)
                ) {
                    LazyColumn(
                        state = leftListState,
                        // 与完整设置页相同：只用公共 overScrollVertical
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical(),
                        contentPadding = PaddingValues(
                            start = paneHorizontal,
                            top = chromeTop,
                            end = paneHorizontal,
                            bottom = 60.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groups.forEach { (group, dests) ->
                            item(key = "g_$group") {
                                SmallTitle(
                                    text = group,
                                    modifier = Modifier.offset(x = (-16).dp)
                                )
                                Card(
                                    cornerRadius = 20.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                    insideMargin = PaddingValues(0.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        dests.filterNot { it in BottomMoreDests }.forEach { dest ->
                                            val jumpActivity =
                                                dest == TabletSettingsDest.EducationalImport
                                            TabletLeftEntry(
                                                dest = dest,
                                                isSelected = !jumpActivity && dest == selected,
                                                onSelect = {
                                                    if (jumpActivity) {
                                                        context.startActivity(
                                                            android.content.Intent(
                                                                context,
                                                                com.haooz.chedule.ui.activities.EducationalImportActivity::class.java
                                                            )
                                                        )
                                                    } else {
                                                        TabletSettingsUiState.selected = dest
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // 底部独立卡片：关于应用 / 捐赠支持 / 交流与反馈（不新建分类）
                        item(key = "bottom_more") {
                            Card(
                                cornerRadius = 20.dp,
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = PaddingValues(0.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    BottomMoreDests.forEach { dest ->
                                        TabletLeftEntry(
                                            dest = dest,
                                            isSelected = dest == selected,
                                            onSelect = { TabletSettingsUiState.selected = dest }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                TabletPaneTopChrome(
                    scrolledPx = leftScrollPx,
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
                    val rightTrack = rememberPaneScrollTracker(selected) { rightScrollPx = it }
                    // 右上角按钮与右栏顶遮罩共用同一 alpha，真实滚动或越界拉伸时同步淡入
                    val rightMaskAlpha = rememberPaneMaskAlpha(rightScrollPx)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(rightTrack)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(rightPaneBackdrop)
                    ) {
                        when (selected) {
                            TabletSettingsDest.Semester -> TabletSemesterPane(
                                viewModel = viewModel,
                                scheduleViewModel = scheduleViewModel,
                                settingsViewModel = settingsViewModel,
                                shiftViewModel = shiftViewModel,
                                isShiftMode = isShiftMode,
                                onExitShiftMode = onExitShiftMode,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                scrollBehavior = null,
                            )

                            TabletSettingsDest.CourseTime -> CourseTimeSettingsScreen(
                                onEditConfig = { config, _ ->
                                    // 从 repository 重读最新配置，避免使用缓存旧数据
                                    editingTimeConfig =
                                        courseRepository.getTimeConfig(config.id) ?: config
                                    creatingTimeConfig = false
                                    TabletSettingsUiState.timeEditorOpen = true
                                    uiScope.launch {
                                        timeEditorSlide.snapTo(1f)
                                        timeEditorSlide.animateTo(
                                            0f,
                                            animationSpec = tween(
                                                520,
                                                easing = CubicBezierEasing(0.3f, 0.92f, 0.3f, 1f)
                                            )
                                        )
                                    }
                                },
                                onCreateConfig = {
                                    editingTimeConfig =
                                        com.haooz.chedule.data.TimeConfig(name = "")
                                    creatingTimeConfig = true
                                    TabletSettingsUiState.timeEditorOpen = true
                                    uiScope.launch {
                                        timeEditorSlide.snapTo(1f)
                                        timeEditorSlide.animateTo(
                                            0f,
                                            animationSpec = tween(
                                                480,
                                                easing = CubicBezierEasing(0.34f, 1.12f, 0.3f, 1f)
                                            )
                                        )
                                    }
                                },
                                refreshTrigger = timeConfigRefreshTrigger,
                                scrollBehavior = null,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                hideFab = true,
                            )

                            TabletSettingsDest.Reminder -> CourseReminderScreen(
                                settingsViewModel = settingsViewModel,
                                scrollBehavior = null,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                            )

                            TabletSettingsDest.Holiday -> TabletHolidayPane(
                                scrollBehavior = null,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                onUpdateReady = { onHolidayUpdate = it },
                                onLoadingChange = { holidayLoading = it },
                            )

                            TabletSettingsDest.Widget -> WidgetIntroScreen(
                                scrollBehavior = null,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                            )

                            TabletSettingsDest.ScheduleImport -> BackupAndMigrationScreen(
                                courseViewModel = viewModel,
                                scheduleViewModel = scheduleViewModel,
                                settingsViewModel = settingsViewModel,
                                scrollBehavior = null,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                mode = ScheduleDataManageMode.Import,
                                compactImport = true,
                            )

                            TabletSettingsDest.EducationalImport -> {
                                Box(modifier = Modifier.fillMaxSize())
                            }

                            TabletSettingsDest.AiImport -> AiImportScreen(
                                onBack = {},
                                scrollBehavior = null,
                                viewModel = viewModel,
                                settingsViewModel = settingsViewModel,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                            )

                            TabletSettingsDest.ScheduleExport -> BackupAndMigrationScreen(
                                courseViewModel = viewModel,
                                scheduleViewModel = scheduleViewModel,
                                settingsViewModel = settingsViewModel,
                                scrollBehavior = null,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                mode = ScheduleDataManageMode.Export,
                            )

                            TabletSettingsDest.LocalBackup -> LocalBackupScreen(
                                scrollBehavior = null,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                            )

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
                            )

                            TabletSettingsDest.Preference -> PreferenceSettingsScreen(
                                scrollBehavior = null,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                            )

                            TabletSettingsDest.Update -> UpdateSettingsScreen(
                                scrollBehavior = null,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                            )

                            TabletSettingsDest.About -> {
                                val aboutBackdrop =
                                    com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                                AboutScreen(onBack = {}, liquidGlassBackdrop = aboutBackdrop, embedded = true)
                            }

                            TabletSettingsDest.Appreciate -> AppreciateAuthorScreen(
                                scrollBehavior = null,
                            )


                            TabletSettingsDest.Communication -> CommunicationScreen(
                                scrollBehavior = null,
                            )
                        }
                    }

                    // 底部操作按钮：与手机 Activity 层同款。玻璃按钮必须采样右栏本地 backdrop（兄弟节点，
                    // 记录内容不含按钮本身）；不能采样全局 liquidGlassBackdrop——按钮就在全局玻璃层内，
                    // 采样自身会触发循环采样。弹窗在根部 PopupHost 渲染，仍可正常采样全局层。
                    val glassBackdrop = liquidGlassBackdrop
                    if (glassBackdrop != null) when (selected) {
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
                                    text = if (islandEnabled) "测试小米超级岛" else "测试实时活动",
                                    onClick = {
                                        com.haooz.chedule.ui.utils.FeatureLog.reminderFlow(
                                            "test_notification",
                                            if (islandEnabled) "island" else "live"
                                        )
                                        if (islandEnabled) {
                                            IslandNotificationHelper.sendTestIslandNotification(context)
                                            com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("test_island_sent")
                                            android.widget.Toast.makeText(
                                                context,
                                                "已发送超级岛测试通知",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            CourseReminderHelper.sendTestLiveNotification(context)
                                            com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("test_live_sent")
                                            android.widget.Toast.makeText(
                                                context,
                                                "已发送实时活动测试通知",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
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
                                    text = "添加到桌面",
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        showWidgetGuideDialog = true
                                    },
                                    backdrop = rightPaneBackdrop,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            OverlayDialog(
                                title = "添加桌面小部件",
                                show = showWidgetGuideDialog,
                                liquidGlassBackdrop = glassBackdrop,
                                onDismissRequest = { showWidgetGuideDialog = false }
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
                                            showWidgetGuideDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        TabletSettingsDest.WebDav -> {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 36.dp)
                                    .navigationBarsPadding()
                                    .padding(bottom = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                LiquidGlassTextButton(
                                    text = if (webDavBackingUp) "备份中..." else "备份到云端",
                                    onClick = { onWebDavBackup() },
                                    backdrop = rightPaneBackdrop,
                                    modifier = Modifier.weight(1f)
                                )
                                LiquidGlassTextButton(
                                    text = if (webDavRestoring) "恢复中..." else "从云端恢复",
                                    onClick = { onWebDavRestore() },
                                    backdrop = rightPaneBackdrop,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        else -> {}
                    }
                }
                // 关于应用页内嵌且自绘顶栏糊层/遮罩，这里不再叠加设置页右栏顶部糊层
                if (selected != TabletSettingsDest.About) {
                    TabletPaneTopChrome(
                        scrolledPx = rightScrollPx,
                        backdrop = rightPaneBackdrop,
                        modifier = Modifier.align(Alignment.TopStart),
                        maskAlpha = rightMaskAlpha,
                    )
                }

                // 右上角操作按钮：节假日「更新」、WebDAV「测试连接」。
                // 与底部按钮同理采样右栏本地 backdrop（兄弟节点，记录内容不含按钮）。
                // 作为 TabletPaneTopChrome 后的兄弟绘制，落在顶部糊层之上。
                // 出现/消失只动玻璃材质（backdropAlpha/shadowAlpha），图标常驻。
                if (liquidGlassBackdrop != null) when (selected) {
                    TabletSettingsDest.Holiday -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
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
                                    contentDescription = "更新",
                                    iconSize = 28.dp,
                                    backdropAlpha = rightMaskAlpha,
                                    shadowAlpha = rightMaskAlpha,
                                )
                            }
                        }
                    }

                    TabletSettingsDest.WebDav -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                                .padding(top = 6.dp, end = 16.dp)
                        ) {
                            LiquidTopBarButton(
                                onClick = { onWebDavTestConnection() },
                                backdrop = rightPaneBackdrop,
                                icon = if (webDavConnected) MiuixIcons.Ok else MiuixIcons.Play,
                                contentDescription = if (webDavConnected) "已连接" else "测试连接",
                                iconTint = if (webDavConnected) Color(0xFF4CAF50) else Color.Unspecified,
                                iconOffset = if (!webDavConnected) DpOffset(x = 2.dp, y = 0.dp) else DpOffset.Zero,
                                backdropAlpha = rightMaskAlpha,
                                shadowAlpha = rightMaskAlpha,
                            )
                        }
                    }

                    else -> {}
                }

                // 课表节数与时间：右栏叠编辑屏，从底部滑入/滑出（对齐 BlurBottomSheet 曲线时长，无透明度变化）
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
                                val editDensity = androidx.compose.ui.platform.LocalDensity.current
                                TimeConfigEditScreen(
                                    timeConfig = config,
                                    onBack = {
                                        uiScope.launch {
                                            timeEditorSlide.animateTo(
                                                1f,
                                                animationSpec = tween(
                                                    320,
                                                    easing = CubicBezierEasing(0.34f, 1f, 0.3f, 1f)
                                                )
                                            )
                                            editingTimeConfig = null
                                            creatingTimeConfig = false
                                            TabletSettingsUiState.timeEditorOpen = false
                                        }
                                    },
                                    onSave = { savedConfig ->
                                        if (creatingTimeConfig) {
                                            val newId = courseRepository.addTimeConfig(savedConfig)
                                            courseRepository.switchToTimeConfig(newId)
                                        } else {
                                            courseRepository.saveTimeConfig(savedConfig)
                                            if (savedConfig.id ==
                                                courseRepository.getCurrentTimeConfigId()
                                            ) {
                                                courseRepository.switchToTimeConfig(savedConfig.id)
                                            }
                                        }
                                        timeConfigRefreshTrigger++
                                        uiScope.launch {
                                            timeEditorSlide.animateTo(
                                                1f,
                                                animationSpec = tween(
                                                    320,
                                                    easing = CubicBezierEasing(0.34f, 1f, 0.3f, 1f)
                                                )
                                            )
                                            editingTimeConfig = null
                                            creatingTimeConfig = false
                                            TabletSettingsUiState.timeEditorOpen = false
                                        }
                                    },
                                    screenWidth = with(editDensity) { maxWidth.toPx() },
                                    screenHeight = with(editDensity) { maxHeight.toPx() },
                                    liquidGlassBackdrop = rightPaneBackdrop,
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

private val Color_Black_08 = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.08f)

/** 左栏底部独立卡片：关于应用 / 捐赠支持 / 交流与反馈（不新建分类，仅拆成独立卡片） */
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
) {
    ArrowPreference(
        title = dest.title,
        holdDownState = isSelected,
        onClick = onSelect,
    )
}
private val Color_White_14 = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f)

/** 右栏：学期与周次 — 与手机设置页同等可编辑（日期/周数/新学期） */
@Composable
private fun TabletSemesterPane(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    shiftViewModel: ShiftViewModel,
    isShiftMode: Boolean,
    onExitShiftMode: () -> Unit,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    scrollBehavior: com.haooz.chedule.ui.basic.SharedScrollBehavior? = null,
) {
    val context = LocalContext.current
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val showNonCurrentWeek by settingsViewModel.showNonCurrentWeek.collectAsState()
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val isSemesterStarted by viewModel.isSemesterStarted.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()

    var showStartDateDialog by remember { mutableStateOf(false) }
    var showCurrentWeekDialog by remember { mutableStateOf(false) }
    var showTotalWeeksDialog by remember { mutableStateOf(false) }
    var showNewSemesterDialog by remember { mutableStateOf(false) }
    var newSemesterName by remember { mutableStateOf("") }
    val (tempYearInit, tempMonthInit, tempDayInit) = remember(classStartTime) { parseDate(classStartTime) }
    var tempYear by remember { mutableIntStateOf(tempYearInit) }
    var tempMonth by remember { mutableIntStateOf(tempMonthInit) }
    var tempDay by remember { mutableIntStateOf(tempDayInit) }
    var tempCurrentWeek by remember { mutableIntStateOf(currentWeek) }
    var tempTotalWeeks by remember { mutableIntStateOf(totalWeeks) }

    val chromeTop = mainWindowTopInset() +
            com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults.CollapsedHeight +
            12.dp

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
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
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "开始上课日期",
                        endActions = {
                            Text(
                                text = classStartTime,
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
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
                        title = "当前周数",
                        endActions = {
                            Text(
                                text = when {
                                    !isSemesterStarted -> "未开始"
                                    currentWeek > totalWeeks -> "放假中"
                                    else -> "第${currentWeek}周"
                                },
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        onClick = {
                            tempCurrentWeek = currentWeek.coerceAtMost(totalWeeks)
                            showCurrentWeekDialog = true
                        },
                        holdDownState = showCurrentWeekDialog
                    )
                    ArrowPreference(
                        title = "本学期总周数",
                        endActions = {
                            Text(
                                text = "第${totalWeeks}周",
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        onClick = {
                            tempTotalWeeks = totalWeeks
                            showTotalWeeksDialog = true
                        },
                        holdDownState = showTotalWeeksDialog
                    )
                    SwitchPreference(
                        title = "智能显示周末",
                        summary = "开启后隐藏无课的周六日",
                        checked = smartWeekend,
                        onCheckedChange = { settingsViewModel.setSmartWeekend(it) }
                    )
                    if (!isShiftMode) {
                        SwitchPreference(
                            title = "显示非本周课程",
                            checked = showNonCurrentWeek,
                            onCheckedChange = { settingsViewModel.setShowNonCurrentWeek(it) }
                        )
                    }
                }
            }
        }
        if (isShiftMode) {
            item {
                SmallTitle(text = "排班", modifier = Modifier.offset(x = (-16).dp))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "排班模式已开启",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            text = "退出排班模式",
                            onClick = onExitShiftMode,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        // 开启新学期：始终在页面最底部，独立卡片
        item(key = "new_semester") {
            SmallTitle(
                text = "其他操作",
                modifier = Modifier.offset(x = (-16).dp)
            )
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "开启新学期",
                        summary = "复用当前课表设置，创建空课程的新课表",
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

    // 开启新学期
    OverlayDialog(
        title = "开启新学期",
        summary = "将复用当前课表的所有设置数据，创建一个清空课程的新课表",
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
                label = "新课表名称",
                modifier = Modifier.fillMaxWidth(),
                requestFocus = showNewSemesterDialog
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showNewSemesterDialog = false
                        newSemesterName = ""
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "创建",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (newSemesterName.isNotBlank()) {
                            val name = newSemesterName
                            if (name in scheduleNames) {
                                android.widget.Toast.makeText(
                                    context,
                                    "该课表名称已存在",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                scheduleViewModel.createNewSemesterSchedule(name)
                                showNewSemesterDialog = false
                                newSemesterName = ""
                                android.widget.Toast.makeText(
                                    context,
                                    "「${name}」创建成功",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
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
        title = "开始上课日期",
        show = showStartDateDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showStartDateDialog = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val maxDaysInMonth = remember(tempYear, tempMonth) { getDaysInMonth(tempYear, tempMonth) }
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
                    label = { "${it}月" },
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
                    label = { "${it}日" },
                    wrapAround = true,
                    textStyle = MiuixTheme.textStyles.title2,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showStartDateDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        val date = String.format("%04d/%02d/%02d", tempYear, tempMonth, tempDay)
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
        title = "选择当前周次",
        show = showCurrentWeekDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showCurrentWeekDialog = false }
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            NumberPicker(
                value = tempCurrentWeek,
                onValueChange = { tempCurrentWeek = it },
                range = 1..totalWeeks.coerceAtLeast(1),
                visibleItemCount = 3,
                itemHeight = 60.dp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showCurrentWeekDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定",
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
        title = "选择学期总周数",
        show = showTotalWeeksDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showTotalWeeksDialog = false }
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            NumberPicker(
                value = tempTotalWeeks,
                onValueChange = { tempTotalWeeks = it },
                range = 1..30,
                visibleItemCount = 3,
                itemHeight = 60.dp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showTotalWeeksDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定",
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

/** 右栏：节假日（状态由 HolidayManager 维护，不启 Activity）。顶部「更新」联网拉取对齐手机 Activity。 */
@Composable
private fun TabletHolidayPane(
    scrollBehavior: com.haooz.chedule.ui.basic.SharedScrollBehavior?,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    onUpdateReady: (() -> Unit) -> Unit = {},
    onLoadingChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentDate = remember { LocalDate.now() }
    var year by remember { mutableStateOf(currentDate.year) }
    var entries by remember { mutableStateOf(HolidayManager.load(context, year)) }
    var loading by remember { mutableStateOf(false) }
    val latestYear by rememberUpdatedState(year)

    val doUpdate = {
        if (!loading) {
            loading = true
            scope.launch(Dispatchers.IO) {
                val targetYear = latestYear
                val result = runCatching {
                    val conn = URL(
                        "https://unpkg.com/holiday-calendar@1.3.0/data/CN/$targetYear.json"
                    ).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    HolidayManager.parseApiResponse(text)
                }.getOrDefault(emptyList())
                withContext(Dispatchers.Main) {
                    HolidayManager.mergeApiEntries(context, targetYear, result)
                    if (targetYear == latestYear) entries = HolidayManager.load(context, latestYear)
                    loading = false
                    if (result.isNotEmpty()) {
                        // API 合并同样要重排提醒并刷小部件，不能只改本地 SP
                        CourseReminderHelper.onHolidayDataChanged(context)
                    }
                    val message =
                        if (result.isEmpty()) "获取失败或暂无数据" else "已更新 ${result.size} 条记录"
                    android.widget.Toast.makeText(
                        context, message, android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    val latestUpdate by rememberUpdatedState(doUpdate)
    LaunchedEffect(Unit) { onUpdateReady({ latestUpdate() }) }
    LaunchedEffect(loading) { onLoadingChange(loading) }

    HolidaySettingsScreen(
        scrollBehavior = scrollBehavior,
        liquidGlassBackdrop = liquidGlassBackdrop,
        year = year,
        entries = entries,
        onYearChange = { y ->
            year = y
            entries = HolidayManager.load(context, y)
        },
        reload = { entries = HolidayManager.load(context, year) },
    )
}
