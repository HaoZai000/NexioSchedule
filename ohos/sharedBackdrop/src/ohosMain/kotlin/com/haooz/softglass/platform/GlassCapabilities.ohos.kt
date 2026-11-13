package com.haooz.softglass.platform

/**
 * 鸿蒙端玻璃能力：全开。
 *
 * 这套 CMP 批次里 `androidx.compose.ui.graphics.Shader` 是 `org.jetbrains.skia.Shader`
 * 的类型别名，`Paint.asFrameworkPaint()` / `ImageFilter.asComposeRenderEffect()` 也都是
 * 公开 API，所以 AGSL/SkSL 运行时着色器、RenderEffect 链与 MaskFilter 都能真实现，
 * 效果与 Android 对齐，不再退化为纯色描边。
 */
actual fun isRenderEffectSupported(): Boolean = true

actual fun isRuntimeShaderSupported(): Boolean = true

actual fun isRuntimeShaderEffectSupported(): Boolean = true

actual fun isSoftGlassShaderAvailable(): Boolean = true
