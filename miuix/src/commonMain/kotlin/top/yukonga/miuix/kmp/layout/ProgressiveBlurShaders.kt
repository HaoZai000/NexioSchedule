// Copyright (C) 2026 Haooz · AGPL-3.0-only

package top.yukonga.miuix.kmp.layout

/**
 * 顶栏「渐进模糊」的 AGSL 着色器。
 */

/** 镜像增益：出界采样折回后再按越界量的 (GAIN-1) 倍往内容深处推，避开与正像那一半重叠 */
private const val MIRROR_GAIN = 2.5f

/** 主 pass 采样点数。加它能降噪，但成本线性上升 */
private const val BLUR_TAPS = 32

/** denoise pass 采样点数（黄金角递推，点数不能是 8 的倍数，否则会转回原点重复） */
private const val DENOISE_TAPS = 12

/** denoise 环半径相对模糊半径的比例：必须够到主 pass 的样本间距（≈radius/3），否则只是磨细 */
private const val DENOISE_RADIUS_RATIO = 0.3f

/** 主 pass 的加权衰减：`exp(-ff / GAUSS_FALLOFF)`，越小越集中 */
private const val GAUSS_FALLOFF = 0.85f

/** 两端导数为 0 的 S 曲线，收尾比 smoothstep 更绵 */
private const val SOFTER_STEP = """
float softerstep(float a, float b, float x) {
    float s = clamp((x - a) / max(b - a, 0.0001), 0.0, 1.0);
    return s * s * s * (s * (s * 6.0 - 15.0) + 10.0);
}
"""

/**
 * 主 pass：沿黄金角螺旋取 [BLUR_TAPS] 个高斯加权样本。
 *
 * 采样坐标一律折回内容区（镜像），**不能**落到 padding 区 —— 顶栏贴屏幕顶边时，
 * 向上外扩的那圈缓冲录的是屏幕外（空），采进去就是一条透明的顶边。
 */
internal const val PROGRESSIVE_BLUR_SHADER = """
uniform shader content;
uniform float2 contentOrigin;
uniform float2 contentSize;
uniform float maxRadius;
uniform float edgeFadeStart;
layout(color) uniform half4 tint;
uniform float tintIntensity;

const float MIRROR_GAIN = $MIRROR_GAIN;

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
    // ⚠ 采样点数**不能**随半径减小（按面积收缩）—— 试过 `taps ∝ r²`，下半段噪点立刻明显：
    // 抖动采样的误差 ∝ 1/√n，与半径无关；半径小处模糊弱、细节还在，噪声反而更刺眼。
    // 保住 32 tap，成本只能从别处找（见 padding=0）。
    float h = hash12(coord);
    float2 dir = float2(cos(h * 6.2831853), sin(h * 6.2831853));
    float2 g = float2(cos(2.39996323), sin(2.39996323));
    half4 sum = half4(0.0);
    float wsum = 0.0;
    float2 span = max(contentSize - 1.0, float2(1.0));
    for (int i = 0; i < $BLUR_TAPS; i++) {
        float fi = float(i);
        float ff = (fi + 0.5) / $BLUR_TAPS.0;
        // sqrt 让样本在圆盘上面积均匀；半径再抖 ±10% 打散螺旋的规则纹路
        float r = radius * sqrt(ff);
        r *= 0.90 + 0.20 * fract(h * 93.9898 + fi * 0.7548776662);
        float2 o = dir * r;
        float w = exp(-ff / $GAUSS_FALLOFF);
        // 镜像折回内容区：clamp-to-edge 会把整条越界采样压到第一行、造成顶边硬切
        float2 relRaw = coord + o - contentOrigin;
        float2 rel = abs(relRaw);
        rel += max(-relRaw, float2(0.0)) * (MIRROR_GAIN - 1.0);
        rel = clamp(rel, float2(0.0), span);
        half4 c = content.eval(contentOrigin + rel);
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
    // padding 区不上屏：直接直通
    float2 local = coord - contentOrigin;
    if (local.x < 0.0 || local.y < 0.0 ||
        local.x >= contentSize.x || local.y >= contentSize.y) {
        return content.eval(coord);
    }
    float t = clamp(local.y / max(contentSize.y, 1.0), 0.0, 1.0);
    float u = 1.0 - smoothstep(0.0, 1.0, t);
    half4 color = progressiveBlur(coord, maxRadius * u);
    float edge = softerstep(edgeFadeStart, 1.0, t);
    color *= (1.0 - edge);
    if (tintIntensity > 0.0) {
        color = mix(color, tint * (1.0 - edge), tintIntensity * u);
    }
    return color;
}
"""

