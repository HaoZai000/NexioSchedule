// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.glass

/*
 * 柔光玻璃材质（HyperOS `mi_glass_sdf` 的 AGSL / SkSL 实现）。
 *
 * 数据流：`SoftGlassBackdrop` 提供轻 / 重两张模糊图；主体按像素到轮廓的距离 `d`
 * 做折射、反射与方向光，`SOFT_GLASS_RIM_SHADER` 再用 LIGHTEN 叠一圈描边。
 *
 * 形状：解析式 p-范数 G2 圆角，主体与描边共用同一份 `SOFT_GLASS_SDF`。
 * 平台：Android 用 AGSL `RuntimeShader`，桌面 / iOS / 鸿蒙用 Skiko SkSL；shader 源码共用。
 */

/*
 * 形状 SDF：G2 圆角用 p-范数超椭圆逼近。
 * r 是名义圆角；s = r * (1 + ext * ratio) 是角的扩展尺寸，p = 2 + bonus * ratio。
 * ext 取 0.40（库里的 G2 为 0.5287），让玻璃角部略收进 Compose 的 G2 形状。
 */

internal val SOFT_GLASS_SDF = """
const float G2_EXT = 0.40;
const float G2_POW_BONUS = 0.60;

float radiusAt(float2 c, float4 r) {
    if (c.x >= 0.0) {
        return c.y <= 0.0 ? r.y : r.z;
    } else {
        return c.y <= 0.0 ? r.x : r.w;
    }
}

float g2Ratio(float2 halfSize, float r) {
    return clamp((min(halfSize.x, halfSize.y) / max(r, 1e-3) - 1.0) / G2_EXT, 0.0, 1.0);
}

float g2CornerSize(float2 halfSize, float r) {
    return r * (1.0 + G2_EXT * g2Ratio(halfSize, r));
}

float g2Pow(float2 halfSize, float r) {
    return 2.0 + G2_POW_BONUS * g2Ratio(halfSize, r);
}

float g2Pnorm(float2 v, float n) {
    return pow(pow(max(v.x, 0.0), n) + pow(max(v.y, 0.0), n), 1.0 / n);
}

float sdG2RoundedRect(float2 p, float2 halfSize, float s, float n) {
    float2 q = abs(p) - halfSize + s;
    return g2Pnorm(q, n) + min(max(q.x, q.y), 0.0) - s;
}

float2 g2Grad(float2 p, float2 halfSize, float s, float n) {
    float2 q = abs(p) - halfSize + s;
    float2 g;
    if (q.x > 0.0 && q.y > 0.0) {
        float pn = g2Pnorm(q, n);
        float denom = pow(max(pn, 1e-4), n - 1.0);
        g = float2(pow(q.x, n - 1.0), pow(q.y, n - 1.0)) / denom;
    } else {
        g = (q.x > q.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
    }
    return sign(p) * g;
}
"""

