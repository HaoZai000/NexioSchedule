package com.haooz.chedule.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.haooz.softglass.shape.Continuity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 两颗胶囊并排、中间按 [LiquidFusion] 缝合的轮廓，**每一半各自可形变**。
 *
 * 和底栏（[BarShape]）同一套手感：左胶囊当「本体」（轴线膨胀端头半径），右胶囊的左端头当「另一团」，
 * 间隙就是表面 gap —— 走进 [LiquidFusion] 的阈值就鼓山丘，再靠近就把两团的距离场用
 * 平滑最小值（smooth-min）焊成一片，两颗按钮读起来是一块玻璃。
 *
 * 参数全是 draw 期才读的 lambda：按压缩放、跟手位移都不进组合期，和 [BarShape] 同理。
 * 形变只改几何，不做 graphicsLayer 变换 —— 因为整块材质是一条 Path，缩放某一半只能改 Path。
 */
internal class FusionPairParams(
    /** 两半的静止间距，px */
    val gapPx: Float,
    val fusion: LiquidFusion,
    /** 材质画布相对可见矩形的余量，px；形状内部按它把原点补回来 */
    val bleedX: Float,
    val bleedY: Float,
    /** 左半的 (sx, sy) 倍率：1 = 静止。按压 + 沿拖动方向拉伸 */
    val leftScale: () -> Offset,
    /** 左半的跟手位移，px */
    val leftTranslation: () -> Offset,
    val rightScale: () -> Offset,
    val rightTranslation: () -> Offset,
    /** 把每次求出的轮廓交出去：按压高光要按真实轮廓裁，不能只在矩形节点里画 */
    val outlineSink: (Path) -> Unit = {},
)

/**
 * 某一帧的形状快照 —— **轮廓和折射必须读同一份**，各算各的迟早对不上。
 *
 * 坐标是材质节点坐标（含 bleed）；[neck] 为 null 表示这两团离得太远，连山丘都没有。
 */
internal class FusionPairFrame(
    val leftCx: Float,
    val leftCy: Float,
    val leftHalfW: Float,
    val leftHalfH: Float,
    val leftRx: Float,
    val leftRy: Float,
    val rightCx: Float,
    val rightCy: Float,
    val rightHalfW: Float,
    val rightHalfH: Float,
    val rightRx: Float,
    val rightRy: Float,
    /** 静止（未形变）胶囊尺寸：Continuity 只吃等比尺寸，路径按它生成再整体缩放 */
    val baseW: Float,
    val baseH: Float,
    val leftSx: Float,
    val leftSy: Float,
    val rightSx: Float,
    val rightSy: Float,
    val neck: BarNeckGeometry?,
    /** 焊缝的场融合半径，px；与折射 shader 的 `weldK` 是同一个数 */
    val weldK: Float,
)