/**
 * denoise pass：环形平均，压掉主 pass 的抖动白噪。
 *
 * 环半径必须够到主 pass 的样本间距（≈radius/3），否则只是把噪声磨细、看起来一样噪。
 * 中心权重 2 —— 全等权会把整体再糊掉一圈。
 */
internal const val PROGRESSIVE_DENOISE_SHADER = """
uniform shader content;
uniform float2 contentOrigin;
uniform float2 contentSize;
uniform float maxRadius;

const float MIRROR_GAIN = $MIRROR_GAIN;

half4 main(float2 coord) {
    float2 local = coord - contentOrigin;
    if (local.x < 0.0 || local.y < 0.0 ||
        local.x >= contentSize.x || local.y >= contentSize.y) {
        return content.eval(coord);
    }
    float t = clamp(local.y / max(contentSize.y, 1.0), 0.0, 1.0);
    float r = maxRadius * (1.0 - smoothstep(0.0, 1.0, t)) * $DENOISE_RADIUS_RATIO;
    if (r < 0.4) {
        return content.eval(coord);
    }
    half4 sum = content.eval(coord) * half4(2.0);
    float wsum = 2.0;
    float2 dir = float2(1.0, 0.0);
    float2 g = float2(cos(2.39996323), sin(2.39996323));
    float2 span = max(contentSize - 1.0, float2(1.0));
    for (int i = 0; i < $DENOISE_TAPS; i++) {
        float2 relRaw = coord + dir * r - contentOrigin;
        float2 rel = abs(relRaw);
        rel += max(-relRaw, float2(0.0)) * (MIRROR_GAIN - 1.0);
        rel = clamp(rel, float2(0.0), span);
        half4 c = content.eval(contentOrigin + rel);
        if (c.a > 0.02) {
            sum += c;
            wsum += 1.0;
        }
        dir = float2(dir.x * g.x - dir.y * g.y, dir.x * g.y + dir.y * g.x);
    }
    return sum / wsum;
}
"""

/**
 * 假渐进的淡出 pass：**单次 `content.eval`、零循环**，跟 [PROGRESSIVE_BLUR_SHADER] 的 32 tap
 * 不是一个量级。
 *
 * 它接在 `blur()` 之后组成同一条 RenderEffect 链，于是淡出不再需要额外一层
 * `CompositingStrategy.Offscreen` + `drawRect(DstIn)`：录制 backdrop 的那一个离屏 layer
 * 就能把「模糊 → 淡出」一次做完。
 *
 * 分段是照搬旧 `Brush.verticalGradient` 的三个停点，保证切到这条路径观感不变：
 * `[0, fadeStart*hold]` 全不透明 → `[…, fadeStart]` 降到 [midAlpha] → `[fadeStart, 1]` 降到 0。
 * 坐标是 layer 缓冲的局部像素（padding 已压回 0），与真渐进的 contentSize 口径一致。
 */
internal const val PROGRESSIVE_FADE_SHADER = """
uniform shader content;
uniform float2 contentSize;
uniform float fadeStart;
uniform float hold;
uniform float midAlpha;

half4 main(float2 coord) {
    half4 color = content.eval(coord);
    float t = clamp(coord.y / max(contentSize.y, 1.0), 0.0, 1.0);
    float holdEnd = fadeStart * hold;
    float head = mix(1.0, midAlpha,
        clamp((t - holdEnd) / max(fadeStart - holdEnd, 0.0001), 0.0, 1.0));
    float tail = 1.0 - clamp((t - fadeStart) / max(1.0 - fadeStart, 0.0001), 0.0, 1.0);
    return color * (head * tail);
}
"""
