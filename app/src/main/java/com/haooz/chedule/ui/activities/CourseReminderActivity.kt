/** 课程提醒设置页面 */
package com.haooz.chedule.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

class CourseReminderActivity : ComponentActivity() {
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
                val context = LocalContext.current
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

                Scaffold(
                    topBar = {
                        ProgressiveBlurTopBar(
                            backdrop = liquidGlassBackdrop,
                        ) {
                            CollapsibleTopAppBar(
                                title = "课程提醒",
                                largeTitle = "课程提醒",
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
                            )
                        }
                    }
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop)
                    ) {
                        // 采样层只包内容；玻璃按钮必须放在层外，否则 drawBackdrop 会采到自己导致循环采样崩溃
                        Box(
                            modifier = Modifier.fillMaxSize().then(
                                Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                            )
                        ) {
                            CourseReminderScreen(
                                scrollBehavior = scrollBehavior,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                            )
                        }

                        val islandEnabled = remember {
                            CourseRepository(context).getIslandNotification() &&
                                IslandNotificationHelper.isIslandSupported(context)
                        }
                        LiquidGlassTextButton(
                            text = if (islandEnabled) "测试小米超级岛" else "测试实时活动",
                            onClick = {
                                if (islandEnabled) {
                                    IslandNotificationHelper.sendTestIslandNotification(context)
                                    Toast.makeText(context, "已发送超级岛测试通知", Toast.LENGTH_SHORT).show()
                                } else {
                                    CourseReminderHelper.sendTestLiveNotification(context)
                                    Toast.makeText(context, "已发送实时活动测试通知", Toast.LENGTH_SHORT).show()
                                }
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
                    }
                }
            }
        }
    }
}
