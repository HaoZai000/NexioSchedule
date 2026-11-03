package com.kyant.backdrop.effects

import androidx.annotation.FloatRange
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.BackdropEffectScopeImpl
import com.kyant.backdrop.internal.RoundedRectRefractionShaderString
import com.kyant.backdrop.internal.RoundedRectRefractionWithDispersionShaderString
import com.kyant.backdrop.internal.RuntimeShaderEffect
import com.kyant.backdrop.internal.chain
import com.kyant.backdrop.isRuntimeShaderEffectSupported
import com.kyant.shapes.RoundedRectangularShape

fun BackdropEffectScope.lens(
    @FloatRange(from = 0.0) refractionHeight: Float,
    @FloatRange(from = 0.0) refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false
) {
    if (!isRuntimeShaderEffectSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding > 0f) {
        padding = (padding - refractionHeight).fastCoerceAtLeast(0f)
    }

    // 不支持的 shape（自定义 Shape / 非圆角矩形）直接跳过折射，**不能抛**——这里跑在 draw/measure 链路里，抛出去即整应用崩溃（曾因弹窗面板用自定义 AnimatedPanelRectShape 一开菜单就 FATAL）。
    val cornerRadii = cornerRadii ?: return
    // 只做记录；能否把颜色段折进合并着色器由 applyEffects() 末尾统一判定
    (this as? BackdropEffectScopeImpl)?.recordLensOp(
        refractionHeight = refractionHeight,
        refractionAmount = refractionAmount,
        depthEffect = depthEffect,
        chromaticAberration = chromaticAberration
    )
    val shader =
        if (!chromaticAberration) {
            obtainRuntimeShader(
                "Refraction",
                RoundedRectRefractionShaderString
            )
        } else {
            obtainRuntimeShader(
                "RefractionWithDispersion",
                RoundedRectRefractionWithDispersionShaderString
            )
        }
    // 折射 shader 在降采样缓冲上执行，像素空间 uniform 需同步 ×scale：等比例缩放可保持 SDF 比例与折射位移
    // 放大回原尺寸后与未降采样一致。scale 取当前缓冲实际降采样比例（非固定常量），与 DrawBackdropNode 的 n 对齐。
    val scale = this.downsampleScale
    shader.apply {
        setFloatUniform("size", size.width * scale, size.height * scale)
        setFloatUniform("offset", -padding * scale, -padding * scale)
        setFloatUniform(
            "cornerRadii",
            cornerRadii[0] * scale,
            cornerRadii[1] * scale,
            cornerRadii[2] * scale,
            cornerRadii[3] * scale
        )
        setFloatUniform("refractionHeight", refractionHeight * scale)
        setFloatUniform("refractionAmount", -refractionAmount * scale)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (chromaticAberration) {
            setFloatUniform("chromaticAberration", 1f)
        }
    }
    // 直接挂链，**不能**走 effect()：那个入口会记一笔「不可合并的自定义效果」，
    // 折射自己就成了合并的拦路石（曾经这样，导致整条合并逻辑静默失效）。
    lensShader = shader
    // 供全分辨率 ImageShader 直采路径重写 uniform（屏幕像素，未乘 downsample）
    lensRefractionHeight = refractionHeight
    lensRefractionAmount = refractionAmount
    lensCornerRadii = floatArrayOf(
        cornerRadii[0], cornerRadii[1], cornerRadii[2], cornerRadii[3]
    )
    renderEffect = renderEffect.chain(RuntimeShaderEffect(shader, "content"))
}

private val BackdropEffectScope.cornerRadii: FloatArray?
    get() = when (val shape = shape) {
        is RoundedRectangularShape -> {
            val corners = shape.corners(size, layoutDirection, this)
            floatArrayOf(
                corners.topLeft,
                corners.topRight,
                corners.bottomRight,
                corners.bottomLeft
            )
        }

        is AbsoluteRoundedCornerShape -> {
            val size = size
            val maxRadius = size.minDimension / 2f
            val topLeft = shape.topStart.toPx(size, this)
            val topRight = shape.topEnd.toPx(size, this)
            val bottomRight = shape.bottomEnd.toPx(size, this)
            val bottomLeft = shape.bottomStart.toPx(size, this)
            floatArrayOf(
                topLeft.fastCoerceAtMost(maxRadius),
                topRight.fastCoerceAtMost(maxRadius),
                bottomRight.fastCoerceAtMost(maxRadius),
                bottomLeft.fastCoerceAtMost(maxRadius)
            )
        }

        is CornerBasedShape -> {
            val size = size
            val maxRadius = size.minDimension / 2f
            val isLtr = layoutDirection == LayoutDirection.Ltr
            val topLeft =
                if (isLtr) shape.topStart.toPx(size, this)
                else shape.topEnd.toPx(size, this)
            val topRight =
                if (isLtr) shape.topEnd.toPx(size, this)
                else shape.topStart.toPx(size, this)
            val bottomRight =
                if (isLtr) shape.bottomEnd.toPx(size, this)
                else shape.bottomStart.toPx(size, this)
            val bottomLeft =
                if (isLtr) shape.bottomStart.toPx(size, this)
                else shape.bottomEnd.toPx(size, this)
            floatArrayOf(
                topLeft.fastCoerceAtMost(maxRadius),
                topRight.fastCoerceAtMost(maxRadius),
                bottomRight.fastCoerceAtMost(maxRadius),
                bottomLeft.fastCoerceAtMost(maxRadius)
            )
        }

        else -> null
    }
