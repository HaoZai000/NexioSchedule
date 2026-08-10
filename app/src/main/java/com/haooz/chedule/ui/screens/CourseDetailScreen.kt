/** 课程详情页面 */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.components.CollapsibleTopAppBar
import com.haooz.chedule.ui.components.rememberSharedScrollBehavior
import com.haooz.chedule.ui.effects.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.effects.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeFifthpowerOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuadraticOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

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

@SuppressLint("ConfigurationScreenWidthHeight")
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
    onBack: () -> Unit,
) {
    val courseName = courses.firstOrNull()?.name ?: ""
    // 按周数排序，最大排在最上
    val sortedCourses = remember(courses) { courses.sortedByDescending { it.endWeek } }

    // 预计算周分组数据，避免在 LazyColumn 内重复计算
    val groupedByWeek = remember(sortedCourses) {
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
        weekEntries.groupBy { it.first }
    }

    val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 16.dp

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
    val animTransY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val startCornerRadiusPx = 20f * density.density
    val morphOpenEase = OobeQuartOutEasing
    val morphExitEase = OobeCubicOutEasing
    val isUpperHalf = cardTop < screenHeight / 2f
    val transOpenEase = OobeFifthpowerOutEasing
    val transExitEase = OobeQuadraticOutEasing
    val transOpenMillis = if (isUpperHalf) 500 else 500
    val transExitMillis = if (isUpperHalf) 320 else 320

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
            val translationY = targetCenter - screenHeight / 2f * (1f - scale) - (cardHeight + (screenHeight - cardHeight) * p) / 2f
            // translationX 保持不变
            val translationX = cardLeft * (1f - p) - screenWidth / 2f * (1f - scale)
            val rawClipBottom = cardHeight + (screenHeight - cardHeight) * p
            val clipBottom = rawClipBottom / scale
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
    val scrollBehavior = rememberSharedScrollBehavior()

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
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    scaleX = s.scale
                    scaleY = s.scale
                    translationX = s.translationX
                    translationY = s.translationY
                }
                .clip(clipShape)
                .background(if (fromToday) MiuixTheme.colorScheme.background else if (isDark) ComposeColor(0xFF303030) else ComposeColor(0xFFF8F8F8))
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = s.contentAlpha }
            ) {
                Scaffold(
                    topBar = {
                        ProgressiveBlurTopBar(
                            backdrop = liquidGlassBackdrop!!,
                        ) {
                            CollapsibleTopAppBar(
                                title = courseName,
                                largeTitle = courseName,
                                modifier = Modifier,
                                scrollBehavior = scrollBehavior,
                                contentPadding = {},
                                startAction = { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
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
                            )
                        }
                    },
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(backdrop)
                                .then(
                                    Modifier.liquidGlassLayerBackdrop(
                                        liquidGlassBackdrop
                                    )
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
                                    contentColor = MiuixTheme.colorScheme.onSurface)
                            ) {
                                val topBarHeightDp = with(density) {
                                    scrollBehavior.currentHeightPx.toDp()
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
                                        start = tabletHorizontalPadding,
                                        top = paddingValues.calculateTopPadding() + topBarHeightDp - 82.dp,
                                        end = tabletHorizontalPadding,
                                        bottom = 120.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
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
