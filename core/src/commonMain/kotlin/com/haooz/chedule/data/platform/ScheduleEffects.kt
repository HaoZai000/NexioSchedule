package com.haooz.chedule.data.platform

import kotlin.concurrent.Volatile

/** 课表变更触发的宿主操作：重排提醒、同步手表、刷新小组件。 */
interface ScheduleChangeSink {
    /** 课程或课表数据已变更，需要重新调度提醒。 */
    fun rescheduleReminders()

    /** 通知手表同步。`reason` 仅用于日志归因（如 `"course-mutate"`）。 */
    fun syncWearable(reason: String)

    /** 刷新已添加到桌面的小组件。 */
    fun refreshWidgets()
}

/** 宿主在启动时注册接收器；未注册时忽略通知。 */
object ScheduleEffects {
    @Volatile
    private var sink: ScheduleChangeSink? = null

    fun init(sink: ScheduleChangeSink) {
        this.sink = sink
    }

    fun rescheduleReminders() {
        sink?.rescheduleReminders()
    }

    fun syncWearable(reason: String) {
        sink?.syncWearable(reason)
    }

    fun refreshWidgets() {
        sink?.refreshWidgets()
    }
}