internal fun FusionPairParams.frame(size: Size, density: Density): FusionPairFrame? {
    // 可见矩形 = 画布挖掉一圈 bleed；按压放大 / 跟手位移都在里面发生，超出部分由 bleed 兜住
    val bx = bleedX
    val by = bleedY
    val h0 = size.height - by * 2f
    val visibleWidth = size.width - bx * 2f
    val cw0 = (visibleWidth - gapPx) / 2f
    if (h0 <= 1f || cw0 <= 1f) return null
    val r0 = h0 / 2f

    val ls = leftScale()
    val lt = leftTranslation()
    val rs = rightScale()
    val rt = rightTranslation()

    // 左胶囊：静止矩形 [0, cw0] 按 (sx, sy) 缩放后整体跟着位移走
    val lw = (cw0 * ls.x).coerceAtLeast(1f)
    val lh = (h0 * ls.y).coerceAtLeast(1f)
    val lcx = bx + cw0 / 2f + lt.x
    val lcy = by + r0 + lt.y
    val lrx = (r0 * ls.x).coerceAtLeast(0.5f)
    val lry = (r0 * ls.y).coerceAtLeast(0.5f)

    // 右胶囊同理，静止矩形 [cw0 + gap, visibleWidth]
    val rw = (cw0 * rs.x).coerceAtLeast(1f)
    val rh = (h0 * rs.y).coerceAtLeast(1f)
    val rcx = bx + visibleWidth - cw0 / 2f + rt.x
    val rcy = by + r0 + rt.y
    val rrx = (r0 * rs.x).coerceAtLeast(0.5f)
    val rry = (r0 * rs.y).coerceAtLeast(0.5f)

    // 颈 / 山丘：左胶囊的右端头 ↔ 右胶囊的左端头（端头中心就是胶囊端点圆/椭圆的圆心）
    val leftCapX = lcx + lw / 2f - lrx
    val rightCapX = rcx - rw / 2f + rrx

    val neck = barNeckGeometryPx(
        pillCenterX = lcx,
        pillCenterY = lcy,
        pillHalfWidth = lw / 2f,
        pillHalfHeight = lh / 2f,
        circleCenterX = rightCapX,
        circleCenterY = rcy,
        circleRadiusX = rrx,
        circleRadiusY = rry,
        maxNeckHalfPx = h0 * fusion.neckStrength,
        fusion = fusion,
        density = density,
        // 形变后的端头是椭圆（rx ≠ ry）：按正圆算会把轴端点摆错，颈的锚点落进本体内部，
        // 接缝处就是一道折痕。两轴半径照实给。
        pillRadiusX = lrx,
        pillRadiusY = lry,
    )

    return FusionPairFrame(
        leftCx = lcx, leftCy = lcy, leftHalfW = lw / 2f, leftHalfH = lh / 2f,
        leftRx = lrx, leftRy = lry,
        rightCx = rcx, rightCy = rcy, rightHalfW = rw / 2f, rightHalfH = rh / 2f,
        rightRx = rrx, rightRy = rry,
        baseW = cw0, baseH = h0,
        leftSx = ls.x, leftSy = ls.y, rightSx = rs.x, rightSy = rs.y,
        neck = neck,
        weldK = with(density) { fusion.weldRadiusDp.dp.toPx() },
    )
}

