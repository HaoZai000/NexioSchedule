package com.haooz.chedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.kyant.backdrop.backdrops.SharedBlurBackdrop

/**
 * 页级玻璃（鸿蒙实现）：不接管采样。
 *
 * 与桌面/iOS 同一个口径 —— 鸿蒙端 `isRenderEffectSupported()` 是 false，本来就没有
 * 可用的采样管线（见 `BackdropPlatformOhos`），卡片直接走「无壁纸」的纯色分支。
 * 保留这个 actual 是为了让可移植源码里的 expect 在每个平台都有落点。
 */
@Composable
actual fun PageGlass(
    registry: GlassCardRegistry,
    backdrop: SharedBlurBackdrop,
    bakedLayer: GraphicsLayer?,
    bakeScale: Float,
    refractionHeight: Float,
    refractionAmount: Float,
    modifier: Modifier
) {
    // no-op
}
