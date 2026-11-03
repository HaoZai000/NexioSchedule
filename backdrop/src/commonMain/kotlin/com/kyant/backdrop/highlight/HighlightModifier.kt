package com.kyant.backdrop.highlight

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.RuntimeShaderCacheImpl
import com.kyant.backdrop.internal.ShapeProvider
import com.kyant.backdrop.internal.blur
import com.kyant.backdrop.internal.clipOutline
import com.kyant.backdrop.internal.setRuntimeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.ceil

internal class HighlightElement(
    val shapeProvider: ShapeProvider,
    val highlight: () -> Highlight?
) : ModifierNodeElement<HighlightNode>() {

    override fun create(): HighlightNode {
        return HighlightNode(shapeProvider, highlight)
    }

    override fun update(node: HighlightNode) {
        node.shapeProvider = shapeProvider
        node.highlight = highlight
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "highlight"
        properties["shapeProvider"] = shapeProvider
        properties["highlight"] = highlight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HighlightElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (highlight != other.highlight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + highlight.hashCode()
        return result
    }
}

internal class HighlightNode(
    var shapeProvider: ShapeProvider,
    var highlight: () -> Highlight?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var highlightLayer: GraphicsLayer? = null

    private val paint =
        Paint().apply {
            style = PaintingStyle.Stroke
        }
    private var clipPath: Path? = null

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    private var prevStyle: HighlightStyle? = null

    // 录制缓存（与 EdgeLightNode / ShadowNode 同一套判据）。高光是**静态装饰**：轮廓、描边宽度、
    // 模糊半径、颜色、样式不变时，录进图层的内容逐像素相同，重录纯属白烧。而它并不便宜——
    // `paint.blur()` 每次都要新建 BlurMaskFilter（native 对象），`createShader()` 还要重设一遍
    // AGSL uniform 并换一个 Shader 包装；本层随后在 GPU 上还要走一遍掩码模糊。
    // Compose 的重绘粒度是整棵节点树，所以只要页面上有任何一个动画在跑，这里就会每帧重跑一次。
    // 每帧仍需写的是 alpha / blendMode，它们是图层属性，不需要重录。
    private var needsRecord = true
    private var recordedOutline: Outline? = null
    private var recordedSafeSize: IntSize = IntSize.Zero
    private var recordedStrokeWidth = Float.NaN
    private var recordedBlurRadius = Float.NaN
    private var recordedColor: Color? = null
    private var recordedStyle: HighlightStyle? = null

    override fun ContentDrawScope.draw() {
        val highlight = highlight()
        if (highlight == null || highlight.width.value <= 0f) {
            return drawContent()
        }

        drawContent()

        val highlightLayer = highlightLayer
        if (highlightLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection

            val safeSize =
                IntSize(
                    ceil(size.width).toInt() + 2,
                    ceil(size.height).toInt() + 2
                )

            val outline = shapeProvider.shape.createOutline(size, layoutDirection, density)
            val clipPath =
                if (outline is Outline.Rounded) {
                    clipPath ?: Path().also { clipPath = it }
                } else {
                    null
                }

            val strokeWidth = strokeWidthPx(highlight)
            val blurRadius = highlight.blurRadius.toPx()
            val color = highlight.style.color
            val style = highlight.style

            val changed = needsRecord ||
                    outline !== recordedOutline ||
                    safeSize != recordedSafeSize ||
                    strokeWidth != recordedStrokeWidth ||
                    blurRadius != recordedBlurRadius ||
                    color != recordedColor ||
                    style != recordedStyle

            if (changed) {
                needsRecord = false
                recordedOutline = outline
                recordedSafeSize = safeSize
                recordedStrokeWidth = strokeWidth
                recordedBlurRadius = blurRadius
                recordedColor = color
                recordedStyle = style

                configurePaint(highlight, strokeWidth, blurRadius)

                highlightLayer.record(safeSize) {
                    translate(1f, 1f) {
                        val canvas = drawContext.canvas
                        canvas.save()
                        canvas.clipOutline(outline, clipPath)
                        canvas.drawOutline(outline, paint)
                        canvas.restore()
                    }
                }
            }

            highlightLayer.alpha = highlight.alpha
            highlightLayer.blendMode = highlight.style.blendMode

            translate(-1f, -1f) {
                drawLayer(highlightLayer)
            }
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer = graphicsContext.createGraphicsLayer()
        needsRecord = true
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            highlightLayer = null
        }
        clipPath = null
        runtimeShaderCache.clear()
        prevStyle = null
        needsRecord = true
        recordedOutline = null
        recordedSafeSize = IntSize.Zero
        recordedColor = null
        recordedStyle = null
    }

    /** 描边宽度（px）：与原实现同一算式——上限取短边一半，再 ×2 抵消「内外各半」的内侧裁切。 */
    private fun DrawScope.strokeWidthPx(highlight: Highlight): Float =
        ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f

    private fun DrawScope.configurePaint(
        highlight: Highlight,
        strokeWidth: Float,
        blurRadius: Float
    ) {
        paint.color = highlight.style.color
        paint.strokeWidth = strokeWidth
        paint.blur(blurRadius)
        if (isRuntimeShaderSupported()) {
            val shader =
                with(highlight.style) {
                    createShader(
                        shape = shapeProvider.shape,
                        runtimeShaderCache = runtimeShaderCache
                    )
                }
            paint.setRuntimeShader(shader)
        }
    }
}
