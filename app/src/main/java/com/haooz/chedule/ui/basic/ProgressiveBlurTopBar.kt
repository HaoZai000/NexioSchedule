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
 * 渐进模糊顶部栏容器
 *
 * 半径随 Y 连续变化的真渐进模糊：顶部最大，向下收到 0。
 * 实现只走 AGSL 多重采样，**不挂 Compose BlurEffect**：
 * 流程：抖动多重采样（消文字星点）→ 轻量空间去噪（平掉细噪）。
 *
 * downsampleScale = 1f：顶栏区域小，用全分辨率录制。
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
    // 每个实例必须用独立 key：ShaderRegistry 按 key 共享同一份 android.graphics.RuntimeShader，
    // 今日/课表/设置顶栏会同时挂载，共用 key 会互相覆盖 uniform，慢滑时表现为一闪一闪。
    val shaderKey = remember {
        "ProgressiveBlurRadial_${progressiveBlurShaderSeq.incrementAndGet()}"
    }
    val denoiseKey = remember(shaderKey) { "${shaderKey}_denoise" }
    val blurEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
        remember(shaderKey, denoiseKey, tintColor, tintIntensity) {
            {
                val maxRadiusPx = 12f.dp.toPx()
                // 扩一圈录制边距：边缘采样可以摸到框外真实内容，减轻顶边/侧边发虚
                padding = maxRadiusPx
                val pad = padding * downsampleScale
                val contentW = size.width * downsampleScale
                val contentH = size.height * downsampleScale
                runtimeShaderEffect(
                    shaderKey,
                    PROGRESSIVE_BLUR_SHADER,
                    "content"
                ) {
                    // contentOrigin/Size：可见区域在带 padding 缓冲里的位置与尺寸
                    setFloatUniform("contentOrigin", pad, pad)
                    setFloatUniform("contentSize", contentW, contentH)
                    setFloatUniform(
                        "bufferSize",
                        contentW + 2f * pad,
                        contentH + 2f * pad
                    )
                    setFloatUniform("maxRadius", maxRadiusPx * downsampleScale)
                    setColorUniform("tint", tintColor)
                    setFloatUniform("tintIntensity", tintIntensity)
                }
                runtimeShaderEffect(
                    denoiseKey,
                    PROGRESSIVE_DENOISE_SHADER,
                    "content"
                ) {
                    setFloatUniform("contentOrigin", pad, pad)
                    setFloatUniform("contentSize", contentW, contentH)
                    setFloatUniform(
                        "bufferSize",
                        contentW + 2f * pad,
                        contentH + 2f * pad
                    )
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
                        // 全分辨率：避免 0.42 降采样在慢滑时跳格抖动
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

private const val PROGRESSIVE_BLUR_SHADER = """
uniform shader content;
uniform float2 contentOrigin;
uniform float2 contentSize;
uniform float2 bufferSize;
uniform float maxRadius;
layout(color) uniform half4 tint;
uniform float tintIntensity;

float hash12(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 progressiveBlur(float2 coord, float radius) {
    if (radius < 0.5) {
        return content.eval(coord);
    }
    half4 sum = half4(0.0);
    float wsum = 0.0;
    // 高半径时采样点距 > 文字笔画宽，固定螺旋会把笔画打成「星星点点」。
    // 每个像素随机旋转核 + 轻微径向抖动，把规则点阵打散成细噪，视觉上更接近真模糊。
    float spin = hash12(coord) * 6.2831853;
    for (int i = 0; i < 64; i++) {
        float fi = float(i);
        float r = radius * pow((fi + 0.5) / 64.0, 0.5);
        r *= 0.90 + 0.20 * hash12(coord + float2(fi, 1.7));
        float a = fi * 2.39996323 + spin;
        float2 o = float2(cos(a), sin(a)) * r;
        float w = exp(-r * r / max(0.85 * radius * radius, 0.001));
        // 采样夹在带 padding 的缓冲内，边缘可以摸到框外真实内容
        float2 sc = clamp(coord + o, float2(0.0), max(bufferSize - 1.0, float2(0.0)));
        half4 c = content.eval(sc);
        // 近透明样本不参与平均，避免顶边/状态栏空隙把 alpha 洗掉、露出下面清晰层
        if (c.a > 0.02) {
            sum += c * w;
            wsum += w;
        }
    }
    if (wsum < 0.0001) {
        return content.eval(coord);
    }
    return sum / wsum;
}

float softerstep(float a, float b, float x) {
    float s = clamp((x - a) / max(b - a, 0.0001), 0.0, 1.0);
    return s * s * s * (s * (s * 6.0 - 15.0) + 10.0);
}

half4 main(float2 coord) {
    // 可见区域从 contentOrigin 起算，padding 边距不参与 Y 渐变
    float t = clamp(
        (coord.y - contentOrigin.y) / max(contentSize.y, 1.0),
        0.0, 1.0
    );
    // smoothstep：比 softerstep 略快进入糊感，但仍比 sqrt 从 0 陡升要缓
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

/**
 * 轻量空间去噪：对上一阶段（抖动多重采样）的结果做小半径盒式平均。
 * 半径与渐进模糊强度成正比，底部自然退化为直通。
 */
private const val PROGRESSIVE_DENOISE_SHADER = """
uniform shader content;
uniform float2 contentOrigin;
uniform float2 contentSize;
uniform float2 bufferSize;
uniform float maxRadius;

float softerstep(float a, float b, float x) {
    float s = clamp((x - a) / max(b - a, 0.0001), 0.0, 1.0);
    return s * s * s * (s * (s * 6.0 - 15.0) + 10.0);
}

half4 main(float2 coord) {
    float t = clamp(
        (coord.y - contentOrigin.y) / max(contentSize.y, 1.0),
        0.0, 1.0
    );
    // 与渐进模糊同一套曲线，避免两阶段半径不一致
    float u = 1.0 - smoothstep(0.0, 1.0, t);
    float radius = maxRadius * u;
    float r = radius * 0.18;
    if (r < 0.4) {
        return content.eval(coord);
    }
    half4 sum = content.eval(coord);
    float wsum = 1.0;
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5235987756;
        float2 o = float2(cos(a), sin(a)) * r;
        float2 sc = clamp(coord + o, float2(0.0), max(bufferSize - 1.0, float2(0.0)));
        half4 c = content.eval(sc);
        if (c.a > 0.02) {
            sum += c;
            wsum += 1.0;
        }
    }
    return sum / wsum;
}
"""
