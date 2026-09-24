package com.haooz.chedule.ui.basic

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.tanh

/**
 * 底部悬浮液态玻璃按钮。效果对齐 [com.haooz.chedule.ui.components.BackToNowFloatingButton]：
 * graphicsLayer clip=false，layerBlock 可拉伸不裁切。无 ShadowPadding 外溢预留。
 * 依赖 Activity 已有的 liquidGlassBackdrop 采样层。
 */
@Composable
fun LiquidGlassTextButton(
    text: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minHeight: Dp = ButtonDefaults.MinHeight,
    primary: Boolean = true,
) {
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val isLightTheme = !isAppDarkTheme()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val interactionSource = remember { MutableInteractionSource() }

    val resolvedContainerColor = if (primary) {
        if (isLightTheme) {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.72f)
        } else {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.62f)
        }
    } else if (isLightTheme) {
        Color.White.copy(alpha = 0.76f)
    } else {
        Color(0xFF242424).copy(alpha = 0.84f)
    }
    val textColor = if (primary) {
        Color.White
    } else if (isLightTheme) {
        Color.Black.copy(alpha = 0.85f)
    } else {
        Color.White.copy(alpha = 0.85f)
    }
    val shadowColor = if (primary) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
    val shadowAlpha = 0.4f

    val buttonShape: Shape = ContinuousRoundedRectangle(cornerRadius)
    val buttonShapeBlock: () -> Shape = remember(cornerRadius) { { buttonShape } }
    val chromeLens = com.haooz.chedule.ui.utils.AppMaterialSettings.chromeLensEnabled()
    val buttonEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(chromeLens) {
        {
            vibrancy()
            blur(4.dp.toPx())
            // 均衡及以下关闭折射
            if (chromeLens) lens(8f.dp.toPx(), 24f.dp.toPx())
        }
    }
    val buttonOnDrawSurface: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit =
        remember(resolvedContainerColor, interactiveHighlight) {
            {
                drawRect(resolvedContainerColor)
                drawRect(Color.Black.copy(alpha = 0.03f * interactiveHighlight.pressProgress))
            }
        }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 整颗按钮（玻璃+采样+文字）一起放大：不走 layerBlock，
            // 避免库对 scale 做反变换导致背景采样被压小
            .graphicsLayer {
                clip = false
                transformOrigin = TransformOrigin.Center
                val progress = interactiveHighlight.pressProgress
                val pressScale = lerp(1f, 1f + 2f.dp.toPx() / size.height.coerceAtLeast(1f), progress)
                scaleX = pressScale
                scaleY = pressScale
                val offset = interactiveHighlight.offset
                val contentMin = size.minDimension.coerceAtLeast(1f)
                val initialDerivative = 0.05f
                translationX = contentMin * tanh(initialDerivative * offset.x / contentMin)
                translationY = contentMin * tanh(initialDerivative * offset.y / contentMin)
            }
            .drawBehind {
                val offsetY = 4.dp.toPx()
                val blurRadius = 10f * density
                val paint = Paint().apply {
                    color = android.graphics.Color.argb(
                        (shadowAlpha * 255f).toInt().coerceIn(0, 255),
                        (shadowColor.red * 255f).toInt().coerceIn(0, 255),
                        (shadowColor.green * 255f).toInt().coerceIn(0, 255),
                        (shadowColor.blue * 255f).toInt().coerceIn(0, 255)
                    )
                    maskFilter = BlurMaskFilter(
                        blurRadius.coerceAtLeast(0.1f),
                        BlurMaskFilter.Blur.NORMAL
                    )
                    style = Paint.Style.FILL
                }
                val radius = cornerRadius.toPx()
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRoundRect(
                        0f,
                        offsetY,
                        size.width,
                        size.height + offsetY,
                        radius,
                        radius,
                        paint
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                enabled = enabled,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick()
                }
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = buttonShapeBlock,
                effects = buttonEffects,
                highlight = null,
                shadow = null,
                // 不传 layerBlock：采样与玻璃同步由外层 graphicsLayer 缩放，不会反向压小
                layerBlock = null,
                onDrawSurface = buttonOnDrawSurface
            )
            .edgeLight(shape = buttonShape, edgeLight = rememberDefaultEdgeLight())
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
            .defaultMinSize(minHeight = minHeight)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.button,
            fontWeight = FontWeight.Medium,
            color = textColor.copy(alpha = if (enabled) 1f else 0.4f)
        )
    }
}
