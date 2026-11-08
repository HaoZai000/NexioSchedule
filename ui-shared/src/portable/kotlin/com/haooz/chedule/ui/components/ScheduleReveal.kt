/**
 * 课表内「出现 / 消失」的唯一动效来源，参数取 [CardRevealSpec]。
 * 首帧不播入场、元素始终占位、稳态复用单例状态机（零 Animatable / 零协程）。
 * 进度只在绘制期读；组合期只能读 [ScheduleRevealState.isAnimating] / [ScheduleRevealState.isActive]。
 * 加号卡与午/晚休分界带刻意直接显隐。
 */
package com.haooz.chedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.haooz.softglass.platform.isRenderEffectSupported
import kotlinx.coroutines.delay

/** 课表元素的显隐进度。[value] 只能在绘制期读。 */
abstract class ScheduleRevealState internal constructor() {
    /** 0 = 隐藏，1 = 呈现；绘制期读，变化只触发重绘。 */
    abstract val value: Float

    /** 图层是否必须留着；退场停在 0 时恒为 true（撤层会让 alpha 跳回 1）。 */
    abstract val isAnimating: Boolean

    /** 是否正在播放；只在动画起止各翻一次，组合期可安全读。 */
    abstract val isActive: Boolean
}

/** 稳态单例：从未切换过显隐的元素复用，零 Animatable / 零协程。 */
private object RevealShown : ScheduleRevealState() {
    override val value = 1f
    override val isAnimating = false
    override val isActive = false
}

private object RevealHidden : ScheduleRevealState() {
    override val value = 0f
    override val isAnimating = false
    override val isActive = false
}

private class ActiveRevealState(
    private val progress: Animatable<Float, AnimationVector1D>,
    private val animating: State<Boolean>,
    private val active: State<Boolean>,
) : ScheduleRevealState() {
    override val value: Float get() = progress.value
    override val isAnimating: Boolean get() = animating.value
    override val isActive: Boolean get() = active.value
}

/** 创建一份课表显隐进度。@param animateEnter 首帧就播入场（运行期新增的卡片传 true）。 */
@Composable
fun rememberScheduleRevealState(
    visible: Boolean,
    animateEnter: Boolean = false,
): ScheduleRevealState {
    // 组合期同步推进显隐代数：进度初值与图层开关必须在首帧绘制前定好，否则会先完整闪一帧
    val lastVisible = remember { arrayOfNulls<Boolean>(1) }
    val generation = remember { intArrayOf(0) }
    if (lastVisible[0] == null) {
        lastVisible[0] = visible
    } else if (lastVisible[0] != visible) {
        lastVisible[0] = visible
        generation[0]++
    }
    val gen = generation[0]

    // 稳态直接复用单例；翻转过就永久留状态机，退场停在 0 时图层不能撤
    if (gen == 0 && !animateEnter) return if (visible) RevealShown else RevealHidden

    // 入场从 0 起、退场从 1 起，首帧就挂图层
    val startProgress = if (visible) 0f else 1f
    val progress = remember(gen) { Animatable(startProgress) }
    val animating = remember(gen) { mutableStateOf(true) }
    val active = remember(gen) { mutableStateOf(true) }
    val state = remember(gen) { ActiveRevealState(progress, animating, active) }

    LaunchedEffect(gen) {
        if (visible) {
            progress.animateTo(
                1f,
                tween(CardRevealSpec.ENTER_MILLIS, easing = FastOutSlowInEasing),
            )
            animating.value = false
            active.value = false
        } else {
            progress.animateTo(
                0f,
                tween(CardRevealSpec.EXIT_MILLIS, easing = FastOutSlowInEasing),
            )
            // 退场停在 0：图层留着，常驻绘制节点可以撤
            active.value = false
        }
    }
    return state
}

/**
 * 同一节点换显示状态时只播一次入场，不产生退场项（避免调课后原卡二次显形）。
 * [epoch] 变化只更新基线不播；同 epoch 内 [state] 翻转才播。
 */
@Composable
fun rememberScheduleRevealReplay(
    epoch: Any?,
    state: Any?,
): ScheduleRevealState {
    val last = remember { arrayOfNulls<Any>(2) }
    val initialized = remember { booleanArrayOf(false) }
    val generation = remember { intArrayOf(0) }

    // 同 epoch 内 state 翻转才换一代；判定必须在组合期，否则首帧会先完整闪一下
    if (!initialized[0]) {
        initialized[0] = true
        last[0] = epoch
        last[1] = state
    } else {
        val epochChanged = last[0] != epoch
        val stateChanged = last[1] != state
        last[0] = epoch
        last[1] = state
        // 翻周只换基线，不播
        if (!epochChanged && stateChanged) generation[0]++
    }
    val gen = generation[0]

    // 没换过状态的卡复用稳态单例
    if (gen == 0) return RevealShown

    val progress = remember(gen) { Animatable(0f) }
    val animating = remember(gen) { mutableStateOf(true) }
    val active = remember(gen) { mutableStateOf(true) }
    val result = remember(gen) { ActiveRevealState(progress, animating, active) }

    LaunchedEffect(gen) {
        progress.animateTo(
            1f,
            tween(CardRevealSpec.ENTER_MILLIS, easing = FastOutSlowInEasing),
        )
        animating.value = false
        active.value = false
    }
    return result
}

