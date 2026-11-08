package com.haooz.softglass.backdrop.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.haooz.softglass.internal.ShapeProvider
import com.haooz.softglass.internal.blur
import com.haooz.softglass.internal.recordLayer
import kotlin.math.ceil

/**
 * 投影绘制节点：按形状录一层模糊轮廓，再用 Clear 挖掉内部，只留外圈落在玻璃下方。
 *
 * 形状与玻璃共用同一个 [ShapeProvider]，轮廓永远贴着玻璃边，不需要调用方另传形状。
 */
internal class ShadowElement(
    val shapeProvider: ShapeProvider,
    val shadow: () -> Shadow?,
) : ModifierNodeElement<ShadowNode>() {

    override fun create(): ShadowNode = ShadowNode(shapeProvider, shadow)

    override fun update(node: ShadowNode) {
        node.shapeProvider = shapeProvider
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "shadow"
        properties["shapeProvider"] = shapeProvider
        properties["shadow"] = shadow
    }

    // 与 drawBackdrop 一致按形状值比较：每次组合都会新建 ShapeProvider 实例，
    // 比引用会让每个玻璃件每帧重录投影层。
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShadowElement) return false
        if (shapeProvider.innerShape != other.shapeProvider.innerShape) return false
        if (shadow != other.shadow) return false
        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.innerShape.hashCode()
        result = 31 * result + shadow.hashCode()
        return result
    }
}

internal class ShadowNode(
    var shapeProvider: ShapeProvider,
    var shadow: () -> Shadow?,
) : DrawModifierNode, Modifier.Node() {

    // 投影自带一层离屏录制的图层，不需要框架再帮忙无效化。
    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null

    private val paint = Paint()

    private var needsRecord = true
    private var recordedOutline: Outline? = null
    private var recordedSize = IntSize.Zero
    private var recordedRadius = Float.NaN
    private var recordedOffsetX = Float.NaN
    private var recordedOffsetY = Float.NaN
    private var recordedColor: Color? = null

    override fun ContentDrawScope.draw() {
        val shadow = shadow() ?: return drawContent()

        val layer = shadowLayer
        if (layer != null) {
            val density: Density = this
            val radius = shadow.radius.toPx()
            val offsetX = shadow.offset.x.toPx()
            val offsetY = shadow.offset.y.toPx()
            // 画布按模糊半径外扩，模糊才不会在边界被截断。
            val shadowSize = IntSize(
                ceil(size.width + radius * 4f + offsetX).toInt(),
                ceil(size.height + radius * 4f + offsetY).toInt(),
            )
            val outline = shapeProvider.shape.createOutline(size, layoutDirection, density)

            val changed = needsRecord ||
                outline !== recordedOutline ||
                shadowSize != recordedSize ||
                radius != recordedRadius ||
                offsetX != recordedOffsetX ||
                offsetY != recordedOffsetY ||
                shadow.color != recordedColor

            if (changed) {
                needsRecord = false
                recordedOutline = outline
                recordedSize = shadowSize
                recordedRadius = radius
                recordedOffsetX = offsetX
                recordedOffsetY = offsetY
                recordedColor = shadow.color

                configurePaint(shadow)

                recordLayer(this@ShadowNode, layer, shadowSize) {
                    translate(radius * 2f + offsetX, radius * 2f + offsetY) {
                        val canvas = drawContext.canvas
                        canvas.drawOutline(outline, paint)
                        canvas.translate(-offsetX, -offsetY)
                        canvas.drawOutline(outline, shadowMaskPaint)
                        canvas.translate(offsetX, offsetY)
                    }
                }
            }

            layer.alpha = shadow.alpha
            layer.blendMode = shadow.blendMode

            translate(-radius * 2f, -radius * 2f) {
                drawLayer(layer)
            }
        }

        drawContent()
    }

    override fun onAttach() {
        shadowLayer = requireGraphicsContext().createGraphicsLayer().apply {
            // Clear 挖空要求离屏合成。
            compositingStrategy = CompositingStrategy.Offscreen
        }
        // 新图层是空的，必须放弃上一轮（同节点重挂）的缓存判据。
        needsRecord = true
    }

    override fun onDetach() {
        shadowLayer?.let { layer ->
            requireGraphicsContext().releaseGraphicsLayer(layer)
            shadowLayer = null
        }
        needsRecord = true
        recordedOutline = null
        recordedSize = IntSize.Zero
        recordedColor = null
    }

    private fun DrawScope.configurePaint(shadow: Shadow) {
        paint.color = shadow.color
        paint.blur(shadow.radius.toPx())
    }
}

/** 挖掉投影中间的原始轮廓，只留模糊外圈。 */
private val shadowMaskPaint = Paint().apply {
    blendMode = BlendMode.Clear
}
