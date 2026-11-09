package com.haooz.chedule.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.flow.drop

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
) {
    if (options.isEmpty()) return
    val isLightTheme = !isAppDarkTheme()
    val containerColor = bottomBarSurfaceColor()
    val labelColor = if (isLightTheme) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current

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
        val maxIndex = (options.size - 1).toFloat()

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

        LaunchedEffect(capsule) {
            snapshotFlow { latestSelected }.collectLatest { index ->
                if (index != currentIndex) currentIndex = index
            }
        }
        LaunchedEffect(capsule) {
            snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
                settleToSlot(index)
                latestOnSelect(index)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = capsuleShape,
                    effects = pillEffects,
                    onDrawSurface = if (glassEffectsOn) null else pillSurface,
                ),
        ) {
            // 高光单独一层并裁到胶囊：光晕半径远大于节点，不裁会画成方块
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(highlight.modifier)
                    .clip(ContinuousCapsule()),
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
                options.forEachIndexed { i, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (i == currentIndex) FontWeight.Medium else null,
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
                .pointerInput(capsule, options.size, tabWidthPx, totalPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x
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
                        }

                        var lastX = downX
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                val target = if (dragging) {
                                    indexAtCapsule()
                                } else {
                                    pressedTab.coerceIn(0, options.lastIndex)
                                }
                                currentIndex = target
                                settleToSlot(target)
                                latestOnSelect(target)
                                break
                            }

                            val dx = change.position.x - lastX
                            val totalDx = change.position.x - downX
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
