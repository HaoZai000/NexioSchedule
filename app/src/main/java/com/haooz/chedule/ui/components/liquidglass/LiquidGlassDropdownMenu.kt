package com.haooz.chedule.ui.components.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch

private val MenuEnterEasing = CubicBezierEasing(0.3f, 1.25f, 0.32f, 1f)
private val ShadowPadding = 12.dp

@Composable
fun LiquidGlassDropdownMenu(
    show: Boolean,
    onDismissRequest: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)
    val shadowColor = if (isLightTheme) android.graphics.Color.parseColor("#12000000")
    else android.graphics.Color.parseColor("#20000000")

    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(show) {
        if (show) {
            launch {
                scale.animateTo(1f, tween(420, easing = MenuEnterEasing))
            }
            launch {
                alpha.animateTo(1f, tween(250))
            }
        } else {
            launch {
                scale.animateTo(0.24f, tween(200, easing = FastOutSlowInEasing))
            }
            launch {
                alpha.animateTo(0f, tween(200))
            }
        }
    }

    if (alpha.value <= 0f && !show) return

    Box(
        modifier = modifier
            .width(200.dp + ShadowPadding * 2)
            .height(96.dp + ShadowPadding * 2)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                transformOrigin = TransformOrigin(1f, 0f)
                clip = false
            }
    ) {
        // 阴影
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(ShadowPadding)
                .drawBehind {
                    val blurRadius = 2f * density
                    val cornerRadiusPx = 22.dp.toPx()
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
        )
        // 菜单内容
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(ShadowPadding)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(22.dp) },
                    effects = {
                        vibrancy()
                        blur(2f.dp.toPx())
                        lens(12f.dp.toPx(), 12f.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(containerColor)
                    }
                )
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
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
    val isLightTheme = !isSystemInDarkTheme()
    val textColor = if (isLightTheme) Color(0xFF1A1A1A) else Color(0xFFE8E4DE)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedRectangle(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
