package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.appearance.CardContentAlignment
import com.haooz.chedule.data.appearance.CardTextColor
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.components.LocalGlassRedrawSignal
import com.haooz.chedule.ui.components.SoftGlassSurface
import com.haooz.chedule.ui.components.rememberScheduleRevealState
import com.haooz.chedule.ui.components.softGlassBackdrop
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.theme.courseCardTextColor
import com.haooz.chedule.ui.theme.rememberCourseColors
import com.haooz.chedule.ui.theme.scheduleAccentColor
import com.haooz.chedule.ui.utils.courseCardNoneTierBacking
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.glass.SoftGlassStyle
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.material.LocalMaterialNoneEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 落地冲击波：中心坐标 + token，触发周围课程卡涟漪。 */
data class LandRippleSpec(
    val center: Offset = Offset.Zero,
    val token: Int = 0,
)

/** 课程卡动画量惰性持有：静止卡永不实例化。 */
private class CardAnims {
    val sinkScale by lazy { Animatable(1f) }
    val rippleScale by lazy { Animatable(1f) }
}

val LocalLandRipple = compositionLocalOf { LandRippleSpec() }

/** 正在上课的课程 id 稳定容器：写 [ids] 只让读过的卡片重组。 */
@Stable
class OngoingCourseIdsState {
    var ids: Set<String> by mutableStateOf(emptySet())
}

private val DefaultOngoingCourseIds = OngoingCourseIdsState()

/** 非当前周页共用：ids 恒空且引用稳定。 */
val EmptyOngoingCourseIds = OngoingCourseIdsState()

/** 当前正在上课的课程 id；无计时器的宿主默认不高亮。 */
val LocalOngoingCourseIds = compositionLocalOf { DefaultOngoingCourseIds }

/** 描边画在卡片外沿，内缘贴卡片边界。 */
private val OngoingOutlineWidth = 2.dp

/**
 * 课表课程卡。
 *
 * 两条渲染路径由采样源决定：有玻璃采样源时走「白/黑垫底 + 课程色 + 柔光玻璃」，
 * 否则退回纯色表面。无材质档不挂任何玻璃节点。
 */
