/** [drawBlurredPathShadow] 的 Skia 实现（iOS / Desktop / wasm）：Skia MaskFilter 与 Android 同源。 */
package top.yukonga.miuix.kmp.internal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint as SkiaPaint

actual fun DrawScope.drawBlurredPathShadow(
    path: Path,
    color: Color,
    blurRadius: Float,
) {
    val paint = SkiaPaint().apply {
        this.color = color.toArgb()
        maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blurRadius)
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawPath(path.asSkiaPath(), paint)
    }
}