internal class FusionPairShape(private val p: FusionPairParams) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val outline = buildOutline(size, density)
        (outline as? Outline.Generic)?.path?.let(p.outlineSink)
        return outline
    }

    private fun buildOutline(size: Size, density: Density): Outline {
        val f = p.frame(size, density) ?: return Outline.Generic(Path())

        val left = capsulePath(
            left = f.leftCx - f.leftHalfW, top = f.leftCy - f.leftHalfH,
            baseWidth = f.baseW, baseHeight = f.baseH,
            sx = f.leftSx, sy = f.leftSy,
        )
        val right = capsulePath(
            left = f.rightCx - f.rightHalfW, top = f.rightCy - f.rightHalfH,
            baseWidth = f.baseW, baseHeight = f.baseH,
            sx = f.rightSx, sy = f.rightSy,
        )

        val merged = Path().apply { op(left, right, PathOperation.Union) }

        val geom = f.neck ?: return Outline.Generic(merged)

        val bulge = run {
            val a = fusionHillPath(
                centerX = f.leftCx + f.leftHalfW - f.leftRx, centerY = f.leftCy,
                rx = f.leftRx, ry = f.leftRy,
                angleCenter = geom.bulgeAngle, phi = geom.phiPill,
                bulgeH = geom.bulgeH, sigma = p.fusion.bulgeSigma, gaussEdge = geom.gaussEdge,
            )
            val b = fusionHillPath(
                centerX = f.rightCx - f.rightHalfW + f.rightRx, centerY = f.rightCy,
                rx = f.rightRx, ry = f.rightRy,
                angleCenter = geom.bulgeAngle + PI_F, phi = geom.phiCircle,
                bulgeH = geom.bulgeH, sigma = p.fusion.bulgeSigma, gaussEdge = geom.gaussEdge,
            )
            if (a == null || b == null) null else Path().apply { op(a, b, PathOperation.Union) }
        }

        // 本体 ∪ 山丘：山丘在接触之前就有了，是「鼓起来」的那一段
        val body = unionWith(merged, bulge) ?: merged

        // 并体：两团的距离场用 smooth-min 焊在一起，轮廓取同一个场的零等值面。
        // 场与折射 shader 的 sceneSdf 是同一份公式 —— 形状和折射天然就是同一条边，
        // 也不再有「平底 + 两端曲率突变」那种折痕。
        val weld = weldPatchPath(f, geom)
        if (weld == null) return Outline.Generic(body)

        val out = Path()
        if (!out.op(body, weld, PathOperation.Union)) return Outline.Generic(body)
        return Outline.Generic(out)
    }

    private fun unionWith(base: Path, extra: Path?): Path? {
        if (extra == null) return null
        val out = Path()
        return if (out.op(base, extra, PathOperation.Union)) out else null
    }

    /**
     * 连续圆角胶囊（Continuity，与卡片 / 玻璃按钮同一套曲率，不是纯圆弧）。
     *
     * 端头要跟着 (sx, sy) 非等比缩放，而 Continuity 只吃等比尺寸 —— 所以先生成静止尺寸的
     * 路径再整体缩放。缩放在前、平移在后（Compose 的 Matrix 操作是后乘，写 translate 再 scale
     * 得到的才是 T·S）。
     */
    private fun capsulePath(
        left: Float,
        top: Float,
        baseWidth: Float,
        baseHeight: Float,
        sx: Float,
        sy: Float,
    ): Path = Path().apply {
        val base = (Continuity.Default.createCapsuleOutline(Size(baseWidth, baseHeight))
            as? Outline.Generic)?.path
        if (base != null) {
            addPath(base)
        } else {
            addRoundRect(
                RoundRect(0f, 0f, baseWidth, baseHeight, baseHeight / 2f, baseHeight / 2f)
            )
        }
        transform(
            Matrix().apply {
                translate(left, top)
                scale(sx, sy)
            }
        )
    }

    /**
     * 焊缝补丁：两团的距离场取**平滑最小值**（smooth-min），再取这个场的零等值面。
     *
     * 只在中缝两侧各 [WELD_SPAN_RATIO]×weldK 的 x 范围内采样：越往两端，smin 与 min 的差按指数
     * 衰减，那里早已回到本体表面；再叠一段平滑内收（[WELD_END_INSET_RATIO]×weldK），保证竖直闭合边
     * 整个埋在本体里，并集之后完全看不见。
     *
     * 为什么不是本体路径：本体是 Continuity（连续曲率）胶囊，shader 用的 SDF 胶囊只是它的近似，
     * 直接拿 SDF 的等值面当外轮廓会与本体差出亚像素级的台阶 —— 描边一放大就是一条缝。
     */
    private fun weldPatchPath(f: FusionPairFrame, geom: BarNeckGeometry): Path? {
        val weldK = f.weldK
        if (weldK <= 0.05f) return null
        val sigma = p.fusion.bulgeSigma
        val gaussEdge = geom.gaussEdge
        val leftCapX = f.leftCx + f.leftHalfW - f.leftRx
        val rightCapX = f.rightCx - f.rightHalfW + f.rightRx
        val midX = (leftCapX + rightCapX) * 0.5f
        val midY = (f.leftCy + f.rightCy) * 0.5f

        val sd: (Float, Float) -> Float = { x, y ->
            val dl = capsuleSdf(
                px = x, py = y,
                centerX = f.leftCx, centerY = f.leftCy,
                halfW = f.leftHalfW,
                rx = f.leftRx, ry = f.leftRy,
                angleC = geom.bulgeAngle, phi = geom.phiPill,
                bulgeH = geom.bulgeH, sigma = sigma, gaussEdge = gaussEdge,
            )
            val dr = capsuleSdf(
                px = x, py = y,
                centerX = f.rightCx, centerY = f.rightCy,
                halfW = f.rightHalfW,
                rx = f.rightRx, ry = f.rightRy,
                angleC = geom.bulgeAngle + PI_F, phi = geom.phiCircle,
                bulgeH = geom.bulgeH, sigma = sigma, gaussEdge = gaussEdge,
            )
            smoothMin(dl, dr, weldK)
        }

        // 中缝还连不起来：两团各鼓各的山丘，没有焊缝可补
        if (sd(midX, midY) >= 0f) return null

        val halfSpan = WELD_SPAN_RATIO * weldK
        val segments = ceil(halfSpan / max(weldK / 3f, 1f)).toInt().coerceIn(4, 40)
        val dx = (halfSpan * 2f) / segments
        val limit = max(f.leftHalfH, f.rightHalfH) + weldK * 2f
        val marchStep = max(limit / 24f, 1f)
        val insetPx = WELD_END_INSET_RATIO * weldK

        val top = FloatArray(segments + 1)
        val bottom = FloatArray(segments + 1)
        // 相邻采样点热启动：边界随 x 连续移动，从上一个结果出发只需走一两步
        var yTop = midY
        var yBottom = midY
        for (i in 0..segments) {
            val x = midX - halfSpan + dx * i
            val t = traceBoundary(sd, x, yTop, up = true, limit = limit, step = marchStep)
                ?: return null
            val b = traceBoundary(sd, x, yBottom, up = false, limit = limit, step = marchStep)
                ?: return null
            top[i] = t
            bottom[i] = b
            yTop = t
            yBottom = b
        }

        // 两端平滑内收进本体（smoothstep：两端一阶导为 0，不会在收口处留折角）
        for (i in 0..segments) {
            val u = abs(i - segments * 0.5f) / (segments * 0.5f)
            val w = ((u - WELD_TAPER_START) / (1f - WELD_TAPER_START)).coerceIn(0f, 1f)
            val inset = insetPx * w * w * (3f - 2f * w)
            top[i] += inset
            bottom[i] -= inset
        }

        val upper = ArrayList<Offset>(segments + 1)
        val lower = ArrayList<Offset>(segments + 1)
        for (i in 0..segments) {
            val x = midX - halfSpan + dx * i
            upper.add(Offset(x, top[i]))
            lower.add(Offset(x, bottom[i]))
        }

        return Path().apply {
            appendCatmullRom(upper, move = true)
            // 上边界末端 → 下边界末端是一段竖直闭合边，整段落在本体内部
            lineTo(lower.last().x, lower.last().y)
            appendCatmullRom(lower.asReversed(), move = false)
            close()
        }
    }

    private companion object {
        const val PI_F = 3.1415927f

        /** 焊缝采样范围（× weldK）：两端处 smin 与 min 的差已衰减到亚像素 */
        const val WELD_SPAN_RATIO = 3.4f

        /** 焊缝两端平滑内收的起点（0 = 中心，1 = 端点）与幅度（× weldK） */
        const val WELD_TAPER_START = 0.55f
        const val WELD_END_INSET_RATIO = 0.4f
    }
}

