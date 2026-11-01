/**
 * 全局文字变化动效：文字被改写时，旧字模糊淡出、新字从模糊中聚焦，两者交叉重叠。
 *
 * ## 四条硬约束
 *
 * 1. **稳态零代价**：不动画时 Box 与 graphicsLayer **整块不挂载**，调用方的 modifier
 *    原样交给唯一的那个 Text，节点数与改造前完全相同。动画进度只在**绘制期**读取，
 *    过渡期间也不触发重组。
 * 2. **首帧不播**：首次组合只是「出现」，不是「变化」。不记住初值的话，列表滚动 / 页面
 *    进入时每个字都会糊一下，看起来像渲染故障。
 * 3. **批量降级**：整屏同帧全变时由 [TextMotionBudget] 卡住并发数，超出的直接换字。
 * 4. **模糊是包络，不是钟形**：模糊走「攻击 → 散焦」两段，**中间没有保持段**。
 *    - 连续换字时散焦总被新值打断，模糊一直在 8 和 6 之间来回，**不会真的清晰**；
 *    - 已经在散焦途中又换了字，就**先把当前半径拉回满值，再从满值散到 0**
 *      （8 → 6 → **6 拉回 8** → 0），绝不顺着往下掉；
 *    - 只有最后一次换字之后，那一段散焦才走到底。
 *    钟形曲线做不到这点：`progress` 每 460ms 必然走完一遍，中间换几次字都一样闪一轮，
 *    看起来就是「播一遍结束、再播一遍」。
 *
 * ⚠ 状态一律在协程里写，**绝不在组合期写**。这个包装器要盖住全部 376 处 Text，
 * 组合期写状态一旦哪里不收敛就是全局性的重组风暴，排查成本远超收益。
 */
package com.haooz.chedule.ui.components.text

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.isRenderEffectSupported
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 交叉淡入总时长（ms）。 */
private const val TEXT_MOTION_MILLIS = 320

/** 从 0 糊到满的时长（ms）。 */
private const val TEXT_MOTION_BLUR_ATTACK_MILLIS = 140

/** 从半路拉回满模糊的最短时长（ms），按剩余距离折算，别让它慢悠悠地爬。 */
private const val TEXT_MOTION_BLUR_REATTACK_MIN_MILLIS = 40

/**
 * 散焦时长（ms）。攻击一到位就立刻开始散，中间**不留保持段**。
 *
 * 单次换字总时长 = [TEXT_MOTION_BLUR_ATTACK_MILLIS] + 本值 = **450ms**，改一边记得补另一边。
 * 连续换字靠「散焦被新值 cancel → 重新攻击」维持模糊：在 8 和 6 之间来回，而不是停住。
 */
private const val TEXT_MOTION_BLUR_RELEASE_MILLIS = 180

/**
 * 模糊半径上限（dp）。
 *
 * 只糊到这个量级有原因：`renderEffect` 每处都要建一份离屏缓冲，半径越大离屏面积越大。
 */
private val TEXT_MOTION_BLUR = 8.dp

/**
 * 让范围内所有文字直接换、不走交叉模糊，同时**不建动效机器**（无状态 / 无 Animatable / 无循环）。
 * 借位页换数据、时间列这类静态标签都用它。
 */
val LocalTextMotionInstant = staticCompositionLocalOf { false }

/** 动画量惰性持有：不换字就永不实例化。 */
private class TextMotionAnims {
    val progress by lazy { Animatable(1f) }
    val blurLevel by lazy { Animatable(0f) }
}

/**
 * 把文字包成「变化时有交叉模糊过渡」的容器，[key] 是判断「文字是否变了」的依据。
 * **布局契约**：调用方**不要**再把自己的 modifier 直接贴到 Text 上，交给本函数的 [modifier]。
 *
 * @param key 用于比较的文字内容，相等即视为「没变」。
 * @param textAlign 调用方传给 Text 的对齐方式，**只影响过渡态两条文字的落点**（见下）。
 *   稳态时容器不挂载，对齐由 Text 自己处理，这里传不传都一样。
 * @param content 绘制文字：参数是要画的文字，以及应施加于该 Text 的修饰。
 *
 * **过渡态的落点**：旧字与新字各画一份（透明度挂在文字自己身上），容器宽取两者的较大值。
 * 父级给的是**固定宽度**
 * （`weight` / `fillMaxWidth`）时，两条都被量成同宽，落点恒为 0 —— 与稳态逐像素一致；
 * 父级给**松约束**时两条各自是固有宽度，短的那条必须按 [textAlign] 靠到对应边上
 *
 * **靠父级居中的文字要自己传 `textAlign = TextAlign.Center`**
 */
