// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp

/*
 * 柔光玻璃（HyperOS `mi_glass_sdf`）的绘制入口。链路：
 * 1. `rememberSoftGlassSurface` 按页烘焙轻 / 重两张低分辨率模糊图（[SoftGlassBackdrop]）；
 * 2. 面板在 `drawWithCache` 里按「尺寸 + 圆角 + 样式」取共享的 [SoftGlassProgram]（`SoftGlassProgramCache`）；
 * 3. 每帧只更新 `uOrigin`，主体 NORMAL、描边 LIGHTEN。
 *
 * 坐标：位图与屏幕同原点，`uOrigin` = 窗口坐标 × `bakeScale`；
 * `uSize` / `uCornerRadii` / `fragCoord` 是面板本地 px，形状由解析式 G2 SDF 计算。
 */

/**
 * program 缓存键：**只放决定渲染目标的量**（哪一趟 / 面板尺寸 / 圆角）。
 *
 * ⚠ 样式与采样纹理**不能进键**：它们全都只是 uniform，改一次重灌一次就够。
 * 以前把 style / backdrop 也放进来，外观页每拖一格都会新建一个 `RuntimeShader`
 * —— AGSL 要现场编译，于是拖滑杆、点开关全都卡成幻灯。
 */
private data class SoftGlassProgramKey(
    val rim: Boolean,
    val width: Int,
    val height: Int,
    val radiusTenths: Int,
)

