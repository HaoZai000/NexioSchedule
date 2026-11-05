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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.haooz.chedule.data.appearance.CardRefractionLevel
import com.haooz.chedule.data.appearance.CardTextColor
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.components.LocalGlassRedrawSignal
import com.haooz.chedule.ui.components.SoftGlassSurface
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.theme.courseCardTextColor
import com.haooz.chedule.ui.theme.rememberCourseColors
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 落地冲击波：中心坐标 + token，触发周围课程卡涟漪 */
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

@Composable
fun CourseCard(
    course: Course,
    isCurrentWeek: Boolean = true,
    isHoliday: Boolean = false,
    isCourseCancelled: Boolean = false,
    isWorkSwap: Boolean = false,
    hasMultipleCourses: Boolean = false,
    softGlassSurface: SoftGlassSurface? = null,
    /** 玻璃采样源模糊（**px**，直接喂 smallBlurRadius）；这里只做透传，卡片自身不用 */
    cardBlurRadius: Float = 32f,
    cardAlpha: Float = 0.15f,
    /** 有壁纸时白/黑底不透明度（卡片不透明度） */
    cardSurfaceAlpha: Float = 0.40f,
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
    cardContentAlignment: CardContentAlignment = CardContentAlignment.CENTER_CENTER,
    cardTextColor: CardTextColor = CardTextColor.COLORFUL,
    cardTextScale: Float = 1f,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    cardRefraction: CardRefractionLevel = CardRefractionLevel.DEFAULT,
    isDragging: Boolean = false,
    /**
     * 长按后是否允许接着拖动（调课）。
     *
     * ⚠ 自定义时间课传 false：它按时间轴定位、不存在「挪到第几节」的目标，
     * 拖出去也无处可放（还会顺带把「复制」带出来，而复制同样依赖节次跨度）。
     * 这类课长按只做一件事：弹「编辑 / 删除」菜单。
     */
    dragEnabled: Boolean = true,
    disablePadding: Boolean = false,
    isDark: Boolean = isAppDarkTheme(),
    // 非 state：滑动中坐标每帧变，跳过 localToRoot；读取不触发重组
    touchState: ScheduleTouchState? = null,
    onClick: () -> Unit,
    onLongPressStart: (cardLeft: Float, cardTop: Float, width: Float, height: Float) -> Unit = { _, _, _, _ -> },
    onDragStart: () -> Unit = {},
    onDrag: (offsetX: Float, offsetY: Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    /** 拖拽被系统/布局回收打断（不是用户松手）：只收浮层，不写库 */
    onDragCancel: () -> Unit = {},
    onMenuDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sectionCount = course.endSection - course.startSection + 1
    val cardHeight = (customCardHeightDp ?: (sectionCount * cardHeightPerSection)).dp
    // 柔光只需要壁纸位图（softGlassSurface 内部自采），不需要 kyant 的共享层。
    val hasBlur = softGlassSurface != null
    val effectiveCornerRadius = if (isTablet) (cardCornerRadius * 1.3f) else cardCornerRadius
    val scope = rememberCoroutineScope()
    val localDensity = LocalDensity.current
    // 滚动/翻页信号：让玻璃的采样原点按帧跟手（见 LocalGlassRedrawSignal 注释）
    val glassRedraw = LocalGlassRedrawSignal.current

    // 近处几乎立刻、远处按距离铺开；token=0（常态）不挂协程，降低新周页首帧组合成本
    val landRipple = LocalLandRipple.current
    // 缩放 / 涟漪两个动画量惰性持有，静止卡不建
    val anims = remember { CardAnims() }
    // 涟漪动画期间为 true；token 不会归零，不能直接拿 token 判层
    var rippleAnimating by remember { mutableStateOf(false) }
    val cardBoundsPx = remember { FloatArray(4) }
    // 只缓存布局坐标，root 位置按需现算
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
    LaunchedEffect(sinkPressed) {
        // 首帧是非按压态，直接收摊：否则每张卡会白跑一次 spring + 两次重组 + 加/撤图层
        if (!sinkPressed && !sinkActive) return@LaunchedEffect
        sinkActive = true
        anims.sinkScale.animateTo(
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
            // 边界按需现算（见 onGloballyPositioned 的注释）
            refreshCardBounds()
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
                anims.rippleScale.animateTo(1.08f, tween(80, easing = FastOutSlowInEasing))
                anims.rippleScale.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            } finally {
                rippleAnimating = false
            }
        }
    }

    val effectiveAlpha = (if (hasBlur) cardAlpha * 1.6f else cardAlpha).coerceIn(0f, 1f)
    val courseColors = rememberCourseColors(course, isDark)
    val useMonet = MiuixTheme.isDynamicColor
    val inactiveColor =
        if (useMonet) MiuixTheme.colorScheme.onSurfaceVariantActions else Color(0xFF9E9E9E)
    val cardColor = remember(
        courseColors.tint,
        inactiveColor,
        isCurrentWeek,
        isHoliday,
        isCourseCancelled,
        effectiveAlpha
    ) {
        if (isCurrentWeek && !isHoliday && !isCourseCancelled) {
            courseColors.tint.copy(alpha = effectiveAlpha)
        } else {
            inactiveColor.copy(alpha = effectiveAlpha * 0.7f)
        }
    }
    val themeBackground = MiuixTheme.colorScheme.background
    val monetCardBackground =
        remember(cardColor, solidBackingColor, themeBackground, hasBlur, cardSurfaceAlpha, isDark) {
            val backing = solidBackingColor?.compositeOver(themeBackground) ?: themeBackground
            val surface = cardColor.compositeOver(backing)
            if (hasBlur) {
                (if (isDark) Color.Black else Color.White)
                    .copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f)).compositeOver(surface)
            } else surface
        }
    val textColor = remember(
        courseColors,
        useMonet,
        inactiveColor,
        monetCardBackground,
        isCurrentWeek,
        isHoliday,
        isCourseCancelled,
        hasBlur,
        isDark,
        cardTextColor,
    ) {
        // 推导在 ui/theme/ScheduleColors.kt，时间轴卡片共用同一份
        courseCardTextColor(
            courseColors = courseColors,
            useMonet = useMonet,
            inactiveColor = inactiveColor,
            background = monetCardBackground,
            isCurrentWeek = isCurrentWeek,
            isHoliday = isHoliday,
            isCourseCancelled = isCourseCancelled,
            hasBlur = hasBlur,
            isDark = isDark,
            cardTextColor = cardTextColor,
        )
    }

    if (hasBlur) {
        key(effectiveCornerRadius) {
            val backdropShape =
                remember(effectiveCornerRadius) { ContinuousRoundedRectangle(effectiveCornerRadius.dp) }
            // 白/黑底由「卡片不透明度」控制；课程色由「卡片着色程度」控制
            val overlayColor = remember(isDark, cardSurfaceAlpha) {
                if (isDark) Color.Black.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
                else Color.White.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
            }

            // 白/黑底 + 课程色压成一次 drawRect（两次全卡 fill 是每卡每帧的固定成本）。
            // ⚠ 顺序固定为「白/黑垫底、课程色在上」，别对调：课程色压在上面是既有观感，
            // 换了整卡会亮一档。代价是不透明度对最终像素的贡献被课程色稀释（×0.85），
            // 所以设置里的百分比指的是这一层自身的 alpha，不是最终像素里的占比。
            val surfaceColor = remember(cardColor, overlayColor) {
                flattenStackedFills(overlayColor, cardColor)
            }
            // 静止卡不挂 graphicsLayer：几十张×两页的合成层是周滑 draw 热点
            val needTransformLayer = sinkPressed || sinkActive || isDragging || rippleAnimating
            val glassRadiusPx = with(localDensity) { effectiveCornerRadius.dp.toPx() }
            // 柔光玻璃：采样原点是「卡片在壁纸位图里的坐标」= 窗口坐标 × 位图缩放。
            // 位置在绘制期现取（onGloballyPositioned 回填的是普通字段，不能组合期读）。
            // 必须 remember：每次重组新建 Modifier 会让 drawWithCache 缓存失效、
            // AGSL 跟着重编译。surface 与圆角不变时复用同一个实例，采样原点仍逐帧现取。
            val softGlassModifier = remember(softGlassSurface, glassRadiusPx, localDensity, glassRedraw) {
                softGlassSurface.modifier(
                    density = localDensity,
                    radiusPx = glassRadiusPx,
                    redraw = glassRedraw,
                    origin = {
                        val c = cardCoords[0]
                        if (c != null && c.isAttached) {
                            val p = c.positionInWindow()
                            // 位图已与屏幕对齐（同原点同尺寸），采样坐标就是窗口坐标
                            Offset(p.x, p.y)
                        } else {
                            Offset.Zero
                        }
                    },
                )
            }
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .then(
                        if (disablePadding) Modifier else Modifier.padding(
                            horizontal = 2.dp,
                            vertical = 2.dp
                        )
                    )
                    .then(
                        if (needTransformLayer) {
                            Modifier.graphicsLayer {
                                val s = anims.sinkScale.value * anims.rippleScale.value
                                scaleX = s
                                scaleY = s
                                alpha = if (isDragging) 0f else 1f
                            }
                        } else {
                            Modifier
                        }
                    )
                    // 只存坐标：布局期不再逐卡 localToRoot，消费点自己 refreshCardBounds
                    .onGloballyPositioned { cardCoords[0] = it }
                    // 垫底纯色画在采样层之前（更靠外=更早画），被玻璃采样盖住属预期
                    .then(
                        if (solidBackingColor != null) {
                            Modifier.background(solidBackingColor, backdropShape)
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        // 柔光玻璃不可用（无壁纸 / 非 Android）时直接铺表面色。
                        // 柔光自带折射 + 着色 + 描边，表面色仍压在最上面。
                        if (softGlassModifier != null) {
                            Modifier
                                .then(softGlassModifier)
                                .background(surfaceColor, backdropShape)
                        } else {
                            Modifier.background(surfaceColor, backdropShape)
                        }
                    )
                    .courseCardGesture(
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
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                        onMenuDismiss = onMenuDismiss,
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 内容单独成层：采样节点每帧失效会触发整页 draw pass
                // 与上面 needTransformLayer 不冲突：那是拖拽缩放的**变换层**（按需挂），
                // 这是常驻的**内容缓存层**，位置不同（内容层在采样层内侧），职责也不同。
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { }
                ) {
                    CardContent(
                        course,
                        textColor,
                        hasMultipleCourses,
                        isTablet,
                        cardContentAlignment,
                        cardHeight.value,
                        cardHeightPerSection,
                        isHoliday,
                        isWorkSwap,
                        isCurrentWeek,
                        showClassroom,
                        showTeacher,
                        cardTextScale,
                        isDark,
                        isCourseCancelled,
                        courseTint = courseColors.tint
                    )
                }
            }
        }
    } else {
        val cardShape = remember(effectiveCornerRadius) {
            ContinuousRoundedRectangle(effectiveCornerRadius.dp)
        }
        // 静止卡不挂 graphicsLayer：几十张×两页的合成层是左右滑 draw 热点。
        // 仅按压/拖拽/落地涟漪动画时挂；松手即撤，缩放回弹让位给无层路径。
        val needTransformLayer = sinkPressed || sinkActive || isDragging || rippleAnimating
        // 垫底纯色 + 课程色两次填充压成一次（链条越靠前越先画，与 flattenStackedFills 同向）
        val filledCardColor = remember(solidBackingColor, cardColor) {
            if (solidBackingColor != null) flattenStackedFills(solidBackingColor, cardColor)
            else cardColor
        }
        // 纯色模式（无壁纸）不画描边：底色本身就和页面背景分得开，那圈线只会显脏
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(cardHeight)
                .then(
                    if (disablePadding) Modifier else Modifier.padding(
                        horizontal = 2.dp,
                        vertical = 2.dp
                    )
                )
                .then(
                    if (needTransformLayer) {
                        Modifier.graphicsLayer {
                            val s = anims.sinkScale.value * anims.rippleScale.value
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
                .background(filledCardColor, cardShape)
                // 与 hasBlur 分支一致：只存坐标，root 位置按需现算
                .onGloballyPositioned { cardCoords[0] = it }
                .courseCardGesture(
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
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    onMenuDismiss = onMenuDismiss,
                )
        ) {
            CardContent(
                course,
                textColor,
                hasMultipleCourses,
                isTablet,
                cardContentAlignment,
                cardHeight.value,
                cardHeightPerSection,
                isHoliday,
                isWorkSwap,
                isCurrentWeek,
                showClassroom,
                showTeacher,
                cardTextScale,
                isDark,
                isCourseCancelled,
                courseTint = courseColors.tint
            )
        }
    }
}

/**
 * 课程卡手势：有壁纸 / 无壁纸两条渲染路径共用同一实现，避免各改一份产生漂移。
 *
 * - 轻点（位移 < 8dp）→ [onClick]
 * - 长按 320ms → [onLongPressStart] 弹菜单，随后移动即进拖拽态（浮层跟手）
 * - 拖拽态里位移超过容差（8dp）才算真的拖起来：此时才 [onDragStart]（点亮跟手高光）
 *   并 [onMenuDismiss] 收菜单（[dragEnabled] 为 false 时这两步都不发生）
 * - 长按前横滑主导 / 位移超容差 / 多指按下 → 放弃本次手势，把滚动让给 Pager
 *
 * ⚠ [dragEnabled] = false 时菜单**不随移动收起**：这类课唯一的操作入口就是菜单，
 * 手指抖一下就把菜单收掉等于没法用。松手后菜单继续留在屏幕上等用户点。
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
    onLongPressStart: (left: Float, top: Float, width: Float, height: Float) -> Unit,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (offsetX: Float, offsetY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onMenuDismiss: () -> Unit,
): Modifier = pointerInput(course, dragEnabled) {
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
        // 拖拽已经接管 / 已经收过尾。节点被回收时 finally 只兜底一次，正常松手不重复收尾。
        var dragOwned = false
        var dragClosed = false

        /**
         * 指针位置换算到 root 坐标。
         *
         * ⚠ 浮层挂在 root 上、锚点 [bounds] 也是 root 坐标，而 [androidx.compose.ui.input.pointer.PointerInputChange.position]
         * 是**课卡本地**坐标。课卡住在周 pager 的页里：翻周（页平移一屏）、页内上下滚，
         * 手指不动本地坐标也会变 —— 直接拿本地差值会让浮层反向飞出一屏（翻页即「丢失」），
         * 上下滚则越拖越偏（不跟手）。换算到 root 后只剩手指自身位移。
         */
        fun rootPosition(local: Offset): Offset {
            val coords = coordinates[0]
            return if (coords != null && coords.isAttached) coords.localToRoot(local) else local
        }

        val downRoot = rootPosition(downPosition)
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
                val pass =
                    if (menuShown || isDragging) PointerEventPass.Main else PointerEventPass.Final
                val event = awaitPointerEvent(pass)
                // 只认按下时那根指针：其余手指的事件不消费，留给页面翻周手势
                // （跨周调课要一只手拖课、另一只手翻周）
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    // 指针不在事件里 = 被系统取消（翻页时合成 cancel / 手势被抢）。
                    // 必须收尾，否则浮层 offset 冻在最后一帧、卡在屏幕外。
                    if (dragOwned) {
                        dragClosed = true
                        val trackerOwns = touchState != null &&
                            (touchState.dragTrackerActive || touchState.dragEndHandled)
                        if (!trackerOwns) onDragCancel()
                    }
                    break
                }

                if (!change.pressed) {
                    onSinkChange(false)
                    when {
                        isDragging -> {
                            // 页面级 tracker 已接管 / 已收尾：位移与落点都归它，
                            // 这里再调一次 onDragEnd 会把同一次拖拽写两遍
                            val st = touchState
                            val trackerOwns = st != null &&
                                (st.dragTrackerActive || st.dragEndHandled)
                            when {
                                trackerOwns -> dragClosed = true
                                menuShown -> {
                                    // 菜单还在、还没到「真拖起来」：把抖动带出的偏移归零，菜单继续留着
                                    onDrag(0f, 0f)
                                    dragClosed = true
                                }

                                else -> {
                                    dragClosed = true
                                    onDragEnd()
                                }
                            }
                        }

                        menuShown -> Unit
                        else -> {
                            change.consume()
                            // 多指时不点击：系统手势（三指截图）拦截后 Compose 会补发一个
                            // 合成的「全部抬起」事件，看起来和松手一样，只能靠页面级多指标记区分
                            val multiTouch = touchState?.multiTouch == true
                            if (!isLongPress && !multiTouch &&
                                (change.position - downPosition).getDistance() < slop
                            ) onClick()
                        }
                    }
                    break
                }

                val pos = change.position
                // 长按前用本地位移：页面惯性/另只手滚屏时内容在手指下跑，
                // 本地坐标能如实反映「手指相对卡片动了」，避免把滚动当成长按/拖拽。
                val localDx = pos.x - downPosition.x
                val localDy = pos.y - downPosition.y
                val localMoved = Offset(localDx, localDy).getDistance()
                // 长按后（真拖拽）用 root 位移：翻周/滚动时本地坐标会被整页平移污染
                val rootNow = rootPosition(pos)
                val dx = rootNow.x - downRoot.x
                val dy = rootNow.y - downRoot.y
                val moved = Offset(dx, dy).getDistance()

                // 长按前横滑主导 → 交给 Pager 翻页
                if (!isLongPress && !isDragging && !menuShown &&
                    abs(localDx) > horizontalSlop && abs(localDx) > abs(localDy) * 1.2f
                ) {
                    onSinkChange(false)
                    break
                }
                // 长按前位移超容差 → 取消长按
                if (!isLongPress && localMoved > slop) {
                    onSinkChange(false)
                    change.consume()
                    break
                }
                // 菜单已弹出：手指一动就进「拖拽态」，浮层开始跟手。
                // ⚠ 这里**不**调 onDragStart —— 它点亮的是跟手高光，抖动几像素就亮
                //   会很廉价；高光留给下面真正「拖起来」的那一刻。
                // ⚠ dragEnabled = false（自定义时间课）时整段跳过：菜单留在原地等用户点
                if (dragEnabled && menuShown && !isDragging) {
                    isDragging = true
                    dragOwned = true
                    onSinkChange(false)
                    // 登记拖拽占用的指针：翻页手势只屏蔽这一根，另一只手仍能翻周
                    val st = touchState
                    if (st != null) {
                        // 起拖前的根坐标：tracker 用它把位移算成「相对按下时」，
                        // 这样交接触手时浮层不会跳。翻转/滚动都不影响 root 差值。
                        st.draggingPointerId = down.id
                        st.draggingCourseId = course.id
                        st.draggingDownRoot = downRoot
                        st.dragStarted = false
                        st.dragTrackerActive = false
                        st.dragEndHandled = false
                    }
                }
                // 位移超容差 = 真的拖起来了：此刻收菜单，并点亮跟手高光
                if (dragEnabled && menuShown && moved > slop) {
                    menuShown = false
                    touchState?.dragStarted = true
                    onDragStart()
                    onMenuDismiss()
                }
                // 页面级 tracker 接管后，卡片不再上报位移（两边都算会翻倍）
                if (dragEnabled && isDragging && touchState?.dragTrackerActive != true) {
                    onDrag(dx, dy)
                }
                change.consume()
            }
        } finally {
            longPressJob.cancel()
            // 课卡所在周页被 pager 回收时协程直接 cancel，走不到上面的抬起分支：
            // 浮层不收尾会永远停在最后一帧。页面级 tracker 还活着时由它收尾（它能跨周跟手），
            // 只有 tracker 没接管才在这里兜底；且只取消、不写库。
            if (dragOwned && !dragClosed) {
                val trackerOwns = touchState != null &&
                    (touchState.dragTrackerActive || touchState.dragEndHandled)
                if (!trackerOwns) onDragCancel()
            }
            if (touchState != null &&
                !touchState.dragTrackerActive && !touchState.dragEndHandled &&
                touchState.draggingPointerId == down.id
            ) {
                touchState.clearDragTracking()
            }

        }
    }
}

