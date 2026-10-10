package com.haooz.chedule.ui.navigation

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.haooz.chedule.ui.effects.motion.OobeQuartOutSoftStartEasing
import com.haooz.chedule.ui.effects.motion.SecondaryPageExitEasing
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 子页叠加层：渲染当前叠加路由 + 在有子页时接管系统返回。
 *
 * **转场只有一个进度 `p`**（对齐 1da9c0a8 的 `SecondaryPageTransitionController`）：
 * 栈顶页 `(1-p)×W`、下一层 `-0.24×p×W`、黑幕 `alpha = 0.42×p`。
 * push = 0→1、pop = 1→0，**公式同一套只方向相反** —— 这是打断续播与手势跟手的前提。
 *
 * ⚠ 别改回 `AnimatedContent` 的 `EnterExitTransition`：那是两条独立动画，拿不到中间进度，
 * 既不能半路续播也不能跟手（它 `sizeTransform` 默认非空，还会让退出「缩小并弹出」）。
 *
 * ⚠ 两处必须**同帧**做完，否则会闪：push 起始帧在组合期把 p 归零（`Animatable.snapTo`
 * 是 suspend，赶不上当帧绘制，新页会先在最终位置闪一帧）；pop 结束时清 `outgoing`
 * 与 p 拉回 1 也要同帧，否则新栈顶会按 `(1-0)×W` 被摆到屏外。
 *
 * ⚠ 必须用 `SaveableStateHolder`：拆 Activity 时子页是独立 Activity（父 Activity 只 stop），
 * 合并成单宿主后，离开组合的页面必须显式托管，否则返回后滚动位置/折叠进度全丢。
 * 已知取舍：出栈不清状态，重开同一页会回到上次位置（文档页可接受）。
 *
 * 栈空时 `isBackEnabled` 变 false，返回键交还 Android 宿主（MainActivity 的
 * 预测性返回 / 隐藏后台）—— 这是 [AppRouter] 栈从空开始、主界面不参与路由的原因。
 */
@Composable
fun AppNavHost(
    router: AppRouter,
    /** 主界面（常驻底座）的首层转场进度：它不在这个转场容器里，视差只能由宿主施加。 */
    mainTransition: NavTransitionState,
    modifier: Modifier = Modifier,
    content: @Composable (AppRoute) -> Unit,
) {
    // key 用 route.id：同一路由压两次会共用状态，当前子页互不跳自己，将来有自跳再改
    val stateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()

    val progress = rememberNavTransitionState(initialValue = 1f)
    // pop 期间仍在屏幕上的那一层：已出栈，但要跟着把退出动画播完
    var outgoing by remember { mutableStateOf<AppRoute?>(null) }

    // 转场起点必须在组合期对齐：LaunchedEffect 晚一帧，那一帧会闪
    var lastRoute by remember { mutableStateOf(router.current) }
    if (lastRoute !== router.current) {
        lastRoute = router.current
        when (router.lastDirection) {
            NavDirection.Push -> progress.snapTo(0f)
            // pop 起点就是 1，不用动 p，只要留住被弹出的那层
            NavDirection.Pop -> outgoing = router.lastPopped
        }
    }

    // 预测性返回：跟手逐帧落值；松手完成 → 出栈（剩余距离交给下面的动画续播）；取消 → 弹回
    val navEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = router.hasOverlay,
        onBackCancelled = {
            val from = progress.value.coerceIn(0f, 1f)
            val (dur, easing) = settleOnGlobalCurve(
                fromProgress = from,
                toProgress = 1f,
                fullEasing = NAV_ENTER_EASING,
                totalDuration = NAV_ENTER_DURATION,
                invertGlobal = false,
                minDuration = MIN_GESTURE_RESTORE_MS,
            )
            scope.launch {
                launch { progress.animateTo(1f, tween(dur, easing = easing)) }
                // 首层返回时主界面也在跟手，一起弹回
                if (router.depth == 1) {
                    launch { mainTransition.animateTo(1f, tween(dur, easing = easing)) }
                }
            }
        },
        onBackCompleted = { router.popBack() },
    )
    LaunchedEffect(Unit) {
        snapshotFlow { navEventState.transitionState }.collect { transitionState ->
            if (
                transitionState is NavigationEventTransitionState.InProgress &&
                transitionState.direction == NavigationEventTransitionState.TRANSITIONING_BACK &&
                PredictiveBackSettings.enabled
            ) {
                val p = (1f - transitionState.latestEvent.progress).coerceIn(0f, 1f)
                progress.snapTo(p)
                // 栈里只剩一层时，被推的是主界面
                if (router.depth == 1) mainTransition.snapTo(p)
            }
        }
    }

    LaunchedEffect(router.current) {
        if (router.lastDirection == NavDirection.Pop) {
            // 从当前值续播到 0：可能是手势松手的剩余距离，也可能是被打断的位置
            val from = progress.value.coerceIn(0f, 1f)
            val (dur, easing) = settleOnGlobalCurve(
                fromProgress = from,
                toProgress = 0f,
                fullEasing = NAV_EXIT_EASING,
                totalDuration = NAV_EXIT_DURATION,
                invertGlobal = true,
                minDuration = MIN_GESTURE_SETTLE_MS,
            )
            coroutineScope {
                launch { progress.animateTo(0f, tween(dur, easing = easing)) }
                if (router.depth == 0) {
                    launch { mainTransition.animateTo(0f, tween(dur, easing = easing)) }
                }
            }
            // 同帧收尾：只清 outgoing 而 p 停在 0 的话，新栈顶会被摆到屏外
            outgoing = null
            progress.snapTo(1f)
        } else {
            val from = progress.value.coerceIn(0f, 1f)
            // minDuration = 0：纯续播
            val (dur, easing) = settleOnGlobalCurve(
                fromProgress = from,
                toProgress = 1f,
                fullEasing = NAV_ENTER_EASING,
                totalDuration = NAV_ENTER_DURATION,
                invertGlobal = false,
                minDuration = 0,
            )
            coroutineScope {
                launch { progress.animateTo(1f, tween(dur, easing = easing)) }
                // 首层 push：被推的是主界面（常驻底座），视差由宿主施加
                if (router.depth == 1) {
                    launch { mainTransition.animateTo(1f, tween(dur, easing = easing)) }
                }
            }
        }
    }

    val topRoute = outgoing ?: router.current
    // pop 期间「下一层」是新的栈顶；push / 稳定态才是栈顶下面那层
    val underRoute = if (outgoing != null) router.current else router.underTop

    Box(modifier.fillMaxSize()) {
        if (underRoute != null) {
            PageLayer(fraction = { -NAV_PARALLAX * progress.value }) {
                stateHolder.SaveableStateProvider(underRoute.id) { content(underRoute) }
            }
        }

        // 黑幕：夹在下层与上层之间，随 p 渐暗。
        // ⚠ 显隐不能挂「转场进行中」这种组合期状态：跟手期间没有动画在跑（纯逐帧落值），
        // 那样手势全程没有压暗。常驻挂载，只在 draw 阶段跳过稳定态。
        if (router.hasOverlay || outgoing != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val p = progress.value
                        val a = NAV_BG_DIM * p
                        if (a > 0.001f && p < 0.999f) drawRect(Color.Black.copy(alpha = a))
                    },
            )
        }

        if (topRoute != null) {
            PageLayer(fraction = { 1f - progress.value }) {
                stateHolder.SaveableStateProvider(topRoute.id) { content(topRoute) }
            }
        }
    }
}

