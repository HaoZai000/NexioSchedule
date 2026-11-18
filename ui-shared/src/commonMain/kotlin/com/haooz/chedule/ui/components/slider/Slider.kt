package com.haooz.chedule.ui.components.slider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.utils.isAppDarkTheme
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.SliderDefaults

/**
 * COUI 风格的跨平台滑块。
 *
 * 对外签名与 Miuix `Slider` 对齐，调用点只需替换 import。交互、物理动画与绘制全部位于
 * commonMain，Android / 桌面 / iOS / 鸿蒙共用同一份实现；唯一的平台差异是跟手光效依赖
 * 运行时着色器，由 softglass 的 `GlassRuntimeShader` 统一两端。
 *
 * 逻辑值与绘制位置分离维护：拖动时 thumb 贴手，吸附、外部赋值与惯性都通过弹簧过渡，
 * 因此调用方在 [onValueChange] 里连续回写也不会打断手势。
 *
 * @param value 当前值，应在 [valueRange] 内。
 * @param onValueChange 值变化回调，拖动过程中连续触发。
 * @param modifier 应用于整条滑条的修饰符。
 * @param enabled 是否可交互；false 时只读并置灰。
 * @param valueRange 取值区间。
 * @param steps 等分档位数（不含两端），0 表示连续；与 [keyPoints] 同时给出时以 [keyPoints] 为准。
 * @param onValueChangeFinished 手势结束回调。
 * @param showKeyPoints 是否绘制锚点刻度。
 * @param keyPoints 自定义锚点（[valueRange] 内的绝对值）；null 时使用 [steps] 的等分位置。
 * @param magnetThreshold 磁吸阈值，占 [valueRange] 的比例，0.05 表示 5%。
 * @param hapticEffect 触感策略，语义与 Miuix 的 `SliderHapticEffect` 一致。
 * @param colors 配色覆盖；null 时使用 [NexioSliderDefaults.colors]。
 * @param shape 轨道形状，同时用于裁剪跟手光效。
 * @param dark 是否深色，影响跟手光效强度。
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    showKeyPoints: Boolean = false,
    keyPoints: List<Float>? = null,
    magnetThreshold: Float = 0.02f,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
    colors: NexioSliderColors? = null,
    shape: Shape = NexioCapsuleShape,
    dark: Boolean = isAppDarkTheme(),
) {
    // Miuix 的 keyPoints 是 valueRange 内的绝对刻度。归一化成 fraction 交给 COUI 磁吸，
    // 绝不能折算成等分 section 段数——那会让单锚点整条刻度消失、多锚点全部错位。
    val rangeStart = valueRange.start
    val rangeEnd = valueRange.endInclusive
    // 归一化结果只与锚点内容相关，缓存避免每次重组重新分配三个集合。
    val keyPointFractions = remember(keyPoints, rangeStart, rangeEnd) {
        val span = rangeEnd - rangeStart
        if (keyPoints.isNullOrEmpty() || span <= 0f) {
            emptyList()
        } else {
            keyPoints.mapNotNull { point ->
                val fraction = (point - rangeStart) / span
                if (fraction.isFinite() && fraction in 0f..1f) fraction else null
            }.distinct().sorted()
        }
    }
    SliderCore(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        colors = colors,
        dark = dark,
        shape = shape,
        valueRange = valueRange,
        steps = steps,
        showTickMarks = showKeyPoints,
        hapticEffect = hapticEffect,
        keyPointFractions = keyPointFractions,
        magnetThreshold = magnetThreshold,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished ?: {},
    )
}

/**
 * 滑条主体，签名与 COUI `CouiSeekBarAdvanced` 逐字对应。
 *
 * 与 [Slider] 的分工：[Slider] 负责把 Miuix 的绝对锚点归一化成 fraction，这里只处理
 * 「锚点 / 等分 section / 连续」三种模式，并负责手势、无障碍、键盘与绘制装配。
 *
 * @param showTickMarks 是否使用 COUI 的分段吸附与等分刻度。
 * @param keyPointFractions 已归一化的自定义锚点。
 */