@Composable
fun CourseCard(
    course: Course,
    isCurrentWeek: Boolean = true,
    isHoliday: Boolean = false,
    isCourseCancelled: Boolean = false,
    isWorkSwap: Boolean = false,
    hasMultipleCourses: Boolean = false,
    softGlassSurface: SoftGlassSurface? = null,
    /**
     * 实时录制版采样源（浮层用）：采样它下面此刻画了什么，才能把课程卡一起录进来。
     * 与 [glassStyle] 配对使用，两个都非空才生效；[softGlassSurface] 优先。
     */
    glassBackdrop: GlassBackdrop? = null,
    glassStyle: SoftGlassStyle? = null,
    cardAlpha: Float = 0.12f,
    /** 有壁纸时白/黑底不透明度（卡片不透明度）。 */
    cardSurfaceAlpha: Float = 0.34f,
    /** 垫在卡片最底部的纯色；null = 不垫（网格里的卡片走默认）。 */
    solidBackingColor: Color? = null,
    cardHeightPerSection: Float = 54f,
    /** 自定义时间课显式指定高度；null 时按节次数算。 */
    customCardHeightDp: Float? = null,
    cardCornerRadius: Float = 10f,
    isTablet: Boolean = false,
    cardContentAlignment: CardContentAlignment = CardContentAlignment.CENTER_CENTER,
    cardTextColor: CardTextColor = CardTextColor.COLORFUL,
    cardTextScale: Float = 1f,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    isDragging: Boolean = false,
    /** 长按后是否允许接着拖动调课；自定义时间课无处可落，传 false。 */
    dragEnabled: Boolean = true,
    disablePadding: Boolean = false,
    isDark: Boolean = isAppDarkTheme(),
    // 非 state：滑动中坐标每帧变，读取不触发重组
    touchState: ScheduleTouchState? = null,
    onClick: () -> Unit,
    onLongPressStart: (cardLeft: Float, cardTop: Float, width: Float, height: Float) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val sectionCount = course.endSection - course.startSection + 1
    val cardHeight = (customCardHeightDp ?: (sectionCount * cardHeightPerSection)).dp
    val glassSourceReady = softGlassSurface != null || (glassBackdrop != null && glassStyle != null)
    val materialsEnabled = !LocalMaterialNoneEnabled.current
    // 有玻璃采样源才走分层表面；无材质档由宿主/兜底垫色处理，不能并进来
    val layeredSurface = glassSourceReady
    val cornerRadius = if (isTablet) cardCornerRadius * 1.3f else cardCornerRadius
    val localDensity = LocalDensity.current
    // 滚动/翻页信号：让玻璃的采样原点按帧跟手
    val glassRedraw = LocalGlassRedrawSignal.current

    // 缩放 / 涟漪两个动画量惰性持有，静止卡不建
    val anims = remember { CardAnims() }
    var rippleAnimating by remember { mutableStateOf(false) }
    val cardBoundsPx = remember { FloatArray(4) }
    // 只缓存布局坐标，root 位置按需现算
    val cardCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val refreshCardBounds = {
        val coords = cardCoords[0]
        if (coords != null && coords.isAttached) {
            val center = coords.localToRoot(Offset(coords.size.width / 2f, coords.size.height / 2f))
            cardBoundsPx[0] = center.x
            cardBoundsPx[1] = center.y
            cardBoundsPx[2] = coords.size.width.toFloat()
            cardBoundsPx[3] = coords.size.height.toFloat()
        }
    }

    var sinkPressed by remember { mutableStateOf(false) }
    var sinkActive by remember { mutableStateOf(false) }
    LaunchedEffect(sinkPressed) {
        // 首帧是非按压态，直接收摊：否则每张卡会白跑一次 spring + 两次重组
        if (!sinkPressed && !sinkActive) return@LaunchedEffect
        sinkActive = true
        anims.sinkScale.animateTo(
            targetValue = if (sinkPressed) 0.94f else 1f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        )
        if (!sinkPressed) sinkActive = false
    }

    val landRipple = LocalLandRipple.current
    val lastRippleToken = remember { mutableIntStateOf(landRipple.token) }
    if (landRipple.token != 0) {
        LaunchedEffect(landRipple.token) {
            if (landRipple.token == lastRippleToken.intValue) return@LaunchedEffect
            lastRippleToken.intValue = landRipple.token
            refreshCardBounds()
            val cx = cardBoundsPx[0]
            val cy = cardBoundsPx[1]
            if (cx == 0f && cy == 0f) return@LaunchedEffect
            val distance = hypot(cx - landRipple.center.x, cy - landRipple.center.y)
            val delayMs = if (distance < 90f) 0L else ((distance - 90f) * 0.3f).toLong().coerceAtMost(400L)
            if (delayMs > 0) delay(delayMs.milliseconds)
            rippleAnimating = true
            try {
                anims.rippleScale.animateTo(1.08f, tween(80, easing = FastOutSlowInEasing))
                anims.rippleScale.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            } finally {
                rippleAnimating = false
            }
        }
    }

    // 玻璃底自带壁纸色会把课程色压淡，有玻璃时把着色放大一档
    val effectiveAlpha = (if (glassSourceReady) cardAlpha * 1.3f else cardAlpha).coerceIn(0f, 1f)
    val courseColors = rememberCourseColors(course, isDark)

    // 只订阅「本卡是否正在上课」，30 秒节拍换集合时其余卡完全 skip
    val ongoingIdsState = LocalOngoingCourseIds.current
    val isOngoing by remember(course.id, ongoingIdsState) {
        derivedStateOf { course.id in ongoingIdsState.ids }
    }
    val ongoingReveal = rememberScheduleRevealState(isOngoing)
    val outlineCache = remember { OutlineCache() }
    val outlineCornerRadius = cornerRadius.dp + OngoingOutlineWidth / 2f
    val accentColor = scheduleAccentColor()
    val ongoingOutlineModifier = if (isOngoing || ongoingReveal.isActive) {
        Modifier.drawWithContent {
            val progress = ongoingReveal.value
            if (progress <= 0f) {
                drawContent()
                return@drawWithContent
            }
            drawOngoingOutline(
                cache = outlineCache,
                cornerRadius = outlineCornerRadius,
                color = accentColor.copy(alpha = accentColor.alpha * progress),
            )
        }
    } else {
        Modifier
    }

    val useMonet = MiuixTheme.isDynamicColor
    val inactiveColor =
        if (useMonet) MiuixTheme.colorScheme.onSurfaceVariantActions else Color(0xFF9E9E9E)
    val cardColor = remember(
        courseColors.tint,
        inactiveColor,
        isCurrentWeek,
        isHoliday,
        isCourseCancelled,
        effectiveAlpha,
    ) {
        if (isCurrentWeek && !isHoliday && !isCourseCancelled) {
            courseColors.tint.copy(alpha = effectiveAlpha)
        } else {
            inactiveColor.copy(alpha = effectiveAlpha * 0.7f)
        }
    }
    val themeBackground = MiuixTheme.colorScheme.background
    // 无壁纸时由宿主给页底色；无材质档有壁纸时自己补同值的一层
    val cardBacking = solidBackingColor
        ?: if (!materialsEnabled) courseCardNoneTierBacking(isDark) else null
    val monetCardBackground = remember(
        cardColor,
        cardBacking,
        themeBackground,
        glassSourceReady,
        cardSurfaceAlpha,
        isDark,
    ) {
        val backing = cardBacking?.compositeOver(themeBackground) ?: themeBackground
        val surface = cardColor.compositeOver(backing)
        if (glassSourceReady) {
            (if (isDark) Color.Black else Color.White)
                .copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
                .compositeOver(surface)
        } else {
            surface
        }
    }
    val textColor = remember(
        courseColors,
        useMonet,
        inactiveColor,
        monetCardBackground,
        isCurrentWeek,
        isHoliday,
        isCourseCancelled,
        glassSourceReady,
        isDark,
        cardTextColor,
    ) {
        courseCardTextColor(
            courseColors = courseColors,
            useMonet = useMonet,
            inactiveColor = inactiveColor,
            background = monetCardBackground,
            isCurrentWeek = isCurrentWeek,
            isHoliday = isHoliday,
            isCourseCancelled = isCourseCancelled,
            // 无材质档没有模糊，必须 false，否则字被提亮后落在近白底上几乎看不见
            hasBlur = glassSourceReady,
            isDark = isDark,
            cardTextColor = cardTextColor,
        )
    }

    val cardShape = remember(cornerRadius) { ContinuousRoundedRectangle(cornerRadius.dp) }
    // 静止卡不挂变换层：几十张×两页的合成层是滑动 draw 热点
    val needTransformLayer = sinkPressed || sinkActive || isDragging || rippleAnimating
    val transformModifier = if (needTransformLayer) {
        Modifier.graphicsLayer {
            val scale = anims.sinkScale.value * anims.rippleScale.value
            scaleX = scale
            scaleY = scale
            alpha = if (isDragging) 0f else 1f
        }
    } else {
        Modifier
    }

    val baseModifier = modifier
        .fillMaxWidth()
        .height(cardHeight)
        .then(
            if (disablePadding) Modifier else Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
        )
        // 描边挂在变换层内侧，缩放/alpha 一起跟随
        .then(transformModifier)
        .then(ongoingOutlineModifier)
        // 只存坐标：布局期不再逐卡 localToRoot，消费点自己 refreshCardBounds
        .onGloballyPositioned { cardCoords[0] = it }

    val surfaceModifier = if (layeredSurface) {
        layeringSurfaceModifier(
            baseModifier = baseModifier,
            cardShape = cardShape,
            solidBackingColor = solidBackingColor,
            surfaceColor = remember(cardColor, cardSurfaceAlpha, isDark) {
                val overlayColor = if (isDark) {
                    Color.Black.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
                } else {
                    Color.White.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
                }
                // 白/黑底 + 课程色压成一次 drawRect
                flattenStackedFills(overlayColor, cardColor)
            },
            glassModifier = rememberGlassModifier(
                materialsEnabled = materialsEnabled,
                softGlassSurface = softGlassSurface,
                glassBackdrop = glassBackdrop,
                glassStyle = glassStyle,
                cardShape = cardShape,
                cornerRadius = cornerRadius,
                coords = cardCoords,
                redraw = glassRedraw,
            ),
        )
    } else {
        val filledCardColor = remember(cardBacking, cardColor) {
            if (cardBacking != null) flattenStackedFills(cardBacking, cardColor) else cardColor
        }
        baseModifier
            .then(if (needTransformLayer) Modifier.clip(cardShape) else Modifier)
            .background(filledCardColor, cardShape)
    }

    Box(
        modifier = surfaceModifier.courseCardGesture(
            course = course,
            touchState = touchState,
            coordinates = cardCoords,
            bounds = cardBoundsPx,
            refreshBounds = refreshCardBounds,
            scope = scope,
            dragEnabled = dragEnabled,
            onSinkChange = { sinkPressed = it },
            onLongPressStart = onLongPressStart,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        val cardContent: @Composable BoxScope.() -> Unit = {
            CardContent(
                course = course,
                textColor = textColor,
                hasMultipleCourses = hasMultipleCourses,
                isTablet = isTablet,
                cardContentAlignment = cardContentAlignment,
                cardHeightDp = cardHeight.value,
                cardHeightPerSection = cardHeightPerSection,
                isHoliday = isHoliday,
                isWorkSwap = isWorkSwap,
                isCurrentWeek = isCurrentWeek,
                showClassroom = showClassroom,
                showTeacher = showTeacher,
                cardTextScale = cardTextScale,
                isDark = isDark,
                isCourseCancelled = isCourseCancelled,
                courseTint = courseColors.tint,
            )
        }
        if (layeredSurface) {
            // 内容单独成层：采样节点每帧失效会触发整页 draw pass
            Box(modifier = Modifier.matchParentSize().graphicsLayer { }) {
                this.cardContent()
            }
        } else {
            this.cardContent()
        }
    }
}

/** 分层表面：垫底纯色 → 柔光玻璃 → 表面色，顺序不可对调。 */
private fun layeringSurfaceModifier(
    baseModifier: Modifier,
    cardShape: ContinuousRoundedRectangle,
    solidBackingColor: Color?,
    surfaceColor: Color,
    glassModifier: Modifier?,
): Modifier = baseModifier
    .then(
        if (solidBackingColor != null) Modifier.background(solidBackingColor, cardShape) else Modifier
    )
    .then(glassModifier ?: Modifier)
    .background(surfaceColor, cardShape)

/**
 * 柔光玻璃 modifier：静态壁纸采样优先，其次浮层用的实时录制层。
 * 无材质档或没有采样源时返回 null，调用方退回纯色。
 */
@Composable
private fun rememberGlassModifier(
    materialsEnabled: Boolean,
    softGlassSurface: SoftGlassSurface?,
    glassBackdrop: GlassBackdrop?,
    glassStyle: SoftGlassStyle?,
    cardShape: ContinuousRoundedRectangle,
    cornerRadius: Float,
    coords: Array<LayoutCoordinates?>,
    redraw: () -> Unit,
): Modifier? {
    if (!materialsEnabled) return null
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.dp.toPx() }

    val staticModifier = remember(softGlassSurface, radiusPx, density, redraw) {
        softGlassSurface?.modifier(
            density = density,
            radiusPx = radiusPx,
            redraw = redraw,
            // 位图已与屏幕对齐，采样坐标就是窗口坐标
            origin = {
                val layout = coords[0]
                if (layout != null && layout.isAttached) {
                    layout.positionInWindow()
                } else {
                    Offset.Zero
                }
            },
        )
    }
    if (staticModifier != null || glassBackdrop == null || glassStyle == null) return staticModifier
    // 浮层走实时录制：采样它下面的真实像素，课程卡才能透过玻璃露出来
    return Modifier.softGlassBackdrop(
        backdrop = glassBackdrop,
        shape = cardShape,
        style = glassStyle,
        radiusPx = radiusPx,
        redraw = redraw,
        blurSigmaPx = glassStyle.smallBlurRadius,
    )
}

