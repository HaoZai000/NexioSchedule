package com.haooz.chedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import kotlin.math.abs

/**
 * 课卡玻璃的**页级**注册表。
 *
 * 背景：`Modifier.drawBackdrop` 是「每个节点各自采样一次」，N 张卡就是 N 个采样节点。
 * 共享的只有上游（壁纸层 + 一次模糊烘焙），**「把纹理画进我的矩形」这件事一点没共享**；
 * 更贵的是失效拓扑——每个采样节点都是「随时可以触发整页 display list 重录」的源，
 * 而 Compose 的重绘粒度是整棵树（没有脏矩形），于是 N 个引用者把线性小成本放大成每帧整页重录。
 *
 * 这里改成：卡片只把「自己的矩形 + 圆角」登记进来，由**一个**页级节点统一采一次样，
 * 失效源从 N 降到 1，GPU 上的折射 pass 也从 N 个降到 1 个。
 *
 * 为什么不让 shader 自己判形状：本项目的卡是 `ContinuousRoundedRectangle`（G2 连续圆角）+
 * 内描边，在 shader 里复刻会有 1~2px 的角部差异。所以形状仍由 Compose 的 `clipPath` 裁剪
 * （路径与卡片完全同源），shader 只负责「折射位移」这一件只有 GPU 能做的事。
 *
 * ## 坐标口径：**相对玻璃节点**，不是 root
 *
 * 早先存的是 root 坐标，页级节点在绘制期减掉自己的 root 原点。看起来等价，实际是那半秒
 * 翻周卡顿的根因：pager 平移时页内每张卡的 root 矩形和节点原点**一起**移动，减出来的本地
 * 矩形数值上也该不变，但两条路径（`localBoundingBoxOf` 的矩阵连乘 vs `positionInRoot`）
 * 的浮点结果不会逐位相同 ⇒ 每帧判定「几何变了」⇒ 一页 84 张卡加权：重建并集路径、
 * 重建 RenderEffect 实例（hwui 会把整层判失效）、重录整页 display list。两页同时在场就是
 * 两倍。
 *
 * 所以改成让登记侧直接算「相对玻璃节点」的矩形（[attachAnchor] 给锚点），pager 平移在
 * 这个坐标系里恒等 ⇒ 版本号不动 ⇒ 翻周期间页级玻璃零重录、零 effect 重建。
 * 竖向滚动会真的改变本地矩形（卡片相对页在动），照常递增，视觉跟随不受影响。
 */
class GlassCardRegistry {

    /** 槽位上限：一页 7 天 × 12 节 = 84 格，另留 4 个余量给跨页借位与临时卡。 */
    val capacity: Int = CAPACITY

    /** 每张卡**相对玻璃节点**的 l/t/r/b（px）。写入侧是布局回调，读取侧是 draw 期。 */
    val rects = FloatArray(CAPACITY * 4)

    /** 四个角半径（px，顺序 tl/tr/br/bl）。本项目的卡四角相等，但保留逐角以免将来变形。 */
    val radii = FloatArray(CAPACITY * 4)

    private val occupied = BooleanArray(CAPACITY)

    /** 每槽登记的坐标与**布局**圆角（未乘变换缩放），锚点变化时用它重算，不必等卡片重新布局。 */
    private val coords = arrayOfNulls<LayoutCoordinates>(CAPACITY)
    private val layoutRadius = FloatArray(CAPACITY)

    /**
     * 坐标原点：页级玻璃节点自己的 [LayoutCoordinates]。`null` 时退回 root 坐标
     * （首帧、或本平台没有页级玻璃实现），此时行为与改造前一致，只是拿不到平移免疫。
     */
    private var anchor: LayoutCoordinates? = null

    /**
     * 布局版本号：卡片**相对玻璃节点**的几何变化时递增。**普通字段**，draw 期比较即可 ——
     * 一旦挂 snapshot state 并在 draw 期读它，滚动就会变成「每帧主动失效」。
     */
    var version: Int = 0
        private set

