package com.haooz.chedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.haooz.chedule.ui.utils.screenHeightDp
import com.haooz.chedule.ui.utils.screenWidthDp
import kotlin.math.roundToInt

/**
 * 平板侧栏的布局常量与内容区避让
 */

/** 展开侧栏占位宽度占屏宽比例 */
const val TabletNavSideWidthFraction = 0.22f

/** 折叠态遮罩（玻璃）宽度 */
val TabletNavIconRailWidth = 84.dp

/** 遮罩相对屏幕左缘的间距，展开/折叠保持不变（不贴边、也不左跳） */
val TabletNavSideInset = 12.dp

/** 今日/课表页滚动内容自己读的左侧留白；视口保持全屏，左右滑动不裁内容 */
val LocalPageStartPadding = staticCompositionLocalOf<Modifier> { Modifier }

/** 侧栏与标题栏共用的左侧安全区留白 */
val LocalChromeStartInset = staticCompositionLocalOf { 0.dp }

/**
 * 内容区左侧的避让宽度。
 * 竖屏：展开是纯叠层（侧栏浮在内容上 + 压暗），内容恒按折叠轨避让，展开不改变布局。
 * 横屏：展开仍让位给侧栏。
 *
 * 目标宽度在展开/折叠那一刻一次性测量出来（只重排一次），中间所有帧只做
 * graphicsLayer 平移，动画期间内容树零重绘。
 */
@Composable
fun tabletNavRailStartPadding(): Modifier {
    // 原为 LocalConfiguration（宽/高都要）；跨平台后用 :ui-shared 的 expect/actual 屏宽屏高
    val widthDp = screenWidthDp()
    if (widthDp < 600) return Modifier
    val isPortrait = screenHeightDp() > widthDp
    val density = LocalDensity.current
    val collapsedTotalPx = with(density) { (TabletNavSideInset + TabletNavIconRailWidth).toPx() }
    val expandedWidthPx = with(density) { (widthDp.dp * TabletNavSideWidthFraction).toPx() }
    // 竖屏叠层：内容 start padding 恒为折叠轨，展开不改变布局
    if (isPortrait) {
        return TabletNavRailTargetPaddingElement(collapsedTotalPx, collapsedTotalPx)
    }
    return TabletNavRailTargetPaddingElement(collapsedTotalPx, expandedWidthPx)
        .graphicsLayer {
            val p = TabletNavSideState.expandProgress.floatValue
            val targetPad =
                if (TabletNavSideState.expanded) expandedWidthPx else collapsedTotalPx
            translationX =
                lerp(collapsedTotalPx, expandedWidthPx, p) - targetPad
        }
}

private class TabletNavRailTargetPaddingElement(
    private val collapsedTotalPx: Float,
    private val expandedWidthPx: Float,
) : androidx.compose.ui.layout.LayoutModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        /** 只跟展开布尔、不跟进度：一次伸缩只在这里重排一次 */
        val padPx = if (TabletNavSideState.expanded) expandedWidthPx else collapsedTotalPx
        val padInt = padPx.roundToInt().coerceAtLeast(0)
        // 与 Modifier.padding(start=) 同语义：只收窄子约束横向，高度跟内容，不撑满
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = (constraints.minWidth - padInt).coerceAtLeast(0),
                maxWidth = (constraints.maxWidth - padInt).coerceAtLeast(0),
            )
        )
        val width = (placeable.width + padInt).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) {
            placeable.placeRelative(padInt, 0)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabletNavRailTargetPaddingElement) return false
        return collapsedTotalPx == other.collapsedTotalPx && expandedWidthPx == other.expandedWidthPx
    }

    override fun hashCode(): Int {
        var result = collapsedTotalPx.hashCode()
        result = 31 * result + expandedWidthPx.hashCode()
        return result
    }
}
