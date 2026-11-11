package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId

/**
 * 课程表页面级触控状态。刻意用普通 holder 而非 snapshot state
 *
 * 长按由课卡判定，之后的跟手与收尾全部归挂在 pager 上的 tracker（跨周不重建）。
 * 课卡只写一次 [begin]，不再参与拖拽，因此不需要「接管 / 已收尾」这类交接标记。
 */
class ScheduleTouchState {
    /** 页面是否正在滑动（横滑翻周或页内纵滑） */
    var scrolling: Boolean = false

    /** 本次手势是否出现过「多指」：页面层统计，卡片看不到落在别处的手指 */
    var multiTouch: Boolean = false

    /** 正在跟手的指针；null = 当前没有拖拽 */
    var pointerId: PointerId? = null
    var courseId: String? = null
    var downRoot: Offset? = null

    /** 位移超过 touchSlop：松手要按落点落课，否则只是菜单上的手抖 */
    var dragStarted: Boolean = false

    fun begin(id: PointerId, course: String, root: Offset) {
        pointerId = id
        courseId = course
        downRoot = root
        dragStarted = false
    }

    fun clear() {
        pointerId = null
        courseId = null
        downRoot = null
        dragStarted = false
    }
}
