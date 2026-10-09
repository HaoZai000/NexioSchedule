package com.haooz.chedule.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults.CollapsedHeight
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val MM_DD_FORMATTER = DateTimeFormatter.ofPattern("MM/dd")

// 不用 Scaffold paddingValues：随当前 tab 顶栏高度变化，切页时内容位移，
// 叠加 SharedBlur 层 draw 阶段写入滞后一帧，表现为顶部慢一帧就位
internal fun scheduleContentTopPadding(statusBarHeight: Dp): Dp {
    val appBar = CollapsedHeight
    val blurHeight = if (statusBarHeight > 0.dp) 120.dp + statusBarHeight else 160.dp
    val weekRowBottom = statusBarHeight + appBar +
            (if (statusBarHeight > 0.dp) 0.dp else 40.dp) + 40.dp
    val topBar = maxOf(blurHeight, appBar + statusBarHeight, weekRowBottom)
    return (appBar + topBar - 78.dp).coerceAtLeast(0.dp)
}

@Composable
internal fun ScheduleTopBar(
    visible: Boolean,
    pagerCurrentPage: Int,
    currentWeek: Int,
    isHoliday: Boolean,
    isViewingCurrentWeek: Boolean,
    dayRange: List<Int>,
    currentDayOfWeek: Int,
    isCurrentWeek: Boolean,
    weekDates: List<LocalDate>,
    isReorganized: Boolean,
    onBackToCurrentWeek: () -> Unit,
    onOpenSwitchSchedule: () -> Unit,
    onJumpWeek: () -> Unit = {},
    onEnterCustomize: () -> Unit = {},
    onCourseManage: () -> Unit = {},
    isTablet: Boolean = false,
    isShiftMode: Boolean = false,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    scrollBehavior: SharedScrollBehavior? = null,
    blurResampleKey: Int = 0,
    blurSampleTrack: () -> Float = { 0f },
    /**
     * 渐变遮罩色。共享顶栏槽里三根顶栏叠在一起平移淡入淡出，外层主题跟的是
     * **当前 tab**（见 MainActivity 的 `forcedDark`），切页瞬间会变 —— 不显式锁色
     * 就会在「课程表深色 → 设置页浅色」时遮罩整体跳浅。由调用方传入本页锁定的 surface。
     */
    gradientColorOverride: Color? = null,
) {
    if (!visible || liquidGlassBackdrop == null) return

    val titleText = when {
        isHoliday -> "放假中"
        currentWeek < 1 -> "学期未开始"
        else -> "第${pagerCurrentPage + 1}周"
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeight = if (statusBarHeight > 0.dp) 120.dp + statusBarHeight else 160.dp
    // 组合期读 currentHeightPx 会拿到未测量的值，星期行会慢一帧就位；改布局期读
    ProgressiveBlurTopBar(
        backdrop = liquidGlassBackdrop,
        height = topBarHeight,
        resampleKey = blurResampleKey,
        sampleTrack = blurSampleTrack,
    ) {
        Box {
            // 平板：标题避让左侧侧栏后左对齐（手机仍居中）
            val titleRailPadding =
                if (isTablet) tabletNavRailStartPadding().padding(start = 12.dp) else Modifier
            // zIndex 必须挂在这层 Box 上，不能直接传给 CollapsibleTopAppBar：
            // 它的 modifier 参数只应用在内部那层 Layout 上（见 CollapsibleTopAppBar 末尾），
            // 挂到孙节点对它与 DayOfWeekRow 的排序无效 —— zIndex 只在同一父节点的兄弟间比较。
            Box(modifier = Modifier.zIndex(1f)) {
                CollapsibleTopAppBar(
                    title = titleText,
                    showLargeTitle = false,
                    showGradientOverlay = true,
                    gradientOverlayScrollTriggered = true,
                    titleStartAligned = isTablet,
                    titleModifier = titleRailPadding,
                    titleAction = {
                        IconButton(
                            onClick = onJumpWeek,
                            modifier = Modifier.padding(start = 4.dp),
                            minWidth = 32.dp,
                            minHeight = 32.dp,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Basic.ArrowUpDown,
                                contentDescription = "跳转周数",
                                modifier = Modifier.size(20.dp),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    gradientMaskHeight = CollapsedHeight + 110.dp,
                    gradientColorOverride = gradientColorOverride,
                    scrollBehavior = scrollBehavior,
                    // 手机：课表切换移到最左侧；平板已在侧栏
                    startAction = if (isTablet || isShiftMode) null else { backdropAlpha, shadowAlpha ->
                        LiquidTopBarButton(
                            onClick = { onOpenSwitchSchedule() },
                            backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.Normal.ConvertFile,
                            contentDescription = "课表切换",
                            iconSize = 27.dp,
                            backdropAlpha = backdropAlpha,
                            shadowAlpha = shadowAlpha
                        )
                    },
                    endAction = { backdropAlpha, shadowAlpha ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTablet) {
                                // pad：切换课表/课程管理已在侧栏，右上角只放课表外观
                                LiquidTopBarButton(
                                    onClick = onEnterCustomize,
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Background,
                                    contentDescription = "课表外观",
                                    iconSize = 23.dp,
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                            } else {
                                if (!isShiftMode) {
                                    LiquidTopBarButton(
                                        onClick = onCourseManage,
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Backup,
                                        contentDescription = "课程管理",
                                        iconSize = 23.dp,
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                }
                                LiquidTopBarButton(
                                    onClick = onEnterCustomize,
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Background,
                                    contentDescription = "课表外观",
                                    iconSize = 23.dp,
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
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
                modifier = Modifier.dayOfWeekTopPadding(statusBarHeight, scrollBehavior)
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(
                if (isTablet) {
                    // 侧栏避让放在原 padding(start=) 位置，避免撑高 dayOfWeekTopPadding
                    Modifier
                        .then(tabletNavRailStartPadding())
                        .padding(horizontal = 24.dp)
                } else {
                    Modifier.padding(end = 2.dp)
                }
            )
    ) {
        val year = (weekDates.firstOrNull()?.year ?: LocalDate.now().year).toString()
        Column(
            modifier = Modifier
                .width(if (isTablet) 56.dp else 36.dp)
                .height(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = year,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
        }
        dayRange.forEach { dayOfWeek ->
            val index = dayOfWeek - 1
            val name = DAY_NAMES[index]
            val isToday = dayOfWeek == currentDayOfWeek && isCurrentWeek &&
                (!isReorganized || weekDates.getOrNull(index) == LocalDate.now())

            val todayHighlightColor = Color(0xFF3482FF)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = name,
                        style = MiuixTheme.textStyles.footnote1.copy(
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isToday) todayHighlightColor
                        else MiuixTheme.colorScheme.onSurface
                    )
                    if (weekDates.isNotEmpty() && index < weekDates.size) {
                        val dateText = remember(weekDates[index]) {
                            weekDates[index].format(MM_DD_FORMATTER)
                        }
                        Text(
                            text = dateText,
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (isToday) todayHighlightColor
                            else MiuixTheme.colorScheme.onSurfaceVariantActions
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
    scrollBehavior: SharedScrollBehavior?,
): Modifier = layout { measurable, constraints ->
    val statusBarPx = statusBarHeight.roundToPx()
    val barHeightPx = scrollBehavior?.currentHeightPx ?: 0f
    val measuredBarPx = if (barHeightPx > 0f) {
        barHeightPx.roundToInt()
    } else {
        CollapsedHeight.roundToPx() + statusBarPx
    }
    val top = statusBarPx + measuredBarPx + if (statusBarPx > 0) 0 else 40.dp.roundToPx()
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height + top) {
        placeable.place(0, top)
    }
}
