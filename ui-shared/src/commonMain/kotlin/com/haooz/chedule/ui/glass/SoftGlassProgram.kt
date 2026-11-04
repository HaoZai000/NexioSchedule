// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.glass

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap

/** 玻璃绘制两趟的混合方式：主体普通叠，描边用 LIGHTEN 只提亮。 */
enum class SoftGlassBlend {
    NORMAL,
    LIGHTEN,
}

/**
 * 平台 shader 后端。
 *
 * AGSL（Android）与 SkSL（桌面 / iOS / 鸿蒙的 Skiko）是同一门 GLSL 方言：
 * `half4 main(float2 fragCoord)`、`uniform shader`、`uniform float4` 都通用。
 * 所以 **shader 源码（`SoftGlassShader.kt`）四端共用**，这里只抽平台差异：
 * 编译 shader、设 uniform、把位图挂成采样器、往 native canvas 上画一个 rect。
 *
 * 实例由 `SoftGlassProgramCache` 按尺寸 / 圆角 / 样式共享，避免每张卡重复编译 AGSL。
 */
expect class SoftGlassProgram(source: String) {
    fun setFloat(name: String, value: Float)
    fun setFloat2(name: String, x: Float, y: Float)
    fun setFloat3(name: String, x: Float, y: Float, z: Float)
    fun setFloat4(name: String, x: Float, y: Float, z: Float, w: Float)
    fun setInt(name: String, value: Int)

    /** 把 [bitmap] 挂到 shader 里的 `uniform shader [name]`。 */
    fun setSampler(name: String, bitmap: ImageBitmap)

    /** 在 [canvas] 的 `(0,0)`–`(width,height)` 上画一遍。 */
    fun draw(canvas: Canvas, width: Float, height: Float, blend: SoftGlassBlend)
}

/** 用 CPU 生成的 ARGB 像素建位图（模糊结果回写用）。 */
expect fun createImageBitmapFromArgb(pixels: IntArray, width: Int, height: Int): ImageBitmap

/** 当前平台是否具备运行时 shader（Android 需要 API 33；Skiko 三端恒为 true）。 */
expect fun isSoftGlassShaderAvailable(): Boolean

/**
 * 保证位图能被软件画布读取。
 * Android 的 `Config.HARDWARE` 位图不能 `getPixels` / 不能画进软件 Canvas，需要先拷一份；
 * Skiko 端没有这个区分，原样返回。
 */
expect fun ImageBitmap.asSoftwareBitmap(): ImageBitmap
