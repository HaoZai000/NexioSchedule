package com.haooz.chedule.ui.basic

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import com.haooz.chedule.ui.utils.isAppDarkTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

// ── 跟手滑选 ────────────────────────────────────────────────────────
// 两处弹窗共用（LiquidGlassDropdownMenu 与 miuix 的 DropdownImpl + ListPopup），
// 区别只在状态由谁创建：前者自持，后者在 Popup 独立窗口里，需调用方创建后下发。
//
// 坐标系统一为**面板局部坐标**：面板整体缩放走 graphicsLayer，不进 layout 坐标。

/**
 * ⚠️ 形变参数必须**尺寸无关**。面板宽度随内容自适应（200~288dp）、高度随项数变化，
 * 「绝对 dp / 当前尺寸」的归一化会让效果在大面板上趋近于 0 ——
 * LiquidTopBarButton 的 `2dp / 高度` 就是现存的反例（42dp 上竖拖 40dp 约 4.8%，
 * 搬到几百 dp 高的面板上只剩零点几个百分点）。新增调用方别再抄那个写法。
 */

/** 沿拖动方向的最大拉伸比例（拖过行程基准时吃满） */
private const val DragStretchRatio = 0.045f

/**
 * 竖向行程基准相对横向的缩放（<1 = 更容易吃满），按展开进度插值。
 * 菜单只有几行高，竖向行程天然比横向短，用同一基准时竖拖明显迟钝。
 */
private const val VerticalDragRefScale = 0.6f

/** 长宽比惩罚下限：以 1:1 为界、偏离时才罚。coerceAtMost(1f) 在扁长面板上会砍掉三成 */
private const val AspectPenaltyFloor = 0.97f

/** 跟手滑选生效的最小展开进度：面板接近满尺寸后菜单项位置不再变动 */
private const val DragSelectReadyFraction = 0.98f

/**
 * 可滚动时判定「这是滚动、不是点选」的位移门槛。
 * 与系统 touchSlop 同量级，略微放宽 —— 手指轻抖不该把点选判没。
 */
private val ScrollTapSlop = 12.dp

internal data class DragTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
)

/**
 * 按压缩放 + 沿拖动方向拉伸 + tanh 阻尼跟手位移。菜单面板与整宽按钮共用这一套。
 *
 * 注意 penalty 只乘在**拉伸**项上（[DragTransform] 的 scaleX/scaleY），
 * 位移项走 tanh 且以 minDimension 归一化，不受宽高比惩罚影响。
 *
 * @param shapeAspectRatio 长宽比惩罚的基准，null = 用实测宽高比。
 *   接近方形的面板传 null 即可（实测就是它的正常形状）。
 *   **天生长条**的控件（整宽按钮 360x40）必须传 1f：否则 penaltyY = 40/360 ≈ 0.11，
 *   纵向拉伸被砍到九分之一，等于把这条轴的跟手感关掉。
 *   惩罚是为防「意外扁长」，不该 punish 设计上就长条的形状。
 */
internal fun computeDragTransform(
    width: Float,
    height: Float,
    fraction: Float,
    pressProgress: Float,
    offset: Offset,
    density: Density,
    shapeAspectRatio: Float? = null,
): DragTransform {
    val selfH = height.coerceAtLeast(1f)
    val minDim = minOf(width, selfH).coerceAtLeast(1f)
    val pressScale = 1f + with(density) { 4f.dp.toPx() } / selfH * pressProgress.coerceAtLeast(0f)
    // 竖向行程基准按展开进度收紧（菜单就几行高，竖向行程天然短）
    val refY = minDim * (1f - (1f - VerticalDragRefScale) * fraction.coerceIn(0f, 1f))
    val base = shapeAspectRatio
    val penaltyX = (base ?: width / selfH).coerceAtMost(AspectPenaltyFloor)
    val penaltyY = (base?.let { 1f / it } ?: (selfH / width)).coerceAtMost(AspectPenaltyFloor)
    val angle = atan2(offset.y, offset.x)
    return DragTransform(
        scaleX = pressScale + DragStretchRatio * abs(cos(angle) * offset.x / minDim) * penaltyX,
        scaleY = pressScale + DragStretchRatio * abs(sin(angle) * offset.y / refY) * penaltyY,
        translationX = minDim * tanh(0.08f * offset.x / minDim),
        translationY = minDim * tanh(0.08f * offset.y / minDim),
    )
}

