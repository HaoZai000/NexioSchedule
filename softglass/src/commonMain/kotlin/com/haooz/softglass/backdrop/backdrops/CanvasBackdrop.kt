package com.haooz.softglass.backdrop.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.haooz.softglass.backdrop.GlassBackdrop

@Composable
fun rememberCanvasBackdrop(onDraw: DrawScope.() -> Unit): GlassBackdrop = remember(onDraw) {
    CanvasBackdrop(onDraw)
}

@Immutable
private class CanvasBackdrop(private val onDraw: DrawScope.() -> Unit) : GlassBackdrop {

    override val isCoordinatesDependent: Boolean = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        onDraw()
    }
}
