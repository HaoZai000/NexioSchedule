// Copyright (C) 2026 Haooz · AGPL-3.0-only
//
// 实时录制版柔光的 uniform 灌装：把 [SoftGlassStyle] 写入 [SOFT_GLASS_LIVE_SHADER]。
//
// 与卡片路径 [softGlass] 的差别仅在采样源：此处采控件脚下的真实内容，
// 由 RuntimeShaderEffect 注入 `content`。

package com.haooz.chedule.ui.glass

import com.kyant.backdrop.RuntimeShader

/**
 * σ（屏幕 px）→ Compose `BlurEffect` 的半径。
 *
 * 两条采样路的模糊量单位不同：
 * - 卡片金字塔（[SoftGlassPanel]）把 `smallBlurRadius` 当 **σ**：`levelPair` 按 texel 的
 *   σ 选级，混合后得到的就是同名 σ；
 * - 实时路径（[com.haooz.chedule.ui.components.softGlassBackdrop]）把数值直接交给
 *   `RenderEffect.createBlurEffect`，那是 Skia 的**半径**，σ = 0.57735 · radius + 0.5。
 *
 * 同一个 12 在两条路上的实际模糊量差约 1.7 倍：浮层（走实时路径）会比网格卡片更亮、
 * 糊得更少。要「看起来是同一块材质」，浮层必须把外观里的 σ 换算成半径再传。
 */
internal fun glassBlurRadiusForSigma(sigmaPx: Float): Float =
    if (sigmaPx <= 0f) 0f else (sigmaPx - 0.5f) / 0.57735f

/**
 * 把 [style] 的全部参数灌入实时录制版 shader。
 *
 * 本函数不包含数值常量：几何、光学与调色均来自样式，样式由 [GlassTone] 与
 * [GlassFill] 决定；调用方只需提供尺寸与可选圆角。
 *
 * @param style 已应用材质基调与填充策略的最终样式。
 * @param width 控件宽度（px）。
 * @param height 控件高度（px）。
 * @param radiusPx 圆角（px）；为 null 时取短边一半（胶囊）。形变中的控件应按当前尺寸逐帧传入。
 * @param shapeRectPx 可见形状外接矩形 `[left, top, right, bottom]`（节点坐标系 px）。
 *   null = 整个节点。材质节点带 bleed、可见轮廓是自定义 Path 时必须传，
 *   否则倒角带会落在 bleed 外的节点边界上，可见区域内全是「平玻璃」。
 */
internal fun RuntimeShader.applyLiveGlass(
    style: SoftGlassStyle,
    width: Float,
    height: Float,
    /** 圆角（px）；为 null 时取短边一半（胶囊）。形变中的控件每帧按当前尺寸传入。 */
    radiusPx: Float? = null,
    shapeRectPx: FloatArray? = null,
) {
    if (width <= 0f || height <= 0f) return
    val rect = shapeRectPx?.takeIf { it.size >= 4 }
    val shapeLeft = rect?.get(0) ?: 0f
    val shapeTop = rect?.get(1) ?: 0f
    val shapeRight = rect?.get(2) ?: width
    val shapeBottom = rect?.get(3) ?: height
    // 胶囊半径按可见形状短边算，否则带 bleed 的控件会被节点高度撑大
    val half = minOf(shapeRight - shapeLeft, shapeBottom - shapeTop).coerceAtLeast(1f) * 0.5f
    val edgePx = style.edgeWidth
    // refractionScale = 0 表示关闭折射，见 [SoftGlassStyle.refractionScale]
    val refractionOn = style.refractionScale > 0f

    // ── 几何 ─────────────────────────────────────────────────────────
    setFloatUniform("uSize", width, height)
    setFloatUniform("uShapeRect", shapeLeft, shapeTop, shapeRight, shapeBottom)
    // 圆角超过短边一半时钳制为胶囊。只灌名义半径：
    // 可见轮廓由 G2 Path 裁剪，shader 的圆弧 SDF 仅用于光学距离，不参与定形。
    val r = (radiusPx ?: half).coerceIn(0f, half)
    setFloatUniform("uCornerRadii", r, r, r, r)

    // ── 光学 ─────────────────────────────────────────────────────────
    setFloatUniform("uEdgePx", edgePx)
    setFloatUniform("uEdgePow", style.edgePow)
    setFloatUniform("uInvEdgePow", 1f / style.edgePow.coerceAtLeast(0.05f))
    setFloatUniform("uThicknessPx", style.thickness * style.refractionScale)
    setFloatUniform("uReflectOffsetPx", if (refractionOn) style.reflectOffset else 0f)
    setFloatUniform("uIOR", style.refractIOR)
    setFloatUniform("uReflStrength", style.reflectionStrength)
    setFloatUniform("uReflLighten", style.reflectionLighten)
    setFloatUniform("uColorPow", style.colorPow)
    setFloatUniform("uAlpha", style.alpha)
    setFloatUniform("uInnerBottom", style.innerBottom)
    setFloatUniform("uEdgeHighlight", if (style.edgeHighlight) 1f else 0f)
    setFloatUniform("uLightFalloff", style.edgeLightFalloff.coerceIn(0f, 1f))
    setFloatUniform("uUnshade", style.unshade)
    setFloatUniform("uUsePowGate", if (style.usePowGate) 1f else 0f)

    // ── 方向光 ───────────────────────────────────────────────────────
    val ld = style.lightDirection
    setFloatUniform("uLightDir", ld.first, ld.second, ld.third)
    // 方向光系数 K = 0.5 / sqrt(1 - cos(angleRange · π/2))
    val cosA = kotlin.math.cos(style.lightAngleRange * kotlin.math.PI.toFloat() * 0.5f)
    setFloatUniform(
        "uAngleK",
        0.5f / kotlin.math.sqrt((1f - cosA).coerceAtLeast(1e-6f)),
    )
    setFloatUniform("uLightIntensity", style.lightIntensity)
    setFloatUniform("uLightOpposite", style.lightOppositeIntensity)
    setFloatUniform(
        "uNmlZAAThreshold",
        smooth5Vertical(3f / edgePx.coerceAtLeast(1f)),
    )

    // ── 调色链 · 与卡片路径共用 processGlassColor ────────────────────
    // 字段与 SoftGlassPanel 的灌装一一对应
    setFloatUniform("uDarker", style.darker)
    val dc = style.darkerColor
    setFloatUniform("uDarkerColor", dc.red, dc.green, dc.blue)
    setFloatUniform("uDarkerRange", style.darkerRange.first, style.darkerRange.second)
    val lc = style.luminanceValues
    setFloatUniform(
        "uLuminance",
        lc.getOrElse(0) { 0f },
        lc.getOrElse(1) { 0f },
        lc.getOrElse(2) { 0f },
        lc.getOrElse(3) { 1f },
    )
    setFloatUniform("uLuminanceAmount", style.luminanceAmount)
    setFloatUniform("uSaturation", style.saturation)
    setFloatUniform("uBrightness", style.brightness)
    setFloatUniform("uBgSat", style.bgColorSaturation)
    setFloatUniform("uBgBri", style.bgColorBrightness)
    setFloatUniform("uInnerWhite", style.innerColorWhite)
    setFloatUniform("uInnerMix", style.innerColorMix)
    setFloatUniform("uBurn", style.burn)
    val gc = style.glassColor
    setFloatUniform("uGlassColor", gc.red, gc.green, gc.blue, style.glassColorMix)
}