internal val SOFT_GLASS_SHADER = SOFT_GLASS_SDF + """
// ── 几何 / 采样 ──
uniform float2 uSize;
uniform float4 uCornerRadii;
uniform float  uCornerExt;
uniform float  uCornerPow;
uniform float2 uOrigin;
uniform float2 uTexSize;
uniform float  uBakeScale;

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
uniform float3 uLightDir;
uniform float  uAngleK;
uniform float  uLightIntensity;
uniform float  uLightOpposite;
uniform float  uUnshade;
uniform float  uUsePowGate;
uniform float  uDebug;
uniform float  uNmlZAAThreshold;

uniform shader uRefractTex;
// 主体不采样 uBgColorTex（颜色处理在烘焙阶段完成）；重模糊只给 rim shader 用。
// AGSL 会优化掉未使用的 uniform，留着声明会让 setSampler 抛异常 ⇒ 整块玻璃退化成白面。

const float3 LUMA = float3(0.2126, 0.7152, 0.0722);


// ── 倒角剖面（系统原文，libhwui.so 的 SDF 烘焙 shader）──
//
// smooth5_vertical(x, 0.5) 不是「一次五次平滑」，而是**先开方、再五次平滑**：
// 它决定「折射区只有边界那几像素」。换成线性剖面，整个倒角带法线都接近水平、
// 位移恒定，卡片会被整片拉花 —— 系统在 x=0.5 处 nmlZ 已到 0.95，只剩最外 1/4 带真正折射。
float smooth5Map(float t) {
    t = mix(0.5, 1.0, t);
    t = clamp(t, 0.0, 1.0);
    t = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    return (t - 0.5) * 2.0;
}

// 系统固定 k = 0.5，这里写死，顺带给出解析导数。
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

// AGSL 没有 refract()
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
    // 系统原式：不加 pow(d, edgePow) —— token 里的 DirectionalLight.edgePow 只给 bloom 滤镜（mainLightLength）。
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


// 颜色处理已在烘焙阶段完成（SoftGlassBackdrop.applyGlassColorProcess），这里只采样。
float4 tex(float2 uvPx) {
    return uRefractTex.eval(clamp(uvPx, float2(0.5), uTexSize - 0.5));
}

half4 main(float2 fragCoord) {
    float2 halfSize = uSize * 0.5;
    float2 p = fragCoord - halfSize;
    float cornerR = radiusAt(p, uCornerRadii);
    float cornerS = cornerR * uCornerExt;
    float cornerPow = uCornerPow;
    float sd = sdG2RoundedRect(p, halfSize, cornerS, cornerPow);
    if (sd > 0.0) { return half4(0.0); }

    // x：0 = 轮廓边界，1 = 倒角带内沿（线性归一化距离）
    float x = clamp(-sd / max(uEdgePx, 1e-3), 0.0, 1.0);
    // circle = smooth5_vertical(x, 0.5)：系统 SDF 纹理里参与法线的那个剖面
    float circle = circleOf(x);
    // w = 纹理 w 通道 = curve(x, edgePow)，x ≥ 0.85 处系统直接夹到 1。
    // 这是「归一化 SDF」：折射厚度、反射衰减、内区判定都用它 —— 用的**不是** circle。
    float w = (x >= 0.85) ? 1.0 : 1.0 - pow(max(1.0 - circle, 1e-6), uEdgePow);
    // nmlZ = 1 - (1-w)^(1/edgePow)：两次幂正好相抵，恒等于 circle。
    // 仍照抄式子，保持与系统「烘焙时乘 edgePow、采样时除回去」的拆分一致。
    float nmlZ = 1.0 - pow(max(1.0 - w, 0.0), max(uInvEdgePow, 1e-3));
    // 法线斜率 = dw/dx。系统对「弯曲后的 sdf」做中心差分，所以是曲线的导数而非曲线值 ——
    // 这一步决定折射带有多宽。
    float g = (x >= 0.85) ? 0.0 :
        uEdgePow * pow(max(1.0 - circle, 1e-6), uEdgePow - 1.0) * circleOfDx(x);
    g = min(g, 64.0);

    // AA 走系统口径：smoothstep(0, smooth5_vertical(3/edge, 0.5), nmlZ)，即最外约 3px 软过渡。
    float finalAlpha = smoothstep(0.0, uNmlZAAThreshold, nmlZ);

    bool inner = (1.0 - w) <= 0.000001;
    // 法线 = normalize(vec3(∇sd · g, 1))：方向取 SDF 梯度，z 由斜率归一化。
    // 这里的 normal.z 与上面的 nmlZ 是两个量，别混用。
    float2 nxy = g2Grad(p, halfSize, cornerS, cornerPow) * g;
    float3 normal = inner
        ? float3(0.0, 0.0, 1.0)
        : normalize(float3(nxy.x, nxy.y, 1.0));

    float2 uvPx = uOrigin + fragCoord * uBakeScale;

    // ── 调试视图 ──
    if (uDebug > 0.5) {
        if (uDebug < 1.5) {
            // 1：归一化距离 x 灰度
            return half4(x, x, x, 1.0);
        } else if (uDebug < 2.5) {
            // 2：红 = 折射带，绿 = 内部
            return half4(inner ? 0.0 : 1.0, inner ? 1.0 : 0.0, 0.0, 1.0);
        } else {
            // 3：uRefractTex 原图
            float2 tuv = clamp(uvPx, float2(0.5), uTexSize - 0.5);
            half4 raw = uRefractTex.eval(tuv);
            return half4(raw.rgb, 1.0);
        }
    }

    float4 refractionResult;
    if (inner) {
        refractionResult = tex(uvPx);
    } else {
        float ior = uIOR + 0.2 * uHit;
        float3 refractDir = miRefract(float3(0.0, 0.0, -1.0), normal, 1.0 / ior);
        float refractThickness = mix((uThicknessPx - uEdgePx) * 2.0, uThicknessPx * 2.0, w);
        // uThicknessPx = 0 是外观里的「关闭折射」。但 t=0 时上式是 mix(-2·edge, 0, w)，
        // 也就是**负厚度 = 反向折射**，不是不折射。这里显式归零。
        refractThickness *= step(0.5, uThicknessPx);
        refractionResult = tex(uvPx + refractDir.xy * refractThickness * uBakeScale);
    }

    float4 glassMaterial;
    if (inner) {
        glassMaterial = refractionResult;
    } else {
        // 反射方向 = 2*N.z*N.xy
        float2 refractXY = 2.0 * normal.z * normal.xy;
        float4 reflectionResult = tex(uvPx + refractXY * (uReflectOffsetPx * (1.0 - w) * uBakeScale));
        glassMaterial = mix(refractionResult, reflectionResult, (1.0 - nmlZ) * uReflStrength);
        glassMaterial.rgb = addLight(glassMaterial.rgb, reflectionResult.rgb, (1.0 - w) * uReflLighten);
    }

    float tEdge = smoothstep(0.0, 1.0, fragCoord.y / max(uSize.y, 1.0));
    glassMaterial.rgb += tEdge * tEdge * uInnerBottom;

    if (!inner) { glassMaterial = processLighting(glassMaterial, normal); }

    if (uUsePowGate > 0.5) {
        glassMaterial = pow(glassMaterial, float4(uColorPow));
    }
    glassMaterial.rgb = mix(clamp(glassMaterial.rgb, float3(0.0), float3(1.0)), uGlassColor.rgb, uUnshade);
    glassMaterial.a *= uAlpha * finalAlpha;

    // 预乘输出
    return half4(glassMaterial.rgb * glassMaterial.a, glassMaterial.a);
}
"""

