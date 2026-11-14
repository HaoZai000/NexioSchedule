/**
 * 天气现象的 1:1 复刻：把整页内容录进图层，再让 runtime shader 逐像素采样。
 *
 * - 雨/雷暴：移植 HyperOS 天气 `rain_drop.frag` 的水珠模型——40 格静态珠阵（淡入淡出）
 *   + 18×3 列的滑动水珠与拖尾；水珠处按覆盖度梯度偏移采样坐标，形成真实折射。
 * - 雪：移植 `snow.frag` 的思路，程序化噪声代替原版的 `vieweffect_snow.webp`，
 *   按 `1 - e^(-(1-y)²)` 从屏幕顶部往下累积。
 * - 霜（冰粒/冻雨）：移植 `frost.frag`，同样用噪声代替 `vieweffect_frost.webp`。
 *
 * 代价是每帧一次全屏录制 + 一次全屏 shader 采样，只在雨/雪/雷暴时挂载。
 */
package com.haooz.chedule.ui.activities.weather

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import kotlinx.coroutines.isActive
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/** 这些现象需要整页采样；晴/多云/雾/沙尘只有矢量层，不付这份开销。 */
internal val WeatherFlowKind.needsShaderPass: Boolean
    get() = this == WeatherFlowKind.Rain ||
        this == WeatherFlowKind.Thunder ||
        this == WeatherFlowKind.Snow ||
        this == WeatherFlowKind.Frost


/**
 * 前 4 张卡片的窗口矩形（px）：水珠只在这些卡上生成。
 * Compose 侧测量、shader 侧读取，因此滚动时水珠跟着卡片走。
 */
internal class CardDropletRects {
    val rects = mutableStateOf(FloatArray(16))
    var count by mutableIntStateOf(0)
    var originX by mutableFloatStateOf(0f)
    var originY by mutableFloatStateOf(0f)
    private val used = BooleanArray(4)

    /** 领一个空槽位；卡片离开组合时必须 [release]，否则滚动几次槽位就被占满。 */
    fun acquire(): Int {
        val free = used.indexOfFirst { !it }
        if (free < 0) return -1
        used[free] = true
        count = used.count { it }
        return free
    }

    fun release(slot: Int) {
        if (slot < 0 || slot >= 4) return
        used[slot] = false
        count = used.count { it }
        val next = rects.value.copyOf()
        next[slot * 4] = 0f
        next[slot * 4 + 1] = 0f
        next[slot * 4 + 2] = 0f
        next[slot * 4 + 3] = 0f
        rects.value = next
    }

    /** 与 shader 同一条淡出曲线：最靠上那张卡相对静止位置的位移。 */
    fun fadeProgress(basePx: Float, span: Float = 360f): Float {
        val r = rects.value
        var minY = Float.MAX_VALUE
        for (i in 0 until 4) {
            if (r[i * 4 + 2] <= 0f) continue
            val y = r[i * 4 + 1] - originY
            if (y < minY) minY = y
        }
        if (minY == Float.MAX_VALUE) return 0f
        return ((minY - (basePx - span)) / span).coerceIn(0f, 1f)
    }

    fun update(slot: Int, left: Float, top: Float, w: Float, h: Float) {
        if (!used[slot]) return
        val next = rects.value.copyOf()
        next[slot * 4] = left
        next[slot * 4 + 1] = top
        next[slot * 4 + 2] = w
        next[slot * 4 + 3] = h
        rects.value = next
    }
}

internal val LocalCardDropletRects = compositionLocalOf<CardDropletRects?> { null }

