// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.basic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A [NumberPicker] component with Miuix style.
 *
 * A vertical scroll picker that displays a range of numbers with the selected value centered.
 * Items fade out and scale down as they move away from the center.
 *
 * @param value The current selected value. If outside [range], it will be coerced into the range.
 * @param onValueChange The callback invoked when the selected value changes.
 * @param modifier The modifier to be applied to the [NumberPicker].
 * @param enabled Whether the [NumberPicker] is enabled for user interaction.
 * @param range The range of selectable values.
 * @param label A function that converts a value to its display string.
 * @param visibleItemCount The number of visible items. Must be odd and at least 3.
 * @param wrapAround Whether the picker wraps around from the last item to the first (infinite scrolling).
 * @param colors The [NumberPickerColors] for this [NumberPicker].
 * @param textStyle The [TextStyle] for the picker items.
 * @param itemHeight The height of each item in the picker.
 */
@Composable
fun NumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    range: IntRange = 0..10,
    label: (Int) -> String = { it.toString() },
    visibleItemCount: Int = 5,
    wrapAround: Boolean = false,
    colors: NumberPickerColors = NumberPickerDefaults.colors(),
    textStyle: TextStyle = MiuixTheme.textStyles.title1,
    itemHeight: Dp = NumberPickerDefaults.ItemHeight,
) {
    require(visibleItemCount % 2 == 1 && visibleItemCount >= 3) {
        "visibleItemCount must be odd and at least 3, but was $visibleItemCount"
    }
    require(range.first <= range.last) {
        "range must not be empty"
    }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val latestValue by rememberUpdatedState(value.coerceIn(range))
    val itemCount = range.last - range.first + 1
    val coercedValue = value.coerceIn(range)
    val currentIndex = coercedValue - range.first
    val halfVisibleCount = visibleItemCount / 2
    val hapticFeedback = LocalHapticFeedback.current

    // 中心项的「虚拟位置」，可以带小数。整数部分即当前停在中心的 item 下标。
    // base 承载惯性 / 吸附动画，dragDelta 承载拖拽期间的实时位移，二者相加即真实位置。
    // 用绝对位置而不是「相对已选中项的偏移」，是为了让「提交选中值」和「视觉位置」始终解耦，
    // 这样在动画中途提交也不会引起画面跳动。
    val base = remember { Animatable(currentIndex.toFloat()) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var itemHeightPx by remember { mutableIntStateOf(0) }

    val position by remember { derivedStateOf { base.value + dragDelta } }

    // 跨过 item 边界时的震动：直接由拖拽回调和动画逐帧回调触发，
    // 不再用 snapshotFlow——它只发信号不携带数值，两次变化合并到同一帧时会漏掉一次。
    var lastHapticIndex by remember { mutableIntStateOf(currentIndex) }

    fun tickHaptic(rawIndex: Int) {
        if (rawIndex != lastHapticIndex) {
            lastHapticIndex = rawIndex
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // 把虚拟位置换算成 range 内的真实值并回调给外部
    fun commitPosition(pos: Float) {
        val rawIndex = pos.roundToInt()
        val newIndex = if (wrapAround) {
            ((rawIndex % itemCount) + itemCount) % itemCount
        } else {
            rawIndex.coerceIn(0, itemCount - 1)
        }
        val newValue = range.first + newIndex
        if (newValue != latestValue) {
            currentOnValueChange(newValue)
        }
    }

    // 把惯性落点吸附到最近的整格
    fun snapToItem(projected: Float): Float {
        val rounded = projected.roundToInt().toFloat()
        return if (wrapAround) rounded else rounded.coerceIn(0f, (itemCount - 1).toFloat())
    }

    // 外部（非本组件自身）改动 value，或 range 发生变化时，把位置同步过去
    LaunchedEffect(coercedValue, itemCount, wrapAround) {
        if (wrapAround) {
            base.updateBounds(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
        } else {
            base.updateBounds(0f, (itemCount - 1).toFloat())
        }
        if (!isDragging && !isSettling) {
            dragDelta = 0f
            base.snapTo(currentIndex.toFloat())
            lastHapticIndex = currentIndex
        }
    }

    val draggableState = rememberDraggableState { delta ->
        if (itemHeightPx > 0) {
            val raw = base.value + dragDelta - delta / itemHeightPx
            dragDelta = if (wrapAround) {
                raw - base.value
            } else {
                raw.coerceIn(0f, (itemCount - 1).toFloat()) - base.value
            }
            val currentPosition = base.value + dragDelta
            // 拖拽过程中就实时提交，手指还没抬起时点「确定」也能拿到正确的值
            commitPosition(currentPosition)
            tickHaptic(currentPosition.roundToInt())
        }
    }

    val displayValue = label(coercedValue)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight * visibleItemCount)
            .clipToBounds()
            .semantics {
                contentDescription = "$displayValue, ${range.first} - ${range.last}"
            }
            .onSizeChanged { size ->
                itemHeightPx = size.height / visibleItemCount
            }
            .then(
                if (enabled) {
                    Modifier.draggable(
                        orientation = Orientation.Vertical,
                        state = draggableState,
                        onDragStarted = {
                            isDragging = true
                            // 打断正在进行的惯性/吸附动画，从当前视觉位置接着拖
                            base.stop()
                            dragDelta = 0f
                        },
                        onDragStopped = { velocity ->
                            isDragging = false
                            if (itemHeightPx > 0) {
                                // 把拖拽位移并入动画基准
                                val startPosition = base.value + dragDelta
                                dragDelta = 0f
                                base.snapTo(startPosition)

                                val velocityInItems = -velocity / itemHeightPx
                                val decay = exponentialDecay<Float>(
                                    frictionMultiplier = NumberPickerDefaults.FlingFrictionMultiplier,
                                    absVelocityThreshold = NumberPickerDefaults.FlingVelocityThreshold,
                                )
                                // 先算出惯性最终会停在哪一格并立刻提交。
                                // 这样即使用户在吸附动画结束前就点「确定」/ 关闭弹窗，
                                // 拿到的也是滚轮最终会停留的那一项，而不是起手前那一项。
                                val target = snapToItem(
                                    decay.calculateTargetValue(startPosition, velocityInItems),
                                )
                                isSettling = true
                                commitPosition(target)
                                try {
                                    val decayResult = base.animateDecay(velocityInItems, decay) {
                                        tickHaptic(base.value.roundToInt())
                                    }
                                    // 用惯性结束时的残余速度衔接吸附动画，
                                    // 避免「滑行 → 顿一下 → 再吸附」的割裂感。
                                    // 注意不能读 base.velocity：动画结束后它会被重置为 0。
                                    base.animateTo(
                                        targetValue = target,
                                        animationSpec = NumberPickerDefaults.SnapSpring,
                                        initialVelocity = decayResult.endState.velocity,
                                    ) {
                                        tickHaptic(base.value.roundToInt())
                                    }
                                    // wrap 模式下位置会一直累加，归一化避免长时间滚动后精度下降
                                    if (wrapAround && !isDragging) {
                                        val normalized =
                                            ((base.value.roundToInt() % itemCount) + itemCount) % itemCount
                                        base.snapTo(normalized.toFloat())
                                        lastHapticIndex = normalized
                                    }
                                } finally {
                                    if (!isDragging) isSettling = false
                                }
                            }
                        },
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (itemHeightPx > 0) {
            val currentPosition = position
            val roundedOffset = currentPosition.roundToInt()
            val centerItemOffset = currentPosition - roundedOffset
            val selectedColor = colors.selectedTextColor(enabled)
            val unselectedColor = colors.unselectedTextColor(enabled)
            val resolvedTextStyle = if (textStyle.fontWeight == null) textStyle.copy(fontWeight = FontWeight.SemiBold) else textStyle

            for (i in -halfVisibleCount - 1..halfVisibleCount + 1) {
                val rawItemIndex = roundedOffset + i
                val itemIndex = if (wrapAround) {
                    ((rawItemIndex % itemCount) + itemCount) % itemCount
                } else {
                    if (rawItemIndex !in 0..<itemCount) continue
                    rawItemIndex
                }

                val distanceFromCenter = i.toFloat() - centerItemOffset
                val normalizedDistance = (abs(distanceFromCenter) / (halfVisibleCount + 0.5f)).coerceIn(0f, 1f)

                val alpha = (1f - normalizedDistance) * (1f - normalizedDistance * 0.5f)
                val scale = 1f - 0.2f * normalizedDistance
                val yOffset = distanceFromCenter * itemHeightPx

                val textColor = Color(
                    red = lerp(selectedColor.red, unselectedColor.red, normalizedDistance),
                    green = lerp(selectedColor.green, unselectedColor.green, normalizedDistance),
                    blue = lerp(selectedColor.blue, unselectedColor.blue, normalizedDistance),
                    alpha = lerp(selectedColor.alpha, unselectedColor.alpha, normalizedDistance),
                )

                Text(
                    text = label(range.first + itemIndex),
                    modifier = Modifier
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                            translationY = yOffset
                        },
                    style = resolvedTextStyle,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Contains default values used by [NumberPicker].
 */
object NumberPickerDefaults {

    val ItemHeight = 45.dp

    /**
     * 惯性滑动的摩擦系数。越大 → 减速越猛、滑得越近、停得越快；调小则滑行更绵长柔和。
     */
    const val FlingFrictionMultiplier: Float = 1.0f

    /**
     * 惯性滑动的结束速度阈值，单位是「项/秒」。
     * 指数衰减的尾巴极长（速度越低爬得越慢），减速到该值就带着这个残余速度交给吸附动画收尾。
     * 调大 → 更早交给弹簧、总时长更短；调小 → 惯性自己多滑一段、减速更均匀绵软。
     */
    const val FlingVelocityThreshold: Float = 3.4f

    /**
     * 吸附动画的阻尼比：
     * - `1f` 临界阻尼，最快停住但完全没有回弹，收尾偏生硬
     * - `0.6 ~ 0.7` 轻微欠阻尼，会柔和地过冲一点点再荡回来（约 7~8px）
     * - 再小回弹幅度会明显变大，容易显得「晃」
     */
    const val SnapDampingRatio: Float = 0.78f

    /**
     * 吸附动画的刚度，决定把滚轮「拽」到目标格的力度。
     * 越大吸附越干脆也越生硬；调小收尾更柔和，但停稳所需时间会变长。
     */
    const val SnapStiffness: Float = 200f

    /**
     * 吸附到最近一格的动画规格。
     */
    val SnapSpring: SpringSpec<Float> = spring(
        dampingRatio = SnapDampingRatio,
        stiffness = SnapStiffness,
    )

    /**
     * Creates the default [NumberPickerColors] for a [NumberPicker].
     *
     * @param selectedTextColor The color for the selected (center) item text.
     * @param unselectedTextColor The color for unselected item text.
     * @param disabledSelectedTextColor The color for the selected item text when disabled.
     * @param disabledUnselectedTextColor The color for unselected item text when disabled.
     */
    @Composable
    fun colors(
        selectedTextColor: Color = MiuixTheme.colorScheme.onSurface,
        unselectedTextColor: Color = MiuixTheme.colorScheme.onSurfaceSecondary,
        disabledSelectedTextColor: Color = MiuixTheme.colorScheme.disabledOnSecondary,
        disabledUnselectedTextColor: Color = MiuixTheme.colorScheme.disabledOnSecondary,
    ): NumberPickerColors = remember(
        selectedTextColor,
        unselectedTextColor,
        disabledSelectedTextColor,
        disabledUnselectedTextColor,
    ) {
        NumberPickerColors(
            selectedTextColor = selectedTextColor,
            unselectedTextColor = unselectedTextColor,
            disabledSelectedTextColor = disabledSelectedTextColor,
            disabledUnselectedTextColor = disabledUnselectedTextColor,
        )
    }
}

/**
 * The colors for a [NumberPicker].
 *
 * @see NumberPickerDefaults.colors
 */
@Immutable
data class NumberPickerColors(
    private val selectedTextColor: Color,
    private val unselectedTextColor: Color,
    private val disabledSelectedTextColor: Color,
    private val disabledUnselectedTextColor: Color,
) {

    @Stable
    internal fun selectedTextColor(enabled: Boolean): Color = if (enabled) selectedTextColor else disabledSelectedTextColor

    @Stable
    internal fun unselectedTextColor(enabled: Boolean): Color = if (enabled) unselectedTextColor else disabledUnselectedTextColor
}