/**
 * 描边：取重模糊，在倒角带上叠方向光，再以 LIGHTEN 叠到主体上。
 *
 * rimMask 把描边收在倒角带最外段（5% 满强度 → 60% 处渐隐到 0）。
 * 剖面固定用原式 `nmlZ = d`，不要换成 S 曲线：那会压平这段梯度，
 * 高光从「沿边起伏」退化成「均匀亮条」。
 */
internal val SOFT_GLASS_RIM_SHADER = SOFT_GLASS_SDF + """
uniform float2 uSize;
uniform float4 uCornerRadii;
uniform float  uCornerExt;
uniform float  uCornerPow;
uniform float2 uOrigin;
uniform float2 uTexSize;
uniform float  uBakeScale;

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
uniform shader uBgColorTex;

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
    // 系统原式：不加 pow(d, edgePow) —— token 里的 DirectionalLight.edgePow 只给 bloom 滤镜（mainLightLength）。
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
    float cornerR = radiusAt(p, uCornerRadii);
    float cornerS = cornerR * uCornerExt;
    float cornerPow = uCornerPow;
    float sd = sdG2RoundedRect(p, halfSize, cornerS, cornerPow);
    if (sd > 0.0) { return half4(0.0); }

    float d = clamp(-sd / max(uEdgePx, 1e-3), 0.0, 1.0);

    // 固定用原式剖面（nmlZ = d）
    float nmlZ = 1.0 - pow(1.0 - d, max(uInvEdgePow, 1e-3));
    float slope = sqrt(max(0.0, 1.0 - nmlZ * nmlZ));
    // 1px 居中 AA：边界内 0.5px 全实，外 0.5px 全透
    float finalAlpha = smoothstep(-0.5, 0.5, -sd);

    bool inner = (1.0 - d) <= 0.000001;
    float2 nlmXY = inner ? float2(0.0) : g2Grad(p, halfSize, cornerS, cornerPow) * slope;
    float3 normal = inner ? float3(0.0, 0.0, 1.0) : float3(nlmXY, nmlZ);

    float2 uv = clamp(uOrigin + fragCoord * uBakeScale, float2(0.5), uTexSize - 0.5);
    float4 color = uBgColorTex.eval(uv);
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
