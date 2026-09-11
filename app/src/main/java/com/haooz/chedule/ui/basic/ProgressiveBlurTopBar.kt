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
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 渐进模糊顶部栏容器
 *
 * 半径随 Y 连续变化的真渐进模糊：顶部最大，向下收到 0。
 *
 * 重影处理：
 * - 64 点黄金螺旋 + 中心加密，把硬边副本融开
 * - 1dp 高斯当重建滤波，只抹亚像素笔画，几乎看不出底板
 * - 仅最末端（约 12%）softerstep 消掉 1dp 与清晰内容的硬边，
 *   中上部不整层淡出，保持连贯的渐进糊感
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
    val blurEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
        remember(tintColor, tintIntensity) {
            {
                // 1dp：只当多重采样的抗锯齿，肉眼几乎无「底板感」
                blur(0.5f.dp.toPx())
                runtimeShaderEffect(
                    "ProgressiveBlurRadial",
                    PROGRESSIVE_BLUR_SHADER,
                    "content"
                ) {
                    setFloatUniform(
                        "size",
                        size.width * downsampleScale,
                        size.height * downsampleScale
                    )
                    setFloatUniform("maxRadius", 20f.dp.toPx() * downsampleScale)
                    setColorUniform("tint", tintColor)
                    setFloatUniform("tintIntensity", tintIntensity)
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
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = blurShapeBlock,
                        effects = blurEffects
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

private const val PROGRESSIVE_BLUR_SHADER = """
uniform shader content;
uniform float2 size;
uniform float maxRadius;
layout(color) uniform half4 tint;
uniform float tintIntensity;

half4 progressiveBlur(float2 coord, float radius) {
    if (radius < 0.5) {
        return content.eval(coord);
    }
    half4 sum = half4(0.0);
    float wsum = 0.0;
    // 64 点：点多副本碎，硬边更易融成连续糊，而不是几道重影
    // pow(x, 0.62) 把采样往中心挤，文字先被高频平均，再向外扩散
    for (int i = 0; i < 64; i++) {
        float fi = float(i);
        float r = radius * pow((fi + 0.5) / 64.0, 0.62);
        float a = fi * 2.39996323;
        float2 o = float2(cos(a), sin(a)) * r;
        float w = exp(-r * r / max(0.45 * radius * radius, 0.001));
        sum += content.eval(coord + o) * w;
        wsum += w;
    }
    return sum / max(wsum, 0.0001);
}

// 两端更软的 S 曲线，避免 smoothstep 在收尾处仍留下可察觉的棱
float softerstep(float a, float b, float x) {
    float s = clamp((x - a) / max(b - a, 0.0001), 0.0, 1.0);
    return s * s * s * (s * (s * 6.0 - 15.0) + 10.0);
}

half4 main(float2 coord) {
    float t = clamp(coord.y / max(size.y, 1.0), 0.0, 1.0);
    // 顶部保持较大半径，向下连续收到 0
    float u = 1.0 - smoothstep(0.0, 1.0, t);
    float radius = maxRadius * sqrt(u);
    half4 color = progressiveBlur(coord, radius);
    // 只在最末端消掉 1dp 底板与清晰内容的硬边。
    // 区间压得很窄 + softerstep：中上部几乎无感，不会像整段淡出带那样把糊感洗掉
    float edge = softerstep(0.88, 1.0, t);
    color *= (1.0 - edge);
    if (tintIntensity > 0.0) {
        color = mix(color, tint * (1.0 - edge), tintIntensity * sqrt(u));
    }
    return color;
}
"""
