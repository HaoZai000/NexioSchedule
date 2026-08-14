/** 课程编辑页面 - 修改课程时段/周次 */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.OverlayDialog
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.components.AddEditCourseBottomSheet
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeFifthpowerOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuadraticOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
        val radiusPx = when {
            s.progress >= 1f -> 0f
            s.progress <= 0.7f -> startCornerRadiusPx + (screenCornerRadiusPx - startCornerRadiusPx) * (s.progress / 0.7f)
            else -> screenCornerRadiusPx
        }
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

@SuppressLint("ConfigurationScreenWidthHeight")
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
    onBackStart: () -> Unit,
    onBack: () -> Unit,
    onCourseUpdated: (Course) -> Unit = { _ -> },
    onCourseAdded: (Course) -> Unit = { _ -> },
    onDeleteCourse: (String) -> Unit = { _ -> },
    onColorChanged: (Long) -> Unit = { _ -> },
    getOccupiedWeeks: (dayOfWeek: Int, startSection: Int, endSection: Int, excludeIds: List<String>) -> Set<Int> = { _, _, _, _ -> emptySet() },
    liquidGlassBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null
) {
    val courseName = courses.firstOrNull()?.name ?: ""
    // 课程颜色状态（所有同名课程共享，仅保存时生效）
    var selectedColor by remember {
        mutableLongStateOf(
            courses.firstOrNull()?.colorRes ?: Course.courseColors.first()
        )
    }
    var showColorDialog by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(Color(selectedColor)) }

    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 16.dp

    val density = LocalDensity.current
    val animProgress = remember { Animatable(0f) }
    val animTransY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val startCornerRadiusPx = 16f * density.density
    val morphOpenEase = OobeQuartOutEasing
    val morphExitEase = OobeCubicOutEasing
    // translationY 独立曲线，时长根据起始卡片位置决定
    val isUpperHalf = cardTop < screenHeight / 2f
    val transOpenEase = OobeFifthpowerOutEasing
    val transExitEase = OobeQuadraticOutEasing
    val transOpenMillis = if (isUpperHalf) 500 else 500
    val transExitMillis = if (isUpperHalf) 320 else 320

    // ---- Back navigation with exit animation ----
    BackHandler {
        onBackStart()
        scope.launch {
            coroutineScope {
                launch {
                    animProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 350,
                            easing = morphExitEase
                        )
                    )
                }
                launch {
                    animTransY.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = transExitMillis,
                            easing = transExitEase
                        )
                    )
                }
            }
            onBack()
        }
    }

    // ---- Enter animation ----
    LaunchedEffect(Unit) {
        // 等待首帧渲染完成后再开始动画
        delay(12.milliseconds)
        launch {
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 560,
                    easing = morphOpenEase
                )
            )
        }
        launch {
            animTransY.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = transOpenMillis,
                    easing = transOpenEase
                )
            )
        }
    }

    // ---- Derived animation state ----
    // graphicsLayer.scale 同时缩放宽高，clipBottom 需要反向补偿
    // 使得 scale * clipBottom 在 p=0 时等于 cardHeight
    val animState = remember {
        derivedStateOf {
            val p = animProgress.value
            val ty = animTransY.value
            val bgAlpha = (p * 0.5f).coerceIn(0f, 0.5f)
            val snapAlpha = (1f - p * 3f).coerceIn(0f, 1f)
            val contAlpha = ((p - 0.1f) / 0.5f).coerceIn(0f, 1f)
            val scale = cardWidth / screenWidth + (1f - cardWidth / screenWidth) * p
            // 起点 = cardCenter, 终点 = screenCenter
            val cardCenter = cardTop + cardHeight / 2f
            val screenCenter = screenHeight / 2f
            // 抛物线插值因子：ty 落后于 p → 前快后慢的曲线
            val curveT = ty  // 直接用 ty 作为曲线参数
            val targetCenter = cardCenter + (screenCenter - cardCenter) * curveT
            // 从 targetCenter 反推 translationY
            val translationY =
                targetCenter - screenHeight / 2f * (1f - scale) - (cardHeight + (screenHeight - cardHeight) * p) / 2f
            // translationX 保持不变
            val translationX = cardLeft * (1f - p) - screenWidth / 2f * (1f - scale)
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
    val scrollBehavior = rememberSharedScrollBehavior()

    // 删除动画状态
    var deletingGroupId by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingDeleteGroup by remember { mutableStateOf<CourseGroup?>(null) }
    var pendingDeleteCourseIds by remember { mutableStateOf<List<String>>(emptyList()) }

    // 添加课程弹窗状态
    var showAddCourseSheet by remember { mutableStateOf(false) }
    // 编辑课程弹窗状态
    var showEditCourseSheet by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<CourseGroup?>(null) }
    // 待添加课程（弹窗关闭后再添加，触发淡入动画）
    var pendingAddCourse by remember { mutableStateOf<Course?>(null) }

    // 动画结束后执行删除
    LaunchedEffect(deletingGroupId) {
        val courseIds = pendingDeleteCourseIds
        if (deletingGroupId != null && courseIds.isNotEmpty()) {
            delay(300.milliseconds) // 等 shrinkVertically + fadeOut 动画完成
            courseIds.forEach { onDeleteCourse(it) }
            pendingDeleteCourseIds = emptyList()
            deletingGroupId = null
        }
    }

    // 全部课程删除后自动退出编辑页
    var hasTriggeredAutoBack by remember { mutableStateOf(false) }
    LaunchedEffect(courses.size) {
        if (courses.isEmpty() && !hasTriggeredAutoBack && animProgress.value > 0.5f) {
            hasTriggeredAutoBack = true
            delay(400.milliseconds)
            onBackStart()
            coroutineScope {
                launch {
                    animProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(350, easing = morphExitEase)
                    )
                }
                launch {
                    animTransY.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(transExitMillis, easing = transExitEase)
                    )
                }
            }
            onBack()
        }
    }

    // 颜色修改即保存
    LaunchedEffect(selectedColor) {
        if (selectedColor != courses.firstOrNull()?.colorRes) {
            onColorChanged(selectedColor)
            courses.forEach { course ->
                onCourseUpdated(
                    course.copy(
                        colorRes = selectedColor,
                        lastModified = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // 弹窗关闭后再添加课程，触发卡片淡入
    LaunchedEffect(showAddCourseSheet) {
        if (!showAddCourseSheet) {
            pendingAddCourse?.let { course ->
                onCourseAdded(course)
                pendingAddCourse = null
            }
        }
    }

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
        val clipShape = remember {
            EditAnimClipShape(
                screenWidth,
                screenCornerRadius,
                startCornerRadiusPx,
                animState
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = false
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
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

                        var topBarBlurAlpha by remember { mutableStateOf(0f) }
                        ProgressiveBlurTopBar(
                            backdrop = liquidGlassBackdrop!!,
                            blurAlpha = topBarBlurAlpha,
                        ) {
                            CollapsibleTopAppBar(
                                title = courseName,
                                largeTitle = courseName,
                                modifier = Modifier,
                                scrollBehavior = scrollBehavior,
                                contentPadding = {},
                                onAlphaChanged = { bd, _ -> topBarBlurAlpha = bd },
                                startAction = { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = {
                                            onBackStart()
                                            scope.launch {
                                                coroutineScope {
                                                    launch {
                                                        animProgress.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = tween(
                                                                durationMillis = 350,
                                                                easing = morphExitEase
                                                            )
                                                        )
                                                    }
                                                    launch {
                                                        animTransY.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = tween(
                                                                durationMillis = transExitMillis,
                                                                easing = transExitEase
                                                            )
                                                        )
                                                    }
                                                }
                                                onBack()
                                            }
                                        },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.ChevronBackward,
                                        contentDescription = "返回",
                                        iconSize = 25.dp,
                                        iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                },
                                endAction = { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            showAddCourseSheet = true
                                        },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Add,
                                        contentDescription = "添加课程",
                                        iconSize = 24.dp,
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                },
                            )
                        }

                    },
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop)
                            .liquidGlassLayerBackdrop(liquidGlassBackdrop!!)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MiuixTheme.colorScheme.surface),
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

                            val gridState = rememberLazyStaggeredGridState()
                            LaunchedEffect(gridState) {
                                snapshotFlow { gridState.firstVisibleItemScrollOffset }
                                    .collect { offset ->
                                        listScrollY = offset
                                    }
                            }
                            val topBarHeightDp = with(density) {
                                scrollBehavior.currentHeightPx.toDp()
                            }
                            LazyVerticalStaggeredGrid(
                                state = gridState,
                                columns = if (isTablet) StaggeredGridCells.Fixed(2) else StaggeredGridCells.Fixed(
                                    1
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .overScrollVertical()
                                    .scrollEndHaptic(
                                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                                    )
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = PaddingValues(
                                    start = tabletHorizontalPadding,
                                    top = paddingValues.calculateTopPadding() + topBarHeightDp - 74.dp,
                                    end = tabletHorizontalPadding,
                                    bottom = 120.dp
                                ),
                                verticalItemSpacing = 12.dp,
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                // 课程颜色选择器（与添加课程弹窗样式一致）
                                item(key = "color_picker", span = StaggeredGridItemSpan.FullLine) {
                                    val allColors = remember { Course.courseColors }
                                    val colorColumns = if (isTablet) allColors.size + 1 else 6
                                    val totalItems =
                                        remember(allColors) { allColors.size + 1 } // +1 for custom color button
                                    val colorRows = remember(
                                        totalItems,
                                        colorColumns
                                    ) { (totalItems + colorColumns - 1) / colorColumns }
                                    Card(
                                        cornerRadius = 20.dp,
                                        modifier = Modifier.fillMaxWidth(),
                                        insideMargin = PaddingValues(top = 14.dp),
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = "课程颜色",
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(
                                                    start = 16.dp,
                                                    bottom = 10.dp
                                                )
                                            )
                                            for (row in 0 until colorRows) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            start = 12.dp,
                                                            end = 12.dp,
                                                            bottom = 12.dp
                                                        ),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    for (col in 0 until colorColumns) {
                                                        val colorIndex = row * colorColumns + col
                                                        if (colorIndex < allColors.size) {
                                                            val color = allColors[colorIndex]
                                                            val isSelected = color == selectedColor
                                                            var isPressed by remember {
                                                                mutableStateOf(
                                                                    false
                                                                )
                                                            }
                                                            val primaryColor =
                                                                MiuixTheme.colorScheme.primary
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
                                                                        animationSpec = tween(
                                                                            durationMillis = 100
                                                                        )
                                                                    )
                                                                } else {
                                                                    scale.animateTo(
                                                                        targetValue = 1f,
                                                                        animationSpec = tween(
                                                                            durationMillis = 180
                                                                        )
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
                                                                                val event =
                                                                                    awaitPointerEvent()
                                                                                val anyPressed =
                                                                                    event.changes.any { it.pressed }
                                                                                isPressed =
                                                                                    anyPressed
                                                                                if (!anyPressed) {
                                                                                    selectedColor =
                                                                                        color
                                                                                }
                                                                            }
                                                                        }
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .graphicsLayer {
                                                                            alpha = borderAlpha
                                                                        }
                                                                        .clip(RoundedRectangle(12.dp))
                                                                        .background(primaryColor)
                                                                )
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .padding(if (isSelected) 2.dp else 0.dp)
                                                                        .clip(RoundedRectangle(10.dp))
                                                                        .background(
                                                                            if (isDark) Color(
                                                                                0xFF242424
                                                                            ) else Color(0xFFFFFFFF)
                                                                        )
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
                                                                        insideMargin = PaddingValues(
                                                                            0.dp
                                                                        ),
                                                                        colors = CardDefaults.defaultColors(
                                                                            color = Color(color).copy(
                                                                                alpha = if (isDark) 0.22f else 0.16f
                                                                            ),
                                                                            contentColor = Color.White
                                                                        ),
                                                                        onClick = {
                                                                            selectedColor = color
                                                                        }
                                                                    ) {}
                                                                }
                                                            }
                                                        } else if (colorIndex == allColors.size) {
                                                            // 自定义颜色按钮
                                                            val isCustomColor =
                                                                selectedColor !in allColors
                                                            val bgColor =
                                                                if (isDark) Color(0xFF505050) else Color(
                                                                    0xFFF7F7F7
                                                                )
                                                            val hintColor =
                                                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                                                            val primaryColor =
                                                                MiuixTheme.colorScheme.primary
                                                            var isCustomPressed by remember {
                                                                mutableStateOf(
                                                                    false
                                                                )
                                                            }
                                                            val customScale =
                                                                remember { Animatable(1f) }
                                                            val customBorderAlpha by animateFloatAsState(
                                                                targetValue = if (isCustomColor) 1f else 0f,
                                                                animationSpec = tween(durationMillis = 200),
                                                                label = "customBorderAlpha"
                                                            )
                                                            LaunchedEffect(isCustomPressed) {
                                                                if (isCustomPressed) {
                                                                    customScale.animateTo(
                                                                        targetValue = 0.94f,
                                                                        animationSpec = tween(
                                                                            durationMillis = 100
                                                                        )
                                                                    )
                                                                } else {
                                                                    customScale.animateTo(
                                                                        targetValue = 1f,
                                                                        animationSpec = tween(
                                                                            durationMillis = 180
                                                                        )
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
                                                                                val event =
                                                                                    awaitPointerEvent()
                                                                                val anyPressed =
                                                                                    event.changes.any { it.pressed }
                                                                                isCustomPressed =
                                                                                    anyPressed
                                                                                if (!anyPressed) {
                                                                                    customColor =
                                                                                        Color(
                                                                                            selectedColor
                                                                                        )
                                                                                    showColorDialog =
                                                                                        true
                                                                                }
                                                                            }
                                                                        }
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .graphicsLayer {
                                                                            alpha =
                                                                                customBorderAlpha
                                                                        }
                                                                        .clip(RoundedRectangle(12.dp))
                                                                        .background(primaryColor)
                                                                )
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .padding(if (isCustomColor) 2.dp else 0.dp)
                                                                        .clip(RoundedRectangle(10.dp))
                                                                        .background(
                                                                            if (isDark) Color(
                                                                                0xFF242424
                                                                            ) else Color(0xFFFFFFFF)
                                                                        )
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
                                                                        insideMargin = PaddingValues(
                                                                            0.dp
                                                                        ),
                                                                        colors = CardDefaults.defaultColors(
                                                                            color = bgColor,
                                                                            contentColor = hintColor
                                                                        ),
                                                                        onClick = {
                                                                            customColor =
                                                                                Color(selectedColor)
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
                                                                                        .fillMaxSize(
                                                                                            0.7f
                                                                                        )
                                                                                        .clip(
                                                                                            RoundedRectangle(
                                                                                                4.dp
                                                                                            )
                                                                                        )
                                                                                        .background(
                                                                                            Color(
                                                                                                selectedColor
                                                                                            ).copy(
                                                                                                alpha = if (isDark) 0.22f else 0.16f
                                                                                            )
                                                                                        )
                                                                                )
                                                                            } else {
                                                                                Icon(
                                                                                    imageVector = MiuixIcons.Add,
                                                                                    contentDescription = "自定义颜色",
                                                                                    modifier = Modifier.size(
                                                                                        18.dp
                                                                                    ),
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
                                    val groupKey =
                                        "${group.key.dayOfWeek}_${group.key.startSection}_${group.key.startWeek}"
                                    val isDeleting = deletingGroupId == groupKey

                                    AnimatedVisibility(
                                        visible = !isDeleting,
                                        exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                                    ) {
                                        val cardAlpha = remember { Animatable(0f) }
                                        val cardScale = remember { Animatable(0.8f) }
                                        LaunchedEffect(Unit) {
                                            launch {
                                                cardAlpha.animateTo(1f, tween(400))
                                            }
                                            launch {
                                                cardScale.animateTo(
                                                    1f,
                                                    tween(400, easing = OobeQuartOutEasing)
                                                )
                                            }
                                        }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .graphicsLayer {
                                                    alpha = cardAlpha.value
                                                    scaleX = cardScale.value
                                                    scaleY = cardScale.value
                                                }
                                        ) {
                                            CourseGroupCard(
                                                group = group,
                                                onEdit = { g ->
                                                    editingGroup = g
                                                    showEditCourseSheet = true
                                                }
                                            )
                                            // 删除按钮
                                            Button(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 12.dp)
                                                    .height(50.dp),
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.Confirm
                                                    )
                                                    pendingDeleteGroup = group
                                                    showDeleteDialog = true
                                                },
                                                colors = if (isDark) ButtonDefaults.buttonColors(
                                                    color = Color(0xFF2A2A2A)
                                                )
                                                else ButtonDefaults.buttonColors(),
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Delete,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = Color(0xFFF44336)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "删除",
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFFF44336)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 自定义颜色选择弹窗
                    OverlayDialog(
                        title = "选择颜色",
                        show = showColorDialog,
                        liquidGlassBackdrop = liquidGlassBackdrop,
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
                                        selectedColor =
                                            (customColor.alpha * 255).toInt().toLong() shl 24 or
                                                    ((customColor.red * 255).toInt()
                                                        .toLong() shl 16) or
                                                    ((customColor.green * 255).toInt()
                                                        .toLong() shl 8) or
                                                    (customColor.blue * 255).toInt().toLong()
                                        showColorDialog = false
                                    },
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 删除确认弹窗
                    OverlayDialog(
                        title = "删除课程",
                        summary = "确定要删除课程「${pendingDeleteGroup?.courses?.firstOrNull()?.name ?: ""}」吗？\n此操作不可撤销。",
                        show = showDeleteDialog,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        onDismissRequest = { showDeleteDialog = false }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    showDeleteDialog = false
                                },
                            ) {
                                Text(
                                    "取消",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    showDeleteDialog = false
                                    pendingDeleteGroup?.let { group ->
                                        pendingDeleteCourseIds = group.courses.map { it.id }
                                        deletingGroupId =
                                            "${group.key.dayOfWeek}_${group.key.startSection}_${group.key.startWeek}"
                                    }
                                },
                            ) {
                                Text(
                                    "删除",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFF44336)
                                )
                            }
                        }
                    }

                    // 添加课程底部弹窗
                    AddEditCourseBottomSheet(
                        show = showAddCourseSheet,
                        courses = courses,
                        backdrop = backdrop,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        onDismissRequest = { showAddCourseSheet = false },
                        onConfirm = { newCourse ->
                            pendingAddCourse = newCourse
                        },
                        getOccupiedWeeks = { dow, ss, es, excludeIds ->
                            getOccupiedWeeks(dow, ss, es, excludeIds)
                        }
                    )

                    // 编辑课程底部弹窗
                    AddEditCourseBottomSheet(
                        show = showEditCourseSheet,
                        courses = editingGroup?.courses ?: emptyList(),
                        backdrop = backdrop,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        editCourse = editingGroup?.courses?.first(),
                        onDismissRequest = {
                            showEditCourseSheet = false
                            editingGroup = null
                        },
                        onConfirm = { updatedCourse ->
                            editingGroup?.courses?.forEach { old ->
                                onCourseUpdated(
                                    old.copy(
                                        classroom = updatedCourse.classroom,
                                        teacher = updatedCourse.teacher,
                                        dayOfWeek = updatedCourse.dayOfWeek,
                                        startSection = updatedCourse.startSection,
                                        endSection = updatedCourse.endSection,
                                        startWeek = updatedCourse.startWeek,
                                        endWeek = updatedCourse.endWeek,
                                        weekType = updatedCourse.weekType,
                                        selectedWeeks = updatedCourse.selectedWeeks,
                                        lastModified = System.currentTimeMillis()
                                    )
                                )
                            }
                            showEditCourseSheet = false
                            editingGroup = null
                        },
                        getOccupiedWeeks = { dow, ss, es, excludeIds ->
                            getOccupiedWeeks(dow, ss, es, excludeIds)
                        }
                    )
                }
            }
        }
    }
}

// ===================== Course Group Card =====================

@Composable
private fun CourseGroupCard(
    group: CourseGroup,
    onEdit: (CourseGroup) -> Unit
) {
    val course = group.courses.first()
    val dayLabels = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val weekText = course.getWeekText().ifEmpty { "未设置" }
    val sectionText = course.getSectionText().ifEmpty { "未设置" }

    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(
            text = weekText,
            modifier = Modifier.offset(x = (-16).dp)
        )

        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onEdit(group) }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "地点",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = course.classroom.ifBlank { "未设置" },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        textAlign = TextAlign.End
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "教师",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = course.teacher.ifBlank { "未设置" },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        textAlign = TextAlign.End
                    )
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(0.5.dp)
                        .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.07f))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "上课时间",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (course.dayOfWeek > 0) "${dayLabels[course.dayOfWeek]} $sectionText" else sectionText,
                        modifier = Modifier.fillMaxWidth(0.8f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        textAlign = TextAlign.End
                    )
                }


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "上课周次",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = weekText,
                        modifier = Modifier.fillMaxWidth(0.8f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        textAlign = TextAlign.End
                    )
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
    }
}

// ===================== Course Group Card + Delete (Tablet) =====================


