package com.haooz.softglass.backdrop.backdrops

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import com.haooz.softglass.internal.recordLayer
import kotlin.math.roundToInt

/**
 * @param recordKey 被录制内容的指纹，值未变时跳过录制；null 表示每帧录制。
 * @param mustRecord draw 阶段强制录制谓词，用于指纹稳定但滚动/动画仍在改像素的情况。
 */
fun Modifier.layerBackdrop(
    backdrop: LayerBackdrop,
    recordKey: Any? = null,
    mustRecord: (() -> Boolean)? = null,
    recordScale: Float = backdrop.recordScale,
): Modifier = this then LayerBackdropElement(backdrop, recordKey, mustRecord, recordScale)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop,
    val recordKey: Any? = null,
    val mustRecord: (() -> Boolean)? = null,
    val recordScale: Float = backdrop.recordScale,
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode = LayerBackdropNode(backdrop, recordKey, mustRecord, recordScale)

    override fun update(node: LayerBackdropNode) {
        if (node.backdrop != backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
        }
        node.recordKey = recordKey
        node.mustRecord = mustRecord
        if (node.recordScale != recordScale) {
            node.recordScale = recordScale
            node.markNeedsRecord()
        }
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
        if (recordScale != other.recordScale) return false
        if (mustRecord !== other.mustRecord) return false
        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + (recordKey?.hashCode() ?: 0)
        result = 31 * result + recordScale.hashCode()
        result = 31 * result + (mustRecord?.hashCode() ?: 0)
        return result
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop,
    var recordKey: Any? = null,
    var mustRecord: (() -> Boolean)? = null,
    var recordScale: Float = backdrop.recordScale,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private var needsRecord = true
    private var recordedW = 0
    private var recordedH = 0

    fun markNeedsRecord() {
        needsRecord = true
    }

    override fun ContentDrawScope.draw() {
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        val force = mustRecord?.invoke() == true
        val shouldRecord = recordKey == null || force || needsRecord || recordedW != w || recordedH != h
        if (shouldRecord) {
            needsRecord = false
            recordedW = w
            recordedH = h
            val rs = recordScale.coerceIn(0.1f, 1f)
            val recorded = recordLayer(
                this@LayerBackdropNode,
                backdrop.graphicsLayer,
                size = IntSize(
                    (w * rs).roundToInt().coerceAtLeast(1),
                    (h * rs).roundToInt().coerceAtLeast(1),
                ),
            ) {
                val canvas = drawContext.canvas
                if (rs != 1f) {
                    canvas.save()
                    canvas.scale(rs, rs)
                    backdrop.onDraw(this@draw)
                    canvas.restore()
                } else {
                    backdrop.onDraw(this@draw)
                }
            }
            if (recorded) {
                backdrop.contentVersion++
                if (backdrop.contentOnlyCapture) {
                    if (rs != 1f) {
                        withTransform({ scale(1f / rs, 1f / rs, Offset.Zero) }) {
                            drawLayer(backdrop.graphicsLayer)
                        }
                    } else {
                        drawLayer(backdrop.graphicsLayer)
                    }
                    return
                }
            }
        }
        drawContent()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        val prev = backdrop.layerCoordinates
        val changed = prev == null ||
            prev.isAttached != coordinates.isAttached ||
            prev.positionInWindow() != coordinates.positionInWindow() ||
            prev.size != coordinates.size
        if (changed) backdrop.layerCoordinates = coordinates
    }

    override fun onDetach() {
        backdrop.layerCoordinates = null
    }
}
