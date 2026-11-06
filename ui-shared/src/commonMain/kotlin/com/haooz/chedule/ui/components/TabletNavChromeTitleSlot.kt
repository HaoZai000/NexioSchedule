package com.haooz.chedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import com.haooz.chedule.ui.utils.screenHeightDp
import com.haooz.chedule.ui.utils.screenWidthDp
import kotlin.math.roundToInt

/**
 * 设置页叠层标题槽：左/右栏顶栏标题随侧栏避让平移与分宽。
 * 竖屏侧栏为叠层，标题不跟随展开；横屏仍随避让。进度只在 measure 读。
 */
@Composable
internal fun Modifier.tabletNavChromeTitleSlot(
    maxWPx: Float,
    collapsedTotalPx: Float,
    expandedWidthPx: Float,
    statusBarPx: Int,
    heightPx: Int,
    isLeftColumn: Boolean,
): Modifier {
    val lockCollapsed = screenHeightDp() > screenWidthDp()
    val startInsetPx = with(LocalDensity.current) { LocalChromeStartInset.current.roundToPx() }
    return this then TabletNavChromeTitleSlotElement(
        maxWPx = maxWPx,
        collapsedTotalPx = collapsedTotalPx,
        expandedWidthPx = expandedWidthPx,
        statusBarPx = statusBarPx,
        heightPx = heightPx,
        isLeftColumn = isLeftColumn,
        lockCollapsed = lockCollapsed,
        startInsetPx = startInsetPx,
    )
}

private class TabletNavChromeTitleSlotElement(
    private val maxWPx: Float,
    private val collapsedTotalPx: Float,
    private val expandedWidthPx: Float,
    private val statusBarPx: Int,
    private val heightPx: Int,
    private val isLeftColumn: Boolean,
    private val lockCollapsed: Boolean = false,
    private val startInsetPx: Int = 0,
) : LayoutModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val sidePad = if (lockCollapsed) {
            collapsedTotalPx
        } else {
            lerp(
                collapsedTotalPx,
                expandedWidthPx,
                TabletNavSideState.expandProgress.floatValue,
            )
        }
        val basePad = startInsetPx + sidePad
        val contentW = (maxWPx - basePad).coerceAtLeast(0f)
        // 左栏标题槽与分栏同步：固定屏宽 0.39（maxWPx 即全屏宽）
        val leftW = maxWPx * 0.39f
        val x: Int
        val w: Int
        if (isLeftColumn) {
            x = basePad.roundToInt()
            w = leftW.roundToInt().coerceAtLeast(0)
        } else {
            x = (basePad + leftW).roundToInt()
            w = (contentW - leftW).roundToInt().coerceAtLeast(0)
        }
        val placeable = measurable.measure(
            Constraints.fixed(
                width = w.coerceIn(0, maxWPx.roundToInt().coerceAtLeast(0)),
                height = heightPx,
            )
        )
        return layout(
            maxWPx.roundToInt().coerceAtLeast(placeable.width + x),
            statusBarPx + heightPx
        ) {
            placeable.place(x, statusBarPx)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabletNavChromeTitleSlotElement) return false
        return maxWPx == other.maxWPx &&
                collapsedTotalPx == other.collapsedTotalPx &&
                expandedWidthPx == other.expandedWidthPx &&
                statusBarPx == other.statusBarPx &&
                heightPx == other.heightPx &&
                isLeftColumn == other.isLeftColumn &&
                lockCollapsed == other.lockCollapsed &&
                startInsetPx == other.startInsetPx
    }

    override fun hashCode(): Int {
        var result = maxWPx.hashCode()
        result = 31 * result + collapsedTotalPx.hashCode()
        result = 31 * result + expandedWidthPx.hashCode()
        result = 31 * result + statusBarPx
        result = 31 * result + heightPx
        result = 31 * result + isLeftColumn.hashCode()
        result = 31 * result + startInsetPx
        return result
    }
}
