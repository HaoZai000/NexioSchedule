package com.haooz.softglass.backdrop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.haooz.softglass.backdrop.shadow.Shadow
import com.haooz.softglass.backdrop.shadow.ShadowElement
import com.haooz.softglass.internal.ShapeProvider
import com.haooz.softglass.internal.recordLayer
import com.haooz.softglass.platform.isRenderEffectSupported

private val DefaultShadow = { Shadow.Default }

/**
 * 采样 + 效果链 + 按形状裁剪的玻璃表面。
 *
 * [shadow] 默认开一盏柔和投影，传 `null` 关掉。
 */
fun Modifier.drawBackdrop(
    backdrop: GlassBackdrop,
    shape: () -> Shape,
    effects: GlassEffectScope.() -> Unit,
    shadow: (() -> Shadow?)? = DefaultShadow,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
): Modifier {
    // layerBlock 既是采样层的逆变换来源，也是节点自身的图层变换（按钮随滚动渐显靠 alpha）
    val base = if (layerBlock != null) graphicsLayer(layerBlock) else this
    // 投影与玻璃共用一份形状，轮廓才能严丝合缝。
    val shapeProvider = ShapeProvider(shape)
    val surface = if (shadow != null) base.then(ShadowElement(shapeProvider, shadow)) else base
    return surface then DrawBackdropElement(
        backdrop = backdrop,
        shapeProvider = shapeProvider,
        effects = effects,
        layerBlock = layerBlock,
        onDrawSurface = onDrawSurface,
    )
}

private class DrawBackdropElement(
    val backdrop: GlassBackdrop,
    val shapeProvider: ShapeProvider,
    val effects: GlassEffectScope.() -> Unit,
    val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    val onDrawSurface: (DrawScope.() -> Unit)?,
) : ModifierNodeElement<DrawBackdropNode>() {

    override fun create(): DrawBackdropNode = DrawBackdropNode(
        backdrop, shapeProvider, effects, layerBlock, onDrawSurface,
    )

    override fun update(node: DrawBackdropNode) {
        node.backdrop = backdrop
        node.shapeProvider = shapeProvider
        node.effects = effects
        node.layerBlock = layerBlock
        node.onDrawSurface = onDrawSurface
        node.invalidateDrawCache()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drawBackdrop"
        properties["backdrop"] = backdrop
        properties["shapeProvider"] = shapeProvider
        properties["effects"] = effects
        properties["layerBlock"] = layerBlock
        properties["onDrawSurface"] = onDrawSurface
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawBackdropElement) return false
        if (backdrop != other.backdrop) return false
        if (shapeProvider.innerShape != other.shapeProvider.innerShape) return false
        if (effects != other.effects) return false
        if (layerBlock != other.layerBlock) return false
        if (onDrawSurface != other.onDrawSurface) return false
        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + shapeProvider.innerShape.hashCode()
        result = 31 * result + effects.hashCode()
        result = 31 * result + (layerBlock?.hashCode() ?: 0)
        result = 31 * result + (onDrawSurface?.hashCode() ?: 0)
        return result
    }
}

