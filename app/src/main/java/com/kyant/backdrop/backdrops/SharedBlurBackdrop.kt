package com.kyant.backdrop.backdrops

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.DOWNSAMPLE_SCALE
import com.kyant.backdrop.internal.InverseLayerScope
import com.kyant.backdrop.isRenderEffectSupported
import kotlin.math.roundToInt

/**
 * 共享预模糊 Backdrop。
 *
 * 包含一个预渲染的降采样+模糊 GraphicsLayer（sharedSampledLayer）。
 * DrawBackdropNode 检测到此属性后，跳过独立壁纸录制，从此层采样，
 * 实现多卡片共享同一底采样图。
 *
 * @param source 原始壁纸 LayerBackdrop（用于降级和坐标获取）
 */
@Stable
class SharedBlurBackdrop(
    internal val source: LayerBackdrop
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    /**
     * 共享降采样+预模糊层。
     * 由预渲染 Modifier 在壁纸变化时填充。
     * DrawBackdropNode 通过 Backdrop.sharedSampledLayer 读取。
     */
    override var sharedSampledLayer: GraphicsLayer? by mutableStateOf(null)
        internal set

    /**
     * 共享层实际使用的降采样比例。
     * DrawBackdropNode 检查是否匹配，不匹配时跳过共享模式。
     */
    var sharedDownsampleScale: Float = DOWNSAMPLE_SCALE
        internal set

    /**
     * 源 Backdrop 的坐标（壁纸层在视图树中的位置）。
     * 供 DrawBackdropNode 计算卡片在壁纸中的偏移量。
     */
    val sourceLayerCoordinates: LayoutCoordinates?
        get() = source.layerCoordinates

    private var inverseLayerScope: InverseLayerScope? = null

    fun release() {
        // GraphicsLayer cleanup is handled by SharedBlurRecorderNode.onDetach()
        sharedSampledLayer = null
    }

    /**
     * 创建预渲染 Modifier。在指定 Composable 的 draw 阶段
     * 将壁纸源录制到 sharedSampledLayer（降采样+预模糊）。
     *
     * 该 Modifier 应附加到壁纸 Box 之后的不可见 Box 上，
     * 确保 source.graphicsLayer 已被录制。
     *
     * @param blurRadiusPx 模糊半径（px）
     * @param downsampleScale 降采样比例，默认使用全局 DOWNSAMPLE_SCALE
     */
    /**
     * @param sourceKey 源内容指纹（壁纸 bitmap / 缩放 / 偏移 / 亮度 / 主题色等）。
     *   它没变时说明源层内容和上一帧逐像素相同，可以整段跳过重新录制——
     *   滑动课表、开关弹窗这类场景下壁纸是静止的，重录纯属白烧。
     *   注意：指纹必须覆盖所有会改变源层内容的因素，否则会用到过期采样。
     */
    fun preRenderModifier(
        blurRadiusPx: Float,
        downsampleScale: Float = DOWNSAMPLE_SCALE,
        sourceKey: Any? = null
    ): Modifier {
        return SharedBlurRecorderElement(this, blurRadiusPx, downsampleScale, sourceKey)
    }

    /**
     * 共享模式下此方法不被 DrawBackdropNode 调用（走共享层分支）。
     * 保留作为 Backdrop 接口实现和降级路径。
     */
    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val coordinates = coordinates ?: return
        val layerCoordinates = source.layerCoordinates ?: return
        withTransform({
            if (layerBlock != null) {
                with(obtainInverseLayerScope()) { inverseTransform(density, layerBlock) }
            }
            val offset = try {
                layerCoordinates.localPositionOf(coordinates)
            } catch (_: Exception) {
                coordinates.positionInWindow() - layerCoordinates.positionInWindow()
            }
            translate(-offset.x, -offset.y)
        }) {
            drawLayer(source.graphicsLayer)
        }
    }

    private fun obtainInverseLayerScope(): InverseLayerScope {
        return inverseLayerScope?.apply { reset() }
            ?: InverseLayerScope().also { inverseLayerScope = it }
    }
}

// --- 预渲染 Modifier 实现 ---

