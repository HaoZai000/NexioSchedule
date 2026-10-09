// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.basic

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.interactive.DropdownPanelDragSelectState
import top.yukonga.miuix.kmp.interactive.LocalDropdownPanelDragSelect
import top.yukonga.miuix.kmp.interactive.dropdownPanelDragSelect
import top.yukonga.miuix.kmp.interactive.dropdownPanelDragTransform
import com.kyant.backdrop.edgelight.edgeLight
import com.kyant.backdrop.edgelight.rememberDefaultEdgeLight
import top.yukonga.miuix.kmp.interactive.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import top.yukonga.miuix.kmp.material.LocalChromeLensEnabled
import top.yukonga.miuix.kmp.internal.drawBlurredRingShadow
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.anim.SinOutEasing
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// =====================================================================
// 工具函数
// =====================================================================

/**
 * 将对齐方向根据布局方向（LTR/RTL）进行解析。
 * 在RTL布局下，Start和End会互换，TopStart和TopEnd会互换，依此类推。
 */
private fun PopupPositionProvider.Align.resolve(layoutDirection: LayoutDirection): PopupPositionProvider.Align {
    if (layoutDirection == LayoutDirection.Ltr) return this
    return when (this) {
        PopupPositionProvider.Align.Start -> PopupPositionProvider.Align.End
        PopupPositionProvider.Align.End -> PopupPositionProvider.Align.Start
        PopupPositionProvider.Align.TopStart -> PopupPositionProvider.Align.TopEnd
        PopupPositionProvider.Align.TopEnd -> PopupPositionProvider.Align.TopStart
        PopupPositionProvider.Align.BottomStart -> PopupPositionProvider.Align.BottomEnd
        PopupPositionProvider.Align.BottomEnd -> PopupPositionProvider.Align.BottomStart
    }
}

/**
 * 安全创建TransformOrigin，处理NaN和负值情况。
 * 如果值无效则返回0，否则返回原始值。
 */
internal fun safeTransformOrigin(x: Float, y: Float): TransformOrigin {
    val safeX = if (x.isNaN() || x < 0f) 0f else x
    val safeY = if (y.isNaN() || y < 0f) 0f else y
    return TransformOrigin(safeX, safeY)
}

/**
 * 从 [PopupPositionResult] 和实际偏移量推导 [PopupLayoutPosition] 和本地 [TransformOrigin]。
 * 在 composition 和 layout 阶段均可调用，保证方向和锚点始终一致。
 */
internal fun resolvePopupAnchors(
    positionResult: PopupPositionResult,
    calculatedOffset: IntOffset,
    popupContentSize: IntSize,
    parentBounds: IntRect,
    alignment: PopupPositionProvider.Align,
    layoutDirection: LayoutDirection,
): Pair<PopupLayoutPosition, TransformOrigin> {
    val isRightAligned = when (alignment.resolve(layoutDirection)) {
        PopupPositionProvider.Align.End,
        PopupPositionProvider.Align.TopEnd,
        PopupPositionProvider.Align.BottomEnd,
        -> true
        else -> false
    }
    val distLeft = abs(calculatedOffset.x - parentBounds.left)
    val distRight = abs((calculatedOffset.x + popupContentSize.width) - parentBounds.right)
    val rightAligned = if (popupContentSize.width > 0) distRight < distLeft else isRightAligned

    val layoutPos = PopupLayoutPosition(
        showBelow = positionResult.showBelow,
        showAbove = positionResult.showAbove,
        isRightAligned = rightAligned,
    )
    val origin = TransformOrigin(
        pivotFractionX = if (rightAligned) 1f else 0f,
        pivotFractionY = if (positionResult.showAbove) 1f else 0f,
    )
    return layoutPos to origin
}

// =====================================================================
// 常量 - 用于ListPopupColumn的测量策略
// =====================================================================

/** 计算宽度时考虑的最大子项数量 */
private const val MAX_ITEMS_FOR_WIDTH = 8
/** 计算高度时考虑的最大子项数量 */
private const val MAX_ITEMS_FOR_HEIGHT = 8

// =====================================================================
// ListPopupColumn - 弹窗内容列组件
// =====================================================================

/**
 * 弹窗内容列，自动将宽度对齐到最宽的子项。
 *
 * 功能说明：
 * - 自动计算宽度：取前8个子项的最大固有宽度，限制在200dp~288dp之间
 * - 支持垂直滚动
 * - 使用自定义MeasurePolicy进行精确的宽度控制
 *
 * @param onFitsOnScreen 内容是否一屏装得下（不需要滚动）。跟手滑选据此决定是否启用：
 *   装不下时纵向手势归滚动，否则两种手势互相抢。不关心就别传（默认空实现）。
 * @param content 弹窗内容子项
 */
@Composable
fun ListPopupColumn(
    onFitsOnScreen: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()

    val measurePolicy = remember {
        object : MeasurePolicy {
            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints,
            ): MeasureResult {
                // 宽度范围：200dp ~ 288dp
                val minPx = 200.dp.roundToPx()
                val maxPx = 288.dp.roundToPx()
                val widthCount = min(MAX_ITEMS_FOR_WIDTH, measurables.size)
                var maxIntrinsic = 0
                for (i in 0 until widthCount) {
                    val w = measurables[i].maxIntrinsicWidth(constraints.maxHeight)
                    if (w > maxIntrinsic) maxIntrinsic = w
                }
                val parentMin = constraints.minWidth
                val parentMax = constraints.maxWidth
                val upper = maxOf(maxPx, parentMin).coerceAtMost(parentMax)
                val lower = maxOf(minPx, parentMin).coerceAtMost(upper)
                val listWidth = maxIntrinsic.coerceIn(lower, upper)

                // 使用计算出的宽度测量所有子项
                val childConstraints = constraints.copy(minWidth = listWidth, maxWidth = listWidth, minHeight = 0)

                val placeables = ArrayList<Placeable>(measurables.size)
                var listHeight = 0
                for (i in measurables.indices) {
                    val p = measurables[i].measure(childConstraints)
                    placeables.add(p)
                    listHeight += p.height
                }

                return layout(listWidth, listHeight) {
                    var currentY = 0
                    for (i in placeables.indices) {
                        val p = placeables[i]
                        p.placeRelative(0, currentY)
                        currentY += p.height
                    }
                }
            }

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int,
            ): Int {
                val minPx = 200.dp.roundToPx()
                val maxPx = 288.dp.roundToPx()
                val widthCount = min(MAX_ITEMS_FOR_WIDTH, measurables.size)
                var maxIntrinsic = 0
                for (i in 0 until widthCount) {
                    val w = measurables[i].maxIntrinsicWidth(Int.MAX_VALUE)
                    if (w > maxIntrinsic) maxIntrinsic = w
                }
                val listWidth = maxIntrinsic.coerceIn(minPx, maxPx)

                val heightCount = min(MAX_ITEMS_FOR_HEIGHT, measurables.size)
                var height = 0
                for (i in 0 until heightCount) {
                    height += measurables[i].minIntrinsicHeight(listWidth)
                }
                return height
            }
        }
    }

    Layout(
        content = content,
        modifier = Modifier
            .focusGroup()
            .height(IntrinsicSize.Min)
            .verticalScroll(state = scrollState),
        measurePolicy = measurePolicy,
    )

    // 上报是否需要滚动。用 snapshotFlow 而非 onSizeChanged：maxValue 还会在
    // 「内容没变、只是被 maxHeight 压缩」时变化，那次 onSizeChanged 不触发。
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.maxValue <= 1 }
            .collect { onFitsOnScreen(it) }
    }
}

