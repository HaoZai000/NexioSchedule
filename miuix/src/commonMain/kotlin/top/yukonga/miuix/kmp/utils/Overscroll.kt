// 基于 miuix 源码修改，添加 offset 属性用于检测超出滚动量
package top.yukonga.miuix.kmp.utils

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.Platform
import top.yukonga.miuix.kmp.utils.platform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign

// SpringMath / SpringEngine / runSettleAnimation 已统一到同包的 SpringUtils.kt
// （Miuix 原版就是放在那里的 internal 声明）。本文件原本又抄了一份 private 版本，
// 既重复，又让 PullToRefresh / OverscrollFactory 访问不到；删除重复，保留增强的 OverScrollState。

class OverScrollState {
    var isOverScrollActive by mutableStateOf(false)
        internal set

    /** 超出滚动偏移量，正值表示向下超出，负值表示向上超出 */
    var offset by mutableFloatStateOf(0f)
        internal set
}

val LocalOverScrollState = compositionLocalOf { OverScrollState() }

// ==================== Modifier extensions ====================

@Stable
fun Modifier.overScrollVertical(
    nestedScrollToParent: Boolean = true,
    isEnabled: () -> Boolean = { platform() == Platform.Android || platform() == Platform.IOS },
): Modifier = overScrollOutOfBound(isVertical = true, nestedScrollToParent = nestedScrollToParent, isEnabled = isEnabled)

@Stable
fun Modifier.overScrollHorizontal(
    nestedScrollToParent: Boolean = true,
    isEnabled: () -> Boolean = { platform() == Platform.Android || platform() == Platform.IOS },
): Modifier = overScrollOutOfBound(isVertical = false, nestedScrollToParent = nestedScrollToParent, isEnabled = isEnabled)

@Stable
fun Modifier.overScrollOutOfBound(
    isVertical: Boolean = true,
    nestedScrollToParent: Boolean = true,
    isEnabled: () -> Boolean = { platform() == Platform.Android || platform() == Platform.IOS },
): Modifier {
    if (!isEnabled()) return this
    return this.clipToBounds().then(OverscrollElement(isVertical, nestedScrollToParent))
}

// ==================== OverscrollNode ====================

private data class OverscrollElement(
    val isVertical: Boolean,
    val nestedScrollToParent: Boolean,
) : ModifierNodeElement<OverscrollNode>() {
    override fun create(): OverscrollNode = OverscrollNode(isVertical, nestedScrollToParent)
    override fun update(node: OverscrollNode) {
        node.update(isVertical, nestedScrollToParent)
        node.invalidatePlacement()
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "overScrollOutOfBound"
        properties["isVertical"] = isVertical
        properties["nestedScrollToParent"] = nestedScrollToParent
    }
}

