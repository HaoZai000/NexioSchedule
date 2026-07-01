package com.haooz.chedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.Color as ComposeColor

class WidgetIntroActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyThemeAwareSystemBars()
        setContent {
            CourseScheduleTheme {
                WidgetIntroScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun WidgetIntroScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var showGuideDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "桌面小部件",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onBack()
                        },
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            WidgetPreview()

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                FeatureItem("今日课程一目了然", "桌面直接显示当天课程、时间、地点和教师信息")
                FeatureItem("进行中课程高亮", "正在上课的课程高亮显示剩余时间")
                FeatureItem("智能显示状态", "智能提醒今日课程当前状态")
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                text = "添加到桌面",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    showGuideDialog = true
                },
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, end= 16.dp, start = 16.dp, bottom = 32.dp)
            )
        }

        OverlayDialog(
            title = "添加桌面小部件",
            show = showGuideDialog,
            outsideMargin = DpSize(17.dp, 12.dp),
            onDismissRequest = { showGuideDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "1. 长按桌面空白处\n2. 选择「全部应用」内的「安卓小部件」\n3. 找到「Hyper课程表」并添加",
                    fontSize = 14.sp,
                    lineHeight = 24.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Spacer(modifier = Modifier.height(20.dp))
                TextButton(
                    text = "我知道了",
                    onClick = { hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showGuideDialog = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        }
    }
}

@Composable
private fun WidgetPreview() {
    val isDark = isAppDarkTheme()
    val bgColor = if (isDark) ComposeColor(0xFF1A1A1A) else ComposeColor(0xFFF7F7F7)
    val cardBg = if (isDark) ComposeColor(0xFF262626) else ComposeColor.White
    val borderColor = if (isDark) ComposeColor(0xFF3A3A3C) else ComposeColor(0xFFE0E0E0)
    val titleColor = if (isDark) ComposeColor(0x77FFFFFF) else ComposeColor(0x77000000)
    val nameColor = if (isDark) ComposeColor(0xFFEEEEEE) else ComposeColor(0xFF1A1A1A)
    val infoColor = if (isDark) ComposeColor(0x55FFFFFF) else ComposeColor(0x55000000)
    val timeStartColor = if (isDark) ComposeColor(0x88FFFFFF) else ComposeColor(0x88000000)
    val timeEndColor = if (isDark) ComposeColor(0x55FFFFFF) else ComposeColor(0x55000000)
    val nowColor = if (isDark) ComposeColor(0xFF42A5F5) else ComposeColor(0xFF2196F3)

    Card(
        cornerRadius = 22.dp,
        modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, com.kyant.shapes.RoundedRectangle(22.dp)),
        insideMargin = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_miuix_logo),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp).padding(end = 6.dp).graphicsLayer(scaleY = -1f),
                    alpha = 0.8f
                )
                Text("今天 / 周五", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = titleColor, fontFamily = FontFamily.SansSerif)
                Spacer(modifier = Modifier.weight(1f))
                Text("第10周", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = titleColor, fontFamily = FontFamily.SansSerif)
            }

            CourseCardPreview("08:00", "09:35", "高等数学", "第1-2节｜A301｜张老师", ComposeColor(0xFF4CAF50), null, cardBg, nameColor, infoColor, timeStartColor, timeEndColor, nowColor)
            Spacer(modifier = Modifier.height(8.dp))
            CourseCardPreview("14:00", "15:35", "大学英语", "第5-6节｜B205｜李老师", ComposeColor(0xFF2196F3), "25分钟结束", ComposeColor(0x1A2196F3), nameColor, infoColor, timeStartColor, timeEndColor, nowColor)
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun CourseCardPreview(
    startTime: String, endTime: String, name: String, info: String,
    barColor: ComposeColor, remaining: String?,
    cardBg: ComposeColor, nameColor: ComposeColor, infoColor: ComposeColor,
    timeStartColor: ComposeColor, timeEndColor: ComposeColor, nowColor: ComposeColor
) {
    Card(
        cornerRadius = 12.dp,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        insideMargin = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBg)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(35.dp), horizontalAlignment = Alignment.Start) {
                Text(startTime, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = timeStartColor, fontFamily = FontFamily.SansSerif)
                Text(endTime, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = timeEndColor, fontFamily = FontFamily.SansSerif)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.width(4.dp).height(32.dp).background(barColor, com.kyant.shapes.RoundedRectangle(2.dp)))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = nameColor, maxLines = 1)
                Text(info, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = infoColor, fontFamily = FontFamily.SansSerif)
            }
            if (remaining != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(remaining, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = nowColor)
            }
        }
    }
}

@Composable
private fun FeatureItem(title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(8.dp).offset(y = 7.dp).background(MiuixTheme.colorScheme.primary, com.kyant.shapes.RoundedRectangle(4.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions)
        }
    }
}
