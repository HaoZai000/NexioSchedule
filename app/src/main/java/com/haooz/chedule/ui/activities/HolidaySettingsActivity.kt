package com.haooz.chedule.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberLiquidGlassBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

class HolidaySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyThemeAwareSystemBars()
        setContent {
            CourseScheduleTheme {
                val scrollBehavior = rememberSharedScrollBehavior()
                val liquidBackdrop = rememberLiquidGlassBackdrop()
                val background = MiuixTheme.colorScheme.surface
                val backdrop = rememberLayerBackdrop { drawRect(background); drawContent() }
                Scaffold(topBar = {
                    val alpha = remember { mutableStateOf(0f) }
                    ProgressiveBlurTopBar(backdrop = liquidBackdrop, blurAlpha = alpha.value) {
                        CollapsibleTopAppBar(
                            title = "假期调休设置", largeTitle = "假期调休设置", scrollBehavior = scrollBehavior,
                            contentPadding = {}, onAlphaChanged = { value, _ -> alpha.value = value },
                            startAction = { ba, sa ->
                                LiquidTopBarButton(
                                    onClick = { finish() }, backdrop = liquidBackdrop,
                                    icon = MiuixIcons.ChevronBackward, contentDescription = "返回",
                                    iconSize = 25.dp, iconOffset = DpOffset((-2).dp, 0.dp),
                                    backdropAlpha = ba, shadowAlpha = sa
                                )
                            }
                        )
                    }
                }) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                        Box(Modifier.fillMaxSize().then(Modifier.liquidGlassLayerBackdrop(liquidBackdrop))) {
                            HolidaySettingsScreen(scrollBehavior = scrollBehavior, liquidGlassBackdrop = liquidBackdrop)
                        }
                    }
                }
            }
        }
    }
}
