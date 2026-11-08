package com.haooz.chedule.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.haooz.softglass.shape.Continuity
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 材质画布相对可见底栏向外的余量。
 *
 * `drawBackdrop` 采样时 `clipRect(0,0,w,h)`、表面色也只铺节点矩形，形状画到节点外的部分
 * 既没采样也没底色。而胶囊按压缩放、圆钮上下拖动都要越界，所以画布做大一圈，
 * [BarShape] 内部再把原点补回来。
 */
internal val MaterialBleedX = 16.dp
internal val MaterialBleedY = 28.dp

/** 滑块（可按压的选中玻璃）左右各收窄的量；宽度减 2×本值、位置补 1×本值保持居中 */
internal val BottomBarSelectorInset = 0.5.dp

/**
 * 融合颈的完整几何：两条**三次**贝塞尔的八个点 + 山丘参数。
 *
 * 每条边 `pu → c1u → c2u → cu`，两个控制点分别沿「胶囊在 pu 处的切线」和
 * 「圆钮在 cu 处的切线」。这样颈边在本体上是**相切**出去的，而不是垂直插入 ——
 * 垂直插入时颈越细、与本体夹角越大（接近 90°），凹角越尖；相切之后夹角恒为 0。
 *
 * 山丘（bulge）是颈出现**之前**就有的隆起，所以 [hasNeck] 为 false 时这八个点是无效的，
 * 但 [bulgeH] 等参数仍然有效 —— 折射必须把它算进去，否则那一段轮廓会凭空少一圈。
 */
internal class BarNeckGeometry(
    val hasNeck: Boolean,
    val pu: Offset,
    val c1u: Offset,
    val c2u: Offset,
    val cu: Offset,
    val pl: Offset,
    val c1l: Offset,
    val c2l: Offset,
    val cl: Offset,
    val bulgeH: Float,
    val bulgeAngle: Float,
    val phiPill: Float,
    val phiCircle: Float,
    val gaussEdge: Float,
)

/** 角度差折到 (-π, π]：atan 在 ±π 处跳变，不折的话张角会被劈成两半。 */
private fun fusionWrapAngle(delta: Float): Float =
    delta - 6.2831853f * floor(delta / 6.2831853f + 0.5f)

/**
 * 山丘在角度 [angle] 上的高度系数（0..1），**带端点窗**的版本。
 *
 * 归一化高斯本身在张角端点只是函数值为 0，一阶导并不为 0，山丘根部与本体之间就是一道折角；
 * 再乘一扇 `1-(2t-1)^4` 窗之后端点值和斜率都归零。
 *
 * 只有 [FusionPairShape]（双胶囊焊缝）用它；底栏 [BarShape] 与
 * [barNeckGeometryPx] 仍走各自的高斯，和折射 shader 逐字一致。
 */
internal fun fusionBumpAt(
    angle: Float,
    center: Float,
    phi: Float,
    sigma: Float,
    gaussEdge: Float,
): Float {
    val t = (fusionWrapAngle(angle - center) + phi) / (2f * phi)
    if (t <= 0f || t >= 1f) return 0f
    val dx = t - 0.5f
    val rawG = exp(-(dx * dx) / (2f * sigma * sigma))
    val bell = ((rawG - gaussEdge) / (1f - gaussEdge)).coerceIn(0f, 1f)
    val u = 2f * t - 1f
    return bell * (1f - u * u * u * u)
}

/** 角度 [a] 处的单位切线，方向取与 [toward] 同侧的那个。 */
private fun tangentAt(a: Float, toward: Offset): Offset {
    val tx = -sin(a)
    val ty = cos(a)
    val dot = tx * toward.x + ty * toward.y
    return if (dot >= 0f) Offset(tx, ty) else Offset(-tx, -ty)
}

