// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/*
 * 柔光玻璃（HyperOS `mi_glass_sdf`）的绘制入口。链路：
 * 1. `rememberSoftGlassSurface` 准备壁纸纹理（降采样 + 缩略图）与「屏幕 → 纹理」仿射；
 * 2. 面板在 `drawWithCache` 里按「尺寸 + 圆角」取共享的 [SoftGlassProgram]（`SoftGlassProgramCache`）；
 * 3. 每帧只更新 `uOrigin` / `uTexMap`；模糊与调色全部由 uniform 驱动，在 shader 内现算。
 *
 * 于是外观页拖模糊、拖壁纸都不触发任何位图重建 —— 这是本文件最硬的约定。
 */

/**
 * program 缓存键：**只放决定渲染目标的量**（面板尺寸 / 圆角）。
 *
 * ⚠ 样式与采样源**不能进键**：它们全都只是 uniform，改一次重灌一次就够。
 * 以前把 style / backdrop 也放进来，外观页每拖一格都会新建一个 `RuntimeShader`
 * —— AGSL 要现场编译，于是拖滑杆、点开关全都卡成幻灯。
 */
private data class SoftGlassProgramKey(
    val width: Int,
    val height: Int,
    val radiusTenths: Int,
)

/** 一次 uniform 灌装对应的配置；与 slot 上记的比对，变了才重灌。 */
private data class SoftGlassProgramConfig(
    val style: SoftGlassStyle,
    val backdrop: SoftGlassBackdrop,
    /** 模糊金字塔的相邻两级；拖动模糊时只在这两个下标之间跳，跨级才重灌。 */
    val levelA: Int,
    val levelB: Int,
)

/** 编译好的 program + 它当前装着哪份配置。 */
private class SoftGlassProgramSlot(val program: SoftGlassProgram) {
    var config: SoftGlassProgramConfig? = null
}

