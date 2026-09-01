package com.haooz.chedule.ui.basic

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 渐变模糊顶部栏容器
 *
 * 在液态玻璃模式下，为顶部栏提供渐变模糊效果。
 * 模糊层在底层，内容在顶层，避免循环采样。
 * API < 33 时降级为渐变遮罩。
 *
 * @param backdrop 液态玻璃 backdrop
 * @param modifier 外部 modifier
 * @param height 模糊区域基础高度（不含状态栏）
 * @param content 顶部栏内容（通常是 SmallTopAppBar）
 */
@Composable
fun ProgressiveBlurTopBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    height: Dp = Dp.Unspecified,
    tintIntensity: Float = 0.2f,
    tintColor: Color = MiuixTheme.colorScheme.surface,
    blurAlpha: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    // 显式传入 height 时直接使用；否则按状态栏自适应：有状态栏 80dp+状态栏，无状态栏 120dp
    val totalHeight = if (height != Dp.Unspecified) {
        height
    } else {
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        if (statusBarHeight > 0.dp) 80.dp + statusBarHeight else 120.dp
    }

    Box(modifier = modifier) {
        if (Build.VERSION.SDK_INT >= 33) {
            // 模糊层 - 在底层，采样 backdrop（API 33+）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
                    .graphicsLayer { alpha = blurAlpha }
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = {
                            blur(4f.dp.toPx())
                            runtimeShaderEffect(
                                "ProgressiveBlurAlphaMask",
                                """
    uniform shader content;
    uniform float2 size;
    layout(color) uniform half4 tint;
    uniform float tintIntensity;

    half4 main(float2 coord) {
        float blurAlpha = smoothstep(size.y, size.y * 0.6, coord.y);
        float tintAlpha = smoothstep(size.y, size.y * 0.7, coord.y);
        return mix(content.eval(coord) * blurAlpha, tint * tintAlpha, tintIntensity);
    }""",
                                "content"
                            ) {
                                // size uniform 需按降采样比例缩放，与模糊缓冲的实际像素范围对齐
                                setFloatUniform(
                                    "size",
                                    size.width * downsampleScale,
                                    size.height * downsampleScale
                                )
                                setColorUniform("tint", tintColor)
                                setFloatUniform("tintIntensity", tintIntensity)
                            }
                        }
                    )
            )
        } else {
            // 渐变遮罩降级（API < 33）- 先快后慢的渐变
            val gradientColor = MiuixTheme.colorScheme.surface
            val endY = totalHeight.value * density.density
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
                    .graphicsLayer { alpha = blurAlpha }
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to gradientColor.copy(alpha = 0.9f),
                                0.4f to gradientColor.copy(alpha = 0.82f),
                                0.7f to gradientColor.copy(alpha = 0.6f),
                                1.0f to gradientColor.copy(alpha = 0.0f)
                            ),
                            startY = 0f,
                            endY = endY
                        )
                    )
            )
        }
        // 内容层 - 在顶层
        content()
    }
}
