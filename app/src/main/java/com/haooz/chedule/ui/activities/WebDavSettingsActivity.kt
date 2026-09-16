/** WebDAV 备份/恢复设置页面 */
package com.haooz.chedule.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.LiquidGlassTextButton
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

class WebDavSettingsActivity : ComponentActivity() {
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
                val backgroundColor = MiuixTheme.colorScheme.surface
                val backdrop = rememberLayerBackdrop {
                    drawRect(backgroundColor)
                    drawContent()
                }
                val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                val scrollBehavior = rememberSharedScrollBehavior()
                val isTablet = LocalConfiguration.current.screenWidthDp >= 600
                val tabletHorizontalPadding = if (isTablet) {
                    val screenWidthDp = LocalConfiguration.current.screenWidthDp
                    ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
                } else 16.dp

                var connected by remember { mutableStateOf(false) }
                var onTestConnection by remember { mutableStateOf({}) }
                var backingUp by remember { mutableStateOf(false) }
                var restoring by remember { mutableStateOf(false) }
                var onBackup by remember { mutableStateOf({}) }
                var onRestore by remember { mutableStateOf({}) }

                Scaffold(
                    topBar = {
                        ProgressiveBlurTopBar(
                            backdrop = liquidGlassBackdrop,
                        ) {
                            CollapsibleTopAppBar(
                                title = "WebDAV 云备份",
                                largeTitle = "WebDAV 云备份",
                                modifier = Modifier,
                                scrollBehavior = scrollBehavior,
                                contentPadding = {},
                                startAction = { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = { finish() },
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
                                endAction = { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = { onTestConnection() },
                                        backdrop = liquidGlassBackdrop,
                                        icon = if (connected) MiuixIcons.Ok else MiuixIcons.Play,
                                        contentDescription = if (connected) "已连接" else "测试连接",
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                        iconOffset = if (!connected) DpOffset(x = 2.dp, y = 0.dp) else DpOffset.Zero,
                                        iconTint = if (connected) Color(0xFF4CAF50) else Color.Unspecified,
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
                            WebDavSettingsScreen(
                                scrollBehavior = scrollBehavior,
                                onConnectedChange = { connected = it },
                                onTestConnectionReady = { onTestConnection = it },
                                onBackupRestoreReady = { backup, restore ->
                                    onBackup = backup
                                    onRestore = restore
                                },
                                onBusyStateChange = { b, r ->
                                    backingUp = b
                                    restoring = r
                                }
                            )
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(
                                    start = tabletHorizontalPadding + 8.dp,
                                    end = tabletHorizontalPadding + 8.dp
                                )
                                .navigationBarsPadding()
                                .padding(bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LiquidGlassTextButton(
                                text = if (backingUp) "备份中..." else "备份到云端",
                                onClick = { onBackup() },
                                backdrop = liquidGlassBackdrop,
                                modifier = Modifier.weight(1f)
                            )
                            LiquidGlassTextButton(
                                text = if (restoring) "恢复中..." else "从云端恢复",
                                onClick = { onRestore() },
                                backdrop = liquidGlassBackdrop,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
