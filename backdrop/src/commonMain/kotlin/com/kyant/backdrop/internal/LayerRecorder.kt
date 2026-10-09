package com.kyant.backdrop.internal

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize

/**
 * 把内容录制进 [layer]，返回是否录制成功。
 *
 * Activity / Dialog / BottomSheet 拆除时，节点可能已经 detach 但仍被绘制一帧
 * （最后一帧的绘制与onDetach 交错）。此时 `requireDensity()` 与
 * `drawContext.canvas` 会抛 `IllegalStateException: LayoutNode should be attached
 * to an owner`，而采样层只是玻璃/模糊等装饰效果，录不成不该让整个应用崩掉。
 *
 * 因此这里统一兜住拆除期的竞态，返回 false 让调用方退回直绘内容。
 */
internal fun DrawScope.recordLayer(
    node: DelegatableNode,
    layer: GraphicsLayer,
    size: IntSize = this.size.toIntSize(),
    block: DrawScope.() -> Unit
): Boolean = runCatching {
    val density = node.requireDensity()
    layer.record(size) {
        val prevDensity = drawContext.density
        drawContext.density = density
        try {
            this.block()
        } finally {
            drawContext.density = prevDensity
        }
    }
}.isSuccess