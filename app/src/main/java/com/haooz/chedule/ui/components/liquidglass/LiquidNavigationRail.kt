package com.haooz.chedule.ui.components.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                .padding(top = 48.dp)
                .width(280.dp)
                .height(56.dp)
        ) {
            if (!isShiftMode) {
                LiquidBottomTab({ onTabSelected(0) }) {
                    Text("今日", fontSize = 13.sp)
                }
                LiquidBottomTab({ onTabSelected(1) }) {
                    Text("课程表", fontSize = 13.sp)
                }
                LiquidBottomTab({ onTabSelected(2) }) {
                    Text("我的", fontSize = 13.sp)
                }
            } else {
                LiquidBottomTab({ onTabSelected(0) }) {
                    Text("排班课表", fontSize = 13.sp)
                }
                LiquidBottomTab({ onTabSelected(1) }) {
                    Text("设置", fontSize = 13.sp)
                }
            }
        }
    }
}
