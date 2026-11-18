package com.haooz.chedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Album
import com.haooz.nexioui.icon.extended.Backup
import com.haooz.nexioui.icon.extended.ConvertFile
import com.haooz.nexioui.icon.extended.Months
import com.haooz.nexioui.icon.extended.Settings
import com.haooz.nexioui.tabletnav.TabletNavItem
import com.haooz.nexioui.tabletnav.TabletNavigation
import com.haooz.softglass.backdrop.GlassBackdrop

/**
 * 平板导航：侧栏与顶栏胶囊由 nexioui 的 [TabletNavigation] 统一绘制。
 *
 * 应用只负责提供导航项、文案与「回到今天」插槽；展开、形变、吸附与点选都在库内完成。
 */
@Composable
fun LiquidNavigationRail(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: GlassBackdrop,
    isShiftMode: Boolean,
    /** 屏幕圆角（px）；宿主注入，0 = 取不到 */
    screenCornerRadiusPx: Float = 0f,
    modifier: Modifier = Modifier,
    onSidebarExpandedChange: ((Boolean) -> Unit)? = null,
    showBackToNow: Boolean = false,
    onBackToNow: () -> Unit = {},
) {
    val items = remember(isShiftMode) {
        if (isShiftMode) {
            listOf(
                TabletNavItem(
                    icon = NexioIcons.Months,
                    label = ChromeStr.shiftSchedule(),
                    inTopBar = true,
                ),
                TabletNavItem(
                    icon = NexioIcons.Demibold.Settings,
                    label = CommonStr.settings(),
                    inTopBar = true,
                ),
            )
        } else {
            listOf(
                TabletNavItem(
                    icon = NexioIcons.Album,
                    label = CommonStr.today(),
                    inTopBar = true,
                ),
                TabletNavItem(
                    icon = NexioIcons.Months,
                    label = ChromeStr.schedule(),
                    inTopBar = true,
                ),
                TabletNavItem(
                    icon = NexioIcons.Demibold.Settings,
                    label = CommonStr.settings(),
                    inTopBar = true,
                ),
                TabletNavItem(
                    icon = NexioIcons.Backup,
                    label = ChromeStr.courseManage(),
                    sectionTitle = ChromeStr.dataManagement(),
                ),
                TabletNavItem(
                    icon = NexioIcons.ConvertFile,
                    label = ChromeStr.switchSchedule(),
                ),
            )
        }
    }

    LaunchedEffect(TabletNavSideState.expanded) {
        onSidebarExpandedChange?.invoke(TabletNavSideState.expanded)
    }

    TabletNavigation(
        items = items,
        selectedIndex = selectedTab,
        onSelect = onTabSelected,
        backdrop = backdrop,
        state = TabletNavSideState,
        modifier = modifier,
        screenCornerRadiusPx = screenCornerRadiusPx,
        navigationLabel = ChromeStr.navigationLabel(),
        backToNow = if (showBackToNow) {
            {
                BackToNowFloatingButton(
                    onClick = onBackToNow,
                    backdrop = backdrop,
                    label = ChromeStr.todayBadge(),
                    adaptiveToSidebarExpand = true,
                )
            }
        } else {
            null
        },
    )
}
