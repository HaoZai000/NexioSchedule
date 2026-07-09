package com.haooz.chedule.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 长按空白区域后浮现的"自定义课表"按钮。
 *
 * 显示时带有缩放/淡入动画，背景点击会隐藏。点击按钮触发 [onClick]。
 *
 * @param visible 是否显示
 * @param backdrop 模糊 backdrop，与主界面共享
 * @param isDark 当前是否为深色主题
 * @param onClick 点击按钮回调
 * @param onDismiss 用户点击外部空白区域时回调
 */
@Composable
internal fun LongPressCustomizeButton(
    visible: Boolean,
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
    isDark: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var showOverlay by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(0f) }
    var alpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            showOverlay = true
            coroutineScope {
                launch {
                    animate(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = tween(400, easing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.15f))
                    ) { value, _ -> scale = value }
                }
                launch {
                    animate(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = tween(400)
                    ) { value, _ -> alpha = value }
                }
            }
        } else if (scale > 0.001f) {
            coroutineScope {
                launch {
                    animate(
                        initialValue = scale,
                        targetValue = 0.6f,
                        animationSpec = tween(120, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
                    ) { value, _ -> scale = value }
                }
                launch {
                    animate(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = tween(120)
                    ) { value, _ -> alpha = value }
                }
            }
            showOverlay = false
        }
    }

    if (!showOverlay) return

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        )
        val bottomblurColors = BlurDefaults.blurColors(
            blendColors = listOf(
                if (isDark) BlendColorEntry(
                    MiuixTheme.colorScheme.background.copy(alpha = 0.7f),
                    BlurBlendMode.Screen
                )
                else BlendColorEntry(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                    BlurBlendMode.Screen
                )
            ),
            brightness = 0f,
            contrast = 1f,
            saturation = 1.2f
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                    this.alpha = alpha
                }
                .textureBlur(
                    backdrop = backdrop,
                    shape = RoundedRectangle(25.dp),
                    blurRadius = 25f,
                    colors = bottomblurColors,
                )
                .clip(RoundedRectangle(25.dp))
        ) {
            Button(
                modifier = Modifier.width(140.dp).height(48.dp),
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(color = androidx.compose.ui.graphics.Color.Transparent)
            ) {
                Text(
                    text = "自定义课表",
                    fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }
        }
    }
}
