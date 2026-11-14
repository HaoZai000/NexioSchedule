package com.haooz.chedule.ui.activities.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.appearance.CardRefractionLevel
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.util.nowLocalTime
import com.haooz.chedule.data.util.parseHourMinute
import com.haooz.chedule.i18n.TodayStr
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.theme.rememberCourseColors
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val MINUTES_PER_DAY = 24 * 60
private const val DEFAULT_HOUR_HEIGHT = 64f
private const val MIN_HOUR_HEIGHT = 34f
private const val MAX_HOUR_HEIGHT = 140f
private const val CARD_CORNER_DP = 12f
private const val CARD_H_GAP_DP = 2f
private const val CARD_V_GAP_DP = 1.5f
private const val MAX_ALL_DAY_CHIPS = 5

internal enum class TimelineSource {
    COURSE,
    CALENDAR,
}

internal data class TimelineEntry(
    val key: String,
    val course: Course,
    val startMinute: Int,
    val endMinute: Int,
    val source: TimelineSource,
)

/** 一项日程在某个时间片里的占位：lane 为该片内的列序，laneCount 为该片的列数。 */
internal data class TimelineBlock(
    val startMinute: Int,
    val endMinute: Int,
    val lane: Int,
    val laneCount: Int,
)

internal data class TimelineLayout(
    val entry: TimelineEntry,
    val blocks: List<TimelineBlock>,
)

/**
 * 按「时间边界切片」计算占位：每一片内只有真正重叠的项才并排分栏，
 * 因此 12–14 与 13–15 会得到「整宽 → 各半 → 另一项整宽」，也就是缺角的长方形。
 * 相邻且分栏状态相同的片会重新合并，避免切出大量碎片。
 */
internal fun buildTimelineLayouts(entries: List<TimelineEntry>): List<TimelineLayout> {
    val validEntries = entries.filter {
        it.startMinute >= 0 && it.endMinute <= MINUTES_PER_DAY && it.endMinute > it.startMinute
    }
    if (validEntries.isEmpty()) return emptyList()
    val boundaries = validEntries
        .flatMap { listOf(it.startMinute, it.endMinute) }
        .distinct()
        .sorted()
    val blocksByKey = LinkedHashMap<String, MutableList<TimelineBlock>>()
    validEntries.forEach { blocksByKey[it.key] = mutableListOf() }

    boundaries.zipWithNext().forEach { (start, end) ->
        val active = validEntries
            .filter { it.startMinute <= start && it.endMinute >= end }
            .sortedWith(compareBy<TimelineEntry> { it.startMinute }.thenBy { it.key })
        active.forEachIndexed { lane, entry ->
            val list = blocksByKey.getValue(entry.key)
            val last = list.lastOrNull()
            if (last != null &&
                last.endMinute == start &&
                last.lane == lane &&
                last.laneCount == active.size
            ) {
                list[list.lastIndex] = last.copy(endMinute = end)
            } else {
                list += TimelineBlock(
                    startMinute = start,
                    endMinute = end,
                    lane = lane,
                    laneCount = active.size,
                )
            }
        }
    }

    return validEntries
        .sortedWith(compareBy<TimelineEntry> { it.startMinute }.thenBy { it.source.ordinal }.thenBy { it.key })
        .mapNotNull { entry ->
            val blocks = blocksByKey[entry.key].orEmpty()
            if (blocks.isEmpty()) null else TimelineLayout(entry, blocks)
        }
}

