package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.util.formatMonthDay
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.activities.main.PageMoreMenuState
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.LocalChromeStartInset
import com.haooz.chedule.ui.components.tabletNavRailStartPadding
import com.haooz.chedule.ui.components.text.LocalTextMotionInstant
import com.haooz.chedule.ui.components.text.miuix.Text
import com.haooz.chedule.ui.theme.scheduleAccentColor
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.LocalNativeTopBarHost
import com.haooz.chedule.ui.utils.hostVerticalBarEdge
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import com.haooz.softglass.platform.isRenderEffectSupported
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.FastForward
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults.CollapsedHeight
import top.yukonga.miuix.kmp.layout.NoStatusBarBlurReduction
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.rememberTopBarMaterialState
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DAY_NAMES = listOf(
    CommonStr.weekdayMon, CommonStr.weekdayTue, CommonStr.weekdayWed, CommonStr.weekdayThu,
    CommonStr.weekdayFri, CommonStr.weekdaySat, CommonStr.weekdaySun,
)

/** 星期行一格的内容：日期文字 + 是否今天高亮。 */
private data class DayCellContent(
    val dayOfWeek: Int,
    val dateText: String?,
    val isToday: Boolean,
)

/**
 * 内容顶部留白。
 *
 * 不用 Scaffold 的 paddingValues：它随当前 tab 顶栏高度变化，切页时内容位移，
 * 叠加采样层的 draw 滞后会表现为顶部慢一帧就位。
 */
fun scheduleContentTopPadding(statusBarHeight: Dp, verticalBarEdge: Boolean): Dp {
    val appBar = CollapsedHeight
    // 折叠屏折叠态屏幕顶部没有状态栏，糊带按 112dp 算
    val noTopStatusBar = verticalBarEdge
    val blurHeight = when {
        noTopStatusBar -> 120.dp - NoStatusBarBlurReduction
        statusBarHeight > 0.dp -> 120.dp + statusBarHeight
        else -> 160.dp
    }
    val weekRowBottom = statusBarHeight + appBar +
        (if (noTopStatusBar || statusBarHeight > 0.dp) 0.dp else 40.dp) + 40.dp
    val topBar = maxOf(blurHeight, appBar + statusBarHeight, weekRowBottom)
    // 内容顶恒为「星期行底 + 2dp」：糊带只是装饰
    return maxOf(weekRowBottom + 2.dp, appBar + topBar - 78.dp).coerceAtLeast(0.dp)
}

/**
 * 课表 / 排班页顶栏的宿主注入数据。
 *
 * 翻周位置、当前周等直接读页面内的 PageSwitchState，这里只传外部动作与周数据。
 */
class SchedulePageChrome(
    val currentWeek: Int,
    val totalWeeks: Int,
    val isHoliday: Boolean,
    val dayRange: List<Int>,
    val currentDayOfWeek: Int,
    val weekDates: List<LocalDate>,
    val isReorganized: Boolean,
    val forcedDarkTheme: Boolean?,
    /** 共享壁纸采样层；页面会组合「壁纸 + 本页内容」给顶栏/菜单采样，避免采到自己 */
    val wallpaperBackdrop: GlassBackdrop? = null,
    /** 「更多」按钮/菜单宿主状态；null 表示不渲染 */
    val moreMenu: PageMoreMenuState? = null,
    /** 外层统一持有的本页内容采样层；null 时页面自建 */
    val contentBackdrop: LayerBackdrop? = null,
    val onOpenSwitchSchedule: () -> Unit = {},
    val onJumpWeek: () -> Unit = {},
    val onEnterCustomize: () -> Unit = {},
    val onCourseManage: () -> Unit = {},
    val isShiftMode: Boolean = false,
    val titleEndPadding: Dp = 0.dp,
    val blurResampleKey: Int = 0,
    val blurSampleTrack: () -> Float = { 0f },
    /** 本页内容采样层的录制指纹/强制重录谓词 */
    val contentRecordKey: Any? = null,
    val contentMustRecord: (() -> Boolean)? = null,
)

