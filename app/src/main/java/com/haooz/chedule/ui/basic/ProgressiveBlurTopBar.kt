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
import com.kyant.backdrop.effects.runtimeShaderEffect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.atomic.AtomicInteger

private val progressiveBlurShaderSeq = AtomicInteger(0)

/**
 * 渐进模糊顶部栏容器
 *
 * 半径随 Y 连续变化的真渐进模糊：顶部最大，向下收到 0。
 *
 * 实现只走 AGSL 多重采样，**不挂 Compose BlurEffect**：
 * 系统 blur 在「每帧重录的 GraphicsLayer + RenderEffect」上滚动时很容易一闪一闪。
 * 流程：抖动多重采样（消文字星点）→ 轻量空间去噪（平掉细噪）。
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
                val maxRadiusPx = 16f.dp.toPx()
                runtimeShaderEffect(
                    shaderKey,
                    PROGRESSIVE_BLUR_SHADER,
                    "content"
                ) {
                    setFloatUniform(
                        "size",
                        size.width * downsampleScale,
                        size.height * downsampleScale
                    )
                    setFloatUniform("maxRadius", maxRadiusPx * downsampleScale)
                    setColorUniform("tint", tintColor)
                    setFloatUniform("tintIntensity", tintIntensity)
                }
                // 抖动去星点后会留细噪：串一道轻量空间平均。
                // 半径跟模糊强度走（约 18%），只平噪、不把渐进糊感洗掉。
                runtimeShaderEffect(
                    denoiseKey,
                    PROGRESSIVE_DENOISE_SHADER,
                    "content"
                ) {
                    setFloatUniform(
                        "size",
                        size.width * downsampleScale,
                        size.height * downsampleScale
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
        // 权重略放平（σ 更大），外圈点不要过稀，避免只在中心糊、外围拖出亮斑
        float w = exp(-r * r / max(0.85 * radius * radius, 0.001));
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

/**
 * 轻量空间去噪：对上一阶段（抖动多重采样）的结果做小半径盒式平均。
 * 半径与渐进模糊强度成正比，底部自然退化为直通。
 */
private const val PROGRESSIVE_DENOISE_SHADER = """
uniform shader content;
uniform float2 size;
uniform float maxRadius;

half4 main(float2 coord) {
    float t = clamp(coord.y / max(size.y, 1.0), 0.0, 1.0);
    float u = 1.0 - smoothstep(0.0, 1.0, t);
    float radius = maxRadius * sqrt(u);
    float r = radius * 0.18;
    if (r < 0.4) {
        return content.eval(coord);
    }
    half4 sum = content.eval(coord);
    float wsum = 1.0;
    // 12 向等权环：盒式平均，比高斯更擅长压细噪
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5235987756;
        float2 o = float2(cos(a), sin(a)) * r;
        sum += content.eval(coord + o);
        wsum += 1.0;
    }
    return sum / wsum;
}
"""