/**
 * 滑选轴向。
 *
 * 竖排弹窗/侧栏按 Y 滑选（默认），横排面板（如课程长按的快捷条）按 X 滑选。
 * 状态上带轴向而不是各调用点各传一次：登记区间、命中测试、手势取坐标三处
 * 必须同轴，分开传迟早会漂。
 */
enum class DragSelectAxis { Vertical, Horizontal }

/** 菜单项登记信息：面板局部坐标下**沿滑选轴**的区间 + 点击动作 */
class DropdownPanelEntry internal constructor() {
    /**
     * 沿滑选轴的起止（不含 graphicsLayer 变换的 root 坐标减面板原点）。
     * 竖轴 = 上下缘，横轴 = 左右缘 —— 同一对字段，一套命中测试。
     */
    internal var start = 0f
    internal var end = 0f
    internal var action: (() -> Unit)? = null
    internal var enabled = true
}

/** 跟手滑选状态。由面板内容层创建，经 [LocalDropdownPanelDragSelect] 下发给菜单项 */
class DropdownPanelDragSelectState internal constructor(
    /** 滑选轴向；横排面板传 [DragSelectAxis.Horizontal]，默认竖排 */
    val axis: DragSelectAxis = DragSelectAxis.Vertical,
) {
    private val entries = mutableStateListOf<DropdownPanelEntry>()

    /** 当前命中的项；null = 松手不执行，菜单保持展开 */
    var selected by mutableStateOf<DropdownPanelEntry?>(null)
        internal set

    /**
     * 列表是否一屏装得下。
     *
     * 装不下时**跟手滑选禁用**，纵向手势归 verticalScroll；
     * [dropdownPanelDragSelect] 只认「位移没超过 slop 的点按」。
     */
    var fitsOnScreen by mutableStateOf(true)
        internal set

    /** 面板顶端在 root 里的 y，供菜单项把 boundsInRoot 换算成面板局部坐标（竖轴） */
    var panelTopInRoot by mutableFloatStateOf(0f)

    /** 面板左缘在 root 里的 x（横轴用，竖轴不读） */
    var panelLeftInRoot by mutableFloatStateOf(0f)

    internal fun register(entry: DropdownPanelEntry) {
        if (!entries.contains(entry)) entries.add(entry)
    }

    internal fun unregister(entry: DropdownPanelEntry) {
        entries.remove(entry)
        if (selected === entry) selected = null
    }

    internal fun updateBounds(entry: DropdownPanelEntry, start: Float, end: Float) {
        entry.start = start
        entry.end = end
    }

    /** @param position 沿滑选轴的面板局部坐标（竖轴 y / 横轴 x）；@return 命中的项，null = 空白处 */
    internal fun hitTest(position: Float): DropdownPanelEntry? {
        val hit = entries.firstOrNull { it.enabled && position >= it.start && position < it.end }
        selected = hit
        return hit
    }

    internal fun clear() {
        selected = null
    }
}

internal val LocalDropdownPanelDragSelect =
    compositionLocalOf<DropdownPanelDragSelectState?> { null }

/**
 * 面板上**唯一**的手势状态：按下 → 命中 → 逐帧改命中 → 松手执行。
 * 菜单项上不能再挂 clickable —— 两个手势状态互相抢事件正是「先按住再滑动会跳状态」的根因。
 *
 * 命中沿 [DragSelectAxis] 轴取坐标：竖排面板（下拉菜单/侧栏）用 y，
 * 横排面板（课程快捷条）用 x —— 三处（登记/命中/手势）同轴由状态保证。
 *
 * 收起态不接管（面板太小、没有项可选），也不消费 down 事件，收起态点按展开仍走触发区
 * 原有的 clickable。
 *
 * @param fraction 现读展开进度。**不能捕获** —— `pointerInput(Unit)` 的 lambda
 *   只在首次组合跑一次，捕获会得到陈旧值（曾因此导致「直接滑动毫无反应」）。
 */
