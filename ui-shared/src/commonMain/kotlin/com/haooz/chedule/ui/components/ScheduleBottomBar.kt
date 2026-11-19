package com.haooz.chedule.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.nexioui.bottombar.BottomBar
import com.haooz.nexioui.bottombar.BottomBarAction
import com.haooz.nexioui.bottombar.BottomBarItem
import com.haooz.nexioui.iconbutton.IconButton
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.NativeBottomBarHost
import com.haooz.chedule.ui.utils.NativeBottomBarState
import com.haooz.chedule.ui.utils.argbValue
import com.haooz.chedule.ui.utils.bottomFloatingPadding
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Album
import com.haooz.nexioui.icon.extended.Months
import com.haooz.nexioui.icon.extended.Settings
import com.haooz.nexioui.theme.NexioTheme

/** 圆钮直径，与胶囊同高 */
private val BOTTOM_BAR_CIRCLE = 56.dp

/** 胶囊与圆钮的静止间距 */
private val BOTTOM_BAR_GAP = 10.dp

/** 每格占屏比，沿用「3 格占 0.63」的密度 */
private const val TAB_WIDTH_FRACTION = 0.21f

/** 底栏整体占位高度（含浮动留白）：宿主接管时 Compose 侧要留同高的空位。 */
@Composable
fun scheduleBottomBarHeight(): Dp = BOTTOM_BAR_CIRCLE + bottomFloatingPadding()

/**
 * 手机端底部导航。平板导航由 MainActivity 叠层 [LiquidNavigationRail] 绘制，此处直接返回，
 * 避免 Scaffold bottomBar 的 fillMaxSize 挡住内容滚动。
 *
 * tab 胶囊与右侧圆按钮由 [BottomBar] 绘制，两块独立玻璃，中间只有间距、不再融合。
 * 宿主接管底部导航（鸿蒙 ArkUI）时这里根本不会被组合，语义发布见 [PublishNativeBottomBar]。
 */
