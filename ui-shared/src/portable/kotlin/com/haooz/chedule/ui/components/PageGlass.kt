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
 * 课卡玻璃的页级注册表：卡片只登记「矩形 + 圆角」，由页内**一个**节点统一采样。
 * N 个采样节点会带来 N 个失效源（Compose 没有脏矩形，任一失效即整页重录）和 N 个折射 pass。
 *
 * 坐标一律是**相对玻璃节点**的本地坐标（锚点由 [attachAnchor] 给），不是 root。
 * pager 平移时卡片与节点一起动，本地矩形恒等 ⇒ 翻周不触发重建；竖向滚动才真的变。
 * 若改回「存 root 坐标、绘制期减节点原点」，两边浮点结果不逐位相同，等于每帧都判「几何变了」。
 *
 * 重录与否还取决于**采样窗口**（节点相对壁纸层的位移）—— 那是另一个失效源，见 PageGlass.android.kt。
 */
class GlassCardRegistry {

    /** 槽位上限：一页 7 天 × 12 节 = 84 格，另留 4 个余量给借位页与临时卡。 */
    val capacity: Int = CAPACITY

    /** 每张卡**相对玻璃节点**的 l/t/r/b（px）。写侧是布局回调，读侧是 draw 期。 */
    val rects = FloatArray(CAPACITY * 4)

    /** 四个角半径（px，tl/tr/br/bl）。本项目的卡四角相等，保留逐角以免将来变形。 */
    val radii = FloatArray(CAPACITY * 4)

    private val occupied = BooleanArray(CAPACITY)
    private val coords = arrayOfNulls<LayoutCoordinates>(CAPACITY)
    private val layoutRadius = FloatArray(CAPACITY)

    /** 坐标原点：页级玻璃节点自己。null 时退回 root 坐标（首帧 / 平台无实现），行为同改造前。 */
    private var anchor: LayoutCoordinates? = null

    /**
     * 几何版本号：卡片**相对玻璃节点**的几何变化时递增。普通字段，绘制期比较即可 ——
     * 挂成 snapshot state 又在 draw 期读，等于每帧主动失效。
     */
    var version: Int = 0
        private set

    /** 由页级玻璃节点登记：几何变了让它重画一次。 */
    var onInvalidate: (() -> Unit)? = null

    /** 已登记数。shader 循环上界用它，而不是固定 88 次。 */
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
                // 先占位，坐标由 onGloballyPositioned 补；补之前是零矩形，绘制侧会跳过
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

    /** 让某槽这一帧不参与合成（卡片被隐藏/拖走），槽位保留给同一张卡复用。 */
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
     * 登记/更新坐标原点。只在**对象**变化时重算：pager 平移不换坐标对象，所以翻周一次都不触发。
     * 用 [LayoutCoordinates] 本身而不是缓存的坐标数值 —— 后者慢一帧，慢一帧就又变成每帧抖。
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
     * 写入卡片几何，只在本地坐标真的变化时递增版本。
     *
     * 必须取「含图形变换的完整边界」：按压时卡片缩小 6%，用 `positionInRoot() + size` 会拿
     * 缩放后的左上角配原始尺寸，玻璃与表面色脱开。
     * 用 `localBoundingBoxOf(clipBounds = false)` 而不是 `boundsInRoot()`：后者会裁到祖先裁剪框，
     * 翻周时露出半边的页会把边缘卡片矩形压小。
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

    /** 按当前锚点重算一槽的本地矩形，返回变化是否超过亚像素阈值。 */
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
        // 数值照写、版本按阈值递增：逐位比较在非整数 density 下必然每帧触发；
        // 只写不报又会累积漂移，而 0.05px 的偏差看不出来。
        rects[base] = bounds.left
        rects[base + 1] = bounds.top
        rects[base + 2] = bounds.right
        rects[base + 3] = bounds.bottom
        for (k in 0 until 4) radii[base + k] = radius
        return changed
    }
}

/** 与 shader 里 `cardRects` 的固定长度一一对应，改一处必须改另一处。 */
private const val CAPACITY = 88

/** 亚像素容差（px），见 [GlassCardRegistry.recompute]。 */
private const val GEOMETRY_EPS = 0.05f

/** 页级玻璃是否接管了课卡采样。非 null 时卡片不再各自 `drawBackdrop`。 */
val LocalGlassCards = staticCompositionLocalOf<GlassCardRegistry?> { null }

/**
 * 页级玻璃层：把页内所有课卡的玻璃采样合并成一次绘制。
 * 只有 Android 有 AGSL 折射（真实现），桌面 / iOS / 鸿蒙为空实现，卡片仍走各自采样。
 *
 * @param bakedLayer 共享模糊层烘焙好的壁纸纹理
 * @param bakeScale 烘焙层相对源层的缩放；采样侧按 1/bakeScale 放大回全屏坐标
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
