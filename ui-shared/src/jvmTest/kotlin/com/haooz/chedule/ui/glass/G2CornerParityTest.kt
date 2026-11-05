// Copyright (C) 2026 Haooz · AGPL-3.0-only
//
// 形状与 shader 光学距离的一致性测试。
//
// 可见轮廓由 G2 Path 裁剪，shader 只提供圆弧 SDF 供倒角带与高光计算。

package com.haooz.chedule.ui.glass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import com.kyant.capsule.Continuity
import com.kyant.capsule.SdfG2Corner
import com.kyant.capsule.continuities.G2Continuity
import com.kyant.capsule.path.PathSegment
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 圆角口径的看门测试。
 *
 * 约束：可见形状只由 G2 Path 决定，玻璃 shader 不参与定形。
 * shader 中复刻 G2 曲线或改用 n>2 的超椭圆，会使角部曲率不连续并出现尖角；
 * 胶囊退化为原生 RoundRect 则失去 G2 连续曲率。本组断言用于防止上述回归。
 */
class G2CornerParityTest {

    /** 默认形状为 kyant 原版 G2（圆弧 + 两段三次贝塞尔，曲率 G2 连续）。 */
    @Test
    fun defaultShapeIsOriginalG2Continuity() {
        assertTrue(Continuity.Default is G2Continuity)
    }

    /** 几何 SDF 只允许标准圆弧，不含超椭圆 / p 范数实现。 */
    @Test
    fun shaderDefinesNoShapeOnlyOptics() {
        assertTrue(SOFT_GLASS_SDF.contains("float sdRoundRect("))
        assertTrue(SOFT_GLASS_SDF.contains("float2 roundRectGrad("))
        for (forbidden in listOf("g2Pnorm", "g2Ratio", "uCornerExt", "uCornerPow", "G2_EXT", "G2_POW_BONUS")) {
            assertTrue(!SOFT_GLASS_SDF.contains(forbidden), "shader 里又冒出第二套圆角：$forbidden")
        }
    }

    /**
     * 玻璃圆弧边界恒不比 G2 更圆（`y_glass ≤ y_g2`）：
     * 玻璃铺满整个 G2 形状，多出部分被裁剪，角上不会露出底色缝。
     * 参考点取自 kyant G2 路径的逐点采样。
     */
    @Test
    fun glassArcNeverCutsInsideG2Shape() {
        val r = 20.0
        val g2Reference = listOf(
            0.000 to 29.464, 3.822 to 8.277, 7.643 to 4.291, 11.465 to 2.008,
            15.287 to 0.776, 19.108 to 0.241, 22.930 to 0.054, 26.751 to 0.006,
            30.573 to 0.000,
        )
        for ((x, yG2) in g2Reference) {
            val yGlass = SdfG2Corner.boundaryY(x, r)
            assertTrue(
                yGlass <= yG2 + 0.05,
                "x=$x 处玻璃比 G2 更圆（玻璃=$yGlass, G2=$yG2）⇒ 角上会露缝",
            )
        }
    }

    /**
     * 45° 对角点：圆弧与 G2 路径均为 `r(1-1/√2) = 0.2929r`，
     * 因此倒角带与方向光仍落在相同位置。
     */
    @Test
    fun highlightDiagonalStaysAtG2Position() {
        val r = 20.0
        val diagonal = r * (1.0 - 1.0 / sqrt(2.0))
        val g2Diagonal = 5.857864  // kyant G2 真实路径在 45° 上的采样值
        assertTrue(abs(SdfG2Corner.boundaryY(diagonal, r) - g2Diagonal) < 0.05)
    }

    /** 胶囊保持 G2 连续曲率路径，不退化成原生 RoundRect。 */
    @Test
    fun capsuleKeepsG2ContinuousCurvature() {
        val segments = G2Continuity().createCapsulePathSegments(137.0, 42.0)
        assertTrue(segments.any { it is PathSegment.Cubic }, "胶囊丢了 G2 的贝塞尔肩部")
        assertTrue(segments.any { it is PathSegment.Arc }, "胶囊丢了 G2 的圆弧")

        val outline = G2Continuity().createCapsuleOutline(Size(137f, 42f))
        assertTrue(outline is Outline.Generic, "胶囊被降级成 Outline.Rounded（普通圆角 clip）")
    }

    /** Kotlin 版距离函数与 shader 的 sdRoundRect 自洽：形状中心为负、角外为正。 */
    @Test
    fun kotlinDistanceMatchesShaderSdf() {
        val halfW = 100.0
        val halfH = 50.0
        val r = 20.0
        assertTrue(SdfG2Corner.distance(0.0, 0.0, halfW, halfH, r) < 0.0)
        assertTrue(SdfG2Corner.distance(-halfW, -halfH, halfW, halfH, r) > 0.0)
        // 圆心 (r,r)、半径 r 上的点应落在零等值线
        val d = SdfG2Corner.distance(-halfW + r, -halfH + r - r, halfW, halfH, r)
        assertTrue(abs(d) < 1e-6, "圆弧上的点不在零等值线：$d")
    }
}
