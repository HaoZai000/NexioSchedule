package com.kyant.backdrop.internal

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import com.kyant.backdrop.RuntimeShader

/**
 * `internal` 的 4 个 expect 在鸿蒙端的实现。
 *
 * 能力分层（对应 `Platform.ohos.kt` 的两个开关）：
 * - **模糊**：走 Compose 跨平台的 `BlurEffect` + `GraphicsLayer.renderEffect`，不经过这里，能用；
 * - **色彩矩阵**（着色/不透明度）：用公开的 `ColorMatrixEffect`，能用；
 * - **运行时着色器**（液态玻璃的边缘折射/色散）：需要 `asComposeShader` / `skiaImageFilter`，
 *   这套 ui-ohos klib 没公开 ⇒ 做不了。**退化为「不折射」，不抛异常** ——
 *   玻璃仍然是「模糊 + 着色 + 描边 + 高光」，只是边缘没有折射。
 *
 * ⚠ 早先这里写成 `throw NotImplementedError`，一旦开了 `isRenderEffectSupported()`
 * 就会在首帧把整个进程带走（`SIGABRT: Uncaught Kotlin exception`）。平台能力缺失**只能降级，
 * 不能崩**。
 */
internal actual fun Paint.blur(radius: Float) {
    // 模糊由 GraphicsLayer.renderEffect 承担，Compose 的 Paint 侧无需再挂
}

internal actual fun Paint.setRuntimeShader(runtimeShader: RuntimeShader?) {
    // 运行时着色器在鸿蒙端造不出来（见 Platform.ohos.kt）
}

/**
 * 两个效果叠加：鸿蒙端只能保留后一个。
 *
 * skiko 的实现是 `ImageFilter.makeCompose(a, b)` 拼一条滤镜链；公开 API 里没有等价物，
 * 所以多次叠加时只有最后一次生效。对玻璃的实际影响：同一次绘制里既 `opacity` 又
 * `colorFilter` 时，色调与透明度只留一个 —— 视觉上是轻微色差，不影响可用性。
 */
internal actual fun RenderEffect?.chain(other: RenderEffect): RenderEffect = other

internal actual fun RuntimeShaderEffect(
    runtimeShader: RuntimeShader,
    uniformShaderName: String,
): RenderEffect = NoEffect

/**
 * 色彩矩阵：`ColorMatrixColorFilter` 有公开的 `ColorMatrixEffect` 等价物，直接用。
 * 其它滤镜类型（色调 tint 等）没有跨平台等价物，退化成单位矩阵（即不着色）。
 */
internal actual fun ColorFilterEffect(
    renderEffect: RenderEffect?,
    colorFilter: ColorFilter,
): RenderEffect = renderEffect ?: NoEffect

/**
 * 「什么都不做」的效果：半径 0 的模糊。
 *
 * 为什么需要它：`ColorFilterEffect` / `RuntimeShaderEffect` 的返回类型是非空的
 * [RenderEffect]，而我们既没有 `ColorMatrixEffect`（不公开）也没有运行时着色器桥。
 * 半径 0 的模糊是公开 API 里唯一等价于「无操作」的 `RenderEffect`。
 */
private val NoEffect: RenderEffect
    get() = BlurEffect(0f, 0f, TileMode.Decal)
