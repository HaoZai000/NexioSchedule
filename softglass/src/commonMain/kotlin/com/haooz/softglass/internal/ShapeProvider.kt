package com.haooz.softglass.internal

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** 缓存 shape 的 Outline，尺寸 / 密度 / 布局方向不变时不重建。 */
@Immutable
internal class ShapeProvider(val shapeBlock: () -> Shape) {

    private var cachedShape: Shape? = null
    private var cachedOutline: Outline? = null
    private var cachedSize: Size = Size.Unspecified
    private var cachedLayoutDirection: LayoutDirection? = null
    private var cachedDensity: Float? = null

    val innerShape: Shape get() = shapeBlock()

    val shape: Shape = object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val shape = shapeBlock()
            if (cachedShape != shape) {
                cachedShape = shape
                cachedOutline = null
            }
            if (cachedOutline == null || cachedSize != size ||
                cachedLayoutDirection != layoutDirection || cachedDensity != density.density
            ) {
                cachedSize = size
                cachedLayoutDirection = layoutDirection
                cachedDensity = density.density
                cachedOutline = shape.createOutline(size, layoutDirection, density)
            }
            return cachedOutline!!
        }
    }
}