private class SharedBlurRecorderElement(
    private val sharedBackdrop: SharedBlurBackdrop,
    private val blurRadiusPx: Float,
    private val downsampleScale: Float = DOWNSAMPLE_SCALE,
    private val sourceKey: Any? = null
) : ModifierNodeElement<SharedBlurRecorderNode>() {

    override fun create(): SharedBlurRecorderNode {
        return SharedBlurRecorderNode(sharedBackdrop, blurRadiusPx, downsampleScale, sourceKey)
    }

    override fun update(node: SharedBlurRecorderNode) {
        // 能走到 update 说明 equals 判定有参数变了 → 必须重录一次
        node.sharedBackdrop = sharedBackdrop
        node.blurRadiusPx = blurRadiusPx
        node.downsampleScale = downsampleScale
        node.sourceKey = sourceKey
        node.markNeedsRecord()
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "SharedBlurRecorder"
        properties["sharedBackdrop"] = sharedBackdrop
        properties["blurRadiusPx"] = blurRadiusPx
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SharedBlurRecorderElement) return false
        return sharedBackdrop == other.sharedBackdrop &&
            blurRadiusPx == other.blurRadiusPx &&
            downsampleScale == other.downsampleScale &&
            sourceKey == other.sourceKey
    }

    override fun hashCode(): Int {
        var result = sharedBackdrop.hashCode()
        result = 31 * result + blurRadiusPx.hashCode()
        result = 31 * result + downsampleScale.hashCode()
        result = 31 * result + (sourceKey?.hashCode() ?: 0)
        return result
    }
}

private class SharedBlurRecorderNode(
    var sharedBackdrop: SharedBlurBackdrop,
    var blurRadiusPx: Float,
    var downsampleScale: Float = DOWNSAMPLE_SCALE,
    var sourceKey: Any? = null
) : DrawModifierNode, Modifier.Node() {

    private var blurLayer: GraphicsLayer? = null

    private var needsRecord = true
    private var recordedW = 0
    private var recordedH = 0
    private var recordedBlurRadius = Float.NaN

    fun markNeedsRecord() { needsRecord = true }

    override fun ContentDrawScope.draw() {
        drawContent()

        if (!isRenderEffectSupported()) return

        val layer = blurLayer ?: return

        val targetW = (size.width * downsampleScale).roundToInt().coerceAtLeast(1)
        val targetH = (size.height * downsampleScale).roundToInt().coerceAtLeast(1)

        // 源内容、模糊半径、目标尺寸都没变 → 复用上一帧的录制结果，跳过整段降采样+模糊
        if (needsRecord || recordedW != targetW || recordedH != targetH) {
            val sourceLayer = sharedBackdrop.source.graphicsLayer
            // 将壁纸源录制到降采样层
            layer.record(IntSize(targetW, targetH)) {
                drawContext.canvas.save()
                drawContext.canvas.scale(downsampleScale, downsampleScale)
                drawLayer(sourceLayer)
                drawContext.canvas.restore()
            }
            needsRecord = false
            recordedW = targetW
            recordedH = targetH

            // 注入到共享 Backdrop
            sharedBackdrop.sharedSampledLayer = layer
            sharedBackdrop.sharedDownsampleScale = downsampleScale
        }

        // 应用模糊 RenderEffect（在 drawLayer 时生效）。
        // 只在半径真正变化时赋值：每帧 new 一个 BlurEffect 会让图层反复失效、重跑一遍 GPU 模糊。
        if (recordedBlurRadius != blurRadiusPx) {
            recordedBlurRadius = blurRadiusPx
            layer.renderEffect = if (blurRadiusPx > 0f) {
                val scaledBlur = blurRadiusPx * downsampleScale
                BlurEffect(
                    null,
                    scaledBlur,
                    scaledBlur,
                    TileMode.Clamp
                )
            } else null
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        blurLayer = graphicsContext.createGraphicsLayer()
        needsRecord = true
        recordedBlurRadius = Float.NaN
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        blurLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            blurLayer = null
        }
        sharedBackdrop.sharedSampledLayer = null
        needsRecord = true
        recordedBlurRadius = Float.NaN
    }
}
