package com.haooz.chedule.ui.components.slider

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.internal.drawBlurredPathShadow

/**
 * 滑块的绘制层，移植自 COUI 的 `CouiSliderRenderer` 与 `TexturedThumbRenderer`。
 *
 * 轨道形状由宿主传入的 [shape] 决定，同一份 Path 也用于裁剪跟手光效。所有几何量按
 * density 换算，`Path` 与 `Paint` 复用，避免每帧分配。
 *
 * @param density 屏幕密度。
 * @param shape 轨道形状；默认 [NexioCapsuleShape]。
 * @param layoutDirection 布局方向，用于形状解析 RTL。
 */
internal class NexioSliderRenderer(
    private val density: Float,
    private val shape: Shape,
    private val layoutDirection: LayoutDirection,
) {
    private val backgroundPath = Path()
    private var backgroundBounds: Rect? = null
    private val progressPath = Path()
    private var progressBounds: Rect? = null
    private var progressRect = Rect.Zero
    private val outlineDensity = Density(density)
    private val thumbPath = Path()
    private val ringPath = Path()
    private val layerPaint = Paint()

    /**
     * 绘制整条滑条。
     *
     * @param scope 目标绘制域。
     * @param height 绘制区域高度（像素）。
     * @param motion 交互状态，提供进度、形变与闪光强度。
     * @param colors 配色。
     * @param enabled 是否启用；禁用时跳过纹理滑块。
     * @param rtl 是否从右往左布局。
     * @param tickCount 等分刻度数量；为 0 表示不画等分刻度。
     * @param tickFractions 自定义锚点位置（0..1），非空时优先于 [tickCount]。
     */
    fun render(
        scope: DrawScope,
        height: Float,
        motion: NexioSliderMotion,
        colors: NexioSliderColors,
        enabled: Boolean,
        rtl: Boolean,
        tickCount: Int,
        tickFractions: List<Float>? = null,
    ) {
        val centerY = (height.toInt() / 2).toFloat()
        val start = 16f * density
        val length = motion.length
        val fraction = motion.visualFraction
        val thumbX = start + length * axis(fraction, rtl)
        val backgroundRadius = backgroundHeight(motion) / 2f
        val background = Rect(
            start - backgroundRadius,
            centerY - backgroundRadius,
            start + length + backgroundRadius,
            centerY + backgroundRadius,
        )
        updatePath(backgroundPath, backgroundBounds, background)
        backgroundBounds = background
        scope.drawPath(backgroundPath, colors.inactiveTrack)

        val trackRadius = 13f * density
        val originX = if (rtl) start + length else start
        val progress = Rect(
            min(originX, thumbX) - trackRadius,
            centerY - trackRadius,
            max(originX, thumbX) + trackRadius,
            centerY + trackRadius,
        )
        updatePath(progressPath, progressBounds, progress)
        progressBounds = progress
        progressRect = progress
        scope.drawPath(progressPath, colors.activeTrack)

        val thumbRadius = motion.thumbRadius.value * density
        drawKeyPoints(scope, tickFractions, tickCount, start, length, rtl, centerY, thumbX, thumbRadius, fraction, colors)
        drawThumb(scope, thumbX, centerY, thumbRadius, motion, colors, enabled)
        drawGlitter(scope, motion, rtl)
    }

    /** 把光效裁剪到轨道形状内。 */
    fun clipTrack(scope: DrawScope, block: DrawScope.() -> Unit) {
        scope.clipPath(backgroundPath) { block() }
    }

    /** 轨道高度：静止 26dp，按下时最多加厚到 32dp。 */
    private fun backgroundHeight(motion: NexioSliderMotion): Float {
        val height = (26f + 6f * motion.heightFraction) * density
        return if (motion.heightFraction > 0f && motion.heightFraction < 1f) height.roundToInt().toFloat() else height
    }

    /** 归一化位置的 RTL 翻转。 */
    private fun axis(fraction: Float, rtl: Boolean): Float = if (rtl) 1f - fraction else fraction

    /**
     * 绘制锚点刻度，被 thumb 遮住的部分跳过。
     *
     * 用普通循环而不是 Sequence，避免每帧为每个刻度装箱一个 Float。
     */
    private fun drawKeyPoints(
        scope: DrawScope,
        tickFractions: List<Float>?,
        tickCount: Int,
        start: Float,
        length: Float,
        rtl: Boolean,
        centerY: Float,
        thumbX: Float,
        thumbRadius: Float,
        fraction: Float,
        colors: NexioSliderColors,
    ) {
        val markRadius = 3f * density
        if (!tickFractions.isNullOrEmpty()) {
            for (i in tickFractions.indices) {
                val mark = tickFractions[i]
                val x = start + length * axis(mark, rtl)
                if (abs(x - thumbX) + markRadius > thumbRadius) {
                    scope.drawCircle(
                        color = if (mark <= fraction) colors.activeTick else colors.inactiveTick,
                        radius = markRadius,
                        center = Offset(x, centerY),
                    )
                }
            }
        } else if (tickCount > 0) {
            for (i in 0..tickCount) {
                val mark = i.toFloat() / tickCount
                val x = start + length * axis(mark, rtl)
                if (abs(x - thumbX) + markRadius > thumbRadius) {
                    scope.drawCircle(
                        color = if (mark <= fraction) colors.activeTick else colors.inactiveTick,
                        radius = markRadius,
                        center = Offset(x, centerY),
                    )
                }
            }
        }
    }

    /** 末端闪光：按闪光强度叠加一层横向渐变。 */
    private fun drawGlitter(scope: DrawScope, motion: NexioSliderMotion, rtl: Boolean) {
        val alpha = (255.0 * (1.0 - exp(-ln(85.0) / 360.0 * motion.glitter.value.coerceAtLeast(0f)))).roundToInt()
        if (alpha <= 0) return
        // 原厂把横向渐变按进度矩形缩放: 起点透明, 终点 0xa0ffffff。
        val brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            1f to Color(0xA0FFFFFF),
            startX = if (rtl) progressRect.right else progressRect.left,
            endX = if (rtl) progressRect.left else progressRect.right,
        )
        scope.drawPath(progressPath, brush, alpha = alpha.coerceIn(0, 255) / 255f)
    }

    private fun updatePath(path: Path, previous: Rect?, bounds: Rect) {
        if (previous == bounds) return
        val outline = shape.createOutline(Size(bounds.width, bounds.height), layoutDirection, outlineDensity)
        path.reset()
        path.addOutline(outline)
        path.translate(Offset(bounds.left, bounds.top))
    }

    /**
     * 绘制 thumb。
     *
     * 静止态是带投影的实心圆；按下后按纹理进度叠三层（竖向渐变、内影、白色覆盖），
     * 三层都裁剪在圆内。
     */
    private fun drawThumb(
        scope: DrawScope,
        x: Float,
        y: Float,
        radius: Float,
        motion: NexioSliderMotion,
        colors: NexioSliderColors,
        enabled: Boolean,
    ) {
        val textureAlpha = (motion.texture.value * 2.55f).toInt().coerceIn(0, 255)
        if (!enabled || textureAlpha <= 0) {
            if (enabled) {
                // 原厂 setShadowLayer(4dp, 0, 2dp)：等价于向下偏移 2dp 的模糊圆。
                val shadowTop = y - radius + 2f * density
                thumbPath.reset()
                thumbPath.addOval(Rect(x - radius, shadowTop, x + radius, shadowTop + radius * 2f))
                scope.drawBlurredPathShadow(thumbPath, colors.thumbShadow, 4f * density)
            }
            scope.drawCircle(colors.thumb, radius, Offset(x, y))
            return
        }
        val bounds = Rect(x - radius, y - radius, x + radius, y + radius)
        val canvas = scope.drawContext.canvas
        layerPaint.alpha = textureAlpha / 255f
        canvas.saveLayer(bounds, layerPaint)
        // layerA: 由下往上 0xe6ffffff → 0x8cffffff 的竖向渐变。
        scope.drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color(0x8CFFFFFF),
                0.8f to Color(0xE6FFFFFF),
                1f to Color(0xE6FFFFFF),
                startY = bounds.top,
                endY = bounds.bottom,
            ),
            topLeft = bounds.topLeft,
            size = bounds.size,
            cornerRadius = CornerRadius(radius, radius),
        )
        // layerB: 原厂内影的 stroke、blur、留白均使用物理像素, 不乘 density。
        thumbPath.reset()
        thumbPath.addOval(bounds)
        scope.clipPath(thumbPath) {
            drawCircle(Color(0x77FFFFFF), radius, Offset(x, y))
            val inner = (radius - 10f).coerceAtLeast(0f)
            ringPath.reset()
            ringPath.addOval(bounds)
            ringPath.addOval(Rect(x - inner, y - inner, x + inner, y + inner))
            ringPath.fillType = PathFillType.EvenOdd
            drawBlurredPathShadow(ringPath, Color.White, 5f)
        }
        canvas.restore()
        scope.drawCircle(
            color = Color.White,
            radius = radius,
            center = Offset(x, y),
            alpha = (motion.thumbCover.value * 2.55f).toInt().coerceIn(0, 255) / 255f,
        )
    }
}
