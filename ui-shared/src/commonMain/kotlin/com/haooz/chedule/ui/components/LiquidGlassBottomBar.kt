package com.haooz.chedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.haooz.nexioui.interaction.DampedDragAnimation
import com.haooz.nexioui.interaction.InteractiveHighlight
import com.haooz.softglass.GlassEnvironment
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberCombinedBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.backdrop.shadow.Shadow
import com.haooz.softglass.glass.BarFill
import com.haooz.softglass.glass.BrightLargeTone
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.softglass.shape.ContinuousCapsule
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * 液态玻璃档的底栏
 */
@Composable
fun LiquidGlassBottomBar(
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    backdrop: GlassBackdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    barHeight: Dp = 56.dp,
    sliderHeight: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val isLightTheme = !isAppDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.4f) else Color(0xFF121212).copy(alpha = 0.4f)
    val tabsBackdrop = rememberLayerBackdrop()
    val sliderBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    val shape: () -> androidx.compose.ui.graphics.Shape = remember { { ContinuousCapsule() } }

    // 底栏背景直接用应用现有的液态玻璃材质（与顶栏按钮 / 下拉面板同一套）
    val glassBlur = AppMaterialSettings.glassBlurDp()
    val glassEffectsOn = AppMaterialSettings.glassEffectsEnabled()
    val barGlassStyle = AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = !isLightTheme,
        fill = BarFill,
        tone = BrightLargeTone,
    )
    val barEffects: GlassEffectScope.() -> Unit =
        remember(glassBlur, glassEffectsOn, barGlassStyle) {
            {
                if (glassEffectsOn) {
                    blur(glassBlur.dp.toPx(), TileMode.Mirror)
                    padding = 0f
                    runtimeShaderEffect(
                        key = LIQUID_BAR_GLASS_KEY,
                        shaderString = SOFT_GLASS_LIVE_SHADER,
                        uniformShaderName = "content",
                    ) {
                        applySoftGlass(
                            style = barGlassStyle,
                            width = size.width,
                            height = size.height,
                        )
                    }
                }
            }
        }
    // 隐藏录制层要和底栏用同一套材质，滑块采样到的才是「底栏的玻璃」
    val tabsEffects: GlassEffectScope.() -> Unit =
        remember(glassBlur, glassEffectsOn, barGlassStyle) {
            {
                if (glassEffectsOn) {
                    blur(glassBlur.dp.toPx(), TileMode.Mirror)
                    padding = 0f
                    runtimeShaderEffect(
                        key = LIQUID_TABS_GLASS_KEY,
                        shaderString = SOFT_GLASS_LIVE_SHADER,
                        uniformShaderName = "content",
                    ) {
                        applySoftGlass(
                            style = barGlassStyle,
                            width = size.width,
                            height = size.height,
                        )
                    }
                }
            }
        }
    // 无材质档才需要实色底；玻璃档由材质自己画
    val surface: DrawScope.() -> Unit = remember(containerColor) { { drawRect(containerColor) } }

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val densityScale = density.density
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        // ⚠ 不能把 selectedTabIndex 当 remember key：调用方每次重组都会传新 lambda，
        // currentIndex 会被冲成新 state，旧的 snapshotFlow 还盯着老对象，点击后滑块就不动了。
        val latestSelectedIndex by rememberUpdatedState(selectedTabIndex)
        var currentIndex by remember { mutableIntStateOf(latestSelectedIndex()) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(Unit) {
            snapshotFlow { latestSelectedIndex() }
                .collectLatest { index -> currentIndex = index }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) {
                            (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        } else {
                            size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        },
                        size.height / 2f
                    )
                },
                isDark = { GlassEnvironment.isDark },
            )
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = shape,
                    effects = barEffects,
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = if (glassEffectsOn) null else surface,
                )
                .then(interactiveHighlight.modifier)
                .height(barHeight)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) }
        ) {
            // 隐藏副本：把「底栏玻璃 + 图标」录进 tabsBackdrop，只给滑块当采样源
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = shape,
                        effects = tabsEffects,
                        onDrawSurface = if (glassEffectsOn) null else surface,
                    )
                    // 按压高光必须画在录制层里面，滑块才能采到它
                    .then(interactiveHighlight.modifier)
                    .height(barHeight)
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        // 选中滑块：采样 combined(page, tabs)，按按压进度做透镜 + 色散
        val sliderModifier = Modifier
            .padding(horizontal = 4.dp)
            .graphicsLayer {
                translationX = if (isLtr) {
                    dampedDragAnimation.value * tabWidth + panelOffset
                } else {
                    size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
            }
            .then(interactiveHighlight.gestureModifier)
            .then(dampedDragAnimation.modifier)
            .drawBackdrop(
                backdrop = sliderBackdrop,
                shape = shape,
                effects = {
                    val progress = dampedDragAnimation.pressProgress
                    liquidLens(
                        key = LIQUID_SLIDER_LENS_KEY,
                        heightPx = 10f * densityScale * progress,
                        amountPx = 14f * densityScale * progress,
                        dispersion = progress,
                    )
                },
                shadow = {
                    val progress = dampedDragAnimation.pressProgress
                    if (progress > 0.001f) Shadow(alpha = progress) else null
                },
                layerBlock = {
                    scaleX = dampedDragAnimation.scaleX
                    scaleY = dampedDragAnimation.scaleY
                    val velocity = dampedDragAnimation.velocity / 10f
                    scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                    scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                },
                onDrawSurface = {
                    val progress = dampedDragAnimation.pressProgress
                    drawRect(
                        if (isLightTheme) Color.Black.copy(alpha = 0.06f)
                        else Color.White.copy(alpha = 0.1f),
                        alpha = 1f - progress,
                    )
                    drawRect(Color.Black.copy(alpha = 0.03f * progress))
                },
            )
            // 滑块描边：浅色模式用白色描边，只在长按 / 拖动（pressProgress > 0）时出现
            .sliderBorder(
                width = 1.2.dp,
                color = if (isLightTheme) Color.White else Color.Gray,
                progress = { dampedDragAnimation.pressProgress },
                shape = ContinuousCapsule(),
            )
            .height(sliderHeight)
            .fillMaxWidth(1f / tabsCount)

        Box(sliderModifier)
    }
}

