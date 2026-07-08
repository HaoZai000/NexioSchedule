package com.haooz.chedule.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.components.liquidglass.LiquidBottomTab
import com.haooz.chedule.ui.components.liquidglass.LiquidBottomTabs
import com.haooz.chedule.ui.components.liquidglass.LiquidNavigationRail
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailDefaults
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailState
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * 主界面底部导航栏 / 侧边导航栏。
 * 平板（navBarStyle == "rail"）使用 NavigationRail；手机使用 NavigationBar。
 * 排班模式下隐藏"今日"入口，将"课程表"替换为"排班课表"。
 * @param navBarStyle "rail" 或 "standard"
 * @param isShiftMode 是否处于排班模式
 * @param selectedTab 当前选中的 tab 索引
 * @param onTabSelected 点击 tab 回调
 * @param railState NavigationRail 的展开/收起状态（仅 navBarStyle == "rail" 时使用）
 * @param backdrop 用于 NavigationBar 的模糊 backdrop（仅 standard 模式使用）
 * @param blurColors NavigationBar 的模糊颜色配置（仅 standard 模式使用）
 * @param isDark 当前是否为深色主题（用于 NavigationRail 背景色）
 * @param liquidGlassBackdrop 用于液态玻璃效果的 backdrop（仅 liquidglass 风格使用）
 */
@Composable
internal fun ScheduleBottomBar(
    navBarStyle: String,
    isShiftMode: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    railState: NavigationRailState? = null,
    backdrop: LayerBackdrop,
    blurColors: BlurColors,
    isDark: Boolean,
    liquidGlassBackdrop: Backdrop? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    val onSelect: (Int) -> Unit = { idx ->
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        onTabSelected(idx)
    }

    val context = LocalContext.current
    val themePrefs = remember { context.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE) }
    val appStyle = com.haooz.chedule.ui.utils.rememberAppStyle()

    if (navBarStyle == "rail" && appStyle == "liquidglass" && liquidGlassBackdrop != null) {
        // Pad 端液态玻璃导航栏
        Box(modifier = Modifier.fillMaxSize()) {
            LiquidNavigationRail(
                selectedTab = selectedTab,
                onTabSelected = { onSelect(it) },
                backdrop = liquidGlassBackdrop,
                isShiftMode = isShiftMode
            )
        }
    } else if (navBarStyle == "rail") {
        Box(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                state = railState,
                expandedWidth = NavigationRailDefaults.ExpandedWidth,
                color = if (isDark) Color.Black else Color.White,
                defaultWindowInsetsPadding = false
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (!isShiftMode) {
                    NavigationRailItem(
                        selected = selectedTab == 0,
                        onClick = { onSelect(0) },
                        icon = MiuixIcons.Album,
                        label = "今日"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = selectedTab == 1,
                        onClick = { onSelect(1) },
                        icon = MiuixIcons.Months,
                        label = "课程表"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = selectedTab == 2,
                        onClick = { onSelect(2) },
                        icon = MiuixIcons.ContactsCircle,
                        label = "我的"
                    )
                } else {
                    NavigationRailItem(
                        selected = selectedTab == 0,
                        onClick = { onSelect(0) },
                        icon = MiuixIcons.Months,
                        label = "排班课表"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = selectedTab == 1,
                        onClick = { onSelect(1) },
                        icon = MiuixIcons.Demibold.Settings,
                        label = "设置"
                    )
                }
            }
        }
    } else if (appStyle == "liquidglass" && liquidGlassBackdrop != null) {
        val iconTint = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.8f)
        var liquidSelectedTab by remember { mutableIntStateOf(selectedTab) }
        LaunchedEffect(selectedTab) { liquidSelectedTab = selectedTab }
        if (isShiftMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                LiquidBottomTabs(
                    selectedTabIndex = { liquidSelectedTab },
                    onTabSelected = { onSelect(it) },
                    backdrop = liquidGlassBackdrop,
                    tabsCount = 2,
                    modifier = Modifier
                        .fillMaxWidth(0.46f)
                        .height(56.dp)
                ) {
                    LiquidBottomTab({ onSelect(0) }) {
                        Image(
                            modifier = Modifier.size(24.dp),
                            imageVector = MiuixIcons.Months,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(iconTint)
                        )
                        Text("排班课表", fontSize = 11.sp, color = iconTint)
                    }
                    LiquidBottomTab({ onSelect(1) }) {
                        Image(
                            modifier = Modifier.size(24.dp),
                            imageVector = MiuixIcons.Demibold.Settings,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(iconTint)
                        )
                        Text("设置", fontSize = 11.sp, color = iconTint)
                    }
                }
            }
        } else {
            LiquidBottomTabs(
                selectedTabIndex = { liquidSelectedTab },
                onTabSelected = { onSelect(it) },
                backdrop = liquidGlassBackdrop,
                tabsCount = 3,
                modifier = Modifier
                    .padding(start = 20.dp, end = 0.dp, bottom = 28.dp)
                    .fillMaxWidth(0.65f)
                    .height(56.dp)
            ) {
                LiquidBottomTab({ onSelect(0) }) {
                    Image(
                        modifier = Modifier.size(24.dp),
                        imageVector = MiuixIcons.Album,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconTint)
                    )
                    Text("今日", fontSize = 11.sp, color = iconTint)
                }
                LiquidBottomTab({ onSelect(1) }) {
                    Image(
                        modifier = Modifier.size(24.dp),
                        imageVector = MiuixIcons.Months,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconTint)
                    )
                    Text("课程表", fontSize = 11.sp, color = iconTint)
                }
                LiquidBottomTab({ onSelect(2) }) {
                    Image(
                        modifier = Modifier.size(24.dp),
                        imageVector = MiuixIcons.ContactsCircle,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconTint)
                    )
                    Text("我的", fontSize = 11.sp, color = iconTint)
                }
            }
        }
    } else {
        NavigationBar(
            modifier = Modifier.height(74.dp).textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                colors = blurColors
            ),
            mode = NavigationBarDisplayMode.IconAndText,
            color = ComposeColor.Transparent
        ) {
            if (!isShiftMode) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { onSelect(0) },
                    icon = MiuixIcons.Album,
                    label = "今日"
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { onSelect(1) },
                    icon = MiuixIcons.Months,
                    label = "课程表"
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { onSelect(2) },
                    icon = MiuixIcons.ContactsCircle,
                    label = "我的"
                )
            } else {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { onSelect(0) },
                    icon = MiuixIcons.Months,
                    label = "排班课表"
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { onSelect(1) },
                    icon = MiuixIcons.Demibold.Settings,
                    label = "设置"
                )
            }
        }
    }
}
