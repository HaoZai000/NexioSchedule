// RuntimeShader 仅在 isRuntimeShaderSupported()（AGSL 能力检测）为真时才被创建/使用，
// 低版本流程不会执行到这里；自定义反射守卫 lint 无法识别，故整个文件豁免 NewApi。

package com.kyant.backdrop.edgelight

// RuntimeShader 等类型来自本模块根包 com.kyant.backdrop（commonMain 的 expect 接口），
// 而不是 android.graphics.RuntimeShader —— 后者是原生 Android 类，Kotlin/Native 上不存在。
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.PI
import kotlin.math.min

internal class EdgeLightShaderCache {
    private val cache = mutableMapOf<String, RuntimeShader>()

    fun getOrCreate(key: String, shaderString: String): RuntimeShader {
        return cache.getOrPut(key) {
            RuntimeShader(shaderString)
        }
    }

    fun clear() {
        cache.clear()
    }
}

internal fun Canvas.clipOutline(outline: Outline, path: Path?) {
    when (outline) {
        is Outline.Rectangle -> clipRect(outline.rect)
        is Outline.Rounded -> {
            path!!.rewind()
            path.addRoundRect(outline.roundRect)
            clipPath(path)
        }
        is Outline.Generic -> clipPath(outline.path)
    }
}

// blur / setRuntimeShader / isRuntimeShaderSupported 全部复用本模块根包的 expect/actual
// （com.kyant.backdrop.internal.blur / setRuntimeShader、com.kyant.backdrop.isRuntimeShaderSupported）。
// 原来这里自己用 asFrameworkPaint() + android.graphics 重新实现了一套，那是它进不了
// commonMain 的根因；重复定义还会与根包同名 internal 函数冲突，故整段删除。

internal fun getCornerRadii(
    shape: Shape,
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density
): FloatArray {
    val maxRadius = min(size.width, size.height) / 2f
    val cornerShape = shape as? CornerBasedShape
        ?: return FloatArray(4) { maxRadius }
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val topLeft = if (isLtr) cornerShape.topStart.toPx(size, density) else cornerShape.topEnd.toPx(size, density)
    val topRight = if (isLtr) cornerShape.topEnd.toPx(size, density) else cornerShape.topStart.toPx(size, density)
    val bottomRight = if (isLtr) cornerShape.bottomEnd.toPx(size, density) else cornerShape.bottomStart.toPx(size, density)
    val bottomLeft = if (isLtr) cornerShape.bottomStart.toPx(size, density) else cornerShape.bottomEnd.toPx(size, density)
    return floatArrayOf(
        min(topLeft, maxRadius),
        min(topRight, maxRadius),
        min(bottomRight, maxRadius),
        min(bottomLeft, maxRadius)
    )
}

internal fun createEdgeLightShader(
    edgeLight: EdgeLight,
    size: Size,
    shape: Shape,
    layoutDirection: LayoutDirection,
    density: Density,
    shaderCache: EdgeLightShaderCache
): RuntimeShader? {
    if (!isRuntimeShaderSupported()) return null

    val style = edgeLight.style
    val shaderString = when (style) {
        is EdgeLightStyle.Uniform -> return null
        is EdgeLightStyle.Directional -> EdgeLightDirectionalShaderString
        is EdgeLightStyle.Glow -> EdgeLightGlowShaderString
    }

    val key = "edgeLight_${style::class.simpleName}"
    val shader = shaderCache.getOrCreate(key, shaderString)

    val cornerRadii = getCornerRadii(shape, size, layoutDirection, density)
    val widthPx = with(density) { edgeLight.width.toPx() }
    val blurRadiusPx = with(density) { edgeLight.blurRadius.toPx() }

    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("cornerRadii", cornerRadii)
    shader.setFloatUniform("width", widthPx)
    shader.setFloatUniform("blurRadius", blurRadiusPx)
    shader.setFloatUniform("intensity", edgeLight.intensity)

    when (style) {
        is EdgeLightStyle.Uniform -> {}
        is EdgeLightStyle.Directional -> {
            // alpha 交给 GraphicsLayer.alpha 统一控制（与 backdrop 的 HighlightStyle 一致），
            // 这里必须传 alpha=1f，否则会与 intensity 相乘、描边整体偏暗。
            shader.setColorUniform("color", style.color.copy(alpha = 1f))
            shader.setFloatUniform("angle", style.angle * (PI / 180f).toFloat())
            shader.setFloatUniform("falloff", style.falloff)
        }
        is EdgeLightStyle.Glow -> {
            shader.setColorUniform("color", style.color.copy(alpha = 1f))
            shader.setFloatUniform("glowSize", style.glowSize)
        }
    }

    return shader
}
