package com.haooz.chedule.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable



/**
 * 供 `:app` 深处的 Composable（如 `SettingsScreen`）发起路由跳转，**不经过 Activity 引用**。
 *
 * 在 `MainActivity.setContent` 的最外层 provide，任何宿主内容里的页面都能读到。
 * ⚠ 只在主线程（组合）里用，不加锁。
 */
val LocalAppRouter = staticCompositionLocalOf<AppRouter> {
    error("LocalAppRouter 未提供：请在 MainActivity.setContent 最外层 CompositionLocalProvider")
}


/** 最近一次导航的方向，供转场动画判方向。 */
enum class NavDirection { Push, Pop }
/**
 * 子页叠加路由器。
 *
 * ## 「叠加」而不是「替换」—— 这是本次设计里最重要的一个决定
 *
 * 主界面（`CourseScheduleApp`）**永远保持组合**，不参与路由的 save/restore。
 * 子页（关于 / 更新日志 / …）以叠加层的方式压在主界面**之上**，出栈时销毁。
 *
 * 这么做的原因：拆 Activity 时代跳到子页，主 Activity 只是 **stop、没有销毁**，
 * 它的**全部组合状态**（pager 位置、壁纸映射、非 saveable 的临时状态）都原样保留。
 * 若把主界面也塞进 `AnimatedContent` + `SaveableStateHolder`，
 * `rememberSaveable` 能救回来，但 `remember { mutableStateOf }` 之类救不回来
 * —— `CourseScheduleApp` 里有大量这类状态，丢了就是「切到设置再回来 pager 位置变了」。
 *
 * 代价：主界面在子页打开期间**持续组合**（没有被回收）。可接受 —— 原来多个
 * Activity 共存的方案里主 Activity 也只是 stop 而不是 destroy。
 *
 * ## 生命周期
 *
 * 通过 [rememberAppRouter] 创建，用 `rememberSaveable` 保存**当前栈**，
 * 配置变更/进程被杀后重建都能回到原来的页面。
 *
 * ## 并发
 *
 * 只在主线程（Compose 组合）里改，与 Activity 的 `OnBackPressedDispatcher` 同线程。
 */
@Stable
class AppRouter {

    private val stack = mutableStateListOf<AppRoute>()

    /** 是否有子页叠加在主界面之上。false 时返回键交给 Android 宿主自己处理。 */
    val hasOverlay: Boolean get() = stack.isNotEmpty()

    /** 当前叠加的路由；没有子页时为 null（此时主界面直接可见）。 */
    val current: AppRoute? get() = stack.lastOrNull()

    val depth: Int get() = stack.size

    var lastDirection: NavDirection = NavDirection.Push
        private set

    /** 入栈。同一个路由重复入栈是允许的（对应原来重复 startActivity）。 */
    fun navigate(route: AppRoute) {
        stack.add(route)
        lastDirection = NavDirection.Push
    }

    /**
     * 出栈。返回 false 表示栈已经空了 —— 此时返回键应交给 Android 宿主
     * （MainActivity 的预测性返回 /「退出即隐藏后台」）。
     */
    fun popBack(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        lastDirection = NavDirection.Pop
        return true
    }

    internal fun stackIds(): List<String> = stack.map { it.id }
}

/**
 * 创建并记住一个空的叠加路由器。
 *
 * ⚠ 栈**从空开始**：主界面不是路由，是常驻底座。
 * 只有 `navigate(AppRoute.X)` 之后栈才非空、`hasOverlay` 才为 true。
 */
@Composable
fun rememberAppRouter(): AppRouter {
    return rememberSaveable(saver = appRouterSaver) { AppRouter() }
}

/** 保存**当前栈**而不是初始值 —— 用初始值会在旋转后把用户弹回首页。 */
private val appRouterSaver: Saver<AppRouter, Any> = listSaver(
    save = { it.stackIds() },
    restore = { ids -> AppRouter().also { r -> ids.mapNotNull(AppRoute::fromId).forEach(r::navigate) } },
)