/**
 * 显隐进度绘制层：透明 + 上浮 + 缩放 + 散焦，与 [CardReveal] 同一套手感。
 * 稳态由调用方判断 `isAnimating` 后不挂本层。
 * [blur] 给细线 / 描边关掉；[transform] 给贴节次几何画的横带 / 时间线关掉，只留淡入淡出。
 */
fun Modifier.scheduleRevealLayer(
    reveal: ScheduleRevealState,
    blur: Boolean = true,
    transform: Boolean = true,
): Modifier = graphicsLayer {
    val p = reveal.value
    alpha = p
    if (transform) {
        translationY = (1f - p) * CardRevealSpec.OFFSET_DP.dp.toPx()
        val scale = CardRevealSpec.scaleOf(p)
        scaleX = scale
        scaleY = scale
    }
    clip = false
    val blurPx = CardRevealSpec.BLUR_DP.dp.toPx() * (1f - p)
    renderEffect = if (blur && p > 0.01f && blurPx > 0.01f && isRenderEffectSupported()) {
        BlurEffect(blurPx, blurPx, TileMode.Decal)
    } else null
}

/** 单个课表元素的统一出现 / 消失容器。消失时会把节点留到退场播完再摘。 */
@Composable
fun ScheduleReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    animateEnter: Boolean = false,
    blur: Boolean = true,
    transform: Boolean = true,
    content: @Composable () -> Unit,
) {
    val reveal = rememberScheduleRevealState(visible, animateEnter)
    var present by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            present = true
        } else {
            // 多留一帧，保证最后一帧 alpha=0 已经画出去
            delay(CardRevealSpec.EXIT_MILLIS.toLong() + 32L)
            present = false
        }
    }
    if (!present) return
    Box(
        modifier = modifier.then(
            if (reveal.isAnimating) {
                Modifier.scheduleRevealLayer(reveal, blur = blur, transform = transform)
            } else Modifier
        ),
    ) {
        content()
    }
}

/** [rememberRevealPresence] 的返回项。 */
class RevealPresence<T>(
    val key: String,
    val value: T,
    /** false = 已从 source 移除，正在播退场。 */
    val visible: Boolean,
    /** true = 运行期新增，首帧要播入场；页面首帧已有的项为 false。 */
    val animateEnter: Boolean,
)

private class PresenceHolder<T> {
    val last = LinkedHashMap<String, T>()
    val exiting = LinkedHashMap<String, T>()
    val presentKeys = HashSet<String>()
    var initialized = false
}

/**
 * 列表项退场保留：source 移除的项留在返回列表里（visible=false）直到退场播完。
 * 身份只按 [keyOf]，不掺显示状态；状态翻转交给 [rememberScheduleRevealReplay]，
 * 否则会凭空生成旧身份退场项、调课落地后二次显形。
 * 组合期只写普通字段，清理由 cleanupTick 触发一次重组，不逐帧重组。
 *
 * @param silentRemoval 返回 true 时不生成退场项，只给视觉已由拖拽浮层接管的卡用。
 */
@Composable
fun <T> rememberRevealPresence(
    source: List<T>,
    silentRemoval: ((T) -> Boolean)? = null,
    keyOf: (T) -> String,
): List<RevealPresence<T>> {
    val holder = remember { PresenceHolder<T>() }
    var cleanupTick by remember { mutableStateOf(0) }
    val keys = source.map(keyOf)
    val keySet = keys.toHashSet()

    val firstFrame = !holder.initialized
    holder.initialized = true
    val added = if (firstFrame) {
        emptySet()
    } else {
        keys.filter { it !in holder.presentKeys }.toHashSet()
    }
    // 组合期同步登记退场项，移除当帧就能开始淡出
    for ((k, v) in holder.last) {
        if (k !in keySet && k !in holder.exiting) {
            // 浮层已接管的卡直接丢弃，落地后不再显形
            if (silentRemoval?.invoke(v) == true) continue
            holder.exiting[k] = v
        }
    }
    holder.last.clear()
    source.forEachIndexed { i, v -> holder.last[keys[i]] = v }
    holder.presentKeys.clear()
    holder.presentKeys.addAll(keys)

    val result: List<RevealPresence<T>> =
        source.mapIndexed { i, v -> RevealPresence(keys[i], v, true, keys[i] in added) } +
            holder.exiting.entries
                .filter { it.key !in keySet }
                .map { (k, v) -> RevealPresence(k, v, false, false) }

    // keys 按内容比较即可，省掉每次重组拼接一遍签名字符串
    LaunchedEffect(keys, cleanupTick) {
        if (holder.exiting.isNotEmpty()) {
            delay(CardRevealSpec.EXIT_MILLIS.toLong() + 32L)
            holder.exiting.clear()
            cleanupTick++
        }
    }
    return result
}