// =====================================================================
// PopupPositionProvider - 弹窗位置提供者接口
// =====================================================================

/**
 * 弹窗位置提供者接口。
 * 负责计算弹窗相对于锚点（触发组件）的显示位置。
 *
 * 注意：位置是相对于窗口计算的，不是相对于锚点！
 */
/**
 * 弹窗位置计算结果，包含偏移量和展开方向。
 *
 * @param offset 弹窗左上角在窗口坐标系中的偏移量
 * @param showBelow 弹窗是否在锚点下方展开
 * @param showAbove 弹窗是否在锚点上方展开
 */
@Immutable
data class PopupPositionResult(
    val offset: IntOffset,
    val showBelow: Boolean,
    val showAbove: Boolean,
)

@Stable
interface PopupPositionProvider {
    /**
     * 计算弹窗的位置（偏移量）和展开方向。
     *
     * @param anchorBounds 锚点（父组件）的边界
     * @param windowBounds 窗口安全区域的边界（排除状态栏、导航栏、刘海等）
     * @param layoutDirection 布局方向（LTR/RTL）
     * @param popupContentSize 弹窗内容的实际大小
     * @param popupMargin 弹窗的额外边距
     * @param alignment 弹窗相对于窗口的对齐方式
     */
    fun calculatePosition(
        anchorBounds: IntRect,
        windowBounds: IntRect,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
        popupMargin: IntRect,
        alignment: Align,
    ): PopupPositionResult

    /**
     * 获取弹窗的额外边距
     */
    fun getMargins(): PaddingValues

    /**
     * 弹窗相对于窗口的对齐方式（不是相对于锚点！）
     */
    enum class Align {
        Start,      // 左对齐（RTL下为右对齐）
        End,        // 右对齐（RTL下为左对齐）
        TopStart,   // 左上角
        TopEnd,     // 右上角
        BottomStart,// 左下角
        BottomEnd,  // 右下角
    }
}

// =====================================================================
// ListPopupDefaults - 弹窗默认配置
// =====================================================================

/**
 * 弹窗的默认配置对象。
 * 包含动画参数、尺寸限制、位置提供者等。
 */
object ListPopupDefaults {
    // ---- 动画参数 ----

    /**
     * 进入时的缩放动画（较慢，stiffness较小）
     * - dampingRatio: 阻尼比，控制弹簧的弹性程度（0.82 = 适中的弹性）
     * - stiffness: 刚度，控制弹簧的硬度（200 = 较慢）
     */
    val FractionEnterAnimationSpec = spring(dampingRatio = 0.78f, stiffness = 232f, visibilityThreshold = 0.0001f)

    /**
     * 退出时的缩放动画（使用原始弹簧参数）
     * - dampingRatio: 阻尼比，控制弹簧的弹性程度（0.82 = 适中的弹性）
     * - stiffness: 刚度，控制弹簧的硬度（362.5 = 中等速度）
     */
    val FractionExitAnimationSpec = spring(dampingRatio = 0.78f, stiffness = 400f, visibilityThreshold = 0.0001f)

    /** 通用缩放动画（兼容库引用，等同于进入动画） */
    val FractionAnimationSpec = FractionEnterAnimationSpec

    /** 进入时的透明度动画（150ms渐入） */
    val AlphaEnterAnimationSpec = tween<Float>(durationMillis = 120)

    /** 退出时的透明度动画（300ms渐出） */
    val AlphaExitAnimationSpec = tween<Float>(durationMillis = 320)

    /** 背景变暗的进入动画（200ms，使用SinOut缓动） */
    val DimEnterAnimationSpec = tween<Float>(durationMillis = 200, easing = SinOutEasing)

    /** 背景变暗的退出动画（300ms，使用SinOut缓动） */
    val DimExitAnimationSpec = tween<Float>(durationMillis = 300, easing = SinOutEasing)

    /** 手势重置动画（弹簧效果，用于返回手势后恢复弹窗状态） */
    val ResetAnimationSpec = spring(dampingRatio = 0.82f, stiffness = 362.5f, visibilityThreshold = 0.0001f)

    // ---- 尺寸限制 ----

    /** 弹窗最小宽度（200dp） */
    val MinWidth = 200.dp

    /** 弹窗测量时的最小高度（50dp），用作maxHeight和minHeight约束的下限 */
    val MinPopupHeight = 50.dp

    // ---- 位置提供者 ----

    /** 下拉式位置提供者的默认补偿量。
     *
     * **必须是 [Dp]，不能写死 px**：px 与 density 绑定，同一数值在 3x 机型上等于 27.3dp，
     * 在 4x 机型上只有 20.5dp（差近 7dp），换机即错位。下面两个值就是原先 `82`/`94` px
     * 在 3x 机型上的等效 dp，视觉不变。
     */
    val DropdownAnchorOffsetX = 27.34.dp
    val DropdownAnchorOffsetY = 31.33.dp