@Composable
fun ScheduleTopBar(
    visible: Boolean,
    pagerCurrentPage: Int,
    currentWeek: Int,
    isHoliday: Boolean,
    dayRange: List<Int>,
    currentDayOfWeek: Int,
    isCurrentWeek: Boolean,
    weekDates: List<LocalDate>,
    isReorganized: Boolean,
    onOpenSwitchSchedule: () -> Unit,
    onJumpWeek: () -> Unit = {},
    onEnterCustomize: () -> Unit = {},
    onCourseManage: () -> Unit = {},
    isTablet: Boolean = false,
    isShiftMode: Boolean = false,
    liquidGlassBackdrop: GlassBackdrop?,
    scrollBehavior: SharedScrollBehavior? = null,
    blurResampleKey: Int = 0,
    blurSampleTrack: () -> Float = { 0f },
    // 标题/星期行右侧额外内边距：避开右下角竖排的原生 tab bar
    titleEndPadding: Dp = 0.dp,
    // 布局期上报「更多」占位槽位置，供下拉菜单按实际位置定位
    onMoreSlotTop: (Float) -> Unit = {},
    // 上报顶栏材质透明度，供「更多」控件同步渐显渐隐
    onMoreMaterial: (Float) -> Unit = {},
    onMoreExpand: () -> Unit = {},
) {
    if (!visible || liquidGlassBackdrop == null) return

    val nativeTopBarHost = LocalNativeTopBarHost.current
    val nativeTopRight = nativeTopBarHost?.ownsTopRight == true
    // iOS/鸿蒙宿主接管右上角时，Compose 这一侧整排不渲染
    val hostOwnsTopRight = LocalHostNavigation.current != null || nativeTopRight
    val nativeBarInset = (LocalHostNavigation.current?.topBarInset ?: 0.0).dp

    val titleText = when {
        isHoliday -> SchedStr.onBreak()
        currentWeek < 1 -> SchedStr.semesterNotStarted()
        else -> CommonStr.weekN(pagerCurrentPage + 1)
    }

    // 竖排 tab bar 形态（折叠屏折叠态）：屏幕顶部没有状态栏，走另一套几何
    val noTopStatusBar = hostVerticalBarEdge()
    val statusBarHeight = (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() -
        nativeBarInset).coerceAtLeast(0.dp)
    val topBarHeight = when {
        noTopStatusBar -> 120.dp - NoStatusBarBlurReduction
        statusBarHeight > 0.dp -> 120.dp + statusBarHeight
        else -> 160.dp
    }
    // 本页唯一材质状态：渐变遮罩与按钮玻璃读同一个实例
    val material = rememberTopBarMaterialState(scrollBehavior)

    ProgressiveBlurTopBar(
        backdrop = liquidGlassBackdrop,
        height = topBarHeight,
        topInsetAdjustment = nativeBarInset,
        resampleKey = blurResampleKey,
        sampleTrack = blurSampleTrack,
        // 无材质档没有模糊层，遮罩是顶栏唯一的材质
        maskAlpha = material.alpha,
    ) {
        Box {
            // 平板标题避让左侧侧栏后左对齐；手机仍居中
            val titleRailPadding = Modifier
                .padding(start = LocalChromeStartInset.current)
                .then(if (isTablet) tabletNavRailStartPadding().padding(start = 12.dp) else Modifier)
            val titleEndModifier =
                if (titleEndPadding > 0.dp) Modifier.padding(end = titleEndPadding) else Modifier
            // zIndex 必须挂在这层 Box 上：CollapsibleTopAppBar 的 modifier 只作用在内层 Layout，
            // 挂到孙节点对它与 DayOfWeekRow 的排序无效
            Box(modifier = Modifier.zIndex(1f)) {
                CollapsibleTopAppBar(
                    title = titleText,
                    showLargeTitle = false,
                    titleStartAligned = isTablet,
                    titleModifier = titleRailPadding.then(titleEndModifier),
                    scrollBehavior = scrollBehavior,
                    materialState = material,
                    // 平板左上角留给标题，天气只在手机显示
                    startAction = if (isTablet) {
                        null
                    } else {
                        { _, _ -> ScheduleWeatherBadge() }
                    },
                    onAlphaChanged = { backdrop, _ -> onMoreMaterial(backdrop) },
                    endAction = { backdropAlpha, shadowAlpha ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isTablet) {
                                // 平板的切换课表/课程管理已在侧栏，右上角放跳转周数 + 课表外观
                                LiquidTopBarButton(
                                    onClick = onJumpWeek,
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Basic.FastForward,
                                    contentDescription = SchedStr.jumpToWeek(),
                                    iconSize = 23.dp,
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                                LiquidTopBarButton(
                                    onClick = onEnterCustomize,
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Background,
                                    contentDescription = SchedStr.scheduleAppearance(),
                                    iconSize = 23.dp,
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                            } else {
                                if (!isShiftMode && !hostOwnsTopRight) {
                                    LiquidTopBarButton(
                                        onClick = onOpenSwitchSchedule,
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Normal.ConvertFile,
                                        contentDescription = SchedStr.switchSchedule(),
                                        iconSize = 27.dp,
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                }
                                // 「更多」按钮由下拉菜单组件自带，这里只占位对齐
                                Spacer(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .then(
                                            if (!hostOwnsTopRight) Modifier
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = onMoreExpand,
                                                )
                                                .semantics { contentDescription = CommonStr.more() }
                                            else Modifier
                                        )
                                        .onGloballyPositioned { onMoreSlotTop(it.positionInRoot().y) }
                                )
                            }
                        }
                    },
                )
            }
            DayOfWeekRow(
                dayRange = dayRange,
                currentDayOfWeek = currentDayOfWeek,
                isCurrentWeek = isCurrentWeek,
                weekDates = weekDates,
                isReorganized = isReorganized,
                isTablet = isTablet,
                contentEndPadding = titleEndPadding,
                modifier = Modifier.dayOfWeekTopPadding(statusBarHeight, noTopStatusBar, scrollBehavior),
            )
        }
    }
}