/** 盖在整页之上的 shader 叠加层：雨、雪、霜、雷暴。 */
@Composable
internal fun WeatherShaderOverlay(
    backdrop: GlassBackdrop,
    kind: WeatherFlowKind,
    rainIntensity: Float = 0.5f,
    dropletRects: CardDropletRects? = null,
    fadeBasePx: Float = 0f,
    cornerRadiusPx: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(kind) {
        clock.floatValue = 0f
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now -> clock.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    val shader = when (kind) {
        WeatherFlowKind.Rain, WeatherFlowKind.Thunder -> WEATHER_RAIN_SHADER
        WeatherFlowKind.Snow -> WEATHER_SNOW_SHADER
        else -> WEATHER_FROST_SHADER
    }
    val key = when (kind) {
        WeatherFlowKind.Rain, WeatherFlowKind.Thunder -> "NexioWeatherRain"
        WeatherFlowKind.Snow -> "NexioWeatherSnow"
        else -> "NexioWeatherFrost"
    }
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                dropletRects?.originX = bounds.left
                dropletRects?.originY = bounds.top
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    runtimeShaderEffect(
                        key = key,
                        shaderString = shader,
                        uniformShaderName = "content",
                    ) {
                        setFloatUniform("uSize", size.width, size.height)
                        setFloatUniform("uTime", clock.floatValue)
                        val fade = (clock.floatValue / 1.5f).coerceIn(0f, 1f)
                        when (kind) {
                            WeatherFlowKind.Rain, WeatherFlowKind.Thunder -> {
                                // 雨势 → 六参数：小雨珠子少而大、速度慢，大雨密而快
                                val i = rainIntensity.coerceIn(0f, 1f)
                                setFloatUniform("uStaticDropSize", 1.8f - 0.9f * i)
                                setFloatUniform("uStaticDropAmount", 0.15f + 0.85f * i)
                                setFloatUniform("uStaticDropSpeed", 0.8f + 0.55f * i)
                                setFloatUniform("uRunningDropSize", 1.8f - 0.9f * i)
                                setFloatUniform("uRunningDropAmount", 0.15f + 0.85f * i)
                                setFloatUniform("uRunningDropSpeed", 0.8f + 0.55f * i)
                                setFloatUniform("uAlpha", 1f)
                                setFloatUniform("uFadeBase", fadeBasePx)
                                setFloatUniform("uCornerRadius", cornerRadiusPx)
                                val rects = dropletRects?.rects?.value ?: FloatArray(16)
                                val ox = dropletRects?.originX ?: 0f
                                val oy = dropletRects?.originY ?: 0f
                                setFloatUniform("uRectCount", (dropletRects?.count ?: 0).toFloat())
                                setFloatUniform("uRect0", rects[0] - ox, rects[1] - oy, rects[2], rects[3])
                                setFloatUniform("uRect1", rects[4] - ox, rects[5] - oy, rects[6], rects[7])
                                setFloatUniform("uRect2", rects[8] - ox, rects[9] - oy, rects[10], rects[11])
                                setFloatUniform("uRect3", rects[12] - ox, rects[13] - oy, rects[14], rects[15])
                                setFloatUniform("uFlash", lightningFlash(clock.floatValue, kind))
                            }
                            WeatherFlowKind.Snow -> {
                                // 积雪进度：缓慢起伏，像雪一点点堆在顶部
                                setFloatUniform(
                                    "uProgress",
                                    0.15f + 0.30f * (0.5f + 0.5f * sin(clock.floatValue * 0.05f)),
                                )
                                setFloatUniform("uAlpha", fade)
                            }
                            else -> {
                                // 结霜进度：缓慢起伏，像玻璃上的霜在生长
                                setFloatUniform(
                                    "uProgress",
                                    0.35f + 0.55f * (0.5f + 0.5f * sin(clock.floatValue * 0.12f)),
                                )
                                setFloatUniform("uAlpha", fade)
                            }
                        }
                    }
                },
                shadow = null,
            ),
    )
}

/** 雷暴闪光包络：约 9 秒一次，主闪后跟一次弱补闪，强度逐次变化。 */
private fun lightningFlash(t: Float, kind: WeatherFlowKind): Float {
    if (kind != WeatherFlowKind.Thunder) return 0f
    val cycle = 9f
    val k = t % cycle
    if (k > 1.7f) return 0f
    val seed = floor(t / cycle).toInt()
    val strength = 0.45f + hash(seed, 41) * 0.55f
    val main = exp(-k * 4.2f)
    val echo = if (k > 0.22f) exp(-(k - 0.22f) * 7f) * 0.55f else 0f
    return ((main + echo) * strength * 0.22f).coerceIn(0f, 0.24f)
}