    /**
     * 创建下拉式位置提供者。
     * 弹窗会覆盖锚点文字显示（弹窗上端或下端与锚点对齐）。
     *
     * @param verticalMargin 弹窗与锚点之间的垂直间距（默认0dp，覆盖模式）
     * @param horizontalMargin 弹窗的水平边距（默认0dp）
     * @param anchorOffsetX 水平补偿。以 [Dp] 声明，组合期按当前 density 换算成 px，
     *   保证任何机型下等效 dp 一致（写死 px 会随 density 漂移 1~2dp 以上）。
     * @param anchorOffsetY 竖直补偿。含义同上。
     */
    @Composable
    fun dropdownPositionProvider(
        verticalMargin: Dp = 0.dp,
        horizontalMargin: Dp = 0.dp,
        anchorOffsetX: Dp = DropdownAnchorOffsetX,
        anchorOffsetY: Dp = DropdownAnchorOffsetY,
    ): PopupPositionProvider {
        val density = LocalDensity.current
        val offsetXDelta = with(density) { anchorOffsetX.roundToPx() }
        val offsetYDelta = with(density) { anchorOffsetY.roundToPx() }

        return remember(verticalMargin, horizontalMargin, offsetXDelta, offsetYDelta) {
            object : PopupPositionProvider {
                private val margins = PaddingValues(horizontal = horizontalMargin, vertical = verticalMargin)

                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowBounds: IntRect,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                    popupMargin: IntRect,
                    alignment: PopupPositionProvider.Align,
                ): PopupPositionResult {
                    // 计算X偏移（左对齐或右对齐，往右偏移）
                    val offsetX = if (alignment.resolve(layoutDirection) == PopupPositionProvider.Align.End) {
                        anchorBounds.right - popupContentSize.width - popupMargin.right + offsetXDelta
                    } else {
                        anchorBounds.left + popupMargin.left + offsetXDelta
                    }

                    // 计算Y偏移并记录展开方向
                    val spaceBelow = windowBounds.bottom - anchorBounds.bottom
                    val spaceAbove = anchorBounds.top - windowBounds.top
                    val offsetY: Int
                    val showBelow: Boolean
                    val showAbove: Boolean
                    if (spaceBelow > popupContentSize.height) {
                        // 显示在下方：弹窗上端与锚点上端对齐，往上偏移
                        offsetY = anchorBounds.top - offsetYDelta
                        showBelow = true
                        showAbove = false
                    } else if (spaceAbove > popupContentSize.height) {
                        // 显示在上方：弹窗下端与锚点下端对齐，往下偏移
                        offsetY = anchorBounds.bottom - popupContentSize.height + offsetYDelta
                        showBelow = false
                        showAbove = true
                    } else {
                        // 居中显示
                        offsetY = anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2
                        showBelow = false
                        showAbove = false
                    }

                    val clampedOffset = IntOffset(
                        x = offsetX.coerceIn(
                            windowBounds.left,
                            (windowBounds.right - popupContentSize.width - popupMargin.right).coerceAtLeast(windowBounds.left),
                        ),
                        y = offsetY.coerceIn(
                            (windowBounds.top + popupMargin.top).coerceAtMost(windowBounds.bottom - popupContentSize.height - popupMargin.bottom),
                            windowBounds.bottom - popupContentSize.height - popupMargin.bottom,
                        ),
                    )
                    return PopupPositionResult(clampedOffset, showBelow, showAbove)
                }

                override fun getMargins(): PaddingValues = margins
            }
        }
    }

    /**
     * 右键菜单/上下文菜单的位置提供者。
     * 弹窗会锚定到锚点的某个角上。
     *
     * 注意：目前此实现与dropdownPositionProvider逻辑相同，可能需要根据需求调整。
     */
    val ContextMenuPositionProvider = object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowBounds: IntRect,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
            popupMargin: IntRect,
            alignment: PopupPositionProvider.Align,
        ): PopupPositionResult {
            val offsetX: Int
            val offsetY: Int
            val showBelow: Boolean
            val showAbove: Boolean
            when (alignment.resolve(layoutDirection)) {
                PopupPositionProvider.Align.TopStart -> {
                    offsetX = anchorBounds.left + popupMargin.left
                    offsetY = anchorBounds.bottom + popupMargin.top
                    showBelow = true
                    showAbove = false
                }
                PopupPositionProvider.Align.TopEnd -> {
                    offsetX = anchorBounds.right - popupContentSize.width - popupMargin.right
                    offsetY = anchorBounds.bottom + popupMargin.top
                    showBelow = true
                    showAbove = false
                }
                PopupPositionProvider.Align.BottomStart -> {
                    offsetX = anchorBounds.left + popupMargin.left
                    offsetY = anchorBounds.top - popupContentSize.height - popupMargin.bottom
                    showBelow = false
                    showAbove = true
                }
                PopupPositionProvider.Align.BottomEnd -> {
                    offsetX = anchorBounds.right - popupContentSize.width - popupMargin.right
                    offsetY = anchorBounds.top - popupContentSize.height - popupMargin.bottom
                    showBelow = false
                    showAbove = true
                }
                else -> {
                    // 兜底逻辑：与dropdownPositionProvider相同
                    offsetX = if (alignment.resolve(layoutDirection) == PopupPositionProvider.Align.End) {
                        anchorBounds.right - popupContentSize.width - popupMargin.right
                    } else {
                        anchorBounds.left + popupMargin.left
                    }
                    val spaceBelow = windowBounds.bottom - anchorBounds.bottom
                    val spaceAbove = anchorBounds.top - windowBounds.top
                    if (spaceBelow > popupContentSize.height) {
                        offsetY = anchorBounds.bottom + popupMargin.bottom
                        showBelow = true
                        showAbove = false
                    } else if (spaceAbove > popupContentSize.height) {
                        offsetY = anchorBounds.top - popupContentSize.height - popupMargin.top
                        showBelow = false
                        showAbove = true
                    } else {
                        offsetY = anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2
                        showBelow = false
                        showAbove = false
                    }
                }
            }
            val clampedOffset = IntOffset(
                x = offsetX.coerceIn(
                    windowBounds.left,
                    (windowBounds.right - popupContentSize.width - popupMargin.right).coerceAtLeast(windowBounds.left),
                ),
                y = offsetY.coerceIn(
                    (windowBounds.top + popupMargin.top).coerceAtMost(windowBounds.bottom - popupContentSize.height - popupMargin.bottom),
                    windowBounds.bottom - popupContentSize.height - popupMargin.bottom,
                ),
            )
            return PopupPositionResult(clampedOffset, showBelow, showAbove)
        }

        override fun getMargins(): PaddingValues = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
    }
}

// =====================================================================
// 布局位置描述
// =====================================================================

/**
 * 描述弹窗相对于其锚点的放置方式。
 * 用于驱动方向性揭示动画和变换原点。
 */
@Immutable
data class PopupLayoutPosition(
    val showBelow: Boolean,     // 弹窗是否显示在锚点下方
    val showAbove: Boolean,     // 弹窗是否显示在锚点上方
    val isRightAligned: Boolean,// 弹窗是否右对齐（与锚点右侧对齐）
)

/**
 * 弹窗的解析布局信息。
 * 由 [rememberListPopupLayoutInfo] 计算并记忆。
 */
@Immutable
data class ListPopupLayoutInfo(
    val windowBounds: IntRect,              // 窗口安全区域边界
    val popupMargin: IntRect,               // 弹窗的额外边距（像素）
    val effectiveTransformOrigin: TransformOrigin, // 窗口坐标系下的变换原点（用于缩放动画）
    val localTransformOrigin: TransformOrigin,     // 本地坐标系下的变换原点（用于graphicsLayer）
    val popupLayoutPosition: PopupLayoutPosition,  // 弹窗的放置方向
)

// =====================================================================
// rememberListPopupLayoutInfo - 计算弹窗布局信息
// =====================================================================

/**
 * 计算并记忆弹窗的布局信息。
 * 根据锚点位置、内容大小、对齐方式等计算弹窗应该显示的位置。
 *
 * @param alignment 弹窗相对于窗口的对齐方式
 * @param popupPositionProvider 弹窗位置提供者
 * @param parentBounds 锚点（父组件）在窗口坐标系中的边界
 * @param popupContentSize 弹窗内容的测量大小
 */
