package com.haooz.chedule.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

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
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val backgroundColor = MiuixTheme.colorScheme.surface
                val backdrop = rememberLayerBackdrop {
                    drawRect(backgroundColor)
                    drawContent()
                }
                val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                val scrollBehavior = rememberSharedScrollBehavior()
                val currentDate = remember { LocalDate.now() }
                var year by remember { mutableIntStateOf(currentDate.year) }
                var entries by remember {
                    mutableStateOf(HolidayManager.load(context, year))
                }
                var loading by remember { mutableStateOf(false) }

                fun reload() {
                    entries = HolidayManager.load(context, year)
                }

                fun requestYear(targetYear: Int, automatic: Boolean = false) {
                    if (loading) return
                    loading = true
                    scope.launch(Dispatchers.IO) {
                        val result = runCatching {
                            val conn = URL(
                                "https://unpkg.com/holiday-calendar@1.3.0/data/CN/$targetYear.json"
                            ).openConnection() as HttpURLConnection
                            conn.connectTimeout = 10_000
                            conn.readTimeout = 10_000
                            val text = conn.inputStream.bufferedReader().use { it.readText() }
                            conn.disconnect()
                            HolidayManager.parseApiResponse(text)
                        }.getOrDefault(emptyList())
                        withContext(Dispatchers.Main) {
                            HolidayManager.mergeApiEntries(context, targetYear, result)
                            if (targetYear == year) reload()
                            loading = false
                            if (!automatic) {
                                val message = if (result.isEmpty()) {
                                    "获取失败或暂无数据"
                                } else {
                                    "已更新 ${result.size} 条记录"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    if (entries.isEmpty()) requestYear(year, automatic = true)
                }

                Scaffold(
                    topBar = {
                        var topBarBlurAlpha by remember { mutableFloatStateOf(0f) }
                        ProgressiveBlurTopBar(
                            backdrop = liquidGlassBackdrop,
                            blurAlpha = topBarBlurAlpha,
                        ) {
                            CollapsibleTopAppBar(
                                title = "假期调休设置",
                                largeTitle = "假期调休设置",
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
                                        iconOffset = DpOffset((-2).dp, 0.dp),
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                },
                                endAction = { backdropAlpha, shadowAlpha ->
                                    if (loading) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = (-6).dp, y = (-4).dp)
                                                .size(40.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                progress = null,
                                            )
                                        }
                                    } else {
                                        LiquidTopBarButton(
                                            onClick = { requestYear(year) },
                                            backdrop = liquidGlassBackdrop,
                                            icon = MiuixIcons.Normal.Update,
                                            contentDescription = "更新",
                                            iconSize = 28.dp,
                                            backdropAlpha = backdropAlpha,
                                            shadowAlpha = shadowAlpha,
                                        )
                                    }
                                },
                            )
                        }
                    }
                ) {
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
                            HolidaySettingsScreen(
                                scrollBehavior = scrollBehavior,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                year = year,
                                entries = entries,
                                onYearChange = { newYear ->
                                    year = newYear
                                    reload()
                                },
                                reload = { reload() },
                            )
                        }
                    }
                }
            }
        }
    }
}
