package com.haooz.chedule.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults.CollapsedHeight
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    scrollBehavior: SharedScrollBehavior? = null
) {
    if (!visible || liquidGlassBackdrop == null) return

    val hapticFeedback = LocalHapticFeedback.current

    val titleText = when {
        isHoliday -> "放假中"
        currentWeek < 1 -> "学期未开始"
        else -> "第${pagerCurrentPage + 1}周"
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeight = if (statusBarHeight > 0.dp) 120.dp + statusBarHeight else 160.dp
    val topBarHeightDp = with(LocalDensity.current) {
        (scrollBehavior?.currentHeightPx ?: 0f).toDp() + if (statusBarHeight > 0.dp) 0.dp else 40.dp
    }
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
                                icon = MiuixIcons.Reset,
                                contentDescription = "返回本周",
                                iconSize = 25.dp,
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha
                            )
                        }
                    }
                },
                endAction = { backdropAlpha, shadowAlpha ->
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
                                iconSize = 22.dp,
                                modifier = Modifier.padding(end = 8.dp),
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                        LiquidTopBarButton(
                            onClick = {
                                onMoreClick()
                            },
                            backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.More,
                            contentDescription = "更多",
                            iconSize = 23.dp,
                            backdropAlpha = backdropAlpha,
                            shadowAlpha = shadowAlpha
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
                modifier = Modifier.padding(top = statusBarHeight + topBarHeightDp)
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
        val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        dayRange.forEach { dayOfWeek ->
            val index = dayOfWeek - 1
            val name = dayNames[index]
            val isToday = dayOfWeek == currentDayOfWeek && isCurrentWeek
            Box(
                modifier = Modifier.weight(1f).height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = name,
                        style = MiuixTheme.textStyles.footnote1.copy(
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isToday) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurface
                    )
                    if (weekDates.isNotEmpty() && index < weekDates.size) {
                        val dateText = weekDates[index].format(
                            DateTimeFormatter.ofPattern("MM/dd")
                        )
                        Text(
                            text = dateText,
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (isToday) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }
        }
    }
}
