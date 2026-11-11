package com.haooz.chedule.ui.navigation

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalDensity
import com.haooz.chedule.ui.effects.motion.OobeQuartOutSoftStartEasing
import com.haooz.chedule.ui.effects.motion.SecondaryPageExitEasing
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.ui.utils.clipDuringTransition
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.toShape
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.rememberNavigationBack

/**
 * 子页叠加层：整栈常驻组合，用一个进度驱动栈顶滑入滑出。
 *
 * 栈内各层始终保持组合，pop 回去是活页而不是重建。转场只有进度 `p`：栈顶 `(1-p)×W`、
 * 直接下层 `-NAV_PARALLAX×p×W`、黑幕 `NAV_BG_DIM×p`；push 0→1 与 pop 1→0 共用同一公式，
 * 打断续播与手势跟手都依赖这一点。
 *
 * 返回手势走 `:miuix` 的 [rememberNavigationBack]（Android 实现基于 navigationevent，skiko 为空实现）。
 *
 * @param mainTransition 主界面（常驻底座）的首层进度；它不在此容器内，视差只能由宿主施加。
 * @param transitionState 外置的栈顶进度持有者，缺省内部自建。鸿蒙宿主用它逐帧读取进度。
 */
@Composable
fun AppNavHost(
    router: AppRouter,
    mainTransition: NavTransitionState,
    modifier: Modifier = Modifier,
    onOverlayActivityChanged: ((Boolean) -> Unit)? = null,
    transitionState: NavTransitionState? = null,
    content: @Composable (layerKey: String, AppRoute) -> Unit,
) {
    val stateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val progress = transitionState ?: rememberNavTransitionState(initialValue = 1f)

    // 已出栈、但还要把退场动画播完的那一层。key 随路由一起存，销毁时栈可能已经又变过。
    var outgoing by remember { mutableStateOf<ExitingLayer?>(null) }

    // 必须在 settleExit 之前取：收尾分支要读它判断手势是否仍在进行。
    val back = rememberNavigationBack()

    /** 出场结束：同帧摘掉这一层、销掉它的 saveable 状态、把 p 拉回 1。 */
    fun destroyExiting(exiting: ExitingLayer) {
        outgoing = null
        // 只清 outgoing 而 p 停在 0，新栈顶会被摆到屏外
        progress.snapTo(1f)
        // 先 removeState 再摘 outgoing：provider 要到 onDispose 才把 rememberSaveable 写回
        // holder，顺序反了状态会残留。
        stateHolder.removeState(exiting.key)
    }

    /** 出场收尾：按当前进度续播到 0，完整播完才 [destroyExiting]。 */
    suspend fun settleExit() {
        val exiting = outgoing ?: return
        val (dur, easing) = settleOnGlobalCurve(
            fromProgress = progress.value,
            toProgress = 0f,
            fullEasing = NAV_EXIT_EASING,
            totalDuration = NAV_EXIT_DURATION,
            invertGlobal = true,
            minDuration = MIN_GESTURE_SETTLE_MS,
        )
        val finished = coroutineScope {
            val exit = async { progress.animateTo(0f, tween(dur, easing = easing)) }
            // 首层 pop：被揭开的是主界面，视差由宿主施加
            if (router.depth == 0) launch { mainTransition.animateTo(0f, tween(dur, easing = easing)) }
            exit.await()
        }
        // 本次收尾不随导航取消：播到一半又 push 了新页时它还在跑，此时再动手会掐掉新页入场。
        if (outgoing !== exiting) return
        if (finished) {
            destroyExiting(exiting)
        } else if (progress.value <= 0f && !back.isTransitioningBack()) {
            // 被打断时 p 已归手势驱动，不能直接收尾；若手势早已结束且页面彻底滑出屏幕，
            // 说明这次收尾被整个吞掉，不补掉这一层会永远挂在渲染列表上。
            destroyExiting(exiting)
        }
    }

    // ── 导航同步（必须在组合期对齐：LaunchedEffect 晚一帧，那一帧会闪）──────────────
    var lastRoute by remember { mutableStateOf(router.current) }
    // 上次同步时的整栈快照：一帧内退多层时，被跳过的层不会进任何渲染列表，
    // 只能靠它算出这些层各自该销的 key。
    var renderedRoutes by remember { mutableStateOf(router.routes.toList()) }
    if (lastRoute !== router.current) {
        lastRoute = router.current
        val before = renderedRoutes
        renderedRoutes = router.routes.toList()
        when (router.lastDirection) {
            NavDirection.Push -> {
                val interrupted = outgoing
                // 必须同帧摘掉 outgoing：出场没播完又点进别的页时，那层会继续盖在新页上。
                outgoing = null
                interrupted?.let { stateHolder.removeState(it.key) }
                // 同一 route 被打断时从当前进度续播，换了 route 才归零推入。
                if (interrupted?.route?.id != router.current?.id) progress.snapTo(0f)
            }
            // pop 起点就是 1，不用动 p，只要留住被弹出的那层
            NavDirection.Pop -> {
                // 出栈只表示移出栈、不销毁：栈顶那层还要播完退场，状态由 settleExit 销。
                // 上一次还没播完就又弹一层时，旧的那层直接掉出渲染列表，得在这里销。
                outgoing?.let { stateHolder.removeState(it.key) }
                // 一帧内退多层时，被跳过的层永远不会进 outgoing，也不在这里销，
                // 就会一直留在 holder 里 —— 下次进同一页会恢复成旧状态。
                // key 按出栈前的栈算，与它们在栈内时逐字相同。
                val removedKeys = if (before.size > router.routes.size) {
                    layerKeys(before, null).first.drop(router.routes.size)
                } else {
                    emptyList()
                }
                removedKeys.dropLast(1).forEach { stateHolder.removeState(it) }
                outgoing = router.lastPopped?.let { popped ->
                    ExitingLayer(
                        popped,
                        removedKeys.lastOrNull() ?: poppedLayerKey(router.routes + popped),
                    )
                }
            }
        }
    }

    // ── 预测性返回 ─────────────────────────────────────────────────────────────
    // handler 与 backProgress 必须取自同一个 rememberNavigationBack：拆成两处各自
    // remember 会编译通过但跟手动画永远不动（miuix fork 踩过）。
    back.handler(
        enabled = router.hasOverlay,
        onBackCompleted = { router.popBack() },
        onBackCancelled = {
            if (outgoing != null) {
                // 手势打断的是一次没收完的出场：回弹 = 把那次出场播完，
                // 否则已出栈的那层会被重新拉回全屏盖住新栈顶。
                scope.launch { settleExit() }
            } else {
                val (dur, easing) = enterSettle(progress.value, MIN_GESTURE_RESTORE_MS)
                scope.launch { playEnter(progress, mainTransition, router.depth, dur, easing) }
            }
        },
    )

    // backProgress 手势期间为 0..1、非手势期间为 null（skiko 恒为 null）。
    LaunchedEffect(Unit) {
        // 必须按手势起点成比例回落，不能写成 `p = 1 - 手势进度`：入场没播完就滑返回
        // （p 还在 0.4）时，第一帧手势进度≈0 会把 p 拉到 1，页面先跳到全屏再跟手。
        var gestureBase = 1f
        var mainGestureBase = 1f
        var sawGesture = false
        snapshotFlow { back.backProgress() }.collect { gestureProgress ->
            if (gestureProgress == null) {
                // 手势打断的若是出场收尾、而页面当时已彻底滑出屏幕，那次收尾没人接手，
                // 这里补一次，否则那一层永远挂在渲染列表上。
                if (sawGesture && outgoing != null && progress.value <= 0f) {
                    scope.launch { settleExit() }
                }
                sawGesture = false
                return@collect
            }
            if (!PredictiveBackSettings.enabled) return@collect
            if (!sawGesture) {
                sawGesture = true
                gestureBase = progress.value.coerceIn(0f, 1f)
                mainGestureBase = mainTransition.value.coerceIn(0f, 1f)
            }
            val remain = (1f - gestureProgress).coerceIn(0f, 1f)
            // snapTo 会挤掉仍在跑的转场，否则被打断的动画下一帧就把手势写入的值盖回去。
            progress.snapTo(gestureBase * remain)
            if (router.depth == 1) mainTransition.snapTo(mainGestureBase * remain)
        }
    }

    // ── 转场驱动：路由一变就起一段动画，被打断则从当前进度续播 ──────────────────────
    LaunchedEffect(router.current) {
        if (router.lastDirection == NavDirection.Pop) {
            settleExit()
        } else {
            val (dur, easing) = enterSettle(progress.value, minDuration = 0)
            playEnter(progress, mainTransition, router.depth, dur, easing)
        }
    }

    NavLayerStack(
        routes = router.routes,
        exiting = outgoing,
        progress = progress,
        stateHolder = stateHolder,
        modifier = modifier.fillMaxSize(),
        onOverlayActivityChanged = onOverlayActivityChanged,
        content = content,
    )
}

