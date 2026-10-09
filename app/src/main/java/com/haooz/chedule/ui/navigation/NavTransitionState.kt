package com.haooz.chedule.ui.navigation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * 一个转场进度持有者。有两份：[AppNavHost] 的栈顶进度，以及主界面（main）的首层进度。
 *
 * 主界面那份为什么要单独存在：它是**常驻底座**，不进转场容器（见 [AppRouter]），
 * 视差拿不到同一个 `p`，只能把进度提到两边都够得着的高度 —— [AppNavHost] 写、
 * MainActivity 的容器读。这也是这块逻辑要动到 MainActivity 的唯一原因。
 *
 * 主界面那份的 `value`：0 = 完全可见，1 = 首层子页完全盖住。只有 **depth 跨越 0/1**
 * 的那次转场才驱动它；更深的 push/pop 期间保持不变 —— 按 1da9c0a8 的口径，
 * 嵌套打开时主页就该**保持既有偏移**。
 */
@Stable
class NavTransitionState(initialValue: Float = 0f) {
    // 显式 .floatValue 读写：委托操作符只在 @Composable 上下文可用，这是个普通类
    private val raw = mutableFloatStateOf(initialValue)

    /**
     * 当前进度 0..1。
     *
     * ⚠ 只能在 layout / draw 的 lambda 里读：在组合期读会让页面整树跟着转场每帧重组。
     */
    val value: Float get() = raw.floatValue

    /**
     * 从**当前值**续播到 [target]，不跳回起点 ——
     * 1da9c0a8 里 `animateEnter` 只在首次 `snapTo(0)` 的那条规则。
     */
    internal suspend fun animateTo(target: Float, spec: AnimationSpec<Float>) {
        animate(
            initialValue = raw.floatValue,
            targetValue = target,
            initialVelocity = 0f,
            animationSpec = spec,
        ) { value, _ -> raw.floatValue = value }
    }

    /** 预测性返回跟手：逐帧直接落值，不播动画。 */
    internal fun snapTo(target: Float) {
        raw.floatValue = target.coerceIn(0f, 1f)
    }
}

/**
 * 记住一个 [NavTransitionState]。
 *
 * `initialValue` 只在首次组合时生效：栈被恢复成非空时（旋转 / 进程重建）应已是「被盖住」
 * 的 1，不能从 0 起步白播一次进入动画。
 */
@Composable
fun rememberNavTransitionState(initialValue: Float = 0f): NavTransitionState =
    remember { NavTransitionState(initialValue) }

/**
 * 主界面外层容器：按 [NavTransitionState] 施加**视差**，
 * 把子页之间那套转场观感补到 main ↔ 首层子页这一段。
 *
 * 压暗**不在这里做** —— 统一由 `AppNavHost` 的全屏黑幕负责，否则首层会 42% 叠 42%。
 *
 * ⚠ 位移用 `Modifier.layout` 自己 place：`offset` 会写进 constraints 导致整棵主界面树
 * 每帧 re-measure（pager / 玻璃采样跟着重测必掉帧）；`graphicsLayer { translationX }`
 * 不重测但会套离屏 layer，可能干扰页内 `layerBackdrop` 采样。
 */
@Composable
fun MainLayerTransition(
    state: NavTransitionState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                // ⚠ 原样 measure：不把位移写进 constraints，子节点才不会被迫重测
                val placeable = measurable.measure(constraints)
                val dx = (-NAV_PARALLAX * state.value * placeable.width).roundToInt()
                layout(placeable.width, placeable.height) {
                    placeable.place(dx, 0)
                }
            },
    ) { content() }
}