private class OverscrollNode(
    var isVertical: Boolean,
    var nestedScrollToParent: Boolean,
) : DelegatingNode(), CompositionLocalConsumerModifierNode, LayoutModifierNode, NestedScrollConnection {
    private val density: Density get() = currentValueOf(LocalDensity)
    private val windowInfo: WindowInfo get() = currentValueOf(LocalWindowInfo)
    private val overScrollState: OverScrollState get() = currentValueOf(LocalOverScrollState)
    private val dispatcher = NestedScrollDispatcher()
    private val springEngine = SpringEngine()
    private var animationJob: Job? = null
    private val offsetThreshold = 1f
    private var lastPlacedOffset = 0f

    var offset = 0f
        private set(value) {
            if (field != value) {
                field = value
                overScrollState.offset = value
                val rounded = round(value)
                if (rounded != lastPlacedOffset) {
                    lastPlacedOffset = rounded
                    if (isAttached) invalidatePlacement()
                }
            }
        }

    private var rawTouchAccumulation = 0f
    private var scrollRange: Float = 0f
    private var cachedScrollRangeDensity: Density? = null
    private var cachedScrollRangeWindowInfo: WindowInfo? = null

    override fun onAttach() {
        super.onAttach()
        updateScrollRange()
        delegate(nestedScrollModifierNode(this, dispatcher))
    }

    override fun onDetach() {
        super.onDetach()
        resetState()
    }

    fun update(isVertical: Boolean, nestedScrollToParent: Boolean) {
        val rangeChanged = this.isVertical != isVertical
        this.isVertical = isVertical
        this.nestedScrollToParent = nestedScrollToParent
        if (rangeChanged && isAttached) updateScrollRange()
    }

    private fun updateScrollRange() {
        val currentDensity = density
        val currentWindowInfo = windowInfo
        if (currentDensity == cachedScrollRangeDensity && currentWindowInfo == cachedScrollRangeWindowInfo) return
        cachedScrollRangeDensity = currentDensity
        cachedScrollRangeWindowInfo = currentWindowInfo
        scrollRange = with(currentDensity) {
            if (isVertical) currentWindowInfo.containerDpSize.height.toPx()
            else currentWindowInfo.containerDpSize.width.toPx()
        }
    }

    private fun resetState() {
        offset = 0f
        rawTouchAccumulation = 0f
        if (isAttached) {
            overScrollState.isOverScrollActive = false
            overScrollState.offset = 0f
        }
    }

    private fun startSpringAnimation(initialVelocity: Float = 0f) {
        if (abs(offset) <= offsetThreshold && initialVelocity == 0f) {
            resetState()
            return
        }
        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            springEngine.runSettleAnimation(
                startValue = offset,
                initialVelocity = initialVelocity,
                onFrame = { currentPos -> offset = currentPos },
                onSettle = { if (abs(offset) <= offsetThreshold) resetState() },
            )
        }
    }

    private fun applyDrag(delta: Float) {
        if (delta == 0f) return
        rawTouchAccumulation += delta
        rawTouchAccumulation = rawTouchAccumulation.coerceIn(-scrollRange, scrollRange)
        val normalized = min(abs(rawTouchAccumulation) / scrollRange, 1.0f)
        val dampedDist = SpringMath.obtainDampingDistance(normalized, scrollRange)
        offset = sign(rawTouchAccumulation) * dampedDist
    }

    private fun syncRawAccumulationFromOffset() {
        rawTouchAccumulation = sign(offset) * SpringMath.obtainTouchDistance(offset, scrollRange)
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        updateScrollRange()
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            val roundedOffset = round(offset)
            if (roundedOffset == 0f) {
                // 无越界偏移时直接摆放：避免强制离屏图层给整个滚动内容带来逐帧合成开销
                placeable.place(0, 0)
            } else {
                placeable.placeWithLayer(0, 0) {
                    if (isVertical) translationY = roundedOffset
                    else translationX = roundedOffset
                    clip = true
                }
            }
        }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!isAttached) return Offset.Zero
        val isActive = abs(offset) > offsetThreshold
        if (overScrollState.isOverScrollActive != isActive) overScrollState.isOverScrollActive = isActive
        if (source != NestedScrollSource.UserInput) return dispatcher.dispatchPreScroll(available, source)
        if (animationJob?.isActive == true) syncRawAccumulationFromOffset()
        animationJob?.cancel()
        val parentConsumed = if (nestedScrollToParent) dispatcher.dispatchPreScroll(available, source) else Offset.Zero
        val realAvailable = available - parentConsumed
        val delta = if (isVertical) realAvailable.y else realAvailable.x
        if (abs(offset) <= offsetThreshold || sign(delta) == sign(rawTouchAccumulation)) return parentConsumed
        if (sign(delta) != sign(rawTouchAccumulation)) {
            val actualConsumed = if (abs(rawTouchAccumulation) <= abs(delta)) -rawTouchAccumulation else delta
            if (abs(rawTouchAccumulation) <= abs(delta)) resetState() else applyDrag(actualConsumed)
            return if (isVertical) Offset(parentConsumed.x, actualConsumed + parentConsumed.y)
            else Offset(actualConsumed + parentConsumed.x, parentConsumed.y)
        }
        applyDrag(delta)
        return if (isVertical) Offset(parentConsumed.x, available.y) else Offset(available.x, parentConsumed.y)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!isAttached) return Offset.Zero
        val isActive = abs(offset) > offsetThreshold
        if (overScrollState.isOverScrollActive != isActive) overScrollState.isOverScrollActive = isActive
        val parentConsumed = if (nestedScrollToParent) {
            dispatcher.dispatchPostScroll(consumed, available, source)
        } else {
            Offset.Zero
        }
        if (source != NestedScrollSource.UserInput) {
            // fling 过程中触边：列表 consumed≈0 时，available 就是触边剩余。
            // 必须用列表给出的 available，不能减 parentConsumed——
            // 平板主 VerticalPager 等父级可能先把速度吃掉，减完恒为 0，回弹就没了。
            val edgeLeftover = if (isVertical) available.y else available.x
            val consumedAxis = if (isVertical) consumed.y else consumed.x
            if (abs(consumedAxis) < 1.5f && abs(edgeLeftover) > 0.5f) {
                animationJob?.cancel()
                applyDrag(edgeLeftover * 0.35f)
            }
            return parentConsumed
        }
        animationJob?.cancel()
        val realAvailable = available - parentConsumed
        val delta = if (isVertical) realAvailable.y else realAvailable.x
        applyDrag(delta)
        return if (isVertical) Offset(parentConsumed.x, available.y) else Offset(available.x, parentConsumed.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!isAttached) return Velocity.Zero
        val isActive = abs(offset) > offsetThreshold
        if (overScrollState.isOverScrollActive != isActive) overScrollState.isOverScrollActive = isActive
        animationJob?.cancel()
        val parentConsumed = if (nestedScrollToParent) dispatcher.dispatchPreFling(available) else Velocity.Zero
        val realAvailable = available - parentConsumed
        val velocity = if (isVertical) realAvailable.y else realAvailable.x
        if (abs(offset) > offsetThreshold) {
            startSpringAnimation(velocity)
            return parentConsumed + if (isVertical) Velocity(0f, realAvailable.y / 2.13333f)
            else Velocity(realAvailable.x / 2.13333f, 0f)
        }
        return parentConsumed
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (!isAttached) return Velocity.Zero
        val isActive = abs(offset) > offsetThreshold
        if (overScrollState.isOverScrollActive != isActive) overScrollState.isOverScrollActive = isActive
        animationJob?.cancel()
        val parentConsumed = if (nestedScrollToParent) dispatcher.dispatchPostFling(consumed, available) else Velocity.Zero
        val realAvailable = available - parentConsumed
        // 甩边预置/弹回用列表给出的 available，不依赖 parent 是否吞掉速度
        val rawVelocity = (if (isVertical) available.y else available.x) / 1.53333f
        val velocity = if (abs(offset) > offsetThreshold) {
            (if (isVertical) realAvailable.y else realAvailable.x) / 1.53333f
        } else {
            rawVelocity
        }
        if (abs(offset) <= offsetThreshold && abs(rawVelocity) > 8f) {
            val preset = (rawVelocity * 0.02f)
                .coerceIn(-scrollRange * 0.05f, scrollRange * 0.05f)
            if (abs(preset) > offsetThreshold) {
                offset = preset
                syncRawAccumulationFromOffset()
                overScrollState.isOverScrollActive = true
            }
        }
        startSpringAnimation(if (abs(offset) > offsetThreshold) rawVelocity else velocity)
        return parentConsumed + if (isVertical) Velocity(0f, velocity) else Velocity(velocity, 0f)
    }
}
