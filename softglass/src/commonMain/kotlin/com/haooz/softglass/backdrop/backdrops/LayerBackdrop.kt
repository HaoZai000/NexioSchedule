package com.haooz.softglass.backdrop.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.internal.InverseLayerScope

private val DefaultOnDraw: ContentDrawScope.() -> Unit = { drawContent() }

/** @param recordScale 采样层录制分辨率比例；纯采样层可降到 1 以下省 blit 面积，上屏层不要降。 */
@Composable
fun rememberLayerBackdrop(
    graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
    recordScale: Float = 1f,
    onDraw: ContentDrawScope.() -> Unit = DefaultOnDraw,
): LayerBackdrop = remember(graphicsLayer, recordScale, onDraw) {
    LayerBackdrop(graphicsLayer, recordScale, onDraw)
}

@Stable
class LayerBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    val recordScale: Float = 1f,
    internal val onDraw: ContentDrawScope.() -> Unit,
) : GlassBackdrop {

    internal val contentOnlyCapture: Boolean = onDraw === DefaultOnDraw

    override val isCoordinatesDependent: Boolean = true

    override var contentVersion: Int = 0
        internal set

    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)

    private var inverseLayerScope: InverseLayerScope? = null

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val coordinates = coordinates ?: return
        val layerCoordinates = layerCoordinates ?: return
        withTransform({
            if (layerBlock != null) {
                with(obtainInverseLayerScope()) { inverseTransform(density, layerBlock) }
            }
            val offset = try {
                layerCoordinates.localPositionOf(coordinates)
            } catch (_: Exception) {
                coordinates.positionInWindow() - layerCoordinates.positionInWindow()
            }
            translate(-offset.x, -offset.y)
            if (recordScale != 1f) {
                scale(1f / recordScale, 1f / recordScale, Offset.Zero)
            }
        }) {
            drawLayer(graphicsLayer)
        }
    }

    private fun obtainInverseLayerScope(): InverseLayerScope =
        inverseLayerScope?.apply { reset() } ?: InverseLayerScope().also { inverseLayerScope = it }
}
