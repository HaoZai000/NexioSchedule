// miuix-blur 的 RenderEffect 桥（鸿蒙端）。
// 三个 skiko 桥接扩展都不公开，一律降级：
// - chain 只留后一个（不做合成）；
// - progressiveStackEffect 返回 null，按上游契约回退到 uniform draw path；
// - runtimeShaderEffect 返回半径 0 的模糊 = 无操作。
package top.yukonga.miuix.kmp.blur.internal

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import top.yukonga.miuix.kmp.blur.BackdropEffectScopeImpl
import top.yukonga.miuix.kmp.shader.RuntimeShader

internal actual fun RenderEffect?.chain(other: RenderEffect): RenderEffect = other

internal actual fun runtimeShaderEffect(
    runtimeShader: RuntimeShader,
    uniformShaderName: String,
): RenderEffect = NoEffect

/** 半径 0 的模糊是公开 API 里唯一的「空 RenderEffect」。 */
private val NoEffect: RenderEffect
    get() = BlurEffect(0f, 0f, TileMode.Decal)

internal actual fun progressiveStackEffect(
    scope: BackdropEffectScopeImpl,
    sigma0X: Float,
    sigma0Y: Float,
    sigma1X: Float,
    sigma1Y: Float,
    sigma2X: Float,
    sigma2Y: Float,
    ax: Float,
    ay: Float,
    projFull: Float,
    projZero: Float,
    curve: Float,
    preEffect: RenderEffect?,
    postEffect: RenderEffect?,
    nativeSharpEnd: Boolean,
): RenderEffect? = null
