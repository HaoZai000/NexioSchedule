package com.haooz.chedule.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** 最近一次导航的方向，供转场动画判方向。 */
enum class NavDirection { Push, Pop }

/**
 * 应用内返回栈。
 *
 * 持有 `AppRoute` 的栈，是「导航状态」的唯一来源 —— 它放在 Compose 里而不是
 * Activity 的 back stack 里，正是为了让同一份状态将来能被 iOS 复用。
 *
 * ## 生命周期
 *
 * 通过 [rememberAppRouter] 创建，用 `rememberSaveable` 保存**当前栈**，
 * 所以配置变更（旋转）与进程被回收后重建都能回到原来的页面。
 *
 * ## 并发
 *
 * 只在主线程（Compose 组合）里改，与 Activity 的 `OnBackPressedDispatcher` 同线程，
 * 不需要加锁。
 */
@Stable
class AppRouter(initial: List<AppRoute>) {

    private val stack = mutableStateListOf<AppRoute>().apply { addAll(initial) }

    /** 当前栈（只读快照语义；内部是 `SnapshotStateList`，读它会自动触发重组）。 */
    val routes: List<AppRoute> get() = stack

    val current: AppRoute get() = stack.last()

    /** 还能不能返回。false 表示再返回就该退出宿主（对应原来 Activity 的 `finish()`）。 */
    val canGoBack: Boolean get() = stack.size > 1

    val depth: Int get() = stack.size

    var lastDirection: NavDirection = NavDirection.Push
        private set

    /** 入栈。同一个路由重复入栈是允许的（对应原来重复 startActivity）。 */
    fun navigate(route: AppRoute) {
        stack.add(route)
        lastDirection = NavDirection.Push
    }

    /**
     * 出栈。返回 false 表示栈里只剩一个，调用方应当退出宿主。
     *
     * 与 Activity 的 back 语义一致：**回到上一个页面**，不是「跳到某个目标页」。
     */
    fun popBack(): Boolean {
        if (!canGoBack) return false
        stack.removeAt(stack.lastIndex)
        lastDirection = NavDirection.Pop
        return true
    }

    /**
     * 返回；栈里已经没有上一页时执行 [onExit]（对应原来 Activity 的 `finish()`）。
     *
     * 页面顶栏的返回按钮必须走这个，**不要只调 [popBack]** ——
     * 在栈底页上 `popBack()` 会返回 false 而不做任何事，按钮看起来就"失灵"了。
     */
    fun backOrExit(onExit: () -> Unit) {
        if (!popBack()) onExit()
    }

    internal fun stackIds(): List<String> = stack.map { it.id }
}

/** 创建并记住一个 [AppRouter]；配置变更/进程重建后按原栈恢复。 */
@Composable
fun rememberAppRouter(initial: List<AppRoute>): AppRouter {
    val saver = remember(initial) { appRouterSaver(initial) }
    return rememberSaveable(saver = saver) { AppRouter(initial) }
}

/**
 * 单路由入口的便捷重载（绝大多数宿主只需要一个初始页）。
 */
@Composable
fun rememberAppRouter(initial: AppRoute): AppRouter = rememberAppRouter(listOf(initial))

/**
 * 保存**当前栈**而不是初始栈 —— 用初始栈会在旋转后把用户弹回首页。
 *
 * 还原时若 id 全部认不出来（版本回退到没有该路由的旧版本），退回 [fallback]，
 * 至少保证有个能渲染的页面，而不是让宿主空白或崩溃。
 */
private fun appRouterSaver(fallback: List<AppRoute>): Saver<AppRouter, Any> = listSaver(
    save = { it.stackIds() },
    restore = { ids ->
        val restored = ids.mapNotNull(AppRoute::fromId)
        AppRouter(restored.ifEmpty { fallback })
    },
)