@Composable
private fun DayOfWeekRow(
    dayRange: List<Int>,
    currentDayOfWeek: Int,
    isCurrentWeek: Boolean,
    weekDates: List<LocalDate>,
    isReorganized: Boolean,
    isTablet: Boolean,
    contentEndPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    // 列数 5 ↔ 6 ↔ 7 过渡：整条文字带按动画中的列数摊开/收拢
    val dayCount = rememberAnimatedDayCount(dayRange.size)

    val cells = remember(dayRange, weekDates, currentDayOfWeek, isCurrentWeek, isReorganized) {
        dayRange.map { dayOfWeek ->
            val date = weekDates.getOrNull(dayOfWeek - 1)
            DayCellContent(
                dayOfWeek = dayOfWeek,
                dateText = date?.formatMonthDay(),
                isToday = dayOfWeek == currentDayOfWeek && isCurrentWeek &&
                    (!isReorganized || date == todayLocalDate()),
            )
        }
    }
    // 列表缩短时被去掉的几天留到过渡结束再消失，否则会闪一帧右侧空一截
    var exitCells by remember { mutableStateOf(emptyList<DayCellContent>()) }
    val previousCells = remember { arrayOfNulls<List<DayCellContent>>(1) }
    val previous = previousCells[0]
    val removedCells =
        if (previous != null && previous.size > cells.size) previous.drop(cells.size) else emptyList()
    if (removedCells.isNotEmpty()) {
        SideEffect { exitCells = removedCells }
    }
    SideEffect { previousCells[0] = cells }
    LaunchedEffect(dayCount.settleTick) {
        if (exitCells.isNotEmpty()) exitCells = emptyList()
    }
    val drawnCells = when {
        exitCells.isNotEmpty() -> cells + exitCells
        removedCells.isNotEmpty() -> cells + removedCells
        else -> cells
    }
    // 只有末尾两格可能露一半，淡入淡出图层只挂给它们
    val revealFrom = drawnCells.size - 2
    val revealBlurPx = with(LocalDensity.current) {
        if (isRenderEffectSupported()) DAY_CELL_REVEAL_BLUR_DP.dp.toPx() else 0f
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(start = LocalChromeStartInset.current)
            .then(
                if (isTablet) {
                    Modifier.then(tabletNavRailStartPadding()).padding(horizontal = 24.dp)
                } else {
                    Modifier.padding(end = 2.dp + contentEndPadding)
                }
            )
    ) {
        Spacer(modifier = Modifier.width(scheduleSectionColumnWidth(isTablet)))
        DayColumnsLayout(
            count = { dayCount.value.value },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            drawnCells.forEachIndexed { cellIndex, cell ->
                val isToday = cell.isToday
                val dateText = cell.dateText
                val todayHighlightColor = scheduleAccentColor()
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .then(
                            if (cellIndex >= revealFrom) {
                                Modifier.dayCellReveal(dayCount, cellIndex, revealBlurPx)
                            } else {
                                Modifier
                            }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = DAY_NAMES[cell.dayOfWeek - 1](),
                        style = MiuixTheme.textStyles.footnote1.copy(
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isToday) todayHighlightColor else MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    if (dateText != null) {
                        // 翻周时一周 7 个日期同帧全变，一起糊一下反而像整行闪了一帧
                        CompositionLocalProvider(LocalTextMotionInstant provides true) {
                            Text(
                                text = dateText,
                                style = MiuixTheme.textStyles.footnote2,
                                color = if (isToday) todayHighlightColor
                                else MiuixTheme.colorScheme.onSurfaceVariantActions,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

// 顶栏高度是测量后才写入的状态，组合期读会得 0；布局期读与测量同帧对齐。
private fun Modifier.dayOfWeekTopPadding(
    statusBarHeight: Dp,
    verticalBarEdge: Boolean,
    scrollBehavior: SharedScrollBehavior?,
): Modifier = layout { measurable, constraints ->
    val statusBarPx = statusBarHeight.roundToPx()
    val barHeightPx = scrollBehavior?.currentHeightPx ?: 0f
    val measuredBarPx = if (barHeightPx > 0f) {
        barHeightPx.roundToInt()
    } else {
        CollapsedHeight.roundToPx() + statusBarPx
    }
    val noTopStatusBar = verticalBarEdge
    val gapPx = if (noTopStatusBar || statusBarPx > 0) 0 else 40.dp.roundToPx()
    val top = statusBarPx + measuredBarPx + gapPx
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height + top) {
        placeable.place(0, top)
    }
}