/**
 * 课程卡手势：只判定轻点/长按，并把长按后的指针登记给页面级 tracker。
 *
 * 不消费按下事件，避免挡住页内滚动；真拖起来后的位移由 tracker 接管。
 */
private fun Modifier.courseCardGesture(
    course: Course,
    touchState: ScheduleTouchState?,
    coordinates: Array<LayoutCoordinates?>,
    bounds: FloatArray,
    refreshBounds: () -> Unit,
    scope: CoroutineScope,
    dragEnabled: Boolean,
    onSinkChange: (Boolean) -> Unit,
    onLongPressStart: (Float, Float, Float, Float) -> Unit,
    onClick: () -> Unit,
): Modifier = pointerInput(course, dragEnabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onSinkChange(true)
        val slop = viewConfiguration.touchSlop
        val horizontalSlop = 5.dp.toPx()
        val downRoot = rootOf(coordinates, down.position)
        var settled = false
        var tap = false

        // 计时只做一件事：确认长按并把指针交给 tracker
        val timer = scope.launch {
            delay(320.milliseconds)
            if (settled || touchState?.multiTouch == true) return@launch
            settled = true
            onSinkChange(false)
            refreshBounds()
            if (dragEnabled && touchState != null) touchState.begin(down.id, course.id, downRoot)
            onLongPressStart(bounds[0], bounds[1], bounds[2], bounds[3])
        }
        try {
            // 320ms 内抬手 = 轻点；横滑主导 / 位移超容差 = 让给翻页与滚动
            while (!settled) {
                val change = awaitPointerEvent(PointerEventPass.Final)
                    .changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    tap = (change.position - down.position).getDistance() < slop
                    break
                }
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (abs(dx) > horizontalSlop && abs(dx) > abs(dy) * 1.2f) break
                if (hypot(dx, dy) > slop) break
            }
            if (!settled) {
                settled = true
                onSinkChange(false)
                if (tap && touchState?.multiTouch != true) onClick()
            }
        } finally {
            timer.cancel()
        }
    }
}

