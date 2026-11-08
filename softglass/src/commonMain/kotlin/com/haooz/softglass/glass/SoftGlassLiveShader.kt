package com.haooz.softglass.glass

/** 实时录制路径的 shader：采样控件脚下的真实内容。 */
val SOFT_GLASS_LIVE_SHADER = GLASS_SDF + """
uniform shader content;

uniform float2 uSize;
uniform float4 uCornerRadii;
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
uniform float  uLightFalloff;
uniform float3 uLightDir;
uniform float  uAngleK;
uniform float  uLightIntensity;
uniform float  uLightOpposite;
uniform float  uNmlZAAThreshold;

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

// 大模糊底：控件脚下只有一张内容，用绕一圈的 5 点平均近似卡片侧的粗级纹理。
float3 contentBig(float2 p, float3 center) {
    float r = min(uSize.x, uSize.y) * 0.42;
    float3 sum = center;
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

float2 circleAt(float x) {
    float s = sqrt(clamp(x, 0.0, 1.0));
    float sr = max(s, 1e-2);
    float u = 0.5 + 0.5 * sr;
    return float2(smooth5Map(s), 15.0 * u * u * (1.0 - u) * (1.0 - u) / sr);
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

float4 processLighting(float4 color, float3 normal, float x, float falloffEnd) {
    float3 lightNormal = float3(normal.x, -normal.y, normal.z);
    float l1 = calculateLighting(lightNormal, uLightDir, uLightIntensity);
    float l2 = calculateLighting(lightNormal, uLightDir * float3(-1.0, -1.0, 1.0), uLightOpposite);
    float mask = (falloffEnd >= 0.999)
        ? 1.0
        : 1.0 - smoothstep(0.0, max(falloffEnd, 1e-3), x);
    float lightenParam = (l1 + l2) * 2.0 * dynamicAdd(color.rgb) * mask;
    return hsvv(color, lightenParam);
}
""" + GLASS_COLOR_PROCESS + """

half4 main(float2 fragCoord) {
    float2 shapeSize = max(uShapeRect.zw - uShapeRect.xy, float2(1.0));
    float2 halfSize = shapeSize * 0.5;
    float2 p = fragCoord - (uShapeRect.xy + uShapeRect.zw) * 0.5;
    float cornerR = min(radiusAt(p, uCornerRadii), min(halfSize.x, halfSize.y));
    float sd = sdRoundRect(p, halfSize, cornerR);
    if (sd > 0.0) { return half4(0.0); }

    float x = clamp(-sd / max(uEdgePx, 1e-3), 0.0, 1.0);
    float2 cd = circleAt(x);
    float circle = cd.x;
    float a = max(1.0 - circle, 1e-6);
    float w = (x >= 0.85)
        ? 1.0
        : (uEdgePow == 4.0 ? 1.0 - (a * a) * (a * a) : 1.0 - pow(a, uEdgePow));
    float nmlZ = (x >= 0.85) ? 1.0 : circle;

    bool inner = (1.0 - w) <= 0.000001;
    float g = inner ? 0.0 : uEdgePow * ((1.0 - w) / a) * cd.y;
    g = min(g, 64.0);

    float finalAlpha = inner ? 1.0 : smoothstep(0.0, uNmlZAAThreshold, nmlZ);

    float2 nxy = inner ? float2(0.0) : roundRectGrad(p, halfSize, cornerR) * g;
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

    float3 bigCenter;
    if (inner) { bigCenter = refractionCol; } else { bigCenter = sampleContent(fragCoord); }
    float3 bigCol = contentBig(fragCoord, bigCenter);

    float4 glassMaterial = processGlassColor(materialRgb, bigCol);

    float tEdge = smoothstep(0.0, 1.0, fragCoord.y / max(uSize.y, 1.0));
    glassMaterial.rgb += tEdge * tEdge * uInnerBottom;

    if (!inner && uEdgeHighlight > 0.5) {
        glassMaterial = processLighting(glassMaterial, normal, x, uLightFalloff);
    }

    if (uUsePowGate > 0.5) {
        glassMaterial = pow(glassMaterial, float4(uColorPow));
    }
    glassMaterial.rgb = mix(clamp(glassMaterial.rgb, float3(0.0), float3(1.0)), uGlassColor.rgb, uUnshade);
    glassMaterial.a *= uAlpha * finalAlpha;

    return half4(glassMaterial.rgb * glassMaterial.a, glassMaterial.a);
}
"""
