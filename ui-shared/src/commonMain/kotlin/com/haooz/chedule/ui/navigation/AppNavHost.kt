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
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.rememberNavigationBack

/**
 * 子页叠加层：整栈常驻渲染 + 在有子页时接管系统返回。
 *
 * 栈内每层都保持组合，pop 回去是活页而非重建；静止时按三档暂停绘制，等价 stop 的 Activity。转场只有一个进度 `p`：栈顶 `(1-p)×W`、下层 `-NAV_PARALLAX×p×W`、黑幕 `NAV_BG_DIM×p`；push 0→1 / pop 1→0 共用同一公式，这是打断续播与手势跟手的前提。
 *
 * 返回手势走 `:miuix` 的 `rememberNavigationBack()`（Android 包 navigationevent，skiko 空实现）。
 */
@Composable
fun AppNavHost(
    router: AppRouter,
    /** 主界面（常驻底座）的首层转场进度：它不在这个转场容器里，视差只能由宿主施加。 */
    mainTransition: NavTransitionState,
    modifier: Modifier = Modifier,
    onOverlayActivityChanged: ((Boolean) -> Unit)? = null,
    /**
     * 外置的栈顶进度持有者。不传 = 内部自建（默认）。
     * 鸿蒙宿主传入，以便在组合外逐帧读进度、驱动原生顶栏与页面同步滑移。
     */
    transitionState: NavTransitionState? = null,
    content: @Composable (layerKey: String, AppRoute) -> Unit,
) {
    val stateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()

    val progress = transitionState ?: rememberNavTransitionState(initialValue = 1f)
    // pop 期间仍在屏幕上的那一层：已出栈，但要跟着把退出动画播完。
    // 连 [SaveableStateHolder] key 一起存 —— 销毁时栈可能又变过，重算会算到别的层上。
    var outgoing by remember { mutableStateOf<ExitingLayer?>(null) }

    // ⚠ 必须在 settleExit 之前取：收尾分支要读它判断「手势是不是还在进行中」。
    val back = rememberNavigationBack()

    /** 出场彻底结束：同帧摘掉这一层 + 销毁它的 saveable 状态 + 把 p 拉回 1。 */
    fun destroyExiting(exiting: ExitingLayer) {
        // 同帧收尾：只清 outgoing 而 p 停在 0 的话，新栈顶会被摆到屏外
        outgoing = null
        progress.snapTo(1f)
        // 趁这一层还在组合里销状态：holder 是在 onDispose 时才把 rememberSaveable 存进
        // savedStates 的，先摘掉 registry 那一步就不会走
        stateHolder.removeState(exiting.key)
    }

    /** 出场收尾：从当前进度续播到 0，**完整播完**才 [destroyExiting]。 */
    suspend fun settleExit() {
        val exiting = outgoing ?: return
        val (dur, easing) = settleOnGlobalCurve(
            fromProgress = progress.value.coerceIn(0f, 1f),
            toProgress = 0f,
            fullEasing = NAV_EXIT_EASING,
            totalDuration = NAV_EXIT_DURATION,
            invertGlobal = true,
            minDuration = MIN_GESTURE_SETTLE_MS,
        )
        val finished = coroutineScope {
            val exit = async { progress.animateTo(0f, tween(dur, easing = easing)) }
            // 首层 pop：被推的是主界面（常驻底座），视差由宿主施加
            if (router.depth == 0) launch { mainTransition.animateTo(0f, tween(dur, easing = easing)) }
            exit.await()
        }
        // ⚠ 本次收尾不随导航取消：播到一半又 push 了新页时不比对，会把新页的入场动画掐掉。
        if (outgoing !== exiting) return
        if (finished) {
            destroyExiting(exiting)
        } else if (progress.value <= 0f && !back.isTransitioningBack()) {
            // 被打断时 p 已归手势驱动，不能直接收尾；但手势已结束、页面也彻底滑出屏幕，
            // 说明这次收尾被整个吞掉 —— 不补掉这一层会永远挂在渲染列表上。
            destroyExiting(exiting)
        }
    }

    // ── 导航同步（必须在组合期对齐，LaunchedEffect 晚一帧、那一帧会闪）────────────
    var lastRoute by remember { mutableStateOf(router.current) }
    if (lastRoute !== router.current) {
        lastRoute = router.current
        when (router.lastDirection) {
            NavDirection.Push -> {
                val interrupted = outgoing
                // ⚠ 必须**同帧**摘掉 outgoing：出场没播完又点进别的页时，那一层会继续盖在
                // 新页上面。顺带销掉它的状态 —— 此刻它也要离开渲染列表了。
                outgoing = null
                interrupted?.let { stateHolder.removeState(it.key) }
                // 同一 route 被打断：这一层从没销毁过，从当前进度续播；换了 route 才归零推入。
                if (interrupted?.route?.id != router.current?.id) progress.snapTo(0f)
            }
            // pop 起点就是 1，不用动 p，只要留住被弹出的那层
            NavDirection.Pop -> {
                // 出栈只是「移出栈」，**不销毁** —— 那一层还得把退出动画画完，状态由
                // settleExit 销。⚠ 上一次还没画完就又弹一层时，旧的那层直接掉出渲染列表，
                // 没人替它销状态，得在这里销。
                outgoing?.let { stateHolder.removeState(it.key) }
                // ⚠ 用「出栈前」的栈算 key：必须与它在栈里时那个 key 逐字相同（见 KDoc）。
                outgoing = router.lastPopped?.let { popped ->
                    ExitingLayer(popped, poppedLayerKey(router.routes + popped))
                }
            }
        }
    }

    // ── 预测性返回 ────────────────────────────────────────────────────────────
    // ⚠ handler 与 backProgress 必须来自同一个 rememberNavigationBack（上面已取得），
    // 拆成两处各自 remember = 编译全绿但跟手动画永远不动（miuix fork 踩过）。
    back.handler(
        enabled = router.hasOverlay,
        onBackCompleted = { router.popBack() },
        onBackCancelled = {
            if (outgoing != null) {
                // 手势打断的是一次还没播完的出场：「回弹」= 把那次出场播完。不这么走的话会把
                // 已出栈的那层又拉回全屏盖住新栈顶。
                scope.launch { settleExit() }
            } else {
                val (dur, easing) = settleOnGlobalCurve(
                    fromProgress = progress.value.coerceIn(0f, 1f),
                    toProgress = 1f,
                    fullEasing = NAV_ENTER_EASING,
                    totalDuration = NAV_ENTER_DURATION,
                    invertGlobal = false,
                    minDuration = MIN_GESTURE_RESTORE_MS,
                )
                scope.launch {
                    launch { progress.animateTo(1f, tween(dur, easing = easing)) }
                    // 首层返回时主界面也在跟手，一起弹回
                    if (router.depth == 1) launch { mainTransition.animateTo(1f, tween(dur, easing = easing)) }
                }
            }
        },
    )
    // backProgress 手势期间为 0..1、非手势期间为 null（skiko 恒 null），Android 上走 navigationevent 的 snapshot state，snapshotFlow 能跟上逐帧变化。
    LaunchedEffect(Unit) {
        // ⚠ 必须按手势起点成比例回落，不能写成 `p = 1 - 手势进度`：入场动画没播完就滑返回时（p 还在 0.4），第一帧手势进度≈0 会把 p 拉到 1，页面先「跳」到完全覆盖再跟手。故先冻结 gestureBase，再 `p = gestureBase × (1 - 进度)`。
        var gestureBase = 1f
        var mainGestureBase = 1f
        var sawGesture = false
        snapshotFlow { back.backProgress() }.collect { gestureProgress ->
            if (gestureProgress == null) {
                // 手势结束：若这次手势把一次出场收尾打断了、而页面当时已彻底滑出屏幕，
                // 那次收尾就没人接手 —— 这里补一次，否则那一层永远挂在渲染列表上不销毁。
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
            // snapTo 会挤掉还在跑的转场动画（NavTransitionState 的 mutation），否则被打断的入场动画下一帧就把手势落的值盖回去，跟手不动。
            progress.snapTo(gestureBase * remain)
            if (router.depth == 1) mainTransition.snapTo(mainGestureBase * remain)
        }
    }

    // ── 转场驱动（路由一变就起一段动画；被打断则从当前进度续播）──────────────────
    LaunchedEffect(router.current) {
        if (router.lastDirection == NavDirection.Pop) {
            settleExit()
        } else {
            val (dur, easing) = settleOnGlobalCurve(
                fromProgress = progress.value.coerceIn(0f, 1f),
                toProgress = 1f,
                fullEasing = NAV_ENTER_EASING,
                totalDuration = NAV_ENTER_DURATION,
                invertGlobal = false,
                minDuration = 0, // 纯续播
            )
            coroutineScope {
                launch { progress.animateTo(1f, tween(dur, easing = easing)) }
                // 首层 push：被推的是主界面（常驻底座），视差由宿主施加
                if (router.depth == 1) launch { mainTransition.animateTo(1f, tween(dur, easing = easing)) }
            }
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

/**
 * 整栈渲染 + 三档绘制策略。
 *
 * ⚠ 所有层都走同一个循环：每层的组合槽位只由下标决定。把栈顶单独放在循环外的话，push 时那层要换槽位 = 新挂一份，新槽位的 `SaveableStateRegistry` 读到还是空的 → `rememberSaveable` 全部重建。栈只在尾部增删，下标天然稳定。
 */
@Composable
private fun NavLayerStack(
    routes: List<AppRoute>,
    /** pop 期间还要画完退出动画的那一层；平时为 null。 */
    exiting: ExitingLayer?,
    progress: NavTransitionState,
    stateHolder: SaveableStateHolder,
    modifier: Modifier = Modifier,
    onOverlayActivityChanged: ((Boolean) -> Unit)? = null,
    content: @Composable (layerKey: String, AppRoute) -> Unit,
) {
    val (stackKeys, exitKey) = layerKeys(routes, exiting)

    // 栈内各层 +（pop 期间）已出栈、正在播退场的那层（排最后 = 画在最上面）
    val layers: List<AppRoute> = if (exiting != null) routes + exiting.route else routes
    val renderKeys: List<String> = if (exitKey != null) stackKeys + exitKey else stackKeys
    // 出场动画还在跑时也拦截原生输入，直到这一层彻底画完
    SideEffect { onOverlayActivityChanged?.invoke(layers.isNotEmpty()) }

    // 屏幕圆角：转场中间态把栈顶裁成圆角，像一张卡片从右边推进来。下层是被揭开的，不该有轮廓。
    val cornerRadiusPx = rememberScreenCornerRadiiPx()
    val density = LocalDensity.current
    val clipShape = remember(cornerRadiusPx, density) {
        if (cornerRadiusPx.maxRadius > 0f) cornerRadiusPx.toShape(density) else null
    }
    val inFlight: () -> Boolean = { progress.value > 0f && progress.value < 1f }
    // 黑幕只压暗被盖住的内容，栈顶自己不被压暗；p 到位就不画，省一次全屏 alpha 混合。
    val scrimAlpha: () -> Float = {
        if (progress.value < 0.999f) NAV_BG_DIM * progress.value else 0f
    }

    Box(modifier) {
        //   栈顶     → 常画（全屏圆角裁切 + 黑幕，都在 PageLayer 里）
        //   直接下层 → p<1 就画，p==1（被完全盖住）才暂停。⚠ 不能用「0<p<1」判：push 起始帧 p 恰好=0、栈顶还在屏外，那一帧下层必须画，否则会闪出主界面（实测）
        //   更深层   → 永远不画，只保留组合与状态
        val topIndex = layers.lastIndex
        for (index in layers.indices) {
            val isTop = index == topIndex
            val isDirectUnder = index == topIndex - 1
            val fraction: () -> Float = when {
                isTop -> ({ 1f - progress.value })
                isDirectUnder -> ({ -NAV_PARALLAX * progress.value })
                else -> ({ -NAV_PARALLAX }) // 更深层固定停靠位，p 永远不影响它们
            }
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
                stateHolder.SaveableStateProvider(renderKeys[index]) { content(renderKeys[index], layers[index]) }
            }
        }
    }
}

/**
 * 正在播退出动画的那一层：路由 + 它在栈里时用的 [SaveableStateHolder] key。
 *
 * ⚠ key 必须随路由一起存下来：销毁可能发生在栈又变过之后（出场途中又 push / 又 pop），那时按栈重算会算到别的层上，`removeState` 销错层。
 */
internal class ExitingLayer(val route: AppRoute, val key: String)

/**
 * 给「这一帧要渲染的每一层」算 [SaveableStateHolder] 的 key。
 *
 * 栈内重复路由首次保持裸 id、其后 `#2`/`#3`：裸 id 才能让重开同一页恢复滚动位置，而同一帧出现两次同 key 会抛 `IllegalArgumentException` 整屏崩（连点两次同一入口就会）。
 *
 * ⚠ 边分配边占坑，别预先把整个栈的 id 塞进 used：那样裸 id 一个都发不出去、每层都变 `#2`，与事后按栈复算的 key 对不上 → `removeState` 静默落空，出栈页状态永远销不掉。
 * ⚠ 别把结果包进 remember：`routes` 是 `SnapshotStateList`，没重写 equals/hashCode → `Composer.changed` 判「没变」→ key 列表跟实际栈对不上。直接算即可。
 *
 * 出栈层沿用它在栈里时的那个 key（见 [poppedLayerKey]），所以天然不与栈内 key 撞。
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

/**
 * 出栈那一层**在栈里时**用的 key（= 出栈前那一帧的栈分配给最后一个元素的 key）
 */
internal fun poppedLayerKey(stackBeforePop: List<AppRoute>): String =
    layerKeys(stackBeforePop, null).first.last()

/**
 * 按「占自身宽度的比例」横向摆位的一层。
 *
 * ⚠ 用 `Modifier.layout` 自己 place：`offset` 把位移写进 constraints，子节点每帧真正 re-measure（整页列表/pager/玻璃采样跟着重测，必掉帧）；`graphicsLayer { translationX }` 不重测但套离屏 layer，可能干扰页内 `layerBackdrop` 采样。
 *
 * ⚠ 黑幕与圆角分居 `layout` 两侧，方向相反理由也相反：黑幕放外层（内层坐标系跟着页面平移，放里面黑幕会跟着滑走）；裁切放内层（圆角才长在页面自己的角上，放外层则锚在屏幕四角，页面滑到贴边时才重合、看起来等于没生效）。
 */
@Composable
private fun PageLayer(
    fraction: () -> Float,
    paused: () -> Boolean = { false },
    clipShape: Shape? = null,
    /** 非 null 时在本层内容**之下**画一层全屏黑幕（只给栈顶传）。 */
    scrimAlpha: (() -> Float)? = null,
    inFlight: () -> Boolean = { false },
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            // 暂停绘制：层还在组合里（状态、协程全活着），只是不进渲染管线 —— 等价 stop 的
            // Activity 不画窗口。放最外层：暂停时连内层 clip 都跳过。
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
 * 暂停绘制（「整栈常驻」策略的一半，另一半是栈内每层都保持组合）。
 *
 * [paused] 在 draw 阶段读，恢复时由快照观察自动失效重画；[SkipDrawElement.update] 里还要显式 `invalidateDraw()`：pop 刚结束时某层从「更深层」升为「栈顶」，paused lambda 换了引用但没有任何进度帧在跑，不显式失效就没人重画。
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

/**
 * 在开/关曲线上取从 [fromProgress] 到 [toProgress] 的剩余段，用于打断续播与手势吸附：半路接手时速度连续，而不是从头按一条新曲线重播。
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

/** 二级页转场参数 */
internal const val NAV_ENTER_DURATION = 600
internal const val NAV_EXIT_DURATION = 320
// NAV_PARALLAX 在同包的 NavTransitionState.kt（本模块），无需 import。
// 压暗只在本文件用，保持 internal。
internal const val NAV_BG_DIM = 0.42f

/** 入场：更晚进入减速，尾段拖得更长更慢 */
internal val NAV_ENTER_EASING = OobeQuartOutSoftStartEasing
/** 退出：起步更缓，后段正常滑出 */
internal val NAV_EXIT_EASING = SecondaryPageExitEasing

/** 手势吸附的最短时长：剩余距离太小时也播一小段，否则看着像卡住没反应 */
private const val MIN_GESTURE_SETTLE_MS = 100
private const val MIN_GESTURE_RESTORE_MS = 200