/** 一次 uniform 灌装对应的配置；与 slot 上记的比对，变了才重灌。 */
private data class SoftGlassProgramConfig(
    val style: SoftGlassStyle,
    val backdrop: SoftGlassBackdrop,
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
 * 一次构造建主体 + 描边两个 [SoftGlassProgram]，把样式 / 采样尺寸灌成 uniform；
 * 逐帧只更新 `uOrigin` 一个 uniform —— 滚动 / 翻周时采样原点跟手，不重建 shader。
 *
 * @param origin 面板左上角在背景位图里的坐标，**绘制期**求值；位图已与屏幕对齐，直接传窗口坐标。
 * @param radiusPx 圆角（px）。null = 用 `style.cornerRadius`；[circle] 为 true 时忽略，取短边一半。
 * @param redraw 绘制期读一次的「位置信号」：祖先图层只平移不重绘子树，
 *   在这里读滚动 / 翻页状态，快照观察才会按帧失效。
 */
fun Modifier.softGlass(
    backdrop: SoftGlassBackdrop?,
    style: SoftGlassStyle,
    density: Density,
    origin: () -> Offset,
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
        else -> style.cornerRadius.value * density.density
    }
    val radius = rawRadius.coerceIn(0f, minOf(size.width, size.height) * 0.5f)
    // 玻璃圆角比卡片 G2 略收：10dp 收 0.9dp，20dp 及以上收 2dp，中间线性。
    val radiusDp = radius / density.density
    val glassInsetDp = (0.9f + (radiusDp - 10f) * 0.11f).coerceIn(0.9f, 2f)
    val glassRadius = (radius - glassInsetDp * density.density).coerceAtLeast(0f)
    val keyBase = if (backdrop != null && sizePx.width > 0) {
        SoftGlassProgramKey(
            rim = false,
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
    val rimSlot = if (keyBase != null && backdrop != null) {
        runCatching {
            SoftGlassProgramCache.get(keyBase.copy(rim = true)) {
                SoftGlassProgramSlot(SoftGlassProgram(SOFT_GLASS_RIM_SHADER))
            }
        }.getOrNull()
    } else null
    val config = if (bodySlot != null && backdrop != null) {
        SoftGlassProgramConfig(style = style, backdrop = backdrop)
    } else null
    val surfaceBackdrop = backdrop

    onDrawBehind {
        // 先读滚动 / 翻页信号：draw 期读 State 才会订阅，滚动时按帧失效。
        redraw()

        if (bodySlot == null || config == null || surfaceBackdrop == null) {
            drawRect(Color.White.copy(alpha = 0.14f))
            return@onDrawBehind
        }
        val o = origin()
        val bake = surfaceBackdrop.bakeScale

        /**
         * 配置变了（拖参数 / 切开关）就在**绘制期**就地重灌 uniform。
         *
         * 放在这里而不是 build 期：shader 实例按尺寸缓存、跨面板共享，uniform 是它自己的状态。
         * 若在 build 期灌，两个配置不同的面板（不同页 / 弹窗 vs 卡片）会互相覆盖；
         * 若把 style 塞进缓存键，拖一格就重编译一次 AGSL —— 那才是原来卡顿的根因。
         * 这里每次绘制比一次配置，相同就跳过，所以常态下只有 compare 开销。
         */
        if (bodySlot.config != config) {
            val ok = runCatching {
                bodySlot.program.applyBodyUniforms(
                    backdrop = surfaceBackdrop,
                    style = style,
                    density = density.density,
                    panelSize = sizePx,
                    radiusPx = glassRadius,
                    origin = o,
                )
            }.isSuccess
            if (!ok) {
                drawRect(Color.White.copy(alpha = 0.14f))
                return@onDrawBehind
            }
            bodySlot.config = config
        }
        rimSlot?.let { slot ->
            if (slot.config != config) {
                runCatching {
                    slot.program.applyRimUniforms(
                        backdrop = surfaceBackdrop,
                        style = style,
                        density = density.density,
                        panelSize = sizePx,
                        radiusPx = glassRadius,
                        origin = o,
                    )
                }
                slot.config = config
            }
        }

        // 采样原点会动（滚动 / 拖拽 / 翻周）：shader 实例复用，只更新这一个 uniform。
        bodySlot.program.setFloat2("uOrigin", o.x * bake, o.y * bake)
        rimSlot?.program?.setFloat2("uOrigin", o.x * bake, o.y * bake)
        drawIntoCanvas { canvas ->
            bodySlot.program.draw(canvas, size.width, size.height, SoftGlassBlend.NORMAL)
            // 描边用 LIGHTEN（逐通道取最大），只提亮、保留左上 / 右下方向差；
            // 换 SCREEN 会把暗部一起抬起来，四周变均匀。
            rimSlot?.program?.draw(canvas, size.width, size.height, SoftGlassBlend.LIGHTEN)
        }
    }
}

@Composable
fun SoftGlassPanel(
    backdrop: SoftGlassBackdrop?,
    /** 面板左上角在背景图里的坐标（= 窗口坐标 − 背景图层原点）。 */
    originInBackdrop: Offset,
    style: SoftGlassStyle,
    modifier: Modifier = Modifier,
    /** 圆形控件（按钮）：圆角取短边一半，压过 style.cornerRadius。 */
    circle: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val tone = backdrop?.meanLuma
    val effectiveStyle = remember(style, tone) {
        if (tone == null) style else style.adapted(tone)
    }

    Box(
        modifier = modifier.softGlass(
            backdrop = backdrop,
            style = effectiveStyle,
            density = density,
            origin = { originInBackdrop },
            circle = circle,
        ),
        content = content,
    )
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

private fun SoftGlassProgram.applyCommonUniforms(
    panelSize: IntSize,
    radiusPx: Float,
    origin: Offset,
    textureSize: IntSize,
    style: SoftGlassStyle,
    density: Float,
    bakeScale: Float,
) {
    setFloat2("uSize", panelSize.width.toFloat(), panelSize.height.toFloat())
    setFloat4("uCornerRadii", radiusPx, radiusPx, radiusPx, radiusPx)
    setFloat2("uOrigin", origin.x * bakeScale, origin.y * bakeScale)
    setFloat2("uTexSize", textureSize.width.toFloat(), textureSize.height.toFloat())
    setFloat("uBakeScale", bakeScale)

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

private fun SoftGlassProgram.applyBodyUniforms(
    backdrop: SoftGlassBackdrop,
    style: SoftGlassStyle,
    density: Float,
    panelSize: IntSize,
    radiusPx: Float,
    origin: Offset,
) {
    setSampler("uRefractTex", backdrop.refract)
    applyCommonUniforms(
        panelSize = panelSize,
        radiusPx = radiusPx,
        origin = origin,
        textureSize = IntSize(backdrop.refract.width, backdrop.refract.height),
        style = style,
        density = density,
        bakeScale = backdrop.bakeScale,
    )

    // 调色（darker / luminanceCurve / adjustColor / gcr）已在烘焙阶段做完，
    // 见 SoftGlassBackdrop.applyGlassColorProcess —— 这里没有对应 uniform。
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
}

private fun SoftGlassProgram.applyRimUniforms(
    backdrop: SoftGlassBackdrop,
    style: SoftGlassStyle,
    density: Float,
    panelSize: IntSize,
    radiusPx: Float,
    origin: Offset,
) {
    setSampler("uBgColorTex", backdrop.big)
    applyCommonUniforms(
        panelSize = panelSize,
        radiusPx = radiusPx,
        origin = origin,
        textureSize = IntSize(backdrop.big.width, backdrop.big.height),
        style = style,
        density = density,
        bakeScale = backdrop.bakeScale,
    )
}
