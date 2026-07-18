/** 课程编辑页面 - 修改课程时段/周次 */
package com.haooz.chedule.ui.activities

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.components.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.oobe.OobeCubicOutEasing
import com.haooz.chedule.ui.oobe.OobeQuartOutEasing
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Color as ComposeColor
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

// ===================== Animation Foundation =====================

private data class AnimState(
    val bgAlpha: Float,
    val snapshotAlpha: Float,
    val contentAlpha: Float,
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val clipBottom: Float,
    val progress: Float
)

private class AnimClipShape(
    private val screenWidth: Float,
    private val screenCornerRadiusPx: Float,
    private val startCornerRadiusPx: Float,
    private val animState: androidx.compose.runtime.State<AnimState>
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val s = animState.value
        val radiusPx = if (s.progress >= 1f) 0f
        else startCornerRadiusPx + (screenCornerRadiusPx - startCornerRadiusPx) * s.progress
        val radiusDp = (radiusPx / s.scale / density.density).dp
        return RoundedRectangle(radiusDp).createOutline(
            androidx.compose.ui.geometry.Size(screenWidth, s.clipBottom),
            layoutDirection,
            density
        )
    }
}

// ===================== Course Grouping Helpers =====================

/** 课程分组键：按节次/周次区分不同课程时段 */
data class CourseGroupKey(
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val weekType: Int,
    val startWeek: Int,
    val endWeek: Int,
    val selectedWeeks: List<Int> = emptyList()
)

/** 一个课程时段（相同节次/周次配置的课程集合） */
data class CourseGroup(
    val key: CourseGroupKey,
    val courses: List<Course>
)

// ===================== CourseEditScreen =====================

@Composable
fun CourseEditScreen(
    courseName: String,
    courses: List<Course>,
    cardLeft: Float,
    cardTop: Float,
    cardWidth: Float,
    cardHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
    screenCornerRadius: Float,
    cardSnapshot: Bitmap?,
    sectionTimes: Map<Int, String>,
    onBackStart: () -> Unit,
    onBack: () -> Unit,
    onCourseUpdated: () -> Unit = {},
    liquidGlassBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null
) {
    val appStyle = rememberAppStyle()
    val isLiquidGlass = appStyle == "liquidglass" && liquidGlassBackdrop != null

    val density = LocalDensity.current
    val animProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val startCornerRadiusPx = 20f * density.density
    val morphOpenEase = OobeQuartOutEasing
    val morphExitEase = OobeCubicOutEasing

    // ---- Back navigation with exit animation ----
    BackHandler {
        onBackStart()
        scope.launch {
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 380,
                    easing = morphExitEase
                )
            )
            onBack()
        }
    }

    // ---- Enter animation ----
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 620,
                easing = morphOpenEase
            )
        )
    }

    // ---- Derived animation state ----
    val animState = remember {
        derivedStateOf {
            val p = animProgress.value
            val bgAlpha = (p * 0.5f).coerceIn(0f, 0.5f)
            val snapAlpha = (1f - p * 3f).coerceIn(0f, 1f)
            val contAlpha = ((p - 0.1f) / 0.5f).coerceIn(0f, 1f)
            val scale = cardWidth / screenWidth + (1f - cardWidth / screenWidth) * p
            val translationX = (cardLeft + cardWidth / 2f - screenWidth / 2f) * (1f - p)
            val translationY = cardTop * (1f - p)
            val clipBottom = cardHeight + 20 + (screenHeight - cardHeight - 20) * p
            AnimState(bgAlpha, snapAlpha, contAlpha, translationX, translationY, scale, clipBottom, p)
        }
    }

    // ---- UI State ----
    val isDark = isAppDarkTheme()
    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    var listScrollY by remember { mutableIntStateOf(0) }
    val scrollBehavior = MiuixScrollBehavior()
    val blurAlpha = if (listScrollY < 50) 0f else ((listScrollY - 50) / 50f).coerceIn(0f, 0.7f)
    val topBarColor = if (listScrollY < 50) {
        MiuixTheme.colorScheme.surface
    } else {
        val topBarColorProgress = ((listScrollY - 50) / 50f).coerceIn(0f, 1f)
        val surface = MiuixTheme.colorScheme.surface
        val target = if (isDark) ComposeColor.Black.copy(alpha = 0.7f) else ComposeColor.White.copy(alpha = 0.7f)
        lerp(surface, target, topBarColorProgress)
    }
    val topAppBarColors = BlurDefaults.blurColors(
        blendColors = listOf(
            if (isDark) BlendColorEntry(ComposeColor.Black.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
            else BlendColorEntry(ComposeColor.White.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
        ),
        brightness = 0f,
        contrast = 1f,
        saturation = 1.2f
    )

    // ---- Morphing container ----
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) ComposeColor(0xFF2C2C2C).copy(alpha = animState.value.bgAlpha)
                else ComposeColor.Black.copy(alpha = animState.value.bgAlpha)
            )
            .pointerInput(Unit) {
                // Block touch events during animation
            }
    ) {
        val s = animState.value
        val clipShape = remember { AnimClipShape(screenWidth, screenCornerRadius, startCornerRadiusPx, animState) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = false
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleX = s.scale
                    scaleY = s.scale
                    translationX = s.translationX
                    translationY = s.translationY
                }
                .clip(clipShape)
                .background(MiuixTheme.colorScheme.surface)
        ) {
            // Card snapshot during morph
            if (cardSnapshot != null && s.snapshotAlpha > 0f) {
                Image(
                    bitmap = cardSnapshot.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .clip(RoundedRectangle(22.dp))
                        .graphicsLayer { alpha = s.snapshotAlpha },
                    contentScale = ContentScale.FillWidth
                )
            }

            // Content that fades in
            if (s.contentAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = s.contentAlpha }
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
                                        title = courseName,
                                        modifier = Modifier.zIndex(1f),
                                        scrollBehavior = scrollBehavior,
                                        navigationIcon = {}
                                    )
                                    LiquidTopBarButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            onBackStart()
                                            scope.launch {
                                                animProgress.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(
                                                        durationMillis = 380,
                                                        easing = morphExitEase
                                                    )
                                                )
                                                onBack()
                                            }
                                        },
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
                            } else {
                                top.yukonga.miuix.kmp.basic.TopAppBar(
                                    modifier = if (blurAlpha > 0f) {
                                        Modifier.textureBlur(
                                            backdrop = backdrop,
                                            shape = RectangleShape,
                                            colors = topAppBarColors
                                        )
                                    } else {
                                        Modifier
                                    },
                                    color = topBarColor,
                                    title = courseName,
                                    scrollBehavior = scrollBehavior,
                                    navigationIcon = {
                                        top.yukonga.miuix.kmp.basic.IconButton(onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            onBackStart()
                                            scope.launch {
                                                animProgress.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(
                                                        durationMillis = 380,
                                                        easing = morphExitEase
                                                    )
                                                )
                                                onBack()
                                            }
                                        },
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            top.yukonga.miuix.kmp.basic.Icon(
                                                imageVector = MiuixIcons.Back,
                                                contentDescription = "返回",
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(backdrop)
                                .then(
                                    if (isLiquidGlass) Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                                    else Modifier
                                )
                        ) {
                            // TODO: Course segment cards will be added in subsequent tasks
                            // LazyColumn with edit cards goes here
                        }
                    }
                }
            }
        }
    }
}
