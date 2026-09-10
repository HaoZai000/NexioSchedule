package com.haooz.chedule.ui.basic

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val MenuEnterEasing = CubicBezierEasing(0.3f, 1.25f, 0.32f, 1f)
private val MenuExitEasing = CubicBezierEasing(0.3f, 1f, 0.3f, 1f)
private val ShadowPadding = 12.dp

data class ShortcutMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val iconSize: Dp = 26.dp
)

@Composable
fun ShortcutMenu(
    show: Boolean,
    items: List<ShortcutMenuItem>,
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    anchorRightPx: Float = Float.MAX_VALUE,
    onDismiss: () -> Unit = {},
    onMeasuredSize: (width: Int, height: Int) -> Unit = { _, _ -> }
) {
    val isLightTheme = !isAppDarkTheme()
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val containerWidthPx = windowInfo.containerSize.width

    val containerColor =
        if (isLightTheme) Color(0xFFFFFFFF).copy(0.6f)
        else Color(0xFF121212).copy(0.54f)

    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    // 默认向右展开；可见右边缘超出屏幕安全边距时向左平移，右边缘对齐卡片右边缘
    // 菜单 layout 宽含左右 ShadowPadding，可见右边缘 = menuPositionX + menuWidth - ShadowPadding
    var menuPositionX by remember { mutableStateOf(0f) }
    var menuWidth by remember { mutableStateOf(0) }
    val safetyPaddingPx = with(density) { 4.dp.toPx() }
    val shadowPadPx = with(density) { ShadowPadding.toPx() }
    val screenRightEdge = containerWidthPx - safetyPaddingPx
    val shouldShiftLeft = remember(menuPositionX, menuWidth, screenRightEdge) {
        val visibleRightEdge = menuPositionX + menuWidth - shadowPadPx
        visibleRightEdge > screenRightEdge
    }
    // 左移目标：卡片右边缘（不超出屏幕安全边距）
    val shiftTarget = minOf(anchorRightPx, screenRightEdge)
    val shiftLeftPx = if (shouldShiftLeft) {
        (menuPositionX + menuWidth - shadowPadPx - shiftTarget).coerceAtLeast(0f)
    } else 0f

    LaunchedEffect(show) {
        if (show) {
            launch {
                scale.animateTo(1f, tween(420, easing = MenuEnterEasing))
            }
            launch {
                alpha.animateTo(1f, tween(240))
            }
        } else {
            launch {
                scale.animateTo(0.24f, tween(240, easing = MenuExitEasing))
            }
            launch {
                alpha.animateTo(0f, tween(120))
            }
        }
    }

    if (alpha.value <= 0f && !show) return

    val itemSize = 36.dp
    val horizontalPadding = 16.dp
    val verticalPadding = 8.dp
    val spacing = 16.dp

    Box(
        modifier = modifier
            .wrapContentSize()
            .onGloballyPositioned { coordinates ->
                onMeasuredSize(coordinates.size.width, coordinates.size.height)
                menuWidth = coordinates.size.width
                menuPositionX = coordinates.positionInWindow().x
            }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                // 需要左移时 pivot 改为右下角(向左展开)，整体向左平移对齐目标右边缘
                transformOrigin = TransformOrigin(
                    if (shouldShiftLeft) 1f else 0f,
                    1f
                )
                translationX = if (shouldShiftLeft) -shiftLeftPx else 0f
                clip = false
            }
            .drawBehind {
                val blurRadius = 16f * density.density
                val cornerRadiusPx = 20f * density.density
                val paint = Paint().apply {
                    color = "#0A000000".toColorInt()
                    maskFilter = BlurMaskFilter(
                        blurRadius,
                        BlurMaskFilter.Blur.NORMAL
                    )
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRoundRect(
                        ShadowPadding.toPx(), ShadowPadding.toPx(),
                        size.width - ShadowPadding.toPx(), size.height - ShadowPadding.toPx(),
                        cornerRadiusPx, cornerRadiusPx,
                        paint
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .padding(ShadowPadding)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousRoundedRectangle(18.dp) },
                    effects = {
                        vibrancy()
                        blur(4f.dp.toPx())
                        lens(12f.dp.toPx(), 12f.dp.toPx())
                    },
                    highlight = null,
                    shadow = null,
                    onDrawSurface = {
                        drawRect(containerColor)
                    }
                )
                .edgeLight(shape = ContinuousRoundedRectangle(18.dp), edgeLight = rememberDefaultEdgeLight())
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    Box(
                        modifier = Modifier
                            .size(itemSize)
                            .clip(CircleShape)
                            .clickable {
                                onDismiss()
                                item.onClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(item.iconSize)
                        )
                    }
                }
            }
        }
    }
}
