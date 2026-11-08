package com.haooz.softglass

import com.haooz.softglass.glass.GlassLightDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GlassGoldenTest {

    @Test
    fun nexioPresetLocksCurrentDefaults() {
        val s = GlassPreset.Nexio.style
        assertEquals(28f, s.cornerRadius.value)
        assertEquals(60f, s.edgeWidth)
        assertEquals(4f, s.edgePow)
        assertEquals(0, s.slopeMode)
        assertEquals(96f, s.thickness)
        assertEquals(600f, s.reflectOffset)
        assertEquals(1f, s.refractionScale)
        assertEquals(3f, s.refractIOR)
        assertEquals(0.8f, s.reflectionStrength)
        assertEquals(1f, s.reflectionLighten)
        assertEquals(1.2f, s.colorPow)
        assertEquals(0.1f, s.glassColorMix)
        assertEquals(1f, s.alpha)
        assertFalse(s.hit)
        assertEquals(0.05f, s.innerBottom)
        assertEquals(0f, s.unshade)
        assertTrue(s.usePowGate)
        assertEquals(GlassLightDirection, s.lightDirection)
        assertEquals(1f, s.lightAngleRange)
        assertEquals(1.6f, s.lightIntensity)
        assertEquals(0.8f, s.lightOppositeIntensity)
        assertEquals(0f, s.bgColorSaturation)
        assertEquals(0f, s.bgColorBrightness)
        assertEquals(listOf(0f, 2f, 0.5f, 0.8f), s.luminanceValues)
        assertEquals(0.15f, s.luminanceAmount)
        assertEquals(1, s.luminanceCurveMode)
        assertEquals(2f, s.saturation)
        assertEquals(0.08f, s.brightness)
        assertEquals(0.15f, s.innerColorWhite)
        assertEquals(0.4f, s.innerColorMix)
        assertEquals(0f, s.burn)
        assertEquals(0f, s.darker)
        assertEquals(0.6f to 1f, s.darkerRange)
        assertEquals(0f, s.pressLightCenterFall)
        assertEquals(0f, s.pressLightCenterStrength)
        assertEquals(0f, s.pressLightCenterPeak)
        assertEquals(listOf(0f, 0f), s.pressRingStrength)
        assertTrue(s.edgeHighlight)
        assertEquals(1f, s.edgeLightFalloff)
        assertEquals(32f, s.smallBlurRadius)
        assertEquals(500f, s.bigBlurRadius)
        assertEquals(0, s.debugView)
    }

    @Test
    fun materialLevelsExportCurrentTuning() {
        val soft = GlassMaterialLevel.SOFT.tuning()
        assertEquals(6f, soft.blurDp)
        assertEquals(12f, soft.panelBlurDp)
        assertEquals(12f, soft.dialogBlurDp)
        assertEquals(0.2f, soft.surfaceAlphaDropLight)
        assertEquals(0.32f, soft.surfaceAlphaDropDark)
        assertEquals(1f, soft.refractionScale)
        assertTrue(soft.edgeHighlight)
        assertTrue(soft.glassEffectsEnabled)

        val frosted = GlassMaterialLevel.FROSTED.tuning()
        assertEquals(6f, frosted.blurDp)
        assertEquals(12f, frosted.panelBlurDp)
        assertEquals(0f, frosted.surfaceAlphaDropLight)
        assertEquals(0f, frosted.refractionScale)
        assertFalse(frosted.edgeHighlight)

        val none = GlassMaterialLevel.NONE.tuning()
        assertEquals(0f, none.blurDp)
        assertEquals(0f, none.panelBlurDp)
        assertEquals(0f, none.dialogBlurDp)
        assertFalse(none.glassEffectsEnabled)
        assertEquals(1f, GlassMaterialLevel.NONE.surfaceAlpha(0.45f, true))
        assertEquals(0.25f, GlassMaterialLevel.SOFT.surfaceAlpha(0.45f, true), 1e-5f)
    }

    @Test
    fun tokenPresetsCarryDocumentedValues() {
        val expected = listOf(
            GlassToken.BIONIC_MEDIUM_THIN to listOf(60f, 60f, 1000f, 1.4f, 0.20f, 2.4f, 40f, 500f),
            GlassToken.BIONIC_MEDIUM_NORMAL to listOf(60f, 80f, 1200f, 1.4f, 0.30f, 2.6f, 120f, 120f),
            GlassToken.BIONIC_MEDIUM_REGULAR to listOf(60f, 80f, 600f, 1.8f, 0.10f, 2f, 36f, 500f),
            GlassToken.BIONIC_SEMIBOLD_MOTION to listOf(60f, 80f, 1200f, 2f, 0.30f, 2.6f, 120f, 120f),
            GlassToken.COMMON_SMALL_REGULAR to listOf(18f, 100f, 110f, 1.4f, 0.20f, 1f, 8f, 500f),
            GlassToken.COMMON_SMALL_BOLD to listOf(60f, 80f, 1200f, 1.4f, 0.40f, 2.6f, 120f, 120f),
            GlassToken.COMMON_MEDIUM_THIN_LOW to listOf(60f, 60f, 600f, 1.5f, 0.10f, 1f, 36f, 500f),
            GlassToken.COMMON_MEDIUM_REGULAR_DARK to listOf(60f, 80f, 600f, 3f, 0f, 0f, 36f, 500f),
            GlassToken.COMMON_LARGE_REGULAR to listOf(50f, 80f, 600f, 2.2f, 0.20f, 0f, 80f, 500f),
            GlassToken.COMMON_LARGE_DEMIBOLD to listOf(50f, 80f, 600f, 2f, 0.26f, 0f, 50f, 500f),
            GlassToken.TINT_PURPLE to listOf(60f, 80f, 600f, 1f, 0.20f, 0f, 36f, 500f),
            GlassToken.TINT_BLACK_REGULAR to listOf(60f, 80f, 1200f, 1.4f, 0.30f, 4f, 120f, 120f),
            GlassToken.TINT_BLACK_HEAVY to listOf(72f, 80f, 1000f, 1.2f, 0.40f, 0f, 40f, 500f),
        )
        assertEquals(13, GlassToken.entries.size)
        expected.forEach { (token, v) ->
            assertEquals(v[0], token.edgePx, token.name)
            assertEquals(v[1], token.thicknessPx, token.name)
            assertEquals(v[2], token.reflectOffsetPx, token.name)
            assertEquals(v[3], token.saturation, token.name)
            assertEquals(v[4], token.darker, token.name)
            assertEquals(v[5], token.bgColorSaturation, token.name)
            assertEquals(v[6], token.smallBlurPx, token.name)
            assertEquals(v[7], token.bigBlurPx, token.name)

            val s = token.spec.style
            assertEquals(v[0], s.edgeWidth, token.name)
            assertEquals(v[1], s.thickness, token.name)
            assertEquals(v[6], s.smallBlurRadius, token.name)
            assertEquals(v[7], s.bigBlurRadius, token.name)
            assertEquals(v[3], s.saturation, token.name)
            assertEquals(1.4f, s.lightAngleRange, token.name)
            assertEquals(Triple(-0.4f, 0.6f, -0.8f), s.lightDirection, token.name)
            assertEquals(listOf(0f, 2f, 0.5f, 0.8f), s.luminanceValues, token.name)
            assertEquals(0.6f to 1f, s.darkerRange, token.name)
            assertEquals(3f, s.refractIOR, token.name)
        }
    }

    @Test
    fun tokenPresetsAreIndependentFromNexio() {
        val large = GlassPreset.of(GlassToken.COMMON_LARGE_REGULAR).style
        assertEquals(50f, large.edgeWidth)
        assertEquals(80f, large.thickness)
        assertEquals(2.2f, large.saturation)
        assertNotEquals(GlassPreset.Nexio.style.edgeWidth, large.edgeWidth)
        assertEquals(GlassToken.COMMON_LARGE_REGULAR.spec, GlassPreset.of(GlassToken.COMMON_LARGE_REGULAR))
    }

    @Test
    fun chromeStyleFollowsMaterialLevel() {
        val soft = GlassPreset.Nexio.chromeStyle(GlassMaterialLevel.SOFT, isDark = false)
        val frosted = GlassPreset.Nexio.chromeStyle(GlassMaterialLevel.FROSTED, isDark = false)
        assertEquals(1f, soft.refractionScale)
        assertTrue(soft.edgeHighlight)
        assertEquals(0f, frosted.refractionScale)
        assertFalse(frosted.edgeHighlight)
        assertEquals(0.08f, soft.darker)
    }
}
