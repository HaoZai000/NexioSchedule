package com.kyant.backdrop.internal

/*
 * 4×5 颜色矩阵的构造与复合。
 * 这些矩阵唯一的新用途是被折进折射着色器（见 BackdropEffectScopeImpl.compileChain）：
 * 一条 effect 里叠多个颜色算子时，先在这里复合，最后只往着色器传一份。
 */

/** 把 [dest] 置为单位矩阵（等价于「不改变颜色」）。 */
internal fun identityColorMatrix(dest: FloatArray) {
    dest.fill(0f)
    dest[0] = 1f
    dest[6] = 1f
    dest[12] = 1f
    dest[18] = 1f
}

/**
 * 复合：`target = op ∘ target`，即先在像素上做 [target] 原有的变换，再做 [op]。
 *
 * 展开写 4×5 而不是 4×4：平移项要跟着乘一遍系数再相加。
 */
internal fun multiplyColorMatrix(target: FloatArray, op: FloatArray) {
    val before = target.copyOf()
    for (row in 0 until 4) {
        val r = row * 5
        for (col in 0 until 4) {
            target[r + col] =
                op[r] * before[col] +
                    op[r + 1] * before[5 + col] +
                    op[r + 2] * before[10 + col] +
                    op[r + 3] * before[15 + col]
        }
        target[r + 4] =
            op[r + 4] +
                op[r] * before[4] +
                op[r + 1] * before[9] +
                op[r + 2] * before[14] +
                op[r + 3] * before[19]
    }
}

/** 纯饱和度矩阵（对比度 1、亮度 0），也就是 `vibrancy()` 用的那一条。 */
internal fun saturationColorMatrix(saturation: Float): FloatArray {
    val invSat = 1f - saturation
    val r = 0.213f * invSat
    val g = 0.715f * invSat
    val b = 0.072f * invSat
    return floatArrayOf(
        r + saturation, g, b, 0f, 0f,
        r, g + saturation, b, 0f, 0f,
        r, g, b + saturation, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

/** 亮度 / 对比度 / 饱和度矩阵，算式与原先直接构造 `ColorMatrixColorFilter` 时逐项一致。 */
internal fun colorControlsColorMatrix(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f
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
        0f, 0f, 0f, 1f, 0f
    )
}

/** 只作用于 alpha 的矩阵。 */
internal fun opacityColorMatrix(alpha: Float): FloatArray = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, alpha, 0f
)
