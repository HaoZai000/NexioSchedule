// Copyright (C) 2026 Haooz · AGPL-3.0-only
//
// 屏幕坐标、纹理坐标与采样原点的几何映射测试。

package com.haooz.chedule.ui.glass

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 屏幕坐标与壁纸纹理坐标之间的仿射映射。
 *
 * 该映射是玻璃采样唯一的几何来源；偏差会表现为内容位移或缩放错误，
 * 且模糊后的内容不易直接判断。此处按壁纸层绘制公式做往返校验。
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
        // 放大 2 倍：屏幕左上角对应纹理的 1/4 处，中心保持对齐
        val (ox, oy) = map(0f, 0f, scale = 2f)
        assertNear(texW / 4f, ox, "放大后左上 x")
        assertNear(texH / 4f, oy, "放大后左上 y")

        val (cx, cy) = map(screenW / 2f, screenH / 2f, scale = 2f)
        assertNear(texW / 2f, cx, "放大后中心 x")
        assertNear(texH / 2f, cy, "放大后中心 y")
    }

    @Test
    fun offsetShiftsSamplingOppositeToContent() {
        // 壁纸内容右移 100px 时，采样点相应左移 100px（纹理为屏幕半分辨率，即 50 texel）
        val (x, _) = map(screenW / 2f, screenH / 2f, offset = Offset(100f, 0f))
        assertNear(texW / 2f - 50f, x, "偏移后 x")
    }

    @Test
    fun identityTransformKeepsOrigin() {
        // 静止态：graphicsLayer 不产生变换，origin 原样返回且不引入浮点误差
        val p = Offset(137f, 892f)
        val out = untransformGlassOrigin(p, 1f, 0f, screenW / 2f, screenH / 2f)
        assertNear(p.x, out.x, "恒等 x")
        assertNear(p.y, out.y, "恒等 y")
    }

    @Test
    fun originTransformInvertsMainContentLayer() {
        // 主内容层缩放 0.65、绕非中心 pivot 并带位移时，
        // positionInWindow() 返回变换后的坐标，需能还原回壁纸层逻辑坐标。
        val scale = 0.65f
        val translationY = 90f
        val pivotX = 0.42f * screenW
        val pivotY = 0.58f * screenH
        val logic = Offset(233f, 1407f)
        val window = Offset(
            (logic.x - pivotX) * scale + pivotX,
            (logic.y - pivotY) * scale + pivotY + translationY,
        )
        val back = untransformGlassOrigin(window, scale, translationY, pivotX, pivotY)
        assertNear(logic.x, back.x, "逆变换 x")
        assertNear(logic.y, back.y, "逆变换 y")
    }
}
