package com.haooz.chedule.ui.activities.weather

import com.haooz.softglass.platform.createGlassRuntimeShader
import kotlin.test.Test

class WeatherShaderCompileTest {
    @Test
    fun shadersCompile() {
        createGlassRuntimeShader(WEATHER_RAIN_SHADER)
        createGlassRuntimeShader(WEATHER_SNOW_SHADER)
    }
}
