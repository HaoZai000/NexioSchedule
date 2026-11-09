package com.haooz.chedule.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 平板左侧导航全局状态。
 *
 * ## 三个进度各自管什么（最容易搞混的地方）
 * | 名字 | 0 → 1 | 谁读 | 能不能夹上界 |
 * |---|---|---|---|
 * | [expandProgress] | 折叠图标轨 → 完整侧栏 | 面板宽度、内容避让 | 不夹（弹簧过冲要透出） |
 * | [morphProgress] | 侧栏 → 顶部胶囊 | 面板四边界、内容避让、圆角、卡片玻璃 | 不夹（横移过冲靠它） |
 * | [landBounce] | 静止 → 顶到底 | 面板底缘额外高度 | 夹 0..1 |
 *
 * 派生曲线见 `TabletNavMorph.kt`：`tabletNavSettleProgress()`（形变提前收尾）、
 * `tabletNavSlideProgress()`（横向含过冲）、`tabletNavItemProgress()`（条目错落）。
 *
 * **一律禁止在组合期读**，否则整树随动画每帧重组；layout / draw / graphicsLayer 随便读。
 *
 * ## 改几何就要想到玻璃
 * 这三个值会让内容区平移，任何"按屏幕位置采样"的玻璃都必须跟着重绘，
 * 统一走 [tabletNavSamplingSignal] / [TabletNavSamplingTracker]，别各写一份。
 */
object TabletNavSideState {
    /** 展开=完整侧栏；折叠=仅图标轨 */
    var expanded by mutableStateOf(true)

    /** 右上按钮控制：true = 收成顶部胶囊，侧边占位归 0；与 expanded 互不干扰。 */
    var topBarMode by mutableStateOf(false)

    /** 展开进度 0=折叠图标轨，1=完整侧栏。 */
    val expandProgress = mutableFloatStateOf(1f)

    /** 侧栏 → 顶栏胶囊的连续变形进度：0 = 侧栏，1 = 胶囊。由帧循环逐帧写入。 */
    val morphProgress = mutableFloatStateOf(0f)

    /**
     * 底部「落地回弹」进度：0 = 静止，1 = 顶到最深。
     *
     * 不靠 spring 自己的过冲：展开方向只有 2~4%，被面板底部 12dp 留白吃掉后肉眼为零。
     */
    val landBounce = mutableFloatStateOf(0f)

    /** 二级页点选主 tab 后宿主待处理下标；-1 = 无 */
    var pendingMainTab by mutableStateOf(-1)
}

/**
 * 侧栏 / 顶栏几何变化的**唯一采样信号**。
 *
 * 内容区会随这两个进度横移，所以任何按位置采样的玻璃（课程卡、顶栏糊层、底栏）
 * 都必须把它并进自己的重绘信号里：
 * - `LocalGlassRedrawSignal`（今日页 / 课表页各有一份）
 * - `layerBackdrop` 的 `mustRecord`
 * - `ProgressiveBlurTopBar` 的 `sampleTrack`
 *
 * 漏一处，那一处玻璃就会冻结在变形前的画面上 —— 而且症状看起来和别处的 bug 一模一样。
 * 所以新增"会让内容区移动"的状态时，**只改这一个函数**。
 */
fun tabletNavSamplingSignal() {
    TabletNavSideState.expandProgress.floatValue
    TabletNavSideState.morphProgress.floatValue
}

/**
 * [tabletNavSamplingSignal] 的"有没有变过"版本，给 `mustRecord` 这类谓词用。
 *
 * 每帧自己记上一次的值，返回期间是否移动过。一个实例对应一个消费方 ——
 * 多个节点共用一份会把状态互相吃掉（各自 new 一个）。
 */
class TabletNavSamplingTracker {
    private var lastExpand = Float.NaN
    private var lastMorph = Float.NaN

    fun moved(): Boolean {
        val expand = TabletNavSideState.expandProgress.floatValue
        val morph = TabletNavSideState.morphProgress.floatValue
        val moved = expand != lastExpand || morph != lastMorph
        lastExpand = expand
        lastMorph = morph
        return moved
    }
}