/** 入场收尾：按当前进度续播到 1；跨过首层时同步推主界面（常驻底座的视差只能由宿主施加）。 */
private suspend fun playEnter(
    progress: NavTransitionState,
    mainTransition: NavTransitionState,
    depth: Int,
    dur: Int,
    easing: Easing,
) = coroutineScope {
    launch { progress.animateTo(1f, tween(dur, easing = easing)) }
    if (depth == 1) launch { mainTransition.animateTo(1f, tween(dur, easing = easing)) }
}

/** 入场段：从 [from] 续播到 1 需要的剩余时长与局部 easing。 */
private fun enterSettle(from: Float, minDuration: Int): Pair<Int, Easing> =
    settleOnGlobalCurve(
        fromProgress = from,
        toProgress = 1f,
        fullEasing = NAV_ENTER_EASING,
        totalDuration = NAV_ENTER_DURATION,
        invertGlobal = false,
        minDuration = minDuration,
    )

/**
 * 整栈渲染 + 三档绘制：栈顶常画、直接下层在 `p<1` 时画、更深层只留组合不画。
 *
 * 所有层走同一个循环，组合槽位只由下标决定。若把栈顶单独放在循环外，push 时它会换槽位
 * = 新挂一份，新槽位的 `SaveableStateRegistry` 还是空的，`rememberSaveable` 全部重建。
 * 栈只在尾部增删，下标天然稳定。
 */
