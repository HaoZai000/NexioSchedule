package com.haooz.softglass.shape

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** 玻璃光学层的圆角距离场；可见轮廓仍由 G2 Path 裁剪，这里只算倒角带与法线。 */
@Immutable
object SdfG2Corner {

    val shaderSource: String = """
float radiusAt(float2 c, float4 r) {
    if (c.x >= 0.0) {
        return c.y <= 0.0 ? r.y : r.z;
    } else {
        return c.y <= 0.0 ? r.x : r.w;
    }
}

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

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

    /** 与 [shaderSource] 的 `sdRoundRect` 逐项一致的 Kotlin 版。 */
    fun distance(px: Double, py: Double, halfW: Double, halfH: Double, r: Double): Double {
        val qx = abs(px) - halfW + r
        val qy = abs(py) - halfH + r
        val outside = sqrt(max(qx, 0.0) * max(qx, 0.0) + max(qy, 0.0) * max(qy, 0.0))
        return outside + minOf(max(qx, qy), 0.0) - r
    }

    /** 角部边界在局部横坐标 `x` 处的纵坐标。 */
    fun boundaryY(x: Double, r: Double): Double =
        if (x >= r) 0.0 else r - sqrt(max(0.0, r * r - (x - r) * (x - r)))
}
