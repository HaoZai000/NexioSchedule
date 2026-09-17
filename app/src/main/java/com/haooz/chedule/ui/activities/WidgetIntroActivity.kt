/** 小组件使用引导页面 */
package com.haooz.chedule.ui.activities

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.LiquidGlassTextButton
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

class WidgetIntroActivity : SecondaryActivity() {
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
        setSecondaryContent {
            val hapticFeedback = LocalHapticFeedback.current
            val backgroundColor = MiuixTheme.colorScheme.surface
            val backdrop = rememberLayerBackdrop {
                drawRect(backgroundColor)
                drawContent()
            }
            val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
            val scrollBehavior = rememberSharedScrollBehavior()
            var showGuideDialog by remember { mutableStateOf(false) }
            val isTablet = LocalConfiguration.current.screenWidthDp >= 600
            val tabletHorizontalPadding = if (isTablet) {
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
            } else 16.dp

            Scaffold(
                topBar = {
                    ProgressiveBlurTopBar(
                        backdrop = liquidGlassBackdrop,
                    ) {
                        CollapsibleTopAppBar(
                            title = "桌面小部件",
                            largeTitle = "桌面小部件",
                            modifier = Modifier,
                            scrollBehavior = scrollBehavior,
                            contentPadding = {},
                            startAction = { backdropAlpha, shadowAlpha ->
                                LiquidTopBarButton(
                                    onClick = { finishSecondary() },
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.ChevronBackward,
                                    contentDescription = "返回",
                                    performHapticFeedback = false,
                                    iconSize = 25.dp,
                                    iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                            },
                        )
                    }
                }
            ) { _ ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                ) {
                    // 采样层只包内容；玻璃按钮放在层外，避免循环采样
                    Box(
                        modifier = Modifier.fillMaxSize().then(
                            Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                        )
                    ) {
                        WidgetIntroScreen(
                            scrollBehavior = scrollBehavior,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                        )
                    }

                    LiquidGlassTextButton(
                        text = "添加到桌面",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            showGuideDialog = true
                        },
                        backdrop = liquidGlassBackdrop,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(
                                start = tabletHorizontalPadding + 16.dp,
                                end = tabletHorizontalPadding + 16.dp
                            )
                            .navigationBarsPadding()
                            .padding(bottom = 20.dp)
                    )

                    OverlayDialog(
                        title = "添加桌面小部件",
                        show = showGuideDialog,
                        liquidGlassBackdrop = liquidGlassBackdrop,
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
    }
}