@Composable
fun rememberListPopupLayoutInfo(
    alignment: PopupPositionProvider.Align,
    popupPositionProvider: PopupPositionProvider,
    parentBounds: IntRect,
    popupContentSize: IntSize,
): ListPopupLayoutInfo {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val layoutDirection = LocalLayoutDirection.current
    val displayCutout = WindowInsets.displayCutout
    val statusBars = WindowInsets.statusBars
    val navigationBars = WindowInsets.navigationBars
    val captionBar = WindowInsets.captionBar

    // 计算弹窗边距（像素）
    val margins = popupPositionProvider.getMargins()
    val popupMargin = remember(layoutDirection, density, margins) {
        with(density) {
            IntRect(
                left = margins.calculateLeftPadding(layoutDirection).roundToPx(),
                top = margins.calculateTopPadding().roundToPx(),
                right = margins.calculateRightPadding(layoutDirection).roundToPx(),
                bottom = margins.calculateBottomPadding().roundToPx(),
            )
        }
    }

    val containerSize = windowInfo.containerSize

    // 计算窗口安全区域边界（排除刘海、状态栏、导航栏等）
    val windowBounds = remember(
        layoutDirection,
        density,
        displayCutout,
        statusBars,
        navigationBars,
        captionBar,
        containerSize,
    ) {
        with(density) {
            IntRect(
                left = displayCutout.getLeft(this, layoutDirection),
                top = statusBars.getTop(this),
                right = containerSize.width - displayCutout.getRight(this, layoutDirection),
                bottom = containerSize.height - navigationBars.getBottom(this) - captionBar.getBottom(this),
            )
        }
    }

    // 预测变换原点（在弹窗未测量时使用）
    val predictedTransformOrigin = remember(alignment, popupMargin, parentBounds, layoutDirection, containerSize) {
        val xInWindow = when (alignment.resolve(layoutDirection)) {
            PopupPositionProvider.Align.End,
            PopupPositionProvider.Align.TopEnd,
            PopupPositionProvider.Align.BottomEnd,
            -> parentBounds.right - popupMargin.right
            else -> parentBounds.left + popupMargin.left
        }
        val yInWindow = when (alignment.resolve(layoutDirection)) {
            PopupPositionProvider.Align.BottomEnd, PopupPositionProvider.Align.BottomStart ->
                parentBounds.top - popupMargin.bottom
            else ->
                parentBounds.bottom + popupMargin.bottom
        }
        safeTransformOrigin(
            xInWindow / containerSize.width.toFloat(),
            yInWindow / containerSize.height.toFloat(),
        )
    }

    // 计算弹窗位置和展开方向（由 positionProvider 一次性返回）
    val positionResult = remember(
        popupContentSize,
        windowBounds,
        parentBounds,
        alignment,
        layoutDirection,
        popupMargin,
        popupPositionProvider,
    ) {
        if (popupContentSize == IntSize.Zero) {
            PopupPositionResult(IntOffset.Zero, showBelow = true, showAbove = false)
        } else {
            popupPositionProvider.calculatePosition(
                parentBounds,
                windowBounds,
                layoutDirection,
                popupContentSize,
                popupMargin,
                alignment,
            )
        }
    }
    val calculatedOffset = positionResult.offset

    // 解析弹窗的放置方向和本地变换原点：方向直接取自 positionProvider 的真实分支。
    val (popupLayoutPosition, localTransformOrigin) = remember(
        popupContentSize,
        calculatedOffset,
        positionResult,
        layoutDirection,
        alignment,
    ) {
        if (popupContentSize == IntSize.Zero) {
            val isRightAligned = when (alignment.resolve(layoutDirection)) {
                PopupPositionProvider.Align.End,
                PopupPositionProvider.Align.TopEnd,
                PopupPositionProvider.Align.BottomEnd,
                -> true
                else -> false
            }
            PopupLayoutPosition(showBelow = true, showAbove = false, isRightAligned = isRightAligned) to
                TransformOrigin(if (isRightAligned) 1f else 0f, 0f)
        } else {
            resolvePopupAnchors(positionResult, calculatedOffset, popupContentSize, parentBounds, alignment, layoutDirection)
        }
    }

    // 计算有效的变换原点（窗口坐标系，用于缩放动画的pivot）。
    // 方向直接取自 positionResult，与实际展开分支完全一致。
    val effectiveTransformOrigin = remember(
        popupContentSize,
        calculatedOffset,
        positionResult,
        containerSize,
        predictedTransformOrigin,
        layoutDirection,
        alignment,
    ) {
        if (popupContentSize == IntSize.Zero) {
            predictedTransformOrigin
        } else {
            val isRightAligned = when (alignment.resolve(layoutDirection)) {
                PopupPositionProvider.Align.End,
                PopupPositionProvider.Align.TopEnd,
                PopupPositionProvider.Align.BottomEnd,
                -> true
                else -> false
            }
            val cornerX = if (isRightAligned) {
                (calculatedOffset.x + popupContentSize.width).toFloat()
            } else {
                calculatedOffset.x.toFloat()
            }

            val cornerY = when {
                positionResult.showBelow -> calculatedOffset.y.toFloat()
                positionResult.showAbove -> (calculatedOffset.y + popupContentSize.height).toFloat()
                else -> (calculatedOffset.y + popupContentSize.height / 2f)
            }

            safeTransformOrigin(
                cornerX / containerSize.width.toFloat(),
                cornerY / containerSize.height.toFloat(),
            )
        }
    }

    return ListPopupLayoutInfo(
        windowBounds = windowBounds,
        popupMargin = popupMargin,
        effectiveTransformOrigin = effectiveTransformOrigin,
        localTransformOrigin = localTransformOrigin,
        popupLayoutPosition = popupLayoutPosition,
    )
}

/**
 * 面板矩形 Shape：每次重组都新建实例，配合 [equals] 让相等判定仍能命中。
 *
 * 存在的理由是 `EdgeLightNode` 的 outline 缓存用**引用比较**（`cachedOutlineShape === shape`）
 * 判断能否复用，而弹窗节点尺寸在动画中恒定不变 —— 若 shape 实例被 remember 住，
 * outline 会被冻结在第一帧的 42dp 小圆上（表现为描边消失、背景框像被钉死）。
 * 每帧换引用可强制它重算。
 *
 * @param rectKey 量化后的矩形标识（整数三元组：动画帧号 + 面板宽 + 面板高），用于 [equals]/[hashCode]
 * @param cornerRadius 圆角半径
 * @param rectProvider 返回 (left, top, width, height) 的 px 计算函数
 */
private class AnimatedPanelRectShape(
    private val rectKey: List<Int>,
    private val cornerRadius: Dp,
    private val rectProvider: (Float, Float) -> FloatArray,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rect = rectProvider(size.width, size.height)
        val base = ContinuousRoundedRectangle(cornerRadius)
            .createOutline(Size(rect[2], rect[3]), layoutDirection, density)
        val offset = Offset(rect[0], rect[1])
        return when (base) {
            // Outline.Rounded 不是 data class（无 copy），只能重新构造
            is Outline.Rounded -> Outline.Rounded(base.roundRect.translate(offset))
            // Path.translate 是原地修改的成员函数，必须新建 Path 承接，否则会污染 base
            is Outline.Generic -> Outline.Generic(Path().apply {
                addPath(base.path)
                translate(offset)
            })
            else -> base
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnimatedPanelRectShape) return false
        return rectKey == other.rectKey
    }

    override fun hashCode(): Int = rectKey.hashCode()
}

// =====================================================================
// ListPopupContent - 弹窗内容容器
// =====================================================================

