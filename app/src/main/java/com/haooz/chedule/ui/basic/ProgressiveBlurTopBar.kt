package com.haooz.chedule.ui.basic

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.runtimeShaderEffect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.atomic.AtomicInteger

private val progressiveBlurShaderSeq = AtomicInteger(0)

/**
 * 顶部栏渐进模糊：模糊半径随 Y 从顶部最大连续收到 0。
 *
 * 管线（API 33+）：AGSL 32 点抖动多重采样 → 轻量空间去噪。
 * 不用 Compose BlurEffect——在每帧重录的 GraphicsLayer 上滚动会闪。
 * downsampleScale = 1，避免默认 0.42 降采样在慢滑时跳格抖动。
 * Shader：黄金角递推、单次 hash；padding 区不上屏直接直通（AGSL 不支持 const 数组表）。
 *
 * API < 33 降级为表面色渐变遮罩。
 */
@Composable
fun ProgressiveBlurTopBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    height: Dp = Dp.Unspecified,
    tintIntensity: Float = 0.2f,
    tintColor: Color = MiuixTheme.colorScheme.surface,
    blurAlpha: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val totalHeight = if (height != Dp.Unspecified) {
        height
    } else {
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        if (statusBarHeight > 0.dp) 80.dp + statusBarHeight else 120.dp
    }

    val blurShapeBlock: () -> androidx.compose.ui.graphics.Shape = remember { { RectangleShape } }
    // ShaderRegistry 按 key 共享 RuntimeShader；多顶栏同时挂载时必须各用独立 key，
    val shaderKey = remember {
        "ProgressiveBlurRadial_${progressiveBlurShaderSeq.incrementAndGet()}"
    }
    val denoiseKey = remember(shaderKey) { "${shaderKey}_denoise" }
    val blurEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
        remember(shaderKey, denoiseKey, tintColor, tintIntensity) {
            {
                val maxRadiusPx = 12f.dp.toPx()
                // 录制缓冲向外扩一圈，边缘采样可摸到框外真实内容
                padding = maxRadiusPx
                val pad = padding * downsampleScale
                val contentW = size.width * downsampleScale
                val contentH = size.height * downsampleScale
                val bufferW = contentW + 2f * pad
                val bufferH = contentH + 2f * pad

                runtimeShaderEffect(shaderKey, PROGRESSIVE_BLUR_SHADER, "content") {
                    setFloatUniform("contentOrigin", pad, pad)
                    setFloatUniform("contentSize", contentW, contentH)
                    setFloatUniform("bufferSize", bufferW, bufferH)
                    setFloatUniform("maxRadius", maxRadiusPx * downsampleScale)
                    setColorUniform("tint", tintColor)
                    setFloatUniform("tintIntensity", tintIntensity)
                }
                runtimeShaderEffect(denoiseKey, PROGRESSIVE_DENOISE_SHADER, "content") {
                    setFloatUniform("contentOrigin", pad, pad)
                    setFloatUniform("contentSize", contentW, contentH)
                    setFloatUniform("bufferSize", bufferW, bufferH)
                    setFloatUniform("maxRadius", maxRadiusPx * downsampleScale)
                }
            }
        }

    Box(modifier = modifier) {
        if (Build.VERSION.SDK_INT >= 33) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
                    .graphicsLayer { alpha = blurAlpha }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = blurShapeBlock,
                        effects = blurEffects,
                        highlight = null,
                        shadow = null,
                        downsampleScale = 1f
                    )
            )
        } else {
            val gradientColor = MiuixTheme.colorScheme.surface
            val endY = totalHeight.value * density.density
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
                    .graphicsLayer { alpha = blurAlpha }
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to gradientColor.copy(alpha = 0.9f),
                                0.4f to gradientColor.copy(alpha = 0.82f),
                                0.7f to gradientColor.copy(alpha = 0.6f),
                                1.0f to gradientColor.copy(alpha = 0.0f)
                            ),
                            startY = 0f,
                            endY = endY
                        )
                    )
            )
        }
        content()
    }
}

