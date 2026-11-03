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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
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
 * 页级玻璃（Android 实现）。
 *
 * 一页只挂**一个**采样节点：把页内所有课卡的轮廓合并成一条路径去裁壁纸，再让一次 AGSL
 * 折射 pass 覆盖整页。相比「每张卡各自 drawBackdrop」：
 *  - Compose 侧失效源 N → 1（不再每帧从整页重录 N 份采样 display list）
 *  - GPU 侧折射离屏 pass N → 1
 *  - 形状仍走 Compose 的 `clipPath`，与卡片轮廓逐点同源（`ContinuousRoundedRectangle`），
 *    不存在「shader 复刻连续圆角」的 1~2px 角部差异
 *
 * 表面色与描边**不进 shader**：它们留在卡片上由 Compose 绘制。实验页量过，省下的那点录制成本
 * 只有 0.2ms，却要拿形状保真去换，不划算。
 *
 * ## 三层缓存（顺序即代价从高到低）
 * 1. [GlassCardRegistry.version]：卡片**相对本节点**的几何。翻周（pager 平移）不改它，
 *    所以「挪一整页」不会触发下面任何一层。
 * 2. 并集路径：只在 version 变时 rewind + addPath，一页最多 88 段连续圆角轮廓。
 * 3. RenderEffect 实例：只在 version / 折射参数变时重建。换实例本身就会让 hwui 把整层判为
 *    失效并重算一次全屏 pass，比比较一个 int 贵几个数量级。
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
    // 当前挂在 layer 上的 effect 对应哪一版几何 / 哪一组参数。Int.MIN_VALUE = 没有 effect。
    val effectStamp = remember(registry) { EffectStamp() }
    // 并集路径缓存：一页最多 88 段连续圆角路径，静止时逐点相同
    val pathCache = remember(registry) { UnionPathCache() }

    val wantsRefraction = refractionHeight > 0f && refractionAmount > 0f
    val shader = if (wantsRefraction && isRuntimeShaderSupported()) {
        remember { RuntimeShader(PAGE_GLASS_REFRACTION_SHADER) }
    } else null
    val platformShader = remember(shader) { shader?.asAndroidRuntimeShader() }

    // 卡片几何变化 → 让本节点重绘一次；静止时 version 不变，零开销
    SideEffect { registry.onInvalidate = { tick.intValue = registry.version } }
    DisposableEffect(registry) {
        onDispose { registry.onInvalidate = null }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                // 本节点即坐标原点：卡片登记「相对我」的矩形，pager 平移在这个坐标系里恒等。
                nodeCoordinates[0] = coordinates
                registry.attachAnchor(coordinates)
            }
            // 采样窗口失效源：**相对壁纸层**的位移。和「卡片几何」是两件事，必须分开。
            // 翻周时卡片在页内的位置不变（几何不动、路径和 effect 都不用重建），但整页相对壁纸
            // 平移了 —— 卡片底下露出的壁纸换了一块，采样窗口必须跟着移。这个位移是烤进显示列表的
            // `translate(-offset)`，不重录就会一直采旧位置：现象是横滑时玻璃冻结、落页后仍冻结，
            // 直到竖向滚动改了卡片几何才连带恢复。
            .graphicsLayer {
                // 版本号一读即被观察：卡片挪动时让 layer 重录并把新矩形传给 shader
                tick.intValue
                val platformShader = platformShader
                // 诊断探针：整页离屏 pass 的代价上限（画面会失去折射，仅用于测量）
                if (PageGlassTuning.probeNoRefractionEffect) {
                    if (renderEffect != null) renderEffect = null
                    effectStamp.invalidate()
                    return@graphicsLayer
                }
                // 没有折射（材质档关闭 / 本机不支持 AGSL）时**绝不能**挂着 pass-through 的全页
                // RenderEffect：它照样分配整页离屏缓冲、跑一遍全屏采样，输出与输入逐像素相同。
                // 以前这里直接 return，effect 会一直留在 layer 上 —— 关掉折射反而更贵。
                if (platformShader == null) {
                    if (renderEffect != null) renderEffect = null
                    effectStamp.invalidate()
                    return@graphicsLayer
                }
                val count = registry.cardCount
                if (count <= 0) {
                    // 本页没有登记任何卡（无壁纸 / 不采样 / 全在视口外）：不能挂着空的全页
                    // RenderEffect —— 空内容也要分配整页离屏缓冲，翻页时几页叠加就是纯亏。
                    if (renderEffect != null) renderEffect = null
                    effectStamp.invalidate()
                    return@graphicsLayer
                }
                // 矩形已是本节点本地坐标，可整份交给 shader（不再逐帧减原点、逐帧拷贝 704 个 float）
                if (effectStamp.isCurrent(registry.version, refractionHeight, refractionAmount)) {
                    return@graphicsLayer
                }
                effectStamp.commit(registry.version, refractionHeight, refractionAmount)
                platformShader.setFloatUniform("cardRects", registry.rects)
                platformShader.setFloatUniform("cardRadii", registry.radii)
                platformShader.setIntUniform("cardCount", count)
                platformShader.setFloatUniform("refractionHeight", refractionHeight)
                // 与原 lens() 一致：位移方向取负（向卡内采）
                platformShader.setFloatUniform("refractionAmount", -refractionAmount)
                platformShader.setFloatUniform("depthEffect", 0f)
                // uniform 变化本身不会让 hwui 感知到，只有换 effect 才强制重算 —— 所以几何一变就得换新实例
                renderEffect = PlatformRenderEffect
                    .createRuntimeShaderEffect(platformShader, "content")
                    .asComposeRenderEffect()
            }
            .then(GlassSampleWindowElement(sourceCoordinates))
            .drawWithContent {
                tick.intValue
                // 并集路径只在卡片几何真的变了才重建。一页最多 88 段连续圆角轮廓，
                // 每帧 rewind + addPath 全是主线程的路径拷贝；而静止时它们逐点相同。
                // ▸ 失效源就是 registry.version：它存的已经是「相对本节点」的坐标，
                //   pager 平移不再碰它，所以翻周期间这里一次都不重建。
                if (pathCache.isStale(registry.version)) {
                    unionPath.rewind()
                    val count = registry.cardCount
                    var drawn = 0
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE
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
                        val radius = registry.radii[base]
                        val slot = slots[i]
                        val outlinePath = slot.pathFor(w, h, radius, density, layoutDirection)
                        unionPath.addPath(outlinePath, Offset(l, t))
                        if (l < minX) minX = l
                        if (t < minY) minY = t
                        if (r > maxX) maxX = r
                        if (b > maxY) maxY = b
                        drawn++
                    }
                    pathCache.commit(registry.version, drawn, minX, minY, maxX, maxY)
                }
                if (pathCache.pathCount > 0) {
                    val coordinates = nodeCoordinates[0]
                    val offset = if (coordinates != null) {
                        try {
                            sourceCoordinates.localPositionOf(coordinates, Offset.Zero)
                        } catch (_: Exception) {
                            Offset.Zero
                        }
                    } else Offset.Zero
                    val drawWallpaper: DrawScope.() -> Unit = {
                        withTransform({
                            translate(-offset.x, -offset.y)
                            if (bakeScale != 1f && bakeScale > 0f) {
                                scale(1f / bakeScale, 1f / bakeScale, Offset.Zero)
                            }
                        }) {
                            drawLayer(bakedLayer)
                        }
                    }
                    // 诊断开关见 PageGlassTuning：只把路径裁剪换成等面积矩形裁剪，
                    // 用来单独量「84 段 G2 轮廓的裁剪光栅化」值多少（画面会变成方块，仅用于测量）。
                    if (PageGlassTuning.probeRectClip) {
                        clipRect(
                            pathCache.left,
                            pathCache.top,
                            pathCache.right,
                            pathCache.bottom
                        ) { drawWallpaper() }
                    } else {
                        clipPath(unionPath) { drawWallpaper() }
                    }
                }
                drawContent()
            }
    )
}

