package top.yukonga.miuix.kmp.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * Android 实现 —— 直接包住 androidx.navigationevent，逻辑逐字照搬 fork 原用法。
 *
 * 关键：**handler 与进度读取共用同一个 `navigationEventState`**。原 fork 就是这么写的：
 * ```
 * val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)
 * NavigationBackHandler(state = navigationEventState, …)
 * snapshotFlow { navigationEventState.transitionState }
 * ```
 * 若拆成两处各自 remember，就会拿到两个独立对象 —— handler 收到手势，
 * 而读取进度的那个 state 永远不动，跟手动画完全失效（且编译无任何报错）。
 *
 * 「低版本退化为立即关闭」是 navigationevent 自身的行为：老平台没有预测性返回手势，
 * onBackCancelled 不会被调用，所以各调用点的状态机仍要能只靠 onBackCompleted 正常关闭。
 */
@Composable
actual fun rememberNavigationBack(): NavigationBackState {
    val navigationEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)

    return remember(navigationEventState) {
        NavigationBackState(
            isTransitioningBack = {
                val t = navigationEventState.transitionState
                t is NavigationEventTransitionState.InProgress &&
                    t.direction == NavigationEventTransitionState.TRANSITIONING_BACK
            },
            // transitionState.latestEvent.progress 就是手势的连续进度；
            // 非手势期间不是 InProgress，此时返回 null 让调用方保持静止。
            backProgress = {
                val t = navigationEventState.transitionState
                if (t is NavigationEventTransitionState.InProgress &&
                    t.direction == NavigationEventTransitionState.TRANSITIONING_BACK
                ) t.latestEvent.progress else null
            },
            onHandler = {
                NavigationBackHandler(
                    state = navigationEventState,
                    isBackEnabled = enabled,
                    onBackCompleted = onBackCompleted,
                    onBackCancelled = onBackCancelled,
                )
            },
        )
    }
}