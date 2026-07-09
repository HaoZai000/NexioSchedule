package com.haooz.chedule.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.utils.isAppDarkTheme
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.NavigationRailDefaults
import top.yukonga.miuix.kmp.basic.NavigationRailState
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarCapsuleButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 主界面顶栏：包含周数标题、返回本周按钮、课表切换按钮、更多菜单（跳转周数 / 课表外观），
 * 以及下方的星期日期行。
 *
 * @param visible 是否显示顶栏（仅在课程表 tab 显示）
 * @param navBarStyle "rail" 或 "standard"，rail 模式会左留白避开侧栏
 * @param railState NavigationRail 的展开/收起状态（仅 navBarStyle == "rail" 时使用）
 * @param pagerCurrentPage 当前 pager 页码（用于显示"第N周"）
 * @param currentWeek 当前实际周次（可能超出 totalWeeks）
 * @param totalWeeks 本学期总周数
 * @param isViewingCurrentWeek 是否正在查看本周（控制返回本周按钮显隐）
 * @param titleBarHeight 上次测量的标题栏高度
 * @param topAppBarColors 顶栏模糊颜色配置
 * @param backdrop 模糊 backdrop
 * @param dayRange 显示的星期范围（如 1..5 或包含周末）
 * @param currentDayOfWeek 今天是星期几（1=周一 ... 7=周日）
 * @param isCurrentWeek pager 是否停留在本周
 * @param weekDates 当前周的日期列表（周一到周日）
 * @param onBackToCurrentWeek 点击"返回本周"按钮
 * @param onOpenSwitchSchedule 点击"课表切换"按钮
 * @param onJumpWeek 选择"跳转周数"菜单项
 * @param onOpenCustomize 选择"课表外观"菜单项
 * @param onTitleBarMeasured 测量到标题栏高度时回调
 */
