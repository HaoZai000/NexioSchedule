// Copyright (C) 2026 Haooz · AGPL-3.0-only
//
// 柔光玻璃的绘制入口与 uniform 灌装。
//
// 采样源由 [SoftGlassBackdrop] 提供；shader 程序按「尺寸 + 圆角」共享，
// 每帧仅更新位置与纹理映射，样式或采样源变化时才重灌全量 uniform。

package com.haooz.chedule.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

// ── shader 程序缓存 ──────────────────────────────────────────────────

/**
 * 缓存键：仅包含决定渲染目标的量（面板尺寸与圆角）。
 *
 * 样式与采样源均为 uniform，不参与缓存键；将其纳入键会在每次参数变化时重新编译 shader。
 */
private data class SoftGlassProgramKey(
    val width: Int,
    val height: Int,
    val radiusTenths: Int,
)

/** 一次 uniform 灌装对应的配置；与缓存槽记录比对，不一致时才重灌。 */
private data class SoftGlassProgramConfig(
    val style: SoftGlassStyle,
    val backdrop: SoftGlassBackdrop,
    /** 模糊金字塔的相邻两级；模糊量变化时仅跨级才需要重灌。 */
    val levelA: Int,
    val levelB: Int,
)

/** 已编译的 shader 程序及其当前装载的配置。 */
private class SoftGlassProgramSlot(val program: SoftGlassProgram) {
    var config: SoftGlassProgramConfig? = null
}

/**
 * 同键面板共享一份 shader 程序，避免逐面板编译 AGSL（编译开销集中在首帧）。
 * 缓存键只含尺寸与圆角，因此程序数量保持在个位数，样式变化不会触发重新编译。
 */
private object SoftGlassProgramCache {
    private const val MAX_ENTRIES = 32
    private val cache = LinkedHashMap<SoftGlassProgramKey, SoftGlassProgramSlot>()

    fun get(key: SoftGlassProgramKey, create: () -> SoftGlassProgramSlot): SoftGlassProgramSlot {
        cache[key]?.let { return it }
        val slot = create()
        if (cache.size >= MAX_ENTRIES) {
            val oldest = cache.keys.firstOrNull()
            if (oldest != null) cache.remove(oldest)
        }
        cache[key] = slot
        return slot
    }
}

/**
 * 为单个面板绘制柔光玻璃，以 Modifier 形式提供，可直接接入既有容器。
 *
 * 构造时取得共享的 [SoftGlassProgram] 并将样式、采样源灌为 uniform；
 * 逐帧仅更新 `uOrigin` 与 `uTexMap`，滚动、翻页与纹理平移都只改变这两个值。
 *
 * @param backdrop 模糊金字塔采样源；为 null 或 shader 不可用时回退为半透明表面。
 * @param styleProvider 在绘制期求值的样式。使用 provider 使参数变化只触发重绘，
 *   不重建 modifier、不触发页面重组。
 * @param density 用于把 [SoftGlassStyle.cornerRadius] 换算为像素。
 * @param origin 面板左上角的窗口坐标，绘制期求值。
 * @param texMapProvider 屏幕坐标到壁纸纹理坐标的仿射 `(kx, ky, mx, my)`，
 *   见 [wallpaperTexMap]。纹理平移时每帧变化，因此在绘制期读取。
 * @param circle 为 true 时使用短边一半作为圆角（胶囊 / 正圆）。
 * @param radiusPx 圆角（px）；为 null 时取 `styleProvider().cornerRadius`，[circle] 为 true 时忽略。
 * @param redraw 绘制期读取一次的位置信号。祖先图层仅平移而不重绘子树时，
 *   在此读取滚动 / 翻页状态可让快照观察按帧失效。
 */
