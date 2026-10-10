package com.haooz.chedule.ui.navigation

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.WindowManager
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.haooz.chedule.ui.effects.motion.OobeQuartOutSoftStartEasing
import com.haooz.chedule.ui.effects.motion.SecondaryPageExitEasing
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 子页叠加层：**整栈常驻渲染** + 在有子页时接管系统返回。
 *
 * **整栈常驻**（2026-10-10 起，对齐 Activity back stack 的实例语义）：
 * 栈内每一层都保持组合，任意深度 pop 回去都是活页而非重建；
 * 静止时按三档暂停绘制（栈顶常画 / 直接下层只在转场·手势时画 / 更深层永不画），
 * 等价 stop 的 Activity：实例全活着、窗口不画。
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
 * ⚠ 仍必须用 `SaveableStateHolder`：整栈常驻只覆盖**在栈期间**；出栈的页面组合销毁，
 * 滚动位置/折叠进度由 holder 按 route.id 托管，重开同一页时恢复。
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

    // pop 期间仍在屏幕上的那一层已出栈，由 outgoing 单独渲染；栈内其余层走下面的整栈循环
    val routes = router.routes
    val lastIndex = routes.lastIndex
    // 直接下层：push/稳定态是栈顶下面那层；pop 期间是被揭开的层（即当前栈顶）
    val underIndex = if (outgoing != null) lastIndex else lastIndex - 1

    // 屏幕圆角：转场中间态把上层裁成圆角，让它像一张卡片从右边推进来。
    // 只对上层做 —— 下层是被揭开的那一层，不该有轮廓。
    val density = LocalDensity.current
    val cornerRadiusPx = rememberScreenCornerRadiusPx()
    val screenClipShape = remember(cornerRadiusPx, density) {
        if (cornerRadiusPx > 0f) {
            with(density) { ContinuousRoundedRectangle(cornerRadiusPx.toDp()) }
        } else {
            null
        }
    }
    val inFlight: () -> Boolean = {
        val p = progress.value
        p > 0f && p < 1f
    }

    Box(modifier.fillMaxSize()) {
        // ── 整栈常驻（2026-10-10 起）：栈内每一层都保持组合，像 Activity back stack
        // 实例全活着 —— 任意深度 pop 回去都是活页，不是重建。
        // 绘制三档（等价 stop 的 Activity 不画窗口）：
        //   栈顶     → 常画
        //   直接下层 → 只在转场/手势进行中（0<p<1）画 —— 预测性返回露出的是活页
        //   更深层   → 永远不画（任何 p 下都被完全遮住），只保留组合与状态
        for (index in routes.indices) {
            val route = routes[index]
            val isTop = outgoing == null && index == lastIndex
            val isDirectUnder = index == underIndex
            val fraction: () -> Float = when {
                isTop -> ({ 1f - progress.value })
                isDirectUnder -> ({ -NAV_PARALLAX * progress.value })
                // 更深层固定停靠位：p 永远不影响它们
                else -> ({ -NAV_PARALLAX })
            }
            val paused: () -> Boolean = when {
                isTop -> ({ false })
                isDirectUnder -> ({ !inFlight() })
                else -> ({ true })
            }
            PageLayer(
                fraction = fraction,
                paused = paused,
                clipShape = if (isTop) screenClipShape else null,
                inFlight = inFlight,
            ) {
                stateHolder.SaveableStateProvider(route.id) { content(route) }
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

        // pop 期间：被弹出的那层（已出栈）仍在屏上，把退出动画播完
        val outgoingRoute = outgoing
        if (outgoingRoute != null) {
            PageLayer(
                fraction = { 1f - progress.value },
                paused = { false },
                clipShape = screenClipShape,
                inFlight = inFlight,
            ) {
                stateHolder.SaveableStateProvider(outgoingRoute.id) { content(outgoingRoute) }
            }
        }
    }
}

/** 屏幕圆角半径（px）；拿不到时为 0，此时不做裁切。多窗口/自由窗口用固定 20dp。 */
@Composable
private fun rememberScreenCornerRadiusPx(): Float {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        try {
            if ((context as? Activity)?.isInMultiWindowMode == true) {
                20f * density.density
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.currentWindowMetrics?.windowInsets
                    ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
                    ?: 0f
            } else {
                0f
            }
        } catch (_: Exception) {
            0f
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
    paused: () -> Boolean = { false },
    clipShape: Shape? = null,
    inFlight: () -> Boolean = { false },
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            // 暂停绘制：层还在组合里（状态、协程全活着），只是不进渲染管线 ——
            // 等价 stop 的 Activity 不画窗口。放最外层：暂停时连内层 clip 都跳过。
            .skipDrawWhen(paused)
            // ⚠ 顺序关键：clip 必须在 layout **之后**（更内层）。
            // 位移是 layout 在「摆放阶段」做的，clip 在内层时它的坐标系跟着
            // 页面一起平移 —— 圆角长在页面自己的角上，整个滑动过程都是圆角卡片
            //（与 1da9c0a8 的语义一致：那版 translationX 与 clip 在同一个
            // graphicsLayer 里，裁切天然跟随图层）。
            // 若把 clip 放到 layout 外层，裁切就锚在屏幕四角 —— 页面的角只有
            // 滑到贴边的瞬间才与屏幕角重合，看起来等于没生效。
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place((fraction() * placeable.width).roundToInt(), 0)
                }
            }
            .clipDuringTransition(clipShape, inFlight),
    ) { content() }
}