/**
 * 沿一串点铺 Catmull-Rom 曲线（转成三次贝塞尔）。直接用 `lineTo` 会在焊缝的鞍部留下
 * 肉眼可见的折线棱角 —— 采样步长只有几 px，而那里的曲率半径也只有焊缝量级。
 */
private fun Path.appendCatmullRom(points: List<Offset>, move: Boolean) {
    if (points.isEmpty()) return
    if (move) moveTo(points[0].x, points[0].y)
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
}

/**
 * 从 [ySeed] 出发找场 [sd] 的零等值面：`up = true` 找上边界（向外是 -y）。
 *
 * 种子大概率已经贴着上一条采样点的边界，所以先判断种子在内 / 在外，再朝相应方向走，
 * 找到符号变化后二分。这样每个 x 只需 2~3 次场求值 + 7 次二分。
 */
private fun traceBoundary(
    sd: (Float, Float) -> Float,
    x: Float,
    ySeed: Float,
    up: Boolean,
    limit: Float,
    step: Float,
): Float? {
    val outward = if (up) -1f else 1f
    var yIn = ySeed
    if (sd(x, yIn) >= 0f) {
        // 种子已经出界（边界比上一条采样高 / 低）：先向里挪回内部
        var moved = 0f
        while (moved < limit) {
            yIn -= outward * step
            moved += step
            if (sd(x, yIn) < 0f) break
        }
        if (moved >= limit) return null
    }
    var yOut = yIn
    var moved = 0f
    while (moved < limit) {
        yOut += outward * step
        moved += step
        if (sd(x, yOut) >= 0f) break
    }
    if (moved >= limit) return null
    repeat(7) {
        val mid = (yIn + yOut) * 0.5f
        if (sd(x, mid) < 0f) yIn = mid else yOut = mid
    }
    return (yIn + yOut) * 0.5f
}

