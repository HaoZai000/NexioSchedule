package com.haooz.chedule.ui.activities.weather

import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SamplingMode

/** 离线把 shader 渲成 PNG，用于肉眼比对参考实现（不参与产品代码）。 */
class WeatherShaderPreview {

    private val w = 420
    private val h = 860

    @Test
    fun previewRainAndSnow() {
        val page = pageShader()
        render(WEATHER_RAIN_SHADER, page, rainUniforms(1.6f), "/tmp/wx_rain_1.png")
        render(WEATHER_RAIN_SHADER, page, rainUniforms(4.4f), "/tmp/wx_rain_2.png")
        render(WEATHER_SNOW_SHADER, page, mutableMapOf("uTime" to 6f, "uProgress" to 0.30f, "uAlpha" to 1f), "/tmp/wx_snow.png")
        render(WEATHER_FROST_SHADER, page, mutableMapOf("uTime" to 8f, "uProgress" to 0.85f, "uAlpha" to 1f), "/tmp/wx_frost.png")
    }

    private fun rainUniforms(t: Float) = mutableMapOf<String, Any>(
        "uTime" to t,
        "uStaticDropSize" to 1f,
        "uStaticDropAmount" to 0.5f,
        "uStaticDropSpeed" to 1f,
        "uRunningDropSize" to 1f,
        "uRunningDropAmount" to 0.5f,
        "uRunningDropSpeed" to 1f,
        "uAlpha" to 1f,
        "uFlash" to 0f,
        "uFade" to 1f,
        "uRectCount" to 1f,
        "uRect0" to floatArrayOf(24f, 120f, w - 48f, 110f),
        "uCornerRadius" to 24f,
    )

    private fun render(src: String, content: org.jetbrains.skia.Shader, uniforms: Map<String, Any>, out: String) {
        val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(src))
        builder.child("content", content)
        builder.uniform("uSize", w.toFloat(), h.toFloat())
        uniforms.forEach { (k, v) ->
            when (v) {
                is Float -> builder.uniform(k, v)
                is FloatArray -> builder.uniform(k, v[0], v[1], v[2], v[3])
                else -> error("unsupported uniform \$k")
            }
        }
        val surface = Surface.makeRasterN32Premul(w, h)
        val paint = Paint().apply { shader = builder.makeShader(null) }
        surface.canvas.drawRect(Rect.makeWH(w.toFloat(), h.toFloat()), paint)
        val png = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!
        File(out).writeBytes(png.bytes)
    }

    /** 造一张大致像天气页的"内容"：天空渐变 + 几张半透明卡片。 */
    private fun pageShader(): org.jetbrains.skia.Shader {
        val surface = Surface.makeRasterN32Premul(w, h)
        val c: Canvas = surface.canvas
        c.drawRect(
            Rect.makeWH(w.toFloat(), h.toFloat()),
            Paint().apply {
                shader = org.jetbrains.skia.Shader.makeLinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    org.jetbrains.skia.Gradient(
                        org.jetbrains.skia.Gradient.Colors(
                            arrayOf(
                                Color4f(150f / 255f, 190f / 255f, 225f / 255f, 1f),
                                Color4f(225f / 255f, 235f / 255f, 245f / 255f, 1f),
                            ),
                            null,
                            FilterTileMode.CLAMP,
                        ),
                    ),
                )
            },
        )
        // 高频纹理：折射偏移才看得出来
        for (i in 0 until 26) {
            val stripe = Paint().apply {
                color = Color.makeARGB(255, 40 + i * 8, 90 + i * 5, 160 - i * 3)
            }
            c.drawRect(Rect.makeXYWH((i * 17).toFloat(), 0f, 8f, h.toFloat()), stripe)
        }
        val card = Paint().apply { color = Color.makeARGB(120, 255, 255, 255) }
        var y = 120f
        repeat(5) {
            c.drawRRect(RRect.makeXYWH(24f, y, w - 48f, 110f, 20f), card)
            y += 130f
        }
        return surface.makeImageSnapshot().makeShader(
            FilterTileMode.CLAMP,
            FilterTileMode.CLAMP,
            SamplingMode.DEFAULT,
            null,
        )
    }

    @Suppress("unused")
    private fun Int.toSk() = this
}
