package com.haooz.softglass.shape

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp

/** 圆角矩形填充，不裁剪子节点。 */
@Composable
fun Modifier.continuousBackground(
    color: Color,
    cornerRadius: Dp,
    continuity: Continuity = Continuity.Default,
): Modifier = background(color, ContinuousRoundedRectangle(cornerRadius, continuity = continuity))

/** 四角独立的圆角矩形填充。 */
@Composable
fun Modifier.continuousBackground(
    color: Color,
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp,
    continuity: Continuity = Continuity.Default,
): Modifier = background(
    color,
    ContinuousRoundedRectangle(topStart, topEnd, bottomEnd, bottomStart, continuity),
)

/** 按圆角矩形裁剪子节点。 */
@Composable
fun Modifier.continuousClip(
    cornerRadius: Dp,
    continuity: Continuity = Continuity.Default,
): Modifier = clip(ContinuousRoundedRectangle(cornerRadius, continuity = continuity))

/** 四角独立裁剪。 */
@Composable
fun Modifier.continuousClip(
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp,
    continuity: Continuity = Continuity.Default,
): Modifier = clip(ContinuousRoundedRectangle(topStart, topEnd, bottomEnd, bottomStart, continuity))

/** 裁剪 + 填充。 */
@Composable
fun Modifier.continuousSurface(
    color: Color,
    cornerRadius: Dp,
    continuity: Continuity = Continuity.Default,
): Modifier = continuousSurface(color, cornerRadius, cornerRadius, cornerRadius, cornerRadius, continuity)

/** 四角独立的裁剪 + 填充。 */
@Composable
fun Modifier.continuousSurface(
    color: Color,
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp,
    continuity: Continuity = Continuity.Default,
): Modifier {
    val shape = ContinuousRoundedRectangle(topStart, topEnd, bottomEnd, bottomStart, continuity)
    return clip(shape).background(color, shape)
}

/** 沿形状描边，内缩半个线宽。 */
@Composable
fun Modifier.continuousBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
    continuity: Continuity = Continuity.Default,
): Modifier = continuousBorder({ width }, { color }, cornerRadius, continuity)

/** 线宽与颜色在绘制期采样，动画不触发重组。 */
@Composable
fun Modifier.continuousBorder(
    width: () -> Dp,
    color: () -> Color,
    cornerRadius: Dp,
    continuity: Continuity = Continuity.Default,
): Modifier = drawWithCache {
    val radiusPx = cornerRadius.toPx()
    onDrawBehind {
        val widthPx = width().toPx()
        if (widthPx <= 0f) return@onDrawBehind
        val innerW = size.width - widthPx
        val innerH = size.height - widthPx
        if (innerW <= 0f || innerH <= 0f) return@onDrawBehind
        val innerRadius = (radiusPx - widthPx / 2f).coerceAtLeast(0f)
        val path = continuity.createRoundedRectangleOutline(
            Size(innerW, innerH), innerRadius, innerRadius, innerRadius, innerRadius,
        ).toPath()
        translate(widthPx / 2f, widthPx / 2f) {
            drawPath(path = path, color = color(), style = Stroke(widthPx))
        }
    }
}

/** 往当前 Path 追加一个圆角矩形。 */
fun Path.addContinuousRoundRect(
    width: Float,
    height: Float,
    cornerRadius: Float,
    continuity: Continuity = Continuity.Default,
): Path {
    if (width <= 0f || height <= 0f) return this
    val radius = cornerRadius.coerceIn(0f, minOf(width, height) * 0.5f)
    addPath(
        continuity.createRoundedRectangleOutline(
            Size(width, height), radius, radius, radius, radius,
        ).toPath()
    )
    return this
}

private fun Outline.toPath(): Path = (this as Outline.Generic).path
