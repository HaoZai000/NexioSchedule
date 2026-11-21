/** 模糊工具类 - 提供高斯模糊效果 */
package com.haooz.chedule.ui.effects.miuix

import androidx.compose.runtime.Composable
import com.haooz.nexioui.theme.NexioTheme
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    val surfaceColor = NexioTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}
