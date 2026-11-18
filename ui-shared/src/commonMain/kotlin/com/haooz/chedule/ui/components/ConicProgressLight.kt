/*
 * COUI IntentSeekBar 的锥形光进度条
 */
package com.haooz.chedule.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI

/** 条高两态（dp）：静止细线 ↔ 有进度满血条。 */
private const val RULE_HEIGHT_DP = 0.5f
private const val BAR_HEIGHT_DP = 5f

/**
 * 锥光 AGSL，逐 token 搬运自 com.coui.appcompat.seekbar.shader.ConicLightShaderParams。
 * 两束光锥叠加，只画在进度头后方；Android 33+ 由 androidMain 的 RuntimeShader 编译。
 */
internal const val CONIC_LIGHT_SHADER = """
uniform float2 u_resolution;
uniform float3 u_startColor;
uniform float3 u_endColor;
uniform float4 u_progressHeightParams;
uniform float4 u_basicParams;
uniform float4 u_alphaParams;
uniform float4 u_coneParams;
uniform float4 u_miscParams;

const float TO_POINT = 0.003921;

float3 blendPlusLighter(float3 base, float3 blend) {
    return min(float3(1.0), base + blend);
}
float4 getBloom(float2 q, float coneK, float alpha1, float2 fragCoord) {
    float u_height = u_progressHeightParams.z;
    float u_heightFalloff = u_progressHeightParams.w;
    float u_alpha = u_basicParams.y;
    float w0 = u_alphaParams.z;
    float u_spotRadius = u_alphaParams.w;
    float radialFalloff = u_coneParams.w;
    float axialFalloff = u_coneParams.z;
    float u_rectWidth = u_progressHeightParams.y;
    float u_process = u_progressHeightParams.x;

    float xp = max(-q.x, 0.0);
    float feather = u_spotRadius;
    float w0_px = w0 * feather;

    float wCone = coneK * xp + w0_px;
    float w = mix(w0_px, wCone, step(0.0, xp));

    float abs_y = abs(q.y);
    float d = abs_y - w;
    float edgeMask = 1.0 - smoothstep(0.0, feather * 2.0, d);

    float rNorm = abs_y / (w + 1e-4);
    float radial = exp(-radialFalloff * rNorm * rNorm);

    float xpNorm = xp / feather;
    float axial = exp(-axialFalloff * xpNorm);

    float maxHeight = feather * u_height;
    float heightFactor = clamp(xp / (maxHeight + 1e-4), 0.0, 1.0);
    float heightAttenuation = 1.0 - pow(heightFactor, u_heightFalloff);
    float cond = step(maxHeight, xp);
    float overDistance = (xp - maxHeight) / feather * cond;
    heightAttenuation *= exp(-overDistance * 3.0);

    float intensity = edgeMask * radial * heightAttenuation * axial;
    intensity *= smoothstep(0.0, 0.03 * feather, xp);

    float3 glowColor = mix(u_startColor, u_endColor, heightFactor) * TO_POINT;
    return half4(glowColor * intensity, intensity * alpha1 * u_alpha);
}

float max3(float3 v) {
    return max(max(v.r, v.g), v.b);
}

float4 premulForAdditiveBlend(float4 c) {
    c.rgb = clamp(c.rgb, 0.0, 1.0);
    c.a = clamp(max(c.a, max3(c.rgb)), 0.0, 1.0);
    return c;
}

half4 main(float2 fragCoord) {
    float u_process = u_progressHeightParams.x;
    float u_rectWidth = u_progressHeightParams.y;

    float u_alpha1 = u_alphaParams.x;
    float u_alpha2 = u_alphaParams.y;

    float coneK = u_coneParams.x;
    float second_coneK = u_coneParams.y;

    float u_lightBlendMode = u_miscParams.x;
    float is_RTL = u_miscParams.y;

    float2 pixelPos = fragCoord - u_resolution * 0.5;
    float spotX = (u_process - 0.5) * u_rectWidth;
    float2 q = pixelPos - float2(spotX, 0.0);

    q.x *= 1.0 - 2.0 * is_RTL;

    float4 blend_color = float4(0.0);
    float4 bloom_color = getBloom(q, coneK, u_alpha1, fragCoord);
    float4 bloom_color2 = getBloom(q, second_coneK, u_alpha2, fragCoord);

    float4 base = bloom_color;
    float4 blend = bloom_color2;
    blend_color.rgb = blendPlusLighter(base.rgb, blend.rgb);
    blend_color.a = base.a * (1.0 - blend.a) + blend.a;

    float percent = abs(blend.a - base.a) / max(blend_color.a, 0.000001);
    float mix_bg = step(blend.a, base.a) * percent;
    float mix_fg = step(base.a, blend.a) * percent;
    blend_color.rgb = mix(blend_color.rgb, base.rgb, mix_bg);
    blend_color.rgb = mix(blend_color.rgb, blend.rgb, mix_fg);

    float4 final_color = mix(blend_color, float4(0.0), step(0.0, q.x));

    return premulForAdditiveBlend(final_color);
}
"""