/**
 * 算出融合颈的八个点。公式与 [BarShape] 内部逐字一致；没有颈时返回 null。
 *
 * 之所以要单独抽一份：折射必须和真实轮廓共用同一份几何，各算各的必然对不上。
 * [fusion] 必须与形状用的是同一个实例，否则山丘 / 颈的尺寸会对不上。
 *
 * @param pillRadiusX 本体端头的横向半径。**非等比缩放后的胶囊端头是椭圆**（rx ≠ ry），
 *   按正圆算会把轴端点摆错位置，颈的锚点落进本体内部，接缝处就是一道折痕。
 *   传 null（或两轴相等）= 正圆，与旧行为逐位一致。
 * @param pillRadiusY 本体端头的纵向半径，同上。
 * @param ellipseTangents 端头是椭圆时置 true：颈边控制点按椭圆的真实切线求，
 *   不能用极角切线（`(-sin, cos)` 只对正圆成立）。
 */
internal fun barNeckGeometryPx(
    pillCenterX: Float,
    pillCenterY: Float,
    pillHalfWidth: Float,
    pillHalfHeight: Float,
    circleCenterX: Float,
    circleCenterY: Float,
    circleRadiusX: Float,
    circleRadiusY: Float,
    maxNeckHalfPx: Float,
    fusion: LiquidFusion,
    density: Density,
    pillRadiusX: Float? = null,
    pillRadiusY: Float? = null,
    ellipseTangents: Boolean = false,
): BarNeckGeometry? {
    val circlePillRadius = minOf(pillHalfWidth, pillHalfHeight)
    if (circlePillRadius <= 0f) return null
    // 端头半径：给了就用椭圆的，没给退回正圆（min(半宽, 半高)）
    val prx = pillRadiusX ?: circlePillRadius
    val pry = pillRadiusY ?: circlePillRadius
    val pillRadius = minOf(prx, pry)

    /** 本体端头在极角 [a] 上的半径。两轴相等时直接回转出正圆，保证旧路径逐位一致。 */
    fun pillRAt(a: Float): Float =
        if (prx == pry) prx else ellipseRadiusAt(prx, pry, a)

    /** 椭圆在极角 [a] 处的真实切线；正圆直接用解析式。方向取与 [toward] 同侧。 */
    fun tangentOf(a: Float, rx: Float, ry: Float, toward: Offset): Offset {
        if (!ellipseTangents || rx == ry) return tangentAt(a, toward)
        val e = 0.002f
        val r0 = ellipseRadiusAt(rx, ry, a - e)
        val r1 = ellipseRadiusAt(rx, ry, a + e)
        val dx = r1 * cos(a + e) - r0 * cos(a - e)
        val dy = r1 * sin(a + e) - r0 * sin(a - e)
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-6f)
        val tx = dx / len
        val ty = dy / len
        val dot = tx * toward.x + ty * toward.y
        return Offset(if (dot >= 0f) tx else -tx, if (dot >= 0f) ty else -ty)
    }

    val axisLeft = pillCenterX - pillHalfWidth + prx
    val axisRight = pillCenterX + pillHalfWidth - prx
    val axisX = circleCenterX.coerceIn(minOf(axisLeft, axisRight), maxOf(axisLeft, axisRight))
    val vx = circleCenterX - axisX
    val vy = circleCenterY - pillCenterY
    val dist = sqrt(vx * vx + vy * vy)
    if (dist < 0.5f) return null
    val ang = atan2(vy, vx)
    val circleDirR = ellipseRadiusAt(circleRadiusX, circleRadiusY, ang)
    val surfaceGap = dist - pillRAt(ang) - circleDirR

    // 山丘先算：它在颈出现**之前**就有了（gap 从 10dp 开始鼓），
    // 所以即使没有颈也要把这组参数交给折射，否则那段轮廓会凭空少一圈。
    val neckThresholdPx = with(density) { (fusion.bulgeMaxDp * 2f).dp.toPx() }
    val bulgeRangePx = with(density) { fusion.bulgeRangeDp.dp.toPx() }
    val bulgeT = if (surfaceGap >= bulgeRangePx) {
        0f
    } else {
        ((bulgeRangePx - surfaceGap) / (bulgeRangePx - neckThresholdPx)).coerceIn(0f, 1f)
    }
    val bulgeH = with(density) { fusion.bulgeMaxDp.dp.toPx() } * (bulgeT * bulgeT)
    val gaussEdge = exp(-0.25f / (2f * fusion.bulgeSigma * fusion.bulgeSigma))
    val circleAvgR = (circleRadiusX + circleRadiusY) / 2f
    // 山丘张角在纵向上的铺开：用纵向半径（端头是椭圆时纵向才是决定张角的那一轴）
    val phiPill = ((bulgeH * fusion.bulgeWidthRatio) / pry)
        .coerceIn(fusion.bulgeMinAngle, fusion.bulgeMaxAngle)
    val phiCircle = ((bulgeH * fusion.bulgeWidthRatio) / circleAvgR)
        .coerceIn(fusion.bulgeMinAngle, fusion.bulgeMaxAngle)
    val pi = PI.toFloat()

    fun bulgeOnly() = BarNeckGeometry(
        hasNeck = false,
        pu = Offset.Zero,
        c1u = Offset.Zero,
        c2u = Offset.Zero,
        cu = Offset.Zero,
        pl = Offset.Zero,
        c1l = Offset.Zero,
        c2l = Offset.Zero,
        cl = Offset.Zero,
        bulgeH = bulgeH,
        bulgeAngle = ang,
        phiPill = phiPill,
        phiCircle = phiCircle,
        gaussEdge = gaussEdge,
    )

    if (surfaceGap >= neckThresholdPx) return bulgeOnly()
    val k = ((neckThresholdPx - surfaceGap) / neckThresholdPx).coerceIn(0f, 1f).pow(fusion.rampPower)
    if (k <= 0.001f) return bulgeOnly()

    val overlapLeadPx = with(density) { fusion.overlapLeadDp.dp.toPx() }
    val effDist = (dist - overlapLeadPx).coerceAtLeast(1f)
    // 重叠时两团已经连成一片：用交叠的弦高补一段颈，腰才不至于比自然重叠更细。
    // 横向用 prx 判交叠位置，纵向用 pry 取弦高（端头是椭圆时这两轴不是一回事）。
    val intersectHh = if (effDist < prx + circleDirR) {
        val proj =
            (effDist * effDist + prx * prx - circleDirR * circleDirR) / (2f * effDist)
        sqrt((pry * pry - proj * proj).coerceAtLeast(0f))
    } else {
        0f
    }
    val neck = (maxNeckHalfPx * k + intersectHh * fusion.neckOverlapGrow).coerceAtMost(pillRadius)

    fun bumpAt(angle: Float, center: Float, phi: Float): Float {
        val t = (angle - (center - phi)) / (2f * phi)
        if (t <= 0f || t >= 1f) return 0f
        val dx = t - 0.5f
        val rawG = exp(-(dx * dx) / (2f * fusion.bulgeSigma * fusion.bulgeSigma))
        return ((rawG - gaussEdge) / (1f - gaussEdge)).coerceIn(0f, 1f)
    }

    fun onPill(a: Float): Offset {
        val r = pillRAt(a) + bulgeH * bumpAt(a, ang, phiPill)
        return Offset(axisX + r * cos(a), pillCenterY + r * sin(a))
    }

    fun onCircle(a: Float): Offset {
        val r = ellipseRadiusAt(circleRadiusX, circleRadiusY, a) +
            bulgeH * bumpAt(a, ang + pi, phiCircle)
        return Offset(circleCenterX + r * cos(a), circleCenterY + r * sin(a))
    }

    val phi1 = asin((neck / (pry + bulgeH)).coerceIn(-1f, 1f))
    val circleNearR = ellipseRadiusAt(circleRadiusX, circleRadiusY, ang + pi)
    val phi2 = asin((neck / (circleNearR + bulgeH).coerceAtLeast(0.5f)).coerceIn(-1f, 1f))

    val a1u = ang + phi1
    val a1l = ang - phi1
    val a2u = ang + pi - phi2
    val a2l = ang + pi + phi2
    val pu = onPill(a1u)
    val pl = onPill(a1l)
    val cu = onCircle(a2u)
    val cl = onCircle(a2l)

    // 两端控制点沿各自表面的切线：颈边相切出去，与本体夹角恒为 0
    val chordU = cu - pu
    val chordL = cl - pl
    val lenU = sqrt(chordU.x * chordU.x + chordU.y * chordU.y).coerceAtLeast(0.001f)
    val lenL = sqrt(chordL.x * chordL.x + chordL.y * chordL.y).coerceAtLeast(0.001f)
    val tu = tangentOf(a1u, prx, pry, chordU)
    val tcu = tangentOf(a2u, circleRadiusX, circleRadiusY, Offset(-chordU.x, -chordU.y))
    val tl = tangentOf(a1l, prx, pry, chordL)
    val tcl = tangentOf(a2l, circleRadiusX, circleRadiusY, Offset(-chordL.x, -chordL.y))

    return BarNeckGeometry(
        hasNeck = true,
        pu = pu,
        c1u = Offset(pu.x + tu.x * lenU / 3f, pu.y + tu.y * lenU / 3f),
        c2u = Offset(cu.x + tcu.x * lenU / 3f, cu.y + tcu.y * lenU / 3f),
        cu = cu,
        pl = pl,
        c1l = Offset(pl.x + tl.x * lenL / 3f, pl.y + tl.y * lenL / 3f),
        c2l = Offset(cl.x + tcl.x * lenL / 3f, cl.y + tcl.y * lenL / 3f),
        cl = cl,
        bulgeH = bulgeH,
        bulgeAngle = ang,
        phiPill = phiPill,
        phiCircle = phiCircle,
        gaussEdge = gaussEdge,
    )
}

