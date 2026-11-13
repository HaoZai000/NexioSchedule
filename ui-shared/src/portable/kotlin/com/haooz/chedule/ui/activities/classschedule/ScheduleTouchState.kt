package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId

/**
 * 课程表页面级触控状态。
 *
 * 刻意使用普通 holder 而非 snapshot state：长按由课程卡判定，跟手与收尾由挂在 pager 上的
 * tracker 接管（跨周不重建），所以这里只登记指针，不参与重组。
 */
class ScheduleTouchState {
    /** 页面是否正在滑动（横滑翻周或页内纵滑）。 */
    var scrolling: Boolean = false

    /** 本次手势是否出现过多指；页面层统计，卡片看不到落在别处的手指。 */
    var multiTouch: Boolean = false

    /** 正在跟手的指针；null 表示当前没有拖拽。 */
    var pointerId: PointerId? = null
    var courseId: String? = null
    var downRoot: Offset? = null

    /** 位移是否已超过 touchSlop；松手时据此决定落课还是当作手抖。 */
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
