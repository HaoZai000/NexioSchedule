package com.haooz.chedule.ui.basic

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.core.graphics.toColorInt
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 顶栏液态玻璃圆形按钮。
 *
 * 拖拽时沿拖动方向拉伸、抬手回弹，机制与 BackToNowFloatingButton 一致：
 * 按压反馈（[InteractiveHighlight] 的高光 + 高光跟随）、图标随动、阴影同步变形。
 *
 * 拉伸/缩放统一放在**外层** graphicsLayer（而非 drawBackdrop 的 layerBlock），
 * 这样图标和阴影才会跟着一起变形；layerBlock 只保留 alpha。
 */
@Composable
fun LiquidTopBarButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    iconOffset: DpOffset = DpOffset.Zero,
    buttonHeight: Dp = 42.dp,
    backdropAlpha: Float = 1f,
    shadowAlpha: Float = 1f,
    iconTint: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    performHapticFeedback: Boolean = true,
    enabled: Boolean = true,
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val hapticFeedback = LocalHapticFeedback.current
    val isLightTheme = !isAppDarkTheme()
    val resolvedContainerColor = if (containerColor != Color.Unspecified) containerColor
        else if (isLightTheme) Color(0xFFFAFAFA).copy(0.76f)
        else Color(0xFF242424).copy(0.84f)

    val shadowColor = if (isLightTheme) "#12000000".toColorInt() else "#20000000".toColorInt()
    val interactionSource = remember { MutableInteractionSource() }

    // drawBackdrop 的 element 用引用比较 shape / effects / onDrawSurface
    // 若 lambda 每次都新建，节点就会每帧 update → invalidateDraw → 每帧重新录制采样层并重新跑一次
    val chromeLens = com.haooz.chedule.ui.utils.AppMaterialSettings.chromeLensEnabled()
    val buttonShapeBlock: () -> androidx.compose.ui.graphics.Shape = remember { { CircleShape } }
    val buttonEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(chromeLens) {
        {
            vibrancy()
            blur(8.dp.toPx())
            // 均衡及以下关闭折射
            if (chromeLens) lens(8f.dp.toPx(), 24f.dp.toPx())
        }
    }
    val buttonOnDrawSurface: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit =
        remember(resolvedContainerColor) {
            { drawRect(resolvedContainerColor) }
        }

    Box(
        modifier = modifier
            .wrapContentSize()
            .size(buttonHeight)
            .graphicsLayer {
                clip = false
                val width = size.width
                val height = size.height
                val progress = interactiveHighlight.pressProgress.coerceAtLeast(0f)
                val scale = lerp(1f, 1f + 4f.dp.toPx() / height, progress)
                val offset = interactiveHighlight.offset
                // 沿拖动方向的最大拉伸比例：2dp/高度（42dp 按钮约 4.8%），与 BackToNowFloatingButton 一致。
                val maxDragScale = 2f.dp.toPx() / height
                val offsetAngle = atan2(offset.y, offset.x)
                scaleX =
                    scale +
                        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                        (width / height).fastCoerceAtMost(1f)
                scaleY =
                    scale +
                        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                        (height / width).fastCoerceAtMost(1f)
                val contentMin = size.minDimension.coerceAtLeast(1f)
                // 起始跟手斜率：0.08 时位移约为手指的 8%，越拖阻尼越强（与 BackToNowFloatingButton 一致）。
                val initialDerivative = 0.08f
                translationX = contentMin * tanh(initialDerivative * offset.x / contentMin)
                translationY = contentMin * tanh(initialDerivative * offset.y / contentMin)
            }
            .drawBehind {
                val spread = shadowAlpha
                if (spread > 0.01f) {
                    val maxBlurRadius = 10f * density
                    val maxShadowSpread = 2f * density
                    val blurRadius = maxBlurRadius * spread
                    val shadowSpread = maxShadowSpread * spread
                    val outerRadius = size.minDimension / 2f + shadowSpread
                    val innerRadius = size.minDimension / 2f
                    val path = Path().apply {
                        addCircle(center.x, center.y, outerRadius, Path.Direction.CW)
                        addCircle(center.x, center.y, innerRadius, Path.Direction.CCW)
                    }
                    val paint = Paint().apply {
                        color = android.graphics.Color.argb(
                            (android.graphics.Color.alpha(shadowColor) * 3.2f).coerceAtMost(255f).toInt(),
                            android.graphics.Color.red(shadowColor),
                            android.graphics.Color.green(shadowColor),
                            android.graphics.Color.blue(shadowColor)
                        )
                        maskFilter = BlurMaskFilter(
                            blurRadius.coerceAtLeast(0.1f),
                            BlurMaskFilter.Blur.NORMAL
                        )
                    }
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawPath(path, paint)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                // 不用 clip：拉伸/位移会超出原 layout bounds（同 BackToNowFloatingButton）
                .size(buttonHeight)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = buttonShapeBlock,
                    effects = buttonEffects,
                    highlight = null,
                    shadow = null,
                    layerBlock = { alpha = backdropAlpha },
                    onDrawSurface = buttonOnDrawSurface
                )
                .edgeLight(shape = CircleShape, edgeLight = rememberDefaultEdgeLight(baseColor = resolvedContainerColor))
                .zIndex(0f)
        )
        // 按压高光单独一层，与材质层平级。
        // 不能挂在上面那个 Box 上：drawBackdrop 的 layerBlock 会把整个节点的
        // alpha 乘上 backdropAlpha，顶栏滚动时材质淡出、**按压高光也跟着消失**。
        // 高光跟随手势（gestureModifier）与 clickable 同层，两者都是 pointerInput，可叠加。
        Box(
            modifier = Modifier
                .size(buttonHeight)
                // 必须 clip：光晕半径是 minDimension * 1.5，本就溢出圆形，
                // 不裁会画成一个亮方块。放在 interactiveHighlight 之前（外层）。
                .clip(CircleShape)
                .then(interactiveHighlight.modifier)
                .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    // 按压视觉由上面的高光层提供，不要默认涟漪
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        if (performHapticFeedback) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                        onClick()
                    }
                )
                .zIndex(1f)
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(iconSize)
                .offset(iconOffset.x, iconOffset.y)
                // 高光层 zIndex=1，图标再高一级，别依赖"同zIndex 靠后绘制"
                .zIndex(2f),
            tint = if (iconTint != Color.Unspecified) iconTint else if (isLightTheme) Color.Black.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f)
        )
    }
}