fun Modifier.softGlass(
    backdrop: SoftGlassBackdrop?,
    styleProvider: () -> SoftGlassStyle,
    density: Density,
    origin: () -> Offset,
    texMapProvider: () -> FloatArray,
    circle: Boolean = false,
    radiusPx: Float? = null,
    redraw: () -> Unit = {},
): Modifier = drawWithCache {
    val sizePx = IntSize(size.width.roundToInt(), size.height.roundToInt())
    // SDF 要求 r ≤ min(w,h)/2，否则会解出负距离并产生尖角；此处统一钳制，
    // 口径与 ContinuousRoundedRectangle 一致。角部可见轮廓由 G2 Path 裁剪，
    // shader 仅用该半径计算倒角带。
    val rawRadius = when {
        circle -> minOf(size.width, size.height) * 0.5f
        radiusPx != null -> radiusPx
        // 仅在未显式传入圆角时读取样式，避免常态路径订阅快照状态
        else -> styleProvider().cornerRadius.value * density.density
    }
    val radius = rawRadius.coerceIn(0f, minOf(size.width, size.height) * 0.5f)
    // 直接灌名义半径，不做内缩。可见轮廓由 G2 Path 裁剪，shader 的圆弧只用于光学距离：
    // 圆弧恒比 G2 更方，玻璃铺满整个形状，多出部分被裁掉，角上不会露缝。
    val glassRadius = radius
    val keyBase = if (backdrop != null && sizePx.width > 0) {
        SoftGlassProgramKey(
            width = sizePx.width,
            height = sizePx.height,
            radiusTenths = (glassRadius * 10f).roundToInt(),
        )
    } else null
    // shader 不可用或编译失败时回退为半透明表面，避免影响上层内容。
    val bodySlot = if (keyBase != null && backdrop != null) {
        runCatching {
            SoftGlassProgramCache.get(keyBase) {
                SoftGlassProgramSlot(SoftGlassProgram(SOFT_GLASS_SHADER))
            }
        }.getOrNull()
    } else null
    val surfaceBackdrop = backdrop

    onDrawBehind {
        // 先读取滚动 / 翻页信号：在 draw 期读取快照状态才会建立订阅，使滚动逐帧失效。
        redraw()

        if (bodySlot == null || surfaceBackdrop == null) {
            drawRect(Color.White.copy(alpha = 0.14f))
            return@onDrawBehind
        }
        // 样式与映射在绘制期读取：参数变化只使当前帧的绘制失效，不重建 modifier / 不重组页面
        val style = styleProvider()
        val texMap = texMapProvider()
        // 模糊半径（屏幕 px，σ）→ 第 0 级 texel → 金字塔相邻两级与权重。
        // 级别离散：仅在跨级时重灌一次，其余每帧只更新 uBlendT。
        val levels = surfaceBackdrop.levelPair(style.smallBlurRadius * texMap[0])
        val config = SoftGlassProgramConfig(
            style = style,
            backdrop = surfaceBackdrop,
            levelA = levels.a,
            levelB = levels.b,
        )
        val o = origin()

        /**
         * 配置变化（参数、开关、采样源）时在绘制期就地重灌 uniform。
         *
         * shader 实例按尺寸缓存并跨面板共享，uniform 属于该实例自身的状态；
         * 在 build 期灌装会使配置不同的面板互相覆盖，而将样式纳入缓存键
         * 会导致每次参数变化都重新编译 AGSL。
         */
        if (bodySlot.config != config) {
            val ok = runCatching {
                bodySlot.program.applyBodyUniforms(
                    backdrop = surfaceBackdrop,
                    style = style,
                    panelSize = sizePx,
                    radiusPx = glassRadius,
                    levelA = levels.a,
                    levelB = levels.b,
                )
            }.isSuccess
            if (!ok) {
                drawRect(Color.White.copy(alpha = 0.14f))
                return@onDrawBehind
            }
            bodySlot.config = config
        }
        // 以下 uniform 每帧写入：滚动 / 翻页只改 uOrigin，纹理平移只改 uTexMap。
        bodySlot.program.setFloat2("uOrigin", o.x, o.y)
        bodySlot.program.setFloat4("uTexMap", texMap[0], texMap[1], texMap[2], texMap[3])
        bodySlot.program.setFloat("uBlendT", levels.t)
        drawIntoCanvas { canvas ->
            bodySlot.program.draw(canvas, size.width, size.height, SoftGlassBlend.NORMAL)
        }
    }
}

// ── uniform 灌装 ─────────────────────────────────────────────────────

/**
 * 参考实现 `libhwui.so` 的 `smooth5Map`：把 `t` 映射到 0.5..1 后作五次平滑，再拉回 0..1。
 * [SOFT_GLASS_SHADER] 中存在同名实现，两处必须保持一致。
 */
private fun smooth5Map(t0: Float): Float {
    var t = 0.5f + 0.5f * t0
    t = t.coerceIn(0f, 1f)
    t = t * t * t * (t * (t * 6f - 15f) + 10f)
    return (t - 0.5f) * 2f
}

/** 参考实现 `smooth5_vertical(x, 0.5)`：先开方再作五次平滑，而非单次 quintic。 */
internal fun smooth5Vertical(x: Float): Float =
    smooth5Map(kotlin.math.sqrt(x.coerceIn(0f, 1f)))

/** 灌装几何与方向光相关 uniform。 */
private fun SoftGlassProgram.applyGeometryUniforms(
    panelSize: IntSize,
    radiusPx: Float,
    style: SoftGlassStyle,
) {
    setFloat2("uSize", panelSize.width.toFloat(), panelSize.height.toFloat())
    // 只灌名义半径。形状由 G2 Path 裁剪，shader 的圆弧 SDF 仅用于倒角带与法线计算。
    setFloat4("uCornerRadii", radiusPx, radiusPx, radiusPx, radiusPx)

    val edgePx = style.edgeWidth  // 单位 px，不乘 density
    setFloat("uEdgePx", edgePx)
    setFloat("uInvEdgePow", 1f / style.edgePow.coerceAtLeast(0.05f))

    val ld = style.lightDirection
    setFloat3("uLightDir", ld.first, ld.second, ld.third)
    // 方向光系数 K = 0.5 / sqrt(1 - cos(angleRange * π * 0.5))
    val cosA = kotlin.math.cos(style.lightAngleRange * kotlin.math.PI.toFloat() * 0.5f)
    val k = 0.5f / kotlin.math.sqrt((1f - cosA).coerceAtLeast(1e-6f))
    setFloat("uAngleK", k)
    setFloat("uLightIntensity", style.lightIntensity)
    setFloat("uLightOpposite", style.lightOppositeIntensity)
    setFloat("uColorPow", style.colorPow)
    setFloat("uAlpha", style.alpha)
}

