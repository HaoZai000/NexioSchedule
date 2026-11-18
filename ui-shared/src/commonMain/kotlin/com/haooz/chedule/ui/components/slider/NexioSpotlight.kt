package com.haooz.chedule.ui.components.slider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.haooz.softglass.platform.GlassRuntimeShader
import com.haooz.softglass.platform.asComposeShader
import com.haooz.softglass.platform.createGlassRuntimeShader
import com.haooz.softglass.platform.isRuntimeShaderSupported
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 跟手光效预设，移植自 COUI 的 `SpotLight`。
 *
 * 半径与光源高度使用设备像素，不随 density 换算。
 *
 * @property lightIntensity 浅色模式光强。
 * @property darkIntensity 深色模式光强。
 * @property endRadius 光斑完全展开时的半径（像素）。
 * @property darkBlendMode 深色模式使用的混合模式。
 * @property lightBlendMode 浅色模式使用的混合模式。
 * @property ambient 环境光强度。
 * @property diffuse 漫反射强度。
 * @property noise 噪声强度。
 */
enum class SpotlightPreset(
    internal val lightIntensity: Float,
    internal val darkIntensity: Float,
    internal val endRadius: Float,
    internal val darkBlendMode: BlendMode = BlendMode.Overlay,
    internal val lightBlendMode: BlendMode = BlendMode.Overlay,
    internal val ambient: Float = 0.4f,
    internal val diffuse: Float = 0.4f,
    internal val noise: Float = 0.08f,
) {
    TransparentSmall1(1f, 1f, 400f),
    TranslucentLarge1(0.6f, 0.1f, 800f),
    OpaqueSmall1(0.6f, 0.5f, 500f),
    OpaqueMedium1(0.6f, 0.8f, 500f),
    OpaqueSmall2(1f, 0.15f, 500f, BlendMode.Plus),
    OpaqueMedium2(1f, 0.15f, 500f, BlendMode.Plus),
    ChipTab(0.2f, 0.3f, 600f, BlendMode.SrcOver, BlendMode.Plus, 0.1f, 0.5f, 0f),
}

private data class SpotlightStyle(
    val intensity: Float,
    val ambient: Float,
    val diffuse: Float,
    val lightHeight: Float,
    val noise: Float,
    val dither: Float,
    val blendMode: BlendMode = BlendMode.Overlay,
    val startRadius: Float = 100f,
    val endRadius: Float = 800f,
    val color: Color = Color.White,
)

/**
 * 创建一个跟手光效，返回「触摸跟随修饰符」与「绘制回调」。
 *
 * 只观察触摸，不拦截点击、滑选与滚动；绘制回调应放在透明前景层，光效熄灭时返回 null。
 * 平台不可用（无运行时着色器）时返回 `Modifier to null`，调用方可直接跳过绘制。
 *
 * @param interactive 是否可交互；false 时不注册触摸监听。
 * @param preset 光效预设。
 * @param dark 是否深色，决定光强与混合模式。
 * @param intensity 光强覆盖值，null 时取 [preset]。
 * @param ambient 环境光覆盖值。
 * @param diffuse 漫反射覆盖值。
 * @param lightHeight 光源高度覆盖值。
 * @param noise 噪声覆盖值。
 * @param dither 抖动覆盖值。
 * @param blendMode 混合模式覆盖值。
 * @param startRadius 光斑初始半径覆盖值。
 * @param endRadius 光斑最终半径覆盖值。
 * @param color 光色覆盖值。
 * @param inputTarget 把触点映射到目标坐标系；返回 null 时取消当前光效会话。
 * @param cancelOnConsumedMove 父容器接管滚动后取消会话，直到所有手指抬起。
 * @param acceptsGesture 判断某次按下是否应点亮。
 * @param scaleRadiusToBounds 是否按目标尺寸对角线缩放光斑半径。
 * @return 修饰符与绘制回调；平台不支持或着色器编译失败时回调为 null。
 */
