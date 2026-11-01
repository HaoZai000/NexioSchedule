/**
 * 全局卡片「出现 / 消失」动效。
 *
 * 两种用法：[CardReveal]（首选，直接包住卡片）或 [rememberCardRevealState]（卡片已有 `graphicsLayer` 时自写图层，参数同一份）。
 *
 * 约定：
 * 1. 稳态零代价：停稳后撤掉 `graphicsLayer`，静态只剩 `Box`。
 * 2. 不在组合期读进度：只在绘制阶段读，整段零重组（仅一个「是否还在动」布尔在起止各翻一次）。
 * 3. 始终参与布局：只做透明/位移/缩放，勿换 `AnimatedVisibility`（收拢尺寸会让外层 `wrapContentHeight` 逐帧变高、弹窗闪烁）。
 * 4. 错开由 [index] 负责：入场延迟 = `START_DELAY_MILLIS + index × STEP_DELAY_MILLIS`。
 *
 * ⚠ 入场延迟只在**首次**出现时生效；之后 `visible` 反复切换立即开始，避免「删一个又等一轮」。
 */
package com.haooz.chedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.kyant.backdrop.isRenderEffectSupported
import kotlinx.coroutines.delay

/** [CardReveal] 的统一参数，改这里即全站生效。 */
object CardRevealSpec {

    /** 入场时长（ms）：透明度 + 位移 + 缩放 + 散焦同时走完。**调入场快慢改这里**。 */
    const val ENTER_MILLIS = 260

    /** 退场时长（ms）：比入场略快，删除时手感干脆。**调退场快慢改这里**。 */
    const val EXIT_MILLIS = 200

    /** 首张卡开始前的静置（ms）：等页面 / 弹窗自身进场先落地。 */
    const val START_DELAY_MILLIS = 120L

    /** 相邻两张卡的错开（ms）。 */
    const val STEP_DELAY_MILLIS = 56L

    /** 入场起始下移量（dp）：字面意义上的「从下面浮上来一点」。 */
    const val OFFSET_DP = 8f

    /** 入场起始缩放：只收一点点，太大就成了弹跳。 */
    const val SCALE_FROM = 0.97f

    /** 模糊幅度（dp）。方向跟着进度走，不需要单独配 */
    const val BLUR_DP = 12f

    /** 进度 [progress]（0..1）对应的缩放。 */
    fun scaleOf(progress: Float): Float = SCALE_FROM + (1f - SCALE_FROM) * progress
}

/**
 * 卡片入场 / 退场进度。
 *
 * [value] **只能在绘制阶段读**（`graphicsLayer` / `drawWithContent` 里），
 * 组合期读会变成逐帧重组。
 */
class CardRevealState internal constructor(
    private val progress: Animatable<Float, AnimationVector1D>,
    private val animating: State<Boolean>,
) {
    /** 0 = 完全隐藏，1 = 完全呈现。 */
    val value: Float get() = progress.value

    /** 是否还在动（含退场停在 0 的状态）；false 时调用方可以撤掉图层。 */
    val isAnimating: Boolean get() = animating.value
}

/**
 * 把 [CardRevealState] 直接铺成图层：透明度 + 上浮 + 缩放 + 模糊**一次给全**。
 * 卡片自己已经有 `graphicsLayer`（要挂 `onGloballyPositioned` / `animateItem`）时用这个，
 * 不要自己再抄一遍那四个量。
 *
 * @param alphaMultiplier 额外乘在透明度上的系数，给「已隐藏」之类的叠加态用。
 */
fun Modifier.cardRevealLayer(
    reveal: CardRevealState,
    alphaMultiplier: Float = 1f,
): Modifier = graphicsLayer {
    // 绘制阶段读：不订阅状态，不触发重组。
    val p = reveal.value
    alpha = alphaMultiplier * p
    translationY = (1f - p) * CardRevealSpec.OFFSET_DP.dp.toPx()
    val scale = CardRevealSpec.scaleOf(p)
    scaleX = scale
    scaleY = scale
    clip = false
    // 入场 8→0 dp（从糊到清）；退场 p 反向走，自然就是 0→8。
    val blurPx = CardRevealSpec.BLUR_DP.dp.toPx() * (1f - p)
    renderEffect = if (p > 0.01f && blurPx > 0.01f && isRenderEffectSupported()) {
        BlurEffect(blurPx, blurPx, TileMode.Decal)
    } else null
}

/**
 * 创建一份卡片入场 / 退场进度，供自己写 `graphicsLayer` 的调用方使用。
 *
 * @param visible 是否应当呈现；false 会播退场。
 * @param index 同屏多张卡的错开序号，从 0 开始；只影响首次入场延迟。
 * @param animateEnter 是否播入场。已经在场上的元素（比如「当前课表」）传 false。
 */
@Composable
fun rememberCardRevealState(
    visible: Boolean = true,
    index: Int = 0,
    animateEnter: Boolean = true,
): CardRevealState {
    // 已经在场上的（animateEnter=false）直接置 1；其余从 0 起，包括一开始就隐藏的。
    val startValue = if (visible && !animateEnter) 1f else 0f
    val progress = remember { Animatable(startValue) }
    // 用裸 State 而不是委托，好把同一个实例交给 CardRevealState 观察。
    val animating = remember { mutableStateOf(visible && animateEnter) }
    // 首次入场的错开延迟只给一次。
    var firstEnter by remember { mutableStateOf(animateEnter) }

    LaunchedEffect(visible) {
        if (!visible) {
            if (progress.value > 0f) {
                animating.value = true
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(CardRevealSpec.EXIT_MILLIS, easing = FastOutSlowInEasing),
                )
            }
            // 停在 0 时图层必须留着，撤掉会让 alpha 跳回 1（卡片「诈尸」一闪）。
            animating.value = true
            return@LaunchedEffect
        }
        if (!animateEnter) {
            progress.snapTo(1f)
            animating.value = false
            return@LaunchedEffect
        }
        animating.value = true
        if (firstEnter) {
            firstEnter = false
            val delayMillis =
                CardRevealSpec.START_DELAY_MILLIS + index * CardRevealSpec.STEP_DELAY_MILLIS
            if (delayMillis > 0L) delay(delayMillis)
        }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(CardRevealSpec.ENTER_MILLIS, easing = FastOutSlowInEasing),
        )
        animating.value = progress.value < 1f
    }

    return remember(progress, animating) { CardRevealState(progress, animating) }
}

/**
 * 把任意卡片包成「出现时从模糊里淡入上浮、消失时淡出变糊」的容器。
 *
 * @param visible 是否应当呈现。false 会播退场（**不会**移除节点，需要移除由调用方的列表负责）。
 * @param index 同屏多张卡的错开序号，从 0 开始；只影响首次入场延迟。
 * @param animateEnter 首帧是否播入场。已经在场上的元素（比如「当前课表」）传 false。
 * @param modifier 落在外层 [Box] 上；默认 `fillMaxWidth`，卡片自己不必再写。
 */
@Composable
fun CardReveal(
    visible: Boolean = true,
    index: Int = 0,
    animateEnter: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reveal = rememberCardRevealState(visible = visible, index = index, animateEnter = animateEnter)
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 停稳后不挂层：静态零代价
            .then(if (reveal.isAnimating) Modifier.cardRevealLayer(reveal) else Modifier),
    ) {
        content()
    }
}
