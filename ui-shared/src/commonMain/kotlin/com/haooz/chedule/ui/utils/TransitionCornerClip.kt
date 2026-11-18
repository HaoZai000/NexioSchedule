package com.haooz.chedule.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.LayoutDirection

/**
 * 转场中间态才把页面裁成 [shape]，到位后恢复直角
 */
internal fun Modifier.clipDuringTransition(shape: Shape?, inFlight: () -> Boolean): Modifier =
    if (shape == null) this else this then TransitionClipElement(shape, inFlight)

/**
 * 同 [clipDuringTransition]，但**常裁**（不按转场中间态开关）。
 */
internal fun Modifier.clipScreenCorners(shape: Shape?): Modifier =
    clipDuringTransition(shape) { true }

private data class TransitionClipElement(
    private val shape: Shape,
    private val inFlight: () -> Boolean,
) : ModifierNodeElement<TransitionClipNode>() {
    override fun create() = TransitionClipNode(shape, inFlight)
    override fun update(node: TransitionClipNode) {
        node.shape = shape
        node.inFlight = inFlight
    }
}

private class TransitionClipNode(
    var shape: Shape,
    var inFlight: () -> Boolean,
) : Modifier.Node(), DrawModifierNode {
    private val path = Path()

    /** Path 缓存键 */
    private var cachedShape: Shape? = null
    private var cachedWidth = -1f
    private var cachedHeight = -1f
    private var cachedLayoutDirection: LayoutDirection? = null

    // inFlight() 在这里读进度 → 只 invalidate draw，不会触发重组
    override fun ContentDrawScope.draw() {
        if (!inFlight() || size.isEmpty()) {
            drawContent()
            return
        }
        if (cachedShape !== shape ||
            cachedWidth != size.width ||
            cachedHeight != size.height ||
            cachedLayoutDirection != layoutDirection
        ) {
            path.rewind()
            path.addOutline(shape.createOutline(size, layoutDirection, this))
            cachedShape = shape
            cachedWidth = size.width
            cachedHeight = size.height
            cachedLayoutDirection = layoutDirection
        }
        // clipPath 的 block receiver 是 DrawScope，drawContent() 得指名外层
        clipPath(path) { this@draw.drawContent() }
    }
}
