package com.haooz.chedule.ui.navigation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * 主界面转场的「静止脉冲」。
 *
 * [MainLayerTransition] 用 `Modifier.layout` 平移整棵主界面树：**转场进行中整层向左平移是设计**，
 * 卡片玻璃要相对卡片不动（纹理跟着整层一起走），逐帧重绘没有意义
 * 真正让「玻璃跳变归位」消失的是 [MainLayerTranslation]
 */
val LocalMainLayerRedrawSignal = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 主界面整层的转场位移（px，负 = 左移），供「按窗口坐标采壁纸」的玻璃补回
 */
@Stable
class MainLayerTranslation {
    var dxPx: Float = 0f
}

val LocalMainLayerTranslation = staticCompositionLocalOf { MainLayerTranslation() }

/**
 * 转场进度持有者（0 = 起始，1 = 完成）。两份：[AppNavHost] 的栈顶进度，和主界面（main）的首层进度 —— 主界面是常驻底座、不进转场容器（见 [AppRouter]），视差只能由宿主施加，故进度提到两边都够得着的高度：[AppNavHost] 写、MainActivity 容器读。
 *
 * 主界面那份只有 depth 跨越 0/1 的转场才驱动；更深的 push/pop 期间保持不变（嵌套打开时主页保持既有偏移）。
 */
@Stable
class NavTransitionState(initialValue: Float = 0f) {
    // 显式 .floatValue：委托操作符只在 @Composable 上下文可用，这是个普通类
    private val raw = mutableFloatStateOf(initialValue)

    /**
     * 当前进度 0..1。
     *
     * ⚠ 只能在 layout / draw 的 lambda 里读 —— 组合期读会让整页每帧重组。
     */
    val value: Float get() = raw.floatValue

    /**
     * 突变代数：每次 [snapTo] / 新的一次 [animateTo] 都自增；正在跑的动画发现代数变了就不再落值，返回 false。
     *
     * ⚠ 别删。没有它，预测性返回跟手时「手势每帧落的值」会被「还在跑的入场动画」下一帧盖回去，表现跟手不动/抖动（等价于旧版 `Animatable` 的 `MutatorMutex`）。
     */
    private var mutation = 0

    /**
     * 从**当前值**续播到 [target]，不跳回起点。
     *
     * @return true = 完整播到了 [target]；false = 中途被 [snapTo] 或新的一次 [animateTo]
     *   挤掉。**被打断时调用方不要再跑收尾代码** —— pop 的收尾是「清 outgoing + 把 p
     *   拉回 1」，半路强拉会把正在跟手 / 正在入场的那一层瞬间甩到屏外。
     */
    suspend fun animateTo(target: Float, spec: AnimationSpec<Float>): Boolean {
        val token = ++mutation
        animate(
            initialValue = raw.floatValue,
            targetValue = target,
            initialVelocity = 0f,
            animationSpec = spec,
        ) { value, _ -> if (token == mutation) raw.floatValue = value }
        return token == mutation
    }

    /** 预测性返回跟手：逐帧直接落值、不播动画，并把正在跑的转场动画挤掉（见 [mutation]）。 */
    fun snapTo(target: Float) {
        mutation++
        raw.floatValue = target.coerceIn(0f, 1f)
    }
}

/**
 * 转场视差系数。主界面的 [MainLayerTransition] 与 `AppNavHost` 的下层页视差必须共用同一个值，否则 push 时两层位移对不上、看着像「底下滑了一半」。
 */
const val NAV_PARALLAX = 0.24f

/**
 * 记住一个 [NavTransitionState]。
 *
 * `initialValue` 只在首次组合时生效：栈被恢复成非空时（旋转 / 进程重建）应已是「被盖住」的 1，不能从 0 起步白播一次进入动画。
 */
@Composable
fun rememberNavTransitionState(initialValue: Float = 0f): NavTransitionState =
    remember { NavTransitionState(initialValue) }

/**
 * 主界面外层容器：按 [NavTransitionState] 施加视差，把子页之间的转场观感补到 main ↔ 首层子页这一段。压暗不在这里做 —— 统一由 `AppNavHost` 的全屏黑幕负责，否则首层会 42% 叠 42%。
 *
 * ⚠ 位移用 `Modifier.layout` 自己 place：`offset` 把位移写进 constraints 导致整棵主界面树每帧 re-measure（pager / 玻璃采样跟着重测必掉帧）；`graphicsLayer { translationX }` 不重测但套离屏 layer，可能干扰页内 `layerBackdrop` 采样。
 */
@Composable
fun MainLayerTransition(
    state: NavTransitionState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 只在「转场结束、主界面回到 0」时递增一次。draw 期订阅的是这个离散值：
    // 转场中间帧它不变 ⇒ 卡片一次都不重绘；动画落到 0 才补一帧重采样。
    val settledEpoch = remember { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        var wasSettled = true
        snapshotFlow { state.value }.collect { v ->
            val settled = v <= 0.001f
            if (settled && !wasSettled) settledEpoch.intValue++
            wasSettled = settled
        }
    }
    // 稳定引用：组合期绝不读快照值（否则整棵主界面随转场每帧重组）
    val redraw: () -> Unit = remember(state) { { settledEpoch.intValue } }
    // 当前整层位移，写在 layout 期、由玻璃在 draw 期读
    val translation = remember { MainLayerTranslation() }
    CompositionLocalProvider(
        LocalMainLayerRedrawSignal provides redraw,
        LocalMainLayerTranslation provides translation,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    // 原样 measure：不把位移写进 constraints，子节点才不会被迫重测
                    val placeable = measurable.measure(constraints)
                    val dx = (-NAV_PARALLAX * state.value * placeable.width).roundToInt()
                    // 供玻璃采样补偿；非快照写，不触发任何失效
                    translation.dxPx = dx.toFloat()
                    layout(placeable.width, placeable.height) {
                        placeable.place(dx, 0)
                    }
                },
        ) { content() }
    }
}