/** 卡片本地坐标 → root：翻周 / 页内滚动都不会污染位移。 */
private fun rootOf(coordinates: Array<LayoutCoordinates?>, local: Offset): Offset {
    val coords = coordinates[0]
    return if (coords != null && coords.isAttached) coords.localToRoot(local) else local
}

@Composable
private fun BoxScope.CardContent(
    course: Course,
    textColor: Color,
    hasMultipleCourses: Boolean,
    isTablet: Boolean,
    cardContentAlignment: CardContentAlignment,
    cardHeightDp: Float,
    cardHeightPerSection: Float,
    isHoliday: Boolean,
    isWorkSwap: Boolean,
    isCurrentWeek: Boolean,
    showClassroom: Boolean,
    showTeacher: Boolean,
    cardTextScale: Float,
    isDark: Boolean,
    isCourseCancelled: Boolean,
    courseTint: Color,
) {
    val textScale = cardTextScale.coerceIn(0.5f, 2.0f)
    val infoFontSize = 11.sp * textScale
    val infoLineHeight = 12.sp * textScale
    val courseNameFontSize = 12.7.sp * textScale
    val courseNameLineHeight = 14.2.sp * textScale

    val effectiveShowClassroom = showClassroom && course.classroom.isNotEmpty()
    val effectiveShowTeacher = showTeacher && course.teacher.isNotEmpty()

    // 卡片高度能放下几行就几行
    val density = LocalDensity.current
    val availableHeightDp = cardHeightDp - 16f
    val courseNameLineH = with(density) { courseNameLineHeight.toDp().value }
    val infoLineH = with(density) { infoLineHeight.toDp().value }
    val reservedForInfo =
        ((if (effectiveShowClassroom) 1 else 0) + (if (effectiveShowTeacher) 1 else 0)) * infoLineH
    val nameMaxLines =
        ((availableHeightDp - reservedForInfo) / courseNameLineH).toInt().coerceAtLeast(1)
    val bodyMaxLines = nameMaxLines +
        (if (effectiveShowClassroom) 1 else 0) +
        (if (effectiveShowTeacher) 1 else 0)

    val textAlign = when (cardContentAlignment) {
        CardContentAlignment.TOP_START,
        CardContentAlignment.CENTER_START -> TextAlign.Start

        CardContentAlignment.TOP_CENTER,
        CardContentAlignment.CENTER_CENTER -> TextAlign.Center
    }
    val contentAlignment = when (cardContentAlignment) {
        CardContentAlignment.TOP_START -> Alignment.TopStart
        CardContentAlignment.TOP_CENTER -> Alignment.TopCenter
        CardContentAlignment.CENTER_START -> Alignment.CenterStart
        CardContentAlignment.CENTER_CENTER -> Alignment.Center
    }

    // 课名 + 地点 + 教师合成一段 Text，省掉多个 Text 与 Spacer
    val bodyText = remember(
        course.name,
        course.classroom,
        course.teacher,
        effectiveShowClassroom,
        effectiveShowTeacher,
        courseNameFontSize,
        infoFontSize,
    ) {
        buildAnnotatedString {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = courseNameFontSize))
            append(course.name)
            pop()
            if (effectiveShowClassroom) {
                append('\n')
                pushStyle(SpanStyle(fontSize = infoFontSize))
                append("@${course.classroom}")
                pop()
            }
            if (effectiveShowTeacher) {
                append('\n')
                pushStyle(SpanStyle(fontSize = infoFontSize))
                append(course.teacher)
                pop()
            }
        }
    }

    Text(
        text = bodyText,
        modifier = Modifier
            .fillMaxWidth()
            .align(contentAlignment)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        lineHeight = courseNameLineHeight,
        color = textColor,
        textAlign = textAlign,
        maxLines = bodyMaxLines,
        overflow = TextOverflow.Ellipsis,
    )

    if (hasMultipleCourses) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(if (isTablet) 6.dp else 5.dp)
                .size(8.dp)
                .background(color = textColor, shape = ContinuousCapsule())
        )
    }

    val badge = resolveCourseCardBadge(
        isHoliday = isHoliday,
        isCourseCancelled = isCourseCancelled,
        isWorkSwap = isWorkSwap,
        isCurrentWeek = isCurrentWeek,
    )
    if (badge != null) {
        val badgeBackground = if (badge.usesWorkSwapStyle) {
            courseTint.copy(alpha = 0.32f)
        } else {
            if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
        }
        Text(
            text = badge.text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                badge.usesWorkSwapStyle && MiuixTheme.isDynamicColor -> textColor
                badge.usesWorkSwapStyle -> if (isDark) Color.White.copy(alpha = 0.8f) else Color.White
                isDark -> Color.White.copy(alpha = 0.4f)
                else -> Color.Black.copy(alpha = 0.4f)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(if (isTablet) 6.dp else 5.dp)
                .clip(ContinuousRoundedRectangle(4.dp))
                .background(badgeBackground)
                .padding(horizontal = 2.dp, vertical = 1.dp),
            maxLines = 1,
            textAlign = TextAlign.End,
        )
    }

    if (course.hasValidCustomTime() && cardHeightDp >= 2 * cardHeightPerSection) {
        Text(
            text = course.customStartTime ?: "",
            fontSize = 8.sp,
            color = textColor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(if (isTablet) 6.dp else 5.dp)
                .clip(ContinuousRoundedRectangle(4.dp))
                .background(textColor.copy(alpha = 0.1f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            // 跨午夜标注「次日」，否则两个角标看着像两节课
            text = if (course.crossesMidnightCustomTime()) {
                SchedStr.nextDayTime(course.customEndTime ?: "")
            } else {
                course.customEndTime ?: ""
            },
            fontSize = 8.sp,
            color = textColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(if (isTablet) 6.dp else 5.dp)
                .clip(ContinuousRoundedRectangle(4.dp))
                .background(textColor.copy(alpha = 0.1f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** 把「底下一层纯色 + 上面一层纯色」的两次 SrcOver 叠加压成一次 drawRect。 */
internal fun flattenStackedFills(bottom: Color, top: Color): Color {
    val bottomAlpha = bottom.alpha
    val topAlpha = top.alpha
    val alpha = 1f - (1f - bottomAlpha) * (1f - topAlpha)
    if (alpha <= 0f) return Color.Transparent
    val bottomWeight = bottomAlpha * (1f - topAlpha) / alpha
    val topWeight = topAlpha / alpha
    return Color(
        red = bottom.red * bottomWeight + top.red * topWeight,
        green = bottom.green * bottomWeight + top.green * topWeight,
        blue = bottom.blue * bottomWeight + top.blue * topWeight,
        alpha = alpha,
    )
}

/** 超椭圆 outline 构建昂贵：仅尺寸/圆角/布局方向变化时重建。 */
internal class OutlineCache {
    var width: Float = Float.NaN
    var height: Float = Float.NaN
    var radius: Float = Float.NaN
    var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    var outline: Outline? = null
}

/** 卡片外沿的主题色描边；outline 外扩一个线宽再平移回来，内缘贴住卡片边界。 */
private fun ContentDrawScope.drawOngoingOutline(
    cache: OutlineCache,
    cornerRadius: Dp,
    color: Color,
) {
    val strokeWidthPx = OngoingOutlineWidth.toPx()
    val half = strokeWidthPx / 2f
    val sizeChanged = cache.width != size.width ||
        cache.height != size.height ||
        cache.radius != cornerRadius.value ||
        cache.layoutDirection != layoutDirection
    if (sizeChanged) {
        cache.outline = ContinuousRoundedRectangle(cornerRadius).createOutline(
            size.copy(width = size.width + strokeWidthPx, height = size.height + strokeWidthPx),
            layoutDirection,
            this,
        )
        cache.width = size.width
        cache.height = size.height
        cache.radius = cornerRadius.value
        cache.layoutDirection = layoutDirection
    }
    drawContent()
    // 形状没解出来也照常显示卡片内容，只是不描边
    cache.outline?.let { outline ->
        translate(left = -half, top = -half) {
            drawOutline(outline = outline, color = color, style = Stroke(strokeWidthPx))
        }
    }
}