@Composable
internal fun ScheduleTopBar(
    visible: Boolean,
    navBarStyle: String,
    railState: NavigationRailState? = null,
    pagerCurrentPage: Int,
    currentWeek: Int,
    totalWeeks: Int,
    isViewingCurrentWeek: Boolean,
    titleBarHeight: Dp,
    topAppBarColors: BlurColors,
    backdrop: LayerBackdrop,
    dayRange: List<Int>,
    currentDayOfWeek: Int,
    isCurrentWeek: Boolean,
    weekDates: List<LocalDate>,
    onBackToCurrentWeek: () -> Unit,
    onOpenSwitchSchedule: () -> Unit,
    onJumpWeek: () -> Unit,
    onOpenCustomize: () -> Unit,
    onTitleBarMeasured: (Dp) -> Unit,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null
) {
    if (!visible) return

    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val themePrefs = remember { context.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE) }
    val appStyle = com.haooz.chedule.ui.utils.rememberAppStyle()
    var showMorePopup by remember { mutableStateOf(false) }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val tabletTopPadding = if (statusBarPadding > 0.dp) statusBarPadding + 4.dp else 40.dp

    val railPaddingStart by animateDpAsState(
        targetValue = if (appStyle == "liquidglass" && navBarStyle == "rail") {
            0.dp
        } else if (railState != null && railState.isExpanded) {
            NavigationRailDefaults.ExpandedWidth
        } else if (navBarStyle == "rail") {
            NavigationRailDefaults.MinWidth
        } else {
            0.dp
        },
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "topBarRailPadding",
    )

    val tintColor = MiuixTheme.colorScheme.surface

    Box(
        modifier = Modifier.padding(start = railPaddingStart)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleBarHeight + if (appStyle == "liquidglass" && liquidGlassBackdrop != null) 70.dp else 40.dp)
                .then(
                    if (appStyle == "liquidglass" && liquidGlassBackdrop != null) {
                        Modifier.drawPlainBackdrop(
                            backdrop = liquidGlassBackdrop,
                            shape = { RectangleShape },
                            effects = {
                                blur(4f.dp.toPx())
                                runtimeShaderEffect(
                                    "AlphaMask",
                                    """
    uniform shader content;
    uniform float2 size;
    layout(color) uniform half4 tint;
    uniform float tintIntensity;

    half4 main(float2 coord) {
        float blurAlpha = smoothstep(size.y, size.y * 0.6, coord.y);
        float tintAlpha = smoothstep(size.y, size.y * 0.7, coord.y);
        return mix(content.eval(coord) * blurAlpha, tint * tintAlpha, tintIntensity);
    }""",
                                    "content"
                                ) {
                                    setFloatUniform("size", size.width, size.height)
                                    setColorUniform("tint", tintColor)
                                    setFloatUniform("tintIntensity", 0.2f)
                                }
                            }
                        )
                    } else if (appStyle != "liquidglass") {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            colors = topAppBarColors
                        )
                    } else {
                        Modifier
                    }
                )
        )
        Column {
            SmallTopAppBar(
                title = if (navBarStyle == "rail" && appStyle == "liquidglass" && liquidGlassBackdrop != null) "" else when {
                    currentWeek > totalWeeks -> "放假中"
                    currentWeek < 1 -> "学期未开始"
                    else -> "第${pagerCurrentPage + 1}周"
                },
                color = ComposeColor.Transparent,
                modifier = Modifier
                    .zIndex(1f)
                    .onGloballyPositioned { coordinates ->
                        onTitleBarMeasured(with(density) { coordinates.size.height.toDp() })
                    },
                navigationIcon = {
                    if (navBarStyle == "rail" && appStyle == "liquidglass" && liquidGlassBackdrop != null) {
                        Text(
                            text = when {
                                currentWeek > totalWeeks -> "放假中"
                                currentWeek < 1 -> "学期未开始"
                                else -> "第${pagerCurrentPage + 1}周"
                            },
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    } else {
                        AnimatedVisibility(
                            visible = !isViewingCurrentWeek && navBarStyle != "rail",
                            enter = fadeIn(animationSpec = tween(180)),
                            exit = fadeOut(animationSpec = tween(120))
                        ) {
                            if (appStyle == "liquidglass" && liquidGlassBackdrop != null) {
                                LiquidTopBarButton(
                                    onClick = onBackToCurrentWeek,
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Medium.Reset,
                                    contentDescription = "返回本周",
                                    iconSize = 22.dp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            } else {
                                IconButton(
                                    onClick = onBackToCurrentWeek,
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Medium.Reset,
                                        contentDescription = "返回本周",
                                        modifier = Modifier.size(25.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (navBarStyle == "rail") {
                        AnimatedVisibility(
                            visible = !isViewingCurrentWeek,
                            enter = fadeIn(animationSpec = tween(180)),
                            exit = fadeOut(animationSpec = tween(120))
                        ) {
                            if (appStyle == "liquidglass" && liquidGlassBackdrop != null) {
                                LiquidTopBarButton(
                                    onClick = onBackToCurrentWeek,
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Medium.Reset,
                                    contentDescription = "返回本周",
                                    iconSize = 22.dp,
                                    buttonHeight = 38.dp,
                                    modifier = Modifier.padding(end = 6.dp, top = 4.dp)
                                )
                            } else {
                                IconButton(
                                    onClick = onBackToCurrentWeek,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Medium.Reset,
                                        contentDescription = "返回本周",
                                        modifier = Modifier.size(25.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (appStyle == "liquidglass" && liquidGlassBackdrop != null) {
                        LiquidTopBarCapsuleButton(
                            onLeftClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onOpenSwitchSchedule()
                            },
                            onRightClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                showMorePopup = true
                            },
                            backdrop = liquidGlassBackdrop,
                            buttonHeight = if (navBarStyle == "rail") 38.dp else 40.dp,
                            modifier = if (navBarStyle == "rail") Modifier.padding(top = 4.dp) else Modifier
                        )
                    } else {
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onOpenSwitchSchedule()
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Normal.ConvertFile,
                                contentDescription = "课表切换",
                                modifier = Modifier.size(27.dp)
                            )
                        }
                    }
                    Box(modifier = Modifier.padding(end = 4.dp)) {
                        if (appStyle != "liquidglass" || liquidGlassBackdrop == null) {
                            IconButton(onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                showMorePopup = true
                            }) {
                                Icon(
                                    imageVector = MiuixIcons.More,
                                    contentDescription = "更多",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        OverlayListPopup(
                            show = showMorePopup,
                            alignment = PopupPositionProvider.Align.End,
                            onDismissRequest = { showMorePopup = false }
                        ) {
                            ListPopupColumn {
                                DropdownImpl(
                                    text = "跳转周数",
                                    optionSize = 2,
                                    isSelected = false,
                                    index = 0,
                                    onSelectedIndexChange = {
                                        showMorePopup = false
                                        onJumpWeek()
                                    }
                                )
                                DropdownImpl(
                                    text = "课表外观",
                                    optionSize = 2,
                                    isSelected = false,
                                    index = 1,
                                    onSelectedIndexChange = {
                                        showMorePopup = false
                                        onOpenCustomize()
                                    }
                                )
                            }
                        }
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Spacer(modifier = Modifier.width(36.dp))
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
    }
}
