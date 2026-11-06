// Copyright (C) 2026 Haooz · AGPL-3.0-only
//
// 柔光玻璃的 shader 源码：AGSL（Android）与 SkSL（Skiko 各平台）共用同一份文本。
//
// 采样源为纹理本身，模糊与调色均由 uniform 在 shader 内现算，调整模糊半径、
// 纹理映射与调色参数不会触发 CPU 侧重建。可见轮廓由 Compose 的 G2 Path 裁剪；
// shader 中的圆弧 SDF 仅用于计算倒角带、法线与高光。

package com.haooz.chedule.ui.glass

import com.kyant.capsule.SdfG2Corner

// ── 公共 shader 片段 ─────────────────────────────────────────────────

/** 几何 SDF：标准圆弧圆角矩形的距离与梯度，用于倒角带、法线与方向光计算。 */
internal val SOFT_GLASS_SDF = SdfG2Corner.shaderSource

/**
 * 大模糊底色采样：固定取金字塔最粗一级，与当前模糊档位无关。
 *
 * 模糊已在金字塔中烘焙（见 [SoftGlassBackdrop]），此处只做相邻级别的混合。
 * 半径超过 20 texel 时，少量抽头的螺旋采样会产生方向性重影，因此采用
 * 「先降采样、再模糊」的金字塔方案。屏幕坐标到纹理坐标的映射由 `uTexMap` 一次仿射完成。
 */
internal val SOFT_GLASS_WALLPAPER_COARSE = """
const float3 GLASS_LUMA = float3(0.2126, 0.7152, 0.0722);

// 大模糊底色固定取最粗一级：与当前模糊档位无关，采样结果更稳定。
float3 sampleCoarse(float2 texPx) {
    return uWallpaperCoarse.eval(
        clamp(texPx * uLevelScaleCoarse, float2(0.5), uTexSizeCoarse - 0.5)
    ).rgb;
}
"""

/** 主体采样：相邻两级按权重混合，实现级别间的连续过渡。 */
internal val SOFT_GLASS_WALLPAPER_LEVELS = """
float3 sampleLevel(float2 texPx) {
    float3 a = uWallpaperA.eval(clamp(texPx * uLevelScaleA, float2(0.5), uTexSizeA - 0.5)).rgb;
    float3 b = uWallpaperB.eval(clamp(texPx * uLevelScaleB, float2(0.5), uTexSizeB - 0.5)).rgb;
    return mix(a, b, uBlendT);
}
"""

/**
 * 颜色处理：与参考实现 `libhwui.so` 的 `processGlassColor` 等价。
 *
 * 顺序为「先模糊、后调色」：gcr 与 glassColor 的输入是重模糊后的背景色 `bigCol`，
 * 该数据依赖决定了二者不可交换。参考实现在 blur stage 的低分辨率图上执行本步；
 * 此处在采样点完成模糊后逐像素调色，连续域上等价，并省去一次降采样与上采样。
 */
