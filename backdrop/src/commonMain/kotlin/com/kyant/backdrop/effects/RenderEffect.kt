package com.kyant.backdrop.effects

import androidx.compose.ui.graphics.RenderEffect
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.BackdropEffectScopeImpl
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.internal.RuntimeShaderEffect
import com.kyant.backdrop.internal.chain
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderEffectSupported
import kotlin.contracts.ExperimentalContracts
import org.intellij.lang.annotations.Language

fun BackdropEffectScope.effect(effect: RenderEffect) {
    if (!isRenderEffectSupported()) return

    renderEffect = renderEffect.chain(effect)
    // 自定义链无法拆解，整条保持原样（不参与「颜色折进折射」的合并）
    (this as? BackdropEffectScopeImpl)?.recordRawOp()
}

@OptIn(ExperimentalContracts::class)
fun BackdropEffectScope.runtimeShaderEffect(
    key: String,
    @Language("AGSL") shaderString: String,
    uniformShaderName: String,
    block: RuntimeShader.() -> Unit
) {
    if (!isRuntimeShaderEffectSupported()) return

    val effect =
        RuntimeShaderEffect(
            runtimeShader = obtainRuntimeShader(key, shaderString).apply(block),
            uniformShaderName = uniformShaderName
        )
    renderEffect = renderEffect.chain(effect)
    // 自写 AGSL 同理：无法与折射合并，整条退回旧链
    (this as? BackdropEffectScopeImpl)?.recordRawOp()
}
