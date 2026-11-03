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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
            .graphicsLayer {
                // 版本号一读即被观察：卡片挪动时让 layer 重录并把新矩形传给 shader
                tick.intValue
                val platformShader = platformShader
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
                        drawn++
                    }
                    pathCache.commit(registry.version, drawn)
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
                    clipPath(unionPath) {
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

/** 当前挂在 layer 上的 effect 对应哪一版几何 + 哪一组折射参数。普通字段，绘制期读写。 */
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

/** 页级并集路径的失效判据。 */
private class UnionPathCache {
    private var version = Int.MIN_VALUE

    /** 上次重建时真正加进路径的卡片数；0 表示本页没有可画的卡。 */
    var pathCount = 0
        private set

    fun isStale(version: Int): Boolean = this.version != version

    fun commit(version: Int, pathCount: Int) {
        this.version = version
        this.pathCount = pathCount
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
