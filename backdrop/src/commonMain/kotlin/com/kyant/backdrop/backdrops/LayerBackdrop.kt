package com.kyant.backdrop.backdrops

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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.internal.InverseLayerScope

private val DefaultOnDraw: ContentDrawScope.() -> Unit = { drawContent() }

@Composable
fun rememberLayerBackdrop(
    graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
    /**
     * 采样层的录制分辨率比例。1f = 与节点同尺寸；< 1 时录进更小的层，采样侧按 1/recordScale 放大回全屏坐标。
     * 适合「纯采样层」（contentOnlyCapture = false，录完不上屏、上屏走 drawContent）：采样 blit 面积随比例平方下降。
     * ⚠ 上屏也走这个层的（contentOnlyCapture = true）不要降——blit 放大的就是用户直接看的内容。
     */
    recordScale: Float = 1f,
    onDraw: ContentDrawScope.() -> Unit = DefaultOnDraw
): LayerBackdrop {
    return remember(graphicsLayer, recordScale, onDraw) {
        LayerBackdrop(graphicsLayer, recordScale, onDraw)
    }
}

@Stable
class LayerBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    /** 录制分辨率比例，见 [rememberLayerBackdrop]。 */
    val recordScale: Float = 1f,
    internal val onDraw: ContentDrawScope.() -> Unit
) : Backdrop {

    /**
     * onDraw 是否仅为 drawContent()（或等价物）。为 true 时录制后直接把图层画上屏，内容树只走一遍；
     * 为 false 时 onDraw 可能额外画背景等，上屏仍走 drawContent()，录制层单独走 onDraw。
     */
    internal val contentOnlyCapture: Boolean = onDraw === DefaultOnDraw

    override val isCoordinatesDependent: Boolean = true

    override var contentVersion: Int = 0
        internal set

    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)

    private var inverseLayerScope: InverseLayerScope? = null

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val coordinates = coordinates ?: return
        val layerCoordinates = layerCoordinates ?: return
        withTransform({
            if (layerBlock != null) {
                with(obtainInverseLayerScope()) { inverseTransform(density, layerBlock) }
            }
            val offset =
                try {
                    layerCoordinates.localPositionOf(coordinates)
                } catch (_: Exception) {
                    // TODO: outer transformations lead to wrong position calculation
                    coordinates.positionInWindow() - layerCoordinates.positionInWindow()
                }
            // 先平移到壁纸层原点，再按 1/recordScale 放大回全屏坐标（矩阵右乘下 T 在 S 左侧）：layer 坐标 d（已缩 rs）
            // 经 d/rs 还原落在窗口 W + d/rs，逐像素对齐；顺序反了错位 offset·(1−rs)，rs=1 时恒等故旧顺序从不暴露。
            translate(-offset.x, -offset.y)
            if (recordScale != 1f) {
                scale(1f / recordScale, 1f / recordScale, Offset.Zero)
            }
        }) {
            drawLayer(graphicsLayer)
        }
    }

    private fun obtainInverseLayerScope(): InverseLayerScope {
        return inverseLayerScope?.apply { reset() }
            ?: InverseLayerScope().also { inverseLayerScope = it }
    }
}
