package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.shape.ContinuousCapsule

/**
 * 胶囊滑块的**唯一参数源**。
 *
 * 手机底栏（[LiquidBottomTabs]）、平板侧栏（TabletNavSideBar）、反馈类型选择、
 * 开发进度筛选四处共用这一份：数值、颜色、弹簧参数都不许在调用点另写。
 *
 * 滑块的运动本身由 [CapsuleSelector] 负责（左右两条边界各一条弹簧、位移途中先撑开再收回），
 * 这里只管尺寸、颜色与按压表现。
 */
object CapsuleSlider {

    /** 滑块左右各内缩：宽度减 2×本值、位置补 1×本值，保持居中 */
    val Inset = 0.5.dp

    /** 滑块上下各内缩（底栏容器 56 → 滑块 48） */
    val VerticalInset = 8.dp

    /** 拖尾上限 */
    val TrailMax = 20.dp

    /** 按压时四周各缩多少（收缩量 = 2 × 本值） */
    val PressShrink = 2.dp

    /** `DampedDragAnimation` 的按压缩放（78 / 56） */
    const val PressScale: Float = 78f / 56f

    /** 拖尾增益：原版 miuix `OverlayView` 的 `min(4 × 单帧位移, 60px)` */
    const val TrailGain: Float = 4f

    /** 拖尾的一阶低通系数：直接吃每帧 dx 会一直哆嗦 */
    const val TrailSmoothing: Float = 0.55f

    /** 判定「按在了滑块上」的半格宽比例：小于它算抓滑块，大于它算点格子 */
    const val GrabHalfWidthRatio: Float = 0.55f

    /** 阻尼动画看成到位的阈值 */
    const val DampedVisibilityThreshold: Float = 0.001f

    /** 滑块底色：浅色压 6% 黑、深色提 10% 白 */
    fun color(isLightTheme: Boolean): Color =
        if (isLightTheme) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.1f)

    @Composable
    fun color(): Color = color(!isAppDarkTheme())
}

/**
 * 滑块本体：[CapsuleSlider] 的唯一绘制实现。
 *
 * 位置与宽度都在 **layout 期**读（[capsuleBounds]），组合期不碰任何高频值；
 * 按压缩放走 `graphicsLayer`，绘制期读一次按压进度，不进组合、不触发 layout。
 * 只收缩、不压暗 —— 没有水波纹、没有遮罩层。
 *
 * @param pressProgress 按压进度 0..1（底栏来自 `InteractiveHighlight`，侧栏来自面板按压）
 * @param visibility 额外透明度（侧栏 morph 到一半才开始显形），默认全不透明
 */
@Composable
internal fun Modifier.capsuleSliderPill(
    selector: CapsuleSelector,
    heightPx: Int,
    pressProgress: () -> Float,
    visibility: () -> Float = { 1f },
): Modifier = this
    .capsuleBounds(selector, heightPx)
    .graphicsLayer {
        val progress = pressProgress()
        if (progress > 0f && size.width > 0f && size.height > 0f) {
            val shrink = CapsuleSlider.PressShrink.toPx() * 2f
            val targetX = ((size.width - shrink) / size.width).coerceAtLeast(0.5f)
            val targetY = ((size.height - shrink) / size.height).coerceAtLeast(0.5f)
            scaleX = 1f - (1f - targetX) * progress
            scaleY = 1f - (1f - targetY) * progress
        }
        val v = visibility()
        if (v < 1f) alpha = v
    }
    .clip(ContinuousCapsule())
    .background(CapsuleSlider.color())
