package com.haooz.chedule.ui.basic

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.rememberDynamicCornerRadiusShape

private val ShadowPadding = 24.dp

/**
 * 课程表右上角"更多"按钮的下拉菜单。
 *
 * 动画体系与 [top.yukonga.miuix.kmp.basic.ListPopupContent] 完全一致：
 * - 缩放：0.24f → 1.0f（fraction 驱动，spring 动画）
 * - 裁剪揭示：朝下方向性展开（从顶部向下）
 * - 阴影渐变：进入 fraction 升到 0.78f 时 200ms 渐入，退出降到 0.99f 时 50ms 渐出；
 *   若进入动画未播完就被打断关闭，阴影立即消失
 * - 模糊：进入时 8dp → 0，退出时 0 → 8dp
 * - 圆角随缩放反向放大，保持视觉圆角不变
 * - transformOrigin 从锚点(1f, 0f)动态移动到中心(0.5f, 0.5f)
 */
@Composable
fun LiquidGlassDropdownMenu(
    show: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    fraction: Animatable<Float,*> = remember { Animatable(0f) },
    content: @Composable ColumnScope.() -> Unit
) {
    val isLightTheme = !isAppDarkTheme()
    val containerColor = if (isLightTheme) Color(0xFFFFFFFF).copy(0.72f)
        else Color(0xFF242424).copy(0.8f)

    val menuAlpha = remember { Animatable(0f) }

    // 内容透明度：进入时从 0.4f 渐显到 1.0f，退出时从 fraction=0.5f 开始消失
    var contentAlpha by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var prevFraction = 0f
        snapshotFlow { fraction.value }
            .collect { current ->
                val isEntering = current >= prevFraction
                prevFraction = current
                contentAlpha = if (isEntering) {
                    0.2f + 0.8f * current
                } else {
                    if (current > 0.5f) 1f else current * 2f
                }
            }
    }

    // 阴影渐变动画：进入时升到 0.78 显示，退出时降到 0.99 消失
    val shadowAlphaState = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        var prevFraction = 0f
        var shadowVisible = false
        var animJob: Job? = null
        snapshotFlow { fraction.value }
            .collect { current ->
                val isEntering = current >= prevFraction
                prevFraction = current
                val newVisible = if (isEntering) current >= 0.78f else current >= 0.99f
                if (newVisible != shadowVisible) {
                    shadowVisible = newVisible
                    animJob?.cancel()
                    animJob = launch {
                        if (newVisible) {
                            shadowAlphaState.animateTo(1f, tween(200))
                        } else {
                            if (shadowAlphaState.value >= 1f) {
                                shadowAlphaState.animateTo(0f, tween(50))
                            } else {
                                shadowAlphaState.snapTo(0f)
                            }
                        }
                    }
                }
            }
    }

    val cornerRadius = 25.dp

    // 裁剪 Shape：fraction=0 时裁为小正方形（对齐右上角），fraction=1 时完整尺寸。
    // 正方形 + 反向放大的圆角（24dp / 0.24f = 100dp > 半边长）→ 视觉圆形。
    val clipShape = remember {
        DropdownClipShape(
            fractionProgress = { fraction.value },
            cornerRadius = cornerRadius,
            buttonDiameter = 42.dp,
        )
    }


    LaunchedEffect(show) {
        if (show) {
            launch {
                fraction.animateTo(
                    1f,
                    spring(dampingRatio = 0.78f, stiffness = 232f, visibilityThreshold = 0.0001f)
                )
            }
            launch {
                menuAlpha.animateTo(1f, tween(120))
            }
        } else {
            // fraction 退出动画与 alpha 退出动画并行
            launch {
                fraction.animateTo(
                    0f,
                    spring(dampingRatio = 0.78f, stiffness = 400f, visibilityThreshold = 0.0001f)
                )
            }
            menuAlpha.animateTo(0f, tween(380))
            fraction.snapTo(0f)
            menuAlpha.snapTo(0f)
        }
    }

    if (menuAlpha.value <= 0f && !show) return

    // 外层 Box：padding 给阴影留空间，阴影不被缩放（与 ListPopupContent 结构一致）
    // offset 随 fraction 渐变：fraction=0 时偏移到按钮中心，fraction=1 时归零
    Box(
        modifier = modifier
            .width(200.dp + ShadowPadding * 2)
            .wrapContentHeight()
            .padding(ShadowPadding)
            .drawBehind {
                val shadowAlpha = shadowAlphaState.value
                if (shadowAlpha <= 0f) return@drawBehind
                val baseAlpha = (32 * shadowAlpha).toInt().coerceIn(0, 255)
                val shadowColor = android.graphics.Color.argb(baseAlpha, 0, 0, 0)
                val blurRadius = 16f * density
                val cornerRadiusPx = cornerRadius.toPx()
                val nativePath = android.graphics.Path().apply {
                    addRoundRect(
                        0f, 0f, size.width, size.height,
                        cornerRadiusPx, cornerRadiusPx,
                        android.graphics.Path.Direction.CW
                    )
                }
                val paint = Paint().apply {
                    color = shadowColor
                    maskFilter = BlurMaskFilter(
                        blurRadius.coerceAtLeast(0.1f),
                        BlurMaskFilter.Blur.NORMAL
                    )
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawPath(nativePath, paint)
                }
            }
    ) {
        // 内层 Box：graphicsLayer 缩放 + 裁剪 + 模糊 + 背景 + 边光
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val f = fraction.value
                    val scale = 0.24f + 0.76f * f
                    scaleX = scale
                    scaleY = scale
                    this.alpha = menuAlpha.value
                    // 从锚点(1f, 0f)动态移动到中心(0.5f, 0.5f)
                    val startOrigin = TransformOrigin(1f, 0f)
                    val targetOrigin = TransformOrigin(0.5f, 0.5f)
                    transformOrigin = TransformOrigin(
                        pivotFractionX = startOrigin.pivotFractionX + (targetOrigin.pivotFractionX - startOrigin.pivotFractionX) * f,
                        pivotFractionY = startOrigin.pivotFractionY + (targetOrigin.pivotFractionY - startOrigin.pivotFractionY) * f
                    )
                    clip = false
                }
                .clip(clipShape)
                .blur(radius = (8f * (1f - fraction.value)).dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = {
                        val f = fraction.value.coerceIn(0f, 1f)
                        val avgScale = 0.24f + 0.76f * f
                        val scaledCornerRadius = cornerRadius / avgScale
                        RoundedRectangle(scaledCornerRadius)
                    },
                    effects = {
                        vibrancy()
                        blur(24.dp.toPx())
                    },
                    highlight = null,
                    shadow = null,
                    onDrawSurface = {
                        drawRect(containerColor)
                    }
                )
                .edgeLight(
                    shape = rememberDynamicCornerRadiusShape(
                        fractionProgress = { fraction.value },
                        cornerRadius = cornerRadius,
                    ),
                    edgeLight = rememberDefaultEdgeLight()
                )
        ) {
            Column(modifier = Modifier
                .padding(vertical = 8.dp)
                .graphicsLayer { alpha = contentAlpha }
            ) {
                content()
            }
        }
    }
}