internal val SOFT_GLASS_COLOR_PROCESS = """
// 压暗混向色：参考实现为硬编码的深紫黑，此处开放为 uniform 以支持中性配色
uniform float3 uDarkerColor;

float glassLuma(float3 c) { return dot(c, GLASS_LUMA); }

float3 glassDarker(float3 c) {
    float k = mix(0.0, uDarker, smoothstep(uDarkerRange.x, uDarkerRange.y, glassLuma(c)));
    return mix(c, uDarkerColor, k);
}

float3 glassAdjust(float3 c, float sat, float bri) {
    float l = dot(c, float3(0.2125, 0.7153, 0.0721));
    return mix(float3(l), c, sat) + bri;
}

float4 processGlassColor(float3 refractCol, float3 bigCol) {
    float3 c = glassDarker(refractCol);
    // luminanceCurve（比例模式）
    float l = clamp(glassLuma(c), 0.0, 1.0);
    float lc = ((uLuminance.x * l + uLuminance.y) * l + uLuminance.z) * l + uLuminance.w;
    float curve = clamp(lc, 0.0, 1.0) / max(l, 0.01) * smoothstep(0.0, 0.1, l);
    c = c * mix(1.0, curve, uLuminanceAmount);
    c = glassAdjust(c, uSaturation, uBrightness);

    // glassColor 由大模糊底色派生：darker → adjustColor(bg) → 向白色提亮
    float3 b = glassDarker(bigCol);
    b = glassAdjust(b, uBgSat, uBgBri);
    b = mix(b, float3(1.0), uInnerWhite);

    float lumin = clamp(glassLuma(b), 0.0, 1.0);
    float tBurn = (uBurn <= 0.0) ? 1.0 : pow(lumin, uBurn);
    tBurn -= 0.5;
    float ratio = 0.8 * mix(lumin, 1.0, 1.587 * tBurn * tBurn * tBurn + 0.5);

    float3 gcr = mix(float3(1.0), b, ratio);
    float m = (gcr.r + gcr.g + gcr.b) / 3.0;
    float gmask = smoothstep(0.0, 0.4, length(b - float3(m)));
    gcr = mix(float3(m), gcr, gmask);
    gcr = mix(gcr, uGlassColor.rgb, uGlassColor.a);

    float im = uInnerMix * ratio;
    c = mix(c * gcr, gcr, im);
    return float4(clamp(c, float3(0.0), float3(1.0)), 1.0);
}
"""

