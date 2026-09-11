package com.haooz.chedule.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults.CollapsedHeight
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val MM_DD_FORMATTER = DateTimeFormatter.ofPattern("MM/dd")

/**
 * 课程表内容的顶部偏移（纯计算、切页不变）。
 *
 * 为什么不用 Scaffold 实测的 paddingValues：
 * paddingValues 反映的是「当前显示的是哪个 tab 的顶栏」的高度 —— 今日/我的页约 92+状态栏，
 * 课程表页因为多一行星期行更高。切 tab 时它会变化，导致课程表内容的顶部偏移
 * 在切页瞬间整体位移一次。而课程卡片的玻璃模糊采样的是 SharedBlurBackdrop 的共享层，
 * 该层是在 **draw 阶段**写入的 mutableState（SharedBlurBackdrop.kt: sharedSampledLayer），
 * 天生滞后一帧；再叠加 layerCoordinates 来自 onGloballyPositioned（同样晚一帧），
 * 于是这一次位移就会看到「顶部距离慢一帧才就位」。无壁纸时模糊层是一片纯色、位移不可见，
 * 有壁纸时才暴露出来（对应「今日页显示壁纸」开关能复现/消除该现象）。
 *
 * 这里改成由课程表顶栏自身几何直接算出，切页时内容不再位移。
 * 与 ScheduleTopBar 的实际组成一一对应（与原始 currentHeightPx + paddingValues - 78dp 一致）：
 *  - CollapsibleTopAppBar：折叠态标题栏高恒为 CollapsedHeight（showLargeTitle = false，
 *    且不含状态栏 —— CollapsibleTopAppBar.kt 里上报高度的 layout 位于 windowInsetsPadding 之内）
 *  - DayOfWeekRow：位于状态栏 + 标题栏之下，高 40dp（无状态栏时再补 40dp 的顶距）
 *  - ProgressiveBlurTopBar：模糊层高度（有状态栏 120dp+状态栏 / 无状态栏 160dp）与内容取最大
 *  - gradientMaskHeight（CollapsedHeight + 110dp）只是渐变遮罩的绘制高度，不参与布局
 */
internal fun scheduleContentTopPadding(statusBarHeight: Dp): Dp {
    // 折叠态标题栏（showLargeTitle = false，不含状态栏）
    val appBar = CollapsedHeight
    // 模糊层高度：有状态栏 120dp+状态栏，无状态栏 160dp
    val blurHeight = if (statusBarHeight > 0.dp) 120.dp + statusBarHeight else 160.dp
    // 星期行底部 = 状态栏 + 折叠态标题栏 + (无状态栏时再补 40dp) + 星期行 40dp
    val weekRowBottom = statusBarHeight + appBar +
            (if (statusBarHeight > 0.dp) 0.dp else 40.dp) + 40.dp
    // 顶栏实测高度 = 模糊层 / 折叠态标题栏(含状态栏) / 星期行底部 三者取最大
    val topBar = maxOf(blurHeight, appBar + statusBarHeight, weekRowBottom)
    // 与原实现一致：折叠态标题栏 + 顶栏实测高度 - 78dp（抵消两者重叠部分）
    return (appBar + topBar - 78.dp).coerceAtLeast(0.dp)
}

