package com.haooz.chedule.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主界面底部导航栏 / 侧边导航栏。
 * 平板（navBarStyle == "rail"）使用 LiquidNavigationRail；手机使用 LiquidBottomTabs。
 * 排班模式下隐藏"今日"入口，将"课程表"替换为"排班课表"。
 * @param navBarStyle "rail" 或 "standard"
 * @param isShiftMode 是否处于排班模式
 * @param selectedTab 当前选中的 tab 索引
 * @param onTabSelected 点击 tab 回调
 * @param liquidGlassBackdrop 用于液态玻璃效果的 backdrop
 */
@Composable
internal fun ScheduleBottomBar(
    navBarStyle: String,
    isShiftMode: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    liquidGlassBackdrop: Backdrop? = null,
    addButton: @Composable () -> Unit = {}
) {
    val onSelect: (Int) -> Unit = { idx ->
        onTabSelected(idx)
    }

    if (navBarStyle == "rail" && liquidGlassBackdrop != null) {
        // Pad 端液态玻璃导航栏
        Box(modifier = Modifier.fillMaxSize()) {
            LiquidNavigationRail(
                selectedTab = selectedTab,
                onTabSelected = { onSelect(it) },
                backdrop = liquidGlassBackdrop,
                isShiftMode = isShiftMode
            )
        }
    } else if (liquidGlassBackdrop != null) {
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
            }
        } else {
            val density = LocalDensity.current
            var navBarWidthPx by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                val isOnMineTab = liquidSelectedTab == 2
                val buttonAlpha by animateFloatAsState(
                    targetValue = if (isOnMineTab) 0f else 1f,
                    animationSpec = if (isOnMineTab) {
                        tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    } else {
                        tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    },
                    label = "addButtonAlpha"
                )
                val buttonScale by animateFloatAsState(
                    targetValue = if (isOnMineTab) 0.5f else 1f,
                    animationSpec = if (isOnMineTab) {
                        tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    } else {
                        tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    },
                    label = "addButtonScale"
                )
                val buttonBlur by animateFloatAsState(
                    targetValue = if (isOnMineTab) 12f else 0f,
                    animationSpec = if (isOnMineTab) {
                        tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    } else {
                        tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    },
                    label = "addButtonBlur"
                )
                val gapPx = with(density) { 8.dp.toPx() }
                val buttonWidthPx = with(density) { 56.dp.toPx() }
                val navBarOffsetTarget = -(buttonWidthPx + gapPx) / 2f
                val navBarOffsetXPx by animateFloatAsState(
                    targetValue = if (isOnMineTab) 0f else navBarOffsetTarget,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "navBarOffsetX"
                )
                val navBarOffsetXDp = with(density) { navBarOffsetXPx.toDp() }
                val buttonOffsetXDp = with(density) { (navBarWidthPx / 2f + gapPx).toDp() }
                LiquidBottomTabs(
                    selectedTabIndex = { liquidSelectedTab },
                    onTabSelected = { onSelect(it) },
                    backdrop = liquidGlassBackdrop,
                    tabsCount = 3,
                    modifier = Modifier
                        .fillMaxWidth(0.63f)
                        .height(56.dp)
                        .offset(x = navBarOffsetXDp, y = 22.dp)
                        .zIndex(1f)
                        .onGloballyPositioned { navBarWidthPx = it.size.width.toFloat() }
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
                Box(
                    modifier = Modifier
                        .offset(x = buttonOffsetXDp, y = 22.dp)
                        .size(100.dp)
                        .blur(if (buttonBlur > 0f) buttonBlur.dp else 0.dp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            scaleX = buttonScale
                            scaleY = buttonScale
                            alpha = buttonAlpha
                            clip = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    addButton()
                }
            }
        }
    }
}
