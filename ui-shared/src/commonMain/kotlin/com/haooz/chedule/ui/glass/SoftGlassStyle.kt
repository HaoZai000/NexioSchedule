// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 柔光玻璃参数。字段名与 `libhwui.so` 的 uniform 名一致。
 *
 * 默认值取自 `miuix.jar` 的 `GlassToken` 档位 **`Glass_Common_Large_Regular`**
 * （大面板档，课程卡与今日页卡片都属这一档）。
 *
 * 不要退回 `Small_Regular`：那档 `angleRange = 0.3`、`intensity = 1.1`，方向光是 35 个档位里
 * 最弱的一组，边缘高光只剩约 +5% 亮度，肉眼看不见。
 */
data class SoftGlassStyle(
    // ── 几何 ──
    val cornerRadius: Dp = 28.dp,
    /**
     * uShapeEdgePx：倒角带宽度，**单位 px**（不是 dp），即「边缘看起来多厚」。
     *
     * 60px 是文件内默认值；外观页的「边缘厚度」只在它上面乘 0.8 / 1.0 / 1.2 / 1.5。
     */
    val edgeWidth: Float = 60f,
    /** uShapeEdgePow（Large 档 4.0）。剖面曲线由 shader 里系统的 `curve()` 唯一确定。 */
    val edgePow: Float = 4f,
    /**
     * **已废弃，无任何作用。**
     *
     * 系统剖面固定是 `smooth5_vertical(x, 0.5)` 再 `1-(1-·)^edgePow`，没有 S 形开关。
     * 字段保留只为不让已存配置 / 调用点报错，别在 shader 里再读它。
     */
    val slopeMode: Int = 0,
    /**
     * 折射厚度（px）：决定采样位移幅度，与 [edgeWidth] 的比值决定边缘压缩率。
     *
     * Large 档给 80，这里取 **96**（外观「折射=默认」即此值）。
     */
    val thickness: Float = 96f,
    /** 反射采样偏移（px）。Large 档 600 */
    val reflectOffset: Float = 600f,

    // ── 折射 / 反射 ──
    /** 折射厚度倍率：外观折射档位直接给这里（0 / 0.75 / 1 / 1.25 / 1.5）。 */
    val refractionScale: Float = 1f,
    /** uRefractIOR。Large 档 3.0。 */
    val refractIOR: Float = 3f,
    /** uReflectionStrength。Large 档 0.8。 */
    val reflectionStrength: Float = 0.8f,
    /** uReflectionLighten。Large 档 1.0。 */
    val reflectionLighten: Float = 1f,
    /** uColorPow。Large 档 1.2。 */
    val colorPow: Float = 1.2f,
    /** uGlassColor（rgb）+ .w 混入比例。Large 档 [1,1,1, 0.10]。 */
    val glassColor: Color = Color.White,
    val glassColorMix: Float = 0.1f,
    /** 整体不透明度；背景自己模糊，取 1。 */
    val alpha: Float = 1f,
    /** uHit。 */
    val hit: Boolean = false,
    /** uInnerBottom：底边提亮。Large 档 0.05。 */
    val innerBottom: Float = 0.05f,
    /** uUnshade：1 = 直接输出 glassColor。 */
    val unshade: Float = 0f,
    /** uUsePowGate。 */
    val usePowGate: Boolean = true,

    // ── 方向光 ──
    /** uDirectionalLightDirection。Large 档 `[-0.6, 0.6, -0.8]`。 */
    val lightDirection: Triple<Float, Float, Float> = Triple(-0.6f, 0.6f, -0.8f),
    /**
     * uDirectionalLightAngleRange。Large 档 **1.0**。
     *
     * 「高光描边明不明显」的主控：`uAngleK = 0.5/sqrt(1-cos(range·π/2))`，
     * 1.0 ⇒ K = 0.5（光覆盖约 90° 的倒角），0.3 ⇒ K = 1.5（只剩 27°）。
     */
    val lightAngleRange: Float = 1f,
    /** uDirectionalLightIntensity。Large 档 1.6。 */
    val lightIntensity: Float = 1.6f,
    /** uDirectionalLightOppositeIntensity。Large 档 0.8（右下那道）。 */
    val lightOppositeIntensity: Float = 0.8f,

    // ── 颜色处理（processGlassColor，烘焙阶段逐像素算）──
    /** uBgColorSaturation。Large 档 **0.0**（大面板不做背景提饱和）。 */
    val bgColorSaturation: Float = 0f,
    /** uBgColorBrightness（加性）。Large 档 0.0。 */
    val bgColorBrightness: Float = 0f,
    /** uLuminanceCoeffs（三次曲线系数）。Large 档 [0.0, 2.0, 0.5, 0.8]。 */
    val luminanceValues: List<Float> = listOf(0.0f, 2.0f, 0.5f, 0.8f),
    /** uLuminanceAmount。Large 档 0.15。 */
    val luminanceAmount: Float = 0.15f,
    /** uLuminanceCurveMode：0 = 混灰，1 = 比例。 */
    val luminanceCurveMode: Int = 1,
    /** uSaturation。Large 档 2.2。 */
    val saturation: Float = 2.2f,
    /**
     * uBrightness（加性，作用在折射内容 `col` 上）。Large 档 0.08。
     *
     * 加性 ⇒ 按相对量提亮：0.05 → 0.13 是 +160%，0.95 → 1.03 只有 +8% 且被 clamp 吃掉。
     * 效果是「暗处抬一点、亮处几乎不动」，逐像素算 ⇒ 玻璃压在暗背景上的那块才亮，属局部自适应。
     */
    val brightness: Float = 0.08f,
    /** uInnerColorWhite：内部往白抬。Large 档 0.15。 */
    val innerColorWhite: Float = 0.15f,
    /** uInnerColorMix。Large 档 0.4。 */
    val innerColorMix: Float = 0.4f,
    /** uBurn：亮度曲线拐点。Large 档 0.0。 */
    val burn: Float = 0f,
    /** uDarker：往深紫黑混合的强度（系统 `Blend.darker`）。Large 档 0.2。 */
    val darker: Float = 0.2f,
    /** uDarkerRange：混合作用区间（系统 `Blend.darkerRange`）。Large 档 `[0.6, 1.0]`。 */
    val darkerRange: Pair<Float, Float> = 0.6f to 1.0f,

    // ── 按压光效（系统 `Mask`，静态渲染不参与，仅保留字段以便 1:1 对齐）──
    val pressLightCenterFall: Float = 0f,
    val pressLightCenterStrength: Float = 0f,
    val pressLightCenterPeak: Float = 0f,
    val pressRingStrength: List<Float> = listOf(0f, 0f),

    /**
     * **高光描边**：只切换倒角带上的方向光（`processLighting`）。
     * 开 = 边上有方向感（左上亮、右下暗、沿边起伏）；关 = 边变成均匀的一圈
     */
    val edgeHighlight: Boolean = true,

    /**
     * 是否额外叠一圈独立描边 shader（LIGHTEN 合成）。
     *
     * **系统没有这一层**：libhwui 的 glass shader 里没有 `rimWidth` / `uRimSaturation` /
     * `uRimBrightness`，边缘高光全部由 `processLighting` 在倒角带上自然产生。
     * 开着会比系统更硬更亮，边缘还会出现「卡中卡」。
     */
    val rimEnabled: Boolean = false,
    /** 描边宽度（px；框架是环几何，这里用带内遮罩顶替）。 */
    val rimWidth: Float = edgeWidth * 0.6f,
    /** 描边饱和度（1 = 不变，>1 更艳）。 */
    val rimSaturation: Float = 1.2f,
    /** 描边提亮倍率（>1 更亮）。 */
    val rimBrightness: Float = 1f,

    // ── 模糊 ──
    /** 轻模糊：折射采样源（px）。系统 Large 档 80px；外观默认取 32px（见 `AppearanceConfig`）。 */
    val smallBlurRadius: Float = 32f,
    /** 重模糊：喂描边的 uBgColorTex（px）。Large 档 500px。 */
    val bigBlurRadius: Float = 500f,

    /**
     * 调试视图。0 = 正常；1 = 归一化离边距离 `x` 画成灰度；2 = 分支着色（折射带红 / 内部绿）；
     * 3 = 直接输出 `uRefractTex`，用于验证模糊是否真的进了 shader。
     */
    val debugView: Int = 0,
) {
    /**
     * 恒等返回：系统照明按固定 token 档位，不随壁纸平均亮度插值。
     *
     * 不要恢复成按 `meanLuma` 拉 `lightAngleRange` 的做法 —— 那会把 `uAngleK` 从 1.5 降到 0.4，
     * 方向光从「只打左上一小段」散成「整圈都亮」，整个折射区过亮。
     */
    @Suppress("UNUSED_PARAMETER")
    fun adapted(meanLuma: Float): SoftGlassStyle = this
}

/** 课表页：与 `Glass_Common_Large_Regular` 1:1；模糊由外观设置覆盖（dp → px 在调用处换算）。 */
val ScheduleSoftGlassStyle = SoftGlassStyle()

/** 今日页：同一档（系统按尺寸选档，今日页卡片与课表卡尺寸同级）。 */
val TodaySoftGlassStyle = SoftGlassStyle()
