package com.haooz.chedule.ui.components.slider

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SliderDefaults

/**
 * 触感事件类型，与 Miuix `SliderHapticState` 对齐。
 *
 * @property Edge 到达取值区间的两端。
 * @property Step 进入一个档位或自定义锚点。
 */
internal enum class NexioSliderHaptic { Edge, Step }

/**
 * 滑块的交互状态机，移植自 COUI 的 `COUISeekBarAdvanced` 与 `SectionAnimationHandler`。
 *
 * 核心特征是**逻辑值与绘制位置分离**：
 * - [value] 是上报给调用方的真实值，受磁吸、等分档位与外部赋值约束；
 * - [position] 是 thumb 的绘制位置，独立做弹簧动画。
 *
 * 这样外部回写、锚点吸附与惯性都不会打断手势：拖动期间 thumb 直接贴手，只在吸附与松手
 * 落位时走弹簧。
 *
 * 三种模式按优先级切换：
 * 1. **锚点模式**（[keyPointFractions] 非空）：连续跟手 + 锚点磁吸；
 * 2. **等分模式**（[section] 为 true）：COUI SectionSeekBar，按段吸附并回弹；
 * 3. **连续模式**：默认，带惯性 fling。
 *
 * @param range 取值区间。
 * @param steps 等分档位数，不含两端。
 * @param section 是否启用 COUI 的分段吸附行为。
 * @param density 屏幕密度，用于把 dp 阈值换算成像素。
 * @param initialValue 初始值。
 * @param scope 承载过渡、吸附、惯性等动画协程的作用域。
 * @param onValueChange 值变化回调。
 * @param onValueChangeFinished 手势结束回调。
 * @param feedback 触感回调。
 * @param hapticEffect 触感策略，决定触发哪些事件。
 * @param keyPointFractions 自定义锚点，已归一化到 0..1。
 * @param magnetThreshold 磁吸阈值（fraction），手指落在锚点该距离内即吸附。
 */