private fun hash(a: Int, b: Int): Float {
    val v = sin(a * 12.9898f + b * 78.233f) * 43758.5453f
    return v - floor(v)
}

/**
 * 水珠折射：`rain_drop.frag` 的逐行移植（这一版带雨势参数，原版就是用它按雨量调效果）。
 *
 * 六个强度 uniform 与原版一一对应：
 * - 静态珠阵：`uStaticDropSize` 决定格子密度（越大越稀）、`uStaticDropAmount` 控制可见量、
 *   `uStaticDropSpeed` 控制闪烁频率；
 * - 滑动水珠：`uRunningDropSize` / `uRunningDropAmount` / `uRunningDropSpeed` 同理；
 * - `uAlpha` 整体强度，`uFlash` 是本项目给雷暴加的闪光。
 */
internal val WEATHER_RAIN_SHADER = """
uniform shader content;
uniform float2 uSize;
uniform float uTime;
uniform float uStaticDropSize;
uniform float uStaticDropAmount;
uniform float uStaticDropSpeed;
uniform float uRunningDropSize;
uniform float uRunningDropAmount;
uniform float uRunningDropSpeed;
uniform float uAlpha;
uniform float uFlash;
uniform float4 uRect0;
uniform float4 uRect1;
uniform float4 uRect2;
uniform float4 uRect3;
uniform float uRectCount;
uniform float uFadeBase;
uniform float uCornerRadius;

// 四张卡同步淡出：用最靠上那张卡离静止位置的位移算进度（下滑自动反向）
float dropletsFade() {
    float minY = 1e9;
    if (uRectCount > 0.5) minY = min(minY, uRect0.y);
    if (uRectCount > 1.5) minY = min(minY, uRect1.y);
    if (uRectCount > 2.5) minY = min(minY, uRect2.y);
    if (uRectCount > 3.5) minY = min(minY, uRect3.y);
    if (minY > 1e8) {
        return 0.0;
    }
    return clamp((minY - (uFadeBase - 360.0)) / 360.0, 0.0, 1.0);
}

// 圆角矩形有向距离：用来把圆角外的水珠裁掉
float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + float2(r);
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float4 pickRect(float2 p) {
    if (uRectCount > 0.5) {
        float4 r = uRect0;
        if (p.x >= r.x && p.y >= r.y && p.x <= r.x + r.z && p.y <= r.y + r.w) {
            return r;
        }
    }
    if (uRectCount > 1.5) {
        float4 r = uRect1;
        if (p.x >= r.x && p.y >= r.y && p.x <= r.x + r.z && p.y <= r.y + r.w) {
            return r;
        }
    }
    if (uRectCount > 2.5) {
        float4 r = uRect2;
        if (p.x >= r.x && p.y >= r.y && p.x <= r.x + r.z && p.y <= r.y + r.w) {
            return r;
        }
    }
    if (uRectCount > 3.5) {
        float4 r = uRect3;
        if (p.x >= r.x && p.y >= r.y && p.x <= r.x + r.z && p.y <= r.y + r.w) {
            return r;
        }
    }
    return float4(0.0);
}

float N(float t) {
    return fract(sin(t * 12345.564) * 7658.76);
}

float3 N13(float p) {
    float3 p3 = fract(float3(p) * float3(0.1031, 0.11369, 0.13787));
    p3 += dot(p3, p3.yzx + 19.19);
    return fract((p3.xxy + p3.yzz) * p3.zyx);
}

float Saw(float b, float t) {
    return smoothstep(0.0, b, t) * smoothstep(1.0, b, t);
}

float4 GenDropLayerN(float2 uv, float t) {
    t *= uRunningDropSpeed;
    uv /= float2(uRunningDropSize);
    uv.y += t * 0.55;
    float2 grid = float2(18.0, 3.0);
    float2 id = floor(uv * grid);
    float colShift = N(id.x);
    uv.y += colShift;
    id = floor(uv * grid);
    return float4(N13(id.x * 35.2 + id.y * 2376.1), colShift);
}

float3 GenStaticLayerN(float2 uv) {
    uv *= 40.0 / uStaticDropSize;
    float2 id = floor(uv);
    return N13(id.x * 107.45 + id.y * 3543.65);
}

float StaticDrops(float2 uv, float3 n, float t) {
    t *= uStaticDropSpeed;
    uv *= 40.0 / uStaticDropSize;
    uv = fract(uv) - float2(0.5);
    float2 p = (n.xy - float2(0.5)) * 0.7;
    float d = length(uv - p);
    float fade = Saw(0.025, fract(t + n.z));
    return smoothstep(0.3, 0.0, d) * fract(n.z * 10.0) * fade;
}

float DropLayer2(float2 uv, float3 n, float t, float colShift) {
    t *= uRunningDropSpeed;
    float oldY = uv.y;
    uv /= float2(uRunningDropSize);
    uv.y += t * 0.55;
    float2 a = float2(6.0, 1.0);
    float2 grid = float2(18.0, 3.0);
    uv.y += colShift;
    float2 st = fract(uv * grid) - float2(0.5, 0.0);
    float x = n.x - 0.5;
    float y = oldY * 20.0;
    x *= 0.7;
    float ti = fract(t + n.z);
    y = (Saw(0.9, ti) - 0.5) * 0.9 + 0.5;
    float2 p = float2(x, y);
    float d = length((st - p) * a.yx);
    float mainDrop = smoothstep(0.4, 0.0, d);
    float r = sqrt(smoothstep(1.0, y, st.y));
    float cd = abs(st.x - x);
    float trailFront = smoothstep(-0.02, 0.02, st.y - y);
    y = oldY;
    float trail2 = smoothstep(0.2 * r, 0.0, cd);
    float droplets = max(0.0, sin((y * (1.0 - y)) * 120.0) - st.y) * trail2 * trailFront * n.z;
    y = fract(y * 10.0) + (st.y - 0.5);
    float dd = length(st - float2(x, y));
    droplets = smoothstep(0.3, 0.0, dd);
    return mainDrop + droplets * r * trailFront;
}

float Drops(float2 uv, float3 dropN, float3 staticN, float t, float rs, float ra, float colShift) {
    float s = StaticDrops(uv, staticN, t * 1.2) * rs * uStaticDropAmount;
    float m1 = DropLayer2(uv, dropN, t * 1.85, colShift) * ra * uRunningDropAmount;
    return smoothstep(0.3, 1.0, s + m1);
}

half4 main(float2 fragCoord) {
    float2 res = max(uSize, float2(1.0));
    float2 px = clamp(fragCoord, float2(0.5), res - 0.5);
    half4 uiColor = content.eval(px);
    if (uiColor.a == 0.0) {
        return uiColor;
    }
    // 水珠只挂在最上面几张卡上：矩形由 Compose 侧测量后传进来
    float4 rect = pickRect(fragCoord);
    if (rect.z <= 0.0) {
        return uiColor;
    }
    float2 local = fragCoord - rect.xy;
    float2 halfSize = rect.zw * 0.5;
    float cornerRadius = min(uCornerRadius, min(halfSize.x, halfSize.y));
    float cornerFade = 1.0 - smoothstep(-1.0, 0.5, sdRoundRect(local - halfSize, halfSize, cornerRadius));
    if (cornerFade <= 0.0) {
        return uiColor;
    }
    float2 tResolution = float2(rect.z);
    float2 UV = local / tResolution;

    float2 uv = UV - float2(0.5);
    uv.y = 1.0 - uv.y;
    float alpha = smoothstep(0.0, 2.0, uTime);
    float t = mod(uTime * 0.46, 5.0);
    float rainAmount = sin(UV.x * UV.y) * 0.3 + 0.7;
    float staticDrops = smoothstep(-0.5, 1.0, rainAmount) * 2.0;

    float4 dropLayer = GenDropLayerN(uv, t);
    float3 staticN = GenStaticLayerN(uv);
    float c = Drops(uv, dropLayer.xyz, staticN, t, staticDrops, rainAmount, dropLayer.w);

    float3 col = uiColor.rgb;
    if (c > 0.001) {
        float2 e = float2(0.0005, 0.0);
        float cx = Drops(uv + e, dropLayer.xyz, staticN, t, staticDrops, rainAmount, dropLayer.w);
        float2 n = float2(c - cx, cx - c);
        float2 samplePx = rect.xy + (UV + n) * rect.z;
        half4 dropColor = content.eval(clamp(samplePx, float2(0.5), res - 0.5));
        col = mix(uiColor.rgb, dropColor.rgb, uAlpha * alpha * dropletsFade() * cornerFade);
    }
    col = mix(col, float3(0.86, 0.84, 1.0), uFlash);
    return half4(col, uiColor.a);
}
"""