/** 椭圆的真实有符号距离；4 次 Newton 迭代，与 shader 的 `sdEllipse` 逐字一致。 */
private fun ellipseSdf(px: Float, py: Float, rx: Float, ry: Float): Float {
    // 正圆是绝大多数状态（等比缩放）：直接闭式解，省掉 4 次三角函数 + Newton
    if (abs(rx - ry) <= 1e-3f) return hypot(px, py) - rx
    val ax = max(rx, 1e-4f)
    val ay = max(ry, 1e-4f)
    var t = atan2(py / ay, px / ax)
    repeat(4) {
        val ct = cos(t)
        val st = sin(t)
        val qx = ax * ct
        val qy = ay * st
        val dqx = -ax * st
        val dqy = ay * ct
        val f = (qx - px) * dqx + (qy - py) * dqy
        val df = dqx * dqx + dqy * dqy - (qx - px) * ax * ct - (qy - py) * ay * st
        t -= f / (if (abs(df) > 1e-4f) df else 1e-4f)
    }
    val qx = ax * cos(t)
    val qy = ay * sin(t)
    val dist = hypot(px - qx, py - qy)
    val nx = px / ax
    val ny = py / ay
    return if (nx * nx + ny * ny < 1f) -dist else dist
}

/**
 * 胶囊距离场：轴线在 x 上、端头是椭圆，山丘（bulge）直接减进距离里。
 * 与 shader 的 `sdCapsule` 逐字一致 —— 任何一处不同，轮廓和折射就会错开。
 */
private fun capsuleSdf(
    px: Float,
    py: Float,
    centerX: Float,
    centerY: Float,
    halfW: Float,
    rx: Float,
    ry: Float,
    angleC: Float,
    phi: Float,
    bulgeH: Float,
    sigma: Float,
    gaussEdge: Float,
): Float {
    val qx = px - centerX
    val qy = py - centerY
    val axisHalf = max(halfW - rx, 0f)
    val dx = qx - qx.coerceIn(-axisHalf, axisHalf)
    val dy = qy
    var d = ellipseSdf(dx, dy, rx, ry)
    if (bulgeH > 0.01f && hypot(dx, dy) > 1e-4f) {
        d -= bulgeH * fusionBumpAt(atan2(dy, dx), angleC, phi, sigma, gaussEdge)
    }
    return d
}

/**
 * 指数平滑最小值：`-k·ln(e^{-a/k} + e^{-b/k})`。
 *
 * 比 `min(a, b)` 小（更早进入内部），于是两团靠近时轮廓会先「焊」出一小段腰，而且是**连续**地
 * 从两团并到一起 —— 不像贝塞尔颈那样有一个「开始拉丝」的换挡点，也不会在谷底留下平底。
 * 先减去 `min` 再取指数，参数里至少有一项是 1，数值上不会下溢。
 */
private fun smoothMin(a: Float, b: Float, k: Float): Float {
    if (k <= 1e-4f) return min(a, b)
    val m = min(a, b)
    return m - k * ln(exp(-(a - m) / k) + exp(-(b - m) / k))
}

/**
 * 山丘扇形的采样轮廓；[bulgeH] 太小直接返回 null（省一次 union）。
 *
 * 两端各留一段 [HILL_END_TAPER] 的收口，把半径往**本体内侧**压 [HILL_END_INSET_RATIO]×半径：
 * 本体的胶囊是 Continuity 曲线，这里的基准半径是椭圆 SDF，两者在端头附近差 1~2px。
 * 端点若正好压在椭圆上，并集之后就会在本体外侧剩一个 1~2px 的小尖刺 —— 被内侧描边一放大
 * 就是山丘根部的一道接缝。压进本体之后这一小段整段不可见，山丘是「从本体里长出来」的。
 */