@Composable
fun ScheduleBottomBar(
    navBarStyle: String,
    isShiftMode: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onOpenSettings: () -> Unit = {},
    liquidGlassBackdrop: GlassBackdrop? = null,
) {
    if (navBarStyle == "rail") return

    // 每格占屏 0.21，沿用「3 格占 0.63」的密度
    val tabCount = if (isShiftMode) 1 else 2
    val bottomPadding = bottomFloatingPadding()

    if (liquidGlassBackdrop == null) return

    val iconTint = NexioTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.8f)

    val tabsWidthFraction = TAB_WIDTH_FRACTION * tabCount

    val openSettings = remember(onOpenSettings) { onOpenSettings }
    // 液态玻璃档整条换成参考库的底栏（原库没有右侧圆钮，设置按钮单独用一颗玻璃圆按钮）
    val liquidTier = AppMaterialSettings.isLiquidTier()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding)
            .height(BOTTOM_BAR_CIRCLE),
    ) {
        val pillWidth = maxWidth * tabsWidthFraction
        // 居中按「胶囊 + 间距 + 圆钮」算；两块独立玻璃，不需要额外拖动余量
        val visibleWidth = pillWidth + BOTTOM_BAR_GAP + BOTTOM_BAR_CIRCLE
        val nodeWidth = visibleWidth
        val nodeLeft = (maxWidth - visibleWidth) / 2

        if (liquidTier) {
            LiquidGlassBottomBar(
                selectedTabIndex = { selectedTab },
                onTabSelected = onTabSelected,
                backdrop = liquidGlassBackdrop,
                tabsCount = tabCount,
                barHeight = BOTTOM_BAR_CIRCLE,
                sliderHeight = BOTTOM_BAR_CIRCLE - 8.dp,
                modifier = Modifier
                    .offset(x = nodeLeft)
                    .width(pillWidth)
                    .height(BOTTOM_BAR_CIRCLE)
                    // 左侧胶囊压在设置圆钮之上：两块玻璃靠近时，胶囊的边缘与阴影不被圆钮盖住
                    .zIndex(1f),
            ) {
                if (isShiftMode) {
                    LiquidGlassBottomTab(onClick = { onTabSelected(0) }) {
                        Image(
                            modifier = Modifier.size(24.dp),
                            imageVector = NexioIcons.Months,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(iconTint)
                        )
                        Text(ChromeStr.shiftSchedule(), fontSize = 11.sp, color = iconTint)
                    }
                } else {
                    LiquidGlassBottomTab(onClick = { onTabSelected(0) }) {
                        Image(
                            modifier = Modifier.size(24.dp),
                            imageVector = NexioIcons.Album,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(iconTint)
                        )
                        Text(CommonStr.today(), fontSize = 11.sp, color = iconTint)
                    }
                    LiquidGlassBottomTab(onClick = { onTabSelected(1) }) {
                        Image(
                            modifier = Modifier.size(24.dp),
                            imageVector = NexioIcons.Months,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(iconTint)
                        )
                        Text(ChromeStr.schedule(), fontSize = 11.sp, color = iconTint)
                    }
                }
            }
            IconButton(
                onClick = openSettings,
                backdrop = liquidGlassBackdrop,
                icon = NexioIcons.Settings,
                contentDescription = CommonStr.settings(),
                modifier = Modifier
                    .offset(x = nodeLeft + pillWidth + BOTTOM_BAR_GAP)
                    .size(BOTTOM_BAR_CIRCLE),
                buttonHeight = BOTTOM_BAR_CIRCLE,
                // 与柔光档 BottomBar 的设置图标对齐：那边是 iconSize + 4.dp = 28.dp
                iconSize = 28.dp,
                // 设置按钮的最小，高光按默认半径略放大一点，避免看着比旁边两个弱
                highlightRadiusScale = 1.25f,
                iconTint = iconTint,
            )
        } else {
            val items = if (isShiftMode) {
                listOf(
                    BottomBarItem(
                        icon = NexioIcons.Months,
                        label = ChromeStr.shiftSchedule(),
                    ),
                )
            } else {
                listOf(
                    BottomBarItem(
                        icon = NexioIcons.Album,
                        label = CommonStr.today(),
                    ),
                    BottomBarItem(
                        icon = NexioIcons.Months,
                        label = ChromeStr.schedule(),
                    ),
                )
            }
            BottomBar(
                items = items,
                selectedIndex = selectedTab,
                onSelect = onTabSelected,
                backdrop = liquidGlassBackdrop,
                modifier = Modifier.fillMaxWidth(),
                height = BOTTOM_BAR_CIRCLE,
                action = BottomBarAction(
                    icon = NexioIcons.Settings,
                    contentDescription = CommonStr.settings(),
                    onClick = openSettings,
                ),
            )
        }
    }
}

/**
 * 宿主画底栏时**唯一**的语义发布点（鸿蒙 ArkUI）。
 *
 * ⚠ 必须在 Scaffold 之外无条件调用：宿主接管时 [ScheduleBottomBar] 不会被组合，
 * 写在它里面等于一次都不发，宿主拿到 null 就整条不画。
 *
 * Compose 这边一个像素都不画；[onTabSelected] 收到 `index >= tabsCount` 即为末尾的设置页签。
 */
@Composable
internal fun PublishNativeBottomBar(
    host: NativeBottomBarHost,
    isShiftMode: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onOpenSettings: (() -> Unit)?,
) {
    val tabsCount = if (isShiftMode) 1 else 2
    // 二级页在宿主那边是独立的一层，压在底栏上面，底栏不需要为它让位；
    // 只有 Compose 内部的全屏层（外观页 / 切换课表页 / 详情页）盖不住它，才整条收起。
    if (host.suppressed) {
        SideEffect {
            host.state = null
            host.onTabSelected = null
        }
        return
    }
    val state = NativeBottomBarState(
        selectedTab = selectedTab.coerceIn(0, (tabsCount - 1).coerceAtLeast(0)),
        tabsCount = tabsCount,
        labels = if (isShiftMode) {
            listOf(ChromeStr.shiftSchedule())
        } else {
            listOf(CommonStr.today(), ChromeStr.schedule())
        },
        onSurfaceArgb = NexioTheme.colorScheme.onSurface.argbValue(),
        accentArgb = NexioTheme.colorScheme.primary.argbValue(),
        heightDp = BOTTOM_BAR_CIRCLE.value,
    )
    SideEffect {
        host.state = state
        host.onTabSelected = { index ->
            if (index >= tabsCount) onOpenSettings?.invoke() else onTabSelected(index)
        }
    }
    DisposableEffect(host) {
        onDispose {
            host.state = null
            host.onTabSelected = null
        }
    }
}
