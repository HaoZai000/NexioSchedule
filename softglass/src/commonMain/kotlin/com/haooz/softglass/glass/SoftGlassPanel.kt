package com.haooz.softglass.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.haooz.softglass.platform.GlassBlend
import com.haooz.softglass.platform.GlassShaderProgram
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class SoftGlassProgramKey(
    val width: Int,
    val height: Int,
    val radiusTenths: Int,
)

private data class SoftGlassProgramConfig(
    val style: SoftGlassStyle,
    val backdrop: SoftGlassBackdrop,
    val levelA: Int,
    val levelB: Int,
)

private class SoftGlassProgramSlot(val program: GlassShaderProgram) {
    var config: SoftGlassProgramConfig? = null
}

/** 同尺寸同圆角共享一份已编译程序；样式与采样源只走 uniform。 */
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

/** 为单个面板绘制柔光玻璃；逐帧只更新 `uOrigin` / `uTexMap` / `uBlendT`。 */
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
    val rawRadius = when {
        circle -> minOf(size.width, size.height) * 0.5f
        radiusPx != null -> radiusPx
        else -> styleProvider().cornerRadius.value * density.density
    }
    val glassRadius = rawRadius.coerceIn(0f, minOf(size.width, size.height) * 0.5f)
    val keyBase = if (backdrop != null && sizePx.width > 0) {
        SoftGlassProgramKey(
            width = sizePx.width,
            height = sizePx.height,
            radiusTenths = (glassRadius * 10f).roundToInt(),
        )
    } else null
    val bodySlot = if (keyBase != null && backdrop != null) {
        runCatching {
            SoftGlassProgramCache.get(keyBase) {
                SoftGlassProgramSlot(GlassShaderProgram(SOFT_GLASS_BODY_SHADER))
            }
        }.getOrNull()
    } else null
    val surfaceBackdrop = backdrop

    onDrawBehind {
        redraw()

        if (bodySlot == null || surfaceBackdrop == null) {
            drawRect(Color.White.copy(alpha = 0.14f))
            return@onDrawBehind
        }
        val style = styleProvider()
        val texMap = texMapProvider()
        val levels = surfaceBackdrop.levelPair(style.smallBlurRadius * texMap[0])
        val config = SoftGlassProgramConfig(style, surfaceBackdrop, levels.a, levels.b)
        val o = origin()

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
        bodySlot.program.setFloat2("uOrigin", o.x, o.y)
        bodySlot.program.setFloat4("uTexMap", texMap[0], texMap[1], texMap[2], texMap[3])
        bodySlot.program.setFloat("uBlendT", levels.t)
        drawIntoCanvas { canvas ->
            bodySlot.program.draw(canvas, size.width, size.height, GlassBlend.NORMAL)
        }
    }
}

private fun GlassShaderProgram.applyGeometryUniforms(
    panelSize: IntSize,
    radiusPx: Float,
    style: SoftGlassStyle,
) {
    setFloat2("uSize", panelSize.width.toFloat(), panelSize.height.toFloat())
    setFloat4("uCornerRadii", radiusPx, radiusPx, radiusPx, radiusPx)

    val edgePx = style.edgeWidth
    setFloat("uEdgePx", edgePx)
    setFloat("uInvEdgePow", 1f / style.edgePow.coerceAtLeast(0.05f))

    val ld = style.lightDirection
    setFloat3("uLightDir", ld.first, ld.second, ld.third)
    val cosA = cos(style.lightAngleRange * PI.toFloat() * 0.5f)
    setFloat("uAngleK", 0.5f / sqrt((1f - cosA).coerceAtLeast(1e-6f)))
    setFloat("uLightIntensity", style.lightIntensity)
    setFloat("uLightOpposite", style.lightOppositeIntensity)
    setFloat("uColorPow", style.colorPow)
    setFloat("uAlpha", style.alpha)
}

private fun GlassShaderProgram.applySamplingUniforms(
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
    setFloat("uLevelScaleA", a.width / baseWidth)
    setFloat("uLevelScaleB", b.width / baseWidth)
    setFloat("uLevelScaleCoarse", coarse.width / baseWidth)
}

private fun GlassShaderProgram.applyBodyUniforms(
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
    setFloat("uReflectOffsetPx", if (refractionScale > 0f) style.reflectOffset else 0f)
    setFloat("uIOR", style.refractIOR)
    setFloat("uReflStrength", style.reflectionStrength)
    setFloat("uReflLighten", style.reflectionLighten)
    setFloat("uInnerBottom", style.innerBottom)
    setFloat("uEdgeHighlight", if (style.edgeHighlight) 1f else 0f)
    setFloat("uLightFalloff", style.edgeLightFalloff.coerceIn(0f, 1f))
    setFloat("uUnshade", style.unshade)
    setFloat("uUsePowGate", if (style.usePowGate) 1f else 0f)
    val edgePx = style.edgeWidth.coerceAtLeast(1f)
    setFloat("uNmlZAAThreshold", smooth5Vertical(3f / edgePx))
    setFloat("uHit", if (style.hit) 1f else 0f)
    val gc = style.glassColor
    setFloat4("uGlassColor", gc.red, gc.green, gc.blue, style.glassColorMix)
    setFloat("uDebug", style.debugView.toFloat())

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