@Composable
fun LiquidGlassDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isAppDarkTheme()
    val textColor = if (isLightTheme) Color(0xFF1A1A1A) else Color(0xFFE8E4DE)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedRectangle(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

/**
 * 下拉菜单裁剪 Shape（参考 CourseDetailScreen.AnimClipShape）。
 *
 * fraction=0 时裁为小正方形（对齐右上角），正方形 + 反向放大的圆角 → 视觉圆形。
 * fraction=1 时为完整尺寸圆角矩形。
 * 尺寸和圆角都除以 scale 来补偿 graphicsLayer 缩放（clip 在未缩放坐标空间中应用）。
 */
private class DropdownClipShape(
    private val fractionProgress: () -> Float,
    private val cornerRadius: Dp,
    private val buttonDiameter: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val f = fractionProgress().coerceIn(0f, 1f)
        val scale = 0.24f + 0.76f * f

        val buttonPx = with(density) { buttonDiameter.toPx() }
        // 目标视觉尺寸：fraction=0 时为 buttonDiameter 正方形，fraction=1 时为完整尺寸
        val targetVisualWidth = buttonPx + (size.width - buttonPx) * f
        val targetVisualHeight = buttonPx + (size.height - buttonPx) * f

        // clip 坐标系（未缩放）中的尺寸，需除以 scale 补偿
        val clipWidth = (targetVisualWidth / scale).coerceAtMost(size.width)
        val clipHeight = (targetVisualHeight / scale).coerceAtMost(size.height)

        // 对齐右上角（与 transformOrigin=(1f,0f) 一致，缩放后该点固定）
        val left = size.width - clipWidth
        val top = 0f
        val right = size.width
        val bottom = clipHeight

        // 圆角反向放大（与 rememberDynamicCornerRadiusShape 一致）
        val cornerRadiusPx = with(density) { (cornerRadius / scale).toPx() }

        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                )
            )
        }
        return Outline.Generic(path)
    }
}
