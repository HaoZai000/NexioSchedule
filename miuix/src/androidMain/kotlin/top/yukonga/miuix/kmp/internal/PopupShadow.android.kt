package top.yukonga.miuix.kmp.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.BlurMaskFilter

/**
 * Android 实现 —— 保持与迁移前完全一致的绘制路径。
 *
 * 环形路径：外圈 CW 让填充覆盖整块，内部矩形 CCW 反向挖空 → 得到「面包圈」，
 * 再交给 BlurMaskFilter 做模糊描边。这一段是原 fork 的代码，逐字保留以保证零回归。
 */
actual fun DrawScope.drawBlurredRingShadow(
    size: Size,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float,
    spread: Float,
    blurRadius: Float,
    color: Color,
) {
    if (blurRadius <= 0f) return

    // 注意：传入的 color 已含增益后的 alpha（调用方算好），这里只做一次
    // Color → ARGB 转换，不要再乘任何系数，否则会与原实现对不上。
    val path = android.graphics.Path().apply {
        addRoundRect(
            left - spread, top - spread,
            left + width + spread, top + height + spread,
            radius + spread, radius + spread,
            android.graphics.Path.Direction.CW
        )
        addRoundRect(
            left, top,
            left + width, top + height,
            radius, radius,
            android.graphics.Path.Direction.CCW
        )
    }
    val paint = android.graphics.Paint().apply {
        this.color = color.toArgbInt()
        maskFilter = BlurMaskFilter(blurRadius.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(path, paint) }
}

/** Color 的分量直接可用，只有「非预乘 ARGB 整数」这一步需要转。 */
private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255f + 0.5f).toInt().coerceIn(0, 255),
    (red * 255f + 0.5f).toInt().coerceIn(0, 255),
    (green * 255f + 0.5f).toInt().coerceIn(0, 255),
    (blue * 255f + 0.5f).toInt().coerceIn(0, 255),
)
