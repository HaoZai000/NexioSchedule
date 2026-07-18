/** 小组件使用引导页面 - Screen */
package com.haooz.chedule.ui.activities

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.R
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.ui.graphics.Color as ComposeColor

@Composable
fun WidgetIntroScreen(onBack: () -> Unit, liquidGlassBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var showGuideDialog by remember { mutableStateOf(false) }
    val appStyle = rememberAppStyle()
    val isLiquidGlass = appStyle == "liquidglass"
    val scrollBehavior = MiuixScrollBehavior()
    val backdropColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backdropColor)
        drawContent()
    }

    Scaffold(
        topBar = {
            if (!isLiquidGlass) {
                TopAppBar(
                    title = "桌面小部件",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(
                            onClick = { onBack() },
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isLiquidGlass) Modifier
                        else Modifier.padding(top = paddingValues.calculateTopPadding())
                    )
                    .padding(horizontal = 16.dp)
                    .overScrollVertical()
                    .then(
                        if (!isLiquidGlass) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
                    )
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val topPadding = if (isLiquidGlass) {
                    paddingValues.calculateTopPadding() + 64.dp
                } else 8.dp
                Spacer(modifier = Modifier.height(topPadding))

                val pagerState = rememberPagerState(pageCount = { 2 })

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> WidgetPreviewToday()
                        1 -> WidgetPreviewSmall2x2()
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 页面指示器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(2) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected)
                                        MiuixTheme.colorScheme.primary
                                    else
                                        MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.12f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    FeatureItem("今日课程一目了然", "桌面显示当天课程、时间、地点和教师信息")
                    FeatureItem("明日课程提前预览", "开启次日课程提醒后，智能显示明日课程")
                    FeatureItem("进行中课程高亮", "正在上课的课程高亮显示剩余时间")
                    FeatureItem("智能显示状态", "智能提醒今日课程当前状态")
                }

                Spacer(modifier = Modifier.height(120.dp))
            }

            // 底部渐变遮罩
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to ComposeColor.Transparent,
                                0.15f to MiuixTheme.colorScheme.surface.copy(alpha = 0.5f),
                                0.5f to MiuixTheme.colorScheme.surface.copy(alpha = 0.85f),
                                1.0f to MiuixTheme.colorScheme.surface
                            )
                        )
                    )
            )

            // 添加到桌面按钮 - 固定在底部
            TextButton(
                text = "添加到桌面",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    showGuideDialog = true
                },
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, bottom = 48.dp)
            )

            OverlayDialog(
                title = "添加桌面小部件",
                show = showGuideDialog,

                onDismissRequest = { showGuideDialog = false }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "1. 长按桌面空白处\n2. 选择「全部应用」内的「安卓小部件」\n3. 找到「Nexio课程表」并添加",
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    TextButton(
                        text = "我知道了",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showGuideDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewToday() {
    val isDark = isAppDarkTheme()
    val bgColor = if (isDark) ComposeColor(0xFF1A1A1A) else ComposeColor(0xFFF7F7F7)
    val cardBg = if (isDark) ComposeColor(0xFF262626) else ComposeColor.White
    val borderColor = if (isDark) ComposeColor(0xFF2A2A2A) else ComposeColor(0x0D000000)
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
private fun WidgetPreviewSmall2x2() {
    val isDark = isAppDarkTheme()
    val bgColor = if (isDark) ComposeColor(0xFF1A1A1A) else ComposeColor.White
    val borderColor = if (isDark) ComposeColor(0xFF2A2A2A) else ComposeColor(0x0D000000)
    val titleColor = if (isDark) ComposeColor(0x99FFFFFF) else ComposeColor(0x99000000)
    val nameColor = if (isDark) ComposeColor(0xFFEEEEEE) else ComposeColor(0xFF1A1A1A)
    val infoColor = if (isDark) ComposeColor(0x88FFFFFF) else ComposeColor(0x88000000)
    val remainingColor = if (isDark) ComposeColor(0x55FFFFFF) else ComposeColor(0x55000000)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(166.dp)
                .height(166.dp)
                .border(1.dp, borderColor, com.kyant.shapes.RoundedRectangle(22.dp))
                .background(bgColor, com.kyant.shapes.RoundedRectangle(22.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, bottom = 4.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_miuix_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer(scaleY = -1f),
                        alpha = 0.8f
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Title: 14sp bold
                    Text("周五", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif)
                    Spacer(modifier = Modifier.weight(1f))
                    // Week: 14sp bold
                    Text("第10周", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif)
                }

                // Course content: gravity=bottom, weight=1
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Course name: 17sp, paddingStart=4dp, paddingTop=8dp, paddingBottom=4dp, marginBottom=4dp
                    Text(
                        "高等数学",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = nameColor,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                    )

                    // Time row: marginStart=4dp, marginBottom=2dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon: 12dp, marginEnd=6dp
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_widget_clock),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            alpha = 0.5f
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Text: 14sp
                        Text("08:00 - 09:35", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = infoColor, fontFamily = FontFamily.SansSerif)
                    }

                    // Location row: marginStart=4dp, marginBottom=16dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon: 12dp, marginEnd=6dp
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_widget_location),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            alpha = 0.5f
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Text: 14sp
                        Text("A301", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = infoColor, fontFamily = FontFamily.SansSerif, maxLines = 1)
                    }
                }

                // Remaining info: marginStart=4dp, text 12sp
                Row(
                    modifier = Modifier.padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "还剩 2 节课",
                        fontSize = 12.sp,
                        color = remainingColor,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(ComposeColor(0xFF4CAF50), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(ComposeColor(0xFF2196F3), CircleShape)
                    )
                }
            }
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
        Box(modifier = Modifier.size(8.dp).padding(top = 7.dp).background(MiuixTheme.colorScheme.primary, com.kyant.shapes.RoundedRectangle(4.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions)
        }
    }
}
