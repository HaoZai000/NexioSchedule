package com.kyant.shapes

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * fork 自 io.github.kyant0:shapes 的 RoundedRectangularShape，
 * 仅保留 backdrop 的 Lens 效果所需的最小接口（corners）。
 */
@Immutable
interface RoundedRectangularShape : Shape {

    fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Corners

    data class Corners(
        val topLeft: Float,
        val topRight: Float,
        val bottomRight: Float,
        val bottomLeft: Float
    )
}