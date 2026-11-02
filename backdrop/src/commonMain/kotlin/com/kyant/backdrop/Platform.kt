package com.kyant.backdrop

expect fun isRenderEffectSupported(): Boolean

/** AGSL 能作为 Compose `Paint` 的 shader 用（Android 33+ / Skia 后端）。 */
expect fun isRuntimeShaderSupported(): Boolean

/**
 * AGSL 能作为图层滤镜（`ImageFilter` → `RenderEffect`）用。
 *
 * 与 [isRuntimeShaderSupported] 分开：鸿蒙端没有 `asComposeShader`（Paint 路线断了），
 * 但 `ImageFilter.makeRuntimeShader` + `asComposeRenderEffect` 是通的，折射走这条路。
 */
expect fun isRuntimeShaderEffectSupported(): Boolean
