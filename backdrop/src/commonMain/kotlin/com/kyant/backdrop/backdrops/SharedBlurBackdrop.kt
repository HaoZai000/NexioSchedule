package com.kyant.backdrop.backdrops

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.kyant.backdrop.internal.InverseLayerScope
import com.kyant.backdrop.isRenderEffectSupported
import kotlin.math.roundToInt

/**
 * 烘焙层的分辨率比例。1f = 与源层同分辨率。降采样安全：模糊只烘焙一次——源先录进带 RenderEffect 的中间层
 * blurLayer，再烘进 renderEffect=null 的 bakedLayer，采样侧 drawLayer(baked) 是纯纹理 blit，不重跑高斯模糊。
 * 变换顺序：采样侧必须先 translate(-offset) 再 scale(1/n)。矩阵右乘下 M=M0·T·S 时 baked 坐标 d 落在窗口
 * W + d/n；反过来 M=M0·S·T 落在 C + (d−offset)/n，整体错位 offset·(1−1/n)。n=1 时 S 恒等、两种等价。
 */
const val BLUR_BAKE_SCALE = 0.5f

/**
 * 共享预模糊 Backdrop。壁纸只模糊一次（预渲染时烘进 [blurredLayer]），之后所有课程卡片都从这张图采样，运行时高斯模糊开销为 0。
 * @param source 原始壁纸 LayerBackdrop（用于降级和坐标获取）
 */
