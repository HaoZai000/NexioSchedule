package com.kyant.backdrop.backdrops

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import com.kyant.backdrop.internal.recordLayer
import kotlin.math.roundToInt

/**
 * @param recordKey 被录制内容的指纹（壁纸 bitmap / 缩放 / 偏移 / 亮度 / 主题色等）。
 *   传了它且值没变时，说明这一帧录出来的内容和上一帧逐像素相同，可以整段跳过 ——
 *   滑动课表时壁纸是静止的，每帧重录一次全屏层纯属白烧。
 *   传 null（默认）保持原行为：每帧录制。
 *   注意：指纹必须覆盖所有能改变被录制内容的因素，否则会用到过期采样。
 */
fun Modifier.layerBackdrop(backdrop: LayerBackdrop, recordKey: Any? = null): Modifier =
    this then LayerBackdropElement(backdrop, recordKey)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop,
    val recordKey: Any? = null
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop, recordKey)
    }

    override fun update(node: LayerBackdropNode) {
        if (node.backdrop != backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
        }
        node.recordKey = recordKey
        node.markNeedsRecord()
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (recordKey != other.recordKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + (recordKey?.hashCode() ?: 0)
        return result
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop,
    var recordKey: Any? = null
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private var needsRecord = true
    private var recordedW = 0
    private var recordedH = 0

    fun markNeedsRecord() { needsRecord = true }

    override fun ContentDrawScope.draw() {
        drawContent()
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        if (needsRecord || recordedW != w || recordedH != h) {
            needsRecord = false
            recordedW = w
            recordedH = h
            recordLayer(this@LayerBackdropNode, backdrop.graphicsLayer) {
                backdrop.onDraw(this@draw)
            }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }

    override fun onDetach() {
        backdrop.layerCoordinates = null
    }
}