@Composable
internal fun <T : Any> TextMotionBox(
    key: T,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    content: @Composable (text: T, modifier: Modifier) -> Unit,
) {
    // 组合期读静态 local：值翻转时本子树整体重组，正好在此收摊/重开
    if (LocalTextMotionInstant.current) {
        content(key, modifier)
        return
    }

    // 当前该画的新文字。
    var displayed by remember { mutableStateOf(key) }
    // 正在淡出的旧文字；非 null 即处于交叉淡入态。
    var outgoing by remember { mutableStateOf<T?>(null) }
    // 模糊包络是否还在（含保持 / 散焦阶段）。只在「起 / 收」两处翻转，不逐帧重组。
    var blurActive by remember { mutableStateOf(false) }
    val anims = remember { TextMotionAnims() }
    // 平台不支持 RenderEffect（Android 12 以下）时全程退化为直接换字。
    val supported = remember { isRenderEffectSupported() }
    // 用 State 承接最新的 key，动画循环只起一次，新值由 snapshotFlow 送进来。
    val latestKey by rememberUpdatedState(key)

    // 动画循环只起一次（单位是 Unit）
    // 单循环 + collectLatest：新值到达时掐掉上一段，模糊停在当前值，再由下一段接住。
    LaunchedEffect(Unit) {
        var isFirstEmission = true
        snapshotFlow { latestKey }.collectLatest { newKey ->
            if (isFirstEmission) {
                // 首帧只是「出现」，不是「变化」，不播动画
                isFirstEmission = false
                displayed = newKey
                outgoing = null
                return@collectLatest
            }
            if (newKey == displayed) return@collectLatest
            // 不支持模糊、或并发配额已满：直接换字，不播动效（批量降级）。
            if (!supported || !TextMotionBudget.tryAcquire()) {
                displayed = newKey
                outgoing = null
                blurActive = false
                return@collectLatest
            }
            try {
                val progress = anims.progress
                val blurLevel = anims.blurLevel
                // 交叉淡入：上一段已经播完（稳态）才从 0 起；半途换字接着当前进度走
                if (progress.value >= 1f) progress.snapTo(0f)
                outgoing = displayed
                displayed = newKey
                blurActive = true
                // 淡入独立跑：它走完只代表「这一对字换清楚了」，不负责散焦。
                launch {
                    val remaining =
                        ((1f - progress.value) * TEXT_MOTION_MILLIS).roundToInt().coerceAtLeast(1)
                    progress.animateTo(1f, tween(remaining, easing = FastOutSlowInEasing))
                    outgoing = null
                }
                // 模糊包络：攻击 → 散焦（**没有保持段**）。
                // 半路又换字时本段被取消：正处在散焦（比如已散到 6）就会先把它拉回 8。
                val from = blurLevel.value
                if (from < 1f) {
                    val attack = ((1f - from) * TEXT_MOTION_BLUR_ATTACK_MILLIS)
                        .roundToInt()
                        .coerceIn(TEXT_MOTION_BLUR_REATTACK_MIN_MILLIS, TEXT_MOTION_BLUR_ATTACK_MILLIS)
                    blurLevel.animateTo(
                        1f,
                        // 往回拉时用「先快后慢」：视觉上是啪一下回到满模糊，而不是慢慢爬。
                        tween(attack, easing = if (from > 0f) LinearOutSlowInEasing else FastOutSlowInEasing),
                    )
                }
                // 到满值立刻开始散。连续换字时这一步总被打断，所以全程都糊着，
                // 只有最后一次换字之后才真的走到底 —— 这正是「一直模糊、最后才清晰」。
                blurLevel.animateTo(
                    0f,
                    tween(TEXT_MOTION_BLUR_RELEASE_MILLIS, easing = FastOutSlowInEasing),
                )
                blurActive = false
            } finally {
                TextMotionBudget.release()
            }
        }
    }

    if (outgoing == null && !blurActive) {
        // 稳态：不套额外节点，调用方的 modifier 原样落到唯一的 Text 上。
        content(displayed, modifier)
        return
    }

    // 过渡态：旧字与新字各画一份，叠在同一原点；模糊只在**最外层**套一次。
    // outgoing 可能已经清掉（淡入走完）而模糊还在散 —— 这时只画一条，但模糊层不能撤。
    val out = outgoing
    val progress = anims.progress
    val blurLevel = anims.blurLevel
    Layout(
        modifier = modifier.then(
            Modifier.graphicsLayer {
                // 绘制期读动画值：只失效本图层，不连累上层重组。
                val blurPx = TEXT_MOTION_BLUR.toPx() * blurLevel.value
                renderEffect = if (supported && blurPx > 0.01f) {
                    BlurEffect(blurPx, blurPx, TileMode.Decal)
                } else null
            }
        ),
        content = {
            if (out != null) {
                content(
                    out,
                    Modifier.graphicsLayer { alpha = 1f - progress.value },
                )
                content(
                    displayed,
                    Modifier.graphicsLayer { alpha = progress.value },
                )
            } else {
                // 淡入已走完、模糊还在散：只画最终字，alpha 恒为 1
                content(displayed, Modifier)
            }
        },
    ) { measurables, constraints ->
        val outPlaceable = if (out != null) measurables[0].measure(constraints) else null
        val inPlaceable = measurables[measurables.size - 1].measure(constraints)

        val width = maxOf(outPlaceable?.width ?: 0, inPlaceable.width)
        val height = maxOf(outPlaceable?.height ?: 0, inPlaceable.height)

        layout(width, height) {
            // 竖直方向一律贴顶：折行数变化时首行不动，比居中（整块下沉半行）稳。
            outPlaceable?.place(alignX(outPlaceable.width, width, textAlign, layoutDirection), 0)
            inPlaceable.place(alignX(inPlaceable.width, width, textAlign, layoutDirection), 0)
        }
    }
}

