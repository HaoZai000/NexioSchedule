/** [drawBlurredPathShadow] 的 Android 实现 —— 与 SearchBar 迁移前的手绘代码逐字同源。 */
package top.yukonga.miuix.kmp.internal

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

actual fun DrawScope.drawBlurredPathShadow(
    path: Path,
    color: Color,
    blurRadius: Float,
) {
    val paint = Paint().apply {
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}
