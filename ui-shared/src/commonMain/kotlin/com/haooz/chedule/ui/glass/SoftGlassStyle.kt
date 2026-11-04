// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 柔光玻璃参数。字段名 = `libhwui.so` 里那套 uniform 名。
 *
 * 分两组：
 *  - 几何 / 光照 / 折射（主体与描边共用）
 *  - 颜色处理（`processGlassColor`，实现在主体 shader 的 `tintAt` 里）
 *
 * 数值取自 HyperOS 主题数组；部分字段与 uniform 的对应关系是推断的，改动时按可调参数处理。
 */
data class SoftGlassStyle(
    // ── 几何 ──
    val cornerRadius: Dp = 28.dp,
    /** uShapeEdgePx：倒角带宽度。主题 Medium 档给 36px；按 56dp 控件缩到 5dp，否则边缘糊成一片。 */
    val edgeWidth: Dp = 5.dp,
    /** uShapeEdgePow。仅在 [slopeMode] = 0 时生效。 */
    val edgePow: Float = 1f,
    /**
     * 主体倒角剖面。`0` = 框架原式（`d→1` 处斜率无穷，平面端会折出硬棱）；
     * `1` = S 形曲线（两端曲率连续）。描边固定用原式，不跟这个开关。
     */
    val slopeMode: Int = 1,
    /** 折射厚度：决定采样位移幅度；与 [edgeWidth] 的比值决定边缘压缩率。 */
    val thickness: Dp = 26.dp,
    /** 反射采样偏移。 */
    val reflectOffset: Dp = 8.dp,

    // ── 折射 / 反射 ──
    /** 折射倍率：0.8 = 较弱，1.0 = 默认，1.2 = 较强；由外观的折射档位统一决定。 */
    val refractionScale: Float = 1f,
    /** uRefractIOR。 */
    val refractIOR: Float = 1.6f,
    /** uReflectionStrength。 */
    val reflectionStrength: Float = 0.30f,
    /** uReflectionLighten。 */
    val reflectionLighten: Float = 0.10f,
    /** uColorPow。 */
    val colorPow: Float = 1f,
    /** uGlassColor（rgb）+ .w 混入比例。 */
    val glassColor: Color = Color.White,
    val glassColorMix: Float = 0f,
    /** 整体不透明度；背景自己模糊，取 1。 */
    val alpha: Float = 1f,
    /** uHit。 */
    val hit: Boolean = false,
    /** uInnerBottom：底边提亮。 */
    val innerBottom: Float = 0f,
    /** uUnshade：1 = 直接输出 glassColor。 */
    val unshade: Float = 0f,
    /** uUsePowGate。 */
    val usePowGate: Boolean = true,

    // ── 方向光 ──
    /** uDirectionalLightDirection（主题原值）。 */
    val lightDirection: Triple<Float, Float, Float> = Triple(-0.4f, 0.6f, -0.8f),
    /** uDirectionalLightAngleRange。 */
    val lightAngleRange: Float = 1.4f,
    /** uDirectionalLightIntensity。 */
    val lightIntensity: Float = 0.85f,
    /** uDirectionalLightOppositeIntensity。 */
    val lightOppositeIntensity: Float = 0.45f,

    // ── 颜色处理（processGlassColor，在主体 shader 的 tintAt 里逐像素算）──
    /** uBgColorSaturation。 */
    val bgColorSaturation: Float = 0.8f,
    /** uBgColorBrightness（加性）。 */
    val bgColorBrightness: Float = 0.02f,
    /** uLuminanceCoeffs（三次曲线系数）。 */
    val luminanceValues: List<Float> = listOf(0.9f, 0.9f, 0.9f, 1f),
    /** uLuminanceAmount。 */
    val luminanceAmount: Float = 0f,
    /** uLuminanceCurveMode：0 = 混灰，1 = 比例。 */
    val luminanceCurveMode: Int = 1,
    /** uSaturation。 */
    val saturation: Float = 1f,
    /**
     * uBrightness（加性，作用在折射内容 `col` 上）。
     *
     * 加性 ⇒ 按相对量提亮：0.05 → 0.09 是 +80%，0.95 → 0.99 只有 +4%。
     * 所以它干的是「暗处提一点」，亮处几乎不动（还会被后续 clamp 吃掉）。
     * 因为是逐像素算的，玻璃叠在暗背景上的那一块才抬，是局部自适应。
     */
    val brightness: Float = 0.04f,
    /** uInnerColorWhite：内部往白抬。 */
    val innerColorWhite: Float = 0.02f,
    /** uInnerColorMix。 */
    val innerColorMix: Float = 0.06f,
    /** uBurn：亮度曲线拐点。 */
    val burn: Float = 1.4f,

    /** 描边宽度（框架是环几何，这里用带内遮罩顶替）。 */
    val rimWidth: Dp = edgeWidth * 0.6f,
    /** 描边饱和度（1 = 不变，>1 更艳）。 */
    val rimSaturation: Float = 1.2f,
    /** 描边提亮倍率（>1 更亮）。 */
    val rimBrightness: Float = 1f,

    // ── 模糊 ──
    /** 轻模糊：折射采样源，半径越大采样越糊。 */
    val smallBlurRadius: Dp = 4.dp,
    /** 重模糊：喂描边的 uBgColorTex。 */
    val bigBlurRadius: Dp = 24.dp,

    /**
     * 调试视图。0 = 正常。
     *
     * - 1 = 把 `d`（到边界的归一化深度）直接画成灰度，边缘带 0→1 由黑到白，内部纯白。
     *   用来量那条线到底落在 `d` 的哪个值。
     * - 2 = 分支涂色：边缘带（走折射）红、内部（inner）绿。
     * - 3 = 直接输出 `uRefractTex` 原图，不折射不染色。用来验证模糊有没有真的进到 shader。
     */
    val debugView: Int = 0,
) {
    /** 按背景平均亮度在暗 / 亮两档之间插值（`meanLuma` 0..1）。 */
    fun adapted(meanLuma: Float): SoftGlassStyle {
        // 0 = 深色背景（dark 档），1 = 浅色背景（lightWallpaper 档）
        val t = ((meanLuma - 0.35f) / 0.25f).coerceIn(0f, 1f)
        return copy(
            lightAngleRange = 2.4f + (1.4f - 2.4f) * t,
            lightIntensity = 1.6f + (0.8f - 1.6f) * t,
        )
    }
}

/** 课表页：edgeWidth 5dp；折射 / 模糊共用外观设置。 */
val ScheduleSoftGlassStyle = SoftGlassStyle(
    edgeWidth = 5.dp,
    smallBlurRadius = 4.dp,
)

/** 今日页：edgeWidth 6dp；折射 / 模糊共用外观设置，仅圆角带比课表宽。 */
val TodaySoftGlassStyle = SoftGlassStyle(
    edgeWidth = 6.dp,
    smallBlurRadius = 4.dp,
)
