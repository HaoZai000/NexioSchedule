package com.haooz.chedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.utils.LocalOverScrollState

/** 顶栏按钮背景的出现阈值：内容滚过 10dp（与 CollapsibleTopAppBar 内部同一判据） */
private val MaterialScrollThreshold = 10.dp

/**
 * 顶栏材质 alpha：内容滚过阈值后按弹簧升到 1，滚回顶部再降回 0。
 *
 * 判据与弹簧都对齐 [top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar] 的按钮背景，
 * 无材质档的渐变遮罩靠它跟按钮一起出现、一起消失。
 *
 * 返回值只在绘制期读（`graphicsLayer` / `draw` 阶段），动画值不进组合。
 */
@Composable
fun rememberTopBarMaterialAlpha(scrollBehavior: SharedScrollBehavior?): () -> Float {
    val thresholdPx = with(LocalDensity.current) { MaterialScrollThreshold.toPx() }
    // 只作身份键：offset 在 derivedStateOf 内部读，本身就会被订阅
    val overScrollState = LocalOverScrollState.current
    val scrolled by remember(scrollBehavior, thresholdPx, overScrollState) {
        derivedStateOf {
            // contentOffset 是累计滚动量：只有回到顶部时才会自然归零（不再被 fling 清零），
            // 所以触底回弹不会把材质抖掉。
            val contentOffset = scrollBehavior?.state?.contentOffset ?: 0f
            // 与 CollapsibleTopAppBar 的按钮背景同源：① 滚过阈值；② 顶部向下回弹
            if (contentOffset < -thresholdPx) true
            else contentOffset >= 0f && overScrollState.offset < 0f
        }
    }
    return rememberTopBarMaterialAlpha(scrolled)
}

/**
 * 判据由调用方给：页面自己有一套「材质何时出现」的进度时用它
 * （关于页按 `scrollProgress >= 1f` 才给按钮上背景）。
 */
@Composable
fun rememberTopBarMaterialAlpha(materialVisible: Boolean): () -> Float {
    val alpha = remember { Animatable(if (materialVisible) 1f else 0f) }
    LaunchedEffect(materialVisible) {
        alpha.animateTo(
            targetValue = if (materialVisible) 1f else 0f,
            animationSpec = if (materialVisible) {
                folmeSpring(damping = 1.0f, response = 0.6f)
            } else {
                folmeSpring(damping = 1.0f, response = 0.4f)
            },
        )
    }
    return remember(alpha) { { alpha.value } }
}