@Stable
class SharedBlurBackdrop(
    internal val source: LayerBackdrop
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    /** 烘焙好的模糊壁纸层。模糊已固化，drawLayer 时不再有模糊运算；预渲染 Modifier 在壁纸/模糊半径变化时重录。 */
    override var blurredLayer: GraphicsLayer? by mutableStateOf(null)
        internal set

    /**
     * 烘焙层相对源层的缩放比例（< 1 表示降采样烘焙）。采样侧按 1/n 放大回全屏坐标，对按压缩放的反向补偿无影响（那条链路用节点尺寸）。
     * 降采样收益实打实：几十张卡采样同一层，纹理面积小使采样带宽与 cache 局部性同时改善；内容本就模糊，放大回来看不出差别。
     */
    var blurredScale: Float = BLUR_BAKE_SCALE
        internal set

    // 原 fork 的 `var sampledBitmap: android.graphics.Bitmap?` 已删除：平台专属类型无法进 commonMain，且全项目零引用，属未接线预留字段；
    // 将来若做「ImageShader 直采折射」，改用 expect/actual 平台位图类型再补回。

    /** 共享层像素内容版本：每次重录源层时递增，供采样侧跳过无变化重录 */
    override var contentVersion: Int = 0
        internal set

    /** 源 Backdrop 的坐标（壁纸层在视图树中的位置），供 DrawBackdropNode 计算卡片在壁纸中的偏移量。 */
    val sourceLayerCoordinates: LayoutCoordinates?
        get() = source.layerCoordinates

    private var inverseLayerScope: InverseLayerScope? = null

    fun release() {
        // GraphicsLayer cleanup is handled by SharedBlurRecorderNode.onDetach()
        blurredLayer = null
    }

    /**
     * 创建预渲染 Modifier：在指定 Composable 的 draw 阶段把壁纸源模糊一次并烘焙进 [blurredLayer]。
     * 应附加到壁纸 Box 之后的不可见 Box 上，确保 source.graphicsLayer 已被录制。
     * @param blurRadiusPx 模糊半径（px，作用于源层全分辨率；烘焙时 ×downsampleScale 适配降采样缓冲，放大回原尺寸后扩散范围不变）
     * @param downsampleScale 烘焙分辨率比例，默认 [BLUR_BAKE_SCALE]
     */
    /**
     * @param sourceKey 源内容指纹（壁纸 bitmap / 缩放 / 偏移 / 亮度 / 主题色等）。值未变时源层内容与上帧逐像素
     *   相同，可整段跳过重新烘焙（滑动课表/开关弹窗时壁纸静止，重烘焙纯属白烧）；指纹须覆盖所有改变源层内容的因素，否则会用到过期采样。
     */
    fun preRenderModifier(
        blurRadiusPx: Float,
        downsampleScale: Float = BLUR_BAKE_SCALE,
        sourceKey: Any? = null
    ): Modifier {
        return SharedBlurRecorderElement(this, blurRadiusPx, downsampleScale, sourceKey)
    }

    /** 采样入口：画已烘焙的模糊层（模糊开销为 0）；未烘焙好时退回原始源层，避免空白。 */
    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val coordinates = coordinates ?: return
        val layerCoordinates = source.layerCoordinates ?: return

        val baked = blurredLayer
        // 烘焙过了才按它的分辨率放大；降级到原始源层时源层是全分辨率，不用缩放
        val n = if (baked != null) blurredScale else 1f

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
            // 顺序：先平移到壁纸层原点，再按 1/n 放大回全屏坐标（矩阵右乘下 T 在 S 左侧，baked 坐标 d 落在 W + d/n，逐像素对齐）。
            if (n != 1f) {
                scale(1f / n, 1f / n, Offset.Zero)
            }
        }) {
            drawLayer(baked ?: source.graphicsLayer)
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
    private val downsampleScale: Float = BLUR_BAKE_SCALE,
    private val sourceKey: Any? = null
) : ModifierNodeElement<SharedBlurRecorderNode>() {

    override fun create(): SharedBlurRecorderNode {
        return SharedBlurRecorderNode(sharedBackdrop, blurRadiusPx, downsampleScale, sourceKey)
    }

    override fun update(node: SharedBlurRecorderNode) {
        // 仅源内容相关参数变化才重录降采样层；仅 blur 半径变化时只刷新 renderEffect，避免壁纸层无意义重录。
        val sourceChanged = node.sharedBackdrop !== sharedBackdrop ||
            node.downsampleScale != downsampleScale ||
            node.sourceKey != sourceKey
        node.sharedBackdrop = sharedBackdrop
        node.blurRadiusPx = blurRadiusPx
        node.downsampleScale = downsampleScale
        node.sourceKey = sourceKey
        if (sourceChanged) {
            node.markNeedsRecord()
        }
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
    var downsampleScale: Float = BLUR_BAKE_SCALE,
    var sourceKey: Any? = null
) : DrawModifierNode, Modifier.Node() {

    /** 中间层：源内容 + BlurEffect。只用于烘焙，不直接给采样侧用（RenderEffect 在 drawLayer 时才应用，直接采样会让每张卡重跑一次全屏模糊）。 */
    private var blurLayer: GraphicsLayer? = null

    /** 持久层：烘焙结果，模糊已固化，无 RenderEffect。 */
    private var bakedLayer: GraphicsLayer? = null

    private var needsRecord = true
    private var recordedW = 0
    private var recordedH = 0
    private var recordedBlurRadius = Float.NaN

    /** 烘焙时**实际达成**的分辨率比例（可能与请求的 downsampleScale 有取整偏差） */
    private var recordedScale = Float.NaN

    fun markNeedsRecord() { needsRecord = true }

    override fun ContentDrawScope.draw() {
        drawContent()

        if (!isRenderEffectSupported()) return

        val blur = blurLayer ?: return
        val baked = bakedLayer ?: return

        val targetW = (size.width * downsampleScale).roundToInt().coerceAtLeast(1)
        val targetH = (size.height * downsampleScale).roundToInt().coerceAtLeast(1)

        val actualScale = if (size.width > 0f) targetW.toFloat() / size.width else 1f

        // 只在半径/比例真正变化时赋值，避免每帧 new BlurEffect 让图层反复失效
        if (recordedBlurRadius != blurRadiusPx || recordedScale != actualScale) {
            recordedBlurRadius = blurRadiusPx
            recordedScale = actualScale
            blur.renderEffect = if (blurRadiusPx > 0f) {
                // 模糊作用在降采样后的缓冲上，半径同步 ×比例，放大回原尺寸后扩散范围不变
                val scaledBlur = blurRadiusPx * actualScale
                BlurEffect(
                    null,
                    scaledBlur,
                    scaledBlur,
                    TileMode.Clamp
                )
            } else null
            needsRecord = true
        }

        // 源内容、模糊半径、目标尺寸都没变 → 复用上一帧的烘焙结果，跳过整段模糊
        if (needsRecord || recordedW != targetW || recordedH != targetH) {
            val sourceLayer = sharedBackdrop.source.graphicsLayer

            // 1) 源 -> 中间层
            blur.record(IntSize(targetW, targetH)) {
                drawContext.canvas.save()
                drawContext.canvas.scale(actualScale, actualScale)
                drawLayer(sourceLayer)
                drawContext.canvas.restore()
            }

            // 2) 烘焙：把带 BlurEffect 的中间层画进持久层，模糊就此固化
            baked.record(IntSize(targetW, targetH)) {
                drawLayer(blur)
            }
            baked.renderEffect = null

            needsRecord = false
            recordedW = targetW
            recordedH = targetH

            sharedBackdrop.blurredLayer = baked
            sharedBackdrop.blurredScale = actualScale
            sharedBackdrop.contentVersion++
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        blurLayer = graphicsContext.createGraphicsLayer()
        bakedLayer = graphicsContext.createGraphicsLayer()
        needsRecord = true
        recordedBlurRadius = Float.NaN
        recordedScale = Float.NaN
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        blurLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            blurLayer = null
        }
        bakedLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            bakedLayer = null
        }
        sharedBackdrop.blurredLayer = null
        needsRecord = true
        recordedBlurRadius = Float.NaN
        recordedScale = Float.NaN
    }
}
