package com.haooz.nexio.ohos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 鸿蒙端入口。
 *
 * 这里**只有入口，没有界面**。鸿蒙端的界面必须与 Android / iOS / 桌面共用同一份 Compose 代码，
 * 也就是 `:ui-shared` 的页面 —— 在鸿蒙侧再写一份网格，就等于两套 UI 两份真相，
 * 改一版要改两处，且永远对不齐。
 *
 * 现在这一页显示「未接入」是因为 `:ui-shared` 还编不到 ohos：
 * 它依赖的 miuix（icons / blur / squircle）与本地 fork 的 backdrop 都没有 ohos 变体。
 * 等 `:ui-core` 从 miuix 上剥出来（见 docs/HARMONY_PORT_PLAN.md 第五节），
 * 就把 `NexioRoot()` 的实现换成 `NexioApp()`，本文件除了 import 不需要再动。
 */

@Composable
fun NexioRoot() {
    Box(
        Modifier.fillMaxSize().background(Color(0xFFF2F2F7)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Nexio 课程表",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1C1C1E)
            )
            Text(
                "共享 UI 待接入（:ui-shared → ohos）",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 12.sp,
                color = Color(0xFF8E8E93),
                textAlign = TextAlign.Center
            )
        }
    }
}
