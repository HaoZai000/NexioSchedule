package com.haooz.softglass

import com.haooz.softglass.glass.SOFT_GLASS_BODY_SHADER
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import org.jetbrains.skia.RuntimeEffect
import kotlin.test.Test

class SoftGlassShaderCompileTest {

    @Test
    fun bodyShaderCompiles() {
        RuntimeEffect.makeForShader(SOFT_GLASS_BODY_SHADER)
    }

    @Test
    fun liveShaderCompiles() {
        RuntimeEffect.makeForShader(SOFT_GLASS_LIVE_SHADER)
    }
}
