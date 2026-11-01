package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId

/**
 * 课程表页面级触控状态。刻意用普通 holder 而非 snapshot state：
 * 值每帧都可能变，用 state 会带着几十张卡一起重组。
 */
class ScheduleTouchState {
    /** 页面是否正在滑动（横滑 pager 或页内纵滑） */
    var scrolling: Boolean = false
    /** 本次手势是否出现过「多指」。必须放页面层统计：Compose 只把命中该节点的指针派发给节点，
     *  卡片看不到落在别处的手指。用于拦掉多指误触发的点击/长按。 */
    var multiTouch: Boolean = false
    /** 正按住课程卡拖拽的那根指针。翻页手势只屏蔽这一根，其余手指仍可翻周（跨周调课）。 */
    var draggingPointerId: PointerId? = null

    // ── 跨周调课：拖拽跟手交给页面级 tracker ─────────────────────────────────
    //
    // 课卡住在周 pager 的页里。直接拿卡片本地坐标算位移有问题：
    //  - 翻周 = 整页平移一屏，手指没动本地坐标也会变一屏 → 浮层飞出去（「翻页就丢」）；
    //  - 页内滚动同理，浮层会越拖越偏（「不跟手」）。
    //  - 拖到隔周后源页会被 pager 回收，卡片上的 pointerInput 被协程取消，
    //    跟手直接断掉。
    // 所以位移改由挂在 pager（跨周不重建）上的 tracker 用 root 坐标计算；卡片只负责
    // 长按、起拖判定和把指针交给 tracker。

    /** 拖拽起点在 root 坐标下的手指位置。tracker 用它把位移算成「相对按下时」。 */
    var draggingDownRoot: Offset? = null
    /** 正在拖的课程 id（tracker 收尾时回传给上层） */
    var draggingCourseId: String? = null
    /** 真的拖起来了（位移超过容差）——松手才按落点落课，否则只是菜单上的手抖 */
    var dragStarted: Boolean = false
    /** 页面级 tracker 已接管跟手位移与收尾 */
    var dragTrackerActive: Boolean = false
    /** 本次拖拽已经被 tracker 收过尾；卡片侧不要再收一次 */
    var dragEndHandled: Boolean = false

    /** 卡片侧交出拖拽 / tracker 收尾后清理。 */
    fun clearDragTracking() {
        draggingPointerId = null
        draggingCourseId = null
        draggingDownRoot = null
        dragStarted = false
        dragTrackerActive = false
        dragEndHandled = false
    }

    /**
     * tracker 收尾：清指针与跟手状态，但把 [dragEndHandled] 留成 true。
     * 同一帧里 tracker 走 Initial、课卡走 Main，课卡要能看出「这次已经被收过尾了」，
     * 否则会把同一次松手再写一遍。下一轮起拖时由课卡重置成 false。
     */
    fun endDragTracking() {
        draggingPointerId = null
        draggingCourseId = null
        draggingDownRoot = null
        dragStarted = false
        dragTrackerActive = false
        dragEndHandled = true
    }
}
