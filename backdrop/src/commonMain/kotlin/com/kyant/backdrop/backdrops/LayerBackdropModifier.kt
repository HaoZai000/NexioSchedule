package com.kyant.backdrop.backdrops

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.internal.recordLayer
import kotlin.math.roundToInt

/**
 * @param recordKey 被录制内容的指纹（壁纸 bitmap / 缩放 / 偏移 / 亮度 / 主题色等）。值未变时本帧录制内容与上帧
 *   逐像素相同，可整段跳过（滑动课表时壁纸静止，每帧重录全屏层纯属白烧）；传 null（默认）每帧录制。指纹须覆盖
 *   所有能改变被录制内容的因素，否则会用到过期采样。
 * @param mustRecord draw 阶段强制录制谓词，返回 true 时即使 recordKey 未变也重录。用于「结构稳定但滚动/动画仍在
 *   改像素」的主内容 liquidGlass：谓词读 ScrollState/Animatable，不进组合，滚动帧只多一次比较；引用须 remember
 *   稳定，否则每次重组 update→markNeedsRecord，跳过失效。
 */
fun Modifier.layerBackdrop(
    backdrop: LayerBackdrop,
    recordKey: Any? = null,
    mustRecord: (() -> Boolean)? = null,
    recordScale: Float = backdrop.recordScale
): Modifier =
    this then LayerBackdropElement(backdrop, recordKey, mustRecord, recordScale)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop,
    val recordKey: Any? = null,
    val mustRecord: (() -> Boolean)? = null,
    val recordScale: Float = backdrop.recordScale
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop, recordKey, mustRecord, recordScale)
    }

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
        // mustRecord 按引用比较：remember 出来的稳定 lambda 才能让 equals 为 true
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
    var recordScale: Float = backdrop.recordScale
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private var needsRecord = true
    private var recordedW = 0
    private var recordedH = 0

    fun markNeedsRecord() { needsRecord = true }

    override fun ContentDrawScope.draw() {
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        // recordKey == null：内容可能每帧变化（滚动中的课表/顶栏），必须每帧重录——曾误写成只在 needsRecord/尺寸变化时录，导致采样层停在旧帧、慢滑发闪。
        // mustRecord：recordKey 非空但滚动/动画仍在改像素时强制重录（draw 阶段读，不触发组合）。
        val force = mustRecord?.invoke() == true
        val shouldRecord = recordKey == null || force || needsRecord || recordedW != w || recordedH != h
        if (shouldRecord) {
            needsRecord = false
            recordedW = w
            recordedH = h
            // recordScale < 1 时录进更小的层（block 内 canvas 缩放），采样侧按 1/rs 放大补偿；contentOnlyCapture 上屏 blit 该层也需放大回全屏，否则只覆盖左上 1/(rs²)。
            val rs = recordScale.coerceIn(0.1f, 1f)
            // 先录进图层：contentOnly 时上屏直接 blit 该层，内容树只走一遍；否则上屏仍 drawContent()（内容走两遍，但保证像素一致）。
            // 拆除期节点可能已 detach，recordLayer 返回 false 时退回直绘内容。
            val recorded = recordLayer(
                this@LayerBackdropNode,
                backdrop.graphicsLayer,
                size = IntSize((w * rs).roundToInt().coerceAtLeast(1), (h * rs).roundToInt().coerceAtLeast(1))
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
        if (coordinates.isAttached) {
            val prev = backdrop.layerCoordinates
            // LayoutCoordinates 通常按实例比较 equals。位置/尺寸未变时不要换实例，否则下游 draw 节点会被无意义整批 invalidate。
            val changed = prev == null ||
                prev.isAttached != coordinates.isAttached ||
                prev.positionInWindow() != coordinates.positionInWindow() ||
                prev.size != coordinates.size
            if (changed) {
                backdrop.layerCoordinates = coordinates
            }
        }
    }

    override fun onDetach() {
        backdrop.layerCoordinates = null
    }
}
