/** 课程详情页面 */
package com.haooz.chedule.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.activities.isAppDarkTheme
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.Color as ComposeColor

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
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val s = animState.value
        // 动画结束后圆角归零
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

@Composable
fun CourseDetailScreen(
    courses: List<Course>,
    cardLeft: Float,
    cardTop: Float,
    cardWidth: Float,
    cardHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
    screenCornerRadius: Float,
    cardSnapshot: Bitmap?,
    fromToday: Boolean = false,
    sectionTimes: Map<Int, String>,
    classStartTime: String,
    onBackStart: () -> Unit,
    onBack: () -> Unit
) {
    val courseName = courses.firstOrNull()?.name ?: ""
    // 按周数排序，最大排在最上
    val sortedCourses = courses.sortedByDescending { it.endWeek }

    // 计算开学日期的周一
    val startMonday = remember(classStartTime) {
        try {
            val startDate = java.time.LocalDate.parse(classStartTime.replace("/", "-"))
            startDate.minusDays((startDate.dayOfWeek.value - 1).toLong())
        } catch (_: Exception) {
            java.time.LocalDate.now()
        }
    }

    val density = LocalDensity.current
    val animProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val startCornerRadiusPx = 20f * density.density
    val morphOpenEase = CubicBezierEasing(0.3f, 0.72f, 0.2f, 1.0f)
    val morphExitEase = CubicBezierEasing(0.3f, 0.65f, 0.35f, 1.0f)

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

    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 520,
                easing = morphOpenEase
            )
        )
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) ComposeColor(0xFF2C2C2C).copy(alpha = animState.value.bgAlpha)
                else ComposeColor.Black.copy(alpha = animState.value.bgAlpha)
            )
            .pointerInput(Unit) {
                // Block touch events without the overhead of clickable
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
                .background(if (fromToday) MiuixTheme.colorScheme.background else if (isDark) ComposeColor(0xFF363636) else ComposeColor(0xFFFFFFFF))
        ) {
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

            if (s.contentAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = s.contentAlpha }
                ) {
                    Scaffold(
                        topBar = {
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
                                                    durationMillis = 400,
                                                    easing = morphExitEase
                                                )
                                            )
                                            onBack()
                                        }
                                    },
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Back,
                                            contentDescription = "返回",
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            )
                        }
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(backdrop)
                        ) {
                            val listState = rememberLazyListState()
                            LaunchedEffect(listState) {
                                snapshotFlow { listState.firstVisibleItemScrollOffset }
                                    .collect { offset ->
                                        listScrollY = offset
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
                                    top = paddingValues.calculateTopPadding(),
                                    end = 16.dp,
                                    bottom = 120.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 按周分，每周单独显示，保持课程排序顺序
                                val weekEntries = sortedCourses.flatMap { course ->
                                    val weeks = course.selectedWeeks.ifEmpty {
                                        (course.startWeek..course.endWeek).filter { week ->
                                            when (course.weekType) {
                                                Course.WEEK_TYPE_ODD -> week % 2 == 1
                                                Course.WEEK_TYPE_EVEN -> week % 2 == 0
                                                else -> true
                                            }
                                        }
                                    }
                                    weeks.map { week -> week to course }
                                }.sortedByDescending { it.first }

                                // 按周分组显示
                                val groupedByWeek = weekEntries.groupBy { it.first }

                                groupedByWeek.forEach { (week, weekCourses) ->
                                    item {
                                        Column {
                                            SmallTitle(
                                                text = "第${week}周",
                                                modifier = Modifier.offset(x = (-15).dp)
                                            )
                                            Card(
                                                cornerRadius = 20.dp,
                                                modifier = Modifier.fillMaxWidth(),
                                                insideMargin = PaddingValues(0.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                                ) {
                                                    weekCourses.forEachIndexed { index, (_, course) ->
                                                        val dayName = when (course.dayOfWeek) {
                                                            1 -> "周一"
                                                            2 -> "周二"
                                                            3 -> "周三"
                                                            4 -> "周四"
                                                            5 -> "周五"
                                                            6 -> "周六"
                                                            7 -> "周日"
                                                            else -> "未知"
                                                        }
                                                        val courseDate = startMonday.plusDays((week - 1).toLong() * 7 + (course.dayOfWeek - 1).toLong())
                                                        val dateFormat = java.time.format.DateTimeFormatter.ofPattern("M/d")
                                                        val dateStr = courseDate.format(dateFormat)
                                                        val sectionText = "第${course.startSection}-${course.endSection}节"
                                                        val timeStart = sectionTimes[course.startSection]?.split("-")?.firstOrNull() ?: ""
                                                        val timeEnd = sectionTimes[course.endSection]?.split("-")?.lastOrNull() ?: ""
                                                        val timeText = if (timeStart.isNotEmpty() && timeEnd.isNotEmpty()) "$timeStart - $timeEnd" else ""

                                                        if (index > 0) {
                                                            Spacer(modifier = Modifier.height(28.dp))
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = "$dateStr $dayName $sectionText",
                                                                    style = MiuixTheme.textStyles.body1.copy(fontSize = 17.sp),
                                                                    fontWeight = FontWeight.Medium,
                                                                    color = MiuixTheme.colorScheme.onSurface
                                                                )
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                val detailParts = mutableListOf<String>()
                                                                if (course.classroom.isNotEmpty()) detailParts.add(course.classroom)
                                                                if (course.teacher.isNotEmpty()) detailParts.add(course.teacher)
                                                                if (detailParts.isNotEmpty()) {
                                                                    Text(
                                                                        text = detailParts.joinToString(" | "),
                                                                        style = MiuixTheme.textStyles.footnote1,
                                                                        color = MiuixTheme.colorScheme.onBackgroundVariant
                                                                    )
                                                                }
                                                            }
                                                            if (timeText.isNotEmpty()) {
                                                                Column(horizontalAlignment = Alignment.End) {
                                                                    Text(
                                                                        text = timeText,
                                                                        style = MiuixTheme.textStyles.footnote1.copy(fontSize = 15.sp),
                                                                        color = MiuixTheme.colorScheme.primary
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
                            }
                        }
                    }
                }
            }
        }
    }
}