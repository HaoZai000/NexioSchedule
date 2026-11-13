package top.yukonga.miuix.kmp.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * 系统返回事件的跨平台分发点（非 Android）。
 *
 * Android 由 activity 的 `OnBackPressedDispatcher` 分发；iOS / 鸿蒙这类「系统返回只能由
 * 宿主转交给 Compose」的平台没有该机制，所以这里留一个栈：弹窗 / 底部抽屉 / 连贯动画页
 * 在 `rememberNavigationBack().handler(...)` 里注册，宿主收到系统返回时调 [dispatch]。
 *
 * 后注册的（z 序在上）先被调用 —— 与组合顺序一致，弹窗天然压住它下面的页面。
 *
 * ⚠ 只在主线程（组合 / UI 线程）使用，不加锁。
 */
object BackDispatcher {

    private val handlers = mutableListOf<() -> Unit>()

    /** 当前是否有能消费返回的层。 */
    val hasHandlers: Boolean get() = handlers.isNotEmpty()

    /** 注册一个返回回调，返回用于注销的 token。 */
    fun push(handler: () -> Unit): Any {
        handlers.add(handler)
        return handler
    }

    fun remove(token: Any) {
        handlers.remove(token)
    }

    /** 分发一次返回；true = 已被消费（宿主不要退出应用）。 */
    fun dispatch(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handler()
        return true
    }
}

/**
 * Skia（iOS / Desktop / ohos / wasm）实现：CMP 1.12 无 predictive back 公共 API
 * （上游 Miuix 0.9.3 也仅在 androidMain 用 androidx.navigationevent）。
 *
 * 这里没有手势进度源，所以进度 / 边缘 / 位移一律报空值；但**返回键本身**必须能吃掉 ——
 * 注册进 [BackDispatcher]，由宿主（鸿蒙是 ArkTS 的 onBackPress）转交。
 * 调用方的状态机只靠 onBackCompleted 就能正常关闭（见上方注释），不需要进度。
 */
@Composable
actual fun rememberNavigationBack(): NavigationBackState = remember {
    NavigationBackState(
        // 没有跨帧进度源：保守报告「不在转场」「无进度」。
        isTransitioningBack = { false },
        backProgress = { null },
        // 没有手势源：不报边缘（2 = EDGE_NONE）
        backSwipeEdge = { 2 },
        // 没有触摸坐标源：无垂直/水平跟随
        backSwipeOffsetY = { null },
        backSwipeOffsetX = { null },
        onHandler = {
            val registered = enabled
            // 回调每次组合都是新 lambda，不能当 key，否则每帧都会注销重注册、打乱栈序
            val latestCompleted = rememberUpdatedState(onBackCompleted)
            DisposableEffect(registered) {
                val token = if (registered) BackDispatcher.push { latestCompleted.value() } else null
                onDispose { if (token != null) BackDispatcher.remove(token) }
            }
        },
    )
}