/** 椭圆在角度 [a] 方向上的极半径（不是参数方程的 t）。rx == ry 时退化成正圆。 */
internal fun ellipseRadiusAt(rx: Float, ry: Float, a: Float): Float {
    val c = cos(a)
    val s = sin(a)
    val d = (ry * c) * (ry * c) + (rx * s) * (rx * s)
    return if (d <= 1e-6f) 0f else rx * ry / sqrt(d)
}

/**
 * 底栏专用折射 shader。
 *
 * 这里用解析 SDF 描述整条轮廓：胶囊（圆角矩形）∪ 圆钮（圆），两者用 `smin` 融出颈，
 * 于是边缘一圈折射会沿着「胶囊 + 融合 + 圆钮」的完整轮廓走。
 */
internal const val BottomBarRefractionShader = """
uniform shader content;

uniform float2 offset;
uniform float2 pillCenter;
uniform float2 pillHalf;
uniform float pillRadius;
uniform float2 circleCenter;
uniform float2 circleRadii;
uniform float2 pu;
uniform float2 c1u;
uniform float2 c2u;
uniform float2 cu;
uniform float2 pl;
uniform float2 c1l;
uniform float2 c2l;
uniform float2 cl;
uniform float neckRadius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float bulgeH;
uniform float bulgeAngle;
uniform float bulgePhiPill;
uniform float bulgePhiCircle;
uniform float bulgeGaussEdge;
uniform float bulgeSigma;

float sdRoundRect(float2 p, float2 c, float2 h, float r) {
    float2 q = abs(p - c) - (h - r);
    return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - r;
}

// angle 来自 atan()，在 ±π 处跳变；圆钮的山丘中心恒为 ang+π，
// 角度差先折到 (-π, π] 再比，否则有一半山丘落在张角外（融合区沿水平轴一条硬缝）。
float bumpAt(float angle, float center, float phi) {
    float da = angle - center;
    da = da - 6.2831853 * floor(da / 6.2831853 + 0.5);
    float t = (da + phi) / (2.0 * phi);
    if (t <= 0.0 || t >= 1.0) {
        return 0.0;
    }
    float dx = t - 0.5;
    float rawG = exp(-(dx * dx) / (2.0 * bulgeSigma * bulgeSigma));
    return clamp((rawG - bulgeGaussEdge) / (1.0 - bulgeGaussEdge), 0.0, 1.0);
}

float sdEllipse(float2 p, float2 c, float2 ab) {
    float2 d = p - c;
    float t = atan(d.y / max(ab.y, 1e-4), d.x / max(ab.x, 1e-4));
    for (int i = 0; i < 4; i++) {
        float ct = cos(t);
        float st = sin(t);
        float2 q = float2(ab.x * ct, ab.y * st);
        float2 dq = float2(-ab.x * st, ab.y * ct);
        float2 d2q = float2(-ab.x * ct, -ab.y * st);
        float f = dot(q - d, dq);
        float df = dot(dq, dq) + dot(q - d, d2q);
        t = t - f / (abs(df) > 1e-4 ? df : 1e-4);
    }
    float2 q = float2(ab.x * cos(t), ab.y * sin(t));
    float dist = length(d - q);
    return (length(d / ab) < 1.0) ? -dist : dist;
}

float2 cBez(float2 a, float2 b, float2 c, float2 d, float t) {
    float mt = 1.0 - t;
    float mt2 = mt * mt;
    float t2 = t * t;
    return mt2 * mt * a + (3.0 * mt2 * t) * b + (3.0 * mt * t2) * c + t2 * t * d;
}

float2 dBez(float2 a, float2 b, float2 c, float2 d, float t) {
    float mt = 1.0 - t;
    return 3.0 * mt * mt * (b - a) + 6.0 * mt * t * (c - b) + 3.0 * t * t * (d - c);
}

float2 d2Bez(float2 a, float2 b, float2 c, float2 d, float t) {
    float mt = 1.0 - t;
    return 6.0 * mt * (c - 2.0 * b + a) + 6.0 * t * (d - 2.0 * c + b);
}

float nearestT(float2 p, float2 a, float2 b, float2 c, float2 d) {
    float bestD = 1e9;
    float bestT = 0.0;
    for (int k = 0; k < 2; k++) {
        float t = (k == 0) ? 0.25 : 0.75;
        for (int i = 0; i < 5; i++) {
            float2 q = cBez(a, b, c, d, t);
            float2 dq = dBez(a, b, c, d, t);
            float2 d2q = d2Bez(a, b, c, d, t);
            float f = dot(q - p, dq);
            float df = dot(dq, dq) + dot(q - p, d2q);
            t = clamp(t - f / (abs(df) > 1e-4 ? df : 1e-4), 0.0, 1.0);
        }
        float dd = length(cBez(a, b, c, d, t) - p);
        if (dd < bestD) {
            bestD = dd;
            bestT = t;
        }
    }
    return bestT;
}

float sdNeck(float2 p) {
    float tu = nearestT(p, pu, c1u, c2u, cu);
    float tl = nearestT(p, pl, c1l, c2l, cl);
    float2 qu = cBez(pu, c1u, c2u, cu, tu);
    float2 ql = cBez(pl, c1l, c2l, cl, tl);

    float2 du = dBez(pu, c1u, c2u, cu, tu);
    float2 dl = dBez(pl, c1l, c2l, cl, tl);
    if (tu < 0.02 || tu > 0.98) {
        du = cu - pu;
    }
    if (tl < 0.02 || tl > 0.98) {
        dl = cl - pl;
    }
    float sU = (du.x * (p.y - qu.y) - du.y * (p.x - qu.x)) / max(length(du), 1e-4);
    float sL = (dl.x * (p.y - ql.y) - dl.y * (p.x - ql.x)) / max(length(dl), 1e-4);

    float2 ax = cu - pu;
    ax = ax / max(length(ax), 1e-4);
    float dLong = max(-dot(p - pu, ax), dot(p - cu, ax));
    return max(sU, -sL) + max(0.0, dLong);
}

float sminSoft(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float bodySdf(float2 p) {
    float2 q = p - pillCenter;
    float axisHalf = max(pillHalf.x - pillRadius, 0.0);
    float2 nearest = float2(clamp(q.x, -axisHalf, axisHalf), 0.0);
    float2 d = q - nearest;
    float distP = length(d);
    float rEff = pillRadius;
    if (bulgeH > 0.01 && distP > 1e-4) {
        rEff = pillRadius + bulgeH * bumpAt(atan(d.y, d.x), bulgeAngle, bulgePhiPill);
    }
    float dPill = distP - rEff;

    float dCirc = sdEllipse(p, circleCenter, circleRadii);
    if (bulgeH > 0.01) {
        float2 dc = p - circleCenter;
        dCirc = dCirc - bulgeH * bumpAt(atan(dc.y, dc.x), bulgeAngle + 3.14159265, bulgePhiCircle);
    }
    return min(dPill, dCirc);
}

float sceneSdf(float2 p) {
    float d = bodySdf(p);
    if (neckRadius > 0.001) {
        d = min(d, sdNeck(p));
    }
    return d;
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 p = coord + offset;
    float sd = sceneSdf(p);
    float rh = refractionHeight;
    if (-sd >= rh) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / rh) * refractionAmount;

    float eps = 0.75;
    float2 g = float2(
        sceneSdf(p + float2(eps, 0.0)) - sceneSdf(p - float2(eps, 0.0)),
        sceneSdf(p + float2(0.0, eps)) - sceneSdf(p - float2(0.0, eps))
    );
    float gl = length(g);
    float2 grad = gl > 0.0001 ? g / gl : float2(0.0, 0.0);
    return content.eval(coord + d * grad);
}
"""