/** 主体 shader：几何 SDF + 金字塔采样 + 颜色处理 + 倒角剖面与光照。 */
internal val SOFT_GLASS_SHADER = SOFT_GLASS_SDF + """
// ── 几何 ─────────────────────────────────────────────────────────────
uniform float2 uSize;
uniform float4 uCornerRadii;
uniform float2 uOrigin;

// ── 采样源 · 壁纸纹理与屏幕到纹理的仿射 ──────────────────────────────
uniform shader uWallpaperA;
uniform shader uWallpaperB;
uniform shader uWallpaperCoarse;
uniform float4 uTexMap;
uniform float2 uTexSizeA;
uniform float2 uTexSizeB;
uniform float2 uTexSizeCoarse;
uniform float  uLevelScaleA;
uniform float  uLevelScaleB;
uniform float  uLevelScaleCoarse;
uniform float  uBlendT;

uniform float  uEdgePx;
uniform float  uEdgePow;
uniform float  uInvEdgePow;
uniform float  uThicknessPx;
uniform float  uReflectOffsetPx;
uniform float  uIOR;
uniform float  uReflStrength;
uniform float  uReflLighten;
uniform float  uColorPow;
uniform float4 uGlassColor;
uniform float  uAlpha;
uniform float  uHit;
uniform float  uInnerBottom;
// 高光描边开关：0 = 关（只切倒角上的方向光）
uniform float  uEdgeHighlight;
uniform float3 uLightDir;
uniform float  uAngleK;
uniform float  uLightIntensity;
uniform float  uLightOpposite;
uniform float  uUnshade;
uniform float  uUsePowGate;
uniform float  uDebug;
uniform float  uNmlZAAThreshold;

// ── 颜色处理 · 与参考实现 applyGlassColorProcess 同层级 ──────────────
uniform float  uDarker;
uniform float2 uDarkerRange;
uniform float4 uLuminance;
uniform float  uLuminanceAmount;
uniform float  uSaturation;
uniform float  uBrightness;
uniform float  uBgSat;
uniform float  uBgBri;
uniform float  uInnerWhite;
uniform float  uInnerMix;
uniform float  uBurn;

const float3 LUMA = float3(0.2126, 0.7152, 0.0722);

""" + SOFT_GLASS_WALLPAPER_COARSE + SOFT_GLASS_WALLPAPER_LEVELS + SOFT_GLASS_COLOR_PROCESS + """

// ── 倒角剖面 · 参考实现 libhwui.so 的 SDF 烘焙 shader ────────────────
//
// smooth5_vertical(x, 0.5) 先开方再作五次平滑，而非单次五次平滑：
// 它使折射区只覆盖边界少量像素。线性剖面会使倒角带法线接近水平、位移恒定，
// 造成整片拉花；参考实现在 x=0.5 处 nmlZ 已达 0.95，仅最外 1/4 带参与折射。
float smooth5Map(float t) {
    t = mix(0.5, 1.0, t);
    t = clamp(t, 0.0, 1.0);
    t = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    return (t - 0.5) * 2.0;
}

// 参考实现固定 k = 0.5；此处同时给出解析导数。
float circleOf(float x) {
    return smooth5Map(sqrt(clamp(x, 0.0, 1.0)));
}

// d(circleOf)/dx = 15·u²(1-u)²/√x，u = 0.5 + 0.5√x
float circleOfDx(float x) {
    float s = sqrt(max(x, 1e-4));
    float u = 0.5 + 0.5 * s;
    return 15.0 * u * u * (1.0 - u) * (1.0 - u) / s;
}

float luma(float3 c) { return dot(c, LUMA); }

// AGSL 未提供内建 refract()，此处给出等价实现
float3 miRefract(float3 I, float3 N, float eta) {
    float d = dot(N, I);
    float k = 1.0 - eta * eta * (1.0 - d * d);
    if (k < 0.0) { return float3(0.0); }
    return eta * I - (eta * d + sqrt(k)) * N;
}

float4 hsvv(float4 col, float lighten) {
    float v = luma(col.rgb);
    float w = smoothstep(0.0, 0.5, v);
    float k = mix(1.0 - v, v, w);
    float g = 1.0 + smoothstep(0.0, 1.0, lighten) * mix(0.75, 0.4, w);
    return float4((col.rgb + k) * g - k, col.a);
}

float dynamicAdd(float3 color) {
    return mix(0.5, mix(0.5, 1.0, smoothstep(0.2, 1.0, distance(float3(1.0), color))), luma(color));
}

float3 addLight(float3 color, float3 lightColor, float strength) {
    float w = 0.3 + 0.7 * clamp(1.9 - 2.15 * luma(color), 0.0, 1.0);
    return color + lightColor * strength * w;
}

float calculateLighting(float3 normal, float3 lightDir, float intensity) {
    float d = dot(normal, lightDir);
    float angleFactor = clamp(1.0 - sqrt(max(1.0 - d, 0.0)) * uAngleK, 0.0, 1.0);
    // 参考实现原式不含 pow(d, edgePow)：档位中的 DirectionalLight.edgePow 仅用于 bloom 滤镜。
    return max(d, 0.0) * intensity * angleFactor;
}

float4 processLighting(float4 color, float3 normal) {
    float3 lightNormal = float3(normal.x, -normal.y, normal.z);
    float l1 = calculateLighting(lightNormal, uLightDir, uLightIntensity);
    float l2 = calculateLighting(lightNormal, uLightDir * float3(-1.0, -1.0, 1.0), uLightOpposite);
    float lightRatio = dynamicAdd(color.rgb);
    float lightenParam = (l1 + l2) * 2.0 * lightRatio;
    return hsvv(color, lightenParam);
}

half4 main(float2 fragCoord) {
    float2 halfSize = uSize * 0.5;
    float2 p = fragCoord - halfSize;
    // 只算光学距离；可见轮廓由 G2 Path 裁剪（圆弧恒比 G2 更方，不会露缝）
    float cornerR = min(radiusAt(p, uCornerRadii), min(halfSize.x, halfSize.y));
    float sd = sdRoundRect(p, halfSize, cornerR);
    if (sd > 0.0) { return half4(0.0); }

    // x：0 = 轮廓边界，1 = 倒角带内沿（线性归一化距离）
    float x = clamp(-sd / max(uEdgePx, 1e-3), 0.0, 1.0);
    // circle = smooth5_vertical(x, 0.5)：系统 SDF 纹理里参与法线的那个剖面
    float circle = circleOf(x);
    // w = 纹理 w 通道 = curve(x, edgePow)，x ≥ 0.85 处参考实现直接夹到 1。
    // 归一化 SDF：折射厚度、反射衰减与内区判定均使用该值，而非 circle。
    float w = (x >= 0.85) ? 1.0 : 1.0 - pow(max(1.0 - circle, 1e-6), uEdgePow);
    // nmlZ = 1 - (1-w)^(1/edgePow)：两次幂正好相抵，恒等于 circle。
    float nmlZ = 1.0 - pow(max(1.0 - w, 0.0), max(uInvEdgePow, 1e-3));
    // 法线斜率为 dw/dx。参考实现对弯曲后的 sdf 做中心差分，取导数而非曲线值，
    // 该值决定折射带宽度。
    float g = (x >= 0.85) ? 0.0 :
        uEdgePow * pow(max(1.0 - circle, 1e-6), uEdgePow - 1.0) * circleOfDx(x);
    g = min(g, 64.0);

    // 抗锯齿口径与参考实现一致：smoothstep(0, smooth5_vertical(3/edge, 0.5), nmlZ)，最外约 3px 软过渡。
    float finalAlpha = smoothstep(0.0, uNmlZAAThreshold, nmlZ);

    bool inner = (1.0 - w) <= 0.000001;
    // 法线 = normalize(vec3(∇sd · g, 1))：方向取 SDF 梯度，z 由斜率归一化。
    // normal.z 与 nmlZ 为两个不同的量，不可互换。
    float2 nxy = roundRectGrad(p, halfSize, cornerR) * g;
    float3 normal = inner
        ? float3(0.0, 0.0, 1.0)
        : normalize(float3(nxy.x, nxy.y, 1.0));

    // 屏幕坐标 → 第 0 级纹理 texel。uOrigin 是面板左上角的窗口坐标，壁纸层与窗口同原点。
    float2 texPx = (uOrigin + fragCoord) * uTexMap.xy + uTexMap.zw;
    // 屏幕 px → 第 0 级 texel 的比例（x / y 相同：对齐是各向同性的），只用于折射 / 反射位移
    float texK = uTexMap.x;

    // ── 调试视图 ─────────────────────────────────────────────────────
    if (uDebug > 0.5) {
        if (uDebug < 1.5) {
            return half4(x, x, x, 1.0);
        } else if (uDebug < 2.5) {
            return half4(inner ? 0.0 : 1.0, inner ? 1.0 : 0.0, 0.0, 1.0);
        } else {
            return half4(sampleLevel(texPx), 1.0);
        }
    }

    float3 refractionCol;
    if (inner) {
        refractionCol = sampleLevel(texPx);
    } else {
        float ior = uIOR + 0.2 * uHit;
        float3 refractDir = miRefract(float3(0.0, 0.0, -1.0), normal, 1.0 / ior);
        float refractThickness = mix((uThicknessPx - uEdgePx) * 2.0, uThicknessPx * 2.0, w);
        // uThicknessPx = 0 表示关闭折射。t=0 时上式为 mix(-2·edge, 0, w)，
        // 即负厚度引起的反向折射；此处显式归零以保持语义一致。
        refractThickness *= step(0.5, uThicknessPx);
        refractionCol = sampleLevel(texPx + refractDir.xy * refractThickness * texK);
    }

    float3 materialRgb;
    if (inner) {
        materialRgb = refractionCol;
    } else {
        // 反射方向 = 2*N.z*N.xy
        float2 refractXY = 2.0 * normal.z * normal.xy;
        float3 reflectionCol = sampleLevel(
            texPx + refractXY * (uReflectOffsetPx * (1.0 - w) * texK)
        );
        materialRgb = mix(refractionCol, reflectionCol, (1.0 - nmlZ) * uReflStrength);
        materialRgb = addLight(materialRgb, reflectionCol, (1.0 - w) * uReflLighten);
    }

    // 大模糊支（glassColor 的来源）只在需要它的地方采：inner 也参与 innerMix。
    float3 bigCol = sampleCoarse(texPx);

    float4 glassMaterial = processGlassColor(materialRgb, bigCol);

    float tEdge = smoothstep(0.0, 1.0, fragCoord.y / max(uSize.y, 1.0));
    glassMaterial.rgb += tEdge * tEdge * uInnerBottom;

    // 高光描边开关仅控制此处：倒角带上的方向光（左上亮、右下暗、沿边起伏）
    if (!inner && uEdgeHighlight > 0.5) { glassMaterial = processLighting(glassMaterial, normal); }

    if (uUsePowGate > 0.5) {
        glassMaterial = pow(glassMaterial, float4(uColorPow));
    }
    glassMaterial.rgb = mix(clamp(glassMaterial.rgb, float3(0.0), float3(1.0)), uGlassColor.rgb, uUnshade);
    glassMaterial.a *= uAlpha * finalAlpha;

    // 预乘 alpha 输出
    return half4(glassMaterial.rgb * glassMaterial.a, glassMaterial.a);
}
"""

