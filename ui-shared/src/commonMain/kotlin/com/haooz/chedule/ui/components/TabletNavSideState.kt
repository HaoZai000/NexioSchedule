package com.haooz.chedule.ui.components

import com.haooz.nexioui.tabletnav.TabletNavState

/**
 * 应用侧平板导航状态。
 *
 * 状态本体由 nexioui 的 [TabletNavState] 提供，应用只保留一个全局单例，
 * 供内容避让、玻璃采样与「回到今天」按钮读取。
 */
val TabletNavSideState = TabletNavState()

/**
 * 几何变化的唯一采样信号。
 *
 * 内容区会随展开 / 形变横移，任何按屏幕位置采样的玻璃都必须把它并进重绘信号。
 */
fun tabletNavSamplingSignal() {
    TabletNavSideState.expandProgress.floatValue
    TabletNavSideState.morphProgress.floatValue
}

/**
 * 顶栏糊层的采样轨道，返回一个可比大小的单调量。
 *
 * 语义与 [tabletNavSamplingSignal] 同源；勿在组合期调用。
 */
val tabletNavExpandSampleTrack: () -> Float = {
    TabletNavSideState.expandProgress.floatValue + TabletNavSideState.morphProgress.floatValue
}

/** 横移进度：不夹上界，保留弹簧过冲。 */
fun tabletNavSlideProgress(): Float =
    TabletNavSideState.morphProgress.floatValue.coerceAtLeast(0f)

/**
 * [tabletNavSamplingSignal] 的「有没有变过」版本。
 *
 * 每个消费方各持一个实例，共用会把状态互相吃掉。
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