@Composable
private fun BoxScope.CardContent(
    course: Course,
    textColor: Color,
    hasMultipleCourses: Boolean,
    isTablet: Boolean = false,
    cardContentAlignment: CardContentAlignment = CardContentAlignment.CENTER_CENTER,
    cardHeightDp: Float = 0f,
    cardHeightPerSection: Float = 54f,
    isHoliday: Boolean = false,
    isWorkSwap: Boolean = false,
    isCurrentWeek: Boolean = true,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    cardTextScale: Float = 1f,
    isDark: Boolean = isAppDarkTheme(),
    isCourseCancelled: Boolean = false,
    courseTint: Color = Color(course.colorRes)
) {
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
    // 只定竖直贴顶/居中 + 水平锚点：宽度由 fillMaxWidth 撑满，水平对齐交给 textAlign
    val contentAlignment = when (cardContentAlignment) {
        CardContentAlignment.TOP_START -> Alignment.TopStart
        CardContentAlignment.TOP_CENTER -> Alignment.TopCenter
        CardContentAlignment.CENTER_START -> Alignment.CenterStart
        CardContentAlignment.CENTER_CENTER -> Alignment.Center
    }

    // 课名+地点+教师合成一段 Text（省 2 个 Text + 2 个 Spacer）；必须 remember，别每次重组重建
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
            pushStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = courseNameFontSize,
                )
            )
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

    // 正文直接挂在课卡 Box 上（居中 + 内边距由 align/padding 完成），每卡少 2 个节点
    Text(
        text = bodyText,
        modifier = Modifier
            .fillMaxWidth()
            .align(contentAlignment)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        // 行高用课名（较大者），保证换行间距与原多 Text 布局接近
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
            color = if (badge.usesWorkSwapStyle) {
                if (MiuixTheme.isDynamicColor) textColor
                else if (isDark) Color.White.copy(alpha = 0.8f) else Color.White
            } else {
                if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f)
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
            // 跨午夜标注「次日」，否则 23:00 / 00:30 两个角标看着像两节课
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

/**
 * 把「底下一层纯色 + 上面一层纯色」的两次 SrcOver 叠加压成**一次** drawRect
 */
internal fun flattenStackedFills(bottom: Color, top: Color): Color {
    val a1 = bottom.alpha
    val a2 = top.alpha
    val a = 1f - (1f - a1) * (1f - a2)
    if (a <= 0f) return Color.Transparent
    val w1 = a1 * (1f - a2) / a
    val w2 = a2 / a
    return Color(
        red = bottom.red * w1 + top.red * w2,
        green = bottom.green * w1 + top.green * w2,
        blue = bottom.blue * w1 + top.blue * w2,
        alpha = a
    )
}

// 超椭圆 outline 构建昂贵，滑动时约 40~80 张×2 页；仅尺寸/圆角/layoutDirection 变时重建
internal class OutlineCache {
    var width: Float = Float.NaN
    var height: Float = Float.NaN
    var radius: Float = Float.NaN
    var layoutDirection: LayoutDirection =
        LayoutDirection.Ltr
    var outline: Outline? = null
}

/** 课表内元素（课程卡 / 午休晚休分界线 / 空槽加号卡）的纯色描边宽度 */
internal val SCHEDULE_OUTLINE_WIDTH = 2.dp
/** 纯色描边 alpha */
private const val SCHEDULE_OUTLINE_ALPHA = 0.08f
/** 底色饱和度低于此值视为中性（白 / 浅灰 / 深灰），描边改用黑或白 */
private const val SCHEDULE_OUTLINE_NEUTRAL_S = 0.08f

/**
 * 课表内元素的纯色描边颜色。
 *
 * 有色底（课程色）→ 同色淡描边，保留课程色的体积感；中性底（分界线、加号卡、
 * 非动态取色）→ 浅色黑、深色白，否则同色描边在白/灰底上完全看不见。
 */
internal fun scheduleOutlineColor(baseColor: Color, isDark: Boolean): Color {
    val v = maxOf(baseColor.red, maxOf(baseColor.green, baseColor.blue))
    val minC = minOf(baseColor.red, minOf(baseColor.green, baseColor.blue))
    val saturation = if (v > 0f) (v - minC) / v else 0f
    return when {
        saturation > SCHEDULE_OUTLINE_NEUTRAL_S -> baseColor.copy(alpha = SCHEDULE_OUTLINE_ALPHA)
        isDark -> Color.White.copy(alpha = SCHEDULE_OUTLINE_ALPHA)
        else -> Color.Black.copy(alpha = SCHEDULE_OUTLINE_ALPHA)
    }
}

/**
 * 纯色描边（不发光、不采样，就是一次 [drawOutline]），复用 [OutlineCache] 避免每帧重建。
 *
 * 在 `drawWithContent { drawContent(); drawScheduleOutline(...) }` 里调用，描边压在内容之上。
 * 注意 `drawBackdrop` 之后调用时外半圈会被它的 shape clip 裁掉，实际呈现为内描边。
 */
internal fun DrawScope.drawScheduleOutline(
    cache: OutlineCache,
    cornerRadius: Dp,
    color: Color,
    strokeWidthPx: Float,
    layoutDirection: LayoutDirection,
) {
    if (cache.width != size.width ||
        cache.height != size.height ||
        cache.radius != cornerRadius.value ||
        cache.layoutDirection != layoutDirection
    ) {
        cache.outline = ContinuousRoundedRectangle(cornerRadius)
            .createOutline(size, layoutDirection, this)
        cache.width = size.width
        cache.height = size.height
        cache.radius = cornerRadius.value
        cache.layoutDirection = layoutDirection
    }
    drawOutline(outline = cache.outline!!, color = color, style = Stroke(strokeWidthPx))
}