/** 锥光绘制器：每个使用点持有一份，uniform 互不覆盖。 */
internal interface ConicProgressLight {
    fun draw(
        canvas: Canvas,
        trackWidth: Float,
        rowHeight: Float,
        progress: Float,
        accent: Color,
        darkSurface: Boolean,
        alphaReveal: Float,
        shapeReveal: Float,
    )
}

/** 平台创建锥光绘制器；不支持或编译失败返回 null，调用方走径向渐变兜底。 */
internal expect fun createConicProgressLight(): ConicProgressLight?

/**
 * 今日助手卡片里的读条：无进度时是一条 0.5dp 细线，有进度时长成 [BAR_HEIGHT_DP]dp 并带 COUI 锥光。
 * 高度由 reveal 驱动（细线 ↔ 满血条），保留"从零涨起来 / 收起"的观感。
 *
 * @param progress 当前阶段进度（0f..1f），null 表示不读条
 * @param accent 进度固色，同时作为锥光色
 * @param foreground 分割线颜色
 * @param darkSurface 卡片是否为深色，决定轨道底色与锥光混合方式
 */
@Composable
internal fun CouiConicProgressRow(
    progress: Float?,
    accent: Color,
    foreground: Color,
    darkSurface: Boolean,
    modifier: Modifier = Modifier,
) {
    val active = progress != null
    val lightPainter = remember { createConicProgressLight() }
    val layoutDirection = LocalLayoutDirection.current
    val visibility = remember { MutableTransitionState(false) }.apply { targetState = active }
    val transition = rememberTransition(visibility, label = "coui-conic-progress")
    val reveal by transition.animateFloat(
        transitionSpec = { tween(if (targetState) 340 else 240, easing = FastOutSlowInEasing) },
        label = "coui-progress-reveal",
    ) { if (it) 1f else 0f }
    val alphaReveal by transition.animateFloat(
        transitionSpec = { conicLightSpring(if (targetState) 0.3f else 0.4f) },
        label = "coui-progress-light-alpha",
    ) { if (it) 1f else 0f }
    val shapeReveal by transition.animateFloat(
        transitionSpec = { conicLightSpring(if (targetState) 0.9f else 0.4f) },
        label = "coui-progress-light-shape",
    ) { if (it) 1f else 0f }
    val animatedValue by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(
            durationMillis = if (active) 1_000 else 220,
            easing = if (active) LinearEasing else FastOutSlowInEasing,
        ),
        label = "coui-progress-value",
    )
    val highlightAlpha by transition.animateFloat(
        transitionSpec = { conicLightSpring(0.5f) },
        label = "coui-progress-leading-edge",
    ) { if (it) 1f else 0f }

    Box(
        modifier.fillMaxWidth()
            // 高度两态：静止 0.5dp 细线 ↔ 满血条，跟随 reveal 长出/收回。
            // 只在测量期读 reveal：只触发重排，不重组整张卡；重排后 drawWithCache 自会重算缓存。
            .layout { measurable, constraints ->
                val h = (RULE_HEIGHT_DP + (BAR_HEIGHT_DP - RULE_HEIGHT_DP) * reveal).dp.roundToPx()
                val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                layout(placeable.width, h) { placeable.place(0, 0) }
            }
            .drawWithCache {
            val railClip = Path().apply {
                addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(size.height / 2f)))
            }
            val hairline = 1.dp.toPx().coerceAtMost(size.height)
            onDrawBehind {
                if (reveal < 0.999f) {
                    drawRect(
                        color = foreground.copy(alpha = 0.10f * (1f - reveal)),
                        topLeft = Offset(0f, (size.height - hairline) / 2f),
                        size = Size(size.width, hairline),
                    )
                }
                if (reveal > 0.01f) {
                    drawConicReadBar(
                        progress = animatedValue,
                        accent = accent,
                        darkSurface = darkSurface,
                        reveal = reveal,
                        alphaReveal = alphaReveal,
                        shapeReveal = shapeReveal,
                        highlightAlpha = highlightAlpha,
                        painter = lightPainter,
                        railClip = railClip,
                        rtl = layoutDirection == LayoutDirection.Rtl,
                    )
                }
            }
        },
    )
}

