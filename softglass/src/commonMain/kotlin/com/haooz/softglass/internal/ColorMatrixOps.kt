package com.haooz.softglass.internal

/** 亮度 / 对比度 / 饱和度矩阵，4×5 展开，平移列单位为 0..255。 */
internal fun colorControlsColorMatrix(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f,
): FloatArray {
    val invSat = 1f - saturation
    val r = 0.213f * invSat
    val g = 0.715f * invSat
    val b = 0.072f * invSat
    val t = (0.5f - contrast * 0.5f + brightness) * 255f
    val cr = contrast * r
    val cg = contrast * g
    val cb = contrast * b
    val cs = contrast * saturation
    return floatArrayOf(
        cr + cs, cg, cb, 0f, t,
        cr, cg + cs, cb, 0f, t,
        cr, cg, cb + cs, 0f, t,
        0f, 0f, 0f, 1f, 0f,
    )
}
