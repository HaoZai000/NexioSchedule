package com.haooz.softglass

import com.haooz.softglass.platform.GlassShaderRegistry
import com.haooz.softglass.platform.GlassShaderProgram
import com.haooz.softglass.platform.asSoftwareBitmap
import com.haooz.softglass.platform.createGlassImageBitmap
import com.haooz.softglass.platform.createGlassRuntimeShader
import com.haooz.softglass.platform.isRenderEffectSupported
import com.haooz.softglass.platform.isRuntimeShaderEffectSupported
import com.haooz.softglass.platform.isRuntimeShaderSupported
import com.haooz.softglass.platform.isSoftGlassShaderAvailable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val SMOKE_SHADER = """
uniform shader content;
uniform float uScale;
uniform float4 uTint;

half4 main(float2 coord) {
    half4 c = content.eval(coord);
    return half4(c.rgb * uScale * uTint.rgb, c.a);
}
"""

class PlatformGoldenTest {

    @Test
    fun capabilitiesAreAvailableOnSkiaBackend() {
        assertTrue(isRenderEffectSupported())
        assertTrue(isRuntimeShaderSupported())
        assertTrue(isRuntimeShaderEffectSupported())
        assertTrue(isSoftGlassShaderAvailable())
    }

    @Test
    fun runtimeShaderCompilesAndAcceptsUniforms() {
        val shader = createGlassRuntimeShader(SMOKE_SHADER)
        shader.setFloatUniform("uScale", 1.5f)
        shader.setFloatUniform("uTint", 1f, 0.5f, 0.25f, 1f)
        shader.setIntUniform("uCount", 2)
    }

    @Test
    fun registrySharesCompiledPrograms() {
        val first = GlassShaderRegistry.obtain("softglass-smoke", SMOKE_SHADER)
        val second = GlassShaderRegistry.obtain("softglass-smoke", SMOKE_SHADER)
        assertSame(first, second)
    }

    @Test
    fun programBackendCompilesShader() {
        val program = GlassShaderProgram(SMOKE_SHADER)
        program.setFloat("uScale", 1f)
        program.setFloat4("uTint", 1f, 1f, 1f, 1f)
    }

    @Test
    fun argbBitmapKeepsSizeAndSurvivesSoftwareConversion() {
        val pixels = IntArray(2 * 2) { 0xFF336699.toInt() }
        val bitmap = createGlassImageBitmap(pixels, 2, 2)
        assertEquals(2, bitmap.width)
        assertEquals(2, bitmap.height)
        assertSame(bitmap, bitmap.asSoftwareBitmap())
    }
}