@Composable
fun rememberNexioSpotlight(
    interactive: Boolean,
    preset: SpotlightPreset,
    dark: Boolean,
    intensity: Float? = null,
    ambient: Float? = null,
    diffuse: Float? = null,
    lightHeight: Float? = null,
    noise: Float? = null,
    dither: Float? = null,
    blendMode: BlendMode? = null,
    startRadius: Float? = null,
    endRadius: Float? = null,
    color: Color? = null,
    inputTarget: ((Offset, Boolean) -> LayoutCoordinates?)? = null,
    cancelOnConsumedMove: Boolean = false,
    acceptsGesture: ((IntSize, Offset) -> Boolean)? = null,
    scaleRadiusToBounds: Boolean = false,
): Pair<Modifier, (DrawScope.() -> Unit)?> {
    if (!isRuntimeShaderSupported()) return Modifier to null
    val shader = remember {
        runCatching { createGlassRuntimeShader(SpotlightShader) }.getOrNull()
    }
    if (shader == null) return Modifier to null
    val style = SpotlightStyle(
        intensity = intensity ?: if (dark) preset.darkIntensity else preset.lightIntensity,
        ambient = ambient ?: preset.ambient,
        diffuse = diffuse ?: preset.diffuse,
        lightHeight = lightHeight ?: 200f,
        noise = noise ?: preset.noise,
        dither = dither ?: 0f,
        blendMode = blendMode ?: if (dark) preset.darkBlendMode else preset.lightBlendMode,
        startRadius = startRadius ?: 100f,
        endRadius = endRadius ?: preset.endRadius,
        color = color ?: Color.White,
    )
    return runtimeSpotlight(
        interactive, style, shader, inputTarget, cancelOnConsumedMove,
        acceptsGesture, scaleRadiusToBounds,
    )
}

@Composable
private fun runtimeSpotlight(
    interactive: Boolean,
    style: SpotlightStyle,
    shader: GlassRuntimeShader,
    inputTarget: ((Offset, Boolean) -> LayoutCoordinates?)?,
    cancelOnConsumedMove: Boolean,
    acceptsGesture: ((IntSize, Offset) -> Boolean)?,
    scaleRadiusToBounds: Boolean,
): Pair<Modifier, (DrawScope.() -> Unit)?> {
    val currentInputTarget by rememberUpdatedState(inputTarget)
    val currentAcceptsGesture by rememberUpdatedState(acceptsGesture)
    val scope = rememberCoroutineScope()
    val light = remember(style.startRadius, style.endRadius, scaleRadiusToBounds, scope) {
        SpotlightState(style.startRadius, style.endRadius, scaleRadiusToBounds, scope)
    }
    DisposableEffect(light) {
        onDispose { light.dispose() }
    }
    val visible by remember(light) {
        derivedStateOf(structuralEqualityPolicy()) { light.progress > 0f }
    }
    var inputCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var inputSize by remember { mutableStateOf(IntSize.Zero) }
    val touchModifier = Modifier
        .onGloballyPositioned { inputCoordinates = it }
        .onSizeChanged { inputSize = it; if (inputTarget == null) light.setSize(it) }
        .pointerInput(light, interactive, inputTarget == null, cancelOnConsumedMove) {
            if (!interactive) return@pointerInput
            try {
                awaitPointerEventScope {
                    var activePointer: PointerId? = null
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.isNexioTouchCancelled()) {
                            activePointer = null
                            light.release()
                            cancelled = false
                            continue
                        }
                        if (cancelled) {
                            if (event.changes.none { it.pressed }) cancelled = false
                            continue
                        }
                        val wasIdle = activePointer == null
                        val newPointer = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                        val tracked = event.changes.firstOrNull { it.id == activePointer }
                        // 与 drawable 一致: 新按下的手指接管光源, 抬起后交回仍按下的首指。
                        val change = if (newPointer != null) {
                            newPointer
                        } else if (tracked != null && !tracked.pressed) {
                            event.changes.firstOrNull { it.pressed } ?: tracked
                        } else {
                            tracked ?: if (currentInputTarget != null) event.changes.firstOrNull { it.pressed } else null
                        } ?: continue
                        if (wasIdle && currentAcceptsGesture?.invoke(size, change.position) == false) continue
                        val target = currentInputTarget?.invoke(change.position, !wasIdle)
                        if (currentInputTarget != null && target?.isAttached != true) {
                            light.release()
                            activePointer = null
                            continue
                        }
                        val bounds = target?.size ?: size
                        if (bounds.width <= 0 || bounds.height <= 0) continue
                        val localPosition = if (target != null) {
                            val input = inputCoordinates?.takeIf { it.isAttached } ?: continue
                            target.localPositionOf(input, change.position)
                        } else change.position
                        light.setSize(bounds)
                        val position = Offset(
                            localPosition.x / bounds.width,
                            localPosition.y / bounds.height,
                        )
                        light.move(position)
                        activePointer = change.id
                        if (wasIdle) light.press()
                        if (!change.pressed) {
                            activePointer = null
                            light.release()
                        }
                        if (cancelOnConsumedMove) {
                            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                            if (activePointer != null && finalEvent.changes.any {
                                    it.isConsumed && it.pressed && it.position != it.previousPosition
                                }) {
                                activePointer = null
                                light.release()
                                cancelled = true
                            }
                        }
                    }
                }
            } finally {
                light.release()
            }
        }
    val drawSpotlight: DrawScope.() -> Unit = {
        val progress = light.progress
        if (progress > 0f && size.width > 0f && size.height > 0f) {
            val position = light.position
            shader.setFloatUniform(
                "u_lightPosAndHeight",
                position.x * size.width,
                position.y * size.height,
                light.radius,
                style.intensity * progress,
            )
            shader.setFloatUniform(
                "u_lightColorAndAmbient",
                style.color.red,
                style.color.green,
                style.color.blue,
                style.ambient,
            )
            shader.setFloatUniform("u_lightingParams", style.diffuse, style.lightHeight, style.noise, style.dither)
            // 与 COUISpotLightEffectDrawable 一致: 整层铺 shader, 混合模式交给预设。
            drawRect(ShaderBrush(shader.asComposeShader()), blendMode = style.blendMode)
        }
    }
    return touchModifier to if (visible) drawSpotlight else null
}