/**
 * 页级玻璃的诊断开关。**只用来在真机上量代价，不是功能开关。**
 *
 * 玻璃这条链路的成本由三块拼成，靠猜永远猜不准，所以各留一个探针，逐项开关后
 * 跑同一段手势（翻周 3 次 + 竖向滚动）看 framestats，就能知道该往哪砍：
 *
 * | 探针 | 关掉的东西 | 画面变化 |
 * |---|---|---|
 * | [probeNoRefractionEffect] | 整页离屏 + 全屏采样 + 合成 | 折射消失，玻璃变成「裁剪后的模糊壁纸」 |
 * | [probeRectClip] | 84 段 G2 轮廓的裁剪光栅化（换成等面积矩形） | 卡片玻璃变方块 |
 *
 * 判读：
 * - 只开 [probeNoRefractionEffect] 就掉很多 → 大头是离屏 pass，值得做「ImageShader 直采」
 *   （用一张烘焙位图当纹理，省掉离屏/全屏采样/合成三趟，视觉完全不变）。
 * - 只开 [probeRectClip] 就掉很多 → 大头是复杂路径裁剪，值得把形状遮罩挪进 shader。
 * - 两个都不明显 → 玻璃这块到此为止，别再折腾，去看组合/测量。
 */
object PageGlassTuning {
    @Volatile
    var probeNoRefractionEffect: Boolean = false