/** 液态档底栏 tab：点击选中 + 按下时随滑块一起放大。 */
internal val LocalLiquidBottomTabScale = staticCompositionLocalOf { { 1f } }

@Composable
fun RowScope.LiquidGlassBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(ContinuousCapsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val current = scale()
                scaleX = current
                scaleY = current
            },
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * 只能在绘制期读进度的描边：静态 alpha 的 `Modifier.border` 没法跟着按压渐显。
 */
private fun Modifier.sliderBorder(
    width: Dp,
    color: Color,
    progress: () -> Float,
    shape: Shape,
): Modifier = drawWithContent {
    drawContent()
    val alpha = 0.5f * progress()
    if (alpha <= 0.001f) return@drawWithContent
    val path = when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Generic -> outline.path
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }
    drawPath(path, color = color, alpha = alpha, style = Stroke(width.toPx()))
}

private const val LIQUID_BAR_GLASS_KEY = "NexioLiquidBottomBarGlass"
private const val LIQUID_TABS_GLASS_KEY = "NexioLiquidBottomBarTabsGlass"
private const val LIQUID_SLIDER_LENS_KEY = "NexioLiquidBottomBarSliderLens"

/**
 * 底栏 / 滑块共用的 lens（移植自 AndroidLiquidGlass 的 `RoundedRectRefractionShaderString`）。
 *
 * 只做折射位移与色散，不做调色：底栏和滑块要采的是「已经渲染好的玻璃内容」。
 * 静止时 `height` 为 0，等价于原样输出。
 */
private fun GlassEffectScope.liquidLens(
    key: String,
    heightPx: Float,
    amountPx: Float,
    dispersion: Float,
) {
    runtimeShaderEffect(
        key = key,
        shaderString = LIQUID_LENS_SHADER,
        uniformShaderName = "content",
    ) {
        val radius = minOf(size.width, size.height) * 0.5f
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", 0f, 0f)
        setFloatUniform("cornerRadii", radius, radius, radius, radius)
        setFloatUniform("refractionHeight", heightPx)
        setFloatUniform("refractionAmount", -amountPx)
        setFloatUniform("depthEffect", 1f)
        setFloatUniform("dispersion", dispersion)
    }
}

private const val LIQUID_LENS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float dispersion;

float radiusAt(float2 c, float4 r) {
    if (c.x >= 0.0) {
        return c.y <= 0.0 ? r.y : r.z;
    } else {
        return c.y <= 0.0 ? r.x : r.w;
    }
}

float sdRoundedRect(float2 c, float2 halfSize, float radius) {
    float2 q = abs(c) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float2 gradSdRoundedRect(float2 c, float2 halfSize, float radius) {
    float2 q = abs(c) - halfSize + radius;
    if (q.x > 0.0 && q.y > 0.0) {
        return sign(c) * (q / max(length(q), 1e-4));
    }
    float2 g = (q.x > q.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
    return sign(c) * g;
}

float circleMap(float x) {
    return 1.0 - sqrt(max(1.0 - x * x, 0.0));
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centered = (coord + offset) - halfSize;
    float radius = radiusAt(centered, cornerRadii);
    float h = max(refractionHeight, 1e-3);

    float sd = sdRoundedRect(centered, halfSize, radius);
    if (-sd >= h) {
        return content.eval(coord);
    }

    float ramp = circleMap(clamp(1.0 + sd / h, 0.0, 1.0));
    float d = ramp * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centered, halfSize, gradRadius)
        + depthEffect * centered / max(length(centered), 1e-4);
    grad = grad / max(length(grad), 1e-4);
    float2 uv = coord + d * grad;

    if (dispersion <= 0.0) {
        return content.eval(uv);
    }

    float2 spread = d * grad * (dispersion * centered.x * centered.y /
        max(halfSize.x * halfSize.y, 1e-3));
    half4 color = half4(0.0);
    half4 s0 = content.eval(uv + spread);
    color.r += s0.r / 3.5; color.a += s0.a / 7.0;
    half4 s1 = content.eval(uv + spread * (2.0 / 3.0));
    color.r += s1.r / 3.5; color.g += s1.g / 7.0; color.a += s1.a / 7.0;
    half4 s2 = content.eval(uv + spread * (1.0 / 3.0));
    color.r += s2.r / 3.5; color.g += s2.g / 3.5; color.a += s2.a / 7.0;
    half4 s3 = content.eval(uv);
    color.g += s3.g / 3.5; color.a += s3.a / 7.0;
    half4 s4 = content.eval(uv - spread * (1.0 / 3.0));
    color.g += s4.g / 3.5; color.b += s4.b / 3.0; color.a += s4.a / 7.0;
    half4 s5 = content.eval(uv - spread * (2.0 / 3.0));
    color.b += s5.b / 3.0; color.a += s5.a / 7.0;
    half4 s6 = content.eval(uv - spread);
    color.r += s6.r / 7.0; color.b += s6.b / 3.0; color.a += s6.a / 7.0;
    return color;
}
"""