    /** 由页级玻璃节点登记：卡片矩形变了就让它重画一次。 */
    var onInvalidate: (() -> Unit)? = null

    /**
     * 已登记的卡片数。用于 shader 循环上界 —— 只遍历真实存在的槽位，
     * 而不是固定 88 次（后者在只有十几张卡时会白烧 6 倍）。
     */
    var cardCount: Int = 0
        private set

    private var scanCursor = 0

    fun isOccupied(slot: Int): Boolean = slot in 0 until CAPACITY && occupied[slot]

    fun alloc(): Int {
        for (i in 0 until CAPACITY) {
            val slot = (scanCursor + i) % CAPACITY
            if (!occupied[slot]) {
                occupied[slot] = true
                scanCursor = (slot + 1) % CAPACITY
                // 先占位，坐标由 onGloballyPositioned 补上；补之前是零矩形，绘制侧会跳过
                coords[slot] = null
                layoutRadius[slot] = 0f
                for (k in 0 until 4) {
                    rects[slot * 4 + k] = 0f
                    radii[slot * 4 + k] = 0f
                }
                recalcCount()
                version++
                return slot
            }
        }
        return -1
    }

    /** 让某个槽位这一帧不参与合成（卡片被隐藏/拖走时用），槽位本身保留给同一张卡复用。 */
    fun clearCard(slot: Int) {
        if (slot < 0 || slot >= CAPACITY) return
        val base = slot * 4
        if (rects[base] == 0f && rects[base + 1] == 0f && rects[base + 2] == 0f && rects[base + 3] == 0f) return
        for (k in 0 until 4) rects[base + k] = 0f
        version++
        onInvalidate?.invoke()
    }

    fun free(slot: Int) {
        if (slot < 0 || slot >= CAPACITY) return
        occupied[slot] = false
        coords[slot] = null
        layoutRadius[slot] = 0f
        for (k in 0 until 4) {
            rects[slot * 4 + k] = 0f
            radii[slot * 4 + k] = 0f
        }
        recalcCount()
        version++
        onInvalidate?.invoke()
    }

    /**
     * 登记/更新玻璃节点的坐标原点（页级节点的 [LayoutCoordinates]）。
     *
     * 只在**对象**变化时重算全部已登记矩形：pager 平移不会换坐标对象，所以这条路在翻周
     * 期间一次都不会触发。首帧卡片可能先于锚点登记（时间轴视图里玻璃节点在网格内部），
     * 那次 `null → 坐标` 会补算一遍，代价是一次性的。
     */
    fun attachAnchor(coordinates: LayoutCoordinates?) {
        if (anchor === coordinates) return
        anchor = coordinates
        var changed = false
        for (i in 0 until CAPACITY) {
            if (occupied[i] && recompute(i)) changed = true
        }
        if (changed) {
            version++
            onInvalidate?.invoke()
        }
    }

    private fun recalcCount() {
        var max = -1
        for (i in 0 until CAPACITY) if (occupied[i]) max = i
        cardCount = max + 1
    }

    /**
     * 写入卡片几何。只在**相对玻璃节点**的数值真的变化时递增版本（静止时零成本）。
     *
     * 坐标必须取「含图形变换的完整边界」，**不能**用 `positionInRoot()` + `size`：
     * 前者位置已按 `graphicsLayer` 的缩放算过、后者尺寸却还是布局值，两者一混矩形就是歪的
     * （按压时卡片缩小 6%，玻璃窗口却按「缩放后的左上角 + 原始尺寸」铺开，表面色与玻璃就此脱开）。
     *
     * 用 `localBoundingBoxOf(clipBounds = false)` 而不是 `boundsInRoot()`：后者会把边界裁到
     * 祖先裁剪框里，翻周时露出半边的页会把边缘卡片的矩形压小。
     * 圆角按「边界宽 / 布局宽」同步缩放，保证与卡片轮廓同源。
     */
    fun setCard(slot: Int, coordinates: LayoutCoordinates?, radiusPx: Float) {
        if (slot < 0 || slot >= CAPACITY) return
        if (coordinates == null || !coordinates.isAttached) return
        coords[slot] = coordinates
        val radiusChanged = layoutRadius[slot] != radiusPx
        layoutRadius[slot] = radiusPx
        val geometryChanged = recompute(slot)
        if (geometryChanged || radiusChanged) {
            version++
            onInvalidate?.invoke()
        }
    }