/**
 * 光效状态：淡入淡出进度、光源位置与半径。
 *
 * 位置直接贴手，只有淡入淡出走弹簧，避免快速滑动时光斑落在手指后面。
 */
private class SpotlightState(
    private val startRadius: Float,
    private val endRadius: Float,
    private val scaleRadiusToBounds: Boolean,
    private val scope: CoroutineScope,
) {
    private var disposed = false
    private var extinguishing = false
    private var bounds = IntSize.Zero
    private var progressTarget = 0f
    private var positionXTarget = 0f
    private var positionYTarget = 0f

    // 原厂 0..10000 的进度与 1f 终止阈值, 归一化成 0..1 后阈值等价为 0.0001。
    private val progressSpring = NexioSpring(0f, scope, 0.0001f) { onProgressUpdate() }
    private val positionX = NexioSpring(0f, scope, 0.0001f)
    private val positionY = NexioSpring(0f, scope, 0.0001f)

    /** 淡入淡出进度（0..1）。 */
    val progress: Float get() = progressSpring.value

    /** 光源位置，归一化到 0..1。 */
    val position: Offset get() = Offset(positionX.value, positionY.value)

    /** 光斑半径（像素）。 */
    var radius by mutableFloatStateOf(0f)
        private set

    /** 同步绘制区域尺寸。 */
    fun setSize(size: IntSize) {
        if (bounds == size) return
        bounds = size
        if (scaleRadiusToBounds && progressSpring.value > 0f) updateRadius()
    }

    /**
     * 更新光源位置。
     *
     * @param target 归一化坐标（0..1）。
     */
    fun move(target: Offset) {
        if (disposed) return
        positionXTarget = target.x
        positionYTarget = target.y
        // 光源直接贴手；0.35s 的跟随弹簧在快速滑动时会滞后十几像素。
        positionX.snapTo(target.x)
        positionY.snapTo(target.y)
    }

    /** 开始点亮。 */
    fun press() {
        if (disposed) return
        extinguishing = false
        progressTarget = 1f
        progressSpring.animateTo(1f, 0.15f, 0f, minimumChange = 0.0001f)
    }

    /** 开始熄灭。 */
    fun release() {
        if (disposed || extinguishing) return
        extinguishing = true
        progressTarget = 0f
        progressSpring.animateTo(0f, 0.4f, 0f, minimumChange = 0.0001f)
    }

    /** 释放协程。 */
    fun dispose() {
        // 防止 pointerInput 的 finally 在移除节点后重新启动帧回调。
        disposed = true
        progressSpring.cancel()
        positionX.cancel()
        positionY.cancel()
    }

    private fun onProgressUpdate() {
        val value = progressSpring.value
        if (value == 0f) {
            radius = 0f
            extinguishing = false
            positionX.snapTo(positionXTarget)
            positionY.snapTo(positionYTarget)
        } else {
            updateRadius()
            // 越界时原厂直接 skipToEnd；这里延后一帧跳到目标，避免在自身动画帧内取消自己。
            if (spotLightOutOfBounds()) scope.launch { progressSpring.snapTo(progressTarget) }
        }
    }

    private fun updateRadius() {
        val end = if (scaleRadiusToBounds) hypot(bounds.width.toFloat(), bounds.height.toFloat()) else endRadius
        val start = if (scaleRadiusToBounds) end * (startRadius / endRadius) else startRadius
        val progress = progressSpring.value
        radius = start + (end - start) * progress +
            if (extinguishing) 2f * end * (1f - progress) else 0f
    }

    private fun spotLightOutOfBounds(): Boolean {
        val x = positionX.value * bounds.width
        val y = positionY.value * bounds.height
        return x - radius > bounds.width || x + radius < 0f ||
            y - radius > bounds.height || y + radius < 0f
    }
}