/**
 * 过渡态里一条文字的横向落点。
 *
 * 只在盒子比文字宽时才有偏移 —— 盒子宽 = 两条的较大值，所以宽的那条恒为 0（边界都不动），
 * 短的这条按对齐方式靠边：右对齐就贴右，居中就居中。
 *
 * 父级给固定宽度时两条同宽，[textWidth] == [boxWidth]，恒返回 0，对既有布局零影响。
 */
private fun alignX(
    textWidth: Int,
    boxWidth: Int,
    textAlign: TextAlign?,
    layoutDirection: LayoutDirection,
): Int {
    val slack = boxWidth - textWidth
    if (slack <= 0) return 0
    val ltr = layoutDirection == LayoutDirection.Ltr
    return when (textAlign) {
        TextAlign.Center -> slack / 2
        TextAlign.End -> if (ltr) slack else 0
        TextAlign.Start -> if (ltr) 0 else slack
        TextAlign.Right -> slack
        // Left / Justify / Unspecified / null 都按起点处理：左对齐是默认观感
        else -> 0
    }
}

/**
 * 同时进行中的文字动效配额。
 *
 * 除了「一个字变了」，还存在「整屏文字同帧全变」的场景（切周、换课表、切主题 / 语言）。
 * 那种时候每个文字都要建一份离屏模糊层，几百个叠在一帧里必然掉帧。这里给一个硬上限：
 * 超出配额的直接换字、不播动效 —— 宁可少几处动效，也不要卡一帧。
 *
 * 计数器只在主线程读写，不做并发保护。
 */
internal object TextMotionBudget {

    /** 同一时刻最多允许多少处文字做动效。 */
    private const val MAX_CONCURRENT = 48

    private var active = 0

    fun tryAcquire(): Boolean {
        if (active >= MAX_CONCURRENT) return false
        active += 1
        return true
    }

    fun release() {
        if (active > 0) active -= 1
    }
}