/**
 * 绑定采样源：模糊金字塔的相邻两级与最粗一级（大模糊底色）。
 *
 * 仅在跨级时重新绑定纹理（`setSampler` 会新建 BitmapShader）；
 * 同一档位内只更新 `uBlendT`，因此连续调整模糊量几乎没有额外开销。
 */
private fun SoftGlassProgram.applySamplingUniforms(
    backdrop: SoftGlassBackdrop,
    levelA: Int,
    levelB: Int,
) {
    val levels = backdrop.levels
    val baseWidth = levels[0].width.toFloat().coerceAtLeast(1f)
    val a = levels[levelA.coerceIn(0, levels.lastIndex)]
    val b = levels[levelB.coerceIn(0, levels.lastIndex)]
    val coarse = levels.last()

    setSampler("uWallpaperA", a)
    setSampler("uWallpaperB", b)
    setSampler("uWallpaperCoarse", coarse)

    setFloat2("uTexSizeA", a.width.toFloat(), a.height.toFloat())
    setFloat2("uTexSizeB", b.width.toFloat(), b.height.toFloat())
    setFloat2("uTexSizeCoarse", coarse.width.toFloat(), coarse.height.toFloat())
    // 第 i 级 texel 为第 0 级的 1/2^i；按实际宽度比计算，纹理较窄时仍能正确对齐
    setFloat("uLevelScaleA", a.width / baseWidth)
    setFloat("uLevelScaleB", b.width / baseWidth)
    setFloat("uLevelScaleCoarse", coarse.width / baseWidth)
}

/** 灌装主体 shader 的全部 uniform。 */
private fun SoftGlassProgram.applyBodyUniforms(
    backdrop: SoftGlassBackdrop,
    style: SoftGlassStyle,
    panelSize: IntSize,
    radiusPx: Float,
    levelA: Int,
    levelB: Int,
) {
    applyGeometryUniforms(panelSize, radiusPx, style)
    applySamplingUniforms(backdrop, levelA, levelB)

    val refractionScale = style.refractionScale.coerceAtLeast(0f)
    setFloat("uEdgePow", style.edgePow.coerceAtLeast(0.05f))
    setFloat("uThicknessPx", style.thickness * refractionScale)
    // 反射偏移各档位固定，仅在关闭折射时归零，见 [SoftGlassStyle.reflectOffset]
    setFloat("uReflectOffsetPx", if (refractionScale > 0f) style.reflectOffset else 0f)
    setFloat("uIOR", style.refractIOR)
    setFloat("uReflStrength", style.reflectionStrength)
    setFloat("uReflLighten", style.reflectionLighten)
    setFloat("uInnerBottom", style.innerBottom)
    setFloat("uEdgeHighlight", if (style.edgeHighlight) 1f else 0f)
    setFloat("uLightFalloff", style.edgeLightFalloff.coerceIn(0f, 1f))
    setFloat("uUnshade", style.unshade)
    setFloat("uUsePowGate", if (style.usePowGate) 1f else 0f)
    // 抗锯齿阈值：参考实现为 smooth5_vertical(3/edge, 0.5) = smooth5Map(√(3/edge))。
    // 直接传 3/edge 会使 alpha 在 0.2px 内升到 1，边缘重新变硬。
    val edgePx = style.edgeWidth.coerceAtLeast(1f)
    setFloat("uNmlZAAThreshold", smooth5Vertical(3f / edgePx))
    setFloat("uHit", if (style.hit) 1f else 0f)
    val gc = style.glassColor
    setFloat4("uGlassColor", gc.red, gc.green, gc.blue, style.glassColorMix)
    setFloat("uDebug", style.debugView.toFloat())

    // 颜色处理 uniform：逐像素在 shader 内计算
    setFloat("uDarker", style.darker)
    val dc = style.darkerColor
    setFloat3("uDarkerColor", dc.red, dc.green, dc.blue)
    setFloat2("uDarkerRange", style.darkerRange.first, style.darkerRange.second)
    val lc = style.luminanceValues
    setFloat4(
        "uLuminance",
        lc.getOrElse(0) { 0f },
        lc.getOrElse(1) { 0f },
        lc.getOrElse(2) { 0f },
        lc.getOrElse(3) { 1f },
    )
    setFloat("uLuminanceAmount", style.luminanceAmount)
    setFloat("uSaturation", style.saturation)
    setFloat("uBrightness", style.brightness)
    setFloat("uBgSat", style.bgColorSaturation)
    setFloat("uBgBri", style.bgColorBrightness)
    setFloat("uInnerWhite", style.innerColorWhite)
    setFloat("uInnerMix", style.innerColorMix)
    setFloat("uBurn", style.burn)
}
