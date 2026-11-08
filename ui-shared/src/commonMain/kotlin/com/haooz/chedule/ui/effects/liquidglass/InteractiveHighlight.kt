package com.haooz.chedule.ui.effects.liquidglass

import com.haooz.softglass.GlassEnvironment
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import com.haooz.softglass.platform.GlassRuntimeShader
import com.haooz.softglass.platform.createGlassRuntimeShader
import com.haooz.softglass.platform.asComposeShader
import com.haooz.softglass.platform.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    /** 光晕半径倍率（实际半径 = 半径基准 * 1.5 * 本值）。在 draw 阶段读，可传会变的值。 */
    val radiusScale: () -> Float = { 1f },
    /**
     * 光晕半径上限（dp），默认 null = 不限、直接用节点 minDimension。
     * 取 min(自身尺寸, 本值) 而非固定值：收起态是个 42dp 小圆按钮，固定大基准会让光晕漫出圆外。
     */
    val radiusBaseDp: Dp? = null,
    /**
     * 是否**固定**光晕半径、不再与节点 minDimension 取 min（默认 false）。
     * 用于「面板本身很小、但光晕需要固定大小」的场合（如 ShortcutMenu，minOf 会把基准夹小）。
     * ⚠ true 时必须给 [radiusBaseDp]，否则回退节点 minDimension。
     */
    val fixedRadius: Boolean = false,
    /**
     * 按压进度改由**外部状态**驱动（非 null 时忽略自身手势与弹簧）。
     * 用于不能挂手势的场景：全屏手势层压在最上层会挡住下层兄弟节点，整页都点不动。
     */
    val pressProgressOverride: (() -> Float)? = null,
    /** 是否画那层整幅 8% 提亮。按钮/菜单要整面提亮；只要圆形光晕的场景传 false。 */
    val drawFlatOverlay: Boolean = true,
    /**
     * **浅色**模式的径向峰值基准（深色恒为 0.15）。
     * null = 走全局规则：页背后有壁纸时 0.1，其余浅色 0.05（近白底加白会被截断成白斑）。
     */
    val lightPeakAlpha: Float? = null
) {

    /** 按压进度与拖动位移共用同一条弹簧曲线（改这里即可整体调节回弹手感）。 */
    private companion object {
        const val SPRING_DAMPING_RATIO = 0.5f
        const val SPRING_STIFFNESS = 300f
    }

    /** 结束阈值：按压与位移统一用 0.001。Offset 版阈值必须是 `Offset(x, x)`，不能写裸 Float */
    private val visibilityThreshold = 0.001f
    private val offsetVisibilityThreshold = Offset(visibilityThreshold, visibilityThreshold)

    private val pressProgressAnimationSpec =
        spring(SPRING_DAMPING_RATIO, SPRING_STIFFNESS, visibilityThreshold)
    private val positionAnimationSpec =
        spring(SPRING_DAMPING_RATIO, SPRING_STIFFNESS, offsetVisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, visibilityThreshold)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, offsetVisibilityThreshold)

    /**
     * 形变位移，独立于高光位置。
     *
     * 高光松手时原地淡出（positionAnimation 停在松手处），若形变从它派生就会永远保持
     * 拖动距离而不回弹，所以这里单独跟一根动画、松手回弹到 0。
     */
    private val dragOffsetAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, offsetVisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = dragOffsetAnimation.value

    private val shader =
        if (isRuntimeShaderSupported()) {
            createGlassRuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressOverride?.invoke() ?: pressProgressAnimation.value
            if (progress > 0f) {
                // 半径/圆心两分支共用，避免 shader 与 Brush 各算一份漂移掉
                val base = when {
                    radiusBaseDp == null -> size.minDimension
                    fixedRadius -> radiusBaseDp.toPx()
                    else -> minOf(size.minDimension, radiusBaseDp.toPx())
                }
                val radius = base * 1.5f * radiusScale()
                val pos = position(size, positionAnimation.value)
                val haloCenter = Offset(
                    pos.x.fastCoerceIn(0f, size.width),
                    pos.y.fastCoerceIn(0f, size.height)
                )
                // 深色恒 0.15；浅色默认 0.05（有壁纸的今日/课程表页 0.1）
                val peak = (
                    if (GlassEnvironment.isDark) 0.15f
                    else lightPeakAlpha
                        ?: if (GlassEnvironment.hasWallpaper) 0.1f else 0.05f
                    ) * progress
                if (drawFlatOverlay) {
                    drawRect(
                        Color.White.copy(0.08f * progress),
                        blendMode = BlendMode.Plus
                    )
                }
                if (shader != null) {
                    shader.apply {
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", Color.White.copy(peak))
                        setFloatUniform("radius", radius)
                        setFloatUniform("position", haloCenter.x, haloCenter.y)
                    }
                    // 强度在半径外恒为 0：裁到光晕包围盒再画，整屏平铺是白刷一遍像素
                    clipRect(
                        left = haloCenter.x - radius,
                        top = haloCenter.y - radius,
                        right = haloCenter.x + radius,
                        bottom = haloCenter.y + radius,
                    ) {
                        drawRect(
                            ShaderBrush(shader.asComposeShader()),
                            blendMode = BlendMode.Plus
                        )
                    }
                } else {
                    // 无 GlassRuntimeShader（API<33）：改画径向渐变而非整块平铺 —— 平铺没有圆形
                    // 边界，在「只要圆形光晕」的场景就是一整屏白。收边比 shader 宽，观感略散。
                    clipRect(
                        left = haloCenter.x - radius,
                        top = haloCenter.y - radius,
                        right = haloCenter.x + radius,
                        bottom = haloCenter.y + radius,
                    ) {
                        drawRect(
                            brush = Brush.radialGradient(
                                center = haloCenter,
                                radius = radius,
                                colors = listOf(
                                    Color.White.copy(peak),
                                    Color.White.copy(peak),
                                    Color.Transparent,
                                ),
                            ),
                            blendMode = BlendMode.Plus
                        )
                    }
                }
            }

            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                        launch { dragOffsetAnimation.snapTo(Offset.Zero) }
                    }
                },
                // 松手：形变回弹到起点，高光原地淡出（positionAnimation 不动）——
                // 高光若也回弹到按下点，就会出现「拖过去再滑回来」的位移感。
                onDragEnd = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { dragOffsetAnimation.animateTo(Offset.Zero, positionAnimationSpec) }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { dragOffsetAnimation.animateTo(Offset.Zero, positionAnimationSpec) }
                    }
                },
                // 纯视觉观察者：翻页手势吃掉横/竖拉后仍继续跟手，保证各方向都有位移
                observeConsumed = true,
            ) { change, _ ->
                animationScope.launch {
                    positionAnimation.snapTo(change.position)
                    dragOffsetAnimation.snapTo(change.position - startPosition)
                }
            }
        }

    // 只处理按压效果，不处理拖动
    val pressOnlyModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            awaitEachGesture {
                val down = awaitFirstDown()
                // 不消费指针事件，让 clickable 能够接收点击
                animationScope.launch {
                    pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec)
                }
                // 等待所有手指释放
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                } while (event.changes.any { it.pressed })
                animationScope.launch {
                    pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec)
                }
            }
        }
}
