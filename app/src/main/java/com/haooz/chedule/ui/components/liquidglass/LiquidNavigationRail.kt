package com.haooz.chedule.ui.components.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop

@Composable
fun LiquidNavigationRail(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    isShiftMode: Boolean,
    modifier: Modifier = Modifier
) {
    var liquidSelectedTab by remember { mutableIntStateOf(selectedTab) }
    LaunchedEffect(selectedTab) { liquidSelectedTab = selectedTab }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = if (statusBarPadding > 0.dp) statusBarPadding else 36.dp
    val isDark = isAppDarkTheme()
    val textColor = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LiquidBottomTabs(
            selectedTabIndex = { liquidSelectedTab },
            onTabSelected = { onTabSelected(it) },
            backdrop = backdrop,
            tabsCount = if (!isShiftMode) 3 else 2,
            modifier = Modifier
                .padding(top = topPadding + 4.dp)
                .width(if (isShiftMode) 160.dp else 240.dp)
                .height(40.dp),
            containerHeight = 400.dp,
            highlightHeight = 34.dp,
            selectorHeight = 34.dp
        ) {
            if (!isShiftMode) {
                LiquidBottomTab({ onTabSelected(0) }) {
                    Text("今日", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                }
                LiquidBottomTab({ onTabSelected(1) }) {
                    Text("课程表", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                }
                LiquidBottomTab({ onTabSelected(2) }) {
                    Text("我的", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                }
            } else {
                LiquidBottomTab({ onTabSelected(0) }) {
                    Text("排班课表", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                }
                LiquidBottomTab({ onTabSelected(1) }) {
                    Text("设置", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                }
            }
        }
    }
}