/** [BarShape] 的参数：全是 draw 期才读的 lambda，拖动 / 按压都不进组合期 */
internal class BarShapeParams(
    val panelOffset: () -> Float,
    /** 胶囊按压缩放，只作用于胶囊 */
    val pressScale: () -> Float,
    val trailingTranslation: () -> Offset,
    /** 圆钮的按压缩放 + 拉伸倍率（rx / ry，1 = 静止未按下） */
    val trailingScale: () -> Offset,
    /** 圆钮占掉的额外宽度（间距 + 直径 + 拖动余量），px；0 = 没有圆钮 */
    val extraPx: Float,
    /** 胶囊与圆钮的静止间距，px */
    val gapPx: Float,
    /** 融球颈的最大半高，px = 参考尺寸 × [LiquidFusion.neckStrength] */
    val maxNeckHalfPx: Float,
    /** 融球手感；必须与 [barNeckGeometryPx] 收到的是同一个实例 */
    val fusion: LiquidFusion,
    /** 画布向外的余量，px；形状内部按它把原点补回来 */
    val bleedX: Float,
    val bleedY: Float,
)

/**
 * 整条底栏的轮廓：胶囊 ∪ 圆钮 ∪ 融球颈。
 *
 * 三者并进同一条 Path，所以整块材质是一个轮廓 —— 折射、高光、描边都沿融合后的边缘走，
 * 交界处不会出现两套各自独立的边。
 */
