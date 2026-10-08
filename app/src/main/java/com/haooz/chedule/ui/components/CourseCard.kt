package com.haooz.chedule.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds
import android.graphics.Color as AndroidColor

/** 落地冲击波：中心坐标 + token，触发周围课程卡涟漪 */
data class LandRippleSpec(
    val center: Offset = Offset.Zero,
    val token: Int = 0,
)

val LocalLandRipple = compositionLocalOf { LandRippleSpec() }

// 按压：快速收一下，轻触也能立刻看到反馈；松手：带一点回弹，避免生硬
private const val SINK_SCALE = 0.94f
private val SinkPressSpec = tween<Float>(durationMillis = 90, easing = FastOutSlowInEasing)
private val SinkReleaseSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 480f)

@Composable
fun CourseCard(
    course: Course,
    isCurrentWeek: Boolean = true,
    isHoliday: Boolean = false,
    isCourseCancelled: Boolean = false,
    isWorkSwap: Boolean = false,
    hasMultipleCourses: Boolean = false,
    wallpaperBackdrop: Backdrop? = null,
    cardBlurRadius: Float = 4f,
    cardAlpha: Float = 0.15f,
    /** 有壁纸时白/黑底不透明度（卡片不透明度） */
    cardSurfaceAlpha: Float = 0.15f,
    /**
     * 垫在卡片**最底部**的纯色，通常由调用方传页面底色 @0.8f。
     *
     * 无壁纸时课程色自身只有 0.1~0.4 透明度，浮层悬在网格上会透出底下的格线，
     * 垫一层不透明度更高的底色才立得住。有壁纸时玻璃层采样的是不透明壁纸层，
     * 垫色会被整块盖住，所以一般只在无壁纸路径传。
     * null = 不垫（网格里的卡片走默认）。
     */
    solidBackingColor: Color? = null,
    cardHeightPerSection: Float = 54f,
    // 自定义时间课显式指定高度；null 时按节次数算
    customCardHeightDp: Float? = null,
    cardCornerRadius: Float = 10f,
    isTablet: Boolean = false,
    cardContentAlignment: com.haooz.chedule.data.CardContentAlignment = com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER,
    cardTextColor: com.haooz.chedule.data.CardTextColor = com.haooz.chedule.data.CardTextColor.COLORFUL,
    cardTextScale: Float = 1f,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    cardRefraction: com.haooz.chedule.data.CardRefractionLevel = com.haooz.chedule.data.CardRefractionLevel.DEFAULT,
    isDragging: Boolean = false,
    disablePadding: Boolean = false,
    isDark: Boolean = isAppDarkTheme(),
    // 非 state：滑动中坐标每帧变，跳过 localToRoot；读取不触发重组
    touchState: com.haooz.chedule.ui.screens.ScheduleTouchState? = null,
    onClick: () -> Unit,
    onLongPressStart: (cardLeft: Float, cardTop: Float, width: Float, height: Float) -> Unit = { _, _, _, _ -> },
    onDragStart: () -> Unit = {},
    onDrag: (offsetX: Float, offsetY: Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    onMenuDismiss: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val sectionCount = course.endSection - course.startSection + 1
    val cardHeight = (customCardHeightDp ?: (sectionCount * cardHeightPerSection)).dp
    val hasBlur = wallpaperBackdrop != null
    val effectiveCornerRadius = if (isTablet) (cardCornerRadius * 1.3f) else cardCornerRadius
    val scope = rememberCoroutineScope()
    val localDensity = LocalDensity.current

    // 近处几乎立刻、远处按距离铺开；token=0（常态）不挂协程，降低新周页首帧组合成本
    val landRipple = LocalLandRipple.current
    val rippleScale = remember { Animatable(1f) }
    // 涟漪动画期间为 true；token 不会归零，不能直接拿 token 判层
    var rippleAnimating by remember { mutableStateOf(false) }
    val cardBoundsPx = remember { FloatArray(4) }
    // 最近一次布局坐标：滑动中 onGloballyPositioned 跳过计算会留下过期/零点，
    // 长按瞬间按需重算一次，保证浮层/菜单锚点准确（无需每帧算，开销只在长按时）
    val cardCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val refreshCardBounds = {
        val c = cardCoords[0]
        if (c != null && c.isAttached) {
            val center = c.localToRoot(Offset(c.size.width / 2f, c.size.height / 2f))
            cardBoundsPx[0] = center.x
            cardBoundsPx[1] = center.y
            cardBoundsPx[2] = c.size.width.toFloat()
            cardBoundsPx[3] = c.size.height.toFloat()
        }
    }
    // Sink：仅缩放反馈
    var sinkPressed by remember { mutableStateOf(false) }
    var sinkActive by remember { mutableStateOf(false) }
    val sinkScale = remember { Animatable(1f) }
    LaunchedEffect(sinkPressed) {
        sinkActive = true
        sinkScale.animateTo(
            targetValue = if (sinkPressed) 0.94f else 1f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f)
        )
        if (!sinkPressed) sinkActive = false
    }

    val lastRippleToken = remember { mutableIntStateOf(landRipple.token) }
    if (landRipple.token != 0) {
        LaunchedEffect(landRipple.token) {
            if (landRipple.token == lastRippleToken.intValue) return@LaunchedEffect
            lastRippleToken.intValue = landRipple.token
            val cx = cardBoundsPx[0]
            val cy = cardBoundsPx[1]
            if (cx == 0f && cy == 0f) return@LaunchedEffect
            val dist = hypot(cx - landRipple.center.x, cy - landRipple.center.y)
            val delayMs = if (dist < 90f) {
                0L
            } else {
                ((dist - 90f) * 0.3f).toLong().coerceAtMost(400L)
            }
            if (delayMs > 0) delay(delayMs.milliseconds)
            rippleAnimating = true
            try {
                rippleScale.animateTo(1.08f, tween(80, easing = FastOutSlowInEasing))
                rippleScale.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            } finally {
                rippleAnimating = false
            }
        }
    }

    val effectiveAlpha = if (hasBlur) cardAlpha * 1.6f else cardAlpha
    val cardColor = remember(course.colorRes, isCurrentWeek, isHoliday, isCourseCancelled, effectiveAlpha) {
        if (isCurrentWeek && !isHoliday && !isCourseCancelled) {
            Color(course.colorRes).copy(alpha = effectiveAlpha)
        } else {
            Color(0xFF9E9E9E).copy(alpha = effectiveAlpha * 0.7f)
        }
    }
    val textColor = remember(
        course.colorRes,
        isCurrentWeek,
        isHoliday,
        isCourseCancelled,
        hasBlur,
        isDark,
        cardTextColor,
    ) {
        // 纯色模式仅本周课：黑白 0.74f；非本周/假期沿用灰色
        if (isCurrentWeek && !isHoliday && !isCourseCancelled &&
            cardTextColor == com.haooz.chedule.data.CardTextColor.SOLID
        ) {
            if (isDark) Color.White.copy(alpha = 0.74f) else Color.Black.copy(alpha = 0.74f)
        } else if (isCurrentWeek && !isHoliday && !isCourseCancelled) {
            if (hasBlur) Color(course.colorRes).let { c ->
                val hsv = FloatArray(3)
                AndroidColor.RGBToHSV((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv)
                if (isDark) {
                    hsv[1] = (hsv[1] * 0.5f).coerceIn(0f, 1f)
                    hsv[2] = (hsv[2] + 0.4f).coerceIn(0f, 1f)
                } else {
                    hsv[1] = (hsv[1] * 0.84f).coerceIn(0f, 1f)
                    hsv[2] = (hsv[2] + 0.5f).coerceIn(0f, 1f)
                }
                val boosted = AndroidColor.HSVToColor(hsv)
                Color(AndroidColor.red(boosted), AndroidColor.green(boosted), AndroidColor.blue(boosted))
            }
            else Color(course.colorRes)
        } else {
            if (hasBlur) {
                if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.3f)
            } else {
                Color(0xFF9E9E9E).copy(alpha = if (isDark) 0.28f else 0.45f)
            }
        }
    }

    if (hasBlur) {
        key(effectiveCornerRadius) {
            val backdropShape = remember(effectiveCornerRadius) { ContinuousRoundedRectangle(effectiveCornerRadius.dp) }
            val blurPx = with(localDensity) { remember(cardBlurRadius) { cardBlurRadius.dp.toPx() } }
            val lensRadiusPx = with(localDensity) { remember(cardRefraction) { cardRefraction.lensRadiusDp.dp.toPx() } }
            val lensStrengthPx = with(localDensity) { remember(cardRefraction) { cardRefraction.lensStrengthDp.dp.toPx() } }
            // 白/黑底由「卡片不透明度」控制；课程色由「卡片着色程度」控制
            val overlayColor = remember(isDark, cardSurfaceAlpha) {
                if (isDark) Color.Black.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
                else Color.White.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
            }
            val isSharedBlur = wallpaperBackdrop is SharedBlurBackdrop
            val backdropEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
                remember(isSharedBlur, blurPx, lensRadiusPx, lensStrengthPx, cardRefraction) {
                    {
                        if (!isSharedBlur) {
                            blur(blurPx)
                        }
                        if (cardRefraction != com.haooz.chedule.data.CardRefractionLevel.OFF) {
                            lens(lensRadiusPx, lensStrengthPx)
                        }
                    }
                }
            // onDrawSurface 每次重组新建会令 drawBackdrop 判不等，每次重录壁纸层并重跑 GPU 模糊
            val onCardSurface: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit =
                remember(cardColor, overlayColor) {
                    {
                        // 白/黑底在下，课程色在上
                        drawRect(overlayColor)
                        drawRect(cardColor)
                    }
                }
            val outlineColor = remember(cardColor) { cardColor.copy(alpha = 0.05f) }
            val outlineStroke = remember(localDensity) {
                androidx.compose.ui.graphics.drawscope.Stroke(with(localDensity) { 2.dp.toPx() })
            }
            val outlineCache = remember { OutlineCache() }
            // 静止卡不挂 graphicsLayer：几十张×两页的合成层是周滑 draw 热点
            val needTransformLayer = sinkPressed || sinkActive || isDragging || rippleAnimating

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .then(if (disablePadding) Modifier else Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
                    .then(
                        if (needTransformLayer) {
                            Modifier.graphicsLayer {
                                val s = sinkScale.value * rippleScale.value
                                scaleX = s
                                scaleY = s
                                alpha = if (isDragging) 0f else 1f
                            }
                        } else {
                            Modifier
                        }
                    )
                    .onGloballyPositioned { coordinates ->
                        cardCoords[0] = coordinates
                        if (touchState?.scrolling == true) return@onGloballyPositioned
                        val center = coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
                        cardBoundsPx[0] = center.x
                        cardBoundsPx[1] = center.y
                        cardBoundsPx[2] = coordinates.size.width.toFloat()
                        cardBoundsPx[3] = coordinates.size.height.toFloat()
                    }
                    // 垫底纯色画在采样层之前（更靠外=更早画），被玻璃采样盖住属预期
                    .then(
                        if (solidBackingColor != null) {
                            Modifier.background(solidBackingColor, backdropShape)
                        } else {
                            Modifier
                        }
                    )
                    .drawBackdrop(
                        backdrop = wallpaperBackdrop,
                        shape = { backdropShape },
                        effects = backdropEffects,
                        highlight = null,
                        shadow = null,
                        downsampleScale = 0.48f,
                        viewport = com.kyant.backdrop.LocalBackdropViewport.current,
                        onDrawSurface = onCardSurface
                    )
                    .drawWithContent {
                        drawContent()
                        val radiusDp = effectiveCornerRadius.dp
                        if (outlineCache.width != size.width ||
                            outlineCache.height != size.height ||
                            outlineCache.radius != radiusDp.value ||
                            outlineCache.layoutDirection != layoutDirection
                        ) {
                            outlineCache.outline = ContinuousRoundedRectangle(radiusDp)
                                .createOutline(size, layoutDirection, this)
                            outlineCache.width = size.width
                            outlineCache.height = size.height
                            outlineCache.radius = radiusDp.value
                            outlineCache.layoutDirection = layoutDirection
                        }
                        drawOutline(
                            outline = outlineCache.outline!!,
                            color = outlineColor,
                            style = outlineStroke
                        )
                    }
                    .courseCardGesture(
                        course = course,
                        touchState = touchState,
                        bounds = cardBoundsPx,
                        refreshBounds = refreshCardBounds,
                        scope = scope,
                        onSinkChange = { sinkPressed = it },
                        onLongPressStart = onLongPressStart,
                        onClick = onClick,
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onMenuDismiss = onMenuDismiss,
                    ),
                contentAlignment = Alignment.Center
            ) {
                CardContent(course, sectionCount, textColor, hasMultipleCourses,
                    isTablet, cardContentAlignment, cardHeight.value, cardHeightPerSection,
                    isHoliday, isWorkSwap, isCurrentWeek, showClassroom, showTeacher, cardTextScale, isDark,
                    isCourseCancelled)
            }
        }
    } else {
        val cardShape = remember(effectiveCornerRadius) {
            ContinuousRoundedRectangle(effectiveCornerRadius.dp)
        }
        // 静止卡不挂 graphicsLayer：几十张×两页的合成层是左右滑 draw 热点。
        // 仅按压/拖拽/落地涟漪动画时挂；松手即撤，缩放回弹让位给无层路径。
        val needTransformLayer = sinkPressed || sinkActive || isDragging || rippleAnimating
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(cardHeight)
                .then(if (disablePadding) Modifier else Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
                .then(
                    if (needTransformLayer) {
                        Modifier.graphicsLayer {
                            val s = sinkScale.value * rippleScale.value
                            scaleX = s
                            scaleY = s
                            alpha = if (isDragging) 0f else 1f
                            shape = cardShape
                            clip = true
                        }
                    } else {
                        Modifier
                    }
                )
                // 垫底纯色在下、课程色在上（链条越靠前越先画）
                .then(
                    if (solidBackingColor != null) Modifier.background(solidBackingColor, cardShape)
                    else Modifier
                )
                .background(cardColor, cardShape)
                .onGloballyPositioned { coordinates ->
                    // 与 hasBlur 分支一致：滑动中跳过 localToRoot，避免几十张卡每帧算坐标
                    cardCoords[0] = coordinates
                    if (touchState?.scrolling == true) return@onGloballyPositioned
                    val center = coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
                    cardBoundsPx[0] = center.x
                    cardBoundsPx[1] = center.y
                    cardBoundsPx[2] = coordinates.size.width.toFloat()
                    cardBoundsPx[3] = coordinates.size.height.toFloat()
                }
                .courseCardGesture(
                    course = course,
                    touchState = touchState,
                    bounds = cardBoundsPx,
                    refreshBounds = refreshCardBounds,
                    scope = scope,
                    onSinkChange = { sinkPressed = it },
                    onLongPressStart = onLongPressStart,
                    onClick = onClick,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onMenuDismiss = onMenuDismiss,
                )
        ) {
            CardContent(course, sectionCount, textColor, hasMultipleCourses,
                isTablet, cardContentAlignment, cardHeight.value, cardHeightPerSection,
                isHoliday, isWorkSwap, isCurrentWeek, showClassroom, showTeacher, cardTextScale, isDark,
                isCourseCancelled)
        }
    }
}

