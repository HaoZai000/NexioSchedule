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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.components.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.rememberAppStyle
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.Color as ComposeColor
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
                val appStyle = rememberAppStyle()
                val liquidGlassBackdrop = if (appStyle == "liquidglass") {
                    com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                } else null
                val isLiquidGlass = liquidGlassBackdrop != null

                var connected by remember { mutableStateOf(false) }
                var onTestConnection by remember { mutableStateOf<() -> Unit>({}) }

                Scaffold(
                    topBar = {
                        if (isLiquidGlass) {
                            val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                            ProgressiveBlurTopBar(
                                backdrop = liquidGlassBackdrop!!,
                            ) {
                                SmallTopAppBar(
                                    color = Color.Transparent,
                                    title = "WebDAV 云备份",
                                    modifier = Modifier.zIndex(1f),
                                    navigationIcon = {}
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(2f)
                                        .offset(y = if (statusBarPadding > 0.dp) statusBarPadding + 5.dp else 42.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    LiquidTopBarButton(
                                        onClick = { finish() },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Medium.ChevronBackward,
                                        contentDescription = "返回",
                                        modifier = Modifier.offset(x = 20.dp),
                                        iconSize = 22.dp,
                                        iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                        useBackdropShadow = true
                                    )
                                    LiquidTopBarButton(
                                        onClick = { onTestConnection() },
                                        backdrop = liquidGlassBackdrop,
                                        icon = if (connected) MiuixIcons.Medium.Ok else MiuixIcons.Play,
                                        contentDescription = if (connected) "已连接" else "测试连接",
                                        modifier = Modifier.offset(x = (-20).dp),
                                        iconSize = 22.dp,
                                        iconOffset = if (!connected) DpOffset(x = 1.5.dp, y = 0.dp) else DpOffset.Zero,
                                        iconTint = if (connected) ComposeColor(0xFF4CAF50) else Color.Unspecified,
                                        useBackdropShadow = true
                                    )
                                }
                            }
                        }
                    }
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().then(
                                if (liquidGlassBackdrop != null) Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                                else Modifier
                            )
                        ) {
                            WebDavSettingsScreen(
                                onBack = { finish() },
                                onConnectedChange = { connected = it },
                                onTestConnectionReady = { onTestConnection = it }
                            )
                        }
                    }
                }
            }
        }
    }
}
