package top.yukonga.miuix.kmp.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class BlurBottomSheetDetent {
    CONTENT,
    EXPANDED,
    DISMISS,
}

internal fun resolveBlurBottomSheetDetent(
    expansionFraction: Float,
    dismissOffsetPx: Float,
    velocityY: Float,
    velocityThresholdPx: Float,
    dismissThresholdPx: Float,
): BlurBottomSheetDetent {
    if (dismissOffsetPx > dismissThresholdPx) return BlurBottomSheetDetent.DISMISS
    if (expansionFraction <= 0.001f && velocityY > velocityThresholdPx) {
        return BlurBottomSheetDetent.DISMISS
    }
    if (velocityY < -velocityThresholdPx) return BlurBottomSheetDetent.EXPANDED
    if (velocityY > velocityThresholdPx) return BlurBottomSheetDetent.CONTENT
    return if (expansionFraction >= 0.5f) {
        BlurBottomSheetDetent.EXPANDED
    } else {
        BlurBottomSheetDetent.CONTENT
    }
}

internal val LocalBlurBottomSheetContentExpanded = compositionLocalOf { false }

@Stable
internal class BlurBottomSheetDetentState {
    private val dragExpansion = mutableFloatStateOf(0f)
    private val settleExpansion = Animatable(0f)
    private val dragDismissOffset = mutableFloatStateOf(0f)
    private val settleDismissOffset = Animatable(0f)

    val collapsedHeightPx = mutableIntStateOf(0)
    private var expansionRangePx = 0

    val expansionFraction: Float
        get() = (dragExpansion.floatValue + settleExpansion.value).coerceIn(0f, 1f)

    val dismissOffsetPx: Float
        get() = (dragDismissOffset.floatValue + settleDismissOffset.value).coerceAtLeast(0f)

    fun updateCollapsedHeight(heightPx: Int) {
        if (
            expansionFraction <= 0.001f && heightPx > 0 && collapsedHeightPx.intValue != heightPx
        ) {
            collapsedHeightPx.intValue = heightPx
        }
    }

    fun updateExpansionRange(maxHeightPx: Int) {
        expansionRangePx = (maxHeightPx - collapsedHeightPx.intValue).coerceAtLeast(0)
    }

    suspend fun reset() {
        settleExpansion.stop()
        settleDismissOffset.stop()
        settleExpansion.snapTo(0f)
        settleDismissOffset.snapTo(0f)
        dragExpansion.floatValue = 0f
        dragDismissOffset.floatValue = 0f
        collapsedHeightPx.intValue = 0
        expansionRangePx = 0
    }

    suspend fun beginDrag() {
        val pendingExpansion = expansionFraction
        val pendingDismissOffset = dismissOffsetPx
        settleExpansion.stop()
        settleDismissOffset.stop()
        settleExpansion.snapTo(0f)
        settleDismissOffset.snapTo(0f)
        dragExpansion.floatValue = pendingExpansion
        dragDismissOffset.floatValue = pendingDismissOffset
    }

    fun dragBy(deltaY: Float) {
        var remaining = deltaY
        var expansion = expansionFraction
        var dismissOffset = dismissOffsetPx
        val range = expansionRangePx.toFloat()

        if (remaining < 0f) {
            val restoredOffset = minOf(-remaining, dismissOffset)
            dismissOffset -= restoredOffset
            remaining += restoredOffset
            if (range > 0f) expansion = (expansion - remaining / range).coerceIn(0f, 1f)
        } else if (remaining > 0f) {
            if (range > 0f) {
                val collapsedDistance = expansion * range
                val collapseDistance = minOf(remaining, collapsedDistance)
                expansion = (expansion - collapseDistance / range).coerceIn(0f, 1f)
                remaining -= collapseDistance
            }
            dismissOffset += remaining
        }

        dragExpansion.floatValue = expansion
        dragDismissOffset.floatValue = dismissOffset
    }

    suspend fun settle(
        velocityY: Float,
        velocityThresholdPx: Float,
        dismissThresholdPx: Float,
        onDismiss: () -> Unit,
    ) {
        val target = resolveBlurBottomSheetDetent(
            expansionFraction = expansionFraction,
            dismissOffsetPx = dismissOffsetPx,
            velocityY = velocityY,
            velocityThresholdPx = velocityThresholdPx,
            dismissThresholdPx = dismissThresholdPx,
        )
        if (target == BlurBottomSheetDetent.DISMISS) {
            onDismiss()
            return
        }

        val startExpansion = expansionFraction
        val startDismissOffset = dismissOffsetPx
        val targetExpansion = if (target == BlurBottomSheetDetent.EXPANDED) 1f else 0f
        val initialVelocity = if (expansionRangePx > 0) {
            (-velocityY / expansionRangePx).coerceIn(-4f, 4f)
        } else {
            0f
        }
        dragExpansion.floatValue = 0f
        dragDismissOffset.floatValue = 0f
        settleExpansion.snapTo(startExpansion)
        settleDismissOffset.snapTo(startDismissOffset)
        coroutineScope {
            launch {
                settleExpansion.animateTo(
                    targetValue = targetExpansion,
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialVelocity = initialVelocity,
                )
            }
            launch {
                settleDismissOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
        }
    }
}

internal fun Modifier.blurBottomSheetDetentHeight(
    state: BlurBottomSheetDetentState,
): Modifier = layout { measurable, constraints ->
    val maxHeight = constraints.maxHeight
    state.updateExpansionRange(maxHeight)
    val collapsedHeight = state.collapsedHeightPx.intValue
    val expansionFraction = state.expansionFraction
    if (collapsedHeight == 0 || expansionFraction <= 0.001f) {
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val boundedCollapsedHeight = collapsedHeight.coerceAtMost(maxHeight)
        val targetHeight = (
            boundedCollapsedHeight +
                (maxHeight - boundedCollapsedHeight) * expansionFraction
            ).roundToInt().coerceIn(0, maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = targetHeight))
        layout(placeable.width, targetHeight) { placeable.place(0, 0) }
    }
}

@Composable
internal fun BlurBottomSheetDragHandle(
    enabled: Boolean,
    onDrag: (Float) -> Unit = {},
    onDragStarted: suspend () -> Unit = {},
    onDragStopped: suspend (Float) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val pressScale = remember { Animatable(1f) }
    val pressWidth = remember { Animatable(45f) }
    val isPressing = remember { mutableFloatStateOf(0f) }
    val handleShape = remember { RoundedCornerShape(2.dp) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .zIndex(2f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        isPressing.floatValue = 1f
                        scope.launch {
                            launch { pressScale.animateTo(1.15f, tween(100)) }
                            launch { pressWidth.animateTo(55f, tween(100)) }
                        }
                        while (awaitPointerEvent().changes.any { it.pressed }) {
                            // Keep tracking until the handle is released.
                        }
                        isPressing.floatValue = 0f
                        scope.launch {
                            launch { pressScale.animateTo(1f, tween(150)) }
                            launch { pressWidth.animateTo(45f, tween(150)) }
                        }
                    }
                }
            }
            .draggable(
                orientation = Orientation.Vertical,
                enabled = enabled,
                state = rememberDraggableState(onDrag),
                onDragStarted = { onDragStarted() },
                onDragStopped = { onDragStopped(it) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(pressWidth.value.dp)
                .height(4.dp)
                .graphicsLayer { scaleY = pressScale.value }
                .clip(handleShape)
                .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(
                    alpha = 0.2f + isPressing.floatValue * 0.15f,
                )),
        )
    }
}
