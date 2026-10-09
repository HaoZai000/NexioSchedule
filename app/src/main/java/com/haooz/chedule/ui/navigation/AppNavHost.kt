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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier

/**
 * 子页叠加层：渲染当前叠加路由 + 在有子页时接管系统返回。
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
 * 栈里还有子页 → 本路由器出栈；栈空了 → `BackHandler` 的 `enabled` 变 false，
 * 返回键**不拦截**，交还给 Android 宿主（MainActivity 的预测性返回 / 隐藏后台）。
 * 这就是为什么 [AppRouter] 的栈从空开始、主界面不参与路由。
 *
 * ## ⚠ 必须用 `SaveableStateHolder`，否则返回后页面状态全丢
 *
 * `AnimatedContent` 切换 `targetState` 时会把离开的页面**从组合里移除**。
 * 而 `rememberSaveable` 托管的滚动位置（`rememberLazyListState()`、
 * `rememberCollapsibleTopAppBarState()`）**只有在有 `SaveableStateHolder` 时才会被保存** ——
 * 没有它，状态随组合一起丢，表现就是「从子页返回，上一页的滚动位置没了」。
 *
 * 这是拆 Activity 改成单宿主时**最容易漏的一步**：拆 Activity 时子页是另一个 Activity，
 * 父 Activity 只是 stop、没有销毁，状态还活着；合并成单宿主之后就必须显式托管。
 * AndroidX Navigation 内部也是用 `SaveableStateHolder` 做这件事的。
 *
 * ## ⚠ 已知行为：被弹出的页面会记住上次的位置
 *
 * 这里**不**在出栈时清掉保存的状态（AndroidX 会在退出转场结束后删）。后果：
 * 同一宿主会话内，重新打开一个刚弹出的页面会回到上次的滚动位置，而不是从顶部开始。
 * 对这几页文档来说「回到你上次看到的地方」是合理甚至更舒服的，所以先这样；
 * 若要严格对齐 Android（重新进入从头开始），得在**退出转场播完之后**再 `removeState`
 * —— 退出中的 `SaveableStateProvider` 在 dispose 时会再保存一次，立刻删会被它写回去。
 */
@Composable
fun AppNavHost(
    router: AppRouter,
    modifier: Modifier = Modifier,
    content: @Composable (AppRoute) -> Unit,
) {
    // 按路由 id 托管各页的 saveable 状态：留在栈里的页面，回来时滚动位置/折叠进度都还在。
    // key 用 route.id：同一个路由被压两次会共用状态 —— 当前子页互不跳自己，
    // 不存在这种情况；将来若出现自跳或带参数路由，需要改成「按栈条目分配唯一 key」。
    val stateHolder = rememberSaveableStateHolder()

    // 只在有子页时接管返回；没有子页时不拦截，Android 宿主的返回逻辑照常生效
    BackHandler(enabled = router.hasOverlay) {
        router.popBack()
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
        if (route != null) {
            stateHolder.SaveableStateProvider(route.id) {
                content(route)
            }
        }
    }
}
