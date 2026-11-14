package com.kyant.backdrop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightElement
import com.kyant.backdrop.internal.ShapeProvider
import com.kyant.backdrop.internal.recordLayer
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.InnerShadowElement
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.ShadowElement
import kotlin.concurrent.Volatile

private val DefaultHighlight = { Highlight.Default }
private val DefaultShadow = { Shadow.Default }
private val DefaultOnDrawBackdrop: DrawScope.(DrawScope.() -> Unit) -> Unit = { it() }

/**
 * 采样视口（window 坐标 px）：完全在视口外的节点跳过 backdrop 采样（被父级裁掉、不可见，纯赚），采样仍 1:1
 * 全分辨率，只是少录外层课卡的 display list。写入侧是滚动容器、读取侧在 draw 阶段，故不能用 state（否则每帧
 * 滚动触发整棵重组）；[bounds] 用单个 volatile 引用整体替换而非 4 个字段，避免读侧看到「新 top + 旧 bottom」
 * 撕裂组合而误剔可见卡片。null = 视口未知，不剔除。
 */
class BackdropViewport {
    class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

    // 必须用 kotlin.concurrent.Volatile：裸 @Volatile 依赖 JVM 专有导入 kotlin.jvm.*，在 Native/JS 上「Unresolved reference」；该字段在 draw 阶段读取，需真正的跨线程可见性。
    @Volatile
    var bounds: Bounds? = null

    fun update(left: Float, top: Float, right: Float, bottom: Float) {
        bounds = Bounds(left, top, right, bottom)
    }

    /** 四边界均严格在视口外才剔除；相等视为相交（贴边卡片保留）。 */
    fun isFullyOutside(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        val b = bounds ?: return false
        return right < b.left || left > b.right || bottom < b.top || top > b.bottom
    }
}

/** @see BackdropViewport */
val LocalBackdropViewport = androidx.compose.runtime.staticCompositionLocalOf<BackdropViewport?> { null }

fun Modifier.drawPlainBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null
): Modifier {
    val shapeProvider = ShapeProvider(shape)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                viewport = null,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront
            )
        )
}

fun Modifier.drawBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    highlight: (() -> Highlight?)? = DefaultHighlight,
    shadow: (() -> Shadow?)? = DefaultShadow,
    innerShadow: (() -> InnerShadow?)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    viewport: BackdropViewport? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null
): Modifier {
    val shapeProvider = ShapeProvider(shape)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            if (innerShadow != null) {
                InnerShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = innerShadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (shadow != null) {
                ShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = shadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (highlight != null) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = highlight
                )
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                viewport = viewport,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront
            )
        )
}

private class DrawBackdropElement(
    val backdrop: Backdrop,
    val shapeProvider: ShapeProvider,
    val effects: BackdropEffectScope.() -> Unit,
    val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    val exportedBackdrop: LayerBackdrop?,
    val viewport: BackdropViewport?,
    val onDrawBehind: (DrawScope.() -> Unit)?,
    val onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    val onDrawSurface: (DrawScope.() -> Unit)?,
    val onDrawFront: (DrawScope.() -> Unit)?
) : ModifierNodeElement<DrawBackdropNode>() {

    override fun create(): DrawBackdropNode {
        return DrawBackdropNode(
            backdrop = backdrop,
            shapeProvider = shapeProvider,
            effects = effects,
            layerBlock = layerBlock,
            exportedBackdrop = exportedBackdrop,
            viewport = viewport,
            onDrawBehind = onDrawBehind,
            onDrawBackdrop = onDrawBackdrop,
            onDrawSurface = onDrawSurface,
            onDrawFront = onDrawFront
        )
    }

    override fun update(node: DrawBackdropNode) {
        node.backdrop = backdrop
        node.shapeProvider = shapeProvider
        node.effects = effects
        node.layerBlock = layerBlock
        if (node.exportedBackdrop != exportedBackdrop) {
            node.exportedBackdrop?.layerCoordinates = null
            node.exportedBackdrop = exportedBackdrop
        }
        node.viewport = viewport
        node.onDrawBehind = onDrawBehind
        node.onDrawBackdrop = onDrawBackdrop
        node.onDrawSurface = onDrawSurface
        node.onDrawFront = onDrawFront
        node.invalidateDrawCache()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drawBackdrop"
        properties["backdrop"] = backdrop
        properties["shapeProvider"] = shapeProvider
        properties["effects"] = effects
        properties["layerBlock"] = layerBlock
        properties["exportedBackdrop"] = exportedBackdrop
        properties["viewport"] = viewport
        properties["onDrawBehind"] = onDrawBehind
        properties["onDrawSurface"] = onDrawSurface
        properties["onDrawFront"] = onDrawFront
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (shapeProvider.innerShape != other.shapeProvider.innerShape) return false
        if (effects != other.effects) return false
        if (layerBlock != other.layerBlock) return false
        if (exportedBackdrop != other.exportedBackdrop) return false
        if (viewport !== other.viewport) return false
        if (onDrawBehind != other.onDrawBehind) return false
        if (onDrawBackdrop != other.onDrawBackdrop) return false
        if (onDrawSurface != other.onDrawSurface) return false
        if (onDrawFront != other.onDrawFront) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + shapeProvider.innerShape.hashCode()
        result = 31 * result + effects.hashCode()
        result = 31 * result + (layerBlock?.hashCode() ?: 0)
        result = 31 * result + (exportedBackdrop?.hashCode() ?: 0)
        result = 31 * result + (viewport?.hashCode() ?: 0)
        result = 31 * result + (onDrawBehind?.hashCode() ?: 0)
        result = 31 * result + onDrawBackdrop.hashCode()
        result = 31 * result + (onDrawSurface?.hashCode() ?: 0)
        result = 31 * result + (onDrawFront?.hashCode() ?: 0)
        return result
    }
}

