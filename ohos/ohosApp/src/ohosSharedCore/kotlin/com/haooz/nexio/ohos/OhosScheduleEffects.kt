package com.haooz.nexio.ohos

import com.haooz.chedule.data.platform.ScheduleChangeSink

/**
 * 课程 / 课表变更后的平台副作用（鸿蒙）。
 *
 * 与安卓 `AndroidScheduleEffects` 同职责：提醒重排、手表同步、小组件刷新。
 * 鸿蒙没有对接手环/手表的通道，[syncWearable] 保持空实现（入口也没展示）。
 */
internal object OhosScheduleEffects : ScheduleChangeSink {

    override fun rescheduleReminders() {
        // 课表变更同样要刷新卡片快照：共享层只在「重排提醒」这一个出口上挂变更事件
        OhosWidgetSnapshot.refresh()
        OhosCourseReminder.reconcile()
    }

    override fun syncWearable(reason: String) {
        // 鸿蒙侧尚未接穿戴推送（导入导出页已隐藏手表入口）
    }

    override fun refreshWidgets() {
        OhosWidgetSnapshot.refresh()
    }
}
