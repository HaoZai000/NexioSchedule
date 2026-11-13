/** 背景特效 - 绘制动态背景效果 */
package com.haooz.chedule.ui.effects.background

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.haooz.chedule.ui.utils.isAppDarkTheme
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BgEffectBackground(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    isFullSize: Boolean = false,
    effectBackground: Boolean = true,
    alpha: () -> Float = { 1f },
    content: @Composable (BoxScope.() -> Unit),
) {
    BgEffectBackgroundWithPreset(
        dynamicBackground = dynamicBackground,
        presetOverride = null,
        timeScale = 1f,
        modifier = modifier,
        bgModifier = bgModifier,
        isFullSize = isFullSize,
        effectBackground = effectBackground,
        alpha = alpha,
        content = content,
    )
}

/**
 * 与 [BgEffectBackground] 相同，但允许调用方注入配色预设与动画时间倍率。
 *
 * @param presetOverride null 时按深浅色 / 平板取内置预设；非 null 时完全以它为准。
 * @param timeScale 动画时间倍率，越小流动越慢（1 = 关于页当前节奏）。
 */
@Composable
internal fun BgEffectBackgroundWithPreset(
    dynamicBackground: Boolean,
    presetOverride: BgEffectConfig.Config?,
    timeScale: Float,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    isFullSize: Boolean = false,
    effectBackground: Boolean = true,
    alpha: () -> Float = { 1f },
    content: @Composable (BoxScope.() -> Unit),
) {
    val shaderSupported = remember { isRuntimeShaderSupported() }
    if (!shaderSupported) {
        Box(modifier = modifier, content = content)
        return
    }
    Box(
        modifier = modifier,
    ) {
        val surface = MiuixTheme.colorScheme.surface
        val isDarkTheme = isAppDarkTheme()
        // 原为 LocalConfiguration.current.screenWidthDp >= 600 —— 跨平台后用 miuix 的 expect/actual
        //（Android 端逐字等价读 LocalConfiguration；skiko 端按窗口尺寸换算）
        val isTablet = isTabletWidth()
        val painter = remember { BgEffectPainter() }

        val preset = remember(isDarkTheme, isTablet, presetOverride) {
            presetOverride ?: BgEffectConfig.get(isDarkTheme, isTablet)
        }

        val colorStage = remember { Animatable(0f) }

        LaunchedEffect(dynamicBackground, preset) {
            if (!dynamicBackground) return@LaunchedEffect
            val animatesColors = preset.colors1 !== preset.colors2 || preset.colors2 !== preset.colors3
            if (!animatesColors) return@LaunchedEffect

            var targetStage = floor(colorStage.value) + 1f
            while (isActive) {
                delay((preset.colorInterpPeriod * 500).toLong().milliseconds)
                colorStage.animateTo(
                    targetValue = targetStage,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
                )
                targetStage += 1f
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .then(bgModifier)
                .bgEffectDraw(
                    painter = painter,
                    preset = preset,
                    isDarkTheme = isDarkTheme,
                    surface = surface,
                    effectBackground = effectBackground,
                    isFullSize = isFullSize,
                    playing = dynamicBackground,
                    colorStage = { colorStage.value },
                    alpha = alpha,
                    isTablet = isTablet,
                    timeScale = timeScale,
                ),
        )
        content()
    }
}
