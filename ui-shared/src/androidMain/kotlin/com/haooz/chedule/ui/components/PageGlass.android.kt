package com.haooz.chedule.ui.components

import android.graphics.RenderEffect as PlatformRenderEffect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asAndroidRuntimeShader
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.capsule.ContinuousRoundedRectangle

/**
 * 页级玻璃（Android）：一页只挂**一个**采样节点，把所有课卡轮廓并成一条路径去裁壁纸，
 * 再用一次 AGSL 折射 pass 覆盖整页。形状仍由 `clipPath` 走与卡片同源的
 * `ContinuousRoundedRectangle`，不在 shader 里复刻，避免 1~2px 的角部差异。
 *
 * 三层缓存，顺序即代价从高到低：
 * 1. [GlassCardRegistry.version]（卡片相对本节点的几何）—— 翻周不改它，所以下两层都不动；
 * 2. 并集路径 —— 只在 version 变时重建；
 * 3. RenderEffect 实例 —— 只在 version / 折射参数变时重建。换实例会让 hwui 把整层判失效。
 *
 * 另有一个**独立**失效源：采样窗口（见 [GlassSampleWindowNode]），它决定要不要重录。
 * 实测（PKB110）：整条链路关掉只省 1~2ms、jank 不变，所以不要再往这里加东西。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
actual fun PageGlass(
    registry: GlassCardRegistry,
    backdrop: SharedBlurBackdrop,
    bakedLayer: GraphicsLayer?,
    bakeScale: Float,
    refractionHeight: Float,
    refractionAmount: Float,
    modifier: Modifier
) {
    if (bakedLayer == null || !isRenderEffectSupported()) return
    val sourceCoordinates: LayoutCoordinates = backdrop.sourceLayerCoordinates ?: return

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val nodeCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val tick = remember { mutableIntStateOf(0) }

    val slots = remember(registry) { Array(registry.capacity) { SlotOutline() } }
    val unionPath = remember { Path() }
    val effectStamp = remember(registry) { EffectStamp() }
    val pathCache = remember(registry) { UnionPathCache() }

    val wantsRefraction = refractionHeight > 0f && refractionAmount > 0f
    val shader = if (wantsRefraction && isRuntimeShaderSupported()) {
        remember { RuntimeShader(PAGE_GLASS_REFRACTION_SHADER) }
    } else null
    val platformShader = remember(shader) { shader?.asAndroidRuntimeShader() }

    // 几何变化 → 重绘一次；静止时 version 不变，零开销
    SideEffect { registry.onInvalidate = { tick.intValue = registry.version } }
    DisposableEffect(registry) {
        onDispose { registry.onInvalidate = null }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                // 本节点即坐标原点，卡片登记「相对我」的矩形
                nodeCoordinates[0] = coordinates
                registry.attachAnchor(coordinates)
            }
            .graphicsLayer {
                tick.intValue
                val platformShader = platformShader
                // 没有折射（材质档关闭 / 不支持 AGSL）时必须撤掉 effect：pass-through 的全页
                // RenderEffect 照样分配整页离屏缓冲并跑一遍全屏采样，比不挂更贵。
                val count = registry.cardCount
                if (platformShader == null || count <= 0) {
                    if (renderEffect != null) renderEffect = null
                    effectStamp.invalidate()
                    return@graphicsLayer
                }
                if (effectStamp.isCurrent(registry.version, refractionHeight, refractionAmount)) {
                    return@graphicsLayer
                }
                effectStamp.commit(registry.version, refractionHeight, refractionAmount)
                // 矩形已是本节点本地坐标，整份交给 shader 即可
                platformShader.setFloatUniform("cardRects", registry.rects)
                platformShader.setFloatUniform("cardRadii", registry.radii)
                platformShader.setIntUniform("cardCount", count)
                platformShader.setFloatUniform("refractionHeight", refractionHeight)
                // 与原 lens() 一致：位移方向取负（向卡内采）
                platformShader.setFloatUniform("refractionAmount", -refractionAmount)
                platformShader.setFloatUniform("depthEffect", 0f)
                // uniform 变化 hwui 不感知，换 effect 实例才强制重算 —— 所以几何一变就得换新实例
                renderEffect = PlatformRenderEffect
                    .createRuntimeShaderEffect(platformShader, "content")
                    .asComposeRenderEffect()
            }
            .then(GlassSampleWindowElement(sourceCoordinates))
            .drawWithContent {
                tick.intValue
                if (pathCache.isStale(registry.version)) {
                    unionPath.rewind()
                    val count = registry.cardCount
                    var drawn = 0
                    for (i in 0 until count) {
                        if (!registry.isOccupied(i)) continue
                        val base = i * 4
                        val l = registry.rects[base]
                        val t = registry.rects[base + 1]
                        val r = registry.rects[base + 2]
                        val b = registry.rects[base + 3]
                        val w = r - l
                        val h = b - t
                        if (w <= 0f || h <= 0f) continue
                        val slot = slots[i]
                        // 轮廓 Path 按卡片尺寸复用，位置靠 addPath 偏移，不重复建 Path
                        val outline = slot.pathFor(w, h, registry.radii[base], density, layoutDirection)
                        unionPath.addPath(outline, Offset(l, t))
                        drawn++
                    }
                    pathCache.commit(registry.version, drawn)
                }
                if (pathCache.hasCards) {
                    val coordinates = nodeCoordinates[0]
                    val offset = if (coordinates != null) {
                        try {
                            sourceCoordinates.localPositionOf(coordinates, Offset.Zero)
                        } catch (_: Exception) {
                            Offset.Zero
                        }
                    } else Offset.Zero
                    clipPath(unionPath) {
                        // 先移到壁纸层原点，再按 1/bakeScale 放大回全屏坐标（矩阵右乘，T 必须在 S 左侧）
                        withTransform({
                            translate(-offset.x, -offset.y)
                            if (bakeScale != 1f && bakeScale > 0f) {
                                scale(1f / bakeScale, 1f / bakeScale, Offset.Zero)
                            }
                        }) {
                            drawLayer(bakedLayer)
                        }
                    }
                }
                drawContent()
            }
    )
}

/**
 * 采样窗口失效源：`translate(-offset)` 是烤进显示列表的，offset 变了必须重录，否则一直采旧位置
 * （现象：横滑时玻璃像贴纸随卡片平移，落页后停在拖动中途那一帧）。
 *
 * 判据是「本节点相对壁纸层的位置」，不是卡片几何 —— 翻周时几何不动但位移动，每帧重录一次省不掉。
 *
 * ⚠ 必须挂在 `graphicsLayer` **内侧**：挂外侧时 `invalidateDraw()` 不会把图层录制标记为脏，
 * 图层继续复用旧内容（实测 250 次回调只换来 11 次重录）。
 */