private class DrawBackdropNode(
    var backdrop: Backdrop,
    var shapeProvider: ShapeProvider,
    var effects: BackdropEffectScope.() -> Unit,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    var exportedBackdrop: LayerBackdrop?,
    var viewport: BackdropViewport?,
    var onDrawBehind: (DrawScope.() -> Unit)?,
    var onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    var onDrawSurface: (DrawScope.() -> Unit)?,
    var onDrawFront: (DrawScope.() -> Unit)?
) : LayoutModifierNode, DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode, Modifier.Node() {

    private val effectScope =
        object : BackdropEffectScopeImpl() {

            override val shape: Shape get() = shapeProvider.innerShape
        }

    private var graphicsLayer: GraphicsLayer? = null

    // 外形裁剪路径缓存：尺寸/圆角未变时不重建 Path
    private var clipPathCache: androidx.compose.ui.graphics.Path? = null
    private var clipPathRadius = Float.NaN
    private var clipPathW = Float.NaN
    private var clipPathH = Float.NaN

    /**
     * 采样坐标。**不能是 `neverEqualPolicy` 的 snapshot state**（见下方 onGloballyPositioned）。
     * draw 在 layout 之后执行，读到的必是本次布局结果，所以普通字段足够。
     */
    private var layoutCoordinates: LayoutCoordinates? = null

    /** 上次上报的 window 位置/尺寸，只用于判断「真的动了没」，静止时避免无谓重绘。 */
    private var lastWindowX = Float.NaN
    private var lastWindowY = Float.NaN
    private var lastSize = IntSize.Zero

    private var padding by mutableFloatStateOf(0f)

    private val recordBackdropBlock: (DrawScope.() -> Unit) = {
        val canvas = drawContext.canvas
        val padding = padding

        if (padding != 0f) {
            canvas.translate(padding, padding)
        }
        onDrawBackdrop {
            with(backdrop) {
                drawBackdrop(
                    density = effectScope,
                    coordinates = layoutCoordinates,
                    layerBlock = layerBlock
                )
            }
        }
        if (padding != 0f) {
            canvas.translate(-padding, -padding)
        }
    }

    private val drawBackdropLayer: DrawScope.() -> Unit = drawBackdropLayer@{
        // 视口剔除：完全在视口外的节点整段跳过采样（像素被父级裁掉、不可见，纯赚）；卡片表面色/文字仍由 draw() 照常画，视觉零差异——省的是 display list 录制与 localPositionOf，非 GPU 光栅化。
        val viewport = viewport
        if (viewport != null) {
            val coordinates = layoutCoordinates
            if (coordinates != null) {
                val pos = try {
                    coordinates.positionInWindow()
                } catch (_: Exception) {
                    null
                }
                if (pos != null && viewport.isFullyOutside(
                        pos.x, pos.y,
                        pos.x + size.width, pos.y + size.height
                    )
                ) {
                    return@drawBackdropLayer
                }
            }
        }
        val layer = graphicsLayer
        val padding = padding

        // 直采：无 RenderEffect 也无 padding 时，录进离屏缓冲再画出只是白搬一次，直接在当前 canvas 采样。
        if (layer == null || (padding == 0f && layer.renderEffect == null)) {
            val canvas = drawContext.canvas
            canvas.save()
            // 先按卡片边界裁剪，避免把整张源/烘焙层扫一遍
            canvas.clipRect(0f, 0f, size.width, size.height)
            onDrawBackdrop {
                with(backdrop) {
                    drawBackdrop(
                        density = effectScope,
                        coordinates = layoutCoordinates,
                        layerBlock = layerBlock
                    )
                }
            }
            canvas.restore()
            return@drawBackdropLayer
        }

        recordLayer(
            this@DrawBackdropNode,
            layer,
            size = IntSize(
                size.width.toInt() + padding.toInt() * 2,
                size.height.toInt() + padding.toInt() * 2
            ),
            block = recordBackdropBlock
        )

        layer.topLeft =
            if (padding != 0f) IntOffset(-padding.toInt(), -padding.toInt())
            else IntOffset.Zero
        drawLayer(layer)
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        // draw 阶段按外形 clipPath：无额外合成层，也不强制离屏。
        return layout(placeable.width, placeable.height) {
            placeable.place(IntOffset.Zero)
        }
    }

    override fun ContentDrawScope.draw() {
        if (effectScope.update(this)) {
            updateEffects()
        }

        val contentScope = this
        val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
        when (outline) {
            is androidx.compose.ui.graphics.Outline.Rectangle ->
                clipRect(outline.rect.left, outline.rect.top, outline.rect.right, outline.rect.bottom) {
                    contentScope.drawCardContents()
                }
            is androidx.compose.ui.graphics.Outline.Rounded -> {
                val rr = outline.roundRect
                // Path 重建不便宜：宽高/圆角未变时复用
                val w = size.width
                val h = size.height
                val radius = rr.topLeftCornerRadius.x
                var path = clipPathCache
                if (path == null || clipPathW != w || clipPathH != h || clipPathRadius != radius) {
                    path = androidx.compose.ui.graphics.Path().apply { addRoundRect(rr) }
                    clipPathCache = path
                    clipPathW = w
                    clipPathH = h
                    clipPathRadius = radius
                }
                clipPath(path) { contentScope.drawCardContents() }
            }
            is androidx.compose.ui.graphics.Outline.Generic ->
                clipPath(outline.path) { contentScope.drawCardContents() }
        }
    }

    private fun ContentDrawScope.drawCardContents() {
        onDrawBehind?.invoke(this)
        drawBackdropLayer()
        onDrawSurface?.invoke(this)
        drawContent()
        onDrawFront?.invoke(this)

        exportedBackdrop?.graphicsLayer?.let { layer ->
            recordLayer(this@DrawBackdropNode, layer) {
                onDrawBehind?.invoke(this)
                drawBackdropLayer()
                onDrawSurface?.invoke(this)
                onDrawFront?.invoke(this)
            }
        }
    }

    /**
     * 采样坐标 + 重绘判据。
     *
     * 为什么不给 [layoutCoordinates] 挂 snapshot state：`onGloballyPositioned` 在动画/滚动期间每帧回调，
     * 早先用 `mutableStateOf(null, neverEqualPolicy())`（neverEqual ⇒ 每次赋值都算「变了」），
     * 而 draw 期又读它，等于**每帧主动失效 draw**。Compose 的绘制失效粒度是整棵 LayoutNode 树，
     * 结果每帧全量重画所有页所有卡（实测切 tab：draw 段 39.8ms → 改后 3.0ms）。
     *
     * 但只比「对象引用」也不行：竖向滚动 / pager 平移走 `graphicsLayer` 位移，只改矩阵不重新布局，
     * 坐标对象始终是同一个 ⇒ 采样层拿旧偏移，表现为玻璃里的壁纸「冻结」或在切页时错位。
     * 所以判据换成**真实 window 位置 + 尺寸**：只有数值变了才重绘，静止时零开销。
     */
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
        exportedBackdrop?.layerCoordinates = coordinates
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

        effectScope.apply(effects)
        val effect = effectScope.renderEffect
        // 仅当需离屏运算（RenderEffect）或外扩（padding）时才建层；直采路径不碰离屏缓冲，几十张课卡省下等量 GraphicsLayer 的创建与释放。
        if (effect != null || effectScope.padding != 0f) {
            obtainGraphicsLayer().renderEffect = effect
        } else {
            graphicsLayer?.renderEffect = null
        }
        padding = effectScope.padding
    }

    private fun obtainGraphicsLayer(): GraphicsLayer {
        return graphicsLayer
            ?: requireGraphicsContext().createGraphicsLayer().also { graphicsLayer = it }
    }

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
        exportedBackdrop?.layerCoordinates = null
        clipPathCache = null
    }
}
