package com.haooz.chedule.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AppreciateAuthorScreen
import com.haooz.chedule.ui.activities.AiImportScreen
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.ChangelogScreen
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
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.chedule.ui.utils.overScrollVertical
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

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
    Changelog("更新日志", "其他"),
    Communication("交流与反馈", "其他"),
}

/** 平板设置选中项：MainActivity 叠层读它画固定标题 */
object TabletSettingsUiState {
    var selected by mutableStateOf(TabletSettingsDest.Semester)
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
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
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

/** pad 设置左右栏顶部糊层高度：120.dp + 状态栏 */
private val TabletPaneBlurHeight: Dp
    @Composable get() {
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        return 80.dp + statusBar
    }

/**
 * 本栏顶部：渐变模糊常驻；表面色遮罩仅在上滑后出现。
 * 糊层采样本栏本地 backdrop（兄弟节点），避免与全局层循环采样。
 */
@Composable
private fun TabletPaneTopChrome(
    scrolledPx: Float,
    backdrop: com.kyant.backdrop.Backdrop?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 16.dp.toPx() }
    // 过阈值后固定时长淡入/淡出，不随滚动距离改变透明度
    val showMask = scrolledPx > thresholdPx
    val maskAnim = remember { Animatable(0f) }
    LaunchedEffect(showMask) {
        maskAnim.animateTo(
            targetValue = if (showMask) 1f else 0f,
            animationSpec = tween(durationMillis = 500),
        )
    }
    val maskAlpha = maskAnim.value
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
                .graphicsLayer { alpha = maskAlpha }
                .background(
                    Brush.verticalGradient(
                        0f to gradientColor.copy(alpha = 0.88f),
                        0.4f to gradientColor.copy(alpha = 0.62f),
                        0.7f to gradientColor.copy(alpha = 0.38f),
                        0.88f to gradientColor.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                    )
                )
        )
    }
}

/** 只观察、不消费的滚动累计：正数表示内容已上滑；到顶/顶部回弹时清零 */
@Composable
private fun rememberPaneScrollTracker(
    onScrollPx: (Float) -> Unit,
): NestedScrollConnection {
    val currentOnScrollPx by rememberUpdatedState(onScrollPx)
    val acc = remember { floatArrayOf(0f) }
    return remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                when {
                    consumed.y != 0f -> {
                        acc[0] = (acc[0] - consumed.y).coerceAtLeast(0f)
                    }
                    // 列表已在顶部：继续下拉/回弹时未消费的向下位移 → 遮罩应收起
                    available.y > 0f -> {
                        acc[0] = 0f
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
    val chromeTop =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults.CollapsedHeight +
            12.dp

    // 左栏滚动用列表状态驱动遮罩；右栏用只观察的 nestedScroll（不挂在列表上）
    var leftScrollPx by remember { mutableFloatStateOf(0f) }
    var rightScrollPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(selected) { rightScrollPx = 0f }

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
    val rightTrack = rememberPaneScrollTracker { rightScrollPx = it }

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
                            bottom = 120.dp,
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
                                        dests.forEach { dest ->
                                            val jumpActivity =
                                                dest == TabletSettingsDest.EducationalImport
                                            ArrowPreference(
                                                title = dest.title,
                                                holdDownState = !jumpActivity && dest == selected,
                                                onClick = {
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
                                onEditConfig = { _, _ -> },
                                onCreateConfig = {},
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
                                AboutScreen(onBack = {}, liquidGlassBackdrop = aboutBackdrop)
                            }

                            TabletSettingsDest.Appreciate -> AppreciateAuthorScreen(
                                scrollBehavior = null,
                            )

                            TabletSettingsDest.Changelog -> ChangelogScreen(
                                scrollBehavior = null,
                            )

                            TabletSettingsDest.Communication -> CommunicationScreen(
                                scrollBehavior = null,
                            )
                        }
                    }
                }
                TabletPaneTopChrome(
                    scrolledPx = rightScrollPx,
                    backdrop = rightPaneBackdrop,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
    }
}

private val Color_Black_08 = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.08f)
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

    val chromeTop =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
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

/** 右栏：节假日（状态由 HolidayManager 维护，不启 Activity） */
@Composable
private fun TabletHolidayPane(
    scrollBehavior: com.haooz.chedule.ui.basic.SharedScrollBehavior?,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
) {
    val context = LocalContext.current
    val currentDate = remember { LocalDate.now() }
    var year by remember { mutableStateOf(currentDate.year) }
    var entries by remember { mutableStateOf(HolidayManager.load(context, year)) }

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
