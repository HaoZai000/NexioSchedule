package com.kyant.backdrop

/**
 * 鸿蒙端（ohosArm64）的平台能力。
 *
 * 两条能力分开看，别再混成一句「都没有」：
 *
 * - **[isRenderEffectSupported] = true**：模糊走的是 Compose 跨平台的
 *   `BlurEffect` + `GraphicsLayer.renderEffect`（见 `backdrops/SharedBlurBackdrop`），
 *   不需要 skiko 桥。鸿蒙跑 skia 后端，这条链路是真的在跑 —— 所以壁纸采样 + 高斯模糊可用。
 *
 * - **[isRuntimeShaderSupported] = false**：折射/色散要的是 `asComposeShader` /
 *   `skiaPaint` / `skiaImageFilter` 这三座桥，而这套 CMP 批次（1.9.2-1.2.0-08）的
 *   ui-ohos klib **没有公开**它们（`compose.ohos.skikoOverlay` 只对 fusion-renderer 生效）。
 *   所以玻璃有模糊、有底色、有描边与高光，**没有边缘折射**。
 *   这是平台能力缺口，不是 UI 分支：调用点一行没改，`isRuntimeShaderSupported()` 在
 *   backdrop 里本来就是"没有折射就退回普通模糊"的开关（Android 低版本同一条路）。
 *
 * 追平口径：CPF 暴露 skiko 桥后把这里改 true，并把 `RuntimeShader.ohos.kt` /
 * `internal/RenderEffect.ohos.kt` 换成 skikoMain 那份即可。
 */
actual fun isRenderEffectSupported(): Boolean = true

actual fun isRuntimeShaderSupported(): Boolean = false
