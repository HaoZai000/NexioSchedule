package com.haooz.chedule.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 手机端底部导航。
 * 平板导航由 MainActivity 叠层 [LiquidNavigationRail] 绘制，此处直接 return，
 * 避免 Scaffold bottomBar 用 fillMaxSize 挡住内容滚动。
 */
@Composable
internal fun ScheduleBottomBar(
    navBarStyle: String,
    isShiftMode: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    liquidGlassBackdrop: Backdrop? = null,
    onTabletNavExpandedChange: ((Boolean) -> Unit)? = null,
) {
    if (navBarStyle == "rail" || liquidGlassBackdrop == null) return

    val onSelect: (Int) -> Unit = { idx -> onTabSelected(idx) }
    val iconTint = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.8f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isShiftMode) {
            LiquidBottomTabs(
                selectedTabIndex = { selectedTab },
                onTabSelected = { onSelect(it) },
                backdrop = liquidGlassBackdrop,
                tabsCount = 2,
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(56.dp)
            ) {
                LiquidBottomTab(index = 0, onClick = { onSelect(0) }) {
                    Image(
                        modifier = Modifier.size(24.dp),
                        imageVector = MiuixIcons.Months,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconTint)
                    )
                    Text("排班课表", fontSize = 11.sp, color = iconTint)
                }
                LiquidBottomTab(index = 1, onClick = { onSelect(1) }) {
                    Image(
                        modifier = Modifier.size(24.dp),
                        imageVector = MiuixIcons.Demibold.Settings,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconTint)
                    )
                    Text("设置", fontSize = 11.sp, color = iconTint)
                }
            }
        } else {
            LiquidBottomTabs(
                selectedTabIndex = { selectedTab },
                onTabSelected = { onSelect(it) },
                backdrop = liquidGlassBackdrop,
                tabsCount = 3,
                modifier = Modifier
                    .fillMaxWidth(0.63f)
                    .height(56.dp)
            ) {
                LiquidBottomTab(index = 0, onClick = { onSelect(0) }) {
                    Image(
                        modifier = Modifier.size(24.dp),
                        imageVector = MiuixIcons.Album,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconTint)
                    )
                    Text("今日", fontSize = 11.sp, color = iconTint)
                }
                LiquidBottomTab(index = 1, onClick = { onSelect(1) }) {
                    Image(
                        modifier = Modifier.size(24.dp),
                        imageVector = MiuixIcons.Months,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconTint)
                    )
                    Text("课程表", fontSize = 11.sp, color = iconTint)
                }
                LiquidBottomTab(index = 2, onClick = { onSelect(2) }) {
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
    }
}
