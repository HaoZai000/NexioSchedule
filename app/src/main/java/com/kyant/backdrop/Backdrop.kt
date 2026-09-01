package com.kyant.backdrop

import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density

interface Backdrop {

    val isCoordinatesDependent: Boolean

    /**
     * 共享降采样+预模糊层。
     * 非 null 时，DrawBackdropNode 跳过独立壁纸录制，
     * 直接从此层采样对应区域，实现多卡片共享同一底采样图。
     */
    val sharedSampledLayer: GraphicsLayer? get() = null

    fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)? = null
    )
}
