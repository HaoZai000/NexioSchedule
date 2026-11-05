// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.glass

import org.jetbrains.skia.RuntimeEffect
import kotlin.test.Test

/**
 * 柔光玻璃 shader 的编译冒烟测试。
 *
 * 两端共用同一份源码（Android 走 AGSL、桌面 / iOS / 鸿蒙走 SkSL），语法错在真机上只会表现为
 * 「玻璃退回白面」—— 被 `runCatching` 吞掉，极难定位。这里用 Skia 的运行时编译器先把语法
 * 过一遍：能过 SkSL 的源码基本都能过 AGSL（两者是同一门方言）。
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
}
