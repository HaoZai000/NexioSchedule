package com.haooz.softglass.backdrop.effects

import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.internal.glassChain
import com.haooz.softglass.internal.glassRuntimeShaderEffect
import com.haooz.softglass.platform.GlassRuntimeShader
import com.haooz.softglass.platform.isRuntimeShaderEffectSupported

fun GlassEffectScope.runtimeShaderEffect(
    key: String,
    shaderString: String,
    uniformShaderName: String,
    block: GlassRuntimeShader.() -> Unit,
) {
    if (!isRuntimeShaderEffectSupported()) return
    val shader = obtainShader(key, shaderString).apply(block)
    renderEffect = renderEffect.glassChain(glassRuntimeShaderEffect(shader, uniformShaderName))
}