private fun fusionHillPath(
    centerX: Float,
    centerY: Float,
    rx: Float,
    ry: Float,
    angleCenter: Float,
    phi: Float,
    bulgeH: Float,
    sigma: Float,
    gaussEdge: Float,
): Path? {
    if (bulgeH < 0.3f) return null
    return Path().apply {
        val steps = 56
        val endInset = HILL_END_INSET_RATIO * min(rx, ry)
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val a = (angleCenter - phi) + 2f * phi * t
            val edge = (min(t, 1f - t) / HILL_END_TAPER).coerceAtMost(1f)
            val dip = 1f - edge * edge * (3f - 2f * edge)
            val radius = ellipseRadiusAt(rx, ry, a) +
                bulgeH * fusionBumpAt(a, angleCenter, phi, sigma, gaussEdge) -
                endInset * dip
            val x = centerX + radius * cos(a)
            val y = centerY + radius * sin(a)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/** 山丘根部收口：占弧长的比例与收进本体的深度（× 端头半径） */
private const val HILL_END_TAPER = 0.18f
private const val HILL_END_INSET_RATIO = 0.035f

/**
 * 这两颗按钮的折射 shader。
 *
 * `lens()` 对自定义 Shape 直接跳过（`cornerRadii` 只认圆角矩形），所以必须自己给一份：
 * 用解析 SDF 描述**整条融合轮廓** —— 左胶囊、右胶囊的距离场取指数平滑最小值（smooth-min），
 * 于是边缘一圈折射沿「两颗按钮 + 中间的腰」连续走，接缝处不会断成两截。
 *
 * 形状那边用的是同一个 `smoothMin` + 同一个 `sdCapsule`：轮廓和折射读同一条边，
 * 不会再出现「形状焊上了、折射还在按 min 算」的错位缝。
 *
 * 端头支持椭圆（rx / ry 分开给）：拖拽时胶囊被非等比拉伸，按正圆算折射会与真实边缘错开。
 */
internal const val FusionPairRefractionShader = """
uniform shader content;

uniform float2 offset;
uniform float2 lc;
uniform float2 lh;
uniform float2 lr;
uniform float2 rc;
uniform float2 rh;
uniform float2 rr;
// 并体焊缝的场融合半径（px）；与 FusionPairShape 的 weldK 同一个数
uniform float weldK;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float bulgeH;
uniform float bulgeAngleL;
uniform float bulgeAngleR;
uniform float bulgePhiL;
uniform float bulgePhiR;
uniform float bulgeGaussEdge;
uniform float bulgeSigma;

float bumpAt(float angle, float center, float phi) {
    float da = angle - center;
    da = da - 6.2831853 * floor(da / 6.2831853 + 0.5);
    float t = (da + phi) / (2.0 * phi);
    if (t <= 0.0 || t >= 1.0) {
        return 0.0;
    }
    float dx = t - 0.5;
    float rawG = exp(-(dx * dx) / (2.0 * bulgeSigma * bulgeSigma));
    float bell = clamp((rawG - bulgeGaussEdge) / (1.0 - bulgeGaussEdge), 0.0, 1.0);
    // 端点乘窗：值归零且（乘上值为 0 的 bell 后）一阶导也归零，山丘根部不留折角
    float u = 2.0 * t - 1.0;
    return bell * (1.0 - u * u * u * u);
}

float sdEllipse(float2 p, float2 c, float2 ab) {
    float2 d = p - c;
    // 正圆走闭式解，与形状那边同一个分支
    if (abs(ab.x - ab.y) <= 0.001) {
        return length(d) - ab.x;
    }
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

// 胶囊 SDF：轴线在 x 上，端头是椭圆（rx == ry 时就是正圆）
float sdCapsule(float2 p, float2 c, float2 halfSize, float2 radii, float angleC, float phi) {
    float2 q = p - c;
    float2 axisHalf = max(halfSize - radii, float2(0.0, 0.0));
    float2 nearest = float2(clamp(q.x, -axisHalf.x, axisHalf.x), 0.0);
    float2 d = q - nearest;
    float d0 = sdEllipse(d, float2(0.0, 0.0), radii);
    if (bulgeH > 0.01 && length(d) > 1e-4) {
        d0 = d0 - bulgeH * bumpAt(atan(d.y, d.x), angleC, phi);
    }
    return d0;
}

// 指数平滑最小值：形状那边用同一个公式取等值面，所以轮廓与折射永远是同一条边。
float smoothMin(float a, float b, float k) {
    if (k <= 0.0001) {
        return min(a, b);
    }
    float m = min(a, b);
    return m - k * log(exp(-(a - m) / k) + exp(-(b - m) / k));
}

float sceneSdf(float2 p) {
    float dL = sdCapsule(p, lc, lh, lr, bulgeAngleL, bulgePhiL);
    float dR = sdCapsule(p, rc, rh, rr, bulgeAngleR, bulgePhiR);
    return smoothMin(dL, dR, weldK);
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 p = coord + offset;
    float sd = sceneSdf(p);
    float refrH = refractionHeight;
    if (-sd >= refrH) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refrH) * refractionAmount;

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
