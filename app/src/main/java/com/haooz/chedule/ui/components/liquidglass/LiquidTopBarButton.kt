package com.haooz.chedule.ui.components.liquidglass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@Composable
fun LiquidTopBarButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    buttonHeight: Dp = 40.dp
) {
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    val shadowColor = if (isLightTheme) android.graphics.Color.parseColor("#12000000") else android.graphics.Color.parseColor("#20000000")

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(buttonHeight)
            .drawBehind {
                val blurRadius = 6f * density
                val cornerRadiusPx = buttonHeight.toPx() / 2f
                val paint = android.graphics.Paint().apply {
                    color = shadowColor
                    maskFilter = android.graphics.BlurMaskFilter(
                        blurRadius,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRoundRect(
                        0f, 0f, size.width, size.height,
                        cornerRadiusPx, cornerRadiusPx,
                        paint
                    )
                }
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 12f.dp.toPx())
                },
                shadow = null,
                layerBlock = {
                    val progress = interactiveHighlight.pressProgress
                    val scale = 1f + 2f.dp.toPx() / buttonHeight.toPx() * progress
                    scaleX = scale
                    scaleY = scale
                    val offset = interactiveHighlight.offset
                    translationX = size.minDimension * 0.05f * offset.x / size.maxDimension
                    translationY = size.minDimension * 0.05f * offset.y / size.maxDimension
                },
                onDrawSurface = {
                    drawRect(containerColor)
                    drawRect(Color.Black.copy(alpha = 0.03f * interactiveHighlight.pressProgress))
                }
            )
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick()
                }
            )
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (isLightTheme) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
        )
    }
}
