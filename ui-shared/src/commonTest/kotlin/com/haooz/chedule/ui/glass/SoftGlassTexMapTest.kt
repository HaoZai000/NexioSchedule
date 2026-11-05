// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.glass

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 屏幕坐标 → 壁纸纹理坐标的映射。
 *
 * 这条仿射是玻璃唯一的几何来源：映射错了整块玻璃会「滑走」或缩放不对，而且因为模糊着，
 * 肉眼很难判断是映射错还是壁纸本来就那样。这里按壁纸层实际绘制的公式做往返校验。
 */
class SoftGlassTexMapTest {

    private val screenW = 1080f
    private val screenH = 2400f
    private val texW = 540   // 与屏幕同比例的半分辨率纹理
    private val texH = 1200

    private fun map(
        screenX: Float,
        screenY: Float,
        scale: Float = 1f,
        offset: Offset = Offset.Zero,
    ): Pair<Float, Float> {
        val m = wallpaperTexMap(texW, texH, scale, offset, screenW, screenH)
        return (screenX * m[0] + m[2]) to (screenY * m[1] + m[3])
    }

    private fun assertNear(expected: Float, actual: Float, label: String) {
        assertTrue(
            abs(expected - actual) < 0.01f,
            "$label: 期望 $expected，实际 $actual",
        )
    }

    @Test
    fun noScaleMapsScreenToTextureLinearly() {
        val (cx, cy) = map(screenW / 2f, screenH / 2f)
        assertNear(texW / 2f, cx, "屏幕中心 x")
        assertNear(texH / 2f, cy, "屏幕中心 y")

        val (ox, oy) = map(0f, 0f)
        assertNear(0f, ox, "左上 x")
        assertNear(0f, oy, "左上 y")

        val (ex, ey) = map(screenW, screenH)
        assertNear(texW.toFloat(), ex, "右下 x")
        assertNear(texH.toFloat(), ey, "右下 y")
    }

    @Test
    fun zoomSamplesInnerRegion() {
        // 放大 2 倍：屏幕左上角对应纹理的 1/4 处，中心仍对齐中心
        val (ox, oy) = map(0f, 0f, scale = 2f)
        assertNear(texW / 4f, ox, "放大后左上 x")
        assertNear(texH / 4f, oy, "放大后左上 y")

        val (cx, cy) = map(screenW / 2f, screenH / 2f, scale = 2f)
        assertNear(texW / 2f, cx, "放大后中心 x")
        assertNear(texH / 2f, cy, "放大后中心 y")
    }

    @Test
    fun offsetShiftsSamplingOppositeToContent() {
        // 壁纸内容右移 100px ⇒ 采样点要左移 100px（纹理与屏幕同比例，即 50 texel）
        val (x, _) = map(screenW / 2f, screenH / 2f, offset = Offset(100f, 0f))
        assertNear(texW / 2f - 50f, x, "偏移后 x")
    }
}
