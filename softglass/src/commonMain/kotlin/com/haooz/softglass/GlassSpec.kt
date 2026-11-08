package com.haooz.softglass

import androidx.compose.runtime.Immutable
import com.haooz.softglass.glass.GlassFill
import com.haooz.softglass.glass.GlassTone
import com.haooz.softglass.glass.NoneFill
import com.haooz.softglass.glass.SoftGlassStyle
import com.haooz.softglass.glass.TokenTone

/** 一份完整的玻璃渲染参数与它的基调、填充策略。 */
@Immutable
data class GlassSpec(
    val style: SoftGlassStyle,
    val tone: GlassTone = TokenTone,
    val fill: GlassFill = NoneFill,
)

/** 悬浮控件的样式：套上基调与填充，非柔光档关闭折射和方向光。 */
fun GlassSpec.chromeStyle(
    level: GlassMaterialLevel,
    isDark: Boolean,
    fill: GlassFill = this.fill,
    tone: GlassTone = this.tone,
): SoftGlassStyle {
    val resolved = style.copy(fill = fill, tone = tone).filled(isDark)
    return if (level == GlassMaterialLevel.SOFT) {
        resolved
    } else {
        resolved.copy(refractionScale = 0f, edgeHighlight = false)
    }
}

object GlassPreset {
    /** 当前应用口径：与既有默认样式逐字段一致。 */
    val Nexio: GlassSpec = GlassSpec(SoftGlassStyle())

    fun of(token: GlassToken): GlassSpec = token.spec
}
