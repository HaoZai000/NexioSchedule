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
 * 系统 blur 在「每帧重录的 GraphicsLayer + RenderEffect」上滚动时很容易一闪一闪，
 * 和半径/采样无关。硬边重影靠采样密度压，不靠 BlurEffect 底噪。
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
    val blurEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
        remember(shaderKey, tintColor, tintIntensity) {
            {

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
    // pow(x, 0.5) = sqrt：面积均匀分布；比 0.62 更靠外一点，外圈也够密
    for (int i = 0; i < 64; i++) {
        float fi = float(i);
        float r = radius * pow((fi + 0.5) / 64.0, 0.5);
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