/**
 * 同键卡片共用一份 shader：不共享时一屏 40 张卡要建 80 个 RuntimeShader（AGSL 编译是首帧大头）。
 * 键里只有尺寸 / 圆角，所以程序数量通常是个位数，且样式来回调不会再触发编译。
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
 * 单面板柔光玻璃。做成 Modifier 是为了塞进课程卡现有的 modifier 链（不为玻璃改卡片容器）。
 *
 * 一次构造建一份 [SoftGlassProgram]，把样式 / 采样源灌成 uniform；
 * 逐帧只更新 `uOrigin` 与 `uTexMap` —— 滚动 / 翻周 / 拖壁纸都只是这两个值在变。
 *
 * @param styleProvider 绘制期求值的样式。做成 provider 是为了让外观页拖参数时**只重绘**
 *   （读 State 失效当帧），不重建 modifier、不重组整页。
 * @param origin 面板左上角的**窗口坐标**，绘制期求值。
 * @param texMapProvider 屏幕坐标 → 壁纸纹理坐标的仿射 `(kx, ky, mx, my)`，见
 *   [wallpaperTexMap]。拖壁纸时它每帧都变，所以也走绘制期。
 * @param radiusPx 圆角（px）。null = 用 `styleProvider().cornerRadius`；[circle] 为 true 时忽略。
 * @param redraw 绘制期读一次的「位置信号」：祖先图层只平移不重绘子树，
 *   在这里读滚动 / 翻页状态，快照观察才会按帧失效。
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
    // SDF 要求 r ≤ min(w,h)/2：20dp 高的分界带配 12dp 圆角会解出负距离、被切成尖角。
    // 这里统一钳住，具体形状交给 shader 的 G2 近似。
    val rawRadius = when {
        circle -> minOf(size.width, size.height) * 0.5f
        radiusPx != null -> radiusPx
        // 只有没传圆角时才读样式；读了会订阅 State，所以别放进常态路径
        else -> styleProvider().cornerRadius.value * density.density
    }
    val radius = rawRadius.coerceIn(0f, minOf(size.width, size.height) * 0.5f)
    // 玻璃圆角比卡片 G2 略收：10dp 收 0.9dp，20dp 及以上收 2dp，中间线性。
    val radiusDp = radius / density.density
    val glassInsetDp = (0.9f + (radiusDp - 10f) * 0.11f).coerceIn(0.9f, 2f)
    val glassRadius = (radius - glassInsetDp * density.density).coerceAtLeast(0f)
    val keyBase = if (backdrop != null && sizePx.width > 0) {
        SoftGlassProgramKey(
            width = sizePx.width,
            height = sizePx.height,
            radiusTenths = (glassRadius * 10f).roundToInt(),
        )
    } else null
    // 挂不住就退回半透明白面（onDrawBehind 的兜底分支），不让 shader 编译失败冒到课表上。
    val bodySlot = if (keyBase != null && backdrop != null) {
        runCatching {
            SoftGlassProgramCache.get(keyBase) {
                SoftGlassProgramSlot(SoftGlassProgram(SOFT_GLASS_SHADER))
            }
        }.getOrNull()
    } else null
    val surfaceBackdrop = backdrop

    onDrawBehind {
        // 先读滚动 / 翻页信号：draw 期读 State 才会订阅，滚动时按帧失效。
        redraw()

        if (bodySlot == null || surfaceBackdrop == null) {
            drawRect(Color.White.copy(alpha = 0.14f))
            return@onDrawBehind
        }
        // 样式与映射都在绘制期读：拖参数时只失效这一帧的绘制，不重建 modifier / 不重组页面
        val style = styleProvider()
        val texMap = texMapProvider()
        // 模糊半径（屏幕 px，就是 σ）→ 第 0 级 texel → 金字塔相邻两级 + 权重。
        // 档位是离散的：拖滑杆只在跨级时重灌一次，其余每帧只改 uBlendT。
        val levels = surfaceBackdrop.levelPair(style.smallBlurRadius * texMap[0])
        val config = SoftGlassProgramConfig(
            style = style,
            backdrop = surfaceBackdrop,
            levelA = levels.a,
            levelB = levels.b,
        )
        val o = origin()

        /**
         * 配置变了（拖参数 / 切开关 / 换壁纸）就在**绘制期**就地重灌 uniform。
         *
         * 放在这里而不是 build 期：shader 实例按尺寸缓存、跨面板共享，uniform 是它自己的状态。
         * 若在 build 期灌，两个配置不同的面板（不同页 / 弹窗 vs 卡片）会互相覆盖；
         * 若把 style 塞进缓存键，拖一格就重编译一次 AGSL —— 那才是原来卡顿的根因。
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
        // 这两个每帧都写：滚动 / 翻页只动 uOrigin，拖壁纸只动 uTexMap，都是纯 setFloat。
        bodySlot.program.setFloat2("uOrigin", o.x, o.y)
        bodySlot.program.setFloat4("uTexMap", texMap[0], texMap[1], texMap[2], texMap[3])
        bodySlot.program.setFloat("uBlendT", levels.t)
        drawIntoCanvas { canvas ->
            bodySlot.program.draw(canvas, size.width, size.height, SoftGlassBlend.NORMAL)
        }
    }
}

// ── uniform 灌装 ────────────────────────────────────────────────────

/**
 * 系统 `smooth5Map`（libhwui.so 原文）：把 `t` 先压到 0.5..1 再走五次平滑，最后拉回 0..1。
 * AGSL 里有一份同名的实现（`SOFT_GLASS_SHADER`），两边必须逐位一致。
 */
private fun smooth5Map(t0: Float): Float {
    var t = 0.5f + 0.5f * t0
    t = t.coerceIn(0f, 1f)
    t = t * t * t * (t * (t * 6f - 15f) + 10f)
    return (t - 0.5f) * 2f
}

/** 系统 `smooth5_vertical(x, 0.5)`：**先开方再五次平滑**，不是一次 plain quintic。 */
private fun smooth5Vertical(x: Float): Float =
    smooth5Map(kotlin.math.sqrt(x.coerceIn(0f, 1f)))

