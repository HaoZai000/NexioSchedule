package com.haooz.softglass.backdrop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.haooz.softglass.platform.GlassRuntimeShader
import com.haooz.softglass.platform.GlassShaderRegistry

/** 效果链作用域，算子在 [applyEffects] 的 lambda 里按顺序叠加。 */
sealed interface GlassEffectScope : Density {
    val size: Size
    val downsampleScale: Float
    val layoutDirection: LayoutDirection
    val shape: Shape
    var padding: Float
    var renderEffect: RenderEffect?

    fun obtainShader(key: String, source: String): GlassRuntimeShader
}

internal abstract class GlassEffectScopeImpl : GlassEffectScope {
    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var size: Size = Size.Unspecified
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override var padding: Float = 0f
    override var renderEffect: RenderEffect? = null
    override val downsampleScale: Float = 1f

    override fun obtainShader(key: String, source: String): GlassRuntimeShader =
        GlassShaderRegistry.obtain(key, source)

    fun update(scope: DrawScope): Boolean {
        val changed = scope.density != density || scope.fontScale != fontScale ||
            scope.size != size || scope.layoutDirection != layoutDirection
        if (changed) {
            density = scope.density
            fontScale = scope.fontScale
            size = scope.size
            layoutDirection = scope.layoutDirection
        }
        return changed
    }

    fun applyEffects(effects: GlassEffectScope.() -> Unit) {
        padding = 0f
        renderEffect = null
        effects()
    }

    fun reset() {
        density = 1f
        fontScale = 1f
        size = Size.Unspecified
        layoutDirection = LayoutDirection.Ltr
        padding = 0f
        renderEffect = null
    }
}