fun Modifier.dropdownPanelDragSelect(
    state: DropdownPanelDragSelectState,
    fraction: () -> Float,
    hapticFeedback: HapticFeedback,
): Modifier = pointerInput(Unit) {
    val scrollSlop = ScrollTapSlop.toPx()
    // 沿滑选轴取坐标：竖排弹窗/侧栏用 y，横排快捷条用 x。axis 构造后不变，可在此取定
    val axisPosition: (Offset) -> Float =
        if (state.axis == DragSelectAxis.Horizontal) ({ it.x }) else ({ it.y })
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // 面板没长开时不参与：收起态照常点按展开
        if (fraction() < DragSelectReadyFraction) return@awaitEachGesture
        if (!state.fitsOnScreen) {
            // 一屏装不下：纵向手势归 verticalScroll，这里只补「点选」。
            // 位移超过 slop 判为滚动：放弃本次点选且不消费事件，滚动不受影响。
            val startY = down.position.y
            val startX = down.position.x
            state.hitTest(axisPosition(down.position))
            var scrolled = false
            while (true) {
                val change = awaitPointerEvent()
                    .changes.fastFirstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (abs(change.position.y - startY) > scrollSlop ||
                    abs(change.position.x - startX) > scrollSlop
                ) {
                    scrolled = true
                    break
                }
            }
            val tapped = state.selected
            state.clear()
            if (!scrolled) tapped?.action?.invoke()
            return@awaitEachGesture
        }
        // 一屏装得下：跟手滑选，独占该轴手势
        down.consume()
        var lastHit = state.hitTest(axisPosition(down.position))
        while (true) {
            val change = awaitPointerEvent()
                .changes.fastFirstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val hit = state.hitTest(axisPosition(change.position))
            if (hit != null && hit !== lastHit) {
                // 仅「换了一项」才震，按下即命中的那一下不震
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastHit = hit
            change.consume()
        }
        val hit = state.selected
        state.clear()
        hit?.action?.invoke()
    }
}

/**
 * 面板的按压 / 跟手拉伸 / 阻尼位移形变。
 *
 * 挂在两个槽位的共同父层（玻璃壳与内容层的父节点），玻璃、边光、阴影、内容
 * 才会整体变形；挂到内容层只能让文字动。收起态的形变幅度天然趋近 0。
 */
fun Modifier.dropdownPanelDragTransform(
    fraction: () -> Float,
    pressProgress: () -> Float,
    dragOffset: () -> Offset,
): Modifier = composed {
    graphicsLayer {
        val t = computeDragTransform(
            width = size.width,
            height = size.height,
            fraction = fraction(),
            pressProgress = pressProgress(),
            offset = dragOffset(),
            density = this,
        )
        scaleX = t.scaleX
        scaleY = t.scaleY
        translationX = t.translationX
        translationY = t.translationY
    }
}

/** 命中项的遮罩底色（浅色压黑、深色提白），全项目统一取这里 */
private fun dropdownPanelEntryHighlightColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.06f)

/**
 * 菜单项侧：登记沿滑选轴的区间（竖排上下缘 / 横排左右缘）+ 绘制命中高亮。
 * 供 `DropdownImpl` 等菜单项组件调用；轴向由 [DropdownPanelDragSelectState.axis] 定，
 * 调用方只管把项排在面板里。
 *
 * 用 drawBehind 而非 background：`DropdownImpl` 的项自带一层 alpha=1 的不透明白底
 * （`.drawBehind { drawRect(surfaceContainer) }`）。Modifier 链越靠后越晚绘制，调用方
 * 必须把本修饰符放在那行 drawBehind 之后，否则高亮被白底吞掉。
 *
 * 登记与绘制合在一个修饰符里：两者共用同一个 [DropdownPanelEntry]，拆开会各自
 * remember 出不同实例，选中态永远匹配不上。
 *
 * @param enabled false 的项不参与命中测试（滑过去不高亮、松手也不执行）
 * @param state 显式指定状态；null = 取 [LocalDropdownPanelDragSelect]。与
 *   `dropdownPanelDragSelect(state, ...)` 的入参形式对齐，侧栏这类自持状态、
 *   不想为下发再包一层 CompositionLocalProvider 的调用方直接传。**必须排在 action 之前**：
 *   `Dropdown.kt` 用尾随 lambda 传 action，挤到后面会把尾随 lambda 绑到 state 上
 *   （编译报 No value passed for parameter 'action'）。
 * @param highlightPadding 命中高光的四周内缩。默认 0 = 满幅，两处弹窗行为不变；
 *   侧栏传与条目选中遮罩相同的值，两个遮罩才对得齐。
 * @param highlightShape 命中高光的裁剪形状。默认 [RectangleShape] = 不裁
 * @param selected **静止态**（手指没命中任何项）是否显示本项遮罩。
 *   用来替代调用方原先那份静态选中遮罩 —— 遮罩只画这一层，不然同一项叠两层。
 *   手指一旦命中某项，就只亮命中项，这里传什么都会被盖掉。必须排在 action 之前，理由同 state。
 * @param action 命中并松手时执行。每次重组都会更新，可直接传 lambda
 */