@Composable
private fun SliderCore(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    colors: NexioSliderColors? = null,
    dark: Boolean = isAppDarkTheme(),
    shape: Shape = NexioCapsuleShape,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    showTickMarks: Boolean = false,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
    keyPointFractions: List<Float> = emptyList(),
    magnetThreshold: Float = 0f,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {},
) {
    val resolvedColors = colors ?: NexioSliderDefaults.colors(enabled)
    // 有自定义锚点时走连续跟手 + 磁吸；否则保留 COUI 原版的等分 / section 行为。
    val useKeyPoints = keyPointFractions.isNotEmpty()
    val coreSteps = if (useKeyPoints) 0 else steps
    val useSection = !useKeyPoints && showTickMarks
    require(steps >= 0 && steps < Int.MAX_VALUE)
    require(value.isFinite() && valueRange.start.isFinite() && valueRange.endInclusive.isFinite())
    require(valueRange.endInclusive >= valueRange.start && (valueRange.endInclusive - valueRange.start).isFinite())
    val interactive = enabled && valueRange.endInclusive > valueRange.start
    val density = LocalDensity.current.density
    val layoutDirection = LocalLayoutDirection.current
    val rtl = layoutDirection == LayoutDirection.Rtl
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    // 闭区间本身不实现 equals，直接当 key 会让状态机每次重组都被重建，这里拆成起止值。
    val rangeStart = valueRange.start
    val rangeEnd = valueRange.endInclusive
    val motion = remember(
        rangeStart, rangeEnd, coreSteps, useSection, density, keyPointFractions, magnetThreshold, hapticEffect, scope,
    ) {
        NexioSliderMotion(
            range = valueRange,
            steps = coreSteps,
            section = useSection,
            density = density,
            initialValue = value,
            scope = scope,
            onValueChange = { onChange(it) },
            onValueChangeFinished = { onFinished() },
            feedback = { event ->
                hapticFeedback.performHapticFeedback(
                    when (event) {
                        NexioSliderHaptic.Edge -> HapticFeedbackType.GestureThresholdActivate
                        NexioSliderHaptic.Step -> HapticFeedbackType.TextHandleMove
                    },
                )
            },
            hapticEffect = hapticEffect,
            keyPointFractions = keyPointFractions,
            magnetThreshold = magnetThreshold,
        )
    }
    val touch = remember(scope, density) { NexioTouchMotion(scope, density) }
    val renderer = remember(density, shape, layoutDirection) { NexioSliderRenderer(density, shape, layoutDirection) }
    val (spotlightInput, drawSpotlight) = rememberNexioSpotlight(
        interactive = interactive,
        preset = SpotlightPreset.OpaqueMedium1,
        dark = dark,
    )
    SideEffect {
        motion.syncValue(value)
        if (!interactive) {
            motion.cancelGesture()
            touch.release(Offset.Zero)
        }
    }
    DisposableEffect(motion) {
        onDispose { motion.dispose() }
    }
    DisposableEffect(touch) {
        onDispose { touch.dispose() }
    }
    val span = valueRange.endInclusive - valueRange.start
    val keyIncrement = span / if (coreSteps > 0 || useSection) coreSteps + 1 else 100
    Box(
        modifier
            .defaultMinSize(minWidth = 144.dp, minHeight = 48.dp)
            .progressSemantics(value.coerceIn(valueRange), valueRange, steps)
            .semantics {
                if (!interactive) disabled()
                setProgress { next ->
                    if (!interactive || !next.isFinite()) false
                    else {
                        motion.setFromAction(next)
                        true
                    }
                }
            }
            .onKeyEvent { event ->
                if (!interactive || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val next = when (event.key) {
                    Key.DirectionLeft -> motion.value + keyIncrement * if (rtl) 1f else -1f
                    Key.DirectionRight -> motion.value + keyIncrement * if (rtl) -1f else 1f
                    Key.DirectionUp -> motion.value + keyIncrement
                    Key.DirectionDown -> motion.value - keyIncrement
                    Key.MoveHome -> valueRange.start
                    Key.MoveEnd -> valueRange.endInclusive
                    else -> return@onKeyEvent false
                }
                motion.setFromAction(next)
                true
            }
            .focusable(interactive)
            .onSizeChanged { motion.resize(it.width - 32f * density) }
            .pointerInput(motion, touch, interactive, rtl) {
                if (!interactive) return@pointerInput
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (motion.length <= 0f) return@awaitEachGesture
                    val tracker = VelocityTracker()
                    var active = down.id
                    var pointerOffset = 0f
                    var origin = down.position
                    var dragging = false
                    var completed = false
                    fun fingerX(x: Float): Float {
                        val local = x - pointerOffset - touch.translationX.value - 16f * density
                        return if (rtl) motion.length - local else local
                    }
                    fun velocityX(): Float =
                        tracker.calculateVelocity().x.coerceIn(-8000f, 8000f) * if (rtl) -1f else 1f
                    try {
                        tracker.addChange(down)
                        motion.press(fingerX(down.position.x))
                        touch.begin(down.position, size)
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.isNexioTouchCancelled()) break
                            val tracked = event.changes.firstOrNull { it.id == active } ?: break
                            val next = if (!tracked.pressed) event.changes.firstOrNull { it.pressed } else null
                            if (next != null) {
                                pointerOffset += next.position.x - tracked.position.x
                                origin += next.position - tracked.position
                                touch.switchPointer(tracked.position, next.position)
                                active = next.id
                                tracker.resetTracking()
                                tracker.addChange(next)
                                continue
                            }
                            if (tracked.isConsumed) break
                            if (!tracked.pressed) {
                                val local = fingerX(tracked.position.x)
                                val inside = tracked.position.x in 0f..size.width.toFloat() &&
                                    tracked.position.y in 0f..size.height.toFloat()
                                if (dragging || inside) {
                                    val velocity = velocityX()
                                    if (dragging) motion.drag(local, velocity)
                                    motion.release(local, velocity)
                                    tracked.consume()
                                } else motion.cancelGesture()
                                touch.release(Offset(velocityX() * 0.0003f, 0f))
                                completed = true
                                break
                            }
                            tracker.addChange(tracked)
                            val delta = tracked.position - origin
                            if (!dragging && abs(delta.y) > touchSlop && abs(delta.y) > abs(delta.x)) break
                            if (abs(delta.x) >= 5f) touch.move(Offset(tracked.position.x, down.position.y))
                            if (dragging || abs(delta.x) > touchSlop) {
                                dragging = true
                                motion.drag(fingerX(tracked.position.x), velocityX())
                                tracked.consume()
                            }
                        }
                    } finally {
                        if (!completed) {
                            motion.cancelGesture()
                            touch.release(Offset.Zero)
                        }
                    }
                }
            },
    ) {
        Canvas(
            Modifier.matchParentSize()
                .graphicsLayer {
                    translationX = touch.translationX.value
                }
                .then(spotlightInput),
        ) {
            renderer.render(
                scope = this,
                height = size.height,
                motion = motion,
                colors = resolvedColors,
                enabled = interactive,
                rtl = rtl,
                tickCount = if (useSection) coreSteps + 1 else 0,
                tickFractions = if (useKeyPoints && showTickMarks) keyPointFractions else null,
            )
            if (drawSpotlight != null) {
                renderer.clipTrack(this) { drawSpotlight() }
            }
        }
    }
}

/** 历史点按原始时间戳回放，与 AndroidX 的 `MotionEvent` 批量喂入等价。 */
private fun VelocityTracker.addChange(change: PointerInputChange) {
    change.historical.forEach { addPosition(it.uptimeMillis, it.position) }
    addPosition(change.uptimeMillis, change.position)
}