/**
 * 描边 shader：取大模糊底色在倒角带上叠加方向光，再以 LIGHTEN 混合到主体。
 *
 * `rimMask` 将描边限制在倒角带最外段（5% 满强度，60% 处渐隐到 0）。
 * 剖面固定使用 `nmlZ = d`：改用 S 曲线会压平该段梯度，使高光由沿边起伏退化为均匀亮条。
 */
internal val SOFT_GLASS_RIM_SHADER = SOFT_GLASS_SDF + """
uniform float2 uSize;
uniform float4 uCornerRadii;
uniform float2 uOrigin;

uniform shader uWallpaperCoarse;
uniform float4 uTexMap;
uniform float2 uTexSizeCoarse;
uniform float  uLevelScaleCoarse;

uniform float  uEdgePx;
uniform float  uInvEdgePow;
uniform float3 uLightDir;
uniform float  uAngleK;
uniform float  uLightIntensity;
uniform float  uLightOpposite;
uniform float  uColorPow;
uniform float  uAlpha;
uniform float  uRimWidthPx;
uniform float  uRimSaturation;
uniform float  uRimBrightness;

""" + SOFT_GLASS_WALLPAPER_COARSE + """

const float3 LUMA = float3(0.2126, 0.7152, 0.0722);

float luma(float3 c) { return dot(c, LUMA); }

float4 hsvv(float4 col, float lighten) {
    float v = luma(col.rgb);
    float w = smoothstep(0.0, 0.5, v);
    float k = mix(1.0 - v, v, w);
    float g = 1.0 + smoothstep(0.0, 1.0, lighten) * mix(0.75, 0.4, w);
    return float4((col.rgb + k) * g - k, col.a);
}

float dynamicAdd(float3 color) {
    return mix(0.5, mix(0.5, 1.0, smoothstep(0.2, 1.0, distance(float3(1.0), color))), luma(color));
}

float calculateLighting(float3 normal, float3 lightDir, float intensity) {
    float d = dot(normal, lightDir);
    float angleFactor = clamp(1.0 - sqrt(max(1.0 - d, 0.0)) * uAngleK, 0.0, 1.0);
    return max(d, 0.0) * intensity * angleFactor;
}

float4 processLighting(float4 color, float3 normal) {
    float3 lightNormal = float3(normal.x, -normal.y, normal.z);
    float l1 = calculateLighting(lightNormal, uLightDir, uLightIntensity);
    float l2 = calculateLighting(lightNormal, uLightDir * float3(-1.0, -1.0, 1.0), uLightOpposite);
    float lightenParam = (l1 + l2) * 2.0 * dynamicAdd(color.rgb);
    return hsvv(color, lightenParam);
}

half4 main(float2 fragCoord) {
    float2 halfSize = uSize * 0.5;
    float2 p = fragCoord - halfSize;
    // 只算光学距离；可见轮廓由 G2 Path 裁剪（圆弧恒比 G2 更方，不会露缝）
    float cornerR = min(radiusAt(p, uCornerRadii), min(halfSize.x, halfSize.y));
    float sd = sdRoundRect(p, halfSize, cornerR);
    if (sd > 0.0) { return half4(0.0); }

    float d = clamp(-sd / max(uEdgePx, 1e-3), 0.0, 1.0);

    // 剖面与参考实现一致（nmlZ = d）
    float nmlZ = 1.0 - pow(1.0 - d, max(uInvEdgePow, 1e-3));
    float slope = sqrt(max(0.0, 1.0 - nmlZ * nmlZ));
    // 1px 居中 AA：边界内 0.5px 全实，外 0.5px 全透
    float finalAlpha = smoothstep(-0.5, 0.5, -sd);

    bool inner = (1.0 - d) <= 0.000001;
    float2 nlmXY = inner ? float2(0.0) : roundRectGrad(p, halfSize, cornerR) * slope;
    float3 normal = inner ? float3(0.0, 0.0, 1.0) : float3(nlmXY, nmlZ);

    float2 texPx = (uOrigin + fragCoord) * uTexMap.xy + uTexMap.zw;
    float4 color = float4(sampleCoarse(texPx), 1.0);
    if (!inner) { color = processLighting(color, normal); }
    color = pow(color, float4(uColorPow));
    color.rgb = clamp(color.rgb, float3(0.0), float3(1.0));
    // 描边更艳、更亮：先按亮度饱和，再整体提亮
    float rimLuma = luma(color.rgb);
    color.rgb = clamp(
        mix(float3(rimLuma), color.rgb, uRimSaturation) * uRimBrightness,
        float3(0.0),
        float3(1.0)
    );

    // 描边集中在最外一圈：极窄满强度平台，之后快速渐隐
    float rimT = clamp(d * uEdgePx / max(uRimWidthPx, 1e-3), 0.0, 1.0);
    float rimMask = 1.0 - smoothstep(0.05, 0.6, rimT);
    color.a *= finalAlpha * rimMask * uAlpha;

    return half4(color.rgb * color.a, color.a);
}
"""


