// Copyright (C) 2026 Haooz · AGPL-3.0-only
//
// 柔光玻璃的样式参数与材质策略入口。
//
// 字段命名与参考实现（HyperOS `mi_glass_sdf` / `libhwui.so`）的 shader uniform 一一对应；
// 默认值取自大面板档位 `Glass_Common_Large_Regular`。几何量统一以像素为单位。

package com.haooz.chedule.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 柔光玻璃的全部可调参数。
 *
 * 默认值对应参考实现的大面板档位 `Glass_Common_Large_Regular`。方向光取该档位的
 * `lightAngleRange = 1.0` / `lightIntensity = 1.6`；较小面板档位的方向光强度不足以形成
 * 可见的边缘高光，因此不作为默认值。
 *
 * 字段按用途分组：几何、折射与反射、方向光、颜色处理、按压光效、模糊、调试。
 * 几何量单位为像素，不随 density 变化。
 */
data class SoftGlassStyle(
    // ── 几何 ─────────────────────────────────────────────────────────
    val cornerRadius: Dp = 28.dp,
    /**
     * 倒角带宽度，单位为像素（非 dp），对应 uniform `uShapeEdgePx`。
     * 该值直接作为最终值使用，不乘 density。
     */
    val edgeWidth: Float = 60f,
    /** 倒角剖面指数，对应 `uShapeEdgePow`（大面板档位 4.0）。剖面曲线由 shader 唯一确定。 */
    val edgePow: Float = 4f,
    /**
     * 兼容字段，不参与渲染。
     *
     * 参考实现的剖面固定为 `smooth5_vertical(x, 0.5)` 后接 `1-(1-·)^edgePow`，
     * 没有 S 形开关；保留该字段以兼容已保存的配置与既有调用点。
     */
    val slopeMode: Int = 0,
    /**
     * 折射厚度（px），决定采样位移幅度；与 [edgeWidth] 的比值决定边缘压缩率。
     * 大面板档位为 80，此处取 96。
     */
    val thickness: Float = 96f,
    /** 反射采样偏移（px），大面板档位 600。 */
    val reflectOffset: Float = 600f,

    // ── 折射与反射 ───────────────────────────────────────────────────
    /** 折射倍率，0 表示关闭折射。 */
    val refractionScale: Float = 1f,
    /** 折射率，对应 `uRefractIOR`（大面板档位 3.0）。 */
    val refractIOR: Float = 3f,
    /** 反射混入强度，对应 `uReflectionStrength`（大面板档位 0.8）。 */
    val reflectionStrength: Float = 0.8f,
    /** 反射提亮量，对应 `uReflectionLighten`（大面板档位 1.0）。 */
    val reflectionLighten: Float = 1f,
    /** 末尾 gamma，对应 `uColorPow`（大面板档位 1.2）。 */
    val colorPow: Float = 1.2f,
    /** 玻璃本色的 rgb，大面板档位为白色。 */
    val glassColor: Color = Color.White,
    /** 玻璃本色混入 gcr 的比例，对应 `uGlassColor.w`（大面板档位 0.10）。 */
    val glassColorMix: Float = 0.1f,
    /** 填充策略：决定配色与混合强度，见 [GlassFill]。 */
    val fill: GlassFill = NoneFill,
    /** 材质基调：决定是否压暗亮部，见 [GlassTone]。 */
    val tone: GlassTone = TokenTone,
    /** 整体不透明度；采样源自带模糊，取 1。 */
    val alpha: Float = 1f,
    /** 对应 uniform `uHit` 的兼容字段。 */
    val hit: Boolean = false,
    /** 底边提亮量，对应 `uInnerBottom`（大面板档位 0.05）。 */
    val innerBottom: Float = 0.05f,
    /** 直出玻璃本色的比例，对应 `uUnshade`；1 表示完全输出 [glassColor]。 */
    val unshade: Float = 0f,
    /** 是否执行末尾 gamma，对应 `uUsePowGate`。 */
    val usePowGate: Boolean = true,

    // ── 方向光 ───────────────────────────────────────────────────────
    /** 方向光方向向量，对应 `uDirectionalLightDirection`（大面板档位 `[-0.6, 0.6, -0.8]`）。 */
    val lightDirection: Triple<Float, Float, Float> = Triple(-0.6f, 0.6f, -0.8f),
    /**
     * 方向光角度范围，对应 `uDirectionalLightAngleRange`（大面板档位 1.0）。
     *
     * 该值决定边缘高光的覆盖范围：`uAngleK = 0.5/sqrt(1-cos(range·π/2))`。
     * 1.0 对应 K = 0.5（覆盖约 90° 的倒角），0.3 对应 K = 1.5（约 27°）。
     */
    val lightAngleRange: Float = 1f,
    /** 主方向光强度，对应 `uDirectionalLightIntensity`（大面板档位 1.6）。 */
    val lightIntensity: Float = 1.6f,
    /** 对侧方向光强度，对应 `uDirectionalLightOppositeIntensity`（大面板档位 0.8）。 */
    val lightOppositeIntensity: Float = 0.8f,

    // ── 颜色处理 ─────────────────────────────────────────────────────
    /** 大模糊底色的饱和度，对应 `uBgColorSaturation`；大面板档位为 0。 */
    val bgColorSaturation: Float = 0f,
    /** 大模糊底色的加性提亮，对应 `uBgColorBrightness`；大面板档位为 0。 */
    val bgColorBrightness: Float = 0f,
    /** 亮度曲线的三次系数，对应 `uLuminanceCoeffs`（大面板档位 `[0.0, 2.0, 0.5, 0.8]`）。 */
    val luminanceValues: List<Float> = listOf(0.0f, 2.0f, 0.5f, 0.8f),
    /** 亮度曲线的混合量，对应 `uLuminanceAmount`（大面板档位 0.15）。 */
    val luminanceAmount: Float = 0.15f,
    /** 亮度曲线模式：0 = 混灰，1 = 比例。 */
    val luminanceCurveMode: Int = 1,
    /** 饱和度，对应 `uSaturation`（大面板档位 2.2）。 */
    val saturation: Float = 2.0f,
    /**
     * 加性提亮量，作用于折射内容，对应 `uBrightness`（大面板档位 0.08）。
     *
     * 加法运算使暗部抬升明显（0.05 → 0.13，+160%），亮部变化很小且被 clamp 吸收
     * （0.95 → 1.03，+8%），形成逐像素的局部自适应。
     */
    val brightness: Float = 0.08f,
    /** 内部底色往白抬升的比例，对应 `uInnerColorWhite`（大面板档位 0.15）。 */
    val innerColorWhite: Float = 0.15f,
    /** 内部底色混入比例，对应 `uInnerColorMix`（大面板档位 0.4）。 */
    val innerColorMix: Float = 0.4f,
    /** 亮度曲线拐点，对应 `uBurn`（大面板档位 0.0）。 */
    val burn: Float = 0f,
    /** 亮部压暗强度，对应 `uDarker`（大面板档位 0.2）。 */
    val darker: Float = 0.2f,
    /**
     * 亮部压暗所混向的颜色。参考实现硬编码为深紫黑 (0.0787, 0.0285, 0.0928)，
     * 其 R/B 分量高于 G，压暗后会带紫色；传 `Color.Black` 可得到中性压暗。
     */
    val darkerColor: Color = Color(0.07874f, 0.02848f, 0.09278f),
    /** 压暗作用区间，对应 `uDarkerRange`（大面板档位 `[0.6, 1.0]`）。 */
    val darkerRange: Pair<Float, Float> = 0.6f to 1.0f,

    // ── 按压光效 · 静态渲染不使用，保留以对齐 uniform 清单 ───────────
    val pressLightCenterFall: Float = 0f,
    val pressLightCenterStrength: Float = 0f,
    val pressLightCenterPeak: Float = 0f,
    val pressRingStrength: List<Float> = listOf(0f, 0f),

    /** 高光描边开关：控制倒角带上的方向光（`processLighting`）。开启时边缘具有方向性明暗变化。 */
    val edgeHighlight: Boolean = true,

    // ── 模糊 ─────────────────────────────────────────────────────────
    /** 折射采样源的轻模糊半径（px）。大面板档位 80，默认 32。 */
    val smallBlurRadius: Float = 32f,
    /** 描边底色 `uBgColorTex` 的重模糊半径（px），大面板档位 500。 */
    val bigBlurRadius: Float = 500f,

    /**
     * 调试视图。0 = 正常；1 = 离边归一化距离 `x` 的灰度；2 = 分支着色（折射带红 / 内部绿）；
     * 3 = 直接输出采样纹理原图（不模糊、不调色），用于核对采样映射。
     */
    val debugView: Int = 0,
) {
    /**
     * 返回原样式：照明参数按固定档位取值，不随采样源平均亮度插值。
     *
     * 保留该入口使调用点可以统一传入 [meanLuma]。按亮度调整 [lightAngleRange]
     * 会使 `uAngleK` 从 1.5 降到 0.4，方向光由局部高光扩散为整圈亮带。
     */
    @Suppress("UNUSED_PARAMETER")
    fun adapted(meanLuma: Float): SoftGlassStyle = this

    /**
     * 套用材质策略，产出交给 shader 的最终样式：先应用 [tone]，再应用 [fill]。
     *
     * 所有调用点统一经由该出口，因此新增基调或填充策略无需修改调用方。
     */
    fun filled(isDark: Boolean): SoftGlassStyle = fill.apply(tone.apply(this), isDark)
}

/** 大面板预设：与 `Glass_Common_Large_Regular` 1:1 对应；模糊半径由调用方按密度换算后覆盖。 */
val ScheduleSoftGlassStyle = SoftGlassStyle()

/** 大面板预设（同上），供与主面板尺寸相近的卡片使用。 */
val TodaySoftGlassStyle = SoftGlassStyle()