/**
 * 雪：`snow.frag` 的移植。
 *
 * 原版读 `vieweffect_snow.webp` 的 R/A 当遮罩，`uProgress` 控制积雪范围，
 * 覆盖度按 `1 - e^(-uvY²)` 加权（顶部最厚），白色只按 `w * 0.4` 叠上去。
 */
internal val WEATHER_SNOW_SHADER = """
uniform shader content;
uniform float2 uSize;
uniform float uTime;
uniform float uProgress;
uniform float uAlpha;

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

half4 main(float2 fragCoord) {
    float2 res = max(uSize, float2(1.0));
    float2 px = clamp(fragCoord, float2(0.5), res - 0.5);
    half4 uiColor = content.eval(px);
    if (uiColor.a < 0.01) {
        return half4(0.0);
    }
    if (uProgress < 0.001) {
        return uiColor;
    }
    float2 uv = fragCoord / res;

    // 代替 vieweffect_snow.webp：两层噪声当雪点遮罩，缓慢向下漂
    float2 q = float2(uv.x, uv.y) * 40.0;
    q.y -= uTime * 0.02;
    float mask = 0.55 + 0.45 * (noise(q * 2.0) * 0.55 + noise(q * 6.0) * 0.45);

    float a = max(smoothstep(uProgress, 0.0, 1.0 - mask), 0.0);
    float uvY = 1.0 - uv.y;
    float w = a * (1.0 - 1.0 / exp(uvY * uvY)) * 0.5;

    float3 col = uiColor.rgb;
    if (w >= 0.01) {
        col = mix(uiColor.rgb, float3(w * 0.4), w);
    }
    return half4(mix(uiColor.rgb, col, uAlpha), uiColor.a);
}
"""

