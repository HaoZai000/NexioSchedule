package com.kyant.backdrop

/**
 * 鸿蒙端的渲染能力，三条分开看：
 *
 * - [isRenderEffectSupported]：模糊走 Compose 的 `BlurEffect` + `GraphicsLayer.renderEffect`；
 * - [isRuntimeShaderEffectSupported]：AGSL 当图层滤镜用 —— `ImageFilter.makeRuntimeShader` +
 *   `asComposeRenderEffect`，这两座桥 ui-ohos klib 都公开，折射/色散走这条；
 * - [isRuntimeShaderSupported]：AGSL 当 Compose `Paint` 的 shader 用 —— 缺 `asComposeShader`
 *   （skia Shader → Compose Shader），做不了，高光/描边着色仍降级。
 */
actual fun isRenderEffectSupported(): Boolean = true

actual fun isRuntimeShaderSupported(): Boolean = false

actual fun isRuntimeShaderEffectSupported(): Boolean = true
