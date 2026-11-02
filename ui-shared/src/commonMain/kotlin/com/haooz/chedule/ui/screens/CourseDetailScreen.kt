/** 课程详情页面 */
package com.haooz.chedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.holiday.TeachingWeekReorganizationRule
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.util.formatMonthDayShort
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.effects.motion.morphClip
import com.haooz.chedule.ui.effects.motion.rememberMorphTransition
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.TransitionLayerImage
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.blockTouchPassThrough
import com.haooz.chedule.ui.utils.hostVerticalBarEdge
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.screenWidthDp
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic


@Composable
fun CourseDetailScreen(
    courses: List<Course>,
    cardLeft: Float,
    cardTop: Float,
    cardWidth: Float,
    cardHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
    screenCornerRadius: ScreenCornerRadiiPx,
    /** Retained card-local drawing, independent of the hidden source card. */
    cardSnapshot: TransitionLayerSnapshot?,
    sectionTimes: Map<Int, String>,
    classStartTime: String,
    teachingWeekReorganizations: List<TeachingWeekReorganizationRule>,
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
        // ⚠ `toSortedMap()` 是 JVM 专有（返回 java.util.SortedMap），commonMain 里
        // Android/JVM 能编过、编 iOS 才炸。等价写法：toList() 后按 key 排序。
        weekEntries.groupBy { it.first }.toList().sortedBy { it.first }
            .map { (week, entries) ->
                week to entries.sortedWith(compareBy({ it.second.dayOfWeek }, { it.second.startSection }))
            }
    }

    val liquidGlassBackdrop = rememberLayerBackdrop()
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) {
        val widthDp = screenWidthDp()
        ((widthDp - 600).coerceIn(0, 600) / 600f * 108 + 20).dp
    } else 16.dp

    val semesterStartDate = remember(classStartTime) {
        try {
            LocalDate.parse(classStartTime.replace("/", "-"))
        } catch (_: Exception) {
            todayLocalDate()
        }
    }

    val density = LocalDensity.current
    val morph = rememberMorphTransition(
        cardLeft = cardLeft,
        cardTop = cardTop,
        cardWidth = cardWidth,
        cardHeight = cardHeight,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        screenCornerRadius = screenCornerRadius,
        startCornerRadiusDp = 20f,
        onBackStart = onBackStart,
        onBack = onBack,
    )


    val isDark = isAppDarkTheme()
    val scrollBehavior = rememberSharedScrollBehavior()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) ComposeColor(0xFF2C2C2C).copy(alpha = morph.state.value.bgAlpha)
                else ComposeColor.Black.copy(alpha = morph.state.value.bgAlpha)
            )
            // 只挡下层穿透、不 consume：consumeAllTouches 会连 LazyColumn 拖动一起吃掉
            .blockTouchPassThrough()
    ) {
        val s = morph.state.value

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = false
                    transformOrigin = TransformOrigin(s.anchorX, 0.5f)
                    scaleX = s.scale
                    scaleY = s.scale
                    translationX = s.translationX
                    translationY = s.translationY
                }
                .morphClip(morph)
                .background(MiuixTheme.colorScheme.background)
        ) {
            if (cardSnapshot != null && s.snapshotAlpha > 0f) {
                TransitionLayerImage(
                    snapshot = cardSnapshot,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .clip(ContinuousRoundedRectangle(20.dp))
                        .graphicsLayer { alpha = s.snapshotAlpha },
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.TopStart,
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
                            noTopStatusBar = hostVerticalBarEdge(),
                        ) {
                            CollapsibleTopAppBar(
                                title = courseName,
                                largeTitle = courseName,
                                modifier = Modifier.windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                                ),
                                scrollBehavior = scrollBehavior,
                                contentPadding = {},
                                startAction = { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = { morph.close() },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.ChevronBackward,
                                        contentDescription = CommonStr.back(),
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
                                    // 无材质档：玻璃全关，采样录屏层不挂
                                    if (com.haooz.chedule.ui.utils.AppMaterialSettings.isNoneTier()) {
                                        Modifier
                                    } else {
                                        Modifier.liquidGlassLayerBackdrop(
                                            liquidGlassBackdrop
                                        )
                                    }
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
                                            val diff = abs(week - targetWeek)
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
                                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
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
                                                    text = CommonStr.weekN(week),
                                                )
                                                Card(
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
                                                                1 -> CommonStr.weekdayMon()
                                                                2 -> CommonStr.weekdayTue()
                                                                3 -> CommonStr.weekdayWed()
                                                                4 -> CommonStr.weekdayThu()
                                                                5 -> CommonStr.weekdayFri()
                                                                6 -> CommonStr.weekdaySat()
                                                                7 -> CommonStr.weekdaySun()
                                                                else -> CommonStr.unknown()
                                                            }
                                                            val courseDate = TeachingWeekReorganization.dateForPosition(
                                                                semesterStartDate, week, course.dayOfWeek, teachingWeekReorganizations,
                                                            ) ?: return@forEachIndexed
                                                            val dateStr = courseDate.formatMonthDayShort()
                                                            val sectionText = course.timeDisplayTextLocalized()
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
                                                                            color = MiuixTheme.colorScheme.primary,
                                                                            textAlign = TextAlign.End,
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