@Composable
private fun NavLayerStack(
    routes: List<AppRoute>,
    exiting: ExitingLayer?,
    progress: NavTransitionState,
    stateHolder: SaveableStateHolder,
    modifier: Modifier = Modifier,
    onOverlayActivityChanged: ((Boolean) -> Unit)? = null,
    content: @Composable (layerKey: String, AppRoute) -> Unit,
) {
    val (stackKeys, exitKey) = layerKeys(routes, exiting)

    // 栈内各层，加上（pop 期间）正在播退场的那层；后者排在最后 = 画在最上面
    val layers: List<AppRoute> = if (exiting != null) routes + exiting.route else routes
    val renderKeys: List<String> = if (exitKey != null) stackKeys + exitKey else stackKeys
    // 出场动画跑着时也拦截原生输入，直到这一层彻底画完
    SideEffect { onOverlayActivityChanged?.invoke(layers.isNotEmpty()) }

    // 转场中间态把栈顶裁成圆角，像一张卡片从右侧推入；下层是被揭开的，不该有轮廓。
    val cornerRadiusPx = rememberScreenCornerRadiiPx()
    val density = LocalDensity.current
    val clipShape = remember(cornerRadiusPx, density) {
        if (cornerRadiusPx.maxRadius > 0f) cornerRadiusPx.toShape(density) else null
    }
    val inFlight: () -> Boolean = { progress.value > 0f && progress.value < 1f }
    // 黑幕只压暗被盖住的内容，栈顶自身不压暗；p 到位就不画，省一次全屏 alpha 混合。
    val scrimAlpha: () -> Float = {
        if (progress.value < 0.999f) NAV_BG_DIM * progress.value else 0f
    }

    Box(modifier) {
        val topIndex = layers.lastIndex
        for (index in layers.indices) {
            val isTop = index == topIndex
            val isDirectUnder = index == topIndex - 1
            val fraction: () -> Float = when {
                isTop -> ({ 1f - progress.value })
                isDirectUnder -> ({ -NAV_PARALLAX * progress.value })
                else -> ({ -NAV_PARALLAX }) // 更深层固定停靠，不受 p 影响
            }
            // 直接下层不能用「0<p<1」判：push 起始帧 p 恰好为 0、栈顶还在屏外，
            // 那一帧下层必须画，否则会闪出主界面。
            val paused: () -> Boolean = when {
                isTop -> ({ false })
                isDirectUnder -> ({ progress.value >= 1f })
                else -> ({ true })
            }
            PageLayer(
                fraction = fraction,
                paused = paused,
                clipShape = if (isTop) clipShape else null,
                scrimAlpha = if (isTop) scrimAlpha else null,
                inFlight = inFlight,
            ) {
                stateHolder.SaveableStateProvider(renderKeys[index]) {
                    content(renderKeys[index], layers[index])
                }
            }
        }
    }
}

/**
 * 正在播退场的那一层：路由 + 它在栈里时使用的 [SaveableStateHolder] key。
 *
 * key 必须随路由一起存：销毁可能发生在栈又变过之后（退场途中又 push / 又 pop），
 * 那时按栈重算会算到别的层上，`removeState` 销错层。
 */
internal class ExitingLayer(val route: AppRoute, val key: String)