private class DrawBackdropNode(
    var backdrop: GlassBackdrop,
    var shapeProvider: ShapeProvider,
    var effects: GlassEffectScope.() -> Unit,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    var onDrawSurface: (DrawScope.() -> Unit)?,
) : LayoutModifierNode, DrawModifierNode, GlobalPositionAwareModifierNode,
    ObserverModifierNode, Modifier.Node() {

    private val effectScope = object : GlassEffectScopeImpl() {
        override val shape: Shape get() = shapeProvider.innerShape
    }

    private var graphicsLayer: GraphicsLayer? = null

    private var clipPathCache: Path? = null
    private var clipPathRadius = Float.NaN
    private var clipPathW = Float.NaN
    private var clipPathH = Float.NaN

    private var layoutCoordinates: LayoutCoordinates? = null
    private var lastWindowX = Float.NaN
    private var lastWindowY = Float.NaN
    private var lastSize = IntSize.Zero

    private var padding by mutableFloatStateOf(0f)

    private val recordBackdropBlock: DrawScope.() -> Unit = {
        val canvas = drawContext.canvas
        val padding = padding
        if (padding != 0f) canvas.translate(padding, padding)
        with(backdrop) {
            drawBackdrop(density = effectScope, coordinates = layoutCoordinates, layerBlock = layerBlock)
        }
        if (padding != 0f) canvas.translate(-padding, -padding)
    }

    private val drawBackdropLayer: DrawScope.() -> Unit = drawBackdropLayer@{
        val layer = graphicsLayer
        val padding = padding

        // 无效果、无外扩时直接在当前 canvas 采样，不建离屏层。
        if (layer == null || (padding == 0f && layer.renderEffect == null)) {
            val canvas = drawContext.canvas
            canvas.save()
            canvas.clipRect(0f, 0f, size.width, size.height)
            with(backdrop) {
                drawBackdrop(density = effectScope, coordinates = layoutCoordinates, layerBlock = layerBlock)
            }
            canvas.restore()
            return@drawBackdropLayer
        }

        recordLayer(
            this@DrawBackdropNode,
            layer,
            size = IntSize(
                size.width.toInt() + padding.toInt() * 2,
                size.height.toInt() + padding.toInt() * 2,
            ),
            block = recordBackdropBlock,
        )
        layer.topLeft = if (padding != 0f) IntOffset(-padding.toInt(), -padding.toInt()) else IntOffset.Zero
        drawLayer(layer)
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.place(IntOffset.Zero)
        }
    }

    override fun ContentDrawScope.draw() {
        if (effectScope.update(this)) updateEffects()

        val contentScope = this
        when (val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)) {
            is Outline.Rectangle -> clipRect(
                outline.rect.left, outline.rect.top, outline.rect.right, outline.rect.bottom,
            ) { contentScope.drawCardContents() }

            is Outline.Rounded -> {
                val roundRect = outline.roundRect
                val w = size.width
                val h = size.height
                val radius = roundRect.topLeftCornerRadius.x
                var path = clipPathCache
                if (path == null || clipPathW != w || clipPathH != h || clipPathRadius != radius) {
                    path = Path().apply { addRoundRect(roundRect) }
                    clipPathCache = path
                    clipPathW = w
                    clipPathH = h
                    clipPathRadius = radius
                }
                clipPath(path) { contentScope.drawCardContents() }
            }

            is Outline.Generic -> clipPath(outline.path) { contentScope.drawCardContents() }
        }
    }

    private fun ContentDrawScope.drawCardContents() {
        drawBackdropLayer()
        onDrawSurface?.invoke(this)
        drawContent()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        if (backdrop.isCoordinatesDependent) {
            layoutCoordinates = coordinates
            val pos = try {
                coordinates.positionInWindow()
            } catch (_: Exception) {
                null
            }
            if (pos != null && (pos.x != lastWindowX || pos.y != lastWindowY)) {
                lastWindowX = pos.x
                lastWindowY = pos.y
                invalidateDraw()
            }
            val size = coordinates.size
            if (size != lastSize) {
                lastSize = size
                invalidateDraw()
            }
        } else if (layoutCoordinates != null) {
            layoutCoordinates = null
            invalidateDraw()
        }
    }

    override fun onObservedReadsChanged() {
        invalidateDrawCache()
    }

    fun invalidateDrawCache() {
        observeEffects()
    }

    private fun observeEffects() {
        observeReads { updateEffects() }
    }

    private fun updateEffects() {
        if (!isRenderEffectSupported()) {
            graphicsLayer?.renderEffect = null
            padding = 0f
            return
        }
        effectScope.applyEffects(effects)
        val effect = effectScope.renderEffect
        if (effect != null || effectScope.padding != 0f) {
            obtainGraphicsLayer().renderEffect = effect
        } else {
            graphicsLayer?.renderEffect = null
        }
        padding = effectScope.padding
    }

    private fun obtainGraphicsLayer(): GraphicsLayer =
        graphicsLayer ?: requireGraphicsContext().createGraphicsLayer().also { graphicsLayer = it }

    override fun onAttach() {
        observeEffects()
    }

    override fun onDetach() {
        graphicsLayer?.let { layer ->
            requireGraphicsContext().releaseGraphicsLayer(layer)
            graphicsLayer = null
        }
        effectScope.reset()
        layoutCoordinates = null
        clipPathCache = null
    }
}
