package com.haooz.softglass.internal

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize

/** 把内容录进 [layer]，返回是否成功；拆除期节点可能已 detach，失败时由调用方退回直绘。 */
internal fun DrawScope.recordLayer(
    node: DelegatableNode,
    layer: GraphicsLayer,
    size: IntSize = this.size.toIntSize(),
    block: DrawScope.() -> Unit,
): Boolean = runCatching {
    val density = node.requireDensity()
    layer.record(size) {
        val previous = drawContext.density
        drawContext.density = density
        try {
            block()
        } finally {
            drawContext.density = previous
        }
    }
}.isSuccess
