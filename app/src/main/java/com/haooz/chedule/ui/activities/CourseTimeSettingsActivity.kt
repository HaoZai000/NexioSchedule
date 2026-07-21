/** 课程时间设置页面 */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.graphics.Bitmap
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.zIndex
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.components.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.oobe.OobeCubicOutEasing
import com.haooz.chedule.ui.oobe.OobeQuartOutEasing
import com.haooz.chedule.ui.screens.TimeConfigEditScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.rememberAppStyle
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

class CourseTimeSettingsActivity : ComponentActivity() {
    // 小窗状态
    private var _isInFreeformWindow = mutableStateOf(false)
    val isInFreeformWindow: Boolean get() = _isInFreeformWindow.value

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        _isInFreeformWindow.value = isInMultiWindowMode
    }

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
        _isInFreeformWindow.value = isInMultiWindowMode
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
                val repository = remember { CourseRepository(this@CourseTimeSettingsActivity) }
                val context = androidx.compose.ui.platform.LocalContext.current
                val activity = context as? CourseTimeSettingsActivity

                // 小窗状态
                var isInFreeformWindow by remember { mutableStateOf(activity?.isInFreeformWindow ?: false) }
                LaunchedEffect(Unit) {
                    snapshotFlow { activity?.isInFreeformWindow }
                        .collect { value ->
                            isInFreeformWindow = value ?: false
                        }
                }

                // 页面状态管理
                var currentPage by remember { mutableStateOf("select") }
                var editingConfig by remember { mutableStateOf<TimeConfig?>(null) }
                var editingCardBounds by remember { mutableStateOf<TimeConfigCardBounds?>(null) }
                var listRefreshTrigger by remember { mutableIntStateOf(0) }
                var hideConfigId by remember { mutableStateOf<Long?>(null) }
                var hideFab by remember { mutableStateOf(false) }
                var newlyAddedConfigId by remember { mutableStateOf<Long?>(null) }

                // 屏幕尺寸与圆角
                val density = LocalDensity.current
                val windowManager = remember { getSystemService(android.view.WindowManager::class.java) }
                val windowMetrics = remember(isInFreeformWindow) { windowManager.currentWindowMetrics }
                val screenWidth = remember(isInFreeformWindow) { windowMetrics.bounds.width().toFloat() }
                val screenHeight = remember(isInFreeformWindow) { windowMetrics.bounds.height().toFloat() }
                @SuppressLint("WrongConstant")
                val screenCornerRadius = remember(isInFreeformWindow) {
                    if (isInFreeformWindow) {
                        20f * density.density
                    } else {
                        try {
                            windowMetrics.windowInsets.getRoundedCorner(0)?.radius?.toFloat() ?: 0f
                        } catch (_: Exception) { 0f }
                    }
                }

                // 快照截取
                val screenGraphicsLayer = rememberGraphicsLayer()
                var cardSnapshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                val coroutineScope = rememberCoroutineScope()

                // 背景缩放与模糊动画
                val backgroundScale = remember { Animatable(1f) }
                val managePageBlurRadius = remember { Animatable(0f) }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 主内容（缩放+模糊动画）
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .blur(managePageBlurRadius.value.dp)
                        .background(MiuixTheme.colorScheme.surface)
                    ) {
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
                                    val scale = backgroundScale.value
                                    if (scale < 0.999f) {
                                        val path = Path().apply {
                                            addSquircleRect(
                                                width = size.width,
                                                height = size.height,
                                                cornerRadius = screenCornerRadius
                                            )
                                        }
                                        clipPath(path) {
                                            this@drawWithContent.drawContent()
                                        }
                                    } else {
                                        drawContent()
                                    }
                                }
                        ) {
                            Scaffold(
                                topBar = {
                                    if (isLiquidGlass) {
                                        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                                        ProgressiveBlurTopBar(
                                            backdrop = liquidGlassBackdrop,
                                        ) {
                                            SmallTopAppBar(
                                                color = Color.Transparent,
                                                title = "课程节数与时间",
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
                                        CourseTimeSettingsScreen(
                                            onBack = { finish() },
                                            onEditConfig = { config, bounds ->
                                                editingConfig = config
                                                editingCardBounds = bounds
                                                hideConfigId = config.id
                                                currentPage = "edit"
                                                coroutineScope.launch {
                                                    val lx = bounds.left.toInt().coerceIn(0, screenWidth.toInt() - 1)
                                                    val ly = bounds.top.toInt().coerceIn(0, screenHeight.toInt() - 1)
                                                    val lw = bounds.width.toInt().coerceIn(1, screenWidth.toInt() - lx)
                                                    val lh = bounds.height.toInt().coerceIn(1, screenHeight.toInt() - ly)
                                                    cardSnapshot = try {
                                                        val fullBitmap = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                                        Bitmap.createBitmap(fullBitmap, lx, ly, lw, lh)
                                                    } catch (_: Exception) { null }
                                                }
                                                coroutineScope.launch {
                                                    delay(16)
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
                                            },
                                            onCreateConfig = { bounds ->
                                                editingConfig = TimeConfig(name = "")
                                                editingCardBounds = bounds
                                                hideFab = true
                                                coroutineScope.launch {
                                                    val lx = bounds.left.toInt().coerceIn(0, screenWidth.toInt() - 1)
                                                    val ly = bounds.top.toInt().coerceIn(0, screenHeight.toInt() - 1)
                                                    val lw = bounds.width.toInt().coerceIn(1, screenWidth.toInt() - lx)
                                                    val lh = bounds.height.toInt().coerceIn(1, screenHeight.toInt() - ly)
                                                    cardSnapshot = try {
                                                        val fullBitmap = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                                        Bitmap.createBitmap(fullBitmap, lx, ly, lw, lh)
                                                    } catch (_: Exception) { null }
                                                }
                                                currentPage = "edit"
                                                coroutineScope.launch {
                                                    delay(16)
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
                                            },
                                            liquidGlassBackdrop = liquidGlassBackdrop,
                                            refreshTrigger = listRefreshTrigger,
                                hideConfigId = hideConfigId,
                                hideFab = hideFab,
                                newlyAddedConfigId = newlyAddedConfigId,
                                onNewConfigAnimDone = { newlyAddedConfigId = null }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 编辑页面渲染在 Scaffold 外面（与 CourseManageActivity 结构一致）
                if (currentPage == "edit") {
                    editingConfig?.let { config ->
                        val isNewConfig = config.id == 0L
                        val bounds = editingCardBounds
                        TimeConfigEditScreen(
                            timeConfig = config,
                            onBackStart = {
                                coroutineScope.launch {
                                    launch {
                                        backgroundScale.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(370, easing = OobeCubicOutEasing)
                                        )
                                    }
                                    launch {
                                        managePageBlurRadius.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(370, easing = OobeCubicOutEasing)
                                        )
                                    }
                                }
                            },
                            onBack = {
                                currentPage = "select"
                                editingConfig = null
                                editingCardBounds = null
                                cardSnapshot = null
                                hideConfigId = null
                                hideFab = false
                            },
                            onSave = { savedConfig ->
                                if (isNewConfig) {
                                    val newId = repository.addTimeConfig(savedConfig)
                                    repository.switchToTimeConfig(newId)
                                    newlyAddedConfigId = newId
                                } else {
                                    repository.saveTimeConfig(savedConfig)
                                    if (savedConfig.id == repository.getCurrentTimeConfigId()) {
                                        repository.switchToTimeConfig(savedConfig.id)
                                    }
                                }
                                listRefreshTrigger++
                            },
                            cardLeft = bounds?.left ?: 0f,
                            cardTop = bounds?.top ?: 0f,
                            cardWidth = bounds?.width ?: screenWidth,
                            cardHeight = bounds?.height ?: (screenHeight * 0.2f),
                            screenWidth = screenWidth,
                            screenHeight = screenHeight,
                            screenCornerRadius = screenCornerRadius,
                            cardStartCornerRadius = if (isNewConfig) 92f else 20f,
                            cardSnapshot = cardSnapshot,
                            isFabCreation = isNewConfig && bounds != null && bounds.width > 0f && bounds.height > 0f
                                    && abs(bounds.width - bounds.height) / bounds.width < 0.2f,
                            liquidGlassBackdrop = editLiquidGlassBackdrop
                        )
                    }
                }
            }
        }
    }
}