internal class BarShape(private val p: BarShapeParams) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val h = size.height - p.bleedY * 2f
        val bx = p.bleedX
        val cy = p.bleedY + h / 2f
        val radius = h / 2f
        val pillPx = size.width - p.extraPx - bx * 2f

        val s = p.pressScale()
        val po = p.panelOffset()
        val halfW = pillPx / 2f * s
        val halfH = h / 2f * s
        val cx = bx + pillPx / 2f + po
        val pillRadius = minOf(halfW, halfH)

        val capsule = Path().apply {
            val outline = Continuity.Default.createCapsuleOutline(
                Size(halfW * 2f, halfH * 2f)
            )
            val continuous = (outline as? Outline.Generic)?.path
            if (continuous != null) {
                addPath(continuous, Offset(cx - halfW, cy - halfH))
                close()
            } else {
                addRoundRect(
                    RoundRect(
                        left = cx - halfW,
                        top = cy - halfH,
                        right = cx + halfW,
                        bottom = cy + halfH,
                        radiusX = pillRadius,
                        radiusY = pillRadius,
                    )
                )
            }
        }
        if (p.extraPx <= 0f) return Outline.Generic(capsule)

        val t = p.trailingTranslation()
        // 沿拖动方向拉伸后是 rx ≠ ry 的椭圆
        val ts = p.trailingScale()
        val circleRx = radius * ts.x
        val circleRy = radius * ts.y
        val circleCx = bx + pillPx + p.gapPx + radius + t.x
        val circleCy = cy + t.y
        val circle = Path().apply {
            addOval(Rect(circleCx - circleRx, circleCy - circleRy, circleCx + circleRx, circleCy + circleRy))
        }

        val merged = Path()
        merged.op(capsule, circle, PathOperation.Union)

        // 融合强度只看两团材质表面之间的最短距离，不看拖动位移：
        // 胶囊是「轴线膨胀 pillRadius」，圆钮是椭圆，用轴线最近点计算，
        // 圆钮被拖到胶囊正上 / 正下方时同样成立。按 tab、整体放大这类「形状变了但没靠近」
        // 的情况因此不会凭空长出颈。
        val axisLeft = cx - halfW + pillRadius
        val axisRight = cx + halfW - pillRadius
        val axisX = circleCx.coerceIn(minOf(axisLeft, axisRight), maxOf(axisLeft, axisRight))
        val vx = circleCx - axisX
        val vy = circleCy - cy
        val dist = sqrt(vx * vx + vy * vy)
        val ang = atan2(vy, vx)

        val fusion = p.fusion
        val circleAvgR = (circleRx + circleRy) / 2f
        // 沿连线方向量间隙：纵向拉伸后（ry > rx）用平均半径会把间隙算小，山丘和桥提前出现
        val circleDirR = ellipseRadiusAt(circleRx, circleRy, ang)
        val surfaceGap = dist - pillRadius - circleDirR
        // 相交判定整体外移到吸附范围之外，gap 从 10dp 一路贴近到重叠都由同一个量驱动，
        // hh 在 gap = lead 处恰为 0，桥与山丘同时从零宽起步。
        // 阈值 = 2 × 山丘最大高度，让「两座山丘碰上」与「桥出现」落在同一时刻
        val neckThresholdPx = with(density) { (fusion.bulgeMaxDp * 2f).dp.toPx() }
        val k = if (surfaceGap >= neckThresholdPx) {
            0f
        } else {
            ((neckThresholdPx - surfaceGap) / neckThresholdPx).coerceIn(0f, 1f).pow(fusion.rampPower)
        }
        if (dist < 0.5f) return Outline.Generic(merged)
        // 连线方向 / 法向单位向量，吸附凸起与融合桥共用
        val pi = PI.toFloat()

        // 山丘强度只由间隙决定，融合之后也不撤：两座山丘先贴到一起，桥再把那道缝填实
        val bulgeRangePx = with(density) { fusion.bulgeRangeDp.dp.toPx() }
        val bulgeT = if (surfaceGap >= bulgeRangePx) {
            0f
        } else {
            ((bulgeRangePx - surfaceGap) / (bulgeRangePx - neckThresholdPx)).coerceIn(0f, 1f)
        }

        // 高度分布取归一化高斯：两端恰好 0（与本体完全重合）、导数为 0（无棱）。
        // 张角与高度外提成同一组参数，桥的锚点才能按角度查到山丘的实际半径 ——
        // 一律按最高点算的话，锚点处山丘已衰减，锚点悬空就是上下各一根尖刺。
        val bulgeH = with(density) { fusion.bulgeMaxDp.dp.toPx() } * (bulgeT * bulgeT)
        val gaussEdge = exp(-0.25f / (2f * fusion.bulgeSigma * fusion.bulgeSigma))
        val phiPill = ((bulgeH * fusion.bulgeWidthRatio) / pillRadius)
            .coerceIn(fusion.bulgeMinAngle, fusion.bulgeMaxAngle)
        val phiCircle = ((bulgeH * fusion.bulgeWidthRatio) / circleAvgR)
            .coerceIn(fusion.bulgeMinAngle, fusion.bulgeMaxAngle)
        val bulgeCenterCircle = ang + pi

        /** 某个角度上的山丘高度系数（0..1）；落在张角之外为 0 */
        fun bumpAt(angle: Float, center: Float, phi: Float): Float {
            val t = (angle - (center - phi)) / (2f * phi)
            if (t <= 0f || t >= 1f) return 0f
            val dx = t - 0.5f
            val rawG = exp(-(dx * dx) / (2f * fusion.bulgeSigma * fusion.bulgeSigma))
            return ((rawG - gaussEdge) / (1f - gaussEdge)).coerceIn(0f, 1f)
        }

        /** 山丘扇形；[rx] / [ry] 必须与本体一致，否则扇形的底边压不到边界上，union 后是一圈毛刺 */
        fun hillPath(
            ccx: Float,
            ccy: Float,
            rx: Float,
            ry: Float,
            center: Float,
            phi: Float,
        ): Path? {
            if (bulgeH < 0.3f) return null
            return Path().apply {
                val steps = 56
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val a = (center - phi) + 2f * phi * t
                    val r = ellipseRadiusAt(rx, ry, a) + bulgeH * bumpAt(a, center, phi)
                    val x = ccx + r * cos(a)
                    val y = ccy + r * sin(a)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }

        fun bulgeAt(): Path? {
            if (bulgeH < 0.3f) return null
            val a = hillPath(axisX, cy, pillRadius, pillRadius, ang, phiPill)
            val b = hillPath(circleCx, circleCy, circleRx, circleRy, bulgeCenterCircle, phiCircle)
            if (a == null || b == null) return null
            return Path().apply { op(a, b, PathOperation.Union) }
        }

        fun unionWith(extra: Path?): Outline.Generic? {
            if (extra == null) return null
            val out = Path()
            return if (out.op(merged, extra, PathOperation.Union)) Outline.Generic(out) else null
        }

        val bulge = bulgeAt()

        // 还没进入融合区间：只有两座山丘各自鼓着
        if (k <= 0.001f) return unionWith(bulge) ?: Outline.Generic(merged)

        // 两段相加：k 覆盖刚进入融合那一段，hh 在重叠后接管。取大不行 —— neckBase 会成天花板。

        // 颈的八个点直接取共享几何（与折射同一份）：
        // 两端控制点沿本体表面的**切线**，颈边相切出去，与胶囊 / 圆钮的夹角恒为 0。
        // 之前控制点朝连线收（垂直插入），颈越细夹角越接近 90°，四个凹角就越尖。
        val neckGeom = barNeckGeometryPx(
            pillCenterX = cx,
            pillCenterY = cy,
            pillHalfWidth = halfW,
            pillHalfHeight = halfH,
            circleCenterX = circleCx,
            circleCenterY = circleCy,
            circleRadiusX = circleRx,
            circleRadiusY = circleRy,
            maxNeckHalfPx = p.maxNeckHalfPx,
            fusion = fusion,
            density = density,
        )
        if (neckGeom == null || !neckGeom.hasNeck) {
            return unionWith(bulge) ?: Outline.Generic(merged)
        }

        val neckPath = Path().apply {
            moveTo(neckGeom.pu.x, neckGeom.pu.y)
            cubicTo(
                neckGeom.c1u.x, neckGeom.c1u.y,
                neckGeom.c2u.x, neckGeom.c2u.y,
                neckGeom.cu.x, neckGeom.cu.y,
            )
            // 这一笔走在圆内部、会被圆盖住，只为闭合轮廓
            lineTo(neckGeom.cl.x, neckGeom.cl.y)
            cubicTo(
                neckGeom.c2l.x, neckGeom.c2l.y,
                neckGeom.c1l.x, neckGeom.c1l.y,
                neckGeom.pl.x, neckGeom.pl.y,
            )
            close()
        }

        // 山丘负责「贴住之前就有的肉」，桥负责「贴住之后那道缝」
        val withNeck = Path()
        if (!withNeck.op(merged, neckPath, PathOperation.Union)) return Outline.Generic(merged)
        if (bulge != null) {
            val combined = Path()
            if (combined.op(withNeck, bulge, PathOperation.Union)) return Outline.Generic(combined)
        }
        return Outline.Generic(withNeck)
    }
}