internal class NexioSliderMotion(
    private val range: ClosedFloatingPointRange<Float>,
    private val steps: Int,
    private val section: Boolean,
    private val density: Float,
    initialValue: Float,
    private val scope: CoroutineScope,
    private val onValueChange: (Float) -> Unit,
    private val onValueChangeFinished: () -> Unit,
    private val feedback: (NexioSliderHaptic) -> Unit,
    private val hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
    private val keyPointFractions: List<Float> = emptyList(),
    private val magnetThreshold: Float = 0f,
) {
    private val intervals = steps + 1
    private val span = range.endInclusive - range.start
    private var lastExternalValue = initialValue
    private var pendingRelease = false
    private var downX = 0f
    private var anchorSection = -1
    private var anchorX = 0f
    private var direction = 0
    private var lastFingerX = 0f
    private var fingerOffset = 0f
    private var dragAnimValue = 0f
    private var dragAnimStart = 0f
    private var dragAnimEnd = Float.NaN
    private var fastRelease = false
    private var bumpingEnd = false
    private var transitionJob: Job? = null
    private var dragSnapJob: Job? = null
    private var flingJob: Job? = null
    private var pressed = false
    private var dragging = false
    // 对齐 Miuix SliderHapticState：边缘一次、锚点/档位每次进入一次。
    private var edgeFeedbackTriggered = false
    private var lastStepValue = initialValue
    private var isAtKeyPoint = false

    /** 拖动吸附弹簧的回弹量：Miuix 为 spring(0.9, 1755)，等价 response 0.15s / bounce 0.1。 */
    private val snapBounce: Float get() = if (keyPointFractions.isNotEmpty()) 0.1f else 0f

    /** 落位弹簧：Miuix 为 spring(0.96, 322)，等价 response 0.35s / bounce 0.04。 */
    private val settleResponse: Float get() = if (keyPointFractions.isNotEmpty()) 0.35f else 0.3f

    /** 落位弹簧的回弹量。 */
    private val settleBounce: Float get() = if (keyPointFractions.isNotEmpty()) 0.04f else 0f

    /** 轨道加厚进度（0..100）。 */
    val height: NexioSpring = NexioSpring(0f, scope) {
        // 关闭系统动画时首个积分帧可直接到达终点；该帧交给结束监听退场，运行中重定向会丢失待定目标。
        if (pendingRelease && heightFraction >= 0.5f && height.value < 100f) {
            scope.launch { consumePendingRelease() }
        }
    }

    /** thumb 半径（dp）。 */
    val thumbRadius = NexioSpring(9f, scope)

    /** thumb 纹理层的显现进度（0..100）。 */
    val texture = NexioSpring(0f, scope)

    /** thumb 白色覆盖层的遮盖进度（0..100）。 */
    val thumbCover = NexioSpring(100f, scope)

    /** thumb 的绘制位置（像素）。 */
    val position = NexioSpring(0f, scope)

    /** 到达末端时的闪光强度。 */
    val glitter = NexioSpring(0f, scope)

    /** 当前逻辑值。 */
    var value by mutableFloatStateOf(initialValue.coerceIn(range))
        private set

    /** 可用轨道长度（像素），由 [resize] 写入。 */
    var length = 0f
        private set

    /** 轨道加厚进度，归一化到 0..1。 */
    val heightFraction: Float get() = (height.value / 100f).coerceIn(0f, 1f)

    /** 逻辑值对应的归一化位置。 */
    val fraction: Float get() = normalize(value)

    /** thumb 当前绘制位置对应的归一化位置，用于绘制进度与刻度判断。 */
    val visualFraction: Float get() = if (length > 0f) (position.value / length).coerceIn(0f, 1f) else fraction

    /**
     * 响应外部值变化。
     *
     * 值与当前一致时只做一次补位，避免调用方把档位量化后回传导致视觉动画反复重启。
     *
     * @param externalValue 调用方传入的最新值。
     */
    fun syncValue(externalValue: Float) {
        if (externalValue == lastExternalValue) return
        lastExternalValue = externalValue
        val next = externalValue.coerceIn(range)
        // 调用方可以把等分档位转成整数, 不让该回传反复打断同一档的视觉动画。
        if (next == value || steps > 0 && index(normalize(next)) == index(fraction)) {
            // 值没有变化但 thumb 仍停在手指处（锚点吸附回写）时，补一次落位动画。
            if (!pressed && !position.isRunning && length > 0f &&
                abs(position.value - fraction * length) > 0.5f
            ) {
                position.animateTo(fraction * length, settleResponse, settleBounce)
            }
            return
        }
        cancelGesture()
        stopPositionAnimations()
        value = next
        // 外部赋值（含松手整档回写）同样走弹簧，避免 thumb 瞬移。
        position.animateTo(fraction * length, settleResponse, settleBounce)
    }

    /**
     * 更新可用轨道长度。
     *
     * @param nextLength 新的长度（像素）。
     */
    fun resize(nextLength: Float) {
        if (nextLength == length) return
        cancelGesture()
        stopPositionAnimations()
        length = nextLength.coerceAtLeast(0f)
        position.snapTo(fraction * length)
    }

    /**
     * 按下。
     *
     * @param fingerX 手指在轨道坐标系中的横坐标。
     */
    fun press(fingerX: Float) {
        stopPositionAnimations()
        resetAnchor()
        resetHaptic(value)
        pressed = true
        dragging = false
        pendingRelease = false
        bumpingEnd = false
        downX = fingerX
        height.animateTo(100f, 0.5f, onEnd = ::consumePendingRelease)
        thumbRadius.animateTo(10f, 0.5f, 0.3f, minimumChange = 1f / density)
        texture.animateTo(100f, 0.3f)
        thumbCover.snapTo(100f)
        thumbCover.animateTo(0f, 0.5f)
    }

    /**
     * 拖动。
     *
     * @param fingerX 手指在轨道坐标系中的横坐标。
     * @param velocity 当前手指速度（像素/秒），仅用于末端闪光判定。
     */
    fun drag(fingerX: Float, velocity: Float) {
        if (!pressed || length <= 0f) return
        dragging = true
        val x = fingerX.coerceIn(0f, length)
        if (section) {
            dragSection(x)
        } else {
            val raw = x / length
            // 吸附到锚点时 thumb 跟到吸附值，否则直接贴手；弹簧只负责吸附与落位。
            val snapped = keyPointIndex(raw) >= 0
            setFraction(raw, vibrate = true)
            if (snapped) {
                position.animateTo(fraction * length, 0.15f, snapBounce, minimumChange = length / 1000f)
            } else {
                position.snapTo(x)
            }
        }
        if (x < length) bumpingEnd = false
        if (!section && x >= length && velocity > 0f && !bumpingEnd) startGlitter(velocity)
    }

    /**
     * 抬手。
     *
     * 未拖动时按点按处理；拖动后按模式落位（锚点吸附、分段吸附或惯性）。
     *
     * @param fingerX 手指在轨道坐标系中的横坐标。
     * @param velocity 抬手速度（像素/秒）。
     */
    fun release(fingerX: Float, velocity: Float) {
        if (!pressed) return
        releaseAppearance()
        pressed = false
        if (!dragging) {
            val target = quantize(normalizePosition(fingerX))
            if (section) {
                setFraction(target, vibrate = true)
                animateSection(target * length, 200L)
            } else {
                position.animateTo(target * length, settleResponse, settleBounce, onFrame = {
                    setFraction(normalizePosition(it), vibrate = false, truncate = true)
                }) {
                    setFraction(target, vibrate = false)
                    onValueChangeFinished()
                }
            }
            return
        }
        dragging = false
        if (section) {
            if (dragSnapJob?.isActive == true) {
                fastRelease = true
            } else {
                val segment = length / intervals
                val target = (index(fraction) + ((fingerX.coerceIn(0f, length) - fraction * length) / segment).toInt())
                    .coerceIn(0, intervals)
                snapSection(target)
                resetAnchor()
            }
        } else {
            val raw = normalizePosition(fingerX)
            val snapped = keyPointIndex(raw) >= 0
            setFraction(raw, vibrate = true)
            if (snapped) {
                // 松手停在锚点上，落位弹簧与 Miuix 静止态一致，不做惯性。
                position.animateTo(fraction * length, settleResponse, settleBounce) { onValueChangeFinished() }
            } else {
                position.snapTo(fingerX.coerceIn(0f, length))
                if (abs(velocity) >= 100f) startFling(velocity.coerceIn(-8000f, 8000f))
                else onValueChangeFinished()
            }
        }
    }

    /** 取消当前手势，恢复到静止外观。 */
    fun cancelGesture() {
        if (pressed) releaseAppearance()
        pressed = false
        dragging = false
        fastRelease = false
        stopPositionAnimations()
        resetAnchor()
    }

    /**
     * 响应无障碍 / 键盘等非触摸操作。
     *
     * @param nextValue 目标值，会被量化到合法档位。
     */
    fun setFromAction(nextValue: Float) {
        cancelGesture()
        setFraction(quantize(normalize(nextValue)), vibrate = true)
        position.snapTo(fraction * length)
        onValueChangeFinished()
    }

    /** 释放所有动画协程。 */
    fun dispose() {
        pressed = false
        pendingRelease = false
        stopPositionAnimations()
        listOf(height, thumbRadius, texture, thumbCover, position, glitter).forEach { it.cancel() }
    }

    private fun releaseAppearance() {
        if (height.isRunning && heightFraction < 0.5f) {
            pendingRelease = true
        } else {
            pendingRelease = false
            height.animateTo(0f, 0.5f)
        }
        thumbRadius.animateTo(9f, 0.5f, minimumChange = 1f / density)
        texture.animateTo(0f, 0.3f)
        thumbCover.animateTo(100f, 0.3f)
    }

    private fun consumePendingRelease() {
        if (!pendingRelease) return
        pendingRelease = false
        height.animateTo(0f, 0.5f)
    }

    private fun dragSection(x: Float) {
        val segment = length / intervals
        if (anchorSection < 0) {
            anchorSection = index(normalizePosition(downX))
            anchorX = anchorSection * segment
            setFraction(anchorSection.toFloat() / intervals, vibrate = true)
            position.snapTo(anchorX)
            direction = if (x < anchorX) -1 else 1
            lastFingerX = x
            return
        }
        val nextDirection = when {
            x > lastFingerX + 0.5f -> 1
            x < lastFingerX - 0.5f -> -1
            else -> 0
        }
        if (nextDirection != 0 && nextDirection == -direction) {
            direction = nextDirection
            if (anchorSection != index(fraction)) {
                anchorSection = index(fraction)
                anchorX = anchorSection * segment
                dragAnimValue = 0f
            }
            dragSnapJob?.cancel()
        }
        fingerOffset = x - anchorX
        val crossed = ((fingerOffset + if (fingerOffset >= 0f) 0.1f else -0.1f) / segment).toInt()
        val offset = crossed * segment
        if ((anchorSection + crossed).coerceIn(0, intervals) != index(fraction)) {
            startDragSnap(anchorX + offset)
        } else if (dragSnapJob?.isActive != true) {
            position.snapTo((anchorX + offset + (fingerOffset - offset) * 0.6f).coerceIn(0f, length))
        }
        lastFingerX = x
    }

    private fun startDragSnap(target: Float) {
        if (position.value == target || dragSnapJob?.isActive == true && dragAnimEnd == target) return
        dragSnapJob?.cancel()
        dragAnimStart = anchorX
        dragAnimEnd = target
        val start = dragAnimValue
        val end = target - anchorX
        dragSnapJob = scope.launch {
            animate(start, end, animationSpec = tween(100, easing = DragEasing)) { animated, _ ->
                dragAnimValue = animated
                val x = (dragAnimStart + dragAnimValue * 0.4f + fingerOffset * 0.6f).coerceIn(0f, length)
                position.snapTo(x)
                val selected = if (dragAnimEnd > dragAnimStart) (x / (length / intervals)).roundToInt()
                else ceil(x / (length / intervals)).toInt()
                setFraction(selected.coerceIn(0, intervals).toFloat() / intervals, vibrate = true)
            }
            if (fastRelease) {
                fastRelease = false
                val targetSection = index(normalizePosition(lastFingerX))
                resetAnchor()
                snapSection(targetSection)
            }
        }
    }

    private fun snapSection(target: Int) {
        setFraction(target.toFloat() / intervals, vibrate = false)
        val x = fraction * length
        if (abs(position.value - x) < 0.5f) {
            position.snapTo(x)
            onValueChangeFinished()
        } else animateSection(x, 150L)
    }

    private fun animateSection(target: Float, durationMs: Long) {
        transitionJob?.cancel()
        val start = position.value
        transitionJob = scope.launch {
            // ValueAnimator 默认插值器为 AccelerateDecelerate（余弦曲线），沿用同一曲线。
            animate(start, target, animationSpec = tween(durationMs.toInt(), easing = AccelerateDecelerateEasing)) { v, _ ->
                position.snapTo(v)
            }
            onValueChangeFinished()
        }
    }

    private fun startFling(initialVelocity: Float) {
        var velocity = initialVelocity
        var x = position.value
        flingJob?.cancel()
        flingJob = scope.launch {
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                // 原厂用固定刷新率步长；这里用真实帧间隔，60Hz / 120Hz 的时间域衰减一致。
                val frameSeconds = ((now - previous) / 1_000_000_000.0).toFloat().coerceIn(1f / 240f, 1f / 30f)
                previous = now
                // Oplus World.solve 的无约束单轴分支, 不是 AndroidX 的指数衰减。
                velocity /= 1f + 15f * frameSeconds
                x += velocity * frameSeconds
                val clamped = x.coerceIn(0f, length)
                if (x >= length && !bumpingEnd) startGlitter(velocity)
                position.snapTo(clamped)
                // 惯性期间不吸附锚点：thumb 与值保持连续，落点由松手后的整档回写统一处理。
                setFraction(normalizePosition(clamped), vibrate = false, ignoreKeyPoint = true)
                // shortcut: 可见边界处结束惯性; 需要越界回弹或精确结束回调时再移植原厂约束体。
                if (x != clamped || abs(velocity) < 0.1f) {
                    onValueChangeFinished()
                    return@launch
                }
            }
        }
    }

    private fun startGlitter(velocity: Float) {
        bumpingEnd = true
        glitter.animateTo(0f, 0.6f, velocity = abs(velocity))
    }

    private fun setFraction(
        next: Float,
        vibrate: Boolean,
        truncate: Boolean = false,
        ignoreKeyPoint: Boolean = false,
    ) {
        val raw = next.coerceIn(0f, 1f)
        val keyPoint = if (ignoreKeyPoint) -1 else keyPointIndex(raw)
        val selected = when {
            // 自定义锚点：落在阈值内就吸附到真实锚点值，否则保持连续跟手。
            keyPoint >= 0 -> keyPointFractions[keyPoint]
            steps > 0 || section -> {
                val i = if (truncate) (raw * intervals).toInt() else index(raw)
                i.toFloat() / intervals
            }
            else -> raw
        }
        val nextValue = range.start + span * selected
        if (vibrate) handleHaptic(nextValue, selected)
        if (value == nextValue) return
        value = nextValue
        onValueChange(nextValue)
    }

    private fun stopPositionAnimations() {
        fastRelease = false
        transitionJob?.cancel()
        transitionJob = null
        dragSnapJob?.cancel()
        dragSnapJob = null
        position.cancelComplete()
        flingJob?.cancel()
        flingJob = null
    }

    private fun resetAnchor() {
        fastRelease = false
        dragSnapJob?.cancel()
        anchorSection = -1
        anchorX = 0f
        direction = 0
        fingerOffset = 0f
        dragAnimValue = 0f
        dragAnimStart = 0f
        dragAnimEnd = Float.NaN
        lastFingerX = 0f
    }

    private fun normalize(next: Float): Float = if (span > 0f) ((next - range.start) / span).coerceIn(0f, 1f) else 0f

    private fun normalizePosition(x: Float): Float = if (length > 0f) (x / length).coerceIn(0f, 1f) else fraction

    private fun index(next: Float): Int = (next * intervals).roundToInt().coerceIn(0, intervals)

    private fun resetHaptic(currentValue: Float) {
        edgeFeedbackTriggered = false
        lastStepValue = currentValue
        isAtKeyPoint = false
    }

    /** 与 Miuix SliderHapticState 同一判定：边缘震一次，自定义锚点进入 ±0.005 时震一次。 */
    private fun handleHaptic(currentValue: Float, selectedFraction: Float) {
        if (hapticEffect == SliderDefaults.SliderHapticEffect.None) return
        val atEdge = selectedFraction <= 0f || selectedFraction >= 1f
        if (atEdge) {
            if (!edgeFeedbackTriggered) {
                feedback(NexioSliderHaptic.Edge)
                edgeFeedbackTriggered = true
            }
        } else {
            edgeFeedbackTriggered = false
        }
        if (hapticEffect != SliderDefaults.SliderHapticEffect.Step) return
        if (keyPointFractions.isNotEmpty()) {
            val currentFraction = normalize(currentValue)
            var nearestDistance = Float.MAX_VALUE
            for (point in keyPointFractions) {
                val distance = abs(point - currentFraction)
                if (distance < nearestDistance) nearestDistance = distance
            }
            val atKeyPoint = nearestDistance < 0.005f
            if (atKeyPoint && !isAtKeyPoint && !atEdge) feedback(NexioSliderHaptic.Step)
            isAtKeyPoint = atKeyPoint
        } else if ((steps > 0 || section) && currentValue != lastStepValue && !atEdge) {
            feedback(NexioSliderHaptic.Step)
            lastStepValue = currentValue
        }
    }

    /** 距 raw 最近且落在磁吸阈值内的锚点下标；无锚点或超出阈值返回 -1。 */
    private fun keyPointIndex(raw: Float): Int {
        if (keyPointFractions.isEmpty() || magnetThreshold <= 0f) return -1
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (i in keyPointFractions.indices) {
            val distance = abs(keyPointFractions[i] - raw)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return if (bestDistance <= magnetThreshold) best else -1
    }

    private fun quantize(next: Float): Float {
        val keyPoint = keyPointIndex(next)
        if (keyPoint >= 0) return keyPointFractions[keyPoint]
        return if (steps > 0 || section) index(next).toFloat() / intervals else next
    }

    private companion object {
        /** COUI dragSnap 的 `PathInterpolator(0, 0, 0.25, 1)`。 */
        val DragEasing = CubicBezierEasing(0f, 0f, 0.25f, 1f)

        /** Android `ValueAnimator` 的默认插值器 AccelerateDecelerateInterpolator。 */
        val AccelerateDecelerateEasing = object : Easing {
            override fun transform(fraction: Float): Float =
                (cos((fraction + 1f) * PI) / 2.0 + 0.5).toFloat()
        }
    }
}