@Composable
internal fun ScheduleTopBar(
    visible: Boolean,
    navBarStyle: String,
    pagerCurrentPage: Int,
    currentWeek: Int,
    isHoliday: Boolean,
    isViewingCurrentWeek: Boolean,
    dayRange: List<Int>,
    currentDayOfWeek: Int,
    isCurrentWeek: Boolean,
    weekDates: List<LocalDate>,
    onBackToCurrentWeek: () -> Unit,
    onOpenSwitchSchedule: () -> Unit,
    onMoreClick: () -> Unit = {},
    isTablet: Boolean = false,
    isShiftMode: Boolean = false,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    scrollBehavior: SharedScrollBehavior? = null,
    showMorePopup: Boolean = false,
) {
    if (!visible || liquidGlassBackdrop == null) return

    val buttonFraction = remember { Animatable(0f) }
    LaunchedEffect(showMorePopup) {
        if (showMorePopup) {
            buttonFraction.animateTo(
                1f,
                tween(340, easing = CubicBezierEasing(0.34f, 1f, 0.3f, 1f))
            )
        } else {
            buttonFraction.animateTo(
                0f,
                tween(420, easing = CubicBezierEasing(0.34f, 1.2f, 0.3f, 1f))
            )
        }
    }

    val titleText = when {
        isHoliday -> "放假中"
        currentWeek < 1 -> "学期未开始"
        else -> "第${pagerCurrentPage + 1}周"
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeight = if (statusBarHeight > 0.dp) 120.dp + statusBarHeight else 160.dp
    // 注意：星期行的顶部间距不要在组合期读 scrollBehavior.currentHeightPx ——
    // 它是顶栏测量后才写入的状态，组合期读会拿到未测量的值，导致星期行慢一帧就位。
    // 改由下面的 dayOfWeekTopPadding 在布局阶段读取。
    ProgressiveBlurTopBar(backdrop = liquidGlassBackdrop, height = topBarHeight) {
        Box {
            CollapsibleTopAppBar(
                title = if (navBarStyle == "rail") "" else titleText,
                showLargeTitle = false,
                showGradientOverlay = true,
                modifier = Modifier.zIndex(1f),
                gradientMaskHeight = CollapsedHeight + 110.dp,
                scrollBehavior = scrollBehavior,
                startAction = { backdropAlpha, shadowAlpha ->
                    if (navBarStyle == "rail") {
                        Text(
                            text = titleText,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    } else {
                        AnimatedVisibility(
                            visible = !isViewingCurrentWeek,
                            enter = fadeIn(animationSpec = tween(180)),
                            exit = fadeOut(animationSpec = tween(120))
                        ) {
                            LiquidTopBarButton(
                                onClick = onBackToCurrentWeek,
                                backdrop = liquidGlassBackdrop,
                                icon = MiuixIcons.Medium.Reset,
                                contentDescription = "返回本周",
                                iconSize = 24.dp,
                                iconOffset = DpOffset(x = 0.dp, y = (-1).dp),
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha
                            )
                        }
                    }
                },
                endAction = { backdropAlpha, shadowAlpha ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (navBarStyle == "rail") {
                            AnimatedVisibility(
                                visible = !isViewingCurrentWeek,
                                enter = fadeIn(animationSpec = tween(180)),
                                exit = fadeOut(animationSpec = tween(120))
                            ) {
                                LiquidTopBarButton(
                                    onClick = onBackToCurrentWeek,
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Medium.Reset,
                                    contentDescription = "返回本周",
                                    iconOffset = DpOffset(x = 0.dp, y = (-1).dp),
                                    iconSize = 24.dp,
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha
                                )
                            }
                        }
                        if (!isShiftMode) {
                            LiquidTopBarButton(
                                onClick = {
                                    onOpenSwitchSchedule()
                                },
                                backdrop = liquidGlassBackdrop,
                                icon = MiuixIcons.Normal.ConvertFile,
                                contentDescription = "课表切换",
                                iconSize = 27.dp,
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha
                            )
                        }
                        LiquidTopBarButton(
                            onClick = {
                                onMoreClick()
                            },
                            backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.More,
                            contentDescription = "更多",
                            iconSize = 23.dp,
                            backdropAlpha = backdropAlpha,
                            shadowAlpha = shadowAlpha,
                            modifier = Modifier.offset {
                                    val f = buttonFraction.value
                                    IntOffset(
                                        x = (-100 * f).dp.roundToPx(),
                                        y = (45 * f).dp.roundToPx()
                                    )
                                }
                        )
                    }
                }
            )
            // 星期行绘制在顶栏下方，不受 CollapsibleTopAppBar 折叠影响
            DayOfWeekRow(
                dayRange = dayRange,
                currentDayOfWeek = currentDayOfWeek,
                isCurrentWeek = isCurrentWeek,
                weekDates = weekDates,
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
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(
                if (isTablet) Modifier.padding(horizontal = 24.dp) else Modifier.padding(end = 2.dp)
            )
    ) {
        Spacer(modifier = Modifier.width(if (isTablet) 56.dp else 36.dp))
        dayRange.forEach { dayOfWeek ->
            val index = dayOfWeek - 1
            val name = DAY_NAMES[index]
            val isToday = dayOfWeek == currentDayOfWeek && isCurrentWeek

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

/**
 * 星期行的顶部间距 = 状态栏 + 顶栏实测高度（无状态栏时再补 40dp）。
 *
 * 顶栏高度（scrollBehavior.currentHeightPx）是顶栏测量完成后才写入的状态：
 * 在组合期读会拿到未测量的值（0），星期行要等下一帧重组才就位。
 * 这里改为在布局阶段读取，与顶栏测量同帧对齐；尚未测量时用
 * CollapsedHeight + 状态栏兜底（本顶栏固定 showLargeTitle = false，实测高度恒为该值）。
 */
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
    // 与 Modifier.padding(top = ...) 等价：只增加自身高度并把内容下移，不改变宽度约束
    layout(placeable.width, placeable.height + top) {
        placeable.place(0, top)
    }
}