@Composable
fun Modifier.dropdownPanelEntry(
    enabled: Boolean,
    state: DropdownPanelDragSelectState? = null,
    highlightPadding: PaddingValues = PaddingValues(0.dp),
    highlightShape: Shape = RectangleShape,
    selected: Boolean = false,
    action: () -> Unit,
): Modifier = composed {
    val dragSelect = state ?: LocalDropdownPanelDragSelect.current
    val entry = remember(dragSelect) { DropdownPanelEntry() }
    DisposableEffect(dragSelect, entry) {
        dragSelect?.register(entry)
        onDispose { dragSelect?.unregister(entry) }
    }
    SideEffect {
        entry.action = action
        entry.enabled = enabled
    }
    // 遮罩只有一层：手指命中某项时只亮命中项，没命中时亮 selected 那项。
    // 松手后 dragSelected 归 null，遮罩在 150ms 内淡变到 selected 那项 —— 滑到新项后
    // 由调用方的选中态接手，所以视觉上是「移过去就不再消失」，而不是跳回旧项。
    val dragSelected = dragSelect?.selected
    val visible = if (dragSelected != null) dragSelected === entry else selected
    // 进/退各 150ms 淡变，切 tab 不再硬切。代价是快速滑过时前后两项会短暂半透明并存
    // （交叉淡变的固有现象），已确认可接受。
    val maskAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(150),
        label = "dropdownPanelEntryAlpha",
    )
    // drawBehind 的 lambda 不是 @Composable，主题色必须在这里取好再传进去
    val highlightColor = dropdownPanelEntryHighlightColor(isAppDarkTheme())
    this
        .onGloballyPositioned {
            // boundsInRoot 不含 graphicsLayer 变换，面板与项都取 root 坐标再作差
            val dragState = dragSelect ?: return@onGloballyPositioned
            val bounds = it.boundsInRoot()
            if (dragState.axis == DragSelectAxis.Horizontal) {
                val left = bounds.left - dragState.panelLeftInRoot
                dragState.updateBounds(entry, left, left + it.size.width)
            } else {
                val top = bounds.top - dragState.panelTopInRoot
                dragState.updateBounds(entry, top, top + it.size.height)
            }
        }
        .drawBehind {
            if (maskAlpha <= 0f) return@drawBehind
            val maskColor = highlightColor.copy(
                alpha = highlightColor.alpha * maskAlpha
            )
            // 命中高光按调用方给的内缩 + 形状画，而不是满幅方块
            val padLeft = highlightPadding.calculateLeftPadding(layoutDirection).toPx()
            val padTop = highlightPadding.calculateTopPadding().toPx()
            val padRight = highlightPadding.calculateRightPadding(layoutDirection).toPx()
            val padBottom = highlightPadding.calculateBottomPadding().toPx()
            val boxW = (size.width - padLeft - padRight).coerceAtLeast(0f)
            val boxH = (size.height - padTop - padBottom).coerceAtLeast(0f)
            // 形状按**内缩后**的尺寸在原点造，再整体平移到内缩左上角，
            // 不必依赖 Path 的位移 API，clip 与绘制也落在同一坐标系
            val outline = highlightShape.createOutline(Size(boxW, boxH), layoutDirection, this)
            withTransform({ translate(padLeft, padTop) }) {
                when (outline) {
                    is Outline.Rectangle ->
                        clipRect(0f, 0f, boxW, boxH) { drawRect(maskColor) }
                    is Outline.Rounded ->
                        clipPath(Path().apply { addRoundRect(outline.roundRect) }) {
                            drawRect(maskColor)
                        }
                    is Outline.Generic ->
                        clipPath(Path().apply { addPath(outline.path) }) {
                            drawRect(maskColor)
                        }
                }
            }
        }
}