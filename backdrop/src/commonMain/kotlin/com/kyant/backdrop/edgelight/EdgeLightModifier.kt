package com.kyant.backdrop.edgelight

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
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
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.internal.blur
import com.kyant.backdrop.internal.clipOutline
import com.kyant.backdrop.internal.setRuntimeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.ceil

/**
 * outline 缓存按引用相等判定（`cachedOutlineShape === shape`），只能覆盖「换了个 Shape 对象」。
 * 形状内部参数在 draw 期变化时（底栏融球把圆钮并进胶囊），要靠 [EdgeLightElement.outlineRevision]
 * 变化才重建。必须是单例：写成 `= { 0L }` 会让既有调用方的 element 相等性失效。
 */
private val ZeroOutlineRevision: () -> Long = { 0L }

internal class EdgeLightElement(
    val shape: androidx.compose.ui.graphics.Shape,
    val edgeLight: () -> EdgeLight?,
    val outlineRevision: () -> Long = ZeroOutlineRevision
) : ModifierNodeElement<EdgeLightNode>() {

    override fun create(): EdgeLightNode {
        return EdgeLightNode(shape, edgeLight, outlineRevision)
    }

    override fun update(node: EdgeLightNode) {
        node.shape = shape
        node.edgeLight = edgeLight
        node.outlineRevision = outlineRevision
        node.invalidateOutlineCache()
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "edgeLight"
        properties["shape"] = shape
        properties["edgeLight"] = edgeLight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EdgeLightElement) return false
        if (shape != other.shape) return false
        if (edgeLight != other.edgeLight) return false
        if (outlineRevision != other.outlineRevision) return false
        return true
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + edgeLight.hashCode()
        result = 31 * result + outlineRevision.hashCode()
        return result
    }
}