    /**
     * 按当前锚点重算一槽的本地矩形。返回「变化是否超过亚像素阈值」。
     *
     * 阈值不是洁癖：非整数 density 下同一段布局在不同帧会差零点几个 px（矩阵连乘的舍入），
     * 逐位比较等于每帧判"变了"。**数值照写、版本不递增**——否则累积漂移会让登记值永远
     * 落后于真实位置，而 0.05px 的偏差在屏幕上看不出来。
     */
    private fun recompute(slot: Int): Boolean {
        if (slot < 0 || slot >= CAPACITY) return false
        val c = coords[slot] ?: return false
        if (!c.isAttached) return false
        val bounds = try {
            val a = anchor
            if (a != null && a.isAttached) {
                a.localBoundingBoxOf(c, clipBounds = false)
            } else {
                c.findRootCoordinates().localBoundingBoxOf(c, clipBounds = false)
            }
        } catch (_: Exception) {
            return false
        }
        val layoutWidth = c.size.width.toFloat()
        val scale = if (layoutWidth > 0f) bounds.width / layoutWidth else 1f
        val base = slot * 4
        val radius = layoutRadius[slot] * scale
        val changed = abs(rects[base] - bounds.left) > GEOMETRY_EPS ||
                abs(rects[base + 1] - bounds.top) > GEOMETRY_EPS ||
                abs(rects[base + 2] - bounds.right) > GEOMETRY_EPS ||
                abs(rects[base + 3] - bounds.bottom) > GEOMETRY_EPS ||
                abs(radii[base] - radius) > GEOMETRY_EPS
        rects[base] = bounds.left
        rects[base + 1] = bounds.top
        rects[base + 2] = bounds.right
        rects[base + 3] = bounds.bottom
        for (k in 0 until 4) radii[base + k] = radius
        return changed
    }
}

/** 页级玻璃槽位上限，与 shader 里 `cardRects` 的固定长度一一对应，改一处必须改另一处。 */
private const val CAPACITY = 88

/** 亚像素容差（px）：小于它的位移不递增版本号，见 [GlassCardRegistry.recompute]。 */
private const val GEOMETRY_EPS = 0.05f

/** 页级玻璃是否接管了课卡采样。非 null 时卡片不再各自 `drawBackdrop`。 */
val LocalGlassCards = staticCompositionLocalOf<GlassCardRegistry?> { null }

/**
 * 页级玻璃层：把「页内所有课卡的玻璃采样」合并成一次绘制。
 *
 * 平台差异：只有 Android 有 AGSL 折射（`actual` 真实现），桌面/iOS 走空实现 ——
 * 那两个平台的卡片仍按旧路径各自采样，视觉与之前完全一致。
 *
 * @param bakedLayer 共享模糊层烘焙好的壁纸纹理（`SharedBlurBackdrop.blurredLayer`）
 * @param sourceCoordinates 壁纸源层坐标，用来把纹理对齐到页面（同一层壁纸被所有卡共享）
 * @param refractionHeight 折射作用区高度（px），0 = 不折射
 * @param refractionAmount 折射位移量（px）
 */
@Composable
expect fun PageGlass(
    registry: GlassCardRegistry,
    backdrop: SharedBlurBackdrop,
    bakedLayer: GraphicsLayer?,
    bakeScale: Float,
    refractionHeight: Float,
    refractionAmount: Float,
    modifier: Modifier
)