private class GlassSampleWindowNode(
    var sourceCoordinates: LayoutCoordinates?
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var lastX = Float.NaN
    private var lastY = Float.NaN

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        val position = try {
            val source = sourceCoordinates
            // 用相对壁纸层的位置：壁纸层自己动了（视差 / 缩放）也能捕捉到
            if (source != null && source.isAttached) {
                source.localPositionOf(coordinates, Offset.Zero)
            } else {
                coordinates.positionInWindow()
            }
        } catch (_: Exception) {
            return
        }
        // 普通字段比较真实坐标，不能挂 snapshot state 再在 draw 期读
        if (position.x != lastX || position.y != lastY) {
            lastX = position.x
            lastY = position.y
            invalidateDraw()
        }
    }

    /** 壁纸层换了（换壁纸 / 切共享层）：旧判据作废。 */
    fun resetWindow() {
        lastX = Float.NaN
        lastY = Float.NaN
    }

    override fun ContentDrawScope.draw() {
        drawContent()
    }
}

private class GlassSampleWindowElement(
    private val sourceCoordinates: LayoutCoordinates?
) : ModifierNodeElement<GlassSampleWindowNode>() {

    override fun create(): GlassSampleWindowNode = GlassSampleWindowNode(sourceCoordinates)

    override fun update(node: GlassSampleWindowNode) {
        if (node.sourceCoordinates !== sourceCoordinates) {
            node.sourceCoordinates = sourceCoordinates
            node.resetWindow()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlassSampleWindowElement) return false
        return sourceCoordinates === other.sourceCoordinates
    }

    override fun hashCode(): Int = sourceCoordinates?.hashCode() ?: 0
}