internal class EdgeLightNode(
    var shape: androidx.compose.ui.graphics.Shape,
    var edgeLight: () -> EdgeLight?,
    var outlineRevision: () -> Long = ZeroOutlineRevision
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var edgeLightLayer: GraphicsLayer? = null

    private val paint =
        Paint().apply {
            style = PaintingStyle.Stroke
            // 光带是**内侧**描边（画完裁到轮廓里）。融球颈与本体相接的肩部是凹角，
            // 默认 Miter 会把外侧（也就是材质内侧）拉成一根楔形尖刺，四个肩部各一根，
            // 看起来就像描边长出了毛刺。圆角接头把它磨成小圆角，光带沿轮廓连续。
            strokeJoin = StrokeJoin.Round
        }
    private var clipPath: Path? = null

    private val shaderCache = EdgeLightShaderCache()

    private var cachedSafeSize: IntSize = IntSize(0, 0)
    private var cachedSize: Size? = null

    private var recordedSize: Size? = null
    private var recordedBlurRadius: Float = -1f
    private var recordedStrokeWidth: Float = -1f
    private var recordedColor: androidx.compose.ui.graphics.Color? = null
    private var recordedIntensity: Float = -1f
    private var recordedBlendMode: androidx.compose.ui.graphics.BlendMode? = null
    private var recordedShaderKey: String? = null
    private var recordedOutlineHashCode: Int = 0
    private var needsRecord = true

    // outline 只在 (shape, size, layoutDirection, revision) 变化时重建
    private var cachedOutline: Outline? = null
    private var cachedOutlineShape: Any? = null
    private var cachedOutlineSize: Size? = null
    private var cachedOutlineLayoutDirection: LayoutDirection? = null
    private var cachedOutlineRevision: Long = Long.MIN_VALUE

    fun invalidateOutlineCache() {
        cachedOutline = null
        cachedOutlineShape = null
        cachedOutlineSize = null
        cachedOutlineLayoutDirection = null
        cachedOutlineRevision = Long.MIN_VALUE
    }

    override fun ContentDrawScope.draw() {
        val edgeLight = edgeLight()
        if (edgeLight == null || edgeLight.width.value <= 0f) {
            return drawContent()
        }

        drawContent()

        val edgeLightLayer = edgeLightLayer
        if (edgeLightLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection

            val safeSize = if (cachedSize != size) {
                cachedSize = size
                cachedSafeSize = IntSize(
                    ceil(size.width).toInt() + 2,
                    ceil(size.height).toInt() + 2
                )
                cachedSafeSize
            } else {
                cachedSafeSize
            }

            val revision = outlineRevision()
            val outline =
                if (cachedOutline != null &&
                    cachedOutlineShape === shape &&
                    cachedOutlineSize == size &&
                    cachedOutlineLayoutDirection == layoutDirection &&
                    cachedOutlineRevision == revision
                ) {
                    cachedOutline!!
                } else {
                    shape.createOutline(size, layoutDirection, density).also {
                        cachedOutlineShape = shape
                        cachedOutlineSize = size
                        cachedOutlineLayoutDirection = layoutDirection
                        cachedOutlineRevision = revision
                        cachedOutline = it
                    }
                }

            val clipPath =
                if (outline is Outline.Rounded) {
                    clipPath ?: Path().also { clipPath = it }
                } else {
                    null
                }

            val strokeWidth = ceil(edgeLight.width.toPx().coerceAtMost(size.minDimension / 2f)) * 2f
            val blurRadius = edgeLight.blurRadius.toPx()
            val color = edgeLight.style.color
            val intensity = edgeLight.intensity
            val blendMode = edgeLight.style.blendMode
            val shaderKey = if (isRuntimeShaderSupported() && edgeLight.style !is EdgeLightStyle.Uniform) {
                edgeLight.style::class.simpleName
            } else null

            edgeLightLayer.alpha = 1f
            edgeLightLayer.blendMode = blendMode

            val outlineHashCode = outline.hashCode()
            val paramsChanged = recordedOutlineHashCode != outlineHashCode
                    || recordedSize != size
                    || recordedBlurRadius != blurRadius
                    || recordedStrokeWidth != strokeWidth
                    || recordedColor != color
                    || recordedIntensity != intensity
                    || recordedBlendMode != blendMode
                    || recordedShaderKey != shaderKey
                    || needsRecord

            if (paramsChanged) {
                recordedOutlineHashCode = outlineHashCode
                recordedSize = size
                recordedBlurRadius = blurRadius
                recordedStrokeWidth = strokeWidth
                recordedColor = color
                recordedIntensity = intensity
                recordedBlendMode = blendMode
                recordedShaderKey = shaderKey
                needsRecord = false

                // 只在真正重录的这一帧配置画笔：paint.blur() 每次都会新建 BlurMaskFilter（native 对象），
                // setRuntimeShader() 还要换一个 Shader 包装，参数没变时这些分配全是白做。
                configurePaint(edgeLight, strokeWidth, blurRadius)

                edgeLightLayer.record(safeSize) {
                    translate(1f, 1f) {
                        val canvas = drawContext.canvas
                        canvas.save()
                        canvas.clipOutline(outline, clipPath)
                        canvas.drawOutline(outline, paint)
                        canvas.restore()
                    }
                }
            }

            edgeLightLayer.alpha = intensity

            translate(-1f, -1f) {
                drawLayer(edgeLightLayer)
            }
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        edgeLightLayer = graphicsContext.createGraphicsLayer()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        edgeLightLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            edgeLightLayer = null
        }
        clipPath = null
        shaderCache.clear()
        needsRecord = true
    }

    // 描边模糊走本模块的 expect/actual（Android=RenderEffect，iOS/Desktop=SkSL），不用 android.graphics.BlurMaskFilter + asFrameworkPaint()——
    // 后者会把整个文件钉死在 Android，是当初进不了 backdrop 模块的唯一障碍。
    private fun DrawScope.configurePaint(
        edgeLight: EdgeLight,
        strokeWidth: Float,
        blurRadius: Float
    ) {
        paint.color = edgeLight.style.color

        paint.strokeWidth = strokeWidth

        paint.blur(blurRadius)

        if (isRuntimeShaderSupported() && edgeLight.style !is EdgeLightStyle.Uniform) {
            val shader = createEdgeLightShader(
                edgeLight = edgeLight,
                size = size,
                shape = shape,
                layoutDirection = layoutDirection,
                density = this,
                shaderCache = shaderCache
            )
            if (shader != null) {
                paint.setRuntimeShader(shader)
            }
        }
    }
}

fun Modifier.edgeLight(
    shape: androidx.compose.ui.graphics.Shape,
    edgeLight: EdgeLight = EdgeLight.Default,
    outlineRevision: () -> Long = ZeroOutlineRevision
): Modifier {
    return this.then(
        EdgeLightElement(
            shape = shape,
            edgeLight = { edgeLight },
            outlineRevision = outlineRevision,
        )
    )
}

fun Modifier.edgeLight(
    shape: androidx.compose.ui.graphics.Shape,
    edgeLight: () -> EdgeLight? = { EdgeLight.Default },
    outlineRevision: () -> Long = ZeroOutlineRevision
): Modifier {
    return this.then(
        EdgeLightElement(
            shape = shape,
            edgeLight = edgeLight,
            outlineRevision = outlineRevision,
        )
    )
}

fun Modifier.edgeLightIf(
    shape: androidx.compose.ui.graphics.Shape,
    condition: Boolean,
    edgeLight: EdgeLight = EdgeLight.Default
): Modifier {
    return this.then(
        EdgeLightElement(
            shape = shape,
            edgeLight = if (condition) {
                { edgeLight }
            } else {
                { null }
            }
        )
    )
}