/**
 * 课程卡手势：有壁纸 / 无壁纸两条渲染路径共用同一实现，避免各改一份产生漂移。
 *
 * - 轻点（位移 < 8dp）→ [onClick]
 * - 长按 320ms → [onLongPressStart] 弹菜单，随后任意移动进入拖拽
 * - 长按前横滑主导 / 位移超容差 / 多指按下 → 放弃本次手势，把滚动让给 Pager
 */
private fun Modifier.courseCardGesture(
    course: Course,
    touchState: com.haooz.chedule.ui.screens.ScheduleTouchState?,
    bounds: FloatArray,
    refreshBounds: () -> Unit,
    scope: CoroutineScope,
    onSinkChange: (Boolean) -> Unit,
    onLongPressStart: (left: Float, top: Float, width: Float, height: Float) -> Unit,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (offsetX: Float, offsetY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onMenuDismiss: () -> Unit,
): Modifier = pointerInput(course) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        onSinkChange(true)
        val downPosition = down.position
        val slop = 8.dp.toPx()
        val horizontalSlop = 5.dp.toPx()
        var isLongPress = false
        var isDragging = false
        var menuShown = false
        val longPressJob = scope.launch {
            delay(320.milliseconds)
            // 多指（三指截图等）不当成长按
            if (touchState?.multiTouch == true) return@launch
            isLongPress = true
            onSinkChange(false)
            menuShown = true
            refreshBounds()
            onLongPressStart(bounds[0], bounds[1], bounds[2], bounds[3])
        }
        try {
            while (true) {
                // 菜单/拖拽需要拦穿透用 Main pass；否则 Final pass 让父级滚动先处理
                val pass = if (menuShown || isDragging) PointerEventPass.Main else PointerEventPass.Final
                val event = awaitPointerEvent(pass)

                if (event.changes.none { it.pressed }) {
                    onSinkChange(false)
                    when {
                        isDragging -> if (menuShown) onDrag(0f, 0f) else onDragEnd()
                        menuShown -> Unit
                        else -> {
                            val up = event.changes.firstOrNull()
                            if (up != null) {
                                up.consume()
                                // 多指时不点击：系统手势（三指截图）拦截后 Compose 会补发一个
                                // 合成的「全部抬起」事件，看起来和松手一样，只能靠页面级多指标记区分
                                val multiTouch = touchState?.multiTouch == true
                                if (!isLongPress && !multiTouch &&
                                    (up.position - downPosition).getDistance() < slop
                                ) onClick()
                            }
                        }
                    }
                    break
                }

                val pos = event.changes.firstOrNull()?.position ?: continue
                val dx = pos.x - downPosition.x
                val dy = pos.y - downPosition.y
                val moved = pos.minus(downPosition).getDistance()

                // 长按前横滑主导 → 交给 Pager 翻页
                if (!isLongPress && !isDragging && !menuShown &&
                    abs(dx) > horizontalSlop && abs(dx) > abs(dy) * 1.2f
                ) {
                    onSinkChange(false)
                    break
                }
                // 长按前位移超容差 → 取消长按
                if (!isLongPress && moved > slop) {
                    onSinkChange(false)
                    event.changes.forEach { it.consume() }
                    break
                }
                // 菜单已弹出：转为拖拽；位移超容差则收起菜单（拖拽浮层保留）
                if (menuShown && !isDragging) {
                    isDragging = true
                    onSinkChange(false)
                    onDragStart()
                }
                if (menuShown && moved > slop) {
                    menuShown = false
                    onMenuDismiss()
                }
                if (isDragging) onDrag(dx, dy)
                event.changes.forEach { it.consume() }
            }
        } finally {
            longPressJob.cancel()
        }
    }
}

