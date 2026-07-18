/** 课程管理页面 */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.components.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.rememberAppStyle
import com.haooz.chedule.ui.oobe.OobeCubicOutEasing
import com.haooz.chedule.ui.oobe.OobeQuartOutEasing
import kotlinx.coroutines.launch
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
                val editLiquidGlassBackdrop = if (appStyle == "liquidglass") {
                    com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                } else null
                val isLiquidGlass = liquidGlassBackdrop != null

                // Edit screen state
                var showEditScreen by remember { mutableStateOf(false) }
                var selectedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
                var cardLeft by remember { mutableStateOf(0f) }
                var cardTop by remember { mutableStateOf(0f) }
                var cardWidth by remember { mutableStateOf(0f) }
                var cardHeight by remember { mutableStateOf(0f) }
                var cardSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                var cardColor by remember { mutableStateOf(Color(0xFF4CAF50)) }
                var cardAlpha by remember { mutableStateOf(0.15f) }

                // Graphics layer for capturing screen content
                val screenGraphicsLayer = rememberGraphicsLayer()

                // Background scale animation (same as MainActivity)
                val backgroundScale = remember { Animatable(1f) }
                val managePageBlurRadius = remember { Animatable(0f) }
                val windowInfo = LocalWindowInfo.current
                val coroutineScope = rememberCoroutineScope()
                val density = LocalDensity.current
                val context = androidx.compose.ui.platform.LocalContext.current

                // Dynamically get screen corner radius from window insets
                val screenCornerRadius = remember {
                    try {
                        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                        val windowMetrics = windowManager.currentWindowMetrics
                        val insets = windowMetrics.windowInsets
                        @SuppressLint("WrongConstant")
                        insets.getRoundedCorner(0)?.radius?.toFloat() ?: 0f
                    } catch (_: Exception) {
                        0f
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Main content with blur and scale animation
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .blur(managePageBlurRadius.value.dp)
                        .background(MiuixTheme.colorScheme.surface)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val scale = backgroundScale.value
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .drawWithContent {
                                    screenGraphicsLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    drawContent()
                                }
                        ) {
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
                                            onCourseClick = { courses, left, top, width, height, _, color, alpha ->
                                                coroutineScope.launch {
                                                    selectedCourses = courses
                                                    cardLeft = left
                                                    cardTop = top
                                                    cardWidth = width
                                                    cardHeight = height
                                                    cardColor = color
                                                    cardAlpha = alpha

                                                    // Capture full screen snapshot
                                                    val fullSnapshot = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()

                                                    // Crop card area from full snapshot
                                                    cardSnapshot = try {
                                                        val x = left.toInt().coerceIn(0, fullSnapshot.width - 1)
                                                        val y = top.toInt().coerceIn(0, fullSnapshot.height - 1)
                                                        val w = width.toInt().coerceIn(1, fullSnapshot.width - x)
                                                        val h = height.toInt().coerceIn(1, fullSnapshot.height - y)
                                                        android.graphics.Bitmap.createBitmap(fullSnapshot, x, y, w, h)
                                                    } catch (_: Exception) {
                                                        null
                                                    }

                                                    showEditScreen = true
                                                    launch {
                                                        backgroundScale.animateTo(
                                                            targetValue = 0.92f,
                                                            animationSpec = tween(620, easing = OobeQuartOutEasing)
                                                        )
                                                    }
                                                    launch {
                                                        managePageBlurRadius.animateTo(
                                                            targetValue = 5f,
                                                            animationSpec = tween(620, easing = OobeQuartOutEasing)
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Edit screen (above blur layer)
                    if (showEditScreen) {
                        CourseEditScreen(
                            courses = selectedCourses,
                            cardLeft = cardLeft,
                            cardTop = cardTop,
                            cardWidth = cardWidth,
                            cardHeight = cardHeight,
                            screenWidth = windowInfo.containerSize.width.toFloat(),
                            screenHeight = windowInfo.containerSize.height.toFloat(),
                            screenCornerRadius = screenCornerRadius,
                            cardSnapshot = cardSnapshot,
                            cardColor = cardColor,
                            cardAlpha = cardAlpha,
                            sectionTimes = Course.defaultSectionTimes,
                            onBackStart = {
                                coroutineScope.launch {
                                    launch {
                                        backgroundScale.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(380, easing = OobeCubicOutEasing)
                                        )
                                    }
                                    launch {
                                        managePageBlurRadius.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(380, easing = OobeCubicOutEasing)
                                        )
                                    }
                                }
                            },
                            onBack = {
                                showEditScreen = false
                                cardSnapshot = null
                            },
                            onCourseUpdated = { },
                            liquidGlassBackdrop = editLiquidGlassBackdrop
                        )
                    }
                }
            }
        }
    }
}
