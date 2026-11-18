@file:SuppressLint("NewApi")

package com.haooz.chedule.ui.components

import android.annotation.SuppressLint
import android.graphics.BlendMode
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.roundToInt

/** null = 还没探测。AGSL 编译失败或系统 < 33 都视为不支持，调用方走径向渐变兜底。 */
private var conicLightCapable: Boolean? = null

internal actual fun createConicProgressLight(): ConicProgressLight? {
    if (conicLightCapable == null) {
        conicLightCapable = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            false
        } else {
            runCatching { RuntimeShader(CONIC_LIGHT_SHADER) }
                .onFailure { Log.w("ConicProgress", "AGSL 编译失败，降级为径向渐变", it) }
                .isSuccess
        }
    }
    if (conicLightCapable != true) return null
    return runCatching { ConicProgressLightApi33() }
        .onFailure { Log.w("ConicProgress", "锥光实例创建失败，降级为径向渐变", it) }
        .getOrNull()
}

/**
 * 原厂 COUIIntentSeekBar 的锥光：450px 高、左右各 300px 留白的光层，
 * 两束光锥叠加后以 SCREEN 混合盖在进度之上，只出现在进度头后方。
 */
private class ConicProgressLightApi33 : ConicProgressLight {
    private val runtimeShader = RuntimeShader(CONIC_LIGHT_SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = runtimeShader }

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
        // 原厂在进度 < 20% 时用三次贝塞尔把高度与 falloff 收敛为线性斜坡。
        val modulated = if (safeProgress > 0.2f) 1f else (safeProgress / 0.21f).coerceIn(0f, 1f)
        // PLAYING 入场：先插值 OFF -> PLAYING，再乘进度斜坡。
        val height = (0.1f + 0.5f * reveal) * modulated
        val heightFalloff = 0.6f * reveal * modulated
        val coneK = 0.2f * reveal
        val w0 = 0.008f * reveal
        val lightWidth = trackWidth + 2f * ConicLightHorizontalPadding
        val lightHeight = ConicLightHeight

        runtimeShader.setFloatUniform("u_resolution", lightWidth, lightHeight)
        val rgb = floatArrayOf(accent.red * 255f, accent.green * 255f, accent.blue * 255f)
        runtimeShader.setFloatUniform("u_startColor", rgb)
        runtimeShader.setFloatUniform("u_endColor", rgb)
        runtimeShader.setFloatUniform(
            "u_progressHeightParams",
            floatArrayOf(safeProgress, trackWidth, height, heightFalloff),
        )
        runtimeShader.setFloatUniform("u_basicParams", 3f, 1f, 2f, 0.2f)
        val lightAlpha = alphaReveal.coerceIn(0f, 1f)
        runtimeShader.setFloatUniform(
            "u_alphaParams",
            floatArrayOf(lightAlpha, 0.6f * lightAlpha, w0, ConicLightSpotRadius),
        )
        runtimeShader.setFloatUniform("u_coneParams", coneK, coneK, 2f, 1f)
        runtimeShader.setFloatUniform("u_miscParams", 1f, 0f, 0f, 0f)

        val native = canvas.nativeCanvas
        val saved = native.save()
        val trackTop = (lightHeight - rowHeight) / 2f
        val trackLeft = ConicLightHorizontalPadding
        val trackRight = trackLeft + trackWidth
        val trackBottom = trackTop + rowHeight
        native.translate(-trackLeft, -trackTop)
        if (!darkSurface) {
            // 浅色卡片轨道外先走 SRC_OVER 把进度色光晕叠在近白底上；
            // 轨道内改走 SOFT_LIGHT —— SCREEN 在浅底会把 accent 冲成死白并过曝。
            val outer = native.save()
            native.clipOutRect(trackLeft, trackTop, trackRight, trackBottom)
            paint.blendMode = BlendMode.SRC_OVER
            paint.alpha = (lightAlpha * LightSurfaceConeOpacity * 255f).roundToInt()
            native.drawRect(0f, 0f, lightWidth, lightHeight, paint)
            native.restoreToCount(outer)
            native.clipRect(trackLeft, trackTop, trackRight, trackBottom)
        }
        paint.blendMode = if (darkSurface) BlendMode.SCREEN else BlendMode.SOFT_LIGHT
        paint.alpha = 255
        native.drawRect(0f, 0f, lightWidth, lightHeight, paint)
        native.restoreToCount(saved)
    }
}

private const val ConicLightHeight = 450f
private const val ConicLightHorizontalPadding = 300f
private const val ConicLightSpotRadius = 500f
private const val LightSurfaceConeOpacity = 0.24f
