package com.haooz.softglass.backdrop

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density

/** 玻璃的采样源。 */
interface GlassBackdrop {

    val isCoordinatesDependent: Boolean

    val contentVersion: Int get() = 0

    fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    )
}
