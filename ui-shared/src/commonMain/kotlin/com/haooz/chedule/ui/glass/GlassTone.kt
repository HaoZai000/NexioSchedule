// Copyright (C) 2026 Haooz · AGPL-3.0-only
//
// 材质基调：以单例形式提供一组完整的渲染参数，供 [SoftGlassStyle.tone] 选择。
//
// 每种基调全量覆盖所负责的字段，因此调整某一材质无需改动样式默认值或调用点。
// 例外：`lightDirection` 三档共引 [GlassLightDirection]，光方向是全局口径，只在那一处改。

package com.haooz.chedule.ui.glass

import androidx.compose.ui.graphics.Color

/**
 * 材质基调：定义一组完整的渲染参数。
 *
 * 用法：
 * ```kotlin
 * ScheduleSoftGlassStyle.copy(tone = BrightTone).filled(isDark)
 * ```
 *
 * ## 可调字段
 *
 * | 字段 | 作用 | 说明 |
 * |---|---|---|
 * | `edgeWidth` | 倒角带宽度 | px，直接作为最终值，不乘 density |
 * | `edgePow` | 倒角剖面指数 | 参考实现固定为 4 |
 * | `thickness` | 折射厚度，决定采样位移幅度 | px |
 * | `reflectOffset` | 反射采样偏移 | px |
 * | `refractionScale` | 折射倍率，0 = 关闭 | 材质档位也会写入该字段 |
 * | `refractIOR` | 折射率 | 参考实现各档位均为 3.0 |
 * | `reflectionStrength` | 反射混入强度 | |
 * | `reflectionLighten` | 反射提亮 | 独立于 [SoftGlassStyle.edgeHighlight] |
 * | `darker` | 亮部压暗强度 | |
 * | `darkerColor` | 压暗混向色 | 参考实现为深紫黑；`Color.Black` 可去除偏色 |
 * | `darkerRange` | 压暗作用区间 | 参考实现为 0.6 ~ 1.0 |
 * | `luminanceValues` / `luminanceAmount` | 亮度曲线系数与混合量 | |
 * | `saturation` / `brightness` | 饱和度 / 加性提亮 | |
 * | `bgColorSaturation` / `bgColorBrightness` | 大模糊底色的饱和度 / 提亮 | 仅小面板档位大于 0 |
 * | `innerColorWhite` / `innerColorMix` | 内部底色往白抬升 / 混入比例 | |
 * | `burn` | 亮度曲线拐点 | |
 * | `glassColor` / `glassColorMix` | 玻璃本色 / 混入比例 | |
 * | `colorPow` | 末尾 gamma | 1.0 表示不调整 |
 * | `alpha` / `unshade` / `usePowGate` | 不透明度 / 直出本色 / 是否执行 gamma | |
 * | `lightDirection` · `lightAngleRange` · `lightIntensity` · `lightOppositeIntensity` | 方向光参数 | 决定边缘高光 |
 * | `edgeHighlight` | 高光描边开关 | 控制倒角带方向光 |
 * | `innerBottom` | 底边提亮 | |
 *
 * 下列字段不属于材质基调：`cornerRadius`（形状由控件提供）、`smallBlurRadius` /
 * `bigBlurRadius`（模糊由采样源负责）、`hit` / `slopeMode` / `debugView` / `press*`
 * （不参与静态渲染）。材质档位还会在外层覆盖 `refractionScale` 与 `edgeHighlight`。
 */
sealed interface GlassTone {
    /** 将本基调的参数应用到 [style]，返回新样式。 */
    fun apply(style: SoftGlassStyle): SoftGlassStyle
}

