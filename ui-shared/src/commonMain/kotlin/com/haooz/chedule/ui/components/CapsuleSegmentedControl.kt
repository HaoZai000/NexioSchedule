package com.haooz.chedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.glass.BarFill
import com.haooz.softglass.glass.BrightLargeTone
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.softglass.shape.ContinuousCapsule
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.internal.drawBlurredPathShadow
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.utils.LocalOverScrollState

/** 分段控件自己的一块玻璃，尺寸与底栏胶囊不同 ⇒ uniform 必须独立 */
private const val LIVE_SEGMENT_PILL_GLASS_KEY = "NexioSoftGlassLiveSegmentPill"

/** 分段控件 */
@Composable
fun CapsuleSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    containerHeight: Dp = 42.dp,
    /** 顶栏滚动状态；为 null 时胶囊恒为静止态。 */
    scrollBehavior: SharedScrollBehavior? = null,
    /** 按下即回调 [onSelect]；默认松手回调。 */
    selectOnPress: Boolean = false,
    /** 每递增一次，滑块停在当前位置、速度归零。 */
    freezeKey: Int = 0,
    /** 每递增一次，滑块吸附到 [selectedIndex]。 */
    alignKey: Int = 0,
) {
    if (options.isEmpty()) return
    val isLightTheme = !isAppDarkTheme()
    val containerColor = bottomBarSurfaceColor()
    // 静止态实底：浅色纯白，深色 searchbar 深灰
    val idleColor = if (isLightTheme) Color.White else Color(0xFF242424)
    val labelColor = if (isLightTheme) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    val overScrollState = LocalOverScrollState.current
    val shadowThresholdPx = with(density) { 10.dp.toPx() }
    val shadowMaxBlurPx = with(density) { 10.dp.toPx() }
    val showMaterial by remember(scrollBehavior, overScrollState) {
        derivedStateOf {
            val offset = scrollBehavior?.state?.contentOffset ?: 0f
            offset < -shadowThresholdPx || (offset >= 0f && overScrollState.offset < 0f)
        }
    }
    val initialAlpha = if (showMaterial) 1f else 0f
    val backdropAlpha = remember { Animatable(initialAlpha) }
    val shadowAlpha = remember { Animatable(initialAlpha) }
    LaunchedEffect(showMaterial) {
        val target = if (showMaterial) 1f else 0f
        val spec = if (showMaterial) {
            folmeSpring<Float>(damping = 1.0f, response = 0.6f)
        } else {
            folmeSpring<Float>(damping = 1.0f, response = 0.4f)
        }
        launch { backdropAlpha.animateTo(target, spec) }
        launch { shadowAlpha.animateTo(target, spec) }
    }

    // 容器玻璃：与底栏同一份材质配方（BarFill + BrightLargeTone + Mirror 模糊）
    val glassBlur = AppMaterialSettings.glassBlurDp()
    val glassEffectsOn = AppMaterialSettings.glassEffectsEnabled()
    val glassStyle = AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = !isLightTheme,
        fill = BarFill,
        tone = BrightLargeTone,
    )
    val capsuleShape: () -> Shape = remember { { ContinuousCapsule() } }
    val pillEffects: GlassEffectScope.() -> Unit = remember(glassBlur, glassEffectsOn, glassStyle) {
        {
            if (glassEffectsOn) {
                blur(glassBlur.dp.toPx(), TileMode.Mirror)
                padding = 0f
                runtimeShaderEffect(
                    key = LIVE_SEGMENT_PILL_GLASS_KEY,
                    shaderString = SOFT_GLASS_LIVE_SHADER,
                    uniformShaderName = "content",
                ) {
                    applySoftGlass(style = glassStyle, width = size.width, height = size.height)
                }
            }
        }
    }
    val pillSurface: DrawScope.() -> Unit = remember(containerColor) { { drawRect(containerColor) } }

    // 跟手高光：只画手指处那团，不铺整块提亮；容器也不做任何按压缩放
    val highlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            lightPeakAlpha = 0.1f,
            drawFlatOverlay = false,
        )
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val viewConfiguration = LocalViewConfiguration.current
        val totalPx = constraints.maxWidth.toFloat()
        val padPx = with(density) { 4.dp.toPx() }
        val tabWidthPx = (totalPx - padPx * 2f) / options.size
        val insetPx = with(density) { CapsuleSlider.Inset.toPx() }
        val selectorWidthPx = tabWidthPx - insetPx * 2f
        val selectorHeightPx = with(density) { (containerHeight - CapsuleSlider.VerticalInset).roundToPx() }
        val trailMaxPx = with(density) { CapsuleSlider.TrailMax.toPx() }
        // 快甩阈值 dp/秒 → px/秒
        val flingVelocityThresholdPxPerSec =
            with(density) { CapsuleSlider.FlingVelocityDpPerSecond.toPx() }

        fun slotLeft(index: Float): Float = padPx + index * tabWidthPx + insetPx

        var currentIndex by remember { mutableIntStateOf(selectedIndex.coerceIn(0, options.lastIndex)) }
        val latestSelected by rememberUpdatedState(selectedIndex)
        val latestOnSelect by rememberUpdatedState(onSelect)

        val capsule = remember(animationScope, options.size, tabWidthPx, totalPx) {
            CapsuleSelector(
                scope = animationScope,
                widthPx = selectorWidthPx,
                startLeftPx = slotLeft(currentIndex.toFloat()),
                leftLimit = { padPx..(totalPx - padPx - selectorWidthPx) },
            )
        }
        val settleToSlot: (Int) -> Unit = { index ->
            val target = slotLeft(index.toFloat())
            capsule.settleTo(target, forward = target > capsule.leftPx)
        }
        val indexAtCapsule: () -> Int = {
            ((capsule.centerPx - padPx) / tabWidthPx - 0.5f).roundToInt()
                .coerceIn(0, options.lastIndex)
        }

        // 外部同步选中项时只移动滑块，不回调 onSelect。
        LaunchedEffect(capsule) {
            snapshotFlow { latestSelected }.collectLatest { index ->
                if (index != currentIndex) {
                    currentIndex = index
                    settleToSlot(index)
                }
            }
        }
        // 手势接管：停住滑块
        LaunchedEffect(capsule, freezeKey) {
            if (freezeKey > 0) capsule.freeze()
        }
        // 接管结束：重新吸附
        LaunchedEffect(capsule, alignKey) {
            if (alignKey > 0) {
                val index = latestSelected.coerceIn(0, options.lastIndex)
                currentIndex = index
                settleToSlot(index)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
                .drawBehind {
                    val shadowProgress = shadowAlpha.value
                    val glassProgress = backdropAlpha.value.coerceIn(0f, 1f)
                    val outline = capsuleShape().createOutline(size, layoutDirection, this@drawBehind)
                    if (shadowProgress > 0.01f) {
                        val shadowPath = Path().apply {
                            when (outline) {
                                is Outline.Rounded -> addRoundRect(outline.roundRect)
                                is Outline.Generic -> addPath(outline.path)
                                is Outline.Rectangle -> addRect(outline.rect)
                            }
                        }
                        // SearchBar 模糊投影，随材质淡入
                        drawBlurredPathShadow(
                            path = shadowPath,
                            color = if (isLightTheme) Color(0x12000000) else Color(0x20000000),
                            blurRadius = (shadowMaxBlurPx * shadowProgress).coerceAtLeast(0.1f),
                        )
                    }
                    // 材质淡入时底色淡出
                    drawOutline(outline = outline, color = idleColor.copy(alpha = 1f - glassProgress))
                },
        ) {
            // 玻璃层 alpha 由滚动控制
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shadow = null,
                        shape = capsuleShape,
                        effects = pillEffects,
                        layerBlock = { alpha = backdropAlpha.value },
                        onDrawSurface = if (glassEffectsOn) null else pillSurface,
                    ),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(ContinuousCapsule())
                    .then(highlight.modifier),
            )
            // 滑块：位置与宽度在 layout 期读，按压只缩不压暗
            Box(
                modifier = Modifier.capsuleSliderPill(
                    selector = capsule,
                    heightPx = selectorHeightPx,
                    pressProgress = { highlight.pressProgress },
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(containerHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEachIndexed { _, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = labelColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // 手势层：按压观察高光、拖动滑块、松手吸附。与平板顶栏同一条流程。
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(highlight.gestureModifier)
                .pointerInput(
                    capsule,
                    options.size,
                    tabWidthPx,
                    totalPx,
                    hapticFeedback,
                    flingVelocityThresholdPxPerSec,
                ) {
                    val touchSlop = viewConfiguration.touchSlop
                    val flingVelocityThreshold = flingVelocityThresholdPxPerSec
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x
                        val startIndex = currentIndex.coerceIn(0, options.lastIndex)
                        // 快甩采样共用 CapsuleFlingTracker
                        val fling = CapsuleFlingTracker(down.uptimeMillis, downX)

                        // 按在滑块上才是拖；按在别的格子上直接选中（滑块就是拖动把手）
                        var dragging = abs(downX - capsule.centerPx) <=
                            tabWidthPx * CapsuleSlider.GrabHalfWidthRatio
                        var pressedTab = -1
                        var dragAnchorLeft = capsule.leftPx
                        var dragAnchorX = downX
                        var trailVelocity = 0f

                        if (!dragging) {
                            pressedTab = floor((downX - padPx) / tabWidthPx)
                                .toInt()
                                .coerceIn(0, options.lastIndex)
                            settleToSlot(pressedTab)
                            // 按下即起跑，松手按落点再回调一次。
                            if (selectOnPress && pressedTab != currentIndex) latestOnSelect(pressedTab)
                        }

                        var lastX = downX
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                val positionalTarget = if (dragging) {
                                    indexAtCapsule()
                                } else {
                                    pressedTab.coerceIn(0, options.lastIndex)
                                }
                                // 快甩至少补切一项，保留更远落点。
                                val flingDir = if (dragging) {
                                    fling.flingDirection(flingVelocityThreshold, touchSlop)
                                } else 0
                                val target = CapsuleSlider.flingTarget(
                                    startIndex = startIndex,
                                    positionalTarget = positionalTarget,
                                    indexDirection = flingDir,
                                    lastIndex = options.lastIndex,
                                )
                                currentIndex = target
                                settleToSlot(target)
                                // 落项震动
                                if (target != startIndex) {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove
                                    )
                                }
                                latestOnSelect(target)
                                break
                            }

                            val dx = change.position.x - lastX
                            val totalDx = change.position.x - downX
                            fling.onMove(change.uptimeMillis, change.position, downX)
                            // 点完继续滑就接管成拖动；锚点取当前手指位置，吃掉过 slop 那一段
                            if (!dragging && abs(totalDx) > touchSlop) {
                                dragging = true
                                dragAnchorLeft = capsule.leftPx
                                dragAnchorX = change.position.x
                                trailVelocity = 0f
                                lastX = change.position.x
                            }
                            if (dragging) {
                                trailVelocity = trailVelocity * CapsuleSlider.TrailSmoothing +
                                    dx * (1f - CapsuleSlider.TrailSmoothing)
                                val trail = (abs(trailVelocity) * CapsuleSlider.TrailGain)
                                    .coerceAtMost(trailMaxPx)
                                val dir = when {
                                    trailVelocity > 0.5f -> 1
                                    trailVelocity < -0.5f -> -1
                                    else -> 0
                                }
                                capsule.onDrag(
                                    leftPx = dragAnchorLeft + (change.position.x - dragAnchorX),
                                    trailPx = trail,
                                    dir = dir,
                                )
                            }
                            lastX = change.position.x
                            change.consume()
                        }
                    }
                },
        )
    }
}