// 两端导数为 0 的 S 曲线，收尾比 smoothstep 更绵
private const val SOFTER_STEP = """
float softerstep(float a, float b, float x) {
    float s = clamp((x - a) / max(b - a, 0.0001), 0.0, 1.0);
    return s * s * s * (s * (s * 6.0 - 15.0) + 10.0);
}
"""

private const val PROGRESSIVE_BLUR_SHADER = """
uniform shader content;
uniform float2 contentOrigin;
uniform float2 contentSize;
uniform float2 bufferSize;
uniform float maxRadius;
layout(color) uniform half4 tint;
uniform float tintIntensity;

$SOFTER_STEP

float hash12(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 progressiveBlur(float2 coord, float radius) {
    if (radius < 0.5) {
        return content.eval(coord);
    }
    float h = hash12(coord);
    float2 dir = float2(cos(h * 6.2831853), sin(h * 6.2831853));
    float2 g = float2(cos(2.39996323), sin(2.39996323));
    half4 sum = half4(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 32; i++) {
        float fi = float(i);
        float ff = (fi + 0.5) / 32.0;
        float r = radius * sqrt(ff);
        r *= 0.90 + 0.20 * fract(h * 93.9898 + fi * 0.7548776662);
        float2 o = dir * r;
        float w = exp(-ff / 0.85);
        float2 sc = clamp(coord + o, float2(0.0), max(bufferSize - 1.0, float2(0.0)));
        half4 c = content.eval(sc);
        if (c.a > 0.02) {
            sum += c * w;
            wsum += w;
        }
        dir = float2(dir.x * g.x - dir.y * g.y, dir.x * g.y + dir.y * g.x);
    }
    if (wsum < 0.0001) {
        return content.eval(coord);
    }
    return sum / wsum;
}

half4 main(float2 coord) {
    // padding 边距不上屏：直接直通
    float2 local = coord - contentOrigin;
    if (local.x < 0.0 || local.y < 0.0 ||
        local.x >= contentSize.x || local.y >= contentSize.y) {
        return content.eval(coord);
    }
    float t = clamp(local.y / max(contentSize.y, 1.0), 0.0, 1.0);
    float u = 1.0 - smoothstep(0.0, 1.0, t);
    float radius = maxRadius * u;
    half4 color = progressiveBlur(coord, radius);
    float edge = softerstep(0.88, 1.0, t);
    color *= (1.0 - edge);
    if (tintIntensity > 0.0) {
        color = mix(color, tint * (1.0 - edge), tintIntensity * u);
    }
    return color;
}
"""

/** 小半径盒式平均，压掉上一阶段抖动细噪；半径与模糊强度成正比。 */
private const val PROGRESSIVE_DENOISE_SHADER = """
uniform shader content;
uniform float2 contentOrigin;
uniform float2 contentSize;
uniform float2 bufferSize;
uniform float maxRadius;

half4 main(float2 coord) {
    float2 local = coord - contentOrigin;
    if (local.x < 0.0 || local.y < 0.0 ||
        local.x >= contentSize.x || local.y >= contentSize.y) {
        return content.eval(coord);
    }
    float t = clamp(local.y / max(contentSize.y, 1.0), 0.0, 1.0);
    float r = maxRadius * (1.0 - smoothstep(0.0, 1.0, t)) * 0.18;
    if (r < 0.4) {
        return content.eval(coord);
    }
    half4 sum = content.eval(coord);
    float wsum = 1.0;
    float2 dir = float2(1.0, 0.0);
    float2 g = float2(cos(0.7853981634), sin(0.7853981634));
    for (int i = 0; i < 8; i++) {
        float2 sc = clamp(coord + dir * r, float2(0.0), max(bufferSize - 1.0, float2(0.0)));
        half4 c = content.eval(sc);
        if (c.a > 0.02) {
            sum += c;
            wsum += 1.0;
        }
        dir = float2(dir.x * g.x - dir.y * g.y, dir.x * g.y + dir.y * g.x);
    }
    return sum / wsum;
}
"""
