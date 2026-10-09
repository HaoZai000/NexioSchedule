// 返回键预测 —— 跨平台封装（navigationevent 的唯一用途）
//
// 背景：Miuix fork 在 6 个文件里用 androidx.navigationevent 做两件事：
//   ① NavigationBackHandler —— 接收系统返回键 / 预测性返回手势
//   ② navigationEventState.transitionState —— 读「转场进行中且方向为后退」，
//      用于在用户滑到一半松手时取消关闭（可回退的退场动画）
//
// navigationevent 是 AndroidX 库，CMP 的 iOS / Desktop artifact 里没有。
// 上游 0.9.3 同样没解决（commonMain 里直接 import 了 22 处，靠 Android artifact 兜底），
// 这里把它收成一个最小 expect 接口，commonMain 不再直接依赖 AndroidX。
//
// Android 实现逐字保留原逻辑（含 onBackCancelled 分支），保证 Android 行为零变化。

package top.yukonga.miuix.kmp.utils

import androidx.compose.runtime.Composable

/** [NavigationBackState.handler] 的入参。用一个类打包，是为了能以**具名参数**调用。 */
class BackHandlerScope(
    val enabled: Boolean,
    val onBackCompleted: () -> Unit,
    val onBackCancelled: () -> Unit,
)

/**
 * 返回转场状态。
 *
 * ⚠ **[handler] 与本对象里的进度读取必须来自同一次 [rememberNavigationBack]。**
 *
 * 原 fork 的写法是「**一个** `navigationEventState` 同时喂给 `NavigationBackHandler`
 * 和 `transitionState` 读取」—— 两者必须是同一个对象，否则 handler 收到的手势进度
 * 不会出现在另一个 state 上，跟手动画永远不动。
 *
 * 迁移时一度把它拆成两个独立的 expect 函数，各自 `rememberNavigationEventState()`，
 * 于是拿到两个不同对象 —— **编译全绿，Android 上预测性返回完全失效**。
 * 现在把 handler 收进本类，两个职责绑在同一份状态上，从签名上杜绝这种拆错。
 */
class NavigationBackState(
    val isTransitioningBack: () -> Boolean,
    /**
     * 手势进度 0f→1f；非手势期间为 null。
     *
     * DialogContentLayout 的可回退退场动画要按进度连续插值（缩放 + 蒙层淡出），
     * 所以这里必须给出连续值，而不只是布尔 —— 布尔只能支持「禁止中途关闭」。
     */
    val backProgress: () -> Float?,
    private val onHandler: @Composable BackHandlerScope.() -> Unit,
) {
    /**
     * 注册系统返回处理器（与本 state 共用同一份底层状态）。
     *
     * @param enabled 是否启用（与原 NavigationBackHandler 的 isBackEnabled 同义）
     * @param onBackCompleted 完整返回（键按下 / 手势完成）
     * @param onBackCancelled 返回手势中途取消（老平台不会触发）
     */
    @Composable
    fun handler(
        enabled: Boolean,
        onBackCompleted: () -> Unit,
        onBackCancelled: () -> Unit,
    ) {
        onHandler(BackHandlerScope(enabled, onBackCompleted, onBackCancelled))
    }
}

/**
 * 取得返回转场状态与返回处理器，**同一份状态**。
 *
 * 用法：
 * ```
 * val back = rememberNavigationBack()
 * back.handler(enabled = show, onBackCompleted = { … }, onBackCancelled = { … })
 * val progress = back.backProgress()
 * ```
 */
@Composable
expect fun rememberNavigationBack(): NavigationBackState

/**
 * 处理系统返回键 —— 只处理不读进度的便捷入口（如 MiuixPopupUtils）。
 *
 * 若调用点还要读手势进度驱动动画，**必须改用 [rememberNavigationBack] 后调用
 * `state.handler(...)`**，否则会各自持有一份独立状态，跟手动画不会动。
 */
@Composable
fun NavigationBackHandlerFor(
    enabled: Boolean,
    onBackCompleted: () -> Unit,
    onBackCancelled: () -> Unit,
    state: NavigationBackState = rememberNavigationBack(),
) {
    state.handler(enabled, onBackCompleted, onBackCancelled)
}