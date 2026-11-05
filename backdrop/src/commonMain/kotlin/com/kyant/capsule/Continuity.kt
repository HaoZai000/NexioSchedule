package com.kyant.capsule

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import com.kyant.capsule.continuities.G2Continuity
import com.kyant.capsule.path.PathSegments
import com.kyant.capsule.path.toPath

@Immutable
interface Continuity {

    fun createRoundedRectanglePathSegments(
        width: Double,
        height: Double,
        topLeft: Double,
        topRight: Double,
        bottomRight: Double,
        bottomLeft: Double
    ): PathSegments

    fun createRoundedRectangleOutline(
        size: Size,
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float
    ): Outline {
        val path =
            createRoundedRectanglePathSegments(
                width = size.width.toDouble(),
                height = size.height.toDouble(),
                topLeft = topLeft.toDouble(),
                topRight = topRight.toDouble(),
                bottomRight = bottomRight.toDouble(),
                bottomLeft = bottomLeft.toDouble()
            ).toPath()
        return Outline.Generic(path)
    }

    fun createCapsuleOutline(size: Size): Outline {
        val radius = size.minDimension * 0.5f
        return createRoundedRectangleOutline(
            size = size,
            topLeft = radius,
            topRight = radius,
            bottomRight = radius,
            bottomLeft = radius
        )
    }

    fun lerp(stop: Continuity, fraction: Double): Continuity

    companion object {

        /**
         * 全应用默认圆角：kyant 原版 [G2Continuity]（圆弧 + 两段三次贝塞尔，G2 连续曲率）。
         *
         * **形状只有这一个真相**：卡片 / 按钮 / 底栏 / 胶囊的裁剪、描边、背景全走它，
         * 玻璃 shader 不参与定形，只按 [SdfG2Corner] 的圆弧 SDF 算倒角带与高光。
         *
         * ⚠ 别再把默认换成别的实现：曾换成 p 范数超椭圆（n=3.4），位置拟合得上但
         * **圆角发尖**、胶囊退化成普通圆角 clip，底栏一眼就能看出来。
         */
        @Stable
        val Default: Continuity = G2Continuity()
    }
}