/**
 * 弹窗内容容器，动画与 [LiquidOverlayDropdownPopup] 完全一致（1:1 复刻）：
 *
 * - 面板矩形从「收起态尺寸」起按每轴 `lerp(收起态, 面板尺寸, f)` **真实插值**（不是 scale，容器永不变形），
 *   矩形锚在锚点角（[localTransformOrigin]，朝上/朝下/朝左/朝右四种 —— 唯一区别于标准实现的地方）。
 *   收起态尺寸由 [collapseSize] 给出（触发区「选中文字 + 箭头」的实测宽高），未传入时退回 42dp。
 * - 锚点迁移位移（`k = 0.5p(1-s)`，`s = 当前宽度比`）与退出回弹位移叠加在矩形左上角。
 * - 内容按 `w / 面板宽` **等比**缩放并移到矩形中心（对应标准的 contentScale + align(Center)）。
 * - 裁剪 / 玻璃 / 边缘光共用同一个矩形，圆角恒为 25dp（收起态被胶囊化成正圆），不做反向补偿。
 * - 内容按 `((f-0.3)/0.4)` 淡入、按 `6dp*(1-|2f-1|)` 起雾。
 *
 * 弹窗节点自身尺寸始终是自然尺寸（不随动画变化），因此弹窗定位不会抖动。
 *
 * @param popupContentSize 弹窗内容的当前大小
 * @param onPopupContentSizeChange 内容大小变化时的回调
 * @param fractionProgress 提供当前展开进度（0→1，spring 可能过冲）
 * @param originProgress 提供锚点迁移进度（比 fraction 更快到 1）
 * @param localTransformOrigin 本地坐标系下的变换原点（= 锚点角）。接入 anchors 时仅作兜底。
 * @param anchors layout 阶段算出的锚点角，draw 同帧可读，优先于 localTransformOrigin。
 * @param collapseContent 收起态显示的内容（选项文本 + 箭头），随容器长大淡出。
 * @param modifier 修饰符
 * @param collapseSize 收起态尺寸（px）。通常是触发区「选中文字 + 箭头图标」的实测尺寸，
 *   弹窗从这块内容原位长成面板。为 null 或 0 时退回 42dp。
 * @param collapseExtra 收起态尺寸的额外补偿（加在 lerp 的起点上）
 * @param isEntering 是否正在进场。进场/退场用不同的淡入淡出时机档位。
 * @param content 弹窗内容
 */
/**
 * layout 阶段算出的锚点角（**普通对象，不是 state**）。
 * state 要下一帧才生效，draw 会先用错一帧，导致 panelRect 的锚点分支选错。
 */
class PopupAnchors(
    var right: Boolean = false,
    var bottom: Boolean = false,
)

@Composable
fun ListPopupContent(
    popupContentSize: IntSize,
    onPopupContentSizeChange: (IntSize) -> Unit,
    fractionProgress: () -> Float,
    originProgress: () -> Float,
    localTransformOrigin: TransformOrigin,
    // layout 阶段算出的锚点角，draw 同帧可读，比 composition 期的
    // localTransformOrigin 早一帧生效。见 [PopupAnchors]。
    anchors: PopupAnchors? = null,
    modifier: Modifier = Modifier,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    collapseSize: IntSize? = null,
    collapseExtra: DpSize = DpSize.Zero,
    isEntering: Boolean = true,
    collapseContent: (@Composable () -> Unit)? = null,
    /** 跟手滑选状态。为 null 时本弹窗不启用跟手选择（内容层直接透传，不挂手势/形变）。 */
    dragSelectState: DropdownPanelDragSelectState? = null,
    /**
     * 是否启用 chrome 透镜（高分辨率下让玻璃面板内部更通透）。
     *
     * 原来直接读 App 的 AppMaterialSettings.chromeLensEnabled()，那让本文件无法进
     * commonMain。默认改为从 [LocalChromeLensEnabled] 读 —— App 在根部 provide，
     * 行为与迁移前一致；也可显式传参覆盖。
     */
    chromeLensEnabled: Boolean = LocalChromeLensEnabled.current,
    content: @Composable () -> Unit,
) {
    val cornerRadius = 25.dp
    val backgroundColor = MiuixTheme.colorScheme.surfaceContainer
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f

    val localDensity = LocalDensity.current
    // 未传 collapseSize 时的兜底
    val fallbackCollapsePx = with(localDensity) { 28.dp.toPx() }
    val extraW = with(localDensity) { collapseExtra.width.toPx() }
    val extraH = with(localDensity) { collapseExtra.height.toPx() }
    val collapseW = (collapseSize?.width?.takeIf { it > 0 }?.toFloat() ?: fallbackCollapsePx) + extraW
    val collapseH = (collapseSize?.height?.takeIf { it > 0 }?.toFloat() ?: fallbackCollapsePx) + extraH
    // overshootRoom 给 spring 过冲刺出的空间（约 2%，动不到这里的零头）；
    // shadowPadding 给外扩阴影。两者合称 chrome，ListPopupLayout 会照同样的量扣回来。
    val shadowPadding = 24.dp
    val overshootRoom = 14.dp
    val overshootRoomPx = with(localDensity) { overshootRoom.toPx() }
    val chromeLens = chromeLensEnabled

    val glassEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
        remember(chromeLens) {
            {
                vibrancy()
                blur(PopupBlurRadius.dp.toPx())
            }
        }

    // 动画期间靠帧号触发重组以刷新 shape 实例；静止时不重组，避免无谓重绘。
    val frame = remember { derivedStateOf { (fractionProgress() * 400f).roundToInt() } }.value

    // 面板绘制参数包：两个 subcompose 槽位共用同一套进度/曲线/颜色，摊平传参会淹掉调用点。
    val style = PopupPanelStyle(
        cornerRadius = cornerRadius,
        backgroundColor = backgroundColor,
        isDark = isDark,
        overshootRoomPx = overshootRoomPx,
        collapseW = collapseW,
        collapseH = collapseH,
        liquidGlassBackdrop = liquidGlassBackdrop,
        glassEffects = glassEffects,
        frame = frame,
        fractionProgress = fractionProgress,
        originProgress = originProgress,
        isEntering = isEntering,
        anchors = anchors,
        fallbackPivot = localTransformOrigin,
    )

    // SubcomposeLayout：把测量提前到布局期，同一 pass 内先量内容、再用真实尺寸铺玻璃
    // 跟手拖动的形变挂在两个槽位的共同父层，玻璃壳才会一起变形；
    // 按压高光夹在材质层与内容层之间（见 [PopupGlassCanvas]）。
    val dragScope = rememberCoroutineScope()
    val dragHighlight = remember(dragScope) {
        InteractiveHighlight(
            animationScope = dragScope,
            // 面板越大光晕要越小，否则收起态的光会漫出小圆
            radiusScale = { 1f - 0.6f * fractionProgress().coerceIn(0f, 1f) },
            // 半径上限 150dp：不随选项数变化的 minDimension —— 否则选项越多
            // 面板越高、光晕越大（2 项 71dp → 6 项 173dp）。
            // 收起态面板小，min 会自动退回自身尺寸。
            radiusBaseDp = HighlightRadiusBase,
        )
    }
    SubcomposeLayout(
        modifier = modifier
            .padding(shadowPadding)
            // 命中测试需要 state，只在下拉菜单场景挂；其余弹窗只有形变与高光
            .then(
                if (dragSelectState != null) {
                    // 覆盖整个面板，手指按下后能滑到任意一项。
                    // fraction 必须现读：pointerInput(Unit) 的 lambda 只跑一次
                    Modifier.dropdownPanelDragSelect(
                        state = dragSelectState,
                        fraction = { fractionProgress() },
                        hapticFeedback = LocalHapticFeedback.current,
                    )
                } else {
                    Modifier
                }
            )
            .then(dragHighlight.gestureModifier)
            // 面板在 root 里的 y，供菜单项把 boundsInRoot 换算成手势所用的局部坐标。
            // boundsInRoot 不含 graphicsLayer 变换，所以形变不影响这个换算。
            .onGloballyPositioned {
                dragSelectState?.panelTopInRoot = it.boundsInRoot().top
            }
            .dropdownPanelDragTransform(
                fraction = { fractionProgress() },
                pressProgress = { dragHighlight.pressProgress },
                dragOffset = { dragHighlight.offset },
            ),
    ) { constraints ->
        val roomPx = overshootRoomPx.roundToInt()

        // 1. 先量内容，拿到真实自然尺寸
        val contentPlaceable = subcompose(PopupSlot.Content) {
            // 下发给菜单项（登记位置 + 命中高亮）。必须在槽位内下发：
            // 菜单项是 content 的后代，在槽位外下发它们读不到。
            if (dragSelectState != null) {
                CompositionLocalProvider(LocalDropdownPanelDragSelect provides dragSelectState) {
                    PopupMenuLayer(
                        style = style,
                        reportedSize = popupContentSize,
                        onSizeChange = onPopupContentSizeChange,
                        content = content,
                    )
                }
            } else {
                PopupMenuLayer(
                    style = style,
                    reportedSize = popupContentSize,
                    onSizeChange = onPopupContentSizeChange,
                    content = content,
                )
            }
        }.first().measure(
            constraints.copy(
                maxWidth = (constraints.maxWidth - 2 * roomPx).coerceAtLeast(0),
                maxHeight = (constraints.maxHeight - 2 * roomPx).coerceAtLeast(0),
                minWidth = 0,
                minHeight = 0,
            )
        )

        // 2. 用真实尺寸铺玻璃画布
        val naturalW = contentPlaceable.width.toFloat()
        val naturalH = contentPlaceable.height.toFloat()
        val canvasW = naturalW + 2 * overshootRoomPx
        val canvasH = naturalH + 2 * overshootRoomPx
        val rect = style.rectOf(naturalW, naturalH)
        val glassPlaceable = subcompose(PopupSlot.Glass) {
            PopupGlassCanvas(
                style = style,
                canvasW = canvasW,
                canvasH = canvasH,
                rect = rect,
                collapseContent = collapseContent,
                highlight = dragHighlight,
            )
        }.first().measure(
            Constraints.fixed(canvasW.roundToInt(), canvasH.roundToInt())
        )

        layout(canvasW.roundToInt(), canvasH.roundToInt()) {
            glassPlaceable.place(0, 0)
            contentPlaceable.place(roomPx, roomPx)
        }
    }
}

