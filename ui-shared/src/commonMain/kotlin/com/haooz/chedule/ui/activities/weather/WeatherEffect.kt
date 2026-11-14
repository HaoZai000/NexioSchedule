/**
 * 天气页的矢量叠加层。
 *
 * 参考实现（HyperOS 天气）里只有 `sun_light.webp` 这一张柔光贴图做晴天的太阳/月亮，
 * 其余现象全部是 runtime shader（见 [WeatherShaderOverlay]），所以这里只保留光晕，
 * 云/雾/沙尘由流光配色本身表达，不再自造粒子。
 */
package com.haooz.chedule.ui.activities.weather

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.floor
import kotlin.math.sin

/** 晴天（含夜间月亮）的柔光叠加层。 */
@Composable
internal fun WeatherEffectLayer(
    phase: SkyPhase,
    modifier: Modifier = Modifier,
) {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(phase) {
        clock.floatValue = 0f
        val start = withFrameNanos { it }
        while (isActive) {
            // 只写状态、不参与组合，读发生在 draw 阶段，因此每帧只重绘这一层
            withFrameNanos { now -> clock.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawSunGlow(phase, clock.floatValue) },
    )
}

/**
 * 柔光：对应原版 `sun_light.webp`——一个没有明显边界的圆形光团。
 * 位置随时段移动（黎明左下、正午右上、黄昏右下、夜里右上当月亮），亮度极缓慢地呼吸。
 */
private fun DrawScope.drawSunGlow(phase: SkyPhase, t: Float) {
    val w = size.width
    val h = size.height
    val low = phase == SkyPhase.Dawn || phase == SkyPhase.Dusk
    val cx = if (phase == SkyPhase.Dawn) w * 0.18f else w * 0.84f
    val cy = if (low) h * 0.80f else h * 0.14f
    val (color, peak) = when (phase) {
        SkyPhase.Dawn -> Color(0xFFFFC98A) to 0.34f
        SkyPhase.Dusk -> Color(0xFFFFA06B) to 0.32f
        SkyPhase.Night -> Color(0xFFCBD8F5) to 0.18f
        else -> Color(0xFFFFF1D2) to 0.36f
    }
    val breath = 1f + 0.06f * sin(t * 0.18f)
    val radius = w * 0.42f * breath
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = peak),
                color.copy(alpha = peak * 0.42f),
                color.copy(alpha = 0f),
            ),
            center = Offset(cx, cy),
            radius = radius,
        ),
        radius = radius,
        center = Offset(cx, cy),
    )
}


/**
 * 落雨层：玻璃上的水珠由 shader 负责，真正「下下来的雨」是这一层雨丝。
 * 画在卡片之上、shader 采样之下，因此也会被水珠折射。
 */
@Composable
internal fun RainfallLayer(
    phase: SkyPhase,
    intensity: Float,
    modifier: Modifier = Modifier,
    scrollFade: () -> Float = { 1f },
) {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(phase, intensity) {
        clock.floatValue = 0f
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now -> clock.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRainfall(phase == SkyPhase.Night, intensity, clock.floatValue, scrollFade())
            },
    )
}

private fun DrawScope.drawRainfall(night: Boolean, intensity: Float, t: Float, fade: Float) {
    if (fade <= 0.01f) return
    val w = size.width
    val h = size.height
    val level = intensity.coerceIn(0f, 1f)
    val count = (24 + 120 * level).toInt()
    // 夜里底色深，同亮度会显得刺眼，所以夜间反而用更暗的蓝灰 + 更低不透明度
    val base = if (night) Color(0xFF8FA9C4) else Color(0xFFA8C4DE)
    for (i in 0 until count) {
        val speed = (0.45f + 0.8f * level) + hash(i, 1) * 0.5f
        val y = ((hash(i, 2) + t * speed) % 1.1f) * h - h * 0.05f
        val x = hash(i, 3) * w * 1.05f
        val len = h * (0.022f + hash(i, 4) * 0.032f)
        val alpha = ((if (night) 0.16f else 0.24f) + hash(i, 5) * (if (night) 0.14f else 0.16f)) *
            (0.6f + 0.5f * level) * fade
        drawLine(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to base.copy(alpha = 0f),
                    0.45f to base.copy(alpha = alpha * 0.3f),
                    0.85f to base.copy(alpha = alpha * 0.85f),
                    1f to base.copy(alpha = alpha),
                ),
                start = Offset(x, y),
                end = Offset(x, y + len),
            ),
            start = Offset(x, y),
            end = Offset(x, y + len),
            strokeWidth = (1.6f + hash(i, 6) * 1.2f).dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}


private fun hash(a: Int, b: Int): Float {
    val v = sin(a * 12.9898f + b * 78.233f) * 43758.5453f
    return v - floor(v)
}