/**
 * 霜：`frost.frag` 的移植。
 *
 * 结霜范围由 `uProgress³` 映射到 0..0.6，遮罩取噪声（代替 `vieweffect_frost.webp`），
 * 同样按 `1 - e^(-uvY²)` 让顶部更厚，混入强度是 `0.7 * w * uProgress`。
 */
internal val WEATHER_FROST_SHADER = """
uniform shader content;
uniform float2 uSize;
uniform float uTime;
uniform float uProgress;
uniform float uAlpha;

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

half4 main(float2 fragCoord) {
    float2 res = max(uSize, float2(1.0));
    float2 px = clamp(fragCoord, float2(0.5), res - 0.5);
    half4 uiColor = content.eval(px);
    if (uiColor.a < 0.01) {
        return half4(0.0);
    }
    if (uProgress < 0.001) {
        return uiColor;
    }
    float2 uv = fragCoord / res;

    float progress = mix(0.0, 0.6, uProgress * uProgress * uProgress);
    float2 q = uv * 78.0 + float2(uTime * 0.006, uTime * 0.010);
    float mask = noise(q) * 0.60 + noise(q * 2.6) * 0.40;

    float a = max(smoothstep(progress, 0.0, mask), 0.0);
    float uvY = 1.0 - uv.y;
    float w = a * (1.0 - 1.0 / exp(uvY * uvY)) * 0.5;

    float3 col = uiColor.rgb;
    if (w >= 0.01) {
        col = mix(uiColor.rgb, float3(w * 0.5), 0.7 * w * clamp(uProgress, 0.0, 1.0));
    }
    return half4(mix(uiColor.rgb, col, uAlpha), uiColor.a);
}
"""