/**
 * 面板阴影档位。**必须与 LiquidGlassDropdownMenu.kt 里的同名常量保持同值**，
 * 否则两种弹窗阴影深浅不一样。
 */
private const val PopupShadowArgbLight = 0x1A000000
private const val PopupShadowArgbDark = 0x2E000000
private const val PopupShadowBlur = 12f

/** 按压高光的半径上限，固定不随面板高度变化 */
private val HighlightRadiusBase = 150.dp
private const val PopupShadowExtend = 2f
/** ARGB 的 alpha 会被放大这么多倍 —— 环形路径重复描边会累积浓度 */
private const val PopupShadowAlphaGain = 3.2f

/** SubcomposeLayout 的槽位标识。Content 必须先于 Glass 测量（后者尺寸依赖前者）。 */
private enum class PopupSlot { Content, Glass }

/** 背景模糊半径（面板尺寸，比收起态更强）。保持常量以便 effects 引用稳定、命中 RenderEffect 缓存。 */
private const val PopupBlurRadius = 24f

// 进场/退场各走一档：单条曲线做不出「进场早淡入 + 退场晚淡出」这两个相反方向。
// 「标准」= LiquidGlassDropdownMenu 原值。
private const val ContainerAlphaEnter = 7f      // 标准 5f
private const val ContainerAlphaExit = 3.6f     // 标准 5f
private const val ContentEnterFrom = 0.22f      // 标准 0.30
private const val ContentEnterTo = 0.62f        // 标准 0.70
private const val ContentExitFrom = 0.30f
private const val ContentExitTo = 0.78f         // 标准 0.70：抬高端点 → 更晚开始淡出
// 收起态内容的淡出系数：alpha = 1 - fraction×K，淡完点 = 1/K。
// 退场与 LiquidGlassDropdownMenu 一致；进场由 2.5 提到 3.2
private const val CollapseIconKEnter = 3.2f
private const val CollapseIconKExit = 1.7f

/**
 * 面板绘制参数包。两个 subcompose 槽位共用同一套参数，摊平传参会淹掉调用点。
 * 每次重组新建实例无妨 —— 只是参数容器，不含状态。
 */
private class PopupPanelStyle(
    val cornerRadius: Dp,
    val backgroundColor: androidx.compose.ui.graphics.Color,
    val isDark: Boolean,
    val overshootRoomPx: Float,
    val collapseW: Float,
    val collapseH: Float,
    val liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    val glassEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit,
    val frame: Int,
    val fractionProgress: () -> Float,
    val originProgress: () -> Float,
    val isEntering: Boolean,
    anchors: PopupAnchors?,
    fallbackPivot: TransformOrigin,
) {
    // 锚点角必须**惰性读取**：layout 阶段才写入 anchors，而本对象在 composition 期就建好了。
    private val anchorRight: () -> Boolean =
        { anchors?.right ?: (fallbackPivot.pivotFractionX >= 0.5f) }
    private val anchorBottom: () -> Boolean =
        { anchors?.bottom ?: (fallbackPivot.pivotFractionY >= 0.5f) }

    fun containerAlpha(f: Float): Float =
        (f * if (isEntering) ContainerAlphaEnter else ContainerAlphaExit).coerceIn(0f, 1f)

    fun contentAlpha(f: Float): Float {
        val from = if (isEntering) ContentEnterFrom else ContentExitFrom
        val to = if (isEntering) ContentEnterTo else ContentExitTo
        return ((f - from) / (to - from)).coerceIn(0f, 1f)
    }

    /** 驼峰模糊：两端清晰、中间最糊 */
    fun contentBlur(f: Float): Dp = 6.dp * (1f - abs(2f * f - 1f)).coerceIn(0f, 1f)

    /**
     * 面板矩形 [left, top, width, height]（px），坐标系原点在玻璃画布左上角。
     * naturalW/H 由 SubcomposeLayout 在同一 pass 测出后显式传入，不从 popupContentSize 反推
     * （后者要等回报，首帧会退回 collapse fallback）。
     * 返回 lambda 的入参是画布尺寸，用于把矩形钳制在画布内。
     */
    fun rectOf(naturalW: Float, naturalH: Float): (Float, Float) -> FloatArray = { canvasW, canvasH ->
        val fr = fractionProgress()
        // 每轴独立 lerp：真插值而非 scale，不会变形
        val wRaw = collapseW + (naturalW - collapseW) * fr
        val hRaw = collapseH + (naturalH - collapseH) * fr
        // 锚点迁移：比尺寸更快到 1，先「移向面板中心」再放大
        val p = originProgress()
        val s = if (naturalW > 0f) wRaw / naturalW else 1f
        val k = 0.5f * p * (1f - s)
        val migrationX = (if (anchorRight()) -1f else 1f) * naturalW * k
        val migrationY = (if (anchorBottom()) -1f else 1f) * naturalH * k
        val leftRaw = (if (anchorRight()) naturalW - wRaw else 0f) + migrationX
        val topRaw = (if (anchorBottom()) naturalH - hRaw else 0f) + migrationY

        // 保险丝：矩形必须完整落在画布内。过冲只有约 2%，远小于每边余量，正常碰不到这个上限，
        // 它兜的是 migration 项把矩形推出画布的情况。
        val maxW = maxOf(canvasW, naturalW + 2 * overshootRoomPx)
        val maxH = maxOf(canvasH, naturalH + 2 * overshootRoomPx)
        val w = wRaw.coerceIn(0f, maxW)
        val h = hRaw.coerceIn(0f, maxH)
        val left = (leftRaw + overshootRoomPx).coerceIn(0f, (maxW - w).coerceAtLeast(0f))
        val top = (topRaw + overshootRoomPx).coerceIn(0f, (maxH - h).coerceAtLeast(0f))
        floatArrayOf(left, top, w, h)
    }
}

