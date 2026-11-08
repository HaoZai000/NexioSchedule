package com.haooz.softglass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.haooz.softglass.backdrop.GlassEffectScopeImpl
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.colorControls
import com.haooz.softglass.internal.colorControlsColorMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private class TestEffectScope : GlassEffectScopeImpl() {
    override val shape: Shape = RectangleShape
}

class EffectChainGoldenTest {

    @Test
    fun colorControlsMatrixMatchesReferenceValues() {
        val controls = colorControlsColorMatrix(brightness = 0.1f, contrast = 1.2f, saturation = 1f)
        assertEquals((0.5f - 1.2f * 0.5f + 0.1f) * 255f, controls[4])
        assertEquals(1.2f, controls[0])
        assertEquals(1f, controls[18])

        val sat = colorControlsColorMatrix(saturation = 1.5f)
        assertEquals(1.5f + 0.213f * -0.5f, sat[0])
        assertEquals(0.715f * -0.5f, sat[1])
        assertEquals(1.5f + 0.715f * -0.5f, sat[6])
    }

    @Test
    fun effectChainBuildsBlurAndColorControls() {
        val scope = TestEffectScope()
        scope.size = Size(200f, 100f)
        scope.applyEffects {
            colorControls(saturation = 1.5f)
            blur(12f)
        }
        assertNotNull(scope.renderEffect)
        assertEquals(12f, scope.padding)
    }

    @Test
    fun effectScopeResetClearsState() {
        val scope = TestEffectScope()
        scope.size = Size(200f, 100f)
        scope.applyEffects { blur(8f) }
        scope.reset()
        assertEquals(Size.Unspecified, scope.size)
        assertEquals(0f, scope.padding)
        assertEquals(null, scope.renderEffect)
    }
}