@Composable
internal fun ScheduleTimeline(
    date: LocalDate,
    isToday: Boolean,
    courses: List<Course>,
    allCourses: List<Course>,
    hiddenCourseIds: Set<String>,
    sectionTimes: Map<Int, String>,
    systemEvents: List<SystemCalendarEvent>,
    contentReady: Boolean,
    pageWeek: Int,
    isTablet: Boolean,
    showClassroom: Boolean,
    showTeacher: Boolean,
    wallpaperBackdrop: Backdrop?,
    cardBlurRadius: Float,
    cardRefraction: CardRefractionLevel,
    cardSurfaceAlpha: Float,
    scrollBehavior: SharedScrollBehavior?,
    onScrollYChanged: (Int) -> Unit,
    onListScrollInProgress: (Boolean) -> Unit,
    page: Int = 0,
    hasAllDayEvents: Boolean = false,
    onHorizontalOverflowChanged: (page: Int, overflow: Boolean) -> Unit = { _, _ -> },
    onCourseClick: (
        courses: List<Course>,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        snapshot: TransitionLayerSnapshot?,
        courseIdToHide: String,
        targetWeek: Int,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val calendarLabel = TodayStr.systemCalendar()
    val untitledEvent = TodayStr.untitledEvent()
    val visibleCourses = remember(courses, hiddenCourseIds) {
        if (hiddenCourseIds.isEmpty()) courses else courses.filterNot { it.id in hiddenCourseIds }
    }
    val entries = remember(visibleCourses, systemEvents, sectionTimes, date, calendarLabel, untitledEvent) {
        buildTimelineEntries(
            date = date,
            courses = visibleCourses,
            sectionTimes = sectionTimes,
            systemEvents = systemEvents,
            calendarLabel = calendarLabel,
            untitledEvent = untitledEvent,
        )
    }
    val layouts = remember(entries) { buildTimelineLayouts(entries) }
    val maxConcurrency = remember(layouts) {
        layouts.flatMap { it.blocks }.maxOfOrNull { it.laneCount } ?: 1
    }
    var hourHeight by remember(date) { mutableFloatStateOf(DEFAULT_HOUR_HEIGHT) }
    var nowMinute by remember(date) { mutableIntStateOf(currentMinuteOfDay()) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)
    val hourLabelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
    // 缩放手势期间与结束后短暂时间内降级玻璃采样，避免每帧多张卡片重复做背景模糊。
    val zoomScope = rememberCoroutineScope()
    var pinching by remember { mutableStateOf(false) }
    // 用数组槽存 Job，避免每次手势事件都写 State 触发重组。
    val pinchSettleJob = remember { arrayOfNulls<Job>(1) }

    LaunchedEffect(isToday, date) {
        while (isToday) {
            nowMinute = currentMinuteOfDay()
            delay(30_000L)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val compactWidth = maxWidth < 360.dp
        val gutterWidth = if (compactWidth) 46.dp else 56.dp
        // 默认让各列均分可用宽度，只有窄到读不出字时才横向溢出，避免为了见右列去抢滑动手势。
        val minimumLaneWidth = if (isTablet) 132.dp else 84.dp
        val eventAreaWidth = maxOf(
            maxWidth - gutterWidth,
            minimumLaneWidth * maxConcurrency,
        )
        val contentWidth = gutterWidth + eventAreaWidth + 6.dp
        val horizontalOverflow = eventAreaWidth > maxWidth - gutterWidth
        val contentHeight = (hourHeight * 24f).dp
        val verticalState = rememberScrollState()
        val horizontalState = rememberScrollState()
        var hasPositioned by remember(date) { mutableStateOf(false) }
        // 滚动 / 缩放中不采样壁纸：每张卡都是独立的模糊图层，运动中重复采样是主要掉帧来源。
        val glassActive = !pinching &&
            !verticalState.isScrollInProgress &&
            !horizontalState.isScrollInProgress

        LaunchedEffect(horizontalOverflow, page) {
            onHorizontalOverflowChanged(page, horizontalOverflow)
        }

        LaunchedEffect(contentReady, date) {
            if (!contentReady || hasPositioned) return@LaunchedEffect
            // 等时间轴真正有可滚动范围后再定位：分页容器里首帧 maxValue 常常还是 0，
            // 只等两帧会把目标位置钳到 0，看起来像「没有自动定位到当前时间」。
            var waitedFrames = 0
            while (verticalState.maxValue == 0 && waitedFrames < 60) {
                withFrameNanos { }
                waitedFrames++
            }
            val targetMinute = when {
                isToday -> (nowMinute - 90).coerceIn(0, MINUTES_PER_DAY)
                entries.isNotEmpty() -> (entries.minOf { it.startMinute } - 30).coerceIn(0, MINUTES_PER_DAY)
                else -> 8 * 60
            }
            val targetPx = with(density) { (hourHeight.dp * (targetMinute / 60f)).toPx() }
            verticalState.scrollTo(targetPx.roundToInt().coerceIn(0, verticalState.maxValue))
            hasPositioned = true
        }

        LaunchedEffect(verticalState) {
            // 按 8px 粒度上报：父级用这个值驱动顶栏折叠，逐像素回调会导致整棵树每帧重组。
            snapshotFlow { verticalState.value / 8 * 8 }
                .distinctUntilChanged()
                .collect { offset ->
                    if (verticalState.isScrollInProgress) hasPositioned = true
                    onScrollYChanged(offset)
                }
        }

        LaunchedEffect(verticalState, horizontalState) {
            snapshotFlow {
                verticalState.isScrollInProgress || horizontalState.isScrollInProgress
            }
                .distinctUntilChanged()
                .collect { scrolling -> onListScrollInProgress(scrolling) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (scrollBehavior != null) {
                        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        Modifier
                    }
                )
                .verticalScroll(verticalState)
                .horizontalScroll(horizontalState)
                .twoFingerTimelineZoom(
                    onZoom = { zoom ->
                        pinching = true
                        pinchSettleJob[0]?.cancel()
                        pinchSettleJob[0] = zoomScope.launch {
                            delay(220L)
                            pinching = false
                        }
                        val previous = hourHeight
                        hourHeight = (hourHeight * zoom).coerceIn(MIN_HOUR_HEIGHT, MAX_HOUR_HEIGHT)
                        // 缩放后按比例补偿滚动位置，让视野停在原来的时段附近。
                        if (previous > 0f && hourHeight != previous) {
                            val ratio = hourHeight / previous
                            val target = (verticalState.value * ratio)
                                .roundToInt()
                                .coerceIn(0, verticalState.maxValue)
                            verticalState.dispatchRawDelta((target - verticalState.value).toFloat())
                        }
                    },
                ),
        ) {
            Box(modifier = Modifier.size(contentWidth, contentHeight)) {
                val gridColor = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.10f)
                val gridStrongColor = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.18f)
                Canvas(modifier = Modifier.size(contentWidth, contentHeight)) {
                    val gutterPx = gutterWidth.toPx()
                    val hourPx = size.height / 24f
                    val lineWidth = 1.dp.toPx()
                    for (hour in 0..24) {
                        val y = hour * hourPx
                        drawLine(
                            color = if (hour % 6 == 0) gridStrongColor else gridColor,
                            start = Offset(gutterPx, y),
                            end = Offset(size.width, y),
                            strokeWidth = lineWidth,
                        )
                    }
                    drawLine(
                        color = gridStrongColor,
                        start = Offset(gutterPx, 0f),
                        end = Offset(gutterPx, size.height),
                        strokeWidth = lineWidth,
                    )
                    // 刻度文字并进同一张画布，省掉 24 个文本布局节点与它们的图层。
                    val labelInset = 8.dp.toPx()
                    for (hour in 0..23) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = formatTimelineMinute(hour * 60),
                            // 0 点刻度不能被顶边裁掉，贴住顶部即可
                            topLeft = Offset(5.dp.toPx(), (hour * hourPx - labelInset).coerceAtLeast(0f)),
                            style = TextStyle(fontSize = 11.sp, color = hourLabelColor),
                        )
                    }
                }

                layouts.forEach { layout ->
                    key(layout.entry.key) {
                        TimelineEntryCard(
                            layout = layout,
                            hourHeight = hourHeight,
                            gutterWidth = gutterWidth,
                            contentWidth = contentWidth,
                            isTablet = isTablet,
                            showClassroom = showClassroom,
                            showTeacher = showTeacher,
                            wallpaperBackdrop = wallpaperBackdrop,
                            cardBlurRadius = cardBlurRadius,
                            cardRefraction = cardRefraction,
                            cardSurfaceAlpha = cardSurfaceAlpha,
                            glassActive = glassActive,
                            allCourses = allCourses,
                            pageWeek = pageWeek,
                            onCourseClick = onCourseClick,
                        )
                    }
                }

                if (entries.isEmpty() && contentReady) {
                    // 只有全天日程时上方已经有胶囊条，这里不重复提示「没有日程」
                    if (!hasAllDayEvents) {
                        Text(
                            text = TodayStr.noSchedule(),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.offset(
                                x = gutterWidth + 16.dp,
                                y = (hourHeight * 8f).dp + 12.dp,
                            ),
                        )
                    }
                }

                if (isToday) {
                    val nowY = (hourHeight * nowMinute / 60f).dp
                    Box(
                        modifier = Modifier
                            .offset(x = gutterWidth, y = nowY)
                            .width(eventAreaWidth)
                            .height(1.5.dp)
                            .background(MiuixTheme.colorScheme.primary),
                    )
                    Text(
                        text = formatTimelineMinute(nowMinute),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.offset(x = 3.dp, y = nowY - 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * 一项日程只画一块卡：所有时间片按并集合成一个路径，
 * 重叠段收窄后形成缺角，未重叠段保持整宽。
 */
@Composable
private fun TimelineEntryCard(
    layout: TimelineLayout,
    hourHeight: Float,
    gutterWidth: Dp,
    contentWidth: Dp,
    isTablet: Boolean,
    showClassroom: Boolean,
    showTeacher: Boolean,
    wallpaperBackdrop: Backdrop?,
    cardBlurRadius: Float,
    cardRefraction: CardRefractionLevel,
    cardSurfaceAlpha: Float,
    glassActive: Boolean,
    allCourses: List<Course>,
    pageWeek: Int,
    onCourseClick: (
        courses: List<Course>,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        snapshot: TransitionLayerSnapshot?,
        courseIdToHide: String,
        targetWeek: Int,
    ) -> Unit,
) {
    val entry = layout.entry
    val course = entry.course
    val density = LocalDensity.current
    val isDark = isAppDarkTheme()
    val courseColors = rememberCourseColors(course, isDark)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }

    val geometry = remember(layout, hourHeight, gutterWidth, contentWidth, density) {
        buildEntryGeometry(
            layout = layout,
            hourHeight = hourHeight,
            gutterWidth = gutterWidth,
            contentWidth = contentWidth,
            density = density,
        )
    }
    val shape = remember(geometry, density) {
        TimelineEntryShape(
            blocks = geometry.blocks.map { it.translate(-geometry.bounds.left, -geometry.bounds.top) },
            cornerRadiusPx = with(density) { CARD_CORNER_DP.dp.toPx() },
        )
    }

    val hasBackdrop = wallpaperBackdrop != null
    // 只有静止时才做模糊/折射采样；滚动与缩放期间用实色兜底，避免每帧重复渲染背景。
    val needsSampling = glassActive && hasBackdrop &&
        (cardBlurRadius > 0f || cardRefraction != CardRefractionLevel.OFF)
    val fillColor = remember(courseColors.tint, isDark, cardSurfaceAlpha, hasBackdrop) {
        val tintAlpha = (0.22f * if (hasBackdrop) 1.6f else 1f).coerceIn(0f, 0.5f)
        val tint = courseColors.tint.copy(alpha = tintAlpha)
        val surface = if (isDark) {
            Color.Black.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
        } else {
            Color.White.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
        }
        surface.compositeOver(tint)
    }
    val textColor = remember(fillColor) {
        if (fillColor.luminance() > 0.55f) {
            Color.Black.copy(alpha = 0.82f)
        } else {
            Color.White.copy(alpha = 0.94f)
        }
    }
    val outlineColor = remember(fillColor, isDark) {
        if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.07f)
    }

    val blurPx = with(density) { cardBlurRadius.dp.toPx() }
    val lensRadiusPx = with(density) { cardRefraction.lensRadiusDp.dp.toPx() }
    val lensStrengthPx = with(density) { cardRefraction.lensStrengthDp.dp.toPx() }
    val effects: BackdropEffectScope.() -> Unit = remember(blurPx, lensRadiusPx, lensStrengthPx, cardRefraction) {
        {
            if (blurPx > 0f) blur(blurPx)
            if (cardRefraction != CardRefractionLevel.OFF) lens(lensRadiusPx, lensStrengthPx)
        }
    }
    val surfaceDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit =
        remember(fillColor) { { drawRect(fillColor) } }

    val contentBlock = remember(geometry) {
        geometry.blocks.firstOrNull { it.height >= with(density) { 42.dp.toPx() } }
            ?: geometry.blocks.maxByOrNull { it.height }
            ?: geometry.blocks.first()
    }
    val blockHeightDp = with(density) { contentBlock.height.toDp() }
    val laneSqueezed = layout.blocks.any { it.laneCount > 1 }
    val baseScale = if (laneSqueezed) 0.92f else 1f
    val timeLabel = "${formatTimelineMinute(entry.startMinute)}–${formatTimelineMinute(entry.endMinute)}"

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { geometry.bounds.left.toDp() },
                y = with(density) { geometry.bounds.top.toDp() },
            )
            .size(
                width = with(density) { geometry.bounds.width.toDp() },
                height = with(density) { geometry.bounds.height.toDp() },
            )
            .onGloballyPositioned { coordinates[0] = it },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (needsSampling) {
                        Modifier.drawBackdrop(
                            backdrop = wallpaperBackdrop,
                            shape = { shape },
                            effects = effects,
                            highlight = null,
                            shadow = null,
                            onDrawSurface = surfaceDraw,
                        )
                    } else {
                        Modifier.background(fillColor, shape)
                    }
                )
                .drawWithContent {
                    drawContent()
                    drawPath(
                        path = shape.path,
                        color = outlineColor,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                },
        )

        Column(
            modifier = Modifier
                .offset(
                    x = with(density) { (contentBlock.left - geometry.bounds.left).toDp() },
                    y = with(density) { (contentBlock.top - geometry.bounds.top).toDp() },
                )
                .width(with(density) { contentBlock.width.toDp() })
                .height(with(density) { contentBlock.height.toDp() })
                .padding(
                    start = if (isTablet) 9.dp else 7.dp,
                    top = 4.dp,
                    end = 5.dp,
                ),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = course.name,
                fontSize = (13f * baseScale).sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = if (blockHeightDp >= 52.dp) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (blockHeightDp >= 44.dp) {
                Text(
                    text = timeLabel,
                    fontSize = (10.5f * baseScale).sp,
                    color = textColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (blockHeightDp >= 60.dp && showClassroom && course.classroom.isNotBlank()) {
                Text(
                    text = course.classroom,
                    fontSize = (10.5f * baseScale).sp,
                    color = textColor.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (blockHeightDp >= 76.dp && showTeacher && course.teacher.isNotBlank() &&
                entry.source == TimelineSource.COURSE
            ) {
                Text(
                    text = course.teacher,
                    fontSize = (10.5f * baseScale).sp,
                    color = textColor.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (entry.source == TimelineSource.COURSE) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(entry.key) {
                        detectTapGestures {
                            val current = coordinates[0]
                            if (current != null && current.isAttached) {
                                val position = current.localToRoot(Offset.Zero)
                                onCourseClick(
                                    allCourses.filter { it.name == course.name },
                                    position.x,
                                    position.y,
                                    current.size.width.toFloat(),
                                    current.size.height.toFloat(),
                                    null,
                                    course.id,
                                    pageWeek,
                                )
                            }
                        }
                    },
            )
        }
    }
}

private data class EntryGeometry(
    val blocks: List<Rect>,
    val bounds: Rect,
)

/** 把时间片换算成像素矩形，并在最外侧留出卡片间距。 */
private fun buildEntryGeometry(
    layout: TimelineLayout,
    hourHeight: Float,
    gutterWidth: Dp,
    contentWidth: Dp,
    density: Density,
): EntryGeometry {
    val gutterPx = with(density) { gutterWidth.toPx() }
    val contentPx = with(density) { contentWidth.toPx() }
    val rightPaddingPx = with(density) { 6.dp.toPx() }
    val eventAreaPx = (contentPx - gutterPx - rightPaddingPx).coerceAtLeast(1f)
    val hourPx = with(density) { hourHeight.dp.toPx() }
    val hGap = with(density) { CARD_H_GAP_DP.dp.toPx() }
    val vGap = with(density) { CARD_V_GAP_DP.dp.toPx() }
    val lastIndex = layout.blocks.lastIndex
    val blocks = layout.blocks.mapIndexed { index, block ->
        val laneWidth = eventAreaPx / block.laneCount
        val left = gutterPx + laneWidth * block.lane + hGap
        val right = gutterPx + laneWidth * (block.lane + 1) - hGap
        val top = hourPx * block.startMinute / 60f + if (index == 0) vGap else 0f
        val bottom = hourPx * block.endMinute / 60f - if (index == lastIndex) vGap else 0f
        Rect(left = left, top = top, right = maxOf(right, left + 1f), bottom = maxOf(bottom, top + 1f))
    }
    val left = blocks.minOf { it.left }
    val top = blocks.minOf { it.top }
    val right = blocks.maxOf { it.right }
    val bottom = blocks.maxOf { it.bottom }
    return EntryGeometry(blocks = blocks, bounds = Rect(left, top, right, bottom))
}

/**
 * 由各时间片矩形求并集得到卡片轮廓。
 * 只在凸出的外角倒圆角，内部接缝保持直角 —— 这正是「被咬过的长方形」。
 */
private class TimelineEntryShape(
    private val blocks: List<Rect>,
    private val cornerRadiusPx: Float,
) : Shape {
    val path: Path = buildUnionOutline(blocks, cornerRadiusPx)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(path)
}

/**
 * 直接由纵向堆叠的矩形生成并集外轮廓：
 * 只在凸角倒圆角，凹进去的接缝保持直角 —— 这正是「被咬过的长方形」。
 * 全程 O(n) 折线拼接，没有 Path.op 布尔运算，缩放手势里每帧重建也不掉帧。
 */
private fun buildUnionOutline(blocks: List<Rect>, radius: Float): Path {
    val path = Path()
    if (blocks.isEmpty()) return path
    if (blocks.size == 1) {
        path.addRoundRect(
            RoundRect(rect = blocks[0], cornerRadius = CornerRadius(radius)),
        )
        return path
    }

    val raw = ArrayList<Offset>(blocks.size * 2 + 4)
    val first = blocks.first()
    raw += Offset(first.left, first.top)
    raw += Offset(first.right, first.top)
    for (i in 0 until blocks.lastIndex) {
        val current = blocks[i]
        val next = blocks[i + 1]
        raw += Offset(current.right, next.top)
        raw += Offset(next.right, next.top)
    }
    val last = blocks.last()
    raw += Offset(last.right, last.bottom)
    raw += Offset(last.left, last.bottom)
    for (i in blocks.lastIndex downTo 1) {
        val current = blocks[i]
        val previous = blocks[i - 1]
        raw += Offset(current.left, previous.bottom)
        raw += Offset(previous.left, previous.bottom)
    }

    // 去掉重合点，避免出现零长度边（横向位置不变时会产生）。
    val points = ArrayList<Offset>(raw.size)
    raw.forEach { point ->
        val previous = points.lastOrNull()
        if (previous == null ||
            abs(previous.x - point.x) > 0.01f ||
            abs(previous.y - point.y) > 0.01f
        ) {
            points += point
        }
    }
    if (points.size < 4) return path

    val count = points.size
    for (i in 0 until count) {
        val previous = points[(i - 1 + count) % count]
        val current = points[i]
        val next = points[(i + 1) % count]
        val cross = (current.x - previous.x) * (next.y - current.y) -
            (current.y - previous.y) * (next.x - current.x)
        val inLength = hypot(current.x - previous.x, current.y - previous.y)
        val outLength = hypot(next.x - current.x, next.y - current.y)
        val r = minOf(radius, inLength / 2f, outLength / 2f)
        if (cross > 0f && r > 0.5f) {
            val start = Offset(
                current.x - (current.x - previous.x) / inLength * r,
                current.y - (current.y - previous.y) / inLength * r,
            )
            val stop = Offset(
                current.x + (next.x - current.x) / outLength * r,
                current.y + (next.y - current.y) / outLength * r,
            )
            if (i == 0) path.moveTo(start.x, start.y) else path.lineTo(start.x, start.y)
            path.quadraticBezierTo(current.x, current.y, stop.x, stop.y)
        } else {
            if (i == 0) path.moveTo(current.x, current.y) else path.lineTo(current.x, current.y)
        }
    }
    path.close()
    return path
}

/**
 * 全天日程胶囊：换行排布而不是横向滚动。
 * 这一层的横向拖动会被外层翻页手势优先吃掉，做横向滚动条用户根本滑不动。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AllDayEventsStrip(
    events: List<SystemCalendarEvent>,
    wallpaperBackdrop: Backdrop?,
    blurRadius: Float,
    surfaceOpacity: Float,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val visible = events.take(MAX_ALL_DAY_CHIPS)
        visible.forEach { event ->
            AllDayChip(
                text = event.title.ifBlank { TodayStr.untitledEvent() },
                wallpaperBackdrop = wallpaperBackdrop,
                blurRadius = blurRadius,
                surfaceOpacity = surfaceOpacity,
            )
        }
        if (events.size > visible.size) {
            AllDayChip(
                text = "+${events.size - visible.size}",
                wallpaperBackdrop = wallpaperBackdrop,
                blurRadius = blurRadius,
                surfaceOpacity = surfaceOpacity,
            )
        }
    }
}

@Composable
private fun AllDayChip(
    text: String,
    wallpaperBackdrop: Backdrop?,
    blurRadius: Float,
    surfaceOpacity: Float,
) {
    BlurCard(
        cornerRadius = 14.dp,
        wallpaperBackdrop = wallpaperBackdrop,
        blurRadius = blurRadius,
        surfaceOpacity = surfaceOpacity,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 200.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

private fun buildTimelineEntries(
    date: LocalDate,
    courses: List<Course>,
    sectionTimes: Map<Int, String>,
    systemEvents: List<SystemCalendarEvent>,
    calendarLabel: String,
    untitledEvent: String,
): List<TimelineEntry> {
    val courseEntries = courses.mapNotNull { course ->
        val start = course.getEffectiveStartTime(sectionTimes)?.toMinuteOfDay() ?: return@mapNotNull null
        val rawEnd = course.getEffectiveEndTime(sectionTimes)?.toMinuteOfDay() ?: return@mapNotNull null
        val end = if (rawEnd <= start) MINUTES_PER_DAY else rawEnd
        if (end <= start) return@mapNotNull null
        TimelineEntry(
            key = "course:${course.id}",
            course = course,
            startMinute = start,
            endMinute = end,
            source = TimelineSource.COURSE,
        )
    }
    val calendarEntries = systemEvents.filterNot { it.allDay }.mapNotNull { event ->
        val start = event.startMinute.coerceIn(0, MINUTES_PER_DAY - 1)
        val end = event.endMinute.coerceIn(0, MINUTES_PER_DAY)
        if (end <= start) return@mapNotNull null
        val syntheticCourse = Course(
            id = "calendar:${event.id}",
            name = event.title.ifBlank { untitledEvent },
            classroom = event.location,
            teacher = event.calendarName.ifBlank { calendarLabel },
            dayOfWeek = date.dayOfWeek.isoDayNumber,
            startSection = 0,
            endSection = 0,
            startWeek = 0,
            endWeek = 0,
            weekType = Course.WEEK_TYPE_ALL,
            colorRes = if (event.color == 0L) 0xFF5B8DEFL else event.color,
            isCustomTime = true,
            customStartTime = formatTimelineMinute(start),
            customEndTime = formatTimelineMinute(end),
        )
        TimelineEntry(
            key = syntheticCourse.id,
            course = syntheticCourse,
            startMinute = start,
            endMinute = end,
            source = TimelineSource.CALENDAR,
        )
    }
    return (courseEntries + calendarEntries).sortedWith(
        compareBy<TimelineEntry> { it.startMinute }
            .thenBy { it.source.ordinal }
            .thenBy { it.course.name }
            .thenBy { it.key }
    )
}

private fun Modifier.twoFingerTimelineZoom(
    onZoom: (Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val zoom = event.calculateZoom()
                if (abs(zoom - 1f) > 0.001f) onZoom(zoom)
                event.changes.forEach { change ->
                    if (change.positionChanged()) change.consume()
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

private fun String.toMinuteOfDay(): Int? = runCatching {
    val time = parseHourMinute(trim()) ?: return@runCatching null
    time.hour * 60 + time.minute
}.getOrNull()

private fun currentMinuteOfDay(): Int {
    val now = nowLocalTime()
    return now.hour * 60 + now.minute
}

private fun formatTimelineMinute(minute: Int): String {
    val safe = minute.coerceIn(0, MINUTES_PER_DAY)
    val hour = safe / 60
    val min = safe % 60
    return "${hour.padTwo()}:${min.padTwo()}"
}

private fun Int.padTwo(): String = if (this in 0..9) "0$this" else toString()
