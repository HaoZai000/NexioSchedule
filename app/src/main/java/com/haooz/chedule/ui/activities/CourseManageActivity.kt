/** 课程管理页面 */
package com.haooz.chedule.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.Course
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.unit.DpOffset
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

class CourseManageActivity : ComponentActivity() {
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

                // Edit screen state
                var showEditScreen by remember { mutableStateOf(false) }
                var selectedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
                var cardPosition by remember { mutableStateOf(CardPosition(0f, 0f, 0f, 0f)) }
                var cardSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

                if (showEditScreen) {
                    val windowInfo = LocalWindowInfo.current
                    val coroutineScope = rememberCoroutineScope()
                    CourseEditScreen(
                        courses = selectedCourses,
                        cardLeft = cardPosition.left,
                        cardTop = cardPosition.top,
                        cardWidth = cardPosition.width,
                        cardHeight = cardPosition.height,
                        screenWidth = windowInfo.containerSize.width.toFloat(),
                        screenHeight = windowInfo.containerSize.height.toFloat(),
                        screenCornerRadius = 40f,
                        cardSnapshot = cardSnapshot,
                        sectionTimes = Course.defaultSectionTimes,
                        onBackStart = { },
                        onBack = {
                            showEditScreen = false
                            cardSnapshot = null
                        },
                        onCourseUpdated = { },
                        liquidGlassBackdrop = liquidGlassBackdrop
                    )
                } else {
                    Scaffold(
                        topBar = {
                            if (isLiquidGlass) {
                                val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                                ProgressiveBlurTopBar(
                                    backdrop = liquidGlassBackdrop!!,
                                ) {
                                    SmallTopAppBar(
                                        color = Color.Transparent,
                                        title = "课程管理",
                                        modifier = Modifier.zIndex(1f),
                                        navigationIcon = {}
                                    )
                                    LiquidTopBarButton(
                                        onClick = { finish() },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Medium.ChevronBackward,
                                        contentDescription = "返回",
                                        modifier = Modifier
                                            .zIndex(2f)
                                            .offset(x = 20.dp, y = if (statusBarPadding > 0.dp) statusBarPadding + 5.dp else 42.dp),
                                        iconSize = 22.dp,
                                        iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                        useBackdropShadow = true
                                    )
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
                                CourseManageScreen(
                                    onBack = { finish() },
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    onCourseClick = { courses, left, top, width, height, snapshot ->
                                        selectedCourses = courses
                                        cardPosition = CardPosition(left, top, width, height)
                                        cardSnapshot = snapshot
                                        showEditScreen = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CardPosition(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)
