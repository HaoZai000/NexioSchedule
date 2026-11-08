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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.LocalChromeStartInset
import com.haooz.chedule.ui.components.rememberTopBarMaterialAlpha
import com.haooz.chedule.ui.components.tabletNavRailStartPadding
import com.haooz.chedule.ui.components.text.miuix.Text
import com.haooz.chedule.ui.theme.scheduleAccentColor
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.LocalNativeTopBarHost
import com.haooz.chedule.ui.utils.NativeTopBarHost
import com.haooz.chedule.ui.utils.NativeTopBarMenuItem
import com.haooz.chedule.ui.utils.NativeTopBarState
import com.haooz.chedule.ui.utils.hostVerticalBarEdge
import com.haooz.softglass.backdrop.GlassBackdrop
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DAY_NAMES = listOf(
    CommonStr.weekdayMon, CommonStr.weekdayTue, CommonStr.weekdayWed, CommonStr.weekdayThu,
    CommonStr.weekdayFri, CommonStr.weekdaySat, CommonStr.weekdaySun,
)

/** 星期行一格的内容：日期文字 + 是否今天高亮（都不参与列数动画）。 */
private data class DayCellContent(
    val dayOfWeek: Int,
    val dateText: String?,
    val isToday: Boolean,
)

// 不用 Scaffold paddingValues：随当前 tab 顶栏高度变化，切页时内容位移，
// 叠加 SharedBlur 层 draw 阶段写入滞后一帧，表现为顶部慢一帧就位
// （原为 internal；MainScheduleScreen 在 :androidApp 引用，跨模块必须 public）
fun scheduleContentTopPadding(statusBarHeight: Dp, verticalBarEdge: Boolean): Dp {
    val appBar = CollapsedHeight
    // 折叠屏折叠态屏幕顶部没有状态栏，糊带按 112dp 算（与 ScheduleTopBar 的 topBarHeight 同源）
    val noTopStatusBar = verticalBarEdge
    val blurHeight = when {
        noTopStatusBar -> 120.dp - NoStatusBarBlurReduction
        statusBarHeight > 0.dp -> 120.dp + statusBarHeight
        else -> 160.dp
    }
    val weekRowBottom = statusBarHeight + appBar +
            (if (noTopStatusBar || statusBarHeight > 0.dp) 0.dp else 40.dp) + 40.dp
    val topBar = maxOf(blurHeight, appBar + statusBarHeight, weekRowBottom)
    // 内容顶恒为「星期行底 + 2dp」：糊带只是装饰，折叠屏上收窄 8dp 不该把内容一起拽上去
    return maxOf(weekRowBottom + 2.dp, appBar + topBar - 78.dp).coerceAtLeast(0.dp)
}

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
    // 标题/星期行的右侧额外内边距：避开右下角竖排的原生 tab bar（折叠屏 iPhone 折叠态）
    titleEndPadding: Dp = 0.dp,
    // 布局期上报「更多」占位槽的真实位置，供下拉菜单按实际位置定位（避免首帧 inset 未到导致偏位）
    onMoreSlotTop: (Float) -> Unit = {},
    // 上报顶栏滚动材质透明度，供「更多」控件收起态同步渐显渐隐
    onMoreMaterial: (Float) -> Unit = {},
    onMoreExpand: () -> Unit = {},
) {
    val nativeTopBarHost = LocalNativeTopBarHost.current
    val nativeTopRight = nativeTopBarHost?.ownsTopRight == true
    if (!visible || liquidGlassBackdrop == null) return
    // iOS 主界面右上角那一排（「更多」UIMenu + 课表切换）已由原生 chrome 接管
    // （见 iosApp/ContentView 的 mainMoreItem / mainSwitchItem）：有宿主导航即 iOS，
    // Compose 这一侧整排都不渲染。Android / desktop 没有宿主导航，照常显示。
    // 鸿蒙同理：顶栏留着 Compose 画，只把右上角两颗交给 ArkUI。
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
    // 组合期读 currentHeightPx 会拿到未测量的值，星期行会慢一帧就位；改布局期读
    ProgressiveBlurTopBar(
        backdrop = liquidGlassBackdrop,
        height = topBarHeight,
        topInsetAdjustment = nativeBarInset,
        resampleKey = blurResampleKey,
        sampleTrack = blurSampleTrack,
        // 无材质档没有模糊层，遮罩是顶栏唯一的材质：跟按钮背景一起出现
        maskAlpha = rememberTopBarMaterialAlpha(scrollBehavior),
    ) {
        Box {
            // 平板：标题避让左侧侧栏后左对齐（手机仍居中）
            val titleRailPadding = Modifier
                .padding(start = LocalChromeStartInset.current)
                .then(if (isTablet) tabletNavRailStartPadding().padding(start = 12.dp) else Modifier)
            val titleEndModifier =
                if (titleEndPadding > 0.dp) Modifier.padding(end = titleEndPadding) else Modifier
            // zIndex 必须挂在这层 Box 上，不能直接传给 CollapsibleTopAppBar：
            // 它的 modifier 参数只应用在内部那层 Layout 上（见 CollapsibleTopAppBar 末尾），
            // 挂到孙节点对它与 DayOfWeekRow 的排序无效 —— zIndex 只在同一父节点的兄弟间比较。
            Box(modifier = Modifier.zIndex(1f)) {
                CollapsibleTopAppBar(
                    title = titleText,
                    showLargeTitle = false,
                    titleStartAligned = isTablet,
                    titleModifier = titleRailPadding.then(titleEndModifier),
                    scrollBehavior = scrollBehavior,
                    // 平板左上角不放返回按钮
                    startAction = null,
                    onAlphaChanged = { backdrop, _ -> onMoreMaterial(backdrop) },
                    endAction = { backdropAlpha, shadowAlpha ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTablet) {
                                // pad：切换课表/课程管理已在侧栏，右上角直接放跳转周数 + 课表外观
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
                                        onClick = {
                                            onOpenSwitchSchedule()
                                        },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Normal.ConvertFile,
                                        contentDescription = SchedStr.switchSchedule(),
                                        iconSize = 27.dp,
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha
                                    )
                                }
                                // 「更多」按钮由下拉菜单组件自带（收起态即那颗按钮，唯一一份），这里只占位对齐
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
                    }
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
                modifier = Modifier.dayOfWeekTopPadding(statusBarHeight, noTopStatusBar, scrollBehavior)
            )
        }
    }
}

