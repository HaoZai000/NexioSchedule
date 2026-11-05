// Copyright (C) 2026 Haooz · AGPL-3.0-only
package com.kyant.capsule

// 玻璃「光学层」的圆角口径。注意：**这里不定义形状**。
// 形状只有一处真相 —— 本库 G2Continuity 生成的贝塞尔+圆弧 Path，由 Compose 裁剪。
// 玻璃 shader 只需要一个「到边界的距离」来算倒角带 / 法线 / 高光，不参与定形。

import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 柔光玻璃光学层的圆角 SDF。
 *
 * ## 为什么是普通圆弧，而不是「复刻 G2」
 *
 * 试过两条错路，都被打回：
 * 1. 让 [com.kyant.capsule.continuities.G2Continuity] 之外另起一套 p 范数超椭圆当默认形状
 *    （ext 0.40→0.5287、n 到 3.4）。位置拟合得上，但 **n>2 的超椭圆是方角带尖**，
 *    底栏 / 卡片一眼就尖；胶囊改原生 RoundRect 更退化成普通圆角 clip，G2 味全没了。
 * 2. 把玻璃 SDF 拟合成 G2 的 y(x) 曲线。曲线位置对了，法线/曲率却还是超椭圆的，
 *    高光在角部发折。
 *
 * 正解是**不要第二套形状**：可见轮廓完全交给 G2 Path 裁剪，shader 只算光学。
 * shader 里的圆弧取名义半径 r：
 * - 曲率连续（n=2），不会尖；
 * - 45° 对角点 = `r(1-1/√2) = 0.2929r`，与 G2 的 0.293r **几乎重合**（实测差 0.3px），
 *   所以倒角带、方向光仍在原来的位置；
 * - 圆弧的切点在 r，G2 的切点在 `1.5287r`，即**圆弧恒比 G2 更方、玻璃铺得更多**
 *   （逐点实测：圆弧边界 y 永远 ≤ G2 边界 y，最大正偏差 0）。
 *   多铺出来的部分被 G2 Path 裁掉，因此永远不会在角上露出底色缝。
 *
 * `DrawBackdropModifier.draw()` 把 effects 输出也包在 `clipPath(outline.path)` 里，
 * 所以 `runtimeShaderEffect`（顶栏按钮 / 下拉菜单）同样由 G2 形状决定边界。
 */
@Immutable
object SdfG2Corner {

    /** 生成 AGSL / SkSL 的圆角段；数值只有一个：调用方灌进来的 `uCornerRadii`。 */
    val shaderSource: String = """
float radiusAt(float2 c, float4 r) {
    if (c.x >= 0.0) {
        return c.y <= 0.0 ? r.y : r.z;
    } else {
        return c.y <= 0.0 ? r.x : r.w;
    }
}

// 标准圆角矩形的有符号距离（负在内）。只给光学用，可见边界由 Compose 的 G2 Path 裁剪。
float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// 边界外法线：直边段是轴向量，角部沿圆弧径向。
float2 roundRectGrad(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    float2 g;
    if (q.x > 0.0 && q.y > 0.0) {
        g = q / max(length(q), 1e-4);
    } else {
        g = (q.x > q.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
    }
    return sign(p) * g;
}
"""

    /** 与 [shaderSource] 的 `sdRoundRect` 逐项一致的 Kotlin 版，供测试核对「不露缝」。 */
    fun distance(px: Double, py: Double, halfW: Double, halfH: Double, r: Double): Double {
        val qx = kotlin.math.abs(px) - halfW + r
        val qy = kotlin.math.abs(py) - halfH + r
        val outside = sqrt(max(qx, 0.0) * max(qx, 0.0) + max(qy, 0.0) * max(qy, 0.0))
        return outside + minOf(max(qx, qy), 0.0) - r
    }

    /**
     * 角部边界在局部横坐标 `x` 处的纵坐标（角在原点，+x 沿上边、+y 沿左边）。
     * 圆心 `(r,r)`、半径 r 的圆弧；`x ≥ r` 后回到上边。
     */
    fun boundaryY(x: Double, r: Double): Double {
        if (x >= r) return 0.0
        return r - sqrt(max(0.0, r * r - (x - r) * (x - r)))
    }
}