/** 内容层：上报自然尺寸，并相对画布居中缩放。 */
@Composable
private fun PopupMenuLayer(
    style: PopupPanelStyle,
    reportedSize: IntSize,
    onSizeChange: (IntSize) -> Unit,
    content: @Composable () -> Unit,
) {
    // 内容按**宽度**等比缩放（contentScale = rect宽 / 内容宽），但面板早期近乎方形、
    // 内容是长条，两者宽高比不一致 → 缩放后的高度会顶穿面板上下边。
    val clipShape: Shape = AnimatedPanelRectShape(
        rectKey = listOf(style.frame),
        cornerRadius = style.cornerRadius,
        rectProvider = { w, h ->
            val rect = style.rectOf(w, h)(w + 2 * style.overshootRoomPx, h + 2 * style.overshootRoomPx)
            floatArrayOf(
                rect[0] - style.overshootRoomPx,
                rect[1] - style.overshootRoomPx,
                rect[2],
                rect[3],
            )
        },
    )

    Box(
        modifier = Modifier
            // 上报自然尺寸给外部（Popup 定位策略 / maxHeight 判断）。
            // 内部渲染不依赖它 —— 画布尺寸在同一个 layout pass 里已经直接算出来了。
            .onGloballyPositioned { coordinates ->
                val size = coordinates.size
                if (reportedSize != size) onSizeChange(size)
            }
            // 这里只保留 fraction 驱动的缩放/位移；拖动形变在外层 SubcomposeLayout。
            // clip 排在 graphicsLayer 之前（外层）→ 作用于变换之后的坐标系，见 clipShape
            .clip(clipShape)
            .graphicsLayer {
                val fr = style.fractionProgress()
                // 本层节点实测尺寸就是内容自然尺寸，据此自算画布——
                // 不能引用外层 canvasW/H（那些要等这次 measure 完才有，会形成依赖环）。
                val rect = style.rectOf(size.width, size.height)(
                    size.width + 2 * style.overshootRoomPx,
                    size.height + 2 * style.overshootRoomPx,
                )
                val contentScale = if (size.width > 0f) rect[2] / size.width else 1f
                scaleX = contentScale
                scaleY = contentScale
                alpha = style.contentAlpha(fr)
                // 本层被 place 到 (room, room)，这里要减掉那个偏移才能对上画布坐标系
                translationX = rect[0] + rect[2] / 2f - (style.overshootRoomPx + size.width / 2f)
                translationY = rect[1] + rect[3] / 2f - (style.overshootRoomPx + size.height / 2f)
            }
            .blur(style.contentBlur(style.fractionProgress())),
    ) {
        content()
    }
}

/** 玻璃画布层：外投射阴影 + 材质 + 按压高光 + 收起态内容。尺寸由外层 Constraints.fixed 定死。 */
@Composable
private fun PopupGlassCanvas(
    style: PopupPanelStyle,
    canvasW: Float,
    canvasH: Float,
    rect: (Float, Float) -> FloatArray,
    collapseContent: (@Composable () -> Unit)?,
    highlight: InteractiveHighlight,
) {
    // 每次重组换新实例：edgeLight 的 outline 缓存按**引用**比较 shape，
    // 若被记住在第一帧的小圆上，outline 会冻结 —— 描边消失、背景框像被钉死。
    val panelShape: Shape = AnimatedPanelRectShape(
        rectKey = listOf(style.frame, canvasW.roundToInt(), canvasH.roundToInt()),
        cornerRadius = style.cornerRadius,
        rectProvider = rect,
    )

    Box(
        modifier = Modifier.popupPanelShadow(
            rect = rect,
            cornerRadius = style.cornerRadius,
            isDark = style.isDark,
            spread = { style.containerAlpha(style.fractionProgress()) },
        )
    ) {
        PopupMaterialLayer(style = style, shape = panelShape)

        // 按压高光：必须夹在材质层之上（否则被玻璃壳盖住）、内容层之下
        // （否则 Plus 加色会把菜单项的白底冲白）。
        Box(
            modifier = Modifier
                // matchParentSize：不撑尺寸的子节点会量成 0×0，高光就画不出来。
                // 与玻璃壳同一个 rect，高光逐像素贴合面板、不溢出到阴影留白
                .matchParentSize()
                .clip(panelShape)
                .then(highlight.modifier),
        )

        // 收起态内容（选项文本 + 箭头）：锁在面板矩形正中心，随容器长大淡出。
        // 自带 alpha —— 材质的 alpha 在材质层，两者互不影响。
        if (collapseContent != null) {
            Box(
                modifier = Modifier
                    // 基准统一取左上角，再用 translation 把中心算到矩形中心。
                    // 之前按锚点角 align + 线性迁过去，收起态（fr≈0，translation 还没起来）会停在角上。
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        val iconK = if (style.isEntering) CollapseIconKEnter else CollapseIconKExit
                        val p = (style.fractionProgress() * iconK).coerceIn(0f, 1f)
                        alpha = 1f - p
                        val r = rect(canvasW, canvasH)
                        translationX = r[0] + r[2] / 2f - size.width / 2f
                        translationY = r[1] + r[3] / 2f - size.height / 2f
                    },
            ) {
                // DropdownTriggerContent 是 RowScope 扩展，这里套 Row 提供作用域
                Row(verticalAlignment = Alignment.CenterVertically) {
                    collapseContent()
                }
            }
        }
    }
}

