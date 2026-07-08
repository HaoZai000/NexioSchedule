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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add

@Composable
fun LiquidAddButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
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

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(56.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(18f.dp.toPx(), 18f.dp.toPx())
                },
                layerBlock = {
                    val progress = interactiveHighlight.pressProgress
                    val scale = 1f + 4f.dp.toPx() / size.height * progress
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
            imageVector = MiuixIcons.Medium.Add,
            contentDescription = "添加课程",
            modifier = Modifier.size(24.dp),
            tint = if (isLightTheme) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
        )
    }
}