@Composable
private fun CardContent(course: Course, sectionCount: Int, textColor: Color, hasMultipleCourses: Boolean,
                        isTablet: Boolean = false, cardContentAlignment: com.haooz.chedule.data.CardContentAlignment = com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER,
                         cardHeightDp: Float = 0f, cardHeightPerSection: Float = 54f,
                         isHoliday: Boolean = false, isWorkSwap: Boolean = false, isCurrentWeek: Boolean = true,
                         showClassroom: Boolean = true, showTeacher: Boolean = true, cardTextScale: Float = 1f,
                         isDark: Boolean = isAppDarkTheme(), isCourseCancelled: Boolean = false) {
    val infoFontSize = 11.sp * cardTextScale.coerceIn(0.5f, 2.0f)
    val infoLineHeight = 12.sp * cardTextScale.coerceIn(0.5f, 2.0f)
    val courseNameFontSize = 12.7.sp * cardTextScale.coerceIn(0.5f, 2.0f)
    val courseNameLineHeight = 14.2.sp * cardTextScale.coerceIn(0.5f, 2.0f)

    val effectiveShowClassroom = showClassroom && course.classroom.isNotEmpty()
    val effectiveShowTeacher = showTeacher && course.teacher.isNotEmpty()

    // 卡片高度能放下几行就几行（单 Text 后按总行数限）
    val density = LocalDensity.current
    val availableHeightDp = cardHeightDp - 16f
    val courseNameLineH = with(density) { courseNameLineHeight.toDp().value }
    val infoLineH = with(density) { infoLineHeight.toDp().value }
    val reservedForInfo = ((if (effectiveShowClassroom) 1 else 0) + (if (effectiveShowTeacher) 1 else 0)) * infoLineH
    val nameMaxLines = ((availableHeightDp - reservedForInfo) / courseNameLineH).toInt().coerceAtLeast(1)
    val bodyMaxLines = nameMaxLines +
        (if (effectiveShowClassroom) 1 else 0) +
        (if (effectiveShowTeacher) 1 else 0)

    Box(modifier = Modifier.fillMaxSize()) {
        val verticalArrangement = when (cardContentAlignment) {
            com.haooz.chedule.data.CardContentAlignment.TOP_START,
            com.haooz.chedule.data.CardContentAlignment.TOP_CENTER -> Arrangement.Top
            com.haooz.chedule.data.CardContentAlignment.CENTER_START,
            com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER -> Arrangement.Center
        }
        val horizontalAlignment = when (cardContentAlignment) {
            com.haooz.chedule.data.CardContentAlignment.TOP_START,
            com.haooz.chedule.data.CardContentAlignment.CENTER_START -> Alignment.Start
            com.haooz.chedule.data.CardContentAlignment.TOP_CENTER,
            com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER -> Alignment.CenterHorizontally
        }
        val textAlign = when (cardContentAlignment) {
            com.haooz.chedule.data.CardContentAlignment.TOP_START,
            com.haooz.chedule.data.CardContentAlignment.CENTER_START -> TextAlign.Start
            com.haooz.chedule.data.CardContentAlignment.TOP_CENTER,
            com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER -> TextAlign.Center
        }
        // 课名+地点+教师合成一段 Text：省掉每卡 2 个 Text + 2 个 Spacer 的测量/绘制节点
        val bodyText = androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = courseNameFontSize,
                )
            )
            append(course.name)
            pop()
            if (effectiveShowClassroom) {
                append('\n')
                pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = infoFontSize))
                append("@${course.classroom}")
                pop()
            }
            if (effectiveShowTeacher) {
                append('\n')
                pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = infoFontSize))
                append(course.teacher)
                pop()
            }
        }
        // 行高用课名（较大者），保证换行间距与原多 Text 布局接近
        val bodyLineHeight = courseNameLineHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement
        ) {
            Text(
                text = bodyText,
                lineHeight = bodyLineHeight,
                color = textColor,
                textAlign = textAlign,
                maxLines = bodyMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (hasMultipleCourses) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (isTablet) 6.dp else 5.dp)
                    .size(8.dp)
                    .background(color = textColor, shape = CircleShape)
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
                Color(course.colorRes).copy(alpha = 0.32f)
            } else {
                if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
            }
            Text(
                text = badge.text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = if (badge.usesWorkSwapStyle) {
                    if (isDark) Color.White.copy(alpha = 0.8f) else Color.White
                } else {
                    if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f)},
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (isTablet) 6.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeBackground)
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                maxLines = 1
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
                    .clip(RoundedCornerShape(4.dp))
                    .background(textColor.copy(alpha = 0.1f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                maxLines = 1
            )
            Text(
                text = course.customEndTime ?: "",
                fontSize = 8.sp,
                color = textColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(if (isTablet) 6.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(textColor.copy(alpha = 0.1f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                maxLines = 1
            )
        }
    }
}

// 超椭圆 outline 构建昂贵，滑动时约 40~80 张×2 页；仅尺寸/圆角/layoutDirection 变时重建
internal class OutlineCache {
    var width: Float = Float.NaN
    var height: Float = Float.NaN
    var radius: Float = Float.NaN
    var layoutDirection: androidx.compose.ui.unit.LayoutDirection =
        androidx.compose.ui.unit.LayoutDirection.Ltr
    var outline: androidx.compose.ui.graphics.Outline? = null
}
