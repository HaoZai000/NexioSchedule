// Copyright (C) 2026 Haooz · AGPL-3.0-only
//
// shader 源码的编译冒烟测试：在 CI 阶段验证 AGSL / SkSL 语法。

package com.haooz.chedule.ui.glass

import org.jetbrains.skia.RuntimeEffect
import kotlin.test.Test

/**
 * shader 源码的编译冒烟测试。
 *
 * 各平台共用同一份源码（Android 使用 AGSL，桌面 / iOS / 鸿蒙使用 SkSL）。
 * 语法错误在运行时会被 `runCatching` 捕获并回退为纯色表面，不易定位，
 * 因此此处用 Skia 的运行时编译器预先校验；两种方言语法一致。
 */
class SoftGlassShaderCompileTest {

    @Test
    fun bodyShaderCompiles() {
        RuntimeEffect.makeForShader(SOFT_GLASS_SHADER)
    }

    @Test
    fun rimShaderCompiles() {
        RuntimeEffect.makeForShader(SOFT_GLASS_RIM_SHADER)
    }

    @Test
    fun liveShaderCompiles() {
        RuntimeEffect.makeForShader(SOFT_GLASS_LIVE_SHADER)
    }
}
