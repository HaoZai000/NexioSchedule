package com.haooz.chedule.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw

/**
 * 暂停这一层的绘制，但**保留它的组合与状态**。
 *
 * ## 覆盖范围
 *
 * 只挡 draw。**布局与重组不受影响** —— 二者本来是惰性的，没有 invalidate 就不会重跑，
 * 静止时本就没有开销；真正持续烧机的只有绘制。
 *
 * @param paused 在 draw 阶段求值，可安全读取快照状态（变化会自动触发失效重画）。
 */
fun Modifier.skipDrawWhen(paused: () -> Boolean): Modifier = this then SkipDrawElement(paused)

/** [SkipDrawElement.update] 里必须显式 [invalidateDraw] */
private data class SkipDrawElement(
    private val paused: () -> Boolean,
) : ModifierNodeElement<SkipDrawNode>() {
    override fun create() = SkipDrawNode(paused)
    override fun update(node: SkipDrawNode) {
        node.paused = paused
        node.invalidateDraw()
    }
}

private class SkipDrawNode(
    var paused: () -> Boolean,
) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        if (!paused()) drawContent()
    }
}
