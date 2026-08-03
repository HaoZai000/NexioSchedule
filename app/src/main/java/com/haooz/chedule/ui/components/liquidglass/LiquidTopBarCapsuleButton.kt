package com.haooz.chedule.ui.components.liquidglass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.edgelight.edgeLight
import com.haooz.chedule.edgelight.rememberDefaultEdgeLight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.More
import androidx.core.graphics.toColorInt

@Composable
fun LiquidTopBarCapsuleButton(
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    buttonHeight: Dp = 40.dp,
    useBackdropShadow: Boolean = false
) {
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val isLightTheme = !isAppDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFFFFFFFF).copy(0.6f)
        else Color(0xFF121212).copy(0.54f)

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    val shadowColor = if (isLightTheme) "#12000000".toColorInt() else "#20000000".toColorInt()

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .height(buttonHeight)
            .width(88.dp)
            .padding(end = 4.dp)
            .then(
                if (!useBackdropShadow) {
                    Modifier.drawBehind {
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
                } else {
                    Modifier
                }
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(18f.dp.toPx(), 18f.dp.toPx())
                },
                highlight = null,
                shadow = if (useBackdropShadow) {
                    { Shadow(alpha = 0.3f) }
                } else {
                    null
                },
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
            .edgeLight(shape = Capsule(), edgeLight = rememberDefaultEdgeLight())
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(88.dp)
        ) {
            Icon(
                imageVector = MiuixIcons.Normal.ConvertFile,
                contentDescription = "课表切换",
                modifier = Modifier
                    .size(25.dp)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onLeftClick()
                        }
                    ),
                tint = if (isLightTheme) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
            )
            Icon(
                imageVector = MiuixIcons.More,
                contentDescription = "更多",
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onRightClick()
                        }
                    ),
                tint = if (isLightTheme) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