/**
 * 给「这一帧要渲染的每一层」分配 [SaveableStateHolder] 的 key。
 *
 * 栈内重复路由首次用裸 id、其后 `#2`/`#3`：裸 id 才能让重开同一页恢复状态，
 * 而同帧出现两个相同 key 会抛 `IllegalArgumentException` 整屏崩（连点两次同一入口即可触发）。
 *
 * 边分配边占坑，不能预先把整个栈的 id 塞进 used —— 那样裸 id 一个都发不出去，
 * 与事后按栈复算的 key 对不上，`removeState` 会静默落空。
 * 也不能包进 remember：`routes` 是 [androidx.compose.runtime.snapshots.SnapshotStateList]，
 * 未重写 equals，`Composer.changed` 会判「没变」而让 key 与实际栈脱节。
 */
internal fun layerKeys(routes: List<AppRoute>, exiting: ExitingLayer?): Pair<List<String>, String?> {
    val used = HashSet<String>(routes.size + 1)
    val stackKeys = routes.map { route ->
        var candidate = route.id
        var n = 2
        while (!used.add(candidate)) candidate = "${route.id}#${n++}"
        candidate
    }
    return stackKeys to exiting?.key
}

/** 出栈那一层在栈里时使用的 key（= 出栈前那一帧栈最后一个元素分配到的 key）。 */
internal fun poppedLayerKey(stackBeforePop: List<AppRoute>): String =
    layerKeys(stackBeforePop, null).first.last()

/**
 * 按「占自身宽度的比例」横向摆位的一层。
 *
 * 用 [Modifier.layout] 自行 place：`offset` 会把位移写进 constraints，子节点每帧真正
 * re-measure（整页列表、pager、玻璃采样全跟着重测，必掉帧）；`graphicsLayer` 虽不重测，
 * 但会套一层离屏 layer，可能干扰页内 `layerBackdrop` 采样。
 *
 * 黑幕与圆角分居 `layout` 两侧：黑幕在外层，内层坐标系跟着页面平移，放里面会跟着滑走；
 * 圆角在内层，才长在页面自己的角上，放外层则锚在屏幕四角，等于没生效。
 */
@Composable
private fun PageLayer(
    fraction: () -> Float,
    paused: () -> Boolean,
    clipShape: Shape? = null,
    /** 非 null 时在本层内容之下画一层全屏黑幕，只给栈顶传。 */
    scrimAlpha: (() -> Float)? = null,
    inFlight: () -> Boolean = { false },
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            // 暂停绘制：层仍在组合里，状态与协程都活着，只是不进渲染。放最外层，
            // 暂停时连内层裁切都跳过。
            .skipDrawWhen(paused)
            .then(
                if (scrimAlpha == null) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        val a = scrimAlpha()
                        if (a > 0.001f) drawRect(Color.Black.copy(alpha = a))
                    }
                },
            )
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
 * 暂停绘制：`paused` 在 draw 阶段读取，恢复时由快照观察自动失效重画。
 *
 * [SkipDrawElement.update] 里必须显式 `invalidateDraw()`：pop 刚结束时某层从更深层升为栈顶，
 * paused lambda 换了引用但没有任何进度帧在跑，不显式失效就没人重画。
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
    }
}

/**
 * 在开/关曲线上截取 [fromProgress] 到 [toProgress] 的剩余段，供打断续播与手势吸附使用：
 * 半路接手时速度连续，而不是按一条新曲线从头重播。
 *
 * - [invertGlobal] = false：全局进度 = `fullEasing(u)`（入场 0→1）
 * - [invertGlobal] = true：全局进度 = `1 - fullEasing(u)`（出场 1→0）
 *
 * @return 剩余时长与该尾段映射到 0..1 的局部 easing
 */
internal fun settleOnGlobalCurve(
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
    if (abs(span) < 1e-4f) {
        return minDuration.coerceAtMost(totalDuration) to fullEasing
    }

    // 全局进度等于 from 时，fullEasing 的参数 u0
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

/** 二分求 `easing(u) = progress` 的 `u ∈ [0,1]`。 */
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

internal const val NAV_ENTER_DURATION = 600
internal const val NAV_EXIT_DURATION = 320
internal const val NAV_BG_DIM = 0.42f

/** 入场：更晚进入减速，尾段拖得更长更慢。 */
internal val NAV_ENTER_EASING = OobeQuartOutSoftStartEasing

/** 出场：起步更缓，后段正常滑出。 */
internal val NAV_EXIT_EASING = SecondaryPageExitEasing

/** 手势吸附的最短时长：剩余距离太小时也播一小段，否则看着像卡住没反应。 */
private const val MIN_GESTURE_SETTLE_MS = 100
private const val MIN_GESTURE_RESTORE_MS = 200