/** 壁纸通透材质：参数与 `Glass_Common_Large_Regular` 一致。 */
object TokenTone : GlassTone {
    override fun apply(style: SoftGlassStyle): SoftGlassStyle = style.copy(
        // ── 几何（px） ───────────────────────────────────────────────
        edgeWidth = 60f,
        edgePow = 4f,
        thickness = 96f,
        reflectOffset = 600f,
        // ── 折射与反射 ───────────────────────────────────────────────
        refractionScale = 1f,
        refractIOR = 3f,
        reflectionStrength = 0.8f,
        reflectionLighten = 1f,
        // ── 压暗与调色 ───────────────────────────────────────────────
        darker = 0.08f,
        darkerColor = Color(0.07874f, 0.02848f, 0.09278f),
        darkerRange = 0.6f to 1.0f,
        luminanceValues = listOf(0f, 2f, 0.5f, 0.8f),
        luminanceAmount = 0.15f,
        saturation = 2f,
        brightness = 0.08f,
        bgColorSaturation = 0f,
        bgColorBrightness = 0f,
        innerColorWhite = 0.15f,
        innerColorMix = 0.4f,
        burn = 0f,
        glassColor = Color.White,
        glassColorMix = 0.1f,
        colorPow = 1.2f,
        alpha = 1f,
        unshade = 0f,
        usePowGate = true,
        // ── 方向光 ───────────────────────────────────────────────────
        lightDirection = GlassLightDirection,
        lightAngleRange = 1f,
        lightIntensity = 1.6f,
        lightOppositeIntensity = 0.8f,
        edgeHighlight = true,
        // ── 底边提亮 ─────────────────────────────────────────────────
        innerBottom = 0.05f,
    )
}

/** 悬浮小控件材质 */
object BrightTone : GlassTone {
    override fun apply(style: SoftGlassStyle): SoftGlassStyle = style.copy(
        // ── 几何（px） ───────────────────────────────────────────────
        edgeWidth = 60f,
        edgePow = 4f,
        thickness = 96f,
        reflectOffset = 600f,
        // ── 折射与反射 ───────────────────────────────────────────────
        refractionScale = 1f,
        refractIOR = 3f,
        reflectionStrength = 0.8f,
        reflectionLighten = 1f,
        // ── 压暗与调色 ───────────────────────────────────────────────
        darker = 0.08f,
        darkerColor = Color.Black,
        darkerRange = 0.6f to 1.0f,
        luminanceValues = listOf(0f, 2f, 0.5f, 0.8f),
        luminanceAmount = 0.15f,
        saturation = 2f,
        brightness = 0.08f,
        bgColorSaturation = 0f,
        bgColorBrightness = 0f,
        innerColorWhite = 0.15f,
        innerColorMix = 0.4f,
        burn = 0f,
        glassColor = Color.White,
        glassColorMix = 0.1f,
        colorPow = 1.2f,
        alpha = 1f,
        unshade = 0f,
        usePowGate = true,
        // ── 方向光 ───────────────────────────────────────────────────
        lightDirection = GlassLightDirection,
        lightAngleRange = 1f,
        lightIntensity = 1.6f,
        lightOppositeIntensity = 0.8f,
        edgeHighlight = true,
        // ── 底边提亮 ─────────────────────────────────────────────────
        innerBottom = 0.05f,
    )
}

/** 悬浮大控件材质 */
object BrightLargeTone : GlassTone {
    override fun apply(style: SoftGlassStyle): SoftGlassStyle = style.copy(
        // ── 几何（px） ───────────────────────────────────────────────
        edgeWidth = 75f,
        edgePow = 4f,
        thickness = 120f,
        reflectOffset = 600f,
        // ── 折射与反射 ───────────────────────────────────────────────
        refractionScale = 1f,
        refractIOR = 3f,
        reflectionStrength = 0.8f,
        reflectionLighten = 1f,
        // ── 压暗与调色 ───────────────────────────────────────────────
        darker = 0.08f,
        darkerColor = Color.Black,
        darkerRange = 0.6f to 1.0f,
        luminanceValues = listOf(0f, 2f, 0.5f, 0.8f),
        luminanceAmount = 0.15f,
        saturation = 2f,
        brightness = 0.08f,
        bgColorSaturation = 0f,
        bgColorBrightness = 0f,
        innerColorWhite = 0.15f,
        innerColorMix = 0.4f,
        burn = 0f,
        glassColor = Color.White,
        glassColorMix = 0.1f,
        colorPow = 1.2f,
        alpha = 1f,
        unshade = 0f,
        usePowGate = true,
        // ── 方向光 ───────────────────────────────────────────────────
        lightDirection = GlassLightDirection,
        lightAngleRange = 1f,
        lightIntensity = 1.6f,
        lightOppositeIntensity = 0.8f,
        edgeHighlight = true,
        // ── 底边提亮 ─────────────────────────────────────────────────
        innerBottom = 0.05f,
    )
}
