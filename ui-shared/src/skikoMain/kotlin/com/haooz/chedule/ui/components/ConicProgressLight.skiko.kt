package com.haooz.chedule.ui.components

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.skiaCanvas
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.math.roundToInt

/**
 * Skia 后端（桌面 / iOS / 鸿蒙）跑与 Android 同一份 shader 源：[CONIC_LIGHT_SHADER]。
 * 与 ArcBendShader.skiko 同理，直接用 Skia API，不碰 miuix-blur 的封装差异。
 * 编译失败返回 null，调用方走径向渐变兜底。
 */
private var conicLightCapable: Boolean? = null

internal actual fun createConicProgressLight(): ConicProgressLight? {
    if (conicLightCapable == null) {
        conicLightCapable = runCatching { RuntimeEffect.makeForShader(CONIC_LIGHT_SHADER) }.isSuccess
    }
    if (conicLightCapable != true) return null
    return runCatching { ConicProgressLightSkia() }.getOrNull()
}

/** 与 Android Api33 版一致：450px 高、左右各 300px 留白，两束光锥叠加只出现在进度头后方。 */
private class ConicProgressLightSkia : ConicProgressLight {
    private val effect = RuntimeEffect.makeForShader(CONIC_LIGHT_SHADER)
    private val builder = RuntimeShaderBuilder(effect)
    private val shader = builder.makeShader()
    private val paint = Paint().apply { this.shader = this@ConicProgressLightSkia.shader }

    override fun draw(
        canvas: Canvas,
        trackWidth: Float,
        rowHeight: Float,
        progress: Float,
        accent: Color,
        darkSurface: Boolean,
        alphaReveal: Float,
        shapeReveal: Float,
    ) {
        if (trackWidth <= 0f || alphaReveal <= 0f) return
        val safeProgress = progress.coerceIn(0f, 1f)
        val reveal = shapeReveal.coerceIn(0f, 1f)
        // 原厂在进度 < 20% 时用贝塞尔把高度与 falloff 收敛为线性斜坡。
        val modulated = if (safeProgress > 0.2f) 1f else (safeProgress / 0.21f).coerceIn(0f, 1f)
        val height = (0.1f + 0.5f * reveal) * modulated
        val heightFalloff = 0.6f * reveal * modulated
        val coneK = 0.2f * reveal
        val w0 = 0.008f * reveal
        val lightWidth = trackWidth + 2f * ConicLightHorizontalPadding
        val lightHeight = ConicLightHeight

        builder.uniform("u_resolution", lightWidth, lightHeight)
        val rgb = floatArrayOf(accent.red * 255f, accent.green * 255f, accent.blue * 255f)
        builder.uniform("u_startColor", rgb)
        builder.uniform("u_endColor", rgb)
        builder.uniform("u_progressHeightParams", floatArrayOf(safeProgress, trackWidth, height, heightFalloff))
        builder.uniform("u_basicParams", 3f, 1f, 2f, 0.2f)
        val lightAlpha = alphaReveal.coerceIn(0f, 1f)
        builder.uniform("u_alphaParams", floatArrayOf(lightAlpha, 0.6f * lightAlpha, w0, ConicLightSpotRadius))
        builder.uniform("u_coneParams", coneK, coneK, 2f, 1f)
        builder.uniform("u_miscParams", 1f, 0f, 0f, 0f)

        val skCanvas = canvas.skiaCanvas
        val saved = skCanvas.save()
        val trackTop = (lightHeight - rowHeight) / 2f
        val trackLeft = ConicLightHorizontalPadding
        val trackRight = trackLeft + trackWidth
        val trackBottom = trackTop + rowHeight
        skCanvas.translate(-trackLeft, -trackTop)
        if (!darkSurface) {
            // 浅色卡：轨道外先走 SRC_OVER 把进度色光晕叠在近白底上，轨道内改走 SOFT_LIGHT。
            val outer = skCanvas.save()
            skCanvas.clipRect(trackLeft, trackTop, trackRight, trackBottom, ClipMode.DIFFERENCE, false)
            paint.blendMode = BlendMode.SRC_OVER
            paint.alpha = (lightAlpha * LightSurfaceConeOpacity * 255f).roundToInt()
            skCanvas.drawRect(0f, 0f, lightWidth, lightHeight, paint)
            skCanvas.restoreToCount(outer)
            skCanvas.clipRect(trackLeft, trackTop, trackRight, trackBottom, ClipMode.INTERSECT, false)
        }
        paint.blendMode = if (darkSurface) BlendMode.SCREEN else BlendMode.SOFT_LIGHT
        paint.alpha = 255
        skCanvas.drawRect(0f, 0f, lightWidth, lightHeight, paint)
        skCanvas.restoreToCount(saved)
    }
}

private const val ConicLightHeight = 450f
private const val ConicLightHorizontalPadding = 300f
private const val ConicLightSpotRadius = 500f
private const val LightSurfaceConeOpacity = 0.24f