/**
 * 转场中间态才把页面裁成屏幕圆角，到位后恢复直角 —— 系统窗口本身已有圆角，
 * Compose 再裁一次会在角上叠出双圆角（1da9c0a8 的 `applyScreenClipDuringTransition`）。
 *
 * ⚠ 在 draw 阶段自己 `clipPath`，**不用** `graphicsLayer { clip = true }`：
 * 后者会给整页套一层离屏 layer，可能干扰页内的 `layerBackdrop` 采样。
 */
private fun Modifier.clipDuringTransition(shape: Shape?, inFlight: () -> Boolean): Modifier =
    if (shape == null) this else this then TransitionClipElement(shape, inFlight)

/**
 * 暂停绘制（「整栈常驻」策略的一半，另一半是栈内每层都保持组合）。
 *
 * [paused] 在 draw 阶段读 —— 恢复绘制时由 draw 阶段的快照观察自动失效重画；
 * [SkipDrawElement.update] 里显式 `invalidateDraw()`，覆盖「层角色变化但无进度
 * 变化」的场景：pop 刚结束时某层从「更深层」升为「栈顶」，paused lambda 整个
 * 换了引用但没有任何进度帧在跑，不显式失效就没人叫它重画。
 */
private fun Modifier.skipDrawWhen(paused: () -> Boolean): Modifier =
    this then SkipDrawElement(paused)

private data class SkipDrawElement(
    private val paused: () -> Boolean,
) : ModifierNodeElement<SkipDrawNode>() {
    override fun create() = SkipDrawNode(paused)
    override fun update(node: SkipDrawNode) {
        node.paused = paused
        node.invalidateDraw()
    }
}

private class SkipDrawNode(
    var paused: () -> Boolean,
) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        if (!paused()) drawContent()
        // paused 时什么都不画：组合、状态、协程全部保留，只是不进渲染
    }
}

private data class TransitionClipElement(
    private val shape: Shape,
    private val inFlight: () -> Boolean,
) : ModifierNodeElement<TransitionClipNode>() {
    override fun create() = TransitionClipNode(shape, inFlight)
    override fun update(node: TransitionClipNode) {
        node.shape = shape
        node.inFlight = inFlight
    }
}

private class TransitionClipNode(
    var shape: Shape,
    var inFlight: () -> Boolean,
) : Modifier.Node(), DrawModifierNode {
    private val path = Path()

    // inFlight() 在这里读进度 → 只 invalidate draw，不会触发重组
    override fun ContentDrawScope.draw() {
        if (!inFlight() || size.isEmpty()) {
            drawContent()
            return
        }
        path.rewind()
        path.addOutline(shape.createOutline(size, layoutDirection, this))
        // clipPath 的 block receiver 是 DrawScope，drawContent() 得指名外层
        clipPath(path) { this@draw.drawContent() }
    }
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