    @Volatile
    var probeRectClip: Boolean = false
}

/**
 * 采样窗口失效源。
 *
 * `drawBackdropLayer` 里那句 `translate(-offset)` 是**烤进显示列表**的：offset 变了下一次绘制
 * 必须重录，否则玻璃一直采旧位置。offset 的输入是「本节点相对壁纸层的位置」，所以判据就是它，
 * 而不是卡片几何 —— 这正是「翻周冻结」那一版漏掉的那一半：
 *
 * - 翻周：几何不动（路径 / effect 复用），位移动（重录一次，每帧一次，省不掉）
 * - 静止：两个都不动，零开销
 * - 竖滚：几何动（路径重建），位移也动（重录）
 *
 * 用普通字段比较真实坐标再 `invalidateDraw()`，**不能**挂 snapshot state 在绘制期读：
 * 那等于每帧主动失效整棵节点树的绘制。
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
            // 优先用「相对壁纸层」的位置：壁纸层自己动了（视差 / 缩放）也能捕捉到
            if (source != null && source.isAttached) {
                source.localPositionOf(coordinates, Offset.Zero)
            } else {
                coordinates.positionInWindow()
            }
        } catch (_: Exception) {
            return
        }
        if (position.x != lastX || position.y != lastY) {
            lastX = position.x
            lastY = position.y
            invalidateDraw()
        }
    }

    /** 壁纸层换了（换壁纸 / 切共享层）：旧判据作废，下一次一定重录。 */
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

/** 当前挂在 layer 上的 effect 对应哪一版几何 + 哪一组折射参数。普通字段，绘制期读写。 */
private class EffectStamp {    private var version = Int.MIN_VALUE
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

/** 页级并集路径的失效判据。 */
private class UnionPathCache {
    private var version = Int.MIN_VALUE

    /** 上次重建时真正加进路径的卡片数；0 表示本页没有可画的卡。 */
    var pathCount = 0
        private set

    /** 并集包围盒（节点本地坐标）。只给诊断用的矩形裁剪探针，正式路径不用。 */
    var left = 0f
        private set
    var top = 0f
        private set
    var right = 0f
        private set
    var bottom = 0f
        private set

    fun isStale(version: Int): Boolean = this.version != version

    fun commit(
        version: Int,
        pathCount: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        this.version = version
        this.pathCount = pathCount
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }
}

/** 每槽一个复用的轮廓 Path：卡片尺寸/圆角不变时不重建（一页 30 张卡、每帧都 new Path 会造成 GC 抖动）。 */
private class SlotOutline {
    private val path = Path()
    private val shape = ContinuousRoundedRectangle(CornerSize(0f))
    private var w = -1f
    private var h = -1f
    private var radius = -1f
    private var cornerSize = CornerSize(0f)

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
        cornerSize = CornerSize(radiusPx)
        val outline = ContinuousRoundedRectangle(cornerSize)
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
 * 只做「折射位移」的页级 shader。
 *
 * 形状、抗锯齿、裁剪全部由 Compose 的 `clipPath` 完成（路径与卡片同源），所以这里不需要
 * SDF 判形状、也不需要 `smoothstep` 描边。唯一的必要条件是：要知道每个像素相对**它所属那张卡**
 * 的位置，才能算出局部位移方向 —— 这正是 `cardRects` 数组的用途。
 *
 * 透明像素（卡片之外）直接早退：滚动期它们占页面的一半以上，早退省下的带宽比逐卡比较更值。
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
