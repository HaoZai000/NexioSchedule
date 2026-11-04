package com.haooz.chedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.kyant.backdrop.backdrops.SharedBlurBackdrop

/**
 * 页级玻璃（鸿蒙实现）：不接管采样。
 *
 * 与桌面/iOS 同一个口径 —— 这条「N 张卡并成一条 unionPath、一页只采样一次」的合并管线
 * 只做了 Android 的 AGSL 版本，鸿蒙没有对应实现，所以卡片保持原来的「每卡各自 drawBackdrop」。
 * 保留这个 actual 是为了让可移植源码里的 expect 在每个平台都有落点。
 *
 * ⚠ 别按「鸿蒙没有采样管线」来推：`backdrop` 那边的 `isRenderEffectSupported()` 在鸿蒙是
 * **true**（模糊走 Compose 的 `BlurEffect` + `GraphicsLayer.renderEffect`），只有
 * `isRuntimeShaderSupported()` 是 false（缺 `asComposeShader` 那座桥）。
 * 也就是说 `hasSharedBlur` 在鸿蒙是成立的，卡片确实有玻璃可画 —— 只是不经过这个 PageGlass。
 *
 * 所以调用点（MainScheduleScreen 的 `pageGlassActive`）**不能**只凭
 * `isRuntimeShaderSupported()` 为 false 就认为「鸿蒙没玻璃、可以随便放开」：
 * 那会让 `GlassCardRegistry` 被提供、卡片不再自己采样，而这里什么都不画，
 * 玻璃直接消失，且编译与测试都不会报错。
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