/** 当前挂在 layer 上的 effect 对应哪一版几何 / 哪组参数；Int.MIN_VALUE = 没有 effect。 */
private class EffectStamp {
    private var version = Int.MIN_VALUE
    private var refractionHeight = Float.NaN
    private var refractionAmount = Float.NaN

    fun isCurrent(version: Int, refractionHeight: Float, refractionAmount: Float): Boolean =
        this.version == version &&
                this.refractionHeight == refractionHeight &&
                this.refractionAmount == refractionAmount

    fun commit(version: Int, refractionHeight: Float, refractionAmount: Float) {
        this.version = version
        this.refractionHeight = refractionHeight
        this.refractionAmount = refractionAmount
    }

    /** effect 被撤掉后必须让下一次一定重建。 */
    fun invalidate() {
        version = Int.MIN_VALUE
        refractionHeight = Float.NaN
        refractionAmount = Float.NaN
    }
}

/** 并集路径的失效判据。 */
private class UnionPathCache {
    private var version = Int.MIN_VALUE

    /** 上次重建时真正进路径的卡片数；0 = 本页没有可画的卡。 */
    var cardCount = 0
        private set

    val hasCards: Boolean get() = cardCount > 0

    fun isStale(version: Int): Boolean = this.version != version

    fun commit(version: Int, cardCount: Int) {
        this.version = version
        this.cardCount = cardCount
    }
}

/** 每槽一个复用的轮廓 Path：尺寸/圆角不变就复用（一页 30 张卡每帧 new Path 会造成 GC 抖动）。 */
private class SlotOutline {
    private val path = Path()
    private var w = -1f
    private var h = -1f
    private var radius = -1f

    fun pathFor(
        width: Float,
        height: Float,
        radiusPx: Float,
        density: androidx.compose.ui.unit.Density,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection
    ): Path {
        if (w == width && h == height && radius == radiusPx) return path
        w = width
        h = height
        radius = radiusPx
        val outline = ContinuousRoundedRectangle(CornerSize(radiusPx))
            .createOutline(Size(width, height), layoutDirection, density)
        path.rewind()
        when (outline) {
            is Outline.Generic -> path.addPath(outline.path)
            is Outline.Rounded -> path.addRoundRect(outline.roundRect)
            is Outline.Rectangle -> path.addRect(outline.rect)
        }
        return path
    }
}

/**
 * 只做折射位移的页级 shader：形状、抗锯齿、裁剪都交给 `clipPath`，这里只算每像素相对
 * **它所属那张卡**的位置，决定往哪采。卡片外的像素 alpha 为 0，第一行就早退。
 */
private val PAGE_GLASS_REFRACTION_SHADER = """
uniform shader content;
uniform float4 cardRects[88];
uniform float4 cardRadii[88];
uniform int cardCount;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) { return radii.y; }
        else { return radii.z; }
    } else {
        if (coord.y <= 0.0) { return radii.x; }
        else { return radii.w; }
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0) + float2(0.00001));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    half4 base = content.eval(coord);
    if (base.a < 0.004) { return base; }
    if (refractionHeight <= 0.0) { return base; }
    for (int i = 0; i < 88; i++) {
        if (i >= cardCount) { break; }
        float4 r = cardRects[i];
        if (coord.x < r.x || coord.y < r.y || coord.x > r.z || coord.y > r.w) { continue; }
        float2 halfSize = (r.zw - r.xy) * 0.5;
        float2 centered = coord - (r.xy + halfSize);
        float radius = radiusAt(centered, cardRadii[i]);
        float sd = sdRoundedRect(centered, halfSize, radius);
        if (sd > 0.0) { continue; }
        float depth = -sd;
        if (depth >= refractionHeight) { return base; }
        float d = circleMap(1.0 - depth / refractionHeight) * refractionAmount;
        float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
        float2 grad = normalize(
            gradSdRoundedRect(centered, halfSize, gradRadius)
            + depthEffect * normalize(centered + float2(0.0001))
        );
        return content.eval(coord + d * grad);
    }
    return base;
}
""".trimIndent()
