/** 课程编辑页面 - 修改课程时段/周次 */
package com.haooz.chedule.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.components.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.oobe.OobeCubicOutEasing
import com.haooz.chedule.ui.oobe.OobeQuartOutEasing
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

// ===================== Animation Foundation =====================

private data class EditAnimState(
    val bgAlpha: Float,
    val snapshotAlpha: Float,
    val contentAlpha: Float,
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val clipBottom: Float,
    val progress: Float
)

private class EditAnimClipShape(
    private val screenWidth: Float,
    private val screenCornerRadiusPx: Float,
    private val startCornerRadiusPx: Float,
    private val animState: androidx.compose.runtime.State<EditAnimState>
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val s = animState.value
        // 动画过程中：从卡片圆角插值到屏幕圆角
        // 动画结束瞬间：圆角归零
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

data class CourseGroupKey(
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val weekType: Int,
    val startWeek: Int,
    val endWeek: Int,
    val selectedWeeks: List<Int> = emptyList()
)

data class CourseGroup(
    val key: CourseGroupKey,
    val courses: List<Course>
)

// ===================== CourseEditScreen =====================

@Composable
fun CourseEditScreen(
    courses: List<Course>,
    cardLeft: Float,
    cardTop: Float,
    cardWidth: Float,
    cardHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
    screenCornerRadius: Float,
    cardSnapshot: Bitmap?,
    cardColor: Color = Color(0xFF4CAF50),
    cardAlpha: Float = 0.15f,
    sectionTimes: Map<Int, String>,
    onBackStart: () -> Unit,
    onBack: () -> Unit,
    onCourseUpdated: (Course) -> Unit = { _ -> },
    onColorChanged: (Long) -> Unit = { _ -> },
    getOccupiedWeeks: (dayOfWeek: Int, startSection: Int, endSection: Int, excludeIds: List<String>) -> Set<Int> = { _, _, _, _ -> emptySet() },
    liquidGlassBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null
) {
    val courseName = courses.firstOrNull()?.name ?: ""
    // 课程颜色状态（所有同名课程共享，仅保存时生效）
    var selectedColor by remember { mutableLongStateOf(courses.firstOrNull()?.colorRes ?: Course.courseColors.first()) }
    var showColorDialog by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(Color(selectedColor)) }

    val appStyle = rememberAppStyle()
    val isLiquidGlass = appStyle == "liquidglass" && liquidGlassBackdrop != null

    val density = LocalDensity.current
    val animProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val startCornerRadiusPx = 16f * density.density
    val morphOpenEase = OobeQuartOutEasing
    val morphExitEase = OobeCubicOutEasing

    // ---- Back navigation with exit animation ----
    BackHandler {
        onBackStart()
        scope.launch {
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 370,
                    easing = morphExitEase
                )
            )
            onBack()
        }
    }

    // ---- Enter animation ----
    LaunchedEffect(Unit) {
        // 等待首帧渲染完成后再开始动画
        delay(16.milliseconds)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 600,
                easing = morphOpenEase
            )
        )
    }

    // ---- Derived animation state ----
    // graphicsLayer.scale 同时缩放宽高，clipBottom 需要反向补偿
    // 使得 scale * clipBottom 在 p=0 时等于 cardHeight
    val animState = remember {
        derivedStateOf {
            val p = animProgress.value
            val bgAlpha = (p * 0.5f).coerceIn(0f, 0.5f)
            val snapAlpha = (1f - p * 3f).coerceIn(0f, 1f)
            val contAlpha = ((p - 0.1f) / 0.5f).coerceIn(0f, 1f)
            val scale = cardWidth / screenWidth + (1f - cardWidth / screenWidth) * p
            val translationX = (cardLeft + cardWidth / 2f - screenWidth / 2f) * (1f - p)
            val translationY = cardTop * (1f - p)
            // clipBottom 在 pre-transform 空间，需要除以 scale 使渲染后高度正确
            val rawClipBottom = cardHeight + (screenHeight - cardHeight) * p
            val clipBottom = rawClipBottom / scale
            EditAnimState(
                bgAlpha,
                snapAlpha,
                contAlpha,
                translationX,
                translationY,
                scale,
                clipBottom,
                p
            )
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
    var saveTrigger by remember { mutableIntStateOf(0) }

    // 保存时同步颜色到所有同名课程
    LaunchedEffect(saveTrigger) {
        if (saveTrigger > 0 && selectedColor != courses.firstOrNull()?.colorRes) {
            onColorChanged(selectedColor)
            courses.forEach { course ->
                onCourseUpdated(course.copy(colorRes = selectedColor, lastModified = System.currentTimeMillis()))
            }
        }
    }

    // 仅在阈值变化时重算模糊相关值，减少 recomposition
    val blurAlpha = if (listScrollY < 50) 0f else ((listScrollY - 50) / 50f).coerceIn(0f, 0.7f)
    val surface = MiuixTheme.colorScheme.surface
    val topBarColor = if (listScrollY < 50) {
        surface
    } else {
        val topBarColorProgress = ((listScrollY - 50) / 50f).coerceIn(0f, 1f)
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

    // ---- Morphing container (identical to CourseDetailScreen) ----
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
        val clipShape = remember { EditAnimClipShape(screenWidth, screenCornerRadius, startCornerRadiusPx, animState) }

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
                .background(cardColor.copy(alpha = cardAlpha))
        ) {
            // Card snapshot during morph (identical to CourseDetailScreen)
            if (cardSnapshot != null && s.snapshotAlpha > 0f) {
                val imageBitmap = remember(cardSnapshot) { cardSnapshot.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .clip(RoundedRectangle((18 / s.scale).dp))
                        .graphicsLayer { alpha = s.snapshotAlpha },
                    contentScale = ContentScale.FillWidth
                )
            }

            // Content that fades in (identical to CourseDetailScreen)
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
                                        icon = MiuixIcons.Normal.Close,
                                        contentDescription = "返回",
                                        modifier = Modifier
                                            .zIndex(2f)
                                            .offset(x = 20.dp, y = if (statusBarPadding > 0.dp) statusBarPadding + 5.dp else 42.dp),
                                        iconSize = 22.dp,
                                        useBackdropShadow = true
                                    )
                                    LiquidTopBarButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            saveTrigger++
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
                                        icon = MiuixIcons.Ok,
                                        contentDescription = "保存并关闭",
                                        modifier = Modifier
                                            .zIndex(2f)
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-20).dp, y = if (statusBarPadding > 0.dp) statusBarPadding + 5.dp else 42.dp),
                                        iconSize = 23.dp,
                                        iconOffset = DpOffset(x = 0.dp, y = 0.dp),
                                        useBackdropShadow = true,
                                        iconTint = Color.White,
                                        containerColor = if (isAppDarkTheme())MiuixTheme.colorScheme.primary.copy(alpha = 0.8f) else MiuixTheme.colorScheme.primary.copy(alpha = 0.9f)
                                    )
                                }
                            } else {
                                TopAppBar(
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
                                        IconButton(onClick = {
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
                                            Icon(
                                                imageVector = MiuixIcons.Normal.Close,
                                                contentDescription = "返回",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                                saveTrigger++
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
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Ok,
                                                contentDescription = "保存并关闭",
                                                modifier = Modifier.size(26.dp)
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
                            val listState = rememberLazyListState()
                            LaunchedEffect(listState) {
                                snapshotFlow { listState.firstVisibleItemScrollOffset }
                                    .collect { offset ->
                                        listScrollY = offset
                                    }
                            }

                            Card(
                                modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface),
                                insideMargin = PaddingValues(0.dp),
                                colors = CardDefaults.defaultColors(
                                    color = MiuixTheme.colorScheme.surface,
                                    contentColor = MiuixTheme.colorScheme.onSurface
                                )
                            ) {
                                // Group courses by day/section/week configuration
                                val courseGroups = remember(courses) {
                                    courses.groupBy { course ->
                                        CourseGroupKey(
                                            dayOfWeek = course.dayOfWeek,
                                            startSection = course.startSection,
                                            endSection = course.endSection,
                                            weekType = course.weekType,
                                            startWeek = course.startWeek,
                                            endWeek = course.endWeek,
                                            selectedWeeks = course.selectedWeeks
                                        )
                                    }.map { (key, groupCourses) ->
                                        CourseGroup(key = key, courses = groupCourses)
                                    }
                                }

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .overScrollVertical()
                                        .scrollEndHaptic(
                                            hapticFeedbackType = HapticFeedbackType.TextHandleMove
                                        )
                                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        top = if (isLiquidGlass) paddingValues.calculateTopPadding() + (-8).dp else paddingValues.calculateTopPadding() + 8.dp,
                                        end = 16.dp,
                                        bottom = 120.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // 课程颜色选择器（与添加课程弹窗样式一致）
                                    item(key = "color_picker") {
                                        val allColors = Course.courseColors
                                        val colorColumns = 6
                                        val totalItems = allColors.size + 1 // +1 for custom color button
                                        val colorRows = (totalItems + colorColumns - 1) / colorColumns
                                        Card(
                                            cornerRadius = 20.dp,
                                            modifier = Modifier.fillMaxWidth(),
                                            insideMargin = PaddingValues(vertical = 12.dp),
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = "课程颜色",
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
                                                )
                                                for (row in 0 until colorRows) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        for (col in 0 until colorColumns) {
                                                            val colorIndex = row * colorColumns + col
                                                            if (colorIndex < allColors.size) {
                                                                val color = allColors[colorIndex]
                                                                val isSelected = color == selectedColor
                                                                var isPressed by remember { mutableStateOf(false) }
                                                                val primaryColor = MiuixTheme.colorScheme.primary
                                                                val scale = remember { Animatable(1f) }
                                                                val borderAlpha by animateFloatAsState(
                                                                    targetValue = if (isSelected) 1f else 0f,
                                                                    animationSpec = tween(durationMillis = 200),
                                                                    label = "borderAlpha"
                                                                )
                                                                LaunchedEffect(isPressed) {
                                                                    if (isPressed) {
                                                                        scale.animateTo(
                                                                            targetValue = 0.94f,
                                                                            animationSpec = tween(durationMillis = 100)
                                                                        )
                                                                    } else {
                                                                        scale.animateTo(
                                                                            targetValue = 1f,
                                                                            animationSpec = tween(durationMillis = 180)
                                                                        )
                                                                    }
                                                                }
                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .aspectRatio(1f)
                                                                        .graphicsLayer {
                                                                            scaleX = scale.value
                                                                            scaleY = scale.value
                                                                        }
                                                                        .pointerInput(Unit) {
                                                                            awaitPointerEventScope {
                                                                                while (true) {
                                                                                    val event = awaitPointerEvent()
                                                                                    val anyPressed = event.changes.any { it.pressed }
                                                                                    isPressed = anyPressed
                                                                                    if (!anyPressed) {
                                                                                        selectedColor = color
                                                                                    }
                                                                                }
                                                                            }
                                                                        },
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .graphicsLayer { alpha = borderAlpha }
                                                                            .clip(RoundedRectangle(12.dp))
                                                                            .background(primaryColor)
                                                                    )
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .padding(if (isSelected) 2.dp else 0.dp)
                                                                            .clip(RoundedRectangle(10.dp))
                                                                            .background(if (isDark) Color(0xFF242424) else Color(0xFFFFFFFF))
                                                                    )
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .padding(4.dp),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Card(
                                                                            modifier = Modifier.fillMaxSize(),
                                                                            cornerRadius = 8.dp,
                                                                            insideMargin = PaddingValues(0.dp),
                                                                            colors = CardDefaults.defaultColors(
                                                                                color = Color(color).copy(alpha = if (isDark) 0.22f else 0.16f),
                                                                                contentColor = Color.White
                                                                            ),
                                                                            onClick = { selectedColor = color }
                                                                        ) {}
                                                                    }
                                                                }
                                                            } else if (colorIndex == allColors.size) {
                                                                // 自定义颜色按钮
                                                                val isCustomColor = selectedColor !in allColors
                                                                val bgColor = if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7)
                                                                val hintColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                                                val primaryColor = MiuixTheme.colorScheme.primary
                                                                var isCustomPressed by remember { mutableStateOf(false) }
                                                                val customScale = remember { Animatable(1f) }
                                                                val customBorderAlpha by animateFloatAsState(
                                                                    targetValue = if (isCustomColor) 1f else 0f,
                                                                    animationSpec = tween(durationMillis = 200),
                                                                    label = "customBorderAlpha"
                                                                )
                                                                LaunchedEffect(isCustomPressed) {
                                                                    if (isCustomPressed) {
                                                                        customScale.animateTo(
                                                                            targetValue = 0.94f,
                                                                            animationSpec = tween(durationMillis = 100)
                                                                        )
                                                                    } else {
                                                                        customScale.animateTo(
                                                                            targetValue = 1f,
                                                                            animationSpec = tween(durationMillis = 180)
                                                                        )
                                                                    }
                                                                }
                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .aspectRatio(1f)
                                                                        .graphicsLayer {
                                                                            scaleX = customScale.value
                                                                            scaleY = customScale.value
                                                                        }
                                                                        .pointerInput(Unit) {
                                                                            awaitPointerEventScope {
                                                                                while (true) {
                                                                                    val event = awaitPointerEvent()
                                                                                    val anyPressed = event.changes.any { it.pressed }
                                                                                    isCustomPressed = anyPressed
                                                                                    if (!anyPressed) {
                                                                                        customColor = Color(selectedColor)
                                                                                        showColorDialog = true
                                                                                    }
                                                                                }
                                                                            }
                                                                        },
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .graphicsLayer { alpha = customBorderAlpha }
                                                                            .clip(RoundedRectangle(12.dp))
                                                                            .background(primaryColor)
                                                                    )
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .padding(if (isCustomColor) 2.dp else 0.dp)
                                                                            .clip(RoundedRectangle(10.dp))
                                                                            .background(if (isDark) Color(0xFF242424) else Color(0xFFFFFFFF))
                                                                    )
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .padding(4.dp),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Card(
                                                                            modifier = Modifier.fillMaxSize(),
                                                                            cornerRadius = 8.dp,
                                                                            insideMargin = PaddingValues(0.dp),
                                                                            colors = CardDefaults.defaultColors(
                                                                                color = bgColor,
                                                                                contentColor = hintColor
                                                                            ),
                                                                            onClick = {
                                                                                customColor = Color(selectedColor)
                                                                                showColorDialog = true
                                                                            }
                                                                        ) {
                                                                            Box(
                                                                                modifier = Modifier.fillMaxSize(),
                                                                                contentAlignment = Alignment.Center
                                                                            ) {
                                                                                if (isCustomColor) {
                                                                                    Box(
                                                                                        modifier = Modifier
                                                                                            .fillMaxSize(0.7f)
                                                                                            .clip(RoundedRectangle(4.dp))
                                                                                            .background(Color(selectedColor).copy(alpha = if (isDark) 0.22f else 0.16f))
                                                                                    )
                                                                                } else {
                                                                                    Icon(
                                                                                        imageVector = MiuixIcons.Add,
                                                                                        contentDescription = "自定义颜色",
                                                                                        modifier = Modifier.size(18.dp),
                                                                                        tint = hintColor
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            } else {
                                                                Spacer(modifier = Modifier.weight(1f))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    items(
                                        items = courseGroups,
                                        key = { "${it.key.dayOfWeek}_${it.key.startSection}_${it.key.startWeek}" },
                                        contentType = { "CourseGroupCard" }
                                    ) { group ->
                                        CourseGroupCard(
                                            group = group,
                                            sectionTimes = sectionTimes,
                                            onCourseUpdated = onCourseUpdated,
                                            saveTrigger = saveTrigger,
                                            getOccupiedWeeks = { dow, ss, es ->
                                                getOccupiedWeeks(dow, ss, es, group.courses.map { it.id })
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 自定义颜色选择弹窗
                        OverlayDialog(
                            title = "选择颜色",
                            show = showColorDialog,
                            onDismissRequest = { showColorDialog = false }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ColorPalette(
                                    color = customColor,
                                    onColorChanged = { customColor = it },
                                    cornerRadius = 20.dp,
                                    indicatorRadius = 12.dp
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TextButton(
                                        text = "取消",
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showColorDialog = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        text = "确定",
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            selectedColor = (customColor.alpha * 255).toInt().toLong() shl 24 or
                                                    ((customColor.red * 255).toInt().toLong() shl 16) or
                                                    ((customColor.green * 255).toInt().toLong() shl 8) or
                                                    (customColor.blue * 255).toInt().toLong()
                                            showColorDialog = false
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

// ===================== Course Group Card =====================

@Composable
private fun CourseGroupCard(
    group: CourseGroup,
    sectionTimes: Map<Int, String>,
    onCourseUpdated: (Course) -> Unit,
    saveTrigger: Int,
    getOccupiedWeeks: (dayOfWeek: Int, startSection: Int, endSection: Int) -> Set<Int> = { _, _, _ -> emptySet() }
) {
    val key = group.key
    val course = group.courses.first()
    val isDark = isAppDarkTheme()
    val hapticFeedback = LocalHapticFeedback.current
    val totalWeeks = 20
    val totalSections = 12

    var name by remember { mutableStateOf(course.name) }
    var classroom by remember { mutableStateOf(course.classroom) }
    var teacher by remember { mutableStateOf(course.teacher) }
    var dayOfWeek by remember { mutableIntStateOf(course.dayOfWeek) }
    var startSection by remember { mutableIntStateOf(course.startSection) }
    var endSection by remember { mutableIntStateOf(course.endSection) }
    var startWeek by remember { mutableIntStateOf(course.startWeek) }
    var endWeek by remember { mutableIntStateOf(course.endWeek) }

    // 根据当前选择的星期和节次动态计算已占用的周次（排除自身）
    val currentOccupiedWeeks by remember(dayOfWeek, startSection, endSection) {
        derivedStateOf {
            getOccupiedWeeks(dayOfWeek, startSection, endSection)
        }
    }

    // 周次选择状态
    val selectedWeeks = remember {
        mutableStateSetOf<Int>().apply {
            if (course.selectedWeeks.isNotEmpty()) {
                addAll(course.selectedWeeks)
            } else {
                for (w in course.startWeek..course.endWeek) {
                    when (course.weekType) {
                        Course.WEEK_TYPE_ODD -> if (w % 2 == 1) add(w)
                        Course.WEEK_TYPE_EVEN -> if (w % 2 == 0) add(w)
                        else -> add(w)
                    }
                }
            }
        }
    }

    val allWeeks = remember { (1..totalWeeks).toList() }
    val oddWeeks = remember { allWeeks.filter { it % 2 == 1 } }
    val evenWeeks = remember { allWeeks.filter { it % 2 == 0 } }

    val selectableWeeks = remember(allWeeks, currentOccupiedWeeks) { allWeeks.filter { it !in currentOccupiedWeeks } }
    val selectableOddWeeks = remember(selectableWeeks) { selectableWeeks.filter { it % 2 == 1 } }
    val selectableEvenWeeks = remember(selectableWeeks) { selectableWeeks.filter { it % 2 == 0 } }
    val allSelectableSelected = remember(selectableWeeks, selectedWeeks) { selectableWeeks.isNotEmpty() && selectableWeeks.all { it in selectedWeeks } }
    val allSelectableOddSelected = remember(selectableOddWeeks, selectedWeeks) { selectableOddWeeks.all { it in selectedWeeks } }
    val allSelectableEvenSelected = remember(selectableEvenWeeks, selectedWeeks) { selectableEvenWeeks.all { it in selectedWeeks } }
    val someSelectableOddSelected = remember(selectableOddWeeks, selectedWeeks) { selectableOddWeeks.any { it in selectedWeeks } }
    val someSelectableEvenSelected = remember(selectableEvenWeeks, selectedWeeks) { selectableEvenWeeks.any { it in selectedWeeks } }
    val hasOccupiedOddWeeks = remember(selectableOddWeeks, oddWeeks) { selectableOddWeeks.size != oddWeeks.size }
    val hasOccupiedEvenWeeks = remember(selectableEvenWeeks, evenWeeks) { selectableEvenWeeks.size != evenWeeks.size }

    // 节次选择弹窗状态
    var showSectionDialog by remember { mutableStateOf(false) }
    var tempStartSection by remember { mutableIntStateOf(course.startSection) }
    var tempEndSection by remember { mutableIntStateOf(course.endSection) }

    // 当 saveTrigger 变化时保存
    LaunchedEffect(saveTrigger) {
        if (saveTrigger > 0 && name.isNotBlank() && startSection <= endSection && selectedWeeks.isNotEmpty()) {
            val sortedWeeks = selectedWeeks.sorted()
            val minWeek = sortedWeeks.first()
            val maxWeek = sortedWeeks.last()
            val allWeeksInRange = (minWeek..maxWeek).toSet()
            val oddWeeksInRange = allWeeksInRange.filter { it % 2 == 1 }.toSet()
            val evenWeeksInRange = allWeeksInRange.filter { it % 2 == 0 }.toSet()

            val weekType = when (selectedWeeks) {
                allWeeksInRange -> Course.WEEK_TYPE_ALL
                oddWeeksInRange -> Course.WEEK_TYPE_ODD
                evenWeeksInRange -> Course.WEEK_TYPE_EVEN
                else -> Course.WEEK_TYPE_ALL
            }

            val isContiguous = selectedWeeks.size == (maxWeek - minWeek + 1)
            val weeksToSave = if (isContiguous) emptyList() else sortedWeeks

            val updatedCourse = course.copy(
                name = name.trim(),
                classroom = classroom.trim(),
                teacher = teacher.trim(),
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                endSection = endSection,
                startWeek = minWeek,
                endWeek = maxWeek,
                weekType = weekType,
                selectedWeeks = weeksToSave,
                lastModified = System.currentTimeMillis()
            )
            onCourseUpdated(updatedCourse)
        }
    }

    // 周次范围标题（对齐课程详情弹窗的 getWeekText 格式）
    val weekTitle = if (selectedWeeks.isNotEmpty()) {
        val sorted = selectedWeeks.sorted()
        course.copy(
            selectedWeeks = sorted,
            startWeek = sorted.first(),
            endWeek = sorted.last()
        ).getWeekText()
    } else {
        course.getWeekText()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = (-16).dp)
    ) {
        SmallTitle(text = weekTitle)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
    // 基本信息卡片
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "地点",
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                BasicTextField(
                    value = classroom,
                    onValueChange = { classroom = it },
                    modifier = Modifier.fillMaxWidth(0.65f),
                    singleLine = true,
                    textStyle = TextStyle(
                        textAlign = TextAlign.End,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (classroom.isEmpty()) {
                                Text(
                                    text = "非必填",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "教师",
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                BasicTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    modifier = Modifier.fillMaxWidth(0.65f),
                    singleLine = true,
                    textStyle = TextStyle(
                        textAlign = TextAlign.End,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (teacher.isEmpty()) {
                                Text(
                                    text = "非必填",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }

    // 详细设置卡片 - 上课星期
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "上课星期",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
                        for (day in 1..7) {
                            val isSelected = day == dayOfWeek
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp),
                                cornerRadius = 10.dp,
                                insideMargin = PaddingValues(0.dp),
                                pressFeedbackType = PressFeedbackType.Sink,
                                colors = CardDefaults.defaultColors(
                                    color = if (isSelected) MiuixTheme.colorScheme.primary
                                    else if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7),
                                    contentColor = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary
                                ),
                                onClick = { dayOfWeek = day }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayLabels[day - 1],
                                        fontSize = 14.sp,
                                        color = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 节次范围（点击弹出 NumberPicker）
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "上课节次",
                    endActions = {
                        Text(
                            text = "第${startSection} - ${endSection}节",
                            fontSize = 14.5.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    },
                    onClick = {
                        tempStartSection = startSection
                        tempEndSection = endSection
                        showSectionDialog = true
                    },
                    holdDownState = showSectionDialog
                )
            }
        }
    }

    // 周次设置（含全部/单周/双周复选框和周次网格）
    Column(modifier = Modifier.fillMaxWidth()) {
        val hasMixedSelection = someSelectableOddSelected && someSelectableEvenSelected

        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "上课周次",
                        modifier = Modifier.weight(1f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 全部
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (allSelectableSelected) {
                                    selectedWeeks.clear()
                                } else {
                                    selectedWeeks.clear()
                                    selectedWeeks.addAll(selectableWeeks)
                                }
                            }
                        ) {
                            Checkbox(
                                state = if (allSelectableSelected) ToggleableState.On else ToggleableState.Off,
                                onClick = {
                                    if (allSelectableSelected) {
                                        selectedWeeks.clear()
                                    } else {
                                        selectedWeeks.clear()
                                        selectedWeeks.addAll(selectableWeeks)
                                    }
                                },
                                colors = CheckboxDefaults.checkboxColors(
                                    uncheckedBackgroundColor = if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7)
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "全部", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }

                        // 单周
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (hasMixedSelection) {
                                    selectedWeeks.clear()
                                    selectedWeeks.addAll(selectableOddWeeks)
                                } else if (allSelectableOddSelected) {
                                    selectedWeeks.clear()
                                } else {
                                    selectedWeeks.clear()
                                    selectedWeeks.addAll(selectableOddWeeks)
                                }
                            }
                        ) {
                            Checkbox(
                                state = when {
                                    hasMixedSelection -> ToggleableState.Off
                                    allSelectableOddSelected && !hasOccupiedOddWeeks -> ToggleableState.On
                                    someSelectableOddSelected -> ToggleableState.Indeterminate
                                    else -> ToggleableState.Off
                                },
                                onClick = {
                                    if (hasMixedSelection) {
                                        selectedWeeks.clear()
                                        selectedWeeks.addAll(selectableOddWeeks)
                                    } else if (allSelectableOddSelected) {
                                        selectedWeeks.clear()
                                    } else {
                                        selectedWeeks.clear()
                                        selectedWeeks.addAll(selectableOddWeeks)
                                    }
                                },
                                colors = CheckboxDefaults.checkboxColors(
                                    uncheckedBackgroundColor = if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7)
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "单周", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }

                        // 双周
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (hasMixedSelection) {
                                    selectedWeeks.clear()
                                    selectedWeeks.addAll(selectableEvenWeeks)
                                } else if (allSelectableEvenSelected) {
                                    selectedWeeks.clear()
                                } else {
                                    selectedWeeks.clear()
                                    selectedWeeks.addAll(selectableEvenWeeks)
                                }
                            }
                        ) {
                            Checkbox(
                                state = when {
                                    hasMixedSelection -> ToggleableState.Off
                                    allSelectableEvenSelected && !hasOccupiedEvenWeeks -> ToggleableState.On
                                    someSelectableEvenSelected -> ToggleableState.Indeterminate
                                    else -> ToggleableState.Off
                                },
                                onClick = {
                                    if (hasMixedSelection) {
                                        selectedWeeks.clear()
                                        selectedWeeks.addAll(selectableEvenWeeks)
                                    } else if (allSelectableEvenSelected) {
                                        selectedWeeks.clear()
                                    } else {
                                        selectedWeeks.clear()
                                        selectedWeeks.addAll(selectableEvenWeeks)
                                    }
                                },
                                colors = CheckboxDefaults.checkboxColors(
                                    uncheckedBackgroundColor = if (isDark) Color(0xFF505050) else Color(0xFFF7F7F7)
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "双周", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }

                // 周次网格
                val columns = 6
                val rows = (totalWeeks + columns - 1) / columns
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (row in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (col in 0 until columns) {
                                val weekNum = row * columns + col + 1
                                if (weekNum <= totalWeeks) {
                                    val isSelected = weekNum in selectedWeeks
                                    val isOccupied = weekNum in currentOccupiedWeeks
                                    val primaryColor = MiuixTheme.colorScheme.primary
                                    val outlineColor = MiuixTheme.colorScheme.outline
                                    val onSurfaceColor = MiuixTheme.colorScheme.onSurface
                                    val onSurfaceSummaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    val cardColor = when {
                                        isSelected -> primaryColor
                                        isOccupied -> if (isDark) Color(0xFF4A4A4A) else Color(0xFFF0F0F0)
                                        isDark -> Color(0xFF505050)
                                        else -> Color(0xFFF7F7F7)
                                    }
                                    val cardContentColor = when {
                                        isSelected -> Color.White
                                        isOccupied -> outlineColor
                                        else -> onSurfaceColor
                                    }
                                    val textColor = when {
                                        isSelected -> Color.White
                                        isOccupied -> outlineColor
                                        else -> onSurfaceSummaryColor
                                    }
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp),
                                        cornerRadius = 10.dp,
                                        insideMargin = PaddingValues(0.dp),
                                        pressFeedbackType = PressFeedbackType.Sink,
                                        showIndication = !isOccupied,
                                        colors = CardDefaults.defaultColors(
                                            color = cardColor,
                                            contentColor = cardContentColor
                                        ),
                                        onClick = {
                                            if (!isOccupied) {
                                                if (isSelected) {
                                                    selectedWeeks.remove(weekNum)
                                                } else {
                                                    selectedWeeks.add(weekNum)
                                                }
                                            }
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$weekNum",
                                                fontSize = 13.sp,
                                                color = textColor
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (group.courses.size > 1) {
        Text(
            text = "包含 ${group.courses.size} 个相同配置的课程",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 32.dp, top = 4.dp)
        )
    }

    } // Column spacing

    // 节次选择弹窗
    OverlayDialog(
        title = "选择上课节次",
        show = showSectionDialog,
        onDismissRequest = { showSectionDialog = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LaunchedEffect(tempStartSection) {
                if (tempEndSection < tempStartSection) {
                    tempEndSection = tempStartSection
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "开始",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    NumberPicker(
                        value = tempStartSection,
                        onValueChange = { tempStartSection = it },
                        range = 1..totalSections,
                        visibleItemCount = 3,
                        itemHeight = 50.dp
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "结束",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    NumberPicker(
                        value = tempEndSection,
                        onValueChange = { tempEndSection = it },
                        range = tempStartSection..totalSections,
                        visibleItemCount = 3,
                        itemHeight = 50.dp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showSectionDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (tempStartSection <= tempEndSection) {
                            startSection = tempStartSection
                            endSection = tempEndSection
                        }
                        showSectionDialog = false
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


