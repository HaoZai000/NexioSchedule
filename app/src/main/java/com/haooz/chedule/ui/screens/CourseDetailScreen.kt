/** 课程详情页面 */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeFifthpowerOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuadraticOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.utils.blockTouchPassThrough
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

private val DATE_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("M/d")

private data class AnimState(
    val bgAlpha: Float,
    val snapshotAlpha: Float,
    val contentAlpha: Float,
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val clipBottom: Float,
    val progress: Float,
    val gesture: Float
)

private class AnimClipShape(
    private val screenWidth: Float,
    private val screenCornerRadiusPx: Float,
    private val startCornerRadiusPx: Float,
    private val animState: androidx.compose.runtime.State<AnimState>
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val s = animState.value
        // 预测性返回：裁切圆角固定为屏幕圆角（并除以 scale 补偿缩放，视觉圆角恒定）；
        // 其余按 morph 进度插值
        val radiusPx = when {
            s.gesture > 0f -> screenCornerRadiusPx
            s.progress >= 1f -> 0f
            s.progress <= 0.7f -> startCornerRadiusPx + (screenCornerRadiusPx - startCornerRadiusPx) * (s.progress / 0.7f)
            else -> screenCornerRadiusPx
        }
        // 补偿在"预测返回不补偿（×1）"与"正常 morph 除以 scale"之间按 gesture 平滑插值，
        // 避免松手瞬间圆角跳变大
        val compensate = (1f - s.gesture) / s.scale + s.gesture
        val radiusDp = (radiusPx * compensate / density.density).dp
        return ContinuousRoundedRectangle(radiusDp).createOutline(
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
    sectionTimes: Map<Int, String>,
    classStartTime: String,
    // 点击来源所在的周次（今日页=当前浏览日期所在周，bottomsheet=当前查看周），
    // 进入后自动滚动到对应周分组；<=0 表示不滚动。
    targetWeek: Int = 0,
    onBackStart: () -> Unit,
    onBack: () -> Unit,
) {
    val courseName = courses.firstOrNull()?.name ?: ""
    val sortedCourses = remember(courses) { courses.sortedBy { it.startWeek } }

    // 预计算周分组，有序列表便于按索引定位自动滚动
    val weekGroups = remember(sortedCourses) {
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
        }
        // 先按周次升序分组，组内再按星期几、起始节次排序
        weekEntries.groupBy { it.first }.toSortedMap().toList()
            .map { (week, entries) ->
                week to entries.sortedWith(compareBy({ it.second.dayOfWeek }, { it.second.startSection }))
            }
    }

    val liquidGlassBackdrop = rememberLayerBackdrop()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 16.dp

    // 计算开学日期所在周的周一
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
    val startCornerRadiusPx = 20f * density.density
    val morphOpenEase = OobeQuartOutEasing
    val morphExitEase = OobeCubicOutEasing
    val isUpperHalf = cardTop < screenHeight / 2f
    val transOpenEase = OobeFifthpowerOutEasing
    val transExitEase = OobeQuadraticOutEasing
    val transOpenMillis = if (isUpperHalf) 500 else 500
    val transExitMillis = if (isUpperHalf) 320 else 320

    // 预测性返回：手势只驱动缩放位置 scaleProgress（1=全屏，0=卡片），
    // 预测返回期间围绕屏幕中心缩放（translation=0），位移/裁切保持全屏不动；
    // 取消回弹全屏、完成随关闭动画一起缩回卡片；
    // 低版本 NavigationBackHandler 自动退化为立即播放完整退出动画
    val navigationEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    // 手势进度（0..1）：>0 表示预测性返回进行中，用于切换"中心缩放"
    val gestureBackProgress = remember { Animatable(0f) }
    val scaleProgress = remember { Animatable(0f) }
    // 手势是否正在推进：进行中裁切完全跟随页面缩放，松手/取消后进入平滑过渡
    var isGestureActive by remember { mutableStateOf(false) }

    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = true,
        onBackCancelled = {
            isGestureActive = false
            scope.launch {
                if (gestureBackProgress.value > 0f) {
                    // 手势取消：缩放回弹恢复全屏
                    gestureBackProgress.animateTo(0f, animationSpec = tween(180))
                    scaleProgress.animateTo(1f, animationSpec = tween(180))
                }
            }
        },
        onBackCompleted = {
            isGestureActive = false
            onBackStart()
            scope.launch {
                coroutineScope {
                    // 缩放中心从屏幕中心平滑过渡回左上角锚点（150ms），morph 位移随之接管
                    launch { gestureBackProgress.animateTo(0f, animationSpec = tween(150)) }
                    launch {
                        scaleProgress.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = 350,
                                easing = morphExitEase
                            )
                        )
                    }
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
    )

    // 逐帧收集返回手势进度（单独协程，避免手势期间每帧取消/重启 LaunchedEffect）；
    // 手势只把缩放位置退到"卡片→全屏"范围的 75%，位移/裁切由 onBackCompleted 接管
    LaunchedEffect(Unit) {
        snapshotFlow { navigationEventState.transitionState }
            .collect { transitionState ->
                if (
                    transitionState is NavigationEventTransitionState.InProgress &&
                    transitionState.direction == NavigationEventTransitionState.TRANSITIONING_BACK
                ) {
                    isGestureActive = true
                    val progress = transitionState.latestEvent.progress
                    gestureBackProgress.snapTo(progress)
                    scaleProgress.snapTo(1f - progress * 2f)
                }
            }
    }

    LaunchedEffect(Unit) {
        delay(12.milliseconds)
        launch {
            scaleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 560,
                    easing = morphOpenEase
                )
            )
        }
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
            // 预测性返回手势只驱动缩放位置 scaleProgress（1=全屏，0=卡片，手势可退到 -1 即 200% 行程），
            // 位移与裁切保持全屏（p 不变）不随手势变化；coerceAtLeast 防止窄卡片时 scale 变负翻转
            val scale = (cardWidth / screenWidth + (1f - cardWidth / screenWidth) * scaleProgress.value).coerceAtLeast(0.05f)
            // 起点 = cardCenter, 终点 = screenCenter；ty 作为曲线参数，前快后慢
            val cardCenter = cardTop + cardHeight / 2f
            val screenCenter = screenHeight / 2f
            val curveT = ty  // 直接用 ty 作为曲线参数
            val targetCenter = cardCenter + (screenCenter - cardCenter) * curveT
            // 正常 morph 缩放锚点为屏幕顶部居中：y 围绕顶部（原有补偿），x 围绕屏幕中轴
            // （去掉左缘补偿，位移基于卡片原始左缘）；预测返回期间围绕屏幕中心缩放
            val gesture = gestureBackProgress.value
            val normalY = targetCenter - screenHeight / 2f * (1f - scale) - (cardHeight + (screenHeight - cardHeight) * p) / 2f
            val normalX = (cardLeft - screenWidth / 2f * (1f - cardWidth / screenWidth)) * (1f - p)
            val translationY = normalY * (1f - gesture)
            val translationX = normalX * (1f - gesture)
            // 手势推进期间裁切完全跟随页面缩放（视觉 Hs，圆角始终完整贴合）；
            // 松手/取消过渡期按 gesture 平滑插值衔接 morph 的底部收缩动画
            val predictiveClip = screenHeight * scale
            val morphClip = cardHeight + (screenHeight - cardHeight) * p
            val rawClipBottom = if (isGestureActive) predictiveClip else predictiveClip * gesture + morphClip * (1f - gesture)
            val clipBottom = rawClipBottom / scale
            AnimState(bgAlpha, snapAlpha, contAlpha, translationX, translationY, scale, clipBottom, p, gesture)
        }
    }


    val isDark = isAppDarkTheme()
    val scrollBehavior = rememberSharedScrollBehavior()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) ComposeColor(0xFF2C2C2C).copy(alpha = animState.value.bgAlpha)
                else ComposeColor.Black.copy(alpha = animState.value.bgAlpha)
            )
            // 只挡下层穿透、不 consume：consumeAllTouches 会连 LazyColumn 拖动一起吃掉
            .blockTouchPassThrough()
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
                .background(MiuixTheme.colorScheme.background)
        ) {
            if (cardSnapshot != null && s.snapshotAlpha > 0f) {
                Image(
                    bitmap = cardSnapshot.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .clip(ContinuousRoundedRectangle(20.dp))
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
                            backdrop = liquidGlassBackdrop,
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
                                        performHapticFeedback = false,
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
                                .then(
                                    Modifier.liquidGlassLayerBackdrop(
                                        liquidGlassBackdrop
                                    )
                                )
                        ) {
                            val listState = rememberLazyListState()
                            // 程序化滚动不走 nestedScroll：监听 listState 同步 contentOffset 与标题栏
                            var isProgrammaticScroll by remember { mutableStateOf(false) }
                            var lastCollapsed by remember { mutableStateOf(false) }
                            val scrollThresholdPx = with(density) { 10.dp.toPx() }
                            LaunchedEffect(listState) {
                                snapshotFlow {
                                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                                }.collect { (index, offset) ->
                                    val state = scrollBehavior.state
                                    val shouldCollapse = index > 0 || offset > scrollThresholdPx
                                    if (shouldCollapse && state.contentOffset >= -scrollThresholdPx) {
                                        state.contentOffset = -scrollThresholdPx - 1f
                                    } else if (!shouldCollapse && state.contentOffset < 0f) {
                                        state.contentOffset = 0f
                                    }
                                    // 仅程序化滚动时收起/展开标题栏，手动 fling 由 nestedScroll 处理
                                    if (isProgrammaticScroll && shouldCollapse != lastCollapsed) {
                                        lastCollapsed = shouldCollapse
                                        if (shouldCollapse) scrollBehavior.collapse() else scrollBehavior.expand()
                                    } else if (!isProgrammaticScroll) {
                                        lastCollapsed = shouldCollapse
                                    }
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
                                // 列表顶部留白（与下方 contentPadding 一致）
                                val topContentPadding = paddingValues.calculateTopPadding() + topBarHeightDp - 82.dp
                                val latestTopPadding by rememberUpdatedState(topContentPadding)
                                // 进入后连贯滚动到来源周；找不到该周时回退到最接近的一周
                                LaunchedEffect(weekGroups, targetWeek) {
                                    if (targetWeek <= 0 || weekGroups.isEmpty()) return@LaunchedEffect
                                    val exact = weekGroups.indexOfFirst { it.first == targetWeek }
                                    val index = if (exact >= 0) exact else {
                                        var best = -1
                                        var bestDiff = Int.MAX_VALUE
                                        weekGroups.forEachIndexed { i, (week, _) ->
                                            val diff = kotlin.math.abs(week - targetWeek)
                                            if (diff < bestDiff) {
                                                bestDiff = diff
                                                best = i
                                            }
                                        }
                                        best
                                    }
                                    if (index > 0) {
                                        // 等入场形变基本完成再滚，避免内容还没看清就已"瞬移"到位
                                        delay(400.milliseconds)
                                        // 先立即收起标题栏，让 contentPadding 在整段滚动中保持稳定
                                        val barState = scrollBehavior.state
                                        val wasExpanded =
                                            barState.heightOffsetLimit < -1f &&
                                            barState.heightOffset > barState.heightOffsetLimit
                                        if (wasExpanded) {
                                            scrollBehavior.collapseImmediately()
                                            lastCollapsed = true
                                            if (barState.contentOffset >= -scrollThresholdPx) {
                                                barState.contentOffset = -scrollThresholdPx - 1f
                                            }
                                            // 等 currentHeightPx / contentPadding 按收起后高度重算完
                                            withFrameNanos { }
                                            withFrameNanos { }
                                            withFrameNanos { }
                                        }
                                        // 负偏移：让目标周标题停在顶栏下方
                                        val offsetPx = with(density) { -latestTopPadding.roundToPx() }
                                        isProgrammaticScroll = true
                                        listState.smoothScrollToItem(index, offsetPx)
                                        isProgrammaticScroll = false
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
                                        start = tabletHorizontalPadding,
                                        top = topContentPadding,
                                        end = tabletHorizontalPadding,
                                        bottom = 120.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    weekGroups.forEach { (week, weekCourses) ->
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
                                                            val dateStr = courseDate.format(DATE_FORMATTER)
                                                            val sectionText = course.getTimeDisplayText()
                                                            val timeStart = sectionTimes[course.startSection]?.split("-")?.firstOrNull() ?: ""
                                                            val timeEnd = sectionTimes[course.endSection]?.split("-")?.lastOrNull() ?: ""
                                                            val timeText = if (course.hasValidCustomTime()) {
                                                                ""
                                                            } else if (timeStart.isNotEmpty() && timeEnd.isNotEmpty()) "$timeStart - $timeEnd" else ""

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
                                                                            style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp),
                                                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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

/**
 * 程序化定位滚动：目标不可见时按视口比例快速推进，可见后按剩余距离比例缓动收敛。
 * 默认 animateScrollToItem 对长距离用 spring 一冲到底，观感像瞬移。
 */
private suspend fun LazyListState.smoothScrollToItem(
    index: Int,
    scrollOffset: Int,
    maxFrames: Int = 160,
) {
    if (index < 0) return
    scroll {
        var frames = 0
        var settled = 0
        while (frames < maxFrames && settled < 2) {
            frames++
            val info = layoutInfo.visibleItemsInfo.find { it.index == index }
            if (info == null) {
                settled = 0
                val forward = index > firstVisibleItemIndex
                val direction = if (forward) 1f else -1f
                val viewport =
                    (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
                // 临近目标时缩小步长，降低越过目标后再回弹的概率
                val itemsAway = abs(index - firstVisibleItemIndex)
                val fraction = when {
                    itemsAway >= 12 -> 0.22f
                    itemsAway >= 6 -> 0.14f
                    itemsAway >= 3 -> 0.09f
                    else -> 0.06f
                }
                scrollBy(direction * viewport * fraction)
            } else {
                val delta = info.offset - scrollOffset
                if (abs(delta) < 1.5f) {
                    if (delta != 0) scrollBy(delta.toFloat())
                    settled++
                } else {
                    settled = 0
                    // 可见后按剩余距离比例收敛，越接近越慢
                    val step = delta * 0.12f
                    scrollBy(
                        if (abs(step) < 1f) {
                            if (delta > 0) 1f else -1f
                        } else step
                    )
                }
            }
            withFrameNanos { }
        }
        // 帧数兜底后仍未贴齐则精确落位
        val info = layoutInfo.visibleItemsInfo.find { it.index == index }
        if (info != null) {
            val remaining = info.offset - scrollOffset
            if (remaining != 0) scrollBy(remaining.toFloat())
        } else {
            scrollToItem(index, scrollOffset)
        }
    }
}
