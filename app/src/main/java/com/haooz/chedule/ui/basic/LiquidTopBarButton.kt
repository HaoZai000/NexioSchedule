package com.haooz.chedule.ui.basic

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.toColorInt
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberLiquidTopBarButtonEdgeLight
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.isAppDarkTheme
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
    iconOffset: DpOffset = DpOffset.Zero,
    buttonHeight: Dp = 42.dp,
    backdropAlpha: Float = 1f,
    shadowAlpha: Float = 1f,
    iconTint: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    draggable: Boolean = false
) {
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val isLightTheme = !isAppDarkTheme()
    val resolvedContainerColor = if (containerColor != Color.Unspecified) containerColor
        else if (isLightTheme) Color(0xFFFFFFFF).copy(0.8f)
        else Color(0xFF242424).copy(0.9f)

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    val shadowColor = if (isLightTheme) "#12000000".toColorInt() else "#20000000".toColorInt()

    Box(
        modifier = modifier
            .wrapContentSize()
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
                .size(buttonHeight)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(15f.dp.toPx(), 15f.dp.toPx())
                    },
                    highlight = null,
                    shadow = null,
                    layerBlock = {
                        val progress = interactiveHighlight.pressProgress
                        val scale = 1f + 2f.dp.toPx() / buttonHeight.toPx() * progress
                        scaleX = scale
                        scaleY = scale
                        val offset = interactiveHighlight.offset
                        translationX = size.minDimension * 0.05f * offset.x / size.maxDimension
                        translationY = size.minDimension * 0.05f * offset.y / size.maxDimension
                        alpha = backdropAlpha
                    },
                    onDrawSurface = {
                        drawRect(resolvedContainerColor)
                        drawRect(Color.Black.copy(alpha = 0.03f * interactiveHighlight.pressProgress))
                    }
                )
                .edgeLight(shape = CircleShape, edgeLight = rememberLiquidTopBarButtonEdgeLight())
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
                .then(if (draggable) interactiveHighlight.gestureModifier else interactiveHighlight.pressOnlyModifier)
                .zIndex(0f)
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(iconSize)
                .offset(iconOffset.x, iconOffset.y)
                .zIndex(1f),
            tint = if (iconTint != Color.Unspecified) iconTint else if (isLightTheme) Color.Black.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f)
        )
    }
}