/**
 * 材质层：撑满玻璃画布，自带淡入淡出。
 * `matchParentSize` 是必须的 —— drawBackdrop 按自身节点尺寸 clipPath + 申请离屏缓冲，
 * 只有撑满画布（自然 + 2×余量）才有空间容纳回弹。画布尺寸由外层 Constraints.fixed 定死。
 * 单独一层是为了让 alpha 只作用在这里，挂在玻璃盒上会把里面的内容一起罩住。
 */
@Composable
private fun BoxScope.PopupMaterialLayer(
    style: PopupPanelStyle,
    shape: Shape,
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { alpha = style.containerAlpha(style.fractionProgress()) }
            .then(
                if (style.liquidGlassBackdrop != null && isRenderEffectSupported()) {
                    Modifier.drawBackdrop(
                        backdrop = style.liquidGlassBackdrop,
                        shape = { shape },
                        effects = style.glassEffects,
                        highlight = null,
                        shadow = null,
                        onDrawSurface = {
                            drawRect(
                                color = style.backgroundColor.copy(
                                    alpha = if (style.isDark) 0.8f else 0.72f,
                                )
                            )
                        }
                    )
                } else Modifier
            )
            .edgeLight(
                shape = shape,
                edgeLight = rememberDefaultEdgeLight(baseColor = style.backgroundColor)
            ),
    )
}

/** 面板外投射阴影：环形路径（外圈 CW 减内圈 CCW）+ 模糊/外扩随材质强度衰减。 */
private fun Modifier.popupPanelShadow(
    rect: (Float, Float) -> FloatArray,
    cornerRadius: Dp,
    isDark: Boolean,
    spread: () -> Float,
): Modifier = drawBehind {
    val s = spread()
    if (s <= 0.01f) return@drawBehind

    // 阴影画在玻璃画布上 → 直接用画布坐标系
    val r = rect(size.width, size.height)
    val left = r[0]
    val top = r[1]
    val boxW = r[2]
    val boxH = r[3]
    val blurRadius = PopupShadowBlur * density * s
    val extend = PopupShadowExtend * density * s
    val radius = cornerRadius.toPx()
    val shadowArgb = if (isDark) PopupShadowArgbDark else PopupShadowArgbLight

    // 原实现是 android.graphics.Path + BlurMaskFilter + nativeCanvas 手绘，
    // 会把整个文件钉死在 Android。改为跨平台 expect/actual（见 internal/PopupShadow.kt）：
    // Android 侧仍是 BlurMaskFilter，视觉与改动前完全一致。
    //
    // 注意增益算法与原实现一致：PopupShadowAlphaGain 乘的是常量里存的**原 alpha**
    // （0x1A/0x2E，即 26/46），不是 255。写成 *255 会被 coerceIn 截到 255，阴影直接爆掉。
    val argb = if (isDark) PopupShadowArgbDark else PopupShadowArgbLight
    val shadowAlpha = ((argb ushr 24) * PopupShadowAlphaGain).coerceAtMost(255f).toInt()
    drawBlurredRingShadow(
        size = size,
        left = left,
        top = top,
        width = boxW,
        height = boxH,
        radius = radius,
        spread = extend,
        blurRadius = blurRadius,
        // 两个常量的 RGB 均为 0，即纯黑阴影；alpha 用上面算出的整数，与原 Paint 一致。
        color = Color(0f, 0f, 0f, shadowAlpha / 255f),
    )
}

// =====================================================================
// rememberDynamicCornerRadiusShape 已移至 DynamicCornerRadiusShape.kt，
// 避免与 libs.miuix.ui 库中的 ListPopupKt 类冲突导致 NoSuchMethodError。

// =====================================================================
// popupClipReveal - 方向性裁剪揭示修饰符
// =====================================================================

/**
 * 方向性裁剪揭示修饰符。
 *
 * 在弹窗进入/退出时，可见区域会沿着弹窗的生成方向逐渐展开：
 * - 显示在锚点下方：从顶部向下展开
 * - 显示在锚点上方：从底部向上展开
 * - 居中显示：从中心向两侧展开
 *
 * 使用squircle（超椭圆）形状来保持四角与周围的squircle修饰符对齐。
 * 当squircleEnabled为false时，会退化为普通的圆角矩形。
 */
fun Modifier.popupClipReveal(
    fractionProgress: () -> Float,
    popupLayoutPosition: PopupLayoutPosition,
    cornerRadius: Dp,
    squircleEnabled: Boolean,
    revealLimitHeightPx: Float = 0f,
): Modifier = drawWithCache {
    val path = Path()
    val showBelow = popupLayoutPosition.showBelow
    val showAbove = popupLayoutPosition.showAbove
    onDrawWithContent {
        // 限制进度值在0~1之间（弹簧动画可能会超出）
        val progress = fractionProgress().coerceIn(0f, 1f)
        if (progress <= 0f) return@onDrawWithContent

        val height = size.height
        // 当设置了 revealLimitHeightPx 且朝上/朝下时，从限制高度展开到完整高度
        val visibleHeight = if (revealLimitHeightPx > 0f && (showBelow || showAbove)) {
            (revealLimitHeightPx + (height - revealLimitHeightPx) * progress).coerceIn(0f, height)
        } else {
            height
        }
        if (visibleHeight <= 0f) return@onDrawWithContent

        // 计算裁剪起始位置
        // 朝上/朝下：从锚点一侧向另一侧展开（配合 visibleHeight 限制）
        // 居中：从中心向两侧展开
        val clipStart = when {
            showBelow -> 0f                        // 朝下：从顶部向下展开
            showAbove -> height - visibleHeight   // 朝上：从底部向上展开
            else -> height * (0.5f - 0.5f * progress) // 居中：从中心向两侧展开
        }

        path.rewind()
        // 使用kyant库的RoundedRectangle创建圆角矩形路径
        // 圆角在动画过程中保持不变：当弹窗缩小时，圆角需要放大以抵消缩放
        val fraction = fractionProgress().coerceIn(0f, 1f)
        val scaleXL = 0.24f + 0.76f * fraction
        val scaleXY = 0.24f + 0.76f * fraction
        // 使用两个轴缩放的平均值来计算圆角，保持圆角不变
        val avgScale = (scaleXL + scaleXY) / 2f
        val scaledCornerRadius = cornerRadius / avgScale
        val roundedRectShape = ContinuousRoundedRectangle(scaledCornerRadius)
        val outline = roundedRectShape.createOutline(
            size = Size(size.width, visibleHeight),
            layoutDirection = layoutDirection,
            density = this@drawWithCache
        )
        when (outline) {
            is Outline.Rounded -> path.addRoundRect(outline.roundRect)
            is Outline.Generic -> path.addPath(outline.path)
            is Outline.Rectangle -> path.addRect(outline.rect)
        }
        if (clipStart == 0f) {
            clipPath(path) {
                this@onDrawWithContent.drawContent()
            }
        } else {
            translate(top = clipStart) {
                clipPath(path) {
                    translate(top = -clipStart) {
                        this@onDrawWithContent.drawContent()
                    }
                }
            }
        }
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
