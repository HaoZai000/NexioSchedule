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
fun rememberCombinedBackdrop(backdrop1: GlassBackdrop, backdrop2: GlassBackdrop): GlassBackdrop =
    remember(backdrop1, backdrop2) { Combined2Backdrops(backdrop1, backdrop2) }

@Composable
fun rememberCombinedBackdrop(
    backdrop1: GlassBackdrop,
    backdrop2: GlassBackdrop,
    backdrop3: GlassBackdrop,
): GlassBackdrop = remember(backdrop1, backdrop2, backdrop3) {
    Combined3Backdrops(backdrop1, backdrop2, backdrop3)
}

@Composable
fun rememberCombinedBackdrop(vararg backdrops: GlassBackdrop): GlassBackdrop = remember(*backdrops) {
    CombinedBackdrops(*backdrops)
}

@Immutable
private class Combined2Backdrops(
    private val backdrop1: GlassBackdrop,
    private val backdrop2: GlassBackdrop,
) : GlassBackdrop {

    override val isCoordinatesDependent: Boolean =
        backdrop1.isCoordinatesDependent || backdrop2.isCoordinatesDependent

    override val contentVersion: Int
        get() = backdrop1.contentVersion * 31 + backdrop2.contentVersion

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        with(backdrop1) { drawBackdrop(density, coordinates, layerBlock) }
        with(backdrop2) { drawBackdrop(density, coordinates, layerBlock) }
    }
}

@Immutable
private class Combined3Backdrops(
    private val backdrop1: GlassBackdrop,
    private val backdrop2: GlassBackdrop,
    private val backdrop3: GlassBackdrop,
) : GlassBackdrop {

    override val isCoordinatesDependent: Boolean =
        backdrop1.isCoordinatesDependent ||
            backdrop2.isCoordinatesDependent ||
            backdrop3.isCoordinatesDependent

    override val contentVersion: Int
        get() = (backdrop1.contentVersion * 31 + backdrop2.contentVersion) * 31 +
            backdrop3.contentVersion

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        with(backdrop1) { drawBackdrop(density, coordinates, layerBlock) }
        with(backdrop2) { drawBackdrop(density, coordinates, layerBlock) }
        with(backdrop3) { drawBackdrop(density, coordinates, layerBlock) }
    }
}

@Immutable
private class CombinedBackdrops(vararg private val backdrops: GlassBackdrop) : GlassBackdrop {

    override val isCoordinatesDependent: Boolean = backdrops.any { it.isCoordinatesDependent }

    override val contentVersion: Int
        get() {
            var version = 1
            for (backdrop in backdrops) version = version * 31 + backdrop.contentVersion
            return version
        }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        backdrops.forEach { backdrop ->
            with(backdrop) { drawBackdrop(density, coordinates, layerBlock) }
        }
    }
}
