// Copyright (C) 2026 Haooz · AGPL-3.0-only
//
// shader 后端的平台契约：编译、uniform 灌装、采样器绑定与绘制。
//
// AGSL 与 SkSL 共用 [SoftGlassShader] 中的源码，本文件只声明各平台需要实现的差异。

package com.haooz.chedule.ui.glass

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap

/** 绘制趟的混合方式：主体使用常规混合，描边使用 LIGHTEN 仅提亮。 */
enum class SoftGlassBlend {
    /** 常规 source-over 混合，用于主体。 */
    NORMAL,
    /** 仅提亮的混合，用于描边叠加。 */
    LIGHTEN,
}

/**
 * 平台 shader 后端。
 *
 * AGSL（Android）与 SkSL（桌面 / iOS / 鸿蒙的 Skiko）使用同一门 GLSL 方言，
 * `half4 main(float2 fragCoord)`、`uniform shader` 与 `uniform float4` 均通用，
 * 因此 shader 源码在各平台共用；本类只封装平台差异：编译源码、设置 uniform、
 * 绑定位图采样器以及在 native canvas 上绘制矩形。
 *
 * 实例按尺寸与圆角共享，避免逐面板重复编译 shader。
 *
 * @param source shader 源码文本。
 */
expect class SoftGlassProgram(source: String) {
    fun setFloat(name: String, value: Float)
    fun setFloat2(name: String, x: Float, y: Float)
    fun setFloat3(name: String, x: Float, y: Float, z: Float)
    fun setFloat4(name: String, x: Float, y: Float, z: Float, w: Float)
    fun setInt(name: String, value: Int)

    /** 把 [bitmap] 绑定为 shader 中名为 [name] 的 `uniform shader`。 */
    fun setSampler(name: String, bitmap: ImageBitmap)

    /** 在 [canvas] 的 `(0,0)`–`(width,height)` 区域绘制一趟。 */
    fun draw(canvas: Canvas, width: Float, height: Float, blend: SoftGlassBlend)
}

/** 由 CPU 生成的 ARGB 像素创建位图，用于回写模糊结果。 */
expect fun createImageBitmapFromArgb(pixels: IntArray, width: Int, height: Int): ImageBitmap

/** 当前平台是否支持运行时 shader：Android 需 API 33，Skiko 各平台恒为 true。 */
expect fun isSoftGlassShaderAvailable(): Boolean

/**
 * 返回可被软件画布读取的位图。
 *
 * Android 的 `Config.HARDWARE` 位图不支持 `getPixels`，也无法绘入软件 Canvas，需复制一份；
 * Skiko 端无此区分，直接返回原位图。
 */
expect fun ImageBitmap.asSoftwareBitmap(): ImageBitmap
