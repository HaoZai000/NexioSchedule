/**
 * 全局「单卡片高度变化」动效。
 *
 * 为什么不用 `Card.animateContentSize`：它补间的是占位尺寸、子节点始终按最终尺寸绘制 ——
 * 收起时内容已按小尺寸摆好，只剩空白在缩（「关闭没有动画」），嵌套卡片还会因两层尺寸不同步而错位。
 * 故高度变化必须由 `AnimatedVisibility` 自己算：退出期间仍保留内容，逐帧压到 0 高度，才能「收得回去」。
 *
 * 用法：[CardExpand] 包住卡片里条件出现/消失的内容；[Modifier.cardContentSize] 用于自绘容器自身尺寸变化。
 *
 * ⚠ 同一段内容只用一种：`CardExpand` 里再套 `cardContentSize` 会变成「动画的动画」，发肉。
 */
package com.haooz.chedule.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.isRenderEffectSupported
import top.yukonga.miuix.kmp.basic.CardDefaults

/** 卡内高度变化的统一参数，改这里即全站生效。 */
object CardExpandSpec {

    /**
     * 高度补间曲线：复用 `CardDefaults.SizeAnimation`（与其它处同一条 spring，避免参数漂移）。
     * `dampingRatio = 0.9` 仅极轻微回弹；`StiffnessMediumLow` 对 40~120dp 行高变化约 260~320ms，不必写固定时长。
     */
    val HeightSpring: FiniteAnimationSpec<IntSize> = CardDefaults.SizeAnimation

    /** 高度变化时内容淡入淡出的时长（ms）：比 spring 短，免得「先亮后长」。 */
    const val FADE_MILLIS = 180

    /** 模糊半径（dp） */
    const val BLUR_DP = 12f

    /**
     * 展开时模糊散尽的时长（ms）。**比高度长**：高度先长到位，模糊再收尾变清晰
     * （高度 spring 大致 260~320ms，这里留 420ms 做拖尾）。
     */
    const val BLUR_IN_MILLIS = 420

    /**
     * 收起时模糊糊满的时长（ms）。**比高度短**：模糊先到位，之后高度继续缩，
     * 观感是「先糊住、再收起来」。
     */
    const val BLUR_OUT_MILLIS = 160
}

/**
 * 卡片里某段内容条件出现 / 消失。
 * 展开：高度 0→实际 + 淡入 + 模糊 12dp→0（比高度晚散尽）；收回：内容压到 0 + 淡出 + 模糊 0→12（更早糊满）。
 * 高度走 spring（[CardExpandSpec.HeightSpring]），模糊走两条固定时长（[CardExpandSpec.BLUR_IN_MILLIS]/[CardExpandSpec.BLUR_OUT_MILLIS]），故意错开（进慢出快）。
 *
 * @param visible 该段内容是否呈现。
 * @param expandFrom 展开/收缩锚点；默认顶部，文件夹展开等需内容跟底边下滑的场景传 `Alignment.Bottom`。
 * @param fade 是否淡入淡出，个别地方想更干净可关。
 */
@Composable
fun CardExpand(
    visible: Boolean,
    modifier: Modifier = Modifier,
    expandFrom: Alignment.Vertical = Alignment.Top,
    fade: Boolean = true,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        // 淡入淡出不走 enter/exit：AnimatedVisibility 的 graphicsLayer 是常驻的，
        // 超长内容被它罩住会踩 layer 尺寸上限，下半部分触摸失效。改由下面按动画态临时挂。
        enter = expandVertically(
            animationSpec = CardExpandSpec.HeightSpring,
            expandFrom = expandFrom,
        ),
        exit = shrinkVertically(
            animationSpec = CardExpandSpec.HeightSpring,
            shrinkTowards = expandFrom,
        ),
    ) {
        // 模糊随展开进度走：展开 12dp → 0（从糊到清），收起 0 → 12。
        // ⚠ 只在 `graphicsLayer` 绘制期读 `blurProgress.value`，组合期不读，整段动画零重组。
        val blurProgress = transition.animateFloat(
            transitionSpec = {
                // 展开（→ Visible）：模糊比高度散得慢，高度长完后再收尾变清晰；
                // 收起（→ PreExit）：模糊比高度先糊满，之后高度继续缩。
                if (targetState == EnterExitState.Visible) {
                    tween(CardExpandSpec.BLUR_IN_MILLIS, easing = FastOutSlowInEasing)
                } else {
                    tween(CardExpandSpec.BLUR_OUT_MILLIS, easing = FastOutSlowInEasing)
                }
            },
            label = "CardExpandBlur",
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        val alphaProgress = transition.animateFloat(
            transitionSpec = { tween(CardExpandSpec.FADE_MILLIS) },
            label = "CardExpandAlpha",
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        // 只在动画期间挂 layer：稳定态留着会把超高内容罩进离屏层，下半部分点不到。
        val animating by remember(transition) {
            derivedStateOf { transition.currentState != transition.targetState }
        }
        // ⚠ 必须包一层 Column：`AnimatedVisibility` 内部的 Layout 会把**每个子项都
        // place 在 (0,0)**（它按「只有一个子内容」设计）。直接放多行内容会全部叠在
        // 同一个位置上，看起来像「好几个选项挤成一行」。
        Column(
            modifier = if (animating) {
                Modifier.graphicsLayer {
                    if (fade) alpha = alphaProgress.value
                    val px = CardExpandSpec.BLUR_DP.dp.toPx() * (1f - blurProgress.value)
                    renderEffect = if (px > 0.01f && isRenderEffectSupported()) {
                        BlurEffect(px, px, TileMode.Decal)
                    } else null
                }
            } else Modifier
        ) {
            content()
        }
    }
}

/**
 * 容器自身高度变化（没有条件子节点，纯测量结果变了）时按同一条 spring 补间。
 *
 * 只给**自绘容器**用；miuix 的 `Card` 请改用 [CardExpand] 包住会变的那一段。
 *
 * @param alignment 尺寸变化时的对齐方向，默认从左上开始长。
 */
fun Modifier.cardContentSize(
    alignment: Alignment = Alignment.TopStart,
): Modifier = animateContentSize(
    animationSpec = CardExpandSpec.HeightSpring,
    alignment = alignment,
)