/**
 * 实时录制版 shader：采样源为控件脚下的真实内容（已经过 BlurEffect 模糊），
 * 而非壁纸纹理，适用于固定样式的小尺寸悬浮控件。
 *
 * 与 [SOFT_GLASS_SHADER] 的差别仅在采样入口：
 * - 不使用金字塔与 `uTexMap`，内容层已是控件局部坐标且已完成模糊；
 * - 调色链不含 gcr（该步骤需要完整的 glassColor 底图），保留 darker / adjustColor / innerMix。
 *
 * `content` 由 `RuntimeShaderEffect(shader, "content")` 注入，坐标单位为控件局部像素。
 */
internal val SOFT_GLASS_LIVE_SHADER = SOFT_GLASS_SDF + """
uniform shader content;

uniform float2 uSize;
uniform float4 uCornerRadii;
// 可见形状的外接矩形 [left, top, right, bottom]（节点坐标系 px）。
// 默认等于整个节点；带 bleed 的自定义 Shape 传入可见矩形，倒角带才会落在真实轮廓上。
uniform float4 uShapeRect;

uniform float  uEdgePx;
uniform float  uEdgePow;
uniform float  uInvEdgePow;
uniform float  uThicknessPx;
uniform float  uReflectOffsetPx;
uniform float  uIOR;
uniform float  uReflStrength;
uniform float  uReflLighten;
uniform float  uColorPow;
uniform float4 uGlassColor;
uniform float  uAlpha;
uniform float  uInnerBottom;
uniform float  uEdgeHighlight;
uniform float3 uLightDir;
uniform float  uAngleK;
uniform float  uLightIntensity;
uniform float  uLightOpposite;
uniform float  uNmlZAAThreshold;

// 调色链的入参：与主体共用 SOFT_GLASS_COLOR_PROCESS，字段一一对应
uniform float  uDarker;
uniform float2 uDarkerRange;
uniform float4 uLuminance;
uniform float  uLuminanceAmount;
uniform float  uSaturation;
uniform float  uBrightness;
uniform float  uBgSat;
uniform float  uBgBri;
uniform float  uInnerWhite;
uniform float  uInnerMix;
uniform float  uBurn;
uniform float  uUnshade;
uniform float  uUsePowGate;

const float3 GLASS_LUMA = float3(0.2126, 0.7152, 0.0722);

float luma(float3 c) { return dot(c, GLASS_LUMA); }

float3 sampleContent(float2 p) {
    return content.eval(clamp(p, float2(0.5), uSize - 0.5)).rgb;
}

/*
 * 大模糊底（glassColor 的来源）。卡片侧直接采 500px 粗级纹理；这里只有一张
 * 「控件脚下内容」，用绕一圈的 5 点平均近似那一级。**只影响底色取样，
 * 后面的调色公式与卡片逐行相同**（同一个 processGlassColor）。
 */
float3 contentBig(float2 p) {
    float r = min(uSize.x, uSize.y) * 0.42;
    float3 sum = sampleContent(p);
    for (int i = 0; i < 4; i++) {
        float a = float(i) * 1.5707963 + 0.6;
        sum += sampleContent(p + float2(cos(a), sin(a)) * r);
    }
    return sum / 5.0;
}

float smooth5Map(float t) {
    t = mix(0.5, 1.0, t);
    t = clamp(t, 0.0, 1.0);
    t = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    return (t - 0.5) * 2.0;
}

float circleOf(float x) { return smooth5Map(sqrt(clamp(x, 0.0, 1.0))); }

float circleOfDx(float x) {
    float s = sqrt(max(x, 1e-4));
    float u = 0.5 + 0.5 * s;
    return 15.0 * u * u * (1.0 - u) * (1.0 - u) / s;
}

float3 miRefract(float3 I, float3 N, float eta) {
    float d = dot(N, I);
    float k = 1.0 - eta * eta * (1.0 - d * d);
    if (k < 0.0) { return float3(0.0); }
    return eta * I - (eta * d + sqrt(k)) * N;
}

float4 hsvv(float4 col, float lighten) {
    float v = luma(col.rgb);
    float w = smoothstep(0.0, 0.5, v);
    float k = mix(1.0 - v, v, w);
    float g = 1.0 + smoothstep(0.0, 1.0, lighten) * mix(0.75, 0.4, w);
    return float4((col.rgb + k) * g - k, col.a);
}

float dynamicAdd(float3 color) {
    return mix(0.5, mix(0.5, 1.0, smoothstep(0.2, 1.0, distance(float3(1.0), color))), luma(color));
}

float3 addLight(float3 color, float3 lightColor, float strength) {
    float w = 0.3 + 0.7 * clamp(1.9 - 2.15 * luma(color), 0.0, 1.0);
    return color + lightColor * strength * w;
}

float calculateLighting(float3 normal, float3 lightDir, float intensity) {
    float d = dot(normal, lightDir);
    float angleFactor = clamp(1.0 - sqrt(max(1.0 - d, 0.0)) * uAngleK, 0.0, 1.0);
    return max(d, 0.0) * intensity * angleFactor;
}

float4 processLighting(float4 color, float3 normal) {
    float3 lightNormal = float3(normal.x, -normal.y, normal.z);
    float l1 = calculateLighting(lightNormal, uLightDir, uLightIntensity);
    float l2 = calculateLighting(lightNormal, uLightDir * float3(-1.0, -1.0, 1.0), uLightOpposite);
    float lightenParam = (l1 + l2) * 2.0 * dynamicAdd(color.rgb);
    return hsvv(color, lightenParam);
}
""" + SOFT_GLASS_COLOR_PROCESS + """

half4 main(float2 fragCoord) {
    float2 shapeSize = max(uShapeRect.zw - uShapeRect.xy, float2(1.0));
    float2 halfSize = shapeSize * 0.5;
    // 倒角带的参考中心是可见形状中心，不是含 bleed 的节点中心
    float2 p = fragCoord - (uShapeRect.xy + uShapeRect.zw) * 0.5;
    // 只算光学距离；可见轮廓由 G2 Path 裁剪（圆弧恒比 G2 更方，不会露缝）
    float cornerR = min(radiusAt(p, uCornerRadii), min(halfSize.x, halfSize.y));
    float sd = sdRoundRect(p, halfSize, cornerR);
    if (sd > 0.0) { return half4(0.0); }

    float x = clamp(-sd / max(uEdgePx, 1e-3), 0.0, 1.0);
    float circle = circleOf(x);
    float w = (x >= 0.85) ? 1.0 : 1.0 - pow(max(1.0 - circle, 1e-6), uEdgePow);
    float nmlZ = 1.0 - pow(max(1.0 - w, 0.0), max(uInvEdgePow, 1e-3));
    float g = (x >= 0.85) ? 0.0 :
        uEdgePow * pow(max(1.0 - circle, 1e-6), uEdgePow - 1.0) * circleOfDx(x);
    g = min(g, 64.0);

    float finalAlpha = smoothstep(0.0, uNmlZAAThreshold, nmlZ);

    bool inner = (1.0 - w) <= 0.000001;
    float2 nxy = roundRectGrad(p, halfSize, cornerR) * g;
    float3 normal = inner
        ? float3(0.0, 0.0, 1.0)
        : normalize(float3(nxy.x, nxy.y, 1.0));

    float3 refractionCol;
    if (inner) {
        refractionCol = sampleContent(fragCoord);
    } else {
        float3 refractDir = miRefract(float3(0.0, 0.0, -1.0), normal, 1.0 / uIOR);
        float refractThickness = mix((uThicknessPx - uEdgePx) * 2.0, uThicknessPx * 2.0, w);
        refractThickness *= step(0.5, uThicknessPx);
        refractionCol = sampleContent(fragCoord + refractDir.xy * refractThickness);
    }

    float3 materialRgb;
    if (inner) {
        materialRgb = refractionCol;
    } else {
        float2 refractXY = 2.0 * normal.z * normal.xy;
        float3 reflectionCol = sampleContent(
            fragCoord + refractXY * (uReflectOffsetPx * (1.0 - w))
        );
        materialRgb = mix(refractionCol, reflectionCol, (1.0 - nmlZ) * uReflStrength);
        materialRgb = addLight(materialRgb, reflectionCol, (1.0 - w) * uReflLighten);
    }

    float3 bigCol = contentBig(fragCoord);

    float4 glassMaterial = processGlassColor(materialRgb, bigCol);

    float tEdge = smoothstep(0.0, 1.0, fragCoord.y / max(uSize.y, 1.0));
    glassMaterial.rgb += tEdge * tEdge * uInnerBottom;

    if (!inner && uEdgeHighlight > 0.5) { glassMaterial = processLighting(glassMaterial, normal); }

    if (uUsePowGate > 0.5) {
        glassMaterial = pow(glassMaterial, float4(uColorPow));
    }
    glassMaterial.rgb = mix(clamp(glassMaterial.rgb, float3(0.0), float3(1.0)), uGlassColor.rgb, uUnshade);
    glassMaterial.a *= uAlpha * finalAlpha;

    return half4(glassMaterial.rgb * glassMaterial.a, glassMaterial.a);
}
"""
