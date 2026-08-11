/** 赞赏作者页面 */
package com.haooz.chedule.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

class AppreciateAuthorActivity : ComponentActivity() {
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

                Scaffold(
                    topBar = {
                        var topBarBlurAlpha by remember { mutableStateOf(0f) }
                        ProgressiveBlurTopBar(
                            backdrop = liquidGlassBackdrop,
                            blurAlpha = topBarBlurAlpha,
                        ) {
                            CollapsibleTopAppBar(
                                title = "捐赠支持",
                                largeTitle = "捐赠支持",
                                modifier = Modifier,
                                scrollBehavior = scrollBehavior,
                                contentPadding = {},
                                onAlphaChanged = { bd, _ -> topBarBlurAlpha = bd },
                                startAction = { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = { finish() },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.ChevronBackward,
                                        contentDescription = "返回",
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
                        Box(
                            modifier = Modifier.fillMaxSize().then(
                                Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                            )
                        ) {
                            AppreciateAuthorScreen(
                                scrollBehavior = scrollBehavior,
                            )
                        }
                    }
                }
            }
        }
    }
}
