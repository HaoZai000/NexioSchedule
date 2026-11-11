// Copyright (C) 2026 Haooz · AGPL-3.0-only

package top.yukonga.miuix.kmp.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import top.yukonga.miuix.kmp.utils.OverScrollState

/**
 * 顶栏材质出现的**唯一**阈值：内容滚过 10dp。
 *
 * 全站（首页三页 + 所有二级/三级/四级路由 + 平板三栏 + 分段胶囊 + 搜索框）
 * 必须引用这里，不要再写字面量 `10.dp` / `2f` / `scrollProgress` 之类的第二口径。
 */
val TopBarMaterialThreshold: Dp = 10.dp

/**
 * 本页顶栏的材质状态（由页面壳下发）。
 *
 * 头部组件（搜索框、分段胶囊）挂在顶栏下方，必须读**同一个实例**才能与返回键同帧出现；
 * 各自建一份 `Animatable` 必然差一帧（用户能直接看出来）。
 */
val LocalTopBarMaterial: ProvidableCompositionLocal<TopBarMaterialState?> =
    compositionLocalOf { null }

/** 材质出现的弹簧（唯一来源） */
val TopBarMaterialShowSpec: AnimationSpec<Float> = folmeSpring(damping = 1.0f, response = 0.6f)

/** 材质消失的弹簧（唯一来源） */
val TopBarMaterialHideSpec: AnimationSpec<Float> = folmeSpring(damping = 1.0f, response = 0.4f)

/**
 * 一页顶栏的材质状态。
 *
 * **一个页面只能有一个实例**，`ProgressiveBlurTopBar.maskAlpha`（无材质档的渐变遮罩）、
 * `CollapsibleTopAppBar` 的按钮玻璃 / 阴影、分段胶囊、搜索框全部读它 —— 这样材质
 * 「一起出现、一起消失」，不会出现「按钮亮了但遮罩还在」「遮罩常驻不消失」这类分叉。
 *
 * [alpha] 是绘制期读取的稳定 lambda：逐帧变化只失效绘制，不进组合。
 */
@Stable
class TopBarMaterialState(initiallyVisible: Boolean) {

    internal val animatable = Animatable(if (initiallyVisible) 1f else 0f)

    /** 绘制期读取（`graphicsLayer` / `draw` 阶段）。不要放进组合期分支。 */
    val alpha: () -> Float = { animatable.value }

    /** 当前判定（无动画），供需要在布局期分支的调用方使用 */
    var visible: Boolean = initiallyVisible
        internal set

    internal suspend fun settleTo(visible: Boolean) {
        this.visible = visible
        animatable.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (visible) TopBarMaterialShowSpec else TopBarMaterialHideSpec,
        )
    }
}

/**
 * 判据由调用方给：页面自己有「材质何时出现」的进度时用它。
 *
 * 初值取当前判定 —— 首帧进页面时材质该有就有，不会在滚动位置已经很深的情况下
 * 从 0 弹一次（`AnimatedVisibility` 式的假闪烁）。
 */
@Composable
fun rememberTopBarMaterialState(visible: Boolean): TopBarMaterialState {
    val state = remember { TopBarMaterialState(visible) }
    LaunchedEffect(state, visible) {
        state.settleTo(visible)
    }
    return state
}

/**
 * 材质判据本体（唯一一份）：给累计滚动量与越界量，回答「现在该不该有材质」。
 *
 * 两支：
 * 1. **停在顶部向下回弹**（`overScrollOffset < 0`）：直接算出现。
 * 2. **内容滚过 [TopBarMaterialThreshold]**：`contentOffset` 是累计滚动量，回到顶部才归零。
 *
 * ⚠ `contentOffset` 会被越界位移污染：回弹时 `OverScrollNode` 把 `available` 返给父级，
 * `SharedScrollBehavior.onPostScroll` 照样累加，于是顶部下拉一次就多出一截**正值**，
 * 之后正常下滑要先「还清」这截才够得到阈值 —— 表现为「滚了一点也不出材质」；
 * 反向的污染则让回到顶部后仍留一点负值 —— 「材质不消失」。正数在真实滚动里不可能出现，
 * 直接夹到 0 即可自愈，不用逐页加代理连接（底栏弹窗里的 `proxyConnection` 就是干这个的）。
 */
internal fun isTopBarMaterialVisible(
    contentOffset: Float,
    thresholdPx: Float,
    overScrollOffset: Float,
): Boolean {
    if (contentOffset >= 0f && overScrollOffset < 0f) return true
    return contentOffset.coerceAtMost(0f) < -thresholdPx
}

/**
 * 判据由调用方以 lambda 给：页面已有自己的「是否滚起」信号，但还想吃掉「顶部下拉回弹」时用它
 * （lambda 在 `derivedStateOf` 里求值，逐帧变化不会触发重组）。
 */
@Composable
fun rememberTopBarMaterialState(visibleProvider: () -> Boolean): TopBarMaterialState {
    val visible by remember(visibleProvider) { derivedStateOf { visibleProvider() } }
    return rememberTopBarMaterialState(visible)
}

/**
 * 判据由调用方以**累计滚动量**给：平板左栏这类只有 `scrollY`、没有 `SharedScrollBehavior`
 * 的容器用它（传 `contentOffset` 语义，向下滚为负）。越界量仍读本作用域的
 * `LocalOverScrollState`，判据本体与全站同一份。
 */
@Composable
fun rememberTopBarMaterialState(
    contentOffsetProvider: () -> Float,
    overScrollState: OverScrollState = LocalOverScrollState.current,
): TopBarMaterialState {
    val thresholdPx = with(LocalDensity.current) { TopBarMaterialThreshold.toPx() }
    val visible by remember(contentOffsetProvider, thresholdPx, overScrollState) {
        derivedStateOf {
            isTopBarMaterialVisible(
                contentOffset = contentOffsetProvider(),
                thresholdPx = thresholdPx,
                overScrollOffset = overScrollState.offset,
            )
        }
    }
    return rememberTopBarMaterialState(visible)
}

/**
 * 标准判据：顶栏材质随内容滚动出现。
 *
 * 两支触发条件（与首页、二级页、平板栏共用同一份）：
 * 1. 内容滚过 [TopBarMaterialThreshold]（`contentOffset` 是累计滚动量，回到顶部才归零，
 *    所以触底回弹不会把材质抖掉）；
 * 2. 停在顶部时向下回弹（`overScrollState.offset < 0`）。
 *
 * ⚠ 第 2 支要求顶栏与内容**在同一个 `LocalOverScrollState` 作用域内**：
 * `DocumentPageScaffold` 把 Scaffold 整个包在 provider 里；首页由
 * `CourseScheduleAppMainContent` 提供。若顶栏槽画在 provider 之外，这一支恒为 false
 * （历史 bug：首页下拉回弹从来不出材质）。
 */
@Composable
fun rememberTopBarMaterialState(scrollBehavior: SharedScrollBehavior?): TopBarMaterialState {
    val thresholdPx = with(LocalDensity.current) { TopBarMaterialThreshold.toPx() }
    // 只作身份键：offset 在 derivedStateOf 内部读，本身就会被订阅
    val overScrollState = LocalOverScrollState.current
    val visible by remember(scrollBehavior, thresholdPx, overScrollState) {
        derivedStateOf {
            isTopBarMaterialVisible(
                contentOffset = scrollBehavior?.state?.contentOffset ?: 0f,
                thresholdPx = thresholdPx,
                overScrollOffset = overScrollState.offset,
            )
        }
    }
    return rememberTopBarMaterialState(visible)
}
