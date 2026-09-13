package com.kyant.backdrop.backdrops

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
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
 * @param mustRecord draw 阶段强制录制谓词。返回 true 时即使 recordKey 未变也重录。
 *   用于「结构稳定但滚动/动画仍在改像素」的主内容 liquidGlass：谓词里读 ScrollState/Animatable，
 *   不进组合，滚动帧只多一次比较。引用须 remember 稳定，否则每次重组 update→markNeedsRecord，跳过失效。
 */
fun Modifier.layerBackdrop(
    backdrop: LayerBackdrop,
    recordKey: Any? = null,
    mustRecord: (() -> Boolean)? = null
): Modifier =
    this then LayerBackdropElement(backdrop, recordKey, mustRecord)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop,
    val recordKey: Any? = null,
    val mustRecord: (() -> Boolean)? = null
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop, recordKey, mustRecord)
    }

    override fun update(node: LayerBackdropNode) {
        if (node.backdrop != backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
        }
        node.recordKey = recordKey
        node.mustRecord = mustRecord
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
        // mustRecord 按引用比较：remember 出来的稳定 lambda 才能让 equals 为 true
        if (mustRecord !== other.mustRecord) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + (recordKey?.hashCode() ?: 0)
        result = 31 * result + (mustRecord?.hashCode() ?: 0)
        return result
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop,
    var recordKey: Any? = null,
    var mustRecord: (() -> Boolean)? = null
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private var needsRecord = true
    private var recordedW = 0
    private var recordedH = 0

    fun markNeedsRecord() { needsRecord = true }

    override fun ContentDrawScope.draw() {
        drawContent()
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        // recordKey == null：内容可能每帧变化（滚动中的课表/顶栏），必须每帧重录。
        // 之前误写成只在 needsRecord/尺寸变化时录，与文档「null = 每帧录制」不一致，
        // 会导致采样层停在旧帧，和当帧内容对不齐，慢滑时看起来发闪。
        // mustRecord：recordKey 非空但滚动/动画仍在改像素时强制重录（draw 阶段读，不触发组合）。
        val force = mustRecord?.invoke() == true
        val shouldRecord = recordKey == null || force || needsRecord || recordedW != w || recordedH != h
        if (shouldRecord) {
            needsRecord = false
            recordedW = w
            recordedH = h
            recordLayer(this@LayerBackdropNode, backdrop.graphicsLayer) {
                backdrop.onDraw(this@draw)
            }
            backdrop.contentVersion++
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            val prev = backdrop.layerCoordinates
            // LayoutCoordinates 通常按实例比较 equals。位置/尺寸未变时不要换实例，
            // 否则下游读 layerCoordinates 的 draw 节点会被无意义地整批 invalidate。
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