// COUISpringForce 临界弹簧：bounce 为零时 ω = 2π / response。
private fun conicLightSpring(response: Float) = spring<Float>(
    dampingRatio = 1f,
    stiffness = (2f * PI.toFloat() / response).let { it * it },
    visibilityThreshold = 0.001f,
)

private fun DrawScope.drawConicReadBar(
    progress: Float,
    accent: Color,
    darkSurface: Boolean,
    reveal: Float,
    alphaReveal: Float,
    shapeReveal: Float,
    highlightAlpha: Float,
    painter: ConicProgressLight?,
    railClip: Path,
    rtl: Boolean,
) {
    val drawLtr: DrawScope.() -> Unit = bar@{
        val trackHeight = size.height
        val halfHeight = trackHeight / 2f
        val radius = CornerRadius(halfHeight)
        val railColor = if (darkSurface) Color(0x26FFFFFF) else Color(0x14000000)
        drawRoundRect(color = railColor.copy(alpha = railColor.alpha * reveal), cornerRadius = radius)
        val safeProgress = progress.coerceIn(0f, 1f)
        val fillWidth = size.width * safeProgress
        if (fillWidth <= 0f) return@bar
        val capEnd = fillWidth + halfHeight
        // 进度本体与亮边裁进轨道；锥光在其外绘制，允许向上下弥散。
        clipPath(railClip) {
            drawRoundRect(
                color = accent.copy(alpha = accent.alpha * reveal),
                topLeft = Offset(-halfHeight, 0f),
                size = Size(fillWidth + trackHeight, trackHeight),
                cornerRadius = radius,
            )
            if (highlightAlpha > 0f) {
                val highlightWidth = (size.width * 0.2f).coerceAtMost(fillWidth)
                val capLeft = fillWidth - highlightWidth - halfHeight
                // 浅色卡最右端用进度色混一点白，避免纯白亮边把进度色盖没；深色保持纯白。
                // 亮边是低强度叠加，混白不会像锥光那样两束饱和过曝。
                val headTint = lerp(accent, Color.White, 0.98f)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        0f to headTint.copy(alpha = 0f),
                        1f to headTint.copy(alpha = highlightAlpha),
                        startX = capLeft,
                        endX = capEnd,
                    ),
                    topLeft = Offset(capLeft, 0f),
                    size = Size(capEnd - capLeft, trackHeight),
                    cornerRadius = radius,
                )
            }
        }
        if (painter != null) {
            painter.draw(
                canvas = drawContext.canvas,
                trackWidth = size.width,
                rowHeight = size.height,
                progress = safeProgress,
                accent = accent,
                darkSurface = darkSurface,
                alphaReveal = alphaReveal,
                shapeReveal = shapeReveal,
            )
        } else {
            drawFallbackConicGlow(fillWidth, accent, darkSurface, alphaReveal, shapeReveal)
        }
    }
    if (rtl) {
        scale(-1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) { drawLtr() }
    } else {
        drawLtr()
    }
}

/** 无 AGSL 的平台（桌面 / iOS / 鸿蒙 / Android < 33）用径向渐变近似光锥前缘。 */
private fun DrawScope.drawFallbackConicGlow(
    fillWidth: Float,
    accent: Color,
    darkSurface: Boolean,
    alphaReveal: Float,
    shapeReveal: Float,
) {
    if (alphaReveal <= 0f || fillWidth <= 0f) return
    val radius = (8f + 6f * shapeReveal.coerceIn(0f, 1f)).dp.toPx().coerceAtMost(fillWidth / 2f)
    val center = Offset(fillWidth - radius, size.height / 2f)
    val opacity = if (darkSurface) 0.55f else 0.16f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = opacity * alphaReveal), accent.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
