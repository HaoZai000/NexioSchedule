/** 小组件刷新广播接收器 */
package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetRefreshReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.haooz.chedule.ACTION_REFRESH_WIDGET"
    }

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("CourseReminder", "WidgetRefreshReceiver: ${intent.action} ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            try {
                // 跨日检测：日期变化时重新调度闹钟（覆盖次日课程无提醒的场景）
                CourseReminderHelper.checkAndRescheduleOnDayChange(context)
                // 兜底补发：若某门课闹钟未触发/丢失，在提醒窗口内立即补发（含超级岛通道）
                CourseReminderHelper.checkPendingPreClassReminders(context)
                com.haooz.chedule.widget.CourseWidgetProviderStandard.updateAllWidgets(context)
                com.haooz.chedule.widget.TodayCourseWidgetProviderStandard.updateAllWidgets(context)
                CourseReminderHelper.updateActiveCountdown(context)
            } finally {
                // 链式调度下一次刷新放在 finally：即使上面任一步抛异常，
                // 也要保证 30 分钟/分钟的刷新链不中断，否则跨日重设与兜底补发会永久停摆
                CourseReminderHelper.scheduleNextWidgetRefresh(context, alarmManager)
            }
        }
    }
}