private fun SoftGlassProgram.applyGeometryUniforms(
    panelSize: IntSize,
    radiusPx: Float,
    style: SoftGlassStyle,
) {
    setFloat2("uSize", panelSize.width.toFloat(), panelSize.height.toFloat())
    setFloat4("uCornerRadii", radiusPx, radiusPx, radiusPx, radiusPx)

    // G2 角：扩展倍率与 p 指数（收小版，见 SoftGlassShader 顶部）
    val minHalf = minOf(panelSize.width, panelSize.height) * 0.5f
    val ratio = (((minHalf / radiusPx.coerceAtLeast(1e-3f)) - 1f) / 0.40f).coerceIn(0f, 1f)
    setFloat("uCornerExt", 1f + 0.40f * ratio)
    setFloat("uCornerPow", 2f + 0.60f * ratio)

    val edgePx = style.edgeWidth  // px，不乘 density
    setFloat("uEdgePx", edgePx)
    setFloat("uInvEdgePow", 1f / style.edgePow.coerceAtLeast(0.05f))

    val ld = style.lightDirection
    setFloat3("uLightDir", ld.first, ld.second, ld.third)
    // K = 0.5 / sqrt(1 - cos(angleRange * π * 0.5))
    val cosA = kotlin.math.cos(style.lightAngleRange * kotlin.math.PI.toFloat() * 0.5f)
    val k = 0.5f / kotlin.math.sqrt((1f - cosA).coerceAtLeast(1e-6f))
    setFloat("uAngleK", k)
    setFloat("uLightIntensity", style.lightIntensity)
    setFloat("uLightOpposite", style.lightOppositeIntensity)
    setFloat("uColorPow", style.colorPow)
    setFloat("uAlpha", style.alpha)
}

/**
 * 采样源：模糊金字塔的相邻两级 + 最粗一级（大模糊底）。
 *
 * 只有**跨级**时才重绑纹理（`setSampler` 会新建 BitmapShader），档位内部只改 `uBlendT`，
 * 所以拖模糊滑杆基本没有额外开销。
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
    // 第 i 级的 texel 是第 0 级的 1/2^i；用实际宽比算，壁纸很窄时某级没减半也不会错位
    setFloat("uLevelScaleA", a.width / baseWidth)
    setFloat("uLevelScaleB", b.width / baseWidth)
    setFloat("uLevelScaleCoarse", coarse.width / baseWidth)
}

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
    // 反射偏移各档固定，只有「折射=关」才归零（详见 SoftGlassStyle.reflectOffset）
    setFloat("uReflectOffsetPx", if (refractionScale > 0f) style.reflectOffset else 0f)
    setFloat("uIOR", style.refractIOR)
    setFloat("uReflStrength", style.reflectionStrength)
    setFloat("uReflLighten", style.reflectionLighten)
    setFloat("uInnerBottom", style.innerBottom)
    setFloat("uEdgeHighlight", if (style.edgeHighlight) 1f else 0f)
    setFloat("uUnshade", style.unshade)
    setFloat("uUsePowGate", if (style.usePowGate) 1f else 0f)
    // AA 阈值：系统 = smooth5_vertical(3/edge, 0.5) = smooth5Map(√(3/edge))。
    // 直接传裸的 3/edge 会让 alpha 在 0.2px 内冲到 1，边缘重新变硬。
    val edgePx = style.edgeWidth.coerceAtLeast(1f)
    setFloat("uNmlZAAThreshold", smooth5Vertical(3f / edgePx))
    setFloat("uHit", if (style.hit) 1f else 0f)
    val gc = style.glassColor
    setFloat4("uGlassColor", gc.red, gc.green, gc.blue, style.glassColorMix)
    setFloat("uDebug", style.debugView.toFloat())

    // 颜色处理：原先在 CPU 烘焙阶段，现在逐像素在 shader 里算
    setFloat("uDarker", style.darker)
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
