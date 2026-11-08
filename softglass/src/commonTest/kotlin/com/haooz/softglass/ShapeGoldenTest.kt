package com.haooz.softglass

import com.haooz.softglass.shape.G2Profile
import com.haooz.softglass.shape.SdfG2Corner
import com.haooz.softglass.shape.lerp
import kotlin.test.Test
import kotlin.test.assertEquals

class ShapeGoldenTest {

    @Test
    fun roundRectDistanceMatchesShaderFormula() {
        assertEquals(-50.0, SdfG2Corner.distance(0.0, 0.0, 100.0, 50.0, 20.0), 1e-9)
        assertEquals(0.0, SdfG2Corner.distance(100.0, 0.0, 100.0, 50.0, 20.0), 1e-9)
        assertEquals(30.0, SdfG2Corner.distance(120.0, 60.0, 100.0, 50.0, 20.0), 1e-9)
        assertEquals(20.0, SdfG2Corner.boundaryY(0.0, 20.0), 1e-9)
        assertEquals(0.0, SdfG2Corner.boundaryY(20.0, 20.0), 1e-9)
        assertEquals(2.6794919243112277, SdfG2Corner.boundaryY(10.0, 20.0), 1e-9)
    }

    @Test
    fun g2ProfilesKeepGeometryConstants() {
        val rect = G2Profile.RoundedRectangle
        assertEquals(0.5286651, rect.extendedFraction, 1e-9)
        assertEquals(5.0 / 9.0, rect.arcFraction, 1e-9)
        assertEquals(1.0732051, rect.bezierCurvatureScale, 1e-9)
        assertEquals(-0.5286651, rect.baseBezier.p0.x, 1e-9)
        assertEquals(0.0, rect.baseBezier.p0.y, 1e-9)

        val capsule = G2Profile.Capsule
        assertEquals(0.5286651 * 0.75, capsule.extendedFraction, 1e-9)
        assertEquals(0.0, capsule.arcFraction, 1e-9)
        assertEquals(-0.5286651 * 0.75, capsule.baseBezier.p0.x, 1e-9)

        val circular = G2Profile.Circular
        assertEquals(0.0, circular.extendedFraction, 1e-9)
        assertEquals(1.0, circular.arcFraction, 1e-9)
        assertEquals(0.0, circular.baseBezier.p0.x, 1e-9)
    }

    @Test
    fun profileLerpIsLinear() {
        val mid = lerp(G2Profile.Circular, G2Profile.RoundedRectangle, 0.5)
        assertEquals(0.5286651 * 0.5, mid.extendedFraction, 1e-9)
        assertEquals((1.0 + 5.0 / 9.0) * 0.5, mid.arcFraction, 1e-9)
        assertEquals(0.25, lerp(0.0, 1.0, 0.25), 1e-9)
    }

    @Test
    fun shaderSourceKeepsOpticalFunctions() {
        val source = SdfG2Corner.shaderSource
        assertEquals(true, source.contains("float sdRoundRect("))
        assertEquals(true, source.contains("float2 roundRectGrad("))
        assertEquals(true, source.contains("float radiusAt("))
    }
}