/** Compose 的 Color → 0xAARRGGBB（宿主侧要的是普通 int，不是 Color 的 packed 值）。 */
private fun Color.argbLong(): Long =
    toArgb().toLong() and 0xFFFFFFFFL

@Composable
private fun DayOfWeekRow(
    dayRange: List<Int>,
    currentDayOfWeek: Int,
    isCurrentWeek: Boolean,
    weekDates: List<LocalDate>,
    isReorganized: Boolean,
    isTablet: Boolean,
    /** 星期行右侧额外内边距：避开右下角竖排的原生 tab bar（折叠屏 iPhone 折叠态）。 */
    contentEndPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    // 列数 5 ↔ 6 ↔ 7 过渡：整条文字带按动画中的列数摊开/收拢
    // 只有这一条 40dp 的星期行参与，课表网格仍是原来的瞬时重排。
    val dayCount = rememberAnimatedDayCount(dayRange.size)

    // 每格内容：日期文字 + 是否今天高亮。
    // 退场的格子必须渲染「退场前」的那份内容
    val cells = remember(dayRange, weekDates, currentDayOfWeek, isCurrentWeek, isReorganized) {
        dayRange.map { dayOfWeek ->
            val index = dayOfWeek - 1
            val date = weekDates.getOrNull(index)
            DayCellContent(
                dayOfWeek = dayOfWeek,
                dateText = date?.formatMonthDay(),
                isToday = dayOfWeek == currentDayOfWeek && isCurrentWeek &&
                    (!isReorganized || date == todayLocalDate()),
            )
        }
    }
    // 列表缩短时被去掉的那几天不能当场消失：留到过渡结束，让它们滑出去 + 淡出。
    // exitCells 的写入走 SideEffect（下一帧才生效），缩短那一帧先用算出的同一份尾巴
    // 补画，否则会闪一帧「新内容按旧列数宽度排、右侧空一截」再弹回
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
    // 只有末尾两格可能露一半（其余恒为全露出），淡入淡出图层只挂给它们
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
                    // 侧栏避让放在原 padding(start=) 位置，避免撑高 dayOfWeekTopPadding
                    Modifier
                        .then(tabletNavRailStartPadding())
                        .padding(horizontal = 24.dp)
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
                val name = DAY_NAMES[cell.dayOfWeek - 1]()
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
                        text = name,
                        style = MiuixTheme.textStyles.footnote1.copy(
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isToday) todayHighlightColor
                        else MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    if (dateText != null) {
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

// 顶栏高度是测量后才写入的状态，组合期读会得 0；布局期读与测量同帧对齐。
// 未测量时用 CollapsedHeight+状态栏兜底（本顶栏 showLargeTitle=false，实测恒为该值）
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
    // 与 scheduleContentTopPadding 同源（内容顶 = 星期行底 + 2dp）；
    // 顶部没有状态栏的折叠态不补这 40dp，否则星期行整体压低
    val noTopStatusBar = verticalBarEdge
    val gapPx = if (noTopStatusBar || statusBarPx > 0) 0 else 40.dp.roundToPx()
    val top = statusBarPx + measuredBarPx + gapPx
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height + top) {
        placeable.place(0, top)
    }
}
