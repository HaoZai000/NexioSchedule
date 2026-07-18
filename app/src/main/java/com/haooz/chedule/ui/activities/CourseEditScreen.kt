/** 课程编辑页面 - 修改课程时段/周次 */
package com.haooz.chedule.ui.activities

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.DpSize
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
                            val listState = rememberLazyListState()

                            // Track scroll state for top bar blur effect
                            LaunchedEffect(listState) {
                                snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                                    .collect { (index, offset) ->
                                        listScrollY = index * 100 + offset
                                    }
                            }

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
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = courseGroups,
                                    key = { "${it.key.dayOfWeek}_${it.key.startSection}_${it.key.startWeek}" }
                                ) { group ->
                                    CourseGroupCard(
                                        group = group,
                                        sectionTimes = sectionTimes,
                                        onCourseUpdated = onCourseUpdated,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }

                                // Bottom spacing for navigation bar
                                item {
                                    Spacer(
                                        modifier = Modifier.padding(bottom = 80.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== CourseGroupCard =====================

@Composable
private fun CourseGroupCard(
    group: CourseGroup,
    sectionTimes: Map<Int, String>,
    onCourseUpdated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val key = group.key
    val course = group.courses.first()
    val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val dayName = dayNames.getOrElse(key.dayOfWeek) { "未知" }

    val sectionRange = if (key.startSection == key.endSection) {
        "第${key.startSection}节"
    } else {
        "第${key.startSection}-${key.endSection}节"
    }

    val weekRange = when (key.weekType) {
        1 -> "第${key.startWeek}-${key.endWeek}周"
        2 -> {
            val weeksStr = key.selectedWeeks.sorted().joinToString(", ")
            if (weeksStr.isNotEmpty()) "第${weeksStr}周" else "自定义周次"
        }
        else -> "第${key.startWeek}-${key.endWeek}周"
    }

    val timeRange = buildString {
        val startTime = sectionTimes[key.startSection]
        val endTime = sectionTimes[key.endSection]?.let {
            sectionTimes[key.endSection]
        }
        if (startTime != null && endTime != null) {
            append("$startTime - $endTime")
        }
    }

    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface,
            contentColor = MiuixTheme.colorScheme.onSurface
        ),
        showIndication = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Day + Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$dayName $sectionRange",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                if (timeRange.isNotEmpty()) {
                    Text(
                        text = timeRange,
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Week info
            Text(
                text = weekRange,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            // Course names in this group
            if (group.courses.size > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "包含 ${group.courses.size} 个相同配置的课程",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Edit button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedRectangle(20.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .clickable { showEditDialog = true }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "编辑",
                    style = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
    }

    if (showEditDialog) {
        CourseEditDialog(
            course = course,
            onDismiss = { showEditDialog = false },
            onConfirm = { updatedCourse ->
                showEditDialog = false
                onCourseUpdated()
            }
        )
    }
}

// ===================== Course Edit Dialog =====================

@Composable
private fun CourseEditDialog(
    course: Course,
    onDismiss: () -> Unit,
    onConfirm: (Course) -> Unit
) {
    val isDark = isAppDarkTheme()
    var showSheet by remember { mutableStateOf(true) }

    var name by remember { mutableStateOf(course.name) }
    var classroom by remember { mutableStateOf(course.classroom) }
    var teacher by remember { mutableStateOf(course.teacher) }
    var dayOfWeek by remember { mutableIntStateOf(course.dayOfWeek) }
    var startSection by remember { mutableIntStateOf(course.startSection) }
    var endSection by remember { mutableIntStateOf(course.endSection) }
    var startWeek by remember { mutableIntStateOf(course.startWeek) }
    var endWeek by remember { mutableIntStateOf(course.endWeek) }

    top.yukonga.miuix.kmp.overlay.OverlayBottomSheet(
        show = showSheet,
        title = "编辑课程",
        startAction = {
            top.yukonga.miuix.kmp.basic.IconButton(
                onClick = {
                    showSheet = false
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = MiuixIcons.Normal.Close,
                    contentDescription = "取消",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        endAction = {
            top.yukonga.miuix.kmp.basic.IconButton(
                onClick = {
                    if (name.isNotBlank() && startSection <= endSection && startWeek <= endWeek) {
                        val updatedCourse = course.copy(
                            name = name.trim(),
                            classroom = classroom.trim(),
                            teacher = teacher.trim(),
                            dayOfWeek = dayOfWeek,
                            startSection = startSection,
                            endSection = endSection,
                            startWeek = startWeek,
                            endWeek = endWeek,
                            lastModified = System.currentTimeMillis()
                        )
                        onConfirm(updatedCourse)
                        showSheet = false
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = "确认",
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        onDismissRequest = { showSheet = false },
        onDismissFinished = onDismiss,
        backgroundColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF7F7F7),
        cornerRadius = 36.dp,
        insideMargin = DpSize(0.dp, 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Basic info card
            top.yukonga.miuix.kmp.basic.Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                insideMargin = PaddingValues(horizontal = 16.dp),
                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                    color = if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF),
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Course name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "课程名称",
                            modifier = Modifier.weight(1f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        top.yukonga.miuix.kmp.basic.TextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(0.65f),
                            singleLine = true
                        )
                    }

                    // Classroom
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
                        top.yukonga.miuix.kmp.basic.TextField(
                            value = classroom,
                            onValueChange = { classroom = it },
                            modifier = Modifier.fillMaxWidth(0.65f),
                            singleLine = true
                        )
                    }

                    // Teacher
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
                        top.yukonga.miuix.kmp.basic.TextField(
                            value = teacher,
                            onValueChange = { teacher = it },
                            modifier = Modifier.fillMaxWidth(0.65f),
                            singleLine = true
                        )
                    }
                }
            }

            // Day of week selection
            top.yukonga.miuix.kmp.basic.Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                    color = if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF),
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "上课星期",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
                        for (day in 1..7) {
                            val isSelected = day == dayOfWeek
                            top.yukonga.miuix.kmp.basic.Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp),
                                cornerRadius = 10.dp,
                                insideMargin = PaddingValues(0.dp),
                                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
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

            // Section range
            top.yukonga.miuix.kmp.basic.Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                    color = if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF),
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "上课节次",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "第${startSection}节",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "至",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                        Text(
                            text = "第${endSection}节",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = "-",
                                onClick = {
                                    if (startSection > 1) {
                                        startSection--
                                        if (endSection < startSection) endSection = startSection
                                    }
                                }
                            )
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = "+",
                                onClick = {
                                    if (endSection < 12) endSection++
                                }
                            )
                        }
                    }
                }
            }

            // Week range
            top.yukonga.miuix.kmp.basic.Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                    color = if (isDark) Color(0xFF363636) else Color(0xFFFFFFFF),
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "上课周次",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "第${startWeek}周",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "至",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                        Text(
                            text = "第${endWeek}周",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = "-",
                                onClick = {
                                    if (startWeek > 1) {
                                        startWeek--
                                        if (endWeek < startWeek) endWeek = startWeek
                                    }
                                }
                            )
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = "+",
                                onClick = {
                                    if (endWeek < 30) endWeek++
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
