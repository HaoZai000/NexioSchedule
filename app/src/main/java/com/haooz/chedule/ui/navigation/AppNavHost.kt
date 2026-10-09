package com.haooz.chedule.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 单宿主导航容器：渲染当前路由 + 接管系统返回。
 *
 * ## 转场
 *
 * 拆成 Activity 时，页面切换用的是系统默认的 Activity 转场；合并成单宿主之后
 * **系统不再提供这个动画**（没有第二个 Activity 可供动画），所以这里自己补一个
 * 「前进从右侧滑入、返回向右滑出」的转场，避免迁移后感觉变生硬。
 *
 * ⚠ **预测性返回（predictive back）目前没有接**：拆 Activity 时系统会给
 * 跨 Activity 的预测动画，单宿主下需要 `PredictiveBackHandler` + 自己驱动进度动画。
 * 这一版先用 `BackHandler` 保证返回**功能**正确，动画留到后续增量（已记进计划文档）。
 *
 * ## 返回语义
 *
 * 与 Activity 一致：栈里还有上一页 → 回上一页；否则 → 调 [onExit]（对应 `finish()`）。
 */
@Composable
fun AppNavHost(
    router: AppRouter,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (AppRoute) -> Unit,
) {
    BackHandler {
        router.backOrExit(onExit)
    }

    AnimatedContent(
        targetState = router.current,
        modifier = modifier,
        transitionSpec = {
            val duration = 220
            if (router.lastDirection == NavDirection.Pop) {
                (slideInHorizontally(tween(duration)) { -it / 4 } + fadeIn(tween(duration))) togetherWith
                    (slideOutHorizontally(tween(duration)) { it } + fadeOut(tween(duration)))
            } else {
                (slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration))) togetherWith
                    (slideOutHorizontally(tween(duration)) { -it / 4 } + fadeOut(tween(duration)))
            }
        },
        label = "AppNavHost",
    ) { route ->
        content(route)
    }
}
