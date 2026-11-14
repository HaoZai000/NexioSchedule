package com.kyant.backdrop

import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density

interface Backdrop {

    val isCoordinatesDependent: Boolean

    /** 预烘焙的模糊结果层；drawLayer 时不再做模糊运算。非 null 时实现应在 [drawBackdrop] 画它而非原始源层。 */
    val blurredLayer: GraphicsLayer? get() = null

    /** 源内容版本号。仅保留兼容：采样链路回退到源库实现（每帧独立录制），不再用于跳过判断。 */
    val contentVersion: Int get() = 0

    fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)? = null
    )
}