/**
 * 按「占自身宽度的比例」横向摆位。
 *
 * ⚠ 用 `Modifier.layout` 自己 place：`offset` 会把位移写进 constraints，子节点每帧真正
 * re-measure（整页列表/pager/玻璃采样跟着重测，必掉帧）；`graphicsLayer { translationX }`
 * 不重测但会给整页套离屏 layer，可能干扰页内 `layerBackdrop` 采样。
 */
@Composable
private fun PageLayer(
    fraction: () -> Float,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place((fraction() * placeable.width).roundToInt(), 0)
                }
            },
    ) { content() }
}

/**
 * 在开/关曲线上取从 [fromProgress] 到 [toProgress] 的**剩余段**（搬自 1da9c0a8）。
 * 用于打断续播与手势吸附：半路接手时速度连续，而不是从头按一条新曲线重播。
 *
 * - [invertGlobal] = false：全局进度 = `fullEasing(u)`（入场 0→1）
 * - [invertGlobal] = true：全局进度 = `1 - fullEasing(u)`（出场 1→0）
 *
 * @return (剩余时长, 把该尾段映射到 0..1 的局部 easing)
 */
private fun settleOnGlobalCurve(
    fromProgress: Float,
    toProgress: Float,
    fullEasing: Easing,
    totalDuration: Int,
    invertGlobal: Boolean,
    minDuration: Int,
): Pair<Int, Easing> {
    val from = fromProgress.coerceIn(0f, 1f)
    val to = toProgress.coerceIn(0f, 1f)
    val span = to - from
    if (kotlin.math.abs(span) < 1e-4f) {
        return minDuration.coerceAtMost(totalDuration) to fullEasing
    }

    // 全局进度 = from 时，fullEasing 的参数 u0
    val eAtFrom = if (invertGlobal) 1f - from else from
    val u0 = inverseEasingTime(fullEasing, eAtFrom)
    val durationMs = ((1f - u0) * totalDuration).toInt().coerceIn(minDuration, totalDuration)

    val local = Easing { fraction ->
        val u = u0 + (1f - u0) * fraction.coerceIn(0f, 1f)
        val e = fullEasing.transform(u)
        val global = if (invertGlobal) 1f - e else e
        ((global - from) / span).coerceIn(0f, 1f)
    }
    return durationMs to local
}

/** 二分求 `easing(u) = progress` 的 u ∈ [0,1] */
private fun inverseEasingTime(easing: Easing, progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0f) return 0f
    if (p >= 1f) return 1f
    var lo = 0f
    var hi = 1f
    repeat(18) {
        val mid = (lo + hi) * 0.5f
        if (easing.transform(mid) < p) lo = mid else hi = mid
    }
    return (lo + hi) * 0.5f
}

/** 还原 1da9c0a8 二级页转场参数 */
private const val NAV_ENTER_DURATION = 600
private const val NAV_EXIT_DURATION = 320
// 这两个也被 [MainLayerTransition] 用（主界面那半边要和下层页用同一套视差/压暗）
internal const val NAV_PARALLAX = 0.24f
internal const val NAV_BG_DIM = 0.42f

/** 入场：更晚进入减速，尾段拖得更长更慢 */
private val NAV_ENTER_EASING = OobeQuartOutSoftStartEasing
/** 退出：起步更缓，后段正常滑出 */
private val NAV_EXIT_EASING = SecondaryPageExitEasing

private const val MIN_GESTURE_SETTLE_MS = 100
private const val MIN_GESTURE_RESTORE_MS = 200
