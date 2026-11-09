package com.haooz.chedule.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 平板弹窗：卡片下边缘 → 内容的留白。**唯一来源**，别在调用点写数字。
 *
 * 注意卡片本身还有 [TabletSheetFloatGap] 的悬浮间距，量到屏幕最下沿时要把它算进去。
 */
val TabletSheetBottomInset: Dp = 16.dp

/** 平板弹窗卡片下边缘与屏幕底的悬浮间距（`BlurBottomSheetTablet` 统一设定） */
val TabletSheetFloatGap: Dp = 20.dp

/**
 * 弹窗内容底部留白：平板恒定 [TabletSheetBottomInset]，手机 = 状态栏 + [phoneExtra]。
 */
@Composable
fun Modifier.sheetContentBottomInset(
    isTablet: Boolean,
    phoneExtra: Dp = 60.dp,
): Modifier {
    val inset = if (isTablet) {
        TabletSheetBottomInset
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + phoneExtra
    }
    return padding(bottom = inset)
}
