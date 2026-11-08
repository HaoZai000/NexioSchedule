package com.haooz.chedule.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 平板左侧导航全局状态。
 * 只保留侧边态：true = 展开（图标+文字），false = 折叠（仅图标）。
 *
 * 2026-10-10 从 TabletNavSideBar.kt 抽出迁入 :ui-shared ——
 * BackToNowFloatingButton（已迁）要读 [expandProgress] 做自适应内边距，
 * 状态对象必须和它同模块。同包名，调用方 import 一行不用改。
 */
object TabletNavSideState {
    /** 展开=完整侧栏；折叠=仅图标轨 */
    var expanded by mutableStateOf(true)

    /** 右上按钮控制：true = 收成顶部胶囊，侧边占位归 0；与 expanded 互不干扰。 */
    var topBarMode by mutableStateOf(false)

    /**
     * 展开进度 0=折叠图标轨，1=完整侧栏。
     * 只在 layout/draw/graphicsLayer 读，禁止在组合期读 ——
     * 否则 CourseScheduleApp 整树会随动画每帧重组，平板展开/缩回直接掉帧。
     */
    val expandProgress = mutableFloatStateOf(1f)

    /**
     * 侧栏 → 顶栏胶囊的连续变形进度：0 = 侧栏，1 = 胶囊。
     * 由 TabletNavMorphMotion 的帧循环逐帧写入；**禁止在组合期读**，
     * 内容区避让、面板四边界、圆角都只在 layout/draw 期读。
     */
    val morphProgress = mutableFloatStateOf(0f)

    /**
     * 底部「落地回弹」进度：0 = 静止，1 = 顶到最深。
     * 面板往下多长 `landBounce × 上限` 再弹回，只在 layout 期读。
     *
     * 为什么不靠 spring 自己的过冲：展开方向的过冲只有 2~4%，被面板底部 12dp 的留白
     * 吃掉以后肉眼为 0（实测就是「一点回弹都没有」）。这里改成一条显式的短路动画。
     */
    val landBounce = mutableFloatStateOf(0f)

    /** 二级页点选主 tab 后宿主待处理下标；-1 = 无 */
    var pendingMainTab by mutableStateOf(-1)
}
